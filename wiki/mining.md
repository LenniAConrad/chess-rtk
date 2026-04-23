# Mining Puzzles

`puzzle mine` drives a configured UCI engine across many seed positions, applies
Filter DSL gates, and writes accepted puzzles and rejected/non-puzzle examples
as reusable record dumps.

The command is built for long-running data work:

- seed positions can come from random legal generation, FEN lists, FEN
  parent/child pairs, PGN mainlines, PGN variations, or Chess960 starts
- UCI analysis is bounded by node and wall-clock limits
- quality, winning-resource, drawing-resource, and accelerate gates are
  explicit Filter DSL programs
- accepted and rejected rows are both preserved, which makes classifier and
  ablation datasets easier to build
- outputs are JSON arrays that downstream `record` commands can merge, filter,
  split, summarize, and export

## Basic Runs

Mine random standard positions:

```bash
crtk puzzle mine --random-count 100 --output dump/
```

Mine from a FEN list:

```bash
crtk puzzle mine \
  --input seeds/fens.txt \
  --output dump/fens.json \
  --engine-instances 4 \
  --max-duration 60s
```

Mine from PGN:

```bash
crtk puzzle mine \
  --input games.pgn \
  --output dump/games.json \
  --engine-instances 4 \
  --max-nodes 5000000
```

Mine Chess960 positions:

```bash
crtk puzzle mine --chess960 --random-count 200 --output dump/
```

## Seed Sources

### Random Seeds

If `--input` is omitted, ChessRTK generates random legal positions. Use
`--random-count <n>` for a finite batch or `--random-infinite` for continuous
generation.

Useful caps:

- `--max-waves <n>` limits expansion waves
- `--max-frontier <n>` limits the frontier size per wave
- `--max-total <n>` limits total processed positions

The infinite mode ignores wave and total caps because it is designed for
continuous runs.

### FEN Lists

`--input seeds.txt` accepts one or two FENs per line:

- one FEN: the row's `position`
- two or more FENs: the first is treated as `parent`, the second as `position`

Blank lines and lines starting with `#` or `//` are ignored. Parent/child pairs
are useful when you want to preserve the move that created the candidate
position.

Create FEN lists from PGN:

```bash
crtk fen pgn -i games.pgn -o seeds.txt
crtk fen pgn -i games.pgn -o pairs.txt --pairs --mainline
```

### PGN Inputs

`--input games.pgn` parses games and extracts positions from movetext. PGN
variations are preserved by the parser, and `fen pgn` can be used separately
when you want to inspect or reuse the extracted seed file before mining.

### Chess960

Use `--chess960` or `-9` to:

- generate Chess960 random starts during random mining
- toggle Chess960 mode in the UCI protocol when the protocol template supports
  it

`fen chess960` is useful for deterministic seed generation:

```bash
crtk fen chess960 518
crtk fen chess960 --all --format both > chess960.tsv
```

## Engine Limits

Each evaluated position is searched up to:

- `--max-nodes <n>`: UCI `go nodes <n>`
- `--max-duration <duration>`: wall-clock safety net, such as `5s`, `2m`, or
  `60000`
- `--engine-instances <n>`: number of UCI engine processes used by the miner

Defaults are read from `config/cli.config.toml`; command-line flags override
those values for the run.

Run a smoke check before long jobs:

```bash
crtk doctor
crtk config validate
crtk engine uci-smoke --nodes 1 --max-duration 5s
```

## Gates

Mining uses Filter DSL programs to decide which positions deserve more work and
which records count as puzzles.

| Gate | Purpose |
| --- | --- |
| `puzzle-accelerate` | fast prefilter for positions that are unlikely to survive expensive checks |
| `puzzle-quality` | effort/depth/shape gate before a row can be accepted |
| `puzzle-winning` | single winning move or decisive tactical resource |
| `puzzle-drawing` | single drawing resource or defensive save |

The acceptance check is:

```text
quality AND (winning OR drawing)
```

Override gates on the command line when you want a run-specific definition:

```bash
crtk puzzle mine \
  --input seeds.txt \
  --output dump/custom.json \
  --puzzle-quality 'gate=AND;nodes>=1000000;' \
  --puzzle-winning 'gate=AND;eval>=3.0;'
```

See [Filter DSL](filter-dsl.md) for syntax and examples.

## Outputs

`--output` accepts either a directory or a file-like root.

Directory output:

- standard mining writes `standard-<timestamp>.puzzles.json`
- standard mining writes `standard-<timestamp>.nonpuzzles.json`
- Chess960 mining uses `chess960-<timestamp>...` stems

File-like output:

- `--output dump/run.json` writes `dump/run.puzzles.json`
- `--output dump/run.json` writes `dump/run.nonpuzzles.json`
- `.jsonl` roots use the same stem behavior

The files are JSON arrays that are appended incrementally while staying valid.
Downstream commands can process them without loading every row into custom
scripts.

## Follow-Up Commands

Inspect and filter records:

```bash
crtk record stats -i dump/run.puzzles.json
crtk record tag-stats -i dump/run.puzzles.json
crtk record files -i dump/ -o dump/merged.puzzles.json --recursive --puzzles
crtk record analysis-delta -i dump/run.puzzles.json -o dump/run.analysis-delta.jsonl
```

Convert accepted puzzles:

```bash
crtk puzzle pgn -i dump/run.puzzles.json -o dump/run.pgn
crtk record export csv -i dump/run.puzzles.json -o dump/run.csv
crtk record export training-jsonl \
  -i dump/run.puzzles.json \
  -i dump/run.nonpuzzles.json \
  -o training/run.training.jsonl
```

Export tensors:

```bash
crtk record dataset npy -i dump/run.puzzles.json -o training/npy/run
crtk record dataset classifier \
  -i dump/run.puzzles.json \
  -i dump/run.nonpuzzles.json \
  -o training/classifier/run
```

Render study material:

```bash
crtk book pdf -i seeds.txt -o dist/seeds.pdf --title "Candidate Positions"
crtk book render -i books/puzzles.toml -o dist/puzzles.pdf
```

## Reliability Checks

Before trusting a large run:

1. Run `crtk doctor --strict` on the target machine.
2. Run `crtk engine uci-smoke` with the protocol, thread, and hash settings
   intended for mining.
3. Run `crtk engine perft-suite --depth 6 --threads <n>` after core changes.
4. Mine a small batch, inspect `record stats`, and open a few converted PGNs.
5. Keep the config file, protocol TOML, and command line with the produced
   dumps so results can be reproduced.
