package chess.engine;

import chess.classical.Wdl;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;
import chess.eval.Nnue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic self-play A/B gauntlet engine for the built-in searchers.
 *
 * <p>
 * Pits a candidate configuration against a baseline at an equal, fixed per-move
 * budget (node count by default, or wall-clock time) or on a per-game clock
 * with increment (see {@link TimeControl}) so a search or evaluation change's
 * strength effect is <em>measured</em> by game results rather than asserted.
 * Both engines run in one process against a set of varied opening positions,
 * each played from both colors, and the aggregate score is returned from the
 * candidate's perspective.
 * </p>
 *
 * <p>
 * This is the reusable runner shared by the {@code crtk engine gauntlet} CLI
 * command, the workbench Engine Lab, and the bundled standalone harness. It owns
 * no input/output: callers pass a {@link Config}, observe progress through a
 * {@link ProgressListener}, and render the returned {@link Score} themselves.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Gauntlet {

    /**
     * Search algorithm used by one side in a gauntlet game.
     */
    public enum SearchKind {

        /**
         * Iterative-deepening alpha-beta.
         */
        ALPHA_BETA,

        /**
         * PUCT Monte-Carlo tree search.
         */
        MCTS;

        /**
         * Parses a search-kind name accepting the common command-line spellings.
         *
         * @param name search name
         * @return parsed search kind
         * @throws IllegalArgumentException for an unknown name
         */
        public static SearchKind parse(String name) {
            String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "ab", "alpha", "alphabeta", "alpha-beta" -> ALPHA_BETA;
                case "mcts", "puct" -> MCTS;
                default -> throw new IllegalArgumentException("unknown search kind: " + name);
            };
        }

        /**
         * Returns the stable command-line name for this search kind.
         *
         * @return command-line name
         */
        public String cliName() {
            return switch (this) {
                case ALPHA_BETA -> "alpha-beta";
                case MCTS -> "mcts";
            };
        }
    }

    /**
     * Receives gauntlet progress and informational notes.
     *
     * <p>
     * {@link #onProgress} is invoked in opening order from a single thread.
     * {@link #onNote} may be invoked from worker threads and must be safe to
     * call concurrently.
     * </p>
     */
    public interface ProgressListener {

        /**
         * No-op listener.
         */
        ProgressListener NONE = new ProgressListener() {
        };

        /**
         * Reports completion of one opening pair.
         *
         * @param completed completed opening pairs
         * @param total total opening pairs
         * @param running running score from the candidate's perspective
         */
        default void onProgress(int completed, int total, Score running) {
            // optional
        }

        /**
         * Reports an informational note such as an evaluator fallback.
         *
         * @param message human-readable note
         */
        default void onNote(String message) {
            // optional
        }

        /**
         * Reports a completed game, with its full move list, so callers can
         * render or replay it. May be invoked from worker threads and must be
         * safe to call concurrently.
         *
         * @param record completed game record
         */
        default void onGame(GameRecord record) {
            // optional
        }

        /**
         * Reports the updated SPRT state after an opening pair has been folded
         * into the pentanomial statistics. Only invoked when SPRT stopping is
         * enabled, in opening order from the same thread as
         * {@link #onProgress}.
         *
         * @param completed completed opening pairs counted so far
         * @param total scheduled opening pairs (the hard cap)
         * @param snapshot immutable SPRT state snapshot
         */
        default void onSprt(int completed, int total, Sprt.Snapshot snapshot) {
            // optional
        }
    }

    /**
     * A single completed gauntlet game, captured so callers can review or
     * replay it.
     *
     * @param index zero-based game index in play order
     * @param candidateWhite whether the candidate played White this game
     * @param result game result from the candidate's perspective
     *               ({@code win}, {@code draw}, or {@code loss})
     * @param openingFen opening position FEN the game started from
     * @param moves moves played, in UCI notation, in order
     */
    public record GameRecord(int index, boolean candidateWhite, String result, String openingFen,
            List<String> moves) {

        /**
         * Normalizes the move list to an immutable copy.
         *
         * @param index zero-based game index
         * @param candidateWhite whether the candidate played White
         * @param result candidate-perspective result label
         * @param openingFen opening FEN
         * @param moves UCI moves in order
         */
        public GameRecord {
            moves = moves == null ? List.of() : List.copyOf(moves);
        }
    }

    /**
     * Aggregate match score from the candidate's perspective.
     *
     * @param win candidate wins
     * @param draw draws
     * @param loss candidate losses
     */
    public record Score(int win, int draw, int loss) {

        /**
         * Returns the total number of games played.
         *
         * @return game count
         */
        public int games() {
            return win + draw + loss;
        }

        /**
         * Returns the candidate's score fraction in {@code [0, 1]}.
         *
         * @return score fraction, or zero when no games were played
         */
        public double fraction() {
            int games = games();
            return games == 0 ? 0.0 : (win + 0.5 * draw) / games;
        }

        /**
         * Returns whether a finite Elo estimate exists (some wins and losses).
         *
         * @return true when a finite Elo estimate is defined
         */
        public boolean hasEloEstimate() {
            double fraction = fraction();
            return fraction > 0.0 && fraction < 1.0;
        }

        /**
         * Returns the point Elo estimate from the candidate's perspective.
         *
         * @return Elo estimate; only meaningful when {@link #hasEloEstimate()}
         */
        public double elo() {
            double fraction = fraction();
            return -400.0 * Math.log10(1.0 / fraction - 1.0);
        }
    }

    /**
     * Immutable gauntlet configuration.
     *
     * @param featuresA candidate alpha-beta features
     * @param featuresB baseline alpha-beta features
     * @param searchA candidate search kind
     * @param searchB baseline search kind
     * @param evalA candidate evaluator name
     * @param evalB baseline evaluator name
     * @param nodes shared fixed node budget per move (used when {@code movetime}
     *              is zero and a side has no per-side override)
     * @param movetime shared fixed time budget per move in milliseconds, or zero
     * @param nodesA candidate per-move node budget, or zero to use {@code nodes}
     * @param nodesB baseline per-move node budget, or zero to use {@code nodes}
     * @param movetimeA candidate per-move time budget in ms, or zero to share
     * @param movetimeB baseline per-move time budget in ms, or zero to share
     * @param tcA candidate game-clock time control, or {@link TimeControl#NONE}
     *            for a fixed per-move budget
     * @param tcB baseline game-clock time control, or {@link TimeControl#NONE}
     *            for a fixed per-move budget
     * @param engineA external UCI engine command for the candidate, or blank to
     *                use the built-in searcher
     * @param engineB external UCI engine command for the baseline, or blank to
     *                use the built-in searcher
     * @param hashA candidate UCI engine hash size in MB, or zero for its default
     * @param hashB baseline UCI engine hash size in MB, or zero for its default
     * @param optionsA candidate UCI {@code name=value} options, semicolon- or
     *                 newline-separated
     * @param optionsB baseline UCI {@code name=value} options, semicolon- or
     *                 newline-separated
     * @param cpuctA candidate MCTS cpuct
     * @param cpuctB baseline MCTS cpuct
     * @param fpuA candidate MCTS first-play-urgency reduction
     * @param fpuB baseline MCTS first-play-urgency reduction
     * @param checkPriorA candidate MCTS check-prior bonus
     * @param checkPriorB baseline MCTS check-prior bonus
     * @param capturePenaltyA candidate MCTS SEE-losing-capture prior penalty
     * @param capturePenaltyB baseline MCTS SEE-losing-capture prior penalty
     * @param captureWinScaleA candidate MCTS positive-SEE capture prior scale
     * @param captureWinScaleB baseline MCTS positive-SEE capture prior scale
     * @param maxPlies draw-adjudication ply cap
     * @param openingCount seeded-random opening count, or zero for the curated set
     * @param seed RNG seed for reproducible openings
     * @param threadsA candidate search threads
     * @param threadsB baseline search threads
     * @param workers concurrent opening-pair workers
     * @param sprt whether pentanomial SPRT early stopping is enabled
     * @param sprtElo0 SPRT H0 hypothesis Elo (ignored unless {@code sprt})
     * @param sprtElo1 SPRT H1 hypothesis Elo (ignored unless {@code sprt})
     */
    public record Config(
            Set<AlphaBeta.Feature> featuresA,
            Set<AlphaBeta.Feature> featuresB,
            SearchKind searchA,
            SearchKind searchB,
            String evalA,
            String evalB,
            long nodes,
            long movetime,
            long nodesA,
            long nodesB,
            long movetimeA,
            long movetimeB,
            TimeControl tcA,
            TimeControl tcB,
            String engineA,
            String engineB,
            int hashA,
            int hashB,
            String optionsA,
            String optionsB,
            double cpuctA,
            double cpuctB,
            double fpuA,
            double fpuB,
            int checkPriorA,
            int checkPriorB,
            int capturePenaltyA,
            int capturePenaltyB,
            int captureWinScaleA,
            int captureWinScaleB,
            int maxPlies,
            int openingCount,
            long seed,
            int threadsA,
            int threadsB,
            int workers,
            boolean sprt,
            double sprtElo0,
            double sprtElo1) {

        /**
         * Returns the shared per-move search limits implied by this configuration.
         *
         * @return shared per-move limits
         */
        public Limits limits() {
            return movetime > 0
                    ? new Limits(AlphaBeta.MAX_DEPTH, 0L, movetime)
                    : new Limits(AlphaBeta.MAX_DEPTH, nodes, 0L);
        }

        /**
         * Returns the candidate (A) per-move limits, honoring a per-side
         * override and falling back to the shared budget.
         *
         * @return candidate per-move limits
         */
        public Limits limitsA() {
            return sideLimits(movetimeA, nodesA);
        }

        /**
         * Returns the baseline (B) per-move limits, honoring a per-side override
         * and falling back to the shared budget.
         *
         * @return baseline per-move limits
         */
        public Limits limitsB() {
            return sideLimits(movetimeB, nodesB);
        }

        /**
         * Resolves per-side limits, preferring a per-side time or node budget and
         * falling back to the shared {@code movetime}/{@code nodes}.
         *
         * @param sideMovetime per-side time budget in ms, or zero
         * @param sideNodes per-side node budget, or zero
         * @return resolved per-move limits
         */
        private Limits sideLimits(long sideMovetime, long sideNodes) {
            long resolvedMovetime = sideMovetime > 0 ? sideMovetime : movetime;
            long resolvedNodes = sideNodes > 0 ? sideNodes : nodes;
            return resolvedMovetime > 0
                    ? new Limits(AlphaBeta.MAX_DEPTH, 0L, resolvedMovetime)
                    : new Limits(AlphaBeta.MAX_DEPTH, resolvedNodes, 0L);
        }

        /**
         * Returns whether the candidate (A) side plays on a game clock.
         *
         * @return true when a candidate time control is configured
         */
        public boolean usesClockA() {
            return tcA != null && tcA.enabled();
        }

        /**
         * Returns whether the baseline (B) side plays on a game clock.
         *
         * @return true when a baseline time control is configured
         */
        public boolean usesClockB() {
            return tcB != null && tcB.enabled();
        }

        /**
         * Returns whether the candidate side is an external UCI engine.
         *
         * @return true when an external candidate engine is configured
         */
        public boolean usesEngineA() {
            return engineA != null && !engineA.isBlank();
        }

        /**
         * Returns whether the baseline side is an external UCI engine.
         *
         * @return true when an external baseline engine is configured
         */
        public boolean usesEngineB() {
            return engineB != null && !engineB.isBlank();
        }

        /**
         * Returns a human-readable per-move budget description, collapsing to one
         * value when both sides share the same budget.
         *
         * @return budget description
         */
        public String budgetText() {
            String a = sideBudgetText(movetimeA, nodesA, tcA);
            String b = sideBudgetText(movetimeB, nodesB, tcB);
            return a.equals(b) ? a : "A=" + a + " B=" + b;
        }

        /**
         * Returns a per-side budget description.
         *
         * @param sideMovetime per-side time budget in ms, or zero
         * @param sideNodes per-side node budget, or zero
         * @param sideTc per-side game-clock time control, possibly disabled
         * @return per-side budget description
         */
        private String sideBudgetText(long sideMovetime, long sideNodes, TimeControl sideTc) {
            if (sideTc != null && sideTc.enabled()) {
                return "tc " + sideTc.text() + "/game";
            }
            long resolvedMovetime = sideMovetime > 0 ? sideMovetime : movetime;
            long resolvedNodes = sideNodes > 0 ? sideNodes : nodes;
            return resolvedMovetime > 0 ? resolvedMovetime + "ms/move" : resolvedNodes + " nodes/move";
        }
    }

    /**
     * Outcome of one game from the candidate's perspective.
     */
    private enum Outcome {

        /**
         * Candidate won.
         */
        WIN,

        /**
         * Game drawn.
         */
        DRAW,

        /**
         * Candidate lost.
         */
        LOSS;

        /**
         * Returns the lowercase candidate-perspective result label.
         *
         * @return result label
         */
        String label() {
            return switch (this) {
                case WIN -> "win";
                case DRAW -> "draw";
                case LOSS -> "loss";
            };
        }
    }

    /**
     * Two games from one opening: candidate as White, then candidate as Black.
     *
     * @param first candidate-as-White result
     * @param second candidate-as-Black result
     */
    private record OpeningResult(Outcome first, Outcome second) {

        /**
         * Returns the candidate's pair score in half points ({@code 0..4}),
         * which is also the pentanomial category index for this pair.
         *
         * @return candidate pair score in half points
         */
        int halfPoints() {
            return Sprt.pairCategory(points(first), points(second));
        }

        /**
         * Returns one game's candidate score in half points.
         *
         * @param outcome game outcome
         * @return {@code 2} for a win, {@code 1} for a draw, {@code 0} for a loss
         */
        private static int points(Outcome outcome) {
            return switch (outcome) {
                case WIN -> 2;
                case DRAW -> 1;
                case LOSS -> 0;
            };
        }
    }

    /**
     * Both sides' game-clock state at the moment a move search starts, in the
     * fields an external UCI engine expects on {@code go}.
     *
     * @param whiteTimeMillis White's remaining time in milliseconds
     * @param blackTimeMillis Black's remaining time in milliseconds
     * @param whiteIncrementMillis White's per-move increment in milliseconds
     * @param blackIncrementMillis Black's per-move increment in milliseconds
     */
    private record ClockState(long whiteTimeMillis, long blackTimeMillis,
            long whiteIncrementMillis, long blackIncrementMillis) {
    }

    /**
     * Per-side searcher abstraction for self-play.
     */
    private interface GameSearcher extends AutoCloseable {

        /**
         * Searches a position.
         *
         * @param position root position
         * @param limits search limits
         * @param history pre-root core-position history
         * @param clock game-clock state when the side to move plays on a
         *              clock, or {@code null} for a fixed per-move budget
         * @return search result
         */
        Result search(Position position, Limits limits, long[] history, ClockState clock);

        /**
         * Releases resources.
         */
        @Override
        void close();
    }

    /**
     * Drives an external UCI engine over its standard-input/-output protocol so
     * it can play one side of a gauntlet game.
     *
     * <p>
     * Each instance owns one engine process for the duration of a single game:
     * it performs the {@code uci}/{@code isready} handshake on construction,
     * sends {@code position fen ...} plus a budget-matched {@code go} for every
     * move, and parses the engine's {@code bestmove}. Failures (a crashed or
     * silent engine) surface as {@link Move#NO_MOVE}, which the game loop treats
     * as a forfeit rather than letting one bad engine hang the whole gauntlet.
     * </p>
     */
    private static final class UciSearcher implements GameSearcher {

        /**
         * Maximum time to wait for the handshake and for each {@code bestmove}.
         */
        private static final long REPLY_TIMEOUT_MILLIS = 60_000L;

        /**
         * Engine process.
         */
        private final Process process;

        /**
         * Engine standard output reader.
         */
        private final BufferedReader reader;

        /**
         * Engine standard input writer.
         */
        private final BufferedWriter writer;

        /**
         * Original command, for diagnostics.
         */
        private final String command;

        /**
         * Engine threads, hash, and extra UCI options applied at startup.
         */
        private final int threads;
        private final int hashMb;
        private final String options;

        /**
         * Starts and handshakes an external UCI engine.
         *
         * @param command engine command line (path plus optional arguments)
         * @param threads number of engine threads, or non-positive for default
         * @param hashMb engine hash size in MB, or non-positive for default
         * @param options extra {@code name=value} UCI options, semicolon- or
         *                newline-separated
         */
        UciSearcher(String command, int threads, int hashMb, String options) {
            this.command = command;
            this.threads = threads;
            this.hashMb = hashMb;
            this.options = options;
            try {
                ProcessBuilder builder = new ProcessBuilder(tokenize(command));
                builder.redirectErrorStream(false);
                this.process = builder.start();
                this.reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                this.writer = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                handshake();
            } catch (IOException ex) {
                throw new IllegalStateException("failed to start UCI engine: " + command, ex);
            }
        }

        /**
         * Performs the UCI initialization handshake, applying the configured
         * Threads, Hash, and any extra options before signalling readiness.
         *
         * @throws IOException on a stream failure or premature engine exit
         */
        private void handshake() throws IOException {
            send("uci");
            awaitToken("uciok");
            if (threads > 0) {
                send("setoption name Threads value " + threads);
            }
            if (hashMb > 0) {
                send("setoption name Hash value " + hashMb);
            }
            for (String option : parseOptions(options)) {
                send(option);
            }
            send("isready");
            awaitToken("readyok");
            send("ucinewgame");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Result search(Position position, Limits limits, long[] history, ClockState clock) {
            try {
                send("position fen " + position);
                send("go " + (clock != null ? clockArguments(clock) : goArguments(limits)));
                return new Result(readBestMove(), 0, 0, 0L, 0L, false, NO_PV);
            } catch (IOException ex) {
                return new Result(Move.NO_MOVE, 0, 0, 0L, 0L, true, NO_PV);
            }
        }

        /**
         * Builds the {@code go} arguments matching the per-move budget.
         *
         * @param limits per-move limits
         * @return {@code go} arguments
         */
        private static String goArguments(Limits limits) {
            if (limits.maxDurationMillis() > 0L) {
                return "movetime " + limits.maxDurationMillis();
            }
            if (limits.maxNodes() > 0L) {
                return "nodes " + limits.maxNodes();
            }
            return "depth " + limits.depth();
        }

        /**
         * Builds the {@code go} clock arguments so the external engine runs
         * its own time management against the live game clocks.
         *
         * @param clock both sides' clock state
         * @return {@code go} arguments
         */
        private static String clockArguments(ClockState clock) {
            return "wtime " + clock.whiteTimeMillis()
                    + " btime " + clock.blackTimeMillis()
                    + " winc " + clock.whiteIncrementMillis()
                    + " binc " + clock.blackIncrementMillis();
        }

        /**
         * Reads lines until a {@code bestmove} is reported and decodes it.
         *
         * @return decoded move, or {@link Move#NO_MOVE} when none is available
         * @throws IOException on a stream failure or premature engine exit
         */
        private short readBestMove() throws IOException {
            String line = awaitLine("bestmove");
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2) {
                return Move.NO_MOVE;
            }
            String uci = parts[1];
            if ("(none)".equals(uci) || "0000".equals(uci)) {
                return Move.NO_MOVE;
            }
            try {
                return Move.parse(uci);
            } catch (IllegalArgumentException ex) {
                return Move.NO_MOVE;
            }
        }

        /**
         * Waits for a line starting with the supplied token.
         *
         * @param token expected leading token
         * @throws IOException on a stream failure or premature engine exit
         */
        private void awaitToken(String token) throws IOException {
            awaitLine(token);
        }

        /**
         * Waits for and returns the first line whose first whitespace-delimited
         * token equals the supplied token, within the reply timeout.
         *
         * @param token expected leading token
         * @return matching line
         * @throws IOException on a stream failure, timeout, or premature exit
         */
        private String awaitLine(String token) throws IOException {
            long deadline = System.nanoTime() + REPLY_TIMEOUT_MILLIS * 1_000_000L;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.equals(token) || trimmed.startsWith(token + " ")) {
                    return trimmed;
                }
                if (System.nanoTime() > deadline) {
                    throw new IOException("UCI engine '" + command + "' timed out waiting for '" + token + "'");
                }
            }
            throw new IOException("UCI engine '" + command + "' exited before '" + token + "'");
        }

        /**
         * Sends one command line to the engine.
         *
         * @param line command without a trailing newline
         * @throws IOException on a stream failure
         */
        private void send(String line) throws IOException {
            writer.write(line);
            writer.write('\n');
            writer.flush();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            try {
                send("quit");
            } catch (IOException ignored) {
                // The engine may already be gone; fall through to destroy.
            }
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        /**
         * Splits an engine command line into a process argument list on
         * whitespace.
         *
         * @param command engine command line
         * @return non-empty argument list
         */
        private static List<String> tokenize(String command) {
            List<String> tokens = new ArrayList<>();
            for (String part : command.trim().split("\\s+")) {
                if (!part.isEmpty()) {
                    tokens.add(part);
                }
            }
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("empty UCI engine command");
            }
            return tokens;
        }

        /**
         * Parses free-form {@code name=value} options (separated by semicolons or
         * newlines) into ready-to-send {@code setoption} command lines.
         *
         * @param options option text, possibly blank
         * @return setoption command lines
         */
        private static List<String> parseOptions(String options) {
            List<String> commands = new ArrayList<>();
            if (options == null || options.isBlank()) {
                return commands;
            }
            for (String entry : options.split("[;\\n]")) {
                String trimmed = entry.trim();
                int equals = trimmed.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String name = trimmed.substring(0, equals).trim();
                String value = trimmed.substring(equals + 1).trim();
                if (!name.isEmpty()) {
                    commands.add("setoption name " + name + " value " + value);
                }
            }
            return commands;
        }
    }

    /**
     * Probes an external UCI engine: starts it, performs the {@code uci}
     * handshake, and returns the engine's reported {@code id name}. Used by the
     * workbench's engine connection test.
     *
     * @param command engine command line (path plus optional arguments)
     * @return reported engine name, never blank
     * @throws IOException if the engine cannot be started or does not respond
     */
    public static String uciEngineName(String command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(UciSearcher.tokenize(command));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write("uci\n");
            writer.flush();
            String name = null;
            long deadline = System.nanoTime() + 10_000L * 1_000_000L;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("id name ")) {
                    name = trimmed.substring("id name ".length()).trim();
                }
                if (trimmed.equals("uciok")) {
                    break;
                }
                if (System.nanoTime() > deadline) {
                    throw new IOException("engine did not answer 'uci' within 10s");
                }
            }
            try {
                writer.write("quit\n");
                writer.flush();
            } catch (IOException ignored) {
                // Engine may already be exiting.
            }
            if (name == null || name.isBlank()) {
                throw new IOException("engine did not report an id name");
            }
            return name;
        } finally {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    /**
     * Shared empty principal variation for synthetic results.
     */
    private static final short[] NO_PV = new short[0];

    /**
     * Varied opening positions (each played from both colors) so the two
     * deterministic engines do not replay one identical game.
     */
    private static final String[] OPENINGS = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/8/6P1/PPPPPP1P/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/8/1P6/P1PPPPPP/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pppppp1p/6p1/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/ppppp1pp/8/5p2/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pppppp1p/6p1/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pppp1ppp/8/4p3/2P5/8/PP1PPPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pp1ppppp/8/2p5/2P5/8/PP1PPPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/ppp1pppp/8/3p4/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - 0 2",
        "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
        "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
        "rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b KQkq - 0 2",
    };

    /**
     * Path to the bundled NNUE network, for {@code nnue} evaluators.
     */
    private static final String NNUE_PATH = "models/crtk-halfkp.nnue";

    /**
     * Starting non-king material for both sides.
     */
    private static final int STARTING_MATERIAL = 7_800;

    /**
     * SPRT type-I error rate (probability of falsely accepting H1).
     */
    public static final double SPRT_ALPHA = 0.05;

    /**
     * SPRT type-II error rate (probability of falsely accepting H0).
     */
    public static final double SPRT_BETA = 0.05;

    /**
     * Prevents instantiation.
     */
    private Gauntlet() {
        // utility
    }

    /**
     * Resolves the opening positions for a configuration.
     *
     * <p>
     * Returns the curated opening set when {@link Config#openingCount()} is zero,
     * otherwise a reproducible seeded-random set of that size.
     * </p>
     *
     * @param config gauntlet configuration
     * @return opening FENs (each played from both colors)
     */
    public static String[] openings(Config config) {
        return config.openingCount() > 0
                ? generateOpenings(config.openingCount(), config.seed())
                : OPENINGS.clone();
    }

    /**
     * Returns the number of openings in the curated default set.
     *
     * @return curated opening count
     */
    public static int curatedOpeningCount() {
        return OPENINGS.length;
    }

    /**
     * Runs the gauntlet over the supplied openings.
     *
     * <p>
     * When {@link Config#sprt()} is enabled the run may stop before exhausting
     * the openings: completed opening pairs feed a pentanomial {@link Sprt}
     * and the run stops scheduling new pairs once a stopping bound is crossed,
     * while the opening count remains a hard cap. The evolving SPRT state is
     * reported through {@link ProgressListener#onSprt}.
     * </p>
     *
     * @param config gauntlet configuration
     * @param openings opening FENs (see {@link #openings(Config)})
     * @param listener progress listener, or {@code null} for none
     * @return aggregate score from the candidate's perspective
     */
    public static Score run(Config config, String[] openings, ProgressListener listener) {
        if (config.workers() < 1) {
            throw new IllegalArgumentException("workers must be positive");
        }
        ProgressListener sink = listener == null ? ProgressListener.NONE : listener;
        // Probe each evaluator once so a missing NNUE is reported a single time
        // rather than per game.
        probeEvaluator(config.evalA(), sink);
        if (!config.evalB().equalsIgnoreCase(config.evalA())) {
            probeEvaluator(config.evalB(), sink);
        }
        Sprt sprt = config.sprt()
                ? new Sprt(config.sprtElo0(), config.sprtElo1(), SPRT_ALPHA, SPRT_BETA)
                : null;
        // Run-wide latch so the first time forfeit is reported exactly once.
        AtomicBoolean timeForfeitNoted = new AtomicBoolean(false);
        return config.workers() == 1
                ? runSequential(config, openings, sink, sprt, timeForfeitNoted)
                : runParallel(config, openings, sink, sprt, timeForfeitNoted);
    }

    /**
     * Runs opening pairs one at a time, stopping at the first crossed SPRT
     * bound when SPRT stopping is enabled.
     *
     * @param config gauntlet configuration
     * @param openings opening FENs
     * @param listener progress listener
     * @param sprt SPRT tracker, or {@code null} when disabled
     * @param timeForfeitNoted run-wide first-time-forfeit note latch
     * @return aggregate score
     */
    private static Score runSequential(Config config, String[] openings, ProgressListener listener,
            Sprt sprt, AtomicBoolean timeForfeitNoted) {
        int[] score = { 0, 0, 0 };
        for (int i = 0; i < openings.length; i++) {
            OpeningResult result = playOpeningPair(openings[i], config, i, listener, timeForfeitNoted);
            add(score, result);
            listener.onProgress(i + 1, openings.length, new Score(score[0], score[1], score[2]));
            if (updateSprt(sprt, result, i + 1, openings.length, listener)) {
                break;
            }
        }
        return new Score(score[0], score[1], score[2]);
    }

    /**
     * Runs opening pairs concurrently, collecting futures in opening order so the
     * progress stream remains deterministic.
     *
     * <p>
     * A pair only enters the score and the pentanomial statistics once both of
     * its games are complete (each submitted task plays the full pair). When an
     * SPRT bound is crossed, the shared stop flag prevents not-yet-started
     * pairs from playing, while pairs already in flight finish and are still
     * counted; the SPRT decision itself is latched at the first crossing.
     * </p>
     *
     * @param config gauntlet configuration
     * @param openings opening FENs
     * @param listener progress listener
     * @param sprt SPRT tracker, or {@code null} when disabled
     * @param timeForfeitNoted run-wide first-time-forfeit note latch
     * @return aggregate score
     */
    private static Score runParallel(Config config, String[] openings, ProgressListener listener,
            Sprt sprt, AtomicBoolean timeForfeitNoted) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(config.workers(), openings.length));
        AtomicBoolean stop = new AtomicBoolean(false);
        try {
            List<Future<OpeningResult>> futures = new ArrayList<>(openings.length);
            for (int i = 0; i < openings.length; i++) {
                final int openingIndex = i;
                final String opening = openings[i];
                futures.add(pool.submit(() -> stop.get()
                        ? null
                        : playOpeningPair(opening, config, openingIndex, listener, timeForfeitNoted)));
            }
            int[] score = { 0, 0, 0 };
            int completed = 0;
            for (int i = 0; i < futures.size(); i++) {
                OpeningResult result = futures.get(i).get();
                if (result == null) {
                    // Skipped: an SPRT bound was crossed before this pair started.
                    continue;
                }
                completed++;
                add(score, result);
                listener.onProgress(completed, openings.length, new Score(score[0], score[1], score[2]));
                if (updateSprt(sprt, result, completed, openings.length, listener)) {
                    stop.set(true);
                }
            }
            return new Score(score[0], score[1], score[2]);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("self-play interrupted", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("self-play worker failed", ex.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Folds one completed opening pair into the SPRT statistics and reports the
     * updated state.
     *
     * @param sprt SPRT tracker, or {@code null} when disabled
     * @param result completed opening-pair result
     * @param completed completed opening pairs counted so far
     * @param total scheduled opening pairs
     * @param listener progress listener
     * @return true when a stopping bound has been crossed
     */
    private static boolean updateSprt(Sprt sprt, OpeningResult result, int completed, int total,
            ProgressListener listener) {
        if (sprt == null) {
            return false;
        }
        sprt.addPair(result.halfPoints());
        listener.onSprt(completed, total, sprt.snapshot());
        return sprt.status() != Sprt.Status.CONTINUE;
    }

    /**
     * Plays both color assignments for one opening.
     *
     * @param opening opening FEN
     * @param config gauntlet configuration
     * @param openingIndex zero-based opening index, for stable game numbering
     * @param listener progress listener notified of each completed game
     * @param timeForfeitNoted run-wide first-time-forfeit note latch
     * @return two-game result for the opening
     */
    private static OpeningResult playOpeningPair(String opening, Config config, int openingIndex,
            ProgressListener listener, AtomicBoolean timeForfeitNoted) {
        // Play each opening from both colors so a result is not just one
        // deterministic game counted twice.
        Outcome first = playGame(opening, true, config, openingIndex * 2, listener, timeForfeitNoted);
        Outcome second = playGame(opening, false, config, openingIndex * 2 + 1, listener, timeForfeitNoted);
        return new OpeningResult(first, second);
    }

    /**
     * Adds one opening-pair result to an aggregate score.
     *
     * @param score mutable {wins, draws, losses} score
     * @param result opening-pair result
     */
    private static void add(int[] score, OpeningResult result) {
        add(score, result.first());
        add(score, result.second());
    }

    /**
     * Adds one game outcome to an aggregate score.
     *
     * @param score mutable {wins, draws, losses} score
     * @param outcome game outcome
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
     * Generates a reproducible set of varied, non-terminal opening positions by
     * playing a short seeded-random sequence of legal moves from the standard
     * start. Each opening is still played deterministically by both engines, so
     * the only randomness is which positions are sampled, fixed by the seed.
     *
     * @param count number of distinct openings to produce
     * @param seed RNG seed for reproducibility
     * @return array of opening FENs
     */
    private static String[] generateOpenings(int count, long seed) {
        Random rng = new Random(seed);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        String start = OPENINGS[0];
        int attempts = 0;
        int cap = count * 100 + 1000;
        while (set.size() < count && attempts < cap) {
            attempts++;
            Position position = new Position(start);
            int plies = 4 + rng.nextInt(5); // 4..8 plies
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
        return set.toArray(new String[0]);
    }

    /**
     * Plays one game and returns its outcome from the candidate's perspective.
     *
     * <p>
     * Game clocks are created fresh per game and per side here, so clocked
     * timing is independent of how opening pairs are distributed over workers.
     * </p>
     *
     * @param fen opening FEN
     * @param candidateIsWhite whether the candidate plays white this game
     * @param config gauntlet configuration
     * @param gameIndex zero-based game index, for stable numbering
     * @param listener progress listener notified when the game completes
     * @param timeForfeitNoted run-wide first-time-forfeit note latch
     * @return game outcome from the candidate's perspective
     */
    private static Outcome playGame(String fen, boolean candidateIsWhite, Config config, int gameIndex,
            ProgressListener listener, AtomicBoolean timeForfeitNoted) {
        Position position = new Position(fen);
        GameSearcher candidate = sideSearcher(config, true);
        GameSearcher baseline = sideSearcher(config, false);
        Limits limitsA = config.limitsA();
        Limits limitsB = config.limitsB();
        GameClock clockA = config.usesClockA() ? new GameClock(config.tcA()) : null;
        GameClock clockB = config.usesClockB() ? new GameClock(config.tcB()) : null;
        List<String> moves = new ArrayList<>();
        Outcome outcome;
        try {
            outcome = playMoves(position, candidate, baseline, candidateIsWhite, limitsA, limitsB,
                    clockA, clockB, config.maxPlies(), moves, listener, timeForfeitNoted);
        } finally {
            candidate.close();
            baseline.close();
        }
        listener.onGame(new GameRecord(gameIndex, candidateIsWhite, outcome.label(), fen, moves));
        return outcome;
    }

    /**
     * Builds the searcher for one side, using an external UCI engine when one is
     * configured for that side and the built-in searcher otherwise.
     *
     * @param config gauntlet configuration
     * @param candidate true for the candidate (A) side, false for baseline (B)
     * @return per-side searcher
     */
    private static GameSearcher sideSearcher(Config config, boolean candidate) {
        if (candidate && config.usesEngineA()) {
            return new UciSearcher(config.engineA(), config.threadsA(), config.hashA(), config.optionsA());
        }
        if (!candidate && config.usesEngineB()) {
            return new UciSearcher(config.engineB(), config.threadsB(), config.hashB(), config.optionsB());
        }
        return candidate
                ? searcher(config.searchA(), evaluator(config.evalA()), config.featuresA(),
                        config.threadsA(), config.cpuctA(), config.fpuA(), config.checkPriorA(),
                        config.capturePenaltyA(), config.captureWinScaleA())
                : searcher(config.searchB(), evaluator(config.evalB()), config.featuresB(),
                        config.threadsB(), config.cpuctB(), config.fpuB(), config.checkPriorB(),
                        config.capturePenaltyB(), config.captureWinScaleB());
    }

    /**
     * Plays a game out from a position, appending each played move (UCI) and
     * returning the candidate-perspective outcome.
     *
     * <p>
     * For a side with a game clock, each move's limits come from that clock
     * through the shared {@link Limits#clockBudgetMillis} mapping, the elapsed
     * wall-clock think time is deducted afterwards, and a flag fall loses the
     * game for that side.
     * </p>
     *
     * @param position mutable game position, advanced in place
     * @param candidate candidate searcher
     * @param baseline baseline searcher
     * @param candidateIsWhite whether the candidate plays White
     * @param limitsA candidate per-move limits (unused while A is on a clock)
     * @param limitsB baseline per-move limits (unused while B is on a clock)
     * @param clockA candidate game clock, or {@code null} for a fixed budget
     * @param clockB baseline game clock, or {@code null} for a fixed budget
     * @param maxPlies draw-adjudication ply cap
     * @param moves output list of played moves in UCI notation
     * @param listener progress listener, for the one-time time-forfeit note
     * @param timeForfeitNoted run-wide first-time-forfeit note latch
     * @return game outcome from the candidate's perspective
     */
    private static Outcome playMoves(Position position, GameSearcher candidate, GameSearcher baseline,
            boolean candidateIsWhite, Limits limitsA, Limits limitsB, GameClock clockA, GameClock clockB,
            int maxPlies, List<String> moves, ProgressListener listener, AtomicBoolean timeForfeitNoted) {
        Map<Long, Integer> seen = new HashMap<>();
        List<Long> history = new ArrayList<>();
        countPosition(seen, position);
        for (int ply = 0; ply < maxPlies; ply++) {
            MoveList legal = position.legalMoves();
            if (legal.isEmpty()) {
                // Checkmate or stalemate; the side to move has no reply.
                if (position.inCheck()) {
                    boolean whiteMated = position.isWhiteToMove();
                    return whiteMated == candidateIsWhite ? Outcome.LOSS : Outcome.WIN;
                }
                return Outcome.DRAW;
            }
            if (position.isInsufficientMaterial()
                    || position.halfMoveClock() >= 100
                    || seen.getOrDefault(position.signatureCore(), 0) >= 3) {
                return Outcome.DRAW;
            }
            boolean candidateToMove = position.isWhiteToMove() == candidateIsWhite;
            GameSearcher engine = candidateToMove ? candidate : baseline;
            GameClock clock = candidateToMove ? clockA : clockB;
            Limits limits = clock != null ? clock.limits() : (candidateToMove ? limitsA : limitsB);
            ClockState clockState = clock == null ? null : clockState(candidateIsWhite, clockA, clockB);
            long startNanos = System.nanoTime();
            Result result = engine.search(position, limits, historyArray(history), clockState);
            long elapsedMillis = elapsedMillisCeil(startNanos);
            if (clock != null && !clock.spend(elapsedMillis)) {
                // Flag fall: the side to move loses on time, like any other
                // decisive result.
                noteTimeForfeit(candidateToMove, listener, timeForfeitNoted);
                return candidateToMove ? Outcome.LOSS : Outcome.WIN;
            }
            short move = result.bestMove();
            if (move == Move.NO_MOVE || !isLegal(legal, move)) {
                // Defensive: a stuck engine forfeits.
                return candidateToMove ? Outcome.LOSS : Outcome.WIN;
            }
            moves.add(Move.toString(move));
            history.add(position.signatureCore());
            position.play(move);
            countPosition(seen, position);
        }
        return Outcome.DRAW;
    }

    /**
     * Builds the UCI-facing clock snapshot for the side to move. An unclocked
     * opponent mirrors the mover's clock so external engines always receive
     * both {@code wtime}/{@code btime} fields.
     *
     * @param candidateIsWhite whether the candidate plays White
     * @param clockA candidate game clock, or {@code null}
     * @param clockB baseline game clock, or {@code null}
     * @return clock snapshot; only called when at least one clock exists
     */
    private static ClockState clockState(boolean candidateIsWhite, GameClock clockA, GameClock clockB) {
        GameClock white = candidateIsWhite ? clockA : clockB;
        GameClock black = candidateIsWhite ? clockB : clockA;
        long whiteTime = white != null ? white.remainingMillis() : black.remainingMillis();
        long blackTime = black != null ? black.remainingMillis() : white.remainingMillis();
        long whiteIncrement = white != null ? white.incrementMillis() : black.incrementMillis();
        long blackIncrement = black != null ? black.incrementMillis() : white.incrementMillis();
        return new ClockState(whiteTime, blackTime, whiteIncrement, blackIncrement);
    }

    /**
     * Returns the elapsed wall-clock time since a start mark, rounded up so a
     * fast move still costs at least one millisecond of clock time.
     *
     * @param startNanos {@link System#nanoTime()} mark taken before the search
     * @return elapsed milliseconds, rounded up
     */
    private static long elapsedMillisCeil(long startNanos) {
        long nanos = Math.max(0L, System.nanoTime() - startNanos);
        return (nanos + 999_999L) / 1_000_000L;
    }

    /**
     * Reports the first time forfeit of a run through the listener; later
     * forfeits stay silent so a long run is not flooded with notes.
     *
     * @param candidateToMove whether the candidate's flag fell
     * @param listener progress listener
     * @param timeForfeitNoted run-wide first-time-forfeit note latch
     */
    private static void noteTimeForfeit(boolean candidateToMove, ProgressListener listener,
            AtomicBoolean timeForfeitNoted) {
        if (timeForfeitNoted.compareAndSet(false, true)) {
            listener.onNote("game clock flag fell for the "
                    + (candidateToMove ? "candidate (A)" : "baseline (B)")
                    + " - scored as a loss for that side (reported once per run)");
        }
    }

    /**
     * Builds a per-side searcher.
     *
     * @param kind search kind
     * @param evaluator evaluator instance
     * @param features alpha-beta features
     * @param threads worker threads
     * @param cpuct MCTS exploration constant
     * @param fpuReduction MCTS first-play urgency reduction
     * @param checkPriorBonus MCTS check-prior bonus
     * @param losingCapturePriorPenalty MCTS SEE-losing-capture prior penalty
     * @param winningCapturePriorScale MCTS positive-SEE capture prior scale
     * @return searcher wrapper
     */
    private static GameSearcher searcher(
            SearchKind kind,
            CentipawnEvaluator evaluator,
            Set<AlphaBeta.Feature> features,
            int threads,
            double cpuct,
            double fpuReduction,
            int checkPriorBonus,
            int losingCapturePriorPenalty,
            int winningCapturePriorScale) {
        if (kind == SearchKind.MCTS) {
            Mcts mcts = new Mcts(evaluator, cpuct, fpuReduction, checkPriorBonus, losingCapturePriorPenalty,
                    winningCapturePriorScale);
            mcts.setThreads(threads);
            return new GameSearcher() {
                /**
                 * Searches with the reusable MCTS tree and supplied repetition
                 * history; clocked limits already arrive as a time budget.
                 */
                @Override
                public Result search(Position position, Limits limits, long[] history, ClockState clock) {
                    return mcts.searchReusable(position, limits, null, history);
                }

                /**
                 * Releases the wrapped MCTS searcher.
                 */
                @Override
                public void close() {
                    mcts.close();
                }
            };
        }
        AlphaBeta alphaBeta = new AlphaBeta(evaluator, true, features).setSearchThreads(threads);
        return new GameSearcher() {
            /**
             * Searches with the alpha-beta engine; clocked limits already
             * arrive as a time budget.
             */
            @Override
            public Result search(Position position, Limits limits, long[] history, ClockState clock) {
                return alphaBeta.search(position, limits);
            }

            /**
             * Releases the wrapped alpha-beta engine.
             */
            @Override
            public void close() {
                alphaBeta.close();
            }
        };
    }

    /**
     * Copies history keys to a primitive array.
     *
     * @param history pre-root history keys
     * @return primitive history
     */
    private static long[] historyArray(List<Long> history) {
        long[] out = new long[history.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = history.get(i).longValue();
        }
        return out;
    }

    /**
     * Records a position occurrence for threefold detection.
     *
     * @param seen occurrence counts by core signature
     * @param position current position
     */
    private static void countPosition(Map<Long, Integer> seen, Position position) {
        long key = position.signatureCore();
        seen.merge(key, 1, Integer::sum);
    }

    /**
     * Returns whether a move is present in a legal move list.
     *
     * @param legal legal move list
     * @param move move to find
     * @return true when legal
     */
    private static boolean isLegal(MoveList legal, short move) {
        for (int i = 0; i < legal.size(); i++) {
            if (legal.raw(i) == move) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds an evaluator once and reports through the listener if a requested
     * NNUE network is unavailable and a classical fallback will be used.
     *
     * @param eval evaluator name
     * @param listener progress listener
     */
    private static void probeEvaluator(String eval, ProgressListener listener) {
        String nnuePath = nnuePath(eval);
        if (nnuePath != null) {
            try {
                new Nnue(Path.of(nnuePath)).close();
            } catch (RuntimeException | java.io.IOException ex) {
                listener.onNote("nnue unavailable: " + ex.getMessage() + " - using classical");
            }
        }
    }

    /**
     * Resolves an evaluator name to an NNUE weights path.
     *
     * <p>
     * Plain {@code nnue} selects the bundled default network, while
     * {@code nnue:<path>} selects an explicit weights file so A/B runs can
     * compare different networks.
     * </p>
     *
     * @param eval evaluator name
     * @return weights path, or {@code null} for non-NNUE evaluators
     */
    private static String nnuePath(String eval) {
        if ("nnue".equalsIgnoreCase(eval)) {
            return NNUE_PATH;
        }
        if (eval.regionMatches(true, 0, "nnue:", 0, "nnue:".length())) {
            return eval.substring("nnue:".length());
        }
        return null;
    }

    /**
     * Builds a fresh evaluator, falling back to classical when a net cannot load.
     *
     * @param eval evaluator name
     * @return evaluator instance
     */
    private static CentipawnEvaluator evaluator(String eval) {
        String nnuePath = nnuePath(eval);
        if (nnuePath != null) {
            try {
                return new Nnue(Path.of(nnuePath));
            } catch (RuntimeException | java.io.IOException ex) {
                // Reported once via probeEvaluator; fall back silently per game.
                return new Classical();
            }
        }
        String normalized = eval.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("scale") && normalized.length() > "scale".length()) {
            int scale = Integer.parseInt(normalized.substring("scale".length()));
            return new ExperimentalClassical(eval, 0, scale, false, false, false);
        }
        return switch (normalized) {
            case "rawclassical" -> new ExperimentalClassical(eval, 0, 100, false, false, false);
            case "tempo16" -> new ExperimentalClassical(eval, 8, 100, false, false, false);
            case "development" -> new ExperimentalClassical(eval, 0, 100, true, false, false);
            case "tropism" -> new ExperimentalClassical(eval, 0, 100, false, true, false);
            case "devtropism" -> new ExperimentalClassical(eval, 0, 100, true, true, false);
            case "drawish" -> new ExperimentalClassical(eval, 0, 100, false, false, true);
            default -> new Classical();
        };
    }

    /**
     * Experimental wrapper around the production classical evaluator.
     *
     * @param label evaluator label
     * @param extraTempoCp additional side-to-move tempo
     * @param scalePercent scalar applied to the base classical score
     * @param development whether to add opening-development pressure
     * @param tropism whether to add piece/king tropism
     * @param drawish whether to damp low-material pawnless endings
     */
    private record ExperimentalClassical(
            String label,
            int extraTempoCp,
            int scalePercent,
            boolean development,
            boolean tropism,
            boolean drawish) implements CentipawnEvaluator {

        /**
         * Production evaluator used for the base score and move priors.
         */
        private static final Classical BASE = new Classical();

        /**
         * {@inheritDoc}
         */
        @Override
        public int evaluate(Position position) {
            int score = Wdl.evaluateStmCentipawns(position) * scalePercent / 100;
            if (extraTempoCp != 0) {
                score += extraTempoCp;
            }
            int whiteExtra = 0;
            if (development) {
                whiteExtra += developmentWhite(position);
            }
            if (tropism) {
                whiteExtra += tropismWhite(position);
            }
            if (whiteExtra != 0) {
                score += position.isWhiteToMove() ? whiteExtra : -whiteExtra;
            }
            if (drawish) {
                score = dampDrawishEndgame(position, score);
            }
            return score;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void prepareMoveOrdering(Position position) {
            BASE.prepareMoveOrdering(position);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void scoreMoves(Position position, short[] moves, int[] scores) {
            BASE.scoreMoves(position, moves, scores);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String name() {
            return label;
        }
    }

    /**
     * Scores undeveloped back-rank pieces from White's perspective.
     *
     * @param position current position
     * @return centipawn term from White's perspective
     */
    private static int developmentWhite(Position position) {
        int material = nonKingMaterial(position);
        if (material < STARTING_MATERIAL / 2) {
            return 0;
        }
        int score = 0;
        score -= undeveloped(position, Field.B1, Piece.WHITE_KNIGHT, 10);
        score -= undeveloped(position, Field.G1, Piece.WHITE_KNIGHT, 10);
        score -= undeveloped(position, Field.C1, Piece.WHITE_BISHOP, 8);
        score -= undeveloped(position, Field.F1, Piece.WHITE_BISHOP, 8);
        score += undeveloped(position, Field.B8, Piece.BLACK_KNIGHT, 10);
        score += undeveloped(position, Field.G8, Piece.BLACK_KNIGHT, 10);
        score += undeveloped(position, Field.C8, Piece.BLACK_BISHOP, 8);
        score += undeveloped(position, Field.F8, Piece.BLACK_BISHOP, 8);
        return score * Math.min(material, STARTING_MATERIAL) / STARTING_MATERIAL;
    }

    /**
     * Returns a penalty when a home-square piece is still undeveloped.
     *
     * @param position current position
     * @param square home square
     * @param piece expected undeveloped piece
     * @param penalty penalty value
     * @return penalty when the piece is on the home square
     */
    private static int undeveloped(Position position, int square, byte piece, int penalty) {
        return position.pieceAt(square) == piece ? penalty : 0;
    }

    /**
     * Scores attacking-piece distance to the enemy king from White's perspective.
     *
     * @param position current position
     * @return centipawn term from White's perspective
     */
    private static int tropismWhite(Position position) {
        int material = nonKingMaterial(position);
        int phase = Math.min(material, STARTING_MATERIAL);
        int whiteKing = position.kingSquare(true);
        int blackKing = position.kingSquare(false);
        if (whiteKing < 0 || blackKing < 0) {
            return 0;
        }
        int white = sideTropism(position, true, blackKing);
        int black = sideTropism(position, false, whiteKing);
        return (white - black) * phase / STARTING_MATERIAL;
    }

    /**
     * Scores one side's attacking pieces by Chebyshev distance to the enemy king.
     *
     * @param position current position
     * @param white side to score
     * @param enemyKing enemy king square
     * @return side tropism score
     */
    private static int sideTropism(Position position, boolean white, int enemyKing) {
        int side = 0;
        side += tropismPieces(position.pieces(white ? Position.WHITE_QUEEN : Position.BLACK_QUEEN), enemyKing, 4);
        side += tropismPieces(position.pieces(white ? Position.WHITE_ROOK : Position.BLACK_ROOK), enemyKing, 2);
        side += tropismPieces(position.pieces(white ? Position.WHITE_BISHOP : Position.BLACK_BISHOP), enemyKing, 2);
        side += tropismPieces(position.pieces(white ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT), enemyKing, 3);
        return side;
    }

    /**
     * Scores a bitboard of pieces by proximity to the enemy king.
     *
     * @param pieces piece bitboard
     * @param enemyKing enemy king square
     * @param weight per-distance weight
     * @return tropism score
     */
    private static int tropismPieces(long pieces, int enemyKing, int weight) {
        int score = 0;
        long remaining = pieces;
        while (remaining != 0L) {
            int square = Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1L;
            score += Math.max(0, 7 - kingDistance(square, enemyKing)) * weight;
        }
        return score;
    }

    /**
     * Damps optimistic scores in pawnless low-material endings.
     *
     * @param position current position
     * @param score side-to-move score
     * @return possibly damped score
     */
    private static int dampDrawishEndgame(Position position, int score) {
        if ((position.pieces(Position.WHITE_PAWN) | position.pieces(Position.BLACK_PAWN)) != 0L) {
            return score;
        }
        int material = nonKingMaterial(position);
        if (material <= Piece.VALUE_ROOK + Piece.VALUE_BISHOP) {
            return score * 65 / 100;
        }
        if (material <= 2 * Piece.VALUE_ROOK + 2 * Piece.VALUE_BISHOP) {
            return score * 80 / 100;
        }
        return score;
    }

    /**
     * Returns total non-king material for both sides.
     *
     * @param position current position
     * @return material in centipawns
     */
    private static int nonKingMaterial(Position position) {
        int total = 0;
        byte[] board = position.getBoard();
        for (byte piece : board) {
            int type = Math.abs(piece);
            if (type != Piece.KING) {
                total += Piece.getValue(piece);
            }
        }
        return total;
    }

    /**
     * Chebyshev king distance between two squares.
     *
     * @param a first square
     * @param b second square
     * @return king distance
     */
    private static int kingDistance(int a, int b) {
        return Math.max(Math.abs((a & 7) - (b & 7)), Math.abs((a >>> 3) - (b >>> 3)));
    }

    /**
     * Parses a feature CSV (or {@code all}/{@code none}) into a feature set.
     *
     * @param csv feature specification
     * @return parsed feature set
     */
    public static Set<AlphaBeta.Feature> parseFeatures(String csv) {
        String trimmed = csv == null ? "" : csv.trim();
        if (trimmed.equalsIgnoreCase("all")) {
            return EnumSet.allOf(AlphaBeta.Feature.class);
        }
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none")) {
            return EnumSet.noneOf(AlphaBeta.Feature.class);
        }
        List<AlphaBeta.Feature> parsed = new ArrayList<>();
        for (String token : trimmed.split(",")) {
            String name = token.trim();
            if (!name.isEmpty()) {
                parsed.add(AlphaBeta.Feature.valueOf(name));
            }
        }
        return parsed.isEmpty() ? EnumSet.noneOf(AlphaBeta.Feature.class) : EnumSet.copyOf(parsed);
    }
}
