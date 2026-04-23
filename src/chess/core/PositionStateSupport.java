package chess.core;

/**
 * Shared state-copy and board-mutation helpers for {@link Position}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class PositionStateSupport {

	/**
	 * Utility class; prevent instantiation.
	 */
	private PositionStateSupport() {
		// utility
	}

	/**
	 * Copies all mutable position state into an already-initialized target.
	 *
	 * @param source source position
	 * @param target target position
	 */
	static void copyState(Position source, Position target) {
		System.arraycopy(source.pieces, 0, target.pieces, 0, target.pieces.length);
		System.arraycopy(source.board, 0, target.board, 0, target.board.length);
		target.whiteOccupancy = source.whiteOccupancy;
		target.blackOccupancy = source.blackOccupancy;
		target.occupancy = source.occupancy;
		target.whiteKingSquare = source.whiteKingSquare;
		target.blackKingSquare = source.blackKingSquare;
		target.whiteToMove = source.whiteToMove;
		target.castlingRights = source.castlingRights;
		target.enPassantSquare = source.enPassantSquare;
		target.halfMoveClock = source.halfMoveClock;
		target.fullMoveNumber = source.fullMoveNumber;
		target.chess960Castling = source.chess960Castling;
		target.whiteKingsideRookSquare = source.whiteKingsideRookSquare;
		target.whiteQueensideRookSquare = source.whiteQueensideRookSquare;
		target.blackKingsideRookSquare = source.blackKingsideRookSquare;
		target.blackQueensideRookSquare = source.blackQueensideRookSquare;
	}

	/**
	 * Places one piece on a square and updates all cached occupancy data.
	 *
	 * @param position target position
	 * @param piece piece index
	 * @param square square index
	 */
	static void setPiece(Position position, int piece, int square) {
		long mask = 1L << square;
		position.pieces[piece] |= mask;
		position.board[square] = (byte) piece;
		if (piece < Position.BLACK_PAWN) {
			position.whiteOccupancy |= mask;
		} else {
			position.blackOccupancy |= mask;
		}
		position.occupancy |= mask;
		if (piece == Position.WHITE_KING) {
			position.whiteKingSquare = (byte) square;
		} else if (piece == Position.BLACK_KING) {
			position.blackKingSquare = (byte) square;
		}
	}

	/**
	 * Removes one piece from a square and updates all cached occupancy data.
	 *
	 * @param position target position
	 * @param piece piece index
	 * @param square square index
	 */
	static void clearPiece(Position position, int piece, int square) {
		long mask = 1L << square;
		position.pieces[piece] &= ~mask;
		position.board[square] = -1;
		if (piece < Position.BLACK_PAWN) {
			position.whiteOccupancy &= ~mask;
		} else {
			position.blackOccupancy &= ~mask;
		}
		position.occupancy &= ~mask;
		if (piece == Position.WHITE_KING) {
			position.whiteKingSquare = Field.NO_SQUARE;
		} else if (piece == Position.BLACK_KING) {
			position.blackKingSquare = Field.NO_SQUARE;
		}
	}

	/**
	 * Moves one piece between two squares.
	 *
	 * @param position target position
	 * @param piece piece index
	 * @param from origin square
	 * @param to target square
	 */
	static void movePiece(Position position, int piece, int from, int to) {
		clearPiece(position, piece, from);
		setPiece(position, piece, to);
	}

	/**
	 * Resolves the piece index to place on the destination square after a move.
	 *
	 * @param moving moving piece index
	 * @param promotion promotion code
	 * @return piece index to place
	 */
	static int promotionPieceIndex(int moving, int promotion) {
		if (promotion == 0 || (moving != Position.WHITE_PAWN && moving != Position.BLACK_PAWN)) {
			return moving;
		}
		boolean white = moving == Position.WHITE_PAWN;
		return switch (promotion) {
			case Position.PROMOTION_KNIGHT -> white ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT;
			case Position.PROMOTION_BISHOP -> white ? Position.WHITE_BISHOP : Position.BLACK_BISHOP;
			case Position.PROMOTION_ROOK -> white ? Position.WHITE_ROOK : Position.BLACK_ROOK;
			case Position.PROMOTION_QUEEN -> white ? Position.WHITE_QUEEN : Position.BLACK_QUEEN;
			default -> moving;
		};
	}
}
