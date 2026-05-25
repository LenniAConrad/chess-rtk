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

/** Cheap-evaluator visibility scores used for feature names. */
final class CheapVisibility {
    final int staticCp;
    final int solutionCp;

    CheapVisibility(int staticCp, int solutionCp) {
        this.staticCp = staticCp;
        this.solutionCp = solutionCp;
    }
}
