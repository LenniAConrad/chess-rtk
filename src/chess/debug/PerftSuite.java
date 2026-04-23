package chess.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import chess.core.MoveGenerator;
import chess.core.Position;

/**
 * Self-contained perft validation suite for critical standard and Chess960
 * positions.
 *
 * <p>
 * The suite compares the current optimized node-only perft implementation
 * against stored reference counts. It never starts an external engine process,
 * so it remains deterministic, offline, and suitable for regression runs in
 * restricted environments.
 * </p>
 *
 * @author Lennart A. Conrad
 * @since 2025
 */
public final class PerftSuite {

    /**
     * Default depth used by the CLI validation suite.
     */
    public static final int DEFAULT_MAX_DEPTH = 6;

    /**
     * Deepest depth covered by the stored reference table.
     */
    public static final int MAX_REFERENCE_DEPTH = 6;

    /**
     * Critical perft cases used by the validation suite.
     */
    private static final Case[] CASES = {
            new Case(
                    "Initial position",
                    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    nodes(20L, 400L, 8_902L, 197_281L, 4_865_609L, 119_060_324L)),
            new Case(
                    "Position 2 Kiwipete",
                    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                    nodes(48L, 2_039L, 97_862L, 4_085_603L, 193_690_690L, 8_031_647_685L)),
            new Case(
                    "Position 3",
                    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
                    nodes(14L, 191L, 2_812L, 43_238L, 674_624L, 11_030_083L)),
            new Case(
                    "Position 4",
                    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                    nodes(6L, 264L, 9_467L, 422_333L, 15_833_292L, 706_045_033L)),
            new Case(
                    "Position 4 mirrored",
                    "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
                    nodes(6L, 264L, 9_467L, 422_333L, 15_833_292L, 706_045_033L)),
            new Case(
                    "Position 5",
                    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
                    nodes(44L, 1_486L, 62_379L, 2_103_487L, 89_941_194L, 3_048_196_529L)),
            new Case(
                    "Position 6",
                    "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
                    nodes(46L, 2_079L, 89_890L, 3_894_594L, 164_075_551L, 6_923_051_137L)),
            new Case(
                    "Open castling lanes",
                    "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
                    nodes(26L, 568L, 13_744L, 314_346L, 7_594_526L, 179_862_938L)),
            new Case(
                    "En-passant pin",
                    "8/2p5/8/KP1p2kr/5p2/8/4P1P1/6R1 w - d6 0 1",
                    nodes(15L, 256L, 4_346L, 82_383L, 1_444_003L, 27_722_538L)),
            new Case(
                    "Promotion race",
                    "4k3/P6P/8/8/8/8/p6p/4K3 w - - 0 1",
                    nodes(13L, 128L, 1_638L, 21_118L, 313_321L, 4_685_890L)),
            new Case(
                    "Chess960 start 0",
                    "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1",
                    nodes(20L, 400L, 9_006L, 201_143L, 4_975_808L, 121_983_565L)),
            new Case(
                    "Chess960 start 959",
                    "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1",
                    nodes(20L, 400L, 9_006L, 201_143L, 4_973_573L, 121_872_948L)),
            new Case(
                    "Chess960 midgame",
                    "bb3rkr/pq1p2pp/1p2pn2/2p2p2/2P2PnP/1P2PN2/PQBP1NP1/B4RKR w HFhf - 9 10",
                    nodes(37L, 1_328L, 48_708L, 1_781_194L, 65_310_141L, 2_412_004_068L)),
            new Case(
                    "Chess960 open castles",
                    "4k3/8/8/8/8/8/8/RK5R w HA - 0 1",
                    nodes(25L, 106L, 2_922L, 16_052L, 465_855L, 2_422_106L)),
            new Case(
                    "Knight swarm",
                    "krN1N1N1/pp1N1N1N/N1N1N1N1/1N1N1N1N/N1N1N1N1/1N1N1N1N/N1N1N1N1/1N1N1N1K w - - 0 1",
                    nodes(162L, 616L, 91_643L, 554_860L, 76_229_306L, 528_930_290L))
    };

    /**
     * Prevents instantiation of this stateless utility class.
     */
    private PerftSuite() {
        // utility
    }

    /**
     * Returns the number of comparison rows for a validation depth.
     *
     * @param maxDepth validation depth
     * @return row count
     */
    public static int rowCount(int maxDepth) {
        requireDepth(maxDepth);
        return CASES.length;
    }

    /**
     * Runs the validation suite and prints an aligned result table.
     *
     * @param maxDepth validation depth to include
     * @param progress optional progress callback after each row
     * @return immutable validation summary
     * @throws InterruptedException when interrupted while waiting for workers
     */
    public static Summary printValidation(
            int maxDepth,
            Runnable progress) throws InterruptedException {
        Summary summary = validate(maxDepth, progress);
        print(summary);
        return summary;
    }

    /**
     * Runs the validation suite without printing the result table.
     *
     * @param maxDepth validation depth to include
     * @param progress optional progress callback after each row
     * @return immutable validation summary
     * @throws InterruptedException when interrupted while waiting for workers
     */
    public static Summary validate(
            int maxDepth,
            Runnable progress) throws InterruptedException {
        return validate(maxDepth, 1, progress);
    }

    /**
     * Runs the validation suite without printing the result table.
     *
     * @param maxDepth validation depth to include
     * @param threads worker thread count
     * @param progress optional progress callback after each row
     * @return immutable validation summary
     * @throws InterruptedException when interrupted while waiting for workers
     */
    public static Summary validate(
            int maxDepth,
            int threads,
            Runnable progress) throws InterruptedException {
        requireDepth(maxDepth);
        requireThreads(threads);
        if (threads == 1) {
            return validateSequentially(maxDepth, progress);
        }
        return validateConcurrently(maxDepth, threads, progress);
    }

    /**
     * Runs all validation rows on the caller thread.
     *
     * @param maxDepth validation depth
     * @param progress optional progress callback
     * @return immutable validation summary
     */
    private static Summary validateSequentially(
            int maxDepth,
            Runnable progress) {
        List<Row> rows = new ArrayList<>();
        long started = System.nanoTime();
        for (int caseIndex = 0; caseIndex < CASES.length; caseIndex++) {
            rows.add(validateCase(maxDepth, caseIndex));
            if (progress != null) {
                progress.run();
            }
        }
        return new Summary(maxDepth, Collections.unmodifiableList(rows), System.nanoTime() - started);
    }

    /**
     * Runs validation rows on a fixed-size worker pool.
     *
     * @param maxDepth validation depth
     * @param threads requested worker count
     * @param progress optional progress callback
     * @return immutable validation summary
     * @throws InterruptedException when interrupted while waiting for workers
     */
    private static Summary validateConcurrently(
            int maxDepth,
            int threads,
            Runnable progress) throws InterruptedException {
        int workerCount = Math.min(threads, CASES.length);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CompletionService<Row> completion = new ExecutorCompletionService<>(executor);
        long started = System.nanoTime();
        try {
            for (int caseIndex = 0; caseIndex < CASES.length; caseIndex++) {
                final int index = caseIndex;
                completion.submit(validateTask(maxDepth, index));
            }
            Row[] rows = new Row[CASES.length];
            for (int completed = 0; completed < CASES.length; completed++) {
                Row row = completion.take().get();
                rows[row.caseNumber() - 1] = row;
                if (progress != null) {
                    progress.run();
                }
            }
            return new Summary(maxDepth, orderedRows(rows), System.nanoTime() - started);
        } catch (ExecutionException ex) {
            throw unwrapExecutionException(ex);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Builds one worker task for a validation row.
     *
     * @param maxDepth validation depth
     * @param caseIndex zero-based case index
     * @return row-producing task
     */
    private static Callable<Row> validateTask(int maxDepth, int caseIndex) {
        return () -> validateCase(maxDepth, caseIndex);
    }

    /**
     * Validates one suite case.
     *
     * @param maxDepth validation depth
     * @param caseIndex zero-based case index
     * @return validation row
     */
    private static Row validateCase(int maxDepth, int caseIndex) {
        Case testCase = CASES[caseIndex];
        Position position = Position.fromFen(testCase.fen());
        long calculatedStart = System.nanoTime();
        long calculated = MoveGenerator.perft(position, maxDepth);
        long calculatedNanos = System.nanoTime() - calculatedStart;
        return new Row(caseIndex + 1, testCase.name(), maxDepth, testCase.fen(),
                testCase.truth(maxDepth), calculated, calculatedNanos);
    }

    /**
     * Converts a dense row array to an immutable ordered list.
     *
     * @param rows row array indexed by case number
     * @return immutable ordered rows
     */
    private static List<Row> orderedRows(Row[] rows) {
        List<Row> out = new ArrayList<>(rows.length);
        Collections.addAll(out, rows);
        return Collections.unmodifiableList(out);
    }

    /**
     * Converts worker failures to unchecked exceptions.
     *
     * @param ex execution failure
     * @return runtime exception to throw
     */
    private static RuntimeException unwrapExecutionException(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }

    /**
     * Prints one aligned comparison table.
     *
     * @param summary comparison summary
     */
    @SuppressWarnings("java:S106")
    public static void print(Summary summary) {
        int fenWidth = Math.max("FEN".length(), summary.rows().stream().mapToInt(row -> row.fen().length()).max().orElse(0));
        String header = "%2s  %5s  %-" + fenWidth + "s  %15s  %15s  %12s  %5s%n";
        String rowFormat = "%2d  %5d  %-" + fenWidth + "s  %15d  %15d  %12s  %5s%n";

        System.out.printf("Perft validation (depth %d)%n", summary.maxDepth());
        System.out.printf(header, "No", "Depth", "FEN", "Truth", "Calculated", "Speed", "Match");
        for (Row row : summary.rows()) {
            System.out.printf(rowFormat,
                    row.caseNumber(),
                    row.depth(),
                    row.fen(),
                    row.truth(),
                    row.calculated(),
                    speed(row),
                    row.matches());
        }
        System.out.printf("Summary: rows=%d match=%s time-ms=%.3f%n",
                summary.rows().size(),
                summary.matches(),
                summary.nanos() / 1_000_000.0);
    }

    /**
     * Validates a maximum perft depth.
     *
     * @param depth requested validation depth
     */
    private static void requireDepth(int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("depth must be positive");
        }
        if (depth > MAX_REFERENCE_DEPTH) {
            throw new IllegalArgumentException("depth must be at most " + MAX_REFERENCE_DEPTH);
        }
    }

    /**
     * Validates a worker thread count.
     *
     * @param threads requested worker threads
     */
    private static void requireThreads(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive");
        }
    }

    /**
     * Formats calculated perft speed for a row.
     *
     * @param row comparison row
     * @return human-readable nodes-per-second text
     */
    private static String speed(Row row) {
        if (row.calculatedNanos() <= 0L) {
            return "0 nps";
        }
        double nps = row.calculated() * 1_000_000_000.0 / row.calculatedNanos();
        if (nps >= 1_000_000.0) {
            return String.format(Locale.ROOT, "%.1fM nps", nps / 1_000_000.0);
        }
        if (nps >= 1_000.0) {
            return String.format(Locale.ROOT, "%.1fk nps", nps / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.0f nps", nps);
    }

    /**
     * Builds a one-indexed truth table for one case.
     *
     * @param values node counts for depths one through {@link #MAX_REFERENCE_DEPTH}
     * @return one-indexed node count array
     */
    private static long[] nodes(long... values) {
        if (values.length != MAX_REFERENCE_DEPTH) {
            throw new IllegalArgumentException("reference table must contain " + MAX_REFERENCE_DEPTH + " depths");
        }
        long[] out = new long[MAX_REFERENCE_DEPTH + 1];
        System.arraycopy(values, 0, out, 1, values.length);
        return out;
    }

    /**
     * One validation position.
     *
     * @param name display name retained for diagnostics
     * @param fen FEN
     * @param truthByDepth one-indexed reference node counts
     */
    private static final class Case {

        /**
         * Display name retained for diagnostics.
         */
        private final String name;

        /**
         * FEN.
         */
        private final String fen;

        /**
         * One-indexed reference node counts.
         */
        private final long[] truthByDepth;

        /**
         * Creates an immutable validation case.
         *
         * @param name display name retained for diagnostics
         * @param fen FEN
         * @param truthByDepth one-indexed reference node counts
         */
        private Case(String name, String fen, long[] truthByDepth) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("case name must not be blank");
            }
            if (fen == null || fen.isBlank()) {
                throw new IllegalArgumentException("case FEN must not be blank");
            }
            if (truthByDepth == null || truthByDepth.length != MAX_REFERENCE_DEPTH + 1) {
                throw new IllegalArgumentException("case truth table has invalid length");
            }
            this.name = name;
            this.fen = fen;
            this.truthByDepth = truthByDepth.clone();
        }

        /**
         * Returns the diagnostic display name.
         *
         * @return case name
         */
        private String name() {
            return name;
        }

        /**
         * Returns the position FEN.
         *
         * @return FEN
         */
        private String fen() {
            return fen;
        }

        /**
         * Compares cases by value, including the array contents.
         *
         * @param other candidate object
         * @return true when all fields match
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof Case that
                    && name.equals(that.name)
                    && fen.equals(that.fen)
                    && Arrays.equals(truthByDepth, that.truthByDepth);
        }

        /**
         * Computes a hash from every case field.
         *
         * @return content-based hash
         */
        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + fen.hashCode();
            result = 31 * result + Arrays.hashCode(truthByDepth);
            return result;
        }

        /**
         * Formats the case with readable truth-table contents.
         *
         * @return diagnostic text
         */
        @Override
        public String toString() {
            return "Case[name="
                    + name
                    + ", fen="
                    + fen
                    + ", truthByDepth="
                    + Arrays.toString(truthByDepth)
                    + "]";
        }

        /**
         * Returns the stored truth count for one depth.
         *
         * @param depth requested depth
         * @return reference node count
         */
        private long truth(int depth) {
            return truthByDepth[depth];
        }
    }

    /**
     * One validation table row.
     *
     * @param caseNumber one-based case index
     * @param name case name retained for programmatic consumers
     * @param depth perft depth
     * @param fen case FEN
     * @param truth stored reference node count
     * @param calculated node count from this package
     * @param calculatedNanos elapsed nanoseconds for this package's calculation
     */
    public record Row(
            int caseNumber,
            String name,
            int depth,
            String fen,
            long truth,
            long calculated,
            long calculatedNanos) {

        /**
         * Returns whether the calculated node count agrees with the stored truth.
         *
         * @return true when truth and calculated counts are equal
         */
        public boolean matches() {
            return truth == calculated;
        }
    }

    /**
     * Full validation result.
     *
     * @param maxDepth requested validation depth
     * @param rows comparison rows
     * @param nanos elapsed nanoseconds
     */
    public record Summary(
            int maxDepth,
            List<Row> rows,
            long nanos) {

        /**
         * Creates a summary with an immutable row list.
         */
        public Summary {
            rows = List.copyOf(rows);
        }

        /**
         * Returns whether every comparison row matched.
         *
         * @return true when every row matched
         */
        public boolean matches() {
            return rows.stream().allMatch(Row::matches);
        }
    }
}
