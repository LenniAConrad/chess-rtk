# Use Cases

ChessRTK is broad, so the easiest way to learn it is to start from the job you
want done.

## I Want To Study One Position

Use the position and move primitives first. They run in-process and do not need
a configured UCI engine.

```bash
crtk fen print --fen "<FEN>"
crtk fen normalize --fen "<FEN>"
crtk move list --fen "<FEN>" --format both
crtk move play --fen "<FEN>" "e4 e5 Nf3 Nc6"
```

Then ask an engine:

```bash
crtk engine bestmove --fen "<FEN>" --format both --max-duration 2s
crtk engine analyze --fen "<FEN>" --multipv 3 --max-duration 5s
```

Read next:

- [Command Cheatsheet](command-cheatsheet)
- [Configuration](configuration)
- [In-House Engine](in-house-engine)

## I Want To Verify Move Generation

Use perft and the stored regression suite. These commands validate ChessRTK's
Java move generator directly.

```bash
crtk engine perft --startpos --depth 4 --threads 4
crtk engine perft --fen "<FEN>" --depth 5 --divide --threads 4
crtk engine perft-suite --depth 6 --threads 4
```

Read next:

- [Quality and Testing](quality-and-testing)
- [Architecture](architecture)
- [Development Notes](development-notes)

## I Want To Mine Puzzles

Start from PGN or FEN seeds, run bounded UCI analysis, then keep accepted and
rejected records for later filtering or training.

```bash
crtk fen pgn --input games.pgn --output seeds.txt
crtk puzzle mine --input seeds.txt --output dump/run.json --engine-instances 4 --max-duration 60s
crtk record stats --input dump/run.puzzles.json
crtk puzzle pgn --input dump/run.puzzles.json --output dump/run.pgn
```

Read next:

- [Mining Puzzles](mining)
- [Filter DSL](filter-dsl)
- [Outputs and Logs](outputs-and-logs)

## I Want To Build Training Data

Use `record` commands after mining or importing analysis records.

```bash
crtk record files -i dump/ -o dump/merged.json --recursive --puzzles
crtk record dataset npy -i dump/merged.json -o training/run
crtk record dataset classifier -i dump/run.puzzles.json -i dump/run.nonpuzzles.json -o training/classifier/run
```

Read next:

- [Datasets](datasets)
- [Piece and Position Tags](piece-tags)
- [Filter DSL](filter-dsl)

## I Want To Publish Diagrams Or Books

Use `book pdf` for quick diagram sheets and `book render` / `book cover` for
full book projects.

```bash
crtk book pdf --fen "<FEN>" -o dist/position.pdf
crtk book render -i books/puzzles.toml --check
crtk book render -i books/puzzles.toml -o dist/puzzles.pdf
crtk book cover -i books/puzzles.toml --pdf dist/puzzles.pdf --check --binding paperback --interior white-bw
```

Read next:

- [Book Publishing](book-publishing)
- [Example Commands](example-commands)

## I Want Agent-Friendly Chess Primitives

Prefer deterministic command shapes with explicit formats and bounded engine
work.

```bash
crtk fen normalize --fen "<FEN>"
crtk move list --fen "<FEN>" --format both
crtk move after --fen "<FEN>" e2e4
crtk engine bestmove --fen "<FEN>" --format both --max-duration 2s
```

Read next:

- [AI Agents and Automation](ai-agents)
- [Command Cheatsheet](command-cheatsheet)

## I Want To Work On ChessRTK Itself

Run focused checks for your edit area and the recommended suite before pushing.

```bash
./scripts/run_regression_suite.sh build
./scripts/run_regression_suite.sh lint
./scripts/run_regression_suite.sh recommended
```

Read next:

- [Quality and Testing](quality-and-testing)
- [Architecture](architecture)
- [Releasing](releasing)
