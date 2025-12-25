package chess.core;

/**
 * Used for representing and working with individual chessboard squares
 * (fields).
 *
 * <p>
 * This utility class provides:
 * </p>
 * <ul>
 * <li>Constants for every square on an 8×8 board, such as {@link #A8},
 * {@link #H1}, etc.</li>
 * <li>A special {@link #NO_SQUARE} constant for cases where no valid square
 * exists.</li>
 * <li>Methods to convert between textual square notation (e.g. "e4") and
 * numeric indices (see {@link #ToIndex(String)}, {@link #ToIndex(char, char)},
 * and {@link #ToString(byte)}).</li>
 * <li>A method to check whether a given string is valid algebraic notation, via
 * {@link #IsField(String)}.</li>
 * <li>Helpers to retrieve file/rank characters, including both static
 * ({@link #GetFile(byte)}, {@link #GetRank(byte)}) and instance-level
 * ({@link #GetFile()}, {@link #GetRank()}) methods.</li>
 * <li>Functions for calculating x/y coordinates in both normal and inverted
 * orientations (e.g. {@link #getX(byte)}, {@link #getY(byte)},
 * {@link #getXInverted(byte)}, {@link #getYInverted(byte)}).</li>
 * <li>Convenience methods to check if a square is on a particular rank (e.g.
 * {@link #IsOn1stRank(byte)} through {@link #IsOn8thRank(byte)}).</li>
 * </ul>
 *
 * <p>
 * The squares are zero-indexed from {@link #A8} (0) through {@link #H1} (63).
 * Each file is numbered from 0 to 7 (i.e. <strong>a</strong> through
 * <strong>h</strong>), and each rank is numbered from 0 to 7 (i.e.
 * <strong>8</strong> down to <strong>1</strong>) when referring to the internal
 * representation. The class also offers {@link #NO_SQUARE} (-1) for invalid or
 * nonexistent squares.
 * </p>
 *
 * <p>
 * While most methods are static, there are also instance-level methods that
 * operate on a stored {@code Index} field (e.g., {@link #GetFile()} and
 * {@link #GetRank()}). However, you cannot directly instantiate this class from
 * outside; it is designed primarily for static usage and internal
 * representation of squares in chess logic.
 * </p>
 *
 * @implNote Designed primarily for internal use in chess move generation, board
 *           rendering, and square validations.
 */

public class Field {

	/**
	 * The index (-1) representing no square.
	 */
	public static final byte NO_SQUARE = -1;

	/**
	 * The index (0) representing the A8 square.
	 */
	public static final byte A8 = 0;

	/**
	 * The index (1) representing the B8 square.
	 */
	public static final byte B8 = 1;

	/**
	 * The index (2) representing the C8 square.
	 */
	public static final byte C8 = 2;

	/**
	 * The index (3) representing the D8 square.
	 */
	public static final byte D8 = 3;

	/**
	 * The index (4) representing the E8 square.
	 */
	public static final byte E8 = 4;

	/**
	 * The index (5) representing the F8 square.
	 */
	public static final byte F8 = 5;

	/**
	 * The index (6) representing the G8 square.
	 */
	public static final byte G8 = 6;

	/**
	 * The index (7) representing the H8 square.
	 */
	public static final byte H8 = 7;

	/**
	 * The index (8) representing the A7 square.
	 */
	public static final byte A7 = 8;

	/**
	 * The index (9) representing the B7 square.
	 */
	public static final byte B7 = 9;

	/**
	 * The index (10) representing the C7 square.
	 */
	public static final byte C7 = 10;

	/**
	 * The index (11) representing the D7 square.
	 */
	public static final byte D7 = 11;

	/**
	 * The index (12) representing the E7 square.
	 */
	public static final byte E7 = 12;

	/**
	 * The index (13) representing the F7 square.
	 */
	public static final byte F7 = 13;

	/**
	 * The index (14) representing the G7 square.
	 */
	public static final byte G7 = 14;

	/**
	 * The index (15) representing the H7 square.
	 */
	public static final byte H7 = 15;

	/**
	 * The index (16) representing the A6 square.
	 */
	public static final byte A6 = 16;

	/**
	 * The index (17) representing the B6 square.
	 */
	public static final byte B6 = 17;

	/**
	 * The index (18) representing the C6 square.
	 */
	public static final byte C6 = 18;

	/**
	 * The index (19) representing the D6 square.
	 */
	public static final byte D6 = 19;

	/**
	 * The index (20) representing the E6 square.
	 */
	public static final byte E6 = 20;

	/**
	 * The index (21) representing the F6 square.
	 */
	public static final byte F6 = 21;

	/**
	 * The index (22) representing the G6 square.
	 */
	public static final byte G6 = 22;

	/**
	 * The index (23) representing the H6 square.
	 */
	public static final byte H6 = 23;

	/**
	 * The index (24) representing the A5 square.
	 */
	public static final byte A5 = 24;

	/**
	 * The index (25) representing the B5 square.
	 */
	public static final byte B5 = 25;

	/**
	 * The index (26) representing the C5 square.
	 */
	public static final byte C5 = 26;

	/**
	 * The index (27) representing the D5 square.
	 */
	public static final byte D5 = 27;

	/**
	 * The index (28) representing the E5 square.
	 */
	public static final byte E5 = 28;

	/**
	 * The index (29) representing the F5 square.
	 */
	public static final byte F5 = 29;

	/**
	 * The index (30) representing the G5 square.
	 */
	public static final byte G5 = 30;

	/**
	 * The index (31) representing the H5 square.
	 */
	public static final byte H5 = 31;

	/**
	 * The index (32) representing the A4 square.
	 */
	public static final byte A4 = 32;

	/**
	 * The index (33) representing the B4 square.
	 */
	public static final byte B4 = 33;

	/**
	 * The index (34) representing the C4 square.
	 */
	public static final byte C4 = 34;

	/**
	 * The index (35) representing the D4 square.
	 */
	public static final byte D4 = 35;

	/**
	 * The index (36) representing the E4 square.
	 */
	public static final byte E4 = 36;

	/**
	 * The index (37) representing the F4 square.
	 */
	public static final byte F4 = 37;

	/**
	 * The index (38) representing the G4 square.
	 */
	public static final byte G4 = 38;

	/**
	 * The index (39) representing the H4 square.
	 */
	public static final byte H4 = 39;

	/**
	 * The index (40) representing the A3 square.
	 */
	public static final byte A3 = 40;

	/**
	 * The index (41) representing the B3 square.
	 */
	public static final byte B3 = 41;

	/**
	 * The index (42) representing the C3 square.
	 */
	public static final byte C3 = 42;

	/**
	 * The index (43) representing the D3 square.
	 */
	public static final byte D3 = 43;

	/**
	 * The index (44) representing the E3 square.
	 */
	public static final byte E3 = 44;

	/**
	 * The index (45) representing the F3 square.
	 */
	public static final byte F3 = 45;

	/**
	 * The index (46) representing the G3 square.
	 */
	public static final byte G3 = 46;

	/**
	 * The index (47) representing the H3 square.
	 */
	public static final byte H3 = 47;

	/**
	 * The index (48) representing the A2 square.
	 */
	public static final byte A2 = 48;

	/**
	 * The index (49) representing the B2 square.
	 */
	public static final byte B2 = 49;

	/**
	 * The index (50) representing the C2 square.
	 */
	public static final byte C2 = 50;

	/**
	 * The index (51) representing the D2 square.
	 */
	public static final byte D2 = 51;

	/**
	 * The index (52) representing the E2 square.
	 */
	public static final byte E2 = 52;

	/**
	 * The index (53) representing the F2 square.
	 */
	public static final byte F2 = 53;

	/**
	 * The index (54) representing the G2 square.
	 */
	public static final byte G2 = 54;

	/**
	 * The index (55) representing the H2 square.
	 */
	public static final byte H2 = 55;

	/**
	 * The index (56) representing the A1 square.
	 */
	public static final byte A1 = 56;

	/**
	 * The index (57) representing the B1 square.
	 */
	public static final byte B1 = 57;

	/**
	 * The index (58) representing the C1 square.
	 */
	public static final byte C1 = 58;

	/**
	 * The index (59) representing the D1 square.
	 */
	public static final byte D1 = 59;

	/**
	 * The index (60) representing the E1 square.
	 */
	public static final byte E1 = 60;

	/**
	 * The index (61) representing the F1 square.
	 */
	public static final byte F1 = 61;

	/**
	 * The index (62) representing the G1 square.
	 */
	public static final byte G1 = 62;

	/**
	 * The index (63) representing the H1 square.
	 */
	public static final byte H1 = 63;

	/**
	 * The board index from which the White rook starts when performing a kingside
	 * castle (traditionally on H1).
	 */
	public static final byte WHITE_KINGSIDE_CASTLE_ROOK_FROM_INDEX = H1;

	/**
	 * The board index from which the White rook starts when performing a queenside
	 * castle (traditionally on A1).
	 */
	public static final byte WHITE_QUEENSIDE_CASTLE_ROOK_FROM_INDEX = A1;

	/**
	 * The board index from which the Black rook starts when performing a kingside
	 * castle (traditionally on H8).
	 */
	public static final byte BLACK_KINGSIDE_CASTLE_ROOK_FROM_INDEX = H8;

	/**
	 * The board index from which the Black rook starts when performing a queenside
	 * castle (traditionally on A8).
	 */
	public static final byte BLACK_QUEENSIDE_CASTLE_ROOK_FROM_INDEX = A8;

	/**
	 * The board index to which the White rook moves when performing a kingside
	 * castle (traditionally on F1).
	 */
	public static final byte WHITE_KINGSIDE_CASTLE_ROOK_TO_INDEX = F1;

	/**
	 * The board index to which the White rook moves when performing a queenside
	 * castle (traditionally on D1).
	 */
	public static final byte WHITE_QUEENSIDE_CASTLE_ROOK_TO_INDEX = D1;

	/**
	 * The board index to which the Black rook moves when performing a kingside
	 * castle (traditionally on F8).
	 */
	public static final byte BLACK_KINGSIDE_CASTLE_ROOK_TO_INDEX = F8;

	/**
	 * The board index to which the Black rook moves when performing a queenside
	 * castle (traditionally on D8).
	 */
	public static final byte BLACK_QUEENSIDE_CASTLE_ROOK_TO_INDEX = D8;

	/**
	 * The board index to which the White king moves when performing a kingside
	 * castle (traditionally on G1).
	 */
	public static final byte WHITE_KINGSIDE_CASTLE_KING_TO_INDEX = G1;

	/**
	 * The board index to which the White king moves when performing a queenside
	 * castle (traditionally on C1).
	 */
	public static final byte WHITE_QUEENSIDE_CASTLE_KING_TO_INDEX = C1;

	/**
	 * The board index to which the Black king moves when performing a kingside
	 * castle (traditionally on G8).
	 */
	public static final byte BLACK_KINGSIDE_CASTLE_KING_TO_INDEX = G8;

	/**
	 * The board index to which the Black king moves when performing a queenside
	 * castle (traditionally on C8).
	 */
	public static final byte BLACK_QUEENSIDE_CASTLE_KING_TO_INDEX = C8;

	/**
	 * The index of the square where the White king starts in standard chess
	 * 
	 */
	public static final byte WHITE_KING_STANDARD_INDEX = E1;

	/**
	 * The index of the square where the White king starts in standard chess
	 * 
	 */
	public static final byte BLACK_KING_STANDARD_INDEX = E8;

	/**
	 * Structure:
	 * <ul>
	 * <li>The first dimension of the array represents the field index, which is the
	 * current position of a piece on the board.</li>
	 * <li>The second dimension represents up to the four possible diagonal
	 * directions a piece can move: northeast, southeast, southwest, and
	 * northwest.</li>
	 * <li>The third dimension is an array of field indices that a piece can move to
	 * in the corresponding direction.</li>
	 * </ul>
	 * 
	 * <p>
	 * Explanation with an example:
	 * </p>
	 * A bishop on E4 has four directions that it can go to. We get the directions
	 * by using: <blockquote>
	 * 
	 * <pre>
	 * byte[][] directions = DIAGONALS[E4];
	 * </pre>
	 * 
	 * </blockquote> Now, we have a array representing the northeast, southeast,
	 * southwest, and northwest directions. Empty direction arrays have been
	 * removed, so if the bishop were on F1 instead, it would only have the
	 * northwest and northeast direction. If we want to access the southeast
	 * direction that a bishop has on the E4 square, we do: <blockquote>
	 * 
	 * <pre>
	 * byte[] southeast = directions[1];
	 * </pre>
	 * 
	 * </blockquote> Finally, we have a array that that gives us the direction of
	 * the fields that the bishop can move to. The fields are: <blockquote>
	 * <ul>
	 * <li>F3</li>
	 * <li>G2</li>
	 * <li>H1</li> </blockquote>
	 * </ul>
	 * So, to check if a bishop on E4 can go to each square, we have to first check,
	 * if it can go to F3, then to G2 and finally to H1.
	 * 
	 * <p>
	 * One could also easily access them in a loop with:
	 * </p>
	 * 
	 * <blockquote>
	 * 
	 * <pre>
	 * for (int i = 0; i < DIAGONALS[E4][1].length; i++) {
	 * 	byte index = DIAGONALS[E4][1][i];
	 * 	// Do calculations
	 * }
	 * </pre>
	 * 
	 * </blockquote>
	 * 
	 * @implNote Used for chess move generation
	 * @implNote Directions that not contain indexes have been removed for
	 *           optimization
	 */
	protected static final byte[][][] DIAGONALS = { { { B7, C6, D5, E4, F3, G2, H1 } },
			{ { C7, D6, E5, F4, G3, H2 }, { A7 } }, { { D7, E6, F5, G4, H3 }, { B7, A6 } },
			{ { E7, F6, G5, H4 }, { C7, B6, A5 } }, { { F7, G6, H5 }, { D7, C6, B5, A4 } },
			{ { G7, H6 }, { E7, D6, C5, B4, A3 } }, { { H7 }, { F7, E6, D5, C4, B3, A2 } },
			{ { G7, F6, E5, D4, C3, B2, A1 } }, { { B8 }, { B6, C5, D4, E3, F2, G1 } },
			{ { C8 }, { C6, D5, E4, F3, G2, H1 }, { A6 }, { A8 } },
			{ { D8 }, { D6, E5, F4, G3, H2 }, { B6, A5 }, { B8 } },
			{ { E8 }, { E6, F5, G4, H3 }, { C6, B5, A4 }, { C8 } },
			{ { F8 }, { F6, G5, H4 }, { D6, C5, B4, A3 }, { D8 } },
			{ { G8 }, { G6, H5 }, { E6, D5, C4, B3, A2 }, { E8 } },
			{ { H8 }, { H6 }, { F6, E5, D4, C3, B2, A1 }, { F8 } }, { { G6, F5, E4, D3, C2, B1 }, { G8 } },
			{ { B7, C8 }, { B5, C4, D3, E2, F1 } }, { { C7, D8 }, { C5, D4, E3, F2, G1 }, { A5 }, { A7 } },
			{ { D7, E8 }, { D5, E4, F3, G2, H1 }, { B5, A4 }, { B7, A8 } },
			{ { E7, F8 }, { E5, F4, G3, H2 }, { C5, B4, A3 }, { C7, B8 } },
			{ { F7, G8 }, { F5, G4, H3 }, { D5, C4, B3, A2 }, { D7, C8 } },
			{ { G7, H8 }, { G5, H4 }, { E5, D4, C3, B2, A1 }, { E7, D8 } },
			{ { H7 }, { H5 }, { F5, E4, D3, C2, B1 }, { F7, E8 } }, { { G5, F4, E3, D2, C1 }, { G7, F8 } },
			{ { B6, C7, D8 }, { B4, C3, D2, E1 } }, { { C6, D7, E8 }, { C4, D3, E2, F1 }, { A4 }, { A6 } },
			{ { D6, E7, F8 }, { D4, E3, F2, G1 }, { B4, A3 }, { B6, A7 } },
			{ { E6, F7, G8 }, { E4, F3, G2, H1 }, { C4, B3, A2 }, { C6, B7, A8 } },
			{ { F6, G7, H8 }, { F4, G3, H2 }, { D4, C3, B2, A1 }, { D6, C7, B8 } },
			{ { G6, H7 }, { G4, H3 }, { E4, D3, C2, B1 }, { E6, D7, C8 } },
			{ { H6 }, { H4 }, { F4, E3, D2, C1 }, { F6, E7, D8 } }, { { G4, F3, E2, D1 }, { G6, F7, E8 } },
			{ { B5, C6, D7, E8 }, { B3, C2, D1 } }, { { C5, D6, E7, F8 }, { C3, D2, E1 }, { A3 }, { A5 } },
			{ { D5, E6, F7, G8 }, { D3, E2, F1 }, { B3, A2 }, { B5, A6 } },
			{ { E5, F6, G7, H8 }, { E3, F2, G1 }, { C3, B2, A1 }, { C5, B6, A7 } },
			{ { F5, G6, H7 }, { F3, G2, H1 }, { D3, C2, B1 }, { D5, C6, B7, A8 } },
			{ { G5, H6 }, { G3, H2 }, { E3, D2, C1 }, { E5, D6, C7, B8 } },
			{ { H5 }, { H3 }, { F3, E2, D1 }, { F5, E6, D7, C8 } }, { { G3, F2, E1 }, { G5, F6, E7, D8 } },
			{ { B4, C5, D6, E7, F8 }, { B2, C1 } }, { { C4, D5, E6, F7, G8 }, { C2, D1 }, { A2 }, { A4 } },
			{ { D4, E5, F6, G7, H8 }, { D2, E1 }, { B2, A1 }, { B4, A5 } },
			{ { E4, F5, G6, H7 }, { E2, F1 }, { C2, B1 }, { C4, B5, A6 } },
			{ { F4, G5, H6 }, { F2, G1 }, { D2, C1 }, { D4, C5, B6, A7 } },
			{ { G4, H5 }, { G2, H1 }, { E2, D1 }, { E4, D5, C6, B7, A8 } },
			{ { H4 }, { H2 }, { F2, E1 }, { F4, E5, D6, C7, B8 } }, { { G2, F1 }, { G4, F5, E6, D7, C8 } },
			{ { B3, C4, D5, E6, F7, G8 }, { B1 } }, { { C3, D4, E5, F6, G7, H8 }, { C1 }, { A1 }, { A3 } },
			{ { D3, E4, F5, G6, H7 }, { D1 }, { B1 }, { B3, A4 } },
			{ { E3, F4, G5, H6 }, { E1 }, { C1 }, { C3, B4, A5 } },
			{ { F3, G4, H5 }, { F1 }, { D1 }, { D3, C4, B5, A6 } },
			{ { G3, H4 }, { G1 }, { E1 }, { E3, D4, C5, B6, A7 } },
			{ { H3 }, { H1 }, { F1 }, { F3, E4, D5, C6, B7, A8 } }, { { G1 }, { G3, F4, E5, D6, C7, B8 } },
			{ { B2, C3, D4, E5, F6, G7, H8 } }, { { C2, D3, E4, F5, G6, H7 }, { A2 } },
			{ { D2, E3, F4, G5, H6 }, { B2, A3 } }, { { E2, F3, G4, H5 }, { C2, B3, A4 } },
			{ { F2, G3, H4 }, { D2, C3, B4, A5 } }, { { G2, H3 }, { E2, D3, C4, B5, A6 } },
			{ { H2 }, { F2, E3, D4, C5, B6, A7 } }, { { G2, F3, E4, D5, C6, B7, A8 } } };

	/**
	 * Structure:
	 * <ul>
	 * <li>The first dimension of the array represents the field index, which is the
	 * current position of a piece on the board.</li>
	 * <li>The second dimension represents up to the four possible file and rank
	 * directions a piece can move: north, east, south, and west.</li>
	 * <li>The third dimension is an array of field indices that a piece can move to
	 * in the corresponding direction.</li>
	 * </ul>
	 * 
	 * <p>
	 * Explanation with an example:
	 * </p>
	 * A rook on E4 has four directions that it can go to. We get the directions by
	 * using: <blockquote>
	 * 
	 * <pre>
	 * byte[][] directions = LINES[E4];
	 * </pre>
	 * 
	 * </blockquote> Now, we have a array representing the north, east, south, and
	 * west directions. Empty direction arrays have been removed, so if the rook
	 * were on H1 instead, it would only have the north and west direction. If we
	 * want to access the east direction that a bishop has on the E4 square, we do:
	 * <blockquote>
	 * 
	 * <pre>
	 * byte[] east = directions[1];
	 * </pre>
	 * 
	 * </blockquote> Finally, we have a array that that gives us the direction of
	 * the fields that the rook can move to. The fields are: <blockquote>
	 * <ul>
	 * <li>F4</li>
	 * <li>G4</li>
	 * <li>H4</li>
	 * </ul>
	 * </blockquote>
	 * So, to check if a rook on E4 can go to each square, we have to first check,
	 * if it can go to F4, then to G4 and finally to H4.
	 * 
	 * <p>
	 * One could also easily access them in a loop with:
	 * </p>
	 * 
	 * <blockquote>
	 * 
	 * <pre>
	 * for (int i = 0; i < LINES[E4][1].length; i++) {
	 * 	byte index = LINES[E4][1][i];
	 * 	// Do calculations
	 * }
	 * </pre>
	 * 
	 * </blockquote>
	 * 
	 * @implNote Used for chess move generation
	 * @implNote Directions that not contain indexes have been removed for
	 *           optimization
	 */
	protected static final byte[][][] LINES = { { { B8, C8, D8, E8, F8, G8, H8 }, { A7, A6, A5, A4, A3, A2, A1 } },
			{ { C8, D8, E8, F8, G8, H8 }, { B7, B6, B5, B4, B3, B2, B1 }, { A8 } },
			{ { D8, E8, F8, G8, H8 }, { C7, C6, C5, C4, C3, C2, C1 }, { B8, A8 } },
			{ { E8, F8, G8, H8 }, { D7, D6, D5, D4, D3, D2, D1 }, { C8, B8, A8 } },
			{ { F8, G8, H8 }, { E7, E6, E5, E4, E3, E2, E1 }, { D8, C8, B8, A8 } },
			{ { G8, H8 }, { F7, F6, F5, F4, F3, F2, F1 }, { E8, D8, C8, B8, A8 } },
			{ { H8 }, { G7, G6, G5, G4, G3, G2, G1 }, { F8, E8, D8, C8, B8, A8 } },
			{ { H7, H6, H5, H4, H3, H2, H1 }, { G8, F8, E8, D8, C8, B8, A8 } },
			{ { A8 }, { B7, C7, D7, E7, F7, G7, H7 }, { A6, A5, A4, A3, A2, A1 } },
			{ { B8 }, { C7, D7, E7, F7, G7, H7 }, { B6, B5, B4, B3, B2, B1 }, { A7 } },
			{ { C8 }, { D7, E7, F7, G7, H7 }, { C6, C5, C4, C3, C2, C1 }, { B7, A7 } },
			{ { D8 }, { E7, F7, G7, H7 }, { D6, D5, D4, D3, D2, D1 }, { C7, B7, A7 } },
			{ { E8 }, { F7, G7, H7 }, { E6, E5, E4, E3, E2, E1 }, { D7, C7, B7, A7 } },
			{ { F8 }, { G7, H7 }, { F6, F5, F4, F3, F2, F1 }, { E7, D7, C7, B7, A7 } },
			{ { G8 }, { H7 }, { G6, G5, G4, G3, G2, G1 }, { F7, E7, D7, C7, B7, A7 } },
			{ { H8 }, { H6, H5, H4, H3, H2, H1 }, { G7, F7, E7, D7, C7, B7, A7 } },
			{ { A7, A8 }, { B6, C6, D6, E6, F6, G6, H6 }, { A5, A4, A3, A2, A1 } },
			{ { B7, B8 }, { C6, D6, E6, F6, G6, H6 }, { B5, B4, B3, B2, B1 }, { A6 } },
			{ { C7, C8 }, { D6, E6, F6, G6, H6 }, { C5, C4, C3, C2, C1 }, { B6, A6 } },
			{ { D7, D8 }, { E6, F6, G6, H6 }, { D5, D4, D3, D2, D1 }, { C6, B6, A6 } },
			{ { E7, E8 }, { F6, G6, H6 }, { E5, E4, E3, E2, E1 }, { D6, C6, B6, A6 } },
			{ { F7, F8 }, { G6, H6 }, { F5, F4, F3, F2, F1 }, { E6, D6, C6, B6, A6 } },
			{ { G7, G8 }, { H6 }, { G5, G4, G3, G2, G1 }, { F6, E6, D6, C6, B6, A6 } },
			{ { H7, H8 }, { H5, H4, H3, H2, H1 }, { G6, F6, E6, D6, C6, B6, A6 } },
			{ { A6, A7, A8 }, { B5, C5, D5, E5, F5, G5, H5 }, { A4, A3, A2, A1 } },
			{ { B6, B7, B8 }, { C5, D5, E5, F5, G5, H5 }, { B4, B3, B2, B1 }, { A5 } },
			{ { C6, C7, C8 }, { D5, E5, F5, G5, H5 }, { C4, C3, C2, C1 }, { B5, A5 } },
			{ { D6, D7, D8 }, { E5, F5, G5, H5 }, { D4, D3, D2, D1 }, { C5, B5, A5 } },
			{ { E6, E7, E8 }, { F5, G5, H5 }, { E4, E3, E2, E1 }, { D5, C5, B5, A5 } },
			{ { F6, F7, F8 }, { G5, H5 }, { F4, F3, F2, F1 }, { E5, D5, C5, B5, A5 } },
			{ { G6, G7, G8 }, { H5 }, { G4, G3, G2, G1 }, { F5, E5, D5, C5, B5, A5 } },
			{ { H6, H7, H8 }, { H4, H3, H2, H1 }, { G5, F5, E5, D5, C5, B5, A5 } },
			{ { A5, A6, A7, A8 }, { B4, C4, D4, E4, F4, G4, H4 }, { A3, A2, A1 } },
			{ { B5, B6, B7, B8 }, { C4, D4, E4, F4, G4, H4 }, { B3, B2, B1 }, { A4 } },
			{ { C5, C6, C7, C8 }, { D4, E4, F4, G4, H4 }, { C3, C2, C1 }, { B4, A4 } },
			{ { D5, D6, D7, D8 }, { E4, F4, G4, H4 }, { D3, D2, D1 }, { C4, B4, A4 } },
			{ { E5, E6, E7, E8 }, { F4, G4, H4 }, { E3, E2, E1 }, { D4, C4, B4, A4 } },
			{ { F5, F6, F7, F8 }, { G4, H4 }, { F3, F2, F1 }, { E4, D4, C4, B4, A4 } },
			{ { G5, G6, G7, G8 }, { H4 }, { G3, G2, G1 }, { F4, E4, D4, C4, B4, A4 } },
			{ { H5, H6, H7, H8 }, { H3, H2, H1 }, { G4, F4, E4, D4, C4, B4, A4 } },
			{ { A4, A5, A6, A7, A8 }, { B3, C3, D3, E3, F3, G3, H3 }, { A2, A1 } },
			{ { B4, B5, B6, B7, B8 }, { C3, D3, E3, F3, G3, H3 }, { B2, B1 }, { A3 } },
			{ { C4, C5, C6, C7, C8 }, { D3, E3, F3, G3, H3 }, { C2, C1 }, { B3, A3 } },
			{ { D4, D5, D6, D7, D8 }, { E3, F3, G3, H3 }, { D2, D1 }, { C3, B3, A3 } },
			{ { E4, E5, E6, E7, E8 }, { F3, G3, H3 }, { E2, E1 }, { D3, C3, B3, A3 } },
			{ { F4, F5, F6, F7, F8 }, { G3, H3 }, { F2, F1 }, { E3, D3, C3, B3, A3 } },
			{ { G4, G5, G6, G7, G8 }, { H3 }, { G2, G1 }, { F3, E3, D3, C3, B3, A3 } },
			{ { H4, H5, H6, H7, H8 }, { H2, H1 }, { G3, F3, E3, D3, C3, B3, A3 } },
			{ { A3, A4, A5, A6, A7, A8 }, { B2, C2, D2, E2, F2, G2, H2 }, { A1 } },
			{ { B3, B4, B5, B6, B7, B8 }, { C2, D2, E2, F2, G2, H2 }, { B1 }, { A2 } },
			{ { C3, C4, C5, C6, C7, C8 }, { D2, E2, F2, G2, H2 }, { C1 }, { B2, A2 } },
			{ { D3, D4, D5, D6, D7, D8 }, { E2, F2, G2, H2 }, { D1 }, { C2, B2, A2 } },
			{ { E3, E4, E5, E6, E7, E8 }, { F2, G2, H2 }, { E1 }, { D2, C2, B2, A2 } },
			{ { F3, F4, F5, F6, F7, F8 }, { G2, H2 }, { F1 }, { E2, D2, C2, B2, A2 } },
			{ { G3, G4, G5, G6, G7, G8 }, { H2 }, { G1 }, { F2, E2, D2, C2, B2, A2 } },
			{ { H3, H4, H5, H6, H7, H8 }, { H1 }, { G2, F2, E2, D2, C2, B2, A2 } },
			{ { A2, A3, A4, A5, A6, A7, A8 }, { B1, C1, D1, E1, F1, G1, H1 } },
			{ { B2, B3, B4, B5, B6, B7, B8 }, { C1, D1, E1, F1, G1, H1 }, { A1 } },
			{ { C2, C3, C4, C5, C6, C7, C8 }, { D1, E1, F1, G1, H1 }, { B1, A1 } },
			{ { D2, D3, D4, D5, D6, D7, D8 }, { E1, F1, G1, H1 }, { C1, B1, A1 } },
			{ { E2, E3, E4, E5, E6, E7, E8 }, { F1, G1, H1 }, { D1, C1, B1, A1 } },
			{ { F2, F3, F4, F5, F6, F7, F8 }, { G1, H1 }, { E1, D1, C1, B1, A1 } },
			{ { G2, G3, G4, G5, G6, G7, G8 }, { H1 }, { F1, E1, D1, C1, B1, A1 } },
			{ { H2, H3, H4, H5, H6, H7, H8 }, { G1, F1, E1, D1, C1, B1, A1 } } };

	/**
	 * Structure:
	 * <ul>
	 * <li>The first dimension of the array represents the field index, which is the
	 * current position of a knight on the board.</li>
	 * <li>The second dimension is an array of field indices that the knight can
	 * jump to from the corresponding position.</li>
	 * </ul>
	 *
	 * <p>
	 * Explanation with an example:
	 * </p>
	 * A knight on <strong>E4</strong> has several possible jumps (up to 8,
	 * depending on whether some moves go off the board). We get the squares it can
	 * jump to by using: <blockquote>
	 * 
	 * <pre>
	 * byte[] knightMoves = Jumps[E4];
	 * </pre>
	 * 
	 * </blockquote> Now, <code>knightMoves</code> is an array of squares (as board
	 * indices) that a knight on E4 could move to. For instance, if we want to see
	 * whether the knight can jump to <strong>G5</strong>, we can check if G5 is in
	 * <code>Jumps[E4]</code>.
	 *
	 * <p>
	 * One could also easily access them in a loop with:
	 * </p>
	 * <blockquote>
	 * 
	 * <pre>
	 * for (int i = 0; i &lt; Jumps[E4].length; i++) {
	 * 	byte index = Jumps[E4][i];
	 * 	// Do calculations for a knight move from E4 to 'index'
	 * }
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @implNote Used for chess move generation. Unreachable or off-board jumps have
	 *           been removed for optimization.
	 */
	protected static final byte[][] JUMPS = { { C7, B6 }, { D7, C6, A6 }, { E7, A7, D6, B6 }, { F7, B7, E6, C6 },
			{ G7, C7, F6, D6 }, { H7, D7, G6, E6 }, { E7, H6, F6 }, { F7, G6 }, { C6, C8, B5 }, { D6, D8, C5, A5 },
			{ E6, E8, A6, A8, D5, B5 }, { F6, F8, B6, B8, E5, C5 }, { G6, G8, C6, C8, F5, D5 },
			{ H6, H8, D6, D8, G5, E5 }, { E6, E8, H5, F5 }, { F6, F8, G5 }, { C5, C7, B4, B8 },
			{ D5, D7, C4, C8, A4, A8 }, { E5, E7, A5, A7, D4, D8, B4, B8 }, { F5, F7, B5, B7, E4, E8, C4, C8 },
			{ G5, G7, C5, C7, F4, F8, D4, D8 }, { H5, H7, D5, D7, G4, G8, E4, E8 }, { E5, E7, H4, H8, F4, F8 },
			{ F5, F7, G4, G8 }, { C4, C6, B3, B7 }, { D4, D6, C3, C7, A3, A7 }, { E4, E6, A4, A6, D3, D7, B3, B7 },
			{ F4, F6, B4, B6, E3, E7, C3, C7 }, { G4, G6, C4, C6, F3, F7, D3, D7 }, { H4, H6, D4, D6, G3, G7, E3, E7 },
			{ E4, E6, H3, H7, F3, F7 }, { F4, F6, G3, G7 }, { C3, C5, B2, B6 }, { D3, D5, C2, C6, A2, A6 },
			{ E3, E5, A3, A5, D2, D6, B2, B6 }, { F3, F5, B3, B5, E2, E6, C2, C6 }, { G3, G5, C3, C5, F2, F6, D2, D6 },
			{ H3, H5, D3, D5, G2, G6, E2, E6 }, { E3, E5, H2, H6, F2, F6 }, { F3, F5, G2, G6 }, { C2, C4, B1, B5 },
			{ D2, D4, C1, C5, A1, A5 }, { E2, E4, A2, A4, D1, D5, B1, B5 }, { F2, F4, B2, B4, E1, E5, C1, C5 },
			{ G2, G4, C2, C4, F1, F5, D1, D5 }, { H2, H4, D2, D4, G1, G5, E1, E5 }, { E2, E4, H1, H5, F1, F5 },
			{ F2, F4, G1, G5 }, { C1, C3, B4 }, { D1, D3, C4, A4 }, { E1, E3, A1, A3, D4, B4 },
			{ F1, F3, B1, B3, E4, C4 }, { G1, G3, C1, C3, F4, D4 }, { H1, H3, D1, D3, G4, E4 }, { E1, E3, H4, F4 },
			{ F1, F3, G4 }, { C2, B3 }, { D2, C3, A3 }, { E2, A2, D3, B3 }, { F2, B2, E3, C3 }, { G2, C2, F3, D3 },
			{ H2, D2, G3, E3 }, { E2, H3, F3 }, { F2, G3 } };

	/**
	 * Structure:
	 * <ul>
	 * <li>The first dimension of the array represents the field index, which is the
	 * current position of a king on the board.</li>
	 * <li>The second dimension is an array of field indices (board squares) that a
	 * king can move to from the corresponding position.</li>
	 * </ul>
	 *
	 * <p>
	 * Explanation with an example:
	 * </p>
	 * A king on <strong>E4</strong> can move (at most) to any of the 8 surrounding
	 * squares, as long as those squares remain on the board. We retrieve these
	 * possible squares by using: <blockquote>
	 * 
	 * <pre>
	 * byte[] kingMoves = Neighbors[E4];
	 * </pre>
	 * 
	 * </blockquote> Now, <code>kingMoves</code> is an array of board indices where
	 * the king can legally move. For instance, if we want to check whether the king
	 * can move to <strong>D3</strong>, we can see whether <code>D3</code> is in
	 * <code>kingMoves</code>.
	 *
	 * <p>
	 * One could also easily loop over them as follows:
	 * </p>
	 * <blockquote>
	 * 
	 * <pre>
	 * for (byte destination : Neighbors[E4]) {
	 * 	// Calculate or generate a move from E4 to 'destination'
	 * }
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @implNote Used for chess move generation. Any off-board squares are omitted
	 *           for efficiency.
	 */
	protected static final byte[][] NEIGHBORS = { { B8, B7, A7 }, { C8, C7, B7, A7, A8 }, { D8, D7, C7, B7, B8 },
			{ E8, E7, D7, C7, C8 }, { F8, F7, E7, D7, D8 }, { G8, G7, F7, E7, E8 }, { H8, H7, G7, F7, F8 },
			{ H7, G7, G8 }, { A8, B8, B7, B6, A6 }, { B8, C8, C7, C6, B6, A6, A7, A8 },
			{ C8, D8, D7, D6, C6, B6, B7, B8 }, { D8, E8, E7, E6, D6, C6, C7, C8 }, { E8, F8, F7, F6, E6, D6, D7, D8 },
			{ F8, G8, G7, G6, F6, E6, E7, E8 }, { G8, H8, H7, H6, G6, F6, F7, F8 }, { H8, H6, G6, G7, G8 },
			{ A7, B7, B6, B5, A5 }, { B7, C7, C6, C5, B5, A5, A6, A7 }, { C7, D7, D6, D5, C5, B5, B6, B7 },
			{ D7, E7, E6, E5, D5, C5, C6, C7 }, { E7, F7, F6, F5, E5, D5, D6, D7 }, { F7, G7, G6, G5, F5, E5, E6, E7 },
			{ G7, H7, H6, H5, G5, F5, F6, F7 }, { H7, H5, G5, G6, G7 }, { A6, B6, B5, B4, A4 },
			{ B6, C6, C5, C4, B4, A4, A5, A6 }, { C6, D6, D5, D4, C4, B4, B5, B6 }, { D6, E6, E5, E4, D4, C4, C5, C6 },
			{ E6, F6, F5, F4, E4, D4, D5, D6 }, { F6, G6, G5, G4, F4, E4, E5, E6 }, { G6, H6, H5, H4, G4, F4, F5, F6 },
			{ H6, H4, G4, G5, G6 }, { A5, B5, B4, B3, A3 }, { B5, C5, C4, C3, B3, A3, A4, A5 },
			{ C5, D5, D4, D3, C3, B3, B4, B5 }, { D5, E5, E4, E3, D3, C3, C4, C5 }, { E5, F5, F4, F3, E3, D3, D4, D5 },
			{ F5, G5, G4, G3, F3, E3, E4, E5 }, { G5, H5, H4, H3, G3, F3, F4, F5 }, { H5, H3, G3, G4, G5 },
			{ A4, B4, B3, B2, A2 }, { B4, C4, C3, C2, B2, A2, A3, A4 }, { C4, D4, D3, D2, C2, B2, B3, B4 },
			{ D4, E4, E3, E2, D2, C2, C3, C4 }, { E4, F4, F3, F2, E2, D2, D3, D4 }, { F4, G4, G3, G2, F2, E2, E3, E4 },
			{ G4, H4, H3, H2, G2, F2, F3, F4 }, { H4, H2, G2, G3, G4 }, { A3, B3, B2, B1, A1 },
			{ B3, C3, C2, C1, B1, A1, A2, A3 }, { C3, D3, D2, D1, C1, B1, B2, B3 }, { D3, E3, E2, E1, D1, C1, C2, C3 },
			{ E3, F3, F2, F1, E1, D1, D2, D3 }, { F3, G3, G2, G1, F1, E1, E2, E3 }, { G3, H3, H2, H1, G1, F1, F2, F3 },
			{ H3, H1, G1, G2, G3 }, { A2, B2, B1 }, { B2, C2, C1, A1, A2 }, { C2, D2, D1, B1, B2 },
			{ D2, E2, E1, C1, C2 }, { E2, F2, F1, D1, D2 }, { F2, G2, G1, E1, E2 }, { G2, H2, H1, F1, F2 },
			{ H2, G1, G2 } };

	/**
	 * Used for determining legal pawn push moves for white pawns.
	 *
	 * <p>
	 * Structure:
	 * <ul>
	 * <li>The first dimension of the array represents the field index, which is the
	 * current position of a pawn on the board.</li>
	 * <li>The second dimension is an array of field indices where a white pawn can
	 * move forward (single or double push) from that square.</li>
	 * </ul>
	 *
	 * <p>
	 * Example usage:
	 * </p>
	 * 
	 * <pre>
	 * byte[] whitePushes = PAWN_PUSH_WHITE[E2];
	 * for (byte dest : whitePushes) {
	 * 	// Check or generate move from E2 to dest
	 * }
	 * </pre>
	 *
	 * @implNote Used for chess move generation. Omits off-board squares.
	 */
	protected static final byte[][] PAWN_PUSH_WHITE = {
			{}, {}, {}, {}, {}, {}, {}, {},
			{ A8 }, { B8 }, { C8 }, { D8 }, { E8 }, { F8 }, { G8 }, { H8 },
			{ A7 }, { B7 }, { C7 }, { D7 }, { E7 }, { F7 }, { G7 }, { H7 },
			{ A6 }, { B6 }, { C6 }, { D6 }, { E6 }, { F6 }, { G6 }, { H6 },
			{ A5 }, { B5 }, { C5 }, { D5 }, { E5 }, { F5 }, { G5 }, { H5 },
			{ A4 }, { B4 }, { C4 }, { D4 }, { E4 }, { F4 }, { G4 }, { H4 },
			{ A3, A4 }, { B3, B4 }, { C3, C4 }, { D3, D4 }, { E3, E4 }, { F3, F4 }, { G3, G4 }, { H3, H4 },
			{ A2 }, { B2 }, { C2 }, { D2 }, { E2 }, { F2 }, { G2 }, { H2 }
	};

	/**
	 * Used for determining legal pawn push moves for black pawns.
	 *
	 * <p>
	 * Structure and usage identical to {@link #PAWN_PUSH_WHITE}, but for black
	 * pawns moving downward.
	 * </p>
	 *
	 * @implNote Used for chess move generation. Omits off-board squares.
	 */
	protected static final byte[][] PAWN_PUSH_BLACK = {
			{ A7 }, { B7 }, { C7 }, { D7 }, { E7 }, { F7 }, { G7 }, { H7 },
			{ A6, A5 }, { B6, B5 }, { C6, C5 }, { D6, D5 }, { E6, E5 }, { F6, F5 }, { G6, G5 }, { H6, H5 },
			{ A5 }, { B5 }, { C5 }, { D5 }, { E5 }, { F5 }, { G5 }, { H5 },
			{ A4 }, { B4 }, { C4 }, { D4 }, { E4 }, { F4 }, { G4 }, { H4 },
			{ A3 }, { B3 }, { C3 }, { D3 }, { E3 }, { F3 }, { G3 }, { H3 },
			{ A2 }, { B2 }, { C2 }, { D2 }, { E2 }, { F2 }, { G2 }, { H2 },
			{ A1 }, { B1 }, { C1 }, { D1 }, { E1 }, { F1 }, { G1 }, { H1 },
			{}, {}, {}, {}, {}, {}, {}, {}
	};

	/**
	 * Used for determining legal pawn capture moves for white pawns.
	 *
	 * <p>
	 * Structure:
	 * <ul>
	 * <li>The first dimension is the pawn's current position.</li>
	 * <li>The second dimension contains the indices of diagonally reachable squares
	 * for capturing (left/right diagonal forward).</li>
	 * </ul>
	 *
	 * <p>
	 * Example usage:
	 * </p>
	 * 
	 * <pre>
	 * byte[] whiteCaptures = PAWN_CAPTURE_WHITE[E4];
	 * for (byte target : whiteCaptures) {
	 * 	// Generate capture move to target
	 * }
	 * </pre>
	 *
	 * @implNote Used for chess move generation. Off-board destinations are
	 *           excluded.
	 */
	protected static final byte[][] PAWN_CAPTURE_WHITE = {
			{}, {}, {}, {}, {}, {}, {}, {},
			{ B8 }, { A8, C8 }, { B8, D8 }, { C8, E8 }, { D8, F8 }, { E8, G8 }, { F8, H8 }, { G8 },
			{ B7 }, { A7, C7 }, { B7, D7 }, { C7, E7 }, { D7, F7 }, { E7, G7 }, { F7, H7 }, { G7 },
			{ B6 }, { A6, C6 }, { B6, D6 }, { C6, E6 }, { D6, F6 }, { E6, G6 }, { F6, H6 }, { G6 },
			{ B5 }, { A5, C5 }, { B5, D5 }, { C5, E5 }, { D5, F5 }, { E5, G5 }, { F5, H5 }, { G5 },
			{ B4 }, { A4, C4 }, { B4, D4 }, { C4, E4 }, { D4, F4 }, { E4, G4 }, { F4, H4 }, { G4 },
			{ B3 }, { A3, C3 }, { B3, D3 }, { C3, E3 }, { D3, F3 }, { E3, G3 }, { F3, H3 }, { G3 },
			{ B2 }, { A2, C2 }, { B2, D2 }, { C2, E2 }, { D2, F2 }, { E2, G2 }, { F2, H2 }, { G2 }
	};

	/**
	 * Used for determining legal pawn capture moves for black pawns.
	 *
	 * <p>
	 * Structure and usage identical to {@link #PAWN_CAPTURE_WHITE}, but for black
	 * pawns
	 * capturing diagonally downward.
	 * </p>
	 *
	 * @implNote Used for chess move generation. Off-board destinations are
	 *           excluded.
	 */
	protected static final byte[][] PAWN_CAPTURE_BLACK = {
			{ B7 }, { A7, C7 }, { B7, D7 }, { C7, E7 }, { D7, F7 }, { E7, G7 }, { F7, H7 }, { G7 },
			{ B6 }, { A6, C6 }, { B6, D6 }, { C6, E6 }, { D6, F6 }, { E6, G6 }, { F6, H6 }, { G6 },
			{ B5 }, { A5, C5 }, { B5, D5 }, { C5, E5 }, { D5, F5 }, { E5, G5 }, { F5, H5 }, { G5 },
			{ B4 }, { A4, C4 }, { B4, D4 }, { C4, E4 }, { D4, F4 }, { E4, G4 }, { F4, H4 }, { G4 },
			{ B3 }, { A3, C3 }, { B3, D3 }, { C3, E3 }, { D3, F3 }, { E3, G3 }, { F3, H3 }, { G3 },
			{ B2 }, { A2, C2 }, { B2, D2 }, { C2, E2 }, { D2, F2 }, { E2, G2 }, { F2, H2 }, { G2 },
			{ B1 }, { A1, C1 }, { B1, D1 }, { C1, E1 }, { D1, F1 }, { E1, G1 }, { F1, H1 }, { G1 },
			{}, {}, {}, {}, {}, {}, {}, {}
	};

	/**
	 * Used for preventing instantiation of the {@code Field} utility class.
	 */
	private Field() {
		// Prevent instantiation
	}

	/**
	 * Used for checking if the provided string represents a valid chessboard square
	 * in standard algebraic notation (e.g., "e4").
	 * <p>
	 * A valid square must have a file character from 'a' to 'h' followed by a rank
	 * character from '1' to '8'.
	 * </p>
	 *
	 * @param string the square notation to validate (e.g. "e4")
	 * @return {@code true} if {@code string} matches the pattern
	 *         {@code "[a-h][1-8]"}, otherwise {@code false}
	 */
	public static boolean isField(String string) {
		return string.equals("-") || string.matches("[a-h][1-8]");
	}

	/**
	 * Used for retrieving the file character (a–h) of the given board
	 * {@code index}.
	 * <p>
	 * For example, if {@code index} is 0 (A8), this returns 'a'. If {@code index}
	 * is 7 (H8), this returns 'h'.
	 * </p>
	 *
	 * @param index a 0-based board index (e.g. 0 for A8, 63 for H1)
	 * @return the file character for the specified square
	 */
	public static char getFile(byte index) {
		return (char) ('a' + index % 8);
	}

	/**
	 * Used for retrieving the uppercase file character (A–H) of the given board
	 * {@code index}.
	 * <p>
	 * For example, if {@code index} is 0 (A8), this returns 'A'. If {@code index}
	 * is 7 (H8), this returns 'H'.
	 * </p>
	 *
	 * @param index a 0-based board index (e.g. 0 for A8, 63 for H1)
	 * @return the uppercase file character for the specified square
	 */
	public static char getFileUppercase(byte index) {
		return (char) ('A' + index % 8);
	}

	/**
	 * Used for retrieving the rank character (1–8) of the given board
	 * {@code index}.
	 * <p>
	 * For example, if {@code index} is 0 (A8), this returns '8'. If {@code index}
	 * is 63 (H1), this returns '1'.
	 * </p>
	 *
	 * @param index a 0-based board index (e.g. 0 for A8, 63 for H1)
	 * @return the rank character for the specified square
	 */
	public static char getRank(byte index) {
		return (char) ('0' + 8 - index / 8);
	}

	/**
	 * Used for calculating the X-coordinate of the index, starting at 0.
	 * 
	 * @return The X-coordinate of the index, starting at 0
	 */
	public static int getX(byte index) {
		return index % 8;
	}

	/**
	 * Used for calculating the Y-coordinate of the index, starting at 0.
	 * 
	 * @return The Y-coordinate of the index, starting at 0
	 */
	public static int getY(byte index) {
		return 7 - index / 8;
	}

	/**
	 * Used for checking whether a file/rank pair lies within the chessboard.
	 *
	 * @param file the file coordinate (0..7)
	 * @param rank the rank coordinate (0..7)
	 * @return {@code true} if the coordinates are on the 8x8 board
	 */
	public static boolean isOnBoard(int file, int rank) {
		return file >= 0 && file < 8 && rank >= 0 && rank < 8;
	}

	/**
	 * Used for converting file/rank coordinates into a 0-based board index.
	 *
	 * @param file the file coordinate (0..7)
	 * @param rank the rank coordinate (0..7)
	 * @return the board index (0..63)
	 */
	public static int toIndex(int file, int rank) {
		return (7 - rank) * 8 + file;
	}

	/**
	 * Used for calculating the inverted X-coordinate of the index, starting at 7.
	 * 
	 * @return The inverted X-coordinate of the index, starting at 7
	 */
	public static int getXInverted(byte index) {
		return 7 - index % 8;
	}

	/**
	 * Used for converting a standard algebraic notation (SAN) square string (e.g.,
	 * "e4") into its corresponding board index.
	 *
	 * @param string a two-character string representing a square, where the first
	 *               character is the file ('a'–'h') and the second character is the
	 *               rank ('1'–'8')
	 * @return the zero-based board index corresponding to the given square
	 * @throws StringIndexOutOfBoundsException if {@code string} is shorter than 2
	 */
	public static byte toIndex(String string) {
		if (string.equals("-")) {
			return Field.NO_SQUARE;
		}
		return toIndex(string.charAt(0), string.charAt(1));
	}

	/**
	 * Used for converting a file character (e.g., 'e') and rank character (e.g.,
	 * '4') into their corresponding board index.
	 *
	 * @param first  the file character ('a'–'h')
	 * @param second the rank character ('1'–'8')
	 * @return the zero-based board index corresponding to the square
	 */
	public static byte toIndex(char first, char second) {
		return (byte) ((first - 'a') + (('8' - second) * 8));
	}

	/**
	 * Used for checking whether the given {@code index} is on the 1st rank (A1–H1).
	 *
	 * @param index the board index to check
	 * @return {@code true} if {@code index} lies between {@link #A1} and
	 *         {@link #H1}, inclusive
	 */
	public static boolean isOn1stRank(byte index) {
		return index >= A1 && index <= H1;
	}

	/**
	 * Used for checking whether the given {@code index} is on the 2nd rank (A2–H2).
	 *
	 * @param index the board index to check
	 * @return {@code true} if {@code index} lies between {@link #A2} and
	 *         {@link #H2}, inclusive
	 */
	public static boolean isOn2ndRank(byte index) {
		return index >= A2 && index <= H2;
	}

	/**
	 * Used for checking whether the given {@code index} is on the 3rd rank (A3–H3).
	 *
	 * @param index the board index to check
	 * @return {@code true} if {@code index} lies between {@link #A3} and
	 *         {@link #H3}, inclusive
	 */
	public static boolean isOn3rdRank(byte index) {
		return index >= A3 && index <= H3;
	}

	/**
	 * Used for checking whether the given {@code index} is on the 4th rank (A4–H4).
	 *
	 * @param index the board index to check
	 * @return {@code true} if {@code index} lies between {@link #A4} and
	 *         {@link #H4}, inclusive
	 */
	public static boolean isOn4thRank(byte index) {
		return index >= A4 && index <= H4;
	}

	/**
	 * Used for checking whether the given {@code index} is on the 5th rank (A5–H5).
	 *
	 * @param index the board index to check
	 * @return {@code true} if {@code index} lies between {@link #A5} and
	 *         {@link #H5}, inclusive
	 */
	public static boolean isOn5thRank(byte index) {
		return index >= A5 && index <= H5;
	}

	/**
	 * Used for checking whether the given {@code index} is on the 6th rank (A6–H6).
	 *
	 * @param index the board index to check
	 * @return {@code true} if {@code index} lies between {@link #A6} and
	 *         {@link #H6}, inclusive
	 */
	public static boolean isOn6thRank(byte index) {
		return index >= A6 && index <= H6;
	}

	/**
	 * Used for checking whether the given {@code index} is on the 7th rank (A7–H7).
	 *
	 * @param index the board index to check
	 * @return {@code true} if {@code index} lies between {@link #A7} and
	 *         {@link #H7}, inclusive
	 */
	public static boolean isOn7thRank(byte index) {
		return index >= A7 && index <= H7;
	}

	/**
	 * Used for checking whether the given {@code index} is on the 8th rank (A8–H8).
	 *
	 * @param index the board index to check
	 * @return {@code true} if {@code index} lies between {@link #A8} and
	 *         {@link #H8}, inclusive
	 */
	public static boolean isOn8thRank(byte index) {
		return index >= A8 && index <= H8;
	}

	/**
	 * Used for converting a zero-based board index (0 through 63) into its
	 * corresponding standard algebraic notation (SAN) string.
	 * <p>
	 * For example, an index of {@code 0} (A8) becomes "a8", and an index of
	 * {@code 63} (H1) becomes "h1".
	 * </p>
	 * <p>
	 * If the index equals {@code Field.NoSquare}, this returns "-".
	 * </p>
	 *
	 * @param index the board index to convert
	 * @return a two-character SAN string representing the square, or "-" if index
	 *         is NoSquare
	 */
	public static String toString(byte index) {
		if (index == Field.NO_SQUARE) {
			return "-";
		}
		return new String(new char[] { (char) ('a' + index % 8), (char) ('0' + 8 - index / 8) });
	}

	/**
	 * Used for inverting a 0-based board index (0 through 63), effectively
	 * mirroring the square's position across the board.
	 * <p>
	 * For example, in a standard 8x8 board indexing scheme: A8 (index 0) becomes H1
	 * (index 63), H1 (index 63) becomes A8 (index 0).
	 * </p>
	 *
	 * @param index the board index to invert
	 * @return the inverted board index, calculated as {@code 63 - index}
	 */
	public static byte invert(byte index) {
		return (byte) (63 - index);
	}

	/**
	 * Used for inverting a 0-based board index (0 through 63) when represented as
	 * an integer, effectively mirroring the square's position across the board.
	 *
	 * <p>
	 * This behaves the same as the byte-based version, but returns an {@code int}
	 * for scenarios where an integer-based operation is needed.
	 * </p>
	 *
	 * @param index the board index to invert, given as an integer
	 * @return the inverted board index, calculated as {@code 63 - index}
	 */
	public static int invert(int index) {
		return 63 - index;
	}

	/**
	 * Used for calculating the inverted Y-coordinate of the index, starting at 7.
	 * 
	 * @return The inverted Y-coordinate of the index, starting at 7
	 */
	public static int getYInverted(byte index) {
		return index / 8;
	}

	/**
	 * 
	 * @param index
	 * @implNote This method does not check for out-of-bounds conditions.
	 * @return
	 */
	public static byte uprank(byte index) {
		return (byte) (index + 8);
	}

	/**
	 * 
	 * @param index
	 * @implNote This method does not check for out-of-bounds conditions.
	 * @return
	 */
	public static byte downrank(byte index) {
		return (byte) (index - 8);
	}

	/**
	 * Used for calculating the right square of the index.
	 * 
	 * @implNote This method does not check for out-of-bounds conditions.
	 * @return The right square of the index
	 */
	public static byte rightOf(byte index) {
		return (byte) (index + 1);
	}

	/**
	 * Used for calculating the left square of the index.
	 * 
	 * @implNote This method does not check for out-of-bounds conditions.
	 * @return The left square of the index
	 */
	public static byte leftOf(byte index) {
		return (byte) (index - 1);
	}

	/**
	 * Provides the cached diagonal rays for each board square.
	 *
	 * @return three-dimensional array where the first dimension is the source
	 *         square and the remaining dimensions describe each diagonal path.
	 */
	public static byte[][][] getDiagonals() {
		return DIAGONALS;
	}

	/**
	 * Provides the cached straight-line rays for each board square.
	 *
	 * @return three-dimensional array where the first dimension is the source
	 *         square and the remaining dimensions describe each orthogonal path.
	 */
	public static byte[][][] getLines() {
		return LINES;
	}

	/**
	 * Provides the cached knight jump targets for each board square.
	 *
	 * @return two-dimensional array where each entry is the set of destinations
	 *         reachable by a knight from the corresponding square.
	 */
	public static byte[][] getJumps() {
		return JUMPS;
	}

	/**
	 * Provides the cached king neighbor squares for each board square.
	 *
	 * @return two-dimensional array with the adjacent squares that can be reached
	 *         by a king from each position.
	 */
	public static byte[][] getNeighbors() {
		return NEIGHBORS;
	}

	/**
	 * Provides the cached forward push targets for white pawns.
	 *
	 * @return two-dimensional array where each entry lists the squares a white
	 *         pawn can push to (one or two steps) from the given source square.
	 */
	public static byte[][] getPawnPushWhite() {
		return PAWN_PUSH_WHITE;
	}

	/**
	 * Provides the cached forward push targets for black pawns.
	 *
	 * @return two-dimensional array where each entry lists the squares a black
	 *         pawn can push to (one or two steps) from the given source square.
	 */
	public static byte[][] getPawnPushBlack() {
		return PAWN_PUSH_BLACK;
	}

	/**
	 * Provides the cached capture targets for white pawns.
	 *
	 * @return two-dimensional array where each entry lists the squares a white
	 *         pawn can capture from the given source square.
	 */
	public static byte[][] getPawnCaptureWhite() {
		return PAWN_CAPTURE_WHITE;
	}

	/**
	 * Provides the cached capture targets for black pawns.
	 *
	 * @return two-dimensional array where each entry lists the squares a black
	 *         pawn can capture from the given source square.
	 */
	public static byte[][] getPawnCaptureBlack() {
		return PAWN_CAPTURE_BLACK;
	}
}
