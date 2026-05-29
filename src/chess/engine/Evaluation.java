package chess.engine;


import chess.classical.Wdl;

/**
 * Tree node and value objects used by {@link Mcts}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
/**
 * WDL leaf value from side-to-move perspective.
 */
record Evaluation(double pWin, double pDraw, double pLoss, double value) {

    /**
     * Returns a drawn evaluation.
     *
     * @return draw evaluation
     */
    static Evaluation draw() {
        return new Evaluation(0.0, 1.0, 0.0, 0.0);
    }

    /**
     * Returns a forced win evaluation.
     *
     * @return win evaluation
     */
    static Evaluation win() {
        return new Evaluation(1.0, 0.0, 0.0, 1.0);
    }

    /**
     * Returns a forced loss evaluation.
     *
     * @return loss evaluation
     */
    static Evaluation loss() {
        return new Evaluation(0.0, 0.0, 1.0, -1.0);
    }

    /**
     * Converts a classical WDL triplet to an evaluation.
     *
     * @param wdl source WDL
     * @return evaluation
     */
    static Evaluation fromWdl(Wdl wdl) {
        return new Evaluation(
                wdl.win() / (double) Wdl.TOTAL,
                wdl.draw() / (double) Wdl.TOTAL,
                wdl.loss() / (double) Wdl.TOTAL,
                (wdl.win() - wdl.loss()) / (double) Wdl.TOTAL);
    }

    /**
     * Converts floating-point WDL probabilities to an evaluation.
     *
     * @param wdl source WDL probabilities
     * @return normalized evaluation
     */
    static Evaluation fromWdl(float[] wdl) {
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
        return new Evaluation(win, draw, loss, win - loss);
    }

    /**
     * Converts centipawns to a soft WDL evaluation.
     *
     * @param centipawns centipawn score from side to move
     * @return evaluation
     */
    static Evaluation fromCentipawns(int centipawns) {
        double value = Math.tanh(Math.max(-2000, Math.min(2000, centipawns)) / 600.0);
        double draw = Math.max(0.0, 0.35 - Math.abs(value) * 0.25);
        double win = (1.0 - draw) * (0.5 + value * 0.5);
        double loss = Math.max(0.0, 1.0 - draw - win);
        return new Evaluation(win, draw, loss, value);
    }

    /**
     * Returns this value from the opponent perspective.
     *
     * @return flipped evaluation
     */
    Evaluation flipped() {
        return new Evaluation(pLoss, pDraw, pWin, -value);
    }
}
