package application.gui.workbench.mcts;

import java.util.List;
import java.util.Locale;

/**
 * Shared helpers for building {@code crtk engine search}/{@code engine tree}
 * command arguments from the workbench MCTS panels, so the panels' "Copy
 * command" affordances stay consistent with the CLI backend surface.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class MctsCliSupport {

    /**
     * Bundled LC0 BT4 weights, required by the CLI {@code --bt4} backend.
     */
    private static final String BT4_WEIGHTS = "models/bt4-1024x15x32h.bin";

    /**
     * Utility class; prevent instantiation.
     */
    private MctsCliSupport() {
        // utility
    }

    /**
     * Appends the CLI backend flags for a workbench MCTS backend.
     *
     * @param args mutable argument list
     * @param backend selected backend
     */
    static void backendArgs(List<String> args, MctsSession.Backend backend) {
        if (backend == null) {
            return;
        }
        switch (backend) {
            case CLASSICAL -> args.add("--classical");
            case NNUE -> args.add("--nnue");
            case LC0_CNN -> args.add("--lc0");
            case LC0_BT4 -> {
                args.add("--bt4");
                args.add("--weights");
                args.add(BT4_WEIGHTS);
            }
            case OTIS -> args.add("--otis");
            default -> {
                // no backend flag; CLI default applies
            }
        }
    }

    /**
     * Formats a double without a trailing {@code .0} for whole values.
     *
     * @param value value to format
     * @return compact string
     */
    static String trimDouble(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%s", value);
    }
}
