package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.Locale;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;

/**
 * Component-level style helpers behind the stable {@link Theme} facade.
 */
final class ThemeComponents {

    /**
     * Prevents instantiation.
     */
    private ThemeComponents() {
        // utility
    }

    /**
     * Applies the standard transparent panel background and inner spacing.
     *
     * @param component Swing component
     */
    static void stylePanel(JComponent component) {
        component.setOpaque(false);
        component.setBackground(Theme.PANEL);
        component.setForeground(Theme.TEXT);
        component.setBorder(Theme.pad(Theme.SPACE_MD));
    }

    /**
     * Styles a text field with the workbench input chrome.
     *
     * @param field text field to style
     */
    static void field(JTextField field) {
        field.setUI(new PlaceholderTextFieldUI());
        field.setOpaque(true);
        field.setBackground(Theme.INPUT);
        field.setForeground(Theme.TEXT);
        field.setDisabledTextColor(Theme.MUTED);
        field.setCaretColor(Theme.ACCENT);
        field.setSelectionColor(Theme.TEXT_SELECTION);
        field.setSelectedTextColor(Theme.TEXT);
        field.setBorder(inputBorder(false));
        field.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        installFocusBorder(field);
        installEnabledBackground(field, Theme.INPUT);
    }

    /**
     * Styles a text area with the workbench input chrome.
     *
     * @param area text area to style
     */
    static void area(JTextArea area) {
        area.setUI(new PlaceholderTextAreaUI());
        area.setOpaque(true);
        area.setBackground(Theme.TEXT_AREA);
        area.setForeground(Theme.TEXT);
        area.setDisabledTextColor(Theme.MUTED);
        area.setCaretColor(Theme.ACCENT);
        area.setSelectionColor(Theme.TEXT_SELECTION);
        area.setSelectedTextColor(Theme.TEXT);
        area.setBorder(inputBorder(false));
        area.setFont(Theme.mono(Theme.FONT_MONO));
        installFocusBorder(area);
        installEnabledBackground(area, Theme.TEXT_AREA);
    }

    /**
     * Styles a text area as a code block.
     *
     * @param area text area to style
     */
    static void codeBlock(JTextArea area) {
        area.putClientProperty(Theme.CLIENT_CODE_BLOCK, Boolean.TRUE);
        area.setOpaque(true);
        area.setBackground(Theme.CODE_BLOCK_BG);
        area.setForeground(Theme.CODE_BLOCK_TEXT);
        area.setCaretColor(Theme.CODE_BLOCK_TEXT);
        area.setSelectionColor(Theme.TEXT_SELECTION);
        area.setSelectedTextColor(Theme.CODE_BLOCK_TEXT);
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CODE_BLOCK_BORDER),
                Theme.pad(Theme.SPACE_SM)));
        area.setFont(Theme.mono(Theme.FONT_MONO));
        installFocusBorder(area);
        installEnabledBackground(area, Theme.CODE_BLOCK_BG);
    }

    /**
     * Stores placeholder text and refreshes the text component.
     *
     * @param component Swing component
     * @param text placeholder text
     */
    static void placeholder(JTextComponent component, String text) {
        String value = text == null ? "" : text;
        component.putClientProperty(Theme.PLACEHOLDER_PROPERTY, value);
        if (component.getToolTipText() == null || component.getToolTipText().isBlank()) {
            component.setToolTipText(value);
        }
        component.repaint();
    }

    /**
     * Styles a text area as terminal output.
     *
     * @param area text area to style
     */
    static void styleTerminal(JTextArea area) {
        area.setOpaque(true);
        area.setBackground(Theme.TERMINAL);
        area.setForeground(Theme.TERMINAL_TEXT);
        area.setCaretColor(Theme.TERMINAL_TEXT);
        area.setSelectionColor(Theme.TEXT_SELECTION);
        area.setSelectedTextColor(Theme.TERMINAL_TEXT);
        area.setBorder(inputBorder(false));
        area.setFont(Theme.mono(Theme.FONT_MONO));
        installFocusBorder(area);
    }

    /**
     * Styles a button for the requested variant.
     *
     * @param button button to style
     * @param primary whether primary
     */
    static void button(AbstractButton button, boolean primary) {
        button(button, primary ? Theme.ButtonVariant.PRIMARY : Theme.ButtonVariant.SECONDARY);
    }

    /**
     * Styles a button for the requested variant.
     *
     * @param button button to style
     * @param variant requested visual variant
     */
    static void button(AbstractButton button, Theme.ButtonVariant variant) {
        Theme.ButtonVariant requested = buttonVariant(variant);
        Theme.ButtonVariant resolved = requested == Theme.ButtonVariant.PRIMARY
                ? requested
                : destructiveActionLabel(button == null ? null : button.getText())
                        ? Theme.ButtonVariant.DESTRUCTIVE
                        : requested;
        button.putClientProperty(Theme.CLIENT_BUTTON_VARIANT, resolved);
        button.putClientProperty(Theme.CLIENT_PRIMARY, Boolean.valueOf(resolved == Theme.ButtonVariant.PRIMARY));
        if (resolved == Theme.ButtonVariant.DESTRUCTIVE) {
            button.putClientProperty(Theme.CLIENT_ICON_KIND, SvgIcon.Kind.DESTRUCTIVE);
        } else if (!Boolean.TRUE.equals(button.getClientProperty(Theme.CLIENT_ICON_ONLY))) {
            button.putClientProperty(Theme.CLIENT_ICON_KIND, null);
        }
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setIcon(SvgIcon.forButton(button, resolved));
        button.setIconTextGap(resolved == Theme.ButtonVariant.DESTRUCTIVE ? 5 : 6);
        button.setMargin(resolved == Theme.ButtonVariant.DESTRUCTIVE
                ? new Insets(6, 9, 6, 10)
                : new Insets(6, 11, 6, 11));
        button.setFont(Theme.font(Theme.FONT_CONTROL,
                resolved == Theme.ButtonVariant.PRIMARY ? Font.BOLD : Font.PLAIN));
        button.setBackground(buttonBackground(resolved));
        button.setForeground(buttonText(resolved));
        button.setDisabledIcon(SvgIcon.disabledForButton(button));
        button.setBorder(Theme.pad(5, 8, 5, 8));
    }

    /**
     * Returns whether a command label should use destructive button styling.
     *
     * @param text command label
     * @return true when the label names a destructive action
     */
    static boolean destructiveActionLabel(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("stop")
                || normalized.startsWith("stop ")
                || normalized.equals("resign")
                || normalized.startsWith("resign ")
                || normalized.equals("clear")
                || normalized.startsWith("clear ")
                || normalized.equals("delete")
                || normalized.startsWith("delete ")
                || normalized.equals("remove")
                || normalized.startsWith("remove ")
                || normalized.equals("abort")
                || normalized.startsWith("abort ")
                || normalized.equals("kill")
                || normalized.startsWith("kill ")
                || normalized.equals("wipe")
                || normalized.startsWith("wipe ")
                || normalized.equals("cancel scan")
                || normalized.equals("cancel search")
                || normalized.equals("cancel run")
                || normalized.equals("cancel command")
                || normalized.equals("cancel job")
                || normalized.equals("cancel gauntlet");
    }

    /**
     * Returns the normal fill color for a button variant.
     *
     * @param variant requested button variant
     * @return fill color
     */
    static Color buttonBackground(Theme.ButtonVariant variant) {
        return switch (buttonVariant(variant)) {
            case PRIMARY -> Theme.ACCENT;
            case SECONDARY -> Theme.SECONDARY_BUTTON;
            case GHOST -> Theme.GHOST_BUTTON;
            case DESTRUCTIVE -> Theme.DESTRUCTIVE_BUTTON;
        };
    }

    /**
     * Returns the hover fill color for a button variant.
     *
     * @param variant requested button variant
     * @return hover fill color
     */
    static Color buttonHover(Theme.ButtonVariant variant) {
        return switch (buttonVariant(variant)) {
            case PRIMARY -> Theme.ACCENT_HOVER;
            case SECONDARY -> Theme.SECONDARY_BUTTON_HOVER;
            case GHOST -> Theme.GHOST_BUTTON_HOVER;
            case DESTRUCTIVE -> Theme.DESTRUCTIVE_BUTTON_HOVER;
        };
    }

    /**
     * Returns the pressed fill color for a button variant.
     *
     * @param variant requested button variant
     * @return pressed fill color
     */
    static Color buttonPressed(Theme.ButtonVariant variant) {
        return switch (buttonVariant(variant)) {
            case PRIMARY -> Theme.ACCENT_PRESSED;
            case SECONDARY -> Theme.SECONDARY_BUTTON_PRESSED;
            case GHOST -> Theme.GHOST_BUTTON_PRESSED;
            case DESTRUCTIVE -> Theme.DESTRUCTIVE_BUTTON_PRESSED;
        };
    }

    /**
     * Returns the border color for a button variant.
     *
     * @param variant requested button variant
     * @return border color
     */
    static Color buttonBorder(Theme.ButtonVariant variant) {
        return switch (buttonVariant(variant)) {
            case PRIMARY -> Theme.ACCENT_PRESSED;
            case SECONDARY -> Theme.INPUT_BORDER;
            case GHOST -> Theme.withAlpha(Theme.INPUT_BORDER, Theme.isDark() ? 90 : 72);
            case DESTRUCTIVE -> Theme.DESTRUCTIVE_BUTTON_PRESSED;
        };
    }

    /**
     * Returns the foreground color for a button variant.
     *
     * @param variant requested button variant
     * @return text color
     */
    static Color buttonText(Theme.ButtonVariant variant) {
        return switch (buttonVariant(variant)) {
            case PRIMARY -> Theme.PRIMARY_BUTTON_TEXT;
            case SECONDARY -> Theme.SECONDARY_BUTTON_TEXT;
            case GHOST -> Theme.GHOST_BUTTON_TEXT;
            case DESTRUCTIVE -> Theme.DESTRUCTIVE_BUTTON_TEXT;
        };
    }

    /**
     * Returns the resolved variant stored on a button.
     *
     * @param button button to inspect
     * @return stored variant, or secondary when unset
     */
    static Theme.ButtonVariant buttonVariant(AbstractButton button) {
        if (button != null
                && button.getClientProperty(Theme.CLIENT_BUTTON_VARIANT) instanceof Theme.ButtonVariant variant) {
            return variant;
        }
        if (button != null && Boolean.TRUE.equals(button.getClientProperty(Theme.CLIENT_PRIMARY))) {
            return Theme.ButtonVariant.PRIMARY;
        }
        return Theme.ButtonVariant.SECONDARY;
    }

    /**
     * Styles a list with the workbench table colors.
     *
     * @param list list to style
     */
    static void list(JList<?> list) {
        list.setOpaque(true);
        list.setBackground(Theme.ELEVATED_SOLID);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.SELECTION_SOLID);
        list.setSelectionForeground(Theme.TEXT);
        list.setFont(Theme.mono(12));
        list.setFixedCellHeight(23);
    }

    /**
     * Creates a small section label.
     *
     * @param text label text
     * @return styled section label
     */
    static JLabel section(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        label.setFont(Theme.font(Theme.FONT_SECTION_TITLE, Font.BOLD));
        label.setBorder(Theme.pad(0, 0, 4, 0));
        return label;
    }

    /**
     * Creates a bold section title label.
     *
     * @param text label text
     * @return styled section title
     */
    static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        label.setFont(Theme.font(Theme.FONT_TITLE, Font.BOLD));
        return label;
    }

    /**
     * Creates a compact card header row.
     *
     * @param title header title
     * @param trailing optional trailing control
     * @return header component
     */
    static JComponent cardHeader(String title, JComponent trailing) {
        JPanel row = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.setOpaque(false);
        row.add(section(title), BorderLayout.WEST);
        if (trailing != null) {
            trailing.setOpaque(false);
            row.add(trailing, BorderLayout.EAST);
        }
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /**
     * Styles a command tab and wires selection-state repainting.
     *
     * @param tab command tab button
     */
    static void commandTab(AbstractButton tab) {
        tab.putClientProperty(Theme.CLIENT_COMMAND_TAB, Boolean.TRUE);
        tab.setFocusPainted(false);
        tab.setContentAreaFilled(false);
        tab.setBorderPainted(false);
        tab.setOpaque(true);
        tab.setBorder(Theme.pad(5, 12, 5, 12));
        reserveCommandTabSize(tab);
        applyCommandTabState(tab);
        tab.addItemListener(event -> applyCommandTabState(tab));
    }

    /**
     * Applies selected or unselected colors and font weight to a command tab.
     *
     * @param tab command tab button
     */
    static void applyCommandTabState(AbstractButton tab) {
        boolean on = tab.isSelected();
        tab.setBackground(on ? Theme.SELECTION_SOLID : Theme.ELEVATED_SOLID);
        tab.setForeground(on ? Theme.STATUS_INFO_TEXT : Theme.MUTED);
        tab.setFont(Theme.font(12, on ? Font.BOLD : Font.PLAIN));
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(on ? Theme.ACCENT : Theme.LINE),
                Theme.pad(4, 11, 4, 11)));
    }

    /**
     * Returns the button variant.
     *
     * @param variant layout or network variant
     * @return button variant
     */
    private static Theme.ButtonVariant buttonVariant(Theme.ButtonVariant variant) {
        return variant == null ? Theme.ButtonVariant.SECONDARY : variant;
    }

    /**
     * Pins command tab dimensions to prevent selection jitter.
     *
     * @param tab command tab button
     */
    private static void reserveCommandTabSize(AbstractButton tab) {
        Font previousFont = tab.getFont();
        Border previousBorder = tab.getBorder();
        tab.setFont(Theme.font(12, Font.BOLD));
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT),
                Theme.pad(4, 11, 4, 11)));
        Dimension preferred = tab.getPreferredSize();
        tab.setPreferredSize(preferred);
        tab.setMinimumSize(preferred);
        tab.setFont(previousFont);
        tab.setBorder(previousBorder);
    }

    /**
     * Returns the input border.
     *
     * @param focused whether focused
     * @return input border
     */
    private static Border inputBorder(boolean focused) {
        return InputChrome.border(focused, false, false);
    }

    /**
     * Installs focus border.
     *
     * @param component Swing component
     */
    private static void installFocusBorder(JComponent component) {
        InputChrome.install(component, false);
    }

    /**
     * Installs enabled background.
     *
     * @param component Swing component
     * @param enabledBackground whether to enable d background
     */
    private static void installEnabledBackground(JTextComponent component, Color enabledBackground) {
        InputChrome.installEnabledBackground(component, enabledBackground);
    }
}
