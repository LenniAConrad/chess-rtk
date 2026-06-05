package application.gui.workbench.layout;

import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * One registrable top-level workbench surface: a display title, its panel
 * component, and an optional factory for additional ("duplicate"/detached)
 * instances.
 *
 * <p>This is a thin, named bundle of the three values
 * {@link EditorSplitArea#addPanel(String, JComponent, Supplier)} already
 * consumes. Naming it turns the workbench's tab set into data — a
 * {@link ViewRegistry} the shell iterates to build the tab strip, and that the
 * "+" new-tab menu and command palette can enumerate — instead of a hand-wired
 * wall of {@code addPanel} calls. It is the registration primitive behind the
 * workbench UX redesign's view/inspector system.</p>
 */
public final class RegisteredView {

    /**
     * Display title shown on the tab.
     */
    private final String title;

    /**
     * The surface's panel component (often a lazily-built panel).
     */
    private final JComponent panel;

    /**
     * Factory for additional instances of this surface, or {@code null} when the
     * surface is single-instance (cannot be duplicated/detached).
     */
    private final transient Supplier<JComponent> duplicateFactory;

    /**
     * Creates a single-instance view (no duplicate factory).
     *
     * @param title display title
     * @param panel panel component
     */
    public RegisteredView(String title, JComponent panel) {
        this(title, panel, null);
    }

    /**
     * Creates a view that can spawn additional instances.
     *
     * @param title display title
     * @param panel initial panel component
     * @param duplicateFactory factory for additional instances, or {@code null}
     */
    public RegisteredView(String title, JComponent panel, Supplier<JComponent> duplicateFactory) {
        this.title = title;
        this.panel = panel;
        this.duplicateFactory = duplicateFactory;
    }

    /**
     * Returns the display title.
     *
     * @return title
     */
    public String title() {
        return title;
    }

    /**
     * Returns the panel component.
     *
     * @return panel
     */
    public JComponent panel() {
        return panel;
    }

    /**
     * Returns the duplicate factory, or {@code null} when single-instance.
     *
     * @return duplicate factory or {@code null}
     */
    public Supplier<JComponent> duplicateFactory() {
        return duplicateFactory;
    }
}
