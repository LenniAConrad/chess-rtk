package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Parsed tier tag with side, piece, and square labels.
 */
final class ParsedPieceTier {

    /**
     * The tier label.
     */
    final String tier;

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
     * Creates a parsed piece tier record.
     *
     * @param tier   the tier label
     * @param side   the side label
     * @param piece  the piece label
     * @param square the square label
     */
    ParsedPieceTier(String tier, String side, String piece, String square) {
        this.tier = tier;
        this.side = side;
        this.piece = piece;
        this.square = square;
    }
}
