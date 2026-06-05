package application.gui.workbench;

import application.cli.PathOps;

/**
 * Shared user-facing defaults for the Swing workbench.
 *
 * <p>The values here are intentionally conservative: they keep the first-run
 * experience responsive, avoid silently consuming extra CPU or memory, and
 * provide stable preview strings for generated workbench files.</p>
 */
public final class Defaults {

    /**
     * Default interactive engine-analysis budget.
     */
    public static final String ANALYSIS_DURATION = "1s";

    /**
     * Minimum shared analysis depth.
     */
    public static final int ANALYSIS_DEPTH_MIN = 1;

    /**
     * Default shared analysis depth.
     */
    public static final int ANALYSIS_DEPTH = 4;

    /**
     * Maximum shared analysis depth.
     */
    public static final int ANALYSIS_DEPTH_MAX = 99;

    /**
     * Step size for shared analysis depth controls.
     */
    public static final int ANALYSIS_DEPTH_STEP = 1;

    /**
     * Minimum shared MultiPV count.
     */
    public static final int ANALYSIS_MULTIPV_MIN = 1;

    /**
     * Default shared MultiPV count.
     */
    public static final int ANALYSIS_MULTIPV = 2;

    /**
     * Maximum shared MultiPV count.
     */
    public static final int ANALYSIS_MULTIPV_MAX = 20;

    /**
     * Step size for shared MultiPV controls.
     */
    public static final int ANALYSIS_MULTIPV_STEP = 1;

    /**
     * Minimum shared thread count.
     */
    public static final int ANALYSIS_THREADS_MIN = 1;

    /**
     * Default shared thread count.
     */
    public static final int ANALYSIS_THREADS = 1;

    /**
     * Maximum shared thread count.
     */
    public static final int ANALYSIS_THREADS_MAX = 256;

    /**
     * Step size for shared thread controls.
     */
    public static final int ANALYSIS_THREADS_STEP = 1;

    /**
     * Default MCTS playout/visit budget for interactive panels.
     */
    public static final int MCTS_VISITS = 300;

    /**
     * Minimum MCTS visit budget.
     */
    public static final int MCTS_VISITS_MIN = 1;

    /**
     * Maximum MCTS visit budget.
     */
    public static final int MCTS_VISITS_MAX = 1_000_000;

    /**
     * Step size for MCTS visit controls.
     */
    public static final int MCTS_VISITS_STEP = 50;

    /**
     * Default MCTS wall-clock budget in milliseconds; zero means visit-only.
     */
    public static final int MCTS_MILLIS = 0;

    /**
     * Minimum MCTS wall-clock budget in milliseconds.
     */
    public static final int MCTS_MILLIS_MIN = 0;

    /**
     * Maximum MCTS wall-clock budget in milliseconds.
     */
    public static final int MCTS_MILLIS_MAX = 3_600_000;

    /**
     * Step size for MCTS wall-clock budget controls in milliseconds.
     */
    public static final int MCTS_MILLIS_STEP = 1000;

    /**
     * Default PUCT exploration constant.
     */
    public static final double MCTS_CPUCT = 2.8;

    /**
     * Minimum PUCT exploration constant.
     */
    public static final double MCTS_CPUCT_MIN = 0.05;

    /**
     * Maximum PUCT exploration constant.
     */
    public static final double MCTS_CPUCT_MAX = 8.0;

    /**
     * Step size for PUCT exploration controls.
     */
    public static final double MCTS_CPUCT_STEP = 0.25;

    /**
     * Default state for the Network tab's leaf-following inference toggle.
     */
    public static final boolean NETWORK_MCTS_FOLLOW_LEAF = false;

    /**
     * Placeholder path used in command previews for generated PGN files.
     */
    public static final String WORKBENCH_GAME_PLACEHOLDER = "<dump/workbench-game.pgn>";

    /**
     * Placeholder path used in command previews for generated FEN files.
     */
    public static final String WORKBENCH_FENS_PLACEHOLDER = "<dump/workbench-fens.txt>";

    /**
     * Default opponent strength on the Play tab's Elo slider.
     */
    public static final int PLAY_ELO = 1200;

    /**
     * Tick step for the Play tab's Elo slider.
     */
    public static final int PLAY_ELO_STEP = 50;

    /**
     * Default diagram PDF output path.
     */
    public static final String PUBLISH_DIAGRAMS_OUTPUT = PathOps.dumpPath("workbench-diagrams.pdf").toString();

    /**
     * Default interior PDF output path.
     */
    public static final String PUBLISH_BOOK_OUTPUT = PathOps.dumpPath("workbench-book.pdf").toString();

    /**
     * Default cover PDF output path.
     */
    public static final String PUBLISH_COVER_OUTPUT = PathOps.dumpPath("workbench-cover.pdf").toString();

    /**
     * Default manifest output path.
     */
    public static final String PUBLISH_MANIFEST_OUTPUT = PathOps.dumpPath("workbench-book.toml").toString();

    /**
     * Prevents instantiation.
     */
    private Defaults() {
        // Utility class.
    }
}
