package testing;

import application.cli.PathOps;
import static testing.TestSupport.readUtf8;
import static testing.TestSupport.writeUtf8;
import static testing.WorkbenchTestSupport.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Predicate;

import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.game.Positions;
import application.gui.workbench.mcts.MctsPanel;
import application.gui.workbench.mcts.MctsSearch;
import application.gui.workbench.mcts.MctsSession;
import application.gui.workbench.mcts.MctsWorkspacePanel;
import application.gui.workbench.mcts.TreeGraphView;
import application.gui.workbench.mcts.TreeLayout;
import application.gui.workbench.mcts.TreePanel;
import application.gui.workbench.session.ArtifactIndex;
import application.gui.workbench.session.LogPanel;
import application.gui.workbench.network.NnueDrawing;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.HitRegions;
import application.gui.workbench.ui.RenderAcceleration;
import application.gui.workbench.ui.SegmentedSwitcher;
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
        testDesktopOpenRejectsMissingPath();
        testSoundServiceProceduralCueSettings();
        testRunManifestWritesReplayMetadata();
        testRunManifestAvoidsClobberingExistingFile();
        testJobHistoryIsBounded();
        testArtifactIndexNormalizesAndDeduplicatesPaths();
        testCommandResultParserSummaries();
        testDashboardPanelConstructsHeadlessly();
        testDashboardCenterUsesPanelSurface();
        testDashboardCardsGrowWithDynamicContent();
        testNetworkPanelSimpleControlsRenderHeadlessly();
        testNetworkLoadingCardTracksRequestedArchitecture();
        testNetworkLoadingCardShowsProviderPhase();
        testNetworkLoadingPanelIsTextOnly();
        testRealActivationProgressReportsFallback();
        testNetworkMctsFollowLeafUsesRenderBackpressure();
        testNetworkMctsPublishesWeightsBeforeLeafActivation();
        testNetworkMctsPublishesDistinctLiveFramesHeadlessly();
        testNetworkMctsUsesSelectedArchitectureBackend();
        testNetworkPositionPickerClearsLeafOverride();
        testNetworkDiagnosticsPreviewHighlightsConfig();
        testNetworkDiagnosticsPreviewRecolorsForDarkTheme();
        testRenderAccelerationProvidesCompatibleImages();
        testSyntheticFeaturePickerReturnsDistinctIndices();
        testNnueStackSummaryStaysCompact();
        testNnueViewsPaintSyntheticSnapshotHeadlessly();
        testNnueContributorLedgerUsesReadableRows();
        testNnueContributorBarUsesNeutralTrackAndBrightFill();
        testNnueTraceFitsViewportAndCentersColumns();
        testNnueRawUsesStableFeatureLanes();
        testNnueHalfKpDecodingUsesFeatureEncoderLayout();
        testNetworkBoardOrientationFollowsSideToMove();
        testNetworkBoardSectionSuppressesSmallLabelsAndKeepsHitOrientation();
        testBt4DetailedBoardUsesSharedTriangleOverlay();
        testNnueForwardSkipUsesNormalLineStyle();
        testNnueTraceRanksCombinedContributionsAndShowsAllFeatures();
        testNnueTraceInlineInspectorShowsGatheredColumn();
        testCnnAndBt4AtlasPaintSyntheticSnapshotsHeadlessly();
        testWorkbenchCnnUsesRealWeightsWhenAvailable();
        testWorkbenchOtisShowsArchitectureMetadata();
        testMctsSearchBuildsRootRows();
        testMctsSearchMateInOneUsesCliShortcut();
        testMctsSearchForcedMateProofOverridesVisits();
        testMctsSearchForcedMateInFourProofOverridesVisits();
        testMctsSearchTerminalAndDrawHandling();
        testMctsSearchReusesRootSubtree();
        testMctsSearchClosesBackend();
        testMctsTreeSnapshotCapsAndSelection();
        testMctsSessionLifecyclePublishesSnapshots();
        testMctsPanelInspectorUsesSolidSurface();
        testMctsWorkspaceDefaultsToTableAndBuildsGraphLazily();
        testTreePanelFollowsSessionSelection();
        testTreePanelGraphStyleSwitcherControlsDisplayMode();
        testTreeLayoutCarriesOmittedNodeCount();
        testTreeGraphLayerGuidesIncludeVerticalDividers();
        testTreeGraphLayerGuidesKeepShortBranchesAtDeeperLevel();
        testTreePanelUsesFixedSvgBoardSize();
        testTreeGraphBoardThumbnailsHaveNoBoardBorder();
        testTreeGraphMoveHighlightsArePixelAlignedFilledRectangles();
        testTreeGraphBoardCacheSeparatesLastMoveHighlights();
        testTreeGraphMovesDisplayModeUsesMoveCards();
        testTreeGraphShowsOmittedNodeBadge();
        testTreeSvgExportShowsOmittedNodeBadge();
        testTreeGraphSelectionRingWrapsFullCard();
        testTreeGraphSelectionRingStaysOutsideCaptionAtHighZoom();
        testTreeGraphSelectedPathDrawsGreenEdges();
        testTreeGraphSelectedPathUsesExactSegments();
        testTreeGraphNodeCaptionClipsOverflowText();
        // Full MCTS tab registration coverage is intentionally not part of
        // this Workbench pass; the Network tab still covers shared MCTS
        // controls.
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
            String text = readUtf8(log);
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
        String source = readUtf8(Path.of("src/application/gui/workbench/session/LogPanel.java"));
        String helper = readUtf8(Path.of("src/application/gui/workbench/session/DesktopOpen.java"));
        assertTrue(source.contains("DesktopOpen.open(path)"),
                "LogPanel delegates desktop opening to the shared helper");
        assertFalse(source.contains("\"===== "),
                "LogPanel avoids ASCII fence headers between log files");
        assertTrue(source.contains("appendSectionHeader(line)"),
                "LogPanel renders synthetic file headers through the styled console path");
        assertTrue(helper.contains("desktop.isSupported(Desktop.Action.OPEN)"),
                "DesktopOpen checks desktop OPEN action support");
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
     * Verifies procedural sound cues are centrally enumerated and settings are
     * clamped before persistence.
     */
    private static void testSoundServiceProceduralCueSettings() {
        int oldVolume = SoundService.volumePercent();
        boolean oldMuted = SoundService.isMuted();
        try {
            assertEquals(Integer.valueOf(23), Integer.valueOf(SoundCue.values().length),
                    "sound cue count");
            assertEquals(SoundCue.UI_CLICK, SoundCue.valueOf("UI_CLICK"),
                    "UI click cue present");
            assertEquals(SoundCue.POSITION_LOAD, SoundCue.valueOf("POSITION_LOAD"),
                    "position load cue present");
            assertEquals(SoundCue.CAPTURE, SoundCue.valueOf("CAPTURE"),
                    "capture cue present");
            assertEquals(SoundCue.PUZZLE_COMPLETE, SoundCue.valueOf("PUZZLE_COMPLETE"),
                    "puzzle complete cue present");
            assertEquals(SoundCue.MCTS_PROGRESS, SoundCue.valueOf("MCTS_PROGRESS"),
                    "MCTS progress cue present");
            int playbackLanes = (Integer) staticField(type("SoundService"), "MAX_SIMULTANEOUS_CUES");
            ThreadPoolExecutor executor =
                    (ThreadPoolExecutor) staticField(type("SoundService"), "EXECUTOR");
            assertTrue(playbackLanes > 1, "sound playback allows overlapping cues");
            assertEquals(Integer.valueOf(playbackLanes), Integer.valueOf(executor.getMaximumPoolSize()),
                    "sound playback lanes match active cue limit");
            int[] notifications = { 0 };
            Runnable listener = () -> notifications[0]++;
            SoundService.addSettingsListener(listener);
            SoundService.setMuted(!oldMuted);
            flushEdt();
            assertTrue(notifications[0] > 0, "sound settings listener notified");
            SoundService.removeSettingsListener(listener);
            SoundService.setVolumePercent(-50);
            assertEquals(Integer.valueOf(0), Integer.valueOf(SoundService.volumePercent()),
                    "sound volume clamps low");
            SoundService.setVolumePercent(150);
            assertEquals(Integer.valueOf(100), Integer.valueOf(SoundService.volumePercent()),
                    "sound volume clamps high");
            SoundService.setMuted(true);
            assertTrue(SoundService.isMuted(), "sound service can mute");
            SoundService.setMuted(false);
            assertFalse(SoundService.isMuted(), "sound service can unmute");
        } finally {
            SoundService.setVolumePercent(oldVolume);
            SoundService.setMuted(oldMuted);
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
        flushEdt();
        assertTrue(panel instanceof JComponent, "dashboard panel is a Swing component");
        assertTrue(((JComponent) panel).getComponentCount() > 0,
                "dashboard panel builds its cards");
        assertTrue(componentTreeHasLabelText((JComponent) panel, "Current Position"),
                "dashboard has a useful position card");
        assertTrue(componentTreeHasLabelText((JComponent) panel, "Engine Status"),
                "dashboard has an engine status card");
        assertTrue(componentTreeHasLabelText((JComponent) panel, "Health Checks"),
                "dashboard has actionable health checks");
        assertTrue(componentTreeHasLabelText((JComponent) panel, "Network Runtime"),
                "dashboard has shared network runtime diagnostics");
        Component diagnostics = componentTreeFindClass((JComponent) panel,
                "NetworkDiagnosticsPanel");
        assertTrue(diagnostics != null,
                "dashboard embeds the shared compact network diagnostics panel");
        assertFalse((Boolean) field(diagnostics, "includeConfigPreview"),
                "dashboard uses the compact diagnostics variant");
        JPanel modelRows = (JPanel) field(diagnostics, "modelRows");
        JPanel gpuRows = (JPanel) field(diagnostics, "gpuRows");
        JPanel cacheRows = (JPanel) field(diagnostics, "cacheRows");
        assertTrue(modelRows.getComponentCount() > 0,
                "dashboard diagnostics exposes model status");
        assertTrue(componentTreeHasLabelText(gpuRows, "Java2D"),
                "dashboard diagnostics exposes Java2D/backend status");
        assertTrue(componentTreeHasLabelText(cacheRows, "Activation"),
                "dashboard diagnostics exposes activation-cache status");
        assertTrue(componentTreeHasLabelText((JComponent) panel, "No recent jobs"),
                "dashboard uses the shared empty state for jobs");
        invoke(session, "updatePosition",
                new Class<?>[] { String.class, boolean.class, int.class, int.class, int.class },
                START_FEN, true, 0, 0, 20);
        invoke(session, "updateTags", new Class<?>[] { List.class },
                List.of("OPENING: name=\"Start\"", "MATERIAL: equal"));
        flushEdt();
        assertTrue(componentTreeHasLabelText((JComponent) panel, "Even"),
                "dashboard surfaces material status when tags provide it");
        assertPaintsOpaqueCorner((JComponent) panel, 1000, 760,
                "dashboard infographics paint opaquely");
    }

    /**
     * Verifies the Dashboard body uses the dark editor surface rather than a
     * lighter root-chrome viewport behind all cards.
     */
    private static void testDashboardCenterUsesPanelSurface() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            Object session = construct(type("Session"), new Class<?>[0]);
            Class<?> actionsType = type("DashboardActions");
            Object actions = Proxy.newProxyInstance(actionsType.getClassLoader(),
                    new Class<?>[] { actionsType }, (proxy, method, args) -> null);
            JComponent panel = (JComponent) construct(type("DashboardPanel"),
                    new Class<?>[] { type("Session"), actionsType }, session, actions);
            JScrollPane centerScroll = null;
            for (Component child : panel.getComponents()) {
                if (child instanceof JScrollPane scroll) {
                    centerScroll = scroll;
                    break;
                }
            }
            assertTrue(centerScroll != null, "dashboard has a center scroll pane");
            assertEquals(Theme.PANEL_SOLID, panel.getBackground(),
                    "dashboard panel uses editor surface");
            assertEquals(Theme.PANEL_SOLID, centerScroll.getViewport().getBackground(),
                    "dashboard center viewport uses editor surface");
            assertFalse(Objects.equals(Theme.BG, centerScroll.getViewport().getBackground()),
                    "dashboard center removes oversized root-chrome box");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Verifies dashboard cards keep their natural height cap after content
     * changes, so dynamic cards like Outputs do not clip newly-added rows.
     */
    private static void testDashboardCardsGrowWithDynamicContent() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(new JLabel("first"));
        JComponent card = (JComponent) invokeStatic(type("DashboardPanel"), "card",
                new Class<?>[] { String.class, JComponent.class }, "Dynamic", body);
        int initialHeight = card.getMaximumSize().height;
        for (int i = 0; i < 12; i++) {
            body.add(new JLabel("row " + i));
        }
        body.revalidate();
        int expandedHeight = card.getMaximumSize().height;
        assertTrue(expandedHeight > initialHeight,
                "dashboard card max height follows dynamic content");
    }

    /**
     * Returns whether a component tree contains a class with the given simple
     * name.
     *
     * @param component root component
     * @param simpleName class simple name
     * @return true when found
     */
    private static boolean componentTreeContainsClass(Component component, String simpleName) {
        return componentTreeFindClass(component, simpleName) != null;
    }

    /**
     * Returns the first component in a tree with the given simple class name.
     *
     * @param component root component
     * @param simpleName class simple name
     * @return matching component, or null
     */
    private static Component componentTreeFindClass(Component component, String simpleName) {
        if (component != null && simpleName.equals(component.getClass().getSimpleName())) {
            return component;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component match = componentTreeFindClass(child, simpleName);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    /**
     * Returns whether every scroll pane in the component subtree uses an opaque
     * viewport.
     *
     * @param component root component
     * @return true when all scroll viewports are opaque
     */
    private static boolean scrollViewportsAreOpaque(Component component) {
        if (component instanceof JScrollPane scroll
                && scroll.getViewport() != null
                && !scroll.getViewport().isOpaque()) {
            return false;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (!scrollViewportsAreOpaque(child)) {
                    return false;
                }
            }
        }
        return true;
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
        JComponent mctsToolbar = (JComponent) field(panel, "mctsToolbar");
        assertTrue(mctsToolbar.getParent() != null
                && !mctsToolbar.getParent().getClass().getName().contains("CollapsibleSection"),
                "one-line network MCTS toolbar is not wrapped in a collapsible section");
        JComponent mctsWeightsPanel = (JComponent) field(panel, "mctsWeightsPanel");
        assertFalse(mctsWeightsPanel.isVisible(),
                "network MCTS edge weights start collapsed");
        assertFalse((Boolean) invoke(mctsWeightsPanel, "isLeafBoardVisible", new Class<?>[0]),
                "network trace keeps a single position board");
        assertTrue(mctsWeightsPanel.getPreferredSize().height < 260,
                "network MCTS diagnostics stay compact without the duplicate board");
        assertFalse(((JComponent) field(panel, "detailsTabs")).isVisible(),
                "network inspector starts collapsed until data is selected");
        JComponent detailsTabs = (JComponent) field(panel, "detailsTabs");
        assertEquals(Integer.valueOf(2), Integer.valueOf(((javax.swing.JTabbedPane) detailsTabs).getTabCount()),
                "network details expose inspector plus trace legend");
        assertEquals("Inspector", ((javax.swing.JTabbedPane) detailsTabs).getTitleAt(0),
                "network details keep the inspector as the primary tab");
        assertEquals("Legend", ((javax.swing.JTabbedPane) detailsTabs).getTitleAt(1),
                "network details include trace legend tab");
        assertEquals(staticField(type("Defaults"), "MCTS_VISITS"), visits.getValue(),
                "network MCTS uses shared visit default");
        assertFalse(followLeaf.isSelected(), "network leaf following starts off");
        JComboBox<?> archCombo = (JComboBox<?>) field(panel, "archCombo");
        assertEquals(Integer.valueOf(5), Integer.valueOf(archCombo.getItemCount()),
                "evaluator selector exposes one entry per neural family plus the classical evaluator");
        assertEquals(Integer.valueOf(1), Integer.valueOf(countArchItems(archCombo, "NNUE")),
                "network selector exposes only one NNUE entry");
        assertEquals(Integer.valueOf(1), Integer.valueOf(countArchItems(archCombo, "Classical")),
                "evaluator selector exposes the classical evaluator");
        archCombo.setSelectedItem("NNUE - HalfKP");
        JComponent viewMode = (JComponent) field(panel, "viewMode");
        assertTrue(viewMode.getPreferredSize().width < 340,
                "view selector exposes only the simple modes");
        assertTrue(scrollViewportsAreOpaque((JComponent) panel),
                "network scroll panes keep opaque viewports to avoid repaint trails");
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
        archCombo.setSelectedItem("OTIS (Policy + WDL)");
        enabled = (boolean[]) field(viewMode, "segmentEnabled");
        assertTrue(enabled[2], "OTIS all-neurons segment enabled");
        assertTrue(enabled[3], "OTIS atlas segment enabled");
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
            /**
             * {@inheritDoc}
             */
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
     * Verifies the Network loading placeholder stays a quiet text-only
     * surface, not a separate animated card.
     */
    private static void testNetworkLoadingPanelIsTextOnly() {
        String source;
        try {
            source = Files.readString(Path.of("src/application/gui/workbench/network/LoadingPanel.java"),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("unable to read LoadingPanel source", ex);
        }
        assertFalse(source.contains("javax.swing.Timer"),
                "network loading placeholder does not run its own animation timer");
        assertFalse(source.contains("drawArc"),
                "network loading placeholder does not paint a spinner");
        assertFalse(source.contains("fillRoundRect"),
                "network loading placeholder does not paint a card chrome");
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
     * Lets the MCTS worker publish and the EDT process one live frame.
     */
    private static void sleepForLiveFramePoll() {
        try {
            Thread.sleep(8L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for live MCTS frames", ex);
        }
    }

    /**
     * Verifies Network-tab MCTS keeps heavy leaf inference off the EDT while
     * applying follow-leaf frames synchronously before the search advances.
     */
    private static void testNetworkMctsFollowLeafUsesRenderBackpressure() {
        String source;
        String viewSource;
        String windowBaseSource;
        try {
            source = Files.readString(Path.of("src/application/gui/workbench/network/NetworkPanel.java"),
                    StandardCharsets.UTF_8);
            viewSource = Files.readString(Path.of("src/application/gui/workbench/network/NetworkView.java"),
                    StandardCharsets.UTF_8);
            windowBaseSource = Files.readString(Path.of("src/application/gui/workbench/window/WindowBase.java"),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("unable to read network source", ex);
        }
        assertTrue(source.contains("NETWORK_MCTS_PUBLISH_INTERVAL"),
                "network MCTS has a publish throttle");
        assertTrue(source.contains("SwingWorker<Void, NetworkMctsFrame>"),
                "network MCTS publishes live tree frames");
        assertTrue(source.contains("NETWORK_MCTS_FRAME_YIELD_MS"),
                "network MCTS yields after streamed tree frames");
        assertTrue(source.contains("public void addNotify()")
                && source.contains("public void removeNotify()")
                && source.contains("setActive(false);"),
                "network panels activate only while attached to the visible hierarchy");
        assertTrue(source.contains("if (!active)")
                && source.contains("Thread.sleep(120L);")
                && source.contains("chunks.isEmpty() || !active"),
                "hidden network MCTS idles and skips EDT frame processing");
        assertTrue(source.contains("|| !active) {\n            return frame;"),
                "hidden network MCTS skips follow-leaf activation inference");
        assertTrue(source.contains("latestNetworkMctsFrameForDisplay"),
                "network MCTS coalesces published tree frames before EDT rendering");
        assertTrue(source.contains("boolean leafFrameDue = mctsFollowLeafEnabled;"),
                "follow-leaf mode visualizes every selected search leaf");
        assertTrue(source.contains("applyLeafFrameSynchronously(this, search, activationFrame);"),
                "network MCTS applies follow-leaf activations before advancing search");
        assertTrue(source.contains("SwingUtilities.invokeAndWait"),
                "network MCTS waits for the EDT to accept each follow-leaf frame");
        assertTrue(source.contains("paintImmediately"),
                "network MCTS flushes the current visible leaf frame");
        assertTrue(source.contains("buildNetworkMctsLeafActivationFrame"),
                "network MCTS still builds follow-leaf activations off the EDT");
        assertTrue(source.contains("buildNetworkMctsFrame"),
                "network MCTS builds live frames off the EDT");
        int previewLeaf = source.indexOf("search.previewNextLeaf(false)");
        int iterateLeaf = source.indexOf("search.iterate();");
        assertTrue(previewLeaf >= 0 && iterateLeaf > previewLeaf,
                "network MCTS publishes the selected leaf before evaluating it");
        assertTrue(source.contains("setCollapsibleExpanded(mctsWeightsSection, true)"),
                "network MCTS expands live edge weights when search starts");
        assertTrue(viewSource.contains("Dimension previousPreferred = getPreferredSize()")
                && viewSource.contains("if (!nextPreferred.equals(previousPreferred))"),
                "network views only revalidate when a snapshot changes layout size");
        assertFalse(windowBaseSource.contains("panel.setActive(true);"),
                "network tabs are not marked active before they are visible");
    }

    /**
     * Verifies Network-tab follow-leaf rendering publishes the tree frame
     * before doing expensive activation inference, so root-edge updates can
     * reach the EDT while the worker builds the neural snapshot.
     */
    private static void testNetworkMctsPublishesWeightsBeforeLeafActivation() {
        String source;
        try {
            source = Files.readString(Path.of("src/application/gui/workbench/network/NetworkPanel.java"),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("unable to read NetworkPanel source", ex);
        }
        int publishTree = source.indexOf("publishNetworkMctsFrame(frame, nextPlayout);");
        int buildActivation = source.indexOf("buildNetworkMctsLeafActivationFrame(frame)");
        int applyActivation = source.indexOf("applyLeafFrameSynchronously(this, search, activationFrame);");
        int iterate = source.indexOf("search.iterate();");
        assertTrue(publishTree >= 0 && buildActivation > publishTree,
                "network MCTS publishes tree weights before building leaf activation");
        assertTrue(applyActivation > buildActivation,
                "network MCTS applies activation frames after inference completes");
        assertTrue(iterate > applyActivation,
                "network MCTS shows the current leaf before evaluating it");
        int frameBuilder = source.indexOf("private NetworkMctsFrame buildNetworkMctsFrame");
        int activationBuilder = source.indexOf("private NetworkMctsFrame buildNetworkMctsLeafActivationFrame");
        String builderBody = source.substring(frameBuilder, activationBuilder);
        assertFalse(builderBody.contains("inferSnapshot("),
                "network MCTS frame builder does not block on model inference");
    }

    /**
     * Verifies live follow-leaf visualization produces multiple observable
     * intermediate frames during a bounded headless search, not only the initial
     * and final position.
     */
    private static void testNetworkMctsPublishesDistinctLiveFramesHeadlessly() {
        Object panel = construct(type("NetworkPanel"), new Class<?>[0]);
        Timer timer = (Timer) field(panel, "debounceTimer");
        timer.stop();
        JComboBox<?> archCombo = (JComboBox<?>) field(panel, "archCombo");
        archCombo.setSelectedItem("NNUE - HalfKP");
        JCheckBox followLeaf = (JCheckBox) field(panel, "mctsFollowLeafToggle");
        followLeaf.setSelected(true);
        JSpinner visits = (JSpinner) field(panel, "mctsVisitsSpinner");
        JSpinner millis = (JSpinner) field(panel, "mctsMillisSpinner");
        visits.setValue(Integer.valueOf(8));
        millis.setValue(Integer.valueOf(0));
        invoke(panel, "setActive", new Class<?>[] { boolean.class }, Boolean.TRUE);
        invoke(panel, "setFen", new Class<?>[] { String.class }, START_FEN);
        invoke(panel, "startNetworkMcts", new Class<?>[0]);
        LinkedHashSet<String> frames = new LinkedHashSet<>();
        try {
            long deadline = System.currentTimeMillis() + 8_000L;
            while (System.currentTimeMillis() < deadline && frames.size() <= 2) {
                flushEdt();
                Object weights = field(panel, "mctsWeightsPanel");
                Object snapshot = field(weights, "snapshot");
                String leafFen = (String) field(panel, "mctsLeafFen");
                if (snapshot != null && leafFen != null && !leafFen.isBlank()) {
                    frames.add(invoke(snapshot, "playouts", new Class<?>[0])
                            + "|" + leafFen
                            + "|" + invoke(snapshot, "exploringLineText", new Class<?>[0]));
                }
                Object worker = field(panel, "mctsWorker");
                if (worker instanceof SwingWorker<?, ?> swingWorker && swingWorker.isDone()) {
                    flushEdt();
                }
                sleepForLiveFramePoll();
            }
            assertTrue(frames.size() > 2,
                    "network MCTS follow-leaf publishes more than start/end live visualization frames: " + frames);
        } finally {
            invoke(panel, "stopNetworkMcts", new Class<?>[] { boolean.class }, Boolean.FALSE);
            invoke(panel, "dispose", new Class<?>[0]);
            flushEdt();
        }
    }

    /**
     * Verifies Network-tab MCTS creates its search backend from the selected
     * architecture rather than always using the classical fallback.
     */
    private static void testNetworkMctsUsesSelectedArchitectureBackend() {
        Object panel = construct(type("NetworkPanel"), new Class<?>[0]);
        Timer timer = (Timer) field(panel, "debounceTimer");
        timer.stop();
        MctsSearch search = (MctsSearch) invoke(panel, "createNetworkMctsSearch",
                new Class<?>[] { Position.class, double.class, String.class },
                new Position(START_FEN), Double.valueOf(1.0d), "NNUE");
        try {
            assertTrue(search.backendName().startsWith("nnue("), "NNUE selection creates NNUE-backed MCTS");
        } finally {
            search.close();
        }

        String source;
        try {
            source = Files.readString(Path.of("src/application/gui/workbench/network/NetworkPanel.java"),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("unable to read NetworkPanel source", ex);
        }
        assertTrue(source.contains("MctsSearch.cnn(root, cpuct, RealActivations.cnnPath())"),
                "CNN selection routes MCTS through the CNN backend");
        assertTrue(source.contains("MctsSearch.bt4(root, cpuct, RealActivations.bt4Path())"),
                "BT4 selection routes MCTS through the BT4 backend");
        assertTrue(source.contains("if (isNetworkMctsRunning())")
                && source.contains("startNetworkMcts();")
                && source.contains("if (!isNetworkMctsRunning())"),
                "switching network family during MCTS restarts the selected backend");
        assertFalse(source.contains("mctsSearch = new MctsSearch(root, cpuct)"),
                "Network-tab MCTS no longer hardwires the default backend");
    }

    /**
     * Verifies a manual Network position selection takes over from any streamed
     * MCTS leaf position.
     */
    private static void testNetworkPositionPickerClearsLeafOverride() {
        Object panel = construct(type("NetworkPanel"), new Class<?>[0]);
        Timer timer = (Timer) field(panel, "debounceTimer");
        timer.stop();
        setField(panel, "active", Boolean.TRUE);
        setField(panel, "mainBoardFen", START_FEN);
        setField(panel, "mctsLeafFen", MATE_IN_ONE_FEN);
        JComboBox<?> positionCombo = (JComboBox<?>) field(panel, "positionCombo");
        positionCombo.setSelectedItem("Rook endgame");
        invoke(panel, "onPositionPicked", new Class<?>[0]);

        String expected = Positions.fenFor("Rook endgame");
        assertEquals(expected, field(panel, "overrideFen"), "canned position is pinned");
        assertEquals(null, field(panel, "mctsLeafFen"), "manual position clears MCTS leaf override");
        assertEquals(expected, invoke(panel, "effectiveFen", new Class<?>[0]),
                "effective network FEN follows the selected position");
        assertEquals(expected, field(panel, "pendingFen"), "selected position queues inference");
        timer.stop();
        invoke(panel, "dispose", new Class<?>[0]);
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
        assertNetworkBackendRows((JComponent) panel);

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
     * Verifies the runtime backend list covers every optional NN native backend
     * family currently present in the codebase.
     *
     * @param panel diagnostics component
     */
    private static void assertNetworkBackendRows(JComponent panel) {
        for (String label : List.of("Java2D", "LC0 CUDA", "LC0 ROCm", "LC0 oneAPI",
                "BT4 CUDA", "BT4 ROCm", "BT4 oneAPI", "T5 CUDA", "T5 ROCm", "T5 oneAPI",
                "OTIS CUDA", "OTIS ROCm", "OTIS oneAPI")) {
            assertTrue(componentTreeHasLabelText(panel, label),
                    "network diagnostics backend row exists: " + label);
        }
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
     * Verifies heavy workbench painters can request Java2D-compatible buffers
     * without depending on a live display during headless regression runs.
     */
    private static void testRenderAccelerationProvidesCompatibleImages() {
        BufferedImage image = RenderAcceleration.compatibleImage(17, 19,
                Transparency.TRANSLUCENT);
        assertEquals(Integer.valueOf(17), Integer.valueOf(image.getWidth()),
                "compatible image width");
        assertEquals(Integer.valueOf(19), Integer.valueOf(image.getHeight()),
                "compatible image height");
        assertTrue(!RenderAcceleration.summary().isBlank(),
                "render acceleration reports status");
    }

    /**
     * Verifies the synthetic NNUE feature picker honors its distinct-index
     * contract.
     */
    private static void testSyntheticFeaturePickerReturnsDistinctIndices() {
        int[] indices = (int[]) invokeStatic(type("SyntheticActivations"), "pickFeatureIndices",
                new Class<?>[] { Random.class, int.class, int.class }, new Random(123L), 64, 64);
        boolean[] seen = new boolean[64];
        for (int index : indices) {
            assertTrue(index >= 0 && index < seen.length,
                    "synthetic feature index is in range");
            assertFalse(seen[index], "synthetic feature index is unique");
            seen[index] = true;
        }
        assertEquals(Integer.valueOf(64), Integer.valueOf(indices.length),
                "synthetic feature picker fills requested unique range");
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
        assertTrue(((Scrollable) view).getScrollableTracksViewportHeight(),
                "NNUE atlas fits the viewport height instead of requiring vertical scroll");
        assertTrue(((JComponent) view).getPreferredSize().height <= 760,
                "NNUE atlas keeps a viewport-sized preferred height");
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
        assertTrue((Boolean) invokeOn(type("NnueAtlasView"), view, "navigateAtlasSelection",
                new Class<?>[] { int.class, int.class }, 0, 1),
                "NNUE atlas whole-plane view supports arrow-key row navigation");
        int selected = (Integer) field(view, "atlasSelected");
        assertTrue(selected >= 0, "NNUE atlas arrow navigation selects a slot");
    }

    /**
     * Verifies the overview contributor ledger keeps large Half-KP board
     * previews, avoids zebra-striping the rows, and uses a one-sided magnitude
     * bar because positive and negative contributors are already split into
     * separate columns.
     */
    private static void testNnueContributorLedgerUsesReadableRows() {
        String source;
        try {
            source = Files.readString(Path.of("src/application/gui/workbench/network/NnueOverviewView.java"),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("unable to read NNUE overview source", ex);
        }
        assertTrue(source.contains("CONTRIBUTOR_ROW_MIN_HEIGHT = 38"),
                "NNUE contributor rows reserve enough height for readable mini boards");
        assertTrue(source.contains("drawContributorMagnitudeBar"),
                "NNUE contributor ledger uses one-sided magnitude bars");
        assertFalse(source.contains("i % 2 == 0"),
                "NNUE contributor rows do not use alternating table backgrounds");
        assertFalse(source.contains("TensorViz.drawHorizontalBar(g, bar, v, maxAbs, null)"),
                "NNUE contributor columns do not render signed midpoint bars");
    }

    /**
     * Verifies NNUE contributor bars use the neutral elevated shade as the lane
     * and a brighter signed colour as the actual magnitude fill.
     */
    private static void testNnueContributorBarUsesNeutralTrackAndBrightFill() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            invokeStatic(type("TensorViz"), "refreshPalette", new Class<?>[0]);
            BufferedImage image = new BufferedImage(120, 24, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(themeColor("ELEVATED_SOLID"));
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                invokeStatic(type("NnueOverviewView"), "drawContributorMagnitudeBar",
                        new Class<?>[] { Graphics2D.class, Rectangle.class, float.class, float.class,
                                boolean.class },
                        graphics, new Rectangle(10, 8, 100, 8), 0.5f, 1.0f, Boolean.TRUE);
            } finally {
                graphics.dispose();
            }
            Color fill = new Color(image.getRGB(28, 12), true);
            Color track = new Color(image.getRGB(92, 12), true);
            assertTrue(colorDistance(track, themeColor("ELEVATED_SOLID")) <= 1.0,
                    "NNUE contributor bar track uses the neutral elevated shade");
            assertTrue(relativeLuminance(fill) > relativeLuminance(track),
                    "NNUE contributor bar fill is brighter than the neutral track");
            assertColorDistanceAtLeast(track, fill, 48.0,
                    "NNUE contributor bar fill is clearly distinct from the track");
        } finally {
            Theme.setMode(previous);
            invokeStatic(type("TensorViz"), "refreshPalette", new Class<?>[0]);
        }
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
     * Verifies Raw/All mode reserves stable sparse input lanes while MCTS leaf
     * following changes the currently active feature count.
     */
    private static void testNnueRawUsesStableFeatureLanes() {
        Class<?> viewType = type("NnueView");
        Class<?> snapshotType = type("ActivationSnapshot");
        Class<?> baseType = type("NetworkView");
        Class<?> overviewType = type("NnueOverviewView");

        Object view = construct(viewType, new Class<?>[0]);
        Object stockfishSnapshot = construct(snapshotType, new Class<?>[0]);
        put(stockfishSnapshot, "nnue.stockfish.fc0.raw", new int[] { 33 }, new float[33]);
        invokeOn(baseType, view, "setSnapshot", new Class<?>[] { snapshotType }, stockfishSnapshot);
        int stockfishLanes = (Integer) invokeOn(overviewType, view, "rawFeatureLaneCount",
                new Class<?>[] { float[].class, float[].class }, new float[30], new float[31]);
        assertEquals(Integer.valueOf(32), Integer.valueOf(stockfishLanes),
                "Raw Stockfish NNUE keeps all 32 sparse input lanes visible");
        assertEquals("30 / 32 active sparse inputs",
                invokeOn(overviewType, view, "rawFeatureDetail",
                        new Class<?>[] { int.class, int.class }, 30, stockfishLanes),
                "Raw NNUE header reports active lanes inside the fixed lane count");

        Object classicSnapshot = construct(snapshotType, new Class<?>[0]);
        invokeOn(baseType, view, "setSnapshot", new Class<?>[] { snapshotType }, classicSnapshot);
        int classicLanes = (Integer) invokeOn(overviewType, view, "rawFeatureLaneCount",
                new Class<?>[] { float[].class, float[].class }, new float[28], new float[29]);
        assertEquals(Integer.valueOf(FeatureEncoder.MAX_ACTIVE_FEATURES), Integer.valueOf(classicLanes),
                "Raw classic NNUE keeps the encoder's maximum sparse feature lanes visible");
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
     * Verifies network mini-board orientation and LC0 square remapping follow
     * the side to move.
     */
    private static void testNetworkBoardOrientationFollowsSideToMove() {
        String whiteFen = START_FEN;
        String blackFen = "rnbqkbnr/ppp1pppp/8/3p4/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1";
        Rectangle board = new Rectangle(0, 0, 80, 80);
        assertTrue(TensorViz.whiteDownForSideToMove(whiteFen),
                "white-to-move network board has White at the bottom");
        assertTrue(!TensorViz.whiteDownForSideToMove(blackFen),
                "black-to-move network board has Black at the bottom");
        assertEquals(Integer.valueOf(56),
                Integer.valueOf(TensorViz.boardSquareAt(board, 5, 5, true)),
                "white-down top-left is a8");
        assertEquals(Integer.valueOf(7),
                Integer.valueOf(TensorViz.boardSquareAt(board, 5, 5, false)),
                "black-down top-left is h1");

        float[] encoded = new float[64];
        encoded[10] = 1.0f; // c2 in side-to-move perspective, i.e. c7 on a black-to-move board.
        float[] boardSquares = TensorViz.lc0NetworkSquaresToBoard(encoded, blackFen, 0);
        assertEquals(Float.valueOf(1.0f), Float.valueOf(boardSquares[50]),
                "black LC0 perspective rank-mirrors back to c7");
        assertEquals(Integer.valueOf(10),
                Integer.valueOf(TensorViz.boardSquareToLc0NetworkSquare(50, blackFen, 0)),
                "black board square maps back to the encoded c2 token");

        int transpose = chess.nn.lc0.bt4.Encoder.TRANSPOSE_TRANSFORM;
        assertEquals(Integer.valueOf(55),
                Integer.valueOf(TensorViz.boardSquareToLc0NetworkSquare(1, whiteFen, transpose)),
                "BT4 transpose maps b1 to h7 like the input encoder");
        float[] transformed = new float[64];
        transformed[55] = 1.0f;
        assertEquals(Float.valueOf(1.0f),
                Float.valueOf(TensorViz.lc0NetworkSquaresToBoard(transformed, whiteFen, transpose)[1]),
                "BT4 transpose remaps token h7 back to b1 for display");

        assertTrue(boardOverlayChangesTopLeft(56, true),
                "white-down overlays use the same top-left square as hit testing");
        assertTrue(boardOverlayChangesTopLeft(7, false),
                "black-down overlays use the same top-left square as hit testing");
    }

    /**
     * Verifies the shared dense-view board helper keeps tiny boards visual-only
     * while preserving oriented square hit regions.
     */
    private static void testNetworkBoardSectionSuppressesSmallLabelsAndKeepsHitOrientation() {
        Rectangle smallBoard = new Rectangle(10, 20, 64, 64);
        BufferedImage small = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        try {
            TensorViz.useHighQuality(g);
            paintNetworkBoardSection(g, new HitRegions(), smallBoard, START_FEN,
                    "caption should not paint on tiny board", null, 1.0f, -1,
                    TensorViz.FOCUS, "caption");
        } finally {
            g.dispose();
        }
        assertEquals(Integer.valueOf(0),
                Integer.valueOf(alphaSum(small, 0, 0, small.getWidth(), smallBoard.y)),
                "tiny network board section suppresses visible captions");

        String blackFen = "rnbqkbnr/ppp1pppp/8/3p4/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1";
        Rectangle board = new Rectangle(0, 0, 80, 80);
        float[] values = new float[64];
        values[7] = 1.0f;
        HitRegions hits = new HitRegions();
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics();
        try {
            TensorViz.useHighQuality(g);
            paintNetworkBoardSection(g, hits, board, blackFen, "orientation",
                    values, 1.0f, 7, TensorViz.FOCUS, "oriented value");
        } finally {
            g.dispose();
        }
        HitRegions.Region topLeft = hits.hitTest(5, 5);
        assertTrue(topLeft != null, "network board section registers square hit regions");
        assertEquals("h1", topLeft.title,
                "black-to-move network board section maps top-left hit to h1");
        assertEquals("+1.0000", topLeft.value,
                "network board section exposes per-square values through tooltips");

        HitRegions inspectHits = new HitRegions();
        image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics();
        try {
            TensorViz.useHighQuality(g);
            paintInspectableNetworkBoardSection(g, inspectHits, board, blackFen,
                    "inspection", null, 1.0f, -1, TensorViz.VALUE,
                    "whole board", "OTIS board", "inspectable OTIS board",
                    "otis.sheaf.laplacian");
        } finally {
            g.dispose();
        }
        HitRegions.Region center = inspectHits.hitTest(40, 40);
        assertTrue(center != null, "inspectable network board section registers a whole-board region");
        assertEquals("otis.sheaf.laplacian", center.dataKey,
                "network board section keeps whole-board inspector binding when no overlay is present");

        List<String> layerOrder = new ArrayList<>();
        Object underlay = networkBoardOverlay((gg, overlayBoard, whiteDown) -> {
            layerOrder.add("underlay");
            assertEquals(Boolean.FALSE, Boolean.valueOf(whiteDown),
                    "network board section passes side-to-move orientation to custom underlays");
            gg.setColor(TensorViz.VALUE);
            gg.fillRect(overlayBoard.x, overlayBoard.y, 4, 4);
        });
        Object overlay = networkBoardOverlay((gg, overlayBoard, whiteDown) -> {
            layerOrder.add("overlay");
            assertEquals(Boolean.FALSE, Boolean.valueOf(whiteDown),
                    "network board section passes side-to-move orientation to custom overlays");
            gg.setColor(TensorViz.FOCUS);
            gg.fillRect(overlayBoard.x + 4, overlayBoard.y, 4, 4);
        });
        image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics();
        try {
            TensorViz.useHighQuality(g);
            paintLayeredNetworkBoardSection(g, new HitRegions(), board, blackFen,
                    "custom", null, 1.0f, -1, TensorViz.FOCUS,
                    "custom overlay", underlay, overlay, null);
        } finally {
            g.dispose();
        }
        assertEquals("underlay,overlay", String.join(",", layerOrder),
                "network board section invokes underlays before overlays");
    }

    /**
     * Verifies the BT4 detailed-board overlay keeps click selection, triangle
     * tinting, and oriented per-square hover metadata after routing through the
     * shared board-section helper.
     */
    private static void testBt4DetailedBoardUsesSharedTriangleOverlay() {
        Class<?> snapshotType = type("ActivationSnapshot");
        Class<?> baseType = type("NetworkView");
        Class<?> modeType = type("ViewMode");
        Object detailed = enumValue(modeType, "DETAILED");
        Object view = construct(type("Bt4View"), new Class<?>[0]);
        Object snapshot = construct(snapshotType, new Class<?>[0]);
        invokeStatic(type("SyntheticActivations"), "fillBt4",
                new Class<?>[] { String.class, snapshotType }, START_FEN, snapshot);
        invoke(snapshot, "seal", new Class<?>[0]);
        invokeOn(baseType, view, "setFen", new Class<?>[] { String.class }, START_FEN);
        invokeOn(baseType, view, "setSnapshot", new Class<?>[] { snapshotType }, snapshot);
        invokeOn(baseType, view, "setViewMode", new Class<?>[] { modeType }, detailed);

        JComponent component = (JComponent) view;
        component.setSize(1240, 760);
        BufferedImage before = paint(component, 1240, 760);
        Rectangle board = (Rectangle) field(view, "boardBounds");
        assertTrue(board.width > 0 && board.height > 0, "BT4 detailed board bounds are painted");

        int x = board.x + board.width / 2;
        int y = board.y + board.height / 2;
        component.dispatchEvent(mouse(component, MouseEvent.MOUSE_PRESSED, 1L, x, y, 1));
        BufferedImage after = paint(component, 1240, 760);
        assertTrue(countDifferingPixels(before, after, board.x, board.y, board.width, board.height) > 256,
                "BT4 selected-square triangle overlay changes the board pixels");

        HitRegions regions = (HitRegions) field(view, "hitRegions");
        HitRegions.Region region = regions.hitTest(x, y);
        assertTrue(region != null, "BT4 detailed board registers selected-square hover metadata");
        assertTrue(region.value.contains("outgoing") && region.value.contains("incoming"),
                "BT4 triangle tooltip keeps both attention directions");
    }

    /**
     * Reflectively paints the package-private network board helper.
     *
     * @param g graphics
     * @param hitRegions hit registry
     * @param board board rectangle
     * @param fen position FEN
     * @param title title text
     * @param values optional square values
     * @param scale overlay scale
     * @param focusSquare highlighted square
     * @param focusColor highlighted-square color
     * @param caption tooltip caption
     */
    private static void paintNetworkBoardSection(Graphics2D g, HitRegions hitRegions,
            Rectangle board, String fen, String title, float[] values, float scale,
            int focusSquare, Color focusColor, String caption) {
        invokeStatic(type("NetworkBoardSection"), "paintOverlayBoard",
                new Class<?>[] {
                        Graphics2D.class, HitRegions.class, Rectangle.class,
                        String.class, String.class, float[].class, float.class,
                        int.class, Color.class, String.class
                },
                g, hitRegions, board, fen, title, values, Float.valueOf(scale),
                Integer.valueOf(focusSquare), focusColor, caption);
    }

    /**
     * Reflectively paints an inspectable network board helper.
     *
     * @param g graphics
     * @param hitRegions hit registry
     * @param board board rectangle
     * @param fen position FEN
     * @param title title text
     * @param values optional square values
     * @param scale overlay scale
     * @param focusSquare highlighted square
     * @param focusColor highlighted-square color
     * @param caption tooltip caption
     * @param inspectionTitle whole-board inspector title
     * @param inspectionDescription whole-board inspector description
     * @param dataKey activation snapshot key
     */
    private static void paintInspectableNetworkBoardSection(Graphics2D g,
            HitRegions hitRegions, Rectangle board, String fen, String title,
            float[] values, float scale, int focusSquare, Color focusColor,
            String caption, String inspectionTitle, String inspectionDescription,
            String dataKey) {
        Class<?> inspectionType = type("NetworkBoardSection$Inspection");
        Object inspection = construct(inspectionType,
                new Class<?>[] {
                        String.class, String.class, String.class, String.class,
                        int.class, int.class, int.class, String.class
                },
                inspectionTitle, inspectionDescription, "", dataKey,
                Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(8), "8x8");
        invokeStatic(type("NetworkBoardSection"), "paintOverlayBoard",
                new Class<?>[] {
                        Graphics2D.class, HitRegions.class, Rectangle.class,
                        String.class, String.class, float[].class, float.class,
                        int.class, Color.class, String.class, inspectionType
                },
                g, hitRegions, board, fen, title, values, Float.valueOf(scale),
                Integer.valueOf(focusSquare), focusColor, caption, inspection);
    }

    /**
     * Reflectively paints a network board helper with a custom overlay hook.
     *
     * @param g graphics
     * @param hitRegions hit registry
     * @param board board rectangle
     * @param fen position FEN
     * @param title title text
     * @param values optional square values
     * @param scale overlay scale
     * @param focusSquare highlighted square
     * @param focusColor highlighted-square color
     * @param caption tooltip caption
     * @param overlay custom board overlay
     * @param inspection optional inspector binding
     */
    private static void paintCustomNetworkBoardSection(Graphics2D g,
            HitRegions hitRegions, Rectangle board, String fen, String title,
            float[] values, float scale, int focusSquare, Color focusColor,
            String caption, Object overlay, Object inspection) {
        paintLayeredNetworkBoardSection(g, hitRegions, board, fen, title, values,
                scale, focusSquare, focusColor, caption, null, overlay,
                inspection);
    }

    /**
     * Reflectively paints a network board helper with custom board layers.
     *
     * @param g graphics
     * @param hitRegions hit registry
     * @param board board rectangle
     * @param fen position FEN
     * @param title title text
     * @param values optional square values
     * @param scale overlay scale
     * @param focusSquare highlighted square
     * @param focusColor highlighted-square color
     * @param caption tooltip caption
     * @param underlay custom board underlay
     * @param overlay custom board overlay
     * @param inspection optional inspector binding
     */
    private static void paintLayeredNetworkBoardSection(Graphics2D g,
            HitRegions hitRegions, Rectangle board, String fen, String title,
            float[] values, float scale, int focusSquare, Color focusColor,
            String caption, Object underlay, Object overlay, Object inspection) {
        Class<?> overlayType = type("NetworkBoardSection$BoardOverlay");
        Class<?> inspectionType = type("NetworkBoardSection$Inspection");
        invokeStatic(type("NetworkBoardSection"), "paintOverlayBoard",
                new Class<?>[] {
                        Graphics2D.class, HitRegions.class, Rectangle.class,
                        String.class, String.class, float[].class, float.class,
                        int.class, Color.class, String.class, overlayType,
                        overlayType, inspectionType
                },
                g, hitRegions, board, fen, title, values, Float.valueOf(scale),
                Integer.valueOf(focusSquare), focusColor, caption, underlay,
                overlay, inspection);
    }

    /**
     * Creates a dynamic proxy for the package-private board overlay interface.
     *
     * @param callback callback invoked by the proxy
     * @return proxy implementing NetworkBoardSection.BoardOverlay
     */
    private static Object networkBoardOverlay(BoardOverlayCallback callback) {
        Class<?> overlayType = type("NetworkBoardSection$BoardOverlay");
        return Proxy.newProxyInstance(overlayType.getClassLoader(),
                new Class<?>[] { overlayType },
                (proxy, method, args) -> {
                    if ("paint".equals(method.getName())) {
                        callback.paint((Graphics2D) args[0], (Rectangle) args[1],
                                ((Boolean) args[2]).booleanValue());
                    }
                    if ("toString".equals(method.getName())) {
                        return "test network board overlay";
                    }
                    return null;
                });
    }

    /**
     * Test-side adapter for the package-private board overlay callback.
     */
    @FunctionalInterface
    private interface BoardOverlayCallback {

        /**
         * Paints a custom board annotation.
         *
         * @param g graphics
         * @param board board rectangle
         * @param whiteDown whether White is rendered at the bottom
         */
        void paint(Graphics2D g, Rectangle board, boolean whiteDown);
    }

    /**
     * Returns whether a single-square overlay changes the board's top-left
     * cell for the requested visual orientation.
     *
     * @param square LERF square to tint
     * @param whiteDown board orientation
     * @return true when top-left pixels changed
     */
    private static boolean boardOverlayChangesTopLeft(int square, boolean whiteDown) {
        Rectangle board = new Rectangle(0, 0, 80, 80);
        BufferedImage base = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        BufferedImage over = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();
        try {
            TensorViz.drawMiniBoard(g, board);
        } finally {
            g.dispose();
        }
        g = over.createGraphics();
        try {
            TensorViz.drawMiniBoard(g, board);
            float[] values = new float[64];
            values[square] = 1.0f;
            TensorViz.drawSquareOverlay(g, board, values, 1.0f, whiteDown);
        } finally {
            g.dispose();
        }
        Color before = new Color(base.getRGB(5, 5), true);
        Color after = new Color(over.getRGB(5, 5), true);
        return colorDistance(before, after) > 8.0;
    }

    /**
     * Verifies the Stockfish forward-skip edge stays a plain straight Trace
     * line without a separate border, endpoint marker, or heavier stroke.
     */
    private static void testNnueForwardSkipUsesNormalLineStyle() {
        String source;
        try {
            source = Files.readString(Path.of("src/application/gui/workbench/network/NnueDrawing.java"),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("unable to read NNUE drawing source", ex);
        }
        assertFalse(source.contains("TRACE_SKIP_EDGE_UNDERLAY_WIDTH"),
                "forward skip line has no cutout underlay");
        assertFalse(source.contains("TRACE_SKIP_EDGE_WIDTH"),
                "forward skip line does not define a separate heavy stroke");
        assertFalse(source.contains("drawSkipEndpoint"),
                "forward skip line has no endpoint marker");
        assertFalse(source.contains("Path2D"),
                "forward skip line does not use a Bezier path");
        assertFalse(source.contains("skipBezierPath"),
                "forward skip line does not build a curved bypass path");
        assertTrue(source.contains("drawTraceEdge(g, x1, y1, x2, y2, strength, false)"),
                "forward skip line delegates to the ordinary straight trace edge");
        assertTrue(source.contains("g.setStroke(new BasicStroke(TRACE_EDGE_WIDTH"),
                "forward skip line reuses ordinary trace edge width");
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
        assertEquals(Integer.valueOf(16), Integer.valueOf(visibleSlots.length),
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
        Object detailed = enumValue(modeType, "DETAILED");
        Object rawMode = enumValue(modeType, "RAW");

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
        assertModesRenderDistinctly((JComponent) cnnView, baseType, modeType,
                new Object[] { atlas, detailed, rawMode },
                new String[] { "Atlas", "Trace", "All" }, "CNN");
        assertCnnTraceAndAllModesAreExplicit();

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
        assertModesRenderDistinctly((JComponent) bt4View, baseType, modeType,
                new Object[] { atlas, detailed, rawMode },
                new String[] { "Atlas", "Trace", "All" }, "BT4");

        Object otisView = construct(type("OtisView"), new Class<?>[0]);
        Object otisSnapshot = construct(snapshotType, new Class<?>[0]);
        invokeStatic(type("SyntheticActivations"), "fillOtis",
                new Class<?>[] { String.class, snapshotType }, START_FEN, otisSnapshot);
        assertEquals(Integer.valueOf(64), Integer.valueOf(data(otisSnapshot, "otis.sheaf.laplacian").length),
                "OTIS synthetic sheaf Laplacian is board-shaped");
        assertEquals(Integer.valueOf(12), Integer.valueOf(data(otisSnapshot, "otis.sheaf.relation.energy").length),
                "OTIS synthetic relation energy exposes i018/i249 relation count");
        assertEquals(Integer.valueOf(12 * 64),
                Integer.valueOf(data(otisSnapshot, "otis.sheaf.target.pressure").length),
                "OTIS synthetic relation target maps are present");
        invoke(otisSnapshot, "seal", new Class<?>[0]);
        invokeOn(baseType, otisView, "setFen", new Class<?>[] { String.class }, START_FEN);
        invokeOn(baseType, otisView, "setSnapshot", new Class<?>[] { snapshotType }, otisSnapshot);
        invokeOn(baseType, otisView, "setViewMode", new Class<?>[] { modeType }, atlas);
        assertPaintsOpaqueCorner((JComponent) otisView, 1240, 760,
                "OTIS atlas paints synthetic snapshot");
        assertModesRenderDistinctly((JComponent) otisView, baseType, modeType,
                new Object[] { atlas, detailed, rawMode },
                new String[] { "Atlas", "Trace", "All" }, "OTIS");
    }

    /**
     * Verifies CNN mode labels and render paths make Trace vs All explicit.
     */
    private static void assertCnnTraceAndAllModesAreExplicit() {
        String source;
        try {
            source = Files.readString(Path.of("src/application/gui/workbench/network/CnnView.java"),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("unable to read CnnView source", ex);
        }
        assertTrue(source.contains("CNN Trace — one selected layer"),
                "CNN Trace advertises selected-layer scope");
        assertTrue(source.contains("raw activation atlas — every CNN layer at once"),
                "CNN All advertises exhaustive atlas scope");
        assertTrue(source.contains("paintTracePath"),
                "CNN Trace uses a path control instead of an all-layer channel atlas");
    }

    /**
     * Renders one network view in every supplied view mode and asserts that
     * each rendering differs from the others by a non-trivial pixel ratio.
     * Guards against a future regression where one mode silently delegates
     * to another (the "Atlas is a reskin of Trace" failure mode).
     *
     * @param view network view component
     * @param baseType NetworkView base class
     * @param modeType ViewMode enum class
     * @param modes view mode values to render
     * @param modeNames human-readable labels for diagnostics
     * @param arch architecture label
     */
    private static void assertModesRenderDistinctly(JComponent view, Class<?> baseType,
            Class<?> modeType, Object[] modes, String[] modeNames, String arch) {
        int width = 1240;
        int height = 760;
        view.setSize(width, height);
        java.awt.image.BufferedImage[] frames = new java.awt.image.BufferedImage[modes.length];
        for (int i = 0; i < modes.length; i++) {
            invokeOn(baseType, view, "setViewMode", new Class<?>[] { modeType }, modes[i]);
            frames[i] = paint(view, width, height);
        }
        // At least 5% of pixels must differ between any two modes; below this
        // the renderings are visually indistinguishable.
        int totalPixels = width * height;
        int minDistinctPixels = totalPixels / 20;
        for (int i = 0; i < frames.length; i++) {
            for (int j = i + 1; j < frames.length; j++) {
                int differing = countDifferingPixels(frames[i], frames[j]);
                String label = arch + " " + modeNames[i] + " vs " + modeNames[j];
                assertTrue(differing >= minDistinctPixels,
                        label + " renders distinct pixels (" + differing + "/" + totalPixels + ")");
            }
        }
    }

    /**
     * Counts pixels whose RGB differs between two equal-sized images.
     *
     * @param a first image
     * @param b second image
     * @return differing-pixel count
     */
    private static int countDifferingPixels(java.awt.image.BufferedImage a,
            java.awt.image.BufferedImage b) {
        int width = a.getWidth();
        int height = a.getHeight();
        int differ = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((a.getRGB(x, y) & 0xFFFFFF) != (b.getRGB(x, y) & 0xFFFFFF)) {
                    differ++;
                }
            }
        }
        return differ;
    }

    /**
     * Counts pixels whose RGB differs between two images inside one region.
     *
     * @param a first image
     * @param b second image
     * @param x region x
     * @param y region y
     * @param width region width
     * @param height region height
     * @return differing-pixel count
     */
    private static int countDifferingPixels(java.awt.image.BufferedImage a,
            java.awt.image.BufferedImage b, int x, int y, int width, int height) {
        int differ = 0;
        int maxX = Math.min(Math.min(a.getWidth(), b.getWidth()), x + Math.max(0, width));
        int maxY = Math.min(Math.min(a.getHeight(), b.getHeight()), y + Math.max(0, height));
        for (int yy = Math.max(0, y); yy < maxY; yy++) {
            for (int xx = Math.max(0, x); xx < maxX; xx++) {
                if ((a.getRGB(xx, yy) & 0xFFFFFF) != (b.getRGB(xx, yy) & 0xFFFFFF)) {
                    differ++;
                }
            }
        }
        return differ;
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
     * Verifies the workbench surfaces OTIS v2 architecture metadata and, when
     * the local placeholder file exists, captures real placeholder tensors.
     */
    private static void testWorkbenchOtisShowsArchitectureMetadata() {
        Object provider = construct(type("RealActivations"), new Class<?>[0]);
        String params = chess.nn.otis.Model.formatParameterCount(chess.nn.otis.Model.DEFAULT_PARAMETER_COUNT)
                + " params";
        assertOtisStatusDetailContains(provider, "simple_18");
        assertOtisStatusDetailContains(provider, params);

        if (!Files.exists(application.gui.workbench.network.RealActivations.otisPath())) {
            return;
        }
        Object snapshot = invoke(provider, "inferOtis", new Class<?>[] { String.class }, START_FEN);
        String status = String.valueOf(invoke(provider, "statusFor", new Class<?>[] { String.class }, "otis"));
        assertTrue(status.contains(params), "OTIS workbench status shows parameter count");
        int[] inputShape = (int[]) invoke(snapshot, "shape", new Class<?>[] { String.class }, "otis.input");
        int[] trunkShape = (int[]) invoke(snapshot, "shape", new Class<?>[] { String.class }, "otis.trunk");
        int[] policyShape = (int[]) invoke(snapshot, "shape", new Class<?>[] { String.class },
                "otis.policy.logits");
        int[] policyHeadShape = (int[]) invoke(snapshot, "shape", new Class<?>[] { String.class },
                "otis.weights.policy_head");
        int[] readoutShape = (int[]) invoke(snapshot, "shape", new Class<?>[] { String.class },
                "otis.weights.readout_hidden");
        int[] rhoShape = (int[]) invoke(snapshot, "shape", new Class<?>[] { String.class },
                "otis.weights.rho_src");
        assertEquals(Integer.valueOf(chess.nn.otis.Model.INPUT_PLANES), Integer.valueOf(inputShape[0]),
                "OTIS real input uses simple_18 planes");
        assertEquals(Integer.valueOf(chess.nn.otis.Model.DEFAULT_TRUNK_CHANNELS), Integer.valueOf(trunkShape[0]),
                "OTIS real trunk channel count");
        assertEquals(Integer.valueOf(chess.nn.otis.Model.DEFAULT_POLICY_SIZE), Integer.valueOf(policyShape[0]),
                "OTIS real policy width");
        assertEquals(Integer.valueOf(chess.nn.otis.Model.DEFAULT_POLICY_SIZE), Integer.valueOf(policyHeadShape[0]),
                "OTIS real policy-head matrix rows");
        assertEquals(Integer.valueOf(chess.nn.otis.Model.HIDDEN_DIM), Integer.valueOf(policyHeadShape[1]),
                "OTIS real policy-head matrix columns");
        assertEquals(Integer.valueOf(chess.nn.otis.Model.HIDDEN_DIM), Integer.valueOf(readoutShape[0]),
                "OTIS real readout matrix rows");
        assertEquals(Integer.valueOf(chess.nn.otis.Model.defaultReadoutDim()), Integer.valueOf(readoutShape[1]),
                "OTIS real readout matrix columns");
        assertEquals(Integer.valueOf(chess.nn.otis.Model.DEFAULT_BLOCKS * chess.nn.otis.Model.RELATION_COUNT),
                Integer.valueOf(rhoShape[0]), "OTIS real sheaf rho map count");
    }

    /**
     * Verifies the OTIS diagnostics row contains expected text.
     *
     * @param provider real activation provider
     * @param expected expected substring
     */
    private static void assertOtisStatusDetailContains(Object provider, String expected) {
        List<Object> statuses = objectList(invoke(provider, "modelStatuses", new Class<?>[0]));
        for (Object status : statuses) {
            String label = String.valueOf(invoke(status, "label", new Class<?>[0]));
            if (label.startsWith("OTIS")) {
                String detail = String.valueOf(invoke(status, "detail", new Class<?>[0]));
                assertTrue(detail.contains(expected), "OTIS diagnostics detail contains " + expected);
                return;
            }
        }
        throw new AssertionError("missing OTIS diagnostics status row");
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
        List<Object> rows = objectList(invoke(snapshot, "rows", new Class<?>[0]));
        assertTrue(!rows.isEmpty(), "MCTS snapshot has root rows");
        short bestMove = (Short) invoke(snapshot, "bestMove", new Class<?>[0]);
        assertTrue(new Position(START_FEN).isLegalMove(bestMove), "MCTS best move is legal");
        long beforePreview = ((Long) invoke(search, "playouts", new Class<?>[0])).longValue();
        Object preview = invoke(search, "previewNextLeaf", new Class<?>[] { boolean.class }, false);
        assertEquals(Long.valueOf(beforePreview), invoke(preview, "playouts", new Class<?>[0]),
                "MCTS leaf preview does not count as a playout");
        String previewLeaf = (String) invoke(preview, "exploringLineText", new Class<?>[0]);
        assertTrue(previewLeaf != null && !previewLeaf.isBlank(),
                "MCTS preview focuses the leaf selected for evaluation");
        invoke(search, "iterate", new Class<?>[0]);
        Object afterPreview = invoke(search, "snapshot", new Class<?>[] { boolean.class }, false);
        assertEquals(previewLeaf, invoke(afterPreview, "exploringLineText", new Class<?>[0]),
                "MCTS snapshot stays on the evaluated leaf instead of the current best line");
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
        List<Object> rows = objectList(invoke(snapshot, "rows", new Class<?>[0]));
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
        List<Object> rows = objectList(invoke(snapshot, "rows", new Class<?>[0]));
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
        List<Object> rows = objectList(invoke(snapshot, "rows", new Class<?>[0]));
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
        java.util.Map<?, ?> table = mapValue(field(search, "transpositions"));
        assertTrue(!table.isEmpty(), "MCTS hash table stores position stats");
        invoke(search, "close", new Class<?>[0]);

        Object deeperSearch = construct(searchType,
                new Class<?>[] { Position.class, double.class },
                new Position(START_FEN), 1.25);
        invoke(deeperSearch, "iterate", new Class<?>[0]);
        Object root = field(deeperSearch, "root");
        List<Object> rootChildren = objectList(field(root, "children"));
        Object grandchild = null;
        for (Object rootChild : rootChildren) {
            List<Object> childChildren = objectList(field(rootChild, "children"));
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
        List<Object> reusedChildren = objectList(field(reusedRoot, "children"));
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
     * Verifies the bounded tree snapshot carries stable node ids, selected
     * node details, and cap accounting before Swing sees the model.
     */
    private static void testMctsTreeSnapshotCapsAndSelection() {
        MctsSearch search = new MctsSearch(new Position(START_FEN), 1.25);
        for (int i = 0; i < 90; i++) {
            search.iterate();
        }
        MctsSearch.TreeSnapshot compact = search.treeSnapshot(
                false,
                new MctsSearch.TreeOptions(8, 4, 0, 0, false),
                "root");
        assertTrue(compact.nodes().size() <= 4, "MCTS tree snapshot respects node cap");
        assertTrue(compact.omittedNodes() > 0, "MCTS tree snapshot reports omitted nodes");
        assertEquals("root", compact.selectedNode().id(),
                "MCTS tree snapshot defaults to root selection");
        assertTrue(!compact.rootRows().isEmpty(), "MCTS tree snapshot includes root rows");
        String childId = compact.rootRows().get(0).nodeId();
        MctsSearch.TreeSnapshot selected = search.treeSnapshot(
                false,
                MctsSearch.TreeOptions.defaults(),
                childId);
        assertEquals(childId, selected.selectedNode().id(),
                "MCTS tree snapshot selects a stable root child id");
        assertTrue(selected.selectedNode().visits() >= 0,
                "MCTS selected node exposes visits");
        assertTrue(selected.selectedNode().pvText() != null,
                "MCTS selected node exposes PV text");
        search.close();
    }

    /**
     * Verifies the shared MCTS session publishes bounded snapshots while
     * supporting pause, resume, and stop without EDT blocking.
     */
    private static void testMctsSessionLifecyclePublishesSnapshots() {
        MctsSession session = new MctsSession();
        int[] notifications = { 0 };
        session.addListener(source -> notifications[0]++);
        session.requestRootFen(START_FEN, true);
        flushEdt();
        assertEquals(START_FEN, session.snapshot().rootFen(),
                "MCTS session stores requested root FEN");
        session.start(new MctsSession.Config(
                START_FEN,
                MctsSession.Backend.CLASSICAL,
                10_000,
                0L,
                1.25,
                true));
        session.pause();
        MctsSession.Snapshot paused = waitForMctsSession(session,
                snapshot -> snapshot.state() == MctsSession.State.PAUSED
                        && snapshot.tree() != null,
                "MCTS session reaches paused state with a tree snapshot");
        assertTrue(!paused.tree().rootRows().isEmpty(),
                "MCTS session publishes root rows");
        session.resume();
        waitForMctsSession(session,
                snapshot -> snapshot.state() == MctsSession.State.RUNNING
                        || snapshot.state() == MctsSession.State.DONE,
                "MCTS session resumes");
        session.stop();
        MctsSession.Snapshot stopped = waitForMctsSession(session,
                snapshot -> snapshot.state() == MctsSession.State.IDLE,
                "MCTS session stops");
        assertEquals(MctsSession.State.IDLE, stopped.state(),
                "MCTS session stop leaves idle state");
        assertTrue(notifications[0] > 0, "MCTS session notifies listeners");
        session.close();
    }

    /**
     * Verifies the MCTS inspector side paints as one solid workbench surface
     * instead of exposing background-colored gutters around its child views.
     */
    private static void testMctsPanelInspectorUsesSolidSurface() {
        MctsSession session = new MctsSession();
        MctsPanel panel = new MctsPanel(session, () -> START_FEN);
        try {
            JSplitPane split = firstSplitPane(panel);
            assertTrue(split.getRightComponent() instanceof JComponent,
                    "MCTS split has an inspector component");
            JComponent inspector = (JComponent) split.getRightComponent();
            assertTrue(inspector.isOpaque(), "MCTS inspector surface is opaque");
            assertEquals(themeColor("PANEL_SOLID"), inspector.getBackground(),
                    "MCTS inspector surface uses panel background");
            assertPaintsOpaqueCorner(inspector, 360, 360,
                    "MCTS inspector paints opaque gutters");
        } finally {
            panel.dispose();
            session.close();
        }
    }

    /**
     * Verifies the combined Engine / Search surface keeps the root-move table as
     * the default view and materializes the graph only when requested.
     */
    private static void testMctsWorkspaceDefaultsToTableAndBuildsGraphLazily() {
        int[] tableBuilds = { 0 };
        int[] graphBuilds = { 0 };
        MctsWorkspacePanel workspace = new MctsWorkspacePanel(
                () -> {
                    tableBuilds[0]++;
                    return new JPanel();
                },
                () -> {
                    graphBuilds[0]++;
                    return new JPanel();
                });

        assertEquals(Integer.valueOf(MctsWorkspacePanel.VIEW_TABLE),
                Integer.valueOf(workspace.viewMode()),
                "Engine Search workspace opens on the table view");
        assertEquals(Integer.valueOf(1), Integer.valueOf(tableBuilds[0]),
                "Engine Search workspace builds the table immediately");
        assertEquals(Integer.valueOf(0), Integer.valueOf(graphBuilds[0]),
                "Engine Search workspace keeps the graph lazy");
        assertTrue(!workspace.graphBuilt(),
                "Engine Search workspace reports graph as unbuilt initially");

        workspace.setViewMode(MctsWorkspacePanel.VIEW_GRAPH);
        assertEquals(Integer.valueOf(MctsWorkspacePanel.VIEW_GRAPH),
                Integer.valueOf(workspace.viewMode()),
                "Engine Search workspace switches to graph");
        assertEquals(Integer.valueOf(1), Integer.valueOf(graphBuilds[0]),
                "Engine Search workspace builds the graph once");
        assertTrue(workspace.graphBuilt(),
                "Engine Search workspace reports graph as built after selection");

        workspace.setViewMode(MctsWorkspacePanel.VIEW_TABLE);
        workspace.setViewMode(MctsWorkspacePanel.VIEW_GRAPH);
        assertEquals(Integer.valueOf(1), Integer.valueOf(tableBuilds[0]),
                "Engine Search workspace reuses the table body");
        assertEquals(Integer.valueOf(1), Integer.valueOf(graphBuilds[0]),
                "Engine Search workspace reuses the graph body");
    }

    /**
     * Verifies the graph/inspector side follows node selections published through
     * the shared MCTS session, including selections made by the root-move table.
     */
    private static void testTreePanelFollowsSessionSelection() {
        MctsSession session = new MctsSession();
        TreePanel panel = new TreePanel(session, () -> START_FEN, false);
        try {
            TreeGraphView view = (TreeGraphView) field(panel, "view");
            int viewWidth = 520;
            int viewHeight = 360;
            view.setSize(viewWidth, viewHeight);
            session.start(new MctsSession.Config(
                    START_FEN,
                    MctsSession.Backend.CLASSICAL,
                    80,
                    0L,
                    1.25,
                    true));
            MctsSession.Snapshot snapshot = waitForMctsSession(session,
                    next -> next.state() == MctsSession.State.DONE
                            && next.tree() != null
                            && !next.tree().rootRows().isEmpty(),
                    "MCTS session publishes final root rows for tree-panel synchronization");
            String childId = snapshot.tree().rootRows().get(0).nodeId();
            session.setSelectedNodeId(childId);
            waitForMctsSession(session,
                    next -> next.tree() != null
                            && next.tree().selectedNode() != null
                            && childId.equals(next.tree().selectedNode().id()),
                    "MCTS session adopts table-selected node");
            flushEdt();
            assertEquals(childId, field(panel, "inspectorNodeId"),
                    "Tree panel inspector follows session-selected node");
            @SuppressWarnings("unchecked")
            Set<String> selectedPath = (Set<String>) field(view, "selectedPath");
            assertTrue(!selectedPath.isEmpty(),
                    "Tree graph paints a selected path after session selection");
            TreeLayout.Node rendered = renderedNodeByInfoId(view.model(), childId);
            assertNodeCentered(view, rendered, viewWidth, viewHeight,
                    "Tree graph centers the externally selected node");
        } finally {
            panel.dispose();
            session.close();
        }
    }

    /**
     * Verifies the Tree toolbar's graph style switcher drives the canvas display
     * mode instead of being a cosmetic-only control.
     */
    private static void testTreePanelGraphStyleSwitcherControlsDisplayMode() {
        MctsSession session = new MctsSession();
        TreePanel panel = new TreePanel(session, () -> START_FEN, false);
        try {
            TreeGraphView view = (TreeGraphView) field(panel, "view");
            SegmentedSwitcher switcher = (SegmentedSwitcher) field(panel, "graphStyleSwitcher");
            assertEquals(TreeGraphView.DisplayMode.BOARDS, view.displayMode(),
                    "tree graph starts in board-thumbnail mode");
            assertEquals("Graph node style", switcher.getAccessibleContext().getAccessibleName(),
                    "tree graph style switcher has a stable accessible name");

            switcher.setSelectedIndex(1);
            assertEquals(TreeGraphView.DisplayMode.MOVES, view.displayMode(),
                    "tree graph style switcher selects move-card mode");

            switcher.setSelectedIndex(0);
            assertEquals(TreeGraphView.DisplayMode.BOARDS, view.displayMode(),
                    "tree graph style switcher restores board-thumbnail mode");
        } finally {
            panel.dispose();
            session.close();
        }
    }

    /**
     * Verifies the graph layout model preserves aggregate omitted-node
     * accounting from the bounded tree snapshot.
     */
    private static void testTreeLayoutCarriesOmittedNodeCount() {
        TreeLayout.Model model = TreeLayout.layout(
                List.of(treeInfo("root", "", 0, 40, 1L)),
                false,
                false,
                "root",
                64,
                90,
                16,
                28,
                17);
        assertEquals(Integer.valueOf(17), Integer.valueOf(model.omittedNodes()),
                "tree layout model carries omitted-node count");

        TreeLayout.Model empty = TreeLayout.layout(
                List.of(),
                false,
                false,
                null,
                64,
                90,
                16,
                28,
                -5);
        assertEquals(Integer.valueOf(0), Integer.valueOf(empty.omittedNodes()),
                "tree layout clamps negative omitted-node counts");
    }

    /**
     * Verifies the tree uses a larger fixed SVG-era node board size and no
     * longer exposes the old pixel-resolution spinner in the toolbar.
     */
    private static void testTreePanelUsesFixedSvgBoardSize() {
        int nodeBoardSize = ((Integer) staticField(type("TreePanel"), "NODE_BOARD_SIZE")).intValue();
        assertTrue(nodeBoardSize >= 96,
                "tree panel uses a larger fixed node board size");
        try {
            String source = Files.readString(Path.of("src/application/gui/workbench/mcts/TreePanel.java"),
                    StandardCharsets.UTF_8);
            assertFalse(source.contains("boardSizeSpinner"),
                    "tree panel removes the old board-size spinner");
            assertFalse(source.contains("Ui.labeledControl(\"Board\""),
                    "tree toolbar no longer exposes a Board resolution control");
        } catch (IOException ex) {
            throw new AssertionError("unable to inspect TreePanel source", ex);
        }
    }

    /**
     * Verifies the tree graph clips SAN/stat text to the node caption instead of
     * letting large counts spill into neighboring graph space.
     */
    private static void testTreeGraphNodeCaptionClipsOverflowText() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            TreeGraphView view = new TreeGraphView();
            int width = 160;
            int height = 220;
            view.setSize(width, height);
            int nodeX = 56;
            int nodeY = 28;
            int nodeW = 48;
            int nodeH = 82;
            short move = Move.parse("g1f3");
            MctsSearch.NodeInfo info = new MctsSearch.NodeInfo(
                    "root",
                    "",
                    new short[] { move },
                    new short[] { move },
                    move,
                    "Rgxg8=Q+ ultra-long",
                    "g1f3",
                    "g1f3",
                    "Rgxg8=Q+ ultra-long",
                    0,
                    123_456_789,
                    0.42,
                    0.04,
                    0.0,
                    0.0,
                    0.55,
                    0.10,
                    0.35,
                    "",
                    "",
                    0,
                    START_FEN,
                    "Rgxg8=Q+",
                    1L);
            TreeLayout.Node node = new TreeLayout.Node("root", info, nodeX, nodeY, nodeW, nodeH,
                    0, true, false, false, false, List.of());
            TreeLayout.Model model = new TreeLayout.Model(List.of(node), List.of(), width, 140, "root", 1, 0);
            view.setModel(model);

            BufferedImage image = paint(view, width, height);
            int panY = 14;
            int captionY = panY + nodeY + nodeW;
            int captionH = nodeH - nodeW;
            int insidePixels = nonBackgroundPixels(image, Theme.BG,
                    nodeX + 8, captionY + 4, Math.max(1, nodeW - 16), captionH - 6);
            int leakedPixels = nonBackgroundPixels(image, Theme.BG,
                    nodeX + nodeW + 3, captionY + 4, 32, captionH - 6);
            assertTrue(insidePixels > 0, "tree node caption paints inside node");
            assertEquals(Integer.valueOf(0), Integer.valueOf(leakedPixels),
                    "tree node caption clips text to node bounds");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Verifies selected/PV/search/target rings are drawn around the full tree
     * card, including the caption area below the board.
     */
    private static void testTreeGraphSelectionRingWrapsFullCard() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            TreeGraphView view = new TreeGraphView();
            int width = 160;
            int height = 220;
            view.setSize(width, height);
            int nodeX = 56;
            int nodeY = 28;
            int nodeW = 48;
            int nodeH = 82;
            MctsSearch.NodeInfo info = new MctsSearch.NodeInfo(
                    "root",
                    "",
                    new short[0],
                    new short[0],
                    Move.NO_MOVE,
                    "root",
                    "",
                    "",
                    "",
                    0,
                    100,
                    0.40,
                    0.0,
                    0.0,
                    0.0,
                    0.55,
                    0.10,
                    0.35,
                    "",
                    "",
                    0,
                    START_FEN,
                    "root",
                    1L);
            TreeLayout.Node node = new TreeLayout.Node("root", info, nodeX, nodeY, nodeW, nodeH,
                    0, true, true, false, false, List.of());
            TreeLayout.Model model = new TreeLayout.Model(List.of(node), List.of(), width, 140, "root", 1, 0);
            view.setModel(model);

            BufferedImage image = paint(view, width, height);
            int panY = 14;
            int captionY = panY + nodeY + nodeW;
            int captionH = nodeH - nodeW;
            int cardSidePixels = nonBackgroundPixels(image, Theme.BG,
                    nodeX + nodeW + 4, captionY + 4, 8, Math.max(1, captionH - 8));
            assertTrue(cardSidePixels > 0,
                    "selected tree node ring wraps outside the caption side of the full card");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Verifies high-zoom selection rings keep their stroke outside the full
     * node card instead of painting over the dark caption lane.
     */
    private static void testTreeGraphSelectionRingStaysOutsideCaptionAtHighZoom() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            int width = 620;
            int height = 740;
            int nodeX = 20;
            int nodeY = 12;
            int nodeW = 44;
            int nodeH = 70;
            double zoom = 8.0;
            double panX = 40.0;
            double panY = 40.0;
            MctsSearch.NodeInfo info = treeInfo("root", "", 0, 100, 1L);
            TreeLayout.Node plainNode = new TreeLayout.Node("root", info, nodeX, nodeY, nodeW, nodeH,
                    0, true, false, false, false, List.of());
            TreeLayout.Node selectedNode = new TreeLayout.Node("root", info, nodeX, nodeY, nodeW, nodeH,
                    0, true, true, false, false, List.of());

            BufferedImage plain = paintTreeNodeWithViewTransform(plainNode, width, height, zoom, panX, panY);
            BufferedImage selected = paintTreeNodeWithViewTransform(selectedNode, width, height, zoom, panX, panY);

            int captionY = (int) Math.round(panY + (nodeY + nodeW) * zoom);
            int captionH = (int) Math.round((nodeH - nodeW) * zoom);
            int cardRight = (int) Math.round(panX + (nodeX + nodeW) * zoom);
            int insideDiff = countDifferingPixels(plain, selected,
                    cardRight - 8, captionY + 14, 8, Math.max(1, captionH - 28));
            int outsideDiff = countDifferingPixels(plain, selected,
                    cardRight + 3, captionY + 14, 10, Math.max(1, captionH - 28));
            assertEquals(Integer.valueOf(0), Integer.valueOf(insideDiff),
                    "high-zoom selection ring stays outside the caption lane");
            assertTrue(outsideDiff > 20,
                    "high-zoom selection ring remains visible outside the card");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Paints one tree node with a deterministic pan/zoom transform.
     *
     * @param node tree node
     * @param width component width
     * @param height component height
     * @param zoom zoom factor
     * @param panX horizontal pan
     * @param panY vertical pan
     * @return rendered image
     */
    private static BufferedImage paintTreeNodeWithViewTransform(TreeLayout.Node node,
            int width, int height, double zoom, double panX, double panY) {
        TreeGraphView view = new TreeGraphView();
        view.setSize(width, height);
        TreeLayout.Model model = new TreeLayout.Model(List.of(node), List.of(), width, height,
                node.key(), 1, 0);
        view.setModel(model);
        setField(view, "zoom", Double.valueOf(zoom));
        setField(view, "panX", Double.valueOf(panX));
        setField(view, "panY", Double.valueOf(panY));
        return paint(view, width, height);
    }

    /**
     * Verifies tree board thumbnails do not draw the standalone chessboard edge
     * border; tree selection/PV/search state owns node outlines instead.
     */
    private static void testTreeGraphBoardThumbnailsHaveNoBoardBorder() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            TreeGraphView view = new TreeGraphView();
            int width = 160;
            int height = 220;
            view.setSize(width, height);
            int nodeX = 56;
            int nodeY = 28;
            int nodeW = 48;
            int nodeH = 82;
            MctsSearch.NodeInfo info = new MctsSearch.NodeInfo(
                    "root",
                    "",
                    new short[0],
                    new short[0],
                    Move.NO_MOVE,
                    "root",
                    "",
                    "",
                    "",
                    0,
                    100,
                    0.40,
                    0.0,
                    0.0,
                    0.0,
                    0.55,
                    0.10,
                    0.35,
                    "",
                    "",
                    0,
                    START_FEN,
                    "root",
                    1L);
            TreeLayout.Node node = new TreeLayout.Node("root", info, nodeX, nodeY, nodeW, nodeH,
                    0, true, false, false, false, List.of());
            TreeLayout.Model model = new TreeLayout.Model(List.of(node), List.of(), width, 140, "root", 1, 0);
            view.setModel(model);

            BufferedImage image = paint(view, width, height);
            int panY = 14;
            Color edgePixel = new Color(image.getRGB(nodeX, panY + nodeY), true);
            assertTrue(!Objects.equals(themeColor("BOARD_EDGE"), edgePixel),
                    "tree board thumbnail omits the standalone board border");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Verifies tree thumbnail move markers are rectangular and aligned to device
     * pixels even when the tree is panned and zoomed to fractional coordinates.
     */
    private static void testTreeGraphMoveHighlightsArePixelAlignedFilledRectangles() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            int width = 520;
            int height = 420;
            int nodeX = 18;
            int nodeY = 16;
            int nodeW = 64;
            int nodeH = 98;
            double zoom = 4.35;
            double panX = 23.4;
            double panY = 31.6;
            short move = Move.parse("g1f3");
            MctsSearch.NodeInfo plainInfo = treeInfoWithMove("root", "", Move.NO_MOVE, 0, 100, 1L);
            MctsSearch.NodeInfo moveInfo = treeInfoWithMove("root", "", move, 0, 100, 1L);
            TreeLayout.Node plainNode = new TreeLayout.Node("root", plainInfo, nodeX, nodeY, nodeW, nodeH,
                    0, true, false, false, false, List.of());
            TreeLayout.Node moveNode = new TreeLayout.Node("root", moveInfo, nodeX, nodeY, nodeW, nodeH,
                    0, true, false, false, false, List.of());

            BufferedImage plain = paintTreeNodeWithViewTransform(plainNode, width, height, zoom, panX, panY);
            BufferedImage highlighted = paintTreeNodeWithViewTransform(moveNode, width, height, zoom, panX, panY);
            Rectangle board = new Rectangle(nodeX, nodeY, nodeW, nodeW);
            Rectangle toSquare = BoardStyle.fieldSquareBounds(board, Move.getToIndex(move), true);
            Rectangle pixelSquare = pixelRect(toSquare, zoom, panX, panY);

            int top = countDifferingPixels(plain, highlighted,
                    pixelSquare.x, pixelSquare.y, pixelSquare.width, 1);
            int bottom = countDifferingPixels(plain, highlighted,
                    pixelSquare.x, pixelSquare.y + pixelSquare.height - 1, pixelSquare.width, 1);
            int left = countDifferingPixels(plain, highlighted,
                    pixelSquare.x, pixelSquare.y, 1, pixelSquare.height);
            int right = countDifferingPixels(plain, highlighted,
                    pixelSquare.x + pixelSquare.width - 1, pixelSquare.y, 1, pixelSquare.height);
            int outsideTop = countDifferingPixels(plain, highlighted,
                    pixelSquare.x, pixelSquare.y - 1, pixelSquare.width, 1);
            int center = countDifferingPixels(plain, highlighted,
                    pixelSquare.x + 3, pixelSquare.y + 3,
                    Math.max(1, pixelSquare.width - 6), Math.max(1, pixelSquare.height - 6));
            Color actualCenter = new Color(highlighted.getRGB(
                    pixelSquare.x + pixelSquare.width / 2,
                    pixelSquare.y + pixelSquare.height / 2), true);
            Color expectedCenter = expectedLastMoveFill(
                    pixelRect(board, zoom, panX, panY),
                    Move.getToIndex(move));
            assertTrue(top >= pixelSquare.width - 2,
                    "tree move highlight touches the exact top edge of the square");
            assertTrue(bottom >= pixelSquare.width - 2,
                    "tree move highlight touches the exact bottom edge of the square");
            assertTrue(left >= pixelSquare.height - 2,
                    "tree move highlight touches the exact left edge of the square");
            assertTrue(right >= pixelSquare.height - 2,
                    "tree move highlight touches the exact right edge of the square");
            assertEquals(Integer.valueOf(0), Integer.valueOf(outsideTop),
                    "tree move highlight does not bleed outside the square edge");
            assertTrue(center > Math.max(12, pixelSquare.width * pixelSquare.height / 3),
                    "tree move highlight fills the square interior with a transparent rectangle");
            assertTrue(colorDistance(actualCenter, expectedCenter) <= 1.5,
                    "tree move highlight uses the same fill color as the main board last-move highlight");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Returns the expected center pixel for a board-square last-move fill.
     *
     * @param board device-space board rectangle
     * @param square highlighted square
     * @return composited last-move color
     */
    private static Color expectedLastMoveFill(Rectangle board, byte square) {
        BufferedImage image = new BufferedImage(board.width, board.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            Rectangle localBoard = new Rectangle(0, 0, board.width, board.height);
            BoardStyle.drawBoardSurface(g, localBoard, false);
            BoardStyle.drawFilledSquareHighlight(g,
                    BoardStyle.fieldSquareBounds(localBoard, square, true),
                    Theme.LAST_MOVE_EDGE);
            Rectangle localSquare = BoardStyle.fieldSquareBounds(localBoard, square, true);
            return new Color(image.getRGB(
                    localSquare.x + localSquare.width / 2,
                    localSquare.y + localSquare.height / 2), true);
        } finally {
            g.dispose();
        }
    }

    /**
     * Verifies the cached tree thumbnail key includes the incoming move now that
     * the last-move highlight is baked into the board image.
     */
    @SuppressWarnings("unchecked")
    private static void testTreeGraphBoardCacheSeparatesLastMoveHighlights() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            TreeGraphView view = new TreeGraphView();
            view.setSize(260, 140);
            MctsSearch.NodeInfo leftInfo = treeInfoWithMove("left", "", Move.parse("g1f3"), 0, 100, 1L);
            MctsSearch.NodeInfo rightInfo = treeInfoWithMove("right", "", Move.parse("b1c3"), 0, 100, 2L);
            TreeLayout.Node left = new TreeLayout.Node("left", leftInfo, 20, 16, 64, 98,
                    0, false, false, false, false, List.of());
            TreeLayout.Node right = new TreeLayout.Node("right", rightInfo, 112, 16, 64, 98,
                    0, false, false, false, false, List.of());
            view.setModel(new TreeLayout.Model(List.of(left, right), List.of(), 260, 140, "left", 2, 0));
            setField(view, "zoom", Double.valueOf(1.0));
            setField(view, "panX", Double.valueOf(0.0));
            setField(view, "panY", Double.valueOf(0.0));
            paint(view, 260, 140);

            Map<String, BufferedImage> cache = (Map<String, BufferedImage>) field(view, "boardCache");
            assertTrue(cache.size() >= 2,
                    "tree board cache separates thumbnails with the same FEN but different incoming moves");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Verifies the move-card tree display is a distinct render path and does not
     * populate the board-thumbnail cache.
     */
    @SuppressWarnings("unchecked")
    private static void testTreeGraphMovesDisplayModeUsesMoveCards() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            int width = 220;
            int height = 170;
            short move = Move.parse("g1f3");
            MctsSearch.NodeInfo info = treeInfoWithMove("Nf3", "root", move, 1, 128, 20L);
            TreeLayout.Node node = new TreeLayout.Node("child", info, 70, 24, 80, 114,
                    1, false, false, false, false, List.of());
            TreeLayout.Model model = new TreeLayout.Model(List.of(node), List.of(),
                    width, height, "child", 1, 0);

            TreeGraphView boardView = new TreeGraphView();
            boardView.setSize(width, height);
            boardView.setModel(model);
            setField(boardView, "zoom", Double.valueOf(1.0));
            setField(boardView, "panX", Double.valueOf(0.0));
            setField(boardView, "panY", Double.valueOf(0.0));
            BufferedImage boardImage = paint(boardView, width, height);
            Map<String, BufferedImage> boardCache = (Map<String, BufferedImage>) field(boardView, "boardCache");
            assertTrue(!boardCache.isEmpty(),
                    "board display mode renders through the thumbnail cache");

            TreeGraphView moveView = new TreeGraphView();
            moveView.setSize(width, height);
            moveView.setDisplayMode(TreeGraphView.DisplayMode.MOVES);
            moveView.setModel(model);
            setField(moveView, "zoom", Double.valueOf(1.0));
            setField(moveView, "panX", Double.valueOf(0.0));
            setField(moveView, "panY", Double.valueOf(0.0));
            BufferedImage moveImage = paint(moveView, width, height);
            Map<String, BufferedImage> moveCache = (Map<String, BufferedImage>) field(moveView, "boardCache");
            assertEquals(TreeGraphView.DisplayMode.MOVES, moveView.displayMode(),
                    "tree graph reports move-card display mode");
            assertEquals(Integer.valueOf(0), Integer.valueOf(moveCache.size()),
                    "move-card display mode does not render board thumbnails");

            int differing = countDifferingPixels(boardImage, moveImage,
                    node.x(), node.y(), node.w(), node.h());
            assertTrue(differing > 1_200,
                    "move-card display mode paints a visibly distinct node body");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Verifies capped snapshots paint an explicit omitted-node badge in the
     * graph overlay.
     */
    private static void testTreeGraphShowsOmittedNodeBadge() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            int width = 260;
            int height = 190;
            TreeLayout.Node node = new TreeLayout.Node(
                    "root",
                    treeInfo("root", "", 0, 100, 1L),
                    98,
                    24,
                    64,
                    98,
                    0,
                    true,
                    false,
                    false,
                    false,
                    List.of());

            TreeLayout.Model plainModel = new TreeLayout.Model(
                    List.of(node), List.of(), width, 140, "root", 1, 0, 0);
            TreeGraphView plainView = new TreeGraphView();
            plainView.setSize(width, height);
            plainView.setModel(plainModel);
            setField(plainView, "zoom", Double.valueOf(1.0));
            setField(plainView, "panX", Double.valueOf(0.0));
            setField(plainView, "panY", Double.valueOf(0.0));
            BufferedImage plain = paint(plainView, width, height);

            TreeLayout.Model omittedModel = new TreeLayout.Model(
                    List.of(node), List.of(), width, 140, "root", 1, 0, 42);
            TreeGraphView omittedView = new TreeGraphView();
            omittedView.setSize(width, height);
            omittedView.setModel(omittedModel);
            setField(omittedView, "zoom", Double.valueOf(1.0));
            setField(omittedView, "panX", Double.valueOf(0.0));
            setField(omittedView, "panY", Double.valueOf(0.0));
            BufferedImage omitted = paint(omittedView, width, height);

            int badgeDiff = countDifferingPixels(plain, omitted, 0, height - 58, width, 28);
            assertTrue(badgeDiff > 80,
                    "tree graph paints an omitted-node badge above the legend");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Verifies SVG tree export preserves capped-snapshot omitted-node accounting.
     */
    private static void testTreeSvgExportShowsOmittedNodeBadge() {
        TreeLayout.Node node = new TreeLayout.Node(
                "root",
                treeInfo("root", "", 0, 100, 1L),
                28,
                28,
                64,
                98,
                0,
                true,
                false,
                false,
                false,
                List.of());
        TreeLayout.Model plainModel = new TreeLayout.Model(
                List.of(node), List.of(), 140, 140, "root", 1, 0, 0);
        TreeLayout.Model omittedModel = new TreeLayout.Model(
                List.of(node), List.of(), 140, 140, "root", 1, 0, 42);

        String plain = treeSvg(plainModel);
        String omitted = treeSvg(omittedModel);
        assertFalse(plain.contains("omitted"),
                "tree SVG export leaves omitted badge out when the snapshot is uncapped");
        assertTrue(omitted.contains("+42 omitted"),
                "tree SVG export includes omitted-node badge text");
        assertTrue(omitted.contains("fill-opacity=\"0.24\""),
                "tree SVG export draws the omitted badge as a visible overlay");
    }

    /**
     * Verifies selecting a node can draw the green path edge back toward the
     * root.
     */
    private static void testTreeGraphSelectedPathDrawsGreenEdges() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            int width = 220;
            int height = 240;
            TreeGraphView view = new TreeGraphView();
            view.setSize(width, height);
            MctsSearch.NodeInfo rootInfo = new MctsSearch.NodeInfo(
                    "root",
                    "",
                    new short[0],
                    new short[0],
                    Move.NO_MOVE,
                    "root",
                    "",
                    "",
                    "",
                    0,
                    100,
                    0.10,
                    0.0,
                    0.0,
                    0.0,
                    0.50,
                    0.0,
                    0.50,
                    "",
                    "",
                    0,
                    START_FEN,
                    "root",
                    1L);
            MctsSearch.NodeInfo childInfo = new MctsSearch.NodeInfo(
                    "child",
                    "root",
                    new short[0],
                    new short[0],
                    Move.NO_MOVE,
                    "child",
                    "",
                    "",
                    "",
                    1,
                    40,
                    0.20,
                    0.0,
                    0.0,
                    0.0,
                    0.55,
                    0.05,
                    0.40,
                    "",
                    "",
                    0,
                    START_FEN,
                    "child",
                    2L);
            TreeLayout.Node root = new TreeLayout.Node("root", rootInfo, 90, 28, 40, 64,
                    0, true, false, false, false, List.of());
            TreeLayout.Node child = new TreeLayout.Node("child", childInfo, 90, 132, 40, 64,
                    1, false, false, false, false, List.of());
            TreeLayout.Edge edge = new TreeLayout.Edge("root", "child", "", false);
            TreeLayout.Model model = new TreeLayout.Model(List.of(root, child), List.of(edge),
                    width, 230, "root", 2, 0);
            view.setModel(model);
            BufferedImage plain = paint(view, width, height);

            view.setSelectedPath(Set.of("root", "child"));
            BufferedImage selected = paint(view, width, height);
            int differing = countDifferingPixels(plain, selected);
            assertTrue(differing > 30, "selected tree path paints a green edge overlay");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Verifies a selected path follows the explicit directed segments instead of
     * highlighting every sibling edge whose endpoints happen to be in the path
     * key set.
     */
    private static void testTreeGraphSelectedPathUsesExactSegments() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            int width = 240;
            int height = 210;
            TreeGraphView view = new TreeGraphView();
            view.setSize(width, height);
            MctsSearch.NodeInfo rootInfo = treeInfo("root", "", 0, 100, 1L);
            MctsSearch.NodeInfo leftInfo = treeInfo("left", "root", 1, 40, 2L);
            MctsSearch.NodeInfo rightInfo = treeInfo("right", "root", 1, 45, 3L);
            TreeLayout.Node root = new TreeLayout.Node("root", rootInfo, 100, 24, 40, 64,
                    0, true, false, false, false, List.of());
            TreeLayout.Node left = new TreeLayout.Node("left", leftInfo, 50, 124, 40, 64,
                    1, false, false, false, false, List.of());
            TreeLayout.Node right = new TreeLayout.Node("right", rightInfo, 150, 124, 40, 64,
                    1, false, false, false, false, List.of());
            TreeLayout.Model model = new TreeLayout.Model(
                    List.of(root, left, right),
                    List.of(
                            new TreeLayout.Edge("root", "left", "", false),
                            new TreeLayout.Edge("root", "right", "", false)),
                    width, 205, "root", 3, 0);
            view.setModel(model);
            BufferedImage plain = paint(view, width, height);

            view.setSelectedPath(
                    Set.of("root", "left", "right"),
                    Set.of(new TreeGraphView.PathSegment("root", "right")));
            BufferedImage selected = paint(view, width, height);
            int leftEdgeDiff = countDifferingPixels(plain, selected, 50, 104, 48, 42);
            int rightEdgeDiff = countDifferingPixels(plain, selected, 142, 104, 58, 42);
            assertTrue(rightEdgeDiff > 20,
                    "selected tree path paints the explicit right-hand segment");
            assertTrue(leftEdgeDiff < 12,
                    "selected tree path does not invent a sibling segment");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Exports a tree layout model through the package-private SVG exporter.
     *
     * @param model layout model
     * @return SVG document
     */
    private static String treeSvg(TreeLayout.Model model) {
        return (String) invokeStatic(
                type("TreeSvgExporter"),
                "toSvg",
                new Class<?>[] {
                        TreeLayout.Model.class,
                        Color.class,
                        Color.class,
                        Color.class,
                        Color.class,
                        Color.class,
                        Color.class,
                        Color.class
                },
                model,
                Theme.BG,
                Theme.ACCENT,
                Theme.MUTED,
                Theme.LINE,
                Theme.PANEL_SOLID,
                Theme.TEXT,
                Theme.MUTED);
    }

    /**
     * Finds the rendered graph node that represents a node-info id.
     *
     * @param model graph model
     * @param id node-info id
     * @return rendered node
     */
    private static TreeLayout.Node renderedNodeByInfoId(TreeLayout.Model model, String id) {
        for (TreeLayout.Node node : model.nodes()) {
            if (node.info().id().equals(id)) {
                return node;
            }
        }
        throw new AssertionError("missing rendered node for " + id);
    }

    /**
     * Asserts a rendered graph node is centered in the visible component.
     *
     * @param view graph view
     * @param node rendered node
     * @param width component width
     * @param height component height
     * @param message assertion message
     */
    private static void assertNodeCentered(TreeGraphView view, TreeLayout.Node node,
            int width, int height, String message) {
        double zoom = ((Double) field(view, "zoom")).doubleValue();
        double panX = ((Double) field(view, "panX")).doubleValue();
        double panY = ((Double) field(view, "panY")).doubleValue();
        double x = node.centerX() * zoom + panX;
        double y = node.centerY() * zoom + panY;
        assertTrue(Math.abs(x - width / 2.0) <= 1.0 && Math.abs(y - height / 2.0) <= 1.0,
                message + " (actual=" + x + "," + y + ")");
    }

    /**
     * Creates a minimal node-info row for tree view visual regressions.
     *
     * @param id stable node id
     * @param parentId parent id
     * @param depth depth
     * @param visits visit count
     * @param signature position signature
     * @return node info
     */
    private static MctsSearch.NodeInfo treeInfo(String id, String parentId,
            int depth, int visits, long signature) {
        return treeInfoWithMove(id, parentId, Move.NO_MOVE, depth, visits, signature);
    }

    /**
     * Creates a minimal node-info row with a chosen incoming move.
     *
     * @param id stable node id
     * @param parentId parent id
     * @param move incoming move
     * @param depth depth
     * @param visits visit count
     * @param signature position signature
     * @return node info
     */
    private static MctsSearch.NodeInfo treeInfoWithMove(String id, String parentId,
            short move, int depth, int visits, long signature) {
        String uci = move == Move.NO_MOVE ? "" : Move.toString(move);
        return new MctsSearch.NodeInfo(
                id,
                parentId,
                new short[0],
                new short[0],
                move,
                id,
                uci,
                uci,
                uci,
                depth,
                visits,
                0.10,
                0.0,
                0.0,
                0.0,
                0.50,
                0.0,
                0.50,
                "",
                "",
                0,
                START_FEN,
                id,
                signature);
    }

    /**
     * Converts a world rectangle to the test's expected device-pixel rectangle.
     *
     * @param bounds world bounds
     * @param zoom zoom factor
     * @param panX horizontal pan
     * @param panY vertical pan
     * @return pixel bounds
     */
    private static Rectangle pixelRect(Rectangle bounds, double zoom, double panX, double panY) {
        int x0 = (int) Math.round(bounds.x * zoom + panX);
        int y0 = (int) Math.round(bounds.y * zoom + panY);
        int x1 = (int) Math.round((bounds.x + bounds.width) * zoom + panX);
        int y1 = (int) Math.round((bounds.y + bounds.height) * zoom + panY);
        return new Rectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    /**
     * Verifies the tree layer guide overlay partitions selected-level subtrees.
     */
    private static void testTreeGraphLayerGuidesIncludeVerticalDividers() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            int width = 220;
            int height = 180;
            TreeGraphView view = new TreeGraphView();
            view.setSize(width, height);
            MctsSearch.NodeInfo rootInfo = new MctsSearch.NodeInfo(
                    "root",
                    "",
                    new short[0],
                    new short[0],
                    Move.NO_MOVE,
                    "root",
                    "",
                    "",
                    "",
                    0,
                    20,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.50,
                    0.0,
                    0.50,
                    "",
                    "",
                    0,
                    START_FEN,
                    "root",
                    10L);
            MctsSearch.NodeInfo leftInfo = new MctsSearch.NodeInfo(
                    "left",
                    "",
                    new short[0],
                    new short[0],
                    Move.NO_MOVE,
                    "left",
                    "",
                    "",
                    "",
                    0,
                    10,
                    0.10,
                    0.0,
                    0.0,
                    0.0,
                    0.50,
                    0.0,
                    0.50,
                    "",
                    "",
                    0,
                    START_FEN,
                    "left",
                    11L);
            MctsSearch.NodeInfo rightInfo = new MctsSearch.NodeInfo(
                    "right",
                    "",
                    new short[0],
                    new short[0],
                    Move.NO_MOVE,
                    "right",
                    "",
                    "",
                    "",
                    0,
                    8,
                    -0.10,
                    0.0,
                    0.0,
                    0.0,
                    0.50,
                    0.0,
                    0.50,
                    "",
                    "",
                    0,
                    START_FEN,
                    "right",
                    12L);
            MctsSearch.NodeInfo leftLeafInfo = new MctsSearch.NodeInfo(
                    "leftLeaf",
                    "",
                    new short[0],
                    new short[0],
                    Move.NO_MOVE,
                    "leftLeaf",
                    "",
                    "",
                    "",
                    0,
                    3,
                    0.20,
                    0.0,
                    0.0,
                    0.0,
                    0.50,
                    0.0,
                    0.50,
                    "",
                    "",
                    0,
                    START_FEN,
                    "leftLeaf",
                    13L);
            MctsSearch.NodeInfo rightLeafInfo = new MctsSearch.NodeInfo(
                    "rightLeaf",
                    "",
                    new short[0],
                    new short[0],
                    Move.NO_MOVE,
                    "rightLeaf",
                    "",
                    "",
                    "",
                    0,
                    2,
                    -0.20,
                    0.0,
                    0.0,
                    0.0,
                    0.50,
                    0.0,
                    0.50,
                    "",
                    "",
                    0,
                    START_FEN,
                    "rightLeaf",
                    14L);
            TreeLayout.Node root = new TreeLayout.Node("root", rootInfo, 90, 20, 40, 64,
                    0, true, false, false, false, List.of());
            TreeLayout.Node left = new TreeLayout.Node("left", leftInfo, 40, 94, 40, 64,
                    1, false, false, false, false, List.of());
            TreeLayout.Node right = new TreeLayout.Node("right", rightInfo, 120, 94, 40, 64,
                    1, false, false, false, false, List.of());
            TreeLayout.Node leftLeaf = new TreeLayout.Node("leftLeaf", leftLeafInfo, 46, 168, 40, 64,
                    2, false, false, false, false, List.of());
            TreeLayout.Node rightLeaf = new TreeLayout.Node("rightLeaf", rightLeafInfo, 126, 168, 40, 64,
                    2, false, false, false, false, List.of());
            TreeLayout.Model model = new TreeLayout.Model(
                    List.of(root, left, right, leftLeaf, rightLeaf),
                    List.of(
                            new TreeLayout.Edge("root", "left", "e4", false),
                            new TreeLayout.Edge("root", "right", "d4", false),
                            new TreeLayout.Edge("left", "leftLeaf", "e5", false),
                            new TreeLayout.Edge("right", "rightLeaf", "d5", false)),
                    width, 180, "root", 5, 0);
            view.setModel(model);

            view.setShowLayers(false);
            BufferedImage plain = paint(view, width, height);
            int plainCount = nonBackgroundPixels(plain, Theme.BG, 26, 60, 1, 100);

            view.setShowLayers(true);
            view.setGuidePartitionLayer(0);
            BufferedImage guidesOff = paint(view, width, height);
            int offCentralBoundary = countDifferingPixels(plain, guidesOff, 102, 126, 5, 44);
            assertTrue(offCentralBoundary < 8,
                    "tree guide level 0 hides vertical subtree partitions");

            view.setGuidePartitionLayer(1);
            BufferedImage guided = paint(view, width, height);
            int guidedCount = nonBackgroundPixels(guided, Theme.BG, 26, 60, 1, 100);
            assertTrue(guidedCount > plainCount + 35,
                    "tree layer guides partition each selected-level subtree");
            int centralBoundary = countDifferingPixels(guidesOff, guided, 102, 126, 5, 44);
            int topBoundary = countDifferingPixels(guidesOff, guided, 102, 4, 5, 30);
            int bottomBoundary = countDifferingPixels(guidesOff, guided, 102, height - 32, 5, 30);
            int oldLeftBoundary = countDifferingPixels(guidesOff, guided, 99, 126, 2, 44);
            int oldRightBoundary = countDifferingPixels(guidesOff, guided, 106, 126, 2, 44);
            assertTrue(centralBoundary > offCentralBoundary + 25,
                    "tree guide level draws one shared separator between sibling subtrees");
            assertTrue(topBoundary > 12 && bottomBoundary > 12,
                    "tree guide level dividers extend through the full visible height");
            assertTrue(oldLeftBoundary < 8 && oldRightBoundary < 8,
                    "tree guide level avoids adjacent double separators between subtrees");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Verifies guide partitions keep ragged branches visible when a selected
     * level is deeper than some branch's terminal node.
     */
    private static void testTreeGraphLayerGuidesKeepShortBranchesAtDeeperLevel() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            int width = 220;
            int height = 240;
            TreeGraphView view = new TreeGraphView();
            view.setSize(width, height);
            TreeLayout.Node root = new TreeLayout.Node("root", treeInfo("root", "", 0, 20, 10L),
                    90, 20, 40, 64, 0, true, false, false, false, List.of());
            TreeLayout.Node shortBranch = new TreeLayout.Node("short", treeInfo("short", "root", 1, 8, 11L),
                    40, 94, 40, 64, 1, false, false, false, false, List.of());
            TreeLayout.Node deepBranch = new TreeLayout.Node("deep", treeInfo("deep", "root", 1, 12, 12L),
                    120, 94, 40, 64, 1, false, false, false, false, List.of());
            TreeLayout.Node deepLeaf = new TreeLayout.Node("deepLeaf", treeInfo("deepLeaf", "deep", 2, 4, 13L),
                    126, 168, 40, 64, 2, false, false, false, false, List.of());
            TreeLayout.Model model = new TreeLayout.Model(
                    List.of(root, shortBranch, deepBranch, deepLeaf),
                    List.of(
                            new TreeLayout.Edge("root", "short", "e4", false),
                            new TreeLayout.Edge("root", "deep", "d4", false),
                            new TreeLayout.Edge("deep", "deepLeaf", "d5", false)),
                    width, height, "root", 4, 0);
            view.setModel(model);
            view.setShowLayers(true);
            view.setGuidePartitionLayer(0);
            BufferedImage guidesOff = paint(view, width, height);

            view.setGuidePartitionLayer(2);
            BufferedImage guided = paint(view, width, height);
            int shortBranchBoundary = countDifferingPixels(guidesOff, guided, 24, 8, 5, height - 16);
            int sharedBoundary = countDifferingPixels(guidesOff, guided, 101, 8, 7, height - 16);
            assertTrue(shortBranchBoundary > 30,
                    "deeper guide level keeps the shorter terminal branch partitioned");
            assertTrue(sharedBoundary > 30,
                    "deeper guide level draws a separator between ragged sibling partitions");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Counts pixels in a region that differ from a background color.
     *
     * @param image image
     * @param background expected background
     * @param x x coordinate
     * @param y y coordinate
     * @param width region width
     * @param height region height
     * @return non-background pixel count
     */
    private static int nonBackgroundPixels(BufferedImage image, Color background,
            int x, int y, int width, int height) {
        int count = 0;
        int maxX = Math.min(image.getWidth(), x + Math.max(0, width));
        int maxY = Math.min(image.getHeight(), y + Math.max(0, height));
        int rgb = background.getRGB();
        for (int yy = Math.max(0, y); yy < maxY; yy++) {
            for (int xx = Math.max(0, x); xx < maxX; xx++) {
                if (image.getRGB(xx, yy) != rgb) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Finds the first split pane in a component tree.
     *
     * @param component root component
     * @return split pane
     */
    private static JSplitPane firstSplitPane(Component component) {
        if (component instanceof JSplitPane split) {
            return split;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return firstSplitPane(child);
                } catch (AssertionError ex) {
                    // Continue searching siblings.
                }
            }
        }
        throw new AssertionError("missing split pane");
    }

    /**
     * Waits for an MCTS session snapshot that satisfies the expected state predicate.
     *
     * @param session session to poll
     * @param predicate snapshot predicate that marks completion
     * @param label assertion label used in timeout and error messages
     * @return first matching session snapshot
     */
    private static MctsSession.Snapshot waitForMctsSession(
            MctsSession session,
            Predicate<MctsSession.Snapshot> predicate,
            String label) {
        long deadline = System.currentTimeMillis() + 5_000L;
        MctsSession.Snapshot snapshot = session.snapshot();
        while (System.currentTimeMillis() < deadline) {
            flushEdt();
            snapshot = session.snapshot();
            if (predicate.test(snapshot)) {
                return snapshot;
            }
            if (snapshot.state() == MctsSession.State.ERROR) {
                throw new AssertionError(label + ": " + snapshot.error());
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(label + " interrupted", ex);
            }
        }
        throw new AssertionError(label + " timed out at " + snapshot.state());
    }

    /**
     * Verifies the Dashboard tab is the first tab in the workbench window.
     */
    private static void testDashboardTabIsFirst() {
        Class<?> window = type("Window");
        assertEquals(Integer.valueOf(0), staticField(window, "TAB_DASHBOARD"),
                "Dashboard is the first tab");
        assertEquals(Integer.valueOf(1), staticField(window, "TAB_BOARD"),
                "Board follows the Dashboard tab");
        assertEquals(Integer.valueOf(4), staticField(window, "BOARD_DRAW"),
                "Draw is the fifth board mode");
        assertEquals(Integer.valueOf(1), staticField(window, "ENGINE_SEARCH"),
                "Search is the second engine mode");
        assertEquals(staticField(window, "ENGINE_SEARCH"), staticField(window, "ENGINE_TREE"),
                "legacy Tree navigation opens the Search mode");
        assertEquals(Integer.valueOf(2), staticField(window, "ENGINE_GAUNTLET"),
                "Gauntlet follows the combined Search mode");
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
