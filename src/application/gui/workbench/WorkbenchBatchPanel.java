package application.gui.workbench;

import static application.gui.workbench.WorkbenchUi.addVerticalFiller;
import static application.gui.workbench.WorkbenchUi.button;
import static application.gui.workbench.WorkbenchUi.buttonRow;
import static application.gui.workbench.WorkbenchUi.changeListener;
import static application.gui.workbench.WorkbenchUi.constraints;
import static application.gui.workbench.WorkbenchUi.fillViewport;
import static application.gui.workbench.WorkbenchUi.grid;
import static application.gui.workbench.WorkbenchUi.label;
import static application.gui.workbench.WorkbenchUi.optionGroup;
import static application.gui.workbench.WorkbenchUi.placeholder;
import static application.gui.workbench.WorkbenchUi.scroll;
import static application.gui.workbench.WorkbenchUi.styleAreas;
import static application.gui.workbench.WorkbenchUi.styleCombos;
import static application.gui.workbench.WorkbenchUi.styleFields;
import static application.gui.workbench.WorkbenchUi.styleSpinners;
import static application.gui.workbench.WorkbenchUi.transparentPanel;

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
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import application.gui.workbench.WorkbenchCommandTemplates.BatchTask;
import application.gui.workbench.WorkbenchCommandTemplates.TemplateContext;
import application.gui.workbench.WorkbenchCommandTemplates.WorkflowControls;
import application.gui.workbench.layout.SplitPaneStyler;
import chess.core.Setup;

/**
 * Batch workflow tab: FEN input, task-specific controls, command preview, and
 * run handling.
 */
final class WorkbenchBatchPanel {

    /**
     * Placeholder path used in command previews for generated FEN files.
     */
    private static final String WORKBENCH_FENS_PLACEHOLDER = "<workbench-fens.txt>";

    /**
     * Cached preview-only path used when building batch commands without
     * materializing the FENs file.
     */
    private static final Path WORKBENCH_FENS_PLACEHOLDER_PATH = Path.of(WORKBENCH_FENS_PLACEHOLDER);

    /**
     * Services supplied by the frame.
     */
    interface Host {

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
    private final JTextField batchDurationField = new JTextField("2s");

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
    private boolean settingDuration;

    /**
     * Creates the batch panel.
     *
     * @param host host callbacks
     * @param depthModel shared depth model
     * @param multipvModel shared MultiPV model
     * @param threadsModel shared thread-count model
     */
    WorkbenchBatchPanel(Host host, SpinnerNumberModel depthModel, SpinnerNumberModel multipvModel,
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
    JComponent component() {
        return component;
    }

    /**
     * Returns the current batch input text.
     *
     * @return input text
     */
    String inputText() {
        return batchInput.getText();
    }

    /**
     * Sets duration text from the analysis controls without feeding back into
     * the duration synchronizer.
     *
     * @param value duration text
     */
    void setDurationText(String value) {
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
    void appendCurrentFen() {
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
    void runBatch() {
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        if (task == null) {
            return;
        }
        try {
            Path input = null;
            if (task.usesFenInput()) {
                WorkbenchFenInput.Summary validation = WorkbenchFenInput.validateBatchFenInput(batchInput.getText());
                refreshInputStatus();
                if (validation.rows() == 0) {
                    host.showError("Batch input", "Add at least one FEN before running this batch task.");
                    return;
                }
                if (validation.hasError()) {
                    host.showError("Batch input", "Line " + validation.firstErrorLine() + ": "
                            + validation.firstError());
                    return;
                }
                input = Files.createTempFile("crtk-workbench-fens-", ".txt");
                input.toFile().deleteOnExit();
                Files.writeString(input, batchInput.getText(), StandardCharsets.UTF_8);
            }
            host.runCommand(task.build(input, host.templateContext()), null);
        } catch (IOException ex) {
            host.showError("Batch input failed", ex.getMessage());
        }
    }

    /**
     * Updates the generated batch command preview.
     */
    void updateCommand() {
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        if (task == null) {
            return;
        }
        batchCommandField.setText(WorkbenchCommandRunner.displayCommand(
                task.build(WORKBENCH_FENS_PLACEHOLDER_PATH, host.templateContext())));
        batchCommandField.setCaretPosition(0);
    }

    /**
     * Builds the tab UI.
     */
    private void buildUi() {
        installBatchTasks();
        batchInput.setText(Setup.getStandardStartFEN() + System.lineSeparator()
                + "4R1k1/5ppp/8/8/8/8/8/6K1 b - - 0 1" + System.lineSeparator());
        placeholder(batchDurationField, "e.g. 2s or 500ms");
        placeholder(batchInput, "one FEN per line; blank uses the current game line");
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
        JPanel panel = new WorkbenchSurfacePanel(new BorderLayout(8, 8));
        JPanel top = transparentPanel(new BorderLayout(8, 0));
        top.add(WorkbenchTheme.section("FEN Batch"), BorderLayout.WEST);
        batchInputStatus.setForeground(WorkbenchTheme.MUTED);
        batchInputStatus.setFont(WorkbenchTheme.font(12, java.awt.Font.PLAIN));
        batchInputStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        top.add(batchInputStatus, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll(batchInput), BorderLayout.CENTER);
        addCurrentFenButton = button("Add Current FEN", false, event -> appendCurrentFen());
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
        JPanel controls = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        styleCombos(batchTaskCombo);
        batchTaskCombo.setPrototypeDisplayValue(new BatchTask("Analyze batch", true,
                new WorkflowControls(true, false, true, true), (input, ctx) -> List.of()));
        batchTaskCombo.addActionListener(event -> {
            updateControls();
            updateCommand();
        });
        grid(controls, WorkbenchTheme.section("Batch Runner"), c, 0, 0, 4, 1);
        grid(controls, label("task"), c, 0, 1, 1, 1);
        grid(controls, batchTaskCombo, c, 1, 1, 3, 1);
        batchDurationField.setColumns(8);
        styleFields(batchDurationField);
        styleAreas(batchCommandField);
        batchDurationField.getDocument().addDocumentListener(changeListener(() -> {
            if (!settingDuration) {
                host.syncBatchDuration(batchDurationField.getText());
            }
        }));
        styleSpinners(batchDepthSpinner, batchMultipvSpinner, batchThreadsSpinner);
        grid(controls, batchOptionsPanel, c, 1, 2, 3, 1);
        grid(controls, label("command"), c, 0, 3, 1, 1);
        batchCommandField.setLineWrap(true);
        batchCommandField.setWrapStyleWord(false);
        batchCommandField.setEditable(false);
        batchCommandField.setFocusable(false);
        batchCommandField.setToolTipText("Generated batch command");
        batchCommandField.setMinimumSize(new Dimension(220, 64));
        grid(controls, batchCommandField, c, 1, 3, 3, 1);
        grid(controls, buttonRow(FlowLayout.LEFT,
                button("Run Batch", true, event -> runBatch()),
                button("Copy Command", false, event -> host.copyText(batchCommandField.getText()))), c, 1, 4, 3, 1);
        addVerticalFiller(controls, c, 5, 4);
        return controls;
    }

    /**
     * Installs batch tasks.
     */
    private void installBatchTasks() {
        batchTaskCombo.setModel(WorkbenchCommandTemplates.batchModel());
    }

    /**
     * Updates batch-only controls for the selected task.
     */
    private void updateControls() {
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        if (task == null) {
            return;
        }
        batchInput.setEditable(task.usesFenInput());
        if (addCurrentFenButton != null) {
            addCurrentFenButton.setEnabled(task.usesFenInput());
        }
        if (clearBatchInputButton != null) {
            clearBatchInputButton.setEnabled(task.usesFenInput());
        }
        rebuildOptionsPanel(task.controls());
        refreshInputStatus();
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
        if (task != null && !task.usesFenInput()) {
            batchInputStatus.setText("FEN list not used");
            batchInputStatus.setToolTipText("The selected batch task runs without FEN input.");
            batchInputStatus.setForeground(WorkbenchTheme.MUTED);
            host.updateBatchSummary(taskName + " · no FEN input required");
            host.updatePublishCommand();
            return;
        }
        WorkbenchFenInput.Summary scan = WorkbenchFenInput.validateBatchFenInput(batchInput.getText());
        if (scan.rows() == 0) {
            batchInputStatus.setText("No FEN rows");
            batchInputStatus.setToolTipText("Add one FEN per line.");
            batchInputStatus.setForeground(WorkbenchTheme.MUTED);
            host.updateBatchSummary(taskName + " · no FEN rows");
        } else if (scan.hasError()) {
            batchInputStatus.setText(scan.rows() + " row" + (scan.rows() == 1 ? "" : "s")
                    + ", issue on line " + scan.firstErrorLine());
            batchInputStatus.setToolTipText(scan.firstError());
            batchInputStatus.setForeground(WorkbenchTheme.STATUS_WARNING_TEXT);
            host.updateBatchSummary(taskName + " · " + scan.rows() + " rows, issue on line "
                    + scan.firstErrorLine());
        } else {
            batchInputStatus.setText(scan.validRows() + " FEN row" + (scan.validRows() == 1 ? "" : "s"));
            batchInputStatus.setToolTipText("Ready to run batch workflow.");
            batchInputStatus.setForeground(WorkbenchTheme.MUTED);
            host.updateBatchSummary(taskName + " · " + scan.validRows() + " FEN row"
                    + (scan.validRows() == 1 ? "" : "s") + " ready");
        }
        host.updatePublishCommand();
    }
}
