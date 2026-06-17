# Position Tag Schema

The position tag schema is the canonical, machine-readable vocabulary that ChessRTK ("crtk") emits to describe a chess position. A *tag* is a single deterministic, grounded statement of fact about a position — its status, material, pawn structure, tactical motifs, candidate moves, checkmate pattern, and more. Tags are produced by `chess.tag.Generator` and surfaced on the command line through `fen tags`, `puzzle tags`, `fen text`, `puzzle text`, and `position describe`. Because every tag is derivable without an external engine and re-running the tagger on the same FEN yields byte-identical output, tags are an ideal stable input for dataset labelling, natural-language generation (the bundled T5 tag-to-text model), the Workbench tag cloud, and any downstream explanation layer. This document is the authoritative reference for the tag families, their fields, and the determinism guarantees that back them.

This is a technical reference for developers extending the tagger and for dataset builders consuming its output. For task-oriented usage see [Command Reference](../wiki/command-reference.md), [Use Cases](../wiki/use-cases.md), and [Workbench](../wiki/workbench.md).

## What produces tags

| Command | Output | Notes |
| --- | --- | --- |
| `fen tags` | tags for one or more FENs / a PGN | `--analyze` enriches with engine candidate moves and PV; `--delta` emits per-move JSONL diffs; `--sequence` treats input as an ordered line |
| `fen tags --pgn FILE --analyze-game` | whole-game analysis JSON | engine-free replay of the parsed game, including per-ply `MOVE_EFFECT`, line-level `LINE`, sideline `VARIATION`, and summary `GAME` tags |
| `puzzle tags` | per-move tags for a puzzle's PVs (JSONL) | expands `--multipv` lines to `--pv-plies` plies and tags every node |
| `fen text` | T5 natural-language summary of `fen tags` | grounded in the tag set, never in invented prose |
| `puzzle text` | T5 summary over a puzzle's PVs | same grounding contract as `fen text` |
| `position describe` | deterministic prose from the same facts | `--engine classical` or `--engine t5` |

The Generator is one component of [the single shared Java chess core](../wiki/architecture.md): the same legal move generator, FEN/SAN/UCI handling, and attack detection feed every tag.

## Design contract

Every tag obeys six rules. They are the reason tags can be trusted as ground truth in a training pipeline.

1. **Deterministic and grounded.** A tag states only what is true of the position. There is no invented chess claim, no prose paragraph, no vague language. Re-running the tagger on the same FEN always produces the same tags in the same order.
2. **One canonical line format.** Each tag is `FAMILY: key=value key=value`. The family is uppercase; fields are space-separated `key=value` pairs. Values containing spaces, quotes, or backslashes are double-quoted with the quote and backslash escaped (for example `name="King's Pawn Game"`). A bare `FAMILY:` with no fields is allowed.
3. **Sorted and de-duplicated.** `chess.tag.Sort` orders tags by family rank, then lexicographically within a family, and removes exact duplicates. `Sort.sort(tags)` is idempotent, so the emitted order is always canonical.
4. **Grounded moves only.** Any concrete move a downstream text generator may quote must appear explicitly in a tag: `CAND: ... move=`, `PV:`, `THREAT: ... move=`, `TACTIC: ... move=`, `IDEA: ... move=`, or `MOVE: only=`. Moves in `CAND`, `THREAT`, and `IDEA` use SAN; static-motif moves and `MOVE: only=` use UCI.
5. **Engine-free derivation.** Every tag is derivable without an external UCI engine. The only internal "engine" inputs allowed are the bundled searchers (`chess.engine.AlphaBeta`, `chess.engine.Mcts`, `chess.engine.MateProver`), `chess.eval.Evaluator`, `chess.eval.See` (static exchange evaluation), and the bundled ECO book (`chess.eco.Encyclopedia`). Searcher-derived tags run under a fixed node/time budget so they stay deterministic and cheap in bulk.
6. **Explanation-friendly.** Where it helps a text model, a tag carries a short grounded `detail="..."` or `note="..."` field stating *why* (for example `note="wins material"`, `detail="hanging black rook e5"`). These are short noun phrases, never sentences with invented evaluation.

> Note on the `--analyze` flag: tags produced *with* `fen tags --analyze` add engine-derived `CAND`, `PV`, and richer `META` evaluation. The static (engine-free) tag set is always reproducible from the FEN alone; the analyze-enriched set depends on the engine, node budget, and PV count you pass, so it is reproducible only when those inputs are held fixed.

## Family order

`chess.tag.Sort` ranks families in this fixed order, and lexicographic order breaks ties within a family:

`FACT` → `META` → `MOVE` → `THREAT` → `CAND` → `PV` → `IDEA` → `TACTIC` → `CHECKMATE` → `PIECE` → `KING` → `PAWN` → `MATERIAL` → `SPACE` → `INITIATIVE` → `DEVELOPMENT` → `MOBILITY` → `OUTPOST` → `ENDGAME` → `OPENING` → `MOVE_EFFECT` → `LINE` → `VARIATION` → `GAME`

The authoritative order lives in `chess.tag.Sort`; the family, key, and value string literals live in `chess.tag.core.Literals`. Register every new identifier there.

## Worked example

`fen tags --fen "6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1"` (a rook-up endgame with mate in one) emits, in canonical order:

```text
FACT: castle_rights=none
FACT: center_control=balanced
FACT: center_state=open
FACT: in_check=none
FACT: status=normal
META: difficulty=very_easy
META: eval_bucket=clear_white
META: eval_cp=578
META: phase=endgame
META: source=engine
META: to_move=white
META: wdl=999/1/0
MOVE: checks=1
MOVE: legal=20
MOVE: mates=1
MOVE: quiet=19
TACTIC: motif=mate_in_1 side=white move=a1a8
PIECE: activity=high_mobility side=white piece=rook square=a1
KING: castled=yes side=white
MATERIAL: balance=white_up_exchange
MATERIAL: imbalance=queenless
SPACE: side=equal
INITIATIVE: side=white
ENDGAME: type=queenless
```

(Output trimmed for readability — the real run also emits the full `PIECE:`, `MATERIAL: piece_count`, and `MOBILITY` rows.)

## Families

### FACT — static board observations

Plain facts read directly off the board.

- `status=normal|check|checkmated|stalemate|insufficient`
- `in_check=white|black|none`
- `castle_rights=KQkq|...|none` (the FEN castling field, or `none`)
- `en_passant=<square>` (only when an en-passant target exists)
- `center_state=open|closed|...`, `center_control=white|black|balanced`
- `puzzle=winning|draw` (only on puzzle inputs)

### META — metadata and evaluation

- `to_move=white|black`, `phase=opening|middlegame|endgame`, `source=engine`
- `eval_cp=<int>` (White-positive centipawns), `eval_bucket=<bucket>`, `mate_in=<n>`, `mated_in=0`, `wdl=<w>/<d>/<l>`, `difficulty=...`
- `eco=<code>`, `opening="<name>"`

Evaluation buckets are conservative, human-readable bands: `equal`, `slight_white`, `clear_white`, `winning_white`, `crushing_white`, and the `*_black` mirror.

On puzzle inputs (`puzzle tags`, `puzzle text`) the META family additionally carries puzzle-scoring keys such as `puzzle_goal`, `puzzle_rating`, `puzzle_difficulty`, `puzzle_difficulty_score`, `puzzle_features="..."`, `puzzle_solution_plies`, `puzzle_variations`, and `puzzle_branch_points`.

> Evaluation, WDL, and difficulty come from crtk's internal evaluators and bounded searchers — not from an external engine. crtk's neural evaluators (NNUE, LC0 CNN, OTIS) are usable, deterministic evaluators; they are **not** bit-exact reimplementations of upstream engines, and the experimental BT4 path is simplified. Treat `eval_cp`, `eval_bucket`, and `wdl` as crtk's own grounded estimates. See [In-House Engine](../wiki/in-house-engine.md) and [LC0](../wiki/lc0.md) for fidelity details.

### MOVE — legal-move facts

Counts and forced-move facts derived from the legal move list.

- `legal`, `captures`, `checks`, `mates`, `promotions`, `castles`, `quiet`, `evasions`, `en_passant`, `underpromotions` (each `=<n>`)
- `only=<uci>`, `forced=true` (when exactly one legal move exists)

### THREAT — what the side to move threatens next

Threats are grounded: each cites a concrete legal move (SAN, double-quoted).

- `type=promote side=<color> severity=immediate square=<sq>` — a legal promotion is available.
- `type=mate side=<color> severity=immediate move="<SAN>"` (mate in one via `play().isCheckmate()`), or `severity=soon move="<SAN>" target="mate_in_N"` for a longer mate proven by `MateProver` within its bound. An immediate mate dominates and suppresses longer mate threats.
- `type=king_attack side=<color> severity=soon move="<SAN>" target="<weakness>"` — a legal check (not mate) into a concrete weak shelter, where `<weakness>` is `open_<file>_file`, `half_open_<file>_file`, or `exposed_king_ring_<n>`. Suppressed when a mate threat already exists.

`type=material` is intentionally omitted from THREAT — it cannot be grounded without search. Material-winning ideas live in `IDEA` and `TACTIC` instead.

### CAND — candidate moves

Emitted only when engine analysis is supplied (`fen tags --analyze`, `puzzle tags`).

- `role=best|alt move=<SAN> eval_cp=<int> note="<reason>"`

`note` is a grounded reason or empty: `checkmate`, `wins material` (a SEE > 0 capture), `promotes`, `check`, or `""`. Example:

```text
CAND: role=best move=Qxf7# eval_cp=100000 note="checkmate"
CAND: role=alt move=Bxf7+ eval_cp=307 note="wins material"
```

### PV — principal variation

A single line of SAN moves from the supplied analysis.

```text
PV: Qxf7#
```

### IDEA — grounded plans

Emitted by `chess.tag.Ideas`. Each idea cites a legal move or a re-derivable board fact; none are speculative.

- `side=<color> type=<type> move=<SAN> detail="<reason>"` for move-bearing ideas, or `side=<color> type=<type> detail="<reason>"` for the structural ones.

Idea types:

- `win_material` — the highest-SEE legal capture with SEE > 0 (`move=` SAN).
- `promote` — a legal promotion move (`move=` SAN).
- `king_safety` — the side's king sits on an open/half-open file with at least one king-ring attacker (no move; `detail` names the open file).
- `space` — the side to move has strictly more non-king, non-pawn pieces deep in the opponent's half than the opponent has in its half (no move).

### TACTIC — tactical motifs

`motif=<id> side=white|black` with motif-specific fields: `move=<uci>`, `piece=<name>`, `square=<square>`, `detail="<concrete detail>"`, `targets=<piece@sq,...>` (fork), `pieces=<p@sq,p@sq>` (battery), and `see=<cp>` (material motifs).

Currently emitted motifs:

| Motif | Description |
| --- | --- |
| `pin` | a piece pinned to its king (`detail=`) |
| `skewer` | a more valuable piece shielding a less valuable one along a line |
| `fork` | one move attacking two or more targets (`move=`, `targets=`) |
| `discovered_attack` | moving a piece reveals a slider's attack |
| `hanging` | an undefended attacked piece (`detail=`) |
| `overload` | a defender guarding more than it can hold |
| `loses_material` | SEE-backed: the side to move can win material via a legal capture; `side=` is the losing side, with `move=<uci> piece= square= see=<cp>` |
| `mate_in_1` | a legal move delivering immediate mate (`move=<uci>`) |
| `mate_in_<n>` | n ≥ 2, proven by `MateProver` (`side=`, `move=<uci>`) |
| `double_check` | a legal move giving check with two pieces at once (`move=<uci>`) |
| `promotion` | a legal queen promotion (`move=<uci>`, `piece=queen`, `square=`) |
| `underpromotion` | a legal under-promotion (`move=<uci>`, `piece=`, `square=`) |
| `rooks_on_7th` | one or two rooks on the enemy second-from-back rank |
| `battery` | aligned heavy/diagonal pieces on one line (`pieces=`) |
| `x_ray` | a slider attacking through one intervening piece |
| `back_rank_weakness` | a king boxed on its back rank by its own pawns |
| `f7_weakness` | an over-attacked f7/f2 square in front of the defending king |
| `decoy` | a forcing sacrifice lures an enemy piece, with a MateProver/SEE-proven follow-up |

Examples:

```text
TACTIC: motif=mate_in_1 side=white move=a1a8
TACTIC: motif=fork side=white move=d5c7 piece=knight square=c7 targets=rook@a8,...
TACTIC: motif=hanging side=black detail="hanging black rook e5"
TACTIC: motif=loses_material side=black move=e1e5 piece=rook square=e5 see=500
TACTIC: motif=promotion side=white move=a7a8q piece=queen square=a8
```

### CHECKMATE — mate identity

Emitted only for checkmated positions. Geometry detectors run on the already-mated board in `chess.tag.Checkmate`.

- `winner=white|black`, `defender=white|black`
- `delivery=pawn|knight|bishop|rook|queen|king|multiple`
- `pattern=<named net>` (zero or more — a position may match several refinements)

Currently emitted patterns: `back_rank_mate`, `smothered_mate`, `corner_mate`, `support_mate`, `double_check`, `arabian_mate`, `epaulette_mate`, `anastasia_mate`, `david_and_goliath_mate`, `damiano_mate`, `scholars_mate`, `swallows_tail_mate`, `dovetail_mate`, `hook_mate`, `opera_mate`, `lawnmower_mate`, `blackburne_mate`, `greco_mate`, `kill_box_mate`, `reti_mate`, `anderssen_mate`, `mayet_mate`.

Named patterns are refinements that co-exist with the generic geometry they specialize. A Scholar's mate, for example, emits both:

```text
CHECKMATE: defender=black
CHECKMATE: delivery=queen
CHECKMATE: pattern=scholars_mate
CHECKMATE: pattern=support_mate
CHECKMATE: winner=white
```

Each named detector ships a golden fixture and is cross-collision-probed against the others. A simple mate may match no named pattern at all; in that case only `winner`, `defender`, and `delivery` are emitted.

### PIECE — piece roles and activity

- `activity=high_mobility|low_mobility|pin|restricted|trapped|... side= piece= square=`
- `tier=<tier> side= piece= square=`, `extreme=strongest|weakest|strongest_white|weakest_black|... side= piece= square=`

### KING — king safety

- `castled=yes|no side=`
- `shelter=pawns_intact|weakened|open side=`
- `safety=very_safe|safe|unsafe|very_unsafe side=`

### PAWN — pawn structure

- `structure=doubled|isolated|backward|passed|connected_passed side=` with `square=` or `file=` (`squares=` for `connected_passed`)
- `majority=kingside|queenside|center side=`
- `islands side= count=`

### MATERIAL — material balance

- `balance=equal|white_up_<unit>|black_up_<unit>` where `<unit>` is `pawn`, `minor`, `exchange`, or `queen`
- `imbalance=bishop_pair_white|bishop_pair_black|rookless|queenless|opposite_color_bishops|same_color_bishops|...`
- `piece_count side= piece= count=`

### SPACE, INITIATIVE, DEVELOPMENT, MOBILITY, OUTPOST

- `SPACE: side=`, `SPACE: center_control side= count=`
- `INITIATIVE: side=`, `INITIATIVE: forcing_moves side= count=`
- `DEVELOPMENT: side=|equal`, `DEVELOPMENT: undeveloped side= piece= square=`, `DEVELOPMENT: king_uncastled side=`
- `MOBILITY: side=`, `MOBILITY: piece= side= square= moves=`
- `OUTPOST: side= square= piece=`

### ENDGAME, OPENING

- `ENDGAME: type=rook|minor|queenless|opposite_bishops|...`
- `OPENING: eco=<code>`, `OPENING: name="<name>"` (inherited from the parent ECO line when the child has no exact book match)

### Dynamic game-analysis families

These families are emitted by the whole-game analysis path, not by a single
static `Generator.tags(position)` call. Use:

```bash
crtk fen tags --pgn game.pgn --analyze-game
```

The command parses each game, replays the legal mainline and sidelines through
the same chess core, and emits one JSON object per game. The fields below are
still deterministic tag strings; the JSON wrapper only groups them by ply,
line, variation, and game summary.

- `MOVE_EFFECT: san=<SAN> type=checkmate|check|capture|quiet` is emitted for
  each replayed mainline ply. Additional `creates="..."` and `removes="..."`
  forms cite tactical, threat, or checkmate motifs that appeared or disappeared
  between the parent and child positions.
- `LINE: motif=<id> ... line="<SAN ...>"` describes multi-move motifs grounded
  by replaying a legal line. Current motifs include `forcing`, `combination`,
  `sacrifice`, `perpetual_check`, and `deflection`.
- `VARIATION: branch_ply=<n> length=<n> line="<SAN ...>"` identifies a replayed
  PGN sideline. `VARIATION: tactic_shared=<motif> branch_ply=<n> count=<n>
  detail="<payload>"` reports motifs shared by sibling sidelines from the same
  branch position.
- `GAME: phase_transition=<from>-><to> ply=<n> move=<SAN>`, `GAME: eco=<code>`,
  `GAME: opening="<name>"`, and `GAME: result_cause=<cause>` summarize facts
  grounded in the replayed game and the static tags of its positions.

## Per-move deltas (`--delta`)

`fen tags --sequence --delta` emits one JSONL object per node in an ordered line. Each object carries the node's `fen`, its full sorted `tags` array, and a `delta` describing what changed versus the previous node:

```text
{"index":1,"parent":"...","fen":"...","move_uci":null,"move_san":null,
 "tags":[...],
 "delta":{
   "added":["FACT: en_passant=e3", "PIECE: tier=very_strong side=white piece=pawn square=e4"],
   "removed":["PIECE: tier=strong side=white piece=pawn square=e2"],
   "changed":[{"key":"META:eval_cp","from":"META: eval_cp=8","to":"META: eval_cp=-100"}]
 }}
```

`added` and `removed` are tag strings; `changed` pairs a stable `key` with the `from` and `to` tag strings. The first node carries `delta:null`. This makes a move's *effect on the position* a first-class, diffable object — ideal for move-annotation datasets and explanation training.

## Internal primitives (not tags)

`chess.eval.See` — Static Exchange Evaluation. `See.see(position, move)` returns the net material (centipawns, from the mover's perspective) of a capture after recursive least-valuable-attacker recaptures with standing-pat resolution; it handles x-ray and battery reveals, en passant, and promotion captures. Pins are ignored, per standard SEE. `See.seeGreaterEqual(position, move, threshold)` is the cheap boolean form. SEE is what makes the `hanging`, `loses_material`, and `win_material` tags sound rather than mere attacker-count heuristics.

## Extending the schema

To add a new `pattern=` or `motif=` value:

1. Register the id string in `chess.tag.core.Literals` and, if it is a new family, in `chess.tag.Sort`.
2. Build a golden fixture under `testdata/tags/*.tsv` and/or a `TaggingRegressionTest` case.
3. Cross-collision-probe the new detector against the existing detectors so it does not silently fire on positions another pattern already owns.
4. Keep it engine-free, or bound it behind a fixed-budget internal searcher with explicit provenance. SEE-back anything that asserts material.

Roadmap vocabulary (checkmate patterns and tactical motifs still to add) is tracked in `TODO.txt` under "Tag Engine Revamp". Several roadmap motifs are dynamic rather than single static frames and may never reduce to a static detector; statically confirmable ones land first.

## Related pages

- [Command Reference](../wiki/command-reference.md) — full flags for `fen tags`, `puzzle tags`, `fen text`, `puzzle text`, `position describe`
- [Architecture](../wiki/architecture.md) — the single shared chess core the tagger sits on
- [Use Cases](../wiki/use-cases.md) — building tagged training datasets
- [In-House Engine](../wiki/in-house-engine.md) and [LC0](../wiki/lc0.md) — the evaluators behind `META` eval fields and their fidelity
- [Glossary](../wiki/glossary.md) — defined terms (SEE, WDL, motif, perft)
