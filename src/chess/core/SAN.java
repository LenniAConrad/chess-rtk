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
	 * Converts a move into Standard Algebraic Notation (SAN) within the supplied context.
	 *
	 * <p>
	 * Handles castling, pawn promotions, captures with file disambiguation, and
	 * check/checkmate suffixes determined by {@link #algebraicEnding(Position, short)}.
	 * </p>
	 *
	 * @param context the current position before the move
	 * @param move move to describe
	 * @return SAN string describing the move
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
	 * Generates the check/mate suffix for a SAN string.
	 *
	 * <p>
	 * A move results in {@code "+"} when the resulting position leaves the opponent
	 * in check, and {@code "#"} when no legal moves remain (checkmate).
	 * </p>
	 *
	 * @param context the position before the move
	 * @param move move being played
	 * @return {@code "+"} for check, {@code "#"} for mate, or {@code ""} otherwise
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
	 * Maps a piece code to the algebraic symbol used in SAN.
	 *
	 * <p>
	 * Returns uppercase letters for minor/major pieces and an empty string for
	 * pawns, whose origin file is printed explicitly when capturing.
	 * </p>
	 *
	 * @param piece the piece code
	 * @return single-character notation or {@code ""} for pawns
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
	 * Builds the disambiguation string when multiple pieces of the same type can reach the destination.
	 *
	 * <p>
	 * Checks every legal move in the position and returns either the moving piece's file,
	 * rank, or both (ordered) just enough to make the SAN string unambiguous.
	 * </p>
	 *
	 * @param context the current position
	 * @param move    the move that requires disambiguation
	 * @param piece   the moving piece code
	 * @return file and/or rank string or {@code ""} when uniquely identified
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
	 * Builds the promotion suffix added to a SAN move.
	 *
	 * @param promotion promotion piece code from the move
	 * @return string like {@code =Q} when promotion occurs, otherwise {@code ""}
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
	 * Parses a SAN string back into the corresponding legal move in the
	 * supplied position.
	 *
	 * <p>
	 * Accepts strings with trailing annotations such as {@code !} or {@code ?}
	 * and matches them against the generated SAN list to ensure legality.
	 * </p>
	 *
	 * @param context   the current position
	 * @param algebraic the SAN string of the move (may include annotations like {@code !} or {@code ?})
	 * @return the matching {@link Move}
	 * @throws IllegalArgumentException if no matching legal move is found
	 */
	public static short fromAlgebraic(Position context, String algebraic) throws IllegalArgumentException {
		String san = normalizeMoveToken(algebraic).replaceAll("[!?]+", "");

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
	 * Applies a SAN move line to a starting position.
	 *
	 * <p>
	 * The input may contain PGN-style comments, move numbers, NAGs, variations, and
	 * result markers accepted by {@link #cleanMoveString(String)}. Parsing stops at
	 * the first invalid token and returns the position reached by all valid prefix
	 * moves together with the offending token.
	 * </p>
	 *
	 * @param start starting position
	 * @param movetext raw SAN/PGN movetext
	 * @return parsed line result
	 * @throws IllegalArgumentException if {@code start} is null
	 */
	public static PlayedLine playLine(Position start, String movetext) {
		if (start == null) {
			throw new IllegalArgumentException("start position cannot be null");
		}
		Position initial = start.copyOf();
		Position current = start.copyOf();
		String cleaned = cleanMoveString(movetext);
		if (cleaned.isBlank()) {
			return new PlayedLine(initial, current, Move.NO_MOVE, "", 0, true, 0, false, "");
		}

		short lastMove = Move.NO_MOVE;
		String lastSan = "";
		int lastMoveNumber = 0;
		boolean lastMoveWasWhite = true;
		int plies = 0;
		String[] tokens = cleaned.split("\\s+");
		for (String token : tokens) {
			if (token == null || token.isBlank()) {
				continue;
			}
			try {
				short move = fromAlgebraic(current, token);
				lastMove = move;
				lastSan = token;
				lastMoveNumber = current.getFullMove();
				lastMoveWasWhite = current.isWhiteTurn();
				current.play(move);
				plies++;
			} catch (IllegalArgumentException ex) {
				return new PlayedLine(initial, current, lastMove, lastSan, lastMoveNumber, lastMoveWasWhite, plies,
						false, token);
			}
		}
		return new PlayedLine(initial, current, lastMove, lastSan, lastMoveNumber, lastMoveWasWhite, plies, true, "");
	}

	/**
	 * Returns the last SAN-like move token from raw movetext.
	 *
	 * <p>
	 * Comments, move numbers, NAGs, variations, and result markers are ignored in
	 * the same way as {@link #cleanMoveString(String)}.
	 * </p>
	 *
	 * @param movetext raw SAN/PGN movetext
	 * @return last move token, or an empty string when none is present
	 */
	public static String lastMoveToken(String movetext) {
		String cleaned = cleanMoveString(movetext);
		if (cleaned.isBlank()) {
			return "";
		}
		String[] parts = cleaned.split("\\s+");
		return parts.length == 0 ? "" : parts[parts.length - 1];
	}

	/**
	 * Normalizes one SAN move token before matching it against legal moves.
	 *
	 * <p>
	 * This accepts common zero and lowercase-o castling spellings while preserving
	 * suffixes such as {@code +}, {@code #}, {@code !}, and {@code ?}.
	 * </p>
	 *
	 * @param token raw move token
	 * @return normalized SAN token
	 */
	public static String normalizeMoveToken(String token) {
		if (token == null) {
			return "";
		}
		String trimmed = token.trim();
		if (trimmed.startsWith("0-0-0")) {
			return CASTLING_QUEENSIDE + trimmed.substring(5);
		}
		if (trimmed.startsWith("0-0")) {
			return CASTLING_KINGSIDE + trimmed.substring(3);
		}
		if (trimmed.startsWith("o-o-o") || trimmed.startsWith("O-O-O")) {
			return CASTLING_QUEENSIDE + trimmed.substring(5);
		}
		if (trimmed.startsWith("o-o") || trimmed.startsWith("O-O")) {
			return CASTLING_KINGSIDE + trimmed.substring(3);
		}
		return trimmed;
	}

	/**
	 * Cleans raw PGN move text by removing comments, variations, NAGs,
	 * move numbers, and result tokens, leaving only SAN move tokens separated
	 * by single spaces.
	 *
	 * <p>
	 * Example:
	 * </p>
	 * <pre>
	 * String before = "1. e4 e5 (2. Nc3) 2... Nc6 {[%clk 2:34:56]} 3. Bb5 a6 $5 4. Ba4 Nf6 1-0";
	 * String after = cleanMoveString(before); // "e4 e5 Nc6 Bb5 a6 Ba4 Nf6"
	 * </pre>
	 *
	 * @param movetext the raw PGN move string (including comments, variations, clocks, etc.)
	 * @return a cleaned string containing only SAN moves separated by spaces; empty when none found
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
	 * Cleans PGN move text while preserving variation parentheses.
	 *
	 * <p>
	 * Block/line comments, NAGs, move numbers, and result tokens are removed, but
	 * variation groups remain (with spacing normalized) so downstream parsers can
	 * still detect sideline content.
	 * </p>
	 *
	 * <pre>
	 * String before = "1. e4 e5 (2. Nc3) 2... Nc6 {[%clk 2:34:56]} 3. Bb5 a6";
	 * String after = cleanMoveStringKeepVariationsRegex(before);
	 * // result: "e4 e5 (Nc3) Nc6 Bb5 a6"
	 * </pre>
	 *
	 * @param movetext the raw PGN move string (including comments, variations, clocks, etc.)
	 * @return a cleaned string with SAN moves and variations preserved, or empty if input is null/empty
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

	/**
	 * Result of applying a SAN move line.
	 *
	 * @since 2026
	 * @author Lennart A. Conrad
	 */
	public static final class PlayedLine {

		/**
		 * Original starting position.
		 */
		private final Position start;

		/**
		 * Position reached by the valid prefix of the line.
		 */
		private final Position result;

		/**
		 * Last successfully parsed move.
		 */
		private final short lastMove;

		/**
		 * Last successfully parsed SAN token.
		 */
		private final String lastSan;

		/**
		 * Full-move number before the last successfully parsed move.
		 */
		private final int lastMoveNumber;

		/**
		 * Whether the last successfully parsed move was played by White.
		 */
		private final boolean lastMoveWasWhite;

		/**
		 * Number of plies successfully parsed.
		 */
		private final int pliesPlayed;

		/**
		 * Whether every cleaned token parsed successfully.
		 */
		private final boolean parsed;

		/**
		 * First invalid token, or an empty string when the line parsed completely.
		 */
		private final String invalidToken;

		/**
		 * Creates one parsed-line result.
		 *
		 * @param start starting position
		 * @param result resulting position
		 * @param lastMove last move
		 * @param lastSan last SAN token
		 * @param lastMoveNumber last move number
		 * @param lastMoveWasWhite true when the last move was by White
		 * @param pliesPlayed number of plies played
		 * @param parsed true when the entire line parsed
		 * @param invalidToken first invalid token
		 */
		private PlayedLine(Position start, Position result, short lastMove, String lastSan, int lastMoveNumber,
				boolean lastMoveWasWhite, int pliesPlayed, boolean parsed, String invalidToken) {
			this.start = start.copyOf();
			this.result = result.copyOf();
			this.lastMove = lastMove;
			this.lastSan = lastSan == null ? "" : lastSan;
			this.lastMoveNumber = Math.max(0, lastMoveNumber);
			this.lastMoveWasWhite = lastMoveWasWhite;
			this.pliesPlayed = Math.max(0, pliesPlayed);
			this.parsed = parsed;
			this.invalidToken = invalidToken == null ? "" : invalidToken;
		}

		/**
		 * Returns the original starting position.
		 *
		 * @return defensive position copy
		 */
		public Position getStart() {
			return start.copyOf();
		}

		/**
		 * Returns the position reached by the valid prefix of the line.
		 *
		 * @return defensive position copy
		 */
		public Position getResult() {
			return result.copyOf();
		}

		/**
		 * Returns the last successfully parsed move.
		 *
		 * @return move, or {@link Move#NO_MOVE}
		 */
		public short getLastMove() {
			return lastMove;
		}

		/**
		 * Returns whether at least one move parsed successfully.
		 *
		 * @return true when a last move is available
		 */
		public boolean hasLastMove() {
			return lastMove != Move.NO_MOVE;
		}

		/**
		 * Returns the last successfully parsed SAN token.
		 *
		 * @return SAN token, or empty string
		 */
		public String getLastSan() {
			return lastSan;
		}

		/**
		 * Returns the full-move number before the last parsed move.
		 *
		 * @return full-move number, or zero when no move parsed
		 */
		public int getLastMoveNumber() {
			return lastMoveNumber;
		}

		/**
		 * Returns whether the last parsed move was played by White.
		 *
		 * @return true for White, false for Black
		 */
		public boolean isLastMoveByWhite() {
			return lastMoveWasWhite;
		}

		/**
		 * Returns the last SAN token with a move-number prefix.
		 *
		 * @return move-numbered SAN, or empty string when no move parsed
		 */
		public String lastSanWithMoveNumber() {
			if (!hasLastMove() || lastSan.isBlank() || lastMoveNumber <= 0) {
				return "";
			}
			return lastMoveNumber + (lastMoveWasWhite ? ". " : "... ") + lastSan;
		}

		/**
		 * Returns the number of successfully parsed plies.
		 *
		 * @return ply count
		 */
		public int getPliesPlayed() {
			return pliesPlayed;
		}

		/**
		 * Returns whether the whole non-empty line parsed successfully.
		 *
		 * @return true when every token parsed
		 */
		public boolean isParsed() {
			return parsed;
		}

		/**
		 * Returns the first invalid token.
		 *
		 * @return invalid token, or empty string
		 */
		public String getInvalidToken() {
			return invalidToken;
		}
	}

}
