package application.gui.workbench;

import chess.core.Field;

/**
 * One arrow or circle markup overlay.
 *
 * @param from origin square
 * @param to target square, or {@link Field#NO_SQUARE} for a circle
 * @param brush annotation brush
 */
record BoardMarkup(byte from, byte to, MarkupBrush brush) {

    /**
     * Returns whether this annotation is a circle marker.
     *
     * @return true for circles
     */
    boolean isCircle() {
        return to == Field.NO_SQUARE;
    }
}
