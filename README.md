# Universal Chess CLI (ucicli)

![ucicli GitHub banner](assets/ucicli-github-banner.png)

Universal Chess CLI is a zero-dependency Java 17 toolkit for driving UCI chess engines, mining tactical puzzles, converting analysis dumps, and inspecting positions without needing a GUI.

---

## Docs (full)

- Start here: `wiki/README.md`
- Commands: `wiki/command-reference.md`
- Examples: `wiki/example-commands.md`
- Config: `wiki/configuration.md`

## Roadmap / ideas

A short list of proposed future subcommands and contributor tooling lives in `wiki/roadmap.md`.

---

## Quickstart

Requirements:
- Java 17+ JDK (needs `javac`)
- A UCI engine on `PATH` (e.g. Stockfish) or configured via `config/*.engine.toml`

Build (no Maven/Gradle):

```bash
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
```

Run:

```bash
java -cp out application.Main help
java -cp out application.Main <command> [options]
```

Linux convenience installer (Debian/Ubuntu):

```bash
./install.sh
ucicli help
```

More: `wiki/build-and-install.md`

---

## Release (Linux CUDA)

This repo includes an optional CUDA JNI backend under `native/cuda/`.

To build and package a CUDA-enabled Linux x86_64 release artifact:

```bash
scripts/make_release_linux_cuda.sh --version v0.0.0
```

To include `models/` in the release bundle:

```bash
scripts/make_release_linux_cuda.sh --version v0.0.0 --include-models
```

Outputs:
- `dist/ucicli-<version>-linux-x86_64-cuda.tar.gz`
- `dist/SHA256SUMS`

---

## What It Does

- `mine`: evaluate lots of seeds (random / `.txt` / `.pgn`) and emit puzzles + non-puzzles JSON
- `record-to-plain`, `record-to-csv`, `record-to-pgn`: convert `.record` analysis dumps to `.plain`, CSV, or PGN
- `record-to-dataset`, `stack-to-dataset`: export NumPy tensors for training (features `(N, 781)`)
- `print`: pretty-print a FEN as ASCII
- `display`: open a small GUI board view (overlays + optional ablation)
- `render`: save a board image to disk (PNG/JPG/BMP)
- `cuda-info`: show LC0 CUDA backend availability and device info
- `analyze`, `bestmove`: analyze a FEN and extract the best move
- `moves`, `tags`: list legal moves or tags for a FEN
- `stats`, `stats-tags`: summarize dumps or tag distributions
- `perft`: validate move generation at a given depth
- `pgn-to-fens`: extract FEN seeds from PGN files
- `eval`: evaluate a position with LC0 or classical heuristics
- `clean`: remove/clean derived artifacts
- `config`: show/validate resolved configuration

---

## Examples

See `wiki/example-commands.md` for more.

```bash
# Print a FEN
ucicli print --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

# Mine puzzles from random seeds
ucicli mine --random-count 50 --output dump/
```

---

## Configuration / Filters / Outputs / Logs

- Configuration: `wiki/configuration.md`
- Mining pipeline: `wiki/mining.md`
- Filter DSL: `wiki/filter-dsl.md`
- Outputs & logs: `wiki/outputs-and-logs.md`

---

## Optional evaluators

ucicli supports two different “LC0” paths:

- LC0 as a UCI engine for mining (usually needs `.pb.gz` weights): see `wiki/lc0.md`
- Built-in Java LC0 evaluator for `eval`/`display`/ablation (uses `models/lc0_744706.bin`): see `wiki/lc0.md` and `models/README.md`

---

## License

See `LICENSE.txt`.
