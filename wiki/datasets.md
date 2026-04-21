# Datasets

This repo can export training tensors from mined or imported analysis dumps.

## From `.record` JSON: `record dataset npy`

Input: a `.record` JSON array (see `chess.struct.Record`).

Output (NumPy `.npy`, float32):
- `<stem>.features.npy` shaped `(N, 781)`
- `<stem>.labels.npy` shaped `(N,)` (evaluation in pawns, clamped to `[-20, +20]`)

Example:

```bash
crtk record dataset npy -i dump/run.puzzles.json -o training/pytorch/data/puzzles
```

## From `.record` JSON: `record dataset lc0`

Exports LC0-style inputs/policy/value tensors:

- `<stem>.lc0.inputs.npy` shaped `(N, 112*64)` float32
- `<stem>.lc0.policy.npy` shaped `(N, policySize)` float32 (one-hot)
- `<stem>.lc0.value.npy` shaped `(N,)` float32 (scalar in `[-1,1]`)
- `<stem>.lc0.meta.json` metadata (policy encoding, compression, value scale)

If `--weights` is provided, the policy is compressed to the LC0 network's policy size using the weights' policy map.
Otherwise, the raw 73-plane policy size (4672) is used.
Weights are local artifacts; fetch the default LC0J weights with
`./install.sh --models` before using the example path below.

Example:

```bash
crtk record dataset lc0 -i dump/run.puzzles.json -o training/lc0/puzzles --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin
```

## For binary classification: `record dataset classifier`

Exports inputs and binary labels for the `chess.nn.classifier` one-logit
model. With mined puzzle/non-puzzle dumps, the positive class is a puzzle:

- `<stem>.classifier.inputs.npy` shaped `(N, 21*64)` float32
- `<stem>.classifier.labels.npy` shaped `(N,)` float32 (`0.0` negative, `1.0` positive)
- `<stem>.classifier.meta.json` metadata (sources, filters, class counts, pos weight)

Labels are resolved in this order:

1. `--label-filter <dsl>` if provided: matching records are positive, non-matching records are negative.
2. The record's `kind` field if present (`puzzle` / `nonpuzzle`).
3. The configured puzzle verification filter when `kind` is absent.

Use `--filter <dsl>` to select which rows are eligible before labeling, and
`--max-positives` / `--max-negatives` to cap class counts for quick balanced
exports.

Examples:

```bash
crtk record dataset classifier \
  -i dump/run.puzzles.json \
  -i dump/run.nonpuzzles.json \
  -o training/classifier/run

crtk record dataset classifier \
  -i dump/run.records.json \
  -o training/classifier/custom \
  --filter 'gate=AND;break=1;nodes>=50000000;' \
  --label-filter 'gate=AND;eval>=3.0;leaf[break=2;eval<=0.0];'
```

## JSONL labels for text or classifier pipelines: `record export training-jsonl`

Exports one position per JSONL line. The command labels rows using the puzzle
Filter DSL and parent-position relationships:

- `coarse_label=1`, `fine_label=2`: rows matching the puzzle DSL.
- `coarse_label=1`, `fine_label=1`: rows sharing a parent FEN with a puzzle.
- `coarse_label=0`, `fine_label=0`: remaining rows.

By default, engine/PV details are omitted from model input. Add
`--include-engine-metadata` when you want them retained as metadata.

Example:

```bash
crtk record export training-jsonl \
  -i dump/run.puzzles.json \
  -i dump/run.nonpuzzles.json \
  -o training/run.training.jsonl \
  --recursive \
  --max-records 100000
```

## Puzzle JSONL with LC0 policy values: `record export puzzle-jsonl`

Exports puzzle rows as JSONL with LC0 policy information. This path requires
LC0J weights because it uses the weights' policy map.

Example:

```bash
crtk record export puzzle-jsonl \
  -i dump/run.puzzles.json \
  -o training/run.puzzle.jsonl \
  --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin \
  --puzzles
```

Use `--filter <dsl>` for row selection, or `--nonpuzzles` to export non-puzzle
rows through the same format.

## Analysis stability features: `record-analysis-delta`

Exports one JSONL row per record with initial/final evaluation, delta type,
fluctuation range, time/depth to final value, and related diagnostics. This is
useful for filtering out unstable engine samples before training.

Example:

```bash
crtk record analysis-delta \
  -i dump/run.puzzles.json \
  -o dump/run.analysis-delta.jsonl
```

## Converting `.npy` → PyTorch shards

This repo no longer ships a conversion helper script. If you need `.pt` shards, use your own small Python utility (e.g. load `*.features.npy`/`*.labels.npy` and `torch.save(...)`).
