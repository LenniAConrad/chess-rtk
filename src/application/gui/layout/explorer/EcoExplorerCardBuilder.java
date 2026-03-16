package application.gui.layout.explorer;

import application.gui.window.GuiWindowHistory;
import application.gui.history.ui.HistoryUiFactory;
import application.gui.render.EcoEntryCellRenderer;
import application.gui.ui.RoundedPanel;
import chess.eco.Entry;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.ListSelectionModel;

/**
 * EcoExplorerCardBuilder class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EcoExplorerCardBuilder {

    /**
     * EcoExplorerCardBuilder method.
     */
    private EcoExplorerCardBuilder() {
        // helper class
    }

    /**
     * build method.
     *
     * @param ctx parameter.
     * @param actions parameter.
     * @return return value.
     */
    public static Result build(Context ctx, Actions actions) {
        RoundedPanel card = ctx.uiFactory().createFlatCard("Explorer", false);
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = ctx.mutedLabel(ctx.formatTitle("Explorer"));
        header.add(title, BorderLayout.WEST);
        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        headerActions.setOpaque(false);
        JButton refresh = ctx.uiFactory().themedButton("⟳", e -> actions.refreshEcoList());
        refresh.setToolTipText("Refresh openings");
        JButton collapse = ctx.uiFactory().themedButton("▾", e -> actions.collapseExplorerSelection());
        collapse.setToolTipText("Collapse");
        JButton more = ctx.uiFactory().themedButton("⋯", e -> actions.moreExplorerOptions());
        more.setToolTipText("More");
        headerActions.add(refresh);
        headerActions.add(collapse);
        headerActions.add(more);
        header.add(headerActions, BorderLayout.EAST);
        body.add(header);
        body.add(Box.createVerticalStrut(6));

        JLabel currentLabel = ctx.mutedLabel("Current: (unknown)");
        body.add(currentLabel);
        body.add(Box.createVerticalStrut(4));

        JTextField searchField = new JTextField();
        ctx.registerTextField(searchField);
        JPanel searchRow = new JPanel(new BorderLayout(8, 8));
        searchRow.setOpaque(false);
        searchRow.add(ctx.mutedLabel("Search"), BorderLayout.WEST);
        int searchHeight = Math.max(20, Math.round(24 * ctx.uiScale()));
        searchField.setPreferredSize(new Dimension(10, searchHeight));
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, searchHeight));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                actions.filterEcoList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                actions.filterEcoList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                actions.filterEcoList();
            }
        });
        searchRow.add(searchField, BorderLayout.CENTER);
        body.add(searchRow);
        body.add(Box.createVerticalStrut(4));

        JLabel statusLabel = ctx.mutedLabel("ECO book: not loaded");
        body.add(statusLabel);
        body.add(Box.createVerticalStrut(6));

        DefaultListModel<Entry> listModel = new DefaultListModel<>();
        JList<Entry> list = new JList<>(listModel);
        list.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        list.setCellRenderer(new EcoEntryCellRenderer(ctx.owner()));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            actions.updateEcoDetails();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = list.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        Entry entry = listModel.getElementAt(idx);
                        actions.applyEcoEntry(entry);
                    }
                }
            }
        });
        ctx.registerList(list);

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        ctx.registerScrollPane(listScroll);
        body.add(listScroll);
        body.add(Box.createVerticalStrut(6));

        JTextArea detailArea = new JTextArea(3, 26);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        ctx.registerTextArea(detailArea);
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createEmptyBorder());
        ctx.registerScrollPane(detailScroll);
        body.add(detailScroll);
        body.add(Box.createVerticalStrut(6));

        JPanel actionsRow = new JPanel(new GridLayout(1, 0, 8, 8));
        actionsRow.setOpaque(false);
        JButton loadLineButton = ctx.uiFactory().themedButton("Load Line", e -> actions.loadCurrentEcoLine());
        JButton copyLineButton = ctx.uiFactory().themedButton("Copy Movetext", e -> actions.copyEcoMovetext());
        actionsRow.add(loadLineButton);
        actionsRow.add(copyLineButton);
        body.add(actionsRow);

        card.setContent(body);
        return new Result(card, currentLabel, statusLabel, searchField, listModel, list, detailArea, listScroll,
                loadLineButton, copyLineButton);
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
         * formatTitle method.
         *
         * @param text parameter.
         * @return return value.
         */
        String formatTitle(String text);
        /**
         * mutedLabel method.
         *
         * @param text parameter.
         * @return return value.
         */
        JLabel mutedLabel(String text);
        /**
         * owner method.
         *
         * @return return value.
         */
        GuiWindowHistory owner();
        /**
         * registerTextField method.
         *
         * @param field parameter.
         */
        void registerTextField(JTextField field);
        /**
         * registerTextArea method.
         *
         * @param area parameter.
         */
        void registerTextArea(JTextArea area);
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
         * refreshEcoList method.
         */
        void refreshEcoList();
        /**
         * collapseExplorerSelection method.
         */
        void collapseExplorerSelection();
        /**
         * moreExplorerOptions method.
         */
        void moreExplorerOptions();
        /**
         * filterEcoList method.
         */
        void filterEcoList();
        /**
         * updateEcoDetails method.
         */
        void updateEcoDetails();
        /**
         * applyEcoEntry method.
         *
         * @param entry parameter.
         */
        void applyEcoEntry(Entry entry);
        /**
         * loadCurrentEcoLine method.
         */
        void loadCurrentEcoLine();
        /**
         * copyEcoMovetext method.
         */
        void copyEcoMovetext();
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
            RoundedPanel panel,
            JLabel currentLabel,
            JLabel statusLabel,
            JTextField searchField,
            DefaultListModel<Entry> listModel,
            JList<Entry> list,
            JTextArea detailArea,
            JScrollPane listScroll,
            JButton loadLineButton,
            JButton copyLineButton) {}
}
