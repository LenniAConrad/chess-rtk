package application.gui.workbench.game;

import application.cli.PathOps;
import application.gui.workbench.command.CommandRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Produces Workbench review artifacts by running {@code crtk review game}.
 */
public final class ReviewCliArtifactProducer implements ReviewArtifactProducer {

    /**
     * Filename used for the materialized Workbench PGN.
     */
    private static final String PGN_FILENAME = "workbench.pgn";

    /**
     * Review JSONL filename used inside the temporary run directory.
     */
    private static final String REVIEW_FILENAME = "workbench.review.jsonl";

    /**
     * Study JSONL filename used inside the temporary run directory.
     */
    private static final String STUDY_FILENAME = "workbench.study.jsonl";

    /**
     * Study Record JSON filename used inside the temporary run directory.
     */
    private static final String RECORD_FILENAME = "workbench.study.record.json";

    /**
     * Offline alpha-beta depth for the first Workbench review-action slice.
     */
    private static final int OFFLINE_DEPTH = 2;

    /**
     * Offline node budget for the first Workbench review-action slice.
     */
    private static final long OFFLINE_NODES = 25_000L;

    /**
     * Offline watchdog budget for the first Workbench review-action slice.
     */
    private static final String OFFLINE_DURATION = "5s";

    /**
     * Creates a CLI-backed review artifact producer.
     */
    public ReviewCliArtifactProducer() {
        // default
    }

    /**
     * Returns the deterministic offline producer used by the Workbench Review panel.
     *
     * @return offline producer
     */
    public static ReviewCliArtifactProducer offline() {
        return new ReviewCliArtifactProducer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RunningTask produce(String pgnText, Consumer<String> onOutput, Consumer<Result> onDone,
            Consumer<Exception> onError) throws IOException {
        String normalizedPgn = Objects.toString(pgnText, "").strip();
        if (normalizedPgn.isEmpty()) {
            throw new IllegalArgumentException("No game PGN is available to review.");
        }
        Path dir = PathOps.createLocalTempDirectory("crtk-workbench-review-");
        Path pgn = dir.resolve(PGN_FILENAME);
        Path review = dir.resolve(REVIEW_FILENAME);
        Path study = dir.resolve(STUDY_FILENAME);
        Path record = dir.resolve(RECORD_FILENAME);
        Files.writeString(pgn, normalizedPgn + System.lineSeparator(), StandardCharsets.UTF_8);
        List<String> args = offlineArgs(pgn, review, study, record);
        CommandRunner.RunningCommand command = CommandRunner.run(args, null, onOutput, result -> {
            if (onDone != null) {
                onDone.accept(new Result(review, study, record, result.exitCode(), result.output(),
                        result.millis()));
            }
        }, onError);
        return new CommandTask(command);
    }

    /**
     * Builds the deterministic offline {@code review game --to-study} command.
     *
     * @param pgn input PGN path
     * @param review output review JSONL path
     * @param study output study-unit JSONL path
     * @param record output study Record JSON path
     * @return command arguments
     */
    public static List<String> offlineArgs(Path pgn, Path review, Path study, Path record) {
        return List.of(
                "review", "game",
                "--pgn", pgn.toString(),
                "--offline",
                "--depth", Integer.toString(OFFLINE_DEPTH),
                "--max-nodes", Long.toString(OFFLINE_NODES),
                "--max-duration", OFFLINE_DURATION,
                "--to-study",
                "--output", review.toString(),
                "--study-output", study.toString(),
                "--record-output", record.toString());
    }

    /**
     * Adapts a {@link CommandRunner} process handle to the review producer API.
     *
     * @param command running command
     */
    private record CommandTask(CommandRunner.RunningCommand command) implements RunningTask {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isRunning() {
            return command != null && command.isRunning();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void cancel() {
            if (command != null) {
                command.cancel();
            }
        }
    }
}
