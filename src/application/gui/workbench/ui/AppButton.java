package application.gui.workbench.ui;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import java.awt.event.ActionListener;
import javax.swing.JButton;

/**
 * Factory for the shared Workbench action button primitive.
 */
final class AppButton {

    /**
     * Prevents instantiation.
     */
    private AppButton() {
        // utility
    }

    /**
     * Creates a button with the requested hierarchy variant.
     *
     * @param text visible button text
     * @param variant action hierarchy variant
     * @param listener optional action listener
     * @return styled button
     */
    public static JButton create(String text, Theme.ButtonVariant variant, ActionListener listener) {
        JButton button = new StyledButton(text);
        Theme.button(button, variant);
        Tooltip.attach(button, text);
        if (listener != null) {
            button.addActionListener(listener);
        }
        button.addActionListener(event -> SoundService.play(SoundCue.UI_CLICK));
        return button;
    }

    /**
     * Creates a primary button.
     *
     * @param text visible button text
     * @param listener optional action listener
     * @return styled button
     */
    public static JButton primary(String text, ActionListener listener) {
        return create(text, Theme.ButtonVariant.PRIMARY, listener);
    }

    /**
     * Creates a secondary button.
     *
     * @param text visible button text
     * @param listener optional action listener
     * @return styled button
     */
    public static JButton secondary(String text, ActionListener listener) {
        return create(text, Theme.ButtonVariant.SECONDARY, listener);
    }

    /**
     * Creates a ghost button.
     *
     * @param text visible button text
     * @param listener optional action listener
     * @return styled button
     */
    public static JButton ghost(String text, ActionListener listener) {
        return create(text, Theme.ButtonVariant.GHOST, listener);
    }

    /**
     * Creates a destructive button.
     *
     * @param text visible button text
     * @param listener optional action listener
     * @return styled button
     */
    public static JButton destructive(String text, ActionListener listener) {
        return create(text, Theme.ButtonVariant.DESTRUCTIVE, listener);
    }
}
