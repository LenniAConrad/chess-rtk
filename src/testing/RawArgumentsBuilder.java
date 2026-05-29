package testing;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Mutable parser state.
 */
final class RawArgumentsBuilder {

    /**
     * Source record files.
     */
    private final List<Path> inputs = new ArrayList<>();

    /**
     * Output prefix, or null for default.
     */
    private Path prefix;

    /**
     * Maximum verified puzzle count.
     */
    private int maxPuzzles;

    /**
     * Accepts one command-line token.
     * @param arg arg value
     * @param tokens token values
     * @return accept result
     */
    boolean accept(String arg, Iterator<String> tokens) {
        if ("--out".equals(arg)) {
            return acceptOutputPrefix(tokens);
        }
        if ("--max-puzzles".equals(arg)) {
            return acceptMaxPuzzles(tokens);
        }
        if (arg.startsWith("-")) {
            return false;
        }
        inputs.add(Path.of(arg));
        return true;
    }

    /**
     * Builds immutable raw arguments.
     * @return to raw arguments result
     */
    RawArguments toRawArguments() {
        return new RawArguments(List.copyOf(inputs), prefix, maxPuzzles);
    }

    /**
     * Accept output prefix.
     * @param tokens token values
     * @return accept output prefix result
     */
    private boolean acceptOutputPrefix(Iterator<String> tokens) {
        if (prefix != null || !tokens.hasNext()) {
            return false;
        }
        prefix = Path.of(tokens.next());
        return true;
    }

    /**
     * Accept max puzzles.
     * @param tokens token values
     * @return accept max puzzles result
     */
    private boolean acceptMaxPuzzles(Iterator<String> tokens) {
        if (maxPuzzles != 0 || !tokens.hasNext()) {
            return false;
        }
        maxPuzzles = parseMaxPuzzles(tokens.next());
        return maxPuzzles != 0;
    }

    /**
     * Parse max puzzles.
     * @param value value to use
     * @return parse max puzzles result
     */
    private static int parseMaxPuzzles(String value) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
