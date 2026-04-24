# ChessRTK Wiki Home

ChessRTK (`crtk`) is a Java 17 toolkit for chess research, automation, puzzle
mining, dataset export, and publishing. It is built around a shared legal move
generator and position model so commands agree about FENs, SAN, UCI moves,
Chess960, perft, tags, records, diagrams, and books.

## For Users

- [Getting started](getting-started.md)
- [FAQ](faq.md)
- [Command reference](command-reference.md)
- [Example commands](example-commands.md)
- [Configuration](configuration.md)
- [Troubleshooting](troubleshooting.md)

## For Workflows

- [Mining puzzles](mining.md)
- [Filter DSL](filter-dsl.md)
- [Datasets](datasets.md)
- [Book publishing](book-publishing.md)
- [Piece and position tags](piece-tags.md)
- [Outputs and logs](outputs-and-logs.md)
- [AI agents and automation](ai-agents.md)

## For Developers

- [Architecture](architecture.md)
- [Development notes](development-notes.md)
- [In-house Java engine](in-house-engine.md)
- [LC0 UCI engine and Java evaluator](lc0.md)
- [T5 tag-to-text pipeline](t5.md)
- [Releasing](releasing.md)
- [Roadmap and ideas](roadmap.md)
- [Glossary](glossary.md)

## First Commands

```bash
crtk doctor
crtk fen print --startpos
crtk move list --startpos --format both
crtk engine perft --startpos --depth 4
crtk engine builtin --startpos --depth 3 --format summary
```

## Support

Start with [Troubleshooting](troubleshooting.md). If you are reporting a bug,
include the exact command, input FEN or file, Java version, operating system,
engine protocol TOML if relevant, and whether
`./scripts/run_regression_suite.sh recommended` passes locally.
