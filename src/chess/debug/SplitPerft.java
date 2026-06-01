package chess.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import chess.core.MoveGenerator;
import chess.core.MoveList;
import chess.core.Position;

/**
 * Split-depth perft driver used by accelerator (GPU) backends.
 *
 * <p>
 * Deep perft on a GPU cannot follow the recursive make/undo shape of
 * {@link Perft}: GPUs need a large batch of independent work items. This class
 * expands the move tree on the CPU down to a fixed <em>split depth</em>,
 * producing a flat frontier of legal positions, and then hands that frontier to
 * a {@link BulkCounter} that computes {@code perft(remainingDepth)} for every
 * frontier position. The per-position counts are summed to the full node count.
 * </p>
 *
 * <p>
 * The frontier is the set of legal positions reached after exactly
 * {@code splitDepth} plies, so:
 * </p>
 * <ul>
 * <li>{@code splitDepth == 0} degenerates to a single bulk call on the root,
 * i.e. plain {@code perft(depth)};</li>
 * <li>{@code splitDepth >= depth} makes every frontier node a leaf, so the node
 * count is simply the frontier size.</li>
 * </ul>
 *
 * <p>
 * The total node count is independent of the split depth, which is exactly what
 * makes this a useful correctness contract: a backend's bulk counter can be
 * validated against {@link MoveGenerator#perft(Position, int)} at every split
 * depth. The split depth only trades CPU expansion cost against device batch
 * size.
 * </p>
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class SplitPerft {

    /**
     * Prevents instantiation of this stateless utility class.
     */
    private SplitPerft() {
        // utility
    }

    /**
     * Counts {@code perft(remainingDepth)} for a batch of frontier positions.
     *
     * <p>
     * Implementations may run on the CPU or offload to an accelerator. The
     * returned array must contain one leaf-node count per frontier position, in
     * the same order as the input.
     * </p>
     */
    @FunctionalInterface
    public interface BulkCounter {

        /**
         * Counts leaf nodes below each frontier position.
         *
         * @param frontier legal frontier positions (not modified)
         * @param remainingDepth non-negative depth below each frontier position
         * @return per-position leaf-node counts, aligned with {@code frontier}
         */
        long[] count(Position[] frontier, int remainingDepth);
    }

    /**
     * Sequential CPU bulk counter using the core recursive perft.
     */
    public static final BulkCounter CPU = SplitPerft::cpuCount;

    /**
     * Runs split-depth perft from a root position with an explicit counter.
     *
     * @param root root position
     * @param depth non-negative total perft depth
     * @param splitDepth non-negative CPU expansion depth; clamped to {@code depth}
     * @param counter frontier bulk counter
     * @return total legal leaf-node count
     */
    public static long perft(Position root, int depth, int splitDepth, BulkCounter counter) {
        if (depth < 0) {
            throw new IllegalArgumentException("depth must be non-negative");
        }
        if (splitDepth < 0) {
            throw new IllegalArgumentException("splitDepth must be non-negative");
        }
        if (counter == null) {
            throw new IllegalArgumentException("counter == null");
        }
        if (depth == 0) {
            return 1L;
        }
        int split = Math.min(splitDepth, depth);
        Position[] frontier = expandFrontier(root, split);
        if (frontier.length == 0) {
            return 0L;
        }
        int remaining = depth - split;
        if (remaining == 0) {
            return frontier.length;
        }
        long[] counts = counter.count(frontier, remaining);
        if (counts.length != frontier.length) {
            throw new IllegalStateException("counter returned " + counts.length
                    + " counts for " + frontier.length + " frontier positions");
        }
        long total = 0L;
        for (long count : counts) {
            total += count;
        }
        return total;
    }

    /**
     * Expands the legal move tree to a fixed ply and returns the frontier.
     *
     * @param root root position
     * @param splitDepth expansion depth in plies
     * @return legal positions reached after exactly {@code splitDepth} plies
     */
    public static Position[] expandFrontier(Position root, int splitDepth) {
        List<Position> frontier = new ArrayList<>();
        expand(root.copy(), splitDepth, frontier);
        return frontier.toArray(new Position[0]);
    }

    /**
     * Recursively collects legal positions at the split ply.
     *
     * @param position current position (owned copy, safe to enumerate)
     * @param remaining remaining expansion plies
     * @param out frontier accumulator
     */
    private static void expand(Position position, int remaining, List<Position> out) {
        if (remaining == 0) {
            out.add(position);
            return;
        }
        MoveList moves = position.legalMoves();
        for (int i = 0; i < moves.size(); i++) {
            expand(position.copy().play(moves.raw(i)), remaining - 1, out);
        }
    }

    /**
     * Sequential CPU implementation of {@link BulkCounter}.
     *
     * @param frontier frontier positions
     * @param remainingDepth depth below each frontier position
     * @return per-position leaf-node counts
     */
    private static long[] cpuCount(Position[] frontier, int remainingDepth) {
        long[] counts = new long[frontier.length];
        for (int i = 0; i < frontier.length; i++) {
            counts[i] = MoveGenerator.perft(frontier[i], remainingDepth);
        }
        return counts;
    }

    /**
     * Builds a parallel CPU bulk counter backed by a fixed worker pool.
     *
     * <p>
     * This is the reference baseline an accelerator backend is benchmarked
     * against: it shares the exact split/recombine structure of the GPU path,
     * differing only in where each frontier subtree is counted.
     * </p>
     *
     * @param threads worker thread count (must be positive)
     * @return parallel CPU bulk counter
     */
    public static BulkCounter cpuParallel(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive");
        }
        return (frontier, remainingDepth) -> parallelCount(frontier, remainingDepth, threads);
    }

    /**
     * Counts each frontier subtree across a worker pool.
     *
     * @param frontier frontier positions
     * @param remainingDepth depth below each frontier position
     * @param threads worker thread count
     * @return per-position leaf-node counts
     */
    private static long[] parallelCount(Position[] frontier, int remainingDepth, int threads) {
        long[] counts = new long[frontier.length];
        int workers = Math.min(threads, Math.max(1, frontier.length));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> futures = new ArrayList<>(frontier.length);
            for (int i = 0; i < frontier.length; i++) {
                final int index = i;
                futures.add(executor.submit(
                        () -> counts[index] = MoveGenerator.perft(frontier[index], remainingDepth)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            return counts;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while bulk counting", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } finally {
            executor.shutdownNow();
        }
    }
}
