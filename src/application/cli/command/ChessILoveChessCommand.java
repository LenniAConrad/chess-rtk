package application.cli.command;

import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.OPT_BINDING;
import static application.cli.Constants.OPT_CHECK;
import static application.cli.Constants.OPT_FREE_WATERMARK;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_INTERIOR;
import static application.cli.Constants.OPT_LIMIT;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PAGES;
import static application.cli.Constants.OPT_SUBTITLE;
import static application.cli.Constants.OPT_TITLE;
import static application.cli.Constants.OPT_VALIDATE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WATERMARK;
import static application.cli.Constants.OPT_WATERMARK_ID;
import static application.cli.PathOps.deriveOutputPath;
import static application.cli.PathOps.ensureParentDir;
import static application.cli.command.CommandSupport.failUsage;
import static application.cli.command.CommandSupport.resolveSingleInputPath;
import static application.cli.command.CommandSupport.trimToNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import application.cli.RecordIO;
import chess.book.cover.Binding;
import chess.book.cover.Interior;
import chess.book.model.Book;
import chess.book.model.Language;
import chess.book.model.TomlWriter;
import chess.book.series.ilovechess.BuildOptions;
import chess.book.series.ilovechess.BuildResult;
import chess.book.series.ilovechess.Builder;
import chess.struct.Record;
import utility.Argv;

/**
 * Implements {@code book ilovechess}.
 *
 * <p>
 * The command builds an I Love Chess-style book manifest from analyzed record
 * dumps. It can also render the interior PDF and matching cover by delegating
 * to the native book and cover writers already present in the repo.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ChessILoveChessCommand {

	/**
	 * Current command label used in diagnostics.
	 */
	private static final String COMMAND_LABEL = "book ilovechess";

	/**
	 * Author option flag.
	 */
	private static final String OPT_AUTHOR = "--author";

	/**
	 * Time option flag.
	 */
	private static final String OPT_TIME = "--time";

	/**
	 * Location option flag.
	 */
	private static final String OPT_LOCATION = "--location";

	/**
	 * Language option flag.
	 */
	private static final String OPT_LANGUAGE = "--language";

	/**
	 * Repeated imprint-line option flag.
	 */
	private static final String OPT_IMPRINT = "--imprint";

	/**
	 * Repeated dedication-line option flag.
	 */
	private static final String OPT_DEDICATION = "--dedication";

	/**
	 * Repeated introduction-paragraph option flag.
	 */
	private static final String OPT_INTRODUCTION = "--introduction";

	/**
	 * Repeated how-to-read paragraph option flag.
	 */
	private static final String OPT_HOW_TO_READ = "--how-to-read";

	/**
	 * Repeated blurb-paragraph option flag.
	 */
	private static final String OPT_BLURB = "--blurb";

	/**
	 * Repeated purchase-link option flag.
	 */
	private static final String OPT_LINK = "--link";

	/**
	 * Repeated afterword-paragraph option flag.
	 */
	private static final String OPT_AFTERWORD = "--afterword";

	/**
	 * Table-frequency override option flag.
	 */
	private static final String OPT_TABLE_FREQUENCY = "--table-frequency";

	/**
	 * Puzzle-row override option flag.
	 */
	private static final String OPT_PUZZLE_ROWS = "--puzzle-rows";

	/**
	 * Puzzle-column override option flag.
	 */
	private static final String OPT_PUZZLE_COLUMNS = "--puzzle-columns";

	/**
	 * Optional interior-PDF output path.
	 */
	private static final String OPT_PDF_OUTPUT = "--pdf-output";

	/**
	 * Optional cover-PDF output path.
	 */
	private static final String OPT_COVER_OUTPUT = "--cover-output";

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessILoveChessCommand() {
		// utility
	}

	/**
	 * Handles {@code book ilovechess}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runILoveChess(Argv a) {
		if (a.flag(CMD_HELP_SHORT, CMD_HELP_LONG)) {
			HelpCommand.runHelp(new Argv(new String[] { "book", "ilovechess" }));
			return;
		}

		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		Path pdfOutput = a.path(OPT_PDF_OUTPUT);
		Path coverOutput = a.path(OPT_COVER_OUTPUT);
		String title = trimToNull(a.string(OPT_TITLE));
		String subtitle = trimToNull(a.string(OPT_SUBTITLE));
		String author = trimToNull(a.string(OPT_AUTHOR));
		String time = trimToNull(a.string(OPT_TIME));
		String location = trimToNull(a.string(OPT_LOCATION));
		String language = trimToNull(a.string(OPT_LANGUAGE));
		int limit = a.integerOr(0, OPT_LIMIT);
		int pages = a.integerOr(0, OPT_PAGES);
		int tableFrequency = a.integerOr(6, OPT_TABLE_FREQUENCY);
		int puzzleRows = a.integerOr(5, OPT_PUZZLE_ROWS);
		int puzzleColumns = a.integerOr(4, OPT_PUZZLE_COLUMNS);
		Binding binding = Binding.parse(a.string(OPT_BINDING));
		Interior interior = Interior.parse(a.string(OPT_INTERIOR));
		boolean check = a.flag(OPT_CHECK, OPT_VALIDATE);
		String watermarkId = trimToNull(a.string(OPT_WATERMARK_ID));
		boolean freeWatermark = a.flag(OPT_FREE_WATERMARK, OPT_WATERMARK) || watermarkId != null;
		List<String> imprint = a.strings(OPT_IMPRINT);
		List<String> dedication = a.strings(OPT_DEDICATION);
		List<String> introduction = a.strings(OPT_INTRODUCTION);
		List<String> howToRead = a.strings(OPT_HOW_TO_READ);
		List<String> blurb = a.strings(OPT_BLURB);
		List<String> link = a.strings(OPT_LINK);
		List<String> afterword = a.strings(OPT_AFTERWORD);
		List<String> rest = a.positionals();
		a.ensureConsumed();

		if (limit < 0) {
			failUsage("--limit must be zero or greater");
		}
		if (pages < 0) {
			failUsage("--pages must be zero or greater");
		}
		if (tableFrequency <= 0) {
			failUsage("--table-frequency must be greater than zero");
		}
		if (puzzleRows <= 0) {
			failUsage("--puzzle-rows must be greater than zero");
		}
		if (puzzleColumns <= 0) {
			failUsage("--puzzle-columns must be greater than zero");
		}
		input = resolveSingleInputPath(input, rest,
				"provide the input record file either as --input or as one positional path",
				"missing input record JSON/JSONL path");

		try {
			List<Record> records = loadRecords(input, limit, verbose);
			BuildOptions options = new BuildOptions()
					.setTitle(title == null ? "I Love Chess" : title)
					.setSubtitle(subtitle)
					.setAuthor(author == null ? "Lennart A. Conrad" : author)
					.setTime(time)
					.setLocation(location)
					.setLanguage(Language.parse(language))
					.setPages(pages)
					.setTableFrequency(tableFrequency)
					.setPuzzleRows(puzzleRows)
					.setPuzzleColumns(puzzleColumns)
					.setImprint(imprint.toArray(new String[0]))
					.setDedication(dedication.toArray(new String[0]))
					.setIntroduction(introduction.toArray(new String[0]))
					.setHowToRead(howToRead.toArray(new String[0]))
					.setBlurb(blurb.toArray(new String[0]))
					.setLink(link.toArray(new String[0]))
					.setAfterword(afterword.toArray(new String[0]));
			BuildResult result = Builder.build(records, options);
			Book book = result.getBook();
			if (check) {
				printValidationSummary(result, book);
				return;
			}

			Path manifestOutput = output != null ? output : deriveOutputPath(input, ".book.toml");
			writeManifest(manifestOutput, book);
			System.out.printf(Locale.ROOT,
					"%s wrote manifest with %d puzzle%s to %s%s%n",
					COMMAND_LABEL,
					result.getAccepted(),
					result.getAccepted() == 1 ? "" : "s",
					manifestOutput.toAbsolutePath(),
					skippedSuffix(result));

			BookRenderSupport.writeInteriorAndCover(
					pdfOutput,
					coverOutput,
					"crtk-ilovechess-",
					path -> writeInteriorPdf(path, book, freeWatermark, watermarkId),
					metricsPdf -> writeCoverPdf(coverOutput, book, metricsPdf, pages, binding, interior),
					path -> System.out.printf(Locale.ROOT, "%s wrote interior PDF to %s%n",
							COMMAND_LABEL, path.toAbsolutePath()),
					() -> System.out.printf(Locale.ROOT, "%s wrote %s cover to %s%n",
							COMMAND_LABEL, binding.token(), coverOutput.toAbsolutePath()));
		} catch (IllegalArgumentException ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, ex.getMessage(), ex, 2, verbose);
		} catch (IOException ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, "failed to build I Love Chess book: " + ex.getMessage(), ex,
					3, verbose);
		} catch (Exception ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, "unexpected failure: " + ex.getMessage(), ex, 3, verbose);
		}
	}

	/**
	 * Loads record JSON/JSONL input and applies the optional limit.
	 *
	 * @param input input record path
	 * @param limit optional maximum number of parsed records
	 * @param verbose whether verbose diagnostics are enabled
	 * @return parsed records in file order
	 * @throws IOException if the file cannot be read
	 */
	private static List<Record> loadRecords(Path input, int limit, boolean verbose) throws IOException {
		List<Record> records = new ArrayList<>();
		RecordIO.streamRecordFile(input, verbose, COMMAND_LABEL, new RecordIO.RecordConsumer() {
			/**
			 * Adds one valid record when the configured limit has not been reached.
			 *
			 * @param rec parsed record
			 */
			@Override
			public void accept(Record rec) {
				if (limit > 0 && records.size() >= limit) {
					return;
				}
				records.add(rec);
			}

			/**
			 * Ignores invalid record rows while loading usable puzzle inputs.
			 */
			@Override
			public void invalid() {
				// Invalid records are skipped; the builder reports only usable puzzle rows.
			}
		});
		return records;
	}

	/**
	 * Writes the generated manifest to disk.
	 *
	 * @param output output path
	 * @param book built book model
	 * @throws IOException if the file cannot be written
	 */
	private static void writeManifest(Path output, Book book) throws IOException {
		ensureParentDir(output);
		Files.writeString(output, TomlWriter.toToml(book), StandardCharsets.UTF_8);
	}

	/**
	 * Writes the interior PDF to disk.
	 *
	 * @param output output path
	 * @param book built book model
	 * @param freeWatermark whether to add the free-edition watermark
	 * @param watermarkId optional watermark identifier
	 * @throws IOException if the file cannot be written
	 */
	private static void writeInteriorPdf(Path output, Book book, boolean freeWatermark, String watermarkId)
			throws IOException {
		ensureParentDir(output);
		chess.book.render.Writer.write(output, book, freeWatermark, watermarkId);
	}

	/**
	 * Writes the cover PDF using dimensions inspected from the rendered interior.
	 *
	 * @param output output path
	 * @param book built book model
	 * @param interiorPdf rendered interior PDF path
	 * @param pages optional page override
	 * @param binding cover binding
	 * @param interior interior paper stock
	 * @throws IOException if writing fails
	 */
	private static void writeCoverPdf(Path output, Book book, Path interiorPdf, int pages, Binding binding,
			Interior interior) throws IOException {
		ensureParentDir(output);
		chess.book.cover.Writer.write(output, book, BookRenderSupport.coverOptions(interiorPdf, pages, binding,
				interior));
	}

	/**
	 * Prints validation details without writing files.
	 *
	 * @param result build result
	 * @param book built book model
	 */
	private static void printValidationSummary(BuildResult result, Book book) {
		ChessBookValidation.Summary summary = ChessBookValidation.validateBook(book);
		System.out.printf(Locale.ROOT,
				"%s OK: %d puzzle%s, %s, grid %dx%d, table frequency %d%s%n",
				COMMAND_LABEL,
				result.getAccepted(),
				result.getAccepted() == 1 ? "" : "s",
				book.getFullTitle(),
				summary.puzzleRows(),
				summary.puzzleColumns(),
				summary.tableFrequency(),
				skippedSuffix(result));
	}

	/**
	 * Returns the skipped-record suffix for console output.
	 *
	 * @param result build result
	 * @return suffix text
	 */
	private static String skippedSuffix(BuildResult result) {
		if (result.getSkipped() == 0) {
			return "";
		}
		return String.format(Locale.ROOT, " (%d skipped: %d without position, %d without PV)",
				result.getSkipped(),
				result.getSkippedWithoutPosition(),
				result.getSkippedWithoutVariation());
	}

}
