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
 * Normalized scalar feature signals used for feature names.
 */
final class FeatureSignals {
    /**
     * Puzzle goal class.
     */
    final Goal goal;

    /**
     * Node-score signal.
     */
    final double node;

    /**
     * Solution-length signal.
     */
    final double length;

    /**
     * Variation-count signal.
     */
    final double variation;

    /**
     * Move-diversity signal.
     */
    final double diversity;

    /**
     * Special-move signal.
     */
    final double special;

    /**
     * Non-forcing-move signal.
     */
    final double nonforcing;

    /**
     * Creates normalized feature signals.
     *
     * @param goal puzzle goal class
     * @param node node-score signal
     * @param length solution-length signal
     * @param variation variation-count signal
     * @param diversity move-diversity signal
     * @param special special-move signal
     * @param nonforcing non-forcing-move signal
     */
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
