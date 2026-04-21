# ChessRTK docs (CLI: `crtk`)

This folder holds the long-form docs for ChessRTK (CLI: `crtk`). The root `README.md` gives the project overview and quick build/run commands.

Command compatibility note:
- Use canonical command names only.
- Removed commands: `gui2`, `cuda-info`, `mine`, `evaluate`, `stack-to-dataset`.

If you did **not** run `./install.sh`, replace `crtk <command> ...` with `java -cp out application.Main <command> ...` (after building).

## Start here

- [Build & install](build-and-install.md)
- [Configuration](configuration.md)
- [Command reference](command-reference.md)
- [Example commands](example-commands.md)
- [Book publishing](book-publishing.md)
- [Roadmap / ideas](roadmap.md)

## Deep dives

- [Mining puzzles](mining.md)
- [Filter DSL](filter-dsl.md)
- [Outputs & logs](outputs-and-logs.md)
- [Datasets](datasets.md)
- [Lc0 (UCI weights + Java evaluator)](lc0.md)
- [T5 tag-to-text pipeline](t5.md)
- [AI agents & automation](ai-agents.md)
- [Troubleshooting](troubleshooting.md)

## Misc

- [Piece tags](piece-tags.md)
- [Development notes](development-notes.md)
- [Releasing](releasing.md)
