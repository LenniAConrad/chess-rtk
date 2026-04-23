package chess.debug;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * Perft comparison suite for critical standard and Chess960 positions.
 *
 * <p>
 * The suite compares this package's node-only perft result against a live UCI
 * engine truth source. It uses one requested depth per position so the output
 * stays compact while still exercising castling, en-passant, promotion,
 * checking, and Chess960 castling paths.
 * </p>
 *
 * @author Lennart A. Conrad
 * @since 2025
 */
public final class PerftSuite {

    /**
     * Default depth used by the CLI comparison suite.
     */
    public static final int DEFAULT_MAX_DEPTH = 6;

    /**
     * Default Stockfish executable name used when no path is supplied.
     */
    public static final String DEFAULT_STOCKFISH = "stockfish";

    /**
     * Critical perft cases used by the validation suite.
     */
    private static final Case[] CASES = {
            new Case(
                    "Initial position",
                    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
            new Case(
                    "Position 2 Kiwipete",
                    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"),
            new Case(
                    "Position 3",
                    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"),
            new Case(
                    "Position 4",
                    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"),
            new Case(
                    "Position 4 mirrored",
                    "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1"),
            new Case(
                    "Position 5",
                    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"),
            new Case(
                    "Position 6",
                    "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"),
            new Case(
                    "Open castling lanes",
                    "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"),
            new Case(
                    "En-passant pin",
                    "8/2p5/8/KP1p2kr/5p2/8/4P1P1/6R1 w - d6 0 1"),
            new Case(
                    "Promotion race",
                    "4k3/P6P/8/8/8/8/p6p/4K3 w - - 0 1"),
            new Case(
                    "Chess960 start 0",
                    "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1",
                    true),
            new Case(
                    "Chess960 start 959",
                    "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1",
                    true),
            new Case(
                    "Chess960 midgame",
                    "bb3rkr/pq1p2pp/1p2pn2/2p2p2/2P2PnP/1P2PN2/PQBP1NP1/B4RKR w HFhf - 9 10",
                    true),
            new Case(
                    "Chess960 open castles",
                    "4k3/8/8/8/8/8/8/RK5R w HA - 0 1",
                    true),
            new Case(
                    "Knight swarm",
                    "krN1N1N1/pp1N1N1N/N1N1N1N1/1N1N1N1N/N1N1N1N1/1N1N1N1N/N1N1N1N1/1N1N1N1K w - - 0 1")
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
     * Runs the comparison suite and prints an aligned result table.
     *
     * @param maxDepth validation depth to include
     * @param stockfishCommand Stockfish executable path or command
     * @param progress optional progress callback after each row
     * @return immutable comparison summary
     * @throws IOException when Stockfish cannot be started or queried
     * @throws InterruptedException when interrupted while waiting for Stockfish
     */
    public static Summary printStockfishComparison(
            int maxDepth,
            String stockfishCommand,
            Runnable progress) throws IOException, InterruptedException {
        Summary summary = compareWithStockfish(maxDepth, stockfishCommand, progress);
        print(summary);
        return summary;
    }

    /**
     * Runs the comparison suite without printing the result table.
     *
     * @param maxDepth validation depth to include
     * @param stockfishCommand Stockfish executable path or command
     * @param progress optional progress callback after each row
     * @return immutable comparison summary
     * @throws IOException when Stockfish cannot be started or queried
     * @throws InterruptedException when interrupted while waiting for Stockfish
     */
    public static Summary compareWithStockfish(
            int maxDepth,
            String stockfishCommand,
            Runnable progress) throws IOException, InterruptedException {
        return compareWithStockfish(maxDepth, stockfishCommand, 1, progress);
    }

    /**
     * Runs the comparison suite without printing the result table.
     *
     * @param maxDepth validation depth to include
     * @param stockfishCommand Stockfish executable path or command
     * @param threads worker thread count
     * @param progress optional progress callback after each row
     * @return immutable comparison summary
     * @throws IOException when Stockfish cannot be started or queried
     * @throws InterruptedException when interrupted while waiting for workers
     */
    public static Summary compareWithStockfish(
            int maxDepth,
            String stockfishCommand,
            int threads,
            Runnable progress) throws IOException, InterruptedException {
        requireDepth(maxDepth);
        requireThreads(threads);
        String command = stockfishCommand == null || stockfishCommand.isBlank()
                ? DEFAULT_STOCKFISH
                : stockfishCommand.trim();
        if (threads == 1) {
            return compareSequentially(maxDepth, command, progress);
        }
        return compareConcurrently(maxDepth, command, threads, progress);
    }

    /**
     * Runs all validation rows on the caller thread.
     *
     * @param maxDepth validation depth
     * @param command Stockfish command
     * @param progress optional progress callback
     * @return immutable comparison summary
     * @throws IOException when Stockfish cannot be started or queried
     */
    private static Summary compareSequentially(
            int maxDepth,
            String command,
            Runnable progress) throws IOException {
        List<Row> rows = new ArrayList<>();
        long started = System.nanoTime();
        try (StockfishClient stockfish = new StockfishClient(command)) {
            for (int caseIndex = 0; caseIndex < CASES.length; caseIndex++) {
                Case testCase = CASES[caseIndex];
                stockfish.setChess960(testCase.chess960());
                long calculatedStart = System.nanoTime();
                long calculated = MoveGenerator.perft(Position.fromFen(testCase.fen), maxDepth);
                long calculatedNanos = System.nanoTime() - calculatedStart;
                long truth = stockfish.perft(testCase.fen, maxDepth);
                rows.add(new Row(caseIndex + 1, testCase.name, maxDepth, testCase.fen,
                        truth, calculated, calculatedNanos));
                if (progress != null) {
                    progress.run();
                }
            }
        }
        return new Summary(maxDepth, command, Collections.unmodifiableList(rows), System.nanoTime() - started);
    }

    /**
     * Runs validation rows on a fixed-size worker pool.
     *
     * @param maxDepth validation depth
     * @param command Stockfish command
     * @param threads requested worker count
     * @param progress optional progress callback
     * @return immutable comparison summary
     * @throws IOException when Stockfish cannot be started or queried
     * @throws InterruptedException when interrupted while waiting for workers
     */
    private static Summary compareConcurrently(
            int maxDepth,
            String command,
            int threads,
            Runnable progress) throws IOException, InterruptedException {
        int workerCount = Math.min(threads, CASES.length);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CompletionService<Row> completion = new ExecutorCompletionService<>(executor);
        long started = System.nanoTime();
        try {
            for (int caseIndex = 0; caseIndex < CASES.length; caseIndex++) {
                final int index = caseIndex;
                completion.submit(compareTask(maxDepth, command, index));
            }
            Row[] rows = new Row[CASES.length];
            for (int completed = 0; completed < CASES.length; completed++) {
                Row row = completion.take().get();
                rows[row.caseNumber() - 1] = row;
                if (progress != null) {
                    progress.run();
                }
            }
            return new Summary(maxDepth, command, orderedRows(rows), System.nanoTime() - started);
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
     * @param command Stockfish command
     * @param caseIndex zero-based case index
     * @return row-producing task
     */
    private static Callable<Row> compareTask(int maxDepth, String command, int caseIndex) {
        return () -> compareCase(maxDepth, command, caseIndex);
    }

    /**
     * Compares one validation case.
     *
     * @param maxDepth validation depth
     * @param command Stockfish command
     * @param caseIndex zero-based case index
     * @return comparison row
     * @throws IOException when Stockfish cannot be started or queried
     */
    private static Row compareCase(int maxDepth, String command, int caseIndex) throws IOException {
        Case testCase = CASES[caseIndex];
        try (StockfishClient stockfish = new StockfishClient(command)) {
            stockfish.setChess960(testCase.chess960());
            long calculatedStart = System.nanoTime();
            long calculated = MoveGenerator.perft(Position.fromFen(testCase.fen), maxDepth);
            long calculatedNanos = System.nanoTime() - calculatedStart;
            long truth = stockfish.perft(testCase.fen, maxDepth);
            return new Row(caseIndex + 1, testCase.name, maxDepth, testCase.fen,
                    truth, calculated, calculatedNanos);
        }
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
     * Converts worker failures to the method's declared exception surface.
     *
     * @param ex execution failure
     * @return IOException to throw
     */
    private static IOException unwrapExecutionException(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof IOException io) {
            return io;
        }
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IOException(cause);
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
     * One validation position.
     *
     * @param name display name
     * @param fen FEN
     * @param chess960 true when the engine must use Chess960 castling rules
     */
    private record Case(String name, String fen, boolean chess960) {

        /**
         * Creates a standard-chess validation position.
         *
         * @param name display name
         * @param fen FEN
         */
        private Case(String name, String fen) {
            this(name, fen, false);
        }
    }

    /**
     * One comparison table row.
     *
     * @param caseNumber one-based case index
     * @param name case name
     * @param depth perft depth
     * @param fen case FEN
     * @param truth independently calculated reference node count
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
         * Returns whether all compared node counts agree.
         *
         * @return true when truth and calculated counts are equal
         */
        public boolean matches() {
            return truth == calculated;
        }
    }

    /**
     * Full comparison result.
     *
     * @param maxDepth requested maximum depth
     * @param stockfishCommand Stockfish command used
     * @param rows comparison rows
     * @param nanos elapsed nanoseconds
     */
    public record Summary(
            int maxDepth,
            String stockfishCommand,
            List<Row> rows,
            long nanos) {

        /**
         * Returns whether every comparison row matched.
         *
         * @return true when every row matched
         */
        public boolean matches() {
            return rows.stream().allMatch(Row::matches);
        }
    }

    /**
     * Minimal Stockfish UCI client for perft commands.
     */
    private static final class StockfishClient implements AutoCloseable {

        /**
         * Running Stockfish process.
         */
        private final Process process;

        /**
         * Reader for merged Stockfish stdout/stderr.
         */
        private final BufferedReader reader;

        /**
         * Writer for Stockfish stdin.
         */
        private final BufferedWriter writer;

        /**
         * Current Chess960 option state sent to the engine.
         */
        private boolean chess960;

        /**
         * Starts Stockfish and waits for UCI readiness.
         *
         * @param command executable command
         * @throws IOException when the process cannot be started
         */
        private StockfishClient(String command) throws IOException {
            this.process = new ProcessBuilder(commandParts(command))
                    .redirectErrorStream(true)
                    .start();
            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            send("uci");
            readUntil("uciok");
            send("isready");
            readUntil("readyok");
        }

        /**
         * Sets the engine's Chess960 mode when the next case needs it.
         *
         * @param value true for Chess960 positions
         * @throws IOException when the option cannot be written
         */
        private void setChess960(boolean value) throws IOException {
            if (chess960 == value) {
                return;
            }
            send("setoption name UCI_Chess960 value " + value);
            send("isready");
            readUntil("readyok");
            chess960 = value;
        }

        /**
         * Runs Stockfish {@code go perft <depth>} for one FEN.
         *
         * @param fen position FEN
         * @param depth perft depth
         * @return Stockfish node count
         * @throws IOException when Stockfish output cannot be read
         */
        private long perft(String fen, int depth) throws IOException {
            send("position fen " + fen);
            send("go perft " + depth);
            String prefix = "Nodes searched:";
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(prefix)) {
                    return Long.parseLong(line.substring(prefix.length()).trim());
                }
            }
            throw new IOException("Stockfish exited before reporting perft depth " + depth);
        }

        /**
         * Sends one command line to Stockfish.
         *
         * @param command command text without newline
         * @throws IOException when the command cannot be written
         */
        private void send(String command) throws IOException {
            writer.write(command);
            writer.newLine();
            writer.flush();
        }

        /**
         * Reads output until the expected line appears.
         *
         * @param expected expected line
         * @throws IOException when Stockfish exits first
         */
        private void readUntil(String expected) throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                if (expected.equals(line)) {
                    return;
                }
            }
            throw new IOException("Stockfish exited before " + expected);
        }

        /**
         * Splits a simple executable command into process-builder arguments.
         *
         * @param command command string
         * @return command parts
         */
        private static List<String> commandParts(String command) {
            return List.of(command.trim().split("\\s+"));
        }

        /**
         * Requests Stockfish shutdown and closes process streams.
         */
        @Override
        public void close() throws IOException {
            try {
                send("quit");
            } finally {
                reader.close();
                writer.close();
                process.destroy();
            }
        }
    }
}
