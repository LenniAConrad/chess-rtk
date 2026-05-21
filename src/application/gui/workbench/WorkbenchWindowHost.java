package application.gui.workbench;

import java.util.Objects;

/**
 * Shared base for small workbench-window host adapters.
 */
abstract class WorkbenchWindowHost {

    /** Owning workbench window. */
    protected final WorkbenchWindowBase window;

    /**
     * Creates a host adapter.
     *
     * @param window owning workbench window
     */
    WorkbenchWindowHost(WorkbenchWindowBase window) {
        this.window = Objects.requireNonNull(window, "window");
    }
}
