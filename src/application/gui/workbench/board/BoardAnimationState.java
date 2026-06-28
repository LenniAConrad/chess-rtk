package application.gui.workbench.board;

import chess.core.Field;
import chess.core.Piece;

/**
 * Owns mutable board-animation state and timing defaults.
 */
final class BoardAnimationState {

    /**
     * Default move-animation duration in milliseconds.
     */
    static final int DEFAULT_MOVE_ANIMATION_MS = 95;

    /**
     * Default snapback-animation duration in milliseconds.
     */
    private static final int DEFAULT_SNAPBACK_ANIMATION_MS = 90;

    /**
     * Default snap-to-square animation duration in milliseconds.
     */
    private static final int DEFAULT_SNAP_ANIMATION_MS = 55;

    /**
     * Default board-flip animation duration in milliseconds.
     */
    private static final int DEFAULT_FLIP_ANIMATION_MS = 140;

    /**
     * Wrong-move marker animation duration in milliseconds.
     */
    private static final int WRONG_MOVE_MARKER_MS = 320;

    /**
     * Wrong-move marker retention after the bounce reaches its final frame.
     */
    private static final int WRONG_MOVE_MARKER_CLEAR_MS = 900;

    /**
     * Pieces involved in the current move animation.
     */
    private byte animatedMovePiece = Piece.EMPTY, animatedSecondaryMovePiece = Piece.EMPTY,
            animatedCapturePiece = Piece.EMPTY;

    /**
     * Squares involved in the current move animation.
     */
    private byte animatedMoveFrom = Field.NO_SQUARE, animatedMoveTo = Field.NO_SQUARE,
            animatedSecondaryMoveFrom = Field.NO_SQUARE, animatedSecondaryMoveTo = Field.NO_SQUARE,
            animatedCaptureSquare = Field.NO_SQUARE;

    /**
     * Move-animation start timestamp.
     */
    private long moveAnimationStartedAt;

    /**
     * True while a move animation is running.
     */
    private boolean moveAnimationActive;

    /**
     * Active snap animation.
     */
    private SnapAnimation snapAnimation;

    /**
     * Square hidden while a snap animation paints the moving piece.
     */
    private byte snapHiddenSquare = Field.NO_SQUARE;

    /**
     * True to skip the next move animation.
     */
    private boolean suppressNextMoveAnimation;

    /**
     * Flip animation progress.
     */
    private double flipAnimationProgress = Double.NaN;

    /**
     * Flip animation start timestamp.
     */
    private long flipAnimationStartedAt;

    /**
     * True when a flip animation is pending completion.
     */
    private boolean flipPending;

    /**
     * Animation durations in milliseconds.
     */
    private int moveAnimationMs = DEFAULT_MOVE_ANIMATION_MS,
            snapbackAnimationMs = DEFAULT_SNAPBACK_ANIMATION_MS,
            snapAnimationMs = DEFAULT_SNAP_ANIMATION_MS,
            flipAnimationMs = DEFAULT_FLIP_ANIMATION_MS;

    /**
     * True when animations are enabled.
     */
    private boolean animationsEnabled = true;

    /**
     * Wrong-move marker square and start timestamp.
     */
    private byte wrongMoveMarkerSquare = Field.NO_SQUARE;

    /**
     * Wrong-move marker start timestamp.
     */
    private long wrongMoveMarkerStartedAt;

    /**
     * Returns whether board animations are enabled.
     *
     * @return true when animations are enabled
     */
    boolean animationsEnabled() {
        return animationsEnabled;
    }

    /**
     * Sets whether board animations are enabled.
     *
     * @param enabled true to enable animations
     */
    void setAnimationsEnabled(boolean enabled) {
        animationsEnabled = enabled;
    }

    /**
     * Sets animation durations and derives the enabled flag from them.
     *
     * @param moveMs move animation duration
     * @param snapbackMs snapback animation duration
     * @param snapMs snap animation duration
     * @param flipMs flip animation duration
     */
    void setAnimationSpeeds(int moveMs, int snapbackMs, int snapMs, int flipMs) {
        moveAnimationMs = Math.max(0, moveMs);
        snapbackAnimationMs = Math.max(0, snapbackMs);
        snapAnimationMs = Math.max(0, snapMs);
        flipAnimationMs = Math.max(0, flipMs);
        animationsEnabled = moveAnimationMs > 0
                || snapbackAnimationMs > 0
                || snapAnimationMs > 0
                || flipAnimationMs > 0;
    }

    /**
     * Returns whether move animation is currently possible.
     *
     * @return true when move animation can run
     */
    boolean canAnimateMove() {
        return animationsEnabled && moveAnimationMs > 0;
    }

    /**
     * Returns whether flip animation is currently possible.
     *
     * @return true when flip animation can run
     */
    boolean canAnimateFlip() {
        return animationsEnabled && flipAnimationMs > 0;
    }

    /**
     * Returns the snapback animation duration.
     *
     * @return duration in milliseconds
     */
    int snapbackAnimationMs() {
        return snapbackAnimationMs;
    }

    /**
     * Returns the snap-to-square animation duration.
     *
     * @return duration in milliseconds
     */
    int snapAnimationMs() {
        return snapAnimationMs;
    }

    /**
     * Returns the flip animation duration.
     *
     * @return duration in milliseconds
     */
    int flipAnimationMs() {
        return flipAnimationMs;
    }

    /**
     * Returns the duration for one snap animation.
     *
     * @param snapback true for snapback animation
     * @return duration in milliseconds
     */
    int snapDuration(boolean snapback) {
        return snapback ? snapbackAnimationMs : snapAnimationMs;
    }

    /**
     * Starts the primary move animation.
     *
     * @param piece moving piece
     * @param from origin square
     * @param to target square
     * @param startedAt start timestamp
     */
    void startMove(byte piece, byte from, byte to, long startedAt) {
        animatedMovePiece = piece;
        animatedMoveFrom = from;
        animatedMoveTo = to;
        moveAnimationStartedAt = startedAt;
        moveAnimationActive = true;
    }

    /**
     * Sets the capture fade-out animation.
     *
     * @param piece captured piece
     * @param square capture square
     */
    void setAnimatedCapture(byte piece, byte square) {
        animatedCapturePiece = piece;
        animatedCaptureSquare = square;
    }

    /**
     * Sets the secondary move animation, used by castling rooks.
     *
     * @param piece moving piece
     * @param from origin square
     * @param to target square
     */
    void setAnimatedSecondaryMove(byte piece, byte from, byte to) {
        animatedSecondaryMovePiece = piece;
        animatedSecondaryMoveFrom = from;
        animatedSecondaryMoveTo = to;
    }

    /**
     * Clears the move animation.
     */
    void clearMoveAnimation() {
        animatedMovePiece = Piece.EMPTY;
        animatedSecondaryMovePiece = Piece.EMPTY;
        animatedCapturePiece = Piece.EMPTY;
        animatedMoveFrom = Field.NO_SQUARE;
        animatedMoveTo = Field.NO_SQUARE;
        animatedSecondaryMoveFrom = Field.NO_SQUARE;
        animatedSecondaryMoveTo = Field.NO_SQUARE;
        animatedCaptureSquare = Field.NO_SQUARE;
        moveAnimationStartedAt = 0L;
        moveAnimationActive = false;
    }

    /**
     * Returns move-animation progress.
     *
     * @param now current timestamp
     * @return clamped progress from zero to one
     */
    double moveAnimationProgress(long now) {
        if (!moveAnimationActive || moveAnimationStartedAt == 0L) {
            return 1.0;
        }
        double elapsed = (double) now - (double) moveAnimationStartedAt;
        return Math.max(0.0, Math.min(1.0, elapsed / Math.max(1.0, moveAnimationMs)));
    }

    /**
     * Returns whether move animation is active.
     *
     * @return true while active
     */
    boolean moveAnimationActive() {
        return moveAnimationActive;
    }

    /**
     * Returns the animated move piece.
     *
     * @return piece code
     */
    byte animatedMovePiece() {
        return animatedMovePiece;
    }

    /**
     * Returns the animated move origin square.
     *
     * @return origin square
     */
    byte animatedMoveFrom() {
        return animatedMoveFrom;
    }

    /**
     * Returns the animated move target square.
     *
     * @return target square
     */
    byte animatedMoveTo() {
        return animatedMoveTo;
    }

    /**
     * Returns the secondary animated piece.
     *
     * @return piece code
     */
    byte animatedSecondaryMovePiece() {
        return animatedSecondaryMovePiece;
    }

    /**
     * Returns the secondary animated origin square.
     *
     * @return origin square
     */
    byte animatedSecondaryMoveFrom() {
        return animatedSecondaryMoveFrom;
    }

    /**
     * Returns the secondary animated target square.
     *
     * @return target square
     */
    byte animatedSecondaryMoveTo() {
        return animatedSecondaryMoveTo;
    }

    /**
     * Returns the capture fade piece.
     *
     * @return piece code
     */
    byte animatedCapturePiece() {
        return animatedCapturePiece;
    }

    /**
     * Returns the capture fade square.
     *
     * @return capture square
     */
    byte animatedCaptureSquare() {
        return animatedCaptureSquare;
    }

    /**
     * Marks the next position update as already animated by snap.
     */
    void suppressNextMoveAnimation() {
        suppressNextMoveAnimation = true;
    }

    /**
     * Consumes the pending move-animation suppression flag.
     *
     * @return true when the next move animation should be skipped
     */
    boolean consumeSuppressNextMoveAnimation() {
        boolean suppress = suppressNextMoveAnimation;
        suppressNextMoveAnimation = false;
        return suppress;
    }

    /**
     * Starts a snap animation.
     *
     * @param animation snap animation state
     * @param hiddenSquare square hidden under the moving piece
     */
    void startSnapAnimation(SnapAnimation animation, byte hiddenSquare) {
        snapAnimation = animation;
        snapHiddenSquare = hiddenSquare;
    }

    /**
     * Returns the active snap animation.
     *
     * @return snap animation, or null
     */
    SnapAnimation snapAnimation() {
        return snapAnimation;
    }

    /**
     * Returns and clears a finished snap animation.
     *
     * @return finished snap animation, or null
     */
    SnapAnimation takeFinishedSnapAnimation() {
        if (snapAnimation == null || snapAnimation.progress() < 1.0) {
            return null;
        }
        SnapAnimation finished = snapAnimation;
        snapAnimation = null;
        snapHiddenSquare = Field.NO_SQUARE;
        return finished;
    }

    /**
     * Returns the square hidden by the snap animation.
     *
     * @return hidden square, or {@link Field#NO_SQUARE}
     */
    byte snapHiddenSquare() {
        return snapHiddenSquare;
    }

    /**
     * Clears the active snap animation.
     */
    void clearSnapAnimation() {
        snapAnimation = null;
        snapHiddenSquare = Field.NO_SQUARE;
    }

    /**
     * Starts a flip animation.
     *
     * @param startedAt start timestamp
     */
    void startFlipAnimation(long startedAt) {
        flipAnimationStartedAt = startedAt;
        flipAnimationProgress = 0.0;
        flipPending = true;
    }

    /**
     * Returns whether the flip midpoint still needs committing.
     *
     * @return true when pending
     */
    boolean flipPending() {
        return flipPending;
    }

    /**
     * Clears the pending flip flag.
     */
    void clearFlipPending() {
        flipPending = false;
    }

    /**
     * Returns flip animation progress.
     *
     * @return progress, or {@link Double#NaN} when no flip is active
     */
    double flipAnimationProgress() {
        return flipAnimationProgress;
    }

    /**
     * Advances the flip animation.
     *
     * @param now current timestamp
     * @return true when the board orientation should be toggled
     */
    boolean tickFlipAnimation(long now) {
        if (Double.isNaN(flipAnimationProgress)) {
            return false;
        }
        double elapsed = (double) now - (double) flipAnimationStartedAt;
        flipAnimationProgress = Math.max(0.0, Math.min(1.0, elapsed / Math.max(1.0, flipAnimationMs)));
        boolean shouldToggle = false;
        if (flipPending && flipAnimationProgress >= 0.5) {
            shouldToggle = true;
            flipPending = false;
        }
        if (flipAnimationProgress >= 1.0) {
            flipAnimationProgress = Double.NaN;
            flipPending = false;
        }
        return shouldToggle;
    }

    /**
     * Clears the flip animation.
     */
    void clearFlipAnimation() {
        flipAnimationStartedAt = 0L;
        flipAnimationProgress = Double.NaN;
        flipPending = false;
    }

    /**
     * Shows the wrong-move marker.
     *
     * @param square target square
     * @param startedAt start timestamp
     */
    void showWrongMoveMarker(byte square, long startedAt) {
        wrongMoveMarkerSquare = square;
        wrongMoveMarkerStartedAt = startedAt;
    }

    /**
     * Returns the wrong-move marker square.
     *
     * @return marker square, or {@link Field#NO_SQUARE}
     */
    byte wrongMoveMarkerSquare() {
        return wrongMoveMarkerSquare;
    }

    /**
     * Sets the wrong-move marker start timestamp for tests.
     *
     * @param startedAt marker start timestamp
     */
    void setWrongMoveMarkerStartedAt(long startedAt) {
        wrongMoveMarkerStartedAt = startedAt;
    }

    /**
     * Returns wrong-move marker progress.
     *
     * @param now current timestamp
     * @return clamped progress from zero to one
     */
    double wrongMoveMarkerProgress(long now) {
        if (wrongMoveMarkerSquare == Field.NO_SQUARE || wrongMoveMarkerStartedAt == 0L) {
            return 1.0;
        }
        double elapsed = (double) now - (double) wrongMoveMarkerStartedAt;
        return Math.max(0.0, Math.min(1.0, elapsed / Math.max(1.0, WRONG_MOVE_MARKER_MS)));
    }

    /**
     * Returns whether the wrong-move marker should be cleared.
     *
     * @param now current timestamp
     * @return true when the marker has expired
     */
    boolean wrongMoveMarkerExpired(long now) {
        return wrongMoveMarkerSquare != Field.NO_SQUARE
                && wrongMoveMarkerStartedAt != 0L
                && now - wrongMoveMarkerStartedAt >= WRONG_MOVE_MARKER_CLEAR_MS;
    }

    /**
     * Clears the wrong-move marker.
     */
    void clearWrongMoveMarker() {
        wrongMoveMarkerSquare = Field.NO_SQUARE;
        wrongMoveMarkerStartedAt = 0L;
    }

    /**
     * Clears all active animations.
     *
     * @return true when a pending flip should be committed first
     */
    boolean clearAllAnimations() {
        boolean commitFlip = flipPending;
        clearMoveAnimation();
        clearSnapAnimation();
        clearFlipAnimation();
        clearWrongMoveMarker();
        return commitFlip;
    }

    /**
     * Returns whether any animation is still active.
     *
     * @return true when at least one animation is active
     */
    boolean hasActiveAnimation() {
        return moveAnimationActive
                || snapAnimation != null
                || !Double.isNaN(flipAnimationProgress)
                || wrongMoveMarkerSquare != Field.NO_SQUARE;
    }
}
