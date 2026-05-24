package application.gui.workbench.mcts;

import chess.classical.Wdl;

/**
 * WDL leaf value from the side-to-move perspective.
 */
record SearchEvaluation(double pWin, double pDraw, double pLoss, double value) {

    /**
     * Returns a forced win evaluation.
     *
     * @return win evaluation
     */
    static SearchEvaluation win() {
        return new SearchEvaluation(1.0, 0.0, 0.0, 1.0);
    }

    /**
     * Returns a drawn evaluation.
     *
     * @return draw evaluation
     */
    static SearchEvaluation draw() {
        return new SearchEvaluation(0.0, 1.0, 0.0, 0.0);
    }

    /**
     * Returns a forced loss evaluation.
     *
     * @return loss evaluation
     */
    static SearchEvaluation loss() {
        return new SearchEvaluation(0.0, 0.0, 1.0, -1.0);
    }

    /**
     * Converts a classical WDL triplet to an evaluation.
     *
     * @param wdl source WDL
     * @return evaluation
     */
    static SearchEvaluation fromWdl(Wdl wdl) {
        return new SearchEvaluation(
                wdl.win() / (double) Wdl.TOTAL,
                wdl.draw() / (double) Wdl.TOTAL,
                wdl.loss() / (double) Wdl.TOTAL,
                (wdl.win() - wdl.loss()) / (double) Wdl.TOTAL);
    }

    /**
     * Converts a floating-point WDL triplet to an evaluation.
     *
     * @param wdl source WDL probabilities
     * @return evaluation
     */
    static SearchEvaluation fromWdl(float[] wdl) {
        if (wdl == null || wdl.length < 3) {
            return draw();
        }
        double win = Math.max(0.0, wdl[0]);
        double draw = Math.max(0.0, wdl[1]);
        double loss = Math.max(0.0, wdl[2]);
        double sum = win + draw + loss;
        if (!Double.isFinite(sum) || sum <= 0.0) {
            return draw();
        }
        win /= sum;
        draw /= sum;
        loss /= sum;
        return new SearchEvaluation(win, draw, loss, win - loss);
    }

    /**
     * Returns this value from the opponent perspective.
     *
     * @return flipped evaluation
     */
    SearchEvaluation flipped() {
        return new SearchEvaluation(pLoss, pDraw, pWin, -value);
    }
}
