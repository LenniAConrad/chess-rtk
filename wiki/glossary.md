# Glossary

Chess research has its own dense vocabulary, and crtk borrows freely from all of it: the notations players use to write moves, the algorithms engines use to choose them, the network pieces that score positions, and a handful of formats crtk invented for itself. Because everything sits on one shared core, a term means the same thing at the CLI, in a batch script, and in the Workbench. The definitions below are deliberately terse. When one term deserves a chapter, the cross-link points to it.

## Terms

| Term | Meaning |
| --- | --- |
| **FEN** | Forsyth-Edwards Notation. A single-line string encoding a complete chess position (piece placement, side to move, castling rights, en-passant target, clocks). The primary input format for nearly every crtk command; see the `fen` and `move` areas in the [Command Reference](command-reference.md). |
| **SAN** | Standard Algebraic Notation. Human-readable move notation such as `Nf3`, `exd5`, `O-O`, or `Qxe8+`. Produced and parsed by `move san`, `move to-san`, and `engine bestmove-san`. |
| **UCI** | Universal Chess Interface. The text protocol crtk uses to drive external engines such as Stockfish and LC0, and also a compact move notation like `e2e4` or `e7e8q`. See `engine analyze`, `move uci`, and `move to-uci`. |
| **perft** | Performance test. Counts the exact number of legal move-tree nodes to a fixed depth, used to validate move generation. Run with `engine perft` (add `--divide` for a per-root-move breakdown) and `engine perft-suite`. |
| **MCTS** | Monte Carlo Tree Search. One of crtk's built-in search algorithms, which grows a search tree by sampling promising lines under a playout budget. Invoked with `engine builtin --search mcts` (alias `engine java --search mcts`) and used by default for LC0/OTIS. |
| **alpha-beta** | A pruning search algorithm that skips branches proven irrelevant to the result, the classic method used by NNUE-style engines. In crtk it is one of the Workbench Play search modes, paired with a local evaluator. |
| **NNUE** | Efficiently Updatable Neural Network. A compact evaluator architecture that updates its accumulator incrementally as moves are made. Selectable as an evaluator for the built-in engine (`engine builtin --evaluator nnue`). |
| **LC0** | Leela Chess Zero. Either an external LC0 UCI engine or crtk's Java LC0-style CNN evaluator path (`engine eval --lc0`). crtk's neural networks are usable evaluators but are not bit-exact reproductions of upstream LC0 weights. |
| **CNN** | Convolutional Neural Network. The network family used by the LC0-style evaluator, which reads the board as stacked planes and emits policy and value heads. Available as a local backend in Workbench Play. |
| **WDL** | Win/Draw/Loss probabilities. An alternative to a single centipawn score, reported alongside it by engines that support it. Enable with `--wdl` (disable with `--no-wdl`) on analysis commands. |
| **MultiPV** | Multiple principal variations. Asks the engine for its top *N* candidate lines instead of just the best one, useful for analysis, threat detection, and puzzle mining. Controlled by `--multipv N`. |
| **PV** | Principal variation. The engine's best line of play from a position: the expected move sequence under optimal play for both sides. The first PV is the basis for the best move. |
| **OTIS** | A neural evaluator backend bundled with crtk, selectable for evaluation and the built-in engine (`engine eval --otis`, `engine builtin --evaluator otis`) and also accelerated by the native GPU backends. |
| **T5** | A text-to-text transformer crtk runs locally to produce natural-language summaries of positions and puzzle lines, via `fen text` and `puzzle text`. Honest scope: T5 output is a generated summary, not engine analysis. |
| **SentencePiece** | The unigram subword tokenizer crtk uses to encode text for the T5 model. It splits summaries into deterministic subword pieces so the same input always produces the same token stream. |
| **ECO** | Encyclopaedia of Chess Openings. A standard code-and-name system for opening lines (e.g. `B90`). crtk ships an in-memory ECO encyclopedia used to label openings in tagging and the Workbench. |
| **Chess960** | Fischer Random Chess. A variant with one of 960 shuffled back-rank starting positions and adapted castling. Supported across the shared core; print starts with `fen chess960` and mine with `puzzle mine --chess960`. |
| **record** | crtk's reusable JSON analysis-record format. Records carry a position plus its engine analysis and tags, and can be merged, filtered, summarized, and exported via the `record` commands; see [Datasets](datasets.md). |
| **tag** | A deterministic label describing a position or move (theme, tactic, motif, mate pattern). Generated with `fen tags` and `puzzle tags`, and summarized with `record tag-stats`. |
| **Filter DSL gate** | A small expression in crtk's Filter DSL used as a pass/fail gate, for example in `puzzle mine` quality filters or `record files --filter`, selecting records by score, PV shape, move type, tags, or metadata. |

## Related pages

- [Command Reference](command-reference.md) — every area and action in full, including the `fen` and `move` tools for FEN, SAN, UCI, and Chess960
- [In-house Engine](in-house-engine.md) — where MCTS, the evaluators, WDL, and MultiPV come together
- [Puzzle Mining](mining.md) — the Filter DSL and gating, applied rather than defined
- [Datasets](datasets.md) — the record format and what you can export it to
