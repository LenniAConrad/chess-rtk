package application.gui.workbench.ui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;

/**
 * Builds and paints shared Workbench empty-state treatments.
 */
final class EmptyStateRenderer {

    /**
     * Prevents instantiation.
     */
    private EmptyStateRenderer() {
        // utility
    }

    /**
     * Builds a centered empty-state block.
     *
     * @param title short title, e.g. "No dataset loaded"
     * @param hint one-line explanation of how to proceed
     * @param actions optional action buttons shown beneath the hint
     * @return centered empty-state component
     */
    static JComponent component(String title, String hint, JButton... actions) {
        return new EmptyState(title, hint, actions);
    }

    /**
     * Paints a centered empty-state directly onto a graphics context.
     *
     * @param graphics graphics context
     * @param bounds area to center within
     * @param title short title
     * @param hint one-line hint, or {@code null}
     */
    static void paint(Graphics2D graphics, Rectangle bounds, String title, String hint) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        Graphics2D scratch = (Graphics2D) graphics.create();
        try {
            scratch.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            String titleText = title == null ? "" : title;
            Font titleFont = Theme.font(Theme.FONT_SECTION_TITLE, Font.BOLD);
            Font hintFont = Theme.font(Theme.FONT_METADATA, Font.PLAIN);
            FontMetrics titleMetrics = scratch.getFontMetrics(titleFont);
            FontMetrics hintMetrics = scratch.getFontMetrics(hintFont);
            boolean hasHint = hint != null && !hint.isBlank();
            int blockHeight = titleMetrics.getHeight() + (hasHint ? hintMetrics.getHeight() + Theme.SPACE_XS : 0);
            int top = bounds.y + (bounds.height - blockHeight) / 2 + titleMetrics.getAscent();
            scratch.setFont(titleFont);
            scratch.setColor(Theme.TEXT);
            int titleX = bounds.x + (bounds.width - titleMetrics.stringWidth(titleText)) / 2;
            scratch.drawString(titleText, titleX, top);
            if (hasHint) {
                scratch.setFont(hintFont);
                scratch.setColor(Theme.MUTED);
                int hintX = bounds.x + (bounds.width - hintMetrics.stringWidth(hint)) / 2;
                scratch.drawString(hint, hintX, top + Theme.SPACE_XS + hintMetrics.getHeight());
            }
        } finally {
            scratch.dispose();
        }
    }
}
