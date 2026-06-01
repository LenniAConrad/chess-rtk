# Outputs and Logs

Nothing crtk prints needs to be scraped. That is the whole design: a default text form a human can read, a machine-readable **JSON** or **JSONL** form one flag away, file artifacts that land in a predictable place under `dump/`, and a documented **exit code** at the end of every run that a script can branch on. Treat this page as the contract behind those promises — what the formats are, how `--format` / `--json` / `--jsonl` pick between them, what each exit code means, where the session cache and logs live, and what `clean` reclaims. Determinism is what makes it worth the trouble: the same command on the same inputs returns the same bytes, so the output is safe to diff, pipe, and commit.

## Output formats at a glance

The vocabulary of output shapes is deliberately small, and a word means the same thing wherever it appears. Learn it once.

| Format | Selected with | Shape | Typical use |
| --- | --- | --- | --- |
| `summary` / text | default on most commands | one or more human-readable lines | reading at the terminal |
| `uci` | `--format uci` | move(s) in UCI long algebraic (`e2e4`, `e7e8q`) | feeding other UCI tooling |
| `san` | `--format san` or `--san` | move(s) in Standard Algebraic Notation (`Nf3`, `O-O`) | PGN-style annotation |
| `both` | `--format both` or `--both` | UCI and SAN together | quick cross-checking |
| `json` | `--json` (or `--format json`) | one JSON array or object | parsing a whole result at once |
| `jsonl` | `--jsonl` (or `--format jsonl`) | one JSON object per line | streaming row-by-row |
| `csv` | file output (`record export csv`) | header row plus data rows | spreadsheets, pandas |
| `pgn` | file output (`record export pgn`, `puzzle pgn`) | one PGN game per record | chess GUIs, databases |
| `plain` | file output (`record export plain`) | compact line-oriented record dump | lightweight piping |

Notation and structure are independent axes. The move-notation formats (`uci` / `san` / `both`) decide how a move is spelled; the structured formats (`json` / `jsonl`) decide whether the result is wrapped as data. The two choices do not interfere with each other.

## Choosing a format

Three mechanisms select output. A command's `crtk help <area> <action>` tells you which it honors, and that help is the authority when this page and your memory disagree.

### `--format`

`--format` takes a named value and is the canonical selector once a command has more than two output modes:

- `move list` / `move uci` / `move san` / `move both`, and `engine bestmove`: `--format uci|san|both` (default `uci`).
- `engine builtin`: `--format uci-info|uci|san|both|summary` (default `uci-info`).
- `engine mate`: `--format summary|uci|san|both|json|jsonl` (default `summary`).
- `engine perft`: `--format detail|table|stockfish`.
- `position describe`: `--format text|json|jsonl|training-jsonl` (default `text`).
- `fen chess960`: `--format fen|layout|both` (default `fen`).
- `fen render`: `--format png|jpg|bmp|svg` (this selects the image encoding, not text).

### `--json` and `--jsonl`

When the only real decision is text versus structured, two boolean flags cover it:

- `--json` emits a single JSON value — one array for list-style commands, one object for single-result commands.
- `--jsonl` emits JSON Lines: one self-contained JSON object per line, with no enclosing array.

Where a command also takes `--format`, these are simply aliases for `--format json` and `--format jsonl` (as on `engine mate` and `position describe`). Batch commands lean the other way: `engine analyze-batch` and `engine bestmove-batch` default to `--jsonl`, because a stream you can process line by line beats one array you must hold in memory; pass `--json` when you want the whole thing collapsed.

```bash
# Same query, three contracts
crtk move list --startpos                 # text: one UCI move per line
crtk move list --startpos --json          # [{"uci":"a2a3","san":"a3"}, ...]
crtk move list --startpos --jsonl         # {"uci":"a2a3","san":"a3"} per line
```

### Script-friendly flags

Most row-producing commands take a few presentation flags that keep a pipeline's input tidy:

- `--fields LIST` — restrict columns (e.g. `--fields uci`, `--fields both`) on `move` commands.
- `--no-header` — accepted for script-friendly consistency.
- `--quiet` — suppress non-row chatter where supported.
- `--output|-o PATH` — write to a file instead of stdout (on commands that produce a file or a redirectable stream).

## Stable machine-readable contracts

The structured formats are for parsing, not for reading over your shoulder. Four guarantees make leaning on them safe:

- **Determinism.** Pure core operations — move generation, FEN/SAN/UCI conversion, perft, tagging — depend only on their inputs, so the same command yields the same JSON every time. Snapshot it once as a golden fixture and every later run is a diff away from caught.
- **One object per JSONL line.** Each line of a `--jsonl` stream parses on its own, so a result larger than memory is still a result you can process. `--json` is the all-at-once counterpart, for when you do want the whole thing in hand.
- **stdout for data, stderr for diagnostics.** Rows and JSON go to stdout; progress notes, warnings, and stack traces go to stderr. Redirect stdout alone (`> out.jsonl`) and the captured data stays clean.
- **Consistent vocabulary.** A `uci` field is always UCI, a `san` field is always SAN, and `eval`, `nodes`, and their kin carry the same meaning across every engine command.

> Engine-backed commands (`engine analyze`, `engine bestmove`, `engine eval`, `puzzle mine`) lean on an external UCI engine, a neural-network evaluator, or both. The *shape* of their output is stable; the *numbers* in it are not, because they answer to the configured engine and the search budget you gave it. crtk's networks are usable evaluators, but neither the LC0 CNN nor the experimental, simplified BT4 path is a bit-exact reproduction of upstream LC0 — do not expect identical scores. For output you can reproduce, pin the engine, the weights, and `--max-nodes` / `--max-duration`. See [Engines and Evaluators](in-house-engine.md) and [LC0 Networks](lc0.md).

## File outputs and the `dump/` directory

Commands that write an artifact instead of streaming to stdout drop it in the directory `crtk config show` reports as `Output:` — `dump/` unless you have said otherwise. Change the root in `config/cli.config.toml` (see [Configuration](configuration.md)), or override a single run with `--output|-o`. Leave `--output` off and the name comes from the input file's stem, so the artifact lands next to its source by an obvious naming rule.

### Derived names from the input stem

| Command | Default output |
| --- | --- |
| `record export plain` | `dump/<stem>.plain` |
| `record export csv` | `dump/<stem>.csv` |
| `record export pgn` | `dump/<stem>.pgn` |
| `record export puzzle-jsonl` | `dump/<stem>.puzzle.jsonl` |
| `record export puzzle-elo-jsonl` | `dump/<stem>.puzzle-elo.jsonl` |
| `record export training-jsonl` | `dump/<stem>.training.jsonl` |
| `record analysis-delta` | `dump/<stem>.analysis-delta.jsonl` |
| `puzzle pgn` | `dump/<stem>.pgn` |
| `fen pgn` | `dump/<stem>.txt` |

### Multi-file tensor exports

A tensor export is rarely one file. Dataset exporters take a single output *prefix* and fan out several companion files around it (see [Datasets](datasets.md)):

- `record dataset npy` — `<prefix>.features.npy`, `<prefix>.labels.npy` (default prefix `dump/<stem>.dataset`).
- `record dataset lc0` — `<stem>.lc0.inputs.npy`, `<stem>.lc0.policy.npy`, `<stem>.lc0.value.npy`, plus a `.meta.json` (default output prefix `dump/<stem>.lc0`).
- `record dataset classifier` — `<stem>.classifier.inputs.npy`, `<stem>.classifier.labels.npy`, plus a `.meta.json` (default output prefix `dump/<stem>.classifier`).

### Puzzle mining outputs

`puzzle mine` keeps its books in pairs: a verified-puzzle set and the rejected set beside it, so you can audit what the gates threw out (see [Puzzle Mining](mining.md)):

- Directory or default output: `standard-<timestamp>.puzzles.json` and `standard-<timestamp>.nonpuzzles.json` (the prefix becomes `chess960-` with `--chess960`).
- A file-like root ending in `.json` or `.jsonl`: `<stem>.puzzles.json` and `<stem>.nonpuzzles.json`.
- `--output -` streams to stdout instead of writing files.

Each file is one top-level JSON array, grown incrementally as positions clear the [Filter DSL](filter-dsl.md) gates rather than written all at once at the end.

### Publishing outputs

PDFs land under `dump/` like everything else, and no LaTeX toolchain is anywhere in the path — the renderer is native (see [Book Publishing](book-publishing.md)):

- `book render` — `dump/<stem>.pdf`.
- `book pdf` — `dump/<stem>.pdf`, or `dump/chess.pdf` when there is no input path.
- `book cover` — `dump/<stem>-cover.pdf`.
- `book collection` — `dump/<stem>.book.toml` (with optional `--pdf-output` / `--cover-output`).
- `book study` — `dump/<stem>.pdf`.

## Exit codes

Every invocation returns a documented status. Branch on `$?` (or whatever your language calls it) and never parse text to learn whether a command succeeded — the exit code already said so.

| Code | Meaning |
| --- | --- |
| `0` | Success. |
| `1` | Diagnostics surfaced an issue, e.g. `doctor --strict` with warnings present. |
| `2` | Usage error — unknown command, missing required action, or a missing flag value. |
| `3` | Input or runtime error — e.g. an invalid FEN or a failed operation. |

```bash
crtk fen validate --fen "$FEN" >/dev/null
case $? in
  0) echo "valid" ;;
  3) echo "rejected by the chess core" ;;
  *) echo "called wrong" ;;
esac
```

When the code alone does not tell you enough, `--verbose` (`-v`) on most commands prints the full stack trace behind the failure.

## Session cache and logs

Long jobs and engine-backed runs leave a trail. crtk keeps a session cache and per-run logs so you can reconstruct what happened after it has already happened. The log directory sits under the configured output root (typically `dump/session/`), and each run drops a timestamped `.log` there.

To see where the output root actually resolved, along with the other paths:

```bash
crtk config show
```

Before committing hours to a job, confirm Java, configuration, the engine protocol, and local artifacts are sound. In CI, make a warning fatal so the failure surfaces before the job does:

```bash
crtk doctor
crtk doctor --strict   # exit 1 if any warnings are present
```

## Resetting state with `clean`

`crtk clean` empties the session cache without removing the directory that holds it. Reach for it to start a reproducible run from a known-empty state, or to win back disk after a mining session that filled it.

```bash
crtk clean
crtk clean --verbose   # print a stack trace if cleanup fails
```

Its reach stops at session cache and log artifacts. Your committed `dump/` outputs — exported datasets, PGNs, PDFs — are yours to keep, and yours to delete when you are done with them. `clean` will not do it for you.

## See also

- [Getting Started](getting-started.md) — first commands and the noun-verb CLI.
- [Command Reference](command-reference.md) — every area, action, and flag.
- [Configuration](configuration.md) — the output root and other settings in `config/cli.config.toml`.
- [Datasets](datasets.md) — tensor and JSONL export formats in depth.
- [Puzzle Mining](mining.md) — `puzzle mine` outputs and the Filter DSL gates.
- [Book Publishing](book-publishing.md) — native PDF output.
- [Troubleshooting](troubleshooting.md) — diagnosing non-zero exit codes.
