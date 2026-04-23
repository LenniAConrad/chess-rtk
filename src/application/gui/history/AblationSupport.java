package application.gui.history;

import application.cli.Format;
import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Stateless helpers for ablation table/label formatting.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class AblationSupport {

	/**
	 * AblationSupport method.
	 */
	private AblationSupport() {
	}

	/**
	 * normalizeForWhite method.
	 *
	 * @param position parameter.
	 * @param matrix parameter.
	 */
	public static void normalizeForWhite(Position position, int[][] matrix) {
		if (position == null || matrix == null || position.isWhiteToMove()) {
			return;
		}
		for (int rank = 0; rank < matrix.length; rank++) {
			int[] row = matrix[rank];
			for (int file = 0; file < row.length; file++) {
				row[file] = -row[file];
			}
		}
	}

	/**
	 * materialScales method.
	 *
	 * @param matrix parameter.
	 * @param board parameter.
	 * @return return value.
	 */
	public static double[] materialScales(int[][] matrix, byte[] board) {
		int[] counts = new int[7];
		long[] sumAbs = new long[7];
		for (int index = 0; index < board.length; index++) {
			byte piece = board[index];
			if (Piece.isEmpty(piece)) {
				continue;
			}
			int file = Field.getX((byte) index);
			int rankFromBottom = Field.getY((byte) index);
			int raw = matrix[rankFromBottom][file];
			int type = Math.abs(piece);
			sumAbs[type] += Math.abs(raw);
			counts[type]++;
		}

		double[] scales = new double[7];
		for (int type = 1; type <= 6; type++) {
			if (counts[type] == 0) {
				scales[type] = 1.0;
				continue;
			}
			double avg = sumAbs[type] / (double) counts[type];
			int material = Math.abs(Piece.getValue((byte) type));
			if (material <= 0 || avg <= 0.0) {
				scales[type] = 1.0;
			} else {
				scales[type] = material / avg;
			}
		}
		return scales;
	}

	/**
	 * buildLabels method.
	 *
	 * @param matrix parameter.
	 * @param board parameter.
	 * @return return value.
	 */
	public static String[] buildLabels(int[][] matrix, byte[] board) {
		if (matrix == null || board == null) {
			return null;
		}
		double[] scales = materialScales(matrix, board);
		String[] labels = new String[64];
		for (int index = 0; index < board.length; index++) {
			byte piece = board[index];
			if (Piece.isEmpty(piece)) {
				continue;
			}
			int file = Field.getX((byte) index);
			int rank = Field.getY((byte) index);
			int delta = matrix[rank][file];
			int type = Math.abs(piece);
			double scaled = delta * scales[type];
			int signed = (int) Math.round(Piece.isWhite(piece) ? scaled : -scaled);
			labels[index] = Format.formatSigned(signed);
		}
		return labels;
	}

	/**
	 * formatScore method.
	 *
	 * @param score parameter.
	 * @return return value.
	 */
	public static String formatScore(int score) {
		if (score > 0) {
			return "+" + score;
		}
		return String.valueOf(score);
	}

	/**
	 * pieceName method.
	 *
	 * @param piece parameter.
	 * @return return value.
	 */
	public static String pieceName(byte piece) {
		switch (piece) {
			case Piece.WHITE_KING:
				return "White King";
			case Piece.WHITE_QUEEN:
				return "White Queen";
			case Piece.WHITE_ROOK:
				return "White Rook";
			case Piece.WHITE_BISHOP:
				return "White Bishop";
			case Piece.WHITE_KNIGHT:
				return "White Knight";
			case Piece.WHITE_PAWN:
				return "White Pawn";
			case Piece.BLACK_KING:
				return "Black King";
			case Piece.BLACK_QUEEN:
				return "Black Queen";
			case Piece.BLACK_ROOK:
				return "Black Rook";
			case Piece.BLACK_BISHOP:
				return "Black Bishop";
			case Piece.BLACK_KNIGHT:
				return "Black Knight";
			case Piece.BLACK_PAWN:
				return "Black Pawn";
			default:
				return "Empty";
		}
	}
}
