package application.gui.workbench.draw;

import application.gui.workbench.board.BoardExportActions;
import application.gui.workbench.board.BoardMarkupTool;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.board.MarkupBrush;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Drawing rail for the shared Workbench board.
 *
 * <p>The panel owns annotation controls only; the board remains the source of
 * truth for gestures, persistent markups, and export snapshots. That keeps the
 * Draw tab, right-click annotations in other board modes, and PNG/SVG export on
 * one renderer path.</p>
 */
public final class DrawPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Default annotation opacity, matching the built-in board gestures.
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
     * Fixed width for labels in compact Draw rail control rows.
     */
    private static final int LABEL_WIDTH = 72;

    /**
     * Selection marker for a custom color outside the preset swatch list.
     */
    private static final int CUSTOM_SWATCH_INDEX = -1;

    /**
     * Source board.
     */
    private final transient BoardPanel board;

    /**
     * Dialog/export owner.
     */
    private final transient Component owner;

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
     * Creates a draw rail for a board.
     *
     * @param board board to control
     * @param owner dialog and toast owner for exports
     */
    public DrawPanel(BoardPanel board, Component owner) {
        super(new GridBagLayout());
        this.board = Objects.requireNonNull(board, "board");
        this.owner = owner;
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setForeground(Theme.TEXT);
        setBorder(Theme.pad(Theme.SPACE_MD));
        build();
        applyTool();
        applyBrush();
    }

    /**
     * Builds the rail controls.
     */
    private void build() {
        GridBagConstraints c = Ui.constraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        int row = 0;
        gridSection("Tool", toolSection(), c, row++);
        gridSection("Color", colorSection(), c, row++);
        gridSection("Arrow Settings", arrowSection(), c, row++);
        gridSection("Export", exportSection(), c, row++);
        Ui.addVerticalFiller(this, c, row, 1);
    }

    /**
     * Adds one titled section to the rail.
     *
     * @param title section title
     * @param content section content
     * @param c constraints
     * @param row grid row
     */
    private void gridSection(String title, JComponent content, GridBagConstraints c, int row) {
        Ui.grid(this, Ui.collapsible(title, content, true), c, 0, row, 1, 1);
    }

    /**
     * Builds the arrow/circle picker.
     *
     * @return tool section
     */
    private JComponent toolSection() {
        JPanel panel = Ui.transparentPanel(new GridBagLayout());
        GridBagConstraints c = Ui.constraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        toolPicker.setOnSelect(index -> applyTool());
        toolPicker.setToolTipText("Pick arrow or circle drawing");
        Ui.grid(panel, controlRow("Shape", toolPicker), c, 0, 0, 1, 1);

        Ui.grid(panel, Ui.button("Clear drawings", false, event -> board.clearMarkup()),
                c, 0, 1, 1, 1);
        return panel;
    }

    /**
     * Builds color controls.
     *
     * @return color section
     */
    private JComponent colorSection() {
        JPanel panel = Ui.transparentPanel(new GridBagLayout());
        GridBagConstraints c = Ui.constraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        JPanel row = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        for (MarkupBrush brush : MarkupBrush.presetBrushes()) {
            Swatch swatch = new Swatch(brush, swatches.size());
            swatches.add(swatch);
            row.add(swatch);
        }
        row.add(Ui.button("Custom", false, event -> chooseCustomColor()));
        Ui.grid(panel, row, c, 0, 0, 1, 1);

        opacitySlider.setToolTipText("Annotation opacity");
        Ui.styleSlider(opacitySlider);
        opacitySlider.addChangeListener(event -> applyBrush());
        Ui.grid(panel, controlRow("Opacity", opacitySlider), c, 0, 1, 1, 1);
        return panel;
    }

    /**
     * Builds arrow settings.
     *
     * @return arrow settings section
     */
    private JComponent arrowSection() {
        JPanel panel = Ui.transparentPanel(new GridBagLayout());
        GridBagConstraints c = Ui.constraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        Ui.styleSpinner(widthSpinner);
        widthSpinner.setToolTipText("Arrow line width");
        widthSpinner.addChangeListener(event -> applyBrush());
        Ui.grid(panel, controlRow("Width", widthSpinner), c, 0, 0, 1, 1);

        suggestedToggle.setSelected(board.isShowSuggestedMoveArrow());
        suggestedToggle.setToolTipText("Include engine suggested-move arrows on the board and in exports");
        suggestedToggle.addActionListener(event -> board.setShowSuggestedMoveArrow(suggestedToggle.isSelected()));
        Ui.grid(panel, suggestedToggle, c, 0, 1, 1, 1);
        return panel;
    }

    /**
     * Builds export actions.
     *
     * @return export section
     */
    private JComponent exportSection() {
        JPanel panel = Ui.transparentPanel(new GridBagLayout());
        GridBagConstraints c = Ui.constraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        JPanel files = Ui.buttonRow(FlowLayout.LEFT,
                Ui.button("Save PNG", false, event -> BoardExportActions.exportPng(owner, board)),
                Ui.button("Save SVG", false, event -> BoardExportActions.exportSvg(owner, board)));
        JPanel clipboard = Ui.buttonRow(FlowLayout.LEFT,
                Ui.button("Copy image", false, event -> BoardExportActions.copyImage(owner, board)),
                Ui.button("Copy SVG", false, event -> BoardExportActions.copySvg(owner, board)));
        Ui.grid(panel, files, c, 0, 0, 1, 1);
        Ui.grid(panel, clipboard, c, 0, 1, 1, 1);
        return panel;
    }

    /**
     * Builds one compact labelled control row for the Draw rail.
     *
     * @param text row label
     * @param control row control
     * @return labelled control row
     */
    private static JComponent controlRow(String text, JComponent control) {
        return Ui.labelControlRow(text, control, LABEL_WIDTH);
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
        repaintSwatches();
    }

    /**
     * Returns the annotation shape selected in the tool picker.
     *
     * @return selected annotation shape
     */
    private BoardMarkupTool selectedTool() {
        return BoardMarkupTool.forIndex(toolPicker.getSelectedIndex());
    }

    /**
     * Returns the brush selected by the Draw controls. Default-width preset
     * colors stay preset brushes; non-default widths create exact custom
     * brushes.
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
     * Returns the selected arrow line width.
     *
     * @return line width
     */
    private int lineWidth() {
        Object value = widthSpinner.getValue();
        return value instanceof Number number ? Math.max(1, number.intValue()) : MarkupBrush.DEFAULT_LINE_WIDTH;
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
            this.color = opaque(brush.displayColor());
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
