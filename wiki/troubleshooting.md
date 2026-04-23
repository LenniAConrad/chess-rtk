# Troubleshooting

## Mining fails to start an engine

- Verify your engine binary exists and is executable (check `path = ...` in your engine protocol TOML).
- Run `crtk help` and confirm you’re passing `--protocol-path` to the right file.
- If your engine is on `PATH`, try `which stockfish` / `which lc0`.
- Commands such as `move list`, `engine perft`, `engine perft-suite`,
  `engine static`, and `engine builtin --classical` do not start a UCI engine;
  use them to isolate whether a failure is in the chess core or in external
  engine configuration.

## Perft validation fails

- `engine perft-suite` compares stored truth values against the Java core move
  generator. It does not call Stockfish or any other external process.
- Re-run a failing row directly with `engine perft --fen "<FEN>" --depth <n>
  --divide` to inspect per-root-move counts.
- Recent edits to FEN parsing, Chess960 castling metadata, en-passant handling,
  SAN move application, make/undo, or attack detection can all affect perft.

## Filters don’t seem to “stick”

- If a Filter DSL string is invalid, the CLI logs an error and falls back to the defaults.
- Prefer editing `config/cli.config.toml` first (then override per-run once you’re happy).

## Mining is too slow

- Lower `--max-nodes` / `--max-duration`.
- Reduce `--engine-instances`.
- Relax the quality gate (`puzzle-quality`) and/or the accelerate prefilter (`puzzle-accelerate`).

## `fen display --ablation` is slow or uses the classical backend

- The evaluator tries to load local `models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin` weights and falls back to a classical heuristic when LC0 is unavailable. Fetch the default weights with `./install.sh --models`.
- If you want native GPU acceleration, build the matching backend under
  `native/cuda/`, `native/rocm/`, or `native/oneapi/` and run with
  `-Djava.library.path=...`.

## Cover dimensions do not match the upload form

- Pass the rendered interior PDF to `book cover --pdf <interior.pdf>` so trim size and page count come from the actual file.
- Use `book cover --pages <n>` only when you need to override the PDF-derived spine count.
- Use a current interior token: `white-bw`, `cream-bw`, `white-standard-color`, or `white-premium-color`.
- Compare the dimensions printed by `book cover` with the publishing service's own cover calculator before upload.

## T5 commands cannot find a model

- Pass `--model /path/to/t5.bin`, or set `t5-model-path` in `config/cli.config.toml`.
- Model binaries are local artifacts and are ignored by git; they are not bundled with a fresh clone.
