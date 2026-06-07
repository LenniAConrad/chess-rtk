package application.gui.workbench.play;

import application.gui.workbench.Defaults;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import chess.core.Setup;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
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
import java.util.Hashtable;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

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
     * Existing Board / Analyze command, exposed as an in-game action.
     */
    private final transient Runnable analyzeCurrentPosition;

    /**
     * Notifies the workbench shell that the Play header context changed.
     */
    private final transient Runnable summaryChanged;

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
    private final JButton newGameButton = Ui.button("Start Game", true, event -> startGame());

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
    private final HoldButton resignButton;

    /**
     * Opens analysis for the current position without altering Play-session state.
     */
    private final JButton analyzeButton = Ui.button("Analyze current", false, null);

    /**
     * Deterministic toggle: when on, the engine always plays its arg-max move.
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
     * Compact state badge for the Current game state card.
     */
    private final StatusBadge gameStatusBadge = new StatusBadge();

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
     * Row wrapping the status badge and label.
     */
    private JComponent statusRow;

    /**
     * Advanced preset note shown while a named one-tap opponent is selected.
     */
    private JComponent presetAdvancedNote;

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
     * Manual search / network / strength controls shown only while the Custom
     * opponent is selected.
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
     * Selected-opponent strength summary.
     */
    private final JLabel opponentStrengthValue = metricValue();

    /**
     * Selected-opponent search summary.
     */
    private final JLabel opponentSearchValue = metricValue();

    /**
     * Selected-opponent evaluation/network summary.
     */
    private final JLabel opponentEvalValue = metricValue();

    /**
     * Selected-opponent deterministic/opening behavior summary.
     */
    private final JLabel opponentModeValue = metricValue();

    /**
     * One-line description for the selected opponent.
     */
    private final JLabel opponentDescriptionLabel = new JLabel();

    /**
     * Current turn value in the state card.
     */
    private final JLabel stateTurnValue = metricValue();

    /**
     * Current material value in the state card.
     */
    private final JLabel stateMaterialValue = metricValue();

    /**
     * Current start-position source value in the state card.
     */
    private final JLabel stateStartValue = metricValue();

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
     * Last engine-thinking flag reported by the session listener.
     */
    private boolean engineThinking;

    /**
     * Last session status line; retained for header and state-card refreshes.
     */
    private String lastStatus = "No game";

    /**
     * Last terminal-result message, if any.
     */
    private String lastResultMessage = "";

    /**
     * Last terminal-result kind, if any.
     */
    private Toast.Kind lastResultKind;

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
        this(session, currentFen, null, null);
    }

    /**
     * Creates the play panel.
     *
     * @param session game session to drive
     * @param currentFen supplier of the current board FEN
     * @param analyzeCurrentPosition existing Analyze command callback
     * @param summaryChanged callback fired when shell header text should refresh
     */
    public PlayPanel(PlaySession session, Supplier<String> currentFen,
            Runnable analyzeCurrentPosition, Runnable summaryChanged) {
        super(new GridBagLayout());
        this.session = session;
        this.currentFen = currentFen;
        this.analyzeCurrentPosition = analyzeCurrentPosition == null ? () -> {
            // optional callback
        } : analyzeCurrentPosition;
        this.summaryChanged = summaryChanged == null ? () -> {
            // optional callback
        } : summaryChanged;
        this.resignButton = new HoldButton("Resign", () -> session.resign(), true);
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
        // hidden otherwise so the setup form stays focused on the selected source.
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
        this.engineThinking = engineThinking;
        this.lastStatus = status == null || status.isBlank() ? "No game" : status;
        statusLabel.setText(status);
        refreshPlayUi();
        applyGameState(gameActive, engineThinking);
        summaryChanged.run();
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
            lastResultMessage = "";
            lastResultKind = null;
            resultBanner.setText("");
            if (resultRow != null) {
                resultRow.setVisible(false);
            }
            refreshPlayUi();
            summaryChanged.run();
            return;
        }
        lastResultMessage = message;
        lastResultKind = kind;
        resultBanner.setText(message);
        resultBanner.setForeground(resultColor(kind));
        if (resultRow != null) {
            resultRow.setVisible(true);
        }
        refreshPlayUi();
        summaryChanged.run();
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
        analyzeButton.setEnabled(currentFen.get() != null && !currentFen.get().isBlank());
        newGameButton.setText(gameActive ? "Start New Game" : "Start Game");
        newGameButton.setToolTipText(gameActive
                ? "Start over with the selected setup"
                : "Start a game with the selected setup");
        if (inGameRow != null) {
            inGameRow.setVisible(gameActive);
        }
        updateGameStatusBadge(gameActive, engineThinking);
    }

    /**
     * Lays out the form rows.
     */
    private void buildUi() {
        setLayout(new BorderLayout());
        removeAll();

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        addStackSection(stack, buildOpponentSection());
        addStackSection(stack, buildGameSetupSection());
        addStackSection(stack, buildGameStateSection());
        addStackSection(stack, buildAdvancedEngineSection());
        stack.add(Box.createVerticalGlue());
        add(stack, BorderLayout.CENTER);

        selectInitialBot();
        refreshPlayUi();
    }

    /**
     * Builds the Opponent inspector section.
     *
     * @return opponent section
     */
    private JComponent buildOpponentSection() {
        JPanel body = verticalPanel();
        body.add(buildBotPicker());
        body.add(Box.createVerticalStrut(Theme.SPACE_MD));
        body.add(buildOpponentSummary());
        return Ui.card("Opponent", body);
    }

    /**
     * Builds the selected-opponent summary below the preset selector.
     *
     * @return summary component
     */
    private JComponent buildOpponentSummary() {
        opponentDescriptionLabel.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        opponentDescriptionLabel.setForeground(Theme.MUTED);
        opponentDescriptionLabel.setBorder(Theme.pad(Theme.SPACE_SM, 0, 0, 0));

        networkNote.setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
        networkNote.setForeground(Theme.STATUS_WARNING_TEXT);
        networkNote.setVisible(false);

        JPanel metrics = metricGrid(2);
        metrics.add(metricTile("Strength", opponentStrengthValue));
        metrics.add(metricTile("Search", opponentSearchValue));
        metrics.add(metricTile("Eval / Network", opponentEvalValue));
        metrics.add(metricTile("Mode", opponentModeValue));

        JPanel summary = verticalPanel();
        summary.add(metrics);
        summary.add(opponentDescriptionLabel);
        summary.add(networkNote);
        return summary;
    }

    /**
     * Builds the Game setup section.
     *
     * @return game setup section
     */
    private JComponent buildGameSetupSection() {
        Ui.styleFields(fenField);
        fenField.setToolTipText("Paste a FEN to start from; used only when From FEN is selected");
        newGameButton.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
        newGameButton.setPreferredSize(new Dimension(0, Theme.CONTROL_HEIGHT_TALL));

        JPanel body = verticalPanel();
        body.add(Ui.labelControlRow("You play", sideChips, LABEL_WIDTH));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(Ui.labelControlRow("Start from", startChips, LABEL_WIDTH));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(fenField);
        body.add(Box.createVerticalStrut(Theme.SPACE_MD));
        body.add(newGameButton);
        return Ui.card("Game Setup", body);
    }

    /**
     * Builds the Current game state section.
     *
     * @return game-state section
     */
    private JComponent buildGameStateSection() {
        statusLabel.setForeground(Theme.TEXT);
        statusLabel.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
        gameStatusBadge.setFixedTextWidth(86);

        JPanel statusLine = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
        statusLine.setOpaque(false);
        statusLine.add(gameStatusBadge, BorderLayout.WEST);
        statusLine.add(statusLabel, BorderLayout.CENTER);
        statusRow = statusLine;

        resultBanner.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
        resultBanner.setForeground(Theme.TEXT);

        JPanel metrics = metricGrid(2);
        metrics.add(metricTile("Turn", stateTurnValue));
        metrics.add(metricTile("Material", stateMaterialValue));
        metrics.add(metricTile("Start", stateStartValue));
        resultRow = metricTile("Result", resultBanner);
        resultRow.setVisible(false);
        metrics.add(resultRow);

        JPanel inGame = new JPanel(new GridLayout(0, 2, Theme.SPACE_SM, Theme.SPACE_SM));
        inGame.setOpaque(false);
        for (JComponent button : List.of(takebackButton, hintButton, analyzeButton, drawButton, resignButton)) {
            button.setPreferredSize(new Dimension(0, Theme.CONTROL_HEIGHT_TALL));
            button.setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
            inGame.add(button);
        }
        inGameRow = inGame;
        inGameRow.setVisible(false);

        JPanel body = verticalPanel();
        body.add(statusLine);
        body.add(Box.createVerticalStrut(Theme.SPACE_MD));
        body.add(metrics);
        body.add(Box.createVerticalStrut(Theme.SPACE_MD));
        body.add(inGame);
        return Ui.card("Current Game State", body);
    }

    /**
     * Builds the Advanced engine settings section.
     *
     * @return advanced engine settings section
     */
    private JComponent buildAdvancedEngineSection() {
        presetAdvancedNote = advancedPresetNote();
        customCard = buildCustomControls();
        customCard.setVisible(false);

        JPanel body = verticalPanel();
        body.add(presetAdvancedNote);
        body.add(customCard);
        return Ui.card("Advanced Engine Settings", body);
    }

    /**
     * Builds the compact note shown for named presets instead of dumping all
     * custom engine controls into the default path.
     *
     * @return preset note
     */
    private JComponent advancedPresetNote() {
        JLabel text = new JLabel("<html>Preset opponents use the stable local Alpha-Beta / Classical path. "
                + "Select Custom to edit search, eval/network, strength, deterministic play, and opening behavior.</html>");
        text.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        text.setForeground(Theme.MUTED);
        text.setBorder(Theme.pad(Theme.SPACE_XS, 0, Theme.SPACE_XS, 0));
        return text;
    }

    /**
     * Adds a section to the vertical inspector stack.
     *
     * @param stack inspector stack
     * @param section section component
     */
    private static void addStackSection(JPanel stack, JComponent section) {
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(section);
        stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
    }

    /**
     * Creates a transparent vertical panel.
     *
     * @return vertical panel
     */
    private static JPanel verticalPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    /**
     * Creates a compact metric grid for inspector cards.
     *
     * @param columns column count
     * @return metric grid
     */
    private static JPanel metricGrid(int columns) {
        JPanel panel = new JPanel(new GridLayout(0, columns, Theme.SPACE_SM, Theme.SPACE_SM));
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Creates one label/value metric tile.
     *
     * @param title metric title
     * @param value value label
     * @return metric tile
     */
    private static JComponent metricTile(String title, JLabel value) {
        JLabel label = new JLabel(title);
        label.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        label.setForeground(Theme.MUTED);
        JPanel tile = new JPanel(new BorderLayout(0, 2));
        tile.setOpaque(true);
        tile.setBackground(Theme.PANEL_SOLID);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                Theme.pad(Theme.SPACE_SM, Theme.SPACE_SM, Theme.SPACE_SM, Theme.SPACE_SM)));
        tile.add(label, BorderLayout.NORTH);
        tile.add(value, BorderLayout.CENTER);
        return tile;
    }

    /**
     * Creates a value label used inside compact metric tiles.
     *
     * @return value label
     */
    private static JLabel metricValue() {
        JLabel label = new JLabel();
        label.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
        label.setForeground(Theme.TEXT);
        return label;
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
            JToggleButton button = new BotCardButton(bot);
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
        customBotButton = new JToggleButton("Custom");
        Theme.commandTab(customBotButton);
        customBotButton.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
        customBotButton.setToolTipText("Set search, eval/network, strength, deterministic play, and opening behavior");
        customBotButton.addActionListener(event -> {
            if (customBotButton.isSelected()) {
                setCustomVisible(true);
                refreshPlayUi();
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
     * Refreshes every UI summary that derives from the selected opponent or
     * current game state.
     */
    private void refreshPlayUi() {
        refreshOpponentSummary();
        refreshGameStateSummary();
        refreshMatchupCard();
        revalidate();
        repaint();
    }

    /**
     * Refreshes the selected-opponent summary values.
     */
    private void refreshOpponentSummary() {
        Bot bot = selectedBot();
        int elo = eloSlider.getValue();
        Opponent.Network network = selectedNetwork();
        opponentStrengthValue.setText(elo + " · " + tier(elo));
        opponentSearchValue.setText(selectedSearch().label());
        opponentEvalValue.setText(network.label() + (Networks.isAvailable(network) ? "" : " missing"));
        opponentModeValue.setText((deterministicToggle.isSelected() ? "Deterministic" : "Sampled")
                + " · " + (openingBookToggle.isSelected() ? "Book on" : "Book off"));
        opponentDescriptionLabel.setText(opponentDescription(bot));
    }

    /**
     * Refreshes the current game-state card.
     */
    private void refreshGameStateSummary() {
        stateTurnValue.setText(sideToMoveLabel() + " to move");
        SideLabels sides = sideLabels();
        stateMaterialValue.setText(materialScore().textFor(sides.playerWhite()));
        stateStartValue.setText(startSourceLabel());
        if (!session.isActive() && lastResultMessage.isBlank()) {
            statusLabel.setText("No game active");
        } else {
            statusLabel.setText(lastStatus);
        }
        updateGameStatusBadge(session.isActive(), engineThinking);
    }

    /**
     * Updates the status badge in the Current game state card.
     *
     * @param gameActive whether a game is active
     * @param thinking whether the bot is searching
     */
    private void updateGameStatusBadge(boolean gameActive, boolean thinking) {
        if (gameActive && thinking) {
            gameStatusBadge.running("bot thinking");
        } else if (gameActive) {
            gameStatusBadge.ready("your move");
        } else if (!lastResultMessage.isBlank()) {
            if (lastResultKind == Toast.Kind.WARNING) {
                gameStatusBadge.warning("game over");
            } else {
                gameStatusBadge.complete("game over");
            }
        } else {
            gameStatusBadge.notRun("not started");
        }
    }

    /**
     * Returns the context line used by the Board / Play workspace header.
     *
     * @return header context
     */
    public String workspaceContext() {
        if (session.isActive() && engineThinking) {
            return "Bot thinking · " + selectedSearch().label() + " / " + selectedNetwork().label();
        }
        if (!lastResultMessage.isBlank() && !session.isActive()) {
            return "Game over · " + compactResult();
        }
        return "You vs " + selectedOpponentTitle() + " · " + sideToMoveLabel() + " to move · "
                + materialScore().textFor(sideLabels().playerWhite());
    }

    /**
     * Returns the selected opponent name plus strength.
     *
     * @return opponent title
     */
    private String selectedOpponentTitle() {
        Bot bot = selectedBot();
        return (bot == null ? "Custom" : bot.name()) + " " + eloSlider.getValue();
    }

    /**
     * Returns a compact result for header display.
     *
     * @return compact result
     */
    private String compactResult() {
        String lower = lastResultMessage.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("draw") || lower.contains("stalemate")) {
            return "Draw";
        }
        boolean playerWhite = sideLabels().playerWhite();
        if (lower.contains("you win")) {
            return (playerWhite ? "White" : "Black") + " won";
        }
        if (lower.contains("engine wins")) {
            return (playerWhite ? "Black" : "White") + " won";
        }
        return lastResultMessage;
    }

    /**
     * Returns a short description for the selected opponent.
     *
     * @param bot selected preset, or null for Custom
     * @return description
     */
    private String opponentDescription(Bot bot) {
        if (bot == null) {
            return "Custom exposes search, eval/network, strength, deterministic play, and opening behavior.";
        }
        if ("Maximum".equals(bot.name())) {
            return "Maximum uses the strongest stable local preset configuration.";
        }
        return bot.name() + " varies strength through the local budget and move-selection model.";
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
     * Returns the side to move from the current board FEN.
     *
     * @return side-to-move label
     */
    private String sideToMoveLabel() {
        try {
            String fen = currentFen.get();
            if (fen == null || fen.isBlank()) {
                return "White";
            }
            String[] parts = fen.trim().split("\\s+");
            return parts.length > 1 && "b".equals(parts[1]) ? "Black" : "White";
        } catch (RuntimeException ex) {
            return "White";
        }
    }

    /**
     * Returns the selected start-source label.
     *
     * @return start-source label
     */
    private String startSourceLabel() {
        return switch (startChips.getSelectedIndex()) {
            case 1 -> "Current board";
            case 2 -> "From FEN";
            default -> "Standard";
        };
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
        eloLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        setFixedControlWidth(eloLabel, 96);
        Ui.styleSlider(eloSlider);
        eloSlider.setOpaque(false);
        eloSlider.setBackground(Theme.BG);
        eloSlider.setMajorTickSpacing(400);
        eloSlider.setMinorTickSpacing(Defaults.PLAY_ELO_STEP);
        eloSlider.setPaintTicks(true);
        eloSlider.setPaintLabels(true);
        eloSlider.setLabelTable(eloStopLabels());

        JPanel strengthRow = new JPanel();
        strengthRow.setOpaque(false);
        strengthRow.setLayout(new BoxLayout(strengthRow, BoxLayout.X_AXIS));
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
        controls.add(Ui.labelControlRow("Eval", networkChips, LABEL_WIDTH), a);
        a.gridy++;
        controls.add(strengthRow, a);
        a.gridy++;
        controls.add(deterministicToggle, a);
        a.gridy++;
        controls.add(openingBookToggle, a);
        return controls;
    }

    /**
     * Builds labeled strength stops for the custom Elo slider.
     *
     * @return slider label table
     */
    private static Hashtable<Integer, JLabel> eloStopLabels() {
        Hashtable<Integer, JLabel> labels = new Hashtable<>();
        for (int elo : new int[] { 800, 1200, 1600, 2000, 2400, 2800 }) {
            JLabel label = new JLabel(Integer.toString(elo));
            label.setFont(Theme.font(10, Font.PLAIN));
            label.setForeground(Theme.MUTED);
            labels.put(Integer.valueOf(elo), label);
        }
        return labels;
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
        }
        if (presetAdvancedNote != null) {
            presetAdvancedNote.setVisible(!visible);
        }
        revalidate();
        repaint();
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
        refreshPlayUi();
        summaryChanged.run();
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
            refreshPlayUi();
            summaryChanged.run();
        });
        searchChips.setOnSelect(index -> {
            PlayPrefs.setSearch(selectedSearch());
            if (!applyingBot) {
                selectCustomBot();
            }
            refreshPlayUi();
            summaryChanged.run();
        });
        networkChips.setOnSelect(index -> {
            PlayPrefs.setNetwork(selectedNetwork());
            updateNetworkNote();
            if (!applyingBot) {
                selectCustomBot();
            }
            refreshPlayUi();
            summaryChanged.run();
        });
        sideChips.setOnSelect(index -> {
            PlayPrefs.setSideIndex(index);
            refreshPlayUi();
            summaryChanged.run();
        });
        startChips.setOnSelect(index -> {
            boolean fromFen = index == 2;
            fenField.setEnabled(fromFen);
            fenField.setVisible(fromFen);
            refreshPlayUi();
            summaryChanged.run();
        });
        deterministicToggle.addActionListener(event -> {
            PlayPrefs.setDeterministic(deterministicToggle.isSelected());
            refreshPlayUi();
            summaryChanged.run();
        });
        openingBookToggle.addActionListener(event -> {
            PlayPrefs.setOpeningBook(openingBookToggle.isSelected());
            session.setBookEnabled(openingBookToggle.isSelected());
            refreshPlayUi();
            summaryChanged.run();
        });
        analyzeButton.addActionListener(event -> analyzeCurrentPosition.run());
        takebackButton.addActionListener(event -> session.takeback());
        hintButton.addActionListener(event -> session.requestHint());
        drawButton.addActionListener(event -> session.offerDraw());
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
        refreshPlayUi();
        summaryChanged.run();
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
     * Selectable two-line card for a named opponent preset.
     */
    private static final class BotCardButton extends JToggleButton {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Preset represented by this card.
         */
        private final Bot bot;

        /**
         * Creates a preset card.
         *
         * @param bot preset opponent
         */
        private BotCardButton(Bot bot) {
            this.bot = bot;
            setText(bot.name() + " " + bot.elo());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(true);
            setBorder(BorderFactory.createEmptyBorder());
            setPreferredSize(new Dimension(160, 70));
            setMinimumSize(new Dimension(136, 70));
            getAccessibleContext().setAccessibleName(bot.name() + " " + bot.elo());
        }

        /**
         * Paints the card.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                boolean selected = isSelected();
                boolean hovered = getModel().isRollover();
                Color fill = selected ? Theme.withAlpha(Theme.ACCENT, 34)
                        : hovered ? Theme.ELEVATED_SOLID : Theme.PANEL_SOLID;
                g.setColor(fill);
                g.fillRoundRect(0, 0, Math.max(0, w - 1), Math.max(0, h - 1), Theme.RADIUS + 3, Theme.RADIUS + 3);
                g.setColor(selected ? Theme.ACCENT : Theme.LINE);
                g.drawRoundRect(0, 0, Math.max(0, w - 1), Math.max(0, h - 1), Theme.RADIUS + 3, Theme.RADIUS + 3);
                if (selected) {
                    g.setColor(Theme.ACCENT);
                    g.fillRoundRect(1, 1, 4, Math.max(0, h - 2), Theme.RADIUS, Theme.RADIUS);
                }
                if (isFocusOwner()) {
                    g.setColor(Theme.FOCUS_RING);
                    g.drawRoundRect(3, 3, Math.max(0, w - 7), Math.max(0, h - 7),
                            Theme.RADIUS + 1, Theme.RADIUS + 1);
                }

                int x = Theme.SPACE_MD;
                int textWidth = Math.max(0, w - x - Theme.SPACE_SM);
                g.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
                g.setColor(Theme.TEXT);
                g.drawString(Ui.elide(bot.name(), g.getFontMetrics(), textWidth), x, 22);
                g.setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
                g.setColor(Theme.ACCENT);
                g.drawString(bot.elo() + " Elo", x, 40);
                g.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
                g.setColor(Theme.MUTED);
                String backend = bot.search().label() + " / " + bot.network().label();
                g.drawString(Ui.elide(backend, g.getFontMetrics(), textWidth), x, 58);
            } finally {
                g.dispose();
            }
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
