package application.cli.command;

import static application.cli.Constants.CMD_TAGS;
import static application.cli.Constants.MSG_FEN_REQUIRED_HINT;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import java.nio.file.Path;
import java.util.List;

import chess.core.Position;
import chess.tag.Tagging;
import utility.Argv;
import chess.io.Reader;
import utility.Json;

/**
 * Implements the {@code tags} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class TagsCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private TagsCommand() {
		// utility
	}

	/**
	 * Handles {@code tags}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runTags(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (input != null && fen != null) {
			System.err.println(CMD_TAGS + ": provide either " + OPT_INPUT + " or a single FEN, not both");
			System.exit(2);
			return;
		}

		if (input != null) {
			try {
				List<String> fens = Reader.readFenList(input);
				for (String entry : fens) {
					printTags(entry, verbose, true);
				}
			} catch (Exception ex) {
				System.err.println(CMD_TAGS + ": failed to read input: " + ex.getMessage());
				if (verbose) {
					ex.printStackTrace(System.err);
				}
				System.exit(2);
			}
			return;
		}

		if (fen == null || fen.isEmpty()) {
			System.err.println(CMD_TAGS + " requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return;
		}
		printTags(fen, verbose, false);
	}

	/**
	 * Tags a position and prints JSON output, optionally prefixed by the FEN.
	 *
	 * @param fen        FEN string to tag
	 * @param verbose    whether to print stack traces on failure
	 * @param includeFen whether to print the FEN before the JSON tags
	 */
	private static void printTags(String fen, boolean verbose, boolean includeFen) {
		try {
			Position pos = new Position(fen.trim());
			List<String> tags = Tagging.tags(pos);
			String json = Json.stringArray(tags.toArray(new String[0]));
			if (includeFen) {
				System.out.println(fen + "\t" + json);
			} else {
				System.out.println(json);
			}
		} catch (IllegalArgumentException ex) {
			System.err.println("tags: invalid FEN skipped: " + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
		} catch (Exception ex) {
			System.err.println("tags: failed to tag position: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
		}
	}
}
