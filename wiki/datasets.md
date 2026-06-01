# Datasets

The hard part of building a training set is rarely the math — it's trusting that the planes you feed the model are the ones you think you're feeding it. `record dataset` and `record export` close that gap. They read `.record` JSON (or the `*.puzzles.json` / `*.nonpuzzles.json` from [puzzle mining](mining.md)) and write NumPy `.npy` tensors, JSONL label streams, and JSON metadata, with no Python and no external toolchain in the way. The writers run on the same chess core as the CLI and Workbench, so a position's planes, labels, and tags are identical to what those tools display — there is no second encoding to drift out of sync. What follows: what each export emits, the layouts that are fixed, the inputs each command expects, and two pipelines run end to end.

## What You Can Export

Dataset work splits along one question: do you want tensors or text?

- `record dataset KIND` writes binary NumPy tensors plus a metadata sidecar — reach for it when your training code wants feature arrays it can load and go.
- `record export FORMAT` writes rows you can read (JSONL, CSV, PGN, plain) — reach for it when you want to inspect, post-process, or hand structured text to your own featurizer.

| Command | Output | Typical use |
| --- | --- | --- |
| `record dataset npy` | `(N, 781)` float32 features plus scalar eval labels | quick regression / value-head experiments |
| `record dataset lc0` | 112-plane LC0-style inputs, one-hot policy, value, metadata | policy/value experiments with LC0-shaped planes |
| `record dataset classifier` | 21-plane inputs plus binary labels | puzzle / non-puzzle (or custom) classifiers |
| `record export training-jsonl` | one JSON position per line with coarse/fine labels | text, tabular, or custom feature pipelines |
| `record export puzzle-jsonl` | puzzle rows with LC0 policy-map information | policy-aware puzzle training rows |
| `record export puzzle-elo-jsonl` | verified puzzles with Elo ratings and position tags | difficulty-graded puzzle datasets |
| `record export plain` / `csv` / `pgn` | flat text, spreadsheet, or game files | inspection, sharing, and external tooling |

> The shapes below describe crtk's own encoding. The LC0-style export borrows LC0's input shape for compatibility checks and experimentation; it is **not** a bit-exact reproduction of any official LC0 training pipeline.

## Inputs

Every dataset command reads chess records. They tend to come from one of three places:

- `*.puzzles.json` and `*.nonpuzzles.json` written by `puzzle mine` (see [Puzzle Mining](mining.md)).
- `.record` JSON arrays from analysis pipelines.
- Merged, filtered, or split files produced by `record files`.

Look before you export. `record stats` gives you record counts and engines, `record tag-stats` shows the tag distribution (the same tags from [Position & Piece Tags](piece-tags.md)), and `record analysis-delta` surfaces positions whose evaluation never settled. A skewed class balance or a pile of unstable samples is cheap to spot here and expensive to discover three epochs into training.

```bash
crtk record stats     -i dump/run.puzzles.json
crtk record tag-stats  -i dump/run.puzzles.json
```

## Eval Regression Tensors: `record dataset npy`

Reads a single `.record` JSON array and writes a flat numeric feature matrix with scalar evaluation labels. This is the export for value-head or regression work that doesn't care about a chess-specific plane layout.

Output (with `--output training/run` as the prefix):

- `training/run.features.npy` shaped `(N, 781)` float32
- `training/run.labels.npy` shaped `(N,)` float32

Labels are evaluations in pawns, clamped to a stable range — left unclamped, a single forced mate would swamp the regression target and the model would spend its capacity chasing outliers.

```bash
crtk record dataset npy \
  -i dump/run.puzzles.json \
  -o training/run
```

Omit `--output` and the prefix defaults to `dump/<input-stem>.dataset`.

## LC0-Style Tensors: `record dataset lc0`

Reads a `.record` JSON array and writes LC0-shaped input planes with one-hot policy targets, a value label, and a metadata file — the export for policy/value experiments and for sanity-checking the LC0-style feature encoding.

Output (with `--output training/run`):

- `training/run.lc0.inputs.npy` shaped `(N, 112*64)` float32
- `training/run.lc0.policy.npy` shaped `(N, policySize)` float32
- `training/run.lc0.value.npy` shaped `(N,)` float32 in `[-1, 1]`
- `training/run.lc0.meta.json` recording the policy encoding, value scale, compression, and source metadata

Each policy row is one-hot over the legal-move target. Without `--weights`, rows use the raw 73-plane policy size (`4672`). Point `--weights PATH` at an LC0 CNN `.bin` file and crtk reads that network's policy map, compressing each target down to the network's own policy size — the rows then line up with the model you mean to train or evaluate against, instead of forcing a remap later.

```bash
crtk record dataset lc0 \
  -i dump/run.puzzles.json \
  -o training/run \
  --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin
```

## Binary Classifier Tensors: `record dataset classifier`

Reads one or more record files or directories and writes binary-labeled input planes — for a puzzle vs. non-puzzle split, or any yes/no question you can phrase as a filter.

Output (with `--output training/run`):

- `training/run.classifier.inputs.npy` shaped `(N, 21*64)` float32
- `training/run.classifier.labels.npy` shaped `(N,)` float32
- `training/run.classifier.meta.json` recording sources, filters, class counts, and the positive-class weight

Labeling tries three sources in order, stopping at the first that applies:

1. `--label-filter <dsl>` marks matching rows positive and the rest negative.
2. A record `kind` field of `puzzle` or `nonpuzzle`, when present.
3. The configured puzzle-verification Filter DSL, when `kind` is absent.

`--filter <dsl>` narrows which rows are eligible before labeling, `--recursive` walks input directories, and `--max-positives` / `--max-negatives` cap each class — the quickest way to a balanced smoke set without re-mining. The Filter DSL is the same gating language `puzzle mine` uses; [Puzzle Mining](mining.md) covers its syntax.

```bash
crtk record dataset classifier \
  -i dump/run.puzzles.json \
  -i dump/run.nonpuzzles.json \
  -o training/run

crtk record dataset classifier \
  -i dump/ \
  -o training/custom \
  --recursive \
  --filter 'gate=AND;break=1;nodes>=50000000;' \
  --label-filter 'gate=AND;eval>=3.0;leaf[break=2;eval<=0.0];' \
  --max-positives 20000 \
  --max-negatives 20000
```

## Labeled Positions: `record export training-jsonl`

Writes one chess position per JSONL line — for pipelines that would rather read structured text than unpack binary tensors. Accepts one or more files or directories.

Labels follow a fixed coarse/fine scheme:

- `coarse_label=1`, `fine_label=2` — rows matching the puzzle DSL (`--filter`); these become `verified_puzzle`.
- `coarse_label=1`, `fine_label=1` — rows that share a parent FEN with a puzzle.
- `coarse_label=0`, `fine_label=0` — every remaining row.

Engine and PV details stay out of the model input by default. `--include-engine-metadata` keeps them as metadata only, `--recursive` walks directories, and `--max-records N` stops after N rows.

```bash
crtk record export training-jsonl \
  -i dump/run.puzzles.json \
  -i dump/run.nonpuzzles.json \
  -o training/run.training.jsonl \
  --recursive \
  --max-records 100000
```

Omit `--output` and the path defaults to `dump/<input-stem>.training.jsonl`.

## Policy-Aware Puzzle Rows: `record export puzzle-jsonl`

Writes puzzle rows carrying LC0 policy-map information. The map comes from a network, so `--weights` pointing at a ChessRTK LC0 CNN `.bin` file is required, not optional.

```bash
crtk record export puzzle-jsonl \
  -i dump/run.puzzles.json \
  -o training/run.puzzle.jsonl \
  --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin \
  --puzzles
```

`--puzzles` keeps only puzzle records, `--nonpuzzles` runs negatives through the same shape, and `--filter <dsl>` selects rows more finely.

## Difficulty-Graded Puzzles: `record export puzzle-elo-jsonl`

Scores verified puzzles and writes one JSONL row each, pairing an Elo rating with the position tags — what you want when the dataset needs difficulty, not just a binary label. Accepts one or more files or directories.

Scoring runs a tree-search pass, which makes this the heaviest export here by a wide margin. `--threads N` (defaulting to the available processors) and `--max-records N` keep it in check. Already have a rating CSV from an earlier run? `--ratings-csv PATH` reuses it for a one-pass re-export and skips the search entirely.

```bash
crtk record export puzzle-elo-jsonl \
  -i dump/run.puzzles.json \
  -o training/run.puzzle-elo.jsonl \
  --recursive \
  --threads 8
```

## Text Exports: plain / csv / pgn

Sometimes you just need to look at the data, hand it to a spreadsheet, or open it in something that speaks PGN. `record export` writes those flat formats from a `.record` JSON array:

- `record export plain` — a `.plain` text dump; mainline only by default, `--sidelines` (alias `--export-all`) pulls in variations, `--csv` writes a CSV alongside it.
- `record export csv` — a spreadsheet-friendly CSV.
- `record export pgn` — one PGN game per record.

All three take `--filter <dsl>` to select records and default their output to `dump/<input-stem>.<ext>`.

```bash
crtk record export csv -i dump/run.puzzles.json -o training/run.csv
crtk record export pgn -i dump/run.puzzles.json -o training/run.pgn
```

## Evaluation Stability: `record analysis-delta`

A position whose evaluation thrashed throughout analysis carries a noisier label than its final number admits. `record analysis-delta` writes one JSONL row per record showing exactly that motion: initial and final eval, the delta's type and magnitude, the fluctuation range, and the depth and time at which the value finally settled. It's the cleanest way to find and drop unstable samples before they reach the model.

```bash
crtk record analysis-delta \
  -i dump/run.puzzles.json \
  -o dump/run.analysis-delta.jsonl
```

## Full Pipeline: Mine, Inspect, Train a Classifier

Mine, look at what you got, then balance it before exporting. The inspection step is the one people skip and regret.

```bash
# 1. Mine puzzles and non-puzzles into a dump directory.
crtk puzzle mine -i seeds/games.pgn -o dump/run --max-nodes 50000000

# 2. Inspect counts and tag distribution.
crtk record stats     -i dump/run.puzzles.json
crtk record tag-stats  -i dump/run.puzzles.json

# 3. Export balanced binary classifier tensors.
crtk record dataset classifier \
  -i dump/run.puzzles.json \
  -i dump/run.nonpuzzles.json \
  -o training/classifier/run \
  --max-positives 50000 \
  --max-negatives 50000
```

## Full Pipeline: LC0-Style Policy/Value Data

Filter a record dump down to stable samples, flag what's left for review, then export LC0-shaped tensors aligned to one network's policy map.

```bash
# 1. Merge and filter source dumps into one clean record file.
crtk record files \
  -i dump/ \
  -o dump/clean.json \
  --recursive \
  --filter 'gate=AND;nodes>=50000000;'

# 2. Flag unstable evaluations for review.
crtk record analysis-delta -i dump/clean.json -o dump/clean.analysis-delta.jsonl

# 3. Export LC0-style planes, policy, and value with the target weights.
crtk record dataset lc0 \
  -i dump/clean.json \
  -o training/lc0/clean \
  --weights models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin
```

## Reproducibility Notes

- Same input file, flags, and weights, same output — every export is deterministic down to the byte. Re-running a command reproduces its tensors and labels exactly.
- Keep each `*.meta.json` next to its tensor files. It records the sources, filters, label policy, and value scale, which is the difference between a model run you can trace and one you can only guess at.
- The `.npy` files are portable NumPy arrays. For framework-specific shards (PyTorch `.pt`, TFRecord, and the like), load them in your training stack and re-save in the native format — crtk stays framework-agnostic on purpose rather than guessing which one you use.

## Related Pages

- [Puzzle Mining](mining.md) — produce the `*.puzzles.json` / `*.nonpuzzles.json` inputs and learn the Filter DSL.
- [Position & Piece Tags](piece-tags.md) — the deterministic tags that ride along in JSONL exports and `record tag-stats`.
