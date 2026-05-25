package application.gui.workbench.window;

import application.gui.workbench.Defaults;
import application.gui.workbench.board.BoardExportActions;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.game.MovesModel;
import application.gui.workbench.game.SanRenderer;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.EvalBar;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import chess.core.Move;
import chess.core.Position;
import chess.core.Setup;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.iconButton;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.styleSpinners;
import static application.gui.workbench.ui.Ui.titled;
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
     * @param commandBuilder command builder
     * @param commandRunner command runner
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

        JPanel side = transparentPanel(new BorderLayout(8, 8));
        side.setPreferredSize(new Dimension(360, 520));
        side.add(createControls(), BorderLayout.NORTH);
        side.add(titled("Legal Moves", scroll(movesTable)), BorderLayout.CENTER);
        configureMovesTable();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, boardStage, side);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        split.setContinuousLayout(true);
        split.setResizeWeight(0.68);
        split.setDividerSize(8);
        split.setDividerLocation(0.68);
        SplitPaneStyler.style(split);
        return split;
    }

    /**
     * Creates local position and analysis controls.
     *
     * @return control component
     */
    private JComponent createControls() {
        JPanel panel = new SurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        grid(panel, Theme.section("Analysis"), c, 0, 0, 4, 1);
        styleFields(fenField, durationField);
        styleSpinners(multipvSpinner);
        Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
        fenField.addActionListener(event -> loadFen(fenField.getText()));
        grid(panel, fenField, c, 0, 1, 4, 1);
        grid(panel, label("Time"), c, 0, 2, 1, 1);
        grid(panel, durationField, c, 1, 2, 1, 1);
        grid(panel, label("Lines"), c, 2, 2, 1, 1);
        grid(panel, multipvSpinner, c, 3, 2, 1, 1);
        startButton = iconButton("Start", event -> jumpPositionToStart());
        backButton = iconButton("Back", event -> navigatePosition(-1));
        forwardButton = iconButton("Forward", event -> navigatePosition(1));
        endButton = iconButton("End", event -> jumpPositionToEnd());
        WindowBoardLayer.setTransportShortcut(startButton, "Up / Home / Numpad 8 / Alt+Up");
        WindowBoardLayer.setTransportShortcut(backButton, "Left / Numpad 4 / Alt+Left");
        WindowBoardLayer.setTransportShortcut(forwardButton, "Right / Numpad 6 / Alt+Right");
        WindowBoardLayer.setTransportShortcut(endButton, "Down / End / Numpad 2 / Alt+Down");
        grid(panel, buttonRow(FlowLayout.LEFT,
                button("Load", true, event -> loadFen(fenField.getText())),
                startButton,
                backButton,
                forwardButton,
                endButton,
                iconButton("Flip", event -> board.setWhiteDown(!board.isWhiteDown())),
                iconButton("Copy FEN", event -> textCopier.accept(currentFen())),
                iconButton("Export PNG", event -> BoardExportActions.exportPng(this, board)),
                iconButton("Export SVG", event -> BoardExportActions.exportSvg(this, board)),
                button("Analyze", false, event -> runAnalyze()),
                button("Reset", false, event -> loadFen(Setup.getStandardStartFEN()))), c, 0, 3, 4, 1);
        grid(panel, statusLabel, c, 0, 4, 4, 1);
        return panel;
    }

    /**
     * Configures the local legal-move table.
     */
    private void configureMovesTable() {
        movesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Theme.table(movesTable, 27);
        movesTable.setAutoCreateColumnsFromModel(false);
        movesTable.getColumnModel().getColumn(1).setCellRenderer(new SanRenderer());
        movesTable.addMouseListener(new MouseAdapter() {
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
            Theme.foreground(statusLabel, Theme.ForegroundRole.ERROR);
            statusLabel.setText("Invalid FEN");
        }
    }

    /**
     * Applies a local position.
     *
     * @param next position
     * @param move last move
     */
    private void setPosition(Position next, short move, boolean recordHistory) {
        if (recordHistory) {
            recordHistory(next, move);
        }
        position = next;
        fenField.setText(currentFen());
        board.setPosition(position.copy(), move);
        visibleMoves = movesModel.setPosition(position);
        Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
        statusLabel.setText((position.isWhiteToMove() ? "white" : "black")
                + " to move - " + visibleMoves.length + " legal moves");
        updateNavigationButtons();
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
     * @param keyCode key code
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
