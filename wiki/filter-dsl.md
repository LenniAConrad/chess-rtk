# Filter DSL

Engine analysis is a firehose of numbers — depth, nodes, evaluations, win/draw/loss estimates across several lines. The Filter DSL is how you ask a sharp question of that data and get back a single `true` or `false`. A filter is a tree of boolean checks over a position's principal variations (PVs), joined by a logical gate. The same machinery decides which analyzed positions become puzzles, picks rows out of `.record` files when you export or split them, and labels rows for ML datasets. It lives in `chess.uci.Filter`, round-trips through parse and serialize without loss, and is deterministic to the bit: identical filter, identical analysis, identical verdict.

What follows is the reference — block structure, keys, predicates, operators — with worked examples you can drop straight into a flag or a config value.

## Where filters are used

A filter is always a plain DSL string. It reaches a command from one of two places:

- **Defaults in `config/cli.config.toml`** — the four mining gates ship as TOML multi-line strings: `puzzle-quality`, `puzzle-winning`, `puzzle-drawing`, and `puzzle-accelerate`. Tune these to match your engine.
- **Per-command flags** — override or supply a filter at the command line.

| Command | Flag | What the filter does |
| --- | --- | --- |
| `puzzle mine` | `--puzzle-quality DSL` | Minimum effort before a position is even considered |
| `puzzle mine` | `--puzzle-winning DSL` | Accept as a "winning" puzzle |
| `puzzle mine` | `--puzzle-drawing DSL` | Accept as a "drawing" puzzle |
| `puzzle mine` | `--puzzle-accelerate DSL` | Cheap early reject to skip obvious non-puzzles |
| `record files` | `--filter\|-f DSL` | Keep only matching records when merging/splitting |
| `record export plain` | `--filter\|-f DSL` | Select records to export |
| `record export csv` | `--filter\|-f DSL` | Select records to export |
| `record export puzzle-jsonl` | `--filter\|-f DSL` | Row selection before export |
| `record export training-jsonl` | `--filter\|-f DSL` | Matching rows become `verified_puzzle` |
| `record dataset classifier` | `--filter\|-f DSL` | Row selection |
| `record dataset classifier` | `--label-filter DSL` | Positive-label rule (overrides the kind) |

See [Mining](mining.md) for the mining pipeline, [Datasets](datasets.md) for export and dataset commands, and the [Configuration](configuration.md) page for the TOML keys.

## Mental model

A filter is a **tree of blocks**. Each block does three things:

1. Optionally picks a MultiPV line to inspect (`break=<n>`, where `1` = the best line / PV1, `2` = PV2, and so on).
2. Evaluates its own predicates (e.g. `eval>=300`) against that line, and recursively evaluates any nested child blocks (`leaf[ ... ]`).
3. Reduces every resulting boolean — predicates plus children — into one value using its gate (`gate=AND`, `gate=OR`, …).

The whole filter's verdict is the outermost block's verdict.

> A block reads the deepest available output at its chosen `break` index. A predicate that references an attribute the engine never reported fails rather than erroring — so a filter is exactly as strict as the data behind it, no stricter.

## Syntax

- Tokens within a block are separated by `;` (semicolons). Commas and spaces also work as separators, and whitespace is otherwise ignored.
- A nested block is written `leaf[ ... ]` — its contents are themselves a full filter.
- A predicate is `key` + operator + `value`, e.g. `nodes>=50000000` or `eval<=0`.
- Comparison operators are `>`, `>=`, `=`, `<=`, `<`.

A minimal one-block filter:

```text
gate=AND;break=1;depth>=20;eval>=300
```

Read it left to right: on PV1, require depth of at least 20 **and** an evaluation of at least +3.00 pawns.

## Block keys

These keys configure the block rather than test the position:

| Key | Values | Default | Meaning |
| --- | --- | --- | --- |
| `gate=` | `AND`, `NOT_AND`, `OR`, `NOT_OR`, `XOR`, `X_NOT_OR`, `SAME`, `NOT_SAME` | `AND` | How this block reduces its booleans |
| `break=` | integer `>= 1` | unbound | Which MultiPV line to read predicates from |
| `null=` | `true` or `false` | `false` | Result returned when the requested PV data is missing |
| `empty=` | `true` or `false` | `false` | Result returned when the block has no predicates and no leaves |

Notes on `break=`:

- `break=1` reads PV1 (the engine's best line), `break=2` reads PV2, etc.
- If a block has predicates but no `break=` (or `break=0`), it defaults to PV1.
- A block with only nested `leaf[...]` children and no predicates of its own does not read any PV, so you can omit `break=` on those purely structural blocks.

Notes on `null=` and `empty=`:

- `null=true` makes a block pass when its PV is absent — handy when a missing second line should read as "no counter-line exists." `null=false` makes the same block fail. Set this deliberately on anything reading `break=2` or higher, because that line genuinely may not be there.
- `empty=` only bites on a block with no predicates and no leaves: it is the constant that empty block returns.

## Gates

A gate folds the booleans a block produced — its predicate results, then its child results — into one value.

| Gate | True when |
| --- | --- |
| `AND` | every value is true |
| `NOT_AND` | the AND result negated |
| `OR` | any value is true |
| `NOT_OR` | the OR result negated (none are true) |
| `XOR` | an odd number of values are true |
| `X_NOT_OR` | an even number of values are true (XNOR) |
| `SAME` | all values are identical |
| `NOT_SAME` | the values are not all identical |

`AND` and `OR` carry almost every filter you will ever write. The rest are here for completeness and to let any serialized filter round-trip exactly.

## Predicates

A predicate tests one attribute of the selected PV with `>`, `>=`, `=`, `<=`, or `<`.

| Key | Type | Notes |
| --- | --- | --- |
| `depth` | integer | Search depth |
| `seldepth` | integer | Selective depth |
| `multipv` | integer | MultiPV index reported on the line |
| `hashfull` | integer | Hash-table fill, `0..1000` (1000 ≈ 100%) |
| `nodes` | integer | Visited node count |
| `nps` | integer | Nodes per second |
| `tbhits` | integer | Endgame tablebase hits |
| `time` | integer | Elapsed time in milliseconds |
| `eval` | score | Centipawns, pawns, or a mate distance |
| `chances` | W/D/L | Win/draw/loss probabilities |

### `eval` values

The right-hand side of an `eval` predicate takes three forms:

- **Centipawns** as a bare integer: `eval>=300` means +3.00 pawns or better.
- **Pawns** with a decimal point: `eval>=3.0` is the same as `eval>=300`; `eval<-0.5` is `eval<-50`.
- **Mate** with a `#`: `eval>=#3` (mate in 3 for the side to move), `eval<=#-2` (getting mated in 2). A leading `+` is optional (`#+3` == `#3`).

Every mate score outranks every centipawn score, so `eval>=#5` matches any forced mate for the side to move regardless of distance. Comparisons also honor the engine's bound hints: a lower-bound score can only satisfy `>`/`>=`, an upper-bound score only `<`/`<=`, and `=` demands an exact, unbounded score.

### `chances` values

`chances` tests the reported win/draw/loss triple. Its canonical form is three integers summing to 1000 (per-mille): `chances>=790/200/10`, `chances=1000/0/0`. Percentages work too — `win 79% draw 20% loss 1%`, or just `79 20 1` when the three sum to 100 — and are normalized onto the `/1000` basis.

## Worked examples

### Quality gate: demand real effort on the top two lines

Don't trust a verdict the engine hasn't worked for: accept a position only after at least 50M nodes on both PV1 and PV2. This is the default `puzzle-quality` filter.

```text
gate=AND;null=false;empty=false;
leaf[gate=AND;break=1;nodes>=50000000];
leaf[gate=AND;break=2;null=false;empty=false;nodes>=50000000];
```

### Winning puzzle: one good move, no good alternative

A good puzzle has exactly one answer. Here PV1 wins outright (+3.00 or better) while PV2 is at best equal — the signature of a forcing tactic where the second-best move goes nowhere. This is the default `puzzle-winning` filter.

```text
gate=AND;
leaf[eval>=300];
leaf[break=2;null=false;eval<=0];
```

### Drawing puzzle: only one move holds

The mirror image of the winning gate. PV1 holds the position equal or better; every alternative collapses by 3 pawns — find the only defense. This is the default `puzzle-drawing` filter.

```text
gate=AND;
leaf[eval>=0];
leaf[break=2;null=false;eval<=-300];
```

### Accelerate: reject obvious non-puzzles cheaply

Deep search is the expensive part, so spend it only on candidates worth the cost. After a shallow two-PV pass, this gate discards anything that can't become a puzzle. It is the default `puzzle-accelerate` filter. The two nested `OR` blocks each say the same kind of thing — "PV1 is unconvincing **or** PV2 is already strong" — and either condition means the position has no unique best move.

```text
gate=AND;
leaf[break=1;nodes>=2000000];
leaf[break=2;null=false;nodes>=2000000];
leaf[gate=OR;eval<300;leaf[break=2;eval>0]];
leaf[gate=OR;eval<0;leaf[break=2;eval>-300]];
```

### Mate-aware quality, on one line

Not every filter needs a tree. A single block mixing predicate types does fine when you only want one deeply searched, decisive line — common when selecting records:

```text
gate=AND;break=1;depth>=30;nodes>=100000000;eval>=#5
```

### Override a mining gate at the command line

```bash
java -jar crtk.jar puzzle mine --input seeds.txt --output dump/run \
  --puzzle-winning "gate=AND;leaf[eval>=500];leaf[break=2;null=false;eval<=0]"
```

### Filter records on export

```bash
java -jar crtk.jar record files --input dump/run.puzzles.json --output dump/strong \
  --filter "gate=AND;break=1;eval>=400"
```

## Reading and writing filters programmatically

`Filter.FilterDSL.fromString(...)` parses a DSL string into an immutable rule tree; `Filter.toString()` serializes it back. The serializer drops `break=` when it is unbound (0) and writes each predicate in canonical `key`+operator+`value` form, so parsing and re-serializing reaches a fixed point — the second pass produces the same string as the first.

A persisted filter — the one recorded alongside a mining run, say — often reads fuller than what you typed: explicit `null=`/`empty=`, a `break=` on every line. That is each block spelling out its effective configuration, not a different filter. The expanded form and your compact one mean exactly the same thing.

## Tips

- **Set `null=` on every counter-line block.** A `break=2`-or-higher block can land on a position with fewer PVs than you assumed; leaving the default in place silently fails it. Decide on purpose whether a missing line should pass (`null=true`) or fail (`null=false`).
- **Calibrate thresholds to your engine.** Centipawn scale, contempt, pruning, and network behavior all differ across Stockfish, LC0, and the built-in evaluators, so `+300` does not mean the same thing everywhere. The shipped defaults lean conservative.
- **Write pawns or `#`, not raw centipawns.** `eval>=3.0` and `eval>=#3` parse identically to their integer forms and are far easier to read back six months later.
- **Order accelerate gates cheap-first.** Put node and time checks ahead of evaluation checks so a fast reject short-circuits before the costly comparison runs.

## Related pages

- [Mining](mining.md) — the mining pipeline these gates drive
- [Datasets](datasets.md) — `record files`, `record export`, and `record dataset` filtering
- [Configuration](configuration.md) — the `cli.config.toml` filter keys
- [Command Reference](command-reference.md) — full flag listings
