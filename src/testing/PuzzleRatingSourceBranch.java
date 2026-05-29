package testing;



/**
 * One opponent reply and solver response branch.
 *
 * @param reply opponent reply after the parent solution move
 * @param child solver-to-move child node after the reply
 */
record PuzzleRatingSourceBranch(short reply, PuzzleRatingSourceNode child) {
}
