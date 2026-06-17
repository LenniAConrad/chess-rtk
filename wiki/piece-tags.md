# Position and Piece Tags

A position holds a lot of truths at once — who moves, who's up material, where the pawns lock, whether the king is exposed, what tactic is hanging in the air. **Tags** are how ChessRTK names each of those truths as a single structured string, so a position becomes a stream of facts you can sort, count, and reason over instead of a board you have to re-read. The same schema feeds three consumers: the [Filter DSL](filter-dsl.md) that gates puzzle mining, the dataset exporters, and the T5 summaries. Every tag traces back to one chess core and a fixed set of deterministic taggers, so the same FEN always produces the same tags. That is the property that makes them worth filtering on, diffing, and handing to an LLM as grounded evidence — the strings don't drift under you.

Two commands emit tags: `fen tags` works on any FEN, FEN file, or PGN, and `puzzle tags` walks a puzzle's principal variations and tags every move. `record tag-stats` rolls the distribution up across a mined dataset.

> For the precise parser grammar, family list, field identities, and golden fixtures, see the [Tag Reference](tag-reference.md). This page is the conceptual overview.

![Tagging pipeline](../assets/diagrams/crtk-tagging-flow.png)

## Tag Shape

Each tag is a family prefix, a colon, then ordered `key=value` fields:

```text
FAMILY: key=value key=value
```

Real output from `crtk fen tags`:

```text
META: to_move=white
META: phase=opening
FACT: status=normal
MATERIAL: balance=equal
PIECE: tier=very_strong side=white piece=bishop square=c4
PAWN: islands side=white count=1
KING: safety=safe side=black
TACTIC: motif=mate_in_1 side=white move=f3f7
```

They're plain strings, but the key/value layout lets tools sort, dedupe, group, count, filter, and render them to text with no bespoke parser. Field order within a family never changes, so even a naive substring match against a tag holds up.

## Two Kinds of Tags

Tags come in two flavors, **heuristic** and **engine-derived**, and the line between them is a reproducibility line. A heuristic tag depends only on the position in front of it. An engine-derived tag also depends on which engine ran, how long you let it think, and which build you used — change any of those and the tag can change too.

| Kind | Source | Reproducible from FEN alone? | How to enable |
| --- | --- | --- | --- |
| Heuristic | Static taggers over the board | Yes — fully deterministic | Always emitted |
| Engine-derived | UCI engine analysis (eval, PV, WDL, threats) | Only if engine + limits are fixed | `--analyze` on `fen tags`; on by default for `puzzle tags` |

Heuristic tags cover material, pawn structure, king safety, piece placement, space, development, mobility, and the tactical motifs the core can prove on its own — pins, hanging pieces, the forced mates it can read directly off the board. The engine-derived families (`META: source=engine`, `META: eval_cp`, `META: wdl`, `PV`, `CAND`, `THREAT`, and the analysis-backed `IDEA` and `TACTIC` rows) only show up once analysis is requested or supplied.

`puzzle tags` analyzes by default; to hold it to the static taggers, pass `--no-analyze`:

```bash
crtk puzzle tags --fen "<FEN>" --no-analyze --multipv 2
```

`fen tags` is static unless you add `--analyze`.

## The Taggers

A fixed set of taggers produces every tag. Each owns one or more families and runs deterministically over the core's view of the position — no tagger reaches outside the board it was handed.

| Tagger | Families | What it reports |
| --- | --- | --- |
| Position / facts | `META`, `FACT` | Side to move, phase, difficulty, status, check state, castling rights, en-passant, center facts, and (with analysis) eval bucket / cp / WDL |
| Material | `MATERIAL`, `ENDGAME` | Balance, per-piece counts, bishop pair, exchange imbalances, endgame class |
| Pawn | `PAWN` | Islands, doubled / isolated / passed / advanced pawns, promotion facts |
| Piece | `PIECE` | Placement tiers, activity, outposts, hanging pieces, strongest/weakest anchors |
| King safety | `KING` | Safety rating, castled state, shelter, back-rank and exposure facts |
| Tactical | `TACTIC`, `CHECKMATE` | Pins, forks, skewers, discovered attacks, overloads, material-losing moves (with SEE), mate-in-N, named mate patterns |
| Threats | `THREAT` | Opponent threats from reply searches (analysis-backed) |
| Ideas | `IDEA`, `MOVE`, `PV`, `CAND` | Strategic ideas, legal-move and one-ply move-count facts, principal-variation and candidate-move facts |
| Strategic | `SPACE`, `DEVELOPMENT`, `MOBILITY`, `INITIATIVE`, `OPENING` | Space and center control, undeveloped pieces, mobility comparisons, tempo/forcing signals, ECO/opening labels when an ECO table is configured |

Families come and go with the position. A symmetric opening produces no passed-pawn or back-rank tags; an endgame produces `ENDGAME` rows a middlegame never will. Absence is itself a signal — don't read a missing family as a bug.

## Commands

### Tag a single position

```bash
crtk fen tags --fen "<FEN>"
```

Include the FEN alongside the tags:

```bash
crtk fen tags --fen "<FEN>" --include-fen
```

### Tag a file or a PGN

```bash
crtk fen tags -i positions.txt
crtk fen tags --pgn games.pgn --mainline
crtk fen tags --pgn games.pgn --sidelines
```

`--mainline` keeps PGN input to the main line; `--sidelines` follows the variations too. `--sequence` reads a FEN file as one ordered game rather than a bag of independent positions — relevant the moment you care about delta tags or move-to-move structure.

### Tag a puzzle's lines

`puzzle tags` takes the engine's top moves, expands each into a principal variation, and tags every move along the way:

```bash
crtk puzzle tags --fen "<FEN>" --multipv 3 --pv-plies 12
```

`--multipv` is how many lines to expand (default 3), `--pv-plies` is how deep each one runs (default 12), and `--tag-multipv` is the MultiPV the engine uses while tagging the individual moves (default 1) — a separate knob, because you often want several candidate lines but only one engine opinion per position inside them.

### Enrich with engine analysis

`--analyze` opens `fen tags` up to eval, WDL, threats, and the analysis-backed motifs. Pin the engine budget every time — an unbounded search makes the run depend on how busy your machine was, and that is the opposite of what tags are for:

```bash
crtk fen tags \
  --fen "<FEN>" \
  --analyze \
  --max-duration 2s \
  --multipv 3 \
  --wdl
```

You pick the engine with `--protocol-path` (an engine protocol TOML) and bound it with `--max-nodes`, `--max-duration`, `--threads`, and `--hash`. See [Configuration](configuration.md) for protocol setup and [In-House Engine](in-house-engine.md) for the built-in evaluator.

## Delta Tags

A move's meaning is mostly in what it changed. `--delta` on `fen tags` emits JSONL rows describing how the tag set shifts from a parent position to a child — the king that just lost its shelter, the pawn that became passed. That is precisely the "what changed after this move" evidence that move explanations, puzzle-line summaries, and training rows are built on.

```bash
crtk fen tags -i pairs.txt --delta --include-fen
```

For FEN-pair input, each line holds a parent and child FEN:

```text
<parent-fen> <child-fen>
```

For PGN input, the parent/child relationships fall out of the parsed movetext, so you don't spell the pairs out yourself. To compare analysis changes across a whole record file rather than one pair at a time, reach for `record analysis-delta`.

## Tags to Natural Language

The T5 commands read the tags and write a short prose summary from them. Because the prose is only as informed as the tags underneath it, the same static-versus-engine split carries straight through: no `--analyze`, no engine facts in the text.

```bash
crtk fen text --fen "<FEN>" --model models/t5.bin --include-fen
crtk puzzle text --fen "<FEN>" --model models/t5.bin --include-fen
```

Add `--analyze` for engine-enriched text, and keep the limits explicit in batch jobs — the same reproducibility argument as before, only now it decides what the sentences say:

```bash
crtk fen text \
  -i positions.txt \
  --model models/t5.bin \
  --analyze \
  --max-duration 2s \
  --multipv 2
```

When you want deterministic prose and no model in the loop, `position describe` renders the same facts to fixed text. See [T5 Summaries](t5.md) for the model setup used by `fen text` and `puzzle text`.

## Reading PIECE Tags

`PIECE` tags speak to where a piece sits and how much it can do. The fields you'll see most:

- `tier` — placement tier: `very_strong`, `strong`, `slightly_strong`, `neutral`, `slightly_weak`, `weak`, or `very_weak`
- `activity` — mobility signal such as `high_mobility` or `low_mobility`
- `extreme` — strongest/weakest piece anchors for the whole board or one side (`strongest`, `strongest_white`, `weakest_black`, and so on)
- `side` — `white` or `black`
- `piece` — `pawn`, `knight`, `bishop`, `rook`, `queen`, or `king`
- `square` — algebraic square

A tier is an auditable hint, not a verdict — a knight on a "weak" square can still deliver mate. When you're explaining a forcing sequence, let `TACTIC`, `CHECKMATE`, and `KING` outrank a generic placement tier; concrete beats positional when the king is in danger.

## Summarizing a Dataset

`record tag-stats` counts how often each tag occurs across a record file or mined dump. Run it before you train or publish: it surfaces the class imbalance, the family that never showed up, and the handful of generic tags drowning everything else — the problems that are cheap to fix now and expensive to discover in a trained model later.

```bash
crtk record tag-stats -i dump/run.puzzles.json
crtk record tag-stats -i dump/run.puzzles.json --top 50
```

For the broader file-level picture there's `record stats`, and the [Filter DSL](filter-dsl.md) turns those same tags into selection criteria for export and dataset building.

## Why a Stable Schema

The schema is frozen on purpose. Freeze it once and the same strings can drive three very different consumers without anyone re-deriving the facts:

- **Filters** — the [Filter DSL](filter-dsl.md) matches on tags to gate `puzzle mine` and to select records for `record files`, `record export`, and `record dataset`.
- **Datasets** — `record dataset npy|lc0|classifier` and `record export training-jsonl` carry tags as labels and features.
- **LLM agents** — tags are deterministic and readable, so a model can quote them as evidence rather than re-deriving facts it's prone to get wrong.

Same FEN, same engine, same limits, same tags. The whole design rests on that one guarantee.

## Related Pages

- [Tag Reference](tag-reference.md) — full grammar, family identities, and fixtures
- [Filter DSL](filter-dsl.md) — selecting positions and records by tag
- [Mining](mining.md) — where tags gate puzzle quality
- [Configuration](configuration.md) — configuring the analysis behind `--analyze`
- [Command Reference](command-reference.md) — every command and flag
