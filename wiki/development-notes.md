# Development Notes

One rule shapes everything in this codebase: there is exactly one chess core, and everyone uses it. Commands, the desktop Workbench, the puzzle miner, the dataset exporters — all of them route legality, FEN/SAN/UCI, and Chess960 through the same code, so the same input produces the same output every time. Protecting that property is the thing to keep in mind as you read the rest of this page, which covers the repository layout, the build (and the one compiler flag you cannot skip), the regression suite, regenerating the docs site and manual PDF, re-rendering the diagrams, and the conventions the source follows.

For end-user workflows see [Getting Started](getting-started.md) and the [Command Reference](command-reference.md). For the system map, read [Architecture](architecture.md). For build prerequisites and install paths, see [Build & Install](build-and-install.md).

## Prerequisites

- A JDK 17 or newer with `javac` on `PATH`. A newer JDK works fine; the build targets the 17 release regardless.
- `jar` (ships with the JDK) to package `crtk.jar`.
- Optional: `python3` for the docs/manual scripts, Graphviz (`dot`) for diagrams, a headless Chromium/Chrome for the manual PDF, and native toolchains (CUDA/ROCm/oneAPI) only if you build GPU backends.
- No Maven, no Gradle, no third-party dependencies. Everything compiles from `src/` with the stock JDK.

## Repository layout

| Path | What lives there |
| --- | --- |
| `src/application/` | CLI entry point, command dispatch, config loading, and the Swing Workbench |
| `src/chess/` | The shared chess core plus engines, evaluators, neural nets, mining, rendering, and I/O |
| `src/utility/` | Dependency-free helpers (JSON, TOML, SVG, argv parsing, numeric clamps) |
| `src/testing/` | Self-contained regression tests, each with its own `main(...)` |
| `scripts/` | Build/regen tooling: docs site, manual PDF, regression runner, release helpers |
| `assets/diagrams/` | Graphviz `.dot` sources and their rendered `.png` exports |
| `wiki/` | Markdown documentation (the source the docs site is generated from) |
| `docs/` | Generated static site and manual (build output — do not hand-edit) |
| `native/` | CUDA, ROCm, and oneAPI native backends for perft and OTIS |
| `config/` | Seeded TOML config and engine protocol files |

### `src/application/` — CLI and Workbench

- `application.Main` is the `main-class` baked into `crtk.jar`, and not much else — a thin launcher that hands off to `application.cli`.
- `application.cli.CliRegistry` maps each area/action to a `CliCommand` implementation under `application.cli.command`. One class per command (`AnalyzeCommand`, `PerftCommand`, `Chess960Command`, and so on) keeps the dispatch table flat and each command testable in isolation.
- `application.cli.Constants` holds the canonical command tokens. The plumbing every command shares — engine setup, eval setup, record I/O, FEN/PGN parsing, output formatting, validation — lives in sibling classes like `EngineOps`, `EvalOps`, `RecordIO`, `PgnOps`, and `Format`, so a new command rarely has to reinvent any of it.
- `application.gui.workbench` is the Swing Workbench launched by `workbench` (alias `gui`): board view, play-vs-engine, command forms, batch jobs, dataset tools, logs, publishing previews, puzzles, and neural-net visualizers.

### `src/chess/` — the shared core and everything built on it

- `chess.core` is the canonical rules engine: `Position` (mutable state, FEN round-trip, `play(...)`/`undo(...)`), `MoveGenerator` (pseudo-legal/legal generation, attack and pin detection), `MoveList` (compact encoded `short` moves), `Fen`, `SAN`, and `Setup` (standard, Chess960, and random starts). Note what is absent: there is no separate `bitboard` package, because the bitboard-backed code *is* the core.
- `chess.debug` holds the perft runners. Plain perft lives in `Perft`, the stored-truth regression suite in `PerftSuite`. The optional GPU path expands frontier positions on the CPU through `SplitPerft`, then hands the heavy node counting to a native backend.
- `chess.uci` is the external-engine protocol layer. It drives Stockfish/LC0 for `engine analyze`, `engine bestmove`, `engine threats`, `engine compare`, MultiPV, and the batch commands, and it hosts the Filter DSL that gates puzzle mining.
- `chess.engine` is the built-in MCTS searcher behind `engine builtin` / `engine java`, plus the alpha-beta search Workbench Play uses. `chess.eval` is the in-process evaluator facade for `engine eval` and `engine static`.
- `chess.nn` holds the neural-network evaluators: an LC0 CNN forward pass, the simplified BT4 attention path, the OTIS policy/WDL evaluator, and the T5 text models behind `fen text` and `puzzle text`. Each backend ships a pure-Java path plus optional CUDA/ROCm/oneAPI acceleration that falls back to the CPU when the native library is missing.
- `chess.puzzle`, `chess.tag`, `chess.io`, `chess.book`, `chess.pdf`, and `chess.images` cover puzzle mining, deterministic tagging, record/dataset export, native PDF publishing (no LaTeX), and board rendering.

> The networks are usable evaluators, not bit-exact reimplementations. crtk does **not** claim LC0/BT4 parity, and the BT4 path is simplified and experimental. Say so plainly in code comments and docs; an honest caveat beats a misleading benchmark.

## Building with `javac --release 17`

The canonical build is whatever `install.sh` does, which is two commands: compile every source under `src/`, then package a runnable jar.

```bash
find src -name '*.java' -print0 | xargs -0 javac --release 17 -d out
jar --create --file crtk.jar --main-class application.Main -C out .
```

Then run it:

```bash
java -jar crtk.jar version
java -jar crtk.jar help
```

You can also run without packaging, straight from the classes directory:

```bash
java -cp out application.Main help
```

### The `--release 17` gotcha (do not skip this)

**Always compile with `--release 17`, never with a bare JDK 21 default.** Build against a newer release and you will get errors that point at nothing real — the MCTS session code is a repeat offender — because the compiler is resolving against a newer class library and language level than the source was written for. `--release 17` pins both the language level and the API surface to what the project targets and what CI uses. So when the editor lights up red, run a clean command-line `javac --release 17` and believe it over the IDE.

IDE markers also go stale mid-edit. When in doubt, compile from scratch instead of trusting a single inline complaint.

## Running the regression suite

`scripts/run_regression_suite.sh` compiles the sources and runs the `src/testing/` checks. There is no test framework: each test is an ordinary class with its own `main(...)`, invoked as `java -cp out testing.<Name>`. That keeps the dependency count at zero and makes any failing test runnable on its own.

```bash
bash scripts/run_regression_suite.sh              # default: recommended
bash scripts/run_regression_suite.sh core         # core chess + parsing tests
bash scripts/run_regression_suite.sh cli          # CLI command regressions
bash scripts/run_regression_suite.sh engine       # engine / NN / GPU perft tests
```

Available suite selectors:

| Selector | Scope |
| --- | --- |
| `recommended` | Default fast set covering core, CLI, and key engine paths |
| `build` | Compile only |
| `lint` | Source/style checks |
| `core` | Move generation, positions, SAN, JSON, parsing, tagging |
| `cli` | CLI command + PGN/book/puzzle command regressions |
| `engine` | Built-in engine, puzzle difficulty, BT4, T5, OTIS, GPU perft |
| `scripts` | Shell-script linting |
| `uci` | External-engine protocol tests |
| `book` | PDF/book rendering tests |
| `docs` | Documentation checks |
| `perft-smoke` | Quick perft sanity run |
| `jar` | Packaged-jar checks |
| `ci` | The continuous-integration set |
| `release` | The pre-release set |

A few environment overrides are worth knowing: `CRTK_PERFT_THREADS`, `CRTK_PERFT_DEPTH`, `CRTK_PERFT_SUITE_DEPTH`, and `CRTK_REQUIRE_STOCKFISH` (set it to `1` when you want a missing Stockfish to fail the run rather than quietly skip).

For a correctness check that needs no script at all, run the built-in perft regression directly:

```bash
java -jar crtk.jar engine perft-suite --depth 4
```

It checks stored node counts against the shared core move generator — the fastest way to know a movegen change did not quietly break something.

## Regenerating the docs site and manual

The Markdown under `wiki/` is the source of truth. The HTML under `docs/` is generated from it, so edits there get overwritten — change the Markdown.

### Static site

```bash
python3 scripts/build_docs_site.py
```

This reads `wiki/*.md` and writes the static site into `docs/`: a top nav, a landing hero, a left sidebar with per-page tables of contents (built from the `##`/`###` headings), a search index (`docs/search-index.js`), and a light/dark theme toggle. Page order and grouping come from the `NAVIGATION` table near the top of the script. Anything not listed there still ships — it lands in a trailing reference group rather than vanishing without a trace.

### Manual PDF

```bash
python3 scripts/build_manual_pdf.py
```

This reuses `build_docs_site` to assemble one continuous `docs/manual.html`, then renders `docs/chessrtk-manual.pdf` through a headless Chromium/Chrome (`--headless --print-to-pdf`). It probes `PATH` for `chromium`, `chromium-browser`, `google-chrome`, `google-chrome-stable`, or `chrome`; if the PDF step fails, the usual cause is that none of those is installed. Run `build_docs_site.py` — or this script, which calls it — after any `wiki/` edit so the site and manual stay in step.

## Regenerating diagrams

The architecture diagrams are Graphviz sources in `assets/diagrams/*.dot`, each with its rendered `*.png` checked in alongside it. They are all `digraph`s, so `dot` renders them:

```bash
dot -Tpng assets/diagrams/crtk-pipeline-overview.dot -o assets/diagrams/crtk-pipeline-overview.png
```

Regenerate every PNG at once:

```bash
for f in assets/diagrams/*.dot; do dot -Tpng "$f" -o "${f%.dot}.png"; done
```

Reach for `neato` only if you write a graph that wants a spring or radial layout; everything currently checked in is a directed-graph layout, so `dot` is the right tool. Reference a rendered diagram from Markdown by its assets path, for example `![Pipeline overview](../assets/diagrams/crtk-pipeline-overview.png)`.

## Adding or changing a CLI command

1. Add or update a `CliCommand` implementation under `src/application/cli/command/` (book/puzzle commands live in the `book` subpackage).
2. Register the area/action in `application.cli.CliRegistry`, using the canonical tokens from `application.cli.Constants`.
3. Reuse the shared plumbing — `EngineOps`, `EvalOps`, `RecordIO`, `PgnOps`, `Format`, `Validation` — rather than re-parsing FENs or re-spawning engines inline.
4. Update the help text. `crtk help <area> [<action>]` is the ground-truth surface and must list every flag you add.
5. Update the docs — [Command Reference](command-reference.md) and [Example Commands](example-commands.md) — then regenerate the site.
6. Add or extend a `testing.*RegressionTest` and run the `cli` suite.

Names are noun-then-verb, everywhere: `move list`, `move uci`, `engine bestmove`, `engine static`, `record tag-stats`, `fen pgn`, `puzzle mine`. The legacy forms — `pgn-to-fens`, `mine-puzzles`, `record-to-csv`, bare `moves`/`bestmove`/`eval-static`, `stats-tags` — are gone for good; do not bring them back.

## Coding conventions

- **One shared core.** New features call `chess.core` for legality, FEN/SAN/UCI, and Chess960. Never fork the rules — determinism here is engineered, not lucky.
- **Determinism by default.** No hidden global state, no wall-clock-dependent output, no unseeded randomness in anything a command prints. The same FEN and flags should produce the same bytes on every run, on any machine.
- **No new dependencies.** The build stays on the stock JDK. Generic, dependency-free helpers go in `utility` (`utility.Json`, `utility.Toml`, `utility.Svg`, `utility.Argv`, `utility.Numbers`); rendering-specific text stays next to its renderer.
- **Small, single-responsibility classes.** Follow the existing split — one command per class, focused support classes — instead of letting a file grow into a monolith.
- **Compile clean at `--release 17`.** Fix the source that produces a warning rather than reaching for a broad suppression, and trust a clean command-line compile over a transient IDE marker.
- **Help text is ground truth.** Every flag you expose must show up in `crtk help`; the docs and AI-agent integrations quote it verbatim, so a stale help string becomes a wrong document elsewhere.

Related: [Architecture](architecture.md), [Build & Install](build-and-install.md), [Quality & Testing](quality-and-testing.md), [Command Reference](command-reference.md).
