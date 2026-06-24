# Agent Guide for ChessRTK

ChessRTK (`crtk`) is a deterministic Java 17 chess research toolkit, and this file is the working brief for AI coding agents and automated contributors. It tells you how to build, how to run the regression suite, where things live in the tree, how to regenerate the docs, and which conventions are non-negotiable. The most important property to preserve is determinism: every command runs through *one shared chess core*, so the same input must always produce the same output. The single hardest rule to remember is the Git attribution policy below — never add yourself, Claude, or any AI as an author or co-author. This file is a sibling to `CLAUDE.md`; both describe the same repo for different agents.

## TL;DR for agents

- Build with `javac --release 17` (the `--release 17` flag is mandatory — see below). No Maven, no Gradle, no third-party Java deps.
- Run `./scripts/run_regression_suite.sh recommended` before declaring work done.
- The CLI is strictly noun-then-verb: `crtk <area> <action> [options]` (for example `crtk move list`, `crtk engine bestmove`). Never reintroduce legacy single-word commands.
- Keep outputs deterministic and reproducible.
- Commit only as the human maintainer. Never add AI co-authorship trailers.

## Build

The whole toolkit compiles from `src/` with the stock JDK. Always target the Java 17 release even on newer JDKs:

```bash
find src -name '*.java' | sort > /tmp/srcs.txt
javac --release 17 -d out @/tmp/srcs.txt
rm -rf out/schemas && cp -R schemas out/schemas
```

Or let the regression runner do it for you:

```bash
./scripts/run_regression_suite.sh build
```

> The `--release 17` flag is non-negotiable. Compiling with a bare JDK 21 (no `--release 17`) surfaces false errors in some sources and produces classfiles the project does not target. Always pass `--release 17`.

To package the runnable jar (this is what `run_regression_suite.sh jar` does):

```bash
rm -rf out/schemas && cp -R schemas out/schemas
jar --create --file crtk.jar --main-class application.Main -C out .
```

### Running the CLI

- Packaged: `java -jar crtk.jar <area> <action> [options]`
- From classes: `java -cp out application.Main <area> <action> [options]`
- Installed launcher: `crtk <area> <action> [options]`

`application.Main` is a thin launcher; real dispatch lives in `application.cli`.

## Test

The regression suite is the source of truth for "does it still work." Prefer it over ad-hoc checks:

```bash
./scripts/run_regression_suite.sh recommended
```

`recommended` runs build, lint, core, cli, engine, uci, book, a perft smoke, and the jar smoke in sequence. You can run a single phase by name when iterating:

| Phase | What it covers |
| --- | --- |
| `build` | Compile all sources with `--release 17` |
| `lint` | Compile with `-Xlint:all`, lint scripts, and `git diff --check` |
| `core` | Core rules and movegen regression tests |
| `cli` | CLI command regression tests |
| `engine` | Built-in engine, puzzle difficulty, BT4, T5, OTIS, and GPU-perft tests |
| `uci` | External UCI protocol tests (Stockfish optional; auto-skips if absent) |
| `book` | PDF/book rendering regression tests (headless) |
| `docs` | Regenerate the docs site and manual HTML |
| `perft-smoke` | `engine perft` plus `engine perft-suite` at a quick depth |
| `jar` | Build `crtk.jar` and smoke the launcher |
| `recommended` / `ci` / `release` | Composite runs (`ci` also runs `docs`) |

Useful environment overrides: `CRTK_SUITE_JOBS` for parallel composite phases and `CRTK_TEST_JOBS` for parallel Java regression classes (both default to `1` for stable shared `out/` and `out/tmp` use; raise them explicitly to parallelize), `CRTK_TEST_TIMEOUT` for per-test timeouts, `CRTK_PERFT_THREADS`, `CRTK_PERFT_DEPTH`, `CRTK_PERFT_SUITE_DEPTH`, and `CRTK_REQUIRE_STOCKFISH=1` to fail (instead of skip) the `uci` phase when Stockfish is missing.

Tests under `src/testing/` are self-contained classes, each with its own `main(...)`; you can run one directly, e.g. `java -cp out testing.UCIRegressionTest`.

## Repository layout

| Path | What lives there |
| --- | --- |
| `src/application/` | CLI entry point, command dispatch (`application.cli`), config, and the Swing Workbench (`application.gui.workbench`) |
| `src/chess/` | The shared chess core plus engines, evaluators, neural nets, mining, tagging, rendering, and I/O |
| `src/utility/` | Dependency-free helpers (JSON, TOML, SVG, argv parsing) |
| `src/testing/` | Self-contained regression tests, each with a `main(...)` |
| `scripts/` | Build/regen tooling: docs site, manual PDF, regression runner, release helpers |
| `assets/diagrams/` | Graphviz `.dot` sources and rendered `.png` exports |
| `wiki/` | Markdown documentation — the source for the generated site |
| `docs/` | Generated static site and manual (build output; do not hand-edit) |
| `native/` | CUDA, ROCm, and oneAPI native backends for perft and OTIS |
| `config/` | Seeded TOML config and engine protocol files |
| `models/` | Local network weights (gitignored; never commit weights) |
| `dump/`, `session/` | Default output and cache directories (gitignored) |

Key anchors in the source:

- `application.cli.CliRegistry` maps each area/action to a `CliCommand` under `application.cli.command`.
- `application.cli.Constants` holds the canonical command tokens — change command names here, not by hand-editing strings.
- `chess.core` is the canonical rules engine: `Position`, `MoveGenerator`, `MoveList`, `Fen`, `SAN`, `Setup`. Everything reuses it; do not fork rules logic.
- `chess.debug` holds the perft runners (`Perft`, `PerftSuite`, GPU `SplitPerft`).
- `chess.uci` drives external engines and hosts the Filter DSL used for puzzle gating.

## Regenerating documentation

The docs site and manual are generated from `wiki/*.md`. Edit the Markdown in `wiki/`, never the files in `docs/`.

```bash
python3 scripts/build_docs_site.py        # regenerate docs/ static site
python3 scripts/build_manual_pdf.py       # regenerate the PDF manual
python3 scripts/build_manual_pdf.py --html-only   # skip the headless-Chromium PDF step
```

Or via the runner:

```bash
./scripts/run_regression_suite.sh docs
```

The static-site generator supports a restricted Markdown subset (one `# Title`, `##`/`###` headings, pipe tables, fenced code, `- `/`1. ` lists with no nesting, blockquotes, `.md`-relative links). Stay inside it. After changing a command name or behavior, regenerate the docs so the site stays consistent.

## CLI conventions

- **Noun then verb.** Every command is `crtk <area> <action>`. Areas: `record`, `fen`, `gen`, `batch`, `move`, `engine`, `mate`, `position`, `book`, `puzzle`, `config`, `workbench`, `doctor`, `clean`, `help`, `version`.
- **Prefer named flags** for structured values: `--fen`, `--input|-i`, `--output|-o`, `--format`, `--protocol-path|-P`. Put options before free-form args when scripting; use `--` if a value could look like a flag.
- **Use the canonical names**, never legacy single-word forms. For example use `move list` / `move uci` (not `moves` / `moves-uci`), `engine bestmove` (not `bestmove`), `engine static` (not `eval-static`), `record export csv` (not `record-to-csv`), `record tag-stats` (not `stats-tags`), `fen pgn` (not `pgn-to-fens`), `puzzle mine` (not `mine-puzzles`). When in doubt, check `crtk help <area> [<action>]` — anything not in the help output does not exist.
- **Determinism.** Outputs must be reproducible: stable ordering, no wall-clock or random noise unless explicitly seeded, and JSON/JSONL shapes that stay backward-compatible. Bound long runs (node/duration caps) and return clean exit codes so CI and agents can rely on them.
- **One shared core.** Route any rules, FEN/SAN/UCI, or movegen need through `chess.core`. Do not duplicate rules logic in a command or the Workbench.

## Neural-network honesty

Represent network fidelity accurately. crtk's evaluators (classical/static, NNUE, LC0 CNN, OTIS) are usable evaluators, and the BT4 path is simplified and experimental. Do **not** claim bit-exact LC0/BT4 parity anywhere in code, comments, or docs.

## Git attribution policy (hard rule)

This overrides any default agent behavior:

- Commit **only** as the human maintainer: `LenniAConrad <lennart.a.conrad@gmail.com>`.
- **Never** add Claude, Anthropic, or any AI assistant as an author, committer, contributor, co-author, sign-off, or generated-by identity.
- **Never** add `Co-authored-by`, `Also-by`, `Assisted-by`, or `Generated-by` trailers for AI assistants, bots, or automated agents.
- Inspect the final commit message before committing and strip any assistant attribution trailer.
- Commit or push only when explicitly asked. If on the default branch, branch first.
- Never commit network weights or large artifacts; `models/`, `dump/`, and `session/` are gitignored for a reason.

## See also

- [Development Notes](development-notes.md) — fuller contributor map of the source tree
- [AI Agents](ai-agents.md) — agent-facing workflows and reproducible command patterns
- [Quality and Testing](quality-and-testing.md) — what the regression suite verifies
- [Build and Install](build-and-install.md) — prerequisites and install paths
- [Command Reference](command-reference.md) — every area, action, and flag
