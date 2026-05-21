package application.gui.workbench;

import java.awt.Color;

import chess.core.Field;

/**
 * Interaction value types used by {@link WorkbenchBoardPanel}.
 */
final class WorkbenchBoardInteractions {

    /**
     * Utility class.
     */
    private WorkbenchBoardInteractions() {
    }
}

/**
 * Annotation brush.
 *
 * @param name brush name
 * @param color brush color
 * @param lineWidth Chessground-style line width
 */
record MarkupBrush(String name, Color color, int lineWidth) { }

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

/**
 * Context handed to a drag-start filter.
 *
 * @param square origin square
 * @param piece piece on the origin square
 * @param fen current FEN
 */
record DragContext(byte square, byte piece, String fen) { }

/**
 * Context handed to a drop resolver.
 *
 * @param fromSquare origin square
 * @param toSquare drop square ({@link Field#NO_SQUARE} when off-board)
 * @param piece piece being dropped
 * @param fen FEN before the drop
 * @param defaultMove first matching legal move, or no move
 */
record DropContext(byte fromSquare, byte toSquare, byte piece, String fen, short defaultMove) { }

/**
 * Snapback/snap animation runtime state.
 */
final class SnapAnimation {

    final byte piece;
    final int startX;
    final int startY;
    final int endX;
    final int endY;
    final long startedAt;
    final int durationMs;
    final boolean snapback;

    SnapAnimation(byte piece, int startX, int startY, int endX, int endY, int durationMs, boolean snapback) {
        this.piece = piece;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.durationMs = Math.max(1, durationMs);
        this.startedAt = System.currentTimeMillis();
        this.snapback = snapback;
    }

    double progress() {
        return Math.max(0.0, Math.min(1.0, (System.currentTimeMillis() - startedAt) / (double) durationMs));
    }
}
