# Study Workspace

Study Workspace is ChessRTK's offline desktop study editor. It keeps the app local like ChessBase: no cloud account, no sync service, no web dependency, and no third-party dependency tree.

## Storage Model

- One PGN file is one study book.
- One PGN game is one chapter.
- PGN remains the canonical chess content format for moves, nested variations, comments, NAGs, FEN roots, and results.
- The optional `<study-stem>.crtk-study.json` sidecar stores only CRTK-only state: stable chapter ids, chapter order, mode, orientation, and descriptions.
- Ordinary PGN files open without a sidecar; title and chapter names are inferred from PGN tags.

## Editing

Open Board -> Analyze -> Study Workspace. The panel has a local study toolbar, chapter list, move tree, annotation inspector, and practice controls.

The move tree supports adding legal moves from the active board, adding variations, promoting variations, deleting branches, and exporting back through `Pgn.toPgn`. Comments before and after moves, root comments, NAGs, and graphical annotations are stored on the existing `chess.struct.Game.Node` tree.

The NAG palette follows the Lichess-compatible study glyph set. It enforces one move assessment, one position assessment, and multiple observation glyphs. Arrows and circles are serialized as standard PGN graphical comments:

```pgn
{Critical square [%csl Yd5] [%cal Ge2e4]}
```

## Review Import

Review artifacts from `crtk review game --to-study` can be imported from the Study Workspace with Import Review. Each `crtk.review.study_unit.v1` JSONL row becomes a chapter rooted at `parent_fen`. The best move starts the mainline, the refutation line becomes the continuation, and the played move is kept as a commented variation with mistake or blunder NAGs.

## Publishing

The older Study tab still builds TOML manifests for `book study`; it has not been removed. Use Study Workspace for PGN project editing, then use the publishing flow when you want a rendered PDF study handout.
