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
 * Root material summary used by tree-evidence heuristics.
 *
 * @param nonKingPieceCount number of non-king pieces on the board
 * @param nonPawnPieceCount number of non-pawn, non-king pieces on the board
 * @param nonKingMaterialCp material value of all non-king pieces
 */
record RootMaterial(int nonKingPieceCount, int nonPawnPieceCount, int nonKingMaterialCp) {
    /**
     * Normalizes material counters.
     */
    RootMaterial {
        nonKingPieceCount = Math.max(0, nonKingPieceCount);
        nonPawnPieceCount = Math.max(0, nonPawnPieceCount);
        nonKingMaterialCp = Math.max(0, nonKingMaterialCp);
    }
}
