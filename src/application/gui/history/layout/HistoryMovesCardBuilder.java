package application.gui.history.layout;

import application.gui.window.GuiWindowHistory;
import application.gui.window.PgnNode;
import application.gui.history.ui.HistoryUiFactory;
import application.gui.model.HistoryEntry;
import application.gui.render.TreeCellRenderer;
import application.gui.ui.RoundedPanel;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JList;

/**
 * Builds the history moves card that renders move list, context menu, and variation controls.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class HistoryMovesCardBuilder {

    /**
     * HistoryMovesCardBuilder method.
     */
    private HistoryMovesCardBuilder() {
        // utility class
    }

    /**
     * build method.
     *
     * @param ctx parameter.
     * @param actions parameter.
     * @return return value.
     */
    public static Result build(Context ctx, Actions actions) {
        RoundedPanel card = ctx.uiFactory().createFlatCard("Moves", false);
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JLabel historyLabel = ctx.uiFactory().mutedLabel("History");
        body.add(historyLabel);
        body.add(Box.createVerticalStrut(6));

        DefaultListModel<HistoryEntry> historyListModel = new DefaultListModel<>();
        JList<HistoryEntry> historyList = new JList<>(historyListModel);
        historyList.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        historyList.setCellRenderer(new TreeCellRenderer(ctx.owner()));
        historyList.setFixedCellHeight(Math.round(22 * ctx.uiScale()));
        historyList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            actions.updateVariationButtons();
        });
        historyList.addMouseMotionListener(new MouseAdapter() {
                        /**
             * Handles mouse moved.
             * @param e e value
             */
@Override
            public void mouseMoved(MouseEvent e) {
                int idx = historyList.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    Rectangle bounds = historyList.getCellBounds(idx, idx);
                    if (bounds != null && bounds.contains(e.getPoint())) {
                        HistoryEntry entry = historyListModel.getElementAt(idx);
                        actions.previewHistoryHover(entry, idx, e.getLocationOnScreen());
                        if (entry != null && entry.node() != null) {
                            PgnNode node = entry.node();
                            String comment = node.getComment();
                            if (comment != null && !comment.isBlank()) {
                                historyList.setToolTipText(comment);
                                return;
                            }
                        }
                    }
                }
                historyList.setToolTipText(null);
                actions.clearBoardPreview();
                actions.hideEnginePreview();
            }
        });
        ctx.registerList(historyList);
        JPopupMenu historyMenu = new JPopupMenu();
        JMenuItem historyCopySanItem = new JMenuItem("Copy SAN");
        historyCopySanItem.addActionListener(e -> actions.copyHistorySan());
        JMenuItem historyCopyUciItem = new JMenuItem("Copy UCI");
        historyCopyUciItem.addActionListener(e -> actions.copyHistoryUci());
        JMenuItem historyJumpItem = new JMenuItem("Jump to Move");
        historyJumpItem.addActionListener(e -> actions.jumpToSelectedHistory());
        JMenuItem historyCommentItem = new JMenuItem("Edit Comment");
        historyCommentItem.addActionListener(e -> actions.editSelectedComment());
        JMenuItem historyClearCommentItem = new JMenuItem("Clear Comment");
        historyClearCommentItem.addActionListener(e -> actions.clearSelectedComment());
        JMenuItem historyClearNagItem = new JMenuItem("Clear Annotation");
        historyClearNagItem.addActionListener(e -> actions.clearSelectedNag());
        JMenu annotateMenu = new JMenu("Annotate");
        annotateMenu.add(actions.createNagItem("!", 1));
        annotateMenu.add(actions.createNagItem("?", 2));
        annotateMenu.add(actions.createNagItem("!!", 3));
        annotateMenu.add(actions.createNagItem("??", 4));
        annotateMenu.add(actions.createNagItem("!?", 5));
        annotateMenu.add(actions.createNagItem("?!", 6));
        annotateMenu.addSeparator();
        annotateMenu.add(historyClearNagItem);
        historyMenu.add(historyCopySanItem);
        historyMenu.add(historyCopyUciItem);
        historyMenu.addSeparator();
        historyMenu.add(historyJumpItem);
        historyMenu.addSeparator();
        historyMenu.add(historyCommentItem);
        historyMenu.add(historyClearCommentItem);
        historyMenu.add(annotateMenu);
        historyList.addMouseListener(new MouseAdapter() {
                        /**
             * Handles mouse clicked.
             * @param e e value
             */
@Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = historyList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        HistoryEntry entry = historyListModel.getElementAt(idx);
                        if (entry != null && entry.node() != null) {
                            actions.applyPgnNode(entry.node());
                        }
                    }
                }
            }

                        /**
             * Handles mouse pressed.
             * @param e e value
             */
@Override
            public void mousePressed(MouseEvent e) {
                actions.maybeShowHistoryMenu(historyList, e);
            }

                        /**
             * Handles mouse released.
             * @param e e value
             */
@Override
            public void mouseReleased(MouseEvent e) {
                actions.maybeShowHistoryMenu(historyList, e);
            }

                        /**
             * Handles mouse exited.
             * @param e e value
             */
@Override
            public void mouseExited(MouseEvent e) {
                actions.clearBoardPreview();
                actions.hideEnginePreview();
            }
        });
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setBorder(BorderFactory.createEmptyBorder());
        Dimension historyScrollPref = new Dimension(260, 240);
        historyScroll.setPreferredSize(ctx.scaleDimension(historyScrollPref));
        ctx.registerScrollPane(historyScroll);
        body.add(historyScroll);
        body.add(Box.createVerticalStrut(8));
        JPanel variationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        variationRow.setOpaque(false);
        JButton historyDeleteButton = ctx.uiFactory().themedButton("Delete", e -> actions.deleteSelectedVariation());
        JButton historyPromoteButton = ctx.uiFactory().themedButton("Promote", e -> actions.promoteSelectedVariation());
        JButton historyVarUpButton = ctx.uiFactory().themedButton("Var Up", e -> actions.moveSelectedVariation(-1));
        JButton historyVarDownButton = ctx.uiFactory().themedButton("Var Down", e -> actions.moveSelectedVariation(1));
        variationRow.add(historyDeleteButton);
        variationRow.add(historyPromoteButton);
        variationRow.add(historyVarUpButton);
        variationRow.add(historyVarDownButton);
        body.add(variationRow);
        card.setContent(body);
        return new Result(card, historyListModel, historyList, historyScroll, historyScrollPref, historyMenu,
                historyCopySanItem, historyCopyUciItem, historyJumpItem, historyCommentItem,
                historyClearCommentItem, historyClearNagItem, historyDeleteButton, historyPromoteButton,
                historyVarUpButton, historyVarDownButton);
    }

    /**
     * Context interface.
     *
     * Provides interface behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    public interface Context {

        /**
         * uiFactory method.
         *
         * @return return value.
         */
        HistoryUiFactory uiFactory();

        /**
         * uiScale method.
         *
         * @return return value.
         */
        float uiScale();

        /**
         * scaleDimension method.
         *
         * @param base parameter.
         * @return return value.
         */
        Dimension scaleDimension(Dimension base);

        /**
         * owner method.
         *
         * @return return value.
         */
        GuiWindowHistory owner();

        /**
         * registerList method.
         *
         * @param list parameter.
         */
        void registerList(JList<?> list);

        /**
         * registerScrollPane method.
         *
         * @param scroll parameter.
         */
        void registerScrollPane(JScrollPane scroll);
    }

    /**
     * Actions interface.
     *
     * Provides interface behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    public interface Actions {

        /**
         * copyHistorySan method.
         */
        void copyHistorySan();

        /**
         * copyHistoryUci method.
         */
        void copyHistoryUci();

        /**
         * jumpToSelectedHistory method.
         */
        void jumpToSelectedHistory();

        /**
         * editSelectedComment method.
         */
        void editSelectedComment();

        /**
         * clearSelectedComment method.
         */
        void clearSelectedComment();

        /**
         * clearSelectedNag method.
         */
        void clearSelectedNag();

        /**
         * updateVariationButtons method.
         */
        void updateVariationButtons();

        /**
         * previewHistoryHover method.
         *
         * @param entry parameter.
         * @param index parameter.
         * @param screenLocation parameter.
         */
        void previewHistoryHover(HistoryEntry entry, int index, Point screenLocation);

        /**
         * clearBoardPreview method.
         */
        void clearBoardPreview();

        /**
         * hideEnginePreview method.
         */
        void hideEnginePreview();

        /**
         * applyPgnNode method.
         *
         * @param node parameter.
         */
        void applyPgnNode(PgnNode node);

        /**
         * maybeShowHistoryMenu method.
         *
         * @param list parameter.
         * @param event parameter.
         */
        void maybeShowHistoryMenu(JList<HistoryEntry> list, MouseEvent event);

        /**
         * deleteSelectedVariation method.
         */
        void deleteSelectedVariation();

        /**
         * promoteSelectedVariation method.
         */
        void promoteSelectedVariation();

        /**
         * moveSelectedVariation method.
         *
         * @param direction parameter.
         */
        void moveSelectedVariation(int direction);

        /**
         * createNagItem method.
         *
         * @param label parameter.
         * @param nag parameter.
         * @return return value.
         */
        JMenuItem createNagItem(String label, int nag);
    }

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
        RoundedPanel panel,
        /**
         * Stores the history list model.
         */
        DefaultListModel<HistoryEntry> historyListModel,
        /**
         * Stores the history list.
         */
        JList<HistoryEntry> historyList,
        /**
         * Stores the history scroll.
         */
        JScrollPane historyScroll,
        /**
         * Stores the history scroll pref.
         */
        Dimension historyScrollPref,
        /**
         * Stores the history menu.
         */
        JPopupMenu historyMenu,
        /**
         * Stores the history copy san item.
         */
        JMenuItem historyCopySanItem,
        /**
         * Stores the history copy uci item.
         */
        JMenuItem historyCopyUciItem,
        /**
         * Stores the history jump item.
         */
        JMenuItem historyJumpItem,
        /**
         * Stores the history comment item.
         */
        JMenuItem historyCommentItem,
        /**
         * Stores the history clear comment item.
         */
        JMenuItem historyClearCommentItem,
        /**
         * Stores the history clear nag item.
         */
        JMenuItem historyClearNagItem,
        /**
         * Stores the history delete button.
         */
        JButton historyDeleteButton,
        /**
         * Stores the history promote button.
         */
        JButton historyPromoteButton,
        /**
         * Stores the history var up button.
         */
        JButton historyVarUpButton,
        /**
         * Stores the history var down button.
         */
        JButton historyVarDownButton
    ) {}
}
