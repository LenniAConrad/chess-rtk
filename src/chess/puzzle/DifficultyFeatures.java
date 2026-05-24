package chess.puzzle;

import java.util.List;

/**
 * Feature vector used by the puzzle difficulty scorer.
 *
 * <p>
 * The vector is intended for diagnostics and export metadata. It keeps both the
 * deep-engine truth signals and the cheaper one-ply visibility signals that
 * explain why the scorer placed a puzzle where it did.
 * </p>
 *
 * <p>
 * <strong>Warning:</strong> most values are from the root side-to-move
 * perspective. Consumers should not mix them with engine scores reported from
 * White's perspective without converting signs first.
 * </p>
 *
 * @param goal inferred puzzle objective
 * @param rawScore direct raw score after feature-derived fine spreading
 * @param solutionMoveUci root solution move in UCI notation
 * @param deepBestCp deep PV1 score from the root side to move
 * @param deepSecondCp deep PV2 score from the root side to move, if available
 * @param deepMarginCp PV1 minus PV2 in centipawns, if available
 * @param cheapStaticCp classical static score from the root side to move
 * @param cheapBestMoveUci one-ply classical preferred move in UCI notation
 * @param cheapBestCp one-ply classical best score from the root side to move
 * @param cheapSolutionCp one-ply classical score for the solution move
 * @param solutionRankByCheap one-ply classical rank for the solution move
 * @param legalMoveCount number of legal root moves
 * @param solutionPlies number of plies in the main PV
 * @param variationCount number of distinct root MultiPV/record candidate moves
 * @param recordVariationCount number of distinct parent-child records in the extracted puzzle tree
 * @param branchPointCount number of parent positions with multiple distinct children
 * @param keyCheck whether the key gives check
 * @param keyMate whether the key gives mate
 * @param keyCapture whether the key captures
 * @param keyPromotion whether the key promotes
 * @param keyUnderpromotion whether the key underpromotes
 * @param featureNames human-readable contributing feature names
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public record DifficultyFeatures(
        Goal goal,
        double rawScore,
        String solutionMoveUci,
        int deepBestCp,
        Integer deepSecondCp,
        Integer deepMarginCp,
        int cheapStaticCp,
        String cheapBestMoveUci,
        int cheapBestCp,
        int cheapSolutionCp,
        int solutionRankByCheap,
        int legalMoveCount,
        int solutionPlies,
        int variationCount,
        int recordVariationCount,
        int branchPointCount,
        boolean keyCheck,
        boolean keyMate,
        boolean keyCapture,
        boolean keyPromotion,
        boolean keyUnderpromotion,
        List<String> featureNames) {

    /**
     * Normalizes mutable list input.
     *
     * <p>
     * Scalar values are clamped or defaulted only where needed for stable export.
     * The feature-name collection is copied so later caller-side list changes
     * cannot mutate this record.
     * </p>
     *
     * <p>
     * <strong>Warning:</strong> centipawn fields are preserved exactly as supplied
     * by the scorer. Mate scores may already have been converted to large
     * centipawn-like sentinel values.
     * </p>
     *
     * @param goal inferred puzzle objective
     * @param rawScore direct raw score after feature-derived fine spreading
     * @param solutionMoveUci root solution move in UCI notation
     * @param deepBestCp deep PV1 score from the root side to move
     * @param deepSecondCp deep PV2 score from the root side to move, if available
     * @param deepMarginCp PV1 minus PV2 in centipawns, if available
     * @param cheapStaticCp classical static score from the root side to move
     * @param cheapBestMoveUci one-ply classical preferred move in UCI notation
     * @param cheapBestCp one-ply classical best score from the root side to move
     * @param cheapSolutionCp one-ply classical score for the solution move
     * @param solutionRankByCheap one-ply classical rank for the solution move
     * @param legalMoveCount number of legal root moves
     * @param solutionPlies number of plies in the main PV
     * @param variationCount number of distinct root MultiPV/record candidate moves
     * @param recordVariationCount number of distinct parent-child records in the extracted puzzle tree
     * @param branchPointCount number of parent positions with multiple distinct children
     * @param keyCheck whether the key gives check
     * @param keyMate whether the key gives mate
     * @param keyCapture whether the key captures
     * @param keyPromotion whether the key promotes
     * @param keyUnderpromotion whether the key underpromotes
     * @param featureNames human-readable contributing feature names
     */
    public DifficultyFeatures {
        goal = goal == null ? Goal.UNKNOWN : goal;
        rawScore = Math.max(0.0, Math.min(1.0, rawScore));
        solutionMoveUci = solutionMoveUci == null ? "0000" : solutionMoveUci;
        cheapBestMoveUci = cheapBestMoveUci == null ? "0000" : cheapBestMoveUci;
        featureNames = featureNames == null ? List.of() : List.copyOf(featureNames);
    }
}
