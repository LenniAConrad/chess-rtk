# Configuration

This repo uses TOML for configuration and keeps defaults in `config/`.

## CLI config: `config/cli.config.toml`

Loaded on startup. If the file is missing, the CLI will create it with built-in defaults (see `src/application/Config.java`).

Common keys:
- `protocol-path`: points to the engine protocol file (default `config/default.engine.toml`)
- `lc0-model-path`: default LC0J weights path for evaluator-backed commands/features (default `models/lc0_744706.bin`)
- `t5-model-path`: default T5 `.bin` path used by `tag-text` / `puzzle-text` when `--model` is omitted (default `models/t5.bin`)
- `output`: default output root for `mine-puzzles` (default `dump/`)
- `engine-instances`: how many engine processes to run in parallel
- `max-nodes`: per-position node cap
- `max-duration`: per-position time cap (ms)
- `threat-min-cp`: minimum centipawn advantage in null-move analysis to emit a threat tag
- `threat-equalize-min-cp`: minimum disadvantage before considering equalizing threats
- `threat-equalize-target-cp`: maximum remaining disadvantage after an equalizing threat
- `puzzle-quality`, `puzzle-winning`, `puzzle-drawing`, `puzzle-accelerate`: Filter DSL strings for mining

Notes:
- CLI flags override TOML values for a single run.
- For `mine-puzzles --output`, a directory produces timestamped outputs; a file-like root ending in `.json`/`.jsonl` produces `<stem>.puzzles.json` and `<stem>.nonpuzzles.json`.

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

An ECO dictionary used for opening lookups (tags / printing).

When present, the tagging subsystem (used by `tags`, `print`, and by mined records) will add:
- `eco: <code>` (e.g. `eco: A00`)
- `opening: <name>` (e.g. `opening: Amar Opening`)

If the file is missing, opening tags are simply omitted.
