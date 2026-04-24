# Development notes

## Project shape

- `src/application/` — CLI entry point, config loading, session/log plumbing
  - `application.Main`: subcommand parsing + dispatch
  - `application.Config`: loads `config/cli.config.toml` (auto-seeded if missing)
- `src/chess/` — chess core, UCI protocol, mining pipeline, render/display helpers
  - `chess.core`: Java-native board state, FEN/SAN helpers, legal move generation, and make/unmake
  - `chess.book.render`: native puzzle-book layout, table text, and figurine movetext formatting
  - `chess.debug`: perft runners, perft validation suite, board/move printers, and logging helpers
  - `chess.uci`: engine protocol + analysis parsing + Filter DSL
  - `chess.io`: converters, readers/writers, dataset exporters
  - `chess.engine`: in-process alpha-beta search used by `engine builtin`
  - `chess.eval`: in-process evaluator facade used by `engine eval`,
    `engine static`, `engine builtin`, display ablation, and tag helpers
  - `chess.nn.lc0`: pure-Java LC0 forward pass with optional CUDA, ROCm, and
    oneAPI native backends

## Core chess implementation

The current chess rules implementation lives in `chess.core`. There is no
separate public `bitboard` package: the optimized bitboard-backed code is the
core implementation.

Important classes:
- `Position`: mutable position state, FEN round-tripping, legal move helpers,
  `play(...)`, and `undo(...)`.
- `MoveGenerator`: pseudo-legal/legal move generation, attack tests, pinned
  piece detection, and node-only perft.
- `MoveList`: compact mutable list for encoded `short` moves.
- `Fen`: strict FEN parse/format helpers, including Chess960 castling metadata.
- `SAN`: SAN formatting, SAN parsing, and move-line application.
- `Setup`: standard starts, Chess960 starts, and random position helpers.

Detailed perft is intentionally in `chess.debug.Perft`, while the stored-truth
validation suite is in `chess.debug.PerftSuite`. Both use `chess.core`
positions and move generation, and neither path depends on an external engine.

## Shared helpers

Keep small cross-package helpers in `utility` when the behavior is genuinely
generic. Current examples:
- `utility.Numbers`: inclusive numeric clamping, unit-interval clamping, and
  8-bit color-channel clamping.
- `utility.Argv`: command-line option parsing.
- `utility.Json`, `utility.Toml`, and `utility.Svg`: dependency-free structured
  text and rendering helpers.

Rendering-specific text belongs near the renderer. For example,
`chess.book.render.MoveText` converts SAN-like solution text to figurine
algebraic notation for book tables and PDF captions.

## Adding/changing CLI commands

1. Add a new `case` in `application.Main.main(...)`.
2. Implement a `runXxx(Argv a)` handler (use `utility.Argv` for parsing).
3. Update the help text in `application.Main.help()`.
4. Update docs:
   - `wiki/command-reference.md`
   - `wiki/example-commands.md`

## Docs policy

The root `README.md` should carry the first-screen framing: what ChessRTK is,
why it is technically hard, how to verify it quickly, and where the main system
boundaries are. Longer explanations, workflows, and reference material live
under `wiki/`.

## Optional tooling

If you plan to iterate on ChessRTK regularly, consider adding a small layer of local tooling:
- `just` (or a `Makefile`) for common tasks like build/release/smoke tests
- `pre-commit` hooks for `shellcheck` (shell scripts) and `google-java-format` (Java)
- `jlink` / `jpackage` for optional self-contained distributions

Related: `wiki/roadmap.md`.
