# Example commands

Examples assume you installed the launcher (`crtk`). If you run from classes, replace `crtk` with `java -cp out application.Main`.

## Quick sanity checks

- `crtk help` — show all commands + flags.
- `crtk gpu-info` — check whether the optional GPU JNI backends are usable (CUDA/ROCm/oneAPI).
- `crtk print --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"` — print the starting position.
- `crtk config show` — dump resolved config values.
- `crtk config validate` — validate config + protocol file paths.

## Convert `.record` → `.plain` / CSV

- `crtk record-to-plain -i data/input.record` — writes `data/input.plain`.
- `crtk record-to-plain -i data/input.record --sidelines --csv` — also writes `data/input.csv`.
- `crtk record-to-csv -i data/input.record -o dump/input.csv` — CSV only.
- `crtk record-to-plain -i data/input.record -f "gate=AND;eval>=300"` — export only records matching a Filter DSL.
- `crtk records -i dump/ -o dump/merged.json` — merge many record files into one JSON array.
- `crtk records -i dump/ -o dump/merged.json --max-records 100000` — merge and split into 100k-sized parts.
- `crtk records -i dump/mixed.json -o dump/puzzles.json --puzzles` — keep only puzzle records.

## Mine puzzles

- `crtk mine-puzzles --random-count 50 --output dump/` — mine 50 random seeds into timestamped outputs under `dump/`.
- `crtk mine-puzzles --input seeds/fens.txt --output dump/fens.json` — mine from a `.txt` file; writes `dump/fens.puzzles.json` + `dump/fens.nonpuzzles.json`.
- `crtk mine-puzzles --input games.pgn --output dump/pgn.json --engine-instances 4 --max-duration 60s` — mine from PGN.
- `crtk mine-puzzles --chess960 --random-count 200 --output dump/` — Chess960 random mining.

## Generate random FEN shards

- `crtk gen-fens --output shards/ --files 2 --per-file 20 --chess960-files 1` — writes 2 shard files (first one Chess960).

## Export datasets (NumPy)

- `crtk record-to-dataset -i dump/fens.puzzles.json -o training/pytorch/data/puzzles` — writes `puzzles.features.npy` + `puzzles.labels.npy`.
- `crtk record-to-classifier -i dump/fens.puzzles.json -i dump/fens.nonpuzzles.json -o training/classifier/fens` — writes 21-plane classifier inputs + 0/1 labels.
- `crtk record-to-training-jsonl -i dump/fens.puzzles.json -i dump/fens.nonpuzzles.json -o training/fens.training.jsonl` — writes coarse/fine JSONL labels.
- `crtk record-analysis-delta -i dump/fens.puzzles.json -o dump/fens.analysis-delta.jsonl` — writes evaluation stability metrics.

## Publish diagrams and books

- `crtk chess-pdf --fen "<FEN>" -o dump/position.pdf` — export one diagram to PDF.
- `crtk chess-pdf -i seeds.txt -o dump/sheet.pdf --title "Training Sheet"` — export a FEN list to a diagram sheet PDF.
- `crtk chess-pdf --pgn games.pgn -o dump/games.pdf --page-size a5 --diagrams-per-row 1` — export PGN mainlines to PDF.
- `crtk chess-book -i books/puzzles.toml -o dist/puzzles.pdf` — render a book manifest to a native vector PDF.
- `crtk chess-book-cover -i books/puzzles.toml -o dist/puzzles-cover.pdf --binding paperback --interior white-bw --pages 120` — render a matching paperback cover.

## Display a position (GUI)

- `crtk display --fen "r1bqk2r/ppppbppp/3n4/4R3/8/8/PPPP1PPP/RNBQ1BK1 b kq - 2 8" --special-arrows --arrow e5e1 --legal d6` — open a window.
- `crtk display --fen "<FEN>" --arrow e2e4 --circle e4 --legal g1` — overlays for quick inspection.
- `crtk display --fen "<FEN>" --ablation --show-backend` — show per-piece ablation (uses LC0 if available; otherwise classical).
- `crtk gui-web --fen "r1bqk2r/ppppbppp/3n4/4R3/8/8/PPPP1PPP/RNBQ1BK1 b kq - 2 8" --dark` — launch the chess-web-inspired desktop GUI.

## Analyze positions / best move

- `crtk analyze --fen "<FEN>" --max-duration 5s` — print PV summaries for a single position.
- `crtk threats --fen "<FEN>" --max-duration 2s` — analyze opponent threats via a null move (MultiPV).
- `crtk bestmove --fen "<FEN>"` — print the best move (UCI).
- `crtk bestmove --fen "<FEN>" --san` — print the best move (SAN).
- `crtk bestmove-uci --fen "<FEN>"` — best move (UCI shortcut).
- `crtk bestmove-san --fen "<FEN>"` — best move (SAN shortcut).
- `crtk bestmove-both --fen "<FEN>"` — best move (UCI + SAN).

## Tags / moves

- `crtk tags --fen "<FEN>"` — emit tags as JSON.
- `crtk tags --pgn games.pgn --delta --mainline` — emit per-move tag deltas as JSONL.
- `crtk puzzle-tags --fen "<FEN>" --multipv 3 --pv-plies 12` — tag puzzle PV positions with per-move deltas.
- `crtk tag-text --fen "<FEN>" --model models/t5.bin --include-fen` — summarize position tags with T5.
- `crtk puzzle-text --fen "<FEN>" --model models/t5.bin --include-fen` — summarize a puzzle PV line with T5.
- `crtk moves --fen "<FEN>" --both` — list legal moves (UCI + SAN).
- `crtk moves-uci --fen "<FEN>"` — list legal moves (UCI shortcut).
- `crtk moves-san --fen "<FEN>"` — list legal moves (SAN shortcut).
- `crtk moves-both --fen "<FEN>"` — list legal moves (UCI + SAN shortcut).
- `crtk uci-to-san --fen "<FEN>" e2e4` — convert a single move to SAN.
- `crtk san-to-uci --fen "<FEN>" Nf3` — convert a single move to UCI.
- `crtk fen-after --fen "<FEN>" e2e4` — apply one move and print the resulting FEN.
- `crtk play-line --fen "<FEN>" e2e4 e7e5 g1f3` — apply a move sequence and print the final FEN.
- `crtk play-line --fen "<FEN>" e4 e5 Nf3 --intermediate` — apply a SAN line and print each intermediate FEN.

## Stats

- `crtk stats -i dump/fens.puzzles.json` — summarize a puzzle dump.
- `crtk stats-tags -i dump/fens.puzzles.json` — summarize tag distributions.

## Perft / PGN conversion

- `crtk perft --depth 4` — perft from the standard start position.
- `crtk perft --fen "<FEN>" --depth 5 --divide` — per-move breakdown.
- `crtk pgn-to-fens -i games.pgn -o seeds.txt` — extract FEN seeds from PGN.

## Eval

- `crtk eval --fen "<FEN>"` — evaluate with LC0 (fallback to classical).
- `crtk eval --fen "<FEN>" --classical` — force classical evaluation.
- `crtk eval-static --fen "<FEN>"` — classical evaluation shortcut.

## Useful helpers

- `./scripts/fetch_lc0_net.sh --url <URL> --out nets` — download an Lc0 UCI network (kept local under `nets/`).
- `./scripts/check_no_weights_tracked.sh` — guardrail: fail if weight files are accidentally tracked by git.
