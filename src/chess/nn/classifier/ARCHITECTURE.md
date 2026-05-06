# `classifier` Architecture

`classifier` is a small LC0-style residual CNN for binary chess position
classification. The first intended target is puzzle detection.

The goal is simple:

- Given one chess position, output one scalar logit for the positive class.

This model should stay close to LC0 in structure, but change two things:

- no history in the input
- no policy/value heads in the output

## Input

The input should be a side-to-move-oriented tensor with shape:

```text
21 x 8 x 8
```

This is intended to be "LC0 current position only":

- one 13-plane board slice
- the same 8 auxiliary planes used by LC0

Recommended plane layout:

1. our pawns
2. our knights
3. our bishops
4. our rooks
5. our queens
6. our king
7. opponent pawns
8. opponent knights
9. opponent bishops
10. opponent rooks
11. opponent queens
12. opponent king
13. repetition plane
14. our queen-side castling right
15. our king-side castling right
16. opponent queen-side castling right
17. opponent king-side castling right
18. side-to-move-is-black plane
19. halfmove clock plane
20. unused zero plane
21. edge plane

Notes:

- Positions should be encoded from the side-to-move perspective, exactly like
  LC0.
- The input intentionally removes history. Only the current position is used.
- If repetition information is unavailable, the repetition plane can stay zero.

## Hidden Layers

The network should be a smaller LC0-like residual tower:

```text
Stem:
  3x3 conv, 21 -> 64 channels
  batch norm
  ReLU

Trunk:
  6 residual blocks, 64 channels throughout

Residual block:
  3x3 conv, 64 -> 64
  batch norm
  ReLU
  3x3 conv, 64 -> 64
  batch norm
  residual add
  ReLU
```

This is intentionally smaller than a typical LC0 network. It keeps the same
general architecture family while remaining cheap enough to train and iterate
on quickly.

Possible larger variants:

- `8 x 96`
- `10 x 128`

Those should only be used if the small baseline underfits.

Approximate parameter counts for the documented variants:

- `6 x 64`: `458,273` trainable parameters
- `8 x 96`: `1,351,681` trainable parameters
- `10 x 128`: `2,982,881` trainable parameters

These counts assume the training-time architecture described above:

- convolution weights
- batch-norm scale and shift parameters
- final linear weights and bias

If the network is exported in an LC0-style fused `.bin` where batch norm is
folded into convolution biases, the serialized parameter counts are slightly
smaller:

- `6 x 64`: `457,409`
- `8 x 96`: `1,350,017`
- `10 x 128`: `2,980,161`

## Weights File

`classifier` weights should use the same general `.bin` container style
as the existing LC0 CNN weights in this repo, but with a
`classifier`-specific network layout.

Recommended filename convention:

- baseline: `models/classifier_21planes-6blocksx64-head32-logit1.bin`
- larger variants: `models/classifier_<blocks>x<channels>.bin`

Examples:

- `models/classifier_8x96.bin`
- `models/classifier_10x128.bin`

## Output

The output should be exactly one scalar logit:

```text
1 scalar logit
```

Recommended head:

```text
1x1 conv, 64 -> 32
batch norm
ReLU
global average pool
linear 32 -> 1
```

Interpretation:

- positive logit: positive class is more likely
- negative logit: negative class is more likely

There should be no auxiliary heads in the baseline architecture.

## Binary Format

The binary format should stay as close as possible to the LC0 CNN weights format
already used by `chess.nn.lc0.cnn`.

For Java code, the package name should be `chess.nn.classifier` because
Java package identifiers cannot contain hyphens.

Recommended rules:

- little-endian
- same versioning approach as LC0 CNN
- same conv / batch-norm / dense tensor serialization style as LC0 CNN
- only the architecture-specific header fields and tail layers should differ

Conceptually, `classifier` should be treated as:

- the same residual-tower family as LC0
- a different input encoder
- a different output head

Recommended header fields:

- magic
- version
- input channels = `21`
- trunk channels = `64`
- residual blocks = `6`
- head channels = `32`
- output size = `1`

Recommended layer order:

1. input stem conv + batch norm (`21 -> 64`)
2. residual tower (`6` blocks at `64` channels)
3. head conv + batch norm (`64 -> 32`)
4. final dense layer (`32 -> 1`)

Compared with LC0 CNN, the important differences are:

- input layer expects `21 x 8 x 8` instead of the LC0 input stack
- there is no policy head
- there is no WDL/value head split
- the output is one scalar logit instead of policy logits plus WDL

In other words, the file format should feel "LC0-like", but the stored network
definition should match the `classifier` architecture documented here
rather than the full LC0 policy+value network.

## Training Recommendation

Train the baseline model as a binary classifier on `is_puzzle`; other binary
position targets can reuse the same architecture and exporter with a different
label filter.

Because the dataset is approximately:

- 20 million non-puzzle positions
- 2 million puzzle positions

the loss should handle class imbalance:

- weighted binary cross-entropy
- or focal loss if hard negatives matter more in practice

Evaluation should prioritize:

- PR-AUC
- recall at fixed precision
- calibration on held-out data

## Summary

`classifier` should be:

- structurally similar to a small LC0 residual CNN
- different from LC0 by using only the current position as input
- different from LC0 by producing only one scalar logit as output
