package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

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
        JPanel stack = Ui.transparentPanel(null);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title == null ? "" : title);
        Theme.foreground(titleLabel, Theme.ForegroundRole.TEXT);
        titleLabel.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        stack.add(titleLabel);
        if (hint != null && !hint.isBlank()) {
            stack.add(Box.createVerticalStrut(Theme.SPACE_XS));
            JLabel hintLabel = new JLabel(hint);
            Theme.foreground(hintLabel, Theme.ForegroundRole.MUTED);
            hintLabel.setFont(Theme.font(Theme.FONT_CAPTION, Font.PLAIN));
            hintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            stack.add(hintLabel);
        }
        if (actions != null && actions.length > 0) {
            stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
            JPanel row = Ui.transparentPanel(new FlowLayout(FlowLayout.CENTER, Theme.SPACE_SM, 0));
            for (JButton action : actions) {
                if (action != null) {
                    row.add(action);
                }
            }
            row.setAlignmentX(Component.CENTER_ALIGNMENT);
            stack.add(row);
        }
        JPanel center = Ui.transparentPanel(new GridBagLayout());
        center.add(stack, new GridBagConstraints());
        return center;
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
            Font titleFont = Theme.font(Theme.FONT_BODY, Font.BOLD);
            Font hintFont = Theme.font(Theme.FONT_CAPTION, Font.PLAIN);
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
