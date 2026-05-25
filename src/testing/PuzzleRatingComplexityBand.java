package testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chess.core.Move;
import chess.core.Position;

/**
 * Difficulty-band aggregate used by the driver chart.
 */
record PuzzleRatingComplexityBand(
        String name,
        String shortName,
        int count,
        double avgLegal,
        double avgRank,
        double avgPlies,
        double avgNodes,
        double branchShare) {
}
