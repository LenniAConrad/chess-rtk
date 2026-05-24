# Outputs & logs

## Output directory (`dump/`)

By default, outputs are written under `dump/` (configurable via `config/cli.config.toml` or `puzzle mine --output ...`).

`puzzle mine --output` behavior:
- directory output: `standard-<timestamp>.puzzles.json` / `standard-<timestamp>.nonpuzzles.json` (or `chess960-...`)
- file-like root (`.json` or `.jsonl`): `<stem>.puzzles.json` / `<stem>.nonpuzzles.json`

Mining outputs are single top-level JSON arrays (objects appended incrementally).

## Derived record outputs

Common converters derive output names under `dump/` from the input stem when
`--output` is omitted:

- `record export plain`: `dump/<stem>.plain`
- `record export csv`: `dump/<stem>.csv`
- `record export pgn`: `dump/<stem>.pgn`
- `puzzle pgn`: `dump/<stem>.pgn`
- `record analysis-delta`: `dump/<stem>.analysis-delta.jsonl`
- `record export training-jsonl`: `dump/<stem>.training.jsonl`

Tensor exporters write multiple files from one output stem:

- `record dataset npy`: `dump/<stem>.features.npy`, `dump/<stem>.labels.npy`
- `record dataset lc0`: `dump/<stem>.lc0.inputs.npy`, `dump/<stem>.lc0.policy.npy`, `dump/<stem>.lc0.value.npy`, `dump/<stem>.lc0.meta.json`
- `record dataset classifier`: `dump/<stem>.classifier.inputs.npy`, `dump/<stem>.classifier.labels.npy`, `dump/<stem>.classifier.meta.json`

Publishing commands follow the same convention:

- `book pdf`: defaults to `dump/<stem>.pdf` when an input path exists, otherwise `dump/chess.pdf`.
- `book render`: defaults to `dump/<stem>.pdf`.
- `book cover`: defaults to `dump/<stem>-cover.pdf`.

## Session logs (`dump/session/`)

The CLI writes logs under `dump/session/` via `chess.debug.LogService`.

To clear session artifacts:

```bash
crtk clean
```
