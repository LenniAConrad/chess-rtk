# ChessRTK

ChessRTK (`crtk`) is a deterministic Java 17 chess research toolkit. One shared rules core powers the CLI, the desktop Workbench, engine workflows, puzzle mining, dataset export, board rendering, and PDF publishing.

## Start here

```bash
git clone https://github.com/LenniAConrad/chess-rtk.git
cd chess-rtk
./install.sh

crtk doctor
crtk fen print --startpos
crtk move list --startpos --format both
crtk engine perft --startpos --depth 4 --threads 4
crtk workbench
```

Need the full setup path? Read [Getting Started](getting-started.md), [Build & Install](build-and-install.md), and [Configuration](configuration.md).

## The Short Version

- **One shared core.** FEN, SAN, UCI, legality, Chess960, perft, tags, diagrams, and books all use the same implementation.
- **Scriptable CLI.** Commands follow `crtk <area> <action>` and keep stable text, JSON, and JSONL output.
- **Desktop Workbench.** The Swing app wraps the same core for board analysis, play, command forms, datasets, publishing previews, and neural-network views.
- **Research workflow.** Mine puzzles, drive UCI engines, export ML tensors, and render native PDFs without Maven, Gradle, LaTeX, or glue scripts.

## Common Paths

| Goal | Page | First command |
| --- | --- | --- |
| Install and verify | [Getting Started](getting-started.md) | `crtk doctor` |
| Learn the CLI shape | [Command Cheatsheet](command-cheatsheet.md) | `crtk move list --startpos` |
| Add a CLI command | [CLI Command Guide](cli-command-guide.md) | `java -cp out testing.CLICommandRegressionTest` |
| Validate move generation | [Quality and Testing](quality-and-testing.md) | `crtk engine perft-suite --depth 6 --threads 4` |
| Configure Stockfish or LC0 | [Configuration](configuration.md) | `crtk engine bestmove --fen "<FEN>"` |
| Mine tactical puzzles | [Puzzle Mining](mining.md) | `crtk puzzle mine --random-count 50 --output dump/` |
| Publish books or diagrams | [Book Publishing](book-publishing.md) | `crtk book render -i books/puzzles.toml --check` |
| Use the desktop app | [Workbench](workbench.md), [Study Workspace](study-workspace.md) | `crtk workbench` |
| Design Workbench UI | [Workbench Design Guide](workbench-design-guide.md) | `java -Djava.awt.headless=true -cp out testing.WorkbenchRegressionTest` |
| Extend GUI architecture | [GUI Architecture](gui-architecture.md) | `java -cp out testing.GuiArchitectureRegressionTest` |

## Documentation Map

| Section | Pages |
| --- | --- |
| Setup | [Getting Started](getting-started.md), [Build & Install](build-and-install.md), [Configuration](configuration.md), [Troubleshooting](troubleshooting.md) |
| Commands | [Command Cheatsheet](command-cheatsheet.md), [Command Reference](command-reference.md), [CLI Command Guide](cli-command-guide.md), [Example Commands](example-commands.md) |
| Workflows | [Puzzle Mining](mining.md), [Filter DSL](filter-dsl.md), [Datasets](datasets.md), [Book Publishing](book-publishing.md), [Study Workspace](study-workspace.md), [Tags](piece-tags.md) |
| Engines | [In-House Engine](in-house-engine.md), [LC0](lc0.md), [GPU Acceleration](gpu.md), [T5 Text](t5.md) |
| Project | [Architecture](architecture.md), [Quality and Testing](quality-and-testing.md), [Development Notes](development-notes.md), [Workbench Design Guide](workbench-design-guide.md), [GUI Architecture](gui-architecture.md), [Releasing](releasing.md) |

For bug reports, start with [Troubleshooting](troubleshooting.md). Include the command, input FEN or file, Java version, operating system, and engine protocol TOML when an external engine is involved.
