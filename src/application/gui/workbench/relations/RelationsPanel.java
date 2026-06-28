package application.gui.workbench.relations;

import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;
import chess.images.render.RelationPalette;
import chess.nn.otis.Model;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Control rail for the Board / Relations mode. It visualizes deterministic OTIS
 * tactical-incidence channels on the shared board while keeping dense overlays
 * filterable and explainable.
 */
public final class RelationsPanel extends JPanel {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Arrow line width for the relation overlay.
     */
    private static final int ARROW_WIDTH = 6;

    /**
     * Edge count where the inspector starts warning about readability.
     */
    private static final int DENSE_WARNING_THRESHOLD = 80;

    /**
     * Default maximum relation arrows drawn before filters are adjusted.
     */
    private static final int DEFAULT_MAX_ARROWS = 96;

    /**
     * Compact label width for display controls.
     */
    private static final int LABEL_WIDTH = 92;

    /**
     * Channels selected on first open.
     */
    private static final int[] DEFAULT_CHANNELS = {0, 1, 9, 11};

    /**
     * A named, related set of channels shown together in the channel list.
     *
     * @param name group heading
     * @param channels member channel indices
     */
    private record ChannelGroup(String name, int[] channels) {
    }

    /**
     * Channel list grouping: every channel appears in exactly one group so the
     * list reads by purpose (attacks, king safety, rays, pieces) rather than as
     * one long flat column.
     */
    private static final ChannelGroup[] CHANNEL_GROUPS = {
        new ChannelGroup("Attacks & defenses", new int[] {0, 1, 2, 3}),
        new ChannelGroup("King safety", new int[] {4, 5, 11}),
        new ChannelGroup("Rays", new int[] {6, 7, 8}),
        new ChannelGroup("Pieces & pawns", new int[] {9, 10})
    };

    /**
     * User-facing channel labels, index-aligned with {@link Model#RELATION_NAMES}.
     */
    private static final String[] CHANNEL_LABELS = {
            "Our attacks",
            "Opponent attacks",
            "Our defenses",
            "Opponent defenses",
            "Our king-zone pressure",
            "Opponent king-zone pressure",
            "Bishop rays",
            "Rook rays",
            "Queen rays",
            "Knight attacks",
            "Pawn pressure",
            "King ray / pin candidates"
    };

    /**
     * Short channel explanations, index-aligned with {@link Model#RELATION_NAMES}.
     */
    private static final String[] CHANNEL_NOTES = {
            "Friendly pieces attacking opponent pieces.",
            "Opponent pieces attacking friendly pieces.",
            "Friendly pieces defending friendly pieces.",
            "Opponent pieces defending opponent pieces.",
            "Friendly attacks into squares near the opponent king.",
            "Opponent attacks into squares near our king.",
            "Visible bishop-line tactical rays.",
            "Visible rook-line tactical rays.",
            "Visible queen-line tactical rays.",
            "Knight attack incidence.",
            "Forward-oriented pawn attack incidence.",
            "King-aligned ray and pin candidates."
    };

    /**
     * Shared board used for the visual overlay.
     */
    private final transient BoardPanel board;

    /**
     * Supplier of the current analysis position.
     */
    private final transient Supplier<Position> currentPosition;

    /**
     * Callback used to refresh the workspace header after relation state changes.
     */
    private final transient Runnable summaryChanged;

    /**
     * Per-channel visibility toggles.
     */
    private final ToggleBox[] channelToggles = new ToggleBox[Model.RELATION_COUNT];

    /**
     * FEN source field.
     */
    private final JTextField fenField = new JTextField();

    /**
     * Overlay opacity control.
     */
    private final JSlider opacitySlider = new JSlider(10, 100, 72);

    /**
     * Maximum arrows displayed.
     */
    private final JSlider maxArrowsSlider = new JSlider(12, 180, DEFAULT_MAX_ARROWS);

    /**
     * Quick filter: restrict visible edges to the selected square.
     */
    private final ToggleBox selectedOnlyToggle = new ToggleBox("", true);

    /**
     * Quick filter: reduce pawn-only channel noise.
     */
    private final ToggleBox hidePawnNoiseToggle = new ToggleBox("", true);

    /**
     * Quick filter: show king danger channels.
     */
    private final ToggleBox kingDangerOnlyToggle = new ToggleBox("", true);

    /**
     * Status badge for density / readiness.
     */
    private final StatusBadge densityBadge = new StatusBadge();

    /**
     * Edge-count summary.
     */
    private final JLabel countLabel = Ui.caption("No relation overlay yet.");

    /**
     * Selected square details.
     */
    private final JTextArea selectionDetails = new JTextArea();

    /**
     * Current position visualized.
     */
    private transient Position position;

    /**
     * Last full relation edge list.
     */
    private transient List<Model.IncidenceEdge> lastEdges = List.of();

    /**
     * Selected board square, or {@link Field#NO_SQUARE}.
     */
    private int selectedSquare = Field.NO_SQUARE;

    /**
     * Last visible edge count.
     */
    private int visibleEdges;

    /**
     * Last active channel count.
     */
    private int activeChannels;

    /**
     * Whether the latest overlay exceeded the readability threshold.
     */
    private boolean denseWarning;

    /**
     * Creates the relations panel.
     *
     * @param board board to overlay
     * @param currentPosition supplier of the current analysis position
     */
    public RelationsPanel(BoardPanel board, Supplier<Position> currentPosition) {
        this(board, currentPosition, null);
    }

    /**
     * Creates the relations panel.
     *
     * @param board board to overlay
     * @param currentPosition supplier of the current analysis position
     * @param summaryChanged callback for workspace header refreshes
     */
    public RelationsPanel(BoardPanel board, Supplier<Position> currentPosition, Runnable summaryChanged) {
        super(new BorderLayout());
        this.board = board;
        this.currentPosition = currentPosition;
        this.summaryChanged = summaryChanged == null ? () -> {
            // optional callback
        } : summaryChanged;
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setBorder(Theme.pad(Theme.SPACE_MD));
        buildUi();
        syncToBoard();
    }

    /**
     * Installs the board click handler used by Relations mode.
     */
    public void activateBoardInteractions() {
        board.setClickSquareObserver(this::selectSquare);
    }

    /**
     * Clears the board click handler used by Relations mode.
     */
    public void deactivateBoardInteractions() {
        board.setClickSquareObserver(null);
    }

    /**
     * Returns the Board / Relations workspace context string.
     *
     * @return context summary
     */
    public String workspaceContext() {
        if (position == null) {
            return "No position loaded";
        }
        String summary = visibleEdges + " edges visible · " + activeChannels + " channels";
        return denseWarning ? summary + " · readability warning" : "Tactical incidence · " + summary;
    }

    /**
     * Loads the current analysis position onto the board.
     */
    public void syncToBoard() {
        Position current = currentPosition == null ? null : currentPosition.get();
        if (current == null) {
            setStatus("No position loaded", StatusBadge.Variant.NOT_RUN);
            return;
        }
        show(current.copy());
    }

    /**
     * Lays out the inspector.
     */
    private void buildUi() {
        JPanel stack = Ui.transparentPanel(null);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        addSection(stack, Ui.titled("Source", sourceSection()));
        addSection(stack, Ui.titled("Presets", presetsSection()));
        addSection(stack, Ui.titled("Channels", channelsSection()));
        addSection(stack, Ui.titled("Display", displaySection()));
        addSection(stack, Ui.titled("Legend", legendSection()));
        addSection(stack, Ui.titled("Selection Details", selectionSection()));
        addSection(stack, Ui.titled("Advanced / Raw Names", advancedSection()));
        stack.add(Box.createVerticalGlue());
        add(stack, BorderLayout.NORTH);
    }

    /**
     * Adds one inspector section to the stack.
     *
     * @param stack target stack
     * @param section section component
     */
    private static void addSection(JPanel stack, JComponent section) {
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(section);
        stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
    }

    /**
     * Builds the position source controls.
     *
     * @return source section
     */
    private JComponent sourceSection() {
        JPanel panel = verticalPanel();
        JLabel caption = Ui.caption("Use the current board position or inspect a pasted FEN.");
        panel.add(caption);
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        JPanel actions = Ui.buttonRow(FlowLayout.RIGHT,
                Ui.button("Sync to board", true, event -> syncToBoard()),
                Ui.button("Load FEN", false, event -> loadFen(fenField.getText())));
        panel.add(actions);
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        Ui.styleFields(fenField);
        fenField.setToolTipText("Paste a FEN to visualize, then press Load FEN.");
        panel.add(fenField);
        return panel;
    }

    /**
     * Builds relation presets.
     *
     * @return preset section
     */
    private JComponent presetsSection() {
        JPanel panel = verticalPanel();
        panel.add(wrappingButtons(
                presetButton("Attacks", "Attacking channels", new int[] {0, 1, 9, 10}),
                presetButton("Defenses", "Defensive channels", new int[] {2, 3}),
                presetButton("King safety", "King-zone pressure and pins", new int[] {4, 5, 11}),
                presetButton("Pins/rays", "Sliding rays and pin candidates", new int[] {6, 7, 8, 11}),
                Ui.button("Selected piece only", false, event -> applySelectedOnlyPreset()),
                presetButton("All", "Show every relation channel", allChannels()),
                new HoldButton("Clear", () -> selectChannels(new int[0]), true)));
        return panel;
    }

    /**
     * Builds one preset button.
     *
     * @param label display label
     * @param tooltip tooltip text
     * @param channels channels to enable
     * @return preset button
     */
    private JButton presetButton(String label, String tooltip, int[] channels) {
        JButton button = Ui.button(label, false, event -> selectChannels(channels));
        button.setToolTipText(tooltip);
        return button;
    }

    /**
     * Builds the channel toggle list.
     *
     * @return channels section
     */
    private JComponent channelsSection() {
        JPanel list = verticalPanel();
        boolean[] defaultOn = new boolean[Model.RELATION_COUNT];
        for (int channel : DEFAULT_CHANNELS) {
            defaultOn[channel] = true;
        }
        boolean firstGroup = true;
        for (ChannelGroup group : CHANNEL_GROUPS) {
            if (!firstGroup) {
                list.add(Box.createVerticalStrut(Theme.SPACE_MD));
            }
            firstGroup = false;
            JPanel header = Ui.transparentPanel(new BorderLayout());
            header.add(Theme.section(group.name()), BorderLayout.WEST);
            list.add(header);
            list.add(Box.createVerticalStrut(Theme.SPACE_XS));
            int[] channels = group.channels();
            for (int i = 0; i < channels.length; i++) {
                list.add(channelRow(channels[i], defaultOn[channels[i]]));
                if (i + 1 < channels.length) {
                    list.add(Box.createVerticalStrut(Theme.SPACE_XS));
                }
            }
        }
        return list;
    }

    /**
     * Builds one channel row.
     *
     * @param channel channel index
     * @param selected initial selected state
     * @return channel row
     */
    private JComponent channelRow(int channel, boolean selected) {
        JPanel row = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                Theme.lineBorder(Theme.LINE),
                Theme.pad(Theme.SPACE_XS, Theme.SPACE_SM, Theme.SPACE_XS, Theme.SPACE_SM)));
        row.add(Ui.colorChip(RelationPalette.color(channel), 14), BorderLayout.WEST);

        JPanel text = verticalPanel();
        JLabel label = new JLabel(CHANNEL_LABELS[channel]);
        label.setForeground(Theme.TEXT);
        label.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        JLabel raw = Ui.caption(Model.RELATION_NAMES[channel]);
        raw.setToolTipText(CHANNEL_NOTES[channel]);
        text.add(label);
        text.add(raw);
        row.add(text, BorderLayout.CENTER);

        // The switch state already conveys visibility and the row labels the
        // channel, so a per-row "Visible" caption is just repeated noise.
        ToggleBox toggle = new ToggleBox("", true);
        toggle.setSelected(selected);
        toggle.setToolTipText("Show " + CHANNEL_LABELS[channel] + " - " + CHANNEL_NOTES[channel]);
        toggle.addActionListener(event -> refresh());
        channelToggles[channel] = toggle;
        row.add(toggle, BorderLayout.EAST);
        return row;
    }

    /**
     * Builds display and dense-overlay filters.
     *
     * @return display section
     */
    private JComponent displaySection() {
        JPanel panel = verticalPanel();
        configureSlider(opacitySlider, "Arrow opacity");
        configureSlider(maxArrowsSlider, "Maximum arrows drawn");
        opacitySlider.addChangeListener(event -> refresh());
        maxArrowsSlider.addChangeListener(event -> refresh());

        selectedOnlyToggle.setSelected(false);
        selectedOnlyToggle.setToolTipText("Show only edges touching the selected board square.");
        selectedOnlyToggle.addActionListener(event -> refresh());
        hidePawnNoiseToggle.setSelected(false);
        hidePawnNoiseToggle.setToolTipText("Hide the pawn-specific pressure channel to reduce dense overlays.");
        hidePawnNoiseToggle.addActionListener(event -> refresh());
        kingDangerOnlyToggle.setSelected(false);
        kingDangerOnlyToggle.setToolTipText("Show only king-zone and pin-candidate channels.");
        kingDangerOnlyToggle.addActionListener(event -> refresh());

        panel.add(Ui.labelControlRow("Opacity", opacitySlider, LABEL_WIDTH));
        panel.add(Ui.labelControlRow("Max arrows", maxArrowsSlider, LABEL_WIDTH));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(filterToggleRow("Selected piece only", selectedOnlyToggle));
        panel.add(filterToggleRow("Hide pawn noise", hidePawnNoiseToggle));
        panel.add(filterToggleRow("King danger only", kingDangerOnlyToggle));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(densityBadge);
        panel.add(countLabel);
        return panel;
    }

    /**
     * Builds the legend section.
     *
     * @return legend section
     */
    private JComponent legendSection() {
        JPanel panel = verticalPanel();
        panel.add(Ui.legendRow(RelationPalette.color(0), "Channel color",
                "Each color maps to one OTIS relation channel."));
        panel.add(Ui.legendRow(Theme.withAlpha(RelationPalette.color(0), 96), "Opacity",
                "Lower opacity means less visual dominance, not weaker relation data."));
        panel.add(Ui.legendRow(Theme.ACCENT, "Selected square",
                "Clicking a board piece focuses filters and selection details."));
        panel.add(Ui.caption("Arrow direction is source square to target square. Overlap means multiple relation channels share geometry."));
        return panel;
    }

    /**
     * Builds selected-square details.
     *
     * @return selection section
     */
    private JComponent selectionSection() {
        selectionDetails.setEditable(false);
        selectionDetails.setOpaque(false);
        selectionDetails.setLineWrap(true);
        selectionDetails.setWrapStyleWord(true);
        selectionDetails.setForeground(Theme.TEXT);
        selectionDetails.setFont(Theme.mono(Theme.FONT_MONO));
        selectionDetails.setText("Click a board piece to inspect its incident relations.");
        return selectionDetails;
    }

    /**
     * Builds raw channel-name access.
     *
     * @return advanced section
     */
    private JComponent advancedSection() {
        return Ui.commandBlock(rawNamesText());
    }

    /**
     * Loads a pasted FEN onto the board.
     *
     * @param fen FEN text
     */
    private void loadFen(String fen) {
        if (fen == null || fen.isBlank()) {
            return;
        }
        try {
            show(new Position(fen.trim()));
        } catch (RuntimeException ex) {
            setStatus("Invalid FEN", StatusBadge.Variant.ERROR);
        }
    }

    /**
     * Shows a position and redraws the selected channels.
     *
     * @param value position to visualize
     */
    private void show(Position value) {
        position = value;
        selectedSquare = Field.NO_SQUARE;
        board.setPositionInstant(value, Move.NO_MOVE);
        refresh();
    }

    /**
     * Selects a square from the board.
     *
     * @param square selected square
     */
    private void selectSquare(int square) {
        if (square < Field.A8 || square > Field.H1) {
            selectedSquare = Field.NO_SQUARE;
        } else {
            selectedSquare = square;
            selectedOnlyToggle.setSelected(true);
        }
        refresh();
    }

    /**
     * Selects a selected-piece-only preset.
     */
    private void applySelectedOnlyPreset() {
        selectChannels(DEFAULT_CHANNELS);
        selectedOnlyToggle.setSelected(true);
        refresh();
    }

    /**
     * Selects the requested channels.
     *
     * @param channels channels to enable
     */
    private void selectChannels(int[] channels) {
        boolean[] selected = new boolean[Model.RELATION_COUNT];
        for (int channel : channels) {
            if (channel >= 0 && channel < selected.length) {
                selected[channel] = true;
            }
        }
        for (int channel = 0; channel < channelToggles.length; channel++) {
            if (channelToggles[channel] != null) {
                channelToggles[channel].setSelected(selected[channel]);
            }
        }
        refresh();
    }

    /**
     * Redraws the relation arrows for the selected channels.
     */
    private void refresh() {
        board.clearMarkup();
        if (position == null) {
            visibleEdges = 0;
            activeChannels = 0;
            denseWarning = false;
            updateSelectionDetails();
            countLabel.setText("No position loaded.");
            setStatus("No position loaded", StatusBadge.Variant.NOT_RUN);
            return;
        }
        lastEdges = Model.incidenceEdges(position);
        activeChannels = activeChannelCount();
        List<Model.IncidenceEdge> filtered = filteredEdges(lastEdges);
        int limit = maxArrowsSlider.getValue();
        visibleEdges = Math.min(filtered.size(), limit);
        denseWarning = filtered.size() > DENSE_WARNING_THRESHOLD || filtered.size() > limit;

        Color[] colors = channelColors();
        for (int index = 0; index < visibleEdges; index++) {
            Model.IncidenceEdge edge = filtered.get(index);
            board.addArrow((byte) edge.from(), (byte) edge.to(), colors[edge.channel()], ARROW_WIDTH);
        }
        String count = visibleEdges + " of " + filtered.size() + " filtered edges · " + activeChannels
                + " visible channels";
        countLabel.setText(count);
        if (activeChannels == 0) {
            setStatus("No channels visible", StatusBadge.Variant.NOT_RUN);
        } else if (denseWarning) {
            setStatus(visibleEdges + " edges visible. Use filters for readability.", StatusBadge.Variant.WARNING);
        } else {
            setStatus("Overlay readable", StatusBadge.Variant.READY);
        }
        updateSelectionDetails();
        summaryChanged.run();
    }

    /**
     * Builds alpha-adjusted channel colors.
     *
     * @return colors
     */
    private Color[] channelColors() {
        int alpha = Math.max(0, Math.min(255, opacitySlider.getValue() * 255 / 100));
        Color[] colors = new Color[Model.RELATION_COUNT];
        for (int channel = 0; channel < colors.length; channel++) {
            Color base = RelationPalette.color(channel);
            colors[channel] = Theme.withAlpha(base, alpha);
        }
        return colors;
    }

    /**
     * Returns filtered relation edges.
     *
     * @param edges source edges
     * @return filtered edges
     */
    private List<Model.IncidenceEdge> filteredEdges(List<Model.IncidenceEdge> edges) {
        List<Model.IncidenceEdge> result = new ArrayList<>();
        for (Model.IncidenceEdge edge : edges) {
            if (edge.from() == edge.to() || !channelVisible(edge.channel()) || !passesQuickFilters(edge)) {
                continue;
            }
            result.add(edge);
        }
        return result;
    }

    /**
     * Returns whether one edge passes quick filters.
     *
     * @param edge relation edge
     * @return true when visible
     */
    private boolean passesQuickFilters(Model.IncidenceEdge edge) {
        if (kingDangerOnlyToggle.isSelected() && !kingDangerChannel(edge.channel())) {
            return false;
        }
        if (hidePawnNoiseToggle.isSelected() && pawnNoise(edge)) {
            return false;
        }
        return !selectedOnlyToggle.isSelected()
                || selectedSquare == Field.NO_SQUARE
                || edge.from() == selectedSquare
                || edge.to() == selectedSquare;
    }

    /**
     * Returns whether a channel is visible.
     *
     * @param channel channel index
     * @return true when visible
     */
    private boolean channelVisible(int channel) {
        return channel >= 0 && channel < channelToggles.length
                && channelToggles[channel] != null
                && channelToggles[channel].isSelected();
    }

    /**
     * Returns the active channel count.
     *
     * @return active channels
     */
    private int activeChannelCount() {
        int count = 0;
        for (int channel = 0; channel < channelToggles.length; channel++) {
            if (channelVisible(channel)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns whether an edge is pawn-channel noise.
     *
     * @param edge relation edge
     * @return true when the edge should be hidden by the pawn-noise filter
     */
    private boolean pawnNoise(Model.IncidenceEdge edge) {
        if (edge.channel() == 10) {
            return true;
        }
        if (position == null) {
            return false;
        }
        byte[] boardState = position.getBoard();
        return isPawn(boardState[edge.from()]) || isPawn(boardState[edge.to()]);
    }

    /**
     * Returns whether a piece is a pawn.
     *
     * @param piece piece code
     * @return true for pawns
     */
    private static boolean isPawn(byte piece) {
        return Math.abs(piece) == Piece.PAWN;
    }

    /**
     * Returns whether a channel belongs to the king-danger preset.
     *
     * @param channel channel index
     * @return true for king danger channels
     */
    private static boolean kingDangerChannel(int channel) {
        return channel == 4 || channel == 5 || channel == 11;
    }

    /**
     * Updates selected-square details.
     */
    private void updateSelectionDetails() {
        if (position == null) {
            selectionDetails.setText("No position loaded.");
            return;
        }
        if (selectedSquare == Field.NO_SQUARE) {
            selectionDetails.setText("Click a board piece to focus its incident relations.");
            return;
        }
        byte[] boardState = position.getBoard();
        byte piece = boardState[selectedSquare];
        int incident = 0;
        int outgoing = 0;
        int incoming = 0;
        for (Model.IncidenceEdge edge : lastEdges) {
            if (edge.from() == selectedSquare) {
                incident++;
                outgoing++;
            } else if (edge.to() == selectedSquare) {
                incident++;
                incoming++;
            }
        }
        selectionDetails.setText(Field.toString((byte) selectedSquare)
                + " · " + pieceName(piece).toLowerCase(Locale.ROOT)
                + "\n" + incident + " incident edges"
                + "\n" + outgoing + " outgoing · " + incoming + " incoming"
                + "\nSelected-piece filter " + (selectedOnlyToggle.isSelected() ? "on" : "off") + ".");
    }

    /**
     * Sets the density status.
     *
     * @param text text to render or parse
     * @param variant layout or network variant
     */
    private void setStatus(String text, StatusBadge.Variant variant) {
        densityBadge.set(text, variant);
        summaryChanged.run();
    }

    /**
     * Configures a shared slider.
     *
     * @param slider slider component
     * @param tooltip tooltip text
     */
    private static void configureSlider(JSlider slider, String tooltip) {
        Ui.styleSlider(slider);
        slider.setOpaque(false);
        slider.setToolTipText(tooltip);
    }

    /**
     * Creates a transparent vertical panel.
     *
     * @return panel
     */
    private static JPanel verticalPanel() {
        JPanel panel = Ui.transparentPanel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    /**
     * Creates a wrapping button panel.
     *
     * @param buttons button components
     * @return row
     */
    private static JComponent wrappingButtons(JComponent... buttons) {
        JPanel row = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, Theme.SPACE_XS));
        for (JComponent button : buttons) {
            row.add(button);
        }
        return row;
    }

    /**
     * Creates a full-width label/switch row for dense-overlay quick filters.
     *
     * @param label row label
     * @param toggle switch control
     * @return filter row
     */
    private static JComponent filterToggleRow(String label, ToggleBox toggle) {
        JPanel row = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.setBorder(Theme.pad(Theme.SPACE_XS, 0, Theme.SPACE_XS, 0));
        JLabel text = Ui.label(label);
        text.setToolTipText(toggle.getToolTipText());
        toggle.getAccessibleContext().setAccessibleName(label);
        row.add(text, BorderLayout.CENTER);
        row.add(toggle, BorderLayout.EAST);
        return row;
    }

    /**
     * Returns all channel indexes.
     *
     * @return all channels
     */
    private static int[] allChannels() {
        int[] channels = new int[Model.RELATION_COUNT];
        for (int channel = 0; channel < channels.length; channel++) {
            channels[channel] = channel;
        }
        return channels;
    }

    /**
     * Returns raw channel names as a code block.
     *
     * @return raw names
     */
    private static String rawNamesText() {
        StringBuilder builder = new StringBuilder();
        for (int channel = 0; channel < Model.RELATION_NAMES.length; channel++) {
            builder.append(channel)
                    .append(": ")
                    .append(Model.RELATION_NAMES[channel])
                    .append('\n');
        }
        return builder.toString().trim();
    }

    /**
     * Returns a readable piece label.
     *
     * @param piece piece code
     * @return piece label
     */
    private static String pieceName(byte piece) {
        if (piece == Piece.EMPTY) {
            return "Empty square";
        }
        String side = Piece.isWhite(piece) ? "White " : "Black ";
        return side + switch (Math.abs(piece)) {
            case Piece.PAWN -> "pawn";
            case Piece.KNIGHT -> "knight";
            case Piece.BISHOP -> "bishop";
            case Piece.ROOK -> "rook";
            case Piece.QUEEN -> "queen";
            case Piece.KING -> "king";
            default -> "piece";
        };
    }

}
