package application.gui.workbench.window;

import application.Config;
import application.gui.workbench.Defaults;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.command.CommandPalette.PaletteAction;
import application.gui.workbench.command.CommandPalette;
import application.gui.workbench.game.PgnExplorerDialog;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.layout.LazyPanel;
import application.gui.workbench.layout.ViewRegistry;
import application.gui.workbench.layout.RegisteredView;
import application.gui.workbench.mcts.MctsWorkspacePanel;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.session.Job;
import application.gui.workbench.session.JobStatus;
import application.gui.workbench.session.LogPanel;
import application.gui.workbench.ui.BackdropPanel;
import application.gui.workbench.ui.SettingsChipRow;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
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
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.trimmed;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */

public abstract class WindowLifecycle extends WindowBase {
    /**
     * Serialization identifier for Swing frame compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Top-level application menu.
     */
    protected transient SettingsMenu settingsMenu;

    /**
     * Title-bar layout control menu.
     */
    protected transient LayoutMenu layoutMenu;

    /**
     * Listener that mirrors global sound settings into window controls.
     */
    private final transient Runnable soundSettingsListener = this::syncSoundSettingsControls;

    /**
     * Toast history listener driving the status-bar notification bell, removed on
     * disposal so it does not leak across window instances.
     */
    private transient Runnable bellHistoryListener;

    /**
     * Preferences node used for persisted workbench state.
     */
    protected static final Preferences WORKBENCH_PREFS =
            Preferences.userNodeForPackage(Window.class);

    /**
     * Preference key for the appearance theme mode.
     */
    protected static final String PREF_THEME_MODE = "appearance.themeMode";

    /**
     * Preference key for the UI density preset.
     */
    protected static final String PREF_DENSITY = "appearance.density";

    /**
     * Preference key for status-bar visibility.
     */
    protected static final String PREF_STATUS_BAR_VISIBLE = "layout.statusBarVisible";

    /**
     * Preference key for the chess-piece artwork set.
     */
    protected static final String PREF_PIECE_SET = "display.pieceSet";

    /**
     * Preference key for legal-move hover previews.
     */
    protected static final String PREF_SHOW_LEGAL_MOVES = "display.showLegalMovePreview";

    /**
     * Preference key for the last-move highlight.
     */
    protected static final String PREF_SHOW_LAST_MOVE = "display.showLastMoveHighlight";

    /**
     * Preference key for best-move board arrows.
     */
    protected static final String PREF_SHOW_BEST_ARROWS = "display.showBestMoveArrows";

    /**
     * Preference key for board coordinates.
     */
    protected static final String PREF_SHOW_COORDINATES = "display.showCoordinates";

    /**
     * Preference key for board animations.
     */
    protected static final String PREF_BOARD_ANIMATIONS = "display.boardAnimations";

    /**
     * Preference key for undefended-piece board badges.
     */
    protected static final String PREF_SHOW_UNDEFENDED_PIECES = "display.showUndefendedPieces";

    /**
     * Preference key for pinned-piece board badges.
     */
    protected static final String PREF_SHOW_PINNED_PIECES = "display.showPinnedPieces";

    /**
     * Preference key for checkable-king board badges.
     */
    protected static final String PREF_SHOW_CHECKABLE_KING = "display.showCheckableKing";

    /**
     * Preference key for automatic eval-bar updates.
     */
    protected static final String PREF_AUTO_EVAL_BAR = "display.autoEvalBar";

    /**
     * Preference key for continuous external-engine analysis.
     */
    protected static final String PREF_LIVE_EXTERNAL_ENGINE = "engine.liveExternal";

    /**
     * Preference key for the external-engine protocol path.
     */
    protected static final String PREF_ENGINE_PROTOCOL = "engine.protocolPath";

    /**
     * Preference key for the external-engine node limit.
     */
    protected static final String PREF_ENGINE_NODES = "engine.maxNodes";

    /**
     * Preference key for the external-engine hash size.
     */
    protected static final String PREF_ENGINE_HASH = "engine.hash";

    /**
     * Default window width in pixels.
     */
    protected static final int DEFAULT_WINDOW_WIDTH = 1440;

    /**
     * Default window height in pixels.
     */
    protected static final int DEFAULT_WINDOW_HEIGHT = 920;

    /**
     * Minimum usable workbench width in pixels.
     */
    protected static final int MIN_WINDOW_WIDTH = 1040;

    /**
     * Minimum usable workbench height in pixels.
     */
    protected static final int MIN_WINDOW_HEIGHT = 700;

    /**
     * Keyboard shortcuts that navigate to the previous move.
     */
    private static final KeyStroke[] PREVIOUS_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_LEFT,
            KeyEvent.VK_KP_LEFT,
            KeyEvent.VK_NUMPAD4);

    /**
     * Keyboard shortcuts that navigate to the next move.
     */
    private static final KeyStroke[] NEXT_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_RIGHT,
            KeyEvent.VK_KP_RIGHT,
            KeyEvent.VK_NUMPAD6);

    /**
     * Keyboard shortcuts that jump to the first move.
     */
    private static final KeyStroke[] FIRST_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_UP,
            KeyEvent.VK_KP_UP,
            KeyEvent.VK_NUMPAD8,
            KeyEvent.VK_HOME);

    /**
     * Keyboard shortcuts that jump to the last move.
     */
    private static final KeyStroke[] LAST_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_DOWN,
            KeyEvent.VK_KP_DOWN,
            KeyEvent.VK_NUMPAD2,
            KeyEvent.VK_END);

    /**
     * Restores persisted window size, position, and maximized state.
     */
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

    /**
     * Persists the current window geometry.
     */
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

    /**
     * Loads the persisted appearance settings (UI density first, then theme
     * mode) before the UI is built so both apply to freshly-constructed panels.
     */
    protected void loadThemeSetting() {
        Theme.setDensity(Theme.Density.fromPreference(
                WORKBENCH_PREFS.get(PREF_DENSITY, Theme.Density.DENSE.id())));
        Theme.setMode(Theme.Mode.fromPreference(
                WORKBENCH_PREFS.get(PREF_THEME_MODE, Theme.Mode.DARK.id())));
        TensorViz.refreshPalette();
    }

    /**
     * Applies and persists the requested UI density, rescaling the live
     * component tree so the change is visible without a restart.
     *
     * @param next requested density
     */
    protected void setDensity(Theme.Density next) {
        Theme.Density resolved = next == null ? Theme.Density.DENSE : next;
        if (Theme.density() == resolved) {
            return;
        }
        double ratio = Theme.setDensity(resolved);
        WORKBENCH_PREFS.put(PREF_DENSITY, resolved.id());
        // Rescale the live tree (and any detached surfaces) from their current
        // fonts FIRST, then re-seed the UIManager defaults for later-created
        // components — so nothing can be scaled off an already-updated default.
        Theme.rescaleFonts(this, ratio);
        rescaleDetachedWindows(ratio);
        if (tabs != null) {
            tabs.rescaleDetachedTabs(ratio);
        }
        Theme.refreshFontDefaults();
        revalidate();
        repaint();
        if (settingsMenu != null) {
            settingsMenu.refreshTheme();
        }
        toast(Toast.Kind.INFO, resolved.label() + " density");
    }

    /**
     * Rescales fonts on surfaces that are not currently attached to this frame's
     * tree (so {@link Theme#rescaleFonts(Component, double)} on {@code this}
     * misses them): the PGN explorer is always a separate window, while the
     * settings dialog and command palette are overlay panels that are detached
     * from the layered pane while hidden. Surfaces currently attached are skipped
     * to avoid scaling them twice.
     *
     * @param ratio font-scale ratio from the previous density to the new one
     */
    private void rescaleDetachedWindows(double ratio) {
        rescaleIfDetached(settingsDialog, ratio);
        rescaleIfDetached(commandPalette, ratio);
        rescaleIfDetached(pgnExplorer, ratio);
    }

    /**
     * Rescales and relayouts one surface only when it is not part of this
     * frame's live component tree (attached surfaces are already covered).
     *
     * @param surface surface to rescale, may be {@code null}
     * @param ratio font-scale ratio from the previous density to the new one
     */
    private void rescaleIfDetached(Component surface, double ratio) {
        if (surface == null || SwingUtilities.isDescendingFrom(surface, this)) {
            return;
        }
        Theme.rescaleFonts(surface, ratio);
        surface.revalidate();
        surface.repaint();
    }

    /**
     * Loads persisted board and analysis display settings.
     */
    protected void loadDisplaySettings() {
        pieceSet = chess.images.assets.PieceSet.fromLabel(
                WORKBENCH_PREFS.get(PREF_PIECE_SET, chess.images.assets.PieceSet.SLATE.name()));
        showLegalMovePreview = WORKBENCH_PREFS.getBoolean(PREF_SHOW_LEGAL_MOVES, true);
        showLastMoveHighlight = WORKBENCH_PREFS.getBoolean(PREF_SHOW_LAST_MOVE, true);
        showBestMoveArrows = WORKBENCH_PREFS.getBoolean(PREF_SHOW_BEST_ARROWS, true);
        showBoardCoordinates = WORKBENCH_PREFS.getBoolean(PREF_SHOW_COORDINATES, true);
        boardAnimationsEnabled = WORKBENCH_PREFS.getBoolean(PREF_BOARD_ANIMATIONS, true);
        showUndefendedPieces = WORKBENCH_PREFS.getBoolean(PREF_SHOW_UNDEFENDED_PIECES, false);
        showPinnedPieces = WORKBENCH_PREFS.getBoolean(PREF_SHOW_PINNED_PIECES, false);
        showCheckableKing = WORKBENCH_PREFS.getBoolean(PREF_SHOW_CHECKABLE_KING, false);
        autoEvalBarEnabled = WORKBENCH_PREFS.getBoolean(PREF_AUTO_EVAL_BAR, true);
        liveExternalEngineEnabled = WORKBENCH_PREFS.getBoolean(PREF_LIVE_EXTERNAL_ENGINE, false);
    }

    /**
     * Loads persisted workbench layout settings.
     */
    protected void loadLayoutSettings() {
        statusBarVisible = WORKBENCH_PREFS.getBoolean(PREF_STATUS_BAR_VISIBLE, true);
    }

    /**
     * Saves board and analysis display settings.
     */
    protected void saveDisplaySettings() {
        WORKBENCH_PREFS.put(PREF_PIECE_SET, pieceSet.name());
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_LEGAL_MOVES, showLegalMovePreview);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_LAST_MOVE, showLastMoveHighlight);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_BEST_ARROWS, showBestMoveArrows);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_COORDINATES, showBoardCoordinates);
        WORKBENCH_PREFS.putBoolean(PREF_BOARD_ANIMATIONS, boardAnimationsEnabled);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_UNDEFENDED_PIECES, showUndefendedPieces);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_PINNED_PIECES, showPinnedPieces);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_CHECKABLE_KING, showCheckableKing);
        WORKBENCH_PREFS.putBoolean(PREF_AUTO_EVAL_BAR, autoEvalBarEnabled);
        WORKBENCH_PREFS.putBoolean(PREF_LIVE_EXTERNAL_ENGINE, liveExternalEngineEnabled);
    }

    /**
     * Applies and persists the requested theme mode.
     *
     * @param next requested theme mode
     */
    protected void setThemeMode(Theme.Mode next) {
        Theme.Mode resolved = next == null ? Theme.Mode.LIGHT : next;
        if (Theme.mode() == resolved) {
            if (settingsMenu != null) {
                settingsMenu.syncMode();
            }
            return;
        }
        Theme.setMode(resolved);
        TensorViz.refreshPalette();
        WORKBENCH_PREFS.put(PREF_THEME_MODE, Theme.mode().id());
        Theme.install();
        Theme.refreshComponentTree(this);
        if (tabs != null) {
            tabs.refreshTheme();
        }
        if (shellFrame != null) {
            shellFrame.refreshTheme();
        }
        if (settingsMenu != null) {
            settingsMenu.syncMode();
            settingsMenu.refreshTheme();
        }
        if (settingsDialog != null) {
            settingsDialog.refreshTheme();
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
     * Applies and persists a legacy light/dark toggle.
     *
     * @param dark true to use the neutral dark mode
     */
    protected void setDarkMode(boolean dark) {
        setThemeMode(dark ? Theme.Mode.DARK : Theme.Mode.LIGHT);
    }

    /**
     * Returns whether dark mode is active.
     *
     * @return true in dark mode
     */
    protected boolean isDarkMode() {
        return Theme.isDark();
    }

    /**
     * Loads persisted external-engine settings.
     */
    protected void loadEngineSettings() {
        engineProtocolField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_PROTOCOL, Config.getProtocolPath()));
        engineNodesField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_NODES, ""));
        engineHashField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_HASH, ""));
    }

    /**
     * Installs example placeholders on editable fields.
     */
    protected void installFieldPlaceholders() {
        placeholder(fenField, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        placeholder(analysisDurationField, "e.g. " + Defaults.ANALYSIS_DURATION + " or 500ms");
        placeholder(engineProtocolField, "path/to/engine.toml");
        placeholder(engineNodesField, "e.g. 1000000");
        placeholder(engineHashField, "e.g. 128");
    }

    /**
     * Installs listeners for engine-setting fields.
     */
    protected void installEngineSettingListeners() {
        engineProtocolField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
        engineNodesField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
        engineHashField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
    }

    /**
     * Saves external-engine settings.
     */
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

    /**
     * Persists changed engine settings and refreshes dependent previews.
     */
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
        board.setPieceSet(pieceSet);
        board.setShowLegalMovePreview(showLegalMovePreview);
        board.setShowLastMoveHighlight(showLastMoveHighlight);
        board.setShowSuggestedMoveArrow(showBestMoveArrows);
        board.setShowNotation(showBoardCoordinates);
        board.setAnimationsEnabled(boardAnimationsEnabled);
        board.setShowUndefendedPieces(showUndefendedPieces);
        board.setShowPinnedPieces(showPinnedPieces);
        board.setShowCheckableKing(showCheckableKing);
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

    /**
     * Synchronizes sound settings controls.
     */
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
        if (bellHistoryListener != null) {
            Toast.removeHistoryListener(bellHistoryListener);
            bellHistoryListener = null;
        }
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
        for (application.gui.workbench.mcts.MctsPanel panel : new java.util.ArrayList<>(mctsPanels)) {
            panel.dispose();
        }
        mctsPanels.clear();
        for (application.gui.workbench.mcts.TreePanel panel : new java.util.ArrayList<>(treePanels)) {
            panel.dispose();
        }
        treePanels.clear();
        mctsSession.close();
        if (playSession != null) {
            playSession.dispose();
        }
        super.dispose();
    }

    /**
     * Builds the complete native workbench UI.
     */
    protected void buildUi() {
        loadLayoutSettings();
        settingsMenu = WindowMenus.settingsMenu(this);
        SoundService.addSettingsListener(soundSettingsListener);
        layoutMenu = WindowMenus.layoutMenu(this);
        settingsMenu.component().add(Box.createHorizontalGlue());
        // VS Code-style title-bar search box. Click anywhere on the box to
        // open the command palette; the surrounding chrome owns app/project
        // identity and live run status.
        JComponent paletteHint = WindowTitleBarSearch.create(this::showCommandPalette);
        settingsMenu.component().add(paletteHint);
        settingsMenu.component().add(javax.swing.Box.createHorizontalStrut(10));
        settingsMenu.component().add(TitleBarChrome.runs(session, this::showConsoleDock));
        settingsMenu.component().add(javax.swing.Box.createHorizontalStrut(6));
        settingsMenu.component().add(Ui.ghostButton("Settings", event -> showDisplaySettings()));
        settingsMenu.component().add(javax.swing.Box.createHorizontalStrut(6));
        settingsMenu.component().add(layoutMenu.component());
        setJMenuBar(settingsMenu.component());

        JPanel root = new BackdropPanel();
        root.setLayout(new BorderLayout(0, 0));
        root.setBackground(Theme.BG);
        root.setBorder(Theme.pad(0, 0, 0, 0));

        tabs = new EditorSplitArea();
        // The top-level tab set is registry data, not a hand-wired sequence: the
        // shell registers every surface as a RegisteredView and the split area
        // adds them in order. This keeps the tab strip enumerable (for the "+"
        // menu / command palette) and is the registration backbone the workbench
        // UX redesign builds the view/inspector system on.
        ViewRegistry registry = new ViewRegistry()
                .add(new RegisteredView("Dashboard", dashboardPanel))
                // Board is the unified board surface: Analyze, Play, Solve,
                // Relations, and Draw are modes of one workspace (a switcher over a shared
                // board area), not four separate tabs. Built eagerly because its
                // Analyze mode wires the main board at startup. Duplicating the
                // Board tab spawns an independent analysis workspace.
                .add(new RegisteredView("Board", createBoardWorkspaceTab(), this::createDetachedAnalysisTab))
                // Run is the command-builder surface. Batch workflows are
                // templates inside the builder; Console and Logs are registered
                // as first-class output views below. Built eagerly because it
                // wires the command form at startup.
                // (Position-description UI stays unregistered until it is ready.)
                .add(new RegisteredView("Run", createRunWorkspaceTab()))
                .add(new RegisteredView("Datasets", new LazyPanel("Datasets", this::createDatasetTab),
                        () -> new LazyPanel("Datasets", this::createDetachedDatasetTab)))
                .add(new RegisteredView("Publish", new LazyPanel("Publish", this::createPublishTab),
                        () -> new LazyPanel("Publish", this::createDetachedPublishTab)))
                // Engine Lab is the unified engine surface: the neural-network
                // visualizer (Network) and the PUCT/MCTS search (Search) are
                // modes of one workspace, not two tabs. Built lazily; duplicating
                // spawns an independent network visualizer.
                .add(new RegisteredView("Engine Lab", new LazyPanel("Engine Lab", this::createEngineWorkspaceTab),
                        () -> new LazyPanel("Engine Lab", this::createDetachedNetworkTab)))
                // Console and Logs are now first-class surfaces (not Run modes),
                // so they can be split, docked side-by-side, resized, and
                // duplicated like the other top-level views. Console is eager so
                // command output has a live target before the first run.
                .add(new RegisteredView("Console", createConsolePanel(), this::createDetachedConsolePanel))
                .add(new RegisteredView("Logs", new LazyPanel("Logs", this::createLogTab),
                        () -> new LazyPanel("Logs", this::createDetachedLogTab)))
                .add(new RegisteredView("Studies", new LazyPanel("Studies", this::createStudiesWorkspaceTab),
                        () -> new LazyPanel("Studies", this::createDetachedStudiesWorkspaceTab)));
        tabs.addViews(registry);
        tabs.install();
        tabs.setSelectionListener(index -> onWorkbenchTabVisibilityChanged());
        tabs.select(TAB_BOARD);

        shellFrame = new ShellFrame(tabs, session, this::selectTab,
                this::showConsoleDock, this::showLogsDock, this::openLatestJobLogOrLogs,
                () -> selectTab(TAB_STUDIES));
        root.add(shellFrame, BorderLayout.CENTER);
        statusBar = createStatusBar();
        statusBar.setVisible(statusBarVisible);
        root.add(statusBar, BorderLayout.SOUTH);
        session.addListener(changed -> SwingUtilities.invokeLater(this::refreshWorkspaceHeaders));
        session.jobs().addListener(() -> SwingUtilities.invokeLater(this::refreshGlobalJobStatus));
        refreshGlobalJobStatus();
        setContentPane(root);
        installKeyBindings();
        installFenPgnDropTarget();
    }

    /**
     * Selects the Console or Logs workbench tab.
     *
     * @param tabTitle tab title ("Console" or "Logs")
     */
    @Override
    public void showInBottomDock(String tabTitle) {
        if (DOCK_LOGS.equals(tabTitle)) {
            showLogsDock();
        } else {
            showConsoleDock();
        }
    }

    /**
     * Focuses the Console surface.
     */
    protected void showConsoleDock() {
        selectTab(TAB_CONSOLE);
    }

    /**
     * Focuses the Logs surface, refreshing it when it already exists.
     */
    protected void showLogsDock() {
        boolean created = logPanel == null;
        LogPanel panel = primaryLogPanel();
        if (!created) {
            panel.refreshLogs();
        }
        selectTab(TAB_LOGS);
    }

    /**
     * Opens the Logs tab and the logs directory.
     */
    protected void openLogsDockAndDirectory() {
        showLogsDock();
        runArtifacts.openLogsDirectory();
    }

    /**
     * Opens the latest job log when one can exist, otherwise focuses Logs.
     */
    private void openLatestJobLogOrLogs() {
        Job job = session.jobs().latest();
        if (job != null && job.status().isTerminal()) {
            runArtifacts.openLog(job);
            refreshLogBrowsers();
        }
        showLogsDock();
    }

    /**
     * Refreshes the global job status footer from the latest command run.
     */
    protected void refreshGlobalJobStatus() {
        Job job = session.jobs().latest();
        if (job == null) {
            statusBarJob.notRun("idle");
            setJobDetail("no jobs");
            return;
        }
        JobStatus status = job.status();
        switch (status) {
            case QUEUED -> statusBarJob.notRun("queued");
            case RUNNING -> statusBarJob.running("running");
            case SUCCEEDED -> statusBarJob.complete("complete");
            case FAILED -> statusBarJob.error("error");
            case CANCELLED -> statusBarJob.paused("stopped");
            default -> statusBarJob.notRun(status.label().toLowerCase(java.util.Locale.ROOT));
        }
        setJobDetail(jobDetail(job));
    }

    /**
     * Refreshes shell headers that derive context from the shared session.
     */
    protected void refreshWorkspaceHeaders() {
        if (boardWorkspace != null) {
            boardWorkspace.refreshHeader();
        }
        if (engineWorkspace != null) {
            engineWorkspace.refreshHeader();
        }
    }

    /**
     * Returns the latest-job footer detail.
     *
     * @param job latest job
     * @return detail text
     */
    private static String jobDetail(Job job) {
        if (job == null) {
            return "no jobs";
        }
        String result = job.resultSummary();
        if (job.status().isTerminal() && result != null && !result.isBlank()) {
            return result;
        }
        String command = job.displayCommand();
        return command == null || command.isBlank() ? job.status().label() : command;
    }

    /**
     * Refreshes all live log browsers.
     */
    protected void refreshLogBrowsers() {
        for (LogPanel panel : logPanels) {
            panel.refreshLogs();
        }
    }

    /**
     * Installs FEN PGN drop target.
     */
    private void installFenPgnDropTarget() {
        new java.awt.dnd.DropTarget(getRootPane(), java.awt.dnd.DnDConstants.ACTION_COPY,
                new java.awt.dnd.DropTargetAdapter() {
                    /**
                     * {@inheritDoc}
                     */
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

    /**
     * Reads the read dropped FEN or PGN.
     *
     * @param t normalized interpolation value
     * @return read dropped FEN or PGN text
     * @throws java.io.IOException if external I/O or engine communication fails
     * @throws java.awt.datatransfer.UnsupportedFlavorException if clipboard data has an unsupported flavor
     */
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

    /**
     * Returns whether supported dropped game file.
     *
     * @param path file-system path
     * @return true when supported dropped game file
     */
    private static boolean isSupportedDroppedGameFile(java.nio.file.Path path) {
        if (path == null || !java.nio.file.Files.isRegularFile(path) || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".fen") || name.endsWith(".pgn") || name.endsWith(".txt");
    }

    /**
     * True when the status bar is visible.
     */
    protected boolean statusBarVisible = true;

    /**
     * Bottom status-bar component.
     */
    protected JComponent statusBar;

    /**
     * Reference-style shell around the editor tabs.
     */
    protected ShellFrame shellFrame;

    /**
     * Status-bar current-position cell.
     */
    protected final JLabel statusBarPosition = new JLabel("");

    /**
     * Status-bar current-ply cell.
     */
    protected final JLabel statusBarPly = new JLabel("");

    /**
     * Status-bar engine-state badge.
     */
    protected final application.gui.workbench.ui.StatusBadge statusBarEngine =
            new application.gui.workbench.ui.StatusBadge();

    /**
     * Status-bar engine-detail cell.
     */
    protected final JLabel statusBarEngineDetail = new JLabel("");

    /**
     * Status-bar latest-job badge.
     */
    protected final application.gui.workbench.ui.StatusBadge statusBarJob =
            new application.gui.workbench.ui.StatusBadge();

    /**
     * Status-bar latest-job detail cell.
     */
    protected final JLabel statusBarJobDetail = new JLabel("");

    /**
     * Builds a compact status bar pinned to the south edge of the workbench.
     *
     * @return status bar component
     */
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
        c.weightx = 0.22;
        bar.add(statusClickCell(engineCell, "Open engine settings",
                this::showEngineSettings), c);

        JPanel jobCell = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        statusBarJob.setFont(Theme.font(11, java.awt.Font.PLAIN));
        statusBarJob.notRun("idle");
        statusBarJob.setFixedTextWidth(
                statusBarJob.getFontMetrics(statusBarJob.getFont()).stringWidth("complete"));
        jobCell.add(statusBarJob, BorderLayout.WEST);
        jobCell.add(statusBarJobDetail, BorderLayout.CENTER);
        c.gridx = 3;
        c.weightx = 0.28;
        bar.add(statusClickCell(jobCell, "Open latest run log",
                this::openLatestJobLogOrLogs), c);

        for (JLabel label : new JLabel[] { statusBarPosition, statusBarPly,
                statusBarEngineDetail, statusBarJobDetail }) {
            label.setFont(Theme.font(11, java.awt.Font.PLAIN));
            Theme.foreground(label, Theme.ForegroundRole.MUTED);
        }
        statusBarEngineDetail.setFont(Theme.mono(11));
        statusBarJobDetail.setFont(Theme.mono(11));
        statusBarPosition.setHorizontalAlignment(SwingConstants.LEFT);
        statusBarPly.setHorizontalAlignment(SwingConstants.CENTER);
        statusBarEngineDetail.setHorizontalAlignment(SwingConstants.RIGHT);
        statusBarJobDetail.setHorizontalAlignment(SwingConstants.RIGHT);

        // Bell at the far right opens a popover with the recent toast
        // history so users can recover paths/messages they missed.
        c.gridx = 4;
        c.weightx = 0.0;
        c.fill = java.awt.GridBagConstraints.NONE;
        bar.add(buildNotificationBell(), c);
        return bar;
    }

    /**
     * Builds the build notification bell.
     *
     * @return build notification bell
     */
    private JComponent buildNotificationBell() {
        final boolean[] hovered = { false };
        final boolean[] unread = { false };
        JComponent bell = new JComponent() {
            /**
             * Serialization identifier for Swing compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * {@inheritDoc}
             */
            @Override
            public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(26, 22);
            }

            /**
             * {@inheritDoc}
             */
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
            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                hovered[0] = true;
                bell.repaint();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                hovered[0] = false;
                bell.repaint();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                unread[0] = false;
                bell.repaint();
                showNotificationHistory(bell);
            }
        });
        bellHistoryListener = () -> SwingUtilities.invokeLater(() -> {
            unread[0] = true;
            bell.repaint();
        });
        Toast.addHistoryListener(bellHistoryListener);
        return bell;
    }

    /**
     * Shows notification history.
     *
     * @param anchor text anchor
     */
    private void showNotificationHistory(JComponent anchor) {
        java.util.List<Toast.Entry> entries = Toast.history();
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
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
        Ui.stylePopupMenu(popup);
        int width = Math.max(360, anchor.getWidth());
        popup.setPreferredSize(new java.awt.Dimension(width, Math.min(360, 24 + entries.size() * 26 + 16)));
        popup.show(anchor, -width + anchor.getWidth(), -popup.getPreferredSize().height);
    }

    /**
     * Returns the status click cell.
     *
     * @param content source content
     * @param tooltip tooltip text
     * @param onClick source on click
     * @return status click cell
     */
    private JPanel statusClickCell(JComponent content, String tooltip, Runnable onClick) {
        final boolean[] hovered = { false };
        JPanel cell = new JPanel(new BorderLayout()) {
            /**
             * Serialization identifier for Swing compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * {@inheritDoc}
             */
            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                if (hovered[0]) {
                    java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                    try {
                        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(Theme.TAB_HOVER);
                        // Soft rounded hover pill so the cell reads as a clickable
                        // affordance rather than a hard-edged band.
                        g.fillRoundRect(0, 1, getWidth(), Math.max(1, getHeight() - 2),
                                Theme.RADIUS, Theme.RADIUS);
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
            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                hovered[0] = true;
                cell.repaint();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                java.awt.Component source = (java.awt.Component) event.getSource();
                java.awt.Point point = SwingUtilities.convertPoint(source, event.getPoint(), cell);
                if (!cell.contains(point)) {
                    hovered[0] = false;
                    cell.repaint();
                }
            }

            /**
             * {@inheritDoc}
             */
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

    /**
     * Installs status cell interaction.
     *
     * @param component Swing component
     * @param tooltip tooltip text
     * @param listener event listener
     */
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

    /**
     * Copies status bar FEN.
     */
    private void copyStatusBarFen() {
        if (currentPosition == null) {
            return;
        }
        copyText(currentPosition.toString());
    }

    /**
     * Toggles status bar ply jump.
     */
    private void toggleStatusBarPlyJump() {
        if (isBoardMode(BOARD_SOLVE)) {
            if (puzzlePanel != null) {
                puzzlePanel.toggleReviewStartEnd();
            }
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

    /**
     * Refreshes status-bar text from the current game state.
     */
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

    /**
     * Returns the position status label.
     *
     * @return position status label text
     */
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
            setEngineDetail("");
            return;
        }
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("live paused")) {
            statusBarEngine.idle("paused");
            setEngineDetail(message.substring("live paused".length()).trim());
            return;
        }
        if (lower.startsWith("live failed")) {
            statusBarEngine.error("error");
            setEngineDetail(message.substring("live failed".length()).trim());
            return;
        }
        if (lower.startsWith("live starting")) {
            statusBarEngine.busy("starting");
            setEngineDetail(message.substring("live starting".length()).trim());
            return;
        }
        if (lower.startsWith("live config")) {
            statusBarEngine.busy("config");
            setEngineDetail(message.substring("live config".length()).trim());
            return;
        }
        if (lower.startsWith("live thinking")) {
            statusBarEngine.busy("live");
            setEngineDetail(message.substring("live thinking".length()).trim());
            return;
        }
        if (lower.startsWith("live")) {
            statusBarEngine.busy("live");
            setEngineDetail(message.substring("live".length()).trim());
            return;
        }
        // Unknown free-form text — keep the badge fixed as "idle" and route the
        // payload to the detail line so it still surfaces.
        statusBarEngine.idle("idle");
        setEngineDetail(message);
    }

    /**
     * Maximum characters shown in the status-bar engine-detail cell before it
     * is truncated, so a long engine message cannot blow out the status bar.
     */
    private static final int ENGINE_DETAIL_MAX_CHARS = 48;

    /**
     * Maximum characters shown in the status-bar latest-job detail cell.
     */
    private static final int JOB_DETAIL_MAX_CHARS = 56;

    /**
     * Sets the status-bar engine-detail text, truncating to a width-safe budget
     * (full text remains available on hover) so long engine/error messages do
     * not overflow the status bar.
     *
     * @param text engine detail text
     */
    private void setEngineDetail(String text) {
        String value = text == null ? "" : text;
        if (value.length() > ENGINE_DETAIL_MAX_CHARS) {
            statusBarEngineDetail.setText(value.substring(0, ENGINE_DETAIL_MAX_CHARS - 1).trim() + "…");
            statusBarEngineDetail.setToolTipText(value);
        } else {
            statusBarEngineDetail.setText(value);
            statusBarEngineDetail.setToolTipText(value.isBlank() ? null : value);
        }
    }

    /**
     * Sets the status-bar latest-job detail text, truncating to a width-safe
     * budget while keeping the full detail available on hover.
     *
     * @param text latest-job detail
     */
    private void setJobDetail(String text) {
        String value = text == null ? "" : text;
        if (value.length() > JOB_DETAIL_MAX_CHARS) {
            statusBarJobDetail.setText(value.substring(0, JOB_DETAIL_MAX_CHARS - 1).trim() + "…");
            statusBarJobDetail.setToolTipText(value);
        } else {
            statusBarJobDetail.setText(value);
            statusBarJobDetail.setToolTipText(value.isBlank() ? null : value);
        }
    }

    /**
     * Shows a non-blocking workbench toast.
     *
     * @param kind toast severity
     * @param message message text
     */
    protected void toast(Toast.Kind kind, String message) {
        Toast.show(this, kind, message);
    }

    /**
     * Installs global workbench key bindings.
     */
    protected void installKeyBindings() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control K"), "openCommandPalette");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("meta K"), "openCommandPalette");
        getRootPane().getActionMap().put("openCommandPalette", new AbstractAction() {
            /**
             * Serialization identifier for Swing action compatibility.
             */
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
        // Ctrl+` focuses the movable Console tab.
        bindWindowAction("control BACK_QUOTE", "showConsoleTab", event -> showConsoleDock());
        bindWindowAction("meta BACK_QUOTE", "showConsoleTabMeta", event -> showConsoleDock());
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
        if (isBoardMode(BOARD_SOLVE)) {
            if (puzzlePanel != null) {
                puzzlePanel.navigateReview(delta);
            }
            return;
        }
        navigateGame(delta);
    }

    /**
     * Jumps the active position surface to its start.
     */
    protected void jumpActivePositionToStart() {
        if (isBoardMode(BOARD_SOLVE)) {
            if (puzzlePanel != null) {
                puzzlePanel.jumpReviewToStart();
            }
            return;
        }
        jumpGameTo(0);
    }

    /**
     * Jumps the active position surface to its end.
     */
    protected void jumpActivePositionToEnd() {
        if (isBoardMode(BOARD_SOLVE)) {
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

    /**
     * Returns the copy keys.
     *
     * @param source source object
     * @param target target object
     * @param offset start offset
     * @return copy keys
     */
    private static int copyKeys(KeyStroke[] source, KeyStroke[] target, int offset) {
        System.arraycopy(source, 0, target, offset, source.length);
        return offset + source.length;
    }

    /**
     * Returns the key strokes.
     *
     * @param keyCodes source key codes
     * @return key strokes
     */
    private static KeyStroke[] keyStrokes(int... keyCodes) {
        KeyStroke[] keys = new KeyStroke[keyCodes.length];
        for (int i = 0; i < keyCodes.length; i++) {
            keys[i] = KeyStroke.getKeyStroke(keyCodes[i], 0);
        }
        return keys;
    }

    /**
     * Installs Ctrl+number shortcuts for visible workbench tabs.
     */
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
            /**
             * Serialization identifier for Swing action compatibility.
             */
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
     * Opens the unified Board surface and selects one of its modes.
     *
     * @param mode board mode (see {@code BOARD_*} constants)
     */
    protected void openBoard(int mode) {
        selectTab(TAB_BOARD);
        if (boardWorkspace != null) {
            boardWorkspace.setMode(mode);
        }
    }

    /**
     * Returns whether the unified Board surface is the active tab.
     *
     * @return true when the Board tab is selected
     */
    protected boolean isBoardTab() {
        return tabs != null && tabs.selectedIndex() == TAB_BOARD;
    }

    /**
     * Returns whether the Board surface is active and showing a given mode.
     *
     * @param mode board mode (see {@code BOARD_*} constants)
     * @return true when the Board tab is selected and showing {@code mode}
     */
    protected boolean isBoardMode(int mode) {
        return isBoardTab() && boardWorkspace != null && boardWorkspace.mode() == mode;
    }

    /**
     * Opens the unified Engine surface and selects one of its modes.
     *
     * @param mode engine mode ({@link #ENGINE_NETWORK} or {@link #ENGINE_SEARCH})
     */
    protected void openEngine(int mode) {
        selectTab(TAB_ENGINE);
        if (engineWorkspace != null) {
            engineWorkspace.setMode(mode);
        }
    }

    /**
     * Opens Engine / Search and selects its Graph subview. This preserves legacy
     * tree entry points after Table and Graph became local Search views.
     */
    protected void openEngineGraph() {
        openEngine(ENGINE_SEARCH);
        if (mctsWorkspacePanel != null) {
            mctsWorkspacePanel.setViewMode(MctsWorkspacePanel.VIEW_GRAPH);
        }
    }

    /**
     * Opens the unified Run surface and selects one of its modes.
     *
     * @param mode run mode (see {@code RUN_*} constants)
     */
    @Override
    protected void openRun(int mode) {
        selectTab(TAB_RUN);
        if (runWorkspace != null) {
            runWorkspace.setMode(mode);
        }
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
        // Position navigation routes only for the analysis-style board modes
        // (Analyze drives the game line, Solve drives the puzzle review); Play
        // and Relations do not consume the navigation shortcuts.
        if (!isBoardMode(BOARD_ANALYZE) && !isBoardMode(BOARD_SOLVE)) {
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
            /**
             * Serialization identifier for Swing action compatibility.
             */
            protected static final long serialVersionUID = 1L;

            /**
             * Runs the bound window action.
             *
             * @param event action event
             */
            @Override
            public void actionPerformed(ActionEvent event) {
                body.accept(event);
            }
        });
    }

    /**
     * Opens the command palette.
     */
    protected void showCommandPalette() {
        if (commandPalette == null) {
            commandPalette = new CommandPalette(this);
        }
        commandPalette.showActions(commandPaletteActions());
    }

    /**
     * Opens the PGN explorer.
     */
    protected void showPgnExplorer() {
        if (pgnExplorer == null) {
            pgnExplorer = new PgnExplorerDialog(this, this::loadGameText, this::currentFen, this::copyText);
        }
        pgnExplorer.showExplorer(gameModel.pgn());
    }

    /**
     * Builds the current command-palette action list.
     *
     * @return palette actions
     */
    protected List<PaletteAction> commandPaletteActions() {
        return WindowPaletteActions.actions(this);
    }

    /**
     * Lazily-created unified settings dialog.
     */
    private SettingsDialog settingsDialog;

    /**
     * Shared modal overlay for settings and inspectors.
     */
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
                /**
                 * {@inheritDoc}
                 */
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

    /**
     * Opens the display settings section.
     */
    protected void showDisplaySettings() {
        SettingsDialog dialog = settingsDialog();
        dialog.selectSection("Display");
        dialog.showCentered();
    }

    /**
     * Opens the engine settings section.
     */
    protected void showEngineSettings() {
        SettingsDialog dialog = settingsDialog();
        dialog.selectSection("Engine");
        dialog.showCentered();
        SwingUtilities.invokeLater(engineProtocolField::requestFocusInWindow);
    }

    /**
     * Opens the analysis data view.
     */
    protected void showAnalysisData() {
        showBoardDetail("Raw");
    }

    /**
     * Opens the Analyze board surface and selects one side-rail detail tab by
     * title. This is used by feature shortcuts and the command palette so
     * buried tools like Review, Study, ECO, and Endgame have direct entry
     * points.
     *
     * @param title detail tab title
     */
    protected void showBoardDetail(String title) {
        openBoard(BOARD_ANALYZE);
        showAnalyzeCard(ANALYZE_CARD_BOARD);
        selectBoardDetailTab(title);
    }

    /**
     * Shows analyze card.
     *
     * @param card source card
     */
    private void showAnalyzeCard(String card) {
        if (analysisCards == null || !(analysisCards.getLayout() instanceof CardLayout layout)) {
            return;
        }
        layout.show(analysisCards, card);
    }

    /**
     * Selects board detail tab.
     *
     * @param title display title
     */
    private void selectBoardDetailTab(String title) {
        if (boardDetailTabs == null || title == null) {
            return;
        }
        String target = "Data".equals(title) ? "Raw" : title;
        for (int i = 0; i < boardDetailTabs.getTabCount(); i++) {
            if (target.equals(boardDetailTabs.getTitleAt(i))) {
                boardDetailTabs.setSelectedIndex(i);
                return;
            }
        }
    }

    /**
     * Copies analysis samples as CSV text.
     */
    protected void copyAnalysisCsv() {
        copyText(analysisGraph.csvText());
    }

    /**
     * Copies the formatted analysis report.
     */
    protected void copyAnalysisReport() {
        copyText(analysisGraph.reportText());
    }

    /**
     * Sends the analysis report to the platform print service.
     */
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

    /**
     * Clears collected analysis graph samples.
     */
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

    /**
     * Focuses the main FEN input field.
     */
    protected void focusFenField() {
        openBoard(BOARD_ANALYZE);
        SwingUtilities.invokeLater(fenField::requestFocusInWindow);
    }

    /**
     * Focuses the command option filter.
     */
    protected void focusOptionFilter() {
        openRun(RUN_BUILD);
        SwingUtilities.invokeLater(() -> {
            commandForm.expandOptionalFlags();
            commandForm.filterField().requestFocusInWindow();
        });
    }

    /**
     * Focuses the game text input.
     */
    protected void focusGameInput() {
        openBoard(BOARD_ANALYZE);
        showAnalyzeCard(ANALYZE_CARD_GAME);
        SwingUtilities.invokeLater(gameInput::requestFocusInWindow);
    }

    /**
     * Opens another independent Analyze tab.
     */
    protected void openNewAnalyzeTab() {
        if (tabs != null) {
            tabs.duplicate(TAB_BOARD);
        }
    }

    /**
     * Selects a workbench tab by index.
     *
     * @param index tab index
     */
    protected void selectTab(int index) {
        if (tabs != null && index >= 0 && index < tabs.count()) {
            tabs.select(index);
        }
    }

    /**
     * Refreshes dependent surfaces after tab visibility changes.
     */
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
        if (layoutMenu != null) {
            layoutMenu.refreshTheme();
        }
        if (shellFrame != null) {
            shellFrame.refreshSelection();
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
        return tabs != null && tabs.isVisibleInPane(TAB_BOARD);
    }

}
