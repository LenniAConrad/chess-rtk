package application.gui.studio;

import java.util.List;

import chess.core.Field;
import chess.core.Move;
import chess.core.Position;

/**
 * Immutable board rendering payload.
 *
 * @param position position to render
 * @param whiteDown board orientation
 * @param selectedSquare selected square
 * @param legalTargets legal target mask
 * @param captureTargets capture target mask
 * @param lastMove last played move
 * @param marks manual board marks
 * @param theme active theme
 */
public record BoardViewModel(
		Position position,
		boolean whiteDown,
		byte selectedSquare,
		boolean[] legalTargets,
		boolean[] captureTargets,
		short lastMove,
		List<BoardMark> marks,
		StudioTheme theme) {

	/**
	 * Creates a default board model.
	 *
	 * @param position position to render
	 * @param whiteDown orientation
	 * @param theme theme
	 * @return view model
	 */
	public static BoardViewModel of(Position position, boolean whiteDown, StudioTheme theme) {
		return new BoardViewModel(position, whiteDown, Field.NO_SQUARE, new boolean[64],
				new boolean[64], Move.NO_MOVE, List.of(), theme);
	}
}
