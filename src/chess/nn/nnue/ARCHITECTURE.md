# `nnue` Architecture

`chess.nn.nnue` contains pure-Java NNUE evaluators. They are intentionally
isolated from the default `chess.eval.Evaluator` path so adding the
implementation does not change existing CLI evaluation behavior.

Two loaders are available:

- `Network.load(Path)` reads the compact CRTK format described below.
- `Model.load(Path)` auto-detects either the CRTK format or supported Stockfish
  `.nnue` files.

## Input

The feature encoder uses a HalfKP-style sparse input:

```text
64 king buckets x 10 relative piece planes x 64 piece squares = 40,960 features
```

For each perspective, the active features are:

- own pawn, knight, bishop, rook, queen
- enemy pawn, knight, bishop, rook, queen

Kings are not emitted as piece features. They select the king bucket instead.
Squares use `a1..h8` order. Black perspective is rank-mirrored so Black's home
rank maps to rank 1.

## Forward Pass

The forward pass is:

```text
feature bias + sum(active feature weights) -> White accumulator
feature bias + sum(active feature weights) -> Black accumulator
clipped ReLU(accumulator)
linear([side-to-move accumulator, opponent accumulator]) -> centipawns
```

`Accumulator` exposes add/remove/replace feature operations so a searcher can
update hidden sums incrementally after a move instead of rebuilding from the
full board.

## Binary Format

The loader accepts a simple little-endian CRTK format:

```text
char[4] magic = "NNUE"
int32 version = 1
int32 featureCount = 40960
int32 hiddenSize
float32 outputScale
float32[] featureBias      length hiddenSize
float32[] featureWeights   length featureCount * hiddenSize, feature-major
float32[] outputWeights    length hiddenSize * 2, [us, them]
float32 outputBias
```

Arrays are length-prefixed with `int32`, matching the style used by other Java
model loaders in this repo.

## Stockfish `.nnue`

`StockfishNnueNetwork` reads official Stockfish NNUE files with version
`0x7AF32F20`. The implementation is scalar Java and evaluates directly from a
`Position`; it does not rely on Stockfish native code.

Supported Stockfish layouts:

- Stockfish 18 `SFNNv10`, big and small nets.
- Current Stockfish `SFNNv13` style, big and small nets.

Implemented feature sets:

- `HalfKAv2_hm` PSQ features.
- `FullThreats` big-net threat features.

Implemented network body:

```text
FeatureTransformer -> AffineSparseInput -> SqrClippedReLU/ClippedReLU
                   -> Affine -> ClippedReLU -> Affine -> centipawns
```

Small-net architecture hashes overlap between the supported Stockfish variants.
`StockfishNnueNetwork.load(path)` resolves known default Stockfish filenames and
otherwise defaults to the current layout. Use
`StockfishNnueNetwork.load(path, Variant.SF_18)` when loading a Stockfish 18
small net from a renamed file.

The Stockfish loader is intended for inference. Incremental accumulator update
APIs on `Model` are only available for the compact CRTK format.
