package chess.engine;

/**
 * Pentanomial sequential probability ratio test (SPRT) for paired-game matches.
 *
 * <p>
 * Tracks the result of each opening pair (the same opening played once from
 * each color) in the five pentanomial outcome categories indexed by the
 * candidate's pair score in half points: {@code 0} (two losses), {@code 1}
 * (loss plus draw), {@code 2} (two draws or a win plus a loss), {@code 3}
 * (win plus draw), and {@code 4} (two wins). Modelling pairs instead of single
 * games absorbs the correlation between the two games of a pair, which a
 * trinomial model overstates.
 * </p>
 *
 * <p>
 * The log-likelihood ratio uses the standard one-line GSPRT approximation on
 * the pentanomial frequencies, as popularized by fishtest and OpenBench:
 * {@code LLR = N * (s1 - s0) * (2 * mean - s0 - s1) / (2 * variance)}, where
 * {@code mean} and {@code variance} are the empirical mean and variance of the
 * normalized pair score and {@code s0}/{@code s1} are the expected scores under
 * the two hypotheses. The Elo bounds are converted to expected scores with the
 * plain logistic model {@code s = 1 / (1 + 10^(-elo / 400))} (not fishtest's
 * normalized Elo), so {@code elo0}/{@code elo1} are ordinary logistic Elo.
 * Every category is regularized by adding one half pair so empty categories
 * never zero the variance. The test accepts H1 ({@code elo >= elo1}) when the
 * LLR reaches {@code log((1 - beta) / alpha)} and H0 ({@code elo <= elo0})
 * when it falls to {@code log(beta / (1 - alpha))}; the first crossing latches
 * the decision even if later pairs move the LLR back inside the bounds.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Sprt {

    /**
     * Decision state of a sequential test.
     */
    public enum Status {

        /**
         * Neither stopping bound has been crossed; keep playing.
         */
        CONTINUE,

        /**
         * The lower bound was crossed: H0 ({@code elo <= elo0}) is accepted.
         */
        ACCEPT_H0,

        /**
         * The upper bound was crossed: H1 ({@code elo >= elo1}) is accepted.
         */
        ACCEPT_H1
    }

    /**
     * Immutable snapshot of the test state after some number of pairs.
     *
     * @param losses pairs the candidate lost both games of (score 0)
     * @param halfLosses pairs scoring one half point (loss plus draw)
     * @param evens pairs scoring one point (two draws, or a win and a loss)
     * @param halfWins pairs scoring one and a half points (win plus draw)
     * @param wins pairs the candidate won both games of (score 2)
     * @param llr current log-likelihood ratio
     * @param lowerBound H0-acceptance bound {@code log(beta / (1 - alpha))}
     * @param upperBound H1-acceptance bound {@code log((1 - beta) / alpha)}
     * @param elo regularized logistic point Elo estimate
     * @param status latched test decision
     * @param decisionLlr LLR captured at the bound crossing that latched the
     *        decision, or {@code NaN} while the test continues; unlike
     *        {@link #llr} it is not moved by in-flight pairs counted after the
     *        decision
     */
    public record Snapshot(long losses, long halfLosses, long evens, long halfWins, long wins,
            double llr, double lowerBound, double upperBound, double elo, Status status,
            double decisionLlr) {

        /**
         * Returns the number of counted opening pairs.
         *
         * @return counted pair count
         */
        public long pairs() {
            return losses + halfLosses + evens + halfWins + wins;
        }

        /**
         * Returns the number of games in the counted pairs.
         *
         * @return counted game count
         */
        public long games() {
            return pairs() * 2L;
        }
    }

    /**
     * Number of pentanomial outcome categories (pair scores 0 to 2 in half
     * points).
     */
    private static final int CATEGORIES = 5;

    /**
     * Pairs added to each category before computing frequencies, so empty
     * categories neither zero the variance nor produce infinite estimates.
     */
    private static final double REGULARIZER = 0.5;

    /**
     * H0 hypothesis Elo (candidate is at most this much stronger).
     */
    private final double elo0;

    /**
     * H1 hypothesis Elo (candidate is at least this much stronger).
     */
    private final double elo1;

    /**
     * H0-acceptance bound {@code log(beta / (1 - alpha))}.
     */
    private final double lowerBound;

    /**
     * H1-acceptance bound {@code log((1 - beta) / alpha)}.
     */
    private final double upperBound;

    /**
     * Pentanomial pair counts indexed by candidate pair score in half points.
     */
    private final long[] counts = new long[CATEGORIES];

    /**
     * Latched test decision; set by the first bound crossing.
     */
    private Status status = Status.CONTINUE;

    /**
     * LLR at the bound crossing that latched {@link #status}, or {@code NaN}
     * while the test continues. Reported by verdicts so in-flight pairs
     * counted after the decision cannot make the printed LLR contradict the
     * crossed bound.
     */
    private double decisionLlr = Double.NaN;

    /**
     * Creates a sequential test of H0 ({@code elo <= elo0}) against H1
     * ({@code elo >= elo1}) at the supplied error rates.
     *
     * @param elo0 H0 hypothesis Elo, strictly below {@code elo1}
     * @param elo1 H1 hypothesis Elo, strictly above {@code elo0}
     * @param alpha type-I error rate (false H1 acceptance), in {@code (0, 1)}
     * @param beta type-II error rate (false H0 acceptance), in {@code (0, 1)}
     * @throws IllegalArgumentException for unordered Elo bounds or error rates
     *                                  outside {@code (0, 1)}
     */
    public Sprt(double elo0, double elo1, double alpha, double beta) {
        if (!(elo1 > elo0)) {
            throw new IllegalArgumentException("sprt requires elo1 > elo0, got " + elo0 + ":" + elo1);
        }
        if (!(alpha > 0.0 && alpha < 1.0) || !(beta > 0.0 && beta < 1.0)) {
            throw new IllegalArgumentException("sprt error rates must be in (0, 1)");
        }
        this.elo0 = elo0;
        this.elo1 = elo1;
        this.lowerBound = lowerBound(alpha, beta);
        this.upperBound = upperBound(alpha, beta);
    }

    /**
     * Records one completed opening pair and, while no decision is latched,
     * re-evaluates the stopping bounds. Pairs added after a decision still
     * update the counts (in-flight games are allowed to finish and report) but
     * can no longer change the decision.
     *
     * @param pairScoreHalfPoints candidate pair score in half points
     *                            ({@code 0} to {@code 4})
     * @throws IllegalArgumentException for a score outside {@code 0..4}
     */
    public void addPair(int pairScoreHalfPoints) {
        if (pairScoreHalfPoints < 0 || pairScoreHalfPoints >= CATEGORIES) {
            throw new IllegalArgumentException("pair score must be 0..4 half points, got "
                    + pairScoreHalfPoints);
        }
        counts[pairScoreHalfPoints]++;
        if (status == Status.CONTINUE) {
            double value = llr();
            if (value >= upperBound) {
                status = Status.ACCEPT_H1;
                decisionLlr = value;
            } else if (value <= lowerBound) {
                status = Status.ACCEPT_H0;
                decisionLlr = value;
            }
        }
    }

    /**
     * Returns the current log-likelihood ratio over the counted pairs.
     *
     * @return current LLR
     */
    public double llr() {
        return llr(counts, elo0, elo1);
    }

    /**
     * Returns the latched test decision.
     *
     * @return test decision
     */
    public Status status() {
        return status;
    }

    /**
     * Returns an immutable snapshot of the current test state.
     *
     * @return state snapshot
     */
    public Snapshot snapshot() {
        return new Snapshot(counts[0], counts[1], counts[2], counts[3], counts[4],
                llr(), lowerBound, upperBound, eloEstimate(counts), status, decisionLlr);
    }

    /**
     * Maps the two candidate-perspective game scores of an opening pair to the
     * pentanomial category index, which equals the pair score in half points.
     *
     * @param firstHalfPoints first-game score in half points ({@code 0} loss,
     *                        {@code 1} draw, {@code 2} win)
     * @param secondHalfPoints second-game score in half points
     * @return pentanomial category index in {@code 0..4}
     * @throws IllegalArgumentException for a game score outside {@code 0..2}
     */
    public static int pairCategory(int firstHalfPoints, int secondHalfPoints) {
        if (firstHalfPoints < 0 || firstHalfPoints > 2 || secondHalfPoints < 0 || secondHalfPoints > 2) {
            throw new IllegalArgumentException("game scores must be 0..2 half points, got "
                    + firstHalfPoints + " and " + secondHalfPoints);
        }
        return firstHalfPoints + secondHalfPoints;
    }

    /**
     * Returns the expected score for an Elo difference under the plain
     * logistic model.
     *
     * @param elo Elo difference from the candidate's perspective
     * @return expected score in {@code (0, 1)}
     */
    public static double logisticScore(double elo) {
        return 1.0 / (1.0 + Math.pow(10.0, -elo / 400.0));
    }

    /**
     * Computes the GSPRT log-likelihood ratio approximation on regularized
     * pentanomial counts.
     *
     * <p>
     * Each category receives an extra half pair, pair scores
     * are normalized to {@code [0, 1]} (category index divided by four), and
     * the LLR is {@code N * (s1 - s0) * (2 * mean - s0 - s1) / (2 * variance)}
     * with {@code s0}/{@code s1} the logistic expected scores of the two
     * hypothesis Elos.
     * </p>
     *
     * @param counts five pentanomial pair counts indexed by half-point score
     * @param elo0 H0 hypothesis Elo
     * @param elo1 H1 hypothesis Elo
     * @return log-likelihood ratio
     * @throws IllegalArgumentException unless exactly five counts are supplied
     */
    public static double llr(long[] counts, double elo0, double elo1) {
        if (counts == null || counts.length != CATEGORIES) {
            throw new IllegalArgumentException("expected " + CATEGORIES + " pentanomial counts");
        }
        double total = 0.0;
        double mean = 0.0;
        for (int category = 0; category < CATEGORIES; category++) {
            double regularized = counts[category] + REGULARIZER;
            total += regularized;
            mean += regularized * score(category);
        }
        mean /= total;
        double variance = 0.0;
        for (int category = 0; category < CATEGORIES; category++) {
            double deviation = score(category) - mean;
            variance += (counts[category] + REGULARIZER) * deviation * deviation;
        }
        variance /= total;
        double s0 = logisticScore(elo0);
        double s1 = logisticScore(elo1);
        return total * (s1 - s0) * (2.0 * mean - s0 - s1) / (2.0 * variance);
    }

    /**
     * Returns the H0-acceptance bound for the supplied error rates.
     *
     * @param alpha type-I error rate
     * @param beta type-II error rate
     * @return lower stopping bound {@code log(beta / (1 - alpha))}
     */
    public static double lowerBound(double alpha, double beta) {
        return Math.log(beta / (1.0 - alpha));
    }

    /**
     * Returns the H1-acceptance bound for the supplied error rates.
     *
     * @param alpha type-I error rate
     * @param beta type-II error rate
     * @return upper stopping bound {@code log((1 - beta) / alpha)}
     */
    public static double upperBound(double alpha, double beta) {
        return Math.log((1.0 - beta) / alpha);
    }

    /**
     * Returns the logistic point Elo estimate from regularized pentanomial
     * counts. The regularizer keeps the mean strictly inside {@code (0, 1)},
     * so the estimate is always finite.
     *
     * @param counts five pentanomial pair counts indexed by half-point score
     * @return point Elo estimate from the candidate's perspective
     * @throws IllegalArgumentException unless exactly five counts are supplied
     */
    public static double eloEstimate(long[] counts) {
        if (counts == null || counts.length != CATEGORIES) {
            throw new IllegalArgumentException("expected " + CATEGORIES + " pentanomial counts");
        }
        double total = 0.0;
        double mean = 0.0;
        for (int category = 0; category < CATEGORIES; category++) {
            double regularized = counts[category] + REGULARIZER;
            total += regularized;
            mean += regularized * score(category);
        }
        mean /= total;
        return -400.0 * Math.log10(1.0 / mean - 1.0);
    }

    /**
     * Returns the normalized pair score of a pentanomial category.
     *
     * @param category category index in {@code 0..4}
     * @return pair score normalized to {@code [0, 1]}
     */
    private static double score(int category) {
        return category / 4.0;
    }
}
