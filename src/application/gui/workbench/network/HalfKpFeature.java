package application.gui.workbench.network;

/**
 * Decoded HalfKP feature components.
 */
public final class HalfKpFeature {

    /**
     * King square used by the decoded feature.
     */
    public final int kingSquare;

    /**
     * Encoded piece identity used by the decoded feature.
     */
    public final int pieceCode;

    /**
     * Piece square used by the decoded feature.
     */
    public final int pieceSquare;

    /**
     * Whether the feature index decoded into a valid board feature.
     */
    public final boolean valid;

    /**
     * Creates a decoded HalfKP feature descriptor.
     *
     * @param kingSquare source king square
     * @param pieceCode encoded piece
     * @param pieceSquare source piece square
     * @param valid true when decoded successfully
     */
    public HalfKpFeature(int kingSquare, int pieceCode, int pieceSquare, boolean valid) {
        this.kingSquare = kingSquare;
        this.pieceCode = pieceCode;
        this.pieceSquare = pieceSquare;
        this.valid = valid;
    }
}
