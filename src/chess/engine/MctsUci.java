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
 * Minimal UCI loop for the built-in MCTS engine.
 *
 * <p>
 * This intentionally implements only the essential UCI surface needed by GUI
 * frontends and smoke tests: identify, readiness, new game, position, go, stop,
 * and quit.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MctsUci {

    /**
     * Engine display name.
     */
    private static final String ENGINE_NAME = "ChessRTK MCTS";

    /**
     * Default UCI hash setting in MiB.
     */
    private static final int DEFAULT_HASH_MB = 16;

    /**
     * Maximum advertised worker count.
     */
    private static final int MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors());

    /**
     * Shared searcher.
     */
    private final Mcts searcher;

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
     * Core-position keys before the current root, excluding the root itself.
     */
    private List<Long> positionHistory = List.of();

    /**
     * Active search thread, if any.
     */
    private Thread searchThread;

    /**
     * Whether the loop should terminate.
     */
    private boolean quit;

    /**
     * Creates a loop wrapper.
     *
     * @param searcher MCTS searcher
     * @param out output stream
     */
    private MctsUci(Mcts searcher, PrintStream out) {
        if (searcher == null) {
            throw new IllegalArgumentException("searcher == null");
        }
        if (out == null) {
            throw new IllegalArgumentException("out == null");
        }
        this.searcher = searcher;
        this.out = out;
        this.searcher.setHashMegabytes(DEFAULT_HASH_MB);
    }

    /**
     * Runs the UCI loop on byte streams.
     *
     * @param input input stream
     * @param output output stream
     * @param searcher MCTS searcher
     * @throws IOException if input fails
     */
    public static void run(InputStream input, PrintStream output, Mcts searcher) throws IOException {
        run(new InputStreamReader(input, StandardCharsets.UTF_8), output, searcher);
    }

    /**
     * Runs the UCI loop on a reader.
     *
     * @param input input reader
     * @param output output stream
     * @param searcher MCTS searcher
     * @throws IOException if input fails
     */
    public static void run(Reader input, PrintStream output, Mcts searcher) throws IOException {
        MctsUci loop = new MctsUci(searcher, output);
        loop.runLoop(new BufferedReader(input));
    }

    /**
     * Main read/evaluate loop.
     * @param reader reader value
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private void runLoop(BufferedReader reader) throws IOException {
        String line;
        while (!quit && (line = reader.readLine()) != null) {
            handle(line.trim());
        }
        joinSearch(false);
    }

    /**
     * Dispatches one UCI command.
     * @param line line text
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
            writeLine("readyok");
        } else if ("ucinewgame".equals(line)) {
            stopAndJoin();
            searcher.reset();
            position = new Position(Setup.getStandardStartFEN());
            positionHistory = List.of();
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
     * Handles the minimal Hash option.
     * @param line line text
     */
    private void handleSetOption(String line) {
        List<String> tokens = tokens(line);
        int valueIndex = indexOf(tokens, "value");
        if (line.toLowerCase(Locale.ROOT).contains("name hash") && valueIndex >= 0 && valueIndex + 1 < tokens.size()) {
            try {
                searcher.setHashMegabytes(Integer.parseInt(tokens.get(valueIndex + 1)));
            } catch (NumberFormatException ignored) {
                writeLine("info string invalid Hash value");
            }
        } else if (line.toLowerCase(Locale.ROOT).contains("name threads")
                && valueIndex >= 0 && valueIndex + 1 < tokens.size()) {
            try {
                searcher.setThreads(Math.min(MAX_THREADS, Integer.parseInt(tokens.get(valueIndex + 1))));
            } catch (NumberFormatException ignored) {
                writeLine("info string invalid Threads value");
            }
        }
    }

    /**
     * Handles UCI position setup.
     * @param line line text
     */
    private void handlePosition(String line) {
        try {
            List<String> tokens = tokens(line);
            int movesIndex = indexOf(tokens, "moves");
            Position next;
            List<Long> history = new ArrayList<>();
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
                playMoves(next, tokens.subList(movesIndex + 1, tokens.size()), history);
            }
            position = next;
            positionHistory = List.copyOf(history);
        } catch (RuntimeException ex) {
            writeLine("info string invalid position: " + ex.getMessage());
        }
    }

    /**
     * Applies UCI moves to a position.
     * @param target target value
     * @param moves candidate moves
     * @param history history value
     */
    private static void playMoves(Position target, List<String> moves, List<Long> history) {
        for (String token : moves) {
            if (history != null) {
                history.add(target.signatureCore());
            }
            short move = Move.parse(token);
            if (!target.isLegalMove(move)) {
                throw new IllegalArgumentException("illegal move " + token);
            }
            target.play(move);
        }
    }

    /**
     * Starts a search.
     * @param line line text
     */
    private void handleGo(String line) {
        stopAndJoin();
        GoLimits go = parseGo(line, position.isWhiteToMove());
        Position root = position.copy();
        long[] history = historyArray(positionHistory);
        searchThread = new Thread(() -> {
            Result result = searcher.searchReusable(root, go.toLimits(), this::printInfo, history);
            writeLine("bestmove " + (result.hasBestMove() ? Move.toString(result.bestMove()) : "0000"));
        }, "crtk-mcts-uci-search");
        searchThread.setDaemon(true);
        searchThread.start();
        while (searchThread.isAlive() && !searcher.isSearching()) {
            Thread.yield();
        }
    }

    /**
     * Copies history keys to a primitive array.
     * @param history history value
     * @return history array result
     */
    private static long[] historyArray(List<Long> history) {
        if (history == null || history.isEmpty()) {
            return new long[0];
        }
        long[] out = new long[history.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = history.get(i);
        }
        return out;
    }

    /**
     * Stops the current search and waits for its bestmove.
     */
    private void stopAndJoin() {
        joinSearch(true);
    }

    /**
     * Waits for the current search, optionally requesting a stop first.
     * @param requestStop request stop value
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
            searcher.requestStop();
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
     * @param line line text
     * @param whiteToMove white to move value
     * @return parse go result
     */
    private static GoLimits parseGo(String line, boolean whiteToMove) {
        List<String> tokens = tokens(line);
        // Without an explicit depth token the search must not be depth-capped:
        // the searcher stops at the depth limit, so the default is the maximum
        // (DEFAULT_DEPTH would silently cap clock/node searches at depth 3).
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
                        TimeControl timeControl = parseTimeControl(tokens, whiteToMove);
                        millis = timeControl.moveMillis();
                        i = tokens.size();
                    }
                    default -> {
                        // Ignore unneeded UCI go parameters.
                    }
                }
            }
        }
        if (infinite) {
            return new GoLimits(depth, Long.MAX_VALUE, 0L);
        }
        // With neither nodes nor a time budget, the searcher synthesizes its
        // own visit ceiling and the depth limit stops the search at the
        // requested depth (see Mcts.effectiveMaxNodes and Mcts.shouldContinue).
        return new GoLimits(depth, nodes, millis);
    }

    /**
     * Parses UCI clock fields into a simple move time.
     * @param tokens token values
     * @param whiteToMove white to move value
     * @return parse time control result
     */
    private static TimeControl parseTimeControl(List<String> tokens, boolean whiteToMove) {
        long wtime = 0L;
        long btime = 0L;
        long winc = 0L;
        long binc = 0L;
        long movestogo = 30L;
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
        long divisor = Math.max(1L, movestogo);
        long budget = time > 0L ? Math.max(1L, time / divisor + inc / 2L) : 0L;
        if (time > 0L) {
            long reserveAdjustedTime = time > 50L ? time - 50L : 1L;
            budget = Math.min(budget, reserveAdjustedTime);
        }
        return new TimeControl(budget);
    }

    /**
     * Emits one UCI info line.
     * @param result result value
     */
    private void printInfo(Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("info depth ").append(result.depth())
                .append(" score ");
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
     * @param result result value
     * @return nodes per second result
     */
    private static long nodesPerSecond(Result result) {
        return result.nodes() * 1_000L / Math.max(1L, result.elapsedMillis());
    }

    /**
     * Writes one output line without interleaving search output.
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
     * @param line line text
     * @return tokens result
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
     * @param tokens token values
     * @param value value to use
     * @return index of result
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
     * @param value value to use
     * @param fallback fallback value
     * @return parse positive int result
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
     * @param value value to use
     * @param fallback fallback value
     * @return parse positive long result
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
     */
    private record GoLimits(int depth, long nodes, long millis) {
        /**
         * Converts UCI go limits to MCTS limits.
         *
         * @return search limits
         */
        private Limits toLimits() {
            return new Limits(Math.min(depth, AlphaBeta.MAX_DEPTH), nodes, millis);
        }
    }

    /**
     * Parsed UCI clock budget.
     */
    private record TimeControl(long moveMillis) {
    }
}
