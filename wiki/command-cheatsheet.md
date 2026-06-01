# Command Cheatsheet

Every command here is one line you can paste, edit, and run. They share a single noun-then-verb shape (`crtk <area> <action>`) and a single chess core, so the same input gives the same output every time. Replace `"<FEN>"` with a real position, and keep the quotes — FENs contain spaces. When you need the full option list for any command, `crtk help <area> <action>` has it (try `crtk help engine bestmove`), as does the [Command Reference](command-reference.md).

> On a fresh checkout, run `crtk doctor` first. It tells you what works before you waste time on what doesn't.

## 60-Second Smoke Test

```bash
crtk doctor                                         # check Java, config, engines, artifacts
crtk version --json                                 # build metadata
crtk fen print --startpos                           # render the start position as text
crtk move list --startpos --format both             # legal moves in UCI + SAN
crtk engine builtin --startpos --depth 3 --format summary   # in-process MCTS search
crtk engine perft-suite --depth 5                   # movegen correctness regression
```

## Setup And Diagnostics

| Task | Command |
| --- | --- |
| Top-level help | `crtk help` |
| Full help (every command) | `crtk help --full` |
| Help for one command | `crtk help engine bestmove` |
| Version metadata as JSON | `crtk version --json` |
| Health check | `crtk doctor` |
| Health check, fail on warnings | `crtk doctor --strict` |
| Show config values | `crtk config show` |
| Validate config file | `crtk config validate` |
| GPU backend status | `crtk engine gpu` |
| Smoke-test a UCI engine | `crtk engine uci-smoke --nodes 1 --max-duration 5s` |
| Delete session cache and logs | `crtk clean` |

## FEN And Board Inspection

| Task | Command |
| --- | --- |
| Pretty-print start position | `crtk fen print --startpos` |
| Pretty-print any FEN | `crtk fen print --fen "<FEN>"` |
| Validate a FEN | `crtk fen validate --fen "<FEN>"` |
| Validate as JSON | `crtk fen validate --fen "<FEN>" --json` |
| Validate many FENs from stdin | `crtk fen validate --stdin --jsonl` |
| Normalize a FEN | `crtk fen normalize --fen "<FEN>"` |
| Chess960 start by index | `crtk fen chess960 518` |
| One random Chess960 start | `crtk fen chess960 --random` |
| Compare two positions | `crtk position diff --fen "<FEN>" --other "<FEN>" --json` |
| Deterministic prose description | `crtk position describe --fen "<FEN>" --detail full` |
| Render a board image | `crtk fen render --fen "<FEN>" -o board.svg` |
| Render with arrows + dark theme | `crtk fen render --fen "<FEN>" -o board.svg --arrows e2e4,d2d4 --dark` |
| Open a board window | `crtk fen display --fen "<FEN>"` |

## Tags And Natural Language

Tags name what a position contains — tactics, themes, structural features — and they come straight from the chess core. The optional T5 path turns those facts into prose.

| Task | Command |
| --- | --- |
| Tag one FEN | `crtk fen tags --fen "<FEN>" --include-fen` |
| Tag a PGN (with variations) | `crtk fen tags --pgn games.pgn --sidelines` |
| Per-move tag deltas as JSONL | `crtk fen tags --input line.txt --sequence --delta` |
| T5 summary of a position | `crtk fen text --fen "<FEN>"` |
| Tag a puzzle line | `crtk puzzle tags --fen "<FEN>" --multipv 3 --pv-plies 12` |
| T5 narration of a puzzle | `crtk puzzle text --fen "<FEN>" --include-fen` |

> The text comes from the bundled simplified T5 path. The tags do not — they are derived from the position, never sampled, so they stay stable across runs.

## Moves

| Task | Command |
| --- | --- |
| Legal moves as UCI | `crtk move list --fen "<FEN>" --format uci` |
| Legal moves as SAN | `crtk move list --fen "<FEN>" --format san` |
| Legal moves as UCI + SAN | `crtk move list --fen "<FEN>" --format both` |
| Legal moves as JSONL | `crtk move list --fen "<FEN>" --jsonl` |
| Convert UCI to SAN | `crtk move to-san --fen "<FEN>" e2e4` |
| Convert SAN to UCI | `crtk move to-uci --fen "<FEN>" Nf3` |
| Apply one move, print FEN | `crtk move after --fen "<FEN>" e2e4` |
| Apply a move line, print FEN | `crtk move play --fen "<FEN>" "e4 e5 Nf3 Nc6"` |
| Apply a line, FEN after each ply | `crtk move play --fen "<FEN>" "e4 e5" --intermediate` |

> `crtk fen after` and `crtk fen line` are aliases for `move after` and `move play` — same behavior, whichever noun you reach for.

## Engine Analysis And Search

Three kinds of engine live here, and the verb tells you which. `engine analyze`/`bestmove`/`threats`/`compare` talk to an external UCI engine — Stockfish, LC0. `engine builtin`/`java` run the in-process MCTS engine, no external process required. `engine mate` proves forced mates by brute force, and `engine static`/`eval` just score a position.

| Task | Command |
| --- | --- |
| Best move (UCI + SAN) | `crtk engine bestmove --fen "<FEN>" --format both --max-duration 2s` |
| Best move, UCI only | `crtk engine bestmove-uci --fen "<FEN>" --max-duration 2s` |
| Analyze with MultiPV | `crtk engine analyze --fen "<FEN>" --multipv 3 --max-duration 5s` |
| Opponent threat analysis | `crtk engine threats --fen "<FEN>" --max-duration 2s` |
| Batch best moves (JSONL) | `crtk engine bestmove-batch --input positions.txt --max-duration 1s` |
| Batch analysis (JSONL) | `crtk engine analyze-batch --input positions.txt --multipv 3 --jsonl` |
| Compare two UCI protocols | `crtk engine compare --input positions.txt --left-protocol a.toml --right-protocol b.toml --jsonl` |
| In-process MCTS search | `crtk engine builtin --fen "<FEN>" --depth 4 --format summary` |
| MCTS with LC0 evaluator | `crtk engine builtin --fen "<FEN>" --lc0 --weights models/<weights>.bin --max-duration 3s` |
| Prove a forced mate | `crtk engine mate --fen "<FEN>" --max-mate 4 --format both` |
| Classical static eval | `crtk engine static --fen "<FEN>"` |
| Evaluate (auto/lc0/otis) | `crtk engine eval --fen "<FEN>" --evaluator auto` |

> A protocol TOML file tells crtk how to launch and talk to each UCI engine. Pass `--protocol-path engine.toml` to override the configured default for one run.

## Movegen Verification (Perft)

| Task | Command |
| --- | --- |
| Perft from start | `crtk engine perft --startpos --depth 5 --threads 4` |
| Perft a FEN, divided | `crtk engine perft --fen "<FEN>" --depth 5 --divide --threads 4` |
| Stockfish-style divide | `crtk engine perft --fen "<FEN>" --depth 3 --format stockfish` |
| GPU perft, divided | `crtk engine perft --startpos --depth 6 --gpu --divide --split 3` |
| Built-in regression suite | `crtk engine perft-suite --depth 6 --threads 4` |
| GPU regression suite | `crtk engine perft-suite --depth 6 --gpu --split 4` |
| Custom suite file | `crtk engine perft-suite --suite custom-perft.tsv --threads 4` |
| Benchmark the move generator | `crtk engine benchmark --startpos --depth 5 --iterations 5` |

> The native backends (CUDA/ROCm/oneAPI) accelerate perft and OTIS. `--gpu` is safe to leave on: with no backend present it falls back to the CPU rather than failing. `crtk engine gpu` shows you what's actually available.

## Generating Positions

| Task | Command |
| --- | --- |
| Random legal FEN shards | `crtk fen generate --output shards/ --files 2 --per-file 20 --chess960-files 1` |
| Endgame shards | `crtk gen fens --output endgames/ --files 1 --per-file 100 --endgame` |
| En-passant positions | `crtk gen fens --output ep/ --files 1 --per-file 25 --en-passant --max-attempts 250000` |
| Positions by side / check | `crtk gen fens --output out/ --files 1 --per-file 50 --side white --in-check` |
| Extract FENs from a PGN | `crtk fen pgn --input games.pgn --output seeds.txt` |
| Parent/child FEN pairs | `crtk fen pgn --input games.pgn --output pairs.txt --pairs` |

> `gen fens` is an alias for `fen generate`. Filters combine with AND, so each one you add narrows the output further. The full set — presets, move-state, count, and material — is in `crtk help fen generate`.

## Puzzle Mining

`puzzle mine` hunts for tactical lines, then runs each candidate through a Filter DSL that decides what counts as a puzzle. The keepers land in `<root>.puzzles.json`; everything rejected goes to `<root>.nonpuzzles.json`, which is worth keeping — it doubles as a negative training set.

| Task | Command |
| --- | --- |
| Mine from seed FENs | `crtk puzzle mine --input seeds.txt --output dump/run.json --engine-instances 4` |
| Mine from PGN games | `crtk puzzle mine --input games.pgn --output dump/ --max-duration 5s` |
| Mine random positions | `crtk puzzle mine --random-count 100 --output dump/` |
| Mine Chess960 puzzles | `crtk puzzle mine --random-count 100 --chess960 --output dump/c960/` |
| Convert puzzle dump to PGN | `crtk puzzle pgn -i dump/run.puzzles.json -o dump/run.pgn` |
| Merge/filter record files | `crtk record files -i dump/ -o dump/filtered.json --recursive --puzzles` |
| Split records into chunks | `crtk record files -i dump/run.puzzles.json -o dump/parts/ --max-records 500` |

## Records, Stats, And Export

| Task | Command |
| --- | --- |
| Summarize a record file | `crtk record stats -i dump/run.puzzles.json` |
| Tag distribution | `crtk record tag-stats -i dump/run.puzzles.json` |
| Parent/child analysis delta | `crtk record analysis-delta -i dump/run.puzzles.json` |
| Export to plain text | `crtk record export plain -i dump/run.puzzles.json -o dump/run.plain` |
| Export to CSV | `crtk record export csv -i dump/run.puzzles.json -o dump/run.csv` |
| Export to PGN | `crtk record export pgn -i dump/run.puzzles.json -o dump/run.pgn` |
| Verified puzzles as JSONL | `crtk record export puzzle-jsonl -i dump/run.puzzles.json --puzzles -o dump/run.puzzle.jsonl` |
| Puzzles with Elo + tags | `crtk record export puzzle-elo-jsonl -i dump/run.puzzles.json -o dump/run.puzzle-elo.jsonl` |
| Labeled training JSONL | `crtk record export training-jsonl -i dump/run.puzzles.json -o training/run.jsonl` |

## ML Dataset Export

| Task | Command |
| --- | --- |
| NPY tensors | `crtk record dataset npy -i dump/run.puzzles.json -o training/run` |
| LC0 tensors | `crtk record dataset lc0 -i dump/run.puzzles.json -o training/lc0/run --weights models/<weights>.bin` |
| Classifier tensors (pos + neg) | `crtk record dataset classifier -i dump/run.puzzles.json -i dump/run.nonpuzzles.json -o training/classifier/run` |
| Classifier with caps | `crtk record dataset classifier -i dump/ --recursive -o training/clf --max-positives 5000 --max-negatives 5000` |

## Publishing (Native PDF, No LaTeX)

| Task | Command |
| --- | --- |
| Single diagram PDF | `crtk book pdf --fen "<FEN>" -o dist/position.pdf` |
| Diagram sheet from a list | `crtk book pdf -i seeds.txt -o dist/sheet.pdf --title "Training Sheet"` |
| Validate a book manifest | `crtk book render -i books/puzzles.toml --check` |
| Render a book to PDF | `crtk book render -i books/puzzles.toml -o dist/puzzles.pdf` |
| Build a dense collection | `crtk book collection -i dump/run.puzzles.json -o dist/collection.toml --pdf-output dist/collection.pdf` |
| Render an annotated study | `crtk book study -i studies/study.toml -o dist/study.pdf` |
| Render a cover | `crtk book cover -i books/puzzles.toml --pdf dist/puzzles.pdf -o dist/cover.pdf --binding paperback --interior white-bw` |

## Batch Runner

| Task | Command |
| --- | --- |
| Run a script of crtk commands | `crtk batch run --input jobs.txt` |
| Read script from stdin | `crtk batch run --stdin --keep-going` |

> Each non-comment line is one full `crtk` command. By default a failing line stops the run; `--keep-going` pushes past it and reports the failures at the end.

## Desktop Workbench

The Swing Workbench is the CLI with a face on it: board play, command forms, batch jobs, dataset tooling, logs, publishing previews, puzzles, and the neural-network visualizers — all driving the same core.

| Task | Command |
| --- | --- |
| Launch the workbench | `crtk workbench` |
| Launch on a position | `crtk workbench --fen "<FEN>"` |
| Launch flipped (Black down) | `crtk workbench --fen "<FEN>" --black-down` |
| Use the alias | `crtk gui` |

## Agent-Friendly One-Liners

When something downstream has to parse the output, reach for these: structured, stable, and free of anything an LLM or a script would have to guess at.

| Need | Command |
| --- | --- |
| Legal moves, structured | `crtk move list --fen "<FEN>" --format both --jsonl` |
| Normalize untrusted input | `crtk fen normalize --fen "<FEN>" --json` |
| Apply a line safely | `crtk move play --fen "<FEN>" "e4 e5 Nf3" --json` |
| One best-move row | `crtk engine bestmove --fen "<FEN>" --format both --max-duration 2s` |
| Best-move batch | `crtk engine bestmove-batch --input positions.txt --max-duration 1s` |
| Position differences | `crtk position diff --fen "<FEN>" --other "<FEN>" --json` |
| Offline in-process fallback | `crtk engine builtin --fen "<FEN>" --depth 3 --format summary` |

## See Also

- [Command Reference](command-reference.md) — full option-by-option documentation.
- [Getting Started](getting-started.md) — install, build, and first runs.
- [Example Commands](example-commands.md) — worked end-to-end recipes.
- [Configuration](configuration.md) — config files and engine protocols.
