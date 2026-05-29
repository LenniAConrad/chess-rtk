# Desktop Workbench

The ChessRTK Workbench is the native Swing GUI for the toolkit. It is the
easiest way to explore positions, run commands, inspect generated data, and use
visual features without assembling long CLI invocations by hand.

Use the CLI when a workflow needs scripting, CI, stable text output, or batch
automation. Use the workbench when the task benefits from a board, controls,
preview panes, and visual feedback.

## Launch

After installing ChessRTK, open `ChessRTK Workbench` from the applications
menu. The same app can be launched from a shell:

```bash
crtk workbench
crtk gui
crtk workbench --fen "<FEN>"
```

If the launcher is not installed, run:

```bash
java -cp out application.Main workbench --fen "<FEN>"
```

## Analysis Workspace

The analysis workspace combines a board, move navigation, legal moves, tags,
ECO lookup, a board editor, MCTS controls, display settings, and engine settings.
It supports PGN loading and game navigation from the same view.

![Workbench analysis board](../assets/screenshots/workbench-analysis.png)

## Command Controller

The command controller exposes the CLI as forms. It groups required inputs,
exclusive choices, optional flags, validation, generated command text, and run
controls so commands can be assembled without memorizing every option.

![Workbench command controller](../assets/screenshots/workbench-commands.png)

## Network Visualizer

The network tab provides NNUE, LC0 CNN, BT4, and OTIS inspection. It can show
loaded model state, inference state, feature boards, activation summaries,
atlases, trace views, runtime information, and exportable visualizations.

![Workbench network visualizer](../assets/screenshots/workbench-network.png)

## Main Areas

| Area | Purpose |
| --- | --- |
| Dashboard | Session overview, health, artifacts, and recent jobs |
| Analyze | Board, PGN, legal moves, tags, ECO, editor, MCTS, and engine controls |
| Commands | GUI forms for CLI commands |
| Batch | FEN-list and batch-command execution |
| Datasets | Dataset loading, validation, summaries, and charts |
| Publish | Diagram, book, study, collection, cover, and report previews |
| Console | Command output with terminal-style progress handling |
| Logs | Persisted workbench job logs and artifacts |
| Network | NNUE, CNN, BT4, and OTIS model diagnostics |
| Puzzles | Interactive puzzle practice |

## Keyboard And Layout

- Arrow keys navigate game positions; Home/End jump to the start or end.
- Tabs can be opened, closed, duplicated, and split into editor groups.
- The layout controls support left, right, top, bottom, and quadrant splits.
- Settings include light and dark appearance modes, board coordinates,
  animations, highlights, eval-bar behavior, and external-engine options.
