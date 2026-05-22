/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench;

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
    public static final int MCTS_CPUCT = 1;

    /**
     * Minimum PUCT exploration constant.
     */
    public static final int MCTS_CPUCT_MIN = 1;

    /**
     * Maximum PUCT exploration constant.
     */
    public static final int MCTS_CPUCT_MAX = 5;

    /**
     * Step size for PUCT exploration controls.
     */
    public static final int MCTS_CPUCT_STEP = 1;

    /**
     * Default state for the Network tab's leaf-following inference toggle.
     */
    public static final boolean NETWORK_MCTS_FOLLOW_LEAF = false;

    /**
     * Placeholder path used in command previews for generated PGN files.
     */
    public static final String WORKBENCH_GAME_PLACEHOLDER = "<workbench-game.pgn>";

    /**
     * Placeholder path used in command previews for generated FEN files.
     */
    public static final String WORKBENCH_FENS_PLACEHOLDER = "<workbench-fens.txt>";

    /**
     * Default diagram PDF output path.
     */
    public static final String PUBLISH_DIAGRAMS_OUTPUT = "workbench-diagrams.pdf";

    /**
     * Default interior PDF output path.
     */
    public static final String PUBLISH_BOOK_OUTPUT = "workbench-book.pdf";

    /**
     * Default cover PDF output path.
     */
    public static final String PUBLISH_COVER_OUTPUT = "workbench-cover.pdf";

    /**
     * Default manifest output path.
     */
    public static final String PUBLISH_MANIFEST_OUTPUT = "workbench-book.toml";

    /**
     * Prevents instantiation.
     */
    private Defaults() {
        // Utility class.
    }
}
