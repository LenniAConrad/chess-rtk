package testing;


import java.nio.file.Path;
import java.util.List;


/**
 * Shared parsed command-line arguments for puzzle report tools.
 *
 * @param inputs input record files or directories
 * @param prefix output path prefix
 * @param maxPuzzles maximum puzzle rows to process
 */
record Arguments(List<Path> inputs, Path prefix, int maxPuzzles) {
}
