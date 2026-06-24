package application.gui.workbench.game;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.board.DropContext;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import chess.core.Move;
import chess.core.Position;
import chess.core.Setup;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Component;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleCombo;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Native Swing chess puzzle trainer with PGN variation support.
 */
public final class PuzzlePanel extends SurfacePanel {

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
    private static final File DEFAULT_PUZZLE_FILE = PuzzleLibrary.defaultPath().toFile();

    /**
     * Width reserved for the puzzle branch-mode selector.
     */
    private static final int MODE_COMBO_WIDTH = 176;

    /**
     * Empty inspector card key.
     */
    private static final String INSPECTOR_EMPTY = "empty";

    /**
     * Loaded inspector card key.
     */
    private static final String INSPECTOR_LOADED = "loaded";

    /**
     * Hidden solution card key.
     */
    private static final String SOLUTION_HIDDEN = "hidden";

    /**
     * Revealed solution card key.
     */
    private static final String SOLUTION_REVEALED = "revealed";

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
    private final JTextArea pgnInput = new JTextArea();

    /**
     * Hidden/collapsible solution preview.
     */
    private final JTextArea solutionArea = new JTextArea();

    /**
     * Raw solution preview retained for the Advanced/debug section.
     */
    private final JTextArea rawSolutionArea = new JTextArea();

    /**
     * Shell header refresh callback.
     */
    private final transient Runnable summaryChanged;

    /**
     * Right inspector card switcher.
     */
    private final JPanel inspectorCards = new JPanel(new CardLayout());

    /**
     * Solution card switcher.
     */
    private final JPanel solutionCards = new JPanel(new CardLayout());

    /**
     * Feedback state badge.
     */
    private final StatusBadge feedbackBadge = new StatusBadge();

    /**
     * Feedback text line.
     */
    private final JLabel feedbackLabel = new JLabel("No puzzle loaded");

    /**
     * Puzzle id metadata value.
     */
    private final JLabel puzzleIdValue = metricValue();

    /**
     * Source metadata value.
     */
    private final JLabel sourceValue = metricValue();

    /**
     * Rating metadata value.
     */
    private final JLabel ratingValue = metricValue();

    /**
     * Theme/tag metadata value.
     */
    private final JLabel themesValue = metricValue();

    /**
     * Side-to-solve metadata value.
     */
    private final JLabel sideToSolveValue = metricValue();

    /**
     * Progress metadata value.
     */
    private final JLabel progressValue = metricValue();

    /**
     * Puzzle title label.
     */
    private final JLabel titleLabel = new JLabel("Puzzles");

    /**
     * Current status label.
     */
    private final JLabel statusLabel = new JLabel("Load a PGN puzzle.");

    /**
     * Branch progress label, painted as a compact accent-tinted count pill.
     */
    private final JLabel progressLabel = new JLabel("0 / 0") {
        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * {@inheritDoc}
         */
        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 48 : 30));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.RADIUS + 3, Theme.RADIUS + 3);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    };

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
     * Active puzzle-library entry, or null for editor/sample PGN mode.
     */
    private PuzzleLibrary.Entry activePuzzleEntry;

    /**
     * Monotonic token used to ignore stale asynchronous puzzle loads.
     */
    private long loadGeneration;

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
     * Whether the visible Solution card may show the line. Set by Reveal only.
     */
    private boolean solutionRevealed;

    /**
     * Creates the puzzle trainer panel.
     */
    public PuzzlePanel() {
        this(true, null);
    }

    /**
     * Creates the puzzle trainer panel.
     *
     * @param summaryChanged callback fired when shell header context should refresh
     */
    public PuzzlePanel(Runnable summaryChanged) {
        this(true, summaryChanged);
    }

    /**
     * Creates the puzzle trainer panel.
     *
     * @param loadLibrary true to load the default difficult-puzzle library
     */
    PuzzlePanel(boolean loadLibrary) {
        this(loadLibrary, null);
    }

    /**
     * Creates the puzzle trainer panel.
     *
     * @param loadLibrary true to load the default difficult-puzzle library
     * @param summaryChanged callback fired when shell header context should refresh
     */
    private PuzzlePanel(boolean loadLibrary, Runnable summaryChanged) {
        super(new BorderLayout(0, 8), Theme.Surface.PANEL);
        this.summaryChanged = summaryChanged == null ? () -> {
            // optional callback
        } : summaryChanged;
        configurePanel();
        add(createBody(), BorderLayout.CENTER);
        installBoardHandlers();
        showStartingPosition("No puzzle library loaded.");
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
        setBorder(Theme.pad(Theme.SPACE_MD));
        board.setShowNotation(true);
        board.setShowLegalMovePreview(true);
        board.setShowLastMoveHighlight(true);
        board.setSnapbackSoundEnabled(false);
        solutionArea.setEditable(false);
        rawSolutionArea.setEditable(false);
        solutionArea.setLineWrap(true);
        solutionArea.setWrapStyleWord(true);
        rawSolutionArea.setLineWrap(true);
        rawSolutionArea.setWrapStyleWord(true);
        libraryLabel.setFont(Theme.font(12, Font.PLAIN));
        Theme.foreground(libraryLabel, Theme.ForegroundRole.MUTED);
        pgnInput.setLineWrap(true);
        pgnInput.setWrapStyleWord(false);
        pgnInput.setRows(10);
        pgnInput.setColumns(36);
        pgnInput.setFont(Theme.mono(12f));
        responseAnimationTimer.setRepeats(false);
        responseAnimationTimer.setCoalesce(true);
        solutionArea.setRows(4);
        solutionArea.setColumns(36);
        solutionArea.setFont(Theme.mono(12f));
        rawSolutionArea.setRows(4);
        rawSolutionArea.setColumns(36);
        rawSolutionArea.setFont(Theme.mono(12f));
        styleAreas(pgnInput, solutionArea, rawSolutionArea);
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
     * Creates the split body.
     *
     * @return body component
     */
    private JComponent createBody() {
        JPanel boardStage = transparentPanel(new BorderLayout(0, 6));
        boardStage.add(board, BorderLayout.CENTER);
        boardStage.add(createBoardFooter(), BorderLayout.SOUTH);

        JPanel side = new SurfacePanel(new BorderLayout(0, 8));
        side.setPreferredSize(new Dimension(360, 560));
        side.add(scroll(fillViewport(createInspector())), BorderLayout.CENTER);

        JSplitPane split = SplitPaneStyler.styledHorizontalSplit(boardStage, side, 0.68);
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
     * Creates the right-side Solve inspector.
     *
     * @return inspector component
     */
    private JComponent createInspector() {
        inspectorCards.setOpaque(false);
        inspectorCards.add(createEmptyInspector(), INSPECTOR_EMPTY);
        inspectorCards.add(createLoadedInspector(), INSPECTOR_LOADED);
        showInspectorCard();
        return inspectorCards;
    }

    /**
     * Creates the empty puzzle inspector state.
     *
     * @return empty inspector
     */
    private JComponent createEmptyInspector() {
        JComponent empty = Ui.emptyState("No puzzle loaded",
                "Solve tactical puzzles on the board - load a file, a sample, or a random puzzle to begin.",
                button("Load Puzzle", true, event -> loadDefaultLibrary()),
                button("Load File", false, event -> openFile()),
                button("Sample", false, event -> loadSamplePuzzle()));
        JComponent card = Ui.card(null, empty);
        // Center the full-width empty card vertically in the tall inspector
        // instead of pinning it to the top, where it reads as a small fragment.
        JPanel center = verticalPanel();
        center.setBorder(Theme.pad(Theme.SPACE_MD));
        center.add(Box.createVerticalGlue());
        center.add(card);
        center.add(Box.createVerticalGlue());
        return center;
    }

    /**
     * Creates the loaded puzzle inspector state.
     *
     * @return loaded inspector
     */
    private JComponent createLoadedInspector() {
        JPanel stack = verticalPanel();
        addStackSection(stack, createPuzzleSection());
        addStackSection(stack, createControlsSection());
        addStackSection(stack, collapsible("PGN / source", createPgnSection(), false));
        addStackSection(stack, createSolutionSection());
        addStackSection(stack, createAdvancedSection());
        stack.add(Box.createVerticalGlue());
        return stack;
    }

    /**
     * Creates compact puzzle metadata and feedback.
     *
     * @return puzzle section
     */
    private JComponent createPuzzleSection() {
        feedbackBadge.setFixedTextWidth(104);
        feedbackLabel.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
        feedbackLabel.setForeground(Theme.TEXT);
        countersLabel.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        Theme.foreground(countersLabel, Theme.ForegroundRole.MUTED);

        JPanel feedback = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
        feedback.setOpaque(false);
        feedback.add(feedbackBadge, BorderLayout.WEST);
        feedback.add(feedbackLabel, BorderLayout.CENTER);

        JPanel body = verticalPanel();
        body.add(feedback);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(detailRow("Puzzle", puzzleIdValue));
        body.add(detailRow("Side", sideToSolveValue));
        body.add(detailRow("Rating", ratingValue));
        body.add(detailRow("Progress", progressValue));
        body.add(detailRow("Source", sourceValue));
        body.add(detailRow("Themes", themesValue));
        body.add(Box.createVerticalStrut(Theme.SPACE_XS));
        body.add(countersLabel);
        return flatSection("Puzzle", null, body);
    }

    /**
     * Creates grouped puzzle controls.
     *
     * @return controls section
     */
    private JComponent createControlsSection() {
        JPanel body = verticalPanel();
        body.add(controlGroup("Load",
                button("Load Puzzle", true, event -> loadFromEditor()),
                button("Load File", false, event -> openFile()),
                button("Sample", false, event -> loadSamplePuzzle())));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(controlGroup("Library",
                button("Previous", false, event -> loadAdjacentPuzzle(-1)),
                button("Next", false, event -> loadAdjacentPuzzle(1)),
                button("Random", false, event -> loadRandomPuzzle())));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(controlGroup("Solve",
                button("Restart", false, event -> restart()),
                button("Hint", false, event -> showHint()),
                button("Reveal", false, event -> reveal()),
                button("Skip", false, event -> skipVariation())));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        JPanel mode = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
        mode.setOpaque(false);
        mode.add(modeCombo, BorderLayout.CENTER);
        mode.add(skipSimilarToggle, BorderLayout.EAST);
        body.add(Ui.labelControlRow("Mode", mode, 68));
        return flatSection("Controls", null, body);
    }

    /**
     * Creates the PGN/source editor section.
     *
     * @return PGN section
     */
    private JComponent createPgnSection() {
        JButton copy = button("Copy PGN", false, event -> copyPgn());
        JPanel body = new JPanel(new BorderLayout(0, Theme.SPACE_SM));
        body.setOpaque(false);
        body.add(scroll(pgnInput), BorderLayout.CENTER);
        return flatSection("PGN", copy, body);
    }

    /**
     * Creates the hidden-by-default solution section.
     *
     * @return solution section
     */
    private JComponent createSolutionSection() {
        solutionCards.setOpaque(false);
        solutionCards.add(solutionLockedState(), SOLUTION_HIDDEN);
        solutionCards.add(scroll(solutionArea), SOLUTION_REVEALED);
        showSolutionCard();
        return flatSection("Solution", null, solutionCards);
    }

    /**
     * Creates the locked solution state.
     *
     * @return locked solution component
     */
    private JComponent solutionLockedState() {
        return Ui.emptyState("Solution hidden",
                "Use Reveal to expose and play the next solution move.",
                button("Reveal", false, event -> reveal()));
    }

    /**
     * Creates the advanced/debug section.
     *
     * @return advanced section
     */
    private JComponent createAdvancedSection() {
        JPanel body = verticalPanel();
        body.add(collapsible("Raw Solution", scroll(rawSolutionArea), false));
        return flatSection("Advanced", null, body);
    }

    /**
     * Creates one flat inspector section without an elevated card surface.
     *
     * @param title section title
     * @param trailing optional trailing component
     * @param body section body
     * @return section component
     */
    private static JComponent flatSection(String title, JComponent trailing, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout(0, Theme.SPACE_SM));
        panel.setOpaque(false);
        panel.add(Ui.sectionHeader(title, null, trailing), BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates one labelled group of buttons.
     *
     * @param title group label
     * @param buttons button components
     * @return control group
     */
    private static JComponent controlGroup(String title, javax.swing.JButton... buttons) {
        JPanel row = buttonRow(FlowLayout.LEFT, buttons);
        return Ui.sectionHeader(title, null, row);
    }

    /**
     * Adds a card to a vertical inspector stack.
     *
     * @param stack stack panel
     * @param section section component
     */
    private static void addStackSection(JPanel stack, JComponent section) {
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(section);
        stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
    }

    /**
     * Creates a transparent vertical stack panel.
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
     * Creates one compact metadata row.
     *
     * @param title metric label
     * @param value value label
     * @return row
     */
    private static JComponent detailRow(String title, JLabel value) {
        JLabel label = new JLabel(title);
        label.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        label.setForeground(Theme.MUTED);
        label.setPreferredSize(new Dimension(72, Theme.CONTROL_HEIGHT));

        JPanel row = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.setOpaque(false);
        row.setBorder(Theme.pad(1, 0, 1, 0));
        row.add(label, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    /**
     * Creates a value label for metadata cards.
     *
     * @return value label
     */
    private static JLabel metricValue() {
        JLabel label = new JLabel("n/a");
        label.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
        label.setForeground(Theme.TEXT);
        return label;
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
     * Shows the normal chess starting position while no puzzle is active.
     *
     * @param status status text
     */
    private void showStartingPosition(String status) {
        finishResponseAnimationInstantly();
        nextLoadGeneration();
        clearLibrarySelection();
        session = null;
        activePuzzleEntry = null;
        solutionRevealed = false;
        pgnInput.setText("");
        board.clearMarkup();
        board.clearWrongMoveMarker();
        board.setWhiteDown(true);
        board.setPositionInstant(new Position(Setup.getStandardStartFEN()), Move.NO_MOVE);
        updateLabels();
        setStatus(status);
    }

    /**
     * Loads a puzzle from the editor text.
     */
    private void loadFromEditor() {
        try {
            finishResponseAnimationInstantly();
            nextLoadGeneration();
            clearLibrarySelection();
            activePuzzleEntry = null;
            solutionRevealed = false;
            session = PuzzleSession.fromPgn(pgnInput.getText(), "editor", variationMode());
            session.reset();
            displaySnapshot(Move.NO_MOVE);
            setStatus("Loaded " + session.title());
        } catch (RuntimeException ex) {
            setStatus("Could not load puzzle: " + ex.getMessage());
        }
    }

    /**
     * Loads the default chess-web puzzle library when available.
     */
    private void loadDefaultLibrary() {
        Path defaultPath = PuzzleLibrary.defaultPath();
        if (!Files.isRegularFile(defaultPath)) {
            showStartingPosition("Default puzzle library not found.");
            return;
        }
        setStatus("Loading chess-web puzzle set...");
        loadLibraryAsync(defaultPath);
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
        if (PuzzleLibrary.isLibrary(path)) {
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
        long generation = nextLoadGeneration();
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
                if (!isCurrentLoadGeneration(generation)) {
                    return;
                }
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
     * Loads a puzzle library asynchronously.
     *
     * @param path selected library path
     */
    private void loadLibraryAsync(Path path) {
        long generation = nextLoadGeneration();
        new SwingWorker<List<PuzzleLibrary.Entry>, Void>() {
            /**
             * Reads and parses the selected library off the Swing thread.
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
                if (!isCurrentLoadGeneration(generation)) {
                    return;
                }
                try {
                    applyLibrary(path, get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showStartingPosition("Puzzle library loading interrupted.");
                } catch (java.util.concurrent.ExecutionException ex) {
                    showStartingPosition("Could not load puzzle library: " + ex.getCause().getMessage());
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
        puzzleLibrary = shuffledLibrary(entries);
        puzzleLibraryPath = path;
        puzzleLibraryIndex = -1;
        if (puzzleLibrary.isEmpty()) {
            showStartingPosition("Puzzle library is empty.");
            return;
        }
        loadLibraryPuzzle(0);
        setStatus("Loaded and shuffled " + puzzleLibrary.size() + " puzzles from " + path.getFileName() + ".");
    }

    /**
     * Returns a randomized, immutable copy of a loaded puzzle library.
     *
     * @param entries source entries
     * @return shuffled entries
     */
    private static List<PuzzleLibrary.Entry> shuffledLibrary(List<PuzzleLibrary.Entry> entries) {
        return shuffledLibrary(entries, ThreadLocalRandom.current());
    }

    /**
     * Returns a randomized, immutable copy of a loaded puzzle library.
     *
     * @param entries source entries
     * @param random random source
     * @return shuffled entries
     */
    private static List<PuzzleLibrary.Entry> shuffledLibrary(List<PuzzleLibrary.Entry> entries, Random random) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<PuzzleLibrary.Entry> shuffled = new ArrayList<>(entries);
        if (shuffled.size() > 1) {
            Collections.shuffle(shuffled, random == null ? ThreadLocalRandom.current() : random);
        }
        return List.copyOf(shuffled);
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
     * Toggles the active puzzle review between the branch start and end.
     */
    public void toggleReviewStartEnd() {
        if (session == null) {
            return;
        }
        if (session.cursor().cursorIndex() > 0) {
            jumpReviewToStart();
        } else {
            jumpReviewToEnd();
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
                session = entry.hasPgnText()
                        ? PuzzleSession.fromPgn(entry.pgnText(), entry.id(), variationMode())
                        : PuzzleSession.fromUciLine(entry.title(), entry.id(), entry.fen(), entry.moves(),
                                variationMode());
                session.reset();
                puzzleLibraryIndex = candidate;
                activePuzzleEntry = entry;
                solutionRevealed = false;
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
        activePuzzleEntry = null;
        libraryLabel.setText("");
    }

    /**
     * Starts a new logical puzzle load and cancels older async completions.
     *
     * @return generation token for the new load
     */
    private long nextLoadGeneration() {
        loadGeneration++;
        return loadGeneration;
    }

    /**
     * Returns whether an async load token is still current.
     *
     * @param generation generation token captured by the async load
     * @return true when the load may still update the panel
     */
    private boolean isCurrentLoadGeneration(long generation) {
        return generation == loadGeneration;
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
        solutionRevealed = false;
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
        board.clearWrongMoveMarker();
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
        solutionRevealed = true;
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
            markWrongMove(move);
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
        session.playUserMove(context.defaultMove(), skipSimilarToggle.isSelected());
        markWrongMove(context.defaultMove());
        return Move.NO_MOVE;
    }

    /**
     * Shows wrong-move feedback without changing the board position.
     *
     * @param attempted attempted move
     */
    private void markWrongMove(short attempted) {
        board.clearMarkup();
        board.clearWrongMoveMarker();
        if (attempted != Move.NO_MOVE) {
            board.showWrongMoveMarker(Move.getToIndex(attempted));
            setStatus("Incorrect. Try again.");
        } else {
            setStatus("Incorrect.");
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
        if (response == null) {
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
                Position afterFallback = appendAnimatedStep(steps, cursor, fallbackMove);
                if (afterFallback != null) {
                    cursor = afterFallback;
                }
            }
            for (String rewindFen : response.rewindFens()) {
                cursor = appendInstantStep(steps, rewindFen);
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
        steps.add(new AnimatedMove(next, move, false));
        return next;
    }

    /**
     * Appends one instant board state to an animation sequence.
     *
     * @param steps output animation sequence
     * @param fen position FEN
     * @return parsed position, or null when the FEN is invalid
     */
    private static Position appendInstantStep(List<AnimatedMove> steps, String fen) {
        if (fen == null || fen.isBlank()) {
            return null;
        }
        Position position = new Position(fen);
        steps.add(new AnimatedMove(position, Move.NO_MOVE, true));
        return position;
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
        if (step.instant()) {
            board.setPositionInstant(step.position(), step.move());
        } else {
            board.setPosition(step.position(), step.move());
        }
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
            rawSolutionArea.setText("");
            clearMetadataLabels();
            showInspectorCard();
            showSolutionCard();
            summaryChanged.run();
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
        refreshMetadataLabels(snapshot);
        String solutionText = solutionText(snapshot);
        rawSolutionArea.setText(solutionText);
        rawSolutionArea.setCaretPosition(0);
        if (solutionRevealed) {
            solutionArea.setText(solutionText);
        } else {
            solutionArea.setText("");
        }
        solutionArea.setCaretPosition(0);
        showInspectorCard();
        showSolutionCard();
        summaryChanged.run();
    }

    /**
     * Clears loaded-puzzle metadata.
     */
    private void clearMetadataLabels() {
        for (JLabel label : List.of(puzzleIdValue, sourceValue, ratingValue, themesValue,
                sideToSolveValue, progressValue)) {
            label.setText("n/a");
        }
    }

    /**
     * Refreshes loaded puzzle metadata labels.
     *
     * @param snapshot current snapshot
     */
    private void refreshMetadataLabels(PuzzleSession.Snapshot snapshot) {
        puzzleIdValue.setText(puzzleIdText());
        sourceValue.setText(sourceText());
        ratingValue.setText(ratingText());
        themesValue.setText(themesText());
        sideToSolveValue.setText((session.userWhite() ? "White" : "Black") + " to solve");
        progressValue.setText((snapshot.lineIndex() + 1) + " / " + Math.max(1, snapshot.totalLines()));
    }

    /**
     * Shows the appropriate right-inspector card.
     */
    private void showInspectorCard() {
        CardLayout layout = (CardLayout) inspectorCards.getLayout();
        layout.show(inspectorCards, session == null ? INSPECTOR_EMPTY : INSPECTOR_LOADED);
    }

    /**
     * Shows the hidden or revealed solution state.
     */
    private void showSolutionCard() {
        CardLayout layout = (CardLayout) solutionCards.getLayout();
        layout.show(solutionCards, solutionRevealed ? SOLUTION_REVEALED : SOLUTION_HIDDEN);
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
     * Returns the Board / Solve workspace context line.
     *
     * @return context text
     */
    public String workspaceContext() {
        if (session == null) {
            return "No puzzle loaded";
        }
        PuzzleSession.Snapshot snapshot = session.snapshot();
        return puzzleIdText() + " · " + (session.userWhite() ? "White" : "Black") + " to solve · "
                + (snapshot.lineIndex() + 1) + " / " + Math.max(1, snapshot.totalLines());
    }

    /**
     * Returns the display puzzle id/title.
     *
     * @return puzzle id text
     */
    private String puzzleIdText() {
        if (activePuzzleEntry != null && !activePuzzleEntry.id().isBlank()) {
            return "Puzzle #" + activePuzzleEntry.id();
        }
        if (session == null) {
            return "No puzzle loaded";
        }
        String source = session.source();
        if (source != null && !source.isBlank() && !"editor".equalsIgnoreCase(source)) {
            return "Puzzle #" + source;
        }
        return session.title();
    }

    /**
     * Returns the source label.
     *
     * @return source label
     */
    private String sourceText() {
        if (puzzleLibraryPath != null && puzzleLibraryPath.getFileName() != null) {
            return puzzleLibraryPath.getFileName().toString();
        }
        if (activePuzzleEntry != null && !activePuzzleEntry.gameUrl().isBlank()) {
            return activePuzzleEntry.gameUrl();
        }
        return session == null ? "n/a" : session.source();
    }

    /**
     * Returns the rating label.
     *
     * @return rating label
     */
    private String ratingText() {
        return activePuzzleEntry != null && activePuzzleEntry.rating() > 0
                ? Integer.toString(activePuzzleEntry.rating())
                : "n/a";
    }

    /**
     * Returns compact theme/opening labels.
     *
     * @return theme label
     */
    private String themesText() {
        if (activePuzzleEntry == null) {
            return "n/a";
        }
        String themes = compactList(activePuzzleEntry.themes());
        String openings = compactList(activePuzzleEntry.openingTags());
        if (themes.isBlank()) {
            return openings.isBlank() ? "n/a" : openings;
        }
        return openings.isBlank() ? themes : themes + " · " + openings;
    }

    /**
     * Compacts comma/space-delimited metadata.
     *
     * @param text source text
     * @return compact label
     */
    private static String compactList(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] parts = text.trim().replace(',', ' ').split("\\s+");
        StringBuilder out = new StringBuilder();
        int limit = Math.min(parts.length, 4);
        for (int i = 0; i < limit; i++) {
            if (parts[i].isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(parts[i]);
        }
        if (parts.length > limit) {
            out.append(" ...");
        }
        return out.toString();
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
        String message = text == null ? "" : text;
        statusLabel.setText(message);
        updateFeedback(message);
        summaryChanged.run();
    }

    /**
     * Updates the feedback card from a status message.
     *
     * @param message status message
     */
    private void updateFeedback(String message) {
        String lower = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("loading")) {
            feedbackBadge.running("loading");
            feedbackLabel.setText(message);
        } else if (session == null) {
            if (lower.contains("could not") || lower.contains("not found") || lower.contains("empty")) {
                feedbackBadge.warning("no puzzle");
                feedbackLabel.setText(message.isBlank() ? "No puzzle loaded" : message);
            } else {
                feedbackBadge.notRun("not loaded");
                feedbackLabel.setText("No puzzle loaded");
            }
        } else if (lower.contains("incorrect")) {
            feedbackBadge.warning("incorrect");
            feedbackLabel.setText("Incorrect, try again");
        } else if (lower.contains("revealed")) {
            feedbackBadge.warning("revealed");
            feedbackLabel.setText("Solution revealed");
        } else if (lower.contains("complete") || lower.contains("solved")) {
            feedbackBadge.complete("complete");
            feedbackLabel.setText("Puzzle complete");
        } else if (lower.contains("correct")) {
            feedbackBadge.complete("correct");
            feedbackLabel.setText("Correct");
        } else {
            feedbackBadge.ready("waiting");
            feedbackLabel.setText("Waiting for move");
        }
    }

    /**
     * Copies current PGN/source text.
     */
    private void copyPgn() {
        CommandRunner.copyToClipboard(pgnInput.getText());
        setStatus("Copied PGN.");
        Toast.show(this, Toast.Kind.SUCCESS, "Copied to clipboard");
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
     * @param moves move list
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
     * @param instant true to show the position without move animation
     */
    private record AnimatedMove(Position position, short move, boolean instant) { }
}
