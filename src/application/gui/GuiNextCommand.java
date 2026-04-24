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

import application.gui.studio.StudioWindow;
import chess.struct.Game;
import utility.Argv;

/**
 * Implements the experimental {@code gui-next} command.
 */
public final class GuiNextCommand {

	/**
	 * Utility constructor.
	 */
	private GuiNextCommand() {
		// utility
	}

	/**
	 * Launches GUI v3.
	 *
	 * @param a parsed arguments
	 */
	public static void runGuiNext(Argv a) {
		boolean help = a.flag(CMD_HELP_SHORT, CMD_HELP_LONG);
		boolean whiteDown = !a.flag(OPT_FLIP, OPT_BLACK_DOWN);
		boolean darkMode = a.flag(OPT_DARK, OPT_DARK_MODE);
		boolean lightMode = a.flag(OPT_LIGHT);
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

		String initialFen = (fen == null || fen.isBlank()) ? Game.STANDARD_START_FEN : fen;
		boolean startLight = lightMode || !darkMode;
		SwingUtilities.invokeLater(() -> new StudioWindow(initialFen, whiteDown, startLight));
	}

	/**
	 * Prints command usage.
	 */
	private static void printHelp() {
		System.out.println("""
				gui-next options:
				  --fen FEN              Start position (default: standard start FEN)
				  --flip|--black-down    Render Black at the bottom
				  --dark|--dark-mode     Start in dark UI theme
				  --light                Start in light UI theme
				  -h|--help              Show help
				""");
	}
}
