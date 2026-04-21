# AI agents & automation

ChessRTK (CLI: `crtk`) is already a solid “research pipeline” CLI. To make it *excellent* for AI agents (LLM tools, CI bots, dataset builders), focus on two things:

1) **Machine contracts** (stable output + exit codes).
2) **Batchable primitives** (suite runners and comparators).

This page is a design checklist + backlog of high-value subcommands and flags for agentic use.

---

![Agentic command contracts](../assets/diagrams/crtk-agentic-commands.png)

Diagram source: `assets/diagrams/crtk-agentic-commands.dot` (render with `dot -Tpng -Gdpi=160 -o assets/diagrams/crtk-agentic-commands.png assets/diagrams/crtk-agentic-commands.dot`).

## What's already available

- `move list --format uci|san|both`, plus `move uci`, `move san`, `move both`, for deterministic move lists.
- `engine bestmove --format uci|san|both`, plus `engine bestmove-uci`, `engine bestmove-san`, `engine bestmove-both`, for fixed-format best moves.
- `move to-san`, `move to-uci`, `move after`, `move play` for move conversion and line application.
- `fen normalize` and `fen validate` for FEN parse/serialize checks.
- `fen chess960` for stable Scharnagl-indexed Chess960 start positions.
- `record` for grouped record workflows.
- `engine static` for classical evaluation.
- `doctor` for local Java/config/protocol/artifact diagnostics.
- `engine uci-smoke` for a bounded UCI engine startup and search check.
- `engine perft-suite` for quick regression checks.
- `record files`, `puzzle pgn`, `fen pgn` for dataset plumbing.

## What AI agents need (contract)

### 1) JSON everywhere (opt-in)

For commands that are commonly composed in pipelines, add `--format json` (or `--json`) to emit a single JSON object per run (or JSONL for streams). Suggested:

- `engine analyze`, `engine bestmove`, `engine eval`
- `move list`, `fen tags`
- `engine perft`
- `record stats`, `record tag-stats`

Keep the default human-readable output, but make JSON the “no ambiguity” mode for agents.

### 2) Stable exit codes

Make exit codes predictable so agents can branch:

- `0`: success
- `1`: runtime error (IO, engine crash, parse error, unexpected exception)
- `2`: usage error (unknown flag, invalid arg)
- `3`: validation failed (a test/suite assertion failed)

### 3) Determinism switches

Add flags to make runs reproducible:

- `--seed <n>`: deterministic random position generation
- `--limit <n>`: cap processed items (even in “infinite” modes)
- `--shuffle/--no-shuffle`: control ordering
- `--time-control` / fixed `--nodes` presets to reduce run-to-run variance

### 4) Schema versioning

For JSON/JSONL outputs that are consumed by tools:

- include `schemaVersion`
- include `toolVersion` (or git hash)
- include the effective config (or a `configHash`)

---

## High-value commands to add (agent-first)

### A) Testing / correctness

#### `engine perft-suite`

Extend the existing perft suite so it can read external expected-node files.

- Input: `--suite <file>` (CSV/JSON/EPD-like) with `fen, depth, nodes`
- Output: summary + per-position diffs; `--format json`
- Exit code `3` if any mismatch

#### `rules-test`

Fast invariant checks on move generation:

- legality (king not left in check)
- reversible move round-trips (make/unmake)
- FEN parse → serialize stability (already available through `fen normalize`)

#### `pgn-validate`

Parse a PGN file and report:

- parse failures with offsets
- illegal moves / ambiguous SAN
- number of games/plies

### B) Engine health & reproducibility

#### `engine uci-smoke --format json`

Extend the existing engine health check with machine-readable output:

- include engine identity, executable path, elapsed time, depth, nodes, and PV
- use a stable schema suitable for CI logs and agent checks

#### `analyze-batch`

Analyze many FENs with strict resource limits and stable structured output:

- `--input <fens.txt>` + `--output <jsonl>`
- supports `--nodes` / `--max-duration` / `--multipv`
- emits one JSON object per position (PV(s), eval, wdl, nodes, nps, time)

### C) Evaluation + comparison (research)

#### `eval-diff`

Compare two evaluators/engines on the same positions:

- engine A vs engine B or engine vs classical
- produces correlation stats, disagreement buckets, “top deltas”
- outputs CSV/JSON for plots

#### `bestmove-diff`

Compare best moves across engines/settings:

- match rate, blunder-like disagreements (thresholded by eval swing)
- optionally request `k` candidates (`--multipv`)

#### `tactical-suite`

Run a tactics/EPD suite and score:

- solved / failed / timeouts
- score by mate-in-n or eval threshold
- emit a report and per-position trace

### D) Data pipeline utilities

#### `dump-validate`

Validate dump files (`*.puzzles.json`, `*.record`):

- JSON parse, required keys, FEN validity
- counts, schema version

#### `dump-shard`

Split large JSON arrays into shards (size or count based), ideally as JSONL.

#### `dump-dedupe`

Deduplicate by `(fen, bestmove[, pv])` with stable ordering and stats.

---

## Recommended “agent defaults” (flags)

These are small additions that massively improve automation:

- `--quiet`: suppress non-essential logs
- `--progress none|bar|ascii`: predictable progress output
- `--fail-fast`: stop on first error in batch mode
- `--strict`: treat warnings as errors (exit `3`)
- `--format json|text` and `--output -` (stdout)

---

## If you want one thing to implement first

Implement `--format json` on `engine perft-suite`, `engine analyze`, and `engine bestmove`.
Those two unlock reliable CI, regression testing, and agent-driven evaluation workflows.
