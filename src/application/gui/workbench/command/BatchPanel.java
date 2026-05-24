package application.gui.workbench.command;

import application.cli.PathOps;
import application.gui.workbench.Defaults;
import application.gui.workbench.command.CommandTemplates.BatchInputKind;
import application.gui.workbench.command.CommandTemplates.BatchTask;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.command.CommandTemplates.WorkflowControls;
import application.gui.workbench.game.FenInput;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import utility.CommandLine;

import static application.gui.workbench.ui.Ui.addVerticalFiller;
import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.collapsible;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.optionGroup;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleCombos;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.styleSpinners;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Batch workflow tab: FEN input, task-specific controls, command preview, and
 * run handling.
 */
public final class BatchPanel {

    /**
     * Placeholder path used in command previews for generated FEN files.
     */
    private static final String WORKBENCH_FENS_PLACEHOLDER = Defaults.WORKBENCH_FENS_PLACEHOLDER;

    /**
     * Cached preview-only path used when building batch commands without
     * materializing the FENs file.
     */
    private static final Path WORKBENCH_FENS_PLACEHOLDER_PATH = Path.of(WORKBENCH_FENS_PLACEHOLDER);

    /**
     * Services supplied by the frame.
     */
    public interface Host {

        /**
         * Returns the current board FEN.
         *
         * @return current FEN
         */
    String currentFen();

        /**
         * Builds the current command-template context.
         *
         * @return template context
         */
    TemplateContext templateContext();

        /**
         * Runs a command through the workbench command controller.
         *
         * @param args command arguments
         * @param stdin optional stdin
         */
    void runCommand(List<String> args, String stdin);

        /**
         * Copies text to the system clipboard.
         *
         * @param text text to copy
         */
    void copyText(String text);

        /**
         * Shows an error notification.
         *
         * @param title error title
         * @param message error message
         */
    void showError(String title, String message);

        /**
         * Updates the session dashboard batch summary.
         *
         * @param summary summary text
         */
    void updateBatchSummary(String summary);

        /**
         * Refreshes publishing command previews that depend on batch input.
         */
    void updatePublishCommand();

        /**
         * Synchronizes the shared duration value from the batch field.
         *
         * @param value duration text
         */
    void syncBatchDuration(String value);
    }

    /**
     * Host callbacks.
     */
    private final Host host;

    /**
     * Root panel.
     */
    private final JPanel component = transparentPanel(new BorderLayout(0, 0));

    /**
     * Batch duration field.
     */
    private final JTextField batchDurationField = new JTextField(Defaults.ANALYSIS_DURATION);

    /**
     * Batch options panel.
     */
    private final JPanel batchOptionsPanel = transparentPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

    /**
     * Batch depth control.
     */
    private final JSpinner batchDepthSpinner;

    /**
     * Batch MultiPV control.
     */
    private final JSpinner batchMultipvSpinner;

    /**
     * Batch thread control.
     */
    private final JSpinner batchThreadsSpinner;

    /**
     * Batch input area.
     */
    private final JTextArea batchInput = new JTextArea();

    /**
     * Batch input status summary.
     */
    private final JLabel batchInputStatus = new JLabel();

    /**
     * Batch command field.
     */
    private final JTextArea batchCommandField = new JTextArea(3, 36);

    /**
     * Batch task selector.
     */
    private final JComboBox<BatchTask> batchTaskCombo = new JComboBox<>();

    /**
     * Button that appends the current FEN to the batch input.
     */
    private JButton addCurrentFenButton;

    /**
     * Button that clears the batch FEN input.
     */
    private JButton clearBatchInputButton;

    /**
     * Whether a batch-input status refresh is already queued on the event thread.
     */
    private boolean batchInputStatusUpdateQueued;

    /**
     * Whether duration text is currently being synchronized from the frame.
     */
    @SuppressWarnings("java:S1450")
    private boolean settingDuration;

    /**
     * Creates the batch panel.
     *
     * @param host host callbacks
     * @param depthModel shared depth model
     * @param multipvModel shared MultiPV model
     * @param threadsModel shared thread-count model
     */
    public BatchPanel(Host host, SpinnerNumberModel depthModel, SpinnerNumberModel multipvModel,
            SpinnerNumberModel threadsModel) {
        this.host = host;
        batchDepthSpinner = new JSpinner(depthModel);
        batchMultipvSpinner = new JSpinner(multipvModel);
        batchThreadsSpinner = new JSpinner(threadsModel);
        buildUi();
    }

    /**
     * Returns the tab component.
     *
     * @return component
     */
    public JComponent component() {
        return component;
    }

    /**
     * Returns the current batch input text.
     *
     * @return input text
     */
    public String inputText() {
        return batchInput.getText();
    }

    /**
     * Sets duration text from the analysis controls without feeding back into
     * the duration synchronizer.
     *
     * @param value duration text
     */
    public void setDurationText(String value) {
        if (java.util.Objects.equals(value, batchDurationField.getText())) {
            return;
        }
        settingDuration = true;
        try {
            batchDurationField.setText(value);
        } finally {
            settingDuration = false;
        }
    }

    /**
     * Appends the current board FEN to the batch input without joining it onto
     * an existing last line.
     */
    public void appendCurrentFen() {
        String text = batchInput.getText();
        if (!text.isEmpty() && !text.endsWith("\n") && !text.endsWith("\r")) {
            batchInput.append(System.lineSeparator());
        }
        batchInput.append(host.currentFen() + System.lineSeparator());
        batchInput.requestFocusInWindow();
        updateCommand();
    }

    /**
     * Runs the selected batch task.
     */
    public void runBatch() {
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        if (task == null) {
            return;
        }
        try {
            Path input = null;
            if (task.usesFenInput() && !validateFenBatchInput()) {
                return;
            }
            if (task.usesCommandInput() && !validateCommandBatchInput()) {
                return;
            }
            if (task.usesTextInput()) {
                String prefix = task.usesCommandInput() ? "crtk-workbench-commands-" : "crtk-workbench-fens-";
                input = PathOps.createLocalTempFile(prefix, ".txt");
                input.toFile().deleteOnExit();
                Files.writeString(input, batchInput.getText(), StandardCharsets.UTF_8);
            }
            host.runCommand(task.build(input, host.templateContext()), null);
        } catch (IOException ex) {
            host.showError("Batch input failed", ex.getMessage());
        }
    }

    /**
     * Validates the text area as one FEN per row.
     *
     * @return true when the input can be written to a temporary file
     */
    private boolean validateFenBatchInput() {
        FenInput.Summary validation = FenInput.validateBatchFenInput(batchInput.getText());
        refreshInputStatus();
        if (validation.rows() == 0) {
            host.showError("Batch input", "Add at least one FEN before running this batch task.");
            return false;
        }
        if (validation.hasError()) {
            host.showError("Batch input", "Line " + validation.firstErrorLine() + ": "
                    + validation.firstError());
            return false;
        }
        return true;
    }

    /**
     * Validates the text area as one command per row.
     *
     * @return true when the input can be written to a temporary file
     */
    private boolean validateCommandBatchInput() {
        CommandScriptSummary summary = scanCommandScript();
        refreshInputStatus();
        if (summary.commands() == 0) {
            host.showError("Batch input", "Add at least one CRTK command before running this batch task.");
            return false;
        }
        if (summary.hasError()) {
            host.showError("Batch input", "Line " + summary.firstErrorLine() + ": "
                    + summary.firstError());
            return false;
        }
        return true;
    }

    /**
     * Updates the generated batch command preview.
     */
    public void updateCommand() {
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        if (task == null) {
            return;
        }
        batchCommandField.setText(CommandRunner.displayCommand(
                task.build(WORKBENCH_FENS_PLACEHOLDER_PATH, host.templateContext())));
        batchCommandField.setCaretPosition(0);
    }

    /**
     * Builds the tab UI.
     */
    private void buildUi() {
        installBatchTasks();
        placeholder(batchDurationField, "e.g. " + Defaults.ANALYSIS_DURATION + " or 500ms");
        placeholder(batchInput, "one FEN per line");
        styleAreas(batchInput);
        batchInput.setRows(12);
        batchInput.getDocument().addDocumentListener(changeListener(this::requestInputStatusUpdate));

        JComponent runner = scroll(fillViewport(createRunnerPanel()));
        runner.setPreferredSize(new Dimension(420, 520));

        JSplitPane batchPage = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createFenPanel(), runner);
        batchPage.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        batchPage.setOpaque(false);
        batchPage.setContinuousLayout(true);
        batchPage.setResizeWeight(0.64);
        batchPage.setDividerSize(8);
        batchPage.setDividerLocation(0.64);
        SplitPaneStyler.style(batchPage);
        component.add(batchPage, BorderLayout.CENTER);
        updateControls();
        updateCommand();
        refreshInputStatus();
    }

    /**
     * Creates the batch FEN input surface.
     *
     * @return FEN input panel
     */
    private JComponent createFenPanel() {
        JPanel panel = new SurfacePanel(new BorderLayout(8, 8));
        JPanel top = transparentPanel(new BorderLayout(8, 0));
        top.add(Theme.section("Input"), BorderLayout.WEST);
        Theme.foreground(batchInputStatus, Theme.ForegroundRole.MUTED);
        batchInputStatus.setFont(Theme.font(12, java.awt.Font.PLAIN));
        batchInputStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        top.add(batchInputStatus, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll(batchInput), BorderLayout.CENTER);
        addCurrentFenButton = button("Add FEN", false, event -> appendCurrentFen());
        clearBatchInputButton = button("Clear", false, event -> batchInput.setText(""));
        panel.add(buttonRow(FlowLayout.LEFT, addCurrentFenButton, clearBatchInputButton), BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Creates the batch-runner controls.
     *
     * @return runner panel
     */
    private JComponent createRunnerPanel() {
        JPanel controls = new SurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        styleCombos(batchTaskCombo);
        batchTaskCombo.setPrototypeDisplayValue(new BatchTask("Analyze batch", BatchInputKind.FEN_LINES,
                new WorkflowControls(true, false, true, true), (input, ctx) -> List.of()));
        batchTaskCombo.addActionListener(event -> {
            updateControls();
            updateCommand();
        });
        grid(controls, Theme.section("Batch"), c, 0, 0, 4, 1);

        JPanel workflow = transparentPanel(new GridBagLayout());
        GridBagConstraints workflowC = constraints();
        grid(workflow, label("task"), workflowC, 0, 0, 1, 1);
        grid(workflow, batchTaskCombo, workflowC, 1, 0, 3, 1);
        batchDurationField.setColumns(8);
        styleFields(batchDurationField);
        styleAreas(batchCommandField);
        batchDurationField.getDocument().addDocumentListener(changeListener(() -> {
            if (!settingDuration) {
                host.syncBatchDuration(batchDurationField.getText());
            }
        }));
        styleSpinners(batchDepthSpinner, batchMultipvSpinner, batchThreadsSpinner);
        grid(workflow, batchOptionsPanel, workflowC, 1, 1, 3, 1);

        JPanel commandPreview = transparentPanel(new GridBagLayout());
        GridBagConstraints commandC = constraints();
        grid(commandPreview, label("command"), commandC, 0, 0, 1, 1);
        batchCommandField.setLineWrap(true);
        batchCommandField.setWrapStyleWord(false);
        batchCommandField.setEditable(false);
        batchCommandField.setFocusable(false);
        batchCommandField.setToolTipText("Generated batch command");
        batchCommandField.setMinimumSize(new Dimension(220, 64));
        grid(commandPreview, batchCommandField, commandC, 1, 0, 3, 1);

        grid(controls, collapsible("Workflow", workflow, true), c, 0, 1, 4, 1);
        grid(controls, collapsible("Command", commandPreview, false), c, 0, 2, 4, 1);
        grid(controls, buttonRow(FlowLayout.LEFT,
                button("Run", true, event -> runBatch()),
                button("Copy", false, event -> host.copyText(batchCommandField.getText()))), c, 0, 3, 4, 1);
        addVerticalFiller(controls, c, 4, 4);
        return controls;
    }

    /**
     * Installs batch tasks.
     */
    private void installBatchTasks() {
        batchTaskCombo.setModel(CommandTemplates.batchModel());
    }

    /**
     * Updates batch-only controls for the selected task.
     */
    private void updateControls() {
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        if (task == null) {
            return;
        }
        batchInput.setEditable(task.usesTextInput());
        if (addCurrentFenButton != null) {
            addCurrentFenButton.setEnabled(task.usesFenInput());
        }
        if (clearBatchInputButton != null) {
            clearBatchInputButton.setEnabled(task.usesTextInput());
        }
        updateInputPlaceholder(task);
        rebuildOptionsPanel(task.controls());
        refreshInputStatus();
    }

    /**
     * Updates the input area's helper text for the selected task.
     *
     * @param task selected batch task
     */
    private void updateInputPlaceholder(BatchTask task) {
        if (task.usesCommandInput()) {
            String text = "one command per line";
            placeholder(batchInput, text);
            batchInput.setToolTipText("One CRTK command per line; leading 'crtk' is optional.");
        } else if (task.usesFenInput()) {
            String text = "one FEN per line";
            placeholder(batchInput, text);
            batchInput.setToolTipText("One FEN per line.");
        } else {
            String text = "input not used";
            placeholder(batchInput, text);
            batchInput.setToolTipText("This task runs without text input.");
        }
    }

    /**
     * Rebuilds the workflow option panel with only the controls that matter.
     *
     * @param controls enabled workflow controls
     */
    private void rebuildOptionsPanel(WorkflowControls controls) {
        batchOptionsPanel.removeAll();
        if (controls.duration()) {
            batchOptionsPanel.add(optionGroup("duration", batchDurationField));
        }
        if (controls.depth()) {
            batchOptionsPanel.add(optionGroup("depth", batchDepthSpinner));
        }
        if (controls.multipv()) {
            batchOptionsPanel.add(optionGroup("multipv", batchMultipvSpinner));
        }
        if (controls.threads()) {
            batchOptionsPanel.add(optionGroup("threads", batchThreadsSpinner));
        }
        batchOptionsPanel.revalidate();
        batchOptionsPanel.repaint();
    }

    /**
     * Queues a lightweight refresh of the batch FEN status line.
     */
    private void requestInputStatusUpdate() {
        if (batchInputStatusUpdateQueued) {
            return;
        }
        batchInputStatusUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            if (!batchInputStatusUpdateQueued) {
                return;
            }
            refreshInputStatus();
        });
    }

    /**
     * Updates the batch FEN status line.
     */
    private void refreshInputStatus() {
        batchInputStatusUpdateQueued = false;
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        String taskName = task == null ? "none" : task.name();
        if (task != null && task.usesCommandInput()) {
            refreshCommandInputStatus(taskName);
            host.updatePublishCommand();
            return;
        }
        if (task != null && !task.usesTextInput()) {
            batchInputStatus.setText("Input not used");
            batchInputStatus.setToolTipText("No input needed.");
            Theme.foreground(batchInputStatus, Theme.ForegroundRole.MUTED);
            host.updateBatchSummary(taskName + " · no input required");
            host.updatePublishCommand();
            return;
        }
        FenInput.Summary scan = FenInput.validateBatchFenInput(batchInput.getText());
        if (scan.rows() == 0) {
            batchInputStatus.setText("No FEN rows");
            batchInputStatus.setToolTipText("Add one FEN per line.");
            Theme.foreground(batchInputStatus, Theme.ForegroundRole.MUTED);
            host.updateBatchSummary(taskName + " · no FEN rows");
        } else if (scan.hasError()) {
            batchInputStatus.setText(scan.rows() + " row" + (scan.rows() == 1 ? "" : "s")
                    + ", issue on line " + scan.firstErrorLine());
            batchInputStatus.setToolTipText(scan.firstError());
            Theme.foreground(batchInputStatus, Theme.ForegroundRole.WARNING);
            host.updateBatchSummary(taskName + " · " + scan.rows() + " rows, issue on line "
                    + scan.firstErrorLine());
        } else {
            batchInputStatus.setText(scan.validRows() + " FEN row" + (scan.validRows() == 1 ? "" : "s"));
            batchInputStatus.setToolTipText("Ready to run batch workflow.");
            Theme.foreground(batchInputStatus, Theme.ForegroundRole.MUTED);
            host.updateBatchSummary(taskName + " · " + scan.validRows() + " FEN row"
                    + (scan.validRows() == 1 ? "" : "s") + " ready");
        }
        host.updatePublishCommand();
    }

    /**
     * Updates status text for command-script input.
     *
     * @param taskName selected task name
     */
    private void refreshCommandInputStatus(String taskName) {
        CommandScriptSummary scan = scanCommandScript();
        if (scan.commands() == 0) {
            batchInputStatus.setText("No commands");
            batchInputStatus.setToolTipText("Add one CRTK command per line.");
            Theme.foreground(batchInputStatus, Theme.ForegroundRole.MUTED);
            host.updateBatchSummary(taskName + " · no commands");
        } else if (scan.hasError()) {
            batchInputStatus.setText(scan.commands() + " command"
                    + (scan.commands() == 1 ? "" : "s") + ", issue on line "
                    + scan.firstErrorLine());
            batchInputStatus.setToolTipText(scan.firstError());
            Theme.foreground(batchInputStatus, Theme.ForegroundRole.WARNING);
            host.updateBatchSummary(taskName + " · issue on line " + scan.firstErrorLine());
        } else {
            batchInputStatus.setText(scan.commands() + " command"
                    + (scan.commands() == 1 ? "" : "s"));
            batchInputStatus.setToolTipText("Ready to run command script.");
            Theme.foreground(batchInputStatus, Theme.ForegroundRole.MUTED);
            host.updateBatchSummary(taskName + " · " + scan.commands() + " command"
                    + (scan.commands() == 1 ? "" : "s") + " ready");
        }
    }

    /**
     * Scans the batch input as command-script rows.
     *
     * @return script summary
     */
    private CommandScriptSummary scanCommandScript() {
        String[] lines = batchInput.getText().split("\\R", -1);
        int commands = 0;
        for (int i = 0; i < lines.length; i++) {
            String row = lines[i].trim();
            if (row.isEmpty() || row.startsWith("#")) {
                continue;
            }
            try {
                List<String> tokens = CommandLine.split(row);
                int commandIndex = !tokens.isEmpty() && "crtk".equals(tokens.get(0)) ? 1 : 0;
                if (commandIndex >= tokens.size() || tokens.get(commandIndex).isBlank()) {
                    return new CommandScriptSummary(commands, i + 1, "missing command after crtk");
                }
                commands++;
            } catch (IllegalArgumentException ex) {
                return new CommandScriptSummary(commands, i + 1, ex.getMessage());
            }
        }
        return new CommandScriptSummary(commands, 0, "");
    }

    /**
     * Summary of command-script input validation.
     *
     * @param commands non-comment command rows
     * @param firstErrorLine first invalid line, or zero
     * @param firstError first validation error
     */
    private record CommandScriptSummary(int commands, int firstErrorLine, String firstError) {

        /**
         * Returns whether validation found an error.
         *
         * @return true when an error exists
         */
        boolean hasError() {
            return firstErrorLine > 0;
        }
    }
}
