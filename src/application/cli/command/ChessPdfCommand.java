package application.cli.command;

import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.OPT_BLACK_DOWN;
import static application.cli.Constants.OPT_BOARD_PIXELS;
import static application.cli.Constants.OPT_DIAGRAMS_PER_ROW;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_FLIP;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_NO_FEN;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PAGE_SIZE;
import static application.cli.Constants.OPT_PGN;
import static application.cli.Constants.OPT_TITLE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.PathOps.deriveOutputPath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import application.cli.PgnOps;
import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.io.Reader;
import chess.pdf.Writer;
import chess.pdf.Options;
import chess.pdf.Composition;
import chess.struct.Game;
import chess.struct.Record;
import chess.text.ChessMoveText;
import utility.Argv;
import chess.pdf.document.PageSize;

/**
 * Implements {@code book pdf}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ChessPdfCommand {

	/**
	 * Current command label used in diagnostics.
	 */
	private static final String COMMAND_LABEL = "book pdf";

	/**
	 * Supported page-size names for the command-line option.
	 */
	private static final Map<String, PageSize> PAGE_SIZES = Map.of(
			"a4", PageSize.A4,
			"a5", PageSize.A5,
			"letter", PageSize.LETTER);

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessPdfCommand() {
		// utility
	}

	/**
	 * Handles {@code book pdf}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runChessPdf(Argv a) {
		if (a.flag(CMD_HELP_SHORT, CMD_HELP_LONG)) {
			HelpCommand.runHelp(new Argv(new String[] { "book", "pdf" }));
			return;
		}

		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean blackDown = a.flag(OPT_FLIP, OPT_BLACK_DOWN);
		boolean showFen = !a.flag(OPT_NO_FEN);
		List<String> fenOptions = a.strings(OPT_FEN);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		Path pgn = a.path(OPT_PGN);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		String title = trimToNull(a.string(OPT_TITLE));
		String pageSizeText = trimToNull(a.string(OPT_PAGE_SIZE));
		int diagramsPerRow = a.integerOr(2, OPT_DIAGRAMS_PER_ROW);
		int boardPixels = a.integerOr(900, OPT_BOARD_PIXELS);
		List<String> rest = a.positionals();
		a.ensureConsumed();

		try {
			validateExclusiveSources(fenOptions, rest, input, pgn);

			Options options = new Options()
					.setPageSize(parsePageSize(pageSizeText))
					.setDiagramsPerRow(diagramsPerRow)
					.setBoardPixels(boardPixels)
					.setWhiteSideDown(!blackDown)
					.setShowFen(showFen);

			if (pgn != null) {
				runPgnExport(pgn, output, title, options, verbose);
				return;
			}

			List<String> fens = resolveFenInputs(fenOptions, rest, input);
			Composition composition = compositionFromFens(fens, defaultFenTitle(title, input, fens.size()));
			Path resolvedOutput = resolveOutputPath(output, input);
			Writer.writeComposition(resolvedOutput, composition, options);
			System.out.printf(Locale.ROOT, "%s wrote %d diagram%s to %s%n",
					COMMAND_LABEL,
					fens.size(),
					fens.size() == 1 ? "" : "s",
					resolvedOutput.toAbsolutePath());
		} catch (IllegalArgumentException ex) {
			System.err.println(COMMAND_LABEL + ": " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		} catch (IOException ex) {
			System.err.println(COMMAND_LABEL + ": failed to generate PDF: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception ex) {
			System.err.println(COMMAND_LABEL + ": unexpected failure: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Renders one or more PGN games as a multi-composition PDF.
	 *
	 * @param pgn PGN input path
	 * @param output optional output path
	 * @param title optional document title
	 * @param options PDF rendering options
	 * @param verbose whether verbose diagnostics are enabled
	 * @throws IOException if the PGN or output file cannot be read or written
	 */
	private static void runPgnExport(Path pgn, Path output, String title, Options options, boolean verbose)
			throws IOException {
		List<Game> games = PgnOps.readPgnOrExit(pgn, verbose, COMMAND_LABEL);
		if (games.isEmpty()) {
			throw new IllegalArgumentException("PGN input has no games: " + pgn.toAbsolutePath());
		}

		List<Composition> compositions = new ArrayList<>(games.size());
		int diagrams = 0;
		for (int i = 0; i < games.size(); i++) {
			Composition composition = compositionFromGame(games.get(i), i + 1,
					games.size() == 1 ? title : null);
			compositions.add(composition);
			diagrams += composition.getFigureFens().size();
		}

		String documentTitle = title != null ? title : stem(pgn);
		Path resolvedOutput = output != null ? output : deriveOutputPath(pgn, ".pdf");
		Writer.writeCompositions(resolvedOutput, documentTitle, compositions, options);
		System.out.printf(Locale.ROOT, "%s wrote %d game%s and %d diagram%s to %s%n",
				COMMAND_LABEL,
				games.size(),
				games.size() == 1 ? "" : "s",
				diagrams,
				diagrams == 1 ? "" : "s",
				resolvedOutput.toAbsolutePath());
	}

	/**
	 * Resolves FEN input from command-line options, positional arguments, or an
	 * input file.
	 *
	 * @param fenOptions repeated {@code --fen} values
	 * @param rest positional arguments
	 * @param input optional record input path
	 * @return validated FEN strings
	 * @throws IOException if the record input file cannot be read
	 */
	private static List<String> resolveFenInputs(List<String> fenOptions, List<String> rest, Path input)
			throws IOException {
		if (!fenOptions.isEmpty()) {
			return validateFens(fenOptions);
		}
		if (!rest.isEmpty()) {
			return validateFens(List.of(String.join(" ", rest).trim()));
		}
		if (input != null) {
			List<Record> records = Reader.readPositionRecords(input);
			List<String> fens = new ArrayList<>(records.size());
			for (Record positionRecord : records) {
				if (positionRecord.getPosition() != null) {
					fens.add(positionRecord.getPosition().toString());
				}
			}
			if (fens.isEmpty()) {
				throw new IllegalArgumentException("input file has no valid positions: " + input.toAbsolutePath());
			}
			return fens;
		}
		throw new IllegalArgumentException("provide one source via --fen, --input, or --pgn");
	}

	/**
	 * Validates candidate FEN strings and removes blank values.
	 *
	 * @param candidates candidate FEN strings
	 * @return validated FEN strings
	 */
	private static List<String> validateFens(List<String> candidates) {
		List<String> fens = new ArrayList<>(candidates.size());
		for (String candidate : candidates) {
			String fen = trimToNull(candidate);
			if (fen == null) {
				continue;
			}
			new Position(fen);
			fens.add(fen);
		}
		if (fens.isEmpty()) {
			throw new IllegalArgumentException("no valid FEN input was provided");
		}
		return fens;
	}

	/**
	 * Ensures the command received exactly one input source.
	 *
	 * @param fenOptions repeated {@code --fen} values
	 * @param rest positional arguments
	 * @param input optional FEN-record input path
	 * @param pgn optional PGN input path
	 */
	private static void validateExclusiveSources(List<String> fenOptions, List<String> rest, Path input, Path pgn) {
		int sourceCount = 0;
		if (!fenOptions.isEmpty() || !rest.isEmpty()) {
			sourceCount++;
		}
		if (input != null) {
			sourceCount++;
		}
		if (pgn != null) {
			sourceCount++;
		}
		if (sourceCount == 0) {
			throw new IllegalArgumentException("provide one source via --fen, --input, or --pgn");
		}
		if (sourceCount > 1) {
			throw new IllegalArgumentException("provide exactly one source: --fen, --input, or --pgn");
		}
		if (!fenOptions.isEmpty() && !rest.isEmpty()) {
			throw new IllegalArgumentException("use either repeated --fen or one positional FEN, not both");
		}
	}

	/**
	 * Builds a simple composition from a list of positions.
	 *
	 * @param fens FEN strings to render
	 * @param title composition title
	 * @return populated composition
	 */
	private static Composition compositionFromFens(List<String> fens, String title) {
		Composition composition = new Composition().setTitle(title);
		for (int i = 0; i < fens.size(); i++) {
			String caption = fens.size() == 1 ? "Position" : "Diagram " + (i + 1);
			composition.addFigure(fens.get(i), caption, "", "");
		}
		return composition;
	}

	/**
	 * Converts one PGN game into a composition with one diagram per mainline
	 * position.
	 *
	 * @param game source game
	 * @param index one-based game index
	 * @param titleOverride optional explicit title
	 * @return populated composition
	 */
	private static Composition compositionFromGame(Game game, int index, String titleOverride) {
		Position start = game.getStartPosition() != null
				? game.getStartPosition().copyOf()
				: new Position(Game.STANDARD_START_FEN);

		Composition composition = new Composition()
				.setTitle(titleOverride != null ? titleOverride : gameTitle(game, index))
				.setDescription(gameDescription(game))
				.setComment(gameComment(game));

		composition.addFigure(start, "Start", startDetail(start), "");

		Position current = start.copyOf();
		Game.Node node = game.getMainline();
		while (node != null) {
			short move;
			try {
				move = SAN.fromAlgebraic(current, node.getSan());
			} catch (IllegalArgumentException ex) {
				String existing = composition.getComment();
				String suffix = "Stopped after invalid SAN: " + node.getSan();
				composition.setComment(existing.isBlank() ? suffix : existing + "\n\n" + suffix);
				break;
			}

			String caption = moveCaption(current, node.getSan());
			String detail = moveDetail(node);
			Position next = current.copyOf().play(move);
			composition.addFigure(next, caption, detail, Move.toString(move));
			current = next;
			node = node.getNext();
		}

		return composition;
	}

	/**
	 * Builds the move-numbered caption for one PGN move.
	 *
	 * @param before position before the move
	 * @param san SAN move token
	 * @return figurine move caption
	 */
	private static String moveCaption(Position before, String san) {
		return before.isWhiteTurn()
				? before.getFullMove() + ". " + ChessMoveText.figurine(san)
				: before.getFullMove() + "... " + ChessMoveText.figurine(san);
	}

	/**
	 * Joins comments attached to one PGN move.
	 *
	 * @param node PGN move node
	 * @return combined comment text
	 */
	private static String moveDetail(Game.Node node) {
		List<String> comments = new ArrayList<>();
		comments.addAll(node.getCommentsBefore());
		comments.addAll(node.getCommentsAfter());
		return joinNonBlank(comments, " ");
	}

	/**
	 * Builds a user-facing game title from PGN tags.
	 *
	 * @param game source game
	 * @param index one-based game index
	 * @return title text
	 */
	private static String gameTitle(Game game, int index) {
		String white = tag(game, "White");
		String black = tag(game, "Black");
		if (!white.isBlank() && !black.isBlank()) {
			return white + " - " + black;
		}
		String event = tag(game, "Event");
		if (!event.isBlank()) {
			return event;
		}
		return "Game " + index;
	}

	/**
	 * Builds descriptive metadata text from PGN tags and preamble comments.
	 *
	 * @param game source game
	 * @return description text
	 */
	private static String gameDescription(Game game) {
		List<String> meta = new ArrayList<>();
		addMeta(meta, tag(game, "Event"));
		addMeta(meta, tag(game, "Site"));
		addMeta(meta, tag(game, "Date"));
		addMeta(meta, tag(game, "Round"));
		String result = game.getResult();
		if (result != null && !result.isBlank() && !"*".equals(result)) {
			meta.add("Result: " + result);
		}

		String preamble = joinNonBlank(game.getPreambleComments(), " ");
		if (preamble.isBlank()) {
			return String.join(" | ", meta);
		}
		if (meta.isEmpty()) {
			return preamble;
		}
		return String.join(" | ", meta) + "\n\n" + preamble;
	}

	/**
	 * Builds a short game-level comment from the result tag.
	 *
	 * @param game source game
	 * @return comment text
	 */
	private static String gameComment(Game game) {
		String result = game.getResult();
		if (result == null || result.isBlank() || "*".equals(result)) {
			return "";
		}
		return "Result: " + result;
	}

	/**
	 * Builds detail text for the initial diagram.
	 *
	 * @param start game start position
	 * @return detail text
	 */
	private static String startDetail(Position start) {
		return Game.STANDARD_START_FEN.equals(start.toString()) ? "Initial position" : "Custom start position";
	}

	/**
	 * Adds a nonblank metadata value to the target list.
	 *
	 * @param meta metadata list to update
	 * @param value candidate metadata value
	 */
	private static void addMeta(List<String> meta, String value) {
		if (value != null && !value.isBlank()) {
			meta.add(value);
		}
	}

	/**
	 * Returns one PGN tag value.
	 *
	 * @param game source game
	 * @param key tag key
	 * @return tag value or an empty string
	 */
	private static String tag(Game game, String key) {
		return game.getTags().getOrDefault(key, "");
	}

	/**
	 * Joins nonblank values with the requested separator.
	 *
	 * @param values source values
	 * @param separator separator text
	 * @return joined text
	 */
	private static String joinNonBlank(List<String> values, String separator) {
		List<String> parts = new ArrayList<>(values.size());
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				parts.add(value.trim());
			}
		}
		return String.join(separator, parts);
	}

	/**
	 * Parses the command-line page-size value.
	 *
	 * @param text page-size option text
	 * @return matching page size
	 */
	private static PageSize parsePageSize(String text) {
		if (text == null) {
			return PageSize.A4;
		}
		PageSize pageSize = PAGE_SIZES.get(text.toLowerCase(Locale.ROOT));
		if (pageSize == null) {
			throw new IllegalArgumentException("unsupported page size '" + text + "' (use a4, a5, or letter)");
		}
		return pageSize;
	}

	/**
	 * Resolves the default output path for a FEN-based export.
	 *
	 * @param output explicit output path
	 * @param input optional input path
	 * @return output path
	 */
	private static Path resolveOutputPath(Path output, Path input) {
		if (output != null) {
			return output;
		}
		if (input != null) {
			return deriveOutputPath(input, ".pdf");
		}
		return Path.of("chess.pdf");
	}

	/**
	 * Builds the default title for a FEN-based export.
	 *
	 * @param title explicit title
	 * @param input optional input path
	 * @param amount number of diagrams
	 * @return document title
	 */
	private static String defaultFenTitle(String title, Path input, int amount) {
		if (title != null) {
			return title;
		}
		if (input != null) {
			return stem(input);
		}
		return amount == 1 ? "Chess Position" : "Chess Diagram Sheet";
	}

	/**
	 * Returns the file name without the final extension.
	 *
	 * @param path source path
	 * @return stem text
	 */
	private static String stem(Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	/**
	 * Trims a string and maps blanks to {@code null}.
	 *
	 * @param value source value
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
