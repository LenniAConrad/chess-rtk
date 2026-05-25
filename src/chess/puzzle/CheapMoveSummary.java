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

/** Cheap move summary after fallback defaults are applied. */
final class CheapMoveSummary {
    final int staticCp;
    final short bestMove;
    final int bestCp;
    final int solutionCp;
    final int solutionRank;

    CheapMoveSummary(int staticCp, short bestMove, int bestCp, int solutionCp, int solutionRank) {
        this.staticCp = staticCp;
        this.bestMove = bestMove;
        this.bestCp = bestCp;
        this.solutionCp = solutionCp;
        this.solutionRank = solutionRank;
    }
}
