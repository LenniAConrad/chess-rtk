package testing;

import java.nio.file.Path;
import java.util.List;


/**
 * Parsed arguments.
 */
record PuzzleRatingArguments(Path input, String prefix, Path report, List<Path> records) {
}
