# Glossary

## CRTK

Short command name for ChessRTK. The installed launcher is `crtk`.

## FEN

Forsyth-Edwards Notation. A single-line representation of a chess position.

## SAN

Standard Algebraic Notation. Human-readable move notation such as `Nf3`,
`exd5`, `O-O`, or `Qxe8+`.

## UCI

Universal Chess Interface. The protocol used by engines such as Stockfish and
LC0. ChessRTK can start and control UCI engines through protocol TOML files.

## Perft

Performance test. Counts legal move-tree nodes to a fixed depth. Perft is a
standard way to validate move generation because every legal move branch must
be counted exactly.

## Divide

A per-root-move perft report. Divide output shows which first move contributes
which node count, making move-generation bugs easier to isolate.

## Record

ChessRTK's reusable JSON analysis-record format. Record commands can filter,
merge, summarize, export, and convert records into other data formats.

## Filter DSL

Small expression language used to select records or puzzle candidates based on
fields such as evaluation, PV shape, move type, tags, and metadata.

## NNUE

Efficient neural-network evaluator architecture used by modern alpha-beta chess
engines. ChessRTK can load NNUE weights for the built-in search evaluator.

## LC0

Leela Chess Zero. In ChessRTK docs this can mean either an external LC0 UCI
engine or the Java LC0-style evaluator path, depending on context.

## WDL

Win/draw/loss probabilities or estimates. Some UCI engines can report WDL
alongside centipawn or mate scores.

## MultiPV

Multiple principal variations from an engine search. Useful for analysis,
threat detection, and puzzle mining.

## PV

Principal variation. The engine's preferred line from the current position.

## NAG

Numeric annotation glyph in PGN, such as `$1` for a good move.
