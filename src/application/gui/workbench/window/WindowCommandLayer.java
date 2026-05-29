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
import java.util.Objects;
import java.util.regex.Matcher;
import javax.swing.ButtonGroup;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.toolbarSeparator;
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
            console.appendOutput(text);
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
        commandButtons.clear();
        ButtonGroup group = new ButtonGroup();
        String previousCategory = null;
        for (int i = 0; i < commandTemplates.size(); i++) {
            CommandTemplate template = commandTemplates.get(i);
            String category = commandCategoryOf(template.name());
            if (previousCategory != null && !category.equals(previousCategory)) {
                commandPicker.add(toolbarSeparator());
            }
            previousCategory = category;
            javax.swing.JToggleButton tab = new javax.swing.JToggleButton(template.name());
            Theme.commandTab(tab);
            tab.setSelected(i == selectedCommandIndex);
            int index = i;
            tab.addActionListener(event -> selectCommandTemplate(index));
            group.add(tab);
            commandButtons.add(tab);
            commandPicker.add(tab);
        }
        commandPicker.revalidate();
        commandPicker.repaint();
    }

    /**
     * Categorises a command template by name so the picker can render related
     * commands together. Three categories: <em>inspect</em> reads the current
     * position, <em>compute</em> runs an engine, <em>mutate</em> changes the
     * board or produces new data.
     *
     * @param name template name
     * @return category key
     */
    private static String commandCategoryOf(String name) {
        return switch (name) {
            case "Legal moves", "Tags", "Threats", "Position diff" -> "inspect";
            case "Best move", "Mate", "Analyze", "Eval", "Built-in search", "Perft" -> "compute";
            case "Apply move", "Generate FENs", "All CLI" -> "mutate";
            default -> "inspect";
        };
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
        for (int i = 0; i < commandButtons.size(); i++) {
            commandButtons.get(i).setSelected(i == index);
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
        Theme.table(gameTable, 29);
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
            return 3;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return Math.max(3, Math.min(6, lines));
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
        batchPanel.updateCommand();
        updatePublishCommand();
    }

    /**
     * Builds arguments for the selected curated command template.
     *
     * @return command arguments
     */
    protected List<String> selectedTemplateArgs() {
        CommandTemplate template = selectedCommandTemplate();
        if (template == null) {
            return List.of();
        }
        List<String> args = new ArrayList<>(template.baseArgs());
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
            batchPanel.setDurationText(analysisDurationField.getText());
            requestCommandPreviews();
        } finally {
            syncingDuration = false;
        }
    }

    /**
     * Synchronizes the analysis duration from the batch duration field.
     *
     * @param value duration text
     */
    protected void syncDurationFromBatch(String value) {
        if (syncingDuration) {
            return;
        }
        syncingDuration = true;
        try {
            if (!java.util.Objects.equals(value, analysisDurationField.getText())) {
                analysisDurationField.setText(value);
            }
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
