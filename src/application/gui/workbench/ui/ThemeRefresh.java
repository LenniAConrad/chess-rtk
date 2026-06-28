package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JFormattedTextField;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;

/**
 * Existing-component palette refresh logic for {@link Theme}.
 */
final class ThemeRefresh {

    /**
     * Prevents instantiation.
     */
    private ThemeRefresh() {
        // utility
    }

    /**
     * Refreshes the refresh component tree.
     *
     * @param component Swing component
     */
    static void refreshComponentTree(Component component) {
        if (component == null) {
            return;
        }
        refreshComponent(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                refreshComponentTree(child);
            }
        }
        component.repaint();
    }

    /**
     * Refreshes the refresh foreground.
     *
     * @param component Swing component
     */
    static void refreshForeground(JComponent component) {
        if (component == null) {
            return;
        }
        Object role = component.getClientProperty(Theme.FOREGROUND_ROLE_PROPERTY);
        if (role instanceof Theme.ForegroundRole foregroundRole) {
            component.setForeground(Theme.foregroundColor(foregroundRole));
            return;
        }
        component.setForeground(Theme.foregroundColor(inferForegroundRole(component.getForeground())));
    }

    /**
     * Refreshes the refresh component.
     *
     * @param component Swing component
     */
    private static void refreshComponent(Component component) {
        if (component instanceof JComponent jComponent) {
            refreshBorder(jComponent);
        }
        if (component instanceof BackdropPanel) {
            component.setBackground(Theme.BG);
        } else if (component instanceof SurfacePanel surface) {
            component.setBackground(SurfacePanel.surfaceColor(surface.surfaceRole()));
            component.setForeground(Theme.TEXT);
        } else if (component instanceof Theme.ConsoleLike console) {
            console.applyConsoleTheme();
        } else if (component instanceof JTextArea area) {
            if (Boolean.TRUE.equals(area.getClientProperty(Theme.CLIENT_CODE_BLOCK))) {
                Theme.codeBlock(area);
            } else if (Boolean.TRUE.equals(area.getClientProperty(Theme.CLIENT_TRANSPARENT_FIELD))) {
                area.setForeground(Theme.TEXT);
                area.setCaretColor(Theme.MUTED);
                area.setSelectionColor(Theme.SELECTION_SOLID);
            } else {
                Theme.area(area);
            }
        } else if (component instanceof JFormattedTextField field
                && field.getParent() instanceof JSpinner.DefaultEditor editor) {
            Ui.styleSpinnerEditor(editor);
        } else if (component instanceof JTextField field) {
            Theme.field(field);
        } else if (component instanceof JTextPane pane) {
            pane.setBackground(Theme.TEXT_AREA);
            pane.setForeground(Theme.TEXT);
            pane.setCaretColor(Theme.TEXT);
            pane.setSelectionColor(Theme.TEXT_SELECTION);
            pane.setSelectedTextColor(Theme.TEXT);
        } else if (component instanceof JComboBox<?> combo) {
            Ui.styleCombo(combo);
        } else if (component instanceof JSlider slider) {
            Ui.styleSlider(slider);
        } else if (component instanceof JSpinner spinner) {
            Ui.styleSpinner(spinner);
        } else if (component instanceof JTable table) {
            Theme.table(table, Math.max(24, table.getRowHeight()));
        } else if (component instanceof JList<?> list) {
            Theme.list(list);
        } else if (component instanceof JTree tree) {
            Ui.styleTree(tree);
        } else if (component instanceof JTabbedPane tabs) {
            Ui.styleTabs(tabs);
        } else if (component instanceof JScrollPane pane) {
            Ui.refreshScrollPaneTheme(pane);
        } else if (component instanceof ToggleBox toggle) {
            toggle.setForeground(Theme.TEXT);
            toggle.setFont(Theme.font(13, Font.PLAIN));
        } else if (component instanceof JMenuBar menuBar) {
            menuBar.setOpaque(true);
            menuBar.setBackground(Theme.BG);
            menuBar.setForeground(Theme.TEXT);
            menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
            menuBar.setFont(Theme.font(12, Font.PLAIN));
        } else if (component instanceof JMenuItem item) {
            item.setOpaque(true);
            item.setBackground(item.getParent() instanceof JMenuBar ? Theme.BG : Theme.PANEL_SOLID);
            item.setForeground(Theme.TEXT);
            item.setFont(Theme.font(12, Font.PLAIN));
            item.setBorder(Theme.pad(5, 10, 5, 10));
        } else if (component instanceof AbstractButton button) {
            refreshButton(button);
        } else if (component instanceof JLabel label) {
            refreshForeground(label);
        } else if (component instanceof JComponent jComponent
                && !(component.getParent() instanceof JScrollPane)) {
            refreshForeground(jComponent);
            if (jComponent.isOpaque()) {
                jComponent.setBackground(Theme.PANEL_SOLID);
            } else {
                jComponent.setBackground(Theme.BG);
            }
        }
    }

    /**
     * Refreshes the refresh button.
     *
     * @param button button component
     */
    private static void refreshButton(AbstractButton button) {
        if (Boolean.TRUE.equals(button.getClientProperty(Theme.CLIENT_COMMAND_TAB))) {
            ThemeComponents.applyCommandTabState(button);
        } else if (button.getClientProperty(Theme.CLIENT_BUTTON_VARIANT) instanceof Theme.ButtonVariant variant) {
            Theme.button(button, variant);
        } else if (button.getClientProperty(Theme.CLIENT_PRIMARY) instanceof Boolean value) {
            Theme.button(button, value.booleanValue());
        } else {
            button.setForeground(Theme.TEXT);
            button.setFont(Theme.font(13, Font.PLAIN));
        }
    }

    /**
     * Refreshes the refresh border.
     *
     * @param component Swing component
     */
    private static void refreshBorder(JComponent component) {
        Border border = component.getBorder();
        Border refreshed = refreshedBorder(border);
        if (refreshed != border) {
            component.setBorder(refreshed);
        }
    }

    /**
     * Refreshes the refreshed border.
     *
     * @param border border color
     * @return refreshed border
     */
    private static Border refreshedBorder(Border border) {
        if (border instanceof CompoundBorder compound) {
            Border outside = refreshedBorder(compound.getOutsideBorder());
            Border inside = refreshedBorder(compound.getInsideBorder());
            if (outside != compound.getOutsideBorder() || inside != compound.getInsideBorder()) {
                return BorderFactory.createCompoundBorder(outside, inside);
            }
        } else if (border instanceof LineBorder line && isWorkbenchLineColor(line.getLineColor())) {
            return BorderFactory.createLineBorder(currentLineColor(line.getLineColor()),
                    line.getThickness(), line.getRoundedCorners());
        } else if (border instanceof MatteBorder matte && isWorkbenchLineColor(matte.getMatteColor())) {
            Insets insets = matte.getBorderInsets();
            return BorderFactory.createMatteBorder(insets.top, insets.left,
                    insets.bottom, insets.right, currentLineColor(matte.getMatteColor()));
        }
        return border;
    }

    /**
     * Returns whether workbench line color.
     *
     * @param color display color
     * @return true when workbench line color
     */
    private static boolean isWorkbenchLineColor(Color color) {
        if (color == null) {
            return false;
        }
        int rgb = color.getRGB() & 0x00ff_ffff;
        return rgb == (Theme.PASTEL_BORDER.getRGB() & 0x00ff_ffff)
                || rgb == 0xe5e5e5
                || rgb == 0xd4d4d4
                || rgb == 0xdadadf
                || rgb == 0xc6c6cc
                || rgb == 0xe1e5eb
                || rgb == 0xc7cdd7
                || rgb == (Theme.DARK_SUBTLE.getRGB() & 0x00ff_ffff)
                || rgb == (Theme.DARK_BORDER.getRGB() & 0x00ff_ffff)
                || rgb == 0x203449
                || rgb == 0x2a4560
                || rgb == 0x26313b
                || rgb == 0x2d3944
                || rgb == 0x303031
                || rgb == 0x3a3a3c
                || rgb == 0x373737
                || rgb == 0x454545
                || rgb == 0x48484a
                || rgb == 0x2b2b2b
                || rgb == 0x3c3c3c;
    }

    /**
     * Returns the line color.
     *
     * @param source source object
     * @return line color
     */
    private static Color currentLineColor(Color source) {
        if (source == null || source.getAlpha() == Theme.LINE.getAlpha()) {
            return Theme.LINE;
        }
        return new Color(Theme.LINE.getRed(), Theme.LINE.getGreen(), Theme.LINE.getBlue(), source.getAlpha());
    }

    /**
     * Returns the infer foreground role.
     *
     * @param color display color
     * @return infer foreground role
     */
    private static Theme.ForegroundRole inferForegroundRole(Color color) {
        if (sameColor(color, Theme.PASTEL_MUTED) || sameColor(color, Theme.DARK_MUTED)
                || sameRgb(color, 0x8fa1b2) || sameRgb(color, 0x91a5b8)) {
            return Theme.ForegroundRole.MUTED;
        }
        if (sameColor(color, Theme.PASTEL_GREEN_TEXT) || sameColor(color, Theme.DARK_SUCCESS_TEXT)
                || sameColor(color, Theme.STATUS_SUCCESS_TEXT)) {
            return Theme.ForegroundRole.SUCCESS;
        }
        if (sameColor(color, Theme.PASTEL_AMBER_TEXT) || sameColor(color, Theme.DARK_WARNING_TEXT)
                || sameColor(color, Theme.STATUS_WARNING_TEXT)) {
            return Theme.ForegroundRole.WARNING;
        }
        if (sameColor(color, Theme.PASTEL_CORAL_TEXT) || sameColor(color, Theme.DARK_ERROR_TEXT)
                || sameColor(color, Theme.STATUS_ERROR_TEXT)) {
            return Theme.ForegroundRole.ERROR;
        }
        if (sameColor(color, Theme.PASTEL_BLUE_TEXT) || sameColor(color, Theme.DARK_INFO_TEXT)
                || sameRgb(color, 0x66b7ff)
                || sameColor(color, Theme.STATUS_INFO_TEXT)) {
            return Theme.ForegroundRole.INFO;
        }
        if (sameColor(color, Theme.TERMINAL_TEXT)) {
            return Theme.ForegroundRole.TERMINAL;
        }
        return Theme.ForegroundRole.TEXT;
    }

    /**
     * Returns the same color.
     *
     * @param first first item
     * @param second source second
     * @return true when same color
     */
    private static boolean sameColor(Color first, Color second) {
        return first != null && second != null && first.getRGB() == second.getRGB();
    }

    /**
     * Returns whether a color has the given RGB channels.
     *
     * @param color display color
     * @param rgb expected RGB channels
     * @return true when RGB channels match
     */
    private static boolean sameRgb(Color color, int rgb) {
        return color != null && (color.getRGB() & 0x00ff_ffff) == rgb;
    }
}
