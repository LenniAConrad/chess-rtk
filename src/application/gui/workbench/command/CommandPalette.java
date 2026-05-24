package application.gui.workbench.command;

import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import static application.gui.workbench.ui.Ui.onTextChange;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Searchable native Swing action palette for command-heavy workbench flows.
 *
 * <p>The palette mounts into the owner frame's layered pane instead of opening
 * a separate top-level window, matching the VS Code command palette model.</p>
 */
public final class CommandPalette extends JPanel {

    /**
     * Serialization identifier for Swing dialog compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred palette width in pixels.
     */
    private static final int WIDTH = 560;

    /**
     * Preferred palette height in pixels.
     */
    private static final int HEIGHT = 520;

    /**
     * Minimum overlay width that keeps the search field usable.
     */
    private static final int MIN_WIDTH = 360;

    /**
     * Minimum overlay height that keeps several actions visible.
     */
    private static final int MIN_HEIGHT = 240;

    /**
     * Horizontal margin inside the layered pane.
     */
    private static final int SIDE_MARGIN = 16;

    /**
     * Distance from the top of the workbench content area.
     */
    private static final int TOP_MARGIN = 12;

    /**
     * Owning workbench frame.
     */
    private final JFrame owner;

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
     * Repositions the floating overlay when the owning frame changes size.
     */
    private final ComponentAdapter ownerResizeListener = new ComponentAdapter() {
        /**
         * Handles owner resize.
         *
         * @param event resize event
         */
        @Override
        public void componentResized(ComponentEvent event) {
            positionOverlay();
        }

        /**
         * Handles owner show.
         *
         * @param event show event
         */
        @Override
        public void componentShown(ComponentEvent event) {
            positionOverlay();
        }
    };

    /**
     * True once the owner resize listener has been installed.
     */
    private boolean resizeListenerInstalled;

    /**
     * Creates a command palette owned by the workbench window.
     *
     * @param owner parent frame
     */
    public CommandPalette(JFrame owner) {
        super(new BorderLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
        setName("commandPaletteOverlay");
        setOpaque(false);
        setVisible(false);
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBorder(BorderFactory.createLineBorder(Theme.LINE));
        add(createContent(), BorderLayout.CENTER);
        installKeys();
    }

    /**
     * Shows the palette with fresh action data.
     *
     * @param nextActions available actions
     */
    public void showActions(List<PaletteAction> nextActions) {
        actions = List.copyOf(nextActions);
        searchField.setText("");
        refill();
        mountOverlay();
        positionOverlay();
        refreshTheme();
        setVisible(true);
        owner.getLayeredPane().moveToFront(this);
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
        JPanel content = new SurfacePanel(new BorderLayout(8, 8));
        content.setBorder(Theme.pad(14, 14, 14, 14));
        content.add(Theme.section("Actions"), BorderLayout.NORTH);

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
     * Reapplies current theme colors after a mode switch.
     */
    public void refreshTheme() {
        setBorder(BorderFactory.createLineBorder(Theme.LINE));
        Theme.refreshComponentTree(this);
        applyActionListChrome();
        emptyLabel.setForeground(Theme.MUTED);
        repaint();
    }

    /**
     * Adds this overlay to the owning frame's layered pane.
     */
    private void mountOverlay() {
        JLayeredPane layeredPane = owner.getLayeredPane();
        if (getParent() != layeredPane) {
            if (getParent() != null) {
                getParent().remove(this);
            }
            layeredPane.add(this, JLayeredPane.POPUP_LAYER);
        }
        if (!resizeListenerInstalled) {
            owner.addComponentListener(ownerResizeListener);
            resizeListenerInstalled = true;
        }
    }

    /**
     * Positions the overlay at the top center of the workbench content.
     */
    private void positionOverlay() {
        JLayeredPane layeredPane = owner.getLayeredPane();
        int availableWidth = layeredPane.getWidth();
        int availableHeight = layeredPane.getHeight();
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }
        int width = Math.min(WIDTH, Math.max(MIN_WIDTH, availableWidth - SIDE_MARGIN * 2));
        int height = Math.min(HEIGHT, Math.max(MIN_HEIGHT, availableHeight - TOP_MARGIN - SIDE_MARGIN));
        int x = Math.max(SIDE_MARGIN, (availableWidth - width) / 2);
        setBounds(x, TOP_MARGIN, width, height);
        revalidate();
        repaint();
    }

    /**
     * Configures the searchable action result list.
     */
    private void configureActionList() {
        actionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actionList.setVisibleRowCount(10);
        actionList.setCellRenderer(new ActionRenderer());
        actionList.getAccessibleContext().setAccessibleName("Action results");
        applyActionListChrome();
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
     * Applies list colors and the two-line palette row height.
     */
    private void applyActionListChrome() {
        Theme.list(actionList);
        int titleHeight = actionList.getFontMetrics(Theme.font(13, Font.BOLD)).getHeight();
        int detailHeight = actionList.getFontMetrics(Theme.font(11, Font.PLAIN)).getHeight();
        actionList.setFixedCellHeight(Math.max(48, titleHeight + detailHeight + 18));
    }

    /**
     * Configures the empty-state label.
     */
    private void configureEmptyLabel() {
        emptyLabel.setForeground(Theme.MUTED);
        emptyLabel.setFont(Theme.font(12, Font.PLAIN));
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setVisible(false);
    }

    /**
     * Installs keyboard behavior for palette execution and dismissal.
     */
    private void installKeys() {
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "closePalette");
        getActionMap().put("closePalette", swingAction(this::hidePalette));
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
        hidePalette();
        SwingUtilities.invokeLater(() -> {
            try {
                selected.action().run();
            } catch (RuntimeException ex) {
                Component parent = owner;
                javax.swing.JOptionPane.showMessageDialog(parent,
                        "Action failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                        "Action error", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * Hides the in-frame overlay without discarding recent-action state.
     */
    private void hidePalette() {
        setVisible(false);
        owner.getLayeredPane().repaint(getBounds());
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
    public record PaletteAction(String title, String detail, Runnable action) {

        /**
         * Creates a normalized palette action.
         *
         * @param title action title
         * @param detail concise action detail
         * @param action action callback
         */
        public PaletteAction {
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
        public boolean matches(String query) {
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
            title.setFont(Theme.font(13, Font.BOLD));
            title.setHorizontalAlignment(SwingConstants.LEFT);
            detail.setFont(Theme.font(11, Font.PLAIN));
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
            panel.setBackground(selected ? Theme.SELECTION_SOLID : Theme.ELEVATED_SOLID);
            title.setForeground(Theme.TEXT);
            title.setText(elideToListWidth(action.title(), list, Theme.font(13, Font.BOLD)));
            detail.setForeground(Theme.MUTED);
            detail.setText(elideToListWidth(action.detail(), list, Theme.font(11, Font.PLAIN)));
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
            return Ui.elide(text, list.getFontMetrics(font), available);
        }
    }
}
