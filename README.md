# ChessRTK (crtk) — chess research toolkit

ChessRTK is a reproducible, zero-dependency Java 17 toolkit for chess research: drive UCI engines, mine tactical puzzles, convert/inspect analysis dumps, and export ML-ready datasets for AI experiments and training (currently emitted as tensor files) without needing a GUI.

CLI command: `crtk`

Command compatibility:
- Use canonical command names only.
- Removed commands: `gui2`, `cuda-info`, `mine`, `evaluate`, `stack-to-dataset`.
- Use instead: `gui`, `gpu-info`, `mine-puzzles`, `eval`.

Built for:
- chess researchers / dataset builders / engine experimenters
- high-throughput, scriptable pipelines (CLI-first)

Not a:
- playing app or chess GUI

---

## Workflow map

Use the CLI as a set of small, scriptable tools:

| Goal | Start with | Follow-up docs |
| --- | --- | --- |
| Inspect one position | `print`, `display`, `render`, `chess-pdf` | `wiki/example-commands.md` |
| Ask an engine | `analyze`, `bestmove`, `threats` | `wiki/configuration.md`, `wiki/lc0.md` |
| Generate seeds | `pgn-to-fens`, `gen-fens` | `wiki/mining.md` |
| Mine puzzles | `mine-puzzles` | `wiki/mining.md`, `wiki/filter-dsl.md` |
| Clean and split records | `records`, `record-analysis-delta` | `wiki/outputs-and-logs.md` |
| Export training data | `record-to-dataset`, `record-to-lc0`, `record-to-classifier`, `record-to-training-jsonl` | `wiki/datasets.md` |
| Publish diagrams or books | `chess-pdf`, `chess-book`, `chess-book-cover` | `wiki/book-publishing.md` |
| Automate checks | `moves-*`, `bestmove-*`, `fen-after`, `play-line`, `perft-suite` | `wiki/ai-agents.md` |

## Pipeline overview

![ChessRTK pipeline overview](assets/diagrams/crtk-pipeline-overview.png)

Diagram source: `assets/diagrams/crtk-pipeline-overview.dot` (render with `dot -Tpng -Gdpi=160 -o assets/diagrams/crtk-pipeline-overview.png assets/diagrams/crtk-pipeline-overview.dot`).

---

## Research workflow (copy/paste)

Mine puzzles from a PGN (via FEN seeds) and convert the results to CSV + PGN:

```bash
crtk pgn-to-fens --input games.pgn --output seeds.txt
crtk mine-puzzles --input seeds.txt --output dump/run.json --engine-instances 4 --max-duration 60s
crtk record-to-csv --input dump/run.puzzles.json --output dump/run.puzzles.csv
crtk record-to-pgn --input dump/run.puzzles.json --output dump/run.puzzles.pgn
```

### Mining decision gates

![ChessRTK mining decision gates](assets/diagrams/crtk-mining-gates.png)

Diagram source: `assets/diagrams/crtk-mining-gates.dot` (render with `dot -Tpng -Gdpi=100 -o assets/diagrams/crtk-mining-gates.png assets/diagrams/crtk-mining-gates.dot`).

Other common primitives:

- Mine random seeds: `crtk mine-puzzles --random-count 200 --output dump/random.json` (or endless with `--random-infinite`)
- Validate movegen: `crtk perft --depth 5`
- Engine probing: `crtk analyze --fen "<FEN>" --max-duration 2s`, `crtk bestmove --fen "<FEN>" --max-duration 200`, `crtk threats --fen "<FEN>" --max-duration 2s`
- Position inspection: `crtk print --fen "<FEN>"`, `crtk display --fen "<FEN>" --special-arrows`, `crtk render --fen "<FEN>" --output dump/pos.png`, `crtk chess-pdf --fen "<FEN>" -o dump/pos.pdf`, `crtk gui-web --fen "<FEN>" --dark`
- Dataset export (NNUE): `crtk record-to-dataset --input dump/run.puzzles.json --output training/puzzles`
- Dataset export (LC0): `crtk record-to-lc0 --input dump/run.puzzles.json --output training/puzzles`
- Dataset export (classifier): `crtk record-to-classifier -i dump/run.puzzles.json -i dump/run.nonpuzzles.json -o training/classifier/run`

## Book/PDF workflow (copy/paste)

Render a native puzzle book PDF, then generate a matching cover PDF from the
same manifest:

```bash
crtk chess-book -i books/puzzles.toml -o dist/puzzles.pdf
crtk chess-book-cover -i books/puzzles.toml -o dist/puzzles-cover.pdf \
  --binding paperback --interior white-bw --pages 120
```

Make a quick diagram sheet from PGN mainlines:

```bash
crtk chess-pdf --pgn games.pgn -o dist/games.pdf --page-size a5 --diagrams-per-row 1
```

## Single-position toolbox

![ChessRTK single-position toolbox](assets/diagrams/crtk-position-toolbox.png)

Diagram source: `assets/diagrams/crtk-position-toolbox.dot` (render with `dot -Tpng -Gdpi=160 -o assets/diagrams/crtk-position-toolbox.png assets/diagrams/crtk-position-toolbox.dot`).

Agent-friendly shortcuts:
- `moves-uci`, `moves-san`, `moves-both`
- `bestmove-uci`, `bestmove-san`, `bestmove-both`
- `uci-to-san`, `san-to-uci`, `fen-after`, `play-line`
- `eval-static`, `perft-suite`, `records`, `puzzles-to-pgn`, `pgn-to-fens`

## Docs (full)

- Start here: `wiki/README.md`
- Commands: `wiki/command-reference.md`
- Examples: `wiki/example-commands.md`
- Config: `wiki/configuration.md`
- Mining: `wiki/mining.md`
- Datasets: `wiki/datasets.md`
- Book publishing: `wiki/book-publishing.md`
- Filter DSL: `wiki/filter-dsl.md`
- T5 tag-to-text: `wiki/t5.md`
- AI agents & automation: `wiki/ai-agents.md`

---

## Quickstart

Requirements:
- Java 17+ JDK (needs `javac`)
- A UCI engine on `PATH` (e.g. Stockfish) or configured via `config/*.engine.toml`

Model files are not stored in this repo. The installer can fetch the default
LC0J weights, or you can download them manually from:
- https://github.com/LenniAConrad/chess-models

Downloaded `models/*.bin` files are local artifacts and are ignored by git.

Build (no Maven/Gradle):

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
```

Run the focused core regression harness:

```bash
java -cp out testing.PositionRegressionTest
```

Useful smoke checks after larger changes:

```bash
java -cp out application.Main perft-suite
java -cp out testing.BookRegressionTest
java -cp out testing.ChessBookCommandRegressionTest
java -cp out testing.ChessBookCoverCommandRegressionTest
```

Run:

```bash
java -cp out application.Main help
java -cp out application.Main <command> [options]
```

Linux convenience installer (Debian/Ubuntu):

```bash
./install.sh
crtk help
```

Fetch optional model weights without prompting:

```bash
./install.sh --models
```

Update an existing checkout and reinstall:

```bash
./scripts/update.sh
```

Uninstall (removes launcher + local build artifacts):

```bash
./scripts/uninstall.sh
```

More: `wiki/build-and-install.md`

---

## What It Does

- `mine-puzzles`: evaluate lots of seeds (random / `.txt` / `.pgn`) and emit puzzles + non-puzzles JSON
- `record-to-plain`, `record-to-csv`, `record-to-pgn`: convert `.record` analysis dumps to `.plain`, CSV, or PGN
- `records`: merge/filter/split record files (with optional puzzle/DSL filtering)
- `puzzles-to-pgn`: convert mixed puzzle/non-puzzle dumps to PGN games
- `record-analysis-delta`: export per-record evaluation stability/shift metrics as JSONL
- `record-to-dataset`: export eval-regression tensors for AI training (features `(N, 781)`)
- `record-to-lc0`: export LC0-style tensors (inputs/policy/value)
- `record-to-classifier`: export 21-plane inputs and 0/1 labels for the one-logit binary classifier
- `record-to-training-jsonl`: export coarse/fine FEN labels for training pipelines
- `record-to-puzzle-jsonl`: export LC0-policy-aware puzzle JSONL rows
- `gen-fens`: generate large shards of random legal FENs
- `print`: pretty-print a FEN as ASCII (includes tags)
- `display`: open a small GUI board view (overlays + optional ablation)
- `render`: save a board image to disk (PNG/JPG/BMP/SVG)
- `chess-book`: render chess-book JSON/TOML manifests to native PDF
- `chess-book-cover`: render native paperback, hardcover, or ebook cover PDFs for book manifests
- `chess-pdf`: export one or more positions or PGN mainlines to PDF
- `gui`, `gui-web`: launch desktop Swing GUIs (`gui-web` uses the chess-web-inspired layout)
- `gpu-info`: show LC0 GPU backend availability and device info (CUDA/ROCm/oneAPI)
- `analyze`, `bestmove`, `threats`: engine probing and tactical checks on a FEN
- `moves`, `moves-uci`, `moves-san`, `moves-both`: list legal moves for a FEN
- `uci-to-san`, `san-to-uci`: convert a move between UCI and SAN
- `fen-after`: apply a move and return the resulting FEN
- `play-line`: apply a line of moves and return the final (or intermediate) FENs
- `bestmove-uci`, `bestmove-san`, `bestmove-both`: best move shortcuts with fixed output format
- `tags`: list tags for a FEN
- `puzzle-tags`, `tag-text`, `puzzle-text`: generate tag deltas and optional T5 prose summaries
- `stats`, `stats-tags`: summarize dumps or tag distributions
- `perft`, `perft-suite`: validate move generation (single position or suite)
- `pgn-to-fens`: extract FEN seeds from PGN files
- `eval`, `eval-static`: evaluate a position with LC0 or classical heuristics
- `clean`: remove/clean derived artifacts
- `config`: show/validate resolved configuration

---

## Configuration / Filters / Outputs / Logs

- Configuration: `wiki/configuration.md`
- Mining pipeline: `wiki/mining.md`
- Filter DSL: `wiki/filter-dsl.md`
- Book publishing: `wiki/book-publishing.md`
- Outputs & logs: `wiki/outputs-and-logs.md`
- More examples: `wiki/example-commands.md`

---

## Citing

If you use ChessRTK in research, consider citing the repository and pinning a commit hash/tag for reproducibility.

---

## Optional evaluators

ChessRTK supports two different “LC0” paths:

- LC0 as a UCI engine for mining (usually needs `.pb.gz` weights): see `wiki/lc0.md`
- Built-in Java LC0 evaluator for `eval`/`display`/ablation (looks for local, gitignored `models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin`): see `wiki/lc0.md` and `models/README.md`

---

## Release (Linux CUDA)

This repo includes an optional CUDA JNI backend under `native/cuda/`.

To build and package a CUDA-enabled Linux x86_64 release artifact:

```bash
scripts/make_release_linux_cuda.sh --version v0.0.0
```

To include local, gitignored model files in the release bundle:

```bash
scripts/make_release_linux_cuda.sh --version v0.0.0 --include-models
```

Outputs:
- `dist/crtk-<version>-linux-x86_64-cuda.tar.gz`
- `dist/SHA256SUMS`

---

## Roadmap / ideas

A short list of proposed future subcommands and contributor tooling lives in `wiki/roadmap.md`.

---

## License

See `LICENSE.txt`.
