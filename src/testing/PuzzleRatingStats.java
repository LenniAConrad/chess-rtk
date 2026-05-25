package testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chess.core.Move;
import chess.core.Position;

/**
 * Report-wide statistics.
 */
record PuzzleRatingStats(
        double mean,
        double sd,
        double skewness,
        double kurtosis,
        int win,
        int draw,
        double rawMin,
        double rawMax,
        int explicitNodes,
        int branchRows,
        double avgNodes,
        double avgReplies,
        double avgBranches,
        int maxNodes,
        int maxPlies,
        int maxReplies,
        int maxBranches) {
}
