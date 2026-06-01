package application.gui.workbench.play;

import application.gui.workbench.Defaults;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import chess.core.Setup;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

/**
 * Setup and control panel for human-versus-engine play.
 *
 * <p>
 * Lets the user pick a side, a target strength on a single Elo slider with
 * named tiers, and a start position (standard, the current board, or a pasted
 * FEN), then drives a {@link PlaySession}. Strength is mapped to a search budget
 * by {@link StrengthModel}; the displayed Elo is an approximate estimate, not a
 * calibrated rating.
 * </p>
 */
public final class PlayPanel extends JPanel {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Game session driven by this panel.
     */
    private final transient PlaySession session;

    /**
     * Supplier of the current board FEN, for the "Current board" start option.
     */
    private final transient Supplier<String> currentFen;

    /**
     * Search-algorithm selector; chip order mirrors {@link Opponent.Search}.
     */
    private final ChipGroup searchChips = new ChipGroup(labels(Opponent.Search.values()));

    /**
     * Evaluation-network selector; chip order mirrors {@link Opponent.Network},
     * so chip indices map directly onto the enum constants.
     */
    private final ChipGroup networkChips = new ChipGroup(labels(Opponent.Network.values()));

    /**
     * Side selector: White / Black / Random.
     */
    private final ChipGroup sideChips = new ChipGroup(List.of("White", "Black", "Random"));

    /**
     * Start-position selector: Standard / Current board / From FEN.
     */
    private final ChipGroup startChips = new ChipGroup(List.of("Standard", "Current", "From FEN"));

    /**
     * Strength slider on the Elo scale.
     */
    private final JSlider eloSlider =
            new JSlider(StrengthModel.MIN_ELO, StrengthModel.MAX_ELO, Defaults.PLAY_ELO);

    /**
     * Strength readout showing Elo and tier.
     */
    private final JLabel eloLabel = new JLabel();

    /**
     * FEN entry used when the "From FEN" start option is selected.
     */
    private final JTextField fenField = new JTextField();

    /**
     * Starts (or restarts) a game with the current settings.
     */
    private final JButton newGameButton = Ui.button("New Game", true, event -> startGame());

    /**
     * Takes back the last move pair.
     */
    private final JButton takebackButton = Ui.button("Takeback", false, null);

    /**
     * Shows the engine's suggested move for the human, without playing it.
     */
    private final JButton hintButton = Ui.button("Hint", false, null);

    /**
     * Offers a draw (the engine accepts in v1).
     */
    private final JButton drawButton = Ui.button("Offer Draw", false, null);

    /**
     * Resigns the current game.
     */
    private final JButton resignButton = Ui.button("Resign", false, null);

    /**
     * Expert toggle: when on, the engine always plays its arg-max move.
     */
    private final ToggleBox deterministicToggle =
            new ToggleBox("Deterministic (no randomness)", false);

    /**
     * Opening-book toggle: when on, the engine answers from the ECO book in the
     * opening (from the standard start) instead of searching.
     */
    private final ToggleBox openingBookToggle =
            new ToggleBox("Opening book", true);

    /**
     * Live status line: whose turn it is, engine-thinking, or the game result.
     */
    private final JLabel statusLabel = new JLabel("No game");

    /**
     * Persistent result banner shown when a game finishes (the toast is
     * transient); hidden while no result is current.
     */
    private final JLabel resultBanner = new JLabel();

    /**
     * Creates the play panel.
     *
     * @param session game session to drive
     * @param currentFen supplier of the current board FEN
     */
    public PlayPanel(PlaySession session, Supplier<String> currentFen) {
        super(new GridBagLayout());
        this.session = session;
        this.currentFen = currentFen;
        setOpaque(true);
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        searchChips.setSelectedIndex(PlayPrefs.search().ordinal());
        networkChips.setSelectedIndex(PlayPrefs.network().ordinal());
        sideChips.setSelectedIndex(PlayPrefs.sideIndex());
        eloSlider.setValue(PlayPrefs.elo());
        deterministicToggle.setSelected(PlayPrefs.deterministic());
        openingBookToggle.setSelected(PlayPrefs.openingBook());
        session.setBookEnabled(PlayPrefs.openingBook());
        buildUi();
        wireControls();
        updateEloLabel();
        fenField.setEnabled(false);
        session.setListener(new PlaySession.Listener() {
            /**
             * Receives play-session status updates.
             */
            @Override
            public void onStatus(String status, boolean gameActive, boolean engineThinking) {
                onSessionStatus(status, gameActive, engineThinking);
            }

            /**
             * Receives play-session result toasts.
             */
            @Override
            public void onResult(String message, application.gui.workbench.ui.Toast.Kind kind) {
                onSessionResult(message, kind);
            }
        });
        applyGameState(session.isActive(), false);
    }

    /**
     * Reflects a session status change in the status line and in-game buttons.
     * Always invoked on the event-dispatch thread by {@link PlaySession}.
     *
     * @param status human-readable status
     * @param gameActive whether a game is in progress
     * @param engineThinking whether the engine is searching
     */
    private void onSessionStatus(String status, boolean gameActive, boolean engineThinking) {
        statusLabel.setText(status);
        applyGameState(gameActive, engineThinking);
    }

    /**
     * Shows a finished game's result in the persistent banner, or clears it when
     * {@code message} is null (a new game starting). Invoked on the EDT.
     *
     * @param message result text, or null to clear
     * @param kind toast kind classifying the result, or null when clearing
     */
    private void onSessionResult(String message, application.gui.workbench.ui.Toast.Kind kind) {
        if (message == null || message.isBlank()) {
            resultBanner.setText("");
            resultBanner.setVisible(false);
            return;
        }
        resultBanner.setText(message);
        resultBanner.setForeground(resultColor(kind));
        resultBanner.setVisible(true);
    }

    /**
     * Maps a result's toast kind to a banner text color.
     *
     * @param kind toast kind, or null
     * @return display color
     */
    private static java.awt.Color resultColor(application.gui.workbench.ui.Toast.Kind kind) {
        if (kind == application.gui.workbench.ui.Toast.Kind.SUCCESS) {
            return Theme.STATUS_SUCCESS_BORDER;
        }
        if (kind == application.gui.workbench.ui.Toast.Kind.WARNING) {
            return Theme.STATUS_WARNING_BORDER;
        }
        return Theme.TEXT;
    }

    /**
     * Enables the in-game controls only while a game is active, and disables
     * take-back while the engine is thinking (it would be a no-op then).
     *
     * @param gameActive whether a game is in progress
     * @param engineThinking whether the engine is searching
     */
    private void applyGameState(boolean gameActive, boolean engineThinking) {
        resignButton.setEnabled(gameActive);
        drawButton.setEnabled(gameActive);
        takebackButton.setEnabled(gameActive && !engineThinking);
        hintButton.setEnabled(gameActive && !engineThinking);
    }

    /**
     * Lays out the form rows.
     */
    private void buildUi() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(4, 0, 4, 0);
        c.gridwidth = 2;

        add(title("Play vs Engine"), c);

        c.gridy++;
        add(labeledRow("Search", searchChips), c);

        c.gridy++;
        add(labeledRow("Network", networkChips), c);

        c.gridy++;
        add(labeledRow("You play", sideChips), c);

        c.gridy++;
        // Group the strength caption, its live Elo readout, and the slider into
        // one unit: "Strength ........ 1800 · Club" directly above the slider,
        // so the readout reads as the slider's value rather than a loose label.
        eloLabel.setForeground(Theme.TEXT);
        eloLabel.setFont(Theme.font(12, Font.BOLD));
        eloLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        Ui.styleSlider(eloSlider);
        eloSlider.setOpaque(false);
        eloSlider.setBackground(Theme.BG);
        eloSlider.setMajorTickSpacing(400);
        eloSlider.setMinorTickSpacing(Defaults.PLAY_ELO_STEP);
        JPanel strengthGroup = new JPanel(new java.awt.BorderLayout(0, 3));
        strengthGroup.setOpaque(false);
        JPanel strengthHeader = new JPanel(new java.awt.BorderLayout(8, 0));
        strengthHeader.setOpaque(false);
        JLabel strengthTitle = new JLabel("Strength");
        strengthTitle.setForeground(Theme.MUTED);
        strengthTitle.setFont(Theme.font(12, Font.PLAIN));
        strengthHeader.add(strengthTitle, java.awt.BorderLayout.WEST);
        strengthHeader.add(eloLabel, java.awt.BorderLayout.CENTER);
        strengthGroup.add(strengthHeader, java.awt.BorderLayout.NORTH);
        strengthGroup.add(eloSlider, java.awt.BorderLayout.CENTER);
        add(strengthGroup, c);

        c.gridy++;
        add(labeledRow("Start from", startChips), c);

        c.gridy++;
        Ui.styleFields(fenField);
        fenField.setToolTipText("Paste a FEN to start from (used when 'From FEN' is selected)");
        add(fenField, c);

        c.gridy++;
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new javax.swing.BoxLayout(buttons, javax.swing.BoxLayout.X_AXIS));
        buttons.add(newGameButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(takebackButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(hintButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(drawButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(resignButton);
        buttons.add(Box.createHorizontalGlue());
        add(buttons, c);

        c.gridy++;
        statusLabel.setForeground(Theme.TEXT);
        statusLabel.setFont(Theme.font(12, Font.BOLD));
        add(labeledRow("Status", statusLabel), c);

        c.gridy++;
        resultBanner.setFont(Theme.font(13, Font.BOLD));
        resultBanner.setVisible(false);
        add(labeledRow("Result", resultBanner), c);

        c.gridy++;
        JPanel expert = new JPanel();
        expert.setOpaque(false);
        expert.setLayout(new javax.swing.BoxLayout(expert, javax.swing.BoxLayout.Y_AXIS));
        deterministicToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        openingBookToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        expert.add(deterministicToggle);
        expert.add(Box.createVerticalStrut(6));
        expert.add(openingBookToggle);
        add(Ui.collapsible("Expert", expert, false), c);

        // Push everything to the top.
        c.gridy++;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        add(Box.createGlue(), c);
    }

    /**
     * Wires control listeners.
     */
    private void wireControls() {
        eloSlider.addChangeListener(event -> {
            updateEloLabel();
            PlayPrefs.setElo(eloSlider.getValue());
        });
        searchChips.setOnSelect(index -> PlayPrefs.setSearch(selectedSearch()));
        networkChips.setOnSelect(index -> PlayPrefs.setNetwork(selectedNetwork()));
        sideChips.setOnSelect(PlayPrefs::setSideIndex);
        startChips.setOnSelect(index -> fenField.setEnabled(index == 2));
        deterministicToggle.addActionListener(event ->
                PlayPrefs.setDeterministic(deterministicToggle.isSelected()));
        openingBookToggle.addActionListener(event -> {
            PlayPrefs.setOpeningBook(openingBookToggle.isSelected());
            session.setBookEnabled(openingBookToggle.isSelected());
        });
        takebackButton.addActionListener(event -> session.takeback());
        hintButton.addActionListener(event -> session.requestHint());
        drawButton.addActionListener(event -> session.offerDraw());
        resignButton.addActionListener(event -> session.resign());
    }

    /**
     * Starts a game with the currently selected settings.
     */
    private void startGame() {
        PlaySession.Side side = switch (sideChips.getSelectedIndex()) {
            case 1 -> PlaySession.Side.BLACK;
            case 2 -> PlaySession.Side.RANDOM;
            default -> PlaySession.Side.WHITE;
        };
        session.start(resolveStartFen(), side,
                StrengthProfile.ofElo(eloSlider.getValue(), deterministicToggle.isSelected()),
                new PlaySession.Config(selectedSearch(), selectedNetwork()));
    }

    /**
     * Returns the currently selected search algorithm.
     *
     * @return search algorithm
     */
    private Opponent.Search selectedSearch() {
        Opponent.Search[] values = Opponent.Search.values();
        return values[Math.max(0, Math.min(values.length - 1, searchChips.getSelectedIndex()))];
    }

    /**
     * Returns the currently selected evaluation network.
     *
     * @return evaluation network
     */
    private Opponent.Network selectedNetwork() {
        Opponent.Network[] values = Opponent.Network.values();
        return values[Math.max(0, Math.min(values.length - 1, networkChips.getSelectedIndex()))];
    }

    /**
     * Returns the chip labels for the search algorithms, in enum order.
     *
     * @param values search constants
     * @return display labels
     */
    private static List<String> labels(Opponent.Search[] values) {
        List<String> out = new java.util.ArrayList<>(values.length);
        for (Opponent.Search value : values) {
            out.add(value.label());
        }
        return out;
    }

    /**
     * Returns the chip labels for the evaluation networks, in enum order.
     *
     * @param values network constants
     * @return display labels
     */
    private static List<String> labels(Opponent.Network[] values) {
        List<String> out = new java.util.ArrayList<>(values.length);
        for (Opponent.Network value : values) {
            out.add(value.label());
        }
        return out;
    }

    /**
     * Resolves the start FEN from the selected start option.
     *
     * @return start FEN
     */
    private String resolveStartFen() {
        return switch (startChips.getSelectedIndex()) {
            case 1 -> currentFen.get();
            case 2 -> fenField.getText();
            default -> Setup.getStandardStartFEN();
        };
    }

    /**
     * Updates the strength readout from the slider value.
     */
    private void updateEloLabel() {
        int elo = eloSlider.getValue();
        eloLabel.setText(elo + "  ·  " + tier(elo));
    }

    /**
     * Returns the named tier for an Elo value.
     *
     * @param elo Elo value
     * @return tier label
     */
    private static String tier(int elo) {
        if (elo < 850) {
            return "Beginner";
        }
        if (elo < 1350) {
            return "Casual";
        }
        if (elo < 1850) {
            return "Club";
        }
        if (elo < 2400) {
            return "Strong";
        }
        return "Max";
    }

    /**
     * Builds the panel title label.
     *
     * @param text title text
     * @return styled title
     */
    private static JComponent title(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.TEXT);
        label.setFont(Theme.font(15, Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return label;
    }

    /**
     * Builds a left-aligned label + control row.
     *
     * @param caption row caption
     * @param control control component
     * @return composed row
     */
    private static JComponent labeledRow(String caption, JComponent control) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new javax.swing.BoxLayout(row, javax.swing.BoxLayout.X_AXIS));
        JLabel label = new JLabel(caption);
        label.setForeground(Theme.MUTED);
        label.setFont(Theme.font(12, Font.PLAIN));
        label.setPreferredSize(new Dimension(78, Theme.CONTROL_HEIGHT));
        label.setAlignmentY(Component.CENTER_ALIGNMENT);
        control.setAlignmentY(Component.CENTER_ALIGNMENT);
        row.add(label);
        row.add(control);
        row.add(Box.createHorizontalGlue());
        return row;
    }
}
