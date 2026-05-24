package chess.core;

import java.util.Objects;

/**
 * Shared helpers for deriving moves and notation from position transitions.
 *
 * <p>
 * Several CLI and tagging workflows work from a parent and child position
 * rather than from the encoded move itself. This utility keeps that inference
 * logic in one place and returns no move when the transition is ambiguous.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MoveInference {

    /**
     * Prevents instantiation of this utility class.
     */
    private MoveInference() {
        // utility
    }

    /**
     * Finds the unique legal move that transforms one position into another.
     *
     * <p>
     * The comparison uses {@link Position#signatureCore()}, so it ignores move
     * clocks while still checking board state, turn, castling rights, and
     * en-passant state. If no legal move matches, or more than one legal move
     * reaches the same child signature, {@link Move#NO_MOVE} is returned.
     * </p>
     *
     * @param from source position
     * @param to target position
     * @return unique legal move, or {@link Move#NO_MOVE}
     * @throws NullPointerException if either position is {@code null}
     */
    public static short uniqueMove(Position from, Position to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        long target = to.signatureCore();
        MoveList moves = from.legalMoves();
        short found = Move.NO_MOVE;
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            Position candidate = from.copy().play(move);
            if (candidate.signatureCore() == target) {
                if (found != Move.NO_MOVE) {
                    return Move.NO_MOVE;
                }
                found = move;
            }
        }
        return found;
    }

    /**
     * Returns SAN and UCI notation for the unique transition move.
     *
     * @param from source position
     * @param to target position
     * @return move notation, or {@code null} if the transition is not unique
     * @throws NullPointerException if either position is {@code null}
     */
    public static Notation notation(Position from, Position to) {
        short move = uniqueMove(from, to);
        return move == Move.NO_MOVE ? null : notation(from, move);
    }

    /**
     * Returns SAN and UCI notation for one encoded move.
     *
     * <p>
     * SAN conversion can fail for malformed caller input. In that case the SAN
     * field falls back to the UCI string, matching the CLI's historical behavior.
     * </p>
     *
     * @param position position before the move
     * @param move encoded move
     * @return move notation, or {@code null} for {@link Move#NO_MOVE}
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static Notation notation(Position position, short move) {
        Objects.requireNonNull(position, "position");
        if (move == Move.NO_MOVE) {
            return null;
        }
        String uci = Move.toString(move);
        String san;
        try {
            san = SAN.toAlgebraic(position, move);
        } catch (RuntimeException ex) {
            san = uci;
        }
        return new Notation(san, uci);
    }

    /**
     * Immutable pair of SAN and UCI notation for one move.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    public static final class Notation {

        /**
         * Standard algebraic notation for the move.
         */
        private final String san;

        /**
         * Coordinate notation for the move.
         */
        private final String uci;

        /**
         * Creates one notation pair.
         *
         * @param san standard algebraic notation
         * @param uci coordinate notation
         */
        private Notation(String san, String uci) {
            this.san = san;
            this.uci = uci;
        }

        /**
         * Returns standard algebraic notation.
         *
         * @return SAN text
         */
        public String san() {
            return san;
        }

        /**
         * Returns coordinate notation.
         *
         * @return UCI text
         */
        public String uci() {
            return uci;
        }
    }
}
