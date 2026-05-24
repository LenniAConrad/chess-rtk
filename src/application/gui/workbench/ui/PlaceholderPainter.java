package application.gui.workbench.ui;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.text.JTextComponent;

/**
 * Shared placeholder painter for styled text fields and text areas.
 */
final class PlaceholderPainter {

    /**
     * Utility class; prevents instantiation.
     */
    private PlaceholderPainter() {
        // utility
    }

    /**
     * Paints placeholder copy for an empty text component.
     *
     * @param graphics graphics
     * @param component text component
     * @param verticalCenter true to center vertically
     */
    static void paint(Graphics graphics, JTextComponent component, boolean verticalCenter) {
        if (component == null || !component.getText().isEmpty()) {
            return;
        }
        Object value = component.getClientProperty(Theme.PLACEHOLDER_PROPERTY);
        if (!(value instanceof String placeholder) || placeholder.isBlank()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setFont(component.getFont());
            g.setColor(Theme.withAlpha(Theme.MUTED, component.isEnabled() ? 150 : 110));
            FontMetrics metrics = g.getFontMetrics();
            Insets insets = component.getInsets();
            int x = insets.left + 2;
            int y = verticalCenter
                    ? Math.max(insets.top + metrics.getAscent(),
                            (component.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent())
                    : insets.top + metrics.getAscent() + 1;
            g.drawString(placeholder, x, y);
        } finally {
            g.dispose();
        }
    }
}
