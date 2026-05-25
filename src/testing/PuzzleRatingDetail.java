package testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chess.core.Move;
import chess.core.Position;

/**
 * Render-ready puzzle snapshot move text.
 *
 * @param movetext move-numbered SAN text
 * @param sourceTree whether the text came from an original source tree
 */
record PuzzleRatingDetail(String movetext, boolean sourceTree) {
}
