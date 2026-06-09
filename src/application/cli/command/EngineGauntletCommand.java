package application.cli.command;

import static application.cli.Constants.OPT_JSON;
import static application.cli.Validation.requireNonNegative;
import static application.cli.Validation.requirePositive;

import chess.engine.AlphaBeta;
import chess.engine.Gauntlet;
import chess.engine.Gauntlet.Config;
import chess.engine.Gauntlet.Score;
import chess.engine.Gauntlet.SearchKind;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import utility.Argv;

/**
 * Implements the {@code engine gauntlet} self-play A/B comparison command.
 *
 * <p>
 * Pits a candidate engine configuration against a baseline at an equal, fixed
 * per-move budget over a set of varied openings (each played from both colors)
 * and reports the candidate-perspective score plus a point Elo estimate. The
 * run is deterministic for a given seed, so the same flags reproduce the same
 * result. Pass {@code --json} for a single machine-readable summary object.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EngineGauntletCommand {

    /**
     * Candidate alpha-beta feature CSV flag.
     */
    private static final String OPT_FEATURES_A = "--a";

    /**
     * Baseline alpha-beta feature CSV flag.
     */
    private static final String OPT_FEATURES_B = "--b";

    /**
     * Fixed node-budget-per-move flag.
     */
    private static final String OPT_NODES = "--nodes";

    /**
     * Fixed time-budget-per-move flag (milliseconds).
     */
    private static final String OPT_MOVETIME = "--movetime";

    /**
     * Candidate per-move node-budget override flag.
     */
    private static final String OPT_NODES_A = "--nodesA";

    /**
     * Baseline per-move node-budget override flag.
     */
    private static final String OPT_NODES_B = "--nodesB";

    /**
     * Candidate per-move time-budget override flag (milliseconds).
     */
    private static final String OPT_MOVETIME_A = "--movetimeA";

    /**
     * Baseline per-move time-budget override flag (milliseconds).
     */
    private static final String OPT_MOVETIME_B = "--movetimeB";

    /**
     * Candidate external UCI engine command flag.
     */
    private static final String OPT_ENGINE_A = "--engineA";

    /**
     * Baseline external UCI engine command flag.
     */
    private static final String OPT_ENGINE_B = "--engineB";

    /**
     * Candidate external UCI engine hash-size flag (MB).
     */
    private static final String OPT_HASH_A = "--hashA";

    /**
     * Baseline external UCI engine hash-size flag (MB).
     */
    private static final String OPT_HASH_B = "--hashB";

    /**
     * Candidate external UCI engine extra-options flag.
     */
    private static final String OPT_OPTIONS_A = "--optionsA";

    /**
     * Baseline external UCI engine extra-options flag.
     */
    private static final String OPT_OPTIONS_B = "--optionsB";

    /**
     * Per-game machine-readable streaming flag.
     */
    private static final String OPT_STREAM = "--stream";

    /**
     * Shared evaluator flag.
     */
    private static final String OPT_EVAL = "--eval";

    /**
     * Candidate evaluator override flag.
     */
    private static final String OPT_EVAL_A = "--evalA";

    /**
     * Baseline evaluator override flag.
     */
    private static final String OPT_EVAL_B = "--evalB";

    /**
     * Shared search flag.
     */
    private static final String OPT_SEARCH = "--search";

    /**
     * Candidate search override flag.
     */
    private static final String OPT_SEARCH_A = "--searchA";

    /**
     * Baseline search override flag.
     */
    private static final String OPT_SEARCH_B = "--searchB";

    /**
     * Candidate MCTS cpuct flag.
     */
    private static final String OPT_CPUCT_A = "--cpuctA";

    /**
     * Baseline MCTS cpuct flag.
     */
    private static final String OPT_CPUCT_B = "--cpuctB";

    /**
     * Candidate MCTS FPU-reduction flag.
     */
    private static final String OPT_FPU_A = "--fpuA";

    /**
     * Baseline MCTS FPU-reduction flag.
     */
    private static final String OPT_FPU_B = "--fpuB";

    /**
     * Candidate MCTS check-prior-bonus flag.
     */
    private static final String OPT_CHECK_PRIOR_A = "--checkPriorA";

    /**
     * Baseline MCTS check-prior-bonus flag.
     */
    private static final String OPT_CHECK_PRIOR_B = "--checkPriorB";

    /**
     * Candidate MCTS losing-capture prior-penalty flag.
     */
    private static final String OPT_CAPTURE_PENALTY_A = "--capturePenaltyA";

    /**
     * Baseline MCTS losing-capture prior-penalty flag.
     */
    private static final String OPT_CAPTURE_PENALTY_B = "--capturePenaltyB";

    /**
     * Candidate MCTS winning-capture prior-scale flag.
     */
    private static final String OPT_CAPTURE_WIN_SCALE_A = "--captureWinScaleA";

    /**
     * Baseline MCTS winning-capture prior-scale flag.
     */
    private static final String OPT_CAPTURE_WIN_SCALE_B = "--captureWinScaleB";

    /**
     * Draw-adjudication ply-cap flag.
     */
    private static final String OPT_MAX_PLIES = "--maxplies";

    /**
     * Seeded-random opening-count flag.
     */
    private static final String OPT_OPENINGS = "--openings";

    /**
     * Opening RNG-seed flag.
     */
    private static final String OPT_SEED = "--seed";

    /**
     * Candidate search-threads flag.
     */
    private static final String OPT_THREADS_A = "--threadsA";

    /**
     * Baseline search-threads flag.
     */
    private static final String OPT_THREADS_B = "--threadsB";

    /**
     * Concurrent opening-pair worker flag.
     */
    private static final String OPT_WORKERS = "--workers";

    /**
     * Default node budget per move.
     */
    private static final long DEFAULT_NODES = 5_000L;

    /**
     * Default MCTS exploration constant.
     */
    private static final double DEFAULT_CPUCT = 2.8;

    /**
     * Default MCTS first-play-urgency reduction.
     */
    private static final double DEFAULT_FPU = 0.05;

    /**
     * Default MCTS check-prior bonus.
     */
    private static final int DEFAULT_CHECK_PRIOR = 4_000;

    /**
     * Default MCTS losing-capture prior penalty.
     */
    private static final int DEFAULT_CAPTURE_PENALTY = 8_000;

    /**
     * Default MCTS winning-capture prior scale.
     */
    private static final int DEFAULT_CAPTURE_WIN_SCALE = 8;

    /**
     * Default draw-adjudication ply cap.
     */
    private static final int DEFAULT_MAX_PLIES = 240;

    /**
     * Default opening RNG seed.
     */
    private static final long DEFAULT_SEED = 20_260_531L;

    /**
     * Utility class; prevent instantiation.
     */
    private EngineGauntletCommand() {
        // utility
    }

    /**
     * Handles {@code engine gauntlet}.
     *
     * @param a argument parser
     */
    public static void runGauntlet(Argv a) {
        String cmd = "engine gauntlet";
        boolean json = a.flag(OPT_JSON);
        boolean stream = a.flag(OPT_STREAM);
        Config config = parseConfig(a, cmd);
        a.ensureConsumed();

        String[] openings = Gauntlet.openings(config);
        if (!json) {
            printHeader(config, openings.length);
        }
        Gauntlet.ProgressListener listener = json ? jsonListener() : textListener(openings.length);
        if (stream) {
            // Keep JSON stdout pure by routing per-game records to stderr in JSON
            // mode; in text mode they share stdout where the workbench reads them.
            listener = streamListener(listener, json ? System.err : System.out);
        }
        Score score = Gauntlet.run(config, openings, listener);
        if (json) {
            System.out.println(toJson(config, openings.length, score));
        } else {
            printReport(score);
        }
    }

    /**
     * Parses command flags into a gauntlet configuration.
     *
     * @param a argument parser
     * @param cmd command label for diagnostics
     * @return parsed configuration
     */
    private static Config parseConfig(Argv a, String cmd) {
        Set<AlphaBeta.Feature> featuresA = Gauntlet.parseFeatures(orDefault(a.string(OPT_FEATURES_A), "all"));
        Set<AlphaBeta.Feature> featuresB = Gauntlet.parseFeatures(orDefault(a.string(OPT_FEATURES_B), "none"));
        long nodes = a.lngOr(DEFAULT_NODES, OPT_NODES);
        long movetime = a.lngOr(0L, OPT_MOVETIME);
        long nodesA = Math.max(0L, a.lngOr(0L, OPT_NODES_A));
        long nodesB = Math.max(0L, a.lngOr(0L, OPT_NODES_B));
        long movetimeA = Math.max(0L, a.lngOr(0L, OPT_MOVETIME_A));
        long movetimeB = Math.max(0L, a.lngOr(0L, OPT_MOVETIME_B));
        String engineA = orDefault(a.string(OPT_ENGINE_A), "");
        String engineB = orDefault(a.string(OPT_ENGINE_B), "");
        int hashA = Math.max(0, a.integerOr(0, OPT_HASH_A));
        int hashB = Math.max(0, a.integerOr(0, OPT_HASH_B));
        String optionsA = orDefault(a.string(OPT_OPTIONS_A), "");
        String optionsB = orDefault(a.string(OPT_OPTIONS_B), "");
        String eval = orDefault(a.string(OPT_EVAL), "classical");
        String evalA = orDefault(a.string(OPT_EVAL_A), eval);
        String evalB = orDefault(a.string(OPT_EVAL_B), eval);
        SearchKind search = parseSearch(a.string(OPT_SEARCH), "alpha-beta", cmd);
        SearchKind searchA = parseSearch(a.string(OPT_SEARCH_A), search.cliName(), cmd);
        SearchKind searchB = parseSearch(a.string(OPT_SEARCH_B), search.cliName(), cmd);
        double cpuctA = a.dblOr(DEFAULT_CPUCT, OPT_CPUCT_A);
        double cpuctB = a.dblOr(DEFAULT_CPUCT, OPT_CPUCT_B);
        double fpuA = a.dblOr(DEFAULT_FPU, OPT_FPU_A);
        double fpuB = a.dblOr(DEFAULT_FPU, OPT_FPU_B);
        int checkPriorA = a.integerOr(DEFAULT_CHECK_PRIOR, OPT_CHECK_PRIOR_A);
        int checkPriorB = a.integerOr(DEFAULT_CHECK_PRIOR, OPT_CHECK_PRIOR_B);
        int capturePenaltyA = a.integerOr(DEFAULT_CAPTURE_PENALTY, OPT_CAPTURE_PENALTY_A);
        int capturePenaltyB = a.integerOr(DEFAULT_CAPTURE_PENALTY, OPT_CAPTURE_PENALTY_B);
        int captureWinScaleA = a.integerOr(DEFAULT_CAPTURE_WIN_SCALE, OPT_CAPTURE_WIN_SCALE_A);
        int captureWinScaleB = a.integerOr(DEFAULT_CAPTURE_WIN_SCALE, OPT_CAPTURE_WIN_SCALE_B);
        int maxPlies = a.integerOr(DEFAULT_MAX_PLIES, OPT_MAX_PLIES);
        int openingCount = a.integerOr(0, OPT_OPENINGS);
        long seed = a.lngOr(DEFAULT_SEED, OPT_SEED);
        int threadsA = a.integerOr(1, OPT_THREADS_A);
        int threadsB = a.integerOr(1, OPT_THREADS_B);
        int workers = a.integerOr(1, OPT_WORKERS);

        requirePositive(cmd, OPT_MAX_PLIES, maxPlies);
        requireNonNegative(cmd, OPT_OPENINGS, openingCount);
        requirePositive(cmd, OPT_THREADS_A, threadsA);
        requirePositive(cmd, OPT_THREADS_B, threadsB);
        requirePositive(cmd, OPT_WORKERS, workers);
        if (movetime > 0) {
            requirePositive(cmd, OPT_MOVETIME, movetime);
        } else {
            requirePositive(cmd, OPT_NODES, nodes);
        }

        return new Config(featuresA, featuresB, searchA, searchB, evalA, evalB, nodes, movetime,
                nodesA, nodesB, movetimeA, movetimeB, engineA, engineB, hashA, hashB, optionsA, optionsB,
                cpuctA, cpuctB, fpuA, fpuB, checkPriorA, checkPriorB, capturePenaltyA, capturePenaltyB,
                captureWinScaleA, captureWinScaleB, maxPlies, openingCount, seed, threadsA, threadsB, workers);
    }

    /**
     * Parses a search-kind flag with a default and a CLI-friendly diagnostic.
     *
     * @param value flag value, or {@code null} when absent
     * @param fallback default search name
     * @param cmd command label for diagnostics
     * @return parsed search kind
     */
    private static SearchKind parseSearch(String value, String fallback, String cmd) {
        try {
            return SearchKind.parse(orDefault(value, fallback));
        } catch (IllegalArgumentException ex) {
            throw new CommandFailure(cmd + ": " + ex.getMessage() + " (use alpha-beta or mcts)", 2);
        }
    }

    /**
     * Returns a progress listener that streams a human-readable header-less
     * progress line every ten opening pairs.
     *
     * @param total total opening pairs
     * @return text progress listener
     */
    private static Gauntlet.ProgressListener textListener(int total) {
        return new Gauntlet.ProgressListener() {
            /**
             * Prints periodic running score progress.
             *
             * @param completed completed opening pairs
             * @param totalPairs total opening pairs
             * @param running running score
             */
            @Override
            public void onProgress(int completed, int totalPairs, Score running) {
                if (completed % 10 == 0 || completed == total) {
                    System.out.printf(Locale.ROOT, "  [%3d/%3d]  running W-D-L = %d-%d-%d%n",
                            completed, total, running.win(), running.draw(), running.loss());
                }
            }

            /**
             * Prints an informational note.
             *
             * @param message note text
             */
            @Override
            public void onNote(String message) {
                System.out.println("  (" + message + ")");
            }
        };
    }

    /**
     * Wraps a progress listener so each completed game is also emitted as a
     * single tab-separated, machine-readable line for live workbench rendering.
     *
     * <p>
     * The line format is {@code GAME\t<index>\t<W|B>\t<result>\t<openingFen>\t
     * <uci moves space-joined>}. The FEN contains spaces but never tabs, so the
     * fields are unambiguous. {@link java.io.PrintStream#println} is atomic per
     * call, so concurrent worker-thread games never interleave a line.
     * </p>
     *
     * @param base wrapped progress listener
     * @param out destination stream for game records
     * @return delegating listener that also streams game records
     */
    private static Gauntlet.ProgressListener streamListener(Gauntlet.ProgressListener base,
            java.io.PrintStream out) {
        return new Gauntlet.ProgressListener() {
            /**
             * Forwards a progress update.
             *
             * @param completed completed opening pairs
             * @param total total opening pairs
             * @param running running score
             */
            @Override
            public void onProgress(int completed, int total, Score running) {
                base.onProgress(completed, total, running);
            }

            /**
             * Forwards an informational note.
             *
             * @param message note text
             */
            @Override
            public void onNote(String message) {
                base.onNote(message);
            }

            /**
             * Streams one completed game as a tab-separated record.
             *
             * @param record completed game record
             */
            @Override
            public void onGame(Gauntlet.GameRecord record) {
                out.println("GAME\t" + record.index()
                        + "\t" + (record.candidateWhite() ? "W" : "B")
                        + "\t" + record.result()
                        + "\t" + record.openingFen()
                        + "\t" + String.join(" ", record.moves()));
            }
        };
    }

    /**
     * Returns a progress listener that keeps standard output clean for JSON by
     * sending notes to standard error and discarding progress.
     *
     * @return JSON-mode progress listener
     */
    private static Gauntlet.ProgressListener jsonListener() {
        return new Gauntlet.ProgressListener() {
            /**
             * Sends an informational note to standard error.
             *
             * @param message note text
             */
            @Override
            public void onNote(String message) {
                System.err.println(message);
            }
        };
    }

    /**
     * Prints the human-readable run header.
     *
     * @param config gauntlet configuration
     * @param openings resolved opening count
     */
    private static void printHeader(Config config, int openings) {
        System.out.println("Gauntlet (candidate A vs baseline B)");
        System.out.println("  candidate (A) features: " + featuresLabel(config.featuresA()));
        System.out.println("  baseline  (B) features: " + featuresLabel(config.featuresB()));
        System.out.println("  searchA=" + config.searchA().cliName() + " searchB=" + config.searchB().cliName()
                + "  evalA=" + config.evalA() + " evalB=" + config.evalB()
                + "  cpuctA=" + config.cpuctA() + " cpuctB=" + config.cpuctB()
                + "  fpuA=" + config.fpuA() + " fpuB=" + config.fpuB()
                + "  checkPriorA=" + config.checkPriorA() + " checkPriorB=" + config.checkPriorB()
                + "  capturePenaltyA=" + config.capturePenaltyA() + " capturePenaltyB=" + config.capturePenaltyB()
                + "  captureWinScaleA=" + config.captureWinScaleA()
                + " captureWinScaleB=" + config.captureWinScaleB()
                + "  budget=" + config.budgetText()
                + "  threadsA=" + config.threadsA() + " threadsB=" + config.threadsB()
                + "  workers=" + config.workers()
                + "  openings=" + openings + "  games=" + (openings * 2));
    }

    /**
     * Prints the human-readable match result and Elo estimate.
     *
     * @param score aggregate score
     */
    private static void printReport(Score score) {
        System.out.println("----");
        System.out.printf(Locale.ROOT, "Result (candidate A vs baseline B): +%d =%d -%d  of %d games%n",
                score.win(), score.draw(), score.loss(), score.games());
        System.out.printf(Locale.ROOT, "Score: %.1f%%%n", score.fraction() * 100.0);
        if (!score.hasEloEstimate()) {
            System.out.println("Elo: " + (score.fraction() >= 1.0 ? "+inf (no losses)" : "-inf (no wins)"));
        } else {
            // Add 0.0 to normalize a negative-zero estimate to a clean "+0".
            System.out.printf(Locale.ROOT, "Elo estimate: %+.0f%n", score.elo() + 0.0);
        }
    }

    /**
     * Renders the run result as a single JSON summary object.
     *
     * @param config gauntlet configuration
     * @param openings resolved opening count
     * @param score aggregate score
     * @return JSON summary
     */
    private static String toJson(Config config, int openings, Score score) {
        StringBuilder out = new StringBuilder(256);
        out.append('{');
        out.append("\"candidate\":").append(sideJson(config.searchA(), config.evalA(), config.featuresA()));
        out.append(",\"baseline\":").append(sideJson(config.searchB(), config.evalB(), config.featuresB()));
        out.append(",\"openings\":").append(openings);
        out.append(",\"games\":").append(score.games());
        out.append(",\"win\":").append(score.win());
        out.append(",\"draw\":").append(score.draw());
        out.append(",\"loss\":").append(score.loss());
        out.append(",\"score\":").append(String.format(Locale.ROOT, "%.4f", score.fraction()));
        out.append(",\"elo\":");
        if (score.hasEloEstimate()) {
            out.append(String.format(Locale.ROOT, "%.0f", score.elo() + 0.0));
        } else {
            out.append("null");
        }
        out.append('}');
        return out.toString();
    }

    /**
     * Renders one side's configuration as a JSON object.
     *
     * @param search side search kind
     * @param eval side evaluator name
     * @param features side alpha-beta features
     * @return JSON object
     */
    private static String sideJson(SearchKind search, String eval, Set<AlphaBeta.Feature> features) {
        return "{\"search\":" + CommandSupport.jsonString(search.cliName())
                + ",\"eval\":" + CommandSupport.jsonString(eval)
                + ",\"features\":" + CommandSupport.jsonString(featuresLabel(features)) + "}";
    }

    /**
     * Returns a compact feature-set label that round-trips through the parser.
     *
     * @param features alpha-beta features
     * @return {@code all}, {@code none}, or a comma-separated name list
     */
    private static String featuresLabel(Set<AlphaBeta.Feature> features) {
        if (features.isEmpty()) {
            return "none";
        }
        if (features.equals(EnumSet.allOf(AlphaBeta.Feature.class))) {
            return "all";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (AlphaBeta.Feature feature : features) {
            joiner.add(feature.name());
        }
        return joiner.toString();
    }

    /**
     * Returns the value when present, otherwise a fallback.
     *
     * @param value candidate value
     * @param fallback fallback value
     * @return non-null resolved value
     */
    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
