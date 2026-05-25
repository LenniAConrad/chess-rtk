package testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chess.core.Move;
import chess.core.Position;

/**
 * Integer row field accessor.
 */
interface PuzzleRatingIntField {
    /**
     * Returns the integer value for a CSV row.
     *
     * @param row source row
     * @return integer value
     */
    int value(PuzzleRatingRow row);
}
