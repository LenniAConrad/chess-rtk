# ChessRTK Project Map

ChessRTK is a deterministic Java 17 chess research toolkit with one shared
chess core, a noun-then-verb CLI, a Swing Workbench, and experimental engine
and neural-network research tools.

This map is the short orientation layer for contributors. Use the deeper
guides in `wiki/` and `AGENTS.md` for command details, but keep this file true
when the repo shape changes.

## Main Surfaces

| Surface | Primary paths | Role |
| --- | --- | --- |
| Shared chess core | `src/chess/core` | Rules, FEN/SAN/UCI move representation, setup, move generation, and position transitions. Everything else should reuse this instead of reimplementing chess rules. |
| Engine lab | `src/chess/engine`, `src/chess/eval`, `src/chess/nn` | Built-in alpha-beta, MCTS, evaluators, neural-network adapters, and tuning support. Stable CLI behavior can depend on this, but many search and NN pieces are research code. |
| Data and records | `src/chess/io`, `src/chess/pgn`, `src/chess/puzzle`, `src/chess/tag`, `schemas` | Deterministic conversion, validation, tagging, puzzle mining, and dataset contracts. |
| CLI | `src/application/cli`, `src/application/cli/command` | User-facing command dispatch. The public shape is `crtk <area> <action> [options]`; do not add legacy single-word commands. |
| Workbench | `src/application/gui/workbench` | Native Swing desktop app for board work, command building, engine/NN views, sessions, publishing, and research workflows. |
| Rendering and publishing | `src/chess/images`, `src/chess/pdf`, `src/chess/book`, `assets`, `reports` | Board/diagram rendering, PDF/book generation, and visual assets. |
| Tests | `src/testing` | Self-contained regression classes with `main(...)`; the suite runner is the source of truth. |
| Docs | `wiki`, `docs` | Edit `wiki`. `docs` is generated static output and tracked intentionally for publishing. |
| Native acceleration | `native` | CUDA, ROCm, oneAPI, and native test support. Keep Java fallbacks deterministic. |

## Ownership Rules

- `chess.core` owns chess rules and movement semantics. UI, CLI, and engine code
  should ask the core rather than duplicating move geometry or notation logic.
- `chess.engine` owns search algorithms, evaluator integration, time/node
  limits, transposition state, and engine result objects. It should stay usable
  without Swing or CLI dependencies.
- `application.cli` owns command parsing, dispatch, help/catalog output, and
  command-specific I/O wiring. It should call into core, engine, and data
  services rather than owning domain rules.
- `application.gui.workbench` owns Swing state, component layout, user input,
  session files, and desktop workflow glue. It should not be the source of truth
  for chess rules.
- `utility` is for dependency-free, broadly reusable helpers. UI-heavy helpers
  do not belong there long term.
- `testing` may use reflection to cover package-private Workbench internals, but
  large regression files should be split by feature area as they grow.

## Stability Tiers

| Tier | Examples | Change policy |
| --- | --- | --- |
| Stable contract | `chess.core`, schemas, CLI command names, deterministic output formats | Change only with focused tests and docs updates. |
| Product surface | Workbench board/play/session flows, generated docs site, package jar | Refactor carefully; keep user-visible behavior stable unless the task says otherwise. |
| Research surface | MCTS tuning, BT4/LC0 adapters, OTIS, NN visualizations, gauntlets | Make experimental status clear and keep claims honest; do not imply bit-exact upstream parity. |
| Local output | `dump`, `session`, `models`, `artifacts`, `out-*`, `dist` | Generated or machine-local. Do not commit weights or local run output. |

## Dependency Direction

Preferred direction:

```text
application.gui.workbench -> application.cli -> chess.* -> utility
application.gui.workbench -> chess.*
application.cli -> chess.*
testing -> application.*, chess.*, utility
```

Avoid:

- `chess.*` depending on `application.*`
- core chess rules implemented inside Swing components or CLI commands
- generated docs edited by hand
- tests depending on wall-clock ordering or local machine artifacts unless the
  test explicitly guards that behavior

## Verification Anchors

Use the focused tests for the area changed, then the standard gate before
declaring repo-level cleanup complete:

```bash
./scripts/run_regression_suite.sh build
java -cp out testing.CoreMoveGenerationRegressionTest
java -Djava.awt.headless=true -cp out testing.WorkbenchRegressionTest
java -cp out testing.SourceHeaderRegressionTest
./scripts/run_regression_suite.sh recommended
```
