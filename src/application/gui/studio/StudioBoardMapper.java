package application.gui.studio;

import java.awt.Point;
import java.awt.Rectangle;

import chess.core.Field;

/**
 * Maps chess squares to board pixels for both orientations.
 */
public final class StudioBoardMapper {

	/**
	 * Utility constructor.
	 */
	private StudioBoardMapper() {
		// utility
	}

	/**
	 * Converts a point to a square index.
	 *
	 * @param point point in component coordinates
	 * @param board board rectangle
	 * @param whiteDown true when White is at the bottom
	 * @return square index or {@link Field#NO_SQUARE}
	 */
	public static byte squareAt(Point point, Rectangle board, boolean whiteDown) {
		if (point == null || board == null || !board.contains(point)) {
			return Field.NO_SQUARE;
		}
		int tile = board.width / 8;
		if (tile <= 0) {
			return Field.NO_SQUARE;
		}
		int fileOnScreen = Math.min(7, Math.max(0, (point.x - board.x) / tile));
		int rankOnScreen = Math.min(7, Math.max(0, (point.y - board.y) / tile));
		int file = whiteDown ? fileOnScreen : 7 - fileOnScreen;
		int rank = whiteDown ? 7 - rankOnScreen : rankOnScreen;
		return (byte) Field.toIndex(file, rank);
	}

	/**
	 * Returns the square bounds.
	 *
	 * @param square square index
	 * @param board board rectangle
	 * @param whiteDown true when White is at the bottom
	 * @return square rectangle
	 */
	public static Rectangle squareBounds(byte square, Rectangle board, boolean whiteDown) {
		int tile = board.width / 8;
		int screenFile = screenFile(square, whiteDown);
		int screenRank = screenRank(square, whiteDown);
		return new Rectangle(board.x + screenFile * tile, board.y + screenRank * tile, tile, tile);
	}

	/**
	 * Returns the file coordinate on screen.
	 *
	 * @param square square index
	 * @param whiteDown true when White is at the bottom
	 * @return screen file 0..7
	 */
	public static int screenFile(byte square, boolean whiteDown) {
		int file = Field.getX(square);
		return whiteDown ? file : 7 - file;
	}

	/**
	 * Returns the rank coordinate on screen.
	 *
	 * @param square square index
	 * @param whiteDown true when White is at the bottom
	 * @return screen rank 0..7
	 */
	public static int screenRank(byte square, boolean whiteDown) {
		int rank = Field.getY(square);
		return whiteDown ? 7 - rank : rank;
	}

	/**
	 * Computes the square board rectangle inside a component.
	 *
	 * @param width component width
	 * @param height component height
	 * @return centered square board rectangle
	 */
	public static Rectangle centeredBoard(int width, int height) {
		int size = Math.max(1, Math.min(width, height));
		size -= size % 8;
		return new Rectangle((width - size) / 2, (height - size) / 2, size, size);
	}
}
