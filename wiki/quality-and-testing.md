# Quality And Testing

ChessRTK is correctness-sensitive. FEN parsing, SAN conversion, Chess960
castling, en-passant, promotion, make/undo state, and move generation all feed
engine analysis, datasets, rendering, and publishing.

## Default Check Before Pushing

```bash
./scripts/run_regression_suite.sh recommended
```

This is the normal local confidence pass. It builds the project, runs lint, and
executes the core regression groups used most often during development.

## Focused Suites

| Suite | Use when |
| --- | --- |
| `build` | checking compilation only |
| `lint` | checking source quality guardrails |
| `scripts` | checking shell script syntax and ShellCheck errors when available |
| `core` | changing `chess.core`, FEN, SAN, move generation, or make/undo |
| `cli` | changing command parsing or command output |
| `engine` | changing the built-in engine, evaluator, or puzzle difficulty scoring |
| `uci` | changing external-engine orchestration |
| `book` | changing PDF/book/rendering code |
| `docs` | changing wiki docs or docs generation scripts |
| `perft-smoke` | changing legality, Chess960, castling, en-passant, or promotion behavior |
| `release` | preparing a packaged release |

Examples:

```bash
./scripts/run_regression_suite.sh build
./scripts/run_regression_suite.sh lint
./scripts/run_regression_suite.sh core
```

## Move-Generation Confidence

Use perft whenever a change can affect legal moves or board state.

```bash
crtk engine perft --startpos --depth 4 --threads 4
crtk engine perft --fen "<FEN>" --depth 5 --divide --threads 4
crtk engine perft-suite --depth 6 --threads 4
```

`engine perft-suite` compares stored truth positions against the Java move
generator. It does not start Stockfish or any other external engine.

## Engine And UCI Confidence

Use UCI smoke tests when external engine setup or protocol handling changed.

```bash
crtk config validate
crtk engine uci-smoke --nodes 1 --max-duration 5s --verbose
```

Use the built-in engine regression when `chess.engine` or evaluator integration
changed:

```bash
java -cp out testing.BuiltInEngineRegressionTest
```

## Publishing Confidence

Use these checks for book, PDF, or rendering changes:

```bash
java -Djava.awt.headless=true -cp out testing.BookRegressionTest
java -Djava.awt.headless=true -cp out testing.ChessBookCommandRegressionTest
java -Djava.awt.headless=true -cp out testing.ChessBookCoverCommandRegressionTest
```

## Documentation Confidence

The website-style docs are generated from the repository wiki source:

```bash
python3 scripts/build_docs_site.py
```

The printable manual uses the same wiki source and writes both
`docs/manual.html` and `docs/chessrtk-manual.pdf`:

```bash
python3 scripts/build_manual_pdf.py
```

Then check links/assets in `docs/` before publishing. The GitHub Wiki is a
separate repository, so changes pushed there should also pass a simple internal
link check.

## Recommended Bug Report Baseline

When reporting a bug, include:

- exact command
- full FEN or input file shape
- Java version and operating system
- UCI engine protocol TOML when relevant
- whether `./scripts/run_regression_suite.sh recommended` passes
- any failing perft row or regression test name
