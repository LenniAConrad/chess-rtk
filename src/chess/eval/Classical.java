package chess.eval;

import chess.classical.Wdl;
import chess.core.Bits;
import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;

/**
 * Classical handcrafted centipawn evaluator backed by {@link Wdl}.
 *
 * <p>
 * This evaluator has no external model dependency and is the default fallback
 * for the built-in searcher. It exposes the existing CRTK WDL heuristic as a
 * side-to-move centipawn score suitable for alpha-beta leaf evaluation.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S135")
public final class Classical implements CentipawnEvaluator {

    /**
     * Evaluates one position.
     *
     * @param position position to evaluate
     * @return centipawns from the side-to-move perspective
     */
    @Override
    public int evaluate(Position position) {
        return Wdl.evaluateStmCentipawns(position);
    }

    /**
     * Adds cheap classical quiet-move priors for alpha-beta move ordering.
     *
     * <p>
     * Captures and promotions are already handled by the searcher, so this keeps
     * the bonus small and focused on castling, development, centralization, and
     * advanced pawn pushes.
     * </p>
     *
     * @param position position whose legal moves are being ordered
     * @param moves legal moves aligned with {@code scores}
     * @param scores mutable ordering scores to adjust in place
     */
    @Override
    public void scoreMoves(Position position, short[] moves, int[] scores) {
        boolean white = position.isWhiteToMove();
        for (int i = 0; i < moves.length; i++) {
            short move = moves[i];
            if (Move.getPromotion(move) != 0) {
                continue;
            }
            int from = Move.getFromIndex(move);
            int to = Move.getToIndex(move);
            byte moving = position.pieceAt(from);
            if (moving == Piece.EMPTY) {
                continue;
            }
            if (position.isCastle(move)) {
                scores[i] += 30_000;
                continue;
            }
            if (position.pieceAt(to) != Piece.EMPTY || isEnPassant(position, moving, from, to)) {
                continue;
            }
            scores[i] += quietMovePrior(moving, from, position.actualToSquare(move), white);
        }
    }

    /**
     * Scores one quiet move for ordering.
     *
     * @param moving moving piece code
     * @param from source square
     * @param to target square
     * @param white side to move
     * @return move-ordering bonus
     */
    private static int quietMovePrior(byte moving, int from, int to, boolean white) {
        int type = Math.abs(moving);
        int bonus = 0;
        if (type == Piece.KNIGHT || type == Piece.BISHOP) {
            bonus += (centerScore(to) - centerScore(from)) * 256;
            if (isBackRank(from, white) && !isEdgeFile(to)) {
                bonus += 8_000;
            }
        } else if (type == Piece.ROOK) {
            bonus += (centerScore(to) - centerScore(from)) * 96;
        } else if (type == Piece.PAWN) {
            int relativeRank = white ? Bits.rank(to) : 7 - Bits.rank(to);
            bonus += Math.max(0, relativeRank - 2) * 1_200;
            if (relativeRank >= 5) {
                bonus += 3_000;
            }
        }
        return bonus;
    }

    /**
     * Returns a small centralization score for a square.
     *
     * @param square board square
     * @return centralization score
     */
    private static int centerScore(int square) {
        int file = square & 7;
        int rank = Bits.rank(square);
        return 14 - 2 * (Math.abs(2 * file - 7) + Math.abs(2 * rank - 7));
    }

    /**
     * Returns whether a square is on the side's back rank.
     *
     * @param square square to test
     * @param white side to inspect
     * @return true for first-rank White or eighth-rank Black squares
     */
    private static boolean isBackRank(int square, boolean white) {
        return white ? Bits.rank(square) == 0 : Bits.rank(square) == 7;
    }

    /**
     * Returns whether a square is on the a- or h-file.
     *
     * @param square square to test
     * @return true on edge files
     */
    private static boolean isEdgeFile(int square) {
        int file = square & 7;
        return file == 0 || file == 7;
    }

    /**
     * Returns whether a pawn move is an en-passant capture.
     *
     * @param position current position
     * @param moving moving piece
     * @param from source square
     * @param to target square
     * @return true when the move captures en passant
     */
    private static boolean isEnPassant(Position position, byte moving, int from, int to) {
        return Piece.isPawn(moving)
                && to == position.enPassantSquare()
                && Math.abs((from & 7) - (to & 7)) == 1;
    }

    /**
     * Returns the evaluator label.
     *
     * @return stable label used in engine output
     */
    @Override
    public String name() {
        return Kind.CLASSICAL.label();
    }
}
