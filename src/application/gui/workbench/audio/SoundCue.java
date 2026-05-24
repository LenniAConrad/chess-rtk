package application.gui.workbench.audio;

/**
 * Short procedural sound cues used by the Swing workbench.
 */
public enum SoundCue {

    /**
     * General button, switch, and menu activation click.
     */
    UI_CLICK,

    /**
     * Position loaded into a board or analysis surface.
     */
    POSITION_LOAD,

    /**
     * Quiet legal non-capture move tick.
     */
    MOVE,

    /**
     * Legal capture tick with a lower transient.
     */
    CAPTURE,

    /**
     * Legal move that leaves the opponent in check.
     */
    CHECK,

    /**
     * Legal castling move.
     */
    CASTLE,

    /**
     * Legal promotion after a promotion choice is committed.
     */
    PROMOTION,

    /**
     * Checkmate, stalemate, or puzzle terminal completion.
     */
    GAME_END,

    /**
     * Invalid move, illegal drop, or snapback.
     */
    ILLEGAL,

    /**
     * Puzzle move accepted but puzzle still has work remaining.
     */
    PUZZLE_CORRECT,

    /**
     * Puzzle move rejected.
     */
    PUZZLE_WRONG,

    /**
     * Puzzle solved.
     */
    PUZZLE_COMPLETE,

    /**
     * Puzzle hint requested.
     */
    HINT,

    /**
     * Puzzle solution reveal requested.
     */
    REVEAL,

    /**
     * User-started workbench command completed successfully.
     */
    JOB_SUCCESS,

    /**
     * User-started workbench command failed.
     */
    JOB_FAILURE,

    /**
     * User-started workbench command was cancelled.
     */
    JOB_CANCELLED,

    /**
     * Monte Carlo search started.
     */
    MCTS_START,

    /**
     * Quiet Monte Carlo search progress tick.
     */
    MCTS_PROGRESS,

    /**
     * Monte Carlo search paused.
     */
    MCTS_PAUSE,

    /**
     * Monte Carlo search resumed.
     */
    MCTS_RESUME,

    /**
     * Monte Carlo search completed normally.
     */
    MCTS_COMPLETE,

    /**
     * Monte Carlo search was stopped by the user.
     */
    MCTS_STOP
}
