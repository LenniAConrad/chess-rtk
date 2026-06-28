package testing;

import application.cli.PathOps;
import static testing.TestSupport.readUtf8;
import static testing.TestSupport.writeUtf8;
import static testing.WorkbenchTestSupport.*;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import application.gui.workbench.session.ArtifactIndex;
import application.gui.workbench.session.LogPanel;
import application.gui.workbench.ui.Theme;

/**
 * Workbench session, job, log, artifact, and manifest regression checks.
 */
final class WorkbenchSessionRegression {

    /**
     * Prevents instantiation.
     */
    private WorkbenchSessionRegression() {
        // utility
    }

    /**
     * Runs the focused session/log regression group.
     */
    static void run() {
        testWorkbenchSessionNotifiesListeners();
        testJobLifecycleTransitions();
        testRunLogWritesFullOutput();
        testRunLogAvoidsClobberingExistingFile();
        testRunArtifactFailuresAreContextualAndDeduplicated();
        testLogPanelConstructsHeadlessly();
        testDesktopOpenRejectsMissingPath();
        testRunManifestWritesReplayMetadata();
        testRunManifestAvoidsClobberingExistingFile();
        testJobHistoryIsBounded();
        testArtifactIndexNormalizesAndDeduplicatesPaths();
        testCommandResultParserSummaries();
    }

    /**
     * Verifies the session model notifies listeners when its state changes.
     */
    private static void testWorkbenchSessionNotifiesListeners() {
        Object session = construct(type("Session"), new Class<?>[0]);
        Class<?> listenerType = type("SessionListener");
        int[] notifications = { 0 };
        Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(),
                new Class<?>[] { listenerType }, (proxy, method, args) -> {
                    if ("sessionChanged".equals(method.getName())) {
                        notifications[0]++;
                    }
                    return null;
                });
        invoke(session, "addListener", new Class<?>[] { listenerType }, listener);
        invoke(session, "updatePosition",
                new Class<?>[] { String.class, boolean.class, int.class, int.class, int.class },
                START_FEN, true, 0, 0, 20);
        invoke(session, "updateBatch", new Class<?>[] { String.class }, "3 FEN rows ready");
        assertEquals(Integer.valueOf(2), Integer.valueOf(notifications[0]),
                "session change notifications");
        assertEquals(START_FEN, invoke(session, "fen", new Class<?>[0]), "session FEN");
        assertEquals(Integer.valueOf(20), invoke(session, "legalMoveCount", new Class<?>[0]),
                "session legal-move count");
    }

    /**
     * Verifies job lifecycle transitions and exit-code-driven terminal status.
     */
    private static void testJobLifecycleTransitions() {
        Object manager = construct(type("JobManager"), new Class<?>[0]);
        Class<?> jobType = type("Job");

        Object queued = invoke(manager, "create", new Class<?>[] { List.class },
                List.of("doctor"));
        assertEquals("QUEUED", String.valueOf(invoke(queued, "status", new Class<?>[0])),
                "new job is queued");
        invoke(manager, "markRunning", new Class<?>[] { jobType }, queued);
        assertEquals("RUNNING", String.valueOf(invoke(queued, "status", new Class<?>[0])),
                "running job");
        invoke(manager, "markFinished", new Class<?>[] { jobType, int.class, String.class, long.class },
                queued, 0, "doctor: ok", 12L);
        assertEquals("SUCCEEDED", String.valueOf(invoke(queued, "status", new Class<?>[0])),
                "exit-zero job succeeded");

        Object failing = invoke(manager, "create", new Class<?>[] { List.class },
                List.of("config", "validate"));
        invoke(manager, "markFinished", new Class<?>[] { jobType, int.class, String.class, long.class },
                failing, 2, "bad config", 7L);
        assertEquals("FAILED", String.valueOf(invoke(failing, "status", new Class<?>[0])),
                "non-zero exit job failed");

        Object cancelled = invoke(manager, "create", new Class<?>[] { List.class },
                List.of("engine", "perft"));
        invoke(manager, "markCancelled", new Class<?>[] { jobType, long.class }, cancelled, 3L);
        assertEquals("CANCELLED", String.valueOf(invoke(cancelled, "status", new Class<?>[0])),
                "cancelled job");
    }

    /**
     * Verifies full workbench command output is persisted as an accessible
     * plain-text log and linked back to the job.
     */
    private static void testRunLogWritesFullOutput() {
        try {
            Path dir = PathOps.createLocalTempDirectory("crtk-workbench-log-");
            Object manager = construct(type("JobManager"), new Class<?>[0]);
            Class<?> jobType = type("Job");
            Object job = invoke(manager, "create", new Class<?>[] { List.class },
                    List.of("engine", "bestmove", "--fen", START_FEN));
            String output = "info depth 1\rinfo depth 2" + System.lineSeparator()
                    + "bestmove e2e4" + System.lineSeparator();
            invoke(manager, "markFinished", new Class<?>[] { jobType, int.class, String.class, long.class },
                    job, 0, output, 42L);

            Path log = (Path) invokeStatic(type("RunLog"), "write",
                    new Class<?>[] { Path.class, jobType, Path.class }, dir, job, Path.of("."));
            String text = readUtf8(log);
            assertTrue(text.contains("CRTK Workbench Command Log"), "log header");
            assertTrue(text.contains("command: crtk engine bestmove"), "log command");
            assertTrue(text.contains("summary: bestmove e2e4"), "log parsed summary");
            assertFalse(text.contains("written:"), "log omits volatile write timestamp");
            assertTrue(text.contains("info depth 1\rinfo depth 2"), "log preserves carriage returns");
            assertTrue(text.contains("bestmove e2e4"), "log output");

            invoke(job, "recordLog", new Class<?>[] { Path.class }, log);
            assertEquals(log, invoke(job, "logPath", new Class<?>[0]), "job log path");
        } catch (java.io.IOException ex) {
            throw new AssertionError("log test setup failed", ex);
        }
    }

    /**
     * Verifies run logs never overwrite an existing file at the deterministic
     * first-choice path.
     */
    private static void testRunLogAvoidsClobberingExistingFile() {
        try {
            Path dir = PathOps.createLocalTempDirectory("crtk-workbench-log-clobber-");
            Path existing = dir.resolve("run-00001-succeeded.log");
            writeUtf8(existing, "keep");

            Object manager = construct(type("JobManager"), new Class<?>[0]);
            Class<?> jobType = type("Job");
            Object job = invoke(manager, "create", new Class<?>[] { List.class },
                    List.of("doctor"));
            invoke(manager, "markFinished", new Class<?>[] { jobType, int.class, String.class, long.class },
                    job, 0, "doctor ok", 7L);

            Path log = (Path) invokeStatic(type("RunLog"), "write",
                    new Class<?>[] { Path.class, jobType, Path.class }, dir, job, Path.of("."));
            assertFalse(existing.equals(log), "run log selects a collision-free filename");
            assertEquals("keep", readUtf8(existing),
                    "existing run log is not clobbered");
            assertTrue(readUtf8(log).contains("doctor ok"),
                    "new run log is written");
        } catch (java.io.IOException ex) {
            throw new AssertionError("log clobber test setup failed", ex);
        }
    }

    /**
     * Guards the artifact-diagnostic contract: enough job context to recover,
     * but one console line per repeated failure.
     */
    private static void testRunArtifactFailuresAreContextualAndDeduplicated() {
        try {
            Path dir = PathOps.createLocalTempDirectory("crtk-workbench-log-failure-");
            Path blocked = dir.resolve("logs");
            writeUtf8(blocked, "not a directory");
            List<String> console = new ArrayList<>();
            Object session = construct(type("Session"), new Class<?>[0]);
            Class<?> hostType = type("RunArtifacts$Host");
            Object host = Proxy.newProxyInstance(hostType.getClassLoader(), new Class<?>[] { hostType },
                    (proxy, method, args) -> {
                        return switch (method.getName()) {
                            case "session" -> session;
                            case "appendConsole" -> {
                                console.add((String) args[0]);
                                yield null;
                            }
                            default -> null;
                        };
                    });
            Object artifacts = construct(type("RunArtifacts"),
                    new Class<?>[] { hostType, Path.class, Path.class },
                    host, blocked, dir.resolve("manifests"));

            Object manager = construct(type("JobManager"), new Class<?>[0]);
            Class<?> jobType = type("Job");
            Object job = invoke(manager, "create", new Class<?>[] { List.class },
                    List.of("doctor"));
            invoke(manager, "markFinished", new Class<?>[] { jobType, int.class, String.class, long.class },
                    job, 2, "doctor failed", 9L);

            assertEquals(null, invoke(artifacts, "persistLog", new Class<?>[] { jobType }, job),
                    "failed log write returns null");
            assertEquals(null, invoke(artifacts, "persistLog", new Class<?>[] { jobType }, job),
                    "repeated failed log write returns null");
            assertEquals(Integer.valueOf(1), Integer.valueOf(console.size()),
                    "repeated log write failure is reported once");
            String message = console.get(0);
            assertTrue(message.contains("Run log write failed for job #1"),
                    "log failure identifies job");
            assertTrue(message.contains("status=failed"), "log failure includes status");
            assertTrue(message.contains("command=\"crtk doctor\""), "log failure includes command");
            assertTrue(message.contains("directory=" + blocked.toAbsolutePath().normalize()),
                    "log failure includes target directory");
            assertTrue(message.contains("Check that the directory exists and is writable."),
                    "log failure gives remediation");
            List<?> recorded = listValue(invoke(artifacts, "recordFromCommand",
                    new Class<?>[] { List.class },
                    List.of("book", "render", "--output", "bad\0path")));
            assertTrue(recorded.isEmpty(), "invalid artifact path is not recorded");
            invoke(artifacts, "recordFromCommand", new Class<?>[] { List.class },
                    List.of("book", "render", "--output", "bad\0path"));
            assertEquals(Integer.valueOf(2), Integer.valueOf(console.size()),
                    "repeated invalid artifact path is reported once");
            String invalidPathMessage = console.get(1);
            assertTrue(invalidPathMessage.contains("Generated artifact path ignored"),
                    "invalid artifact path warning names the failure");
            assertTrue(invalidPathMessage.contains("token=\"bad\\u0000path\""),
                    "invalid artifact path warning escapes the token");
            assertFalse(invalidPathMessage.contains("\0"),
                    "invalid artifact path warning contains no raw control character");
            assertTrue(invalidPathMessage.contains("Check the output path argument."),
                    "invalid artifact path warning gives remediation");
        } catch (java.io.IOException ex) {
            throw new AssertionError("log failure test setup failed", ex);
        }
    }

    /**
     * Verifies the persisted-log browser constructs and paints without a
     * display server.
     */
    private static void testLogPanelConstructsHeadlessly() {
        String source = readUtf8(Path.of("src/application/gui/workbench/session/LogPanel.java"));
        String helper = readUtf8(Path.of("src/application/gui/workbench/session/DesktopOpen.java"));
        assertTrue(source.contains("DesktopOpen.open(path)"),
                "LogPanel delegates desktop opening to the shared helper");
        assertFalse(source.contains("\"===== "),
                "LogPanel avoids ASCII fence headers between log files");
        assertTrue(source.contains("appendSectionHeader(line)"),
                "LogPanel renders synthetic file headers through the styled console path");
        assertTrue(source.contains("setActionCommand(\"logs.openSelected\")"),
                "LogPanel exposes stable action commands for toolbar actions");
        assertTrue(source.contains("filterField.getAccessibleContext().setAccessibleName(\"Filter logs\")"),
                "LogPanel gives the file filter an accessible name");
        assertTrue(source.contains("implements ListCellRenderer<LogEntry>"),
                "LogPanel uses a theme-aware two-line renderer for log rows");
        assertTrue(source.contains("cleanButton.getAccessibleContext().setAccessibleName(\"Clean logs\")"),
                "LogPanel destructive hold control exposes an accessible name");
        assertTrue(helper.contains("desktop.isSupported(Desktop.Action.OPEN)"),
                "DesktopOpen checks desktop OPEN action support");
        Theme.Mode previous = Theme.mode();
        LogPanel panel = new LogPanel(value -> {
            // Clipboard writes are not part of this headless paint regression.
        });
        try {
            assertTrue(componentTreeHasTooltip(panel, "Filter logs by filename"),
                    "log browser exposes a filter tooltip");
            assertTrue(componentTreeHasTooltip(panel, "Select an individual log file to open it."),
                    "disabled selected-log action explains how to enable it");
            Theme.setMode(Theme.Mode.LIGHT);
            Theme.refreshComponentTree(panel);
            assertPaintsOpaqueCorner(panel, 760, 460, "log panel light opaque background");
            Theme.setMode(Theme.Mode.DARK);
            Theme.refreshComponentTree(panel);
            assertPaintsOpaqueCorner(panel, 760, 460, "log panel dark opaque background");
        } finally {
            Theme.setMode(previous);
            Theme.refreshComponentTree(panel);
        }
    }

    /**
     * Verifies missing artifact/log paths do not throw from desktop-open
     * integration.
     */
    private static void testDesktopOpenRejectsMissingPath() {
        Object result = invokeStatic(type("DesktopOpen"), "open",
                new Class<?>[] { Path.class }, new Object[] { null });
        assertEquals("FAILED", String.valueOf(invoke(result, "status", new Class<?>[0])),
                "desktop open null path status");
        assertTrue(String.valueOf(invoke(result, "detail", new Class<?>[0])).contains("No path"),
                "desktop open null path detail");
    }

    /**
     * Verifies a completed job can be persisted as a replayable JSON run
     * manifest with command, limits, input hash, output hash and job linkage.
     */
    private static void testRunManifestWritesReplayMetadata() {
        try {
            Path dir = PathOps.createLocalTempDirectory("crtk-workbench-manifest-");
            Path input = dir.resolve("input.fens");
            Path output = dir.resolve("result.jsonl");
            writeUtf8(input, START_FEN + System.lineSeparator());
            writeUtf8(output, "{\"bestmove\":\"e2e4\"}" + System.lineSeparator());

            Object manager = construct(type("JobManager"), new Class<?>[0]);
            Class<?> jobType = type("Job");
            Object job = invoke(manager, "create", new Class<?>[] { List.class }, List.of(
                    "engine", "bestmove-batch",
                    "--input", input.toString(),
                    "--protocol-path", input.toString(),
                    "--max-duration", "1s",
                    "--threads", "2",
                    "--output", output.toString(),
                    "--jsonl"));
            invoke(manager, "markFinished", new Class<?>[] { jobType, int.class, String.class, long.class },
                    job, 0, "bestmove e2e4", 42L);
            Path log = (Path) invokeStatic(type("RunLog"), "write",
                    new Class<?>[] { Path.class, jobType, Path.class }, dir, job, Path.of("."));
            invoke(job, "recordLog", new Class<?>[] { Path.class }, log);

            Path manifest = (Path) invokeStatic(type("RunManifest"), "write",
                    new Class<?>[] { Path.class, jobType, List.class, String.class, Path.class },
                    dir, job, List.of(output), "stdin payload", Path.of("."));
            String json = readUtf8(manifest);

            assertTrue(json.contains("\"schema\": \"crtk.workbench.run-manifest.v1\""),
                    "manifest schema");
            assertTrue(json.contains("\"status\": \"succeeded\""), "manifest status");
            assertTrue(json.contains("\"exitCode\": 0"), "manifest exit code");
            assertTrue(json.contains("\"args\": [\"engine\",\"bestmove-batch\""),
                    "manifest args");
            assertTrue(json.contains("\"--max-duration\":\"1s\""), "manifest limits");
            assertTrue(json.contains("\"--protocol-path\":\"" + jsonEsc(input.toString()) + "\""),
                    "manifest engine protocol");
            assertTrue(json.contains("\"kind\":\"--input\""), "manifest input entry");
            assertTrue(json.contains("\"kind\":\"--output\""), "manifest declared output entry");
            assertTrue(json.contains("\"sha256\""), "manifest file hashes");
            assertTrue(json.contains("\"present\":true"), "manifest stdin metadata");
            assertTrue(json.contains("\"logPath\": \"" + jsonEsc(log.toString()) + "\""),
                    "manifest links full log");

            invoke(job, "recordManifest", new Class<?>[] { Path.class, List.class }, manifest, List.of(output));
            assertEquals(manifest, invoke(job, "manifestPath", new Class<?>[0]),
                    "job manifest path");
            assertEquals(Integer.valueOf(1), Integer.valueOf(listValue(invoke(job, "artifacts",
                    new Class<?>[0])).size()), "job artifact count");
        } catch (java.io.IOException ex) {
            throw new AssertionError("manifest test setup failed", ex);
        }
    }

    /**
     * Verifies run manifests never overwrite an existing file at the
     * deterministic first-choice path.
     */
    private static void testRunManifestAvoidsClobberingExistingFile() {
        try {
            Path dir = PathOps.createLocalTempDirectory("crtk-workbench-manifest-clobber-");
            Path existing = dir.resolve("run-00001-succeeded.json");
            writeUtf8(existing, "keep");

            Object manager = construct(type("JobManager"), new Class<?>[0]);
            Class<?> jobType = type("Job");
            Object job = invoke(manager, "create", new Class<?>[] { List.class },
                    List.of("doctor"));
            invoke(manager, "markFinished", new Class<?>[] { jobType, int.class, String.class, long.class },
                    job, 0, "doctor ok", 7L);

            Path manifest = (Path) invokeStatic(type("RunManifest"), "write",
                    new Class<?>[] { Path.class, jobType, List.class, String.class, Path.class },
                    dir, job, List.of(), "", Path.of("."));
            assertFalse(existing.equals(manifest), "run manifest selects a collision-free filename");
            assertEquals("keep", readUtf8(existing),
                    "existing run manifest is not clobbered");
            assertTrue(readUtf8(manifest)
                    .contains("\"schema\": \"crtk.workbench.run-manifest.v1\""),
                    "new run manifest is written");
        } catch (java.io.IOException ex) {
            throw new AssertionError("manifest clobber test setup failed", ex);
        }
    }

    /**
     * Verifies the job history is bounded to {@code HISTORY_LIMIT} entries.
     */
    private static void testJobHistoryIsBounded() {
        Object manager = construct(type("JobManager"), new Class<?>[0]);
        int limit = (Integer) staticField(type("JobManager"), "HISTORY_LIMIT");
        for (int i = 0; i < limit + 17; i++) {
            invoke(manager, "create", new Class<?>[] { List.class }, List.of("doctor"));
        }
        assertEquals(Integer.valueOf(limit), invoke(manager, "size", new Class<?>[0]),
                "job history is bounded");
    }

    /**
     * Verifies dashboard artifact paths normalize before deduplication.
     */
    private static void testArtifactIndexNormalizesAndDeduplicatesPaths() {
        ArtifactIndex index = new ArtifactIndex();
        Path first = Path.of("out", "..", "out", "workbench.log");
        Path second = Path.of("out", "workbench.log");
        index.add(first);
        index.add(second);
        List<Path> recent = index.recent();
        assertEquals(Integer.valueOf(1), Integer.valueOf(recent.size()),
                "equivalent artifact paths are deduplicated");
        assertEquals(second.toAbsolutePath().normalize(), recent.get(0),
                "artifact path is stored normalized");
    }

    /**
     * Verifies the command-result parser produces representative summaries.
     */
    private static void testCommandResultParserSummaries() {
        Class<?> parser = type("CommandResultParser");
        assertEquals("bestmove e2e4", invokeStatic(parser, "summarize",
                new Class<?>[] { List.class, int.class, String.class },
                List.of("engine", "bestmove"), 0, "info depth 12\nbestmove e2e4 ponder e7e5\n"),
                "bestmove summary");
        assertEquals("config valid", invokeStatic(parser, "summarize",
                new Class<?>[] { List.class, int.class, String.class },
                List.of("config", "validate"), 0, "all settings valid"),
                "config validate summary");
        Object failure = invokeStatic(parser, "summarize",
                new Class<?>[] { List.class, int.class, String.class },
                List.of("doctor"), 3, "missing engine binary");
        assertTrue(String.valueOf(failure).startsWith("exit 3"),
                "non-zero exit summary names the exit code");
        String detail = String.valueOf(invokeStatic(parser, "detail",
                new Class<?>[] { List.class, int.class, String.class, long.class },
                List.of("engine", "bestmove"), 0,
                "info depth 12 score cp 34 nodes 1000 nps 50000 time 20\nbestmove e2e4 ponder e7e5\n",
                Long.valueOf(25L)));
        assertTrue(detail.contains("Best move: e2e4"),
                "parsed result exposes best move");
        assertTrue(detail.contains("Score: cp 34"),
                "parsed result exposes score");
        assertTrue(detail.contains("Depth: 12"),
                "parsed result exposes depth");
    }
}
