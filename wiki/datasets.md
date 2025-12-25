# Datasets

This repo can export training tensors from mined or imported analysis dumps.

## From `.record` JSON: `record-to-dataset`

Input: a `.record` JSON array (see `chess.struct.Record`).

Output (NumPy `.npy`, float32):
- `<stem>.features.npy` shaped `(N, 781)`
- `<stem>.labels.npy` shaped `(N,)` (evaluation in pawns, clamped to `[-20, +20]`)

Example:

```bash
ucicli record-to-dataset -i dump/run.puzzles.json -o training/pytorch/data/puzzles
```

## From Stack dumps: `stack-to-dataset`

Input: `Stack-*.json` JSON array dumps (one object per position, with `position` and `analysis` fields).

Output: the same `(N, 781)` / `(N,)` tensors as above.

Example:

```bash
ucicli stack-to-dataset -i Stack-0001.json -o training/pytorch/data/stack_0001
```

## Converting `.npy` → PyTorch shards

This repo no longer ships a conversion helper script. If you need `.pt` shards, use your own small Python utility (e.g. load `*.features.npy`/`*.labels.npy` and `torch.save(...)`).
