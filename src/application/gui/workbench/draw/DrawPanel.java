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
import chess.core.Field;
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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
     * Lowest visible opacity allowed for annotation overlays.
     */
    private static final int MIN_ALPHA = 48;

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
     * Fixed width for compact Draw rail control rows.
     */
    private static final int LABEL_WIDTH = 78;

    /**
     * Compact height for the current-color preview card.
     */
    private static final int COLOR_PREVIEW_HEIGHT = 56;

    /**
     * Compact height for the current-stroke preview card.
     */
    private static final int STROKE_PREVIEW_HEIGHT = 38;

    /**
     * Maximum color sample size inside the current-color preview.
     */
    private static final int COLOR_PREVIEW_SWATCH_MAX = 36;

    /**
     * Preferred visible height for the annotation list.
     */
    private static final int ANNOTATION_LIST_HEIGHT = 146;

    /**
     * Soft light-theme annotation row selection.
     */
    private static final Color ANNOTATION_SELECTION_LIGHT = new Color(0xEAF4FF);

    /**
     * Soft dark-theme annotation row selection.
     */
    private static final Color ANNOTATION_SELECTION_DARK = new Color(0x253A52);

    /**
     * Selection marker for a custom color outside the preset swatch list.
     */
    private static final int CUSTOM_SWATCH_INDEX = -1;

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
    private Color baseColor = opaque(MarkupBrush.defaultBrush().displayColor());

    /**
     * Active swatch index.
     */
    private int selectedSwatch;

    /**
     * True while RGB widgets are being updated from code.
     */
    private boolean syncingColorControls;

    /**
     * Preset color swatches.
     */
    private final transient List<Swatch> swatches = new ArrayList<>();

    /**
     * Annotation shape picker.
     */
    private final ChipGroup toolPicker = new ChipGroup(BoardMarkupTool.labels());

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
     * Numeric RGB fields paired with the inline color sliders.
     */
    private final JSpinner redSpinner = channelSpinner(baseColor.getRed()),
            greenSpinner = channelSpinner(baseColor.getGreen()),
            blueSpinner = channelSpinner(baseColor.getBlue());

    /**
     * Arrow line width.
     */
    private final JSpinner widthSpinner =
            new JSpinner(new SpinnerNumberModel(MarkupBrush.DEFAULT_LINE_WIDTH, MIN_LINE_WIDTH, MAX_LINE_WIDTH, 1));

    /**
     * Suggested-engine-arrow export/display toggle.
     */
    private final ToggleBox suggestedToggle = new ToggleBox("", true);

    /**
     * Inline color preview.
     */
    private final ColorPreview colorPreview = new ColorPreview();

    /**
     * Stroke preview component.
     */
    private final StrokePreview strokePreview = new StrokePreview();

    /**
     * Annotation list model.
     */
    private final DefaultListModel<String> annotationListModel = new DefaultListModel<>();

    /**
     * Annotation list.
     */
    private final JList<String> annotationList = new JList<>(annotationListModel);

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
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setForeground(Theme.TEXT);
        setBorder(Theme.pad(Theme.SPACE_MD));
        build();
        installShortcuts();
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
        int arrows = 0;
        int circles = 0;
        for (BoardMarkup markup : board.boardMarkups()) {
            if (markup.isCircle()) {
                circles++;
            } else {
                arrows++;
            }
        }
        return "Annotation tool · " + arrows + " arrows · " + circles + " circles · PNG/SVG export ready";
    }

    /**
     * Builds the rail controls.
     */
    private void build() {
        JPanel stack = Ui.transparentPanel(null);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        addSection(stack, Ui.titled("Tool", toolSection()));
        addSection(stack, Ui.titled("Color Mixer", styleSection()));
        addSection(stack, Ui.titled("Brush", brushSection()));
        addSection(stack, Ui.titled("Annotation List", annotationSection()));
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
        toolPicker.setToolTipText("Pick arrow or circle drawing.");
        panel.add(controlRow("Shape", toolPicker));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        undoButton = Ui.button("Undo", false, event -> undo());
        redoButton = Ui.button("Redo", false, event -> redo());
        deleteButton = new HoldButton("Delete selected", this::deleteSelected, true);
        clearButton = new HoldButton("Clear all", this::clearAll, true);
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
        JPanel row = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        for (MarkupBrush brush : MarkupBrush.presetBrushes()) {
            addSwatch(row, brush.name(), opaque(brush.displayColor()));
        }
        addSwatch(row, "purple", new Color(0x8B, 0x5C, 0xD6));
        addSwatch(row, "cyan", new Color(0x00, 0x9F, 0xB7));
        addSwatch(row, "white", new Color(0xF6, 0xF6, 0xF6));
        addSwatch(row, "black", new Color(0x24, 0x24, 0x24));
        panel.add(row);

        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(colorPreview);
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        configureColorChannel(redSlider, redSpinner);
        configureColorChannel(greenSlider, greenSpinner);
        configureColorChannel(blueSlider, blueSpinner);
        panel.add(channelRow("Red", redSlider, redSpinner));
        panel.add(Box.createVerticalStrut(Theme.SPACE_XS));
        panel.add(channelRow("Green", greenSlider, greenSpinner));
        panel.add(Box.createVerticalStrut(Theme.SPACE_XS));
        panel.add(channelRow("Blue", blueSlider, blueSpinner));
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
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(strokePreview);

        Ui.styleSpinner(widthSpinner);
        widthSpinner.setToolTipText("Arrow line width.");
        widthSpinner.addChangeListener(event -> applyBrush());
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(controlRow("Width", widthSpinner));

        suggestedToggle.setSelected(board.isShowSuggestedMoveArrow());
        suggestedToggle.setToolTipText("Show the current engine suggested-move arrow on the board and include it in exports.");
        suggestedToggle.addActionListener(event -> board.setShowSuggestedMoveArrow(suggestedToggle.isSelected()));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(toggleRow("Suggested arrows", suggestedToggle));
        return panel;
    }

    /**
     * Builds annotation list controls.
     *
     * @return annotation section
     */
    private JComponent annotationSection() {
        JPanel panel = verticalPanel();
        annotationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        annotationList.setVisibleRowCount(6);
        Theme.list(annotationList);
        annotationList.setFont(Theme.mono(Theme.FONT_MONO));
        annotationList.setFixedCellHeight(22);
        annotationList.setSelectionBackground(annotationSelectionBackground());
        annotationList.setSelectionForeground(Theme.TEXT);
        annotationList.setBorder(Theme.pad(Theme.SPACE_XS, Theme.SPACE_SM));
        annotationList.addListSelectionListener(event -> updateActionState());

        annotationCards.add(Ui.emptyState("No annotations", "Draw arrows or circles on the board to list them here."),
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
        panel.add(Ui.buttonRow(FlowLayout.LEFT,
                Ui.button("Save PNG", true, event -> BoardExportActions.exportPng(owner, board)),
                Ui.button("Save SVG", false, event -> BoardExportActions.exportSvg(owner, board))));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(Ui.buttonRow(FlowLayout.LEFT,
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
        updateSelectedColorPreview();
        repaintSwatches();
        colorPreview.repaint();
        strokePreview.repaint();
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
        for (int index = 0; index < markups.size(); index++) {
            annotationListModel.addElement(annotationText(index + 1, markups.get(index)));
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
        Color color = Theme.withAlpha(baseColor, opacitySlider.getValue());
        int lineWidth = lineWidth();
        return lineWidth == MarkupBrush.DEFAULT_LINE_WIDTH
                ? MarkupBrush.forColor(color)
                : MarkupBrush.custom(color, lineWidth);
    }

    /**
     * Updates selected color preview details.
     */
    private void updateSelectedColorPreview() {
        String readout = colorLabel(baseColor) + " RGB "
                + baseColor.getRed() + " / " + baseColor.getGreen() + " / " + baseColor.getBlue()
                + " opacity " + opacitySlider.getValue() + "/255";
        colorPreview.setToolTipText("Current draw color: " + readout);
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
        selectedSwatch = index;
        baseColor = opaque(color);
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
            syncingColorControls = false;
            applyColorFromChannels();
        });
        spinner.addChangeListener(event -> {
            if (syncingColorControls) {
                return;
            }
            syncingColorControls = true;
            slider.setValue(channelValue(spinner));
            syncingColorControls = false;
            applyColorFromChannels();
        });
    }

    /**
     * Applies RGB controls as a custom color.
     */
    private void applyColorFromChannels() {
        selectedSwatch = CUSTOM_SWATCH_INDEX;
        baseColor = new Color(redSlider.getValue(), greenSlider.getValue(), blueSlider.getValue());
        applyBrush();
    }

    /**
     * Updates the RGB controls to match the selected swatch.
     */
    private void updateColorControlsFromBase() {
        syncingColorControls = true;
        redSlider.setValue(baseColor.getRed());
        redSpinner.setValue(Integer.valueOf(baseColor.getRed()));
        greenSlider.setValue(baseColor.getGreen());
        greenSpinner.setValue(Integer.valueOf(baseColor.getGreen()));
        blueSlider.setValue(baseColor.getBlue());
        blueSpinner.setValue(Integer.valueOf(baseColor.getBlue()));
        syncingColorControls = false;
    }

    /**
     * Returns one numeric RGB channel value.
     *
     * @param spinner source spinner
     * @return clamped channel value
     */
    private static int channelValue(JSpinner spinner) {
        Object value = spinner.getValue();
        if (value instanceof Number number) {
            return Math.max(0, Math.min(255, number.intValue()));
        }
        return 0;
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
     * Returns the draw rail's softer annotation selection color.
     *
     * @return annotation list selection color
     */
    private static Color annotationSelectionBackground() {
        return Theme.isDark() ? ANNOTATION_SELECTION_DARK : ANNOTATION_SELECTION_LIGHT;
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
     * Returns one annotation list row.
     *
     * @param number one-based row number
     * @param markup markup
     * @return list row text
     */
    private static String annotationText(int number, BoardMarkup markup) {
        String from = Field.toString(markup.from());
        String shape = markup.isCircle() ? "Circle" : "Arrow";
        String target = markup.isCircle() ? from : Field.toString(markup.to());
        return number + ". " + shape + " " + from + (markup.isCircle() ? "" : " -> " + target)
                + " · " + markup.brush().name();
    }

    /**
     * Returns an opaque version of a color.
     *
     * @param color source color
     * @return opaque color
     */
    private static Color opaque(Color color) {
        return color == null ? Color.GREEN : new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Returns a color label.
     *
     * @param color color
     * @return color label
     */
    private static String colorLabel(Color color) {
        Color value = opaque(color);
        return "#" + String.format("%02X%02X%02X", value.getRed(), value.getGreen(), value.getBlue());
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
                paintChecker(g, x, y, swatch, swatch);
                g.setColor(Theme.withAlpha(baseColor, opacitySlider.getValue()));
                g.fillRoundRect(x, y, swatch, swatch, Theme.RADIUS, Theme.RADIUS);
                g.setColor(Theme.withAlpha(Theme.TEXT, 80));
                g.drawRoundRect(x, y, swatch, swatch, Theme.RADIUS, Theme.RADIUS);

                g.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
                g.setColor(Theme.TEXT);
                g.drawString("Color", x + swatch + Theme.SPACE_MD, y + 17);
                g.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
                g.setColor(Theme.MUTED);
                g.drawString(colorLabel(baseColor), x + swatch + Theme.SPACE_MD, y + 36);
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
     * Stroke preview.
     */
    private final class StrokePreview extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the preview.
         */
        StrokePreview() {
            setPreferredSize(new Dimension(220, STROKE_PREVIEW_HEIGHT));
            setMinimumSize(new Dimension(120, STROKE_PREVIEW_HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, STROKE_PREVIEW_HEIGHT));
            setToolTipText("Current stroke preview.");
        }

        /**
         * Paints the preview.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.INPUT);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                g.setColor(Theme.LINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                MarkupBrush brush = selectedAnnotationBrush();
                g.setColor(brush.displayColor());
                int y = getHeight() / 2;
                int width = Math.max(3, brush.lineWidth());
                g.setStroke(new java.awt.BasicStroke(width, java.awt.BasicStroke.CAP_ROUND,
                        java.awt.BasicStroke.JOIN_ROUND));
                int start = Theme.SPACE_LG;
                int end = Math.max(start, getWidth() - Theme.SPACE_LG - 20);
                g.drawLine(start, y, end, y);
                java.awt.Polygon arrow = new java.awt.Polygon(
                        new int[] { end, end - 16, end - 16 },
                        new int[] { y, y - 9, y + 9 },
                        3);
                g.fillPolygon(arrow);
                g.drawOval(Math.max(start, getWidth() - Theme.SPACE_XL), y - 9, 18, 18);
                g.setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
                g.setColor(Theme.TEXT);
                g.drawString(width + " px", Theme.SPACE_LG, Math.max(12, y - width));
            } finally {
                g.dispose();
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
            this.color = opaque(color);
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
