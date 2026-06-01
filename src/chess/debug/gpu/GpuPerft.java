package chess.debug.gpu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import chess.core.Move;
import chess.core.MoveGenerator;
import chess.core.MoveList;
import chess.core.Position;
import chess.debug.Perft;
import chess.debug.SplitPerft;

/**
 * GPU-accelerated perft driver.
 *
 * <p>
 * Combines the CPU split-depth expansion in {@link SplitPerft} with a native
 * bulk counter ({@link NativePerftBackend}). The CPU expands the move tree to a
 * split depth, the device counts {@code perft(remainingDepth)} for every frontier
 * position in parallel, and the per-position counts are summed.
 * </p>
 *
 * <p>
 * The result is identical to {@code MoveGenerator.perft} regardless of split
 * depth; the split depth only trades CPU expansion cost for device batch size.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GpuPerft {

    /**
     * Maximum per-frontier depth the native kernels can count.
     *
     * <p>
     * The device kernels use a fixed-size explicit stack ({@code MAX_PERFT_DEPTH}
     * in {@code native/common/perft_core.h}). Counting more than this many plies
     * below a single frontier position is physically infeasible anyway, but the
     * driver rejects it explicitly rather than letting the kernel return a silent
     * zero. Increase the split depth to keep the remaining depth within bounds.
     * </p>
     */
    public static final int MAX_REMAINING_DEPTH = 16;

    /**
     * Utility class; prevents instantiation.
     */
    private GpuPerft() {
    }

    /**
     * Returns whether a native perft backend is available.
     *
     * @return true when GPU perft can run
     */
    public static boolean isAvailable() {
        return NativePerftBackend.isAvailable();
    }

    /**
     * Returns whether the selected native backend has detailed perft counters.
     *
     * @return true when device counters include captures/checks/etc.
     */
    public static boolean isDetailedAvailable() {
        return NativePerftBackend.isDetailedAvailable();
    }

    /**
     * Returns the selected backend identifier (cuda/rocm/oneapi/none).
     *
     * @return backend id
     */
    public static String backendName() {
        return NativePerftBackend.name();
    }

    /**
     * Returns a {@link SplitPerft.BulkCounter} backed by the native device.
     *
     * @return native bulk counter
     */
    public static SplitPerft.BulkCounter bulkCounter() {
        return (frontier, remainingDepth) -> {
            if (remainingDepth > MAX_REMAINING_DEPTH) {
                throw new IllegalArgumentException("native perft remaining depth " + remainingDepth
                        + " exceeds the device limit of " + MAX_REMAINING_DEPTH
                        + "; raise the split depth so depth - split <= " + MAX_REMAINING_DEPTH);
            }
            return NativePerftBackend.bulkPerft(PositionCodec.pack(frontier), frontier.length, remainingDepth);
        };
    }

    /**
     * Runs GPU perft with an explicit split depth.
     *
     * @param root root position
     * @param depth total perft depth
     * @param splitDepth CPU expansion depth
     * @return total legal leaf-node count
     */
    public static long perft(Position root, int depth, int splitDepth) {
        return SplitPerft.perft(root, depth, splitDepth, bulkCounter());
    }

    /**
     * Runs GPU perft with a default split depth chosen from the total depth.
     *
     * @param root root position
     * @param depth total perft depth
     * @return total legal leaf-node count
     */
    public static long perft(Position root, int depth) {
        return perft(root, depth, defaultSplitDepth(depth));
    }

    /**
     * Number of detailed counters returned per frontier position by the device.
     */
    private static final int DETAIL_FIELDS = 7;

    /**
     * Runs detailed GPU perft, returning the full counter breakdown (captures,
     * en passant, castles, promotions, checks, checkmates) like
     * {@link Perft#run(Position, int)}.
     *
     * <p>
     * Leaves are classified in the device kernel, so the split must leave at least
     * one ply for the device; a split that reaches the leaf ply is reduced by one.
     * If the loaded native library only supports node counts, this method falls
     * back to the Java detailed perft implementation.
     * </p>
     *
     * @param root root position
     * @param depth total perft depth
     * @param splitDepth CPU expansion depth
     * @return aggregated detailed perft statistics
     */
    public static Perft.Stats perftDetailed(Position root, int depth, int splitDepth) {
        if (depth <= 0) {
            return new Perft.Stats(1L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
        if (!isDetailedAvailable()) {
            return Perft.run(root, depth).stats();
        }
        int split = Math.min(splitDepth, depth - 1);
        if (split < 0) {
            split = 0;
        }
        Position[] frontier = SplitPerft.expandFrontier(root, split);
        if (frontier.length == 0) {
            return new Perft.Stats(0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
        int remaining = depth - split;
        if (remaining > MAX_REMAINING_DEPTH) {
            throw new IllegalArgumentException("native perft remaining depth " + remaining
                    + " exceeds the device limit of " + MAX_REMAINING_DEPTH
                    + "; raise the split depth so depth - split <= " + MAX_REMAINING_DEPTH);
        }
        long[] flat = NativePerftBackend.bulkPerftDetailed(
                PositionCodec.pack(frontier), frontier.length, remaining);
        long nodes = 0;
        long captures = 0;
        long enPassant = 0;
        long castles = 0;
        long promotions = 0;
        long checks = 0;
        long checkmates = 0;
        for (int i = 0; i < frontier.length; i++) {
            int base = i * DETAIL_FIELDS;
            nodes += flat[base];
            captures += flat[base + 1];
            enPassant += flat[base + 2];
            castles += flat[base + 3];
            promotions += flat[base + 4];
            checks += flat[base + 5];
            checkmates += flat[base + 6];
        }
        return new Perft.Stats(nodes, captures, enPassant, castles, promotions, checks, checkmates);
    }

    /**
     * Runs detailed GPU perft with a default split depth.
     *
     * @param root root position
     * @param depth total perft depth
     * @return aggregated detailed perft statistics
     */
    public static Perft.Stats perftDetailed(Position root, int depth) {
        return perftDetailed(root, depth, defaultSplitDepth(depth));
    }

    /**
     * Runs per-root-move divide perft through the native device backend.
     *
     * <p>
     * The root move expansion stays on the CPU so the CLI can report one row per
     * legal root move. Each child subtree is then counted through the same
     * split-depth GPU path used by total perft. Detailed divide uses detailed
     * device counters below each root child; depth-one leaves are classified on
     * the CPU because the root move itself is the leaf.
     * </p>
     *
     * @param root root position
     * @param depth total perft depth
     * @param splitDepth CPU expansion depth measured from the root
     * @param detailed true to return capture/check/etc counters, false for node-only
     * @return timed divide result
     */
    public static Perft.DivideResult divide(Position root, int depth, int splitDepth, boolean detailed) {
        if (depth < 0) {
            throw new IllegalArgumentException("depth must be non-negative");
        }
        if (splitDepth < 0) {
            throw new IllegalArgumentException("splitDepth must be non-negative");
        }
        if (detailed && !isDetailedAvailable()) {
            return Perft.divide(root, depth);
        }
        long start = System.nanoTime();
        if (depth == 0) {
            return new Perft.DivideResult(depth, stats(1L), List.of(), System.nanoTime() - start);
        }

        MoveList moves = root.legalMoves();
        List<Perft.DivideEntry> entries = new ArrayList<>(moves.size());
        Perft.Stats total = stats(0L);
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            Position child = root.copy();
            Position.State state = new Position.State();
            child.play(move, state);
            Perft.Stats stats = detailed
                    ? divideDetailedStats(move, state, child, depth, splitDepth)
                    : divideNodeStats(child, depth, splitDepth);
            total = add(total, stats);
            entries.add(new Perft.DivideEntry(move, stats));
        }
        if (detailed) {
            entries.sort(Comparator.comparing(entry -> Move.toString(entry.move())));
        }
        return new Perft.DivideResult(depth, total, Collections.unmodifiableList(entries),
                System.nanoTime() - start);
    }

    /**
     * Counts one root child for node-only divide.
     *
     * @param child position after the root move
     * @param depth root depth
     * @param splitDepth split depth measured from the root
     * @return node-only stats
     */
    private static Perft.Stats divideNodeStats(Position child, int depth, int splitDepth) {
        long nodes = depth == 1 ? 1L : perft(child, depth - 1, childSplitDepth(splitDepth));
        return stats(nodes);
    }

    /**
     * Counts one root child for detailed divide.
     *
     * @param move root move
     * @param state undo state produced by the root move
     * @param child position after the root move
     * @param depth root depth
     * @param splitDepth split depth measured from the root
     * @return detailed stats
     */
    private static Perft.Stats divideDetailedStats(
            short move,
            Position.State state,
            Position child,
            int depth,
            int splitDepth) {
        if (depth == 1) {
            return leafStats(move, state, child);
        }
        return perftDetailed(child, depth - 1, childSplitDepth(splitDepth));
    }

    /**
     * Converts a root split depth to the split depth below one already-played
     * root move.
     *
     * @param splitDepth split depth measured from the root
     * @return child split depth
     */
    private static int childSplitDepth(int splitDepth) {
        return Math.max(0, splitDepth - 1);
    }

    /**
     * Classifies a depth-one divide leaf.
     *
     * @param move leaf move
     * @param state undo state produced by the move
     * @param after position after the move
     * @return detailed leaf stats
     */
    private static Perft.Stats leafStats(short move, Position.State state, Position after) {
        long checks = 0L;
        long checkmates = 0L;
        boolean checkedSide = after.isWhiteToMove();
        if (MoveGenerator.isKingAttacked(after, checkedSide)) {
            checks = 1L;
            if (!MoveGenerator.hasLegalMove(after, new MoveList(), new Position.State())) {
                checkmates = 1L;
            }
        }
        return new Perft.Stats(
                1L,
                state.capture() ? 1L : 0L,
                state.enPassantCapture() ? 1L : 0L,
                state.castle() ? 1L : 0L,
                ((move >>> 12) & 0x7) != 0 ? 1L : 0L,
                checks,
                checkmates);
    }

    /**
     * Creates node-only stats.
     *
     * @param nodes node count
     * @return stats
     */
    private static Perft.Stats stats(long nodes) {
        return new Perft.Stats(nodes, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    /**
     * Adds two immutable stat records.
     *
     * @param first first stats
     * @param second second stats
     * @return summed stats
     */
    private static Perft.Stats add(Perft.Stats first, Perft.Stats second) {
        return new Perft.Stats(
                first.nodes() + second.nodes(),
                first.captures() + second.captures(),
                first.enPassant() + second.enPassant(),
                first.castles() + second.castles(),
                first.promotions() + second.promotions(),
                first.checks() + second.checks(),
                first.checkmates() + second.checkmates());
    }

    /**
     * Picks a split depth that produces a sizable device batch while keeping the
     * CPU expansion and host-to-device transfer small.
     *
     * <p>
     * Benchmarks (startpos) show a frontier of roughly 10^5 positions — split
     * depth 4 from the start — saturates the device without the expansion or the
     * transfer dominating; splitting one ply deeper explodes both. Shallow depths
     * use a smaller split, and very deep searches can afford one more ply.
     * </p>
     *
     * @param depth total perft depth
     * @return CPU expansion depth
     */
    public static int defaultSplitDepth(int depth) {
        if (depth <= 3) {
            return 0;
        }
        if (depth == 4) {
            return 2;
        }
        if (depth == 5) {
            return 3;
        }
        return depth >= 10 ? 5 : 4;
    }
}
