package application.gui.workbench.play;

import application.gui.workbench.play.Opponent.RankedMove;
import chess.core.Move;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Maps a {@link StrengthProfile} to a concrete search budget and selects the
 * move actually played from an opponent's ranked candidates.
 *
 * <p>
 * This is the one calibratable place for opponent strength. It is pure and has
 * no Swing dependency, so it can be unit-tested headlessly. Two layers:
 * </p>
 * <ul>
 * <li><b>Layer A — budget:</b> {@link #budgetFor(StrengthProfile)} turns a
 * target Elo into a playout cap, a wall-clock cap, and a {@code cpuct}. The
 * wall-clock cap guarantees every search terminates promptly.</li>
 * <li><b>Layer B — selection:</b> {@link #select(List, StrengthProfile, Random)}
 * samples a move from the ranked candidates with a temperature, a top-k window,
 * a value cutoff, and an occasional larger "blunder" window — all derived from
 * Elo. The cutoffs are measured in the search's own value units (mean action
 * value {@code q} in roughly [-1, 1]) so a weakened engine drifts into inferior
 * but not instantly-losing moves, never into a hung mate or a stalemate-when-
 * winning. At maximum strength (or when the profile is deterministic) selection
 * collapses to the arg-max move.</li>
 * </ul>
 *
 * <p>
 * The Elo numbers are approximate seed values, not calibrated ratings; the
 * mapping is monotonic and controllable. Calibration against a reference ladder
 * is future work.
 * </p>
 */
public final class StrengthModel {

    /**
     * Lowest supported target Elo.
     */
    public static final int MIN_ELO = 400;

    /**
     * Highest supported target Elo; at or above this the engine plays arg-max.
     */
    public static final int MAX_ELO = 2800;

    /**
     * Playout budget at minimum strength. Even the weakest setting searches a
     * little so the engine is purposeful, not random.
     */
    public static final int MIN_PLAYOUTS = 64;

    /**
     * Playout budget at maximum strength. The classical-eval MCTS needs several
     * thousand playouts to play soundly; this gives a genuinely strong opponent
     * (~4-5s per move on typical hardware).
     */
    public static final int MAX_PLAYOUTS = 25_000;

    /**
     * Playout-budget multiplier for the slower policy/value networks (CNN, OTIS),
     * whose per-playout cost is much higher than the classical/NNUE evaluators.
     * Keeps a move responsive at a given Elo; conservative by design and always
     * floored by {@link #MIN_PLAYOUTS}.
     */
    private static final double SLOW_NET_PLAYOUT_SCALE = 0.30;

    /**
     * Safety floor for the per-move wall-clock cap. The playout budget is the
     * real strength lever; at weak settings it binds long before this.
     */
    private static final long MIN_MILLIS = 1_000L;

    /**
     * Safety ceiling for the per-move wall-clock cap. Generous enough that the
     * playout budget — not the clock — decides strength on normal hardware, so
     * a given Elo plays the same regardless of CPU speed. The cap only guards
     * against a pathologically slow position hanging the UI.
     */
    private static final long MAX_MILLIS = 20_000L;

    /**
     * Exploration constant at maximum strength.
     */
    private static final double CPUCT_STRONG = 1.1;

    /**
     * Additional exploration applied at minimum strength.
     */
    private static final double CPUCT_WEAK_BONUS = 0.7;

    /**
     * Sampling temperature at minimum strength (before the linear falloff).
     */
    private static final double MAX_TEMPERATURE = 1.7;

    /**
     * Largest per-move blunder probability, at minimum strength.
     */
    private static final double MAX_BLUNDER = 0.22;

    /**
     * Largest value cutoff (in q units) tolerated at minimum strength.
     */
    private static final double MAX_VALUE_CUTOFF = 0.5;

    /**
     * Smallest value cutoff (in q units), applied near maximum strength.
     */
    private static final double MIN_VALUE_CUTOFF = 0.05;

    /**
     * Hard ceiling on the widened blunder cutoff so a weak engine never walks
     * into an outright lost position when a playable one exists.
     */
    private static final double MAX_BLUNDER_CUTOFF = 0.6;

    /**
     * Floor applied to the temperature divisor so weights stay finite.
     */
    private static final double MIN_TEMPERATURE_DIVISOR = 0.05;

    /**
     * Lowest iterative-deepening depth for the alpha-beta opponent.
     */
    public static final int MIN_DEPTH = 1;

    /**
     * Highest iterative-deepening depth for the alpha-beta opponent. Deep enough
     * to be a genuinely strong club opponent without long thinks.
     */
    public static final int MAX_DEPTH = 18;

    /**
     * Concrete per-move search budget shared by all in-process Play opponents.
     * Each backend reads the fields it needs: MCTS uses
     * {@code maxPlayouts}/{@code cpuct}, and alpha-beta uses {@code depth} plus
     * the wall-clock cap. {@code targetElo} is retained for calibration and
     * future backend mappings.
     *
     * @param maxPlayouts MCTS playout cap; always positive so search terminates
     * @param maxMillis wall-clock cap in milliseconds; always positive
     * @param cpuct PUCT exploration constant
     * @param depth iterative-deepening depth for the alpha-beta engine
     * @param targetElo requested Elo, for engines with native strength limiting
     */
    public record Budget(int maxPlayouts, long maxMillis, double cpuct, int depth, int targetElo) {
    }

    /**
     * Resolved move-sampling parameters for one strength profile.
     *
     * @param temperature softmax temperature over candidate weights; 0 = arg-max
     * @param topK maximum number of candidates considered
     * @param valueCutoff max q gap below the best move a candidate may have
     * @param blunderCutoff widened gap used on a blunder roll
     * @param blunderProbability per-move chance of using the widened window
     */
    public record Sampling(double temperature, int topK, double valueCutoff,
            double blunderCutoff, double blunderProbability) {
    }

    /**
     * Derives a search budget from a strength profile.
     *
     * @param profile strength profile
     * @return concrete budget with positive caps
     */
    public Budget budgetFor(StrengthProfile profile) {
        int elo = clamp(profile.targetElo(), MIN_ELO, MAX_ELO);
        double fraction = fraction(elo);

        int playouts;
        if (profile.nodesOverride() != null) {
            playouts = Math.max(1, profile.nodesOverride());
        } else {
            // Geometric interpolation from MIN_PLAYOUTS at the floor to
            // MAX_PLAYOUTS at the ceiling, so strength rises smoothly across the
            // whole slider instead of saturating far too low.
            double geometric = MIN_PLAYOUTS
                    * Math.pow((double) MAX_PLAYOUTS / MIN_PLAYOUTS, fraction);
            playouts = (int) clampLong(Math.round(geometric), MIN_PLAYOUTS, MAX_PLAYOUTS);
        }

        long millis;
        if (profile.movetimeOverride() != null) {
            millis = Math.max(1L, profile.movetimeOverride().longValue());
        } else {
            millis = Math.round(MIN_MILLIS + fraction * (MAX_MILLIS - MIN_MILLIS));
        }

        double cpuct = CPUCT_STRONG + CPUCT_WEAK_BONUS * (1.0 - fraction);
        int depth = (int) clampLong(Math.round(MIN_DEPTH + fraction * (MAX_DEPTH - MIN_DEPTH)),
                MIN_DEPTH, MAX_DEPTH);
        return new Budget(playouts, millis, cpuct, depth, elo);
    }

    /**
     * Derives a search budget for a specific evaluation network, scaling the MCTS
     * playout cap by the network's per-playout cost so a slow policy/value net
     * (CNN, OTIS) stays as responsive as the cheap classical/NNUE evaluators at
     * the same Elo. Only {@code maxPlayouts} is scaled — the wall-clock cap, depth,
     * cpuct and target Elo are unchanged — and an explicit node override is never
     * re-scaled (the expert pin wins). The result is still clamped to
     * {@link #MIN_PLAYOUTS}, preserving mate/draw correctness at any setting.
     *
     * <p>
     * The scale affects only the MCTS opponent, which reads {@code maxPlayouts};
     * the alpha-beta opponent ignores it (it is depth/time-bounded).
     * </p>
     *
     * @param profile strength profile
     * @param network evaluation network the search will use
     * @return concrete budget, with playouts scaled for the network's cost
     */
    public Budget budgetFor(StrengthProfile profile, Opponent.Network network) {
        Budget base = budgetFor(profile);
        if (profile.nodesOverride() != null) {
            return base;
        }
        double factor = responsivenessFactor(network);
        if (factor >= 1.0) {
            return base;
        }
        int scaled = (int) clampLong(Math.round(base.maxPlayouts() * factor), MIN_PLAYOUTS, MAX_PLAYOUTS);
        return new Budget(scaled, base.maxMillis(), base.cpuct(), base.depth(), base.targetElo());
    }

    /**
     * Returns the playout-budget scale for a network, approximating its
     * per-playout cost relative to the classical evaluator. The fast evaluators
     * keep their full budget; the heavier policy/value nets get proportionally
     * fewer playouts so a move still returns promptly. These are measured
     * approximations, not exact ratios; the wall-clock cap remains the hard bound.
     *
     * @param network evaluation network
     * @return multiplier in (0, 1]
     */
    private static double responsivenessFactor(Opponent.Network network) {
        return switch (network) {
            case CNN, OTIS -> SLOW_NET_PLAYOUT_SCALE;
            default -> 1.0;
        };
    }

    /**
     * Maps a target Elo to a per-move think time, scaling linearly between the
     * given bounds across the supported Elo range. Shared by the search-based
     * opponents so depth stays the strength ceiling while a wall-clock governor
     * keeps moves responsive.
     *
     * @param targetElo requested Elo
     * @param minMillis think time at minimum strength
     * @param maxMillis think time at maximum strength
     * @return per-move think time in milliseconds
     */
    public static long thinkMillis(int targetElo, long minMillis, long maxMillis) {
        int elo = Math.max(MIN_ELO, Math.min(MAX_ELO, targetElo));
        double fraction = fraction(elo);
        return Math.round(minMillis + fraction * (maxMillis - minMillis));
    }

    /**
     * Resolves the move-sampling parameters for a profile, honoring any expert
     * overrides and clamping the rest off the Elo curve.
     *
     * @param profile strength profile
     * @return resolved sampling parameters
     */
    public Sampling samplingFor(StrengthProfile profile) {
        int elo = clamp(profile.targetElo(), MIN_ELO, MAX_ELO);
        double weakness = 1.0 - fraction(elo);

        double temperature = profile.temperatureOverride() != null
                ? Math.max(0.0, profile.temperatureOverride())
                : clampDouble(MAX_TEMPERATURE * weakness - 0.15, 0.0, MAX_TEMPERATURE);
        int topK = profile.topKOverride() != null
                ? Math.max(1, profile.topKOverride())
                : (int) clampLong(Math.round(2 + 8 * weakness), 2, 64);
        double blunder = profile.blunderOverride() != null
                ? clampDouble(profile.blunderOverride(), 0.0, 1.0)
                : clampDouble(MAX_BLUNDER * weakness - 0.02, 0.0, MAX_BLUNDER);
        double valueCutoff = clampDouble(MIN_VALUE_CUTOFF + (MAX_VALUE_CUTOFF - MIN_VALUE_CUTOFF) * weakness,
                MIN_VALUE_CUTOFF, MAX_VALUE_CUTOFF);
        double blunderCutoff = Math.min(MAX_BLUNDER_CUTOFF, valueCutoff * 2.5);
        return new Sampling(temperature, topK, valueCutoff, blunderCutoff, blunder);
    }

    /**
     * Selects the move to play from ranked candidates.
     *
     * <p>
     * Candidates arrive best-first (the opponent pins the authoritative best
     * move to the front). A deterministic profile, a zero-temperature/zero-
     * blunder Elo, or a single candidate all collapse to that best move.
     * Otherwise a weighted-random move is drawn from the value-bounded top-k
     * window, occasionally widened by a blunder roll.
     * </p>
     *
     * @param ranked ranked candidates, best first; may be empty
     * @param profile strength profile
     * @param rng random source
     * @return chosen move, or {@link Move#NO_MOVE} when there are none
     */
    public short select(List<RankedMove> ranked, StrengthProfile profile, Random rng) {
        if (ranked == null || ranked.isEmpty()) {
            return Move.NO_MOVE;
        }
        short best = ranked.get(0).move();
        if (profile.deterministic() || ranked.size() == 1) {
            return best;
        }
        Sampling sampling = samplingFor(profile);
        if (sampling.temperature() <= 1e-6 && sampling.blunderProbability() <= 0.0) {
            return best;
        }

        double bestQ = ranked.get(0).q();
        for (RankedMove move : ranked) {
            bestQ = Math.max(bestQ, move.q());
        }
        boolean blunder = rng.nextDouble() < sampling.blunderProbability();
        double cutoff = blunder ? sampling.blunderCutoff() : sampling.valueCutoff();

        List<RankedMove> pool = new ArrayList<>();
        for (RankedMove move : ranked) {
            if (bestQ - move.q() <= cutoff + 1e-9) {
                pool.add(move);
                if (pool.size() >= sampling.topK()) {
                    break;
                }
            }
        }
        if (pool.size() <= 1) {
            return best;
        }

        // At tiny budgets visit counts are nearly uniform; lean on the policy
        // priors so the opening still shows variety. Otherwise weight by visits.
        int maxVisits = 0;
        for (RankedMove move : pool) {
            maxVisits = Math.max(maxVisits, move.visits());
        }
        boolean useVisits = maxVisits > 1;
        double invTemperature = 1.0 / Math.max(sampling.temperature(), MIN_TEMPERATURE_DIVISOR);

        double[] weights = new double[pool.size()];
        double sum = 0.0;
        for (int i = 0; i < pool.size(); i++) {
            double base = useVisits ? Math.max(1, pool.get(i).visits()) : Math.max(pool.get(i).prior(), 1e-6);
            double weight = Math.pow(base, invTemperature);
            weights[i] = weight;
            sum += weight;
        }
        if (!(sum > 0.0) || !Double.isFinite(sum)) {
            return best;
        }

        double target = rng.nextDouble() * sum;
        double running = 0.0;
        for (int i = 0; i < pool.size(); i++) {
            running += weights[i];
            if (target <= running) {
                return pool.get(i).move();
            }
        }
        return pool.get(pool.size() - 1).move();
    }

    /**
     * Returns the 0..1 strength fraction for an Elo within the supported range.
     *
     * @param elo clamped Elo
     * @return strength fraction
     */
    private static double fraction(int elo) {
        return (double) (elo - MIN_ELO) / (MAX_ELO - MIN_ELO);
    }

    /**
     * Clamps an integer to a range.
     *
     * @param value value
     * @param min minimum
     * @param max maximum
     * @return clamped value
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps a long to a range.
     *
     * @param value value
     * @param min minimum
     * @param max maximum
     * @return clamped value
     */
    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps a double to a range.
     *
     * @param value value
     * @param min minimum
     * @param max maximum
     * @return clamped value
     */
    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
