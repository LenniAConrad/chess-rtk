# Tagger Evolution: Move / Line / Variation / Game Analysis

Design plan for evolving `chess.tag` from a **static position tagger** into a
**dynamic, game-aware analysis tagger** that can drive full game annotation
(multi-move tactics, multi-variation tactics, and whole-game narratives).

Status: ALL FOUR LAYERS IMPLEMENTED (deterministic, engine-free, golden-tested).
Layer 1 `chess.tag.MoveEffect`, Layer 2 `chess.tag.game.LineAnalyzer`, Layer 3
`chess.tag.game.VariationAnalyzer` (incl. the `tactic_shared` multi-variation
tactic), Layer 4 `chess.tag.game.GameAnalyzer` (with a unified `analyze(Game)`
entry point). Exposed on the CLI via `fen tags --pgn <file> --analyze-game`,
which emits one whole-game analysis JSON object per game (per-ply MOVE_EFFECT,
LINE tactics, VARIATION sidelines, and a GAME summary). The per-layer STATUS
banners below record each layer's specifics. Companion to `tag/SCHEMA.md` (the
static position schema); the dynamic families documented here are intentionally
kept in this doc rather than folded into SCHEMA.md.

---

## 1. The core insight

The current tagger answers **"what is true of this position."** Full game
analysis needs three more questions answered:

1. **MOVE** — what did *this one move* change? (parent -> child)
2. **LINE** — what does *this sequence of moves* accomplish? (a PV / variation / game segment)
3. **GAME** — what is the *story* of the whole game?

The decisive realization: **the multi-move tactics we could not ground
statically become groundable the moment we have the move sequence.** A
deflection, decoy, zwischenzug, windmill, or combination is not something we must
*search* for — when the moves are given (a played game, a PV, an annotated
variation), we simply **replay the line and observe** the motif. The line *is*
the proof. This is exactly why those motifs were rejected in the static batches
and why they succeed here.

Everything below is ADDITIVE: new tag families + a sequence/game orchestrator,
built on the existing static tagger. No teardown.

---

## 2. What already exists (build on, do not rebuild)

- `chess.tag.Delta` — diffs two tag sets: `diff(parentTags, childTags)` ->
  `added` / `removed` / `changed`, with `toJson()`. **This is the seed of every
  move-transition tag.**
- `chess.core.MoveInference` — `notation(parent, position)` yields the SAN/UCI of
  the move connecting two positions; `uniqueMove(...)`.
- `chess.struct.Game` / `chess.struct.Record` — full game + per-position records
  with parent links and variation trees (sidelines).
- `tags` CLI already has `--delta`, `--sequence`, `--pgn`, `--mainline`,
  `--sidelines` — the plumbing to feed sequences/games in is already present
  (`TagsCommand` builds `Record` chains with parents and emits per-position
  delta JSON).
- The grounded static tagger: 22 checkmate patterns, SEE (`chess.eval.See`),
  IDEA / THREAT / CAND families, tactic motifs (pin/skewer/fork/discovered/
  hanging/overload/loses_material/rooks_on_7th/battery/x_ray/back_rank_weakness/
  f7_weakness/decoy/mate_in_N/double_check).
- `chess.engine.MateProver` (bounded mate proof) and `chess.engine.AlphaBeta`
  (SEE + repetition search) for the OPTIONAL engine layer.

So the new work is mostly: a `Generator` overload that accepts a parent, two new
orchestrator classes, and four new tag families.

---

## 3. Engine policy (decided)

**Deterministic core + optional engine layer.**

- The MOVE / LINE / VARIATION / GAME *facts* are engine-free: they come from
  tag-set deltas, replaying given moves, SEE on the actual capture sequences, and
  forcing-move counts. These are always emitted and always grounded.
- An OPTIONAL engine layer adds graded *judgments* (`quality=blunder`,
  `eval_swing=-3.1`, `turning_point`), clearly marked as engine-sourced via a
  `source=engine` field or a distinct key prefix.
- Rationale: preserves the hard grounding guarantee (a downstream T5/explanation
  model can trust every deterministic tag as fact, and treat engine tags as
  opinion). Analysis degrades gracefully with no engine — you still get the full
  move/line/game structure, just without the graded verdicts.

Every engine-derived tag MUST be bounded (fixed node/time budget) for
determinism, as with the existing `MateProver` usage.

---

## 4. Layer 1 — MOVE tags ("what this one move did")

> **STATUS: Layer 1 IMPLEMENTED (deterministic core).** `chess.tag.MoveEffect.effects(parent, child)`
> emits `MOVE_EFFECT: san=<san> type=checkmate|check|capture|quiet` plus factual
> `creates="<motif>[ side=<side>]"` / `removes="..."` from the parent->child tag-set
> delta (TACTIC/THREAT/CHECKMATE families). Engine-free, grounded; good/bad polarity is
> deferred to the explanation layer (motif side polarity is non-uniform). `removes` is
> suppressed on terminal (checkmate) children. Golden test: testMoveEffect. Optional
> engine `quality=` grade still TODO. Next: Layer 2 LineAnalyzer.


A new family `MOVE_EFFECT:` describing the transition parent -> child. Produced
when the tagger is given BOTH a parent and a child position (new `Generator`
overload, see §8). Each effect is `tags(child) Δ tags(parent)` plus the played
move — pure observation, no guessing.

Proposed fields (move in SAN):
- `MOVE_EFFECT: san=Nxe5 type=capture captured=pawn@e5 see=+100` — what it took,
  SEE-graded (true material outcome, not nominal).
- `type=quiet|capture|check|castle|promotion|en_passant`
- `creates="fork@..."` — a tactic present in the child but NOT the parent
  (Delta added, restricted to the TACTIC family).
- `resolves="pin@e2"` — a tactic/threat present in the parent but gone in the
  child (Delta removed).
- `allows="mate_in_2"` / `allows="fork@..."` — a tactic now available to the
  OPPONENT that was not before. **This is how blunders are detected without an
  engine label** (the move handed the opponent a grounded tactic).
- `develops=Nf3 | gains_tempo=true | improves=rook@d1->d7`
- `quality=best|good|inaccuracy|mistake|blunder source=engine` — OPTIONAL,
  engine-only, from eval swing across the move.

Grounding: `Delta` already computes the set math; restrict to TACTIC/THREAT/
CHECKMATE families and attribute by side to derive creates/resolves/allows.

Tests: golden fixtures of (parent FEN, move) -> expected MOVE_EFFECT tags,
including a known blunder that `allows=` a mate, and a quiet developing move.

---

## 5. Layer 2 — LINE tags ("what this sequence accomplishes")

> **STATUS: Layer 2 IMPLEMENTED (deterministic, replay-grounded).** `chess.tag.game.LineAnalyzer.tags(Position start, short[] moves)` replays a move
> sequence (validating legality each ply; stops gracefully on an illegal move) and emits
> `LINE: motif=combination` (forcing line ending in mate or >300cp net, length+nets+outcome),
> `motif=forcing` (run of consecutive forcing plies), `motif=sacrifice` (material dips then
> recoups/mates), `motif=perpetual_check` (checks + FEN-key repetition), and a conservative
> `motif=deflection`. Every tag is grounded in the replay itself (end.isCheckmate / SEE / > position-key repetition) — the line is the proof, no search. Engine-free. Golden test:
> testLineAnalyzer. Next: Layer 3 VARIATION (LineAnalyzer over each sideline) + Layer 4 GAME.

A `LineAnalyzer` taking `Position start + short[] moves` (a PV, a variation, or a
game segment). It replays the line, runs the static tagger + Delta at each ply,
and pattern-matches the deltas ACROSS the line. **This revives the rejected
multi-move tactics by observation.**

Proposed `LINE:` tags:
- `LINE: motif=combination length=4 nets=+5 line="Rxh7+ Kxh7 Qh5+ Kg8 Qh8#"` —
  a forcing sequence with a proven material/mate payoff (walk it; sum SEE; check
  terminal `isCheckmate`).
- `LINE: motif=deflection square=e8 ...` — move N forces an enemy piece off a
  defended square; move N+1 exploits that exact square. Provable from the moves.
- `LINE: motif=decoy | zwischenzug | windmill | clearance | interference` — each
  defined as a concrete delta pattern across consecutive plies (see per-motif
  notes when implemented). No search; the played line is the witness.
- `LINE: forcing=true count=5` — run of consecutive checks/captures/credible
  threats.
- `LINE: sacrifice=Qh5 recouped_in=3 net=+2` — material given then regained
  (SEE-tracked across the line).
- `LINE: motif=perpetual_check | motif=repetition | motif=fortress_hint` — draw
  mechanisms that require the move loop (impossible to see statically).

Mechanism: bounded, deterministic replay; reuses `Generator` + `Delta` per ply.
Tests: golden fixtures of famous lines (a known windmill, a known smothered-mate
combination, a perpetual) -> expected LINE tags.

---

## 6. Layer 3 — VARIATION tags ("what if" / multi-line)

> **STATUS: Layer 3 IMPLEMENTED (deterministic, replay-grounded).**
> `chess.tag.game.VariationAnalyzer.analyze(Game)` walks the mainline plus every
> root and nested `Game.Node` variation, resolves each SAN to a legal move with
> `chess.tag.game.SanResolver` (the move core has no SAN parser — it matches
> generated `SAN.toAlgebraic` over the legal moves, tolerating +/#/!/? glyphs),
> and runs `LineAnalyzer` over the full line (mainline prefix + sideline). Emits
> one `VARIATION: branch_ply=<n> length=<l> line="..."` header per sideline,
> each followed by that line's `LINE:` tags. It also emits the grounded
> multi-variation tactic `VARIATION: tactic_shared=<motif> branch_ply=<n>
> count=<k> detail="..."` when a motif recurs across ≥ 2 sibling sidelines (see
> below). Branches that cannot be legally replayed are skipped, never guessed.
> `Result.toJson()` / `Result.arrayJson()` serialize the variations for embedding
> (`toJson` also carries `sharedTactics`). Golden tests: testVariationAnalyzer,
> testGameAnalyzerFromGame, testTacticShared.

Built on Layer 2, applied to the variation tree (mainline + sidelines from PGN
parsing). The `Game`/`Record` variation tree already holds the structure.

Implemented `VARIATION:` tags:
- `VARIATION: branch_ply=<n> length=<l> line="<san> ..."` — followed by the
  sideline's grounded `LINE:` tags.
- `VARIATION: tactic_shared=<motif> branch_ply=<n> count=<k> detail="..."` — the
  "multi-variation tactic": a tactical motif appearing in the **divergent**
  portion of `k` (≥ 2) sibling sidelines that diverge from the **same** branch
  position. Sound by construction: siblings are grouped by the exact branch
  position (4-field FEN) AND the SAN path reaching it (never the ply integer);
  motifs already present in the shared mainline prefix are subtracted (so a
  prefix tactic cannot masquerade as shared); the benefiting side is folded into
  the compared descriptor (so opposite-polarity motifs never merge); siblings are
  counted distinctly by their first divergent SAN; only fully-replayed siblings
  count. `<motif>` ∈ {combination, sacrifice, perpetual_check, forcing,
  deflection}; `detail` carries the matched payload (e.g.
  `outcome=mate beneficiary=white`, `piece=N square=d4 beneficiary=white`).
  Engine-free — every input is an already-replay-proven `LINE:` string.

Deferred (each needs more cross-sibling logic or an engine — not yet groundable
here without guessing):
- `refutes="<the move it punishes>"`, `only_move=true`,
  `transposition_to="..."`, `eval=+2.3 source=engine`.

---

## 7. Layer 4 — GAME tags ("the story of the whole game")

> **STATUS: Layer 4 IMPLEMENTED (deterministic).** `chess.tag.game.GameAnalyzer.analyze(Position start, short[] moves)` replays the mainline
> and returns a structured `Analysis`: per-ply `MOVE_EFFECT` tags, whole-game `LINE` tactics
> (via LineAnalyzer over the legally-played prefix), and `GAME:` summary tags — opening/ECO
> from the book, `phase_transition=` on META phase change, `result_cause=checkmate pattern=...`
> or `=stalemate`. `Analysis.toJson()` gives nested JSON (gameTags, lines, moves[ply/san/
> effects]) for the explanation/T5 layer. Takes (start, moves) — same shape as LineAnalyzer,
> independent of any PGN/Node model (PGN->moves is a separate concern). Engine-free, grounded
> by replay. Golden test: testGameAnalyzer (Scholar's mate -> result_cause=checkmate
> pattern=scholars_mate; note the bare start position emits no eco/opening tag —
> ECO keys on opening positions, not the initial array). A unified `analyze(Game)`
> overload resolves the game's SAN mainline via `SanResolver`, runs the per-ply /
> line / summary analysis, and attaches the `VariationAnalyzer` result
> (`Analysis.variations`, also emitted in `toJson()`). Golden test:
> testGameAnalyzerFromGame. All four layers (1, 2, 3, 4) are implemented and green.


## 8. Cross-cutting work

1. **`Generator` parent overload.** Add `tags(Position parent, Position child,
   ...)` (or a `transitionTags(parent, child)` helper) so MOVE_EFFECT can be
   produced in one pass. `--sequence` in `TagsCommand` already threads parents;
   wire it through.
2. **New classes** `chess.tag.game.LineAnalyzer` and `chess.tag.game.GameAnalyzer`
   (new subpackage to keep position-tagging clean), plus a `MoveEffect` helper in
   `chess.tag`.
3. **Families + Sort + Literals.** Register `MOVE_EFFECT`, `LINE`, `VARIATION`,
   `GAME` in `chess.tag.core.Literals` and place them in `chess.tag.Sort` order.
   Document in `tag/SCHEMA.md`.
4. **Provenance discipline.** Keep deterministic vs engine-sourced strictly
   separated (a `source=engine` field on graded tags). The T5/explanation layer
   must know fact vs judgment.
5. **PREREQUISITE cleanup** — the `FACT: tactical="..."` -> `TACTIC: motif=...`
   string round-trip in `Generator`/`GeneratorSupport`. MOVE/LINE lean heavily on
   tactic emission and Delta over tactic tags; do this fragile cleanup FIRST so
   the dynamic layers build on structured tags, not a re-parsed string.
6. **JSON output mode** for nested game analysis (`GameAnalysis.toJson`).

---

## 9. Suggested sequencing (each independently shippable + verified)

1. **Round-trip cleanup** (prerequisite; makes tactics structured for Delta).
2. **MOVE_EFFECT** (Layer 1) — highest value, smallest, reuses `Delta`; unlocks
   threat-creation / blunder detection immediately.
3. **LineAnalyzer + multi-move LINE tactics** (Layer 2) — revives deflection /
   zwischenzug / windmill / combination by replay. The biggest payoff.
4. **VARIATION** (Layer 3) — multi-line / multi-variation tactics.
5. **GAME** (Layer 4) — the capstone for the game-analysis feature; add
   `GameAnalysis.toJson`.

Each layer uses the established construct -> adversarially-verify -> golden-fixture
loop, with fixtures now being KNOWN annotated games/lines (a famous windmill, a
Najdorf miniature, a perpetual) rather than single FENs. See
`feedback_verified_detector_workflow` in memory.

---

## 10. What this enables (the end goal)

A `game analyze` capability that, for any PGN, emits a grounded, structured
annotation: per-move effects, the tactical/strategic lines, the variations that
refute alternatives, and the whole-game narrative — every fact grounded in the
actual moves, with engine judgments clearly labelled. That is the input format a
ChessGPT-style explanation layer (or the T5 model) turns into human prose without
having to guess any chess fact.
