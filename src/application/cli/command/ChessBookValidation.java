package application.cli.command;

import chess.book.model.Book;
import chess.book.model.Element;
import chess.core.Position;

/**
 * Shared validation helpers for book and cover commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class ChessBookValidation {

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessBookValidation() {
		// utility
	}

	/**
	 * Validates the book manifest and every puzzle line.
	 *
	 * @param book loaded book model.
	 * @return validation summary.
	 */
	static Summary validateBook(Book book) {
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
	 * @param book loaded book model.
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
	 * @param element puzzle element.
	 * @param number  one-based puzzle number.
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
		for (String token : MoveCommandSupport.tokenizeMoves(java.util.List.of(movesText))) {
			ply++;
			try {
				short move = MoveCommandSupport.parseMove(position, token, MoveCommandSupport.MoveFormat.AUTO);
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
	 * Book validation summary.
	 *
	 * @param puzzles        puzzle count.
	 * @param paperWidthCm   paper width in centimeters.
	 * @param paperHeightCm  paper height in centimeters.
	 * @param innerMarginCm  inner margin in centimeters.
	 * @param outerMarginCm  outer margin in centimeters.
	 * @param topMarginCm    top margin in centimeters.
	 * @param bottomMarginCm bottom margin in centimeters.
	 * @param puzzleRows     puzzle rows per page.
	 * @param puzzleColumns  puzzle columns per page.
	 * @param tableFrequency solution table cadence.
	 */
	record Summary(
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
