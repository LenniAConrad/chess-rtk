package application.gui.workbench.ui;

import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * One lazily-built mode inside a {@link SwitchedWorkspace}.
 *
 * <p>A mode keeps the switcher label and body factory together so callers do
 * not have to maintain parallel label arrays and builder lists. Keeping those
 * values as one descriptor makes mode registration easier to reorder, audit,
 * and eventually expose through command-palette or layout metadata.</p>
 */
public final class WorkspaceMode {

    /**
     * Label shown in the segmented switcher.
     */
    private final String label;

    /**
     * Lazy body factory for this mode.
     */
    private final transient Supplier<JComponent> builder;

    /**
     * Creates a workspace mode descriptor.
     *
     * @param label segment label shown to users
     * @param builder lazy body factory
     */
    public WorkspaceMode(String label, Supplier<JComponent> builder) {
        this.label = Objects.requireNonNull(label, "label");
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    /**
     * Returns the segment label shown to users.
     *
     * @return segment label
     */
    public String label() {
        return label;
    }

    /**
     * Returns the lazy body factory.
     *
     * @return body factory
     */
    public Supplier<JComponent> builder() {
        return builder;
    }
}
