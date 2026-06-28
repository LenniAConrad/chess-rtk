package application.gui.workbench.ui;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionListener;
import javax.swing.JButton;

/**
 * Factory for compact icon-only Workbench buttons.
 */
final class IconButton {

    /**
     * Prevents instantiation.
     */
    private IconButton() {
        // utility
    }

    /**
     * Creates an icon-only button with mandatory tooltip/accessibility text.
     *
     * @param label tooltip and accessible label
     * @param listener optional action listener
     * @return styled icon-only button
     */
    public static JButton create(String label, ActionListener listener) {
        JButton button = style(AppButton.create(label, Theme.ButtonVariant.SECONDARY, listener), label);
        button.setText("");
        return button;
    }

    /**
     * Creates a compact transport/utility button that keeps a visible glyph but
     * carries a separate descriptive tooltip and accessible name. Use this for
     * symbol controls such as {@code ⏮ ◀ ▶ ⏭} where the glyph is the affordance
     * but the screen-reader/tooltip text must be a real word.
     *
     * @param glyph visible glyph rendered on the button
     * @param accessibleLabel tooltip and accessible label
     * @param listener optional action listener
     * @return styled compact button showing the glyph
     */
    public static JButton create(String glyph, String accessibleLabel, ActionListener listener) {
        return style(AppButton.create(glyph, Theme.ButtonVariant.SECONDARY, listener), accessibleLabel);
    }

    /**
     * Applies shared compact icon-button chrome and the mandatory
     * tooltip/accessible label.
     *
     * @param button button to style
     * @param accessibleLabel tooltip and accessible label
     * @return the styled button
     */
    private static JButton style(JButton button, String accessibleLabel) {
        Tooltip.require(button, accessibleLabel);
        button.getAccessibleContext().setAccessibleName(accessibleLabel);
        button.putClientProperty(Theme.CLIENT_ICON_ONLY, Boolean.TRUE);
        button.setMargin(new Insets(6, 8, 6, 8));
        button.setBorder(Theme.pad(5, 7, 5, 7));
        Dimension size = new Dimension(34, Theme.CONTROL_HEIGHT);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        return button;
    }
}
