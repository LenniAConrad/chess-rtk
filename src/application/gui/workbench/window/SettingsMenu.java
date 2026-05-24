package application.gui.workbench.window;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.MenuGlyphs;
import application.gui.workbench.ui.Ui;
import java.awt.Component;
import java.awt.Container;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.MenuSelectionManager;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;

/**
 * Top-level settings menu for the native workbench window.
 */
public final class SettingsMenu {

    /**
     * Preferred size for the grouped settings popup.
     */
    private static final Dimension SETTINGS_PANEL_SIZE = new Dimension(390, 294);

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
     * Chip selector for the light/dark appearance.
     */
    private final ChipGroup appearanceChips = new ChipGroup(List.of("Light", "Dark"));

    /**
     * Chip selector for global sound effects.
     */
    private final ChipGroup soundChips = new ChipGroup(List.of("On", "Muted"));

    /**
     * Large grouped settings popup content.
     */
    private final JPanel settingsPanel = new JPanel(new GridBagLayout());

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
        appearanceChips.setSelectedIndex(mode == Theme.Mode.DARK ? 1 : 0);
        soundChips.setSelectedIndex(controller.soundEnabled() ? 0 : 1);
    }

    /**
     * Reapplies current colors and fonts after a theme switch.
     */
    public void refreshTheme() {
        styleMenuBar(menuBar);
        styleMenuTree(menuBar);
        Theme.refreshComponentTree(settingsPanel);
        settingsPanel.setBackground(Theme.PANEL_SOLID);
    }

    /**
     * Builds all menus and menu items.
     */
    private void buildMenuBar() {
        JMenu settings = new JMenu("Settings");
        settings.getPopupMenu().setPreferredSize(SETTINGS_PANEL_SIZE);
        settings.add(createSettingsPanel());
        menuBar.add(settings);
    }

    /**
     * Creates the grouped settings panel shown by the Settings menu.
     *
     * @return settings panel
     */
    private JPanel createSettingsPanel() {
        settingsPanel.setName("settingsPanel");
        settingsPanel.setOpaque(true);
        settingsPanel.setBackground(Theme.PANEL_SOLID);
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
        settingsPanel.setPreferredSize(SETTINGS_PANEL_SIZE);
        appearanceChips.setName("settings.appearance");
        soundChips.setName("settings.sound");
        appearanceChips.setToolTipText("Choose the workbench palette");
        soundChips.setToolTipText("Enable or mute workbench sound cues");
        appearanceChips.setOnSelect(index -> {
            SoundService.play(SoundCue.UI_CLICK);
            controller.setThemeMode(index == 1 ? Theme.Mode.DARK : Theme.Mode.LIGHT);
            syncMode();
        });
        soundChips.setOnSelect(index -> {
            SoundService.play(SoundCue.UI_CLICK);
            controller.setSoundEnabled(index == 0);
            syncMode();
        });

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
        int row = 0;
        addPanelRow(settingsPanel, sectionLabel("Appearance"), c, row++);
        addPanelRow(settingsPanel, chipRow("Theme", "Switch all workbench chrome and boards", appearanceChips),
                c, row++);
        addPanelRow(settingsPanel, sectionLabel("Feedback"), c, row++);
        addPanelRow(settingsPanel, chipRow("Sound effects", "Moves, puzzles, MCTS, jobs, and controls", soundChips),
                c, row++);
        addPanelRow(settingsPanel, actionGrid(), c, row);
        return settingsPanel;
    }

    /**
     * Creates a compact two-column action grid.
     *
     * @return action grid
     */
    private JPanel actionGrid() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(3, 0, 3, 6);
        grid.add(actionButton("Board", controller::showDisplaySettings), c);
        c.gridx = 1;
        c.insets = new Insets(3, 6, 3, 0);
        grid.add(actionButton("Engine", controller::showEngineSettings), c);
        c.gridx = 0;
        c.gridy = 1;
        c.insets = new Insets(3, 0, 3, 6);
        grid.add(actionButton("Sound", controller::showSoundSettings), c);
        c.gridx = 1;
        c.insets = new Insets(3, 6, 3, 0);
        grid.add(actionButton("Actions", controller::showCommandPalette), c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.insets = new Insets(3, 0, 0, 0);
        grid.add(actionButton("Open logs folder", controller::openLogsDirectory), c);
        return grid;
    }

    /**
     * Creates one action button and closes the popup before running it.
     *
     * @param text button text
     * @param action action to run
     * @return action button
     */
    private static JButton actionButton(String text, Runnable action) {
        return Ui.button(text, false, event -> {
            MenuSelectionManager.defaultManager().clearSelectedPath();
            action.run();
        });
    }

    /**
     * Adds a component row to the settings panel.
     *
     * @param panel target panel
     * @param component component to add
     * @param c base constraints
     * @param row row index
     */
    private static void addPanelRow(JPanel panel, Component component, GridBagConstraints c, int row) {
        c.gridy = row;
        panel.add(component, c);
    }

    /**
     * Creates a section label.
     *
     * @param text label text
     * @return section label
     */
    private static JLabel sectionLabel(String text) {
        JLabel label = Theme.section(text);
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        return label;
    }

    /**
     * Creates a label-plus-chip settings row.
     *
     * @param title row title
     * @param detail row detail
     * @param chips selector chips
     * @return settings row
     */
    private static JPanel chipRow(String title, String detail, ChipGroup chips) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        JPanel copy = new JPanel(new BorderLayout(0, 1));
        copy.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.font(13, Font.BOLD));
        Theme.foreground(titleLabel, Theme.ForegroundRole.TEXT);
        JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(Theme.font(11, Font.PLAIN));
        Theme.foreground(detailLabel, Theme.ForegroundRole.MUTED);
        copy.add(titleLabel, BorderLayout.NORTH);
        copy.add(detailLabel, BorderLayout.CENTER);
        row.add(copy, BorderLayout.CENTER);
        row.add(chips, BorderLayout.EAST);
        return row;
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
