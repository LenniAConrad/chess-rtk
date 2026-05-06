# Models (Optional)

Model binaries are not stored in this repository. Files downloaded into this
directory are local artifacts and `*.bin`, `*.pb.gz`, and `*.nnue` weights are
ignored by git.

`crtk engine builtin --nnue` looks for `models/crtk-halfkp.nnue` by default.
If that file is absent, the command fails with a clear error instead of silently
using a neutral smoke-test network. Provide `--weights <file>` to use a real
CRTK or supported Stockfish NNUE network. The low-level Java NNUE API still
exposes an in-memory fallback for isolated smoke tests.

The installer can fetch the default NNUE, LC0 CNN, and official LC0 BT4
weights:

```bash
./install.sh --models
```

You can also download files manually from
[`LenniAConrad/chess-models`](https://github.com/LenniAConrad/chess-models)
and place them in this directory.

## Available Models

Compact ChessRTK `.bin` files use this filename convention:

```text
<model>_<input>planes-<blocks>blocksx<trunkChannels>-<headSpecs>-<outputSpecs>.bin
```

| Local file | Architecture | Source |
| --- | --- | --- |
| `crtk-halfkp.nnue` | Local default for `engine builtin --nnue`; currently installed from the official Stockfish big NNUE `nn-f68ec79f0fe3.nnue` | [Download](https://github.com/LenniAConrad/chess-models/blob/main/models/nn-f68ec79f0fe3.nnue) |
| `leela_112planes-30blocksx384-policyhead80-valuehead32-policy4672-wdl3.bin` | 112 input planes, 30 residual blocks x 384 channels, 80-channel policy head, 32-channel value head, 4672 policy logits, 3 WDL outputs | [Download](https://github.com/LenniAConrad/chess-models/blob/main/models/lc0_610153.bin) |
| `leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin` | 112 input planes, 10 residual blocks x 128 channels, 80-channel policy head, 32-channel value head, 4672 policy logits, 3 WDL outputs | [Download](https://github.com/LenniAConrad/chess-models/blob/main/models/lc0_744706.bin) |
| `BT4-1024x15x32h-swa-6147500-policytune-332.pb.gz` | Official LCZero BT4 transformer network, 1024 embedding size, 15 encoder blocks, 32 attention heads, LC0 protobuf gzip format; SHA-256 `e6ada9d6c4a769bfab3aa0848d82caeb809aa45f83e6c605fc58a31d21bdd618` | [Download](https://storage.lczero.org/files/networks-contrib/BT4-1024x15x32h-swa-6147500-policytune-332.pb.gz) |
| `classifier_21planes-6blocksx64-head32-logit1.bin` | 21 input planes, 6 residual blocks x 64 channels, 32-channel classifier head, 1 output logit | Local smoke-test weights |

The BT4 network is kept under its upstream LCZero filename. Use it with LC0 as a
UCI engine through `WeightsFile`, or keep it in `models/` as the local archived
copy downloaded by `./install.sh --models`.

For model details, see the upstream
[`models/README.md`](https://github.com/LenniAConrad/chess-models/blob/main/models/README.md).

Expected local layout:

```text
models/
  crtk-halfkp.nnue
  leela_112planes-30blocksx384-policyhead80-valuehead32-policy4672-wdl3.bin
  leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin
  BT4-1024x15x32h-swa-6147500-policytune-332.pb.gz
  classifier_21planes-6blocksx64-head32-logit1.bin
```
