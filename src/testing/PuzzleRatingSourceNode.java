package testing;

import java.util.ArrayList;
import java.util.List;

import chess.core.Move;
import chess.core.Position;

/**
 * One solver-to-move node in an explicit puzzle solution tree.
 */
final class PuzzleRatingSourceNode {

    /**
     * Solver-to-move position.
     */
    private final Position position;

    /**
     * Solver solution move.
     */
    private final short solution;

    /**
     * Sortable evaluation score from the record's primary output.
     */
    private final int evalScore;

    /**
     * Opponent-reply branches after this solution move.
     */
    private final List<PuzzleRatingSourceBranch> branches = new ArrayList<>();

    /**
     * Creates a source solution node.
     *
     * @param position solver-to-move position
     * @param solution solver solution move
     * @param evalScore sortable evaluation score
     */
    PuzzleRatingSourceNode(Position position, short solution, int evalScore) {
        this.position = position;
        this.solution = solution;
        this.evalScore = evalScore;
    }

    /**
     * Returns the solver-to-move position.
     *
     * @return solver-to-move position
     */
    Position position() {
        return position;
    }

    /**
     * Returns the solver solution move.
     *
     * @return solver solution move
     */
    short solution() {
        return solution;
    }

    /**
     * Returns the sortable evaluation score.
     *
     * @return sortable evaluation score
     */
    int evalScore() {
        return evalScore;
    }

    /**
     * Returns mutable opponent-reply branches.
     *
     * @return opponent-reply branches
     */
    List<PuzzleRatingSourceBranch> branches() {
        return branches;
    }

    /**
     * Returns the position after the solver solution.
     *
     * @return position after the solution, or {@code null} when invalid
     */
    Position afterSolution() {
        if (position == null || solution == Move.NO_MOVE) {
            return null;
        }
        try {
            return position.copy().play(solution);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Checks whether this node already contains a reply branch.
     *
     * @param reply opponent reply move
     * @return true when the reply is already present
     */
    boolean hasReply(short reply) {
        for (PuzzleRatingSourceBranch branch : branches) {
            if (Move.equals(branch.reply(), reply)) {
                return true;
            }
        }
        return false;
    }
}
