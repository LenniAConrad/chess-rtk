package application.gui.layout.tab;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

import application.gui.window.GuiWindowHistory;
import application.gui.render.ReportCellRenderer;
import application.gui.window.PgnNode;
import application.gui.model.ReportEntry;
import application.gui.ui.RoundedPanel;

/**
 * ReportTabPanel class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ReportTabPanel {

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
         * Stores the status label.
         */
        JLabel statusLabel,
        /**
         * Stores the analyze button.
         */
        JButton analyzeButton,
        /**
         * Stores the stop button.
         */
        JButton stopButton,
        /**
         * Stores the apply nag button.
         */
        JButton applyNagButton,
        /**
         * Stores the list model.
         */
        DefaultListModel<ReportEntry> listModel,
        /**
         * Stores the list.
         */
        JList<ReportEntry> list
    ) {}

    /**
     * build method.
     *
     * @param ctx parameter.
     * @param owner parameter.
     * @param analyzeAction parameter.
     * @param stopAction parameter.
     * @param applyNagsAction parameter.
     * @return return value.
     */
    public static Result build(ReportTabContext ctx,
            GuiWindowHistory owner,
            Runnable analyzeAction,
            Runnable stopAction,
            Runnable applyNagsAction) {
        RoundedPanel card = new RoundedPanel(0);
        ctx.registerFlatCard(card);
        card.setLayout(new BorderLayout(12, 12));
        card.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setOpaque(false);
        JLabel statusLabel = ctx.mutedLabel("Not analyzed");
        header.add(statusLabel, BorderLayout.CENTER);
        JPanel buttonsRow = new JPanel(new GridLayout(1, 0, 8, 8));
        buttonsRow.setOpaque(false);
        JButton analyzeButton = ctx.themedButton("Analyze", e -> analyzeAction.run());
        JButton stopButton = ctx.themedButton("Stop", e -> stopAction.run());
        JButton applyNagButton = ctx.themedButton("Apply NAGs", e -> applyNagsAction.run());
        stopButton.setEnabled(false);
        applyNagButton.setEnabled(false);
        ctx.registerButton(analyzeButton);
        ctx.registerButton(stopButton);
        ctx.registerButton(applyNagButton);
        buttonsRow.add(analyzeButton);
        buttonsRow.add(stopButton);
        buttonsRow.add(applyNagButton);
        header.add(buttonsRow, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        DefaultListModel<ReportEntry> reportListModel = new DefaultListModel<>();
        JList<ReportEntry> reportList = new JList<>(reportListModel);
        reportList.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        reportList.setCellRenderer(new ReportCellRenderer(owner));
        reportList.setFixedCellHeight(ctx.scaledRowHeight(22));
        reportList.addMouseMotionListener(new MouseAdapter() {
                        /**
             * Handles mouse moved.
             * @param e e value
             */
@Override
            public void mouseMoved(MouseEvent e) {
                int idx = reportList.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    Rectangle bounds = reportList.getCellBounds(idx, idx);
                    if (bounds != null && bounds.contains(e.getPoint())) {
                        ReportEntry entry = reportListModel.getElementAt(idx);
                        ctx.previewNode(entry != null ? entry.node() : null, e.getLocationOnScreen());
                        return;
                    }
                }
                ctx.clearHoverPreviews();
            }
        });
        reportList.addMouseListener(new MouseAdapter() {
                        /**
             * Handles mouse clicked.
             * @param e e value
             */
@Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = reportList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        ReportEntry entry = reportListModel.getElementAt(idx);
                        PgnNode node = entry != null ? entry.node() : null;
                        if (node != null) {
                            ctx.applyPgnNode(node);
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
        ctx.registerList(reportList);

        JScrollPane scroll = new JScrollPane(reportList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        ctx.registerScrollPane(scroll);
        card.add(scroll, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(card, BorderLayout.CENTER);
        return new Result(panel, statusLabel, analyzeButton, stopButton, applyNagButton, reportListModel, reportList);
    }
}
