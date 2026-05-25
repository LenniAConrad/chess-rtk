package chess.engine;

import java.util.Arrays;

import chess.core.Move;
import chess.core.MoveList;

/**
 * Prepared negamax node data or an already-resolved score.
 *
 * @param resolved whether the node resolved before move search
 * @param resolvedScore resolved score when {@code resolved} is true
 * @param inCheck true when the side to move is in check
 * @param legalMoves legal moves to search
 * @param staticEval cached static evaluation, or {@link #AlphaBeta.NO_SCORE}
 * @param key position signature
 * @param entry transposition-table entry
 */
record NegamaxSetup(
        boolean resolved,
        int resolvedScore,
        boolean inCheck,
        MoveList legalMoves,
        int alpha,
        int staticEval,
        long key,
        Transposition entry) {

    /**
     * Creates a resolved setup.
     *
     * @param score resolved score
     * @return resolved setup
     */
static NegamaxSetup resolved(int score) {
        return new NegamaxSetup(true, score, false, null, AlphaBeta.NO_SCORE, AlphaBeta.NO_SCORE, 0L, null);
    }

    /**
     * Creates a prepared setup for normal move search.
     *
     * @param inCheck true when the side to move is in check
     * @param legalMoves legal moves to search
     * @param staticEval cached static evaluation, or {@link #AlphaBeta.NO_SCORE}
     * @param key position signature
     * @param entry transposition-table entry
     * @return search setup
     * @param alpha alpha search bound
     */
static NegamaxSetup search(
            boolean inCheck,
            MoveList legalMoves,
            int alpha,
            int staticEval,
            long key,
            Transposition entry) {
        return new NegamaxSetup(false, AlphaBeta.NO_SCORE, inCheck, legalMoves, alpha, staticEval, key, entry);
    }
}
