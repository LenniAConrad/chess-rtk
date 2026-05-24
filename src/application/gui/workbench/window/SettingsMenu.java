package application.gui.workbench.window;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.MenuGlyphs;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;

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
         * Opens the display and board settings panel.
         */
        void showDisplaySettings();

        /**
         * Opens the external-engine settings panel.
         */
        void showEngineSettings();

        /**
         * Opens the searchable command palette.
         */
        void showCommandPalette();

        /**
         * Opens the persisted workbench log directory.
         */
        void openLogsDirectory();
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
     * Radio item selecting the light appearance.
     */
    private final JRadioButtonMenuItem lightModeItem = new JRadioButtonMenuItem("Light");

    /**
     * Radio item selecting the dark appearance.
     */
    private final JRadioButtonMenuItem darkModeItem = new JRadioButtonMenuItem("Dark");

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
     * Synchronizes checked appearance items with the current theme state.
     */
    public void syncMode() {
        Theme.Mode mode = controller.themeMode();
        lightModeItem.setSelected(mode != Theme.Mode.DARK);
        darkModeItem.setSelected(mode == Theme.Mode.DARK);
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
        JMenu settings = new JMenu("Settings");
        settings.add(createAppearanceMenu());
        settings.add(new JSeparator());
        settings.add(menuItem("Board Settings", "control COMMA", controller::showDisplaySettings));
        settings.add(menuItem("Engine Settings", null, controller::showEngineSettings));
        settings.add(new JSeparator());
        settings.add(menuItem("Command Palette", "control K", controller::showCommandPalette));
        settings.add(menuItem("Open Logs Folder", null, controller::openLogsDirectory));
        menuBar.add(settings);
    }

    /**
     * Creates the appearance submenu.
     *
     * @return appearance submenu
     */
    private JMenu createAppearanceMenu() {
        JMenu appearance = new JMenu("Appearance");
        ButtonGroup group = new ButtonGroup();
        group.add(lightModeItem);
        group.add(darkModeItem);
        lightModeItem.addActionListener(event -> controller.setThemeMode(Theme.Mode.LIGHT));
        darkModeItem.addActionListener(event -> controller.setThemeMode(Theme.Mode.DARK));
        appearance.add(lightModeItem);
        appearance.add(darkModeItem);
        return appearance;
    }

    /**
     * Creates one menu item.
     *
     * @param text item label
     * @param accelerator optional accelerator keystroke
     * @param action action to run
     * @return menu item
     */
    private static JMenuItem menuItem(String text, String accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        }
        item.addActionListener(event -> action.run());
        return item;
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
            separator.setBackground(Theme.BG);
            separator.setForeground(Theme.LINE);
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
        popup.setOpaque(true);
        popup.setBackground(Theme.PANEL_SOLID);
        popup.setForeground(Theme.TEXT);
        popup.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        for (Component child : popup.getComponents()) {
            if (child instanceof JComponent component) {
                component.setBackground(Theme.PANEL_SOLID);
            }
            styleMenuTree(child);
        }
    }

}
