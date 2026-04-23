package chess.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import chess.core.MoveGenerator;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.Move;

/**
 * Detailed perft runner for the core move generator.
 *
 * <p>
 * Besides node counts, this class counts captures, en-passant captures,
 * castling moves, promotions, checks, and checkmates at the leaf ply. It uses
 * mutable scratch state internally so the public immutable records are created
 * only for final results and divide rows.
 * </p>
 *
 * @author Lennart A. Conrad
 * @since 2025
 */
public final class Perft {

    /**
     * Prevents instantiation of this stateless utility class.
     */
    private Perft() {
        // utility
    }

    /**
     * Runs detailed perft from a root position.
     *
     * @param position root position
     * @param depth non-negative perft depth
     * @return timed detailed perft result
     */
    public static Result run(Position position, int depth) {
        requireDepth(depth);
        long start = System.nanoTime();
        Counter counter = new Counter();
        perft(position, depth, new PerftContext(depth), 0, counter);
        Stats stats = counter.toStats();
        return new Result(depth, stats, System.nanoTime() - start);
    }

    /**
     * Runs detailed divide perft from a root position.
     *
     * <p>
     * Each divide entry contains the detailed counters for one legal root move.
     * Entries are sorted by UCI move text for deterministic CLI output.
     * </p>
     *
     * @param position root position
     * @param depth non-negative perft depth
     * @return timed divide result
     */
    public static DivideResult divide(Position position, int depth) {
        requireDepth(depth);
        long start = System.nanoTime();
        Counter total = new Counter();
        List<DivideEntry> entries = new ArrayList<>();
        PerftContext context = new PerftContext(depth);
        if (depth == 0) {
            total.nodes = 1L;
        } else {
            addDivideEntries(position, depth, context, total, entries);
        }
        return new DivideResult(depth, total.toStats(), Collections.unmodifiableList(entries),
                System.nanoTime() - start);
    }

    /**
     * Adds detailed counters for every legal root move.
     *
     * @param position root position
     * @param depth non-zero perft depth
     * @param context reusable move and undo-state scratch objects
     * @param total total counter to update
     * @param entries divide entries to update
     */
    private static void addDivideEntries(
            Position position,
            int depth,
            PerftContext context,
            Counter total,
            List<DivideEntry> entries) {
        MoveList moves = context.moves[0];
        MoveGenerator.generatePseudoLegalMoves(position, moves);
        boolean white = position.isWhiteToMove();
        boolean inCheck = MoveGenerator.isKingAttacked(position, white);
        long pinned = inCheck ? 0L : MoveGenerator.pinnedPieces(position, white);
        int king = position.kingSquare(white);
        int enPassant = position.enPassantSquare();
        Position.State state = context.states[0];
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            boolean usuallyLegal = !inCheck
                    && MoveGenerator.isUsuallyLegal(position, move, pinned, king, enPassant);
            position.play(move, state);
            if (usuallyLegal || !MoveGenerator.isKingAttacked(position, white)) {
                addDivideEntry(position, depth, context, total, entries, move, state);
            }
            position.undo(move, state);
        }
        entries.sort(Comparator.comparing(entry -> Move.toString(entry.move())));
    }

    /**
     * Adds one legal root move's detailed counters.
     *
     * @param position position after the root move
     * @param depth root perft depth
     * @param context reusable move and undo-state scratch objects
     * @param total total counter to update
     * @param entries divide entries to update
     * @param move legal root move
     * @param state undo state filled by the root move
     */
    private static void addDivideEntry(
            Position position,
            int depth,
            PerftContext context,
            Counter total,
            List<DivideEntry> entries,
            short move,
            Position.State state) {
        Counter child = new Counter();
        if (depth == 1) {
            addLeaf(child, move, state, position, context, 1);
        } else {
            perft(position, depth - 1, context, 1, child);
        }
        total.add(child);
        entries.add(new DivideEntry(move, child.toStats()));
    }

    /**
     * Accumulates detailed counters for a subtree.
     *
     * @param position mutable position at the current ply
     * @param depth remaining depth
     * @param context reusable move and undo-state scratch objects
     * @param ply current recursion ply
     * @param counter target counter to update
     */
    private static void perft(
            Position position,
            int depth,
            PerftContext context,
            int ply,
            Counter counter) {
        if (depth == 0) {
            counter.nodes++;
            return;
        }

        MoveList moves = context.moves[ply];
        MoveGenerator.generatePseudoLegalMoves(position, moves);
        boolean white = position.isWhiteToMove();
        boolean inCheck = MoveGenerator.isKingAttacked(position, white);
        long pinned = inCheck ? 0L : MoveGenerator.pinnedPieces(position, white);
        int king = position.kingSquare(white);
        int enPassant = position.enPassantSquare();
        Position.State state = context.states[ply];
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            boolean usuallyLegal = !inCheck
                    && MoveGenerator.isUsuallyLegal(position, move, pinned, king, enPassant);
            position.play(move, state);
            if (usuallyLegal || !MoveGenerator.isKingAttacked(position, white)) {
                if (depth == 1) {
                    addLeaf(counter, move, state, position, context, ply + 1);
                } else {
                    perft(position, depth - 1, context, ply + 1, counter);
                }
            }
            position.undo(move, state);
        }
    }

    /**
     * Adds counters for one legal leaf move.
     *
     * @param counter target counter to update
     * @param move move
     * @param state undo state filled by the move
     * @param after position after the move
     * @param context reusable scratch objects for checkmate detection
     * @param scratchPly scratch ply used by legal-move existence checks
     */
    private static void addLeaf(
            Counter counter,
            short move,
            Position.State state,
            Position after,
            PerftContext context,
            int scratchPly) {
        counter.nodes++;
        if (state.capture()) {
            counter.captures++;
        }
        if (state.enPassantCapture()) {
            counter.enPassant++;
        }
        if (state.castle()) {
            counter.castles++;
        }
        if (((move >>> 12) & 0x7) != 0) {
            counter.promotions++;
        }

        boolean checkedSide = after.isWhiteToMove();
        if (MoveGenerator.isKingAttacked(after, checkedSide)) {
            counter.checks++;
            if (!MoveGenerator.hasLegalMove(after, context.moves[scratchPly], context.states[scratchPly])) {
                counter.checkmates++;
            }
        }
    }

    /**
     * Validates that a requested perft depth is non-negative.
     *
     * @param depth requested depth
     */
    private static void requireDepth(int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("depth must be non-negative");
        }
    }

    /**
     * Immutable detailed perft counters.
     *
     * @param nodes leaf nodes
     * @param captures capture leaves
     * @param enPassant en-passant leaves
     * @param castles castle leaves
     * @param promotions promotion leaves
     * @param checks checking leaves
     * @param checkmates checkmating leaves
     */
    public record Stats(
            long nodes,
            long captures,
            long enPassant,
            long castles,
            long promotions,
            long checks,
            long checkmates) {
    }

    /**
     * Timed detailed perft result for one root position.
     *
     * @param depth depth
     * @param stats counters
     * @param nanos elapsed nanoseconds
     */
    public record Result(
            int depth,
            Stats stats,
            long nanos) {

        /**
         * Returns measured nodes per second.
         *
         * @return nodes per second, or 0 when no measurable time elapsed
         */
        public double nodesPerSecond() {
            return nanos <= 0L ? 0.0 : stats.nodes() * 1_000_000_000.0 / nanos;
        }
    }

    /**
     * Detailed counters for one root move in divide output.
     *
     * @param move root move
     * @param stats counters below that root move
     */
    public record DivideEntry(
            short move,
            Stats stats) {
    }

    /**
     * Timed divide result with total and per-root-move counters.
     *
     * @param depth depth
     * @param total total counters
     * @param entries per-root-move counters
     * @param nanos elapsed nanoseconds
     */
    public record DivideResult(
            int depth,
            Stats total,
            List<DivideEntry> entries,
            long nanos) {

        /**
         * Returns measured nodes per second.
         *
         * @return nodes per second, or 0 when no measurable time elapsed
         */
        public double nodesPerSecond() {
            return nanos <= 0L ? 0.0 : total.nodes() * 1_000_000_000.0 / nanos;
        }
    }

    /**
     * Mutable counter accumulator used during recursive traversal.
     */
    private static final class Counter {

        /**
         * Accumulated leaf-node count.
         */
        private long nodes;

        /**
         * Accumulated number of capture leaves.
         */
        private long captures;

        /**
         * Accumulated number of en-passant capture leaves.
         */
        private long enPassant;

        /**
         * Accumulated number of castling leaves.
         */
        private long castles;

        /**
         * Accumulated number of promotion leaves.
         */
        private long promotions;

        /**
         * Accumulated number of leaves giving check.
         */
        private long checks;

        /**
         * Accumulated number of leaves giving checkmate.
         */
        private long checkmates;

        /**
         * Adds another accumulator into this one.
         *
         * @param other source
         */
        private void add(Counter other) {
            nodes += other.nodes;
            captures += other.captures;
            enPassant += other.enPassant;
            castles += other.castles;
            promotions += other.promotions;
            checks += other.checks;
            checkmates += other.checkmates;
        }

        /**
         * Converts the current accumulator state to immutable stats.
         *
         * @return stats
         */
        private Stats toStats() {
            return new Stats(nodes, captures, enPassant, castles, promotions, checks, checkmates);
        }
    }

    /**
     * Scratch arrays for one detailed perft traversal.
     *
     * <p>
     * A traversal owns one move list and one undo state per ply. Reusing them
     * keeps detailed perft allocation-light while preserving simple recursion.
     * </p>
     */
    private static final class PerftContext {

        /**
         * Reusable pseudo-legal move lists indexed by recursion ply.
         */
        private final MoveList[] moves;

        /**
         * Reusable undo states indexed by recursion ply.
         */
        private final Position.State[] states;

        /**
         * Creates scratch arrays large enough for a traversal of the given depth.
         *
         * @param depth requested perft depth
         */
        private PerftContext(int depth) {
            int size = Math.max(1, depth + 1);
            this.moves = new MoveList[size];
            this.states = new Position.State[size];
            for (int i = 0; i < size; i++) {
                moves[i] = new MoveList();
                states[i] = new Position.State();
            }
        }
    }
}
