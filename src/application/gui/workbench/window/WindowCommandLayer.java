package application.gui.workbench.window;

import application.Config;
import application.gui.workbench.command.CommandPalette.PaletteAction;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.command.CommandTemplates;
import application.gui.workbench.game.EngineEval;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.tag.Generator;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Evaluation;
import chess.uci.Output;
import chess.uci.Protocol;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

import static application.gui.workbench.command.CommandArgs.addOptionalPositiveIntegerArg;
import static application.gui.workbench.command.CommandArgs.addOptionalTextArg;
import static application.gui.workbench.ui.SwingTasks.runAsync;
import static application.gui.workbench.ui.Ui.addVerticalFiller;
import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.collapsible;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.flow;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.iconButton;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.optionGroup;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.showConfirmDialog;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.styleSpinners;
import static application.gui.workbench.ui.Ui.tabbedPane;
import static application.gui.workbench.ui.Ui.titled;
import static application.gui.workbench.ui.Ui.transparentPanel;
import static application.gui.workbench.ui.Ui.trimmed;
import static application.gui.workbench.ui.Ui.withTooltip;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */
@SuppressWarnings("java:S6539")

public abstract class WindowCommandLayer extends WindowGameLayer {
    /** Serialization identifier for Swing frame compatibility. */
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
        for (int i = 0; i < commandTemplates.size(); i++) {
            CommandTemplate template = commandTemplates.get(i);
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
        gameTable.addMouseListener(new MouseAdapter() {
            /**
             * Jumps to a move when a history row is double-clicked.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int row = gameTable.getSelectedRow();
                    if (row >= 0) {
                        jumpGameTo(gameModel.plyForRow(row));
                    }
                }
            }
        });
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
            commandField.setText(CommandRunner.displayCommand(selectedTemplateArgs()));
        } catch (IllegalArgumentException ex) {
            commandField.setText("invalid template: " + ex.getMessage());
        }
        // Keep the command start visible rather than scrolling to the caret.
        commandField.setCaretPosition(0);
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
        publishingPanel.updateCommand();
    }

    /**
     * Queues a publishing preview refresh after document edits settle.
     */
    protected void requestPublishCommandUpdate() {
        publishingPanel.requestCommandUpdate();
    }

    /**
     * Runs the selected publishing workflow.
     */
    protected void runPublishingCommand() {
        publishingPanel.runCommand();
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
        return value.isEmpty() ? "2s" : value;
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
        appendConsole("[copied]\n");
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
