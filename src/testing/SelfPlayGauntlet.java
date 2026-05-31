package testing;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Result;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;
import chess.eval.Nnue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Self-play A/B harness for the built-in {@link AlphaBeta} engine.
 *
 * <p>
 * Pits a candidate feature configuration against a baseline at an equal,
 * fixed per-move budget (node count by default, or wall-clock time) so that a
 * search change's strength effect is <em>measured</em> by game results rather
 * than asserted. Both engines run in one process against an embedded set of
 * varied opening positions, each played from both colors. The score and a
 * point Elo estimate are printed from the candidate's perspective.
 * </p>
 *
 * <p>
 * Usage: {@code java -cp out testing.SelfPlayGauntlet [flags]}
 * </p>
 * <ul>
 * <li>{@code --a CSV} candidate features (default {@code all})</li>
 * <li>{@code --b CSV} baseline features (default {@code none})</li>
 * <li>{@code --nodes N} fixed node budget per move (default 5000)</li>
 * <li>{@code --movetime MS} fixed time budget per move (overrides --nodes)</li>
 * <li>{@code --eval classical|nnue} evaluator (default classical)</li>
 * <li>{@code --maxplies N} adjudicate a draw past this many plies (default 240)</li>
 * </ul>
 * <p>
 * A CSV is a comma-separated list of {@link AlphaBeta.Feature} names, or the
 * shorthands {@code all} / {@code none}.
 * </p>
 */
public final class SelfPlayGauntlet {

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
        LOSS
    }

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
     * Path to the bundled NNUE network, for {@code --eval nnue}.
     */
    private static final String NNUE_PATH = "models/crtk-halfkp.nnue";

    /**
     * Prevents instantiation.
     */
    private SelfPlayGauntlet() {
    }

    /**
     * Runs the gauntlet.
     *
     * @param args optional flags (see class docs)
     */
    public static void main(String[] args) {
        Map<String, String> opts = parseArgs(args);
        Set<AlphaBeta.Feature> featuresA = parseFeatures(opts.getOrDefault("a", "all"));
        Set<AlphaBeta.Feature> featuresB = parseFeatures(opts.getOrDefault("b", "none"));
        long nodes = Long.parseLong(opts.getOrDefault("nodes", "5000"));
        long movetime = Long.parseLong(opts.getOrDefault("movetime", "0"));
        String eval = opts.getOrDefault("eval", "classical");
        int maxPlies = Integer.parseInt(opts.getOrDefault("maxplies", "240"));
        int openingCount = Integer.parseInt(opts.getOrDefault("openings", "0"));
        long seed = Long.parseLong(opts.getOrDefault("seed", "20260531"));
        int threadsA = Integer.parseInt(opts.getOrDefault("threadsA", "1"));
        int threadsB = Integer.parseInt(opts.getOrDefault("threadsB", "1"));

        // Either the curated set, or a larger seeded-random set for a tighter
        // (reproducible) Elo estimate.
        String[] openings = openingCount > 0
                ? generateOpenings(openingCount, seed)
                : OPENINGS;

        Limits limits = movetime > 0
                ? new Limits(AlphaBeta.MAX_DEPTH, 0L, movetime)
                : new Limits(AlphaBeta.MAX_DEPTH, nodes, 0L);

        System.out.println("SelfPlayGauntlet");
        System.out.println("  candidate (A) features: " + featuresA);
        System.out.println("  baseline  (B) features: " + featuresB);
        System.out.println("  eval=" + eval
                + "  budget=" + (movetime > 0 ? movetime + "ms/move" : nodes + " nodes/move")
                + "  threadsA=" + threadsA + " threadsB=" + threadsB
                + "  openings=" + openings.length + "  games=" + (openings.length * 2));

        int win = 0;
        int draw = 0;
        int loss = 0;
        for (int i = 0; i < openings.length; i++) {
            // Play each opening from both colors so a result is not just one
            // deterministic game counted twice.
            Outcome first = playGame(openings[i], true, featuresA, featuresB, eval, limits, maxPlies, threadsA, threadsB);
            Outcome second = playGame(openings[i], false, featuresA, featuresB, eval, limits, maxPlies, threadsA, threadsB);
            for (Outcome o : new Outcome[] { first, second }) {
                switch (o) {
                    case WIN -> win++;
                    case DRAW -> draw++;
                    case LOSS -> loss++;
                    default -> { }
                }
            }
            if ((i + 1) % 10 == 0 || i + 1 == openings.length) {
                System.out.printf("  [%3d/%3d]  running W-D-L = %d-%d-%d%n",
                        i + 1, openings.length, win, draw, loss);
            }
        }
        report(win, draw, loss);
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
        java.util.Random rng = new java.util.Random(seed);
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
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
     * @param fen opening FEN
     * @param candidateIsWhite whether the candidate plays white this game
     * @param featuresA candidate features
     * @param featuresB baseline features
     * @param eval evaluator name
     * @param limits per-move search limits
     * @param maxPlies draw-adjudication ply cap
     * @return game outcome from the candidate's perspective
     */
    private static Outcome playGame(
            String fen,
            boolean candidateIsWhite,
            Set<AlphaBeta.Feature> featuresA,
            Set<AlphaBeta.Feature> featuresB,
            String eval,
            Limits limits,
            int maxPlies,
            int threadsA,
            int threadsB) {
        Position position = new Position(fen);
        AlphaBeta candidate = new AlphaBeta(evaluator(eval), true, featuresA).setSearchThreads(threadsA);
        AlphaBeta baseline = new AlphaBeta(evaluator(eval), true, featuresB).setSearchThreads(threadsB);
        Map<Long, Integer> seen = new HashMap<>();
        try {
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
                AlphaBeta engine = candidateToMove ? candidate : baseline;
                Result result = engine.search(position, limits);
                short move = result.bestMove();
                if (move == Move.NO_MOVE || !isLegal(legal, move)) {
                    // Defensive: a stuck engine forfeits.
                    return candidateToMove ? Outcome.LOSS : Outcome.WIN;
                }
                position.play(move);
                countPosition(seen, position);
            }
            return Outcome.DRAW;
        } finally {
            candidate.close();
            baseline.close();
        }
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
     * Builds a fresh evaluator, falling back to classical when a net cannot load.
     *
     * @param eval evaluator name
     * @return evaluator instance
     */
    private static CentipawnEvaluator evaluator(String eval) {
        if ("nnue".equalsIgnoreCase(eval)) {
            try {
                return new Nnue(Path.of(NNUE_PATH));
            } catch (RuntimeException | java.io.IOException ex) {
                System.out.println("  (nnue unavailable: " + ex.getMessage() + " — using classical)");
            }
        }
        return new Classical();
    }

    /**
     * Prints the match result and a point Elo estimate.
     *
     * @param win candidate wins
     * @param draw draws
     * @param loss candidate losses
     */
    private static void report(int win, int draw, int loss) {
        int games = win + draw + loss;
        double score = games == 0 ? 0.0 : (win + 0.5 * draw) / games;
        System.out.println("----");
        System.out.printf("Result (candidate A vs baseline B): +%d =%d -%d  of %d games%n",
                win, draw, loss, games);
        System.out.printf("Score: %.1f%%%n", score * 100.0);
        if (score <= 0.0 || score >= 1.0) {
            System.out.println("Elo: " + (score >= 1.0 ? "+inf (no losses)" : "-inf (no wins)"));
        } else {
            double elo = -400.0 * Math.log10(1.0 / score - 1.0);
            System.out.printf("Elo estimate: %+.0f%n", elo);
        }
    }

    /**
     * Parses {@code --key value} flags into a map.
     *
     * @param args raw arguments
     * @return option map
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
     * Parses a feature CSV (or {@code all}/{@code none}) into a feature set.
     *
     * @param csv feature specification
     * @return parsed feature set
     */
    private static Set<AlphaBeta.Feature> parseFeatures(String csv) {
        String trimmed = csv.trim();
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
