# ChessRTK Tag Pipeline (developer notes)

This folder is the source-adjacent developer reference for ChessRTK's deterministic **tag pipeline** — the code under `src/chess/tag` that turns any chess position into short, parseable facts such as `MATERIAL: balance=white_up_exchange` or `TACTIC: motif=fork side=white move=e4c5`. It documents the exact tag families, field formats, sort order, and per-move delta semantics so that anyone editing the Java taggers (or porting them) has a single, authoritative spec next to the code. Tags are produced by the `fen tags` and `puzzle tags` CLI commands, summarized by `record tag-stats`, and feed both the classical `position describe` path and the optional in-house T5 summaries (`fen text` / `puzzle text`). Everything here is derived from the one shared Java 17 chess core, so the same FEN always yields the same tags in the same order — that determinism is the whole point.

> **Scope.** These notes target contributors working inside `src/chess/tag`. If you are a user looking for the polished, public explanation of tags, read the wiki pages instead: [Tag Reference](../wiki/tag-reference.md) for the full grammar and family list, and [Position and Piece Tags](../wiki/piece-tags.md) for the conceptual overview. This README intentionally mirrors those pages but stays close to the source.

## Files in this folder

| File | Purpose |
| --- | --- |
| `README.md` | This developer overview and source map. |
| `SCHEMA.md` | Canonical tag-and-delta reference: every family, field, identity key, and the delta JSON shape, as implemented in the current tree. |
| `PROMPTS.md` | Guidance for the in-house tag-to-text path: the active T5 prompt shape, grounding rules, and prose templates. |

`SCHEMA.md` is the contract; treat it as the spec a change must keep true. `PROMPTS.md` matches the code path in `chess.nn.t5.TagPrompt` and the tag schema in `SCHEMA.md`.

## What a tag is

A tag is one line in a single canonical format:

```text
FAMILY: key=value key=value
```

- The **family** is uppercase (`FACT`, `META`, `TACTIC`, ...) and names the category of fact.
- **Fields** are space-separated `key=value` pairs. A bare `FAMILY:` with no fields is allowed.
- Field keys and enum values are lowercase, except ECO codes. Squares are lowercase algebraic, e.g. `e4`. Target labels use `piece@square`, e.g. `queen@h5`.
- Values that contain spaces or punctuation are double-quoted, e.g. `name="King's Pawn Game"`.

Every tag is derivable from the position. Engine-derived tags (candidate moves, principal variations, null-move threats) come from crtk's own bounded searchers and the static evaluator under a fixed node/time budget, or from an external UCI engine only when a CLI flag like `--analyze` is set — never by inventing moves. Any concrete move a downstream text generator may mention appears explicitly in `MOVE: only=`, `CAND: ... move=`, `PV:`, `THREAT: ... move=`, or `TACTIC: ... move=`.

## Canonical families

The current family sort order, as enforced by `Sort.sort`:

```text
FACT META MOVE THREAT CAND PV IDEA TACTIC CHECKMATE PIECE KING PAWN
MATERIAL SPACE INITIATIVE DEVELOPMENT MOBILITY OUTPOST ENDGAME OPENING
```

`Sort.sort` drops blank entries, sorts by this family priority, sorts lexicographically within each family, then deduplicates. Unknown families sort after known ones. `MOVE` (exact legal-move counts) and `CHECKMATE` (actual mate attributes) are active families emitted by `Generator`. `IDEA` is present in the sort and identity order and has a producer in `Ideas.java`; verify against the current source before documenting specific `IDEA` fields, since its output set is still evolving.

For the exact fields of each family, the terminal-position special cases (e.g. a checkmate emits `META: mated_in=0` and suppresses `META: eval_cp`), and the identity keys used for diffing, see `SCHEMA.md`.

## Source map

The main entry point is `chess.tag.Generator`. `Generator.tags(position)` builds the static tag set; `Generator.tags(position, analysis)` additionally emits `CAND`/`PV`/threat tags when analysis carries usable PV data.

| Concern | Current source |
| --- | --- |
| Canonical tag assembly | `src/chess/tag/Generator.java`, `GeneratorSupport.java` |
| Sorting and dedupe | `src/chess/tag/Sort.java` |
| Tag parsing | `src/chess/tag/Line.java` |
| Delta identity keys | `src/chess/tag/Identity.java` |
| Delta JSON | `src/chess/tag/Delta.java` |
| Emission helpers | `src/chess/tag/Emitter.java`, `Detector.java`, `Context.java` |
| Shared literals | `src/chess/tag/core/` |
| Evaluation summary helper | `src/chess/tag/eval/Summary.java` |
| Exact move facts and checkmate attributes | `src/chess/tag/MoveFacts.java`, `Checkmate.java` |
| Idea tags | `src/chess/tag/Ideas.java` |
| Engine null-move threats | `src/chess/tag/Threats.java` |
| Material | `src/chess/tag/material/` |
| Piece activity | `src/chess/tag/piece/` |
| Pawn features | `src/chess/tag/pawn/`, `PawnStats.java` |
| Position features | `src/chess/tag/position/` |
| Tactics | `src/chess/tag/tactical/` |
| Move/line helper tags | `src/chess/tag/move/` |
| Puzzle difficulty metadata | `src/chess/puzzle/difficulty/Difficulty.java` |

When the source and `SCHEMA.md` disagree, the source wins and `SCHEMA.md` is the bug.

## CLI entry points

All commands below are real crtk commands; run `crtk help fen tags` or `crtk help puzzle tags` for the authoritative flag list.

Generate tags for one FEN:

```bash
crtk fen tags --fen "<FEN>"
```

Generate tags from a FEN list or parent/child pairs:

```bash
crtk fen tags --input positions.txt
crtk fen tags --input pairs.txt --sequence --delta
```

Generate tags from PGN:

```bash
crtk fen tags --pgn game.pgn --mainline
crtk fen tags --pgn game.pgn --sidelines
```

Enrich tags with UCI engine analysis (adds `CAND`/`PV` and may add null-move `THREAT` tags):

```bash
crtk fen tags --fen "<FEN>" --analyze --multipv 3 --max-nodes 100000
```

Generate per-move puzzle tags and deltas across the principal variations:

```bash
crtk puzzle tags --fen "<FEN>" --multipv 3 --pv-plies 12
```

Use tags as input to the in-house T5 summarizer:

```bash
crtk fen text --fen "<FEN>" --model path/to/model.bin
crtk puzzle text --fen "<FEN>" --model path/to/model.bin
```

Summarize tags or analysis changes across a record file:

```bash
crtk record tag-stats --input records.json
crtk record analysis-delta --input records.json
```

## Output shapes

Without `--delta`, `fen tags` prints one JSON string array per position:

```json
["FACT: status=normal","META: to_move=white","MATERIAL: balance=equal"]
```

With `--delta`, `fen tags` and `puzzle tags` print JSONL records, one per move, carrying the position, the move that produced it, the full tag set, and the added/removed/changed tag delta:

```json
{"index":1,"game_index":null,"parent":"<parent-fen>","fen":"<fen>","move_san":"Nf3","move_uci":"g1f3","tags":["..."],"delta":{"added":["..."],"removed":["..."],"changed":[{"key":"META:eval_cp","from":"META: eval_cp=10","to":"META: eval_cp=45"}]}}
```

`--include-fen` adds `META: fen="<FEN>"` to non-delta tag arrays. Delta rows already carry top-level `parent` and `fen`, so `--include-fen` does not duplicate the FEN inside `tags`.

## Behavior notes worth knowing before editing

- Tags are sorted by family priority, then lexicographically within a family, then deduplicated. Do not assume insertion order survives.
- `Generator` always emits `MOVE: legal=<int>`; other `MOVE` count tags appear only when non-zero, except `MOVE: evasions`, which appears whenever the side to move is in check.
- `CAND` and `PV` are only emitted when analysis carries usable PV data. `role=alt` requires `multipv >= 2` and a second PV.
- The null-move `THREAT` path runs only for engine-backed CLI flows and is skipped when the base position is already in check. A successful threat replaces existing `INITIATIVE` tags with the threatening side.
- Opening tags use `config/book.eco.toml`; exact child matches win, otherwise sequence/delta flows may inherit a parent `OPENING` tag.
- Chess960 positions skip opening lookup and king-safety (`KING`) tags.
- `puzzle tags` attaches puzzle-level `META: puzzle_*` fields to every emitted puzzle record.

## Tag-to-text: the in-house path is canonical

`PROMPTS.md` documents the **current, supported** tag-to-text route. Tags (and deltas) are formatted into a grounding prompt by `chess.nn.t5.TagPrompt` and run through crtk's bundled T5 model via `fen text` and `puzzle text`. The hard rule is grounding: the generator may only state facts present in the tags, delta fields, `move_san`/`move_uci`, or `PV` text, and may mention a concrete move only if that move appears in a tag.

> **Deprecated alternative.** An earlier route sent tags to a cloud LLM via `scripts/deprecated/azure_tag_text.py` (Azure OpenAI Responses API), driven by system-prompt drafts named `README-chatgpt*.md` that once lived in this folder. Those prompt drafts were replaced by `PROMPTS.md`, and the Azure script now lives under `scripts/deprecated/` (an untracked, unsupported scratch area). Do not build new tooling on that path; use `fen text` / `puzzle text` instead.

Represent the model honestly: the bundled T5 summarizer is a usable natural-language generator over grounded tags, not a chess engine and not a source of ground truth. The grounding comes entirely from the deterministic tags it is fed.

## Where to go next

- [Tag Reference](../wiki/tag-reference.md) — the polished public grammar, every family and value, and golden fixtures.
- [Position and Piece Tags](../wiki/piece-tags.md) — the conceptual overview and how tags feed the Filter DSL, dataset exporters, and T5 summaries.
- `SCHEMA.md` — exact tag lines, field order, identity keys, and delta semantics for this code path.
- `PROMPTS.md` — the active T5 prompt shape and prose templates.
