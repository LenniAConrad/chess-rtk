# Models Directory

This is where ChessRTK ("crtk") looks for neural-network weight files: NNUE, LC0 CNN, the experimental BT4 transformer, OTIS, and the T5 text model. **No weight files are tracked in git** — everything in here is a local artifact you download or generate. crtk's CLI and the Workbench resolve model paths against this directory, so dropping the right files in place is usually all the setup a neural backend needs. This page covers which files live here, the naming convention, how to obtain them, and how commands point at them with `--weights`.

> crtk's networks are usable, deterministic evaluators — not bit-exact clones of upstream engines. The LC0 CNN path is a faithful but simplified reimplementation, and the BT4 transformer path is experimental. For production-grade LC0/Stockfish play, run those engines as external UCI backends instead (see [LC0 backend](lc0.md) and [Configuration](configuration.md)).

## Why nothing here is in git

Weight files are large binaries and most are redistributable only under their own upstream terms, so they are excluded from version control by design.

- `.gitignore` ignores `models/*.bin`, `*.pb`, `*.pb.gz`, `*.onnx`, `*.nnue`, `*.pt`, `*.pth`, `*.safetensors`, `*.part`, and the `nets/` scratch directory.
- `scripts/check_no_weights_tracked.sh` enforces this in CI: it fails if git is tracking anything under `nets/`, any `models/*.bin`, or any `*.pb` / `*.pb.gz` / `*.onnx` / `*.nnue` file. Run it yourself before committing.

```bash
./scripts/check_no_weights_tracked.sh
```

A clean checkout therefore has an empty `models/` directory (apart from this README). crtk runs without any weights at all — commands that need a missing model fail with a clear error, and the Workbench falls back to classical evaluation — so you only fetch the networks for the backends you actually want to use.

## What lives here

| Local file | Backend | What it is |
| --- | --- | --- |
| `crtk-halfkp.nnue` | NNUE | Default network for `engine builtin --nnue`. Distributed as the official Stockfish big NNUE `nn-fcf986aea78a.nnue`; a real CRTK or supported Stockfish HalfKP/HalfKA network also works. |
| `leela_112planes-30blocksx384-policyhead80-valuehead32-policy4672-wdl3.bin` | LC0 CNN | Larger compact ChessRTK CNN: 112 input planes, 30 residual blocks x 384 channels, policy + WDL value heads. |
| `leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin` | LC0 CNN | Smaller compact ChessRTK CNN (10 blocks x 128 channels). This is the default `lc0-model-path` in the shipped config. |
| `BT4-1024x15x32h-swa-6147500-policytune-332.pb.gz` | BT4 (experimental) | Official LCZero BT4 transformer, kept under its upstream filename in LC0 protobuf-gzip format. Use it with LC0 as a UCI engine, or convert it for crtk's experimental BT4 path. |
| `otis_policy_wdl_random.bin` | OTIS | Default OTIS policy/WDL network used by `engine eval --otis` and the Workbench Play `OTIS` mode. The shipped file is a randomized placeholder. |
| `puzzle-classifier_21planes-6blocksx64-head32-logit1.bin` | Classifier | Compact puzzle/position classifier head (21 planes, 6 blocks x 64 channels, single logit). Used as smoke-test weights for classifier dataset work. |
| `t5.bin` | T5 | Text model for natural-language summaries (`fen text`, `puzzle text`). Default `t5-model-path` in the shipped config. |

## Naming convention

Compact ChessRTK `.bin` networks encode their architecture in the filename so a file is self-describing:

```text
<model>_<input>planes-<blocks>blocksx<trunkChannels>-<headSpecs>-<outputSpecs>.bin
```

For example, `leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin` reads as: a Leela-style CNN with 112 input planes, 10 residual blocks of 128 trunk channels, an 80-channel policy head and 32-channel value head, 4672 policy logits, and 3 WDL outputs.

The BT4 transformer keeps its upstream LCZero filename verbatim (`BT4-1024x15x32h-swa-...pb.gz`) so it stays interchangeable with LC0.

## How to obtain the weights

### Option 1 — let the installer fetch them

The installer downloads the default NNUE, both LC0 CNN networks, and the official LC0 BT4 weights into this directory:

```bash
./install.sh --models
```

Pass `--no-models` to skip the download. If you decline, neural backends report a clear "weights missing" error until you add the files (classical evaluation keeps working).

### Option 2 — download manually

The compact `.bin` networks are published at [`LenniAConrad/chess-models`](https://github.com/LenniAConrad/chess-models). The BT4 protobuf comes straight from the LCZero network archive. Download the file you want and drop it into this directory under the local name from the table above.

### Option 3 — fetch any LC0 network with the helper script

`scripts/fetch_lc0_net.sh` downloads an LC0 network from a URL, optionally verifying its SHA-256, and prints the resulting path. It writes to `nets/` by default (gitignored); point `--out` at `models/` to land it here.

```bash
./scripts/fetch_lc0_net.sh \
  --url https://storage.lczero.org/files/networks-contrib/BT4-1024x15x32h-swa-6147500-policytune-332.pb.gz \
  --sha256 e6ada9d6c4a769bfab3aa0848d82caeb809aa45f83e6c605fc58a31d21bdd618 \
  --out models
```

### Option 4 — convert a BT4 protobuf to a crtk `.bin`

To run a BT4 network through crtk's own (experimental) loader rather than via external LC0, convert the `.pb.gz` to a version-2 `.bin` with `scripts/convert_lc0_bt4_to_bin.py`. It needs the Python `protobuf` package and the `protoc` compiler.

```bash
./scripts/convert_lc0_bt4_to_bin.py \
  --in  models/BT4-1024x15x32h-swa-6147500-policytune-332.pb.gz \
  --out models/bt4-1024x15x32h.bin
```

Useful flags: `--input-format` (`CLASSICAL_112`, `CASTLING_PLANE_112`, `BT4_CANONICAL_112`), `--policy-head` (`vanilla`, `optimistic_st`, `soft`, `opponent`), and `--value-head` (`winner`, `q`, `st`). The converted file is usable but **not** bit-equivalent to LC0 inference; treat the crtk BT4 path as experimental.

## How commands reference models

Most neural backends pick up a sensible default from this directory, and every one accepts an explicit `--weights <file>` (or a config key) so you can point at any network you like. Defaults are resolved relative to the project root, so a path like `models/crtk-halfkp.nnue` just works.

| Command | Default weights | Override |
| --- | --- | --- |
| `engine builtin --nnue` | `models/crtk-halfkp.nnue` | `--weights <file>` |
| `engine builtin --lc0` / `engine builtin --otis` | LC0 from `lc0-model-path`; OTIS placeholder | `--weights <file>` |
| `engine eval --lc0` / `engine eval --otis` | `lc0-model-path` (config); OTIS `models/otis_policy_wdl_random.bin` | `--weights <file>` |
| `fen text` / `puzzle text` | `t5-model-path` (config, `models/t5.bin`) | `--model <file>` |
| `record dataset lc0` | none (classical mapping) | `--weights <file>` for policy-map compression |
| `record export puzzle-jsonl` | none | `--weights <file>` for LC0 CNN scoring |

A few concrete examples:

```bash
# Built-in MCTS with the default NNUE network
java -jar crtk.jar engine builtin --nnue --startpos

# Built-in MCTS with an explicit LC0 CNN file
java -jar crtk.jar engine builtin --lc0 \
  --weights models/leela_112planes-30blocksx384-policyhead80-valuehead32-policy4672-wdl3.bin \
  --startpos

# One-shot OTIS evaluation of a position
java -jar crtk.jar engine eval --otis --weights models/otis_policy_wdl_random.bin \
  --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

# Natural-language summary with a T5 model
java -jar crtk.jar fen text --model models/t5.bin --startpos
```

The shipped configuration sets `lc0-model-path` and `t5-model-path`; see [Configuration](configuration.md) to change the defaults. For UCI-style LC0 play (including the BT4 protobuf) configure it as an external engine instead of using crtk's in-process loader — details in [LC0 backend](lc0.md).

> If a model selected in the Workbench Play tab cannot be loaded, the GUI falls back to classical evaluation so the game still starts. The Play tab reads model paths from this same directory.

## Expected local layout

After `./install.sh --models` (plus any conversions you run), a populated directory looks like:

```text
models/
  crtk-halfkp.nnue
  leela_112planes-30blocksx384-policyhead80-valuehead32-policy4672-wdl3.bin
  leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin
  BT4-1024x15x32h-swa-6147500-policytune-332.pb.gz
  bt4-1024x15x32h.bin            # optional: from convert_lc0_bt4_to_bin.py
  otis_policy_wdl_random.bin
  puzzle-classifier_21planes-6blocksx64-head32-logit1.bin
  t5.bin                         # optional: for fen text / puzzle text
```

## Verify your setup

`doctor` checks Java, config, protocol, engines, and local artifacts in one pass — a good way to confirm the right weights are visible to crtk:

```bash
java -jar crtk.jar doctor
```

## See also

- [Configuration](configuration.md) — config keys including `lc0-model-path` and `t5-model-path`.
- [LC0 backend](lc0.md) — running LC0 / BT4 as an external UCI engine.
- [In-house engine](in-house-engine.md) — the built-in MCTS search and its evaluators.
- [Build and install](build-and-install.md) — installer flags including `--models`.
- [Getting started](getting-started.md) — first commands to try.
