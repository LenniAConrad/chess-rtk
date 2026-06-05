# Architecture

Most chess tools accrete a second, third, and fourth notion of "what is legal" as they grow, and then spend their lives reconciling them. ChessRTK ("crtk") refuses to. There is **one shared chess core**, and every part of the tool asks it the same questions. A FEN that is legal for `move list` is legal in exactly the same way for `engine perft`, `fen tags`, `record dataset npy`, `book render`, and the Swing `workbench`. Notation, search, evaluation, tagging, datasets, rendering, publishing — all of it sits on the single `chess.core` model of the board and the rules. That is why two commands never quietly disagree. This page maps the layers, names the real packages, and follows a command from a CLI flag down to its output.

## What "one shared core" buys you

The bugs that hurt most in chess tooling are the consistency bugs. Let notation conversion believe one thing about castling rights and the move generator believe another, and the disagreement leaks into everything downstream: a SAN string, a perft count, a tagged puzzle, a training tensor, a printed diagram. None of them is obviously wrong on its own. Routing every question through one core closes that gap:

- Is this FEN legal, and what is its normalized form?
- Which moves are legal here, and what are their SAN and UCI spellings?
- What position results after this move or this whole line?
- Do perft counts match known reference positions (including Chess960)?
- What should the tagger, dataset exporter, renderer, and book generator all see?

One implementation answers all of them, so `fen normalize`, `engine perft-suite`, and a rendered PDF are demonstrably talking about the same chess.

![ChessRTK position toolbox](../assets/diagrams/crtk-position-toolbox.png)

## Layer map

The codebase is a stack, and the dependency arrows point one way. Lower layers know nothing of the ones above; higher layers reuse the core instead of reinventing chess inside themselves.

| Layer | Packages | Responsibility |
| --- | --- | --- |
| Chess core | `chess.core`, `chess.struct` | Board state, legal movegen, make/undo, attacks, FEN/SAN/UCI, Chess960, records |
| Movegen tooling | `chess.debug`, `chess.debug.gpu` | Perft, divide, perft-suite, optional GPU perft dispatch |
| Search | `chess.engine` | Built-in MCTS, alpha-beta, forced-mate prover, evaluator hooks |
| Evaluation | `chess.eval`, `chess.classical` | Classical/static eval, NNUE, LC0, OTIS evaluator backends |
| Neural nets | `chess.nn.*`, `chess.gpu` | LC0 CNN/BT4, NNUE, OTIS, T5, classifier model loaders + native ops |
| External engines | `chess.uci` | UCI protocol orchestration, analysis parsing, MultiPV/WDL |
| Workflow + I/O | `chess.io`, `chess.puzzle`, `chess.tag`, `chess.describe`, `chess.eco` | Puzzle mining/scoring, tagging, dataset export, descriptions |
| Rendering + publishing | `chess.images`, `chess.pdf`, `chess.book` | Board images/SVG, native PDF books, covers, studies |
| Application | `application.cli`, `application.console`, `utility` | Deterministic command contracts, dispatch, arg/config parsing |
| Desktop UI | `application.gui.workbench.*` | Native Swing workbench reusing every layer below |

Entry is `application.Main`, which dispatches into `application.cli`.

### Chess core (`chess.core`, `chess.struct`)

Everything else stands on this. `Position` holds the mutable board state; `MoveGenerator`, `MoveList`, and `Move` produce and represent legal moves; `PositionRules`, `PositionMoveSupport`, and `PositionUndoState` carry make/undo and legality; `SlidingAttacks` and the attack helpers answer the check, pin, and threat questions. Notation lives in `Fen` and `SAN`, with `Setup` handling Chess960 start positions and the castling rules they imply. `chess.struct` defines the `Record`, `Game`, `Pgn`, and `Plain` shapes that the workflow layers pass around. Crucially, nothing here depends on engines, neural nets, or I/O — the core can be reasoned about, and tested, in isolation.

### Movegen tooling (`chess.debug`)

Perft is how a move generator proves it is honest: count the leaves to a fixed depth and compare against numbers the chess world already agrees on. `Perft`, `SplitPerft`, and `PerftSuite` do exactly that, backing `engine perft`, `engine perft-suite`, and `engine benchmark`. The `chess.debug.gpu` package (`GpuPerft`, `NativePerftBackend`, `PositionCodec`) can hand the deep part of the tree to a native GPU library; `--split N` sets where the CPU stops expanding and the device takes over.

### Search (`chess.engine`)

The built-in search never leaves the JVM, which keeps it dependency-free and trivially scriptable. `AlphaBeta` (with `AspirationWindow`, `NegamaxSetup`, `QuiescenceSetup`) is the default `engine builtin` search for classical/NNUE and plays the Workbench Play opponent; `Mcts` and `MctsUci` provide the policy/value Monte Carlo Tree Search used by LC0/OTIS and by the minimal UCI loop. `MateProver` and `ProofState` are the brute-force forced-mate prover behind `engine mate`. `EvaluatorBackend`, `SearchBackend`, and the `*Backend` classes (`CnnBackend`, `OtisBackend`, `Bt4Backend`) are the seam between search and whatever scores a position.

### Evaluation (`chess.eval`, `chess.classical`)

One abstraction, several scorers behind it. `chess.eval` defines the evaluator interface (`Evaluator`, `Backend`, `Kind`, `Factory`) and ships `Classical`, `Nnue`, `Lc0`, and `Otis` implementations, plus `See` for static exchange evaluation. The classical scorer's heavier scanning routines live in `chess.classical`, kept separate so the hot loops stay readable. Together they back `engine static` (classical only) and `engine eval` (`auto`, `lc0`, `otis`, or `classical`).

### Neural networks (`chess.nn`, `chess.gpu`)

Model loading and inference live under `chess.nn`: `chess.nn.lc0` (the CNN and BT4 paths), `chess.nn.nnue`, `chess.nn.otis`, `chess.nn.t5` (the natural-language summaries behind `fen text` and `puzzle text`), and `chess.nn.classifier`. `chess.gpu` (`BackendNames`, `SharedLibrarySupport`) decides which backend is live — `cpu`, `cuda`, `rocm`, or `oneapi` — and the native CUDA, ROCm, and oneAPI sources for OTIS and T5 sit right beside the in-process loaders that call them.

> Treat the neural networks as usable evaluators, not clones of their upstream namesakes. The LC0 CNN path works as a CNN evaluator; the BT4 path is simplified and experimental, and crtk makes no claim of bit-exact LC0 or BT4 parity. When you want the strongest play, point crtk at a real external engine through `chess.uci`.

### External engines (`chess.uci`)

When you need real strength, this layer talks to it. `Engine`, `Protocol`, `Analysis`, and `Output` drive external UCI engines such as Stockfish and Leela Chess Zero — spawning the process, speaking the protocol, and parsing back the MultiPV, WDL, node, time, thread, and hash fields. It powers `engine analyze`, `engine bestmove`, `engine threats`, `engine compare`, the `*-batch` commands, and `engine uci-smoke`. Each binary is described by a protocol TOML file passed with `--protocol-path`, so swapping engines is configuration, not code.

### Workflow and I/O (`chess.io`, `chess.puzzle`, `chess.tag`, `chess.describe`, `chess.eco`)

Above the core sit the products a research pipeline actually ships:

- `chess.io` — record readers/writers and exporters: `RecordPgnExporter`, `RecordDatasetExporter`, `RecordLc0Exporter`, `ClassifierDatasetExporter`, `PuzzleEloExporter`. These back `record export *`, `record dataset *`, and `record files`/`stats`.
- `chess.puzzle` — puzzle scoring and difficulty signals (`Scorer`, `Difficulty`, `Goal`) used by `puzzle mine`.
- `chess.tag` — deterministic position/tactic/theme tagging (`Generator`, `Detector`, `Emitter`, `Checkmate`, plus `core`, `material`, `move`, `pawn`, `piece`, `position` detectors) behind `fen tags` and `puzzle tags`.
- `chess.describe` — deterministic and T5 position descriptions behind `position describe`.
- `chess.eco` — ECO opening lookup.

### Rendering and publishing (`chess.images`, `chess.pdf`, `chess.book`)

`chess.images.render` draws boards to PNG/JPG/BMP/SVG with arrows, circles, legal-move dots, and themes (behind `fen render` and `fen display`). `chess.pdf` is a from-scratch PDF writer — **no LaTeX, no external typesetter** — which means a clean machine produces the same book as a cluttered one. `chess.book` (`collection`, `study`, `render`, `cover`) assembles publication-ready puzzle books and diagram PDFs behind the `book` commands.

### Application layer (`application.cli`, `utility`)

This is where the noun-verb grammar lives. `CliRegistry` maps each area (`fen`, `move`, `engine`, `record`, `puzzle`, `book`, `position`, `config`, `batch`, `doctor`, `clean`, `workbench`, `version`, `help`) to its `application.cli.command.*` handler. The consistency you feel using crtk comes from shared helpers — `EngineOps`, `EvalOps`, `ConfigOps`, `PathOps`, `Format` — rather than copied conventions. `utility.Argv` parses flags and `application.Config` resolves defaults, so `--fen`, `--input`, `--output`, `--format`, `--max-nodes`, and `--max-duration` mean the same thing everywhere they appear.

### Desktop workbench (`application.gui.workbench`)

The native Swing workbench (`workbench`, alias `gui`) is deliberately thin: a UI over the layers below, with no chess logic of its own to drift out of sync. Its subpackages track its features — `board`, `play` (play against the in-process engine), `command` (CLI command forms), `dataset`, `network` (neural-net visualizers), `publish` (book previews), `dashboard`, `game`, and `session`. Play opponents run in-process via `chess.engine`; an external engine only starts for live analysis and the UCI-backed CLI commands.

## Command flow

Learn the shape once and every command becomes legible:

1. `application.Main` dispatches the `<area> <action>` to a `CliRegistry` handler.
2. `utility.Argv` parses flags; `application.Config` resolves defaults.
3. The handler builds a `Position` from a FEN, PGN, generated seed, or record.
4. It runs the request against the shared `chess.core` (plus search/eval/tag/render layers as needed).
5. It emits a deterministic text, JSON/JSONL, image/SVG, tensor, or PDF output.

Step 4 forks when a command needs analysis:

- **In-process** — call the built-in engine in `chess.engine` (`engine builtin`, `engine java`, `engine mate`, Workbench Play).
- **External** — spawn a configured UCI engine via `chess.uci` (`engine analyze`, `engine bestmove`, `engine threats`, `engine compare`, the `*-batch` commands).

## Data flow

The layers compose into a pipeline that runs from raw games to a finished artifact:

```text
PGN / FEN seeds / random shards
  -> puzzle mine | engine analyze
  -> .record files
  -> record stats | record tag-stats | record analysis-delta
  -> record export plain|csv|pgn|puzzle-jsonl|puzzle-elo-jsonl|training-jsonl
  -> record dataset npy|lc0|classifier
  -> fen render | book collection | book render | downstream training
```

`puzzle mine` writes `*.puzzles.json` and `*.nonpuzzles.json`, gated by a Filter DSL; `record files` merges, filters, and splits what comes out; the export and dataset commands reshape it into JSONL, CSV, PGN, or tensors; and the `book` commands turn the verified puzzles into native PDFs. Each stage reads the previous stage's files, so you can stop, inspect, and resume anywhere.

## Native GPU backends

GPU support is an accelerator, never a requirement. The in-process loaders in `chess.nn.otis`, `chess.nn.t5`, and `chess.nn.perft` probe for a native CUDA, ROCm, or oneAPI shared library at load time; find none and crtk simply runs on the CPU — no error, no missing feature, just slower. See what got wired up with `engine gpu`, push movegen onto the device with `engine perft --gpu` (and `--split N`), and tune `engine perft-suite --gpu`. The active backend reports as one of `cpu`, `cuda`, `rocm`, or `oneapi`.

## Design constraints

A few rules earn the rest of the design, and they are not negotiated away for convenience:

- Core commands are deterministic by default: same input, same output, every run.
- Engine work states its budget out loud through `--max-nodes` and `--max-duration` — no silent open-ended search.
- Model files, engine binaries, records, and generated PDFs stay local artifacts, never hidden state.
- Output favors line-based or structured (`--json`/`--jsonl`) forms, because the next command in your pipe should not have to guess.
- Anything that touches legality ships only after `engine perft-suite` and the SAN/FEN regressions agree.
- The whole project builds with `javac --release 17` — no Maven, no Gradle. Licensed GPL-3.0-only.

## Related pages

- [Getting started](getting-started.md)
- [Command reference](command-reference.md)
- [In-house Java engine](in-house-engine.md)
- [LC0 UCI engine and Java evaluator](lc0.md)
- [Workbench](workbench.md)
- [Development notes](development-notes.md)
