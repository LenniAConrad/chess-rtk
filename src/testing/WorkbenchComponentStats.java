package testing;

import java.awt.Component;
import java.awt.Container;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JComponent;

/**
 * Collects summary statistics for a Swing component tree.
 */
final class WorkbenchComponentStats {

    /**
     * Total components.
     */
    private int total;

    /**
     * Maximum depth.
     */
    private int maxDepth;

    /**
     * Visible component count.
     */
    private int visible;

    /**
     * Showing component count.
     */
    private int showing;

    /**
     * Displayable component count.
     */
    private int displayable;

    /**
     * Enabled component count.
     */
    private int enabled;

    /**
     * Focusable component count.
     */
    private int focusable;

    /**
     * Opaque Swing component count.
     */
    private int opaque;

    /**
     * Component count grouped by concrete class.
     */
    private final Map<String, Integer> byClass = new TreeMap<>();

    /**
     * Creates an empty statistics accumulator.
     */
    private WorkbenchComponentStats() {
        // use collect(Component)
    }

    /**
     * Collects statistics for a component tree.
     *
     * @param root root component
     * @return populated statistics
     */
    static WorkbenchComponentStats collect(Component root) {
        WorkbenchComponentStats stats = new WorkbenchComponentStats();
        stats.collect(root, 0);
        return stats;
    }

    /**
     * Collects one component and all descendants.
     *
     * @param component component to inspect
     * @param depth component depth
     */
    private void collect(Component component, int depth) {
        if (component == null) {
            return;
        }
        total++;
        maxDepth = Math.max(maxDepth, depth);
        if (component.isVisible()) {
            visible++;
        }
        if (component.isShowing()) {
            showing++;
        }
        if (component.isDisplayable()) {
            displayable++;
        }
        if (component.isEnabled()) {
            enabled++;
        }
        if (component.isFocusable()) {
            focusable++;
        }
        if (component instanceof JComponent swing && swing.isOpaque()) {
            opaque++;
        }
        byClass.merge(component.getClass().getName(), 1, Integer::sum);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, depth + 1);
            }
        }
    }

    /**
     * Returns the total component count.
     *
     * @return total components
     */
    int total() {
        return total;
    }

    /**
     * Returns the maximum component depth.
     *
     * @return maximum depth
     */
    int maxDepth() {
        return maxDepth;
    }

    /**
     * Returns the visible component count.
     *
     * @return visible components
     */
    int visible() {
        return visible;
    }

    /**
     * Returns the showing component count.
     *
     * @return showing components
     */
    int showing() {
        return showing;
    }

    /**
     * Returns the displayable component count.
     *
     * @return displayable components
     */
    int displayable() {
        return displayable;
    }

    /**
     * Returns the enabled component count.
     *
     * @return enabled components
     */
    int enabled() {
        return enabled;
    }

    /**
     * Returns the focusable component count.
     *
     * @return focusable components
     */
    int focusable() {
        return focusable;
    }

    /**
     * Returns the opaque Swing component count.
     *
     * @return opaque Swing components
     */
    int opaque() {
        return opaque;
    }

    /**
     * Returns component counts by concrete class name.
     *
     * @return class counts
     */
    Map<String, Integer> byClass() {
        return byClass;
    }
}
