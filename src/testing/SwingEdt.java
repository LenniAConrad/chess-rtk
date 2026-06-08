package testing;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Runs checked-exception Swing work on the event dispatch thread.
 */
final class SwingEdt {

    /**
     * Prevents instantiation.
     */
    private SwingEdt() {
        // utility
    }

    /**
     * Checked runnable used for event-dispatch-thread tasks.
     */
    @FunctionalInterface
    interface CheckedRunnable {

        /**
         * Runs the task.
         *
         * @throws Exception when the task fails
         */
        void run() throws Exception;
    }

    /**
     * Checked supplier used for event-dispatch-thread queries.
     *
     * @param <T> supplied value type
     */
    @FunctionalInterface
    interface CheckedSupplier<T> {

        /**
         * Supplies a value.
         *
         * @return supplied value
         * @throws Exception when the supplier fails
         */
        T get() throws Exception;
    }

    /**
     * Runs checked-exception work on Swing's event dispatch thread.
     *
     * @param task task to run
     * @throws Exception when the task fails
     */
    static void run(CheckedRunnable task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }
        final Exception[] exception = new Exception[1];
        final Error[] error = new Error[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    task.run();
                } catch (Exception ex) {
                    exception[0] = ex;
                } catch (Error err) {
                    error[0] = err;
                }
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Error err) {
                throw err;
            }
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw new IllegalStateException(cause);
        }
        if (error[0] != null) {
            throw error[0];
        }
        if (exception[0] != null) {
            throw exception[0];
        }
    }

    /**
     * Runs a checked supplier on Swing's event dispatch thread.
     *
     * @param supplier supplier to run
     * @param <T> supplied value type
     * @return supplied value
     * @throws Exception when the supplier fails
     */
    static <T> T call(CheckedSupplier<T> supplier) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        run(() -> value.set(supplier.get()));
        return value.get();
    }
}
