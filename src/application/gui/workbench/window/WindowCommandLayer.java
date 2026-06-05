package application.gui.workbench.window;

import application.gui.workbench.Defaults;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.command.CommandTemplates;
import application.gui.workbench.game.EngineEval;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Setup;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.styleCombo;
import static application.gui.workbench.ui.Ui.trimmed;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */

public abstract class WindowCommandLayer extends WindowGameLayer {
    /**
     * Serialization identifier for Swing frame compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Appends console text on the EDT.
     *
     * @param text text
     */
    protected void appendConsole(String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            // Fan out to every open console (primary plus any duplicates).
            for (application.gui.workbench.command.Console target : consoles) {
                target.appendOutput(text);
            }
        } else {
            SwingUtilities.invokeLater(() -> appendConsole(text));
        }
    }

    /**
     * Updates the command execution state label.
     *
     * @param state state text
     */
    protected void setCommandState(String state) {
        commandStateLabel.setText(state);
        setStatusBarEngine(state == null || state.isBlank() ? "idle" : state);
    }

    /**
     * Installs command templates.
     */
    protected void installTemplates() {
        commandTemplates = CommandTemplates.commandTemplates();
        commandPicker.removeAll();
        // A single dropdown over every template, replacing a wrapping row of 14
        // peer toggle buttons that dominated the toolbar. One compact control
        // reads as "pick what to run" instead of a wall of equal-weight pills.
        String[] names = new String[commandTemplates.size()];
        for (int i = 0; i < commandTemplates.size(); i++) {
            names[i] = commandTemplates.get(i).name();
        }
        commandCombo = new javax.swing.JComboBox<>(names);
        styleCombo(commandCombo);
        if (selectedCommandIndex >= 0 && selectedCommandIndex < names.length) {
            commandCombo.setSelectedIndex(selectedCommandIndex);
        }
        commandCombo.addActionListener(event -> {
            if (!syncingCommandCombo) {
                selectCommandTemplate(commandCombo.getSelectedIndex());
            }
        });
        commandPicker.add(label("Command"));
        commandPicker.add(commandCombo);
        commandPicker.revalidate();
        commandPicker.repaint();
    }

    /**
     * Selects a command template by index and refreshes the builder.
     *
     * @param index template index
     */
    protected void selectCommandTemplate(int index) {
        if (index < 0 || index >= commandTemplates.size()) {
            return;
        }
        selectedCommandIndex = index;
        if (commandCombo != null && commandCombo.getSelectedIndex() != index) {
            syncingCommandCombo = true;
            commandCombo.setSelectedIndex(index);
            syncingCommandCombo = false;
        }
        updateCommandOptions();
        updateBuiltCommand();
    }

    /**
     * Returns whether option-row text matches all filter tokens.
     *
     * @param query raw query
     * @param values row values
     * @return true when every token appears somewhere in the row text
     */
    public static boolean optionFilterMatches(String query, String... values) {
        if (query == null || query.isBlank()) {
            return true;
        }
        StringBuilder haystack = new StringBuilder();
        for (String value : values) {
            if (value != null) {
                haystack.append(value).append(' ');
            }
        }
        String normalized = haystack.toString().toLowerCase(Locale.ROOT);
        for (String token : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!token.isBlank() && !normalized.contains(token)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Configures the game history table.
     */
    protected void configureGameTable() {
        gameTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Theme.table(gameTable, Theme.TABLE_ROW_HEIGHT);
        gameTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        gameTable.getColumnModel().getColumn(0).setPreferredWidth(64);
        gameTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        gameTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        gameTable.getColumnModel().getColumn(3).setPreferredWidth(520);
        gameTable.setPreferredScrollableViewportSize(new Dimension(780, 180));
        if (!gameTableSelectionListenerInstalled) {
            gameTable.getSelectionModel().addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting() && !syncingGameTableSelection) {
                    int viewRow = gameTable.getSelectedRow();
                    if (viewRow >= 0) {
                        showGameRow(gameTable.convertRowIndexToModel(viewRow));
                    }
                }
            });
            gameTableSelectionListenerInstalled = true;
        }
    }

    /**
     * Updates command options for the selected template.
     */
    protected void updateCommandOptions() {
        CommandTemplate template = selectedCommandTemplate();
        if (template == null) {
            return;
        }
        commandForm.setTemplate(template, templateContext());
    }

    /**
     * Resets the selected command to its default flags and values.
     */
    protected void resetSelectedTemplate() {
        commandForm.resetDefaults(templateContext());
        updateBuiltCommand();
    }

    /**
     * Returns the selected command to its default enabled option set.
     */
    protected void clearOptionalTemplateOptions() {
        commandForm.clearOptional();
        updateBuiltCommand();
    }

    /**
     * Updates command field from selected template.
     */
    protected void updateBuiltCommand() {
        try {
            commandField.setText(CommandRunner.displayCommandBlock(selectedTemplateArgs()));
        } catch (IllegalArgumentException ex) {
            commandField.setText("invalid template: " + ex.getMessage());
        }
        commandField.setRows(previewRows(commandField.getText()));
        commandField.revalidate();
        // Keep the command start visible rather than scrolling to the caret.
        commandField.setCaretPosition(0);
    }

    /**
     * Chooses a compact but useful preview height for a multiline command.
     *
     * @param text preview text
     * @return row count
     */
    private static int previewRows(String text) {
        if (text == null || text.isBlank()) {
            return 1;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return Math.max(1, Math.min(5, lines));
    }

    /**
     * Queues command, batch, and publishing preview refreshes.
     */
    protected void requestCommandPreviews() {
        if (commandPreviewUpdateQueued) {
            return;
        }
        commandPreviewUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            if (!commandPreviewUpdateQueued) {
                return;
            }
            updateCommandPreviews();
        });
    }

    /**
     * Updates command, batch, and publishing previews immediately.
     */
    protected void updateCommandPreviews() {
        commandPreviewUpdateQueued = false;
        commandForm.refreshDynamicValues(templateContext());
        updateBuiltCommand();
        updatePublishCommand();
    }

    /**
     * Builds preview arguments for the selected command template. Batch-style
     * commands show a stable {@code --input} placeholder path; the real file is
     * written only at run time (see {@link #selectedTemplateRunArgs()}).
     *
     * @return command arguments
     */
    protected List<String> selectedTemplateArgs() {
        CommandTemplate template = selectedCommandTemplate();
        if (template == null) {
            return List.of();
        }
        List<String> args = new ArrayList<>(template.baseArgs());
        if (template.usesPositionList()) {
            args.add("--input");
            args.add(Defaults.WORKBENCH_FENS_PLACEHOLDER);
        }
        args.addAll(commandForm.args());
        return List.copyOf(args);
    }

    /**
     * Builds runnable arguments for the selected command template. For a
     * batch-style command this materializes the editor's positions/commands to
     * a temporary file and substitutes the real {@code --input} path.
     *
     * @return command arguments
     * @throws java.io.IOException when the input file cannot be written
     */
    protected List<String> selectedTemplateRunArgs() throws java.io.IOException {
        CommandTemplate template = selectedCommandTemplate();
        if (template == null) {
            return List.of();
        }
        List<String> args = new ArrayList<>(template.baseArgs());
        if (template.usesPositionList()) {
            String prefix = template.inputKind() == CommandTemplates.BatchInputKind.COMMAND_LINES
                    ? "crtk-workbench-commands-"
                    : "crtk-workbench-fens-";
            java.nio.file.Path input = application.cli.PathOps.createLocalTempFile(prefix, ".txt");
            input.toFile().deleteOnExit();
            java.nio.file.Files.writeString(input, commandForm.positionsText(),
                    java.nio.charset.StandardCharsets.UTF_8);
            args.add("--input");
            args.add(input.toString());
        }
        args.addAll(commandForm.args());
        return List.copyOf(args);
    }

    /**
     * Returns the selected command template.
     *
     * @return selected template or null
     */
    protected CommandTemplate selectedCommandTemplate() {
        if (selectedCommandIndex < 0 || selectedCommandIndex >= commandTemplates.size()) {
            return null;
        }
        return commandTemplates.get(selectedCommandIndex);
    }

    /**
     * Updates the publishing command preview.
     */
    protected void updatePublishCommand() {
        for (application.gui.workbench.publish.PublishingPanel panel : publishingPanels) {
            panel.updateCommand();
        }
    }

    /**
     * Queues a publishing preview refresh after document edits settle.
     */
    protected void requestPublishCommandUpdate() {
        for (application.gui.workbench.publish.PublishingPanel panel : publishingPanels) {
            panel.requestCommandUpdate();
        }
    }

    /**
     * Runs the selected publishing workflow.
     */
    protected void runPublishingCommand() {
        publishingPanel().runCommand();
    }

    /**
     * Builds template context.
     *
     * @return context
     */
    protected TemplateContext templateContext() {
    return new TemplateContext(currentFen(), durationValue(), depthValue(), multipvValue(), threadsValue(),
                engineProtocolValue(), engineNodesValue(), engineHashValue());
    }

    /**
     * Synchronizes the batch duration from the analysis duration field.
     */
    protected void syncDurationFromAnalysis() {
        if (syncingDuration) {
            return;
        }
        syncingDuration = true;
        try {
            requestCommandPreviews();
        } finally {
            syncingDuration = false;
        }
    }

    /**
     * Highlights first legal UCI move in output.
     *
     * @param args command arguments
     * @param output command output
     */
    protected void maybeHighlightMove(List<String> args, String output) {
        board.setSuggestedMove(Move.NO_MOVE);
        if (!shouldShowSuggestedMove(args) || currentPosition == null || output == null) {
            return;
        }
        MoveList legal = currentPosition.legalMoves();
        Matcher matcher = UCI_PATTERN.matcher(output);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (Move.isMove(candidate)) {
                short move = Move.parse(candidate);
                if (legal.contains(move)) {
                    board.setSuggestedMove(move);
                    return;
                }
            }
        }
    }

    /**
     * Returns whether a command output should be interpreted as a current-position
     * engine suggestion.
     *
     * @param args command arguments
     * @return true when a best-move arrow is appropriate
     */
    protected static boolean shouldShowSuggestedMove(List<String> args) {
        if (args == null || args.size() < 2 || !"engine".equals(args.get(0))) {
            return false;
        }
    return switch (args.get(1)) {
            case "bestmove", "bestmove-uci", "bestmove-both", "analyze", "builtin" -> true;
            default -> false;
        };
    }

    /**
     * Parses the first engine evaluation emitted by {@code engine analyze}.
     *
     * @param output command output
     * @return parsed evaluation, or null when unavailable
     */
    protected static EngineEval parseEngineEval(String output) {
        return EngineEval.parse(output);
    }

    /**
     * Returns current FEN.
     *
     * @return FEN
     */
    protected String currentFen() {
        return currentPosition == null ? Setup.getStandardStartFEN() : currentPosition.toString();
    }

    /**
     * Returns duration.
     *
     * @return duration string
     */
    protected String durationValue() {
        String value = trimmed(analysisDurationField);
        return value.isEmpty() ? Defaults.ANALYSIS_DURATION : value;
    }

    /**
     * Returns depth.
     *
     * @return depth string
     */
    protected String depthValue() {
        return String.valueOf(depthModel.getValue());
    }

    /**
     * Returns multipv.
     *
     * @return multipv string
     */
    protected String multipvValue() {
        return String.valueOf(multipvModel.getValue());
    }

    /**
     * Returns threads.
     *
     * @return thread count string
     */
    protected String threadsValue() {
        return String.valueOf(threadsModel.getValue());
    }

    /**
     * Returns external-engine protocol path.
     *
     * @return protocol path or blank to use CLI config
     */
    protected String engineProtocolValue() {
    return trimmed(engineProtocolField);
    }

    /**
     * Returns optional external-engine node budget.
     *
     * @return node budget or blank
     */
    protected String engineNodesValue() {
    return trimmed(engineNodesField);
    }

    /**
     * Returns optional external-engine hash size.
     *
     * @return hash MB or blank
     */
    protected String engineHashValue() {
    return trimmed(engineHashField);
    }

    /**
     * Copies text to clipboard.
     *
     * @param text text
     */
    protected void copyText(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text == null ? "" : text),
                null);
    }

    /**
     * Shows a warning in the workbench notification layer.
     *
     * @param title title
     * @param message message
     */
    protected void showWarning(String title, String message) {
        showNotice(Toast.Kind.WARNING, title, message);
    }

    /**
     * Shows an error in the workbench notification layer.
     *
     * @param title title
     * @param message message
     */
    protected void showError(String title, String message) {
        showNotice(Toast.Kind.ERROR, title, message);
    }

    /**
     * Shows an inline bottom notice without opening a modal dialog.
     *
     * @param kind severity
     * @param title fallback title
     * @param message primary message
     */
    protected void showNotice(Toast.Kind kind, String title, String message) {
        toast(kind, message == null || message.isBlank() ? title : message);
    }

}
