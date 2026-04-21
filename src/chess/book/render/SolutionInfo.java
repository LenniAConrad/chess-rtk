package chess.book.render;

import chess.core.Move;
import chess.core.Position;

/**
 * Stores one parsed puzzle solution.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class SolutionInfo {

	/**
	 * Final position after applying the solution line.
	 */
	final Position result;

	/**
	 * Last move in compact internal form, or {@link Move#NO_MOVE}.
	 */
	final short lastMove;

	/**
	 * Last SAN token with its move number, used under solution diagrams.
	 */
	final String lastSanLabel;

	/**
	 * Creates a parsed solution description.
	 *
	 * @param result final position
	 * @param lastMove last move, or {@link Move#NO_MOVE}
	 * @param lastSanLabel move-numbered last SAN label
	 */
	SolutionInfo(Position result, short lastMove, String lastSanLabel) {
		this.result = result;
		this.lastMove = lastMove;
		this.lastSanLabel = BookModelText.blankTo(lastSanLabel, "");
	}
}
