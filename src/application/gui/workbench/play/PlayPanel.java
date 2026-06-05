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
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

/**
 * Setup and control panel for human-versus-engine play.
 *
 * <p>
 * The opponent is chosen either as a one-tap preset bot or via "Custom", which
 * reveals a distinct setup card with the manual search / network / strength
 * controls. The user also picks a side and a start position (standard, the
 * current board, or a pasted FEN), then drives a {@link PlaySession}. Strength is
 * mapped to a search budget by {@link StrengthModel}; the displayed Elo is an
 * approximate estimate, not a calibrated rating. When a chosen network's weights
 * are missing, play falls back to Alpha-Beta + Classical and a note says so.
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
     * Row wrapping the result caption + banner, hidden whole until a game ends
     * so no stranded "Result" label sits in the idle form.
     */
    private JComponent resultRow;

    /**
     * In-game action row (Takeback/Hint/Draw/Resign), hidden until a game is
     * active so the idle form leads with setup + New Game alone.
     */
    private JComponent inGameRow;

    /**
     * Row wrapping the status caption + label, shown only while a game is active.
     */
    private JComponent statusRow;

    /**
     * Selectable preset-bot toggles (one per {@link #BOTS} entry), plus a
     * trailing "Custom" toggle for the advanced manual controls.
     */
    private final List<JToggleButton> botButtons = new ArrayList<>();

    /**
     * Radio group keeping exactly one opponent (a preset or Custom) selected.
     */
    private final ButtonGroup botGroup = new ButtonGroup();

    /**
     * The "Custom" opponent toggle — selected whenever the advanced controls
     * are changed by hand rather than by a preset.
     */
    private JToggleButton customBotButton;

    /**
     * The "Custom setup" card holding the manual search / network / strength
     * controls; shown only while the Custom opponent is selected, so presets stay
     * a one-tap choice and the advanced knobs appear as a distinct panel.
     */
    private JComponent customCard;

    /**
     * Note shown when the selected network's weights are missing, warning that
     * the always-available Classical evaluation will be used instead.
     */
    private final JLabel networkNote = new JLabel();

    /**
     * Guards the advanced-control listeners while a preset applies its values,
     * so applying a preset does not bounce the selection back to Custom.
     */
    private boolean applyingBot;

    /**
     * A prebuilt opponent: a fixed Elo with a predetermined search algorithm
     * and evaluation network.
     *
     * @param name display name
     * @param elo approximate strength
     * @param search search algorithm
     * @param network evaluation network
     */
    private record Bot(String name, int elo, Opponent.Search search, Opponent.Network network) {
    }

    /**
     * Prebuilt opponents, weakest to strongest. Each pins a search algorithm and
     * network so picking one is a single decision; the Advanced section exposes
     * the same controls for fine-tuning.
     */
    private static final List<Bot> BOTS = List.of(
            new Bot("Rookie", 800, Opponent.Search.ALPHA_BETA, Opponent.Network.CLASSICAL),
            new Bot("Casual", 1200, Opponent.Search.ALPHA_BETA, Opponent.Network.CLASSICAL),
            new Bot("Club", 1600, Opponent.Search.ALPHA_BETA, Opponent.Network.NNUE),
            new Bot("Expert", 2000, Opponent.Search.ALPHA_BETA, Opponent.Network.NNUE),
            new Bot("Master", 2400, Opponent.Search.ALPHA_BETA, Opponent.Network.NNUE),
            new Bot("Maximum", 2800, Opponent.Search.MCTS, Opponent.Network.OTIS));

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
        // The FEN field only matters for the "From FEN" start option; keep it
        // hidden otherwise so the setup form stays clean instead of showing an
        // empty disabled box (chess.com/lichess reveal extra inputs on demand).
        fenField.setEnabled(false);
        fenField.setVisible(false);
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
            if (resultRow != null) {
                resultRow.setVisible(false);
            }
            return;
        }
        resultBanner.setText(message);
        resultBanner.setForeground(resultColor(kind));
        if (resultRow != null) {
            resultRow.setVisible(true);
        }
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
        if (inGameRow != null) {
            inGameRow.setVisible(gameActive);
        }
        if (statusRow != null) {
            statusRow.setVisible(gameActive);
        }
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

        // Prebuilt opponents: pick one to set its strength, search, and network
        // in a single tap (chess.com-style). The Advanced section below exposes
        // the same knobs for anyone who wants to build a custom opponent.
        c.gridy++;
        JLabel opponentCaption = new JLabel("Opponent");
        opponentCaption.setForeground(Theme.MUTED);
        opponentCaption.setFont(Theme.font(12, Font.PLAIN));
        add(opponentCaption, c);
        c.gridy++;
        add(buildBotPicker(), c);

        // The manual search/network/strength knobs live in a distinct elevated
        // card directly under the picker, revealed only when "Custom" is chosen,
        // so presets stay one-tap and building your own is a clear, separate mode.
        c.gridy++;
        networkNote.setFont(Theme.font(12, Font.PLAIN));
        networkNote.setForeground(Theme.STATUS_WARNING_BORDER);
        networkNote.setVisible(false);
        add(networkNote, c);

        c.gridy++;
        customCard = Ui.card("Custom setup", buildCustomControls());
        customCard.setVisible(false);
        add(customCard, c);

        c.gridy++;
        add(labeledRow("You play", sideChips), c);

        c.gridy++;
        add(labeledRow("Start from", startChips), c);

        c.gridy++;
        Ui.styleFields(fenField);
        fenField.setToolTipText("Paste a FEN to start from (used when 'From FEN' is selected)");
        add(fenField, c);

        // Prominent primary call-to-action — like chess.com's "Play" or
        // lichess's "Play against computer": full-width, taller, the obvious
        // next step once the setup above is chosen.
        c.gridy++;
        c.insets = new Insets(Theme.SPACE_MD, 0, Theme.SPACE_XS, 0);
        newGameButton.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
        newGameButton.setPreferredSize(new Dimension(0, Theme.CONTROL_HEIGHT_TALL));
        add(newGameButton, c);
        c.insets = new Insets(4, 0, 4, 0);

        // In-game actions and the status line appear only during a game, so the
        // idle form is just the setup rows + New Game (mirrors the resultRow
        // idiom). applyGameState toggles their visibility.
        c.gridy++;
        JPanel inGame = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, Theme.SPACE_SM, 0));
        inGame.setOpaque(false);
        inGame.add(takebackButton);
        inGame.add(hintButton);
        inGame.add(drawButton);
        inGame.add(resignButton);
        inGameRow = inGame;
        inGameRow.setVisible(false);
        add(inGame, c);

        c.gridy++;
        statusLabel.setForeground(Theme.TEXT);
        statusLabel.setFont(Theme.font(12, Font.BOLD));
        statusRow = labeledRow("Status", statusLabel);
        statusRow.setVisible(false);
        add(statusRow, c);

        c.gridy++;
        resultBanner.setFont(Theme.font(13, Font.BOLD));
        resultRow = labeledRow("Result", resultBanner);
        resultRow.setVisible(false);
        add(resultRow, c);

        c.gridy++;
        JPanel expert = new JPanel();
        expert.setOpaque(false);
        expert.setLayout(new javax.swing.BoxLayout(expert, javax.swing.BoxLayout.Y_AXIS));
        deterministicToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        openingBookToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        expert.add(deterministicToggle);
        expert.add(Box.createVerticalStrut(Theme.SPACE_SM));
        expert.add(openingBookToggle);
        add(Ui.collapsible("Expert", expert, false), c);

        // Push everything to the top.
        c.gridy++;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        add(Box.createGlue(), c);

        selectInitialBot();
    }

    /**
     * Builds the prebuilt-opponent picker: a 2-column grid of selectable preset
     * toggles plus a trailing "Custom" toggle.
     *
     * @return bot picker component
     */
    private JComponent buildBotPicker() {
        JPanel grid = new JPanel(new GridLayout(0, 2, Theme.SPACE_SM, Theme.SPACE_SM));
        grid.setOpaque(false);
        for (Bot bot : BOTS) {
            JToggleButton button = new JToggleButton(bot.name() + "  ·  " + bot.elo());
            Theme.commandTab(button);
            button.setToolTipText(bot.name() + " · " + bot.elo() + " Elo · "
                    + bot.search().label() + " / " + bot.network().label());
            button.addActionListener(event -> {
                if (button.isSelected()) {
                    applyBot(bot);
                }
            });
            botGroup.add(button);
            botButtons.add(button);
            grid.add(button);
        }
        // "Custom" gets its own full-width row beneath the even preset grid (no
        // orphaned half-cell), and reveals the Custom-setup card when chosen.
        customBotButton = new JToggleButton("Custom");
        Theme.commandTab(customBotButton);
        customBotButton.setToolTipText("Set the search, network, and strength yourself below");
        customBotButton.addActionListener(event -> {
            if (customBotButton.isSelected()) {
                setCustomVisible(true);
            }
        });
        botGroup.add(customBotButton);

        JPanel picker = new JPanel(new java.awt.BorderLayout(0, Theme.SPACE_SM));
        picker.setOpaque(false);
        picker.add(grid, java.awt.BorderLayout.NORTH);
        picker.add(customBotButton, java.awt.BorderLayout.CENTER);
        return picker;
    }

    /**
     * Builds the Custom-setup controls: the manual search, network, and strength
     * knobs shown inside the Custom card. A preset writes its values into these;
     * editing them by hand switches the opponent to Custom.
     *
     * @return custom controls component
     */
    private JComponent buildCustomControls() {
        eloLabel.setForeground(Theme.TEXT);
        eloLabel.setFont(Theme.font(12, Font.BOLD));
        eloLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        eloLabel.setPreferredSize(new Dimension(96, Theme.CONTROL_HEIGHT));
        eloLabel.setMaximumSize(new Dimension(96, Theme.CONTROL_HEIGHT));
        Ui.styleSlider(eloSlider);
        eloSlider.setOpaque(false);
        eloSlider.setBackground(Theme.BG);
        eloSlider.setMajorTickSpacing(400);
        eloSlider.setMinorTickSpacing(Defaults.PLAY_ELO_STEP);

        // Strength row: caption, the slider (grows to fill), and the live
        // Elo/tier readout — its left edge aligned with the Search/Network chips.
        JPanel strengthRow = new JPanel();
        strengthRow.setOpaque(false);
        strengthRow.setLayout(new javax.swing.BoxLayout(strengthRow, javax.swing.BoxLayout.X_AXIS));
        JLabel strengthTitle = new JLabel("Strength");
        strengthTitle.setForeground(Theme.MUTED);
        strengthTitle.setFont(Theme.font(12, Font.PLAIN));
        strengthTitle.setPreferredSize(new Dimension(78, Theme.CONTROL_HEIGHT));
        strengthTitle.setMaximumSize(new Dimension(78, Theme.CONTROL_HEIGHT));
        strengthTitle.setAlignmentY(Component.CENTER_ALIGNMENT);
        eloSlider.setAlignmentY(Component.CENTER_ALIGNMENT);
        eloLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        strengthRow.add(strengthTitle);
        strengthRow.add(eloSlider);
        strengthRow.add(Box.createHorizontalStrut(Theme.SPACE_SM));
        strengthRow.add(eloLabel);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        GridBagConstraints a = new GridBagConstraints();
        a.gridx = 0;
        a.gridy = 0;
        a.fill = GridBagConstraints.HORIZONTAL;
        a.weightx = 1.0;
        a.anchor = GridBagConstraints.WEST;
        a.insets = new Insets(Theme.SPACE_XS, 0, Theme.SPACE_XS, 0);
        controls.add(labeledRow("Search", searchChips), a);
        a.gridy++;
        controls.add(labeledRow("Network", networkChips), a);
        a.gridy++;
        controls.add(strengthRow, a);
        return controls;
    }

    /**
     * Shows or hides the Custom-setup card, relaying out the form so hidden
     * controls take no space.
     *
     * @param visible whether the Custom controls should be shown
     */
    private void setCustomVisible(boolean visible) {
        if (customCard != null && customCard.isVisible() != visible) {
            customCard.setVisible(visible);
            revalidate();
            repaint();
        }
    }

    /**
     * Updates the missing-weights note for the selected network. When a neural
     * network's weights are absent the game falls back to the always-available
     * Classical evaluation, so the note makes that visible instead of silently
     * degrading. Classical is always available, so the note hides for it.
     */
    private void updateNetworkNote() {
        Opponent.Network network = selectedNetwork();
        if (Networks.isAvailable(network)) {
            networkNote.setVisible(false);
            return;
        }
        networkNote.setText(network.label() + " weights not found — using Classical");
        networkNote.setVisible(true);
        revalidate();
        repaint();
    }

    /**
     * Applies a preset opponent's strength, search, and network to the advanced
     * controls without bouncing the selection back to Custom.
     *
     * @param bot preset opponent
     */
    private void applyBot(Bot bot) {
        applyingBot = true;
        try {
            searchChips.setSelectedIndex(bot.search().ordinal());
            networkChips.setSelectedIndex(bot.network().ordinal());
            eloSlider.setValue(bot.elo());
        } finally {
            applyingBot = false;
        }
        updateEloLabel();
        updateNetworkNote();
        setCustomVisible(false);
        PlayPrefs.setElo(bot.elo());
        PlayPrefs.setSearch(bot.search());
        PlayPrefs.setNetwork(bot.network());
    }

    /**
     * Selects the "Custom" opponent and reveals its setup card, used when an
     * advanced control is edited by hand rather than by a preset.
     */
    private void selectCustomBot() {
        if (customBotButton != null && !customBotButton.isSelected()) {
            customBotButton.setSelected(true);
        }
        setCustomVisible(true);
    }

    /**
     * Highlights the preset matching the saved Elo/search/network on first
     * build, falling back to Custom when none matches exactly.
     */
    private void selectInitialBot() {
        int elo = PlayPrefs.elo();
        Opponent.Search search = PlayPrefs.search();
        Opponent.Network network = PlayPrefs.network();
        for (int i = 0; i < BOTS.size(); i++) {
            Bot bot = BOTS.get(i);
            if (bot.elo() == elo && bot.search() == search && bot.network() == network) {
                botButtons.get(i).setSelected(true);
                setCustomVisible(false);
                updateNetworkNote();
                return;
            }
        }
        if (customBotButton != null) {
            customBotButton.setSelected(true);
        }
        setCustomVisible(true);
        updateNetworkNote();
    }

    /**
     * Wires control listeners.
     */
    private void wireControls() {
        eloSlider.addChangeListener(event -> {
            updateEloLabel();
            PlayPrefs.setElo(eloSlider.getValue());
            if (!applyingBot) {
                selectCustomBot();
            }
        });
        searchChips.setOnSelect(index -> {
            PlayPrefs.setSearch(selectedSearch());
            if (!applyingBot) {
                selectCustomBot();
            }
        });
        networkChips.setOnSelect(index -> {
            PlayPrefs.setNetwork(selectedNetwork());
            updateNetworkNote();
            if (!applyingBot) {
                selectCustomBot();
            }
        });
        sideChips.setOnSelect(PlayPrefs::setSideIndex);
        startChips.setOnSelect(index -> {
            boolean fromFen = index == 2;
            fenField.setEnabled(fromFen);
            fenField.setVisible(fromFen);
            revalidate();
            repaint();
        });
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
        Opponent.Search searchAlgorithm = selectedSearch();
        Opponent.Network network = selectedNetwork();
        // If the chosen network's weights are missing, don't silently degrade
        // (e.g. MCTS+OTIS quietly becoming a weak classical-MCTS bot): fall back
        // to the always-available strong weightless engine, Alpha-Beta + Classical.
        if (!Networks.isAvailable(network)) {
            searchAlgorithm = Opponent.Search.ALPHA_BETA;
            network = Opponent.Network.CLASSICAL;
        }
        session.start(resolveStartFen(), side,
                StrengthProfile.ofElo(eloSlider.getValue(), deterministicToggle.isSelected()),
                new PlaySession.Config(searchAlgorithm, network));
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
        // Route through the shared panel-title helper so every panel heading
        // uses one sentence-case FONT_TITLE treatment; keep a small bottom gap.
        JLabel label = Theme.sectionTitle(text);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.SPACE_XS, 0));
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
