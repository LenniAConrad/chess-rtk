package chess.puzzle;



/**
 * Key move shape summary.
 */
final class KeyShape {
    /**
     * True when the key move captures.
     */
    final boolean capture;

    /**
     * True when the key move promotes.
     */
    final boolean promotion;

    /**
     * True when the key move promotes below queen value.
     */
    final boolean underpromotion;

    /**
     * True when the key move castles.
     */
    final boolean castle;

    /**
     * True when the key move captures en passant.
     */
    final boolean enPassant;

    /**
     * True when the key move gives check.
     */
    final boolean check;

    /**
     * True when the key move gives mate.
     */
    final boolean mate;

    /**
     * True when the key move is non-forcing.
     */
    final boolean quiet;

    /**
     * Creates a key-move shape summary.
     *
     * @param capture true when the key move captures
     * @param promotion true when the key move promotes
     * @param underpromotion true when the key move underpromotes
     * @param castle true when the key move castles
     * @param enPassant true when the key move captures en passant
     * @param check true when the key move gives check
     * @param mate true when the key move gives mate
     * @param quiet true when the key move is quiet
     */
    KeyShape(boolean capture, boolean promotion, boolean underpromotion, boolean castle, boolean enPassant,
            boolean check, boolean mate, boolean quiet) {
        this.capture = capture;
        this.promotion = promotion;
        this.underpromotion = underpromotion;
        this.castle = castle;
        this.enPassant = enPassant;
        this.check = check;
        this.mate = mate;
        this.quiet = quiet;
    }
}
