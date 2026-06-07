package application.gui.workbench.game;

import chess.core.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

/**
 * Compact White/Black move-pair view for the Play-vs-engine rail.
 */
public final class PlayMoveHistoryModel extends AbstractTableModel {

    /**
     * Serialization identifier for Swing table model compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Column names.
     */
    private static final String[] COLUMNS = { "#", "White", "Black" };

    /**
     * Shared game model that owns the authoritative line.
     */
    private final GameModel source;

    /**
     * Creates a compact history model backed by the shared game model.
     *
     * @param source source game model
     */
    public PlayMoveHistoryModel(GameModel source) {
        this.source = Objects.requireNonNull(source, "source");
        this.source.addTableModelListener(event -> fireTableDataChanged());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getColumnName(int column) {
        return column >= 0 && column < COLUMNS.length ? COLUMNS[column] : "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRowCount() {
        return (source.currentPath().size() + 1) / 2;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
            return Integer.valueOf(rowIndex + 1);
        }
        List<String> san = sanMoves();
        int ply = rowIndex * 2 + (columnIndex == 2 ? 1 : 0);
        return ply >= 0 && ply < san.size() ? san.get(ply) : "";
    }

    /**
     * Builds SAN for the currently selected game path.
     *
     * @return SAN moves in ply order
     */
    private List<String> sanMoves() {
        List<Short> path = source.currentPath();
        List<String> san = new ArrayList<>(path.size());
        Position cursor = source.startPosition();
        for (Short boxed : path) {
            if (boxed == null) {
                continue;
            }
            short move = boxed.shortValue();
            san.add(PositionText.safeSan(cursor, move));
            cursor.play(move);
        }
        return san;
    }
}
