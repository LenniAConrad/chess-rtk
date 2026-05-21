package application.gui.workbench;

/**
 * Snapback/snap animation runtime state.
 */
final class SnapAnimation {

    /**
     * Piece being animated.
     */
    final byte piece;

    /**
     * Starting x coordinate.
     */
    final int startX;

    /**
     * Starting y coordinate.
     */
    final int startY;

    /**
     * Ending x coordinate.
     */
    final int endX;

    /**
     * Ending y coordinate.
     */
    final int endY;

    /**
     * Animation start time in epoch milliseconds.
     */
    final long startedAt;

    /**
     * Animation duration in milliseconds.
     */
    final int durationMs;

    /**
     * Whether this animation returns a dragged piece to its origin.
     */
    final boolean snapback;

    /**
     * Creates a snap animation.
     *
     * @param piece piece being animated
     * @param startX starting x coordinate
     * @param startY starting y coordinate
     * @param endX ending x coordinate
     * @param endY ending y coordinate
     * @param durationMs duration in milliseconds
     * @param snapback true when the piece returns to its origin
     */
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

    /**
     * Returns the normalized animation progress.
     *
     * @return progress from 0.0 to 1.0
     */
    double progress() {
        return Math.max(0.0, Math.min(1.0, (System.currentTimeMillis() - startedAt) / (double) durationMs));
    }
}
