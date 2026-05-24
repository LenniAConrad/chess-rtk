package application.cli.command.book;

import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.OPT_CHECK;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_SUBTITLE;
import static application.cli.Constants.OPT_TITLE;
import static application.cli.Constants.OPT_VALIDATE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.PathOps.deriveOutputPath;
import static application.cli.PathOps.ensureParentDir;
import static application.cli.command.CommandSupport.resolveSingleInputPath;
import static application.cli.command.CommandSupport.trimToNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import application.cli.command.CommandSupport;
import application.cli.command.HelpCommand;
import application.cli.command.book.render.BookPdfSupport;
import application.cli.command.book.options.PdfCliOptions.CoverLayout;
import application.cli.command.book.options.PdfCliOptions.DiagramLayoutOverrides;
import chess.book.study.StudyBook;
import chess.book.study.StudyBookValidator;
import utility.Argv;

/**
 * Implements {@code book study}.
 *
 * <p>
 * The command loads a rich JSON/TOML composition manifest, optionally writes a
 * normalized TOML copy, renders the annotated interior PDF, and can build a
 * matching native cover using the existing cover writer.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings({"java:S107", "java:S3776"})
public final class PuzzleStudyCommand {

	/**
	 * Current command label used in diagnostics.
	 */
	private static final String COMMAND_LABEL = "book study";

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
	 * Manifest-output option flag.
	 */
	private static final String OPT_MANIFEST_OUTPUT = "--manifest-output";

	/**
	 * Cover-output option flag.
	 */
	private static final String OPT_COVER_OUTPUT = "--cover-output";

	/**
	 * Repeated blurb-line option flag.
	 */
	private static final String OPT_BLURB = "--blurb";

	/**
	 * Repeated link-line option flag.
	 */
	private static final String OPT_LINK = "--link";

	/**
	 * Utility class; prevent instantiation.
	 */
	private PuzzleStudyCommand() {
		// utility
	}

	/**
	 * Handles {@code book study}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPuzzleStudy(Argv a) {
		if (a.flag(CMD_HELP_SHORT, CMD_HELP_LONG)) {
			HelpCommand.runHelp(new Argv(new String[] { "book", "study" }));
			return;
		}

		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		Path manifestOutput = a.path(OPT_MANIFEST_OUTPUT);
		Path coverOutput = a.path(OPT_COVER_OUTPUT);
		String title = trimToNull(a.string(OPT_TITLE));
		String subtitle = trimToNull(a.string(OPT_SUBTITLE));
		String author = trimToNull(a.string(OPT_AUTHOR));
		String time = trimToNull(a.string(OPT_TIME));
		String location = trimToNull(a.string(OPT_LOCATION));
		DiagramLayoutOverrides layout = DiagramLayoutOverrides.parse(a);
		CoverLayout cover = CoverLayout.parse(a);
		boolean check = a.flag(OPT_CHECK, OPT_VALIDATE);
		List<String> blurb = a.strings(OPT_BLURB);
		List<String> link = a.strings(OPT_LINK);
		input = resolveSingleInputPath(a, input,
				"provide the input manifest either as --input or as one positional path",
				"missing input puzzle study JSON/TOML path");

		try {
			StudyBook book = StudyBook.load(input);
			applyOverrides(book, title, subtitle, author, time, location, cover, layout, blurb, link);
			StudyBookValidator.Summary summary = StudyBookValidator.validate(book);

			if (check) {
				printValidationSummary(book, summary);
				return;
			}

			if (manifestOutput != null) {
				writeManifest(manifestOutput, book);
				System.out.printf(Locale.ROOT, "%s wrote normalized manifest with %d composition%s to %s%n",
						COMMAND_LABEL,
						summary.compositions(),
						summary.compositions() == 1 ? "" : "s",
						manifestOutput.toAbsolutePath());
			}

			boolean wantsInteriorPdf = output != null || (manifestOutput == null && coverOutput == null);
			Path resolvedInteriorOutput = null;
			if (wantsInteriorPdf) {
				resolvedInteriorOutput = output != null ? output : deriveOutputPath(input, ".pdf");
			}

			BookPdfSupport.writeInteriorAndCover(
					resolvedInteriorOutput,
					coverOutput,
					"crtk-study-",
					path -> writeInteriorPdf(path, book),
					metricsPdf -> writeCoverPdf(coverOutput, book, metricsPdf, cover),
					path -> System.out.printf(Locale.ROOT,
							"%s wrote interior PDF with %d composition%s and %d diagram%s to %s%n",
							COMMAND_LABEL,
							summary.compositions(),
							summary.compositions() == 1 ? "" : "s",
							summary.diagrams(),
							summary.diagrams() == 1 ? "" : "s",
							path.toAbsolutePath()),
					() -> System.out.printf(Locale.ROOT, "%s wrote %s cover to %s%n",
							COMMAND_LABEL, cover.binding().token(), coverOutput.toAbsolutePath()));
		} catch (IllegalArgumentException ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, ex.getMessage(), ex, 2, verbose);
		} catch (IOException ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, "failed to build puzzle study: " + ex.getMessage(), ex,
					3, verbose);
		} catch (Exception ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, "unexpected failure: " + ex.getMessage(), ex, 3, verbose);
		}
	}

	/**
	 * Applies CLI overrides to the loaded manifest.
	 *
	 * @param book loaded manifest
	 * @param title title override
	 * @param subtitle subtitle override
	 * @param author author override
	 * @param time time override
	 * @param location location override
	 * @param cover cover and page-count overrides
	 * @param layout diagram layout overrides
	 * @param blurb cover blurb override
	 * @param link cover link override
	 */
	private static void applyOverrides(StudyBook book, String title, String subtitle, String author, String time,
			String location, CoverLayout cover, DiagramLayoutOverrides layout, List<String> blurb, List<String> link) {
		if (book == null) {
			throw new IllegalArgumentException("book cannot be null");
		}
		if (title != null) {
			book.setTitle(title);
		}
		if (subtitle != null) {
			book.setSubtitle(subtitle);
		}
		if (author != null) {
			book.setAuthor(author);
		}
		if (time != null) {
			book.setTime(time);
		}
		if (location != null) {
			book.setLocation(location);
		}
		if (cover.hasPagesOverride()) {
			book.setPages(cover.pagesOrZero());
		}
		if (layout.pageSize() != null) {
			book.setPageSize(layout.pageSize());
		}
		if (layout.diagramsPerRow() != null) {
			book.setDiagramsPerRow(layout.diagramsPerRow());
		}
		if (layout.boardPixels() != null) {
			book.setBoardPixels(layout.boardPixels());
		}
		if (layout.margin() != null) {
			book.setMargin(layout.margin());
		}
		if (layout.whiteSideDown() != null) {
			book.setWhiteSideDown(layout.whiteSideDown());
		}
		if (layout.showFen() != null) {
			book.setShowFen(layout.showFen());
		}
		if (!blurb.isEmpty()) {
			book.setBlurb(blurb.toArray(new String[0]));
		}
		if (!link.isEmpty()) {
			book.setLink(link.toArray(new String[0]));
		}
	}

	/**
	 * Writes the normalized manifest to disk.
	 *
	 * @param output output path
	 * @param book manifest to serialize
	 * @throws IOException if the file cannot be written
	 */
	private static void writeManifest(Path output, StudyBook book) throws IOException {
		ensureParentDir(output);
		Files.writeString(output, chess.book.study.TomlWriter.toToml(book), StandardCharsets.UTF_8);
	}

	/**
	 * Writes the annotated interior PDF to disk.
	 *
	 * @param output output path
	 * @param book manifest to render
	 * @throws IOException if the file cannot be written
	 */
	private static void writeInteriorPdf(Path output, StudyBook book) throws IOException {
		ensureParentDir(output);
		chess.pdf.Writer.writeCompositions(output, book.getFullTitle(), Arrays.asList(book.getCompositions()),
				book.toPdfOptions());
	}

	/**
	 * Writes the matching cover PDF using the rendered interior metrics.
	 *
	 * @param output output path
	 * @param book manifest to render
	 * @param interiorPdf rendered interior PDF path
	 * @param cover parsed cover layout options
	 * @throws IOException if the cover cannot be written
	 */
	private static void writeCoverPdf(Path output, StudyBook book, Path interiorPdf, CoverLayout cover)
			throws IOException {
		BookPdfSupport.writeBookCover(output, book.toCoverBook(), interiorPdf, cover);
	}

	/**
	 * Prints validation details without writing files.
	 *
	 * @param book validated manifest
	 * @param summary validation summary
	 */
	private static void printValidationSummary(StudyBook book, StudyBookValidator.Summary summary) {
		System.out.printf(Locale.ROOT,
				"%s OK: %d composition%s, %d diagram%s, %s, %s, %d per row%n",
				COMMAND_LABEL,
				summary.compositions(),
				summary.compositions() == 1 ? "" : "s",
				summary.diagrams(),
				summary.diagrams() == 1 ? "" : "s",
				book.getFullTitle(),
				StudyBook.pageSizeToken(book.getPageSize()),
				book.getDiagramsPerRow());
	}

}
