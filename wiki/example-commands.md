# Example commands

Examples assume you installed the launcher (`crtk`). If you run from classes, replace `crtk` with `java -cp out application.Main`.

## Quick sanity checks

- `crtk help` — show all commands + flags.
- `crtk help --full` — show subcommands and detailed option groups.
- `crtk doctor` — check Java, config, protocol, engine discovery, and local artifact paths.
- `crtk doctor --strict` — treat setup warnings as failures.
- `crtk config validate` — validate config + protocol file paths.
- `crtk engine uci-smoke --nodes 1 --max-duration 5s` — start the configured engine and run a tiny bounded search.
- `crtk engine gpu` — check whether the optional GPU JNI backends are usable (CUDA/ROCm/oneAPI).
- `crtk engine perft-suite --depth 6 --threads 4` — validate critical stored-truth positions with the Java move generator.
- `crtk fen print --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"` — print the starting position.
- `crtk config show` — dump resolved config values.

## Convert `.record` → `.plain` / CSV

- `crtk record export plain -i data/input.record` — writes `data/input.plain`.
- `crtk record export plain -i data/input.record --sidelines --csv` — also writes `data/input.csv`.
- `crtk record export csv -i data/input.record -o dump/input.csv` — CSV only.
- `crtk record export plain -i data/input.record -f "gate=AND;eval>=300"` — export only records matching a Filter DSL.
- `crtk record files -i dump/ -o dump/merged.json` — merge many record files into one JSON array.
- `crtk record files -i dump/ -o dump/merged.json --max-records 100000` — merge and split into 100k-sized parts.
- `crtk record files -i dump/mixed.json -o dump/puzzles.json --puzzles` — keep only puzzle records.

## Mine puzzles

- `crtk puzzle mine --random-count 50 --output dump/` — mine 50 random seeds into timestamped outputs under `dump/`.
- `crtk puzzle mine --input seeds/fens.txt --output dump/fens.json` — mine from a `.txt` file; writes `dump/fens.puzzles.json` + `dump/fens.nonpuzzles.json`.
- `crtk puzzle mine --input games.pgn --output dump/pgn.json --engine-instances 4 --max-duration 60s` — mine from PGN.
- `crtk puzzle mine --chess960 --random-count 200 --output dump/` — Chess960 random mining.
- `crtk puzzle pgn -i dump/fens.puzzles.json -o dump/fens.pgn` — convert accepted puzzle records to PGN.
- `crtk record files -i dump/ -o dump/filtered.json --recursive --puzzles` — collect puzzle records from a directory tree.

## Generate random FEN shards

- `crtk fen generate --output shards/ --files 2 --per-file 20 --chess960-files 1` — writes 2 shard files (first one Chess960).
- `crtk fen chess960 518` — print the standard start position by its Chess960 index.
- `crtk fen chess960 --all --format both > chess960.tsv` — export `index`, back-rank layout, and FEN for all 960 starts.

## Export datasets (NumPy)

- `crtk record dataset npy -i dump/fens.puzzles.json -o training/pytorch/data/puzzles` — writes `puzzles.features.npy` + `puzzles.labels.npy`.
- `crtk record dataset lc0 -i dump/fens.puzzles.json -o training/lc0/puzzles --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin` — write LC0-style input, policy, value, and metadata tensors.
- `crtk record dataset classifier -i dump/fens.puzzles.json -i dump/fens.nonpuzzles.json -o training/classifier/fens` — writes 21-plane classifier inputs + 0/1 labels.
- `crtk record export training-jsonl -i dump/fens.puzzles.json -i dump/fens.nonpuzzles.json -o training/fens.training.jsonl` — writes coarse/fine JSONL labels.
- `crtk record export puzzle-jsonl -i dump/fens.puzzles.json -o training/fens.puzzle.jsonl --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin --puzzles` — write JSONL puzzle rows with LC0 policy indices.
- `crtk record analysis-delta -i dump/fens.puzzles.json -o dump/fens.analysis-delta.jsonl` — writes evaluation stability metrics.

## Publish diagrams and books

- `crtk book pdf --fen "<FEN>" -o dump/position.pdf` — export one diagram to PDF.
- `crtk book pdf -i seeds.txt -o dump/sheet.pdf --title "Training Sheet"` — export a FEN list to a diagram sheet PDF.
- `crtk book pdf --pgn games.pgn -o dump/games.pdf --page-size a5 --diagrams-per-row 1` — export PGN mainlines to PDF.
- `crtk book render -i books/puzzles.toml --check` — validate book layout, FENs, and solution lines without writing.
- `crtk book render -i books/puzzles.toml -o dist/puzzles.pdf` — render a book manifest to a native vector PDF.
- `crtk book cover -i books/puzzles.toml --pdf dist/puzzles.pdf --check --binding paperback --interior white-bw` — verify cover dimensions from the rendered interior PDF before rendering.
- `crtk book cover -i books/puzzles.toml --pdf dist/puzzles.pdf -o dist/puzzles-cover.pdf --binding paperback --interior white-bw` — render a matching paperback cover from the interior PDF geometry.

## Display a position (GUI)

- `crtk fen display --fen "r1bqk2r/ppppbppp/3n4/4R3/8/8/PPPP1PPP/RNBQ1BK1 b kq - 2 8" --special-arrows --arrow e5e1 --legal d6` — open a window.
- `crtk fen display --fen "<FEN>" --arrow e2e4 --circle e4 --legal g1` — overlays for quick inspection.
- `crtk fen display --fen "<FEN>" --ablation --show-backend` — show per-piece ablation (uses LC0 if available; otherwise classical).
- `crtk fen render --fen "<FEN>" -o dump/position.png --arrow e2e4 --circle e4 --size 1200` — save a board image.
- `crtk fen render --fen "<FEN>" -o dump/position.svg --format svg --flip` — save a vector board from Black's perspective.
- `crtk gui-web --fen "r1bqk2r/ppppbppp/3n4/4R3/8/8/PPPP1PPP/RNBQ1BK1 b kq - 2 8" --dark` — launch the chess-web-inspired desktop GUI.
- `crtk gui-next --fen "<FEN>" --dark` — launch the Studio research workbench.

## Analyze positions / best move with a UCI engine

- `crtk engine analyze --fen "<FEN>" --max-duration 5s` — print PV summaries for a single position.
- `crtk engine analyze -i positions.txt --max-nodes 1000000 --multipv 3 --threads 4 --hash 256` — analyze a FEN list with bounded UCI engine settings.
- `crtk engine threats --fen "<FEN>" --max-duration 2s` — analyze opponent threats via a null move (MultiPV).
- `crtk engine bestmove --fen "<FEN>"` — print the best move (UCI).
- `crtk engine bestmove --fen "<FEN>" --format san` — print the best move (SAN).
- `crtk engine bestmove-uci --fen "<FEN>"` — best move (UCI shortcut).
- `crtk engine bestmove-san --fen "<FEN>"` — best move (SAN shortcut).
- `crtk engine bestmove-both --fen "<FEN>"` — best move (UCI + SAN).

## Tags / moves

- `crtk fen tags --fen "<FEN>"` — emit tags as JSON.
- `crtk fen tags --fen "<FEN>" --analyze --max-duration 2s --multipv 3 --wdl` — add engine-enriched tags.
- `crtk fen tags -i positions.txt --sequence --delta --include-fen` — tag an ordered line and emit parent/child deltas.
- `crtk fen tags --pgn games.pgn --delta --mainline` — emit per-move tag deltas as JSONL.
- `crtk fen tags --pgn games.pgn --delta --sidelines` — include PGN variations in tag deltas.
- `crtk puzzle tags --fen "<FEN>" --multipv 3 --pv-plies 12` — tag puzzle PV positions with per-move deltas.
- `crtk puzzle tags --fen "<FEN>" --no-analyze --multipv 2` — tag puzzle PV positions using static tags only.
- `crtk fen text --fen "<FEN>" --model models/t5.bin --include-fen` — summarize position tags with T5.
- `crtk puzzle text --fen "<FEN>" --model models/t5.bin --include-fen` — summarize a puzzle PV line with T5.
- `crtk fen normalize --fen "<FEN>"` — parse and print ChessRTK's normalized FEN.
- `crtk fen validate --fen "<FEN>"` — print `valid` plus the normalized FEN on success.
- `crtk move list --fen "<FEN>" --format both` — list legal moves (UCI + SAN).
- `crtk move uci --fen "<FEN>"` — list legal moves (UCI shortcut).
- `crtk move san --fen "<FEN>"` — list legal moves (SAN shortcut).
- `crtk move both --fen "<FEN>"` — list legal moves (UCI + SAN shortcut).
- `crtk move to-san --fen "<FEN>" e2e4` — convert a single move to SAN.
- `crtk move to-uci --fen "<FEN>" Nf3` — convert a single move to UCI.
- `crtk move after --fen "<FEN>" e2e4` — apply one move and print the resulting FEN.
- `crtk move play --fen "<FEN>" e2e4 e7e5 g1f3` — apply a move sequence and print the final FEN.
- `crtk move play --fen "<FEN>" e4 e5 Nf3 --intermediate` — apply a SAN line and print each intermediate FEN.

## Stats

- `crtk record stats -i dump/fens.puzzles.json` — summarize a puzzle dump.
- `crtk record tag-stats -i dump/fens.puzzles.json` — summarize tag distributions.

## Perft / PGN conversion

- `crtk engine perft --depth 4` — perft from the standard start position.
- `crtk engine perft --fen "<FEN>" --depth 5` — detailed counters for nodes, captures, en-passant captures, castles, promotions, checks, and checkmates.
- `crtk engine perft --fen "<FEN>" --depth 5 --divide --threads 4` — per-root-move table with the same detailed counters.
- `crtk engine perft --depth 3 --format stockfish --threads 4` — Stockfish-style `move: nodes` divide output.
- `crtk engine perft-suite --depth 6 --threads 4` — compare critical standard, Chess960, promotion, en-passant, castling, and stress positions against stored truth values without starting an external engine.
- `crtk fen pgn -i games.pgn -o seeds.txt` — extract FEN seeds from PGN.
- `crtk fen pgn -i games.pgn -o pairs.txt --pairs --mainline` — extract parent/child FEN pairs from mainlines.

## In-process eval and built-in search

- `crtk engine eval --fen "<FEN>"` — evaluate with the Java LC0 evaluator and classical fallback.
- `crtk engine eval --fen "<FEN>" --lc0 --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin` — force Java LC0 evaluation.
- `crtk engine eval --fen "<FEN>" --classical` — force classical evaluation.
- `crtk engine static --fen "<FEN>"` — classical evaluation shortcut.
- `crtk engine builtin --fen "<FEN>" --depth 4 --format summary` — search with the built-in Java engine.
- `crtk engine builtin --fen "<FEN>" --classical --depth 6 --nodes 250000 --format both` — bounded classical built-in search.
- `crtk engine builtin --fen "<FEN>" --evaluator nnue --weights models/crtk-halfkp.nnue --depth 3 --format both` — use the Java NNUE evaluator at the search frontier.
- `crtk engine builtin --fen "<FEN>" --lc0 --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin --depth 2 --format summary` — use the Java LC0 value evaluator at the search frontier.

## Useful helpers

- `./scripts/fetch_lc0_net.sh --url <URL> --out nets` — download an Lc0 UCI network (kept local under `nets/`).
- `./scripts/check_no_weights_tracked.sh` — guardrail: fail if weight files are accidentally tracked by git.
