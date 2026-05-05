# Command reference

All commands are subcommands of `application.Main`.

- Installed launcher: `crtk <area> <action> [options] [args]`
- From classes: `java -cp out application.Main <area> <action> [options] [args]`
- Built-in usage text: `crtk help --full`

Use grouped commands for scripts and automation: `record`, `fen`, `move`,
`engine`, `book`, and `puzzle`.

Command style:

- Prefer the full grouped form in scripts: `crtk record export ...`,
  `crtk record dataset ...`, `crtk move list ...`, `crtk engine bestmove ...`.
- Use named flags for structured values such as `--fen`, `--input`, `--output`,
  and `--format`.
- Put options before free-form args when scripting. Use `--` or
  `--end-of-options` if a value could be parsed as an option.
- Contextual help works with either `crtk help move list` or
  `crtk move list --help`.

The Java-native commands (`fen`, `move`, `engine perft`, `engine perft-suite`,
`engine builtin --classical`, and `engine static`) do not require an external
engine. External UCI configuration is only needed for UCI-backed analysis,
best-move, threat, smoke-test, and mining workflows.

## Capability Overview

| Area | Commands |
| --- | --- |
| Position parsing and generation | `fen normalize`, `fen validate`, `fen generate`, `fen pgn`, `fen chess960` |
| Board inspection and rendering | `fen print`, `fen display`, `fen render`, `book pdf` |
| Move primitives | `move list`, `move uci`, `move san`, `move both`, `move to-san`, `move to-uci`, `move after`, `move play` |
| Move-generation validation | `engine perft`, `engine perft-suite` |
| External UCI engines | `engine analyze`, `engine bestmove`, `engine threats`, `engine uci-smoke` |
| In-process search and evaluation | `engine builtin`, `engine java`, `engine eval`, `engine static` |
| Puzzle mining and conversion | `puzzle mine`, `puzzle pgn` |
| Tagging and text generation | `fen tags`, `puzzle tags`, `fen text`, `puzzle text` |
| Record processing | `record files`, `record stats`, `record tag-stats`, `record analysis-delta` |
| Dataset export | `record dataset npy`, `record dataset lc0`, `record dataset classifier`, `record export training-jsonl`, `record export puzzle-jsonl` |
| Publishing | `book collection`, `book study`, `book render`, `book cover`, `book pdf` |
| Local health checks | `doctor`, `config show`, `config validate`, `engine gpu` |

## `record`

Grouped record workflows. This keeps export, dataset, stats, and file-management
operations under one entry point.

Usage:
- `crtk record export plain ...`: convert `.record` JSON to `.plain`
- `crtk record export csv ...`: convert `.record` JSON to CSV
- `crtk record export pgn ...`: convert `.record` JSON to PGN games
- `crtk record export puzzle-jsonl ...`: export LC0-policy-aware puzzle JSONL rows
- `crtk record export training-jsonl ...`: export coarse/fine FEN labels
- `crtk record dataset npy ...`: export eval-regression tensors
- `crtk record dataset lc0 ...`: export LC0-style tensors
- `crtk record dataset classifier ...`: export binary classifier tensors
- `crtk record files ...`: merge/filter/split record files
- `crtk record stats ...`: summarize record files
- `crtk record tag-stats ...`: summarize tag distributions
- `crtk record analysis-delta ...`: export evaluation stability metrics

Use the full `record export ...` and `record dataset ...` forms in scripts.

## `fen`

Grouped FEN and position workflows.

Usage:
- `crtk fen normalize ...`: normalize and validate a FEN
- `crtk fen validate ...`: validate a FEN
- `crtk fen after ...`: apply one move and print the resulting FEN
- `crtk fen line ...`: apply a move line and print the resulting FEN
- `crtk fen generate ...`: generate random legal FEN shards
- `crtk fen pgn ...`: convert PGN games to FEN lists
- `crtk fen chess960 ...`: print Chess960 starting positions
- `crtk fen print ...`: pretty-print a FEN
- `crtk fen display ...`: render a board image in a window
- `crtk fen render ...`: save a board image to disk
- `crtk fen tags ...`: generate tags for FENs, PGNs, or variations
- `crtk fen text ...`: summarize position tags with T5

## `move`

Grouped move-listing, notation, and application workflows.

Usage:
- `crtk move list ...`: list legal moves for a FEN
- `crtk move uci ...`: list legal moves in UCI
- `crtk move san ...`: list legal moves in SAN
- `crtk move both ...`: list legal moves in UCI and SAN
- `crtk move to-san ...`: convert one UCI move to SAN
- `crtk move to-uci ...`: convert one SAN move to UCI
- `crtk move after ...`: apply one move and print the resulting FEN
- `crtk move play ...`: apply a move line and print the resulting FEN

## `engine`

Grouped engine, evaluator, and move-generation workflows.

Usage:
- `crtk engine analyze ...`: analyze a FEN with the configured engine
- `crtk engine bestmove ...`: print the best move for a FEN
- `crtk engine bestmove-uci ...`: print the best move in UCI
- `crtk engine bestmove-san ...`: print the best move in SAN
- `crtk engine bestmove-both ...`: print the best move in UCI and SAN
- `crtk engine builtin ...`: search with the in-house Java engine
- `crtk engine java ...`: run the same built-in Java engine
- `crtk engine threats ...`: analyze opponent threats
- `crtk engine eval ...`: evaluate a FEN with LC0 or classical heuristics
- `crtk engine static ...`: evaluate a FEN with the classical backend
- `crtk engine perft ...`: run perft on a position
- `crtk engine perft-suite ...`: run the perft regression suite
- `crtk engine gpu ...`: print GPU JNI backend status
- `crtk engine uci-smoke ...`: start the engine and run a tiny bounded search

## `engine builtin`

Search a position with ChessRTK's in-house Java engine. `engine java` runs the
same implementation.

The built-in engine is an in-process search and benchmarking engine. It is
useful for deterministic local search, smoke tests, puzzle-solve timing, and
automation that should not depend on spawning a UCI engine. It is not intended
to be a top-tier engine competing with mature UCI engines.

Examples:

```bash
crtk engine builtin --fen "<FEN>" --depth 20
crtk engine builtin --fen "<FEN>" --depth 4 --format summary
crtk engine builtin --fen "<FEN>" --depth 5 --nodes 250000 --format both
crtk engine builtin --fen "<FEN>" --depth 8 --max-duration 2s --format summary
crtk engine builtin --input positions.txt --depth 3 --format uci
crtk engine builtin --evaluator nnue --weights models/crtk-halfkp.nnue --fen "<FEN>"
crtk engine builtin --lc0 --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin --fen "<FEN>"
```

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--evaluator <classical|nnue|lc0>`: static evaluator family (default `classical`)
- `--classical`: shortcut for `--evaluator classical`
- `--nnue`: shortcut for `--evaluator nnue`
- `--lc0`: shortcut for `--evaluator lc0`
- `--weights <path>`: NNUE or LC0 evaluator weights path
- `--depth|-d <n>`: maximum iterative-deepening depth in plies (default `3`)
- `--max-nodes|--nodes <n>`: node budget; `0` means unlimited. If omitted,
  the default is `250000` without `--depth`, and unlimited when `--depth` is
  explicit.
- `--max-duration <dur>`: wall-clock budget; `0` means unlimited. If omitted,
  the default is `5s` without `--depth`, and unlimited when `--depth` is
  explicit.
- `--format <uci-info|uci|san|both|summary>`: output format (default
  `uci-info`)
- `--verbose|-v`: print stack traces on failure

Evaluator notes:
- `classical`: handcrafted evaluator with no model dependency.
- `nnue`: pure-Java NNUE evaluator using `models/crtk-halfkp.nnue` by default,
  or an explicit `--weights` path. If no NNUE weights are available, the
  command fails with a missing-weights error instead of silently using the
  smoke-test fallback.
- `lc0`: pure-Java LC0 value evaluator using the configured LC0J model path by
  default, or an explicit `--weights` path.

Search notes:
- The engine uses alpha-beta search, not LC0-style MCTS.
- The search is single-threaded and owns an internal transposition table plus a
  static-evaluation cache. The table size is fixed in code; `--threads` and
  `--hash` are UCI-engine options, not built-in-engine options.
- The default `uci-info` format prints one UCI-style `info depth ... pv ...`
  line for each completed depth, then a final `bestmove ...` line.
- If a slow evaluator exhausts the budget before depth 1 completes, `uci-info`
  prints the static root fallback as `info depth 0 ... pv ...`.
- Use `--format uci` for best-move-only UCI coordinate output.
- `--format summary` prints the evaluator, best move, score, completed depth,
  nodes, elapsed time, stopped flag, and principal variation.

## `book`

Grouped book and diagram PDF workflows.

Usage:
- `crtk book collection ...`: build a dense puzzle-collection book manifest from record JSON/JSONL
- `crtk book study ...`: render annotated puzzle studies to PDF, cover, and optional normalized TOML
- `crtk book render ...`: render a chess-book JSON/TOML file to a native PDF
- `crtk book cover ...`: render a native PDF cover for a chess-book file
- `crtk book pdf ...`: export chess diagrams to a PDF

Compatibility aliases:
- `book ilovechess` -> `book collection`
- `book artofchess` / `book art` -> `book study`

## `puzzle`

Grouped puzzle mining, conversion, tag, and text workflows.

Usage:
- `crtk puzzle mine ...`: mine chess puzzles
- `crtk puzzle pgn ...`: convert mixed puzzle dumps to PGN games
- `crtk puzzle tags ...`: generate per-move tags for puzzle PVs
- `crtk puzzle text ...`: run T5 over puzzle PVs

## `record export plain`

Convert a `.record` JSON array into Leela-style `.plain` blocks.

Options:
- `--input|-i <path>`: input `.record` (required)
- `--output|-o <path>`: output `.plain` (optional; default derived from input)
- `--filter|-f <dsl>`: Filter DSL to select which records are exported
- `--sidelines|--export-all|-a`: include sidelines / export additional PVs when present
- `--csv`: also emit a CSV export (default path derived)
- `--csv-output|-c <path>`: explicit CSV output path (also enables CSV export)

## `record export csv`

Convert a `.record` JSON array directly to CSV (no `.plain` output).

Options:
- `--input|-i <path>`: input `.record` (required)
- `--output|-o <path>`: output `.csv` (optional; default derived from input)
- `--filter|-f <dsl>`: Filter DSL to select which records are exported

## `record export pgn`

Convert a `.record` JSON array into one or more PGN games.

Options:
- `--input|-i <path>`: input `.record` (required)
- `--output|-o <path>`: output `.pgn` (optional; default derived from input)

## `record analysis-delta`

Export one JSONL row per record with evaluation stability metrics: initial and
final eval, delta type/value, fluctuation range, and time/depth to final value.

Options:
- `--input|-i <path>`: input record file (required)
- `--output|-o <path>`: output `.analysis-delta.jsonl` path (optional; default derived from input)
- `--verbose|-v`: print stack traces on failure

## `puzzle pgn`

Convert a mixed puzzle/non-puzzle dump into PGN games (filters to puzzles).
If entries include a `kind` field, only `kind:"puzzle"` is kept; otherwise the
configured puzzle verify filter is applied.

Options:
- `--input|-i <path>`: input dump (JSON array or JSONL)
- `--output|-o <path>`: output `.pgn` (optional; default derived from input)

## `record files`

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

## `record dataset npy`

Convert a `.record` JSON array into NumPy tensors:
- `<stem>.features.npy` shaped `(N, 781)` float32
- `<stem>.labels.npy` shaped `(N,)` float32 (pawns)

Options:
- `--input|-i <path>`: input `.record` (required)
- `--output|-o <path>`: output stem (optional; default derived when omitted)

## `record dataset lc0`

Convert a `.record` JSON array into LC0-style tensors:
- `<stem>.lc0.inputs.npy` shaped `(N, 112*64)` float32
- `<stem>.lc0.policy.npy` shaped `(N, policySize)` float32 (one-hot)
- `<stem>.lc0.value.npy` shaped `(N,)` float32 (scalar in `[-1,1]`)
- `<stem>.lc0.meta.json` metadata

Options:
- `--input|-i <path>`: input `.record` (required)
- `--output|-o <path>`: output stem (optional; default derived when omitted)
- `--weights <path>`: optional LC0 weights to compress the policy to the net's size

## `record export puzzle-jsonl`

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

## `record dataset classifier`

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

## `record export training-jsonl`

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

## `engine gpu`

Print whether the optional GPU JNI backends are available (CUDA/ROCm/oneAPI) and what devices they see.

Notes:
- If you built a native library under `native/cuda/`, run with `-Djava.library.path=native/cuda/build`.

## `engine uci-smoke`

Start the configured UCI engine, apply optional engine settings, run a tiny
bounded search from the standard start position, and print a concise health
report. Use this before long mining or analysis jobs to catch bad engine paths,
bad protocol TOML, and startup failures.

Options:
- `--protocol-path|-P <toml>`: override `Config.getProtocolPath()`
- `--max-nodes|--nodes <n>`: search node cap (default `1`)
- `--max-duration <dur>`: wall-clock search cap (default `5s`)
- `--threads <n>`: set engine thread count
- `--hash <mb>`: set engine hash size
- `--wdl|--no-wdl`: enable/disable WDL output
- `--verbose|-v`: print stack traces on failure

## `fen generate`

Generate random legal FEN shards to disk (standard + Chess960 mix).

Options:
- `--output|-o <dir>`: output directory (default `all_positions_shards/`)
- `--files <n>`: number of shard files to generate (default `1000`)
- `--per-file <n>` / `--fens-per-file <n>`: FENs per file (default `100000`)
- `--chess960-files <n>` / `--chess960 <n>`: how many of the first shard files use Chess960 starts (default `100`)
- `--batch <n>`: positions generated per batch (default `2048`)
- `--max-attempts <n>`: maximum candidate positions sampled per shard when filters are selective
- `--ascii`: ASCII progress bar (useful when Unicode is borked)
- `--verbose|-v`: print stack trace on failure

Filters combine with AND: every selected condition must match.

Filtering presets:
- `--stage <name>`: `endgame`, `late-endgame`, `king-pawn`, `minor`, `rook`, or `queenless`
- `--endgame`: queenless positions with at most 14 total pieces
- `--late-endgame`: queenless positions with at most 8 total pieces
- `--king-pawn-endgame`: no queens, rooks, bishops, or knights
- `--minor-endgame`: queenless minor-piece endgames without rooks
- `--rook-endgame`: queenless rook endgames without minor pieces
- `--queenless`, `--opposite-bishops`

Move-state filters:
- `--side white|black|w|b`
- `--in-check`, `--not-in-check`, `--checkmate`, `--stalemate`
- `--en-passant` / `--ep`: legal en-passant capture available
- `--promotion`, `--underpromotion`, `--capture`
- `--castle-rights`, `--legal-castle`

Count and material filters:
- `--pieces <n>`, `--min-pieces <n>`, `--max-pieces <n>`
- `--white-pieces <n>`, `--black-pieces <n>` plus `--min-*` / `--max-*` forms
- `--pawns|--knights|--bishops|--rooks|--queens <n>` plus `--min-*` / `--max-*` forms
- `--white-rooks <n>`, `--black-rooks <n>` and the same side-specific forms for pawns, knights, bishops, and queens
- `--material <cp>`, `--min-material <cp>`, `--max-material <cp>`
- `--material-diff <cp>`, `--min-material-diff <cp>`, `--max-material-diff <cp>`
- `--max-material-imbalance <cp>`
- `--legal-moves <n>`, `--fullmove <n>`, `--halfmove <n>` plus `--min-*` / `--max-*` forms

Shortcut:
- `crtk gen fens ...` is an alias for `crtk fen generate ...`

Examples:
- `crtk fen generate --output shards/ --files 2 --per-file 20 --chess960-files 1`
- `crtk gen fens --output endgames/ --files 1 --per-file 100 --endgame --max-material-imbalance 300`
- `crtk gen fens --output rook-endgames/ --files 1 --per-file 200 --rook-endgame --rooks 2`
- `crtk gen fens --output specials/ --files 1 --per-file 25 --en-passant --max-attempts 250000`
- `crtk gen fens --output promotions/ --files 1 --per-file 50 --promotion --capture --max-attempts 1000000`

## `puzzle mine`

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

## `fen print`

Pretty-print a FEN as ASCII (board + metadata + tags).

Options:
- `--fen "<FEN...>"` (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `fen display`

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

## `fen render`

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

## `book render`

Render a chess-book JSON or TOML manifest directly to a native PDF.
The renderer builds cover/front matter, a generated table of contents, mirrored margins, puzzle/solution spreads, page numbers, running headers, and recurring solution tables.
Solution moves are rendered as figurine algebraic notation in captions and
tables.

See also: `book-publishing.md`.

Options:
- `--input|-i <path>`: input book manifest (`.json`, `.toml`, or TOML-like text)
- `--output|-o <path>`: output PDF path (optional; default derived from the input path)
- `--title <text>`: title override
- `--subtitle <text>`: subtitle override
- `--limit <n>`: render only the first `n` puzzles from the source manifest and update obvious source-count references in front matter
- `--check|--validate`: validate layout, FENs, and solution lines without writing a PDF
- `--free-watermark` / `--watermark`: add a noisy free-edition overlay to every page with visible print, resale, and unauthorized redistribution restrictions
- `--watermark-id <text>`: embed a traceable watermark ID in page overlays and PDF metadata; implies `--watermark`
- `--verbose|-v`: print stack traces on failure

## `book collection`

Build a puzzle-collection book manifest from analyzed record JSON/JSONL.
The command converts the first PV in each accepted record into move-numbered
SAN, writes a TOML manifest, and can optionally render the interior PDF and
matching cover.

See also: `book-publishing.md`.

Options:
- `--input|-i <path>`: input record JSON/JSONL file with `position` and `analysis`
- `--output|-o <path>`: output TOML manifest path (optional; default derived from the input path as `*.book.toml`)
- `--pdf-output <path>`: also render the interior PDF to this path
- `--cover-output <path>`: also render the matching cover PDF to this path
- `--title <text>`: book title (default `Chess Puzzle Collection`)
- `--subtitle <text>`: subtitle override (default `"<count> Chess Puzzles"`)
- `--author <text>`: author credit (default `Lennart A. Conrad`)
- `--time <text>`: publication time string
- `--location <text>`: publication location string
- `--language <text>`: book language token (default `English`)
- `--pages <n>`: printed page-count hint for the manifest and optional cover
- `--limit <n>`: import at most the first `n` records from the source file
- `--table-frequency <n>`: puzzle pages between solution tables (default `6`)
- `--puzzle-rows <n>`: puzzle grid rows per page (default `5`)
- `--puzzle-columns <n>`: puzzle grid columns per page (default `4`)
- `--imprint <text>`: repeatable imprint line
- `--dedication <text>`: repeatable dedication line
- `--introduction <text>`: repeatable introduction paragraph
- `--how-to-read <text>`: repeatable custom how-to-read paragraph
- `--blurb <text>`: repeatable back-cover blurb paragraph
- `--link <text>`: repeatable purchase link
- `--afterword <text>`: repeatable closing paragraph
- `--binding <type>`: `paperback`, `hardcover`, or `ebook` for `--cover-output`
- `--interior <type>`: `white-bw`, `cream-bw`, `white-standard-color`, or `white-premium-color`
- `--free-watermark` / `--watermark`: add a noisy free-edition overlay to `--pdf-output`
- `--watermark-id <text>`: embed a traceable watermark ID in the interior PDF; implies `--watermark`
- `--check|--validate`: validate the generated book model without writing files
- `--verbose|-v`: print stack traces on failure

## `book study`

Render deeply annotated puzzle studies from a richer JSON or TOML manifest.
This command keeps the composition-style fields intact: per-entry description
text, comments, hints, analysis, and explicit figure lists. It can also write a
normalized TOML manifest and render a matching cover in one pass.

See also: `book-publishing.md`.

Options:
- `--input|-i <path>`: input puzzle-study manifest (`.json` or `.toml`)
- `--output|-o <path>`: output interior PDF path (optional; defaults to `*.pdf` when no other output is requested)
- `--manifest-output <path>`: also write a normalized TOML manifest
- `--cover-output <path>`: also render the matching native cover PDF
- `--title <text>`: book title override
- `--subtitle <text>`: subtitle override
- `--author <text>`: author override
- `--time <text>`: publication time override
- `--location <text>`: publication location override
- `--blurb <text>`: repeatable back-cover blurb override
- `--link <text>`: repeatable cover-link override
- `--pages <n>`: printed page count for cover metadata / spine width
- `--page-size <size>`: `a4`, `a5`, or `letter`
- `--margin <n>`: page margin in PostScript points
- `--diagrams-per-row <n>`: diagrams per row override
- `--board-pixels <n>`: raster size per diagram before embedding
- `--flip|--black-down`: render Black at the bottom
- `--no-fen`: hide FEN text below diagrams
- `--binding <type>`: `paperback`, `hardcover`, or `ebook` for `--cover-output`
- `--interior <type>`: `white-bw`, `cream-bw`, `white-standard-color`, or `white-premium-color`
- `--check|--validate`: validate the manifest and composition layout without writing files
- `--verbose|-v`: print stack traces on failure

## `book cover`

Render a native vector PDF cover for a chess-book JSON or TOML manifest.
The renderer builds the back blurb, front title/subtitle/author, spine text, barcode-safe box, and page-count-based spine width.

See also: `book-publishing.md`.

Dimension notes:
- `paperback` adds 0.125 inch bleed on every outside edge.
- `hardcover` adds 1.5 cm wrap and 1.0 cm hinge allowance.
- Spine width is calculated from `--pages`, or else the supplied interior PDF page count, or else manifest `pages`, times the selected interior paper thickness.

Options:
- `--input|-i <path>`: input book manifest (`.json`, `.toml`, or TOML-like text)
- `--pdf <path>`: interior PDF used to infer trim size and page count
- `--output|-o <path>`: output cover PDF path (optional; default derived from the input path as `*-cover.pdf`)
- `--title <text>`: title override
- `--subtitle <text>`: subtitle override
- `--binding <type>`: `paperback`, `hardcover`, or `ebook` (default `paperback`)
- `--interior <type>`: `white-bw`, `cream-bw`, `white-standard-color`, or `white-premium-color`
- `--pages <n>`: printed page count for spine width (default from the interior PDF, then book metadata, then an estimate)
- `--check|--validate`: validate the manifest and print calculated cover dimensions without writing a PDF
- `--verbose|-v`: print stack traces on failure

## `fen chess960`

Print Chess960 starting positions in Scharnagl index order.

Modes:
- `crtk fen chess960 <index>` or `crtk fen chess960 --index <n>`: print one position.
- `crtk fen chess960 --all`: print all 960 positions in order.
- `crtk fen chess960 --random --count <n>`: print random positions.

Options:
- `--format fen`: print FEN only (default)
- `--format layout`: print the white back-rank layout only
- `--format both`: print `index<TAB>layout<TAB>fen`

## `book pdf`

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

## `gui-next`

Launch the ChessRTK Studio GUI v3 research workbench.

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

## `record stats`

Summarize a `.record` or puzzle JSON dump.

Options:
- `--input|-i <path>`: input JSON array/JSONL (required)
- `--top <n>`: show top-N tags/engines (default `10`)
- `--verbose|-v`: print stack traces on failure

## `record tag-stats`

Summarize tag distributions in a dump.

Options:
- `--input|-i <path>`: input JSON array/JSONL (required)
- `--top <n>`: show top-N tags (default `20`)
- `--verbose|-v`: print stack traces on failure

## `fen tags`

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

## `puzzle tags`

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

## `puzzle text`

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

## `fen text`

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

## `move list`

List legal moves for a FEN.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--format <uci|san|both>`: output format (default `uci`)
- `--san`: output SAN instead of UCI
- `--both`: output UCI + SAN per move
- `--verbose|-v`: print stack traces on failure

## `move uci`

List legal moves for a FEN (UCI only).

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `move san`

List legal moves for a FEN (SAN only).

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `move both`

List legal moves for a FEN (UCI + SAN).

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `move to-san`

Convert a single UCI move to SAN in the given position.

Notes:
- If no FEN is provided, the standard start position is used.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `<move>`: UCI move token (e.g. `e2e4`, `a7a8q`)
- `--verbose|-v`: print stack traces on failure

## `move to-uci`

Convert a single SAN move to UCI in the given position.

Notes:
- If no FEN is provided, the standard start position is used.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `<move>`: SAN move token (e.g. `Nf3`, `exd5`, `O-O`)
- `--verbose|-v`: print stack traces on failure

## `move after`

Apply a single move (UCI or SAN) and print the resulting FEN.

Notes:
- If no FEN is provided, the standard start position is used.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `<move>`: move token (UCI or SAN)
- `--verbose|-v`: print stack traces on failure

## `move play`

Apply a move sequence (UCI or SAN) and print the resulting FEN.

Notes:
- If no FEN is provided, the standard start position is used.
- Move lists are cleaned using the PGN sanitizer (move numbers and comments are ignored).

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `<moves...>`: move sequence (UCI or SAN)
- `--intermediate`: print intermediate FENs after each move instead of only the final FEN
- `--verbose|-v`: print stack traces on failure

## `fen normalize`

Parse a FEN and print the normalized FEN used internally by ChessRTK.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `fen validate`

Validate a FEN and print `valid<TAB><normalized-fen>` on success.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `engine analyze`

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

## `engine threats`

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

## `engine bestmove`

Return the best move for a FEN.

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--format <uci|san|both>`: output format (default `uci`)
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

## `engine bestmove-uci`

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

## `engine bestmove-san`

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

## `engine bestmove-both`

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

## `engine perft`

Run detailed perft on a position using the Java-native `chess.core` move
generator. The output includes leaf nodes, captures, en-passant captures,
castles, promotions, checks, checkmates, elapsed time, and nodes per second.
When no position selector is provided, the command defaults to the standard
start position.

Options:
- `--fen "<FEN...>"`: FEN string (or pass it positionally; defaults to start position)
- `--startpos`: use the standard chess start position explicitly
- `--randompos`: use one reachable random legal standard-chess position
- `--depth|-d <n>`: perft depth (required)
- `--divide|--per-move`: print per-root-move detailed counters as a table
- `--format <detail|table|stockfish>`: output format. `table` and `stockfish`
  imply divide; `detail` preserves the key-value divide rows.
- `--threads <n>`: worker threads for legal root moves (default: 1)
- `--verbose|-v`: print stack traces on failure

## `engine perft-suite`

Compare each critical standard and Chess960 perft position at one requested
depth against stored in-repo truth values. The command shows a progress bar
while the rows run, then prints an aligned table with `Truth`, `Calculated`,
`Speed`, and `Match` columns. It never starts Stockfish or any other external
engine process.

The suite covers the standard start, Kiwipete, promotion, en-passant, open
castling lanes, Chess960 starts/midgames, and a knight-heavy stress position.
`Calculated` is always produced by the in-process Java core move generator.

Options:
- `--depth|-d <n>`: depth to validate, 1 through 6 (default: 6)
- `--threads <n>`: worker threads for independent positions (default: 1)

## `doctor`

Check the local Java runtime, main config file, configured engine protocol,
engine executable discovery, model paths, and output directory path. Missing
model files are warnings because models are optional local artifacts.

Options:
- `--strict`: exit non-zero when warnings are present
- `--verbose|-v`: print stack traces on failure

## `fen pgn`

Convert PGN games to a FEN list that can be used as seeds.

Options:
- `--input|-i <path>`: input PGN file (required)
- `--output|-o <path>`: output `.txt` (optional; default derived)
- `--pairs`: write "parent child" FEN pairs per line
- `--mainline`: only output the mainline (skip variations)
- `--verbose|-v`: print stack traces on failure

## `engine eval`

Evaluate a position using the Java LC0 evaluator with classical fallback, or
force one backend explicitly. This command does not use a UCI engine.

Options:
- `--input|-i <path>`: FEN list file (optional)
- `--fen "<FEN...>"`: FEN string (or pass it positionally)
- `--lc0`: force LC0 evaluation and fail if LC0 cannot run
- `--classical`: force classical evaluation
- `--weights <path>`: LC0J `.bin` weights path (optional)
- `--terminal-aware`: use terminal-aware classical evaluation
- `--verbose|-v`: print stack traces on failure

## `engine static`

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
