package application.gui.workbench.session;

import static application.cli.Constants.OPT_COVER_OUTPUT;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_DIR;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PDF;
import static application.cli.Constants.OPT_PDF_OUTPUT;
import static application.cli.Constants.OPT_PGN;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_SUITE;
import static application.cli.Constants.OPT_WEIGHTS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Owns artifact discovery and persistence for workbench command runs.
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
         * @param text text to render or parse
         */
    void appendConsole(String text);

        /**
         * Shows a warning.
         *
         * @param title display title
         * @param message diagnostic message
         */
    void showWarning(String title, String message);

        /**
         * Shows an error.
         *
         * @param title display title
         * @param message diagnostic message
         */
    void showError(String title, String message);
    }

    /**
     * Owning host.
     */
    private final Host host;

    /**
     * Full-log directory, injectable so tests can exercise failed destinations.
     */
    private final Path logDirectory;

    /**
     * Manifest directory, kept separate because logs and manifests have different retention use.
     */
    private final Path manifestDirectory;

    /**
     * Failure keys already reported; retries must not flood the console.
     */
    private final Set<String> reportedArtifactFailures = new LinkedHashSet<>();

    /**
     * Malformed path tokens already reported, keyed by token and parse reason.
     */
    private final Set<String> reportedInvalidArtifactPaths = new LinkedHashSet<>();

    /**
     * Creates the artifact controller.
     *
     * @param host owning host
     */
    public RunArtifacts(Host host) {
        this(host, RunLog.DEFAULT_DIR, RunManifest.DEFAULT_DIR);
    }

    /**
     * Creates the artifact controller with explicit artifact directories.
     * Package-private for regression tests that need temporary or unwritable
     * targets without redirecting the real workbench dump paths.
     *
     * @param host owning host
     * @param logDirectory directory for full run logs
     * @param manifestDirectory directory for run manifests
     */
    RunArtifacts(Host host, Path logDirectory, Path manifestDirectory) {
        this.host = host;
        this.logDirectory = logDirectory == null ? RunLog.DEFAULT_DIR : logDirectory;
        this.manifestDirectory = manifestDirectory == null ? RunManifest.DEFAULT_DIR : manifestDirectory;
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
                addIfPresent(artifacts, args.get(++i), args);
                continue;
            }
            if (isArtifactInputFlag(token) && i + 1 < args.size()) {
                i++;
                continue;
            }
            if (token == null || token.startsWith("-") || !looksLikeArtifactPath(token)) {
                continue;
            }
            addIfPresent(artifacts, token, args);
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
            Path log = RunLog.write(logDirectory, job, Path.of(""));
            job.recordLog(log);
            host.session().artifacts().add(log);
            return log;
        } catch (IOException ex) {
            reportArtifactFailure("Run log", job, logDirectory, ex);
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
            Path manifest = RunManifest.write(manifestDirectory, job, artifacts, stdin, Path.of(""));
            job.recordManifest(manifest, artifacts);
            host.session().artifacts().add(manifest);
        } catch (IOException ex) {
            reportArtifactFailure("Run manifest", job, manifestDirectory, ex);
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
        Path dir = RunLog.DEFAULT_DIR.toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            openPath(dir, "Workbench logs");
        } catch (IOException ex) {
            host.showError("Workbench logs",
                    "Failed to open log directory " + dir + ": " + failureReason(ex));
        }
    }

    /**
     * Records existing generated outputs. Missing files are ignored because some
     * output flags are optional, but malformed path tokens are reported with the
     * command that produced them.
     *
     * @param artifacts mutable artifact list
     * @param token path token
     * @param args command arguments
     */
    private void addIfPresent(List<Path> artifacts, String token, List<String> args) {
        try {
            Path path = Path.of(token);
            if (Files.exists(path)) {
                Path artifact = path.toAbsolutePath().normalize();
                artifacts.add(artifact);
                host.session().artifacts().add(artifact);
            }
        } catch (java.nio.file.InvalidPathException ex) {
            reportInvalidArtifactPath(token, args, ex);
        }
    }

    /**
     * Emits one sanitized diagnostic for a generated path token the platform
     * cannot parse.
     *
     * @param token raw path token
     * @param args command arguments
     * @param ex parse failure
     */
    private void reportInvalidArtifactPath(String token, List<String> args, java.nio.file.InvalidPathException ex) {
        String reason = failureReason(ex);
        String key = token + ":" + reason;
        if (!reportedInvalidArtifactPaths.add(key)) {
            return;
        }
        host.appendConsole("Generated artifact path ignored"
                + " (command=\"" + oneLine(commandText(args)) + "\""
                + ", token=\"" + visibleToken(token) + "\"): "
                + reason
                + ". Check the output path argument."
                + System.lineSeparator());
    }

    /**
     * Reports one artifact write failure per job, artifact kind, and cause.
     * The context is intentionally richer than the IOException message because
     * those messages often omit the workbench command that triggered the write.
     *
     * @param artifact artifact label
     * @param job job being persisted
     * @param directory target directory
     * @param ex failure
     */
    private void reportArtifactFailure(String artifact, Job job, Path directory, IOException ex) {
        String reason = failureReason(ex);
        String key = artifact + ":" + job.id() + ":" + reason;
        if (!reportedArtifactFailures.add(key)) {
            return;
        }
        host.appendConsole(artifact + " write failed for job #" + job.id()
                + " (status=" + job.status().name().toLowerCase(Locale.ROOT)
                + ", command=\"" + oneLine(job.displayCommand()) + "\""
                + ", directory=" + normalize(directory) + "): "
                + reason
                + ". Check that the directory exists and is writable."
                + System.lineSeparator());
    }

    /**
     * Chooses stable, single-line failure text for persisted diagnostics.
     *
     * @param ex failure
     * @return reason text
     */
    private static String failureReason(IOException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : oneLine(message);
    }

    /**
     * Chooses stable, single-line failure text for invalid path tokens.
     *
     * @param ex invalid path failure
     * @return reason text
     */
    private static String failureReason(java.nio.file.InvalidPathException ex) {
        String reason = ex.getReason();
        return reason == null || reason.isBlank()
                ? ex.getClass().getSimpleName()
                : oneLine(reason);
    }

    /**
     * Canonicalizes diagnostic paths so repeated failures produce the same text.
     *
     * @param path file-system path
     * @return normalized absolute path
     */
    private static String normalize(Path path) {
        return path == null ? "" : path.toAbsolutePath().normalize().toString();
    }

    /**
     * Sanitizes metadata before embedding it inside one-line console messages.
     *
     * @param value text value
     * @return one-line text
     */
    private static String oneLine(String value) {
        return value == null ? ""
                : visibleToken(value.replace('\r', ' ').replace('\n', ' ').strip());
    }

    /**
     * Rebuilds the workbench display command, not a shell-escaped invocation.
     *
     * @param args command arguments
     * @return display command
     */
    private static String commandText(List<String> args) {
        return "crtk " + String.join(" ", args == null ? List.of() : args);
    }

    /**
     * Escapes control characters so invalid paths stay printable in logs and UI.
     *
     * @param token raw token
     * @return printable token
     */
    private static String visibleToken(String token) {
        if (token == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isISOControl(c)) {
                out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Maps desktop-shell results to the workbench warning/error contract.
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
        DesktopOpen.Result result = DesktopOpen.open(path);
        switch (result.status()) {
            case OPENED -> { }
            case UNSUPPORTED_DESKTOP -> host.showWarning(title, "Desktop integration is not supported.");
            case UNSUPPORTED_OPEN -> host.showWarning(title, "Opening files is not supported by this desktop.");
            case FAILED -> host.showError(title, "Failed to open file: " + result.detail());
        }
    }

    /**
     * Flags whose following token is expected to be created by the command.
     *
     * @param flag command flag
     * @return true when the next token is an output path
     */
    private static boolean isArtifactOutputFlag(String flag) {
        return OPT_OUTPUT.equals(flag)
                || OPT_OUTPUT_SHORT.equals(flag)
                || OPT_OUTPUT_DIR.equals(flag)
                || OPT_PDF_OUTPUT.equals(flag)
                || OPT_COVER_OUTPUT.equals(flag);
    }

    /**
     * Flags whose following token is consumed input and should not be recorded.
     *
     * @param flag command flag
     * @return true when the next token should not be recorded as an output
     */
    private static boolean isArtifactInputFlag(String flag) {
        return OPT_INPUT.equals(flag)
                || OPT_INPUT_SHORT.equals(flag)
                || OPT_PGN.equals(flag)
                || OPT_SUITE.equals(flag)
                || OPT_PROTOCOL_PATH.equals(flag)
                || OPT_WEIGHTS.equals(flag)
                || OPT_PDF.equals(flag);
    }

    /**
     * Detects output-like free arguments without reading command stdout.
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
