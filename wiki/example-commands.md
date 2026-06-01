# Example Commands

Every recipe below is something you can paste into a terminal and watch run to completion: study one position, verify the move generator, mine puzzles from a PGN, export training datasets, publish a puzzle book, run batch analysis. They lean on a single shared chess core, so move generation, FEN/SAN/UCI handling, and tagging behave the same whether the call comes from the CLI, a batch script, or the desktop [Workbench](workbench.md). Same inputs and flags, same output — which is what makes these recipes worth saving and rerunning.

The examples assume the `crtk` launcher is on your `PATH`. Running from compiled classes instead? Swap `crtk` for `java -cp out application.Main`. See [Getting Started](getting-started.md) for installation and [Command Reference](command-reference.md) for the full option list.

## Conventions

- Replace `<FEN>` with a real FEN string in double quotes.
- Put named flags before free-form positional arguments when scripting, and use `--` if a value could look like a flag.
- Engine-backed commands (`engine analyze`, `engine bestmove`, `puzzle mine`) use an external UCI engine resolved from your config or `--protocol-path`. Run [`crtk doctor`](troubleshooting.md) first if engine discovery is uncertain.
- Bound every engine search with `--max-duration` or `--max-nodes` so runs are predictable.

## Study One Position

A position is worth looking at from several angles before you trust any single read of it. Pretty-print the board, list the legal moves in SAN, get a deterministic textual description, then hand it to a UCI engine.

```bash
crtk fen print --fen "r1bqk2r/ppppbppp/3n4/4R3/8/8/PPPP1PPP/RNBQ1BK1 b kq - 2 8"
crtk move list --fen "r1bqk2r/ppppbppp/3n4/4R3/8/8/PPPP1PPP/RNBQ1BK1 b kq - 2 8" --format both
crtk position describe --fen "r1bqk2r/ppppbppp/3n4/4R3/8/8/PPPP1PPP/RNBQ1BK1 b kq - 2 8" --detail full
crtk engine analyze --fen "r1bqk2r/ppppbppp/3n4/4R3/8/8/PPPP1PPP/RNBQ1BK1 b kq - 2 8" --multipv 3 --max-duration 5s
```

`fen print` and `move list` never touch an engine; they run in the core. `position describe` emits deterministic text from static analysis. Only `engine analyze` shells out — to the configured UCI engine — and prints the top three principal variations.

### Visualize and Tag the Same Position

The same position, seen rather than read: open it in a window with overlays, write a board image to disk, then emit deterministic tags and a natural-language summary.

```bash
crtk fen display --fen "<FEN>" --arrow e5e1 --circle e4 --legal d6 --special-arrows
crtk fen render --fen "<FEN>" -o dump/position.svg --arrow e2e4 --size 1200
crtk fen tags --fen "<FEN>" --include-fen
crtk fen text --fen "<FEN>" --model models/t5.bin --include-fen
```

`fen tags` produces a JSON tag set from the static tagger; add `--analyze --max-duration 2s --multipv 3` to fold engine output into the tags. `fen text` runs the T5 summarizer over those tags and returns a plain-English sentence. Treat that sentence as a readable gloss, not as analysis — T5 describes the tags it was given, it does not reason about the position.

## Verify the Core (perft-suite)

Before you trust anything downstream, confirm the move generator counts correctly. The suite checks it against stored ground-truth node counts across standard, Chess960, promotion, en-passant, castling, and stress positions — no external engine involved.

```bash
crtk engine perft-suite --depth 6 --threads 4
```

A clean pass means the move generator is sound on this build — and since every other workflow inherits its determinism from the core, this is the check worth running first. For one position with the full set of counters (nodes, captures, en-passant, castles, promotions, checks, checkmates), or a per-root-move breakdown, reach for `engine perft`:

```bash
crtk engine perft --fen "<FEN>" --depth 5
crtk engine perft --fen "<FEN>" --depth 5 --divide --threads 4
crtk engine perft-suite --depth 6 --gpu --split 4
```

The `--gpu` form routes node counting through the optional native CUDA/ROCm/oneAPI backend, and quietly falls back to CPU when no usable backend is present — so it never fails just because the hardware isn't there. Confirm what you actually have with `crtk engine gpu`. See [In-House Engine](in-house-engine.md) and [Architecture](architecture.md) for details.

## Mine Puzzles From a PGN

A PGN of games is a seam of tactics waiting to be cut out. crtk walks candidate positions, runs the configured engine, and gates each line through its Filter DSL quality system — what survives is a puzzle.

```bash
crtk puzzle mine --input games.pgn --output dump/pgn.json --engine-instances 4 --max-duration 60s
```

Accepted lines land in `dump/pgn.puzzles.json`, rejected ones in `dump/pgn.nonpuzzles.json` — keep the rejects; they make excellent negatives for a classifier. Other seed sources follow the same shape:

```bash
crtk puzzle mine --random-count 50 --output dump/
crtk puzzle mine --input seeds/fens.txt --output dump/fens.json
crtk puzzle mine --chess960 --random-count 200 --output dump/
```

With a dump in hand, summarize it, turn it into PGN you can scroll through in any viewer, and look at how the tags break down:

```bash
crtk record stats -i dump/pgn.puzzles.json
crtk record tag-stats -i dump/pgn.puzzles.json
crtk puzzle pgn -i dump/pgn.puzzles.json -o dump/pgn.pgn
```

To merge or filter many mined files into one dump, use `record files` with the Filter DSL. See [Use Cases](use-cases.md) for the full puzzle-mining playbook.

## Export ML Datasets

Records become training data once you encode them. The exporters all draw on the same FEN encoder, so a plane means the same thing in every format — no per-exporter drift in layout or labels to debug later.

```bash
crtk record dataset npy -i dump/pgn.puzzles.json -o training/pytorch/data/puzzles
crtk record dataset classifier -i dump/pgn.puzzles.json -i dump/pgn.nonpuzzles.json -o training/classifier/fens
crtk record dataset lc0 -i dump/pgn.puzzles.json -o training/lc0/puzzles --weights models/leela.bin
```

`dataset npy` writes a paired `puzzles.features.npy` + `puzzles.labels.npy`. `dataset classifier` takes both the puzzle and non-puzzle sources and writes plane inputs with 0/1 labels. `dataset lc0` emits LC0-style input, policy, value, and metadata tensors — pass `--weights` to compress the policy map. For text-pipeline training, export JSONL instead:

```bash
crtk record export training-jsonl -i dump/pgn.puzzles.json -i dump/pgn.nonpuzzles.json -o training/fens.training.jsonl
crtk record export puzzle-jsonl -i dump/pgn.puzzles.json -o training/fens.puzzle.jsonl --puzzles --weights models/leela.bin
crtk record export puzzle-elo-jsonl -i dump/pgn.puzzles.json -o training/fens.puzzle-elo.jsonl
```

One caveat on the LC0 path: it is a serviceable encoder/evaluator for generating datasets, not a bit-exact reproduction of LC0/BT4 inference. If your pipeline depends on matching Leela's numbers exactly, read [LC0 Integration](lc0.md) for the fidelity notes first; [Configuration](configuration.md) covers weight paths.

## Publish a Puzzle Book

Analyzed records go straight to a print-ready PDF, drawn as native vectors. No LaTeX, no typesetting toolchain to install and placate.

```bash
crtk book collection -i dump/pgn.puzzles.json -o books/tactics.book.toml \
  --subtitle "Tactics From My Games" \
  --pdf-output dist/tactics.pdf --cover-output dist/tactics-cover.pdf \
  --binding paperback --interior white-bw
```

`book collection` builds a TOML manifest from the records, and when given `--pdf-output`/`--cover-output` renders the matching interior and cover in the same pass. A full render is slow enough that you'll want to catch bad layout, FENs, or solution lines before paying for it — `--check` does exactly that:

```bash
crtk book render -i books/tactics.book.toml --check
crtk book render -i books/tactics.book.toml -o dist/tactics.pdf
crtk book cover -i books/tactics.book.toml --pdf dist/tactics.pdf -o dist/tactics-cover.pdf --binding paperback --interior white-bw
```

For richer annotated studies, reach for `book study`. For a quick diagram sheet off a FEN list or PGN, `book pdf` is the short path:

```bash
crtk book pdf -i seeds.txt -o dump/sheet.pdf --title "Training Sheet"
crtk book pdf --pgn games.pgn -o dump/games.pdf --page-size a5 --diagrams-per-row 1
```

## Batch Analysis

A thousand positions analyzed one process-launch at a time is a thousand cold starts. The JSONL batch commands keep the engine warm across the whole list, which is why they're the fastest path for a FEN file:

```bash
crtk engine analyze-batch -i positions.txt --multipv 3 --max-duration 2s -o out/analysis.jsonl
crtk engine bestmove-batch -i positions.txt --max-nodes 1000000 -o out/bestmoves.jsonl
```

Both emit one JSON object per line — pass `--json` for a single array instead. To put two engines or protocols on the same positions and see where they disagree, use `engine compare`:

```bash
crtk engine compare -i positions.txt --left-protocol sf.toml --right-protocol lc0.toml --max-duration 2s --jsonl
```

### Script Many Commands With `batch run`

When a pipeline mixes everything — convert, mine, export, publish — you don't want it scattered across shell history. Put one `crtk` command per line in a file and let `batch run` drive it:

```bash
crtk batch run -i pipeline.crtk --keep-going
```

`pipeline.crtk` is a UTF-8 file, one command per non-comment line:

```text
# Build a tactics book end to end
puzzle mine --input games.pgn --output dump/pgn.json --max-duration 30s
record stats -i dump/pgn.puzzles.json
record dataset classifier -i dump/pgn.puzzles.json -i dump/pgn.nonpuzzles.json -o training/classifier/fens
book collection -i dump/pgn.puzzles.json -o books/tactics.book.toml --pdf-output dist/tactics.pdf --binding paperback --interior white-bw
```

`--keep-going` presses on after a command exits non-zero, `--quiet` drops the command echo, and `--stdin` reads the script from standard input. A whole research run, captured in one file you can rerun next month and trust to do the same thing.

## See Also

- [Command Reference](command-reference.md) — every area, action, and flag.
- [Command Cheatsheet](command-cheatsheet.md) — terse one-liners by task.
- [Getting Started](getting-started.md) — install and first run.
- [Use Cases](use-cases.md) — deeper puzzle-mining and dataset workflows.
- [Workbench](workbench.md) — drive all of the above from the desktop GUI.
