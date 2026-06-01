# LC0 Networks in ChessRTK

There are two ways to put a Leela Chess Zero network to work here, and they answer different questions. Run upstream `lc0` as an **external UCI engine** against its own `.pb.gz` weights when you want LC0 itself, exactly. Or use the **built-in Java LC0 CNN evaluator**, which loads compact ChessRTK `.bin` weights and runs policy/value inference in-process on the CPU or an optional native GPU backend. The in-process evaluator sits on the same deterministic chess core as everything else, so the position you score (`engine eval --lc0`), search (`engine builtin --lc0`), visualize in the Workbench, or export as training tensors (`record dataset lc0`) is one and the same object. The catch worth stating up front: these are honest, usable evaluators, **not** bit-exact reproductions of upstream LC0. The transformer (BT4) path is more experimental still. When fidelity is the point, run real `lc0`.

What follows: the CNN evaluator and its 112-plane input, the policy and value heads, the CPU and GPU backends, LC0 as an external engine, and dataset export. See [Configuration](configuration.md) for engine protocols, [In-House Engine](in-house-engine.md) for the built-in MCTS, and [AI Agents](ai-agents.md) for the evaluator overview.

## Two LC0 Workflows at a Glance

| Workflow | Weights | Entry points | Fidelity |
| --- | --- | --- | --- |
| External UCI engine | upstream LC0 `.pb.gz` | `engine analyze`, `engine bestmove`, `engine threats`, `engine compare`, `puzzle mine` | Full upstream LC0 (it *is* LC0) |
| Java LC0 CNN evaluator | ChessRTK `.bin` | `engine eval --lc0`, `engine builtin --lc0`, `fen display --ablation`, `record dataset lc0` | Usable; not bit-exact |
| Java LC0 BT4 (transformer) | ChessRTK BT4 `.bin` | backend checks / integration only | Simplified, experimental |

Model weights are not committed to the repository. Local `models/*.bin`, `models/*.pb.gz`, and `models/*.nnue` files are gitignored. Fetch the defaults with `./install.sh --models`, or download them manually as described in `models/README.md`.

## Java LC0 CNN Evaluator

![LC0 CNN evaluator flow](../assets/diagrams/crtk-lc0-cnn.png)

The CNN evaluator lives under `src/chess/nn/lc0/cnn/`: a forward pass over a Leela-style convolutional residual network with squeeze-and-excitation (SE) blocks, a policy head, and a WDL value head, written in plain Java with no native dependency. Sharing the one chess core buys something useful here — the `Position` you analyze, search, or render becomes the exact tensor the network sees, so the same input always produces the same output.

The default ChessRTK LC0 CNN weights file is:

```text
models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin
```

The name spells out the shape: 112-plane input, 10 residual blocks of 128 trunk channels, an 80-channel policy head emitting 4,672 raw move logits, and a 32-channel value head emitting 3 WDL outputs. A heavier `30blocksx384` network ships alongside it. `chess.nn.lc0.cnn.Model.DEFAULT_WEIGHTS` defines the default path.

### Evaluate a Position

`engine eval` runs one forward pass and prints the value and a policy summary. Choose the LC0 backend with `--lc0` and point `--weights` at a ChessRTK `.bin` file:

```bash
java -jar crtk.jar engine eval --fen "<FEN>" --lc0 \
  --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin
```

The evaluator modes are `auto`, `lc0`, `otis`, and `classical` (default `auto`), and `--lc0`, `--otis`, and `--classical` are shortcuts for them. `--terminal-aware` folds mate and stalemate detection into the score, which matters when the network's opinion of a dead position is beside the point. For a classical-only baseline that needs no weights at all, reach for `engine static`.

### Drive the Built-in MCTS Search

The same network can serve as the policy/value oracle inside the built-in MCTS engine. `engine builtin` accepts `--evaluator classical|nnue|lc0|otis` (default `classical`), with `--lc0` as a shortcut:

```bash
java -jar crtk.jar engine builtin --fen "<FEN>" --lc0 \
  --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin \
  --depth 2 --format summary
```

`engine builtin` exposes `--max-nodes` (the MCTS playout budget; `0` removes the cap), `--max-duration` (e.g. `5s`), and `--format uci-info|uci|san|both|summary`. See [In-House Engine](in-house-engine.md) for how the search uses them. This evaluator also backs the Workbench Play tab's `CNN` opponent, and if the chosen model fails to load, Play quietly drops to classical evaluation rather than refusing to play.

### Inspect the Evaluator on the Board

To see what the evaluator is reacting to, `fen display` can overlay its per-square ablation heatmap and name the backend that produced it:

```bash
java -jar crtk.jar fen display --fen "<FEN>" --ablation --show-backend
```

`fen render` takes the same `--ablation` and `--show-backend` flags but writes the overlay to an image file instead of a window.

## Input Planes (112 x 64)

The encoder (`chess.nn.lc0.cnn.Encoder`) flattens a `Position` into a `float[112 * 64]` tensor in **channel-major** order: the first 64 values are plane 0, the next 64 plane 1, and so on. Within a plane, squares run `a1..h1, a2..h2, ..., a8..h8`. Every plane is written from the **side-to-move perspective**, so when Black is to move the board is rank-mirrored and colors are swapped — "our" pieces always sit nearest rank 1. That is LC0's convention, and copying it is what lets LC0-trained weights mean anything here.

The 112 planes break down this way:

| Planes | Meaning |
| --- | --- |
| 0..103 | 8 history blocks of 13 planes each: 6 "our" pieces (P,N,B,R,Q,K), 6 "their" pieces, plus 1 repetition plane |
| 104..107 | Castling rights: we-queenside, we-kingside, they-queenside, they-kingside |
| 108 | Side-to-move flag (all ones when Black is to move) |
| 109 | Rule-50 / halfmove clock as a constant-valued plane |
| 110 | Unused (all zeros) |
| 111 | Edge plane (all ones) |

> **Fidelity note.** `Position` carries no move history and no repetition state, so all eight history blocks hold copies of the current board and the repetition planes stay zero. That is the deliberate compromise: the layout is a faithful LC0 *classical 112-plane* encoding of a single position, but the temporal and repetition signal a real self-play input would carry is simply absent. The network can't see what it was never given, which is why evaluations come out reasonable rather than bit-identical to upstream LC0.

## Policy and Value Heads

The trunk splits into two heads, following LC0's structure.

**Policy head.** A policy stem and an output convolution produce raw move planes — 4,672 logits for the default network. The weights file carries a `policyMap` that gathers those raw planes into the compressed LC0 move-logit vector, so downstream code sees standard LC0-ordered move probabilities and never has to know about the raw plane layout. Out-of-range map indices count as zero.

**Value head.** A value convolution feeds two dense layers and a softmax, yielding three **WDL** probabilities ordered `[win, draw, loss]` from the side-to-move perspective. The scalar the evaluator reports is `win - loss`, so positive favors the side to move.

**SE blocks.** A residual block may carry a squeeze-and-excitation unit: global-average-pool each channel, run it through a small two-layer gate, then use the result to re-scale and shift the trunk activations before the residual add and ReLU. Whether a given block has one is read from the weights file, block by block.

Along the way the forward pass captures named intermediate activations — stem, per-block ReLU, policy hidden/planes/logits, value conv/fc1/logits, WDL, scalar — which the Workbench neural-net visualizers draw.

## Backends: CPU and GPU

Out of the box the evaluator runs on a deterministic CPU backend with no native code behind it. Optional JNI backends offload inference to a GPU when you have one. JVM system properties decide which is used:

| Property | Values | Effect |
| --- | --- | --- |
| `-Dcrtk.lc0.backend` | `auto`, `cpu`, `cuda`, `rocm`, `amd`, `hip`, `oneapi`, `intel` | Force the CNN backend (default `auto`) |
| `-Dcrtk.lc0.threads` | `N` | CPU-backend worker threads |
| `-Dcrtk.lc0.bt4.backend` | same as `crtk.lc0.backend` | Force the BT4 backend separately |
| `-Djava.library.path` | path | Where the JVM finds the native libraries |

`auto` tries an available native backend and falls back to the CPU evaluator if none loads — the GPU is a bonus, never a requirement. The shared libraries are `liblc0_cuda.so`, `liblc0_rocm.so`, and `liblc0_oneapi.so`.

Check what's actually available:

```bash
java -jar crtk.jar engine gpu
```

`engine gpu` reports OTIS and native perft backend status too; the LC0 rows are the ones the `crtk.lc0.*` properties control.

### Building a Native Backend

Each backend is its own CMake project. The CUDA one, for instance:

```bash
cmake -S native/cuda -B native/cuda/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/cuda/build -j
```

Then run with the library on `java.library.path` and the backend pinned:

```bash
java -cp out \
  -Djava.library.path=native/cuda/build \
  -Dcrtk.lc0.backend=cuda \
  application.Main fen display --fen "<FEN>" --ablation --show-backend
```

See `native/cuda/README.md`, `native/rocm/README.md`, and `native/oneapi/README.md` for backend-specific build notes, and [Build and Install](build-and-install.md) for the overall build flow.

## LC0 as an External UCI Engine

When you need LC0 to behave exactly as LC0, point an engine protocol at the real `lc0` binary and let crtk drive it over UCI. This is the path to take for the official BT4 transformer and for any analysis where the answer has to be trustworthy rather than merely fast. The installer's default model set includes the upstream network:

```text
models/BT4-1024x15x32h-swa-6147500-policytune-332.pb.gz
```

That is an LC0 protobuf-gzip file, not a ChessRTK `.bin` — `lc0` loads it through its own `WeightsFile` option, set in your engine protocol TOML:

```toml
setup = [
  "setoption name WeightsFile value models/BT4-1024x15x32h-swa-6147500-policytune-332.pb.gz",
]
```

Once the protocol resolves to `lc0`, the ordinary engine commands apply (`--protocol-path` selects a specific protocol):

```bash
java -jar crtk.jar engine uci-smoke --nodes 1 --max-duration 5s
java -jar crtk.jar engine analyze --fen "<FEN>" --max-duration 10s --multipv 3
java -jar crtk.jar engine bestmove --fen "<FEN>" --format both --max-duration 5s
java -jar crtk.jar puzzle mine --input seeds.txt --output dump/lc0.json --engine-instances 2
```

`engine compare` pits an LC0 protocol against another UCI engine via `--left-protocol` / `--right-protocol`. See [Configuration](configuration.md) for protocol TOML structure and [Use Cases](use-cases.md) for mining workflows.

## LC0 Dataset Export

`record dataset lc0` turns a `.record` JSON file into LC0-format training tensors. Pass `--weights` to supply an LC0 CNN network, and policy targets get compressed against the very `policyMap` the evaluator uses — so the targets you train on line up with the moves the network can express:

```bash
java -jar crtk.jar record dataset lc0 --input dump/run.record.json \
  --output dump/run.lc0 \
  --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin
```

| Option | Meaning |
| --- | --- |
| `--input` / `-i` | Input `.record` JSON file |
| `--output` / `-o` | Output dataset prefix (default `dump/<input-stem>.lc0`) |
| `--weights` | Optional LC0 weights for policy-map compression |

Sibling exporters cover the other formats: `record dataset npy` (NPY tensors), `record dataset classifier` (classifier tensors), and the JSONL exporters under `record export` (`training-jsonl`, `puzzle-jsonl`, `puzzle-elo-jsonl`). See [Quality and Testing](quality-and-testing.md) and [Use Cases](use-cases.md) for end-to-end dataset pipelines.

## Java LC0 BT4 (Experimental)

An in-process take on LC0's attention-body **BT4** transformer lives under `src/chess/nn/lc0/bt4/` (with `src/chess/nn/lc0/bt4/ARCHITECTURE.md` alongside it). It models the `lc0-bt4-1024x15x32h` descriptor: 64 board tokens, 112 input planes, 1024 channels, 15 post-layer-norm encoder blocks, 32 attention heads, attention-policy compression to 1,858 logits, and a WDL value head ordered `[win, draw, loss]` with scalar `win - loss`.

Treat this one as a work in progress: **experimental and not bit-exact** with upstream LC0. The loader reads a compact ChessRTK `.bin` integration format (`magic BT4J`, little-endian, version 1) that is intentionally far simpler than LC0's protobuf — it exists for exporter tests, correctness checks, and crtk-side integration work, not as a lossless image of upstream weights. It runs on the CPU reference path or, as with the CNN, on optional CUDA/ROCm/oneAPI backends (`crtk.lc0.bt4.backend`, falling back to `crtk.lc0.backend`). For BT4 inference you can actually rely on, use the official `.pb.gz` network through the external LC0 UCI engine described above.
