# Frequently Asked Questions

These are the questions that come up before anyone reads the manual: what crtk is, what it expects from your machine, what it deliberately leaves out, and how the parts connect. Every command and flag below is real and copy-pasteable. For broader orientation see [Getting Started](getting-started.md), [Command Reference](command-reference.md), and the [Glossary](glossary.md).

## What is ChessRTK?

ChessRTK ("crtk") is a command-line workbench for chess research, automation, and content production. One shared chess core does the unglamorous, exacting work — legal move generation, make/undo, attack detection, FEN/SAN/UCI parsing, Chess960, perft — and everything else is built on top of it: external-engine analysis, a built-in search, several position evaluators, puzzle mining, deterministic tagging, machine-learning dataset export, and native PDF book publishing. It ships as a single `crtk.jar` and is driven entirely by `crtk <area> <action>`. Run `crtk help` to see every area.

## Is ChessRTK a chess engine?

It contains one, but that is the smaller half of the story. `engine builtin` (alias `engine java`) is an in-process MCTS searcher, and `engine mate` is a brute-force forced-mate prover that needs no neural network at all. When you want genuinely strong analysis you hand the position to an external UCI engine through `engine analyze`, `engine bestmove`, and friends. crtk is the research platform around those engines, not a replacement for them.

```bash
crtk engine builtin --startpos --max-nodes 20000 --format both
crtk engine mate --fen "6k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1" --mate 3
```

## Do I need Stockfish or LC0?

Not for the core. FEN, move, perft, tagging, the built-in MCTS, and the forced-mate prover all run in-process with nothing else installed. An external UCI engine — Stockfish, LC0, whatever you trust — only enters the picture for the UCI-backed commands: `engine analyze`, `engine bestmove`, `engine threats`, `engine compare`, `engine uci-smoke`, the `*-batch` variants, and `puzzle mine`. Describe your engine once in a protocol TOML, then pass it with `--protocol-path` or let it default from `crtk config show`.

```bash
crtk move list --startpos                 # in-process, no engine needed
crtk engine perft --startpos --depth 5    # in-process
crtk engine bestmove --fen "<FEN>"        # needs a configured UCI engine
```

## Does ChessRTK support Chess960?

Yes, and not as a bolt-on — the support lives in the shared core, so every tool inherits it. Enumerate the 960 standard openings by Scharnagl index, draw random ones, mine Chess960 puzzles, and run the full move and FEN toolkit on any of them. Castling and move generation follow Chess960 rules where they differ.

```bash
crtk fen chess960 --index 518            # the standard chess start position
crtk fen chess960 --random --count 5
crtk puzzle mine --chess960 --random-count 100
```

## Is the output deterministic and reproducible?

This is a design goal, not a happy accident. The shared core, perft, tagging, the built-in MCTS, and the dataset and export pipelines return the same output for the same input, which is what makes results worth diffing, caching, and regression-testing. `engine perft-suite` checks move generation against stored reference node counts. The one place determinism stops is the external UCI engine: its output depends on its own threads, hash, and node or time budgets, so pin those budgets when you need byte-stable engine results.

```bash
crtk engine perft-suite --depth 6 --threads 4
```

## Does ChessRTK need a GPU?

No. CPU is the default and the floor. The native backends — CUDA, ROCm, oneAPI — optionally accelerate perft and the OTIS/LC0 evaluators, and when none is available crtk quietly falls back to CPU rather than failing. `engine gpu` reports what actually loaded on your machine; the `--gpu` flag opts perft into the accelerated path.

```bash
crtk engine gpu                                  # report JNI backend status
crtk engine perft --startpos --depth 7 --gpu     # GPU when available, else CPU
```

## What neural networks are supported?

Several evaluators are selectable through `engine eval`, `engine builtin`, and the workbench: classical/static, NNUE, an LC0 CNN, and OTIS. T5 natural-language models drive position summaries through `fen text` and `puzzle text`. A caveat worth stating plainly: these are usable evaluators, not bit-exact reproductions of the upstream engines, and the BT4 path especially is simplified and experimental. Drop local weight files under `models/` and point at them with `--weights`, or `--model` for the T5 models.

```bash
crtk engine eval --fen "<FEN>" --otis --weights models/otis.bin
crtk fen text --fen "<FEN>"
```

## Does book publishing require LaTeX?

No — no LaTeX, TeX, or external typesetting toolchain of any kind. The `book` commands render print-ready PDFs natively: dense puzzle collections with `book collection`, annotated studies with `book study`, diagram sheets with `book pdf`, full interiors with `book render`, and matching covers with `book cover`.

```bash
crtk book collection --input dump/puzzles.json --pdf-output dist/collection.pdf
crtk book pdf --startpos --output dist/diagram.pdf
```

## What Java version does ChessRTK require?

Java 17 — that exact language and class-file level. There is no Maven or Gradle anywhere in the build, so a JDK and `javac` are all you need. See [Build and Install](build-and-install.md) for the full procedure.

```bash
javac --release 17 -d out $(find src -name "*.java")
```

## How is ChessRTK licensed?

Free and open-source software under the **GPL-3.0-only** license. The full text is in `LICENSE.txt` at the repository root.

## Is there a GUI?

Yes. `crtk workbench` (alias `crtk gui`) opens a native desktop Swing application over the toolkit: an interactive board, play-vs-engine, command forms for the CLI areas, batch jobs, dataset tools, logs, publishing previews, puzzle views, and neural-network visualizers. Play mode uses in-process opponents — alpha-beta or MCTS with local evaluators — while the live-analysis controls drive a configured external UCI engine.

```bash
crtk workbench
crtk workbench --fen "<FEN>" --flip
```

## What is a `.record` file?

It is crtk's reusable analysis-record JSON format, and the hub of the data pipeline. `record` commands summarize records (`record stats`, `record tag-stats`, `record analysis-delta`), merge, filter, and split them (`record files`), export them to other formats (`record export plain|csv|pgn|puzzle-jsonl|puzzle-elo-jsonl|training-jsonl`), and turn them into ML tensors (`record dataset npy|lc0|classifier`). Most record and puzzle commands take a Filter DSL through `--filter` to pick rows.

```bash
crtk record stats --input dump/run.record
crtk record export pgn --input dump/run.record --output dump/run.pgn
crtk record dataset npy --input dump/run.record
```

## How do I check my setup?

`crtk doctor` inspects your Java version, configuration, engine protocol, and local artifacts in one pass. Add `--strict` to exit non-zero on warnings — useful when the check runs in CI.

```bash
crtk doctor
crtk doctor --strict
```

See also [Troubleshooting](troubleshooting.md) and [Configuration](configuration.md) for deeper diagnostics.
