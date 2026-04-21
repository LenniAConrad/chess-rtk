# Outputs & logs

## Output directory (`dump/`)

By default, outputs are written under `dump/` (configurable via `config/cli.config.toml` or `puzzle mine --output ...`).

`puzzle mine --output` behavior:
- directory output: `standard-<timestamp>.puzzles.json` / `standard-<timestamp>.nonpuzzles.json` (or `chess960-...`)
- file-like root (`.json` or `.jsonl`): `<stem>.puzzles.json` / `<stem>.nonpuzzles.json`

Mining outputs are single top-level JSON arrays (objects appended incrementally).

## Derived record outputs

Common converters derive output names from the input stem when `--output` is
omitted:

- `record export plain`: `<stem>.plain`
- `record export csv`: `<stem>.csv`
- `record export pgn`: `<stem>.pgn`
- `puzzle pgn`: `<stem>.pgn`
- `record analysis-delta`: `<stem>.analysis-delta.jsonl`
- `record export training-jsonl`: `<stem>.training.jsonl`

Tensor exporters write multiple files from one output stem:

- `record dataset npy`: `<stem>.features.npy`, `<stem>.labels.npy`
- `record dataset lc0`: `<stem>.lc0.inputs.npy`, `<stem>.lc0.policy.npy`, `<stem>.lc0.value.npy`, `<stem>.lc0.meta.json`
- `record dataset classifier`: `<stem>.classifier.inputs.npy`, `<stem>.classifier.labels.npy`, `<stem>.classifier.meta.json`

Publishing commands follow the same convention:

- `book pdf`: defaults to the input stem plus `.pdf` when an input path exists.
- `book render`: defaults to the manifest stem plus `.pdf`.
- `book cover`: defaults to the manifest stem plus `-cover.pdf`.

## Session logs (`session/`)

The CLI writes logs under `session/` via `chess.debug.LogService`.

To clear session artifacts:

```bash
crtk clean
```
