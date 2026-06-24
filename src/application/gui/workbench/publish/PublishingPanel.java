package application.gui.workbench.publish;

import static application.cli.Constants.OPT_BOARD_PIXELS;
import static application.cli.Constants.OPT_CHECK;
import static application.cli.Constants.OPT_COVER_OUTPUT;
import static application.cli.Constants.OPT_DIAGRAMS_PER_ROW;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_FLIP;
import static application.cli.Constants.OPT_FREE_WATERMARK;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_LIMIT;
import static application.cli.Constants.OPT_NO_FEN;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_PAGE_SIZE;
import static application.cli.Constants.OPT_PAGES;
import static application.cli.Constants.OPT_PDF;
import static application.cli.Constants.OPT_PDF_OUTPUT;
import static application.cli.Constants.OPT_PGN;
import static application.cli.Constants.OPT_SUBTITLE;
import static application.cli.Constants.OPT_TITLE;
import static application.cli.Constants.OPT_WATERMARK_ID;

import application.cli.PathOps;
import application.gui.workbench.Defaults;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.FieldValidator;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WorkspaceHeader;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
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
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import static application.gui.workbench.command.CommandArgs.addOptionalPositiveIntegerArg;
import static application.gui.workbench.command.CommandArgs.addOptionalTextArg;
import static application.gui.workbench.ui.Ui.addVerticalFiller;
import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.controlRow;
import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.collapsible;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.flow;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.iconButton;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.onTextChange;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleCheckBox;
import static application.gui.workbench.ui.Ui.styleCombos;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.transparentPanel;
import static application.gui.workbench.ui.Ui.trimmed;
import static application.gui.workbench.ui.Ui.withTooltip;

/**
 * Publishing command builder and preview panel.
 */
public final class PublishingPanel {

    /**
     * Placeholder path used in command previews for generated PGN files.
     */
    private static final String WORKBENCH_GAME_PLACEHOLDER = Defaults.WORKBENCH_GAME_PLACEHOLDER;

    /**
     * Placeholder path used in command previews for generated FEN files.
     */
    private static final String WORKBENCH_FENS_PLACEHOLDER = Defaults.WORKBENCH_FENS_PLACEHOLDER;

    /**
     * Default diagram PDF output path.
     */
    private static final String DEFAULT_DIAGRAMS_OUTPUT = Defaults.PUBLISH_DIAGRAMS_OUTPUT;

    /**
     * Default interior PDF output path.
     */
    private static final String DEFAULT_BOOK_OUTPUT = Defaults.PUBLISH_BOOK_OUTPUT;

    /**
     * Default cover PDF output path.
     */
    private static final String DEFAULT_COVER_OUTPUT = Defaults.PUBLISH_COVER_OUTPUT;

    /**
     * Default manifest output path.
     */
    private static final String DEFAULT_MANIFEST_OUTPUT = Defaults.PUBLISH_MANIFEST_OUTPUT;

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
    public interface Host {

        /**
         * Returns the owning component for dialogs.
         *
         * @return owner component
         */
    Component owner();

        /**
         * Returns the current board FEN.
         *
         * @return current FEN
         */
    String currentFen();

        /**
         * Returns the current game model.
         *
         * @return game model
         */
    GameModel gameModel();

        /**
         * Returns batch input text.
         *
         * @return batch input
         */
    String batchInputText();

        /**
         * Returns the report panel component.
         *
         * @return report panel
         */
    JComponent reportPanel();

        /**
         * Generates the current report.
         */
    void generateReport();

        /**
         * Runs a command.
         *
         * @param args command arguments
         * @param stdin standard input text
         */
    void runCommand(List<String> args, String stdin);

        /**
         * Copies text to the clipboard.
         *
         * @param text text to copy
         */
    void copyText(String text);

    /**
     * Stops the running command.
     */
    void stopCommand();

    /**
     * Creates a stop button that follows the foreground command lifecycle.
     *
     * @return command stop button
     */
    JComponent commandStopButton();

        /**
         * Shows a toast.
         *
         * @param kind toast kind
         * @param message toast message
         */
    void toast(Toast.Kind kind, String message);

        /**
         * Shows an error dialog.
         *
         * @param title dialog title
         * @param message dialog message
         */
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
    private final JComboBox<PublishTask> publishTaskCombo = new JComboBox<>(PublishTask.values());

    /**
     * Per-task hint stating what input the selected book task needs.
     */
    private final JLabel publishTaskHint = Ui.caption("");

    /**
     * Diagram publishing source selector.
     */
    private final JComboBox<PublishSource> publishSourceCombo = new JComboBox<>(PublishSource.values());

    /**
     * Publishing input row label.
     */
    private final JLabel publishInputLabel = label("input");

    /**
     * Required marker shown beside the input label when an input is mandatory.
     */
    private final JLabel publishInputStar = requiredStar();

    /**
     * Publishing input path field.
     */
    private final JTextField publishInputField = new JTextField();

    /**
     * Publishing primary output row label.
     */
    private final JLabel publishOutputLabel = label("output");

    /**
     * Required marker shown beside the output label when an output is mandatory.
     */
    private final JLabel publishOutputStar = requiredStar();

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
    private final JCheckBox publishValidateBox = withTooltip(new ToggleBox("validate only"),
            "Adds --check: validate inputs without writing output");

    /**
     * Publishing board-orientation toggle.
     */
    private final JCheckBox publishFlipBox = withTooltip(new ToggleBox("black down"),
            "Adds --flip: render diagrams with Black at the bottom");

    /**
     * Publishing FEN visibility toggle.
     */
    private final JCheckBox publishNoFenBox = withTooltip(new ToggleBox("hide FEN"),
            "Adds --no-fen: omit FEN captions under generated diagrams");

    /**
     * Publishing watermark toggle.
     */
    private final JCheckBox publishWatermarkBox = withTooltip(new ToggleBox("watermark"),
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

    /**
     * Visual publishing preview.
     */
    private final PublishPreview publishVisualPreview = new PublishPreview();

    /**
     * Publishing preview page label.
     */
    private final JLabel publishPreviewPageLabel = new JLabel("page 1 / 1");

    /**
     * Publishing preview zoom label.
     */
    private final JLabel publishPreviewZoomLabel = new JLabel("100%");

    /**
     * Publishing readiness label.
     */
    private final JLabel publishReadinessLabel = new JLabel("Ready");

    /**
     * Scroll container for the visual preview.
     */
    private JScrollPane publishPreviewScroll;

    /**
     * Publishing run button, disabled while the command cannot be built.
     */
    private JButton publishCreateButton;

    /**
     * Opens the current output PDF when it already exists.
     */
    private JButton publishOpenPdfButton;

    /**
     * Shared workspace header for the Publish surface.
     */
    private final WorkspaceHeader workspaceHeader = new WorkspaceHeader("Publish", "", null);

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
    public PublishingPanel(Host host) {
        this.host = host;
        installFieldPlaceholders();
        this.component = createPublishTab();
    }

    /**
     * Returns the root component.
     *
     * @return root component
     */
    public JComponent component() {
        return component;
    }

    /**
     * Refreshes the command preview immediately.
     */
    public void updateCommand() {
        updatePublishCommand();
    }

    /**
     * Queues a command preview refresh.
     */
    public void requestCommandUpdate() {
        requestPublishCommandUpdate();
    }

    /**
     * Runs the current publishing workflow.
     */
    public void runCommand() {
        runPublishingCommand();
    }

    /**
     * Adds empty-field examples for publishing inputs.
     */
    private void installFieldPlaceholders() {
        placeholder(publishInputField, "path/to/input.pgn or path/to/fens.txt");
        placeholder(publishOutputField, Defaults.PUBLISH_BOOK_OUTPUT);
        placeholder(publishManifestOutputField, Defaults.PUBLISH_MANIFEST_OUTPUT);
        placeholder(publishPdfOutputField, PathOps.dumpPath("workbench-interior.pdf").toString());
        placeholder(publishCoverOutputField, Defaults.PUBLISH_COVER_OUTPUT);
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
     * Installs live type validation on the numeric publishing fields. Each field
     * marks itself with an error border and an explanatory tooltip the moment
     * its text stops being the expected kind of number. Blank is accepted
     * throughout, since every field falls back to a built-in default. A disabled
     * field (one the current task ignores) never reports an error.
     */
    private void installNumericFieldValidation() {
        FieldValidator.Check positiveInteger = FieldValidator.wholeNumber(1, Long.MAX_VALUE, true);
        FieldValidator.attach(publishLimitField, positiveInteger);
        FieldValidator.attach(publishPagesField, positiveInteger);
        FieldValidator.attach(publishDiagramsPerRowField, positiveInteger);
        FieldValidator.attach(publishBoardPixelsField, positiveInteger);
        FieldValidator.attach(publishTableFrequencyField, positiveInteger);
        FieldValidator.attach(publishPuzzleRowsField, positiveInteger);
        FieldValidator.attach(publishPuzzleColumnsField, positiveInteger);
        FieldValidator.attach(publishMarginField,
                FieldValidator.decimal(0, Double.POSITIVE_INFINITY, true));
    }

    /**
     * Creates the publishing and report tab.
     *
     * @return publish tab
     */
    private JComponent createPublishTab() {
        SurfacePanel panel = new SurfacePanel(new BorderLayout(0, 0), Theme.Surface.BACKDROP);
        JComponent body = createBookPublishingPanel();
        workspaceHeader.setActions(createPublishActions());
        workspaceHeader.setContext(PublishingPreviewPlanner.contextLine(previewContext(), null));
        panel.add(workspaceHeader, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
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
        JPanel root = transparentPanel(new BorderLayout(0, 0));
        JComponent controls = scroll(fillViewport(createPublishingControlsPanel()));
        JComponent preview = createPublishingPreviewPanel();
        JSplitPane split = SplitPaneStyler.styledHorizontalSplit(controls, preview, 0.40);
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
        panel.setBorder(Theme.pad(Theme.SPACE_MD, Theme.SPACE_MD, Theme.SPACE_MD, Theme.SPACE_MD));
        GridBagConstraints c = constraints();
        int row = 0;

        styleCombos(publishTaskCombo, publishSourceCombo, publishPageSizeCombo, publishBindingCombo,
                publishInteriorCombo, publishLanguageCombo);
        applyPublishingFieldColumns();
        styleFields(publishInputField, publishOutputField, publishPdfOutputField, publishCoverOutputField,
                publishManifestOutputField, publishTitleField, publishSubtitleField, publishAuthorField,
                publishTimeField, publishLocationField, publishLimitField, publishPagesField,
                publishDiagramsPerRowField, publishBoardPixelsField, publishMarginField,
                publishTableFrequencyField, publishPuzzleRowsField, publishPuzzleColumnsField,
                publishWatermarkIdField);
        styleAreas(publishImprintArea, publishDedicationArea, publishIntroductionArea,
                publishHowToReadArea, publishBlurbArea, publishLinkArea, publishAfterwordArea);
        installNumericFieldValidation();
        configurePublishingArea(publishImprintArea, true);
        configurePublishingArea(publishDedicationArea, true);
        configurePublishingArea(publishIntroductionArea, true);
        configurePublishingArea(publishHowToReadArea, true);
        configurePublishingArea(publishBlurbArea, true);
        configurePublishingArea(publishLinkArea, true);
        configurePublishingArea(publishAfterwordArea, true);

        JPanel source = transparentPanel(new GridBagLayout());
        GridBagConstraints sourceC = constraints();
        int sectionRow = 0;
        sectionRow = addPublishingControlRow(source, sourceC, label("source"), publishSourceCombo, sectionRow);
        publishInputButton = addChooserRow(source, sourceC, requiredLabelCell(publishInputLabel, publishInputStar),
                publishInputField, "Choose Input", sectionRow,
                () -> FileDialogs.choosePath(host.owner(), publishInputField, false, "Choose publishing input"));
        grid(panel, fieldGroup("Source", source), c, 0, row++, 4, 1);

        JPanel template = transparentPanel(new GridBagLayout());
        GridBagConstraints templateC = constraints();
        sectionRow = 0;
        sectionRow = addPublishingControlRow(template, templateC, label("task"), publishTaskCombo, sectionRow);
        sectionRow = addPublishingControlRow(template, templateC, label("templates"),
                createPublishingTemplatePanel(), sectionRow);
        addPublishingControlRow(template, templateC, label("note"), publishTaskHint, sectionRow);
        grid(panel, fieldGroup("Template", template), c, 0, row++, 4, 1);

        JPanel options = transparentPanel(new GridBagLayout());
        GridBagConstraints optionsC = constraints();
        sectionRow = 0;
        JPanel toggles = flow(FlowLayout.LEFT);
        toggles.add(publishValidateBox);
        toggles.add(publishFlipBox);
        toggles.add(publishNoFenBox);
        toggles.add(publishWatermarkBox);
        sectionRow = addPublishingControlRow(options, optionsC, label("flags"), toggles, sectionRow);
        addPublishingControlRow(options, optionsC, label("watermark id"), publishWatermarkIdField, sectionRow);
        grid(panel, fieldGroup("Display Options", options), c, 0, row++, 4, 1);

        JPanel metadata = transparentPanel(new GridBagLayout());
        GridBagConstraints metadataC = constraints();
        sectionRow = 0;
        sectionRow = addPublishingControlRow(metadata, metadataC, label("title"), publishTitleField, sectionRow);
        sectionRow = addPublishingControlRow(metadata, metadataC, label("subtitle"), publishSubtitleField, sectionRow);
        sectionRow = addPublishingControlRow(metadata, metadataC, label("author"), publishAuthorField, sectionRow);
        sectionRow = addPublishingControlRow(metadata, metadataC, label("date/place"),
                compactPublishingRow(label("time"), publishTimeField, label("location"), publishLocationField),
                sectionRow);
        addPublishingControlRow(metadata, metadataC, label("language"), publishLanguageCombo, sectionRow);
        grid(panel, collapsible("Metadata", metadata, false), c, 0, row++, 4, 1);

        JPanel layout = transparentPanel(new GridBagLayout());
        GridBagConstraints layoutC = constraints();
        sectionRow = 0;
        sectionRow = addPublishingControlRow(layout, layoutC, label("diagram"),
                compactPublishingRow(label("page size"), publishPageSizeCombo, label("per row"),
                        publishDiagramsPerRowField),
                sectionRow);
        sectionRow = addPublishingControlRow(layout, layoutC, label("board"),
                compactPublishingRow(label("pixels"), publishBoardPixelsField, label("margin"), publishMarginField),
                sectionRow);
        sectionRow = addPublishingControlRow(layout, layoutC, label("count"),
                compactPublishingRow(label("limit"), publishLimitField, label("pages"), publishPagesField),
                sectionRow);
        sectionRow = addPublishingControlRow(layout, layoutC, label("collection"),
                compactPublishingRow(label("table"), publishTableFrequencyField, label("rows"), publishPuzzleRowsField,
                        label("cols"), publishPuzzleColumnsField),
                sectionRow);
        addPublishingControlRow(layout, layoutC, label("cover"),
                compactPublishingRow(label("binding"), publishBindingCombo, label("interior"), publishInteriorCombo),
                sectionRow);
        grid(panel, collapsible("Layout", layout, false), c, 0, row++, 4, 1);

        JPanel files = transparentPanel(new GridBagLayout());
        GridBagConstraints filesC = constraints();
        sectionRow = 0;
        publishOutputButton = addChooserRow(files, filesC, requiredLabelCell(publishOutputLabel, publishOutputStar),
                publishOutputField, "Choose Output", sectionRow++,
                () -> FileDialogs.choosePath(host.owner(), publishOutputField, true, "Choose publishing output"));
        publishManifestOutputButton = addChooserRow(files, filesC, publishManifestOutputLabel,
                publishManifestOutputField, "Choose Manifest", sectionRow++,
                () -> FileDialogs.choosePath(host.owner(), publishManifestOutputField, true, "Choose manifest output"));
        publishPdfOutputButton = addChooserRow(files, filesC, publishPdfOutputLabel, publishPdfOutputField,
                "Choose PDF", sectionRow++,
                this::choosePublishPdfPath);
        publishCoverOutputButton = addChooserRow(files, filesC, publishCoverOutputLabel, publishCoverOutputField,
                "Choose Cover", sectionRow,
                () -> FileDialogs.choosePath(host.owner(), publishCoverOutputField, true, "Choose cover output"));
        grid(panel, collapsible("Output Files", files, false), c, 0, row++, 4, 1);

        grid(panel, collapsible("Front Matter", createPublishingFrontMatterPanel(), false), c, 0, row++, 4, 1);

        JPanel advanced = transparentPanel(new GridBagLayout());
        GridBagConstraints advancedC = constraints();
        sectionRow = 0;
        addPublishingControlRow(advanced, advancedC, label("report"), createReportPanel(), sectionRow);
        grid(panel, collapsible("Advanced", advanced, false), c, 0, row++, 4, 1);

        publishCreateButton = button("Create PDF", true, event -> runPublishingCommand());
        addVerticalFiller(panel, c, row, 4);
        return panel;
    }

    /**
     * Creates the Publish header action row.
     *
     * @return action row
     */
    private JComponent createPublishActions() {
        if (publishCreateButton == null) {
            publishCreateButton = button("Create PDF", true, event -> runPublishingCommand());
        }
        return controlRow(FlowLayout.RIGHT,
                publishCreateButton,
                button("Copy Command", false, event -> host.copyText(currentPublishCommandText())),
                host.commandStopButton());
    }

    /**
     * Sets the preferred column widths for the publishing fields.
     */
    private void applyPublishingFieldColumns() {
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
    }

    /**
     * Wraps a form section in a hairline-bordered box with a section header, so
     * related controls read as one group. The border tracks the active palette
     * through {@link Theme}'s border refresh.
     *
     * @param title section title
     * @param body section body
     * @return boxed section
     */
    private static JComponent fieldGroup(String title, JComponent body) {
        JPanel group = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        group.setBorder(BorderFactory.createCompoundBorder(
                Theme.lineBorder(Theme.LINE),
                Theme.pad(Theme.SPACE_SM, Theme.SPACE_MD, Theme.SPACE_MD, Theme.SPACE_MD)));
        group.add(Theme.section(title), BorderLayout.NORTH);
        group.add(body, BorderLayout.CENTER);
        return group;
    }

    /**
     * Creates a hidden required marker. Its colour is bound to the semantic
     * {@code ERROR} foreground role, so {@link Theme}'s refresh recolours it on a
     * light/dark switch instead of leaving a stale hex behind.
     *
     * @return required-marker label
     */
    private static JLabel requiredStar() {
        JLabel star = new JLabel("*");
        star.setFont(Theme.font(12, Font.BOLD));
        Theme.foreground(star, Theme.ForegroundRole.ERROR);
        star.setToolTipText("Required");
        star.setVisible(false);
        return star;
    }

    /**
     * Pairs a row label with its trailing required marker in one grid cell.
     *
     * @param rowLabel base label
     * @param star required marker
     * @return label cell
     */
    private static JComponent requiredLabelCell(JLabel rowLabel, JLabel star) {
        JPanel cell = transparentPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        cell.add(rowLabel);
        cell.add(star);
        return cell;
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
     * Adds one publishing control row.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param rowLabel source row label
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
     * Creates high-level publishing template shortcuts. Each enabled shortcut
     * maps to an existing backend task; unsupported templates stay disabled
     * instead of implying generation code that does not exist.
     *
     * @return template shortcut row
     */
    private JComponent createPublishingTemplatePanel() {
        JPanel list = transparentPanel(null);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JButton diagram = button("Diagram sheet", false, event -> selectPublishTemplate(PublishTask.DIAGRAMS));
        JButton worksheet = button("Puzzle worksheet", false, event -> selectPublishTemplate(PublishTask.COLLECTION));
        JButton handout = button("Study handout", false, event -> selectPublishTemplate(PublishTask.STUDY));
        diagram.setAlignmentX(Component.LEFT_ALIGNMENT);
        worksheet.setAlignmentX(Component.LEFT_ALIGNMENT);
        handout.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(diagram);
        list.add(Box.createVerticalStrut(Theme.SPACE_XS));
        list.add(worksheet);
        list.add(Box.createVerticalStrut(Theme.SPACE_XS));
        list.add(handout);
        return list;
    }

    /**
     * Selects an existing backend publishing template.
     *
     * @param task backend task
     */
    private void selectPublishTemplate(PublishTask task) {
        publishTaskCombo.setSelectedItem(task);
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
        JPanel panel = new SurfacePanel(new BorderLayout(8, 8));
        publishReadinessLabel.setFont(Theme.font(12, Font.BOLD));
        publishPreviewZoomLabel.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        publishPreviewZoomLabel.setForeground(Theme.MUTED);
        publishPreviewPageLabel.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        publishPreviewPageLabel.setForeground(Theme.MUTED);

        JPanel header = transparentPanel(new BorderLayout(Theme.SPACE_MD, Theme.SPACE_SM));
        header.add(Theme.section("Preview"), BorderLayout.WEST);
        header.add(publishReadinessLabel, BorderLayout.EAST);

        JPanel toolbar = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        JPanel zoomControls = flow(FlowLayout.LEFT);
        zoomControls.add(button("-", false, event -> zoomPublishPreview(-0.1d)));
        zoomControls.add(publishPreviewZoomLabel);
        zoomControls.add(button("+", false, event -> zoomPublishPreview(0.1d)));
        zoomControls.add(button("Fit Page", false, event -> fitPublishPreviewPage()));
        zoomControls.add(button("Fit Width", false, event -> fitPublishPreviewWidth()));
        toolbar.add(zoomControls, BorderLayout.WEST);

        JPanel pageControls = flow(FlowLayout.RIGHT);
        pageControls.add(iconButton("Back", event -> previousPublishPreviewPage()));
        pageControls.add(publishPreviewPageLabel);
        pageControls.add(iconButton("Forward", event -> nextPublishPreviewPage()));
        pageControls.add(button("Refresh", false, event -> updatePublishCommand()));
        publishOpenPdfButton = button("Open PDF", false, event -> openPublishPdf());
        pageControls.add(publishOpenPdfButton);
        toolbar.add(pageControls, BorderLayout.EAST);

        JPanel north = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        north.add(header, BorderLayout.NORTH);
        north.add(toolbar, BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);

        publishPreviewScroll = scroll(publishVisualPreview);
        panel.add(publishPreviewScroll, BorderLayout.CENTER);
        updatePublishPreviewZoomLabel();
        updatePublishOpenPdfButton();
        return panel;
    }

    /**
     * Adds a publishing path row with a matching chooser button.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param rowLabel source row label
     * @param field path field
     * @param buttonText chooser button text
     * @param row grid row
     * @param chooserAction source chooser action
     * @return chooser button
     */
    private JButton addChooserRow(JPanel panel, GridBagConstraints c, JComponent rowLabel, JTextField field,
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
        PublishTask task = selectedPublishTask();
        boolean diagrams = task == PublishTask.DIAGRAMS;
        boolean render = task == PublishTask.RENDER;
        boolean collection = task == PublishTask.COLLECTION;
        boolean study = task == PublishTask.STUDY;
        boolean cover = task == PublishTask.COVER;
        boolean existingDiagramInput = diagrams && selectedPublishSource() == PublishSource.EXISTING_FILE;
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
        // --output is the only strictly-required output flag (book pdf); the rest
        // default sensibly, so only diagrams marks its output required.
        publishOutputStar.setVisible(diagrams);
        publishInputStar.setVisible(!diagrams || existingDiagramInput);
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
        updatePublishOpenPdfButton();
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
        PublishTask task = selectedPublishTask();
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
     * Recomputes whether the publishing command is currently runnable and
     * refreshes the visual preview. The generated command itself is no longer
     * displayed in Publish — it surfaces through Run/output when Create PDF
     * runs, and is rebuilt on demand by {@link #currentPublishCommandText()}
     * for the Copy Command action.
     */
    private void updatePublishCommand() {
        publishCommandUpdateQueued = false;
        String issue = PublishingPreviewPlanner.preflightIssue(previewContext());
        if (issue == null) {
            try {
                buildPublishArgs(false);
            } catch (IllegalArgumentException | IOException ex) {
                issue = ex.getMessage();
            }
        }
        if (publishCreateButton != null) {
            // Don't offer Create when the command can't even be built.
            publishCreateButton.setEnabled(issue == null);
        }
        updatePublishingVisualPreview(issue);
    }

    /**
     * Builds the current publishing command text on demand, mirroring what
     * Create PDF would launch. Returns an {@code incomplete:} marker when the
     * command can't be built so the user copies an actionable message instead
     * of a stale or empty string.
     *
     * @return display-form command text
     */
    private String currentPublishCommandText() {
        try {
            return CommandRunner.displayCommand(buildPublishArgs(false));
        } catch (IllegalArgumentException | IOException ex) {
            return "incomplete: " + ex.getMessage();
        }
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
     * Adjusts preview zoom.
     *
     * @param delta zoom delta
     */
    private void zoomPublishPreview(double delta) {
        publishVisualPreview.setZoom(publishVisualPreview.zoom() + delta);
        updatePublishPreviewZoomLabel();
    }

    /**
     * Fits the preview page into the standard preview size.
     */
    private void fitPublishPreviewPage() {
        publishVisualPreview.fitPage();
        updatePublishPreviewZoomLabel();
    }

    /**
     * Fits the preview width to the current scroll viewport.
     */
    private void fitPublishPreviewWidth() {
        int viewportWidth = publishPreviewScroll == null ? 0 : publishPreviewScroll.getViewport().getExtentSize().width;
        double zoom = viewportWidth <= 0 ? 1.0d : Math.max(0.65d, (viewportWidth - 24) / 360.0d);
        publishVisualPreview.setZoom(zoom);
        updatePublishPreviewZoomLabel();
    }

    /**
     * Updates the preview zoom label.
     */
    private void updatePublishPreviewZoomLabel() {
        publishPreviewZoomLabel.setText(String.format(Locale.ROOT, "%.0f%%", publishVisualPreview.zoom() * 100.0d));
    }

    /**
     * Opens the selected output PDF if it exists.
     */
    private void openPublishPdf() {
        Path pdf = PublishingPreviewPlanner.currentPdfPath(previewContext());
        if (pdf == null) {
            host.showError("Open PDF", "No PDF output path is configured for the current task.");
            return;
        }
        if (!Files.exists(pdf)) {
            host.showError("Open PDF", "PDF does not exist yet: " + pdf);
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            host.showError("Open PDF", "Desktop file opening is not supported in this environment.");
            return;
        }
        try {
            Desktop.getDesktop().open(pdf.toFile());
        } catch (IOException ex) {
            host.showError("Open PDF", ex.getMessage());
        }
    }

    /**
     * Refreshes Open PDF enablement and tooltip.
     */
    private void updatePublishOpenPdfButton() {
        if (publishOpenPdfButton == null) {
            return;
        }
        Path pdf = PublishingPreviewPlanner.currentPdfPath(previewContext());
        boolean exists = pdf != null && Files.exists(pdf);
        publishOpenPdfButton.setEnabled(exists);
        publishOpenPdfButton.setToolTipText(pdf == null
                ? "No PDF output path is configured"
                : exists ? "Open " + pdf : "PDF has not been generated yet: " + pdf);
    }

    /**
     * Updates the visual publishing preview.
     *
     * @param issue readiness issue, or null
     */
    private void updatePublishingVisualPreview(String issue) {
        PublishingPreviewPlanner.Context context = previewContext();
        PublishPreview.Preview preview = PublishingPreviewPlanner.preview(context, issue);
        publishVisualPreview.setPreview(preview);
        publishReadinessLabel.setText(issue == null ? "Ready to publish" : "Needs attention");
        publishReadinessLabel.setToolTipText(issue == null ? "Publishing command is ready" : issue);
        publishReadinessLabel.setForeground(issue == null ? Theme.STATUS_SUCCESS_TEXT
                : Theme.STATUS_WARNING_TEXT);
        workspaceHeader.setContext(PublishingPreviewPlanner.contextLine(context, issue));
        updatePublishPreviewPageLabel();
        updatePublishOpenPdfButton();
    }

    /**
     * Returns a snapshot adapter for publishing preview planning.
     *
     * @return preview planner context
     */
    private PublishingPreviewPlanner.Context previewContext() {
        return new PublishingPreviewPlanner.Context() {
            /**
             * {@inheritDoc}
             */
            @Override
            PublishTask task() {
                return selectedPublishTask();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            PublishSource source() {
                return selectedPublishSource();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String title() {
                return trimmed(publishTitleField);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String subtitle() {
                return trimmed(publishSubtitleField);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String inputPath() {
                return trimmed(publishInputField);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String outputPath() {
                return trimmed(publishOutputField);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String manifestOutputPath() {
                return trimmed(publishManifestOutputField);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String pdfOutputPath() {
                return trimmed(publishPdfOutputField);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String coverOutputPath() {
                return trimmed(publishCoverOutputField);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String limit() {
                return trimmed(publishLimitField);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String pages() {
                return trimmed(publishPagesField);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            boolean flip() {
                return publishFlipBox.isSelected();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            boolean noFen() {
                return publishNoFenBox.isSelected();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String currentFen() {
                return host.currentFen();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String gameFenList() {
                return host.gameModel().fenList();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            int gameLastPly() {
                return host.gameModel().lastPly();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String batchInputText() {
                return host.batchInputText();
            }
        };
    }

    /**
     * Updates visual preview page label.
     */
    private void updatePublishPreviewPageLabel() {
        publishPreviewPageLabel.setText(publishVisualPreview.pageLabel());
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
        PublishTask task = selectedPublishTask();
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
        PublishSource source = selectedPublishSource();
        switch (source) {
            case CURRENT_FEN -> {
                args.add(OPT_FEN);
                args.add(host.currentFen());
            }
            case GAME_PGN -> {
                args.add(OPT_PGN);
                args.add(materialize ? materializeWorkbenchPgn().toString() : WORKBENCH_GAME_PLACEHOLDER);
            }
            case BATCH_FENS -> {
                args.add(OPT_INPUT);
                args.add(materialize ? materializeWorkbenchFens().toString() : WORKBENCH_FENS_PLACEHOLDER);
            }
            case EXISTING_FILE -> addBookPdfFileInput(args, requiredText(publishInputField, "diagram input path"));
        }
        addRequiredTextArg(args, OPT_OUTPUT, publishOutputField, "output path");
        addOptionalTextArg(args, OPT_TITLE, publishTitleField);
        addOptionalComboArg(args, OPT_PAGE_SIZE, publishPageSizeCombo);
        addOptionalPositiveIntegerArg(args, OPT_DIAGRAMS_PER_ROW, publishDiagramsPerRowField);
        addOptionalPositiveIntegerArg(args, OPT_BOARD_PIXELS, publishBoardPixelsField);
        addToggleArg(args, OPT_FLIP, publishFlipBox.isSelected());
        addToggleArg(args, OPT_NO_FEN, publishNoFenBox.isSelected());
    }

    /**
     * Builds {@code book render} arguments.
     *
     * @param args target arguments
     */
    private void buildRenderPublishArgs(List<String> args) {
        args.add("render");
        addRequiredTextArg(args, OPT_INPUT, publishInputField, "manifest input path");
        addOptionalTextArg(args, OPT_OUTPUT, publishOutputField);
        addOptionalTextArg(args, OPT_TITLE, publishTitleField);
        addOptionalTextArg(args, OPT_SUBTITLE, publishSubtitleField);
        addOptionalPositiveIntegerArg(args, OPT_LIMIT, publishLimitField);
        addWatermarkArgs(args);
        addToggleArg(args, OPT_CHECK, publishValidateBox.isSelected());
    }

    /**
     * Builds {@code book collection} arguments.
     *
     * @param args target arguments
     */
    private void buildCollectionPublishArgs(List<String> args) {
        args.add("collection");
        addRequiredTextArg(args, OPT_INPUT, publishInputField, "record input path");
        addOptionalTextArg(args, OPT_OUTPUT, publishOutputField);
        addOptionalTextArg(args, OPT_PDF_OUTPUT, publishPdfOutputField);
        addOptionalTextArg(args, OPT_COVER_OUTPUT, publishCoverOutputField);
        addOptionalTextArg(args, OPT_TITLE, publishTitleField);
        addOptionalTextArg(args, OPT_SUBTITLE, publishSubtitleField);
        addOptionalTextArg(args, "--author", publishAuthorField);
        addOptionalTextArg(args, "--time", publishTimeField);
        addOptionalTextArg(args, "--location", publishLocationField);
        addOptionalComboArg(args, "--language", publishLanguageCombo);
        addOptionalPositiveIntegerArg(args, OPT_LIMIT, publishLimitField);
        addOptionalPositiveIntegerArg(args, OPT_PAGES, publishPagesField);
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
        addToggleArg(args, OPT_CHECK, publishValidateBox.isSelected());
    }

    /**
     * Builds {@code book study} arguments.
     *
     * @param args target arguments
     */
    private void buildStudyPublishArgs(List<String> args) {
        args.add("study");
        addRequiredTextArg(args, OPT_INPUT, publishInputField, "study manifest input path");
        addOptionalTextArg(args, OPT_OUTPUT, publishOutputField);
        addOptionalTextArg(args, "--manifest-output", publishManifestOutputField);
        addOptionalTextArg(args, OPT_COVER_OUTPUT, publishCoverOutputField);
        addOptionalTextArg(args, OPT_TITLE, publishTitleField);
        addOptionalTextArg(args, OPT_SUBTITLE, publishSubtitleField);
        addOptionalTextArg(args, "--author", publishAuthorField);
        addOptionalTextArg(args, "--time", publishTimeField);
        addOptionalTextArg(args, "--location", publishLocationField);
        addOptionalComboArg(args, OPT_PAGE_SIZE, publishPageSizeCombo);
        addOptionalPositiveDecimalArg(args, "--margin", publishMarginField);
        addOptionalPositiveIntegerArg(args, OPT_DIAGRAMS_PER_ROW, publishDiagramsPerRowField);
        addOptionalPositiveIntegerArg(args, OPT_BOARD_PIXELS, publishBoardPixelsField);
        addOptionalPositiveIntegerArg(args, OPT_PAGES, publishPagesField);
        addCoverLayoutArgs(args);
        addRepeatedTextArgs(args, "--blurb", publishBlurbArea);
        addRepeatedTextArgs(args, "--link", publishLinkArea);
        addToggleArg(args, OPT_FLIP, publishFlipBox.isSelected());
        addToggleArg(args, OPT_NO_FEN, publishNoFenBox.isSelected());
        addToggleArg(args, OPT_CHECK, publishValidateBox.isSelected());
    }

    /**
     * Builds {@code book cover} arguments.
     *
     * @param args target arguments
     */
    private void buildCoverPublishArgs(List<String> args) {
        args.add("cover");
        addRequiredTextArg(args, OPT_INPUT, publishInputField, "book manifest input path");
        addOptionalTextArg(args, OPT_PDF, publishPdfOutputField);
        addOptionalTextArg(args, OPT_OUTPUT, publishOutputField);
        addOptionalTextArg(args, OPT_TITLE, publishTitleField);
        addOptionalTextArg(args, OPT_SUBTITLE, publishSubtitleField);
        addOptionalPositiveIntegerArg(args, OPT_PAGES, publishPagesField);
        addCoverLayoutArgs(args);
        addToggleArg(args, OPT_CHECK, publishValidateBox.isSelected());
    }

    /**
     * Adds the correct diagram-file input flag for {@code book pdf}.
     *
     * @param args target arguments
     * @param input input path
     */
    private static void addBookPdfFileInput(List<String> args, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        args.add(lower.endsWith(".pgn") ? OPT_PGN : OPT_INPUT);
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
        addToggleArg(args, OPT_FREE_WATERMARK, publishWatermarkBox.isSelected());
        addOptionalTextArg(args, OPT_WATERMARK_ID, publishWatermarkIdField);
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
     * @param flag boolean flag
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
     * Writes the current workbench game to a temporary PGN file.
     *
     * @return temporary PGN file
     * @throws IOException on write failure
     */
    private Path materializeWorkbenchPgn() throws IOException {
        if (host.gameModel().lastPly() <= 0) {
            throw new IllegalArgumentException("Play or import at least one game move before exporting PGN.");
        }
        Path file = PathOps.createLocalTempFile("crtk-workbench-game-", ".pgn");
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
        Path file = PathOps.createLocalTempFile("crtk-workbench-fens-", ".txt");
        file.toFile().deleteOnExit();
        Files.writeString(file, text + System.lineSeparator(), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Returns the selected publishing task.
     *
     * @return selected task label
     */
    private PublishTask selectedPublishTask() {
        PublishTask selected = (PublishTask) publishTaskCombo.getSelectedItem();
        return selected == null ? PublishTask.DIAGRAMS : selected;
    }

    /**
     * Returns the selected diagram source.
     *
     * @return selected source
     */
    private PublishSource selectedPublishSource() {
        PublishSource selected = (PublishSource) publishSourceCombo.getSelectedItem();
        return selected == null ? PublishSource.CURRENT_FEN : selected;
    }

    /**
     * Chooses a PDF path using the direction required by the selected task.
     */
    private void choosePublishPdfPath() {
        boolean save = selectedPublishTask() != PublishTask.COVER;
        String title = save ? "Choose PDF output" : "Choose interior PDF";
        FileDialogs.choosePath(host.owner(), publishPdfOutputField, save, title, new FileNameExtensionFilter("PDF document", "pdf"));
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

}
