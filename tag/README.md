# ChessRTK Tag Documentation

Last refreshed against the local source tree on 2026-05-06.

This folder documents the current chess tag pipeline. It keeps maintained
references that match the implementation
under `src/chess/tag`, the puzzle difficulty tags under `src/chess/puzzle`, and
the CLI commands that emit tag output.

## Files

- `README.md` is the operational overview.
- `SCHEMA.md` is the canonical tag and delta reference.
- `PROMPTS.md` is the current guidance for tag-to-text prompts and T5 use.

## What Tags Are

Tags are deterministic, line-oriented chess facts. A tag normally has this form:

```text
FAMILY: key=value key=value
```

The current canonical families are:

```text
FACT META MOVE THREAT CAND PV IDEA TACTIC CHECKMATE PIECE KING PAWN
MATERIAL SPACE INITIATIVE DEVELOPMENT MOBILITY OUTPOST ENDGAME OPENING
```

`IDEA` is present in the sorter and identity order, but there is no active
producer in the current source tree. `MOVE` and `CHECKMATE` are active canonical
families emitted by `Generator`.

## Source Map

The main entry point is `chess.tag.Generator`.

| Area | Current source |
| --- | --- |
| Canonical tag assembly | `src/chess/tag/Generator.java` |
| Sorting and dedupe | `src/chess/tag/Sort.java` |
| Tag parsing | `src/chess/tag/Line.java` |
| Delta identity keys | `src/chess/tag/Identity.java` |
| Delta JSON | `src/chess/tag/Delta.java` |
| Shared literals | `src/chess/tag/core/Literals.java` |
| Evaluation summary helper | `src/chess/tag/eval/Summary.java` |
| Exact move facts and checkmate attributes | `src/chess/tag/MoveFacts.java`, `src/chess/tag/Checkmate.java` |
| Material | `src/chess/tag/material/Counts.java`, `Endgame.java` |
| Piece activity | `src/chess/tag/piece/Ablation.java`, `Activity.java` |
| Pawn features | `src/chess/tag/pawn/Structure.java`, `Promotion.java` |
| Position features | `src/chess/tag/position/CenterSpace.java`, `KingSafety.java`, `Opening.java` |
| Tactics | `src/chess/tag/tactical/Motifs.java` |
| Move/line helper tags | `src/chess/tag/move/Description.java`, `Mainline.java`, `Sequence.java` |
| Puzzle difficulty metadata | `src/chess/puzzle/difficulty/Difficulty.java` |

## CLI Entry Points

Generate tags for one FEN:

```bash
crtk fen tags --fen "<FEN>"
```

Generate tags from a FEN list or record file:

```bash
crtk fen tags --input positions.txt
crtk fen tags --input records.json --sequence --delta
```

Generate tags from PGN:

```bash
crtk fen tags --pgn game.pgn --mainline
crtk fen tags --pgn game.pgn --sidelines
```

Enrich tags with UCI engine analysis:

```bash
crtk fen tags --fen "<FEN>" --analyze --multipv 3 --max-nodes 100000
```

Generate puzzle line tags and per-move deltas:

```bash
crtk puzzle tags --fen "<FEN>" --multipv 3 --pv-plies 12
```

Use tags as T5 input:

```bash
crtk fen text --fen "<FEN>" --model path/to/model.bin
crtk puzzle text --fen "<FEN>" --model path/to/model.bin
```

Summarize existing records:

```bash
crtk record tag-stats --input records.json
crtk record analysis-delta --input records.json
```

## Output Shapes

Without `--delta`, `fen tags` prints one JSON string array per position:

```json
["FACT: status=normal","META: to_move=white","MATERIAL: balance=equal"]
```

With `--delta`, `fen tags` and `puzzle tags` print JSONL records:

```json
{"index":1,"game_index":null,"parent":"<parent-fen>","fen":"<fen>","move_san":"Nf3","move_uci":"g1f3","tags":["..."],"delta":{"added":["..."],"removed":["..."],"changed":[{"key":"META:eval_cp","from":"META: eval_cp=10","to":"META: eval_cp=45"}]}}
```

`--include-fen` adds `META: fen="<FEN>"` to non-delta tag arrays. Delta output
already has top-level `parent` and `fen` fields, so `--include-fen` is not added
inside `tags` there.

## Current Behavior Notes

- Tags are sorted by family priority, then lexicographically within each family,
  then deduplicated.
- The current family order is `FACT`, `META`, `MOVE`, `THREAT`, `CAND`, `PV`,
  `IDEA`, `TACTIC`, `CHECKMATE`, `PIECE`, `KING`, `PAWN`, `MATERIAL`, `SPACE`,
  `INITIATIVE`, `DEVELOPMENT`, `MOBILITY`, `OUTPOST`, `ENDGAME`, `OPENING`.
- `Generator` emits exact legal-move counts in the `MOVE` family and actual
  mate attributes in the `CHECKMATE` family.
- `Generator.tags(position, analysis)` emits candidate move (`CAND`) and principal
  variation (`PV`) tags when analysis contains usable PV data.
- `fen tags --analyze` and `puzzle tags` may add null-move threat tags after
  engine analysis. Threat tags also override `INITIATIVE` to the threatening
  side when applicable.
- Opening tags use `config/book.eco.toml`; exact child matches win, otherwise
  CLI sequence/delta flows can inherit parent `OPENING` tags.
- Chess960 positions skip opening lookup and king-safety tags.
- Puzzle tags attach puzzle-level `META: puzzle_*` fields to every emitted
  puzzle record.

For exact tag lines, field order, identity keys, and delta semantics, see
`SCHEMA.md`.
