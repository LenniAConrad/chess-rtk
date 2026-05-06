package application.gui.workbench;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.struct.Game;
import chess.struct.Pgn;

/**
 * Tracks the workbench game line and exposes it as a move-history table.
 */
final class WorkbenchGameModel extends AbstractTableModel {

    /**
     * Serialization identifier for Swing table model compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Ply label column index.
     */
    private static final int COL_PLY = 0;

    /**
     * SAN column index.
     */
    private static final int COL_SAN = 1;

    /**
     * UCI column index.
     */
    private static final int COL_UCI = 2;

    /**
     * FEN column index.
     */
    private static final int COL_FEN = 3;

    /**
     * Column names.
     */
    private static final String[] COLUMNS = { "Ply", "SAN", "UCI", "FEN" };

    /**
     * Positions from root through current line end.
     */
    private final List<Position> positions = new ArrayList<>();

    /**
     * Move rows for the main line.
     */
    private final List<MoveRow> moves = new ArrayList<>();

    /**
     * Currently selected ply, where zero means the root.
     */
    private int currentPly;

    /**
     * Creates an empty game model.
     */
    WorkbenchGameModel() {
        reset(new Position(Setup.getStandardStartFEN()));
    }

    /**
     * Starts a new game from a root position.
     *
     * @param start root position
     */
    void reset(Position start) {
        positions.clear();
        moves.clear();
        positions.add(start.copy());
        currentPly = 0;
        fireTableDataChanged();
    }

    /**
     * Appends a move at the current ply, truncating any future line first.
     *
     * @param before position before the move
     * @param move encoded move
     * @param after position after the move
     */
    void append(Position before, short move, Position after) {
        int truncatedFrom = currentPly;
        boolean truncated = moves.size() > currentPly;
        truncateAfterCurrentPly();
        if (truncated) {
            fireTableRowsDeleted(truncatedFrom, truncatedFrom + (moves.size() - truncatedFrom));
        }
        int newRow = moves.size();
        moves.add(new MoveRow(plyLabel(before), safeSan(before, move), Move.toString(move), after.toString(), move));
        positions.add(after.copy());
        currentPly = moves.size();
        fireTableRowsInserted(newRow, newRow);
    }

    /**
     * Replaces the game line with a validated move sequence.
     *
     * @param start root position
     * @param line encoded moves
     */
    void loadLine(Position start, List<Short> line) {
        positions.clear();
        moves.clear();
        positions.add(start.copy());
        Position cursor = start.copy();
        for (Short boxed : line) {
            if (boxed == null) {
                continue;
            }
            short move = boxed.shortValue();
            Position next = cursor.copy();
            next.play(move);
            moves.add(new MoveRow(plyLabel(cursor), safeSan(cursor, move), Move.toString(move),
                    next.toString(), move));
            positions.add(next.copy());
            cursor = next;
        }
        currentPly = moves.size();
        fireTableDataChanged();
    }

    /**
     * Moves the current pointer to a ply.
     *
     * @param ply requested ply
     */
    void jumpToPly(int ply) {
        int next = Math.max(0, Math.min(ply, moves.size()));
        if (next == currentPly) {
            return;
        }
        currentPly = next;
    }

    /**
     * Returns whether there is a previous ply.
     *
     * @return true when the line can navigate backward
     */
    boolean canBack() {
        return currentPly > 0;
    }

    /**
     * Returns whether there is a next ply.
     *
     * @return true when the line can navigate forward
     */
    boolean canForward() {
        return currentPly < moves.size();
    }

    /**
     * Returns the current ply.
     *
     * @return current ply
     */
    int currentPly() {
        return currentPly;
    }

    /**
     * Returns the final ply in the current line.
     *
     * @return line length in plies
     */
    int lastPly() {
        return moves.size();
    }

    /**
     * Returns the current position.
     *
     * @return current position copy
     */
    Position currentPosition() {
        return positions.get(currentPly).copy();
    }

    /**
     * Returns the root position.
     *
     * @return root position copy
     */
    Position startPosition() {
        return positions.get(0).copy();
    }

    /**
     * Returns the last move that led to the current ply.
     *
     * @return encoded move, or {@link Move#NO_MOVE} at the root
     */
    short currentLastMove() {
        return currentPly == 0 ? Move.NO_MOVE : moves.get(currentPly - 1).move();
    }

    /**
     * Returns the table row for the current ply.
     *
     * @return row index, or -1 at root
     */
    int currentRow() {
        return currentPly == 0 ? -1 : currentPly - 1;
    }

    /**
     * Returns the ply represented by a row.
     *
     * @param row table row
     * @return ply
     */
    int plyForRow(int row) {
        return row + 1;
    }

    /**
     * Returns the SAN move line.
     *
     * @return SAN line
     */
    String sanLine() {
        StringBuilder sb = new StringBuilder();
        Position cursor = startPosition();
        for (MoveRow row : moves) {
            appendMovePrefix(sb, cursor);
            sb.append(row.san()).append(' ');
            cursor.play(row.move());
        }
        return sb.toString().trim();
    }

    /**
     * Returns the UCI move line.
     *
     * @return UCI line
     */
    String uciLine() {
        List<String> parts = new ArrayList<>();
        for (MoveRow row : moves) {
            parts.add(row.uci());
        }
        return String.join(" ", parts);
    }

    /**
     * Returns all positions in the game line as newline-delimited FENs.
     *
     * @return FEN list
     */
    String fenList() {
        StringBuilder sb = new StringBuilder();
        for (Position position : positions) {
            if (!sb.isEmpty()) {
                sb.append(System.lineSeparator());
            }
            sb.append(position);
        }
        return sb.toString();
    }

    /**
     * Returns the current game line as PGN.
     *
     * @return PGN text
     */
    String pgn() {
        Game game = new Game();
        game.setStartPosition(startPosition());
        game.putTag("Event", "ChessRTK Workbench");
        game.putTag("Site", "?");
        game.putTag("Result", "*");
        game.setResult("*");
        Game.Node head = null;
        Game.Node previous = null;
        for (MoveRow row : moves) {
            Game.Node node = new Game.Node(row.san());
            if (head == null) {
                head = node;
            } else {
                previous.setNext(node);
            }
            previous = node;
        }
        game.setMainline(head);
        return Pgn.toPgn(game);
    }

    /**
     * Returns the row count.
     *
     * @return row count
     */
    @Override
    public int getRowCount() {
        return moves.size();
    }

    /**
     * Returns the column count.
     *
     * @return column count
     */
    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    /**
     * Returns a column name.
     *
     * @param column column index
     * @return column name
     */
    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    /**
     * Returns a table cell value.
     *
     * @param rowIndex row index
     * @param columnIndex column index
     * @return cell value
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        MoveRow row = moves.get(rowIndex);
        return switch (columnIndex) {
            case COL_PLY -> row.ply();
            case COL_SAN -> row.san();
            case COL_UCI -> row.uci();
            case COL_FEN -> row.fen();
            default -> "";
        };
    }

    /**
     * Returns whether a cell is editable.
     *
     * @param row row index
     * @param column column index
     * @return false because history rows are read-only
     */
    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    /**
     * Drops all future moves after the current ply.
     */
    private void truncateAfterCurrentPly() {
        while (moves.size() > currentPly) {
            moves.remove(moves.size() - 1);
        }
        while (positions.size() > currentPly + 1) {
            positions.remove(positions.size() - 1);
        }
    }

    /**
     * Builds a ply label from the position before a move.
     *
     * @param position position before move
     * @return move-number label
     */
    private static String plyLabel(Position position) {
        return position.fullMoveNumber() + (position.isWhiteToMove() ? "." : "...");
    }

    /**
     * Appends a SAN move-number prefix.
     *
     * @param sb target builder
     * @param position position before move
     */
    private static void appendMovePrefix(StringBuilder sb, Position position) {
        boolean first = sb.length() == 0;
        if (!first) {
            sb.append(' ');
        }
        if (first || position.isWhiteToMove()) {
            sb.append(plyLabel(position)).append(' ');
        }
    }

    /**
     * Formats SAN while keeping the history resilient to unexpected move data.
     *
     * @param position position before move
     * @param move encoded move
     * @return SAN or UCI fallback
     */
    private static String safeSan(Position position, short move) {
        try {
            return SAN.toAlgebraic(position, move);
        } catch (IllegalArgumentException ex) {
            return Move.toString(move);
        }
    }

    /**
     * Immutable move-history row.
     *
     * @param ply ply label
     * @param san SAN move
     * @param uci UCI move
     * @param fen resulting FEN
     * @param move encoded move
     */
    private record MoveRow(String ply, String san, String uci, String fen, short move) {
    }
}
