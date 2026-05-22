/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.board;

/**
 * Snapback/snap animation runtime state.
 */
public final class SnapAnimation {

    /**
     * Piece being animated.
     */
    public final byte piece;

    /**
     * Starting x coordinate.
     */
    public final int startX;

    /**
     * Starting y coordinate.
     */
    public final int startY;

    /**
     * Ending x coordinate.
     */
    public final int endX;

    /**
     * Ending y coordinate.
     */
    public final int endY;

    /**
     * Animation start time in epoch milliseconds.
     */
    public final long startedAt;

    /**
     * Animation duration in milliseconds.
     */
    public final int durationMs;

    /**
     * Whether this animation returns a dragged piece to its origin.
     */
    public final boolean snapback;

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
    public SnapAnimation(byte piece, int startX, int startY, int endX, int endY, int durationMs, boolean snapback) {
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
    public double progress() {
        return Math.max(0.0, Math.min(1.0, (System.currentTimeMillis() - startedAt) / (double) durationMs));
    }
}
