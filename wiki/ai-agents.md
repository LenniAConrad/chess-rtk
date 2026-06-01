# Automation and AI Agents

A program driving `crtk` should never have to guess. Every command is a noun-verb invocation (`crtk move list`, `crtk engine bestmove`, `crtk fen validate`) that takes its inputs through named flags, writes a stable line-based or JSON/JSONL shape to stdout, and exits with a code that means what it says. One chess core handles legality, notation, and search for the entire toolkit, so the FEN/SAN/UCI semantics an agent learns from one command hold everywhere else. What follows: where the machine-readable contracts live, how to keep a run from going open-ended, and how to push a whole plan through in a single process.

![Agentic command contracts](../assets/diagrams/crtk-agentic-commands.png)

## Why crtk Is Agent-Friendly

- **Deterministic outputs.** Move ordering, FEN normalization, perft counters, and the built-in search return the same answer for the same inputs and budgets. The core paths carry no hidden clocks and no randomized defaults.
- **Stable contracts.** Most machine-facing commands take `--json` (one object or array) or `--jsonl` (one object per line); notation commands take `--format uci|san|both`. Parse a documented shape rather than scrape prose.
- **Explicit inputs.** Positions come in through named flags (`--fen`, `--input`, `--startpos`, `--randompos`, `--stdin`), never positional guesswork. When a free-form argument could pass for a flag, separate it with `--`.
- **Exit codes that mean something.** Success exits `0`; a bad FEN, a missing file, or a failed command exits non-zero. Errors go to stderr, so a failing run still leaves stdout clean for the parser.
- **Bounded work.** Engine and search commands take `--max-nodes`, `--max-duration`, and `--multipv`; batch and export commands take `--limit`, `--max-records`, and `--max-total`. Nothing forces an agent to trust an unbounded run.
- **One shared core.** Move generation is checked against stored perft truth positions (`engine perft-suite`), so a legality-sensitive workflow is correct without an external engine to vouch for it.

## Quickstart for an Agent

```bash
crtk doctor                 # environment + config health
crtk config validate        # config file is well-formed
crtk version --json         # {"name":"ChessRTK","launcher":"crtk","version":"...","java":"..."}
crtk help                   # area list; `crtk help <area> <action>` for any command
```

The smallest useful loop — is this position legal, and what can I play from it:

```bash
crtk fen validate --fen "<FEN>" --json
crtk fen normalize --fen "<FEN>" --json
crtk move list --fen "<FEN>" --format both
```

## Machine-Readable Output by Command

These are the commands worth reaching for first, paired with the contract each one emits.

| Task | Command | Output contract |
| --- | --- | --- |
| Validate a FEN | `fen validate --fen "<FEN>" --json` | `{"valid":true,"input":...,"fen":...}`; exits non-zero on invalid input |
| Normalize a FEN | `fen normalize --fen "<FEN>" --json` | normalized FEN object; `--jsonl` for batched stdin rows |
| List legal moves | `move list --fen "<FEN>" --jsonl` | one `{"uci":...,"san":...}` object per line |
| Legal moves (text) | `move list --format both` | `uci<TAB>san` per line, deterministic order |
| Convert one move | `move to-san`, `move to-uci` | one converted move; `--json` for an object |
| Apply one move | `move after --fen "<FEN>" <move> --json` | resulting FEN as an object |
| Apply a line | `move play --fen "<FEN>" <moves...> --jsonl` | final FEN, or one per ply with `--intermediate` |
| Best move (engine) | `engine bestmove --format both` | one best-move row |
| Best move (UCI/SAN) | `engine bestmove-uci`, `engine bestmove-san`, `engine bestmove-both` | one move row in the named notation |
| Analyze a position | `engine analyze --multipv <n>` | per-PV analysis lines |
| Best move, many FENs | `engine bestmove-batch --input <file>` | one JSON object per position (`--jsonl` default) |
| Analyze, many FENs | `engine analyze-batch --input <file> --multipv <n>` | one JSON object per position (`--jsonl` default) |
| Compare two engines | `engine compare --left-protocol <a> --right-protocol <b> --json` | rows plus summary as one object |
| In-process search | `engine builtin --format summary` | bounded built-in MCTS output; no external process |
| Prove a forced mate | `engine mate --format jsonl` | deterministic mate-prover row |
| Static evaluation | `engine static`, `engine eval` | one evaluation per position |
| Position diff | `position diff --fen "<L>" --other "<R>" --json` | changed state fields and board squares |
| Position description | `position describe --format training-jsonl` | deterministic prompt/target row with features |
| Perft counters | `engine perft --depth <d>` | nodes plus detailed counters |
| Perft regression | `engine perft-suite --depth <d>` | progress bar, then truth/calculated/match table |
| Core benchmark | `engine benchmark --json` | one benchmark object |
| Version metadata | `version --json` | one object: name, launcher, version, java |

> Notation commands (`move list`, `move uci`, `move san`, `move both`, `move to-san`, `move to-uci`, `move after`, `move play`) and `fen validate`/`fen normalize` also accept `--no-header` and `--quiet` for script-friendly piping.

## Bounding Engine and Search Runs

Cap every automated analysis. The budget flags are consistent across commands, so one agent-side policy covers all of them.

- **External UCI engine** (`engine analyze`, `bestmove`, `bestmove-batch`, `analyze-batch`, `compare`, `threats`): bound it with `--max-nodes` and `--max-duration` (`5s`, `500ms`), and pin `--multipv`, `--threads`, and `--hash` to keep the engine reproducible. Point at a protocol with `--protocol-path` (or `--left-protocol`/`--right-protocol` for `compare`).
- **Built-in MCTS** (`engine builtin`, alias `engine java`): use `--depth`, `--max-nodes` (alias `--nodes`; `0` lifts the cap), and `--max-duration` (`0` means no time cap). It runs in-process, so `--threads`/`--hash` do not apply. Choose an evaluator with `--evaluator classical|nnue|lc0|otis`.
- **Forced-mate prover** (`engine mate`): bound it with `--max-mate` (default `4`) and `--max-nodes`. This is brute force, no neural-network evaluation — depth, not heuristics, is what costs you.
- **Mining / dataset / book** commands: cap volume with `--max-total`, `--max-waves`, `--max-frontier` (`puzzle mine`), `--max-records` (record exports), or `--limit` (`book collection`, `book render`).

```bash
crtk engine bestmove --fen "<FEN>" --format both --max-duration 5s
crtk engine analyze --fen "<FEN>" --multipv 3 --max-nodes 1000000
crtk engine builtin --fen "<FEN>" --evaluator classical --max-nodes 100000 --max-duration 500ms --format summary
crtk engine mate --fen "<FEN>" --max-mate 4 --format jsonl
```

See [In-House Engine](in-house-engine.md) and [LC0 Integration](lc0.md) for engine setup and evaluator details.

## Batch Many Commands in One Run

`batch run` reads a UTF-8 script, one `crtk` command per line. Blank rows and `#` comments are skipped, and the leading `crtk` token is optional. For a fixed plan, this beats shelling out repeatedly — the runtime starts once instead of once per call.

```bash
crtk batch run --input commands.crtk
crtk batch run --stdin --keep-going
```

Script behavior:

- Each command is echoed before it runs; `--quiet` suppresses the echo and trims output to the results.
- The run stops at the first command that exits non-zero. `--keep-going` continues through the remaining rows and still reports the failures.
- `--stdin` reads the script from standard input — handy when the agent writes the plan as it goes.

Example `commands.crtk`:

```text
# normalize, list moves, then ask the engine
fen normalize --fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1" --json
move list --startpos --jsonl
engine bestmove --startpos --format both --max-duration 2s
```

For large position sets, reach past `batch run` for the dedicated batch engine commands: they load the engine once and stream one JSON object per position.

```bash
crtk engine bestmove-batch --input positions.txt --max-duration 1s --jsonl
crtk engine analyze-batch  --input positions.txt --multipv 3 --jsonl
```

## Scripting Recipes

**Verify legality before acting.** Validate, normalize, then enumerate.

```bash
crtk fen validate --fen "<FEN>" --json || exit 1
crtk move list --fen "<FEN>" --format both
```

**Advance a game without hand-editing FEN fields.** Let the core apply the moves; it tracks castling rights, en passant, and clocks for you.

```bash
crtk move after --fen "<FEN>" e2e4 --json
crtk move play --fen "<FEN>" "e4 e5 Nf3 Nc6" --jsonl
crtk move play --fen "<FEN>" e2e4 e7e5 g1f3 --intermediate --jsonl
```

**Diff two candidate positions.** A structured field/square delta, not a string comparison that breaks on cosmetic FEN differences.

```bash
crtk position diff --fen "<LEFT_FEN>" --other "<RIGHT_FEN>" --json
```

**Tag positions deterministically** for labeling pipelines (no engine required unless you add `--analyze`):

```bash
crtk fen tags --fen "<FEN>" --include-fen
crtk puzzle tags --fen "<FEN>" --no-analyze
```

**Generate natural-language summaries** with the local T5 model (see [T5 Summaries](t5.md)):

```bash
crtk fen text --fen "<FEN>" --include-fen
crtk puzzle text --fen "<FEN>" --include-fen
```

## Move-Generation Checks in CI

With one shared core, perft is the legality gate that matters. Run it after any change that could reach move generation — a regression here is silent everywhere else.

```bash
crtk engine perft --fen "<FEN>" --depth 5 --divide --threads 4
crtk engine perft-suite --depth 6 --threads 4
crtk engine perft-suite --suite custom-perft.tsv --threads 4
crtk engine benchmark --startpos --depth 5 --iterations 5 --json
```

`engine perft-suite` prints a progress bar, then a table with `No`, `Depth`, `FEN`, `Truth`, `Calculated`, `Speed`, and `Match`. A custom `--suite` TSV takes `name<TAB>depth<TAB>fen<TAB>nodes` rows, with shorter variants falling back to `--depth`. GPU acceleration is optional through `--gpu` with `--split <n>`, and quietly drops to CPU when no backend is present. See [GPU Backends](gpu.md) and [Quality and Testing](quality-and-testing.md).

## Record and Dataset Plumbing

Data transformations stay explicit and reproducible because nothing is implicit: inputs and outputs are named, and exports emit stable JSON/JSONL or tensor artifacts.

```bash
crtk record files -i dump/ -o dump/merged.json --recursive --puzzles
crtk record stats -i dump/merged.json
crtk record tag-stats -i dump/merged.json
crtk record analysis-delta -i dump/merged.json -o dump/merged.analysis-delta.jsonl
```

Dataset and JSONL exports:

```bash
crtk record dataset npy -i dump/merged.json -o training/npy/merged
crtk record dataset classifier -i dump/puzzles.json -i dump/nonpuzzles.json -o training/classifier/run
crtk record export training-jsonl -i dump/puzzles.json -o training/run.jsonl --max-records 100000
```

Puzzle mining (`puzzle mine`) writes `*.puzzles.json` / `*.nonpuzzles.json` and is gated by the Filter DSL. See [Puzzle Mining](mining.md), [Datasets](datasets.md), and [Filter DSL](filter-dsl.md).

## Setup Gates for Pipelines

Run these as preflight checks before the real work, at the top of a CI job or agent session:

- `crtk doctor` — checks the runtime, config, protocol, engines, and local artifacts. `doctor --strict` exits non-zero on warnings, not only on errors.
- `crtk config validate` — confirms the config file is well-formed.
- `crtk engine uci-smoke` — starts the configured engine and runs a tiny bounded search, proving the protocol works end to end before you depend on it.

## Practical Rules

- Take `--json`, `--jsonl`, or `--format uci|san|both|summary` over parsing prose.
- Base decisions on FEN and UCI/SAN commands, not GUI or image output.
- Put an explicit budget (`--max-nodes` and/or `--max-duration`) on every automated analysis command.
- Leave `--verbose` off in normal pipelines; reach for it only when diagnosing a failure, since it prints stack traces.
- Read the exit code: `0` is success, non-zero is failure, and stdout stays parseable on both.

> The neural evaluators (`nnue`, `lc0`, `otis`) are working position evaluators, not bit-exact reproductions of the upstream engines. The BT4/LC0 CNN path is simplified and experimental: fine for analysis and labeling, but its scores are not LC0 parity and should not be quoted as such. When you need reference-grade evaluation, route it through an external UCI engine with `engine analyze`/`bestmove`.

## Related Pages

- [Command Reference](command-reference.md) — every area, action, and flag
- [Command Cheatsheet](command-cheatsheet.md) — one-liners for common tasks
- [Example Commands](example-commands.md) — copy-pasteable walkthroughs
- [Outputs and Logs](outputs-and-logs.md) — output formats and where artifacts land
- [Configuration](configuration.md) — config keys and engine protocols
- [Use Cases](use-cases.md) — end-to-end workflows
