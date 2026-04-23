package chess.core;

import static chess.core.Position.*;

/**
 * Optimized FEN parser and formatter for bitboard positions.
 *
 * <p>
 * The implementation writes directly into {@link Position}'s bitboards,
 * occupancy caches, king-square caches, and castling metadata. It intentionally
 * mirrors the strict validation used by the core position parser, including
 * king counts, pawn placement, castling rook presence, and legal en-passant
 * square consistency.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Fen {

    /**
     * Utility class; prevents instantiation.
     */
    private Fen() {
        // utility
    }

    /**
     * Normalizes FEN text before strict parsing.
     *
     * <p>
     * The normalizer trims leading and trailing whitespace and collapses internal
     * whitespace runs to one ASCII space.
     * </p>
     *
     * @param value raw FEN text
     * @return normalized FEN text, or an empty string for null input
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return collapseWhitespace(value.trim());
    }

    /**
     * Parses a FEN string into a bitboard-backed position.
     *
     * <p>
     * The returned position is fully initialized and validated. The parser
     * accepts either four fields or six fields after normalization.
     * </p>
     *
     * @param fen source FEN
     * @return parsed position
     * @throws IllegalArgumentException when the FEN is malformed or illegal
     */
    public static Position parse(String fen) {
        String normalized = normalize(fen);
        String[] parts = normalized.split(" ");
        if (parts.length < 4 || parts.length > 6) {
            throw new IllegalArgumentException(
                    "Invalid FEN '" + normalized + "' contains " + parts.length + " fields, expected 4 to 6.");
        }
        if (parts.length == 5) {
            throw new IllegalArgumentException(
                    "Invalid FEN '" + normalized + "' contains 5 fields, expected 4 or 6.");
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Invalid FEN '" + normalized + "' contains empty fields.");
            }
        }

        Position position = new Position();
        parsePlacement(position, parts[0], normalized);
        parseActiveColor(position, parts[1], normalized);
        parseCastling(position, parts[2], normalized);
        parseEnPassant(position, parts[3], normalized);
        if (parts.length == 6) {
            position.halfMoveClock = parseClock(parts[4], "half move clock", normalized, false);
            position.fullMoveNumber = parseClock(parts[5], "full move number", normalized, true);
        }
        validate(position, normalized);
        return position;
    }

    /**
     * Formats a bitboard position as a six-field FEN string.
     *
     * @param position source position
     * @return FEN text
     * @throws IllegalArgumentException when the position is null
     */
    public static String format(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        StringBuilder result = new StringBuilder(90);
        appendPlacement(position, result);
        result.append(' ')
                .append(position.whiteToMove ? 'w' : 'b')
                .append(' ');
        appendCastling(position, result);
        result.append(' ')
                .append(Field.toString(position.enPassantSquare))
                .append(' ')
                .append(position.halfMoveClock)
                .append(' ')
                .append(position.fullMoveNumber);
        return result.toString();
    }

    /**
     * Collapses every whitespace run to one ASCII space.
     *
     * @param value trimmed source text
     * @return source text with normalized whitespace
     */
    private static String collapseWhitespace(String value) {
        if (value.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        boolean inWhitespace = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch)) {
                inWhitespace = true;
            } else {
                if (inWhitespace && !result.isEmpty()) {
                    result.append(' ');
                }
                result.append(ch);
                inWhitespace = false;
            }
        }
        return result.toString();
    }

    /**
     * Checks whether a string contains only decimal digits.
     *
     * @param value source text
     * @return true when the string is non-empty and unsigned
     */
    private static boolean isUnsignedInteger(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses the piece-placement field.
     *
     * @param position target position
     * @param placement placement field
     * @param source complete normalized FEN for diagnostics
     */
    private static void parsePlacement(Position position, String placement, String source) {
        int square = 0;
        int rankSquares = 0;
        int ranks = 0;
        for (int i = 0; i < placement.length(); i++) {
            char ch = placement.charAt(i);
            if (ch == '/') {
                if (rankSquares != 8) {
                    throw new IllegalArgumentException("Invalid FEN '" + source + "' rank width: " + placement);
                }
                rankSquares = 0;
                ranks++;
                continue;
            }
            if (ch >= '1' && ch <= '8') {
                int empty = ch - '0';
                rankSquares += empty;
                square += empty;
            } else {
                int piece = pieceIndexFromFen(ch);
                if (piece < 0) {
                    throw new IllegalArgumentException("Invalid FEN '" + source + "' piece: " + ch);
                }
                if (square >= 64) {
                    throw new IllegalArgumentException("Too many squares in FEN '" + source + "': " + placement);
                }
                position.setPiece(piece, square++);
                rankSquares++;
            }
            if (rankSquares > 8 || square > 64) {
                throw new IllegalArgumentException("Too many squares in FEN '" + source + "': " + placement);
            }
        }
        if (rankSquares != 8) {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' rank width: " + placement);
        }
        ranks++;
        if (ranks != 8 || square != 64) {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' board size: " + placement);
        }
    }

    /**
     * Parses the active-color field.
     *
     * @param position target position
     * @param color active-color field
     * @param source complete normalized FEN for diagnostics
     */
    private static void parseActiveColor(Position position, String color, String source) {
        if ("w".equals(color)) {
            position.whiteToMove = true;
        } else if ("b".equals(color)) {
            position.whiteToMove = false;
        } else {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' color: " + color);
        }
    }

    /**
     * Parses castling availability.
     *
     * @param position target position
     * @param castling castling field
     * @param source complete normalized FEN for diagnostics
     */
    private static void parseCastling(Position position, String castling, String source) {
        position.castlingRights = 0;
        position.whiteKingsideRookSquare = Field.NO_SQUARE;
        position.whiteQueensideRookSquare = Field.NO_SQUARE;
        position.blackKingsideRookSquare = Field.NO_SQUARE;
        position.blackQueensideRookSquare = Field.NO_SQUARE;
        position.chess960Castling = false;

        if ("-".equals(castling)) {
            return;
        }
        if (castling.indexOf('-') >= 0) {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' castling string: " + castling);
        }
        if (isStandardCastling(castling)) {
            parseStandardCastling(position, castling);
            return;
        }
        parseChess960Castling(position, castling, source);
    }

    /**
     * Parses standard castling symbols.
     *
     * @param position target position
     * @param castling castling field
     */
    private static void parseStandardCastling(Position position, String castling) {
        for (int i = 0; i < castling.length(); i++) {
            switch (castling.charAt(i)) {
                case 'K' -> {
                    position.castlingRights |= WHITE_KINGSIDE;
                    position.whiteKingsideRookSquare = Field.H1;
                }
                case 'Q' -> {
                    position.castlingRights |= WHITE_QUEENSIDE;
                    position.whiteQueensideRookSquare = Field.A1;
                }
                case 'k' -> {
                    position.castlingRights |= BLACK_KINGSIDE;
                    position.blackKingsideRookSquare = Field.H8;
                }
                case 'q' -> {
                    position.castlingRights |= BLACK_QUEENSIDE;
                    position.blackQueensideRookSquare = Field.A8;
                }
                default -> throw new IllegalArgumentException("Invalid standard castling character");
            }
        }
    }

    /**
     * Parses Chess960 castling file letters.
     *
     * @param position target position
     * @param castling castling field
     * @param source complete normalized FEN for diagnostics
     */
    private static void parseChess960Castling(Position position, String castling, String source) {
        position.chess960Castling = true;
        boolean seenBlack = false;
        boolean seenWhiteQueenside = false;
        boolean seenBlackQueenside = false;
        for (int i = 0; i < castling.length(); i++) {
            char ch = castling.charAt(i);
            if (ch >= 'A' && ch <= 'H') {
                if (seenBlack) {
                    throw invalidChess960Castling(source, castling);
                }
                seenWhiteQueenside |= parseWhiteChess960Castling(
                        position, ch - 'A', seenWhiteQueenside, source, castling);
                continue;
            }
            if (ch >= 'a' && ch <= 'h') {
                seenBlack = true;
                seenBlackQueenside |= parseBlackChess960Castling(
                        position, ch - 'a', seenBlackQueenside, source, castling);
                continue;
            }
            throw new IllegalArgumentException("Invalid FEN '" + source + "' castling character: " + ch);
        }
    }

    /**
     * Parses one White Chess960 castling file letter.
     *
     * @param position target position
     * @param file rook file from {@code a} to {@code h}
     * @param seenWhiteQueenside whether a White queen-side right was already read
     * @param source complete normalized FEN for diagnostics
     * @param castling castling field
     * @return true when the parsed right was queen-side
     */
    private static boolean parseWhiteChess960Castling(
            Position position, int file, boolean seenWhiteQueenside, String source, String castling) {
        int kingFile = validateChess960CastlingFile(position.whiteKingSquare, file, source, castling);
        byte square = (byte) (Field.A1 + file);
        if (file > kingFile) {
            if (position.canCastle(WHITE_KINGSIDE) || seenWhiteQueenside) {
                throw invalidChess960Castling(source, castling);
            }
            position.castlingRights |= WHITE_KINGSIDE;
            position.whiteKingsideRookSquare = square;
            return false;
        }
        if (position.canCastle(WHITE_QUEENSIDE)) {
            throw invalidChess960Castling(source, castling);
        }
        position.castlingRights |= WHITE_QUEENSIDE;
        position.whiteQueensideRookSquare = square;
        return true;
    }

    /**
     * Parses one Black Chess960 castling file letter.
     *
     * @param position target position
     * @param file rook file from {@code a} to {@code h}
     * @param seenBlackQueenside whether a Black queen-side right was already read
     * @param source complete normalized FEN for diagnostics
     * @param castling castling field
     * @return true when the parsed right was queen-side
     */
    private static boolean parseBlackChess960Castling(
            Position position, int file, boolean seenBlackQueenside, String source, String castling) {
        int kingFile = validateChess960CastlingFile(position.blackKingSquare, file, source, castling);
        byte square = (byte) (Field.A8 + file);
        if (file > kingFile) {
            if (position.canCastle(BLACK_KINGSIDE) || seenBlackQueenside) {
                throw invalidChess960Castling(source, castling);
            }
            position.castlingRights |= BLACK_KINGSIDE;
            position.blackKingsideRookSquare = square;
            return false;
        }
        if (position.canCastle(BLACK_QUEENSIDE)) {
            throw invalidChess960Castling(source, castling);
        }
        position.castlingRights |= BLACK_QUEENSIDE;
        position.blackQueensideRookSquare = square;
        return true;
    }

    /**
     * Validates a Chess960 castling rook file against the king square.
     *
     * @param kingSquare king square for the color being parsed
     * @param file rook file from {@code a} to {@code h}
     * @param source complete normalized FEN for diagnostics
     * @param castling castling field
     * @return king file from {@code a} to {@code h}
     */
    private static int validateChess960CastlingFile(int kingSquare, int file, String source, String castling) {
        int kingFile = kingSquare & 7;
        if (kingSquare == Field.NO_SQUARE || file == kingFile) {
            throw invalidChess960Castling(source, castling);
        }
        return kingFile;
    }

    /**
     * Builds a standard Chess960 castling parse exception.
     *
     * @param source complete normalized FEN for diagnostics
     * @param castling castling field
     * @return exception carrying the invalid-castling message
     */
    private static IllegalArgumentException invalidChess960Castling(String source, String castling) {
        return new IllegalArgumentException("Invalid FEN '" + source + "' Chess960 castling: " + castling);
    }

    /**
     * Checks whether the castling field is a valid standard-castling subsequence.
     *
     * @param castling castling field
     * @return true for standard KQkq-style availability
     */
    private static boolean isStandardCastling(String castling) {
        int previous = -1;
        for (int i = 0; i < castling.length(); i++) {
            int order = switch (castling.charAt(i)) {
                case 'K' -> 0;
                case 'Q' -> 1;
                case 'k' -> 2;
                case 'q' -> 3;
                default -> -1;
            };
            if (order < 0 || order <= previous) {
                return false;
            }
            previous = order;
        }
        return true;
    }

    /**
     * Parses en-passant target state.
     *
     * @param position target position
     * @param enPassant en-passant field
     * @param source complete normalized FEN for diagnostics
     */
    private static void parseEnPassant(Position position, String enPassant, String source) {
        if ("-".equals(enPassant)) {
            position.enPassantSquare = Field.NO_SQUARE;
            return;
        }
        if (!Field.isField(enPassant)) {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' en-passant square: " + enPassant);
        }
        position.enPassantSquare = Field.toIndex(enPassant);
    }

    /**
     * Parses a halfmove or fullmove clock field.
     *
     * @param value clock text
     * @param label diagnostic label
     * @param source complete normalized FEN for diagnostics
     * @param positive true when the value must be at least one
     * @return parsed value
     */
    private static short parseClock(String value, String label, String source, boolean positive) {
        if (!isUnsignedInteger(value)) {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' " + label + ": " + value);
        }
        try {
            short parsed = Short.parseShort(value);
            if ((positive && parsed < 1) || (!positive && parsed < 0)) {
                throw new IllegalArgumentException("Invalid FEN '" + source + "' " + label + ": " + value);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' " + label + ": " + value, ex);
        }
    }

    /**
     * Validates the parsed position.
     *
     * @param position parsed position
     * @param source complete normalized FEN for diagnostics
     */
    private static void validate(Position position, String source) {
        if (!validKings(position)) {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' results in illegal king placement.");
        }
        if (!validPawns(position)) {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' results in illegal pawn placement.");
        }
        if (!validCastling(position)) {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' results in illegal castling rights.");
        }
        if (!validEnPassant(position)) {
            throw new IllegalArgumentException("Invalid FEN '" + source + "' results in illegal en-passant state.");
        }
    }

    /**
     * Validates king counts and side-not-to-move king safety.
     *
     * @param position position to validate
     * @return true when king placement is legal
     */
    private static boolean validKings(Position position) {
        if (Long.bitCount(position.pieces[WHITE_KING]) != 1 || Long.bitCount(position.pieces[BLACK_KING]) != 1) {
            return false;
        }
        if (position.whiteKingSquare == Field.NO_SQUARE || position.blackKingSquare == Field.NO_SQUARE) {
            return false;
        }
        return !MoveGenerator.isKingAttacked(position, !position.whiteToMove);
    }

    /**
     * Validates pawn counts and ranks.
     *
     * @param position position to validate
     * @return true when pawn placement is legal
     */
    private static boolean validPawns(Position position) {
        long pawns = position.pieces[WHITE_PAWN] | position.pieces[BLACK_PAWN];
        if ((pawns & (Bits.RANK_1 | Bits.RANK_8)) != 0L) {
            return false;
        }
        return Long.bitCount(position.pieces[WHITE_PAWN]) <= 8
                && Long.bitCount(position.pieces[BLACK_PAWN]) <= 8;
    }

    /**
     * Validates castling rights against the board.
     *
     * @param position position to validate
     * @return true when all advertised rights have their king and rook
     */
    private static boolean validCastling(Position position) {
        return validCastlingRight(position, WHITE_KINGSIDE, WHITE_ROOK, Field.E1)
                && validCastlingRight(position, WHITE_QUEENSIDE, WHITE_ROOK, Field.E1)
                && validCastlingRight(position, BLACK_KINGSIDE, BLACK_ROOK, Field.E8)
                && validCastlingRight(position, BLACK_QUEENSIDE, BLACK_ROOK, Field.E8);
    }

    /**
     * Validates one castling right.
     *
     * @param position position to validate
     * @param right castling-right bit
     * @param rook expected rook piece
     * @param standardKingSquare required king square in standard chess
     * @return true when the right is absent or valid
     */
    private static boolean validCastlingRight(Position position, int right, int rook, byte standardKingSquare) {
        if (!position.canCastle(right)) {
            return true;
        }
        int rookSquare = position.castlingRookSquare(right);
        if (rookSquare == Field.NO_SQUARE || position.pieceIndexAt(rookSquare) != rook) {
            return false;
        }
        byte king = rook < BLACK_PAWN ? position.whiteKingSquare : position.blackKingSquare;
        if (king == Field.NO_SQUARE) {
            return false;
        }
        if (!position.chess960Castling) {
            return king == standardKingSquare;
        }
        return validChess960CastlingGeometry(right, king, rookSquare, standardKingSquare);
    }

    /**
     * Validates the king and rook geometry for one Chess960 castling right.
     *
     * @param right castling-right bit
     * @param king king source square
     * @param rook rook source square
     * @param standardKingSquare standard home square for the moving king
     * @return true when king and rook are on the correct home rank and side
     */
    private static boolean validChess960CastlingGeometry(
            int right,
            int king,
            int rook,
            byte standardKingSquare) {
        if (Bits.rank(king) != Bits.rank(standardKingSquare) || Bits.rank(rook) != Bits.rank(standardKingSquare)) {
            return false;
        }
        int kingFile = Bits.file(king);
        int rookFile = Bits.file(rook);
        if (rookFile == kingFile) {
            return false;
        }
        boolean kingside = right == WHITE_KINGSIDE || right == BLACK_KINGSIDE;
        return kingside ? rookFile > kingFile : rookFile < kingFile;
    }

    /**
     * Validates en-passant metadata.
     *
     * @param position position to validate
     * @return true when no en-passant exists or the target and passed pawn match
     */
    private static boolean validEnPassant(Position position) {
        int target = position.enPassantSquare;
        if (target == Field.NO_SQUARE) {
            return true;
        }
        if (position.pieceIndexAt(target) >= 0) {
            return false;
        }
        if (position.whiteToMove) {
            return Field.isOn6thRank((byte) target)
                    && target + 8 < 64
                    && position.pieceIndexAt(target + 8) == BLACK_PAWN;
        }
        return Field.isOn3rdRank((byte) target)
                && target - 8 >= 0
                && position.pieceIndexAt(target - 8) == WHITE_PAWN;
    }

    /**
     * Appends the FEN piece-placement field.
     *
     * @param position source position
     * @param result target builder
     */
    private static void appendPlacement(Position position, StringBuilder result) {
        for (int rank = 0; rank < 8; rank++) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = position.board[rank * 8 + file];
                if (piece < 0) {
                    empty++;
                } else {
                    if (empty > 0) {
                        result.append(empty);
                        empty = 0;
                    }
                    result.append(pieceToFen(piece));
                }
            }
            if (empty > 0) {
                result.append(empty);
            }
            if (rank < 7) {
                result.append('/');
            }
        }
    }

    /**
     * Appends the FEN castling-availability field.
     *
     * @param position source position
     * @param result target builder
     */
    private static void appendCastling(Position position, StringBuilder result) {
        int before = result.length();
        if (position.chess960Castling) {
            appendChess960Castling(position, result);
        } else {
            appendStandardCastling(position, result);
        }
        if (result.length() == before) {
            result.append('-');
        }
    }

    /**
     * Appends standard KQkq castling flags.
     *
     * @param position source position
     * @param result target builder
     */
    private static void appendStandardCastling(Position position, StringBuilder result) {
        if (position.canCastle(WHITE_KINGSIDE)) {
            result.append('K');
        }
        if (position.canCastle(WHITE_QUEENSIDE)) {
            result.append('Q');
        }
        if (position.canCastle(BLACK_KINGSIDE)) {
            result.append('k');
        }
        if (position.canCastle(BLACK_QUEENSIDE)) {
            result.append('q');
        }
    }

    /**
     * Appends Chess960 castling file letters.
     *
     * @param position source position
     * @param result target builder
     */
    private static void appendChess960Castling(Position position, StringBuilder result) {
        if (position.canCastle(WHITE_KINGSIDE)) {
            result.append(Field.getFileUppercase(position.whiteKingsideRookSquare));
        }
        if (position.canCastle(WHITE_QUEENSIDE)) {
            result.append(Field.getFileUppercase(position.whiteQueensideRookSquare));
        }
        if (position.canCastle(BLACK_KINGSIDE)) {
            result.append(Field.getFile(position.blackKingsideRookSquare));
        }
        if (position.canCastle(BLACK_QUEENSIDE)) {
            result.append(Field.getFile(position.blackQueensideRookSquare));
        }
    }

    /**
     * Converts a FEN piece character to an internal piece index.
     *
     * @param piece FEN piece character
     * @return piece index, or -1 for invalid input
     */
    private static int pieceIndexFromFen(char piece) {
        return switch (piece) {
            case 'P' -> WHITE_PAWN;
            case 'N' -> WHITE_KNIGHT;
            case 'B' -> WHITE_BISHOP;
            case 'R' -> WHITE_ROOK;
            case 'Q' -> WHITE_QUEEN;
            case 'K' -> WHITE_KING;
            case 'p' -> BLACK_PAWN;
            case 'n' -> BLACK_KNIGHT;
            case 'b' -> BLACK_BISHOP;
            case 'r' -> BLACK_ROOK;
            case 'q' -> BLACK_QUEEN;
            case 'k' -> BLACK_KING;
            default -> -1;
        };
    }

    /**
     * Converts an internal piece index to a FEN piece character.
     *
     * @param piece piece index
     * @return FEN piece character
     */
    private static char pieceToFen(int piece) {
        return switch (piece) {
            case WHITE_PAWN -> 'P';
            case WHITE_KNIGHT -> 'N';
            case WHITE_BISHOP -> 'B';
            case WHITE_ROOK -> 'R';
            case WHITE_QUEEN -> 'Q';
            case WHITE_KING -> 'K';
            case BLACK_PAWN -> 'p';
            case BLACK_KNIGHT -> 'n';
            case BLACK_BISHOP -> 'b';
            case BLACK_ROOK -> 'r';
            case BLACK_QUEEN -> 'q';
            case BLACK_KING -> 'k';
            default -> '?';
        };
    }
}
