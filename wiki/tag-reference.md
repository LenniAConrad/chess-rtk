# Tag Reference

ChessRTK tags are deterministic strings with a family prefix and ordered
key/value fields:

```text
FAMILY: key=value key=value
```

Values containing whitespace or quotes are quoted and escaped. Tags are sorted
and deduplicated by `chess.tag.Sort`, parsed by `chess.tag.Line`, and compared
by semantic identity for delta output.

## Families

| Family | Status | Typical fields | Notes |
| --- | --- | --- | --- |
| `META` | emitted | `to_move`, `phase`, `source`, `eval_cp`, `eval_bucket`, `mate_in`, `mated_in`, `wdl`, `difficulty` | Position and analysis metadata. |
| `FACT` | emitted | `status`, `in_check`, `castle_rights`, `en_passant`, `center_state` | Exact position facts. |
| `MATERIAL` | emitted | `balance`, `imbalance`, `piece_count`, `side`, `piece`, `count` | Material balance and imbalances. |
| `MOVE` | emitted | `legal`, `captures`, `checks`, `mates`, `promotions`, `underpromotions`, `castles`, `en_passant`, `quiet`, `only`, `forced`, `evasions` | Exact aggregate legal move facts. |
| `CAND` | emitted with analysis | `role`, `move`, `score`, `wdl` | Engine candidate moves. |
| `PV` | emitted with analysis | `move`, `line`, `score`, `wdl` | Principal variation data. |
| `IDEA` | inactive | `plan`, `move`, `side` | No current `Generator` output. |
| `THREAT` | emitted | `type`, `side` | Current static threat tags focus on promotion threats. |
| `KING` | emitted | `castled`, `shelter`, `safety`, `side` | King state and safety. |
| `PAWN` | emitted | `structure`, `side`, `square`, `squares`, `file`, `islands`, `majority` | Pawn structure and promotion readiness. |
| `PIECE` | emitted | `tier`, `activity`, `extreme`, `side`, `piece`, `square` | Piece placement and activity. |
| `TACTIC` | emitted | `motif`, `side`, `move`, `detail`, `target`, `targets` | Static tactical motifs and move-specific tactics. |
| `CHECKMATE` | emitted | `winner`, `defender`, `delivery`, `pattern` | Actual mate attributes and conservative named mate patterns. Current status also appears in `FACT`. |
| `ENDGAME` | emitted | `type` | Material endgame classes. |
| `OPENING` | emitted when ECO data matches | `eco`, `name` | ECO code and opening label. |
| `SPACE` | emitted | `side`, `center_control`, `space_advantage` | Center and space signals. |
| `DEVELOPMENT` | emitted | `side`, `state` | Development lead and undeveloped-piece signals. |
| `MOBILITY` | emitted | `side`, `state` | Mobility comparisons and restrictions. |
| `INITIATIVE` | emitted | `side`, `state` | Tempo and forcing-move signals. |
| `OUTPOST` | emitted | `side`, `square`, `piece` | Compatibility family for existing outpost output. |

`MOVE` and `CHECKMATE` are active canonical families emitted by
`chess.tag.Generator`; no compatibility aliases are emitted for older drafts.

## Identity Rules

Delta output compares tags by semantic identity rather than raw text alone.
Examples:

```text
META: to_move=white        -> META:to_move
FACT: status=normal        -> FACT:status
MOVE: legal=20             -> MOVE:legal
CHECKMATE: delivery=queen  -> CHECKMATE:delivery
CHECKMATE: pattern=smothered_mate -> CHECKMATE:pattern:smothered_mate
```

Count and single-attribute tags normally keep identity by field key so value
changes are reported as changes. Multi-valued concepts such as mate patterns
include the value in the identity so separate patterns can coexist.

## Fixture Format

Static tag fixtures live under `testdata/tags/` as TSV files:

```text
id<TAB>fen<TAB>must_contain_tags<TAB>must_not_contain_tags
```

Tag columns use semicolons to separate exact tag strings. Run the fixture suite
with:

```bash
./scripts/run_regression_suite.sh core
```
