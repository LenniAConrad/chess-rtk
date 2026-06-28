package chess.review;

import java.util.Objects;

/**
 * Engine-free mistake classifier for game-review rows.
 *
 * <p>The classifier consumes scores that are already expressed from the mover's
 * perspective. Positive centipawns and larger win-share values are good for the
 * player who made the move; lower post-move values therefore represent a loss.
 * No engine process, board state, PGN parsing, or tag generation happens here,
 * which keeps the false-positive guards deterministic and easy to regression
 * test.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Classifier {

    /**
     * Win-share above which a position is treated as still clearly winning.
     */
    private static final double WINNING_SHARE = 0.90d;

    /**
     * Win-share below which a position is treated as still clearly losing.
     */
    private static final double LOSING_SHARE = 0.10d;

    /**
     * Prevents instantiation.
     */
    private Classifier() {
        // utility
    }

    /**
     * Classifies a move result using the classical threshold profile.
     *
     * @param before score before the played move
     * @param after  score after the played move
     * @return classification verdict
     */
    public static Verdict classify(Score before, Score after) {
        return classify(new Request(before, after, null, Thresholds.classical(), false));
    }

    /**
     * Classifies a move result using explicit context.
     *
     * @param request classifier request
     * @return classification verdict
     */
    public static Verdict classify(Request request) {
        Objects.requireNonNull(request, "request");
        Score before = request.before();
        Score after = request.after();
        Thresholds thresholds = request.thresholds();
        int cpLoss = Math.max(0, before.centipawns() - after.centipawns());
        Double wdlLoss = wdlLoss(before, after);
        boolean deadDraw = isDeadDraw(before, after, thresholds);
        boolean onlyMovePosition = isOnlyMovePosition(before, request.secondBest(), thresholds);
        boolean alreadyDecided = isAlreadyDecided(before, after, thresholds);

        Category category = deadDraw ? Category.OK : categoryForCpLoss(cpLoss, thresholds);
        boolean theorySuppressed = false;
        if (!deadDraw) {
            category = applyWdlGate(category, cpLoss, wdlLoss, thresholds);
            if (request.theoryPosition() && cpLoss < thresholds.blunderCp()) {
                theorySuppressed = category != Category.OK;
                category = Category.OK;
            }
            if (alreadyDecided) {
                category = category.downgrade();
            }
        }

        return new Verdict(
                category,
                cpLoss,
                wdlLoss,
                severity(cpLoss, wdlLoss, thresholds),
                onlyMovePosition,
                deadDraw,
                alreadyDecided,
                theorySuppressed);
    }

    /**
     * Computes WDL win-share loss when both scores include it.
     *
     * @param before pre-move score
     * @param after  post-move score
     * @return loss, or {@code null} if either side lacks WDL
     */
    private static Double wdlLoss(Score before, Score after) {
        if (before.winShare() == null || after.winShare() == null) {
            return null;
        }
        return Math.max(0.0d, before.winShare() - after.winShare());
    }

    /**
     * Returns the threshold category implied by centipawn loss alone.
     *
     * @param cpLoss     non-negative centipawn loss
     * @param thresholds active thresholds
     * @return base category
     */
    private static Category categoryForCpLoss(int cpLoss, Thresholds thresholds) {
        if (cpLoss < thresholds.inaccuracyCp()) {
            return Category.OK;
        }
        if (cpLoss < thresholds.mistakeCp()) {
            return Category.INACCURACY;
        }
        if (cpLoss < thresholds.blunderCp()) {
            return Category.MISTAKE;
        }
        return Category.BLUNDER;
    }

    /**
     * Applies WDL escalation and blunder gating.
     *
     * @param category   centipawn-derived category
     * @param cpLoss     centipawn loss
     * @param wdlLoss    WDL loss, or null
     * @param thresholds active thresholds
     * @return adjusted category
     */
    private static Category applyWdlGate(
            Category category,
            int cpLoss,
            Double wdlLoss,
            Thresholds thresholds) {
        if (wdlLoss == null) {
            return category;
        }
        if (category == Category.BLUNDER && wdlLoss < thresholds.blunderWdlLoss()) {
            return Category.MISTAKE;
        }
        if (category == Category.INACCURACY && wdlLoss >= thresholds.mistakeWdlLoss()) {
            return Category.MISTAKE;
        }
        if (category == Category.MISTAKE
                && cpLoss >= thresholds.blunderCp()
                && wdlLoss >= thresholds.blunderWdlLoss()) {
            return Category.BLUNDER;
        }
        return category;
    }

    /**
     * Returns whether a loss happened in a dead-draw band.
     *
     * @param before pre-move score
     * @param after post-move score
     * @param thresholds active thresholds
     * @return true when both scores stay inside the draw band
     */
    private static boolean isDeadDraw(Score before, Score after, Thresholds thresholds) {
        return Math.abs(before.centipawns()) <= thresholds.drawBandCp()
                && Math.abs(after.centipawns()) <= thresholds.drawBandCp();
    }

    /**
     * Returns whether the best move is much better than the second-best move.
     *
     * @param before pre-move best score
     * @param secondBest second-best score, or null
     * @param thresholds active thresholds
     * @return true when the position appears to have one clearly best move
     */
    private static boolean isOnlyMovePosition(Score before, Score secondBest, Thresholds thresholds) {
        return secondBest != null
                && before.centipawns() - secondBest.centipawns() >= thresholds.onlyMoveGapCp();
    }

    /**
     * Returns whether the before and after scores remain in a decided band.
     *
     * @param before pre-move score
     * @param after post-move score
     * @param thresholds active thresholds
     * @return true when the result band is unchanged and already decided
     */
    private static boolean isAlreadyDecided(Score before, Score after, Thresholds thresholds) {
        if (Math.abs(before.centipawns()) < thresholds.decidedCp()) {
            return false;
        }
        if (before.winShare() != null && after.winShare() != null) {
            return (before.winShare() >= WINNING_SHARE && after.winShare() >= WINNING_SHARE)
                    || (before.winShare() <= LOSING_SHARE && after.winShare() <= LOSING_SHARE);
        }
        return sameSign(before.centipawns(), after.centipawns())
                && Math.abs(after.centipawns()) >= thresholds.decidedCp();
    }

    /**
     * Returns whether two centipawn scores have the same non-zero sign.
     *
     * @param left first centipawn score
     * @param right second centipawn score
     * @return true when signs match
     */
    private static boolean sameSign(int left, int right) {
        return (left > 0 && right > 0) || (left < 0 && right < 0);
    }

    /**
     * Computes deterministic normalized severity.
     *
     * @param cpLoss non-negative centipawn loss
     * @param wdlLoss WDL loss, or null
     * @param thresholds active thresholds
     * @return severity in {@code [0,1]}
     */
    private static double severity(int cpLoss, Double wdlLoss, Thresholds thresholds) {
        double cpShare = clamp01((double) cpLoss / thresholds.blunderReferenceCp());
        if (wdlLoss == null) {
            return cpShare;
        }
        return 0.5d * cpShare + 0.5d * clamp01(wdlLoss);
    }

    /**
     * Clamps a value to the unit interval.
     *
     * @param value source value
     * @return clamped value
     */
    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    /**
     * Review threshold profile.
     */
    public enum Profile {

        /**
         * Classical/default game-review thresholds.
         */
        CLASSICAL,

        /**
         * Slightly wider thresholds for rapid games.
         */
        RAPID,

        /**
         * Wider thresholds for blitz games.
         */
        BLITZ
    }

    /**
     * Mistake classification category.
     */
    public enum Category {

        /**
         * No actionable mistake.
         */
        OK,

        /**
         * Small but notable loss.
         */
        INACCURACY,

        /**
         * Clear mistake.
         */
        MISTAKE,

        /**
         * Severe mistake.
         */
        BLUNDER;

        /**
         * Returns a stable JSON-friendly label.
         *
         * @return lower-case label
         */
        public String label() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /**
         * Downgrades this category by one severity level.
         *
         * @return downgraded category
         */
        Category downgrade() {
            return switch (this) {
                case BLUNDER -> MISTAKE;
                case MISTAKE -> INACCURACY;
                case INACCURACY, OK -> OK;
            };
        }
    }

    /**
     * Score from the mover's perspective.
     *
     * @param centipawns centipawn score, positive for the mover
     * @param winShare optional WDL win share in {@code [0,1]}
     */
    public record Score(int centipawns, Double winShare) {

        /**
         * Creates and validates a score.
         *
         * @param centipawns centipawn score
         * @param winShare win-share expectation
         */
        public Score {
            if (winShare != null && (winShare < 0.0d || winShare > 1.0d || winShare.isNaN())) {
                throw new IllegalArgumentException("winShare must be in [0,1]");
            }
        }

        /**
         * Creates a centipawn-only score.
         *
         * @param centipawns centipawn score
         * @return score
         */
        public static Score centipawns(int centipawns) {
            return new Score(centipawns, null);
        }

        /**
         * Creates a score with WDL win-share context.
         *
         * @param centipawns centipawn score
         * @param winShare WDL win share from the mover's perspective
         * @return score
         */
        public static Score withWinShare(int centipawns, double winShare) {
            return new Score(centipawns, winShare);
        }
    }

    /**
     * Immutable classifier thresholds.
     *
     * @param inaccuracyCp centipawn loss for an inaccuracy
     * @param mistakeCp centipawn loss for a mistake
     * @param blunderCp centipawn loss for a blunder
     * @param mistakeWdlLoss WDL loss that can escalate an inaccuracy
     * @param blunderWdlLoss WDL loss required for WDL-backed blunders
     * @param decidedCp already-decided centipawn band
     * @param drawBandCp dead-draw centipawn band
     * @param onlyMoveGapCp best-to-second-best gap that marks only-move positions
     * @param blunderReferenceCp centipawn denominator for severity
     */
    public record Thresholds(
            int inaccuracyCp,
            int mistakeCp,
            int blunderCp,
            double mistakeWdlLoss,
            double blunderWdlLoss,
            int decidedCp,
            int drawBandCp,
            int onlyMoveGapCp,
            int blunderReferenceCp) {

        /**
         * Creates and validates thresholds.
         *
         * @param inaccuracyCp centipawn loss threshold for inaccuracies
         * @param mistakeCp centipawn loss threshold for mistakes
         * @param blunderCp centipawn loss threshold for blunders
         * @param mistakeWdlLoss WDL loss threshold for mistakes
         * @param blunderWdlLoss WDL loss threshold for blunders
         * @param decidedCp centipawn threshold for decided positions
         * @param drawBandCp centipawn band treated as drawish
         * @param onlyMoveGapCp centipawn gap used to detect only moves
         * @param blunderReferenceCp centipawn reference threshold for blunders
         */
        public Thresholds {
            if (inaccuracyCp <= 0 || mistakeCp <= inaccuracyCp || blunderCp <= mistakeCp) {
                throw new IllegalArgumentException("centipawn thresholds must be increasing");
            }
            if (mistakeWdlLoss < 0.0d || blunderWdlLoss <= mistakeWdlLoss || blunderWdlLoss > 1.0d) {
                throw new IllegalArgumentException("WDL thresholds must be increasing in [0,1]");
            }
            if (decidedCp <= 0 || drawBandCp < 0 || onlyMoveGapCp <= 0 || blunderReferenceCp <= 0) {
                throw new IllegalArgumentException("guard thresholds must be positive");
            }
        }

        /**
         * Returns thresholds for a named profile.
         *
         * @param profile threshold profile
         * @return thresholds
         */
        public static Thresholds forProfile(Profile profile) {
            Objects.requireNonNull(profile, "profile");
            return switch (profile) {
                case CLASSICAL -> new Thresholds(50, 120, 300, 0.20d, 0.30d, 600, 30, 150, 300);
                case RAPID -> new Thresholds(60, 140, 350, 0.20d, 0.30d, 600, 30, 150, 350);
                case BLITZ -> new Thresholds(80, 180, 400, 0.20d, 0.30d, 600, 30, 150, 400);
            };
        }

        /**
         * Returns the default classical thresholds.
         *
         * @return classical thresholds
         */
        public static Thresholds classical() {
            return forProfile(Profile.CLASSICAL);
        }

        /**
         * Returns blitz thresholds.
         *
         * @return blitz thresholds
         */
        public static Thresholds blitz() {
            return forProfile(Profile.BLITZ);
        }
    }

    /**
     * Input request for one classification.
     *
     * @param before best score before the played move
     * @param after score after the played move
     * @param secondBest optional second-best score before the played move
     * @param thresholds active thresholds
     * @param theoryPosition whether the ply is still inside known theory
     */
    public record Request(
            Score before,
            Score after,
            Score secondBest,
            Thresholds thresholds,
            boolean theoryPosition) {

        /**
         * Creates and normalizes a request.
         *
         * @param before position before the move
         * @param after position after the move
         * @param secondBest second-best move assessment
         * @param thresholds review classification thresholds
         * @param theoryPosition whether the position is still in opening theory
         */
        public Request {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            thresholds = thresholds == null ? Thresholds.classical() : thresholds;
        }
    }

    /**
     * Classification result for one played move.
     *
     * @param category final mistake category
     * @param cpLoss non-negative centipawn loss
     * @param wdlLoss non-negative WDL loss, or null
     * @param severity normalized severity in {@code [0,1]}
     * @param onlyMovePosition whether the best move was far above second-best
     * @param deadDraw whether dead-draw suppression fired
     * @param alreadyDecided whether already-decided downgrade applied
     * @param theorySuppressed whether theory suppression fired
     */
    public record Verdict(
            Category category,
            int cpLoss,
            Double wdlLoss,
            double severity,
            boolean onlyMovePosition,
            boolean deadDraw,
            boolean alreadyDecided,
            boolean theorySuppressed) {
    }
}
