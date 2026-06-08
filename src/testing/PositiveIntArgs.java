package testing;

/**
 * Parses positive integer command-line arguments for testing and preview
 * utilities.
 */
final class PositiveIntArgs {

    /**
     * Prevents instantiation.
     */
    private PositiveIntArgs() {
        // utility
    }

    /**
     * Parses one positive integer argument with a fallback for missing or blank
     * values.
     *
     * @param args command-line arguments
     * @param index argument index
     * @param fallback fallback value
     * @return parsed positive value or fallback
     */
    static int parse(String[] args, int index, int fallback) {
        if (args.length <= index || args[index].isBlank()) {
            return fallback;
        }
        int value = Integer.parseInt(args[index]);
        if (value <= 0) {
            throw new IllegalArgumentException("Expected a positive integer at argument " + index + ": " + value);
        }
        return value;
    }
}
