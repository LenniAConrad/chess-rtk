package chess.core;

/**
 * Used for conversion between moves and Standard Algebraic Notation (SAN).
 * <p>
 * This class supports:
 * <ul>
 * <li>Generating SAN from a given {@link Position} and {@link Move}, including
 * disambiguation, captures, promotions, and check/checkmate indicators.</li>
 * <li>Parsing a SAN string back into a legal {@link Move} in a given
 * position.</li>
 * </ul>
 * </p>
 * 
 * @author Lennart A. Conrad
 * @since 2025
 */
public class SAN {

	/**
	 * Used for notation of kingside castling in SAN.
	 */
	private static final String CASTLING_KINGSIDE = "O-O";

	/**
	 * Used for notation of queenside castling in SAN.
	 */
	private static final String CASTLING_QUEENSIDE = "O-O-O";

	/**
	 * Used for the result token representing a White win.
	 */
	public static final String RESULT_WHITE_WIN = "1-0";

	/**
	 * Used for the result token representing a Black win.
	 */
	public static final String RESULT_BLACK_WIN = "0-1";

	/**
	 * Used for the result token representing a draw.
	 */
	public static final String RESULT_DRAW = "1/2-1/2";

	/**
	 * Used for the result token representing an undefined or ongoing game.
	 */
	public static final String RESULT_UNKNOWN = "*";

	/**
	 * Private constructor to prevent instantiation of this class.
	 */
	private SAN() {
		// Prevent instantiation
	}

	/**
	 * Used for converting this move into algebraic notation.
	 *
	 * @param context the current position before the move
	 * @return the move in standard algebraic notation
	 */
	public static String toAlgebraic(Position context, short move) {
		String ending = algebraicEnding(context, move);

		if (Move.isKingsideCastle(context, move)) {
			return CASTLING_KINGSIDE + ending;
		}

		if (Move.isQueensideCastle(context, move)) {
			return CASTLING_QUEENSIDE + ending;
		}

		byte moveto = Move.getToIndex(move);
		byte movefrom = Move.getFromIndex(move);
		byte promotion = Move.getPromotion(move);

		boolean iscapture = context.isCapture(movefrom, moveto);
		byte piece = context.board[movefrom];
		String disambiguation = buildDisambiguation(context, move, piece);
		String capture = iscapture ? "x" : "";

		if (iscapture && Piece.isPawn(piece)) {
			capture = Field.getFile(movefrom) + capture;
		}

		String destination = Field.toString(moveto);
		String promotions = buildPromotionSuffix(promotion);

		return new StringBuilder(7)
				.append(getPieceSymbol(piece))
				.append(disambiguation)
				.append(capture)
				.append(destination)
				.append(promotions)
				.append(ending)
				.toString();
	}

	/**
	 * Used for determining check or checkmate suffix.
	 *
	 * @param context the position before the move
	 * @return "+" for check, "#" for checkmate, or "" otherwise
	 */
	private static String algebraicEnding(Position context, short move) {
		Position next = context.copyOf().play(move);
		if (!next.inCheck()) {
			return "";
		}

		return next.getMoves().size == 0
				? "#"
				: "+";
	}

	/**
	 * Used for mapping piece byte to algebraic symbol.
	 *
	 * @param piece the piece code
	 * @param index the index of the piece on the board (only used for pawns)
	 * @return single-character notation or "" for pawn
	 */
	private static String getPieceSymbol(byte piece) {
		switch (piece) {
			case Piece.BLACK_BISHOP, Piece.WHITE_BISHOP:
				return "B";
			case Piece.BLACK_KING, Piece.WHITE_KING:
				return "K";
			case Piece.BLACK_KNIGHT, Piece.WHITE_KNIGHT:
				return "N";
			case Piece.BLACK_QUEEN, Piece.WHITE_QUEEN:
				return "Q";
			case Piece.BLACK_ROOK, Piece.WHITE_ROOK:
				return "R";
			case Piece.BLACK_PAWN, Piece.WHITE_PAWN:
				return "";
			default:
				return "?";
		}
	}

	/**
	 * Used for generating file and rank disambiguation when needed.
	 *
	 * @param context the current position
	 * @param piece   the moving piece code
	 * @return file and/or rank string or "" if unambiguous
	 */
	private static String buildDisambiguation(Position context, short move, byte piece) {
		MoveList moves = context.getMoves();
		byte movefrom = Move.getFromIndex(move);
		byte moveto = Move.getToIndex(move);
		char file = Field.getFile(movefrom);
		char rank = Field.getRank(movefrom);
		String fileString = "";
		String rankString = "";

		if (!Piece.isPawn(piece)) {
			for (int i = 0; i < moves.size; i++) {
				short m = moves.moves[i];
				byte mfrom = Move.getFromIndex(m);
				byte mto = Move.getToIndex(m);
				if (movefrom != mfrom && moveto == mto && piece == context.board[mfrom]) {
					if (file != Field.getFile(mfrom)) {
						fileString = String.valueOf(file);
					} else if (rank != Field.getRank(mfrom)) {
						rankString = String.valueOf(rank);
					}
				}
			}
		}

		return fileString + rankString;
	}

	/**
	 * Used for building promotion suffix.
	 *
	 * @return promotion string or "" if none
	 */
	private static String buildPromotionSuffix(byte promotion) {
		switch (promotion) {
			case Move.PROMOTION_BISHOP:
				return "=B";
			case Move.PROMOTION_KNIGHT:
				return "=N";
			case Move.PROMOTION_QUEEN:
				return "=Q";
			case Move.PROMOTION_ROOK:
				return "=R";
			default:
				return "";
		}
	}

	/**
	 * Used for parsing a SAN string and returning the corresponding legal move.
	 *
	 * @param context   the current position
	 * @param algebraic the SAN string of the move (may include annotations like
	 *                  "!", "?")
	 * @return the matching move
	 * @throws IllegalArgumentException if no matching legal move is found
	 */
	public static short fromAlgebraic(Position context, String algebraic) throws IllegalArgumentException {
		String san = algebraic.replaceAll("[!?]+", "");

		MoveList moveList = context.getMoves();
		for (int i = 0; i < moveList.size; i++) {
			short move = moveList.moves[i];
			if (toAlgebraic(context, move).equals(san)) {
				return move;
			}
		}

		throw new IllegalArgumentException("Invalid SAN '" + algebraic + "' in position '" + context.toString() + "'");
	}

	/**
	 * Cleans a raw PGN move‑text string by stripping out all non‑SAN content,
	 * including:
	 * <ul>
	 * <li>Comments (anything between {@code {...}}, e.g., move clocks)</li>
	 * <li>Variations (anything between {@code (...)} )</li>
	 * <li>Numeric Annotation Glyphs (NAGs) like {@code $3}</li>
	 * <li>Move numbers (e.g. {@code 1.} or {@code 1...})</li>
	 * <li>Game result tokens ({@code 1-0}, {@code 0-1}, {@code 1/2-1/2},
	 * {@code *})</li>
	 * <li>Extra whitespace (collapsed to single spaces)</li>
	 * </ul>
	 *
	 * After cleaning, only the individual Standard Algebraic Notation (SAN) move
	 * tokens
	 * remain, separated by a single space.
	 *
	 * <p>
	 * <strong>Example:</strong>
	 * 
	 * <pre>
	 * // before cleaning:
	 * String before = "1. e4 e5 (2. Nc3) 2... Nc6 {[%clk 2:34:56]} 3. Bb5 a6 $5 4. Ba4 Nf6 1-0";
	 * // after cleaning:
	 * String after = cleanMoveString(before);
	 * // result: "e4 e5 Nc6 Bb5 a6 Ba4 Nf6"
	 * </pre>
	 *
	 * @param movetext the raw PGN move string (including comments, variations,
	 *                 clocks, etc.)
	 * @return a cleaned string containing only the SAN moves, each separated by a
	 *         single space;
	 *         or an empty string if no SAN moves are found
	 */
	public static String cleanMoveString(String movetext) {
		if (movetext == null || movetext.isEmpty()) {
			return "";
		}
		return movetext.replaceAll("\\{[^}]*\\}", " ").replaceAll("\\([^)]*\\)", " ").replaceAll("\\$\\d+", " ")
				.replaceAll("\\d+\\.(?:\\.\\.)?", " ").replaceAll("\\b1-0\\b|\\b0-1\\b|1/2-1/2|\\*", " ").trim()
				.replaceAll("\\s+", " ");
	}

	/**
	 * Used for cleaning a PGN move-text string while preserving move variations.
	 * <p>
	 * This method removes:
	 * <ul>
	 * <li>Block comments (e.g., {@code {[%clk 2:34:56]}})</li>
	 * <li>Line comments (e.g., {@code ; this is a comment})</li>
	 * <li>Numeric Annotation Glyphs (e.g., {@code $5})</li>
	 * <li>Move numbers (e.g., {@code 1.} or {@code 1...})</li>
	 * <li>Game result tokens (e.g., {@code 1-0}, {@code 0-1}, {@code 1/2-1/2},
	 * {@code *})</li>
	 * </ul>
	 * Variation groups enclosed in parentheses are retained and spaced
	 * appropriately.
	 *
	 * <p>
	 * <strong>Example:</strong>
	 * 
	 * <pre>
	 * String before = "1. e4 e5 (2. Nc3) 2... Nc6 {[%clk 2:34:56]} 3. Bb5 a6 $5 4. Ba4 Nf6 1-0";
	 * String after = cleanMoveStringKeepVariationsRegex(before);
	 * // result: "e4 e5 (Nc3) Nc6 Bb5 a6 Ba4 Nf6"
	 * </pre>
	 *
	 * @param movetext the raw PGN move string (including comments, variations,
	 *                 clocks, etc.)
	 * @return a cleaned string with SAN moves and variations, or empty if input is
	 *         null/empty
	 */
	public static String cleanMoveStringKeepVariationsRegex(String movetext) {
		if (movetext == null || movetext.isEmpty()) {
			return "";
		}
		// Remove: {…} comments, ; line comments, $N NAGs, move numbers, result tokens.
		movetext = movetext
				.replaceAll("\\{[^}]*\\}", " ") // block comments
				.replaceAll("(?m);[^\\r\\n]*", " ") // line comments
				.replaceAll("\\$\\d+", " ") // NAGs
				.replaceAll("\\d+\\.(?:\\.\\.)?", " ") // 12. or 12...
				.replaceAll("(?<!\\S)(?:1-0|0-1|1/2-1/2|\\*)(?!\\S)", " "); // results w/ token boundaries

		// Normalize spacing while preserving variations
		movetext = movetext.replaceAll("\\s*\\(\\s*", " ( "); // space before '('; none after
		movetext = movetext.replaceAll("\\s*\\)\\s*", " ) "); // space after ')'; none before
		movetext = movetext.replaceAll("\\s+", " ").trim();
		return movetext;
	}

}
