package application.cli.command;

import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.OPT_CHECK;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_FREE_WATERMARK;
import static application.cli.Constants.OPT_LIMIT;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_SUBTITLE;
import static application.cli.Constants.OPT_TITLE;
import static application.cli.Constants.OPT_VALIDATE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WATERMARK;
import static application.cli.Constants.OPT_WATERMARK_ID;
import static application.cli.PathOps.deriveOutputPath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import chess.book.model.Book;
import chess.book.model.Element;
import chess.book.render.Writer;
import utility.Argv;

/**
 * Implements {@code book render}.
 *
 * <p>
 * The command reads a JSON or TOML book manifest and renders it directly through
 * the native dependency-free PDF engine.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ChessBookCommand {

	/**
	 * Current command label used in diagnostics.
	 */
	private static final String COMMAND_LABEL = "book render";

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessBookCommand() {
		// utility
	}

	/**
	 * Handles {@code book render}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runChessBook(Argv a) {
		if (a.flag(CMD_HELP_SHORT, CMD_HELP_LONG)) {
			HelpCommand.runHelp(new Argv(new String[] { "book", "render" }));
			return;
		}

		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		String titleOverride = trimToNull(a.string(OPT_TITLE));
		String subtitleOverride = trimToNull(a.string(OPT_SUBTITLE));
		int limit = a.integerOr(0, OPT_LIMIT);
		boolean check = a.flag(OPT_CHECK, OPT_VALIDATE);
		String watermarkId = trimToNull(a.string(OPT_WATERMARK_ID));
		boolean freeWatermark = a.flag(OPT_FREE_WATERMARK, OPT_WATERMARK) || watermarkId != null;
		List<String> rest = a.positionals();
		a.ensureConsumed();

		if (limit < 0) {
			failUsage("--limit must be zero or greater");
		}

		if (input == null && rest.size() == 1) {
			input = Path.of(rest.get(0));
		} else if (input != null && !rest.isEmpty()) {
			failUsage("provide the input book either as --input or as one positional path");
		} else if (input == null) {
			failUsage("missing input book JSON path");
		}

		try {
			Book book = Book.load(input);
			if (limit > 0) {
				applyPuzzleLimit(book, limit);
			}
			if (titleOverride != null) {
				book.setTitle(titleOverride);
			}
			if (subtitleOverride != null) {
				book.setSubtitle(subtitleOverride);
			}
			if (check) {
				printValidationSummary(book);
				return;
			}

			Path resolvedOutput = output != null ? output : deriveOutputPath(input, ".pdf");
			Writer.write(resolvedOutput, book, freeWatermark, watermarkId);
			System.out.printf(Locale.ROOT, "%s wrote %d puzzle%s to %s%s%n",
					COMMAND_LABEL,
					book.getElements().length,
					book.getElements().length == 1 ? "" : "s",
					resolvedOutput.toAbsolutePath(),
					watermarkSuffix(freeWatermark, watermarkId));
		} catch (IllegalArgumentException ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, ex.getMessage(), ex, 2, verbose);
		} catch (IOException ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, "failed to generate PDF: " + ex.getMessage(), ex, 3, verbose);
		} catch (Exception ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, "unexpected failure: " + ex.getMessage(), ex, 3, verbose);
		}
	}

	/**
	 * Prints validation details without writing a PDF.
	 *
	 * @param book loaded and option-adjusted book.
	 */
	private static void printValidationSummary(Book book) {
		ChessBookValidation.Summary summary = ChessBookValidation.validateBook(book);
		System.out.printf(Locale.ROOT,
				"%s OK: %d puzzle%s, %.2f x %.2f cm, margins %.2f/%.2f/%.2f/%.2f cm, grid %dx%d, table frequency %d%n",
				COMMAND_LABEL,
				summary.puzzles(),
				summary.puzzles() == 1 ? "" : "s",
				summary.paperWidthCm(),
				summary.paperHeightCm(),
				summary.innerMarginCm(),
				summary.outerMarginCm(),
				summary.topMarginCm(),
				summary.bottomMarginCm(),
				summary.puzzleRows(),
				summary.puzzleColumns(),
				summary.tableFrequency());
	}

	/**
	 * Builds the console suffix for watermarked renders.
	 *
	 * @param freeWatermark whether a watermarked PDF was generated
	 * @param watermarkId optional explicit watermark identifier
	 * @return status suffix
	 */
	private static String watermarkSuffix(boolean freeWatermark, String watermarkId) {
		if (!freeWatermark) {
			return "";
		}
		return watermarkId == null ? " (free watermarked PDF)"
				: " (free watermarked PDF, watermark ID embedded)";
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
	 * Limits the puzzle list and updates obvious source-count references.
	 *
	 * @param book  loaded book model
	 * @param limit maximum number of puzzles to render
	 */
	private static void applyPuzzleLimit(Book book, int limit) {
		Element[] elements = book.getElements();
		int originalCount = elements.length;
		if (elements.length > limit) {
			book.setElements(Arrays.copyOf(elements, limit));
		}
		if (originalCount > limit) {
			book.setSubtitle(replaceCount(book.getSubtitle(), originalCount, limit));
			book.setIntroduction(replaceCount(book.getIntroduction(), originalCount, limit));
			book.setHowToRead(replaceCount(book.getHowToRead(), originalCount, limit));
			book.setAfterword(replaceCount(book.getAfterword(), originalCount, limit));
		}
	}

	/**
	 * Replaces common decimal and grouped count spellings in a text array.
	 *
	 * @param values        source values
	 * @param originalCount original puzzle count
	 * @param limit         limited puzzle count
	 * @return updated values
	 */
	private static String[] replaceCount(String[] values, int originalCount, int limit) {
		String[] copy = values.clone();
		for (int i = 0; i < copy.length; i++) {
			copy[i] = replaceCount(copy[i], originalCount, limit);
		}
		return copy;
	}

	/**
	 * Replaces common decimal and grouped count spellings in one string.
	 *
	 * @param value         source value
	 * @param originalCount original puzzle count
	 * @param limit         limited puzzle count
	 * @return updated value
	 */
	private static String replaceCount(String value, int originalCount, int limit) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		String replacement = Integer.toString(limit);
		return value
				.replace(String.format(Locale.ROOT, "%,d", originalCount), replacement)
				.replace(Integer.toString(originalCount), replacement);
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
