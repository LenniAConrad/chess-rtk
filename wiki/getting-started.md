# Getting Started

One chess core backs everything in `crtk`: move generation, make/undo, attack detection, FEN/SAN/UCI parsing, Chess960, perft. The CLI, the desktop Workbench, dataset exports, and PDF books all call into it, which is why they never disagree about a position. The goal here is to get you from a fresh checkout to commands you'll actually use. Build or install, confirm the environment with `doctor`, then walk a short tour: print a board, list legal moves, count positions, search, open the Workbench.

## What you can do in five minutes

- Print any position from a FEN, in the terminal or as an image.
- List, convert, and apply moves in UCI or SAN, including full move lines.
- Count positions exactly with `engine perft` and validate the move generator with `engine perft-suite`.
- Search positions with the built-in engine, with no external process.
- Drive everything visually from the native Swing Workbench.

Most of this runs against nothing but the core: positions, moves, perft, forced-mate search, and the built-in engine all work on a bare checkout. You only need Stockfish or LC0 for the UCI-backed commands — `engine analyze`, `engine bestmove`, `engine compare`, and puzzle mining. The neural evaluators (NNUE, LC0 CNN, OTIS) and T5 text summaries are optional too; they read local files under `models/`.

## Requirements

| Need | Required for |
| --- | --- |
| Java 17+ JDK with `javac` | Building and running everything |
| A UCI engine (`stockfish`, `lc0`) on `PATH` | `engine analyze`, `engine bestmove`, `engine compare`, `puzzle mine` |
| Model files under `models/` | NNUE / LC0 / OTIS evaluation, T5 text |
| A GPU + native backend | Optional acceleration for `engine perft --gpu` and OTIS |

The build is `javac --release 17` and nothing else — no Maven, no Gradle. Licensed GPL-3.0-only.

## Option A: install the launcher (Debian/Ubuntu)

`./install.sh` compiles the sources, drops a `crtk` launcher on your `PATH`, and registers the Workbench as a desktop application. Ask it to, and it will also fetch optional model files and probe for GPU backends.

```bash
./install.sh
crtk doctor
crtk help
```

From here on, commands look like `crtk <area> <action> [options]`, and the rest of this guide uses that form.

## Option B: build from source (no Maven, no Gradle)

Skip the launcher and compile straight to an output directory, then run the main class.

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
java -cp out application.Main help
```

A prebuilt `crtk.jar` ships in the repository root, so you can run any command without compiling first:

```bash
java -jar crtk.jar version
java -jar crtk.jar help
```

Wherever this page says `crtk`, `java -jar crtk.jar` and `java -cp out application.Main` work just as well. See [Build and install](build-and-install.md) for the full matrix, including GPU backends.

## Check your environment with doctor

`crtk doctor` inspects your runtime version, configuration file, engine protocol, engine pool, output directory, and local model artifacts in one pass. Run it once after installing, and again whenever something behaves unexpectedly.

```bash
crtk doctor
```

A healthy run looks like this. Missing optional models show up as warnings, not failures — `ok-with-warnings` is still a pass:

```text
doctor: ok-with-warnings
Java: 21.0.11
Config: /path/to/config/cli.config.toml
Protocol: config/default.engine.toml
Engine instances: 4
Output: dump/
Warnings:
  - Missing model file for t5-model-path: .../models/t5.bin
```

In CI, where a warning should fail the build, use `crtk doctor --strict` and it exits non-zero on any warning.

## The command shape

Every command is a noun group plus an action. For anything structured, reach for a named flag rather than a positional.

```bash
crtk <area> <action> [options] [args]
```

Help is available at every level, from the top down to a single action:

```bash
crtk help
crtk move --help
crtk move list --help
crtk help --full
```

The [Command cheatsheet](command-cheatsheet.md) lists every area and action on one page.

## First commands tour

### 1. Print a position

`fen print` draws the board and the facts that go with it: side to move, castling rights, checkers, and the legal moves. `--startpos` gives you the standard opening; otherwise pass `--fen "<FEN>"`.

```bash
crtk fen print --startpos
crtk fen print --fen "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"
```

For a vector board instead of text, `crtk fen render -o board.svg` writes an SVG; `crtk fen display` opens a live window.

### 2. List and apply moves

`move list` enumerates the legal moves; pick the notation with `--format uci`, `--format san`, or `--format both`.

```bash
crtk move list --startpos --format both
crtk move uci --fen "<FEN>"
crtk move to-san --startpos e2e4
```

`move after` applies one move; `move play` runs a whole line:

```bash
crtk move after --startpos e2e4
crtk move play --startpos "e4 e5 Nf3 Nc6"
```

Any of these takes `--json` or `--jsonl` when you want to pipe the result somewhere else.

### 3. Count positions exactly with perft

`engine perft` walks the legal move tree to a given depth and counts the leaves. Since every command shares one move generator, a wrong count anywhere means a bug everywhere — which is exactly why these numbers are the project's ground truth. Depth 3 from the start position is 8902 nodes, no more and no less.

```bash
crtk engine perft --startpos --depth 3
crtk engine perft --startpos --depth 4 --divide
```

To check the generator against a suite of known-good positions:

```bash
crtk engine perft-suite --depth 5
```

Both take `--gpu`, which uses a native GPU backend when one is installed and falls back to the CPU when it isn't.

### 4. Search with the built-in engine

`engine builtin` runs in-process — no subprocess, no socket, and the same single-thread budget always yields the same result. `--format summary` gives you something readable; bound the search with `--depth`, `--max-nodes`, or `--max-duration`.

```bash
crtk engine builtin --startpos --depth 6 --format summary --max-duration 2s
```

Out comes the chosen move, its score, the node count, and the principal variation in both UCI and SAN. When you want a proven forced mate rather than an evaluation — no neural net involved — reach for `engine mate`:

```bash
crtk engine mate --fen "<FEN>" --mate 3 --format san
```

For real playing strength, point `crtk` at Stockfish or LC0 and use `engine bestmove` or `engine analyze` (see [Configuration](configuration.md)):

```bash
crtk engine bestmove --startpos --format both --max-duration 2s
```

### 5. Open the Workbench

The Workbench is the same core behind a desktop GUI: an interactive board, play-vs-engine, command forms, batch jobs, dataset tools, publishing previews, puzzles, and neural-network visualizers.

```bash
crtk workbench
```

![ChessRTK Workbench analysis view](../assets/screenshots/workbench-analysis.png)

Pass `crtk workbench --fen "<FEN>"` to open it on a specific position. See [Workbench](workbench.md) for the full walkthrough.

## Verify the checkout

Before you trust a build, or before you send changes upstream, the regression suite is the quick check.

```bash
./scripts/run_regression_suite.sh build
./scripts/run_regression_suite.sh core
```

For a wider pass before publishing:

```bash
./scripts/run_regression_suite.sh recommended
```

## Where to go next

- [Build and install](build-and-install.md) — full build matrix, GPU backends, and model setup.
- [Workbench](workbench.md) — the native desktop application in depth.
- [Command cheatsheet](command-cheatsheet.md) — every area and action at a glance.
- [Command reference](command-reference.md) — complete flag-level documentation.
- [Configuration](configuration.md) — engine protocols, model paths, and defaults.
