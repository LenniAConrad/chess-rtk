package application.gui.workbench.window;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.MenuGlyphs;
import application.gui.workbench.ui.Ui;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;

/**
 * Top-level settings menu for the native workbench window.
 */
public final class SettingsMenu {

    /**
     * Window callbacks used by the menu without coupling it to one concrete
     * window subclass.
     */
    public interface Controller {

        /**
         * Returns the active workbench appearance mode.
         *
         * @return active appearance mode
         */
        Theme.Mode themeMode();

        /**
         * Applies a new workbench appearance mode.
         *
         * @param mode requested appearance mode
         */
        void setThemeMode(Theme.Mode mode);

        /**
         * Returns the active workbench UI density.
         *
         * @return active density
         */
        default Theme.Density density() {
            return Theme.density();
        }

        /**
         * Applies a new workbench UI density.
         *
         * @param density requested density
         */
        default void setDensity(Theme.Density density) {
            // Implemented by the window; default keeps unrelated controllers compiling.
        }

        /**
         * Returns whether workbench sound effects are enabled.
         *
         * @return true when sounds are enabled
         */
        boolean soundEnabled();

        /**
         * Applies the global sound-effects enabled state.
         *
         * @param enabled true to enable sounds
         */
        void setSoundEnabled(boolean enabled);

        /**
         * Opens the display and board settings panel.
         */
        void showDisplaySettings();

        /**
         * Opens the external-engine settings panel.
         */
        void showEngineSettings();

        /**
         * Opens the display panel where sound volume is configured.
         */
        void showSoundSettings();

        /**
         * Opens the searchable command palette.
         */
        void showCommandPalette();

        /**
         * Opens the persisted workbench log directory.
         */
        void openLogsDirectory();

        /**
         * Opens a PGN file or the PGN explorer.
         */
        default void openPgn() { }

        /**
         * Saves the current game as PGN.
         */
        default void savePgn() { }

        /**
         * Copies the current FEN.
         */
        default void copyFen() { }

        /**
         * Copies the generated command preview.
         */
        default void copyBuiltCommand() { }

        /**
         * Opens the dashboard.
         */
        default void openDashboard() { }

        /**
         * Opens the analysis workspace.
         */
        default void openAnalyze() { }

        /**
         * Opens the command builder.
         */
        default void openCommands() { }

        /**
         * Opens batch workflows.
         */
        default void openBatch() { }

        /**
         * Opens dataset tools.
         */
        default void openDatasets() { }

        /**
         * Opens publishing tools.
         */
        default void openPublish() { }

        /**
         * Opens network visualizations.
         */
        default void openNetwork() { }

        /**
         * Opens MCTS inspection.
         */
        default void openMcts() { }

        /**
         * Opens puzzles.
         */
        default void openPuzzles() { }

        /**
         * Opens a new independent analysis tab.
         */
        default void newAnalyzeTab() { }

        /**
         * Shows command output.
         */
        default void showConsole() { }

        /**
         * Shows persisted logs.
         */
        default void showLogs() { }

        /**
         * Jumps to the first position.
         */
        default void firstPosition() { }

        /**
         * Moves to the previous position.
         */
        default void previousPosition() { }

        /**
         * Moves to the next position.
         */
        default void nextPosition() { }

        /**
         * Jumps to the final position.
         */
        default void lastPosition() { }

        /**
         * Runs the generated command.
         */
        default void runBuiltCommand() { }

        /**
         * Runs best-move analysis.
         */
        default void runBestMove() { }

        /**
         * Runs engine analysis.
         */
        default void runAnalyze() { }

        /**
         * Runs perft.
         */
        default void runPerft() { }

        /**
         * Runs the selected batch workflow.
         */
        default void runBatch() { }

        /**
         * Runs the publishing workflow.
         */
        default void runPublishing() { }

        /**
         * Runs all health checks.
         */
        default void runAllChecks() { }

        /**
         * Stops the running command.
         */
        default void stopCommand() { }

        /**
         * Splits the selected editor to the right.
         */
        default void splitRight() { }

        /**
         * Splits the selected editor below.
         */
        default void splitDown() { }

        /**
         * Reopens all workbench tabs.
         */
        default void reopenAllTabs() { }
    }

    /**
     * Controller that owns the actions exposed by the menu.
     */
    private final Controller controller;

    /**
     * Native Swing menu bar installed on the workbench frame.
     */
    private final JMenuBar menuBar = new JMenuBar();

    /**
     * Creates the settings menu.
     *
     * @param controller window callback controller
     */
    public SettingsMenu(Controller controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
        buildMenuBar();
        syncMode();
        refreshTheme();
    }

    /**
     * Returns the menu-bar component to install on the frame.
     *
     * @return menu-bar component
     */
    public JMenuBar component() {
        return menuBar;
    }

    /**
     * No-op placeholder retained so existing callers (theme-switch hooks,
     * sound listeners) keep compiling. The popover chips that this used to
     * sync have been removed in favour of the unified SettingsDialog.
     */
    public void syncMode() {
        // Settings live in the unified dialog now; nothing to sync here.
    }

    /**
     * Reapplies current colors and fonts after a theme switch.
     */
    public void refreshTheme() {
        styleMenuBar(menuBar);
        styleMenuTree(menuBar);
    }

    /**
     * Builds all menus and menu items.
     */
    private void buildMenuBar() {
        menuBar.add(menu("File",
                item("Open PGN…", "Ctrl+P", controller::openPgn),
                item("Save PGN", null, controller::savePgn),
                separator(),
                item("Open Logs Folder", null, controller::openLogsDirectory),
                separator(),
                item("Settings…", "Ctrl+,", controller::showDisplaySettings)));
        menuBar.add(menu("Edit",
                item("Command Palette…", "Ctrl+K", controller::showCommandPalette),
                separator(),
                item("Copy FEN", null, controller::copyFen),
                item("Copy Built Command", null, controller::copyBuiltCommand)));
        menuBar.add(menu("Selection",
                item("First Position", "Home", controller::firstPosition),
                item("Previous Position", "Left", controller::previousPosition),
                item("Next Position", "Right", controller::nextPosition),
                item("Last Position", "End", controller::lastPosition)));
        menuBar.add(menu("View",
                item("Dashboard", "Ctrl+1", controller::openDashboard),
                item("Analyze", "Ctrl+2", controller::openAnalyze),
                item("Commands", "Ctrl+3", controller::openCommands),
                item("Batch", "Ctrl+4", controller::openBatch),
                item("Datasets", "Ctrl+5", controller::openDatasets),
                item("Publish", "Ctrl+6", controller::openPublish),
                item("Network", "Ctrl+7", controller::openNetwork),
                item("MCTS", "Ctrl+8", controller::openMcts),
                item("Puzzles", "Ctrl+9", controller::openPuzzles),
                separator(),
                item("Console", "Ctrl+`", controller::showConsole),
                item("Logs", null, controller::showLogs),
                separator(),
                densityMenu(),
                separator(),
                item("Board Settings…", null, controller::showDisplaySettings),
                item("Engine Settings…", null, controller::showEngineSettings)));
        menuBar.add(menu("Go",
                item("New Analyze Tab", null, controller::newAnalyzeTab),
                separator(),
                item("Split Tab Right", "Ctrl+\\", controller::splitRight),
                item("Split Tab Down", "Ctrl+Shift+\\", controller::splitDown),
                item("Restore Closed Tabs", null, controller::reopenAllTabs)));
        menuBar.add(menu("Run",
                item("Run Built Command", null, controller::runBuiltCommand),
                item("Best Move", null, controller::runBestMove),
                item("Analyze Position", null, controller::runAnalyze),
                item("Perft", null, controller::runPerft),
                separator(),
                item("Run Batch", null, controller::runBatch),
                item("Run Publishing", null, controller::runPublishing),
                item("Run All Checks", null, controller::runAllChecks),
                separator(),
                item("Stop Command", "Esc", controller::stopCommand)));
        menuBar.add(menu("Terminal",
                item("Show Console", "Ctrl+`", controller::showConsole),
                item("Show Logs", null, controller::showLogs),
                item("Open Logs Folder", null, controller::openLogsDirectory)));
        menuBar.add(menu("Help",
                item("Command Palette…", "Ctrl+K", controller::showCommandPalette),
                item("Settings…", "Ctrl+,", controller::showDisplaySettings)));
    }

    /**
     * Builds the UI density submenu: a radio group reflecting and switching the
     * active {@link Theme.Density}. Ordered least-to-most roomy
     * (Compact, Dense, Comfortable).
     *
     * @return density submenu
     */
    private JMenu densityMenu() {
        JMenu menu = new JMenu("Density");
        ButtonGroup group = new ButtonGroup();
        Theme.Density active = controller.density();
        for (Theme.Density value : new Theme.Density[] {
                Theme.Density.COMPACT, Theme.Density.DENSE, Theme.Density.COMFORTABLE }) {
            JRadioButtonMenuItem entry = new JRadioButtonMenuItem(value.label(), value == active);
            entry.addActionListener(event -> controller.setDensity(value));
            group.add(entry);
            menu.add(entry);
        }
        return menu;
    }

    /**
     * Creates one top-level menu.
     *
     * @param label menu label
     * @param items menu contents
     * @return menu
     */
    private static JMenu menu(String label, Component... items) {
        JMenu menu = new JMenu(label);
        for (Component item : items) {
            menu.add(item);
        }
        return menu;
    }

    /**
     * Creates one action menu item.
     *
     * @param label item label
     * @param shortcut optional shortcut hint
     * @param action item action
     * @return menu item
     */
    private static JMenuItem item(String label, String shortcut, Runnable action) {
        JMenuItem item = new JMenuItem(shortcut == null || shortcut.isBlank()
                ? label
                : label + "    " + shortcut);
        item.addActionListener(event -> action.run());
        return item;
    }

    /**
     * Creates a menu separator.
     *
     * @return separator
     */
    private static JSeparator separator() {
        return new JSeparator();
    }

    /**
     * Styles the menu bar itself.
     *
     * @param bar menu bar
     */
    private static void styleMenuBar(JMenuBar bar) {
        bar.setOpaque(true);
        bar.setBackground(Theme.BG);
        bar.setForeground(Theme.TEXT);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
        bar.setFont(Theme.font(12, Font.PLAIN));
    }

    /**
     * Recursively styles menus, menu items, popups, and separators.
     *
     * @param component root component
     */
    private static void styleMenuTree(Component component) {
        if (component instanceof JMenuBar bar) {
            styleMenuBar(bar);
        } else if (component instanceof JMenu menu) {
            styleMenuItem(menu);
            stylePopup(menu.getPopupMenu());
        } else if (component instanceof JMenuItem item) {
            styleMenuItem(item);
        } else if (component instanceof JPopupMenu popup) {
            stylePopup(popup);
        } else if (component instanceof JSeparator separator) {
            // Match the popup surface (PANEL_SOLID), not the window BG — otherwise
            // the separator band is a lighter stripe against the darker menu.
            separator.setBackground(Theme.PANEL_SOLID);
            separator.setForeground(Theme.LINE);
        } else if (component instanceof JPanel panel && "settingsPanel".equals(panel.getName())) {
            panel.setOpaque(true);
            panel.setBackground(Theme.PANEL_SOLID);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleMenuTree(child);
            }
        }
    }

    /**
     * Styles one clickable menu item.
     *
     * @param item menu item
     */
    private static void styleMenuItem(JMenuItem item) {
        if (item instanceof JRadioButtonMenuItem radio) {
            styleRadioMenuItem(radio);
        } else if (item instanceof JCheckBoxMenuItem check) {
            MenuGlyphs.styleCheckItem(check);
        } else if (item.getParent() instanceof JPopupMenu) {
            MenuGlyphs.styleItem(item);
        } else {
            item.setOpaque(true);
            item.setBackground(Theme.BG);
            item.setForeground(Theme.TEXT);
            item.setFont(Theme.font(12, Font.PLAIN));
            item.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        }
    }

    /**
     * Styles one radio menu item with the workbench glyph rather than the
     * platform look-and-feel radio.
     *
     * @param item radio menu item
     */
    private static void styleRadioMenuItem(JRadioButtonMenuItem item) {
        MenuGlyphs.styleRadioItem(item);
    }

    /**
     * Styles one popup menu.
     *
     * @param popup popup menu
     */
    private static void stylePopup(JPopupMenu popup) {
        Ui.stylePopupMenu(popup);
        for (Component child : popup.getComponents()) {
            if (child instanceof JComponent component) {
                component.setBackground(Theme.PANEL_SOLID);
            }
            styleMenuTree(child);
        }
    }

}
