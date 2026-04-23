package chess.engine.search;

import java.util.Arrays;
import java.util.Locale;

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
public record Result(
    /**
     * Best root move selected by the search. The value is {@link Move#NO_MOVE}
     * when the root position is terminal or no move completed before a stop.
     */
    short bestMove,
    /**
     * Root-perspective evaluation in centipawns, or a mate sentinel score when
     * the search found a forced mate line.
     */
    int scoreCentipawns,
    /**
     * Last fully completed iterative-deepening depth. A value of zero means only
     * the deterministic static fallback was available.
     */
    int depth,
    /**
     * Number of visited search nodes accumulated across completed and partial
     * iterations.
     */
    long nodes,
    /**
     * Wall-clock runtime in milliseconds measured from the beginning of the root
     * search.
     */
    long elapsedMillis,
    /**
     * Whether the final iteration ended because a time or node budget was
     * exhausted.
     */
    boolean stopped,
    /**
     * Principal variation from the root in internal move encoding. The compact
     * constructor defensively copies this mutable array.
     */
    short[] principalVariation
) {

    /**
     * Normalizes mutable array input.
     *
     * @param bestMove best root move, or {@link Move#NO_MOVE}
     * @param scoreCentipawns root-perspective centipawn or mate score
     * @param depth last fully completed search depth
     * @param nodes visited search nodes
     * @param elapsedMillis elapsed wall-clock time in milliseconds
     * @param stopped true when a budget stopped the final iteration
     * @param principalVariation principal variation moves from the root
     */
    public Result {
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
        return Math.abs(scoreCentipawns) >= AlphaBeta.MATE_THRESHOLD;
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
        int plies = Math.max(0, AlphaBeta.MATE_SCORE - Math.abs(scoreCentipawns));
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
            return String.format(Locale.ROOT, "%+d", scoreCentipawns);
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
    Result withRuntime(long updatedNodes, long updatedElapsedMillis, boolean updatedStopped) {
        return new Result(
                bestMove,
                scoreCentipawns,
                depth,
                updatedNodes,
                updatedElapsedMillis,
                updatedStopped,
                principalVariation);
    }
}
