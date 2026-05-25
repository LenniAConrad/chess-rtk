package chess.engine;

import java.util.Arrays;

import chess.core.Move;
import chess.core.MoveList;

/**
 * Prepared quiescence state or an already-resolved score.
 *
 * @param resolved whether quiescence resolved before move search
 * @param resolvedScore resolved score when {@code resolved} is true
 * @param legalMoves legal moves to search
 * @param inCheck true when the side to move is in check
 * @param standPat stand-pat score, or {@link #AlphaBeta.NO_SCORE}
 * @param alpha current alpha bound
 */
record QuiescenceSetup(
        boolean resolved,
        int resolvedScore,
        MoveList legalMoves,
        boolean inCheck,
        int standPat,
        int alpha) {

    /**
     * Creates a resolved quiescence setup.
     *
     * @param score resolved score
     * @return resolved setup
     */
static QuiescenceSetup resolved(int score) {
        return new QuiescenceSetup(true, score, null, false, AlphaBeta.NO_SCORE, AlphaBeta.NO_SCORE);
    }

    /**
     * Creates a prepared quiescence setup.
     *
     * @param legalMoves legal moves to search
     * @param inCheck true when the side to move is in check
     * @param standPat stand-pat score, or {@link #AlphaBeta.NO_SCORE}
     * @param alpha current alpha bound
     * @return search setup
     */
static QuiescenceSetup search(MoveList legalMoves, boolean inCheck, int standPat, int alpha) {
        return new QuiescenceSetup(false, AlphaBeta.NO_SCORE, legalMoves, inCheck, standPat, alpha);
    }
}
