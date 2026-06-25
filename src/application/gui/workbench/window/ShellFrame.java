package application.gui.workbench.window;

import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.game.StudyRepository;
import application.gui.workbench.session.Job;
import application.gui.workbench.session.JobStatus;
import application.gui.workbench.session.Session;
import application.gui.workbench.ui.TagCloud;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.accessibility.AccessibleContext;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Reference-style shell frame around the editor: navigator, inspector, and
 * bottom run dock wrapped around the existing workbench tab system.
 */
final class ShellFrame extends JPanel {

    /**
     * Serialization identifier for Swing compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Left navigator width in pixels.
     */
    private static final int NAVIGATOR_WIDTH = 236;

    /**
     * Right inspector width in pixels.
     */
    private static final int INSPECTOR_WIDTH = 304;

    /**
     * Bottom run dock height in pixels.
     */
    private static final int RUN_DOCK_HEIGHT = 172;

    /**
     * Collapsed bottom run dock height in pixels.
     */
    private static final int RUN_DOCK_COLLAPSED_HEIGHT = 42;

    /**
     * Client property marking inspector blocks that keep elevated chrome.
     */
    private static final String CLIENT_ELEVATED_BLOCK = ShellFrame.class.getName() + ".elevatedBlock";

    /**
     * The editor tabs hosted in the center of the shell.
     */
    private final EditorSplitArea tabs;

    /**
     * Observable workbench session.
     */
    private final Session session;

    /**
     * Callback used by navigator rows.
     */
    private final IntConsumer selectTab;

    /**
     * Callback for opening the Console surface.
     */
    private final Runnable showConsole;

    /**
     * Callback for opening the Logs surface.
     */
    private final Runnable showLogs;

    /**
     * Callback for opening the latest run log.
     */
    private final Runnable openLatestLog;

    /**
     * Callback for opening the board Study authoring rail.
     */
    private final Runnable showStudies;

    /**
     * Local PGN-backed study repository.
     */
    private final StudyRepository studyRepository = StudyRepository.defaultRepository();

    /**
     * Navigator rows that mirror the selected editor tab.
     */
    private final List<NavigatorRow> navigatorRows = new ArrayList<>();

    /**
     * Inspector key/value host.
     */
    private final JPanel inspectorFields = verticalPanel();

    /**
     * Inspector tags.
     */
    private final TagCloud inspectorTags = new TagCloud(TagCloud.Mode.COMPACT);

    /**
     * Latest artifact rows shown in the inspector.
     */
    private final JPanel artifactRows = verticalPanel();

    /**
     * Run center rows shown in the bottom dock.
     */
    private final JPanel runRows = verticalPanel();

    /**
     * Split pane between the editor shell and the bottom run dock.
     */
    private JSplitPane mainAndRunSplit;

    /**
     * Bottom run dock shell.
     */
    private JComponent runDock;

    /**
     * Run dock body hidden when the dock is collapsed.
     */
    private JComponent runDockBody;

    /**
     * Run dock collapse/expand button.
     */
    private JButton runDockToggle;

    /**
     * Whether the bottom run dock is collapsed.
     */
    private boolean runDockCollapsed;

    /**
     * Run dock tab strip.
     */
    private final JComponent dockTabs = new DockTabs();

    /**
     * Latest command output preview.
     */
    private final JTextArea latestOutput = new JTextArea();

    /**
     * Creates the shell frame.
     *
     * @param tabs editor tabs to host
     * @param session session model
     * @param selectTab tab-selection callback
     * @param showConsole console callback
     * @param showLogs logs callback
     * @param openLatestLog latest-log callback
     */
    ShellFrame(EditorSplitArea tabs, Session session, IntConsumer selectTab,
            Runnable showConsole, Runnable showLogs, Runnable openLatestLog, Runnable showStudies) {
        super(new BorderLayout());
        this.tabs = tabs;
        this.session = session;
        this.selectTab = selectTab;
        this.showConsole = showConsole;
        this.showLogs = showLogs;
        this.openLatestLog = openLatestLog;
        this.showStudies = showStudies == null ? () -> selectTab.accept(WindowBase.TAB_BOARD) : showStudies;
        setOpaque(false);
        add(buildShell(), BorderLayout.CENTER);
        installSessionListeners();
        refreshSelection();
        refreshSession();
    }

    /**
     * Refreshes custom shell backgrounds after a theme switch.
     */
    void refreshTheme() {
        Theme.refreshComponentTree(this);
        refreshChromeBackgrounds(this);
        latestOutput.setBackground(Theme.TERMINAL);
        latestOutput.setForeground(Theme.TERMINAL_TEXT);
        for (NavigatorRow row : navigatorRows) {
            row.repaint();
        }
        refreshSession();
        revalidate();
        repaint();
    }

    /**
     * Refreshes navigator selection from the editor state.
     */
    void refreshSelection() {
        int selected = tabs == null ? -1 : tabs.selectedIndex();
        for (NavigatorRow row : navigatorRows) {
            row.setSelected(row.selectedForTab() && row.tabIndex() == selected);
        }
    }

    /**
     * Returns an intentionally blank navigator count.
     *
     * @return blank text
     */
    private String emptyCount() {
        return "";
    }

    /**
     * Returns a zero navigator count.
     *
     * @return zero text
     */
    private String zeroCount() {
        return "0";
    }

    /**
     * Returns whether the current session has an active game line.
     *
     * @return count text
     */
    private String currentGameCount() {
        return session.lastPly() > 0 || !session.fen().isBlank() ? "1" : "0";
    }

    /**
     * Returns the recent-job count.
     *
     * @return count text
     */
    private String jobHistoryCount() {
        return Integer.toString(session.jobs().size());
    }

    /**
     * Returns the recent artifact count.
     *
     * @return count text
     */
    private String artifactCount() {
        return Integer.toString(session.artifacts().recent().size());
    }

    /**
     * Returns the current tag count.
     *
     * @return count text
     */
    private String tagCount() {
        return Integer.toString(session.tags().size());
    }

    /**
     * Returns the number of active command runs.
     *
     * @return count text
     */
    private String activeRunCount() {
        int count = 0;
        for (Job job : session.jobs().recent()) {
            if (job != null && !job.status().isTerminal()) {
                count++;
            }
        }
        return Integer.toString(count);
    }

    /**
     * Counts current tags matching one of the requested tokens.
     *
     * @param tokens uppercase match tokens
     * @return count text
     */
    private String tagMatchCount(String... tokens) {
        int count = 0;
        for (String tag : session.tags()) {
            String upper = tag == null ? "" : tag.toUpperCase(Locale.ROOT);
            for (String token : tokens) {
                if (upper.contains(token)) {
                    count++;
                    break;
                }
            }
        }
        return Integer.toString(count);
    }

    /**
     * Builds the shell layout.
     *
     * @return shell component
     */
    private JComponent buildShell() {
        JComponent navigator = buildNavigator();
        JComponent inspector = buildInspector();
        JSplitPane editorAndInspector = SplitPaneStyler.styledHorizontalSplit(tabs, inspector, 0.78d);
        editorAndInspector.setResizeWeight(1.0d);
        editorAndInspector.setDividerLocation(0.78d);

        JSplitPane withNavigator = SplitPaneStyler.styledHorizontalSplit(navigator, editorAndInspector, 0.16d);
        withNavigator.setResizeWeight(0.0d);
        withNavigator.setDividerLocation(NAVIGATOR_WIDTH);

        runDock = buildRunDock();
        mainAndRunSplit = SplitPaneStyler.styledVerticalSplit(withNavigator, runDock, 0.80d);
        mainAndRunSplit.setResizeWeight(1.0d);
        mainAndRunSplit.setDividerLocation(0.80d);
        return mainAndRunSplit;
    }

    /**
     * Registers listeners for session-backed shell chrome.
     */
    private void installSessionListeners() {
        Runnable refresh = () -> SwingUtilities.invokeLater(this::refreshSession);
        session.addListener(changed -> refresh.run());
        session.jobs().addListener(refresh);
        session.artifacts().addListener(refresh);
    }

    /**
     * Builds the left navigator.
     *
     * @return navigator component
     */
    private JComponent buildNavigator() {
        JPanel panel = shellPanel();
        panel.setPreferredSize(new Dimension(NAVIGATOR_WIDTH, 1));
        panel.setMinimumSize(new Dimension(188, 1));
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE));

        JPanel content = verticalPanel();
        content.setBorder(Theme.pad(Theme.SPACE_MD, Theme.SPACE_MD, Theme.SPACE_SM, Theme.SPACE_MD));
        content.add(sectionLabel("NAVIGATOR"));
        content.add(navRow("Project Home", WindowBase.TAB_DASHBOARD, "Home",
                this::emptyCount, true));
        content.add(Box.createVerticalStrut(Theme.SPACE_MD));
        content.add(navigatorSection("LIBRARY", true,
                navRow("All Games", WindowBase.TAB_BOARD, "Open the current game and board workspace",
                        this::currentGameCount, true),
                navRow("Resume", WindowBase.TAB_LOGS, "Resume from recent persisted runs and logs",
                        this::jobHistoryCount, true),
                navRow("Imported", WindowBase.TAB_BOARD, "Open imported PGN/FEN content in Board",
                        this::artifactCount, false),
                navRow("Favorites", WindowBase.TAB_BOARD, "Saved-game workflow",
                        this::zeroCount, false),
                navRow("Duplicates", WindowBase.TAB_DATASETS, "Dataset and duplicate review tools",
                        this::zeroCount, false)));
        content.add(Box.createVerticalStrut(Theme.SPACE_SM));
        content.add(navigatorSection("STUDIES", true,
                navRow("My Studies", WindowBase.TAB_BOARD,
                        "Open local PGN-backed study books and create a starter study",
                        this::studyCount, false, this::openStudyLibrary),
                navRow("Endgames", WindowBase.TAB_BOARD, "Endgame-oriented tags and tablebase state",
                        () -> tagMatchCount("ENDGAME", "TABLEBASE"), false),
                navRow("Openings", WindowBase.TAB_BOARD, "Opening and ECO-oriented board tools",
                        () -> tagMatchCount("OPENING", "ECO"), false)));
        content.add(Box.createVerticalStrut(Theme.SPACE_SM));
        content.add(navigatorSection("PUZZLE SETS", false,
                navRow("Tactics", WindowBase.TAB_BOARD, "Tactical tags and solve mode",
                        () -> tagMatchCount("TACTIC", "THREAT", "CHECK"), false),
                navRow("Themes", WindowBase.TAB_BOARD, "Current position tag set",
                        this::tagCount, false)));
        content.add(Box.createVerticalStrut(Theme.SPACE_SM));
        content.add(navigatorSection("EXPERIMENTS", false,
                navRow("Engine Tests", WindowBase.TAB_ENGINE, "Engine Lab, search, and gauntlet tools",
                        this::jobHistoryCount, true),
                navRow("Model Comparisons", WindowBase.TAB_ENGINE, "Engine comparison workflows",
                        this::activeRunCount, false),
                navRow("Search Experiments", WindowBase.TAB_ENGINE, "MCTS and search-tree workflows",
                        this::activeRunCount, false)));
        content.add(Box.createVerticalStrut(Theme.SPACE_SM));
        content.add(navigatorSection("DATA", false,
                navRow("Datasets", WindowBase.TAB_DATASETS, "Training-data summaries and validation",
                        this::artifactCount, true),
                navRow("Transformations", WindowBase.TAB_DATASETS, "Dataset conversion outputs",
                        this::artifactCount, false)));
        content.add(Box.createVerticalStrut(Theme.SPACE_SM));
        content.add(navigatorSection("PUBLICATIONS", false,
                navRow("Books", WindowBase.TAB_PUBLISH, "Book and study rendering",
                        this::artifactCount, true),
                navRow("Articles", WindowBase.TAB_PUBLISH, "Report and article rendering",
                        this::artifactCount, false)));
        content.add(Box.createVerticalGlue());
        panel.add(Ui.scroll(content, () -> Theme.BG), BorderLayout.CENTER);
        panel.add(navigatorFooter(), BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Builds one navigator row.
     *
     * @param text row label
     * @param tabIndex target tab index
     * @param tooltip tooltip text
     * @param countSupplier row count supplier
     * @param selectedForTab whether this row mirrors tab selection
     * @return row component
     */
    private JComponent navRow(String text, int tabIndex, String tooltip,
            Supplier<String> countSupplier, boolean selectedForTab) {
        return navRow(text, tabIndex, tooltip, countSupplier, selectedForTab,
                () -> selectTab.accept(tabIndex));
    }

    /**
     * Builds one navigator row with a custom action.
     *
     * @param text row label
     * @param tabIndex target tab index
     * @param tooltip tooltip text
     * @param countSupplier row count supplier
     * @param selectedForTab whether this row mirrors tab selection
     * @param action row action
     * @return row component
     */
    private JComponent navRow(String text, int tabIndex, String tooltip,
            Supplier<String> countSupplier, boolean selectedForTab, Runnable action) {
        NavigatorRow row = new NavigatorRow(text, tabIndex, tooltip, countSupplier,
                selectedForTab, action);
        navigatorRows.add(row);
        return row;
    }

    /**
     * Builds one collapsible navigator section.
     *
     * @param title section title
     * @param expanded initial expanded state
     * @param rows section rows
     * @return section component
     */
    private static JComponent navigatorSection(String title, boolean expanded, JComponent... rows) {
        return new NavigatorSection(title, expanded, rows);
    }

    /**
     * Returns the local study-book count.
     *
     * @return count text
     */
    private String studyCount() {
        return Integer.toString(studyRepository.count());
    }

    /**
     * Creates a starter study when needed and opens the Study authoring rail.
     */
    private void openStudyLibrary() {
        try {
            studyRepository.ensureStarterStudy();
        } catch (IOException ex) {
            latestOutput.setText("Study repository failed: " + ex.getMessage());
        }
        showStudies.run();
        refreshSession();
    }

    /**
     * Builds the navigator footer.
     *
     * @return footer component
     */
    private JComponent navigatorFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, Theme.SPACE_SM));
        footer.setOpaque(true);
        footer.setBackground(Theme.BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));
        footer.add(Ui.iconButton("+", "Open Run", event -> selectTab.accept(WindowBase.TAB_RUN)));
        footer.add(Ui.iconButton(">", "Open Console", event -> showConsole.run()));
        footer.add(Ui.iconButton("#", "Open Logs", event -> showLogs.run()));
        return footer;
    }

    /**
     * Builds the right inspector.
     *
     * @return inspector component
     */
    private JComponent buildInspector() {
        JPanel panel = shellPanel();
        panel.setPreferredSize(new Dimension(INSPECTOR_WIDTH, 1));
        panel.setMinimumSize(new Dimension(240, 1));
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.LINE));

        JPanel content = verticalPanel();
        content.setBorder(Theme.pad(Theme.SPACE_MD, Theme.SPACE_MD, Theme.SPACE_MD, Theme.SPACE_MD));
        content.add(sectionLabel("INSPECTOR"));
        content.add(inspectorFields);
        content.add(Box.createVerticalGlue());

        panel.add(Ui.scroll(content, () -> Theme.BG), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Builds the bottom run dock.
     *
     * @return run dock component
     */
    private JComponent buildRunDock() {
        JPanel panel = shellPanel();
        panel.setPreferredSize(new Dimension(1, RUN_DOCK_HEIGHT));
        panel.setMinimumSize(new Dimension(1, 124));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));

        JPanel header = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
        header.setOpaque(false);
        header.setBorder(Theme.pad(Theme.SPACE_SM, Theme.SPACE_MD, Theme.SPACE_XS, Theme.SPACE_MD));
        header.add(sectionLabel("RUN CENTER"), BorderLayout.WEST);
        header.add(dockTabs, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        actions.setOpaque(false);
        runDockToggle = Ui.ghostButton("Collapse", event -> setRunDockCollapsed(!runDockCollapsed));
        runDockToggle.setName("workbench.runDock.toggle");
        runDockToggle.setActionCommand("workbench.runDock.toggle");
        describe(runDockToggle, "Collapse Run Center");
        actions.add(Ui.ghostButton("Console", event -> showConsole.run()));
        actions.add(Ui.ghostButton("Logs", event -> showLogs.run()));
        actions.add(runDockToggle);
        header.add(actions, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        JPanel runColumn = verticalPanel();
        runColumn.setBorder(Theme.pad(0, Theme.SPACE_MD, Theme.SPACE_MD, Theme.SPACE_MD));
        runColumn.add(new RunHeaderRow());
        runColumn.add(runRows);

        configureLatestOutput();
        JPanel outputColumn = new JPanel(new BorderLayout(0, 0));
        outputColumn.setOpaque(false);
        outputColumn.setBorder(Theme.pad(0, Theme.SPACE_MD, Theme.SPACE_MD, Theme.SPACE_MD));
        outputColumn.add(outputHeader(), BorderLayout.NORTH);
        outputColumn.add(Ui.scroll(latestOutput, () -> Theme.TERMINAL), BorderLayout.CENTER);

        JSplitPane split = SplitPaneStyler.styledHorizontalSplit(runColumn, outputColumn, 0.56d);
        split.setResizeWeight(0.56d);
        runDockBody = split;
        panel.add(runDockBody, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Collapses or expands the bottom run dock.
     *
     * @param collapsed true for compact header-only dock
     */
    private void setRunDockCollapsed(boolean collapsed) {
        runDockCollapsed = collapsed;
        if (runDockBody != null) {
            runDockBody.setVisible(!collapsed);
        }
        if (runDock != null) {
            int height = collapsed ? RUN_DOCK_COLLAPSED_HEIGHT : RUN_DOCK_HEIGHT;
            int minimum = collapsed ? RUN_DOCK_COLLAPSED_HEIGHT : 124;
            runDock.setPreferredSize(new Dimension(1, height));
            runDock.setMinimumSize(new Dimension(1, minimum));
        }
        if (runDockToggle != null) {
            runDockToggle.setText(collapsed ? "Expand" : "Collapse");
            describe(runDockToggle, collapsed ? "Expand Run Center" : "Collapse Run Center");
        }
        revalidate();
        repaint();
        SwingUtilities.invokeLater(this::resizeRunDock);
    }

    /**
     * Keeps the bottom split divider aligned with the run dock's current
     * preferred height.
     */
    private void resizeRunDock() {
        if (mainAndRunSplit == null || runDock == null || mainAndRunSplit.getHeight() <= 0) {
            return;
        }
        int dockHeight = runDock.getPreferredSize().height;
        mainAndRunSplit.setDividerLocation(Math.max(0, mainAndRunSplit.getHeight() - dockHeight));
    }

    /**
     * Styles the latest-output preview.
     */
    private void configureLatestOutput() {
        latestOutput.setEditable(false);
        latestOutput.setLineWrap(false);
        latestOutput.setRows(4);
        Theme.codeBlock(latestOutput);
        latestOutput.setBackground(Theme.TERMINAL);
        latestOutput.setForeground(Theme.TERMINAL_TEXT);
        latestOutput.setText("No command output yet.");
    }

    /**
     * Builds the latest-output header.
     *
     * @return header component
     */
    private static JComponent outputHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(Theme.pad(0, 0, Theme.SPACE_XS, 0));
        header.add(sectionLabel("LATEST OUTPUT"), BorderLayout.WEST);
        return header;
    }

    /**
     * Refreshes all session-derived shell panels.
     */
    private void refreshSession() {
        refreshInspector();
        refreshArtifacts();
        refreshRunRows();
        refreshLatestOutput();
        dockTabs.repaint();
        refreshSelection();
    }

    /**
     * Refreshes the right inspector.
     */
    private void refreshInspector() {
        inspectorFields.removeAll();
        inspectorFields.add(sectionLabel("LINES"));
        inspectorFields.add(lineCard("Main Line", lineSummary(), engineText()));
        inspectorFields.add(spacer());
        inspectorFields.add(sectionLabel("OPENING"));
        inspectorFields.add(infoRow("Current", openingText()));
        inspectorFields.add(infoRow("Phase", phaseText()));
        inspectorFields.add(openingBar());
        inspectorFields.add(spacer());
        inspectorFields.add(sectionLabel("NOTES"));
        inspectorFields.add(noteBlock());
        inspectorFields.add(spacer());
        inspectorFields.add(sectionLabel("TAGS"));
        inspectorTags.setTags(session.tags());
        inspectorTags.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspectorFields.add(inspectorTags);
        inspectorFields.add(spacer());
        inspectorFields.add(sectionLabel("TABLEBASE"));
        inspectorFields.add(infoRow("Gaviota 7-piece", tablebaseText()));
        inspectorFields.add(spacer());
        inspectorFields.add(sectionLabel("ARTIFACTS"));
        inspectorFields.add(artifactRows);
        inspectorFields.revalidate();
        inspectorFields.repaint();
    }

    /**
     * Refreshes recent artifact rows.
     */
    private void refreshArtifacts() {
        artifactRows.removeAll();
        List<Path> paths = session.artifacts().recent();
        if (paths.isEmpty()) {
            artifactRows.add(mutedLine("No artifacts yet"));
        } else {
            int limit = Math.min(4, paths.size());
            for (int i = 0; i < limit; i++) {
                artifactRows.add(fileLine(paths.get(i)));
            }
        }
        artifactRows.revalidate();
        artifactRows.repaint();
    }

    /**
     * Refreshes bottom run rows.
     */
    private void refreshRunRows() {
        runRows.removeAll();
        List<Job> jobs = session.jobs().recent();
        if (jobs.isEmpty()) {
            runRows.add(mutedLine("No recent runs"));
        } else {
            int limit = Math.min(4, jobs.size());
            for (int i = 0; i < limit; i++) {
                runRows.add(new RunRow(jobs.get(i), openLatestLog));
            }
        }
        runRows.revalidate();
        runRows.repaint();
    }

    /**
     * Refreshes latest output text.
     */
    private void refreshLatestOutput() {
        Job latest = session.jobs().latest();
        String output = latest == null ? "" : latest.output();
        if (output == null || output.isBlank()) {
            latestOutput.setText(latest == null ? "No command output yet." : latest.displayCommand());
        } else {
            latestOutput.setText(trimOutput(output));
        }
        latestOutput.setCaretPosition(0);
    }

    /**
     * Returns the current engine inspector text.
     *
     * @return engine summary
     */
    private String engineText() {
        String summary = session.engineSummary();
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        return session.liveEngine() ? "Live analysis on" : "Idle";
    }

    /**
     * Returns a compact active-line summary.
     *
     * @return line summary
     */
    private String lineSummary() {
        return "Ply " + session.ply() + " / " + session.lastPly()
                + " - " + session.legalMoveCount() + " legal moves";
    }

    /**
     * Returns the best available opening summary from current tags.
     *
     * @return opening text
     */
    private String openingText() {
        for (String tag : session.tags()) {
            String upper = tag == null ? "" : tag.toUpperCase(Locale.ROOT);
            if (upper.contains("ECO") || upper.contains("OPENING")) {
                return tag;
            }
        }
        return session.lastPly() <= 12 ? "Opening not classified" : "No opening line";
    }

    /**
     * Returns a simple game-phase summary.
     *
     * @return phase text
     */
    private String phaseText() {
        int pieces = pieceCount(session.fen());
        if (pieces > 0 && pieces <= 7) {
            return "Endgame";
        }
        int ply = Math.max(session.ply(), session.lastPly());
        if (ply <= 12) {
            return "Opening";
        }
        return "Middlegame";
    }

    /**
     * Returns the tablebase eligibility summary.
     *
     * @return tablebase text
     */
    private String tablebaseText() {
        int pieces = pieceCount(session.fen());
        if (pieces <= 0) {
            return "N/A";
        }
        if (pieces <= 7) {
            return pieces + " pieces - eligible";
        }
        return pieces + " pieces - N/A";
    }

    /**
     * Counts chess pieces in a FEN string.
     *
     * @param fen FEN text
     * @return piece count
     */
    private static int pieceCount(String fen) {
        if (fen == null || fen.isBlank()) {
            return 0;
        }
        String board = fen.split("\\s+")[0];
        int count = 0;
        for (int i = 0; i < board.length(); i++) {
            if (Character.isLetter(board.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Builds the active-line card.
     *
     * @param title card title
     * @param summary main summary
     * @param detail detail text
     * @return card component
     */
    private static JComponent lineCard(String title, String summary, String detail) {
        JPanel card = elevatedBlock(new BorderLayout(0, 2));
        card.add(smallLabel(title, Theme.TEXT, Font.BOLD), BorderLayout.NORTH);
        JPanel body = verticalPanel();
        body.add(smallLabel(summary, Theme.TEXT, Font.PLAIN));
        body.add(smallLabel(detail, Theme.MUTED, Font.PLAIN));
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /**
     * Builds a note block from the current position state.
     *
     * @return note block
     */
    private JComponent noteBlock() {
        JPanel card = elevatedBlock(new BorderLayout(0, 2));
        String side = session.whiteToMove() ? "White to move" : "Black to move";
        card.add(smallLabel(side + ", " + session.legalMoveCount() + " legal moves",
                Theme.TEXT, Font.PLAIN), BorderLayout.NORTH);
        card.add(smallLabel(compactFen(session.fen()), Theme.MUTED, Font.PLAIN), BorderLayout.CENTER);
        return card;
    }

    /**
     * Builds the opening-distribution placeholder bar.
     *
     * @return bar component
     */
    private JComponent openingBar() {
        return new OpeningBar(phaseText());
    }

    /**
     * Creates a vertical spacer.
     *
     * @return spacer component
     */
    private static JComponent spacer() {
        return (JComponent) Box.createVerticalStrut(Theme.SPACE_MD);
    }

    /**
     * Creates an elevated inspector block.
     *
     * @param layout layout manager
     * @return block panel
     */
    private static JPanel elevatedBlock(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(true);
        panel.setBackground(Theme.ELEVATED_SOLID);
        panel.putClientProperty(CLIENT_ELEVATED_BLOCK, Boolean.TRUE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                Theme.pad(Theme.SPACE_SM, Theme.SPACE_SM)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    /**
     * Builds an inspector key/value row.
     *
     * @param key row key
     * @param value row value
     * @return row component
     */
    private static JComponent infoRow(String key, String value) {
        JPanel row = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.setOpaque(false);
        row.setBorder(Theme.pad(3, 0, 3, 0));
        JLabel left = smallLabel(key, Theme.MUTED, Font.BOLD);
        JLabel right = smallLabel(value == null || value.isBlank() ? "N/A" : value, Theme.TEXT, Font.PLAIN);
        right.setHorizontalAlignment(JLabel.RIGHT);
        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        return row;
    }

    /**
     * Builds a muted one-line placeholder.
     *
     * @param text text
     * @return line component
     */
    private static JComponent mutedLine(String text) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(Theme.pad(3, 0, 3, 0));
        row.add(smallLabel(text, Theme.MUTED, Font.PLAIN), BorderLayout.CENTER);
        return row;
    }

    /**
     * Builds one artifact row.
     *
     * @param path artifact path
     * @return artifact row
     */
    private static JComponent fileLine(Path path) {
        String name = path == null || path.getFileName() == null ? "artifact" : path.getFileName().toString();
        JPanel row = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.setOpaque(false);
        row.setBorder(Theme.pad(3, 0, 3, 0));
        row.add(smallLabel(name, Theme.TEXT, Font.PLAIN), BorderLayout.CENTER);
        JLabel size = smallLabel(fileHint(path), Theme.MUTED, Font.PLAIN);
        row.add(size, BorderLayout.EAST);
        return row;
    }

    /**
     * Returns a compact file hint.
     *
     * @param path file path
     * @return hint text
     */
    private static String fileHint(Path path) {
        if (path == null || path.getParent() == null) {
            return "";
        }
        Path parent = path.getParent().getFileName();
        return parent == null ? "" : parent.toString();
    }

    /**
     * Builds a section label.
     *
     * @param text label text
     * @return label
     */
    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setFont(Theme.font(Theme.FONT_MICRO, Font.BOLD));
        label.setForeground(Theme.STATUS_INFO_TEXT);
        label.setBorder(Theme.pad(0, 0, Theme.SPACE_XS, 0));
        return label;
    }

    /**
     * Creates a small label.
     *
     * @param text label text
     * @param color foreground color
     * @param style font style
     * @return label
     */
    private static JLabel smallLabel(String text, Color color, int style) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setFont(Theme.font(Theme.FONT_METADATA, style));
        label.setForeground(color);
        return label;
    }

    /**
     * Applies a concise tooltip and accessible description.
     *
     * @param component component to describe
     * @param description user-facing description
     */
    private static void describe(JComponent component, String description) {
        String text = description == null ? "" : description;
        component.setToolTipText(text);
        AccessibleContext context = component.getAccessibleContext();
        if (context != null) {
            context.setAccessibleDescription(text);
        }
    }

    /**
     * Builds a stable component/action identifier from a visible label.
     *
     * @param scope identifier scope
     * @param label visible label
     * @return stable identifier
     */
    private static String actionId(String scope, String label) {
        String clean = (label == null ? "item" : label.toLowerCase(Locale.ROOT))
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        return scope + "." + (clean.isBlank() ? "item" : clean);
    }

    /**
     * Creates a shell panel.
     *
     * @return panel
     */
    private static JPanel shellPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(Theme.BG);
        return panel;
    }

    /**
     * Creates a vertical transparent panel.
     *
     * @return panel
     */
    private static JPanel verticalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    /**
     * Restores chrome backgrounds after the generic theme refresher recolors
     * ordinary opaque panels as editor surfaces.
     *
     * @param component subtree root
     */
    private static void refreshChromeBackgrounds(Component component) {
        if (component instanceof javax.swing.JScrollPane) {
            return;
        }
        if (component instanceof JPanel panel && panel.isOpaque()) {
            if (Boolean.TRUE.equals(panel.getClientProperty(CLIENT_ELEVATED_BLOCK))) {
                panel.setBackground(Theme.ELEVATED_SOLID);
            } else {
                panel.setBackground(Theme.BG);
            }
        } else if (component instanceof JSplitPane split) {
            split.setBackground(Theme.BG);
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                refreshChromeBackgrounds(child);
            }
        }
    }

    /**
     * Returns a compact FEN preview.
     *
     * @param fen FEN text
     * @return compact text
     */
    private static String compactFen(String fen) {
        if (fen == null || fen.isBlank()) {
            return "No position";
        }
        String[] parts = fen.split("\\s+");
        if (parts.length == 0) {
            return "No position";
        }
        String board = parts[0];
        return board.length() <= 22 ? board : board.substring(0, 21) + "...";
    }

    /**
     * Trims a command-output preview to the latest useful chunk.
     *
     * @param text output text
     * @return trimmed output
     */
    private static String trimOutput(String text) {
        String normalized = text == null ? "" : text.strip();
        if (normalized.length() <= 2200) {
            return normalized;
        }
        return normalized.substring(normalized.length() - 2200).stripLeading();
    }

    /**
     * Status-progress fraction for a job.
     *
     * @param status job status
     * @return progress amount
     */
    private static double progressFor(JobStatus status) {
        if (status == null) {
            return 0.0d;
        }
        return switch (status) {
            case QUEUED -> 0.08d;
            case RUNNING -> 0.64d;
            case SUCCEEDED -> 1.0d;
            case FAILED, CANCELLED -> 1.0d;
        };
    }

    /**
     * Display text for a job status.
     *
     * @param status job status
     * @return label text
     */
    private static String statusText(JobStatus status) {
        return status == null ? "unknown" : status.label().toLowerCase(Locale.ROOT);
    }

    /**
     * Compact opening/middlegame/endgame distribution bar.
     */
    private static final class OpeningBar extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Phase label.
         */
        private final String phase;

        /**
         * Creates the opening bar.
         *
         * @param phase active phase label
         */
        OpeningBar(String phase) {
            this.phase = phase == null ? "" : phase;
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        /**
         * Returns the preferred size.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(1, 42);
        }

        /**
         * Returns the maximum size.
         *
         * @return maximum size
         */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        /**
         * Paints the phase distribution.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int x = 0;
                int y = 9;
                int width = Math.max(1, getWidth() - 1);
                int height = 9;
                int[] parts = shares();
                Color[] colors = {
                    Theme.STATUS_INFO_TEXT,
                    Theme.MUTED,
                    Theme.STATUS_SUCCESS_TEXT
                };
                int drawn = 0;
                for (int i = 0; i < parts.length; i++) {
                    int segment = i == parts.length - 1 ? width - drawn : Math.round(width * parts[i] / 100.0f);
                    g.setColor(Theme.withAlpha(colors[i], Theme.isDark() ? 210 : 180));
                    g.fillRect(x + drawn, y, Math.max(0, segment), height);
                    drawn += segment;
                }
                g.setColor(Theme.LINE);
                g.drawRoundRect(x, y, width, height, 3, 3);
                g.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
                g.setColor(Theme.MUTED);
                FontMetrics metrics = g.getFontMetrics();
                int baseline = y + height + metrics.getAscent() + 7;
                g.drawString(parts[0] + "%", 0, baseline);
                String middle = parts[1] + "%";
                g.drawString(middle, (width - metrics.stringWidth(middle)) / 2, baseline);
                String end = parts[2] + "%";
                g.drawString(end, width - metrics.stringWidth(end), baseline);
            } finally {
                g.dispose();
            }
        }

        /**
         * Returns display shares for the current phase.
         *
         * @return opening, middlegame, endgame percentages
         */
        private int[] shares() {
            return switch (phase) {
                case "Endgame" -> new int[] { 12, 23, 65 };
                case "Middlegame" -> new int[] { 24, 56, 20 };
                default -> new int[] { 62, 28, 10 };
            };
        }
    }

    /**
     * Dock-tab strip painted in the bottom run center.
     */
    private final class DockTabs extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the dock tab strip.
         */
        DockTabs() {
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        /**
         * Returns preferred size.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(420, 24);
        }

        /**
         * Paints the dock tabs.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
                int x = 0;
                x = paintDockTab(g, x, "Queue (" + session.jobs().size() + ")", true);
                x = paintDockTab(g, x, "Problems (" + failedRunCount() + ")", false);
                x = paintDockTab(g, x, "Output", false);
                paintDockTab(g, x, "Artifacts (" + session.artifacts().recent().size() + ")", false);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints one dock tab.
         *
         * @param g graphics context
         * @param x left position
         * @param label tab label
         * @param selected selected state
         * @return next x position
         */
        private int paintDockTab(Graphics2D g, int x, String label, boolean selected) {
            FontMetrics metrics = g.getFontMetrics();
            int width = metrics.stringWidth(label) + 22;
            int height = 22;
            if (selected) {
                g.setColor(Theme.ELEVATED_SOLID);
                g.fillRoundRect(x, 1, width, height - 2, Theme.RADIUS, Theme.RADIUS);
                g.setColor(Theme.ACCENT);
                g.fillRoundRect(x + 7, height - 3, width - 14, 2, 2, 2);
            }
            g.setColor(selected ? Theme.TEXT : Theme.MUTED);
            int baseline = (height + metrics.getAscent() - metrics.getDescent()) / 2;
            g.drawString(label, x + 11, baseline);
            return x + width + 4;
        }

        /**
         * Counts failed recent jobs.
         *
         * @return failed count
         */
        private int failedRunCount() {
            int count = 0;
            for (Job job : session.jobs().recent()) {
                if (job != null && job.status() == JobStatus.FAILED) {
                    count++;
                }
            }
            return count;
        }
    }

    /**
     * Table header row for the run-center queue.
     */
    private static final class RunHeaderRow extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the row header.
         */
        RunHeaderRow() {
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        /**
         * Returns the stable row size.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(1, 22);
        }

        /**
         * Paints the run-table headings.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
                g.setColor(Theme.MUTED);
                FontMetrics metrics = g.getFontMetrics();
                int baseline = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
                g.drawString("ID", 10, baseline);
                g.drawString("Name", 74, baseline);
                g.drawString("Status", Math.max(80, getWidth() - 118), baseline);
                g.drawString("Progress", Math.max(120, getWidth() - 210), baseline);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Collapsible section in the left navigator.
     */
    private static final class NavigatorSection extends JPanel {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Section header.
         */
        private final NavigatorSectionHeader header;

        /**
         * Section body rows.
         */
        private final JPanel body = verticalPanel();

        /**
         * Whether the section body is visible.
         */
        private boolean expanded;

        /**
         * Creates a navigator section.
         *
         * @param title section title
         * @param expanded initial expanded state
         * @param rows section rows
         */
        NavigatorSection(String title, boolean expanded, JComponent... rows) {
            super(new BorderLayout(0, 0));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setName(actionId("workbench.navigator.section", title));
            header = new NavigatorSectionHeader(title, () -> setExpanded(!this.expanded));
            add(header, BorderLayout.NORTH);
            if (rows != null) {
                for (JComponent row : rows) {
                    body.add(row);
                }
            }
            add(body, BorderLayout.CENTER);
            setExpanded(expanded);
        }

        /**
         * Sets whether the section is expanded.
         *
         * @param value expanded state
         */
        private void setExpanded(boolean value) {
            expanded = value;
            body.setVisible(value);
            header.setExpanded(value);
            revalidate();
            repaint();
        }

        /**
         * Returns the maximum size used by the vertical navigator layout.
         *
         * @return maximum size
         */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /**
     * Header control for a collapsible navigator section.
     */
    private static final class NavigatorSectionHeader extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Header title.
         */
        private final String title;

        /**
         * Toggle action.
         */
        private final Runnable toggle;

        /**
         * Whether the pointer is over the header.
         */
        private boolean hovered;

        /**
         * Whether the section is expanded.
         */
        private boolean expanded;

        /**
         * Creates the header.
         *
         * @param title section title
         * @param toggle toggle action
         */
        NavigatorSectionHeader(String title, Runnable toggle) {
            this.title = title == null ? "" : title;
            this.toggle = toggle == null ? () -> { } : toggle;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setName(actionId("workbench.navigator.section.toggle", this.title));
            putClientProperty("workbench.actionId", getName());
            getAccessibleContext().setAccessibleName(this.title);
            describe(this, "Toggle " + this.title);
            addMouseListener(new MouseAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mousePressed(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        NavigatorSectionHeader.this.toggle.run();
                    }
                }
            });
        }

        /**
         * Sets the expanded state.
         *
         * @param value expanded state
         */
        private void setExpanded(boolean value) {
            boolean changed = expanded != value;
            expanded = value;
            AccessibleContext context = getAccessibleContext();
            if (context != null) {
                context.setAccessibleDescription((value ? "Collapse " : "Expand ") + title);
            }
            setToolTipText((value ? "Collapse " : "Expand ") + title);
            if (changed) {
                repaint();
            }
        }

        /**
         * Returns accessibility metadata for the custom-painted section header.
         *
         * @return accessible context
         */
        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    /**
                     * Serialization identifier for Swing compatibility.
                     */
                    private static final long serialVersionUID = 1L;
                };
            }
            return accessibleContext;
        }

        /**
         * Returns the stable header size.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(1, 23);
        }

        /**
         * Returns the maximum size used by the vertical navigator layout.
         *
         * @return maximum size
         */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        /**
         * Paints the section header.
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
                if (hovered) {
                    g.setColor(Theme.TAB_HOVER);
                    g.fillRoundRect(0, 1, Math.max(0, w - 1), h - 2, Theme.RADIUS, Theme.RADIUS);
                }
                Font headerFont = Theme.font(Theme.FONT_MICRO, Font.BOLD);
                g.setColor(Theme.MUTED);
                FontMetrics fm = g.getFontMetrics(headerFont);
                int baseline = (h + fm.getAscent() - fm.getDescent()) / 2;
                String chevron = expanded ? "v" : ">";
                new java.awt.font.TextLayout(chevron, headerFont, g.getFontRenderContext())
                        .draw(g, 8, baseline);
                new java.awt.font.TextLayout(Ui.elide(title, fm, Math.max(20, w - 30)),
                        headerFont, g.getFontRenderContext()).draw(g, 22, baseline);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * One custom-painted navigator row.
     */
    private static final class NavigatorRow extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Row label.
         */
        private final String text;

        /**
         * Target tab index.
         */
        private final int tabIndex;

        /**
         * Row action.
         */
        private final Runnable action;

        /**
         * Supplies the right-aligned count text.
         */
        private final Supplier<String> countSupplier;

        /**
         * Whether this row mirrors the selected editor tab.
         */
        private final boolean selectedForTab;

        /**
         * Whether the row is selected.
         */
        private boolean selected;

        /**
         * Whether the row is hovered.
         */
        private boolean hovered;

        /**
         * Creates a navigator row.
         *
         * @param text label text
         * @param tabIndex target tab
         * @param tooltip tooltip text
         * @param countSupplier row-count supplier
         * @param selectedForTab whether this row mirrors tab selection
         * @param action row action
         */
        NavigatorRow(String text, int tabIndex, String tooltip, Supplier<String> countSupplier,
                boolean selectedForTab, Runnable action) {
            this.text = text == null ? "" : text;
            this.tabIndex = tabIndex;
            this.countSupplier = countSupplier == null ? () -> "" : countSupplier;
            this.selectedForTab = selectedForTab;
            this.action = action == null ? () -> { } : action;
            setName(actionId("workbench.navigator.row", this.text));
            putClientProperty("workbench.actionId", getName());
            setToolTipText(tooltip);
            getAccessibleContext().setAccessibleName(this.text);
            getAccessibleContext().setAccessibleDescription(tooltip == null ? this.text : tooltip);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mousePressed(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        NavigatorRow.this.action.run();
                    }
                }
            });
        }

        /**
         * Returns accessibility metadata for the custom-painted navigator row.
         *
         * @return accessible context
         */
        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    /**
                     * Serialization identifier for Swing compatibility.
                     */
                    private static final long serialVersionUID = 1L;
                };
            }
            return accessibleContext;
        }

        /**
         * Returns the target tab index.
         *
         * @return tab index
         */
        int tabIndex() {
            return tabIndex;
        }

        /**
         * Returns whether this row mirrors tab selection.
         *
         * @return true when selected from tab state
         */
        boolean selectedForTab() {
            return selectedForTab;
        }

        /**
         * Sets the selected state.
         *
         * @param value selected state
         */
        void setSelected(boolean value) {
            if (selected != value) {
                selected = value;
                repaint();
            }
        }

        /**
         * Returns the stable row size.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(1, 27);
        }

        /**
         * Paints the navigator row.
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
                if (selected || hovered) {
                    g.setColor(selected ? Theme.SELECTION_SOLID : Theme.TAB_HOVER);
                    g.fillRoundRect(0, 1, w - 1, h - 2, Theme.RADIUS, Theme.RADIUS);
                }
                if (selected) {
                    g.setColor(Theme.ACCENT);
                    g.fillRoundRect(0, 5, 3, h - 10, 3, 3);
                }
                paintIcon(g, 15, h / 2);
                g.setFont(Theme.font(Theme.FONT_METADATA, selected ? Font.BOLD : Font.PLAIN));
                g.setColor(selected ? Theme.TEXT : Theme.MUTED);
                FontMetrics fm = g.getFontMetrics();
                int baseline = (h + fm.getAscent() - fm.getDescent()) / 2;
                String count = countText();
                int countWidth = count.isBlank() ? 0 : fm.stringWidth(count);
                int countX = w - 10 - countWidth;
                String label = Ui.elide(text, fm, Math.max(24, countX - 36));
                g.drawString(label, 30, baseline);
                if (!count.isBlank()) {
                    g.setColor(selected ? Theme.TEXT : Theme.MUTED);
                    g.drawString(count, countX, baseline);
                }
            } finally {
                g.dispose();
            }
        }

        /**
         * Returns the current count text.
         *
         * @return count text
         */
        private String countText() {
            String value = countSupplier.get();
            return value == null ? "" : value;
        }

        /**
         * Paints a compact route icon.
         *
         * @param g graphics context
         * @param cx icon center x
         * @param cy icon center y
         */
        private void paintIcon(Graphics2D g, int cx, int cy) {
            g.setColor(selected ? Theme.ACCENT : Theme.withAlpha(Theme.MUTED, hovered ? 230 : 170));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int r = 5;
            switch (tabIndex) {
                case WindowBase.TAB_DASHBOARD -> {
                    g.drawLine(cx - 5, cy, cx, cy - 5);
                    g.drawLine(cx, cy - 5, cx + 5, cy);
                    g.drawRect(cx - 4, cy, 8, 6);
                }
                case WindowBase.TAB_BOARD -> {
                    g.drawRect(cx - r, cy - r, r * 2, r * 2);
                    g.drawLine(cx, cy - r, cx, cy + r);
                    g.drawLine(cx - r, cy, cx + r, cy);
                }
                case WindowBase.TAB_RUN, WindowBase.TAB_CONSOLE, WindowBase.TAB_LOGS -> {
                    g.drawLine(cx - 4, cy - 5, cx + 5, cy);
                    g.drawLine(cx + 5, cy, cx - 4, cy + 5);
                }
                case WindowBase.TAB_ENGINE -> {
                    g.drawOval(cx - 5, cy - 5, 4, 4);
                    g.drawOval(cx + 1, cy - 1, 4, 4);
                    g.drawLine(cx - 1, cy - 2, cx + 2, cy + 1);
                }
                case WindowBase.TAB_DATASETS -> {
                    g.drawRect(cx - 5, cy - 5, 10, 10);
                    g.drawLine(cx - 2, cy - 5, cx - 2, cy + 5);
                    g.drawLine(cx + 2, cy - 5, cx + 2, cy + 5);
                }
                case WindowBase.TAB_PUBLISH -> {
                    g.drawRect(cx - 5, cy - 5, 8, 10);
                    g.drawLine(cx - 3, cy - 2, cx + 1, cy - 2);
                    g.drawLine(cx - 3, cy + 1, cx + 1, cy + 1);
                }
                default -> {
                    g.drawRoundRect(cx - r, cy - r, r * 2, r * 2, 3, 3);
                    g.drawLine(cx - 2, cy, cx + 2, cy);
                    g.drawLine(cx, cy - 2, cx, cy + 2);
                }
            }
        }
    }

    /**
     * One custom-painted run-center row.
     */
    private static final class RunRow extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Job rendered by the row.
         */
        private final Job job;

        /**
         * Click action.
         */
        private final Runnable action;

        /**
         * Whether the row is hovered.
         */
        private boolean hovered;

        /**
         * Creates a run row.
         *
         * @param job rendered job
         * @param action click action
         */
        RunRow(Job job, Runnable action) {
            this.job = job;
            this.action = action == null ? () -> { } : action;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(job == null ? null : job.displayCommand());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mousePressed(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        RunRow.this.action.run();
                    }
                }
            });
        }

        /**
         * Returns the stable row size.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(1, 30);
        }

        /**
         * Paints a run row.
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
                g.setColor(hovered ? Theme.TAB_HOVER : Theme.ELEVATED_SOLID);
                g.fillRoundRect(0, 2, Math.max(0, w - 1), Math.max(0, h - 4), Theme.RADIUS, Theme.RADIUS);
                g.setColor(Theme.LINE);
                g.drawRoundRect(0, 2, Math.max(0, w - 1), Math.max(0, h - 4), Theme.RADIUS, Theme.RADIUS);
                paintJobText(g, w, h);
                paintProgress(g, w, h);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints row labels.
         *
         * @param g graphics context
         * @param width row width
         * @param height row height
         */
        private void paintJobText(Graphics2D g, int width, int height) {
            if (job == null) {
                return;
            }
            g.setFont(Theme.mono(Theme.FONT_METADATA));
            FontMetrics mono = g.getFontMetrics();
            String id = "#" + job.id();
            g.setColor(Theme.STATUS_INFO_TEXT);
            g.drawString(id, 10, (height + mono.getAscent() - mono.getDescent()) / 2);

            g.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Theme.TEXT);
            String command = Ui.elide(job.displayCommand(), fm, Math.max(40, width - 260));
            g.drawString(command, 74, (height + fm.getAscent() - fm.getDescent()) / 2);
            g.setColor(statusColor(job.status()));
            g.drawString(statusText(job.status()), Math.max(80, width - 118),
                    (height + fm.getAscent() - fm.getDescent()) / 2);
        }

        /**
         * Paints the job progress bar.
         *
         * @param g graphics context
         * @param width row width
         * @param height row height
         */
        private void paintProgress(Graphics2D g, int width, int height) {
            int x = Math.max(120, width - 210);
            int y = height / 2 + 6;
            int barWidth = 74;
            int barHeight = 5;
            g.setColor(Theme.withAlpha(Theme.MUTED, Theme.isDark() ? 62 : 50));
            g.fillRoundRect(x, y, barWidth, barHeight, barHeight, barHeight);
            g.setColor(statusColor(job == null ? null : job.status()));
            g.fillRoundRect(x, y, (int) Math.round(barWidth * progressFor(job == null ? null : job.status())),
                    barHeight, barHeight, barHeight);
        }

        /**
         * Returns the status color.
         *
         * @param status job status
         * @return color
         */
        private static Color statusColor(JobStatus status) {
            if (status == null) {
                return Theme.MUTED;
            }
            return switch (status) {
                case RUNNING -> Theme.STATUS_INFO_TEXT;
                case SUCCEEDED -> Theme.STATUS_SUCCESS_TEXT;
                case FAILED -> Theme.STATUS_ERROR_TEXT;
                case CANCELLED -> Theme.STATUS_WARNING_TEXT;
                case QUEUED -> Theme.MUTED;
            };
        }
    }
}
