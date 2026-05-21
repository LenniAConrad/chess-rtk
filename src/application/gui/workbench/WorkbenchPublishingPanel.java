package application.gui.workbench;

import static application.gui.workbench.WorkbenchUi.button;
import static application.gui.workbench.WorkbenchUi.buttonRow;
import static application.gui.workbench.WorkbenchUi.changeListener;
import static application.gui.workbench.WorkbenchUi.collapsible;
import static application.gui.workbench.WorkbenchUi.constraints;
import static application.gui.workbench.WorkbenchUi.fillViewport;
import static application.gui.workbench.WorkbenchUi.flow;
import static application.gui.workbench.WorkbenchUi.grid;
import static application.gui.workbench.WorkbenchUi.iconButton;
import static application.gui.workbench.WorkbenchUi.label;
import static application.gui.workbench.WorkbenchUi.onTextChange;
import static application.gui.workbench.WorkbenchUi.placeholder;
import static application.gui.workbench.WorkbenchUi.scroll;
import static application.gui.workbench.WorkbenchUi.styleAreas;
import static application.gui.workbench.WorkbenchUi.styleCheckBox;
import static application.gui.workbench.WorkbenchUi.styleCombos;
import static application.gui.workbench.WorkbenchUi.styleFields;
import static application.gui.workbench.WorkbenchUi.transparentPanel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Publishing command builder and preview panel.
 */
final class WorkbenchPublishingPanel {

    /**
     * Placeholder path used in command previews for generated PGN files.
     */
    private static final String WORKBENCH_GAME_PLACEHOLDER = "<workbench-game.pgn>";

    /**
     * Placeholder path used in command previews for generated FEN files.
     */
    private static final String WORKBENCH_FENS_PLACEHOLDER = "<workbench-fens.txt>";

    /**
     * Default diagram PDF output path.
     */
    private static final String DEFAULT_DIAGRAMS_OUTPUT = "workbench-diagrams.pdf";

    /**
     * Default interior PDF output path.
     */
    private static final String DEFAULT_BOOK_OUTPUT = "workbench-book.pdf";

    /**
     * Default cover PDF output path.
     */
    private static final String DEFAULT_COVER_OUTPUT = "workbench-cover.pdf";

    /**
     * Default manifest output path.
     */
    private static final String DEFAULT_MANIFEST_OUTPUT = "workbench-book.toml";

    /**
     * Display value for optional publishing combos that should not emit a flag.
     */
    private static final String PUBLISH_DEFAULT_CHOICE = "Default";

    /**
     * Page-size values accepted by the book PDF/study commands.
     */
    private static final String[] PUBLISH_PAGE_SIZE_CHOICES = {
            PUBLISH_DEFAULT_CHOICE, "a4", "a5", "letter"
    };

    /**
     * Cover bindings accepted by the native cover writer.
     */
    private static final String[] PUBLISH_BINDING_CHOICES = {
            PUBLISH_DEFAULT_CHOICE, "paperback", "hardcover", "ebook"
    };

    /**
     * Cover interior tokens accepted by the native cover writer.
     */
    private static final String[] PUBLISH_INTERIOR_CHOICES = {
            PUBLISH_DEFAULT_CHOICE,
            "white-bw",
            "white-standard-color",
            "white-premium-color",
            "cream-bw"
    };

    /**
     * Book language names accepted by the collection builder.
     */
    private static final String[] PUBLISH_LANGUAGE_CHOICES = {
            PUBLISH_DEFAULT_CHOICE,
            "English",
            "German",
            "Spanish",
            "French",
            "Chinese",
            "Russian",
            "Italian",
            "Turkish",
            "SwissGerman",
            "Japanese",
            "Hebrew",
            "Portuguese",
            "Korean",
            "Arabic"
    };

    /**
     * Services supplied by the frame.
     */
    interface Host {

        Component owner();

        String currentFen();

        WorkbenchGameModel gameModel();

        String batchInputText();

        JComponent reportPanel();

        void generateReport();

        void runCommand(List<String> args, String stdin);

        void copyText(String text);

        void stopCommand();

        void toast(WorkbenchToast.Kind kind, String message);

        void showError(String title, String message);
    }

    /**
     * Host callbacks.
     */
    private final Host host;

    /**
     * Root component.
     */
    private final JComponent component;

    /**
     * Whether a command preview refresh is already queued.
     */
    private boolean publishCommandUpdateQueued;

    /**
     * Publishing task selector.
     */
    private final JComboBox<WorkbenchPublishTask> publishTaskCombo = new JComboBox<>(WorkbenchPublishTask.values());

    /**
     * Per-task hint stating what input the selected book task needs.
     */
    private final JLabel publishTaskHint = WorkbenchUi.caption("");

    /**
     * Diagram publishing source selector.
     */
    private final JComboBox<WorkbenchPublishSource> publishSourceCombo = new JComboBox<>(WorkbenchPublishSource.values());

    /**
     * Publishing input row label.
     */
    private final JLabel publishInputLabel = label("input");

    /**
     * Publishing input path field.
     */
    private final JTextField publishInputField = new JTextField();

    /**
     * Publishing primary output row label.
     */
    private final JLabel publishOutputLabel = label("output");

    /**
     * Publishing primary output path field.
     */
    private final JTextField publishOutputField = new JTextField(DEFAULT_DIAGRAMS_OUTPUT);

    /**
     * Publishing normalized-manifest output row label.
     */
    private final JLabel publishManifestOutputLabel = label("manifest copy");

    /**
     * Publishing normalized-manifest output path field.
     */
    private final JTextField publishManifestOutputField = new JTextField();

    /**
     * Publishing interior PDF row label.
     */
    private final JLabel publishPdfOutputLabel = label("interior pdf");

    /**
     * Publishing PDF or interior path field.
     */
    private final JTextField publishPdfOutputField = new JTextField();

    /**
     * Publishing cover output row label.
     */
    private final JLabel publishCoverOutputLabel = label("cover pdf");

    /**
     * Publishing cover output path field.
     */
    private final JTextField publishCoverOutputField = new JTextField(DEFAULT_COVER_OUTPUT);

    /**
     * Publishing title field.
     */
    private final JTextField publishTitleField = new JTextField("ChessRTK Workbench");

    /**
     * Publishing subtitle field.
     */
    private final JTextField publishSubtitleField = new JTextField();

    /**
     * Publishing author override field.
     */
    private final JTextField publishAuthorField = new JTextField();

    /**
     * Publishing time/date override field.
     */
    private final JTextField publishTimeField = new JTextField();

    /**
     * Publishing location override field.
     */
    private final JTextField publishLocationField = new JTextField();

    /**
     * Publishing language selector.
     */
    private final JComboBox<String> publishLanguageCombo = new JComboBox<>(PUBLISH_LANGUAGE_CHOICES);

    /**
     * Publishing limit field.
     */
    private final JTextField publishLimitField = new JTextField();

    /**
     * Publishing page-count field.
     */
    private final JTextField publishPagesField = new JTextField();

    /**
     * Publishing page-size selector.
     */
    private final JComboBox<String> publishPageSizeCombo = new JComboBox<>(PUBLISH_PAGE_SIZE_CHOICES);

    /**
     * Publishing diagrams-per-row field.
     */
    private final JTextField publishDiagramsPerRowField = new JTextField();

    /**
     * Publishing board raster size field.
     */
    private final JTextField publishBoardPixelsField = new JTextField();

    /**
     * Publishing study margin override field.
     */
    private final JTextField publishMarginField = new JTextField();

    /**
     * Publishing collection table-frequency field.
     */
    private final JTextField publishTableFrequencyField = new JTextField();

    /**
     * Publishing collection puzzle-row field.
     */
    private final JTextField publishPuzzleRowsField = new JTextField();

    /**
     * Publishing collection puzzle-column field.
     */
    private final JTextField publishPuzzleColumnsField = new JTextField();

    /**
     * Publishing binding selector.
     */
    private final JComboBox<String> publishBindingCombo = new JComboBox<>(PUBLISH_BINDING_CHOICES);

    /**
     * Publishing cover interior selector.
     */
    private final JComboBox<String> publishInteriorCombo = new JComboBox<>(PUBLISH_INTERIOR_CHOICES);

    /**
     * Publishing validation toggle.
     */
    private final JCheckBox publishValidateBox = withTooltip(new WorkbenchToggleBox("validate only"),
            "Adds --check: validate inputs without writing output");

    /**
     * Returns the toggle after attaching a tooltip describing the underlying
     * CLI flag, since the chip labels alone are too terse to discover.
     *
     * @param toggle target toggle
     * @param tooltip tooltip text
     * @return the same toggle for fluent field initialization
     */
    private static <T extends JCheckBox> T withTooltip(T toggle, String tooltip) {
        toggle.setToolTipText(tooltip);
        return toggle;
    }

    /**
     * Publishing board-orientation toggle.
     */
    private final JCheckBox publishFlipBox = withTooltip(new WorkbenchToggleBox("black down"),
            "Adds --flip: render diagrams with Black at the bottom");

    /**
     * Publishing FEN visibility toggle.
     */
    private final JCheckBox publishNoFenBox = withTooltip(new WorkbenchToggleBox("hide FEN"),
            "Adds --no-fen: omit FEN captions under generated diagrams");

    /**
     * Publishing watermark toggle.
     */
    private final JCheckBox publishWatermarkBox = withTooltip(new WorkbenchToggleBox("watermark"),
            "Adds --free-watermark for render and collection PDFs");

    /**
     * Publishing watermark identifier.
     */
    private final JTextField publishWatermarkIdField = new JTextField();

    /**
     * Repeated collection imprint lines.
     */
    private final JTextArea publishImprintArea = new JTextArea(2, 28);

    /**
     * Repeated collection dedication lines.
     */
    private final JTextArea publishDedicationArea = new JTextArea(2, 28);

    /**
     * Repeated collection introduction paragraphs.
     */
    private final JTextArea publishIntroductionArea = new JTextArea(2, 28);

    /**
     * Repeated collection how-to-read paragraphs.
     */
    private final JTextArea publishHowToReadArea = new JTextArea(2, 28);

    /**
     * Repeated cover blurb lines.
     */
    private final JTextArea publishBlurbArea = new JTextArea(2, 28);

    /**
     * Repeated cover link lines.
     */
    private final JTextArea publishLinkArea = new JTextArea(2, 28);

    /**
     * Repeated collection afterword paragraphs.
     */
    private final JTextArea publishAfterwordArea = new JTextArea(2, 28);

    /**
     * Publishing command preview field.
     */
    private final JTextArea publishCommandField = new JTextArea(3, 40);

    /**
     * Human-readable publishing preview.
     */
    private final JTextArea publishPreview = new JTextArea();

    /**
     * Visual publishing preview.
     */
    private final WorkbenchPublishPreview publishVisualPreview = new WorkbenchPublishPreview();

    /**
     * Publishing preview page label.
     */
    private final JLabel publishPreviewPageLabel = new JLabel("page 1 / 1");

    /**
     * Publishing readiness label.
     */
    private final JLabel publishReadinessLabel = new JLabel("Ready");

    /**
     * Publishing input chooser button.
     */
    private JButton publishInputButton;

    /**
     * Publishing output chooser button.
     */
    private JButton publishOutputButton;

    /**
     * Publishing normalized-manifest chooser button.
     */
    private JButton publishManifestOutputButton;

    /**
     * Publishing PDF chooser button.
     */
    private JButton publishPdfOutputButton;

    /**
     * Publishing cover chooser button.
     */
    private JButton publishCoverOutputButton;


    /**
     * Creates the publishing panel.
     *
     * @param host host callbacks
     */
    WorkbenchPublishingPanel(Host host) {
        this.host = host;
        installFieldPlaceholders();
        this.component = createPublishTab();
    }

    /**
     * Returns the root component.
     *
     * @return root component
     */
    JComponent component() {
        return component;
    }

    /**
     * Refreshes the command preview immediately.
     */
    void updateCommand() {
        updatePublishCommand();
    }

    /**
     * Queues a command preview refresh.
     */
    void requestCommandUpdate() {
        requestPublishCommandUpdate();
    }

    /**
     * Runs the current publishing workflow.
     */
    void runCommand() {
        runPublishingCommand();
    }

    /**
     * Adds empty-field examples for publishing inputs.
     */
    private void installFieldPlaceholders() {
        placeholder(publishInputField, "path/to/input.pgn or path/to/fens.txt");
        placeholder(publishOutputField, "path/to/output.pdf");
        placeholder(publishManifestOutputField, "path/to/normalized-study.toml");
        placeholder(publishPdfOutputField, "path/to/interior.pdf");
        placeholder(publishCoverOutputField, "path/to/cover.pdf");
        placeholder(publishTitleField, "e.g. My Chess Study");
        placeholder(publishSubtitleField, "e.g. Annotated tactics");
        placeholder(publishAuthorField, "e.g. Lennart A. Conrad");
        placeholder(publishTimeField, "e.g. 2026");
        placeholder(publishLocationField, "e.g. Hamburg");
        placeholder(publishLimitField, "e.g. 50");
        placeholder(publishPagesField, "e.g. 120");
        placeholder(publishDiagramsPerRowField, "e.g. 2");
        placeholder(publishBoardPixelsField, "e.g. 900");
        placeholder(publishMarginField, "e.g. 1.6");
        placeholder(publishTableFrequencyField, "default 6");
        placeholder(publishPuzzleRowsField, "default 5");
        placeholder(publishPuzzleColumnsField, "default 4");
        placeholder(publishWatermarkIdField, "optional trace ID");
        placeholder(publishImprintArea, "one --imprint line per row");
        placeholder(publishDedicationArea, "one --dedication line per row");
        placeholder(publishIntroductionArea, "one --introduction paragraph per row");
        placeholder(publishHowToReadArea, "one --how-to-read paragraph per row");
        placeholder(publishBlurbArea, "one --blurb line per row");
        placeholder(publishLinkArea, "one --link line per row");
        placeholder(publishAfterwordArea, "one --afterword paragraph per row");
    }

    /**
     * Creates the publishing and report tab.
     *
     * @return publish tab
     */
    private JComponent createPublishTab() {
        JPanel panel = transparentPanel(new BorderLayout(10, 10));
        panel.add(createBookPublishingPanel(), BorderLayout.CENTER);
        configurePublishControls();
        updatePublishControlState();
        host.generateReport();
        updatePublishCommand();
        return panel;
    }

    /**
     * Creates the position and game report panel.
     *
     * @return report panel
     */
    private JComponent createReportPanel() {
        return host.reportPanel();
    }

    /**
     * Creates the book publishing command panel.
     *
     * @return publishing panel
     */
    private JComponent createBookPublishingPanel() {
        JPanel root = transparentPanel(new BorderLayout(10, 10));
        JComponent controls = scroll(fillViewport(createPublishingControlsPanel()));
        JComponent preview = createPublishingPreviewPanel();
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controls, preview);
        split.setResizeWeight(0.56);
        split.setDividerLocation(0.56);
        split.setDividerSize(8);
        split.setContinuousLayout(true);
        WorkbenchSplitPanes.style(split);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    /**
     * Creates publishing input controls.
     *
     * @return controls panel
     */
    private JPanel createPublishingControlsPanel() {
        JPanel panel = transparentPanel(new GridBagLayout());
        panel.setBorder(WorkbenchTheme.pad(8, 8, 8, 8));
        GridBagConstraints c = constraints();
        int row = 0;
        JPanel buildHeader = transparentPanel(new BorderLayout(0, 2));
        buildHeader.add(WorkbenchTheme.section("Publishing"), BorderLayout.NORTH);
        buildHeader.add(WorkbenchUi.caption(
                "Task-aware book output: every enabled field maps directly to a supported CRTK book flag."),
                BorderLayout.SOUTH);
        grid(panel, buildHeader, c, 0, row++, 4, 1);

        styleCombos(publishTaskCombo, publishSourceCombo, publishPageSizeCombo, publishBindingCombo,
                publishInteriorCombo, publishLanguageCombo);
        grid(panel, label("task"), c, 0, row, 1, 1);
        JPanel taskCell = transparentPanel(new BorderLayout(0, 3));
        taskCell.add(publishTaskCombo, BorderLayout.NORTH);
        taskCell.add(publishTaskHint, BorderLayout.SOUTH);
        grid(panel, taskCell, c, 1, row++, 3, 1);

        grid(panel, label("source"), c, 0, row, 1, 1);
        grid(panel, publishSourceCombo, c, 1, row++, 3, 1);

        publishInputField.setColumns(28);
        publishOutputField.setColumns(28);
        publishManifestOutputField.setColumns(28);
        publishPdfOutputField.setColumns(28);
        publishCoverOutputField.setColumns(28);
        publishTitleField.setColumns(28);
        publishSubtitleField.setColumns(28);
        publishAuthorField.setColumns(28);
        publishTimeField.setColumns(12);
        publishLocationField.setColumns(12);
        publishLimitField.setColumns(8);
        publishPagesField.setColumns(8);
        publishDiagramsPerRowField.setColumns(8);
        publishBoardPixelsField.setColumns(8);
        publishMarginField.setColumns(8);
        publishTableFrequencyField.setColumns(8);
        publishPuzzleRowsField.setColumns(8);
        publishPuzzleColumnsField.setColumns(8);
        publishWatermarkIdField.setColumns(18);
        styleFields(publishInputField, publishOutputField, publishPdfOutputField, publishCoverOutputField,
                publishManifestOutputField, publishTitleField, publishSubtitleField, publishAuthorField,
                publishTimeField, publishLocationField, publishLimitField, publishPagesField,
                publishDiagramsPerRowField, publishBoardPixelsField, publishMarginField,
                publishTableFrequencyField, publishPuzzleRowsField, publishPuzzleColumnsField,
                publishWatermarkIdField);
        styleAreas(publishCommandField, publishImprintArea, publishDedicationArea, publishIntroductionArea,
                publishHowToReadArea, publishBlurbArea, publishLinkArea, publishAfterwordArea);
        configurePublishingArea(publishCommandField, false);
        configurePublishingArea(publishImprintArea, true);
        configurePublishingArea(publishDedicationArea, true);
        configurePublishingArea(publishIntroductionArea, true);
        configurePublishingArea(publishHowToReadArea, true);
        configurePublishingArea(publishBlurbArea, true);
        configurePublishingArea(publishLinkArea, true);
        configurePublishingArea(publishAfterwordArea, true);

        row = addPublishingSection(panel, c, "Files", row);
        publishInputButton = addChooserRow(panel, c, publishInputLabel, publishInputField, "Choose Input", row++,
                () -> WorkbenchFileDialogs.choosePath(host.owner(), publishInputField, false, "Choose publishing input"));
        publishOutputButton = addChooserRow(panel, c, publishOutputLabel, publishOutputField, "Choose Output", row++,
                () -> WorkbenchFileDialogs.choosePath(host.owner(), publishOutputField, true, "Choose publishing output"));
        publishManifestOutputButton = addChooserRow(panel, c, publishManifestOutputLabel,
                publishManifestOutputField, "Choose Manifest", row++,
                () -> WorkbenchFileDialogs.choosePath(host.owner(), publishManifestOutputField, true, "Choose manifest output"));
        publishPdfOutputButton = addChooserRow(panel, c, publishPdfOutputLabel, publishPdfOutputField, "Choose PDF",
                row++,
                this::choosePublishPdfPath);
        publishCoverOutputButton = addChooserRow(panel, c, publishCoverOutputLabel, publishCoverOutputField,
                "Choose Cover", row++,
                () -> WorkbenchFileDialogs.choosePath(host.owner(), publishCoverOutputField, true, "Choose cover output"));

        row = addPublishingSection(panel, c, "Details", row);
        row = addPublishingControlRow(panel, c, label("title"), publishTitleField, row);
        row = addPublishingControlRow(panel, c, label("subtitle"), publishSubtitleField, row);
        row = addPublishingControlRow(panel, c, label("author"), publishAuthorField, row);
        row = addPublishingControlRow(panel, c, label("date/place"),
                compactPublishingRow(label("time"), publishTimeField, label("location"), publishLocationField), row);
        row = addPublishingControlRow(panel, c, label("language"), publishLanguageCombo, row);

        row = addPublishingSection(panel, c, "Layout", row);
        row = addPublishingControlRow(panel, c, label("diagram"),
                compactPublishingRow(label("page size"), publishPageSizeCombo, label("per row"),
                        publishDiagramsPerRowField),
                row);
        row = addPublishingControlRow(panel, c, label("board"),
                compactPublishingRow(label("pixels"), publishBoardPixelsField, label("margin"), publishMarginField),
                row);
        row = addPublishingControlRow(panel, c, label("count"),
                compactPublishingRow(label("limit"), publishLimitField, label("pages"), publishPagesField), row);
        row = addPublishingControlRow(panel, c, label("collection"),
                compactPublishingRow(label("table"), publishTableFrequencyField, label("rows"), publishPuzzleRowsField,
                        label("cols"), publishPuzzleColumnsField),
                row);
        row = addPublishingControlRow(panel, c, label("cover"),
                compactPublishingRow(label("binding"), publishBindingCombo, label("interior"), publishInteriorCombo),
                row);

        JPanel toggles = flow(FlowLayout.LEFT);
        toggles.add(publishValidateBox);
        toggles.add(publishFlipBox);
        toggles.add(publishNoFenBox);
        toggles.add(publishWatermarkBox);
        row = addPublishingControlRow(panel, c, label("options"), toggles, row);
        row = addPublishingControlRow(panel, c, label("watermark id"), publishWatermarkIdField, row);

        row = addPublishingControlRow(panel, c, label("front matter"),
                collapsible("Copy", createPublishingFrontMatterPanel(), false), row);
        row = addPublishingControlRow(panel, c, label("report"),
                collapsible("Position Report", createReportPanel(), false), row);

        row = addPublishingSection(panel, c, "Command", row);
        publishCommandField.setEditable(false);
        publishCommandField.setFocusable(false);
        publishCommandField.setToolTipText("Generated publishing command");
        grid(panel, label("preview"), c, 0, row, 1, 1);
        grid(panel, scroll(publishCommandField), c, 1, row++, 3, 1);

        grid(panel, buttonRow(FlowLayout.LEFT,
                button("Create PDF", true, event -> runPublishingCommand()),
                button("Copy Command", false, event -> host.copyText(publishCommandField.getText())),
                button("Copy Preview", false, event -> copyPublishingPreview()),
                button("Stop", false, event -> host.stopCommand())), c, 1, row++, 3, 1);
        addVerticalFiller(panel, c, row, 4);
        return panel;
    }

    /**
     * Configures a publishing multi-line text control.
     *
     * @param area target area
     * @param editable whether the user edits the area
     */
    private static void configurePublishingArea(JTextArea area, boolean editable) {
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(editable);
        area.setRows(editable ? 2 : 3);
        area.setMinimumSize(new Dimension(220, editable ? 58 : 76));
    }

    /**
     * Adds a visual section label to the publishing form.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param title section title
     * @param row target row
     * @return next row
     */
    private static int addPublishingSection(JPanel panel, GridBagConstraints c, String title, int row) {
        grid(panel, WorkbenchTheme.section(title), c, 0, row, 4, 1);
        return row + 1;
    }

    /**
     * Adds one publishing control row.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param rowLabel row label
     * @param control row control
     * @param row target row
     * @return next row
     */
    private static int addPublishingControlRow(JPanel panel, GridBagConstraints c, JLabel rowLabel,
            JComponent control, int row) {
        grid(panel, rowLabel, c, 0, row, 1, 1);
        grid(panel, control, c, 1, row, 3, 1);
        return row + 1;
    }

    /**
     * Creates a compact inline row for related publishing controls.
     *
     * @param components alternating labels and controls
     * @return row panel
     */
    private static JPanel compactPublishingRow(JComponent... components) {
        JPanel row = flow(FlowLayout.LEFT);
        for (JComponent component : components) {
            row.add(component);
        }
        return row;
    }

    /**
     * Creates the advanced repeated-text controls for book commands.
     *
     * @return front-matter panel
     */
    private JComponent createPublishingFrontMatterPanel() {
        JPanel panel = transparentPanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        int row = 0;
        row = addPublishingTextArea(panel, c, "imprint", publishImprintArea, row);
        row = addPublishingTextArea(panel, c, "dedication", publishDedicationArea, row);
        row = addPublishingTextArea(panel, c, "introduction", publishIntroductionArea, row);
        row = addPublishingTextArea(panel, c, "how to read", publishHowToReadArea, row);
        row = addPublishingTextArea(panel, c, "blurb", publishBlurbArea, row);
        row = addPublishingTextArea(panel, c, "link", publishLinkArea, row);
        addPublishingTextArea(panel, c, "afterword", publishAfterwordArea, row);
        return panel;
    }

    /**
     * Adds one repeated-text area row.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param labelText row label
     * @param area text area
     * @param row target row
     * @return next row
     */
    private static int addPublishingTextArea(JPanel panel, GridBagConstraints c, String labelText, JTextArea area,
            int row) {
        grid(panel, label(labelText), c, 0, row, 1, 1);
        grid(panel, scroll(area), c, 1, row, 3, 1);
        return row + 1;
    }

    /**
     * Creates the visual publishing preview panel.
     *
     * @return preview panel
     */
    private JComponent createPublishingPreviewPanel() {
        JPanel panel = new WorkbenchSurfacePanel(new BorderLayout(8, 8));
        panel.add(WorkbenchTheme.section("Preview"), BorderLayout.NORTH);

        publishReadinessLabel.setFont(WorkbenchTheme.font(12, Font.BOLD));
        publishPreviewPageLabel.setFont(WorkbenchTheme.mono(11));
        publishPreviewPageLabel.setForeground(WorkbenchTheme.MUTED);
        JPanel toolbar = transparentPanel(new BorderLayout(8, 0));
        toolbar.add(publishReadinessLabel, BorderLayout.CENTER);
        JPanel pageControls = flow(FlowLayout.RIGHT);
        pageControls.add(iconButton("Back", event -> previousPublishPreviewPage()));
        pageControls.add(publishPreviewPageLabel);
        pageControls.add(iconButton("Forward", event -> nextPublishPreviewPage()));
        toolbar.add(pageControls, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.SOUTH);

        styleAreas(publishPreview);
        publishPreview.setRows(7);
        publishPreview.setLineWrap(true);
        publishPreview.setWrapStyleWord(true);
        publishPreview.setEditable(false);

        JSplitPane previewSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, publishVisualPreview,
                scroll(publishPreview));
        previewSplit.setResizeWeight(0.72);
        previewSplit.setDividerLocation(0.72);
        previewSplit.setDividerSize(8);
        previewSplit.setContinuousLayout(true);
        WorkbenchSplitPanes.style(previewSplit);
        panel.add(previewSplit, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Adds a publishing path row with a matching chooser button.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param labelText row label
     * @param field path field
     * @param buttonText chooser button text
     * @param row grid row
     * @param chooserAction chooser action
     * @return chooser button
     */
    private JButton addChooserRow(JPanel panel, GridBagConstraints c, String labelText, JTextField field,
            String buttonText, int row, Runnable chooserAction) {
        return addChooserRow(panel, c, label(labelText), field, buttonText, row, chooserAction);
    }

    /**
     * Adds a publishing path row with a matching chooser button.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param rowLabel row label
     * @param field path field
     * @param buttonText chooser button text
     * @param row grid row
     * @param chooserAction chooser action
     * @return chooser button
     */
    private JButton addChooserRow(JPanel panel, GridBagConstraints c, JLabel rowLabel, JTextField field,
            String buttonText, int row, Runnable chooserAction) {
        grid(panel, rowLabel, c, 0, row, 1, 1);
        grid(panel, field, c, 1, row, 2, 1);
        JButton chooserButton = button(buttonText, false, event -> chooserAction.run());
        chooserButton.setText("");
        chooserButton.setToolTipText(buttonText);
        chooserButton.getAccessibleContext().setAccessibleName(buttonText);
        chooserButton.setPreferredSize(new Dimension(34, 34));
        chooserButton.setMinimumSize(new Dimension(34, 34));
        grid(panel, chooserButton, c, 3, row, 1, 1);
        return chooserButton;
    }

    /**
     * Installs publishing control listeners and checkbox styling.
     */
    private void configurePublishControls() {
        publishTaskCombo.addActionListener(event -> {
            applyDefaultPublishingOutputs();
            updatePublishControlState();
            updatePublishCommand();
        });
        publishSourceCombo.addActionListener(event -> {
            updatePublishControlState();
            updatePublishCommand();
        });
        publishPageSizeCombo.addActionListener(event -> updatePublishCommand());
        publishBindingCombo.addActionListener(event -> updatePublishCommand());
        publishInteriorCombo.addActionListener(event -> updatePublishCommand());
        publishLanguageCombo.addActionListener(event -> updatePublishCommand());
        publishValidateBox.addActionListener(event -> updatePublishCommand());
        publishFlipBox.addActionListener(event -> updatePublishCommand());
        publishNoFenBox.addActionListener(event -> updatePublishCommand());
        publishWatermarkBox.addActionListener(event -> updatePublishCommand());
        onTextChange(this::requestPublishCommandUpdate, publishInputField, publishOutputField, publishPdfOutputField,
                publishCoverOutputField, publishTitleField, publishSubtitleField, publishLimitField,
                publishPagesField, publishManifestOutputField, publishAuthorField, publishTimeField,
                publishLocationField, publishDiagramsPerRowField, publishBoardPixelsField, publishMarginField,
                publishTableFrequencyField, publishPuzzleRowsField, publishPuzzleColumnsField,
                publishWatermarkIdField);
        onAreaChange(this::requestPublishCommandUpdate, publishImprintArea, publishDedicationArea,
                publishIntroductionArea, publishHowToReadArea, publishBlurbArea, publishLinkArea,
                publishAfterwordArea);
        styleCheckBox(publishValidateBox);
        styleCheckBox(publishFlipBox);
        styleCheckBox(publishNoFenBox);
        styleCheckBox(publishWatermarkBox);
    }

    /**
     * Adds one document-change callback to multiple text areas.
     *
     * @param runnable callback
     * @param areas text areas
     */
    private static void onAreaChange(Runnable runnable, JTextArea... areas) {
        for (JTextArea area : areas) {
            area.getDocument().addDocumentListener(changeListener(runnable));
        }
    }

    /**
     * Updates publishing controls so irrelevant fields are visually disabled.
     */
    private void updatePublishControlState() {
        WorkbenchPublishTask task = selectedPublishTask();
        boolean diagrams = task == WorkbenchPublishTask.DIAGRAMS;
        boolean render = task == WorkbenchPublishTask.RENDER;
        boolean collection = task == WorkbenchPublishTask.COLLECTION;
        boolean study = task == WorkbenchPublishTask.STUDY;
        boolean cover = task == WorkbenchPublishTask.COVER;
        boolean existingDiagramInput = diagrams && selectedPublishSource() == WorkbenchPublishSource.EXISTING_FILE;
        publishTaskHint.setText(switch (task) {
            case DIAGRAMS -> "book pdf: one FEN source or PGN/FEN file -> diagram PDF.";
            case RENDER -> "book render: existing book TOML/JSON manifest -> PDF.";
            case COLLECTION -> "book collection: solved record JSON/JSONL -> manifest, interior PDF, and cover.";
            case STUDY -> "book study: study TOML/JSON manifest -> annotated PDF, optional manifest copy and cover.";
            case COVER -> "book cover: book manifest plus optional interior PDF -> printable cover.";
        });

        publishSourceCombo.setEnabled(diagrams);
        publishInputField.setEnabled(!diagrams || existingDiagramInput);
        publishOutputLabel.setText(switch (task) {
            case DIAGRAMS, RENDER, STUDY -> "pdf";
            case COLLECTION -> "manifest";
            case COVER -> "cover pdf";
        });
        publishInputLabel.setText(switch (task) {
            case DIAGRAMS -> existingDiagramInput ? "diagram file" : "generated";
            case RENDER, STUDY, COVER -> "manifest";
            case COLLECTION -> "records";
        });
        publishPdfOutputLabel.setText("interior pdf");
        publishCoverOutputLabel.setText("cover pdf");
        publishManifestOutputField.setEnabled(study);
        publishPdfOutputField.setEnabled(collection || cover);
        publishCoverOutputField.setEnabled(collection || study);
        publishSubtitleField.setEnabled(!diagrams);
        publishAuthorField.setEnabled(collection || study);
        publishTimeField.setEnabled(collection || study);
        publishLocationField.setEnabled(collection || study);
        publishLanguageCombo.setEnabled(collection);
        publishLimitField.setEnabled(render || collection);
        publishPagesField.setEnabled(collection || study || cover);
        publishPageSizeCombo.setEnabled(diagrams || study);
        publishDiagramsPerRowField.setEnabled(diagrams || study);
        publishBoardPixelsField.setEnabled(diagrams || study);
        publishMarginField.setEnabled(study);
        publishTableFrequencyField.setEnabled(collection);
        publishPuzzleRowsField.setEnabled(collection);
        publishPuzzleColumnsField.setEnabled(collection);
        publishBindingCombo.setEnabled(collection || study || cover);
        publishInteriorCombo.setEnabled(collection || study || cover);
        publishValidateBox.setEnabled(!diagrams);
        publishFlipBox.setEnabled(diagrams || study);
        publishNoFenBox.setEnabled(diagrams || study);
        publishWatermarkBox.setEnabled(render || collection);
        publishWatermarkIdField.setEnabled(render || collection);
        setTextAreaEnabled(publishImprintArea, collection);
        setTextAreaEnabled(publishDedicationArea, collection);
        setTextAreaEnabled(publishIntroductionArea, collection);
        setTextAreaEnabled(publishHowToReadArea, collection);
        setTextAreaEnabled(publishBlurbArea, collection || study);
        setTextAreaEnabled(publishLinkArea, collection || study);
        setTextAreaEnabled(publishAfterwordArea, collection);
        setButtonEnabled(publishInputButton, publishInputField.isEnabled());
        setButtonEnabled(publishOutputButton, publishOutputField.isEnabled());
        setButtonEnabled(publishManifestOutputButton, publishManifestOutputField.isEnabled());
        setButtonEnabled(publishPdfOutputButton, publishPdfOutputField.isEnabled());
        setButtonEnabled(publishCoverOutputButton, publishCoverOutputField.isEnabled());
    }

    /**
     * Enables or disables a publishing text area.
     *
     * @param area text area
     * @param enabled target enabled state
     */
    private static void setTextAreaEnabled(JTextArea area, boolean enabled) {
        area.setEnabled(enabled);
        area.setEditable(enabled);
    }

    /**
     * Enables a lazily-created button when it is available.
     *
     * @param button button, or null before panel creation
     * @param enabled target enabled state
     */
    private static void setButtonEnabled(JButton button, boolean enabled) {
        if (button != null) {
            button.setEnabled(enabled);
        }
    }

    /**
     * Applies a sensible output default when switching publishing tasks.
     */
    private void applyDefaultPublishingOutputs() {
        String current = trimmed(publishOutputField);
        if (!current.isEmpty() && !isKnownPublishDefault(current)) {
            return;
        }
        WorkbenchPublishTask task = selectedPublishTask();
        switch (task) {
            case DIAGRAMS -> publishOutputField.setText(DEFAULT_DIAGRAMS_OUTPUT);
            case COLLECTION -> {
                publishOutputField.setText(DEFAULT_MANIFEST_OUTPUT);
                setDefaultWhenBlank(publishPdfOutputField, DEFAULT_BOOK_OUTPUT);
                setDefaultWhenBlank(publishCoverOutputField, DEFAULT_COVER_OUTPUT);
            }
            case COVER -> publishOutputField.setText(DEFAULT_COVER_OUTPUT);
            case STUDY -> {
                publishOutputField.setText(DEFAULT_BOOK_OUTPUT);
                setDefaultWhenBlank(publishCoverOutputField, DEFAULT_COVER_OUTPUT);
            }
            case RENDER -> publishOutputField.setText(DEFAULT_BOOK_OUTPUT);
        }
    }

    /**
     * Sets a text field to a default value when it is blank.
     *
     * @param field field to update
     * @param value default value
     */
    private static void setDefaultWhenBlank(JTextField field, String value) {
        if (trimmed(field).isEmpty()) {
            field.setText(value);
        }
    }

    /**
     * Returns whether a path is one of the workbench publish defaults.
     *
     * @param value path text
     * @return true when the text is a known default output
     */
    private static boolean isKnownPublishDefault(String value) {
        return DEFAULT_DIAGRAMS_OUTPUT.equals(value)
                || DEFAULT_BOOK_OUTPUT.equals(value)
                || DEFAULT_COVER_OUTPUT.equals(value)
                || DEFAULT_MANIFEST_OUTPUT.equals(value);
    }

    /**
     * Updates the publishing command preview.
     */
    private void updatePublishCommand() {
        publishCommandUpdateQueued = false;
        String command = "";
        String issue = publishPreflightIssue();
        try {
            command = WorkbenchCommandRunner.displayCommand(buildPublishArgs(false));
            publishCommandField.setText(command);
        } catch (IllegalArgumentException | IOException ex) {
            issue = ex.getMessage();
            command = "incomplete: " + issue;
            publishCommandField.setText(command);
        }
        publishPreview.setText(buildPublishingPreview(command, issue));
        publishPreview.setCaretPosition(0);
        updatePublishingVisualPreview(issue);
    }

    /**
     * Moves publishing preview to the previous page.
     */
    private void previousPublishPreviewPage() {
        publishVisualPreview.previousPage();
        updatePublishPreviewPageLabel();
    }

    /**
     * Moves publishing preview to the next page.
     */
    private void nextPublishPreviewPage() {
        publishVisualPreview.nextPage();
        updatePublishPreviewPageLabel();
    }

    /**
     * Updates the visual publishing preview.
     *
     * @param issue readiness issue, or null
     */
    private void updatePublishingVisualPreview(String issue) {
        WorkbenchPublishTask task = selectedPublishTask();
        WorkbenchPublishPreview.Preview preview = new WorkbenchPublishPreview.Preview(
                task.toString(),
                trimmed(publishTitleField),
                trimmed(publishSubtitleField),
                publishSourcePreview(task),
                publishOutputPreview(task),
                issue == null,
                issue == null ? "" : issue,
                estimatedPublishPages(task),
                task == WorkbenchPublishTask.COVER,
                task == WorkbenchPublishTask.DIAGRAMS,
                publishFlipBox.isSelected(),
                publishNoFenBox.isSelected());
        publishVisualPreview.setPreview(preview);
        publishReadinessLabel.setText(issue == null ? "Ready to publish" : "Needs attention");
        publishReadinessLabel.setToolTipText(issue == null ? "Publishing command is ready" : issue);
        publishReadinessLabel.setForeground(issue == null ? WorkbenchTheme.STATUS_SUCCESS_TEXT
                : WorkbenchTheme.STATUS_WARNING_TEXT);
        updatePublishPreviewPageLabel();
    }

    /**
     * Updates visual preview page label.
     */
    private void updatePublishPreviewPageLabel() {
        publishPreviewPageLabel.setText(publishVisualPreview.pageLabel());
    }

    /**
     * Copies the publishing preview after flushing pending edits.
     */
    private void copyPublishingPreview() {
        updatePublishCommand();
        host.copyText(publishPreview.getText());
        host.toast(WorkbenchToast.Kind.SUCCESS, "Publishing preview copied");
    }

    /**
     * Builds a human-readable publishing preview.
     *
     * @param command command text
     * @param issue readiness issue, or null
     * @return preview text
     */
    private String buildPublishingPreview(String command, String issue) {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder(768);
        WorkbenchPublishTask task = selectedPublishTask();
        sb.append("Workflow: ").append(task).append(newline);
        sb.append("Status: ").append(issue == null ? "ready" : "needs attention - " + issue).append(newline);
        sb.append("Source: ").append(publishSourcePreview(task)).append(newline);
        sb.append("Output: ").append(publishOutputPreview(task)).append(newline);
        appendPublishingTextLine(sb, "Title", trimmed(publishTitleField), newline);
        if (task != WorkbenchPublishTask.DIAGRAMS) {
            appendPublishingTextLine(sb, "Subtitle", trimmed(publishSubtitleField), newline);
        }
        if (task == WorkbenchPublishTask.COLLECTION || task == WorkbenchPublishTask.STUDY) {
            appendPublishingTextLine(sb, "Author", trimmed(publishAuthorField), newline);
        }
        sb.append("Options: ").append(publishOptionsPreview(task)).append(newline);
        sb.append("Command: ").append(command);
        return sb.toString();
    }

    /**
     * Returns a source summary for the selected publishing task.
     *
     * @param task selected task
     * @return source summary
     */
    private String publishSourcePreview(WorkbenchPublishTask task) {
        if (task != WorkbenchPublishTask.DIAGRAMS) {
            return pathOrMissing("input file", publishInputField);
        }
        return switch (selectedPublishSource()) {
            case CURRENT_FEN -> "current board FEN (" + compactFenPreview(host.currentFen()) + ")";
            case GAME_PGN -> host.gameModel().lastPly() <= 0
                    ? "workbench game PGN (no moves)"
                    : "workbench game PGN (" + host.gameModel().lastPly() + " ply)";
            case BATCH_FENS -> batchFenPreview();
            case EXISTING_FILE -> pathOrMissing("diagram input file", publishInputField);
        };
    }

    /**
     * Returns an output summary for the selected publishing task.
     *
     * @param task selected task
     * @return output summary
     */
    private String publishOutputPreview(WorkbenchPublishTask task) {
        return switch (task) {
            case DIAGRAMS, RENDER, STUDY -> pathOrMissing("PDF", publishOutputField)
                    + optionalOutput("manifest copy", publishManifestOutputField, task == WorkbenchPublishTask.STUDY)
                    + optionalOutput("cover", publishCoverOutputField, task == WorkbenchPublishTask.STUDY);
            case COLLECTION -> pathOrMissing("manifest", publishOutputField)
                    + optionalOutput("interior PDF", publishPdfOutputField, true)
                    + optionalOutput("cover", publishCoverOutputField, true);
            case COVER -> pathOrMissing("cover PDF", publishOutputField)
                    + optionalOutput("interior PDF", publishPdfOutputField, true);
        };
    }

    /**
     * Returns relevant publishing option summary.
     *
     * @param task selected task
     * @return option summary
     */
    private String publishOptionsPreview(WorkbenchPublishTask task) {
        List<String> options = new ArrayList<>();
        addPreviewOption(options, publishValidateBox.isSelected() && task != WorkbenchPublishTask.DIAGRAMS, "validate only");
        addPreviewOption(options, publishFlipBox.isSelected() && (task == WorkbenchPublishTask.DIAGRAMS
                || task == WorkbenchPublishTask.STUDY), "black down");
        addPreviewOption(options, publishNoFenBox.isSelected() && (task == WorkbenchPublishTask.DIAGRAMS
                || task == WorkbenchPublishTask.STUDY), "hide FEN");
        addPreviewOption(options, (task == WorkbenchPublishTask.RENDER || task == WorkbenchPublishTask.COLLECTION)
                && !trimmed(publishLimitField).isEmpty(), "limit " + trimmed(publishLimitField));
        addPreviewOption(options, (task == WorkbenchPublishTask.COLLECTION || task == WorkbenchPublishTask.STUDY
                || task == WorkbenchPublishTask.COVER) && !trimmed(publishPagesField).isEmpty(),
                "pages " + trimmed(publishPagesField));
        addPreviewOption(options, (task == WorkbenchPublishTask.DIAGRAMS || task == WorkbenchPublishTask.STUDY)
                && !comboValue(publishPageSizeCombo).isEmpty(), "page " + comboValue(publishPageSizeCombo));
        addPreviewOption(options, (task == WorkbenchPublishTask.DIAGRAMS || task == WorkbenchPublishTask.STUDY)
                && !trimmed(publishDiagramsPerRowField).isEmpty(),
                trimmed(publishDiagramsPerRowField) + " diagrams/row");
        addPreviewOption(options, (task == WorkbenchPublishTask.DIAGRAMS || task == WorkbenchPublishTask.STUDY)
                && !trimmed(publishBoardPixelsField).isEmpty(),
                trimmed(publishBoardPixelsField) + " px boards");
        addPreviewOption(options, task == WorkbenchPublishTask.STUDY && !trimmed(publishMarginField).isEmpty(),
                "margin " + trimmed(publishMarginField));
        addPreviewOption(options, task == WorkbenchPublishTask.COLLECTION && !trimmed(publishTableFrequencyField).isEmpty(),
                "table every " + trimmed(publishTableFrequencyField));
        addPreviewOption(options, task == WorkbenchPublishTask.COLLECTION && !trimmed(publishPuzzleRowsField).isEmpty(),
                trimmed(publishPuzzleRowsField) + " puzzle rows");
        addPreviewOption(options, task == WorkbenchPublishTask.COLLECTION && !trimmed(publishPuzzleColumnsField).isEmpty(),
                trimmed(publishPuzzleColumnsField) + " puzzle columns");
        addPreviewOption(options, (task == WorkbenchPublishTask.COLLECTION || task == WorkbenchPublishTask.STUDY
                || task == WorkbenchPublishTask.COVER) && !comboValue(publishBindingCombo).isEmpty(),
                "binding " + comboValue(publishBindingCombo));
        addPreviewOption(options, (task == WorkbenchPublishTask.COLLECTION || task == WorkbenchPublishTask.STUDY
                || task == WorkbenchPublishTask.COVER) && !comboValue(publishInteriorCombo).isEmpty(),
                "interior " + comboValue(publishInteriorCombo));
        addPreviewOption(options, task == WorkbenchPublishTask.COLLECTION && !comboValue(publishLanguageCombo).isEmpty(),
                "language " + comboValue(publishLanguageCombo));
        addPreviewOption(options, (task == WorkbenchPublishTask.RENDER || task == WorkbenchPublishTask.COLLECTION)
                && publishWatermarkBox.isSelected(), "free watermark");
        addPreviewOption(options, (task == WorkbenchPublishTask.RENDER || task == WorkbenchPublishTask.COLLECTION)
                && !trimmed(publishWatermarkIdField).isEmpty(), "watermark ID");
        return options.isEmpty() ? "default" : String.join(", ", options);
    }

    /**
     * Returns the selected combo value unless it is the non-emitting default.
     *
     * @param combo combo box
     * @return command-line value or empty string
     */
    private static String comboValue(JComboBox<String> combo) {
        Object selected = combo.getSelectedItem();
        if (!(selected instanceof String value) || PUBLISH_DEFAULT_CHOICE.equals(value)) {
            return "";
        }
        return value.trim();
    }

    /**
     * Estimates pages for the visual publishing preview.
     *
     * @param task selected task
     * @return estimated page count
     */
    private int estimatedPublishPages(WorkbenchPublishTask task) {
        Integer explicitPages = optionalPreviewInteger(publishPagesField);
        if (explicitPages != null && (task == WorkbenchPublishTask.COLLECTION || task == WorkbenchPublishTask.STUDY
                || task == WorkbenchPublishTask.COVER)) {
            return explicitPages.intValue();
        }
        return switch (task) {
            case COVER -> 1;
            case DIAGRAMS -> estimatedDiagramPages();
            case RENDER -> Math.max(1, optionalPreviewInteger(publishLimitField, 12) / 2);
            case COLLECTION -> Math.max(8, optionalPreviewInteger(publishLimitField, 64));
            case STUDY -> Math.max(12, host.gameModel().lastPly() + 8);
        };
    }

    /**
     * Estimates diagram-page count.
     *
     * @return page count
     */
    private int estimatedDiagramPages() {
        return switch (selectedPublishSource()) {
            case CURRENT_FEN -> 1;
            case GAME_PGN -> Math.max(1, Math.max(1, host.gameModel().lastPly()) / 2);
            case BATCH_FENS -> Math.max(1, WorkbenchFenInput.validateBatchFenInput(host.batchInputText()).validRows());
            case EXISTING_FILE -> 6;
        };
    }

    /**
     * Parses an optional preview integer.
     *
     * @param field source field
     * @return parsed value or null
     */
    private static Integer optionalPreviewInteger(JTextField field) {
        String value = trimmed(field);
        if (!value.matches("[1-9]\\d*")) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Parses an optional preview integer with fallback.
     *
     * @param field source field
     * @param fallback fallback value
     * @return parsed or fallback
     */
    private static int optionalPreviewInteger(JTextField field, int fallback) {
        Integer value = optionalPreviewInteger(field);
        return value == null ? fallback : value.intValue();
    }

    /**
     * Returns a preflight issue for generated workbench input sources.
     *
     * @return issue text or null
     */
    private String publishPreflightIssue() {
        if (selectedPublishTask() != WorkbenchPublishTask.DIAGRAMS) {
            return null;
        }
        return switch (selectedPublishSource()) {
            case GAME_PGN -> host.gameModel().lastPly() <= 0
                    ? "Play or import at least one game move before exporting PGN diagrams." : null;
            case BATCH_FENS -> batchFenIssue();
            case CURRENT_FEN, EXISTING_FILE -> null;
        };
    }

    /**
     * Returns a compact batch FEN preview.
     *
     * @return batch source summary
     */
    private String batchFenPreview() {
        String text = host.batchInputText() == null ? "" : host.batchInputText().trim();
        if (text.isEmpty()) {
            return host.gameModel().lastPly() <= 0 ? "batch FENs (empty)" : "game FEN list (" + host.gameModel().lastPly() + " ply)";
        }
        WorkbenchFenInput.Summary scan = WorkbenchFenInput.validateBatchFenInput(text);
        if (scan.hasError()) {
            return scan.rows() + " row" + (scan.rows() == 1 ? "" : "s")
                    + ", issue on line " + scan.firstErrorLine();
        }
        return scan.validRows() + " valid FEN row" + (scan.validRows() == 1 ? "" : "s");
    }

    /**
     * Returns a batch source issue, if present.
     *
     * @return issue text or null
     */
    private String batchFenIssue() {
        String text = host.batchInputText() == null ? "" : host.batchInputText().trim();
        if (text.isEmpty()) {
            return host.gameModel().lastPly() <= 0 ? "Add FEN rows in Batch or play a game line before exporting diagrams."
                    : null;
        }
        WorkbenchFenInput.Summary scan = WorkbenchFenInput.validateBatchFenInput(text);
        return scan.hasError() ? "Batch FEN line " + scan.firstErrorLine() + ": " + scan.firstError() : null;
    }

    /**
     * Appends a non-empty publishing text line.
     *
     * @param sb target builder
     * @param label line label
     * @param value line value
     * @param newline line separator
     */
    private static void appendPublishingTextLine(StringBuilder sb, String label, String value, String newline) {
        if (!value.isEmpty()) {
            sb.append(label).append(": ").append(value).append(newline);
        }
    }

    /**
     * Adds one preview option when active.
     *
     * @param options target options
     * @param active whether option applies
     * @param text option text
     */
    private static void addPreviewOption(List<String> options, boolean active, String text) {
        if (active) {
            options.add(text);
        }
    }

    /**
     * Returns a required path preview.
     *
     * @param label path label
     * @param field source field
     * @return preview text
     */
    private static String pathOrMissing(String label, JTextField field) {
        String value = trimmed(field);
        return value.isEmpty() ? "missing " + label : label + " " + value;
    }

    /**
     * Returns optional output text when enabled and present.
     *
     * @param label path label
     * @param field source field
     * @param enabled whether this output applies to the selected task
     * @return optional preview text
     */
    private static String optionalOutput(String label, JTextField field, boolean enabled) {
        String value = trimmed(field);
        return enabled && !value.isEmpty() ? "; " + label + " " + value : "";
    }

    /**
     * Runs the selected publishing command.
     */
    private void runPublishingCommand() {
        try {
            host.runCommand(buildPublishArgs(true), null);
        } catch (IllegalArgumentException | IOException ex) {
            host.showError("Publishing command failed", ex.getMessage());
        }
    }

    /**
     * Builds publishing command arguments.
     *
     * @param materialize whether generated workbench inputs should be written to temporary files
     * @return CRTK arguments
     * @throws IOException on temporary input creation failure
     */
    private List<String> buildPublishArgs(boolean materialize) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("book");
        WorkbenchPublishTask task = selectedPublishTask();
        switch (task) {
            case DIAGRAMS -> buildDiagramPublishArgs(args, materialize);
            case RENDER -> buildRenderPublishArgs(args);
            case COLLECTION -> buildCollectionPublishArgs(args);
            case STUDY -> buildStudyPublishArgs(args);
            case COVER -> buildCoverPublishArgs(args);
        }
        return List.copyOf(args);
    }

    /**
     * Builds {@code book pdf} arguments.
     *
     * @param args target arguments
     * @param materialize whether generated inputs should be written
     * @throws IOException on temporary input creation failure
     */
    private void buildDiagramPublishArgs(List<String> args, boolean materialize) throws IOException {
        args.add("pdf");
        WorkbenchPublishSource source = selectedPublishSource();
        switch (source) {
            case CURRENT_FEN -> {
                args.add("--fen");
                args.add(host.currentFen());
            }
            case GAME_PGN -> {
                args.add("--pgn");
                args.add(materialize ? materializeWorkbenchPgn().toString() : WORKBENCH_GAME_PLACEHOLDER);
            }
            case BATCH_FENS -> {
                args.add("--input");
                args.add(materialize ? materializeWorkbenchFens().toString() : WORKBENCH_FENS_PLACEHOLDER);
            }
            case EXISTING_FILE -> addBookPdfFileInput(args, requiredText(publishInputField, "diagram input path"));
        }
        addRequiredTextArg(args, "--output", publishOutputField, "output path");
        addOptionalTextArg(args, "--title", publishTitleField);
        addOptionalComboArg(args, "--page-size", publishPageSizeCombo);
        addOptionalPositiveIntegerArg(args, "--diagrams-per-row", publishDiagramsPerRowField);
        addOptionalPositiveIntegerArg(args, "--board-pixels", publishBoardPixelsField);
        addToggleArg(args, "--flip", publishFlipBox.isSelected());
        addToggleArg(args, "--no-fen", publishNoFenBox.isSelected());
    }

    /**
     * Builds {@code book render} arguments.
     *
     * @param args target arguments
     */
    private void buildRenderPublishArgs(List<String> args) {
        args.add("render");
        addRequiredTextArg(args, "--input", publishInputField, "manifest input path");
        addOptionalTextArg(args, "--output", publishOutputField);
        addOptionalTextArg(args, "--title", publishTitleField);
        addOptionalTextArg(args, "--subtitle", publishSubtitleField);
        addOptionalPositiveIntegerArg(args, "--limit", publishLimitField);
        addWatermarkArgs(args);
        addToggleArg(args, "--check", publishValidateBox.isSelected());
    }

    /**
     * Builds {@code book collection} arguments.
     *
     * @param args target arguments
     */
    private void buildCollectionPublishArgs(List<String> args) {
        args.add("collection");
        addRequiredTextArg(args, "--input", publishInputField, "record input path");
        addOptionalTextArg(args, "--output", publishOutputField);
        addOptionalTextArg(args, "--pdf-output", publishPdfOutputField);
        addOptionalTextArg(args, "--cover-output", publishCoverOutputField);
        addOptionalTextArg(args, "--title", publishTitleField);
        addOptionalTextArg(args, "--subtitle", publishSubtitleField);
        addOptionalTextArg(args, "--author", publishAuthorField);
        addOptionalTextArg(args, "--time", publishTimeField);
        addOptionalTextArg(args, "--location", publishLocationField);
        addOptionalComboArg(args, "--language", publishLanguageCombo);
        addOptionalPositiveIntegerArg(args, "--limit", publishLimitField);
        addOptionalPositiveIntegerArg(args, "--pages", publishPagesField);
        addCoverLayoutArgs(args);
        addOptionalPositiveIntegerArg(args, "--table-frequency", publishTableFrequencyField);
        addOptionalPositiveIntegerArg(args, "--puzzle-rows", publishPuzzleRowsField);
        addOptionalPositiveIntegerArg(args, "--puzzle-columns", publishPuzzleColumnsField);
        addRepeatedTextArgs(args, "--imprint", publishImprintArea);
        addRepeatedTextArgs(args, "--dedication", publishDedicationArea);
        addRepeatedTextArgs(args, "--introduction", publishIntroductionArea);
        addRepeatedTextArgs(args, "--how-to-read", publishHowToReadArea);
        addRepeatedTextArgs(args, "--blurb", publishBlurbArea);
        addRepeatedTextArgs(args, "--link", publishLinkArea);
        addRepeatedTextArgs(args, "--afterword", publishAfterwordArea);
        addWatermarkArgs(args);
        addToggleArg(args, "--check", publishValidateBox.isSelected());
    }

    /**
     * Builds {@code book study} arguments.
     *
     * @param args target arguments
     */
    private void buildStudyPublishArgs(List<String> args) {
        args.add("study");
        addRequiredTextArg(args, "--input", publishInputField, "study manifest input path");
        addOptionalTextArg(args, "--output", publishOutputField);
        addOptionalTextArg(args, "--manifest-output", publishManifestOutputField);
        addOptionalTextArg(args, "--cover-output", publishCoverOutputField);
        addOptionalTextArg(args, "--title", publishTitleField);
        addOptionalTextArg(args, "--subtitle", publishSubtitleField);
        addOptionalTextArg(args, "--author", publishAuthorField);
        addOptionalTextArg(args, "--time", publishTimeField);
        addOptionalTextArg(args, "--location", publishLocationField);
        addOptionalComboArg(args, "--page-size", publishPageSizeCombo);
        addOptionalPositiveDecimalArg(args, "--margin", publishMarginField);
        addOptionalPositiveIntegerArg(args, "--diagrams-per-row", publishDiagramsPerRowField);
        addOptionalPositiveIntegerArg(args, "--board-pixels", publishBoardPixelsField);
        addOptionalPositiveIntegerArg(args, "--pages", publishPagesField);
        addCoverLayoutArgs(args);
        addRepeatedTextArgs(args, "--blurb", publishBlurbArea);
        addRepeatedTextArgs(args, "--link", publishLinkArea);
        addToggleArg(args, "--flip", publishFlipBox.isSelected());
        addToggleArg(args, "--no-fen", publishNoFenBox.isSelected());
        addToggleArg(args, "--check", publishValidateBox.isSelected());
    }

    /**
     * Builds {@code book cover} arguments.
     *
     * @param args target arguments
     */
    private void buildCoverPublishArgs(List<String> args) {
        args.add("cover");
        addRequiredTextArg(args, "--input", publishInputField, "book manifest input path");
        addOptionalTextArg(args, "--pdf", publishPdfOutputField);
        addOptionalTextArg(args, "--output", publishOutputField);
        addOptionalTextArg(args, "--title", publishTitleField);
        addOptionalTextArg(args, "--subtitle", publishSubtitleField);
        addOptionalPositiveIntegerArg(args, "--pages", publishPagesField);
        addCoverLayoutArgs(args);
        addToggleArg(args, "--check", publishValidateBox.isSelected());
    }

    /**
     * Adds the correct diagram-file input flag for {@code book pdf}.
     *
     * @param args target arguments
     * @param input input path
     */
    private static void addBookPdfFileInput(List<String> args, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        args.add(lower.endsWith(".pgn") ? "--pgn" : "--input");
        args.add(input);
    }

    /**
     * Adds a required text option.
     *
     * @param args target arguments
     * @param flag option flag
     * @param field source field
     * @param label human-readable field label
     */
    private static void addRequiredTextArg(List<String> args, String flag, JTextField field, String label) {
        args.add(flag);
        args.add(requiredText(field, label));
    }

    /**
     * Adds an optional text option.
     *
     * @param args target arguments
     * @param flag option flag
     * @param field source field
     */
    private static void addOptionalTextArg(List<String> args, String flag, JTextField field) {
        String value = trimmed(field);
        if (!value.isEmpty()) {
            args.add(flag);
            args.add(value);
        }
    }

    /**
     * Adds an optional combo-box option when it is not set to the default item.
     *
     * @param args target arguments
     * @param flag option flag
     * @param combo combo source
     */
    private static void addOptionalComboArg(List<String> args, String flag, JComboBox<String> combo) {
        String value = comboValue(combo);
        if (!value.isEmpty()) {
            args.add(flag);
            args.add(value);
        }
    }

    /**
     * Adds an optional positive integer option.
     *
     * @param args target arguments
     * @param flag option flag
     * @param field source field
     */
    private static void addOptionalPositiveIntegerArg(List<String> args, String flag, JTextField field) {
        String value = trimmed(field);
        if (value.isEmpty()) {
            return;
        }
        if (!value.matches("[1-9]\\d*")) {
            throw new IllegalArgumentException(flag + " expects a positive integer.");
        }
        args.add(flag);
        args.add(value);
    }

    /**
     * Adds an optional positive decimal option.
     *
     * @param args target arguments
     * @param flag option flag
     * @param field source field
     */
    private static void addOptionalPositiveDecimalArg(List<String> args, String flag, JTextField field) {
        String value = trimmed(field);
        if (value.isEmpty()) {
            return;
        }
        if (!value.matches("(?:[1-9]\\d*|0?\\.\\d+|[1-9]\\d*\\.\\d+)")) {
            throw new IllegalArgumentException(flag + " expects a positive number.");
        }
        args.add(flag);
        args.add(value);
    }

    /**
     * Adds shared cover layout flags.
     *
     * @param args target arguments
     */
    private void addCoverLayoutArgs(List<String> args) {
        addOptionalComboArg(args, "--binding", publishBindingCombo);
        addOptionalComboArg(args, "--interior", publishInteriorCombo);
    }

    /**
     * Adds free-watermark options.
     *
     * @param args target arguments
     */
    private void addWatermarkArgs(List<String> args) {
        addToggleArg(args, "--free-watermark", publishWatermarkBox.isSelected());
        addOptionalTextArg(args, "--watermark-id", publishWatermarkIdField);
    }

    /**
     * Adds one option occurrence for each nonblank line in a text area.
     *
     * @param args target arguments
     * @param flag repeated option flag
     * @param area source area
     */
    private static void addRepeatedTextArgs(List<String> args, String flag, JTextArea area) {
        String text = area.getText() == null ? "" : area.getText();
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (!value.isEmpty()) {
                args.add(flag);
                args.add(value);
            }
        }
    }

    /**
     * Adds a toggle flag when selected.
     *
     * @param args target arguments
     * @param flag flag
     * @param selected whether the flag is selected
     */
    private static void addToggleArg(List<String> args, String flag, boolean selected) {
        if (selected) {
            args.add(flag);
        }
    }

    /**
     * Returns required trimmed text from a field.
     *
     * @param field source field
     * @param label human-readable field label
     * @return trimmed text
     */
    private static String requiredText(JTextField field, String label) {
        String value = trimmed(field);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing " + label + ".");
        }
        return value;
    }

    /**
     * Returns trimmed field text.
     *
     * @param field source field
     * @return trimmed text
     */
    private static String trimmed(JTextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    /**
     * Returns a short FEN label for compact previews.
     *
     * @param fen full FEN
     * @return piece placement plus side to move when available
     */
    private static String compactFenPreview(String fen) {
        if (fen == null || fen.isBlank()) {
            return "";
        }
        String[] parts = fen.trim().split("\\s+");
        return parts.length > 1 ? parts[0] + " " + parts[1] : parts[0];
    }

    /**
     * Writes the current workbench game to a temporary PGN file.
     *
     * @return temporary PGN file
     * @throws IOException on write failure
     */
    private Path materializeWorkbenchPgn() throws IOException {
        if (host.gameModel().lastPly() <= 0) {
            throw new IllegalArgumentException("Play or import at least one game move before exporting PGN.");
        }
        Path file = Files.createTempFile("crtk-workbench-game-", ".pgn");
        file.toFile().deleteOnExit();
        Files.writeString(file, host.gameModel().pgn() + System.lineSeparator(), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Writes batch FENs or the game FEN list to a temporary file.
     *
     * @return temporary FEN file
     * @throws IOException on write failure
     */
    private Path materializeWorkbenchFens() throws IOException {
        String text = host.batchInputText() == null ? "" : host.batchInputText().trim();
        if (text.isEmpty()) {
            text = host.gameModel().fenList();
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("Add FENs to the Batch tab first.");
        }
        Path file = Files.createTempFile("crtk-workbench-fens-", ".txt");
        file.toFile().deleteOnExit();
        Files.writeString(file, text + System.lineSeparator(), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Returns the selected publishing task.
     *
     * @return selected task label
     */
    private WorkbenchPublishTask selectedPublishTask() {
        WorkbenchPublishTask selected = (WorkbenchPublishTask) publishTaskCombo.getSelectedItem();
        return selected == null ? WorkbenchPublishTask.DIAGRAMS : selected;
    }

    /**
     * Returns the selected diagram source.
     *
     * @return selected source
     */
    private WorkbenchPublishSource selectedPublishSource() {
        WorkbenchPublishSource selected = (WorkbenchPublishSource) publishSourceCombo.getSelectedItem();
        return selected == null ? WorkbenchPublishSource.CURRENT_FEN : selected;
    }

    /**
     * Chooses a PDF path using the direction required by the selected task.
     */
    private void choosePublishPdfPath() {
        boolean save = selectedPublishTask() != WorkbenchPublishTask.COVER;
        String title = save ? "Choose PDF output" : "Choose interior PDF";
        WorkbenchFileDialogs.choosePath(host.owner(), publishPdfOutputField, save, title, new FileNameExtensionFilter("PDF document", "pdf"));
    }


    /**
     * Queues a publishing preview refresh after document edits settle.
     */
    private void requestPublishCommandUpdate() {
        if (publishCommandUpdateQueued) {
            return;
        }
        publishCommandUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            if (!publishCommandUpdateQueued) {
                return;
            }
            updatePublishCommand();
        });
    }

    /**
     * Adds a transparent filler row so stretched form panels keep controls at
     * the top of the work surface.
     *
     * @param panel target panel
     * @param c reusable constraints
     * @param row grid row
     * @param width grid width
     */
    private static void addVerticalFiller(JPanel panel, GridBagConstraints c, int row, int width) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = width;
        c.gridheight = 1;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        panel.add(transparentPanel(new BorderLayout()), c);
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
    }
}
