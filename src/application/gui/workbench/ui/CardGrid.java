package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * Responsive masonry panel. It flows its child cards into as many equal-width
 * columns as the available width allows — each column at least
 * {@code minColumnWidth} wide — and drops each next card into the currently
 * shortest column so cards of differing heights pack tightly instead of leaving
 * a ragged gap beside a tall neighbour. The grid reflows from one to several
 * columns as the window resizes, and tracks the viewport width so it never
 * overflows horizontally inside a {@link javax.swing.JScrollPane}.
 *
 * <p>This is the single shared primitive behind the dashboard's card layout and
 * any other surface that should use the full desktop canvas rather than a
 * narrow centred column.</p>
 */
public final class CardGrid extends JPanel implements Scrollable {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Minimum width of a single column before the grid drops to fewer columns.
     */
    private final int minColumnWidth;

    /**
     * Gap, in pixels, between columns and between stacked cards.
     */
    private final int gap;

    /**
     * Creates a masonry grid.
     *
     * @param minColumnWidth minimum column width before the grid reflows
     * @param gap gap between columns and stacked cards
     */
    public CardGrid(int minColumnWidth, int gap) {
        super(null);
        this.minColumnWidth = Math.max(80, minColumnWidth);
        this.gap = Math.max(0, gap);
        setOpaque(false);
    }

    /**
     * Returns the column count for an available width and child count.
     *
     * @param availableWidth usable inner width
     * @param children number of visible children
     * @return column count, at least one and never more than the child count
     */
    private int columnCount(int availableWidth, int children) {
        if (children <= 0 || availableWidth <= 0) {
            return 1;
        }
        int columns = Math.max(1, (availableWidth + gap) / (minColumnWidth + gap));
        return Math.max(1, Math.min(columns, children));
    }

    /**
     * Returns the visible children in their add order.
     *
     * @return visible child components
     */
    private Component[] visibleChildren() {
        int count = 0;
        for (Component child : getComponents()) {
            if (child.isVisible()) {
                count++;
            }
        }
        Component[] out = new Component[count];
        int index = 0;
        for (Component child : getComponents()) {
            if (child.isVisible()) {
                out[index++] = child;
            }
        }
        return out;
    }

    /**
     * Returns the index of the shortest column.
     *
     * @param columnBottoms running bottom y of each column
     * @return index of the column with the smallest bottom y
     */
    private static int shortestColumn(int[] columnBottoms) {
        int best = 0;
        for (int i = 1; i < columnBottoms.length; i++) {
            if (columnBottoms[i] < columnBottoms[best]) {
                best = i;
            }
        }
        return best;
    }

    /**
     * Returns the width this grid should lay out against, preferring its own
     * realised width and falling back to the parent's so the first layout pass
     * already reflows correctly.
     *
     * @return layout width
     */
    private int layoutWidth() {
        if (getWidth() > 0) {
            return getWidth();
        }
        return getParent() == null ? 0 : getParent().getWidth();
    }

    /**
     * Lays cards into the shortest column, reflowing by available width.
     */
    @Override
    public void doLayout() {
        Insets insets = getInsets();
        int availableWidth = Math.max(0, layoutWidth() - insets.left - insets.right);
        Component[] children = visibleChildren();
        if (children.length == 0 || availableWidth == 0) {
            return;
        }
        int columns = columnCount(availableWidth, children.length);
        int columnWidth = (availableWidth - (columns - 1) * gap) / columns;
        int[] columnBottoms = new int[columns];
        for (int i = 0; i < columns; i++) {
            columnBottoms[i] = insets.top;
        }
        for (Component child : children) {
            int column = shortestColumn(columnBottoms);
            int x = insets.left + column * (columnWidth + gap);
            int height = child.getPreferredSize().height;
            child.setBounds(x, columnBottoms[column], columnWidth, height);
            columnBottoms[column] += height + gap;
        }
    }

    /**
     * Computes the packed content height for the current width.
     *
     * @return total content height including insets
     */
    private int contentHeight() {
        Insets insets = getInsets();
        int availableWidth = Math.max(0, layoutWidth() - insets.left - insets.right);
        Component[] children = visibleChildren();
        if (children.length == 0) {
            return insets.top + insets.bottom;
        }
        int columns = columnCount(availableWidth, children.length);
        int[] columnBottoms = new int[columns];
        for (int i = 0; i < columns; i++) {
            columnBottoms[i] = insets.top;
        }
        for (Component child : children) {
            int column = shortestColumn(columnBottoms);
            columnBottoms[column] += child.getPreferredSize().height + gap;
        }
        int max = insets.top;
        for (int bottom : columnBottoms) {
            max = Math.max(max, bottom);
        }
        return Math.max(insets.top, max - gap) + insets.bottom;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getPreferredSize() {
        Insets insets = getInsets();
        int width = layoutWidth();
        if (width <= 0) {
            width = minColumnWidth + insets.left + insets.right;
        }
        return new Dimension(width, contentHeight());
    }

    /**
     * Mirrors the preferred size so a parent layout that compresses oversized
     * content (GridBag, BoxLayout) never crushes the grid's masonry row to
     * nothing — the surrounding scroll pane scrolls instead.
     *
     * @return the preferred size
     */
    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    // ------------------------------------------------------------------
    // Scrollable — fill the viewport width, scroll vertically.
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return ScrollableSupport.preferredViewportSize(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return ScrollableSupport.DEFAULT_UNIT_INCREMENT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return ScrollableSupport.verticalBlockIncrement(visibleRect,
                ScrollableSupport.DEFAULT_UNIT_INCREMENT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
