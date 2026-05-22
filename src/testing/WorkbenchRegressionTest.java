package testing;

import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;

import application.cli.CliCommand;
import application.cli.CliRegistry;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.window.LayoutMenu;
import application.gui.workbench.window.SettingsMenu;
import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;
import chess.nn.nnue.FeatureEncoder;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.uci.Output;
import utility.CommandLine;

/**
 * Headless regression checks for workbench support classes.
 */
@SuppressWarnings("java:S2187")
public final class WorkbenchRegressionTest {

    /**
     * Workbench root package.
     */
    private static final String WORKBENCH_PACKAGE = "application.gui.workbench.";

    /**
     * Feature packages used by the split workbench implementation.
     */
    private static final String[] WORKBENCH_SUBPACKAGES = {
            "board.",
            "command.",
            "dashboard.",
            "game.",
            "layout.",
            "mcts.",
            "network.",
            "publish.",
            "session.",
            "ui.",
            "window."
    };

    /**
     * Shared standard starting position.
     */
    private static final String START_FEN = Game.STANDARD_START_FEN;

    /**
     * Simple mate-in-one position: {@code Qg7#}.
     */
    private static final String MATE_IN_ONE_FEN =
            "7k/8/5KQ1/8/8/8/8/8 w - - 0 1";

    /**
     * User-reported forced mate in four for workbench MCTS proof shortcuts.
     */
    private static final String FORCED_MATE_IN_FOUR_FEN =
            "7k/3rrrpp/8/8/8/8/PP6/1KR5 w - - 0 1";

    /**
     * Use column index in the command option table.
     */
    private static final int COL_USE = 0;

    /**
     * Flag column index in the command option table.
     */
    private static final int COL_FLAG = 1;

    /**
     * Value column index in the command option table.
     */
    private static final int COL_VALUE = 2;

    /**
     * Prevents instantiation.
     */
    private WorkbenchRegressionTest() {
        // utility
    }

    /**
     * Runs all workbench regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testFirstFenLineSkipsNonFenRows();
        testBatchFenValidationReportsLineErrors();
        testCommandOptionConflictsDisableStaleRows();
        testEvaluatorSelectorsUseExplicitDefaults();
        testCommandFormatSelectorsUseDirectChoices();
        testDynamicOptionRefresh();
        testDynamicOptionRefreshSkipsUnchangedValues();
        testEngineTemplateContextFeedsExternalConfigOptions();
        testEngineBatchTasksUseExternalConfigOptions();
        testBatchPanelStartsWithEmptyInputAndSharedDuration();
        testCommandTemplatesHaveCompactTabLabels();
        testCommandControllerCoversCliRegistry();
        testBatchRunnerOffersCliScriptForAllCliCommands();
        testCommandLineTokenizerPreservesQuotedArguments();
        testPublishingPreviewFenCompaction();
        testPublishingVisualPreviewPages();
        testPaletteTokenMatching();
        testOptionFilterTokenMatching();
        testTextAreaScrollPaintsOpaque();
        testScrollPaneUsesSolidCorners();
        testViewportFillWrapperTracksAvailableHeight();
        testDataSurfacesUseSolidBackgrounds();
        testCustomPaintedSurfacesClearBackground();
        testBooleanTableRendererIsStyled();
        testComponentTreeStylingCoversPlainControls();
        testDisabledComboUsesThemeBackground();
        testGameLineImportInputKeepsMultilineHeight();
        testSettingsToggleRowsAreReadable();
        testSettingsMenuExposesThemeModes();
        testLayoutMenuExposesUsefulWorkbenchControls();
        testToggleSwitchAnimatesStateChanges();
        testCommandFormOptionalTogglesFillLeadColumn();
        testThemeColorContrast();
        testThemeUsesVscodeChromeWithPastelAccentTokens();
        testNetworkPaletteUsesSemanticFocusColor();
        testThemeRefreshPreservesLabelRoles();
        testThemeRefreshUpdatesLineBorders();
        testThemeRefreshRestoresCustomControlUis();
        testThemeInstallSetsTooltipColors();
        testTextPlaceholdersDoNotSetValues();
        testCollapsibleInfoSectionTogglesContent();
        testCommandTabsReserveSelectedTextWidth();
        testTabbedPaneSwitchesWithoutSnapshotOverlay();
        testSplitAreaUsesIndependentEditorGroups();
        testSplitAreaSupportsCornerEditorGroups();
        testSplitAreaDocksDraggedTabsBackIntoGroup();
        testSplitAreaExposesFlexibleTabActions();
        testEditorShellUsesVscodeStyleSplitChrome();
        testEditorShellShowsRookWatermarkWhenEmpty();
        testEditorShellRefreshesHiddenPanelTheme();
        testButtonHoverTransitionStarts();
        testWorkbenchTimingDefaultsAreSnappy();
        testWorkbenchOperationalDefaultsAreSnappy();
        testResetButtonUsesResetIcon();
        testButtonDisabledIconIsMuted();
        testIconOnlyButtonKeepsIconAfterThemeRefresh();
        testBoardNavigationButtonsUseTransportIcons();
        testBoardNavigationButtonsExposeShortcutTooltips();
        testCommandPreviewQuoting();
        testWorkbenchSanRendererUsesNeutralFigurines();
        testGameModelLoadsPgnVariations();
        testEcoExplorerFiltersAndLoadsLines();
        testEvalBarMapping();
        testEvalBarAnimation();
        testEvalBarFrameFollowsTheme();
        testEvalBarThinkingIsStatic();
        testEngineEvalParsing();
        testLiveEngineStatusFormatting();
        testOptionalPositiveIntegerParsing();
        testAnalysisGraphStoresSamples();
        testAnalysisGraphExportsReportData();
        testAnalysisGraphPaintsOpaqueSurface();
        testBoardHasNoInstructionTooltip();
        testBoardHasNoKeyboardPieceSelector();
        testBoardEditorBuildsAndAppliesFen();
        testWindowPositionNavigationRoutingSkipsTextAndDataControls();
        testBoardRightDragTogglesLichessArrowMarkup();
        testBoardRightClickTogglesLichessCircleMarkup();
        testBoardRightDragReplacesMarkupColorLikeLichess();
        testBoardDragEmitsLegalMove();
        testBoardDragInvalidHoverDoesNotPaintRedBox();
        testBoardDragDirtyBoundsIncludesInvalidHoverSquare();
        testBoardMoveAnimationStarts();
        testBoardCaptureAnimationStarts();
        testBoardCastlingAnimationStarts();
        testBoardPaintUsesChessboardJsColors();
        testBoardSuggestedMoveArrowIsLegalAndClean();
        testBoardLegalMovePreviewCanBeHidden();
        testBoardLastMoveAndBestArrowCanBeHidden();
        testBoardNotationAndAnimationsCanBeHidden();
        testBoardReverseMoveAnimationStarts();
        testBoardCheckHighlightPaintsCheckedKingMarker();
        testBoardTextureCachesRenderedLayer();
        testBoardPieceImageCacheReusesScaledSvg();
        testTensorMiniBoardPiecesRenderWhiteBottom();
        testDashboardClassesLoad();
        testWorkbenchSessionNotifiesListeners();
        testJobLifecycleTransitions();
        testRunLogWritesFullOutput();
        testRunLogAvoidsClobberingExistingFile();
        testRunManifestWritesReplayMetadata();
        testRunManifestAvoidsClobberingExistingFile();
        testJobHistoryIsBounded();
        testCommandResultParserSummaries();
        testDashboardPanelConstructsHeadlessly();
        testNetworkPanelSimpleControlsRenderHeadlessly();
        testNetworkMctsUpdatesAreNonBlocking();
        testNetworkDiagnosticsPreviewHighlightsConfig();
        testNetworkDiagnosticsPreviewRecolorsForDarkTheme();
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
        System.out.println("WorkbenchRegressionTest: all checks passed");
    }

    /**
     * Verifies pasted notes before a FEN do not prevent FEN import.
     */
    private static void testFirstFenLineSkipsNonFenRows() {
        String text = "analysis note" + System.lineSeparator() + START_FEN;
        assertEquals(START_FEN, invokeStatic(type("Window"), "firstFenLine",
                new Class<?>[] { String.class }, text), "first FEN after non-FEN note");
    }

    /**
     * Verifies batch FEN validation surfaces the exact failing row before a
     * command is launched.
     */
    private static void testBatchFenValidationReportsLineErrors() {
        Object summary = invokeStatic(type("Window"), "validateBatchFenInput",
                new Class<?>[] { String.class }, START_FEN + System.lineSeparator() + "not a fen");
        assertEquals(Integer.valueOf(2), invoke(summary, "rows", new Class<?>[0]), "batch FEN row count");
        assertEquals(Integer.valueOf(1), invoke(summary, "validRows", new Class<?>[0]), "batch valid row count");
        assertEquals(Integer.valueOf(2), invoke(summary, "firstErrorLine", new Class<?>[0]),
                "batch FEN error line");
        assertTrue((Boolean) invoke(summary, "hasError", new Class<?>[0]), "batch FEN error flag");
    }

    /**
     * Verifies mutually-exclusive command-builder rows disable stale selections.
     */
    private static void testCommandOptionConflictsDisableStaleRows() {
        Object options = optionsFor("Generate FENs");
        int exactPieces = rowForFlag(options, "--pieces");
        int minPieces = rowForFlag(options, "--min-pieces");

        invoke(options, "setValueAt", new Class<?>[] { Object.class, int.class, int.class },
                "8", exactPieces, COL_VALUE);
        assertTrue(hasFlag(enabledArgs(options), "--pieces"), "exact pieces enabled");

        invoke(options, "setValueAt", new Class<?>[] { Object.class, int.class, int.class },
                "4", minPieces, COL_VALUE);
        List<String> args = enabledArgs(options);
        assertFalse(hasFlag(args, "--pieces"), "exact pieces disabled by min pieces");
        assertTrue(hasFlag(args, "--min-pieces"), "min pieces enabled");
    }

    /**
     * Verifies evaluator selectors expose concrete defaults instead of a
     * misleading "none" option.
     */
    private static void testEvaluatorSelectorsUseExplicitDefaults() {
        Object eval = optionsFor("Eval");
        assertTrue(hasRowForFlag(eval, "auto"), "eval exposes auto evaluator default");
        assertFalse(hasRowForFlag(eval, "none"), "eval does not expose none evaluator");
        assertFalse(hasFlag(enabledArgs(eval), "--lc0"), "eval auto emits no lc0 shortcut");
        assertFalse(hasFlag(enabledArgs(eval), "--classical"), "eval auto emits no classical shortcut");

        invoke(eval, "setValueAt", new Class<?>[] { Object.class, int.class, int.class },
                Boolean.TRUE, rowForFlag(eval, "--classical"), COL_USE);
        List<String> evalClassicalArgs = enabledArgs(eval);
        assertTrue(hasFlag(evalClassicalArgs, "--classical"), "eval classical shortcut emits flag");
        assertFalse(hasFlag(evalClassicalArgs, "--lc0"), "eval classical disables lc0 shortcut");

        Object builtin = optionsFor("Built-in search");
        assertTrue(hasRowForFlag(builtin, "classical"), "built-in exposes classical evaluator default");
        assertFalse(hasRowForFlag(builtin, "none"), "built-in does not expose none evaluator");
        assertFalse(hasRowForFlag(builtin, "--evaluator"), "built-in removes duplicate evaluator value row");
        assertFalse(hasFlag(enabledArgs(builtin), "--classical"), "built-in default emits no redundant shortcut");

        invoke(builtin, "setValueAt", new Class<?>[] { Object.class, int.class, int.class },
                Boolean.TRUE, rowForFlag(builtin, "--lc0"), COL_USE);
        List<String> builtinLc0Args = enabledArgs(builtin);
        assertTrue(hasFlag(builtinLc0Args, "--lc0"), "built-in lc0 shortcut emits flag");
        assertFalse(hasFlag(builtinLc0Args, "--nnue"), "built-in lc0 disables nnue shortcut");
    }

    /**
     * Verifies command formats are direct selector choices instead of a
     * redundant format chip that opens a second dropdown.
     */
    private static void testCommandFormatSelectorsUseDirectChoices() {
        Object legalMoves = optionsFor("Legal moves");
        assertTrue(hasRowForFlag(legalMoves, "UCI"), "legal moves exposes UCI direct choice");
        assertTrue(hasRowForFlag(legalMoves, "SAN"), "legal moves exposes SAN direct choice");
        assertTrue(hasRowForFlag(legalMoves, "Both"), "legal moves exposes Both direct choice");
        assertFalse(hasRowForFlag(legalMoves, "--format"), "legal moves hides redundant format row");
        assertEquals("both", valueAfterFlag(enabledArgs(legalMoves), "--format"), "legal moves default format");

        invoke(legalMoves, "setValueAt", new Class<?>[] { Object.class, int.class, int.class },
                Boolean.TRUE, rowForFlag(legalMoves, "SAN"), COL_USE);
        assertEquals("san", valueAfterFlag(enabledArgs(legalMoves), "--format"), "legal moves SAN format");

        Object builtin = optionsFor("Built-in search");
        assertTrue(hasRowForFlag(builtin, "Summary"), "built-in exposes summary direct choice");
        assertTrue(hasRowForFlag(builtin, "UCI info"), "built-in exposes uci-info direct choice");
        assertFalse(hasRowForFlag(builtin, "--format"), "built-in hides redundant format row");
        assertEquals("summary", valueAfterFlag(enabledArgs(builtin), "--format"), "built-in default format");

        Object perft = optionsFor("Perft");
        assertTrue(hasRowForFlag(perft, "detail"), "perft exposes detail default");
        assertTrue(hasRowForFlag(perft, "Table"), "perft exposes table direct choice");
        assertTrue(hasRowForFlag(perft, "Stockfish"), "perft exposes stockfish direct choice");
        assertFalse(hasFlag(enabledArgs(perft), "--format"), "perft default emits no redundant format");
        invoke(perft, "setValueAt", new Class<?>[] { Object.class, int.class, int.class },
                Boolean.TRUE, rowForFlag(perft, "Stockfish"), COL_USE);
        assertEquals("stockfish", valueAfterFlag(enabledArgs(perft), "--format"), "perft stockfish format");
    }

    /**
     * Verifies dynamic command values follow the latest shared context.
     */
    private static void testDynamicOptionRefresh() {
        Object options = optionsFor("Best move");
        int fenRow = rowForFlag(options, "--fen");
        String nextFen = "8/8/8/8/8/8/K7/7k b - - 1 1";
        invoke(options, "refreshDynamicValues", new Class<?>[] { type("CommandTemplates$TemplateContext") },
                templateContext(nextFen, "9s", "5", "2", "1"));
        assertEquals(nextFen, String.valueOf(invoke(options, "getValueAt",
                new Class<?>[] { int.class, int.class }, fenRow, COL_VALUE)), "dynamic FEN refresh");
    }

    /**
     * Verifies dynamic option refreshes avoid redundant table repaint events.
     */
    private static void testDynamicOptionRefreshSkipsUnchangedValues() {
        Object options = optionsFor("Best move");
        int[] events = { 0 };
        ((javax.swing.table.AbstractTableModel) options).addTableModelListener(event -> events[0]++);

        invoke(options, "refreshDynamicValues", new Class<?>[] { type("CommandTemplates$TemplateContext") },
                templateContext(START_FEN, "2s", "4", "3", "1"));
        assertEquals(Integer.valueOf(0), Integer.valueOf(events[0]), "unchanged dynamic values do not fire events");

        invoke(options, "refreshDynamicValues", new Class<?>[] { type("CommandTemplates$TemplateContext") },
                templateContext("8/8/8/8/8/8/K7/7k b - - 1 1", "2s", "4", "3", "1"));
        assertTrue(events[0] > 0, "changed dynamic values still fire events");
    }

    /**
     * Verifies engine command templates pick up shared external-engine settings
     * when those optional rows are enabled.
     */
    private static void testEngineTemplateContextFeedsExternalConfigOptions() {
        Object options = optionsFor("Analyze");
        Object context = templateContext(START_FEN, "3s", "4", "2", "8",
                "config/lc0.engine.toml", "1200", "256");
        invoke(options, "refreshDynamicValues", new Class<?>[] { type("CommandTemplates$TemplateContext") },
                context);

        assertEquals("config/lc0.engine.toml", optionValue(options, "--protocol-path"),
                "dynamic engine protocol");
        assertEquals("1200", optionValue(options, "--max-nodes"), "dynamic engine nodes");
        assertEquals("256", optionValue(options, "--hash"), "dynamic engine hash");
    }

    /**
     * Verifies batch engine workflows inherit external-engine settings.
     */
    private static void testEngineBatchTasksUseExternalConfigOptions() {
        Object task = batchTask("Analyze batch");
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) invoke(task, "build",
                new Class<?>[] { java.nio.file.Path.class, type("CommandTemplates$TemplateContext") },
                java.nio.file.Path.of("positions.txt"),
                templateContext(START_FEN, "4s", "5", "3", "6",
                        "config/default.engine.toml", "900", "128"));

        assertTrue(hasFlag(args, "--protocol-path"), "batch protocol flag");
        assertTrue(hasFlag(args, "--max-nodes"), "batch nodes flag");
        assertTrue(hasFlag(args, "--threads"), "batch threads flag");
        assertTrue(hasFlag(args, "--hash"), "batch hash flag");
        assertTrue(hasFlag(args, "--jsonl"), "batch jsonl flag");
    }

    /**
     * Verifies batch workflows do not start with demo FENs hidden in the input
     * and inherit the shared interactive duration default.
     */
    private static void testBatchPanelStartsWithEmptyInputAndSharedDuration() {
        Class<?> hostType = type("BatchPanel$Host");
        Object host = Proxy.newProxyInstance(WorkbenchRegressionTest.class.getClassLoader(),
                new Class<?>[] { hostType }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "currentFen":
                            return START_FEN;
                        case "templateContext":
                            return templateContext(START_FEN, "1s", "4", "2", "1");
                        default:
                            return null;
                    }
                });
        Object panel = construct(type("BatchPanel"),
                new Class<?>[] {
                        hostType,
                        javax.swing.SpinnerNumberModel.class,
                        javax.swing.SpinnerNumberModel.class,
                        javax.swing.SpinnerNumberModel.class
                },
                host,
                new javax.swing.SpinnerNumberModel(4, 1, 99, 1),
                new javax.swing.SpinnerNumberModel(2, 1, 20, 1),
                new javax.swing.SpinnerNumberModel(1, 1, 256, 1));

        JTextArea input = (JTextArea) field(panel, "batchInput");
        JTextField duration = (JTextField) field(panel, "batchDurationField");
        assertEquals("", input.getText(), "batch FEN input starts empty");
        assertEquals("1s", duration.getText(), "batch duration uses shared default");
    }

    /**
     * Verifies command templates expose stable labels suitable for command tabs.
     */
    @SuppressWarnings("unchecked")
    private static void testCommandTemplatesHaveCompactTabLabels() {
        List<Object> templates = (List<Object>) invokeStatic(type("CommandTemplates"),
                "commandTemplates", new Class<?>[0]);
        DefaultComboBoxModel<?> model = (DefaultComboBoxModel<?>) invokeStatic(type("CommandTemplates"),
                "commandModel", new Class<?>[0]);
        assertEquals(Integer.valueOf(model.getSize()), Integer.valueOf(templates.size()),
                "command template tab count");
        assertTrue(templates.size() > 4, "command template tab coverage");
        for (int i = 0; i < templates.size(); i++) {
            String name = String.valueOf(invoke(templates.get(i), "name", new Class<?>[0]));
            assertFalse(name.isBlank(), "command tab label is not blank");
            for (int j = 0; j < i; j++) {
                String prior = String.valueOf(invoke(templates.get(j), "name", new Class<?>[0]));
                assertFalse(name.equals(prior), "command tab labels are unique");
            }
        }
    }

    /**
     * Verifies the generic command-controller template is backed by the real CLI
     * registry.
     */
    @SuppressWarnings("unchecked")
    private static void testCommandControllerCoversCliRegistry() {
        Object allCli = template("All CLI");
        List<Object> options = (List<Object>) invoke(allCli, "options", new Class<?>[0]);
        List<String> choices = List.of();
        for (Object option : options) {
            List<String> candidate = (List<String>) invoke(option, "choices", new Class<?>[0]);
            if (!candidate.isEmpty()) {
                choices = candidate;
                break;
            }
        }
        assertFalse(choices.isEmpty(), "all-cli template exposes command choices");
        for (String path : runnableCliPaths()) {
            assertTrue(choices.contains(path), "all-cli template covers " + path);
        }
        for (String choice : choices) {
            CliCommand command = CliRegistry.resolve(List.of(choice.split("\\s+")));
            assertTrue(command != null && command.isRunnable(), "all-cli choice resolves: " + choice);
        }
    }

    /**
     * Verifies the batch runner can execute arbitrary CLI command scripts.
     */
    @SuppressWarnings("unchecked")
    private static void testBatchRunnerOffersCliScriptForAllCliCommands() {
        Object task = batchTask("CLI script");
        assertEquals("COMMAND_LINES", String.valueOf(invoke(task, "inputKind", new Class<?>[0])),
                "CLI script uses command-line input");
        assertEquals(Boolean.FALSE, invoke(task, "usesFenInput", new Class<?>[0]),
                "CLI script does not validate commands as FEN");
        assertEquals(Boolean.TRUE, invoke(task, "usesCommandInput", new Class<?>[0]),
                "CLI script validates command rows");
        Path script = Path.of("/tmp/crtk-commands.txt");
        List<String> args = (List<String>) invoke(task, "build",
                new Class<?>[] { Path.class, type("CommandTemplates$TemplateContext") },
                script, templateContext(START_FEN, "1s", "4", "2", "1"));
        assertEquals(List.of("batch", "run", "--input", script.toString(), "--keep-going"), args,
                "CLI script delegates to batch run");
    }

    /**
     * Verifies raw command fields preserve quoted values and reject unfinished
     * quotes.
     */
    private static void testCommandLineTokenizerPreservesQuotedArguments() {
        assertEquals(List.of("move", "list", "--fen", "8/8/8/8/8/8/K7/7k w - - 0 1",
                "--label", "", "a b"),
                CommandLine.split("move list --fen \"8/8/8/8/8/8/K7/7k w - - 0 1\" --label '' a\\ b"),
                "command tokenizer preserves quoted FENs and empty values");
        assertEquals(List.of("book", "render", "--input", "C:\\Users\\Lennart\\book.toml"),
                CommandLine.split("book render --input C:\\Users\\Lennart\\book.toml"),
                "command tokenizer preserves literal path backslashes");
        try {
            CommandLine.split("move list \"unterminated");
            throw new AssertionError("unterminated quote should fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unclosed quote"),
                    "unterminated quote message");
        }
    }

    /**
     * Returns canonical runnable CLI paths from the central registry.
     *
     * @return runnable paths
     */
    private static List<String> runnableCliPaths() {
        List<String> paths = new ArrayList<>();
        collectRunnableCliPaths(CliRegistry.root(), paths);
        return List.copyOf(paths);
    }

    /**
     * Recursively collects runnable CLI paths.
     *
     * @param command current command node
     * @param paths destination list
     */
    private static void collectRunnableCliPaths(CliCommand command, List<String> paths) {
        if (command.isRunnable() && !command.commandPath().isBlank()) {
            paths.add(command.commandPath());
        }
        for (CliCommand child : command.children()) {
            collectRunnableCliPaths(child, paths);
        }
    }

    /**
     * Verifies publishing preview FEN labels stay compact.
     */
    private static void testPublishingPreviewFenCompaction() {
        assertEquals("8/8/8/8/8/8/K7/7k b", invokeStatic(type("Window"), "compactFenPreview",
                new Class<?>[] { String.class }, "8/8/8/8/8/8/K7/7k b - - 4 22"),
                "publishing preview compact FEN");
    }

    /**
     * Verifies the visual publishing preview tracks page navigation.
     */
    private static void testPublishingVisualPreviewPages() {
        JComponent preview = (JComponent) construct(type("PublishPreview"), new Class<?>[0]);
        Object data = construct(type("PublishPreview$Preview"),
                new Class<?>[] { String.class, String.class, String.class, String.class, String.class,
                        boolean.class, String.class, int.class, boolean.class, boolean.class,
                        boolean.class, boolean.class },
                "Study Book", "Test Study", "Subtitle", "source", "output", Boolean.TRUE, "", 3,
                Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);
        invoke(preview, "setPreview", new Class<?>[] { type("PublishPreview$Preview") }, data);
        assertEquals("page 1 / 3", invoke(preview, "pageLabel", new Class<?>[0]),
                "publishing preview initial page");
        invoke(preview, "nextPage", new Class<?>[0]);
        assertEquals("page 2 / 3", invoke(preview, "pageLabel", new Class<?>[0]),
                "publishing preview next page");
        invoke(preview, "previousPage", new Class<?>[0]);
        assertEquals(Integer.valueOf(1), invoke(preview, "pageNumber", new Class<?>[0]),
                "publishing preview previous page");
        assertPaintsOpaqueCorner(preview, 280, 360, "publishing preview paints surface");
    }

    /**
     * Verifies placeholder examples are hints, not real field values.
     */
    private static void testTextPlaceholdersDoNotSetValues() {
        JTextField field = new JTextField();
        invokeStatic(type("Ui"), "placeholder",
                new Class<?>[] { javax.swing.text.JTextComponent.class, String.class },
                field, "path/to/input.pgn");
        assertEquals("", field.getText(), "placeholder leaves text empty");
        assertEquals("path/to/input.pgn", field.getToolTipText(), "placeholder tooltip");
    }

    /**
     * Verifies palette search uses all query tokens.
     */
    private static void testPaletteTokenMatching() {
        Object action = construct(type("CommandPalette$PaletteAction"),
                new Class<?>[] { String.class, String.class, Runnable.class },
                "Run publishing", "Execute the selected book workflow", (Runnable) () -> {
                    // no-op test action
                });
        assertTrue((Boolean) invoke(action, "matches", new Class<?>[] { String.class }, "publish book"),
                "palette multi-token match");
        assertFalse((Boolean) invoke(action, "matches", new Class<?>[] { String.class }, "publish batch"),
                "palette missing token");
    }

    /**
     * Verifies command option filtering matches tokens across row columns.
     */
    private static void testOptionFilterTokenMatching() {
        assertTrue(optionFilterMatches("min pieces", "--min-pieces", "", "Minimum piece count"),
                "option filter split token match");
        assertTrue(optionFilterMatches("output dir", "--output", "workbench-fens", "Output directory"),
                "option filter cross-column match");
        assertFalse(optionFilterMatches("output mate", "--output", "workbench-fens", "Output directory"),
                "option filter missing token");
    }

    /**
     * Verifies text-area scroll panes repaint with solid backgrounds.
     */
    private static void testTextAreaScrollPaintsOpaque() {
        JTextArea area = new JTextArea("report");
        invokeStatic(type("Theme"), "area", new Class<?>[] { JTextArea.class }, area);
        JScrollPane pane = (JScrollPane) invokeStatic(type("Ui"), "scroll",
                new Class<?>[] { javax.swing.JComponent.class }, area);
        assertEquals(255, area.getBackground().getAlpha(), "text area alpha");
        assertTrue(pane.getViewport().isOpaque(), "text area viewport opaque");
        assertEquals(area.getBackground(), pane.getViewport().getBackground(), "viewport background");
    }

    /**
     * Verifies non-opaque scroll views still get solid viewport and corner fills.
     */
    private static void testScrollPaneUsesSolidCorners() {
        JPanel view = new JPanel();
        view.setOpaque(false);
        JScrollPane pane = (JScrollPane) invokeStatic(type("Ui"), "scroll",
                new Class<?>[] { javax.swing.JComponent.class }, view);
        assertTrue(pane.getViewport().isOpaque(), "solid viewport opaque fallback");
        assertEquals(Integer.valueOf(255), Integer.valueOf(pane.getViewport().getBackground().getAlpha()),
                "viewport solid alpha");
        assertTrue(pane.getCorner(javax.swing.ScrollPaneConstants.UPPER_RIGHT_CORNER).isOpaque(),
                "upper right scroll corner opaque");
        assertEquals(pane.getViewport().getBackground(),
                pane.getCorner(javax.swing.ScrollPaneConstants.LOWER_RIGHT_CORNER).getBackground(),
                "scroll corner background");
    }

    /**
     * Verifies short scroll content expands to the available viewport height.
     */
    private static void testViewportFillWrapperTracksAvailableHeight() {
        JPanel child = new JPanel();
        child.setPreferredSize(new Dimension(200, 100));
        JComponent wrapper = (JComponent) invokeStatic(type("Ui"), "fillViewport",
                new Class<?>[] { javax.swing.JComponent.class }, child);
        assertTrue(wrapper instanceof Scrollable, "viewport fill wrapper is scrollable");

        JViewport viewport = new JViewport();
        viewport.setSize(new Dimension(320, 360));
        viewport.setView(wrapper);
        Scrollable scrollable = (Scrollable) wrapper;
        assertTrue(scrollable.getScrollableTracksViewportWidth(), "viewport fill tracks width");
        assertTrue(scrollable.getScrollableTracksViewportHeight(), "short content fills viewport height");

        child.setPreferredSize(new Dimension(200, 520));
        wrapper.revalidate();
        assertFalse(scrollable.getScrollableTracksViewportHeight(), "tall content keeps vertical scrolling");
    }

    /**
     * Verifies table and list data surfaces avoid translucent opaque repaint trails.
     */
    private static void testDataSurfacesUseSolidBackgrounds() {
        JTable table = new JTable(1, 1);
        invokeStatic(type("Theme"), "table", new Class<?>[] { JTable.class, int.class }, table, 24);
        assertTrue(table.isOpaque(), "table opaque");
        assertEquals(Integer.valueOf(255), Integer.valueOf(table.getBackground().getAlpha()), "table solid alpha");
        assertFalse(table.getShowHorizontalLines(), "table grid artifacts disabled");
        Component header = table.getTableHeader().getDefaultRenderer()
                .getTableCellRendererComponent(table, "Header", false, false, -1, 0);
        assertEquals(themeColor("PANEL_SOLID"), header.getBackground(), "table header background themed");
        assertEquals(themeColor("MUTED"), header.getForeground(), "table header foreground themed");

        JList<String> list = new JList<>(new String[] { "a" });
        invokeStatic(type("Theme"), "list", new Class<?>[] { JList.class }, list);
        assertTrue(list.isOpaque(), "list opaque");
        assertEquals(Integer.valueOf(255), Integer.valueOf(list.getBackground().getAlpha()), "list solid alpha");
    }

    /**
     * Verifies custom-painted surfaces clear their own background so partial
     * repaints cannot leave translucent trails.
     */
    private static void testCustomPaintedSurfacesClearBackground() {
        JComponent panel = (JComponent) construct(type("SurfacePanel"),
                new Class<?>[] { java.awt.LayoutManager.class }, new java.awt.BorderLayout());
        assertPaintsOpaqueCorner(panel, 160, 80, "solid panel clears background");

        JComponent board = (JComponent) construct(type("BoardPanel"), new Class<?>[0]);
        assertPaintsOpaqueCorner(board, 320, 320, "board panel clears margin background");

        JComponent eval = (JComponent) construct(type("EvalBar"), new Class<?>[0]);
        assertPaintsOpaqueCorner(eval, 40, 260, "eval bar clears background");
    }

    /**
     * Verifies boolean table cells do not fall back to Swing's default checkbox styling.
     */
    private static void testBooleanTableRendererIsStyled() {
        JTable table = new JTable(1, 1);
        invokeStatic(type("Theme"), "table", new Class<?>[] { JTable.class, int.class }, table, 24);
        TableCellRenderer renderer = table.getDefaultRenderer(Boolean.class);
        Component cell = renderer.getTableCellRendererComponent(table, Boolean.TRUE, true, false, 0, 0);
        assertTrue(cell instanceof JComponent, "boolean renderer component");
        assertTrue(((JComponent) cell).isOpaque(), "boolean renderer opaque");
        assertEquals(table.getSelectionBackground(), cell.getBackground(), "boolean renderer selected background");
        assertTrue(table.getDefaultEditor(Boolean.class) != null, "boolean table editor installed");
    }

    /**
     * Verifies recursive styling catches plain controls from dialogs and file choosers.
     */
    private static void testComponentTreeStylingCoversPlainControls() {
        JPanel root = new JPanel();
        JTextField field = new JTextField("path");
        JButton button = new JButton("Open");
        JList<String> list = new JList<>(new String[] { "one" });
        JScrollPane pane = new JScrollPane(list);
        root.add(field);
        root.add(button);
        root.add(pane);

        invokeStatic(type("Ui"), "styleComponentTree", new Class<?>[] { Component.class }, root);

        assertEquals(Integer.valueOf(255), Integer.valueOf(field.getBackground().getAlpha()),
                "recursive text field solid alpha");
        assertFalse(button.isContentAreaFilled(), "recursive button content area hidden");
        assertTrue(list.isOpaque(), "recursive list opaque");
        assertEquals(list.getBackground(), pane.getViewport().getBackground(), "recursive scroll viewport background");
    }

    /**
     * Verifies disabled combo boxes paint with theme tokens instead of the
     * platform look-and-feel's light selected-value background.
     */
    private static void testDisabledComboUsesThemeBackground() {
        Class<?> modeType = type("Theme$Mode");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "DARK"));
        JComboBox<String> combo = new JComboBox<>(new String[] { "Default language" });
        invokeStatic(type("Ui"), "styleCombo", new Class<?>[] { JComboBox.class }, combo);
        combo.setEnabled(false);
        combo.setSize(220, 32);
        combo.doLayout();

        Color sample = new Color(paint(combo, 220, 32).getRGB(170, 16), true);
        Color expected = themeColor("INPUT_DISABLED");
        if (colorDistance(sample, expected) > 6.0) {
            throw new AssertionError("disabled combo background: expected near " + colorText(expected)
                    + ", got " + colorText(sample));
        }
        assertColorDistanceAtLeast(sample, Color.WHITE, 80.0, "disabled combo avoids white background");

        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
    }

    /**
     * Verifies the Game tab's import-line editor keeps enough height for
     * algebraic movetext and variations instead of collapsing into one line.
     */
    private static void testGameLineImportInputKeepsMultilineHeight() {
        JTextArea input = new JTextArea();
        JScrollPane pane = (JScrollPane) invokeStatic(type("window.WindowBoardLayer"),
                "configureGameInputScroll", new Class<?>[] { JTextArea.class }, input);
        assertEquals(Integer.valueOf(5), Integer.valueOf(input.getRows()), "game input reserves five rows");
        assertTrue(input.getLineWrap(), "game input wraps movetext");
        assertTrue(input.getWrapStyleWord(), "game input wraps on words");
        assertTrue(pane.getPreferredSize().height >= 90, "game input scroll preferred height");
        assertTrue(pane.getMinimumSize().height >= 70, "game input scroll minimum height");
    }

    /**
     * Verifies settings toggles reserve enough width for full labels.
     */
    private static void testSettingsToggleRowsAreReadable() {
        JComponent toggle = (JComponent) construct(type("ToggleBox"), new Class<?>[] { String.class },
                "Legal move preview");
        Dimension size = toggle.getPreferredSize();
        assertTrue(size.width >= 300, "settings toggle row is wide enough for labels");
        assertTrue(size.height <= 36, "settings toggle row remains compact");
    }

    /**
     * Verifies the top-level settings menu exposes light and dark modes.
     */
    private static void testSettingsMenuExposesThemeModes() {
        Theme.Mode[] activeMode = { Theme.Mode.LIGHT };
        boolean[] displaySettingsOpened = { false };
        boolean[] engineSettingsOpened = { false };
        SettingsMenu menu = new SettingsMenu(new SettingsMenu.Controller() {
            @Override
            public Theme.Mode themeMode() {
                return activeMode[0];
            }

            @Override
            public void setThemeMode(Theme.Mode mode) {
                activeMode[0] = mode;
            }

            @Override
            public void showDisplaySettings() {
                displaySettingsOpened[0] = true;
            }

            @Override
            public void showEngineSettings() {
                engineSettingsOpened[0] = true;
            }

            @Override
            public void showCommandPalette() {
                // not needed for this regression
            }

            @Override
            public void openLogsDirectory() {
                // not needed for this regression
            }
        });

        JMenuBar bar = menu.component();
        assertEquals(Integer.valueOf(1), Integer.valueOf(bar.getMenuCount()), "settings menu count");
        JMenu settings = bar.getMenu(0);
        assertEquals("Settings", settings.getText(), "settings menu label");
        JMenu appearance = menu(settings, "Appearance");
        JRadioButtonMenuItem light = radioItem(appearance, "Light");
        JRadioButtonMenuItem dark = radioItem(appearance, "Dark");
        assertTrue(light.isSelected(), "light mode starts selected");
        assertTrue(!dark.isSelected(), "dark mode starts unselected");

        dark.doClick();
        assertEquals(Theme.Mode.DARK, activeMode[0], "dark menu item applies dark mode");
        menu.syncMode();
        assertTrue(dark.isSelected(), "dark menu item reflects controller state");

        item(settings, "Board Settings").doClick();
        item(settings, "Engine Settings").doClick();
        assertTrue(displaySettingsOpened[0], "settings menu opens board settings");
        assertTrue(engineSettingsOpened[0], "settings menu opens engine settings");
    }

    /**
     * Verifies the compact layout menu exposes the useful supported workbench
     * layout controls without depending on pointer drag gestures.
     */
    private static void testLayoutMenuExposesUsefulWorkbenchControls() {
        boolean[] statusVisible = { true };
        int[] splitRight = { 0 };
        int[] splitDown = { 0 };
        int[] splitLeft = { 0 };
        int[] splitUp = { 0 };
        int[] restoreTabs = { 0 };
        int[] closeOthers = { 0 };
        LayoutMenu menu = new LayoutMenu(new LayoutMenu.Controller() {
            @Override
            public boolean statusBarVisible() {
                return statusVisible[0];
            }

            @Override
            public void setStatusBarVisible(boolean visible) {
                statusVisible[0] = visible;
            }

            @Override
            public void splitRight() {
                splitRight[0]++;
            }

            @Override
            public void splitDown() {
                splitDown[0]++;
            }

            @Override
            public void splitLeft() {
                splitLeft[0]++;
            }

            @Override
            public void splitUp() {
                splitUp[0]++;
            }

            @Override
            public void reopenAllTabs() {
                restoreTabs[0]++;
            }

            @Override
            public void closeOtherTabs() {
                closeOthers[0]++;
            }

            @Override
            public int openTabCount() {
                return 7;
            }

            @Override
            public int visibleGroupCount() {
                return 2;
            }
        });

        JComponent component = menu.component();
        assertEquals(Integer.valueOf(4), Integer.valueOf(component.getComponentCount()),
                "layout toolbar exposes four chrome buttons");
        assertEquals("Customize Layout", ((JButton) component.getComponent(0)).getToolTipText(),
                "layout toolbar starts with customize button");
        ((JButton) component.getComponent(1)).doClick();
        ((JButton) component.getComponent(2)).doClick();
        ((JButton) component.getComponent(3)).doClick();
        assertEquals(Integer.valueOf(1), Integer.valueOf(splitRight[0]),
                "layout toolbar split-right button works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(splitDown[0]),
                "layout toolbar split-down button works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(restoreTabs[0]),
                "layout toolbar restore button works");

        JPopupMenu popup = (JPopupMenu) invoke(menu, "buildPopup", new Class<?>[0]);
        assertEquals("Status Bar", popupItem(popup, "Status Bar").getText(),
                "layout popup exposes status-bar visibility");
        JCheckBoxMenuItem statusItem = (JCheckBoxMenuItem) popupItem(popup, "Status Bar");
        assertTrue(statusItem.isSelected(), "status-bar row reflects controller state");
        statusItem.doClick();
        assertFalse(statusVisible[0], "status-bar row toggles controller state");

        popupItem(popup, "Split Left").doClick();
        popupItem(popup, "Split Up").doClick();
        popupItem(popup, "Close Other Tabs").doClick();
        assertEquals(Integer.valueOf(1), Integer.valueOf(splitLeft[0]),
                "layout popup split-left item works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(splitUp[0]),
                "layout popup split-up item works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(closeOthers[0]),
                "layout popup close-others item works");
    }

    /**
     * Finds a submenu by label.
     *
     * @param parent parent menu
     * @param text submenu text
     * @return matching submenu
     */
    private static JMenu menu(JMenu parent, String text) {
        for (int index = 0; index < parent.getItemCount(); index++) {
            JMenuItem child = parent.getItem(index);
            if (child instanceof JMenu menu && text.equals(menu.getText())) {
                return menu;
            }
        }
        throw new AssertionError("missing submenu " + text);
    }

    /**
     * Finds a menu item by label.
     *
     * @param parent parent menu
     * @param text item text
     * @return matching item
     */
    private static JMenuItem item(JMenu parent, String text) {
        for (int index = 0; index < parent.getItemCount(); index++) {
            JMenuItem child = parent.getItem(index);
            if (child != null && text.equals(child.getText())) {
                return child;
            }
        }
        throw new AssertionError("missing menu item " + text);
    }

    /**
     * Finds a popup menu item by label.
     *
     * @param popup popup menu
     * @param text item text
     * @return matching item
     */
    private static JMenuItem popupItem(JPopupMenu popup, String text) {
        for (Component child : popup.getComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("missing popup item " + text);
    }

    /**
     * Finds a radio menu item by label.
     *
     * @param parent parent menu
     * @param text item text
     * @return matching radio item
     */
    private static JRadioButtonMenuItem radioItem(JMenu parent, String text) {
        JMenuItem child = item(parent, text);
        if (child instanceof JRadioButtonMenuItem radio) {
            return radio;
        }
        throw new AssertionError("menu item is not a radio item " + text);
    }

    /**
     * Verifies switch toggles animate the thumb/track rather than snapping
     * directly to their final state.
     */
    private static void testToggleSwitchAnimatesStateChanges() {
        JCheckBox toggle = (JCheckBox) construct(type("ToggleBox"),
                new Class<?>[] { String.class, boolean.class }, "Follow leaf", true);
        assertEquals(Double.valueOf(0.0), Double.valueOf((Double) field(toggle, "visualProgress")),
                "toggle starts visually off");

        toggle.setSelected(true);
        Timer timer = (Timer) field(toggle, "animationTimer");
        assertTrue(timer.isRunning(), "toggle starts animation timer when turned on");
        assertEquals(Double.valueOf(1.0), Double.valueOf((Double) field(toggle, "animationTargetProgress")),
                "toggle animates toward on state");

        setField(toggle, "animationStartedAt", Long.valueOf(System.currentTimeMillis() - 1000L));
        invoke(toggle, "tickAnimation", new Class<?>[0]);
        assertFalse(timer.isRunning(), "toggle animation stops at final frame");
        assertTrue((Double) field(toggle, "visualProgress") > 0.99,
                "toggle finishes visually on");

        toggle.setSelected(false);
        assertTrue(timer.isRunning(), "toggle starts animation timer when turned off");
        assertEquals(Double.valueOf(0.0), Double.valueOf((Double) field(toggle, "animationTargetProgress")),
                "toggle animates toward off state");
        timer.stop();
    }

    /**
     * Verifies command-builder optional toggles fill the shared lead column so
     * short flags such as --quiet align with longer flags such as --no-header.
     */
    private static void testCommandFormOptionalTogglesFillLeadColumn() {
        JComponent toggle = (JComponent) construct(type("ToggleBox"),
                new Class<?>[] { String.class, boolean.class }, "--quiet", true);
        JComponent holder = (JComponent) invokeStatic(type("CommandForm"), "fixedLead",
                new Class<?>[] { javax.swing.JComponent.class }, toggle);
        holder.setSize(holder.getPreferredSize());
        holder.doLayout();

        int leadWidth = (Integer) staticField(type("CommandForm"), "LEAD_WIDTH");
        assertEquals(Integer.valueOf(leadWidth), Integer.valueOf(toggle.getWidth()),
                "optional toggle fills command lead column");
    }

    /**
     * Verifies core theme foreground/background pairs meet practical contrast
     * thresholds for extended workbench use.
     */
    private static void testThemeColorContrast() {
        Class<?> modeType = type("Theme$Mode");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
        assertCurrentThemeContrast("light");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "DARK"));
        assertCurrentThemeContrast("dark");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
    }

    /**
     * Verifies the workbench keeps the VS Code Modern neutral chrome stops
     * while using vibrant pastel action-color tokens.
     */
    private static void testThemeUsesVscodeChromeWithPastelAccentTokens() {
        Theme.setMode(Theme.Mode.LIGHT);
        assertColor(new Color(0xF8F8F8), themeColor("BG"), "light VS Code panel background");
        assertColor(Color.WHITE, themeColor("PANEL_SOLID"), "light VS Code editor background");
        assertColor(Color.WHITE, themeColor("ELEVATED_SOLID"), "light VS Code dropdown background");
        assertColor(new Color(0xE5E5E5), themeColor("LINE"), "light VS Code panel border");
        assertColor(new Color(0xCECECE), themeColor("INPUT_BORDER"), "light VS Code input border");
        assertColor(new Color(0xFFFFFF), themeColor("TAB_HOVER"), "light VS Code tab hover");
        assertColor(new Color(0xF8F8F8), themeColor("TAB_IDLE"), "light VS Code inactive tab");
        assertColor(new Color(0x3B3B3B), themeColor("TEXT"), "light VS Code foreground");
        assertColor(new Color(0x616161), themeColor("MUTED"), "light VS Code muted foreground");
        assertColor(new Color(0x8ECBF4), themeColor("ACCENT"), "light vibrant pastel accent");
        assertColor(new Color(0xD2EFFF), themeColor("TOGGLE_ON_BG"), "light vibrant pastel active option fill");

        Theme.setMode(Theme.Mode.DARK);
        assertColor(new Color(0x181818), themeColor("BG"), "dark VS Code panel background");
        assertColor(new Color(0x1F1F1F), themeColor("PANEL_SOLID"), "dark VS Code editor background");
        assertColor(new Color(0x313131), themeColor("ELEVATED_SOLID"), "dark VS Code dropdown background");
        assertColor(new Color(0x2B2B2B), themeColor("LINE"), "dark VS Code panel border");
        assertColor(new Color(0x3C3C3C), themeColor("INPUT_BORDER"), "dark VS Code input border");
        assertColor(new Color(0x1F1F1F), themeColor("TAB_HOVER"), "dark VS Code tab hover");
        assertColor(new Color(0x181818), themeColor("TAB_IDLE"), "dark VS Code inactive tab");
        assertColor(new Color(0xCCCCCC), themeColor("TEXT"), "dark VS Code foreground");
        assertColor(new Color(0x9D9D9D), themeColor("MUTED"), "dark VS Code muted foreground");
        assertColor(new Color(0x79C8FF), themeColor("ACCENT"), "dark vibrant pastel accent");
        assertColor(new Color(121, 200, 255, 130), themeColor("TOGGLE_ON_BG"),
                "dark vibrant pastel active option fill");
        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies NN visual helpers use the semantic NN palette instead of the
     * generic application accent.
     */
    private static void testNetworkPaletteUsesSemanticFocusColor() {
        Theme.setMode(Theme.Mode.LIGHT);
        TensorViz.refreshPalette();
        assertColor(themeColor("NN_FOCUS"), TensorViz.FOCUS, "light NN focus alias");
        assertColor(TensorViz.FOCUS, TensorViz.sequentialRamp(1.0f), "light sequential NN ramp");

        Theme.setMode(Theme.Mode.DARK);
        TensorViz.refreshPalette();
        assertColor(themeColor("NN_FOCUS"), TensorViz.FOCUS, "dark NN focus alias");
        assertColor(TensorViz.FOCUS, TensorViz.sequentialRamp(1.0f), "dark sequential NN ramp");
        Theme.setMode(Theme.Mode.LIGHT);
        TensorViz.refreshPalette();
    }

    /**
     * Verifies the active theme palette has sufficient contrast.
     *
     * @param modeName mode label for assertion messages
     */
    private static void assertCurrentThemeContrast(String modeName) {
        assertThemeContrast("body text on panel", "TEXT", "PANEL_SOLID", 7.0);
        assertThemeContrast("muted text on panel", "MUTED", "PANEL_SOLID", 4.5);
        assertThemeContrast("input text", "TEXT", "INPUT", 7.0);
        assertThemeContrast("terminal text", "TERMINAL_TEXT", "TERMINAL", 7.0);
        assertThemeContrast("primary button text", "PRIMARY_BUTTON_TEXT", "ACCENT", 4.5);
        assertThemeContrast("secondary button text", "SECONDARY_BUTTON_TEXT", "SECONDARY_BUTTON", 7.0);
        assertThemeContrast("tooltip text", "TOOLTIP_TEXT", "TOOLTIP_BG", 7.0);
        assertThemeContrast("success toast text", "STATUS_SUCCESS_TEXT", "STATUS_SUCCESS_BG", 4.5);
        assertThemeContrast("warning toast text", "STATUS_WARNING_TEXT", "STATUS_WARNING_BG", 4.5);
        assertThemeContrast("error toast text", "STATUS_ERROR_TEXT", "STATUS_ERROR_BG", 4.5);
        assertThemeContrast("info toast text", "STATUS_INFO_TEXT", "STATUS_INFO_BG", 4.5);
        assertThemeContrast("disabled button text", "BUTTON_DISABLED_TEXT", "BUTTON_DISABLED_BG", 3.0);
        Color moveHighlight = themeColor("BOARD_HIGHLIGHT");
        assertTrue(moveHighlight.getGreen() > moveHighlight.getRed()
                && moveHighlight.getGreen() > moveHighlight.getBlue(), "move highlight is green");
        assertColorDistanceAtLeast(moveHighlight, themeColor("BOARD_LIGHT"), 95.0,
                "move highlight distinguishes from light board squares");
        assertColorDistanceAtLeast(moveHighlight, themeColor("BOARD_DARK"), 95.0,
                "move highlight distinguishes from dark board squares");
        assertColorDistanceAtLeast(themeColor("BOARD_ARROW"), themeColor("BOARD_LIGHT"), 95.0,
                "best-move arrow distinguishes from light board squares");
        assertColorDistanceAtLeast(themeColor("BOARD_ARROW"), themeColor("BOARD_DARK"), 95.0,
                "best-move arrow distinguishes from dark board squares in " + modeName + " mode");
    }

    /**
     * Verifies a live theme refresh keeps the semantic foreground hierarchy for
     * muted labels, section labels, and status labels in both modes.
     */
    private static void testThemeRefreshPreservesLabelRoles() {
        Class<?> modeType = type("Theme$Mode");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType }, enumValue(modeType, "LIGHT"));

        JLabel muted = (JLabel) invokeStatic(type("Ui"), "label", new Class<?>[] { String.class }, "muted");
        JLabel section = (JLabel) invokeStatic(type("Theme"), "section", new Class<?>[] { String.class }, "section");
        JLabel warning = new JLabel("warning");
        invokeStatic(type("Theme"), "foreground",
                new Class<?>[] { javax.swing.JComponent.class, type("Theme$ForegroundRole") },
                warning, enumValue(type("Theme$ForegroundRole"), "WARNING"));
        JPanel panel = new JPanel();
        panel.add(muted);
        panel.add(section);
        panel.add(warning);

        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType }, enumValue(modeType, "DARK"));
        invokeStatic(type("Theme"), "refreshComponentTree", new Class<?>[] { Component.class }, panel);
        assertEquals(themeColor("MUTED"), muted.getForeground(), "muted label stays muted in dark");
        assertEquals(themeColor("TEXT"), section.getForeground(), "section label stays text in dark");
        assertEquals(themeColor("STATUS_WARNING_TEXT"), warning.getForeground(), "warning label stays warning in dark");

        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType }, enumValue(modeType, "LIGHT"));
        invokeStatic(type("Theme"), "refreshComponentTree", new Class<?>[] { Component.class }, panel);
        assertEquals(themeColor("MUTED"), muted.getForeground(), "muted label stays muted in light");
        assertEquals(themeColor("TEXT"), section.getForeground(), "section label stays text in light");
        assertEquals(themeColor("STATUS_WARNING_TEXT"), warning.getForeground(),
                "warning label stays warning in light");
    }

    /**
     * Verifies fixed Swing line borders are rebuilt with the active theme.
     */
    private static void testThemeRefreshUpdatesLineBorders() {
        Theme.setMode(Theme.Mode.LIGHT);
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.LINE)));

        Theme.setMode(Theme.Mode.DARK);
        Theme.refreshComponentTree(panel);
        assertEquals(themeColor("LINE"), firstBorderColor(panel.getBorder()),
                "compound line border follows dark theme");

        Theme.setMode(Theme.Mode.LIGHT);
        Theme.refreshComponentTree(panel);
        assertEquals(themeColor("LINE"), firstBorderColor(panel.getBorder()),
                "compound line border follows light theme");
    }

    /**
     * Verifies palette refresh restores workbench-specific control delegates.
     */
    private static void testThemeRefreshRestoresCustomControlUis() {
        Theme.setMode(Theme.Mode.LIGHT);
        JPanel panel = new JPanel();
        JTabbedPane tabs = Ui.tabbedPane();
        tabs.addTab("A", new JPanel());
        JComboBox<String> combo = new JComboBox<>(new String[] { "one", "two" });
        Ui.styleCombo(combo);
        JSpinner spinner = new JSpinner();
        Ui.styleSpinner(spinner);
        JScrollPane scroll = Ui.scroll(new JPanel());
        panel.add(tabs);
        panel.add(combo);
        panel.add(spinner);
        panel.add(scroll);

        Theme.setMode(Theme.Mode.DARK);
        javax.swing.SwingUtilities.updateComponentTreeUI(panel);
        Theme.refreshComponentTree(panel);

        assertTrue(tabs.getUI().getClass().getName().contains("FlatTabbedPaneUI"),
                "tabbed pane custom UI restored");
        assertTrue(combo.getUI().getClass().getName().contains("StyledComboBoxUI"),
                "combo custom UI restored");
        assertTrue(spinner.getUI().getClass().getName().contains("StyledSpinnerUI"),
                "spinner custom UI restored");
        assertTrue(scroll.getVerticalScrollBar().getUI().getClass().getName().contains("StyledScrollBarUI"),
                "scrollbar custom UI restored");
        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies the installed Swing defaults use the workbench palette rather
     * than default look-and-feel colors.
     */
    private static void testThemeInstallSetsTooltipColors() {
        invokeStatic(type("Theme"), "install", new Class<?>[0]);
        assertEquals(themeColor("TOOLTIP_BG"), UIManager.getColor("ToolTip.background"), "tooltip background themed");
        assertEquals(themeColor("TOOLTIP_TEXT"), UIManager.getColor("ToolTip.foreground"), "tooltip foreground themed");
        assertEquals(themeColor("TEXT_SELECTION"), UIManager.getColor("TextField.selectionBackground"),
                "text-field selection background themed");
        assertEquals(themeColor("SELECTION_SOLID"), UIManager.getColor("List.selectionBackground"),
                "list selection background themed");
    }

    /**
     * Verifies collapsible information sections hide and restore their content.
     */
    private static void testCollapsibleInfoSectionTogglesContent() {
        JLabel content = new JLabel("details");
        JComponent section = (JComponent) invokeStatic(type("Ui"), "collapsible",
                new Class<?>[] { String.class, javax.swing.JComponent.class, boolean.class },
                "Info", content, true);
        JButton toggle = firstButton(section);
        assertTrue(content.isVisible(), "collapsible content initially visible");
        toggle.doClick();
        assertFalse(content.isVisible(), "collapsible content hidden");
        toggle.doClick();
        assertTrue(content.isVisible(), "collapsible content restored");
    }

    /**
     * Verifies command-selector buttons reserve their bold selected width so
     * neighboring controls do not shift while clicking through command tabs.
     */
    private static void testCommandTabsReserveSelectedTextWidth() {
        JToggleButton tab = new JToggleButton("Generate FENs");
        Theme.commandTab(tab);
        Dimension plain = tab.getPreferredSize();
        tab.setSelected(true);
        Dimension selected = tab.getPreferredSize();
        assertEquals(plain, selected, "command tab preferred size is stable when selected");
    }

    /**
     * Verifies styled tab panes switch immediately without keeping outgoing
     * content snapshots that can ghost over the next tab.
     */
    private static void testTabbedPaneSwitchesWithoutSnapshotOverlay() {
        JTabbedPane tabs = (JTabbedPane) invokeStatic(type("Ui"), "tabbedPane",
                new Class<?>[0]);
        JPanel first = new JPanel();
        first.add(new JLabel("first"));
        JPanel second = new JPanel();
        second.add(new JLabel("second"));
        tabs.addTab("A", first);
        tabs.addTab("B", second);
        first.setSize(220, 120);
        first.setBounds(0, 28, 220, 120);
        tabs.setSelectedIndex(1);

        assertEquals(1, tabs.getSelectedIndex(), "tab selection changed");
        assertEquals(JTabbedPane.class, tabs.getClass(), "tab pane has no snapshot overlay subclass");
    }

    /**
     * Verifies the workbench split shell keeps separate editor-group tab lists,
     * so moving a tab into the other pane does not duplicate every tab in both
     * strips.
     */
    @SuppressWarnings("unchecked")
    private static void testSplitAreaUsesIndependentEditorGroups() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        for (int i = 0; i < 4; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, 2, false);

        List<Integer> primary = (List<Integer>) field(area, "primaryTabs");
        List<Integer> secondary = (List<Integer>) field(area, "secondaryTabs");
        assertFalse(primary.contains(2), "dragged tab moved out of primary group");
        assertTrue(secondary.contains(2), "dragged tab moved into secondary group");
        assertEquals(Integer.valueOf(2), invoke(area, "selectedIndex", new Class<?>[0]),
                "secondary group becomes active after right split");

        invoke(area, "setSecondary", new Class<?>[] { int.class }, 1);
        assertFalse(primary.contains(1), "center drop removes tab from source group");
        assertTrue(secondary.contains(1), "center drop adds tab to target group");
        assertTrue((Boolean) invoke(area, "isVisibleInPane", new Class<?>[] { int.class }, 1),
                "moved tab is visible in target group");

        invoke(area, "setPrimary", new Class<?>[] { int.class }, 1);
        assertTrue(primary.contains(1), "tab can move back to primary group");
        assertFalse(secondary.contains(1), "tab is not duplicated across editor groups");
    }

    /**
     * Verifies corner tab drops can create VS Code-style quadrant editor groups
     * instead of being limited to left/right or top/bottom splits.
     */
    @SuppressWarnings("unchecked")
    private static void testSplitAreaSupportsCornerEditorGroups() {
        Class<?> areaType = type("layout.EditorSplitArea");
        Object area = construct(areaType, new Class<?>[0]);
        for (int i = 0; i < 4; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, 2, false);

        setField(area, "dragZone", staticField(areaType, "DROP_BOTTOM_RIGHT"));
        invoke(area, "finishTabDrag", new Class<?>[] { int.class }, 1);
        List<Integer> secondary = (List<Integer>) field(area, "secondaryTabs");
        List<Integer> quaternary = (List<Integer>) field(area, "quaternaryTabs");
        assertTrue(secondary.contains(2), "existing right group stays in the top-right quadrant");
        assertTrue(quaternary.contains(1), "bottom-right corner creates a bottom-right group");
        assertEquals(Integer.valueOf(1), invoke(area, "selectedIndex", new Class<?>[0]),
                "bottom-right corner drop activates the moved tab");

        setField(area, "dragZone", staticField(areaType, "DROP_TOP_LEFT"));
        invoke(area, "finishTabDrag", new Class<?>[] { int.class }, 3);
        List<Integer> primary = (List<Integer>) field(area, "primaryTabs");
        List<Integer> tertiary = (List<Integer>) field(area, "tertiaryTabs");
        assertTrue(primary.contains(3), "top-left corner isolates the dragged tab");
        assertTrue(tertiary.contains(0), "previous top-left tabs move into the bottom-left group");
        assertFalse(primary.contains(0), "top-left corner split does not duplicate displaced tabs");

        invoke(area, "closeTab", new Class<?>[] { int.class }, 3);
        assertEquals(Integer.valueOf(2), invoke(area, "selectedIndex", new Class<?>[0]),
                "closing active top-left group falls back to the repaired primary group");
        invoke(area, "select", new Class<?>[] { int.class }, 3);
        primary = (List<Integer>) field(area, "primaryTabs");
        assertTrue(primary.contains(3), "closed tab reopens in the active visible group");

        invoke(area, "collapseSplit", new Class<?>[0]);
        primary = (List<Integer>) field(area, "primaryTabs");
        secondary = (List<Integer>) field(area, "secondaryTabs");
        tertiary = (List<Integer>) field(area, "tertiaryTabs");
        quaternary = (List<Integer>) field(area, "quaternaryTabs");
        assertTrue(primary.contains(0), "collapse preserves bottom-left tabs");
        assertTrue(primary.contains(1), "collapse preserves bottom-right tabs");
        assertTrue(primary.contains(2), "collapse preserves top-right tabs");
        assertTrue(primary.contains(3), "collapse preserves reopened top-left tabs");
        assertTrue(secondary.isEmpty(), "collapse clears top-right group");
        assertTrue(tertiary.isEmpty(), "collapse clears bottom-left group");
        assertTrue(quaternary.isEmpty(), "collapse clears bottom-right group");
    }

    /**
     * Verifies a tab dragged onto another editor group's tab strip docks back
     * into that group instead of creating another split zone.
     */
    @SuppressWarnings("unchecked")
    private static void testSplitAreaDocksDraggedTabsBackIntoGroup() {
        Class<?> areaType = type("layout.EditorSplitArea");
        Object area = construct(areaType, new Class<?>[0]);
        for (int i = 0; i < 4; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, 2, false);

        invoke(area, "dockDraggedTab", new Class<?>[] { int.class, int.class, int.class },
                2, staticField(areaType, "PANE_PRIMARY"), 1);
        List<Integer> primary = (List<Integer>) field(area, "primaryTabs");
        List<Integer> secondary = (List<Integer>) field(area, "secondaryTabs");
        assertEquals(Integer.valueOf(1), invoke(area, "visibleGroupCount", new Class<?>[0]),
                "docking the only split tab back collapses to one editor group");
        assertEquals(Integer.valueOf(2), primary.get(1),
                "docked tab uses the requested tab-strip insertion point");
        assertTrue(secondary.isEmpty(), "source editor group is emptied after docking back");
        assertEquals(Integer.valueOf(2), invoke(area, "selectedIndex", new Class<?>[0]),
                "docked tab becomes the active tab in the target group");
    }

    /**
     * Verifies tab management does not depend on pointer dragging alone.
     */
    private static void testSplitAreaExposesFlexibleTabActions() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        for (int i = 0; i < 3; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "select", new Class<?>[] { int.class }, 1);
        invoke(area, "splitSelectedTabRight", new Class<?>[0]);
        assertEquals(Integer.valueOf(2), invoke(area, "visibleGroupCount", new Class<?>[0]),
                "shortcut split creates a second editor group");
        assertTrue((Boolean) invoke(area, "isVisibleInPane", new Class<?>[] { int.class }, 1),
                "split tab remains visible");

        JComponent primaryStrip = (JComponent) field(area, "primaryStrip");
        Component firstTab = primaryStrip.getComponent(0);
        JPopupMenu menu = ((JComponent) firstTab).getComponentPopupMenu();
        assertTrue(menu != null, "tab exposes a context action menu");
        assertEquals("Split Right", ((JMenuItem) menu.getComponent(0)).getText(),
                "tab menu starts with split actions");
        assertEquals("Close Others", ((JMenuItem) menu.getComponent(5)).getText(),
                "tab menu exposes close-others action");

        invoke(area, "closeOtherTabs", new Class<?>[0]);
        assertEquals(Integer.valueOf(1), invoke(area, "openTabCount", new Class<?>[0]),
                "close-others keeps only the active tab");
        invoke(area, "reopenAllTabs", new Class<?>[0]);
        assertEquals(Integer.valueOf(3), invoke(area, "openTabCount", new Class<?>[0]),
                "restore-all reopens hidden tabs");
        invoke(area, "closeSelectedTab", new Class<?>[0]);
        assertEquals(Integer.valueOf(2), invoke(area, "openTabCount", new Class<?>[0]),
                "close active tab hides one tab");
    }

    /**
     * Verifies editor-group split chrome follows VS Code's compact icon/sash
     * model rather than a text button and a visible divider grip.
     */
    private static void testEditorShellUsesVscodeStyleSplitChrome() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "One", new JPanel());
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "Two", new JPanel());
        invoke(area, "install", new Class<?>[0]);
        JToggleButton splitButton = (JToggleButton) field(area, "splitButton");
        assertEquals("", splitButton.getText(), "split action uses icon-only chrome");
        assertEquals(new Dimension(28, 28), splitButton.getPreferredSize(),
                "split action keeps a compact VS Code-style hit target");

        JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        invokeStatic(type("layout.SplitPaneStyler"), "style", new Class<?>[] { JSplitPane.class }, pane);
        assertEquals(Integer.valueOf(4), Integer.valueOf(pane.getDividerSize()),
                "split sash uses VS Code's four-pixel interaction strip");
        assertTrue(pane.isContinuousLayout(), "split sash resizes continuously");
        assertFalse(pane.isOneTouchExpandable(), "split sash has no Swing one-touch affordance");
    }

    /**
     * Verifies the editor shell can close to an empty VS Code-style host and
     * paints a subtle rook silhouette watermark instead of a blank pane.
     */
    @SuppressWarnings("unchecked")
    private static void testEditorShellShowsRookWatermarkWhenEmpty() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "Only", new JPanel());
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "closeTab", new Class<?>[] { int.class }, 0);

        assertTrue(((List<Integer>) field(area, "open")).isEmpty(), "last tab can close");
        assertEquals(Integer.valueOf(-1), invoke(area, "selectedIndex", new Class<?>[0]),
                "empty editor has no selected tab");
        JComponent host = (JComponent) field(area, "primaryHost");
        assertEquals(Integer.valueOf(0), Integer.valueOf(host.getComponentCount()),
                "empty editor host contains no panel");
        assertEmbeddedRookWatermarkSilhouette();

        host.setSize(360, 300);
        BufferedImage image = paint(host, 360, 300);
        Color background = themeColor("PANEL_SOLID");
        int markedPixels = 0;
        for (int y = 70; y < 230; y++) {
            for (int x = 110; x < 250; x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (colorDistance(pixel, background) > 2.0) {
                    markedPixels++;
                }
            }
        }
        assertTrue(markedPixels > 1500, "empty editor paints filled rook watermark silhouette");
    }

    /**
     * Verifies the empty-editor watermark is sourced from the embedded rook SVG
     * silhouette rather than a simplified hand-built shape.
     */
    private static void assertEmbeddedRookWatermarkSilhouette() {
        try {
            Class<?> hostType = Class.forName("application.gui.workbench.layout.EmptyEditorHost");
            java.awt.Shape silhouette = (java.awt.Shape) staticField(hostType, "ROOK_WATERMARK_SILHOUETTE");
            java.awt.geom.Rectangle2D bounds = silhouette.getBounds2D();
            assertTrue(bounds.getWidth() > 110.0 && bounds.getHeight() > 130.0,
                    "empty editor rook watermark uses embedded rook SVG silhouette");
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("missing empty editor host class", ex);
        }
    }

    /**
     * Verifies panels constructed before a theme switch are refreshed when they
     * become visible later.
     */
    private static void testEditorShellRefreshesHiddenPanelTheme() {
        Class<?> modeType = type("Theme$Mode");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
        JTextArea hidden = new JTextArea("late panel");
        invokeStatic(type("Theme"), "area", new Class<?>[] { JTextArea.class }, hidden);
        Color lightBackground = hidden.getBackground();

        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "First", new JPanel());
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "Hidden", hidden);
        invoke(area, "install", new Class<?>[0]);

        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "DARK"));
        invoke(area, "select", new Class<?>[] { int.class }, 1);
        assertFalse(lightBackground.equals(hidden.getBackground()),
                "late-visible panel does not keep stale light background");
        assertEquals(themeColor("TEXT_AREA"), hidden.getBackground(),
                "late-visible panel refreshes to active theme");

        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
    }

    /**
     * Verifies styled buttons ease into hover state instead of switching instantly.
     */
    private static void testButtonHoverTransitionStarts() {
        JButton button = (JButton) invokeStatic(type("Ui"), "button",
                new Class<?>[] { String.class, boolean.class, ActionListener.class },
                "Run", true, (ActionListener) event -> {
                    // no-op test listener
        });
        button.getModel().setRollover(true);
        assertTrue((Boolean) invoke(button, "isFillRunning", new Class<?>[0]),
                "button hover transition starts");
    }

    /**
     * Verifies visual timing defaults stay short enough to feel responsive.
     */
    private static void testWorkbenchTimingDefaultsAreSnappy() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        assertTrue((Integer) staticField(type("Ui"), "BUTTON_TRANSITION_MS") <= 80,
                "button transitions are short");
        assertTrue((Integer) staticField(type("BoardPanel"), "MOVE_ANIMATION_MS") <= 120,
                "board move animation is short");
        assertTrue((Integer) field(board, "snapbackAnimationMs") <= 100,
                "snapback animation is short");
        assertTrue((Integer) field(board, "snapAnimationMs") <= 70,
                "snap animation is short");
        assertTrue((Integer) field(board, "flipAnimationMs") <= 160,
                "flip animation is short");
        assertTrue((Integer) staticField(type("EvalBar"), "ANIMATION_DURATION_MS") <= 260,
                "eval bar transition is smooth but still responsive");
        assertTrue((Integer) staticField(type("Window"), "EVAL_DEBOUNCE_MS") <= 100,
                "eval refresh debounce is short");
    }

    /**
     * Verifies operational defaults stay responsive and avoid unnecessary
     * first-run CPU work.
     */
    private static void testWorkbenchOperationalDefaultsAreSnappy() {
        Class<?> defaults = type("Defaults");
        assertEquals("1s", staticField(defaults, "ANALYSIS_DURATION"),
                "interactive analysis default is short");
        assertEquals(Integer.valueOf(2), staticField(defaults, "ANALYSIS_MULTIPV"),
                "interactive MultiPV default is compact");
        assertTrue((Integer) staticField(defaults, "MCTS_VISITS") <= 300,
                "MCTS default visit budget is lightweight");
        assertEquals(Boolean.FALSE, staticField(defaults, "NETWORK_MCTS_FOLLOW_LEAF"),
                "Network MCTS does not re-infer every leaf by default");
    }

    /**
     * Verifies the Reset button keeps the reset glyph.
     */
    private static void testResetButtonUsesResetIcon() {
        JButton button = (JButton) invokeStatic(type("Ui"), "button",
                new Class<?>[] { String.class, boolean.class, ActionListener.class },
                "Reset", false, (ActionListener) event -> {
                    // no-op test listener
                });
        assertTrue(button.getIcon() != null, "reset icon present");
        assertEquals("RESET", String.valueOf(field(button.getIcon(), "kind")), "reset icon kind");
    }

    /**
     * Verifies disabled buttons use a distinct muted icon.
     */
    private static void testButtonDisabledIconIsMuted() {
        JButton button = (JButton) invokeStatic(type("Ui"), "button",
                new Class<?>[] { String.class, boolean.class, ActionListener.class },
                "Stop", false, (ActionListener) event -> {
                    // no-op test listener
                });
        assertTrue(button.getIcon() != null, "button icon present");
        assertTrue(button.getDisabledIcon() != null, "disabled button icon present");
        assertFalse(button.getIcon() == button.getDisabledIcon(), "disabled icon distinct");
    }

    /**
     * Verifies icon-only buttons keep their resolved glyph after theme refreshes.
     */
    private static void testIconOnlyButtonKeepsIconAfterThemeRefresh() {
        JButton button = (JButton) invokeStatic(type("Ui"), "iconButton",
                new Class<?>[] { String.class, ActionListener.class },
                "Back", (ActionListener) event -> {
                    // no-op test listener
                });
        assertTrue(button.getIcon() != null, "icon-only button starts with icon");
        JPanel panel = new JPanel();
        panel.add(button);
        invokeStatic(type("Theme"), "refreshComponentTree", new Class<?>[] { Component.class }, panel);
        assertTrue(button.getIcon() != null, "icon-only button keeps icon after refresh");
        assertEquals("PREVIOUS", String.valueOf(field(button.getIcon(), "kind")), "icon-only button keeps kind");
    }

    /**
     * Verifies board-navigation labels resolve to distinct transport glyphs.
     */
    private static void testBoardNavigationButtonsUseTransportIcons() {
        assertButtonIconKind("Start", "FIRST");
        assertButtonIconKind("Back", "PREVIOUS");
        assertButtonIconKind("Forward", "NEXT");
        assertButtonIconKind("End", "LAST");
    }

    /**
     * Verifies board transport buttons expose their keyboard shortcuts.
     */
    private static void testBoardNavigationButtonsExposeShortcutTooltips() {
        JButton button = (JButton) invokeStatic(type("Ui"), "iconButton",
                new Class<?>[] { String.class, ActionListener.class },
                "Start", (ActionListener) event -> {
                    // no-op test listener
                });
        invokeStatic(type("window.WindowBoardLayer"), "setTransportShortcut",
                new Class<?>[] { JButton.class, String.class }, button, "Home / Alt+Up");
        assertEquals("Start (Home / Alt+Up)", button.getToolTipText(),
                "board transport tooltip includes shortcut");
    }

    /**
     * Verifies one button label resolves to the expected icon kind.
     *
     * @param label button label
     * @param kind expected icon kind
     */
    private static void assertButtonIconKind(String label, String kind) {
        JButton button = (JButton) invokeStatic(type("Ui"), "iconButton",
                new Class<?>[] { String.class, ActionListener.class },
                label, (ActionListener) event -> {
                    // no-op test listener
                });
        assertEquals(kind, String.valueOf(field(button.getIcon(), "kind")), label + " icon kind");
    }

    /**
     * Verifies command previews quote arguments containing spaces.
     */
    private static void testCommandPreviewQuoting() {
        String command = (String) invokeStatic(type("CommandRunner"), "displayCommand",
                new Class<?>[] { List.class }, List.of("book", "render", "--title", "A B"));
        assertEquals("crtk book render --title \"A B\"", command, "quoted command preview");
    }

    /**
     * Verifies SAN notation uses neutral white figurines, not black-side piece
     * silhouettes.
     */
    private static void testWorkbenchSanRendererUsesNeutralFigurines() {
        String rendered = (String) invokeStatic(type("SanRenderer"), "figurine",
                new Class<?>[] { String.class }, "1. Qxe8+ Nbd6 2. bxa8=Q#");
        assertTrue(rendered.contains("\u2655xe8+"), "queen uses neutral figurine");
        assertTrue(rendered.contains("\u2658bd6"), "knight uses neutral figurine");
        assertTrue(rendered.contains("bxa8=\u2655#"), "promotion uses neutral figurine");
        assertFalse(rendered.contains("\u265B") || rendered.contains("\u265E"), "no black figurine glyphs");
    }

    /**
     * Verifies imported algebraic movetext keeps PGN variation rows.
     */
    private static void testGameModelLoadsPgnVariations() {
        GameModel model = new GameModel();
        Game game = Pgn.parseGame("1. e4 (1. d4 d5) e5 *");
        model.loadGame(game.getStartPosition(), game);

        assertEquals(Integer.valueOf(4), Integer.valueOf(model.getRowCount()), "variation rows visible");
        assertEquals(Integer.valueOf(2), Integer.valueOf(model.lastPly()), "mainline ply count");
        assertEquals(Integer.valueOf(2), Integer.valueOf(model.variationRowCount()), "variation ply count");
        assertTrue(model.pgn().contains("(1. d4 d5)"), "PGN export preserves variation");

        Position afterD4 = new Position(START_FEN);
        afterD4.play(Move.parse("d2d4"));
        model.jumpToRow(1);
        assertEquals("d2d4", Move.toString(model.currentLastMove()), "variation row move");
        assertEquals(afterD4.toString(), model.currentPosition().toString(), "variation row position");
        assertEquals(List.of(Short.valueOf(Move.parse("d2d4"))), model.currentPath(),
                "variation row path");

        Position afterMainline = new Position(START_FEN);
        afterMainline.play(Move.parse("e2e4"));
        afterMainline.play(Move.parse("e7e5"));
        model.jumpToPly(2);
        assertEquals(afterMainline.toString(), model.currentPosition().toString(), "mainline navigation restored");
        assertEquals(List.of(Short.valueOf(Move.parse("e2e4")), Short.valueOf(Move.parse("e7e5"))),
                model.currentPath(), "mainline path");
    }

    /**
     * Verifies the ECO explorer filters the bundled book and loads a selected
     * line through its host callback.
     */
    private static void testEcoExplorerFiltersAndLoadsLines() {
        Position root = new Position(START_FEN);
        List<Short> path = List.of(Short.valueOf(Move.parse("e2e4")), Short.valueOf(Move.parse("e7e5")));
        String[] loaded = { null };
        String[] copied = { null };
        Object explorer = construct(type("EcoExplorerPanel"),
                new Class<?>[] {
                        java.util.function.Supplier.class,
                        java.util.function.Supplier.class,
                        java.util.function.Consumer.class,
                        java.util.function.Consumer.class },
                (java.util.function.Supplier<Position>) root::copy,
                (java.util.function.Supplier<List<Short>>) () -> path,
                (java.util.function.Consumer<String>) value -> loaded[0] = value,
                (java.util.function.Consumer<String>) value -> copied[0] = value);

        assertTrue(((Integer) invoke(explorer, "rowCount", new Class<?>[0])).intValue() > 0,
                "ECO explorer has continuations for e4 e5");
        invoke(explorer, "setFilter", new Class<?>[] { String.class }, "Ruy Lopez");
        assertTrue(((Integer) invoke(explorer, "rowCount", new Class<?>[0])).intValue() > 0,
                "ECO explorer filters Ruy Lopez rows");
        assertTrue((Boolean) invoke(explorer, "selectFirstRow", new Class<?>[0]),
                "ECO explorer selects first row");
        assertTrue((Boolean) invoke(explorer, "loadSelectedLine", new Class<?>[0]),
                "ECO explorer loads selected line");
        assertTrue(loaded[0] != null && loaded[0].contains("Bb5"), "loaded ECO movetext");
        assertTrue((Boolean) invoke(explorer, "copySelectedLine", new Class<?>[0]),
                "ECO explorer copies selected line");
        assertEquals(loaded[0], copied[0], "copied selected ECO movetext");
        JTable table = (JTable) field(explorer, "table");
        Component header = table.getTableHeader().getDefaultRenderer()
                .getTableCellRendererComponent(table, "Line", false, false, -1, 4);
        assertEquals(themeColor("PANEL_SOLID"), header.getBackground(),
                "ECO table header uses workbench background");
        assertEquals(themeColor("MUTED"), header.getForeground(),
                "ECO table header uses workbench foreground");
        assertPaintsOpaqueCorner((JComponent) explorer, 420, 520, "ECO explorer opaque background");
    }

    /**
     * Verifies eval-bar score formatting and white-share mapping.
     */
    private static void testEvalBarMapping() {
        assertEquals("+0.42", formatCentipawns(42), "positive eval format");
        assertEquals("-0.42", formatCentipawns(-42), "negative eval format");
        assertClose(0.5, whiteShareForCentipawns(0), 0.0001, "neutral eval mapping");
        assertTrue(whiteShareForCentipawns(120) > 0.5, "positive eval mapping");
        assertTrue(whiteShareForCentipawns(-120) < 0.5, "negative eval mapping");
    }

    /**
     * Verifies eval-bar score changes use a bounded smooth transition.
     */
    private static void testEvalBarAnimation() {
        JComponent component = (JComponent) construct(type("EvalBar"), new Class<?>[0]);
        assertTrue(component.getPreferredSize().width <= 24, "eval bar is visually thin");
        assertClose(0.0, evalEase(0.0), 0.0001, "eval ease start");
        assertClose(0.5, evalEase(0.5), 0.0001, "eval ease midpoint");
        assertClose(1.0, evalEase(1.0), 0.0001, "eval ease end");
        assertTrue(evalEase(0.75) > evalEase(0.25), "eval ease monotonic");

        Object bar = component;
        invoke(bar, "setCentipawns", new Class<?>[] { int.class }, 250);
        Timer timer = (Timer) field(bar, "timer");
        assertTrue(timer.isRunning(), "eval score animation starts");
        timer.stop();
    }

    /**
     * Verifies the eval-bar rail border remains visible in light and dark mode.
     */
    private static void testEvalBarFrameFollowsTheme() {
        Theme.setMode(Theme.Mode.LIGHT);
        JComponent component = (JComponent) construct(type("EvalBar"), new Class<?>[0]);
        component.setSize(40, 260);
        Color lightFrame = new Color(paint(component, 40, 260).getRGB(20, 0), true);
        assertColor(themeColor("EVAL_FRAME"), lightFrame, "light eval frame paint");

        Theme.setMode(Theme.Mode.DARK);
        Theme.refreshComponentTree(component);
        component.setSize(40, 260);
        Color darkFrame = new Color(paint(component, 40, 260).getRGB(20, 0), true);
        assertColor(themeColor("EVAL_FRAME"), darkFrame, "dark eval frame paint");
        assertColorDistanceAtLeast(darkFrame, themeColor("BG"), 180.0,
                "dark eval frame contrasts with dark workbench background");
        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies the pending engine state does not start a loading animation.
     */
    private static void testEvalBarThinkingIsStatic() {
        Object bar = construct(type("EvalBar"), new Class<?>[0]);
        invoke(bar, "setThinking", new Class<?>[0]);
        Timer timer = (Timer) field(bar, "timer");
        assertFalse(timer.isRunning(), "eval thinking timer stopped");
        assertEquals("", field(bar, "label"), "eval thinking label hidden");
    }

    /**
     * Verifies eval parsing from engine analyze output.
     */
    private static void testEngineEvalParsing() {
        Object cp = parseEngineEval("PV1\n  eval: +42\n");
        assertTrue(cp != null, "centipawn eval parsed");
        assertFalse(engineEvalMate(cp), "centipawn eval is not mate");
        assertEquals(Integer.valueOf(42), Integer.valueOf(engineEvalValue(cp)), "centipawn eval value");

        Object mate = parseEngineEval("PV1\n  eval: #-3\n");
        assertTrue(mate != null, "mate eval parsed");
        assertTrue(engineEvalMate(mate), "mate eval flag");
        assertEquals(Integer.valueOf(-3), Integer.valueOf(engineEvalValue(mate)), "mate eval value");
    }

    /**
     * Verifies streamed live-engine updates produce compact board status text.
     */
    private static void testLiveEngineStatusFormatting() {
        Output cp = new Output("info depth 12 multipv 1 score cp 42 nodes 100 pv e2e4 e7e5");
        String status = (String) invokeStatic(type("Window"), "formatLiveEngineStatus",
                new Class<?>[] { Output.class, short.class }, cp, Move.parse("e2e4"));
        assertEquals("live d12 +42 e2e4", status, "live centipawn status");

        Output mate = new Output("info depth 9 multipv 1 score mate -3 nodes 100 pv g1f3");
        String mateStatus = (String) invokeStatic(type("Window"), "formatLiveEngineStatus",
                new Class<?>[] { Output.class, short.class }, mate, Move.parse("g1f3"));
        assertEquals("live d9 #-3 g1f3", mateStatus, "live mate status");
    }

    /**
     * Verifies live-engine numeric settings reject non-positive values.
     */
    private static void testOptionalPositiveIntegerParsing() {
        JTextField blank = new JTextField(" ");
        assertEquals(null, invokeStatic(type("Window"), "optionalPositiveInteger",
                new Class<?>[] { JTextField.class, String.class }, blank, "--hash"), "blank optional integer");

        JTextField valid = new JTextField("128");
        assertEquals(Integer.valueOf(128), invokeStatic(type("Window"), "optionalPositiveInteger",
                new Class<?>[] { JTextField.class, String.class }, valid, "--hash"), "valid optional integer");

        try {
            invokeStatic(type("Window"), "optionalPositiveInteger",
                    new Class<?>[] { JTextField.class, String.class }, new JTextField("0"), "--hash");
            throw new AssertionError("zero optional integer rejected");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("positive integer"), "invalid optional integer message");
        }
    }

    /**
     * Verifies the analysis graph stores live samples and formats the latest eval.
     */
    private static void testAnalysisGraphStoresSamples() {
        Object graph = construct(type("AnalysisGraph"), new Class<?>[0]);
        invoke(graph, "resetForPosition", new Class<?>[] { String.class }, START_FEN);
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 12 multipv 1 score cp 42 nodes 1000 nps 5000 pv e2e4 e7e5"),
                Move.parse("e2e4"));
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 13 multipv 1 nodes 2200 pv e2e4 e7e5"),
                Move.parse("e2e4"));
        assertEquals(Integer.valueOf(2), invoke(graph, "sampleCount", new Class<?>[0]),
                "analysis graph sparse sample count");
        assertEquals("+0.42", invoke(graph, "latestEvalLabel", new Class<?>[0]),
                "analysis graph eval label");
    }

    /**
     * Verifies graph data can be exported for reports and downstream analysis.
     */
    private static void testAnalysisGraphExportsReportData() {
        Object graph = construct(type("AnalysisGraph"), new Class<?>[0]);
        invoke(graph, "resetForPosition", new Class<?>[] { String.class }, START_FEN);
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 12 multipv 1 score cp 42 nodes 1000 nps 5000 time 90 pv e2e4 e7e5"),
                Move.parse("e2e4"));
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 13 multipv 1 score cp 15 nodes 2200 nps 7000 time 180 pv g1f3"),
                Move.parse("g1f3"));

        String csv = (String) invoke(graph, "csvText", new Class<?>[0]);
        assertTrue(csv.contains("index,eval_cp,eval,depth,nodes,nps,time_ms,best_move"),
                "analysis CSV header");
        assertTrue(csv.contains("g1f3"), "analysis CSV contains best move");

        String report = (String) invoke(graph, "reportText", new Class<?>[0]);
        assertTrue(report.contains("CRTK Workbench Analysis Report"), "analysis report title");
        assertTrue(report.contains("Samples: 2"), "analysis report sample count");
        assertTrue(report.contains("max 13"), "analysis report max depth");
    }

    /**
     * Verifies the graph paints a solid non-blank surface.
     */
    private static void testAnalysisGraphPaintsOpaqueSurface() {
        JComponent graph = (JComponent) construct(type("AnalysisGraph"), new Class<?>[0]);
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 14 multipv 1 score cp -31 nodes 2000 nps 6500 pv g1f3"),
                Move.parse("g1f3"));
        assertPaintsOpaqueCorner(graph, 360, 260, "analysis graph opaque background");
    }

    /**
     * Verifies the chessboard does not show an instructional tooltip over play.
     */
    private static void testBoardHasNoInstructionTooltip() {
        JComponent board = (JComponent) construct(type("BoardPanel"), new Class<?>[0]);
        assertEquals(null, board.getToolTipText(), "board instruction tooltip removed");
    }

    /**
     * Verifies board-local keyboard piece selection is disabled.
     */
    private static void testBoardHasNoKeyboardPieceSelector() {
        JComponent board = (JComponent) construct(type("BoardPanel"), new Class<?>[0]);
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("LEFT")),
                "board left key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("RIGHT")),
                "board right key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("UP")),
                "board up key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("DOWN")),
                "board down key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER")),
                "board enter key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("SPACE")),
                "board space key does not select pieces");
    }

    /**
     * Verifies the setup editor emits legal FEN and applies through its host
     * callback.
     */
    private static void testBoardEditorBuildsAndAppliesFen() {
        String base = "4k3/8/8/8/8/8/8/4K3 w - - 0 1";
        String[] applied = { null };
        Object editor = construct(type("BoardEditorPanel"),
                new Class<?>[] {
                        java.util.function.Supplier.class,
                        java.util.function.Consumer.class,
                        java.util.function.Consumer.class },
                (java.util.function.Supplier<String>) () -> base,
                (java.util.function.Consumer<String>) value -> applied[0] = value,
                (java.util.function.Consumer<String>) value -> {
                    // Clipboard writes are not needed for this regression.
                });

        assertTrue((Boolean) invoke(editor, "loadFen", new Class<?>[] { String.class }, base),
                "board editor loads valid FEN");
        invoke(editor, "setPieceAt", new Class<?>[] { byte.class, byte.class },
                Field.H4, Piece.WHITE_QUEEN);
        assertEquals(Byte.valueOf(Piece.WHITE_QUEEN),
                invoke(editor, "pieceAt", new Class<?>[] { byte.class }, Field.H4),
                "board editor stores placed piece");
        assertEquals("4k3/8/8/8/7Q/8/8/4K3 w - - 0 1",
                invoke(editor, "fen", new Class<?>[0]), "board editor FEN placement");

        invoke(editor, "setWhiteToMove", new Class<?>[] { boolean.class }, false);
        assertTrue((Boolean) invoke(editor, "applyEditedFen", new Class<?>[0]),
                "board editor applies legal FEN");
        assertEquals("4k3/8/8/8/7Q/8/8/4K3 b - - 0 1", applied[0],
                "board editor normalizes applied FEN");
        assertPaintsOpaqueCorner((JComponent) editor, 360, 560, "board editor opaque background");
    }

    /**
     * Verifies window-level arrow routing does not steal arrows from editors or
     * data controls.
     */
    private static void testWindowPositionNavigationRoutingSkipsTextAndDataControls() {
        assertTrue((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JButton("Run")),
                "button focus may route arrows to positions");
        assertFalse((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JTextField()),
                "text fields keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JTable(1, 1)),
                "tables keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JList<>()),
                "lists keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JComboBox<>()),
                "combo boxes keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JSpinner()),
                "spinners keep arrow navigation");
    }

    /**
     * Verifies right-button drag toggles a Lichess-style arrow.
     */
    private static void testBoardRightDragTogglesLichessArrowMarkup() {
        Component board = (Component) construct(type("BoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        Point from = boardPoint(Field.toIndex('e', '2'), true, 640, 640);
        Point to = boardPoint(Field.toIndex('e', '4'), true, 640, 640);

        drawRightArrow(board, from, to, 0);
        List<?> markups = (List<?>) field(board, "boardMarkups");
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "right drag adds one arrow");
        Object markup = markups.get(0);
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(markup, "from"), "arrow origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')), field(markup, "to"), "arrow target");
        assertEquals("green", field(field(markup, "brush"), "name"), "default arrow brush");

        drawRightArrow(board, from, to, 0);
        assertEquals(Integer.valueOf(0), Integer.valueOf(markups.size()), "same right drag deletes arrow");
    }

    /**
     * Verifies right-clicking one square toggles a Lichess-style circle marker.
     */
    private static void testBoardRightClickTogglesLichessCircleMarkup() {
        Component board = (Component) construct(type("BoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        Point square = boardPoint(Field.toIndex('d', '4'), true, 640, 640);

        rightClick(board, square, 0);
        List<?> markups = (List<?>) field(board, "boardMarkups");
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "right click adds one circle");
        Object markup = markups.get(0);
        assertEquals(Byte.valueOf(Field.toIndex('d', '4')), field(markup, "from"), "circle square");
        assertEquals(Byte.valueOf(Field.NO_SQUARE), field(markup, "to"), "circle has no target");

        rightClick(board, square, 0);
        assertEquals(Integer.valueOf(0), Integer.valueOf(markups.size()), "same right click deletes circle");
    }

    /**
     * Verifies drawing the same endpoints with a modifier replaces the color.
     */
    private static void testBoardRightDragReplacesMarkupColorLikeLichess() {
        Component board = (Component) construct(type("BoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        Point from = boardPoint(Field.toIndex('b', '1'), true, 640, 640);
        Point to = boardPoint(Field.toIndex('c', '3'), true, 640, 640);

        drawRightArrow(board, from, to, 0);
        drawRightArrow(board, from, to, MouseEvent.SHIFT_DOWN_MASK);
        List<?> markups = (List<?>) field(board, "boardMarkups");
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "same endpoints keep one markup");
        assertEquals("red", field(field(markups.get(0), "brush"), "name"),
                "shift right drag replaces with red brush");
    }

    /**
     * Verifies dragging a piece emits the legal move through the board callback.
     */
    private static void testBoardDragEmitsLegalMove() {
        Component board = (Component) construct(type("BoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        List<Short> played = new ArrayList<>();
        invoke(board, "setMoveHandler", new Class<?>[] { type("BoardPanel$MoveHandler") },
                moveHandler(played));

        Point from = boardPoint(Field.toIndex('e', '2'), true, 640, 640);
        Point to = boardPoint(Field.toIndex('e', '4'), true, 640, 640);
        long now = System.currentTimeMillis();
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_PRESSED, now, from.x, from.y, 1));
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_DRAGGED, now + 1L, to.x, to.y, 0));
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_RELEASED, now + 2L, to.x, to.y, 1));

        assertEquals(Integer.valueOf(1), Integer.valueOf(played.size()), "dragged move count");
        assertEquals(Move.toString(Move.parse("e2e4")), Move.toString(played.get(0)), "dragged move");
    }

    /**
     * Verifies invalid drag hovers do not paint a red rejection box.
     */
    private static void testBoardDragInvalidHoverDoesNotPaintRedBox() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);

        byte from = Field.toIndex('e', '2');
        byte invalidHover = Field.toIndex('f', '3');
        setField(board, "dragSquare", Byte.valueOf(from));
        setField(board, "draggedPiece", Byte.valueOf(Piece.WHITE_PAWN));
        setField(board, "draggingPiece", Boolean.TRUE);
        setField(board, "dragX", Integer.valueOf(24));
        setField(board, "dragY", Integer.valueOf(24));
        setField(board, "dragHoverSquare", Byte.valueOf(Field.NO_SQUARE));

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        Point hover = boardPoint(invalidHover, true, 640, 640);
        int sampleX = hover.x - cell / 2 + 2;
        int sampleY = hover.y - cell / 2 + 2;
        Color baseline = new Color(paint(component, 640, 640).getRGB(sampleX, sampleY), true);

        setField(board, "dragHoverSquare", Byte.valueOf(invalidHover));
        Color actual = new Color(paint(component, 640, 640).getRGB(sampleX, sampleY), true);

        assertColor(baseline, actual, "invalid drag hover leaves square unboxed");
    }

    /**
     * Verifies drag repaint bounds include an invalid hovered square so a stale
     * hover marker cannot be left behind after pointer movement.
     */
    private static void testBoardDragDirtyBoundsIncludesInvalidHoverSquare() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        byte invalidHover = Field.toIndex('f', '3');
        setField(board, "dragSquare", Byte.valueOf(Field.toIndex('e', '2')));
        setField(board, "draggedPiece", Byte.valueOf(Piece.WHITE_PAWN));

        Rectangle boardBounds = (Rectangle) invoke(board, "boardBounds", new Class<?>[0]);
        Rectangle dirty = (Rectangle) invoke(board, "dragRepaintBounds",
                new Class<?>[] { Rectangle.class, byte.class, byte.class, int.class, int.class, boolean.class },
                boardBounds, Field.NO_SQUARE, invalidHover, 12, 12, Boolean.FALSE);

        assertTrue(dirty != null && dirty.contains(boardPoint(invalidHover, true, 640, 640)),
                "drag dirty bounds include invalid hover square");
    }

    /**
     * Verifies setting a played move starts the Java2D glide animation.
     */
    private static void testBoardMoveAnimationStarts() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Position start = new Position(START_FEN);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);

        short move = Move.parse("e2e4");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        assertTrue((Boolean) field(board, "moveAnimationActive"), "move animation active");
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(board, "animatedMoveFrom"), "animated origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')), field(board, "animatedMoveTo"), "animated target");
        double eased = (Double) invokeStatic(type("BoardPanel"), "easeOutCubic",
                new Class<?>[] { double.class }, 0.5d);
        assertTrue(eased > 0.5d && eased < 1.0d, "move animation ease-out curve");
    }

    /**
     * Verifies stepping backward glides the moved piece back to its origin.
     */
    private static void testBoardReverseMoveAnimationStarts() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Position start = new Position(START_FEN);
        short move = Move.parse("e2e4");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class, boolean.class },
                start, move, Boolean.TRUE);

        assertTrue((Boolean) field(board, "moveAnimationActive"), "reverse move animation active");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')), field(board, "animatedMoveFrom"),
                "reverse animated origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(board, "animatedMoveTo"),
                "reverse animated target");
    }

    /**
     * Verifies capture moves retain the victim piece for fade-out drawing.
     */
    private static void testBoardCaptureAnimationStarts() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Position start = new Position("8/8/8/3p4/4P3/8/8/4K2k w - - 0 1");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);

        short move = Move.parse("e4d5");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        assertTrue((Boolean) field(board, "moveAnimationActive"), "capture animation active");
        assertEquals(Byte.valueOf(Field.toIndex('d', '5')), field(board, "animatedCaptureSquare"),
                "capture fade square");
        assertFalse(((Byte) field(board, "animatedCapturePiece")).byteValue() == Piece.EMPTY,
                "capture fade piece");
    }

    /**
     * Verifies castling starts a secondary rook glide animation.
     */
    private static void testBoardCastlingAnimationStarts() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Position start = new Position("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);

        short move = Move.parse("e1g1");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        assertEquals(Byte.valueOf(Field.toIndex('h', '1')), field(board, "animatedSecondaryMoveFrom"),
                "castle rook origin");
        assertEquals(Byte.valueOf(Field.toIndex('f', '1')), field(board, "animatedSecondaryMoveTo"),
                "castle rook target");
        assertFalse(((Byte) field(board, "animatedSecondaryMovePiece")).byteValue() == Piece.EMPTY,
                "castle rook animated piece");
    }

    /**
     * Verifies the board paints chessboard.js base colors.
     */
    private static void testBoardPaintUsesChessboardJsColors() {
        Component board = (Component) construct(type("BoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position("8/8/8/8/8/8/8/4K2k w - - 0 1"), Move.NO_MOVE);
        Color moveHighlight = themeColor("BOARD_HIGHLIGHT");
        invoke(board, "highlightSquare", new Class<?>[] { byte.class, Color.class },
                Field.toIndex('e', '4'), moveHighlight);

        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            board.paint(graphics);
        } finally {
            graphics.dispose();
        }

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (640 - size) / 2;
        int boardY = (640 - size) / 2;
        assertColor(new Color(64, 64, 64),
                new Color(image.getRGB(boardX - 1, boardY - 1), true),
                "chessboard.js border");
        assertColor(new Color(240, 217, 181),
                new Color(image.getRGB(boardX + cell / 2, boardY + cell / 2), true),
                "chessboard.js light square");
        assertColor(new Color(181, 136, 99),
                new Color(image.getRGB(boardX + cell + cell / 2, boardY + cell / 2), true),
                "chessboard.js dark square");
        int e4x = boardX + 4 * cell;
        int e4y = boardY + 4 * cell;
        Color e4Center = new Color(image.getRGB(e4x + cell / 2, e4y + cell / 2), true);
        assertFalse(e4Center.equals(moveHighlight), "highlight does not fill square center");
        assertColor(moveHighlight, new Color(image.getRGB(e4x + 2, e4y + 2), true),
                "green inset move highlight");
    }

    /**
     * Verifies suggested best-move arrows are legal and do not leave square clutter.
     */
    private static void testBoardSuggestedMoveArrowIsLegalAndClean() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        short artificialLastMove = Move.parse("g1f3");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), artificialLastMove);

        invoke(board, "setSuggestedMove", new Class<?>[] { short.class }, Move.parse("e2e5"));
        assertEquals(Short.valueOf(Move.NO_MOVE), field(board, "suggestedMove"),
                "illegal suggested move rejected");

        invoke(board, "selectSquare", new Class<?>[] { byte.class }, Field.toIndex('e', '2'));
        short best = Move.parse("e2e4");
        invoke(board, "setSuggestedMove", new Class<?>[] { short.class }, best);
        assertEquals(Short.valueOf(best), field(board, "suggestedMove"), "legal suggested move accepted");
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(board, "selectedSquare"),
                "best arrow keeps the user's selected square");

        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (640 - size) / 2;
        int boardY = (640 - size) / 2;
        Color arrowBody = new Color(image.getRGB(boardX + 4 * cell + cell / 2, boardY + 5 * cell + cell / 2), true);
        assertTrue(arrowBody.getBlue() > arrowBody.getRed(), "suggested move arrow is blue");
        Color moveHighlight = themeColor("BOARD_HIGHLIGHT");
        Color e4Edge = new Color(image.getRGB(boardX + 4 * cell + 2, boardY + 4 * cell + 2), true);
        assertFalse(e4Edge.equals(moveHighlight), "best arrow does not add square highlight");
        Color f3Edge = new Color(image.getRGB(boardX + 5 * cell + 2, boardY + 5 * cell + 2), true);
        assertFalse(f3Edge.equals(moveHighlight), "best arrow suppresses stale last-move highlight");
    }

    /**
     * Verifies legal destination previews can be disabled.
     */
    private static void testBoardLegalMovePreviewCanBeHidden() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        invoke(board, "selectSquare", new Class<?>[] { byte.class }, Field.toIndex('e', '2'));
        Color highlight = themeColor("BOARD_HIGHLIGHT");
        Color visibleEdge = boardSquareEdgeColor(component, Field.toIndex('e', '4'));
        assertFalse(visibleEdge.equals(highlight), "legal move preview avoids green square rectangles");
        Color visible = boardSquareCenterColor(component, Field.toIndex('e', '4'));

        invoke(board, "setShowLegalMovePreview", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowLegalMovePreview", new Class<?>[0]),
                "legal move preview disabled");
        Color hidden = boardSquareCenterColor(component, Field.toIndex('e', '4'));
        assertColorDistanceAtLeast(visible, hidden, 10.0, "legal move preview hidden");
    }

    /**
     * Verifies last-move highlights and suggested arrows can be disabled.
     */
    private static void testBoardLastMoveAndBestArrowCanBeHidden() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        Position start = new Position(START_FEN);
        short move = Move.parse("e2e4");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start.copy().play(move), move);
        Color highlight = themeColor("BOARD_HIGHLIGHT");
        assertColor(highlight, boardSquareEdgeColor(component, Field.toIndex('e', '2')),
                "last move highlight visible by default");

        invoke(board, "setShowLastMoveHighlight", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowLastMoveHighlight", new Class<?>[0]),
                "last move highlight disabled");
        assertFalse(boardSquareEdgeColor(component, Field.toIndex('e', '2')).equals(highlight),
                "last move highlight hidden");

        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        Point arrowSample = boardPoint(Field.toIndex('e', '3'), true, 640, 640);
        Color baseline = new Color(paint(component, 640, 640).getRGB(arrowSample.x, arrowSample.y), true);
        invoke(board, "setShowSuggestedMoveArrow", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowSuggestedMoveArrow", new Class<?>[0]),
                "best move arrow disabled");
        invoke(board, "setSuggestedMove", new Class<?>[] { short.class }, Move.parse("e2e4"));
        Color hiddenArrow = new Color(paint(component, 640, 640).getRGB(arrowSample.x, arrowSample.y), true);
        assertColor(baseline, hiddenArrow, "best move arrow hidden");
    }

    /**
     * Verifies notation and animations can be disabled.
     */
    private static void testBoardNotationAndAnimationsCanBeHidden() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        invoke(board, "setShowNotation", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowNotation", new Class<?>[0]), "board notation disabled");

        invoke(board, "setAnimationsEnabled", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isAnimationsEnabled", new Class<?>[0]), "board animations disabled");
        Position start = new Position(START_FEN);
        short move = Move.parse("e2e4");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start.copy().play(move), move);
        assertFalse((Boolean) field(board, "moveAnimationActive"), "move animation suppressed");
    }

    /**
     * Verifies the board paints the checked-king marker.
     */
    private static void testBoardCheckHighlightPaintsCheckedKingMarker() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position("4k3/8/8/8/8/8/8/K3R3 b - - 0 1"), Move.NO_MOVE);
        assertEquals(Byte.valueOf(Field.toIndex('e', '8')),
                invoke(board, "checkedKingSquare", new Class<?>[0]),
                "checked king square");

        Component component = (Component) board;
        component.setSize(640, 640);
        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (640 - size) / 2;
        int boardY = (640 - size) / 2;
        Color marker = new Color(image.getRGB(boardX + 4 * cell + 5, boardY + 5), true);
        assertTrue(marker.getRed() > marker.getGreen() && marker.getRed() > marker.getBlue(),
                "check marker keeps a coral tint");
        assertColorDistanceAtLeast(marker, themeColor("BOARD_LIGHT"), 25.0,
                "check marker distinguishes from pastel light board squares");
    }

    /**
     * Verifies the expensive board layer is cached by board size.
     */
    private static void testBoardTextureCachesRenderedLayer() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Object firstTexture = invoke(board, "boardTexture", new Class<?>[] { int.class }, 576);
        Object secondTexture = invoke(board, "boardTexture", new Class<?>[] { int.class }, 576);
        Object largerTexture = invoke(board, "boardTexture", new Class<?>[] { int.class }, 640);

        assertTrue(firstTexture == secondTexture, "same board texture cache reuse");
        assertFalse(firstTexture == largerTexture, "different board texture cache refresh");
    }

    /**
     * Verifies scaled SVG pieces are cached per board cell size.
     */
    private static void testBoardPieceImageCacheReusesScaledSvg() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Object first = invoke(board, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 72);
        Object second = invoke(board, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 72);
        Object larger = invoke(board, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 80);

        assertTrue(first == second, "same cell piece cache reuse");
        assertFalse(first == larger, "different cell piece cache refresh");
    }

    /**
     * Verifies network mini-board piece rendering uses absolute board
     * coordinates with White's home rank at the bottom.
     */
    private static void testTensorMiniBoardPiecesRenderWhiteBottom() {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            invokeStatic(type("TensorViz"), "drawPositionPieces",
                    new Class<?>[] { Graphics2D.class, Rectangle.class, String.class },
                    graphics, new Rectangle(0, 0, 80, 80),
                    "7k/8/8/8/8/8/8/K7 w - - 0 1");
        } finally {
            graphics.dispose();
        }
        int topLeftAlpha = alphaSum(image, 0, 0, 10, 10);
        int bottomLeftAlpha = alphaSum(image, 0, 70, 10, 10);
        assertTrue(bottomLeftAlpha > topLeftAlpha + 1000,
                "mini-board white king renders on a1 at the bottom");
    }

    /**
     * Creates a reflected board move handler.
     *
     * @param played captured moves
     * @return move handler proxy
     */
    private static Object moveHandler(List<Short> played) {
        Class<?> handlerType = type("BoardPanel$MoveHandler");
        return Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[] { handlerType },
                (proxy, method, args) -> {
                    if ("play".equals(method.getName())) {
                        played.add((Short) args[0]);
                    }
                    return null;
                });
    }

    /**
     * Finds the first button contained in a component tree.
     *
     * @param component root component
     * @return first button
     */
    private static JButton firstButton(Component component) {
        if (component instanceof JButton button) {
            return button;
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return firstButton(child);
                } catch (AssertionError ex) {
                    // Continue searching siblings.
                }
            }
        }
        throw new AssertionError("missing button in " + component.getClass().getName());
    }

    /**
     * Paints a component into an ARGB image.
     *
     * @param component component to paint
     * @param width width
     * @param height height
     * @return painted image
     */
    private static BufferedImage paint(Component component, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Sums alpha values over a rectangular image region.
     * @param image image value
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param height height in pixels
     * @return alpha sum result
     */
    private static int alphaSum(BufferedImage image, int x, int y, int width, int height) {
        int sum = 0;
        for (int yy = y; yy < y + height; yy++) {
            for (int xx = x; xx < x + width; xx++) {
                sum += new Color(image.getRGB(xx, yy), true).getAlpha();
            }
        }
        return sum;
    }

    /**
     * Paints a component and verifies the top-left pixel was cleared opaquely.
     *
     * @param component component to paint
     * @param width width
     * @param height height
     * @param label assertion label
     */
    private static void assertPaintsOpaqueCorner(JComponent component, int width, int height, String label) {
        component.setSize(width, height);
        BufferedImage image = paint(component, width, height);
        int alpha = new Color(image.getRGB(0, 0), true).getAlpha();
        assertEquals(Integer.valueOf(255), Integer.valueOf(alpha), label);
    }

    /**
     * Creates a mouse event for board tests.
     *
     * @param board board component
     * @param id event id
     * @param when event timestamp
     * @param x x coordinate
     * @param y y coordinate
     * @param clicks click count
     * @return mouse event
     */
    private static MouseEvent mouse(Component board, int id, long when, int x, int y, int clicks) {
        int modifiers = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        return new MouseEvent(board, id, when, modifiers, x, y, clicks, false, button);
    }

    /**
     * Draws one right-button board arrow with optional modifier bits.
     *
     * @param board board component
     * @param from origin point
     * @param to target point
     * @param extraModifiers modifier bits
     */
    private static void drawRightArrow(Component board, Point from, Point to, int extraModifiers) {
        long now = System.currentTimeMillis();
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_PRESSED, now, from.x, from.y, 1, extraModifiers));
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_DRAGGED, now + 1L, to.x, to.y, 0, extraModifiers));
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_RELEASED, now + 2L, to.x, to.y, 1, extraModifiers));
    }

    /**
     * Sends one right-click to a board square.
     *
     * @param board board component
     * @param point click point
     * @param extraModifiers modifier bits
     */
    private static void rightClick(Component board, Point point, int extraModifiers) {
        long now = System.currentTimeMillis();
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_PRESSED, now, point.x, point.y, 1, extraModifiers));
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_RELEASED, now + 1L, point.x, point.y, 1,
                extraModifiers));
    }

    /**
     * Creates a right-button mouse event.
     *
     * @param board board component
     * @param id event id
     * @param when timestamp
     * @param x x coordinate
     * @param y y coordinate
     * @param clicks click count
     * @param extraModifiers modifier bits
     * @return mouse event
     */
    private static MouseEvent rightMouse(
            Component board, int id, long when, int x, int y, int clicks, int extraModifiers) {
        int modifiers = extraModifiers | (id == MouseEvent.MOUSE_RELEASED ? 0 : MouseEvent.BUTTON3_DOWN_MASK);
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON3;
        return new MouseEvent(board, id, when, modifiers, x, y, clicks, false, button);
    }

    /**
     * Returns the board-center point for one square under the tested board sizing.
     *
     * @param square square index
     * @param whiteDown true when White is down
     * @param width component width
     * @param height component height
     * @return square center point
     */
    private static Point boardPoint(byte square, boolean whiteDown, int width, int height) {
        int size = Math.min(width - 64, height - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (width - size) / 2;
        int boardY = (height - size) / 2;
        int file = Field.getX(square);
        int rankRow = square / 8;
        int col = whiteDown ? file : 7 - file;
        int row = whiteDown ? rankRow : 7 - rankRow;
        return new Point(boardX + col * cell + cell / 2, boardY + row * cell + cell / 2);
    }

    /**
     * Samples the top-left inset highlight pixel for one board square.
     *
     * @param board board component
     * @param square square index
     * @return sampled color
     */
    private static Color boardSquareEdgeColor(Component board, byte square) {
        int width = board.getWidth();
        int height = board.getHeight();
        int size = Math.min(width - 64, height - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        Point center = boardPoint(square, true, width, height);
        return new Color(paint(board, width, height).getRGB(center.x - cell / 2 + 2, center.y - cell / 2 + 2), true);
    }

    /**
     * Samples the center pixel for one board square.
     *
     * @param board board component
     * @param square square index
     * @return sampled color
     */
    private static Color boardSquareCenterColor(Component board, byte square) {
        Point center = boardPoint(square, true, board.getWidth(), board.getHeight());
        return new Color(paint(board, board.getWidth(), board.getHeight()).getRGB(center.x, center.y), true);
    }

    /**
     * Creates a command option model for a named template.
     *
     * @param name template name
     * @return populated option table model
     */
    private static Object optionsFor(String name) {
        Object template = template(name);
        Object options = construct(type("OptionTableModel"), new Class<?>[0]);
        invoke(options, "setOptions",
                new Class<?>[] { List.class, type("CommandTemplates$TemplateContext") },
                templateOptions(template), templateContext(START_FEN, "2s", "4", "3", "1"));
        return options;
    }

    /**
     * Finds a command template by display name.
     *
     * @param name template name
     * @return command template
     */
    private static Object template(String name) {
        DefaultComboBoxModel<?> model = (DefaultComboBoxModel<?>) invokeStatic(type("CommandTemplates"),
                "commandModel", new Class<?>[0]);
        for (int i = 0; i < model.getSize(); i++) {
            Object template = model.getElementAt(i);
            if (name.equals(invoke(template, "name", new Class<?>[0]))) {
                return template;
            }
        }
        throw new AssertionError("missing command template: " + name);
    }

    /**
     * Finds a batch task by display name.
     *
     * @param name task name
     * @return batch task
     */
    private static Object batchTask(String name) {
        DefaultComboBoxModel<?> model = (DefaultComboBoxModel<?>) invokeStatic(type("CommandTemplates"),
                "batchModel", new Class<?>[0]);
        for (int i = 0; i < model.getSize(); i++) {
            Object task = model.getElementAt(i);
            if (name.equals(invoke(task, "name", new Class<?>[0]))) {
                return task;
            }
        }
        throw new AssertionError("missing batch task: " + name);
    }

    /**
     * Finds a row by flag label.
     *
     * @param model option model
     * @param flag flag label
     * @return row index
     */
    private static int rowForFlag(Object model, String flag) {
        int rowCount = (Integer) invoke(model, "getRowCount", new Class<?>[0]);
        for (int row = 0; row < rowCount; row++) {
            if (flag.equals(invoke(model, "getValueAt", new Class<?>[] { int.class, int.class },
                    row, COL_FLAG))) {
                return row;
            }
        }
        throw new AssertionError("missing flag row: " + flag);
    }

    /**
     * Returns whether a row exists for a flag label.
     *
     * @param model option model
     * @param flag flag label
     * @return true when present
     */
    private static boolean hasRowForFlag(Object model, String flag) {
        try {
            rowForFlag(model, flag);
            return true;
        } catch (AssertionError ex) {
            return false;
        }
    }

    /**
     * Returns the current value for a command option flag.
     *
     * @param model option model
     * @param flag flag label
     * @return value
     */
    private static String optionValue(Object model, String flag) {
        return String.valueOf(invoke(model, "getValueAt",
                new Class<?>[] { int.class, int.class }, rowForFlag(model, flag), COL_VALUE));
    }

    /**
     * Returns whether an argument list contains a flag.
     *
     * @param args argument list
     * @param flag flag
     * @return true when present
     */
    private static boolean hasFlag(List<String> args, String flag) {
        return args.contains(flag);
    }

    /**
     * Returns the token immediately after a flag in an argument list.
     *
     * @param args argument list
     * @param flag flag
     * @return following value, or empty string
     */
    private static String valueAfterFlag(List<String> args, String flag) {
        int index = args.indexOf(flag);
        if (index < 0 || index + 1 >= args.size()) {
            return "";
        }
        return args.get(index + 1);
    }

    /**
     * Returns command args enabled in an option model.
     *
     * @param options option model
     * @return enabled arguments
     */
    @SuppressWarnings("unchecked")
    private static List<String> enabledArgs(Object options) {
        return (List<String>) invoke(options, "enabledArgs", new Class<?>[0]);
    }

    /**
     * Reads a template's option metadata.
     *
     * @param template command template
     * @return option metadata
     */
    @SuppressWarnings("unchecked")
    private static List<Object> templateOptions(Object template) {
        return (List<Object>) invoke(template, "options", new Class<?>[0]);
    }

    /**
     * Creates a workbench template context.
     *
     * @param fen FEN
     * @param duration duration
     * @param depth depth
     * @param multipv MultiPV
     * @param threads threads
     * @return template context
     */
    private static Object templateContext(String fen, String duration, String depth, String multipv, String threads) {
        return templateContext(fen, duration, depth, multipv, threads, "config/default.engine.toml", "", "");
    }

    /**
     * Creates a workbench template context.
     *
     * @param fen FEN
     * @param duration duration
     * @param depth depth
     * @param multipv MultiPV
     * @param threads threads
     * @param protocolPath engine protocol path
     * @param nodes node budget
     * @param hash hash MB
     * @return template context
     */
    private static Object templateContext(String fen, String duration, String depth, String multipv, String threads,
            String protocolPath, String nodes, String hash) {
        return construct(type("CommandTemplates$TemplateContext"),
                new Class<?>[] { String.class, String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class },
                fen, duration, depth, multipv, threads, protocolPath, nodes, hash);
    }

    /**
     * Runs the workbench option-filter matcher.
     *
     * @param query query text
     * @param values searchable values
     * @return true when the query matches
     */
    private static boolean optionFilterMatches(String query, String... values) {
        return (Boolean) invokeStatic(type("Window"), "optionFilterMatches",
                new Class<?>[] { String.class, String[].class }, query, values);
    }

    /**
     * Runs the workbench engine-output parser.
     *
     * @param output command output
     * @return parsed eval record
     */
    private static Object parseEngineEval(String output) {
        return invokeStatic(type("Window"), "parseEngineEval", new Class<?>[] { String.class }, output);
    }

    /**
     * Reads the mate flag from a parsed eval.
     *
     * @param eval parsed eval
     * @return mate flag
     */
    private static boolean engineEvalMate(Object eval) {
        return (Boolean) invoke(eval, "mate", new Class<?>[0]);
    }

    /**
     * Reads the score from a parsed eval.
     *
     * @param eval parsed eval
     * @return score
     */
    private static int engineEvalValue(Object eval) {
        return (Integer) invoke(eval, "value", new Class<?>[0]);
    }

    /**
     * Formats a centipawn value through the workbench eval bar.
     *
     * @param centipawns centipawns
     * @return formatted score
     */
    private static String formatCentipawns(int centipawns) {
        return (String) invokeStatic(type("EvalBar"), "formatCentipawns",
                new Class<?>[] { int.class }, centipawns);
    }

    /**
     * Maps centipawns to white's bar share through the workbench eval bar.
     *
     * @param centipawns centipawns
     * @return white share
     */
    private static double whiteShareForCentipawns(int centipawns) {
        return (Double) invokeStatic(type("EvalBar"), "whiteShareForCentipawns",
                new Class<?>[] { int.class }, centipawns);
    }

    /**
     * Eases animation progress through the workbench eval bar.
     *
     * @param progress linear progress
     * @return eased progress
     */
    private static double evalEase(double progress) {
        return (Double) invokeStatic(type("EvalBar"), "easeInOutCubic",
                new Class<?>[] { double.class }, progress);
    }

    /**
     * Reads one color token from the workbench theme.
     *
     * @param name field name
     * @return color token
     */
    private static Color themeColor(String name) {
        return (Color) staticField(type("Theme"), name);
    }

    /**
     * Finds the first line color in a Swing border tree.
     *
     * @param border border
     * @return first line color, or null when no line color exists
     */
    private static Color firstBorderColor(Border border) {
        if (border instanceof LineBorder line) {
            return line.getLineColor();
        }
        if (border instanceof MatteBorder matte) {
            return matte.getMatteColor();
        }
        if (border instanceof CompoundBorder compound) {
            Color outside = firstBorderColor(compound.getOutsideBorder());
            return outside == null ? firstBorderColor(compound.getInsideBorder()) : outside;
        }
        return null;
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
            Path dir = Files.createTempDirectory("crtk-workbench-log-");
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
     * Verifies run logs never overwrite an existing file at the deterministic
     * first-choice path.
     */
    private static void testRunLogAvoidsClobberingExistingFile() {
        try {
            Path dir = Files.createTempDirectory("crtk-workbench-log-clobber-");
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
            Path dir = Files.createTempDirectory("crtk-workbench-manifest-");
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
            Path dir = Files.createTempDirectory("crtk-workbench-manifest-clobber-");
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
        archCombo.setSelectedItem("NNUE");
        JComponent viewMode = (JComponent) field(panel, "viewMode");
        assertTrue(viewMode.getPreferredSize().width < 340,
                "view selector exposes only the simple modes");
        boolean[] enabled = (boolean[]) field(viewMode, "enabled");
        assertTrue(enabled[2], "NNUE all-neurons segment enabled");
        assertTrue(enabled[3], "NNUE atlas segment enabled");
        archCombo.setSelectedItem("LC0 CNN");
        enabled = (boolean[]) field(viewMode, "enabled");
        assertTrue(enabled[2], "CNN all-neurons segment enabled");
        assertTrue(enabled[3], "CNN atlas segment enabled");
        archCombo.setSelectedItem("LC0 BT4");
        enabled = (boolean[]) field(viewMode, "enabled");
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

    /**
     * Loads a workbench implementation class.
     *
     * @param name simple or nested class name
     * @return class
     */
    private static Class<?> type(String name) {
        ClassNotFoundException first = null;
        try {
            return Class.forName(WORKBENCH_PACKAGE + name);
        } catch (ClassNotFoundException ex) {
            first = ex;
        }
        for (String subpackage : WORKBENCH_SUBPACKAGES) {
            try {
                return Class.forName(WORKBENCH_PACKAGE + subpackage + name);
            } catch (ClassNotFoundException ignored) {
                // Try the next feature package.
            }
        }
        throw new AssertionError("missing workbench type " + name, first);
    }

    /**
     * Constructs a package-private workbench type.
     *
     * @param type type
     * @param parameterTypes constructor parameter types
     * @param args constructor arguments
     * @return instance
     */
    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... args) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (InvocationTargetException ex) {
            rethrowCause(ex);
            throw new AssertionError("unreachable");
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not construct " + type.getName(), ex);
        }
    }

    /**
     * Invokes a package-private static method.
     *
     * @param owner declaring type
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    private static Object invokeStatic(Class<?> owner, String name, Class<?>[] parameterTypes, Object... args) {
        return invokeMethod(null, owner, name, parameterTypes, args);
    }

    /**
     * Invokes a package-private instance method.
     *
     * @param target target instance
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        return invokeMethod(target, target.getClass(), name, parameterTypes, args);
    }

    /**
     * Invokes a package-private instance method declared on a superclass.
     *
     * @param owner declaring type
     * @param target target instance
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    private static Object invokeOn(
            Class<?> owner,
            Object target,
            String name,
            Class<?>[] parameterTypes,
            Object... args) {
        return invokeMethod(target, owner, name, parameterTypes, args);
    }

    /**
     * Looks up a package-private enum constant.
     *
     * @param type enum type
     * @param name constant name
     * @return enum value
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    /**
     * Reads a private field from a package-private workbench object.
     *
     * @param target target instance
     * @param name field name
     * @return field value
     */
    private static Object field(Object target, String name) {
        try {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    java.lang.reflect.Field reflectedField = type.getDeclaredField(name);
                    reflectedField.setAccessible(true);
                    return reflectedField.get(target);
                } catch (NoSuchFieldException ex) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not read " + target.getClass().getName() + "." + name, ex);
        }
    }

    /**
     * Writes a private field on a package-private workbench object.
     *
     * @param target target instance
     * @param name field name
     * @param value replacement value
     */
    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field reflectedField = target.getClass().getDeclaredField(name);
            reflectedField.setAccessible(true);
            reflectedField.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not write " + target.getClass().getName() + "." + name, ex);
        }
    }

    /**
     * Reads a static field from a package-private workbench class.
     *
     * @param owner declaring type
     * @param name field name
     * @return field value
     */
    private static Object staticField(Class<?> owner, String name) {
        try {
            java.lang.reflect.Field reflectedField = owner.getDeclaredField(name);
            reflectedField.setAccessible(true);
            return reflectedField.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not read " + owner.getName() + "." + name, ex);
        }
    }

    /**
     * Invokes a reflected method.
     *
     * @param target target instance or null for static methods
     * @param owner declaring type
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    private static Object invokeMethod(
            Object target,
            Class<?> owner,
            String name,
            Class<?>[] parameterTypes,
            Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            rethrowCause(ex);
            throw new AssertionError("unreachable");
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not invoke " + owner.getName() + "." + name, ex);
        }
    }

    /**
     * Preserves the original failure thrown by a reflected method.
     *
     * @param ex invocation wrapper
     */
    private static void rethrowCause(InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new AssertionError(cause);
    }

    /**
     * Verifies a condition is true.
     *
     * @param condition condition
     * @param label assertion label
     */
    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + ": expected true");
        }
    }

    /**
     * Verifies a condition is false.
     *
     * @param condition condition
     * @param label assertion label
     */
    private static void assertFalse(boolean condition, String label) {
        if (condition) {
            throw new AssertionError(label + ": expected false");
        }
    }

    /**
     * Verifies floating-point closeness.
     *
     * @param expected expected value
     * @param actual actual value
     * @param tolerance accepted absolute tolerance
     * @param label assertion label
     */
    private static void assertClose(double expected, double actual, double tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies two float arrays are bit-identical.
     *
     * @param expected expected values
     * @param actual actual values
     * @param label assertion label
     */
    private static void assertFloatArrayExact(float[] expected, float[] actual, String label) {
        if (expected == null || actual == null || expected.length != actual.length) {
            throw new AssertionError(label + ": array length mismatch");
        }
        for (int i = 0; i < expected.length; i++) {
            if (Float.floatToIntBits(expected[i]) != Float.floatToIntBits(actual[i])) {
                throw new AssertionError(label + ": mismatch at " + i
                        + ", expected " + expected[i] + ", got " + actual[i]);
            }
        }
    }

    /**
     * Verifies a theme foreground/background pair has sufficient contrast.
     *
     * @param label assertion label
     * @param foregroundName foreground token name
     * @param backgroundName background token name
     * @param minimumRatio minimum accepted contrast ratio
     */
    private static void assertThemeContrast(String label, String foregroundName, String backgroundName,
            double minimumRatio) {
        Color foreground = themeColor(foregroundName);
        Color background = themeColor(backgroundName);
        double ratio = contrastRatio(foreground, background);
        if (ratio < minimumRatio) {
            throw new AssertionError(label + ": contrast " + ratio + " below " + minimumRatio
                    + " for " + foregroundName + " on " + backgroundName);
        }
    }

    /**
     * Escapes the subset needed for string-containing JSON assertions.
     *
     * @param value raw value
     * @return escaped value
     */
    private static String jsonEsc(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Calculates WCAG contrast ratio for two opaque colors.
     *
     * @param first first color
     * @param second second color
     * @return contrast ratio
     */
    private static double contrastRatio(Color first, Color second) {
        double firstLum = relativeLuminance(first);
        double secondLum = relativeLuminance(second);
        double light = Math.max(firstLum, secondLum);
        double dark = Math.min(firstLum, secondLum);
        return (light + 0.05) / (dark + 0.05);
    }

    /**
     * Calculates relative luminance for an sRGB color.
     *
     * @param color color
     * @return relative luminance
     */
    private static double relativeLuminance(Color color) {
        return 0.2126 * linearChannel(color.getRed())
                + 0.7152 * linearChannel(color.getGreen())
                + 0.0722 * linearChannel(color.getBlue());
    }

    /**
     * Converts one sRGB channel to linear light.
     *
     * @param channel 0..255 channel value
     * @return linearized channel
     */
    private static double linearChannel(int channel) {
        double normalized = channel / 255.0;
        return normalized <= 0.03928
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    /**
     * Verifies two colors are visually separated enough for a non-text mark.
     *
     * @param first first color
     * @param second second color
     * @param minimum minimum Euclidean RGB distance
     * @param label assertion label
     */
    private static void assertColorDistanceAtLeast(Color first, Color second, double minimum, String label) {
        double distance = colorDistance(first, second);
        if (distance < minimum) {
            throw new AssertionError(label + ": distance " + distance + " below " + minimum);
        }
    }

    /**
     * Calculates Euclidean RGB distance.
     *
     * @param first first color
     * @param second second color
     * @return RGB distance
     */
    private static double colorDistance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }

    /**
     * Verifies exact RGB color equality.
     *
     * @param expected expected color
     * @param actual actual color
     * @param label assertion label
     */
    private static void assertColor(Color expected, Color actual, String label) {
        if (expected.getRGB() != actual.getRGB()) {
            throw new AssertionError(label + ": expected " + colorText(expected) + ", got " + colorText(actual));
        }
    }

    /**
     * Returns a compact RGB color label.
     *
     * @param color color
     * @return RGB label
     */
    private static String colorText(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Verifies object equality.
     *
     * @param expected expected value
     * @param actual actual value
     * @param label assertion label
     */
    private static void assertEquals(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
