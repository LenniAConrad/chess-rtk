package application.gui.workbench.game;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumnModel;

/**
 * Reusable compact move list shared by the board-centric workbench surfaces: a
 * styled {@code #/White/Black} pair table over the shared {@link GameModel},
 * with click-to-jump navigation, follow-the-tail auto-scroll, and a shared
 * empty state.
 *
 * <p>Extracted from the Play tab so Play — and any later board mode that shows
 * the live line (post-game review, the merged Study surface) — docks this one
 * move list instead of re-assembling the table. It is the move-list half of the
 * shared board chrome the inspector rails compose around.</p>
 */
public final class MoveListPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Builds a move list over the supplied game line.
     *
     * @param gameModel shared analysis/game line backing the table
     * @param jumpToPly navigates the board to a clicked ply
     */
    public MoveListPanel(GameModel gameModel, IntConsumer jumpToPly) {
        super(new BorderLayout());
        setOpaque(false);
        PlayMoveHistoryModel historyModel = new PlayMoveHistoryModel(gameModel);
        JTable moves = new JTable(historyModel) {
            /**
             * Serialization identifier for Swing table compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Paints a shared empty state into the viewport when no moves exist.
             *
             * @param graphics graphics context
             */
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (getRowCount() == 0 && graphics instanceof Graphics2D graphics2D) {
                    Ui.paintEmptyState(graphics2D, new Rectangle(0, 0, getWidth(), getHeight()),
                            "No moves yet", "Start a game to record moves here.");
                }
            }
        };
        Theme.table(moves, Theme.TABLE_ROW_HEIGHT);
        moves.setAutoCreateColumnsFromModel(false);
        moves.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        moves.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        moves.setFillsViewportHeight(true);
        moves.setPreferredScrollableViewportSize(new Dimension(320, 220));
        TableColumnModel columns = moves.getColumnModel();
        columns.getColumn(0).setMaxWidth(42);
        columns.getColumn(0).setPreferredWidth(42);
        columns.getColumn(1).setPreferredWidth(130);
        columns.getColumn(2).setPreferredWidth(130);
        columns.getColumn(1).setCellRenderer(new SanRenderer());
        columns.getColumn(2).setCellRenderer(new SanRenderer());
        moves.addMouseListener(new MouseAdapter() {
            /**
             * Navigates to the clicked move ply when a recorded move cell is selected.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = moves.rowAtPoint(event.getPoint());
                int viewColumn = moves.columnAtPoint(event.getPoint());
                if (row < 0 || viewColumn < 0) {
                    return;
                }
                int column = moves.convertColumnIndexToModel(viewColumn);
                int ply = row * 2 + (column == 2 ? 2 : 1);
                if (ply > 0 && ply <= gameModel.lastPly()) {
                    jumpToPly.accept(ply);
                }
            }
        });
        historyModel.addTableModelListener(event -> SwingUtilities.invokeLater(() -> {
            int lastRow = moves.getRowCount() - 1;
            if (lastRow >= 0) {
                moves.getSelectionModel().setSelectionInterval(lastRow, lastRow);
                moves.scrollRectToVisible(moves.getCellRect(lastRow, 0, true));
            }
        }));
        add(Ui.card("Moves", Ui.scroll(moves)), BorderLayout.CENTER);
    }
}
