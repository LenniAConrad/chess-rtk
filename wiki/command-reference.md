# Command reference

All commands are subcommands of `application.Main`.

- Installed launcher: `crtk <command> ...`
- From classes: `java -cp out application.Main <command> ...`
- Proposed/future additions: `roadmap.md`

Compatibility note:
- This reference lists the canonical commands only.
- Removed commands: `gui2`, `cuda-info`, `mine`, `evaluate`, `stack-to-dataset`.

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

## `record-analysis-delta`

Export one JSONL row per record with evaluation stability metrics: initial and
final eval, delta type/value, fluctuation range, and time/depth to final value.

Options:
- `--input|-i <path>`: input record file (required)
- `--output|-o <path>`: output `.analysis-delta.jsonl` path (optional; default derived from input)
- `--verbose|-v`: print stack traces on failure

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

## `record-to-lc0`

Convert a `.record` JSON array into LC0-style tensors:
- `<stem>.lc0.inputs.npy` shaped `(N, 112*64)` float32
- `<stem>.lc0.policy.npy` shaped `(N, policySize)` float32 (one-hot)
- `<stem>.lc0.value.npy` shaped `(N,)` float32 (scalar in `[-1,1]`)
- `<stem>.lc0.meta.json` metadata

Options:
- `--input|-i <path>`: input `.record` (required)
- `--output|-o <path>`: output stem (optional; default derived when omitted)
- `--weights <path>`: optional LC0 weights to compress the policy to the net's size

## `record-to-puzzle-jsonl`

Convert `.record` rows into puzzle JSONL with LC0 policy values. This command
requires LC0J weights so it can use the network policy map.

Options:
- `--input|-i <path>`: input `.record` file (required)
- `--output|-o <path>`: output `.jsonl` path (optional; default derived from input)
- `--weights <path>`: LC0J weights path (required)
- `--filter|-f <dsl>`: optional row-selection Filter DSL
- `--puzzles`: keep only records classified as puzzles by the configured verify filter
- `--nonpuzzles`: keep only records classified as non-puzzles
- `--verbose|-v`: print stack traces on failure

## `record-to-classifier`

Convert one or more `.record` JSON/JSONL files into tensors for the one-logit
binary classifier:
- `<stem>.classifier.inputs.npy` shaped `(N, 21*64)` float32
- `<stem>.classifier.labels.npy` shaped `(N,)` float32 (`0.0` negative, `1.0` positive)
- `<stem>.classifier.meta.json` metadata

Labeling:
- `--label-filter <dsl>` overrides record kind and labels matching records as positive.
- Without `--label-filter`, `kind:"puzzle"` and `kind:"nonpuzzle"` are used when present.
- If `kind` is absent, the configured puzzle verification filter is used.

Options:
- `--input|-i <path>`: input record file or directory (repeatable; required)
- `--output|-o <path>`: output stem (optional for a single file; required for multiple inputs/directories)
- `--filter|-f <dsl>`: optional row-selection Filter DSL before labeling
- `--label-filter <dsl>`: optional positive-label Filter DSL
- `--max-positives <n>`: cap positive rows
- `--max-negatives <n>`: cap negative rows
- `--recursive`: recurse into input directories
- `--verbose|-v`: print stack traces on failure

## `record-to-training-jsonl`

Convert one or more `.record` JSON/JSONL files into one-position-per-line
training JSONL. Rows matching the puzzle DSL become verified puzzles, rows
sharing a parent FEN with a puzzle become similar examples, and all remaining
rows become random/negative examples.

Options:
- `--input|-i <path>`: input record file or directory (repeatable; required)
- `--output|-o <path>`: output `.jsonl` path (optional for a single file; required for multiple inputs/directories)
- `--filter|-f <dsl>`: puzzle Filter DSL; defaults to configured puzzle verification
- `--recursive`: recurse into input directories
- `--include-engine-metadata`: retain engine/PV details as metadata
- `--max-records <n>`: stop after writing `n` rows (`0` or omitted means no cap)
- `--verbose|-v`: print stack traces on failure

## `gpu-info`

Print whether the optional GPU JNI backends are available (CUDA/ROCm/oneAPI) and what devices they see.

Notes:
- If you built a native library under `native/cuda/`, run with `-Djava.library.path=native/cuda/build`.

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

Render a board image to disk (PNG/JPG/BMP/SVG).

Options:
- `--fen "<FEN...>"` (or pass it positionally)
- `--output|-o <path>`: output image/SVG path (required)
- `--format <fmt>`: output format override (`png`, `jpg`, `bmp`, `svg`)
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

## `chess-book`

Render a chess-book JSON or TOML manifest directly to a native PDF.
The renderer builds cover/front matter, a generated table of contents, mirrored margins, puzzle/solution spreads, page numbers, running headers, and recurring solution tables.

See also: `book-publishing.md`.

Options:
- `--input|-i <path>`: input book manifest (`.json`, `.toml`, or TOML-like text)
- `--output|-o <path>`: output PDF path (optional; default derived from the input path)
- `--title <text>`: title override
- `--subtitle <text>`: subtitle override
- `--limit <n>`: render only the first `n` puzzles from the source manifest and update obvious source-count references in front matter
- `--free-watermark` / `--watermark`: add a noisy free-edition overlay to every page with visible print, resale, and unauthorized redistribution restrictions
- `--verbose|-v`: print stack traces on failure

## `chess-book-cover`

Render a native vector PDF cover for a chess-book JSON or TOML manifest.
The renderer builds the back blurb, front title/subtitle/author, spine text, barcode-safe box, and page-count-based spine width.

See also: `book-publishing.md`.

Dimension notes:
- `paperback` adds 0.125 inch bleed on every outside edge.
- `hardcover` adds 1.5 cm wrap and 1.0 cm hinge allowance.
- Spine width is calculated from `--pages` (or manifest `pages`) times the selected interior paper thickness.

Options:
- `--input|-i <path>`: input book manifest (`.json`, `.toml`, or TOML-like text)
- `--output|-o <path>`: output cover PDF path (optional; default derived from the input path as `*-cover.pdf`)
- `--title <text>`: title override
- `--subtitle <text>`: subtitle override
- `--binding <type>`: `paperback`, `hardcover`, or `ebook` (default `paperback`)
- `--interior <type>`: `white-bw`, `cream-bw`, `white-standard-color`, or `white-premium-color`
- `--pages <n>`: printed page count for spine width (default from book metadata, then an estimate)
- `--verbose|-v`: print stack traces on failure

## `chess-pdf`

Export chess diagrams to PDF from direct FEN input, a FEN list file, or a PGN file.
PGN export currently uses one composition per game's mainline.

Options:
- `--fen "<FEN...>"`: input FEN (repeatable; a single positional FEN is also allowed)
- `--input|-i <path>`: input FEN list or FEN-pair text file
- `--pgn <path>`: input PGN file
- `--output|-o <path>`: output PDF path (optional; default derived from input or `chess.pdf`)
- `--title <text>`: document title override
- `--page-size <size>`: `a4`, `a5`, or `letter`
- `--diagrams-per-row <n>`: diagrams per row (default `2`)
- `--board-pixels <n>`: raster size per diagram before embedding (default `900`)
- `--flip|--black-down`: render Black at the bottom
- `--no-fen`: hide FEN text below diagrams
- `--verbose|-v`: print stack traces on failure

## `gui`

Launch the existing desktop Swing GUI.

Options:
- `--fen "<FEN...>"` (or pass it positionally)
- `--flip|--black-down`: render Black at the bottom
- `--dark|--dark-mode`: start in dark UI theme
- `--light`: start in light UI theme
- `-h|--help`: show help

## `gui-web`

Launch the chess-web-inspired desktop Swing GUI.

Options:
- `--fen "<FEN...>"` (or pass it positionally)
- `--flip|--black-down`: render Black at the bottom
- `--dark|--dark-mode`: start in dark UI theme
- `--light`: start in light UI theme
- `-h|--help`: show help

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
- `--delta` emits JSONL records with parent/child tag differences instead of plain JSON arrays.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--input|-i <path>`: FEN list, FEN-pair text file, or record-like position input
- `--pgn <path>`: PGN input
- `--include-fen`: include the FEN as a `META` tag when not using `--delta`
- `--analyze`: run engine analysis to enrich tags (PV/mate/enables)
- `--sequence`: interpret input as an ordered line (enable/disable tags)
- `--delta`: emit per-move tag deltas as JSONL
- `--mainline`: with `--pgn`, only export mainline positions
- `--sidelines`: with `--pgn`, include variations
- `--protocol|-p <path>`: engine protocol TOML file
- `--max-nodes <n>`: max nodes per position
- `--max-duration <duration>`: max duration per position (e.g. `5s`)
- `--multipv <n>`: number of PVs
- `--threads <n>`: engine threads
- `--hash <n>`: engine hash (MB)
- `--wdl`: enable WDL output (if supported)
- `--no-wdl`: disable WDL output
- `--verbose|-v`: print stack traces on failure

## `puzzle-tags`

Analyze a root puzzle position, expand engine PVs, and emit JSONL rows with
per-move tags and tag deltas.

Options:
- `--fen "<FEN...>"`: root puzzle FEN (required; positional FEN is also accepted)
- `--multipv <n>`: number of PVs to expand (default `3`)
- `--pv-plies <n>`: plies to keep from each PV (default `12`)
- `--tag-multipv <n>`: MultiPV used while enriching tags (default `1`)
- `--analyze`: run engine analysis to enrich tags (default)
- `--no-analyze`: skip per-move analysis and use static tags
- `--protocol|-p <path>`: engine protocol TOML file
- `--max-nodes <n>`: max nodes per position
- `--max-duration <duration>`: max duration per position (e.g. `5s`)
- `--threads <n>`: engine threads
- `--hash <n>`: engine hash (MB)
- `--wdl`: enable WDL output (if supported)
- `--no-wdl`: disable WDL output
- `--verbose|-v`: print stack traces on failure

## `puzzle-text`

Run the T5 tag-to-text model over a puzzle PV expansion.

Options:
- `--model <path>`: T5 `.bin` model path (defaults to `t5-model-path` in config)
- `--fen "<FEN...>"`: root puzzle FEN (required; positional FEN is also accepted)
- `--multipv <n>`: number of PVs to expand (default `3`)
- `--pv-plies <n>`: plies to keep from each PV (default `12`)
- `--tag-multipv <n>`: MultiPV used while enriching tags (default `1`)
- `--max-new <n>`: max generated tokens (default `128`)
- `--include-fen`: emit JSON with FEN, inferred move, and summary
- `--analyze`: run engine analysis to enrich tags (default)
- `--no-analyze`: skip per-move analysis and use static tags
- `--protocol|-p <path>`: engine protocol TOML file
- `--max-nodes <n>`: max nodes per position
- `--max-duration <duration>`: max duration per position (e.g. `5s`)
- `--threads <n>`: engine threads
- `--hash <n>`: engine hash (MB)
- `--wdl`: enable WDL output (if supported)
- `--no-wdl`: disable WDL output
- `--verbose|-v`: print stack traces on failure

## `tag-text`

Run the T5 tag-to-text model for one FEN or a FEN list.

Options:
- `--model <path>`: T5 `.bin` model path (defaults to `t5-model-path` in config)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--input|-i <path>`: FEN list file
- `--include-fen`: emit JSON with FEN and summary
- `--max-new <n>`: max generated tokens (default `128`)
- `--analyze`: run engine analysis to enrich tags
- `--protocol|-p <path>`: engine protocol TOML file
- `--max-nodes <n>`: max nodes per position
- `--max-duration <duration>`: max duration per position (e.g. `5s`)
- `--multipv <n>`: number of PVs
- `--threads <n>`: engine threads
- `--hash <n>`: engine hash (MB)
- `--wdl`: enable WDL output (if supported)
- `--no-wdl`: disable WDL output
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

## `uci-to-san`

Convert a single UCI move to SAN in the given position.

Notes:
- If no FEN is provided, the standard start position is used.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `<move>`: UCI move token (e.g. `e2e4`, `a7a8q`)
- `--verbose|-v`: print stack traces on failure

## `san-to-uci`

Convert a single SAN move to UCI in the given position.

Notes:
- If no FEN is provided, the standard start position is used.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `<move>`: SAN move token (e.g. `Nf3`, `exd5`, `O-O`)
- `--verbose|-v`: print stack traces on failure

## `fen-after`

Apply a single move (UCI or SAN) and print the resulting FEN.

Notes:
- If no FEN is provided, the standard start position is used.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `<move>`: move token (UCI or SAN)
- `--verbose|-v`: print stack traces on failure

## `play-line`

Apply a move sequence (UCI or SAN) and print the resulting FEN.

Notes:
- If no FEN is provided, the standard start position is used.
- Move lists are cleaned using the PGN sanitizer (move numbers and comments are ignored).

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `<moves...>`: move sequence (UCI or SAN)
- `--intermediate`: print intermediate FENs after each move instead of only the final FEN
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
