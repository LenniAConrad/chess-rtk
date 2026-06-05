package application.gui.workbench.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered registry of top-level {@link RegisteredView}s.
 *
 * <p>The shell builds one registry, registers each surface, then hands the list
 * to {@link EditorSplitArea#addView(RegisteredView)} in order — so the workbench
 * tab set is a single data structure rather than a hard-coded sequence of
 * {@code addPanel} calls. Keeping it as data is what lets later work drive the
 * "+" new-tab menu and command palette from the same source, and reorder or
 * gate surfaces without touching layout code.</p>
 */
public final class ViewRegistry {

    /**
     * Registered views, in insertion (display) order.
     */
    private final List<RegisteredView> views = new ArrayList<>();

    /**
     * Registers a view at the end of the strip.
     *
     * @param view view to register
     * @return this registry, for chaining
     */
    public ViewRegistry add(RegisteredView view) {
        if (view != null) {
            views.add(view);
        }
        return this;
    }

    /**
     * Returns the registered views in display order.
     *
     * @return an unmodifiable snapshot of the registered views
     */
    public List<RegisteredView> views() {
        return List.copyOf(views);
    }
}
