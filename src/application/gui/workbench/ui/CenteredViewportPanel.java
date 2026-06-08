package application.gui.workbench.ui;

import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JComponent;

/**
 * Scrollable wrapper that centers one child up to a maximum width while still
 * tracking compact viewport widths to avoid horizontal scrolling.
 */
final class CenteredViewportPanel extends ViewportPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Wrapped content component.
     */
    private final JComponent content;

    /**
     * Maximum centered content width.
     */
    private final int maxWidth;

    /**
     * Creates the centered viewport wrapper.
     *
     * @param content content component
     * @param maxWidth maximum centered content width
     */
    CenteredViewportPanel(JComponent content, int maxWidth) {
        super(null);
        this.content = content;
        this.maxWidth = Math.max(320, maxWidth);
        add(content);
    }

    /**
     * Lays out the child at the smaller of the viewport width and the
     * configured content cap.
     */
    @Override
    public void doLayout() {
        Insets insets = getInsets();
        int availableWidth = Math.max(0, getWidth() - insets.left - insets.right);
        int availableHeight = Math.max(0, getHeight() - insets.top - insets.bottom);
        int childWidth = Math.min(maxWidth, availableWidth);
        Dimension preferred = content.getPreferredSize();
        int childHeight = Math.max(availableHeight, preferred.height);
        int childX = insets.left + Math.max(0, (availableWidth - childWidth) / 2);
        content.setBounds(childX, insets.top, childWidth, childHeight);
    }

    /**
     * Returns the preferred wrapper size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        Insets insets = getInsets();
        Dimension preferred = content.getPreferredSize();
        return new Dimension(Math.min(maxWidth, preferred.width) + insets.left + insets.right,
                preferred.height + insets.top + insets.bottom);
    }
}
