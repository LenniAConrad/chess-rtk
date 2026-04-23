# Datasets

ChessRTK exports machine-learning datasets from mined or imported analysis
records. The dataset commands are dependency-free Java writers: they create
NumPy `.npy`, JSONL, and metadata files directly from record dumps.

The usual inputs are:

- `*.puzzles.json` and `*.nonpuzzles.json` from `puzzle mine`
- `.record` JSON arrays from analysis pipelines
- JSONL record streams accepted by `record` utilities
- filtered/merged files produced by `record files`

Use `record stats`, `record tag-stats`, and `record analysis-delta` before
exporting large datasets. They make it easier to catch unstable evaluations,
unexpected tag distributions, and class imbalance.

## Export Inventory

| Command | Output | Typical use |
| --- | --- | --- |
| `record dataset npy` | `(N, 781)` float32 features plus scalar eval labels | quick PyTorch/JAX/regression experiments |
| `record dataset lc0` | 112-plane LC0-style inputs, policy, value, metadata | policy/value experiments and LC0-compatible preprocessing checks |
| `record dataset classifier` | 21-plane inputs plus binary labels | puzzle/non-puzzle or custom binary classifiers |
| `record export training-jsonl` | one JSON object per position with coarse/fine labels | text, tabular, or custom feature pipelines |
| `record export puzzle-jsonl` | puzzle JSONL with LC0 policy-map information | policy-aware puzzle training rows |
| `record analysis-delta` | JSONL evaluation stability diagnostics | filtering and dataset quality reports |

## Eval Regression: `record dataset npy`

Input: a `.record` JSON array.

Output:

- `<stem>.features.npy` shaped `(N, 781)` float32
- `<stem>.labels.npy` shaped `(N,)` float32

Labels are evaluations in pawns, clamped to `[-20, +20]`.

```bash
crtk record dataset npy \
  -i dump/run.puzzles.json \
  -o training/pytorch/data/puzzles
```

Use this export when you want compact numeric features without a chess-engine
specific plane layout.

## LC0-Style Tensors: `record dataset lc0`

Input: a `.record` JSON array.

Output:

- `<stem>.lc0.inputs.npy` shaped `(N, 112*64)` float32
- `<stem>.lc0.policy.npy` shaped `(N, policySize)` float32
- `<stem>.lc0.value.npy` shaped `(N,)` float32 in `[-1, 1]`
- `<stem>.lc0.meta.json` with policy encoding, compression, value scale, and
  source metadata

Policy rows are one-hot. If `--weights` is supplied, ChessRTK reads the LC0J
weights policy map and compresses policy targets to the network's policy size.
Without weights, the raw 73-plane policy size (`4672`) is used.

```bash
crtk record dataset lc0 \
  -i dump/run.puzzles.json \
  -o training/lc0/puzzles \
  --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin
```

This export is useful when you want to verify LC0-style feature encoding,
inspect policy targets, or train/evaluate tooling that expects LC0-shaped
planes.

## Binary Classifier: `record dataset classifier`

Input: one or more record files or directories.

Output:

- `<stem>.classifier.inputs.npy` shaped `(N, 21*64)` float32
- `<stem>.classifier.labels.npy` shaped `(N,)` float32
- `<stem>.classifier.meta.json` with sources, filters, class counts, and
  positive-class weight

Labels are resolved in this order:

1. `--label-filter <dsl>` marks matching rows positive and non-matching rows
   negative.
2. A record `kind` field of `puzzle` or `nonpuzzle` is used when present.
3. The configured puzzle verification Filter DSL is used when `kind` is absent.

Use `--filter <dsl>` to select eligible rows before labeling. Use
`--max-positives` and `--max-negatives` to cap classes for balanced smoke
datasets.

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

## JSONL Labels: `record export training-jsonl`

This command writes one position per JSONL line. It is designed for pipelines
that prefer structured text over tensor files.

Label policy:

- `coarse_label=1`, `fine_label=2`: rows matching the puzzle DSL
- `coarse_label=1`, `fine_label=1`: rows sharing a parent FEN with a puzzle
- `coarse_label=0`, `fine_label=0`: remaining rows

Engine/PV details are omitted from model input by default. Add
`--include-engine-metadata` when you want those details retained as metadata.

```bash
crtk record export training-jsonl \
  -i dump/run.puzzles.json \
  -i dump/run.nonpuzzles.json \
  -o training/run.training.jsonl \
  --recursive \
  --max-records 100000
```

## Puzzle JSONL: `record export puzzle-jsonl`

This command writes puzzle rows with LC0 policy information. It requires LC0J
weights because it uses the weights policy map.

```bash
crtk record export puzzle-jsonl \
  -i dump/run.puzzles.json \
  -o training/run.puzzle.jsonl \
  --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin \
  --puzzles
```

Use `--filter <dsl>` for row selection. Use `--nonpuzzles` to export negative
examples through the same shape.

## Stability Features: `record analysis-delta`

`record analysis-delta` writes one JSONL row per record with:

- initial and final evaluation
- delta type and delta magnitude
- fluctuation range
- depth/time to final value
- diagnostics useful for filtering unstable engine samples

```bash
crtk record analysis-delta \
  -i dump/run.puzzles.json \
  -o dump/run.analysis-delta.jsonl
```

## Recommended Export Flow

1. Merge and filter source dumps with `record files`.
2. Inspect counts with `record stats` and `record tag-stats`.
3. Generate `record analysis-delta` and remove unstable rows if needed.
4. Export one tensor or JSONL shape for the target training code.
5. Keep the generated `*.meta.json` next to tensor files so model runs can be
   traced back to sources, filters, and label policy.

## Converting `.npy` to Framework Shards

ChessRTK writes portable `.npy` files. Framework-specific shards can be built by
loading those files in the training stack and saving the native format for that
framework.
