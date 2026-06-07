package testing;

import application.Main;

/**
 * Standalone entry point for the deterministic self-play A/B gauntlet.
 *
 * <p>
 * Thin wrapper kept for backward compatibility with
 * {@code java -cp out testing.SelfPlayGauntlet [flags]}. The gauntlet itself is
 * now the first-class CLI command {@code crtk engine gauntlet}, backed by the
 * reusable {@link chess.engine.Gauntlet} runner; this shim simply forwards its
 * flags to that command through {@link Main}, so it inherits identical flags
 * (for example {@code --a}, {@code --b}, {@code --nodes}, {@code --searchA},
 * {@code --evalB}, {@code --openings}, {@code --seed}, {@code --workers}),
 * diagnostics, and exit codes.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SelfPlayGauntlet {

    /**
     * Prevents instantiation.
     */
    private SelfPlayGauntlet() {
        // utility
    }

    /**
     * Runs the gauntlet from raw command-line flags.
     *
     * @param args gauntlet flags (see {@code crtk engine gauntlet --help})
     */
    public static void main(String[] args) {
        String[] full = new String[args.length + 2];
        full[0] = "engine";
        full[1] = "gauntlet";
        System.arraycopy(args, 0, full, 2, args.length);
        int exit = Main.run(full);
        if (exit != 0) {
            System.exit(exit);
        }
    }
}
