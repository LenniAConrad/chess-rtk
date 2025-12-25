package chess.classical;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Evaluates a position using a simple <em>mobility</em> heuristic.
 *
 * <p>
 * Mobility here means the number of <strong>pseudo-legal</strong> destinations
 * available to each side from the current board layout:
 * </p>
 * <ul>
 * <li>Moves are counted if the destination square is empty or occupied by an
 * enemy piece.</li>
 * <li>For sliding pieces, rays stop at the first occupied square (optionally
 * counting that square if it holds an enemy piece).</li>
 * <li>For pawns, forward pushes are counted as long as intermediate squares are
 * empty; diagonal captures are counted if an enemy piece is present.</li>
 * </ul>
 *
 * <p>
 * <strong>Important limitations:</strong> this is intentionally fast and does
 * not attempt to be a full legal move generator.
 * It therefore ignores:
 * </p>
 * <ul>
 * <li>Check legality (e.g., pinned pieces and king moves into check are still
 * counted).</li>
 * <li>Castling and en passant.</li>
 * <li>Promotion-specific move expansion (a pawn move to the last rank is
 * counted once).</li>
 * </ul>
 *
 * <p>
 * The returned score is symmetric: positive values favor White, negative values
 * favor Black.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Mobility {

    /**
     * Weight applied when translating the raw mobility difference into a
     * floating-point score.
     *
     * <p>
     * The raw mobility difference is a plain count of reachable destinations
     * (White minus Black). This factor brings the magnitude into a more
     * "evaluation-like" range.
     * </p>
     */
    private static final double MOBILITY_WEIGHT = 0.08;

    /**
     * Precomputed diagonal rays used for bishop and queen mobility scans.
     *
     * <p>
     * Indexed as {@code rays[fromSquare][rayIndex][stepIndex]} and contains only
     * on-board squares.
     * </p>
     */
    private static final byte[][][] DIAGONAL_RAYS = Field.getDiagonals();

    /**
     * Precomputed orthogonal rays used for rook and queen mobility scans.
     *
     * <p>
     * Indexed as {@code rays[fromSquare][rayIndex][stepIndex]} and contains only
     * on-board squares.
     * </p>
     */
    private static final byte[][][] ORTHOGONAL_RAYS = Field.getLines();

    /**
     * Candidate destination offsets for knights.
     *
     * <p>
     * Indexed as {@code jumps[fromSquare][i]}.
     * </p>
     */
    private static final byte[][] KNIGHT_JUMPS = Field.getJumps();

    /**
     * Candidate destination offsets for kings.
     *
     * <p>
     * Indexed as {@code neighbors[fromSquare][i]}.
     * </p>
     */
    private static final byte[][] KING_NEIGHBORS = Field.getNeighbors();

    /**
     * Pawn push targets for White.
     *
     * <p>
     * Typically includes one-step and (where applicable) two-step pushes. The
     * caller stops scanning pushes on the first occupied square.
     * </p>
     */
    private static final byte[][] PAWN_PUSH_WHITE = Field.getPawnPushWhite();

    /**
     * Pawn push targets for Black.
     *
     * <p>
     * Typically includes one-step and (where applicable) two-step pushes. The
     * caller stops scanning pushes on the first occupied square.
     * </p>
     */
    private static final byte[][] PAWN_PUSH_BLACK = Field.getPawnPushBlack();

    /**
     * Pawn capture targets for White.
     *
     * <p>
     * Note: en passant is not represented here; only direct captures onto an
     * occupied enemy square are counted.
     * </p>
     */
    private static final byte[][] PAWN_CAPTURE_WHITE = Field.getPawnCaptureWhite();

    /**
     * Pawn capture targets for Black.
     *
     * <p>
     * Note: en passant is not represented here; only direct captures onto an
     * occupied enemy square are counted.
     * </p>
     */
    private static final byte[][] PAWN_CAPTURE_BLACK = Field.getPawnCaptureBlack();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private Mobility() {
        // Utility class; prevent instantiation.
    }

    /**
     * Evaluates the provided position using the difference in pseudo-legal
     * mobility between the two sides.
     *
     * @param pos the position to evaluate
     * @return positive values when White is more mobile; negative when Black
     *         dominates
     */
    public static double evaluate(Position pos) {
        byte[] board = pos.getBoard();
        int whiteMobility = countSideMobility(board, true);
        int blackMobility = countSideMobility(board, false);
        return (whiteMobility - blackMobility) * MOBILITY_WEIGHT;
    }

    /**
     * Counts all pseudo-legal target squares reachable by the given side.
     *
     * <p>
     * This is a board-only scan (no side-to-move, no check constraints). It is
     * therefore suitable as a cheap feature in other evaluators.
     * </p>
     *
     * @param board the board array to evaluate
     * @param white {@code true} to count White's mobility; {@code false} for Black
     * @return the mobility count for the requested side
     */
    private static int countSideMobility(byte[] board, boolean white) {
        int mobility = 0;
        for (int square = 0; square < board.length; square++) {
            byte piece = board[square];
            if (piece == Piece.EMPTY || white != Piece.isWhite(piece)) {
                continue;
            }

            if (Piece.isPawn(piece)) {
                mobility += countPawnMobility(board, square, white);
            } else if (Piece.isKnight(piece)) {
                mobility += countStaticMoves(board, square, KNIGHT_JUMPS, white);
            } else if (Piece.isKing(piece)) {
                mobility += countStaticMoves(board, square, KING_NEIGHBORS, white);
            } else if (Piece.isBishop(piece)) {
                mobility += countSlidingMoves(board, square, DIAGONAL_RAYS, white);
            } else if (Piece.isRook(piece)) {
                mobility += countSlidingMoves(board, square, ORTHOGONAL_RAYS, white);
            } else if (Piece.isQueen(piece)) {
                mobility += countSlidingMoves(board, square, DIAGONAL_RAYS, white);
                mobility += countSlidingMoves(board, square, ORTHOGONAL_RAYS, white);
            }
        }
        return mobility;
    }

    /**
     * Counts how many pawn pushes and captures are currently available from the
     * given square.
     *
     * <p>
     * Pushes are counted in-order (one-step, then optional two-step) and stop at
     * the first occupied square. Captures are counted only if an enemy piece is
     * present on the target square.
     * </p>
     *
     * @param board  current board layout
     * @param square square containing the pawn
     * @param white  {@code true} if the pawn belongs to White
     * @return number of reachable pawn target squares
     */
    private static int countPawnMobility(byte[] board, int square, boolean white) {
        int mobility = 0;
        byte[][] pushTargets = white ? PAWN_PUSH_WHITE : PAWN_PUSH_BLACK;
        byte[][] captureTargets = white ? PAWN_CAPTURE_WHITE : PAWN_CAPTURE_BLACK;

        for (byte to : pushTargets[square]) {
            if (Piece.isEmpty(board[to])) {
                mobility++;
            } else {
                break;
            }
        }

        for (byte to : captureTargets[square]) {
            if (isEnemy(white, board[to])) {
                mobility++;
            }
        }
        return mobility;
    }

    /**
     * Counts reachable squares for fixed-offset pieces (knights and kings).
     *
     * <p>
     * A destination is counted if it is empty or occupied by an enemy piece.
     * Friendly-occupied squares are not counted.
     * </p>
     *
     * @param board   current board layout
     * @param square  source square
     * @param offsets precomputed offsets for the piece
     * @param white   whether the piece belongs to White
     * @return number of target squares that are empty or contain enemy pieces
     */
    private static int countStaticMoves(byte[] board, int square, byte[][] offsets, boolean white) {
        int mobility = 0;
        for (byte to : offsets[square]) {
            byte occupant = board[to];
            if (Piece.isEmpty(occupant) || isEnemy(white, occupant)) {
                mobility++;
            }
        }
        return mobility;
    }

    /**
     * Walks each ray for sliding pieces and counts legal destinations.
     *
     * <p>
     * Empty squares are counted until a blocking piece is found. If the blocking
     * piece is an enemy, that square is also counted (capture square) and the ray
     * terminates.
     * </p>
     *
     * @param board  current board state
     * @param square source square
     * @param rays   precomputed rays to traverse
     * @param white  whether the sliding piece belongs to White
     * @return number of legal destinations along the rays
     */
    private static int countSlidingMoves(byte[] board, int square, byte[][][] rays, boolean white) {
        int mobility = 0;
        for (byte[] ray : rays[square]) {
            for (byte to : ray) {
                byte occupant = board[to];
                if (Piece.isEmpty(occupant)) {
                    mobility++;
                } else {
                    if (isEnemy(white, occupant)) {
                        mobility++;
                    }
                    break;
                }
            }
        }
        return mobility;
    }

    /**
     * Determines whether a piece belongs to the opposing side.
     *
     * <p>
     * This helper treats {@link Piece#EMPTY} as "not an enemy".
     * </p>
     *
     * @param white reference color (true for White)
     * @param piece the piece to test
     * @return {@code true} if {@code piece} belongs to the opponent
     */
    private static boolean isEnemy(boolean white, byte piece) {
        if (Piece.isEmpty(piece)) {
            return false;
        }
        return white ? Piece.isBlack(piece) : Piece.isWhite(piece);
    }
}
