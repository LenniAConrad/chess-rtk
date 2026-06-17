# Configuration

Most of crtk needs no configuration at all. FEN handling, `move list`, SAN/UCI conversion, `engine perft`, `engine perft-suite`, `fen tags` without `--analyze`, `engine static`, `engine mate`, the built-in search (`engine builtin`), and the Workbench Play tab run entirely in-process — no external program, no model file, nothing to set up. Configuration enters the picture only at the edges: when you drive an external UCI engine (Stockfish/LC0), load a neural-network evaluator, ask for T5 natural-language summaries, want opening tags, or tune puzzle mining. Settings live in plain TOML files under `config/` and weight files under `models/`. This page documents each of them, how a value is resolved when several sources disagree, and how to confirm a setup with `config show`, `config validate`, and `doctor`.

## At a glance

| File | Purpose | Read by |
| --- | --- | --- |
| `config/cli.config.toml` | Main CLI config: engine protocol path, default model paths, output root, search limits, threat thresholds, puzzle mining filters | All commands at startup |
| `config/cli.lc0.config.toml` | Shipped alternative baseline tuned for LC0 as the UCI engine | Copy over `cli.config.toml` to activate |
| `config/default.engine.toml` | Stockfish-style UCI protocol (commands and templates) | `engine analyze`, `engine bestmove`, `engine threats`, `engine compare`, `engine uci-smoke`, `puzzle mine`, `--analyze` tagging |
| `config/lc0.engine.toml` | LC0-style UCI protocol | Same external-engine commands when selected |
| `config/book.eco.toml` | Optional ECO opening dictionary | Tagging (`fen tags`, `puzzle tags`, mined records) |
| `models/` | Local neural-network weights (`.bin`, `.nnue`, `.pb.gz`) | `engine eval`, `engine builtin --nnue/--lc0/--otis`, `fen text`, `puzzle text`, Workbench Play |

Two terms used throughout:

- **CLI config** means `config/cli.config.toml`, the single file that holds defaults for the whole tool.
- **Engine protocol** means an `*.engine.toml` file that describes how to drive one external UCI engine.

## CLI config: `config/cli.config.toml`

Loaded once at startup. If it isn't there, crtk writes it out with documented defaults, so a fresh clone is runnable before you've touched anything. Every key also has a compiled-in fallback: a malformed value logs a warning and reverts to that fallback rather than killing the run, which means a typo costs you a setting, not the whole session.

### Keys

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `protocol-path` | string | `config/default.engine.toml` | Engine protocol TOML used by external-engine commands |
| `lc0-model-path` | string | `models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin` | Default ChessRTK LC0 CNN `.bin` weights for the in-process LC0 evaluator |
| `t5-model-path` | string | `models/t5.bin` | Default T5 `.bin` used by `fen text` / `puzzle text` when `--model` is omitted |
| `output` | string | `dump/` | Default output root for generated artifacts such as mined puzzles |
| `engine-instances` | integer | `4` | External UCI engine processes to run in parallel |
| `max-nodes` | integer | `50000000` | Per-position node budget for external analysis |
| `max-duration` | integer | `1000000` | Per-position time cap in milliseconds |
| `threat-min-cp` | integer | `100` | Minimum centipawn advantage in null-move analysis to emit a threat tag |
| `threat-equalize-min-cp` | integer | `150` | Minimum disadvantage before considering an equalizing threat |
| `threat-equalize-target-cp` | integer | `50` | Maximum remaining disadvantage after an equalizing threat |
| `puzzle-analysis-cache` | integer | `500000` (built-in); `5000000` shipped | Maximum analyzed positions remembered during mining (LRU eviction when full). The compiled default is 500,000; the bundled `config/cli.config.toml` raises it to 5,000,000, which is what `config show` reports out of the box. |
| `puzzle-quality` | string (Filter DSL) | see below | Gate requiring sufficient search depth on both PVs |
| `puzzle-winning` | string (Filter DSL) | see below | Defines a clearly winning candidate |
| `puzzle-drawing` | string (Filter DSL) | see below | Defines a clearly drawing candidate |
| `puzzle-accelerate` | string (Filter DSL) | see below | Cheap prefilter to reject obvious non-puzzles early |

TOML allows underscores in numbers, so the shipped file writes `max-nodes = 50_000_000` — the digit grouping makes a node budget legible at a glance.

### Output root behavior

The `output` value — and the `--output` flag on commands that write files — is read by its shape, not by a separate mode switch:

- A directory such as `dump/` collects timestamped files inside it.
- A file-like root ending in `.json` / `.jsonl`, e.g. `run.json`, becomes a stem: `puzzle mine` writes `run.puzzles.json` and `run.nonpuzzles.json`.
- An empty string `""` sends output to stdout where the command supports it.

### Puzzle mining filters

The four `puzzle-*` keys are [Filter DSL](filter-dsl.md) strings that decide whether an analyzed position survives as a candidate puzzle. Each reasons over per-PV facts — `eval`, `nodes`, `break=N` for the Nth principal variation — joined with `gate=AND` / `gate=OR`. The shipped defaults are calibrated for Stockfish-style centipawn evals:

```text
puzzle-quality = """
gate=AND;null=false;empty=false;
leaf[gate=AND;break=1;nodes>=50000000];
leaf[gate=AND;break=2;null=false;empty=false;nodes>=50000000];
"""
```

Eval scale, contempt, and pruning all differ between engines, so these thresholds are starting points to tune, not constants. The shipped `config/cli.lc0.config.toml` softens them (`eval>=150`, `nodes>=6000000`) for LC0's compressed evals. Any of the four can be overridden for a single run without touching TOML:

```bash
java -jar crtk.jar puzzle mine --input seeds.txt --output run.json \
  --puzzle-winning "gate=AND;leaf[eval>=250];leaf[break=2;null=false;eval<=0];"
```

> The filters compare PV1 against PV2, so the mining protocol must report at least MultiPV 2. The shipped protocol files already set `multipv` to 2 in their `setup` block; drop below it and the second `leaf` clause has nothing to read.

### Switching to the LC0-tuned baseline

`config/cli.lc0.config.toml` is a complete alternative baseline, not a patch: it points `protocol-path` at `config/lc0.engine.toml`, dials in LC0-appropriate filter thresholds, and drops to a single engine instance — one LC0 process already saturates a strong GPU, so spawning four buys you nothing but contention. Activate it by copying it over the active config:

```bash
cp config/cli.lc0.config.toml config/cli.config.toml
java -jar crtk.jar config show
```

### Per-run overrides

A CLI flag always beats TOML, for that one invocation only. The common ones:

- `--protocol-path` / `-P PATH` selects a different engine protocol.
- `--max-nodes` / `--nodes N` and `--max-duration D` (e.g. `5s`) change the per-position budget.
- `--engine-instances` / `-E N` sets the mining pool size.
- `--multipv N`, `--threads N`, `--hash N`, `--wdl` / `--no-wdl` adjust the engine for one run.

See [Command Reference](command-reference.md) for the full flag list per command.

## External UCI engine protocol: `config/*.engine.toml`

An engine protocol file is the translation layer between crtk and one external UCI engine. It spells out the exact strings crtk sends — how to handshake, set options, push a position, and start a search — so a new engine is a config change rather than a code change. These files back `engine analyze`, `engine bestmove` (and its `-uci`/`-san`/`-both`/`-batch` variants), `engine threats`, `engine compare`, `engine uci-smoke`, `puzzle mine`, and `fen tags --analyze`. They have nothing to do with `engine perft`, `engine perft-suite`, `engine mate`, `engine builtin`/`engine java`, `engine static`, or the Workbench Play opponent, all of which run in-process.

Shipped files:

- `config/default.engine.toml` — Stockfish-style defaults; expects `stockfish` on `PATH` unless you change `path`.
- `config/lc0.engine.toml` — LC0-style defaults; expects `lc0` on `PATH` unless you change `path`.

### Key fields

Templates carry `printf`-style placeholders — `%d` for an integer, `%s` for a string, `%b` for a boolean — and at most one per template.

| Field | Example | Role |
| --- | --- | --- |
| `path` | `stockfish` | Engine executable: a name on `PATH` or an absolute path |
| `name` | `Stockfish` | Friendly label (informational) |
| `showUci` / `uciok` | `uci` / `uciok` | Handshake command and the response crtk waits for |
| `isready` / `readyok` | `isready` / `readyok` | Readiness probe and its expected reply |
| `setPosition` | `position fen %s` | Template that sends the current FEN |
| `searchNodes` | `go nodes %d` | Node-limited search command |
| `searchTime` | `go movetime %d` | Time-limited search command |
| `searchDepth` | `go depth %d` | Depth-limited search command |
| `stop` / `newGame` | `stop` / `ucinewgame` | Stop search; start a new game |
| `setHashSize` | `setoption name Hash value %d` | Transposition / cache size (LC0 uses `Cache`) |
| `setMultiPivotAmount` | `setoption name multipv value %d` | Sets MultiPV (mining needs at least 2) |
| `setThreadAmount` | `setoption name threads value %d` | Engine thread count |
| `setChess960` | `setoption name UCI_Chess960 value %b` | Toggles Chess960 mode |
| `showWinDrawLoss` | `setoption name UCI_ShowWDL value %b` | Toggles WDL output |
| `setup` | array of strings | UCI commands sent once after the handshake |

The `setup` array holds your standing options — whatever you want sent once, every session. `config/default.engine.toml` turns WDL on, sets `Hash` to 4000 MB, `threads` to 4, and `multipv` to 2. `config/lc0.engine.toml` chooses a backend and cache size, plus a commented `WeightsFile` line waiting to pin a specific network.

### Pointing crtk at your engine

crtk neither ships nor builds engines — you bring your own. Install one through your package manager (`apt-get install stockfish`, say) or compile it, then point `path` in the protocol file at the executable:

```toml
path = "/usr/local/bin/stockfish"
```

Confirm the handshake before you lean on it:

```bash
java -jar crtk.jar engine uci-smoke --protocol-path config/default.engine.toml
```

When LC0 runs as a UCI engine it loads its own `.pb.gz` weights through `WeightsFile`. That has nothing to do with crtk's `lc0-model-path`, which only feeds the in-process evaluator — two separate networks, two separate paths, easy to conflate. [LC0](lc0.md) lays out both workflows.

## Neural-network models: the `models/` directory

The in-process evaluators read weight files directly — no UCI process, no subprocess at all. The binaries themselves aren't committed: `models/*.bin`, `models/*.pb.gz`, and `models/*.nnue` are git-ignored, since weights are large and version separately from code. The installer fetches the defaults:

```bash
./install.sh --models
```

| Command / feature | Weights used | Default location |
| --- | --- | --- |
| `engine eval --evaluator lc0` (and `auto`) | ChessRTK LC0 CNN `.bin` | `lc0-model-path` from CLI config |
| `engine builtin --nnue` | NNUE network | `models/crtk-halfkp.nnue` or `--weights` |
| `engine builtin --lc0` | ChessRTK LC0 CNN `.bin` | `lc0-model-path` or `--weights` |
| `engine builtin --otis` / `engine eval --otis` | OTIS `.bin` | `models/otis_policy_wdl_random.bin` or `--weights` |
| `fen text` / `puzzle text` | T5 `.bin` | `t5-model-path` or `--model` |
| Workbench Play (`NNUE`/`CNN`/`OTIS` selectors) | the corresponding files above | same defaults |

A few behaviors worth knowing:

- `engine eval --evaluator auto` reaches for the LC0 evaluator and drops to classical evaluation when no LC0 weights are present; `--lc0`, `--otis`, and `--classical` each force one backend.
- `engine static` is the classical evaluator, always, and needs no model.
- `engine builtin --nnue` fails loudly with a missing-weights error if neither `--weights` nor the default NNUE file is there — it will not silently substitute another evaluator.
- Workbench Play falls back to classical evaluation when a chosen model won't load, so a game can always start.

Treat these as research evaluators, not bit-exact clones of the engines they're modeled on. The LC0 path reads crtk's compact `.bin` format instead of LC0's protobuf, and the BT4 transformer is simplified and experimental — it is not LC0-bit-equivalent, so don't expect matching output. For filenames, architectures, and download links, see the in-repo `models/README.md`; the evaluators that consume them are documented in [In-House Engine](in-house-engine.md) and [LC0](lc0.md).

## Optional ECO book: `config/book.eco.toml`

An Encyclopedia of Chess Openings dictionary: it maps move sequences to ECO codes and opening names. An entry reads:

```text
[[A00]]
name     = "Amar Opening"
movetext = "1. Nh3"
```

With the file present, the tagging subsystem — `fen tags`, `puzzle tags`, and mined records — annotates positions reachable through book lines with tags like `eco: A00` and `opening: Amar Opening`. Without it, opening tags drop out and nothing else changes; the book is enrichment, not a dependency. Lines starting with `#` are comments.

## Resolution order

Every setting passes through three layers, each overriding the last:

1. Compiled-in defaults (see `src/application/Config.java`).
2. `config/cli.config.toml` — auto-created with those defaults if absent.
3. The CLI flag for the command, scoped to that one invocation.

Model and protocol paths are resolved lazily: crtk reads the configured path and checks the file exists only when the feature is actually exercised, not at startup. Relative paths resolve against the working directory, so either run crtk from the repository root — where `config/` and `models/` live — or write absolute paths into the config and stop worrying about it.

## Environment

Configuration lives in TOML and flags, not a wall of environment variables, so the runtime expectations stay short:

- A Java 17+ runtime on `PATH`. crtk targets Java 17 (built with `javac --release 17`) and runs on newer JDKs; `doctor` prints the version it found.
- Any engine named in a protocol file must resolve — by name on `PATH` or by absolute `path`.
- Ordinary JVM flags still apply at launch. Large mining runs benefit from more heap, e.g. `java -Xmx8g -jar crtk.jar puzzle mine ...`, since memory use scales with `puzzle-analysis-cache`.

## Verifying your configuration

Three commands confirm a setup without committing to a full workload.

### `config show`

Prints the resolved CLI config — the absolute config path and every effective value, including the fully parsed puzzle filters. It's the fastest way to check that an override or a swapped baseline actually took.

```bash
java -jar crtk.jar config show
java -jar crtk.jar config show --json
```

```text
Config path: /home/you/chess-rtk/config/cli.config.toml
Protocol path: config/default.engine.toml
LC0 model path: models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin
T5 model path: models/t5.bin
Output: dump/
Engine instances: 4
Max nodes: 50000000
Max duration (ms): 1000000
Puzzle analysis cache: 5000000
Puzzle quality: gate=AND;...
```

`--json` emits one stable `crtk.config.v1` object with the same resolved values, using script-friendly field names such as `config`, `protocol`, `engineInstances`, `maxNodes`, `maxDurationMs`, and `puzzleQuality`.

### `config validate`

Checks the config and reports problems — a model path pointing at a file that isn't there, for instance. A missing optional model is a warning, not a failure, since you may simply not use that evaluator.

```bash
java -jar crtk.jar config validate
```

```text
Warnings:
  - Missing model file for t5-model-path: /home/you/chess-rtk/models/t5.bin
```

### `doctor`

The broader sweep: runtime version, CLI config, engine protocol, output root, and local artifacts. It reports one overall status — `ok`, `ok-with-warnings`, or an error — followed by an itemized warning list. `--strict` promotes warnings to a non-zero exit, which is what you want when this runs in CI.

```bash
java -jar crtk.jar doctor
java -jar crtk.jar doctor --strict
java -jar crtk.jar doctor --json
```

`--json` emits one stable `crtk.doctor.v1` object with the text-mode status, Java version, resolved config/protocol/output values, warning/error arrays, and a `nativeBackends` matrix matching `engine gpu`.

```text
doctor: ok-with-warnings
Java: 21.0.11
Config: /home/you/chess-rtk/config/cli.config.toml
Protocol: config/default.engine.toml
Engine instances: 4
Output: dump/
Warnings:
  - Missing model file for t5-model-path: /home/you/chess-rtk/models/t5.bin
```

## Related pages

- [Getting Started](getting-started.md) — first run and verification
- [Build and Install](build-and-install.md) — building the jar and fetching models with `install.sh`
- [LC0](lc0.md) — the UCI-engine versus in-process evaluator workflows
- [In-House Engine](in-house-engine.md) — the built-in evaluators and search
- [Filter DSL](filter-dsl.md) — the language behind the `puzzle-*` keys
- [Command Reference](command-reference.md) — every command, action, and flag
