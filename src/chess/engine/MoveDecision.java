package chess.engine;



/**
 * Small immutable state records used by {@link AlphaBeta}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
/**
 * Immutable move-decision inputs used by pruning and reduction heuristics.
 *
 * @param staticEval static evaluation of the node
 * @param alpha current alpha bound
 * @param depth remaining depth
 * @param searchedMoves number of moves already searched
 * @param pvNode true for principal-variation nodes
 * @param inCheck true when the side to move is in check
 * @param tactical true for captures, promotions, and en-passant
 * @param move move being considered
 * @param ply current ply from root
 */
record MoveDecision(
        int staticEval,
        int alpha,
        int depth,
        int searchedMoves,
        boolean pvNode,
        boolean inCheck,
        boolean tactical,
        short move,
        int ply) {
}
