# Review To Study

`crtk review game` turns a PGN mainline into two durable artifacts: a per-ply review stream and, when requested, study material for drillable mistakes. The command is meant to answer "what did I get wrong, and what should I practice from this game?" without adding a GUI-only review model or an ungrounded coach layer.

## Command

External UCI review is the default. Use `--offline` only when you want the deterministic in-process alpha-beta path instead of a configured engine.

```bash
crtk review game \
  --pgn games/rapid.pgn \
  --protocol-path config/default.engine.toml \
  --max-nodes 50000 \
  --max-duration 3s \
  --multipv 2 \
  --threads 1 \
  --hash 64 \
  --to-study
```

The Workbench command builder has a `Review game` template that emits the same CLI path. It does not recompute verdicts in Swing; it previews and runs the real `crtk review game` command.

## Outputs

With no output flags, paths are derived from the PGN stem under `dump/`.

| Artifact | Default path | Schema or shape | Purpose |
| --- | --- | --- | --- |
| Review rows | `dump/<stem>.review.jsonl` | `crtk.review.ply.v1` JSONL | One row per reviewed mainline ply |
| Study units | `dump/<stem>.study.jsonl` | `crtk.review.study_unit.v1` JSONL | One row per drillable mistake or blunder |
| Study records | `dump/<stem>.study.record.json` | `crtk.record.v2` JSON array | Existing Record-compatible puzzle handoff |

Override paths with `--output`, `--study-output`, and `--record-output`.

```bash
crtk review game --pgn games/rapid.pgn --to-study \
  --output dump/rapid.review.jsonl \
  --study-output dump/rapid.study.jsonl \
  --record-output dump/rapid.study.record.json
```

## Review Rows

Every review row carries `schemaVersion: "crtk.review.ply.v1"`. The row includes game identity, PGN headers, ply number, pre-move FEN, played move, best move, eval before and after, centipawn or WDL loss, PV, grounded tag deltas, mistake category, recommended action, optional study-unit id, and a `repro` block.

Rows are written as JSONL so long reviews can be streamed and diffed line by line. External UCI rows record `repro.deterministic: false`; offline rows record `true`.

## Study Units

`--to-study` converts rows whose `recommended_action` is `drill_puzzle` into `crtk.review.study_unit.v1` rows. The first slice intentionally emits only mistakes and blunders, not every inaccuracy.

| Field | Meaning |
| --- | --- |
| `id` | Stable id derived from game id and ply |
| `parent_fen` | The pre-move FEN where the reviewed player made the mistake |
| `position_fen` | The FEN after applying the recommended best move |
| `played_uci` / `played_san` | The move from the game |
| `best_uci` / `best_san` | The refutation move to study |
| `refutation_line` | Capped best PV in UCI notation |
| `mistake_category` | `mistake` or `blunder` |
| `difficulty` | Deterministic coarse label: `easy`, `medium`, or `hard` |
| `tags` | Grounded review tags plus `META:` linkage fields |
| `repro` | Engine, protocol, budget, version, and determinism metadata |

The review row's `study_unit_id` is set only when `--to-study` is active and a matching study unit is emitted.

## Workbench Study Import

The Workbench Study Workspace can import the `crtk.review.study_unit.v1` JSONL file directly into a local PGN-backed study. Open Board -> Analyze -> Study Workspace, choose Import Review, and select the `*.study.jsonl` artifact. Each study unit becomes a chapter rooted at `parent_fen`; the recommended best move starts the mainline, the refutation line becomes the continuation, and the played move is kept as a marked variation with mistake/blunder NAGs.

Swing does not recompute review verdicts. The import consumes the existing JSONL fields: `played_san`, `best_san`, `mistake_category`, `difficulty`, and `tags` are written into comments, while the exported PGN remains normal PGN with comments, NAGs, and variations.

## Record Handoff

The Record sidecar uses the existing `crtk.record.v2` shape so record, puzzle, mining, and publishing tools can read it without a new parser. For each study unit:

| Record field | Value |
| --- | --- |
| `parent` | Same as `parent_fen` |
| `position` | Same as `position_fen` |
| `description` | Review summary naming the played move and best move |
| `tags` | Study-unit tags, including `META: study_unit_id`, `META: review_game_id`, `META: review_ply`, `META: played_uci`, and `META: best_uci` |
| `analysis` | Minimal UCI info line whose PV starts with the refutation |

Record provenance is still stringly typed because `Record` does not yet have dedicated game-id and ply fields. Keep linkage in the `META:` tags until the store/schema layer grows first-class provenance fields.

## Validation

Use the schema commands to inspect the contracts:

```bash
crtk schema show crtk.review.ply.v1
crtk schema show crtk.review.study_unit.v1
crtk record validate --input dump/rapid.study.record.json --strict
```

The regression suite pins the JSON shapes with `ReviewRowSchemaRegressionTest` and the end-to-end fake-UCI review fixture in `ReviewCommandRegressionTest`.

## Boundaries

Review-to-study is not an accuracy score, not a prose coach, and not an SRS scheduler. It emits structured, reproducible artifacts. Later Workbench panels and spaced-repetition features should consume those artifacts instead of recomputing verdicts.
