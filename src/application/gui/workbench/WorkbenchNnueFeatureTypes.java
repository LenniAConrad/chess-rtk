package application.gui.workbench;

/**
 * Decoded HalfKP feature components.
 */
final class HalfKpFeature {

    /**
     * King square used by the decoded feature.
     */
    final int kingSquare;

    /**
     * Encoded piece identity used by the decoded feature.
     */
    final int pieceCode;

    /**
     * Piece square used by the decoded feature.
     */
    final int pieceSquare;

    /**
     * Whether the feature index decoded into a valid board feature.
     */
    final boolean valid;

    /**
     * Creates a decoded HalfKP feature descriptor.
     *
     * @param kingSquare king square
     * @param pieceCode piece code
     * @param pieceSquare piece square
     * @param valid true when decoded successfully
     */
    HalfKpFeature(int kingSquare, int pieceCode, int pieceSquare, boolean valid) {
        this.kingSquare = kingSquare;
        this.pieceCode = pieceCode;
        this.pieceSquare = pieceSquare;
        this.valid = valid;
    }
}

/**
 * One active Half-KP feature ranked by signed centipawn impact.
 *
 * @param row active feature row
 * @param featureIndex sparse feature index
 * @param impact signed centipawn impact
 * @param rank absolute-impact rank
 * @param valid true when this driver points at a real feature
 */
record FeatureDriver(int row, int featureIndex, float impact, int rank, boolean valid) {

    /**
     * Empty feature-driver sentinel.
     *
     * @return invalid driver
     */
    static FeatureDriver invalid() {
        return new FeatureDriver(-1, -1, 0.0f, 0, false);
    }
}
