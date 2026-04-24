# FAQ

## What is ChessRTK?

ChessRTK is a Java 17 command-line toolkit for chess research, automation,
engine workflows, puzzle mining, dataset export, rendering, and book
publishing.

## Is this a chess engine?

Partly. ChessRTK includes `engine builtin`, a small in-process Java alpha-beta
searcher, but the project is broader than an engine. It also provides FEN,
move, perft, record, dataset, rendering, GUI, and publishing tools.

For strength-sensitive analysis, use an external UCI engine through
`engine analyze`, `engine bestmove`, or `puzzle mine`.

## Do I need Stockfish or LC0?

No for core commands. These run in-process:

- `fen normalize`, `fen validate`, `fen print`
- `move list`, `move to-san`, `move to-uci`, `move after`, `move play`
- `engine perft`, `engine perft-suite`
- `engine static`
- `engine builtin --classical`

You need a UCI engine for UCI-backed analysis, best-move, threat, smoke-test,
and mining workflows.

## Why not use Maven or Gradle?

The repo is intentionally build-tool light. A JDK is enough:

```bash
javac --release 17 -d out $(find src -name "*.java")
```

The regression runner wraps the common local checks.

## Where do model files go?

Put local model files under `models/`. They are intentionally ignored by git.
Use `./install.sh --models` for the default model artifacts, or pass explicit
paths with options such as `--weights` and `--model`.

## What is a `.record` file?

It is ChessRTK's reusable analysis-record JSON shape. Record commands can
filter, merge, summarize, export, and convert those records into PGN, CSV,
JSONL, NumPy tensors, LC0-style tensors, classifier tensors, and diagnostics.

## How do I know move generation is correct?

Run:

```bash
crtk engine perft-suite --depth 6 --threads 4
```

The suite compares stored reference positions against the Java move generator
and reports whether every calculated node count matches the expected truth.

## What should I run before opening a PR or publishing a change?

```bash
./scripts/run_regression_suite.sh recommended
```

If you touched move generation, FEN, SAN, Chess960, or make/undo logic, also run
a deeper perft smoke pass:

```bash
CRTK_PERFT_SUITE_DEPTH=6 CRTK_PERFT_THREADS=4 ./scripts/run_regression_suite.sh perft-smoke
```

## Where should generated files go?

Use local artifact directories such as `dump/`, `data/`, `dist/`, `models/`,
and `out/`. Do not commit generated PDFs, model weights, build output, or large
analysis dumps unless a specific small fixture is required by a regression test.
