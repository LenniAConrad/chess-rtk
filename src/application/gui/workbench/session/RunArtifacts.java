/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.session;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tracks command output artifacts, run logs, and run manifests for the workbench.
 */
public final class RunArtifacts {

    /**
     * Host callbacks supplied by the owning workbench window.
     */
    public interface Host {

        /**
         * Returns the active workbench session.
         *
         * @return session
         */
    Session session();

        /**
         * Appends text to the console.
         *
         * @param text text
         */
    void appendConsole(String text);

        /**
         * Shows a warning.
         *
         * @param title title
         * @param message message
         */
    void showWarning(String title, String message);

        /**
         * Shows an error.
         *
         * @param title title
         * @param message message
         */
    void showError(String title, String message);
    }

    /**
     * Owning host.
     */
    private final Host host;

    /**
     * Creates the artifact controller.
     *
     * @param host owning host
     */
    public RunArtifacts(Host host) {
        this.host = host;
    }

    /**
     * Records any artifact files a finished command produced into the session
     * artifact index. Reads only the command arguments the workbench itself
     * built, so it never parses arbitrary command output.
     *
     * @param args finished command arguments
     * @return recorded artifacts
     */
    public List<Path> recordFromCommand(List<String> args) {
        List<Path> artifacts = new ArrayList<>();
        if (args == null) {
            return artifacts;
        }
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (isArtifactOutputFlag(token) && i + 1 < args.size()) {
                addIfPresent(artifacts, args.get(++i));
                continue;
            }
            if (isArtifactInputFlag(token) && i + 1 < args.size()) {
                i++;
                continue;
            }
            if (token == null || token.startsWith("-") || !looksLikeArtifactPath(token)) {
                continue;
            }
            addIfPresent(artifacts, token);
        }
        return List.copyOf(artifacts);
    }

    /**
     * Persists the full plain-text log for a finished, failed, or cancelled
     * command run, then records that log in the dashboard artifact list.
     *
     * @param job job to persist
     * @return log path, or null when writing failed
     */
    public Path persistLog(Job job) {
        if (job == null) {
            return null;
        }
        if (job.logPath() != null) {
            return job.logPath();
        }
        try {
            Path log = RunLog.write(job, Path.of(""));
            job.recordLog(log);
            host.session().artifacts().add(log);
            return log;
        } catch (IOException ex) {
            host.appendConsole("Run log failed: " + ex.getMessage() + System.lineSeparator());
            return null;
        }
    }

    /**
     * Persists a full log and JSON manifest for a finished, failed, or
     * cancelled command run, then records them in the dashboard artifact list.
     *
     * @param job job to persist
     * @param artifacts output artifacts detected for the job
     * @param stdin optional stdin payload
     */
    public void persistManifest(Job job, List<Path> artifacts, String stdin) {
        if (job == null || job.manifestPath() != null) {
            return;
        }
        persistLog(job);
        try {
            Path manifest = RunManifest.write(job, artifacts, stdin, Path.of(""));
            job.recordManifest(manifest, artifacts);
            host.session().artifacts().add(manifest);
        } catch (IOException ex) {
            host.appendConsole("Run manifest failed: " + ex.getMessage() + System.lineSeparator());
        }
    }

    /**
     * Opens a job's persisted run manifest through the desktop shell.
     *
     * @param job job whose manifest should be opened
     */
    public void openManifest(Job job) {
        if (job == null || job.manifestPath() == null) {
            host.showWarning("Run manifest", "No manifest has been written for this job yet.");
            return;
        }
        openPath(job.manifestPath(), "Run manifest");
    }

    /**
     * Opens a job's persisted full log through the desktop shell.
     *
     * @param job job whose log should be opened
     */
    public void openLog(Job job) {
        if (job == null) {
            host.showWarning("Run log", "No job is selected.");
            return;
        }
        Path log = job.logPath();
        if (log == null && job.status().isTerminal()) {
            log = persistLog(job);
        }
        if (log == null) {
            host.showWarning("Run log", "No log has been written for this job yet.");
            return;
        }
        openPath(log, "Run log");
    }

    /**
     * Opens the workbench log directory.
     */
    public void openLogsDirectory() {
        try {
            Path dir = RunLog.DEFAULT_DIR.toAbsolutePath().normalize();
            Files.createDirectories(dir);
            openPath(dir, "Workbench logs");
        } catch (IOException ex) {
            host.showError("Workbench logs", "Failed to open log directory: " + ex.getMessage());
        }
    }

    /**
     * Records an artifact path when it exists.
     *
     * @param artifacts mutable artifact list
     * @param token path token
     */
    private void addIfPresent(List<Path> artifacts, String token) {
        try {
            Path path = Path.of(token);
            if (Files.exists(path)) {
                Path artifact = path.toAbsolutePath().normalize();
                artifacts.add(artifact);
                host.session().artifacts().add(artifact);
            }
        } catch (java.nio.file.InvalidPathException ex) {
            // Not a usable path token; ignore.
        }
    }

    /**
     * Opens a path through the desktop shell.
     *
     * @param path path to open
     * @param title dialog title
     */
    private void openPath(Path path, String title) {
        if (path == null) {
            host.showWarning(title, "No file is available.");
            return;
        }
        if (!Files.exists(path)) {
            host.showWarning(title, "File does not exist: " + path);
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            host.showWarning(title, "Desktop integration is not supported.");
            return;
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException ex) {
            host.showError(title, "Failed to open file: " + ex.getMessage());
        }
    }

    /**
     * Returns whether a command flag names a generated output path.
     *
     * @param flag command flag
     * @return true when the next token is an output path
     */
    private static boolean isArtifactOutputFlag(String flag) {
        return "--output".equals(flag)
                || "-o".equals(flag)
                || "--output-dir".equals(flag)
                || "--pdf-output".equals(flag)
                || "--cover-output".equals(flag);
    }

    /**
     * Returns whether a command flag names an input/config path.
     *
     * @param flag command flag
     * @return true when the next token should not be recorded as an output
     */
    private static boolean isArtifactInputFlag(String flag) {
        return "--input".equals(flag)
                || "-i".equals(flag)
                || "--pgn".equals(flag)
                || "--suite".equals(flag)
                || "--protocol-path".equals(flag)
                || "--weights".equals(flag)
                || "--pdf".equals(flag);
    }

    /**
     * Returns whether a free command token looks like an artifact path.
     *
     * @param token command token
     * @return true when the token has an output-like extension
     */
    private static boolean looksLikeArtifactPath(String token) {
        if (token == null) {
            return false;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".jsonl") || lower.endsWith(".csv")
                || lower.endsWith(".png") || lower.endsWith(".pgn");
    }
}
