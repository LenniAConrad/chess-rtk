package application.gui.workbench.window;

import java.util.Objects;

/**
 * Shared base for small workbench-window host adapters.
 */
public abstract class WindowHost {

    /** Owning workbench window. */
    protected final WindowBase window;

    /**
     * Creates a host adapter.
     *
     * @param window owning workbench window
     */
    protected WindowHost(WindowBase window) {
        this.window = Objects.requireNonNull(window, "window");
    }
}
