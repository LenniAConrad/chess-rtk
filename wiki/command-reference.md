# Command reference

All commands are subcommands of `application.Main`.

- Installed launcher: `ucicli <command> ...`
- From classes: `java -cp out application.Main <command> ...`

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

## `cuda-info`

Print whether the optional CUDA JNI backend is available (and how many CUDA devices it sees).

Notes:
- If you built the native library under `native-cuda/`, run with `-Djava.library.path=native-cuda/build`.

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

## `mine`

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

Pretty-print a FEN as ASCII.

Options:
- `--fen "<FEN...>"` (or pass it positionally)
- `--verbose|-v`: print stack traces on failure

## `display`

Render a board image in a window (with optional overlays).

Options:
- `--fen "<FEN...>"` (or pass it positionally)
- `--arrow <uci>`: add an arrow (repeatable)
- `--circle <sq>`: add a circle (repeatable)
- `--legal <sq>`: highlight legal moves from a square (repeatable)
- `--ablation`: overlay inverted per-piece ablation scores
- `--show-backend`: print which evaluator backend was used
- `--flip|--black-down`: render Black at the bottom
- `--no-border`: hide the board frame
- `--size <px>`: window size (square)
- `--width <px>`, `--height <px>`: window size override
- `--dark|--dark-mode`: dark window styling
- `--verbose|-v`: print stack traces on failure

## `clean`

Delete session cache/logs under `session/`.

Options:
- `--verbose|-v`: print stack traces on failure

## `help`

Print the built-in usage text.
