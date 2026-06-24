package application.gui.workbench.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/**
 * Shared helpers for small Swing background tasks.
 */
public final class SwingTasks {

    /**
     * Prevents instantiation.
     */
    private SwingTasks() {
        // utility
    }

    /**
     * Runs a possibly blocking task off the EDT and reports the result back on
     * the EDT.
     *
     * @param <T> result type
     * @param task background task
     * @param onDone EDT callback for success
     * @param onError EDT callback for failure or cancellation
     */
    public static <T> void runAsync(Callable<T> task, Consumer<T> onDone, Consumer<Exception> onError) {
        new SwingWorker<T, Void>() {
            /**
             * Executes the task on a worker thread.
             *
             * @return task
             * @throws Exception when the task fails
             */
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            /**
             * Dispatches the worker result or failure to the requested callback.
             */
            @Override
            protected void done() {
                try {
                    onDone.accept(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    onError.accept(ex);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    onError.accept(cause instanceof Exception exception ? exception : ex);
                } catch (CancellationException ex) {
                    onError.accept(ex);
                }
            }
        }.execute();
    }
}
