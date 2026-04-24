# Getting Started

This page gets a fresh checkout from source code to useful commands.

## Requirements

- Java 17+ JDK with `javac`
- Optional: `stockfish` or another UCI engine on `PATH`
- Optional: local model files under `models/` for NNUE, LC0, and T5 workflows

Core FEN, move, perft, and classical built-in search commands do not require an
external chess engine.

## Build From Source

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
java -cp out application.Main help
```

## Install The Launcher

```bash
./install.sh
crtk doctor
crtk help
```

If you do not install the launcher, use:

```bash
java -cp out application.Main <area> <action> [options]
```

## Verify The Checkout

```bash
./scripts/run_regression_suite.sh build
./scripts/run_regression_suite.sh lint
./scripts/run_regression_suite.sh core
```

Recommended broader pass:

```bash
./scripts/run_regression_suite.sh recommended
```

## Learn The Command Shape

ChessRTK commands use a noun group plus an action:

```bash
crtk <area> <action> [options] [args]
```

Examples:

```bash
crtk move list --startpos --format both
crtk engine bestmove --fen "<FEN>" --format san
crtk record dataset npy --input dump/run.puzzles.json --output training/run
```

Help works at each level:

```bash
crtk help
crtk move --help
crtk move list --help
crtk help --full
```

## First Position Workflow

```bash
crtk fen print --startpos
crtk move list --startpos --format both
crtk move after --startpos e2e4
crtk move play --startpos "e4 e5 Nf3 Nc6"
```

## First Engine Workflow

Use the in-process engine when you want no external process:

```bash
crtk engine builtin --startpos --depth 3 --format summary
```

Use a configured UCI engine when strength matters:

```bash
crtk engine uci-smoke --nodes 1 --max-duration 5s
crtk engine bestmove --startpos --format both --max-duration 2s
```

## First Publishing Workflow

```bash
crtk book pdf --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" -o dist/startpos.pdf
```

For full books and covers, continue with [Book publishing](book-publishing.md).

## Next Pages

- [Configuration](configuration.md)
- [Command reference](command-reference.md)
- [Example commands](example-commands.md)
- [FAQ](faq.md)
- [Troubleshooting](troubleshooting.md)
