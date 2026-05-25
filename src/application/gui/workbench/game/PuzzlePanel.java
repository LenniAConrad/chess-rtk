package application.gui.workbench.game;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.board.DropContext;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WrappingFlowLayout;
import chess.core.Move;
import chess.core.Position;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.collapsible;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.labeledControl;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleCombo;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Native Swing chess puzzle trainer with PGN variation support.
 */
public final class PuzzlePanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Built-in sample puzzle matching the chess-web regression fixture.
     */
    private static final String SAMPLE_PGN = """
            [Event "Variation Tactics"]
            [SetUp "1"]
            [FEN "6n1/1P2k2r/3r1b2/R2p1b1p/pp2NP2/1n6/7R/7K w - - 4 63"]

            63. Nxd6 Be4+ (Kxd6 64. b8=Q+) 64. Nxe4 Nxa5 65. b8=Q *
            """;

    /**
     * Default file used for the built-in difficult puzzle collection.
     */
    private static final File DEFAULT_PUZZLE_FILE = PuzzleLibrary.DEFAULT_PATH.toFile();

    /**
     * Width reserved for the puzzle branch-mode selector.
     */
    private static final int MODE_COMBO_WIDTH = 176;

    /**
     * Delay between the user's visible move and the automatic opponent reply.
     */
    private static final int OPPONENT_REPLY_DELAY_MS = 130;

    /**
     * Main puzzle board.
     */
    private final BoardPanel board = new BoardPanel();

    /**
     * PGN input area.
     */
    private final JTextArea pgnInput = new JTextArea(SAMPLE_PGN);

    /**
     * Hidden/collapsible solution preview.
     */
    private final JTextArea solutionArea = new JTextArea();

    /**
     * Puzzle title label.
     */
    private final JLabel titleLabel = new JLabel("Puzzles");

    /**
     * Current status label.
     */
    private final JLabel statusLabel = new JLabel("Load a PGN puzzle.");

    /**
     * Branch progress label.
     */
    private final JLabel progressLabel = new JLabel("0 / 0");

    /**
     * Counter label for attempts, hints, and reveals.
     */
    private final JLabel countersLabel = new JLabel("");

    /**
     * Current puzzle-library status label.
     */
    private final JLabel libraryLabel = new JLabel("");

    /**
     * Current side-to-solve label.
     */
    private final JLabel sideLabel = new JLabel("");

    /**
     * Branch traversal mode selector.
     */
    private final JComboBox<String> modeCombo = new JComboBox<>(
            new String[] { "Explore variations", "Mainline only" });

    /**
     * Duplicate-branch skip toggle.
     */
    private final ToggleBox skipSimilarToggle = new ToggleBox("Skip repeats", true);

    /**
     * Timer that advances queued puzzle reply animations one move at a time.
     */
    private final Timer responseAnimationTimer = new Timer(OPPONENT_REPLY_DELAY_MS,
            event -> advanceResponseAnimation());

    /**
     * Active puzzle session.
     */
    private PuzzleSession session;

    /**
     * Loaded puzzle-library entries.
     */
    private List<PuzzleLibrary.Entry> puzzleLibrary = List.of();

    /**
     * Active puzzle-library index.
     */
    private int puzzleLibraryIndex = -1;

    /**
     * Source file for the loaded puzzle library.
     */
    private Path puzzleLibraryPath;

    /**
     * Queued board positions for an animated puzzle response.
     */
    private List<AnimatedMove> pendingResponseMoves = List.of();

    /**
     * Index of the next queued response move to show.
     */
    private int pendingResponseIndex;

    /**
     * Final FEN used if an in-flight response animation is interrupted.
     */
    private String pendingResponseFinalFen = "";

    /**
     * Final last-move highlight used if an in-flight response animation is interrupted.
     */
    private short pendingResponseFinalMove = Move.NO_MOVE;

    /**
     * Whether a puzzle response is currently animating through automatic moves.
     */
    private boolean responseAnimationActive;

    /**
     * Creates the puzzle trainer panel.
     */
    public PuzzlePanel() {
        this(true);
    }

    /**
     * Creates the puzzle trainer panel.
     *
     * @param loadLibrary true to load the default difficult-puzzle library
     */
    PuzzlePanel(boolean loadLibrary) {
        super(new BorderLayout(0, 8));
        configurePanel();
        add(createHeader(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);
        installBoardHandlers();
        loadSamplePuzzle();
        if (loadLibrary) {
            loadDefaultLibrary();
        }
    }

    /**
     * Stops pending reply timers when the puzzle panel leaves the component tree.
     */
    @Override
    public void removeNotify() {
        clearResponseAnimationState();
        super.removeNotify();
    }

    /**
     * Applies root component styling.
     */
    private void configurePanel() {
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setForeground(Theme.TEXT);
        setBorder(Theme.pad(10, 10, 10, 10));
        board.setShowNotation(true);
        board.setShowLegalMovePreview(true);
        board.setShowLastMoveHighlight(true);
        board.setSnapbackSoundEnabled(false);
        solutionArea.setEditable(false);
        solutionArea.setLineWrap(true);
        solutionArea.setWrapStyleWord(true);
        libraryLabel.setFont(Theme.font(12, Font.PLAIN));
        Theme.foreground(libraryLabel, Theme.ForegroundRole.MUTED);
        pgnInput.setLineWrap(true);
        pgnInput.setWrapStyleWord(false);
        pgnInput.setRows(13);
        pgnInput.setColumns(36);
        responseAnimationTimer.setRepeats(false);
        responseAnimationTimer.setCoalesce(true);
        solutionArea.setRows(5);
        solutionArea.setColumns(36);
        styleAreas(pgnInput, solutionArea);
        placeholder(pgnInput, "Paste a PGN puzzle with a FEN tag and variations");
        styleCombo(modeCombo);
        modeCombo.setPrototypeDisplayValue("Explore variations");
        Dimension modeSize = new Dimension(MODE_COMBO_WIDTH, Theme.CONTROL_HEIGHT);
        modeCombo.setPreferredSize(modeSize);
        modeCombo.setMinimumSize(modeSize);
        skipSimilarToggle.setToolTipText("Skip variation branches with the same remaining user moves.");
    }

    /**
     * Installs board callbacks.
     */
    private void installBoardHandlers() {
        board.setMoveHandler(this::playUserMove);
        board.setDropResolver(this::resolveDrop);
    }

    /**
     * Creates the top command strip.
     *
     * @return header component
     */
    private JComponent createHeader() {
        JPanel header = transparentPanel(new GridBagLayout());
        header.setOpaque(true);
        header.setBackground(Theme.INPUT);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                Theme.pad(4, 8, 4, 8)));

        JLabel scope = new JLabel("chess-rtk");
        Theme.foreground(scope, Theme.ForegroundRole.MUTED);
        scope.setFont(Theme.font(12, Font.PLAIN));

        Theme.foreground(titleLabel, Theme.ForegroundRole.TEXT);
        titleLabel.setFont(Theme.font(13, Font.BOLD));

        progressLabel.setFont(Theme.font(12, Font.BOLD));
        Theme.foreground(progressLabel, Theme.ForegroundRole.MUTED);

        GridBagConstraints c = constraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 3, Theme.SPACE_MD);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        grid(header, scope, c, 0, 0, 1, 1);

        c.insets = new Insets(0, 0, 3, Theme.SPACE_MD);
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid(header, titleLabel, c, 1, 0, 1, 1);

        c.insets = new Insets(0, 0, 3, 0);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        grid(header, progressLabel, c, 2, 0, 1, 1);

        JPanel controls = transparentPanel(new WrappingFlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.add(labeledControl("", modeCombo));
        controls.add(skipSimilarToggle);
        c.gridy = 1;
        c.gridx = 0;
        c.gridwidth = 3;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(1, 0, 0, 0);
        header.add(controls, c);
        return header;
    }

    /**
     * Creates the split body.
     *
     * @return body component
     */
    private JComponent createBody() {
        JPanel boardStage = transparentPanel(new BorderLayout(0, 6));
        boardStage.add(board, BorderLayout.CENTER);
        boardStage.add(createBoardFooter(), BorderLayout.SOUTH);

        JPanel side = new SurfacePanel(new BorderLayout(0, 8));
        side.setPreferredSize(new Dimension(430, 560));
        side.add(createActions(), BorderLayout.NORTH);
        side.add(createPuzzleTools(), BorderLayout.CENTER);

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
     * Creates board status text.
     *
     * @return footer component
     */
    private JComponent createBoardFooter() {
        JPanel footer = transparentPanel(new BorderLayout(8, 0));
        statusLabel.setFont(Theme.font(12, Font.PLAIN));
        Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
        sideLabel.setFont(Theme.font(12, Font.BOLD));
        Theme.foreground(sideLabel, Theme.ForegroundRole.MUTED);
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(sideLabel, BorderLayout.EAST);
        return footer;
    }

    /**
     * Creates the primary action cluster.
     *
     * @return actions component
     */
    private JComponent createActions() {
        JPanel panel = transparentPanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.insets = new Insets(0, 0, 6, 0);
        grid(panel, Theme.section("Puzzle"), c, 0, 0, 3, 1);

        c.insets = new Insets(3, 0, 3, 0);
        countersLabel.setFont(Theme.font(12, Font.PLAIN));
        Theme.foreground(countersLabel, Theme.ForegroundRole.MUTED);
        grid(panel, countersLabel, c, 0, 1, 3, 1);
        grid(panel, libraryLabel, c, 0, 2, 3, 1);

        JPanel rowOne = buttonRow(FlowLayout.LEFT,
                button("Load Puzzle", true, event -> loadFromEditor()),
                button("Load File", false, event -> openFile()),
                button("Sample", false, event -> loadSamplePuzzle()));
        grid(panel, rowOne, c, 0, 3, 3, 1);

        JPanel rowTwo = buttonRow(FlowLayout.LEFT,
                button("Previous", false, event -> loadAdjacentPuzzle(-1)),
                button("Next", false, event -> loadAdjacentPuzzle(1)),
                button("Random", false, event -> loadRandomPuzzle()));
        grid(panel, rowTwo, c, 0, 4, 3, 1);

        JPanel rowThree = buttonRow(FlowLayout.LEFT,
                button("Restart", false, event -> restart()),
                button("Hint", false, event -> showHint()),
                button("Reveal", false, event -> reveal()),
                button("Skip", false, event -> skipVariation()));
        grid(panel, rowThree, c, 0, 5, 3, 1);
        return panel;
    }

    /**
     * Creates collapsible puzzle details.
     *
     * @return tools component
     */
    private JComponent createPuzzleTools() {
        JPanel stack = transparentPanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
        stack.add(collapsible("PGN", scroll(pgnInput), true), c);
        c.gridy = 1;
        stack.add(collapsible("Solution", scroll(solutionArea), false), c);
        c.gridy = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 0);
        stack.add(transparentPanel(new BorderLayout()), c);
        return stack;
    }

    /**
     * Loads the bundled sample puzzle.
     */
    private void loadSamplePuzzle() {
        finishResponseAnimationInstantly();
        clearLibrarySelection();
        pgnInput.setText(SAMPLE_PGN);
        loadFromEditor();
    }

    /**
     * Loads a puzzle from the editor text.
     */
    private void loadFromEditor() {
        try {
            finishResponseAnimationInstantly();
            clearLibrarySelection();
            session = PuzzleSession.fromPgn(pgnInput.getText(), "editor", variationMode());
            session.reset();
            displaySnapshot(Move.NO_MOVE);
            setStatus("Loaded " + session.title());
        } catch (RuntimeException ex) {
            setStatus("Could not load puzzle: " + ex.getMessage());
        }
    }

    /**
     * Loads the default difficult puzzle library when available.
     */
    private void loadDefaultLibrary() {
        if (!Files.isRegularFile(PuzzleLibrary.DEFAULT_PATH)) {
            setStatus("Default puzzle file not found; loaded sample.");
            return;
        }
        setStatus("Loading difficult puzzle set...");
        loadLibraryAsync(PuzzleLibrary.DEFAULT_PATH);
    }

    /**
     * Opens a PGN or CSV puzzle file and loads it asynchronously.
     */
    private void openFile() {
        finishResponseAnimationInstantly();
        JFileChooser chooser = FileDialogs.createFileChooser("Open Puzzle File",
                DEFAULT_PUZZLE_FILE,
                new FileNameExtensionFilter("Puzzle files", "pgn", "txt", "csv"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        if (PuzzleLibrary.isCsv(path)) {
            setStatus("Loading puzzle library " + path.getFileName() + "...");
            loadLibraryAsync(path);
            return;
        }
        loadPgnFileAsync(path);
    }

    /**
     * Loads a PGN text file asynchronously.
     *
     * @param path selected file path
     */
    private void loadPgnFileAsync(Path path) {
        setStatus("Loading " + path.getFileName() + "...");
        new SwingWorker<String, Void>() {
            /**
             * Reads the chosen file off the Swing thread.
             *
             * @return file text
             * @throws Exception when the file cannot be read
             */
            @Override
            protected String doInBackground() throws Exception {
                return Files.readString(path, StandardCharsets.UTF_8);
            }

            /**
             * Applies the loaded PGN on the Swing thread.
             */
            @Override
            protected void done() {
                try {
                    pgnInput.setText(get());
                    loadFromEditor();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setStatus("Puzzle loading interrupted.");
                } catch (java.util.concurrent.ExecutionException ex) {
                    setStatus("Could not read puzzle: " + ex.getCause().getMessage());
                }
            }
        }.execute();
    }

    /**
     * Loads a puzzle CSV library asynchronously.
     *
     * @param path selected library path
     */
    private void loadLibraryAsync(Path path) {
        new SwingWorker<List<PuzzleLibrary.Entry>, Void>() {
            /**
             * Reads and parses the selected CSV off the Swing thread.
             *
             * @return puzzle entries
             * @throws Exception when loading fails
             */
            @Override
            protected List<PuzzleLibrary.Entry> doInBackground() throws Exception {
                return PuzzleLibrary.read(path);
            }

            /**
             * Applies the parsed library on the Swing thread.
             */
            @Override
            protected void done() {
                try {
                    applyLibrary(path, get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setStatus("Puzzle library loading interrupted.");
                } catch (java.util.concurrent.ExecutionException ex) {
                    setStatus("Could not load puzzle library: " + ex.getCause().getMessage());
                    updateLabels();
                }
            }
        }.execute();
    }

    /**
     * Applies a loaded puzzle library.
     *
     * @param path source path
     * @param entries puzzle entries
     */
    private void applyLibrary(Path path, List<PuzzleLibrary.Entry> entries) {
        puzzleLibrary = entries == null ? List.of() : List.copyOf(entries);
        puzzleLibraryPath = path;
        puzzleLibraryIndex = -1;
        if (puzzleLibrary.isEmpty()) {
            setStatus("Puzzle library is empty.");
            updateLabels();
            return;
        }
        loadLibraryPuzzle(0);
        setStatus("Loaded " + puzzleLibrary.size() + " difficult puzzles from " + path.getFileName() + ".");
    }

    /**
     * Loads the adjacent puzzle from the active collection.
     *
     * @param delta index delta
     */
    private void loadAdjacentPuzzle(int delta) {
        finishResponseAnimationInstantly();
        if (puzzleLibrary.isEmpty()) {
            setStatus("Load a puzzle library first.");
            return;
        }
        int base = puzzleLibraryIndex < 0 ? 0 : puzzleLibraryIndex;
        loadLibraryPuzzle(Math.floorMod(base + delta, puzzleLibrary.size()));
    }

    /**
     * Loads a random puzzle from the active collection.
     */
    private void loadRandomPuzzle() {
        finishResponseAnimationInstantly();
        if (puzzleLibrary.isEmpty()) {
            setStatus("Load a puzzle library first.");
            return;
        }
        loadLibraryPuzzle(ThreadLocalRandom.current().nextInt(puzzleLibrary.size()));
    }

    /**
     * Moves the visible puzzle board backward or forward inside the active
     * branch.
     *
     * @param delta signed ply delta
     */
    public void navigateReview(int delta) {
        finishResponseAnimationInstantly();
        if (session == null) {
            return;
        }
        if (session.navigatePly(delta)) {
            displaySnapshot(Move.NO_MOVE);
            setStatus("Review ply " + session.cursor().cursorIndex() + " / " + session.lastPly() + ".");
        } else {
            updateLabels();
        }
    }

    /**
     * Shows the start position of the active puzzle branch.
     */
    public void jumpReviewToStart() {
        jumpReviewTo(0);
    }

    /**
     * Shows the final position of the active puzzle branch.
     */
    public void jumpReviewToEnd() {
        if (session != null) {
            jumpReviewTo(session.lastPly());
        }
    }

    /**
     * Shows one ply in the active puzzle branch.
     *
     * @param ply target ply index
     */
    private void jumpReviewTo(int ply) {
        finishResponseAnimationInstantly();
        if (session == null) {
            return;
        }
        if (session.jumpToPly(ply)) {
            displaySnapshot(Move.NO_MOVE);
            setStatus("Review ply " + session.cursor().cursorIndex() + " / " + session.lastPly() + ".");
        } else {
            updateLabels();
        }
    }

    /**
     * Loads a selected library puzzle into the trainer.
     *
     * @param requestedIndex requested entry index
     */
    private void loadLibraryPuzzle(int requestedIndex) {
        finishResponseAnimationInstantly();
        if (puzzleLibrary.isEmpty()) {
            return;
        }
        List<String> errors = new ArrayList<>();
        for (int offset = 0; offset < puzzleLibrary.size(); offset++) {
            int candidate = Math.floorMod(requestedIndex + offset, puzzleLibrary.size());
            PuzzleLibrary.Entry entry = puzzleLibrary.get(candidate);
            try {
                session = PuzzleSession.fromUciLine(entry.title(), entry.id(), entry.fen(), entry.moves(),
                        variationMode());
                session.reset();
                puzzleLibraryIndex = candidate;
                pgnInput.setText(PuzzleLibrary.toPgn(entry));
                displaySnapshot(Move.NO_MOVE);
                setStatus("Loaded " + entry.title() + ".");
                return;
            } catch (RuntimeException ex) {
                errors.add(entry.id() + ": " + ex.getMessage());
            }
        }
        session = null;
        setStatus("Could not load any puzzle from library: " + String.join("; ", errors));
        updateLabels();
    }

    /**
     * Clears active collection metadata for single-PGN mode.
     */
    private void clearLibrarySelection() {
        puzzleLibrary = List.of();
        puzzleLibraryIndex = -1;
        puzzleLibraryPath = null;
        libraryLabel.setText("");
    }

    /**
     * Restarts the active puzzle.
     */
    private void restart() {
        finishResponseAnimationInstantly();
        if (session == null) {
            loadFromEditor();
            return;
        }
        session.reset();
        displaySnapshot(Move.NO_MOVE);
        setStatus("Restarted " + session.title());
    }

    /**
     * Shows a source-square and arrow hint.
     */
    private void showHint() {
        finishResponseAnimationInstantly();
        if (session == null) {
            return;
        }
        PuzzleSession.Hint hint = session.hint(skipSimilarToggle.isSelected());
        if (hint.move() == Move.NO_MOVE) {
            setStatus("No remaining move.");
            updateLabels();
            return;
        }
        board.clearMarkup();
        board.highlightSquare(hint.fromSquare(), Theme.ACCENT);
        board.addArrow(hint.fromSquare(), Move.getToIndex(hint.move()), Theme.ACCENT);
        board.setSuggestedMove(hint.move());
        setStatus("Hint: " + Move.toString(hint.move()));
        SoundService.play(SoundCue.HINT);
        updateLabels();
    }

    /**
     * Reveals and plays the expected move.
     */
    private void reveal() {
        finishResponseAnimationInstantly();
        if (session == null) {
            return;
        }
        PuzzleSession.MoveResponse response = session.reveal(skipSimilarToggle.isSelected());
        playPuzzleResponseSound(response, true);
        applyResponse(response, response.expectedMove(), "Revealed");
    }

    /**
     * Skips the active branch.
     */
    private void skipVariation() {
        finishResponseAnimationInstantly();
        if (session == null) {
            return;
        }
        PuzzleSession.MoveResponse response = session.skipVariation(skipSimilarToggle.isSelected());
        applyResponse(response, Move.NO_MOVE, response.solved() ? "Completed" : "Skipped branch");
    }

    /**
     * Handles a move emitted by the board.
     *
     * @param move CRTK move encoding
     */
    private void playUserMove(short move) {
        if (responseAnimationActive || session == null || move == Move.NO_MOVE) {
            return;
        }
        PuzzleSession.MoveResponse response = session.playUserMove(move, skipSimilarToggle.isSelected());
        if (response.result() == PuzzleSession.StepResult.INCORRECT) {
            markWrongMove(response.expectedMove());
            return;
        }
        playPuzzleResponseSound(response, false);
        applyResponse(response, move, response.result() == PuzzleSession.StepResult.COMPLETED ? "Solved" : "Correct");
    }

    /**
     * Resolves a dragged piece before the board commits it visually.
     *
     * @param context drop context
     * @return move to allow, or {@link Move#NO_MOVE} for snapback
     */
    private int resolveDrop(DropContext context) {
        if (responseAnimationActive) {
            return Move.NO_MOVE;
        }
        if (session == null || context.defaultMove() == Move.NO_MOVE) {
            return Move.NO_MOVE;
        }
        if (session.acceptsDrop(context.defaultMove())) {
            return context.defaultMove();
        }
        PuzzleSession.MoveResponse response =
                session.playUserMove(context.defaultMove(), skipSimilarToggle.isSelected());
        markWrongMove(response.expectedMove());
        return Move.NO_MOVE;
    }

    /**
     * Shows wrong-move feedback without changing the board position.
     *
     * @param expected expected move
     */
    private void markWrongMove(short expected) {
        board.clearMarkup();
        if (expected != Move.NO_MOVE) {
            board.highlightSquare(Move.getFromIndex(expected), Theme.STATUS_WARNING_TEXT);
            board.addArrow(Move.getFromIndex(expected), Move.getToIndex(expected), Theme.STATUS_WARNING_TEXT);
            board.setSuggestedMove(expected);
            setStatus("Try again. Best is " + Move.toString(expected) + ".");
        } else {
            setStatus("Try again.");
        }
        SoundService.play(SoundCue.PUZZLE_WRONG);
        updateLabels();
    }

    /**
     * Plays the restrained puzzle feedback cue for one response.
     *
     * @param response puzzle response
     * @param reveal true when the response came from a reveal command
     */
    private static void playPuzzleResponseSound(PuzzleSession.MoveResponse response, boolean reveal) {
        if (response.result() == PuzzleSession.StepResult.COMPLETED || response.solved()) {
            SoundService.play(SoundCue.PUZZLE_COMPLETE);
        } else if (reveal) {
            SoundService.play(SoundCue.REVEAL);
        } else {
            SoundService.play(SoundCue.PUZZLE_CORRECT);
        }
    }

    /**
     * Applies a move response to the board and labels.
     *
     * @param response session response
     * @param fallbackMove move to use for highlighting when no auto move exists
     * @param prefix status prefix
     */
    private void applyResponse(PuzzleSession.MoveResponse response, short fallbackMove, String prefix) {
        short displayMove = displayMove(response.autoPlayedMoves(), fallbackMove);
        List<AnimatedMove> sequence = responseAnimationSequence(response, fallbackMove);
        if (sequence.isEmpty()) {
            displaySnapshot(displayMove);
        } else {
            beginResponseAnimation(sequence, response.snapshot().fen(), displayMove);
        }
        if (response.result() == PuzzleSession.StepResult.COMPLETED || response.solved()) {
            setStatus(prefix + ". Puzzle complete.");
        } else if (!response.autoPlayedMoves().isEmpty()) {
            setStatus(prefix + ". Opponent: " + moveList(response.autoPlayedMoves()) + ".");
        } else {
            setStatus(prefix + ".");
        }
    }

    /**
     * Displays the session snapshot on the board.
     *
     * @param lastMove move highlighted as the last move
     */
    private void displaySnapshot(short lastMove) {
        if (session == null) {
            return;
        }
        clearResponseAnimationState();
        PuzzleSession.Snapshot snapshot = session.snapshot();
        board.clearMarkup();
        board.setWhiteDown(session.userWhite());
        short highlightMove = lastMove == Move.NO_MOVE ? session.currentLastMove() : lastMove;
        board.setPosition(new Position(snapshot.fen()), highlightMove);
        updateLabels();
    }

    /**
     * Builds the visible board steps for a puzzle response.
     *
     * @param response session response
     * @param fallbackMove solver move to include before automatic replies
     * @return animated board steps, or an empty list when the response cannot
     *     be represented as a simple forward move sequence
     */
    private List<AnimatedMove> responseAnimationSequence(PuzzleSession.MoveResponse response, short fallbackMove) {
        if (response == null || !response.rewindFens().isEmpty()) {
            return List.of();
        }
        String currentFen = board.position();
        if (currentFen == null || currentFen.isBlank()) {
            return List.of();
        }
        try {
            Position cursor = new Position(currentFen);
            List<AnimatedMove> steps = new ArrayList<>();
            if (fallbackMove != Move.NO_MOVE) {
                cursor = appendAnimatedStep(steps, cursor, fallbackMove);
                if (cursor == null) {
                    return List.of();
                }
            }
            for (Short moveValue : response.autoPlayedMoves()) {
                short move = moveValue == null ? Move.NO_MOVE : moveValue.shortValue();
                cursor = appendAnimatedStep(steps, cursor, move);
                if (cursor == null) {
                    return List.of();
                }
            }
            if (steps.isEmpty() || !cursor.toString().equals(response.snapshot().fen())) {
                return List.of();
            }
            return List.copyOf(steps);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /**
     * Appends one legal move to an animation sequence.
     *
     * @param steps output animation sequence
     * @param position position before the move
     * @param move move to play
     * @return position after the move, or null when the move is not legal
     */
    private static Position appendAnimatedStep(List<AnimatedMove> steps, Position position, short move) {
        if (position == null || move == Move.NO_MOVE || !position.isLegalMove(move)) {
            return null;
        }
        Position next = position.copy();
        next.play(move);
        steps.add(new AnimatedMove(next, move));
        return next;
    }

    /**
     * Starts a queued response animation.
     *
     * @param sequence positions to show in order
     * @param finalFen final session FEN
     * @param finalMove final last-move highlight
     */
    private void beginResponseAnimation(List<AnimatedMove> sequence, String finalFen, short finalMove) {
        clearResponseAnimationState();
        board.clearMarkup();
        board.setWhiteDown(session.userWhite());
        pendingResponseMoves = List.copyOf(sequence);
        pendingResponseFinalFen = finalFen == null ? "" : finalFen;
        pendingResponseFinalMove = finalMove;
        pendingResponseIndex = 0;
        responseAnimationActive = pendingResponseMoves.size() > 1;
        applyResponseAnimationStep();
        if (responseAnimationActive) {
            responseAnimationTimer.restart();
        } else {
            clearResponseAnimationState();
        }
        updateLabels();
    }

    /**
     * Advances to the next automatic response move.
     */
    private void advanceResponseAnimation() {
        if (!responseAnimationActive) {
            return;
        }
        applyResponseAnimationStep();
        if (pendingResponseIndex < pendingResponseMoves.size()) {
            responseAnimationTimer.restart();
            return;
        }
        clearResponseAnimationState();
        updateLabels();
    }

    /**
     * Applies the next queued response move to the board.
     */
    private void applyResponseAnimationStep() {
        if (pendingResponseIndex >= pendingResponseMoves.size()) {
            return;
        }
        AnimatedMove step = pendingResponseMoves.get(pendingResponseIndex);
        pendingResponseIndex++;
        board.setPosition(step.position(), step.move());
    }

    /**
     * Finishes any pending response animation at the session's final position.
     */
    private void finishResponseAnimationInstantly() {
        if (!responseAnimationActive) {
            clearResponseAnimationState();
            return;
        }
        responseAnimationTimer.stop();
        if (!pendingResponseFinalFen.isBlank()) {
            board.setPositionInstant(new Position(pendingResponseFinalFen), pendingResponseFinalMove);
            updateLabels();
        }
        clearResponseAnimationState();
    }

    /**
     * Clears queued response animation state.
     */
    private void clearResponseAnimationState() {
        responseAnimationTimer.stop();
        pendingResponseMoves = List.of();
        pendingResponseIndex = 0;
        pendingResponseFinalFen = "";
        pendingResponseFinalMove = Move.NO_MOVE;
        responseAnimationActive = false;
    }

    /**
     * Updates title, progress, and solution labels.
     */
    private void updateLabels() {
        if (session == null) {
            titleLabel.setText("Puzzles");
            progressLabel.setText("0 / 0");
            countersLabel.setText("");
            libraryLabel.setText("");
            sideLabel.setText("");
            solutionArea.setText("");
            return;
        }
        PuzzleSession.Snapshot snapshot = session.snapshot();
        titleLabel.setText(session.title());
        progressLabel.setText((snapshot.lineIndex() + 1) + " / " + Math.max(1, snapshot.totalLines()));
        countersLabel.setText("wrong " + session.wrongMoveCount()
                + "  hints " + session.hintCount()
                + "  reveals " + session.revealCount()
                + "  skipped " + session.skippedSimilarVariationCount());
        libraryLabel.setText(libraryText());
        sideLabel.setText((session.userWhite() ? "White" : "Black") + " to solve");
        solutionArea.setText(solutionText(snapshot));
        solutionArea.setCaretPosition(0);
    }

    /**
     * Builds the current puzzle-library label.
     *
     * @return library label text
     */
    private String libraryText() {
        if (puzzleLibrary.isEmpty() || puzzleLibraryIndex < 0 || puzzleLibraryIndex >= puzzleLibrary.size()) {
            return "Single PGN puzzle";
        }
        PuzzleLibrary.Entry entry = puzzleLibrary.get(puzzleLibraryIndex);
        String source = puzzleLibraryPath == null || puzzleLibraryPath.getFileName() == null
                ? "library"
                : puzzleLibraryPath.getFileName().toString();
        String detail = entry.detail();
        String prefix = (puzzleLibraryIndex + 1) + " / " + puzzleLibrary.size() + " · " + source;
        return detail.isBlank() ? prefix : prefix + " · " + detail;
    }

    /**
     * Builds the solution text.
     *
     * @param snapshot current snapshot
     * @return solution text
     */
    private String solutionText(PuzzleSession.Snapshot snapshot) {
        if (snapshot.solved()) {
            return "Complete.";
        }
        String expected = session.expectedSan();
        String line = session.solutionLine();
        if (line.isBlank()) {
            return expected.isBlank() ? "No remaining move." : expected;
        }
        return line;
    }

    /**
     * Sets the status label.
     *
     * @param text status text
     */
    private void setStatus(String text) {
        statusLabel.setText(text == null ? "" : text);
    }

    /**
     * Returns the selected variation mode.
     *
     * @return selected variation mode
     */
    private PuzzleSession.VariationMode variationMode() {
        return modeCombo.getSelectedIndex() == 1
                ? PuzzleSession.VariationMode.MAINLINE
                : PuzzleSession.VariationMode.EXPLORE;
    }

    /**
     * Returns the move used for last-move highlighting.
     *
     * @param autoPlayed auto-played moves
     * @param fallback fallback move
     * @return display move
     */
    private static short displayMove(List<Short> autoPlayed, short fallback) {
        if (autoPlayed == null || autoPlayed.isEmpty()) {
            return fallback;
        }
        return autoPlayed.get(autoPlayed.size() - 1).shortValue();
    }

    /**
     * Formats a move list as UCI text.
     *
     * @param moves moves
     * @return move list text
     */
    private static String moveList(List<Short> moves) {
        List<String> text = new java.util.ArrayList<>();
        for (Short move : moves) {
            if (move != null && move.shortValue() != Move.NO_MOVE) {
                text.add(Move.toString(move.shortValue()));
            }
        }
        return String.join(" ", text);
    }

    /**
     * One visible move in a puzzle response animation.
     *
     * @param position board position after the move
     * @param move move that produced the position
     */
    private record AnimatedMove(Position position, short move) { }
}
