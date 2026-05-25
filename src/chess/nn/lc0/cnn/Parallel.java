package chess.nn.lc0.cnn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import chess.nn.lc0.cnn.Network.DebugValue;
import chess.nn.lc0.cnn.Network.Prediction;

/**
 * Fork-join helper used when channel counts justify parallel work.
 *
 * <p>
 * Use {@code -Dcrtk.lc0.threads=N} to override thread count (default:
 * {@code availableProcessors()}).
 */
final class Parallel {

    /**
     * Number of threads configured for LC0 convolutions.
     */
    static final int THREADS = parseThreads();

    /**
     * Minimum number of channels before parallelism is enabled.
     */
    static final int MIN_CHANNELS = 128;

    /**
     * Optional {@link ForkJoinPool} used when more than one thread is configured.
     */
    static final ForkJoinPool POOL = (THREADS > 1) ? new ForkJoinPool(THREADS) : null;

    /**
     * Reads the configured thread count or falls back to available processors.
     *
     * @return resolved thread count (at least 1)
     */
    private static int parseThreads() {
        String v = System.getProperty("crtk.lc0.threads");
        if (v == null || v.isBlank()) {
            return Math.max(1, Runtime.getRuntime().availableProcessors());
        }
        try {
            return Math.max(1, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return Math.max(1, Runtime.getRuntime().availableProcessors());
        }
    }

    /**
     * Returns {@code true} when parallelism should be used for the provided channel
     * count.
     *
     * @param channels output channel count
     * @return true if parallel execution should be used
     */
    static boolean enabledForChannels(int channels) {
        return POOL != null && channels >= MIN_CHANNELS;
    }

    /**
     * Converts a range into work that can be executed by fork/join tasks.
     */
    interface RangeBody {

        /**
         * Executes work for a half-open channel range.
         *
         * @param startInclusive inclusive start index
         * @param endExclusive   exclusive end index
         */
        void run(int startInclusive, int endExclusive);
    }

    /**
     * Executes {@link RangeBody} either sequentially or on the fork/join pool.
     *
     * @param startInclusive inclusive start index
     * @param endExclusive   exclusive end index
     * @param body           work body to execute
     */
    static void forRange(int startInclusive, int endExclusive, RangeBody body) {
        if (POOL == null) {
            body.run(startInclusive, endExclusive);
            return;
        }
        POOL.invoke(new RangeTask(startInclusive, endExclusive, body));
    }

    /**
     * Task used by {@link ForkJoinPool} to split channel ranges.
     */
    private static final class RangeTask extends RecursiveAction {

		 /**
		 * Shared serial version uid constant.
		 */
		 @java.io.Serial
		private static final long serialVersionUID = 1L;

        /**
         * Grain size used to stop splitting ranges.
         */
        private static final int GRAIN = 16;

        /**
         * Inclusive start index for this task's range.
         */
        private final int start;

        /**
         * Exclusive end index for this task's range.
         */
        private final int end;

        /**
         * Work body executed for each range chunk.
         */
        private transient RangeBody body;

        /**
         * Records the range and work body for the task.
         *
         * @param start inclusive start index for this range
         * @param end   exclusive end index for this range
         * @param body  work body to execute
         */
        RangeTask(int start, int end, RangeBody body) {
            this.start = start;
            this.end = end;
            this.body = body;
        }

        /**
         * Splits the range recursively until small enough to execute directly.
         */
        @Override
        protected void compute() {
            int len = end - start;
            if (len <= GRAIN) {
                body.run(start, end);
                return;
            }
            int mid = start + (len / 2);
            RangeTask left = new RangeTask(start, mid, body);
            RangeTask right = new RangeTask(mid, end, body);
            invokeAll(left, right);
        }
    }
}
