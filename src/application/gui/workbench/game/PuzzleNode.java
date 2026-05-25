package application.gui.workbench.game;

import chess.core.Move;

/**
 * Normalized move-tree node used by the native puzzle trainer.
 *
 * @param id stable node identifier inside one parsed puzzle
 * @param parentId parent node identifier, or {@link #NO_PARENT} for the root
 * @param ply one-based ply number, or zero for the root
 * @param san SAN move text shown to the user
 * @param move CRTK move encoding, or {@link Move#NO_MOVE} for the root
 * @param uci UCI move text, or an empty string for the root
 * @param actor side role that owns this move
 * @param mainline true when this node belongs to the PGN mainline
 * @param siblingOrder stable order among children of the same parent
 * @param fenAfter six-field FEN after this move, or the root FEN
 */
public record PuzzleNode(
        int id,
        int parentId,
        int ply,
        String san,
        short move,
        String uci,
        MoveActor actor,
        boolean mainline,
        int siblingOrder,
        String fenAfter) {

    /**
     * Parent identifier used by the root node.
     */
    public static final int NO_PARENT = 0;

    /**
     * Role assigned to one move in a puzzle tree.
     */
    public enum MoveActor {
        /**
         * Move the solver must enter.
         */
        USER,

        /**
         * Move auto-played by the puzzle trainer.
         */
        OPPONENT
    }

    /**
     * Validates and normalizes node text.
     *
     * @param id stable node identifier
     * @param parentId parent node identifier
     * @param ply one-based ply number, or zero for the root
     * @param san SAN move text
     * @param move CRTK move encoding
     * @param uci UCI move text
     * @param actor side role that owns this move
     * @param mainline true for PGN mainline nodes
     * @param siblingOrder stable child order
     * @param fenAfter FEN after this move
     */
    public PuzzleNode {
        san = san == null ? "" : san;
        uci = uci == null ? "" : uci;
        actor = actor == null ? MoveActor.OPPONENT : actor;
        fenAfter = fenAfter == null ? "" : fenAfter;
    }

    /**
     * Returns whether this node is the root sentinel.
     *
     * @return true when the node has no parent
     */
    public boolean root() {
        return parentId == NO_PARENT;
    }
}
