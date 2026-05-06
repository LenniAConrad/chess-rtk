# Command Cheatsheet

This page is the shortest path from "what do I want?" to the command to run.
For complete options, use `crtk help --full` or open
[Command Reference](command-reference).

## One-Minute Demo

```bash
crtk doctor
crtk fen print --startpos
crtk move list --startpos --format both
crtk engine builtin --startpos --depth 3 --format summary
```

## Setup And Health

| Task | Command |
| --- | --- |
| Show top-level help | `crtk help` |
| Show complete help | `crtk help --full` |
| Show version metadata | `crtk version --json` |
| Check local setup | `crtk doctor` |
| Validate config | `crtk config validate` |
| Smoke-test UCI engine | `crtk engine uci-smoke --nodes 1 --max-duration 5s` |
| Run recommended checks | `./scripts/run_regression_suite.sh recommended` |

## FEN And Board Inspection

| Task | Command |
| --- | --- |
| Print the start position | `crtk fen print --startpos` |
| Validate a FEN | `crtk fen validate --fen "<FEN>"` |
| Normalize a FEN | `crtk fen normalize --fen "<FEN>"` |
| Validate a FEN as JSON | `crtk fen validate --fen "<FEN>" --json` |
| Generate Chess960 start | `crtk fen chess960 518` |
| Generate random FEN shards | `crtk fen generate --output shards/ --files 2 --per-file 20 --chess960-files 1` |
| Generate endgame shards | `crtk gen fens --output endgames/ --files 1 --per-file 100 --endgame` |
| Generate special-move shards | `crtk gen fens --output specials/ --files 1 --per-file 25 --en-passant --max-attempts 250000` |
| Render a PNG board | `crtk fen render --fen "<FEN>" -o board.png` |
| Open a board window | `crtk fen display --fen "<FEN>"` |
| Compare two positions | `crtk position diff --fen "<FEN>" --other "<FEN>" --json` |

## Moves

| Task | Command |
| --- | --- |
| List legal moves as UCI | `crtk move list --fen "<FEN>" --format uci` |
| List legal moves as SAN | `crtk move list --fen "<FEN>" --format san` |
| List both UCI and SAN | `crtk move list --fen "<FEN>" --format both` |
| List legal moves as JSONL | `crtk move list --fen "<FEN>" --jsonl` |
| Convert UCI to SAN | `crtk move to-san --fen "<FEN>" e2e4` |
| Convert SAN to UCI | `crtk move to-uci --fen "<FEN>" Nf3` |
| Apply one move | `crtk move after --fen "<FEN>" e2e4` |
| Apply a line | `crtk move play --fen "<FEN>" "e4 e5 Nf3 Nc6"` |

## Engine Work

| Task | Command |
| --- | --- |
| Best move with UCI engine | `crtk engine bestmove --fen "<FEN>" --format both --max-duration 2s` |
| Analyze with UCI engine | `crtk engine analyze --fen "<FEN>" --multipv 3 --max-duration 5s` |
| Batch best moves | `crtk engine bestmove-batch --input positions.txt --max-duration 1s` |
| Batch analysis JSONL | `crtk engine analyze-batch --input positions.txt --multipv 3 --jsonl` |
| Compare two UCI protocols | `crtk engine compare --input positions.txt --left-protocol a.toml --right-protocol b.toml` |
| Search in-process | `crtk engine builtin --fen "<FEN>" --depth 4 --format summary` |
| Benchmark movegen | `crtk engine benchmark --startpos --depth 5 --iterations 5` |
| Classical static eval | `crtk engine static --fen "<FEN>"` |
| Java evaluator eval | `crtk engine eval --fen "<FEN>"` |
| Threat analysis | `crtk engine threats --fen "<FEN>" --max-duration 2s` |

## Move-Generation Verification

| Task | Command |
| --- | --- |
| Perft from startpos | `crtk engine perft --startpos --depth 4 --threads 4` |
| Perft one FEN | `crtk engine perft --fen "<FEN>" --depth 5 --divide --threads 4` |
| Stockfish-style divide | `crtk engine perft --depth 3 --format stockfish` |
| Regression suite | `crtk engine perft-suite --depth 6 --threads 4` |
| Custom perft suite | `crtk engine perft-suite --suite custom-perft.tsv --threads 4` |

## Puzzle Mining

| Task | Command |
| --- | --- |
| Extract FENs from PGN | `crtk fen pgn --input games.pgn --output seeds.txt` |
| Mine from FENs | `crtk puzzle mine --input seeds.txt --output dump/run.json --engine-instances 4` |
| Mine random positions | `crtk puzzle mine --random-count 100 --output dump/` |
| Convert puzzles to PGN | `crtk puzzle pgn -i dump/run.puzzles.json -o dump/run.pgn` |
| Filter record files | `crtk record files -i dump/ -o dump/filtered.json --recursive --puzzles` |

## Datasets

| Task | Command |
| --- | --- |
| Export NPY tensors | `crtk record dataset npy -i dump/run.puzzles.json -o training/run` |
| Export LC0 tensors | `crtk record dataset lc0 -i dump/run.puzzles.json -o training/lc0/run --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin` |
| Export classifier tensors | `crtk record dataset classifier -i dump/run.puzzles.json -i dump/run.nonpuzzles.json -o training/classifier/run` |
| Export training JSONL | `crtk record export training-jsonl -i dump/run.puzzles.json -o training/run.jsonl` |
| Summarize records | `crtk record stats -i dump/run.puzzles.json` |
| Tag distribution | `crtk record tag-stats -i dump/run.puzzles.json` |

## Publishing

| Task | Command |
| --- | --- |
| Single diagram PDF | `crtk book pdf --fen "<FEN>" -o dist/position.pdf` |
| Diagram sheet | `crtk book pdf -i seeds.txt -o dist/sheet.pdf --title "Training Sheet"` |
| Validate book | `crtk book render -i books/puzzles.toml --check` |
| Render book | `crtk book render -i books/puzzles.toml -o dist/puzzles.pdf` |
| Validate cover | `crtk book cover -i books/puzzles.toml --pdf dist/puzzles.pdf --check --binding paperback --interior white-bw` |
| Render cover | `crtk book cover -i books/puzzles.toml --pdf dist/puzzles.pdf -o dist/puzzles-cover.pdf --binding paperback --interior white-bw` |

## Desktop Workbench

| Task | Command |
| --- | --- |
| Open focused analysis workbench | `crtk gui-workbench` |
| Open workbench on a position | `crtk workbench --fen "<FEN>"` |

## Agent-Friendly Commands

| Need | Command |
| --- | --- |
| Deterministic legal moves | `crtk move list --fen "<FEN>" --format both` or `--jsonl` |
| Normalize input | `crtk fen normalize --fen "<FEN>"` |
| Apply a line safely | `crtk move play --fen "<FEN>" "e4 e5 Nf3"` |
| One best move row | `crtk engine bestmove --fen "<FEN>" --format both --max-duration 2s` |
| Best move batch rows | `crtk engine bestmove-batch --input positions.txt --max-duration 1s` |
| Position differences | `crtk position diff --fen "<FEN>" --other "<FEN>" --json` |
| In-process fallback | `crtk engine builtin --fen "<FEN>" --depth 3 --format summary` |
