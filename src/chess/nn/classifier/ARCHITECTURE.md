# Classifier Network Architecture

The `chess.nn.classifier` package is a small, LC0-style residual convolutional network that maps a **single chess position** to **one scalar logit** for binary classification. The first target is puzzle detection ("is this position the start of a tactic worth keeping?"), but the same architecture and exporter work for any per-position binary label. It is a deterministic, pure-Java CPU evaluator: the same `.bin` weights and the same input always produce the same logit, with no history, no policy head, and no value/WDL head.

This document describes the package as it exists in code today: the 21-plane input encoder, the residual trunk, the single-logit head, the on-disk `.bin` format, and how the package is fed by `record dataset classifier`. For the full neural-net story across crtk, see [In-House Engine](in-house-engine.md) and [LC0](lc0.md); for the data pipeline that produces training records, see [Use Cases](use-cases.md).

## What this package is for

- Score one position with one number. A positive logit means the positive class (for example, "puzzle") is more likely; a negative logit means the negative class is more likely.
- Stay structurally close to an LC0 residual tower so the format and forward pass are familiar, while dropping everything that does not serve a single binary decision.
- Provide a matching dataset exporter (`record dataset classifier`) so training data and inference share exactly the same 21-plane encoding. This is the same "one shared chess core" discipline used elsewhere in crtk: the encoder that builds training tensors is the encoder that runs at inference time.

> Honesty note: this is crtk's own compact classifier, not an LC0 network and not bit-exact with any LC0 release. It reuses the LC0 residual *family* and an LC0-like binary container, but the input encoding and the head are classifier-specific.

## Package layout

| Class | Role |
| --- | --- |
| `Encoder` | Encodes a `Position` into the `21 x 8 x 8` side-to-move input tensor. |
| `Network` | Loads a classifier `.bin` file, validates its shape, and runs the CPU forward pass. |
| `Model` | Convenience wrapper: encodes a `Position` with `Encoder`, then calls `Network`. Implements `AutoCloseable`. |

`ClassifierDatasetExporter` (in `chess.io`) is the export side of the same contract. It reuses `Encoder` directly so exported tensors match inference inputs byte-for-byte.

## Input encoding

The input is a `float[21 * 64]` tensor, stored **channel-major**: the first 64 floats are plane 0 laid out `a1, b1, ... h1, a2, ... h8`, the next 64 floats are plane 1, and so on. `Encoder.TOTAL_CHANNELS` is `21`.

All planes are written **from the side-to-move perspective**. When it is Black's turn, the board is rank-mirrored so that "our" pieces still sit closest to rank 1, exactly like LC0's current-position slice. There is **no history**: only the current position is encoded.

The 21 planes are:

| Plane | Contents |
| --- | --- |
| 0 | Our pawns |
| 1 | Our knights |
| 2 | Our bishops |
| 3 | Our rooks |
| 4 | Our queens |
| 5 | Our king |
| 6 | Opponent pawns |
| 7 | Opponent knights |
| 8 | Opponent bishops |
| 9 | Opponent rooks |
| 10 | Opponent queens |
| 11 | Opponent king |
| 12 | Repetition (left at zero; `Position` does not expose repetition history) |
| 13 | Our queenside castling right (all ones if available) |
| 14 | Our kingside castling right |
| 15 | Opponent queenside castling right |
| 16 | Opponent kingside castling right |
| 17 | Side-to-move-is-black (all ones when Black is to move) |
| 18 | Halfmove clock (the rule-50 counter, broadcast to all 64 squares) |
| 19 | Unused, all zero |
| 20 | Edge plane, all ones |

Implementation details worth knowing:

- Piece planes hold `1.0` on occupied squares and `0.0` elsewhere. The encoder reads per-piece bitboards in white-first order (`WP, WN, WB, WR, WQ, WK, BP, ... BK`) and, for a Black side-to-move position, swaps the our/opponent groups and rank-mirrors each board.
- Core bitboards index squares as `0 = a8 .. 63 = h1`; the encoder converts to plane indexing `0 = a1 .. 63 = h8` by reversing bytes (a vertical rank mirror).
- Castling availability is read from `Position.activeCastlingMoveTarget(...)`, so it is correct for standard chess and Chess960 castling targets, not just file-based assumptions.
- The halfmove-clock plane carries the raw counter value (not normalized) on every square.

`Encoder` is a stateless utility (`Encoder.encode(Position)` is `static`), which keeps encoding deterministic and thread-safe.

## Network architecture

The forward pass mirrors crtk's pure-Java LC0 CNN, but with the 21-plane input and a single-logit head. The reference baseline is a `6 x 64` tower (6 residual blocks, 64 trunk channels), though `Network` reads the actual shape from the file header rather than hard-coding it.

```text
Input stem:
  3x3 conv, 21 -> 64
  + bias, ReLU

Residual trunk (6 blocks, 64 channels throughout):
  3x3 conv, 64 -> 64, + bias, ReLU
  3x3 conv, 64 -> 64, + bias, + residual (block input), ReLU

Head:
  1x1 conv, 64 -> 32, + bias, ReLU
  global average pool over the 64 squares  -> 32-vector
  dense 32 -> 1   (no activation)

Output:
  1 scalar logit
```

The CPU forward pass (`Network.Evaluator`) uses thread-local scratch buffers, ping-pong `current`/`next` trunk tensors, and a precomputed per-square neighbor list for 3x3 convolutions (with a fast specialized path for 1x1). Convolutions and bias+ReLU passes parallelize across output channels via a `ForkJoinPool` once the channel count reaches 64; the thread count comes from the `crtk.classifier.threads` system property (legacy `crtk.puzzleclassifier.threads` is also honored), defaulting to the number of available processors. Parallelism changes scheduling but not the result.

Note that in the live `.bin` the network is **inference-fused**: batch norm is folded into convolution bias, so each conv layer carries weights plus a per-output-channel bias and the runtime applies a plain bias-add then ReLU. The head conv (1x1, trunk to head channels), global average pool, and final `headChannels -> 1` dense are applied in sequence; the dense output is returned as-is.

### Reference parameter counts

These are the documented sizes for the residual-tower variants. The fused, serialized `.bin` (batch norm folded into bias) is slightly smaller than the unfused training graph.

| Variant (blocks x channels) | Training params | Fused `.bin` params |
| --- | --- | --- |
| 6 x 64 (baseline) | 458,273 | 457,409 |
| 8 x 96 | 1,351,681 | 1,350,017 |
| 10 x 128 | 2,982,881 | 2,980,161 |

Larger variants are only worth using if the `6 x 64` baseline underfits. `Network.info()` reports the actual `parameterCount` it decoded from a given file.

## Output and interpretation

`Network.Prediction` is a record around a single `float logit`:

- `logit()` — the raw scalar output of the final dense layer.
- `probability()` — `sigmoid(logit)`, a value in `[0, 1]`.
- `isPositive()` — the default decision at threshold 0 (`logit >= 0`, equivalently probability `>= 0.5`).

There are no auxiliary heads. A model trained on `is_puzzle` reads as: high probability means "likely a puzzle / tactic", low probability means "likely a quiet or non-puzzle position".

## Binary weights format (`.bin`)

Weights use a little-endian, LC0-like container that stores the classifier-specific architecture. Files are read whole, parsed strictly, and rejected if any trailing bytes remain.

Header (after the 4-byte magic):

| Field | Type | Meaning |
| --- | --- | --- |
| magic | 4 bytes | One of `CLSF` (preferred), `PCLS`, `PCJ0`, or `LC0J` (compat) |
| version | int32 | Must be `1` |
| inputChannels | int32 | `21` for the documented encoder |
| trunkChannels | int32 | Trunk width (64 for the baseline) |
| residualBlocks | int32 | Number of residual blocks |
| headChannels | int32 | Head conv width (32 for the baseline) |
| outputSize | int32 | Number of logits (`1`) |

The body is then read in this fixed order, with each conv stored as `outChannels, inChannels, kernel`, then a length-prefixed weight array, then a length-prefixed bias array:

1. Input stem conv (`21 -> trunkChannels`, kernel 3).
2. For each residual block: conv1 then conv2 (`trunk -> trunk`, kernel 3).
3. Head conv (`trunk -> headChannels`, kernel 1).
4. Final dense layer: `outDim, inDim`, weights, bias (`headChannels -> outputSize`).

The loader validates every shape against the header (input/trunk/head channel counts, dense input dimension equal to `headChannels`, dense output equal to `outputSize`) and throws a clear `IOException` on mismatch. The preferred magic is `CLSF`; the others exist only so early puzzle-classifier exports keep loading.

How this differs from the full LC0 CNN format:

- Input layer expects `21 x 8 x 8`, not the LC0 input stack.
- There is no policy head.
- There is no WDL/value head split.
- The output is one scalar logit instead of policy logits plus WDL.

The Java package is named `chess.nn.classifier` (Java identifiers cannot contain hyphens); the on-disk filename convention does use hyphens, e.g. `classifier_21planes-6blocksx64-head32-logit1.bin`.

## Loading and running a model

`Model` is the simplest entry point. `Model.DEFAULT_WEIGHTS` points at `models/classifier_21planes-6blocksx64-head32-logit1.bin`.

```java
import chess.core.Position;
import chess.nn.classifier.Model;
import chess.nn.classifier.Network;

try (Model model = Model.loadDefault()) {            // or Model.load(Path.of(...))
    Position position = Position.fromFen(fen);
    Network.Prediction p = model.predict(position);  // encodes + runs inference
    float logit = p.logit();
    float probability = p.probability();             // sigmoid(logit)
    boolean positive = p.isPositive();               // logit >= 0
}
```

`Model.predict(Position)` calls `Encoder.encode` and checks the encoded length against the loaded model's `inputChannels * 64` before inference, so a mismatched encoder/model pairing fails fast. `Model.predictEncoded(float[])` accepts pre-encoded planes for batch pipelines. The active backend is reported by `Model.backend()` and is `cpu` for this pure-Java implementation. `Model` (and `Network`) are `AutoCloseable`; closing clears the thread-local inference workspace.

## Producing training data: `record dataset classifier`

The classifier is trained on tensors exported from crtk `.record` dumps. The exporter writes the **identical** 21-plane encoding the network consumes at inference time, so there is no train/serve skew.

```bash
crtk record dataset classifier --input dump/ --output training/run --recursive
```

Inputs can be one or more `.record` files or directories (with `--recursive` to descend into them); records may be JSONL (one object per line) or a top-level JSON array. The command writes three NumPy/JSON artifacts next to the output stem:

| Artifact | Contents |
| --- | --- |
| `<stem>.classifier.inputs.npy` | 2-D `float32` matrix, one row per sample, `21 * 64 = 1344` columns, channel-major `a1..h8`, side-to-move oriented |
| `<stem>.classifier.labels.npy` | 1-D `float32` vector: `1.0` positive, `0.0` negative |
| `<stem>.classifier.meta.json` | Encoding metadata, counts, label definition, and a recommended `pos_weight` for imbalance |

Useful options (see `crtk help record dataset classifier` for the full list):

| Option | Effect |
| --- | --- |
| `--input` / `-i` | Input record file(s) or directories |
| `--output` / `-o` | Output prefix (default `dump/<input-stem>.classifier`) |
| `--filter` / `-f` | Row-selection Filter DSL applied before labeling |
| `--label-filter` | Positive-label Filter DSL; overrides the default `kind`-based labeling |
| `--max-positives` | Cap positive rows written |
| `--max-negatives` | Cap negative rows written |
| `--recursive` | Recurse into input directories |
| `--verbose` / `-v` | Print a stack trace on failure |

Labeling logic: if `--label-filter` is given, a record is positive when its analysis matches that Filter DSL. Otherwise the exporter uses the record's `kind` field (`puzzle` is positive; `nonpuzzle`/`non-puzzle` is negative), falling back to a configured filter when `kind` is absent. Records without a usable position or label are counted as skipped, never silently mislabeled. The `--max-positives`/`--max-negatives` caps let you build a balanced subset and stop streaming early once both caps are hit. For the Filter DSL itself and how puzzle records are produced, see [Use Cases](use-cases.md) and the puzzle-mining workflow (`puzzle mine`).

The `meta.json` records the encoder name (`classifier-21planes`), the `[rows, 1344]` shape, `side_to_move_perspective: true`, the positive-class definition, and `recommended_pos_weight` (negatives / positives) so a trainer can compensate for class imbalance. The typical mined corpus is heavily imbalanced (far more non-puzzle than puzzle positions), so weighted binary cross-entropy or focal loss and PR-AUC / recall-at-fixed-precision evaluation are recommended.

## Relationship to `engine eval`

`engine eval` and `engine static` select among crtk's position evaluators — `auto`, `lc0`, `otis`, and `classical` (`crtk help engine eval`). The binary classifier is **not** one of those evaluator modes: it answers a binary classification question, not a centipawn/WDL position evaluation, so it is exposed through the `chess.nn.classifier` API (`Model`/`Network`) and the `record dataset classifier` training pipeline rather than as an `--evaluator` choice. If you want a numeric position score from the command line, use `engine eval`/`engine static`; if you want a per-position binary score from a trained classifier, load it through `Model`. See [Command Reference](command-reference.md) for the evaluator-backed commands.

## Determinism and threading

- The encoder and forward pass are fully deterministic for a given `.bin` and input position.
- Channel-parallel convolutions are an optimization only; results do not depend on the configured thread count.
- `Network`/`Model` use thread-local workspaces, so a single loaded model can be shared across worker threads for batch scoring; close it to release per-thread scratch buffers.

## Summary

The `classifier` package is a compact, deterministic LC0-family residual CNN with a 21-plane current-position input and a single-logit binary head. It loads from an LC0-like little-endian `.bin`, runs on a pure-Java CPU backend, and shares its `Encoder` with `record dataset classifier` so training and inference encodings match exactly. It is crtk's own classifier — usable and reproducible, but not LC0-equivalent — and it complements, rather than replaces, the centipawn/WDL evaluators behind `engine eval`.
