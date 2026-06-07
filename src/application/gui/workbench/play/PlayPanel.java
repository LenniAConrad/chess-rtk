package application.gui.workbench.play;

import application.gui.workbench.Defaults;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import chess.core.Setup;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.Icon;
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
     * Fixed width for compact Play rail control labels.
     */
    private static final int LABEL_WIDTH = 78;

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
     * Bot portrait in the matchup card.
     */
    private final JLabel opponentAvatar = new JLabel();

    /**
     * Bot name in the matchup card.
     */
    private final JLabel opponentNameLabel = new JLabel();

    /**
     * Bot side, Elo, and backend summary.
     */
    private final JLabel opponentMetaLabel = new JLabel();

    /**
     * Bot material line.
     */
    private final JLabel opponentMaterialLabel = new JLabel();

    /**
     * Human portrait in the matchup card.
     */
    private final JLabel playerAvatar = new JLabel(new AvatarIcon(0x5B8DEF, "Y"));

    /**
     * Human name in the matchup card.
     */
    private final JLabel playerNameLabel = new JLabel("You");

    /**
     * Human side summary.
     */
    private final JLabel playerMetaLabel = new JLabel();

    /**
     * Human material line.
     */
    private final JLabel playerMaterialLabel = new JLabel();

    /**
     * Opponent identity strip hosted above the shared Play board.
     */
    private JComponent opponentStrip;

    /**
     * Player identity strip hosted below the shared Play board.
     */
    private JComponent playerStrip;

    /**
     * Guards the advanced-control listeners while a preset applies its values,
     * so applying a preset does not bounce the selection back to Custom.
     */
    private boolean applyingBot;

    /**
     * A prebuilt opponent: a fixed Elo shown as a one-tap bot. Presets all use
     * the same always-available backend so difficulty changes do not silently
     * swap engine families or depend on optional model files.
     *
     * @param name display name
     * @param elo approximate strength
     * @param search search algorithm
     * @param network evaluation network
     */
    private record Bot(String name, int elo, int colorRgb, Opponent.Search search, Opponent.Network network) {
    }

    /**
     * Search backend used by every named Play preset.
     */
    private static final Opponent.Search PRESET_SEARCH = Opponent.Search.ALPHA_BETA;

    /**
     * Evaluation backend used by every named Play preset.
     */
    private static final Opponent.Network PRESET_NETWORK = Opponent.Network.CLASSICAL;

    /**
     * Prebuilt opponents, weakest to strongest. Each one uses alpha-beta with
     * classical evaluation; strength is varied through Elo-derived budget and
     * sampling only. The Custom section still exposes the other search/network
     * backends for engine experiments.
     */
    private static final List<Bot> BOTS = List.of(
            new Bot("Rookie", 800, 0x7A6FF0, PRESET_SEARCH, PRESET_NETWORK),
            new Bot("Casual", 1200, 0x2BA7A2, PRESET_SEARCH, PRESET_NETWORK),
            new Bot("Club", 1600, 0x4F8DD3, PRESET_SEARCH, PRESET_NETWORK),
            new Bot("Expert", 2000, 0xC07A2D, PRESET_SEARCH, PRESET_NETWORK),
            new Bot("Master", 2400, 0xB64E62, PRESET_SEARCH, PRESET_NETWORK),
            new Bot("Maximum", 2800, 0x5E64C8, PRESET_SEARCH, PRESET_NETWORK));

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
        refreshMatchupCard();
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
            refreshMatchupCard();
            return;
        }
        resultBanner.setText(message);
        resultBanner.setForeground(resultColor(kind));
        if (resultRow != null) {
            resultRow.setVisible(true);
        }
        refreshMatchupCard();
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
        add(Ui.labelControlRow("You play", sideChips, LABEL_WIDTH), c);

        c.gridy++;
        add(Ui.labelControlRow("Start from", startChips, LABEL_WIDTH), c);

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
        JPanel inGame = new JPanel(new GridLayout(2, 2, Theme.SPACE_SM, Theme.SPACE_SM));
        inGame.setOpaque(false);
        for (JButton button : List.of(takebackButton, hintButton, drawButton, resignButton)) {
            button.setPreferredSize(new Dimension(0, Theme.CONTROL_HEIGHT_TALL));
            button.setFont(Theme.font(12, Font.BOLD));
            inGame.add(button);
        }
        inGameRow = inGame;
        inGameRow.setVisible(false);
        add(inGame, c);

        c.gridy++;
        statusLabel.setForeground(Theme.TEXT);
        statusLabel.setFont(Theme.font(12, Font.BOLD));
        statusRow = Ui.labelControlRow("Status", statusLabel, LABEL_WIDTH);
        statusRow.setVisible(false);
        add(statusRow, c);

        c.gridy++;
        resultBanner.setFont(Theme.font(13, Font.BOLD));
        resultRow = Ui.labelControlRow("Result", resultBanner, LABEL_WIDTH);
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
        refreshMatchupCard();
    }

    /**
     * Returns the opponent identity strip hosted above the Play board.
     *
     * @return opponent strip
     */
    public JComponent opponentIdentityStrip() {
        ensureIdentityStrips();
        return opponentStrip;
    }

    /**
     * Returns the player identity strip hosted below the Play board.
     *
     * @return player strip
     */
    public JComponent playerIdentityStrip() {
        ensureIdentityStrips();
        return playerStrip;
    }

    /**
     * Lazily creates the board-adjacent identity strips.
     */
    private void ensureIdentityStrips() {
        if (opponentStrip == null) {
            opponentStrip = playerStrip(opponentAvatar, opponentNameLabel, opponentMetaLabel, opponentMaterialLabel);
            playerStrip = playerStrip(playerAvatar, playerNameLabel, playerMetaLabel, playerMaterialLabel);
            refreshMatchupCard();
        }
    }

    /**
     * Builds one player strip for the board stage.
     *
     * @param avatar avatar label
     * @param name name label
     * @param meta side/backend label
     * @param material material label
     * @return strip component
     */
    private static JComponent playerStrip(JLabel avatar, JLabel name, JLabel meta, JLabel material) {
        avatar.setPreferredSize(new Dimension(40, 40));
        avatar.setMinimumSize(new Dimension(40, 40));
        name.setFont(Theme.font(13, Font.BOLD));
        name.setForeground(Theme.TEXT);
        meta.setFont(Theme.font(12, Font.PLAIN));
        meta.setForeground(Theme.MUTED);
        material.setFont(Theme.font(12, Font.BOLD));
        material.setForeground(Theme.TEXT);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(name);
        text.add(Box.createVerticalStrut(2));
        text.add(meta);

        JPanel row = new JPanel(new java.awt.BorderLayout(Theme.SPACE_SM, 0));
        row.setOpaque(true);
        row.setBackground(Theme.PANEL_SOLID);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, Theme.LINE),
                BorderFactory.createEmptyBorder(Theme.SPACE_SM, Theme.SPACE_SM,
                        Theme.SPACE_SM, Theme.SPACE_SM)));
        row.add(avatar, java.awt.BorderLayout.WEST);
        row.add(text, java.awt.BorderLayout.CENTER);
        row.add(material, java.awt.BorderLayout.EAST);
        return row;
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
     * Refreshes the matchup card from the selected bot, side, and board.
     */
    private void refreshMatchupCard() {
        Bot bot = selectedBot();
        int elo = eloSlider.getValue();
        int avatarColor = bot == null ? 0x626D7A : bot.colorRgb();
        String opponentName = bot == null ? "Custom Engine" : bot.name();
        opponentAvatar.setIcon(new AvatarIcon(avatarColor, initials(opponentName)));
        opponentNameLabel.setText(opponentName + " (" + elo + ")");

        SideLabels sides = sideLabels();
        opponentMetaLabel.setText(sides.opponentSide() + " - " + selectedSearch().label()
                + " / " + selectedNetwork().label());
        playerMetaLabel.setText(sides.playerSide() + " - Human");

        MaterialScore material = materialScore();
        opponentMaterialLabel.setText(material.textFor(sides.opponentWhite()));
        playerMaterialLabel.setText(material.textFor(sides.playerWhite()));
    }

    /**
     * Returns the currently selected preset bot, or null for Custom.
     *
     * @return selected preset bot
     */
    private Bot selectedBot() {
        for (int i = 0; i < botButtons.size(); i++) {
            if (botButtons.get(i).isSelected()) {
                return BOTS.get(i);
            }
        }
        return null;
    }

    /**
     * Returns player/opponent side labels, using the resolved side once a game
     * starts so Random becomes concrete.
     *
     * @return side labels
     */
    private SideLabels sideLabels() {
        if (!session.isActive() && sideChips.getSelectedIndex() == 2) {
            return new SideLabels("Random side", "Opposite side", true, false);
        }
        boolean playerWhite = session.isActive() ? session.isHumanWhite() : sideChips.getSelectedIndex() != 1;
        return new SideLabels(playerWhite ? "White pieces" : "Black pieces",
                playerWhite ? "Black pieces" : "White pieces", playerWhite, !playerWhite);
    }

    /**
     * Computes a simple material balance from the current FEN.
     *
     * @return material score
     */
    private MaterialScore materialScore() {
        try {
            String fen = currentFen.get();
            if (fen == null || fen.isBlank()) {
                return MaterialScore.EVEN;
            }
            String board = fen.trim().split("\\s+")[0];
            int white = 0;
            int black = 0;
            for (int i = 0; i < board.length(); i++) {
                char piece = board.charAt(i);
                int value = pieceValue(piece);
                if (value == 0) {
                    continue;
                }
                if (Character.isUpperCase(piece)) {
                    white += value;
                } else {
                    black += value;
                }
            }
            return new MaterialScore(white, black);
        } catch (RuntimeException ex) {
            return MaterialScore.EVEN;
        }
    }

    /**
     * Returns a standard material value for a FEN piece.
     *
     * @param piece FEN piece character
     * @return material value
     */
    private static int pieceValue(char piece) {
        return switch (Character.toLowerCase(piece)) {
            case 'p' -> 1;
            case 'n', 'b' -> 3;
            case 'r' -> 5;
            case 'q' -> 9;
            default -> 0;
        };
    }

    /**
     * Returns compact initials for an avatar.
     *
     * @param name display name
     * @return one-character initials
     */
    private static String initials(String name) {
        return name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
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
        setFixedControlWidth(eloLabel, 96);
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
        setFixedControlWidth(strengthTitle, LABEL_WIDTH);
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
        controls.add(Ui.labelControlRow("Search", searchChips, LABEL_WIDTH), a);
        a.gridy++;
        controls.add(Ui.labelControlRow("Network", networkChips, LABEL_WIDTH), a);
        a.gridy++;
        controls.add(strengthRow, a);
        return controls;
    }

    /**
     * Applies the compact fixed-width footprint used by controls inside the Play
     * setup rail.
     *
     * @param component component to size
     * @param width fixed width in pixels
     */
    private static void setFixedControlWidth(JComponent component, int width) {
        Dimension size = new Dimension(width, Theme.CONTROL_HEIGHT);
        component.setPreferredSize(size);
        component.setMaximumSize(size);
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
        refreshMatchupCard();
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
        for (int i = 0; i < BOTS.size(); i++) {
            Bot bot = BOTS.get(i);
            if (bot.elo() == elo && isLegacyPresetBackend(bot, search, network)) {
                botButtons.get(i).setSelected(true);
                applyBot(bot);
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
     * Recognizes backend pairs used by older named presets, so a saved preset
     * selection migrates to the same named bot after all presets moved to
     * alpha-beta + classical. Arbitrary Custom settings still remain Custom.
     *
     * @param bot preset with the saved Elo
     * @param search saved search backend
     * @param network saved evaluation backend
     * @return true when the saved backend was an older version of this preset
     */
    private static boolean isLegacyPresetBackend(Bot bot, Opponent.Search search, Opponent.Network network) {
        return switch (bot.name()) {
            case "Club", "Expert", "Master" ->
                    search == Opponent.Search.ALPHA_BETA && network == Opponent.Network.NNUE;
            case "Maximum" ->
                    search == Opponent.Search.MCTS && network == Opponent.Network.OTIS;
            default -> false;
        };
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
            refreshMatchupCard();
        });
        searchChips.setOnSelect(index -> {
            PlayPrefs.setSearch(selectedSearch());
            if (!applyingBot) {
                selectCustomBot();
            }
            refreshMatchupCard();
        });
        networkChips.setOnSelect(index -> {
            PlayPrefs.setNetwork(selectedNetwork());
            updateNetworkNote();
            if (!applyingBot) {
                selectCustomBot();
            }
            refreshMatchupCard();
        });
        sideChips.setOnSelect(index -> {
            PlayPrefs.setSideIndex(index);
            refreshMatchupCard();
        });
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
        refreshMatchupCard();
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
     * Side labels for the two matchup-card rows.
     *
     * @param playerSide player side text
     * @param opponentSide opponent side text
     * @param playerWhite whether the player owns White
     * @param opponentWhite whether the opponent owns White
     */
    private record SideLabels(String playerSide, String opponentSide, boolean playerWhite, boolean opponentWhite) {
    }

    /**
     * Material totals by side.
     *
     * @param white white material value
     * @param black black material value
     */
    private record MaterialScore(int white, int black) {

        /**
         * Equal material fallback.
         */
        private static final MaterialScore EVEN = new MaterialScore(39, 39);

        /**
         * Formats material from one player's perspective.
         *
         * @param whiteSide whether the player owns White
         * @return material label
         */
        private String textFor(boolean whiteSide) {
            int delta = whiteSide ? white - black : black - white;
            if (delta == 0) {
                return "Material even";
            }
            return "Material " + (delta > 0 ? "+" : "") + delta;
        }
    }

    /**
     * Small generated portrait for bot/player rows.
     */
    private static final class AvatarIcon implements Icon {

        /**
         * Icon size in pixels.
         */
        private static final int SIZE = 40;

        /**
         * Base portrait color.
         */
        private final int rgb;

        /**
         * One-letter mark.
         */
        private final String mark;

        /**
         * Creates an avatar icon.
         *
         * @param rgb base RGB color
         * @param mark one-letter mark
         */
        private AvatarIcon(int rgb, String mark) {
            this.rgb = rgb;
            this.mark = mark == null || mark.isBlank() ? "?" : mark.substring(0, 1);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getIconWidth() {
            return SIZE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getIconHeight() {
            return SIZE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = new Color(rgb);
                g.setColor(base);
                g.fillRoundRect(x, y, SIZE, SIZE, 10, 10);
                g.setColor(Theme.withAlpha(Color.WHITE, 220));
                g.fillRoundRect(x + 9, y + 14, 22, 18, 7, 7);
                g.setColor(Theme.withAlpha(Color.WHITE, 180));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x + 20, y + 12, x + 20, y + 7);
                g.fillOval(x + 17, y + 5, 6, 6);
                g.setColor(base.darker());
                g.fillOval(x + 15, y + 20, 4, 4);
                g.fillOval(x + 24, y + 20, 4, 4);
                g.drawLine(x + 16, y + 27, x + 27, y + 27);
                g.setFont(Theme.font(10, Font.BOLD));
                g.setColor(Theme.withAlpha(Color.WHITE, 235));
                g.drawString(mark, x + 5, y + 12);
            } finally {
                g.dispose();
            }
        }
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

}
