package chess.book.study;

import java.util.List;

import chess.core.Move;
import chess.core.Position;
import chess.pdf.Composition;

/**
 * Validates rich puzzle-study manifests.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class StudyBookValidator {

	/**
	 * Utility class; prevent instantiation.
	 */
	private StudyBookValidator() {
		// utility
	}

	/**
	 * Validates the manifest and returns a small rendering summary.
	 *
	 * @param book manifest to validate
	 * @return validation summary
	 */
	public static Summary validate(StudyBook book) {
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
	 * Validation summary for console output.
	 *
	 * @param compositions composition count
	 * @param diagrams diagram count
	 */
	public record Summary(int compositions, int diagrams) {
	}
}
