package chess.engine;

import java.util.Arrays;


/**
 * Root-search outcome.
 *
 * @param bestMove best move selected at the root
 * @param score root-perspective score for the selected move
 * @param principalVariation principal variation beginning with
 *        {@code bestMove}
 */
record RootOutcome(short bestMove, int score, short[] principalVariation) {

    /**
     * Creates an immutable root outcome.
     *
     * @param bestMove best move selected at the root
     * @param score root-perspective score for the selected move
     * @param principalVariation principal variation beginning with
     *        {@code bestMove}
     */
    RootOutcome {
        principalVariation = principalVariation == null
                ? new short[0]
                : Arrays.copyOf(principalVariation, principalVariation.length);
    }

    /**
     * Returns a defensive copy of the root principal variation.
     *
     * @return principal variation moves
     */
    @Override
    public short[] principalVariation() {
        return Arrays.copyOf(principalVariation, principalVariation.length);
    }

    /**
     * Compares this outcome with another root-search outcome.
     *
     * @param other object to compare
     * @return true when all scalar fields and principal-variation moves match
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof RootOutcome that
                && bestMove == that.bestMove
                && score == that.score
                && Arrays.equals(principalVariation, that.principalVariation);
    }

    /**
     * Computes a hash over the selected move, score, and principal variation.
     *
     * @return outcome hash code
     */
    @Override
    public int hashCode() {
        int result = Short.hashCode(bestMove);
        result = 31 * result + Integer.hashCode(score);
        result = 31 * result + Arrays.hashCode(principalVariation);
        return result;
    }

    /**
     * Formats this outcome for diagnostics.
     *
     * @return debug string containing move, score, and principal variation
     */
    @Override
    public String toString() {
        return "RootOutcome[bestMove="
                + bestMove
                + ", score="
                + score
                + ", principalVariation="
                + Arrays.toString(principalVariation)
                + "]";
    }
}
