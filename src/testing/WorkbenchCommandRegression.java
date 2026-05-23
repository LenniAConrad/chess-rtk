/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package testing;

import static testing.WorkbenchTestSupport.*;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import application.cli.CliCommand;
import application.cli.CliRegistry;


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
        testCommandFormMovesHelperCopyToTooltips();
        testDynamicOptionRefresh();
        testDynamicOptionRefreshSkipsUnchangedValues();
        testEngineTemplateContextFeedsExternalConfigOptions();
        testEngineBatchTasksUseExternalConfigOptions();
        testBatchPanelStartsWithEmptyInputAndSharedDuration();
        testBatchPanelRejectsEmptyCliScriptRows();
        testCommandTemplatesHaveCompactTabLabels();
        testCommandControllerCoversCliRegistry();
        testBatchRunnerOffersCliScriptForAllCliCommands();
        testCommandLineTokenizerPreservesQuotedArguments();
        testCommandPreviewQuoting();
        testPublishingPreviewFenCompaction();
        testPublishingVisualPreviewPages();
    }

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
     * Verifies command-script validation rejects launcher-only rows before the
     * runner creates a child process.
     */
    private static void testBatchPanelRejectsEmptyCliScriptRows() {
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
        input.setText("crtk version\nhelp batch run");
        Object valid = invoke(panel, "scanCommandScript", new Class<?>[0]);
        assertEquals(Integer.valueOf(2), invoke(valid, "commands", new Class<?>[0]),
                "valid command-script row count");
        assertEquals(Boolean.FALSE, invoke(valid, "hasError", new Class<?>[0]),
                "valid command-script rows have no error");

        input.setText("crtk");
        Object invalid = invoke(panel, "scanCommandScript", new Class<?>[0]);
        assertEquals(Boolean.TRUE, invoke(invalid, "hasError", new Class<?>[0]),
                "launcher-only command-script row is invalid");
        assertEquals(Integer.valueOf(1), invoke(invalid, "firstErrorLine", new Class<?>[0]),
                "launcher-only command-script error line");
        assertEquals("missing command after crtk", invoke(invalid, "firstError", new Class<?>[0]),
                "launcher-only command-script error text");
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
     * Verifies command previews quote arguments containing spaces.
     */
    private static void testCommandPreviewQuoting() {
        String command = (String) invokeStatic(type("CommandRunner"), "displayCommand",
                new Class<?>[] { List.class }, List.of("book", "render", "--title", "A B"));
        assertEquals("crtk book render --title \"A B\"", command, "quoted command preview");
    }
}
