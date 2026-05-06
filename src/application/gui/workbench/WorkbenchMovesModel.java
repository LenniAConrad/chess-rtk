package application.gui.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.swing.table.DefaultTableModel;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;

/**
 * Table model for legal moves in the current position.
 */
final class WorkbenchMovesModel extends DefaultTableModel {

    /**
     * Serialization identifier for Swing table-model compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the model.
     */
    WorkbenchMovesModel() {
        super(new Object[] { "#", "SAN", "UCI", "Flags" }, 0);
    }

    /**
     * Replaces rows with the legal moves for a position.
     *
     * @param position position
     * @return visible moves
     */
    short[] setPosition(Position position) {
        if (position == null) {
            setDataVector(new Vector<>(), columnIdentifiers());
            return new short[0];
        }
        MoveList moves = position.legalMoves();
        short[] visibleMoves = moves.toArray();
        Vector<Vector<Object>> rows = new Vector<>(moves.size());
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            Vector<Object> row = new Vector<>(4);
            row.add(i + 1);
            row.add(safeSan(position, move));
            row.add(Move.toString(move));
            row.add(moveFlags(position, move));
            rows.add(row);
        }
        setDataVector(rows, columnIdentifiers());
        return visibleMoves;
    }

    /**
     * Returns the model's column identifier vector.
     *
     * @return column identifiers
     */
    private static Vector<Object> columnIdentifiers() {
        Vector<Object> ids = new Vector<>(4);
        ids.add("#");
        ids.add("SAN");
        ids.add("UCI");
        ids.add("Flags");
        return ids;
    }

    /**
     * Returns whether a legal-move table cell can be edited.
     *
     * @param row row index
     * @param column column index
     * @return false because legal-move rows are read-only
     */
    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    /**
     * Returns SAN without failing table rendering.
     *
     * @param position position
     * @param move move
     * @return SAN
     */
    private static String safeSan(Position position, short move) {
        try {
            return SAN.toAlgebraic(position, move);
        } catch (IllegalArgumentException ex) {
            return Move.toString(move);
        }
    }

    /**
     * Returns move flags.
     *
     * @param position position
     * @param move move
     * @return flags
     */
    private static String moveFlags(Position position, short move) {
        List<String> flags = new ArrayList<>();
        if (position.isCapture(move)) {
            flags.add("capture");
        }
        if (position.isCastle(move)) {
            flags.add("castle");
        }
        if (position.isPromotion(move)) {
            flags.add("promotion");
        }
        Position next = position.copy();
        next.play(move);
        if (next.inCheck()) {
            flags.add(next.isCheckmate() ? "mate" : "check");
        }
        return String.join(", ", flags);
    }
}
