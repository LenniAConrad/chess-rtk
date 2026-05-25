package application.gui.workbench.command;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import static application.gui.workbench.ui.Ui.onTextChange;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Searchable native Swing action palette for command-heavy workbench flows.
 *
 * <p>The palette mounts into the owner frame's layered pane instead of opening
 * a separate top-level window, matching the VS Code command palette model.</p>
 */
public final class CommandPalette extends JPanel {

    /** Serialization identifier for Swing dialog compatibility. */
    private static final long serialVersionUID = 1L;

    /** Preferred palette width in pixels. */
    private static final int WIDTH = 600;

    /** Preferred palette height in pixels. */
    private static final int HEIGHT = 520;

    /** Minimum overlay width that keeps the search field usable. */
    private static final int MIN_WIDTH = 360;

    /** Minimum overlay height that keeps several actions visible. */
    private static final int MIN_HEIGHT = 240;

    /** Horizontal margin inside the layered pane. */
    private static final int SIDE_MARGIN = 16;

    /** Distance from the top of the workbench content area. */
    private static final int TOP_MARGIN = 12;

    /** Tight corner radius matching VS Code's palette body. */
    private static final int PALETTE_RADIUS = 4;

    /** Pixels reserved below the body for the drop shadow. */
    private static final int SHADOW_SIZE = 6;

    /** Horizontal padding shared by the search field underline and result rows. */
    private static final int ROW_HPAD = 12;

    /** Maximum number of recent action titles retained for frecency boosting. */
    private static final int MAX_RECENTS = 12;

    /** Shared empty-hits sentinel so non-search renders allocate nothing. */
    private static final int[] NO_HITS = new int[0];

    /** Owning workbench frame. */
    private final JFrame owner;

    /** Input field used to filter actions. */
    private final JTextField searchField = new JTextField();

    /** Visible row model. */
    private final DefaultListModel<PaletteRow> visibleRows = new DefaultListModel<>();

    /** Result row list. */
    private final JList<PaletteRow> rowList = new JList<>(visibleRows);

    /** Empty-state label shown when no actions match. */
    private final JLabel emptyLabel = new JLabel("No matching commands. Press Esc to dismiss.");

    /** Full action list for the current palette invocation. */
    private List<PaletteAction> actions = List.of();

    /** Recently-run action titles, most-recent first. */
    private final LinkedHashSet<String> recentTitles = new LinkedHashSet<>();

    /** Repositions the floating overlay when the owning frame changes size. */
    private final ComponentAdapter ownerResizeListener = new ComponentAdapter() {
        /**
         * Repositions the palette after the owner frame is resized.
         *
         * @param event resize event
         */
        @Override
        public void componentResized(ComponentEvent event) {
            positionOverlay();
        }

        /**
         * Repositions the palette after the owner frame becomes visible.
         *
         * @param event show event
         */
        @Override
        public void componentShown(ComponentEvent event) {
            positionOverlay();
        }
    };

    /** True once the owner resize listener has been installed. */
    private boolean resizeListenerInstalled;

    /** Lazily-created outside-click dismiss listener. */
    private java.awt.event.AWTEventListener outsideClickDismiss;

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
        // Empty border reserves the bottom band where paintComponent renders
        // the drop shadow, so content never overlaps it.
        setBorder(BorderFactory.createEmptyBorder(0, 0, SHADOW_SIZE, 0));
        add(createContent(), BorderLayout.CENTER);
        installKeys();
    }

    /**
     * Paints a soft drop shadow followed by the rounded palette body so the
     * whole interior reads as one elevated surface above the workbench.
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int bodyW = w - 1;
            int bodyH = h - SHADOW_SIZE - 1;
            // Stacked translucent rects fake a soft shadow underneath the body.
            for (int i = SHADOW_SIZE; i >= 1; i--) {
                int alpha = Math.max(4, 26 - (i * 4));
                g.setColor(new Color(0, 0, 0, alpha));
                g.fillRoundRect(i / 2, i, bodyW - i, bodyH,
                        PALETTE_RADIUS + 1, PALETTE_RADIUS + 1);
            }
            g.setColor(Theme.ELEVATED_SOLID);
            g.fillRoundRect(0, 0, bodyW, bodyH, PALETTE_RADIUS, PALETTE_RADIUS);
            g.setColor(Theme.LINE);
            g.drawRoundRect(0, 0, bodyW, bodyH, PALETTE_RADIUS, PALETTE_RADIUS);
        } finally {
            g.dispose();
        }
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
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(
                outsideClickListener(), java.awt.AWTEvent.MOUSE_EVENT_MASK);
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
        JPanel content = transparentPanel(new BorderLayout(0, 4));
        content.setBorder(Theme.pad(8, 8, 0, 8));

        styleFields(searchField);
        Ui.placeholder(searchField, "Type a command or action…");
        searchField.setToolTipText("Search actions");
        searchField.getAccessibleContext().setAccessibleName("Search actions");
        searchField.setOpaque(true);
        searchField.setBackground(Theme.PANEL_SOLID);
        searchField.setBorder(buildSearchBorder());
        onTextChange(this::refill, searchField);
        content.add(searchField, BorderLayout.NORTH);

        configureRowList();
        JPanel results = transparentPanel(new BorderLayout(0, 6));
        JScrollPane resultScroll = new JScrollPane(rowList);
        resultScroll.setOpaque(false);
        resultScroll.setBorder(BorderFactory.createEmptyBorder());
        resultScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        resultScroll.getViewport().setOpaque(false);
        resultScroll.getViewport().setBackground(new Color(0, 0, 0, 0));
        Color clear = new Color(0, 0, 0, 0);
        for (String corner : new String[] {
                ScrollPaneConstants.UPPER_LEFT_CORNER,
                ScrollPaneConstants.UPPER_RIGHT_CORNER,
                ScrollPaneConstants.LOWER_LEFT_CORNER,
                ScrollPaneConstants.LOWER_RIGHT_CORNER }) {
            JPanel c = new JPanel();
            c.setOpaque(false);
            c.setBackground(clear);
            resultScroll.setCorner(corner, c);
        }
        results.add(resultScroll, BorderLayout.CENTER);
        configureEmptyLabel();
        results.add(emptyLabel, BorderLayout.SOUTH);
        content.add(results, BorderLayout.CENTER);
        return content;
    }

    /**
     * Builds the search-field border so the underline aligns with the row
     * padding (looks like one continuous column).
     */
    private static javax.swing.border.Border buildSearchBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT, 1),
                BorderFactory.createEmptyBorder(7, ROW_HPAD, 7, ROW_HPAD));
    }

    /** Reapplies current theme colors after a mode switch. */
    public void refreshTheme() {
        Theme.refreshComponentTree(this);
        searchField.setBackground(Theme.PANEL_SOLID);
        searchField.setBorder(buildSearchBorder());
        applyRowListChrome();
        emptyLabel.setForeground(Theme.MUTED);
        repaint();
    }

    /** Adds this overlay to the owning frame's layered pane. */
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

    /** Positions the overlay at the top center of the workbench content. */
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

    /** Configures the searchable row list. */
    private void configureRowList() {
        rowList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rowList.setVisibleRowCount(10);
        rowList.setCellRenderer(new RowRenderer());
        rowList.getAccessibleContext().setAccessibleName("Action results");
        applyRowListChrome();
        rowList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    runSelection();
                }
            }
        });
    }

    /** Applies list colors. The renderer is responsible for selection paint. */
    private void applyRowListChrome() {
        Theme.list(rowList);
        rowList.setOpaque(false);
        rowList.setBackground(new Color(0, 0, 0, 0));
        // Defeat the L&F selection background — RowRenderer paints its own
        // accent fill so the list's selection bg would double-draw.
        rowList.setSelectionBackground(new Color(0, 0, 0, 0));
        rowList.setFixedCellHeight(-1);
    }

    /** Configures the empty-state label. */
    private void configureEmptyLabel() {
        emptyLabel.setForeground(Theme.MUTED);
        emptyLabel.setFont(Theme.font(12, Font.PLAIN));
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        emptyLabel.setVisible(false);
    }

    /** Installs keyboard behavior for palette execution and dismissal. */
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
        rowList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "paletteRun");
        rowList.getActionMap().put("paletteRun", swingAction(this::runSelection));
        searchField.addActionListener(event -> runSelection());
    }

    /** Creates a Swing action from a runnable. */
    private static AbstractAction swingAction(Runnable runnable) {
        return new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                runnable.run();
            }
        };
    }

    /** Rebuilds visible rows for the current search query. */
    private void refill() {
        visibleRows.clear();
        String query = searchField.getText();
        boolean blank = query == null || query.isBlank();
        if (blank) {
            List<PaletteAction> recents = recentActionsInOrder();
            if (!recents.isEmpty()) {
                visibleRows.addElement(new PaletteRow.Header("recently used"));
                for (PaletteAction action : recents) {
                    visibleRows.addElement(new PaletteRow.ActionRow(action, NO_HITS));
                }
            }
            addGroupedRows(actions.stream()
                    .filter(action -> !recentTitles.contains(action.title()))
                    .toList(), !recents.isEmpty());
        } else {
            String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
            List<RankedRow> ranked = new ArrayList<>();
            for (PaletteAction action : actions) {
                PaletteAction.MatchResult mr = action.match(lowerQuery);
                if (mr == null) {
                    continue;
                }
                int boost = recencyBoost(action.title());
                ranked.add(new RankedRow(action, mr.hits(), mr.score() + boost));
            }
            ranked.sort((a, b) -> Integer.compare(b.score, a.score));
            for (RankedRow ra : ranked) {
                visibleRows.addElement(new PaletteRow.ActionRow(ra.action, ra.hits));
            }
        }
        selectFirstAction();
        boolean hasAction = anySelectableRow();
        emptyLabel.setVisible(!hasAction);
        emptyLabel.getParent().revalidate();
        emptyLabel.getParent().repaint();
    }

    /**
     * Adds blank-query actions grouped by command type. The palette still
     * preserves the caller's category order, but headers and dividers make the
     * command families scannable instead of one undifferentiated list.
     *
     * @param source actions to add
     * @param leadingDivider true when a section already precedes these rows
     */
    private void addGroupedRows(List<PaletteAction> source, boolean leadingDivider) {
        Map<String, List<PaletteAction>> groups = new LinkedHashMap<>();
        for (PaletteAction action : source) {
            String category = action.category().isBlank() ? "Commands" : action.category();
            groups.computeIfAbsent(category, key -> new ArrayList<>()).add(action);
        }
        boolean first = true;
        for (Map.Entry<String, List<PaletteAction>> entry : groups.entrySet()) {
            if (!first || leadingDivider) {
                visibleRows.addElement(PaletteRow.Divider.INSTANCE);
            }
            visibleRows.addElement(new PaletteRow.Header(entry.getKey()));
            for (PaletteAction action : entry.getValue()) {
                visibleRows.addElement(new PaletteRow.ActionRow(action, NO_HITS, false));
            }
            first = false;
            leadingDivider = false;
        }
    }

    /** Returns recent actions in most-recently-used order. */
    private List<PaletteAction> recentActionsInOrder() {
        List<PaletteAction> recents = new ArrayList<>();
        for (String title : recentTitles) {
            for (PaletteAction action : actions) {
                if (action.title().equals(title)) {
                    recents.add(action);
                    break;
                }
            }
        }
        return recents;
    }

    /** Returns true when at least one selectable action row is visible. */
    private boolean anySelectableRow() {
        for (int i = 0; i < visibleRows.size(); i++) {
            if (visibleRows.get(i) instanceof PaletteRow.ActionRow) {
                return true;
            }
        }
        return false;
    }

    /** Selects the first action row, skipping headers and dividers. */
    private void selectFirstAction() {
        for (int i = 0; i < visibleRows.size(); i++) {
            if (visibleRows.get(i) instanceof PaletteRow.ActionRow) {
                rowList.setSelectedIndex(i);
                rowList.ensureIndexIsVisible(i);
                return;
            }
        }
        rowList.clearSelection();
    }

    /**
     * Score boost for actions that were run recently, scaled by recency rank.
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

    /** One ranked palette result. */
    private record RankedRow(PaletteAction action, int[] hits, int score) { }

    /**
     * Moves the selected row, skipping past headers and dividers in the
     * stepping direction.
     */
    private void moveSelection(int delta) {
        if (visibleRows.isEmpty() || delta == 0) {
            return;
        }
        int dir = delta > 0 ? 1 : -1;
        int steps = Math.abs(delta);
        int current = rowList.getSelectedIndex();
        if (current < 0) {
            current = dir > 0 ? -1 : visibleRows.size();
        }
        int next = current;
        for (int s = 0; s < steps; s++) {
            int candidate = next + dir;
            while (candidate >= 0 && candidate < visibleRows.size()
                    && !(visibleRows.get(candidate) instanceof PaletteRow.ActionRow)) {
                candidate += dir;
            }
            if (candidate < 0 || candidate >= visibleRows.size()) {
                break;
            }
            next = candidate;
        }
        if (next != current && next >= 0 && next < visibleRows.size()) {
            rowList.setSelectedIndex(next);
            rowList.ensureIndexIsVisible(next);
        }
    }

    /** Runs the currently selected action. */
    private void runSelection() {
        PaletteRow selected = rowList.getSelectedValue();
        if (!(selected instanceof PaletteRow.ActionRow row)) {
            return;
        }
        PaletteAction action = row.action();
        recordRecent(action.title());
        hidePalette();
        SwingUtilities.invokeLater(() -> {
            try {
                action.action().run();
            } catch (RuntimeException ex) {
                javax.swing.JOptionPane.showMessageDialog(owner,
                        "Action failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                        "Action error", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * Returns the cached outside-click dismissal listener, creating it on
     * first demand.
     */
    private java.awt.event.AWTEventListener outsideClickListener() {
        if (outsideClickDismiss == null) {
            outsideClickDismiss = event -> {
                if (!isVisible() || !(event instanceof MouseEvent mouse)) {
                    return;
                }
                if (mouse.getID() != MouseEvent.MOUSE_PRESSED) {
                    return;
                }
                java.awt.Point onScreen = mouse.getLocationOnScreen();
                java.awt.Point inFrame = new java.awt.Point(onScreen);
                SwingUtilities.convertPointFromScreen(inFrame, owner.getLayeredPane());
                if (!getBounds().contains(inFrame)) {
                    hidePalette();
                }
            };
        }
        return outsideClickDismiss;
    }

    /** Hides the in-frame overlay without discarding recent-action state. */
    private void hidePalette() {
        setVisible(false);
        owner.getLayeredPane().repaint(getBounds());
        if (outsideClickDismiss != null) {
            java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickDismiss);
        }
    }

    /**
     * Records that an action title was just run so subsequent palette opens
     * float it toward the top.
     */
    private void recordRecent(String title) {
        recentTitles.remove(title);
        LinkedHashSet<String> rebuilt = new LinkedHashSet<>();
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
     * @param category short grouping prefix shown muted before the title (e.g. {@code "View"})
     * @param title action title
     * @param detail concise action detail or shortcut text
     * @param action action callback
     */
    public record PaletteAction(String category, String title, String detail, Runnable action) {

        /**
         * Normalizes the action fields.
         *
         * @param category short grouping prefix
         * @param title action title
         * @param detail action detail text
         * @param action action callback
         */
        public PaletteAction {
            category = category == null ? "" : category;
            title = title == null ? "" : title;
            detail = detail == null ? "" : detail;
            action = action == null ? () -> {
                // no-op fallback
            } : action;
        }

        /** Match score plus title-relative hit indices. */
        public record MatchResult(int score, int[] hits) { }

        /**
         * Returns the title displayed in the palette: {@code "Category: Title"}
         * when a category is present, otherwise just the title.
         *
         * @return display title
         */
        public String displayTitle() {
            return displayTitle(true);
        }

        /**
         * Returns the title displayed in the palette.
         *
         * @param includeCategory true to prepend {@code "Category: "}
         * @return display title
         */
        public String displayTitle(boolean includeCategory) {
            if (!includeCategory) {
                return title;
            }
            return category.isEmpty() ? title : category + ": " + title;
        }

        /**
         * Single-pass fuzzy match over the display title, with a fall-back
         * subsequence pass over the detail (without highlights).
         *
         * @param lowerQuery lowercase trimmed query
         * @return match result, or {@code null} when the action does not match
         */
        public MatchResult match(String lowerQuery) {
            if (lowerQuery == null || lowerQuery.isEmpty()) {
                return new MatchResult(0, NO_HITS);
            }
            String displayLower = displayTitle().toLowerCase(Locale.ROOT);
            int[] titleHits = fuzzy(displayLower, lowerQuery);
            if (titleHits != null) {
                int score = scoreOf(titleHits);
                if (displayLower.contains(lowerQuery)) {
                    score += 80;
                }
                // Title matches outrank detail-only matches by a large margin.
                return new MatchResult(score + 100, titleHits);
            }
            int[] detailHits = fuzzy(detail.toLowerCase(Locale.ROOT), lowerQuery);
            if (detailHits != null) {
                return new MatchResult(scoreOf(detailHits), NO_HITS);
            }
            return null;
        }

        /**
         * Walks {@code haystack} left-to-right and records the position of
         * each query character in order. Returns {@code null} when the query
         * cannot be matched as a subsequence.
         */
        private static int[] fuzzy(String haystack, String query) {
            int[] hits = new int[query.length()];
            int hi = 0;
            for (int qi = 0; qi < query.length(); qi++) {
                char target = query.charAt(qi);
                while (hi < haystack.length() && haystack.charAt(hi) != target) {
                    hi++;
                }
                if (hi >= haystack.length()) {
                    return null;
                }
                hits[qi] = hi;
                hi++;
            }
            return hits;
        }

        /**
         * Scores a hit array, rewarding early matches and consecutive runs.
         */
        private static int scoreOf(int[] hits) {
            if (hits.length == 0) {
                return 0;
            }
            int base = 30 + Math.max(0, 30 - hits[0]);
            int consec = 0;
            for (int i = 1; i < hits.length; i++) {
                if (hits[i] == hits[i - 1] + 1) {
                    consec++;
                }
            }
            return base + consec * 5 + hits.length * 10;
        }

        /**
         * Returns the display title for debugger and Swing renderer fallbacks.
         *
         * @return display title
         */
        @Override
        public String toString() {
            return displayTitle();
        }
    }

    /**
     * Visible row in the palette list. Headers and dividers structure the
     * blank-query view; action rows are the selectable entries.
     */
    public sealed interface PaletteRow {

        /** Section title shown above a group (e.g. {@code "recently used"}). */
        record Header(String label) implements PaletteRow { }

        /** Horizontal rule between sections. */
        final class Divider implements PaletteRow {
            /** Singleton divider instance. */
            public static final Divider INSTANCE = new Divider();

            /**
             * Creates the singleton divider row.
             */
            private Divider() {
            }
        }

        /** Selectable command row with optional title-hit highlights. */
        record ActionRow(PaletteAction action, int[] hits, boolean showCategory) implements PaletteRow {
            /**
             * Creates a command row that includes the category prefix.
             *
             * @param action palette action
             * @param hits highlighted title positions
             */
            ActionRow(PaletteAction action, int[] hits) {
                this(action, hits, true);
            }
        }
    }

    /**
     * Native Swing renderer that paints headers, dividers, and action rows
     * with VS Code-style bold match highlights and right-aligned detail or
     * keycap chips.
     */
    private static final class RowRenderer extends DefaultListCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Reused action-row renderer component.
         */
        private final ActionRowPanel actionPanel = new ActionRowPanel();

        /**
         * Reused section-header renderer component.
         */
        private final HeaderRowPanel headerPanel = new HeaderRowPanel();

        /**
         * Reused divider renderer component.
         */
        private final DividerRowPanel dividerPanel = new DividerRowPanel();

        /**
         * Returns the renderer component for one palette row.
         *
         * @param list owning list
         * @param value row value
         * @param index row index
         * @param selected true when selected
         * @param focused true when focused
         * @return renderer component
         */
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                boolean focused) {
            if (value instanceof PaletteRow.Header header) {
                headerPanel.update(header.label());
                return headerPanel;
            }
            if (value instanceof PaletteRow.Divider) {
                return dividerPanel;
            }
            PaletteRow.ActionRow row = value instanceof PaletteRow.ActionRow ar
                    ? ar
                    : new PaletteRow.ActionRow(new PaletteAction("", String.valueOf(value), "", null), NO_HITS);
            actionPanel.update(row, selected, list.getWidth());
            return actionPanel;
        }
    }

    /** Row panel for a selectable command, with bold-highlighted title. */
    private static final class ActionRowPanel extends JPanel {

        /**
         * Serialization identifier for Swing panel compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Title renderer.
         */
        private final TitleLabel titleLabel = new TitleLabel();

        /**
         * Right-side detail renderer.
         */
        private final DetailComponent detailComponent = new DetailComponent();

        /**
         * Creates an action row panel.
         */
        ActionRowPanel() {
            super(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(4, ROW_HPAD, 4, ROW_HPAD));
            setOpaque(false);
            add(titleLabel, BorderLayout.CENTER);
            add(detailComponent, BorderLayout.EAST);
        }

        /**
         * Updates the row contents and selected state.
         *
         * @param row row model
         * @param selected true when selected
         * @param listWidth available list width
         */
        void update(PaletteRow.ActionRow row, boolean selected, int listWidth) {
            setOpaque(selected);
            if (selected) {
                setBackground(Theme.ACCENT);
            }
            Color fg = selected ? Theme.PRIMARY_BUTTON_TEXT : Theme.TEXT;
            Color categoryFg = selected ? Theme.PRIMARY_BUTTON_TEXT : Theme.MUTED;
            Color detailFg = selected ? Theme.PRIMARY_BUTTON_TEXT : Theme.MUTED;
            titleLabel.update(row.action(), row.hits(), row.showCategory(), fg, categoryFg);
            detailComponent.update(row.action().detail(), detailFg, selected);
            int detailW = detailComponent.getPreferredSize().width;
            int titleAvail = Math.max(80, listWidth - detailW - (ROW_HPAD * 2) - 16);
            titleLabel.setAvailableWidth(titleAvail);
            setToolTipText(row.action().displayTitle()
                    + (row.action().detail().isEmpty() ? "" : " — " + row.action().detail()));
        }
    }

    /**
     * Title label that renders matched characters in bold over a plain base.
     * Also tints the {@code "Category:"} prefix muted.
     */
    private static final class TitleLabel extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Text currently rendered by the title label.
         */
        private String text = "";

        /**
         * Character indexes to draw in bold.
         */
        private int[] hits = NO_HITS;

        /**
         * End offset of the category prefix, or zero when hidden.
         */
        private int categoryEnd;

        /**
         * Foreground color for the command title.
         */
        private Color titleFg = Color.BLACK;

        /**
         * Foreground color for the category prefix.
         */
        private Color categoryFg = Color.GRAY;

        /**
         * Base title font.
         */
        private final Font baseFont = Theme.font(12, Font.PLAIN);

        /**
         * Bold font used for matched characters.
         */
        private final Font boldFont = baseFont.deriveFont(Font.BOLD);

        /**
         * Maximum title width available before elision.
         */
        private int availableWidth = Integer.MAX_VALUE;

        /**
         * Creates a title label.
         */
        TitleLabel() {
            setOpaque(false);
        }

        /**
         * Updates the rendered action title.
         *
         * @param action action to display
         * @param hits highlighted title positions
         * @param showCategory true to include the category prefix
         * @param titleFg title foreground
         * @param categoryFg category foreground
         */
        void update(PaletteAction action, int[] hits, boolean showCategory, Color titleFg, Color categoryFg) {
            this.text = action.displayTitle(showCategory);
            this.hits = hits == null ? NO_HITS : hits;
            this.categoryEnd = !showCategory || action.category().isEmpty() ? 0 : action.category().length() + 2;
            this.titleFg = titleFg;
            this.categoryFg = categoryFg;
        }

        /**
         * Sets the available width before the title is elided.
         *
         * @param width available width in pixels
         */
        void setAvailableWidth(int width) {
            this.availableWidth = Math.max(40, width);
        }

        /**
         * Returns the preferred label size after width elision is applied.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(baseFont);
            return new Dimension(Math.min(availableWidth, fm.stringWidth(text) + 4), fm.getHeight() + 2);
        }

        /**
         * Paints the title with bold fuzzy-match characters.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                FontMetrics regularMetrics = g.getFontMetrics(baseFont);
                int y = regularMetrics.getAscent() + 1;
                int x = 0;
                int max = availableWidth;
                String visible = Ui.elide(text, regularMetrics, max);
                for (int i = 0; i < visible.length(); i++) {
                    char ch = visible.charAt(i);
                    boolean hit = isHit(i);
                    Font font = hit ? boldFont : baseFont;
                    g.setFont(font);
                    g.setColor(i < categoryEnd ? categoryFg : titleFg);
                    g.drawString(String.valueOf(ch), x, y);
                    x += g.getFontMetrics(font).charWidth(ch);
                    if (x > max) {
                        break;
                    }
                }
            } finally {
                g.dispose();
            }
        }

        /**
         * Returns whether a character index is highlighted.
         *
         * @param index character index
         * @return true when highlighted
         */
        private boolean isHit(int index) {
            for (int h : hits) {
                if (h == index) {
                    return true;
                }
                if (h > index) {
                    break;
                }
            }
            return false;
        }
    }

    /**
     * Right-side detail. If the detail string looks like a keyboard shortcut,
     * it is rendered as bordered keycap chips; otherwise as plain muted text.
     */
    private static final class DetailComponent extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Pattern identifying shortcut strings that should render as keycaps.
         */
        private static final java.util.regex.Pattern SHORTCUT = java.util.regex.Pattern.compile(
                "(?i)\\b(ctrl|cmd|alt|shift|meta|⌘|⌃|⌥|⇧|win)\\b.*\\+.*");

        /**
         * Shortcut chips to render.
         */
        private String[] chips = new String[0];

        /**
         * Plain detail text to render when no chips are present.
         */
        private String plain = "";

        /**
         * Current foreground color.
         */
        private Color fg = Color.GRAY;

        /**
         * Whether the owning row is selected.
         */
        private boolean selected;

        /**
         * Plain detail font.
         */
        private final Font baseFont = Theme.font(11, Font.PLAIN);

        /**
         * Keycap chip font.
         */
        private final Font chipFont = Theme.font(10, Font.PLAIN);

        /**
         * Creates a detail renderer.
         */
        DetailComponent() {
            setOpaque(false);
        }

        /**
         * Updates the detail text and selection state.
         *
         * @param detail detail text
         * @param fg foreground color
         * @param selected true when the owning row is selected
         */
        void update(String detail, Color fg, boolean selected) {
            this.fg = fg;
            this.selected = selected;
            if (detail == null || detail.isEmpty()) {
                this.plain = "";
                this.chips = new String[0];
                return;
            }
            if (looksLikeShortcut(detail)) {
                this.plain = "";
                this.chips = detail.split("\\s*\\+\\s*");
            } else {
                this.plain = detail;
                this.chips = new String[0];
            }
        }

        /**
         * Returns whether a detail string looks like a shortcut.
         *
         * @param s detail string
         * @return true when the detail should render as keycaps
         */
        private static boolean looksLikeShortcut(String s) {
            return s.length() <= 24 && SHORTCUT.matcher(s).matches();
        }

        /**
         * Returns preferred size for chips or plain detail text.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            if (chips.length > 0) {
                FontMetrics fm = getFontMetrics(chipFont);
                int w = 0;
                for (int i = 0; i < chips.length; i++) {
                    w += chipWidth(fm, chips[i]);
                    if (i < chips.length - 1) {
                        w += 3;
                    }
                }
                return new Dimension(w, fm.getHeight() + 4);
            }
            FontMetrics fm = getFontMetrics(baseFont);
            return new Dimension(Math.min(260, fm.stringWidth(plain) + 2), fm.getHeight() + 2);
        }

        /**
         * Calculates the rendered width of one shortcut chip.
         *
         * @param fm font metrics
         * @param label chip label
         * @return chip width in pixels
         */
        private static int chipWidth(FontMetrics fm, String label) {
            return fm.stringWidth(label) + 10;
        }

        /**
         * Paints shortcut chips or the plain detail string.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (chips.length > 0) {
                    paintChips(g);
                } else {
                    paintPlain(g);
                }
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints keycap-style shortcut chips.
         *
         * @param g graphics context
         */
        private void paintChips(Graphics2D g) {
            FontMetrics fm = g.getFontMetrics(chipFont);
            int h = fm.getHeight() + 2;
            int y = (getHeight() - h) / 2;
            int x = 0;
            Color chipBorder = selected ? Theme.PRIMARY_BUTTON_TEXT : Theme.LINE;
            Color chipText = fg;
            g.setFont(chipFont);
            for (int i = 0; i < chips.length; i++) {
                int w = chipWidth(fm, chips[i]);
                g.setColor(chipBorder);
                g.drawRoundRect(x, y, w - 1, h - 1, 4, 4);
                g.setColor(chipText);
                g.drawString(chips[i], x + 5, y + fm.getAscent());
                x += w + 3;
            }
        }

        /**
         * Paints the plain right-aligned detail text.
         *
         * @param g graphics context
         */
        private void paintPlain(Graphics2D g) {
            g.setFont(baseFont);
            FontMetrics fm = g.getFontMetrics();
            String fitted = Ui.elide(plain, fm, getWidth());
            g.setColor(fg);
            int textWidth = fm.stringWidth(fitted);
            int x = Math.max(0, getWidth() - textWidth);
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(fitted, x, y);
        }
    }

    /** Section header row. */
    private static final class HeaderRowPanel extends JPanel {

        /**
         * Serialization identifier for Swing panel compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Header label.
         */
        private final JLabel label = new JLabel();

        /**
         * Creates a section-header row.
         */
        HeaderRowPanel() {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, ROW_HPAD, 2, ROW_HPAD));
            label.setFont(Theme.font(10, Font.PLAIN));
            label.setForeground(Theme.MUTED);
            add(label, BorderLayout.CENTER);
        }

        /**
         * Updates the header text.
         *
         * @param text header text
         */
        void update(String text) {
            label.setText(text == null ? "" : text.toUpperCase(Locale.ROOT));
            label.setForeground(Theme.MUTED);
        }

        /**
         * Returns the fixed header row height.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = label.getFontMetrics(label.getFont());
            return new Dimension(0, fm.getHeight() + 10);
        }
    }

    /** Horizontal divider between sections. */
    private static final class DividerRowPanel extends JPanel {

        /**
         * Serialization identifier for Swing panel compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates a divider row.
         */
        DividerRowPanel() {
            setOpaque(false);
        }

        /**
         * Returns the fixed divider row height.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, 7);
        }

        /**
         * Paints the horizontal divider line.
         *
         * @param g graphics context
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Theme.LINE);
            int y = getHeight() / 2;
            g.drawLine(ROW_HPAD, y, getWidth() - ROW_HPAD, y);
        }
    }
}
