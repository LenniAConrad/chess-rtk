# ChessRTK (`crtk`)

![ChessRTK banner](assets/banner/github/crtk-github-banner.png)

ChessRTK is a reproducible Java 17 toolkit for chess research, puzzle mining,
move-generation validation, engine experiments, dataset export, tagging,
rendering, and chess-book publishing. The core chess stack is dependency-free
Java; UCI engines, neural weights, and native GPU backends are optional local
artifacts layered on top. It turns chess work into inspectable files, commands,
diagrams, tensors, PDFs, and repeatable runs.

The project has one practical bias: keep the interesting chess work explicit.
Positions are FENs, moves can be UCI or SAN, searches have limits, records keep
engine evidence, datasets ship metadata, and book output is generated directly
from source manifests. The same primitives work from a terminal, scripts, CI, or
agent workflows.

If ChessRTK saves you time, inspires an experiment, or helps you publish
something useful, please star the repository so other chess builders can find it.

CLI entry point:

```bash
crtk <command> [options]
```

Without installing the launcher:

```bash
java -cp out application.Main <command> [options]
```

## What It Can Do

| Goal | Commands to start with |
| --- | --- |
| Validate and normalize positions | `fen validate`, `fen normalize`, `fen chess960` |
| Inspect a board | `fen print`, `fen display`, `fen render`, `book pdf` |
| List, convert, and apply moves | `move list`, `move uci`, `move san`, `move both`, `move to-san`, `move to-uci`, `move after`, `move play` |
| Verify move generation | `engine perft`, `engine perft-suite` |
| Run an external UCI engine | `engine analyze`, `engine bestmove`, `engine threats`, `engine uci-smoke` |
| Search and evaluate in-process | `engine builtin`, `engine static`, `engine eval` |
| Generate and extract positions | `fen generate`, `fen pgn`, `fen chess960` |
| Tag positions and puzzle lines | `fen tags`, `puzzle tags`, `fen text`, `puzzle text` |
| Mine tactical puzzles | `puzzle mine`, `puzzle pgn` |
| Filter and reshape record dumps | `record files`, `record stats`, `record tag-stats`, `record analysis-delta` |
| Export ML datasets | `record dataset npy`, `record dataset lc0`, `record dataset classifier`, `record export training-jsonl`, `record export puzzle-jsonl` |
| Publish diagrams and books | `book pdf`, `book render`, `book cover` |
| Work in GUIs | `gui`, `gui-web`, `gui-next` |
| Check local setup | `doctor`, `config show`, `config validate`, `engine gpu` |

ChessRTK is a good fit for:

- chess researchers and engine experimenters
- puzzle miners and dataset builders
- authors producing diagram sheets, puzzle books, or covers
- automation workflows that need deterministic CLI output
- AI agents that need reliable chess primitives

It is not trying to be a consumer chess-playing app. It can display boards and
run desktop workbenches, but the heart of the project is deterministic command
execution.

## Core Capabilities

ChessRTK combines a Java-native chess core with research and publishing tools:

- Bitboard-backed legal move generation with make/undo, Chess960 castling,
  SAN/FEN support, attack helpers, pin helpers, and perft counters.
- Detailed perft reporting for nodes, captures, en-passant captures, castles,
  promotions, checks, and checkmates, with detailed, table, and engine-compatible
  `move: nodes` divide output.
- A self-contained perft suite for critical standard, Chess960, en-passant,
  promotion, castling, and stress positions. It compares stored truth values to
  the Java core move generator and never starts an external engine process.
- UCI engine orchestration for analysis, best moves, threats, WDL, MultiPV,
  node/time limits, threads, hash, and smoke checks.
- An in-process alpha-beta Java engine with a transposition table,
  quiescence search, move ordering, and classical, NNUE, or LC0 value
  evaluators for bounded local search and reproducible automation.
- Static and engine-enriched position tags covering facts, material, pawn
  structure, king safety, piece activity, tactics, eval buckets, WDL, phase,
  mobility, initiative, and development.
- Record pipelines that merge, filter, split, summarize, export, and convert
  analysis dumps into PGN, CSV, JSONL, NumPy, LC0-style tensors, and classifier
  tensors.
- Native PDF generation for diagram sheets, book interiors, and print covers.
- Board image rendering to windows, PNG/JPG/BMP/SVG files, and PDFs with
  arrows, circles, legal-move dots, board flipping, dark mode, and evaluator
  ablation overlays.
- Agent-friendly command shapes for deterministic move lists, notation
  conversion, FEN normalization, line application, best-move output, and
  regression checks.

## Quickstart

Requirements:

- Java 17+ JDK with `javac`
- Optional: a UCI engine on `PATH`, or a config in `config/*.engine.toml`, for
  external engine analysis and mining
- Optional: LC0/NNUE/T5 model files under `models/` for evaluator and text
  workflows

Build directly, with no Maven or Gradle:

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
java -cp out application.Main help
```

Install the `crtk` launcher on Debian/Ubuntu-style systems:

```bash
./install.sh
crtk doctor
crtk help
```

Fetch optional LC0J model weights:

```bash
./install.sh --models
```

Model files are local artifacts and are ignored by git. The default weights can
also be downloaded manually from:

- https://github.com/LenniAConrad/chess-models

More setup details: [wiki/build-and-install.md](wiki/build-and-install.md)

## First Commands

Inspect a position:

```bash
crtk fen print --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
crtk move list --format both --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
crtk engine bestmove --format both --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" --max-duration 2s
```

Normalize, validate, and transform FENs:

```bash
crtk fen normalize --fen "<FEN>"
crtk fen validate --fen "<FEN>"
crtk move after --fen "<FEN>" e2e4
crtk move play --fen "<FEN>" "e4 e5 Nf3 Nc6"
```

Check the engine and core move generation:

```bash
crtk doctor
crtk engine uci-smoke --nodes 1 --max-duration 5s
crtk engine builtin --depth 3 --format summary --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
crtk engine perft --depth 4 --threads 4
crtk engine perft --fen "<FEN>" --depth 5 --divide --threads 4
crtk engine perft --depth 3 --format stockfish --threads 4
crtk engine perft-suite --depth 6 --threads 4
```

`engine perft-suite` is an internal regression check. It runs stored reference
positions through the Java core move generator and prints a progress bar followed
by a `Truth` / `Calculated` / `Speed` / `Match` table.

Start-position depth-8 detailed divide run:

```bash
crtk engine perft \
  --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" \
  --depth 8 --threads 12 --divide
```

| Move | Nodes | Captures | En-passant | Castles | Promotions | Checks | Checkmates |
|---|---:|---:|---:|---:|---:|---:|---:|
| `a2a3` | 2863411653 | 113558832 | 271826 | 817354 | 0 | 29978154 | 394468 |
| `a2a4` | 3676309619 | 141101295 | 331446 | 1031603 | 0 | 37335535 | 467803 |
| `b1a3` | 3193522577 | 127182592 | 305284 | 878550 | 0 | 34089600 | 431766 |
| `b1c3` | 3926684340 | 146419658 | 337907 | 1117423 | 0 | 31785958 | 472466 |
| `b2b3` | 3579299617 | 126872451 | 301576 | 966814 | 0 | 36198333 | 478444 |
| `b2b4` | 3569067629 | 188043397 | 333510 | 854736 | 0 | 31357534 | 466125 |
| `c2c3` | 3806229124 | 136372610 | 304129 | 1129730 | 0 | 26360185 | 386119 |
| `c2c4` | 4199667616 | 182245919 | 372641 | 1147131 | 0 | 41978320 | 426934 |
| `d2d3` | 6093248619 | 222520642 | 458241 | 1575843 | 0 | 104496344 | 166701 |
| `d2d4` | 7184581950 | 345921667 | 564581 | 1927577 | 0 | 123831364 | 234546 |
| `e2e3` | 8039390919 | 312030442 | 553545 | 2401727 | 0 | 74189864 | 96296 |
| `e2e4` | 8102108221 | 402191506 | 605162 | 2321721 | 0 | 90233448 | 225443 |
| `f2f3` | 2728615868 | 90868333 | 246376 | 752702 | 0 | 50920765 | 1205498 |
| `f2f4` | 3199039406 | 134509503 | 314794 | 924630 | 0 | 56636856 | 1162705 |
| `g1f3` | 3937354096 | 148963219 | 340055 | 1074811 | 0 | 32478728 | 139890 |
| `g1h3` | 3221278282 | 126706434 | 307711 | 882360 | 0 | 32693823 | 84953 |
| `g2g3` | 3641432923 | 128897981 | 305434 | 1008801 | 0 | 31715033 | 425274 |
| `g2g4` | 3466204702 | 179208541 | 329217 | 960427 | 0 | 35199463 | 1648128 |
| `h2h3` | 2860408680 | 110574933 | 271801 | 788358 | 0 | 29939917 | 591179 |
| `h2h4` | 3711123115 | 159550151 | 332741 | 1042907 | 0 | 37562369 | 347298 |
| **Total** | **84998978956** | **3523740106** | **7187977** | **23605205** | **0** | **968981593** | **9852036** |

Example runtime for that run: `189.9M nps`, `447677.707 ms`.

Generate tags, text, and diagrams:

```bash
crtk fen tags --fen "<FEN>" --include-fen
crtk fen tags --pgn games.pgn --delta --mainline
crtk puzzle tags --fen "<FEN>" --multipv 3 --pv-plies 12
crtk fen text --fen "<FEN>" --model models/t5.bin --include-fen
crtk fen render --fen "<FEN>" -o dist/position.png --arrow e2e4 --circle e4
```

## Pipeline Overview

![ChessRTK pipeline overview](assets/diagrams/crtk-pipeline-overview.png)

ChessRTK is organized as small tools that compose:

1. Create or import positions from FEN, PGN, random legal generation, or
   Chess960 starts.
2. Analyze positions with a UCI engine or built-in evaluators.
3. Keep the useful records, measure stability, and filter with the DSL.
4. Export to CSV, PGN, JSONL, NumPy tensors, LC0-style tensors, diagrams, or
   PDFs.

Diagram source: `assets/diagrams/crtk-pipeline-overview.dot`

## Research Workflow

Mine puzzles from a PGN, then export the accepted results to CSV and PGN:

```bash
crtk fen pgn --input games.pgn --output seeds.txt
crtk puzzle mine --input seeds.txt --output dump/run.json --engine-instances 4 --max-duration 60s
crtk record export csv --input dump/run.puzzles.json --output dump/run.puzzles.csv
crtk record export pgn --input dump/run.puzzles.json --output dump/run.puzzles.pgn
```

Common puzzle-mining primitives:

- Mine random seeds: `crtk puzzle mine --random-count 200 --output dump/random.json`
- Mine continuously: `crtk puzzle mine --random-infinite --output dump/endless.json`
- Extract FEN seeds from games: `crtk fen pgn --input games.pgn --output seeds.txt`
- Filter records: `crtk record files -i dump/ -o filtered.json --recursive --puzzles`
- Measure stability: `crtk record analysis-delta -i dump/run.puzzles.json -o dump/run.analysis-delta.jsonl`

### Mining Gates

![ChessRTK mining decision gates](assets/diagrams/crtk-mining-gates.png)

The mining pipeline uses explicit gates for quality, forcing moves, winning
tactics, drawing resources, and other tactical signals. The goal is not just to
find engine-approved moves, but to leave behind data that can be inspected,
filtered, replayed, and reused.

Diagram source: `assets/diagrams/crtk-mining-gates.dot`

## Dataset Workflow

Export tensors from mined or imported analysis records:

```bash
crtk record dataset npy \
  --input dump/run.puzzles.json \
  --output training/puzzles

crtk record dataset lc0 \
  --input dump/run.puzzles.json \
  --output training/lc0/puzzles \
  --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin

crtk record dataset classifier \
  -i dump/run.puzzles.json \
  -i dump/run.nonpuzzles.json \
  -o training/classifier/run
```

Available dataset shapes:

- `record dataset npy`: eval-regression tensors, including features shaped
  `(N, 781)`
- `record dataset lc0`: LC0-style input, policy, value, and metadata tensors
- `record dataset classifier`: 21-plane inputs and binary labels
- `record export training-jsonl`: coarse/fine FEN labels for training pipelines
- `record export puzzle-jsonl`: puzzle rows with LC0 policy information

More: [wiki/datasets.md](wiki/datasets.md)

## Publishing Workflow

Render a full puzzle book and a matching cover from the same manifest:

```bash
crtk book render -i books/puzzles.toml --check
crtk book render -i books/puzzles.toml -o dist/puzzles.pdf
crtk book cover -i books/puzzles.toml --check \
  --pdf dist/puzzles.pdf --binding paperback --interior white-bw
crtk book cover -i books/puzzles.toml -o dist/puzzles-cover.pdf \
  --pdf dist/puzzles.pdf --binding paperback --interior white-bw
```

Make a quick diagram sheet from a FEN list or PGN mainlines:

```bash
crtk book pdf --fen "<FEN>" -o dist/position.pdf
crtk book pdf -i seeds.txt -o dist/sheet.pdf --title "Training Sheet"
crtk book pdf --pgn games.pgn -o dist/games.pdf --page-size a5 --diagrams-per-row 1
```

ChessRTK writes native PDFs, so the publishing path does not require LaTeX.
Book and diagram rendering share the same `chess.book.render` helpers; SAN
solution text is converted to figurine algebraic notation before it is written
into tables and captions.

More: [wiki/book-publishing.md](wiki/book-publishing.md)

## Single-Position Toolbox

![ChessRTK single-position toolbox](assets/diagrams/crtk-position-toolbox.png)

Useful commands when you are studying, scripting, or giving an AI agent a
deterministic chess primitive:

- `move list --format uci|san|both`
- `move uci`, `move san`, `move both`
- `move to-san`, `move to-uci`
- `move after`, `move play`
- `engine bestmove --format uci|san|both`
- `engine bestmove-uci`, `engine bestmove-san`, `engine bestmove-both`
- `engine builtin --format uci|san|both|summary`
- `fen normalize`, `fen validate`, `fen chess960`, `fen pgn`
- `engine static`, `engine perft-suite`, `record files`, `puzzle pgn`

Diagram source: `assets/diagrams/crtk-position-toolbox.dot`

## Command Map

Core command groups:

- `fen`: normalize, validate, generate, render, display, tag, summarize, and
  extract positions
- `move`: list legal moves, convert notation, apply one move, or play a line
- `engine`: analyze, choose best moves, search with the in-house Java engine,
  evaluate, inspect threats, run perft, and smoke-test UCI engines
- `puzzle`: mine puzzles, convert puzzle dumps to PGN, tag positions, and
  generate text summaries
- `record`: export, filter, merge, split, summarize, and convert record files
  into training datasets
- `book`: render diagram PDFs, puzzle-book interiors, and covers
- `gui-next`: launch the Studio GUI v3 research workbench
- `config`: show and validate resolved configuration
- `doctor`: check the local runtime, config, engines, and artifact paths

Full command reference: [wiki/command-reference.md](wiki/command-reference.md)

## Implementation Notes

The chess rules implementation is the Java-native `chess.core` package. It
contains the bitboard-backed `Position`, strict FEN/SAN helpers, Chess960 setup
logic, legal move generation, make/undo support, attack/pin helpers, and
perft-facing APIs. Detailed perft and the self-contained reference suite live in
`chess.debug`, so the debug tools exercise the same move generator used by the
CLI, tags, renderer, and engine.

Rendering-specific movetext formatting lives with the book renderer as
`chess.book.render.MoveText`; shared numeric clamp helpers live in
`utility.Numbers` to keep small generic utilities in one place.

More: [wiki/development-notes.md](wiki/development-notes.md)

## Built-In Engine and Optional Evaluators

`engine builtin` is ChessRTK's in-house Java engine. It exists as an
in-process search and benchmarking target, not as a top-tier engine meant to
compete with mature UCI engines. It is useful when you need deterministic CLI
search without starting a UCI process, when a workflow needs bounded search on
a machine with no engine configured, or when you want a reproducible baseline
for puzzle-solve timing.

```bash
crtk engine builtin --depth 4 --nodes 100000 --format summary --fen "<FEN>"
crtk engine builtin --depth 20 --fen "<FEN>"   # UCI-style info depth lines + bestmove
crtk engine builtin --evaluator nnue --weights models/crtk-halfkp.nnue --fen "<FEN>"
crtk engine builtin --lc0 --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin --fen "<FEN>"
```

ChessRTK supports two LC0-related paths:

- LC0 as a UCI engine for mining and analysis, usually with `.pb.gz` weights
- the built-in Java LC0 evaluator for `engine eval`, `fen display`, and
  ablation-style inspection, using local `models/*.bin` weights

The built-in search uses alpha-beta, quiescence search, move ordering, and
internal transposition/evaluation caches. It can choose the classical, NNUE, or
LC0 value evaluator at the frontier. `engine eval` is a single-position
evaluator command that prefers LC0 and falls back to classical unless
`--classical` or `--lc0` is specified. External engine commands remain the right
path for strength-sensitive analysis, MultiPV production, and long mining runs.

See [wiki/in-house-engine.md](wiki/in-house-engine.md),
[wiki/lc0.md](wiki/lc0.md), and [models/README.md](models/README.md).

## Maintenance

Update an existing checkout and reinstall the launcher:

```bash
./scripts/update.sh
```

Uninstall the launcher and local build artifacts:

```bash
./scripts/uninstall.sh
```

Build a Linux x86_64 CUDA release bundle:

```bash
scripts/make_release_linux_cuda.sh --version v0.0.0
```

The release script writes `dist/crtk-<version>-linux-x86_64-cuda.tar.gz` and
`dist/SHA256SUMS`. Add `--include-models` only when you intentionally want local,
gitignored model files bundled into the archive.

## Regression Checks

Focused checks after core changes:

```bash
javac --release 17 -d out $(find src -name "*.java")
java -cp out testing.PositionRegressionTest
java -cp out testing.CoreMoveGenerationRegressionTest
java -cp out testing.BuiltInEngineRegressionTest
java -cp out application.Main doctor
java -cp out application.Main engine perft-suite --depth 6 --threads 4
```

`engine uci-smoke --nodes 1 --max-duration 5s` is the matching setup check when
external UCI analysis is part of the workflow.

Publishing checks:

```bash
java -Djava.awt.headless=true -cp out testing.BookRegressionTest
java -Djava.awt.headless=true -cp out testing.ChessBookCommandRegressionTest
java -Djava.awt.headless=true -cp out testing.ChessBookCoverCommandRegressionTest
```

## Documentation

- [Docs index](wiki/README.md)
- [Build and install](wiki/build-and-install.md)
- [Command reference](wiki/command-reference.md)
- [Example commands](wiki/example-commands.md)
- [Configuration](wiki/configuration.md)
- [Mining puzzles](wiki/mining.md)
- [Filter DSL](wiki/filter-dsl.md)
- [Datasets](wiki/datasets.md)
- [Book publishing](wiki/book-publishing.md)
- [Tagging implementation plan](wiki/tagging-implementation-plan.md)
- [Outputs and logs](wiki/outputs-and-logs.md)
- [LC0](wiki/lc0.md)
- [T5 tag-to-text](wiki/t5.md)
- [AI agents and automation](wiki/ai-agents.md)
- [Releasing](wiki/releasing.md)
- [Troubleshooting](wiki/troubleshooting.md)

## Citing

If you use ChessRTK in research, cite the repository and pin a commit hash or
tag so your workflow can be reproduced.

## License

See [LICENSE.txt](LICENSE.txt).

The current license is intentionally restrictive source-available licensing, not
a standard open-source license: it allows evaluation and personal
non-commercial use, and it forbids redistribution and derivative works without
permission. If broader external adoption matters, replace it intentionally with
a standard license before release rather than assuming the current text already
covers that goal.
