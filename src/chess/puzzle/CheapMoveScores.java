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

/** Cheap move search result before fallback defaults are applied. */
final class CheapMoveScores {
    final short bestMove;
    final int bestCp;
    final int solutionCp;

    CheapMoveScores(short bestMove, int bestCp, int solutionCp) {
        this.bestMove = bestMove;
        this.bestCp = bestCp;
        this.solutionCp = solutionCp;
    }
}
