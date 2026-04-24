# Architecture

ChessRTK is organized around one shared Java position model. The goal is that a
FEN accepted by one command means the same thing to every other command:
notation conversion, legal move lists, perft, search, tags, datasets, rendering,
and publishing all depend on the same chess rules implementation.

## High-Level Layers

![ChessRTK pipeline overview](../assets/diagrams/crtk-pipeline-overview.png)

1. `chess.core`
   - mutable position state
   - legal move generation
   - make/undo state
   - attack and pin helpers
   - FEN, SAN, UCI move helpers
   - Chess960 setup and castling behavior
2. `chess.debug`
   - perft and divide tools
   - reference-suite validation
   - diagnostics and logging helpers
3. `chess.engine` and `chess.eval`
   - built-in alpha-beta search
   - classical, NNUE, and LC0-facing evaluator hooks
4. `chess.uci`
   - external engine protocol orchestration
   - analysis parsing
   - MultiPV, WDL, node, time, thread, and hash options
5. `application.cli`
   - stable command contracts
   - grouped command dispatch
   - config resolution and validation
6. Workflow layers
   - puzzle mining
   - record filtering and export
   - dataset generation
   - image and PDF rendering
   - Studio GUI and other GUI entry points

## Why One Core Matters

Chess workflows are coupled. If notation conversion and move generation use
different assumptions, downstream results become hard to trust. ChessRTK avoids
that by letting the same core answer these questions:

- Is the FEN legal?
- Which moves are legal?
- What is the SAN for this move?
- What position results after this line?
- Do perft counts match known reference positions?
- What should the renderer, tagger, dataset exporter, and book generator show?

## Command Flow

Most commands follow this shape:

1. Parse CLI flags with `utility.Argv`.
2. Resolve defaults through `application.Config`.
3. Build a `Position` from FEN, PGN, generated input, or record data.
4. Execute the command against the shared chess core.
5. Emit a deterministic text, JSON, image, tensor, or PDF output.

Commands that need engine analysis either:

- call the in-process Java engine through `chess.engine`, or
- start a configured UCI engine through `chess.uci`.

## Data Flow

Typical research pipeline:

```text
PGN/FEN/random seeds
  -> puzzle mine / engine analyze
  -> record files
  -> stats, tag-stats, analysis-delta
  -> PGN, CSV, JSONL, NPY, LC0 tensors, classifier tensors
  -> diagrams, books, or downstream training jobs
```

## Important Design Constraints

- Keep core commands deterministic by default.
- Make node and time budgets explicit for engine work.
- Keep model files, engine binaries, dumps, and generated PDFs as local
  artifacts.
- Prefer line-based or structured outputs that are easy to script.
- Verify legality-sensitive changes with perft and SAN/FEN regressions.

## Related Pages

- [Development notes](development-notes.md)
- [Command reference](command-reference.md)
- [In-house Java engine](in-house-engine.md)
- [LC0 UCI engine and Java evaluator](lc0.md)
- [Datasets](datasets.md)
