/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package testing;

import application.cli.PathOps;
import static testing.WorkbenchTestSupport.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;

import application.gui.workbench.session.LogPanel;
import application.gui.workbench.network.NnueDrawing;
import application.gui.workbench.ui.Theme;

import chess.core.Move;
import chess.core.Position;
import chess.nn.nnue.FeatureEncoder;

/**
 * Dashboard, network, NN, MCTS, and job regression checks.
 */
final class WorkbenchBackendRegression {

    /**
     * Prevents instantiation.
     */
    private WorkbenchBackendRegression() {
        // utility
    }

    /**
     * Runs this focused regression group.
     */
    static void run() {
        testDashboardClassesLoad();
        testWorkbenchSessionNotifiesListeners();
        testJobLifecycleTransitions();
        testRunLogWritesFullOutput();
        testRunLogAvoidsClobberingExistingFile();
        testLogPanelConstructsHeadlessly();
        testRunManifestWritesReplayMetadata();
        testRunManifestAvoidsClobberingExistingFile();
        testJobHistoryIsBounded();
        testCommandResultParserSummaries();
        testDashboardPanelConstructsHeadlessly();
        testNetworkPanelSimpleControlsRenderHeadlessly();
        testNetworkLoadingCardTracksRequestedArchitecture();
        testNetworkLoadingCardShowsProviderPhase();
        testRealActivationProgressReportsFallback();
        testNetworkMctsUpdatesAreNonBlocking();
        testNetworkDiagnosticsPreviewHighlightsConfig();
        testNetworkDiagnosticsPreviewRecolorsForDarkTheme();
        testNnueStackSummaryStaysCompact();
        testNnueViewsPaintSyntheticSnapshotHeadlessly();
        testNnueTraceFitsViewportAndCentersColumns();
        testNnueHalfKpDecodingUsesFeatureEncoderLayout();
        testNnueTraceRanksCombinedContributionsAndShowsAllFeatures();
        testNnueTraceInlineInspectorShowsGatheredColumn();
        testCnnAndBt4AtlasPaintSyntheticSnapshotsHeadlessly();
        testWorkbenchCnnUsesRealWeightsWhenAvailable();
        testMctsSearchBuildsRootRows();
        testMctsSearchMateInOneUsesCliShortcut();
        testMctsSearchForcedMateProofOverridesVisits();
        testMctsSearchForcedMateInFourProofOverridesVisits();
        testMctsSearchTerminalAndDrawHandling();
        testMctsSearchReusesRootSubtree();
        testMctsSearchClosesBackend();
        testMctsPanelConstructsHeadlessly();
        testDashboardTabIsFirst();
        testSessionEvalHistory();
        testMiniChartRendersHeadlessly();
    }

    /**
     * Verifies every dashboard support class loads.
     */
    private static void testDashboardClassesLoad() {
        for (String name : new String[] {
                "Session", "SessionListener", "HealthSnapshot",
                "ArtifactIndex", "Job", "JobStatus",
                "JobManager", "JobTableModel", "CommandResultParser",
                "DashboardActions", "DashboardPanel" }) {
            assertTrue(type(name) != null, "dashboard class " + name + " loads");
        }
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
            String text = Files.readString(log, StandardCharsets.UTF_8);
            assertTrue(text.contains("CRTK Workbench Command Log"), "log header");
            assertTrue(text.contains("command: crtk engine bestmove"), "log command");
            assertTrue(text.contains("info depth 1\rinfo depth 2"), "log preserves carriage returns");
            assertTrue(text.contains("bestmove e2e4"), "log output");

            invoke(job, "recordLog", new Class<?>[] { Path.class }, log);
            assertEquals(log, invoke(job, "logPath", new Class<?>[0]), "job log path");
        } catch (java.io.IOException ex) {
            throw new AssertionError("log test setup failed", ex);
        }
    }

    /**
     * Verifies the persisted-log browser constructs and paints without a
     * display server.
     */
    private static void testLogPanelConstructsHeadlessly() {
        Theme.Mode previous = Theme.mode();
        LogPanel panel = new LogPanel(value -> {
            // Clipboard writes are not part of this headless paint regression.
        });
        try {
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
     * Verifies run logs never overwrite an existing file at the deterministic
     * first-choice path.
     */
    private static void testRunLogAvoidsClobberingExistingFile() {
        try {
            Path dir = PathOps.createLocalTempDirectory("crtk-workbench-log-clobber-");
            Path existing = dir.resolve("run-00001-succeeded.log");
            Files.writeString(existing, "keep", StandardCharsets.UTF_8);

            Object manager = construct(type("JobManager"), new Class<?>[0]);
            Class<?> jobType = type("Job");
            Object job = invoke(manager, "create", new Class<?>[] { List.class },
                    List.of("doctor"));
            invoke(manager, "markFinished", new Class<?>[] { jobType, int.class, String.class, long.class },
                    job, 0, "doctor ok", 7L);

            Path log = (Path) invokeStatic(type("RunLog"), "write",
                    new Class<?>[] { Path.class, jobType, Path.class }, dir, job, Path.of("."));
            assertFalse(existing.equals(log), "run log selects a collision-free filename");
            assertEquals("keep", Files.readString(existing, StandardCharsets.UTF_8),
                    "existing run log is not clobbered");
            assertTrue(Files.readString(log, StandardCharsets.UTF_8).contains("doctor ok"),
                    "new run log is written");
        } catch (java.io.IOException ex) {
            throw new AssertionError("log clobber test setup failed", ex);
        }
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
            Files.writeString(input, START_FEN + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.writeString(output, "{\"bestmove\":\"e2e4\"}" + System.lineSeparator(), StandardCharsets.UTF_8);

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
            String json = Files.readString(manifest, StandardCharsets.UTF_8);

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
            assertEquals(Integer.valueOf(1), Integer.valueOf(((List<?>) invoke(job, "artifacts",
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
            Files.writeString(existing, "keep", StandardCharsets.UTF_8);

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
            assertEquals("keep", Files.readString(existing, StandardCharsets.UTF_8),
                    "existing run manifest is not clobbered");
            assertTrue(Files.readString(manifest, StandardCharsets.UTF_8)
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
    }

    /**
     * Verifies the dashboard panel and its cards build headlessly.
     */
    private static void testDashboardPanelConstructsHeadlessly() {
        Object session = construct(type("Session"), new Class<?>[0]);
        Class<?> actionsType = type("DashboardActions");
        Object actions = Proxy.newProxyInstance(actionsType.getClassLoader(),
                new Class<?>[] { actionsType }, (proxy, method, args) -> null);
        Object panel = construct(type("DashboardPanel"),
                new Class<?>[] { type("Session"), actionsType }, session, actions);
        assertTrue(panel instanceof JComponent, "dashboard panel is a Swing component");
        assertTrue(((JComponent) panel).getComponentCount() > 0,
                "dashboard panel builds its cards");
        invoke(session, "updatePosition",
                new Class<?>[] { String.class, boolean.class, int.class, int.class, int.class },
                START_FEN, true, 0, 0, 20);
        invoke(session, "updateTags", new Class<?>[] { List.class },
                List.of("OPENING: name=\"Start\"", "MATERIAL: equal"));
        assertPaintsOpaqueCorner((JComponent) panel, 1000, 760,
                "dashboard infographics paint opaquely");
    }

    /**
     * Verifies the simplified network controls paint headlessly and keep the
     * visible view selector compact.
     */
    private static void testNetworkPanelSimpleControlsRenderHeadlessly() {
        Theme.setMode(Theme.Mode.LIGHT);
        Object panel = construct(type("NetworkPanel"), new Class<?>[0]);
        Timer timer = (Timer) field(panel, "debounceTimer");
        timer.stop();
        JSpinner visits = (JSpinner) field(panel, "mctsVisitsSpinner");
        JCheckBox followLeaf = (JCheckBox) field(panel, "mctsFollowLeafToggle");
        assertFalse(((JComponent) field(panel, "mctsWeightsPanel")).isVisible(),
                "network MCTS edge weights start collapsed");
        assertTrue(((JComponent) field(panel, "detailsTabs")).isVisible(),
                "network inspector stays available by default");
        assertEquals(staticField(type("Defaults"), "MCTS_VISITS"), visits.getValue(),
                "network MCTS uses shared visit default");
        assertFalse(followLeaf.isSelected(), "network leaf following starts off");
        JComboBox<?> archCombo = (JComboBox<?>) field(panel, "archCombo");
        assertEquals(Integer.valueOf(3), Integer.valueOf(archCombo.getItemCount()),
                "network selector exposes one entry per network family");
        assertEquals(Integer.valueOf(1), Integer.valueOf(countArchItems(archCombo, "NNUE")),
                "network selector exposes only one NNUE entry");
        archCombo.setSelectedItem("NNUE - HalfKP");
        JComponent viewMode = (JComponent) field(panel, "viewMode");
        assertTrue(viewMode.getPreferredSize().width < 340,
                "view selector exposes only the simple modes");
        boolean[] enabled = (boolean[]) field(viewMode, "segmentEnabled");
        assertTrue(enabled[2], "NNUE all-neurons segment enabled");
        assertTrue(enabled[3], "NNUE atlas segment enabled");
        archCombo.setSelectedItem("CNN - 10x128");
        enabled = (boolean[]) field(viewMode, "segmentEnabled");
        assertTrue(enabled[2], "CNN all-neurons segment enabled");
        assertTrue(enabled[3], "CNN atlas segment enabled");
        archCombo.setSelectedItem("BT4 - 1024x15x32h");
        enabled = (boolean[]) field(viewMode, "segmentEnabled");
        assertTrue(enabled[2], "BT4 all-neurons segment enabled");
        assertTrue(enabled[3], "BT4 atlas segment enabled");
        invoke(panel, "setFen", new Class<?>[] { String.class }, START_FEN);
        invoke(panel, "setActive", new Class<?>[] { boolean.class }, true);
        timer.stop();
        Object loadingPanel = field(panel, "loadingPanel");
        assertTrue((Boolean) invoke(loadingPanel, "isActive", new Class<?>[0]),
                "network panel shows animated loading card before first inference");
        invoke(viewMode, "setSelectedIndex", new Class<?>[] { int.class }, 3);
        assertPaintsOpaqueCorner((JComponent) panel, 1180, 720,
                "network panel simple controls paint opaquely");
        Theme.setMode(Theme.Mode.DARK);
        Theme.refreshComponentTree((JComponent) panel);
        assertEquals(themeColor("LINE"), firstBorderColor(((JComponent) field(panel, "networkToolbar")).getBorder()),
                "network toolbar separator follows dark theme");
        assertEquals(themeColor("LINE"), firstBorderColor(((JComponent) field(panel, "inspectorPanel")).getBorder()),
                "network inspector separator follows dark theme");
        Theme.setMode(Theme.Mode.LIGHT);
        timer.stop();
        invoke(panel, "dispose", new Class<?>[0]);
    }

    /**
     * Verifies the NNUE header stack label remains compact enough for small
     * network panels.
     */
    private static void testNnueStackSummaryStaysCompact() {
        String summary = NnueDrawing.stockfishStackSummary(new float[1024], new float[512],
                new float[32], new float[32]);
        assertEquals("1024 / 31 / 32", summary, "compact Stockfish NNUE stack summary");
        assertFalse(summary.contains("fwd"), "forward skip detail stays in the trace, not cramped header");
    }

    /**
     * Verifies the network loading card is tied to the model/FEN that
     * requested it, rather than any unrelated in-flight inference worker.
     */
    private static void testNetworkLoadingCardTracksRequestedArchitecture() {
        Theme.setMode(Theme.Mode.LIGHT);
        Object panel = construct(type("NetworkPanel"), new Class<?>[0]);
        Timer timer = (Timer) field(panel, "debounceTimer");
        timer.stop();
        JComboBox<?> archCombo = (JComboBox<?>) field(panel, "archCombo");
        archCombo.setSelectedItem("CNN - 10x128");
        setField(panel, "mainBoardFen", START_FEN);
        Object loadingPanel = field(panel, "loadingPanel");
        invoke(loadingPanel, "start",
                new Class<?>[] { String.class, String.class, String.class, String.class },
                "Loading NNUE", "Reading weights", "crtk-halfkp.nnue", START_FEN);
        setField(panel, "loadingArch", "NNUE");
        setField(panel, "loadingFen", START_FEN);
        setField(panel, "runningArch", "NNUE");
        setField(panel, "runningFen", START_FEN);
        setField(panel, "inferenceWorker", new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                return null;
            }
        });
        assertFalse((Boolean) invoke(panel, "isLoadingActiveCard",
                new Class<?>[] { String.class }, "LC0 CNN"),
                "unrelated in-flight worker does not make the selected model show a loading card");
        invoke(panel, "dispose", new Class<?>[0]);
    }

    /**
     * Verifies the loading card can distinguish model-loading and inference
     * phases reported from the background worker.
     */
    private static void testNetworkLoadingCardShowsProviderPhase() {
        Theme.setMode(Theme.Mode.LIGHT);
        Object panel = construct(type("NetworkPanel"), new Class<?>[0]);
        Timer timer = (Timer) field(panel, "debounceTimer");
        timer.stop();
        Object loadingPanel = field(panel, "loadingPanel");
        invoke(loadingPanel, "start",
                new Class<?>[] { String.class, String.class, String.class, String.class },
                "Loading CNN - 10x128", "Preparing", "model", START_FEN);
        setField(panel, "loadingArch", "LC0 CNN");
        setField(panel, "loadingFen", START_FEN);

        Class<?> phaseType = type("RealActivations$Phase");
        invoke(panel, "updateLoadingPhase",
                new Class<?>[] { String.class, String.class, phaseType },
                "LC0 CNN", START_FEN, enumValue(phaseType, "LOADING_MODEL"));
        flushEdt();
        assertTrue(((String) field(loadingPanel, "title")).contains("Loading CNN"),
                "loading card reports model load phase");

        invoke(panel, "updateLoadingPhase",
                new Class<?>[] { String.class, String.class, phaseType },
                "LC0 CNN", START_FEN, enumValue(phaseType, "RUNNING_INFERENCE"));
        flushEdt();
        assertTrue(((String) field(loadingPanel, "title")).contains("Running CNN"),
                "loading card reports inference phase");

        invoke(panel, "updateLoadingPhase",
                new Class<?>[] { String.class, String.class, phaseType },
                "NNUE", START_FEN, enumValue(phaseType, "LOADING_MODEL"));
        flushEdt();
        assertTrue(((String) field(loadingPanel, "title")).contains("Running CNN"),
                "loading card ignores unrelated architecture phase");
        invoke(panel, "dispose", new Class<?>[0]);
    }

    /**
     * Counts selector items whose display text contains a token.
     *
     * @param combo selector to inspect
     * @param token required display-text token
     * @return matching item count
     */
    private static int countArchItems(JComboBox<?> combo, String token) {
        int count = 0;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (String.valueOf(combo.getItemAt(i)).contains(token)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Verifies activation providers surface fallback progress when a model
     * cannot be loaded.
     */
    private static void testRealActivationProgressReportsFallback() {
        Object provider = construct(type("RealActivations"), new Class<?>[0]);
        Path missing = Path.of("models/__missing-progress-test__.nnue");
        invoke(provider, "setNnuePath", new Class<?>[] { Path.class }, missing);
        Class<?> listenerType = type("RealActivations$ProgressListener");
        List<String> phases = new ArrayList<>();
        Object listener = Proxy.newProxyInstance(
                listenerType.getClassLoader(),
                new Class<?>[] { listenerType },
                (proxy, method, args) -> {
                    if ("onProgress".equals(method.getName())) {
                        phases.add(args[1].toString());
                    }
                    return null;
                });
        invoke(provider, "inferNnue", new Class<?>[] { String.class, listenerType }, START_FEN, listener);
        assertTrue(phases.contains("SYNTHETIC_FALLBACK"),
                "missing NNUE reports synthetic fallback progress");
    }

    /**
     * Flushes pending Swing event-dispatch work.
     */
    private static void flushEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> {
                // flush queued events
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while flushing EDT", ex);
        } catch (Exception ex) {
            throw new AssertionError("unable to flush EDT", ex);
        }
    }

    /**
     * Verifies Network-tab MCTS uses throttled SwingWorker publishing instead
     * of blocking the event thread for every streamed playout.
     */
    private static void testNetworkMctsUpdatesAreNonBlocking() {
        String source;
        try {
            source = Files.readString(Path.of("src/application/gui/workbench/network/NetworkPanel.java"),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("unable to read NetworkPanel source", ex);
        }
        assertFalse(source.contains("invokeAndWait"),
                "network MCTS does not block the EDT with invokeAndWait");
        assertFalse(source.contains("paintImmediately"),
                "network MCTS does not force synchronous repainting");
        assertTrue(source.contains("NETWORK_MCTS_PUBLISH_INTERVAL"),
                "network MCTS has a publish throttle");
    }

    /**
     * Verifies Network runtime diagnostics expose model/GPU/config status and
     * apply readable TOML token coloring to the config preview.
     */
    private static void testNetworkDiagnosticsPreviewHighlightsConfig() {
        Object panel = construct(type("NetworkDiagnosticsPanel"), new Class<?>[0]);
        Object provider = construct(type("RealActivations"), new Class<?>[0]);
        invoke(panel, "refresh", new Class<?>[] { type("RealActivations"), String.class },
                provider, "NNUE");

        JTextPane configPane = (JTextPane) field(panel, "configPane");
        String text = configPane.getText();
        assertTrue(text.contains("protocol-path"),
                "diagnostics config preview shows CLI config text");
        int keyOffset = text.indexOf("protocol-path");
        AttributeSet keyAttrs = configPane.getStyledDocument()
                .getCharacterElement(keyOffset).getAttributes();
        assertTrue(!Objects.equals(StyleConstants.getForeground(keyAttrs), themeColor("TEXT")),
                "diagnostics config keys are color-coded");

        int commentOffset = text.indexOf('#');
        assertTrue(commentOffset >= 0, "diagnostics config preview includes comments");
        AttributeSet commentAttrs = configPane.getStyledDocument()
                .getCharacterElement(commentOffset).getAttributes();
        assertTrue(StyleConstants.isItalic(commentAttrs),
                "diagnostics config comments are styled differently");
        assertPaintsOpaqueCorner((JComponent) panel, 380, 680,
                "network diagnostics paints opaquely");
    }

    /**
     * Verifies the diagnostics config preview reapplies syntax colors after a
     * runtime palette switch.
     */
    private static void testNetworkDiagnosticsPreviewRecolorsForDarkTheme() {
        Theme.setMode(Theme.Mode.LIGHT);
        JComponent panel = (JComponent) construct(type("NetworkDiagnosticsPanel"),
                new Class<?>[0]);
        Object provider = construct(type("RealActivations"), new Class<?>[0]);
        invoke(panel, "refresh", new Class<?>[] { type("RealActivations"), String.class },
                provider, "NNUE");

        JTextPane configPane = (JTextPane) field(panel, "configPane");
        String text = configPane.getText();
        int keyOffset = text.indexOf("protocol-path");
        assertTrue(keyOffset >= 0, "diagnostics config has a key to inspect");

        Theme.setMode(Theme.Mode.DARK);
        Theme.refreshComponentTree(panel);
        AttributeSet keyAttrs = configPane.getStyledDocument()
                .getCharacterElement(keyOffset).getAttributes();
        assertEquals(themeColor("ACCENT"), StyleConstants.getForeground(keyAttrs),
                "diagnostics config key recolors to dark accent");
        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies the refreshed NNUE visual modes paint real synthetic data and
     * fill the viewport width instead of leaving a narrow atlas strip.
     */
    private static void testNnueViewsPaintSyntheticSnapshotHeadlessly() {
        Class<?> viewType = type("NnueView");
        Class<?> snapshotType = type("ActivationSnapshot");
        Class<?> baseType = type("NetworkView");
        Class<?> modeType = type("ViewMode");
        Object view = construct(viewType, new Class<?>[0]);
        Object snapshot = construct(snapshotType, new Class<?>[0]);
        invokeStatic(type("SyntheticActivations"), "fillNnue",
                new Class<?>[] { String.class, snapshotType }, START_FEN, snapshot);
        invoke(snapshot, "seal", new Class<?>[0]);
        invokeOn(baseType, view, "setFen", new Class<?>[] { String.class }, START_FEN);
        invokeOn(baseType, view, "setSnapshot", new Class<?>[] { snapshotType }, snapshot);

        invokeOn(baseType, view, "setViewMode", new Class<?>[] { modeType }, enumValue(modeType, "ABSTRACT"));
        assertPaintsOpaqueCorner((JComponent) view, 1200, 720,
                "NNUE overview paints synthetic snapshot");
        invokeOn(baseType, view, "setViewMode", new Class<?>[] { modeType }, enumValue(modeType, "DETAILED"));
        assertPaintsOpaqueCorner((JComponent) view, 1200, 720,
                "NNUE trace paints synthetic snapshot");
        assertTrue(((Scrollable) view).getScrollableTracksViewportHeight(),
                "NNUE trace fits the viewport height");
        assertTrue(((JComponent) view).getPreferredSize().height <= 720,
                "NNUE trace does not request a tall scroll canvas");
        invokeOn(baseType, view, "setViewMode", new Class<?>[] { modeType }, enumValue(modeType, "RAW"));
        assertPaintsOpaqueCorner((JComponent) view, 1200, 720,
                "NNUE all-neurons view paints synthetic snapshot");
        invokeOn(baseType, view, "setViewMode", new Class<?>[] { modeType }, enumValue(modeType, "ATLAS"));
        assertPaintsOpaqueCorner((JComponent) view, 1200, 900,
                "NNUE atlas paints synthetic snapshot");
        assertTrue(((Scrollable) view).getScrollableTracksViewportWidth(),
                "NNUE atlas tracks viewport width");
        assertFalse(((Scrollable) view).getScrollableTracksViewportHeight(),
                "NNUE atlas uses a scroll canvas for the whole pixel-plane overview");
        assertTrue(((JComponent) view).getPreferredSize().height > 900,
                "NNUE atlas leaves enough vertical room for the wrapped pixel-plane overview");
        Object atlasFrame = field(view, "atlasPaintFrame");
        Object atlasRaster = field(atlasFrame, "wholePlaneImage");
        assertTrue(atlasRaster instanceof BufferedImage,
                "NNUE atlas caches the dense whole pixel-plane raster");
        assertPaintsOpaqueCorner((JComponent) view, 1200, 900,
                "NNUE atlas repaints synthetic snapshot from cache");
        Object repaintedFrame = field(view, "atlasPaintFrame");
        assertTrue(atlasFrame == repaintedFrame,
                "NNUE atlas reuses cached render frame across repeated paints");
        assertTrue(atlasRaster == field(repaintedFrame, "wholePlaneImage"),
                "NNUE atlas reuses cached dense raster across repeated paints");
        String atlasTip = ((JComponent) view).getToolTipText(new MouseEvent((JComponent) view,
                MouseEvent.MOUSE_MOVED, 0L, 0, 600, 200, 0, false, MouseEvent.NOBUTTON));
        assertTrue(atlasTip != null && atlasTip.contains("whole atlas"),
                "NNUE atlas exposes a whole-atlas pixel-plane overview");
    }

    /**
     * Verifies Trace mode centers feature/slot columns inside the visible graph
     * pane so changing active-feature counts during MCTS does not leave rows
     * anchored awkwardly at the top.
     */
    private static void testNnueTraceFitsViewportAndCentersColumns() {
        Class<?> viewType = type("NnueView");
        Class<?> snapshotType = type("ActivationSnapshot");
        Class<?> baseType = type("NetworkView");
        Class<?> modeType = type("ViewMode");
        Object view = construct(viewType, new Class<?>[0]);
        Object snapshot = construct(snapshotType, new Class<?>[0]);
        invokeStatic(type("SyntheticActivations"), "fillNnue",
                new Class<?>[] { String.class, snapshotType }, START_FEN, snapshot);
        invoke(snapshot, "seal", new Class<?>[0]);
        invokeOn(baseType, view, "setFen", new Class<?>[] { String.class }, START_FEN);
        invokeOn(baseType, view, "setSnapshot", new Class<?>[] { snapshotType }, snapshot);
        invokeOn(baseType, view, "setViewMode", new Class<?>[] { modeType }, enumValue(modeType, "DETAILED"));

        JComponent component = (JComponent) view;
        component.setSize(1200, 720);
        Rectangle body = new Rectangle(12, 64, 1176, 644);
        int boardSide = 280;
        int graphTop = body.y + 48 + 92 + 22;
        Rectangle wire = new Rectangle(body.x, graphTop,
                body.width - boardSide - 20, body.height - (graphTop - body.y));
        Object layout = invoke(view, "layout", new Class<?>[] { Rectangle.class }, wire);
        int featureStart = (Integer) field(layout, "featureStartY");
        int featureBottom = (Integer) field(layout, "featureBottomY");
        int slotStart = (Integer) field(layout, "startY");
        int slotBottom = (Integer) field(layout, "bottomY");
        int featureCx = (Integer) field(layout, "featureCx");
        int accumCx = (Integer) field(layout, "accumCx");
        int clippedCx = (Integer) field(layout, "clippedCx");
        int contribCx = (Integer) field(layout, "contribCx");
        int outputCx = (Integer) field(layout, "outputCx");
        int graphContentTop = wire.y + 40;
        int graphContentBottom = wire.y + wire.height - 28;
        assertTrue(featureStart >= graphContentTop, "feature column starts inside visible graph");
        assertTrue(featureBottom <= graphContentBottom, "feature column ends inside visible graph");
        assertTrue(slotStart >= graphContentTop, "slot column starts inside visible graph");
        assertTrue(slotBottom <= graphContentBottom, "slot column ends inside visible graph");
        assertTrue(featureCx < accumCx && accumCx < clippedCx && clippedCx < contribCx && contribCx < outputCx,
                "trace lays out trunk columns left-to-right");
        assertTrue(outputCx + 100 <= wire.x + wire.width,
                "trace output head leaves room for its value bar before the board");
        assertTrue(featureStart > graphContentTop || slotStart > graphContentTop,
                "trace columns are centered when there is spare height");
    }

    /**
     * Verifies workbench HalfKP labels use the same 10x64 stride as the real
     * NNUE feature encoder and mirror black-perspective squares back onto the
     * displayed board.
     */
    private static void testNnueHalfKpDecodingUsesFeatureEncoderLayout() {
        Class<?> viewType = type("NnueView");
        int whiteFeature = FeatureEncoder.encodeFeature(4, FeatureEncoder.OWN_KNIGHT, 10);
        assertEquals("Ke1 / Nc2",
                invokeStatic(viewType, "decodeHalfKP",
                        new Class<?>[] { int.class, boolean.class }, whiteFeature, true),
                "white-perspective HalfKP decode");
        assertEquals("Ke8 / Nc7",
                invokeStatic(viewType, "decodeHalfKP",
                        new Class<?>[] { int.class, boolean.class }, whiteFeature, false),
                "black-perspective HalfKP decode mirrors board squares");

        Class<?> snapshotType = type("ActivationSnapshot");
        Object snapshot = construct(snapshotType, new Class<?>[0]);
        invokeStatic(type("SyntheticActivations"), "fillNnue",
                new Class<?>[] { String.class, snapshotType }, START_FEN, snapshot);
        assertSyntheticFeatureBounds(snapshot, "nnue.features.us.indices");
        assertSyntheticFeatureBounds(snapshot, "nnue.features.them.indices");
        assertTrue(data(snapshot, "nnue.output.contribution.total").length > 0,
                "synthetic NNUE emits total contribution");
        assertTrue(data(snapshot, "nnue.stockfish.fc0.raw").length > 0,
                "synthetic NNUE emits Stockfish-shaped FC0 data");
        assertTrue(data(snapshot, "nnue.stockfish.fc0.weights.fwd.us").length > 0,
                "synthetic NNUE emits Stockfish FC0 forward weights");
        assertTrue(data(snapshot, "nnue.stockfish.fc0.fwd.cp").length == 1,
                "synthetic NNUE emits Stockfish FC0 forward contribution");
        assertTrue(data(snapshot, "nnue.stockfish.fc1.clipped").length > 0,
                "synthetic NNUE emits Stockfish-shaped FC1 data");
        assertTrue(data(snapshot, "nnue.stockfish.output.parts").length == 4,
                "synthetic NNUE emits Stockfish output decomposition");
    }

    /**
     * Verifies Trace mode ranks slots by the net us+them output contribution,
     * keeps the accumulator column focused, and keeps active feature rows in
     * natural lane order so the left column does not reshuffle during search.
     */
    private static void testNnueTraceRanksCombinedContributionsAndShowsAllFeatures() {
        Class<?> viewType = type("NnueView");
        Class<?> snapshotType = type("ActivationSnapshot");
        Class<?> baseType = type("NetworkView");
        Object view = construct(viewType, new Class<?>[0]);
        Object snapshot = construct(snapshotType, new Class<?>[0]);
        int hidden = 40;
        int features = 20;
        float[] us = new float[hidden];
        float[] them = new float[hidden];
        us[1] = 5.0f;
        them[35] = -9.0f;
        put(snapshot, "nnue.output.contribution.us", new int[] { hidden }, us);
        put(snapshot, "nnue.output.contribution.them", new int[] { hidden }, them);
        put(snapshot, "nnue.features.us.impact", new int[] { features }, range(features, 1.0f));
        invokeOn(baseType, view, "setSnapshot", new Class<?>[] { snapshotType }, snapshot);

        int[] visibleSlots = (int[]) field(view, "visibleSlots");
        int[] visibleFeatures = (int[]) field(view, "visibleFeatures");
        assertEquals(Integer.valueOf(35), Integer.valueOf(visibleSlots[0]),
                "Trace slot ranking uses combined contribution");
        assertEquals(Integer.valueOf(32), Integer.valueOf(visibleSlots.length),
                "Trace keeps top accumulator slots focused");
        assertEquals(Integer.valueOf(features), Integer.valueOf(visibleFeatures.length),
                "Trace shows every active feature");
        assertEquals(Integer.valueOf(0), Integer.valueOf(visibleFeatures[0]),
                "Trace keeps active features in stable lane order");
        assertEquals(Integer.valueOf(features - 1), Integer.valueOf(visibleFeatures[features - 1]),
                "Trace does not rank feature lanes by impact");
    }

    /**
     * Verifies gathered trace values can be inspected as computed inline data
     * instead of pretending they are a contiguous tensor slice.
     */
    private static void testNnueTraceInlineInspectorShowsGatheredColumn() {
        Object regions = construct(type("HitRegions"), new Class<?>[0]);
        Rectangle bounds = new Rectangle(0, 0, 20, 20);
        invoke(regions, "addInline",
                new Class<?>[] { Rectangle.class, String.class, String.class,
                        String.class, float[].class, String.class },
                bounds, "computed slot column", "gathered values", "2 rows",
                new float[] { 1.25f, -2.5f }, "2x1");
        Object region = invoke(regions, "hitTest", new Class<?>[] { int.class, int.class }, 5, 5);
        Object panel = construct(type("InspectorPanel"), new Class<?>[0]);
        invoke(panel, "inspect",
                new Class<?>[] { region.getClass(), type("ActivationSnapshot") },
                region, null);
        JTextArea dataArea = (JTextArea) field(panel, "dataArea");
        String text = dataArea.getText();
        assertTrue(text.contains("+1.25000"), "inline inspector shows first gathered value");
        assertTrue(text.contains("-2.50000"), "inline inspector shows second gathered value");
    }

    /**
     * Verifies synthetic NNUE feature indices stay inside the real encoder's
     * feature domain.
     *
     * @param snapshot snapshot
     * @param key tensor key
     */
    private static void assertSyntheticFeatureBounds(Object snapshot, String key) {
        float[] values = data(snapshot, key);
        assertTrue(values.length > 0, key + " populated");
        assertTrue(values.length <= FeatureEncoder.MAX_ACTIVE_FEATURES,
                key + " does not exceed legal active feature count");
        for (float value : values) {
            int feature = Math.round(value);
            assertTrue(feature >= 0 && feature < FeatureEncoder.FEATURE_COUNT,
                    key + " feature in encoder domain");
        }
    }

    /**
     * Stores one activation tensor in a reflected snapshot.
     *
     * @param snapshot snapshot
     * @param key key
     * @param shape shape
     * @param values values
     */
    private static void put(Object snapshot, String key, int[] shape, float[] values) {
        invoke(snapshot, "put", new Class<?>[] { String.class, int[].class, float[].class },
                key, shape, values);
    }

    /**
     * Reads one activation tensor from a reflected snapshot.
     *
     * @param snapshot snapshot
     * @param key key
     * @return values
     */
    private static float[] data(Object snapshot, String key) {
        return (float[]) invoke(snapshot, "data", new Class<?>[] { String.class }, key);
    }

    /**
     * Builds a simple descending range used by synthetic trace tests.
     *
     * @param length length
     * @param start first value
     * @return range
     */
    private static float[] range(int length, float start) {
        float[] values = new float[length];
        for (int i = 0; i < length; i++) {
            values[i] = start + length - i;
        }
        return values;
    }

    /**
     * Verifies CNN and BT4 have real atlas renderers, backed by synthetic
     * activation snapshots.
     */
    private static void testCnnAndBt4AtlasPaintSyntheticSnapshotsHeadlessly() {
        Class<?> snapshotType = type("ActivationSnapshot");
        Class<?> baseType = type("NetworkView");
        Class<?> modeType = type("ViewMode");
        Object atlas = enumValue(modeType, "ATLAS");

        Object cnnView = construct(type("CnnView"), new Class<?>[0]);
        Object cnnSnapshot = construct(snapshotType, new Class<?>[0]);
        invokeStatic(type("SyntheticActivations"), "fillCnn",
                new Class<?>[] { String.class, snapshotType }, START_FEN, cnnSnapshot);
        invoke(cnnSnapshot, "seal", new Class<?>[0]);
        invokeOn(baseType, cnnView, "setFen", new Class<?>[] { String.class }, START_FEN);
        invokeOn(baseType, cnnView, "setSnapshot", new Class<?>[] { snapshotType }, cnnSnapshot);
        invokeOn(baseType, cnnView, "setViewMode", new Class<?>[] { modeType }, atlas);
        assertPaintsOpaqueCorner((JComponent) cnnView, 1240, 760,
                "CNN atlas paints synthetic snapshot");

        Object bt4View = construct(type("Bt4View"), new Class<?>[0]);
        Object bt4Snapshot = construct(snapshotType, new Class<?>[0]);
        invokeStatic(type("SyntheticActivations"), "fillBt4",
                new Class<?>[] { String.class, snapshotType }, START_FEN, bt4Snapshot);
        invoke(bt4Snapshot, "seal", new Class<?>[0]);
        invokeOn(baseType, bt4View, "setFen", new Class<?>[] { String.class }, START_FEN);
        invokeOn(baseType, bt4View, "setSnapshot", new Class<?>[] { snapshotType }, bt4Snapshot);
        invokeOn(baseType, bt4View, "setViewMode", new Class<?>[] { modeType }, atlas);
        assertPaintsOpaqueCorner((JComponent) bt4View, 1240, 760,
                "BT4 atlas paints synthetic snapshot");
    }

    /**
     * Verifies the workbench CNN path captures real model activations when the
     * local CNN weights file is installed.
     */
    private static void testWorkbenchCnnUsesRealWeightsWhenAvailable() {
        Path weights = Path.of("models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin");
        if (!Files.exists(weights)) {
            return;
        }
        Object provider = construct(type("RealActivations"), new Class<?>[0]);
        Object snapshot = invoke(provider, "inferCnn", new Class<?>[] { String.class }, START_FEN);
        assertEquals("real inference", invoke(provider, "statusFor", new Class<?>[] { String.class }, "cnn"),
                "CNN workbench status uses real weights");
        float[] capturedInput = (float[]) invoke(snapshot, "data", new Class<?>[] { String.class }, "cnn.input");
        float[] encodedInput = chess.nn.lc0.cnn.Encoder.encode(new Position(START_FEN));
        assertFloatArrayExact(encodedInput, capturedInput, "CNN captured input planes");
        int[] blockShape = (int[]) invoke(snapshot, "shape", new Class<?>[] { String.class }, "cnn.block0.relu");
        assertEquals(Integer.valueOf(3), Integer.valueOf(blockShape.length), "CNN block shape rank");
        assertEquals(Integer.valueOf(128), Integer.valueOf(blockShape[0]), "CNN real trunk channels");
        assertEquals(Integer.valueOf(8), Integer.valueOf(blockShape[1]), "CNN block board rows");
        assertEquals(Integer.valueOf(8), Integer.valueOf(blockShape[2]), "CNN block board columns");
    }

    /**
     * Verifies the workbench PUCT search produces root child rows and a legal
     * best move from the standard start position.
     */
    private static void testMctsSearchBuildsRootRows() {
        Object search = construct(type("mcts.MctsSearch"),
                new Class<?>[] { Position.class, double.class },
                new Position(START_FEN), 1.25);
        for (int i = 0; i < 40; i++) {
            invoke(search, "iterate", new Class<?>[0]);
        }
        Object snapshot = invoke(search, "snapshot", new Class<?>[] { boolean.class }, false);
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) invoke(snapshot, "rows", new Class<?>[0]);
        assertTrue(!rows.isEmpty(), "MCTS snapshot has root rows");
        short bestMove = (Short) invoke(snapshot, "bestMove", new Class<?>[0]);
        assertTrue(new Position(START_FEN).isLegalMove(bestMove), "MCTS best move is legal");
    }

    /**
     * Verifies the workbench MCTS proves root mate-in-one from expanded
     * terminal children before any PUCT playout is sampled.
     */
    private static void testMctsSearchMateInOneUsesCliShortcut() {
        Class<?> searchType = type("mcts.MctsSearch");
        Object search = construct(searchType,
                new Class<?>[] { Position.class, double.class },
                new Position(MATE_IN_ONE_FEN), 1.25);
        Object snapshot = invoke(search, "snapshot", new Class<?>[] { boolean.class }, false);
        assertEquals("g6g7",
                Move.toString((Short) invoke(snapshot, "bestMove", new Class<?>[0])),
                "MCTS mate-in-one shortcut best move");
        assertEquals(Long.valueOf(0L),
                invoke(snapshot, "playouts", new Class<?>[0]),
                "MCTS mate-in-one shortcut uses no playouts");
        assertFalse((Boolean) invoke(search, "shouldContinue",
                new Class<?>[] { long.class, long.class }, 100L, 0L),
                "MCTS mate-in-one shortcut stops the worker loop");
        invoke(search, "iterate", new Class<?>[0]);
        snapshot = invoke(search, "snapshot", new Class<?>[] { boolean.class }, false);
        assertEquals(Long.valueOf(0L),
                invoke(snapshot, "playouts", new Class<?>[0]),
                "MCTS mate-in-one iterate remains a no-op");
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) invoke(snapshot, "rows", new Class<?>[0]);
        assertTrue(!rows.isEmpty(), "MCTS mate-in-one snapshot still lists root rows");
        assertEquals("g6g7",
                invoke(rows.get(0), "uci", new Class<?>[0]),
                "MCTS mate-in-one row is pinned first");
        assertTrue(((Integer) invoke(snapshot, "rootCentipawns", new Class<?>[0])) > 0,
                "MCTS mate-in-one shortcut reports a winning root value");
        assertEquals("#1",
                invoke(snapshot, "rootScoreLabel", new Class<?>[0]),
                "MCTS mate-in-one shortcut reports mate label");
        invoke(search, "close", new Class<?>[0]);
    }

    /**
     * Verifies deeper root mates use the same bounded proof shortcut as the CLI.
     */
    private static void testMctsSearchForcedMateProofOverridesVisits() {
        Class<?> searchType = type("mcts.MctsSearch");
        Object search = construct(searchType,
                new Class<?>[] { Position.class, double.class },
                new Position(FORCED_MATE_IN_FOUR_FEN), 1.25);
        Object snapshot = invoke(search, "snapshot", new Class<?>[] { boolean.class }, false);
        assertEquals(Long.valueOf(0L),
                invoke(snapshot, "playouts", new Class<?>[0]),
                "deeper forced mate starts with no playouts");
        assertFalse((Boolean) invoke(search, "shouldContinue",
                new Class<?>[] { long.class, long.class }, 2_000L, 0L),
                "deeper forced mate root proof stops the worker loop");
        assertEquals("#4",
                invoke(snapshot, "rootScoreLabel", new Class<?>[0]),
                "deeper forced mate root proof reports mate label");
        invoke(search, "close", new Class<?>[0]);
    }

    /**
     * Verifies LC0-style terminal proof propagation reaches the reported mate in
     * four and pins it ahead of visit/Q ordering.
     */
    private static void testMctsSearchForcedMateInFourProofOverridesVisits() {
        Class<?> searchType = type("mcts.MctsSearch");
        Object search = construct(searchType,
                new Class<?>[] { Position.class, double.class },
                new Position(FORCED_MATE_IN_FOUR_FEN), 1.25);
        Object snapshot = invoke(search, "snapshot", new Class<?>[] { boolean.class }, false);
        assertEquals("c1c8",
                Move.toString((Short) invoke(snapshot, "bestMove", new Class<?>[0])),
                "MCTS forced mate-in-four proof best move");
        long playouts = ((Long) invoke(snapshot, "playouts", new Class<?>[0])).longValue();
        assertEquals(0L, playouts, "MCTS forced mate-in-four root proof uses no playouts");
        assertEquals("#4",
                invoke(snapshot, "rootScoreLabel", new Class<?>[0]),
                "MCTS forced mate-in-four proof reports mate label");
        assertFalse((Boolean) invoke(search, "shouldContinue",
                new Class<?>[] { long.class, long.class }, 100L, 0L),
                "MCTS forced mate-in-four proof stops the worker loop");
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) invoke(snapshot, "rows", new Class<?>[0]);
        assertTrue(!rows.isEmpty(), "MCTS forced mate-in-four proof snapshot still lists root rows");
        assertEquals("c1c8",
                invoke(rows.get(0), "uci", new Class<?>[0]),
                "MCTS forced mate-in-four proof row is pinned first");
        assertTrue(((String) invoke(snapshot, "bestPvText", new Class<?>[0])).contains("#"),
                "MCTS forced mate-in-four proof PV reaches mate");
        invoke(search, "close", new Class<?>[0]);
    }

    /**
     * Verifies MCTS terminal values use side-to-move perspective and automatic
     * static draws do not expand as live tree nodes.
     */
    private static void testMctsSearchTerminalAndDrawHandling() {
        Class<?> searchType = type("mcts.MctsSearch");
        Object search = construct(searchType,
                new Class<?>[] { Position.class, double.class },
                new Position(START_FEN), 1.25);
        double mateValue = (Double) invoke(search, "evaluate",
                new Class<?>[] { Position.class },
                new Position("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1"));
        assertTrue(mateValue <= -0.999, "MCTS evaluates mated side as terminal loss");
        double stalemateValue = (Double) invoke(search, "evaluate",
                new Class<?>[] { Position.class },
                new Position("7k/5Q2/5K2/8/8/8/8/8 b - - 0 1"));
        assertTrue(Math.abs(stalemateValue) < 1e-9, "MCTS evaluates stalemate as draw");
        double leafMateValue = (Double) invoke(search, "evaluate",
                new Class<?>[] { Position.class },
                new Position(MATE_IN_ONE_FEN));
        assertTrue(leafMateValue >= 0.999, "MCTS leaf quiescence sees mate in one");
        double captureValue = (Double) invoke(search, "evaluate",
                new Class<?>[] { Position.class },
                new Position("7k/8/4q3/8/2B5/8/8/4K3 w - - 0 1"));
        assertTrue(captureValue > -0.05, "MCTS leaf quiescence resolves a hanging queen capture");

        Position drawRoot = new Position("4k3/8/8/8/8/8/8/R3K3 w - - 100 75");
        double fiftyMoveValue = (Double) invoke(search, "evaluate",
                new Class<?>[] { Position.class }, drawRoot);
        assertTrue(Math.abs(fiftyMoveValue) < 1e-9, "MCTS evaluates 50-move positions as draw");
        Object drawSearch = construct(searchType,
                new Class<?>[] { Position.class, double.class }, drawRoot, 1.25);
        Object snapshot = invoke(drawSearch, "snapshot", new Class<?>[] { boolean.class }, false);
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) invoke(snapshot, "rows", new Class<?>[0]);
        assertEquals(Integer.valueOf(0), Integer.valueOf(rows.size()),
                "MCTS does not expand 50-move draw roots");
        invoke(drawSearch, "iterate", new Class<?>[0]);
        snapshot = invoke(drawSearch, "snapshot", new Class<?>[] { boolean.class }, false);
        short drawBestMove = (Short) invoke(snapshot, "bestMove", new Class<?>[0]);
        assertTrue(drawBestMove != Move.NO_MOVE && drawRoot.isLegalMove(drawBestMove),
                "MCTS draw root returns the CLI fallback best move");
        assertEquals(Integer.valueOf(0),
                invoke(snapshot, "rootCentipawns", new Class<?>[0]),
                "MCTS draw root backs up neutral value");
    }

    /**
     * Verifies the workbench MCTS can preserve an already-searched child tree
     * when the root advances to that position.
     */
    private static void testMctsSearchReusesRootSubtree() {
        Class<?> searchType = type("mcts.MctsSearch");
        Object search = construct(searchType,
                new Class<?>[] { Position.class, double.class },
                new Position(START_FEN), 1.25);
        for (int i = 0; i < 80; i++) {
            invoke(search, "iterate", new Class<?>[0]);
        }
        Object snapshot = invoke(search, "snapshot", new Class<?>[] { boolean.class }, false);
        short bestMove = (Short) invoke(snapshot, "bestMove", new Class<?>[0]);
        Position child = new Position(START_FEN).play(bestMove);
        boolean reused = (Boolean) invoke(search, "reuseRoot",
                new Class<?>[] { Position.class }, child);
        assertTrue(reused, "MCTS reuses searched child subtree");
        snapshot = invoke(search, "snapshot", new Class<?>[] { boolean.class }, false);
        assertEquals(child.toString(), invoke(snapshot, "rootFen", new Class<?>[0]),
                "MCTS root FEN advances after subtree reuse");
        @SuppressWarnings("unchecked")
        java.util.Map<Long, ?> table = (java.util.Map<Long, ?>) field(search, "transpositions");
        assertTrue(!table.isEmpty(), "MCTS hash table stores position stats");
        invoke(search, "close", new Class<?>[0]);

        Object deeperSearch = construct(searchType,
                new Class<?>[] { Position.class, double.class },
                new Position(START_FEN), 1.25);
        invoke(deeperSearch, "iterate", new Class<?>[0]);
        Object root = field(deeperSearch, "root");
        @SuppressWarnings("unchecked")
        List<Object> rootChildren = (List<Object>) field(root, "children");
        Object grandchild = null;
        for (Object rootChild : rootChildren) {
            @SuppressWarnings("unchecked")
            List<Object> childChildren = (List<Object>) field(rootChild, "children");
            if (!childChildren.isEmpty()) {
                grandchild = childChildren.get(0);
                break;
            }
        }
        assertTrue(grandchild != null, "MCTS search expanded a descendant for reuse");
        Position grandchildPosition = ((Position) field(grandchild, "position")).copy();
        boolean reusedGrandchild = (Boolean) invoke(deeperSearch, "reuseRoot",
                new Class<?>[] { Position.class }, grandchildPosition);
        assertTrue(reusedGrandchild, "MCTS reuses deeper searched descendants");
        Object reusedRoot = field(deeperSearch, "root");
        assertEquals(Integer.valueOf(0), field(reusedRoot, "depth"),
                "MCTS rebases reused descendant root depth");
        @SuppressWarnings("unchecked")
        List<Object> reusedChildren = (List<Object>) field(reusedRoot, "children");
        if (!reusedChildren.isEmpty()) {
            assertEquals(Integer.valueOf(1), field(reusedChildren.get(0), "depth"),
                    "MCTS rebases reused descendant child depth");
        }
        invoke(deeperSearch, "close", new Class<?>[0]);
    }

    /**
     * Verifies the workbench MCTS rejects more playouts after close while still
     * keeping the final snapshot readable for UI teardown paths.
     */
    private static void testMctsSearchClosesBackend() {
        Object search = construct(type("mcts.MctsSearch"),
                new Class<?>[] { Position.class, double.class },
                new Position(START_FEN), 1.25);
        invoke(search, "iterate", new Class<?>[0]);
        invoke(search, "close", new Class<?>[0]);
        Object snapshot = invoke(search, "snapshot", new Class<?>[] { boolean.class }, false);
        assertEquals(START_FEN, invoke(snapshot, "rootFen", new Class<?>[0]),
                "MCTS snapshot remains readable after close");
        boolean failed = false;
        try {
            invoke(search, "iterate", new Class<?>[0]);
        } catch (IllegalStateException ex) {
            failed = true;
        }
        assertTrue(failed, "MCTS close prevents further iteration");
        invoke(search, "close", new Class<?>[0]);
    }

    /**
     * Verifies the MCTS panel builds its controls headlessly.
     */
    private static void testMctsPanelConstructsHeadlessly() {
        Object panel = construct(type("MctsPanel"), new Class<?>[0]);
        assertTrue(panel instanceof JComponent, "MCTS panel is a Swing component");
        JSpinner playouts = (JSpinner) field(panel, "playoutSpinner");
        assertEquals(staticField(type("Defaults"), "MCTS_VISITS"), playouts.getValue(),
                "MCTS panel uses shared visit default");
        invoke(panel, "setFen", new Class<?>[] { String.class }, START_FEN);
        JComponent component = (JComponent) panel;
        component.setSize(720, 560);
        paint(component, 720, 560);
        invoke(panel, "dispose", new Class<?>[0]);
    }

    /**
     * Verifies the Dashboard tab is the first tab in the workbench window.
     */
    private static void testDashboardTabIsFirst() {
        Class<?> window = type("Window");
        assertEquals(Integer.valueOf(0), staticField(window, "TAB_DASHBOARD"),
                "Dashboard is the first tab");
        assertEquals(Integer.valueOf(1), staticField(window, "TAB_ANALYZE"),
                "Analyze follows the Dashboard tab");
    }

    /**
     * Verifies the session keeps an ordered, per-ply evaluation history that
     * the latest sample overwrites and a new game clears.
     */
    private static void testSessionEvalHistory() {
        Object session = construct(type("Session"), new Class<?>[0]);
        invoke(session, "recordEval", new Class<?>[] { int.class, int.class }, 0, 35);
        invoke(session, "recordEval", new Class<?>[] { int.class, int.class }, 1, -120);
        invoke(session, "recordEval", new Class<?>[] { int.class, int.class }, 0, 60);
        int[] history = (int[]) invoke(session, "evalHistoryCentipawns", new Class<?>[0]);
        assertEquals(Integer.valueOf(2), Integer.valueOf(history.length), "eval history length");
        assertEquals(Integer.valueOf(60), Integer.valueOf(history[0]),
                "latest sample overwrites the ply");
        assertEquals(Integer.valueOf(-120), Integer.valueOf(history[1]),
                "eval history stays in ply order");
        invoke(session, "clearEvalHistory", new Class<?>[0]);
        assertEquals(Integer.valueOf(0),
                Integer.valueOf(((int[]) invoke(session, "evalHistoryCentipawns",
                        new Class<?>[0])).length),
                "new game clears eval history");
    }

    /**
     * Verifies the reusable mini chart paints both modes headlessly without
     * throwing.
     */
    private static void testMiniChartRendersHeadlessly() {
        Object chart = construct(type("MiniChart"), new Class<?>[0]);
        invoke(chart, "setLine", new Class<?>[] { float[].class },
                (Object) new float[] { 0.3f, -0.5f, 1.2f, 0.1f });
        invoke(chart, "setBars", new Class<?>[] { float[].class, Color[].class },
                new float[] { 0.2f, 0.9f, 0.5f },
                new Color[] { Color.GREEN, Color.RED, Color.GRAY });
        JComponent component = (JComponent) chart;
        component.setSize(140, 46);
        BufferedImage image = new BufferedImage(140, 46, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            component.paint(g);
        } finally {
            g.dispose();
        }
        assertTrue(component.getPreferredSize().height > 0, "mini chart has a compact height");
    }
}
