package testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chess.core.Move;
import chess.core.Position;

/**
 * Floating-point accessor for difficulty-driver metrics.
 */
interface PuzzleRatingDoubleMetric {
    /**
     * Returns the floating-point metric value for one complexity band.
     *
     * @param band source band
     * @return metric value
     */
    double value(PuzzleRatingComplexityBand band);
}
