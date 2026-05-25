package testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chess.core.Move;
import chess.core.Position;

/**
 * One opponent reply and solver response branch.
 *
 * @param reply opponent reply after the parent solution move
 * @param child solver-to-move child node after the reply
 */
record PuzzleRatingSourceBranch(short reply, PuzzleRatingSourceNode child) {
}
