package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Captures king-safety properties while folding multiple tokens together.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
final class KingSafety {

    /**
     * Whether castling status was observed.
     */
    Boolean castled;

    /**
     * Whether the pawn shield appears weakened.
     */
    boolean shieldWeakened;

    /**
     * Whether the king appears exposed.
     */
    boolean exposed;

    /**
     * Whether an open file is near the king.
     */
    boolean openFile;
}
