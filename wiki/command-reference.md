# Command reference

All commands are subcommands of `application.Main`.

- Installed launcher: `crtk <command> ...`
- From classes: `java -cp out application.Main <command> ...`
- Proposed/future additions: `roadmap.md`

## `record-to-plain`

Convert a `.record` JSON array into Leela-style `.plain` blocks.

Options:
- `--input|-i <path>`: input `.record` (required)
- `--output|-o <path>`: output `.plain` (optional; default derived from input)
- `--filter|-f <dsl>`: Filter DSL to select which records are exported
- `--sidelines|--export-all|-a`: include sidelines / export additional PVs when present
- `--csv`: also emit a CSV export (default path derived)
- `--csv-output|-c <path>`: explicit CSV output path (also enables CSV export)

## `record-to-csv`

Convert a `.record` JSON array directly to CSV (no `.plain` output).

Options:
- `--input|-i <path>`: input `.record` (required)
- `--output|-o <path>`: output `.csv` (optional; default derived from input)
- `--filter|-f <dsl>`: Filter DSL to select which records are exported

## `record-to-pgn`

Convert a `.record` JSON array into one or more PGN games.

Options:
- `--input|-i <path>`: input `.record` (required)
- `--output|-o <path>`: output `.pgn` (optional; default derived from input)

## `puzzles-to-pgn`

Convert a mixed puzzle/non-puzzle dump into PGN games (filters to puzzles).
If entries include a `kind` field, only `kind:"puzzle"` is kept; otherwise the
configured puzzle verify filter is applied.

Options:
- `--input|-i <path>`: input dump (JSON array or JSONL)
- `--output|-o <path>`: output `.pgn` (optional; default derived from input)

## `records`

Merge/filter/split record files.

Options:
- `--input|-i <path>`: input file or directory (repeatable; positionals allowed)
- `--output|-o <path>`: output `.json` file or directory (required)
- `--filter|-f <dsl>`: Filter-DSL string to select records
- `--puzzles`: keep only puzzle records (uses `kind` or puzzle verify filter)
- `--nonpuzzles`: keep only non-puzzle records
- `--max-records <n>`: split output into parts with at most `n` records each
- `--recursive`: recurse into directories
- `--verbose|-v`: print stack traces on failure

## `record-to-dataset`

Convert a `.record` JSON array into NumPy tensors:
- `<stem>.features.npy` shaped `(N, 781)` float32
- `<stem>.labels.npy` shaped `(N,)` float32 (pawns)

Options:
- `--input|-i <path>`: input `.record` (required)
- `--output|-o <path>`: output stem (optional; default derived when omitted)

## `stack-to-dataset`

Convert a `Stack-*.json` JSON array (puzzle dump format) into the same NumPy tensors as `record-to-dataset`.

Options:
- `--input|-i <path>`: input `Stack-*.json` (required)
- `--output|-o <path>`: output stem (optional; default derived when omitted)

## `gpu-info`

Print whether the optional GPU JNI backends are available (CUDA/ROCm/oneAPI) and what devices they see.

Notes:
- If you built a native library under `native/cuda/`, run with `-Djava.library.path=native/cuda/build`.
- `cuda-info` is a legacy alias.

## `gen-fens`

Generate random legal FEN shards to disk (standard + Chess960 mix).

Options:
- `--output|-o <dir>`: output directory (default `all_positions_shards/`)
- `--files <n>`: number of shard files to generate (default `1000`)
- `--per-file <n>` / `--fens-per-file <n>`: FENs per file (default `100000`)
- `--chess960-files <n>` / `--chess960 <n>`: how many of the first shard files use Chess960 starts (default `100`)
- `--batch <n>`: positions generated per batch (default `2048`)
- `--ascii`: ASCII progress bar (useful when Unicode is borked)
- `--verbose|-v`: print stack trace on failure

## `mine-puzzles`

Drive a UCI engine, apply Filter DSL gates, and emit JSON outputs for puzzles and non-puzzles.

Inputs & outputs:
- `--chess960|-9`: enable Chess960 mining
- `--input|-i <path>`: `.pgn` or `.txt` seeds; omit to mine random seeds
- `--output|-o <path>`: output directory or file-like root (`.json` / `.jsonl`)

Engine & limits:
- `--protocol-path|-P <toml>`: override `Config.getProtocolPath()`
- `--engine-instances|-e <n>`: override `Config.getEngineInstances()`
- `--max-nodes <n>`: override `Config.getMaxNodes()` (per position)
- `--max-duration <dur>`: override `Config.getMaxDuration()` (per position), e.g. `60s`, `2m`, `60000`

Random generation & bounds:
- `--random-count <n>`: random seeds to generate (default `100`)
- `--random-infinite`: continuously add random seeds (ignores wave/total caps)
- `--max-waves <n>`: max waves (default `100`; ignored with `--random-infinite`)
- `--max-frontier <n>`: frontier cap (default `5000`)
- `--max-total <n>`: total processed cap (default `500000`; ignored with `--random-infinite`)

Filter overrides:
- `--puzzle-quality <dsl>`
- `--puzzle-winning <dsl>`
- `--puzzle-drawing <dsl>`
- `--puzzle-accelerate <dsl>`
- `--verbose|-v`: print stack traces on failure

Notes:
- `mine` is a legacy alias.

## `print`

Pretty-print a FEN as ASCII (board + metadata + tags).

Options:
- `--fen "<FEN...>"` (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `display`

Render a board image in a window (with optional overlays).

Options:
- `--fen "<FEN...>"` (or pass it positionally)
- `--arrow <uci>` / `--arrows <uci,uci,...>`: add arrow overlays
- `--special-arrows`: include castling/en-passant hint arrows
- `--circle <sq>` / `--circles <sq,sq,...>`: add circle overlays
- `--legal <sq>`: highlight legal moves from a square (repeatable)
- `--ablation`: overlay inverted per-piece ablation scores
- `--show-backend|--backend`: print which evaluator backend was used
- `--flip|--black-down`: render Black at the bottom
- `--no-border`: hide the board frame
- `--size <px>`: window size (square)
- `--width <px>`, `--height <px>`: window size override
- `--zoom <factor>`: zoom multiplier (1.0 = fit-to-window)
- `--dark|--dark-mode`: dark window styling
- `--details-inside`: show coordinates inside the board
- `--details-outside`: show coordinates outside the board
- `--shadow|--drop-shadow`: apply a subtle drop shadow
- `--verbose|-v`: print stack traces on failure

## `render`

Render a board image to disk (PNG/JPG/BMP).

Options:
- `--fen "<FEN...>"` (or pass it positionally)
- `--output|-o <path>`: output image path (required)
- `--format <fmt>`: output format override (`png`, `jpg`, `bmp`)
- `--arrow <uci>` / `--arrows <uci,uci,...>`: add arrow overlays
- `--special-arrows`: include castling/en-passant hint arrows
- `--circle <sq>` / `--circles <sq,sq,...>`: add circle overlays
- `--legal <sq>`: highlight legal moves from a square (repeatable)
- `--ablation`: overlay inverted per-piece ablation scores
- `--show-backend|--backend`: print which evaluator backend was used
- `--flip|--black-down`: render Black at the bottom
- `--no-border`: hide the board frame
- `--size <px>`: board size (square)
- `--width <px>`, `--height <px>`: image size override
- `--dark|--dark-mode`: dark board styling
- `--details-inside`: show coordinates inside the board
- `--details-outside`: show coordinates outside the board
- `--shadow|--drop-shadow`: apply a subtle drop shadow
- `--verbose|-v`: print stack traces on failure

## `config`

Show or validate CLI configuration.

Subcommands:
- `show`: print resolved configuration values
- `validate`: validate config + protocol files

## `stats`

Summarize a `.record` or puzzle JSON dump.

Options:
- `--input|-i <path>`: input JSON array/JSONL (required)
- `--top <n>`: show top-N tags/engines (default `10`)
- `--verbose|-v`: print stack traces on failure

## `stats-tags`

Summarize tag distributions in a dump.

Options:
- `--input|-i <path>`: input JSON array/JSONL (required)
- `--top <n>`: show top-N tags (default `20`)
- `--verbose|-v`: print stack traces on failure

## `tags`

Generate tags for a FEN or FEN list.

Notes:
- If `config/book.eco.toml` is present, tags may include `eco:` / `opening:` for positions that match the ECO book.

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `moves`

List legal moves for a FEN.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--san`: output SAN instead of UCI
- `--both`: output UCI + SAN per move
- `--verbose|-v`: print stack traces on failure

## `moves-uci`

List legal moves for a FEN (UCI only).

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `moves-san`

List legal moves for a FEN (SAN only).

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `moves-both`

List legal moves for a FEN (UCI + SAN).

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `analyze`

Analyze a position with the engine and print PV summaries.

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--protocol-path|-P <toml>`: override `Config.getProtocolPath()`
- `--max-nodes|--nodes <n>`: override `Config.getMaxNodes()`
- `--max-duration <dur>`: override `Config.getMaxDuration()`, e.g. `60s`, `2m`, `60000`
- `--multipv <n>`: set engine MultiPV
- `--threads <n>`: set engine thread count
- `--hash <mb>`: set engine hash size
- `--wdl|--no-wdl`: enable/disable WDL output
- `--verbose|-v`: print stack traces on failure

## `threats`

Compute opponent "threats" via a null move: the side to move is swapped, en-passant is cleared, and the resulting position is analyzed with MultiPV. The resulting PV best moves are the threats.

Notes:
- Positions where the side to move is in check are skipped (null move would be illegal).
- If `--multipv` is not set, MultiPV defaults to all legal opponent moves in the null-move position.

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--protocol-path|-P <toml>`: override `Config.getProtocolPath()`
- `--max-nodes|--nodes <n>`: override `Config.getMaxNodes()`
- `--max-duration <dur>`: override `Config.getMaxDuration()`, e.g. `60s`, `2m`, `60000`
- `--multipv <n>`: set engine MultiPV (default: all legal opponent moves)
- `--threads <n>`: set engine thread count
- `--hash <mb>`: set engine hash size
- `--wdl|--no-wdl`: enable/disable WDL output
- `--verbose|-v`: print stack traces on failure

## `bestmove`

Return the best move for a FEN.

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--san`: output SAN instead of UCI
- `--both`: output UCI + SAN
- `--protocol-path|-P <toml>`: override `Config.getProtocolPath()`
- `--max-nodes|--nodes <n>`: override `Config.getMaxNodes()`
- `--max-duration <dur>`: override `Config.getMaxDuration()`, e.g. `60s`, `2m`, `60000`
- `--multipv <n>`: set engine MultiPV
- `--threads <n>`: set engine thread count
- `--hash <mb>`: set engine hash size
- `--wdl|--no-wdl`: enable/disable WDL output
- `--verbose|-v`: print stack traces on failure

## `bestmove-uci`

Return the best move for a FEN (UCI only).

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--protocol-path|-P <toml>`: override `Config.getProtocolPath()`
- `--max-nodes|--nodes <n>`: override `Config.getMaxNodes()`
- `--max-duration <dur>`: override `Config.getMaxDuration()`, e.g. `60s`, `2m`, `60000`
- `--multipv <n>`: set engine MultiPV
- `--threads <n>`: set engine thread count
- `--hash <mb>`: set engine hash size
- `--wdl|--no-wdl`: enable/disable WDL output
- `--verbose|-v`: print stack traces on failure

## `bestmove-san`

Return the best move for a FEN (SAN only).

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--protocol-path|-P <toml>`: override `Config.getProtocolPath()`
- `--max-nodes|--nodes <n>`: override `Config.getMaxNodes()`
- `--max-duration <dur>`: override `Config.getMaxDuration()`, e.g. `60s`, `2m`, `60000`
- `--multipv <n>`: set engine MultiPV
- `--threads <n>`: set engine thread count
- `--hash <mb>`: set engine hash size
- `--wdl|--no-wdl`: enable/disable WDL output
- `--verbose|-v`: print stack traces on failure

## `bestmove-both`

Return the best move for a FEN (UCI + SAN).

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--protocol-path|-P <toml>`: override `Config.getProtocolPath()`
- `--max-nodes|--nodes <n>`: override `Config.getMaxNodes()`
- `--max-duration <dur>`: override `Config.getMaxDuration()`, e.g. `60s`, `2m`, `60000`
- `--multipv <n>`: set engine MultiPV
- `--threads <n>`: set engine thread count
- `--hash <mb>`: set engine hash size
- `--wdl|--no-wdl`: enable/disable WDL output
- `--verbose|-v`: print stack traces on failure

## `perft`

Run perft on a position (move generation validation).

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally; defaults to start position)
- `--depth|-d <n>`: perft depth (required)
- `--divide|--per-move`: print per-move breakdown
- `--verbose|-v`: print stack traces on failure

## `perft-suite`

Run a compact perft regression suite with known node counts.

Options:
- (no options)

## `pgn-to-fens`

Convert PGN games to a FEN list that can be used as seeds.

Options:
- `--input|-i <path>`: input PGN file (required)
- `--output|-o <path>`: output `.txt` (optional; default derived)
- `--pairs`: write "parent child" FEN pairs per line
- `--mainline`: only output the mainline (skip variations)
- `--verbose|-v`: print stack traces on failure

## `eval`

Evaluate a position using LC0 or the classical evaluator.

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--lc0`: force LC0 evaluation
- `--classical`: force classical evaluation
- `--weights <path>`: LC0 weights path (optional)
- `--terminal-aware`: use terminal-aware classical evaluation
- `--verbose|-v`: print stack traces on failure

Notes:
- `evaluate` is a legacy alias.

## `eval-static`

Evaluate a position using the classical evaluator only.

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--terminal-aware`: use terminal-aware classical evaluation
- `--verbose|-v`: print stack traces on failure

## `clean`

Delete session cache/logs under `session/`.

Options:
- `--verbose|-v`: print stack traces on failure

## `help`

Print the built-in usage text.
