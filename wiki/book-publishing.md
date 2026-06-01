# Book publishing

Most chess-book pipelines bolt SAN rendering onto a TeX stack and hope the two agree. The `book` area skips that entirely: it writes print-ready books, covers, and diagram sheets straight to native vector PDF, with no LaTeX, no TeX distribution, and nothing to install beyond the runtime. Feed it a JSON or TOML manifest (or a `.record` dump) and it emits a finished PDF through the same chess core that backs every other command, so the SAN, the diagrams, and the figurine notation are the ones you already trust. Five subcommands span the path from raw material to a press-ready product. `book collection` turns a record dump into a dense puzzle book, `book study` renders deeply annotated studies, `book render` typesets a manifest into the interior, `book cover` builds the matching wraparound, and `book pdf` cuts quick diagram sheets. Each one takes a `--check` dry run that validates geometry, FENs, and solution moves before a single byte hits disk.

## Why native PDF matters

- **No toolchain.** `crtk book render -i book.toml -o book.pdf` produces a finished PDF on a clean machine with only Java 17 present. No LaTeX packages, no font installs.
- **Deterministic output.** A manifest renders byte-stable diagrams and SAN every time, which makes a book diffable and reviewable in version control like any other source artifact.
- **One shared chess core.** FEN parsing, legal-move validation, and SAN run the exact code paths behind `move`, `engine`, and `fen`. Solution lines are validated, not taken on faith.
- **Figurine notation.** Caption and table SAN is converted to figurine algebraic, so `Qxe8+` prints with a queen figurine and a multiplication sign where the capture is.

## Command map

| Command | Purpose | Input | Output |
| --- | --- | --- | --- |
| `book collection` | Dense puzzle collection from a record dump | record JSON/JSONL | TOML manifest, optional interior + cover PDF |
| `book study` | Deeply annotated puzzle studies | JSON/TOML manifest | interior PDF, optional TOML manifest + cover |
| `book render` | Typeset a manifest into the book interior | JSON/TOML manifest | interior PDF |
| `book cover` | Wraparound paperback/hardcover/ebook cover | JSON/TOML manifest (+ interior PDF) | cover PDF |
| `book pdf` | Quick diagram sheets | FENs, FEN list, or PGN | diagram PDF |

## Typical render then cover flow

Two commands cover the usual case: render the interior, then cut a cover from the same manifest. Hand the rendered interior to `book cover --pdf` and the cover command reads trim width, trim height, and printed page count directly out of the file instead of guessing them. The page count drives the spine width, so this is the difference between a cover that fits and one that does not.

```bash
mkdir -p dist

# 1. Dry-run the interior: validate geometry, FENs, and solution moves.
crtk book render -i books/puzzles.toml --check

# 2. Render the interior PDF.
crtk book render -i books/puzzles.toml -o dist/puzzles.pdf

# 3. Dry-run the cover and read back the computed trim, spine, and full-cover size.
crtk book cover -i books/puzzles.toml --check \
  --pdf dist/puzzles.pdf --binding paperback --interior white-bw

# 4. Render the matching cover.
crtk book cover -i books/puzzles.toml -o dist/puzzles-cover.pdf \
  --pdf dist/puzzles.pdf --binding paperback --interior white-bw
```

Without `--pdf`, the cover command works down a fallback chain: manifest `paperwidth` / `paperheight` for trim size, manifest `pages` for the count, and failing that an estimate from the puzzle grid and solution-table cadence. The further down that chain it goes, the more the spine is a guess. Pass `--pages` to pin the count yourself when you need the spine width to be exact.

## The `--check` dry run

Every `book` subcommand accepts `--check` (alias `--validate`), which runs the full pipeline and writes nothing:

- `book render --check` validates the manifest, page dimensions, every FEN, and every solution line.
- `book cover --check` validates the same manifest and prints the calculated trim, spine, and full-cover dimensions so you can compare them against your printer's calculator before uploading.
- `book collection --check` and `book study --check` validate the generated model and layout without writing output.

Wire `--check` into CI. It catches a malformed FEN or an impossible solution move while the cost is a failed build, not a printed page.

## `book render`: manifest to interior PDF

This is the typesetter the rest of the area leans on. It reads a chess-book manifest in JSON or TOML, lays out the puzzle grid with solution tables interleaved through it, and writes the interior PDF.

| Option | Purpose |
| --- | --- |
| `--input` / `-i` | Input chess-book JSON/TOML file |
| `--output` / `-o` | Output PDF path (default `dump/<input-stem>.pdf`) |
| `--title` | Optional title override |
| `--subtitle` | Optional subtitle override |
| `--limit` | Render the first N puzzles and update the count text |
| `--check` / `--validate` | Validate manifest, dimensions, FENs, and solution lines without writing |
| `--free-watermark` / `--watermark` | Add a noisy free-edition watermark and print restrictions |
| `--watermark-id` | Add a traceable ID to the watermark (implies `--watermark`) |

```bash
crtk book render -i books/puzzles.toml -o dist/puzzles.pdf
```

For reviewer or free-edition copies, watermark the PDF so a leaked file points back to whoever it went to. `--watermark-id` implies `--watermark` and stamps page-specific overlay text plus matching PDF metadata:

```bash
crtk book render -i books/puzzles.toml -o dist/puzzles-review.pdf \
  --watermark-id "reviewer@example.com 2026-05-31"
```

### Render manifest shape

The renderer takes JSON or TOML; TOML tends to age better under hand-editing. The keys below are the ones it actually reads.

```toml
title = "Tactical Positions"
subtitle = "100 exercises"
author = "ChessRTK"
time = "2026"
location = "Hangzhou"
language = "English"
pages = 120

paperwidth = 15.24
paperheight = 22.86
innermargin = 2.0
outermargin = 1.5
topmargin = 2.0
bottommargin = 2.0

tablefrequency = 6
puzzlerows = 2
puzzlecolumns = 2

imprint = ["Generated with ChessRTK"]
dedication = ["For careful solvers."]
introduction = ["Solve each position before checking the answer."]
howToRead = ["White is shown at the bottom unless the position requires otherwise."]
blurb = ["A compact set of tactical exercises with full solutions."]
afterword = ["Thanks for reading."]
link = ["example.com/puzzles"]

[[elements]]
position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
moves = "1. e4"
```

Key manifest fields:

- `paperwidth` / `paperheight` are the trim dimensions, in centimeters.
- `innermargin`, `outermargin`, `topmargin`, `bottommargin` set the margins, also in centimeters.
- `tablefrequency` is how many puzzle pages pass between solution tables.
- `puzzlerows` and `puzzlecolumns` set the diagram grid per puzzle page.
- `pages` is what cover generation falls back to for spine width when no interior PDF is supplied.
- `blurb` reappears on the back cover; `imprint`, `dedication`, `introduction`, `howToRead`, and `afterword` are repeatable front- and back-matter paragraph blocks.
- Each `[[elements]]` entry needs a starting FEN in `position` and a SAN solution line in `moves`.

## `book cover`: matching cover PDF

The cover reads the same manifest and prints to spec. Its geometry is a product of four inputs: trim size, binding type, interior paper, and printed page count. Change any one and the spine moves.

| Option | Purpose |
| --- | --- |
| `--input` / `-i` | Input chess-book JSON/TOML file |
| `--pdf` | Interior PDF used to infer trim size and page count |
| `--output` / `-o` | Output cover PDF path (default `dump/<input-stem>-cover.pdf`) |
| `--title` / `--subtitle` | Optional overrides |
| `--binding` | `paperback`, `hardcover`, or `ebook` (default `paperback`) |
| `--interior` | `white-bw`, `cream-bw`, `white-standard-color`, or `white-premium-color` |
| `--pages` | Printed page count for spine width (default: interior PDF, then book pages, then estimate) |
| `--check` / `--validate` | Validate manifest and cover dimensions without writing |

### Bindings

- `paperback`: wraparound back + spine + front cover with 0.125 inch bleed on every outside edge.
- `hardcover`: case-laminate cover with 1.5 cm wrap and 1.0 cm hinge allowance.
- `ebook`: front-cover-only PDF; spine and back cover are omitted.

### Interior paper and spine width

Spine width is `pages * paper-thickness`, and thickness per page is set by the interior token:

| Interior | Inches per page |
| --- | ---: |
| `white-bw` | 0.002252 |
| `cream-bw` | 0.0025 |
| `white-standard-color` | 0.002252 |
| `white-premium-color` | 0.002347 |

Before any print-on-demand upload, run `book cover --check` and reconcile the reported trim, spine, and full-cover values against your printer's own cover calculator. The two should agree to the millimeter; if they do not, fix it here rather than discovering it in a proof.

## `book collection`: record dump to dense puzzle book

When the material already exists as mined data, this is the shortest route to a finished book. The source is a record JSON or JSONL dump whose rows each carry a starting `position` and an `analysis` block holding a principal variation. The command takes the first PV from each row, rewrites it as move-numbered SAN, writes a TOML manifest, and will render the interior and cover in the same pass if you ask it to.

| Option | Purpose |
| --- | --- |
| `--input` / `-i` | Input record JSON or JSONL with `position` + analysis PV |
| `--output` / `-o` | Output TOML manifest path (default `dump/<input-stem>.book.toml`) |
| `--pdf-output` | Also render the interior PDF to this path |
| `--cover-output` | Also render the matching cover PDF to this path |
| `--title` | Book title (default `Chess Puzzle Collection`) |
| `--subtitle` | Optional subtitle; defaults to `"<count> Chess Puzzles"` |
| `--author` | Author credit |
| `--time` / `--location` / `--language` | Publication metadata |
| `--limit` | Import at most N records from the source file |
| `--pages` | Printed page-count hint for the manifest and cover |
| `--table-frequency` | Puzzle pages between solution tables (default 6) |
| `--puzzle-rows` | Puzzle grid rows per page (default 5) |
| `--puzzle-columns` | Puzzle grid columns per page (default 4) |
| `--imprint` / `--dedication` / `--introduction` / `--how-to-read` / `--blurb` / `--link` / `--afterword` | Repeatable front- and back-matter paragraphs |
| `--binding` | `paperback`, `hardcover`, or `ebook` for `--cover-output` |
| `--interior` | `white-bw`, `cream-bw`, `white-standard-color`, or `white-premium-color` |
| `--free-watermark` / `--watermark` | Add a free-edition watermark to `--pdf-output` |
| `--watermark-id` | Traceable watermark ID (implies `--watermark`) |
| `--check` / `--validate` | Validate the generated book model without writing files |

```bash
crtk book collection -i dump/mate1.json -o books/mate1.book.toml \
  --title "Chess Puzzle Collection" \
  --subtitle "4,000 Mate in 1 Puzzles" \
  --author "Lennart A. Conrad" \
  --time "2026" \
  --location "Shanghai" \
  --pdf-output dist/mate1.pdf \
  --cover-output dist/mate1-cover.pdf \
  --binding paperback \
  --interior white-bw
```

Leave `--subtitle` off and you get `"<count> Chess Puzzles"`. Leave the front-matter paragraphs off and the builder fills in a compact default introduction while keeping its native how-to-read and afterword text. A record with no usable position or PV is dropped, and the dropped count shows up in the summary, so a thin book is never silent about why.

Record dumps come from the mining and dataset pipeline. See [Mining puzzles](mining.md) for how to produce them and [Datasets and exports](datasets.md) for the record export and filter options.

## `book study`: deeply annotated studies

Reach for this when each entry is more than a puzzle: description text, comments, hints at several depths, a diagram for every move. Rather than collapse an entry to `position + moves`, it keeps the full `Composition` model, which is why the page becomes one large diagram wrapped in prose instead of a grid. It reads JSON or TOML, optionally writes back a normalized TOML manifest, renders the composition-style interior, and can build the matching cover in the same pass.

| Option | Purpose |
| --- | --- |
| `--input` / `-i` | Input puzzle-study JSON/TOML manifest |
| `--output` / `-o` | Output interior PDF path |
| `--manifest-output` | Also write a normalized TOML manifest |
| `--cover-output` | Also render the matching native cover PDF |
| `--title` / `--subtitle` / `--author` / `--time` / `--location` | Optional metadata overrides |
| `--blurb` / `--link` | Repeatable back-cover blurb and purchase-link overrides |
| `--pages` | Printed page count for spine width / cover metadata |
| `--page-size` | Page size: `a4`, `a5`, `letter` |
| `--margin` | Page margin in PostScript points |
| `--diagrams-per-row` | Diagrams per row override |
| `--board-pixels` | Raster size per diagram before embedding |
| `--flip` / `--black-down` | Render Black at the bottom |
| `--no-fen` | Hide the FEN text under diagrams |
| `--binding` | `paperback`, `hardcover`, or `ebook` for `--cover-output` |
| `--interior` | `white-bw`, `cream-bw`, `white-standard-color`, or `white-premium-color` |
| `--check` / `--validate` | Validate the manifest and layout without writing files |

```bash
crtk book study -i books/puzzle-studies.json \
  --manifest-output books/puzzle-studies.toml \
  -o dist/puzzle-studies.pdf \
  --cover-output dist/puzzle-studies-cover.pdf \
  --title "Chess Puzzle Studies" \
  --subtitle "400 Annotated Studies" \
  --author "Lennart A. Conrad" \
  --binding paperback \
  --interior white-bw
```

### Study manifest shape

A study manifest is a `[[compositions]]` array, each entry pairing annotated text with a diagram backbone that stays aligned to it.

```toml
title = "Chess Puzzle Studies"
subtitle = "Annotated Sample"
author = "ChessRTK"
time = "2026"
location = "Shanghai"
pageSize = "a5"
margin = 42.0
diagramsPerRow = 1
boardPixels = 720
whiteSideDown = false
showFen = false
blurb = ["Annotated positions with guided hints and commentary."]
link = ["example.com/studies"]

[[compositions]]
title = "Mate in One"
description = "White to move and mate in one."
analysis = "1. Qh8#."
hintLevel1 = "Look at the back rank."
figureMovesAlgebraic = ["Start", "1. Qh8#"]
figureMovesDetail = ["Initial position", "Mate"]
figureFens = [
  "6k1/5ppp/8/8/8/8/5PPP/6KQ w - - 0 1",
  "7Q/5pkp/8/8/8/8/5PPP/6K1 b - - 0 1",
]
figureArrows = ["h1h8", ""]
```

Key study fields:

- `[[compositions]]` is the repeated annotated-entry list.
- `description`, `comment`, `analysis`, and `hintLevel1` through `hintLevel4` map straight onto the rendered text blocks.
- `figureFens` is the diagram backbone; `figureMovesAlgebraic`, `figureMovesDetail`, and `figureArrows` are parallel arrays that must stay index-aligned with it, or the captions drift off their boards.
- `pageSize`, `margin`, `diagramsPerRow`, `boardPixels`, `whiteSideDown`, and `showFen` govern the composition-PDF layout.
- An optional `id` per composition tags the entry for cross-referencing.

## `book pdf`: quick diagram sheets

Not everything needs to be a book. `book pdf` lays out a plain sheet of diagrams from FENs, a FEN list, or a PGN. The three inputs are mutually exclusive: pick exactly one of `--fen`, `--input`, or `--pgn`.

| Option | Purpose |
| --- | --- |
| `--fen` | Input FEN (repeatable; a positional FEN is also allowed) |
| `--input` / `-i` | Input FEN list / FEN-pair text file |
| `--pgn` | Input PGN file (one composition per mainline game) |
| `--output` / `-o` | Output PDF path (default `dump/<input-stem>.pdf` or `dump/chess.pdf`) |
| `--title` | Document title override |
| `--page-size` | Page size: `a4`, `a5`, `letter` (default `a4`) |
| `--diagrams-per-row` | Diagrams per row (default 2) |
| `--board-pixels` | Raster size per diagram before embedding (default 900) |
| `--flip` / `--black-down` | Render Black at the bottom |
| `--no-fen` | Hide the FEN text under diagrams |

```bash
crtk book pdf --fen "6k1/5ppp/8/8/8/8/5PPP/6KQ w - - 0 1" -o dist/position.pdf
crtk book pdf -i seeds.txt -o dist/sheet.pdf --title "Training Sheet"
crtk book pdf --pgn games.pgn -o dist/games.pdf --page-size a5 --diagrams-per-row 1
```

## Regression checks

Touch the publishing code or a manifest and run these headless tests; they exercise the native PDF paths end to end:

```bash
java -Djava.awt.headless=true -cp out testing.BookRegressionTest
java -Djava.awt.headless=true -cp out testing.ChessBookCommandRegressionTest
java -Djava.awt.headless=true -cp out testing.ChessBookCoverCommandRegressionTest
java -Djava.awt.headless=true -cp out testing.PuzzleStudyCommandRegressionTest
java -Djava.awt.headless=true -cp out testing.PuzzleCollectionCommandRegressionTest
```

Keep `-Djava.awt.headless=true` whenever there is no reachable display server, which covers CI and most remote shells. Without it, the diagram rasterizer tries to open one and the test dies before it renders anything.

## See also

- [Mining puzzles](mining.md) — produce the record dumps that feed `book collection`.
- [Datasets and exports](datasets.md) — record export, filtering, and the puzzle data sources.
