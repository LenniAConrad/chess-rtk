package application.gui.workbench.engine;

import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.command.CommandRunner.RunningCommand;
import application.gui.workbench.ui.CardGrid;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WorkspaceHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JFileChooser;
import javax.swing.JLabel;

import static application.gui.workbench.ui.Ui.card;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.onTextChange;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleCombos;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Workbench launcher for the deterministic {@code crtk engine gauntlet} command.
 *
 * <p>
 * Builds the CRTK gauntlet command from the candidate/baseline configuration and
 * runs it through {@link CommandRunner} like the rest of the workbench, so the
 * preview, execution path, and output capture match every other command screen.
 * </p>
 */
public final class EngineGauntletPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Leading CRTK command path tokens for the gauntlet command.
     */
    private static final List<String> COMMAND_PATH = List.of("engine", "gauntlet");

    /**
     * Clipboard helper.
     */
    private final transient Consumer<String> copyText;

    /**
     * Candidate and baseline search selectors.
     */
    private final JComboBox<String> searchA = combo("alpha-beta", "mcts");
    private final JComboBox<String> searchB = combo("alpha-beta", "mcts");

    /**
     * Candidate and baseline evaluator selectors.
     */
    private final JComboBox<String> evalA = combo("classical", "nnue");
    private final JComboBox<String> evalB = combo("classical", "nnue");

    /**
     * Feature and budget fields.
     */
    private final JTextField featuresA = field("all");
    private final JTextField featuresB = field("none");
    private final JTextField nodes = field("3000");
    private final JTextField openings = field("8");
    private final JTextField seed = field("20260531");
    private final JTextField maxPlies = field("160");
    private final JTextField workers = field("1");
    private final JTextField threadsA = field("1");
    private final JTextField threadsB = field("1");

    /**
     * Command and process output views.
     */
    private final JTextArea commandArea = new JTextArea();
    private final JTextArea outputArea = new JTextArea();

    /**
     * Shared workspace header for this experiment runner.
     */
    private final WorkspaceHeader workspaceHeader = new WorkspaceHeader("Gauntlet", "", null);

    /**
     * Status label retained for regression access and screen-reader text.
     */
    private final JLabel statusLabel = Ui.label("Ready");

    /**
     * Compact run-state badge.
     */
    private final StatusBadge statusBadge = new StatusBadge();

    /**
     * Candidate/baseline validation badges.
     */
    private final StatusBadge candidateBadge = new StatusBadge();
    private final StatusBadge baselineBadge = new StatusBadge();
    private final StatusBadge settingsBadge = new StatusBadge();

    /**
     * Result metric cards.
     */
    private final MetricCard scoreMetric = new MetricCard("Score");
    private final MetricCard wdlMetric = new MetricCard("W / D / L");
    private final MetricCard eloMetric = new MetricCard("Elo");
    private final MetricCard gamesMetric = new MetricCard("Games");
    private final MetricCard errorsMetric = new MetricCard("Crashes / errors");
    private final MetricCard durationMetric = new MetricCard("Duration");

    /**
     * Result charts.
     */
    private final ResultDistributionChart distributionChart = new ResultDistributionChart();
    private final CumulativeScoreChart cumulativeChart = new CumulativeScoreChart();

    /**
     * Raw output collapsible section.
     */
    private JComponent rawSection;

    /**
     * Run and stop controls.
     */
    private final JButton runButton = Ui.button("Run Gauntlet", true, event -> runGauntlet());
    private final HoldButton stopButton = new HoldButton("Stop", this::stopGauntlet, true);
    private final JButton runAgainButton = Ui.button("Run again", false, event -> runGauntlet());
    private final JButton saveResultButton = Ui.button("Save result", false, event -> saveResult());

    /**
     * Active command handle.
     */
    private transient RunningCommand running;

    /**
     * Captured raw output.
     */
    private final StringBuilder outputBuffer = new StringBuilder();

    /**
     * Parsed running W-D-L snapshots.
     */
    private final List<ProgressPoint> progress = new ArrayList<>();

    /**
     * Latest parsed result.
     */
    private ResultSummary lastResult = ResultSummary.empty();

    /**
     * Creates a gauntlet panel.
     *
     * @param copyText clipboard callback
     */
    public EngineGauntletPanel(Consumer<String> copyText) {
        super(new BorderLayout(Theme.SPACE_MD, Theme.SPACE_MD));
        this.copyText = copyText == null ? text -> { } : copyText;
        configure();
    }

    /**
     * Builds the CRTK argument list (without the {@code crtk} launcher token) for
     * the gauntlet command.
     *
     * @param config gauntlet configuration
     * @return CRTK arguments
     */
    public static List<String> buildCommand(GauntletConfig config) {
        Objects.requireNonNull(config, "config");
        List<String> command = new ArrayList<>(COMMAND_PATH);
        addOption(command, "--a", config.featuresA());
        addOption(command, "--b", config.featuresB());
        addOption(command, "--searchA", config.searchA());
        addOption(command, "--searchB", config.searchB());
        addOption(command, "--evalA", config.evalA());
        addOption(command, "--evalB", config.evalB());
        addOption(command, "--nodes", config.nodes());
        addOption(command, "--openings", config.openings());
        addOption(command, "--seed", config.seed());
        addOption(command, "--maxplies", config.maxPlies());
        addOption(command, "--workers", config.workers());
        addOption(command, "--threadsA", config.threadsA());
        addOption(command, "--threadsB", config.threadsB());
        return List.copyOf(command);
    }

    /**
     * Configures the panel.
     */
    private void configure() {
        setOpaque(true);
        setBackground(Theme.BG);
        getAccessibleContext().setAccessibleName("Engine gauntlet panel");
        getAccessibleContext().setAccessibleDescription(
                "Builds and runs deterministic self-play gauntlets for built-in engines.");
        styleCombos(searchA, searchB, evalA, evalB);
        styleFields(featuresA, featuresB, nodes, openings, seed, maxPlies, workers, threadsA, threadsB);
        styleAreas(commandArea, outputArea);
        applyAccessibleNames();

        commandArea.setEditable(false);
        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        stopButton.setEnabled(false);
        stopButton.setVisible(false);
        runAgainButton.setEnabled(false);
        saveResultButton.setEnabled(false);
        statusBadge.notRun("not run");
        candidateBadge.ready("valid");
        baselineBadge.ready("valid");
        settingsBadge.ready("ready");

        installPreviewRefresh();
        workspaceHeader.setActions(createHeaderActions());
        add(workspaceHeader, BorderLayout.NORTH);
        add(createExperimentBody(), BorderLayout.CENTER);
        refreshCommandPreview();
        refreshExperimentCards();
        updateResultView(ResultSummary.empty(), List.of());
    }

    /**
     * Applies accessible names to controls.
     */
    private void applyAccessibleNames() {
        searchA.getAccessibleContext().setAccessibleName("Candidate search");
        searchB.getAccessibleContext().setAccessibleName("Baseline search");
        evalA.getAccessibleContext().setAccessibleName("Candidate evaluator");
        evalB.getAccessibleContext().setAccessibleName("Baseline evaluator");
        featuresA.getAccessibleContext().setAccessibleName("Candidate features");
        featuresB.getAccessibleContext().setAccessibleName("Baseline features");
        nodes.getAccessibleContext().setAccessibleName("Nodes per move");
        openings.getAccessibleContext().setAccessibleName("Opening count");
        seed.getAccessibleContext().setAccessibleName("Opening seed");
        maxPlies.getAccessibleContext().setAccessibleName("Maximum plies");
        workers.getAccessibleContext().setAccessibleName("Worker count");
        threadsA.getAccessibleContext().setAccessibleName("Candidate threads");
        threadsB.getAccessibleContext().setAccessibleName("Baseline threads");
        commandArea.getAccessibleContext().setAccessibleName("Gauntlet command preview");
        outputArea.getAccessibleContext().setAccessibleName("Gauntlet output");
    }

    /**
     * Creates the workspace-header action row.
     *
     * @return header actions
     */
    private JComponent createHeaderActions() {
        JPanel actions = transparentPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        actions.add(runButton);
        actions.add(runAgainButton);
        actions.add(Ui.button("Copy Command", false, event -> copyCommand()));
        actions.add(saveResultButton);
        actions.add(Ui.button("Open Logs", false, event -> showRawOutput()));
        actions.add(stopButton);
        actions.add(statusBadge);
        return actions;
    }

    /**
     * Creates the experiment-runner layout.
     *
     * @return experiment body
     */
    private JComponent createExperimentBody() {
        JPanel page = transparentPanel(new BorderLayout(0, Theme.SPACE_MD));
        page.setBorder(Theme.pad(Theme.SPACE_MD));

        CardGrid setup = new CardGrid(260, Theme.SPACE_MD);
        setup.add(createEngineConfigCard("Candidate A", searchA, evalA, featuresA, candidateBadge));
        setup.add(createEngineConfigCard("Baseline B", searchB, evalB, featuresB, baselineBadge));
        setup.add(createRunSettingsCard());
        page.add(setup, BorderLayout.NORTH);

        JPanel middleLower = transparentPanel(new BorderLayout(0, Theme.SPACE_MD));
        middleLower.add(createResultCards(), BorderLayout.NORTH);
        middleLower.add(createLowerResults(), BorderLayout.CENTER);
        page.add(middleLower, BorderLayout.CENTER);
        return scroll(page);
    }

    /**
     * Creates candidate/baseline configuration card.
     *
     * @param title card title
     * @param search search selector
     * @param eval evaluator selector
     * @param features feature field
     * @param badge validation badge
     * @return card
     */
    private JComponent createEngineConfigCard(String title, JComboBox<String> search, JComboBox<String> eval,
            JTextField features, StatusBadge badge) {
        JPanel body = transparentPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = constraints();
        int row = 0;
        row = detailControl(body, c, "Search", search, row);
        row = detailControl(body, c, "Eval", eval, row);
        row = detailControl(body, c, "Features", features, row);
        row = detailValue(body, c, "Network", networkLabel(eval), row);
        detailComponent(body, c, "Status", badge, row);
        return card(title, body);
    }

    /**
     * Creates run settings card.
     *
     * @return card
     */
    private JComponent createRunSettingsCard() {
        JPanel body = transparentPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = constraints();
        int row = 0;
        // Two columns keep this card's height close to the Candidate/Baseline
        // cards beside it, so the setup row no longer leaves a dead gap below
        // the shorter cards.
        row = settingsPair(body, c, "Nodes", nodes, "Openings", openings, row);
        row = settingsPair(body, c, "Seed", seed, "Max plies", maxPlies, row);
        row = settingsPair(body, c, "Workers", workers, "Threads A", threadsA, row);
        row = settingsPair(body, c, "Threads B", threadsB, null, null, row);
        detailComponent(body, c, "Status", settingsBadge, row);
        return card("Run Settings", body);
    }

    /**
     * Adds one or two label/control cells on a single row of a four-column grid.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param keyA first label
     * @param valueA first control
     * @param keyB second label, or {@code null} for a single cell
     * @param valueB second control, or {@code null}
     * @param row row index
     * @return next row
     */
    private static int settingsPair(JPanel panel, java.awt.GridBagConstraints c, String keyA,
            JComponent valueA, String keyB, JComponent valueB, int row) {
        addPairCell(panel, c, keyA, valueA, 0, row);
        if (keyB != null && valueB != null) {
            addPairCell(panel, c, keyB, valueB, 2, row);
        }
        return row + 1;
    }

    /**
     * Adds a label + stretching control into two adjacent grid columns.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param key label text
     * @param control control component
     * @param col label column (control goes in {@code col + 1})
     * @param row row index
     */
    private static void addPairCell(JPanel panel, java.awt.GridBagConstraints c, String key,
            JComponent control, int col, int row) {
        JLabel label = Ui.label(key);
        label.setToolTipText(tooltipFor(key));
        c.gridx = col;
        c.gridy = row;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.weightx = 0;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        panel.add(label, c);
        c.gridx = col + 1;
        c.weightx = 1;
        panel.add(control, c);
    }

    /**
     * Creates result summary cards.
     *
     * @return summary grid
     */
    private JComponent createResultCards() {
        CardGrid grid = new CardGrid(150, Theme.SPACE_MD);
        grid.add(scoreMetric);
        grid.add(wdlMetric);
        grid.add(eloMetric);
        grid.add(gamesMetric);
        grid.add(errorsMetric);
        grid.add(durationMetric);
        return grid;
    }

    /**
     * Creates charts and raw output section.
     *
     * @return lower result area
     */
    private JComponent createLowerResults() {
        CardGrid charts = new CardGrid(300, Theme.SPACE_MD);
        charts.add(card("Result Distribution", distributionChart));
        charts.add(card("Cumulative Score", cumulativeChart));

        JPanel raw = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        raw.add(Ui.titled("Command", scroll(commandArea)), BorderLayout.NORTH);
        raw.add(Ui.titled("Raw Output", scroll(outputArea)), BorderLayout.CENTER);
        rawSection = Ui.collapsible("Command / Raw Output", raw, false);

        JPanel lower = transparentPanel(new BorderLayout(0, Theme.SPACE_MD));
        lower.add(charts, BorderLayout.CENTER);
        lower.add(rawSection, BorderLayout.SOUTH);
        return lower;
    }

    /**
     * Installs command-preview refresh listeners.
     */
    private void installPreviewRefresh() {
        for (JTextField field : List.of(featuresA, featuresB, nodes, openings, seed, maxPlies,
                workers, threadsA, threadsB)) {
            onTextChange(this::refreshCommandPreview, field);
        }
        for (JComboBox<String> combo : List.of(searchA, searchB, evalA, evalB)) {
            combo.addActionListener(event -> refreshCommandPreview());
        }
    }

    /**
     * Refreshes the command preview.
     */
    private void refreshCommandPreview() {
        commandArea.setText(CommandRunner.displayCommandBlock(buildCommand(currentConfig())));
        commandArea.setCaretPosition(0);
        refreshExperimentCards();
    }

    /**
     * Copies the current command.
     */
    private void copyCommand() {
        copyText.accept(CommandRunner.displayCommand(buildCommand(currentConfig())));
        statusLabel.setText("Command copied");
        statusBadge.ready("command copied");
    }

    /**
     * Starts the gauntlet command in a child JVM.
     */
    private void runGauntlet() {
        if (running != null && running.isRunning()) {
            return;
        }
        List<String> command = buildCommand(currentConfig());
        outputBuffer.setLength(0);
        progress.clear();
        lastResult = ResultSummary.running(gamesFromConfig(currentConfig()));
        outputArea.setText("$ " + CommandRunner.displayCommand(command) + System.lineSeparator());
        outputBuffer.append(outputArea.getText());
        updateResultView(lastResult, progress);
        setRunning(true);
        Ui.setCollapsibleExpanded(rawSection, true);
        running = CommandRunner.run(command, null, this::appendOutput, this::onCompleted, this::onFailed);
    }

    /**
     * Appends a chunk of command output.
     *
     * @param chunk output chunk
     */
    private void appendOutput(String chunk) {
        outputBuffer.append(chunk);
        outputArea.append(chunk);
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
        parseProgress(outputBuffer.toString());
        updateResultView(lastResult, progress);
    }

    /**
     * Updates panel state when the command completes.
     *
     * @param result command result
     */
    private void onCompleted(CommandRunner.CommandResult result) {
        setRunning(false);
        lastResult = parseResult(result.output(), result.exitCode(), result.millis());
        updateResultView(lastResult, progress);
        statusLabel.setText(result.exitCode() == 0
                ? "Gauntlet complete"
                : "Gauntlet failed: " + result.exitCode());
        if (result.exitCode() == 0) {
            statusBadge.complete("complete");
        } else {
            statusBadge.error("failed");
        }
    }

    /**
     * Updates panel state when the command fails or is cancelled.
     *
     * @param error failure cause
     */
    private void onFailed(Exception error) {
        setRunning(false);
        if (error instanceof CancellationException) {
            statusLabel.setText("Gauntlet stopped");
            statusBadge.paused("stopped");
            return;
        }
        statusLabel.setText("Gauntlet failed");
        statusBadge.error("failed");
        outputArea.append(System.lineSeparator() + error.getMessage() + System.lineSeparator());
        outputBuffer.append(System.lineSeparator()).append(error.getMessage()).append(System.lineSeparator());
        lastResult = lastResult.withError("failed");
        updateResultView(lastResult, progress);
    }

    /**
     * Stops the active gauntlet command.
     */
    private void stopGauntlet() {
        if (running != null) {
            running.cancel();
        }
        setRunning(false);
        statusLabel.setText("Gauntlet stopped");
        statusBadge.paused("stopped");
    }

    /**
     * Applies running control state.
     *
     * @param active true while active
     */
    private void setRunning(boolean active) {
        runButton.setEnabled(!active);
        runAgainButton.setEnabled(!active && lastResult.games() > 0);
        saveResultButton.setEnabled(!active && outputBuffer.length() > 0);
        stopButton.setEnabled(active);
        stopButton.setVisible(active);
        statusLabel.setText(active ? "Running" : "Ready");
        if (active) {
            statusBadge.running("running");
        }
        refreshExperimentCards();
    }

    /**
     * Updates static experiment cards and header context.
     */
    private void refreshExperimentCards() {
        candidateBadge.ready(selected(searchA) + " / " + selected(evalA));
        baselineBadge.ready(selected(searchB) + " / " + selected(evalB));
        settingsBadge.ready(gamesFromConfig(currentConfig()) + " games");
        workspaceHeader.setContext(contextText());
        repaint();
    }

    /**
     * Adds a labelled control row.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param key label text
     * @param control control component
     * @param row row index
     * @return next row
     */
    private static int detailControl(JPanel panel, java.awt.GridBagConstraints c, String key,
            JComponent control, int row) {
        return detailComponent(panel, c, key, control, row);
    }

    /**
     * Adds a labelled value row.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param key label text
     * @param value value component
     * @param row row index
     * @return next row
     */
    private static int detailComponent(JPanel panel, java.awt.GridBagConstraints c, String key,
            JComponent value, int row) {
        JLabel label = Ui.label(key);
        label.setToolTipText(tooltipFor(key));
        grid(panel, label, c, 0, row, 1, 1);
        grid(panel, value, c, 1, row, 3, 1);
        return row + 1;
    }

    /**
     * Adds a text value row.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param key label text
     * @param text value text
     * @param row row index
     * @return next row
     */
    private static int detailValue(JPanel panel, java.awt.GridBagConstraints c, String key,
            JComponent text, int row) {
        return detailComponent(panel, c, key, text, row);
    }

    /**
     * Creates a live network summary label for an evaluator selector.
     *
     * @param eval evaluator combo
     * @return label
     */
    private static JLabel networkLabel(JComboBox<String> eval) {
        JLabel label = detailTextLabel(networkText(selected(eval)));
        eval.addActionListener(event -> label.setText(networkText(selected(eval))));
        return label;
    }

    /**
     * Returns a network summary for an evaluator.
     *
     * @param eval evaluator value
     * @return network text
     */
    private static String networkText(String eval) {
        return "nnue".equals(eval) ? "local NNUE / HalfKP" : "none (classical eval)";
    }

    /**
     * Creates a styled detail text label.
     *
     * @param text label text
     * @return label
     */
    private static JLabel detailTextLabel(String text) {
        JLabel label = new JLabel(text == null ? "-" : text);
        label.setFont(Theme.font(12, Font.PLAIN));
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        return label;
    }

    /**
     * Returns a short tooltip for technical row labels.
     *
     * @param key row key
     * @return tooltip
     */
    private static String tooltipFor(String key) {
        return switch (key) {
            case "Eval" -> "Evaluator backend: classical terms or NNUE neural evaluation.";
            case "Features" -> "Alpha-beta feature set used by the candidate or baseline.";
            case "Nodes" -> "Fixed search-node budget per move.";
            case "Max plies" -> "Maximum half-moves before draw adjudication.";
            case "Workers" -> "Parallel opening-pair workers.";
            case "Threads A", "Threads B" -> "Search threads assigned to each side.";
            default -> null;
        };
    }

    /**
     * Updates visible result metrics and charts.
     *
     * @param result parsed result
     * @param points running W-D-L points
     */
    private void updateResultView(ResultSummary result, List<ProgressPoint> points) {
        scoreMetric.setValue(result.scoreText(), result.scoreDetail());
        wdlMetric.setValue(result.wdlText(), result.wdlDetail());
        eloMetric.setValue(result.eloText(), result.eloDetail());
        gamesMetric.setValue(result.gamesText(), result.gamesDetail());
        errorsMetric.setValue(result.errorsText(), result.errorsDetail());
        durationMetric.setValue(result.durationText(), result.durationDetail());
        distributionChart.setResult(result);
        cumulativeChart.setPoints(points);
        workspaceHeader.setContext(contextText());
    }

    /**
     * Focuses the raw command/output section.
     */
    private void showRawOutput() {
        Ui.setCollapsibleExpanded(rawSection, true);
        outputArea.requestFocusInWindow();
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    /**
     * Saves the captured result text.
     */
    private void saveResult() {
        if (outputBuffer.length() == 0) {
            statusBadge.warning("nothing to save");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Gauntlet Result");
        chooser.setSelectedFile(new java.io.File("gauntlet-result.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path output = chooser.getSelectedFile().toPath();
        String text = "Command: " + CommandRunner.displayCommand(buildCommand(currentConfig()))
                + System.lineSeparator() + System.lineSeparator() + outputBuffer;
        try {
            Files.writeString(output, text, StandardCharsets.UTF_8);
            statusBadge.complete("saved");
        } catch (IOException ex) {
            statusBadge.error("save failed");
            outputArea.append(System.lineSeparator() + "Save failed: " + ex.getMessage() + System.lineSeparator());
        }
    }

    /**
     * Returns the current header context.
     *
     * @return context line
     */
    private String contextText() {
        GauntletConfig config = currentConfig();
        String state = running != null && running.isRunning() ? "Running"
                : lastResult.complete() ? "Complete" : "Ready";
        return "Candidate A vs Baseline B · " + gamesFromConfig(config) + " games · " + state;
    }

    /**
     * Parses running W-D-L snapshots from raw output.
     *
     * @param output raw output
     */
    private void parseProgress(String output) {
        Pattern pattern = Pattern.compile("running W-D-L = (\\d+)-(\\d+)-(\\d+)");
        Matcher matcher = pattern.matcher(output == null ? "" : output);
        progress.clear();
        while (matcher.find()) {
            progress.add(new ProgressPoint(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))));
        }
    }

    /**
     * Parses final gauntlet result text.
     *
     * @param output command output
     * @param exitCode process exit code
     * @param millis elapsed time
     * @return parsed summary
     */
    private static ResultSummary parseResult(String output, int exitCode, long millis) {
        String text = output == null ? "" : output;
        Pattern resultPattern = Pattern.compile("\\+(\\d+)\\s+=(\\d+)\\s+-(\\d+)\\s+of\\s+(\\d+)\\s+games");
        Matcher resultMatcher = resultPattern.matcher(text);
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int games = 0;
        if (resultMatcher.find()) {
            wins = Integer.parseInt(resultMatcher.group(1));
            draws = Integer.parseInt(resultMatcher.group(2));
            losses = Integer.parseInt(resultMatcher.group(3));
            games = Integer.parseInt(resultMatcher.group(4));
        }
        Double score = matchDouble(text, "Score:\\s+([0-9.]+)%");
        String elo = matchText(text, "Elo estimate:\\s+([+-]?\\d+)");
        return new ResultSummary(
                true,
                wins,
                draws,
                losses,
                games,
                score,
                elo == null ? "-" : elo,
                exitCode == 0 ? "0" : "exit " + exitCode,
                millis);
    }

    /**
     * Returns first matched double.
     *
     * @param text source text
     * @param regex regex with one numeric capture
     * @return parsed value or null
     */
    private static Double matchDouble(String text, String regex) {
        String value = matchText(text, regex);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Returns first matched text.
     *
     * @param text source text
     * @param regex regex with one capture
     * @return capture or null
     */
    private static String matchText(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Returns configured game count.
     *
     * @param config current config
     * @return games
     */
    private static int gamesFromConfig(GauntletConfig config) {
        return Math.max(0, parseInt(config.openings(), 0) * 2);
    }

    /**
     * Parses an integer with fallback.
     *
     * @param value text
     * @param fallback fallback
     * @return parsed integer
     */
    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Formats milliseconds.
     *
     * @param millis milliseconds
     * @return duration
     */
    private static String duration(long millis) {
        if (millis <= 0L) {
            return "-";
        }
        if (millis < 1000L) {
            return millis + " ms";
        }
        return String.format(Locale.ROOT, "%.1f s", millis / 1000.0d);
    }

    /**
     * One running W-D-L point.
     *
     * @param wins candidate wins
     * @param draws draws
     * @param losses candidate losses
     */
    private record ProgressPoint(int wins, int draws, int losses) {

        /**
         * Returns games observed.
         *
         * @return game count
         */
        int games() {
            return wins + draws + losses;
        }

        /**
         * Returns candidate score fraction.
         *
         * @return score fraction
         */
        double score() {
            int games = games();
            return games <= 0 ? 0.5d : (wins + draws * 0.5d) / games;
        }
    }

    /**
     * Parsed gauntlet summary.
     *
     * @param complete true when a process result has arrived
     * @param wins candidate wins
     * @param draws draws
     * @param losses candidate losses
     * @param games total games
     * @param scorePercent score percentage
     * @param elo estimate text
     * @param errors error summary
     * @param millis elapsed duration
     */
    private record ResultSummary(
            boolean complete,
            int wins,
            int draws,
            int losses,
            int games,
            Double scorePercent,
            String elo,
            String errors,
            long millis) {

        /**
         * Empty summary.
         *
         * @return summary
         */
        static ResultSummary empty() {
            return new ResultSummary(false, 0, 0, 0, 0, null, "-", "-", 0L);
        }

        /**
         * Running summary.
         *
         * @param games expected games
         * @return summary
         */
        static ResultSummary running(int games) {
            return new ResultSummary(false, 0, 0, 0, games, null, "-", "-", 0L);
        }

        /**
         * Returns copy with error text.
         *
         * @param value error value
         * @return summary
         */
        ResultSummary withError(String value) {
            return new ResultSummary(complete, wins, draws, losses, games, scorePercent, elo, value, millis);
        }

        /**
         * Score card value.
         *
         * @return value
         */
        String scoreText() {
            return scorePercent == null ? "-" : String.format(Locale.ROOT, "%.1f%%", scorePercent.doubleValue());
        }

        /**
         * Score detail.
         *
         * @return detail
         */
        String scoreDetail() {
            return complete ? "candidate perspective" : "run a gauntlet";
        }

        /**
         * W-D-L value.
         *
         * @return value
         */
        String wdlText() {
            return wins + " / " + draws + " / " + losses;
        }

        /**
         * W-D-L detail.
         *
         * @return detail
         */
        String wdlDetail() {
            return complete ? "wins / draws / losses" : "not complete";
        }

        /**
         * Elo value.
         *
         * @return value
         */
        String eloText() {
            return elo == null || elo.isBlank() ? "-" : elo;
        }

        /**
         * Elo detail.
         *
         * @return detail
         */
        String eloDetail() {
            return "point estimate";
        }

        /**
         * Games value.
         *
         * @return value
         */
        String gamesText() {
            return games <= 0 ? "-" : Integer.toString(games);
        }

        /**
         * Games detail.
         *
         * @return detail
         */
        String gamesDetail() {
            return complete ? "completed games" : "configured games";
        }

        /**
         * Errors value.
         *
         * @return value
         */
        String errorsText() {
            return errors == null || errors.isBlank() ? "-" : errors;
        }

        /**
         * Errors detail.
         *
         * @return detail
         */
        String errorsDetail() {
            return complete ? "process status" : "unavailable until run";
        }

        /**
         * Duration value.
         *
         * @return value
         */
        String durationText() {
            return duration(millis);
        }

        /**
         * Duration detail.
         *
         * @return detail
         */
        String durationDetail() {
            return "wall-clock runtime";
        }
    }

    /**
     * Compact metric card.
     */
    private static final class MetricCard extends JPanel {

        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Title.
         */
        private final JLabel title = new JLabel();

        /**
         * Value.
         */
        private final JLabel value = new JLabel("-");

        /**
         * Detail.
         */
        private final JLabel detail = new JLabel("-");

        /**
         * Creates a metric card.
         *
         * @param label label
         */
        MetricCard(String label) {
            super(new BorderLayout(0, 3));
            setOpaque(false);
            setBorder(Theme.pad(8, 10, 8, 10));
            title.setText(label);
            Theme.foreground(title, Theme.ForegroundRole.MUTED);
            title.setFont(Theme.font(11, Font.BOLD));
            Theme.foreground(value, Theme.ForegroundRole.TEXT);
            value.setFont(Theme.font(18, Font.BOLD));
            Theme.foreground(detail, Theme.ForegroundRole.MUTED);
            detail.setFont(Theme.font(11, Font.PLAIN));
            add(title, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            add(detail, BorderLayout.SOUTH);
        }

        /**
         * Sets metric content.
         *
         * @param nextValue value
         * @param nextDetail detail
         */
        void setValue(String nextValue, String nextDetail) {
            value.setText(nextValue == null || nextValue.isBlank() ? "-" : nextValue);
            detail.setText(nextDetail == null || nextDetail.isBlank() ? "-" : nextDetail);
            setToolTipText(title.getText() + ": " + value.getText() + " - " + detail.getText());
            repaint();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Dimension getPreferredSize() {
            Dimension base = super.getPreferredSize();
            return new Dimension(Math.max(142, base.width), Math.max(76, base.height));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.ELEVATED_SOLID);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                g.setColor(Theme.LINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    /**
     * W-D-L distribution chart.
     */
    private static final class ResultDistributionChart extends JComponent {

        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Summary.
         */
        private ResultSummary result = ResultSummary.empty();

        /**
         * Sets result.
         *
         * @param next result
         */
        void setResult(ResultSummary next) {
            result = next == null ? ResultSummary.empty() : next;
            repaint();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(320, 150);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!result.complete() || result.games() <= 0) {
                    Ui.paintEmptyState(g, new java.awt.Rectangle(0, 0, getWidth(), getHeight()),
                            "No result yet", "Run a gauntlet to see win/draw/loss distribution.");
                    return;
                }
                int x = Theme.SPACE_MD;
                int y = getHeight() / 2 - 14;
                int w = Math.max(1, getWidth() - Theme.SPACE_MD * 2);
                int h = 28;
                int games = Math.max(1, result.games());
                int winW = Math.round(w * result.wins() / (float) games);
                int drawW = Math.round(w * result.draws() / (float) games);
                int lossW = Math.max(0, w - winW - drawW);
                paintSegment(g, x, y, winW, h, Theme.STATUS_SUCCESS_BORDER);
                paintSegment(g, x + winW, y, drawW, h, Theme.STATUS_WARNING_BORDER);
                paintSegment(g, x + winW + drawW, y, lossW, h, Theme.STATUS_ERROR_BORDER);
                g.setFont(Theme.font(11, Font.BOLD));
                g.setColor(Theme.TEXT);
                g.drawString("wins " + result.wins() + "   draws " + result.draws()
                        + "   losses " + result.losses(), x, y + h + 24);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints one segment.
         *
         * @param g graphics
         * @param x x
         * @param y y
         * @param w width
         * @param h height
         * @param color color
         */
        private static void paintSegment(Graphics2D g, int x, int y, int w, int h, Color color) {
            if (w <= 0) {
                return;
            }
            g.setColor(color);
            g.fillRoundRect(x, y, w, h, Theme.RADIUS, Theme.RADIUS);
        }
    }

    /**
     * Cumulative score chart.
     */
    private static final class CumulativeScoreChart extends JComponent {

        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Progress points.
         */
        private List<ProgressPoint> points = List.of();

        /**
         * Sets progress points.
         *
         * @param next points
         */
        void setPoints(List<ProgressPoint> next) {
            points = next == null ? List.of() : List.copyOf(next);
            repaint();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(320, 150);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (points.isEmpty()) {
                    Ui.paintEmptyState(g, new java.awt.Rectangle(0, 0, getWidth(), getHeight()),
                            "No running score", "Running W-D-L snapshots appear during a gauntlet.");
                    return;
                }
                int left = Theme.SPACE_LG;
                int top = Theme.SPACE_MD;
                int width = Math.max(1, getWidth() - Theme.SPACE_LG * 2);
                int height = Math.max(1, getHeight() - Theme.SPACE_LG * 2);
                int midY = top + height / 2;
                g.setColor(Theme.LINE);
                g.drawLine(left, midY, left + width, midY);
                g.setColor(Theme.ACCENT);
                int lastX = left;
                int lastY = scoreY(points.get(0), top, height);
                for (int i = 1; i < points.size(); i++) {
                    int x = left + Math.round(i * width / (float) Math.max(1, points.size() - 1));
                    int y = scoreY(points.get(i), top, height);
                    g.drawLine(lastX, lastY, x, y);
                    lastX = x;
                    lastY = y;
                }
                g.fillOval(lastX - 3, lastY - 3, 6, 6);
                g.setFont(Theme.font(11, Font.BOLD));
                g.setColor(Theme.TEXT);
                ProgressPoint latest = points.get(points.size() - 1);
                g.drawString(String.format(Locale.ROOT, "score %.1f%% after %d games",
                        latest.score() * 100.0d, latest.games()), left, top + height + 16);
            } finally {
                g.dispose();
            }
        }

        /**
         * Returns y coordinate for a score.
         *
         * @param point point
         * @param top chart top
         * @param height chart height
         * @return y
         */
        private static int scoreY(ProgressPoint point, int top, int height) {
            double score = Math.max(0.0d, Math.min(1.0d, point.score()));
            return top + (int) Math.round((1.0d - score) * height);
        }
    }

    /**
     * Reads current controls into a config record.
     *
     * @return current config
     */
    private GauntletConfig currentConfig() {
        return new GauntletConfig(
                text(featuresA, "all"),
                text(featuresB, "none"),
                selected(searchA),
                selected(searchB),
                selected(evalA),
                selected(evalB),
                text(nodes, "3000"),
                text(openings, "8"),
                text(seed, "20260531"),
                text(maxPlies, "160"),
                text(workers, "1"),
                text(threadsA, "1"),
                text(threadsB, "1"));
    }

    /**
     * Creates a text field.
     *
     * @param value default value
     * @return field
     */
    private static JTextField field(String value) {
        JTextField field = new JTextField(value, 12);
        field.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
        return field;
    }

    /**
     * Creates a combo box.
     *
     * @param values choices
     * @return combo
     */
    private static JComboBox<String> combo(String... values) {
        JComboBox<String> combo = new JComboBox<>(values);
        combo.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
        return combo;
    }

    /**
     * Returns selected combo value.
     *
     * @param combo combo
     * @return selected string
     */
    private static String selected(JComboBox<String> combo) {
        Object value = combo.getSelectedItem();
        return value == null ? "" : value.toString();
    }

    /**
     * Returns field text with fallback.
     *
     * @param field source field
     * @param fallback fallback
     * @return text
     */
    private static String text(JTextField field, String fallback) {
        String value = field.getText();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * Adds one option pair when the value is present.
     *
     * @param command command argv
     * @param flag option flag
     * @param value option value
     */
    private static void addOption(List<String> command, String flag, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        command.add(flag);
        command.add(value.trim());
    }

    /**
     * Gauntlet command configuration.
     *
     * @param featuresA candidate alpha-beta features
     * @param featuresB baseline alpha-beta features
     * @param searchA candidate search
     * @param searchB baseline search
     * @param evalA candidate evaluator
     * @param evalB baseline evaluator
     * @param nodes fixed nodes per move
     * @param openings seeded opening count
     * @param seed opening seed
     * @param maxPlies draw-adjudication ply cap
     * @param workers opening-pair workers
     * @param threadsA candidate threads
     * @param threadsB baseline threads
     */
    public record GauntletConfig(
            String featuresA,
            String featuresB,
            String searchA,
            String searchB,
            String evalA,
            String evalB,
            String nodes,
            String openings,
            String seed,
            String maxPlies,
            String workers,
            String threadsA,
            String threadsB) {
    }
}
