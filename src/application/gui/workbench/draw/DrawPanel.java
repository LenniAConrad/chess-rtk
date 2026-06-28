package application.gui.workbench.draw;

import application.gui.workbench.board.BoardExportActions;
import application.gui.workbench.board.BoardMarkup;
import application.gui.workbench.board.BoardMarkupTool;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.board.AnnotationGlyphs;
import application.gui.workbench.board.MarkupBrush;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

/**
 * Drawing rail for the shared Workbench board.
 *
 * <p>The panel owns annotation controls only; the board remains the source of
 * truth for gestures, persistent markups, and export snapshots. That keeps Draw
 * mode, right-click annotations in other board modes, and PNG/SVG export on one
 * renderer path.</p>
 */
public final class DrawPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Default annotation opacity.
     */
    private static final int DEFAULT_ALPHA = 212;

    /**
     * Lowest opacity allowed for annotation overlays.
     */
    private static final int MIN_ALPHA = 0;

    /**
     * Highest opacity allowed for annotation overlays.
     */
    private static final int MAX_ALPHA = 255;

    /**
     * Narrowest arrow line width exposed in the Draw rail.
     */
    private static final int MIN_LINE_WIDTH = 4;

    /**
     * Widest arrow line width exposed in the Draw rail.
     */
    private static final int MAX_LINE_WIDTH = 28;

    /**
     * Smallest annotation border width exposed in the Draw rail.
     */
    private static final int MIN_BORDER_WIDTH = 0;

    /**
     * Widest annotation border width exposed in the Draw rail.
     */
    private static final int MAX_BORDER_WIDTH = 16;

    /**
     * Width and height of color swatches in the Draw rail.
     */
    private static final int SWATCH_SIZE = 30;

    /**
     * Width for direct color text fields.
     */
    private static final int DIRECT_COLOR_FIELD_WIDTH = 224;

    /**
     * Adobe-style inspector column width for the Draw rail.
     */
    private static final int INSPECTOR_WIDTH = 420;

    /**
     * Preferred width for the saturation/value color plane.
     */
    private static final int COLOR_PLANE_WIDTH = INSPECTOR_WIDTH;

    /**
     * Preferred height for the saturation/value color plane.
     */
    private static final int COLOR_PLANE_HEIGHT = 132;

    /**
     * Preferred height for the hue strip.
     */
    private static final int HUE_STRIP_HEIGHT = 24;

    /**
     * Fixed width for compact Draw rail control rows.
     */
    private static final int LABEL_WIDTH = 86;

    /**
     * Client property exposing a preset button's fill color for tests and
     * component dumps.
     */
    private static final String PRESET_FILL_PROPERTY = "workbench.draw.preset.fill";

    /**
     * Client property exposing a preset button's border color for tests and
     * component dumps.
     */
    private static final String PRESET_BORDER_PROPERTY = "workbench.draw.preset.border";

    /**
     * Client property exposing a preset button's shape/tool for tests and
     * component dumps.
     */
    private static final String PRESET_TOOL_PROPERTY = "workbench.draw.preset.tool";

    /**
     * Client property exposing a preset button's line width for tests and
     * component dumps.
     */
    private static final String PRESET_LINE_WIDTH_PROPERTY = "workbench.draw.preset.lineWidth";

    /**
     * Client property exposing a preset button's border width for tests and
     * component dumps.
     */
    private static final String PRESET_BORDER_WIDTH_PROPERTY = "workbench.draw.preset.borderWidth";

    /**
     * Client property exposing whether a preset uses rounded rectangle corners.
     */
    private static final String PRESET_ROUNDED_PROPERTY = "workbench.draw.preset.rounded";

    /**
     * Compact preset button height.
     */
    private static final int PRESET_BUTTON_HEIGHT = 48;

    /**
     * Compact annotation-menu row height.
     */
    private static final int ANNOTATION_PRESET_ROW_HEIGHT = 42;

    /**
     * Square symbol lane in one annotation-menu row.
     */
    private static final int ANNOTATION_PRESET_SYMBOL_SIZE = 28;

    /**
     * Number of preset preview buttons per row.
     */
    private static final int PRESET_GRID_COLUMNS = 6;

    /**
     * Chess.com-inspired annotation palette used by Draw one-click presets.
     */
    private static final int PRESET_BRILLIANT_RGB = 0x8B_4C_E0,
            PRESET_EXCELLENT_RGB = 0x79_B9_4A,
            PRESET_GOOD_RGB = 0x79_B9_4A,
            PRESET_INTERESTING_RGB = 0x2F_78_C4,
            PRESET_DUBIOUS_RGB = 0xF4_C5_42,
            PRESET_ONLY_RGB = 0xA9_44_52,
            PRESET_ZUGZWANG_RGB = 0x7C_5A_E6,
            PRESET_BOOK_RGB = 0x96_6F_48,
            PRESET_MISTAKE_RGB = 0xF3_9A_35,
            PRESET_BLUNDER_RGB = 0xC9_4A_3A,
            PRESET_WHITE_WINNING_RGB = 0x79_B9_4A,
            PRESET_COUNTERPLAY_RGB = 0xFF_71_4B,
            PRESET_DRAW_RESULT_RGB = 0x6E_77_81,
            PRESET_WHITE_CHECKMATED_RGB = 0xEC_EF_F4,
            PRESET_BLACK_CHECKMATED_RGB = 0x2A_2D_33,
            PRESET_DARK_MARK_RGB = 0x1F_23_28,
            PRESET_MISSED_WIN_RGB = 0xE5_6A_38,
            PRESET_BLUE_RGB = 0x2F_78_C4,
            PRESET_PURPLE_RGB = 0x6F_52_CC,
            PRESET_SLATE_RGB = 0x6E_77_81,
            PRESET_NOVELTY_RGB = 0x73_57_D5,
            PRESET_AMBER_RGB = 0xE6_A2_3C,
            PRESET_TEAL_RGB = 0x20_A3_93;

    /**
     * Chess annotation presets as four columns of tight, mutually-exclusive
     * sub-sections, each sorted in a natural reading order (move quality
     * best&rarr;worst, evaluation white&rarr;black, etc.).
     */
    private static final List<List<PresetCategory>> ANNOTATION_PRESET_COLUMNS = List.of(
            List.of(
                    new PresetCategory("Move quality", List.of(
                            annotationPreset("!!", "Brilliant move", PRESET_BRILLIANT_RGB),
                            annotationPreset("!", "Good move", PRESET_EXCELLENT_RGB, new Color(PRESET_DARK_MARK_RGB)),
                            annotationPreset("!?", "Interesting move", PRESET_INTERESTING_RGB),
                            annotationPreset("?!", "Dubious move", PRESET_DUBIOUS_RGB,
                                    new Color(PRESET_DARK_MARK_RGB)),
                            annotationPreset("?", "Mistake", PRESET_MISTAKE_RGB, new Color(PRESET_DARK_MARK_RGB)),
                            annotationPreset("??", "Blunder", PRESET_BLUNDER_RGB),
                            annotationPreset(AnnotationGlyphs.MISSED_WIN, "Miss", PRESET_MISSED_WIN_RGB))),
                    new PresetCategory("Conditions", List.of(
                            annotationPreset(AnnotationGlyphs.ONLY_MOVE, "Only move", PRESET_ONLY_RGB),
                            annotationPreset(AnnotationGlyphs.ZUGZWANG, "Zugzwang", PRESET_ZUGZWANG_RGB),
                            annotationPreset("⊕", "Time trouble", PRESET_SLATE_RGB)))),
            List.of(
                    new PresetCategory("Evaluation", List.of(
                            annotationPreset(AnnotationGlyphs.WHITE_WINNING, "White is winning",
                                    PRESET_WHITE_WINNING_RGB, new Color(PRESET_DARK_MARK_RGB)),
                            annotationPreset("+=", "White is better", PRESET_GOOD_RGB,
                                    new Color(PRESET_DARK_MARK_RGB)),
                            annotationPreset("±", "White is slightly better", PRESET_GOOD_RGB,
                                    new Color(PRESET_DARK_MARK_RGB)),
                            annotationPreset("=", "Equal position", PRESET_SLATE_RGB),
                            annotationPreset("∞", "Unclear position", PRESET_SLATE_RGB),
                            annotationPreset("=∞", "With compensation", PRESET_SLATE_RGB),
                            annotationPreset("∓", "Black is slightly better", PRESET_BLUNDER_RGB),
                            annotationPreset("=+", "Black is better", PRESET_BLUNDER_RGB),
                            annotationPreset("-+", "Black is winning", PRESET_BLUNDER_RGB))),
                    new PresetCategory("Result", List.of(
                            annotationPreset(AnnotationGlyphs.DRAW_RESULT, "Draw", PRESET_DRAW_RESULT_RGB),
                            annotationPreset(AnnotationGlyphs.WHITE_CHECKMATED, "White checkmated",
                                    PRESET_WHITE_CHECKMATED_RGB, new Color(PRESET_DARK_MARK_RGB)),
                            annotationPreset(AnnotationGlyphs.BLACK_CHECKMATED, "Black checkmated",
                                    PRESET_BLACK_CHECKMATED_RGB)))),
            List.of(
                    new PresetCategory("Plans", List.of(
                            annotationPreset("→", "Attack", PRESET_BLUE_RGB),
                            annotationPreset("↑", "Initiative", PRESET_BLUE_RGB),
                            annotationPreset("↑↑", "Development", PRESET_BLUE_RGB),
                            annotationPreset(AnnotationGlyphs.COUNTERPLAY, "Counterplay", PRESET_COUNTERPLAY_RGB,
                                    new Color(PRESET_DARK_MARK_RGB)),
                            annotationPreset("△", "With the idea", PRESET_GOOD_RGB, new Color(PRESET_DARK_MARK_RGB)),
                            annotationPreset(AnnotationGlyphs.COUNTERING, "Countering", PRESET_BLUNDER_RGB))),
                    new PresetCategory("Opening", List.of(
                            annotationPreset("N", "Novelty", PRESET_NOVELTY_RGB),
                            annotationPreset(AnnotationGlyphs.BOOK_MOVE, "Book move", PRESET_BOOK_RGB)))),
            List.of(
                    new PresetCategory("Tactics", List.of(
                            annotationPreset(AnnotationGlyphs.FORK, "Fork", PRESET_PURPLE_RGB),
                            annotationPreset(AnnotationGlyphs.PIN, "Pin", PRESET_PURPLE_RGB),
                            annotationPreset(AnnotationGlyphs.SKEWER, "Skewer", PRESET_PURPLE_RGB),
                            annotationPreset(AnnotationGlyphs.DISCOVERED_ATTACK, "Discovered attack",
                                    PRESET_PURPLE_RGB),
                            annotationPreset(AnnotationGlyphs.DOUBLE_ATTACK, "Double attack", PRESET_PURPLE_RGB),
                            annotationPreset(AnnotationGlyphs.XRAY, "X-ray", PRESET_PURPLE_RGB),
                            annotationPreset(AnnotationGlyphs.BATTERY, "Battery", PRESET_PURPLE_RGB),
                            annotationPreset(AnnotationGlyphs.UNDEFENDED, "Undefended", PRESET_BLUNDER_RGB))),
                    new PresetCategory("Checks", List.of(
                            annotationPreset(AnnotationGlyphs.CHECK, "Check", PRESET_BLUE_RGB),
                            annotationPreset(AnnotationGlyphs.DOUBLE_CHECK, "Double check", PRESET_BLUE_RGB),
                            annotationPreset(AnnotationGlyphs.CHECKABLE_KING, "Checkable king", PRESET_BLUE_RGB),
                            annotationPreset(AnnotationGlyphs.MATE, "Checkmate", PRESET_BLUNDER_RGB)))));

    /**
     * Plain one-click shape presets shown below the notation menu: a row of
     * meaning-coloured arrows (the everyday "best move / threat / idea / plan"
     * markers) over a row of square highlights and box/stroke variants.
     */
    private static final List<DrawPreset> SHAPE_PRESETS = List.of(
            shapePreset("Best move", BoardMarkupTool.ARROW, PRESET_EXCELLENT_RGB, 14, 4, false),
            shapePreset("Threat", BoardMarkupTool.ARROW, PRESET_BLUNDER_RGB, 12, 4, false),
            shapePreset("Idea", BoardMarkupTool.ARROW, PRESET_BLUE_RGB, 12, 4, false),
            shapePreset("Plan", BoardMarkupTool.ARROW, PRESET_AMBER_RGB, 12, 4, false),
            shapePreset("Thin", BoardMarkupTool.ARROW, PRESET_SLATE_RGB, 6, 2, false),
            shapePreset("Wide", BoardMarkupTool.ARROW, PRESET_PURPLE_RGB, 18, 5, false),
            shapePreset("Good square", BoardMarkupTool.CIRCLE, PRESET_EXCELLENT_RGB, 10, 4, false),
            shapePreset("Weak square", BoardMarkupTool.CIRCLE, PRESET_BLUNDER_RGB, 10, 4, false),
            shapePreset("Key square", BoardMarkupTool.CIRCLE, PRESET_AMBER_RGB, 10, 4, false),
            shapePreset("Circle", BoardMarkupTool.CIRCLE, PRESET_BLUE_RGB, 10, 4, false),
            shapePreset("Box", BoardMarkupTool.RECTANGLE, PRESET_BOOK_RGB, 10, 2, false),
            shapePreset("Round", BoardMarkupTool.RECTANGLE, PRESET_PURPLE_RGB, 10, 2, true));

    /**
     * Compact height for the current-color preview card.
     */
    private static final int COLOR_PREVIEW_HEIGHT = 56;

    /**
     * Maximum color sample size inside the current-color preview.
     */
    private static final int COLOR_PREVIEW_SWATCH_MAX = 36;

    /**
     * Preferred visible height for the annotation list.
     */
    private static final int ANNOTATION_LIST_HEIGHT = 210;

    /**
     * Fixed height for one detailed annotation history row.
     */
    private static final int ANNOTATION_ROW_HEIGHT = 66;

    /**
     * Fixed height for one compact annotation history row.
     */
    private static final int ANNOTATION_COMPACT_ROW_HEIGHT = 34;

    /**
     * Width for direct alpha and opacity spinners.
     */
    private static final int DIRECT_CHANNEL_SPINNER_WIDTH = 78;

    /**
     * Size of the annotation color swatch in the history list.
     */
    private static final int ANNOTATION_SWATCH_SIZE = 14;

    /**
     * Selection marker for a custom color outside the preset swatch list.
     */
    private static final int CUSTOM_SWATCH_INDEX = -1;

    /**
     * Fill color role index.
     */
    private static final int ROLE_FILL = 0;

    /**
     * Border color role index.
     */
    private static final int ROLE_BORDER = 1;

    /**
     * Board light-square color role index.
     */
    private static final int ROLE_BOARD_LIGHT = 2;

    /**
     * Board dark-square color role index.
     */
    private static final int ROLE_BOARD_DARK = 3;

    /**
     * Annotation list card name for the empty state.
     */
    private static final String ANNOTATIONS_EMPTY = "empty";

    /**
     * Annotation list card name for populated state.
     */
    private static final String ANNOTATIONS_LIST = "list";

    /**
     * Source board.
     */
    private final transient BoardPanel board;

    /**
     * Dialog/export owner.
     */
    private final transient Component owner;

    /**
     * Callback used to refresh the workspace header after annotation changes.
     */
    private final transient Runnable summaryChanged;

    /**
     * Active base color before opacity is applied.
     */
    private Color baseColor = DrawColorFormat.opaque(MarkupBrush.defaultBrush().displayColor());

    /**
     * Active fill color before opacity is applied.
     */
    private Color fillBaseColor = DrawColorFormat.opaque(MarkupBrush.defaultBrush().displayColor());

    /**
     * Active border color before opacity is applied.
     */
    private Color borderBaseColor = DrawColorFormat.opaque(MarkupBrush.defaultBrush().displayBorderColor());

    /**
     * Active board light-square color.
     */
    private Color boardLightBaseColor = DrawColorFormat.opaque(Theme.BOARD_LIGHT);

    /**
     * Active board dark-square color.
     */
    private Color boardDarkBaseColor = DrawColorFormat.opaque(Theme.BOARD_DARK);

    /**
     * Active swatch index.
     */
    private int selectedSwatch;

    /**
     * Active fill swatch index.
     */
    private int fillSwatch;

    /**
     * Active border swatch index.
     */
    private int borderSwatch = CUSTOM_SWATCH_INDEX;

    /**
     * Active board light-square swatch index.
     */
    private int boardLightSwatch = CUSTOM_SWATCH_INDEX;

    /**
     * Active board dark-square swatch index.
     */
    private int boardDarkSwatch = CUSTOM_SWATCH_INDEX;

    /**
     * True while RGB widgets are being updated from code.
     */
    private boolean syncingColorControls;

    /**
     * True while direct color text fields are being rewritten from code.
     */
    private boolean syncingDirectColorFields;

    /**
     * True while the annotation list is being rebuilt from board state.
     */
    private boolean refreshingAnnotationList;

    /**
     * True after the user edits board square colors.
     */
    private boolean customBoardColors;

    /**
     * Preset color swatches.
     */
    private final transient List<Swatch> swatches = new ArrayList<>();

    /**
     * Color role picker.
     */
    private final ChipGroup colorRolePicker = new ChipGroup(List.of("Fill", "Border", "Light", "Dark"));

    /**
     * Annotation shape picker.
     */
    private final ChipGroup toolPicker = new ChipGroup(BoardMarkupTool.labels());

    /**
     * Chess annotation glyph picker.
     */
    private final ChipGroup glyphPicker = new ChipGroup(MarkupBrush.glyphs());

    /**
     * Annotation opacity.
     */
    private final JSpinner opacitySpinner = channelSpinner(DEFAULT_ALPHA);

    /**
     * Direct alpha channel field for the active color role.
     */
    private final JSpinner alphaSpinner = channelSpinner(DEFAULT_ALPHA);

    /**
     * Current hue picker value.
     */
    private int hueValue;

    /**
     * Current saturation picker value.
     */
    private int saturationValue;

    /**
     * Current brightness picker value.
     */
    private int brightnessValue;

    /**
     * Numeric RGB fields paired with the inline color sliders.
     */
    private final JSpinner redSpinner = channelSpinner(baseColor.getRed()),
            greenSpinner = channelSpinner(baseColor.getGreen()),
            blueSpinner = channelSpinner(baseColor.getBlue());

    /**
     * Direct hexadecimal color entry for the active color role.
     */
    private final JTextField hexField = new JTextField(10);

    /**
     * Arrow line width.
     */
    private final JSpinner widthSpinner =
            new JSpinner(new SpinnerNumberModel(MarkupBrush.DEFAULT_LINE_WIDTH, MIN_LINE_WIDTH, MAX_LINE_WIDTH, 1));

    /**
     * Annotation border width.
     */
    private final JSpinner borderWidthSpinner =
            new JSpinner(new SpinnerNumberModel(MarkupBrush.DEFAULT_BORDER_WIDTH,
                    MIN_BORDER_WIDTH, MAX_BORDER_WIDTH, 1));

    /**
     * Suggested-engine-arrow export/display toggle.
     */
    private final ToggleBox suggestedToggle = new ToggleBox("", true);

    /**
     * Castling-right and en-passant hint arrow toggle.
     */
    private final ToggleBox specialHintsToggle = new ToggleBox("", false);

    /**
     * Glyph badge drop-shadow toggle.
     */
    private final ToggleBox glyphShadowToggle = new ToggleBox("", true);

    /**
     * Rectangle corner style toggle.
     */
    private final ToggleBox roundedRectangleToggle = new ToggleBox("", true);

    /**
     * Annotation detail visibility toggle.
     */
    private final ToggleBox detailsToggle = new ToggleBox("", true);

    /**
     * Saturation/value picker.
     */
    private final ColorPlane colorPlane = new ColorPlane();

    /**
     * Hue picker.
     */
    private final HueStrip hueStrip = new HueStrip();

    /**
     * Inline color preview.
     */
    private final ColorPreview colorPreview = new ColorPreview();

    /**
     * Annotation list model.
     */
    private final DefaultListModel<DrawAnnotationRow> annotationListModel = new DefaultListModel<>();

    /**
     * Annotation list.
     */
    private final JList<DrawAnnotationRow> annotationList = new JList<>(annotationListModel);

    /**
     * Annotation list card holder.
     */
    private final JPanel annotationCards = Ui.transparentPanel(new CardLayout());

    /**
     * Undo button.
     */
    private JButton undoButton;

    /**
     * Redo button.
     */
    private JButton redoButton;

    /**
     * Delete selected button.
     */
    private HoldButton deleteButton;

    /**
     * Clear all button.
     */
    private HoldButton clearButton;

    /**
     * Creates a draw rail for a board.
     *
     * @param board board to control
     * @param owner dialog and toast owner for exports
     */
    public DrawPanel(BoardPanel board, Component owner) {
        this(board, owner, null);
    }

    /**
     * Creates a draw rail for a board.
     *
     * @param board board to control
     * @param owner dialog and toast owner for exports
     * @param summaryChanged callback for workspace header refreshes
     */
    public DrawPanel(BoardPanel board, Component owner, Runnable summaryChanged) {
        super(new BorderLayout());
        this.board = Objects.requireNonNull(board, "board");
        this.owner = owner;
        this.summaryChanged = summaryChanged == null ? () -> {
            // optional callback
        } : summaryChanged;
        boardLightBaseColor = DrawColorFormat.opaque(board.boardLightColor());
        boardDarkBaseColor = DrawColorFormat.opaque(board.boardDarkColor());
        baseColor = fillBaseColor;
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setForeground(Theme.TEXT);
        setBorder(Theme.pad(Theme.SPACE_SM));
        build();
        installShortcuts();
        updateColorControlsFromBase();
        applyTool();
        applyBrush();
        board.addMarkupChangeObserver(this::refreshAnnotationState);
        refreshAnnotationState();
    }

    /**
     * Returns the Board / Draw workspace context string.
     *
     * @return context summary
     */
    public String workspaceContext() {
        if (!showDetails()) {
            return "Annotation tool · " + board.markupCount() + " annotations · PNG/SVG export ready";
        }
        int arrows = 0;
        int circles = 0;
        int rectangles = 0;
        int glyphs = 0;
        for (BoardMarkup markup : board.boardMarkups()) {
            if (markup.isCircle()) {
                circles++;
            } else if (markup.isRectangle()) {
                rectangles++;
            } else if (markup.isGlyph()) {
                glyphs++;
            } else {
                arrows++;
            }
        }
        return "Annotation tool · " + arrows + " arrows · " + circles + " circles · "
                + rectangles + " rectangles · " + glyphs + " glyphs · PNG/SVG export ready";
    }

    /**
     * Builds the rail controls.
     */
    private void build() {
        JPanel stack = new InspectorStack();
        addSection(stack, inspectorSection("Presets", presetSection()));
        addSection(stack, inspectorSection("Shape", toolSection()));
        addSection(stack, Ui.collapsible("Brush", brushSection(), false));
        addSection(stack, Ui.collapsible("Color", styleSection(), false));
        addSection(stack, inspectorSection("Annotations", annotationSection()));
        addSection(stack, inspectorSection("Export", exportSection()));
        stack.add(Box.createVerticalGlue());
        JPanel holder = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.add(stack);
        add(holder, BorderLayout.NORTH);
    }

    /**
     * Adds one inspector section.
     *
     * @param stack target stack
     * @param section section component
     */
    private static void addSection(JPanel stack, JComponent section) {
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(section);
        stack.add(Box.createVerticalStrut(Theme.SPACE_SM));
    }

    /**
     * Builds one Adobe-style inspector section with a compact heading.
     *
     * @param title section title
     * @param body section body
     * @return section component
     */
    private static JComponent inspectorSection(String title, JComponent body) {
        JPanel section = verticalPanel();
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        Theme.withAlpha(Theme.LINE, Theme.isDark() ? 200 : 160)),
                Theme.pad(Theme.SPACE_SM, 0, Theme.SPACE_SM, 0)));
        JLabel label = new JLabel(title);
        label.setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
        label.setForeground(Theme.MUTED);
        label.setBorder(Theme.pad(0, 0, Theme.SPACE_XS, 0));
        section.add(label);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(body);
        return section;
    }

    /**
     * Builds the tool section.
     *
     * @return tool section
     */
    private JComponent toolSection() {
        JPanel panel = verticalPanel();
        toolPicker.setOnSelect(index -> applyTool());
        toolPicker.setToolTipText("Pick arrow, circle, rectangle, or glyph drawing.");
        panel.add(controlRow("Shape", toolPicker));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        undoButton = Ui.button("Undo", false, event -> undo());
        redoButton = Ui.button("Redo", false, event -> redo());
        deleteButton = new HoldButton("Delete", this::deleteSelected, true);
        clearButton = new HoldButton("Clear", this::clearAll, true);
        panel.add(actionGrid(undoButton, redoButton, deleteButton, clearButton));
        return panel;
    }

    /**
     * Builds one-click annotation presets.
     *
     * @return preset section
     */
    private JComponent presetSection() {
        JPanel panel = verticalPanel();
        panel.add(annotationPresetMenu());
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        JPanel shapePanel = Ui.transparentPanel(new GridLayout(0, PRESET_GRID_COLUMNS,
                Theme.SPACE_XS, Theme.SPACE_XS));
        for (DrawPreset preset : SHAPE_PRESETS) {
            JButton button = new PresetButton(preset);
            button.addActionListener(event -> applyPreset(preset));
            shapePanel.add(button);
        }
        panel.add(shapePanel);
        return panel;
    }

    /**
     * Builds the labeled chess-notation annotation menu.
     *
     * @return annotation preset menu
     */
    private JComponent annotationPresetMenu() {
        JPanel menu = Ui.transparentPanel(new GridLayout(1, ANNOTATION_PRESET_COLUMNS.size(),
                Theme.SPACE_XS, 0));
        for (List<PresetCategory> columnSections : ANNOTATION_PRESET_COLUMNS) {
            JPanel column = verticalPanel();
            boolean firstSection = true;
            for (PresetCategory section : columnSections) {
                if (!firstSection) {
                    column.add(Box.createVerticalStrut(Theme.SPACE_SM));
                }
                firstSection = false;
                column.add(presetColumnHeader(section.title()));
                column.add(Box.createVerticalStrut(Theme.SPACE_XS));
                for (AnnotationPreset item : section.items()) {
                    JButton button = new AnnotationPresetButton(item);
                    button.setAlignmentX(Component.LEFT_ALIGNMENT);
                    button.addActionListener(event -> applyPreset(item.preset()));
                    column.add(button);
                    column.add(Box.createVerticalStrut(Theme.SPACE_XS));
                }
            }
            menu.add(column);
        }
        return menu;
    }

    /**
     * Builds a muted heading shown above one annotation preset column.
     *
     * @param title column heading text
     * @return styled column header label
     */
    private static JLabel presetColumnHeader(String title) {
        JLabel header = new JLabel(title);
        Theme.foreground(header, Theme.ForegroundRole.MUTED);
        header.setFont(Theme.font(Theme.FONT_MICRO, Font.BOLD));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));
        return header;
    }

    /**
     * Builds style controls.
     *
     * @return style section
     */
    private JComponent styleSection() {
        JPanel panel = verticalPanel();
        colorRolePicker.setOnSelect(index -> switchColorRole());
        colorRolePicker.setToolTipText("Choose which color the mixer edits.");
        panel.add(controlRow("Target", colorRolePicker));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        JPanel row = Ui.transparentPanel(new GridLayout(0, 10, Theme.SPACE_XS, Theme.SPACE_XS));
        for (MarkupBrush brush : MarkupBrush.presetBrushes()) {
            addSwatch(row, brush.name(), DrawColorFormat.opaque(brush.displayColor()));
        }
        addSwatch(row, "plum", new Color(PRESET_PURPLE_RGB));
        addSwatch(row, "teal", new Color(PRESET_TEAL_RGB));
        addSwatch(row, "white", new Color(0xF6, 0xF6, 0xF6));
        addSwatch(row, "black", new Color(0x24, 0x24, 0x24));
        addSwatch(row, "wood light", new Color(0xF0, 0xD9, 0xB5));
        addSwatch(row, "wood dark", new Color(0xB5, 0x88, 0x63));
        panel.add(row);

        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(colorPreview);
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(colorPlane);
        panel.add(Box.createVerticalStrut(Theme.SPACE_XS));
        panel.add(hueStrip);
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        configureDirectColorField(hexField, "Enter #RRGGBB or #AARRGGBB for the selected target.",
                this::applyHexField);
        configureArgbSpinners();
        panel.add(controlRow("Hex", hexField));
        panel.add(Box.createVerticalStrut(Theme.SPACE_XS));
        panel.add(controlRow("ARGB", argbChannelRow()));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(actionGrid(
                Ui.button("Reset board", false, event -> resetBoardColors()),
                Ui.button("Swap board", false, event -> swapBoardColors())));
        return panel;
    }

    /**
     * Builds brush controls.
     *
     * @return brush section
     */
    private JComponent brushSection() {
        JPanel panel = verticalPanel();

        glyphPicker.setOnSelect(index -> applyBrush());
        glyphPicker.setToolTipText("Pick the chess annotation glyph used by Glyph mode.");
        panel.add(controlRow("Glyph", glyphPicker));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        configureOpacitySpinner();
        Ui.styleSpinner(widthSpinner);
        widthSpinner.setToolTipText("Arrow line width.");
        widthSpinner.addChangeListener(event -> applyBrush());
        Ui.styleSpinner(borderWidthSpinner);
        borderWidthSpinner.setToolTipText("Annotation border thickness; use 0 for no border.");
        borderWidthSpinner.addChangeListener(event -> applyBrush());
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(propertyGrid(
                propertyCell("Opacity", opacitySpinner),
                propertyCell("Width", widthSpinner),
                propertyCell("Border", borderWidthSpinner)));

        roundedRectangleToggle.setSelected(false);
        roundedRectangleToggle.setToolTipText("Draw rectangle annotations with rounded corners.");
        roundedRectangleToggle.addActionListener(event -> applyBrush());
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(toggleRow("Rounded corners", roundedRectangleToggle));

        glyphShadowToggle.setSelected(board.isGlyphShadow());
        glyphShadowToggle.setToolTipText("Draw glyph annotation badges with a soft drop shadow.");
        glyphShadowToggle.addActionListener(event -> board.setGlyphShadow(glyphShadowToggle.isSelected()));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(toggleRow("Glyph shadow", glyphShadowToggle));

        suggestedToggle.setSelected(board.isShowSuggestedMoveArrow());
        suggestedToggle.setToolTipText("Show the current engine suggested-move arrow on the board and include it in exports.");
        suggestedToggle.addActionListener(event -> board.setShowSuggestedMoveArrow(suggestedToggle.isSelected()));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(toggleRow("Suggested", suggestedToggle));
        specialHintsToggle.setSelected(board.isShowSpecialMoveHints());
        specialHintsToggle.setToolTipText("Show castling-right and en-passant hint arrows on the board and in exports.");
        specialHintsToggle.addActionListener(event -> board.setShowSpecialMoveHints(specialHintsToggle.isSelected()));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(toggleRow("Move hints", specialHintsToggle));
        return panel;
    }

    /**
     * Builds annotation list controls.
     *
     * @return annotation section
     */
    private JComponent annotationSection() {
        JPanel panel = verticalPanel();
        detailsToggle.setSelected(true);
        detailsToggle.setToolTipText("Show square and color details in the annotation list and workspace header.");
        detailsToggle.addActionListener(event -> refreshAnnotationState());
        panel.add(toggleRow("Details", detailsToggle));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        annotationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        annotationList.setVisibleRowCount(6);
        Theme.list(annotationList);
        annotationList.setFont(Theme.font(Theme.FONT_BODY, Font.PLAIN));
        annotationList.setFixedCellHeight(ANNOTATION_ROW_HEIGHT);
        annotationList.setCellRenderer(new DrawAnnotationRenderer(ANNOTATION_ROW_HEIGHT, ANNOTATION_SWATCH_SIZE));
        annotationList.setSelectionBackground(Theme.SELECTION_SOLID);
        annotationList.setSelectionForeground(Theme.TEXT);
        annotationList.setBorder(Theme.pad(Theme.SPACE_XS));
        annotationList.addListSelectionListener(event -> {
            updateActionState();
            if (!event.getValueIsAdjusting()) {
                applySelectedAnnotationTemplate();
            }
        });
        installAnnotationListMenu();

        annotationCards.add(Ui.emptyState("No annotations", "Draw board annotations to list them here."),
                ANNOTATIONS_EMPTY);
        JScrollPane listScroll = Ui.scroll(annotationList, () -> Theme.ELEVATED_SOLID);
        listScroll.setPreferredSize(new Dimension(240, ANNOTATION_LIST_HEIGHT));
        annotationCards.add(listScroll, ANNOTATIONS_LIST);
        panel.add(annotationCards);
        return panel;
    }

    /**
     * Installs annotation-list context actions.
     */
    private void installAnnotationListMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem useTemplate = new JMenuItem("Use as template");
        useTemplate.addActionListener(event -> applySelectedAnnotationTemplate());
        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(event -> deleteSelected());
        menu.add(useTemplate);
        menu.add(delete);
        Ui.stylePopupMenu(menu);
        annotationList.setComponentPopupMenu(menu);
        annotationList.addMouseListener(new MouseAdapter() {
            /**
             * Handles platform popup triggers.
             *
             * @param event mouse event
             */
            @Override
            public void mousePressed(MouseEvent event) {
                handleAnnotationPopup(event, menu, useTemplate, delete);
            }

            /**
             * Handles platform popup triggers.
             *
             * @param event mouse event
             */
            @Override
            public void mouseReleased(MouseEvent event) {
                handleAnnotationPopup(event, menu, useTemplate, delete);
            }
        });
    }

    /**
     * Shows the annotation context menu when requested.
     *
     * @param event mouse event
     * @param menu popup menu
     * @param useTemplate use-template item
     * @param delete delete item
     */
    private void handleAnnotationPopup(MouseEvent event, JPopupMenu menu, JMenuItem useTemplate, JMenuItem delete) {
        if (!event.isPopupTrigger()) {
            return;
        }
        selectAnnotationAt(event);
        boolean hasSelection = annotationList.getSelectedIndex() >= 0;
        useTemplate.setEnabled(hasSelection);
        delete.setEnabled(hasSelection);
        menu.show(annotationList, event.getX(), event.getY());
    }

    /**
     * Selects the annotation row under the pointer.
     *
     * @param event mouse event
     */
    private void selectAnnotationAt(MouseEvent event) {
        int index = annotationList.locationToIndex(event.getPoint());
        if (index < 0) {
            annotationList.clearSelection();
            return;
        }
        Rectangle bounds = annotationList.getCellBounds(index, index);
        if (bounds != null && bounds.contains(event.getPoint())) {
            annotationList.setSelectedIndex(index);
        } else {
            annotationList.clearSelection();
        }
    }

    /**
     * Builds export actions.
     *
     * @return export section
     */
    private JComponent exportSection() {
        JPanel panel = verticalPanel();
        panel.add(actionGrid(
                Ui.button("Save PNG", true, event -> BoardExportActions.exportPng(owner, board)),
                Ui.button("Save SVG", false, event -> BoardExportActions.exportSvg(owner, board))));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(actionGrid(
                Ui.button("Copy image", false, event -> BoardExportActions.copyImage(owner, board)),
                Ui.button("Copy SVG", false, event -> BoardExportActions.copySvg(owner, board))));
        return panel;
    }

    /**
     * Installs local keyboard shortcuts for Draw actions.
     */
    private void installShortcuts() {
        bindShortcut("draw.undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), this::undo);
        bindShortcut("draw.redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), this::redo);
        bindShortcut("draw.delete", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), this::deleteSelected);
    }

    /**
     * Binds one shortcut.
     *
     * @param name action name
     * @param key key stroke
     * @param action source action
     */
    private void bindShortcut(String name, KeyStroke key, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(key, name);
        getActionMap().put(name, new javax.swing.AbstractAction() {
            /**
             * Serialization identifier for Swing action compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Runs the shortcut action.
             *
             * @param event action event
             */
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (isShowing()) {
                    action.run();
                }
            }
        });
    }

    /**
     * Applies the selected shape to the board.
     */
    private void applyTool() {
        board.setMarkupTool(selectedTool());
        updateGlyphControlState();
    }

    /**
     * Applies the selected color, opacity, and line width to the board.
     */
    private void applyBrush() {
        board.setDirectAnnotationBrush(selectedAnnotationBrush());
        applyBoardColors();
        updateSelectedColorPreview();
        repaintSwatches();
        colorPreview.repaint();
        colorPlane.repaint();
        hueStrip.repaint();
    }

    /**
     * Applies one Draw preset.
     *
     * @param preset selected preset
     */
    private void applyPreset(DrawPreset preset) {
        toolPicker.setSelectedIndex(preset.tool().ordinal());
        selectGlyph(preset.glyph());
        fillBaseColor = DrawColorFormat.opaque(preset.fillColor());
        borderBaseColor = DrawColorFormat.opaque(preset.borderColor());
        fillSwatch = CUSTOM_SWATCH_INDEX;
        borderSwatch = CUSTOM_SWATCH_INDEX;
        setOpacity(preset.fillColor().getAlpha());
        widthSpinner.setValue(Integer.valueOf(preset.lineWidth()));
        borderWidthSpinner.setValue(Integer.valueOf(preset.borderWidth()));
        roundedRectangleToggle.setSelected(preset.roundedRectangle());
        colorRolePicker.setSelectedIndex(ROLE_FILL);
        baseColor = fillBaseColor;
        selectedSwatch = fillSwatch;
        updateColorControlsFromBase();
        applyTool();
        applyBrush();
    }

    /**
     * Uses the selected annotation as the current drawing template.
     */
    private void applySelectedAnnotationTemplate() {
        if (refreshingAnnotationList) {
            return;
        }
        int index = annotationList.getSelectedIndex();
        if (index < 0 || index >= annotationListModel.size()) {
            return;
        }
        applyMarkupTemplate(annotationListModel.get(index).markup());
    }

    /**
     * Applies an existing annotation's shape and brush to future drawing.
     *
     * @param markup source annotation
     */
    private void applyMarkupTemplate(BoardMarkup markup) {
        if (markup == null) {
            return;
        }
        MarkupBrush brush = markup.brush();
        toolPicker.setSelectedIndex(markup.tool().ordinal());
        selectGlyph(brush.glyph());
        fillBaseColor = DrawColorFormat.opaque(brush.displayColor());
        borderBaseColor = DrawColorFormat.opaque(brush.displayBorderColor());
        fillSwatch = CUSTOM_SWATCH_INDEX;
        borderSwatch = CUSTOM_SWATCH_INDEX;
        setOpacity(brush.displayColor().getAlpha());
        widthSpinner.setValue(Integer.valueOf(Math.max(MIN_LINE_WIDTH,
                Math.min(MAX_LINE_WIDTH, brush.lineWidth()))));
        borderWidthSpinner.setValue(Integer.valueOf(Math.max(MIN_BORDER_WIDTH,
                Math.min(MAX_BORDER_WIDTH, brush.displayBorderWidth()))));
        roundedRectangleToggle.setSelected(brush.displayRoundedRectangle());
        colorRolePicker.setSelectedIndex(ROLE_FILL);
        baseColor = fillBaseColor;
        selectedSwatch = fillSwatch;
        updateColorControlsFromBase();
        applyTool();
        applyBrush();
    }

    /**
     * Updates the exact glyph picker to match the active annotation shape.
     */
    private void updateGlyphControlState() {
        boolean glyphMode = selectedTool() == BoardMarkupTool.GLYPH;
        glyphPicker.setEnabled(glyphMode);
        glyphPicker.setToolTipText(glyphMode
                ? "Pick the chess annotation glyph used by Glyph mode."
                : "Glyph choices are active only when Shape is Glyph.");
    }

    /**
     * Undoes the last annotation edit.
     */
    private void undo() {
        board.undoMarkupEdit();
        refreshAnnotationState();
    }

    /**
     * Redoes the last annotation edit.
     */
    private void redo() {
        board.redoMarkupEdit();
        refreshAnnotationState();
    }

    /**
     * Deletes the selected annotation.
     */
    private void deleteSelected() {
        int index = annotationList.getSelectedIndex();
        if (index >= 0) {
            board.removeMarkup(index);
            refreshAnnotationState();
        }
    }

    /**
     * Clears all annotations.
     */
    private void clearAll() {
        board.clearUserMarkup();
        refreshAnnotationState();
    }

    /**
     * Refreshes annotation list, button state, and workspace header.
     */
    private void refreshAnnotationState() {
        int selected = annotationList.getSelectedIndex();
        List<BoardMarkup> markups = board.boardMarkups();
        refreshingAnnotationList = true;
        try {
            annotationListModel.clear();
            boolean details = showDetails();
            annotationList.setFixedCellHeight(details ? ANNOTATION_ROW_HEIGHT : ANNOTATION_COMPACT_ROW_HEIGHT);
            for (int index = 0; index < markups.size(); index++) {
                annotationListModel.addElement(new DrawAnnotationRow(index + 1, markups.get(index), details));
            }
            if (!markups.isEmpty()) {
                int restored = Math.min(Math.max(0, selected), markups.size() - 1);
                annotationList.setSelectedIndex(restored);
            }
        } finally {
            refreshingAnnotationList = false;
        }
        CardLayout layout = (CardLayout) annotationCards.getLayout();
        layout.show(annotationCards, markups.isEmpty() ? ANNOTATIONS_EMPTY : ANNOTATIONS_LIST);
        updateActionState();
        summaryChanged.run();
    }

    /**
     * Updates action enabled state.
     */
    private void updateActionState() {
        if (undoButton != null) {
            undoButton.setEnabled(board.canUndoMarkupEdit());
        }
        if (redoButton != null) {
            redoButton.setEnabled(board.canRedoMarkupEdit());
        }
        boolean hasSelection = annotationList.getSelectedIndex() >= 0 && !annotationListModel.isEmpty();
        if (deleteButton != null) {
            deleteButton.setEnabled(hasSelection);
        }
        if (clearButton != null) {
            clearButton.setEnabled(board.markupCount() > 0);
        }
    }

    /**
     * Returns the selected annotation shape.
     *
     * @return selected annotation shape
     */
    private BoardMarkupTool selectedTool() {
        return BoardMarkupTool.forIndex(toolPicker.getSelectedIndex());
    }

    /**
     * Returns the brush selected by the Draw controls.
     *
     * @return selected annotation brush
     */
    private MarkupBrush selectedAnnotationBrush() {
        Color fill = Theme.withAlpha(fillBaseColor, opacityValue());
        Color border = Theme.withAlpha(borderBaseColor, opacityValue());
        int lineWidth = lineWidth();
        return MarkupBrush.custom(fill, border, lineWidth, borderWidth(), selectedGlyph(), roundedRectangles());
    }

    /**
     * Updates selected color preview details.
     */
    private void updateSelectedColorPreview() {
        String readout = DrawColorFormat.colorLabel(baseColor) + " RGB "
                + baseColor.getRed() + " / " + baseColor.getGreen() + " / " + baseColor.getBlue()
                + " HSV " + hueValue + " / " + saturationValue + " / "
                + brightnessValue
                + (activeRole() <= ROLE_BORDER ? " opacity " + opacityValue() + "/255" : "");
        colorPreview.setToolTipText(activeRoleName() + " color: " + readout);
        updateDirectColorFields();
    }

    /**
     * Repaints all preset swatches after selection or theme changes.
     */
    private void repaintSwatches() {
        for (Swatch swatch : swatches) {
            swatch.repaint();
        }
    }

    /**
     * Handles one preset swatch selection.
     *
     * @param index swatch index
     * @param color swatch color
     */
    private void selectSwatch(int index, Color color) {
        setActiveSwatch(index);
        setActiveBaseColor(DrawColorFormat.opaque(color));
        updateColorControlsFromBase();
        applyBrush();
    }

    /**
     * Switches the active editable color role.
     */
    private void switchColorRole() {
        baseColor = activeBaseColor();
        selectedSwatch = activeSwatch();
        updateColorControlsFromBase();
        applyBrush();
    }

    /**
     * Restores theme board square colors.
     */
    private void resetBoardColors() {
        boardLightBaseColor = DrawColorFormat.opaque(Theme.BOARD_LIGHT);
        boardDarkBaseColor = DrawColorFormat.opaque(Theme.BOARD_DARK);
        boardLightSwatch = CUSTOM_SWATCH_INDEX;
        boardDarkSwatch = CUSTOM_SWATCH_INDEX;
        customBoardColors = false;
        baseColor = activeBaseColor();
        selectedSwatch = activeSwatch();
        updateColorControlsFromBase();
        board.resetBoardColors();
        updateSelectedColorPreview();
        repaintSwatches();
        colorPreview.repaint();
        colorPlane.repaint();
        hueStrip.repaint();
    }

    /**
     * Swaps the active board square colors.
     */
    private void swapBoardColors() {
        Color light = boardLightBaseColor;
        boardLightBaseColor = boardDarkBaseColor;
        boardDarkBaseColor = light;
        int lightSwatch = boardLightSwatch;
        boardLightSwatch = boardDarkSwatch;
        boardDarkSwatch = lightSwatch;
        customBoardColors = true;
        baseColor = activeBaseColor();
        selectedSwatch = activeSwatch();
        updateColorControlsFromBase();
        applyBrush();
    }

    /**
     * Adds one selectable color swatch.
     *
     * @param row swatch row
     * @param name swatch name
     * @param color swatch color
     */
    private void addSwatch(JPanel row, String name, Color color) {
        Swatch swatch = new Swatch(name, color, swatches.size());
        swatches.add(swatch);
        row.add(swatch);
    }

    /**
     * Configures the opacity spinner.
     */
    private void configureOpacitySpinner() {
        styleCompactSpinner(opacitySpinner, "Annotation opacity from 0 to 255.");
        opacitySpinner.addChangeListener(event -> {
            if (!syncingDirectColorFields && activeRole() <= ROLE_BORDER) {
                syncingDirectColorFields = true;
                try {
                    alphaSpinner.setValue(Integer.valueOf(opacityValue()));
                } finally {
                    syncingDirectColorFields = false;
                }
            }
            applyBrush();
        });
    }

    /**
     * Configures direct ARGB channel spinners.
     */
    private void configureArgbSpinners() {
        styleCompactSpinner(alphaSpinner, "Alpha channel, 0 transparent to 255 opaque.");
        styleCompactSpinner(redSpinner, "Red channel, 0 to 255.");
        styleCompactSpinner(greenSpinner, "Green channel, 0 to 255.");
        styleCompactSpinner(blueSpinner, "Blue channel, 0 to 255.");
        alphaSpinner.addChangeListener(event -> applyArgbSpinnerColor());
        redSpinner.addChangeListener(event -> applyArgbSpinnerColor());
        greenSpinner.addChangeListener(event -> applyArgbSpinnerColor());
        blueSpinner.addChangeListener(event -> applyArgbSpinnerColor());
    }

    /**
     * Applies the current ARGB channel spinner values.
     */
    private void applyArgbSpinnerColor() {
        if (syncingDirectColorFields || syncingColorControls) {
            return;
        }
        syncingColorControls = true;
        setActiveSwatch(CUSTOM_SWATCH_INDEX);
        setActiveBaseColor(new Color(
                spinnerValue(redSpinner, 0, 255),
                spinnerValue(greenSpinner, 0, 255),
                spinnerValue(blueSpinner, 0, 255)));
        if (activeRole() <= ROLE_BORDER) {
            setOpacity(spinnerValue(alphaSpinner, MIN_ALPHA, MAX_ALPHA));
        }
        updateHsvControlsFromBase();
        syncingColorControls = false;
        applyBrush();
    }

    /**
     * Styles a compact numeric channel spinner.
     *
     * @param spinner spinner component
     * @param tooltip tooltip text
     */
    private static void styleCompactSpinner(JSpinner spinner, String tooltip) {
        Ui.styleSpinner(spinner);
        Dimension size = new Dimension(DIRECT_CHANNEL_SPINNER_WIDTH, Theme.CONTROL_HEIGHT);
        spinner.setPreferredSize(size);
        spinner.setMinimumSize(size);
        spinner.setToolTipText(tooltip);
    }

    /**
     * Configures one direct color text field.
     *
     * @param field color field
     * @param tooltip resting tooltip
     * @param commit commit action
     */
    private static void configureDirectColorField(JTextField field, String tooltip, Runnable commit) {
        Theme.field(field);
        field.setFont(Theme.mono(Theme.FONT_MONO));
        Dimension size = new Dimension(DIRECT_COLOR_FIELD_WIDTH, Theme.CONTROL_HEIGHT);
        field.setPreferredSize(size);
        field.setMinimumSize(size);
        field.setToolTipText(tooltip);
        field.addActionListener(event -> commit.run());
        field.addFocusListener(new FocusAdapter() {
            /**
             * Commits the color value when keyboard focus leaves the field.
             *
             * @param event focus event
             */
            @Override
            public void focusLost(FocusEvent event) {
                commit.run();
            }
        });
    }

    /**
     * Applies HSV controls as a custom color.
     */
    private void applyHsvControlsAsActiveColor() {
        setActiveSwatch(CUSTOM_SWATCH_INDEX);
        float hue = hueValue / 359f;
        float saturation = saturationValue / 100f;
        float brightness = brightnessValue / 100f;
        setActiveBaseColor(new Color(Color.HSBtoRGB(hue, saturation, brightness)));
    }

    /**
     * Updates the RGB and HSV controls to match the selected color.
     */
    private void updateColorControlsFromBase() {
        syncingColorControls = true;
        updateRgbControlsFromBase();
        updateHsvControlsFromBase();
        syncingColorControls = false;
    }

    /**
     * Updates the RGB controls to match the selected color.
     */
    private void updateRgbControlsFromBase() {
        redSpinner.setValue(Integer.valueOf(baseColor.getRed()));
        greenSpinner.setValue(Integer.valueOf(baseColor.getGreen()));
        blueSpinner.setValue(Integer.valueOf(baseColor.getBlue()));
    }

    /**
     * Updates the HSV controls to match the selected color.
     */
    private void updateHsvControlsFromBase() {
        float[] hsb = Color.RGBtoHSB(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), null);
        hueValue = Math.min(359, Math.round(hsb[0] * 359f));
        saturationValue = Math.round(hsb[1] * 100f);
        brightnessValue = Math.round(hsb[2] * 100f);
        colorPlane.repaint();
        hueStrip.repaint();
    }

    /**
     * Updates the active color from a saturation/value point in the color plane.
     *
     * @param x pointer x
     * @param y pointer y
     */
    private void chooseColorPlanePoint(int x, int y) {
        syncingColorControls = true;
        saturationValue = Math.round(clamp01(x / (float) Math.max(1, colorPlane.getWidth() - 1)) * 100f);
        brightnessValue = Math.round((1f
                - clamp01(y / (float) Math.max(1, colorPlane.getHeight() - 1))) * 100f);
        applyHsvControlsAsActiveColor();
        updateRgbControlsFromBase();
        syncingColorControls = false;
        applyBrush();
    }

    /**
     * Updates the active color from a point in the hue strip.
     *
     * @param x pointer x
     */
    private void chooseHuePoint(int x) {
        syncingColorControls = true;
        hueValue = Math.round(clamp01(x / (float) Math.max(1, hueStrip.getWidth() - 1)) * 359f);
        applyHsvControlsAsActiveColor();
        updateRgbControlsFromBase();
        syncingColorControls = false;
        applyBrush();
    }

    /**
     * Applies the direct hexadecimal color field.
     */
    private void applyHexField() {
        if (syncingDirectColorFields) {
            return;
        }
        Color parsed = DrawColorFormat.parseHexColor(Ui.trimmed(hexField), activeRoleAlpha());
        if (parsed == null) {
            updateDirectColorFields();
            return;
        }
        applyDirectColor(parsed);
    }

    /**
     * Applies one directly entered color to the active role.
     *
     * @param color parsed color
     */
    private void applyDirectColor(Color color) {
        setActiveSwatch(CUSTOM_SWATCH_INDEX);
        setActiveBaseColor(new Color(color.getRed(), color.getGreen(), color.getBlue()));
        if (activeRole() <= ROLE_BORDER) {
            setOpacity(color.getAlpha());
        }
        updateColorControlsFromBase();
        applyBrush();
    }

    /**
     * Updates direct color text fields from the active color role.
     */
    private void updateDirectColorFields() {
        if (syncingDirectColorFields) {
            return;
        }
        syncingDirectColorFields = true;
        try {
            Color color = activeArgbColor();
            boolean includeAlpha = activeRole() <= ROLE_BORDER;
            hexField.setText(DrawColorFormat.hexLabel(color, includeAlpha));
            alphaSpinner.setValue(Integer.valueOf(color.getAlpha()));
            redSpinner.setValue(Integer.valueOf(color.getRed()));
            greenSpinner.setValue(Integer.valueOf(color.getGreen()));
            blueSpinner.setValue(Integer.valueOf(color.getBlue()));
        } finally {
            syncingDirectColorFields = false;
        }
    }

    /**
     * Returns one numeric spinner value.
     *
     * @param spinner source spinner
     * @param min minimum value
     * @param max maximum value
     * @return clamped value
     */
    private static int spinnerValue(JSpinner spinner, int min, int max) {
        Object value = spinner.getValue();
        if (value instanceof Number number) {
            return Math.max(min, Math.min(max, number.intValue()));
        }
        return min;
    }

    /**
     * Returns the selected chess annotation glyph.
     *
     * @return glyph
     */
    private String selectedGlyph() {
        int index = glyphPicker.getSelectedIndex();
        List<String> glyphs = MarkupBrush.glyphs();
        return index >= 0 && index < glyphs.size() ? glyphs.get(index) : MarkupBrush.DEFAULT_GLYPH;
    }

    /**
     * Selects a glyph by value when it is present in the picker.
     *
     * @param glyph glyph text
     */
    private void selectGlyph(String glyph) {
        List<String> glyphs = MarkupBrush.glyphs();
        int index = glyphs.indexOf(glyph);
        if (index >= 0) {
            glyphPicker.setSelectedIndex(index);
        }
    }

    /**
     * Returns the active color role.
     *
     * @return role index
     */
    private int activeRole() {
        int role = colorRolePicker.getSelectedIndex();
        return role >= ROLE_FILL && role <= ROLE_BOARD_DARK ? role : ROLE_FILL;
    }

    /**
     * Returns the active role name.
     *
     * @return role name
     */
    private String activeRoleName() {
        return switch (activeRole()) {
            case ROLE_BORDER -> "Border";
            case ROLE_BOARD_LIGHT -> "Board light";
            case ROLE_BOARD_DARK -> "Board dark";
            default -> "Fill";
        };
    }

    /**
     * Returns the active role color.
     *
     * @return role color
     */
    private Color activeBaseColor() {
        return switch (activeRole()) {
            case ROLE_BORDER -> borderBaseColor;
            case ROLE_BOARD_LIGHT -> boardLightBaseColor;
            case ROLE_BOARD_DARK -> boardDarkBaseColor;
            default -> fillBaseColor;
        };
    }

    /**
     * Sets the active role color.
     *
     * @param color role color
     */
    private void setActiveBaseColor(Color color) {
        baseColor = DrawColorFormat.opaque(color);
        switch (activeRole()) {
            case ROLE_BORDER -> borderBaseColor = baseColor;
            case ROLE_BOARD_LIGHT -> {
                boardLightBaseColor = baseColor;
                customBoardColors = true;
            }
            case ROLE_BOARD_DARK -> {
                boardDarkBaseColor = baseColor;
                customBoardColors = true;
            }
            default -> fillBaseColor = baseColor;
        }
    }

    /**
     * Returns the active role swatch index.
     *
     * @return swatch index
     */
    private int activeSwatch() {
        return switch (activeRole()) {
            case ROLE_BORDER -> borderSwatch;
            case ROLE_BOARD_LIGHT -> boardLightSwatch;
            case ROLE_BOARD_DARK -> boardDarkSwatch;
            default -> fillSwatch;
        };
    }

    /**
     * Sets the active role swatch index.
     *
     * @param index swatch index
     */
    private void setActiveSwatch(int index) {
        selectedSwatch = index;
        switch (activeRole()) {
            case ROLE_BORDER -> borderSwatch = index;
            case ROLE_BOARD_LIGHT -> boardLightSwatch = index;
            case ROLE_BOARD_DARK -> boardDarkSwatch = index;
            default -> fillSwatch = index;
        }
    }

    /**
     * Applies board square colors to the controlled board.
     */
    private void applyBoardColors() {
        if (customBoardColors) {
            board.setBoardColors(boardLightBaseColor, boardDarkBaseColor);
        } else {
            board.resetBoardColors();
        }
    }

    /**
     * Returns selected arrow line width.
     *
     * @return line width
     */
    private int lineWidth() {
        Object value = widthSpinner.getValue();
        return value instanceof Number number ? Math.max(1, number.intValue()) : MarkupBrush.DEFAULT_LINE_WIDTH;
    }

    /**
     * Returns selected opacity.
     *
     * @return opacity alpha
     */
    private int opacityValue() {
        return spinnerValue(opacitySpinner, MIN_ALPHA, MAX_ALPHA);
    }

    /**
     * Sets selected opacity and keeps the direct alpha field aligned.
     *
     * @param value opacity alpha
     */
    private void setOpacity(int value) {
        int alpha = Math.max(MIN_ALPHA, Math.min(MAX_ALPHA, value));
        opacitySpinner.setValue(Integer.valueOf(alpha));
        alphaSpinner.setValue(Integer.valueOf(alpha));
    }

    /**
     * Returns selected border width.
     *
     * @return border width
     */
    private int borderWidth() {
        Object value = borderWidthSpinner.getValue();
        return value instanceof Number number ? Math.max(0, number.intValue()) : MarkupBrush.DEFAULT_BORDER_WIDTH;
    }

    /**
     * Returns whether rectangle annotations use rounded corners.
     *
     * @return true for rounded rectangle corners
     */
    private boolean roundedRectangles() {
        return roundedRectangleToggle.isSelected();
    }

    /**
     * Returns active alpha, or opaque alpha for board square colors.
     *
     * @return alpha value
     */
    private int activeRoleAlpha() {
        return activeRole() <= ROLE_BORDER ? opacityValue() : 255;
    }

    /**
     * Returns the active color including its editable alpha.
     *
     * @return active color with alpha
     */
    private Color activeArgbColor() {
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), activeRoleAlpha());
    }

    /**
     * Returns whether annotation detail text is visible.
     *
     * @return true when detail text is visible
     */
    private boolean showDetails() {
        return detailsToggle.isSelected();
    }

    /**
     * Builds one compact labelled control row.
     *
     * @param text row label
     * @param control row control
     * @return labelled control row
     */
    private static JComponent controlRow(String text, JComponent control) {
        return Ui.labelControlRow(text, control, LABEL_WIDTH);
    }

    /**
     * Builds one compact inspector property grid.
     *
     * @param cells property cells
     * @return property grid
     */
    private static JComponent propertyGrid(JComponent... cells) {
        JPanel row = Ui.transparentPanel(new GridLayout(1, Math.max(1, cells.length), Theme.SPACE_SM, 0));
        for (JComponent cell : cells) {
            row.add(cell);
        }
        row.setPreferredSize(new Dimension(INSPECTOR_WIDTH, Theme.CONTROL_HEIGHT + 22));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT + 22));
        return row;
    }

    /**
     * Builds one label-over-control property cell.
     *
     * @param label label text
     * @param control property control
     * @return property cell
     */
    private static JComponent propertyCell(String label, JComponent control) {
        JPanel cell = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_XS / 2));
        JLabel text = Ui.label(label);
        text.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        Dimension minimum = control.getMinimumSize();
        control.setMinimumSize(new Dimension(Math.max(70, minimum.width), Theme.CONTROL_HEIGHT));
        cell.add(text, BorderLayout.NORTH);
        cell.add(control, BorderLayout.CENTER);
        return cell;
    }

    /**
     * Builds an evenly spaced compact action grid.
     *
     * @param actions action controls
     * @return action grid
     */
    private static JComponent actionGrid(JComponent... actions) {
        JPanel row = Ui.transparentPanel(new GridLayout(1, Math.max(1, actions.length), Theme.SPACE_XS, 0));
        for (JComponent action : actions) {
            row.add(action);
        }
        row.setPreferredSize(new Dimension(INSPECTOR_WIDTH, Theme.CONTROL_HEIGHT));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
        return row;
    }

    /**
     * Builds the direct ARGB channel controls.
     *
     * @return channel row
     */
    private JComponent argbChannelRow() {
        JPanel row = Ui.transparentPanel(new GridLayout(2, 2, Theme.SPACE_SM, Theme.SPACE_XS));
        row.add(miniChannel("A", alphaSpinner));
        row.add(miniChannel("R", redSpinner));
        row.add(miniChannel("G", greenSpinner));
        row.add(miniChannel("B", blueSpinner));
        row.setPreferredSize(new Dimension(2 * (DIRECT_CHANNEL_SPINNER_WIDTH + 18) + Theme.SPACE_SM,
                Theme.CONTROL_HEIGHT * 2 + Theme.SPACE_XS));
        return row;
    }

    /**
     * Adds one labelled compact channel spinner.
     *
     * @param row target row
     * @param label channel label
     * @param spinner channel spinner
     * @return created labelled compact channel spinner
     */
    private static JComponent miniChannel(String label, JSpinner spinner) {
        JPanel row = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_XS, 0));
        JLabel text = Ui.label(label);
        text.setPreferredSize(new Dimension(12, Theme.CONTROL_HEIGHT));
        row.add(text);
        row.add(spinner);
        row.setPreferredSize(new Dimension(DIRECT_CHANNEL_SPINNER_WIDTH + 18, Theme.CONTROL_HEIGHT));
        return row;
    }

    /**
     * Creates one RGB channel spinner.
     *
     * @param value initial value
     * @return spinner
     */
    private static JSpinner channelSpinner(int value) {
        return new JSpinner(new SpinnerNumberModel(value, 0, 255, 1));
    }

    /**
     * Clamps a normalized value.
     *
     * @param value input value
     * @return clamped value
     */
    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * Returns the shared rounded shape for direct color-picking surfaces.
     *
     * @param width component width
     * @param height component height
     * @return rounded picker bounds
     */
    private static RoundRectangle2D.Float pickerShape(int width, int height) {
        return new RoundRectangle2D.Float(0f, 0f,
                Math.max(0, width - 1), Math.max(0, height - 1),
                Theme.RADIUS, Theme.RADIUS);
    }

    /**
     * Creates a full-width label/switch row for Draw boolean settings.
     *
     * @param label row label
     * @param toggle switch control
     * @return row component
     */
    private static JComponent toggleRow(String label, ToggleBox toggle) {
        JPanel row = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.setBorder(Theme.pad(Theme.SPACE_XS, 0, Theme.SPACE_XS, 0));
        Dimension rowSize = new Dimension(240, Theme.CONTROL_HEIGHT + Theme.SPACE_XS * 2);
        row.setPreferredSize(rowSize);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowSize.height));
        JLabel text = Ui.label(label);
        text.setToolTipText(toggle.getToolTipText());
        Dimension toggleSize = new Dimension(70, Theme.CONTROL_HEIGHT);
        toggle.setPreferredSize(toggleSize);
        toggle.setMinimumSize(toggleSize);
        toggle.setMaximumSize(toggleSize);
        toggle.getAccessibleContext().setAccessibleName(label);
        row.add(text, BorderLayout.CENTER);
        row.add(toggle, BorderLayout.EAST);
        return row;
    }

    /**
     * Creates a transparent vertical panel.
     *
     * @return panel
     */
    private static JPanel verticalPanel() {
        JPanel panel = Ui.transparentPanel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    /**
     * Builds a glyph preset using the shared professional annotation palette.
     *
     * @param glyph glyph label
     * @param tooltip button tooltip
     * @param rgb fill RGB
     * @return draw preset
     */
    private static DrawPreset glyphPreset(String glyph, String tooltip, int rgb) {
        return glyphPreset(glyph, tooltip, rgb, Color.WHITE);
    }

    /**
     * Builds a glyph preset using the shared professional annotation palette.
     *
     * @param glyph glyph label
     * @param tooltip button tooltip
     * @param rgb fill RGB
     * @param borderColor glyph stroke and border color
     * @return draw preset
     */
    private static DrawPreset glyphPreset(String glyph, String tooltip, int rgb, Color borderColor) {
        return new DrawPreset(glyph, BoardMarkupTool.GLYPH, glyph, presetColor(rgb),
                borderColor, MarkupBrush.DEFAULT_LINE_WIDTH, MarkupBrush.DEFAULT_BORDER_WIDTH,
                false, tooltip);
    }

    /**
     * Builds one labeled notation-menu row.
     *
     * @param glyph glyph label
     * @param description visible notation description
     * @param rgb fill RGB
     * @return annotation preset row
     */
    private static AnnotationPreset annotationPreset(String glyph, String description, int rgb) {
        return new AnnotationPreset(glyphPreset(glyph, description + " glyph", rgb), description);
    }

    /**
     * Builds one labeled notation-menu row with a custom glyph mark color.
     *
     * @param glyph glyph label
     * @param description visible notation description
     * @param rgb fill RGB
     * @param borderColor glyph stroke and border color
     * @return annotation preset row
     */
    private static AnnotationPreset annotationPreset(String glyph, String description, int rgb, Color borderColor) {
        return new AnnotationPreset(glyphPreset(glyph, description + " glyph", rgb, borderColor), description);
    }

    /**
     * Builds a shape preset using the same color contract as glyph presets.
     *
     * @param label button label
     * @param tool annotation tool
     * @param rgb fill RGB
     * @param lineWidth stroke width in pixels
     * @param borderWidth source border width
     * @param roundedRectangle true for rounded rectangle markups
     * @return draw preset
     */
    private static DrawPreset shapePreset(String label, BoardMarkupTool tool, int rgb,
            int lineWidth, int borderWidth, boolean roundedRectangle) {
        return new DrawPreset(label, tool, MarkupBrush.DEFAULT_GLYPH, presetColor(rgb), Color.WHITE,
                lineWidth, borderWidth, roundedRectangle, label + " annotation preset");
    }

    /**
     * Creates an opaque preset color from a palette value.
     *
     * @param rgb color RGB
     * @return preset color
     */
    private static Color presetColor(int rgb) {
        return new Color(rgb);
    }

    /**
     * Exposes preset values on a Swing component for component dumps and tests.
     *
     * @param component target component
     * @param preset source preset
     */
    private static void exposePresetProperties(JComponent component, DrawPreset preset) {
        component.putClientProperty(PRESET_FILL_PROPERTY, preset.fillColor());
        component.putClientProperty(PRESET_BORDER_PROPERTY, preset.borderColor());
        component.putClientProperty(PRESET_TOOL_PROPERTY, preset.tool());
        component.putClientProperty(PRESET_LINE_WIDTH_PROPERTY, Integer.valueOf(preset.lineWidth()));
        component.putClientProperty(PRESET_BORDER_WIDTH_PROPERTY, Integer.valueOf(preset.borderWidth()));
        component.putClientProperty(PRESET_ROUNDED_PROPERTY, Boolean.valueOf(preset.roundedRectangle()));
        component.putClientProperty("workbench.draw.preset.glyph", preset.glyph());
    }

    /**
     * Builds a precise tooltip from the brush values applied by the preset.
     *
     * @param preset source preset
     * @return preset tooltip text
     */
    private static String presetTooltip(DrawPreset preset) {
        return preset.tooltip()
                + " · fill " + DrawColorFormat.colorLabel(preset.fillColor())
                + " · border " + DrawColorFormat.colorLabel(preset.borderColor())
                + " · width " + preset.lineWidth()
                + " · edge " + preset.borderWidth()
                + (preset.roundedRectangle() ? " · rounded" : "");
    }

    /**
     * One titled column of related annotation presets.
     *
     * @param title column heading
     * @param items presets shown under the heading
     */
    private record PresetCategory(String title, List<AnnotationPreset> items) {
    }

    /**
     * One labeled annotation-menu row.
     *
     * @param preset applied preset
     * @param description visible row label
     */
    private record AnnotationPreset(DrawPreset preset, String description) {
    }

    /**
     * One-click Draw preset.
     *
     * @param label button label
     * @param tool annotation tool
     * @param glyph glyph label
     * @param fillColor source fill color
     * @param borderColor source border color
     * @param lineWidth stroke width in pixels
     * @param borderWidth source border width
     * @param roundedRectangle true for rounded rectangle markups
     * @param tooltip button tooltip
     */
    private record DrawPreset(String label, BoardMarkupTool tool, String glyph, Color fillColor,
            Color borderColor, int lineWidth, int borderWidth, boolean roundedRectangle, String tooltip) {
    }

    /**
     * Fixed-width inspector stack that keeps the Draw rail at a deliberate
     * properties-panel width even when its scroll viewport is wider.
     */
    private static final class InspectorStack extends JPanel {

        /**
         * Serialization identifier for Swing panel compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the inspector stack.
         */
        InspectorStack() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        /**
         * Returns the fixed-width preferred size with the natural content height.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            return new Dimension(INSPECTOR_WIDTH, preferred.height);
        }

        /**
         * Returns the minimum size.
         *
         * @return minimum size
         */
        @Override
        public Dimension getMinimumSize() {
            Dimension minimum = super.getMinimumSize();
            return new Dimension(Math.min(320, INSPECTOR_WIDTH), minimum.height);
        }

        /**
         * Returns the maximum size.
         *
         * @return maximum size
         */
        @Override
        public Dimension getMaximumSize() {
            Dimension maximum = super.getMaximumSize();
            return new Dimension(INSPECTOR_WIDTH, maximum.height);
        }
    }

    /**
     * Labeled annotation preset row used by the chess-notation menu.
     */
    private static final class AnnotationPresetButton extends JButton {

        /**
         * Serialization identifier for Swing button compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Menu item represented by this button.
         */
        private final AnnotationPreset item;

        /**
         * Creates one labeled annotation-menu button.
         *
         * @param item represented menu row
         */
        AnnotationPresetButton(AnnotationPreset item) {
            super(item.description());
            this.item = item;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setFont(Theme.font(Theme.FONT_MICRO, Font.PLAIN));
            Dimension size = new Dimension(120, ANNOTATION_PRESET_ROW_HEIGHT);
            setPreferredSize(size);
            setMinimumSize(new Dimension(96, ANNOTATION_PRESET_ROW_HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, ANNOTATION_PRESET_ROW_HEIGHT));
            setToolTipText(presetTooltip(item.preset()));
            exposePresetProperties(this, item.preset());
        }

        /**
         * Paints the notation-menu row.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintChrome(g);
                int side = Math.min(ANNOTATION_PRESET_SYMBOL_SIZE, Math.max(18, getHeight() - 10));
                int symbolX = 8;
                int symbolY = Math.max(4, (getHeight() - side) / 2);
                paintSymbol(g, symbolX, symbolY, side);
                int textX = symbolX + side + Theme.SPACE_XS + 3;
                int textWidth = Math.max(1, getWidth() - textX - Theme.SPACE_XS);
                paintWrappedDescription(g, textX, textWidth);
                if (isFocusOwner()) {
                    g.setColor(Theme.FOCUS_RING);
                    g.drawRoundRect(2, 2, Math.max(0, getWidth() - 5), Math.max(0, getHeight() - 5),
                            Theme.RADIUS, Theme.RADIUS);
                }
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints row chrome.
         *
         * @param g graphics context
         */
        private void paintChrome(Graphics2D g) {
            boolean pressed = getModel().isPressed();
            boolean rollover = getModel().isRollover();
            int width = Math.max(0, getWidth() - 1);
            int height = Math.max(0, getHeight() - 1);
            Color surface = pressed ? Theme.SELECTION_SOLID
                    : rollover ? Theme.ELEVATED_SOLID : Theme.INPUT;
            g.setColor(surface);
            g.fillRoundRect(0, 0, width, height, Theme.RADIUS, Theme.RADIUS);
            g.setColor(Theme.withAlpha(item.preset().fillColor(), Theme.isDark() ? 180 : 150));
            g.fillRoundRect(3, 6, 3, Math.max(0, height - 12), 3, 3);
            if (rollover || pressed) {
                g.setColor(Theme.withAlpha(item.preset().fillColor(), Theme.isDark() ? 30 : 22));
                g.fillRoundRect(1, 1, Math.max(0, width - 2), Math.max(0, height - 2),
                        Theme.RADIUS, Theme.RADIUS);
            }
            g.setColor(Theme.withAlpha(Theme.INPUT_BORDER, Theme.isDark() ? 184 : 156));
            g.drawRoundRect(0, 0, width, height, Theme.RADIUS, Theme.RADIUS);
        }

        /**
         * Paints the annotation symbol badge.
         *
         * @param g graphics context
         * @param x x coordinate
         * @param y y coordinate
         * @param side badge side length
         */
        private void paintSymbol(Graphics2D g, int x, int y, int side) {
            DrawPreset preset = item.preset();
            if (AnnotationGlyphs.isCustom(preset.glyph())) {
                AnnotationGlyphs.paintCustom(g, preset.glyph(), x, y, side, preset.fillColor(),
                        preset.borderColor(), Math.max(1f, Math.min(2f, preset.borderWidth())));
                return;
            }
            g.setColor(preset.fillColor());
            g.fillOval(x, y, side, side);
            g.setStroke(new BasicStroke(Math.max(1f, Math.min(2f, preset.borderWidth()))));
            g.setColor(preset.borderColor());
            g.drawOval(x, y, side, side);
            Font font = Theme.font(glyphFontSize(preset.glyph()), Font.BOLD);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(preset.glyph(), x + (side - metrics.stringWidth(preset.glyph())) / 2,
                    y + (side - metrics.getHeight()) / 2 + metrics.getAscent());
        }

        /**
         * Returns a compact symbol font size for the badge.
         *
         * @param glyph glyph text
         * @return font size
         */
        private static float glyphFontSize(String glyph) {
            return glyph != null && glyph.length() > 1 ? 9.5f : 12f;
        }

        /**
         * Paints the row description, wrapping at one word boundary when needed.
         *
         * @param g graphics context
         * @param x text x coordinate
         * @param width available text width
         */
        private void paintWrappedDescription(Graphics2D g, int x, int width) {
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            List<String> lines = descriptionLines(metrics, getText(), width);
            int lineHeight = metrics.getHeight();
            int totalHeight = lineHeight * lines.size();
            int baseline = (getHeight() - totalHeight) / 2 + metrics.getAscent();
            g.setColor(Theme.isDark() ? Theme.TEXT : Theme.withAlpha(Theme.TEXT, 232));
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(lines.get(i), x, baseline + i * lineHeight);
            }
        }

        /**
         * Splits a label into one or two paint lines.
         *
         * @param metrics font metrics
         * @param text source text
         * @param width available width
         * @return one or two label lines
         */
        private static List<String> descriptionLines(FontMetrics metrics, String text, int width) {
            if (metrics.stringWidth(text) <= width) {
                return List.of(text);
            }
            int bestSplit = -1;
            int bestOverflow = Integer.MAX_VALUE;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) != ' ') {
                    continue;
                }
                String first = text.substring(0, i);
                String second = text.substring(i + 1);
                int overflow = Math.max(metrics.stringWidth(first) - width, metrics.stringWidth(second) - width);
                int balance = Math.abs(metrics.stringWidth(first) - metrics.stringWidth(second));
                int score = Math.max(0, overflow) * 1000 + balance;
                if (score < bestOverflow) {
                    bestOverflow = score;
                    bestSplit = i;
                }
            }
            if (bestSplit > 0) {
                return List.of(text.substring(0, bestSplit), text.substring(bestSplit + 1));
            }
            return List.of(text);
        }
    }

    /**
     * One-click preset control that previews the exact brush it applies.
     */
    private static final class PresetButton extends JButton {

        /**
         * Serialization identifier for Swing button compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Preset represented by this button.
         */
        private final DrawPreset preset;

        /**
         * Creates one preset preview button.
         *
         * @param preset represented preset
         */
        PresetButton(DrawPreset preset) {
            super(preset.label());
            this.preset = preset;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
            int width = Math.max(54, preset.label().length() * 8 + 28);
            Dimension size = new Dimension(width, PRESET_BUTTON_HEIGHT);
            setPreferredSize(size);
            setMinimumSize(size);
            setToolTipText(presetTooltip(preset));
            exposePresetProperties(this, preset);
        }

        /**
         * Paints the preset preview and label.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintChrome(g);
                paintPreview(g);
                paintLabel(g);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints restrained button chrome with a slim color accent.
         *
         * @param g graphics context
         */
        private void paintChrome(Graphics2D g) {
            boolean pressed = getModel().isPressed();
            boolean rollover = getModel().isRollover();
            int width = Math.max(0, getWidth() - 1);
            int height = Math.max(0, getHeight() - 1);
            Color surface = pressed ? Theme.SELECTION_SOLID
                    : rollover ? Theme.ELEVATED_SOLID : Theme.INPUT;
            g.setColor(surface);
            g.fillRoundRect(0, 0, width, height,
                    Theme.RADIUS, Theme.RADIUS);
            if (rollover || pressed) {
                g.setColor(Theme.withAlpha(preset.fillColor(), Theme.isDark() ? 34 : 24));
                g.fillRoundRect(1, 1, Math.max(0, width - 2), Math.max(0, height - 2),
                        Theme.RADIUS, Theme.RADIUS);
            }
            g.setColor(Theme.withAlpha(preset.fillColor(), Theme.isDark() ? 166 : 142));
            g.fillRoundRect(4, 6, 4, Math.max(0, height - 12), 4, 4);
            g.setColor(rollover
                    ? Theme.withAlpha(preset.borderColor(), Theme.isDark() ? 224 : 196)
                    : Theme.withAlpha(Theme.INPUT_BORDER, Theme.isDark() ? 210 : 180));
            g.drawRoundRect(0, 0, width, height, Theme.RADIUS, Theme.RADIUS);
            if (isFocusOwner()) {
                g.setColor(Theme.FOCUS_RING);
                g.drawRoundRect(2, 2, Math.max(0, getWidth() - 5), Math.max(0, getHeight() - 5),
                        Theme.RADIUS, Theme.RADIUS);
            }
        }

        /**
         * Paints the actual annotation primitive the preset will apply.
         *
         * @param g graphics context
         */
        private void paintPreview(Graphics2D g) {
            int x = 12;
            int y = 7;
            int width = Math.max(18, getWidth() - 22);
            int height = 14;
            Color fill = preset.fillColor();
            Color border = preset.borderColor();
            switch (preset.tool()) {
                case GLYPH -> paintGlyphPreview(g, x, y, width, height, fill, border);
                case CIRCLE -> paintCirclePreview(g, x, y, width, height, fill, border);
                case RECTANGLE -> paintRectanglePreview(g, x, y, width, height, fill, border);
                default -> paintArrowPreview(g, x, y, width, height, fill, border);
            }
        }

        /**
         * Paints a compact glyph annotation sample.
         *
         * @param g graphics context
         * @param x x coordinate in pixels
         * @param y y coordinate in pixels
         * @param width width in pixels
         * @param height height in pixels
         * @param fill fill color
         * @param border border color
         */
        private void paintGlyphPreview(Graphics2D g, int x, int y, int width, int height, Color fill, Color border) {
            int side = Math.min(width, height + 5);
            int chipX = x + Math.max(0, (width - side) / 2);
            if (AnnotationGlyphs.isCustom(preset.glyph())) {
                AnnotationGlyphs.paintCustom(g, preset.glyph(), chipX, y - 1, side, fill, border,
                        Math.max(1f, Math.min(2f, preset.borderWidth())));
                return;
            }
            g.setColor(fill);
            g.fillRoundRect(chipX, y - 1, side, side, Theme.RADIUS, Theme.RADIUS);
            g.setStroke(new BasicStroke(Math.max(1f, Math.min(2f, preset.borderWidth()))));
            g.setColor(Color.WHITE);
            g.drawRoundRect(chipX, y - 1, side, side, Theme.RADIUS, Theme.RADIUS);
            g.setFont(Theme.font(Theme.FONT_MICRO, Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            String glyph = preset.glyph();
            g.setColor(Color.WHITE);
            g.drawString(glyph, chipX + (side - metrics.stringWidth(glyph)) / 2,
                    y - 1 + (side - metrics.getHeight()) / 2 + metrics.getAscent());
        }

        /**
         * Paints a compact arrow annotation sample.
         *
         * @param g graphics context
         * @param x x coordinate in pixels
         * @param y y coordinate in pixels
         * @param width width in pixels
         * @param height height in pixels
         * @param fill fill color
         * @param border border color
         */
        private void paintArrowPreview(Graphics2D g, int x, int y, int width, int height, Color fill, Color border) {
            int midY = y + height / 2;
            float stroke = Math.max(2f, Math.min(5f, preset.lineWidth() / 3f));
            g.setStroke(new BasicStroke(stroke + Math.max(0f, preset.borderWidth() / 2f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Color.WHITE);
            g.drawLine(x, midY, x + width - 5, midY);
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(fill);
            g.drawLine(x, midY, x + width - 5, midY);
            int tipX = x + width - 4;
            g.fillPolygon(new int[] { tipX, tipX - 7, tipX - 7 },
                    new int[] { midY, midY - 5, midY + 5 }, 3);
        }

        /**
         * Paints a compact circle annotation sample.
         *
         * @param g graphics context
         * @param x x coordinate in pixels
         * @param y y coordinate in pixels
         * @param width width in pixels
         * @param height height in pixels
         * @param fill fill color
         * @param border border color
         */
        private void paintCirclePreview(Graphics2D g, int x, int y, int width, int height, Color fill, Color border) {
            int side = Math.min(width, height + 5);
            int chipX = x + Math.max(0, (width - side) / 2);
            g.setColor(fill);
            g.fillOval(chipX, y - 1, side, side);
            g.setStroke(new BasicStroke(Math.max(1f, Math.min(3f, preset.borderWidth()))));
            g.setColor(Color.WHITE);
            g.drawOval(chipX, y - 1, side, side);
        }

        /**
         * Paints a compact rectangle annotation sample.
         *
         * @param g graphics context
         * @param x x coordinate in pixels
         * @param y y coordinate in pixels
         * @param width width in pixels
         * @param height height in pixels
         * @param fill fill color
         * @param border border color
         */
        private void paintRectanglePreview(Graphics2D g, int x, int y, int width, int height,
                Color fill, Color border) {
            int arc = preset.roundedRectangle() ? Theme.RADIUS * 2 : 0;
            g.setColor(fill);
            g.fillRoundRect(x + 2, y - 1, width - 4, height + 4, arc, arc);
            g.setStroke(new BasicStroke(Math.max(1f, Math.min(3f, preset.borderWidth()))));
            g.setColor(Color.WHITE);
            g.drawRoundRect(x + 2, y - 1, width - 4, height + 4, arc, arc);
        }

        /**
         * Paints the preset label below the primitive sample.
         *
         * @param g graphics context
         */
        private void paintLabel(Graphics2D g) {
            String label = getText();
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            int baseline = getHeight() - 8;
            g.setColor(Theme.isDark() ? Theme.TEXT : Theme.withAlpha(Theme.TEXT, 232));
            g.drawString(label, Math.max(4, (getWidth() - metrics.stringWidth(label)) / 2), baseline);
        }

    }

    /**
     * Saturation/value color plane.
     */
    private final class ColorPlane extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the plane.
         */
        ColorPlane() {
            Dimension size = new Dimension(COLOR_PLANE_WIDTH, COLOR_PLANE_HEIGHT);
            setPreferredSize(size);
            setMinimumSize(new Dimension(160, COLOR_PLANE_HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, COLOR_PLANE_HEIGHT));
            setToolTipText("Pick saturation and value.");
            MouseAdapter listener = new MouseAdapter() {
                /**
                 * Chooses the color at the pointer.
                 *
                 * @param event mouse event
                 */
                @Override
                public void mousePressed(MouseEvent event) {
                    chooseColorPlanePoint(event.getX(), event.getY());
                }

                /**
                 * Chooses the color at the pointer.
                 *
                 * @param event mouse event
                 */
                @Override
                public void mouseDragged(MouseEvent event) {
                    chooseColorPlanePoint(event.getX(), event.getY());
                }
            };
            addMouseListener(listener);
            addMouseMotionListener(listener);
        }

        /**
         * Paints the saturation/value plane.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = Math.max(1, getWidth());
                int height = Math.max(1, getHeight());
                RoundRectangle2D.Float shape = pickerShape(width, height);
                float hue = hueValue / 359f;
                Graphics2D fill = (Graphics2D) g.create();
                try {
                    fill.clip(shape);
                    for (int y = 0; y < height; y++) {
                        float value = 1f - y / (float) Math.max(1, height - 1);
                        for (int x = 0; x < width; x++) {
                            float saturation = x / (float) Math.max(1, width - 1);
                            fill.setColor(new Color(Color.HSBtoRGB(hue, saturation, value)));
                            fill.fillRect(x, y, 1, 1);
                        }
                    }
                } finally {
                    fill.dispose();
                }
                g.setColor(Theme.LINE);
                g.draw(shape);
                int markerX = Math.round(saturationValue / 100f * (width - 1));
                int markerY = Math.round((1f - brightnessValue / 100f) * (height - 1));
                g.setStroke(new java.awt.BasicStroke(2f));
                g.setColor(Color.WHITE);
                g.drawOval(markerX - 5, markerY - 5, 10, 10);
                g.setColor(Color.BLACK);
                g.drawOval(markerX - 6, markerY - 6, 12, 12);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Hue strip color picker.
     */
    private final class HueStrip extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the hue strip.
         */
        HueStrip() {
            Dimension size = new Dimension(COLOR_PLANE_WIDTH, HUE_STRIP_HEIGHT);
            setPreferredSize(size);
            setMinimumSize(new Dimension(160, HUE_STRIP_HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, HUE_STRIP_HEIGHT));
            setToolTipText("Pick hue.");
            MouseAdapter listener = new MouseAdapter() {
                /**
                 * Chooses the hue at the pointer.
                 *
                 * @param event mouse event
                 */
                @Override
                public void mousePressed(MouseEvent event) {
                    chooseHuePoint(event.getX());
                }

                /**
                 * Chooses the hue at the pointer.
                 *
                 * @param event mouse event
                 */
                @Override
                public void mouseDragged(MouseEvent event) {
                    chooseHuePoint(event.getX());
                }
            };
            addMouseListener(listener);
            addMouseMotionListener(listener);
        }

        /**
         * Paints the hue strip.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = Math.max(1, getWidth());
                int height = Math.max(1, getHeight());
                RoundRectangle2D.Float shape = pickerShape(width, height);
                Graphics2D fill = (Graphics2D) g.create();
                try {
                    fill.clip(shape);
                    for (int x = 0; x < width; x++) {
                        float hue = x / (float) Math.max(1, width - 1);
                        fill.setColor(new Color(Color.HSBtoRGB(hue, 1f, 1f)));
                        fill.fillRect(x, 0, 1, height);
                    }
                } finally {
                    fill.dispose();
                }
                g.setColor(Theme.LINE);
                g.draw(shape);
                int markerX = Math.round(hueValue / 359f * (width - 1));
                g.setColor(Theme.TEXT);
                g.drawLine(markerX, 2, markerX, height - 3);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Inline color preview.
     */
    private final class ColorPreview extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the preview.
         */
        ColorPreview() {
            setPreferredSize(new Dimension(220, COLOR_PREVIEW_HEIGHT));
            setMinimumSize(new Dimension(120, COLOR_PREVIEW_HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, COLOR_PREVIEW_HEIGHT));
            setToolTipText("Current draw color.");
        }

        /**
         * Paints the current color and alpha sample.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth() - 1;
                int height = getHeight() - 1;
                g.setColor(Theme.INPUT);
                g.fillRoundRect(0, 0, width, height, Theme.RADIUS, Theme.RADIUS);
                g.setColor(Theme.LINE);
                g.drawRoundRect(0, 0, width, height, Theme.RADIUS, Theme.RADIUS);

                int swatch = Math.min(COLOR_PREVIEW_SWATCH_MAX, Math.max(24, height - Theme.SPACE_MD));
                int x = Theme.SPACE_SM;
                int y = (height - swatch) / 2;
                Color active = activeRole() <= ROLE_BORDER
                        ? Theme.withAlpha(baseColor, opacityValue())
                        : baseColor;
                paintChecker(g, x, y, swatch, swatch);
                g.setColor(active);
                g.fillRoundRect(x, y, swatch, swatch, Theme.RADIUS, Theme.RADIUS);
                g.setColor(Theme.withAlpha(Theme.TEXT, 80));
                g.drawRoundRect(x, y, swatch, swatch, Theme.RADIUS, Theme.RADIUS);

                g.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
                g.setColor(Theme.TEXT);
                g.drawString(activeRoleName(), x + swatch + Theme.SPACE_MD, y + 17);
                g.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
                g.setColor(Theme.MUTED);
                g.drawString(DrawColorFormat.colorLabel(baseColor), x + swatch + Theme.SPACE_MD, y + 36);

                int boardX = Math.max(x + swatch + 112, getWidth() - Theme.SPACE_SM - swatch);
                int half = Math.max(8, swatch / 2);
                g.setColor(boardLightBaseColor);
                g.fillRect(boardX, y, half, half);
                g.fillRect(boardX + half, y + half, half, half);
                g.setColor(boardDarkBaseColor);
                g.fillRect(boardX + half, y, half, half);
                g.fillRect(boardX, y + half, half, half);
                g.setColor(Theme.withAlpha(Theme.TEXT, 80));
                g.drawRect(boardX, y, half * 2, half * 2);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints a small checkerboard under translucent color samples.
         *
         * @param g graphics context
         * @param x x-coordinate in pixels
         * @param y y-coordinate in pixels
         * @param width width in pixels
         * @param height height in pixels
         */
        private void paintChecker(Graphics2D g, int x, int y, int width, int height) {
            int cell = 7;
            for (int yy = 0; yy < height; yy += cell) {
                for (int xx = 0; xx < width; xx += cell) {
                    boolean light = ((xx / cell) + (yy / cell)) % 2 == 0;
                    g.setColor(light ? Theme.PANEL_SOLID : Theme.ELEVATED_SOLID);
                    g.fillRect(x + xx, y + yy, Math.min(cell, width - xx), Math.min(cell, height - yy));
                }
            }
        }
    }

    /**
     * Color swatch control.
     */
    private final class Swatch extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Swatch color.
         */
        private final Color color;

        /**
         * Swatch index.
         */
        private final int index;

        /**
         * Creates one swatch.
         *
         * @param name swatch name
         * @param color swatch color
         * @param index swatch index
         */
        Swatch(String name, Color color, int index) {
            this.color = DrawColorFormat.opaque(color);
            this.index = index;
            Dimension size = new Dimension(SWATCH_SIZE, Theme.CONTROL_HEIGHT);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            setToolTipText("Use " + name);
            setBorder(BorderFactory.createEmptyBorder());
            addMouseListener(new java.awt.event.MouseAdapter() {
                /**
                 * Selects this color swatch.
                 *
                 * @param event mouse event
                 */
                @Override
                public void mousePressed(java.awt.event.MouseEvent event) {
                    selectSwatch(index, Swatch.this.color);
                }
            });
        }

        /**
         * Paints the swatch chrome and selected color sample.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int inset = selectedSwatch == index ? 4 : 6;
                int sampleWidth = Math.max(0, getWidth() - inset * 2);
                int sampleHeight = Math.max(0, getHeight() - inset * 2);
                g.setColor(Theme.INPUT);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                g.setColor(selectedSwatch == index
                        ? Theme.ACCENT
                        : Theme.withAlpha(Theme.INPUT_BORDER, Theme.isDark() ? 210 : 176));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                g.setColor(color);
                g.fillRoundRect(inset, inset, sampleWidth, sampleHeight,
                        Math.max(3, Theme.RADIUS - 2), Math.max(3, Theme.RADIUS - 2));
                g.setColor(Theme.withAlpha(Theme.TEXT, Theme.isDark() ? 88 : 54));
                g.drawRoundRect(inset, inset, sampleWidth, sampleHeight,
                        Math.max(3, Theme.RADIUS - 2), Math.max(3, Theme.RADIUS - 2));
            } finally {
                g.dispose();
            }
        }
    }
}
