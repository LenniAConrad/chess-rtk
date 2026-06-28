package application.gui.workbench.study;

import application.gui.workbench.game.GameModel;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WrappingFlowLayout;
import chess.core.Move;
import chess.core.Position;
import chess.study.NagCatalog;
import chess.study.StudyChapter;
import chess.study.StudyChapterMode;
import chess.study.StudyNodePath;
import chess.study.StudyProject;
import chess.study.StudyShape;
import chess.study.StudyTreeModel;
import chess.struct.Game;
import chess.struct.Pgn;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;

/**
 * Offline PGN-backed study editor for the Workbench.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class StudyWorkspacePanel extends JPanel {

    /**
     * Serialization identifier for Swing compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Chapter list width.
     */
    private static final int CHAPTER_WIDTH = 210;

    /**
     * Local repository.
     */
    private final transient StudyRepository repository;

    /**
     * Current game supplier.
     */
    private final transient Supplier<GameModel> gameSupplier;

    /**
     * Current FEN supplier.
     */
    private final transient Supplier<String> fenSupplier;

    /**
     * Board position callback.
     */
    private final transient Consumer<Position> showPosition;

    /**
     * Clipboard callback.
     */
    private final transient Consumer<String> copyText;

    /**
     * Chapter list model.
     */
    private final javax.swing.DefaultListModel<StudyChapter> chapterListModel =
            new javax.swing.DefaultListModel<>();

    /**
     * Chapter list.
     */
    private final javax.swing.JList<StudyChapter> chapterList =
            new javax.swing.JList<>(chapterListModel);

    /**
     * Move tree table model.
     */
    private final MoveTreeTableModel moveTreeModel = new MoveTreeTableModel();

    /**
     * Move tree table.
     */
    private final javax.swing.JTable moveTreeTable = new javax.swing.JTable(moveTreeModel);

    /**
     * Shape list model.
     */
    private final javax.swing.DefaultListModel<StudyShape> shapeListModel =
            new javax.swing.DefaultListModel<>();

    /**
     * Shape list.
     */
    private final javax.swing.JList<StudyShape> shapeList = new javax.swing.JList<>(shapeListModel);

    /**
     * Focused shape list in the notation study mode.
     */
    private final javax.swing.JList<StudyShape> notationShapeList = new javax.swing.JList<>(shapeListModel);

    /**
     * Comment before field.
     */
    private final javax.swing.JTextArea commentBeforeArea = new javax.swing.JTextArea(4, 24);

    /**
     * Comment after field.
     */
    private final javax.swing.JTextArea commentAfterArea = new javax.swing.JTextArea(4, 24);

    /**
     * Focused comment-before field in the comments study mode.
     */
    private final javax.swing.JTextArea commentBeforeFocusArea = new javax.swing.JTextArea(8, 34);

    /**
     * Focused comment-after field in the comments study mode.
     */
    private final javax.swing.JTextArea commentAfterFocusArea = new javax.swing.JTextArea(8, 34);

    /**
     * Shape origin field.
     */
    private final javax.swing.JTextField shapeOriginField = new javax.swing.JTextField("e2", 4);

    /**
     * Shape destination field.
     */
    private final javax.swing.JTextField shapeDestinationField = new javax.swing.JTextField("e4", 4);

    /**
     * Focused shape origin field in the notation study mode.
     */
    private final javax.swing.JTextField notationShapeOriginField = new javax.swing.JTextField("e2", 4);

    /**
     * Focused shape destination field in the notation study mode.
     */
    private final javax.swing.JTextField notationShapeDestinationField = new javax.swing.JTextField("e4", 4);

    /**
     * Shape type selector.
     */
    private final javax.swing.JComboBox<StudyShape.Type> shapeTypeBox =
            new javax.swing.JComboBox<>(StudyShape.Type.values());

    /**
     * Focused shape type selector in the notation study mode.
     */
    private final javax.swing.JComboBox<StudyShape.Type> notationShapeTypeBox =
            new javax.swing.JComboBox<>(StudyShape.Type.values());

    /**
     * Shape brush selector.
     */
    private final javax.swing.JComboBox<StudyShape.Brush> shapeBrushBox =
            new javax.swing.JComboBox<>(StudyShape.Brush.values());

    /**
     * Focused shape brush selector in the notation study mode.
     */
    private final javax.swing.JComboBox<StudyShape.Brush> notationShapeBrushBox =
            new javax.swing.JComboBox<>(StudyShape.Brush.values());

    /**
     * Chapter mode selector.
     */
    private final javax.swing.JComboBox<StudyChapterMode> modeBox =
            new javax.swing.JComboBox<>(StudyChapterMode.values());

    /**
     * Orientation selector.
     */
    private final javax.swing.JComboBox<String> orientationBox =
            new javax.swing.JComboBox<>(new String[] { "auto", "white", "black" });

    /**
     * Conceal toggle.
     */
    private final javax.swing.JCheckBox concealFutureBox =
            new javax.swing.JCheckBox("Conceal future moves");

    /**
     * Evaluation bar toggle for the chapter-board study mode.
     */
    private final javax.swing.JCheckBox evaluationBarsBox =
            new javax.swing.JCheckBox("Evaluation bars", true);

    /**
     * Status label.
     */
    private final javax.swing.JLabel statusLabel = Ui.label("No study loaded");

    /**
     * Chapter analysis status label.
     */
    private final javax.swing.JLabel analysisStatusLabel = Ui.label("");

    /**
     * Chapter-board overview text.
     */
    private final javax.swing.JTextArea boardOverviewArea = new javax.swing.JTextArea(8, 40);

    /**
     * Share/export PGN preview text.
     */
    private final javax.swing.JTextArea sharePgnArea = new javax.swing.JTextArea(10, 40);

    /**
     * Study information text.
     */
    private final javax.swing.JTextArea infoArea = new javax.swing.JTextArea(10, 40);

    /**
     * Study module mode tabs.
     */
    private JTabbedPane studyModeTabs;

    /**
     * Active project.
     */
    private StudyProject project;

    /**
     * Active chapter.
     */
    private StudyChapter chapter;

    /**
     * Active tree model.
     */
    private final StudyTreeModel tree = new StudyTreeModel();

    /**
     * Guards inspector refreshes from document writes.
     */
    private boolean refreshing;

    /**
     * Creates a study workspace.
     *
     * @param repository local repository
     * @param gameSupplier current game supplier
     * @param fenSupplier current FEN supplier
     * @param showPosition board position callback
     * @param copyText clipboard callback
     */
    public StudyWorkspacePanel(StudyRepository repository, Supplier<GameModel> gameSupplier,
            Supplier<String> fenSupplier, Consumer<Position> showPosition, Consumer<String> copyText) {
        super(new BorderLayout(Theme.SPACE_SM, Theme.SPACE_SM));
        this.repository = repository == null ? StudyRepository.defaultRepository() : repository;
        this.gameSupplier = gameSupplier == null ? GameModel::new : gameSupplier;
        this.fenSupplier = fenSupplier == null ? () -> Game.STANDARD_START_FEN : fenSupplier;
        this.showPosition = showPosition == null ? position -> { } : showPosition;
        this.copyText = copyText == null ? text -> { } : copyText;
        configure();
        newInMemoryStudy();
    }

    /**
     * Adds a board move to the active study when the workspace is active.
     *
     * @param before position before the move
     * @param move encoded move
     * @return true when the study accepted the move
     */
    public boolean addBoardMove(Position before, short move) {
        if (chapter == null || move == Move.NO_MOVE) {
            return false;
        }
        try {
            if (before != null && !before.toString().equals(tree.currentPosition().toString())) {
                status("Study position is not on the board path");
                return false;
            }
            tree.addLegalMove(move);
            refreshTree();
            selectPath(tree.currentPath());
            status("Added " + Move.toString(move) + " to study");
            return true;
        } catch (RuntimeException ex) {
            status("Study move ignored: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Returns the active chapter count.
     *
     * @return chapter count
     */
    public int chapterCount() {
        return project == null ? 0 : project.chapters().size();
    }

    /**
     * Returns the active PGN export text.
     *
     * @return PGN text
     */
    public String exportPgnText() {
        return project == null ? "" : Pgn.toPgnString(project.chapters().stream()
                .map(StudyChapter::game)
                .toList());
    }

    /**
     * Configures the panel.
     */
    private void configure() {
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setBorder(Theme.pad(Theme.SPACE_MD));
        getAccessibleContext().setAccessibleName("Study Workspace");
        getAccessibleContext().setAccessibleDescription("Offline PGN-backed local study editor.");
        configureControls();
        add(createToolbar(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);
    }

    /**
     * Configures child controls.
     */
    private void configureControls() {
        Theme.list(chapterList);
        Theme.list(shapeList);
        Theme.list(notationShapeList);
        Theme.table(moveTreeTable, 25);
        Ui.styleAreas(commentBeforeArea, commentAfterArea, commentBeforeFocusArea, commentAfterFocusArea,
                boardOverviewArea, sharePgnArea, infoArea);
        Ui.styleFields(shapeOriginField, shapeDestinationField, notationShapeOriginField,
                notationShapeDestinationField);
        Ui.styleCombos(shapeTypeBox, shapeBrushBox, notationShapeTypeBox, notationShapeBrushBox,
                modeBox, orientationBox);
        chapterList.getAccessibleContext().setAccessibleName("Study chapters");
        moveTreeTable.getAccessibleContext().setAccessibleName("Study move tree");
        commentBeforeArea.getAccessibleContext().setAccessibleName("Comment before move");
        commentAfterArea.getAccessibleContext().setAccessibleName("Comment after move");
        commentBeforeFocusArea.getAccessibleContext().setAccessibleName("Focused comment before move");
        commentAfterFocusArea.getAccessibleContext().setAccessibleName("Focused comment after move");
        shapeList.getAccessibleContext().setAccessibleName("Study shapes");
        notationShapeList.getAccessibleContext().setAccessibleName("Focused study shapes");
        modeBox.getAccessibleContext().setAccessibleName("Chapter mode");
        orientationBox.getAccessibleContext().setAccessibleName("Chapter orientation");
        concealFutureBox.getAccessibleContext().setAccessibleName("Conceal future study moves");
        evaluationBarsBox.getAccessibleContext().setAccessibleName("Study board evaluation bars");
        configureReadOnlyArea(boardOverviewArea, "Study board overview");
        configureReadOnlyArea(sharePgnArea, "Study PGN preview");
        configureReadOnlyArea(infoArea, "Study information");
        analysisStatusLabel.getAccessibleContext().setAccessibleName("Study analysis status");
        chapterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        moveTreeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        moveTreeTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        moveTreeTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectMoveRow(moveTreeTable.getSelectedRow());
            }
        });
        chapterList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectChapter(chapterList.getSelectedValue());
            }
        });
        modeBox.addActionListener(event -> updateChapterMode());
        orientationBox.addActionListener(event -> updateChapterMode());
        evaluationBarsBox.addActionListener(event -> refreshStudyModeSummaries());
        installDirtyCommentListeners();
    }

    /**
     * Configures a read-only study text area.
     *
     * @param area area to configure
     * @param accessibleName accessible name
     */
    private static void configureReadOnlyArea(javax.swing.JTextArea area, String accessibleName) {
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.getAccessibleContext().setAccessibleName(accessibleName);
    }

    /**
     * Creates the toolbar.
     *
     * @return toolbar
     */
    private JComponent createToolbar() {
        // A wrapping row so the study rail can narrow beside the board without
        // a fixed ~7-button-wide minimum forcing the board column to collapse.
        JPanel toolbar = Ui.transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, Theme.SPACE_XS));
        toolbar.add(Ui.label("✓ SYNC"));
        toolbar.add(Ui.label("✓ REC"));
        toolbar.add(statusLabel);
        toolbar.add(Ui.button("New", false, event -> createStudy()));
        toolbar.add(Ui.button("Open", false, event -> openStudy()));
        toolbar.add(Ui.button("Save", true, event -> saveStudy()));
        toolbar.add(Ui.button("Save As", false, event -> saveStudyAs()));
        toolbar.add(Ui.button("Import PGN", false, event -> importPgn()));
        toolbar.add(Ui.button("Import Review", false, event -> importReviewUnits()));
        toolbar.add(Ui.button("Export PGN", false, event -> exportPgn()));
        return toolbar;
    }

    /**
     * Creates the body.
     *
     * @return body component
     */
    private JComponent createBody() {
        studyModeTabs = Ui.tabbedPane();
        studyModeTabs.getAccessibleContext().setAccessibleName("Study module modes");
        studyModeTabs.addTab("Editor", createEditorBody());
        studyModeTabs.addTab("Comments", createCommentsMode());
        studyModeTabs.addTab("Notation", createNotationMode());
        studyModeTabs.addTab("Analysis", createAnalysisMode());
        studyModeTabs.addTab("Boards", createBoardsMode());
        studyModeTabs.addTab("Share", createShareMode());
        studyModeTabs.addTab("Info", createInfoMode());
        studyModeTabs.addChangeListener(event -> refreshStudyModeSummaries());
        return studyModeTabs;
    }

    /**
     * Creates the primary study editor body.
     *
     * @return editor body component
     */
    private JComponent createEditorBody() {
        // Stacked vertically so the editor reads as a board-side rail (chapters
        // band on top, the move tree + inspector filling the rest) rather than
        // three cramped columns.
        javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT,
                createChapterPanel(), createEditorPanel());
        split.setResizeWeight(0.0d);
        split.setDividerLocation(170);
        SplitPaneStyler.style(split);
        return split;
    }

    /**
     * Creates the focused comments mode.
     *
     * @return comments mode component
     */
    private JComponent createCommentsMode() {
        JPanel stack = Ui.transparentPanel(null);
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.add(Ui.titled("Comment before", Ui.scroll(commentBeforeFocusArea)));
        stack.add(Ui.titled("Comment after", Ui.scroll(commentAfterFocusArea)));
        stack.add(Ui.controlRow(FlowLayout.LEFT,
                Ui.button("Apply", true, event -> applyFocusedComments()),
                Ui.button("Clear", false, event -> clearFocusedComments())));
        return Ui.scroll(stack);
    }

    /**
     * Creates the study notation mode.
     *
     * @return notation mode component
     */
    private JComponent createNotationMode() {
        JPanel stack = Ui.transparentPanel(null);
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.add(createNagPanel());
        stack.add(Ui.titled("Shapes", Ui.scroll(notationShapeList)));
        stack.add(Ui.controlRow(FlowLayout.LEFT, notationShapeTypeBox, notationShapeBrushBox));
        stack.add(Ui.controlRow(FlowLayout.LEFT,
                Ui.label("From"), notationShapeOriginField,
                Ui.label("To"), notationShapeDestinationField,
                Ui.button("Add Shape", false, event -> addNotationShape()),
                Ui.destructiveButton("Remove", event -> removeNotationShape())));
        return Ui.scroll(stack);
    }

    /**
     * Creates the study analysis mode.
     *
     * @return analysis mode component
     */
    private JComponent createAnalysisMode() {
        JPanel panel = Ui.transparentPanel(new BorderLayout());
        analysisStatusLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        panel.add(analysisStatusLabel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the chapter board overview mode.
     *
     * @return boards mode component
     */
    private JComponent createBoardsMode() {
        JPanel panel = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        panel.add(Ui.controlRow(FlowLayout.RIGHT, evaluationBarsBox), BorderLayout.NORTH);
        panel.add(Ui.scroll(boardOverviewArea), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the share/export mode.
     *
     * @return share mode component
     */
    private JComponent createShareMode() {
        JPanel panel = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        panel.add(Ui.controlRow(FlowLayout.LEFT,
                Ui.button("Copy PGN", false, event -> copyText.accept(exportPgnText())),
                Ui.button("Export PGN", false, event -> exportPgn())), BorderLayout.NORTH);
        panel.add(Ui.scroll(sharePgnArea), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the study information mode.
     *
     * @return info mode component
     */
    private JComponent createInfoMode() {
        return Ui.scroll(infoArea);
    }

    /**
     * Creates the chapter panel.
     *
     * @return chapter panel
     */
    private JComponent createChapterPanel() {
        JPanel panel = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        panel.setPreferredSize(new Dimension(CHAPTER_WIDTH, 150));
        panel.add(Ui.scroll(chapterList), BorderLayout.CENTER);
        panel.add(Ui.controlRow(FlowLayout.LEFT,
                Ui.button("Add", false, event -> addCurrentLineChapter()),
                Ui.button("FEN", false, event -> addFenChapter()),
                Ui.button("Rename", false, event -> renameChapter()),
                Ui.destructiveButton("Delete", event -> deleteChapter()),
                Ui.button("Up", false, event -> moveChapter(-1)),
                Ui.button("Down", false, event -> moveChapter(1))), BorderLayout.SOUTH);
        return Ui.card("Chapters", panel);
    }

    /**
     * Creates the main editor.
     *
     * @return editor panel
     */
    private JComponent createEditorPanel() {
        javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT,
                createMoveTreePanel(), createInspectorPanel());
        split.setResizeWeight(0.5d);
        split.setDividerLocation(300);
        SplitPaneStyler.style(split);
        return split;
    }

    /**
     * Creates the move tree panel.
     *
     * @return move tree panel
     */
    private JComponent createMoveTreePanel() {
        JPanel panel = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        panel.add(Ui.scroll(moveTreeTable), BorderLayout.CENTER);
        panel.add(Ui.controlRow(FlowLayout.LEFT,
                Ui.button("Promote", false, event -> promoteVariation()),
                Ui.button("Promote Up", false, event -> promoteVariationOneStep()),
                Ui.destructiveButton("Delete Branch", event -> deleteBranch()),
                Ui.button("Copy PGN", false, event -> copyText.accept(tree.toPgn()))), BorderLayout.SOUTH);
        return Ui.card("Move Tree", panel);
    }

    /**
     * Creates the annotation inspector.
     *
     * @return inspector panel
     */
    private JComponent createInspectorPanel() {
        JPanel panel = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        panel.setPreferredSize(new Dimension(360, 420));
        JPanel stack = Ui.transparentPanel(null);
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.add(Ui.titled("Comment before", Ui.scroll(commentBeforeArea)));
        stack.add(Ui.titled("Comment after", Ui.scroll(commentAfterArea)));
        stack.add(Ui.controlRow(FlowLayout.LEFT,
                Ui.button("Apply", true, event -> applyComments()),
                Ui.button("Clear", false, event -> clearComments())));
        stack.add(createNagPanel());
        stack.add(createShapePanel());
        stack.add(createModePanel());
        panel.add(Ui.scroll(stack), BorderLayout.CENTER);
        return Ui.card("Annotation Inspector", panel);
    }

    /**
     * Creates the NAG palette.
     *
     * @return NAG panel
     */
    private JComponent createNagPanel() {
        JPanel panel = Ui.transparentPanel(new java.awt.GridLayout(0, 1, 0, Theme.SPACE_XS));
        panel.add(nagRow("Move", NagCatalog.Category.MOVE_ASSESSMENT));
        panel.add(nagRow("Position", NagCatalog.Category.POSITION_ASSESSMENT));
        panel.add(nagRow("Observation", NagCatalog.Category.OBSERVATION));
        return Ui.titled("NAGs", panel);
    }

    /**
     * Creates one NAG row.
     *
     * @param title row title
     * @param category NAG category
     * @return row
     */
    private JComponent nagRow(String title, NagCatalog.Category category) {
        JPanel row = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_XS, 0));
        row.add(Ui.caption(title));
        for (NagCatalog.Entry entry : NagCatalog.entries(category)) {
            javax.swing.JButton button = Ui.button(entry.symbol(), false,
                    event -> toggleNag(entry.code()));
            button.setToolTipText("$" + entry.code() + " " + entry.label());
            row.add(button);
        }
        return row;
    }

    /**
     * Creates the shapes panel.
     *
     * @return shapes panel
     */
    private JComponent createShapePanel() {
        JPanel panel = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        panel.add(Ui.scroll(shapeList), BorderLayout.CENTER);
        JPanel controls = Ui.transparentPanel(new java.awt.GridLayout(0, 1, 0, Theme.SPACE_XS));
        controls.add(Ui.controlRow(FlowLayout.LEFT, shapeTypeBox, shapeBrushBox));
        controls.add(Ui.controlRow(FlowLayout.LEFT,
                Ui.label("From"), shapeOriginField,
                Ui.label("To"), shapeDestinationField,
                Ui.button("Add Shape", false, event -> addShape()),
                Ui.destructiveButton("Remove", event -> removeShape())));
        panel.add(controls, BorderLayout.SOUTH);
        return Ui.titled("Arrows / circles", panel);
    }

    /**
     * Creates the mode and practice panel.
     *
     * @return mode panel
     */
    private JComponent createModePanel() {
        JPanel panel = Ui.transparentPanel(new java.awt.GridLayout(0, 1, 0, Theme.SPACE_XS));
        panel.add(Ui.labelControlRow("Mode", modeBox, 82));
        panel.add(Ui.labelControlRow("Board", orientationBox, 82));
        panel.add(Ui.controlRow(FlowLayout.LEFT, concealFutureBox,
                Ui.button("Guess next move", false, event -> guessNextMove())));
        return Ui.titled("Practice", panel);
    }

    /**
     * Creates an unsaved local project.
     */
    private void newInMemoryStudy() {
        Game game = new Game();
        game.putTag("Event", "Workbench Study");
        game.putTag("Site", "ChessRTK");
        game.putTag("Result", "*");
        game.putTag("ChapterName", "Chapter 1");
        loadProject(new StudyProject(null, "Workbench Study",
                List.of(new StudyChapter("chapter-001", "Chapter 1", 0, game,
                        StudyChapterMode.NORMAL, "auto", "")),
                java.util.Map.of()));
        status("New unsaved study");
    }

    /**
     * Creates a repository-backed study.
     */
    private void createStudy() {
        try {
            loadProject(repository.createStudy("Workbench Study"));
            status("Created local study");
        } catch (IOException ex) {
            status("Create failed: " + ex.getMessage());
        }
    }

    /**
     * Opens a PGN study.
     */
    private void openStudy() {
        JFileChooser chooser = FileDialogs.createFileChooser("Open Study", null,
                new FileNameExtensionFilter("PGN studies", "pgn"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            loadProject(repository.openStudy(chooser.getSelectedFile().toPath()));
            status("Opened " + chooser.getSelectedFile().getName());
        } catch (IOException ex) {
            status("Open failed: " + ex.getMessage());
        }
    }

    /**
     * Saves the study.
     */
    private void saveStudy() {
        if (project == null) {
            return;
        }
        try {
            repository.saveStudy(project);
            status("Saved " + project.pgnPath());
        } catch (IOException ex) {
            status("Save failed: " + ex.getMessage());
        }
    }

    /**
     * Saves the study as a new PGN.
     */
    private void saveStudyAs() {
        if (project == null) {
            return;
        }
        JFileChooser chooser = FileDialogs.createFileChooser("Save Study As", new File("study.pgn"),
                new FileNameExtensionFilter("PGN studies", "pgn"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            File file = FileDialogs.ensureExtension(chooser.getSelectedFile(), ".pgn");
            repository.saveStudyAs(project, file.toPath());
            status("Saved " + file.getName());
        } catch (IOException ex) {
            status("Save As failed: " + ex.getMessage());
        }
    }

    /**
     * Imports PGN chapters.
     */
    private void importPgn() {
        if (project == null) {
            newInMemoryStudy();
        }
        JFileChooser chooser = FileDialogs.createFileChooser("Import PGN", null,
                new FileNameExtensionFilter("PGN files", "pgn"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            int count = repository.importChaptersFromPgn(project, chooser.getSelectedFile().toPath());
            refreshChapters();
            status("Imported " + count + " PGN chapter(s)");
        } catch (IOException ex) {
            status("Import failed: " + ex.getMessage());
        }
    }

    /**
     * Imports review study-unit JSONL rows.
     */
    private void importReviewUnits() {
        if (project == null) {
            newInMemoryStudy();
        }
        JFileChooser chooser = FileDialogs.createFileChooser("Import Review Study Units", null,
                new FileNameExtensionFilter("JSONL files", "jsonl"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            int count = repository.importStudyUnitsFromJsonl(project, chooser.getSelectedFile().toPath());
            refreshChapters();
            status("Imported " + count + " review chapter(s)");
        } catch (IOException ex) {
            status("Review import failed: " + ex.getMessage());
        }
    }

    /**
     * Exports PGN text.
     */
    private void exportPgn() {
        JFileChooser chooser = FileDialogs.createFileChooser("Export Study PGN", new File("study-export.pgn"),
                new FileNameExtensionFilter("PGN files", "pgn"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = FileDialogs.ensureExtension(chooser.getSelectedFile(), ".pgn");
        try {
            Files.writeString(file.toPath(), exportPgnText(), StandardCharsets.UTF_8);
            status("Exported " + file.getName());
        } catch (IOException ex) {
            status("Export failed: " + ex.getMessage());
        }
    }

    /**
     * Adds the current game line as a new study chapter (the inline
     * "make into study" action) and selects it.
     */
    public void addCurrentGameAsChapter() {
        addCurrentLineChapter();
    }

    /**
     * Adds a chapter from the current game line.
     */
    private void addCurrentLineChapter() {
        repository.addChapterFromCurrentLine(project, "Current Line", gameSupplier.get());
        refreshChapters();
        selectLastChapter();
        status("Added current line chapter");
    }

    /**
     * Adds a chapter from the current FEN.
     */
    private void addFenChapter() {
        repository.addChapterFromFen(project, "FEN Chapter", fenSupplier.get());
        refreshChapters();
        selectLastChapter();
        status("Added FEN chapter");
    }

    /**
     * Renames the selected chapter.
     */
    private void renameChapter() {
        if (chapter == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Chapter name", chapter.name());
        if (name != null && repository.renameChapter(project, chapter.id(), name)) {
            refreshChapters();
            status("Renamed chapter");
        }
    }

    /**
     * Deletes the selected chapter.
     */
    private void deleteChapter() {
        if (chapter == null || project.chapters().isEmpty()) {
            return;
        }
        repository.deleteChapter(project, chapter.id());
        if (project.mutableChapters().isEmpty()) {
            repository.addChapterFromFen(project, "Chapter 1", Game.STANDARD_START_FEN);
        }
        refreshChapters();
        chapterList.setSelectedIndex(0);
        status("Deleted chapter");
    }

    /**
     * Moves selected chapter.
     *
     * @param delta index delta
     */
    private void moveChapter(int delta) {
        if (chapter == null) {
            return;
        }
        int index = chapter.order();
        if (repository.reorderChapter(project, chapter.id(), index + delta)) {
            refreshChapters();
            chapterList.setSelectedIndex(Math.max(0, Math.min(index + delta, chapterListModel.size() - 1)));
            status("Reordered chapter");
        }
    }

    /**
     * Loads a project into the panel.
     *
     * @param nextProject project
     */
    private void loadProject(StudyProject nextProject) {
        project = Objects.requireNonNull(nextProject, "nextProject");
        refreshChapters();
        if (!project.chapters().isEmpty()) {
            chapterList.setSelectedIndex(0);
        }
    }

    /**
     * Refreshes chapter list.
     */
    private void refreshChapters() {
        chapterListModel.clear();
        if (project == null) {
            return;
        }
        for (StudyChapter item : project.chapters()) {
            chapterListModel.addElement(item);
        }
        refreshStudyModeSummaries();
    }

    /**
     * Selects the last chapter.
     */
    private void selectLastChapter() {
        if (!chapterListModel.isEmpty()) {
            chapterList.setSelectedIndex(chapterListModel.size() - 1);
        }
    }

    /**
     * Selects a chapter.
     *
     * @param nextChapter chapter
     */
    private void selectChapter(StudyChapter nextChapter) {
        if (nextChapter == null) {
            return;
        }
        chapter = nextChapter;
        tree.load(chapter.game());
        modeBox.setSelectedItem(chapter.mode());
        orientationBox.setSelectedItem(chapter.orientation());
        refreshTree();
        showPosition.accept(tree.currentPosition());
        refreshInspector();
        refreshStudyModeSummaries();
    }

    /**
     * Refreshes the move tree table.
     */
    private void refreshTree() {
        moveTreeModel.setRows(tree.rows());
        refreshStudyModeSummaries();
    }

    /**
     * Selects a row by path.
     *
     * @param path path
     */
    private void selectPath(StudyNodePath path) {
        for (int row = 0; row < moveTreeModel.getRowCount(); row++) {
            if (moveTreeModel.row(row).path().equals(path)) {
                moveTreeTable.getSelectionModel().setSelectionInterval(row, row);
                return;
            }
        }
        refreshInspector();
    }

    /**
     * Selects a move row.
     *
     * @param row row index
     */
    private void selectMoveRow(int row) {
        if (row < 0 || row >= moveTreeModel.getRowCount()) {
            return;
        }
        StudyTreeModel.Row modelRow = moveTreeModel.row(row);
        if (tree.select(modelRow.path())) {
            showPosition.accept(tree.currentPosition());
            refreshInspector();
        }
    }

    /**
     * Refreshes inspector fields.
     */
    private void refreshInspector() {
        refreshing = true;
        try {
            StudyNodePath path = tree.currentPath();
            commentBeforeArea.setText(tree.commentBefore(path));
            commentAfterArea.setText(path.isRoot() ? "" : tree.commentAfter(path));
            commentBeforeFocusArea.setText(tree.commentBefore(path));
            commentAfterFocusArea.setText(path.isRoot() ? "" : tree.commentAfter(path));
            refreshShapes();
        } finally {
            refreshing = false;
        }
    }

    /**
     * Refreshes the secondary study-mode summary views.
     */
    private void refreshStudyModeSummaries() {
        int moves = Math.max(0, moveTreeModel.getRowCount() - 1);
        analysisStatusLabel.setText(moves < 6
                ? "The chapter is too short to be analyzed."
                : "Chapter analysis ready: " + moves + " move row(s).");
        boardOverviewArea.setText(boardOverviewText());
        sharePgnArea.setText(exportPgnText());
        infoArea.setText(infoText());
    }

    /**
     * Builds the chapter-board overview text.
     *
     * @return chapter-board overview
     */
    private String boardOverviewText() {
        if (project == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        text.append(project.title()).append(System.lineSeparator());
        text.append("Chapters: ").append(project.chapters().size()).append(System.lineSeparator());
        text.append("Evaluation bars: ").append(evaluationBarsBox.isSelected() ? "on" : "off")
                .append(System.lineSeparator()).append(System.lineSeparator());
        for (StudyChapter item : project.chapters()) {
            text.append(item.order() + 1).append(". ").append(item.name())
                    .append(" · ").append(halfMoveCount(item.game())).append(" ply")
                    .append(System.lineSeparator());
        }
        return text.toString();
    }

    /**
     * Builds study information text.
     *
     * @return information text
     */
    private String infoText() {
        StringBuilder text = new StringBuilder();
        text.append(project == null ? "No study" : project.title()).append(System.lineSeparator());
        if (project != null) {
            text.append("Path: ").append(project.pgnPath() == null ? "unsaved" : project.pgnPath())
                    .append(System.lineSeparator());
            text.append("Chapters: ").append(project.chapters().size()).append(System.lineSeparator());
        }
        if (chapter != null) {
            text.append("Active chapter: ").append(chapter.name()).append(System.lineSeparator());
            text.append("Mode: ").append(chapter.mode()).append(System.lineSeparator());
            text.append("Board: ").append(chapter.orientation()).append(System.lineSeparator());
            text.append("Moves: ").append(Math.max(0, moveTreeModel.getRowCount() - 1)).append(System.lineSeparator());
        }
        return text.toString();
    }

    /**
     * Counts half moves in a chapter game.
     *
     * @param game chapter game
     * @return half-move count
     */
    private static int halfMoveCount(Game game) {
        return game == null ? 0 : Math.max(0, new StudyTreeModel(game).rows().size() - 1);
    }

    /**
     * Refreshes shape list.
     */
    private void refreshShapes() {
        shapeListModel.clear();
        for (StudyShape shape : tree.shapes(tree.currentPath())) {
            shapeListModel.addElement(shape);
        }
    }

    /**
     * Applies comment fields.
     */
    private void applyComments() {
        if (tree.currentPath().isRoot()) {
            tree.setCommentBefore(StudyNodePath.root(), commentBeforeArea.getText());
        } else {
            tree.setCommentBefore(tree.currentPath(), commentBeforeArea.getText());
            tree.setCommentAfter(tree.currentPath(), commentAfterArea.getText());
        }
        refreshTree();
        refreshInspector();
        refreshStudyModeSummaries();
        status("Updated comments");
    }

    /**
     * Applies focused comment-mode fields.
     */
    private void applyFocusedComments() {
        if (tree.currentPath().isRoot()) {
            tree.setCommentBefore(StudyNodePath.root(), commentBeforeFocusArea.getText());
        } else {
            tree.setCommentBefore(tree.currentPath(), commentBeforeFocusArea.getText());
            tree.setCommentAfter(tree.currentPath(), commentAfterFocusArea.getText());
        }
        refreshTree();
        refreshInspector();
        refreshStudyModeSummaries();
        status("Updated comments");
    }

    /**
     * Clears comment fields.
     */
    private void clearComments() {
        commentBeforeArea.setText("");
        commentAfterArea.setText("");
        applyComments();
    }

    /**
     * Clears focused comment-mode fields.
     */
    private void clearFocusedComments() {
        commentBeforeFocusArea.setText("");
        commentAfterFocusArea.setText("");
        applyFocusedComments();
    }

    /**
     * Toggles one NAG.
     *
     * @param nag NAG code
     */
    private void toggleNag(int nag) {
        if (tree.currentPath().isRoot()) {
            return;
        }
        tree.toggleNag(tree.currentPath(), nag);
        refreshTree();
        status("Updated NAGs");
    }

    /**
     * Adds a graphical shape.
     */
    private void addShape() {
        if (tree.currentPath().isRoot()) {
            return;
        }
        try {
            List<StudyShape> shapes = new java.util.ArrayList<>(tree.shapes(tree.currentPath()));
            StudyShape.Type type = (StudyShape.Type) shapeTypeBox.getSelectedItem();
            StudyShape.Brush brush = (StudyShape.Brush) shapeBrushBox.getSelectedItem();
            shapes.add(type == StudyShape.Type.CIRCLE
                    ? StudyShape.circle(brush, shapeOriginField.getText())
                    : StudyShape.arrow(brush, shapeOriginField.getText(), shapeDestinationField.getText()));
            tree.setShapes(tree.currentPath(), shapes);
            refreshShapes();
            refreshTree();
            refreshStudyModeSummaries();
            status("Added shape");
        } catch (IllegalArgumentException ex) {
            status("Shape failed: " + ex.getMessage());
        }
    }

    /**
     * Adds a graphical shape from the focused notation mode.
     */
    private void addNotationShape() {
        if (tree.currentPath().isRoot()) {
            return;
        }
        try {
            List<StudyShape> shapes = new java.util.ArrayList<>(tree.shapes(tree.currentPath()));
            StudyShape.Type type = (StudyShape.Type) notationShapeTypeBox.getSelectedItem();
            StudyShape.Brush brush = (StudyShape.Brush) notationShapeBrushBox.getSelectedItem();
            shapes.add(type == StudyShape.Type.CIRCLE
                    ? StudyShape.circle(brush, notationShapeOriginField.getText())
                    : StudyShape.arrow(brush, notationShapeOriginField.getText(),
                            notationShapeDestinationField.getText()));
            tree.setShapes(tree.currentPath(), shapes);
            refreshShapes();
            refreshTree();
            refreshStudyModeSummaries();
            status("Added shape");
        } catch (IllegalArgumentException ex) {
            status("Shape failed: " + ex.getMessage());
        }
    }

    /**
     * Removes the selected shape.
     */
    private void removeShape() {
        int index = shapeList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        List<StudyShape> shapes = new java.util.ArrayList<>(tree.shapes(tree.currentPath()));
        if (index < shapes.size()) {
            shapes.remove(index);
            tree.setShapes(tree.currentPath(), shapes);
            refreshShapes();
            refreshTree();
            refreshStudyModeSummaries();
            status("Removed shape");
        }
    }

    /**
     * Removes the selected focused notation-mode shape.
     */
    private void removeNotationShape() {
        int index = notationShapeList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        List<StudyShape> shapes = new java.util.ArrayList<>(tree.shapes(tree.currentPath()));
        if (index < shapes.size()) {
            shapes.remove(index);
            tree.setShapes(tree.currentPath(), shapes);
            refreshShapes();
            refreshTree();
            refreshStudyModeSummaries();
            status("Removed shape");
        }
    }

    /**
     * Updates chapter mode fields.
     */
    private void updateChapterMode() {
        if (refreshing || chapter == null) {
            return;
        }
        chapter.setMode((StudyChapterMode) modeBox.getSelectedItem());
        chapter.setOrientation(String.valueOf(orientationBox.getSelectedItem()));
        status("Updated chapter mode");
    }

    /**
     * Promotes the selected variation.
     */
    private void promoteVariation() {
        if (tree.promoteVariation()) {
            refreshTree();
            status("Promoted variation");
        }
    }

    /**
     * Promotes the selected variation one step.
     */
    private void promoteVariationOneStep() {
        if (tree.promoteVariationOneStep()) {
            refreshTree();
            status("Promoted variation upward");
        }
    }

    /**
     * Deletes the selected branch.
     */
    private void deleteBranch() {
        if (tree.deleteBranch()) {
            refreshTree();
            refreshInspector();
            status("Deleted branch");
        }
    }

    /**
     * Enters a simple guess-next-move practice prompt.
     */
    private void guessNextMove() {
        StudyNodePath next = tree.nextMainlinePath();
        if (next == null) {
            status("No next move in this chapter");
            return;
        }
        for (StudyTreeModel.Row row : tree.rows()) {
            if (row.path().equals(next)) {
                status("Guess next move: " + (concealFutureBox.isSelected() ? "concealed" : row.san()));
                return;
            }
        }
        status("Guess next move enabled");
    }

    /**
     * Installs lightweight dirty markers for comments.
     */
    private void installDirtyCommentListeners() {
        DocumentListener listener = new DocumentListener() {
            /**
             * Handles insertion.
             *
             * @param event document event
             */
            @Override
            public void insertUpdate(DocumentEvent event) {
                dirtyComment();
            }

            /**
             * Handles removal.
             *
             * @param event document event
             */
            @Override
            public void removeUpdate(DocumentEvent event) {
                dirtyComment();
            }

            /**
             * Handles attribute changes.
             *
             * @param event document event
             */
            @Override
            public void changedUpdate(DocumentEvent event) {
                dirtyComment();
            }
        };
        commentBeforeArea.getDocument().addDocumentListener(listener);
        commentAfterArea.getDocument().addDocumentListener(listener);
        commentBeforeFocusArea.getDocument().addDocumentListener(listener);
        commentAfterFocusArea.getDocument().addDocumentListener(listener);
    }

    /**
     * Marks comments as edited.
     */
    private void dirtyComment() {
        if (!refreshing) {
            status("Comments edited");
        }
    }

    /**
     * Updates the status label.
     *
     * @param text status text
     */
    private void status(String text) {
        statusLabel.setText(text == null ? "" : text);
    }

    /**
     * Move tree table model.
     */
    private static final class MoveTreeTableModel extends AbstractTableModel {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Column names.
         */
        private static final String[] COLUMNS = { "#", "Move", "NAG", "UCI", "Comment" };

        /**
         * Rows.
         */
        private List<StudyTreeModel.Row> rows = List.of();

        /**
         * Replaces rows.
         *
         * @param nextRows rows
         */
        private void setRows(List<StudyTreeModel.Row> nextRows) {
            rows = nextRows == null ? List.of() : List.copyOf(nextRows);
            fireTableDataChanged();
        }

        /**
         * Returns one row.
         *
         * @param row row index
         * @return row
         */
        private StudyTreeModel.Row row(int row) {
            return rows.get(row);
        }

        /**
         * Returns row count.
         *
         * @return row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns column count.
         *
         * @return column count
         */
        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        /**
         * Returns a column name.
         *
         * @param column column
         * @return column name
         */
        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        /**
         * Returns a cell value.
         *
         * @param rowIndex row
         * @param columnIndex column
         * @return value
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            StudyTreeModel.Row row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.moveNumber();
                case 1 -> "  ".repeat(Math.max(0, row.depth())) + row.san();
                case 2 -> NagCatalog.symbols(row.nags());
                case 3 -> row.uci();
                case 4 -> row.commentAfter();
                default -> "";
            };
        }
    }
}
