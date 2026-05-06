# Configuration

This repo uses TOML for configuration and keeps defaults in `config/`. Most
core commands do not need any configured external program: FEN handling, move
listing, SAN/UCI conversion, perft, perft-suite, tagging without `--analyze`,
`engine static`, and `engine builtin --classical` all run in-process.

## CLI config: `config/cli.config.toml`

Loaded on startup. If the file is missing, the CLI will create it with built-in defaults (see `src/application/Config.java`).

Common keys:
- `protocol-path`: points to the UCI engine protocol file used by external
  engine commands (default `config/default.engine.toml`)
- `lc0-model-path`: default local ChessRTK LC0 CNN `.bin` weights path for
  evaluator-backed commands/features (default
  `models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin`;
  fetch with `./install.sh --models`)
- `t5-model-path`: default local T5 `.bin` path used by `fen text` / `puzzle text` when `--model` is omitted (default `models/t5.bin`)
- `output`: default output root for `puzzle mine` (default `dump/`)
- `engine-instances`: how many engine processes to run in parallel
- `max-nodes`: per-position node cap
- `max-duration`: per-position time cap (ms)
- `threat-min-cp`: minimum centipawn advantage in null-move analysis to emit a threat tag
- `threat-equalize-min-cp`: minimum disadvantage before considering equalizing threats
- `threat-equalize-target-cp`: maximum remaining disadvantage after an equalizing threat
- `puzzle-quality`, `puzzle-winning`, `puzzle-drawing`, `puzzle-accelerate`: Filter DSL strings for mining

Notes:
- CLI flags override TOML values for a single run.
- Model binaries are not committed; local `models/*.bin`, `models/*.pb.gz`,
  and `models/*.nnue` files are ignored by git.
- For `puzzle mine --output`, a directory produces timestamped outputs; a file-like root ending in `.json`/`.jsonl` produces `<stem>.puzzles.json` and `<stem>.nonpuzzles.json`.

### Switching to the included Lc0-tuned defaults

The repo ships `config/cli.lc0.config.toml` as an alternative baseline. To use it, copy it over `config/cli.config.toml` (or move/swap the files).

## External UCI Engine Protocol: `config/*.engine.toml`

Engine protocol files describe how to talk to a specific external engine via
UCI: how to set options, how to send `position`, and how to issue `go`
commands. They are used by `engine analyze`, `engine bestmove`, `engine
threats`, `engine uci-smoke`, and puzzle mining. They are not used by
`engine perft`, `engine perft-suite`, or the built-in Java search.

Shipped files:
- `config/default.engine.toml`: Stockfish-style UCI defaults (expects
  `stockfish` on `PATH` unless `path` is changed)
- `config/lc0.engine.toml`: Lc0-style UCI defaults (expects `lc0` on `PATH`
  unless `path` is changed)

Key fields (examples from `config/default.engine.toml`):
- `path`: engine executable (name on `PATH` or absolute path)
- `showUci` / `uciok`: initialization command and response used before readiness checks
- `setPosition`: template for `position fen %s`
- `searchNodes`: template for `go nodes %d`
- `setMultiPivotAmount`: template to set MultiPV (mining expects MultiPV ≥ 2)
- `setup = [ ... ]`: UCI commands sent after `uci`/`isready`

### Building engines locally (optional)

This repo no longer ships helper scripts for building engines from source.

Use your package manager (e.g. `apt-get install stockfish`) or build engines yourself, then point `path` in your `config/*.engine.toml` to the resulting executable.

## In-Process Evaluator Models

The Java evaluators use model files directly and do not start UCI engines:

- `engine builtin --nnue` loads NNUE weights from `--weights`, or from the
  default NNUE model path; `./install.sh --models` populates that default. If
  neither exists, it fails with a missing-weights error.
- `engine builtin --lc0` loads ChessRTK LC0 CNN `.bin` weights from
  `--weights`, or from `lc0-model-path`.
- `engine eval` prefers the Java LC0 evaluator and falls back to classical
  evaluation unless `--lc0` or `--classical` is specified.
- `engine static` is always the classical evaluator.

LC0 as a UCI engine usually uses `.pb.gz` weights through the engine's own
`WeightsFile` option. The Java LC0 evaluator uses ChessRTK's `.bin` model
format. See [LC0](lc0.md) for the two workflows.

## Optional ECO book: `config/book.eco.toml`

An ECO dictionary used for opening lookups (tags / printing).

When present, the tagging subsystem (used by `tags`, `print`, and by mined records) will add:
- `eco: <code>` (e.g. `eco: A00`)
- `opening: <name>` (e.g. `opening: Amar Opening`)

If the file is missing, opening tags are simply omitted.
