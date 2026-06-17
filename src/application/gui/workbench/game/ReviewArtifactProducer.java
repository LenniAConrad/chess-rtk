package application.gui.workbench.game;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Produces review artifacts for a Workbench game through an external path.
 */
public interface ReviewArtifactProducer {

    /**
     * Starts artifact production for PGN text.
     *
     * @param pgnText game PGN text
     * @param onOutput live command output callback
     * @param onDone completion callback
     * @param onError failure callback
     * @return running task handle
     * @throws IOException when setup fails before a command can start
     */
    RunningTask produce(String pgnText, Consumer<String> onOutput, Consumer<Result> onDone,
            Consumer<Exception> onError) throws IOException;

    /**
     * Returns a producer that reports review production as unavailable.
     *
     * @return unavailable producer
     */
    static ReviewArtifactProducer unavailable() {
        return (pgnText, onOutput, onDone, onError) -> {
            UnsupportedOperationException error =
                    new UnsupportedOperationException("review artifact production is not configured");
            if (onError != null) {
                onError.accept(error);
            }
            return RunningTask.NONE;
        };
    }

    /**
     * Running artifact-production handle.
     */
    interface RunningTask {

        /**
         * No-op task for unavailable or already-failed starts.
         */
        RunningTask NONE = new RunningTask() {
            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isRunning() {
                return false;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void cancel() {
                // no-op
            }
        };

        /**
         * Returns whether this task is active.
         *
         * @return true while active
         */
        boolean isRunning();

        /**
         * Cancels the task.
         */
        void cancel();
    }

    /**
     * Produced artifact metadata.
     *
     * @param reviewJsonl review JSONL path
     * @param studyJsonl study-unit JSONL path
     * @param recordJson study Record JSON path
     * @param exitCode command exit code
     * @param output command output
     * @param millis elapsed milliseconds
     */
    record Result(
            Path reviewJsonl,
            Path studyJsonl,
            Path recordJson,
            int exitCode,
            String output,
            long millis) {
    }
}
