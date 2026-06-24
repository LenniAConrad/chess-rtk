package application.gui.workbench.window;

import application.gui.workbench.Defaults;
import application.gui.workbench.board.BoardExportActions;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.game.MovesModel;
import application.gui.workbench.game.SanRenderer;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.EvalBar;
import application.gui.workbench.ui.FieldValidator;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.Theme;
import chess.core.Move;
import chess.core.Position;
import chess.core.Setup;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.card;
import static application.gui.workbench.ui.Ui.controlRow;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.iconButton;
import static application.gui.workbench.ui.Ui.optionGroup;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.styleSpinners;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Self-contained analysis board used by duplicate Analyze editor tabs.
 */
final class AnalysisWorkspacePanel extends JPanel {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Builds an engine-analysis command for the workspace state.
     */
    interface CommandBuilder {
        /**
         * Builds command arguments.
         *
         * @param fen position FEN
         * @param multipv requested line count
         * @param duration maximum analysis duration
         * @return command argument list
         */
        List<String> build(String fen, String multipv, String duration);
    }

    /**
     * Local board view.
     */
    private final BoardPanel board = new BoardPanel();

    /**
     * Local eval rail.
     */
    private final EvalBar evalBar = new EvalBar();

    /**
     * Local FEN field.
     */
    private final JTextField fenField = new JTextField();

    /**
     * Local status label.
     */
    private final JLabel statusLabel = new JLabel();

    /**
     * Compact validity badge for the Position card.
     */
    private final StatusBadge positionBadge = new StatusBadge();

    /**
     * Compact move-count badge for the Legal Moves card.
     */
    private final StatusBadge legalMovesBadge = new StatusBadge();

    /**
     * Local duration field.
     */
    private final JTextField durationField = new JTextField(Defaults.ANALYSIS_DURATION);

    /**
     * Local multipv model.
     */
    private final SpinnerNumberModel multipvModel = new SpinnerNumberModel(1, 1, 10, 1);

    /**
     * Local multipv spinner.
     */
    private final JSpinner multipvSpinner = new JSpinner(multipvModel);

    /**
     * Local legal-move model.
     */
    private final MovesModel movesModel = new MovesModel();

    /**
     * Local legal-move table.
     */
    private final JTable movesTable = new JTable(movesModel);

    /**
     * Local start-position navigation button.
     */
    private JButton startButton;

    /**
     * Local previous-position navigation button.
     */
    private JButton backButton;

    /**
     * Local next-position navigation button.
     */
    private JButton forwardButton;

    /**
     * Local final-position navigation button.
     */
    private JButton endButton;

    /**
     * Command builder supplied by the owning window.
     */
    private final transient CommandBuilder commandBuilder;

    /**
     * Command runner supplied by the owning window.
     */
    private final transient Consumer<List<String>> commandRunner;

    /**
     * Clipboard bridge supplied by the owning window.
     */
    private final transient Consumer<String> textCopier;

    /**
     * Current local position.
     */
    private Position position;

    /**
     * Legal moves currently shown by the move table.
     */
    private short[] visibleMoves = new short[0];

    /**
     * Local position history for this detached workspace.
     */
    private final List<Position> history = new ArrayList<>();

    /**
     * Last move that produced each history entry.
     */
    private final List<Short> historyMoves = new ArrayList<>();

    /**
     * Current history cursor.
     */
    private int historyIndex = -1;

    /**
     * Creates an independent analysis workspace.
     *
     * @param initialFen starting FEN
     * @param whiteDown true when White is rendered at the bottom
     * @param commandBuilder source command builder
     * @param commandRunner source command runner
     * @param textCopier clipboard bridge
     */
    AnalysisWorkspacePanel(String initialFen, boolean whiteDown, CommandBuilder commandBuilder,
            Consumer<List<String>> commandRunner, Consumer<String> textCopier) {
        super(new BorderLayout(0, 0));
        this.commandBuilder = commandBuilder;
        this.commandRunner = commandRunner;
        this.textCopier = textCopier;
        setOpaque(false);
        board.setWhiteDown(whiteDown);
        board.setMoveHandler(this::playMove);
        board.setEvalBar(evalBar);
        add(createBody(), BorderLayout.CENTER);
        installNavigationBindings();
        loadFen(initialFen == null || initialFen.isBlank() ? Setup.getStandardStartFEN() : initialFen);
    }

    /**
     * Creates the split workspace body.
     *
     * @return body component
     */
    private JComponent createBody() {
        JPanel boardStage = transparentPanel(new BorderLayout());
        boardStage.add(board, BorderLayout.CENTER);

        JPanel side = transparentPanel(new BorderLayout(0, Theme.SPACE_MD));
        side.setPreferredSize(new Dimension(390, 520));
        side.add(createControls(), BorderLayout.NORTH);
        side.add(createLegalMovesCard(), BorderLayout.CENTER);
        configureMovesTable();

        JSplitPane split = SplitPaneStyler.styledHorizontalSplit(boardStage, side, 0.68);
        return split;
    }

    /**
     * Creates local position and analysis controls.
     *
     * @return control component
     */
    private JComponent createControls() {
        styleFields(fenField, durationField);
        fenField.setFont(Theme.mono(Theme.FONT_MONO));
        durationField.setToolTipText("Maximum analysis time, e.g. 1s or 500ms");
        FieldValidator.attach(durationField,
                FieldValidator.numberWithOptionalUnit(true, "ms", "s", "m", "h"));
        styleSpinners(multipvSpinner);
        Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
        statusLabel.setFont(Theme.font(Theme.FONT_METADATA, java.awt.Font.PLAIN));
        statusLabel.setText("No position loaded");
        fenField.addActionListener(event -> loadFen(fenField.getText()));
        startButton = iconButton("Start", event -> jumpPositionToStart());
        backButton = iconButton("Back", event -> navigatePosition(-1));
        forwardButton = iconButton("Forward", event -> navigatePosition(1));
        endButton = iconButton("End", event -> jumpPositionToEnd());
        WindowBoardLayer.setTransportShortcut(startButton, "Up / Home / Numpad 8 / Alt+Up");
        WindowBoardLayer.setTransportShortcut(backButton, "Left / Numpad 4 / Alt+Left");
        WindowBoardLayer.setTransportShortcut(forwardButton, "Right / Numpad 6 / Alt+Right");
        WindowBoardLayer.setTransportShortcut(endButton, "Down / End / Numpad 2 / Alt+Down");
        positionBadge.setFixedTextWidth(72);
        legalMovesBadge.setFixedTextWidth(72);

        JPanel stack = verticalStack();
        stack.add(createPositionCard());
        stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
        stack.add(createEngineCard());
        return stack;
    }

    /**
     * Creates the local position-loading card.
     *
     * @return position card
     */
    private JComponent createPositionCard() {
        JPanel body = verticalStack();
        JLabel fenCaption = caption("FEN");
        body.add(fullWidth(fenCaption));
        body.add(Box.createVerticalStrut(Theme.SPACE_XS));
        body.add(fullWidth(fenField));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(fullWidth(statusLabel));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(fullWidth(controlRow(FlowLayout.LEFT,
                button("Load", true, event -> loadFen(fenField.getText())),
                button("Reset", false, event -> loadFen(Setup.getStandardStartFEN())))));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(transportButtonGroup(startButton, backButton, forwardButton, endButton));
        return card("Position", positionBadge, body);
    }

    /**
     * Creates the local engine-analysis card.
     *
     * @return engine card
     */
    private JComponent createEngineCard() {
        JPanel body = verticalStack();
        body.add(fullWidth(controlRow(FlowLayout.LEFT,
                optionGroup("Time", durationField),
                optionGroup("Lines", multipvSpinner))));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        JButton analyze = button("Analyze", true, event -> runAnalyze());
        analyze.setPreferredSize(new Dimension(112, Theme.CONTROL_HEIGHT));
        body.add(fullWidth(controlRow(FlowLayout.LEFT,
                analyze,
                iconButton("Flip", event -> board.setWhiteDown(!board.isWhiteDown())),
                iconButton("Copy FEN", event -> textCopier.accept(currentFen())),
                iconButton("Export PNG", event -> BoardExportActions.exportPng(this, board)),
                iconButton("Export SVG", event -> BoardExportActions.exportSvg(this, board)))));
        return card("Engine", body);
    }

    /**
     * Creates the legal move list card.
     *
     * @return legal-move card
     */
    private JComponent createLegalMovesCard() {
        JScrollPane moveScroll = scroll(movesTable, () -> Theme.CARD);
        moveScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel body = transparentPanel(new BorderLayout());
        body.add(moveScroll, BorderLayout.CENTER);
        return card("Legal Moves", legalMovesBadge, body);
    }

    /**
     * Configures the local legal-move table.
     */
    private void configureMovesTable() {
        movesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Theme.table(movesTable, Theme.TABLE_ROW_HEIGHT);
        movesTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        movesTable.setAutoCreateColumnsFromModel(false);
        movesTable.getColumnModel().getColumn(1).setCellRenderer(new SanRenderer());
        // Pin the narrow index/SAN/UCI columns so the trailing flags column
        // (usually empty for legal moves) stops floating as a wide dead zone.
        javax.swing.table.TableColumnModel moveColumns = movesTable.getColumnModel();
        moveColumns.getColumn(0).setMaxWidth(46);
        moveColumns.getColumn(0).setPreferredWidth(46);
        moveColumns.getColumn(1).setPreferredWidth(112);
        if (moveColumns.getColumnCount() > 2) {
            moveColumns.getColumn(2).setPreferredWidth(108);
        }
        if (moveColumns.getColumnCount() > 3) {
            moveColumns.getColumn(3).setPreferredWidth(116);
        }
        movesTable.addMouseListener(new MouseAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int row = movesTable.getSelectedRow();
                    if (row >= 0 && row < visibleMoves.length) {
                        playMove(visibleMoves[row]);
                    }
                }
            }
        });
    }

    /**
     * Loads a FEN into this workspace.
     *
     * @param fen FEN text
     */
    private void loadFen(String fen) {
        try {
            setPosition(new Position(fen.trim()), Move.NO_MOVE, true);
        } catch (IllegalArgumentException ex) {
            positionBadge.error("invalid");
            legalMovesBadge.notRun("no moves");
            Theme.foreground(statusLabel, Theme.ForegroundRole.ERROR);
            statusLabel.setText("Invalid FEN");
        }
    }

    /**
     * Applies a local position.
     *
     * @param next position
     * @param move last move
     * @param recordHistory whether to append the position to local history
     */
    private void setPosition(Position next, short move, boolean recordHistory) {
        if (recordHistory) {
            recordHistory(next, move);
        }
        position = next;
        fenField.setText(currentFen());
        board.setPosition(position.copy(), move);
        visibleMoves = movesModel.setPosition(position);
        positionBadge.ready("loaded");
        legalMovesBadge.ready(visibleMoves.length + " moves");
        Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
        statusLabel.setText((position.isWhiteToMove() ? "White" : "Black")
                + " to move - " + visibleMoves.length + " legal moves");
        updateNavigationButtons();
    }

    /**
     * Creates a transparent vertical stack.
     *
     * @return vertical stack
     */
    private static JPanel verticalStack() {
        JPanel panel = transparentPanel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    /**
     * Creates one muted caption.
     *
     * @param text caption text
     * @return caption label
     */
    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Theme.FONT_METADATA, java.awt.Font.BOLD));
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Lets a BoxLayout child use the whole card width while preserving its
     * preferred height.
     *
     * @param component child component
     * @param <T> child component type
     * @return the same component
     */
    private static <T extends JComponent> T fullWidth(T component) {
        Dimension preferred = component.getPreferredSize();
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        return component;
    }

    /**
     * Packs navigation buttons into one segmented transport control.
     *
     * @param buttons buttons in display order
     * @return transport control
     */
    private static JComponent transportButtonGroup(JButton... buttons) {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        group.setOpaque(true);
        group.setBackground(Theme.ELEVATED_SOLID);
        group.setBorder(Theme.lineBorder(Theme.LINE, 1));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JButton button : buttons) {
            group.add(button);
        }
        group.setMaximumSize(group.getPreferredSize());
        return group;
    }

    /**
     * Plays one local legal move.
     *
     * @param move CRTK move
     */
    private void playMove(short move) {
        if (position == null || move == Move.NO_MOVE) {
            return;
        }
        Position next = position.copy();
        next.play(move);
        setPosition(next, move, true);
    }

    /**
     * Moves the local history cursor by one or more plies.
     *
     * @param delta signed ply delta
     * @return true when the visible position changed
     */
    private boolean navigatePosition(int delta) {
        int nextIndex = historyIndex + delta;
        if (nextIndex < 0 || nextIndex >= history.size()) {
            return false;
        }
        historyIndex = nextIndex;
        restoreHistoryPosition();
        return true;
    }

    /**
     * Jumps to the first local history entry.
     */
    private void jumpPositionToStart() {
        if (!history.isEmpty()) {
            historyIndex = 0;
            restoreHistoryPosition();
        }
    }

    /**
     * Jumps to the final local history entry.
     */
    private void jumpPositionToEnd() {
        if (!history.isEmpty()) {
            historyIndex = history.size() - 1;
            restoreHistoryPosition();
        }
    }

    /**
     * Records a new local history entry, dropping stale forward entries.
     *
     * @param next position to record
     * @param move move that produced the position
     */
    private void recordHistory(Position next, short move) {
        while (history.size() > historyIndex + 1) {
            history.remove(history.size() - 1);
            historyMoves.remove(historyMoves.size() - 1);
        }
        history.add(next.copy());
        historyMoves.add(Short.valueOf(move));
        historyIndex = history.size() - 1;
    }

    /**
     * Restores the current local history entry.
     */
    private void restoreHistoryPosition() {
        if (historyIndex < 0 || historyIndex >= history.size()) {
            return;
        }
        Position restored = history.get(historyIndex).copy();
        short move = historyMoves.get(historyIndex).shortValue();
        setPosition(restored, move, false);
    }

    /**
     * Updates local transport button enabled states.
     */
    private void updateNavigationButtons() {
        boolean canBack = historyIndex > 0;
        boolean canForward = historyIndex >= 0 && historyIndex < history.size() - 1;
        if (startButton != null) {
            startButton.setEnabled(canBack);
        }
        if (backButton != null) {
            backButton.setEnabled(canBack);
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(canForward);
        }
        if (endButton != null) {
            endButton.setEnabled(canForward);
        }
    }

    /**
     * Installs local board-navigation shortcuts for duplicate analysis tabs.
     */
    private void installNavigationBindings() {
        bindNavigation(KeyEvent.VK_LEFT, "back", () -> navigatePosition(-1));
        bindNavigation(KeyEvent.VK_KP_LEFT, "backKeypad", () -> navigatePosition(-1));
        bindNavigation(KeyEvent.VK_NUMPAD4, "backNumpad", () -> navigatePosition(-1));
        bindNavigation(KeyEvent.VK_RIGHT, "forward", () -> navigatePosition(1));
        bindNavigation(KeyEvent.VK_KP_RIGHT, "forwardKeypad", () -> navigatePosition(1));
        bindNavigation(KeyEvent.VK_NUMPAD6, "forwardNumpad", () -> navigatePosition(1));
        bindNavigation(KeyEvent.VK_UP, "start", this::jumpPositionToStart);
        bindNavigation(KeyEvent.VK_KP_UP, "startKeypad", this::jumpPositionToStart);
        bindNavigation(KeyEvent.VK_NUMPAD8, "startNumpad", this::jumpPositionToStart);
        bindNavigation(KeyEvent.VK_HOME, "startHome", this::jumpPositionToStart);
        bindNavigation(KeyEvent.VK_DOWN, "end", this::jumpPositionToEnd);
        bindNavigation(KeyEvent.VK_KP_DOWN, "endKeypad", this::jumpPositionToEnd);
        bindNavigation(KeyEvent.VK_NUMPAD2, "endNumpad", this::jumpPositionToEnd);
        bindNavigation(KeyEvent.VK_END, "endKey", this::jumpPositionToEnd);
    }

    /**
     * Binds one local navigation key.
     *
     * @param keyCode source key code
     * @param name action name suffix
     * @param action action body
     */
    private void bindNavigation(int keyCode, String name, Runnable action) {
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(keyCode, 0), "analysis-" + name);
        getActionMap().put("analysis-" + name, new AbstractAction() {
            /**
             * Serialization identifier for Swing action compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Runs the local navigation action.
             *
             * @param event key action event
             */
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    /**
     * Returns the workspace FEN.
     *
     * @return FEN text
     */
    private String currentFen() {
        return position == null ? fenField.getText().trim() : position.toString();
    }

    /**
     * Runs engine analysis for the workspace FEN.
     */
    private void runAnalyze() {
        commandRunner.accept(commandBuilder.build(currentFen(),
                String.valueOf(multipvModel.getNumber().intValue()), durationField.getText().trim()));
    }
}
