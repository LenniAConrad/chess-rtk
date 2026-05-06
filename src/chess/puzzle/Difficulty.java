package chess.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Final puzzle difficulty estimate.
 *
 * <p>
 * The record combines the direct scalar score, the exported rating, the coarse
 * label, and the feature vector that explains the estimate.
 * </p>
 *
 * <p>
 * <strong>Warning:</strong> the rating is Elo-like only inside the puzzle
 * corpus. It should not be presented as a player strength estimate.
 * </p>
 *
 * @param goal inferred puzzle objective
 * @param score normalized difficulty score in {@code [0,1]}
 * @param rating direct Elo-like rating
 * @param label coarse difficulty label
 * @param features contributing feature vector
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public record Difficulty(
        Goal goal,
        double score,
        int rating,
        String label,
        DifficultyFeatures features) {

    /**
     * Normalizes constructor values.
     *
     * <p>
     * Null goals are downgraded to {@link Goal#UNKNOWN}; scores and ratings are
     * clamped to keep persisted metadata valid even when an upstream caller
     * provides partial data.
     * </p>
     *
     * <p>
     * <strong>Warning:</strong> normalization preserves the original feature
     * vector reference. Callers that need immutable feature content should use
     * {@link DifficultyFeatures}, which copies its feature-name list.
     * </p>
     *
     * @param goal inferred puzzle objective
     * @param score normalized difficulty score in {@code [0,1]}
     * @param rating direct Elo-like rating
     * @param label coarse difficulty label
     * @param features contributing feature vector
     */
    public Difficulty {
        goal = goal == null ? Goal.UNKNOWN : goal;
        score = Math.max(0.0, Math.min(1.0, score));
        rating = Math.max(100, rating);
        label = label == null || label.isBlank() ? "medium" : label;
    }

    /**
     * Converts this estimate to canonical META tags.
     *
     * <p>
     * The returned list is immutable and safe to attach to exported puzzle
     * records. Optional feature tags are omitted when no feature vector is
     * present.
     * </p>
     *
     * @return puzzle difficulty tags
     */
    public List<String> tags() {
        List<String> tags = new ArrayList<>(8);
        tags.add("META: puzzle_goal=" + goal.label());
        tags.add("META: puzzle_rating=" + rating);
        tags.add("META: puzzle_difficulty=" + label);
        tags.add("META: puzzle_difficulty_score=" + String.format(Locale.ROOT, "%.2f", score));
        if (features != null) {
            tags.add("META: puzzle_variations=" + features.variationCount());
            tags.add("META: puzzle_branch_points=" + features.branchPointCount());
            tags.add("META: puzzle_solution_plies=" + features.solutionPlies());
            if (!features.featureNames().isEmpty()) {
                tags.add("META: puzzle_features=\"" + String.join(",", features.featureNames()) + "\"");
            }
        }
        return List.copyOf(tags);
    }
}
