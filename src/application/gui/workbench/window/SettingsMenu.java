/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.window;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
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
     * Theme-aware radio glyph used by appearance menu items.
     */
    private static final Icon RADIO_ICON = new MenuRadioIcon();

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
        item.setOpaque(true);
        item.setBackground(item.getParent() instanceof JPopupMenu ? Theme.PANEL_SOLID : Theme.BG);
        item.setForeground(Theme.TEXT);
        item.setFont(Theme.font(12, Font.PLAIN));
        item.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        if (item instanceof JRadioButtonMenuItem radio) {
            styleRadioMenuItem(radio);
        }
    }

    /**
     * Styles one radio menu item with the workbench glyph rather than the
     * platform look-and-feel radio.
     *
     * @param item radio menu item
     */
    private static void styleRadioMenuItem(JRadioButtonMenuItem item) {
        item.setIcon(RADIO_ICON);
        item.setSelectedIcon(RADIO_ICON);
        item.setDisabledIcon(RADIO_ICON);
        item.setDisabledSelectedIcon(RADIO_ICON);
        item.setIconTextGap(8);
        item.setBorderPainted(false);
        item.setFocusPainted(false);
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

    /**
     * Theme-aware radio icon for settings menu rows.
     */
    private static final class MenuRadioIcon implements Icon {

        /**
         * Icon box size.
         */
        private static final int SIZE = 16;

        /**
         * Outer circle diameter.
         */
        private static final int OUTER = 11;

        /**
         * Inner selected dot diameter.
         */
        private static final int INNER = 5;

        /**
         * Stroke used for the outer ring.
         */
        private static final BasicStroke STROKE =
                new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        /**
         * Returns icon width.
         *
         * @return width
         */
        @Override
        public int getIconWidth() {
            return SIZE;
        }

        /**
         * Returns icon height.
         *
         * @return height
         */
        @Override
        public int getIconHeight() {
            return SIZE;
        }

        /**
         * Paints the themed radio ring and selected dot.
         *
         * @param component owner component
         * @param graphics graphics context
         * @param x icon x
         * @param y icon y
         */
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            boolean selected = component instanceof AbstractButton button && button.isSelected();
            boolean armed = component instanceof AbstractButton button
                    && (button.getModel().isArmed() || button.getModel().isRollover());
            boolean enabled = component == null || component.isEnabled();
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int ox = x + (SIZE - OUTER) / 2;
                int oy = y + (SIZE - OUTER) / 2;
                g.setStroke(STROKE);
                g.setColor(ringColor(selected, armed, enabled));
                g.drawOval(ox, oy, OUTER, OUTER);
                if (selected) {
                    int ix = x + (SIZE - INNER) / 2;
                    int iy = y + (SIZE - INNER) / 2;
                    g.setColor(enabled ? Theme.ACCENT : Theme.BUTTON_DISABLED_TEXT);
                    g.fillOval(ix, iy, INNER, INNER);
                }
            } finally {
                g.dispose();
            }
        }

        /**
         * Returns the ring color for a menu radio state.
         *
         * @param selected true when selected
         * @param armed true when hovered or armed
         * @param enabled true when enabled
         * @return ring color
         */
        private static java.awt.Color ringColor(boolean selected, boolean armed, boolean enabled) {
            if (!enabled) {
                return Theme.BUTTON_DISABLED_TEXT;
            }
            if (selected) {
                return Theme.ACCENT;
            }
            return armed ? Theme.TEXT : Theme.MUTED;
        }
    }
}
