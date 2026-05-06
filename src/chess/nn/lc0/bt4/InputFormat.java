package chess.nn.lc0.bt4;

/**
 * LCZero 112-plane input variants used by attention-body networks.
 *
 * <p>
 * BT4 networks use the same 8-history-slot, 112-plane board representation as
 * LC0, then reinterpret it as 64 square tokens for the transformer body.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public enum InputFormat {

    /**
     * Legacy LC0 112-plane format with castling rights encoded as constant planes
     * and side-to-move encoded as a constant plane.
     */
    CLASSICAL_112(false, false, false, false),

    /**
     * LC0 112-plane format with castling rook-location planes.
     */
    CASTLING_PLANE_112(true, false, false, false),

    /**
     * Modern LC0 attention-body input: castling rook-location planes, en-passant
     * target plane, halfmove clock scaled to hectoplies, and canonical transform.
     */
    BT4_CANONICAL_112(true, true, true, true);

    /**
     * Whether castling is encoded as rook-location planes.
     */
    private final boolean castlingPlane;

    /**
     * Whether aux plane 108 stores the en-passant target instead of side-to-move.
     */
    private final boolean enPassantPlane;

    /**
     * Whether the halfmove clock is scaled by 100.
     */
    private final boolean hectoplies;

    /**
     * Whether a deterministic LC0 canonical board transform is applied.
     */
    private final boolean canonical;

    /**
     * Creates one input format descriptor.
     *
     * @param castlingPlane true for rook-location castling planes
     * @param enPassantPlane true for en-passant target plane
     * @param hectoplies true to divide rule-50 by 100
     * @param canonical true to apply LC0 canonical spatial transform
     */
    InputFormat(boolean castlingPlane, boolean enPassantPlane, boolean hectoplies, boolean canonical) {
        this.castlingPlane = castlingPlane;
        this.enPassantPlane = enPassantPlane;
        this.hectoplies = hectoplies;
        this.canonical = canonical;
    }

    /**
     * Returns whether castling is encoded as rook-location planes.
     *
     * @return true for modern rook-location castling planes
     */
    public boolean castlingPlane() {
        return castlingPlane;
    }

    /**
     * Returns whether aux plane 108 stores the en-passant target.
     *
     * @return true for en-passant target encoding
     */
    public boolean enPassantPlane() {
        return enPassantPlane;
    }

    /**
     * Returns whether the halfmove clock is scaled by 100.
     *
     * @return true for hectoply scaling
     */
    public boolean hectoplies() {
        return hectoplies;
    }

    /**
     * Returns whether LC0 canonical spatial transform is applied.
     *
     * @return true for canonical transform
     */
    public boolean canonical() {
        return canonical;
    }
}
