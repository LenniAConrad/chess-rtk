# `bt4` Architecture

`bt4` supports Leela Chess Zero attention-body transformer networks under the
Java package `chess.nn.lc0.bt4`.

The default descriptor is:

```text
lc0-bt4-1024x15x32h
```

That means:

- 64 board-square tokens
- 112 LC0 input planes
- 1024 model channels
- 15 transformer encoder blocks
- 32 attention heads
- 1858 compressed attention-policy logits
- WDL value output

## Input

BT4 starts from LC0's 112-plane input:

```text
112 x 8 x 8
```

The Java encoder exposes both layouts:

- channel-major planes: `112 * 64`
- token-major features: `64 * 112`

For the BT4 positional map embedding, the network appends a 64-way one-hot
square vector to each token before the input projection:

```text
64 x (112 + 64)
```

The default input format is `BT4_CANONICAL_112`, which uses modern LC0
auxiliary planes:

- castling rook-location planes
- en-passant target plane
- rule-50 counter scaled to hectoplies
- edge plane
- LC0 canonical spatial transform when castling rights do not block it

## Body

The reference body is a post-layer-norm transformer encoder:

```text
input projection
encoder block x 15:
  multi-head self-attention
  residual add
  layer norm
  feed-forward network with Mish
  residual add
  layer norm
```

The Java implementation includes a CPU reference path plus optional native GPU
backends for CUDA, ROCm, and oneAPI. Native backend selection follows
`crtk.lc0.bt4.backend` when set, otherwise it follows the shared
`crtk.lc0.backend` property.

## Policy

The attention policy produces an internal tensor:

```text
67 x 64 = 4288 logits
```

It contains:

- `64 x 64` from-square/to-square logits
- three underpromotion planes

The generated map gathers those internal logits into the standard LC0
attention-policy vector:

```text
1858 logits
```

## Value

The value head returns WDL probabilities ordered as:

```text
win, draw, loss
```

The scalar value is `win - loss`, from the side-to-move perspective.

## Binary Format

`BinLoader` reads a compact CRTK BT4 `.bin` format:

- little-endian
- magic: `BT4J`
- version: `1`
- architecture metadata
- input embedding dense layer
- encoder blocks
- attention policy head
- WDL value head

Dense layers store row-major float32 weights as:

```text
inDim, outDim, weightCount, weights..., biasCount, bias...
```

This is the Java/native evaluator format. It is deliberately simpler than
LC0's protobuf and is meant for exporter tests, correctness checks, and
Java-side integration work.
