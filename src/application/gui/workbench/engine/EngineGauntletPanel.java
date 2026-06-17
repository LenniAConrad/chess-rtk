package application.gui.workbench.engine;

import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.command.CommandRunner.RunningCommand;
import application.gui.workbench.ui.CardGrid;
import application.gui.workbench.ui.FieldValidator;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WorkspaceHeader;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
public final class EngineGauntletPanel extends SurfacePanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Leading CRTK command path tokens for the gauntlet command.
     */
    private static final List<String> COMMAND_PATH = List.of("engine", "gauntlet");

    /**
     * Engine-source labels for the per-side mode selector.
     */
    private static final String MODE_BUILTIN = "Built-in";
    private static final String MODE_UCI = "External (UCI)";

    /**
     * Budget-unit labels for the per-side budget selector.
     */
    private static final String BUDGET_NODES = "Nodes";
    private static final String BUDGET_TIME = "Time (ms)";

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
    private final JComboBox<String> evalA = combo("classical", "nnue", "cnn", "bt4", "otis");
    private final JComboBox<String> evalB = combo("classical", "nnue", "cnn", "bt4", "otis");

    /**
     * Feature and budget fields.
     */
    private final JTextField featuresA = field("all");
    private final JTextField featuresB = field("none");
    private final JTextField openings = field("8");
    private final JTextField seed = field("20260531");
    private final JTextField maxPlies = field("160");
    private final JTextField workers = field("1");
    private final JTextField threadsA = field("1");
    private final JTextField threadsB = field("1");

    /**
     * Per-side engine source: the built-in searcher or an external UCI engine.
     */
    private final JComboBox<String> engineModeA = combo(MODE_BUILTIN, MODE_UCI);
    private final JComboBox<String> engineModeB = combo(MODE_BUILTIN, MODE_UCI);

    /**
     * External UCI engine commands, used when a side's mode is External.
     */
    private final JTextField engineA = field("");
    private final JTextField engineB = field("");

    /**
     * Per-side external UCI engine hash size (MB) and extra {@code name=value}
     * options, used when a side's mode is External.
     */
    private final JTextField hashA = field("");
    private final JTextField hashB = field("");
    private final JTextField optionsA = field("");
    private final JTextField optionsB = field("");

    /**
     * Per-side per-move budget: a unit (nodes or time) and a value.
     */
    private final JComboBox<String> budgetTypeA = combo(BUDGET_NODES, BUDGET_TIME);
    private final JComboBox<String> budgetTypeB = combo(BUDGET_NODES, BUDGET_TIME);
    private final JTextField budgetValueA = field("3000");
    private final JTextField budgetValueB = field("3000");

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
     * Result metric cards.
     */
    private final GauntletMetricCard scoreMetric = new GauntletMetricCard("Score");
    private final GauntletMetricCard wdlMetric = new GauntletMetricCard("W / D / L");
    private final GauntletMetricCard eloMetric = new GauntletMetricCard("Elo");
    private final GauntletMetricCard gamesMetric = new GauntletMetricCard("Games");
    private final GauntletMetricCard errorsMetric = new GauntletMetricCard("Crashes / errors");
    private final GauntletMetricCard durationMetric = new GauntletMetricCard("Duration");

    /**
     * Result charts.
     */
    private final GauntletDistributionChart distributionChart = new GauntletDistributionChart();
    private final GauntletCumulativeScoreChart cumulativeChart = new GauntletCumulativeScoreChart();

    /**
     * Live gallery of finished games, replayable on click.
     */
    private final GauntletGameBrowser gamesBrowser = new GauntletGameBrowser();

    /**
     * Game indices already added to the gallery, for incremental parsing.
     */
    private final java.util.Set<Integer> seenGameIndices = new java.util.HashSet<>();

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
    private final List<GauntletProgressPoint> progress = new ArrayList<>();

    /**
     * Latest parsed result.
     */
    private GauntletResultSummary lastResult = GauntletResultSummary.empty();

    /**
     * Live candidate-perspective tally accumulated from the per-game stream while
     * a gauntlet runs, so the result cards and chart update game by game.
     */
    private int liveWins;
    private int liveDraws;
    private int liveLosses;

    /**
     * Total games the active run will play, for the live "played of total" view.
     */
    private int runGames;

    /**
     * Start time of the active run, for the live duration readout.
     */
    private long runStartNanos;

    /**
     * One-second ticker that keeps the duration and live metrics current between
     * games while a gauntlet runs.
     */
    private transient javax.swing.Timer liveTimer;

    /**
     * Type validators for the numeric run-settings fields. The gauntlet refuses
     * to launch while any of them holds a non-numeric value, since the bad text
     * would only fail later inside the child CLI process.
     */
    private final List<FieldValidator> numericValidators = new ArrayList<>();

    /**
     * True while a gauntlet command is running.
     */
    private boolean runActive;

    /**
     * Creates a gauntlet panel.
     *
     * @param copyText clipboard callback
     */
    public EngineGauntletPanel(Consumer<String> copyText) {
        super(new BorderLayout(Theme.SPACE_MD, Theme.SPACE_MD), Theme.Surface.BACKDROP);
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
        addOption(command, "--openings", config.openings());
        addOption(command, "--seed", config.seed());
        addOption(command, "--maxplies", config.maxPlies());
        addOption(command, "--workers", config.workers());
        addOption(command, "--threadsA", config.threadsA());
        addOption(command, "--threadsB", config.threadsB());
        addOption(command, "--engineA", config.engineA());
        addOption(command, "--engineB", config.engineB());
        addOption(command, "--hashA", config.hashA());
        addOption(command, "--hashB", config.hashB());
        addOption(command, "--optionsA", config.optionsA());
        addOption(command, "--optionsB", config.optionsB());
        addBudget(command, "A", config.budgetA());
        addBudget(command, "B", config.budgetB());
        // The workbench always streams per-game records so the live gallery can
        // render and replay each game; this keeps preview and execution identical.
        command.add("--stream");
        return List.copyOf(command);
    }

    /**
     * Appends a per-side budget override flag, choosing a time or node budget
     * from the field value. A trailing {@code ms} (e.g. {@code 200ms}) selects a
     * time budget; a plain number selects a node budget. Blank adds nothing.
     *
     * @param command command argv
     * @param side side suffix ({@code A} or {@code B})
     * @param value raw budget field value
     */
    private static void addBudget(List<String> command, String side, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (trimmed.endsWith("ms")) {
            String millis = trimmed.substring(0, trimmed.length() - 2).trim();
            addOption(command, "--movetime" + side, millis);
        } else {
            addOption(command, "--nodes" + side, trimmed);
        }
    }

    /**
     * Configures the panel.
     */
    private void configure() {
        getAccessibleContext().setAccessibleName("Engine gauntlet panel");
        getAccessibleContext().setAccessibleDescription(
                "Builds and runs deterministic self-play gauntlets for built-in engines.");
        styleCombos(searchA, searchB, evalA, evalB, engineModeA, engineModeB, budgetTypeA, budgetTypeB);
        styleFields(featuresA, featuresB, openings, seed, maxPlies, workers, threadsA, threadsB,
                engineA, engineB, hashA, hashB, optionsA, optionsB, budgetValueA, budgetValueB);
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

        installEvaluatorSearchDefaults();
        installPreviewRefresh();
        installFieldValidation();
        workspaceHeader.setActions(createHeaderActions());
        add(workspaceHeader, BorderLayout.NORTH);
        add(createExperimentBody(), BorderLayout.CENTER);
        refreshCommandPreview();
        refreshExperimentCards();
        updateResultView(GauntletResultSummary.empty(), List.of());
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
        openings.getAccessibleContext().setAccessibleName("Opening count");
        seed.getAccessibleContext().setAccessibleName("Opening seed");
        maxPlies.getAccessibleContext().setAccessibleName("Maximum plies");
        workers.getAccessibleContext().setAccessibleName("Worker count");
        threadsA.getAccessibleContext().setAccessibleName("Candidate threads");
        threadsB.getAccessibleContext().setAccessibleName("Baseline threads");
        engineModeA.getAccessibleContext().setAccessibleName("Candidate engine source");
        engineModeB.getAccessibleContext().setAccessibleName("Baseline engine source");
        engineA.getAccessibleContext().setAccessibleName("Candidate external UCI engine command");
        engineB.getAccessibleContext().setAccessibleName("Baseline external UCI engine command");
        hashA.getAccessibleContext().setAccessibleName("Candidate engine hash size in MB");
        hashB.getAccessibleContext().setAccessibleName("Baseline engine hash size in MB");
        optionsA.getAccessibleContext().setAccessibleName("Candidate engine UCI options");
        optionsB.getAccessibleContext().setAccessibleName("Baseline engine UCI options");
        budgetTypeA.getAccessibleContext().setAccessibleName("Candidate budget unit");
        budgetTypeB.getAccessibleContext().setAccessibleName("Baseline budget unit");
        budgetValueA.getAccessibleContext().setAccessibleName("Candidate per-move budget value");
        budgetValueB.getAccessibleContext().setAccessibleName("Baseline per-move budget value");
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
        setup.add(createEngineConfigCard("Candidate A", true));
        setup.add(createEngineConfigCard("Baseline B", false));
        setup.add(createRunSettingsCard());
        page.add(setup, BorderLayout.NORTH);

        JPanel middleLower = transparentPanel(new BorderLayout(0, Theme.SPACE_MD));
        middleLower.add(createResultCards(), BorderLayout.NORTH);
        middleLower.add(createLowerResults(), BorderLayout.CENTER);
        page.add(middleLower, BorderLayout.CENTER);
        return scroll(page);
    }

    /**
     * Creates a candidate/baseline configuration card. The Engine selector
     * chooses between the built-in searcher and an external UCI engine, and the
     * card body re-lays itself to show only the rows that apply to the chosen
     * mode, so the built-in-versus-external choice is explicit. External mode
     * exposes the engine command (with Browse and Test), Threads, Hash, and
     * extra UCI options.
     *
     * @param title card title
     * @param candidate true for the candidate (A) side, false for baseline (B)
     * @return card
     */
    private JComponent createEngineConfigCard(String title, boolean candidate) {
        JComboBox<String> mode = candidate ? engineModeA : engineModeB;
        JComboBox<String> search = candidate ? searchA : searchB;
        JComboBox<String> eval = candidate ? evalA : evalB;
        JTextField features = candidate ? featuresA : featuresB;
        JTextField engine = candidate ? engineA : engineB;
        JTextField threads = candidate ? threadsA : threadsB;
        JTextField hash = candidate ? hashA : hashB;
        JTextField options = candidate ? optionsA : optionsB;
        JComboBox<String> budgetType = candidate ? budgetTypeA : budgetTypeB;
        JTextField budgetValue = candidate ? budgetValueA : budgetValueB;

        JPanel body = transparentPanel(new java.awt.GridBagLayout());
        // Build the per-side persistent sub-controls once so re-laying the card
        // on a mode change never stacks duplicate listeners.
        JLabel network = networkLabel(eval);
        JComponent commandRow = uciCommandControl(engine);
        JComponent budgetRow = budgetControl(budgetType, budgetValue);
        Runnable relayout = () -> {
            fillEngineCard(body, mode, search, eval, features, commandRow, threads, hash, options,
                    budgetRow, network);
            body.revalidate();
            body.repaint();
        };
        mode.addActionListener(event -> {
            relayout.run();
            refreshCommandPreview();
        });
        relayout.run();
        return card(title, body);
    }

    /**
     * Lays out an engine card for its current mode, showing the built-in rows or
     * the external-engine rows but not both.
     *
     * @param body card body, cleared and repopulated
     * @param mode engine-source selector
     * @param search built-in search selector
     * @param eval built-in evaluator selector
     * @param features built-in feature field
     * @param commandRow external engine command row (field + Browse + Test)
     * @param threads per-side thread field
     * @param hash external engine hash-size field
     * @param options external engine extra-options field
     * @param budgetRow shared budget-unit + value row
     * @param network shared network summary label
     */
    private static void fillEngineCard(JPanel body, JComboBox<String> mode, JComboBox<String> search,
            JComboBox<String> eval, JTextField features, JComponent commandRow, JTextField threads,
            JTextField hash, JTextField options, JComponent budgetRow, JLabel network) {
        body.removeAll();
        java.awt.GridBagConstraints c = constraints();
        int row = 0;
        row = detailControl(body, c, "Engine", mode, row);
        if (isExternal(mode)) {
            row = detailControl(body, c, "UCI command", commandRow, row);
            row = detailControl(body, c, "Threads", threads, row);
            row = detailControl(body, c, "Hash (MB)", hash, row);
            row = detailControl(body, c, "Options", options, row);
            detailControl(body, c, "Budget", budgetRow, row);
        } else {
            row = detailControl(body, c, "Search", search, row);
            row = detailControl(body, c, "Eval", eval, row);
            row = detailControl(body, c, "Features", features, row);
            row = detailControl(body, c, "Threads", threads, row);
            row = detailControl(body, c, "Budget", budgetRow, row);
            detailValue(body, c, "Network", network, row);
        }
    }

    /**
     * Builds the UCI command row: the command field with Browse and Test
     * buttons, so picking and verifying an external engine is a one-click step.
     *
     * @param engine engine command field
     * @return command control
     */
    private JComponent uciCommandControl(JTextField engine) {
        JPanel row = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        JPanel buttons = transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        buttons.add(Ui.button("Browse…", false, event -> browseEngine(engine)));
        buttons.add(Ui.button("Test", false, event -> testEngine(engine)));
        row.add(engine, BorderLayout.CENTER);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    /**
     * Opens a file picker to choose an external UCI engine binary.
     *
     * @param engine engine command field to populate
     */
    private void browseEngine(JTextField engine) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select UCI engine");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            engine.setText(chooser.getSelectedFile().getAbsolutePath());
            refreshCommandPreview();
        }
    }

    /**
     * Tests an external UCI engine by performing the handshake off the event
     * thread and reporting the engine's name or the failure reason.
     *
     * @param engine engine command field
     */
    private void testEngine(JTextField engine) {
        String command = engine.getText() == null ? "" : engine.getText().trim();
        if (command.isEmpty()) {
            statusBadge.warning("enter an engine command");
            statusLabel.setText("Enter a UCI engine command to test.");
            return;
        }
        statusBadge.running("testing engine…");
        statusLabel.setText("Testing UCI engine…");
        new javax.swing.SwingWorker<String, Void>() {
            /**
             * {@inheritDoc}
             */
            @Override
            protected String doInBackground() throws Exception {
                return chess.engine.Gauntlet.uciEngineName(command);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected void done() {
                try {
                    String name = get();
                    statusBadge.complete("engine ok");
                    statusLabel.setText("UCI engine OK: " + name);
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    statusBadge.error("engine test failed");
                    statusLabel.setText("UCI engine test failed: " + cause.getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    /**
     * Builds the budget row: a unit selector ({@link #BUDGET_NODES} or
     * {@link #BUDGET_TIME}) beside its value field, so the unit is always
     * explicit.
     *
     * @param budgetType budget-unit selector
     * @param budgetValue budget-value field
     * @return budget control
     */
    private JComponent budgetControl(JComboBox<String> budgetType, JTextField budgetValue) {
        JPanel row = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        budgetType.setPreferredSize(new Dimension(110, Theme.CONTROL_HEIGHT));
        budgetType.addActionListener(event -> refreshCommandPreview());
        row.add(budgetType, BorderLayout.WEST);
        row.add(budgetValue, BorderLayout.CENTER);
        return row;
    }

    /**
     * Returns whether an engine-mode selector is set to the external engine.
     *
     * @param mode engine-source selector
     * @return true when external (UCI) is selected
     */
    private static boolean isExternal(JComboBox<String> mode) {
        return MODE_UCI.equals(selected(mode));
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
        // cards beside it. Per-move budget and threads now live per side, so
        // they are no longer duplicated here.
        row = settingsPair(body, c, "Openings", openings, "Seed", seed, row);
        settingsPair(body, c, "Max plies", maxPlies, "Workers", workers, row);
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
        lower.add(charts, BorderLayout.NORTH);
        lower.add(card("Games", gamesBrowser), BorderLayout.CENTER);
        lower.add(rawSection, BorderLayout.SOUTH);
        return lower;
    }

    /**
     * Installs command-preview refresh listeners.
     */
    private void installPreviewRefresh() {
        for (JTextField field : List.of(featuresA, featuresB, openings, seed, maxPlies,
                workers, threadsA, threadsB, engineA, engineB, hashA, hashB, optionsA, optionsB,
                budgetValueA, budgetValueB)) {
            onTextChange(this::refreshCommandPreview, field);
        }
        // engineMode and budgetType combos refresh through their own listeners
        // (mode re-lays the card; budgetType lives in budgetControl).
        for (JComboBox<String> combo : List.of(searchA, searchB, evalA, evalB)) {
            combo.addActionListener(event -> refreshCommandPreview());
        }
    }

    /**
     * Keeps policy/value evaluator choices on the search that can use them
     * directly.
     */
    private void installEvaluatorSearchDefaults() {
        evalA.addActionListener(event -> preferPolicySearch(evalA, searchA));
        evalB.addActionListener(event -> preferPolicySearch(evalB, searchB));
    }

    /**
     * Selects MCTS when a policy/value backend is chosen.
     *
     * @param eval evaluator combo
     * @param search search combo for the same side
     */
    private static void preferPolicySearch(JComboBox<String> eval, JComboBox<String> search) {
        if (policyEvaluator(selected(eval))) {
            search.setSelectedItem("mcts");
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
        if (!numericFieldsValid()) {
            return;
        }
        List<String> command = buildCommand(currentConfig());
        outputBuffer.setLength(0);
        progress.clear();
        seenGameIndices.clear();
        gamesBrowser.clear();
        liveWins = 0;
        liveDraws = 0;
        liveLosses = 0;
        runGames = gamesFromConfig(currentConfig());
        runStartNanos = System.nanoTime();
        lastResult = GauntletResultSummary.running(runGames);
        outputArea.setText("$ " + CommandRunner.displayCommand(command) + System.lineSeparator());
        outputBuffer.append(outputArea.getText());
        updateResultView(lastResult, progress);
        setRunning(true);
        Ui.setCollapsibleExpanded(rawSection, true);
        startLiveTimer();
        running = CommandRunner.run(command, null, this::appendOutput, this::onCompleted, this::onFailed);
    }

    /**
     * Starts the one-second ticker that refreshes the live metrics (chiefly the
     * elapsed duration) between games while a gauntlet runs.
     */
    private void startLiveTimer() {
        stopLiveTimer();
        liveTimer = new javax.swing.Timer(1000, event -> {
            if (running != null && running.isRunning()) {
                lastResult = liveSummary();
                updateResultView(lastResult, progress);
            }
        });
        liveTimer.start();
    }

    /**
     * Stops the live ticker if it is running.
     */
    private void stopLiveTimer() {
        if (liveTimer != null) {
            liveTimer.stop();
            liveTimer = null;
        }
    }

    /**
     * Builds a live result summary from the running per-game tally.
     *
     * @return live summary
     */
    private GauntletResultSummary liveSummary() {
        long millis = (System.nanoTime() - runStartNanos) / 1_000_000L;
        return GauntletResultSummary.live(liveWins, liveDraws, liveLosses, runGames, millis);
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
        parseGames(outputBuffer.toString());
        updateResultView(lastResult, progress);
    }

    /**
     * Parses any newly completed {@code GAME} stream lines: adds each game's
     * thumbnail to the live gallery, folds its result into the running tally, and
     * appends a running-score point so the cards and cumulative chart advance
     * game by game. De-duplicates by game index so re-scanned lines never count
     * twice.
     *
     * @param output accumulated raw output
     */
    private void parseGames(String output) {
        boolean added = false;
        int from = 0;
        while (true) {
            int newline = output.indexOf('\n', from);
            if (newline < 0) {
                break;
            }
            String line = output.substring(from, newline);
            from = newline + 1;
            if (!line.startsWith("GAME\t")) {
                continue;
            }
            GauntletGameBrowser.Game game = GauntletGameBrowser.Game.parse(line);
            if (game != null && seenGameIndices.add(game.index())) {
                gamesBrowser.addGame(game);
                tallyGame(game.result());
                progress.add(new GauntletProgressPoint(liveWins, liveDraws, liveLosses));
                added = true;
            }
        }
        if (added) {
            lastResult = liveSummary();
        }
    }

    /**
     * Folds one game result into the running candidate-perspective tally.
     *
     * @param result {@code win}, {@code draw}, or {@code loss}
     */
    private void tallyGame(String result) {
        switch (result) {
            case "win" -> liveWins++;
            case "loss" -> liveLosses++;
            default -> liveDraws++;
        }
    }

    /**
     * Updates panel state when the command completes.
     *
     * @param result command result
     */
    private void onCompleted(CommandRunner.CommandResult result) {
        stopLiveTimer();
        setRunning(false);
        lastResult = GauntletResultSummary.parse(result.output(), result.exitCode(), result.millis());
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
        stopLiveTimer();
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
        stopLiveTimer();
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
        this.runActive = active;
        saveResultButton.setEnabled(!active && outputBuffer.length() > 0);
        stopButton.setEnabled(active);
        stopButton.setVisible(active);
        statusLabel.setText(active ? "Running" : "Ready");
        if (active) {
            statusBadge.running("running");
        }
        refreshRunEnabled();
        refreshExperimentCards();
    }

    /**
     * Installs live type validation on the numeric run-settings fields. Each
     * field flashes an error border and an explanatory tooltip the moment its
     * text stops being a whole number, and the change re-evaluates whether the
     * gauntlet can launch.
     */
    private void installFieldValidation() {
        addNumberValidator(openings, 1);
        addNumberValidator(seed, 0);
        addNumberValidator(maxPlies, 1);
        addNumberValidator(workers, 1);
        addNumberValidator(threadsA, 1);
        addNumberValidator(threadsB, 1);
        // The budget unit is chosen by a combo, so the value is a plain number
        // (nodes, or milliseconds when the unit is Time).
        addNumberValidator(budgetValueA, 1);
        addNumberValidator(budgetValueB, 1);
        // Hash is an optional MB count; blank means the engine's own default.
        addNumberValidator(hashA, 1);
        addNumberValidator(hashB, 1);
    }

    /**
     * Attaches a whole-number validator to one field. Blank is accepted because
     * each field falls back to a built-in default when left empty.
     *
     * @param field numeric field
     * @param min smallest accepted value
     */
    private void addNumberValidator(JTextField field, long min) {
        numericValidators.add(FieldValidator.attach(field,
                FieldValidator.wholeNumber(min, Long.MAX_VALUE, true),
                this::refreshRunEnabled));
    }

    /**
     * Returns whether every numeric run-settings field holds a usable value.
     *
     * @return true when all numeric fields are valid
     */
    private boolean numericFieldsValid() {
        for (FieldValidator validator : numericValidators) {
            if (!validator.valid()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Enables the run controls only when the settings are valid and idle.
     */
    private void refreshRunEnabled() {
        boolean valid = numericFieldsValid();
        runButton.setEnabled(valid && !runActive);
        runAgainButton.setEnabled(valid && !runActive && lastResult.games() > 0);
    }

    /**
     * Updates static experiment cards and header context.
     */
    private void refreshExperimentCards() {
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
        return switch (eval == null ? "" : eval) {
            case "nnue" -> "local NNUE / HalfKP";
            case "cnn" -> "LC0 CNN policy/value";
            case "bt4" -> "LC0 BT4 transformer policy/value";
            case "otis" -> "OTIS policy/WDL";
            default -> "none (classical eval)";
        };
    }

    /**
     * Returns whether an evaluator label is a policy/value MCTS backend.
     *
     * @param eval evaluator label
     * @return true for policy/value neural backends
     */
    private static boolean policyEvaluator(String eval) {
        return switch (eval == null ? "" : eval) {
            case "cnn", "lc0", "bt4", "otis" -> true;
            default -> false;
        };
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
            case "Eval" -> "Evaluator backend: classical terms, NNUE, LC0 CNN, BT4, or OTIS.";
            case "Features" -> "Alpha-beta feature set used by the candidate or baseline.";
            case "Engine" -> "Engine source for this side: the built-in searcher or an external UCI engine.";
            case "UCI command" -> "Command that launches the external UCI engine, e.g. /usr/bin/stockfish. "
                    + "Use Browse to pick it and Test to verify it responds.";
            case "Threads" -> "Threads for this side — built-in search threads, or the engine's Threads option for UCI.";
            case "Hash (MB)" -> "Transposition-table size sent to the UCI engine. Blank uses its default.";
            case "Options" -> "Extra UCI options as name=value, separated by ';' (e.g. SyzygyPath=/tb; UCI_Elo=2200).";
            case "Max plies" -> "Maximum half-moves before draw adjudication.";
            case "Workers" -> "Parallel opening-pair workers.";
            case "Threads A", "Threads B" -> "Search threads assigned to each side.";
            case "Budget" -> "Per-move budget for this side. Pick Nodes or Time (ms), then enter the amount.";
            default -> null;
        };
    }

    /**
     * Updates visible result metrics and charts.
     *
     * @param result parsed result
     * @param points running W-D-L points
     */
    private void updateResultView(GauntletResultSummary result, List<GauntletProgressPoint> points) {
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
                text(openings, "8"),
                text(seed, "20260531"),
                text(maxPlies, "160"),
                text(workers, "1"),
                text(threadsA, "1"),
                text(threadsB, "1"),
                externalOnly(engineModeA, engineA),
                externalOnly(engineModeB, engineB),
                externalOnly(engineModeA, hashA),
                externalOnly(engineModeB, hashB),
                externalOnly(engineModeA, optionsA),
                externalOnly(engineModeB, optionsB),
                budgetText(budgetTypeA, budgetValueA),
                budgetText(budgetTypeB, budgetValueB));
    }

    /**
     * Returns a field's text only when the side is in External mode, so
     * external-engine settings never leak into a built-in run.
     *
     * @param mode engine-source selector
     * @param field external-only field
     * @return field text when external, otherwise empty
     */
    private static String externalOnly(JComboBox<String> mode, JTextField field) {
        return isExternal(mode) ? optional(field) : "";
    }

    /**
     * Returns the per-side budget string, encoding a time budget with an
     * {@code ms} suffix and a node budget as a plain number.
     *
     * @param type budget-unit selector
     * @param value budget-value field
     * @return budget string
     */
    private static String budgetText(JComboBox<String> type, JTextField value) {
        String amount = text(value, "3000");
        return BUDGET_TIME.equals(selected(type)) ? amount + "ms" : amount;
    }

    /**
     * Returns trimmed field text, or an empty string when blank. Unlike
     * {@link #text(JTextField, String)} this never substitutes a default, so an
     * optional control left blank stays blank.
     *
     * @param field source field
     * @return trimmed text, or empty
     */
    private static String optional(JTextField field) {
        String value = field.getText();
        return value == null ? "" : value.trim();
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
     * @param openings seeded opening count
     * @param seed opening seed
     * @param maxPlies draw-adjudication ply cap
     * @param workers opening-pair workers
     * @param threadsA candidate threads
     * @param threadsB baseline threads
     * @param engineA candidate external UCI engine command, or empty
     * @param engineB baseline external UCI engine command, or empty
     * @param hashA candidate external engine hash size (MB), or empty
     * @param hashB baseline external engine hash size (MB), or empty
     * @param optionsA candidate external engine extra UCI options, or empty
     * @param optionsB baseline external engine extra UCI options, or empty
     * @param budgetA candidate per-move budget override, or empty
     * @param budgetB baseline per-move budget override, or empty
     */
    public record GauntletConfig(
            String featuresA,
            String featuresB,
            String searchA,
            String searchB,
            String evalA,
            String evalB,
            String openings,
            String seed,
            String maxPlies,
            String workers,
            String threadsA,
            String threadsB,
            String engineA,
            String engineB,
            String hashA,
            String hashB,
            String optionsA,
            String optionsB,
            String budgetA,
            String budgetB) {
    }
}
