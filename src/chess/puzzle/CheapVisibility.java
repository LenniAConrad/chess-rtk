package chess.puzzle;



/**
 * Cheap-evaluator visibility scores used for feature names.
 */
final class CheapVisibility {
    /**
     * Static evaluation before applying the solution move.
     */
    final int staticCp;

    /**
     * Evaluation after applying the solution move.
     */
    final int solutionCp;

    /**
     * Creates cheap visibility scores.
     *
     * @param staticCp static evaluation
     * @param solutionCp solution evaluation
     */
    CheapVisibility(int staticCp, int solutionCp) {
        this.staticCp = staticCp;
        this.solutionCp = solutionCp;
    }
}
