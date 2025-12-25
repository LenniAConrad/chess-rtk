# Troubleshooting

## Mining fails to start an engine

- Verify your engine binary exists and is executable (check `path = ...` in your engine protocol TOML).
- Run `ucicli help` and confirm you’re passing `--protocol-path` to the right file.
- If your engine is on `PATH`, try `which stockfish` / `which lc0`.

## Filters don’t seem to “stick”

- If a Filter DSL string is invalid, the CLI logs an error and falls back to the defaults.
- Prefer editing `config/cli.config.toml` first (then override per-run once you’re happy).

## Mining is too slow

- Lower `--max-nodes` / `--max-duration`.
- Reduce `--engine-instances`.
- Relax the quality gate (`puzzle-quality`) and/or the accelerate prefilter (`puzzle-accelerate`).

## `display --ablation` is slow or uses the classical backend

- The evaluator tries to load `models/lc0_744706.bin` and falls back to a classical heuristic when LC0 is unavailable.
- If you want CUDA acceleration, build `native-cuda/` and run with `-Djava.library.path=...`.

