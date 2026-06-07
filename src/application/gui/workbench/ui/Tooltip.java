package application.gui.workbench.ui;

import javax.swing.JComponent;

/**
 * Small tooltip helper used by icon-only controls and compact code previews.
 */
public final class Tooltip {

    /**
     * Prevents instantiation.
     */
    private Tooltip() {
        // utility
    }

    /**
     * Attaches tooltip text when the text is non-blank.
     *
     * @param <T> component type
     * @param component target component
     * @param text tooltip text
     * @return the same component
     */
    public static <T extends JComponent> T attach(T component, String text) {
        if (component != null && text != null && !text.isBlank()) {
            component.setToolTipText(text);
        }
        return component;
    }

    /**
     * Attaches mandatory tooltip text.
     *
     * @param <T> component type
     * @param component target component
     * @param text tooltip text
     * @return the same component
     */
    public static <T extends JComponent> T require(T component, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("icon-only controls require tooltip text");
        }
        return attach(component, text);
    }
}
