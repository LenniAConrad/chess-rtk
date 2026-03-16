package application.gui;

import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.OPT_BLACK_DOWN;
import static application.cli.Constants.OPT_DARK;
import static application.cli.Constants.OPT_DARK_MODE;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_FLIP;
import static application.cli.Constants.OPT_LIGHT;

import java.util.List;

import javax.swing.SwingUtilities;

import application.gui.window.GuiWindow;
import utility.Argv;

/**
 * Implements the {@code gui} CLI command.
 *
 * Parses CLI flags for side-to-move, theme, and FEN overrides before creating the Swing window on the EDT so the rest of the application can stay responsive.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GuiCommand {

	/**
	 * Constructor.
	 */
	private GuiCommand() {
		// utility
	}

	/**
	 * Launches the Swing GUI on the EDT after parsing CLI flags.
	 *
	 * @param a parsed CLI arguments to configure fen, theme, and orientation.
	 */
	public static void runGui(Argv a) {
		boolean help = a.flag(CMD_HELP_SHORT, CMD_HELP_LONG);
		boolean whiteDown = !a.flag(OPT_FLIP, OPT_BLACK_DOWN);
		boolean darkMode = a.flag(OPT_DARK, OPT_DARK_MODE);
		boolean lightMode = a.flag(OPT_LIGHT);
		boolean light = lightMode || !darkMode;
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (help) {
			printHelp();
			return;
		}

		if (fen == null || fen.isEmpty()) {
			fen = GuiWindow.DEFAULT_FEN;
		}

		String finalFen = fen;
		SwingUtilities.invokeLater(() -> new GuiWindow(finalFen, whiteDown, light));
	}

	/**
	 * Prints CLI help for the GUI entry point.
	 */
	private static void printHelp() {
		System.out.println("""
				gui options:
				  --fen FEN              Start position (default: standard start FEN)
				  --flip|--black-down    Render Black at the bottom
				  --dark|--dark-mode     Start in dark UI theme
				  --light                Start in light UI theme
				  -h|--help              Show this help
				""");
	}
}
