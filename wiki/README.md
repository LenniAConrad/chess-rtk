# ChessRTK Documentation

ChessRTK (`crtk`) is a Java 17 command-line toolkit for chess research,
position handling, move-generation validation, engine orchestration, puzzle
mining, dataset export, tagging, rendering, and book publishing.

If the launcher is not installed, replace `crtk <command> ...` with
`java -cp out application.Main <command> ...` after compiling the project.

## Start Here

- [Build and install](build-and-install.md)
- [Configuration](configuration.md)
- [Command reference](command-reference.md)
- [Example commands](example-commands.md)
- [Troubleshooting](troubleshooting.md)

## Capability Map

| Task | Main commands | Docs |
| --- | --- | --- |
| Validate, normalize, and print positions | `fen validate`, `fen normalize`, `fen print` | [Command reference](command-reference.md) |
| Generate and extract positions | `fen generate`, `fen pgn`, `fen chess960` | [Mining puzzles](mining.md) |
| List, convert, and apply moves | `move list`, `move to-san`, `move to-uci`, `move after`, `move play` | [AI agents and automation](ai-agents.md) |
| Verify move generation | `engine perft`, `engine perft-suite` | [Development notes](development-notes.md) |
| Analyze with UCI engines | `engine analyze`, `engine bestmove`, `engine threats`, `engine uci-smoke` | [Configuration](configuration.md) |
| Search in-process | `engine builtin`, `engine static`, `engine eval` | [In-house Java engine](in-house-engine.md) |
| Mine and export puzzles | `puzzle mine`, `puzzle pgn`, `record files` | [Mining puzzles](mining.md), [Filter DSL](filter-dsl.md) |
| Tag positions and lines | `fen tags`, `puzzle tags` | [Tagging implementation plan](tagging-implementation-plan.md), [Piece tags](piece-tags.md) |
| Generate text from tags | `fen text`, `puzzle text` | [T5 tag-to-text pipeline](t5.md) |
| Export training data | `record dataset npy`, `record dataset lc0`, `record dataset classifier`, `record export training-jsonl`, `record export puzzle-jsonl` | [Datasets](datasets.md) |
| Render images and PDFs | `fen render`, `fen display`, `book pdf`, `book render`, `book cover` | [Book publishing](book-publishing.md) |
| Automate safely | `doctor`, `config validate`, deterministic move/bestmove commands | [AI agents and automation](ai-agents.md) |

## Core Workflow

1. Build the project with the JDK or install the `crtk` launcher.
2. Validate configuration with `crtk doctor` and `crtk config validate`.
3. Create seed positions from FEN files, PGNs, random legal generation, or
   Chess960 starts.
4. Inspect and validate the chess core with `move list`, `fen normalize`,
   `engine perft`, and `engine perft-suite`.
5. Analyze positions with a configured UCI engine or with the built-in Java
   search and evaluators.
6. Mine, filter, and summarize puzzle/record dumps with the `puzzle` and
   `record` command groups.
7. Export the resulting data to PGN, CSV, JSONL, NumPy tensors, LC0-style
   tensors, classifier tensors, diagrams, or PDFs.

## Deep Dives

- [Mining puzzles](mining.md)
- [Filter DSL](filter-dsl.md)
- [Outputs and logs](outputs-and-logs.md)
- [Datasets](datasets.md)
- [In-house Java engine](in-house-engine.md)
- [LC0 UCI weights and Java evaluator](lc0.md)
- [T5 tag-to-text pipeline](t5.md)
- [AI agents and automation](ai-agents.md)
- [Development notes](development-notes.md)

## Publishing

- [Book publishing](book-publishing.md)
- [Piece tags](piece-tags.md)
- [Tagging implementation plan](tagging-implementation-plan.md)

## Maintenance

- [Releasing](releasing.md)
- [Roadmap and ideas](roadmap.md)
