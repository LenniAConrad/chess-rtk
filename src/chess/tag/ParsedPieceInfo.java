package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Holds the parsed side, piece, and square components for piece activity tags.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
final class ParsedPieceInfo {

    /**
     * The side label.
     */
    final String side;

    /**
     * The piece label.
     */
    final String piece;

    /**
     * The square label.
     */
    final String square;

    /**
     * Creates a parsed piece-info record.
     *
     * @param side   the side label
     * @param piece  the piece label
     * @param square the square label
     */
    ParsedPieceInfo(String side, String piece, String square) {
        this.side = side;
        this.piece = piece;
        this.square = square;
    }
}
