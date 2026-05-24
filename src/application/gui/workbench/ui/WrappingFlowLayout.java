package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * Flow layout variant whose preferred height accounts for wrapped rows.
 *
 * <p>Swing's default {@link FlowLayout} can wrap during layout, but its
 * preferred size remains a single row. That makes toolbar controls appear cut
 * off when a parent panel uses the preferred height. This layout reports the
 * wrapped height once the container has a usable width.</p>
 */
public final class WrappingFlowLayout extends FlowLayout {

    /**
     * Serialization identifier for Swing layout compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a left-aligned wrapping flow layout.
     */
    public WrappingFlowLayout() {
        super();
    }

    /**
     * Creates a wrapping flow layout.
     *
     * @param align flow alignment
     * @param hgap horizontal gap
     * @param vgap vertical gap
     */
    public WrappingFlowLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    /**
     * Returns the preferred size using preferred child sizes.
     *
     * @param target container being laid out
     * @return preferred wrapped size
     */
    @Override
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            return layoutSize(target, true);
        }
    }

    /**
     * Returns the minimum size using minimum child sizes.
     *
     * @param target container being laid out
     * @return minimum wrapped size
     */
    @Override
    public Dimension minimumLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Dimension minimum = layoutSize(target, false);
            minimum.width = Math.max(0, minimum.width - getHgap() - 1);
            return minimum;
        }
    }

    /**
     * Computes the layout size for a target container.
     *
     * @param target target container
     * @param preferred true to use preferred child sizes
     * @return computed size
     */
    private Dimension layoutSize(Container target, boolean preferred) {
        int availableWidth = availableWidth(target);
        if (availableWidth <= 0) {
            return preferred ? super.preferredLayoutSize(target) : super.minimumLayoutSize(target);
        }

        Insets insets = target.getInsets();
        int maxWidth = Math.max(0, availableWidth - insets.left - insets.right - getHgap() * 2);
        int rowWidth = 0;
        int rowHeight = 0;
        int width = 0;
        int height = insets.top + insets.bottom + getVgap() * 2;
        for (Component child : target.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            Dimension size = preferred ? child.getPreferredSize() : child.getMinimumSize();
            if (rowWidth > 0 && rowWidth + getHgap() + size.width > maxWidth) {
                width = Math.max(width, rowWidth);
                height += rowHeight + getVgap();
                rowWidth = 0;
                rowHeight = 0;
            }
            if (rowWidth > 0) {
                rowWidth += getHgap();
            }
            rowWidth += size.width;
            rowHeight = Math.max(rowHeight, size.height);
        }
        width = Math.max(width, rowWidth);
        height += rowHeight;
        return new Dimension(width + insets.left + insets.right + getHgap() * 2, height);
    }

    /**
     * Returns the current usable width for preferred-size calculations.
     *
     * @param target target container
     * @return available width, or zero when not known yet
     */
    private static int availableWidth(Container target) {
        int width = target.getWidth();
        if (width > 0) {
            return width;
        }
        Container parent = target.getParent();
        return parent == null ? 0 : parent.getWidth();
    }
}
