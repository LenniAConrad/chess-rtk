# Mining puzzles (`mine`)

`mine` drives a UCI engine on many seed positions and emits two JSON files:
- puzzles (`*.puzzles.json`)
- non-puzzles (`*.nonpuzzles.json`)

The mining logic is intentionally “data pipeline” shaped: start from seeds, evaluate, keep/expand the interesting ones, and write everything as a streamable JSON array.

## Inputs

### Random seeds (default)

If you omit `--input`, seeds are generated randomly:

```bash
ucicli mine --random-count 100 --output dump/
```

Use `--random-infinite` to keep generating seeds continuously (caps are disabled).

### FEN list (`.txt`)

`--input seeds.txt` accepts one or two FENs per line:
- one FEN: it becomes the `position`
- two (or more) FENs: the first is treated as `parent`, the second as `position`

Lines starting with `#` or `//` are treated as comments.

### PGN (`.pgn`)

`--input games.pgn` parses games and extracts positions from movetext (variations preserved).

## Engine limits

Each evaluated position is searched up to:
- `max-nodes` (UCI `go nodes <n>`)
- `max-duration` (wall-clock safety net)

Defaults are loaded from `config/cli.config.toml` and can be overridden on the command line.

## Filters / gates (Filter DSL)

Mining uses four Filter DSL programs:
- `puzzle-accelerate`: fast prefilter to early-reject obvious non-puzzles
- `puzzle-quality`: depth/effort gate before a position can be accepted
- `puzzle-winning`: “single winning move” gate
- `puzzle-drawing`: “single drawing resource” gate

The overall “is puzzle?” check is:

`quality AND (winning OR drawing)`

See `filter-dsl.md` for the syntax and examples.

## Outputs

`--output` can be either:
- a directory: writes `standard-<timestamp>.puzzles.json` / `standard-<timestamp>.nonpuzzles.json` (or `chess960-...` when `--chess960`)
- a file-like root ending in `.json` or `.jsonl`: writes `<stem>.puzzles.json` and `<stem>.nonpuzzles.json` next to it

These files are single top-level JSON arrays. The miner appends objects incrementally while keeping the files valid JSON, and downstream tooling can stream the objects without loading everything into memory.

## Chess960

Use `--chess960` (or `-9`) to:
- generate Chess960 random starts when mining randomly
- toggle Chess960 mode in your engine protocol via `setChess960` when the template exists

Make sure your engine protocol TOML supports Chess960 if you plan to mine it.
