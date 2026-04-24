package application.cli.command;

import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.OPT_BINDING;
import static application.cli.Constants.OPT_BLACK_DOWN;
import static application.cli.Constants.OPT_BOARD_PIXELS;
import static application.cli.Constants.OPT_CHECK;
import static application.cli.Constants.OPT_DIAGRAMS_PER_ROW;
import static application.cli.Constants.OPT_FLIP;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_INTERIOR;
import static application.cli.Constants.OPT_NO_FEN;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PAGE_SIZE;
import static application.cli.Constants.OPT_PAGES;
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

import chess.book.cover.Binding;
import chess.book.cover.Interior;
import chess.book.series.artofchess.ArtBook;
import chess.core.Move;
import chess.core.Position;
import chess.pdf.Composition;
import utility.Argv;

/**
 * Implements {@code book artofchess}.
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
public final class ChessArtOfChessCommand {

	/**
	 * Current command label used in diagnostics.
	 */
	private static final String COMMAND_LABEL = "book artofchess";

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
	 * Margin override option flag.
	 */
	private static final String OPT_MARGIN = "--margin";

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessArtOfChessCommand() {
		// utility
	}

	/**
	 * Handles {@code book artofchess}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runArtOfChess(Argv a) {
		if (a.flag(CMD_HELP_SHORT, CMD_HELP_LONG)) {
			HelpCommand.runHelp(new Argv(new String[] { "book", "artofchess" }));
			return;
		}

		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean blackDown = a.flag(OPT_FLIP, OPT_BLACK_DOWN);
		boolean hideFen = a.flag(OPT_NO_FEN);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		Path manifestOutput = a.path(OPT_MANIFEST_OUTPUT);
		Path coverOutput = a.path(OPT_COVER_OUTPUT);
		String title = trimToNull(a.string(OPT_TITLE));
		String subtitle = trimToNull(a.string(OPT_SUBTITLE));
		String author = trimToNull(a.string(OPT_AUTHOR));
		String time = trimToNull(a.string(OPT_TIME));
		String location = trimToNull(a.string(OPT_LOCATION));
		String pageSizeText = trimToNull(a.string(OPT_PAGE_SIZE));
		Integer pages = a.integer(OPT_PAGES);
		Integer diagramsPerRow = a.integer(OPT_DIAGRAMS_PER_ROW);
		Integer boardPixels = a.integer(OPT_BOARD_PIXELS);
		Double margin = a.dbl(OPT_MARGIN);
		Binding binding = Binding.parse(a.string(OPT_BINDING));
		Interior interior = Interior.parse(a.string(OPT_INTERIOR));
		boolean check = a.flag(OPT_CHECK, OPT_VALIDATE);
		List<String> blurb = a.strings(OPT_BLURB);
		List<String> link = a.strings(OPT_LINK);
		input = resolveSingleInputPath(a, input,
				"provide the input manifest either as --input or as one positional path",
				"missing input Art of Chess JSON/TOML path");

		try {
			ArtBook book = ArtBook.load(input);
			applyOverrides(book, title, subtitle, author, time, location, pages, pageSizeText, diagramsPerRow,
					boardPixels, margin, blackDown, hideFen, blurb, link);
			Summary summary = validate(book);

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

			BookRenderSupport.writeInteriorAndCover(
					resolvedInteriorOutput,
					coverOutput,
					"crtk-artofchess-",
					path -> writeInteriorPdf(path, book),
					metricsPdf -> writeCoverPdf(coverOutput, book, metricsPdf, pages, binding, interior),
					path -> System.out.printf(Locale.ROOT,
							"%s wrote interior PDF with %d composition%s and %d diagram%s to %s%n",
							COMMAND_LABEL,
							summary.compositions(),
							summary.compositions() == 1 ? "" : "s",
							summary.diagrams(),
							summary.diagrams() == 1 ? "" : "s",
							path.toAbsolutePath()),
					() -> System.out.printf(Locale.ROOT, "%s wrote %s cover to %s%n",
							COMMAND_LABEL, binding.token(), coverOutput.toAbsolutePath()));
		} catch (IllegalArgumentException ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, ex.getMessage(), ex, 2, verbose);
		} catch (IOException ex) {
			CommandSupport.exitWithError(COMMAND_LABEL, "failed to build Art of Chess book: " + ex.getMessage(), ex,
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
	 * @param pages pages override
	 * @param pageSizeText page-size override
	 * @param diagramsPerRow diagrams-per-row override
	 * @param boardPixels board-pixel override
	 * @param margin margin override
	 * @param blackDown whether Black should be at the bottom
	 * @param hideFen whether FEN text should be hidden
	 * @param blurb cover blurb override
	 * @param link cover link override
	 */
	private static void applyOverrides(ArtBook book, String title, String subtitle, String author, String time,
			String location, Integer pages, String pageSizeText, Integer diagramsPerRow, Integer boardPixels,
			Double margin, boolean blackDown, boolean hideFen, List<String> blurb, List<String> link) {
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
		if (pages != null) {
			book.setPages(pages);
		}
		if (pageSizeText != null) {
			book.setPageSize(PdfCommandSupport.parsePageSize(pageSizeText));
		}
		if (diagramsPerRow != null) {
			book.setDiagramsPerRow(diagramsPerRow);
		}
		if (boardPixels != null) {
			book.setBoardPixels(boardPixels);
		}
		if (margin != null) {
			book.setMargin(margin);
		}
		if (blackDown) {
			book.setWhiteSideDown(false);
		}
		if (hideFen) {
			book.setShowFen(false);
		}
		if (!blurb.isEmpty()) {
			book.setBlurb(blurb.toArray(new String[0]));
		}
		if (!link.isEmpty()) {
			book.setLink(link.toArray(new String[0]));
		}
	}

	/**
	 * Validates the manifest and returns a small rendering summary.
	 *
	 * @param book manifest to validate
	 * @return validation summary
	 */
	private static Summary validate(ArtBook book) {
		if (book == null) {
			throw new IllegalArgumentException("book cannot be null");
		}
		if (book.getMargin() < 12.0) {
			throw new IllegalArgumentException("margin must be at least 12 points");
		}
		if (book.getDiagramsPerRow() <= 0) {
			throw new IllegalArgumentException("diagrams per row must be positive");
		}
		if (book.getBoardPixels() < 256) {
			throw new IllegalArgumentException("boardPixels must be at least 256");
		}

		Composition[] compositions = book.getCompositions();
		if (compositions.length == 0) {
			throw new IllegalArgumentException("book must contain at least one composition");
		}

		int diagrams = 0;
		for (int i = 0; i < compositions.length; i++) {
			diagrams += validateComposition(compositions[i], i + 1);
		}
		return new Summary(compositions.length, diagrams);
	}

	/**
	 * Validates one composition entry.
	 *
	 * @param composition composition entry
	 * @param number one-based composition number
	 * @return number of rendered diagrams
	 */
	private static int validateComposition(Composition composition, int number) {
		if (composition == null) {
			throw new IllegalArgumentException("composition " + number + " is null");
		}
		boolean hasBody = !composition.getTitle().isBlank()
				|| !composition.getDescription().isBlank()
				|| !composition.getComment().isBlank()
				|| !composition.getAnalysis().isBlank()
				|| !composition.getHintLevel1().isBlank()
				|| !composition.getHintLevel2().isBlank()
				|| !composition.getHintLevel3().isBlank()
				|| !composition.getHintLevel4().isBlank()
				|| !composition.getFigureFens().isEmpty();
		if (!hasBody) {
			throw new IllegalArgumentException("composition " + number + " has no content");
		}

		List<String> fens = composition.getFigureFens();
		for (int i = 0; i < fens.size(); i++) {
			String fen = fens.get(i);
			if (fen == null || fen.isBlank()) {
				throw new IllegalArgumentException("composition " + number + " has a blank figure FEN");
			}
			new Position(fen);
		}
		List<String> arrows = composition.getFigureArrows();
		for (int i = 0; i < arrows.size(); i++) {
			String arrow = arrows.get(i);
			if (arrow == null || arrow.isBlank()) {
				continue;
			}
			if (!Move.isMove(arrow)) {
				throw new IllegalArgumentException(
						"composition " + number + " has invalid figure arrow " + (i + 1) + ": " + arrow);
			}
		}
		return fens.size();
	}

	/**
	 * Writes the normalized manifest to disk.
	 *
	 * @param output output path
	 * @param book manifest to serialize
	 * @throws IOException if the file cannot be written
	 */
	private static void writeManifest(Path output, ArtBook book) throws IOException {
		ensureParentDir(output);
		Files.writeString(output, chess.book.series.artofchess.TomlWriter.toToml(book), StandardCharsets.UTF_8);
	}

	/**
	 * Writes the annotated interior PDF to disk.
	 *
	 * @param output output path
	 * @param book manifest to render
	 * @throws IOException if the file cannot be written
	 */
	private static void writeInteriorPdf(Path output, ArtBook book) throws IOException {
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
	 * @param pages optional CLI page override
	 * @param binding selected binding
	 * @param interior selected paper stock
	 * @throws IOException if the cover cannot be written
	 */
	private static void writeCoverPdf(Path output, ArtBook book, Path interiorPdf, Integer pages, Binding binding,
			Interior interior) throws IOException {
		ensureParentDir(output);
		chess.book.cover.Writer.write(output, book.toCoverBook(), BookRenderSupport.coverOptions(interiorPdf,
				pages == null ? 0 : pages, binding, interior));
	}

	/**
	 * Prints validation details without writing files.
	 *
	 * @param book validated manifest
	 * @param summary validation summary
	 */
	private static void printValidationSummary(ArtBook book, Summary summary) {
		System.out.printf(Locale.ROOT,
				"%s OK: %d composition%s, %d diagram%s, %s, %s, %d per row%n",
				COMMAND_LABEL,
				summary.compositions(),
				summary.compositions() == 1 ? "" : "s",
				summary.diagrams(),
				summary.diagrams() == 1 ? "" : "s",
				book.getFullTitle(),
				ArtBook.pageSizeToken(book.getPageSize()),
				book.getDiagramsPerRow());
	}

	/**
	 * Validation summary for console output.
	 *
	 * @param compositions composition count
	 * @param diagrams diagram count
	 */
	private record Summary(int compositions, int diagrams) {
	}
}
