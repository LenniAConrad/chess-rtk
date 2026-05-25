package application.gui.workbench.window;

import application.Config;
import application.gui.workbench.Defaults;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.command.CommandPalette.PaletteAction;
import application.gui.workbench.command.CommandPalette;
import application.gui.workbench.game.PgnExplorerDialog;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.layout.LazyPanel;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.session.LogPanel;
import application.gui.workbench.ui.BackdropPanel;
import application.gui.workbench.ui.SettingsChipRow;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.Ui;
import chess.core.Move;
import chess.core.Position;
import chess.uci.Analysis;
import chess.uci.Engine;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.print.PrinterException;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.transparentPanel;
import static application.gui.workbench.ui.Ui.trimmed;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */

public abstract class WindowLifecycle extends WindowBase {
    /** Serialization identifier for Swing frame compatibility. */
    private static final long serialVersionUID = 1L;

    /** Top-level application menu. */
    protected transient SettingsMenu settingsMenu;

    /** Title-bar layout control menu. */
    protected transient LayoutMenu layoutMenu;

    private final transient Runnable soundSettingsListener = this::syncSoundSettingsControls;

    /**
     * Preferences node used for persisted workbench state.
     */
    protected static final Preferences WORKBENCH_PREFS =
            Preferences.userNodeForPackage(Window.class);

    /** Preference key for the appearance theme mode. */
    protected static final String PREF_THEME_MODE = "appearance.themeMode";

    /** Preference key for status-bar visibility. */
    protected static final String PREF_STATUS_BAR_VISIBLE = "layout.statusBarVisible";

    /** Preference key for legal-move hover previews. */
    protected static final String PREF_SHOW_LEGAL_MOVES = "display.showLegalMovePreview";

    /** Preference key for the last-move highlight. */
    protected static final String PREF_SHOW_LAST_MOVE = "display.showLastMoveHighlight";

    /** Preference key for best-move board arrows. */
    protected static final String PREF_SHOW_BEST_ARROWS = "display.showBestMoveArrows";

    /** Preference key for board coordinates. */
    protected static final String PREF_SHOW_COORDINATES = "display.showCoordinates";

    /** Preference key for board animations. */
    protected static final String PREF_BOARD_ANIMATIONS = "display.boardAnimations";

    /** Preference key for automatic eval-bar updates. */
    protected static final String PREF_AUTO_EVAL_BAR = "display.autoEvalBar";

    /** Preference key for continuous external-engine analysis. */
    protected static final String PREF_LIVE_EXTERNAL_ENGINE = "engine.liveExternal";

    /** Preference key for the external-engine protocol path. */
    protected static final String PREF_ENGINE_PROTOCOL = "engine.protocolPath";

    /** Preference key for the external-engine node limit. */
    protected static final String PREF_ENGINE_NODES = "engine.maxNodes";

    /** Preference key for the external-engine hash size. */
    protected static final String PREF_ENGINE_HASH = "engine.hash";

    /** Default window width in pixels. */
    protected static final int DEFAULT_WINDOW_WIDTH = 1440;

    /** Default window height in pixels. */
    protected static final int DEFAULT_WINDOW_HEIGHT = 920;

    /** Minimum usable workbench width in pixels. */
    protected static final int MIN_WINDOW_WIDTH = 1040;

    /** Minimum usable workbench height in pixels. */
    protected static final int MIN_WINDOW_HEIGHT = 700;

    private static final KeyStroke[] PREVIOUS_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_LEFT,
            KeyEvent.VK_KP_LEFT,
            KeyEvent.VK_NUMPAD4);

    private static final KeyStroke[] NEXT_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_RIGHT,
            KeyEvent.VK_KP_RIGHT,
            KeyEvent.VK_NUMPAD6);

    private static final KeyStroke[] FIRST_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_UP,
            KeyEvent.VK_KP_UP,
            KeyEvent.VK_NUMPAD8,
            KeyEvent.VK_HOME);

    private static final KeyStroke[] LAST_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_DOWN,
            KeyEvent.VK_KP_DOWN,
            KeyEvent.VK_NUMPAD2,
            KeyEvent.VK_END);

    /** Restores persisted window size, position, and maximized state. */
    protected void restoreWindowGeometry() {
        int width = WORKBENCH_PREFS.getInt("window.width", DEFAULT_WINDOW_WIDTH);
        int height = WORKBENCH_PREFS.getInt("window.height", DEFAULT_WINDOW_HEIGHT);
        int x = WORKBENCH_PREFS.getInt("window.x", Integer.MIN_VALUE);
        int y = WORKBENCH_PREFS.getInt("window.y", Integer.MIN_VALUE);
        boolean maximized = WORKBENCH_PREFS.getBoolean("window.maximized", false);
        setSize(Math.max(MIN_WINDOW_WIDTH, width), Math.max(MIN_WINDOW_HEIGHT, height));
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || !isOnAnyScreen(x, y, width, height)) {
            setLocationRelativeTo(null);
        } else {
            setLocation(x, y);
        }
        if (maximized) {
            setExtendedState(getExtendedState() | java.awt.Frame.MAXIMIZED_BOTH);
        }
    }

    /**
     * Returns whether a rectangle intersects any available screen.
     *
     * @param x window x coordinate
     * @param y window y coordinate
     * @param width window width
     * @param height window height
     * @return true when the rectangle is on a screen
     */
    protected static boolean isOnAnyScreen(int x, int y, int width, int height) {
        try {
            java.awt.Rectangle bounds = new java.awt.Rectangle(x, y, Math.max(1, width), Math.max(1, height));
            for (java.awt.GraphicsDevice device :
                    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                if (device.getDefaultConfiguration().getBounds().intersects(bounds)) {
                    return true;
                }
            }
        } catch (java.awt.HeadlessException ignored) {
            return false;
        }
        return false;
    }

    /** Persists the current window geometry. */
    protected void saveWindowGeometry() {
        boolean maximized = (getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) == java.awt.Frame.MAXIMIZED_BOTH;
        WORKBENCH_PREFS.putBoolean("window.maximized", maximized);
        if (maximized) {
            return;
        }
        WORKBENCH_PREFS.putInt("window.width", getWidth());
        WORKBENCH_PREFS.putInt("window.height", getHeight());
        WORKBENCH_PREFS.putInt("window.x", getX());
        WORKBENCH_PREFS.putInt("window.y", getY());
    }

    /** Loads the persisted theme mode. */
    protected void loadThemeSetting() {
        Theme.setMode(Theme.Mode.fromPreference(
                WORKBENCH_PREFS.get(PREF_THEME_MODE, Theme.Mode.LIGHT.id())));
        TensorViz.refreshPalette();
    }

    /** Loads persisted board and analysis display settings. */
    protected void loadDisplaySettings() {
        showLegalMovePreview = WORKBENCH_PREFS.getBoolean(PREF_SHOW_LEGAL_MOVES, true);
        showLastMoveHighlight = WORKBENCH_PREFS.getBoolean(PREF_SHOW_LAST_MOVE, true);
        showBestMoveArrows = WORKBENCH_PREFS.getBoolean(PREF_SHOW_BEST_ARROWS, true);
        showBoardCoordinates = WORKBENCH_PREFS.getBoolean(PREF_SHOW_COORDINATES, true);
        boardAnimationsEnabled = WORKBENCH_PREFS.getBoolean(PREF_BOARD_ANIMATIONS, true);
        autoEvalBarEnabled = WORKBENCH_PREFS.getBoolean(PREF_AUTO_EVAL_BAR, true);
        liveExternalEngineEnabled = WORKBENCH_PREFS.getBoolean(PREF_LIVE_EXTERNAL_ENGINE, false);
    }

    /** Loads persisted workbench layout settings. */
    protected void loadLayoutSettings() {
        statusBarVisible = WORKBENCH_PREFS.getBoolean(PREF_STATUS_BAR_VISIBLE, true);
    }

    /** Saves board and analysis display settings. */
    protected void saveDisplaySettings() {
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_LEGAL_MOVES, showLegalMovePreview);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_LAST_MOVE, showLastMoveHighlight);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_BEST_ARROWS, showBestMoveArrows);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_COORDINATES, showBoardCoordinates);
        WORKBENCH_PREFS.putBoolean(PREF_BOARD_ANIMATIONS, boardAnimationsEnabled);
        WORKBENCH_PREFS.putBoolean(PREF_AUTO_EVAL_BAR, autoEvalBarEnabled);
        WORKBENCH_PREFS.putBoolean(PREF_LIVE_EXTERNAL_ENGINE, liveExternalEngineEnabled);
    }

    /**
     * Applies and persists the requested theme mode.
     *
     * @param dark true to use dark mode
     */
    protected void setDarkMode(boolean dark) {
        Theme.Mode next = dark ? Theme.Mode.DARK : Theme.Mode.LIGHT;
        if (Theme.mode() == next) {
            if (settingsMenu != null) {
                settingsMenu.syncMode();
            }
            return;
        }
        Theme.setMode(next);
        TensorViz.refreshPalette();
        WORKBENCH_PREFS.put(PREF_THEME_MODE, Theme.mode().id());
        Theme.install();
        Theme.refreshComponentTree(this);
        if (tabs != null) {
            tabs.refreshTheme();
        }
        if (settingsMenu != null) {
            settingsMenu.syncMode();
            settingsMenu.refreshTheme();
        }
        if (layoutMenu != null) {
            layoutMenu.refreshTheme();
        }
        if (pgnExplorer != null) {
            pgnExplorer.refreshTheme();
        }
        if (commandPalette != null) {
            commandPalette.refreshTheme();
        }
        repaint();
        toast(Toast.Kind.INFO, Theme.mode().label() + " mode");
    }

    /**
     * Returns whether dark mode is active.
     *
     * @return true in dark mode
     */
    protected boolean isDarkMode() {
        return Theme.isDark();
    }

    /** Loads persisted external-engine settings. */
    protected void loadEngineSettings() {
        engineProtocolField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_PROTOCOL, Config.getProtocolPath()));
        engineNodesField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_NODES, ""));
        engineHashField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_HASH, ""));
    }

    /** Installs example placeholders on editable fields. */
    protected void installFieldPlaceholders() {
        placeholder(fenField, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        placeholder(analysisDurationField, "e.g. " + Defaults.ANALYSIS_DURATION + " or 500ms");
        placeholder(engineProtocolField, "path/to/engine.toml");
        placeholder(engineNodesField, "e.g. 1000000");
        placeholder(engineHashField, "e.g. 128");
    }

    /** Installs listeners for engine-setting fields. */
    protected void installEngineSettingListeners() {
        engineProtocolField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
        engineNodesField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
        engineHashField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
    }

    /** Saves external-engine settings. */
    protected void saveEngineSettings() {
        putPreference(PREF_ENGINE_PROTOCOL, trimmed(engineProtocolField));
        putPreference(PREF_ENGINE_NODES, trimmed(engineNodesField));
        putPreference(PREF_ENGINE_HASH, trimmed(engineHashField));
    }

    /**
     * Stores or clears a string preference.
     *
     * @param key preference key
     * @param value preference value
     */
    protected static void putPreference(String key, String value) {
        if (value == null || value.isBlank()) {
            WORKBENCH_PREFS.remove(key);
        } else {
            WORKBENCH_PREFS.put(key, value);
        }
    }

    /** Persists changed engine settings and refreshes dependent previews. */
    protected void engineSettingsChanged() {
        saveEngineSettings();
        requestCommandPreviews();
        if (liveExternalEngineEnabled) {
            restartLiveAnalysis();
        } else {
            requestEvalUpdate();
        }
    }

    /**
     * Applies display settings to the board and dependent analysis surfaces.
     *
     * @param refreshEval true to refresh evaluation immediately
     */
    protected void applyDisplaySettings(boolean refreshEval) {
        board.setShowLegalMovePreview(showLegalMovePreview);
        board.setShowLastMoveHighlight(showLastMoveHighlight);
        board.setShowSuggestedMoveArrow(showBestMoveArrows);
        board.setShowNotation(showBoardCoordinates);
        board.setAnimationsEnabled(boardAnimationsEnabled);
        liveEngineToggle.setSelected(liveExternalEngineEnabled);
        if (liveExternalEngineEnabled) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
            if (!isAnalyzePaneVisible()) {
                pauseHiddenLiveAnalysis();
            } else if (refreshEval) {
                requestLiveAnalysisUpdate();
            }
            return;
        }
        if (!autoEvalBarEnabled) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
            stopLiveAnalysis();
            evalBar.setUnavailable("off");
        } else if (refreshEval) {
            requestEvalUpdate();
        }
    }

    private void syncSoundSettingsControls() {
        if (settingsMenu != null) {
            settingsMenu.syncMode();
        }
    }

    /**
     * Cancels background work and persists window geometry before disposing.
     */
    @Override
    public void dispose() {
        SoundService.removeSettingsListener(soundSettingsListener);
        saveWindowGeometry();
        evalRequestId++;
        cancelEvalCommand();
        stopLiveAnalysis();
        evalDebounceTimer.stop();
        if (runningCommand != null && runningCommand.isRunning()) {
            runningCommand.cancel();
        }
        if (tagWorker != null && !tagWorker.isDone()) {
            tagWorker.cancel(true);
        }
        for (application.gui.workbench.network.NetworkPanel panel : new java.util.ArrayList<>(networkPanels)) {
            panel.dispose();
        }
        networkPanels.clear();
        super.dispose();
    }

    /** Builds the complete native workbench UI. */
    protected void buildUi() {
        loadLayoutSettings();
        settingsMenu = new SettingsMenu(new SettingsMenu.Controller() {
            /**
             * Returns the active appearance mode.
             *
             * @return active appearance mode
             */
            @Override
            public Theme.Mode themeMode() {
                return Theme.mode();
            }

            /**
             * Applies the selected appearance mode.
             *
             * @param mode requested appearance mode
             */
            @Override
            public void setThemeMode(Theme.Mode mode) {
                setDarkMode(mode == Theme.Mode.DARK);
            }

            /**
             * Returns whether sounds are enabled.
             *
             * @return true when sounds are enabled
             */
            @Override
            public boolean soundEnabled() {
                return !SoundService.isMuted();
            }

            /**
             * Applies the sound enabled flag.
             *
             * @param enabled true when sounds are enabled
             */
            @Override
            public void setSoundEnabled(boolean enabled) {
                SoundService.setMuted(!enabled);
                settingsMenu.syncMode();
            }

            /**
             * Opens board and display settings.
             */
            @Override
            public void showDisplaySettings() {
                WindowLifecycle.this.showDisplaySettings();
            }

            /**
             * Opens engine settings.
             */
            @Override
            public void showEngineSettings() {
                WindowLifecycle.this.showEngineSettings();
            }

            /**
             * Opens sound settings.
             */
            @Override
            public void showSoundSettings() {
                WindowLifecycle.this.showDisplaySettings();
            }

            /**
             * Opens the command palette.
             */
            @Override
            public void showCommandPalette() {
                WindowLifecycle.this.showCommandPalette();
            }

            /**
             * Opens the persisted logs folder.
             */
            @Override
            public void openLogsDirectory() {
                openLogsDockAndDirectory();
            }

            @Override
            public void openPgn() {
                showPgnExplorer();
            }

            @Override
            public void savePgn() {
                savePgnFile();
            }

            @Override
            public void copyFen() {
                copyText(currentFen());
            }

            @Override
            public void copyBuiltCommand() {
                WindowLifecycle.this.copyBuiltCommand();
            }

            @Override
            public void openDashboard() {
                selectTab(TAB_DASHBOARD);
            }

            @Override
            public void openAnalyze() {
                selectTab(TAB_ANALYZE);
            }

            @Override
            public void openCommands() {
                selectTab(TAB_COMMANDS);
            }

            @Override
            public void openBatch() {
                selectTab(TAB_BATCH);
            }

            @Override
            public void openDatasets() {
                selectTab(TAB_DATASETS);
            }

            @Override
            public void openPublish() {
                selectTab(TAB_PUBLISH);
            }

            @Override
            public void openNetwork() {
                selectTab(TAB_NETWORK);
            }

            @Override
            public void openPuzzles() {
                selectTab(TAB_PUZZLES);
            }

            @Override
            public void newAnalyzeTab() {
                openNewAnalyzeTab();
            }

            @Override
            public void showConsole() {
                showConsoleDock();
            }

            @Override
            public void showLogs() {
                showLogsDock();
            }

            @Override
            public void firstPosition() {
                jumpActivePositionToStart();
            }

            @Override
            public void previousPosition() {
                navigateActivePosition(-1);
            }

            @Override
            public void nextPosition() {
                navigateActivePosition(1);
            }

            @Override
            public void lastPosition() {
                jumpActivePositionToEnd();
            }

            @Override
            public void runBuiltCommand() {
                runSelectedTemplate();
            }

            @Override
            public void runBestMove() {
                WindowLifecycle.this.runBestMove();
            }

            @Override
            public void runAnalyze() {
                WindowLifecycle.this.runAnalyze();
            }

            @Override
            public void runPerft() {
                WindowLifecycle.this.runPerft();
            }

            @Override
            public void runBatch() {
                batchPanel.runBatch();
            }

            @Override
            public void runPublishing() {
                runPublishingCommand();
            }

            @Override
            public void runAllChecks() {
                runAllHealthChecks();
            }

            @Override
            public void stopCommand() {
                WindowLifecycle.this.stopCommand();
            }

            @Override
            public void splitRight() {
                tabs.splitSelectedTabRight();
            }

            @Override
            public void splitDown() {
                tabs.splitSelectedTabDown();
            }

            @Override
            public void reopenAllTabs() {
                tabs.reopenAllTabs();
            }
        });
        SoundService.addSettingsListener(soundSettingsListener);
        layoutMenu = new LayoutMenu(new LayoutMenu.Controller() {
            /**
             * Returns whether the workbench status bar is visible.
             *
             * @return true when visible
             */
            @Override
            public boolean statusBarVisible() {
                return WindowLifecycle.this.isStatusBarVisible();
            }

            /**
             * Applies workbench status-bar visibility.
             *
             * @param visible true to show the status bar
             */
            @Override
            public void setStatusBarVisible(boolean visible) {
                WindowLifecycle.this.setStatusBarVisible(visible);
            }

            /**
             * Splits the selected tab to the right.
             */
            @Override
            public void splitRight() {
                tabs.splitSelectedTabRight();
            }

            /**
             * Splits the selected tab below.
             */
            @Override
            public void splitDown() {
                tabs.splitSelectedTabDown();
            }

            /**
             * Splits the selected tab to the left.
             */
            @Override
            public void splitLeft() {
                tabs.splitSelectedTabLeft();
            }

            /**
             * Splits the selected tab above.
             */
            @Override
            public void splitUp() {
                tabs.splitSelectedTabUp();
            }

            /**
             * Reopens all tabs.
             */
            @Override
            public void reopenAllTabs() {
                tabs.reopenAllTabs();
            }

            /**
             * Closes all tabs except the selected one.
             */
            @Override
            public void closeOtherTabs() {
                tabs.closeOtherTabs();
            }

            /**
             * Returns the open tab count.
             *
             * @return open tab count
             */
            @Override
            public int openTabCount() {
                return tabs == null ? 0 : tabs.openTabCount();
            }

            /**
             * Returns the visible editor group count.
             *
             * @return visible editor group count
             */
            @Override
            public int visibleGroupCount() {
                return tabs == null ? 0 : tabs.visibleGroupCount();
            }
        });
        settingsMenu.component().add(Box.createHorizontalGlue());
        // VS Code-style title-bar search box: a rounded rectangle painted in
        // a lighter shade than the title bar, with a small magnifying-glass
        // icon on the left and the workspace name centred. Click anywhere on
        // the box to open the command palette. Hover lightens the body.
        JComponent paletteHint = buildTitleBarSearch();
        settingsMenu.component().add(paletteHint);
        settingsMenu.component().add(javax.swing.Box.createHorizontalStrut(10));
        settingsMenu.component().add(layoutMenu.component());
        setJMenuBar(settingsMenu.component());

        JPanel root = new BackdropPanel();
        root.setLayout(new BorderLayout(0, 0));
        root.setBackground(Theme.BG);
        root.setBorder(Theme.pad(0, 0, 0, 0));

        tabs = new EditorSplitArea();
        tabs.addPanel("Dashboard", dashboardPanel);
        tabs.addPanel("Analyze", createBoardTab(), this::createDetachedAnalysisTab);
        tabs.addPanel("Commands", createCommandTab());
        tabs.addPanel("Batch", createBatchTab());
        tabs.addPanel("Datasets", new LazyPanel("Datasets", this::createDatasetTab),
                () -> new LazyPanel("Datasets", this::createDetachedDatasetTab));
        tabs.addPanel("Publish", new LazyPanel("Publish", this::createPublishTab),
                () -> new LazyPanel("Publish", this::createDetachedPublishTab));
        // Console and Logs moved out of the top tab row into a VS Code-style
        // bottom dock. The top tabs are navigation destinations; Console
        // and Logs are transient output surfaces that belong below.
        tabs.addPanel("Network", new LazyPanel("Network", this::createNetworkTab),
                () -> new LazyPanel("Network", this::createDetachedNetworkTab));
        tabs.addPanel("Puzzles", new LazyPanel("Puzzles", this::createPuzzleTab),
                () -> new LazyPanel("Puzzles", this::createDetachedPuzzleTab));
        tabs.install();
        tabs.setSelectionListener(index -> onWorkbenchTabVisibilityChanged());
        tabs.select(TAB_DASHBOARD);

        bottomDock = buildBottomDock();
        bottomDock.setVisible(bottomDockVisible);
        JPanel centerStack = new JPanel(new BorderLayout(0, 0));
        centerStack.setOpaque(false);
        centerStack.add(tabs, BorderLayout.CENTER);
        centerStack.add(bottomDock, BorderLayout.SOUTH);
        root.add(centerStack, BorderLayout.CENTER);
        statusBar = createStatusBar();
        statusBar.setVisible(statusBarVisible);
        root.add(statusBar, BorderLayout.SOUTH);
        setContentPane(root);
        installKeyBindings();
        installFenPgnDropTarget();
    }

    /** Tab container for Console and Logs in the bottom dock. */
    protected JTabbedPane bottomDockTabs;

    /** Bottom-docked output panel. */
    protected JComponent bottomDock;

    /** True when the bottom dock is visible. */
    protected boolean bottomDockVisible;

    private JComponent buildBottomDock() {
        bottomDockTabs = Ui.tabbedPane();
        bottomDockTabs.addTab(DOCK_CONSOLE, createConsolePanel());
        bottomDockTabs.addTab(DOCK_LOGS, new LazyPanel(DOCK_LOGS, this::createLogTab));

        JPanel dock = new JPanel(new BorderLayout());
        dock.setOpaque(true);
        dock.setBackground(Theme.PANEL_SOLID);
        dock.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));
        JPanel header = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        header.setOpaque(true);
        header.setBackground(Theme.PANEL_SOLID);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                Theme.pad(4, 8, 4, 8)));
        JLabel title = label("Output");
        title.setFont(Theme.font(12, Font.BOLD));
        Theme.foreground(title, Theme.ForegroundRole.MUTED);
        header.add(title, BorderLayout.WEST);
        javax.swing.JButton hideButton = Ui.iconButton("Hide", event -> setBottomDockVisible(false));
        hideButton.setToolTipText("Hide bottom dock");
        header.add(hideButton, BorderLayout.EAST);
        dock.add(header, BorderLayout.NORTH);
        dock.add(bottomDockTabs, BorderLayout.CENTER);
        dock.setPreferredSize(new java.awt.Dimension(0, 240));
        return dock;
    }

    /**
     * Shows or hides the bottom dock.
     *
     * @param visible true to show the dock
     */
    protected void setBottomDockVisible(boolean visible) {
        bottomDockVisible = visible;
        if (bottomDock != null) {
            bottomDock.setVisible(visible);
            bottomDock.revalidate();
            getContentPane().revalidate();
            getContentPane().repaint();
        }
    }

    /**
     * Selects a section in the bottom dock and ensures the dock is shown.
     *
     * @param tabTitle dock tab title ("Console" or "Logs")
     */
    @Override
    public void showInBottomDock(String tabTitle) {
        if (bottomDockTabs == null) {
            return;
        }
        for (int i = 0; i < bottomDockTabs.getTabCount(); i++) {
            if (tabTitle.equals(bottomDockTabs.getTitleAt(i))) {
                bottomDockTabs.setSelectedIndex(i);
                break;
            }
        }
        setBottomDockVisible(true);
    }

    /** Opens the Console dock tab. */
    protected void showConsoleDock() {
        showInBottomDock(DOCK_CONSOLE);
    }

    /** Opens the Logs dock tab. */
    protected void showLogsDock() {
        boolean created = logPanel == null;
        LogPanel panel = primaryLogPanel();
        if (!created) {
            panel.refreshLogs();
        }
        showInBottomDock(DOCK_LOGS);
    }

    /** Opens the Logs dock tab and the logs directory. */
    protected void openLogsDockAndDirectory() {
        showLogsDock();
        runArtifacts.openLogsDirectory();
    }

    /** Refreshes all live log browsers. */
    protected void refreshLogBrowsers() {
        for (LogPanel panel : logPanels) {
            panel.refreshLogs();
        }
    }

    private void installFenPgnDropTarget() {
        new java.awt.dnd.DropTarget(getRootPane(), java.awt.dnd.DnDConstants.ACTION_COPY,
                new java.awt.dnd.DropTargetAdapter() {
                    @Override
                    public void drop(java.awt.dnd.DropTargetDropEvent event) {
                        try {
                            event.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                            java.awt.datatransfer.Transferable t = event.getTransferable();
                            String text = readDroppedFenOrPgn(t);
                            if (text != null && !text.isBlank()) {
                                final String payload = text;
                                SwingUtilities.invokeLater(() -> loadGameText(payload));
                                event.dropComplete(true);
                                return;
                            }
                            event.dropComplete(false);
                        } catch (Exception ex) {
                            event.dropComplete(false);
                            toast(Toast.Kind.ERROR, "Drop failed: " + ex.getMessage());
                        }
                    }
                }, true);
    }

    private static String readDroppedFenOrPgn(java.awt.datatransfer.Transferable t)
            throws java.io.IOException, java.awt.datatransfer.UnsupportedFlavorException {
        if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
            Object payload = t.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            if (payload instanceof java.util.List<?> files && !files.isEmpty()) {
                for (Object item : files) {
                    java.nio.file.Path path = item instanceof java.io.File file ? file.toPath() : null;
                    if (isSupportedDroppedGameFile(path)) {
                        return java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
                return null;
            }
        }
        if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
            return (String) t.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
        }
        return null;
    }

    private static boolean isSupportedDroppedGameFile(java.nio.file.Path path) {
        if (path == null || !java.nio.file.Files.isRegularFile(path) || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".fen") || name.endsWith(".pgn") || name.endsWith(".txt");
    }

    /** True when the status bar is visible. */
    protected boolean statusBarVisible = true;

    /** Bottom status-bar component. */
    protected JComponent statusBar;

    /** Status-bar current-position cell. */
    protected final JLabel statusBarPosition = new JLabel("");

    /** Status-bar current-ply cell. */
    protected final JLabel statusBarPly = new JLabel("");

    /**
     * Status-bar engine-state badge.
     */
    protected final application.gui.workbench.ui.StatusBadge statusBarEngine =
            new application.gui.workbench.ui.StatusBadge();

    /** Status-bar engine-detail cell. */
    protected final JLabel statusBarEngineDetail = new JLabel("");

    /**
     * Builds a compact status bar pinned to the south edge of the workbench.
     *
     * @return status bar component
     */
    private JComponent buildTitleBarSearch() {
        final int radius = 5;
        final int preferredWidth = 360;
        final int preferredHeight = 22;
        final boolean[] hovered = { false };
        JComponent box = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(preferredWidth, preferredHeight);
            }

            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(preferredWidth, preferredHeight);
            }

            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    java.awt.Color fill = hovered[0] ? Theme.TAB_HOVER : Theme.ELEVATED_SOLID;
                    g.setColor(fill);
                    g.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);
                    g.setColor(Theme.LINE);
                    g.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
                    // Magnifying glass: small circle outline + diagonal handle.
                    int iconCx = 12;
                    int iconCy = h / 2;
                    int circleR = 4;
                    g.setColor(Theme.MUTED);
                    g.setStroke(new java.awt.BasicStroke(1.4f,
                            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    g.drawOval(iconCx - circleR, iconCy - circleR, circleR * 2, circleR * 2);
                    g.drawLine(iconCx + circleR - 1, iconCy + circleR - 1,
                            iconCx + circleR + 3, iconCy + circleR + 3);
                    // Workspace label, centred.
                    g.setFont(Theme.font(11, java.awt.Font.PLAIN));
                    g.setColor(Theme.MUTED);
                    java.awt.FontMetrics fm = g.getFontMetrics();
                    String label = "Command Palette";
                    int textX = (w - fm.stringWidth(label)) / 2;
                    int textY = (h + fm.getAscent() - fm.getDescent()) / 2 - 1;
                    g.drawString(label, textX, textY);
                } finally {
                    g.dispose();
                }
            }
        };
        box.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        box.setToolTipText("Open command palette (Ctrl+K)");
        box.setOpaque(false);
        box.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                hovered[0] = true;
                box.repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                hovered[0] = false;
                box.repaint();
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                showCommandPalette();
            }
        });
        return box;
    }

    /**
     * Builds the workbench status bar.
     *
     * @return status bar component
     */
    protected JComponent createStatusBar() {
        JPanel bar = Ui.transparentPanel(new java.awt.GridBagLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                Theme.pad(6, 14, 6, 14)));
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        c.weighty = 0.0;

        // Each segment is wrapped in a click cell so the bar gains useful
        // surface area: position cell copies the FEN, ply cell jumps to
        // start/end, engine cell opens engine settings. Hover paints a
        // subtle background so the affordance is discoverable.
        c.gridx = 0;
        c.weightx = 0.45;
        bar.add(statusClickCell(statusBarPosition, "Copy current FEN",
                this::copyStatusBarFen), c);

        c.gridx = 1;
        c.weightx = 0.25;
        bar.add(statusClickCell(statusBarPly, "Jump to start / end of the line",
                this::toggleStatusBarPlyJump), c);

        JPanel engineCell = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        statusBarEngine.setFont(Theme.font(11, java.awt.Font.PLAIN));
        statusBarEngine.idle("idle");
        statusBarEngine.setFixedTextWidth(
                statusBarEngine.getFontMetrics(statusBarEngine.getFont()).stringWidth("starting"));
        engineCell.add(statusBarEngine, BorderLayout.WEST);
        engineCell.add(statusBarEngineDetail, BorderLayout.CENTER);
        c.gridx = 2;
        c.weightx = 0.3;
        bar.add(statusClickCell(engineCell, "Open engine settings",
                this::showEngineSettings), c);

        for (JLabel label : new JLabel[] { statusBarPosition, statusBarPly, statusBarEngineDetail }) {
            label.setFont(Theme.font(11, java.awt.Font.PLAIN));
            Theme.foreground(label, Theme.ForegroundRole.MUTED);
        }
        statusBarEngineDetail.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11));
        statusBarPosition.setHorizontalAlignment(SwingConstants.LEFT);
        statusBarPly.setHorizontalAlignment(SwingConstants.CENTER);
        statusBarEngineDetail.setHorizontalAlignment(SwingConstants.RIGHT);

        // Bell at the far right opens a popover with the recent toast
        // history so users can recover paths/messages they missed.
        c.gridx = 3;
        c.weightx = 0.0;
        c.fill = java.awt.GridBagConstraints.NONE;
        bar.add(buildNotificationBell(), c);
        return bar;
    }

    private JComponent buildNotificationBell() {
        final boolean[] hovered = { false };
        final boolean[] unread = { false };
        JComponent bell = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(26, 22);
            }

            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    if (hovered[0]) {
                        g.setColor(Theme.TAB_HOVER);
                        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
                    }
                    g.setColor(Theme.MUTED);
                    g.setStroke(new java.awt.BasicStroke(1.4f,
                            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    // Bell body: rounded triangle.
                    g.drawLine(cx - 5, cy + 3, cx + 5, cy + 3);
                    g.drawLine(cx - 5, cy + 3, cx - 4, cy - 4);
                    g.drawLine(cx + 5, cy + 3, cx + 4, cy - 4);
                    g.drawLine(cx - 4, cy - 4, cx + 4, cy - 4);
                    // Bell clapper.
                    g.fillOval(cx - 1, cy + 3, 3, 3);
                    if (unread[0]) {
                        g.setColor(Theme.ACCENT);
                        g.fillOval(getWidth() - 9, 2, 6, 6);
                    }
                } finally {
                    g.dispose();
                }
            }
        };
        bell.setOpaque(false);
        bell.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        bell.setToolTipText("Recent notifications");
        bell.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                hovered[0] = true;
                bell.repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                hovered[0] = false;
                bell.repaint();
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                unread[0] = false;
                bell.repaint();
                showNotificationHistory(bell);
            }
        });
        Toast.addHistoryListener(() -> SwingUtilities.invokeLater(() -> {
            unread[0] = true;
            bell.repaint();
        }));
        return bell;
    }

    private void showNotificationHistory(JComponent anchor) {
        java.util.List<Toast.Entry> entries = Toast.history();
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        popup.setOpaque(true);
        popup.setBackground(Theme.PANEL_SOLID);
        popup.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        if (entries.isEmpty()) {
            JLabel empty = new JLabel("No notifications yet.");
            empty.setBorder(Theme.pad(8, 14, 8, 14));
            empty.setForeground(Theme.MUTED);
            popup.add(empty);
        } else {
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
            for (Toast.Entry entry : entries) {
                JPanel row = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
                row.setBorder(Theme.pad(4, 12, 4, 12));
                String time = java.time.LocalDateTime
                        .ofInstant(java.time.Instant.ofEpochMilli(entry.timestamp()),
                                java.time.ZoneId.systemDefault())
                        .format(fmt);
                JLabel timeLabel = new JLabel(time);
                timeLabel.setFont(Theme.font(11, java.awt.Font.PLAIN));
                timeLabel.setForeground(Theme.MUTED);
                JLabel msg = new JLabel("<html>" + entry.message().replace("<", "&lt;") + "</html>");
                msg.setFont(Theme.font(12, java.awt.Font.PLAIN));
                Theme.foreground(msg, Theme.ForegroundRole.TEXT);
                row.add(timeLabel, BorderLayout.WEST);
                row.add(msg, BorderLayout.CENTER);
                popup.add(row);
            }
        }
        int width = Math.max(360, anchor.getWidth());
        popup.setPreferredSize(new java.awt.Dimension(width, Math.min(360, 24 + entries.size() * 26 + 16)));
        popup.show(anchor, -width + anchor.getWidth(), -popup.getPreferredSize().height);
    }

    private JPanel statusClickCell(JComponent content, String tooltip, Runnable onClick) {
        final boolean[] hovered = { false };
        JPanel cell = new JPanel(new BorderLayout()) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                if (hovered[0]) {
                    java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                    try {
                        g.setColor(Theme.TAB_HOVER);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    } finally {
                        g.dispose();
                    }
                }
            }
        };
        cell.setOpaque(false);
        cell.setBorder(Theme.pad(2, 6, 2, 6));
        cell.add(content, BorderLayout.CENTER);
        Runnable action = onClick == null ? () -> {
            // no-op fallback
        } : onClick;
        java.awt.event.MouseAdapter listener = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                hovered[0] = true;
                cell.repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                java.awt.Component source = (java.awt.Component) event.getSource();
                java.awt.Point point = SwingUtilities.convertPoint(source, event.getPoint(), cell);
                if (!cell.contains(point)) {
                    hovered[0] = false;
                    cell.repaint();
                }
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    action.run();
                }
            }
        };
        installStatusCellInteraction(cell, tooltip, listener);
        return cell;
    }

    private static void installStatusCellInteraction(
            java.awt.Component component,
            String tooltip,
            java.awt.event.MouseListener listener) {
        component.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        component.addMouseListener(listener);
        if (component instanceof JComponent jComponent) {
            jComponent.setToolTipText(tooltip);
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                installStatusCellInteraction(child, tooltip, listener);
            }
        }
    }

    private void copyStatusBarFen() {
        if (currentPosition == null) {
            return;
        }
        copyText(currentPosition.toString());
        toast(Toast.Kind.SUCCESS, "FEN copied");
    }

    private void toggleStatusBarPlyJump() {
        if (tabs != null && tabs.selectedIndex() == TAB_PUZZLES) {
            if (puzzlePanel != null) {
                puzzlePanel.toggleReviewStartEnd();
            }
            return;
        }
        if (gameModel == null) {
            return;
        }
        if (gameModel.currentPly() > 0) {
            jumpActivePositionToStart();
        } else {
            jumpActivePositionToEnd();
        }
    }

    /**
     * Returns whether the status bar is visible.
     *
     * @return true when visible
     */
    protected boolean isStatusBarVisible() {
        return statusBarVisible;
    }

    /**
     * Sets and persists status-bar visibility.
     *
     * @param visible true to show the status bar
     */
    protected void setStatusBarVisible(boolean visible) {
        statusBarVisible = visible;
        WORKBENCH_PREFS.putBoolean(PREF_STATUS_BAR_VISIBLE, visible);
        if (statusBar != null) {
            statusBar.setVisible(visible);
            statusBar.revalidate();
        }
        if (layoutMenu != null) {
            layoutMenu.refreshTheme();
        }
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    /** Maximum compact FEN characters shown in the status bar. */
    protected static final int STATUS_FEN_BUDGET = 60;

    /** Refreshes status-bar text from the current game state. */
    protected void refreshStatusBar() {
        if (currentPosition == null) {
            statusBarPosition.setText("");
            statusBarPosition.setToolTipText(null);
            statusBarPly.setText("");
            return;
        }
        statusBarPosition.setText(positionStatusLabel());
        statusBarPosition.setToolTipText(currentPosition.toString());
        int ply = gameModel.currentPly();
        int last = gameModel.lastPly();
        statusBarPly.setText("ply " + ply + " / " + last + "  ·  "
                + (currentPosition.isWhiteToMove() ? "white to move" : "black to move"));
    }

    private String positionStatusLabel() {
        int ply = gameModel.currentPly();
        if (ply <= 0) {
            return chess.core.Setup.getStandardStartFEN().equals(currentPosition.toString())
                    ? "start position"
                    : "imported position";
        }
        String sanLine = gameModel.sanLine();
        if (sanLine != null && !sanLine.isBlank()) {
            String[] tokens = sanLine.trim().split("\\s+");
            if (tokens.length > 0) {
                String last = tokens[tokens.length - 1];
                int moveNumber = (ply + 1) / 2;
                // Plain ply N corresponds to white's Nth move; even ply means
                // black just moved, so the standard "5…Nc6" ellipsis applies.
                boolean blackJustMoved = ply % 2 == 0;
                return "after " + moveNumber + (blackJustMoved ? "…" : ".") + last;
            }
        }
        return "ply " + ply;
    }

    /**
     * Updates the status-bar engine badge.
     *
     * @param text status text
     */
    protected void setStatusBarEngine(String text) {
        String message = text == null ? "" : text.trim();
        if (message.isEmpty() || "idle".equalsIgnoreCase(message)) {
            statusBarEngine.idle("idle");
            statusBarEngineDetail.setText("");
            return;
        }
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("live paused")) {
            statusBarEngine.idle("paused");
            statusBarEngineDetail.setText(message.substring("live paused".length()).trim());
            return;
        }
        if (lower.startsWith("live failed")) {
            statusBarEngine.error("error");
            statusBarEngineDetail.setText(message.substring("live failed".length()).trim());
            return;
        }
        if (lower.startsWith("live starting")) {
            statusBarEngine.busy("starting");
            statusBarEngineDetail.setText(message.substring("live starting".length()).trim());
            return;
        }
        if (lower.startsWith("live config")) {
            statusBarEngine.busy("config");
            statusBarEngineDetail.setText(message.substring("live config".length()).trim());
            return;
        }
        if (lower.startsWith("live thinking")) {
            statusBarEngine.busy("live");
            statusBarEngineDetail.setText(message.substring("live thinking".length()).trim());
            return;
        }
        if (lower.startsWith("live")) {
            statusBarEngine.busy("live");
            statusBarEngineDetail.setText(message.substring("live".length()).trim());
            return;
        }
        // Unknown free-form text — keep the badge fixed as "idle" and route the
        // payload to the detail line so it still surfaces.
        statusBarEngine.idle("idle");
        statusBarEngineDetail.setText(message);
    }

    protected void toast(Toast.Kind kind, String message) {
        Toast.show(this, kind, message);
    }

    /** Installs global workbench key bindings. */
    protected void installKeyBindings() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control K"), "openCommandPalette");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("meta K"), "openCommandPalette");
        getRootPane().getActionMap().put("openCommandPalette", new AbstractAction() {
            protected static final long serialVersionUID = 1L;

            /**
             * Opens the command palette.
             *
             * @param event action event
             */
            @Override
            public void actionPerformed(ActionEvent event) {
                showCommandPalette();
            }
        });
        bindWindowAction("control P", "openPgnExplorer", event -> showPgnExplorer());
        bindWindowAction("meta P", "openPgnExplorerMeta", event -> showPgnExplorer());
        // VS Code-style Ctrl+` toggles the bottom Console/Logs dock.
        bindWindowAction("control BACK_QUOTE", "toggleBottomDock",
                event -> setBottomDockVisible(!bottomDockVisible));
        bindWindowAction("meta BACK_QUOTE", "toggleBottomDockMeta",
                event -> setBottomDockVisible(!bottomDockVisible));
        bindWindowAction("control COMMA", "openDisplaySettings", event -> showDisplaySettings());
        bindWindowAction("meta COMMA", "openDisplaySettingsMeta", event -> showDisplaySettings());
        bindWindowAction("alt LEFT", "navigateBack", event -> navigateActivePosition(-1));
        bindWindowAction("alt RIGHT", "navigateForward", event -> navigateActivePosition(1));
        bindWindowAction("alt UP", "navigateStart", event -> jumpActivePositionToStart());
        bindWindowAction("alt DOWN", "navigateEnd", event -> jumpActivePositionToEnd());
        bindWindowAction("control TAB", "nextWorkbenchTab", event -> tabs.selectNextTab());
        bindWindowAction("control shift TAB", "previousWorkbenchTab", event -> tabs.selectPreviousTab());
        bindWindowAction("control PAGE_DOWN", "nextWorkbenchTabPage", event -> tabs.selectNextTab());
        bindWindowAction("control PAGE_UP", "previousWorkbenchTabPage", event -> tabs.selectPreviousTab());
        bindWindowAction("control W", "closeWorkbenchTab", event -> tabs.closeSelectedTab());
        bindWindowAction("control BACK_SLASH", "splitWorkbenchTabRight",
                event -> tabs.splitSelectedTabRight());
        bindWindowAction("control shift BACK_SLASH", "splitWorkbenchTabDown",
                event -> tabs.splitSelectedTabDown());
        installTabNumberShortcuts();
        bindWindowAction("ESCAPE", "stopRunningCommand", event -> stopCommand());
        bindPositionNavigation(PREVIOUS_POSITION_KEYS, "previousPosition", event -> navigateActivePosition(-1));
        bindPositionNavigation(NEXT_POSITION_KEYS, "nextPosition", event -> navigateActivePosition(1));
        bindPositionNavigation(FIRST_POSITION_KEYS, "firstPosition", event -> jumpActivePositionToStart());
        bindPositionNavigation(LAST_POSITION_KEYS, "lastPosition", event -> jumpActivePositionToEnd());
    }

    /**
     * Navigates the active position surface by a relative delta.
     *
     * @param delta ply delta
     */
    protected void navigateActivePosition(int delta) {
        if (tabs != null && tabs.selectedIndex() == TAB_PUZZLES) {
            if (puzzlePanel != null) {
                puzzlePanel.navigateReview(delta);
            }
            return;
        }
        navigateGame(delta);
    }

    /** Jumps the active position surface to its start. */
    protected void jumpActivePositionToStart() {
        if (tabs != null && tabs.selectedIndex() == TAB_PUZZLES) {
            if (puzzlePanel != null) {
                puzzlePanel.jumpReviewToStart();
            }
            return;
        }
        jumpGameTo(0);
    }

    /** Jumps the active position surface to its end. */
    protected void jumpActivePositionToEnd() {
        if (tabs != null && tabs.selectedIndex() == TAB_PUZZLES) {
            if (puzzlePanel != null) {
                puzzlePanel.jumpReviewToEnd();
            }
            return;
        }
        jumpGameTo(gameModel.lastPly());
    }

    /**
     * Returns every keyboard shortcut used for active-line navigation.
     *
     * @return navigation key strokes
     */
    protected static KeyStroke[] allPositionNavigationKeyStrokes() {
        KeyStroke[] out = new KeyStroke[
                PREVIOUS_POSITION_KEYS.length
                        + NEXT_POSITION_KEYS.length
                        + FIRST_POSITION_KEYS.length
                        + LAST_POSITION_KEYS.length];
        int offset = 0;
        offset = copyKeys(PREVIOUS_POSITION_KEYS, out, offset);
        offset = copyKeys(NEXT_POSITION_KEYS, out, offset);
        offset = copyKeys(FIRST_POSITION_KEYS, out, offset);
        copyKeys(LAST_POSITION_KEYS, out, offset);
        return out;
    }

    private static int copyKeys(KeyStroke[] source, KeyStroke[] target, int offset) {
        System.arraycopy(source, 0, target, offset, source.length);
        return offset + source.length;
    }

    private static KeyStroke[] keyStrokes(int... keyCodes) {
        KeyStroke[] keys = new KeyStroke[keyCodes.length];
        for (int i = 0; i < keyCodes.length; i++) {
            keys[i] = KeyStroke.getKeyStroke(keyCodes[i], 0);
        }
        return keys;
    }

    /** Installs Ctrl+number shortcuts for visible workbench tabs. */
    protected void installTabNumberShortcuts() {
        int tabLimit = Math.min(9, tabs.count());
        for (int i = 0; i < tabLimit; i++) {
            int tabIndex = i;
            int visibleNumber = i + 1;
            bindWindowAction("control " + visibleNumber, "openWorkbenchTab" + visibleNumber,
                    event -> selectTab(tabIndex));
        }
    }

    /**
     * Binds a family of position-navigation shortcuts.
     *
     * @param keyStrokes shortcuts to bind
     * @param name action-name prefix
     * @param body action body
     */
    protected void bindPositionNavigation(KeyStroke[] keyStrokes, String name,
            Consumer<ActionEvent> body) {
        for (int i = 0; i < keyStrokes.length; i++) {
            bindPositionNavigation(keyStrokes[i], name + i, body);
        }
    }

    /**
     * Binds one position-navigation shortcut.
     *
     * @param keyStroke shortcut to bind
     * @param name action name
     * @param body action body
     */
    protected void bindPositionNavigation(KeyStroke keyStroke, String name,
            Consumer<ActionEvent> body) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            protected static final long serialVersionUID = 1L;

            /**
             * Runs position navigation when focus routing allows it.
             *
             * @param event action event
             */
            @Override
            public void actionPerformed(ActionEvent event) {
                if (shouldRoutePositionNavigation()) {
                    body.accept(event);
                }
            }
        });
    }

    /**
     * Returns whether position-navigation shortcuts should be handled.
     *
     * @return true when shortcuts should navigate the active position
     */
    protected boolean shouldRoutePositionNavigation() {
        if (tabs == null) {
            return false;
        }
        int selectedTab = tabs.selectedIndex();
        if (selectedTab != TAB_ANALYZE && selectedTab != TAB_PUZZLES) {
            return false;
        }
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return shouldRoutePositionNavigation(focusOwner);
    }

    /**
     * Returns whether a focus owner permits position-navigation shortcuts.
     *
     * @param focusOwner current focus owner
     * @return true when navigation should be routed
     */
    protected static boolean shouldRoutePositionNavigation(Component focusOwner) {
        if (focusOwner == null) {
            return true;
        }
        return !(focusOwner instanceof JTextComponent
                || focusOwner instanceof JTable
                || focusOwner instanceof JList<?>
                || focusOwner instanceof JComboBox<?>
                || focusOwner instanceof JSpinner);
    }

    /**
     * Binds one window-scoped shortcut action.
     *
     * @param keyStroke Swing key-stroke descriptor
     * @param name action name
     * @param body action body
     */
    protected void bindWindowAction(String keyStroke, String name,
            Consumer<ActionEvent> body) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyStroke), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            protected static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent event) {
                body.accept(event);
            }
        });
    }

    /** Opens the command palette. */
    protected void showCommandPalette() {
        if (commandPalette == null) {
            commandPalette = new CommandPalette(this);
        }
        commandPalette.showActions(commandPaletteActions());
    }

    /** Opens the PGN explorer. */
    protected void showPgnExplorer() {
        if (pgnExplorer == null) {
            pgnExplorer = new PgnExplorerDialog(this, this::loadGameText);
        }
        pgnExplorer.showExplorer(gameModel.pgn());
    }

    /**
     * Builds the current command-palette action list.
     *
     * @return palette actions
     */
    protected List<PaletteAction> commandPaletteActions() {
        // Arrays.asList avoids List.of's "too many varargs" type-inference
        // hiccup when the list grows past a handful of entries.
        return java.util.Arrays.asList(
    new PaletteAction("Engine", "Analyze board", "Run built-in search on the current FEN", this::runBuiltInSearch),
    new PaletteAction("Engine", "Best move", "Run engine bestmove with the current duration", this::runBestMove),
    new PaletteAction("Engine", "Engine analysis", "Run multipv analysis for the current position", this::runAnalyze),
    new PaletteAction("Engine", "Live external engine", "Toggle continuous UCI analysis for the board",
                        () -> setLiveExternalEngineEnabled(!liveExternalEngineEnabled)),
    new PaletteAction("View", "Analysis data", "Show live evaluation, depth, and speed graphs",
                        this::showAnalysisData),
    new PaletteAction("Copy", "Copy analysis CSV", "Copy live analysis graph data as CSV",
                        this::copyAnalysisCsv),
    new PaletteAction("Copy", "Copy analysis report", "Copy the live analysis summary report",
                        this::copyAnalysisReport),
    new PaletteAction("Engine", "Print analysis report", "Print the live analysis graph report",
                        this::printAnalysisReport),
    new PaletteAction("Engine", "Position tags", "Generate tags for the current FEN", this::runTagsCommand),
    new PaletteAction("Debug", "Perft", "Run perft with the selected depth and threads", this::runPerft),
    new PaletteAction("Run", "Run built command", "Execute the selected command template", this::runSelectedTemplate),
    new PaletteAction("Copy", "Copy built command", "Copy the command-builder preview", this::copyBuiltCommand),
    new PaletteAction("Edit", "Reset built command", "Restore default command-builder options",
                        this::resetSelectedTemplate),
    new PaletteAction("View", "Filter command flags", "Focus the command-builder option filter",
                        this::focusOptionFilter),
    new PaletteAction("Run", "Run batch", "Execute the selected batch workflow", batchPanel::runBatch),
    new PaletteAction("Run", "Run publishing", "Execute the selected publishing workflow", this::runPublishingCommand),
    new PaletteAction("Run", "Generate report", "Refresh the report maker output", this::generateReport),
    new PaletteAction("Copy", "Copy report", "Copy the current report output", this::copyReport),
    new PaletteAction("Copy", "Copy FEN", "Copy the current board FEN", () -> copyText(fenField.getText())),
    new PaletteAction("View", "Toggle dark mode", "Switch the workbench appearance palette",
                        () -> setDarkMode(!Theme.isDark())),
    new PaletteAction("Settings", "Board settings", "Adjust board highlights, arrows, notation, animations, and eval",
                        this::showDisplaySettings),
    new PaletteAction("Settings", "Engine settings", "Adjust external engine protocol, nodes, and hash",
                        this::showEngineSettings),
    new PaletteAction("Engine", "Engine smoke test", "Validate that the configured UCI engine starts",
                        this::runEngineSmoke),
    new PaletteAction("Debug", "Validate config", "Run config validation", this::runConfigValidate),
    new PaletteAction("View", "Focus FEN", "Move focus to the position field", this::focusFenField),
    new PaletteAction("Run", "Stop command", "Cancel the running child process", this::stopCommand),
    new PaletteAction("View", "Open analyze tab", "Show board analysis tools", () -> selectTab(TAB_ANALYZE)),
    new PaletteAction("View", "New analyze tab", "Open another independent analysis workspace",
                        this::openNewAnalyzeTab),
    new PaletteAction("View", "Focus game line", "Show the merged game tools", this::focusGameInput),
    new PaletteAction("File", "PGN explorer", "Search the current PGN or open a PGN file", this::showPgnExplorer),
    new PaletteAction("View", "Open commands tab", "Show command controller", () -> selectTab(TAB_COMMANDS)),
    new PaletteAction("View", "Open batch tab", "Show batch workflows", () -> selectTab(TAB_BATCH)),
    new PaletteAction("View", "Open datasets tab", "Inspect and analyze training datasets",
                        () -> selectTab(TAB_DATASETS)),
    new PaletteAction("Dataset", "Analyze dataset", "Scan the selected dataset source",
                        () -> datasetPanel().analyzeCurrentSource()),
    new PaletteAction("View", "Open publish tab", "Show report and publishing tools", () -> selectTab(TAB_PUBLISH)),
    new PaletteAction("View", "Show Console", "Open the bottom command-output dock",
                        this::showConsoleDock),
    new PaletteAction("View", "Show Logs", "Open and refresh the bottom log browser",
                        this::showLogsDock),
    new PaletteAction("View", "Toggle bottom dock", "Show or hide the Console + Logs dock",
                        () -> setBottomDockVisible(!bottomDockVisible)),
    new PaletteAction("View", "Open puzzles tab", "Train PGN tactics with variation branches",
                        () -> selectTab(TAB_PUZZLES)),
    new PaletteAction("Workbench", "Split tab right", "Move the active workbench tab into a right editor group",
                        tabs::splitSelectedTabRight),
    new PaletteAction("Workbench", "Split tab down", "Move the active workbench tab into a lower editor group",
                        tabs::splitSelectedTabDown),
    new PaletteAction("Workbench", "Close active tab", "Hide the active workbench tab", tabs::closeSelectedTab),
    new PaletteAction("Workbench", "Reopen all tabs", "Restore every hidden workbench tab", tabs::reopenAllTabs),
    new PaletteAction("File", "Open logs folder", "Show persisted workbench command logs",
                        this::openLogsDockAndDirectory));
    }

    /** Lazily-created unified settings dialog. */
    private SettingsDialog settingsDialog;

    /** Shared modal overlay for settings and inspectors. */
    private application.gui.workbench.ui.ModalOverlay overlay;

    /**
     * Returns the shared modal overlay.
     *
     * @return modal overlay
     */
    protected application.gui.workbench.ui.ModalOverlay overlay() {
        if (overlay == null) {
            overlay = new application.gui.workbench.ui.ModalOverlay(this);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent event) {
                    overlay.revalidateBounds();
                }
            });
        }
        return overlay;
    }

    /**
     * Returns the unified settings dialog.
     *
     * @return settings dialog
     */
    protected SettingsDialog settingsDialog() {
        if (settingsDialog == null) {
            settingsDialog = new SettingsDialog(overlay());
            settingsDialog.addSection("Display", createDisplaySettingsPanel());
            settingsDialog.addSection("Engine", createEngineSettingsPanel());
        }
        return settingsDialog;
    }

    protected void showDisplaySettings() {
        SettingsDialog dialog = settingsDialog();
        dialog.selectSection("Display");
        dialog.showCentered();
    }

    protected void showEngineSettings() {
        SettingsDialog dialog = settingsDialog();
        dialog.selectSection("Engine");
        dialog.showCentered();
        SwingUtilities.invokeLater(engineProtocolField::requestFocusInWindow);
    }

    protected void showAnalysisData() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(0);
        }
        if (boardDetailTabs != null) {
            selectBoardDetailTab("Data");
        }
    }

    private void selectBoardDetailTab(String title) {
        if (boardDetailTabs == null || title == null) {
            return;
        }
        for (int i = 0; i < boardDetailTabs.getTabCount(); i++) {
            if (title.equals(boardDetailTabs.getTitleAt(i))) {
                boardDetailTabs.setSelectedIndex(i);
                return;
            }
        }
    }

    protected void copyAnalysisCsv() {
        copyText(analysisGraph.csvText());
    }

    protected void copyAnalysisReport() {
        copyText(analysisGraph.reportText());
    }

    protected void printAnalysisReport() {
        if (!analysisGraph.hasSamples()) {
            toast(Toast.Kind.WARNING, "No analysis data to print");
            return;
        }
        try {
            boolean submitted = analysisGraph.printReport();
            if (submitted) {
                toast(Toast.Kind.SUCCESS, "Analysis report sent to printer");
            }
        } catch (PrinterException ex) {
            showError("Print report failed", ex.getMessage());
        }
    }

    protected void clearAnalysisData() {
        analysisGraph.clearSamples();
        toast(Toast.Kind.INFO, "Analysis data cleared");
    }

    /**
     * Creates one settings toggle row.
     *
     * @param text visible label
     * @param tooltip tooltip text
     * @param selected initial state
     * @param onChange change callback
     * @return settings toggle row
     */
    protected SettingsChipRow settingsToggle(String text, String tooltip, boolean selected,
            Consumer<Boolean> onChange) {
        return new SettingsChipRow(text, tooltip, selected, onChange);
    }

    /**
     * Adds a settings toggle to a grid panel.
     *
     * @param panel target panel
     * @param c grid constraints
     * @param row target row
     * @param text visible label
     * @param tooltip tooltip text
     * @param selected initial state
     * @param update setting update callback
     * @param refreshEval true to refresh evaluation after the change
     */
    protected void addSettingsToggle(JPanel panel, GridBagConstraints c, int row, String text, String tooltip,
            boolean selected, Consumer<Boolean> update, boolean refreshEval) {
        grid(panel, settingsToggle(text, tooltip, selected,
                value -> updateDisplaySetting(() -> update.accept(value), refreshEval)), c, 0, row, 1, 1);
    }

    /**
     * Persists a display setting update and reapplies board state.
     *
     * @param update update to run before saving
     * @param refreshEval true to refresh evaluation after the change
     */
    protected void updateDisplaySetting(Runnable update, boolean refreshEval) {
        update.run();
        saveDisplaySettings();
        applyDisplaySettings(refreshEval);
    }

    protected void focusFenField() {
        selectTab(TAB_ANALYZE);
        SwingUtilities.invokeLater(fenField::requestFocusInWindow);
    }

    protected void focusOptionFilter() {
        selectTab(TAB_COMMANDS);
        SwingUtilities.invokeLater(() -> {
            commandForm.expandOptionalFlags();
            commandForm.filterField().requestFocusInWindow();
        });
    }

    protected void focusGameInput() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(1);
        }
        SwingUtilities.invokeLater(gameInput::requestFocusInWindow);
    }

    /** Opens another independent Analyze tab. */
    protected void openNewAnalyzeTab() {
        if (tabs != null) {
            tabs.duplicate(TAB_ANALYZE);
        }
    }

    protected void selectTab(int index) {
        if (tabs != null && index >= 0 && index < tabs.count()) {
            tabs.select(index);
        }
    }

    /** Refreshes dependent surfaces after tab visibility changes. */
    protected void onWorkbenchTabVisibilityChanged() {
        if (tabs != null) {
            for (application.gui.workbench.network.NetworkPanel panel : networkPanels) {
                panel.setActive(SwingUtilities.isDescendingFrom(panel, tabs));
            }
        }
        for (LogPanel panel : logPanels) {
            if (tabs != null && SwingUtilities.isDescendingFrom(panel, tabs)) {
                panel.refreshLogs();
            }
        }
        if (liveExternalEngineEnabled && isAnalyzePaneVisible()) {
            requestLiveAnalysisUpdate();
        } else if (hasLiveEngineWorker()) {
            pauseHiddenLiveAnalysis();
        } else if (liveExternalEngineEnabled) {
            setStatusBarEngine("live paused");
            session.updateEngine(engineProtocolValue(), true, "paused");
        }
    }

    /**
     * Returns whether an Analyze pane is visible.
     *
     * @return true when Analyze is visible
     */
    protected boolean isAnalyzePaneVisible() {
        return tabs != null && tabs.isVisibleInPane(TAB_ANALYZE);
    }

}
