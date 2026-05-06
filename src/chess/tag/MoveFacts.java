package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.List;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;

/**
 * Emits exact legal-move facts for the side to move.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
final class MoveFacts {

    /**
     * Prevents instantiation.
     */
    private MoveFacts() {
        // utility
    }

    /**
     * Adds exact legal-move count tags for the side to move.
     *
     * @param context shared tagging context
     * @param out mutable tag accumulator
     */
    static void addTags(Context context, List<String> out) {
        Position position = context.position;
        MoveList moves = context.legalMoves();
        Counts counts = count(position, moves);
        context.forcingMovesCount = counts.forcing;

        Emitter.tag(MOVE_FAMILY).field(LEGAL, moves.size()).emit(out);
        addCount(out, CAPTURES, counts.captures);
        addCount(out, CHECKS, counts.checks);
        addCount(out, MATES, counts.mates);
        addCount(out, PROMOTIONS, counts.promotions);
        addCount(out, UNDERPROMOTIONS, counts.underpromotions);
        addCount(out, CASTLES, counts.castles);
        addCount(out, EN_PASSANT, counts.enPassant);
        addCount(out, QUIET, counts.quiet);

        if (moves.size() == 1) {
            Emitter.tag(MOVE_FAMILY).field(ONLY, Move.toString(moves.get(0))).emit(out);
            Emitter.tag(MOVE_FAMILY).field(FORCED, TRUE).emit(out);
        }
        if (position.inCheck()) {
            Emitter.tag(MOVE_FAMILY).field(EVASIONS, moves.size()).emit(out);
        }
    }

    /**
     * Adds a move count tag when the count is non-zero.
     *
     * @param out mutable tag accumulator
     * @param key move-count key
     * @param count count to emit
     */
    private static void addCount(List<String> out, String key, int count) {
        if (count > 0) {
            Emitter.tag(MOVE_FAMILY).field(key, count).emit(out);
        }
    }

    /**
     * Counts exact move facts by playing and undoing each legal move.
     *
     * @param position position being tagged
     * @param moves legal moves for the side to move
     * @return move-count summary
     */
    private static Counts count(Position position, MoveList moves) {
        Counts counts = new Counts();
        Position.State state = new Position.State();
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            boolean capture = position.isCapture(move);
            boolean promotion = Move.isPromotion(move);
            boolean castle = position.isCastle(move);
            boolean enPassant = position.isEnPassantCapture(move);

            boolean givesCheck;
            boolean mate;
            position.play(move, state);
            try {
                givesCheck = position.inCheck();
                mate = givesCheck && position.legalMoves().isEmpty();
            } finally {
                position.undo(move, state);
            }

            counts.captures += capture ? 1 : 0;
            counts.checks += givesCheck ? 1 : 0;
            counts.mates += mate ? 1 : 0;
            counts.promotions += promotion ? 1 : 0;
            counts.underpromotions += Move.isUnderPromotion(move) ? 1 : 0;
            counts.castles += castle ? 1 : 0;
            counts.enPassant += enPassant ? 1 : 0;
            counts.quiet += !capture && !givesCheck && !promotion && !castle ? 1 : 0;
            counts.forcing += capture || givesCheck || promotion ? 1 : 0;
        }
        return counts;
    }

    /**
     * Exact legal-move counters for the side to move.
     */
    private static final class Counts {

        /**
         * Legal capture count.
         */
        private int captures;

        /**
         * Legal checking move count.
         */
        private int checks;

        /**
         * Legal mate-in-one move count.
         */
        private int mates;

        /**
         * Legal promotion move count.
         */
        private int promotions;

        /**
         * Legal underpromotion move count.
         */
        private int underpromotions;

        /**
         * Legal castling move count.
         */
        private int castles;

        /**
         * Legal en-passant capture count.
         */
        private int enPassant;

        /**
         * Legal quiet move count.
         */
        private int quiet;

        /**
         * Legal forcing move count (checks, captures, or promotions).
         */
        private int forcing;
    }
}
