package chess.core;

import static chess.core.Position.*;

/**
 * Standard Algebraic Notation helpers backed by core move generation.
 *
 * <p>
 * The helper generates SAN by inspecting legal moves directly. It
 * handles captures, disambiguation, promotions, standard castling, Chess960
 * castling, check suffixes, and checkmate suffixes without converting through
 * another position representation.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class SAN {

    /**
     * SAN token for king-side castling.
     */
    private static final String CASTLING_KINGSIDE = "O-O";

    /**
     * SAN token for queen-side castling.
     */
    private static final String CASTLING_QUEENSIDE = "O-O-O";

    /**
     * Result token representing a White win.
     */
    public static final String RESULT_WHITE_WIN = "1-0";

    /**
     * Result token representing a Black win.
     */
    public static final String RESULT_BLACK_WIN = "0-1";

    /**
     * Result token representing a draw.
     */
    public static final String RESULT_DRAW = "1/2-1/2";

    /**
     * Result token representing an unknown or ongoing game.
     */
    public static final String RESULT_UNKNOWN = "*";

    /**
     * Utility class; prevents instantiation.
     */
    private SAN() {
        // utility
    }

    /**
     * Converts one legal move to SAN in the supplied position.
     *
     * @param context position before the move
     * @param move encoded move
     * @return SAN text
     * @throws IllegalArgumentException when the move has no moving piece
     */
    public static String toAlgebraic(Position context, short move) {
        return toAlgebraic(context, move, null);
    }

    /**
     * Converts one legal move to SAN with an optional legal-move cache.
     *
     * @param context position before the move
     * @param move encoded move
     * @param legalMoves legal moves for {@code context}, or null when unavailable
     * @return SAN text
     * @throws IllegalArgumentException when the move has no moving piece
     */
    private static String toAlgebraic(Position context, short move, MoveList legalMoves) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        int from = move & 0x3F;
        int to = (move >>> 6) & 0x3F;
        int promotion = (move >>> 12) & 0x7;
        int moving = context.pieceIndexAt(from);
        if (moving < 0) {
            throw new IllegalArgumentException("No piece on " + Bits.name(from));
        }

        String ending = algebraicEnding(context, move);
        int castleRight = context.castlingRightForMove(moving, to);
        if (castleRight != 0) {
            return isKingsideCastle(castleRight) ? CASTLING_KINGSIDE + ending : CASTLING_QUEENSIDE + ending;
        }

        boolean capture = context.pieceIndexAt(to) >= 0 || isEnPassantCapture(context, moving, to);
        StringBuilder result = new StringBuilder(8);
        String piece = pieceSymbol(moving);
        result.append(piece);
        if (!piece.isEmpty()) {
            result.append(disambiguation(context, move, moving, legalMoves));
        } else if (capture) {
            result.append(Field.getFile((byte) from));
        }
        if (capture) {
            result.append('x');
        }
        result.append(Field.toString((byte) to));
        appendPromotion(result, promotion);
        result.append(ending);
        return result.toString();
    }

    /**
     * Parses a SAN token to the matching legal move.
     *
     * @param context position before the move
     * @param algebraic SAN token
     * @return encoded legal move
     * @throws IllegalArgumentException when no legal move matches
     */
    public static short fromAlgebraic(Position context, String algebraic) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        String san = stripAnnotations(normalizeMoveToken(algebraic));
        MoveList moves = MoveGenerator.generateLegalMoves(context);
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            if (toAlgebraic(context, move, moves).equals(san)) {
                return move;
            }
        }
        throw new IllegalArgumentException("Invalid SAN '" + algebraic + "' in position '" + context + "'");
    }

    /**
     * Normalizes one SAN token before matching.
     *
     * <p>
     * This accepts common zero and lowercase-o castling spellings while
     * preserving suffixes such as {@code +}, {@code #}, {@code !}, and
     * {@code ?}.
     * </p>
     *
     * @param token raw SAN token
     * @return normalized SAN token
     */
    public static String normalizeMoveToken(String token) {
        if (token == null) {
            return "";
        }
        String trimmed = token.trim();
        if (startsWithCastle(trimmed, "0-0-0") || startsWithCastle(trimmed, "o-o-o")) {
            return CASTLING_QUEENSIDE + trimmed.substring(5);
        }
        if (startsWithCastle(trimmed, "0-0") || startsWithCastle(trimmed, "o-o")) {
            return CASTLING_KINGSIDE + trimmed.substring(3);
        }
        return trimmed;
    }

    /**
     * Applies a SAN move line to a starting position.
     *
     * <p>
     * Parsing stops at the first invalid token and returns the position reached
     * by the valid prefix together with metadata for the last parsed move.
     * </p>
     *
     * @param start starting position
     * @param movetext SAN move text
     * @return parsed line result
     */
    public static PlayedLine playLine(Position start, String movetext) {
        if (start == null) {
            throw new IllegalArgumentException("start == null");
        }
        Position initial = start.copy();
        Position current = start.copy();
        String cleaned = cleanMoveString(movetext);
        PlayedMoveState state = PlayedMoveState.empty();
        if (cleaned.isEmpty()) {
            return new PlayedLine(initial, current, state, false, "");
        }
        int tokenStart = 0;
        for (int i = 0; i <= cleaned.length(); i++) {
            if (i == cleaned.length() || cleaned.charAt(i) == ' ') {
                if (i > tokenStart) {
                    String token = cleaned.substring(tokenStart, i);
                    try {
                        short move = fromAlgebraic(current, token);
                        state = state.played(move, token, current.fullMoveNumber(), current.isWhiteToMove());
                        current.play(move);
                    } catch (IllegalArgumentException ex) {
                        return new PlayedLine(initial, current, state, false, token);
                    }
                }
                tokenStart = i + 1;
            }
        }
        return new PlayedLine(initial, current, state, true, "");
    }

    /**
     * Returns the last SAN-like move token from raw movetext.
     *
     * @param movetext raw SAN/PGN movetext
     * @return last move token, or an empty string when none is present
     */
    public static String lastMoveToken(String movetext) {
        String cleaned = cleanMoveString(movetext);
        if (cleaned.isBlank()) {
            return "";
        }
        int index = cleaned.lastIndexOf(' ');
        return index < 0 ? cleaned : cleaned.substring(index + 1);
    }

    /**
     * Cleans simple PGN movetext to SAN tokens separated by single spaces.
     *
     * <p>
     * Comments, parenthesized variations, move numbers, NAGs, and result tokens
     * are skipped. This method is intentionally allocation-light and regex-free
     * for common engine and CLI conversion paths.
     * </p>
     *
     * @param movetext raw SAN or PGN-style movetext
     * @return cleaned SAN token stream
     */
    public static String cleanMoveString(String movetext) {
        if (movetext == null || movetext.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(movetext.length());
        StringBuilder token = new StringBuilder(16);
        int commentDepth = 0;
        int variationDepth = 0;
        for (int i = 0; i <= movetext.length(); i++) {
            char ch = i == movetext.length() ? ' ' : movetext.charAt(i);
            if (commentDepth > 0) {
                commentDepth = updateCommentDepth(commentDepth, ch);
            } else if (variationDepth > 0) {
                variationDepth = updateVariationDepth(variationDepth, ch);
            } else if (ch == '{') {
                appendCleanToken(result, token);
                commentDepth++;
            } else if (ch == '(') {
                appendCleanToken(result, token);
                variationDepth++;
            } else if (Character.isWhitespace(ch)) {
                appendCleanToken(result, token);
            } else {
                token.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Updates the current brace-comment nesting depth.
     *
     * @param depth current depth
     * @param ch current character
     * @return updated depth
     */
    private static int updateCommentDepth(int depth, char ch) {
        return ch == '}' ? depth - 1 : depth;
    }

    /**
     * Updates the current parenthesized-variation nesting depth.
     *
     * @param depth current depth
     * @param ch current character
     * @return updated depth
     */
    private static int updateVariationDepth(int depth, char ch) {
        if (ch == '(') {
            return depth + 1;
        }
        return ch == ')' ? depth - 1 : depth;
    }

    /**
     * Cleans PGN move text while preserving variation parentheses.
     *
     * @param movetext raw PGN move text
     * @return cleaned text with variation grouping preserved
     */
    public static String cleanMoveStringKeepVariationsRegex(String movetext) {
        if (movetext == null || movetext.isEmpty()) {
            return "";
        }
        return movetext
                .replaceAll("\\{[^}]*\\}", " ")
                .replaceAll("(?m);[^\\r\\n]*", " ")
                .replaceAll("\\$\\d+", " ")
                .replaceAll("\\d+\\.(?:\\.\\.)?", " ")
                .replaceAll("(?<!\\S)(?:1-0|0-1|1/2-1/2|\\*)(?!\\S)", " ")
                .replaceAll("\\s*\\(\\s*", " ( ")
                .replaceAll("\\s*\\)\\s*", " ) ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Appends a normalized token unless it is PGN metadata.
     *
     * @param result cleaned movetext builder
     * @param token current token builder
     */
    private static void appendCleanToken(StringBuilder result, StringBuilder token) {
        if (token.isEmpty()) {
            return;
        }
        String value = token.toString();
        token.setLength(0);
        value = stripMoveNumberPrefix(value);
        if (value.isEmpty()) {
            return;
        }
        if (isMoveNumber(value) || isNag(value) || isResult(value)) {
            return;
        }
        if (!result.isEmpty()) {
            result.append(' ');
        }
        result.append(value);
    }

    /**
     * Computes the check or checkmate suffix for a move.
     *
     * @param context position before the move
     * @param move encoded move
     * @return {@code +}, {@code #}, or an empty string
     */
    private static String algebraicEnding(Position context, short move) {
        Position after = context.copy().play(move);
        boolean checkedSide = after.isWhiteToMove();
        if (!MoveGenerator.isKingAttacked(after, checkedSide)) {
            return "";
        }
        return MoveGenerator.hasLegalMove(after) ? "+" : "#";
    }

    /**
     * Returns whether a castling right is king-side.
     *
     * @param right castling-right bit
     * @return true for king-side castling
     */
    private static boolean isKingsideCastle(int right) {
        return right == WHITE_KINGSIDE || right == BLACK_KINGSIDE;
    }

    /**
     * Returns whether a move is an en-passant capture.
     *
     * @param context position before the move
     * @param moving moving piece index
     * @param to target square
     * @return true when the move captures en-passant
     */
    private static boolean isEnPassantCapture(Position context, int moving, int to) {
        return (moving == WHITE_PAWN || moving == BLACK_PAWN)
                && to == context.enPassantSquare
                && context.pieceIndexAt(to) < 0;
    }

    /**
     * Builds SAN disambiguation for a non-pawn piece move.
     *
     * @param context position before the move
     * @param move encoded move
     * @param moving moving piece index
     * @return minimal file/rank disambiguation
     */
    private static String disambiguation(Position context, short move, int moving, MoveList legalMoves) {
        if (moving == WHITE_PAWN || moving == BLACK_PAWN) {
            return "";
        }
        int from = move & 0x3F;
        int to = (move >>> 6) & 0x3F;
        char file = Field.getFile((byte) from);
        char rank = Field.getRank((byte) from);
        boolean ambiguous = false;
        boolean sameFile = false;
        boolean sameRank = false;
        MoveList moves = legalMoves == null ? MoveGenerator.generateLegalMoves(context) : legalMoves;
        for (int i = 0; i < moves.size(); i++) {
            short candidate = moves.raw(i);
            int candidateFrom = candidate & 0x3F;
            int candidateTo = (candidate >>> 6) & 0x3F;
            if (candidateFrom == from || candidateTo != to || context.pieceIndexAt(candidateFrom) != moving) {
                continue;
            }
            ambiguous = true;
            sameFile |= Field.getFile((byte) candidateFrom) == file;
            sameRank |= Field.getRank((byte) candidateFrom) == rank;
        }
        if (!ambiguous) {
            return "";
        }
        if (!sameFile) {
            return String.valueOf(file);
        }
        if (!sameRank) {
            return String.valueOf(rank);
        }
        return new String(new char[] { file, rank });
    }

    /**
     * Maps an internal piece index to a SAN piece symbol.
     *
     * @param piece piece index
     * @return uppercase piece symbol, or an empty string for pawns
     */
    private static String pieceSymbol(int piece) {
        return switch (piece) {
            case WHITE_KNIGHT, BLACK_KNIGHT -> "N";
            case WHITE_BISHOP, BLACK_BISHOP -> "B";
            case WHITE_ROOK, BLACK_ROOK -> "R";
            case WHITE_QUEEN, BLACK_QUEEN -> "Q";
            case WHITE_KING, BLACK_KING -> "K";
            case WHITE_PAWN, BLACK_PAWN -> "";
            default -> "?";
        };
    }

    /**
     * Appends a SAN promotion suffix.
     *
     * @param result target builder
     * @param promotion promotion code
     */
    private static void appendPromotion(StringBuilder result, int promotion) {
        switch (promotion) {
            case PROMOTION_QUEEN -> result.append("=Q");
            case PROMOTION_ROOK -> result.append("=R");
            case PROMOTION_BISHOP -> result.append("=B");
            case PROMOTION_KNIGHT -> result.append("=N");
            default -> {
                // no promotion
            }
        }
    }

    /**
     * Strips trailing annotation glyphs accepted by the SAN parser.
     *
     * @param token normalized SAN token
     * @return token without trailing {@code !} and {@code ?}
     */
    private static String stripAnnotations(String token) {
        int end = token.length();
        while (end > 0) {
            char ch = token.charAt(end - 1);
            if (ch != '!' && ch != '?') {
                break;
            }
            end--;
        }
        return end == token.length() ? token : token.substring(0, end);
    }

    /**
     * Removes a compact PGN move-number prefix from one token.
     *
     * @param value token text
     * @return token without a leading {@code 12.} or {@code 12...} prefix
     */
    private static String stripMoveNumberPrefix(String value) {
        int i = 0;
        while (i < value.length() && Character.isDigit(value.charAt(i))) {
            i++;
        }
        if (i == 0 || i == value.length() || value.charAt(i) != '.') {
            return value;
        }
        while (i < value.length() && value.charAt(i) == '.') {
            i++;
        }
        return value.substring(i);
    }

    /**
     * Checks one normalized castling spelling.
     *
     * @param value token to inspect
     * @param castle lowercase or zero castle prefix
     * @return true when the token starts with the castle prefix
     */
    private static boolean startsWithCastle(String value, String castle) {
        if (value.length() < castle.length()) {
            return false;
        }
        for (int i = 0; i < castle.length(); i++) {
            char expected = castle.charAt(i);
            char actual = value.charAt(i);
            if (expected == 'o' && actual != 'o' && actual != 'O') {
                return false;
            }
            if (expected != 'o' && actual != expected) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether a token is a PGN move number.
     *
     * @param value token text
     * @return true for tokens such as {@code 12.} or {@code 12...}
     */
    private static boolean isMoveNumber(String value) {
        int i = 0;
        while (i < value.length() && Character.isDigit(value.charAt(i))) {
            i++;
        }
        if (i == 0 || i == value.length()) {
            return false;
        }
        while (i < value.length() && value.charAt(i) == '.') {
            i++;
        }
        return i == value.length();
    }

    /**
     * Returns whether a token is a numeric annotation glyph.
     *
     * @param value token text
     * @return true for NAG tokens such as {@code $5}
     */
    private static boolean isNag(String value) {
        if (value.length() < 2 || value.charAt(0) != '$') {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether a token is a game result marker.
     *
     * @param value token text
     * @return true for PGN result tokens
     */
    private static boolean isResult(String value) {
        return RESULT_WHITE_WIN.equals(value)
                || RESULT_BLACK_WIN.equals(value)
                || RESULT_DRAW.equals(value)
                || RESULT_UNKNOWN.equals(value);
    }

    /**
     * Result of applying a SAN move line.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    public static final class PlayedLine {

        /**
         * Original starting position.
         */
        private final Position start;

        /**
         * Position reached by the valid prefix of the line.
         */
        private final Position result;

        /**
         * Last successfully parsed move.
         */
        private final short lastMove;

        /**
         * Last successfully parsed SAN token.
         */
        private final String lastSan;

        /**
         * Full-move number before the last successfully parsed move.
         */
        private final int lastMoveNumber;

        /**
         * Whether the last successfully parsed move was played by White.
         */
        private final boolean lastMoveWasWhite;

        /**
         * Number of successfully parsed plies.
         */
        private final int pliesPlayed;

        /**
         * Whether every cleaned token parsed successfully.
         */
        private final boolean parsed;

        /**
         * First invalid token, or an empty string when the line parsed completely.
         */
        private final String invalidToken;

        /**
         * Creates one parsed-line result.
         *
         * @param start starting position
         * @param result resulting position
         * @param lastMove last parsed move
         * @param lastSan last parsed SAN token
         * @param lastMoveNumber last full-move number
         * @param lastMoveWasWhite true when last move was White
         * @param pliesPlayed number of plies parsed
         * @param parsed true when all tokens parsed
         * @param invalidToken first invalid token
         */
        private PlayedLine(
                Position start,
                Position result,
                PlayedMoveState progress,
                boolean parsed,
                String invalidToken) {
            this.start = start.copy();
            this.result = result.copy();
            this.lastMove = progress.lastMove();
            this.lastSan = progress.lastSan();
            this.lastMoveNumber = progress.lastMoveNumber();
            this.lastMoveWasWhite = progress.lastMoveWasWhite();
            this.pliesPlayed = progress.pliesPlayed();
            this.parsed = parsed;
            this.invalidToken = invalidToken == null ? "" : invalidToken;
        }

        /**
         * Returns the original starting position.
         *
         * @return defensive position copy
         */
        public Position getStart() {
            return start.copy();
        }

        /**
         * Returns the reached position.
         *
         * @return defensive position copy
         */
        public Position getResult() {
            return result.copy();
        }

        /**
         * Returns the last successfully parsed move.
         *
         * @return move, or {@link chess.core.Move#NO_MOVE}
         */
        public short getLastMove() {
            return lastMove;
        }

        /**
         * Returns whether at least one move parsed successfully.
         *
         * @return true when a last move exists
         */
        public boolean hasLastMove() {
            return lastMove != chess.core.Move.NO_MOVE;
        }

        /**
         * Returns the last successfully parsed SAN token.
         *
         * @return SAN token, or an empty string
         */
        public String getLastSan() {
            return lastSan;
        }

        /**
         * Returns the full-move number before the last parsed move.
         *
         * @return full-move number, or zero when no move parsed
         */
        public int getLastMoveNumber() {
            return lastMoveNumber;
        }

        /**
         * Returns whether the last parsed move was played by White.
         *
         * @return true for White, false for Black
         */
        public boolean isLastMoveByWhite() {
            return lastMoveWasWhite;
        }

        /**
         * Returns the last SAN token with a move-number prefix.
         *
         * @return move-numbered SAN, or empty string when no move parsed
         */
        public String lastSanWithMoveNumber() {
            if (!hasLastMove() || lastSan.isBlank() || lastMoveNumber <= 0) {
                return "";
            }
            return lastMoveNumber + (lastMoveWasWhite ? ". " : "... ") + lastSan;
        }

        /**
         * Returns the number of successfully parsed plies.
         *
         * @return ply count
         */
        public int getPliesPlayed() {
            return pliesPlayed;
        }

        /**
         * Returns whether the whole non-empty line parsed successfully.
         *
         * @return true when every token parsed
         */
        public boolean isParsed() {
            return parsed;
        }

        /**
         * Returns the first invalid token.
         *
         * @return invalid token, or empty string
         */
        public String getInvalidToken() {
            return invalidToken;
        }
    }

    /**
     * Parse progress captured for the most recent valid move.
     *
     * @param lastMove last successfully parsed move
     * @param lastSan last successfully parsed SAN token
     * @param lastMoveNumber full-move number before the last move
     * @param lastMoveWasWhite whether White played the last move
     * @param pliesPlayed number of plies successfully applied
     */
    private record PlayedMoveState(
            short lastMove,
            String lastSan,
            int lastMoveNumber,
            boolean lastMoveWasWhite,
            int pliesPlayed) {

        /**
         * Returns the initial empty parse state.
         *
         * @return empty state
         */
        private static PlayedMoveState empty() {
            return new PlayedMoveState(chess.core.Move.NO_MOVE, "", 0, true, 0);
        }

        /**
         * Returns the state after one move was applied.
         *
         * @param move applied move
         * @param san parsed SAN token
         * @param moveNumber full-move number before the move
         * @param moveWasWhite whether White played the move
         * @return updated state
         */
        private PlayedMoveState played(short move, String san, int moveNumber, boolean moveWasWhite) {
            return new PlayedMoveState(
                    move,
                    san == null ? "" : san,
                    Math.max(0, moveNumber),
                    moveWasWhite,
                    pliesPlayed + 1);
        }
    }
}
