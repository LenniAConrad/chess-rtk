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

/** Normalized scalar feature signals used for feature names. */
final class FeatureSignals {
    final Goal goal;
    final double node;
    final double length;
    final double variation;
    final double diversity;
    final double special;
    final double nonforcing;

    FeatureSignals(Goal goal, double node, double length, double variation, double diversity, double special,
            double nonforcing) {
        this.goal = goal;
        this.node = node;
        this.length = length;
        this.variation = variation;
        this.diversity = diversity;
        this.special = special;
        this.nonforcing = nonforcing;
    }
}
