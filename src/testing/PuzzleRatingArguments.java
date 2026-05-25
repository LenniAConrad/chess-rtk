package testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chess.core.Move;
import chess.core.Position;

/**
 * Parsed arguments.
 */
record PuzzleRatingArguments(Path input, String prefix, Path report, List<Path> records) {
}
