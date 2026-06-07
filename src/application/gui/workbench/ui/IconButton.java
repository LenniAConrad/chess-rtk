package application.gui.workbench.ui;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionListener;
import javax.swing.JButton;

/**
 * Factory for compact icon-only Workbench buttons.
 */
public final class IconButton {

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
        JButton button = AppButton.create(label, Theme.ButtonVariant.SECONDARY, listener);
        button.setText("");
        Tooltip.require(button, label);
        button.getAccessibleContext().setAccessibleName(label);
        button.putClientProperty(Theme.CLIENT_ICON_ONLY, Boolean.TRUE);
        button.setMargin(new Insets(6, 8, 6, 8));
        button.setBorder(Theme.pad(5, 7, 5, 7));
        Dimension size = new Dimension(34, Theme.CONTROL_HEIGHT);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        return button;
    }
}
