package application.gui.workbench.library;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/**
 * Workbench panel for browsing the local PGN game library.
 */
public final class GameLibraryPanel extends JPanel {

    /**
     * Serialization identifier for Swing compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Loads library rows.
     */
    private final transient Supplier<List<GameLibrary.Entry>> entriesSupplier;

    /**
     * Opens one stored PGN game on the board.
     */
    private final transient Consumer<GameLibrary.Entry> openGame;

    /**
     * Copies PGN text.
     */
    private final transient Consumer<String> copyText;

    /**
     * Imports a PGN file.
     */
    private final transient Runnable importPgn;

    /**
     * Saves the current board line.
     */
    private final transient Runnable saveCurrent;

    /**
     * All loaded rows before filtering.
     */
    private final List<GameLibrary.Entry> allEntries = new ArrayList<>();

    /**
     * Visible list model.
     */
    private final DefaultListModel<GameLibrary.Entry> listModel = new DefaultListModel<>();

    /**
     * Visible library list.
     */
    private final JList<GameLibrary.Entry> list = new EntryList(listModel);

    /**
     * Filter text.
     */
    private final JTextField filterField = new FilterField();

    /**
     * Detail preview.
     */
    private final JTextArea detailArea = Ui.commandBlock("");

    /**
     * Creates a library panel.
     *
     * @param entriesSupplier entry supplier
     * @param openGame open callback
     * @param copyText copy callback
     * @param importPgn import callback
     * @param saveCurrent save-current callback
     */
    public GameLibraryPanel(Supplier<List<GameLibrary.Entry>> entriesSupplier,
            Consumer<GameLibrary.Entry> openGame,
            Consumer<String> copyText,
            Runnable importPgn,
            Runnable saveCurrent) {
        super(new BorderLayout(Theme.SPACE_SM, Theme.SPACE_SM));
        this.entriesSupplier = Objects.requireNonNull(entriesSupplier, "entriesSupplier");
        this.openGame = Objects.requireNonNull(openGame, "openGame");
        this.copyText = Objects.requireNonNull(copyText, "copyText");
        this.importPgn = Objects.requireNonNull(importPgn, "importPgn");
        this.saveCurrent = Objects.requireNonNull(saveCurrent, "saveCurrent");
        configure();
        refresh();
    }

    /**
     * Reloads the visible library.
     */
    public void refresh() {
        GameLibrary.Entry selected = selectedEntry();
        allEntries.clear();
        allEntries.addAll(entriesSupplier.get());
        applyFilter();
        restoreSelection(selected);
        updateDetails();
    }

    private void configure() {
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setBorder(Theme.pad(Theme.SPACE_MD));

        Theme.list(list);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new EntryRenderer());
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateDetails();
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            /**
             * Opens one library game on double-click.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && selectedEntry() != null) {
                    openGame.accept(selectedEntry());
                }
            }
        });

        Ui.styleFields(filterField);
        filterField.setToolTipText("Filter by event, player, result, source, or PGN text");
        Ui.onTextChange(this::applyFilterAndSelect, filterField);

        Theme.area(detailArea);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setRows(8);

        add(toolbar(), BorderLayout.NORTH);
        add(Ui.titled("Library", Ui.scroll(list)), BorderLayout.CENTER);
        add(Ui.titled("PGN Preview", Ui.scroll(detailArea)), BorderLayout.SOUTH);
    }

    private JComponent toolbar() {
        JPanel toolbar = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        toolbar.add(filterField, BorderLayout.CENTER);
        JPanel actions = Ui.transparentPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        JButton importButton = Ui.button("Import PGN", true, event -> {
            importPgn.run();
            refresh();
        });
        actions.add(importButton);
        actions.add(Ui.button("Save Current", false, event -> {
            saveCurrent.run();
            refresh();
        }));
        actions.add(Ui.button("Open", false, event -> {
            GameLibrary.Entry entry = selectedEntry();
            if (entry != null) {
                openGame.accept(entry);
            }
        }));
        actions.add(Ui.button("Copy PGN", false, event -> {
            GameLibrary.Entry entry = selectedEntry();
            if (entry != null) {
                copyText.accept(entry.pgn());
            }
        }));
        actions.add(Ui.button("Refresh", false, event -> refresh()));
        toolbar.add(actions, BorderLayout.EAST);
        return toolbar;
    }

    private void applyFilterAndSelect() {
        applyFilter();
        if (!listModel.isEmpty()) {
            list.setSelectedIndex(0);
        }
        updateDetails();
    }

    private void applyFilter() {
        String filter = filterField.getText() == null
                ? ""
                : filterField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        listModel.clear();
        for (GameLibrary.Entry entry : allEntries) {
            if (filter.isBlank() || entry.searchableText().contains(filter)) {
                listModel.addElement(entry);
            }
        }
    }

    private GameLibrary.Entry selectedEntry() {
        return list.getSelectedValue();
    }

    private void restoreSelection(GameLibrary.Entry previous) {
        if (listModel.isEmpty()) {
            return;
        }
        if (previous != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (previous.gameId().equals(listModel.get(i).gameId())) {
                    list.setSelectedIndex(i);
                    return;
                }
            }
        }
        list.setSelectedIndex(0);
    }

    private void updateDetails() {
        GameLibrary.Entry entry = selectedEntry();
        if (entry == null) {
            detailArea.setText("No library games yet.");
            return;
        }
        String source = entry.source().isBlank() ? "unknown source" : entry.source();
        detailArea.setText(entry.label() + System.lineSeparator()
                + "Source: " + source + System.lineSeparator()
                + "Game ID: " + entry.gameId() + System.lineSeparator()
                + System.lineSeparator()
                + entry.pgn());
        detailArea.setCaretPosition(0);
    }

    /**
     * Typed list component for library rows.
     */
    private static final class EntryList extends JList<GameLibrary.Entry> {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the typed list.
         *
         * @param model entry model
         */
        EntryList(DefaultListModel<GameLibrary.Entry> model) {
            super(model);
        }
    }

    /**
     * Filter text field for the library list.
     */
    private static final class FilterField extends JTextField {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;
    }

    /**
     * Renders one library entry.
     */
    private static final class EntryRenderer extends DefaultListCellRenderer {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> source, Object value, int index,
                boolean selected, boolean focused) {
            Component component = super.getListCellRendererComponent(source, value, index, selected, focused);
            if (component instanceof JLabel label && value instanceof GameLibrary.Entry entry) {
                String from = entry.source().isBlank() ? "" : "   |   " + entry.source();
                label.setText(entry.label() + from);
            }
            return component;
        }
    }
}
