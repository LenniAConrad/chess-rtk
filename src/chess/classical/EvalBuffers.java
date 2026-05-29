package chess.classical;



/**
 * Internal scratch space used for file-based pawn/rook analysis.
 *
 * <p>
 * Instances are thread-confined via {@link #BUFFERS}. Call {@link #reset()}
 * before use.
 * </p>
 */
final class EvalBuffers {

    /**
     * Number of White pawns found on each file during this scan.
     * {@link #reset()} zeroes all entries before the next evaluation.
     */
    final int[] whitePawnsPerFile = new int[8];

    /**
     * Number of Black pawns found on each file during this scan.
     * {@link #reset()} zeroes all entries before the next evaluation.
     */
    final int[] blackPawnsPerFile = new int[8];

    /**
     * Lowest rank index (0..7) on each file containing a White pawn.
     * Starts at 8 and is clamped downwards as pawns are discovered.
     */
    final int[] minWhitePawnRank = new int[8];

    /**
     * Highest rank index (0..7) on each file containing a Black pawn.
     * Starts at -1 and moves upward as pawns are discovered.
     */
    final int[] maxBlackPawnRank = new int[8];

    /**
     * Lowest rank index (0..7) on each file containing a Black pawn.
     * Starts at 8 and is clamped downwards as pawns are discovered.
     */
    final int[] minBlackPawnRank = new int[8];

    /**
     * Highest rank index (0..7) on each file containing a White pawn.
     * Starts at -1 and moves upward as pawns are discovered.
     */
    final int[] maxWhitePawnRank = new int[8];

    /**
     * Count of White rooks on each file observed during the current scan.
     * Values are reset to zero when {@link #reset()} is called.
     */
    final int[] whiteRooksFileCount = new int[8];

    /**
     * Count of Black rooks on each file observed during the current scan.
     * Values are reset to zero when {@link #reset()} is called.
     */
    final int[] blackRooksFileCount = new int[8];

    /**
     * Transient scan state that accumulates material totals and PST scores.
     * This object is reused across evaluations to avoid allocations.
     */
    final EvalScan scan = new EvalScan();

    /**
     * Transient attack-map state reused across evaluations.
     */
    final AttackInfo attacks = new AttackInfo();

    /**
     * Estimated game phase between 0.0 (endgame) and 1.0 (opening/middlegame).
     * Reset to 1.0 before each evaluation and dampened as material is collected.
     */
    double phase = 1.0;

    /**
     * Reset all arrays to their sentinel values for a fresh evaluation pass.
     */
    void reset() {
        for (int i = 0; i < 8; i++) {
            whitePawnsPerFile[i] = 0;
            blackPawnsPerFile[i] = 0;
            minWhitePawnRank[i] = 8;
            maxBlackPawnRank[i] = -1;
            minBlackPawnRank[i] = 8;
            maxWhitePawnRank[i] = -1;
            whiteRooksFileCount[i] = 0;
            blackRooksFileCount[i] = 0;
        }
        phase = 1.0;
    }
}
