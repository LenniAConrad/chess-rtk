# Generator Implementation Plan

This document is the implementation plan for completing ChessRTK's position,
move, tactic, and puzzle-theme tags.

The goal is not just to add many strings. The goal is to build a deterministic,
auditable tagging layer that can serve three consumers:

- CLI users running `fen tags`, `puzzle tags`, `record tag-stats`, and `record analysis-delta`.
- Dataset builders using tags as labels, filters, or training text inputs.
- LLM/agent workflows that need compact, stable chess facts instead of prose.

## Current State

There is one public tag implementation:

- `chess.tag`: canonical tag pipeline used by CLI, GUI, records, tests, and
  engine analysis workflows.

No duplicate tag pipeline is part of the documented CLI surface.

## Guiding Rules

1. Tags must be deterministic for the same position, analysis input, and config.
2. Heuristic tags must not require an engine, neural model, or network access.
3. Engine-derived tags must be clearly separated and only emitted when analysis
   was requested or supplied.
4. Tags must be stable strings. Renames need migration notes because users may
   train models or write filters against them.
5. Prefer exact board predicates over vague names. A tag should explain why it
   was emitted or encode enough fields to reconstruct the reason.
6. Avoid duplicate concepts. Chess.com, Lichess, and ChessFox labels should map
   into canonical tags where possible.
7. Each additional tag family needs positive and negative regression positions.
8. Keep tag generation allocation-conscious. It will be used in batch pipelines.

## Canonical Tag Shape

Use the existing family style:

```text
FAMILY: key=value key=value
```

Examples:

```text
META: to_move=white
FACT: status=normal
MATERIAL: balance=white
PAWN: structure=passed side=white square=e7
TACTIC: motif=pin side=white piece=bishop square=e2 by=black_rook@e8
CHECKMATE: pattern=back_rank_mate
```

Keep the canonical internal output structured enough for `Line`, `Identity`,
and `Sort`; compatibility converters should live outside the tag pipeline.

## Tag Families

The complete family set should be:

- `META`: side to move, phase, source, evaluator metadata, difficulty, ECO/opening metadata.
- `FACT`: status, check state, checkers, castling, en-passant, legal state facts.
- `MATERIAL`: material totals, imbalance, piece counts, bishop pair, exchange state.
- `MOVE`: legal move counts and one-ply tactical move counts.
- `CAND`: candidate moves from engine or bounded tactical search.
- `PV`: principal variation tags from engine analysis.
- `IDEA`: concise strategic idea tags derived from facts or analysis.
- `THREAT`: threats from engine or bounded reply search.
- `KING`: king safety, exposure, flight squares, pawn shield, back-rank issues.
- `PAWN`: pawn structure and promotion facts.
- `PIECE`: piece activity, outposts, trapped pieces, hanging pieces, overloaded defenders.
- `TACTIC`: tactical motifs and forcing mechanisms.
- `CHECKMATE`: checkmate status and named mate patterns.
- `ENDGAME`: material endgame classes and practical endgame themes.
- `OPENING`: ECO code and opening name.
- `SPACE`: center/side space information.
- `DEVELOPMENT`: undeveloped pieces and development lead.
- `MOBILITY`: side and piece mobility.
- `INITIATIVE`: forcing-move and tempo indicators.

`Sort` and `Identity` must understand every family and every identity-relevant
field. Any additional family gets added there before it is exposed by CLI commands.

## Target Architecture

### Package Layout

Keep `chess.tag` as the public API. Split implementation into focused modules:

```text
src/chess/tag/
  Generator.java              public facade and orchestration
  Context.java                shared immutable/scratch context
  Emitter.java                string builder / canonical field emitter
  Identity.java               semantic identity keys
  Sort.java                   stable family-aware sort/dedupe
  Line.java                   tag parser
  core/
    Literals.java
    Text.java
  fact/
    Facts.java
    MoveFacts.java
  material/
    Material.java
    Endgame.java
  pawn/
    PawnStructure.java
    Promotion.java
  piece/
    PieceActivity.java
    PiecePressure.java
    Outpost.java
  position/
    Castling.java
    CenterSpace.java
    Development.java
    KingSafety.java
    Mobility.java
    Opening.java
  tactical/
    Tactical.java
    AttackMap.java
    MatePatterns.java
    MotifDetectors.java
  move/
    Move.java
    Mainline.java
    Sequence.java
  eval/
    Evaluation.java
    Difficulty.java
```

The exact filenames can change, but the rule is simple: one large `Generator`
class should not accumulate every motif.

### Tag Context

Create a reusable context object for one position:

- `Position position`
- `byte[] board`
- `MoveList legalMoves`
- side to move
- king squares
- material counts and material phase
- attack maps by side
- defenders by square
- attackers by square
- pinned pieces by side
- checkers
- pawn file counts
- optional `Analysis`
- optional `Evaluator`

The context should compute expensive values lazily and cache them for all
detectors. This avoids repeated legal move generation and repeated attack scans.

### Tag Emitter

Introduce a small helper for canonical tags:

```java
Emitter.tag("PAWN")
    .field("structure", "passed")
    .field("side", "white")
    .field("square", "e7")
    .emit(tags);
```

This reduces typo risk and makes field ordering consistent. It also makes it
easy to enforce escaping rules if a field value ever contains spaces.

### Detector Contract

Each detector should follow this shape:

```java
interface Detector {
    void addTags(Context ctx, List<String> tags);
}
```

No detector should call the engine or load config unless it is explicitly an
engine/config detector. Heuristic detectors should be pure functions over the
position and cached context.

## Implementation Phases

### Phase 0: Stabilize The Contract

Deliverables:

- Document canonical tag grammar in `wiki/piece-tags.md` or a dedicated tag reference.
- Add `Emitter`.
- Add `Context`.
- Add `Detector` or an equivalent internal pattern.
- Update `Sort.FAMILY_ORDER` with the final family set.
- Update `Identity` to handle all planned family keys gracefully.
- Add a no-engine golden test harness for tag fixtures.

Definition of done:

- Existing `fen tags` output is unchanged except for intentional normalizations.
- `TaggingRegressionTest` still passes.
- Parser tests prove every planned family can be parsed and sorted.

### Phase 1: Canonical Tag Consolidation

Keep all additional detectors in `chess.tag` modules.

Port first:

- `META: to_move`
- `META: phase`
- `FACT: status`
- `FACT: in_check`
- `FACT: castling`
- `FACT: en_passant`
- `FACT: checker`
- `MATERIAL: cp_white`
- `MATERIAL: cp_black`
- `MATERIAL: balance`
- `MATERIAL: queens`
- `MATERIAL: rooks`
- `MATERIAL: bishop_pair`
- `MOVE: legal`
- `MOVE: captures`
- `MOVE: checks`
- `MOVE: mates`
- `MOVE: promotions`
- `MOVE: castles`
- `KING: attackers`
- `KING: exposed`
- `PAWN: doubled`
- `PAWN: isolated`
- `PAWN: advanced`
- `PAWN: promotion_ready`
- `PAWN: passed`
- `TACTIC: pin`
- `TACTIC: hanging`
- `TACTIC: fork`
- `CHECKMATE: winner`
- `CHECKMATE: defender`
- `CHECKMATE: delivery`
- `CHECKMATE: pattern=double_check`
- `CHECKMATE: pattern=back_rank_mate`
- `CHECKMATE: pattern=smothered_mate`
- `CHECKMATE: pattern=support_mate`
- `CHECKMATE: pattern=corner_mate`

Definition of done:

- `chess.tag.Generator.tags(position)` covers the fixture categories used by the
  regression tests.
- No duplicate tag package exists in `src`.

### Phase 2: Core Facts And Status

Add robust position-state facts:

| Tag | Meaning | Detector |
| --- | --- | --- |
| `FACT: status=normal` | legal non-terminal position | facts |
| `FACT: status=checkmate` | side to move has no legal move and is in check | facts |
| `FACT: status=stalemate` | side to move has no legal move and is not in check | facts |
| `FACT: in_check=white|black|none` | checked side | facts |
| `FACT: checker=<side>_<piece>@<square>` | checking piece | facts |
| `FACT: castling=KQkq|-` | FEN castling rights | castling |
| `FACT: castled side=<side> flank=<king|queen>` | king has castled inferred from structure where safe | castling |
| `FACT: en_passant=<square>` | current en-passant square | facts |
| `FACT: halfmove_clock=<n>` | optional draw clock metadata | facts |
| `FACT: fullmove=<n>` | current fullmove number | facts |
| `FACT: chess960=true` | Chess960 castling metadata is active | facts |

Notes:

- Do not emit noisy clock tags by default if they hurt tag stability. Consider a
  `--include-counters` flag later.
- `castled` inference should be conservative. If uncertain, omit it.

### Phase 3: Material And Imbalance Tags

Complete material facts:

| Tag | Meaning |
| --- | --- |
| `MATERIAL: cp_white=<n>` | White material in centipawns |
| `MATERIAL: cp_black=<n>` | Black material in centipawns |
| `MATERIAL: balance=white|black|equal` | material leader |
| `MATERIAL: imbalance=<minor|rook|queen|large>` | coarse imbalance |
| `MATERIAL: piece_count side=<side> piece=<piece> count=<n>` | exact piece counts |
| `MATERIAL: queens=white|black|both|none` | queen presence |
| `MATERIAL: rooks=white|black|both|none` | rook presence |
| `MATERIAL: bishop_pair=white|black|both` | bishop pair |
| `MATERIAL: opposite_bishops=true` | opposite-colored bishops with no same-color pair |
| `MATERIAL: exchange side=<side>` | exchange-up side |
| `MATERIAL: minor_exchange side=<side>` | bishop/knight imbalance |
| `MATERIAL: bare_king side=<side>` | no non-king material |
| `MATERIAL: insufficient=true` | conservative insufficient mating material |

Implementation details:

- Material code should use `Position.pieceCount`-style helpers or bitboards,
  not repeated board scans in every detector.
- For `insufficient`, keep the rule conservative:
  - K vs K
  - K+B vs K
  - K+N vs K
  - K+B vs K+B with bishops on same color, if implemented confidently

### Phase 4: Move Facts And Forcing Move Counts

Add move-level aggregate facts:

| Tag | Meaning |
| --- | --- |
| `MOVE: legal=<n>` | legal moves |
| `MOVE: captures=<n>` | legal captures |
| `MOVE: checks=<n>` | legal checking moves |
| `MOVE: mates=<n>` | legal mate-in-one moves |
| `MOVE: promotions=<n>` | legal promotions |
| `MOVE: castles=<n>` | legal castling moves |
| `MOVE: quiet=<n>` | legal non-capture non-check moves |
| `MOVE: only=<uci>` | only legal move |
| `MOVE: forced=true` | only one legal move |
| `MOVE: evasions=<n>` | legal replies while in check |
| `MOVE: en_passant=<n>` | legal en-passant captures |
| `MOVE: underpromotions=<n>` | legal non-queen promotions |

Implementation details:

- Use one `MoveList` generated in `Context`.
- Use `Position.State` and `play/undo` for low-allocation move consequence checks.
- Do not copy the position once per move in hot paths.

### Phase 5: Pawn Structure

Add complete pawn themes:

| Tag | Meaning |
| --- | --- |
| `PAWN: structure=doubled side=<side> file=<file> count=<n>` | doubled pawns |
| `PAWN: structure=tripled side=<side> file=<file> count=<n>` | tripled pawns |
| `PAWN: structure=isolated side=<side> file=<file>` | isolated pawn file |
| `PAWN: structure=passed side=<side> square=<square>` | passed pawn |
| `PAWN: structure=protected_passed side=<side> square=<square>` | passed pawn defended by pawn/piece |
| `PAWN: structure=connected_passed side=<side> squares=<a,b>` | adjacent passed pawns |
| `PAWN: structure=candidate_passed side=<side> square=<square>` | candidate passer |
| `PAWN: structure=backward side=<side> square=<square>` | backward pawn |
| `PAWN: structure=advanced side=<side> square=<square>` | advanced pawn |
| `PAWN: structure=promotion_ready side=<side> square=<square>` | pawn on 7th/2nd |
| `PAWN: structure=chain side=<side> base=<square> head=<square>` | pawn chain |
| `PAWN: structure=phalanx side=<side> squares=<a,b>` | side-by-side pawns |
| `PAWN: structure=hanging side=<side> files=<files>` | hanging pawns |
| `PAWN: islands side=<side> count=<n>` | pawn islands |
| `PAWN: majority side=<side> flank=<queen|king|center>` | pawn majority |
| `PAWN: lever side=<side> square=<square> target=<square>` | pawn break lever |
| `PAWN: ram side=<side> square=<square> target=<square>` | locked opposing pawns |
| `PAWN: underpromotion_available side=<side> move=<uci>` | underpromotion exists |

Implementation details:

- Build file/rank pawn masks once per side.
- Distinguish structural pawn tags from move tags. A pawn on the 7th rank gets
  `promotion_ready`; a legal promotion move gets `MOVE: promotions`.
- Keep candidate-passer rules conservative until tests are strong.

### Phase 6: King Safety

Add king safety facts and themes:

| Tag | Meaning |
| --- | --- |
| `KING: side=<side> attackers=<n>` | attackers on king square |
| `KING: exposed side=<side>` | low cover and attacked nearby squares |
| `KING: vulnerable side=<side>` | high attack pressure or weak cover |
| `KING: flight_squares side=<side> count=<n>` | legal-ish escape squares |
| `KING: no_flight side=<side>` | no safe adjacent escape squares |
| `KING: back_rank_weak side=<side>` | back-rank mate risk |
| `KING: pawn_shield side=<side> quality=<good|thin|broken|none>` | pawn cover |
| `KING: open_file_near side=<side> file=<file>` | open file near king |
| `KING: diagonal_weakness side=<side> diagonal=<name>` | diagonal pressure |
| `KING: castling_rights side=<side> value=<none|king|queen|both>` | rights per side |
| `KING: castled side=<side> flank=<king|queen>` | conservative castled inference |
| `KING: trapped side=<side>` | own pieces/opponent coverage restrict king |

Implementation details:

- Use attack maps for enemy attacks and pawn maps for pawn shield.
- Avoid over-tagging every small weakness. Emit only meaningful thresholds.
- Keep "vulnerable king" and "attacking castled king" derived from this module.

### Phase 7: Piece Activity And Pressure

Complete piece-specific placement tags:

| Tag | Meaning |
| --- | --- |
| `PIECE: tier=<tier> side=<side> piece=<piece> square=<square>` | placement strength |
| `PIECE: extreme=strongest ...` | strongest placement |
| `PIECE: extreme=weakest ...` | weakest placement |
| `PIECE: activity=active side=<side> piece=<piece> square=<square>` | active piece |
| `PIECE: activity=passive ...` | passive piece |
| `PIECE: outpost side=<side> piece=knight square=<square>` | knight/bishop outpost |
| `PIECE: trapped side=<side> piece=<piece> square=<square>` | low mobility, capturable or boxed |
| `PIECE: hanging side=<side> piece=<piece> square=<square>` | attacked and undefended |
| `PIECE: loose side=<side> piece=<piece> square=<square>` | undefended but not attacked |
| `PIECE: overloaded side=<side> piece=<piece> square=<square>` | defends multiple critical targets |
| `PIECE: defender side=<side> piece=<piece> square=<square> target=<square>` | defender relationship |
| `PIECE: battery side=<side> pieces=<pieces> line=<line>` | queen/bishop/rook battery |
| `PIECE: rook_open_file side=<side> square=<square> file=<file>` | rook on open file |
| `PIECE: rook_seventh side=<side> square=<square>` | rook on 7th/2nd rank |
| `PIECE: bishop_pair side=<side>` | duplicate material signal if useful |
| `PIECE: bad_bishop side=<side> square=<square>` | bishop blocked by own pawns |
| `PIECE: knight_outpost side=<side> square=<square>` | named shortcut if needed |

Implementation details:

- Keep the piece activity score explainable.
- Use piece-square heuristics, mobility, attacks, defended status, and king
  proximity, but do not hide a huge opaque formula in one method.
- Existing `wiki/piece-tags.md` should become the reference for this family.

### Phase 8: Space, Development, Mobility, Initiative

Add strategic tags:

| Tag | Meaning |
| --- | --- |
| `SPACE: advantage=white|black|equal` | central/territorial space |
| `SPACE: center_control side=<side> count=<n>` | control of e4/d4/e5/d5 |
| `SPACE: flank_control side=<side> flank=<king|queen>` | flank space |
| `DEVELOPMENT: lead=white|black|equal` | development lead |
| `DEVELOPMENT: undeveloped side=<side> piece=<piece> square=<square>` | undeveloped minor/rook |
| `DEVELOPMENT: king_uncastled side=<side>` | king still central with castling unavailable/unused |
| `MOBILITY: side=<side> legal=<n>` | legal moves by side |
| `MOBILITY: piece=<piece> side=<side> square=<square> moves=<n>` | piece mobility |
| `MOBILITY: restricted side=<side>` | low legal mobility |
| `INITIATIVE: side=<side>` | forcing-move advantage |
| `INITIATIVE: forcing_moves side=<side> count=<n>` | checks/captures/threats count |
| `INITIATIVE: tempo side=<side>` | side has immediate forcing tempo |

Implementation details:

- `MOBILITY` for the side not to move requires generating moves after flipping
  side to move. This must be cached and should not be done unless requested by
  detectors that need it.
- `INITIATIVE` should be conservative and probably combine forcing move counts,
  king vulnerability, and engine/PV data when available.

### Phase 9: Tactical Motifs

Implement tactical themes in increasing complexity.

#### Simple Board Motifs

| Tag | Meaning |
| --- | --- |
| `TACTIC: motif=pin ...` | absolute or relative pin |
| `TACTIC: motif=fork ...` | one piece attacks two valuable targets |
| `TACTIC: motif=hanging ...` | attacked undefended piece |
| `TACTIC: motif=skewer ...` | high-value piece in front of lower-value piece |
| `TACTIC: motif=xray ...` | line pressure through piece |
| `TACTIC: motif=discovered_attack ...` | moving piece reveals attack |
| `TACTIC: motif=discovered_check ...` | moving piece reveals check |
| `TACTIC: motif=double_check ...` | move gives two checks |
| `TACTIC: motif=trapped_piece ...` | piece has no safe moves and is attacked/trappable |

#### Defender Motifs

| Tag | Meaning |
| --- | --- |
| `TACTIC: motif=overloading ...` | defender has multiple critical duties |
| `TACTIC: motif=removal_of_defender ...` | capture/deflection removes defender |
| `TACTIC: motif=deflection ...` | move lures defender away |
| `TACTIC: motif=decoy ...` | move lures target onto bad square |
| `TACTIC: motif=attraction ...` | alias/canonical mapping to decoy if desired |
| `TACTIC: motif=interference ...` | move blocks defender line |
| `TACTIC: motif=clearance ...` | move vacates a square/line for another piece |

#### Sacrifice And Forcing Motifs

| Tag | Meaning |
| --- | --- |
| `TACTIC: motif=sacrifice piece=<piece> square=<square>` | material sacrifice candidate |
| `TACTIC: motif=queen_sacrifice ...` | queen sacrifice candidate |
| `TACTIC: motif=exchange_sacrifice ...` | rook-for-minor style sacrifice |
| `TACTIC: motif=desperado ...` | doomed piece captures before loss |
| `TACTIC: motif=zwischenzug ...` | in-between forcing move |
| `TACTIC: motif=intermezzo ...` | alias to zwischenzug |
| `TACTIC: motif=quiet_move ...` | non-capture non-check forcing move |
| `TACTIC: motif=simplification ...` | trades into favorable material/endgame |
| `TACTIC: motif=perpetual_check ...` | repeated checking resource |
| `TACTIC: motif=windmill ...` | repeated discovered checks/attacks |

Implementation order:

1. Pins, forks, hanging, skewers, x-rays.
2. Discovered attack/check and double check.
3. Trapped piece.
4. Removal/deflection/decoy/overload/interference/clearance.
5. Sacrifice, desperado, quiet move, zwischenzug.
6. Windmill and perpetual check.

Important constraint:

- Some motifs are not reliable from static position alone. For those, emit only
  when a legal move demonstrates the motif. Prefer move-specific fields:
  `move=<uci>`, `san=<san>`, `target=<square>`, `victim=<piece>@<square>`.

### Phase 10: Checkmate Pattern Tags

Use `CHECKMATE` for actual mating positions and `TACTIC` or `THREAT` for mating
nets that are not yet mate.

Canonical coverage currently includes:

- back-rank mate
- corner mate
- smothered mate
- support mate
- double-check mate
- delivery piece

Implement named mate recognizers:

| Pattern | Priority | Core predicate |
| --- | ---: | --- |
| `anastasias_mate` | high | knight controls escape, rook/queen mates on file/rank, trapped king |
| `arabian_mate` | high | rook + knight mate near corner |
| `boden_mate` | high | two bishops crossfire around castled/boxed king |
| `dovetail_mate` | high | queen mates adjacent king, own pieces cover diagonals |
| `epaulette_mate` | high | king boxed by own pieces on both sides, queen/rook delivers |
| `hook_mate` | high | rook/queen plus knight/pawn hook covers escape |
| `kill_box_mate` | high | queen/rook creates boxed king with support |
| `swallow_tail_mate` | high | queen mate with king's own pieces blocking diagonals |
| `triangle_mate` | high | queen/king or queen/support triangle pattern |
| `vukovic_mate` | high | rook + knight/pawn against castled king |
| `blind_swine_mate` | medium | two rooks on 7th/2nd rank mate/trap king |
| `corridor_mate` | medium | king trapped in corridor by own pieces/files |
| `h_file_mate` | medium | rook/queen mate on h-file against castled king |
| `lolli_mate` | medium | queen + pawn/king support near castled king |
| `morphys_mate` | medium | bishop/rook battery on castled king |
| `opera_mate` | medium | rook + bishop pattern resembling Morphy's Opera mate |
| `pillsburys_mate` | medium | rook/queen plus bishop/knight net |
| `reti_mate` | medium | bishop + rook/queen geometry pattern |
| `balestra_mate` | low | bishop/queen diagonal net |
| `blackburnes_mate` | low | bishops + knight mate pattern |
| `cozios_mate` | low | queen/king boxed pattern |
| `damianos_mate` | low | queen + pawn support near king |
| `grecos_mate` | low | bishop/queen/rook classical pattern |
| `max_langes_mate` | low | queen/bishop pattern |
| `mayets_mate` | low | bishop + rook/queen pattern |
| `railroad_mate` | low | two heavy pieces confine king |
| `suffocation_mate` | low | own pieces fully block king, non-knight delivery |
| `bishop_and_knight_checkmate` | medium | final mate with only B+N mating material for winner, no non-king material for loser, and bishop+knight+king covering all flights |
| `lawnmower_mate` | medium | queen+rook or two rooks deliver adjacent rank/file ladder mate |
| `legals_mate` | context-only | emit only from PGN context that proves the Legal trap pattern; omit from FEN-only tagging |
| `anderssens_mate` | excluded | do not emit until the project owns a fixture-backed predicate |
| `david_and_goliath_mate` | excluded | do not emit until the project owns a fixture-backed predicate |
| `ladder_trick` | excluded | not a final mate-pattern tag; map to concrete endgame/tactic tags when known |

Implementation approach:

1. Add a `MateContext` built only when `status=checkmate`.
2. Identify mated king, delivery move/piece if possible, checkers, blockers,
   escape squares, and supporting attackers.
3. Implement each named pattern as a small predicate class or method.
4. Emit multiple pattern tags if multiple names genuinely apply.
5. Add one positive FEN and at least one near-miss negative FEN per pattern.

Avoid implementing names that require game history unless we can detect them
from the final position. Opening-trap names such as Legal's Mate require PGN
line context, not just FEN. Excluded labels are intentionally not emitted in
normal output; this prevents source-site names from appearing without a concrete
reason the user can inspect.

### Phase 11: Endgame Tags

Add practical endgame classification:

| Tag | Meaning |
| --- | --- |
| `ENDGAME: type=pawn` | pawn endgame |
| `ENDGAME: type=rook` | rook endgame |
| `ENDGAME: type=queen` | queen endgame |
| `ENDGAME: type=bishop` | bishop-only minor endgame |
| `ENDGAME: type=knight` | knight-only minor endgame |
| `ENDGAME: type=minor_piece` | minor-piece endgame |
| `ENDGAME: type=opposite_bishops` | opposite bishops |
| `ENDGAME: type=same_color_bishops` | same-color bishops |
| `ENDGAME: type=rook_vs_minor` | rook vs minor |
| `ENDGAME: type=queen_vs_rook` | queen vs rook |
| `ENDGAME: type=king_and_pawn` | K+P style |
| `ENDGAME: theme=outside_passer` | outside passed pawn |
| `ENDGAME: theme=connected_passers` | connected passers |
| `ENDGAME: theme=wrong_rook_pawn` | wrong rook pawn with bishop |
| `ENDGAME: theme=fortress_candidate` | static fortress-like material/structure |
| `ENDGAME: theme=zugzwang_candidate` | low-move endgame pressure |

No tablebase dependency. These are heuristic labels, not perfect theoretical
outcomes.

### Phase 12: Opening Tags

Opening tags remain config-backed:

- `OPENING: eco=<code>`
- `OPENING: name=<name>`
- `OPENING: family=<family>` if we add family extraction
- `OPENING: variation=<variation>` if config supports it

Plan:

1. Keep `config/book.eco.toml` optional.
2. Cache ECO lookup once.
3. Add tests for missing config, malformed config, exact match, and no match.
4. Avoid engine dependency.

### Phase 13: Engine-Enriched Tags

Only emit these when analysis is supplied or requested:

| Tag | Meaning |
| --- | --- |
| `META: eval_cp=<n>` | engine/static eval |
| `META: wdl=<w>/<d>/<l>` | WDL |
| `META: difficulty=<label>` | difficulty bucket |
| `PV: ply=<n> move=<san|uci>` | principal variation moves |
| `CAND: rank=<n> move=<move> eval=<...>` | candidate move |
| `THREAT: type=<motif> side=<side>` | engine-supported threat |
| `IDEA: ...` | analysis-derived idea |
| `TACTIC: motif=mate_in_<n>` | bounded mate from engine/PV |
| `TACTIC: motif=advantage|crushing|equality` | evaluation result bucket |

Rules:

- Engine tags must include enough metadata to avoid confusing them with static
  facts.
- If Stockfish/LC0 analysis is unavailable, do not fake engine tags from
  heuristics.
- Keep engine tags optional in CLI output for deterministic no-engine workflows.

### Phase 14: Sequence And Delta Tags

The existing sequence/delta workflow should remain stable, but it needs richer
identity handling for new tags.

Add or verify:

- `Delta` can compare all new canonical family identities.
- `Sequence` can identify enabled/disabled tactics across a move line.
- `record analysis-delta` can aggregate new families.
- CLI `--delta` output includes stable identities and raw tag strings.

Useful sequence tags:

- `move: <ply> enables ... <tag>`
- `move: <ply> disables ... <tag>`
- `IDEA: move=<uci> creates=<tag-id>`
- `IDEA: move=<uci> removes=<tag-id>`

## Computational Definitions

This section defines how each tag should be computed reliably. If a tag cannot
meet its evidence rule, it should not be emitted. The default behavior should be
"quiet unless there is a clear reason", not "guess and hope".

### Shared Primitives

All detectors should use these primitives through `Context` rather than
reimplementing them locally.

| Primitive | Definition |
| --- | --- |
| `side(square)` | owner of the piece on `square`, or none |
| `piece(square)` | piece type on `square`, or empty |
| `value(piece)` | pawn=100, knight=320, bishop=330, rook=500, queen=900, king=infinite |
| `attackers(side, square)` | pieces of `side` attacking `square`, using legal attack geometry, not only legal moves |
| `defenders(side, square)` | friendly pieces attacking/defending `square` |
| `isAttacked(side, square)` | `attackers(side, square)` is non-empty |
| `isDefended(side, square)` | `defenders(side, square)` is non-empty |
| `leastAttacker(side, square)` | lowest-value attacker of `square` |
| `leastDefender(side, square)` | lowest-value defender of `square` |
| `netCaptureGain(square)` | approximate static exchange result using attacker/defender values |
| `ray(a, b)` | squares on the same rank, file, or diagonal between `a` and `b` |
| `lineSlider(piece)` | rook/queen for ranks/files, bishop/queen for diagonals |
| `kingZone(side)` | enemy king square plus adjacent squares and the three pawn-shield files |
| `safeFor(side, square)` | square is not occupied by own piece and not attacked by opponent |
| `legalAfter(move)` | result of applying a legal move with `play/undo` |
| `givesCheck(move)` | opponent king is attacked after legal move |
| `givesMate(move)` | `givesCheck(move)` and opponent has no legal replies |
| `criticalTarget(square)` | king, queen, rook, advanced passer, mate-defense square, or attacked high-value piece |

Implementation notes:

- Attack maps are pseudo-legal attack maps, but king safety checks still use
  legal move validation.
- Pawn attacks are attacks even if the pawn cannot legally move there.
- A pinned piece still attacks for geometric pressure tags, but the pin must be
  recorded because it may not be a legal defender.
- When a detector needs a legal move consequence, use `play(move, state)` and
  `undo(move, state)`.
- If a predicate depends on a multi-ply result and no engine/PV proof exists,
  emit a candidate tag only when the static evidence is very strong.

### Reliability Levels

Use these internal levels when deciding whether to emit:

| Level | Meaning | Default output |
| --- | --- | --- |
| `exact` | direct FEN/legal-move fact | emit |
| `static-strong` | heuristic with concrete board evidence and low false-positive risk | emit |
| `static-weak` | plausible but may surprise users | omit by default or hide behind debug/explain mode |
| `engine-proved` | shown by engine/PV/search | emit only in engine profile |
| `history-dependent` | cannot be known from FEN alone | omit unless PGN line context exists |

For normal `fen tags`, emit only `exact` and `static-strong`.

### META Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `META: to_move=<side>` | direct from `Position.isWhiteToMove()` | never omit |
| `META: phase=opening` | non-king material ratio `>= 0.75` or full opening material with undeveloped back rank | never omit |
| `META: phase=middlegame` | non-king material ratio `>= 0.35` and `< 0.75` | never omit |
| `META: phase=endgame` | non-king material ratio `< 0.35` | never omit |
| `META: source=<value>` | caller supplies source label | omit when absent |
| `META: fen=<fen>` | CLI requests `--include-fen` | omit by default |
| `META: eval_cp=<n>` | evaluator/analysis supplies centipawns | omit without evaluator/analysis |
| `META: wdl=<w>/<d>/<l>` | evaluator/analysis supplies WDL | omit without WDL |
| `META: difficulty=<label>` | WDL exists; map expected score to difficulty | omit without WDL |
| `META: evaluator=<name>` | engine/evaluator profile known | omit for static-only tags |

Difficulty definition:

- expected score = `win + 0.5 * draw`.
- difficulty = logarithmic scale of `1.0 - expected`.
- labels:
  - `very_easy`: `<= 0.20`
  - `easy`: `<= 0.35`
  - `medium`: `<= 0.55`
  - `hard`: `<= 0.70`
  - `very_hard`: `> 0.70`

### FACT Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `FACT: status=normal` | legal moves exist and side to move is not checkmated/stalemated | never omit |
| `FACT: status=checkmate` | `position.isCheckmate()` | never omit |
| `FACT: status=stalemate` | no legal moves and side to move not in check | never omit |
| `FACT: in_check=<side>` | `position.inCheck()` and side to move is checked | emit `none` if using full fact profile |
| `FACT: checker=<side>_<piece>@<square>` | checked side has checker list | omit if not in check |
| `FACT: castling=<rights>` | format current castling rights as `KQkq` subset or `-` | never omit |
| `FACT: en_passant=<square>` | `enPassantSquare != NO_SQUARE` | omit if none |
| `FACT: chess960=true` | `Position.isChess960()` | omit if false |
| `FACT: halfmove_clock=<n>` | user requested counters/debug tags | omit by default |
| `FACT: fullmove=<n>` | user requested counters/debug tags | omit by default |

The status tags are exact. They must never be inferred from material or
evaluation.

### MATERIAL Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `MATERIAL: cp_white=<n>` | sum piece values excluding king | never omit in material profile |
| `MATERIAL: cp_black=<n>` | sum piece values excluding king | never omit in material profile |
| `MATERIAL: balance=white` | white material lead `>= 150cp` | use `equal` below threshold |
| `MATERIAL: balance=black` | black material lead `>= 150cp` | use `equal` below threshold |
| `MATERIAL: balance=equal` | absolute material difference `< 150cp` | never omit in material profile |
| `MATERIAL: imbalance=minor` | lead in `[150, 399]` cp | omit if equal |
| `MATERIAL: imbalance=rook` | lead in `[400, 699]` cp | omit if lower |
| `MATERIAL: imbalance=queen` | lead in `[700, 1199]` cp | omit if lower |
| `MATERIAL: imbalance=large` | lead `>= 1200` cp | omit if lower |
| `MATERIAL: piece_count side=<side> piece=<piece> count=<n>` | exact count requested | omit by default if too noisy |
| `MATERIAL: queens=<white|black|both|none>` | queen count presence | emit in compact form |
| `MATERIAL: rooks=<white|black|both|none>` | rook count presence | emit in compact form |
| `MATERIAL: bishop_pair=<side|both>` | side has two or more bishops | omit if neither side has pair |
| `MATERIAL: opposite_bishops=true` | both sides have exactly one bishop and bishops are on opposite colors | omit otherwise |
| `MATERIAL: exchange side=<side>` | side has rook vs opponent minor-piece material shape and net material lead resembles exchange | omit if queens/extra rooks make it ambiguous |
| `MATERIAL: minor_exchange side=<side>` | bishop-vs-knight imbalance with equal other material | omit if multiple minor imbalances |
| `MATERIAL: bare_king side=<side>` | side has no non-king pieces | omit otherwise |
| `MATERIAL: insufficient=true` | conservative insufficient mating material | omit if any uncertainty |

Insufficient material exact rules:

- K vs K.
- K+B vs K.
- K+N vs K.
- K+B vs K+B with same-colored bishops and no pawns.

Do not tag insufficient material for K+NN vs K, K+B+N vs K, or positions with
pawns.

### MOVE Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `MOVE: legal=<n>` | count legal moves | never omit |
| `MOVE: captures=<n>` | legal moves capturing occupied target or en-passant | omit if zero |
| `MOVE: checks=<n>` | legal moves where opponent is in check after move | omit if zero |
| `MOVE: mates=<n>` | legal moves that checkmate | omit if zero |
| `MOVE: promotions=<n>` | legal promotion moves | omit if zero |
| `MOVE: underpromotions=<n>` | legal promotions to rook, bishop, or knight | omit if zero |
| `MOVE: castles=<n>` | legal castling moves | omit if zero |
| `MOVE: en_passant=<n>` | legal en-passant captures | omit if zero |
| `MOVE: quiet=<n>` | legal moves that are not captures, promotions, castles, or checks | omit by default unless debug/profile asks |
| `MOVE: only=<uci>` | exactly one legal move | omit otherwise |
| `MOVE: forced=true` | exactly one legal move | omit otherwise |
| `MOVE: evasions=<n>` | side to move is in check; legal move count | omit if not in check |

Mate-in-one:

- `MOVE: mates=<n>` is exact and static.
- `TACTIC: motif=mate_in_1 move=<uci>` may be emitted for each mating move if
  detailed tactical tags are requested.

### PAWN Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `PAWN: structure=doubled side=<side> file=<file> count=<n>` | side has at least two pawns on file | omit otherwise |
| `PAWN: structure=tripled side=<side> file=<file> count=<n>` | side has at least three pawns on file | can emit in addition to doubled |
| `PAWN: structure=isolated side=<side> file=<file>` | side has pawn(s) on file and no friendly pawn on adjacent files | omit otherwise |
| `PAWN: structure=passed side=<side> square=<square>` | no enemy pawns on same/adjacent files ahead of pawn | omit otherwise |
| `PAWN: structure=protected_passed side=<side> square=<square>` | passed pawn defended by own pawn or piece | omit if passed but undefended |
| `PAWN: structure=connected_passed side=<side> squares=<a,b>` | adjacent passed pawns on neighboring files whose ranks differ by at most one | omit otherwise |
| `PAWN: structure=candidate_passed side=<side> square=<square>` | non-passed pawn can become passed after one legal/credible pawn advance or capture and is not blockaded by a lower-value enemy piece | omit if ambiguous |
| `PAWN: structure=backward side=<side> square=<square>` | pawn cannot safely advance, has no friendly pawn support from adjacent files, and enemy controls advance square | omit if it can safely advance |
| `PAWN: structure=advanced side=<side> square=<square>` | white pawn on rank 5+ or black pawn on rank 4- | omit otherwise |
| `PAWN: structure=promotion_ready side=<side> square=<square>` | white pawn on rank 7 or black pawn on rank 2 | omit otherwise |
| `PAWN: structure=chain side=<side> base=<square> head=<square>` | diagonal pawn chain of length at least three, base is rearmost pawn, head foremost | omit for two-pawn pairs |
| `PAWN: structure=phalanx side=<side> squares=<a,b>` | two friendly pawns adjacent horizontally on same rank | omit otherwise |
| `PAWN: structure=hanging side=<side> files=<files>` | two adjacent friendly pawns with no friendly pawns on neighboring outer files and both are attackable/advance-constrained | omit if only one pawn |
| `PAWN: islands side=<side> count=<n>` | count groups of occupied pawn files | emit if `n >= 2`; omit one island |
| `PAWN: majority side=<side> flank=<queen|king|center>` | side has more pawns than opponent on flank | omit equal flank counts |
| `PAWN: lever side=<side> square=<square> target=<square>` | pawn can advance/capture to challenge enemy pawn chain or create break | omit if move illegal |
| `PAWN: ram side=<side> square=<square> target=<square>` | opposing pawns block each other directly on same file | omit otherwise |
| `PAWN: underpromotion_available side=<side> move=<uci>` | legal underpromotion exists and underpromotion gives check, mate, avoids stalemate, or wins material compared to queen promotion | omit routine underpromotions |

Reliability notes:

- Passed pawns and doubled pawns are exact static tags.
- Backward, candidate, lever, and hanging-pawn tags are heuristic. Emit only
  when all listed conditions are met.
- Underpromotion should not fire just because underpromotion moves are legal.
  It needs a reason.

### KING Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `KING: side=<side> attackers=<n>` | enemy attackers on king square or king zone, depending field name chosen | emit count in king profile |
| `KING: exposed side=<side>` | king has `<= 1` friendly adjacent cover piece and at least two adjacent squares attacked | omit otherwise |
| `KING: vulnerable side=<side>` | enemy has at least three attackers into king zone, or queen/rook/bishop battery plus weak pawn shield | omit if only one weak signal |
| `KING: flight_squares side=<side> count=<n>` | adjacent squares that are empty/enemy-occupied and not enemy attacked | emit in king profile |
| `KING: no_flight side=<side>` | `flight_squares == 0` and king is under pressure or in check | omit if safe/no pressure |
| `KING: back_rank_weak side=<side>` | king on home rank, adjacent escape squares blocked by own pieces/pawns, and enemy rook/queen can plausibly check along back rank | omit without heavy-piece pressure |
| `KING: pawn_shield side=<side> quality=<good|thin|broken|none>` | evaluate three files around king for friendly pawns on shield ranks | emit only if castled/king near flank |
| `KING: open_file_near side=<side> file=<file>` | open or half-open file adjacent to king with enemy rook/queen line potential | omit without enemy heavy piece |
| `KING: diagonal_weakness side=<side> diagonal=<name>` | bishop/queen diagonal to king zone with at most one removable blocker | omit otherwise |
| `KING: castling_rights side=<side> value=<none|king|queen|both>` | parse castling rights per side | emit in king/castling profile |
| `KING: castled side=<side> flank=<king|queen>` | king on g/c-file style castled square or Chess960 final castled square with rook placement consistent | omit if uncertain |
| `KING: trapped side=<side>` | no flight squares and own pieces occupy/block most adjacent squares | omit unless pressure exists |

Pawn shield quality:

- `good`: at least two shield pawns close to king and no adjacent open file.
- `thin`: one shield pawn missing or advanced.
- `broken`: two or more shield pawns missing/advanced or direct open file.
- `none`: no friendly pawn shield near flank king.

### PIECE Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `PIECE: tier=<tier> side=<side> piece=<piece> square=<square>` | piece activity score crosses tier thresholds | emit for every piece only in piece-profile; otherwise top extremes |
| `PIECE: extreme=strongest ...` | highest positive piece activity score | emit when piece-profile enabled |
| `PIECE: extreme=weakest ...` | lowest piece activity score | emit when piece-profile enabled |
| `PIECE: activity=active ...` | mobility, attacks, defended status, and central/king-zone role exceed active threshold | omit neutral pieces |
| `PIECE: activity=passive ...` | low mobility and poor placement for that piece type | omit if blocked only temporarily but has legal improving move |
| `PIECE: outpost side=<side> piece=<piece> square=<square>` | knight or bishop on enemy half, defended by pawn/piece, cannot be attacked by enemy pawns | omit otherwise |
| `PIECE: trapped side=<side> piece=<piece> square=<square>` | non-king piece has no safe legal moves and is attacked or can be attacked by a lower-value piece | omit if piece can trade or escape |
| `PIECE: hanging side=<side> piece=<piece> square=<square>` | piece is attacked by enemy, not defended by legal defender, and not tactically immune | omit kings and pawns unless profile asks |
| `PIECE: loose side=<side> piece=<piece> square=<square>` | piece is undefended but not currently attacked | omit low-value pieces unless many loose pieces |
| `PIECE: overloaded side=<side> piece=<piece> square=<square>` | piece is sole legal defender of at least two critical targets | omit if other defenders can recapture |
| `PIECE: defender side=<side> piece=<piece> square=<square> target=<square>` | piece is sole/important defender of critical target | omit ordinary defended pawns |
| `PIECE: battery side=<side> pieces=<pieces> line=<line>` | queen+rook, rook+rook, queen+bishop, or bishop+bishop aligned toward king/critical target | omit harmless alignment |
| `PIECE: rook_open_file side=<side> square=<square> file=<file>` | rook on open file, no friendly/enemy pawns on file | omit if file blocked by own piece directly ahead |
| `PIECE: rook_seventh side=<side> square=<square>` | rook on 7th rank for white or 2nd rank for black attacking pawns/king zone | omit if trapped and irrelevant |
| `PIECE: bad_bishop side=<side> square=<square>` | bishop blocked by own pawns on same color and low mobility `<= 3` | omit if active diagonal exists |
| `PIECE: knight_outpost side=<side> square=<square>` | alias of outpost for knights | prefer canonical `outpost` unless UI wants shortcut |

Piece activity score should be explainable:

- mobility component by piece type
- attacks on enemy half/king zone
- defended status
- centralization for knights/bishops
- open-file and rank bonuses for rooks
- queen activity tempered by vulnerability
- penalties for trapped, pinned, blocked, or undefended high-value pieces

### SPACE Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `SPACE: advantage=white|black|equal` | compare controlled safe squares in opponent half and center; lead threshold `>= 4` squares | emit `equal` only in space profile |
| `SPACE: center_control side=<side> count=<n>` | count attacks/occupancy on d4/e4/d5/e5 | emit in space profile |
| `SPACE: flank_control side=<side> flank=<king|queen>` | side controls at least three more relevant flank squares than opponent | omit if small edge |

Space should not be based on pawns alone. Use safe controlled squares that
pieces can plausibly use.

### DEVELOPMENT Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `DEVELOPMENT: lead=white|black|equal` | compare developed minor pieces, rooks connected, castling/king safety, queen not prematurely exposed | emit only opening/middlegame |
| `DEVELOPMENT: undeveloped side=<side> piece=<piece> square=<square>` | minor piece on original square after move 8 or phase still opening but opponent developed | omit in endgames |
| `DEVELOPMENT: king_uncastled side=<side>` | king on original/central square, castling rights gone or unsafe, and phase not endgame | omit if castling still easy/safe |

Development tags are opening/middlegame-only. Do not emit "undeveloped" in
simplified endgames.

### MOBILITY Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `MOBILITY: side=<side> legal=<n>` | legal move count for side | emit in mobility profile |
| `MOBILITY: piece=<piece> side=<side> square=<square> moves=<n>` | legal or pseudo-legal safe moves for piece | emit only for unusually high/low mobility |
| `MOBILITY: restricted side=<side>` | legal move count `<= 5` outside checkmate/stalemate, or piece mobility broadly constrained | omit if side is simply in check unless evasions low |

For side not to move, mobility requires a temporary side-to-move flip and legal
move generation. Cache it and do it only if needed.

### INITIATIVE Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `INITIATIVE: side=<side>` | side has materially meaningful forcing-move lead: checks + threats + captures against king zone exceed opponent by threshold | omit if only one checking move with no follow-up |
| `INITIATIVE: forcing_moves side=<side> count=<n>` | count legal checks, mate threats, high-value captures, promotions | emit in initiative profile |
| `INITIATIVE: tempo side=<side>` | legal move creates immediate threat against king/queen or wins material while opponent has no equal forcing reply | omit without one-ply evidence |

Initiative is vague on many puzzle sites. Our definition is: "a side has the
initiative if it has multiple forcing moves or one forcing move that creates a
concrete high-value threat while the opponent lacks an equivalent immediate
forcing reply."

### TACTIC Tags

All tactical tags should include `move=<uci>` when the motif is move-dependent.
Static relation tags can omit `move` only when the motif already exists in the
position.

| Tag | Reliable definition | Do not emit when |
| --- | --- | --- |
| `TACTIC: motif=pin` | line slider attacks valuable piece/king through exactly one enemy piece; pinned piece cannot legally move without exposing higher-value target or king | blocker can freely move or target is not valuable |
| `TACTIC: motif=absolute_pin` | pinned piece shields own king | use `pin subtype=absolute` if fields support it |
| `TACTIC: motif=relative_pin` | pinned piece shields queen/rook or mate-defense square | target is not critical |
| `TACTIC: motif=fork` | one piece attacks at least two critical targets after legal move or in current position | one target can capture for free and wins material |
| `TACTIC: motif=double_attack` | same as fork but non-piece target included, such as mate threat + queen | no two critical targets |
| `TACTIC: motif=hanging` | attacked undefended non-king piece | defended or tactically poisoned |
| `TACTIC: motif=skewer` | line attack hits high-value front piece; legal/forced move exposes lower piece behind | front piece can capture attacker safely or line not forced |
| `TACTIC: motif=xray` | slider attacks/pressures target through intervening piece, and intervening piece is pinned, overloaded, or removable | merely aligned pieces with no pressure |
| `TACTIC: motif=discovered_attack` | legal move by front piece reveals attack by back slider on critical target | revealed target not critical |
| `TACTIC: motif=discovered_check` | legal move by front piece reveals check by back slider | move illegal or checking line blocked |
| `TACTIC: motif=double_check` | legal move results in two independent attacks on king | only one checker after move |
| `TACTIC: motif=trapped_piece` | piece has no safe legal squares and is attacked/can be attacked by lower-value piece | piece can trade for equal or better material |
| `TACTIC: motif=overloading` | one defender is sole legal defender of two or more critical targets; legal move attacks one target and wins the other | other defender can replace it |
| `TACTIC: motif=removal_of_defender` | legal capture/removal of a defender makes a critical target undefended and tactically winnable | removed piece not a key defender |
| `TACTIC: motif=deflection` | legal move attacks/lures sole defender away from critical duty; if accepted, target falls or mate occurs | defender can decline without loss |
| `TACTIC: motif=decoy` | legal forcing move lures king/queen/piece onto a square where a known tactic follows | no forcing acceptance or no follow-up proof |
| `TACTIC: motif=attraction` | alias of decoy for external mapping; internally prefer decoy | do not emit both unless alias mode requested |
| `TACTIC: motif=interference` | legal move occupies line between defender and critical target, breaking defense or attack | line was not important |
| `TACTIC: motif=clearance` | legal move vacates square/line so another piece gains check, mate, or material win | vacated line does not matter |
| `TACTIC: motif=sacrifice` | legal move gives up material of at least pawn value and creates mate, wins more material within proof window, or engine/PV confirms compensation | speculative sacrifice with no proof |
| `TACTIC: motif=queen_sacrifice` | sacrifice where moving/lost piece is queen and compensation is mate/material proof | queen trade or simple recapture equalization |
| `TACTIC: motif=exchange_sacrifice` | rook intentionally trades for bishop/knight/pawn with concrete follow-up or engine/PV proof | forced losing exchange without compensation |
| `TACTIC: motif=desperado` | attacked high-value piece has no safe retreat, but legal capture/check wins material before it is lost | piece has safe retreat |
| `TACTIC: motif=zwischenzug` | legal forcing move inserted before expected recapture changes material/check result favorably | ordinary check with no in-between context |
| `TACTIC: motif=intermezzo` | alias of zwischenzug | do not emit both unless alias mode requested |
| `TACTIC: motif=quiet_move` | legal non-capture non-check move creates unavoidable mate/material threat in one, or engine/PV proves it | positional improving move only |
| `TACTIC: motif=simplification` | legal sequence/trade reduces material into clearly winning/equalizing endgame by material/eval proof | routine trade with no result change |
| `TACTIC: motif=perpetual_check` | engine/PV or bounded repetition search finds repeated checks with no escape and non-losing eval | single checking resource only |
| `TACTIC: motif=windmill` | repeated discovered checks/attacks possible by same two pieces, winning at least rook value or mating | one discovered check only |
| `TACTIC: motif=defensive_move` | only or best legal move prevents mate, wins back material, or restores equality according to static proof/engine | any normal developing move |
| `TACTIC: motif=attacking_f2_f7` | move/piece attacks f2/f7 before castling or around king, with queen/bishop/knight support and king still vulnerable | pawn attack only or no king relevance |
| `TACTIC: motif=mating_net` | king has no or one flight square, multiple escape squares controlled, and side has legal checking continuation or engine proof | vague pressure with many escapes |
| `TACTIC: motif=mate_in_1` | legal move gives mate | no legal mating move |
| `TACTIC: motif=mate_in_2+` | bounded engine/PV proves mate distance | static guess without search |
| `TACTIC: motif=advantage` | engine/static eval bucket crosses advantage threshold | static tags alone |
| `TACTIC: motif=crushing` | engine/static eval bucket crosses crushing threshold or mate is forced | static tags alone |
| `TACTIC: motif=equality` | engine/static eval near zero after forced line or defensive resource | static material equality alone |

Evaluation buckets:

- `equality`: absolute eval `<= 50cp`.
- `advantage`: `150cp..499cp`.
- `crushing`: `>= 500cp` or forced mate.
- These should be engine/static-evaluator profile tags, not pure board facts.

### CHECKMATE Tags

All named mate tags require `FACT: status=checkmate`. If the position is not
mate, use `TACTIC: motif=mating_net` or `THREAT` instead.

| Tag | Reliable definition |
| --- | --- |
| `CHECKMATE: winner=<side>` | opposite of mated side |
| `CHECKMATE: defender=<side>` | side to move in checkmate |
| `CHECKMATE: delivery=<piece>` | checker piece if exactly one checker; `multiple` if double check |
| `CHECKMATE: pattern=double_check` | final mate has at least two checkers |
| `CHECKMATE: pattern=back_rank_mate` | mated king on home rank, own pieces/pawns block flight squares, rook/queen delivers rank/file mate |
| `CHECKMATE: pattern=smothered_mate` | knight delivers mate, king surrounded mostly by own pieces, no flight squares |
| `CHECKMATE: pattern=support_mate` | checking piece is protected by another piece and king cannot capture it |
| `CHECKMATE: pattern=corner_mate` | mated king on corner square and all adjacent squares controlled/blocked |
| `CHECKMATE: pattern=anastasias_mate` | rook/queen checks along file/rank near edge; knight controls key escape squares around castled/edge king |
| `CHECKMATE: pattern=arabian_mate` | rook delivers mate with knight support controlling escape/capture squares near corner |
| `CHECKMATE: pattern=boden_mate` | two bishops create crossing diagonal controls around king, usually with king boxed by own pieces |
| `CHECKMATE: pattern=dovetail_mate` | queen adjacent/near king delivers mate; two diagonal escape squares blocked by defender's own pieces |
| `CHECKMATE: pattern=epaulette_mate` | king has own pieces on both horizontal shoulders and queen/rook delivers direct mate |
| `CHECKMATE: pattern=hook_mate` | rook/queen delivery plus knight or pawn hook controls the main flight square |
| `CHECKMATE: pattern=kill_box_mate` | queen/rook and king/supporting piece form a box where all flights are controlled, not necessarily corner |
| `CHECKMATE: pattern=swallow_tail_mate` | queen mates king whose diagonal rear squares are blocked by own pieces |
| `CHECKMATE: pattern=triangle_mate` | queen and support piece/king form triangular coverage of king and all flights |
| `CHECKMATE: pattern=vukovic_mate` | rook/queen attacks castled king with knight/pawn support controlling h/g or a/b escape complex |
| `CHECKMATE: pattern=blind_swine_mate` | two rooks or rook+queen on 7th/2nd rank trap king along back rank |
| `CHECKMATE: pattern=corridor_mate` | king trapped in a rank/file corridor by own pieces or board edge, rook/queen controls corridor |
| `CHECKMATE: pattern=h_file_mate` | rook/queen mates on h-file against king-side king with h-file opened and escape squares covered |
| `CHECKMATE: pattern=lolli_mate` | queen delivers near castled king with pawn on g/h or b/a support controlling escape |
| `CHECKMATE: pattern=morphys_mate` | rook delivers on open file/rank with bishop controlling escape diagonal against castled king |
| `CHECKMATE: pattern=opera_mate` | rook and bishop coordinate against trapped king in Morphy-like geometry; final position must match rook+bishop confinement |
| `CHECKMATE: pattern=pillsburys_mate` | rook/queen delivery supported by bishop/knight controlling both escape and capture squares |
| `CHECKMATE: pattern=reti_mate` | bishop plus rook/queen geometry traps king with diagonal and rank/file control |
| `CHECKMATE: pattern=balestra_mate` | queen and bishop form diagonal net where queen cuts off king and bishop supports |
| `CHECKMATE: pattern=blackburnes_mate` | two bishops plus knight/heavy-piece support cover all escapes |
| `CHECKMATE: pattern=cozios_mate` | queen mates a boxed king with own pieces restricting escape on both sides |
| `CHECKMATE: pattern=damianos_mate` | queen supported by pawn or bishop mates near castled king on h/a-file complex |
| `CHECKMATE: pattern=grecos_mate` | bishop controls long diagonal while queen/rook mates exposed king on edge/castled area |
| `CHECKMATE: pattern=max_langes_mate` | queen/bishop/king geometry controls all adjacent squares near edge |
| `CHECKMATE: pattern=mayets_mate` | bishop controls diagonal escape while rook/queen mates along rank/file |
| `CHECKMATE: pattern=railroad_mate` | two heavy pieces confine king along adjacent ranks/files like rails |
| `CHECKMATE: pattern=suffocation_mate` | mated king's own pieces block nearly every flight; delivery is not the standard knight smother |
| `CHECKMATE: pattern=lawnmower_mate` | two heavy pieces or queen+rook form adjacent-file/rank ladder mate |

Special mate-name rules:

- `legals_mate`: emit only from PGN sequence context where the opening trap is
  actually proven. Do not emit it from a final FEN alone.
- `bishop_and_knight_checkmate`: require actual checkmate, winning side has
  bishop+knight+king only, losing side has king only, and the bishop/knight/king
  jointly cover every flight square.
- `lawnmower_mate`: require actual checkmate by queen+rook or two rooks using
  adjacent rank/file confinement.
- `anderssens_mate`, `david_and_goliath_mate`, and `ladder_trick`: excluded
  from normal output until they have fixture-backed project predicates. If a
  source imports these labels, keep them as external metadata, not canonical
  generated tags.

Named mate reliability rule:

- It is acceptable to emit multiple mate pattern tags when the geometry truly
  overlaps.
- It is better to emit only `CHECKMATE: delivery=queen` than to force a named
  pattern on a generic mate.

### ENDGAME Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `ENDGAME: type=pawn` | only kings and pawns remain | omit if any piece remains |
| `ENDGAME: type=rook` | at least one rook and no queens; minor pieces absent or symmetrical enough for rook-endgame classification | omit if queen present |
| `ENDGAME: type=queen` | queen(s) present, rooks absent, low total material | omit in middlegame material |
| `ENDGAME: type=bishop` | bishops are only non-pawn pieces | omit if knights/rooks/queens |
| `ENDGAME: type=knight` | knights are only non-pawn pieces | omit if bishops/rooks/queens |
| `ENDGAME: type=minor_piece` | only bishops/knights and pawns remain | omit if rook/queen |
| `ENDGAME: type=opposite_bishops` | exactly one bishop each, opposite colors, no other pieces or only light extra material | omit if multiple bishops |
| `ENDGAME: type=same_color_bishops` | exactly one bishop each, same color | omit otherwise |
| `ENDGAME: type=rook_vs_minor` | one side has rook, other has bishop/knight, no queens | omit with extra heavy pieces |
| `ENDGAME: type=queen_vs_rook` | queen vs rook primary material, no extra queens | omit with many pieces |
| `ENDGAME: type=king_and_pawn` | synonym/profile alias for pawn endgame | prefer `type=pawn` canonical |
| `ENDGAME: theme=outside_passer` | passed pawn on flank farthest from both kings compared to main pawn mass | omit if not passed |
| `ENDGAME: theme=connected_passers` | two adjacent passed pawns | reuse pawn evidence |
| `ENDGAME: theme=wrong_rook_pawn` | rook pawn promotes on color not controlled by only bishop, defending king can reach corner | omit if unsure |
| `ENDGAME: theme=fortress_candidate` | material down side has blocked pawns, opposite bishops or rook-pawn fortress geometry, and no clear pawn breaks | static-weak; omit by default unless profile asks |
| `ENDGAME: theme=zugzwang_candidate` | low material, side to move has only king/pawn moves and all legal moves worsen static facts | omit unless verified by move comparison or engine |

Endgame tags are classification and heuristic tags, not tablebase claims.

### OPENING Tags

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `OPENING: eco=<code>` | exact normalized FEN/signature match in configured ECO book | omit if no match |
| `OPENING: name=<name>` | ECO match includes name | omit if no match |
| `OPENING: family=<family>` | name can be split reliably by known family rules or config field | omit if parser uncertain |
| `OPENING: variation=<variation>` | config provides variation field or reliable split | omit if uncertain |

Opening names are config-derived. Do not infer named openings from piece shapes
unless a real opening book entry matches.

### CAND, PV, THREAT, IDEA Tags

These are engine/analysis or sequence tags, not static position tags.

| Tag | Evidence rule | Omit rule |
| --- | --- | --- |
| `CAND: rank=<n> move=<move> eval=<...>` | MultiPV or candidate search produced move | omit without analysis |
| `PV: ply=<n> move=<move>` | principal variation exists | omit without PV |
| `THREAT: type=<motif> side=<side>` | opponent has a concrete next-move tactic/mate/material win after null/quiet model or engine threat analysis | omit if just "pressure" |
| `IDEA: move=<uci> creates=<tag-id>` | applying move creates a canonical tag absent before | omit if tag identity cannot be compared |
| `IDEA: move=<uci> removes=<tag-id>` | applying move removes a bad/threat tag | omit if removal is unrelated/noisy |
| `IDEA: plan=<text>` | curated deterministic mapping from tag cluster to short idea | omit free-form generated text from core tags |

Threat definition:

- A threat is an opponent legal move after the current side makes no tactically
  relevant improvement, or a PV-supported next move, that produces one of:
  mate, winning queen/rook, promotion, decisive discovered attack, or material
  swing above threshold.
- Avoid `THREAT` for "the king looks unsafe" without a concrete move.

### Vague Puzzle Tags With Project Definitions

Some puzzle-site tags are too broad. ChessRTK should define them narrowly:

| Vague label | ChessRTK definition |
| --- | --- |
| `advantage` | evaluator or static material/king-safety score indicates `>= 150cp` and no immediate tactical refutation |
| `crushing` | evaluator `>= 500cp`, forced mate, or material win of rook+ with safe king |
| `equality` | evaluator within `50cp` or defensive move restores material/status to equal |
| `defensive move` | legal move is the only move, prevents mate, saves a critical piece, or changes eval bucket from losing to equal/advantage |
| `mating net` | enemy king has `<= 1` flight square, checking side controls all escape corridors, and at least one legal checking move keeps the net |
| `quiet move` | non-capture non-check legal move creates mate/material threat that opponent cannot ignore |
| `zugzwang` | low-material position where every legal move worsens a static evaluation bucket or loses critical opposition/pawn; engine confirmation preferred |
| `endgame tactic` | any tactic motif emitted while `META: phase=endgame` or an endgame type tag exists |
| `attacking castled king` | king is castled or flank-placed, pawn shield is broken/thin, and attacker has at least two pieces aimed at king zone |
| `kingside attack` | same as attacking castled king on files e-h |
| `queenside attack` | same as attacking castled king on files a-d or king placed queenside |
| `basic checkmates` | do not emit as a canonical tag; use specific mate/endgame pattern |
| `mate in three+` | engine/PV only; no static guess |

If a user asks why a tag appeared, the implementation should be able to point to
the exact rule above.

## Pseudocode

The pseudocode below is Java-like, but intentionally not final source. It shows
the control flow, shared caches, evidence checks, and conservative emit rules
that should drive the real implementation.

### Generator Pipeline

```java
TagResult tag(Position position, TagOptions options) {
    Context ctx = Context.build(position, options);
    Emitter out = new Emitter(options);

    runExactDetectors(ctx, out);

    if (options.includesStatic()) {
        runStaticStrongDetectors(ctx, out);
    }

    if (options.includesWeakDebug()) {
        runStaticWeakDetectors(ctx, out);
    }

    if (options.includesEngine() && options.analysis() != null) {
        runEngineDetectors(ctx, out);
    }

    if (options.includesAliases()) {
        mapAliases(ctx, out);
    }

    return out.sortedResult();
}

void runExactDetectors(Context ctx, Emitter out) {
    detectMeta(ctx, out);
    detectFacts(ctx, out);
    detectMaterial(ctx, out);
    detectLegalMoveCounts(ctx, out);
    detectExactPawns(ctx, out);
    detectExactEndgames(ctx, out);
    detectOpenings(ctx, out);

    if (ctx.isCheckmate()) {
        detectCheckmatePatterns(ctx, out);
    }
}

void runStaticStrongDetectors(Context ctx, Emitter out) {
    detectKingSafety(ctx, out);
    detectPieceActivity(ctx, out);
    detectSpace(ctx, out);
    detectDevelopment(ctx, out);
    detectMobility(ctx, out);
    detectInitiative(ctx, out);
    detectStaticTactics(ctx, out);
    detectStrongPawnHeuristics(ctx, out);
    detectStrongEndgameThemes(ctx, out);
}
```

### Context Construction

```java
final class Context {
    Position root;
    TagOptions options;
    Side stm;

    long[] piecesByTypeAndSide;
    long occupied;
    long empty;

    AttackMap whiteAttacks;
    AttackMap blackAttacks;
    LegalMoves legalMovesForStm;
    LegalMoves legalMovesForOtherSide; // lazy

    MaterialSummary material;
    KingSummary whiteKing;
    KingSummary blackKing;
    Phase phase;

    static Context build(Position position, TagOptions options) {
        Context ctx = new Context();
        ctx.root = position;
        ctx.options = options;
        ctx.stm = position.sideToMove();

        ctx.piecesByTypeAndSide = position.pieceBitboards();
        ctx.occupied = position.occupied();
        ctx.empty = ~ctx.occupied;

        ctx.whiteAttacks = AttackMap.from(position, WHITE);
        ctx.blackAttacks = AttackMap.from(position, BLACK);
        ctx.legalMovesForStm = LegalMoves.generate(position);

        ctx.material = MaterialSummary.from(position);
        ctx.phase = Phase.fromMaterial(ctx.material, position.fullmoveNumber());
        ctx.whiteKing = KingSummary.from(ctx, WHITE);
        ctx.blackKing = KingSummary.from(ctx, BLACK);
        return ctx;
    }

    LegalMoves legalMovesFor(Side side) {
        if (side == stm) {
            return legalMovesForStm;
        }
        if (legalMovesForOtherSide == null) {
            legalMovesForOtherSide = generateWithTemporarySideToMove(root, side);
        }
        return legalMovesForOtherSide;
    }

    MoveEffect effectOf(Move move) {
        State state = root.play(move);
        try {
            return MoveEffect.from(root, move);
        } finally {
            root.undo(move, state);
        }
    }
}
```

### Emission Gate

```java
final class Emitter {
    void emit(Tag tag, Reliability reliability, Evidence evidence) {
        if (!options.allows(tag.family())) {
            return;
        }
        if (!options.allowsReliability(reliability)) {
            return;
        }
        if (!evidence.isConcrete()) {
            return;
        }
        tags.add(tag.withEvidence(evidence));
    }

    void exact(Tag tag, Evidence evidence) {
        emit(tag, EXACT, evidence);
    }

    void strong(Tag tag, Evidence evidence) {
        emit(tag, STATIC_STRONG, evidence);
    }

    void weak(Tag tag, Evidence evidence) {
        emit(tag, STATIC_WEAK, evidence);
    }
}
```

A detector should never create a tag before it has a short evidence object. The
same evidence object can later power `--explain-tags`.

### META, FACT, and MATERIAL

```java
void detectMeta(Context ctx, Emitter out) {
    out.exact(tag("META", "to_move", ctx.stm), evidence("fen side-to-move"));
    out.exact(tag("META", "phase", ctx.phase), evidence(ctx.material.summary()));

    if (ctx.options.includeFen()) {
        out.exact(tag("META", "fen", ctx.root.toFen()), evidence("requested"));
    }
    if (ctx.options.analysis() != null) {
        Analysis a = ctx.options.analysis();
        out.exact(tag("META", "eval_cp", a.centipawns()), evidence("analysis"));
        out.exact(tag("META", "wdl", a.wdl()), evidence("analysis"));
        out.exact(tag("META", "difficulty", difficulty(a.wdl())), evidence("wdl"));
    }
}

void detectFacts(Context ctx, Emitter out) {
    if (ctx.root.isCheckmate()) {
        out.exact(tag("FACT", "status", "checkmate"), evidence("no legal moves and in check"));
    } else if (ctx.root.isStalemate()) {
        out.exact(tag("FACT", "status", "stalemate"), evidence("no legal moves and not in check"));
    } else {
        out.exact(tag("FACT", "status", "normal"), evidence("legal moves exist"));
    }

    if (ctx.root.inCheck()) {
        out.exact(tag("FACT", "in_check", ctx.stm), evidence("king attacked"));
        for (Square checker : checkers(ctx, ctx.stm)) {
            out.exact(tag("FACT", "checker", checkerPiece(ctx, checker)), evidence("direct checker"));
        }
    }

    out.exact(tag("FACT", "castling", ctx.root.castlingRights()), evidence("fen rights"));
    if (ctx.root.enPassantSquare().exists()) {
        out.exact(tag("FACT", "en_passant", ctx.root.enPassantSquare()), evidence("fen square"));
    }
    if (ctx.root.isChess960()) {
        out.exact(tag("FACT", "chess960", true), evidence("position mode"));
    }
}

void detectMaterial(Context ctx, Emitter out) {
    int white = ctx.material.cp(WHITE);
    int black = ctx.material.cp(BLACK);
    int diff = white - black;

    out.exact(tag("MATERIAL", "cp_white", white), evidence("piece values"));
    out.exact(tag("MATERIAL", "cp_black", black), evidence("piece values"));

    if (Math.abs(diff) < 150) {
        out.exact(tag("MATERIAL", "balance", "equal"), evidence("diff under threshold"));
    } else {
        Side leader = diff > 0 ? WHITE : BLACK;
        out.exact(tag("MATERIAL", "balance", leader), evidence("diff=" + Math.abs(diff)));
        out.exact(tag("MATERIAL", "imbalance", imbalanceBucket(Math.abs(diff))), evidence("diff bucket"));
    }

    emitPresenceTags(ctx, out, QUEEN);
    emitPresenceTags(ctx, out, ROOK);
    emitBishopPairTags(ctx, out);
    emitOppositeBishopTag(ctx, out);
    emitInsufficientMaterialTag(ctx, out);
}
```

### MOVE Counters

```java
void detectLegalMoveCounts(Context ctx, Emitter out) {
    MoveCounters c = new MoveCounters();

    for (Move move : ctx.legalMovesForStm) {
        MoveEffect effect = ctx.effectOf(move);
        c.legal++;

        if (effect.isCapture()) c.captures++;
        if (effect.isPromotion()) c.promotions++;
        if (effect.isUnderpromotion()) c.underpromotions++;
        if (effect.isCastle()) c.castles++;
        if (effect.isEnPassant()) c.enPassant++;
        if (effect.givesCheck()) c.checks++;
        if (effect.givesMate()) c.mates++;
        if (effect.isQuietNonCheck()) c.quiet++;
    }

    out.exact(tag("MOVE", "legal", c.legal), evidence("legal generator"));
    emitPositive(out, tag("MOVE", "captures", c.captures), c.captures);
    emitPositive(out, tag("MOVE", "checks", c.checks), c.checks);
    emitPositive(out, tag("MOVE", "mates", c.mates), c.mates);
    emitPositive(out, tag("MOVE", "promotions", c.promotions), c.promotions);
    emitPositive(out, tag("MOVE", "underpromotions", c.underpromotions), c.underpromotions);
    emitPositive(out, tag("MOVE", "castles", c.castles), c.castles);
    emitPositive(out, tag("MOVE", "en_passant", c.enPassant), c.enPassant);

    if (c.legal == 1) {
        Move only = ctx.legalMovesForStm.first();
        out.exact(tag("MOVE", "only", only.uci()), evidence("single legal move"));
        out.exact(tag("MOVE", "forced", true), evidence("single legal move"));
    }
    if (ctx.root.inCheck()) {
        out.exact(tag("MOVE", "evasions", c.legal), evidence("side to move in check"));
    }
}
```

### PAWN Structure

```java
void detectExactPawns(Context ctx, Emitter out) {
    for (Side side : SIDES) {
        for (File file : FILES) {
            int count = ctx.pawns(side).countOn(file);
            if (count >= 2) {
                out.exact(tag("PAWN", "structure", "doubled", side, file, count), evidence("same file"));
            }
            if (count >= 3) {
                out.exact(tag("PAWN", "structure", "tripled", side, file, count), evidence("same file"));
            }
            if (count > 0 && noFriendlyPawnsOnAdjacentFiles(ctx, side, file)) {
                out.exact(tag("PAWN", "structure", "isolated", side, file), evidence("no adjacent pawn files"));
            }
        }

        for (Square pawn : ctx.pawns(side)) {
            if (isPassedPawn(ctx, side, pawn)) {
                out.exact(tag("PAWN", "structure", "passed", side, pawn), evidence("no enemy pawn ahead"));
                if (isDefended(ctx, side, pawn)) {
                    out.strong(tag("PAWN", "structure", "protected_passed", side, pawn), evidence("passed and defended"));
                }
                if (isPromotionReady(side, pawn)) {
                    out.exact(tag("PAWN", "structure", "promotion_ready", side, pawn), evidence("rank 7 or 2"));
                }
            }
            if (isAdvancedPawn(side, pawn)) {
                out.exact(tag("PAWN", "structure", "advanced", side, pawn), evidence("advanced rank"));
            }
        }

        emitPawnIslands(ctx, out, side);
        emitPawnMajorities(ctx, out, side);
        emitChainsAndPhalanxes(ctx, out, side);
    }
}

void detectStrongPawnHeuristics(Context ctx, Emitter out) {
    for (Side side : SIDES) {
        for (Square pawn : ctx.pawns(side)) {
            if (isBackwardPawn(ctx, side, pawn)) {
                out.strong(tag("PAWN", "structure", "backward", side, pawn), evidence("blocked, unsupported, controlled"));
            }
            if (isCandidatePassedPawn(ctx, side, pawn)) {
                out.strong(tag("PAWN", "structure", "candidate_passed", side, pawn), evidence("one credible break from passed"));
            }
            if (isPawnLever(ctx, side, pawn)) {
                out.strong(tag("PAWN", "lever", side, pawn, leverTarget(ctx, side, pawn)), evidence("legal pawn break"));
            }
        }
        emitHangingPawnsIfBothConstrained(ctx, out, side);
    }
}
```

### KING Safety

```java
void detectKingSafety(Context ctx, Emitter out) {
    for (Side side : SIDES) {
        KingSummary k = ctx.king(side);

        if (k.flightSquares == 0 && k.hasPressure()) {
            out.strong(tag("KING", "no_flight", side), evidence(k.reason()));
        }
        if (k.exposed()) {
            out.strong(tag("KING", "exposed", side), evidence("low cover and attacked flights"));
        }
        if (k.vulnerable()) {
            out.strong(tag("KING", "vulnerable", side), evidence("king-zone attackers"));
        }
        if (k.backRankWeak()) {
            out.strong(tag("KING", "back_rank_weak", side), evidence("blocked escape and heavy-piece pressure"));
        }

        out.strong(tag("KING", "pawn_shield", side, k.shieldQuality()), evidence(k.shieldEvidence()));

        for (File file : filesNear(k.square())) {
            if (openFileNearKingWithEnemyHeavy(ctx, side, file)) {
                out.strong(tag("KING", "open_file_near", side, file), evidence("open file plus rook/queen"));
            }
        }

        if (isConservativelyCastled(ctx, side)) {
            out.exact(tag("KING", "castled", side, castledFlank(ctx, side)), evidence("king/rook castled geometry"));
        }
    }
}
```

### PIECE Activity and Static Piece Tags

```java
void detectPieceActivity(Context ctx, Emitter out) {
    PieceScore best = null;
    PieceScore worst = null;

    for (Side side : SIDES) {
        for (Square square : ctx.occupiedBy(side)) {
            Piece piece = ctx.piece(square);
            PieceScore score = scorePiece(ctx, side, piece, square);
            best = max(best, score);
            worst = min(worst, score);

            if (score.isActive()) {
                out.strong(tag("PIECE", "activity", "active", side, piece, square), evidence(score.explain()));
            }
            if (score.isPassive()) {
                out.strong(tag("PIECE", "activity", "passive", side, piece, square), evidence(score.explain()));
            }
            if (isOutpost(ctx, side, piece, square)) {
                out.strong(tag("PIECE", "outpost", side, piece, square), evidence("defended and cannot be chased by pawns"));
            }
            if (isHangingPiece(ctx, side, piece, square)) {
                out.strong(tag("PIECE", "hanging", side, piece, square), evidence("attacked and not legally defended"));
            }
            if (isLoosePiece(ctx, side, piece, square)) {
                out.strong(tag("PIECE", "loose", side, piece, square), evidence("undefended"));
            }
            if (isTrappedPiece(ctx, side, piece, square)) {
                out.strong(tag("PIECE", "trapped", side, piece, square), evidence("no safe squares and under threat"));
            }
            if (isOverloaded(ctx, side, square)) {
                out.strong(tag("PIECE", "overloaded", side, piece, square), evidence("sole defender of multiple critical targets"));
            }
        }
    }

    if (ctx.options.pieceProfile()) {
        out.strong(tag("PIECE", "extreme", "strongest", best.identity()), evidence(best.explain()));
        out.strong(tag("PIECE", "extreme", "weakest", worst.identity()), evidence(worst.explain()));
    }
}

PieceScore scorePiece(Context ctx, Side side, Piece piece, Square square) {
    int score = 0;
    score += mobilityBonus(piece, safeMoves(ctx, side, square));
    score += targetBonus(attacksCriticalTargets(ctx, side, square));
    score += centerBonus(piece, square);
    score += kingZoneBonus(ctx, side, square);
    score += rookFileBonus(ctx, side, piece, square);
    score -= vulnerabilityPenalty(ctx, side, piece, square);
    score -= pinnedPenalty(ctx, side, square);
    return new PieceScore(piece, square, score);
}
```

### SPACE, DEVELOPMENT, MOBILITY, and INITIATIVE

```java
void detectSpace(Context ctx, Emitter out) {
    int white = safeControlledSquaresInEnemyHalf(ctx, WHITE);
    int black = safeControlledSquaresInEnemyHalf(ctx, BLACK);
    int diff = white - black;

    if (Math.abs(diff) >= 4) {
        out.strong(tag("SPACE", "advantage", diff > 0 ? WHITE : BLACK), evidence("safe-square lead=" + Math.abs(diff)));
    } else if (ctx.options.spaceProfile()) {
        out.strong(tag("SPACE", "advantage", "equal"), evidence("safe-square lead below threshold"));
    }
}

void detectDevelopment(Context ctx, Emitter out) {
    if (ctx.phase == ENDGAME) {
        return;
    }
    DevelopmentScore white = developmentScore(ctx, WHITE);
    DevelopmentScore black = developmentScore(ctx, BLACK);
    emitDevelopmentLeadIfClear(out, white, black);
    emitUndevelopedBackRankPieces(ctx, out);
    emitUncastledKingIfUnsafe(ctx, out);
}

void detectMobility(Context ctx, Emitter out) {
    for (Side side : SIDES) {
        LegalMoves moves = ctx.legalMovesFor(side);
        if (ctx.options.mobilityProfile()) {
            out.exact(tag("MOBILITY", "legal", side, moves.size()), evidence("legal generator"));
        }
        if (moves.size() <= 5 && !ctx.root.isCheckmate() && !ctx.root.isStalemate()) {
            out.strong(tag("MOBILITY", "restricted", side), evidence("low legal move count"));
        }
    }
}

void detectInitiative(Context ctx, Emitter out) {
    ForceScore white = forcingMoveScore(ctx, WHITE);
    ForceScore black = forcingMoveScore(ctx, BLACK);
    ForceScore lead = white.minus(black);

    if (lead.sideHasClearEdge()) {
        out.strong(tag("INITIATIVE", "side", lead.side()), evidence(lead.explain()));
    }
}
```

### Tactical Motifs

```java
void detectStaticTactics(Context ctx, Emitter out) {
    detectPins(ctx, out);
    detectForksAndDoubleAttacks(ctx, out);
    detectSkewers(ctx, out);
    detectXrays(ctx, out);
    detectDiscoveredAttacks(ctx, out);
    detectTrappedPiecesAsTactics(ctx, out);
    detectOverloadingAndDefenders(ctx, out);
    detectRemovalDeflectionDecoy(ctx, out);
    detectInterferenceAndClearance(ctx, out);
    detectSacrifices(ctx, out);
    detectDesperado(ctx, out);
    detectQuietMoves(ctx, out);
    detectMatingNets(ctx, out);

    if (ctx.options.hasEngineOrLineContext()) {
        detectZwischenzug(ctx, out);
        detectPerpetualCheck(ctx, out);
        detectWindmill(ctx, out);
        detectMateInTwoPlus(ctx, out);
    }
}

void detectPins(Context ctx, Emitter out) {
    for (Side attacker : SIDES) {
        Side defender = attacker.opposite();
        for (Square target : criticalTargets(ctx, defender)) {
            for (Ray ray : raysFrom(target)) {
                PinCandidate c = firstEnemyBlockerThenSlider(ctx, attacker, defender, target, ray);
                if (c.exists() && !canLegallyMoveWithoutLoss(ctx, defender, c.blocker(), target)) {
                    String motif = targetContainsKing(ctx, target) ? "absolute_pin" : "relative_pin";
                    out.strong(tag("TACTIC", "motif", motif, "piece", c.blocker(), "target", target),
                               evidence("single blocker on slider line"));
                }
            }
        }
    }
}

void detectForksAndDoubleAttacks(Context ctx, Emitter out) {
    for (Move move : ctx.legalMovesForStm) {
        MoveEffect effect = ctx.effectOf(move);
        List<Square> targets = criticalTargetsAttackedByMovedPiece(effect);
        targets.removeIf(target -> targetIsTacticallyPoisoned(ctx, move, target));

        if (targets.size() >= 2) {
            String motif = allTargetsArePieces(targets) ? "fork" : "double_attack";
            out.strong(tag("TACTIC", "motif", motif, "move", move.uci(), "targets", targets),
                       evidence("moved piece attacks multiple critical targets"));
        }
    }
}

void detectSkewers(Context ctx, Emitter out) {
    for (LineAttack attack : sliderLineAttacks(ctx)) {
        if (value(attack.front()) > value(attack.behind())
                && attack.frontCanBeForcedToMove()
                && !attack.frontCanCaptureAttackerSafely()) {
            out.strong(tag("TACTIC", "motif", "skewer", "target", attack.front(), "behind", attack.behind()),
                       evidence("high-value front piece exposes piece behind"));
        }
    }
}

void detectXrays(Context ctx, Emitter out) {
    for (LineAttack attack : sliderLineAttacksThroughOnePiece(ctx)) {
        if (attack.behindIsCritical() && attack.blockerIsPinnedOverloadedOrRemovable()) {
            out.strong(tag("TACTIC", "motif", "xray", "target", attack.behind()),
                       evidence("critical target behind constrained blocker"));
        }
    }
}

void detectDiscoveredAttacks(Context ctx, Emitter out) {
    for (Move move : ctx.legalMovesForStm) {
        MoveEffect effect = ctx.effectOf(move);
        if (effect.opensSliderAttackOnCriticalTarget()) {
            String motif = effect.revealedCheck() ? "discovered_check" : "discovered_attack";
            out.strong(tag("TACTIC", "motif", motif, "move", move.uci(), "target", effect.revealedTarget()),
                       evidence("front piece vacates slider line"));
        }
        if (effect.checkerCount() >= 2) {
            out.exact(tag("TACTIC", "motif", "double_check", "move", move.uci()), evidence("two checkers after move"));
        }
    }
}
```

### Tactical Proof Windows

Some motifs need a bounded proof because static geometry alone is too noisy.

```java
void detectSacrifices(Context ctx, Emitter out) {
    for (Move move : ctx.legalMovesForStm) {
        if (!moveLosesMaterialImmediately(ctx, move)) {
            continue;
        }
        Proof proof = proofAfterMove(ctx, move, 1);
        if (proof.mates() || proof.winsMaterialAtLeast(value(PAWN)) || proof.engineConfirms()) {
            String motif = sacrificeMotif(move);
            out.strong(tag("TACTIC", "motif", motif, "move", move.uci()), evidence(proof.explain()));
        }
    }
}

void detectQuietMoves(Context ctx, Emitter out) {
    for (Move move : ctx.legalMovesForStm) {
        if (!move.isQuiet() || ctx.effectOf(move).givesCheck()) {
            continue;
        }
        Threat threat = strongestThreatAfter(ctx, move);
        if (threat.isConcrete() && opponentCannotIgnore(ctx, move, threat)) {
            out.strong(tag("TACTIC", "motif", "quiet_move", "move", move.uci()), evidence(threat.explain()));
        }
    }
}

void detectRemovalDeflectionDecoy(Context ctx, Emitter out) {
    for (Move move : ctx.legalMovesForStm) {
        MoveEffect effect = ctx.effectOf(move);

        if (effect.removesSoleDefenderOfCriticalTarget()) {
            out.strong(tag("TACTIC", "motif", "removal_of_defender", "move", move.uci()),
                       evidence("sole defender removed"));
        }
        if (effect.attacksOrLuresSoleDefenderAway() && forcedAcceptanceLoses(ctx, move)) {
            out.strong(tag("TACTIC", "motif", "deflection", "move", move.uci()),
                       evidence("defender cannot keep critical duty"));
        }
        if (effect.luresCriticalPieceToTacticalSquare() && forcedLineExists(ctx, move, 2)) {
            out.strong(tag("TACTIC", "motif", "decoy", "move", move.uci()),
                       evidence("forced lure with follow-up"));
        }
    }
}

void detectZwischenzug(Context ctx, Emitter out) {
    if (!ctx.hasPreviousCaptureOrExpectedRecapture()) {
        return;
    }
    for (Move move : ctx.legalMovesForStm) {
        if (move.isExpectedRecapture()) {
            continue;
        }
        if (move.isForcing() && resultAfter(move).beatsExpectedRecapture()) {
            out.strong(tag("TACTIC", "motif", "zwischenzug", "move", move.uci()),
                       evidence("forcing move improves expected recapture result"));
        }
    }
}
```

### CHECKMATE Patterns

```java
void detectCheckmatePatterns(Context ctx, Emitter out) {
    MateContext mate = MateContext.from(ctx);

    out.exact(tag("CHECKMATE", "winner", mate.winner()), evidence("checkmate"));
    out.exact(tag("CHECKMATE", "defender", mate.defender()), evidence("checkmate"));
    out.exact(tag("CHECKMATE", "delivery", mate.deliveryPieceOrMultiple()), evidence("checker list"));

    for (MatePattern pattern : MatePattern.values()) {
        if (pattern.matches(mate)) {
            out.strong(tag("CHECKMATE", "pattern", pattern.id()), evidence(pattern.explain(mate)));
        }
    }
}

boolean backRankMate(MateContext m) {
    return m.kingOnHomeRank()
        && m.deliveryBy(ROOK, QUEEN)
        && m.deliveryAlongRankOrFile()
        && m.flightSquares() == 0
        && m.escapeSquaresBlockedMostlyByOwnPiecesOrPawns();
}

boolean smotheredMate(MateContext m) {
    return m.singleCheckerIs(KNIGHT)
        && m.flightSquares() == 0
        && m.adjacentSquaresMostlyOccupiedByDefender()
        && !m.kingCanCaptureChecker();
}

boolean arabianMate(MateContext m) {
    return m.kingNearCorner()
        && m.singleCheckerIs(ROOK)
        && m.hasSupportPiece(KNIGHT)
        && m.knightControlsKeyFlights()
        && m.flightSquares() == 0;
}

boolean bodenMate(MateContext m) {
    return m.hasTwoAttackingBishops()
        && m.bishopsControlCrossingDiagonalsAroundKing()
        && m.defenderPiecesOrBoardBlockRemainingFlights();
}

boolean lawnmowerMate(MateContext m) {
    return m.deliveryBy(ROOK, QUEEN)
        && m.hasSecondHeavyPieceConfiningAdjacentRankOrFile()
        && m.kingConfinedByParallelHeavyPieceLines()
        && m.flightSquares() == 0;
}

boolean legalsMate(MateContext m) {
    return m.hasPgnContext()
        && m.pgnContext().matchesLegalTrapSequence()
        && m.positionIsCheckmate();
}
```

Every named mate predicate should be written as a positive geometry test plus at
least one near-miss rejection. If no near-miss can be expressed, the tag is not
ready.

### ENDGAME and OPENING

```java
void detectExactEndgames(Context ctx, Emitter out) {
    if (onlyKingsAndPawns(ctx)) {
        out.exact(tag("ENDGAME", "type", "pawn"), evidence("only kings and pawns"));
    }
    if (onlyPieceFamily(ctx, ROOK)) {
        out.exact(tag("ENDGAME", "type", "rook"), evidence("rook-only non-pawn pieces"));
    }
    if (onlyPieceFamily(ctx, BISHOP)) {
        out.exact(tag("ENDGAME", "type", "bishop"), evidence("bishop-only non-pawn pieces"));
    }
    if (onlyPieceFamily(ctx, KNIGHT)) {
        out.exact(tag("ENDGAME", "type", "knight"), evidence("knight-only non-pawn pieces"));
    }
    if (onlyMinorPiecesAndPawns(ctx)) {
        out.exact(tag("ENDGAME", "type", "minor_piece"), evidence("minor pieces only"));
    }
    emitOppositeAndSameColorBishopEndgames(ctx, out);
    emitPrimaryMaterialEndgames(ctx, out);
}

void detectStrongEndgameThemes(Context ctx, Emitter out) {
    if (ctx.phase != ENDGAME) {
        return;
    }
    emitOutsidePasser(ctx, out);
    emitConnectedPassers(ctx, out);
    emitWrongRookPawnIfDefenderCanReachCorner(ctx, out);

    if (ctx.options.includesWeakDebug() && looksLikeFortress(ctx)) {
        out.weak(tag("ENDGAME", "theme", "fortress_candidate"), evidence("blocked geometry"));
    }
    if (ctx.options.includesWeakDebug() && looksLikeZugzwang(ctx)) {
        out.weak(tag("ENDGAME", "theme", "zugzwang_candidate"), evidence("all moves worsen static facts"));
    }
}

void detectOpenings(Context ctx, Emitter out) {
    OpeningMatch match = ctx.options.openingBook().find(ctx.root.normalizedOpeningKey());
    if (match == null) {
        return;
    }
    out.exact(tag("OPENING", "eco", match.eco()), evidence("book match"));
    out.exact(tag("OPENING", "name", match.name()), evidence("book match"));
    if (match.familyKnown()) {
        out.exact(tag("OPENING", "family", match.family()), evidence("book field"));
    }
    if (match.variationKnown()) {
        out.exact(tag("OPENING", "variation", match.variation()), evidence("book field"));
    }
}
```

### CAND, PV, THREAT, and IDEA

```java
void detectAnalysisTags(Context ctx, Emitter out) {
    Analysis analysis = ctx.options.analysis();
    if (analysis == null) {
        return;
    }

    for (Candidate c : analysis.candidates()) {
        out.emit(tag("CAND", "rank", c.rank(), "move", c.move(), "eval", c.eval()),
                 ENGINE_PROVED, evidence("analysis candidate"));
    }
    for (int ply = 0; ply < analysis.pv().size(); ply++) {
        out.emit(tag("PV", "ply", ply + 1, "move", analysis.pv().get(ply)),
                 ENGINE_PROVED, evidence("principal variation"));
    }
    for (Threat threat : analysis.threats()) {
        if (threat.isConcrete()) {
            out.emit(tag("THREAT", "type", threat.type(), "side", threat.side()),
                     ENGINE_PROVED, evidence(threat.explain()));
        }
    }
}

void detectIdeaTags(Context ctx, Emitter out) {
    TagSet before = tag(ctx.root, ctx.options.staticOnly()).withoutIdeaTags();

    for (Move move : ctx.legalMovesForStm) {
        State state = ctx.root.play(move);
        try {
            TagSet after = tag(ctx.root, ctx.options.staticOnly()).withoutIdeaTags();
            for (Tag created : after.minus(before).importantOnly()) {
                out.strong(tag("IDEA", "move", move.uci(), "creates", created.identity()),
                           evidence("tag appears after legal move"));
            }
            for (Tag removed : before.minus(after).badOrThreatOnly()) {
                out.strong(tag("IDEA", "move", move.uci(), "removes", removed.identity()),
                           evidence("tag disappears after legal move"));
            }
        } finally {
            ctx.root.undo(move, state);
        }
    }
}
```

### Fixture Runner

```java
void runTagFixtures(Path root) {
    int rows = 0;
    int failures = 0;

    for (Path file : fixtureFiles(root)) {
        for (Fixture fixture : readFixture(file)) {
            rows++;
            Position position = Position.fromFen(fixture.fen());
            TagResult tags = tag(position, fixture.options());

            for (Tag expected : fixture.mustContain()) {
                if (!tags.contains(expected)) {
                    failures++;
                    printMissing(file, fixture, expected, tags);
                }
            }
            for (Tag forbidden : fixture.mustNotContain()) {
                if (tags.contains(forbidden)) {
                    failures++;
                    printUnexpected(file, fixture, forbidden, tags.explain(forbidden));
                }
            }
        }
    }

    printSummary(rows, failures);
    if (failures > 0) {
        throw new AssertionError("tag fixture failures");
    }
}
```

### Reliability Checklist Per Detector

Before adding a detector, write the following in code comments or fixture notes:

```text
detector: <family/name>
input facts: <bitboards, legal moves, attacks, analysis, PGN context>
positive rule: <when to emit>
omit rule: <near-miss conditions>
reliability: exact | static-strong | static-weak | engine-proved | history-dependent
fixtures: <positive ids>
near misses: <negative ids>
explain text: <short reason shown by --explain-tags>
```

## External Theme Mapping

The canonical tags should cover source-site names without duplicating concepts.

### Lichess Theme Mapping

| Lichess theme | Canonical tag |
| --- | --- |
| `advancedPawn` | `PAWN: structure=advanced ...` |
| `advantage` | engine `TACTIC: motif=advantage` or eval bucket |
| `anastasiaMate` | `CHECKMATE: pattern=anastasias_mate` |
| `arabianMate` | `CHECKMATE: pattern=arabian_mate` |
| `attackingF2F7` | `TACTIC: motif=attacking_f2_f7 ...` |
| `attraction` | `TACTIC: motif=decoy ...` or `motif=attraction` alias |
| `backRankMate` | `CHECKMATE: pattern=back_rank_mate` |
| `bishopEndgame` | `ENDGAME: type=bishop` |
| `bodenMate` | `CHECKMATE: pattern=boden_mate` |
| `capturingDefender` | `TACTIC: motif=removal_of_defender ...` |
| `castling` | `MOVE: castles=<n>` or `FACT: castling=...` |
| `clearance` | `TACTIC: motif=clearance ...` |
| `crushing` | engine eval bucket |
| `defensiveMove` | `TACTIC: motif=defensive_move move=<uci>` |
| `deflection` | `TACTIC: motif=deflection ...` |
| `discoveredAttack` | `TACTIC: motif=discovered_attack ...` |
| `doubleCheck` | `TACTIC: motif=double_check ...` |
| `dovetailMate` | `CHECKMATE: pattern=dovetail_mate` |
| `endgame` | `META: phase=endgame` |
| `enPassant` | `MOVE: en_passant=<n>` or `FACT: en_passant=<square>` |
| `equality` | engine eval bucket |
| `exposedKing` | `KING: exposed side=<side>` |
| `fork` | `TACTIC: motif=fork ...` |
| `hangingPiece` | `PIECE: hanging ...` or `TACTIC: motif=hanging ...` |
| `interference` | `TACTIC: motif=interference ...` |
| `intermezzo` | `TACTIC: motif=zwischenzug ...` |
| `kingsideAttack` | `KING: vulnerable side=<side>` plus flank field |
| `knightEndgame` | `ENDGAME: type=knight` |
| `mateIn1` | `MOVE: mates=<n>` and/or `TACTIC: motif=mate_in_1` |
| `mateIn2+` | bounded engine/PV tag |
| `middlegame` | `META: phase=middlegame` |
| `opening` | `META: phase=opening` or `OPENING: ...` |
| `pawnEndgame` | `ENDGAME: type=pawn` |
| `pin` | `TACTIC: motif=pin ...` |
| `promotion` | `MOVE: promotions=<n>` / `PAWN: promotion_ready ...` |
| `queenEndgame` | `ENDGAME: type=queen` |
| `queensideAttack` | king/flank attack tags |
| `quietMove` | `TACTIC: motif=quiet_move move=<uci>` |
| `rookEndgame` | `ENDGAME: type=rook` |
| `sacrifice` | `TACTIC: motif=sacrifice ...` |
| `skewer` | `TACTIC: motif=skewer ...` |
| `smotheredMate` | `CHECKMATE: pattern=smothered_mate` |
| `trappedPiece` | `PIECE: trapped ...` |
| `underPromotion` | `PAWN: underpromotion_available ...` |
| `xRayAttack` | `TACTIC: motif=xray ...` |
| `zugzwang` | `ENDGAME: theme=zugzwang_candidate` or engine-confirmed tag |

### Chess.com Theme Mapping

| Chess.com tag | Canonical tag |
| --- | --- |
| Back Rank | `KING: back_rank_weak` or `CHECKMATE: pattern=back_rank_mate` |
| Fork / Double Attack | `TACTIC: motif=fork` |
| Hanging Piece | `PIECE: hanging` |
| Mate in One | `MOVE: mates` / `TACTIC: motif=mate_in_1` |
| Pin | `TACTIC: motif=pin` |
| Smothered Mate | `CHECKMATE: pattern=smothered_mate` |
| Stalemate | `FACT: status=stalemate` |
| Attacking f7/f2 | `TACTIC: motif=attacking_f2_f7` |
| Attacking the Castled King | `KING: vulnerable ... flank=<king|queen>` |
| Clearance Sacrifice | `TACTIC: motif=clearance` plus `motif=sacrifice` |
| Decoy / Deflection | `TACTIC: motif=decoy` / `motif=deflection` |
| Defense | `TACTIC: motif=defensive_move` |
| Desperado | `TACTIC: motif=desperado` |
| Discovered Attack / Check | `TACTIC: motif=discovered_attack/check` |
| Double Check | `TACTIC: motif=double_check` |
| En Passant | `MOVE: en_passant` |
| Endgame Tactic | `META: phase=endgame` plus tactic motif |
| Exchange Sacrifice | `TACTIC: motif=exchange_sacrifice` |
| Interference | `TACTIC: motif=interference` |
| Mating Net | `TACTIC: motif=mating_net` |
| Overloading | `TACTIC: motif=overloading` |
| Pawn Promotion | `MOVE: promotions` |
| Perpetual Check | `TACTIC: motif=perpetual_check` |
| Queen Sacrifice | `TACTIC: motif=queen_sacrifice` |
| Removal of Defender | `TACTIC: motif=removal_of_defender` |
| Sacrifice | `TACTIC: motif=sacrifice` |
| Simplification | `TACTIC: motif=simplification` |
| Skewer | `TACTIC: motif=skewer` |
| Trapped Piece | `PIECE: trapped` |
| Underpromotion | `PAWN: underpromotion_available` |
| Vulnerable King | `KING: vulnerable` |
| Windmill | `TACTIC: motif=windmill` |
| X-Ray Attack | `TACTIC: motif=xray` |
| Zugzwang / Zwischenzug | `ENDGAME: theme=zugzwang_candidate` / `TACTIC: motif=zwischenzug` |

## Fixture Strategy

Create test fixtures under:

```text
testdata/tags/
  facts.tsv
  material.tsv
  pawns.tsv
  king.tsv
  tactics.tsv
  mates.tsv
  endgames.tsv
  openings.tsv
```

Each row:

```text
id<TAB>fen<TAB>must_contain_tags<TAB>must_not_contain_tags
```

Use `;` to separate tags inside the tag columns.

Example:

```text
back_rank_mate_001	4R1k1/5ppp/8/8/8/8/8/6K1 b - - 0 1	CHECKMATE: pattern=back_rank_mate;FACT: status=checkmate	CHECKMATE: pattern=smothered_mate
```

Add a small zero-dependency runner:

```text
testing.TagFixtureRegressionTest
```

The runner should:

1. Load every TSV.
2. Parse FEN with `Position.fromFen`.
3. Generate tags with canonical `chess.tag.Generator`.
4. Assert every required tag exists.
5. Assert every forbidden tag is absent.
6. Print a concise summary.

## Regression Test Plan

Required tests:

- `TaggingRegressionTest`: smoke tests for key existing tags.
- `TagFixtureRegressionTest`: broad fixture-driven coverage.
- `ParserRegressionTest`: `Line`, `Identity`, `Sort`, and delta identity behavior.
- `TagDeltaRegressionTest`: enabled/disabled tags across a short line.
- `TagPerformanceRegressionTest`: optional, local-only benchmark on hundreds of FENs.

Test categories:

- positive fixtures
- near-miss negative fixtures
- malformed FEN rejection via existing FEN parser
- Chess960 fixtures
- promotion/en-passant/castling fixtures
- low-material endgame fixtures
- tactical ambiguity fixtures

## Performance Budget

Initial target:

- Static tags: under 1 ms per ordinary middlegame FEN on a development laptop.
- Heavy static tactics: under 3 ms per FEN.
- No-engine batch tagging: capable of tens of thousands of FENs per minute.
- Engine-enriched tags: budget controlled by existing engine search limits.

Rules:

- Generate legal moves once per side unless a detector explicitly needs more.
- Prefer attack maps and bitboards over repeated board scans.
- Use `play/undo` with reusable state for move consequence checks.
- Avoid allocating `Position.copy()` inside per-move loops except in simple
  non-hot code or tests.
- Add benchmark commands only after correctness is stable.

## CLI Plan

Existing commands should stay:

- `fen tags`
- `puzzle tags`
- `record tag-stats`
- `record analysis-delta`
- `fen text`
- `puzzle text`

Add flags only when needed:

- `--tag-profile static|engine|all`
- `--tag-family <family>` repeated or comma-separated
- `--include-counters`
- `--include-debug-reasons`
- `--format text|json|jsonl`
- `--explain-tags` for one position, with detector reasons

Do not add flags before the static pipeline is stable.

## Documentation Plan

Update:

- `wiki/piece-tags.md`: keep as piece activity reference.
- `wiki/tagging-implementation-plan.md`: this plan.
- `wiki/command-reference.md`: tag command flags and output examples.
- `wiki/example-commands.md`: examples for `fen tags`, `record tag-stats`,
  deltas, and fixture verification.
- `wiki/ai-agents.md`: recommended deterministic tag commands.
- `README.md`: one-line link to tag docs when tag work lands.

Maintain the dedicated reference page:

```text
wiki/tag-reference.md
```

That page should list every canonical tag, fields, examples, and whether the tag
is static, engine-derived, or config-derived.

## Regression Fixtures

Regression coverage targets canonical `Generator` output for:

   - start position
   - checkmate
   - pin
   - hanging piece
   - pawn structure
   - Chess960 castling
   - en-passant
   - promotion

## Milestone Checklist

### Milestone A: Foundation

- [x] Add `Context`.
- [x] Add `Emitter`.
- [x] Add detector contract.
- [x] Add fixture regression runner.
- [x] Add tag reference skeleton.
- [x] Confirm current `chess.tag` output stays stable.

### Milestone B: Prototype Parity

- [ ] Port meta tags.
- [ ] Port fact tags.
- [ ] Port material tags.
- [ ] Port move count tags.
- [ ] Port king exposure tags.
- [ ] Port pawn structure tags.
- [ ] Port pin/hanging/fork tags.
- [ ] Port existing checkmate tags.
- [x] Switch `TaggingRegressionTest` to `chess.tag`.
- [x] Keep one canonical tag package.

### Milestone C: Structure Tags

- [ ] Add complete pawn structure.
- [ ] Add king safety.
- [ ] Add piece activity and pressure.
- [ ] Add space/development/mobility.
- [ ] Add endgame classifiers.

### Milestone D: Tactical Tags

- [ ] Add skewers.
- [ ] Add x-rays.
- [ ] Add discovered attacks.
- [ ] Add discovered checks.
- [ ] Add double checks.
- [ ] Add trapped pieces.
- [ ] Add overloading.
- [ ] Add removal of defender.
- [ ] Add deflection/decoy.
- [ ] Add clearance/interference.
- [ ] Add sacrifices.
- [ ] Add quiet moves.
- [ ] Add zwischenzug.
- [ ] Add perpetual-check and windmill candidates.

### Milestone E: Mate Patterns

- [ ] Add mate context.
- [ ] Add all high-priority named mates.
- [ ] Add medium-priority named mates.
- [ ] Add low-priority named mates only when predicates are clear.
- [ ] Document excluded/history-dependent mate names.

### Milestone F: Engine-Enriched Tags

- [ ] Normalize eval/WDL tags.
- [ ] Normalize PV tags.
- [ ] Normalize candidate move tags.
- [ ] Add threat tags from bounded analysis.
- [ ] Add mate-in-N tags from PV/engine.
- [ ] Add `--tag-profile` if needed.

### Milestone G: Docs And Release Readiness

- [x] Write `wiki/tag-reference.md`.
- [x] Update command examples.
- [ ] Add performance notes.
- [ ] Add migration notes for renamed tags.
- [ ] Run full regression suite.

## Definition Of Done For "All Tags"

The tag implementation is complete enough when:

1. Every implemented tag is documented with fields and examples.
2. Every tag family has fixture coverage.
3. Every backlog item is either implemented, mapped to another canonical tag,
   or explicitly excluded with a reason.
4. No duplicate tag pipeline exists.
5. `fen tags` works without engine dependencies by default.
6. Engine-enriched tags are opt-in and clearly marked by source/profile.
7. `record tag-stats` remains useful and does not explode with duplicate aliases.
8. Tag output is stable across repeated runs.
9. Performance is acceptable for large FEN batches.

## Risks

- Some named mate patterns are historically defined by move sequence, not final
  position. Those should not be forced into FEN-only detection.
- Some tactical motifs need engine or multi-ply proof. Static approximations can
  create false positives if emitted too confidently.
- Over-tagging can make outputs noisy and reduce usefulness for LLM summaries.
- Renaming tags can break user filters and training labels.
- Adding many detectors without shared context can slow batch tagging.

## Recommended Next Task

Start with release-readiness cleanup and the next conservative detector slice:

1. Add performance notes for large FEN and puzzle batches.
2. Add migration notes for renamed or canonicalized tags.
3. Expand fixture coverage for emitted families that still rely on ad hoc tests.
4. Pick the next tactical motif only when it has a stable final-position predicate.

Keep noisy or history-dependent motifs documented as excluded until they have a
clear evidence rule.
