package chess.core;

/**
 * Used for defining byte codes for chess pieces and the empty square.
 * 
 * All constants must be unique to ensure correct identification and processing
 * of board state.
 */
public class Piece {

	/**
	 * Used for preventing instantiation of this utility class.
	 */
	private Piece() {
		// Prevent instantiation
	}

	/**
	 * Used for representing no piece.
	 * 
	 * @implNote this value must be 0
	 */
	public static final byte NONE = 0;

	/**
	 * Used for representing a pawn.
	 */
	public static final byte PAWN = 1;

	/**
	 * Used for representing a knight.
	 */
	public static final byte KNIGHT = 2;

	/**
	 * Used for representing a bishop.
	 */
	public static final byte BISHOP = 3;

	/**
	 * Used for representing a rook.
	 */
	public static final byte ROOK = 4;

	/**
	 * Used for representing a queen.
	 */
	public static final byte QUEEN = 5;

	/**
	 * Used for representing a king.
	 */
	public static final byte KING = 6;

	/**
	 * Used for representing the white king piece in a chess game.
	 */
	public static final byte WHITE_KING = 6;

	/**
	 * Used for representing the white queen piece in a chess game.
	 */
	public static final byte WHITE_QUEEN = 5;

	/**
	 * Used for representing the white rook piece in a chess game.
	 */
	public static final byte WHITE_ROOK = 4;

	/**
	 * Used for representing the white bishop piece in a chess game.
	 */
	public static final byte WHITE_BISHOP = 3;

	/**
	 * Used for representing the white knight piece in a chess game.
	 */
	public static final byte WHITE_KNIGHT = 2;

	/**
	 * Used for representing the white pawn piece in a chess game.
	 */
	public static final byte WHITE_PAWN = 1;

	/**
	 * Used for representing the black king piece in a chess game.
	 */
	public static final byte BLACK_KING = -6;

	/**
	 * Used for representing the black queen piece in a chess game.
	 */
	public static final byte BLACK_QUEEN = -5;

	/**
	 * Used for representing the black rook piece in a chess game.
	 */
	public static final byte BLACK_ROOK = -4;

	/**
	 * Used for representing the black bishop piece in a chess game.
	 */
	public static final byte BLACK_BISHOP = -3;

	/**
	 * Used for representing the black knight piece in a chess game.
	 */
	public static final byte BLACK_KNIGHT = -2;

	/**
	 * Used for representing the black pawn piece in a chess game.
	 */
	public static final byte BLACK_PAWN = -1;

	/**
	 * Used for representing an empty square on the chessboard.
	 * 
	 * @implNote this value must be 0
	 */
	public static final byte EMPTY = 0;

	/**
	 * Used for indicating the relative centipawn value of a king in material
	 * evaluation.
	 */
	public static final int VALUE_KING = 000;

	/**
	 * Used for indicating the relative centipawn value of a queen in material
	 * evaluation.
	 */
	public static final int VALUE_QUEEN = 900;

	/**
	 * Used for indicating the relative centipawn value of a rook in material
	 * evaluation.
	 */
	public static final int VALUE_ROOK = 500;

	/**
	 * Used for indicating the relative centipawn value of a bishop in material
	 * evaluation.
	 */
	public static final int VALUE_BISHOP = 300;

	/**
	 * Used for indicating the relative centipawn value of a knight in material
	 * evaluation.
	 */
	public static final int VALUE_KNIGHT = 300;

	/**
	 * Used for indicating the relative centipawn value of a pawn in material
	 * evaluation.
	 */
	public static final int VALUE_PAWN = 100;

	/**
	 * Used for indicating the relative centipawn value of an empty square.
	 */
	public static final int VALUE_EMPTY = 000;

	/**
	 * Used for indicating the Fischer centipawn value of a king.
	 */
	public static final int VALUE_FISCHER_KING = 0;

	/**
	 * Used for indicating the Fischer centipawn value of a queen.
	 */
	public static final int VALUE_FISCHER_QUEEN = 900;

	/**
	 * Used for indicating the Fischer centipawn value of a rook.
	 */
	public static final int VALUE_FISCHER_ROOK = 500;

	/**
	 * Used for indicating the Fischer centipawn value of a bishop.
	 */
	public static final int VALUE_FISCHER_BISHOP = 325;

	/**
	 * Used for indicating the Fischer centipawn value of a knight.
	 */
	public static final int VALUE_FISCHER_KNIGHT = 300;

	/**
	 * Used for indicating the Fischer centipawn value of a pawn.
	 */
	public static final int VALUE_FISCHER_PAWN = 100;

	/**
	 * Used for indicating the Fischer centipawn value of an empty square.
	 */
	public static final int VALUE_FISCHER_EMPTY = 0;

	/**
	 * Used for checking if a piece belongs to White.
	 *
	 * @param piece the piece byte value
	 * @return true if the piece is White; false otherwise
	 */
	public static boolean isWhitePiece(byte piece) {
		return piece > 0;
	}

	/**
	 * Used for checking if a piece belongs to Black.
	 *
	 * @param piece the piece byte value
	 * @return true if the piece is Black; false otherwise
	 */
	public static boolean isBlackPiece(byte piece) {
		return piece < 0;
	}

	/**
	 * Used for checking if a square contains any piece.
	 *
	 * @param piece the piece byte value
	 * @return true if the square is not empty; false otherwise
	 */
	public static boolean isPiece(byte piece) {
		return piece != Piece.EMPTY;
	}

	/**
	 * Used for checking if a square is empty.
	 *
	 * @param piece the piece byte value
	 * @return true if the square is empty; false otherwise
	 */
	public static boolean isEmpty(byte piece) {
		return piece == Piece.EMPTY;
	}

	/**
	 * Used for checking if a piece is a pawn.
	 *
	 * @param piece the piece byte value
	 * @return true if the piece is a pawn; false otherwise
	 */
	public static boolean isPawn(byte piece) {
		return piece == WHITE_PAWN || piece == BLACK_PAWN;
	}

	/**
	 * Used for checking if a piece is a knight.
	 *
	 * @param piece the piece byte value
	 * @return true if the piece is a knight; false otherwise
	 */
	public static boolean isKnight(byte piece) {
		return piece == WHITE_KNIGHT || piece == BLACK_KNIGHT;
	}

	/**
	 * Used for checking if a piece is a bishop.
	 *
	 * @param piece the piece byte value
	 * @return true if the piece is a bishop; false otherwise
	 */
	public static boolean isBishop(byte piece) {
		return piece == WHITE_BISHOP || piece == BLACK_BISHOP;
	}

	/**
	 * Used for checking if a piece is a rook.
	 *
	 * @param piece the piece byte value
	 * @return true if the piece is a rook; false otherwise
	 */
	public static boolean isRook(byte piece) {
		return piece == WHITE_ROOK || piece == BLACK_ROOK;
	}

	/**
	 * Used for checking if a piece is a queen.
	 *
	 * @param piece the piece byte value
	 * @return true if the piece is a queen; false otherwise
	 */
	public static boolean isQueen(byte piece) {
		return piece == WHITE_QUEEN || piece == BLACK_QUEEN;
	}

	/**
	 * Used for checking if a piece is a king.
	 *
	 * @param piece the piece byte value
	 * @return true if the piece is a king; false otherwise
	 */
	public static boolean isKing(byte piece) {
		return piece == WHITE_KING || piece == BLACK_KING;
	}

	/**
	 * Returns the material value of a given piece.
	 * This method is used to evaluate the material balance of a position in chess.
	 * 
	 * @param piece
	 * @return the material value of the piece
	 */
	public static int getValue(byte piece) {
		switch (piece) {
			case WHITE_KING:
				return VALUE_KING;
			case WHITE_QUEEN:
				return VALUE_QUEEN;
			case WHITE_ROOK:
				return VALUE_ROOK;
			case WHITE_BISHOP:
				return VALUE_BISHOP;
			case WHITE_KNIGHT:
				return VALUE_KNIGHT;
			case WHITE_PAWN:
				return VALUE_PAWN;
			case BLACK_KING:
				return VALUE_KING;
			case BLACK_QUEEN:
				return VALUE_QUEEN;
			case BLACK_ROOK:
				return VALUE_ROOK;
			case BLACK_BISHOP:
				return VALUE_BISHOP;
			case BLACK_KNIGHT:
				return VALUE_KNIGHT;
			case BLACK_PAWN:
				return VALUE_PAWN;
			case EMPTY:
				return VALUE_EMPTY;
			default:
				return VALUE_EMPTY; /* Can never trigger */
		}
	}

	/**
	 * Checks if the given piece is a white piece.
	 * 
	 * @param piece the piece to check
	 * @return true if the piece is white, false otherwise
	 */
	public static boolean isWhite(byte piece) {
		return piece > 0;
	}

	/**
	 * Checks if the given piece is a black piece.
	 * 
	 * @param piece the piece to check
	 * @return true if the piece is black, false otherwise
	 */
	public static boolean isBlack(byte piece) {
		return piece < 0;
	}

	public static char toLowerCaseChar(byte piece) {
		switch (piece) {
			case WHITE_KING:
				return 'K';
			case WHITE_QUEEN:
				return 'Q';
			case WHITE_ROOK:
				return 'R';
			case WHITE_BISHOP:
				return 'B';
			case WHITE_KNIGHT:
				return 'N';
			case WHITE_PAWN:
				return 'P';
			case BLACK_KING:
				return 'k';
			case BLACK_QUEEN:
				return 'q';
			case BLACK_ROOK:
				return 'r';
			case BLACK_BISHOP:
				return 'b';
			case BLACK_KNIGHT:
				return 'n';
			case BLACK_PAWN:
				return 'p';
			case EMPTY:
				return ' ';
			default:
				return ' '; /* Can never trigger */
		}
	}

	public static char toUpperCaseChar(byte piece) {
		switch (piece) {
			case WHITE_KING:
				return 'K';
			case WHITE_QUEEN:
				return 'Q';
			case WHITE_ROOK:
				return 'R';
			case WHITE_BISHOP:
				return 'B';
			case WHITE_KNIGHT:
				return 'N';
			case WHITE_PAWN:
				return 'P';
			case BLACK_KING:
				return 'K';
			case BLACK_QUEEN:
				return 'Q';
			case BLACK_ROOK:
				return 'R';
			case BLACK_BISHOP:
				return 'B';
			case BLACK_KNIGHT:
				return 'N';
			case BLACK_PAWN:
				return 'P';
			case EMPTY:
				return ' ';
			default:
				return ' '; /* Can never trigger */
		}
	}

	/**
	 * Used for returning the absolute material centipawn value of a given piece.
	 *
	 * @param piece the piece byte value
	 * @return the absolute material centipawn value based on standard valuations.
	 */
	public static int getMaterialValue(byte piece) {
		switch (piece) {
			case WHITE_KING:
				return VALUE_KING;
			case WHITE_QUEEN:
				return VALUE_QUEEN;
			case WHITE_ROOK:
				return VALUE_ROOK;
			case WHITE_BISHOP:
				return VALUE_BISHOP;
			case WHITE_KNIGHT:
				return VALUE_KNIGHT;
			case WHITE_PAWN:
				return VALUE_PAWN;
			case BLACK_KING:
				return VALUE_KING;
			case BLACK_QUEEN:
				return VALUE_QUEEN;
			case BLACK_ROOK:
				return VALUE_ROOK;
			case BLACK_BISHOP:
				return VALUE_BISHOP;
			case BLACK_KNIGHT:
				return VALUE_KNIGHT;
			case BLACK_PAWN:
				return VALUE_PAWN;
			case EMPTY:
				return VALUE_EMPTY;
			default:
				return VALUE_EMPTY;
		}
	}

	/**
	 * Used for returning the signed material centipawn value of a given piece.
	 *
	 * @param piece the piece byte value
	 * @return the signed material centipawn value (positive for White, negative for
	 *         Black).
	 */
	public static int getSignedMaterialValue(byte piece) {
		switch (piece) {
			case WHITE_KING:
				return VALUE_KING;
			case WHITE_QUEEN:
				return VALUE_QUEEN;
			case WHITE_ROOK:
				return VALUE_ROOK;
			case WHITE_BISHOP:
				return VALUE_BISHOP;
			case WHITE_KNIGHT:
				return VALUE_KNIGHT;
			case WHITE_PAWN:
				return VALUE_PAWN;
			case BLACK_KING:
				return -VALUE_KING;
			case BLACK_QUEEN:
				return -VALUE_QUEEN;
			case BLACK_ROOK:
				return -VALUE_ROOK;
			case BLACK_BISHOP:
				return -VALUE_BISHOP;
			case BLACK_KNIGHT:
				return -VALUE_KNIGHT;
			case BLACK_PAWN:
				return -VALUE_PAWN;
			case EMPTY:
				return VALUE_EMPTY;
			default:
				return VALUE_EMPTY;
		}
	}

	/**
	 * Used for returning the absolute Fischer centipawn value of a given piece.
	 *
	 * @param piece the piece byte value
	 * @return the absolute Fischer centipawn value based on Fischer valuations.
	 */
	public static int getFischerValue(byte piece) {
		switch (piece) {
			case WHITE_KING:
				return VALUE_FISCHER_KING;
			case WHITE_QUEEN:
				return VALUE_FISCHER_QUEEN;
			case WHITE_ROOK:
				return VALUE_FISCHER_ROOK;
			case WHITE_BISHOP:
				return VALUE_FISCHER_BISHOP;
			case WHITE_KNIGHT:
				return VALUE_FISCHER_KNIGHT;
			case WHITE_PAWN:
				return VALUE_FISCHER_PAWN;
			case BLACK_KING:
				return VALUE_FISCHER_KING;
			case BLACK_QUEEN:
				return VALUE_FISCHER_QUEEN;
			case BLACK_ROOK:
				return VALUE_FISCHER_ROOK;
			case BLACK_BISHOP:
				return VALUE_FISCHER_BISHOP;
			case BLACK_KNIGHT:
				return VALUE_FISCHER_KNIGHT;
			case BLACK_PAWN:
				return VALUE_FISCHER_PAWN;
			case EMPTY:
				return VALUE_FISCHER_EMPTY;
			default:
				return VALUE_FISCHER_EMPTY;
		}
	}

	/**
	 * Used for returning the signed Fischer centipawn value of a given piece.
	 *
	 * @param piece the piece byte value
	 * @return the signed Fischer centipawn value (positive for White, negative for
	 *         Black).
	 */
	public static int getSignedFischerValue(byte piece) {
		switch (piece) {
			case WHITE_KING:
				return VALUE_FISCHER_KING;
			case WHITE_QUEEN:
				return VALUE_FISCHER_QUEEN;
			case WHITE_ROOK:
				return VALUE_FISCHER_ROOK;
			case WHITE_BISHOP:
				return VALUE_FISCHER_BISHOP;
			case WHITE_KNIGHT:
				return VALUE_FISCHER_KNIGHT;
			case WHITE_PAWN:
				return VALUE_FISCHER_PAWN;
			case BLACK_KING:
				return -VALUE_FISCHER_KING;
			case BLACK_QUEEN:
				return -VALUE_FISCHER_QUEEN;
			case BLACK_ROOK:
				return -VALUE_FISCHER_ROOK;
			case BLACK_BISHOP:
				return -VALUE_FISCHER_BISHOP;
			case BLACK_KNIGHT:
				return -VALUE_FISCHER_KNIGHT;
			case BLACK_PAWN:
				return -VALUE_FISCHER_PAWN;
			case EMPTY:
				return VALUE_FISCHER_EMPTY;
			default:
				return VALUE_FISCHER_EMPTY;
		}
	}
}
