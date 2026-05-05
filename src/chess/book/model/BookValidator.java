package chess.book.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;

/**
 * Validates lean puzzle-grid book manifests.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BookValidator {

	/**
	 * Utility class; prevent instantiation.
	 */
	private BookValidator() {
		// utility
	}

	/**
	 * Validates the book manifest and every puzzle line.
	 *
	 * @param book loaded book model
	 * @return validation summary
	 */
	public static Summary validate(Book book) {
		if (book == null) {
			throw new IllegalArgumentException("book cannot be null");
		}
		validatePhysicalLayout(book);
		Element[] elements = book.getElements();
		if (elements.length == 0) {
			throw new IllegalArgumentException("book must contain at least one puzzle element");
		}
		for (int i = 0; i < elements.length; i++) {
			validateElement(elements[i], i + 1);
		}
		return new Summary(
				elements.length,
				book.getPaperWidthCm(),
				book.getPaperHeightCm(),
				book.getInnerMarginCm(),
				book.getOuterMarginCm(),
				book.getTopMarginCm(),
				book.getBottomMarginCm(),
				book.getPuzzleRows(),
				book.getPuzzleColumns(),
				book.getTableFrequency());
	}

	/**
	 * Validates paper size, margins, and puzzle-grid dimensions.
	 *
	 * @param book loaded book model
	 */
	private static void validatePhysicalLayout(Book book) {
		if (book.getPaperWidthCm() <= 0.0 || book.getPaperHeightCm() <= 0.0) {
			throw new IllegalArgumentException("paper dimensions must be positive");
		}
		if (book.getInnerMarginCm() < 0.0 || book.getOuterMarginCm() < 0.0
				|| book.getTopMarginCm() < 0.0 || book.getBottomMarginCm() < 0.0) {
			throw new IllegalArgumentException("page margins cannot be negative");
		}
		if (book.getInnerMarginCm() + book.getOuterMarginCm() >= book.getPaperWidthCm()) {
			throw new IllegalArgumentException("horizontal margins must be smaller than paper width");
		}
		if (book.getTopMarginCm() + book.getBottomMarginCm() >= book.getPaperHeightCm()) {
			throw new IllegalArgumentException("vertical margins must be smaller than paper height");
		}
		if (book.getPuzzleRows() <= 0 || book.getPuzzleColumns() <= 0 || book.getTableFrequency() <= 0) {
			throw new IllegalArgumentException("puzzle grid and table frequency must be positive");
		}
	}

	/**
	 * Validates one puzzle entry.
	 *
	 * @param element puzzle element
	 * @param number one-based puzzle number
	 */
	private static void validateElement(Element element, int number) {
		if (element == null) {
			throw new IllegalArgumentException("puzzle " + number + " is null");
		}
		String fen = element.getPosition();
		if (fen == null || fen.isBlank()) {
			throw new IllegalArgumentException("puzzle " + number + " has no FEN");
		}
		Position position = new Position(fen);
		String movesText = element.getMoves();
		if (movesText == null || movesText.isBlank()) {
			throw new IllegalArgumentException("puzzle " + number + " has no solution moves");
		}
		int ply = 0;
		for (String token : tokenizeMoves(movesText)) {
			ply++;
			try {
				short move = parseMove(position, token);
				position.play(move);
			} catch (Exception ex) {
				throw new IllegalArgumentException("puzzle " + number + " has invalid move at ply "
						+ ply + ": " + token + " (" + ex.getMessage() + ")", ex);
			}
		}
		if (ply == 0) {
			throw new IllegalArgumentException("puzzle " + number + " has no parsed solution moves");
		}
	}

	/**
	 * Cleans PGN-like movetext and splits it into move tokens.
	 *
	 * @param movesText raw move text
	 * @return normalized move tokens
	 */
	private static List<String> tokenizeMoves(String movesText) {
		String cleaned = SAN.cleanMoveString(movesText == null ? "" : movesText.trim());
		if (cleaned.isEmpty()) {
			return List.of();
		}
		return Arrays.asList(cleaned.split("\\s+"));
	}

	/**
	 * Parses one move token as UCI when possible, otherwise SAN.
	 *
	 * @param position current position
	 * @param token move token
	 * @return encoded move
	 */
	private static short parseMove(Position position, String token) {
		String trimmed = token.trim();
		String uciCandidate = trimmed.toLowerCase(Locale.ROOT);
		if (Move.isMove(uciCandidate)) {
			return parseUci(position, uciCandidate);
		}
		return SAN.fromAlgebraic(position, trimmed);
	}

	/**
	 * Parses and verifies a UCI move.
	 *
	 * @param position current position
	 * @param token UCI token
	 * @return encoded move
	 */
	private static short parseUci(Position position, String token) {
		short move = Move.parse(token);
		if (!position.isLegalMove(move)) {
			throw new IllegalArgumentException("Illegal move '" + token + "' in position '" + position + "'");
		}
		return move;
	}

	/**
	 * Book validation summary.
	 *
	 * @param puzzles puzzle count
	 * @param paperWidthCm paper width in centimeters
	 * @param paperHeightCm paper height in centimeters
	 * @param innerMarginCm inner margin in centimeters
	 * @param outerMarginCm outer margin in centimeters
	 * @param topMarginCm top margin in centimeters
	 * @param bottomMarginCm bottom margin in centimeters
	 * @param puzzleRows puzzle rows per page
	 * @param puzzleColumns puzzle columns per page
	 * @param tableFrequency solution table cadence
	 */
	public record Summary(
		/**
		 * Stores the puzzles.
		 */
		int puzzles,
		/**
		 * Stores the paper width cm.
		 */
		double paperWidthCm,
		/**
		 * Stores the paper height cm.
		 */
		double paperHeightCm,
		/**
		 * Stores the inner margin cm.
		 */
		double innerMarginCm,
		/**
		 * Stores the outer margin cm.
		 */
		double outerMarginCm,
		/**
		 * Stores the top margin cm.
		 */
		double topMarginCm,
		/**
		 * Stores the bottom margin cm.
		 */
		double bottomMarginCm,
		/**
		 * Stores the puzzle rows.
		 */
		int puzzleRows,
		/**
		 * Stores the puzzle columns.
		 */
		int puzzleColumns,
		/**
		 * Stores the table frequency.
		 */
		int tableFrequency
	) {
	}
}
