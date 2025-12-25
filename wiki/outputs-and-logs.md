# Outputs & logs

## Output directory (`dump/`)

By default, outputs are written under `dump/` (configurable via `config/cli.config.toml` or `mine --output ...`).

`mine --output` behavior:
- directory output: `standard-<timestamp>.puzzles.json` / `standard-<timestamp>.nonpuzzles.json` (or `chess960-...`)
- file-like root (`.json` or `.jsonl`): `<stem>.puzzles.json` / `<stem>.nonpuzzles.json`

Mining outputs are single top-level JSON arrays (objects appended incrementally).

## Session logs (`session/`)

The CLI writes logs under `session/` via `chess.debug.LogService`.

To clear session artifacts:

```bash
ucicli clean
```
