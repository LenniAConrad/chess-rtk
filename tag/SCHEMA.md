# Tag Schema

This is the schema implemented by the current source tree. It is intentionally
conservative: list only formats that the code currently emits or explicitly
normalizes.

## General Rules

- Tags are plain strings.
- The family prefix is uppercase and followed by `: `.
- Field keys and enum values are lowercase unless the value is an ECO code.
- Use `side=white|black` for a real side and `side=equal` only for aggregate
  strategic families.
- Squares are lowercase algebraic squares such as `e4`.
- UCI move fields appear on `MOVE: only` and generated tactical tags.
- SAN move fields appear on `CAND`, `PV`, and engine threat tags.
- Quoted fields are used for text values that can contain spaces or punctuation.

## Canonical Sort Order

`Sort.sort` removes blank entries, sorts by this family order, then removes
duplicates:

```text
FACT META MOVE THREAT CAND PV IDEA TACTIC CHECKMATE PIECE KING PAWN
MATERIAL SPACE INITIATIVE DEVELOPMENT MOBILITY OUTPOST ENDGAME OPENING
```

Unknown families sort after known families.

## Families And Fields

### META

Core position metadata:

```text
META: to_move=white|black
META: phase=opening|middlegame|endgame
META: source=engine|analysis
META: eval_cp=<int>
META: eval_bucket=equal|slight_white|slight_black|clear_white|clear_black|winning_white|winning_black|crushing_white|crushing_black
META: mate_in=<int>
META: mated_in=<int>
META: wdl=<win>/<draw>/<loss>
META: difficulty=very_easy|easy|medium|hard|very_hard
```

CLI-only or helper metadata:

```text
META: fen="<FEN>"
META: puzzle_goal=win|draw|unknown
META: puzzle_rating=<int>
META: puzzle_difficulty=very_easy|easy|medium|hard|very_hard
META: puzzle_difficulty_score=<0.00..1.00>
META: puzzle_variations=<int>
META: puzzle_branch_points=<int>
META: puzzle_solution_plies=<int>
META: puzzle_features="<comma-separated feature names>"
```

Terminal checkmate emits `META: mated_in=0` and intentionally does not emit
`META: eval_cp` or `META: eval_bucket`.

### FACT

Canonical position facts:

```text
FACT: status=normal|check|checkmated|stalemate|insufficient
FACT: in_check=white|black|none
FACT: castle_rights=KQkq|KQk|KQq|KQ|Kkq|Kk|Kq|K|Qkq|Qk|Qq|Q|kq|k|q|none
FACT: en_passant=<square>
FACT: center_control=white|black|balanced
FACT: center_state=open|closed
FACT: space_advantage=white|black
```

`chess.tag.eval.Summary` can also emit:

```text
FACT: puzzle=winning|draw
```

Lower-level helper facts are internal inputs. `Generator` normalizes them into
the canonical public families below.

### MOVE

Exact legal-move facts for the side to move:

```text
MOVE: legal=<int>
MOVE: captures=<int>
MOVE: checks=<int>
MOVE: mates=<int>
MOVE: promotions=<int>
MOVE: underpromotions=<int>
MOVE: castles=<int>
MOVE: en_passant=<int>
MOVE: quiet=<int>
MOVE: only=<uci>
MOVE: forced=true
MOVE: evasions=<int>
```

`MOVE: legal=<int>` is always emitted. Other count tags are emitted only when
the count is non-zero, except `MOVE: evasions=<int>`, which is emitted whenever
the side to move is in check. `MOVE: only` and `MOVE: forced=true` are emitted
only when exactly one legal move exists.

### THREAT

Promotion availability:

```text
THREAT: type=promote side=white|black severity=immediate square=<square>
```

Engine null-move threat analysis:

```text
THREAT: side=white|black severity=immediate|soon|latent type=mate|promote|material|tactic move="<SAN>"
```

The null-move threat path is only used by engine-backed CLI flows and is skipped
when the base position is already in check.

### CAND

Candidate moves from engine analysis:

```text
CAND: role=best move=<SAN> eval_cp=<int> note=""
CAND: role=alt move=<SAN> eval_cp=<int> note=""
```

`role=alt` is emitted when `multipv >= 2` and the analysis contains a second PV.

### PV

Principal variation from engine analysis:

```text
PV: <SAN> <SAN> <SAN>
```

The current `Generator` implementation keeps up to six plies.

### TACTIC

Static motifs from the current board:

```text
TACTIC: motif=pin side=white|black detail="<text>"
TACTIC: motif=skewer side=white|black detail="<text>"
TACTIC: motif=overload side=white|black detail="<text>"
TACTIC: motif=hanging side=white|black detail="<text>"
```

Move-specific tactical motifs found by checking legal moves:

```text
TACTIC: motif=mate_in_1 side=white|black move=<uci>
TACTIC: motif=promotion side=white|black move=<uci> piece=queen square=<square>
TACTIC: motif=underpromotion side=white|black move=<uci> piece=bishop|knight|rook square=<square>
TACTIC: motif=fork side=white|black move=<uci> piece=pawn|knight|bishop|rook|queen square=<square> targets=<piece@square,piece@square>
TACTIC: motif=skewer side=white|black move=<uci> piece=bishop|rook|queen square=<square> front=<piece@square> behind=<piece@square>
TACTIC: motif=discovered_attack side=white|black move=<uci> piece=pawn|knight|bishop|rook|queen|king square=<square> slider=<piece@square> target=<piece@square>
```

Target labels use `piece@square`, for example `queen@h5`.

### CHECKMATE

Actual checkmate attributes. These are emitted only when the current position is
checkmate:

```text
CHECKMATE: winner=white|black
CHECKMATE: defender=white|black
CHECKMATE: delivery=pawn|knight|bishop|rook|queen|king|multiple
CHECKMATE: pattern=double_check|back_rank_mate|smothered_mate|corner_mate|support_mate
```

Pattern tags are conservative and may be absent even when a human would name the
mate pattern.

### PIECE

Piece strength and activity:

```text
PIECE: tier=very_strong|strong|slightly_strong|neutral|slightly_weak|weak|very_weak side=white|black piece=pawn|knight|bishop|rook|queen|king square=<square>
PIECE: extreme=strongest|weakest|strongest_white|weakest_white|strongest_black|weakest_black side=white|black piece=pawn|knight|bishop|rook|queen|king square=<square>
PIECE: activity=pin|trapped|low_mobility|high_mobility side=white|black piece=pawn|knight|bishop|rook|queen|king square=<square>
```

### KING

King status:

```text
KING: castled=yes|no side=white|black
KING: shelter=pawns_intact|weakened|open side=white|black
KING: safety=very_safe|safe|unsafe|very_unsafe side=white|black
```

King tags are skipped for Chess960 positions.

### PAWN

Pawn structure:

```text
PAWN: structure=isolated side=white|black square=<square>
PAWN: structure=passed side=white|black square=<square>
PAWN: structure=backward side=white|black square=<square>
PAWN: structure=doubled side=white|black file=<file>
PAWN: structure=connected_passed side=white|black squares=<square,square>
PAWN: islands side=white|black count=<int>
PAWN: majority=queenside|center|kingside side=white|black
```

### MATERIAL

Material summary:

```text
MATERIAL: balance=equal|white_up_pawn|black_up_pawn|white_up_minor|black_up_minor|white_up_exchange|black_up_exchange|white_up_queen|black_up_queen
MATERIAL: imbalance=bishop_pair_white|bishop_pair_black|queenless|rookless|opposite_color_bishops|same_color_bishops
MATERIAL: piece_count side=white|black piece=pawn|knight|bishop|rook|queen|king count=<int>
```

### Strategic Families

```text
SPACE: side=white|black|equal
DEVELOPMENT: side=white|black|equal
MOBILITY: side=white|black|equal
INITIATIVE: side=white|black|equal
OUTPOST: side=white|black square=<square> piece=knight|bishop
```

Threat-aware CLI flows replace existing `INITIATIVE` tags when one side has an
engine-backed threat.

### ENDGAME

```text
ENDGAME: type=queenless|rook|minor|opposite_bishops
```

### OPENING

```text
OPENING: eco=<ECO>
OPENING: name="<opening name>"
```

Opening tags are skipped for Chess960 positions. CLI sequence and delta flows
can inherit missing opening tags from a parent position.

## Identity Keys

`Delta.diff` parses each tag into fields and compares tags by identity. Matching
identity with changed raw text goes into `changed`; otherwise tags are `added`
or `removed`.

| Family | Identity |
| --- | --- |
| `META` | family plus first key, for example `META:eval_cp` |
| `FACT` | family plus first key |
| `MOVE` | family plus first key |
| `CHECKMATE: pattern` | `CHECKMATE:pattern:<pattern>` |
| other `CHECKMATE` | family plus first key |
| `MATERIAL: piece_count` | `MATERIAL:piece_count:<side>:<piece>` |
| `MATERIAL: imbalance` | `MATERIAL:imbalance:<imbalance>` |
| other `MATERIAL` | family plus first key |
| `PIECE: tier` | `PIECE:tier:<side>:<piece>:<square>` |
| `PIECE: extreme` | `PIECE:extreme:<extreme>` |
| `PIECE: activity` | `PIECE:activity:<activity>:<side>:<piece>:<square>` |
| `PAWN: structure` | `PAWN:structure:<structure>:<side>:<square|file|squares>` |
| `PAWN: islands` | `PAWN:islands:<side>` |
| `PAWN: majority` | `PAWN:majority:<side>` |
| `KING` | `KING:<first-key>:<side>` |
| static `TACTIC` with detail | `TACTIC:<motif>:<detail>` |
| move `TACTIC` | `TACTIC:<motif>:<move>` plus target/front/behind/targets/square suffix when present |
| `THREAT` | `THREAT:<type>:<side>` |
| `CAND` | `CAND:<role>` |
| `PV` | `PV` |
| `SPACE`, `INITIATIVE`, `DEVELOPMENT`, `MOBILITY` | family name |
| `OUTPOST` | `OUTPOST:<side>:<square>:<piece>` |
| `ENDGAME` | family plus first key and value |
| `OPENING` | family plus first key |
| unknown | raw tag text |

## Delta JSON

The compact delta object has this shape:

```json
{
  "added": ["TACTIC: motif=mate_in_1 side=white move=d1d8"],
  "removed": ["FACT: status=normal"],
  "changed": [
    {
      "key": "META:eval_cp",
      "from": "META: eval_cp=10",
      "to": "META: eval_cp=45"
    }
  ]
}
```

The surrounding CLI JSONL row adds `index`, `game_index`, `parent`, `fen`,
`move_san`, `move_uci`, `tags`, and `delta`.
