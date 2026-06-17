package application.gui.workbench.engine;

/**
 * One running candidate-perspective W-D-L point in a gauntlet.
 *
 * @param wins candidate wins
 * @param draws draws
 * @param losses candidate losses
 */
record GauntletProgressPoint(int wins, int draws, int losses) {

    /**
     * Returns games observed.
     *
     * @return game count
     */
    int games() {
        return wins + draws + losses;
    }

    /**
     * Returns candidate score fraction.
     *
     * @return score fraction
     */
    double score() {
        int games = games();
        return games <= 0 ? 0.5d : (wins + draws * 0.5d) / games;
    }
}
