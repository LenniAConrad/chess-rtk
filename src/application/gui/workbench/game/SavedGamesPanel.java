package application.gui.workbench.game;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/**
 * Workbench panel for reopening locally saved game lines.
 */
public final class SavedGamesPanel extends JPanel {

    /**
     * Serialization identifier for Swing compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Compact timestamp format.
     */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());

    /**
     * Loads saved games.
     */
    private final transient Supplier<List<SavedGame>> gamesSupplier;

    /**
     * Resumes a selected game.
     */
    private final transient Consumer<SavedGame> resumeGame;

    /**
     * Reviews a selected game.
     */
    private final transient Consumer<SavedGame> reviewGame;

    /**
     * Copies text through the Workbench clipboard helper.
     */
    private final transient Consumer<String> copyText;

    /**
     * Saves the current game line.
     */
    private final transient Runnable saveCurrent;

    /**
     * List model.
     */
    private final DefaultListModel<SavedGame> listModel = new DefaultListModel<>();

    /**
     * Saved-game list.
     */
    private final JList<SavedGame> list = new JList<>(listModel);

    /**
     * Detail preview.
     */
    private final JTextArea detailArea = new JTextArea();

    /**
     * Creates the panel.
     *
     * @param gamesSupplier saved-game supplier
     * @param resumeGame resume callback
     * @param reviewGame review callback
     * @param copyText copy callback
     * @param saveCurrent save-current callback
     */
    public SavedGamesPanel(Supplier<List<SavedGame>> gamesSupplier,
            Consumer<SavedGame> resumeGame,
            Consumer<SavedGame> reviewGame,
            Consumer<String> copyText,
            Runnable saveCurrent) {
        super(new BorderLayout(Theme.SPACE_SM, Theme.SPACE_SM));
        this.gamesSupplier = Objects.requireNonNull(gamesSupplier, "gamesSupplier");
        this.resumeGame = Objects.requireNonNull(resumeGame, "resumeGame");
        this.reviewGame = Objects.requireNonNull(reviewGame, "reviewGame");
        this.copyText = Objects.requireNonNull(copyText, "copyText");
        this.saveCurrent = Objects.requireNonNull(saveCurrent, "saveCurrent");
        configure();
        refresh();
    }

    /**
     * Reloads the visible saved-game list.
     */
    public void refresh() {
        SavedGame selected = list.getSelectedValue();
        listModel.clear();
        List<SavedGame> games = gamesSupplier.get();
        for (SavedGame game : games) {
            listModel.addElement(game);
        }
        restoreSelection(selected);
        updateDetails();
    }

    /**
     * Configures value.
     */
    private void configure() {
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setBorder(Theme.pad(Theme.SPACE_MD));

        Theme.list(list);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new SavedGameRenderer());
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateDetails();
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            /**
             * Resumes a game on double-click.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && selectedGame() != null) {
                    resumeGame.accept(selectedGame());
                }
            }
        });

        Theme.area(detailArea);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setRows(7);

        add(toolbar(), BorderLayout.NORTH);
        add(Ui.titled("Saved Games", Ui.scroll(list)), BorderLayout.CENTER);
        add(Ui.titled("Game Preview", Ui.scroll(detailArea)), BorderLayout.SOUTH);
    }

    /**
     * Builds the saved-game action toolbar.
     *
     * @return toolbar component
     */
    private JComponent toolbar() {
        JPanel toolbar = Ui.transparentPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        JButton save = Ui.button("Save Current", true, event -> {
            saveCurrent.run();
            refresh();
        });
        toolbar.add(save);
        toolbar.add(Ui.button("Resume", false, event -> {
            SavedGame game = selectedGame();
            if (game != null) {
                resumeGame.accept(game);
            }
        }));
        toolbar.add(Ui.button("Review", false, event -> {
            SavedGame game = selectedGame();
            if (game != null) {
                reviewGame.accept(game);
            }
        }));
        toolbar.add(Ui.button("Copy PGN", false, event -> {
            SavedGame game = selectedGame();
            if (game != null) {
                copyText.accept(game.pgn());
            }
        }));
        toolbar.add(Ui.button("Refresh", false, event -> refresh()));
        return toolbar;
    }

    /**
     * Returns the currently selected saved game.
     *
     * @return selected game, or {@code null}
     */
    private SavedGame selectedGame() {
        return list.getSelectedValue();
    }

    /**
     * Restores list selection after refreshing saved games.
     *
     * @param previous source previous
     */
    private void restoreSelection(SavedGame previous) {
        if (listModel.isEmpty()) {
            return;
        }
        if (previous != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (previous.id().equals(listModel.get(i).id())) {
                    list.setSelectedIndex(i);
                    return;
                }
            }
        }
        list.setSelectedIndex(0);
    }

    /**
     * Refreshes the update details.
     */
    private void updateDetails() {
        SavedGame game = selectedGame();
        if (game == null) {
            detailArea.setText("No saved games yet.");
            return;
        }
        detailArea.setText(game.title() + System.lineSeparator()
                + "Status: " + game.status() + "  |  Ply " + game.currentPly() + " / " + game.plyCount()
                + "  |  Updated " + formatTime(game.updatedAtMillis()) + System.lineSeparator()
                + "SAN: " + game.sanLine() + System.lineSeparator()
                + "UCI: " + game.uciLine() + System.lineSeparator()
                + "FEN: " + game.currentFen());
        detailArea.setCaretPosition(0);
    }

    /**
     * Formats the format time.
     *
     * @param millis duration in milliseconds
     * @return format time text
     */
    private static String formatTime(long millis) {
        if (millis <= 0L) {
            return "unknown";
        }
        return TIME_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    /**
     * Compact renderer for saved-game rows.
     */
    private static final class SavedGameRenderer extends DefaultListCellRenderer {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Renders one saved game.
         */
        @Override
        public Component getListCellRendererComponent(JList<?> source, Object value, int index,
                boolean selected, boolean focused) {
            Component component = super.getListCellRendererComponent(source, value, index, selected, focused);
            if (component instanceof javax.swing.JLabel label && value instanceof SavedGame game) {
                label.setText(game.title() + "   ·   " + game.status() + "   ·   "
                        + "ply " + game.currentPly() + "/" + game.plyCount()
                        + "   ·   " + formatTime(game.updatedAtMillis()));
            }
            return component;
        }
    }
}
