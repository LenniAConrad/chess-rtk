/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.window;

import application.Config;
import application.gui.workbench.Defaults;
import application.gui.workbench.command.CommandPalette.PaletteAction;
import application.gui.workbench.command.CommandPalette;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.BackdropPanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
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
import static application.gui.workbench.ui.Ui.withTooltip;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */
@SuppressWarnings("java:S6539")

public abstract class WindowLifecycle extends WindowBase {
    /** Serialization identifier for Swing frame compatibility. */
    private static final long serialVersionUID = 1L;

    /**
     * Top-level application settings menu, including the theme selector.
     */
    protected transient SettingsMenu settingsMenu;

    /**
     * Top-level layout controls in the menu-bar chrome.
     */
    protected transient LayoutMenu layoutMenu;

    /**
     * Preferences node used to persist workbench UI state.
     */
    protected static final Preferences WORKBENCH_PREFS =
            Preferences.userNodeForPackage(Window.class);

    /**
     * Preference key for the workbench appearance mode.
     */
    protected static final String PREF_THEME_MODE = "appearance.themeMode";

    /**
     * Preference key for status-bar visibility.
     */
    protected static final String PREF_STATUS_BAR_VISIBLE = "layout.statusBarVisible";

    /**
     * Preference key for legal-move preview.
     */
    protected static final String PREF_SHOW_LEGAL_MOVES = "display.showLegalMovePreview";

    /**
     * Preference key for last-move highlighting.
     */
    protected static final String PREF_SHOW_LAST_MOVE = "display.showLastMoveHighlight";

    /**
     * Preference key for best-move arrows.
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
     * Preference key for the automatic eval bar.
     */
    protected static final String PREF_AUTO_EVAL_BAR = "display.autoEvalBar";

    /**
     * Preference key for live external-engine analysis.
     */
    protected static final String PREF_LIVE_EXTERNAL_ENGINE = "engine.liveExternal";

    /**
     * Preference key for the engine protocol path.
     */
    protected static final String PREF_ENGINE_PROTOCOL = "engine.protocolPath";

    /**
     * Preference key for external-engine node limit.
     */
    protected static final String PREF_ENGINE_NODES = "engine.maxNodes";

    /**
     * Preference key for external-engine hash size.
     */
    protected static final String PREF_ENGINE_HASH = "engine.hash";

    /**
     * Default window width when no preference is recorded.
     */
    protected static final int DEFAULT_WINDOW_WIDTH = 1440;

    /**
     * Default window height when no preference is recorded.
     */
    protected static final int DEFAULT_WINDOW_HEIGHT = 920;

    /**
     * Keyboard shortcuts that move to the previous game ply.
     */
    private static final KeyStroke[] PREVIOUS_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_LEFT,
            KeyEvent.VK_KP_LEFT,
            KeyEvent.VK_NUMPAD4);

    /**
     * Keyboard shortcuts that move to the next game ply.
     */
    private static final KeyStroke[] NEXT_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_RIGHT,
            KeyEvent.VK_KP_RIGHT,
            KeyEvent.VK_NUMPAD6);

    /**
     * Keyboard shortcuts that jump to the first game ply.
     */
    private static final KeyStroke[] FIRST_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_UP,
            KeyEvent.VK_KP_UP,
            KeyEvent.VK_NUMPAD8,
            KeyEvent.VK_HOME);

    /**
     * Keyboard shortcuts that jump to the final game ply.
     */
    private static final KeyStroke[] LAST_POSITION_KEYS = keyStrokes(
            KeyEvent.VK_DOWN,
            KeyEvent.VK_KP_DOWN,
            KeyEvent.VK_NUMPAD2,
            KeyEvent.VK_END);

    /**
     * Restores window size and position from {@link #WORKBENCH_PREFS}, with a
     * fallback to the historical defaults when no preference exists or the
     * stored bounds are off-screen.
     */
    protected void restoreWindowGeometry() {
        int width = WORKBENCH_PREFS.getInt("window.width", DEFAULT_WINDOW_WIDTH);
        int height = WORKBENCH_PREFS.getInt("window.height", DEFAULT_WINDOW_HEIGHT);
        int x = WORKBENCH_PREFS.getInt("window.x", Integer.MIN_VALUE);
        int y = WORKBENCH_PREFS.getInt("window.y", Integer.MIN_VALUE);
        boolean maximized = WORKBENCH_PREFS.getBoolean("window.maximized", false);
        setSize(Math.max(1180, width), Math.max(760, height));
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
     * Returns whether the supplied bounds intersect any visible screen.
     *
     * @param x window x
     * @param y window y
     * @param width window width
     * @param height window height
     * @return true when at least one virtual screen contains the rectangle
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
     * Persists current window size and position so the next launch restores
     * them.
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
     * Loads the persisted appearance mode.
     */
    protected void loadThemeSetting() {
        Theme.setMode(Theme.Mode.fromPreference(
                WORKBENCH_PREFS.get(PREF_THEME_MODE, Theme.Mode.LIGHT.id())));
        TensorViz.refreshPalette();
    }

    /**
     * Loads persisted display settings.
     */
    protected void loadDisplaySettings() {
        showLegalMovePreview = WORKBENCH_PREFS.getBoolean(PREF_SHOW_LEGAL_MOVES, true);
        showLastMoveHighlight = WORKBENCH_PREFS.getBoolean(PREF_SHOW_LAST_MOVE, true);
        showBestMoveArrows = WORKBENCH_PREFS.getBoolean(PREF_SHOW_BEST_ARROWS, true);
        showBoardCoordinates = WORKBENCH_PREFS.getBoolean(PREF_SHOW_COORDINATES, true);
        boardAnimationsEnabled = WORKBENCH_PREFS.getBoolean(PREF_BOARD_ANIMATIONS, true);
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
     * Persists display settings.
     */
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
     * Applies and persists a new appearance mode.
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
        if (settingsMenu != null) {
            settingsMenu.syncMode();
            settingsMenu.refreshTheme();
        }
        if (layoutMenu != null) {
            layoutMenu.refreshTheme();
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

    /**
     * Loads persisted external-engine settings.
     */
    protected void loadEngineSettings() {
        engineProtocolField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_PROTOCOL, Config.getProtocolPath()));
        engineNodesField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_NODES, ""));
        engineHashField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_HASH, ""));
    }

    /**
     * Adds empty-field examples for the workbench's editable text inputs.
     */
    protected void installFieldPlaceholders() {
        placeholder(fenField, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        placeholder(analysisDurationField, "e.g. " + Defaults.ANALYSIS_DURATION + " or 500ms");
        placeholder(engineProtocolField, "path/to/engine.toml");
        placeholder(engineNodesField, "e.g. 1000000");
        placeholder(engineHashField, "e.g. 128");
    }

    /**
     * Installs change listeners for external-engine fields.
     */
    protected void installEngineSettingListeners() {
        engineProtocolField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
        engineNodesField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
        engineHashField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
    }

    /**
     * Persists external-engine settings.
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
     * Handles a changed external-engine setting.
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
     * Applies current display settings to the board and auxiliary analysis UI.
     *
     * @param refreshEval true to restart eval behavior immediately
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

    /**
     * Cancels background work and persists window geometry before disposing.
     */
    @Override
    public void dispose() {
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
        networkPanel.dispose();
        mctsPanel.dispose();
        super.dispose();
    }

    /**
     * Builds the UI.
     */
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
                runArtifacts.openLogsDirectory();
            }
        });
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
        tabs.addPanel("Datasets", createDatasetTab());
        tabs.addPanel("Publish", createPublishTab());
        tabs.addPanel("Console", createConsolePanel());
        tabs.addPanel("Network", networkPanel);
        tabs.install();
        tabs.setSelectionListener(index -> onWorkbenchTabVisibilityChanged());
        tabs.select(TAB_DASHBOARD);

        root.add(tabs, BorderLayout.CENTER);
        statusBar = createStatusBar();
        statusBar.setVisible(statusBarVisible);
        root.add(statusBar, BorderLayout.SOUTH);
        setContentPane(root);
        installKeyBindings();
    }

    /**
     * Whether the compact status bar is visible.
     */
    protected boolean statusBarVisible = true;

    /**
     * Status-bar component installed at the bottom of the workbench.
     */
    protected JComponent statusBar;

    /**
     * Status-bar position label (FEN summary).
     */
    protected final JLabel statusBarPosition = new JLabel("");

    /**
     * Status-bar move-counter label.
     */
    protected final JLabel statusBarPly = new JLabel("");

    /**
     * Status-bar engine-state label.
     */
    protected final JLabel statusBarEngine = new JLabel("idle");

    /**
     * Builds a compact status bar pinned to the south edge of the workbench.
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

        c.gridx = 0;
        c.weightx = 0.5;
        bar.add(statusBarPosition, c);

        c.gridx = 1;
        c.weightx = 0.3;
        bar.add(statusBarPly, c);

        c.gridx = 2;
        c.weightx = 0.2;
        bar.add(statusBarEngine, c);

        for (JLabel label : new JLabel[] { statusBarPosition, statusBarPly, statusBarEngine }) {
            label.setFont(Theme.font(11, java.awt.Font.PLAIN));
            Theme.foreground(label, Theme.ForegroundRole.MUTED);
        }
        statusBarPosition.setHorizontalAlignment(SwingConstants.LEFT);
        statusBarPly.setHorizontalAlignment(SwingConstants.CENTER);
        statusBarEngine.setHorizontalAlignment(SwingConstants.RIGHT);
        return bar;
    }

    /**
     * Returns whether the compact status bar is visible.
     *
     * @return true when visible
     */
    protected boolean isStatusBarVisible() {
        return statusBarVisible;
    }

    /**
     * Shows or hides the compact status bar and persists the layout setting.
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
     * Truncation budget for the inline FEN preview in the status bar; the
     * full FEN is exposed via the label tooltip.
     */
    protected static final int STATUS_FEN_BUDGET = 60;

    /**
     * Updates the status bar from the current model state.
     */
    protected void refreshStatusBar() {
        if (currentPosition == null) {
            statusBarPosition.setText("");
            statusBarPosition.setToolTipText(null);
            statusBarPly.setText("");
            return;
        }
        String fen = currentPosition.toString();
        statusBarPosition.setText(fen.length() > STATUS_FEN_BUDGET
                ? fen.substring(0, STATUS_FEN_BUDGET - 3) + "…"
                : fen);
        statusBarPosition.setToolTipText(fen);
        int ply = gameModel.currentPly();
        int last = gameModel.lastPly();
        statusBarPly.setText("ply " + ply + " / " + last + "  ·  "
                + (currentPosition.isWhiteToMove() ? "white to move" : "black to move"));
    }

    /**
     * Updates the engine status label.
     *
     * @param text status text
     */
    protected void setStatusBarEngine(String text) {
        statusBarEngine.setText(text == null ? "" : text);
    }

    /**
     * Shows a transient toast notification.
     *
     * @param kind severity
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
        bindWindowAction("control COMMA", "openDisplaySettings", event -> showDisplaySettings());
        bindWindowAction("meta COMMA", "openDisplaySettingsMeta", event -> showDisplaySettings());
        bindWindowAction("alt LEFT", "navigateBack", event -> navigateGame(-1));
        bindWindowAction("alt RIGHT", "navigateForward", event -> navigateGame(1));
        bindWindowAction("alt UP", "navigateStart", event -> jumpGameTo(0));
        bindWindowAction("alt DOWN", "navigateEnd", event -> jumpGameTo(gameModel.lastPly()));
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
        bindPositionNavigation(PREVIOUS_POSITION_KEYS, "previousPosition", event -> navigateGame(-1));
        bindPositionNavigation(NEXT_POSITION_KEYS, "nextPosition", event -> navigateGame(1));
        bindPositionNavigation(FIRST_POSITION_KEYS, "firstPosition", event -> jumpGameTo(0));
        bindPositionNavigation(LAST_POSITION_KEYS, "lastPosition", event -> jumpGameTo(gameModel.lastPly()));
    }

    /**
     * Returns all unmodified key strokes that navigate the game position.
     *
     * @return copied key-stroke array
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
     * Copies key strokes into an output array.
     *
     * @param source source key strokes
     * @param target target array
     * @param offset first target offset
     * @return next free offset
     */
    private static int copyKeys(KeyStroke[] source, KeyStroke[] target, int offset) {
        System.arraycopy(source, 0, target, offset, source.length);
        return offset + source.length;
    }

    /**
     * Creates unmodified key strokes for physical keyboard key codes.
     *
     * @param keyCodes key codes
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
     * Installs direct workbench-tab number shortcuts.
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
     * Binds a window-level arrow key to game-position navigation when the board
     * view is active and focus is not in a component that owns arrow keys.
     *
     * @param keyStrokes key strokes
     * @param name action name
     * @param body action body
     */
    protected void bindPositionNavigation(KeyStroke[] keyStrokes, String name,
            Consumer<ActionEvent> body) {
        for (int i = 0; i < keyStrokes.length; i++) {
            bindPositionNavigation(keyStrokes[i], name + i, body);
        }
    }

    /**
     * Binds one window-level key stroke to game-position navigation.
     *
     * @param keyStroke key stroke
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
     * Returns whether an unmodified arrow key should navigate game positions.
     *
     * @return true when routing to position navigation is appropriate
     */
    protected boolean shouldRoutePositionNavigation() {
        if (tabs == null || tabs.selectedIndex() != TAB_ANALYZE) {
            return false;
        }
        if (analysisTabs != null && analysisTabs.getSelectedIndex() != 0) {
            return false;
        }
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return shouldRoutePositionNavigation(focusOwner);
    }

    /**
     * Returns whether arrow-key focus should be routed to position navigation.
     *
     * @param focusOwner current focus owner
     * @return true when arrow keys should move through game positions
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
     * Binds a window-level keyboard action.
     *
     * @param keyStroke keystroke
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

    /**
     * Opens the searchable action palette.
     */
    protected void showCommandPalette() {
        if (commandPalette == null) {
            commandPalette = new CommandPalette(this);
        }
        commandPalette.showActions(commandPaletteActions());
    }

    /**
     * Builds the current command-palette action list.
     *
     * @return palette actions
     */
    protected List<PaletteAction> commandPaletteActions() {
        return List.of(
    new PaletteAction("Analyze board", "Run built-in search on the current FEN", this::runBuiltInSearch),
    new PaletteAction("Best move", "Run engine bestmove with the current duration", this::runBestMove),
    new PaletteAction("Engine analysis", "Run multipv analysis for the current position", this::runAnalyze),
    new PaletteAction("Live external engine", "Toggle continuous UCI analysis for the board",
                        () -> setLiveExternalEngineEnabled(!liveExternalEngineEnabled)),
    new PaletteAction("Analysis data", "Show live evaluation, depth, and speed graphs",
                        this::showAnalysisData),
    new PaletteAction("Copy analysis CSV", "Copy live analysis graph data as CSV",
                        this::copyAnalysisCsv),
    new PaletteAction("Copy analysis report", "Copy the live analysis summary report",
                        this::copyAnalysisReport),
    new PaletteAction("Print analysis report", "Print the live analysis graph report",
                        this::printAnalysisReport),
    new PaletteAction("Position tags", "Generate tags for the current FEN", this::runTagsCommand),
    new PaletteAction("Perft", "Run perft with the selected depth and threads", this::runPerft),
    new PaletteAction("Run built command", "Execute the selected command template", this::runSelectedTemplate),
    new PaletteAction("Copy built command", "Copy the command-builder preview", this::copyBuiltCommand),
    new PaletteAction("Reset built command", "Restore default command-builder options", this::resetSelectedTemplate),
    new PaletteAction("Filter command flags", "Focus the command-builder option filter", this::focusOptionFilter),
    new PaletteAction("Run batch", "Execute the selected batch workflow", batchPanel::runBatch),
    new PaletteAction("Run publishing", "Execute the selected publishing workflow", this::runPublishingCommand),
    new PaletteAction("Generate report", "Refresh the report maker output", this::generateReport),
    new PaletteAction("Copy report", "Copy the current report output", this::copyReport),
    new PaletteAction("Copy FEN", "Copy the current board FEN", () -> copyText(fenField.getText())),
    new PaletteAction("Toggle dark mode", "Switch the workbench appearance palette",
                        () -> setDarkMode(!Theme.isDark())),
    new PaletteAction("Board settings", "Adjust board highlights, arrows, notation, animations, and eval",
                        this::showDisplaySettings),
    new PaletteAction("Engine settings", "Adjust external engine protocol, nodes, and hash",
                        this::showEngineSettings),
    new PaletteAction("Engine smoke test", "Validate that the configured UCI engine starts",
                        this::runEngineSmoke),
    new PaletteAction("Validate config", "Run config validation", this::runConfigValidate),
    new PaletteAction("Focus FEN", "Move focus to the position field", this::focusFenField),
    new PaletteAction("Stop command", "Cancel the running child process", this::stopCommand),
    new PaletteAction("Open analyze tab", "Show board analysis tools", () -> selectTab(TAB_ANALYZE)),
    new PaletteAction("New analyze tab", "Open another independent analysis workspace",
                        this::openNewAnalyzeTab),
    new PaletteAction("Focus game line", "Show the merged game tools", this::focusGameInput),
    new PaletteAction("Open commands tab", "Show command controller", () -> selectTab(TAB_COMMANDS)),
    new PaletteAction("Open batch tab", "Show batch workflows", () -> selectTab(TAB_BATCH)),
    new PaletteAction("Open datasets tab", "Inspect and analyze training datasets", () -> selectTab(TAB_DATASETS)),
    new PaletteAction("Analyze dataset", "Scan the selected dataset source", datasetPanel::analyzeCurrentSource),
    new PaletteAction("Open publish tab", "Show report and publishing tools", () -> selectTab(TAB_PUBLISH)),
    new PaletteAction("Open console tab", "Show command output and process state",
                        () -> selectTab(TAB_CONSOLE)),
    new PaletteAction("Split tab right", "Move the active workbench tab into a right editor group",
                        tabs::splitSelectedTabRight),
    new PaletteAction("Split tab down", "Move the active workbench tab into a lower editor group",
                        tabs::splitSelectedTabDown),
    new PaletteAction("Close active tab", "Hide the active workbench tab", tabs::closeSelectedTab),
    new PaletteAction("Reopen all tabs", "Restore every hidden workbench tab", tabs::reopenAllTabs),
    new PaletteAction("Open logs folder", "Show persisted workbench command logs",
                        runArtifacts::openLogsDirectory));
    }

    /**
     * Shows the in-workbench board settings panel.
     */
    protected void showDisplaySettings() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(0);
        }
        if (boardDetailTabs != null) {
            boardDetailTabs.setSelectedIndex(4);
        }
        toast(Toast.Kind.INFO, "Settings are in the side panel");
    }

    /**
     * Shows the external-engine settings panel.
     */
    protected void showEngineSettings() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(0);
        }
        if (boardDetailTabs != null) {
            boardDetailTabs.setSelectedIndex(5);
        }
        SwingUtilities.invokeLater(engineProtocolField::requestFocusInWindow);
    }

    /**
     * Shows the analysis data visualization panel.
     */
    protected void showAnalysisData() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(0);
        }
        if (boardDetailTabs != null) {
            boardDetailTabs.setSelectedIndex(2);
        }
    }

    /**
     * Copies the live-analysis data as CSV.
     */
    protected void copyAnalysisCsv() {
        copyText(analysisGraph.csvText());
        toast(Toast.Kind.SUCCESS, "Analysis CSV copied");
    }

    /**
     * Copies the live-analysis report.
     */
    protected void copyAnalysisReport() {
        copyText(analysisGraph.reportText());
        toast(Toast.Kind.SUCCESS, "Analysis report copied");
    }

    /**
     * Prints the live-analysis report.
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
     * Clears the live-analysis graph.
     */
    protected void clearAnalysisData() {
        analysisGraph.clearSamples();
        toast(Toast.Kind.INFO, "Analysis data cleared");
    }

    /**
     * Creates one settings toggle.
     *
     * @param text toggle text
     * @param tooltip tooltip text
     * @param selected selected state
     * @param onChange change callback
     * @return styled toggle
     */
    protected ToggleBox settingsToggle(String text, String tooltip, boolean selected,
            Consumer<Boolean> onChange) {
        ToggleBox toggle = withTooltip(new ToggleBox(text), tooltip);
        toggle.setSelected(selected);
        toggle.addActionListener(event -> onChange.accept(toggle.isSelected()));
        return toggle;
    }

    /**
     * Adds one display setting row and wires persistence.
     *
     * @param panel target panel
     * @param c constraints
     * @param row grid row
     * @param text toggle text
     * @param tooltip tooltip text
     * @param selected selected state
     * @param update update callback
     * @param refreshEval whether eval behavior should refresh immediately
     */
    protected void addSettingsToggle(JPanel panel, GridBagConstraints c, int row, String text, String tooltip,
            boolean selected, Consumer<Boolean> update, boolean refreshEval) {
        grid(panel, settingsToggle(text, tooltip, selected,
                value -> updateDisplaySetting(() -> update.accept(value), refreshEval)), c, 0, row, 1, 1);
    }

    /**
     * Applies one display setting change.
     *
     * @param update state update
     * @param refreshEval whether eval behavior should refresh immediately
     */
    protected void updateDisplaySetting(Runnable update, boolean refreshEval) {
        update.run();
        saveDisplaySettings();
        applyDisplaySettings(refreshEval);
    }

    /**
     * Focuses the FEN input field.
     */
    protected void focusFenField() {
        selectTab(TAB_ANALYZE);
        SwingUtilities.invokeLater(fenField::requestFocusInWindow);
    }

    /**
     * Focuses the command option filter.
     */
    protected void focusOptionFilter() {
        selectTab(TAB_COMMANDS);
        SwingUtilities.invokeLater(() -> {
            commandForm.expandOptionalFlags();
            commandForm.filterField().requestFocusInWindow();
        });
    }

    /**
     * Focuses the merged game-line editor.
     */
    protected void focusGameInput() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(1);
        }
        SwingUtilities.invokeLater(gameInput::requestFocusInWindow);
    }

    /**
     * Opens a new independent Analyze editor tab.
     */
    protected void openNewAnalyzeTab() {
        if (tabs != null) {
            tabs.duplicate(TAB_ANALYZE);
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
     * Reacts to top-level tab visibility changes. Expensive live analysis is
     * scoped to visible board panes so Dashboard/Commands idle without a
     * background UCI engine consuming a core.
     */
    protected void onWorkbenchTabVisibilityChanged() {
        networkPanel.setActive(tabs != null && tabs.isVisibleInPane(TAB_NETWORK));
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
     * Returns whether the Analyze board pane is visible in any editor group.
     *
     * @return true when Analyze is visible
     */
    protected boolean isAnalyzePaneVisible() {
        return tabs != null && tabs.isVisibleInPane(TAB_ANALYZE);
    }

}
