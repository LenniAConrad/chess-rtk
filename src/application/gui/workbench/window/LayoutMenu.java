package application.gui.workbench.window;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.layout.EditorLayoutCommands;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * VS Code-style compact layout controls for the workbench chrome.
 */
public final class LayoutMenu {

    /**
     * Size of one chrome toolbar button.
     */
    private static final int BUTTON_SIZE = 28;

    /**
     * Width of the customize-layout popup.
     */
    private static final int POPUP_WIDTH = 320;

    /**
     * Window callbacks used by the layout menu without depending on one
     * concrete window class.
     */
    public interface Controller {

        /**
         * Returns whether the status bar is visible.
         *
         * @return true when visible
         */
        boolean statusBarVisible();

        /**
         * Applies status-bar visibility.
         *
         * @param visible true to show the status bar
         */
        void setStatusBarVisible(boolean visible);

        /**
         * Splits the selected editor to the right.
         */
        void splitRight();

        /**
         * Splits the selected editor below.
         */
        void splitDown();

        /**
         * Splits the selected editor to the left.
         */
        void splitLeft();

        /**
         * Splits the selected editor above.
         */
        void splitUp();

        /**
         * Detaches the selected editor tab into a new window.
         */
        void detachTab();

        /**
         * Reopens every workbench tab.
         */
        void reopenAllTabs();

        /**
         * Closes every tab except the selected one.
         */
        void closeOtherTabs();

        /**
         * Returns the number of open tabs.
         *
         * @return open tab count
         */
        int openTabCount();

        /**
         * Returns the number of visible editor groups.
         *
         * @return editor group count
         */
        int visibleGroupCount();

        /**
         * Returns whether the active tab can be split into another editor group.
         *
         * @return true when a split action is available
         */
        default boolean canSplitActiveTab() {
            return openTabCount() > 1;
        }

        /**
         * Returns whether the active tab can be detached.
         *
         * @return true when a tab is selected
         */
        default boolean canDetachActiveTab() {
            return openTabCount() > 0;
        }

        /**
         * Returns whether closing other tabs would do useful work.
         *
         * @return true when another tab exists
         */
        default boolean canCloseOtherTabs() {
            return openTabCount() > 1;
        }

        /**
         * Returns whether hidden or detached tabs can be restored.
         *
         * @return true when restore can change layout state
         */
        default boolean hasRestorableTabs() {
            return false;
        }
    }

    /**
     * Controller that owns the layout actions.
     */
    private final Controller controller;

    /**
     * Top-right chrome button strip.
     */
    private final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));

    /**
     * Button that opens the customize-layout popup.
     */
    private final ChromeButton customizeButton;

    /**
     * Toolbar split-right action.
     */
    private final ChromeButton splitRightButton;

    /**
     * Toolbar split-down action.
     */
    private final ChromeButton splitDownButton;

    /**
     * Toolbar restore-tabs action.
     */
    private final ChromeButton restoreTabsButton;

    /**
     * Creates layout controls.
     *
     * @param controller callback controller
     */
    public LayoutMenu(Controller controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
        customizeButton = button("Customize layout and visibility",
                LayoutIcon.Kind.CUSTOMIZE, this::showPopup);
        toolbar.add(customizeButton);
        splitRightButton = button(EditorLayoutCommands.SPLIT_RIGHT_TOOLTIP,
                LayoutIcon.Kind.SPLIT_RIGHT, controller::splitRight);
        splitDownButton = button(EditorLayoutCommands.SPLIT_DOWN_TOOLTIP,
                LayoutIcon.Kind.SPLIT_DOWN, controller::splitDown);
        restoreTabsButton = button(EditorLayoutCommands.TABS_RESTORE_CLOSED_LABEL,
                LayoutIcon.Kind.RESTORE, controller::reopenAllTabs);
        toolbar.add(splitRightButton);
        toolbar.add(splitDownButton);
        toolbar.add(restoreTabsButton);
        refreshTheme();
    }

    /**
     * Returns the chrome component.
     *
     * @return toolbar component
     */
    public JComponent component() {
        return toolbar;
    }

    /**
     * Reapplies current theme colors to the layout controls.
     */
    public void refreshTheme() {
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 8));
        for (Component child : toolbar.getComponents()) {
            if (child instanceof ChromeButton button) {
                button.refreshTheme();
            }
        }
        refreshControlStates();
        toolbar.revalidate();
        toolbar.repaint();
    }

    /**
     * Updates layout-toolbar enabled states from the live editor state.
     */
    public void refreshControlStates() {
        updateButtonState(splitRightButton, controller.canSplitActiveTab(),
                EditorLayoutCommands.SPLIT_RIGHT_ACCESSIBLE_NAME,
                EditorLayoutCommands.SPLIT_RIGHT_TOOLTIP,
                EditorLayoutCommands.SPLIT_RIGHT_DISABLED_TOOLTIP);
        updateButtonState(splitDownButton, controller.canSplitActiveTab(),
                EditorLayoutCommands.SPLIT_DOWN_ACCESSIBLE_NAME,
                EditorLayoutCommands.SPLIT_DOWN_TOOLTIP,
                EditorLayoutCommands.SPLIT_DOWN_DISABLED_TOOLTIP);
        updateButtonState(restoreTabsButton, controller.hasRestorableTabs(),
                EditorLayoutCommands.TABS_RESTORE_CLOSED_LABEL,
                EditorLayoutCommands.TABS_RESTORE_CLOSED_LABEL,
                EditorLayoutCommands.RESTORE_CLOSED_DISABLED_TOOLTIP);
    }

    /**
     * Applies state, tooltip, and accessibility text to one chrome button.
     *
     * @param button target button
     * @param enabled whether the action is currently available
     * @param accessibleName stable accessible action name
     * @param enabledTooltip tooltip while enabled
     * @param disabledTooltip tooltip while disabled
     */
    private static void updateButtonState(ChromeButton button, boolean enabled, String accessibleName,
            String enabledTooltip, String disabledTooltip) {
        button.setEnabled(enabled);
        button.setToolTipText(enabled ? enabledTooltip : disabledTooltip);
        button.getAccessibleContext().setAccessibleName(accessibleName);
        button.getAccessibleContext().setAccessibleDescription(enabled ? enabledTooltip : disabledTooltip);
    }

    /**
     * Creates one chrome icon button.
     *
     * @param tooltip tooltip text
     * @param kind icon kind
     * @param action action to run
     * @return button
     */
    private ChromeButton button(String tooltip, LayoutIcon.Kind kind, Runnable action) {
        ChromeButton button = new ChromeButton();
        button.setToolTipText(tooltip);
        button.setActionCommand(actionCommand(kind));
        button.setName(actionCommand(kind));
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.getAccessibleContext().setAccessibleDescription(tooltip);
        button.setIcon(new LayoutIcon(kind));
        button.addActionListener(event -> {
            if (!button.isEnabled()) {
                return;
            }
            SoundService.play(SoundCue.UI_CLICK);
            action.run();
            refreshControlStates();
            button.repaint();
        });
        return button;
    }

    /**
     * Returns the stable command id for a chrome layout button.
     *
     * @param kind icon/action kind
     * @return action command
     */
    private static String actionCommand(LayoutIcon.Kind kind) {
        return switch (kind) {
            case CUSTOMIZE -> "workbench.layout.customize";
            case SPLIT_RIGHT -> EditorLayoutCommands.SPLIT_RIGHT;
            case SPLIT_DOWN -> EditorLayoutCommands.SPLIT_DOWN;
            case RESTORE -> EditorLayoutCommands.TABS_RESTORE_CLOSED;
        };
    }

    /**
     * Shows the customize-layout popup below the layout button.
     */
    private void showPopup() {
        refreshControlStates();
        JPopupMenu popup = buildPopup();
        customizeButton.setMenuOpen(true);
        popup.addPopupMenuListener(new PopupMenuListener() {
            /**
             * Handles a visible popup.
             *
             * @param event popup event
             */
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                customizeButton.setMenuOpen(true);
            }

            /**
             * Handles a hidden popup.
             *
             * @param event popup event
             */
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                customizeButton.setMenuOpen(false);
            }

            /**
             * Handles a cancelled popup.
             *
             * @param event popup event
             */
            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
                customizeButton.setMenuOpen(false);
            }
        });
        popup.show(customizeButton, 0, customizeButton.getHeight());
    }

    /**
     * Builds a fresh customize-layout popup.
     *
     * @return popup menu
     */
    private JPopupMenu buildPopup() {
        JPopupMenu popup = new JPopupMenu();
        popup.add(header());
        popup.add(section("Visibility"));
        popup.add(checkItem("Menu Bar", "workbench.layout.menuBar.visible", true, false,
                "The menu bar is always visible.", ignored -> { }));
        popup.add(checkItem("Editor Tabs", "workbench.layout.editorTabs.visible", true, false,
                "Editor tabs are always visible.", ignored -> { }));
        popup.add(checkItem("Status Bar", "workbench.layout.statusBar.visible",
                controller.statusBarVisible(), true, "Show or hide the bottom status bar.",
                controller::setStatusBarVisible));
        popup.add(new JSeparator());
        popup.add(section("Editor Groups"));
        boolean canSplit = controller.canSplitActiveTab();
        popup.add(actionItem(EditorLayoutCommands.TAB_SPLIT_RIGHT_LABEL, EditorLayoutCommands.TAB_SPLIT_RIGHT,
                "Ctrl + \\", canSplit, EditorLayoutCommands.SPLIT_DISABLED_TOOLTIP,
                controller::splitRight));
        popup.add(actionItem(EditorLayoutCommands.TAB_SPLIT_DOWN_LABEL, EditorLayoutCommands.TAB_SPLIT_DOWN,
                "Ctrl + Shift + \\", canSplit, EditorLayoutCommands.SPLIT_DISABLED_TOOLTIP,
                controller::splitDown));
        popup.add(actionItem(EditorLayoutCommands.TAB_SPLIT_LEFT_LABEL, EditorLayoutCommands.TAB_SPLIT_LEFT,
                null, canSplit, EditorLayoutCommands.SPLIT_DISABLED_TOOLTIP,
                controller::splitLeft));
        popup.add(actionItem(EditorLayoutCommands.TAB_SPLIT_UP_LABEL, EditorLayoutCommands.TAB_SPLIT_UP,
                null, canSplit, EditorLayoutCommands.SPLIT_DISABLED_TOOLTIP,
                controller::splitUp));
        popup.add(actionItem(EditorLayoutCommands.TAB_DETACH_LABEL, EditorLayoutCommands.TAB_DETACH,
                null, controller.canDetachActiveTab(), EditorLayoutCommands.DETACH_DISABLED_TOOLTIP,
                controller::detachTab));
        popup.add(actionItem(EditorLayoutCommands.TAB_CLOSE_OTHERS_LABEL, EditorLayoutCommands.TAB_CLOSE_OTHERS,
                null, controller.canCloseOtherTabs(), EditorLayoutCommands.CLOSE_OTHERS_DISABLED_TOOLTIP,
                controller::closeOtherTabs));
        popup.add(actionItem(EditorLayoutCommands.TABS_RESTORE_CLOSED_LABEL,
                EditorLayoutCommands.TABS_RESTORE_CLOSED,
                null, controller.hasRestorableTabs(), EditorLayoutCommands.RESTORE_CLOSED_DISABLED_TOOLTIP,
                controller::reopenAllTabs));
        popup.add(new JSeparator());
        popup.add(summary());
        stylePopupTree(popup);
        return popup;
    }

    /**
     * Creates the popup header.
     *
     * @return header component
     */
    private static JComponent header() {
        JLabel label = new JLabel("Customize Layout", SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(7, 12, 6, 12));
        label.setFont(Theme.font(13, Font.PLAIN));
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(label, java.awt.BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(POPUP_WIDTH, 32));
        return panel;
    }

    /**
     * Creates one section row.
     *
     * @param text section label
     * @return section row
     */
    private static JComponent section(String text) {
        JLabel label = new JLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder(8, 12, 3, 12));
        label.setFont(Theme.font(11, Font.PLAIN));
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(label, java.awt.BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates one visibility checkbox row.
     *
     * @param text row label
     * @param selected selected state
     * @param enabled enabled state
     * @param action action receiving the selected state
     * @return checkbox item
     */
    private static JCheckBoxMenuItem checkItem(String text, String command, boolean selected, boolean enabled,
            String detail, Consumer<Boolean> action) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, selected);
        item.setActionCommand(command);
        item.setEnabled(enabled);
        item.setToolTipText(detail);
        item.getAccessibleContext().setAccessibleName(text);
        item.getAccessibleContext().setAccessibleDescription(detail);
        item.addActionListener(event -> {
            if (!item.isEnabled()) {
                return;
            }
            SoundService.play(SoundCue.UI_CLICK);
            action.accept(Boolean.valueOf(item.isSelected()));
        });
        return item;
    }

    /**
     * Creates one command row.
     *
     * @param text row label
     * @param shortcut optional shortcut label
     * @param action action to run
     * @return menu item
     */
    private static JMenuItem actionItem(String text, String command, String shortcut,
            boolean enabled, String disabledTooltip, Runnable action) {
        JMenuItem item = new ShortcutItem(text, shortcut);
        item.setActionCommand(command);
        item.setEnabled(enabled);
        item.setToolTipText(enabled ? text : disabledTooltip);
        item.getAccessibleContext().setAccessibleName(text);
        item.getAccessibleContext().setAccessibleDescription(enabled ? text : disabledTooltip);
        item.addActionListener(event -> {
            if (!item.isEnabled()) {
                return;
            }
            SoundService.play(SoundCue.UI_CLICK);
            action.run();
        });
        return item;
    }

    /**
     * Creates the read-only layout summary row.
     *
     * @return summary item
     */
    private JMenuItem summary() {
        String text = controller.openTabCount() + " tabs, "
                + controller.visibleGroupCount() + " editor group(s)";
        JMenuItem item = new JMenuItem(text);
        item.setActionCommand("workbench.layout.summary");
        item.setToolTipText("Current layout: " + text);
        item.getAccessibleContext().setAccessibleName("Current layout summary");
        item.getAccessibleContext().setAccessibleDescription("Current layout: " + text);
        item.setEnabled(false);
        return item;
    }

    /**
     * Recursively styles popup components.
     *
     * @param component root component
     */
    private static void stylePopupTree(Component component) {
        if (component instanceof JPopupMenu popup) {
            Ui.stylePopupMenu(popup);
        } else if (component instanceof JMenuItem item) {
            styleMenuItem(item);
        } else if (component instanceof JSeparator separator) {
            separator.setBackground(Theme.PANEL_SOLID);
            separator.setForeground(Theme.LINE);
        } else if (component instanceof JComponent panel) {
            panel.setOpaque(true);
            panel.setBackground(Theme.PANEL_SOLID);
            panel.setForeground(Theme.TEXT);
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                stylePopupTree(child);
            }
        }
    }

    /**
     * Styles one popup item.
     *
     * @param item menu item
     */
    private static void styleMenuItem(JMenuItem item) {
        if (item instanceof JCheckBoxMenuItem checkbox) {
            Ui.styleCheckMenuItem(checkbox);
        } else {
            Ui.styleMenuItem(item);
        }
    }

    /**
     * Compact menu-bar button with VS Code-style hover treatment.
     */
    private static final class ChromeButton extends JButton {

        /**
         * Serialization identifier for Swing button compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Whether the attached popup is currently visible.
         */
        private boolean menuOpen;

        /**
         * Creates a chrome button.
         */
        ChromeButton() {
            setText("");
            setPreferredSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
            setMinimumSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
            setMaximumSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setRolloverEnabled(true);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        /**
         * Applies current theme tokens.
         */
        void refreshTheme() {
            setForeground(Theme.MUTED);
            setBackground(Theme.BG);
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            repaint();
        }

        /**
         * Updates popup-open state.
         *
         * @param open true while the popup is open
         */
        void setMenuOpen(boolean open) {
            menuOpen = open;
            repaint();
        }

        /**
         * Returns whether hover or popup state should be painted.
         *
         * @return true when highlighted
         */
        private boolean highlighted() {
            return menuOpen || getModel().isRollover() || getModel().isPressed();
        }

        /**
         * Paints the hover background and icon.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                if (highlighted()) {
                    g.setColor(getModel().isPressed() ? Theme.SELECTION : Theme.SELECTION_SOLID);
                    g.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 4, 4);
                }
                if (hasFocus()) {
                    g.setColor(Theme.withAlpha(Theme.ACCENT, 190));
                    g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 4, 4);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    /**
     * Menu item that paints a compact right-aligned shortcut hint.
     */
    private static final class ShortcutItem extends JMenuItem {

        /**
         * Serialization identifier for Swing menu item compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Optional shortcut label.
         */
        private final String shortcut;

        /**
         * Creates a shortcut menu item.
         *
         * @param text item label
         * @param shortcut shortcut label, or null
         */
        ShortcutItem(String text, String shortcut) {
            super(text);
            this.shortcut = shortcut;
        }

        /**
         * Paints the normal menu item and a muted shortcut hint.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (shortcut == null || shortcut.isBlank()) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(isEnabled() ? Theme.MUTED : Theme.BUTTON_DISABLED_TEXT);
                g.setFont(Theme.font(11, Font.PLAIN));
                java.awt.FontMetrics metrics = g.getFontMetrics();
                int x = getWidth() - 12 - metrics.stringWidth(shortcut);
                int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(shortcut, x, y);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Small Java2D layout icon set.
     */
    private static final class LayoutIcon implements Icon {

        /**
         * Icon viewport size.
         */
        private static final int SIZE = 16;

        /**
         * Stroke used for layout glyphs.
         */
        private static final BasicStroke STROKE =
                new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        /**
         * Icon kind.
         */
        private final Kind kind;

        /**
         * Creates a layout icon.
         *
         * @param kind icon kind
         */
        LayoutIcon(Kind kind) {
            this.kind = kind;
        }

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
            return getIconWidth();
        }

        /**
         * Paints the icon.
         *
         * @param component target component
         * @param graphics graphics context
         * @param x x coordinate
         * @param y y coordinate
         */
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(STROKE);
                g.setColor(iconColor(component));
                paintKind(g);
            } finally {
                g.dispose();
            }
        }

        /**
         * Returns the current icon color.
         *
         * @param component target component
         * @return icon color
         */
        private static Color iconColor(Component component) {
            if (component != null && !component.isEnabled()) {
                return Theme.BUTTON_DISABLED_TEXT;
            }
            if (component instanceof AbstractButton button
                    && (button.getModel().isRollover() || button.getModel().isPressed())) {
                return Theme.TEXT;
            }
            return Theme.MUTED;
        }

        /**
         * Paints the selected glyph.
         *
         * @param g graphics context
         */
        private void paintKind(Graphics2D g) {
            switch (kind) {
                case CUSTOMIZE -> paintCustomize(g);
                case SPLIT_RIGHT -> paintSplitRight(g);
                case SPLIT_DOWN -> paintSplitDown(g);
                case RESTORE -> paintRestore(g);
            }
        }

        /**
         * Paints the customize-layout glyph.
         *
         * @param g graphics context
         */
        private static void paintCustomize(Graphics2D g) {
            g.drawRoundRect(1, 1, 14, 14, 3, 3);
            g.drawRoundRect(4, 4, 3, 3, 1, 1);
            g.drawRoundRect(4, 9, 3, 3, 1, 1);
            g.drawLine(9, 4, 12, 4);
            g.drawLine(9, 8, 12, 8);
            g.drawLine(9, 12, 12, 12);
        }

        /**
         * Paints the split-right glyph.
         *
         * @param g graphics context
         */
        private static void paintSplitRight(Graphics2D g) {
            g.drawRoundRect(2, 2, 12, 12, 2, 2);
            g.drawLine(9, 2, 9, 14);
        }

        /**
         * Paints the split-down glyph.
         *
         * @param g graphics context
         */
        private static void paintSplitDown(Graphics2D g) {
            g.drawRoundRect(2, 2, 12, 12, 2, 2);
            g.drawLine(2, 9, 14, 9);
        }

        /**
         * Paints the restore-tabs glyph.
         *
         * @param g graphics context
         */
        private static void paintRestore(Graphics2D g) {
            g.drawRoundRect(5, 3, 8, 8, 2, 2);
            g.drawRoundRect(3, 6, 8, 7, 2, 2);
        }

        /**
         * Supported layout icon kinds.
         */
        enum Kind {
            /**
             * Customize layout menu.
             */
            CUSTOMIZE,

            /**
             * Split editor right.
             */
            SPLIT_RIGHT,

            /**
             * Split editor down.
             */
            SPLIT_DOWN,

            /**
             * Restore hidden tabs.
             */
            RESTORE
        }
    }
}
