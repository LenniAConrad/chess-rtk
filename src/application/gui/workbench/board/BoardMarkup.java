package application.gui.workbench.board;

import chess.core.Field;

/**
 * One arrow or circle markup overlay.
 *
 * @param from origin square
 * @param to target square, or {@link Field#NO_SQUARE} for a circle
 * @param brush annotation brush
 */
public record BoardMarkup(byte from, byte to, MarkupBrush brush) {

    /**
     * Returns whether this annotation is a circle marker.
     *
     * @return true for circles
     */
    public boolean isCircle() {
        return to == Field.NO_SQUARE;
    }
}
