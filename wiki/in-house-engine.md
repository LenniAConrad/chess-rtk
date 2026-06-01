# Built-in Engine

Everything here runs inside the same process that issued the command — no UCI subprocess to spawn, no socket to configure, no background engine to outlive its job. The core is a deterministic PUCT-style MCTS searcher (`engine builtin` / `engine java`) that you point at one of four evaluators: classical/handcrafted, NNUE, LC0 CNN, or OTIS. Around it sit a standalone exhaustive forced-mate prover (`engine mate`), one-shot position evaluation (`engine eval` / `engine static`), an opponent-threat analyzer (`engine threats`), and a move-generator benchmark (`engine benchmark`). All of them share crtk's one chess core, return the same answer for the same inputs and budget, and stop only where you tell them to — via explicit `--depth`, `--nodes`, and `--max-duration` limits. That combination is what makes it pleasant in scripts, CI, and research you need to reproduce.

It will not out-play Stockfish or LC0, and it isn't trying to. The point is determinism and zero dependencies: a search that gives the same result every time and keeps working when no UCI engine is configured. When you need real playing strength, MultiPV, or large-scale mining, drive an external engine through `engine analyze` / `engine bestmove` instead — see [LC0 & external engines](lc0.md) and [Mining](mining.md).

## What runs entirely in-process

| Command | What it does | Needs external engine? |
| --- | --- | --- |
| `engine builtin` / `engine java` | MCTS search with a selectable evaluator | No |
| `engine mate` | Brute-force prover for forced mate-in-N | No |
| `engine eval` | One-shot LC0 / OTIS / classical evaluation | No |
| `engine static` | One-shot classical (handcrafted) evaluation | No |
| `engine benchmark` | Move-generator throughput benchmark (perft-based) | No |
| `engine threats` | Opponent-threat analysis | Yes (uses configured UCI engine) |

`engine builtin` and `engine java` are the same command — `engine java` is an alias, so pick whichever reads better in your scripts. `engine mate` also answers to `engine find-mate`.

## MCTS search — `engine builtin` / `engine java`

Search a single position with the default classical evaluator:

```bash
crtk engine builtin --fen "<FEN>" --depth 4 --format summary
crtk engine builtin --startpos --depth 4 --format both
crtk engine java --fen "<FEN>" --depth 6
```

Read a file with one FEN per line:

```bash
crtk engine builtin --input positions.txt --depth 3 --format both
```

Or expose the searcher as a minimal UCI loop, which is enough for local protocol tests:

```bash
crtk engine builtin --uci
```

### Choosing an evaluator

Pass `--evaluator classical|nnue|lc0|otis`, or use the shortcut selectors `--classical`, `--nnue`, `--lc0`, `--otis`. The default is `classical`.

```bash
crtk engine builtin --classical --depth 6 --fen "<FEN>"
crtk engine builtin --nnue --fen "<FEN>"
crtk engine builtin --evaluator nnue --weights models/crtk-halfkp.nnue --fen "<FEN>"
crtk engine builtin --lc0 --weights models/leela.bin --fen "<FEN>"
crtk engine builtin --otis --fen "<FEN>"
```

`--weights PATH` names an explicit NNUE, LC0, or OTIS model file; omit it and each neural evaluator falls back to its configured default path. Install those defaults with `./install.sh --models` — see [Build & install](build-and-install.md). The neural backends cost far more per node than the handcrafted one, so give them a real `--nodes` or `--max-duration` budget rather than the depth-derived default.

### Bounding the search

Three flags decide how long the search runs, and nothing else does:

| Flag | Meaning |
| --- | --- |
| `--depth N` / `-d N` | Search depth hint (default: `3`) |
| `--nodes N` / `--max-nodes N` | MCTS playout budget; `0` disables the node cap |
| `--max-duration D` | Wall-clock budget, e.g. `5s`; `0` means no time cap |

```bash
crtk engine builtin --fen "<FEN>" --depth 5 --nodes 250000 --format both
crtk engine builtin --fen "<FEN>" --depth 8 --max-duration 2s --format summary
```

For the MCTS CLI, `--nodes` is the playout budget directly. Set `--depth` alone — with no `--nodes` and no `--max-duration` — and the command translates depth into a small playout budget rather than searching forever. Whenever you care about the exact budget, state it: pass `--nodes` or `--max-duration` explicitly.

### Output formats

`--format` accepts `uci-info`, `uci`, `san`, `both`, or `summary` (default: `uci-info`).

| Format | Output |
| --- | --- |
| `uci-info` (default) | UCI-style `info depth ... pv ...` lines, then `bestmove ...` |
| `uci` | Best move only, e.g. `e2e4` |
| `san` | Best move only, e.g. `e4` |
| `both` | UCI and SAN, separated by a tab |
| `summary` | FEN, evaluator, best move, score, completed depth, nodes, elapsed time, stopped flag, and PV |

If a slow evaluator burns through its budget before depth 1 even completes, `uci-info` falls back to a static evaluation and emits it as `info depth 0 ... pv ...` — you still get a move out of it.

## Forced-mate prover — `engine mate`

`engine mate` answers one question without guessing: does the side to move have a forced mate within the given distance? It is an AND/OR proof search over legal moves, ordered to try forcing replies first and memoizing its proof bounds. No evaluator, no network — the verdict is proven, not estimated.

```bash
crtk engine mate --fen "<FEN>" --max-mate 4
crtk engine mate --fen "<FEN>" --max-mate 6 --nodes 5000000 --threads 4 --format summary
```

| Flag | Meaning |
| --- | --- |
| `--mate N` / `--max-mate N` | Maximum mate distance in moves (default: `4`) |
| `--nodes N` / `--max-nodes N` | Proof node budget; `0` disables the node cap |
| `--threads N` | Worker threads for root moves (default: `1`) |
| `--format FORMAT` | `summary`, `uci`, `san`, `both`, `json`, or `jsonl` (default: `summary`) |
| `--json` / `--jsonl` | Aliases for `--format json` / `--format jsonl` |

Because the search is exhaustive within `--max-mate`, a positive result is a forced mate, full stop. Raise `--max-mate` to reach for longer mates; raise `--nodes` if the budget cut a proof short before it could finish.

## One-shot evaluation — `engine eval` and `engine static`

Sometimes you want a number, not a search. These commands run a single forward evaluation and print the score — no tree, no playouts, no budget flags to set.

`engine static` runs the handcrafted classical evaluator:

```bash
crtk engine static --startpos
crtk engine static --fen "<FEN>" --terminal-aware
```

`engine eval` selects among the neural and classical backends:

```bash
crtk engine eval --startpos
crtk engine eval --fen "<FEN>" --lc0
crtk engine eval --fen "<FEN>" --otis --weights models/otis.bin
crtk engine eval --fen "<FEN>" --classical --terminal-aware
```

| Flag (`engine eval`) | Meaning |
| --- | --- |
| `--evaluator MODE` | `auto`, `lc0`, `otis`, or `classical` (default: `auto`) |
| `--lc0` / `--otis` / `--classical` | Shortcut evaluator selectors |
| `--weights PATH` | LC0 or OTIS weights path |
| `--terminal-aware` / `--terminal` | Enable terminal-aware evaluation |

Both commands also accept `--input PATH` to score one FEN per line.

## The evaluators

The MCTS searcher and the one-shot eval commands draw on the same four evaluators, and the split matters. `classical` and `nnue` return centipawns; the searcher uses them for leaf values and move priors. `lc0` and `otis` carry a policy head as well as a value, which lets their policy steer root exploration during MCTS rather than only scoring leaves.

| Evaluator | What it is | Default model |
| --- | --- | --- |
| `classical` | Handcrafted material, piece-square, mobility, king safety, pawn structure, and terminal-position scoring | none (built in) |
| `nnue` | Pure-Java NNUE evaluator (HalfKP-style sparse features) | `models/crtk-halfkp.nnue` |
| `lc0` | In-process LC0 CNN value/policy evaluator | configured LC0 CNN `.bin` |
| `otis` | OTIS tactical-sheaf policy/WDL evaluator | `models/otis_policy_wdl_random.bin` |

> A word on fidelity, plainly: crtk's networks are usable, deterministic evaluators, not bit-exact copies of the upstream engines. The LC0 path is a simplified CNN and the BT4-style path is experimental, so do not expect LC0/BT4 parity from either. The caveats are spelled out in [LC0 & external engines](lc0.md).

### NNUE internals

The NNUE path is kept small and legible on purpose — you can follow every step by hand:

- The compact CRTK-format network uses HalfKP-style sparse features: 64 king buckets x 10 relative piece planes x 64 piece squares = 40,960 possible features.
- For each perspective, the active sparse features accumulate into a hidden vector, clipped ReLU runs, and the side-to-move and opponent activations pass through a single linear output layer to a centipawn score.
- The loader recognizes both the compact CRTK format and supported Stockfish `.nnue` files; an unsupported large Stockfish net falls back to full-rebuild evaluation.
- `--nnue` needs its weights. If `models/crtk-halfkp.nnue` is missing it fails loudly with a missing-weights error instead of quietly substituting a smoke-test net — so a wrong score never masquerades as a real one. Install the file with `./install.sh --models`.

### OTIS

OTIS is crtk's tactical-sheaf evaluator. It emits both a move-policy distribution and a win/draw/loss head, so the policy can steer MCTS root exploration while the value scores leaves. Reach it through `engine eval --otis`, or behind the built-in MCTS search, and point `--weights` at an OTIS `.bin`. Like every crtk network it is a usable, deterministic evaluator, not a bit-exact reproduction of anything upstream. It is also the backend the optional native GPU libraries accelerate — see [GPU Acceleration](gpu.md).

![OTIS tactical-lattice mark](../assets/logo/otis/crtk-otis-lattice.svg)

## Opponent threats — `engine threats`

`engine threats` answers the counterfactual: what could the opponent do if the move were theirs? Handy for tactical alerts and annotation. It is the one command on this page that reaches outside the process — it drives the configured external UCI engine, so it takes the standard analysis flags.

```bash
crtk engine threats --startpos --max-duration 2s
crtk engine threats --fen "<FEN>" --max-duration 2s --multipv 3
```

Relevant flags include `--protocol-path|-P`, `--max-nodes`, `--max-duration`, `--multipv`, `--threads`, `--hash`, and `--wdl` / `--no-wdl`. Configure the engine protocol as described in [Configuration](configuration.md) and [LC0 & external engines](lc0.md).

## Move-generator benchmark — `engine benchmark`

`engine benchmark` times raw move generation by running perft on the shared core. It deliberately leaves search quality out of the picture, so the number reflects how fast the core enumerates moves and nothing else.

```bash
crtk engine benchmark --startpos --depth 5 --iterations 5
crtk engine benchmark --fen "<FEN>" --depth 4 --json
```

| Flag | Meaning |
| --- | --- |
| `--depth N` / `-d N` | Perft depth (default: `4`) |
| `--iterations N` | Repetitions (default: `3`) |
| `--threads N` | Worker threads for root moves (default: `1`) |
| `--json` / `--jsonl` | Emit one JSON object / one JSON object line |

For correctness rather than speed, reach for `engine perft` and `engine perft-suite` — optionally on a native GPU backend with `--gpu`. The shared-core and GPU design is covered in [Architecture](architecture.md).

## Determinism and reproducibility

Reproducibility was a design goal, not an afterthought:

- One shared chess core handles legal move generation, make/undo, attack detection, FEN/SAN/UCI, and Chess960 for every command — the same code path the rest of crtk runs on.
- The same FEN, evaluator, and budget yield the same best move, score, and PV. Every time.
- The MCTS searcher is single-threaded and keeps its transposition state in-process and deterministic. That table is internal, with no `--hash` knob; when you pass `--threads`, `--hash`, and friends to the external commands, they go to the configured UCI engine, not to the in-house searcher.
- Every run stops at an explicit `--depth`, `--nodes`, or `--max-duration` limit, which is exactly what makes the output safe to assert on in CI.

## When to use which

Reach for the built-in engine when you want:

- deterministic best-move or score output in scripts that must not spawn a subprocess
- small smoke tests and bounded timing in CI
- forced-mate proofs you can trust, via `engine mate`
- evaluator experiments that swap classical, NNUE, LC0, and OTIS inside one search shell
- local analysis when no UCI engine is configured

Reach for a mature external engine (through `engine analyze` / `engine bestmove`) when you want:

- strength-sensitive analysis and long MultiPV runs
- production puzzle mining and large-scale tactical validation

## See also

- [LC0 & external engines](lc0.md) — neural-network models, fidelity caveats, and configuring UCI engines
- [Configuration](configuration.md) — model paths and engine protocols
- [Build & install](build-and-install.md) — building with `javac --release 17` and installing models
- [Architecture](architecture.md) — the shared core, evaluators, and GPU backends
- [Workbench](workbench.md) — the desktop GUI, including play-vs-engine against the in-house search
- [Command reference](command-reference.md) — every command and flag
