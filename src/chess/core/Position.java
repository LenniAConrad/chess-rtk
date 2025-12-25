package chess.core;

import java.util.Arrays;

/**
 * Used for representing a chess position, encapsulating board state, side to
 * move, castling
 * rights, en-passant target, half-move clock, and full-move number.
 *
 * <p>
 * The implementation of this class prioritizes correctness,
 * clarity, and long-term maintainability over raw performance. It deliberately
 * avoids bitboard or magic-bitboard techniques so the code remains
 * approachable,
 * robust, and easy to debug and extend.
 * </p>
 * 
 * <p>
 * This implementation supports both <strong>standard chess</strong> and
 * <strong>Chess960</strong>. Main capabilities:
 * </p>
 * <ul>
 * <li>Parse and validate FEN strings.</li>
 * <li>Generate and play all legal moves, including castling and
 * en&nbsp;passant.</li>
 * <li>Detect check, check-mate, attackers, and pin situations.</li>
 * <li>Serialize the current position back to FEN.</li>
 * <li>Counting material & pieces</li>
 * <li>Generating subpositions</li>
 * </ul>
 * 
 * <hr>
 * </hr>
 *
 * <h4>Example&nbsp;– FENs</h4>
 * <ul>
 * <li><strong>Starting Position</strong>:
 * rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w QKqk - 0 1</li>
 * <li><strong>Endgame Puzzle</strong>: 8/p1p3Q1/1p4r1/5qk1/5pp1/P7/1P5R/K7 w -
 * - 0 1</li>
 * <li><strong>Double Checkmate</strong>: 2rkr3/2p1p3/4N3/8/5K2/8/8/3R4 b - - 0
 * 1</li>
 * <li><strong>Standard Perft Testing</strong>:
 * r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10</li>
 * <li><strong>Chess960 Perft Testing</strong>:
 * bb3rkr/pq1p2pp/1p2pn2/2p2p2/2P2PnP/1P2PN2/PQBP1NP1/B4RKR w HFhf - 9 10</li>
 * </ul>
 * 
 * <hr>
 * </hr>
 * 
 * @since 2025 (since 2023 but heavily modified in 2025)
 * @author Lennart A. Conrad
 */
public class Position implements Comparable<Position> {

	/**
	 * All possible combinations of a standard Chess castling string
	 * 
	 * The allowed default castling order.
	 * <p>
	 * In the default castling system, the allowed order is given as a sequence of
	 * characters.
	 * Uppercase characters denote one side's castling rights, and lowercase denote
	 * the other side's.
	 * For example, in this default order, 'K' is considered the highest priority
	 * for kingside castling,
	 * while 'q' is considered the lowest priority for queenside castling.
	 * </p>
	 */
	private static final String[] STANDARD_CHESS_CASTLING_STRINGS = { "-", "K", "KQ", "KQk", "KQkq", "KQq", "Kk", "Kkq",
			"Kq", "Q", "Qk", "Qkq", "Qq", "k", "kq", "q" };

	/**
	 * All possible combinations of a Chess960 castling string
	 * 
	 * The allowed Chess960 castling order.
	 * <p>
	 * In Chess960, the allowed castling positions are specified in a descending
	 * order.
	 * The uppercase letters denote one side's castling positions, and the lowercase
	 * letters denote the other side's.
	 * For example, in this order, 'H' represents the highest-ranked allowed
	 * position and 'a' the lowest.
	 * </p>
	 */
	private static final String[] CHESS_960_CASTLING_STRINGS = { "-", "A", "Aa", "Ac", "Aca", "Ad", "Ada", "Ae", "Aea",
			"Af", "Afa", "Ag", "Aga", "Ah", "Aha", "B", "Bb", "Bd", "Bdb", "Be", "Beb", "Bf", "Bfb", "B6g", "Bgb", "Bh",
			"Bhb", "C", "CA", "CAa", "CAc", "CAca", "Ca", "Cc", "Cca", "Ce", "Cec", "Cf", "Cfc", "Cg", "Cgc", "Ch",
			"Chc", "D", "DA", "DAa", "DAd", "DAda", "DB", "DBb", "DBd", "DBdb", "Da", "Db", "Dd", "Dda", "Ddb", "Df",
			"Dfd", "Dg", "Dgd", "Dh", "Dhd", "E", "EA", "EAa", "EAe", "EAea", "EB", "EBb", "EBe", "EBeb", "EC", "ECc",
			"ECe", "ECec", "Ea", "Eb", "Ec", "Ee", "Eea", "Eeb", "Eec", "Eg", "Ege", "Eh", "Ehe", "F", "FA", "FAa",
			"FAf", "FAfa", "FB", "FBb", "FBf", "FBfb", "FC", "FCc", "FCf", "FCfc", "FD", "FDd", "FDf", "FDfd", "Fa",
			"Fb", "Fc", "Fd", "Ff", "Ffa", "Ffb", "Ffc", "Ffd", "Fh", "Fhf", "G", "GA", "GAa", "GAg", "GAga", "GB",
			"GBb", "GBg", "GBgb", "GC", "GCc", "GCg", "GCgc", "GD", "GDd", "GDg", "GDgd", "GE", "GEe", "GEg", "GEge",
			"Ga", "Gb", "Gc", "Gd", "Ge", "Gg", "Gga", "Ggb", "Ggc", "Ggd", "Gge", "H", "HA", "HAa", "HAh", "HAha",
			"HB", "HBb", "HBh", "HBhb", "HC", "HCc", "HCh", "HChc", "HD", "HDd", "HDh", "HDhd", "HE", "HEe", "HEh",
			"HEhe", "HF", "HFf", "HFh", "HFhf", "Ha", "Hb", "Hc", "Hd", "He", "Hf", "Hh", "Hha", "Hhb", "Hhc", "Hhd",
			"Hhe", "Hhf", "a", "b", "c", "ca", "d", "da", "db", "e", "ea", "eb", "ec", "f", "fa", "fb", "fc", "fd", "g",
			"ga", "gb", "gc", "gd", "ge", "h", "ha", "hb", "hc", "hd", "he", "hf" };

	/**
	 * The en passant target square. If there is no en passant target, this is set
	 * to {@link Field#NO_SQUARE}.
	 */
	protected byte enPassant = Field.NO_SQUARE;

	/**
	 * The index of the White king on the board.
	 * If the White king is not present, this is set to {@link Field#NO_SQUARE}.
	 */
	protected byte whiteKing = Field.NO_SQUARE;

	/**
	 * The index of the Black king on the board.
	 * If the Black king is not present, this is set to {@link Field#NO_SQUARE}.
	 */
	protected byte blackKing = Field.NO_SQUARE;

	/**
	 * The half-move clock, which counts the number of half-moves since the last
	 * pawn move or capture.
	 * This is used for the fifty-move rule.
	 */
	protected short halfMove = 0;

	/**
	 * The full move number, which starts at 1 and increments after each Black move.
	 * This is used to track the progress of the game.
	 */
	protected short fullMove = 1;

	/**
	 * Indicates whether the position is in Chess960 format.
	 * If true, the castling rights and piece positions follow the Chess960 rules.
	 */
	protected boolean chess960 = false;

	/**
	 * Indicates whether it is White's turn to move.
	 * If true, it is White's turn; if false, it is Black's turn.
	 */
	protected boolean whitesTurn = true;

	/**
	 * The kingside castling index of the position. If Chess960, then it is the
	 * index of the rook.
	 * If castling to this side is not possible, this is set to
	 * {@link Field#NO_SQUARE}.
	 */
	protected byte whiteKingside = Field.NO_SQUARE;

	/**
	 * The queenside castling index of the position. If Chess960, then it is the
	 * index of the rook.
	 * If castling to this side is not possible, this is set to
	 * {@link Field#NO_SQUARE}.
	 */
	protected byte whiteQueenside = Field.NO_SQUARE;

	/**
	 * The kingside castling index of the position. If Chess960, then it is the
	 * index of the rook.
	 * If castling to this side is not possible, this is set to
	 * {@link Field#NO_SQUARE}.
	 */
	protected byte blackKingside = Field.NO_SQUARE;

	/**
	 * The queenside castling index of the position. If Chess960, then it is the
	 * index of the rook.
	 * If castling to this side is not possible, this is set to
	 * {@link Field#NO_SQUARE}.
	 */
	protected byte blackQueenside = Field.NO_SQUARE;

	/**
	 * A byte array representing the pieces on the board.
	 */
	protected byte[] board = new byte[64]; // Simpler and easier to modify than bitboards.

	/**
	 * Constructs an empty Position.
	 * 
	 * <p>
	 * This constructor initializes the position with no pieces, no castling rights,
	 * no en passant target, and sets the half-move clock and full move number to
	 * zero.
	 * </p>
	 */
	private Position() {
		// empty constructor for creating an empty position
	}

	/**
	 * Used for creating a deep copy of this Position.
	 * <p>
	 * This method performs a full duplication of the current board state,
	 * including piece placement, castling rights, en passant target square,
	 * half-move clock, full-move number, and active player. The returned
	 * instance is a completely independent object, so subsequent modifications
	 * to it do not affect the original.
	 * </p>
	 *
	 * @return a new Position object representing an exact copy of this one
	 */
	public Position copyOf() {
		Position copy = new Position();
		copy.board = Arrays.copyOf(this.board, this.board.length);
		copy.whitesTurn = this.whitesTurn;
		copy.whiteKing = this.whiteKing;
		copy.blackKing = this.blackKing;
		copy.whiteQueenside = this.whiteQueenside;
		copy.whiteKingside = this.whiteKingside;
		copy.blackQueenside = this.blackQueenside;
		copy.blackKingside = this.blackKingside;
		copy.enPassant = this.enPassant;
		copy.halfMove = this.halfMove;
		copy.fullMove = this.fullMove;
		copy.chess960 = this.chess960;
		return copy;
	}

	/**
	 * Used for constructing a Position from a FEN string.
	 *
	 * @param fen the FEN string to parse.
	 * @throws IllegalArgumentException if the FEN string is invalid, contains empty
	 *                                  fields, numeric fields are not shorts, or
	 *                                  the resulting position is illegal.
	 */
	public Position(String fen) {
		String[] parts = fen.split(" ");

		if (parts.length < 4 || parts.length > 6) {
			throw new IllegalArgumentException(
					"Invalid FEN '" + fen + "' contains " + parts.length + " fields, expected 4 to 6.");
		}

		if (parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty() || parts[3].isEmpty()) {
			throw new IllegalArgumentException("Invalid FEN '" + fen + "' contains empty fields.");
		}

		if (parts.length > 4) {
			try {
				Short.parseShort(parts[4]);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(
						"Invalid FEN '" + fen + "' half move clock (not a short): '" + parts[4] + "'", e);
			}
		}

		if (parts.length > 5) {
			try {
				Short.parseShort(parts[5]);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(
						"Invalid FEN '" + fen + "' full move number (not a short): '" + parts[5] + "'", e);
			}
		}

		initializePieces(parts[0])
				.initializeColor(parts[1])
				.initializeCastling(parts[2])
				.initializeEnPassant(parts[3]);

		if (parts.length > 4) {
			initializeHalfMoveClock(parts[4])
					.initializeFullMove(parts[5]);
		}

		if (!isLegalPosition()) {
			throw new IllegalArgumentException("Invalid FEN '" + fen + "' results in an illegal position.");
		}
	}

	/**
	 * Used for initializing board pieces from a FEN string segment.
	 *
	 * @param pieces the piece placement field of the FEN string
	 * @return itself
	 * @throws IllegalArgumentException if there are too many pieces or invalid
	 *                                  characters
	 */
	private Position initializePieces(String pieces) {
		int index = 0;
		int empty = 0;
		for (int i = 0; i < pieces.length(); i++) {
			if (index >= board.length) {
				throw new IllegalArgumentException("Too many pieces in FEN string: " + pieces);
			}
			switch (pieces.charAt(i)) {
				case '/': {
					index += empty; // skip empty squares
					empty = 0; // reset empty counter
					break;
				}
				case '1', '2', '3', '4', '5', '6', '7', '8': {
					index += pieces.charAt(i) - '0'; // add empty squares
					break;
				}
				case 'P': {
					board[index++] = Piece.WHITE_PAWN;
					break;
				}
				case 'R': {
					board[index++] = Piece.WHITE_ROOK;
					break;
				}
				case 'N': {
					board[index++] = Piece.WHITE_KNIGHT;
					break;
				}
				case 'B': {
					board[index++] = Piece.WHITE_BISHOP;
					break;
				}
				case 'Q': {
					board[index++] = Piece.WHITE_QUEEN;
					break;
				}
				case 'K': {
					whiteKing = (byte) (index);
					board[index++] = Piece.WHITE_KING;
					break;
				}
				case 'p': {
					board[index++] = Piece.BLACK_PAWN;
					break;
				}
				case 'r': {
					board[index++] = Piece.BLACK_ROOK;
					break;
				}
				case 'n': {
					board[index++] = Piece.BLACK_KNIGHT;
					break;
				}
				case 'b': {
					board[index++] = Piece.BLACK_BISHOP;
					break;
				}
				case 'q': {
					board[index++] = Piece.BLACK_QUEEN;
					break;
				}
				case 'k': {
					blackKing = (byte) (index);
					board[index++] = Piece.BLACK_KING;
					break;
				}
				default: {
					throw new IllegalArgumentException("Invalid character in FEN string: " + pieces.charAt(i));
				}
			}
		}
		return this;
	}

	/**
	 * Used for setting the active color from a FEN string segment.
	 *
	 * @param color the active color field ('w' or 'b')
	 * @return itself
	 * @throws IllegalArgumentException if the color is not 'w' or 'b'
	 */
	private Position initializeColor(String color) {
		if (color.equals("b")) {
			whitesTurn = false;
		} else if (color.equals("w")) {
			whitesTurn = true;
		} else {
			throw new IllegalArgumentException("Invalid color in FEN string: " + color);
		}
		return this;
	}

	/**
	 * Validates a default castling string against the default castling order.
	 * <p>
	 * A valid default castling string must satisfy all of the following:
	 * <ul>
	 * <li>It is either a single dash "-" (representing no castling rights) or has a
	 * length between 1 and 4.</li>
	 * <li>Each character in the string is one of the allowed castling characters
	 * defined in {@code STANDARD_CHESS_CASTLING_STRINGS}.</li>
	 * <li>The characters appear in the strict order defined by
	 * {@code STANDARD_CHESS_CASTLING_STRINGS}
	 * (although some characters may be omitted).</li>
	 * </ul>
	 * For example, "KQ" is valid because 'K' appears before 'Q', while "QK" is
	 * invalid.
	 * </p>
	 *
	 * @param string the castling string to validate (must not be {@code null})
	 * @return {@code true} if the string is valid according to the default castling
	 *         rules; {@code false} otherwise
	 * @implNote The input is assumed to be non-{@code null}.
	 */
	private static boolean isStandardCastling(String string) {
		return Arrays.binarySearch(STANDARD_CHESS_CASTLING_STRINGS, string) >= 0;
	}

	/**
	 * Validates a Chess960 castling string against the Chess960 castling order.
	 * <p>
	 * A valid Chess960 castling string must satisfy all of the following:
	 * <ul>
	 * <li>It is either a single dash "-" (representing no castling rights) or has a
	 * length between 1 and 4.</li>
	 * <li>Each character in the string is one of the allowed castling characters
	 * defined in {@code CHESS_960_CASTLING_STRINGS}.</li>
	 * <li>The characters appear in the strict order defined by
	 * {@code CHESS_960_CASTLING_STRINGS}
	 * (although some characters may be omitted).</li>
	 * </ul>
	 * For example, "HFAh" is valid because 'H' comes first, then 'F', then 'A',
	 * then 'h', whereas "HAF" is invalid because the order is incorrect.
	 * </p>
	 *
	 * @param string the castling string to validate (must not be {@code null})
	 * @return {@code true} if the string is valid according to the Chess960
	 *         castling rules; {@code false} otherwise
	 * @implNote This method assumes that the input string is non-{@code null} and
	 *           only verifies that it is a valid subsequence of
	 *           {@code CHESS_960_CASTLING_STRINGS}.
	 *           It does not enforce that the castling rights are mirrored between
	 *           both sides (e.g. "HAha" is accepted even if not symmetric).
	 */
	private static boolean is960Castling(String string) {
		return Arrays.binarySearch(CHESS_960_CASTLING_STRINGS, string) >= 0;
	}

	/**
	 * Sets the default castling rights based on a standard castling string.
	 * <p>
	 * The castling string is expected to be valid and non-{@code null}. A single
	 * dash ("-")
	 * indicates that no castling rights are available. Otherwise, the string
	 * contains between 1
	 * and 4 characters. Uppercase letters represent White's castling rights and are
	 * mapped to
	 * the corresponding square on White's back rank (A1–H1), while lowercase
	 * letters represent
	 */
	private Position initializeStandardCastling(String string) {
		whiteKingside = whiteQueenside = blackKingside = blackQueenside = Field.NO_SQUARE;
		if (string.equals("-")) {
			return this;
		}
		for (int i = 0; i < string.length(); i++) {
			switch (string.charAt(i)) {
				case 'K': {
					whiteKingside = Field.WHITE_KINGSIDE_CASTLE_KING_TO_INDEX;
					break;
				}
				case 'Q': {
					whiteQueenside = Field.WHITE_QUEENSIDE_CASTLE_KING_TO_INDEX;
					break;
				}
				case 'k': {
					blackKingside = Field.BLACK_KINGSIDE_CASTLE_KING_TO_INDEX;
					break;
				}
				case 'q': {
					blackQueenside = Field.BLACK_QUEENSIDE_CASTLE_KING_TO_INDEX;
					break;
				}
				default: {
					break;
				}
			}
		}
		return this;
	}

	/**
	 * Extracts the Chess960 castling rights from the provided castling string.
	 * <p>
	 * The castling string is expected to be valid and non-{@code null}. A single
	 * dash ("-")
	 * indicates that no castling rights are available. Otherwise, the string
	 * contains between 1
	 * and 4 characters. Uppercase letters represent White's castling rights and are
	 * mapped to
	 * the corresponding square on White's back rank (A1–H1), while lowercase
	 * letters represent
	 * Black's rights and are mapped to the corresponding square on Black's back
	 * rank (A8–H8).
	 * <br>
	 * The first encountered letter for a given color is interpreted as the kingside
	 * right,
	 * and a second letter (if present) as the queenside right.
	 * </p>
	 *
	 * @param string - the Chess960 castling string (must be non-{@code null} and
	 *               already validated)
	 */
	private Position initialize960Castling(String string) {
		whiteKingside = whiteQueenside = blackKingside = blackQueenside = Field.NO_SQUARE;
		if (string.equals("-")) {
			return this;
		}
		for (int i = 0; i < string.length(); i++) {
			char character = string.charAt(i);
			if (Character.isUpperCase(character)) {
				if (whiteKingside == Field.NO_SQUARE) {
					whiteKingside = (byte) (Field.A1 + (character - 'A'));
				} else {
					whiteQueenside = (byte) (Field.A1 + (character - 'A'));
				}
			} else {
				if (blackKingside == Field.NO_SQUARE) {
					blackKingside = (byte) (character - 'a');
				} else {
					blackQueenside = (byte) (character - 'a');
				}
			}
		}
		return this;
	}

	/**
	 * Used for configuring castling rights from a FEN string segment.
	 *
	 * @param castling the castling availability field
	 * @return itself
	 * @throws IllegalArgumentException if the castling string is invalid
	 */
	private Position initializeCastling(String castling) {
		if (isStandardCastling(castling)) {
			initializeStandardCastling(castling);
		} else if (is960Castling(castling)) {
			chess960 = true;
			initialize960Castling(castling);
		} else {
			throw new IllegalArgumentException("Invalid castling string: " + castling);
		}
		return this;
	}

	/**
	 * Used for parsing and setting the en passant target square from FEN.
	 *
	 * @param enPassant the en passant field (square or '-')
	 * @return itself
	 */
	private Position initializeEnPassant(String enPassant) {
		if (!Field.isField(enPassant)) {
			throw new IllegalArgumentException("Invalid En-Passant string: " + enPassant);
		}
		this.enPassant = Field.toIndex(enPassant);
		return this;
	}

	/**
	 * Used for parsing and setting the halfmove clock from FEN.
	 *
	 * @param halfMoveClock the halfmove clock field as string
	 * @return itself
	 */
	private Position initializeHalfMoveClock(String halfMoveClock) {
		this.halfMove = Short.parseShort(halfMoveClock);
		if (this.halfMove < 0) {
			throw new IllegalArgumentException("Invalid halfmove clock: " + halfMoveClock + ", must be >= 0.");
		}
		return this;
	}

	/**
	 * Used for parsing and setting the full move number from FEN.
	 *
	 * @param fullMove the full move number field as string
	 * @return itself
	 */
	private Position initializeFullMove(String fullMove) {
		this.fullMove = Short.parseShort(fullMove);
		if (this.fullMove < 1) {
			throw new IllegalArgumentException("Invalid full move number: " + fullMove + ", must be >= 1.");
		}
		return this;
	}

	/**
	 * Used for verifying whether the current position is legal.
	 *
	 * @return true if the position is legal; false otherwise
	 */
	private boolean isLegalPosition() {
		// Kings must be present (only 1 black and only 1 white) and not in check
		if (!validKings()) {
			return false;
		}

		// No pawns on rank 1 or 8 and no more than 8 pawns per side
		if (!validPawns()) {
			return false;
		}

		// En passant: ensure pawn is present and en passant is technically possible
		if (!validEnPassant()) {
			return false;
		}

		// Castling: king and corresponding rook must be present and on correct square
		return validCastlingRights();
	}

	/**
	 * Used for validating that each side owns exactly one king, both
	 * {@code whiteKing} and
	 * {@code blackKing} indices are set, and the side not having the move is not
	 * currently in
	 * check.
	 *
	 * @return {@code true} if king placement and check status are legal;
	 *         {@code false} otherwise
	 */
	private boolean validKings() {
		int whiteKingCount = 0;
		int blackKingCount = 0;
		for (byte square : board) {
			if (square == Piece.WHITE_KING) {
				whiteKingCount++;
			}
			if (square == Piece.BLACK_KING) {
				blackKingCount++;
			}
		}
		if (whiteKingCount != 1 || blackKingCount != 1) {
			return false;
		}
		if (whiteKing == Field.NO_SQUARE || blackKing == Field.NO_SQUARE) {
			return false;
		}
		if (!whitesTurn && whiteKingInCheck()) {
			return false;
		}
		return !(whitesTurn && blackKingInCheck());
	}

	/**
	 * Used for guaranteeing that no pawn occupies the first or eighth rank and that
	 * each side
	 * has at most eight pawns on the board.
	 *
	 * @return {@code true} if all pawns reside on legal ranks and counts do not
	 *         exceed eight
	 */
	private boolean validPawns() {
		for (int i = 0; i < 8; i++) {
			if (Piece.isPawn(board[i]) || Piece.isPawn(board[Field.A1 + i])) {
				return false;
			}
		}
		int whitePawns = 0;
		int blackPawns = 0;
		for (int i = Field.A2; i < Field.H1; i++) {
			if (board[i] == Piece.WHITE_PAWN) {
				whitePawns++;
			}
			if (board[i] == Piece.BLACK_PAWN) {
				blackPawns++;
			}
		}
		return whitePawns <= 8 && blackPawns <= 8;
	}

	/**
	 * Used for validating the {@code enPassant} target square against both the
	 * board state and
	 * the side to move, delegating to colour-specific helpers.
	 *
	 * @return {@code true} if the stored en-passant target permits at least one
	 *         legal capture;
	 *         {@code false} otherwise
	 */
	private boolean validEnPassant() {
		if (enPassant == Field.NO_SQUARE) {
			return true;
		}

		if (whitesTurn) {
			return validWhiteEnPassant();
		}

		return validBlackEnPassant();
	}

	/**
	 * Used for validating that an en-passant target allows at least one legal White
	 * capture.
	 *
	 * @return {@code true} if a legal White en-passant capture exists
	 */
	private boolean validWhiteEnPassant() {
		if (!Field.isOn6thRank(enPassant)) {
			return false;
		}

		byte enPassantTarget = Field.uprank(enPassant);

		if (board[enPassantTarget] != Piece.BLACK_PAWN) {
			return false;
		}

		byte leftSquare = Field.leftOf(enPassantTarget);
		byte rightSquare = Field.rightOf(enPassantTarget);

		board[enPassantTarget] = Piece.EMPTY;
		boolean leftCapture = Field.isOn5thRank(leftSquare) && isLegalWhiteMove(Move.of(leftSquare, enPassant));
		boolean rightCapture = Field.isOn5thRank(rightSquare) && isLegalWhiteMove(Move.of(rightSquare, enPassant));
		board[enPassantTarget] = Piece.BLACK_PAWN;

		return leftCapture || rightCapture;
	}

	/**
	 * Used for validating that an en-passant target allows at least one legal Black
	 * capture.
	 *
	 * @return {@code true} if a legal Black en-passant capture exists
	 */
	private boolean validBlackEnPassant() {
		if (!Field.isOn3rdRank(enPassant)) {
			return false;
		}

		byte enPassantTarget = Field.downrank(enPassant);

		if (board[enPassantTarget] != Piece.WHITE_PAWN) {
			return false;
		}

		byte leftSquare = Field.leftOf(enPassantTarget);
		byte rightSquare = Field.rightOf(enPassantTarget);

		board[enPassantTarget] = Piece.EMPTY;
		boolean leftCapture = Field.isOn4thRank(leftSquare) && isLegalBlackMove(Move.of(leftSquare, enPassant));
		boolean rightCapture = Field.isOn4thRank(rightSquare) && isLegalBlackMove(Move.of(rightSquare, enPassant));
		board[enPassantTarget] = Piece.WHITE_PAWN;

		return leftCapture || rightCapture;
	}

	/**
	 * Used for verifying if castling rights are valid based on piece positions.
	 *
	 * @return true if castling rights correspond to existing rooks and king; false
	 *         otherwise
	 */
	private boolean validCastlingRights() {
		return checkCastling(whiteKingside, chess960 ? whiteKingside : Field.H1, Piece.WHITE_ROOK,
				chess960 ? Field.NO_SQUARE : Field.E1, whiteKing)
				&& checkCastling(whiteQueenside, chess960 ? whiteQueenside : Field.A1, Piece.WHITE_ROOK,
						chess960 ? Field.NO_SQUARE : Field.E1, whiteKing)
				&& checkCastling(blackKingside, chess960 ? blackKingside : Field.H8, Piece.BLACK_ROOK,
						chess960 ? Field.NO_SQUARE : Field.E8, blackKing)
				&& checkCastling(blackQueenside, chess960 ? blackQueenside : Field.A8, Piece.BLACK_ROOK,
						chess960 ? Field.NO_SQUARE : Field.E8, blackKing);
	}

	/**
	 * Used for checking a single castling flag against board state.
	 *
	 * @param flagSquare         the castling flag square (or NO_SQUARE if no right)
	 * @param rookSquare         the square where the rook should be located
	 * @param rookPiece          the expected rook piece constant
	 * @param requiredKingSquare the king's home square for standard castling, or
	 *                           NO_SQUARE for Chess960
	 * @param actualKingPosition the current king position square
	 * @return true if the castling flag is valid; false otherwise
	 */
	private boolean checkCastling(byte flagSquare, byte rookSquare, byte rookPiece, byte requiredKingSquare,
			byte actualKingPosition) {
		// no right → always valid
		if (flagSquare == Field.NO_SQUARE) {
			return true;
		}
		// rook must be in place and king must exist
		if (board[rookSquare] != rookPiece || actualKingPosition == Field.NO_SQUARE) {
			return false;
		}

		// for standard chess, king must also be on its home square; for Chess960
		// requiredKingSquare == NO_SQUARE so this check is skipped
		return requiredKingSquare == Field.NO_SQUARE
				|| actualKingPosition == requiredKingSquare;
	}

	/**
	 * Used for checking if the white king is checkmated.
	 *
	 * @return true if the white king is checkmated, false otherwise.
	 */
	private boolean whiteCheckmated() {
		return whiteKingInCheck() && getWhiteMoves(new MoveList()).isEmpty();
	}

	/**
	 * Used for checking if the black king is checkmated.
	 *
	 * @return true if the black king is checkmated, false otherwise.
	 */
	private boolean blackCheckmated() {
		return blackKingInCheck() && getBlackMoves(new MoveList()).isEmpty();
	}

	/**
	 * Used for checking if the current player's king is checkmated.
	 *
	 * @return true if the active player's king is checkmated; false otherwise
	 */
	public boolean isMate() {
		if (whitesTurn) {
			return whiteCheckmated();
		}

		return blackCheckmated();
	}

	/**
	 * Used for checking if a sliding piece (bishop, rook, or queen) attacks a
	 * square.
	 *
	 * @param field     the index of the square being attacked
	 * @param attacker1 the first possible attacking piece
	 * @param attacker2 the second possible attacking piece
	 * @param options   movement directions and ranges from each square
	 * @return true if either attacker is found before obstruction; false otherwise
	 */
	private boolean slidingAttack(int field, byte attacker1, byte attacker2, byte[][][] options) {
		for (byte[] ray : options[field]) {
			for (byte square : ray) {
				byte value = board[square];
				if (value == attacker1 || value == attacker2) {
					return true;
				}
				if (value != Piece.EMPTY) {
					break;
				}
			}
		}
		return false;
	}

	/**
	 * Used for checking if a non-sliding piece attacks a square.
	 *
	 * @param field    the index of the square being attacked
	 * @param attacker the attacking piece type
	 * @param options  array of offset positions
	 * @return true if the attacker is found at any offset; false otherwise
	 */
	private boolean staticAttack(int field, byte attacker, byte[][] options) {
		for (int i = 0; i < options[field].length; i++) {
			if (board[options[field][i]] == attacker) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if the given square is attacked by the opponent's pieces.
	 * This is a pure geometric attack test; it does not verify side to move.
	 *
	 * @param field target square index (0..63)
	 * @return true if any opponent piece attacks {@code field}
	 */
	protected boolean attacked(int field) {
		if (whitesTurn) {
			return attackedByBlack(field);
		}
		return attackedByWhite(field);
	}

	/**
	 * Used for determining if a square is attacked by any Black piece.
	 *
	 * @param field the index of the square to check
	 * @return true if the square is attacked by Black; false otherwise
	 */
	protected boolean attackedByBlack(int field) {
		return staticAttack(field, Piece.BLACK_KNIGHT, Field.JUMPS)
				|| staticAttack(field, Piece.BLACK_KING, Field.NEIGHBORS)
				|| staticAttack(field, Piece.BLACK_PAWN, Field.PAWN_CAPTURE_WHITE)
				|| slidingAttack(field, Piece.BLACK_BISHOP, Piece.BLACK_QUEEN, Field.DIAGONALS)
				|| slidingAttack(field, Piece.BLACK_ROOK, Piece.BLACK_QUEEN, Field.LINES);
	}

	/**
	 * Used for determining if a square is attacked by any White piece.
	 *
	 * @param field the index of the square to check
	 * @return true if the square is attacked by White; false otherwise
	 */
	protected boolean attackedByWhite(int field) {
		return staticAttack(field, Piece.WHITE_KNIGHT, Field.JUMPS)
				|| staticAttack(field, Piece.WHITE_KING, Field.NEIGHBORS)
				|| staticAttack(field, Piece.WHITE_PAWN, Field.PAWN_CAPTURE_BLACK)
				|| slidingAttack(field, Piece.WHITE_BISHOP, Piece.WHITE_QUEEN, Field.DIAGONALS)
				|| slidingAttack(field, Piece.WHITE_ROOK, Piece.WHITE_QUEEN, Field.LINES);
	}

	/**
	 * Used for checking if the White king is in check.
	 *
	 * @return true if the White king is attacked; false otherwise
	 */
	private boolean whiteKingInCheck() {
		return attackedByBlack(whiteKing);
	}

	/**
	 * Used for checking if the Black king is in check.
	 *
	 * @return true if the Black king is attacked; false otherwise
	 */
	private boolean blackKingInCheck() {
		return attackedByWhite(blackKing);
	}

	/**
	 * Used for checking if the chosen player's king is in check.
	 *
	 * @return true if the chosen player's king is under attack; false otherwise
	 */
	private boolean inCheck(boolean whitesTurn) {
		if (whitesTurn) {
			return whiteKingInCheck();
		}
		return blackKingInCheck();
	}

	/**
	 * Used for checking if the current player's king is in check.
	 *
	 * @return true if the active player's king is under attack; false otherwise
	 */
	public boolean inCheck() {
		return inCheck(whitesTurn);
	}

	/**
	 * Used for checking if a sliding piece (bishop, rook, or queen) attacks a
	 * square.
	 *
	 * @param buffer    the array to store attacking positions
	 * @param index     the current index in the buffer
	 * @param field     the index of the square being attacked
	 * @param attacker1 the first possible attacking piece
	 * @param attacker2 the second possible attacking piece
	 * @param options   movement directions and ranges from each square
	 * @return updated index after storing attack positions
	 */
	private int slidingAttackBuffer(byte[] buffer, int index, int field, byte attacker1, byte attacker2,
			byte[][][] options) {
		for (byte[] ray : options[field]) {
			for (byte sq : ray) {
				byte value = board[sq];
				boolean isAttacker = (value == attacker1 || value == attacker2);
				if (isAttacker) {
					buffer[index++] = sq;
				}
				if (isAttacker || !Piece.isEmpty(value)) {
					break;
				}
			}
		}
		return index;
	}

	/**
	 * Used for checking if a non-sliding piece attacks a square.
	 *
	 * @param buffer   the array to store attacking positions
	 * @param index    the current index in the buffer
	 * @param field    the index of the square being attacked
	 * @param attacker the attacking piece type
	 * @param options  array of offset positions
	 * @return updated index after storing attack positions
	 */
	private int staticAttackBuffer(byte[] buffer, int index, int field, byte attacker, byte[][] options) {
		for (int i = 0; i < options[field].length; i++) {
			if (board[options[field][i]] == attacker) {
				buffer[index] = options[field][i];
				index++;
			}
		}
		return index;
	}

	/**
	 * Used for finding which squares are attacking a black piece at the given
	 * field.
	 *
	 * @param field the square index of the black piece being checked.
	 * @return an array of square indices from which white pieces attack the
	 *         specified field.
	 */
	protected byte[] attacksByWhite(int field) {
		byte[] buffer = new byte[32];
		int index = 0;
		index = staticAttackBuffer(buffer, index, field, Piece.WHITE_KNIGHT, Field.JUMPS);
		index = staticAttackBuffer(buffer, index, field, Piece.WHITE_KING, Field.NEIGHBORS);
		index = staticAttackBuffer(buffer, index, field, Piece.WHITE_PAWN, Field.PAWN_CAPTURE_BLACK);
		index = slidingAttackBuffer(buffer, index, field, Piece.WHITE_BISHOP, Piece.WHITE_QUEEN, Field.DIAGONALS);
		index = slidingAttackBuffer(buffer, index, field, Piece.WHITE_ROOK, Piece.WHITE_QUEEN, Field.LINES);
		return Arrays.copyOf(buffer, index);
	}

	/**
	 * Used for finding which squares are attacking a white piece at the given
	 * field.
	 *
	 * @param field the square index of the white piece being checked.
	 * @return an array of square indices from which black pieces attack the
	 *         specified field.
	 */
	protected byte[] attacksByBlack(int field) {
		byte[] buffer = new byte[32];
		int index = 0;
		index = staticAttackBuffer(buffer, index, field, Piece.BLACK_KNIGHT, Field.JUMPS);
		index = staticAttackBuffer(buffer, index, field, Piece.BLACK_KING, Field.NEIGHBORS);
		index = staticAttackBuffer(buffer, index, field, Piece.BLACK_PAWN, Field.PAWN_CAPTURE_WHITE);
		index = slidingAttackBuffer(buffer, index, field, Piece.BLACK_BISHOP, Piece.BLACK_QUEEN, Field.DIAGONALS);
		index = slidingAttackBuffer(buffer, index, field, Piece.BLACK_ROOK, Piece.BLACK_QUEEN, Field.LINES);
		return Arrays.copyOf(buffer, index);
	}

	/**
	 * Used for getting the squares that are attacking the king of the current
	 * player.
	 * 
	 * @return an array of indices representing the squares that are attacking the
	 *         king
	 */
	public byte[] getCheckers() {
		if (whitesTurn) {
			return attacksByBlack(whiteKing);
		}
		return attacksByWhite(blackKing);
	}

	/**
	 * Returns true if the destination square contains an opponent piece (regular
	 * capture),
	 * excluding en passant.
	 *
	 * @param moveto destination square index (0..63)
	 * @return true if a regular capture occurs on {@code moveto}
	 */
	protected boolean isRegularCapture(short moveto) {
		if (whitesTurn) {
			return Piece.isBlack(board[moveto]);
		}

		return Piece.isWhite(board[moveto]);
	}

	/**
	 * Used for executing the given move on this Position.
	 * <p>
	 * Applies all standard updates required by chess rules: moves the piece,
	 * updates castling rights if a rook or king moves, sets en passant target
	 * when a pawn advances two squares, increments the half-move clock for
	 * the fifty-move rule, advances the full-move counter after Black's move,
	 * and toggles the active player. Assumes the move is legal in the current
	 * context.
	 * </p>
	 *
	 * @param move the Move to apply; must be legal and validated beforehand
	 * @return this Position after applying the move, for method chaining
	 * @throws IllegalArgumentException if the move is illegal or inconsistent
	 */
	public Position play(short move) {
		boolean isCaptureOrPawnMove;

		enPassant = Field.NO_SQUARE;

		if (whitesTurn) {
			isCaptureOrPawnMove = playWhite(move);
		} else {
			isCaptureOrPawnMove = playBlack(move);
			// Full-move counter increments after black's move
			fullMove++;
		}

		// Update half-move clock (for 50-move rule)
		if (isCaptureOrPawnMove) {
			halfMove = 0;
		} else {
			halfMove++;
		}

		// Switch active player
		whitesTurn = !whitesTurn;

		return this;
	}

	/**
	 * Used for executing a non-special move (queen, rook, bishop or knight) and
	 * reporting captures.
	 *
	 * @param move the move to apply
	 * @return {@code true} if a piece was captured, otherwise {@code false}
	 */
	private boolean playRegular(byte movefrom, byte moveto) {
		boolean captured = isRegularCapture(moveto);
		board[moveto] = board[movefrom];
		board[movefrom] = Piece.EMPTY;
		return captured;
	}

	/**
	 * Used for performing White's move and updating clocks, en-passant and castling
	 * metadata.
	 *
	 * @param move the move chosen for White
	 * @return {@code true} if the half-move clock must be reset
	 */
	private boolean playWhite(short move) {
		byte movefrom = Move.getFromIndex(move);
		byte moveto = Move.getToIndex(move);

		updateBlackCastlingRights(moveto);
		return switch (board[movefrom]) {
			case Piece.WHITE_PAWN -> playWhitePawn(movefrom, moveto, Move.getPromotion(move));
			case Piece.WHITE_KING -> playWhiteKing(movefrom, moveto);
			case Piece.WHITE_ROOK -> playWhiteRook(movefrom, moveto);
			case Piece.WHITE_BISHOP, Piece.WHITE_KNIGHT, Piece.WHITE_QUEEN -> playRegular(movefrom, moveto);
			default -> false; // something went wrong, this should never trigger
		};
	}

	/**
	 * Used for updating Black's castling rights when a rook on its initial square
	 * gets captured or when the king/rook moves to {@code moveto}.
	 *
	 * @param moveto destination square index (0..63) that may clear Black's rights
	 */
	private void updateBlackCastlingRights(short moveto) {
		byte queensideRookStart = chess960 ? blackQueenside : Field.BLACK_QUEENSIDE_CASTLE_ROOK_FROM_INDEX;
		byte kingsideRookStart = chess960 ? blackKingside : Field.BLACK_KINGSIDE_CASTLE_ROOK_FROM_INDEX;
		if (moveto == queensideRookStart) {
			blackQueenside = Field.NO_SQUARE;
		} else if (moveto == kingsideRookStart) {
			blackKingside = Field.NO_SQUARE;
		}
	}

	/**
	 * Used for simulating an en passant move for white. If legal, sets the en
	 * passant
	 * target square for the position and returns true.
	 *
	 * @param from       the moving pawn’s origin square
	 * @param to         the moving pawn’s destination square
	 * @param capturedSq the square behind {@code to} where the capture would occur
	 * @param pawnSq     the adjacent opponent pawn square to test
	 * @see #playWhitePawn(byte, byte, byte)
	 * @return true if the en passant right is legal for White
	 */
	private boolean simulateWhiteEnPassant(int from, int to, byte capturedSq, byte pawnSq) {
		if (!Field.isOn4thRank(pawnSq) || board[pawnSq] != Piece.BLACK_PAWN) {
			return false;
		}

		byte saveTo = board[to];
		byte saveFrom = board[from];
		byte saveTarget = board[capturedSq];
		byte savePawn = board[pawnSq];
		board[to] = Piece.EMPTY;
		board[from] = Piece.EMPTY;
		board[capturedSq] = savePawn;
		board[pawnSq] = Piece.EMPTY;
		boolean legal = !blackKingInCheck();
		if (legal) {
			enPassant = capturedSq;
		}
		// restore
		board[to] = saveTo;
		board[from] = saveFrom;
		board[capturedSq] = saveTarget;
		board[pawnSq] = savePawn;
		return legal;
	}

	/**
	 * Performs the movement of a white pawn, updates the board and castling rights,
	 * and returns whether a piece has been captured or a pawn has moved.
	 * <p>
	 * This method handles special pawn moves including:
	 * <ul>
	 * <li>Double-square advances (sets en passant opportunities)</li>
	 * <li>En passant captures</li>
	 * <li>Pawn promotions</li>
	 * <li>Clearing of en passant markers</li>
	 * </ul>
	 * 
	 * @param movefrom  origin square (0..63)
	 * @param moveto    destination square (0..63)
	 * @param promotion promotion code (0=none, 1=knight, 2=bishop, 3=rook, 4=queen)
	 */
	private boolean playWhitePawn(byte movefrom, byte moveto, byte promotion) {
		// Enable En-Passant for Black
		if (Field.isOn2ndRank(movefrom) && Field.isOn4thRank(moveto)) {
			byte enPassantTarget = Field.uprank(moveto); // Square behind destination

			// Check left and right for potential en passant opportunities
			byte leftSquare = Field.leftOf(moveto);
			simulateWhiteEnPassant(movefrom, moveto, enPassantTarget, leftSquare);

			byte rightSquare = Field.rightOf(moveto);
			simulateWhiteEnPassant(movefrom, moveto, enPassantTarget, rightSquare);
		}

		// Clear en passant marker on non-capture moves
		else if (board[moveto] == Piece.EMPTY) {
			board[Field.uprank(moveto)] = Piece.EMPTY;
		}

		// Execute the pawn move
		board[movefrom] = Piece.EMPTY;

		// Handle promotion if specified
		switch (promotion) {
			case Move.NO_PROMOTION:
				board[moveto] = Piece.WHITE_PAWN;
				break;
			case Move.PROMOTION_QUEEN:
				board[moveto] = Piece.WHITE_QUEEN;
				break;
			case Move.PROMOTION_ROOK:
				board[moveto] = Piece.WHITE_ROOK;
				break;
			case Move.PROMOTION_BISHOP:
				board[moveto] = Piece.WHITE_BISHOP;
				break;
			case Move.PROMOTION_KNIGHT:
				board[moveto] = Piece.WHITE_KNIGHT;
				break;
			default:
				break;
		}
		return true; // Pawn always moves, so return true
	}

	/**
	 * Handles the movement of the White king, including standard and Chess960
	 * castling,
	 * updates castling rights, and returns whether a capture occurred.
	 *
	 * @param movefrom origin square (0..63)
	 * @param moveto   destination square (0..63)
	 * @return true if a piece was captured, false otherwise (including castling)
	 */
	private boolean playWhiteKing(byte movefrom, byte moveto) {
		// Chess960: Castling occurs when the king captures his own rook
		boolean isKingsideCastle = moveto == whiteKingside && board[moveto] == Piece.WHITE_ROOK;
		boolean isQueensideCastle = moveto == whiteQueenside && board[moveto] == Piece.WHITE_ROOK;

		// King moved: permanently revoke both castling rights
		whiteQueenside = Field.NO_SQUARE;
		whiteKingside = Field.NO_SQUARE;

		whiteKing = moveto;

		if (chess960) {
			if (isKingsideCastle) {
				return executeWhiteCastling(
						movefrom,
						Field.WHITE_KINGSIDE_CASTLE_KING_TO_INDEX,
						moveto,
						Field.WHITE_KINGSIDE_CASTLE_ROOK_TO_INDEX);
			} else if (isQueensideCastle) {
				return executeWhiteCastling(
						movefrom,
						Field.WHITE_QUEENSIDE_CASTLE_KING_TO_INDEX,
						moveto,
						Field.WHITE_QUEENSIDE_CASTLE_ROOK_TO_INDEX);
			}
		} else {
			// Standard chess: Castling is identified by king moving two squares
			boolean isStartingPosition = movefrom == Field.WHITE_KING_STANDARD_INDEX;
			boolean isKingsideMove = moveto == Field.WHITE_KINGSIDE_CASTLE_KING_TO_INDEX;
			boolean isQueensideMove = moveto == Field.WHITE_QUEENSIDE_CASTLE_KING_TO_INDEX;

			if (isStartingPosition) {
				if (isKingsideMove) {
					return executeWhiteCastling(
							movefrom,
							moveto,
							Field.WHITE_KINGSIDE_CASTLE_ROOK_FROM_INDEX,
							Field.WHITE_KINGSIDE_CASTLE_ROOK_TO_INDEX);
				} else if (isQueensideMove) {
					return executeWhiteCastling(
							movefrom,
							moveto,
							Field.WHITE_QUEENSIDE_CASTLE_ROOK_FROM_INDEX,
							Field.WHITE_QUEENSIDE_CASTLE_ROOK_TO_INDEX);
				}
			}
		}

		// Regular king move (non-castling)
		return playRegular(movefrom, moveto);
	}

	/**
	 * Used for executing White castling by relocating the king and rook to their
	 * destination squares.
	 *
	 * @param kingFrom the king's starting square
	 * @param kingTo   the king's target square
	 * @param rookFrom the rook's starting square
	 * @param rookTo   the rook's target square
	 * @return always {@code false} – castling never captures
	 */
	private boolean executeWhiteCastling(byte kingFrom, byte kingTo, byte rookFrom, byte rookTo) {
		// Clear original positions
		board[kingFrom] = Piece.EMPTY;
		board[rookFrom] = Piece.EMPTY;

		// Place pieces in new positions
		board[kingTo] = Piece.WHITE_KING;
		board[rookTo] = Piece.WHITE_ROOK;

		// Update king position
		whiteKing = kingTo;

		return false;
	}

	/**
	 * Used for handling a White rook move and clearing obsolete castling rights.
	 *
	 * @param movefrom origin square (0..63)
	 * @param moveto   destination square (0..63)
	 * @return {@code true} when a capture occurs, otherwise {@code false}
	 */
	private boolean playWhiteRook(byte movefrom, byte moveto) {
		byte queensideStart = chess960 ? whiteQueenside : Field.WHITE_QUEENSIDE_CASTLE_ROOK_FROM_INDEX;
		byte kingsideStart = chess960 ? whiteKingside : Field.WHITE_KINGSIDE_CASTLE_ROOK_FROM_INDEX;
		if (movefrom == queensideStart) {
			whiteQueenside = Field.NO_SQUARE;
		} else if (movefrom == kingsideStart) {
			whiteKingside = Field.NO_SQUARE;
		}
		return playRegular(movefrom, moveto);
	}

	/**
	 * Used for performing Black's move and updating clocks, en-passant and castling
	 * metadata.
	 *
	 * @param move the move chosen for Black
	 * @return {@code true} if the half-move clock must be reset
	 */
	private boolean playBlack(short move) {
		byte movefrom = Move.getFromIndex(move);
		byte moveto = Move.getToIndex(move);

		updateWhiteCastlingRights(moveto);

		return switch (board[movefrom]) {
			case Piece.BLACK_PAWN -> playBlackPawn(movefrom, moveto, Move.getPromotion(move));
			case Piece.BLACK_KING -> playBlackKing(movefrom, moveto);
			case Piece.BLACK_ROOK -> playBlackRook(movefrom, moveto);
			case Piece.BLACK_BISHOP, Piece.BLACK_KNIGHT, Piece.BLACK_QUEEN -> playRegular(movefrom, moveto);
			default -> false; // something went wrong, this should never trigger
		};
	}

	/**
	 * Used for updating White's castling rights when a rook on its initial square
	 * gets captured or when the king/rook moves to {@code moveto}.
	 *
	 * @param moveto destination square index (0..63) that may clear White's rights
	 */
	private void updateWhiteCastlingRights(short moveto) {
		byte queensideRookStart = chess960 ? whiteQueenside : Field.WHITE_QUEENSIDE_CASTLE_ROOK_FROM_INDEX;
		byte kingsideRookStart = chess960 ? whiteKingside : Field.WHITE_KINGSIDE_CASTLE_ROOK_FROM_INDEX;
		if (moveto == queensideRookStart) {
			whiteQueenside = Field.NO_SQUARE;
		} else if (moveto == kingsideRookStart) {
			whiteKingside = Field.NO_SQUARE;
		}
	}

	/**
	 * Used for simulating an en passant move for black. If legal, sets the en
	 * passant
	 * target square for the position and returns true.
	 *
	 * @param from       the moving pawn’s origin square
	 * @param to         the moving pawn’s destination square
	 * @param capturedSq the square behind {@code to} where the capture would occur
	 * @param pawnSq     the adjacent opponent pawn square to test
	 * @see #playBlackPawn(byte, byte, byte)
	 * @return true if the en passant right is legal for Black
	 */
	private boolean simulateBlackEnPassant(int from, int to, byte capturedSq, byte pawnSq) {
		if (!Field.isOn5thRank(pawnSq) || board[pawnSq] != Piece.WHITE_PAWN) {
			return false;
		}

		byte saveTo = board[to];
		byte saveFrom = board[from];
		byte saveTarget = board[capturedSq];
		byte savePawn = board[pawnSq];
		board[to] = Piece.EMPTY;
		board[from] = Piece.EMPTY;
		board[capturedSq] = savePawn;
		board[pawnSq] = Piece.EMPTY;
		boolean legal = !whiteKingInCheck();
		if (legal) {
			enPassant = capturedSq;
		}

		// restore
		board[to] = saveTo;
		board[from] = saveFrom;
		board[capturedSq] = saveTarget;
		board[pawnSq] = savePawn;
		return legal;
	}

	/**
	 * Performs the movement of a black pawn, updates the board and castling rights,
	 * and returns whether a piece has been captured or a pawn has moved.
	 * <p>
	 * This method handles special pawn moves including:
	 * <ul>
	 * <li>Double-square advances (sets en passant opportunities)</li>
	 * <li>En passant captures</li>
	 * <li>Pawn promotions</li>
	 * <li>Clearing of en passant markers</li>
	 * </ul>
	 * 
	 * @param move the move to execute
	 * @return true if a piece was captured or pawn moved (always true for pawn
	 *         moves)
	 * @see #playBlack(Move)
	 */
	private boolean playBlackPawn(byte movefrom, byte moveto, byte promotion) {
		// Enable En-Passant for Black
		if (Field.isOn7thRank(movefrom) && Field.isOn5thRank(moveto)) {
			byte enPassantTarget = Field.downrank(moveto); // Square behind destination

			// Check left and right for potential en passant opportunities
			byte leftSquare = Field.leftOf(moveto);
			simulateBlackEnPassant(movefrom, moveto, enPassantTarget, leftSquare);

			byte rightSquare = Field.rightOf(moveto);
			simulateBlackEnPassant(movefrom, moveto, enPassantTarget, rightSquare);
		}

		// Clear en passant marker on non-capture moves
		else if (board[moveto] == Piece.EMPTY) {
			board[Field.downrank(moveto)] = Piece.EMPTY;
		}

		// Execute the pawn move
		board[movefrom] = Piece.EMPTY;

		// Handle promotion if specified
		switch (promotion) {
			case Move.NO_PROMOTION:
				board[moveto] = Piece.BLACK_PAWN;
				break;
			case Move.PROMOTION_QUEEN:
				board[moveto] = Piece.BLACK_QUEEN;
				break;
			case Move.PROMOTION_ROOK:
				board[moveto] = Piece.BLACK_ROOK;
				break;
			case Move.PROMOTION_BISHOP:
				board[moveto] = Piece.BLACK_BISHOP;
				break;
			case Move.PROMOTION_KNIGHT:
				board[moveto] = Piece.BLACK_KNIGHT;
				break;
			default:
				break;
		}
		return true; // Pawn always moves, so return true
	}

	/**
	 * Handles the movement of the Black king, including standard and Chess960
	 * castling,
	 * updates castling rights, and returns whether a capture occurred.
	 *
	 * @param move the king move
	 * @see #playWhite(Move)
	 * @return true if a piece was captured, false otherwise (including castling)
	 */
	private boolean playBlackKing(byte movefrom, byte moveto) {
		// Chess960: Castling occurs when the king captures his own rook
		boolean isKingsideCastle = moveto == blackKingside && board[moveto] == Piece.BLACK_ROOK;
		boolean isQueensideCastle = moveto == blackQueenside && board[moveto] == Piece.BLACK_ROOK;

		// King moved: permanently revoke both castling rights
		blackQueenside = Field.NO_SQUARE;
		blackKingside = Field.NO_SQUARE;

		blackKing = moveto;

		if (chess960) {
			if (isKingsideCastle) {
				return executeBlackCastling(
						movefrom,
						Field.BLACK_KINGSIDE_CASTLE_KING_TO_INDEX,
						moveto,
						Field.BLACK_KINGSIDE_CASTLE_ROOK_TO_INDEX);
			} else if (isQueensideCastle) {
				return executeBlackCastling(
						movefrom,
						Field.BLACK_QUEENSIDE_CASTLE_KING_TO_INDEX,
						moveto,
						Field.BLACK_QUEENSIDE_CASTLE_ROOK_TO_INDEX);
			}
		} else {
			// Standard chess: Castling is identified by king moving two squares
			boolean isStartingPosition = movefrom == Field.BLACK_KING_STANDARD_INDEX;
			boolean isKingsideMove = moveto == Field.BLACK_KINGSIDE_CASTLE_KING_TO_INDEX;
			boolean isQueensideMove = moveto == Field.BLACK_QUEENSIDE_CASTLE_KING_TO_INDEX;

			if (isStartingPosition) {
				if (isKingsideMove) {
					return executeBlackCastling(
							movefrom,
							moveto,
							Field.BLACK_KINGSIDE_CASTLE_ROOK_FROM_INDEX,
							Field.BLACK_KINGSIDE_CASTLE_ROOK_TO_INDEX);
				} else if (isQueensideMove) {
					return executeBlackCastling(
							movefrom,
							moveto,
							Field.BLACK_QUEENSIDE_CASTLE_ROOK_FROM_INDEX,
							Field.BLACK_QUEENSIDE_CASTLE_ROOK_TO_INDEX);
				}
			}
		}

		// Regular king move (non-castling)
		return playRegular(movefrom, moveto);
	}

	/**
	 * Used for executing Black castling by relocating the king and rook to their
	 * destination squares.
	 *
	 * @param kingFrom the king's starting square
	 * @param kingTo   the king's target square
	 * @param rookFrom the rook's starting square
	 * @param rookTo   the rook's target square
	 * @return always {@code false} – castling never captures
	 */
	private boolean executeBlackCastling(byte kingFrom, byte kingTo, byte rookFrom, byte rookTo) {
		// Clear original positions
		board[kingFrom] = Piece.EMPTY;
		board[rookFrom] = Piece.EMPTY;

		// Place pieces in new positions
		board[kingTo] = Piece.BLACK_KING;
		board[rookTo] = Piece.BLACK_ROOK;

		// Update king position
		blackKing = kingTo;

		return false;
	}

	/**
	 * Used for handling a Black rook move and clearing obsolete castling rights.
	 *
	 * @param move the rook move to execute
	 * @return {@code true} when a capture occurs, otherwise {@code false}
	 */
	private boolean playBlackRook(byte movefrom, byte moveto) {
		byte queensideStart = chess960 ? blackQueenside : Field.BLACK_QUEENSIDE_CASTLE_ROOK_FROM_INDEX;
		byte kingsideStart = chess960 ? blackKingside : Field.BLACK_KINGSIDE_CASTLE_ROOK_FROM_INDEX;
		if (movefrom == queensideStart) {
			blackQueenside = Field.NO_SQUARE;
		} else if (movefrom == kingsideStart) {
			blackKingside = Field.NO_SQUARE;
		}
		return playRegular(movefrom, moveto);
	}

	/**
	 * Used for determining if a given move captures an opponent's piece.
	 *
	 * @param movefrom origin square index (0..63)
	 * @param moveto   destination square index (0..63)
	 * @return true if the move is a regular or en passant capture; false otherwise
	 */
	public boolean isCapture(byte movefrom, byte moveto) {
		return isRegularCapture(moveto) || isEnPassantCapture(movefrom, moveto);
	}

	/**
	 * Used for determining if a given move is an en passant capture.
	 *
	 * @param movefrom origin square index (0..63)
	 * @param moveto   destination square index (0..63)
	 * @return true if the move is an en passant capture; false otherwise
	 */
	public boolean isEnPassantCapture(short movefrom, short moveto) {
		return Piece.isPawn(board[movefrom]) && moveto == enPassant;
	}

	/**
	 * Used for returning a FEN (Forsyth-Edwards Notation) string representing
	 * the current chess position.
	 * <p>
	 * Constructs the FEN by serializing piece placement row by row,
	 * followed by active player, castling availability, en passant target,
	 * half-move clock, and full-move number. This string can be used
	 * to reconstruct the position state in other chess engines or tools.
	 * </p>
	 *
	 * @return the FEN representation of this position
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(90);
		sb.append(buildPiecePlacement())
				.append(' ')
				.append(whitesTurn ? 'w' : 'b')
				.append(' ')
				.append(buildCastlingAvailability())
				.append(' ')
				.append(Field.toString(enPassant))
				.append(' ')
				.append(halfMove)
				.append(' ')
				.append(fullMove);
		return sb.toString();
	}

	/**
	 * Builds the piece placement part of the FEN string.
	 * <p>
	 * The piece placement is represented as a series of ranks, with each rank
	 * separated by a '/'.
	 * Each piece is represented by its corresponding character, and empty squares
	 * are represented
	 * by numbers indicating the count of consecutive empty squares.
	 * </p>
	 * for the character representation of pieces.
	 * 
	 * @see #toString()
	 * @return the piece placement string for the FEN representation.
	 */
	private String buildPiecePlacement() {
		StringBuilder placement = new StringBuilder(64);
		for (int rank = 0; rank < 8; rank++) {
			int emptyCount = 0;
			for (int file = 0; file < 8; file++) {
				byte p = board[rank * 8 + file];
				if (p == Piece.EMPTY) {
					emptyCount++;
				} else {
					if (emptyCount > 0) {
						placement.append(emptyCount);
						emptyCount = 0;
					}
					placement.append(Piece.toLowerCaseChar(p));
				}
			}
			if (emptyCount > 0) {
				placement.append(emptyCount);
			}
			if (rank < 7) {
				placement.append('/');
			}
		}
		return placement.toString();
	}

	/**
	 * Used for building the castling availability portion of the FEN string.
	 *
	 * @return the FEN string segment representing castling availability.
	 */
	private String buildCastlingAvailability() {
		StringBuilder rights = new StringBuilder(4);
		if (chess960) {
			appendChess960Castling(rights);
		} else {
			appendStandardCastling(rights);
		}
		return !rights.isEmpty() ? rights.toString() : "-";
	}

	/**
	 * Used for appending standard chess castling symbols to the builder.
	 *
	 * @param rights the StringBuilder to append to.
	 */
	private void appendStandardCastling(StringBuilder rights) {
		if (whiteKingside != Field.NO_SQUARE) {
			rights.append('K');
		}
		if (whiteQueenside != Field.NO_SQUARE) {
			rights.append('Q');
		}
		if (blackKingside != Field.NO_SQUARE) {
			rights.append('k');
		}
		if (blackQueenside != Field.NO_SQUARE) {
			rights.append('q');
		}
	}

	/**
	 * Used for appending Chess960 castling file letters to the builder.
	 *
	 * @param rights the StringBuilder to append to.
	 */
	private void appendChess960Castling(StringBuilder rights) {
		if (whiteKingside != Field.NO_SQUARE) {
			rights.append(Field.getFileUppercase(whiteKingside));
		}
		if (whiteQueenside != Field.NO_SQUARE) {
			rights.append(Field.getFileUppercase(whiteQueenside));
		}
		if (blackKingside != Field.NO_SQUARE) {
			rights.append(Field.getFile(blackKingside));
		}
		if (blackQueenside != Field.NO_SQUARE) {
			rights.append(Field.getFile(blackQueenside));
		}
	}

	/**
	 * Used for generating all legal moves for the current player.
	 * <p>
	 * Scans the board to identify each piece belonging to the active side,
	 * computes its pseudo-legal moves, filters out those that would leave the
	 * king in check, and returns a consolidated MoveList. The computation
	 * runs in O(P * M) time, where P is the number of pieces and M the
	 * average moves per piece.
	 * </p>
	 *
	 * @return a MoveList containing every valid move for the active side
	 */
	public MoveList getMoves() {
		MoveList moves = new MoveList();

		if (whitesTurn) {
			return getWhiteMoves(moves);
		}

		return getBlackMoves(moves);
	}

	/**
	 * Used for checking that every square strictly between two given squares is
	 * empty,
	 * except for a specified square to ignore (occupied by the other piece).
	 *
	 * @param a            start square (0..63)
	 * @param b            end square (0..63)
	 * @param ignoreSquare a square to treat as empty during the check, or -1 for
	 *                     none
	 * @return true if the path is clear (excluding endpoints)
	 */
	private boolean isPathClearExcluding(int a, int b, int ignoreSquare) {
		int step = Integer.signum(b - a);
		for (int sq = a + step; sq != b + step; sq += step) {
			if (sq == ignoreSquare)
				continue;
			if (!Piece.isEmpty(board[sq]))
				return false;
		}
		return true;
	}

	/**
	 * Used for retrieving the list of squares strictly between two given square
	 * indices.
	 *
	 * @param a start square (0..63)
	 * @param b end square (0..63)
	 * @return an array of intermediate squares in board index order
	 */
	private int[] squaresBetween(int a, int b) {
		int dist = Math.abs(b - a) - 1;
		if (dist < 0) {
			return new int[0]; // No squares between a and b
		}
		int step = Integer.signum(b - a);
		int[] path = new int[dist];
		for (int i = 0, sq = a + step; i < dist; i++, sq += step) {
			path[i] = sq;
		}
		return path;
	}

	/**
	 * Used for generating all legal white moves for the current board state.
	 *
	 * @param moves the list to which valid moves will be added
	 * @return the updated move list containing all valid white moves
	 */
	private MoveList getWhiteMoves(MoveList moves) {
		addWhiteEnPassantMoves(moves);
		addWhiteCastlingMoves(moves);
		for (byte i = 0; i < board.length; i++) {
			byte piece = board[i];
			if (piece == Piece.WHITE_PAWN) {
				addWhitePawnMoves(moves, i);
			} else if (piece == Piece.WHITE_KING) {
				addWhiteKingMoves(moves, i, Field.NEIGHBORS);
			} else if (piece == Piece.WHITE_ROOK) {
				addWhiteSlidingMoves(moves, i, Field.LINES);
			} else if (piece == Piece.WHITE_BISHOP) {
				addWhiteSlidingMoves(moves, i, Field.DIAGONALS);
			} else if (piece == Piece.WHITE_KNIGHT) {
				addWhiteStaticMoves(moves, i, Field.JUMPS);
			} else if (piece == Piece.WHITE_QUEEN) {
				addWhiteSlidingMoves(moves, i, Field.DIAGONALS);
				addWhiteSlidingMoves(moves, i, Field.LINES);
			}
		}
		return moves;
	}

	/**
	 * Used for adding all legal non-sliding moves (knight) for a White piece.
	 *
	 * @param moves   the list to append to
	 * @param index   the piece's square
	 * @param options pre-computed destination offsets
	 * @return the updated {@code moves} list
	 */
	private MoveList addWhiteStaticMoves(MoveList moves, byte index, byte[][] options) {
		for (byte to : options[index]) {
			if (Piece.isWhite(board[to]))
				continue;
			short move = Move.of(index, to);
			if (isLegalWhiteMove(move)) {
				moves.add(move);
			}
		}
		return moves;
	}

	/**
	 * Used for adding all legal king moves for White while ensuring the king stays
	 * out of check.
	 *
	 * @param moves   the list to append to
	 * @param index   the king's square
	 * @param options one-square neighbor offsets
	 * @return the updated {@code moves} list
	 */
	private MoveList addWhiteKingMoves(MoveList moves, byte index, byte[][] options) {
		for (byte to : options[index]) {
			if (Piece.isWhite(board[to]))
				continue;
			short move = Move.of(index, to);
			whiteKing = to;
			if (isLegalWhiteMove(move)) {
				moves.add(move);
			}
			whiteKing = index;
		}
		return moves;
	}

	/**
	 * Used for adding legal sliding piece moves (bishop, rook, queen) for white
	 * pieces.
	 *
	 * @param moves   the list to which valid sliding moves will be added
	 * @param index   the starting square of the sliding piece
	 * @param options the move rays available for each square on the board
	 * @return the updated move list containing all valid sliding moves
	 */
	private MoveList addWhiteSlidingMoves(MoveList moves, byte index, byte[][][] options) {
		// for each direction ray from 'index'
		for (byte[] ray : options[index]) {
			// walk square by square
			for (byte to : ray) {
				short move = Move.of(index, to);

				// if empty or enemy‐occupied, try the move
				if (!Piece.isWhite(board[to]) && isLegalWhiteMove(move)) {
					moves.add(move);
				}

				// stop ray on any occupied square (own or enemy)
				if (!Piece.isEmpty(board[to])) {
					break;
				}
			}
		}
		return moves;
	}

	/**
	 * Used for adding legal white castling moves to the move list.
	 *
	 * @param moves the current move list to append castling moves to.
	 * @return the move list including any valid castling moves.
	 */
	private MoveList addWhiteCastlingMoves(MoveList moves) {
		if (whiteKingInCheck()) {
			return moves;
		}

		if (chess960) {
			return addWhite960CastlingMoves(moves);
		}
		return addWhiteStandardCastlingMoves(moves);
	}

	/**
	 * Used for adding standard chess white castling moves to the move list.
	 *
	 * @param moves the move list to append to.
	 * @return the move list including any valid castling moves.
	 */
	private MoveList addWhiteStandardCastlingMoves(MoveList moves) {
		if (whiteKingside != Field.NO_SQUARE
				&& Piece.isEmpty(board[Field.F1])
				&& Piece.isEmpty(board[Field.G1])
				&& !attackedByBlack(Field.F1)
				&& !attackedByBlack(Field.G1)) {
			moves.add(Move.of(whiteKing, whiteKingside));
		}

		if (whiteQueenside != Field.NO_SQUARE
				&& Piece.isEmpty(board[Field.D1])
				&& Piece.isEmpty(board[Field.C1])
				&& Piece.isEmpty(board[Field.B1])
				&& !attackedByBlack(Field.D1)
				&& !attackedByBlack(Field.C1)) {
			moves.add(Move.of(whiteKing, whiteQueenside));
		}

		return moves;
	}

	/**
	 * Used for generating white Chess960 castling moves.
	 *
	 * @param moves the current move list to append castling moves to.
	 * @return the move list including any valid castling moves.
	 */
	private MoveList addWhite960CastlingMoves(MoveList moves) {
		addWhiteCastlingMove(moves, whiteKingside, Field.WHITE_KINGSIDE_CASTLE_KING_TO_INDEX,
				Field.WHITE_KINGSIDE_CASTLE_ROOK_TO_INDEX);
		addWhiteCastlingMove(moves, whiteQueenside, Field.WHITE_QUEENSIDE_CASTLE_KING_TO_INDEX,
				Field.WHITE_QUEENSIDE_CASTLE_ROOK_TO_INDEX);
		return moves;
	}

	/**
	 * Used for attempting to add a single white castling move if legal under
	 * Chess960.
	 *
	 * @param moves      the move list to append to.
	 * @param rookSquare the starting square of the rook.
	 * @param kingTarget the destination square of the king (e.g. G1 or C1).
	 * @param rookTarget the destination square of the rook (e.g. F1 or D1).
	 */
	private void addWhiteCastlingMove(MoveList moves,
			byte rookSquare,
			int kingTarget,
			int rookTarget) {
		if (rookSquare == Field.NO_SQUARE)
			return;

		// 1) King's path: from whiteKing → kingTarget (excluding rookSquare) must be
		// empty and not attacked.
		if (!isPathClearExcluding(whiteKing, kingTarget, rookSquare)) {
			return;
		}

		if (attackedByBlack(kingTarget)) {
			return;
		}

		for (int sq : squaresBetween(whiteKing, kingTarget)) {
			if (attackedByBlack(sq))
				return;
		}

		// 2) Rook's path: from rookSquare → rookTarget (excluding whiteKing) must be
		// empty.
		if (!isPathClearExcluding(rookSquare, rookTarget, whiteKing))
			return;

		// All conditions satisfied → add castling move (king→rookSquare,
		// rook→rookTarget)
		moves.add(Move.of(whiteKing, rookSquare));
	}

	/**
	 * Used for adding promotion and normal move options for a white pawn.
	 *
	 * @param moves the list to which valid moves will be added
	 * @param from  the starting square of the pawn
	 * @param to    the target square of the move
	 */
	private void addWhitePawnOptions(MoveList moves, byte from, byte to) {
		short test = Move.of(from, to);
		if (!isLegalWhiteMove(test)) {
			return;
		}

		if (Field.isOn8thRank(to)) {
			moves.add(Move.of(from, to, Move.PROMOTION_QUEEN));
			moves.add(Move.of(from, to, Move.PROMOTION_ROOK));
			moves.add(Move.of(from, to, Move.PROMOTION_BISHOP));
			moves.add(Move.of(from, to, Move.PROMOTION_KNIGHT));
		} else {
			moves.add(test);
		}
	}

	/**
	 * Used for generating all legal pawn moves for a white pawn at a given index.
	 *
	 * @param moves the list to which valid pawn moves will be added
	 * @param index the board index of the white pawn
	 * @return the updated move list containing all valid pawn moves
	 */
	private MoveList addWhitePawnMoves(MoveList moves, byte index) {
		// pawn pushes
		byte[] pushRay = Field.PAWN_PUSH_WHITE[index];
		for (int i = 0; i < pushRay.length; i++) {
			byte to = pushRay[i];
			if (Piece.isEmpty(board[to])) {
				addWhitePawnOptions(moves, index, to);
			} else {
				// blocked - stop looking further
				break;
			}
		}

		// pawn captures (excluding en passant)
		byte[] capRay = Field.PAWN_CAPTURE_WHITE[index];
		for (int i = 0; i < capRay.length; i++) {
			byte to = capRay[i];
			if (Piece.isBlack(board[to])) {
				addWhitePawnOptions(moves, index, to);
			}
		}

		return moves;
	}

	/**
	 * Used for adding en passant moves for white pawns to the given move list.
	 *
	 * @param moves the list to which legal en passant moves will be added
	 * @return the updated move list containing any valid en passant moves
	 */
	private MoveList addWhiteEnPassantMoves(MoveList moves) {
		if (enPassant == Field.NO_SQUARE) {
			return moves;
		}

		byte uprank = Field.uprank(enPassant);
		byte left = Field.leftOf(uprank);
		byte right = Field.rightOf(uprank);

		short lefttakes = Move.of(left, enPassant);
		short righttakes = Move.of(right, enPassant);

		board[uprank] = Piece.EMPTY;

		if (Field.isOn5thRank(left) && board[left] == Piece.WHITE_PAWN && isLegalWhiteMove(lefttakes)) {
			moves.add(lefttakes);
		}

		if (Field.isOn5thRank(right) && board[right] == Piece.WHITE_PAWN && isLegalWhiteMove(righttakes)) {
			moves.add(righttakes);
		}

		board[uprank] = Piece.BLACK_PAWN;

		return moves;
	}

	/**
	 * Used for checking if a white move is legal based on king safety.
	 *
	 * @param move the move to be validated
	 * @return true if the move does not leave the white king in check
	 */
	private boolean isLegalWhiteMove(short move) {
		byte movefrom = Move.getFromIndex(move);
		byte moveto = Move.getToIndex(move);

		byte buffer = board[moveto];

		board[moveto] = board[movefrom];
		board[movefrom] = Piece.EMPTY;

		boolean legal = !whiteKingInCheck();

		board[movefrom] = board[moveto];
		board[moveto] = buffer;

		return legal;
	}

	/**
	 * Used for generating every legal Black move from the current position.
	 *
	 * @param moves the list to populate
	 * @return the populated {@code moves} list
	 */
	private MoveList getBlackMoves(MoveList moves) {
		addBlackEnPassantMoves(moves);
		addBlackCastlingMoves(moves);
		for (byte i = 0; i < board.length; i++) {
			byte piece = board[i];
			if (piece == Piece.BLACK_PAWN) {
				addBlackPawnMoves(moves, i);
			} else if (piece == Piece.BLACK_KING) {
				addBlackKingMoves(moves, i, Field.NEIGHBORS);
			} else if (piece == Piece.BLACK_ROOK) {
				addBlackSlidingMoves(moves, i, Field.LINES);
			} else if (piece == Piece.BLACK_BISHOP) {
				addBlackSlidingMoves(moves, i, Field.DIAGONALS);
			} else if (piece == Piece.BLACK_KNIGHT) {
				addBlackStaticMoves(moves, i, Field.JUMPS);
			} else if (piece == Piece.BLACK_QUEEN) {
				addBlackSlidingMoves(moves, i, Field.DIAGONALS);
				addBlackSlidingMoves(moves, i, Field.LINES);
			}
		}
		return moves;
	}

	/**
	 * Used for adding all legal non-sliding moves (knight or king) for a Black
	 * piece.
	 *
	 * @param moves   the list to append to
	 * @param index   the piece's square
	 * @param options pre-computed destination offsets
	 * @return the updated {@code moves} list
	 */
	private MoveList addBlackStaticMoves(MoveList moves, byte index, byte[][] options) {
		for (byte to : options[index]) {
			if (Piece.isBlack(board[to]))
				continue;
			short move = Move.of(index, to);
			if (isLegalBlackMove(move)) {
				moves.add(move);
			}
		}
		return moves;
	}

	/**
	 * Used for adding all legal king moves for Black while ensuring the king stays
	 * out of check.
	 *
	 * @param moves   the list to append to
	 * @param index   the king's square
	 * @param options one-square neighbor offsets
	 * @return the updated {@code moves} list
	 */
	private MoveList addBlackKingMoves(MoveList moves, byte index, byte[][] options) {
		for (byte to : options[index]) {
			if (Piece.isBlack(board[to]))
				continue;
			short move = Move.of(index, to);
			blackKing = to;
			if (isLegalBlackMove(move)) {
				moves.add(move);
			}
			blackKing = index;
		}
		return moves;
	}

	/**
	 * Used for adding all legal sliding moves (bishop, rook, queen) for a Black
	 * piece.
	 *
	 * @param moves   the list to append to
	 * @param index   the piece's square
	 * @param options directional rays generated per square
	 * @return the updated {@code moves} list
	 */
	private MoveList addBlackSlidingMoves(MoveList moves, byte index, byte[][][] options) {
		// for each direction ray from 'index'
		for (byte[] ray : options[index]) {
			// walk square by square
			for (byte to : ray) {
				short move = Move.of(index, to);

				// if empty or enemy‐occupied, try the move
				if (!Piece.isBlack(board[to]) && isLegalBlackMove(move)) {
					moves.add(move);
				}

				// stop ray on any occupied square (own or enemy)
				if (!Piece.isEmpty(board[to])) {
					break;
				}
			}
		}
		return moves;
	}

	/**
	 * Used for adding every legal Black castling move (standard or Chess960) to
	 * {@code moves}.
	 *
	 * @param moves the list to append to
	 * @return the updated {@code moves} list
	 */
	private MoveList addBlackCastlingMoves(MoveList moves) {
		// Can't castle out of check
		if (blackKingInCheck()) {
			return moves;
		}

		if (chess960) {
			return addBlack960CastlingMoves(moves);
		}
		return addBlackStandardCastlingMoves(moves);
	}

	/**
	 * Used for adding standard-chess Black castling moves to {@code moves}.
	 *
	 * @param moves the list to append to
	 * @return the updated {@code moves} list
	 */
	private MoveList addBlackStandardCastlingMoves(MoveList moves) {
		// Kingside castling to G8
		if (blackKingside != Field.NO_SQUARE
				&& Piece.isEmpty(board[Field.F8])
				&& Piece.isEmpty(board[Field.G8])
				&& !attackedByWhite(Field.F8)
				&& !attackedByWhite(Field.G8)) {
			moves.add(Move.of(blackKing, blackKingside));
		}

		// Queenside castling to C8
		if (blackQueenside != Field.NO_SQUARE
				&& Piece.isEmpty(board[Field.D8])
				&& Piece.isEmpty(board[Field.C8])
				&& Piece.isEmpty(board[Field.B8])
				&& !attackedByWhite(Field.D8)
				&& !attackedByWhite(Field.C8)) {
			moves.add(Move.of(blackKing, blackQueenside));
		}

		return moves;
	}

	/**
	 * Used for adding Black Chess960 castling moves to {@code moves}.
	 *
	 * @param moves the list to append to
	 * @return the updated {@code moves} list
	 */
	private MoveList addBlack960CastlingMoves(MoveList moves) {
		addBlackCastlingMove(moves, blackKingside, Field.BLACK_KINGSIDE_CASTLE_KING_TO_INDEX,
				Field.BLACK_KINGSIDE_CASTLE_ROOK_TO_INDEX);
		addBlackCastlingMove(moves, blackQueenside, Field.BLACK_QUEENSIDE_CASTLE_KING_TO_INDEX,
				Field.BLACK_QUEENSIDE_CASTLE_ROOK_TO_INDEX);
		return moves;
	}

	/**
	 * Used for attempting to add a single black castling move if legal under
	 * Chess960.
	 *
	 * @param moves      the move list to append to.
	 * @param rookSquare the starting square of the rook.
	 * @param kingTarget the destination square of the king (e.g. G8 or C8).
	 * @param rookTarget the destination square of the rook (e.g. F8 or D8).
	 */
	private void addBlackCastlingMove(MoveList moves,
			byte rookSquare,
			int kingTarget,
			int rookTarget) {
		if (rookSquare == Field.NO_SQUARE)
			return;

		// 1) King's path: from blackKing → kingTarget (excluding rookSquare) must be
		// empty and not attacked.
		if (!isPathClearExcluding(blackKing, kingTarget, rookSquare)) {
			return;
		}

		if (attackedByWhite(kingTarget)) {
			return;
		}

		for (int sq : squaresBetween(blackKing, kingTarget)) {
			if (attackedByWhite(sq))
				return;
		}

		// 2) Rook's path: from rookSquare → rookTarget (excluding blackKing) must be
		// empty.
		if (!isPathClearExcluding(rookSquare, rookTarget, blackKing))
			return;

		// All conditions satisfied → add castling move (king→rookSquare,
		// rook→rookTarget)
		moves.add(Move.of(blackKing, rookSquare));
	}

	/**
	 * Used for adding promotion and quiet-move options for a Black pawn to
	 * {@code moves}.
	 *
	 * @param moves the list to append to
	 * @param from  the pawn's origin square
	 * @param to    the candidate destination square
	 */
	private void addBlackPawnOptions(MoveList moves, byte from, byte to) {
		short test = Move.of(from, to);
		if (!isLegalBlackMove(test))
			return;

		if (Field.isOn1stRank(to)) {
			moves.add(Move.of(from, to, Move.PROMOTION_QUEEN));
			moves.add(Move.of(from, to, Move.PROMOTION_ROOK));
			moves.add(Move.of(from, to, Move.PROMOTION_BISHOP));
			moves.add(Move.of(from, to, Move.PROMOTION_KNIGHT));
		} else {
			moves.add(test);
		}
	}

	/**
	 * Used for generating all legal (non-en-passant) pawn moves for a Black pawn at
	 * {@code index}.
	 *
	 * @param moves the list to append to
	 * @param index the pawn's square
	 * @return the same {@code moves} instance for chaining
	 */
	private MoveList addBlackPawnMoves(MoveList moves, byte index) {
		// pawn pushes
		byte[] pushRay = Field.PAWN_PUSH_BLACK[index];
		for (int i = 0; i < pushRay.length; i++) {
			byte to = pushRay[i];
			if (Piece.isEmpty(board[to])) {
				addBlackPawnOptions(moves, index, to);
			} else {
				// blocked - stop looking further
				break;
			}
		}

		// pawn captures (excluding en passant)
		byte[] capRay = Field.PAWN_CAPTURE_BLACK[index];
		for (int i = 0; i < capRay.length; i++) {
			byte to = capRay[i];
			if (Piece.isWhite(board[to])) {
				addBlackPawnOptions(moves, index, to);
			}
		}

		return moves;
	}

	/**
	 * Used for appending legal Black en-passant captures to {@code moves}.
	 *
	 * @param moves the list to append to
	 * @return the updated {@code moves} list
	 */
	private MoveList addBlackEnPassantMoves(MoveList moves) {
		if (enPassant == Field.NO_SQUARE) {
			return moves;
		}

		byte down = Field.downrank(enPassant);
		byte left = Field.leftOf(down);
		byte right = Field.rightOf(down);

		short lefttakes = Move.of(left, enPassant);
		short righttakes = Move.of(right, enPassant);

		board[down] = Piece.EMPTY;

		if (Field.isOn4thRank(left) && board[left] == Piece.BLACK_PAWN && isLegalBlackMove(lefttakes)) {
			moves.add(lefttakes);
		}

		if (Field.isOn4thRank(right) && board[right] == Piece.BLACK_PAWN && isLegalBlackMove(righttakes)) {
			moves.add(righttakes);
		}

		board[down] = Piece.WHITE_PAWN;

		return moves;
	}

	/**
	 * Used for checking if a black move is legal based on king safety.
	 *
	 * @param move the move to be validated
	 * @return true if the move does not leave the black king in check
	 */
	private boolean isLegalBlackMove(short move) {
		byte movefrom = Move.getFromIndex(move);
		byte moveto = Move.getToIndex(move);

		byte buffer = board[moveto];

		board[moveto] = board[movefrom];
		board[movefrom] = Piece.EMPTY;

		boolean legal = !blackKingInCheck();

		board[movefrom] = board[moveto];
		board[moveto] = buffer;

		return legal;
	}

	/**
	 * Used for performing a perft search to count all legal moves up to a given
	 * depth.
	 * <p>
	 * This method is a convenience wrapper that starts the perft search from the
	 * current position.
	 * </p>
	 *
	 * @param depth the depth to search down to
	 * @return the total number of legal moves at the specified depth
	 */
	public long perft(int depth) {
		if (depth < 0) {
			throw new IllegalArgumentException("Depth (" + depth + ") must be non-negative");
		}
		if (depth == 0) {
			return 1;
		}
		return perft(depth, this);
	}

	/**
	 * Used for performing a perft search to count all legal moves up to a given
	 * depth.
	 * <p>
	 * This method recursively counts all legal moves from the current position
	 * down to the specified depth, returning the total number of leaf nodes.
	 * </p>
	 *
	 * @param depth    the depth to search down to
	 * @param position the current position to evaluate
	 * @return the total number of legal moves at the specified depth
	 */
	private static long perft(int depth, Position position) {
		if (depth == 1) {
			return position.getMoves().size();
		}
		long amount = 0;
		MoveList moves = position.getMoves();
		for (int i = 0; i < moves.size(); i++) {
			Position next = position.copyOf();
			next.play(moves.moves[i]);
			amount += perft(depth - 1, next);
		}
		return amount;
	}

	/**
	 * Used for returning the total centipawn value of all White pieces on the
	 * board.
	 *
	 * @return total centipawn value of all White pieces on the board.
	 */
	public int countWhiteMaterial() {
		int sum = 0;
		for (byte piece : board) {
			if (Piece.isWhite(piece)) {
				sum += Piece.getValue(piece);
			}
		}
		return sum;
	}

	/**
	 * Used for returning the total centipawn value of all Black pieces on the
	 * board.
	 *
	 * @return total centipawn value of all Black pieces on the board.
	 */
	public int countBlackMaterial() {
		int sum = 0;
		for (byte piece : board) {
			if (Piece.isBlack(piece)) {
				sum += Piece.getValue(piece);
			}
		}
		return sum;
	}

	/**
	 * Used for returning the total centipawn value of all pieces on the board,
	 * regardless of colour.
	 *
	 * @return total centipawn value of all pieces.
	 */
	public int countTotalMaterial() {
		return countWhiteMaterial() + countBlackMaterial();
	}

	/**
	 * Used for returning the centipawn balance (White minus Black).
	 *
	 * @return centipawn balance (White minus Black).
	 */
	public int materialDiscrepancy() {
		return countWhiteMaterial() - countBlackMaterial();
	}

	/**
	 * Used for counting the number of White pieces currently on the board.
	 *
	 * @return number of White pieces.
	 */
	public int countWhitePieces() {
		int count = 0;
		for (byte piece : board) {
			if (Piece.isWhite(piece)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Used for counting the number of Black pieces currently on the board.
	 *
	 * @return number of Black pieces.
	 */
	public int countBlackPieces() {
		int count = 0;
		for (byte piece : board) {
			if (Piece.isBlack(piece)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Used for returning the total number of pieces on the board.
	 *
	 * @return total number of pieces.
	 */
	public int countTotalPieces() {
		return countWhitePieces() + countBlackPieces();
	}

	/**
	 * Used for returning the piece-count balance (White minus Black).
	 *
	 * @return piece-count balance (White minus Black).
	 */
	public int countPieceDiscrepancy() {
		return countWhitePieces() - countBlackPieces();
	}

	/**
	 * Used for generating all successor positions by playing each legal move.
	 *
	 * @return an array containing a Position for each legal move.
	 */
	public Position[] generateSubPositions() {
		MoveList moveList = getMoves();
		Position[] positions = new Position[moveList.size()];
		for (int i = 0; i < positions.length; i++) {
			positions[i] = copyOf().play(moveList.moves[i]);
		}
		return positions;
	}

	/**
	 * Used for checking if the given move is legal in this position.
	 *
	 * @param move the move to check
	 * @return true if the move is legal in this position; false otherwise
	 */
	public boolean isLegalMove(short move) {
		MoveList moveList = getMoves();
		for (int i = 0; i < moveList.size; i++) {
			if (Move.equals(moveList.moves[i], move)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Used for retrieving the full-move number (increments after each Black move).
	 *
	 * @return the full-move number
	 */
	public short getFullMove() {
		return fullMove;
	}

	/**
	 * Used for retrieving the half-move clock (since the last capture or pawn
	 * move).
	 *
	 * @return the half-move clock
	 */
	public short getHalfMove() {
		return halfMove;
	}

	/**
	 * Used for retrieving the en-passant target square.
	 *
	 * @return the en-passant target square
	 */
	public byte getEnPassant() {
		return enPassant;
	}

	/**
	 * Used for retrieving White's king square index.
	 *
	 * @return the White king index
	 */
	public byte getWhiteKing() {
		return whiteKing;
	}

	/**
	 * Used for retrieving Black's king square index.
	 *
	 * @return the Black king index
	 */
	public byte getBlackKing() {
		return blackKing;
	}

	/**
	 * Used for retrieving the White kingside-castling rook square (or
	 * {@code NO_SQUARE}).
	 *
	 * @return the White kingside rook square
	 */
	public byte getWhiteKingside() {
		return whiteKingside;
	}

	/**
	 * Used for retrieving the White queenside-castling rook square (or
	 * {@code NO_SQUARE}).
	 *
	 * @return the White queenside rook square
	 */
	public byte getWhiteQueenside() {
		return whiteQueenside;
	}

	/**
	 * Used for retrieving the Black kingside-castling rook square (or
	 * {@code NO_SQUARE}).
	 *
	 * @return the Black kingside rook square
	 */
	public byte getBlackKingside() {
		return blackKingside;
	}

	/**
	 * Used for retrieving the Black queenside-castling rook square (or
	 * {@code NO_SQUARE}).
	 *
	 * @return the Black queenside rook square
	 */
	public byte getBlackQueenside() {
		return blackQueenside;
	}

	/**
	 * Used for checking whether it is White's turn.
	 *
	 * @return {@code true} if White moves next
	 */
	public boolean isWhiteTurn() {
		return whitesTurn;
	}

	/**
	 * Used for checking whether it is Black's turn.
	 *
	 * @return {@code true} if Black moves next
	 */
	public boolean isBlackTurn() {
		return !whitesTurn;
	}

	/**
	 * Used for checking whether this position follows Chess960 castling rules.
	 *
	 * @return {@code true} if the position is Chess960
	 */
	public boolean isChess960() {
		return chess960;
	}

	/**
	 * Used for obtaining a defensive copy of the board array.
	 *
	 * @return a copy of the board representation
	 */
	public byte[] getBoard() {
		return Arrays.copyOf(board, board.length);
	}

	/**
	 * Compares this position to another for natural ordering.
	 * <p>
	 * First, the board arrays are compared. If equal, turn and castling flags
	 * are compared. Finally, en passant, king positions, and move counters
	 * are compared.
	 * </p>
	 *
	 * @param other the position to compare against
	 * @return a negative integer, zero, or a positive integer as this position
	 *         is less than, equal to, or greater than the specified position
	 * @throws NullPointerException if {@code other} is {@code null}
	 */
	@Override
	public int compareTo(Position other) {
		if (other == null) {
			throw new NullPointerException("other");
		}
		int result = compareCoreFields(other);
		if (result != 0) {
			return result;
		}
		return compareAdditionalFields(other);
	}

	/**
	 * Compares the core fields of two positions: board layout, side to move,
	 * and castling rights.
	 *
	 * @param other the position to compare against
	 * @return a non-zero comparison result if any core fields differ,
	 *         otherwise zero
	 */
	private int compareCoreFields(Position other) {
		// Compare piece placement
		int cmp = Arrays.compare(board, other.board);
		if (cmp != 0)
			return cmp;

		// Compare side to move
		if (whitesTurn != other.whitesTurn) {
			return whitesTurn ? 1 : -1;
		}
		// Compare Chess960 flag
		if (chess960 != other.chess960) {
			return chess960 ? 1 : -1;
		}

		// Compare castling rights
		cmp = Byte.compare(whiteKingside, other.whiteKingside);
		if (cmp != 0)
			return cmp;
		cmp = Byte.compare(whiteQueenside, other.whiteQueenside);
		if (cmp != 0)
			return cmp;
		cmp = Byte.compare(blackKingside, other.blackKingside);
		if (cmp != 0)
			return cmp;
		return Byte.compare(blackQueenside, other.blackQueenside);
	}

	/**
	 * Compares additional fields of two positions: en passant target,
	 * king positions, and move counters (half-move and full-move).
	 *
	 * @param other the position to compare against
	 * @return a non-zero comparison result if any additional fields differ,
	 *         otherwise zero
	 */
	private int compareAdditionalFields(Position other) {
		// Compare en passant target square
		int cmp = Byte.compare(enPassant, other.enPassant);
		if (cmp != 0) {
			return cmp;
		}

		// Compare king positions
		cmp = Byte.compare(whiteKing, other.whiteKing);
		if (cmp != 0) {
			return cmp;
		}

		cmp = Byte.compare(blackKing, other.blackKing);
		if (cmp != 0) {
			return cmp;
		}

		// Compare move counters
		cmp = Short.compare(halfMove, other.halfMove);
		if (cmp != 0) {
			return cmp;
		}

		return Short.compare(fullMove, other.fullMove);
	}

	/**
	 * Used for testing structural equality between two positions.
	 *
	 * @param obj the object to compare with
	 * @return {@code true} if all fields match, otherwise {@code false}
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (!(obj instanceof Position other)) {
			return false;
		}

		return Arrays.equals(board, other.board) &&
				whitesTurn == other.whitesTurn &&
				chess960 == other.chess960 &&
				whiteKingside == other.whiteKingside &&
				whiteQueenside == other.whiteQueenside &&
				blackKingside == other.blackKingside &&
				blackQueenside == other.blackQueenside &&
				enPassant == other.enPassant &&
				whiteKing == other.whiteKing &&
				blackKing == other.blackKing &&
				halfMove == other.halfMove &&
				fullMove == other.fullMove;
	}

	/**
	 * Used for computing a hash code consistent with {@link #equals(Object)}.
	 *
	 * @return the hash code for this position
	 */
	@Override
	public int hashCode() {
		int result = Arrays.hashCode(board);
		result = 31 * result + Boolean.hashCode(whitesTurn);
		result = 31 * result + Boolean.hashCode(chess960);
		result = 31 * result + Byte.hashCode(whiteKingside);
		result = 31 * result + Byte.hashCode(whiteQueenside);
		result = 31 * result + Byte.hashCode(blackKingside);
		result = 31 * result + Byte.hashCode(blackQueenside);
		result = 31 * result + Byte.hashCode(enPassant);
		result = 31 * result + Byte.hashCode(whiteKing);
		result = 31 * result + Byte.hashCode(blackKing);
		result = 31 * result + Short.hashCode(halfMove);
		result = 31 * result + Short.hashCode(fullMove);
		return result;
	}

	/**
	 * Returns a 64-bit signature of the full position state.
	 *
	 * <p>
	 * This is intended as a fast, allocation-free key for memoization and caching.
	 * It incorporates the same state components as {@link #equals(Object)} /
	 * {@link #hashCode()} (board, turn, castling rights, en-passant, king squares,
	 * and move counters).
	 * </p>
	 *
	 * @return 64-bit signature for the current position state
	 */
	public long signature() {
		long h = 1469598103934665603L; // FNV-1a 64-bit offset basis
		for (byte piece : board) {
			h ^= (piece & 0xFFL);
			h *= 1099511628211L; // FNV prime
		}

		h ^= whitesTurn ? 1L : 0L;
		h *= 1099511628211L;
		h ^= chess960 ? 1L : 0L;
		h *= 1099511628211L;

		h ^= (whiteKingside & 0xFFL);
		h *= 1099511628211L;
		h ^= (whiteQueenside & 0xFFL);
		h *= 1099511628211L;
		h ^= (blackKingside & 0xFFL);
		h *= 1099511628211L;
		h ^= (blackQueenside & 0xFFL);
		h *= 1099511628211L;
		h ^= (enPassant & 0xFFL);
		h *= 1099511628211L;
		h ^= (whiteKing & 0xFFL);
		h *= 1099511628211L;
		h ^= (blackKing & 0xFFL);
		h *= 1099511628211L;

		h ^= (halfMove & 0xFFFFL);
		h *= 1099511628211L;
		h ^= (fullMove & 0xFFFFL);
		h *= 1099511628211L;

		return h;
	}

}
