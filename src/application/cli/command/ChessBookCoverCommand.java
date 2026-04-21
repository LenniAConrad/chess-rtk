package application.cli.command;

import static application.cli.Constants.CMD_CHESS_BOOK_COVER;
import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.OPT_BINDING;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_INTERIOR;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PAGES;
import static application.cli.Constants.OPT_SUBTITLE;
import static application.cli.Constants.OPT_TITLE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.PathOps.deriveOutputPath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import chess.book.model.Book;
import chess.book.cover.Binding;
import chess.book.cover.Dimensions;
import chess.book.cover.Interior;
import chess.book.cover.Options;
import chess.book.cover.Writer;
import utility.Argv;

/**
 * Implements {@code chess-book-cover}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ChessBookCoverCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessBookCoverCommand() {
		// utility
	}

	/**
	 * Handles {@code chess-book-cover}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runChessBookCover(Argv a) {
		if (a.flag(CMD_HELP_SHORT, CMD_HELP_LONG)) {
			HelpCommand.runHelp(new Argv(new String[] { CMD_CHESS_BOOK_COVER }));
			return;
		}

		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		String titleOverride = trimToNull(a.string(OPT_TITLE));
		String subtitleOverride = trimToNull(a.string(OPT_SUBTITLE));
		Binding binding = Binding.parse(a.string(OPT_BINDING));
		Interior interior = Interior.parse(a.string(OPT_INTERIOR));
		int pages = a.integerOr(0, OPT_PAGES);
		List<String> rest = a.positionals();
		a.ensureConsumed();

		if (pages < 0) {
			failUsage("--pages must be zero or greater");
		}
		if (input == null && rest.size() == 1) {
			input = Path.of(rest.get(0));
		} else if (input != null && !rest.isEmpty()) {
			failUsage("provide the input book either as --input or as one positional path");
		} else if (input == null) {
			failUsage("missing input book JSON/TOML path");
		}

		try {
			Book book = Book.load(input);
			if (titleOverride != null) {
				book.setTitle(titleOverride);
			}
			if (subtitleOverride != null) {
				book.setSubtitle(subtitleOverride);
			}
			Options options = new Options()
					.setBinding(binding)
					.setInterior(interior)
					.setPages(pages);
			Dimensions dimensions = Writer.calculateDimensions(book, options);
			Path resolvedOutput = output != null ? output : deriveOutputPath(input, "-cover.pdf");
			Writer.write(resolvedOutput, book, options);
			System.out.printf(Locale.ROOT,
					"%s wrote %s cover for %d pages (spine %.2f cm, %.2f x %.2f cm) to %s%n",
					CMD_CHESS_BOOK_COVER,
					dimensions.binding().token(),
					dimensions.pages(),
					dimensions.spineWidthCm(),
					dimensions.fullWidthCm(),
					dimensions.fullHeightCm(),
					resolvedOutput.toAbsolutePath());
		} catch (IllegalArgumentException ex) {
			System.err.println(CMD_CHESS_BOOK_COVER + ": " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		} catch (IOException ex) {
			System.err.println(CMD_CHESS_BOOK_COVER + ": failed to generate cover PDF: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception ex) {
			System.err.println(CMD_CHESS_BOOK_COVER + ": unexpected failure: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Throws a usage error for invalid command-line combinations.
	 *
	 * @param message explanation of the invalid input
	 */
	private static void failUsage(String message) {
		throw new IllegalArgumentException(message);
	}

	/**
	 * Normalizes optional strings by trimming and converting blank values to null.
	 *
	 * @param value raw option value
	 * @return trimmed value or {@code null}
	 */
	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
