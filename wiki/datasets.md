# Datasets

This repo can export training tensors from mined or imported analysis dumps.

## From `.record` JSON: `record-to-dataset`

Input: a `.record` JSON array (see `chess.struct.Record`).

Output (NumPy `.npy`, float32):
- `<stem>.features.npy` shaped `(N, 781)`
- `<stem>.labels.npy` shaped `(N,)` (evaluation in pawns, clamped to `[-20, +20]`)

Example:

```bash
crtk record-to-dataset -i dump/run.puzzles.json -o training/pytorch/data/puzzles
```

## From `.record` JSON: `record-to-lc0`

Exports LC0-style inputs/policy/value tensors:

- `<stem>.lc0.inputs.npy` shaped `(N, 112*64)` float32
- `<stem>.lc0.policy.npy` shaped `(N, policySize)` float32 (one-hot)
- `<stem>.lc0.value.npy` shaped `(N,)` float32 (scalar in `[-1,1]`)
- `<stem>.lc0.meta.json` metadata (policy encoding, compression, value scale)

If `--weights` is provided, the policy is compressed to the LC0 network's policy size using the weights' policy map.
Otherwise, the raw 73-plane policy size (4672) is used.

Example:

```bash
crtk record-to-lc0 -i dump/run.puzzles.json -o training/lc0/puzzles --weights models/lc0_744706.bin
```

## From Stack dumps: `stack-to-dataset`

Input: `Stack-*.json` JSON array dumps (one object per position, with `position` and `analysis` fields).

Output: the same `(N, 781)` / `(N,)` tensors as above.

Example:

```bash
crtk stack-to-dataset -i Stack-0001.json -o training/pytorch/data/stack_0001
```

## Converting `.npy` → PyTorch shards

This repo no longer ships a conversion helper script. If you need `.pt` shards, use your own small Python utility (e.g. load `*.features.npy`/`*.labels.npy` and `torch.save(...)`).
