package application.gui.workbench.board;

import chess.core.Field;

/**
 * One user markup overlay.
 *
 * @param tool annotation shape
 * @param from origin square
 * @param to target square, or {@link Field#NO_SQUARE} for single-square shapes
 * @param brush annotation brush
 */
public record BoardMarkup(BoardMarkupTool tool, byte from, byte to, MarkupBrush brush) {

    /**
     * Creates a legacy arrow/circle markup from endpoints.
     *
     * @param from origin square
     * @param to target square, or {@link Field#NO_SQUARE} for a circle
     * @param brush annotation brush
     */
    public BoardMarkup(byte from, byte to, MarkupBrush brush) {
        this(to == Field.NO_SQUARE ? BoardMarkupTool.CIRCLE : BoardMarkupTool.ARROW, from, to, brush);
    }

    /**
     * Normalizes nullable shapes.
     *
     * @param tool markup drawing tool
     * @param from source square index
     * @param to destination square index
     * @param brush markup brush style
     */
    public BoardMarkup {
        tool = tool == null ? (to == Field.NO_SQUARE ? BoardMarkupTool.CIRCLE : BoardMarkupTool.ARROW) : tool;
    }

    /**
     * Returns whether this annotation is a circle marker.
     *
     * @return true for circles
     */
    public boolean isCircle() {
        return tool == BoardMarkupTool.CIRCLE;
    }

    /**
     * Returns whether this annotation is an arrow.
     *
     * @return true for arrows
     */
    public boolean isArrow() {
        return tool == BoardMarkupTool.ARROW;
    }

    /**
     * Returns whether this annotation is a filled board rectangle.
     *
     * @return true for rectangles
     */
    public boolean isRectangle() {
        return tool == BoardMarkupTool.RECTANGLE;
    }

    /**
     * Returns whether this annotation is a chess glyph label.
     *
     * @return true for glyph labels
     */
    public boolean isGlyph() {
        return tool == BoardMarkupTool.GLYPH;
    }
}
