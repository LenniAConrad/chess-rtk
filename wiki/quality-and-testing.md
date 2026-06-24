# Quality and Testing

Because everything in crtk routes through one chess core — legal move generation, make/undo, attack detection, FEN/SAN/UCI conversion, Chess960, perft — a single regression suite can guard parsing, engine analysis, datasets, rendering, and PDF publishing in one pass. Correctness isn't a phase here; it's the product. The suite is a plain Bash script with named targets: `./scripts/run_regression_suite.sh <target>`. Each target is deterministic and builds with `javac --release 17` (no Maven, no Gradle). The same script drives your laptop and GitHub Actions, which is the whole point — a green local run is the same checks that gate CI, so it means a green CI run.

> Run commands from the repository root (`/home/lennart/Code/chess-rtk`). Examples below use `crtk` as shorthand for `java -jar crtk.jar`.

## The One Command You Run Most

```bash
./scripts/run_regression_suite.sh recommended
```

This is the pass to run before pushing. It builds, lints sources and shell scripts, then walks the core, CLI, engine, UCI, book, perft-smoke, and jar groups in order. Green means the change is ready to share.

The `release` alias points at the same target, so cutting a release runs exactly these checks and nothing extra. One bar, used everywhere.

## Suite Targets

Run a single group when you only touched one area and want a fast answer. The script announces each step as it goes (`==> build`, `==> lint`, `==> jar`, and so on), so a failure tells you immediately which stage broke.

| Target | What it does | Run it when |
| --- | --- | --- |
| `build` | Clean-compiles every source under `src/` with `javac --release 17` into `out/` | Checking compilation only |
| `lint` | Recompiles with `-Xlint:all`, runs the shell-script lint, and `git diff --check` for whitespace errors | Tightening source quality before a commit |
| `scripts` | `bash -n` syntax check on every tracked `*.sh`, `scripts/check_no_weights_tracked.sh`, plus `shellcheck -S error` when ShellCheck is on `PATH` | Editing shell scripts or model-artifact guards |
| `core` | Position, move-generation, split-perft, SAN, JSON, XML-security, install-script, source-header, Chess960, parser, tagging, and workbench-structure regressions | Changing `chess.core`, FEN, SAN, move generation, make/undo, or tagging |
| `cli` | CLI command, PGN, book/cover/PDF command, and puzzle collection/study command regressions | Changing command parsing or command output |
| `engine` | Built-in engine, puzzle-difficulty, BT4, T5, OTIS-backend, and GPU-perft regressions | Changing the built-in engine, an evaluator, or puzzle scoring |
| `uci` | UCI orchestration regression plus a live `engine uci-smoke` against Stockfish if available | Changing external-engine orchestration or protocol handling |
| `book` | Book, chess-PDF, and PDF-document regressions (headless) | Changing book, cover, or PDF rendering |
| `docs` | Rebuilds the docs site and the HTML manual from the wiki source | Changing wiki docs or the docs generators |
| `perft-smoke` | Runs `engine perft` and `engine perft-suite` at a shallow depth | Changing legality, Chess960, castling, en-passant, or promotion |
| `jar` | Packages `crtk.jar` and smoke-tests `--help`, `workbench --help`, and `gui --help` | Verifying the packaged artifact |
| `recommended` | build + lint + core + cli + engine + uci + book + perft-smoke + jar | Default pre-push pass |
| `ci` | build + lint + core + cli + engine + book + docs + uci + perft-smoke + jar | Reproducing the full CI gate locally |
| `release` | Alias for `recommended` | Preparing a packaged release |

A few examples:

```bash
./scripts/run_regression_suite.sh build
./scripts/run_regression_suite.sh core
./scripts/run_regression_suite.sh perft-smoke
./scripts/run_regression_suite.sh ci
```

No target falls back to `recommended`. An unknown target prints the valid list and exits non-zero, so a typo never quietly runs the wrong thing.

## Move-Generation Confidence with Perft

Perft (performance test) counts the legal leaf nodes reachable at a fixed depth. The answer is one integer with a known correct value, which is exactly what makes it merciless: any change that touches legality, Chess960 castling, en-passant, or promotion either reproduces the count or it doesn't.

```bash
crtk engine perft --startpos --depth 5 --threads 4
crtk engine perft --fen "<FEN>" --depth 5 --divide --threads 4
crtk engine perft-suite --depth 6 --threads 4
```

`engine perft` walks one position. `--divide` (alias `--per-move`) breaks the total down by root move, which is how you bisect a mismatch to a single move; `--threads N` splits those root moves across workers.

`engine perft-suite` checks a set of stored truth positions against the core move generator. Depth is clamped to `1..6` (default `6`). Point `--suite PATH` at a tab-delimited file to test your own positions — rows are parsed by column count, so several shapes work:

```text
name<TAB>depth<TAB>fen<TAB>nodes
fen<TAB>depth<TAB>nodes
name<TAB>fen<TAB>nodes
fen<TAB>nodes
```

Neither command touches Stockfish or any external UCI engine. Both exercise the core move generator and only that — what they measure is crtk's own legality, not someone else's.

### Optional GPU Perft

When a native backend (CUDA, ROCm, or oneAPI) is built and loadable, perft hands leaf expansion to the GPU. When it isn't, crtk takes the CPU path without comment, so the same command works on any machine.

```bash
crtk engine perft --startpos --depth 6 --gpu --split 4
crtk engine perft-suite --depth 6 --gpu --split 4
crtk engine gpu
```

`--gpu` requests the native backend; `--split N` is how deep the CPU expands before handing whole subtrees to the GPU — the knob that trades launch overhead against parallelism. Run `crtk engine gpu` to print the JNI backend status (`--verbose` for detail). See [GPU Acceleration](gpu.md) and [Build and Install](build-and-install.md) for setup.

## Tuning the Perft Smoke Run

A perft smoke test is only useful if it fits the runner. Three environment variables scale the cost of `perft-smoke` to your hardware; a fourth decides whether the `uci` target insists on a live Stockfish.

| Variable | Default | Effect |
| --- | --- | --- |
| `CRTK_PERFT_THREADS` | Online CPU count (fallback `2`) | Worker threads for `engine perft` and `engine perft-suite` |
| `CRTK_PERFT_DEPTH` | `4` | Depth passed to `engine perft` in `perft-smoke` |
| `CRTK_PERFT_SUITE_DEPTH` | `4` | Depth passed to `engine perft-suite` in `perft-smoke` |
| `CRTK_REQUIRE_STOCKFISH` | `0` | When `1`, the `uci` target fails if Stockfish is missing instead of skipping the live smoke |

```bash
CRTK_PERFT_DEPTH=5 CRTK_PERFT_SUITE_DEPTH=5 CRTK_PERFT_THREADS=8 \
  ./scripts/run_regression_suite.sh perft-smoke
```

## Engine and UCI Confidence

Reach for these after touching external-engine setup or protocol handling. Confirm the configuration parses, then prove the round trip with the smallest search that can fail:

```bash
crtk config validate
crtk engine uci-smoke --nodes 1 --max-duration 5s --verbose
```

`engine uci-smoke` launches the configured engine and runs a one-node search — just enough to prove the UCI handshake completes end to end. The `uci` suite target wraps it: the orchestration regression always runs, and the live smoke runs too when `stockfish` is on `PATH`. Set `CRTK_REQUIRE_STOCKFISH=1` and a missing Stockfish becomes a hard failure instead of a silent skip. That is precisely how CI runs it, because a test that quietly skips itself isn't a test.

See [Configuration](configuration.md) for protocol TOML files and [LC0 and External Engines](lc0.md) for engine setup.

## Determinism

Same inputs, byte-identical outputs, every run. This isn't a nicety — it's what lets a golden comparison mean anything and what lets a reported failure reproduce on the first try.

- The build is hermetic: `compile_sources` deletes `out/`, finds every `*.java` under `src/`, sorts the list, and compiles with a fixed `--release 17`. No discovery order to drift.
- Core checks compare against stored golden values — perft node counts, SAN strings, tag fixtures, JSON shapes — so any drift lands as a concrete diff, not a vague flake.
- `lint` runs `git diff --check`, which means a stray trailing space or a leftover conflict marker fails the suite outright.
- The random generators (`--randompos`, `fen generate`) break the rule on purpose: they draw fresh positions every run. Pin an explicit FEN in a regression instead of assuming a "random" input will recur across runs or machines — it won't.

To lean on determinism directly, run a command twice and diff:

```bash
crtk move list --startpos --format both > /tmp/a.txt
crtk move list --startpos --format both > /tmp/b.txt
diff /tmp/a.txt /tmp/b.txt && echo "deterministic"
```

## What the Regression Groups Cover

Each group maps to a cluster of focused regression classes under the `testing` package. A sample of what's behind each target:

- **Core** — `PositionRegressionTest`, `CoreMoveGenerationRegressionTest`, `SplitPerftRegressionTest`, `SANRegressionTest`, `Chess960SetupRegressionTest`, `ParserRegressionTest`, `TaggingRegressionTest`, `TagFixtureRegressionTest`, plus JSON, XML-security, install-script, source-header, and workbench-structure checks.
- **CLI** — `CLICommandRegressionTest`, `PGNRegressionTest`, book/cover/PDF command tests, and puzzle collection/study command tests.
- **Engine** — `BuiltInEngineRegressionTest`, `PuzzleDifficultyRegressionTest`, `BT4RegressionTest`, `T5RegressionTest`, `OtisBackendRegressionTest`, `GpuPerftRegressionTest`.
- **UCI** — `UCIRegressionTest`, plus the live `engine uci-smoke`.
- **Book** — `BookRegressionTest`, `ChessPDFRegressionTest`, `PDFDocumentRegressionTest` (run headless).

> A word on neural-network fidelity, stated plainly. The LC0 CNN, NNUE, and OTIS evaluators are usable research evaluators; the BT4 path is simplified and experimental. crtk does **not** claim bit-exact parity with upstream LC0/BT4, and the docs won't pretend otherwise. `BT4RegressionTest`, `T5RegressionTest`, and `OtisBackendRegressionTest` guard crtk's own deterministic behavior — they pin what crtk does, not what upstream does.

## Documentation Confidence

These pages and the printable manual are generated from this same wiki source, which puts documentation inside the quality gate rather than beside it.

```bash
./scripts/run_regression_suite.sh docs
```

Under the hood the `docs` target runs the two generators in sequence:

```bash
python3 scripts/build_docs_site.py
python3 scripts/build_manual_pdf.py --html-only
```

After regenerating, eyeball the links and assets under `docs/` before publishing. See [Development Notes](development-notes.md) for the docs pipeline.

## Continuous Integration

CI lives in `.github/workflows/ci.yml` and fires on every pull request, every push to `main`, and manual dispatch. It calls the same `run_regression_suite.sh` you run locally — there is no second CI-only code path waiting to drift out of sync with the one you can see.

The workflow:

1. Checks out the repo and sets up Temurin Java 17.
2. Installs `shellcheck` and `stockfish` so lint and the live UCI smoke have what they need.
3. Runs each target as its own step — `build`, `lint`, `core`, `cli`, `engine`, `book`, `docs`, `uci`, `perft-smoke` — so a red check names the failing stage directly. The packaged-jar smoke rides along inside `recommended`/`ci` locally.
4. Pins the smoke run to `CRTK_PERFT_THREADS=2`, `CRTK_PERFT_DEPTH=4`, `CRTK_PERFT_SUITE_DEPTH=4`, and sets `CRTK_REQUIRE_STOCKFISH=1` on the UCI step so a missing engine breaks the build rather than passing on a skip.

To reproduce the full CI gate in one local command:

```bash
./scripts/run_regression_suite.sh ci
```

## Environment Health Check

Separate from the regression suite, `doctor` audits the environment around crtk — Java version, configuration, engine protocols, and local artifacts:

```bash
crtk doctor
crtk doctor --strict
```

`--strict` promotes warnings to a non-zero exit, which is what you want in a script or a pre-commit hook where a warning should stop the line. See [Troubleshooting](troubleshooting.md) and [Build and Install](build-and-install.md) when `doctor` flags something.

## A Good Bug Report

Given the determinism guarantees above, a good report is one someone else can replay exactly. Include:

- The exact command, every flag.
- The full FEN, or the shape of the input file.
- Java version and operating system.
- The engine protocol TOML, if an external engine is in play.
- Whether `./scripts/run_regression_suite.sh recommended` passes.
- The failing perft row, or the name of the failing regression class.

See also [Build and Install](build-and-install.md), [Configuration](configuration.md), and [Development Notes](development-notes.md).
