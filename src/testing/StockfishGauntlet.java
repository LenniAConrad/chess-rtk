package testing;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Result;
import chess.eval.Classical;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Head-to-head gauntlet between CRTK's built-in alpha-beta engine and a
 * Stockfish-compatible UCI binary.
 *
 * <p>
 * The harness plays a seeded set of generated openings from both colors. CRTK
 * uses a fixed node or time budget per move through the in-process
 * {@link AlphaBeta} searcher; Stockfish is driven by UCI {@code go nodes N} or
 * {@code go movetime N}. Results are reported from CRTK's perspective.
 * </p>
 */
public final class StockfishGauntlet {

    /**
     * Standard start position.
     */
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Default path used by Debian/Ubuntu Stockfish packages.
     */
    private static final String DEFAULT_STOCKFISH = "/usr/games/stockfish";

    /**
     * Default startup/readiness timeout for UCI commands.
     */
    private static final long UCI_TIMEOUT_MS = 30_000L;

    /**
     * Outcome from CRTK's perspective.
     */
    private enum Outcome {
        /**
         * CRTK won.
         */
        WIN,

        /**
         * Game was drawn.
         */
        DRAW,

        /**
         * CRTK lost.
         */
        LOSS
    }

    /**
     * Two games from one opening.
     *
     * @param first CRTK as White
     * @param second CRTK as Black
     */
    private record OpeningResult(Outcome first, Outcome second) {
    }

    /**
     * Aggregate score from CRTK's perspective.
     *
     * @param win wins
     * @param draw draws
     * @param loss losses
     */
    private record Score(int win, int draw, int loss) {
    }

    /**
     * Prevents instantiation.
     */
    private StockfishGauntlet() {
    }

    /**
     * Runs the gauntlet.
     *
     * @param args {@code --key value} options
     */
    public static void main(String[] args) {
        Map<String, String> opts = parseArgs(args);
        String stockfishPath = opts.getOrDefault("stockfish", DEFAULT_STOCKFISH);
        int games = Integer.parseInt(opts.getOrDefault("games", "100"));
        if (games <= 0 || games % 2 != 0) {
            throw new IllegalArgumentException("--games must be a positive even number");
        }
        long commonMillis = Long.parseLong(opts.getOrDefault("millis", "0"));
        int crtkNodes = Integer.parseInt(opts.getOrDefault("crtkNodes", "10000"));
        int stockfishNodes = Integer.parseInt(opts.getOrDefault("stockfishNodes", "1000"));
        long crtkMillis = Long.parseLong(opts.getOrDefault("crtkMillis", Long.toString(commonMillis)));
        long stockfishMillis = Long.parseLong(opts.getOrDefault("stockfishMillis", Long.toString(commonMillis)));
        int crtkThreads = Integer.parseInt(opts.getOrDefault("crtkThreads", "1"));
        int maxPlies = Integer.parseInt(opts.getOrDefault("maxplies", "240"));
        int workers = Integer.parseInt(opts.getOrDefault("workers", "1"));
        long seed = Long.parseLong(opts.getOrDefault("seed", "20260623"));
        if (crtkMillis < 0L || stockfishMillis < 0L || crtkThreads <= 0 || maxPlies <= 0 || workers <= 0) {
            throw new IllegalArgumentException(
                    "time budgets, --crtkThreads, --maxplies, and --workers must be non-negative/positive");
        }
        if ((crtkMillis == 0L && crtkNodes <= 0) || (stockfishMillis == 0L && stockfishNodes <= 0)) {
            throw new IllegalArgumentException("node budgets must be positive when time budget is disabled");
        }

        String[] openings = generateOpenings(games / 2, seed);
        System.out.println("StockfishGauntlet");
        System.out.println("  CRTK: AlphaBeta classical, " + budgetLabel(crtkNodes, crtkMillis)
                + ", threads=" + crtkThreads);
        System.out.println("  Stockfish: " + stockfishPath + ", " + budgetLabel(stockfishNodes, stockfishMillis));
        System.out.println("  seed=" + seed
                + "  workers=" + workers
                + "  openings=" + openings.length
                + "  games=" + (openings.length * 2)
                + "  maxplies=" + maxPlies);

        Score score = workers == 1
                ? runSequential(openings, stockfishPath, crtkNodes, stockfishNodes, crtkMillis, stockfishMillis,
                        crtkThreads, maxPlies)
                : runParallel(openings, stockfishPath, crtkNodes, stockfishNodes, crtkMillis, stockfishMillis,
                        crtkThreads, maxPlies, workers);
        report(score.win(), score.draw(), score.loss());
    }

    /**
     * Runs opening pairs sequentially.
     */
    private static Score runSequential(
            String[] openings,
            String stockfishPath,
            int crtkNodes,
            int stockfishNodes,
            long crtkMillis,
            long stockfishMillis,
            int crtkThreads,
            int maxPlies) {
        int[] score = { 0, 0, 0 };
        for (int i = 0; i < openings.length; i++) {
            add(score, playOpeningPair(openings[i], stockfishPath, crtkNodes, stockfishNodes, crtkMillis,
                    stockfishMillis, crtkThreads, maxPlies));
            printProgress(i + 1, openings.length, score);
        }
        return new Score(score[0], score[1], score[2]);
    }

    /**
     * Runs opening pairs concurrently and collects results in opening order.
     */
    private static Score runParallel(
            String[] openings,
            String stockfishPath,
            int crtkNodes,
            int stockfishNodes,
            long crtkMillis,
            long stockfishMillis,
            int crtkThreads,
            int maxPlies,
            int workers) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(workers, openings.length));
        try {
            List<Future<OpeningResult>> futures = new ArrayList<>(openings.length);
            for (String opening : openings) {
                futures.add(pool.submit(() -> playOpeningPair(
                        opening,
                        stockfishPath,
                        crtkNodes,
                        stockfishNodes,
                        crtkMillis,
                        stockfishMillis,
                        crtkThreads,
                        maxPlies)));
            }
            int[] score = { 0, 0, 0 };
            for (int i = 0; i < futures.size(); i++) {
                add(score, futures.get(i).get());
                printProgress(i + 1, openings.length, score);
            }
            return new Score(score[0], score[1], score[2]);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stockfish gauntlet interrupted", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Stockfish gauntlet worker failed", ex.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Plays both color assignments for one opening.
     */
    private static OpeningResult playOpeningPair(
            String opening,
            String stockfishPath,
            int crtkNodes,
            int stockfishNodes,
            long crtkMillis,
            long stockfishMillis,
            int crtkThreads,
            int maxPlies) {
        try (UciClient stockfish = new UciClient(stockfishPath)) {
            Outcome first = playGame(opening, true, stockfish, crtkNodes, stockfishNodes, crtkMillis,
                    stockfishMillis, crtkThreads, maxPlies);
            Outcome second = playGame(opening, false, stockfish, crtkNodes, stockfishNodes, crtkMillis,
                    stockfishMillis, crtkThreads, maxPlies);
            return new OpeningResult(first, second);
        } catch (IOException ex) {
            throw new IllegalStateException("Stockfish failed for opening " + opening, ex);
        }
    }

    /**
     * Plays one game from an opening.
     */
    private static Outcome playGame(
            String fen,
            boolean crtkIsWhite,
            UciClient stockfish,
            int crtkNodes,
            int stockfishNodes,
            long crtkMillis,
            long stockfishMillis,
            int crtkThreads,
            int maxPlies) throws IOException {
        Position position = new Position(fen);
        Limits crtkLimits = new Limits(AlphaBeta.MAX_DEPTH, crtkMillis > 0L ? 0L : crtkNodes, crtkMillis);
        Map<Long, Integer> seen = new HashMap<>();
        stockfish.newGame();
        try (AlphaBeta crtk = new AlphaBeta(new Classical(), true).setSearchThreads(crtkThreads)) {
            countPosition(seen, position);
            for (int ply = 0; ply < maxPlies; ply++) {
                MoveList legal = position.legalMoves();
                if (legal.isEmpty()) {
                    if (position.inCheck()) {
                        boolean whiteMated = position.isWhiteToMove();
                        return whiteMated == crtkIsWhite ? Outcome.LOSS : Outcome.WIN;
                    }
                    return Outcome.DRAW;
                }
                if (position.isInsufficientMaterial()
                        || position.halfMoveClock() >= 100
                        || seen.getOrDefault(position.signatureCore(), 0) >= 3) {
                    return Outcome.DRAW;
                }
                boolean crtkToMove = position.isWhiteToMove() == crtkIsWhite;
                short move = crtkToMove
                        ? crtk.search(position, crtkLimits).bestMove()
                        : stockfish.bestMove(position, stockfishNodes, stockfishMillis);
                if (move == Move.NO_MOVE || !isLegal(legal, move)) {
                    return crtkToMove ? Outcome.LOSS : Outcome.WIN;
                }
                position.play(move);
                countPosition(seen, position);
            }
            return Outcome.DRAW;
        }
    }

    /**
     * Generates seeded, non-terminal opening positions.
     */
    private static String[] generateOpenings(int count, long seed) {
        Random rng = new Random(seed);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        int attempts = 0;
        int cap = count * 100 + 1000;
        while (set.size() < count && attempts < cap) {
            attempts++;
            Position position = new Position(START_FEN);
            int plies = 4 + rng.nextInt(5);
            boolean ok = true;
            for (int i = 0; i < plies; i++) {
                MoveList legal = position.legalMoves();
                if (legal.isEmpty()) {
                    ok = false;
                    break;
                }
                position.play(legal.raw(rng.nextInt(legal.size())));
            }
            if (!ok || position.legalMoves().isEmpty() || position.isInsufficientMaterial()) {
                continue;
            }
            set.add(position.toString());
        }
        if (set.size() < count) {
            throw new IllegalStateException("could only generate " + set.size() + " openings out of " + count);
        }
        return set.toArray(new String[0]);
    }

    /**
     * Records one position occurrence for threefold adjudication.
     */
    private static void countPosition(Map<Long, Integer> seen, Position position) {
        seen.merge(position.signatureCore(), 1, Integer::sum);
    }

    /**
     * Returns whether a move is present in the legal move list.
     */
    private static boolean isLegal(MoveList legal, short move) {
        for (int i = 0; i < legal.size(); i++) {
            if (Move.equals(legal.raw(i), move)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an opening result to an aggregate score.
     */
    private static void add(int[] score, OpeningResult result) {
        add(score, result.first());
        add(score, result.second());
    }

    /**
     * Adds one outcome to an aggregate score.
     */
    private static void add(int[] score, Outcome outcome) {
        switch (outcome) {
            case WIN -> score[0]++;
            case DRAW -> score[1]++;
            case LOSS -> score[2]++;
            default -> { }
        }
    }

    /**
     * Prints periodic progress.
     */
    private static void printProgress(int completed, int total, int[] score) {
        if (completed % 5 == 0 || completed == total) {
            System.out.printf("  [%3d/%3d]  running W-D-L = %d-%d-%d%n",
                    completed, total, score[0], score[1], score[2]);
        }
    }

    /**
     * Prints the match score and point Elo estimate.
     */
    private static void report(int win, int draw, int loss) {
        int games = win + draw + loss;
        double score = games == 0 ? 0.0 : (win + 0.5 * draw) / games;
        System.out.println("----");
        System.out.printf("Result (CRTK vs Stockfish): +%d =%d -%d  of %d games%n",
                win, draw, loss, games);
        System.out.printf("Score: %.1f%%%n", score * 100.0);
        if (score <= 0.0 || score >= 1.0) {
            System.out.println("Elo estimate: " + (score >= 1.0 ? "+inf (no losses)" : "-inf (no wins)"));
        } else {
            double elo = -400.0 * Math.log10(1.0 / score - 1.0);
            System.out.printf("Elo estimate: %+.0f%n", elo);
        }
    }

    /**
     * Formats the active search budget.
     */
    private static String budgetLabel(int nodes, long millis) {
        return millis > 0L ? millis + " ms/move" : nodes + " nodes/move";
    }

    /**
     * Parses {@code --key value} options.
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            String key = args[i];
            if (key.startsWith("--")) {
                opts.put(key.substring(2), args[i + 1]);
            }
        }
        return opts;
    }

    /**
     * Minimal UCI client for node-limited Stockfish best-move searches.
     */
    private static final class UciClient implements AutoCloseable {

        /**
         * Engine process.
         */
        private final Process process;

        /**
         * Engine stdout.
         */
        private final BufferedReader input;

        /**
         * Engine stdin.
         */
        private final PrintWriter output;

        /**
         * Starts and initializes a UCI engine.
         */
        private UciClient(String path) throws IOException {
            process = new ProcessBuilder(path).redirectErrorStream(true).start();
            input = new BufferedReader(new InputStreamReader(process.getInputStream()));
            output = new PrintWriter(process.getOutputStream(), true);
            send("uci");
            waitForExact("uciok", UCI_TIMEOUT_MS);
            send("setoption name Threads value 1");
            send("setoption name Hash value 16");
            ready();
        }

        /**
         * Starts a new game for hash/history hygiene.
         */
        private void newGame() throws IOException {
            send("ucinewgame");
            ready();
        }

        /**
         * Searches one position to a fixed node budget and returns the best move.
         */
        private short bestMove(Position position, int nodes, long millis) throws IOException {
            send("position fen " + position);
            send(millis > 0L ? "go movetime " + millis : "go nodes " + nodes);
            long deadline = System.currentTimeMillis() + UCI_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                String line = readLine(deadline);
                if (line == null) {
                    break;
                }
                if (line.startsWith("bestmove ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 2 || "(none)".equals(parts[1])) {
                        return Move.NO_MOVE;
                    }
                    return Move.parse(parts[1]);
                }
            }
            throw new IOException("Stockfish did not return bestmove within " + UCI_TIMEOUT_MS + "ms");
        }

        /**
         * Sends an isready command and waits for readyok.
         */
        private void ready() throws IOException {
            send("isready");
            waitForExact("readyok", UCI_TIMEOUT_MS);
        }

        /**
         * Sends one command.
         */
        private void send(String command) {
            output.println(command);
            output.flush();
        }

        /**
         * Waits for an exact response line.
         */
        private void waitForExact(String expected, long timeoutMs) throws IOException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                String line = readLine(deadline);
                if (expected.equals(line)) {
                    return;
                }
            }
            throw new IOException("Stockfish did not emit " + expected + " within " + timeoutMs + "ms");
        }

        /**
         * Reads one line before the deadline.
         */
        private String readLine(long deadline) throws IOException {
            while (System.currentTimeMillis() < deadline) {
                if (input.ready()) {
                    return input.readLine();
                }
                if (!process.isAlive()) {
                    throw new IOException("Stockfish process exited");
                }
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while waiting for Stockfish", ex);
                }
            }
            return null;
        }

        /**
         * Stops the process.
         */
        @Override
        public void close() {
            send("quit");
            process.destroy();
        }
    }
}
