# BT4 Attention-Body Network Architecture

The Java package `chess.nn.lc0.bt4` implements a from-scratch, deterministic
evaluator for Leela Chess Zero "BT4"-style attention-body transformer networks:
a 64-token, 112-plane transformer with a smolgen attention-bias generator, an
attention policy head, and a WDL value head. It plugs into the same shared
ChessRTK chess core as every other tool, runs on a pure-Java CPU reference path
or on optional CUDA / ROCm / oneAPI native backends, and reads a compact CRTK
`.bin` weights file. This page documents the architecture as built today.

> The BT4 path is **simplified and experimental**. It is a usable
> policy/value evaluator, but it is **not bit-equivalent** to upstream LC0/BT4
> inference. Read [Known Limitations](#known-limitations) before treating its
> outputs as ground truth. For honest fidelity guidance across all networks,
> see [LC0 networks](lc0.md) and [In-house engine](in-house-engine.md).

## What this package is

- A transformer evaluator for the public BT4 family, identified by the default descriptor `lc0-bt4-1024x15x32h`.
- A deterministic CPU reference forward pass plus optional GPU acceleration with automatic CPU fallback.
- A loader for a self-describing CRTK BT4 `.bin` format that stores already-decoded float32 tensors (it is **not** LC0's protobuf).
- A policy mapper that gathers the network's internal logits into LC0's standard 1858-entry attention-policy vector.
- A search backend (`chess.engine.Bt4Backend`) that supplies policy priors and WDL values to the built-in MCTS, used by the desktop [Workbench](workbench.md) play-vs-engine mode and neural-net visualizers.

## Default architecture

The reference architecture `Architecture.BT4_1024X15X32H` captures the full
LC0-faithful configuration. Architecture metadata is a validated `record`, so
malformed shapes are rejected at load time.

| Property | Value |
| --- | --- |
| Descriptor name | `lc0-bt4-1024x15x32h` |
| Board tokens | 64 (one per square) |
| Input planes | 112 |
| Model width (embedding) | 1024 |
| Encoder layers | 15 |
| Attention heads | 32 |
| FFN hidden width | 1536 |
| Policy output | 1858 compressed logits |
| Value output | WDL (win, draw, loss) |
| Input format | `BT4_CANONICAL_112` |
| Input embedding | `PE_DENSE` |
| Default activation | Mish |
| FFN activation | Mish |
| Smolgen activation | Swish (SiLU) |
| Layer-norm epsilon | 1e-3 |

A `simplified(...)` factory produces lighter architectures with the extended
LC0 stack disabled (no input preproc, no embedding FFN, no input gates, no
smolgen). These predate the full stack and exist for smaller export and test
nets.

## Input encoding

`Encoder` builds LC0's 112-plane board representation and reinterprets it as 64
square tokens for the transformer body. The base layout is channel-major
`[112][64]`; the body transposes it into token-major `[64][112]`, one token per
square.

The 112 planes are 8 history slots of 13 planes each (12 piece planes plus a
repetition slot), followed by auxiliary planes. The encoder always works in
**side-to-move perspective**: when Black is to move, the board is mirrored so
the mover's pieces occupy the first six planes.

`InputFormat` selects how the auxiliary planes are filled:

| Format | Castling | En passant | Rule-50 | Canonical transform |
| --- | --- | --- | --- | --- |
| `CLASSICAL_112` | constant planes | side-to-move plane | raw count | none |
| `CASTLING_PLANE_112` | rook-location planes | side-to-move plane | raw count | none |
| `BT4_CANONICAL_112` | rook-location planes | en-passant target plane | hectoplies (`/100`) | LC0 spatial transform |

The default `BT4_CANONICAL_112` uses modern LC0 auxiliary planes: castling
rook-location planes (so Chess960 castling encodes correctly), an en-passant
target plane, the rule-50 counter scaled to hectoplies, a constant edge/ones
plane, and a deterministic LC0 canonical spatial transform. The canonical
transform applies the flip / mirror / transpose bits LC0 uses to fold symmetric
positions, but **only when castling rights do not block it** (it is disabled
whenever any castling right is present). The chosen transform bits are returned
alongside the encoded planes so the policy mapper can map moves back to
unrotated squares.

### Input embedding strategies

The `Architecture.InputEmbedding` enum controls how per-token features reach the
transformer's input projection:

- `NONE` — the 112 planes are used directly as per-square token features.
- `PE_MAP` — a 64-way one-hot square vector is appended to each token (`64 x (112 + 64)`), so the following dense layer learns a positional embedding.
- `PE_DENSE` — the BT4 default: the leading input channels are routed through a learned per-square dense preprocessor (`ip_emb_preproc`) and concatenated before the main embedding projection.

## Transformer body

The body is a post-layer-norm encoder stack. Each of the 15 encoder blocks runs
multi-head self-attention with a residual add and layer norm, then a two-layer
feed-forward network (Mish activation) with a residual add and layer norm. The
input stack (preproc, embedding projection, embedding layer norm, optional
multiplicative/additive gates, optional embedding FFN) runs once before the
blocks.

### Smolgen attention bias

When the architecture enables smolgen, each attention layer adds a per-head,
data-dependent `[64 x 64]` bias to the attention scores before the softmax. The
per-block smolgen module compresses each token, projects through a shared
mid-width dense with a Swish activation and layer norm, then expands through a
globally shared `smolgenW` projection to produce the `tokens x tokens` bias for
every head. This mirrors LC0's smolgen design and is what lets the descriptor
claim BT4 fidelity at the structural level.

## Policy head

`PolicyEncoder` maps moves to LC0's attention-policy layout. The attention
policy first produces an internal `67 x 64 = 4288` logit tensor:

- `64 x 64` from-square / to-square logits for queen-like and knight moves.
- three underpromotion planes (knight, bishop, rook) over the relevant from/to files.

A geometry-derived gather map compresses those internal logits into LC0's
standard `1858`-entry policy vector, dropping the impossible from/to pairs. The
same map runs in reverse to score legal moves: `topLegalMoves(...)` looks up the
compressed index for each legal move (undoing the canonical transform), then
computes a softmax **over the legal moves only**, so the returned probabilities
sum to 1. Queen promotions reuse the queen-move from/to slot; only knight,
bishop, and rook promotions use the underpromotion planes.

## Value head

The value head returns WDL probabilities ordered `win, draw, loss`. The scalar
value reported in `Network.Prediction` is `win - loss` from the side-to-move
perspective.

## Backends and determinism

`Network.load(path)` selects a backend through the same fallback model as the
LC0 CNN evaluator:

1. Parse the backend preference. The BT4-specific system property `crtk.lc0.bt4.backend` takes priority; if unset it falls back to the shared `crtk.lc0.backend` property; if neither is set the mode is `auto`.
2. In `auto`, try CUDA, then ROCm, then oneAPI; use whichever native backend is available.
3. If no native backend is available (or the request is `cpu`), use the pure-Java CPU reference path.

Accepted backend tokens are `auto`, `cuda`, `rocm` (aliases `amd`, `hip`),
`oneapi` (alias `intel`), and `cpu`. Naming an explicit backend that is
unavailable (no JNI library loaded and/or no matching device) is a hard error
rather than a silent fallback. `Network.loadCpu(path)` forces the CPU path
without consulting any property or mutating JVM-wide state; the Workbench
activation visualizers use it because only the CPU path exposes the intermediate
tensors.

The CPU path is intentionally simple and batch-size-1 oriented, which keeps it a
clean, auditable reference. Like the rest of ChessRTK, a given build evaluates a
given position deterministically. See [Native GPU backends](architecture.md) and
[Build and install](build-and-install.md) for backend build details.

## CRTK BT4 binary format

`BinLoader` reads a compact little-endian CRTK BT4 `.bin` file. It is a
deliberately simple Java/native export target for correctness checks and
integration work, not a replacement for LC0's protobuf.

- Magic: ASCII `BT4J`.
- Version: `2` (adds the full BT4 input stack and the smolgen attention-bias generator over version 1).
- Architecture metadata (descriptor, input format, embedding strategy, dimensions, activations, feature flags).
- Input stack (optional preproc dense, embedding dense, layer-norm parameters, optional gates, optional embedding FFN).
- Encoder blocks (attention with optional smolgen, FFN, layer norms, activation, residual alpha).
- Shared smolgen projection (`smolgenW`) when smolgen is enabled.
- Attention policy head and WDL value head.

Dense layers are stored as row-major float32:

```text
inDim, outDim, weightCount, weights..., biasCount, bias...
```

The loader fails fast on a wrong magic, an unsupported version, malformed
tensor lengths, or unexpected trailing bytes, so a partially written file never
loads as a valid network.

## How to run BT4

A `.bin` model loaded by this package supplies policy priors and WDL values to
the built-in MCTS via `chess.engine.Bt4Backend` (constructed by
`Mcts.bt4(Path)`). Today the BT4 evaluator is reached **programmatically** and
through the desktop [Workbench](workbench.md): the Workbench loads it via
`Bt4SearchBackend`, drives its play-vs-engine mode, and renders the encoded
planes and captured encoder activations through its neural-net visualizers
(which use the CPU path, since only that path exposes intermediate tensors).

> The CLI `engine` evaluator selectors `--lc0` / `--evaluator lc0` currently
> route to the **LC0 CNN** evaluator, not to this BT4 path. Do not expect
> `engine eval --lc0 --weights <bt4.bin>` to load a BT4 `.bin` correctly. For
> the LC0 CNN evaluator and its `--weights` handling, see [LC0 networks](lc0.md);
> for the classical, non-network evaluator, use `engine static`. The general
> `engine` command surface is documented in
> [Command reference](command-reference.md) and the
> [Command cheat sheet](command-cheatsheet.md).

## Known limitations

These are real and current. Do not treat BT4 outputs as upstream-faithful.

- **Not bit-exact.** This is an independent reimplementation. It does not reproduce LC0/BT4 inference bit-for-bit, and it is not validated against upstream LC0 outputs. Treat it as a usable evaluator, not a reference oracle.
- **Input encoding is the main suspect.** The 112-plane encoding (perspective mirroring, auxiliary planes, and the LC0 canonical transform) is the most likely source of divergence from upstream. Discrepancies in observed evaluations most often trace back here.
- **Flat / weak policy in practice.** Converted weights have produced near-flat policy distributions and, in some positions, inverted WDL signs. The structure is faithful; the loaded values are not guaranteed to match a real BT4 net.
- **Lossy conversion.** There is no lossless, validated mapping from LC0's `pb.gz` protobuf to the CRTK `.bin` format. A faithful converter would require extending this package to cover every upstream tensor; the current `.bin` is a simplified target.
- **CPU path is batch-size-1.** The reference forward pass favors clarity over throughput. For large batches, prefer a native backend when one is available.
- **Simplified architectures exist.** Nets built via `simplified(...)` omit smolgen, input preproc, gates, and the embedding FFN, so their behavior differs structurally from the full `lc0-bt4-1024x15x32h` configuration.

## Related pages

- [LC0 networks](lc0.md) — the LC0 CNN evaluator and weights handling.
- [In-house engine](in-house-engine.md) — the built-in Java MCTS and evaluators.
- [Architecture](architecture.md) — the shared chess core and overall system design.
- [Command reference](command-reference.md) — full `engine` command surface.
- [Workbench](workbench.md) — desktop play-vs-engine and network visualizers.
