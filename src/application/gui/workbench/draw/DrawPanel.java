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
import javax.swing.JColorChooser;
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
     * Fixed width for compact Draw rail control rows.
     */
    private static final int LABEL_WIDTH = 78;

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
     * Arrow line width.
     */
    private final JSpinner widthSpinner =
            new JSpinner(new SpinnerNumberModel(MarkupBrush.DEFAULT_LINE_WIDTH, MIN_LINE_WIDTH, MAX_LINE_WIDTH, 1));

    /**
     * Suggested-engine-arrow export/display toggle.
     */
    private final ToggleBox suggestedToggle = new ToggleBox("Suggested arrows", true);

    /**
     * Snap status toggle. Board gestures are square-based by design.
     */
    private final ToggleBox snapToggle = new ToggleBox("Snap to square", true);

    /**
     * Selected color label.
     */
    private final JLabel selectedColorLabel = new JLabel();

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
        addSection(stack, Ui.card("Tool", toolSection()));
        addSection(stack, Ui.card("Style", styleSection()));
        addSection(stack, Ui.card("Arrow Settings", arrowSection()));
        addSection(stack, Ui.card("Annotation List", annotationSection()));
        addSection(stack, Ui.card("Export", exportSection()));
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
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(Ui.caption("Shortcuts: Ctrl+Z undo, Ctrl+Y redo, Delete removes the selected annotation."));
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
            Swatch swatch = new Swatch(brush, swatches.size());
            swatches.add(swatch);
            row.add(swatch);
        }
        row.add(Ui.button("Custom", false, event -> chooseCustomColor()));
        panel.add(row);

        selectedColorLabel.setForeground(Theme.TEXT);
        selectedColorLabel.setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(selectedColorLabel);
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));

        opacitySlider.setToolTipText("Annotation opacity.");
        Ui.styleSlider(opacitySlider);
        opacitySlider.addChangeListener(event -> applyBrush());
        panel.add(controlRow("Opacity", opacitySlider));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(strokePreview);
        return panel;
    }

    /**
     * Builds arrow settings.
     *
     * @return arrow settings section
     */
    private JComponent arrowSection() {
        JPanel panel = verticalPanel();
        Ui.styleSpinner(widthSpinner);
        widthSpinner.setToolTipText("Arrow line width.");
        widthSpinner.addChangeListener(event -> applyBrush());
        panel.add(controlRow("Width", widthSpinner));

        suggestedToggle.setSelected(board.isShowSuggestedMoveArrow());
        suggestedToggle.setToolTipText("Show the current engine suggested-move arrow on the board and include it in exports.");
        suggestedToggle.addActionListener(event -> board.setShowSuggestedMoveArrow(suggestedToggle.isSelected()));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(suggestedToggle);
        panel.add(Ui.caption("Suggested arrows come from the board's current engine suggestion, not from Draw annotations."));

        snapToggle.setSelected(true);
        snapToggle.setEnabled(false);
        snapToggle.setToolTipText("Board annotations already begin and end on exact board squares.");
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(snapToggle);
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
        annotationList.setBackground(Theme.INPUT);
        annotationList.setForeground(Theme.TEXT);
        annotationList.setFont(Theme.mono(Theme.FONT_MONO));
        annotationList.setBorder(Theme.pad(Theme.SPACE_SM));
        annotationList.addListSelectionListener(event -> updateActionState());

        annotationCards.add(Ui.emptyState("No annotations", "Draw arrows or circles on the board to list them here."),
                ANNOTATIONS_EMPTY);
        JScrollPane listScroll = Ui.scroll(annotationList, () -> Theme.INPUT);
        listScroll.setPreferredSize(new Dimension(240, 154));
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
        updateSelectedColorLabel();
        repaintSwatches();
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
     * Updates selected color label.
     */
    private void updateSelectedColorLabel() {
        selectedColorLabel.setText("Selected color: " + colorLabel(baseColor)
                + " · " + opacitySlider.getValue() + "/255 opacity");
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
     * Opens a custom color chooser.
     */
    private void chooseCustomColor() {
        Color chosen = JColorChooser.showDialog(this, "Draw Color", baseColor);
        if (chosen == null) {
            return;
        }
        selectedSwatch = CUSTOM_SWATCH_INDEX;
        baseColor = opaque(chosen);
        applyBrush();
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
        applyBrush();
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
            setPreferredSize(new Dimension(220, 44));
            setMinimumSize(new Dimension(120, 44));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
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
                g.drawLine(Theme.SPACE_LG, y, Math.max(Theme.SPACE_LG, getWidth() - Theme.SPACE_LG), y);
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
         * @param brush source preset brush
         * @param index swatch index
         */
        Swatch(MarkupBrush brush, int index) {
            color = opaque(brush.displayColor());
            this.index = index;
            Dimension size = new Dimension(SWATCH_SIZE, Theme.CONTROL_HEIGHT);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            setToolTipText("Use " + brush.name());
            setBorder(BorderFactory.createEmptyBorder());
            addMouseListener(new java.awt.event.MouseAdapter() {
                /**
                 * Selects this color swatch.
                 *
                 * @param event mouse event
                 */
                @Override
                public void mousePressed(java.awt.event.MouseEvent event) {
                    selectSwatch(index, color);
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
