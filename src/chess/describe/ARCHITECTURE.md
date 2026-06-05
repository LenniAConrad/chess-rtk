# Position Description — Architecture, Quality Assessment, and T5 Roadmap

This package turns a chess position into English at three detail levels
(`brief` / `normal` / `full`). Today that text is produced by a **deterministic
heuristic engine**. A trained **T5** text model is the planned upgrade; its stub
already lives here (`T5PositionDescriptionGenerator`). This note records, honestly,
how good the heuristic text is, where it hits a hard ceiling, and exactly what the
transformer is supposed to buy us.

## Pipeline today

```
Position
  -> PositionDescriptionExtractor   (cheap, static facts only)
  -> PositionDescriptionInput        (fen, side, status, phase, material counts,
                                      move-category counts, cp/WDL eval, ordered
                                      candidates with reason labels, tags, threats)
  -> ClassicalPositionDescriptionGenerator   (classify once -> render per level)
       + DescriptionLexicon          (pure word/number/list helpers)
```

The generator classifies the position **once** (terminal state, evaluation band,
material gap from piece-count diffs) and renders each level from that shared read,
in a confident chess-annotator voice. Output is a pure function of the input — no
RNG, no clock — so it is byte-stable for golden tests and the training export.

### Evaluation source: static (default) or engine search (opt-in)

The verdict is driven by the `Evaluation` in the input. By default that is the
cheap **static** eval, read "at a glance." A static read can judge a tactical
position falsely — calling a side winning that is actually being mated, or missing
a forced win — which is a *truthfulness* failure. So `EngineEvaluator` offers an
opt-in **engine-search** evaluation (`--eval engine` on the CLI, the "Engine eval"
toggle in the Describe tab): a deterministic short alpha-beta search with the
bundled classical evaluator (fresh table, one thread, fixed depth, node cap, no
clock). It carries a `mateIn` distance, so a forced mate is described as such
rather than as an absurd pawn figure, and the prose names its source ("the engine
evaluation" vs. "the static evaluation"). Static stays the default everywhere
because the training export and golden tests require the cheap, deterministic
read; engine eval is enrichment, not a replacement.

## How good is the text? (honest guess)

**Overall: about 7 / 10 as a product.** A competent, trustworthy narrator that
states what is true and never lies — but it *describes* the position rather than
*understanding* it. Broken down:

| Dimension | Score | Why |
|---|---|---|
| Truthfulness / no hallucination | ~9.5 | Faithful by construction. Every clause traces to a structured field. The only soft spot is mild interpretive framing ("an edge that rarely survives accurate play"), which is general chess truth, not a position-specific claim. |
| Grammar / mechanics | ~9 | Clean after review: articles, plurals, capitalization, list joins, number-words, no double spaces. |
| Reads as human — one position | ~7.5 | Genuinely natural in isolation; leads with the verdict, weaves candidate moves as judgment, handles terminal states with dignity. |
| Reads as human — across many positions | ~5.5 | The determinism that makes it trustworthy also makes it **repeat**. Every "clearly better" position uses the identical sentence. At scale the template shows. This is the most visible "not-quite-human" tell. |
| Chess insight / usefulness | ~4 | It reports surface facts (eval, material, move categories). It cannot say *why* a side is better or *what the plan is*. This is the real ceiling. |

One-line version: **the heuristic nails truthfulness and falls short on insight and
variety — and those two gaps are exactly what a transformer is for.**

## What the heuristic does genuinely well — keep these

- **Cannot hallucinate.** No invented openings, sacrifices, captured-piece
  identities, or king-safety claims. This is the property to protect at all costs.
- **Deterministic.** Testable, and a clean faithfulness *anchor* for distillation.
- **Correct verdicts.** Eval band, material idiom (a queen up, the exchange, two
  pawns up — never "9 pawns" for a queen), drawn/lost from WDL, terminal side.
- **Sensible move weaving** and graceful mate/stalemate/insufficient/in-check prose.

## Hard ceilings — what a heuristic fundamentally cannot do

These are not bugs to fix; they are the limits of the approach. Each is a concrete
job for the transformer.

1. **No plans, no "why" (the biggest gap).** It can say *"White is clearly
   better"* but never *"White is better because the d6-pawn is backward and the
   light squares around Black's king are weak; the plan is Nd5 and pressure down
   the c-file."* The structured input contains no notion of plan, target, or
   causation, and no amount of templating invents one.

2. **Fixed phrasing → repetition at scale.** Determinism pins exactly one wording
   per (level, band, side). So *every* clear-edge position says: *"...mark a real
   and lasting pull for White, a genuine edge if not yet a decisive one."* A reader
   skimming twenty positions sees a form letter. Only a generative model varies
   phrasing per position without drifting into lies.

3. **Cannot describe concrete board features.** Passed pawns, the bishop pair, an
   open file, an isolated queen pawn, an outpost, a weak square, opposite-side
   castling, space — none appear in the prose. Some are *collected* by `chess.tag`
   (king safety, pawn structure, passers, center/space) but are opaque
   `FAMILY: key=value` strings; parsing them into fluent, correct English by rule
   is brittle and was deliberately left out. The transformer can ingest them
   directly.

4. **No per-move quality or consequences.** The overall *verdict* can now come
   from a real search (`--eval engine`), which fixes the false-static-verdict
   problem — but the candidate *moves* are still ordered by a cheap static priority
   (captures/checks rank high). It knows *"Bxf7+ is a check,"* not whether it wins,
   loses, or leads anywhere, and outside a found forced mate it does not call any
   move "best." Saying *"Nf3 is the only move"* or narrating a line's consequences
   needs per-move search/PV fed in, not just a single root evaluation.

5. **Phase mislabeling it must faithfully repeat.** `phase` is a crude
   material-ratio bucket, so a castled, fully developed middlegame is reported as
   *"in the opening."* The text engine cannot override a field it is paid to trust.

6. **Shallow connective tissue.** It lists facts (eval, then material, then
   mobility, then candidates). It does not reason *from* one to the next
   ("*because* a queen down, the only path is to complicate"). Narrative causation
   is a transformer strength.

7. **One fixed register and three fixed lengths.** No adapting to audience
   (beginner vs. master), no humor, no naturally variable length.

8. **Deliberately cannot name openings or named motifs.** Correct for faithfulness,
   but a real limitation for readers who would value *"a typical IQP middlegame"* —
   and precisely the kind of thing a trained model can supply (with hallucination
   risk; see the tradeoff below).

## What the T5 architecture should improve, and how

**Role.** A text-to-text transformer that maps the structured feature prompt
(already emitted by `T5PositionDescriptionGenerator.prompt()`:
`describe_position detail=X\nfeatures: {json}\ntext:`) to a natural description.

**The wins T5 unlocks that the heuristic structurally cannot:**

- **Variety** — kills the form-letter repetition (ceiling #2).
- **Plans and "why"** — the headline upgrade (ceiling #1, #6).
- **Concrete-feature prose** from the richer tag/feature set (ceiling #3).
- **Best-move and consequence talk**, *if* we enrich the input with engine
  analysis (ceiling #4).
- **Audience/length/register adaptation** (ceiling #7) and motif naming (ceiling #8).

**Training data — two layers:**

- *Faithfulness floor:* distill from the classical engine. The deterministic
  `--format training-jsonl` export (FEN + structured signals + classical text +
  metadata) already exists; it teaches the model the grounded baseline so it learns
  the facts before it learns flourish.
- *Quality ceiling:* augment targets with LLM-distilled / human-annotation-style
  text **and** engine-analysis enrichment (eval, PV, per-move scores, mate
  distance) so the model can discuss best moves and consequences — facts the
  heuristic never possessed. Feed richer **input** features too: the `chess.tag`
  families and a real phase classifier (this is the "stable input format" that
  `src/chess/tag/SCHEMA.md` and the TODO's Tag Engine Revamp are building toward).

**The central tradeoff to manage.** T5 will hallucinate — wrong openings, invented
threats, a capture "winning a piece" it does not win — which is *exactly* what the
heuristic is guaranteed never to do. So the heuristic does not retire; it becomes
the safety net:

- Keep the classical engine as the **faithfulness anchor / fallback** (it already
  refuses to emit fake output) and as a **fact sheet** the T5 text must not
  contradict.
- Apply the dataset filters the TODO already lists: illegal FENs, contradictory
  tags, hallucinated moves, repeated sentences, excessive length, missing tactical
  facts.
- Add a **grounding/verification pass** at inference: every SAN the model names must
  be a legal candidate; claimed material/eval/side-to-move must match the input;
  on contradiction, regenerate or fall back to the classical text.

**Concrete "needs the transformer" examples** (things tried and impossible to do
faithfully by rule):

- *"White's extra pawn is a protected passed pawn on the queenside; the plan is to
  push it while the king shepherds it home."* — feature synthesis + plan.
- *"Black is lost; the only practical chance is to muddy the waters with ...Qh4 and
  hope White stumbles."* — move-consequence reasoning + register.
- Writing the fiftieth "clearly better" position so it does not read like the
  previous forty-nine. — generative variety without losing the truth.

## Cross-references

- Stable feature/tag input format: `src/chess/tag/SCHEMA.md`.
- Planned work and verification: `TODO.txt` → "Position Description Text".
- The deterministic training export lives in `position describe --format
  training-jsonl` (`application.cli.command.PositionDescribeCommand`).
