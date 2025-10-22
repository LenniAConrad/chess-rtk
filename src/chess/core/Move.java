package chess.core;

/**
 * Compact 16-bit encoding of a UCI move stored in a {@code short}.
 *
 * Encoding: bits {@code 5..0}=from (6), {@code 11..6}=to (6),
 * {@code 14..12}=promotion (3), bit 15 unused (0).
 * Promotion codes: 0=none, 1=knight (n), 2=bishop (b), 3=rook (r), 4=queen (q).
 *
 * Examples:
 * {@code Move.of(Field.toIndex('e','2'), Field.toIndex('e','4')) -> "e2e4"}
 * {@code Move.of(a7, a8, (byte)4) -> "a7a8q"}
 *
 * Notes: value semantics (plain short), no extra flags (castle/en passant
 * inferred against a position),
 * allocation-free except {@link #toString(short)} and {@link #parse(String)}.
 * Constants like {@code FROM_SHIFT}
 * are kept for clarity; {@code 0} is intentional and gets constant-folded.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Move {

	/**
	 * Used for representing a sentinel value that denotes the absence of a move.
	 * The bit pattern is {@code 0xFFFF}, which is invalid under this encoding.
	 */
	public static final short NO_MOVE = (short) 0xFFFF;

	/**
	 * Used for indicating promotion to a queen.
	 */
	protected static final byte PROMOTION_QUEEN = 4;

	/**
	 * Used for indicating promotion to a rook.
	 */
	protected static final byte PROMOTION_ROOK = 3;

	/**
	 * Used for indicating promotion to a bishop.
	 */
	protected static final byte PROMOTION_BISHOP = 2;

	/**
	 * Used for indicating promotion to a knight.
	 */
	protected static final byte PROMOTION_KNIGHT = 1;

	/**
	 * Used for indicating that no promotion occurs.
	 */
	protected static final byte NO_PROMOTION = 0;

	/**
	 * Used for mapping the 3-bit promotion code (0..4) to its UCI character.
	 * Index 0 = no promotion (NUL), 1='n', 2='b', 3='r', 4='q'.
	 */
	private static final char[] PROMOTION_CHAR = { '\u0000', 'n', 'b', 'r', 'q' };

	/**
	 * Used for defining the bit position of the {@code from} field.
	 * The {@code from} index occupies bits 5..0; shift is 0 (no shift).
	 */
	private static final int FROM_SHIFT = 0;

	/**
	 * Used for defining the bit position of the {@code to} field.
	 * The {@code to} index occupies bits 11..6; shift is 6.
	 */
	private static final int TO_SHIFT = 6;

	/**
	 * Used for defining the bit position of the {@code promotion} field.
	 * The promotion code occupies bits 14..12; shift is 12.
	 */
	private static final int PROMO_SHIFT = 12;

	/**
	 * Used for masking 6-bit fields (values 0..63), e.g., {@code from} and
	 * {@code to}.
	 */
	private static final int SIX_BIT_MASK = 0x3F;

	/**
	 * Used for masking 3-bit fields (values 0..7), e.g., {@code promotion}.
	 */
	private static final int THREE_BIT_MASK = 0x07;

	/**
	 * Used for preventing instantiation of this utility class.
	 */
	private Move() {
		// Used for preventing instantiation.
	}

	/**
	 * Used for creating a compact move value from indices.
	 *
	 * @param from the origin square index (0..63)
	 * @param to   the destination square index (0..63)
	 * @return the encoded move as a {@code short}
	 * @throws IllegalArgumentException if indices are invalid
	 */
	public static short of(byte from, byte to) {
		return of(from, to, NO_PROMOTION);
	}

	/**
	 * Used for creating a compact move value from indices and promotion.
	 *
	 * @param from      the origin square index (0..63)
	 * @param to        the destination square index (0..63)
	 * @param promotion the promotion type (0..4)
	 * @return the encoded move as a {@code short}
	 * @throws IllegalArgumentException if any component is invalid
	 */
	public static short of(byte from, byte to, byte promotion) {
		if (!isValid(from, to, promotion)) {
			throw new IllegalArgumentException(
					"Illegal UCI move: from '" + from + "', to '" + to + "', promotion '" + promotion + "'");
		}
		int v = ((promotion & THREE_BIT_MASK) << PROMO_SHIFT)
				| ((to & SIX_BIT_MASK) << TO_SHIFT)
				| ((from & SIX_BIT_MASK) << FROM_SHIFT);
		return (short) v;
	}

	/**
	 * Used for constructing a compact move from a UCI string such as {@code e2e4}
	 * or {@code a7a8q}.
	 *
	 * @param string the UCI move text
	 * @return the encoded move as a {@code short}
	 * @throws IllegalArgumentException if the text is malformed or out of range
	 */
	public static short parse(String string) {
		if (!isMove(string)) {
			throw new IllegalArgumentException("Invalid move format: '" + string + "'");
		}
		byte promo = NO_PROMOTION;
		if (string.length() == 5) {
			switch (string.charAt(4)) {
				case 'q': {
					promo = PROMOTION_QUEEN;
					break;
				}
				case 'r': {
					promo = PROMOTION_ROOK;
					break;
				}
				case 'b': {
					promo = PROMOTION_BISHOP;
					break;
				}
				case 'n': {
					promo = PROMOTION_KNIGHT;
					break;
				}
				default: {
					break;
				}
			}
		}
		byte to = Field.toIndex(string.charAt(2), string.charAt(3));
		byte from = Field.toIndex(string.charAt(0), string.charAt(1));
		return of(from, to, promo);
	}

	/**
	 * Used for converting a compact move to a UCI string.
	 *
	 * @param move the encoded move
	 * @return the UCI string (e.g., {@code e2e4}, {@code a7a8q})
	 */
	public static String toString(short move) {
		if (move == NO_MOVE) {
			return "0000";
		}
		byte from = getFromIndex(move);
		byte to = getToIndex(move);
		byte promotion = getPromotion(move);
		if (promotion == NO_PROMOTION) {
			return new String(new char[] {
					(char) ('a' + Field.getX(from)),
					(char) ('1' + Field.getY(from)),
					(char) ('a' + Field.getX(to)),
					(char) ('1' + Field.getY(to))
			});
		}
		return new String(new char[] {
				(char) ('a' + Field.getX(from)),
				(char) ('1' + Field.getY(from)),
				(char) ('a' + Field.getX(to)),
				(char) ('1' + Field.getY(to)),
				PROMOTION_CHAR[promotion]
		});
	}

	/**
	 * Used for retrieving the origin square index from a compact move.
	 *
	 * @param move the encoded move
	 * @return the origin index (0..63)
	 */
	public static byte getFromIndex(short move) {
		return (byte) ((move >>> FROM_SHIFT) & SIX_BIT_MASK);
	}

	/**
	 * Used for retrieving the destination square index from a compact move.
	 *
	 * @param move the encoded move
	 * @return the destination index (0..63)
	 */
	public static byte getToIndex(short move) {
		return (byte) ((move >>> TO_SHIFT) & SIX_BIT_MASK);
	}

	/**
	 * Used for retrieving the promotion value from a compact move.
	 *
	 * @param move the encoded move
	 * @return the promotion value (0..4)
	 */
	public static byte getPromotion(short move) {
		return (byte) ((move >>> PROMO_SHIFT) & THREE_BIT_MASK);
	}

	/**
	 * Used for calculating the X-coordinate (file) of the origin square.
	 *
	 * @param move the encoded move
	 * @return 0-based X-coordinate
	 */
	public static int getFromX(short move) {
		return Field.getX(getFromIndex(move));
	}

	/**
	 * Used for calculating the Y-coordinate (rank) of the origin square.
	 *
	 * @param move the encoded move
	 * @return 0-based Y-coordinate
	 */
	public static int getFromY(short move) {
		return Field.getY(getFromIndex(move));
	}

	/**
	 * Used for calculating the X-coordinate (file) of the destination square.
	 *
	 * @param move the encoded move
	 * @return 0-based X-coordinate
	 */
	public static int getToX(short move) {
		return Field.getX(getToIndex(move));
	}

	/**
	 * Used for calculating the Y-coordinate (rank) of the destination square.
	 *
	 * @param move the encoded move
	 * @return 0-based Y-coordinate
	 */
	public static int getToY(short move) {
		return Field.getY(getToIndex(move));
	}

	/**
	 * Used for retrieving the inverted X-coordinate of the origin square.
	 *
	 * @param move the encoded move
	 * @return inverted 0-based X-coordinate
	 */
	public static int getFromXInverted(short move) {
		return Field.getXInverted(getFromIndex(move));
	}

	/**
	 * Used for retrieving the inverted Y-coordinate of the origin square.
	 *
	 * @param move the encoded move
	 * @return inverted 0-based Y-coordinate
	 */
	public static int getFromYInverted(short move) {
		return Field.getYInverted(getFromIndex(move));
	}

	/**
	 * Used for retrieving the inverted X-coordinate of the destination square.
	 *
	 * @param move the encoded move
	 * @return inverted 0-based X-coordinate
	 */
	public static int getToXInverted(short move) {
		return Field.getXInverted(getToIndex(move));
	}

	/**
	 * Used for retrieving the inverted Y-coordinate of the destination square.
	 *
	 * @param move the encoded move
	 * @return inverted 0-based Y-coordinate
	 */
	public static int getToYInverted(short move) {
		return Field.getYInverted(getToIndex(move));
	}

	/**
	 * Used for checking if this move is a promotion.
	 *
	 * @param move the encoded move
	 * @return {@code true} if promotion is non-zero
	 */
	public static boolean isPromotion(short move) {
		return getPromotion(move) != NO_PROMOTION;
	}

	/**
	 * Used for checking if this move is an underpromotion (non-queen promotion).
	 *
	 * @param move the encoded move
	 * @return {@code true} if promotes to piece other than queen
	 */
	public static boolean isUnderPromotion(short move) {
		byte p = getPromotion(move);
		return p != NO_PROMOTION && p != PROMOTION_QUEEN;
	}

	/**
	 * Used for comparing two compact moves for ordering by {@code from}, then
	 * {@code to}, then {@code promotion}.
	 *
	 * @param a the first encoded move
	 * @param b the second encoded move
	 * @return negative, zero, or positive per lexicographic order
	 */
	public static int compare(short a, short b) {
		int cmp = Byte.compare(getFromIndex(a), getFromIndex(b));
		if (cmp != 0) {
			return cmp;
		}
		cmp = Byte.compare(getToIndex(a), getToIndex(b));
		if (cmp != 0) {
			return cmp;
		}
		return Byte.compare(getPromotion(a), getPromotion(b));
	}

	/**
	 * Used for checking if a move is a kingside castle based on the
	 * {@code Position}.
	 *
	 * @param position the current board position
	 * @param move     the encoded move
	 * @return {@code true} if the move is a kingside castle
	 */
	protected static boolean isKingsideCastle(Position position, short move) {
		byte from = getFromIndex(move);
		byte to = getToIndex(move);
		return (to == position.whiteKingside && position.board[from] == Piece.WHITE_KING)
				|| (to == position.blackKingside && position.board[from] == Piece.BLACK_KING);
	}

	/**
	 * Used for checking if a move is a queenside castle based on the
	 * {@code Position}.
	 *
	 * @param position the current board position
	 * @param move     the encoded move
	 * @return {@code true} if the move is a queenside castle
	 */
	protected static boolean isQueensideCastle(Position position, short move) {
		byte from = getFromIndex(move);
		byte to = getToIndex(move);
		return (to == position.whiteQueenside && position.board[from] == Piece.WHITE_KING)
				|| (to == position.blackQueenside && position.board[from] == Piece.BLACK_KING);
	}

	/**
	 * Used for checking if a move is any type of castling.
	 *
	 * @param position the current board position
	 * @param move     the encoded move
	 * @return {@code true} if the move is a kingside or queenside castle
	 */
	protected static boolean isCastle(Position position, short move) {
		return isKingsideCastle(position, move) || isQueensideCastle(position, move);
	}

	/**
	 * Used for checking if a move is an en passant capture.
	 *
	 * @param position the current board position
	 * @param move     the encoded move
	 * @return {@code true} if the move is an en passant capture
	 */
	protected static boolean isEnPassantCapture(Position position, short move) {
		byte from = getFromIndex(move);
		byte to = getToIndex(move);
		return Piece.isPawn(position.board[from]) && to == position.enPassant;
	}

	/**
	 * Used for figuring out the Euclidean distance a piece travels in a move.
	 *
	 * @param move the encoded move
	 * @return the distance moved
	 */
	public static double getDistance(short move) {
		return Math.hypot(getHorizontalDistance(move), getVerticalDistance(move));
	}

	/**
	 * Used for returning the horizontal (file) distance between from and to.
	 *
	 * @param move the encoded move
	 * @return the absolute horizontal distance
	 */
	public static int getHorizontalDistance(short move) {
		byte from = getFromIndex(move);
		byte to = getToIndex(move);
		return Math.abs(from % 8 - to % 8);
	}

	/**
	 * Used for returning the vertical (rank) distance between from and to.
	 *
	 * @param move the encoded move
	 * @return the absolute vertical distance
	 */
	public static int getVerticalDistance(short move) {
		byte from = getFromIndex(move);
		byte to = getToIndex(move);
		return Math.abs(from / 8 - to / 8);
	}

	/**
	 * Used for checking whether the given string looks like a UCI move.
	 * Avoids regex backtracking and allocations by validating character ranges.
	 *
	 * @param string the candidate string
	 * @return {@code true} if the string is a valid UCI move form
	 */
	public static boolean isMove(String string) {
		if (string == null) {
			return false;
		}
		int len = string.length();
		if (len != 4 && len != 5) {
			return false;
		}
		char fFile = string.charAt(0);
		char fRank = string.charAt(1);
		char tFile = string.charAt(2);
		char tRank = string.charAt(3);
		if (fFile < 'a' || fFile > 'h' || tFile < 'a' || tFile > 'h') {
			return false;
		}
		if (fRank < '1' || fRank > '8' || tRank < '1' || tRank > '8') {
			return false;
		}
		if (len == 5) {
			char p = string.charAt(4);
			return p == 'q' || p == 'r' || p == 'b' || p == 'n';
		}
		return true;
	}

	/**
	 * Used for validating indices and promotion range.
	 *
	 * @param from      the origin square index
	 * @param to        the destination square index
	 * @param promotion the promotion code (0..4)
	 * @return {@code true} if all parts are within valid ranges
	 */
	public static boolean isValid(byte from, byte to, byte promotion) {
		boolean fromValid = from >= Field.A8 && from <= Field.H1;
		boolean toValid = to >= Field.A8 && to <= Field.H1;
		boolean promoValid = promotion >= NO_PROMOTION && promotion <= PROMOTION_QUEEN;
		return fromValid && toValid && promoValid;
	}

	/**
	 * Used for checking whether two moves are bitwise equal.
	 *
	 * @param a the first move
	 * @param b the second move
	 * @return {@code true} when both encoded values are identical
	 */
	public static boolean equals(short a, short b) {
		return a == b;
	}

	/**
	 * Used for getting a stable int hash for an encoded move.
	 *
	 * @param move the encoded move
	 * @return an unsigned 16-bit value as an {@code int}
	 */
	public static int hash(short move) {
		return move & 0xFFFF;
	}

	/**
	 * Used for reversing a move by swapping origin and destination.
	 *
	 * @param move the encoded move
	 * @return a new move with from/to swapped, promotion preserved
	 */
	public static short reverse(short move) {
		return of(getToIndex(move), getFromIndex(move), getPromotion(move));
	}

}
