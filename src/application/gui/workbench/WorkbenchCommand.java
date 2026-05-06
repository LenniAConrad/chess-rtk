package application.gui.workbench;

import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.OPT_BLACK_DOWN;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_FLIP;

import java.util.List;

import javax.swing.SwingUtilities;

import chess.core.Setup;
import utility.Argv;

/**
 * Launches the native CRTK Workbench GUI.
 */
public final class WorkbenchCommand {

    /**
     * Prevents instantiation.
     */
    private WorkbenchCommand() {
        // utility
    }

    /**
     * Runs the workbench command.
     *
     * @param argv parsed CLI arguments
     */
    public static void runWorkbench(Argv argv) {
        boolean help = argv.flag(CMD_HELP_SHORT, CMD_HELP_LONG);
        boolean whiteDown = !argv.flag(OPT_FLIP, OPT_BLACK_DOWN);
        String fen = argv.string(OPT_FEN);
        List<String> rest = argv.positionals();
        if (fen == null && !rest.isEmpty()) {
            fen = String.join(" ", rest);
        }
        argv.ensureConsumed();

        if (help) {
            printHelp();
            return;
        }

        String initialFen = fen == null || fen.isBlank() ? Setup.getStandardStartFEN() : fen;
        SwingUtilities.invokeLater(() -> new WorkbenchWindow(initialFen, whiteDown));
    }

    /**
     * Prints command help.
     */
    private static void printHelp() {
        System.out.println("""
                gui-workbench options:
                  --fen FEN              Start position (default: standard start FEN)
                  --flip|--black-down    Render Black at the bottom
                  -h|--help              Show this help
                """);
    }
}
