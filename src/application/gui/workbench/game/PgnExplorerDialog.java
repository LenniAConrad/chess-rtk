package application.gui.workbench.game;

import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

import static application.gui.workbench.ui.Ui.onTextChange;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * VS Code-inspired quick-open dialog for PGN files and searchable game lists.
 */
public final class PgnExplorerDialog extends JDialog {

    /**
     * Serialization identifier for Swing dialog compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Dialog width in pixels.
     */
    private static final int WIDTH = 660;

    /**
     * Dialog height in pixels.
     */
    private static final int HEIGHT = 460;

    /**
     * Search launcher height in pixels.
     */
    private static final int BAR_HEIGHT = 34;

    /**
     * Horizontal padding inside the search field (no theme colour, so it is a
     * shared constant).
     */
    private static final javax.swing.border.Border SEARCH_FIELD_PADDING =
            Theme.pad(0, Theme.SPACE_XS, 0, Theme.SPACE_XS);

    /**
     * Builds the quick-open bar border. Rebuilt on demand (not cached) because it
     * embeds {@link Theme#LINE}, which changes when the theme is toggled.
     *
     * @return search-bar compound border
     */
    private static javax.swing.border.Border searchBarBorder() {
        return BorderFactory.createCompoundBorder(
                Theme.lineBorder(Theme.LINE),
                Theme.pad(0, 8, 0, 8));
    }

    /**
     * Loader called with the chosen PGN/FEN/SAN text.
     */
    private final transient Consumer<String> textLoader;

    /**
     * Supplies the current Workbench board FEN for position filtering.
     */
    private final transient Supplier<String> currentFenSupplier;

    /**
     * Clipboard helper for selected database rows.
     */
    private final transient Consumer<String> copyText;

    /**
     * Text field that filters the currently loaded PGN games.
     */
    private final JTextField searchField = new JTextField();

    /**
     * Filtered PGN entry model.
     */
    private final DefaultListModel<PgnExplorerModel.Entry> visibleEntries =
            new DefaultListModel<>();

    /**
     * Result list.
     */
    private final JList<PgnExplorerModel.Entry> resultList =
            new JList<>(visibleEntries);

    /**
     * Status badge shown in the command bar.
     */
    private final JLabel countLabel = new JLabel("0");

    /**
     * Custom quick-open bar whose background/border must be refreshed manually.
     */
    private JPanel quickOpenBar;

    /**
     * Secondary status line.
     */
    private final JLabel statusLabel = new JLabel("Open a PGN file or load the current game.");

    /**
     * Primary action button.
     */
    private final JButton loadButton = Ui.button("Load", true, event -> loadSelection());

    /**
     * File-open action button.
     */
    private final JButton openButton = Ui.button("Open PGN", false, event -> openFile());

    /**
     * Position filter button.
     */
    private final JButton positionButton = Ui.button("Current Position", false, event -> togglePositionFilter());

    /**
     * Duplicate cleanup button.
     */
    private final JButton dedupeButton = Ui.button("Dedupe Games", false, event -> deduplicateSource());

    /**
     * Selected-PGN copy button.
     */
    private final JButton copyButton = Ui.button("Copy PGN", false, event -> copySelection());

    /**
     * Prep-report copy button.
     */
    private final JButton reportButton = Ui.button("Prep Report", false, event -> copyPrepReport());

    /**
     * Full source text for the currently loaded file or seed.
     */
    private String sourceText = "";

    /**
     * All parsed PGN entries for the current source.
     */
    private List<PgnExplorerModel.Entry> entries = List.of();

    /**
     * True when result rows are limited to games reaching the current board.
     */
    private boolean positionFilterActive;

    /**
     * Creates a PGN explorer dialog.
     *
     * @param owner parent workbench window
     * @param textLoader callback receiving selected text
     */
    public PgnExplorerDialog(JFrame owner, Consumer<String> textLoader) {
        this(owner, textLoader, () -> "", text -> { });
    }

    /**
     * Creates a PGN explorer dialog.
     *
     * @param owner parent workbench window
     * @param textLoader callback receiving selected text
     * @param currentFenSupplier current board FEN supplier
     * @param copyText clipboard callback
     */
    public PgnExplorerDialog(JFrame owner, Consumer<String> textLoader,
            Supplier<String> currentFenSupplier, Consumer<String> copyText) {
        super(owner, "PGN Explorer", false);
        this.textLoader = textLoader == null ? text -> { } : textLoader;
        this.currentFenSupplier = currentFenSupplier == null ? () -> "" : currentFenSupplier;
        this.copyText = copyText == null ? text -> { } : copyText;
        positionButton.getAccessibleContext().setAccessibleDescription(
                "Filter loaded PGN games to those reaching the current Workbench board position.");
        dedupeButton.getAccessibleContext().setAccessibleDescription(
                "Remove exact duplicate games from the loaded PGN database.");
        reportButton.getAccessibleContext().setAccessibleDescription(
                "Copy an opening and player prep report for the visible games.");
        copyButton.getAccessibleContext().setAccessibleDescription("Copy the selected PGN game.");
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setMinimumSize(new Dimension(WIDTH, HEIGHT));
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        getRootPane().setBorder(Theme.lineBorder(Theme.LINE));
        setContentPane(createContent());
        installKeys();
        pack();
    }

    /**
     * Shows the explorer with a seed PGN, usually the current workbench game.
     *
     * @param seedText current PGN/FEN/SAN text
     */
    public void showExplorer(String seedText) {
        refreshTheme();
        applySource(seedText == null ? "" : seedText, "Current workbench game",
                PgnExplorerModel.entries(seedText));
        searchField.setText("");
        setLocationRelativeTo(getOwner());
        setVisible(true);
        SwingUtilities.invokeLater(() -> {
            searchField.requestFocusInWindow();
            searchField.selectAll();
        });
    }

    /**
     * Refreshes custom chrome after a workbench theme switch.
     */
    public void refreshTheme() {
        getRootPane().setBorder(Theme.lineBorder(Theme.LINE));
        Theme.refreshComponentTree(this);
        if (quickOpenBar != null) {
            quickOpenBar.setBackground(Theme.INPUT);
            quickOpenBar.setBorder(searchBarBorder());
        }
        searchField.setBackground(Theme.INPUT);
        searchField.setBorder(SEARCH_FIELD_PADDING);
        statusLabel.setForeground(Theme.MUTED);
        countLabel.setForeground(Theme.MUTED);
        repaint();
    }

    /**
     * Creates the dialog content.
     *
     * @return root component
     */
    private JComponent createContent() {
        JPanel content = new SurfacePanel(new BorderLayout(8, 8));
        content.setBorder(Theme.pad(Theme.SPACE_MD));
        content.add(createQuickOpenBar(), BorderLayout.NORTH);
        configureResults();
        content.add(scroll(resultList), BorderLayout.CENTER);

        JPanel footer = transparentPanel(new BorderLayout(8, 0));
        Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
        statusLabel.setFont(Theme.font(12, Font.PLAIN));
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(Ui.buttonRow(java.awt.FlowLayout.RIGHT, positionButton, dedupeButton, reportButton, copyButton,
                openButton, loadButton),
                BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        return content;
    }

    /**
     * Creates the compact command-center search bar.
     *
     * @return search bar
     */
    private JComponent createQuickOpenBar() {
        JPanel bar = transparentPanel(new BorderLayout(8, 0));
        quickOpenBar = bar;
        bar.setOpaque(true);
        bar.setBackground(Theme.INPUT);
        bar.setBorder(searchBarBorder());
        bar.setPreferredSize(new Dimension(WIDTH - 20, BAR_HEIGHT));

        JLabel title = new JLabel("chess-rtk");
        Theme.foreground(title, Theme.ForegroundRole.MUTED);
        title.setFont(Theme.font(12, Font.PLAIN));
        title.setHorizontalAlignment(SwingConstants.LEFT);
        bar.add(title, BorderLayout.WEST);

        styleFields(searchField);
        searchField.setBorder(SEARCH_FIELD_PADDING);
        searchField.setBackground(Theme.INPUT);
        searchField.setToolTipText("Search loaded PGN games");
        searchField.getAccessibleContext().setAccessibleName("Search PGN games");
        Ui.placeholder(searchField, "Search games, players, openings, tags...");
        onTextChange(this::refilter, searchField);
        bar.add(searchField, BorderLayout.CENTER);

        JPanel right = transparentPanel(new BorderLayout(8, 0));
        Theme.foreground(countLabel, Theme.ForegroundRole.MUTED);
        countLabel.setFont(Theme.font(12, Font.BOLD));
        countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        right.add(countLabel, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /**
     * Configures result list selection, rendering, and activation.
     */
    private void configureResults() {
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setVisibleRowCount(9);
        resultList.setFixedCellHeight(48);
        resultList.setCellRenderer(new EntryRenderer());
        Theme.list(resultList);
        resultList.getAccessibleContext().setAccessibleName("PGN games");
        resultList.addMouseListener(new MouseAdapter() {
            /**
             * Loads a game on double click.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    loadSelection();
                }
            }
        });
    }

    /**
     * Installs dialog keyboard behavior.
     */
    private void installKeys() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "closePgnExplorer");
        getRootPane().getActionMap().put("closePgnExplorer",
                swingAction(() -> setVisible(false)));
        searchField.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "pgnExplorerDown");
        searchField.getInputMap().put(KeyStroke.getKeyStroke("UP"), "pgnExplorerUp");
        searchField.getActionMap().put("pgnExplorerDown", swingAction(() -> moveSelection(1)));
        searchField.getActionMap().put("pgnExplorerUp", swingAction(() -> moveSelection(-1)));
        searchField.addActionListener(event -> loadSelection());
        resultList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "pgnExplorerLoad");
        resultList.getActionMap().put("pgnExplorerLoad", swingAction(this::loadSelection));
    }

    /**
     * Creates a Swing action from a runnable callback.
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
             * Runs the action callback.
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
     * Opens and parses a PGN/text/FEN file without blocking the Swing thread.
     */
    private void openFile() {
        JFileChooser chooser = FileDialogs.createFileChooser("Open PGN",
                new File("game.pgn"),
                new FileNameExtensionFilter("PGN, text, or FEN files", "pgn", "txt", "fen"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        setBusy("Loading " + path.getFileName() + "...");
        new SwingWorker<LoadedSource, Void>() {
            /**
             * Reads and parses the file off the EDT.
             *
             * @return loaded source
             * @throws Exception when reading fails
             */
            @Override
            protected LoadedSource doInBackground() throws Exception {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                return new LoadedSource(path.getFileName().toString(), text,
                        PgnExplorerModel.entries(text));
            }

            /**
             * Applies the loaded source on the EDT.
             */
            @Override
            protected void done() {
                try {
                    LoadedSource loaded = get();
                    applySource(loaded.text(), loaded.name(), loaded.entries());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showLoadError(ex);
                } catch (java.util.concurrent.ExecutionException ex) {
                    showLoadError(ex.getCause());
                }
            }
        }.execute();
    }

    /**
     * Applies loaded source text and parsed entries.
     *
     * @param text raw source text
     * @param name source display name
     * @param parsed parsed entries
     */
    private void applySource(String text, String name, List<PgnExplorerModel.Entry> parsed) {
        sourceText = text == null ? "" : text;
        entries = parsed == null ? List.of() : List.copyOf(parsed);
        positionFilterActive = false;
        positionButton.setText("Current Position");
        statusLabel.setText(entries.isEmpty()
                ? "No PGN games parsed. Load will try the source as FEN or SAN/UCI."
                : databaseStatus(name, entries));
        refilter();
    }

    /**
     * Rebuilds the filtered result list.
     */
    private void refilter() {
        visibleEntries.clear();
        List<PgnExplorerModel.Entry> base = positionFilterActive
                ? PgnExplorerModel.filterByPosition(entries, currentFenSupplier.get())
                : entries;
        List<PgnExplorerModel.Entry> filtered =
                PgnExplorerModel.filter(base, searchField.getText());
        for (PgnExplorerModel.Entry entry : filtered) {
            visibleEntries.addElement(entry);
        }
        countLabel.setText(filtered.size() + "/" + entries.size());
        if (!visibleEntries.isEmpty()) {
            resultList.setSelectedIndex(0);
        }
        boolean hasSelection = !visibleEntries.isEmpty() || !sourceText.isBlank();
        loadButton.setEnabled(hasSelection);
        copyButton.setEnabled(hasSelection);
        reportButton.setEnabled(!visibleEntries.isEmpty());
        dedupeButton.setEnabled(PgnExplorerModel.duplicateCount(entries) > 0);
    }

    /**
     * Moves the selected result row.
     *
     * @param delta row delta
     */
    private void moveSelection(int delta) {
        if (visibleEntries.isEmpty()) {
            return;
        }
        int current = Math.max(0, resultList.getSelectedIndex());
        int next = Math.max(0, Math.min(visibleEntries.size() - 1, current + delta));
        resultList.setSelectedIndex(next);
        resultList.ensureIndexIsVisible(next);
    }

    /**
     * Loads the selected PGN game, or the whole source when no games parsed.
     */
    private void loadSelection() {
        PgnExplorerModel.Entry selected = resultList.getSelectedValue();
        String text = selected == null ? sourceText : selected.pgn();
        if (text == null || text.isBlank()) {
            statusLabel.setText("Open a PGN file first.");
            return;
        }
        setVisible(false);
        SwingUtilities.invokeLater(() -> textLoader.accept(text));
    }

    /**
     * Toggles filtering to games that reach the current Workbench position.
     */
    private void togglePositionFilter() {
        if (entries.isEmpty()) {
            statusLabel.setText("Open a PGN file first.");
            return;
        }
        positionFilterActive = !positionFilterActive;
        positionButton.setText(positionFilterActive ? "All Games" : "Current Position");
        refilter();
        if (positionFilterActive && visibleEntries.isEmpty()) {
            statusLabel.setText("No loaded games reach the current board position.");
        } else if (positionFilterActive) {
            statusLabel.setText(visibleEntries.size() + " games reach the current board position.");
        } else {
            statusLabel.setText(databaseStatus("Database", entries));
        }
    }

    /**
     * Removes exact duplicate game rows from the loaded source.
     */
    private void deduplicateSource() {
        int duplicates = PgnExplorerModel.duplicateCount(entries);
        if (duplicates <= 0) {
            statusLabel.setText("No duplicate games found.");
            return;
        }
        entries = PgnExplorerModel.deduplicate(entries);
        positionFilterActive = false;
        positionButton.setText("Current Position");
        refilter();
        statusLabel.setText("Removed " + duplicates + " duplicate game" + (duplicates == 1 ? "" : "s")
                + ".");
    }

    /**
     * Copies the selected PGN game or raw source text.
     */
    private void copySelection() {
        PgnExplorerModel.Entry selected = resultList.getSelectedValue();
        String text = selected == null ? sourceText : selected.pgn();
        if (text == null || text.isBlank()) {
            statusLabel.setText("Open a PGN file first.");
            return;
        }
        copyText.accept(text);
        statusLabel.setText(selected == null ? "Copied loaded source." : "Copied game " + selected.index() + ".");
    }

    /**
     * Copies a prep report for the visible database rows.
     */
    private void copyPrepReport() {
        List<PgnExplorerModel.Entry> rows = visibleRows();
        if (rows.isEmpty()) {
            statusLabel.setText("No games to report.");
            return;
        }
        String player = searchField.getText() == null ? "" : searchField.getText().trim();
        copyText.accept(PgnPrepReport.report(rows, player));
        statusLabel.setText("Copied prep report for " + rows.size() + " game"
                + (rows.size() == 1 ? "" : "s") + ".");
    }

    /**
     * Returns visible rows in display order.
     *
     * @return visible rows
     */
    private List<PgnExplorerModel.Entry> visibleRows() {
        java.util.ArrayList<PgnExplorerModel.Entry> rows = new java.util.ArrayList<>(visibleEntries.size());
        for (int i = 0; i < visibleEntries.size(); i++) {
            rows.add(visibleEntries.get(i));
        }
        return List.copyOf(rows);
    }

    /**
     * Shows a loading state in the footer.
     *
     * @param message status message
     */
    private void setBusy(String message) {
        statusLabel.setText(message);
        loadButton.setEnabled(false);
        countLabel.setText("...");
    }

    /**
     * Shows an error for failed file loading.
     *
     * @param error load error
     */
    private void showLoadError(Throwable error) {
        String message = error == null || error.getMessage() == null
                ? "Unable to load PGN file."
                : error.getMessage();
        statusLabel.setText("Load failed: " + message);
        loadButton.setEnabled(!visibleEntries.isEmpty() || !sourceText.isBlank());
        countLabel.setText(Integer.toString(visibleEntries.size()));
    }

    /**
     * Builds a compact database status line.
     *
     * @param name source name
     * @param rows database rows
     * @return status line
     */
    private static String databaseStatus(String name, List<PgnExplorerModel.Entry> rows) {
        int games = rows == null ? 0 : rows.size();
        int duplicates = PgnExplorerModel.duplicateCount(rows);
        return name + ": " + games + " game" + (games == 1 ? "" : "s")
                + (duplicates > 0 ? ", " + duplicates + " duplicate" + (duplicates == 1 ? "" : "s") : "");
    }

    /**
     * Loaded source bundle produced by the background file worker.
     *
     * @param name source display name
     * @param text source text
     * @param entries parsed entries
     */
    private record LoadedSource(String name, String text,
            List<PgnExplorerModel.Entry> entries) { }

    /**
     * Renderer for PGN explorer entries.
     */
    private static final class EntryRenderer extends DefaultListCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Renders one PGN entry row.
         *
         * @param list source list
         * @param value row value
         * @param index row index
         * @param selected true when selected
         * @param focus true when focused
         * @return renderer component
         */
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof PgnExplorerModel.Entry entry) {
                label.setText(html(entry));
            }
            label.setBorder(Theme.pad(6, 10, 6, 10));
            label.setFont(Theme.font(12, Font.PLAIN));
            label.setOpaque(true);
            if (selected) {
                label.setBackground(Theme.SELECTION_SOLID);
                label.setForeground(Theme.TEXT);
            } else {
                label.setBackground(Theme.PANEL_SOLID);
                label.setForeground(Theme.TEXT);
            }
            return label;
        }

        /**
         * Builds safe two-line HTML for one entry.
         *
         * @param entry source entry
         * @return HTML text
         */
        private static String html(PgnExplorerModel.Entry entry) {
            return "<html><b>" + escape(entry.title()) + "</b><br><span style='color:"
                    + colorHex(Theme.MUTED) + "'>" + escape(databaseLine(entry)) + "</span></html>";
        }

        /**
         * Builds a compact database row detail.
         *
         * @param entry source entry
         * @return detail text
         */
        private static String databaseLine(PgnExplorerModel.Entry entry) {
            String base = entry.detail() == null || entry.detail().isBlank() ? "" : entry.detail();
            String plys = entry.plyCount() + " ply";
            return base.isBlank() ? plys : base + " | " + plys;
        }

        /**
         * Escapes HTML-sensitive characters.
         *
         * @param text raw text
         * @return escaped text
         */
        private static String escape(String text) {
            if (text == null) {
                return "";
            }
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        /**
         * Converts a color to an HTML hex value.
         *
         * @param color source color
         * @return HTML hex color
         */
        private static String colorHex(java.awt.Color color) {
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }
    }
}
