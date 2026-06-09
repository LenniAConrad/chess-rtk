# ChessRTK Wiki

Everything in ChessRTK ("crtk") runs through one chess core. A single legal move generator, one make/undo, one attack detector, one FEN/SAN/UCI/Chess960 codepath — and every command above it inherits the same answers. That is what makes the noun-verb CLI (`move list`, `engine bestmove`, `puzzle mine`) agree with the search, the tagger, the datasets, and the GUI down to the last bit. On top of the core sit external UCI analysis (Stockfish, LC0), built-in alpha-beta/MCTS search with a forced-mate prover, classical/NNUE/LC0/OTIS evaluators, position and tactic tagging, optional T5 summaries, puzzle mining driven by a Filter DSL, ML dataset export, native PDF publishing with no LaTeX, optional GPU backends for perft and OTIS, and a Swing desktop Workbench. This page is the index. Pick a section and follow the one-line descriptions to the page that goes deeper.

Pages document what the code actually does. Licensed GPL-3.0-only.

## Other Reading Formats

The same content reaches you three ways, depending on whether you want to browse, read offline, or check the source of truth:

- **Website**: this wiki with sidebar navigation and a wider reading layout at `https://LenniAConrad.github.io/chess-rtk/`.
- **PDF manual**: one offline handbook, produced by ChessRTK's own PDF pipeline — the publishing code eating its own cooking.
- **Built-in help**: `crtk help`, `crtk help <area>`, or `crtk help <area> <action>`. When the prose and the binary disagree, the binary wins.

> If the `crtk` launcher is not installed, replace `crtk <command> ...` with `java -cp out application.Main <command> ...` after building. See [Build and install](build-and-install.md).

## Start Here

- [Getting started](getting-started.md) - build, install, verify, and run your first commands.
- [Build and install](build-and-install.md) - compile with `javac --release 17` (no Maven or Gradle) and set up the launcher.
- [Configuration](configuration.md) - config files, engine protocol TOML, model and weights paths.
- [FAQ](faq.md) - short, practical answers for new users.
- [Use cases](use-cases.md) - workflow paths grouped by role and job.
- [Troubleshooting](troubleshooting.md) - common setup and runtime failures with fixes.
- [Support](support.md) - where to get help and how to report issues.

## Commands

- [Command reference](command-reference.md) - the full grouped CLI surface, areas, actions, and options.
- [CLI command guide](cli-command-guide.md) - contributor guide for adding commands, flags, help, docs, and regression tests.
- [Command cheatsheet](command-cheatsheet.md) - common tasks mapped to exact commands.
- [Example commands](example-commands.md) - copy-pasteable command recipes.
- [Glossary](glossary.md) - defined terms used across the toolkit and wiki.

## Workbench

- [Desktop workbench](workbench.md) - the native Swing GUI: board, play-vs-engine, command forms, batch jobs, datasets, logs, publishing previews, puzzles, and neural-net visualizers. Launch it with `workbench` (alias `gui`).
- [Workbench design guide](workbench-design-guide.md) - visual direction, layout rules, shared UI primitives, copy, accessibility, and verification for Workbench contributors.

## Engines & Models

- [Running engines](in-house-engine.md) - the built-in engine (`engine builtin` / `engine java`) and the forced-mate prover (`engine mate`).
- [LC0 and the Java evaluator](lc0.md) - the LC0 UCI engine path plus the in-process LC0 CNN evaluator. Networks are usable evaluators, not bit-exact LC0/BT4 reproductions.
- [GPU backends](gpu.md) - native CUDA, ROCm, and oneAPI backends for perft and OTIS, with automatic CPU fallback (`engine gpu`, `engine perft --gpu`).

## Workflows

- [Mining puzzles](mining.md) - generate and verify puzzles with `puzzle mine`, writing `*.puzzles.json` / `*.nonpuzzles.json`.
- [Filter DSL](filter-dsl.md) - the gating language that selects records and decides what counts as a puzzle.
- [Piece and position tags](piece-tags.md) - deterministic position, tactic, and theme tagging via `fen tags` and `puzzle tags`.
- [Tag reference](tag-reference.md) - the catalog of tag names and their meanings.
- [Datasets](datasets.md) - ML dataset export with `record dataset npy|lc0|classifier` and `record export ...-jsonl`.
- [T5 tag-to-text pipeline](t5.md) - natural-language position and puzzle summaries with `fen text` and `puzzle text`.
- [Book publishing](book-publishing.md) - native PDF books, covers, collections, and studies (`book render`, `book cover`, `book collection`, `book study`, `book pdf`) - no LaTeX.
- [AI agents and automation](ai-agents.md) - deterministic, scriptable commands for agents and batch pipelines (`batch run`, `config validate`, `doctor`).

## Project

- [Architecture](architecture.md) - the one-shared-core design and how it powers the CLI, search, tagging, datasets, rendering, and GUI.
- [Quality and testing](quality-and-testing.md) - regression suites, perft validation, and determinism checks.
- [Outputs and logs](outputs-and-logs.md) - where artifacts land and how session cache/logs work (`clean`).
- [Development notes](development-notes.md) - internals, conventions, and contributor notes.
- [CLI command guide](cli-command-guide.md) - exact implementation workflow for new CLI commands.
- [Releasing](releasing.md) - how releases are cut and versioned.

## Command Areas At A Glance

| Task | Main commands | Docs |
| --- | --- | --- |
| Validate, normalize, and print positions | `fen validate`, `fen normalize`, `fen print` | [Getting started](getting-started.md), [Command reference](command-reference.md) |
| Generate random or filtered FEN shards | `fen generate`, `gen fens` | [Command cheatsheet](command-cheatsheet.md), [Example commands](example-commands.md) |
| List, convert, and apply moves | `move list`, `move to-san`, `move to-uci`, `move after`, `move play` | [Command reference](command-reference.md), [AI agents and automation](ai-agents.md) |
| Verify move generation | `engine perft`, `engine perft-suite` | [Architecture](architecture.md), [Quality and testing](quality-and-testing.md) |
| Analyze with UCI engines | `engine analyze`, `engine bestmove`, `engine threats`, `engine compare` | [Configuration](configuration.md), [LC0 and the Java evaluator](lc0.md) |
| Search in-process | `engine builtin`, `engine java`, `engine mate` | [Running engines](in-house-engine.md) |
| Evaluate positions | `engine eval`, `engine static` | [Running engines](in-house-engine.md), [LC0 and the Java evaluator](lc0.md) |
| Mine and export puzzles | `puzzle mine`, `puzzle pgn`, `record files` | [Mining puzzles](mining.md), [Filter DSL](filter-dsl.md) |
| Tag positions and lines | `fen tags`, `puzzle tags`, `record tag-stats` | [Piece and position tags](piece-tags.md), [Tag reference](tag-reference.md) |
| Summarize in plain English | `fen text`, `puzzle text`, `position describe` | [T5 tag-to-text pipeline](t5.md) |
| Export training data | `record dataset npy`, `record dataset lc0`, `record dataset classifier` | [Datasets](datasets.md) |
| Render diagrams and books | `fen render`, `book render`, `book cover`, `book pdf` | [Book publishing](book-publishing.md) |
| Use the desktop GUI | `workbench`, `gui` | [Desktop workbench](workbench.md) |
| Automate command use | `batch run`, `doctor`, `config validate` | [AI agents and automation](ai-agents.md) |

## Quick Verification

Before trusting anything else, prove the core is sound. Move generation underpins every command, so a clean regression run plus a couple of spot checks tells you the foundation holds:

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
java -cp out testing.CoreMoveGenerationRegressionTest
java -cp out application.Main move list --startpos --format both
java -cp out application.Main engine builtin --startpos --depth 3 --format summary
```

When you want more than a smoke test:

```bash
./scripts/run_regression_suite.sh recommended
```

See [Quality and testing](quality-and-testing.md) for the full suite.

## Project Policy Notes

- Source and shell scripts are tracked; everything generated is not. Analysis dumps, model weights, PDFs, and build output are local artifacts and stay out of git.
- UCI engines and model weights are optional. FEN, move, perft, Workbench Play, and the classical built-in search all run in-process, so a fresh checkout does useful work before you download a single weight.
- The neural-network paths are honest evaluators, not impersonations: no claim of bit-exact LC0 or BT4 parity, and the BT4 path remains simplified and experimental.
- Determinism and the single shared core are design goals, not accidents. One legality-and-notation implementation answers for the CLI, search, tagging, datasets, rendering, and GUI alike, which is why a result reproduces wherever you ask for it.
