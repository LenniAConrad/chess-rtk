package chess.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import chess.core.Move;
import chess.core.Position;
import chess.core.Setup;

/**
 * Minimal UCI loop for the built-in alpha-beta engine.
 *
 * <p>
 * This mirrors {@link MctsUci} but drives an {@link AlphaBeta} searcher, so the
 * classical hand-crafted engine can be plugged into a UCI frontend or pitted
 * against another build through the self-play gauntlet's external-engine
 * support. It implements only the essential UCI surface: identify, readiness,
 * new game, position, go (movetime/nodes/depth/clock), stop, and quit.
 * </p>
 *
 * <p>
 * Alpha-beta detects repetitions inside its own search path, so the pre-root
 * game history that {@link MctsUci} threads through is not needed here; the
 * replayed {@code position} already carries the board state and halfmove clock
 * the search needs.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class AlphaBetaUci {

    /**
     * Engine display name.
     */
    private static final String ENGINE_NAME = "ChessRTK AlphaBeta";

    /**
     * Maximum advertised worker count.
     */
    private static final int MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors());

    /**
     * Shared searcher.
     */
    private final AlphaBeta searcher;

    /**
     * Output stream.
     */
    private final PrintStream out;

    /**
     * Output/search coordination lock.
     */
    private final Object lock = new Object();

    /**
     * Current root position.
     */
    private Position position = new Position(Setup.getStandardStartFEN());

    /**
     * Active search thread, if any.
     */
    private Thread searchThread;

    /**
     * Set when a stop has been requested for the active search; the per-depth
     * listener interrupts the search thread so iterative deepening returns its
     * best completed line.
     */
    private volatile boolean stopRequested;

    /**
     * Whether the loop should terminate.
     */
    private boolean quit;

    /**
     * Whether the one-time warmup search has run.
     */
    private boolean warmedUp;

    /**
     * Creates a loop wrapper.
     *
     * @param searcher alpha-beta searcher
     * @param out output stream
     */
    private AlphaBetaUci(AlphaBeta searcher, PrintStream out) {
        if (searcher == null) {
            throw new IllegalArgumentException("searcher == null");
        }
        if (out == null) {
            throw new IllegalArgumentException("out == null");
        }
        this.searcher = searcher;
        this.out = out;
    }

    /**
     * Runs the UCI loop on byte streams.
     *
     * @param input input stream
     * @param output output stream
     * @param searcher alpha-beta searcher
     * @throws IOException if input fails
     */
    public static void run(InputStream input, PrintStream output, AlphaBeta searcher) throws IOException {
        run(new InputStreamReader(input, StandardCharsets.UTF_8), output, searcher);
    }

    /**
     * Runs the UCI loop on a reader.
     *
     * @param input input reader
     * @param output output stream
     * @param searcher alpha-beta searcher
     * @throws IOException if input fails
     */
    public static void run(Reader input, PrintStream output, AlphaBeta searcher) throws IOException {
        AlphaBetaUci loop = new AlphaBetaUci(searcher, output);
        loop.runLoop(new BufferedReader(input));
    }

    /**
     * Main read/evaluate loop.
     *
     * @param reader command reader
     * @throws IOException if input fails
     */
    private void runLoop(BufferedReader reader) throws IOException {
        String line;
        while (!quit && (line = reader.readLine()) != null) {
            handle(line.trim());
        }
        joinSearch(true);
    }

    /**
     * Dispatches one UCI command.
     *
     * @param line command line
     */
    private void handle(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if ("uci".equals(line)) {
            writeLine("id name " + ENGINE_NAME);
            writeLine("id author Lennart A. Conrad");
            writeLine("option name Hash type spin default 16 min 1 max 1024");
            writeLine("option name Threads type spin default 1 min 1 max " + MAX_THREADS);
            writeLine("uciok");
        } else if ("isready".equals(line)) {
            warmUp();
            writeLine("readyok");
        } else if ("ucinewgame".equals(line)) {
            stopAndJoin();
            position = new Position(Setup.getStandardStartFEN());
        } else if (line.startsWith("setoption ")) {
            handleSetOption(line);
        } else if (line.startsWith("position ")) {
            handlePosition(line);
        } else if (line.startsWith("go")) {
            handleGo(line);
        } else if ("stop".equals(line)) {
            stopAndJoin();
        } else if ("quit".equals(line)) {
            quit = true;
            stopAndJoin();
        }
    }

    /**
     * Runs a short bounded search once, before the first {@code readyok}, so
     * the JIT has compiled the hot search and evaluation paths before the
     * game clock starts. Without this a freshly spawned engine plays its
     * first moves interpreted, which both wastes clock time and skews
     * external-engine measurements between builds of different code size.
     */
    private void warmUp() {
        if (warmedUp) {
            return;
        }
        warmedUp = true;
        searcher.search(new Position(Setup.getStandardStartFEN()),
                new Limits(AlphaBeta.MAX_DEPTH, 30_000L, 1_500L));
    }

    /**
     * Handles the minimal Threads option (Hash is advertised but the alpha-beta
     * table is internally sized, so the value is accepted and ignored).
     *
     * @param line command line
     */
    private void handleSetOption(String line) {
        List<String> tokens = tokens(line);
        int valueIndex = indexOf(tokens, "value");
        if (line.toLowerCase(Locale.ROOT).contains("name threads")
                && valueIndex >= 0 && valueIndex + 1 < tokens.size()) {
            try {
                searcher.setSearchThreads(Math.min(MAX_THREADS, Integer.parseInt(tokens.get(valueIndex + 1))));
            } catch (NumberFormatException ignored) {
                writeLine("info string invalid Threads value");
            }
        }
    }

    /**
     * Handles UCI position setup.
     *
     * @param line command line
     */
    private void handlePosition(String line) {
        try {
            List<String> tokens = tokens(line);
            int movesIndex = indexOf(tokens, "moves");
            Position next;
            if (tokens.size() >= 2 && "startpos".equals(tokens.get(1))) {
                next = new Position(Setup.getStandardStartFEN());
            } else if (tokens.size() >= 3 && "fen".equals(tokens.get(1))) {
                int end = movesIndex < 0 ? tokens.size() : movesIndex;
                next = new Position(String.join(" ", tokens.subList(2, end)));
            } else {
                writeLine("info string unsupported position command");
                return;
            }
            if (movesIndex >= 0) {
                playMoves(next, tokens.subList(movesIndex + 1, tokens.size()));
            }
            position = next;
        } catch (RuntimeException ex) {
            writeLine("info string invalid position: " + ex.getMessage());
        }
    }

    /**
     * Applies UCI moves to a position.
     *
     * @param target position to advance
     * @param moves coordinate moves
     */
    private static void playMoves(Position target, List<String> moves) {
        for (String token : moves) {
            short move = Move.parse(token);
            if (!target.isLegalMove(move)) {
                throw new IllegalArgumentException("illegal move " + token);
            }
            target.play(move);
        }
    }

    /**
     * Starts a search.
     *
     * @param line command line
     */
    private void handleGo(String line) {
        stopAndJoin();
        GoLimits go = parseGo(line, position.isWhiteToMove());
        Position root = position.copy();
        stopRequested = false;
        searchThread = new Thread(() -> {
            Result result = searcher.search(root, go.toLimits(), this::onDepth);
            writeLine("bestmove " + (result.hasBestMove() ? Move.toString(result.bestMove()) : "0000"));
        }, "crtk-ab-uci-search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    /**
     * Per-depth listener: emits an info line and aborts the search at the next
     * depth boundary when a stop has been requested.
     *
     * @param result completed-depth result
     */
    private void onDepth(Result result) {
        printInfo(result);
        if (stopRequested) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stops the current search and waits for its bestmove.
     */
    private void stopAndJoin() {
        joinSearch(true);
    }

    /**
     * Waits for the current search, optionally requesting a stop first.
     *
     * @param requestStop true to ask the search to abort
     */
    private void joinSearch(boolean requestStop) {
        Thread thread;
        synchronized (lock) {
            thread = searchThread;
        }
        if (thread == null) {
            return;
        }
        if (requestStop) {
            stopRequested = true;
        }
        try {
            thread.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (lock) {
                if (searchThread == thread) {
                    searchThread = null;
                }
            }
        }
    }

    /**
     * Parses a UCI go command.
     *
     * @param line command line
     * @param whiteToMove whether white is to move
     * @return parsed limits
     */
    private static GoLimits parseGo(String line, boolean whiteToMove) {
        List<String> tokens = tokens(line);
        // Without an explicit depth token the search must not be depth-capped:
        // DEFAULT_DEPTH here silently limited clock and node games to depth 3.
        int depth = AlphaBeta.MAX_DEPTH;
        long nodes = 0L;
        long millis = 0L;
        boolean infinite = false;
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("infinite".equals(token)) {
                infinite = true;
            } else if (i + 1 < tokens.size()) {
                String value = tokens.get(++i);
                switch (token) {
                    case "depth" -> depth = parsePositiveInt(value, depth);
                    case "nodes" -> nodes = parsePositiveLong(value, nodes);
                    case "movetime" -> millis = parsePositiveLong(value, millis);
                    case "wtime", "btime", "winc", "binc", "movestogo" -> {
                        millis = parseTimeControl(tokens, whiteToMove);
                        i = tokens.size();
                    }
                    default -> {
                        // Ignore unneeded UCI go parameters.
                    }
                }
            }
        }
        if (infinite) {
            return new GoLimits(AlphaBeta.MAX_DEPTH, 0L, 0L, true);
        }
        if (nodes <= 0L && millis <= 0L) {
            nodes = Math.max(256L, depth * 1024L);
        }
        return new GoLimits(depth, nodes, millis, false);
    }

    /**
     * Parses UCI clock fields into a simple per-move time budget through the
     * shared {@link Limits#clockBudgetMillis} time-management mapping.
     *
     * @param tokens command tokens
     * @param whiteToMove whether white is to move
     * @return per-move budget in milliseconds
     */
    private static long parseTimeControl(List<String> tokens, boolean whiteToMove) {
        long wtime = 0L;
        long btime = 0L;
        long winc = 0L;
        long binc = 0L;
        long movestogo = Limits.CLOCK_MOVES_TO_GO;
        for (int i = 1; i + 1 < tokens.size(); i++) {
            String key = tokens.get(i);
            String value = tokens.get(i + 1);
            switch (key) {
                case "wtime" -> wtime = parsePositiveLong(value, wtime);
                case "btime" -> btime = parsePositiveLong(value, btime);
                case "winc" -> winc = parsePositiveLong(value, winc);
                case "binc" -> binc = parsePositiveLong(value, binc);
                case "movestogo" -> movestogo = parsePositiveLong(value, movestogo);
                default -> {
                    continue;
                }
            }
            i++;
        }
        long time = whiteToMove ? wtime : btime;
        long inc = whiteToMove ? winc : binc;
        return Limits.clockBudgetMillis(time, inc, movestogo);
    }

    /**
     * Emits one UCI info line.
     *
     * @param result completed-depth result
     */
    private void printInfo(Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("info depth ").append(result.depth()).append(" score ");
        if (result.isMateScore()) {
            sb.append("mate ").append(result.mateIn());
        } else {
            sb.append("cp ").append(result.scoreCentipawns());
        }
        sb.append(" nodes ").append(result.nodes())
                .append(" nps ").append(nodesPerSecond(result))
                .append(" time ").append(result.elapsedMillis());
        short[] pv = result.principalVariation();
        if (pv.length > 0) {
            sb.append(" pv");
            for (short move : pv) {
                sb.append(' ').append(Move.toString(move));
            }
        }
        writeLine(sb.toString());
    }

    /**
     * Computes nodes per second for UCI output.
     *
     * @param result completed-depth result
     * @return nodes per second
     */
    private static long nodesPerSecond(Result result) {
        return result.nodes() * 1_000L / Math.max(1L, result.elapsedMillis());
    }

    /**
     * Writes one output line without interleaving search output.
     *
     * @param line line text
     */
    private void writeLine(String line) {
        synchronized (lock) {
            out.println(line);
            out.flush();
        }
    }

    /**
     * Splits a command into whitespace tokens.
     *
     * @param line command line
     * @return tokens
     */
    private static List<String> tokens(String line) {
        String[] raw = line.trim().split("\\s+");
        List<String> out = new ArrayList<>(raw.length);
        for (String token : raw) {
            if (!token.isBlank()) {
                out.add(token);
            }
        }
        return out;
    }

    /**
     * Finds a token.
     *
     * @param tokens token values
     * @param value value to find
     * @return index, or -1
     */
    private static int indexOf(List<String> tokens, String value) {
        for (int i = 0; i < tokens.size(); i++) {
            if (value.equals(tokens.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Parses a positive int.
     *
     * @param value text value
     * @param fallback fallback value
     * @return parsed positive int, or the fallback
     */
    private static int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Parses a positive long.
     *
     * @param value text value
     * @param fallback fallback value
     * @return parsed positive long, or the fallback
     */
    private static long parsePositiveLong(String value, long fallback) {
        try {
            return Math.max(1L, Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Parsed go limits.
     *
     * @param depth depth bound
     * @param nodes node bound, or zero
     * @param millis time bound in milliseconds, or zero
     * @param infinite whether the search is unbounded
     */
    private record GoLimits(int depth, long nodes, long millis, boolean infinite) {

        /**
         * Converts UCI go limits to search limits.
         *
         * @return search limits
         */
        private Limits toLimits() {
            int boundedDepth = Math.min(depth, AlphaBeta.MAX_DEPTH);
            if (infinite) {
                return new Limits(AlphaBeta.MAX_DEPTH, Long.MAX_VALUE, 0L);
            }
            return new Limits(boundedDepth, nodes, millis);
        }
    }
}
