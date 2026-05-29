package chess.puzzle;

import java.util.ArrayList;
import java.util.List;

import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.eval.CentipawnEvaluator;
import chess.puzzle.Scorer.NodeScore;
import chess.puzzle.Scorer.PuzzleTreeSummary;

/**
 * Cheap move summary after fallback defaults are applied.
 */
final class CheapMoveSummary {
    /**
     * Static evaluation before trying candidate moves.
     */
    final int staticCp;

    /**
     * Best cheap-evaluator move.
     */
    final short bestMove;

    /**
     * Best cheap-evaluator move score.
     */
    final int bestCp;

    /**
     * Intended solution move score.
     */
    final int solutionCp;

    /**
     * One-based solution rank among cheap-evaluator moves.
     */
    final int solutionRank;

    /**
     * Creates a cheap move summary.
     *
     * @param staticCp static evaluation before candidate moves
     * @param bestMove best cheap-evaluator move
     * @param bestCp best move score
     * @param solutionCp solution move score
     * @param solutionRank one-based solution rank
     */
    CheapMoveSummary(int staticCp, short bestMove, int bestCp, int solutionCp, int solutionRank) {
        this.staticCp = staticCp;
        this.bestMove = bestMove;
        this.bestCp = bestCp;
        this.solutionCp = solutionCp;
        this.solutionRank = solutionRank;
    }
}
