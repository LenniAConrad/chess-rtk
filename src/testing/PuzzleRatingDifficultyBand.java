package testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chess.core.Move;
import chess.core.Position;

/**
 * Difficulty-band aggregate used by dynamic report copy.
 */
record PuzzleRatingDifficultyBand(String name, int count) {
}
