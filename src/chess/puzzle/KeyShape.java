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

/** Key move shape summary. */
final class KeyShape {
    final boolean capture;
    final boolean promotion;
    final boolean underpromotion;
    final boolean castle;
    final boolean enPassant;
    final boolean check;
    final boolean mate;
    final boolean quiet;

    KeyShape(boolean capture, boolean promotion, boolean underpromotion, boolean castle, boolean enPassant,
            boolean check, boolean mate, boolean quiet) {
        this.capture = capture;
        this.promotion = promotion;
        this.underpromotion = underpromotion;
        this.castle = castle;
        this.enPassant = enPassant;
        this.check = check;
        this.mate = mate;
        this.quiet = quiet;
    }
}
