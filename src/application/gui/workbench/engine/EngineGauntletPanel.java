package application.gui.workbench.engine;

import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.command.CommandRunner.RunningCommand;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import static application.gui.workbench.ui.Ui.labelControlRow;
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
     * Status label.
     */
    private final javax.swing.JLabel statusLabel = Ui.label("Ready");

    /**
     * Run and stop controls.
     */
    private final JButton runButton = Ui.button("Run", true, event -> runGauntlet());
    private final JButton stopButton = Ui.button("Stop", false, event -> stopGauntlet());

    /**
     * Active command handle.
     */
    private transient RunningCommand running;

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
        setBorder(Theme.pad(Theme.SPACE_MD));
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
        stopButton.setEnabled(false);

        installPreviewRefresh();
        add(createToolbar(), BorderLayout.NORTH);
        JSplitPane split = SplitPaneStyler.styledHorizontalSplit(createControls(), createOutput(), 0.34);
        add(split, BorderLayout.CENTER);
        refreshCommandPreview();
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
     * Creates the action toolbar.
     *
     * @return toolbar
     */
    private JComponent createToolbar() {
        JPanel toolbar = transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        toolbar.add(runButton);
        toolbar.add(stopButton);
        toolbar.add(Ui.button("Copy Command", false, event -> copyCommand()));
        toolbar.add(statusLabel);
        return toolbar;
    }

    /**
     * Creates the configuration controls.
     *
     * @return controls
     */
    private JComponent createControls() {
        JPanel panel = new SurfacePanel(new BorderLayout());
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBorder(Theme.pad(Theme.SPACE_MD));
        panel.add(Theme.section("Candidate A"));
        panel.add(labelControlRow("Search", searchA, 86));
        panel.add(labelControlRow("Eval", evalA, 86));
        panel.add(labelControlRow("Features", featuresA, 86));
        panel.add(javax.swing.Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(Theme.section("Baseline B"));
        panel.add(labelControlRow("Search", searchB, 86));
        panel.add(labelControlRow("Eval", evalB, 86));
        panel.add(labelControlRow("Features", featuresB, 86));
        panel.add(javax.swing.Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(Theme.section("Run"));
        panel.add(labelControlRow("Nodes", nodes, 86));
        panel.add(labelControlRow("Openings", openings, 86));
        panel.add(labelControlRow("Seed", seed, 86));
        panel.add(labelControlRow("Max plies", maxPlies, 86));
        panel.add(labelControlRow("Workers", workers, 86));
        panel.add(labelControlRow("Threads A", threadsA, 86));
        panel.add(labelControlRow("Threads B", threadsB, 86));
        return panel;
    }

    /**
     * Creates command preview and output.
     *
     * @return output area
     */
    private JComponent createOutput() {
        JPanel panel = transparentPanel(new BorderLayout(Theme.SPACE_SM, Theme.SPACE_SM));
        panel.add(Ui.titled("Command", scroll(commandArea)), BorderLayout.NORTH);
        panel.add(Ui.titled("Output", scroll(outputArea)), BorderLayout.CENTER);
        return panel;
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
    }

    /**
     * Copies the current command.
     */
    private void copyCommand() {
        copyText.accept(CommandRunner.displayCommand(buildCommand(currentConfig())));
        statusLabel.setText("Command copied");
    }

    /**
     * Starts the gauntlet command in a child JVM.
     */
    private void runGauntlet() {
        if (running != null && running.isRunning()) {
            return;
        }
        List<String> command = buildCommand(currentConfig());
        outputArea.setText("$ " + CommandRunner.displayCommand(command) + System.lineSeparator());
        setRunning(true);
        running = CommandRunner.run(command, null, this::appendOutput, this::onCompleted, this::onFailed);
    }

    /**
     * Appends a chunk of command output.
     *
     * @param chunk output chunk
     */
    private void appendOutput(String chunk) {
        outputArea.append(chunk);
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    /**
     * Updates panel state when the command completes.
     *
     * @param result command result
     */
    private void onCompleted(CommandRunner.CommandResult result) {
        setRunning(false);
        statusLabel.setText(result.exitCode() == 0
                ? "Gauntlet complete"
                : "Gauntlet failed: " + result.exitCode());
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
            return;
        }
        statusLabel.setText("Gauntlet failed");
        outputArea.append(System.lineSeparator() + error.getMessage() + System.lineSeparator());
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
    }

    /**
     * Applies running control state.
     *
     * @param active true while active
     */
    private void setRunning(boolean active) {
        runButton.setEnabled(!active);
        stopButton.setEnabled(active);
        statusLabel.setText(active ? "Running" : "Ready");
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
