package testing;

import static testing.WorkbenchTestSupport.*;

import java.awt.Component;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JTextField;

import application.cli.CliCommand;
import application.cli.CliRegistry;
import application.gui.workbench.engine.EngineGauntletPanel;

import utility.CommandLine;

/**
 * Command, batch, and publishing regression checks.
 */
final class WorkbenchCommandRegression {

    /**
     * Prevents instantiation.
     */
    private WorkbenchCommandRegression() {
        // utility
    }

    /**
     * Runs this focused regression group.
     */
    static void run() {
        testFirstFenLineSkipsNonFenRows();
        testBatchFenValidationReportsLineErrors();
        testCommandOptionConflictsDisableStaleRows();
        testEvaluatorSelectorsUseExplicitDefaults();
        testCommandFormatSelectorsUseDirectChoices();
        testMateTemplateUsesCliShortcut();
        testEngineGauntletCommandBuilder();
        testCommandFormMovesHelperCopyToTooltips();
        testDynamicOptionRefresh();
        testDynamicOptionRefreshSkipsUnchangedValues();
        testCommandFormRefreshesUneditedDynamicDepth();
        testCommandFormPreservesManualDynamicDepth();
        testEngineTemplateContextFeedsExternalConfigOptions();
        testEngineBatchTasksUseExternalConfigOptions();
        testPositionListEditorStartsEmpty();
        testPositionListEditorRejectsEmptyCliScriptRows();
        testCommandTemplatesHaveCompactTabLabels();
        testCommandControllerCoversCliRegistry();
        testBatchRunnerOffersCliScriptForAllCliCommands();
        testRunArtifactsDesktopOpenHandlesRuntimeFailures();
        testCommandLineTokenizerPreservesQuotedArguments();
        testCommandPreviewQuoting();
        testCommandPreviewHeightUsesReadableRange();
        testPublishingPreviewFenCompaction();
        testPublishingVisualPreviewPages();
        testPublishingVisualPreviewPaintsTaskLayouts();
    }

    /**
     * Verifies FEN extraction ignores non-FEN notes before the first position.
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
        assertFalse(hasFlag(enabledArgs(eval), "--otis"), "eval auto emits no otis shortcut");
        assertFalse(hasFlag(enabledArgs(eval), "--classical"), "eval auto emits no classical shortcut");

        invoke(eval, "setValueAt", new Class<?>[] { Object.class, int.class, int.class },
                Boolean.TRUE, rowForFlag(eval, "--classical"), COL_USE);
        List<String> evalClassicalArgs = enabledArgs(eval);
        assertTrue(hasFlag(evalClassicalArgs, "--classical"), "eval classical shortcut emits flag");
        assertFalse(hasFlag(evalClassicalArgs, "--lc0"), "eval classical disables lc0 shortcut");
        assertFalse(hasFlag(evalClassicalArgs, "--otis"), "eval classical disables otis shortcut");

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
        assertFalse(hasFlag(builtinLc0Args, "--otis"), "built-in lc0 disables otis shortcut");
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
     * Verifies the mate command has a first-class GUI builder wired to the
     * top-level CLI shortcut.
     */
    private static void testMateTemplateUsesCliShortcut() {
        Object template = template("Mate");
        List<String> baseArgs = stringList(invoke(template, "baseArgs", new Class<?>[0]));
        assertEquals(List.of("mate"), baseArgs, "mate template uses top-level shortcut");

        Object options = optionsFor("Mate");
        assertEquals("4", optionValue(options, "--mate"), "mate template default distance");
        assertEquals(START_FEN, valueAfterFlag(enabledArgs(options), "--fen"),
                "mate template default FEN source");
        assertEquals("summary", valueAfterFlag(enabledArgs(options), "--format"),
                "mate template default format");
        assertTrue(hasRowForFlag(options, "Both"), "mate template exposes both format");

        invoke(options, "setValueAt", new Class<?>[] { Object.class, int.class, int.class },
                Boolean.TRUE, rowForFlag(options, "Both"), COL_USE);
        assertEquals("both", valueAfterFlag(enabledArgs(options), "--format"),
                "mate template both format");
    }

    /**
     * Verifies the Engine Gauntlet panel maps GUI fields to the bundled
     * self-play harness command.
     */
    private static void testEngineGauntletCommandBuilder() {
        EngineGauntletPanel.GauntletConfig config = new EngineGauntletPanel.GauntletConfig(
                "all",
                "none",
                "mcts",
                "alpha-beta",
                "classical",
                "nnue",
                "12",
                "77",
                "120",
                "2",
                "1",
                "3",
                "/usr/bin/stockfish",
                "",
                "256",
                "",
                "SyzygyPath=/tb",
                "",
                "200ms",
                "4000");
        List<String> command = EngineGauntletPanel.buildCommand(config);
        assertEquals("engine", command.get(0), "gauntlet command targets the engine area");
        assertEquals("gauntlet", command.get(1), "gauntlet command targets the gauntlet action");
        assertEquals("mcts", valueAfterFlag(command, "--searchA"), "gauntlet candidate search");
        assertEquals("alpha-beta", valueAfterFlag(command, "--searchB"), "gauntlet baseline search");
        assertEquals("nnue", valueAfterFlag(command, "--evalB"), "gauntlet baseline evaluator");
        assertFalse(command.contains("--nodes"), "shared node budget is replaced by per-side budgets");
        assertEquals("12", valueAfterFlag(command, "--openings"), "gauntlet opening count");
        assertEquals("77", valueAfterFlag(command, "--seed"), "gauntlet seed");
        assertEquals("2", valueAfterFlag(command, "--workers"), "gauntlet worker count");
        assertEquals("3", valueAfterFlag(command, "--threadsB"), "gauntlet baseline threads");
        assertEquals("/usr/bin/stockfish", valueAfterFlag(command, "--engineA"),
                "gauntlet candidate external UCI engine");
        assertEquals("256", valueAfterFlag(command, "--hashA"), "gauntlet candidate engine hash");
        assertEquals("SyzygyPath=/tb", valueAfterFlag(command, "--optionsA"),
                "gauntlet candidate engine UCI options");
        assertFalse(command.contains("--engineB"), "blank baseline engine adds no flag");
        assertFalse(command.contains("--hashB"), "blank baseline hash adds no flag");
        assertEquals("200", valueAfterFlag(command, "--movetimeA"),
                "gauntlet candidate ms-suffixed budget maps to movetime");
        assertEquals("4000", valueAfterFlag(command, "--nodesB"),
                "gauntlet baseline numeric budget maps to nodes");
        assertTrue(command.contains("--stream"), "gauntlet GUI always streams per-game records");
    }

    /**
     * Verifies command-option helper copy stays out of the visible row text
     * while remaining discoverable through tooltips.
     */
    private static void testCommandFormMovesHelperCopyToTooltips() {
        JComponent form = (JComponent) construct(type("CommandForm"), new Class<?>[0]);
        invoke(form, "setTemplate",
                new Class<?>[] { type("CommandTemplates$CommandTemplate"),
                        type("CommandTemplates$TemplateContext") },
                template("Legal moves"), templateContext(START_FEN, "2s", "4", "3", "1"));

        assertFalse(componentTreeHasLabelText(form, "Print UCI moves"),
                "command form hides option descriptions from row labels");
        assertFalse(componentTreeHasLabelText(form, "REQUIRED"),
                "command form removes redundant required section heading");
        assertTrue(componentTreeHasTooltip(form, "Print UCI moves"),
                "command form keeps option descriptions in tooltips");
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
     * Verifies unedited command-form depth fields keep following the shared
     * workbench depth context.
     */
    private static void testCommandFormRefreshesUneditedDynamicDepth() {
        JComponent form = (JComponent) construct(type("CommandForm"), new Class<?>[0]);
        invoke(form, "setTemplate",
                new Class<?>[] { type("CommandTemplates$CommandTemplate"),
                        type("CommandTemplates$TemplateContext") },
                template("Perft"), templateContext(START_FEN, "2s", "4", "3", "1"));

        invoke(form, "refreshDynamicValues", new Class<?>[] { type("CommandTemplates$TemplateContext") },
                templateContext(START_FEN, "2s", "6", "3", "1"));
        assertEquals("6", valueAfterFlag(formArgs(form), "--depth"),
                "unedited perft depth follows shared context");
    }

    /**
     * Verifies manually edited command-form depth fields are not overwritten by
     * later preview refreshes that still carry the old shared depth.
     */
    private static void testCommandFormPreservesManualDynamicDepth() {
        JComponent form = (JComponent) construct(type("CommandForm"), new Class<?>[0]);
        invoke(form, "setTemplate",
                new Class<?>[] { type("CommandTemplates$CommandTemplate"),
                        type("CommandTemplates$TemplateContext") },
                template("Perft"), templateContext(START_FEN, "2s", "4", "3", "1"));

        JTextField depth = textFieldWithTooltip(form, "Search or perft depth");
        depth.setText("6");
        assertEquals("6", valueAfterFlag(formArgs(form), "--depth"),
                "edited perft depth is reflected in args");

        invoke(form, "refreshDynamicValues", new Class<?>[] { type("CommandTemplates$TemplateContext") },
                templateContext(START_FEN, "2s", "4", "3", "1"));
        assertEquals("6", valueAfterFlag(formArgs(form), "--depth"),
                "edited perft depth survives stale shared context refresh");

        depth.setText("4");
        invoke(form, "refreshDynamicValues", new Class<?>[] { type("CommandTemplates$TemplateContext") },
                templateContext(START_FEN, "2s", "7", "3", "1"));
        assertEquals("7", valueAfterFlag(formArgs(form), "--depth"),
                "matching shared depth resumes dynamic refreshes");
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
     * Verifies the merged "Analyze batch" Build template emits the external-engine
     * flags when the shared context supplies their values.
     */
    private static void testEngineBatchTasksUseExternalConfigOptions() {
        JComponent form = (JComponent) construct(type("CommandForm"), new Class<?>[0]);
        invoke(form, "setTemplate",
                new Class<?>[] { type("CommandTemplates$CommandTemplate"),
                        type("CommandTemplates$TemplateContext") },
                template("Analyze batch"),
                templateContext(START_FEN, "4s", "5", "3", "6",
                        "config/default.engine.toml", "900", "128"));
        List<String> args = stringList(invoke(form, "args", new Class<?>[0]));

        assertTrue(hasFlag(args, "--protocol-path"), "batch protocol flag");
        assertTrue(hasFlag(args, "--max-nodes"), "batch nodes flag");
        assertTrue(hasFlag(args, "--threads"), "batch threads flag");
        assertTrue(hasFlag(args, "--hash"), "batch hash flag");
        assertTrue(hasFlag(args, "--jsonl"), "batch jsonl flag");
        assertTrue((Boolean) invoke(template("Analyze batch"), "usesPositionList", new Class<?>[0]),
                "analyze batch reveals the position list");
    }

    /**
     * Verifies the merged position-list editor starts empty (no demo FENs).
     */
    private static void testPositionListEditorStartsEmpty() {
        Object editor = construct(type("PositionListEditor"),
                new Class<?>[] { type("PositionListEditor$Context") }, positionListContext());
        assertEquals("", invoke(editor, "text", new Class<?>[0]), "position list starts empty");
    }

    /**
     * Verifies command-script validation rejects launcher-only rows before the
     * runner creates a child process.
     */
    private static void testPositionListEditorRejectsEmptyCliScriptRows() {
        Object editor = construct(type("PositionListEditor"),
                new Class<?>[] { type("PositionListEditor$Context") }, positionListContext());

        invoke(editor, "setText", new Class<?>[] { String.class }, "crtk version\nhelp batch run");
        Object valid = invoke(editor, "scanCommandScript", new Class<?>[0]);
        assertEquals(Integer.valueOf(2), invoke(valid, "commands", new Class<?>[0]),
                "valid command-script row count");
        assertEquals(Boolean.FALSE, invoke(valid, "hasError", new Class<?>[0]),
                "valid command-script rows have no error");

        invoke(editor, "setText", new Class<?>[] { String.class }, "crtk");
        Object invalid = invoke(editor, "scanCommandScript", new Class<?>[0]);
        assertEquals(Boolean.TRUE, invoke(invalid, "hasError", new Class<?>[0]),
                "launcher-only command-script row is invalid");
        assertEquals(Integer.valueOf(1), invoke(invalid, "firstErrorLine", new Class<?>[0]),
                "launcher-only command-script error line");
        assertEquals("missing command after crtk", invoke(invalid, "firstError", new Class<?>[0]),
                "launcher-only command-script error text");
    }

    /**
     * Builds a no-op position-list editor context for tests.
     *
     * @return proxy context
     */
    private static Object positionListContext() {
        Class<?> contextType = type("PositionListEditor$Context");
        return Proxy.newProxyInstance(WorkbenchRegressionTest.class.getClassLoader(),
                new Class<?>[] { contextType }, (proxy, method, args) ->
                        "currentFen".equals(method.getName()) ? START_FEN : null);
    }

    /**
     * Verifies command templates expose stable labels suitable for command tabs.
     */
    private static void testCommandTemplatesHaveCompactTabLabels() {
        List<Object> templates = objectList(invokeStatic(type("CommandTemplates"),
                "commandTemplates", new Class<?>[0]));
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
    private static void testCommandControllerCoversCliRegistry() {
        Object allCli = template("All CLI");
        List<Object> options = objectList(invoke(allCli, "options", new Class<?>[0]));
        List<String> choices = List.of();
        for (Object option : options) {
            List<String> candidate = stringList(invoke(option, "choices", new Class<?>[0]));
            if (!candidate.isEmpty()) {
                choices = candidate;
                break;
            }
        }
        assertFalse(choices.isEmpty(), "all-cli template exposes command choices");
        for (String path : commandControllerCliPaths()) {
            assertTrue(choices.contains(path), "all-cli template covers " + path);
        }
        for (String choice : choices) {
            CliCommand command = CliRegistry.resolve(List.of(choice.split("\\s+")));
            assertTrue(command != null && command.isRunnable(), "all-cli choice resolves: " + choice);
            assertFalse(isWorkbenchLauncherPath(choice), "all-cli excludes nested workbench launcher: " + choice);
        }
    }

    /**
     * Verifies the merged "CLI script" Build template delegates to {@code batch
     * run}, consumes a command list, and keeps going on failures.
     */
    private static void testBatchRunnerOffersCliScriptForAllCliCommands() {
        Object template = template("CLI script");
        assertEquals(List.of("batch", "run"),
                stringList(invoke(template, "baseArgs", new Class<?>[0])),
                "CLI script delegates to batch run");
        assertEquals("COMMAND_LINES", String.valueOf(invoke(template, "inputKind", new Class<?>[0])),
                "CLI script uses command-line input");
        assertTrue((Boolean) invoke(template, "usesPositionList", new Class<?>[0]),
                "CLI script reveals the command list");

        JComponent form = (JComponent) construct(type("CommandForm"), new Class<?>[0]);
        invoke(form, "setTemplate",
                new Class<?>[] { type("CommandTemplates$CommandTemplate"),
                        type("CommandTemplates$TemplateContext") },
                template, templateContext(START_FEN, "1s", "4", "2", "1"));
        assertTrue(hasFlag(stringList(invoke(form, "args", new Class<?>[0])), "--keep-going"),
                "CLI script keeps going after failures");
    }

    /**
     * Verifies artifact opening reports desktop-integration runtime failures
     * through the Workbench error path instead of letting them escape the EDT.
     */
    private static void testRunArtifactsDesktopOpenHandlesRuntimeFailures() {
        String source;
        String helper;
        try {
            source = java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/application/gui/workbench/session/RunArtifacts.java"),
                    java.nio.charset.StandardCharsets.UTF_8);
            helper = java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/application/gui/workbench/session/DesktopOpen.java"),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            throw new AssertionError("unable to read desktop-open sources", ex);
        }
        assertTrue(source.contains("DesktopOpen.open(path)"),
                "RunArtifacts delegates desktop opening to the shared helper");
        assertTrue(helper.contains("desktop.isSupported(Desktop.Action.OPEN)"),
                "DesktopOpen checks desktop OPEN action support");
        assertTrue(helper.contains("catch (IOException | RuntimeException ex)"),
                "RunArtifacts catches desktop-open runtime failures");
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
     * Returns runnable CLI command paths and aliases that should be offered by
     * the command controller.
     *
     * @return runnable command-controller paths
     */
    private static List<String> commandControllerCliPaths() {
        List<String> paths = new ArrayList<>();
        collectCommandControllerCliPaths(CliRegistry.root(), paths);
        return List.copyOf(paths);
    }

    /**
     * Returns command args from a command form.
     *
     * @param form command form
     * @return command args
     */
    private static List<String> formArgs(JComponent form) {
        return stringList(invoke(form, "args", new Class<?>[0]));
    }

    /**
     * Finds a text field by tooltip fragment.
     *
     * @param component root component
     * @param tooltip tooltip fragment
     * @return matching text field
     */
    private static JTextField textFieldWithTooltip(Component component, String tooltip) {
        if (component instanceof JTextField field
                && field.getToolTipText() != null
                && field.getToolTipText().contains(tooltip)) {
            return field;
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return textFieldWithTooltip(child, tooltip);
                } catch (AssertionError ex) {
                    // Continue searching siblings.
                }
            }
        }
        throw new AssertionError("missing text field with tooltip: " + tooltip);
    }

    /**
     * Recursively collects runnable CLI paths and aliases.
     *
     * @param command current command node
     * @param paths destination list
     */
    private static void collectCommandControllerCliPaths(CliCommand command, List<String> paths) {
        if (command.isRunnable() && !command.commandPath().isBlank()
                && !isWorkbenchLauncherPath(command.commandPath())) {
            paths.add(command.commandPath());
            for (String aliasPath : command.aliasPaths()) {
                if (!isWorkbenchLauncherPath(aliasPath)) {
                    paths.add(aliasPath);
                }
            }
        }
        for (CliCommand child : command.children()) {
            collectCommandControllerCliPaths(child, paths);
        }
    }

    /**
     * Returns whether a command path launches the workbench itself.
     *
     * @param path command path
     * @return true when path is a nested workbench launcher
     */
    private static boolean isWorkbenchLauncherPath(String path) {
        return "workbench".equals(path) || "gui".equals(path);
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
                        boolean.class, boolean.class, java.util.List.class },
                "Study Book", "Test Study", "Subtitle", "source", "output", Boolean.TRUE, "", 3,
                Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, java.util.List.of());
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
     * Verifies every publishing workflow preview can paint its task-specific
     * layout.
     */
    private static void testPublishingVisualPreviewPaintsTaskLayouts() {
        JComponent preview = (JComponent) construct(type("PublishPreview"), new Class<?>[0]);
        String[] workflows = {
                "Diagrams PDF",
                "Render Manifest PDF",
                "Puzzle Collection",
                "Study Book",
                "Cover PDF"
        };
        boolean[] cover = { false, false, false, false, true };
        boolean[] diagram = { true, false, false, false, false };
        for (int i = 0; i < workflows.length; i++) {
            Object data = construct(type("PublishPreview$Preview"),
                    new Class<?>[] { String.class, String.class, String.class, String.class, String.class,
                            boolean.class, String.class, int.class, boolean.class, boolean.class,
                            boolean.class, boolean.class, java.util.List.class },
                    workflows[i], "Preview Title", "Subtitle",
                    "current board FEN (rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b)",
                    "output preview", Boolean.TRUE, "", 4,
                    Boolean.valueOf(cover[i]), Boolean.valueOf(diagram[i]), Boolean.FALSE, Boolean.FALSE,
                    java.util.List.of());
            invoke(preview, "setPreview", new Class<?>[] { type("PublishPreview$Preview") }, data);
            assertPaintsOpaqueCorner(preview, 320, 420, "publishing preview paints " + workflows[i]);
        }
    }

    /**
     * Verifies command previews quote arguments containing spaces.
     */
    private static void testCommandPreviewQuoting() {
        String command = (String) invokeStatic(type("CommandRunner"), "displayCommand",
                new Class<?>[] { List.class }, List.of("book", "render", "--title", "A B"));
        assertEquals("crtk book render --title \"A B\"", command, "quoted command preview");
        String block = (String) invokeStatic(type("CommandRunner"), "displayCommandBlock",
                new Class<?>[] { List.class }, List.of("book", "render", "--title", "A B"));
        assertEquals("crtk book render \\\n  --title \"A B\"", block,
                "multiline command preview");
        String fenBlock = (String) invokeStatic(type("CommandRunner"), "displayCommandBlock",
                new Class<?>[] { List.class }, List.of("move", "list", "--fen", START_FEN, "--format", "both"));
        assertEquals("crtk move list \\\n  --fen \"" + START_FEN + "\" \\\n  --format both",
                fenBlock, "value-taking options stay with their values");
        String flagBlock = (String) invokeStatic(type("CommandRunner"), "displayCommandBlock",
                new Class<?>[] { List.class }, List.of("move", "after", "--json", "e2e4"));
        assertEquals("crtk move after \\\n  --json \\\n  e2e4", flagBlock,
                "boolean flags do not absorb trailing positional arguments");
    }

    /**
     * Verifies the Run command preview reserves readable height but caps tall
     * commands so the scroll pane handles overflow.
     */
    private static void testCommandPreviewHeightUsesReadableRange() {
        String shortCommand = "crtk move list";
        assertEquals(Integer.valueOf(6), invokeStatic(type("WindowCommandLayer"), "previewRows",
                new Class<?>[] { String.class }, shortCommand), "short preview keeps readable minimum");
        String tallCommand = String.join("\n", List.of("crtk", "a", "b", "c", "d", "e", "f", "g", "h", "i"));
        assertEquals(Integer.valueOf(10), invokeStatic(type("WindowCommandLayer"), "previewRows",
                new Class<?>[] { String.class }, tallCommand), "tall preview caps at ten rows");
    }
}
