package testing;


import java.nio.file.Path;
import java.util.List;


/**
 * Parsed flag-based arguments before defaults.
 */
record RawArguments(List<Path> inputs, Path prefix, int maxPuzzles) {
}
