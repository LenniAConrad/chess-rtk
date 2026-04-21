package application.gui.layout.tab;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import application.gui.window.PgnNode;
import application.gui.ui.RoundedPanel;

/**
 * VariationTabPanel class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class VariationTabPanel {

    /**
     * Result record.
     *
     * Provides record behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    public record Result(
        /**
         * Stores the panel.
         */
        JPanel panel,
        /**
         * Stores the model.
         */
        DefaultTableModel model,
        /**
         * Stores the table.
         */
        JTable table
    ) {}

    /**
     * build method.
     *
     * @param ctx parameter.
     * @param nodesSupplier parameter.
     * @param baseRowHeight parameter.
     * @return return value.
     */
    public static Result build(VariationTabContext ctx,
            Supplier<List<PgnNode>> nodesSupplier,
            int baseRowHeight) {
        RoundedPanel card = ctx.createFlatCard("Variations", false);
        ctx.registerFlatCard(card);
        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setOpaque(false);

        DefaultTableModel model = new DefaultTableModel(new Object[] { "Start", "Line" }, 0) {
                        /**
             * Returns whether cell editable.
             * @param row row value
             * @param column column value
             * @return computed value
             */
@Override
            /**
             * isCellEditable method.
             *
             * @param row parameter.
             * @param column parameter.
             * @return return value.
             */
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        ctx.registerTable(table);
        table.setFillsViewportHeight(true);
        table.setRowHeight(baseRowHeight);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setAutoCreateRowSorter(true);
        table.addMouseMotionListener(new MouseAdapter() {
                        /**
             * Handles mouse moved.
             * @param e e value
             */
@Override
            public void mouseMoved(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    List<PgnNode> nodes = nodesSupplier.get();
                    if (nodes != null && modelRow >= 0 && modelRow < nodes.size()) {
                        ctx.previewNode(nodes.get(modelRow), e.getLocationOnScreen());
                        return;
                    }
                }
                ctx.clearHoverPreviews();
            }
        });
        table.addMouseListener(new MouseAdapter() {
                        /**
             * Handles mouse clicked.
             * @param e e value
             */
@Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        List<PgnNode> nodes = nodesSupplier.get();
                        if (nodes != null && modelRow >= 0 && modelRow < nodes.size()) {
                            ctx.applyPgnNode(nodes.get(modelRow));
                        }
                    }
                }
            }

                        /**
             * Handles mouse exited.
             * @param e e value
             */
@Override
            public void mouseExited(MouseEvent e) {
                ctx.clearHoverPreviews();
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        ctx.registerScrollPane(scroll);
        body.add(scroll, BorderLayout.CENTER);
        card.setContent(body);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(card, BorderLayout.CENTER);
        return new Result(panel, model, table);
    }
}
