# Configuration

This repo uses TOML for configuration and keeps defaults in `config/`.

## CLI config: `config/cli.config.toml`

Loaded on startup. If the file is missing, the CLI will create it with built-in defaults (see `src/application/Config.java`).

Common keys:
- `protocol-path`: points to the engine protocol file (default `config/default.engine.toml`)
- `output`: default output root for `mine` (default `dump/`)
- `engine-instances`: how many engine processes to run in parallel
- `max-nodes`: per-position node cap
- `max-duration`: per-position time cap (ms)
- `puzzle-quality`, `puzzle-winning`, `puzzle-drawing`, `puzzle-accelerate`: Filter DSL strings for mining

Notes:
- CLI flags override TOML values for a single run.
- For `mine --output`, a directory produces timestamped outputs; a file-like root ending in `.json`/`.jsonl` produces `<stem>.puzzles.json` and `<stem>.nonpuzzles.json`.

### Switching to the included Lc0-tuned defaults

The repo ships `config/cli.lc0.config.toml` as an alternative baseline. To use it, copy it over `config/cli.config.toml` (or move/swap the files).

## Engine protocol: `config/*.engine.toml`

Engine protocol files describe how to talk to a specific engine via UCI: how to set options, how to send `position`, and how to issue `go` commands.

Shipped files:
- `config/default.engine.toml`: Stockfish-style defaults (expects `stockfish` on `PATH`)
- `config/lc0.engine.toml`: Lc0-style defaults (expects `lc0` on `PATH`)

Key fields (examples from `config/default.engine.toml`):
- `path`: engine executable (name on `PATH` or absolute path)
- `setPosition`: template for `position fen %s`
- `searchNodes`: template for `go nodes %d`
- `setMultiPivotAmount`: template to set MultiPV (mining expects MultiPV ≥ 2)
- `setup = [ ... ]`: UCI commands sent after `uci`/`isready`

### Building engines locally (optional)

This repo no longer ships helper scripts for building engines from source.

Use your package manager (e.g. `apt-get install stockfish`) or build engines yourself, then point `path` in your `config/*.engine.toml` to the resulting executable.

## Optional ECO book: `config/book.eco.toml`

An ECO dictionary used when printing/logging positions. If you don't need ECO names, you can ignore this file.
