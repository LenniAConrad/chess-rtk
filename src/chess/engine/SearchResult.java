package chess.engine;

import java.util.Arrays;

import chess.core.Move;

/**
 * Search output from the built-in Java engine.
 *
 * <p>
 * Scores are centipawns from the root side-to-move perspective. Mate scores use
 * the same sign convention: positive means the root side is mating, negative
 * means it is getting mated.
 * </p>
 *
 * @param bestMove best root move, or {@link Move#NO_MOVE}
 * @param scoreCentipawns root score in centipawns or a mate sentinel score
 * @param depth completed search depth
 * @param nodes visited nodes
 * @param elapsedMillis elapsed wall-clock time
 * @param stopped true when a node or time budget interrupted the final iteration
 * @param principalVariation principal variation moves from the root
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public record SearchResult(
    /**
     * Stores the best move.
     */
    short bestMove,
    /**
     * Stores the score centipawns.
     */
    int scoreCentipawns,
    /**
     * Stores the depth.
     */
    int depth,
    /**
     * Stores the nodes.
     */
    long nodes,
    /**
     * Stores the elapsed millis.
     */
    long elapsedMillis,
    /**
     * Stores the stopped.
     */
    boolean stopped,
    /**
     * Stores the principal variation.
     */
    short[] principalVariation
) {

    /**
     * Normalizes mutable array input.
     */
    public SearchResult {
        principalVariation = principalVariation == null
                ? new short[0]
                : Arrays.copyOf(principalVariation, principalVariation.length);
    }

    /**
     * Defensive copy accessor for the principal variation.
     *
     * @return principal variation moves
     */
    @Override
    public short[] principalVariation() {
        return Arrays.copyOf(principalVariation, principalVariation.length);
    }

    /**
     * Returns whether a concrete root move was found.
     *
     * @return true when {@link #bestMove()} is a move
     */
    public boolean hasBestMove() {
        return bestMove != Move.NO_MOVE;
    }

    /**
     * Returns whether this result encodes a forced mate score.
     *
     * @return true for mate scores
     */
    public boolean isMateScore() {
        return Math.abs(scoreCentipawns) >= Searcher.MATE_THRESHOLD;
    }

    /**
     * Returns the mate distance in moves, if the score is a mate score.
     *
     * <p>
     * Positive values mean the root side mates; negative values mean the root
     * side is mated. A value of zero means the score is not a mate score, except
     * for already-terminal positions where the side to move has no legal move.
     * </p>
     *
     * @return signed mate distance in moves, or zero
     */
    public int mateIn() {
        if (!isMateScore()) {
            return 0;
        }
        int plies = Math.max(0, Searcher.MATE_SCORE - Math.abs(scoreCentipawns));
        int moves = (plies + 1) / 2;
        return scoreCentipawns > 0 ? moves : -moves;
    }

    /**
     * Formats the score as either a signed centipawn value or mate label.
     *
     * @return score label
     */
    public String scoreLabel() {
        if (!isMateScore()) {
            return String.format("%+d", scoreCentipawns);
        }
        int mate = mateIn();
        if (mate == 0 && scoreCentipawns < 0) {
            return "#-0";
        }
        return "#" + mate;
    }

    /**
     * Returns a copy with updated runtime counters and stopped state.
     *
     * @param updatedNodes updated visited-node count
     * @param updatedElapsedMillis updated elapsed time
     * @param updatedStopped updated stopped flag
     * @return adjusted result
     */
    SearchResult withRuntime(long updatedNodes, long updatedElapsedMillis, boolean updatedStopped) {
        return new SearchResult(
                bestMove,
                scoreCentipawns,
                depth,
                updatedNodes,
                updatedElapsedMillis,
                updatedStopped,
                principalVariation);
    }
}
