package application.gui.workbench.window;

import application.Config;
import application.cli.PathOps;
import application.gui.workbench.Defaults;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.command.CommandTemplates;
import application.gui.workbench.command.Console;
import application.gui.workbench.game.EngineEval;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.session.LogPanel;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WorkspaceHeader;
import application.gui.workbench.ui.WrappingFlowLayout;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Setup;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import static application.gui.workbench.ui.SwingTasks.runAsync;
import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.collapsible;
import static application.gui.workbench.ui.Ui.controlRow;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleCombo;
import static application.gui.workbench.ui.Ui.transparentPanel;
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
        refreshRunStateControls(state);
        refreshConsoleHeaders(state);
        setStatusBarEngine(state == null || state.isBlank() ? "idle" : state);
        refreshAnalysisCommandState();
    }

    /**
     * Creates the Run surface: the single command builder (Build). Batch
     * workflows are now commands inside this builder, and the Console and Logs
     * are first-class top-level surfaces, so the Run tab no longer needs a
     * mode switcher.
     *
     * @return run workspace component
     */
    @Override
    protected JComponent createRunWorkspaceTab() {
        return createCommandTab();
    }

    /**
     * Creates the command tab.
     *
     * @return tab
     */
    @Override
    protected JComponent createCommandTab() {
        installTemplates();
        runCommandButton = button("Run", true, event -> runSelectedTemplate());
        JPanel panel = transparentPanel(new BorderLayout(0, 0));
        runHeader = new WorkspaceHeader("Run", "", createCommandActions());
        panel.add(runHeader, BorderLayout.NORTH);
        panel.add(createCommandBuilder(), BorderLayout.CENTER);
        refreshRunHeader();
        return panel;
    }

    /**
     * Creates command builder.
     *
     * @return panel
     */
    protected JPanel createCommandBuilder() {
        JPanel panel = transparentPanel(new BorderLayout(0, 0));
        commandForm.setChangeListener(this::updateBuiltCommand);
        commandForm.setRunGate(this::updateCommandRunGate);
        commandForm.setPositionContext(new application.gui.workbench.command.PositionListEditor.Context() {
            /**
             * Returns the current board FEN.
             *
             * @return current FEN
             */
            @Override
            public String currentFen() {
                return WindowCommandLayer.this.currentFen();
            }

            /**
             * Shows a command-builder error dialog.
             *
             * @param title dialog title
             * @param message dialog message
             */
            @Override
            public void showError(String title, String message) {
                WindowCommandLayer.this.showError(title, message);
            }

            /**
             * Returns the parent component for modal dialogs.
             *
             * @return dialog parent component
             */
            @Override
            public java.awt.Component dialogParent() {
                return WindowCommandLayer.this;
            }
        });
        styleCommandPreviewField(commandField);
        depthModel.addChangeListener(event -> requestCommandPreviews());
        multipvModel.addChangeListener(event -> requestCommandPreviews());
        threadsModel.addChangeListener(event -> requestCommandPreviews());

        commandField.setEditable(false);
        commandField.setToolTipText("Generated command");
        configureRunOutputPanes();

        JSplitPane split = SplitPaneStyler.styledHorizontalSplit(
                createRunSettingsColumn(), createRunOutputColumn(), 0.47);
        split.setMinimumSize(new Dimension(880, 560));
        panel.add(split, BorderLayout.CENTER);

        updateCommandOptions();
        updateBuiltCommand();
        setCommandState("Idle");
        return panel;
    }

    /**
     * Creates the command-settings column.
     *
     * @return scrollable settings column
     */
    private JComponent createRunSettingsColumn() {
        JPanel column = verticalBody();
        column.setBorder(Theme.pad(Theme.SPACE_MD));
        column.add(createRunCommandSelectorCard());
        column.add(Box.createVerticalStrut(Theme.SPACE_MD));
        commandForm.setAlignmentX(LEFT_ALIGNMENT);
        column.add(Ui.card("Command Settings", commandForm));
        column.add(Box.createVerticalGlue());
        JScrollPane pane = scroll(fillViewport(column));
        pane.setMinimumSize(new Dimension(380, 520));
        pane.setPreferredSize(new Dimension(470, 640));
        return pane;
    }

    /**
     * Creates the command selector card.
     *
     * @return selector card
     */
    private JComponent createRunCommandSelectorCard() {
        JPanel body = verticalBody();
        commandPicker.setAlignmentX(LEFT_ALIGNMENT);
        commandPathLabel.setAlignmentX(LEFT_ALIGNMENT);
        commandDescriptionLabel.setAlignmentX(LEFT_ALIGNMENT);
        commandPathLabel.setFont(Theme.mono(Theme.FONT_MONO));
        commandDescriptionLabel.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        commandDescriptionLabel.putClientProperty(Theme.CLIENT_TRANSPARENT_FIELD, Boolean.TRUE);
        commandDescriptionLabel.setOpaque(false);
        commandDescriptionLabel.setEditable(false);
        commandDescriptionLabel.setLineWrap(true);
        commandDescriptionLabel.setWrapStyleWord(true);
        commandDescriptionLabel.setBorder(BorderFactory.createEmptyBorder());
        Theme.foreground(commandPathLabel, Theme.ForegroundRole.MUTED);
        Theme.foreground(commandDescriptionLabel, Theme.ForegroundRole.TEXT);
        body.add(commandPicker);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(commandPathLabel);
        body.add(Box.createVerticalStrut(Theme.SPACE_XS));
        body.add(commandDescriptionLabel);
        return Ui.card("Command", body);
    }

    /**
     * Creates the preview/output column.
     *
     * @return scrollable output column
     */
    private JComponent createRunOutputColumn() {
        JPanel column = verticalBody();
        column.setBorder(Theme.pad(Theme.SPACE_MD));
        column.add(createRunPreviewCard());
        column.add(Box.createVerticalStrut(Theme.SPACE_MD));
        column.add(createRunValidationCard());
        column.add(Box.createVerticalStrut(Theme.SPACE_MD));
        column.add(createRunParsedResultCard());
        column.add(Box.createVerticalStrut(Theme.SPACE_MD));
        column.add(createRunRawOutputCard());
        column.add(Box.createVerticalStrut(Theme.SPACE_MD));
        column.add(createRecentCommandsCard());
        column.add(Box.createVerticalGlue());
        JScrollPane pane = scroll(fillViewport(column));
        pane.setMinimumSize(new Dimension(420, 520));
        pane.setPreferredSize(new Dimension(560, 640));
        return pane;
    }

    /**
     * Creates the generated command preview card.
     *
     * @return preview card
     */
    private JComponent createRunPreviewCard() {
        JPanel body = verticalBody();
        commandPreviewScroll = scroll(commandField, () -> Theme.CODE_BLOCK_BG);
        commandPreviewScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        commandPreviewScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        commandPreviewScroll.setPreferredSize(new Dimension(520, 132));
        commandPreviewScroll.setMinimumSize(new Dimension(320, 110));
        body.add(commandPreviewScroll);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));

        JCheckBox wrapToggle = new ToggleBox("Wrap", true);
        wrapToggle.setSelected(true);
        wrapToggle.setToolTipText("Wrap long generated commands inside the preview");
        wrapToggle.addActionListener(event -> setCommandPreviewWrap(wrapToggle.isSelected()));

        JPanel actions = transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        actions.add(button("Copy Command", false, event -> copyBuiltCommand()));
        actions.add(button("Open Console", false, event -> showConsoleDock()));
        actions.add(wrapToggle);
        body.add(actions);
        return Ui.card("Command Preview", body);
    }

    /**
     * Creates the validation badge card.
     *
     * @return validation card
     */
    private JComponent createRunValidationCard() {
        JPanel body = verticalBody();
        body.add(validationRow("Position source", runFenBadge));
        body.add(Box.createVerticalStrut(Theme.SPACE_XS));
        body.add(validationRow("Engine config", runProtocolBadge));
        body.add(Box.createVerticalStrut(Theme.SPACE_XS));
        body.add(validationRow("Search duration", runDurationBadge));
        return Ui.card("Validation", body);
    }

    /**
     * Creates one validation row.
     *
     * @param title row title
     * @param badge status badge
     * @return row
     */
    private static JComponent validationRow(String title, StatusBadge badge) {
        JPanel row = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        JLabel label = new JLabel(title);
        label.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        row.add(label, BorderLayout.WEST);
        row.add(badge, BorderLayout.EAST);
        row.setBorder(Theme.pad(Theme.SPACE_XS, 0, Theme.SPACE_XS, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    /**
     * Creates the parsed output card.
     *
     * @return parsed-result card
     */
    private JComponent createRunParsedResultCard() {
        JScrollPane pane = scroll(runParsedOutput, () -> Theme.TEXT_AREA);
        pane.setPreferredSize(new Dimension(520, 150));
        pane.setMinimumSize(new Dimension(320, 120));
        return Ui.card("Parsed Result", runStateBadge, pane);
    }

    /**
     * Creates the raw command-output card.
     *
     * @return raw-output card
     */
    private JComponent createRunRawOutputCard() {
        JScrollPane pane = scroll(runRawOutput, () -> Theme.TERMINAL);
        pane.setPreferredSize(new Dimension(520, 220));
        pane.setMinimumSize(new Dimension(320, 160));
        // Raw log is secondary to the parsed result: collapse it by default so it
        // no longer dominates the column with a large empty terminal box. A run
        // re-expands it (see prepareRunCommandOutput) so live output stays visible.
        runRawOutputSection = collapsible("Raw Output / Log", pane, false);
        return runRawOutputSection;
    }

    /**
     * Creates the recent-command history card.
     *
     * @return recent command card
     */
    private JComponent createRecentCommandsCard() {
        JPanel body = verticalBody();
        Theme.list(recentCommandList);
        recentCommandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentCommandList.setVisibleRowCount(5);
        JScrollPane listScroll = scroll(recentCommandList, () -> Theme.ELEVATED_SOLID);
        listScroll.setPreferredSize(new Dimension(520, 112));
        body.add(listScroll);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        JButton copySelected = button("Copy Selected", false, event -> copySelectedRecentCommand());
        body.add(buttonRow(FlowLayout.LEFT, copySelected));
        // History is a convenience; keep it collapsed until the user wants it so
        // an empty list does not read as a dead panel.
        return collapsible("Recent Commands", body, false);
    }

    /**
     * Configures the parsed/raw output panes.
     */
    private void configureRunOutputPanes() {
        styleAreas(runParsedOutput);
        runParsedOutput.setEditable(false);
        runParsedOutput.setLineWrap(true);
        runParsedOutput.setWrapStyleWord(true);
        runParsedOutput.setRows(7);
        runParsedOutput.setText("No command run yet.\nRun a command to see parsed fields here.");
        runParsedOutput.setCaretPosition(0);
        runRawOutput.setPlaceholder("Raw command output appears here after running.");
        runStateBadge.notRun("idle");
    }

    /**
     * Copies the selected recent command to the clipboard.
     */
    private void copySelectedRecentCommand() {
        String selected = recentCommandList.getSelectedValue();
        if (selected == null || selected.isBlank()) {
            showWarning("Recent commands", "Select a command to copy.");
            return;
        }
        copyText(selected);
    }

    /**
     * Toggles command preview wrapping.
     *
     * @param wrap true to wrap
     */
    private void setCommandPreviewWrap(boolean wrap) {
        commandField.setLineWrap(wrap);
        commandField.setWrapStyleWord(false);
        if (commandPreviewScroll != null) {
            commandPreviewScroll.setHorizontalScrollBarPolicy(wrap
                    ? JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                    : JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        }
        commandField.revalidate();
    }

    /**
     * Creates the Run header actions.
     *
     * @return action row
     */
    private JComponent createCommandActions() {
        if (runCommandButton == null) {
            runCommandButton = button("Run", true, event -> runSelectedTemplate());
        }
        runStopButton = new HoldButton("Stop", this::stopCommand, true);
        runStopButton.setVisible(false);
        return controlRow(FlowLayout.RIGHT,
                runCommandButton,
                button("Copy Command", false, event -> copyBuiltCommand()),
                button("Reset", false, event -> resetSelectedTemplate()),
                button("Clear Flags", false, event -> clearOptionalTemplateOptions()),
                runStopButton);
    }

    /**
     * Styles the generated command field so it reads as command output rather
     * than another input row: monospace text, no editable border, muted
     * foreground, transparent background that blends with the wrapper panel.
     *
     * @param field generated-command field
     */
    private static void styleCommandPreviewField(JTextArea field) {
        Theme.codeBlock(field);
        field.setEditable(false);
        field.setLineWrap(true);
        field.setWrapStyleWord(false);
        field.setRows(6);
        field.setColumns(72);
    }

    /**
     * Enables or disables the Run button from the command form's validity.
     *
     * @param ready true when the built command is runnable
     */
    protected void updateCommandRunGate(boolean ready) {
        commandFormRunnable = ready;
        boolean running = runningCommand != null && runningCommand.isRunning();
        if (runCommandButton != null) {
            runCommandButton.setEnabled(ready && !running);
            runCommandButton.setToolTipText(ready ? null
                    : "Fix the highlighted fields before running");
        }
    }

    /**
     * Saves the given console's visible text to a user-chosen log file.
     *
     * @param target console whose text to save
     */
    protected void saveConsoleLog(Console target) {
        JFileChooser chooser = FileDialogs.createFileChooser(null, PathOps.dumpPath("workbench-console.log").toFile(),
                new FileNameExtensionFilter("Log files", "log", "txt"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = FileDialogs.ensureExtension(chooser.getSelectedFile(), ".log");
        String contents = target.getText();
        runAsync(
                () -> {
                    Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);
                    return file.toPath().toAbsolutePath().normalize();
                },
                saved -> {
                    session.artifacts().add(saved);
                    appendConsole("Saved console log to " + saved + "\n");
                    toast(Toast.Kind.SUCCESS, "Saved log to " + saved.getFileName());
                },
                ex -> showError("Save log failed", ex.getMessage()));
    }

    /**
     * Creates the primary console surface (uses the shared console instance and
     * the shared run-state label).
     *
     * @return panel
     */
    @Override
    protected JComponent createConsolePanel() {
        return createConsolePanelInstance(true);
    }

    /**
     * Creates an independent duplicate console surface with its own console
     * instance, wired into the output fan-out via {@link #consoles}.
     *
     * @return duplicate console panel
     */
    @Override
    protected JComponent createDetachedConsolePanel() {
        return createConsolePanelInstance(false);
    }

    /**
     * Creates the persisted-log tab.
     *
     * @return log tab component
     */
    @Override
    protected JComponent createLogTab() {
        return primaryLogPanel();
    }

    /**
     * Creates an independent duplicate logs surface.
     *
     * @return duplicate logs component
     */
    @Override
    protected JComponent createDetachedLogTab() {
        return createLogPanelInstance(false);
    }

    /**
     * Builds a console surface. The primary surface reuses the shared
     * {@code console} and {@code commandStateLabel}; a duplicate builds a fresh
     * console (registered in {@link #consoles}) and its own state label so a
     * Swing component is never parented twice.
     *
     * @param primary true for the canonical console surface
     * @return console panel
     */
    private JComponent createConsolePanelInstance(boolean primary) {
        Console target = primary ? console : new Console();
        if (!primary) {
            consoles.add(target);
        }
        JPanel panel = new SurfacePanel(new BorderLayout(6, 6));
        WorkspaceHeader header = new WorkspaceHeader("Console",
                "Command output · " + commandStateLabel.getText(),
                controlRow(FlowLayout.RIGHT,
                button("Open Logs", false, event -> showLogsDock()),
                button("Save Log", false, event -> saveConsoleLog(target)),
                button("Empty Log", false, event -> target.clearOutput()),
                createCommandStopButton()));
        consoleHeaders.add(header);
        panel.add(header, BorderLayout.NORTH);
        target.setPlaceholder("Run a command to see its output here.");
        // The shared console is constructed eagerly (a WindowBase field) before
        // the theme is applied; a duplicate is built fresh. Either way, apply the
        // current theme now that it joins the tree so it is never light-on-dark.
        target.applyConsoleTheme();
        panel.add(scroll(target), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Returns the lazily-created persisted-log browser.
     *
     * @return log panel
     */
    protected LogPanel primaryLogPanel() {
        if (logPanel == null) {
            logPanel = createLogPanelInstance(true);
        }
        return logPanel;
    }

    /**
     * Creates and registers a log browser instance.
     *
     * @param primary true when this is the canonical Logs tab
     * @return log panel
     */
    private LogPanel createLogPanelInstance(boolean primary) {
        if (primary && logPanel != null) {
            return logPanel;
        }
        LogPanel panel = new LogPanel(this::copyText);
        logPanels.add(panel);
        if (primary) {
            logPanel = panel;
        }
        return panel;
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
     * Opens Run and selects a named command template.
     *
     * @param name command-template display name
     */
    protected void openCommandTemplate(String name) {
        openRun(RUN_BUILD);
        if (commandTemplates.isEmpty()) {
            installTemplates();
        }
        for (int i = 0; i < commandTemplates.size(); i++) {
            if (name.equals(commandTemplates.get(i).name())) {
                selectCommandTemplate(i);
                return;
            }
        }
        showWarning("Command template", "No command template named " + name + ".");
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
        refreshRunBuilderDetails();
        refreshRunValidation();
        refreshRunHeader();
    }

    /**
     * Refreshes the Run workspace header after command-template or context
     * changes.
     */
    @Override
    protected void refreshRunHeader() {
        if (runHeader == null) {
            return;
        }
        CommandTemplate template = selectedCommandTemplate();
        runHeader.setTitle("Run" + (template == null ? "" : " / " + template.name()));
        runHeader.setContext(runContextSummary(template));
    }

    /**
     * Returns the Run shell context line.
     *
     * @param template selected command template, or null
     * @return context summary
     */
    private String runContextSummary(CommandTemplate template) {
        String position = currentPosition == null ? "Default FEN" : "Current FEN";
        String duration = "max duration " + durationValue();
        String command = template == null ? "No command selected" : template.name();
        return position + " · " + duration + " · " + command;
    }

    /**
     * Refreshes selected-command description labels.
     */
    private void refreshRunBuilderDetails() {
        CommandTemplate template = selectedCommandTemplate();
        if (template == null) {
            commandPathLabel.setText("crtk");
            commandDescriptionLabel.setText("Select a command template to build a reproducible CRTK invocation.");
            return;
        }
        commandPathLabel.setText("crtk " + String.join(" ", template.baseArgs()));
        commandDescriptionLabel.setText(commandDescription(template));
    }

    /**
     * Returns a concise description for the selected command.
     *
     * @param template command template
     * @return description text
     */
    private static String commandDescription(CommandTemplate template) {
        return switch (template.name()) {
            case "Best move" -> "Finds the best move for the selected position using the configured engine limits.";
            case "Legal moves" -> "Lists legal moves for the selected position in UCI, SAN, or both formats.";
            case "Analyze" -> "Runs engine analysis and returns evaluation, search depth, and candidate lines.";
            case "Built-in search" -> "Runs the deterministic built-in search stack with local evaluator settings.";
            case "Perft" -> "Counts legal move-tree nodes for deterministic move-generation validation.";
            case "Tags" -> "Extracts position tags and optional engine-enriched annotations from FEN or PGN input.";
            case "Review game" -> "Reviews a PGN with bounded engine settings and can emit study-unit artifacts.";
            case "PGN import" -> "Imports PGN games into the local append-safe PGN store.";
            case "PGN find" -> "Finds stored PGN games that pass through the selected position.";
            case "PGN show" -> "Shows one stored PGN game by game id.";
            case "PGN stats" -> "Summarizes the local PGN store.";
            case "Mate" -> "Searches for bounded mate proofs from the selected position.";
            case "Eval" -> "Evaluates the selected position with the chosen local evaluator.";
            case "Threats" -> "Reports tactical threats in the selected position.";
            case "Apply move" -> "Applies one move to a position and prints the resulting position data.";
            case "Position diff" -> "Compares two positions and reports structural differences.";
            case "Position describe" -> "Generates deterministic text or structured descriptors for positions.";
            default -> "Builds a reproducible CRTK command from the selected template and options.";
        };
    }

    /**
     * Refreshes command-builder validation badges.
     */
    private void refreshRunValidation() {
        List<String> args;
        try {
            args = selectedTemplateArgs();
        } catch (IllegalArgumentException ex) {
            runFenBadge.error("invalid");
            runFenBadge.setToolTipText(ex.getMessage());
            runProtocolBadge.notRun("not checked");
            runDurationBadge.notRun("not checked");
            return;
        }
        refreshFenValidation(args);
        refreshProtocolValidation(args);
        refreshDurationValidation(args);
    }

    /**
     * Refreshes position-source validation.
     *
     * @param args preview args
     */
    private void refreshFenValidation(List<String> args) {
        if (args.contains("--startpos")) {
            runFenBadge.ready("startpos");
            runFenBadge.setToolTipText("Standard chess start position");
            return;
        }
        if (args.contains("--randompos")) {
            runFenBadge.stale("randompos");
            runFenBadge.setToolTipText("The CLI will generate a random legal position when run");
            return;
        }
        String input = valueAfter(args, "--input");
        if (!input.isBlank()) {
            runFenBadge.ready("input source");
            runFenBadge.setToolTipText(input);
            return;
        }
        String fen = valueAfter(args, "--fen");
        if (fen.isBlank()) {
            runFenBadge.notRun("not required");
            runFenBadge.setToolTipText(null);
            return;
        }
        try {
            new chess.core.Position(fen);
            runFenBadge.ready("FEN valid");
            runFenBadge.setToolTipText(fen);
        } catch (IllegalArgumentException ex) {
            runFenBadge.error("FEN invalid");
            runFenBadge.setToolTipText(ex.getMessage());
        }
    }

    /**
     * Refreshes protocol/config path validation.
     *
     * @param args preview args
     */
    private void refreshProtocolValidation(List<String> args) {
        CommandTemplate template = selectedCommandTemplate();
        if (template == null || !usesProtocol(template, args)) {
            runProtocolBadge.notRun("not used");
            runProtocolBadge.setToolTipText("Selected command does not use an external engine protocol");
            return;
        }
        String explicit = valueAfter(args, "--protocol-path");
        String path = explicit.isBlank() ? engineProtocolValue() : explicit;
        if (path.isBlank()) {
            path = Config.getProtocolPath();
        }
        if (path.isBlank()) {
            runProtocolBadge.missing("config missing");
            runProtocolBadge.setToolTipText("No protocol path configured");
            return;
        }
        Path protocol = Path.of(path);
        runProtocolBadge.setToolTipText(path);
        if (Files.isRegularFile(protocol)) {
            runProtocolBadge.ready("config found");
        } else {
            runProtocolBadge.warning("config missing");
        }
    }

    /**
     * Refreshes max-duration validation.
     *
     * @param args preview args
     */
    private void refreshDurationValidation(List<String> args) {
        String duration = valueAfter(args, "--max-duration");
        if (duration.isBlank()) {
            runDurationBadge.notRun("not used");
            runDurationBadge.setToolTipText("Selected command has no duration limit enabled");
            return;
        }
        if (duration.matches("[0-9]+(ms|s|m|h)?")) {
            runDurationBadge.ready(duration);
            runDurationBadge.setToolTipText("Valid duration token");
        } else {
            runDurationBadge.error("invalid");
            runDurationBadge.setToolTipText("Use e.g. 500ms, 60s, 2m, 1h, or plain milliseconds");
        }
    }

    /**
     * Returns whether the command can use an external engine protocol.
     *
     * @param template command template
     * @param args preview args
     * @return true when protocol validation is relevant
     */
    private static boolean usesProtocol(CommandTemplate template, List<String> args) {
        if (args.contains("--protocol-path")) {
            return true;
        }
        return template.options().stream().anyMatch(option -> "--protocol-path".equals(option.flag()));
    }

    /**
     * Returns a flag value from args.
     *
     * @param args command args
     * @param flag flag
     * @return value after flag, or blank
     */
    private static String valueAfter(List<String> args, String flag) {
        if (args == null || flag == null) {
            return "";
        }
        for (int i = 0; i < args.size() - 1; i++) {
            if (flag.equals(args.get(i))) {
                return args.get(i + 1);
            }
        }
        return "";
    }

    /**
     * Refreshes Run header buttons and status badge.
     *
     * @param state latest command state
     */
    private void refreshRunStateControls(String state) {
        String value = state == null ? "" : state.trim();
        boolean running = "Running".equalsIgnoreCase(value)
                || runningCommand != null && runningCommand.isRunning();
        if (running) {
            runStateBadge.running("running");
        } else if (value.startsWith("Exit 0")) {
            runStateBadge.complete("complete");
        } else if (value.startsWith("Exit")) {
            runStateBadge.error(value.toLowerCase(Locale.ROOT));
        } else if ("Stopped".equalsIgnoreCase(value)) {
            runStateBadge.paused("stopped");
        } else {
            runStateBadge.notRun(value.isBlank() ? "idle" : value.toLowerCase(Locale.ROOT));
        }
        if (runStopButton != null) {
            runStopButton.setVisible(running);
            runStopButton.setEnabled(running);
        }
        for (JComponent button : commandStopButtons) {
            button.setVisible(running);
            button.setEnabled(running);
            if (button.getParent() != null) {
                button.getParent().revalidate();
                button.getParent().repaint();
            }
        }
        if (runCommandButton != null) {
            runCommandButton.setEnabled(commandFormRunnable && !running);
            runCommandButton.setToolTipText(commandFormRunnable ? null
                    : "Fix the highlighted fields before running");
        }
    }

    /**
     * Refreshes materialized console headers from command-run state.
     *
     * @param state latest command state
     */
    private void refreshConsoleHeaders(String state) {
        String value = state == null || state.isBlank() ? "Idle" : state;
        for (application.gui.workbench.ui.WorkspaceHeader header : consoleHeaders) {
            header.setContext("Command output · " + value);
        }
    }

    /**
     * Chooses a compact but useful preview height for a multiline command.
     *
     * @param text preview text
     * @return row count
     */
    private static int previewRows(String text) {
        if (text == null || text.isBlank()) {
            return 6;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return Math.max(6, Math.min(10, lines));
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
