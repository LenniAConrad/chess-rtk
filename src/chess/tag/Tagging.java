package chess.tag;

import chess.core.Position;

/**
 * Used for tagging {@code Position} with standard chess tags.
 *
 * <p>
 * Extend this class later.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public class Tagging {

    // Prevent instantiation
    private Tagging() {
        // non-instantiable
    }

    /**
     * Used for generating standard tags for a given position.
     * 
     * @param position the position to generate tags for
     * @return an array of standard tags
     */
    public static String[] positionalTags(Position parent, Position position) {
        String pieceamount = parent.countTotalPieces() + " parent total pieces, "+ position.countTotalPieces() + " pieces";
        return new String[] { pieceamount };
    }
}
