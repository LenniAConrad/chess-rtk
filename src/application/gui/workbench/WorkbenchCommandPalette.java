package application.gui.workbench;

import static application.gui.workbench.WorkbenchUi.onTextChange;
import static application.gui.workbench.WorkbenchUi.scroll;
import static application.gui.workbench.WorkbenchUi.styleFields;
import static application.gui.workbench.WorkbenchUi.transparentPanel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Searchable native Swing action palette for command-heavy workbench flows.
 */
final class WorkbenchCommandPalette extends JDialog {

    /**
     * Serialization identifier for Swing dialog compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Palette width in pixels.
     */
    private static final int WIDTH = 560;

    /**
     * Palette height in pixels.
     */
    private static final int HEIGHT = 520;

    /**
     * Input field used to filter actions.
     */
    private final JTextField searchField = new JTextField();

    /**
     * Visible filtered action model.
     */
    private final DefaultListModel<PaletteAction> visibleActions = new DefaultListModel<>();

    /**
     * Action result list.
     */
    private final JList<PaletteAction> actionList = new JList<>(visibleActions);

    /**
     * Empty-state label shown when no actions match.
     */
    private final JLabel emptyLabel = new JLabel("No matching actions");

    /**
     * Full action list for the current palette invocation.
     */
    private List<PaletteAction> actions = List.of();

    /**
     * Recently-run action titles, most-recent first. Capped to keep the
     * "no query" view readable.
     */
    private final java.util.LinkedHashSet<String> recentTitles = new java.util.LinkedHashSet<>();

    /**
     * Maximum number of recent action titles retained for frecency boosting.
     */
    private static final int MAX_RECENTS = 12;

    /**
     * Creates a command palette owned by the workbench window.
     *
     * @param owner parent frame
     */
    WorkbenchCommandPalette(JFrame owner) {
        super(owner, "Actions", false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setMinimumSize(new Dimension(WIDTH, HEIGHT));
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        getRootPane().setBorder(BorderFactory.createLineBorder(WorkbenchTheme.LINE));
        setContentPane(createContent());
        installKeys();
        pack();
    }

    /**
     * Shows the palette with fresh action data.
     *
     * @param nextActions available actions
     */
    void showActions(List<PaletteAction> nextActions) {
        actions = List.copyOf(nextActions);
        searchField.setText("");
        refill();
        setLocationRelativeTo(getOwner());
        setVisible(true);
        SwingUtilities.invokeLater(() -> {
            searchField.requestFocusInWindow();
            searchField.selectAll();
        });
    }

    /**
     * Builds the palette content.
     *
     * @return content component
     */
    private JComponent createContent() {
        JPanel content = new WorkbenchSurfacePanel(new BorderLayout(8, 8));
        content.setBorder(WorkbenchTheme.pad(14, 14, 14, 14));
        content.add(WorkbenchTheme.section("Actions"), BorderLayout.NORTH);

        JPanel body = transparentPanel(new BorderLayout(8, 8));
        styleFields(searchField);
        searchField.setToolTipText("Search actions");
        searchField.getAccessibleContext().setAccessibleName("Search actions");
        onTextChange(this::refill, searchField);
        body.add(searchField, BorderLayout.NORTH);

        configureActionList();
        JPanel results = transparentPanel(new BorderLayout(0, 6));
        results.add(scroll(actionList), BorderLayout.CENTER);
        configureEmptyLabel();
        results.add(emptyLabel, BorderLayout.SOUTH);
        body.add(results, BorderLayout.CENTER);
        content.add(body, BorderLayout.CENTER);
        return content;
    }

    /**
     * Configures the searchable action result list.
     */
    private void configureActionList() {
        actionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actionList.setVisibleRowCount(10);
        actionList.setCellRenderer(new ActionRenderer());
        actionList.getAccessibleContext().setAccessibleName("Action results");
        WorkbenchTheme.list(actionList);
        actionList.setFixedCellHeight(46);
        actionList.addMouseListener(new MouseAdapter() {
            /**
             * Runs an action on double click.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    runSelection();
                }
            }
        });
    }

    /**
     * Configures the empty-state label.
     */
    private void configureEmptyLabel() {
        emptyLabel.setForeground(WorkbenchTheme.MUTED);
        emptyLabel.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setVisible(false);
    }

    /**
     * Installs keyboard behavior for palette execution and dismissal.
     */
    private void installKeys() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "closePalette");
        getRootPane().getActionMap().put("closePalette", swingAction(() -> setVisible(false)));
        searchField.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "paletteDown");
        searchField.getInputMap().put(KeyStroke.getKeyStroke("UP"), "paletteUp");
        searchField.getInputMap().put(KeyStroke.getKeyStroke("PAGE_DOWN"), "palettePageDown");
        searchField.getInputMap().put(KeyStroke.getKeyStroke("PAGE_UP"), "palettePageUp");
        searchField.getActionMap().put("paletteDown", swingAction(() -> moveSelection(1)));
        searchField.getActionMap().put("paletteUp", swingAction(() -> moveSelection(-1)));
        searchField.getActionMap().put("palettePageDown", swingAction(() -> moveSelection(6)));
        searchField.getActionMap().put("palettePageUp", swingAction(() -> moveSelection(-6)));
        actionList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "paletteRun");
        actionList.getActionMap().put("paletteRun", swingAction(this::runSelection));
        searchField.addActionListener(event -> runSelection());
    }

    /**
     * Creates a Swing action from a runnable.
     *
     * @param runnable callback
     * @return Swing action
     */
    private static AbstractAction swingAction(Runnable runnable) {
        return new AbstractAction() {
            /**
             * Serialization identifier for Swing action compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Runs the callback.
             *
             * @param event action event
             */
            @Override
            public void actionPerformed(ActionEvent event) {
                runnable.run();
            }
        };
    }

    /**
     * Rebuilds visible actions for the current search query.
     */
    private void refill() {
        visibleActions.clear();
        String query = searchField.getText();
        java.util.List<RankedAction> ranked = new java.util.ArrayList<>(actions.size());
        boolean blank = query == null || query.isBlank();
        String lowerQuery = blank ? "" : query.toLowerCase(Locale.ROOT).trim();
        for (PaletteAction action : actions) {
            int score = blank ? 0 : action.matchScore(lowerQuery);
            if (!blank && score == Integer.MIN_VALUE) {
                continue;
            }
            int recentBoost = recencyBoost(action.title());
            ranked.add(new RankedAction(action, blank ? recentBoost : score + recentBoost));
        }
        if (!blank) {
            ranked.sort((a, b) -> Integer.compare(b.score, a.score));
        } else {
            ranked.sort((a, b) -> Integer.compare(b.score, a.score));
        }
        for (RankedAction ra : ranked) {
            visibleActions.addElement(ra.action);
        }
        if (!visibleActions.isEmpty()) {
            actionList.setSelectedIndex(0);
        }
        emptyLabel.setVisible(visibleActions.isEmpty());
        emptyLabel.getParent().revalidate();
        emptyLabel.getParent().repaint();
    }

    /**
     * Returns a small score boost for actions that were run recently, with
     * higher boosts for more-recent invocations.
     *
     * @param title action title
     * @return frecency boost (0 if never used)
     */
    private int recencyBoost(String title) {
        int rank = 0;
        for (String entry : recentTitles) {
            if (entry.equals(title)) {
                return Math.max(1, MAX_RECENTS - rank);
            }
            rank++;
        }
        return 0;
    }

    /**
     * One ranked palette result.
     */
    private record RankedAction(PaletteAction action, int score) { }

    /**
     * Moves the selected palette row.
     *
     * @param delta row delta
     */
    private void moveSelection(int delta) {
        if (visibleActions.isEmpty()) {
            return;
        }
        int current = Math.max(0, actionList.getSelectedIndex());
        int next = Math.max(0, Math.min(visibleActions.size() - 1, current + delta));
        actionList.setSelectedIndex(next);
        actionList.ensureIndexIsVisible(next);
    }

    /**
     * Runs the currently selected action.
     */
    private void runSelection() {
        PaletteAction selected = actionList.getSelectedValue();
        if (selected == null) {
            return;
        }
        recordRecent(selected.title());
        setVisible(false);
        SwingUtilities.invokeLater(() -> {
            try {
                selected.action().run();
            } catch (RuntimeException ex) {
                JFrame owner = (JFrame) getOwner();
                Component parent = owner != null ? owner : null;
                javax.swing.JOptionPane.showMessageDialog(parent,
                        "Action failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                        "Action error", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * Records that an action title was just run so subsequent palette opens
     * float it toward the top.
     *
     * @param title most-recently-run action title
     */
    private void recordRecent(String title) {
        recentTitles.remove(title);
        java.util.LinkedHashSet<String> rebuilt = new java.util.LinkedHashSet<>();
        rebuilt.add(title);
        rebuilt.addAll(recentTitles);
        recentTitles.clear();
        int kept = 0;
        for (String entry : rebuilt) {
            if (kept >= MAX_RECENTS) {
                break;
            }
            recentTitles.add(entry);
            kept++;
        }
    }

    /**
     * One command-palette action.
     *
     * @param title action title
     * @param detail concise action detail
     * @param action action callback
     */
    record PaletteAction(String title, String detail, Runnable action) {

        /**
         * Creates a normalized palette action.
         *
         * @param title action title
         * @param detail concise action detail
         * @param action action callback
         */
        PaletteAction {
            title = title == null ? "" : title;
            detail = detail == null ? "" : detail;
            action = action == null ? () -> {
                // no-op fallback
            } : action;
        }

        /**
         * Returns whether this action matches a search query.
         *
         * @param query query text
         * @return true when title or detail contains every query token
         */
        boolean matches(String query) {
            if (query == null || query.isBlank()) {
                return true;
            }
            return matchScore(query.toLowerCase(Locale.ROOT).trim()) != Integer.MIN_VALUE;
        }

        /**
         * Returns a fuzzy match score, or {@link Integer#MIN_VALUE} when the
         * action does not match the query. Higher scores rank first.
         *
         * @param lowerQuery lowercase query
         * @return ranking score
         */
        int matchScore(String lowerQuery) {
            String haystack = haystack();
            int score = 0;
            for (String token : tokens(lowerQuery)) {
                int idx = haystack.indexOf(token);
                if (idx >= 0) {
                    score += 100 + Math.max(0, 50 - idx);
                    if (title.toLowerCase(Locale.ROOT).contains(token)) {
                        score += 60;
                    }
                    continue;
                }
                int sub = subsequenceScore(haystack, token);
                if (sub == Integer.MIN_VALUE) {
                    return Integer.MIN_VALUE;
                }
                score += sub;
            }
            return score;
        }

        /**
         * Returns a non-negative score when {@code token} appears as a
         * subsequence inside {@code haystack}, or {@link Integer#MIN_VALUE}
         * when the characters cannot be matched in order.
         *
         * @param haystack lowercased haystack
         * @param token lowercased query token
         * @return score or no-match sentinel
         */
        private static int subsequenceScore(String haystack, String token) {
            int hi = 0;
            int matched = 0;
            int firstHit = -1;
            for (int ti = 0; ti < token.length() && hi < haystack.length(); ti++) {
                char target = token.charAt(ti);
                while (hi < haystack.length() && haystack.charAt(hi) != target) {
                    hi++;
                }
                if (hi >= haystack.length()) {
                    return Integer.MIN_VALUE;
                }
                if (firstHit < 0) {
                    firstHit = hi;
                }
                matched++;
                hi++;
            }
            return matched < token.length() ? Integer.MIN_VALUE : 30 + Math.max(0, 30 - firstHit);
        }

        /**
         * Returns the lazily-computed lowercase haystack for matching.
         *
         * @return lowercase title + detail
         */
        private String haystack() {
            return (title + " " + detail).toLowerCase(Locale.ROOT);
        }

        /**
         * Splits a query into lowercase tokens.
         *
         * @param query raw query
         * @return query tokens
         */
        private static List<String> tokens(String query) {
            List<String> tokens = new ArrayList<>();
            for (String raw : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
                if (!raw.isBlank()) {
                    tokens.add(raw);
                }
            }
            return tokens;
        }

        /**
         * Returns the list label fallback.
         *
         * @return action title
         */
        @Override
        public String toString() {
            return title;
        }
    }

    /**
     * Two-line native Swing renderer for palette actions.
     */
    private static final class ActionRenderer extends DefaultListCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Title label.
         */
        private final JLabel title = new JLabel();

        /**
         * Detail label.
         */
        private final JLabel detail = new JLabel();

        /**
         * Row panel.
         */
        private final JPanel panel = new JPanel(new BorderLayout(4, 2));

        /**
         * Creates the renderer.
         */
        ActionRenderer() {
            panel.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
            panel.setOpaque(true);
            title.setFont(WorkbenchTheme.font(13, Font.BOLD));
            title.setHorizontalAlignment(SwingConstants.LEFT);
            detail.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            detail.setHorizontalAlignment(SwingConstants.LEFT);
            panel.add(title, BorderLayout.NORTH);
            panel.add(detail, BorderLayout.SOUTH);
        }

        /**
         * Returns a component configured for one list row.
         *
         * @param list source list
         * @param value row value
         * @param index row index
         * @param selected whether the row is selected
         * @param focused whether the row has focus
         * @return renderer component
         */
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            PaletteAction action = value instanceof PaletteAction paletteAction ? paletteAction
                    : new PaletteAction(String.valueOf(value), "", null);
            panel.setBackground(selected ? WorkbenchTheme.SELECTION_SOLID : WorkbenchTheme.ELEVATED_SOLID);
            title.setForeground(WorkbenchTheme.TEXT);
            title.setText(elideToListWidth(action.title(), list, WorkbenchTheme.font(13, Font.BOLD)));
            detail.setForeground(WorkbenchTheme.MUTED);
            detail.setText(elideToListWidth(action.detail(), list, WorkbenchTheme.font(11, Font.PLAIN)));
            panel.setToolTipText(action.title() + " - " + action.detail());
            return panel;
        }

        /**
         * Shortens text for the current list width.
         *
         * @param text source text
         * @param list source list
         * @param font label font
         * @return fitted text
         */
        private static String elideToListWidth(String text, JList<?> list, Font font) {
            int available = Math.max(80, list.getWidth() - 34);
            return WorkbenchUi.elide(text, list.getFontMetrics(font), available);
        }
    }
}
