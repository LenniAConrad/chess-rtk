# In-house Java engine

ChessRTK includes a small built-in Java engine exposed through
`crtk engine builtin` and `crtk engine java`.

The engine is mainly a fallback alternative and benchmarking tool. It is not
intended to be a top-tier engine competing with Stockfish or LC0. Use it when an
in-process, zero-dependency search is valuable, when a workflow should continue
without a configured UCI engine, or when you want to benchmark engine tasks such
as puzzle solve speed.

## CLI usage

Search one FEN with the default classical evaluator:

```bash
crtk engine builtin --fen "<FEN>" --depth 20
crtk engine builtin --fen "<FEN>" --depth 4 --format summary
```

Cap the search by nodes or time:

```bash
crtk engine builtin --fen "<FEN>" --depth 5 --nodes 250000 --format both
crtk engine builtin --fen "<FEN>" --depth 8 --max-duration 2s --format summary
```

When `--depth` is explicit and `--nodes`/`--max-duration` are omitted, the
search tries to complete that depth without the default node/time caps. Add
`--nodes` or `--max-duration` when you want depth to act as a maximum inside a
budget.

Read a file with one FEN per line:

```bash
crtk engine builtin --input positions.txt --depth 3 --format both
```

Select an evaluator:

```bash
crtk engine builtin --evaluator classical --fen "<FEN>"
crtk engine builtin --evaluator nnue --weights models/crtk-halfkp.nnue --fen "<FEN>"
crtk engine builtin --evaluator lc0 --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin --fen "<FEN>"
```

Shortcut flags are also available: `--classical`, `--nnue`, and `--lc0`.

## Evaluators

- `classical`: handcrafted material, piece-square, mobility, king safety, pawn
  structure, and terminal-position scoring.
- `nnue`: pure-Java NNUE evaluator using `models/crtk-halfkp.nnue` by default,
  or an explicit `--weights` path. If the default file is absent, `--nnue`
  uses a tiny neutral in-memory fallback for smoke tests; provide real weights
  for meaningful NNUE analysis.
- `lc0`: pure-Java LC0 value evaluator using the configured LC0J model path by
  default, or an explicit `--weights` path.

The evaluator is used as the static score at the search frontier. The engine
search itself is still a classical alpha-beta search; choosing `--lc0` does not
turn the engine into an LC0-style MCTS engine. LC0 evaluation is much more
expensive than the handcrafted evaluator, so high alpha-beta depths with
`--lc0` can take a long time.

## Search behavior

The search is deterministic and supports:

- iterative deepening
- negamax alpha-beta search
- quiescence search for noisy leaf positions
- transposition and static-evaluation caches
- principal variation output
- move ordering with principal variation, transposition-table, capture,
  promotion, killer-move, and history signals
- `--depth`, `--nodes`, and `--max-duration` limits

Output formats:

- default / `--format uci-info`: UCI-style `info depth ... pv ...` lines,
  followed by `bestmove ...`
- if a slow evaluator exhausts its budget before depth 1 completes, `uci-info`
  emits the static fallback as `info depth 0 ... pv ...`
- `--format uci`: best move only, e.g. `e2e4`
- `--format san`: best move only, e.g. `e4`
- `--format both`: UCI and SAN separated by a tab
- `--format summary`: FEN, evaluator, best move, score, completed depth, nodes,
  elapsed time, stopped flag, and PV

## Practical Uses

Use the built-in engine for:

- deterministic best-move output in scripts that should not spawn an external
  process
- small smoke tests in CI
- bounded puzzle-solve timing
- evaluator experiments with the same search shell
- fallback analysis when no UCI engine is configured
- quick comparisons between classical, NNUE, and LC0 value evaluators

Use a mature UCI engine for:

- strength-sensitive analysis
- long MultiPV analysis
- production puzzle mining
- large-scale tactical validation

## Benchmarking and Validation

The built-in engine reports completed depth, nodes, elapsed time, stopped flag,
score, and PV in `--format summary`:

```bash
crtk engine builtin \
  --fen "<FEN>" \
  --depth 5 \
  --nodes 250000 \
  --max-duration 2s \
  --format summary
```

Compare move-generation correctness separately from search quality:

```bash
crtk engine perft --fen "<FEN>" --depth 5
crtk engine perft --fen "<FEN>" --depth 5 --divide
crtk engine perft-suite --depth 6 --threads 4
```

Use external engine commands when you want a reference move or PV:

```bash
crtk engine bestmove --fen "<FEN>" --format both --max-duration 5s
crtk engine analyze --fen "<FEN>" --multipv 3 --max-nodes 1000000
```

When comparing engines, keep the limits explicit. Depth, node budgets, time
budgets, evaluator choice, thread count, hash size, and startup overhead all
change what a result means.
