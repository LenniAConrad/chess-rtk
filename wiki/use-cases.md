# Use Cases

Five very different jobs run on one set of rules here. Analyzing a position, mining tactics, building training tensors, publishing a book, scripting an agent — all of them reach through the same legal move generator, the same make/undo, the same FEN/SAN/UCI/Chess960 layer. Nothing forks its own slightly-wrong notion of a legal move. That is the whole point, and it's why the output is reproducible no matter which door you come in through. The page is sorted by who you are: find the persona that fits, take the recipe, follow the links when you want the depth.

![ChessRTK pipeline overview](../assets/diagrams/crtk-pipeline-overview.png)

Every command reads as `crtk <area> <action> [options]`, noun then verb, and leans on explicit flags (`--fen`, `--input`, `--output`, `--format`) rather than positional guesswork — which is what keeps a script stable when you come back to it months later. The `fen`, `move`, and `position` primitives run in-process and ask for no engine at all. The `engine` area is where Stockfish or LC0 plug in, alongside a built-in searcher and a forced-mate prover.

## Personas At A Glance

| You are… | You want to… | Start with |
| --- | --- | --- |
| Chess researcher | Study positions, validate move generation, compare engines | `fen print`, `engine analyze`, `engine perft` |
| ML dataset builder | Turn games and analysis into labeled tensors and JSONL | `fen pgn`, `puzzle mine`, `record dataset` |
| Player reviewing games | Turn your PGNs into mistake rows and study units | `review game --to-study` |
| Puzzle-book author | Mine tactics and publish print-ready PDFs | `puzzle mine`, `book collection`, `book render` |
| Engine tester | Benchmark movegen, run perft suites, A/B engines | `engine perft-suite`, `engine compare`, `engine benchmark` |
| AI-agent / automation builder | Get clean JSON/JSONL from deterministic primitives | `fen normalize`, `move list`, `engine bestmove-batch` |

## Chess Researchers

Start with the primitives — they tell you what's legal, what a move does, what the position is — and reach for search only when you actually need it. The `fen`, `move`, and `position` commands run entirely in-process and need no configured engine, so they answer instantly and identically every time. When you want depth, the `engine` area adds UCI analysis, the built-in searcher, and a choice of evaluators: classical/static, NNUE, LC0 CNN, OTIS.

```bash
crtk fen print --fen "<FEN>"
crtk move list --fen "<FEN>" --format both
crtk engine analyze --fen "<FEN>" --multipv 3 --max-duration 5s
crtk engine eval --fen "<FEN>" --evaluator lc0 --weights <WEIGHTS>
crtk position describe --fen "<FEN>" --detail full
```

> A word on neural fidelity: the LC0/OTIS evaluators are honest research evaluators, not bit-exact LC0 reproductions. The BT4 path in particular is simplified and still experimental, so read its numbers as approximate rather than authoritative.

Read next:

- [Getting Started](getting-started.md)
- [Command Cheatsheet](command-cheatsheet.md)
- [In-House Engine](in-house-engine.md)
- [LC0 Networks](lc0.md)

## ML Dataset Builders

Raw PGN goes in one end, labeled tensors come out the other, and you never have to leave the toolkit to stitch the steps together. Pull FENs from games, enrich them with bounded engine analysis or puzzle mining if you want labels, then export either deterministic tensors (`record dataset npy|lc0|classifier`) or labeled JSONL rows (`record export training-jsonl`, `puzzle-jsonl`, `puzzle-elo-jsonl`). The reproducibility is not incidental: because one core normalizes every FEN, a dataset built today and the same dataset rebuilt next month are byte-for-byte identical.

```bash
crtk fen pgn --input games.pgn --output seeds/positions.txt
crtk fen generate --output shards/ --files 4 --per-file 1000 --endgame
crtk record files -i dump/ -o dump/merged.json --recursive --puzzles
crtk record dataset npy -i dump/merged.json -o training/run
crtk record export training-jsonl -i dump/merged.json -o training/labels.jsonl --filter "<DSL>"
```

Read next:

- [Datasets](datasets.md)
- [Review to Study](review-to-study.md)
- [Filter DSL](filter-dsl.md)
- [Piece and Position Tags](piece-tags.md)
- [Outputs and Logs](outputs-and-logs.md)

## Players Reviewing Their Games

Export a PGN, run bounded review with your configured engine, and let crtk write both the per-ply verdict stream and drillable study units. The review JSONL tells you what happened on every mainline ply; the study JSONL and Record sidecar keep only mistakes and blunders that should become practice material.

```bash
crtk review game --pgn games/rapid.pgn --max-nodes 50000 --max-duration 3s --to-study
```

Read next:

- [Review to Study](review-to-study.md)
- [Outputs and Logs](outputs-and-logs.md)
- [Configuration](configuration.md)
- [Desktop Workbench](workbench.md)

## Puzzle-Book Authors

Your own games are full of tactics you missed. This path digs them out, verifies and tags them, and hands you a finished PDF — interior and cover both — without a LaTeX toolchain anywhere in sight. `puzzle mine` runs bounded UCI analysis over FEN or PGN seeds and sorts the results through a Filter DSL: the keepers land in `*.puzzles.json`, the rest in `*.nonpuzzles.json` so you can audit what got rejected and why. `book collection` turns the keepers into a print-ready manifest and PDF; `book cover` produces a matching cover sized to the binding you chose.

```bash
crtk fen pgn --input games.pgn --output seeds.txt
crtk puzzle mine --input seeds.txt --output dump/run.json --engine-instances 4 --max-duration 60s
crtk book collection -i dump/run.puzzles.json --pdf-output dist/puzzles.pdf --title "My Puzzle Book"
crtk book cover -i dump/run.puzzles.json --pdf dist/puzzles.pdf --binding paperback --interior white-bw
crtk book render -i books/puzzles.toml --check
```

Read next:

- [Book Publishing](book-publishing.md)
- [Mining Puzzles](mining.md)
- [Filter DSL](filter-dsl.md)
- [Tag Reference](tag-reference.md)

## Engine Testers

A move generator is either correct or it isn't, and perft is how you settle the argument. `engine perft` and `engine perft-suite` walk the shared generator directly — Chess960 castling and en-passant corners included, the places where most implementations quietly go wrong — with native GPU acceleration when it's available and a CPU fallback when it isn't. `engine compare` pits two UCI protocols against the same positions, `engine benchmark` measures raw movegen throughput, and `engine mate` proves forced mates by brute force, no neural net involved and no chance of a hallucinated win.

```bash
crtk engine perft --startpos --depth 5 --divide --threads 4
crtk engine perft-suite --depth 6 --threads 4
crtk engine perft-suite --depth 6 --gpu --split 4
crtk engine compare --input positions.txt --left-protocol stockfish.toml --right-protocol lc0.toml --max-duration 2s
crtk engine mate --fen "<FEN>" --max-mate 4 --format san
crtk engine gpu
```

Read next:

- [Quality and Testing](quality-and-testing.md)
- [Architecture](architecture.md)
- [Development Notes](development-notes.md)
- [In-House Engine](in-house-engine.md)

## AI-Agent And Automation Builders

An agent driving `crtk` wants two things a human can shrug off: machine-readable output and a guarantee the call will end. So pass explicit `--format`, take `--json` / `--jsonl` from the primitives, and put a leash on every engine call with `--max-duration` or `--max-nodes` — an unbounded search is a hung agent. When throughput matters, the batch commands (`engine analyze-batch`, `engine bestmove-batch`, `batch run`) chew through many FENs or commands in one invocation instead of paying startup cost per call. And since identical inputs yield identical output, you can cache aggressively and verify cheaply.

```bash
crtk fen normalize --fen "<FEN>" --json
crtk move list --fen "<FEN>" --format both --jsonl
crtk move after --fen "<FEN>" e2e4 --json
crtk engine bestmove --fen "<FEN>" --format both --max-duration 2s
crtk engine bestmove-batch --input fens.txt --output out.jsonl --max-nodes 100000
```

For multi-step workflows, hand `batch run` a script with one `crtk` command per line:

```bash
crtk batch run --input pipeline.txt --keep-going
```

Read next:

- [AI Agents and Automation](ai-agents.md)
- [Command Reference](command-reference.md)
- [Command Cheatsheet](command-cheatsheet.md)
- [Example Commands](example-commands.md)

## Prefer A Desktop UI?

Same core, with a window around it. The native Swing **Workbench** gives you board editing, play-vs-engine, command forms, batch jobs, datasets, logs, publishing previews, puzzle browsing, and neural-network visualizers — the CLI's behavior, made clickable for the times you'd rather see than type.

```bash
crtk workbench
crtk workbench --fen "<FEN>"
```

Read next:

- [Desktop Workbench](workbench.md)
- [Getting Started](getting-started.md)

## Setup And Health Checks

Five minutes spent here saves an afternoon of confusing failures later. `crtk doctor` checks the runtime, your config, the engine protocols, and the local artifacts, then tells you what's missing before a command does it for you obliquely. `crtk config show` prints the settings as actually resolved — which is the version that matters when something disagrees with what you thought you set.

```bash
crtk doctor
crtk config show
crtk config validate
crtk version
```

Read next:

- [Build and Install](build-and-install.md)
- [Configuration](configuration.md)
- [Troubleshooting](troubleshooting.md)
- [FAQ](faq.md)
