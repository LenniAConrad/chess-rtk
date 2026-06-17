package application.gui.workbench.draw;

import application.gui.workbench.board.BoardExportActions;
import application.gui.workbench.board.BoardMarkup;
import application.gui.workbench.board.BoardMarkupTool;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.board.MarkupBrush;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
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
     * Preferred width for RGB channel sliders.
     */
    private static final int CHANNEL_SLIDER_WIDTH = 150;

    /**
     * Width for RGB numeric fields, including spinner arrows.
     */
    private static final int CHANNEL_SPINNER_WIDTH = 90;

    /**
     * Width for direct color text fields.
     */
    private static final int DIRECT_COLOR_FIELD_WIDTH = 224;

    /**
     * Preferred width for the saturation/value color plane.
     */
    private static final int COLOR_PLANE_WIDTH = 232;

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
    private static final int LABEL_WIDTH = 78;

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
    private static final int ANNOTATION_LIST_HEIGHT = 146;

    /**
     * Fixed height for one annotation history row.
     */
    private static final int ANNOTATION_ROW_HEIGHT = 40;

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
    private final JSlider opacitySlider = new JSlider(MIN_ALPHA, MAX_ALPHA, DEFAULT_ALPHA);

    /**
     * Inline RGB controls for custom draw colors.
     */
    private final JSlider redSlider = new JSlider(0, 255, baseColor.getRed()),
            greenSlider = new JSlider(0, 255, baseColor.getGreen()),
            blueSlider = new JSlider(0, 255, baseColor.getBlue());

    /**
     * Inline HSV controls for custom draw colors.
     */
    private final JSlider hueSlider = new JSlider(0, 359, 0),
            saturationSlider = new JSlider(0, 100, 0),
            valueSlider = new JSlider(0, 100, 0);

    /**
     * Numeric RGB fields paired with the inline color sliders.
     */
    private final JSpinner redSpinner = channelSpinner(baseColor.getRed()),
            greenSpinner = channelSpinner(baseColor.getGreen()),
            blueSpinner = channelSpinner(baseColor.getBlue());

    /**
     * Numeric HSV fields paired with the inline color controls.
     */
    private final JSpinner hueSpinner = hsvSpinner(0, 0, 359),
            saturationSpinner = hsvSpinner(0, 0, 100),
            valueSpinner = hsvSpinner(0, 0, 100);

    /**
     * Direct hexadecimal color entry for the active color role.
     */
    private final JTextField hexField = new JTextField(10);

    /**
     * Direct ARGB color entry for the active color role.
     */
    private final JTextField argbField = new JTextField(20);

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
        setBorder(Theme.pad(Theme.SPACE_MD));
        build();
        installShortcuts();
        updateColorControlsFromBase();
        applyTool();
        applyBrush();
        board.setMarkupChangeObserver(this::refreshAnnotationState);
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
        JPanel stack = Ui.transparentPanel(null);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        addSection(stack, Ui.titled("Tool", toolSection()));
        addSection(stack, Ui.titled("Color", styleSection()));
        addSection(stack, Ui.titled("Brush", brushSection()));
        addSection(stack, Ui.titled("Annotations", annotationSection()));
        addSection(stack, Ui.titled("Export", exportSection()));
        stack.add(Box.createVerticalGlue());
        add(stack, BorderLayout.NORTH);
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
        stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
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
        glyphPicker.setOnSelect(index -> applyBrush());
        glyphPicker.setToolTipText("Pick the chess annotation glyph used by Glyph mode.");
        panel.add(controlRow("Glyph", glyphPicker));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        undoButton = Ui.button("Undo", false, event -> undo());
        redoButton = Ui.button("Redo", false, event -> redo());
        deleteButton = new HoldButton("Delete", this::deleteSelected, true);
        clearButton = new HoldButton("Clear", this::clearAll, true);
        panel.add(wrappingButtons(undoButton, redoButton, deleteButton, clearButton));
        return panel;
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

        JPanel row = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        for (MarkupBrush brush : MarkupBrush.presetBrushes()) {
            addSwatch(row, brush.name(), DrawColorFormat.opaque(brush.displayColor()));
        }
        addSwatch(row, "purple", new Color(0x8B, 0x5C, 0xD6));
        addSwatch(row, "cyan", new Color(0x00, 0x9F, 0xB7));
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
        configureDirectColorField(argbField, "Enter alpha, red, green, blue as 0-255 values.",
                this::applyArgbField);
        panel.add(controlRow("Hex", hexField));
        panel.add(Box.createVerticalStrut(Theme.SPACE_XS));
        panel.add(controlRow("ARGB", argbField));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(Ui.buttonRow(FlowLayout.LEFT,
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

        opacitySlider.setToolTipText("Annotation opacity.");
        Ui.styleSlider(opacitySlider);
        opacitySlider.addChangeListener(event -> applyBrush());
        panel.add(controlRow("Opacity", opacitySlider));

        Ui.styleSpinner(widthSpinner);
        widthSpinner.setToolTipText("Arrow line width.");
        widthSpinner.addChangeListener(event -> applyBrush());
        Ui.styleSpinner(borderWidthSpinner);
        borderWidthSpinner.setToolTipText("Annotation border thickness; use 0 for no border.");
        borderWidthSpinner.addChangeListener(event -> applyBrush());
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(spinnerPairRow("Width", widthSpinner, "Border", borderWidthSpinner));

        roundedRectangleToggle.setSelected(false);
        roundedRectangleToggle.setToolTipText("Draw rectangle annotations with rounded corners.");
        roundedRectangleToggle.addActionListener(event -> applyBrush());
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(toggleRow("Rounded corners", roundedRectangleToggle));

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
        annotationList.addListSelectionListener(event -> updateActionState());

        annotationCards.add(Ui.emptyState("No annotations", "Draw board annotations to list them here."),
                ANNOTATIONS_EMPTY);
        JScrollPane listScroll = Ui.scroll(annotationList, () -> Theme.ELEVATED_SOLID);
        listScroll.setPreferredSize(new Dimension(240, ANNOTATION_LIST_HEIGHT));
        annotationCards.add(listScroll, ANNOTATIONS_LIST);
        panel.add(annotationCards);
        return panel;
    }

    /**
     * Builds export actions.
     *
     * @return export section
     */
    private JComponent exportSection() {
        JPanel panel = verticalPanel();
        panel.add(Ui.buttonRow(FlowLayout.RIGHT,
                Ui.button("Save PNG", true, event -> BoardExportActions.exportPng(owner, board)),
                Ui.button("Save SVG", false, event -> BoardExportActions.exportSvg(owner, board))));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(Ui.buttonRow(FlowLayout.RIGHT,
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
     * @param action action
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
        annotationListModel.clear();
        List<BoardMarkup> markups = board.boardMarkups();
        boolean details = showDetails();
        for (int index = 0; index < markups.size(); index++) {
            annotationListModel.addElement(new DrawAnnotationRow(index + 1, markups.get(index), details));
        }
        if (!markups.isEmpty()) {
            int restored = Math.min(Math.max(0, selected), markups.size() - 1);
            annotationList.setSelectedIndex(restored);
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
        Color fill = Theme.withAlpha(fillBaseColor, opacitySlider.getValue());
        Color border = Theme.withAlpha(borderBaseColor, opacitySlider.getValue());
        int lineWidth = lineWidth();
        return MarkupBrush.custom(fill, border, lineWidth, borderWidth(), selectedGlyph(), roundedRectangles());
    }

    /**
     * Updates selected color preview details.
     */
    private void updateSelectedColorPreview() {
        String readout = DrawColorFormat.colorLabel(baseColor) + " RGB "
                + baseColor.getRed() + " / " + baseColor.getGreen() + " / " + baseColor.getBlue()
                + " HSV " + hueSlider.getValue() + " / " + saturationSlider.getValue() + " / "
                + valueSlider.getValue()
                + (activeRole() <= ROLE_BORDER ? " opacity " + opacitySlider.getValue() + "/255" : "");
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
     * Configures one RGB channel control pair.
     *
     * @param slider channel slider
     * @param spinner channel numeric input
     */
    private void configureColorChannel(JSlider slider, JSpinner spinner) {
        Ui.styleSlider(slider);
        slider.setPreferredSize(new Dimension(CHANNEL_SLIDER_WIDTH, Theme.CONTROL_HEIGHT));
        slider.setToolTipText("Custom color channel.");
        Ui.styleSpinner(spinner);
        Dimension spinnerSize = new Dimension(CHANNEL_SPINNER_WIDTH, Theme.CONTROL_HEIGHT);
        spinner.setPreferredSize(spinnerSize);
        spinner.setMinimumSize(spinnerSize);
        spinner.setToolTipText("Custom color channel value.");
        slider.addChangeListener(event -> {
            if (syncingColorControls) {
                return;
            }
            syncingColorControls = true;
            spinner.setValue(Integer.valueOf(slider.getValue()));
            applyRgbControlsAsActiveColor();
            updateHsvControlsFromBase();
            syncingColorControls = false;
            applyBrush();
        });
        spinner.addChangeListener(event -> {
            if (syncingColorControls) {
                return;
            }
            syncingColorControls = true;
            slider.setValue(spinnerValue(spinner, slider.getMinimum(), slider.getMaximum()));
            applyRgbControlsAsActiveColor();
            updateHsvControlsFromBase();
            syncingColorControls = false;
            applyBrush();
        });
    }

    /**
     * Configures one HSV channel control pair.
     *
     * @param slider channel slider
     * @param spinner channel numeric input
     */
    private void configureHsvChannel(JSlider slider, JSpinner spinner) {
        Ui.styleSlider(slider);
        slider.setPreferredSize(new Dimension(CHANNEL_SLIDER_WIDTH, Theme.CONTROL_HEIGHT));
        slider.setToolTipText("Custom HSV color channel.");
        Ui.styleSpinner(spinner);
        Dimension spinnerSize = new Dimension(CHANNEL_SPINNER_WIDTH, Theme.CONTROL_HEIGHT);
        spinner.setPreferredSize(spinnerSize);
        spinner.setMinimumSize(spinnerSize);
        spinner.setToolTipText("Custom HSV color channel value.");
        slider.addChangeListener(event -> {
            if (syncingColorControls) {
                return;
            }
            syncingColorControls = true;
            spinner.setValue(Integer.valueOf(slider.getValue()));
            applyHsvControlsAsActiveColor();
            updateRgbControlsFromBase();
            syncingColorControls = false;
            applyBrush();
        });
        spinner.addChangeListener(event -> {
            if (syncingColorControls) {
                return;
            }
            syncingColorControls = true;
            slider.setValue(spinnerValue(spinner, slider.getMinimum(), slider.getMaximum()));
            applyHsvControlsAsActiveColor();
            updateRgbControlsFromBase();
            syncingColorControls = false;
            applyBrush();
        });
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
     * Applies RGB controls as a custom color.
     */
    private void applyRgbControlsAsActiveColor() {
        setActiveSwatch(CUSTOM_SWATCH_INDEX);
        setActiveBaseColor(new Color(redSlider.getValue(), greenSlider.getValue(), blueSlider.getValue()));
    }

    /**
     * Applies HSV controls as a custom color.
     */
    private void applyHsvControlsAsActiveColor() {
        setActiveSwatch(CUSTOM_SWATCH_INDEX);
        float hue = hueSlider.getValue() / 359f;
        float saturation = saturationSlider.getValue() / 100f;
        float value = valueSlider.getValue() / 100f;
        setActiveBaseColor(new Color(Color.HSBtoRGB(hue, saturation, value)));
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
        redSlider.setValue(baseColor.getRed());
        redSpinner.setValue(Integer.valueOf(baseColor.getRed()));
        greenSlider.setValue(baseColor.getGreen());
        greenSpinner.setValue(Integer.valueOf(baseColor.getGreen()));
        blueSlider.setValue(baseColor.getBlue());
        blueSpinner.setValue(Integer.valueOf(baseColor.getBlue()));
    }

    /**
     * Updates the HSV controls to match the selected color.
     */
    private void updateHsvControlsFromBase() {
        float[] hsb = Color.RGBtoHSB(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), null);
        hueSlider.setValue(Math.min(359, Math.round(hsb[0] * 359f)));
        hueSpinner.setValue(Integer.valueOf(hueSlider.getValue()));
        saturationSlider.setValue(Math.round(hsb[1] * 100f));
        saturationSpinner.setValue(Integer.valueOf(saturationSlider.getValue()));
        valueSlider.setValue(Math.round(hsb[2] * 100f));
        valueSpinner.setValue(Integer.valueOf(valueSlider.getValue()));
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
        saturationSlider.setValue(Math.round(clamp01(x / (float) Math.max(1, colorPlane.getWidth() - 1)) * 100f));
        saturationSpinner.setValue(Integer.valueOf(saturationSlider.getValue()));
        valueSlider.setValue(Math.round((1f - clamp01(y / (float) Math.max(1, colorPlane.getHeight() - 1))) * 100f));
        valueSpinner.setValue(Integer.valueOf(valueSlider.getValue()));
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
        hueSlider.setValue(Math.round(clamp01(x / (float) Math.max(1, hueStrip.getWidth() - 1)) * 359f));
        hueSpinner.setValue(Integer.valueOf(hueSlider.getValue()));
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
     * Applies the direct ARGB color field.
     */
    private void applyArgbField() {
        if (syncingDirectColorFields) {
            return;
        }
        Color parsed = DrawColorFormat.parseArgbColor(Ui.trimmed(argbField));
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
            opacitySlider.setValue(color.getAlpha());
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
            argbField.setText(color.getAlpha() + ", " + color.getRed() + ", "
                    + color.getGreen() + ", " + color.getBlue());
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
        return activeRole() <= ROLE_BORDER ? opacitySlider.getValue() : 255;
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
     * Builds a compact row with two labelled spinner controls.
     *
     * @param firstLabel first control label
     * @param first first spinner
     * @param secondLabel second control label
     * @param second second spinner
     * @return paired spinner row
     */
    private static JComponent spinnerPairRow(String firstLabel, JSpinner first, String secondLabel, JSpinner second) {
        Dimension spinnerSize = new Dimension(78, Theme.CONTROL_HEIGHT);
        first.setPreferredSize(spinnerSize);
        first.setMinimumSize(spinnerSize);
        second.setPreferredSize(spinnerSize);
        second.setMinimumSize(spinnerSize);

        JPanel row = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        JLabel firstText = Ui.label(firstLabel);
        firstText.setPreferredSize(new Dimension(LABEL_WIDTH, Theme.CONTROL_HEIGHT));
        JLabel secondText = Ui.label(secondLabel);
        secondText.setPreferredSize(new Dimension(54, Theme.CONTROL_HEIGHT));
        row.add(firstText);
        row.add(first);
        row.add(Box.createHorizontalStrut(Theme.SPACE_SM));
        row.add(secondText);
        row.add(second);
        return row;
    }

    /**
     * Builds one RGB channel row.
     *
     * @param text row label
     * @param slider channel slider
     * @param spinner channel numeric input
     * @return row component
     */
    private static JComponent channelRow(String text, JSlider slider, JSpinner spinner) {
        JPanel controls = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        controls.add(slider, BorderLayout.CENTER);
        controls.add(spinner, BorderLayout.EAST);
        controls.setPreferredSize(new Dimension(CHANNEL_SLIDER_WIDTH + CHANNEL_SPINNER_WIDTH + Theme.SPACE_SM,
                Theme.CONTROL_HEIGHT));
        return controlRow(text, controls);
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
     * Creates one HSV channel spinner.
     *
     * @param value initial value
     * @param min minimum value
     * @param max maximum value
     * @return spinner
     */
    private static JSpinner hsvSpinner(int value, int min, int max) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, 1));
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
        JLabel text = Ui.label(label);
        text.setToolTipText(toggle.getToolTipText());
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
     * Creates a wrapping button panel.
     *
     * @param buttons buttons
     * @return button panel
     */
    private static JComponent wrappingButtons(JComponent... buttons) {
        JPanel row = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, Theme.SPACE_XS));
        for (JComponent button : buttons) {
            row.add(button);
        }
        return row;
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
                float hue = hueSlider.getValue() / 359f;
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
                int markerX = Math.round(saturationSlider.getValue() / 100f * (width - 1));
                int markerY = Math.round((1f - valueSlider.getValue() / 100f) * (height - 1));
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
                int markerX = Math.round(hueSlider.getValue() / 359f * (width - 1));
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
                        ? Theme.withAlpha(baseColor, opacitySlider.getValue())
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
                int inset = selectedSwatch == index ? 3 : 6;
                g.setColor(Theme.INPUT);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                g.setColor(selectedSwatch == index ? Theme.ACCENT : Theme.INPUT_BORDER);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                g.setColor(color);
                g.fillOval(inset, inset, getWidth() - inset * 2, getHeight() - inset * 2);
            } finally {
                g.dispose();
            }
        }
    }
}
