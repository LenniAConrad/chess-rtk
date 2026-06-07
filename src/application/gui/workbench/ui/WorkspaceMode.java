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
     * Live one-line context summary for the mode header.
     */
    private final transient Supplier<String> contextSupplier;

    /**
     * Optional right-side action component for the mode header.
     */
    private final transient Supplier<JComponent> actionsSupplier;

    /**
     * Creates a workspace mode descriptor.
     *
     * @param label segment label shown to users
     * @param builder lazy body factory
     */
    public WorkspaceMode(String label, Supplier<JComponent> builder) {
        this(label, builder, () -> "", () -> null);
    }

    /**
     * Creates a workspace mode descriptor with a context summary.
     *
     * @param label segment label shown to users
     * @param builder lazy body factory
     * @param contextSupplier live context summary supplier
     */
    public WorkspaceMode(String label, Supplier<JComponent> builder, Supplier<String> contextSupplier) {
        this(label, builder, contextSupplier, () -> null);
    }

    /**
     * Creates a workspace mode descriptor with header metadata.
     *
     * @param label segment label shown to users
     * @param builder lazy body factory
     * @param contextSupplier live context summary supplier
     * @param actionsSupplier optional action-component supplier
     */
    public WorkspaceMode(String label, Supplier<JComponent> builder, Supplier<String> contextSupplier,
            Supplier<JComponent> actionsSupplier) {
        this.label = Objects.requireNonNull(label, "label");
        this.builder = Objects.requireNonNull(builder, "builder");
        this.contextSupplier = contextSupplier == null ? () -> "" : contextSupplier;
        this.actionsSupplier = actionsSupplier == null ? () -> null : actionsSupplier;
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

    /**
     * Returns this mode's current one-line context summary.
     *
     * @return context summary
     */
    public String context() {
        String value = contextSupplier.get();
        return value == null ? "" : value;
    }

    /**
     * Returns this mode's optional action component.
     *
     * @return action component, or null
     */
    public JComponent actions() {
        return actionsSupplier.get();
    }
}
