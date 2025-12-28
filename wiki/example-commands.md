# Example commands

Examples assume you installed the launcher (`ucicli`). If you run from classes, replace `ucicli` with `java -cp out application.Main`.

## Quick sanity checks

- `ucicli help` — show all commands + flags.
- `ucicli cuda-info` — check whether the optional CUDA JNI backend is usable.
- `ucicli print --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"` — print the starting position.
- `ucicli config show` — dump resolved config values.
- `ucicli config validate` — validate config + protocol file paths.

## Convert `.record` → `.plain` / CSV

- `ucicli record-to-plain -i data/input.record` — writes `data/input.plain`.
- `ucicli record-to-plain -i data/input.record --sidelines --csv` — also writes `data/input.csv`.
- `ucicli record-to-csv -i data/input.record -o dump/input.csv` — CSV only.
- `ucicli record-to-plain -i data/input.record -f "gate=AND;eval>=300"` — export only records matching a Filter DSL.

## Mine puzzles

- `ucicli mine --random-count 50 --output dump/` — mine 50 random seeds into timestamped outputs under `dump/`.
- `ucicli mine --input seeds/fens.txt --output dump/fens.json` — mine from a `.txt` file; writes `dump/fens.puzzles.json` + `dump/fens.nonpuzzles.json`.
- `ucicli mine --input games.pgn --output dump/pgn.json --engine-instances 4 --max-duration 60s` — mine from PGN.
- `ucicli mine --chess960 --random-count 200 --output dump/` — Chess960 random mining.

## Generate random FEN shards

- `ucicli gen-fens --output shards/ --files 2 --per-file 20 --chess960-files 1` — writes 2 shard files (first one Chess960).

## Export datasets (NumPy)

- `ucicli record-to-dataset -i dump/fens.puzzles.json -o training/pytorch/data/puzzles` — writes `puzzles.features.npy` + `puzzles.labels.npy`.
- `ucicli stack-to-dataset -i Stack-0001.json -o training/pytorch/data/stack_0001` — same tensor format from Stack dumps.

## Display a position (GUI)

- `ucicli display --fen "r1k5/2p4p/2p5/3p4/1Q4P1/1P3P2/PR3R2/1K5q w - - 1 34"` — open a window.
- `ucicli display --fen "<FEN>" --arrow e2e4 --circle e4 --legal g1` — overlays for quick inspection.
- `ucicli display --fen "<FEN>" --ablation --show-backend` — show per-piece ablation (uses LC0 if available; otherwise classical).

## Analyze positions / best move

- `ucicli analyze --fen "<FEN>" --max-duration 5s` — print PV summaries for a single position.
- `ucicli bestmove --fen "<FEN>"` — print the best move (UCI).
- `ucicli bestmove --fen "<FEN>" --san` — print the best move (SAN).

## Tags / moves

- `ucicli tags --fen "<FEN>"` — emit tags as JSON.
- `ucicli moves --fen "<FEN>" --both` — list legal moves (UCI + SAN).

## Stats

- `ucicli stats -i dump/fens.puzzles.json` — summarize a puzzle dump.
- `ucicli stats-tags -i dump/fens.puzzles.json` — summarize tag distributions.

## Perft / PGN conversion

- `ucicli perft --depth 4` — perft from the standard start position.
- `ucicli perft --fen "<FEN>" --depth 5 --divide` — per-move breakdown.
- `ucicli pgn-to-fens -i games.pgn -o seeds.txt` — extract FEN seeds from PGN.

## Eval

- `ucicli eval --fen "<FEN>"` — evaluate with LC0 (fallback to classical).
- `ucicli eval --fen "<FEN>" --classical` — force classical evaluation.

## Useful helpers

- `./scripts/fetch_lc0_net.sh --url <URL> --out nets` — download an Lc0 UCI network (kept local under `nets/`).
- `./scripts/check_no_weights_tracked.sh` — guardrail: fail if weight files are accidentally tracked by git.
