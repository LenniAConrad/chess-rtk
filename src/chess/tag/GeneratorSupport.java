package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Parsing and label helpers for {@link Generator}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class GeneratorSupport {

    /**
     * Minimum move-count gap used to report mobility outliers.
     */
    private static final int MOBILITY_OUTLIER_THRESHOLD = 6;

    /**
     * Maximum legal-move count treated as restricted mobility.
     */
    private static final int MOBILITY_RESTRICTED_THRESHOLD = 5;

    /**
     * Utility class; prevent instantiation.
     */
    private GeneratorSupport() {
        // utility
    }

    /**
     * Returns text after a key inside a serialized tag.
     *
     * @param tag serialized tag text
     * @param key key marker
     * @return trailing text, or {@code null} when the key is absent
     */
    static String valueAfter(String tag, String key) {
        int idx = tag.indexOf(key);
        if (idx < 0) {
            return null;
        }
        return tag.substring(idx + key.length()).trim();
    }

    /**
     * Extracts the text between the first pair of quotes.
     *
     * @param tag the tag text to inspect
     * @return the quoted text, or {@code null} when no quoted segment exists
     */
    static String extractQuoted(String tag) {
        int first = tag.indexOf(QUOTE);
        if (first < 0) {
            return null;
        }
        int second = tag.indexOf(QUOTE, first + 1);
        if (second < 0) {
            return null;
        }
        return tag.substring(first + 1, second);
    }

    /**
     * Escapes backslashes and quotes for tag output.
     *
     * @param value the raw text to escape
     * @return the escaped text
     */
    static String escape(String value) {
        return value.replace(String.valueOf(BACKSLASH), ESCAPED_BACKSLASH)
                .replace(String.valueOf(QUOTE), ESCAPED_QUOTE);
    }

/**
 * Appends an optional field to a serialized tag buffer.
 *
 * @param sb    the output buffer
 * @param label the field label to append
 * @param value the field value, if present
 */
static void appendIfPresent(StringBuilder sb, String label, String value) {
    if (value != null) {
        sb.append(SPACE_TEXT).append(label).append(EQUAL_SIGN).append(value);
    }
}

/**
 * Parses whitespace-separated key-value pairs into a map.
 *
 * @param text the text to parse
 * @return the parsed key-value map
 */
static Map<String, String> parseKeyValues(String text) {
    Map<String, String> map = new LinkedHashMap<>();
    if (text == null || text.isBlank()) {
        return map;
    }
    String[] tokens = text.split(SPACE_REGEX);
    for (String token : tokens) {
        int eq = token.indexOf(EQUAL_SIGN);
        if (eq <= 0) {
            continue;
        }
        String key = token.substring(0, eq).trim();
        String value = token.substring(eq + 1).trim();
        if (!key.isEmpty() && !value.isEmpty()) {
            map.put(key, value);
        }
    }
    return map;
}

/**
 * Parses a tier-style piece tag into a structured record.
 *
 * @param tag the tag text to parse
 * @return the parsed tier record, or {@code null} if the text does not match
 */
static ParsedPieceTier parseTierTag(String tag) {
    if (tag == null || tag.isBlank()) {
        return null;
    }
    String lowered = tag.toLowerCase();
    ParsedPrefix prefix = matchTierPrefix(lowered);
    if (prefix == null) {
        return null;
    }
    String[] parts = prefix.remainder.split(SPACE_REGEX);
    if (parts.length < 3) {
        return null;
    }
    if (!isSideLabel(parts[0])) {
        return null;
    }
    if (isSquareLabel(parts[1]) && isPieceLabel(parts[2])) {
        return new ParsedPieceTier(prefix.value, parts[0], parts[2], parts[1]);
    }
    if (isPieceLabel(parts[1]) && isSquareLabel(parts[2])) {
        return new ParsedPieceTier(prefix.value, parts[0], parts[1], parts[2]);
    }
    return null;
}

/**
 * Matches a tier prefix in lowercase text.
 *
 * @param lowered the lowercase text to inspect
 * @return the matched prefix, or {@code null} if none matches
 */
static ParsedPrefix matchTierPrefix(String lowered) {
    ParsedPrefix prefix = findPrefix(lowered,
            new ParsedPrefix(VERY_STRONG_TEXT, VERY_STRONG),
            new ParsedPrefix(STRONG, STRONG),
            new ParsedPrefix(SLIGHTLY_STRONG_TEXT, SLIGHTLY_STRONG),
            new ParsedPrefix(NEUTRAL, NEUTRAL),
            new ParsedPrefix(SLIGHTLY_WEAK_TEXT, SLIGHTLY_WEAK),
            new ParsedPrefix(VERY_WEAK_TEXT, VERY_WEAK),
            new ParsedPrefix(WEAK, WEAK));
    if (prefix == null) {
        return null;
    }
    return new ParsedPrefix(prefix.key, prefix.value, lowered.substring(prefix.key.length()).trim());
}

/**
 * Finds the first matching prefix and returns its associated value.
 *
 * @param text     the text to inspect
 * @param prefixes the candidate prefixes
 * @return the matched prefix value, or {@code null} if none matches
 */
static String startsWithAny(String text, ParsedPrefix... prefixes) {
    ParsedPrefix match = findPrefix(text, prefixes);
    return match == null ? null : match.value;
}

/**
 * Finds the first matching prefix from a list of candidates.
 *
 * @param text     the text to inspect
 * @param prefixes the candidate prefixes
 * @return the matched prefix, or {@code null} if none matches
 */
static ParsedPrefix findPrefix(String text, ParsedPrefix... prefixes) {
    for (ParsedPrefix prefix : prefixes) {
        if (text.startsWith(prefix.key)) {
            return prefix;
        }
    }
    return null;
}

/**
 * Parses a piece description into side, piece, and square fields.
 *
 * @param text the piece description text
 * @return the parsed piece info, or {@code null} if the text is incomplete
 */
static ParsedPieceInfo parsePieceInfo(String text) {
    if (text == null || text.isBlank()) {
        return null;
    }
    String[] parts = text.trim().split(SPACE_REGEX);
    if (parts.length < 3) {
        return null;
    }
    String side = parts[0];
    String piece = parts[1];
    String square = parts[2];
    return new ParsedPieceInfo(side, piece, square);
}

/**
 * Detects the tactical motif name from a free-form tactical description.
 *
 * @param text the tactical description text
 * @return the motif label, or {@code null} when no known motif is found
 */
static String tacticalMotif(String text) {
    String lowered = text.toLowerCase();
    return startsWithAny(lowered,
            new ParsedPrefix(PIN_HEADER, PIN),
            new ParsedPrefix(SKEWER_HEADER, SKEWER),
            new ParsedPrefix(DISCOVERED_ATTACK_HEADER, DISCOVERED_ATTACK),
            new ParsedPrefix(OVERLOADED_DEFENDER_HEADER, OVERLOAD),
            new ParsedPrefix(HANGING_PREFIX, HANGING));
}

/**
 * Extracts a leading color token from a free-form text.
 *
 * @param text the text to inspect
 * @return {@code white} or {@code black} when present, otherwise {@code null}
 */
static String leadingColor(String text) {
    String[] tokens = text.split(SPACE_REGEX);
    for (String token : tokens) {
        if (WHITE.equals(token)) {
            return WHITE;
        }
        if (BLACK.equals(token)) {
            return BLACK;
        }
    }
    return null;
}

/**
 * Checks whether text is a canonical side label.
 *
 * @param value the value to inspect
 * @return {@code true} when the value is {@code white} or {@code black}
 */
static boolean isSideLabel(String value) {
    return WHITE.equals(value) || BLACK.equals(value);
}

/**
 * Checks whether text is a canonical piece label.
 *
 * @param value the value to inspect
 * @return {@code true} when the value names a chess piece
 */
static boolean isPieceLabel(String value) {
    return PAWN.equals(value) || KNIGHT.equals(value) || BISHOP.equals(value) || ROOK.equals(value)
            || QUEEN.equals(value) || KING_NAME.equals(value);
}

/**
 * Checks whether text is a lowercase algebraic board square.
 *
 * @param value the value to inspect
 * @return {@code true} when the value is in {@code a1..h8}
 */
static boolean isSquareLabel(String value) {
    return value != null && value.length() == 2
            && value.charAt(0) >= 'a' && value.charAt(0) <= 'h'
            && value.charAt(1) >= '1' && value.charAt(1) <= '8';
}

/**
 * Applies king-safety markers to the aggregated king-safety state.
 *
 * @param safety the mutable safety state to update
 * @param token  the normalized token text
 */
static void applyKingSafetyToken(KingSafety safety, String token) {
    if (token.contains(CASTLED)) {
        safety.castled = true;
    }
    if (token.contains(UNCASTLED)) {
        safety.castled = false;
    }
    if (token.contains(PAWN_SHIELD_WEAKENED)) {
        safety.shieldWeakened = true;
    }
    if (token.contains(KING_EXPOSED)) {
        safety.exposed = true;
    }
    if (token.contains(OPEN_FILE_NEAR)) {
        safety.openFile = true;
    }
}

/**
 * Emits the final king-safety tags for one side.
 *
 * @param tags        the mutable tag accumulator
 * @param side        the side label to attach
 * @param safety      the aggregated safety state
 * @param kingPresent whether this side's king exists on the board
 */
static void addKingSafetyTags(List<String> tags, String side, KingSafety safety, boolean kingPresent) {
    if (!kingPresent) {
        return;
    }
    boolean castled = Boolean.TRUE.equals(safety.castled);
    tags.add(KING_CASTLED_PREFIX + (castled ? YES : NO) + SIDE_FIELD + side);
    String shelter = PAWNS_INTACT;
    if (safety.openFile) {
        shelter = OPEN;
    } else if (safety.shieldWeakened) {
        shelter = WEAKENED;
    }
    tags.add(KING_SHELTER_PREFIX + shelter + SIDE_FIELD + side);

    String safetyLabel = SAFE;
    if (safety.exposed) {
        safetyLabel = VERY_UNSAFE;
    } else if (safety.openFile || safety.shieldWeakened) {
        safetyLabel = UNSAFE;
    } else if (castled) {
        safetyLabel = VERY_SAFE;
    }
    tags.add(KING_SAFETY_PREFIX + safetyLabel + SIDE_FIELD + side);
}

/**
 * Checks whether the board contains bishops on opposite colors.
 *
 * @param board the board array to inspect
 * @return {@code true} when both bishop colors occupy opposite-colored squares
 */
static boolean hasOppositeColoredBishops(byte[] board) {
    BishopColorState bishops = new BishopColorState();
    for (int index = 0; index < board.length; index++) {
        byte piece = board[index];
        if (Piece.isBishop(piece)) {
            bishops.mark(piece, (byte) index);
        }
    }
    return bishops.hasOppositeColors();
}

/**
 * Determines whether the position has insufficient mating material.
 *
 * @param position the position to inspect
 * @return {@code true} when the remaining material cannot force mate
 */
static boolean isInsufficientMaterial(Position position) {
    return position.isInsufficientMaterial();
}

/**
 * Maps verbose endgame labels to the canonical endgame tag values.
 *
 * @param val the raw endgame label
 * @return the canonical endgame label
 */
static String mapEndgame(String val) {
    switch (val) {
        case QUEENLESS:
            return QUEENLESS;
        case ROOK_ENDGAME:
            return ROOK_ENDGAME_SHORT;
        case MINOR_PIECE_ENDGAME:
            return MINOR_ENDGAME_SHORT;
        default:
            return val;
    }
}

/**
 * Determines the side with a space advantage.
 *
 * @param ctx the shared tagging context
 * @return the side with the advantage, or equal when no preference exists
 */
static String spaceSide(Context ctx) {
    if (WHITE.equals(ctx.spaceAdvantage) || BLACK.equals(ctx.spaceAdvantage)) {
        return ctx.spaceAdvantage;
    }
    if (ctx.centerControl != null) {
        if (WHITE.equals(ctx.centerControl)) {
            return WHITE;
        }
        if (BLACK.equals(ctx.centerControl)) {
            return BLACK;
        }
    }
    return EQUAL;
}

/**
 * Determines the side with a development advantage.
 *
 * @param position the position to inspect
 * @return the side with the advantage, or equal when no preference exists
 */
static String developmentSide(Position position) {
    int white = undevelopedMinors(position, true);
    int black = undevelopedMinors(position, false);
    int diff = black - white;
    if (diff >= 2) {
        return WHITE;
    }
    if (diff <= -2) {
        return BLACK;
    }
    return EQUAL;
}

/**
 * Determines the side with a mobility advantage.
 *
 * @param position the position to inspect
 * @return the side with the advantage, or equal when no preference exists
 */
static String mobilitySide(Position position) {
    int whiteMoves = mobilityForSide(position, true);
    int blackMoves = mobilityForSide(position, false);
    int diff = whiteMoves - blackMoves;
    if (diff >= 5) {
        return WHITE;
    }
    if (diff <= -5) {
        return BLACK;
    }
    return EQUAL;
}

/**
 * Determines the side with the initiative.
 *
 * @param ctx the shared tagging context
 * @return the side that appears to have the initiative, or equal when unclear
 */
static String initiativeSide(Context ctx) {
    if (ctx.hasThreatWhite && !ctx.hasThreatBlack) {
        return WHITE;
    }
    if (ctx.hasThreatBlack && !ctx.hasThreatWhite) {
        return BLACK;
    }
    if (ctx.evalCpWhite != null) {
        if (ctx.evalCpWhite > 80) {
            return WHITE;
        }
        if (ctx.evalCpWhite < -80) {
            return BLACK;
        }
    }
    return EQUAL;
}

/**
 * Counts undeveloped minor pieces for a given side.
 *
 * @param position the position to inspect
 * @param white    whether to count White pieces or Black pieces
 * @return the number of undeveloped minor pieces
 */
static int undevelopedMinors(Position position, boolean white) {
    int count = 0;
    byte[] board = position.getBoard();
    if (white) {
        count += pieceCountAt(board, Piece.WHITE_KNIGHT, Field.B1);
        count += pieceCountAt(board, Piece.WHITE_KNIGHT, Field.G1);
        count += pieceCountAt(board, Piece.WHITE_BISHOP, Field.C1);
        count += pieceCountAt(board, Piece.WHITE_BISHOP, Field.F1);
    } else {
        count += pieceCountAt(board, Piece.BLACK_KNIGHT, Field.B8);
        count += pieceCountAt(board, Piece.BLACK_KNIGHT, Field.G8);
        count += pieceCountAt(board, Piece.BLACK_BISHOP, Field.C8);
        count += pieceCountAt(board, Piece.BLACK_BISHOP, Field.F8);
    }
    return count;
}

/**
 * Checks whether a specific piece occupies a specific square.
 *
 * @param board  the board array to inspect
 * @param piece  the piece to match
 * @param square the square to inspect
 * @return {@code true} when the board square contains the piece
 */
static boolean isPieceAt(byte[] board, byte piece, byte square) {
    if (square == Field.NO_SQUARE) {
        return false;
    }
    return board[square] == piece;
}

/**
 * Returns 1 when a piece is on a given square and 0 otherwise.
 *
 * @param board  the board array to inspect
 * @param piece  the piece to match
 * @param square the square to inspect
 * @return 1 when the piece occupies the square, otherwise 0
 */
static int pieceCountAt(byte[] board, byte piece, byte square) {
    return isPieceAt(board, piece, square) ? 1 : 0;
}

/**
 * Counts legal moves for one side, even when that side is not on move.
 *
 * @param position the position to inspect
 * @param white    whether to count White's mobility or Black's mobility
 * @return the number of legal moves for the requested side
 */
static int mobilityForSide(Position position, boolean white) {
    if (position.isWhiteToMove() == white) {
        return position.legalMoves().size();
    }
    String flipped = flipSideToMove(position.toString(), white);
    if (flipped == null) {
        return position.legalMoves().size();
    }
    try {
        Position other = new Position(flipped);
        return other.legalMoves().size();
    } catch (IllegalArgumentException ex) {
        return position.legalMoves().size();
    }
}

/**
 * Flips the side-to-move field in a FEN string.
 *
 * @param fen   the source FEN text
 * @param white whether the resulting FEN should indicate White to move
 * @return the rewritten FEN, or {@code null} when the input is malformed
 */
static String flipSideToMove(String fen, boolean white) {
    String[] parts = fen.split(SPACE_REGEX);
    if (parts.length < 2) {
        return null;
    }
    parts[1] = white ? FEN_WHITE_TO_MOVE : FEN_BLACK_TO_MOVE;
    return String.join(SPACE_TEXT, parts);
}
}
