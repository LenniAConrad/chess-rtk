package application.gui.workbench.game;

import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.struct.Game;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import static application.gui.workbench.ui.Ui.labelControlRow;
import static application.gui.workbench.ui.Ui.onTextChange;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Editable study/repertoire authoring panel backed by the current game line.
 */
public final class StudyAuthorPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Current game supplier.
     */
    private final transient Supplier<GameModel> gameSupplier;

    /**
     * Current board FEN supplier.
     */
    private final transient Supplier<String> fenSupplier;

    /**
     * Clipboard helper.
     */
    private final transient Consumer<String> copyText;

    /**
     * Editable study fields.
     */
    private final JTextField bookTitleField = new JTextField("Workbench Study");
    /**
     * Text field for composition title.
     */
    private final JTextField compositionTitleField = new JTextField("Current Line");
    /**
     * Text area for description.
     */
    private final JTextArea descriptionArea = new JTextArea("Study the critical line from the current board.");
    /**
     * Text area for analysis.
     */
    private final JTextArea analysisArea = new JTextArea();
    /**
     * Text area for hint.
     */
    private final JTextArea hintArea = new JTextArea();

    /**
     * Generated manifest preview.
     */
    private final JTextArea manifestArea = new JTextArea();

    /**
     * Status label.
     */
    private final javax.swing.JLabel statusLabel = Ui.label("Ready");

    /**
     * Creates a study authoring panel.
     *
     * @param gameSupplier current game supplier
     * @param fenSupplier current FEN supplier
     * @param copyText clipboard helper
     */
    public StudyAuthorPanel(Supplier<GameModel> gameSupplier, Supplier<String> fenSupplier,
            Consumer<String> copyText) {
        super(new BorderLayout(Theme.SPACE_SM, Theme.SPACE_SM));
        this.gameSupplier = gameSupplier == null ? GameModel::new : gameSupplier;
        this.fenSupplier = fenSupplier == null ? () -> Game.STANDARD_START_FEN : fenSupplier;
        this.copyText = copyText == null ? text -> { } : copyText;
        configure();
        refreshManifest();
    }

    /**
     * Builds a TOML study manifest from a draft.
     *
     * @param draft draft data
     * @return TOML manifest
     */
    public static String buildStudyToml(StudyDraft draft) {
        StringBuilder out = new StringBuilder(2048);
        out.append("title = ").append(toml(draft.bookTitle())).append('\n');
        out.append("subtitle = ").append(toml("Workbench repertoire")).append('\n');
        out.append("author = ").append(toml("ChessRTK")).append('\n');
        out.append("pageSize = ").append(toml("a5")).append('\n');
        out.append("diagramsPerRow = 1\n");
        out.append("boardPixels = 720\n");
        out.append("showFen = false\n\n");
        out.append("[[compositions]]\n");
        out.append("title = ").append(toml(draft.compositionTitle())).append('\n');
        appendOptional(out, "description", draft.description());
        appendOptional(out, "analysis", draft.analysis());
        appendOptional(out, "hintLevel1", draft.hint());
        appendArray(out, "figureMovesAlgebraic", draft.figureMovesAlgebraic());
        appendArray(out, "figureMovesDetail", draft.figureMovesDetail());
        appendArray(out, "figureFens", draft.figureFens());
        appendArray(out, "figureArrows", draft.figureArrows());
        return out.toString();
    }

    /**
     * Creates a draft from the current game and editor fields.
     *
     * @return draft
     */
    public StudyDraft draft() {
        GameModel model = gameSupplier.get();
        List<GameModel.PlySnapshot> snapshots = model == null ? List.of() : model.mainlineSnapshots();
        List<String> moves = new ArrayList<>();
        List<String> details = new ArrayList<>();
        List<String> fens = new ArrayList<>();
        List<String> arrows = new ArrayList<>();
        moves.add("Start");
        details.add("Initial position");
        fens.add(startFen(model));
        arrows.add("");
        for (GameModel.PlySnapshot snapshot : snapshots) {
            moves.add(snapshot.ply() + ". " + snapshot.san());
            details.add(snapshot.whiteMove() ? "White move" : "Black move");
            fens.add(snapshot.afterFen());
            arrows.add(snapshot.uci());
        }
        if (snapshots.isEmpty()) {
            fens.set(0, fenSupplier.get());
        }
        String analysis = text(analysisArea);
        if (analysis.isBlank() && model != null && model.lastPly() > 0) {
            analysis = model.sanLine();
        }
        return new StudyDraft(
                text(bookTitleField),
                text(compositionTitleField),
                text(descriptionArea),
                analysis,
                text(hintArea),
                List.copyOf(moves),
                List.copyOf(details),
                List.copyOf(fens),
                List.copyOf(arrows));
    }

    /**
     * Refreshes the manifest preview.
     */
    public void refreshManifest() {
        manifestArea.setText(buildStudyToml(draft()));
        manifestArea.setCaretPosition(0);
    }

    /**
     * Configures the panel.
     */
    private void configure() {
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setBorder(Theme.pad(Theme.SPACE_MD));
        getAccessibleContext().setAccessibleName("Study authoring panel");
        getAccessibleContext().setAccessibleDescription(
                "Creates a book study TOML composition from the current game line.");
        styleFields(bookTitleField, compositionTitleField);
        styleAreas(descriptionArea, analysisArea, hintArea, manifestArea);
        bookTitleField.getAccessibleContext().setAccessibleName("Study book title");
        compositionTitleField.getAccessibleContext().setAccessibleName("Study entry title");
        descriptionArea.getAccessibleContext().setAccessibleName("Study description");
        analysisArea.getAccessibleContext().setAccessibleName("Study analysis");
        hintArea.getAccessibleContext().setAccessibleName("Study hint");
        manifestArea.getAccessibleContext().setAccessibleName("Generated study TOML");
        manifestArea.getAccessibleContext().setAccessibleDescription(
                "Read-only TOML manifest generated from the current study draft.");
        manifestArea.setEditable(false);
        add(createToolbar(), BorderLayout.NORTH);
        add(createEditor(), BorderLayout.WEST);
        add(Ui.titled("Study TOML", scroll(manifestArea)), BorderLayout.CENTER);
        installRefreshListeners();
    }

    /**
     * Creates the action toolbar.
     *
     * @return toolbar
     */
    private JComponent createToolbar() {
        JComponent actions = Ui.controlRow(FlowLayout.RIGHT,
                Ui.button("Refresh", false, event -> refreshManifest()),
                Ui.button("Copy Manifest", true, event -> {
                    refreshManifest();
                    copyText.accept(manifestArea.getText());
                    statusLabel.setText("Manifest copied");
                }),
                Ui.button("Save TOML", false, event -> saveManifest()));
        JPanel row = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.add(statusLabel, BorderLayout.WEST);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    /**
     * Creates the authoring form.
     *
     * @return editor component
     */
    private JComponent createEditor() {
        JPanel panel = new SurfacePanel(new BorderLayout(0, Theme.SPACE_SM));
        panel.setPreferredSize(new java.awt.Dimension(300, 420));
        JPanel top = transparentPanel(new BorderLayout());
        top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
        top.add(labelControlRow("Book", bookTitleField, 78));
        top.add(labelControlRow("Entry", compositionTitleField, 78));
        panel.add(top, BorderLayout.NORTH);
        JPanel body = transparentPanel(new java.awt.GridLayout(0, 1, 0, Theme.SPACE_SM));
        body.add(Ui.titled("Description", scroll(descriptionArea)));
        body.add(Ui.titled("Analysis", scroll(analysisArea)));
        body.add(Ui.titled("Hint", scroll(hintArea)));
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Installs manifest refresh listeners.
     */
    private void installRefreshListeners() {
        onTextChange(this::refreshManifest, bookTitleField);
        onTextChange(this::refreshManifest, compositionTitleField);
        onAreaChange(descriptionArea);
        onAreaChange(analysisArea);
        onAreaChange(hintArea);
    }

    /**
     * Installs a manifest-refresh listener on a text area.
     *
     * @param area text area
     */
    private void onAreaChange(JTextArea area) {
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            /**
             * Refreshes after insertion.
             *
             * @param event document event
             */
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                refreshManifest();
            }

            /**
             * Refreshes after removal.
             *
             * @param event document event
             */
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                refreshManifest();
            }

            /**
             * Refreshes after attribute changes.
             *
             * @param event document event
             */
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                refreshManifest();
            }
        });
    }

    /**
     * Saves the generated manifest.
     */
    private void saveManifest() {
        refreshManifest();
        JFileChooser chooser = FileDialogs.createFileChooser("Save Study TOML",
                new File("workbench-study.toml"),
                new FileNameExtensionFilter("TOML manifest", "toml"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = FileDialogs.ensureExtension(chooser.getSelectedFile(), ".toml");
        try {
            Files.writeString(file.toPath(), manifestArea.getText(), StandardCharsets.UTF_8);
            statusLabel.setText("Saved " + file.getName());
        } catch (IOException ex) {
            statusLabel.setText("Save failed: " + ex.getMessage());
        }
    }

    /**
     * Returns the start FEN for a model.
     *
     * @param model game model
     * @return start FEN
     */
    private static String startFen(GameModel model) {
        if (model == null) {
            return Game.STANDARD_START_FEN;
        }
        return model.startPosition().toString();
    }

    /**
     * Appends an optional string field.
     *
     * @param out output builder
     * @param key TOML key
     * @param value candidate value
     */
    private static void appendOptional(StringBuilder out, String key, String value) {
        if (value != null && !value.isBlank()) {
            out.append(key).append(" = ").append(toml(value)).append('\n');
        }
    }

    /**
     * Appends a TOML string array.
     *
     * @param out output builder
     * @param key TOML key
     * @param values input values
     */
    private static void appendArray(StringBuilder out, String key, List<String> values) {
        out.append(key).append(" = [\n");
        for (String value : values) {
            out.append("  ").append(toml(value)).append(",\n");
        }
        out.append("]\n");
    }

    /**
     * Returns text from a text field.
     *
     * @param field record field
     * @return text
     */
    private static String text(JTextField field) {
        String value = field.getText();
        return value == null || value.isBlank() ? "Untitled" : value.trim();
    }

    /**
     * Returns text from a text area.
     *
     * @param area target area
     * @return text
     */
    private static String text(JTextArea area) {
        String value = area.getText();
        return value == null ? "" : value.trim();
    }

    /**
     * Escapes a TOML string.
     *
     * @param value raw value
     * @return TOML string
     */
    private static String toml(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }

    /**
     * Study authoring draft.
     *
     * @param bookTitle source book title
     * @param compositionTitle source composition title
     * @param description description text
     * @param analysis analysis text
     * @param hint first hint text
     * @param figureMovesAlgebraic figure labels
     * @param figureMovesDetail figure details
     * @param figureFens source figure fens
     * @param figureArrows source figure arrows
     */
    public record StudyDraft(
            String bookTitle,
            String compositionTitle,
            String description,
            String analysis,
            String hint,
            List<String> figureMovesAlgebraic,
            List<String> figureMovesDetail,
            List<String> figureFens,
            List<String> figureArrows) {
    }
}
