package application.gui.workbench.session;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/**
 * Persists full plain-text logs for workbench command runs.
 */
public final class RunLog {

    /**
     * Directory for full workbench command logs.
     */
    public static final Path DEFAULT_DIR = Path.of("session", "workbench-logs");

    /**
     * Prevents instantiation.
     */
    private RunLog() {
        // utility
    }

    /**
     * Writes a command log into the default session directory.
     *
     * @param job finished or cancelled job
     * @param workingDirectory process working directory
     * @return log path
     * @throws IOException when writing fails
     */
    public static Path write(Job job, Path workingDirectory) throws IOException {
    return write(DEFAULT_DIR, job, workingDirectory);
    }

    /**
     * Writes a command log into a supplied directory.
     *
     * @param directory log directory
     * @param job finished or cancelled job
     * @param workingDirectory process working directory
     * @return log path
     * @throws IOException when writing fails
     */
    public static Path write(Path directory, Job job, Path workingDirectory) throws IOException {
        if (job == null) {
    throw new IllegalArgumentException("job is required");
        }
        Path dir = directory == null ? DEFAULT_DIR : directory;
        return SessionFiles.writeString(dir, fileName(job), logText(job, workingDirectory));
    }

    /**
     * Builds a stable log filename.
     *
     * @param job job
     * @return filename
     */
    private static String fileName(Job job) {
        String status = job.status().name().toLowerCase(Locale.ROOT);
        return String.format(Locale.ROOT, "run-%05d-%s.log", job.id(), status);
    }

    /**
     * Builds the full plain-text log.
     *
     * @param job job
     * @param workingDirectory process working directory
     * @return log text
     */
    private static String logText(Job job, Path workingDirectory) {
        StringBuilder sb = new StringBuilder(Math.max(4096, job.output().length() + 512));
        sb.append("CRTK Workbench Command Log\n");
        sb.append("job: ").append(job.id()).append('\n');
        sb.append("created: ").append(Instant.ofEpochMilli(job.createdAtMillis())).append('\n');
        sb.append("written: ").append(Instant.now()).append('\n');
        sb.append("status: ").append(job.status().name().toLowerCase(Locale.ROOT)).append('\n');
        sb.append("exitCode: ").append(job.hasExitCode() ? Integer.toString(job.exitCode()) : "n/a").append('\n');
        sb.append("durationMillis: ").append(job.durationMillis()).append('\n');
        sb.append("workingDirectory: ").append(normalize(workingDirectory)).append('\n');
        sb.append("command: ").append(job.displayCommand()).append("\n\n");
        sb.append("----- output -----\n");
        sb.append(job.output());
        if (!job.output().endsWith("\n") && !job.output().endsWith("\r")) {
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Normalizes a path for display.
     *
     * @param path path
     * @return normalized string
     */
    private static String normalize(Path path) {
        return path == null ? "" : path.toAbsolutePath().normalize().toString();
    }
}
