package chess.core;

/**
 * Package-local storage and accessors backing {@link Position.State}.
 */
class PositionUndoState {

    /**
     * Piece index moved by the encoded move.
     */
    int moving;

    /**
     * Captured piece index, or -1 when the move was not a capture.
     */
    int captured;

    /**
     * Square where the captured piece was removed.
     *
     * <p>
     * This differs from the move target square for en-passant captures.
     * </p>
     */
    int capturedSquare;

    /**
     * Actual king target square for a castling move.
     */
    int kingTo;

    /**
     * Castling-right mask before the move was applied.
     */
    int castlingRights;

    /**
     * En-passant target square before the move was applied.
     */
    byte enPassantSquare;

    /**
     * Halfmove clock before the move was applied.
     */
    short halfMoveClock;

    /**
     * Fullmove number before the move was applied.
     */
    short fullMoveNumber;

    /**
     * Side-to-move flag before the move was applied.
     */
    boolean whiteToMove;

    /**
     * Castling rook piece index, or -1 when the move was not castling.
     */
    int rook;

    /**
     * Castling rook origin square.
     */
    int rookFrom;

    /**
     * Castling rook target square.
     */
    int rookTo;

    /**
     * Whether the move captured by en-passant.
     */
    boolean enPassantCapture;

    /**
     * Whether the move was castling.
     */
    boolean castle;

    /**
     * Returns whether the move captured a piece.
     *
     * @return true when captured
     */
    public boolean capture() {
        return captured >= 0;
    }

    /**
     * Returns the moved internal piece index.
     *
     * @return moved piece index
     */
    public int movingPiece() {
        return moving;
    }

    /**
     * Returns the captured internal piece index.
     *
     * @return captured piece index, or {@code -1}
     */
    public int capturedPiece() {
        return captured;
    }

    /**
     * Returns the square from which the captured piece was removed.
     *
     * @return captured square, or {@link Field#NO_SQUARE}
     */
    public int capturedSquare() {
        return capturedSquare;
    }

    /**
     * Returns the final king target square used by the move.
     *
     * <p>
     * For non-castling moves this is the move's normal destination square.
     * </p>
     *
     * @return actual destination square
     */
    public int actualToSquare() {
        return kingTo;
    }

    /**
     * Returns the side to move before the move was applied.
     *
     * @return true when White moved
     */
    public boolean whiteToMove() {
        return whiteToMove;
    }

    /**
     * Returns the castling rook piece index.
     *
     * @return rook piece index, or {@code -1}
     */
    public int rookPiece() {
        return rook;
    }

    /**
     * Returns the castling rook origin square.
     *
     * @return rook origin, or {@link Field#NO_SQUARE}
     */
    public int rookFromSquare() {
        return rookFrom;
    }

    /**
     * Returns the castling rook target square.
     *
     * @return rook target, or {@link Field#NO_SQUARE}
     */
    public int rookToSquare() {
        return rookTo;
    }

    /**
     * Returns whether the move was an en-passant capture.
     *
     * @return true when en-passant
     */
    public boolean enPassantCapture() {
        return enPassantCapture;
    }

    /**
     * Returns whether the move was standard castling.
     *
     * @return true when castling
     */
    public boolean castle() {
        return castle;
    }
}
