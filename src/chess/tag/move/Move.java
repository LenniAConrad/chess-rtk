package chess.tag.move;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.tag.core.AttackUtils;
import chess.tag.core.Text;

/**
 * Produces descriptive tags for an individual chess move or a move list.
 * <p>
 * The tags capture the SAN move, move classification details, capture and
 * promotion information, king-safety consequences, and check or stalemate
 * outcomes.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Move {

    /**
     * Prevents instantiation of this utility class.
     */
    private Move() {
        // Utility class
    }

    /**
     * Builds descriptive tags for a single move.
     * <p>
     * The returned list contains at most one serialized move tag. If the source
     * square is empty, the method returns an empty list.
     * </p>
     *
     * @param position the position before the move is played
     * @param move the move to describe
     * @return an immutable list containing the move tag, or an empty list when
     *         the move cannot be described
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position, short move) {
        Objects.requireNonNull(position, POSITION);
        List<String> tags = new ArrayList<>(1);
        byte[] board = position.getBoard();
        byte from = chess.core.Move.getFromIndex(move);
        if (board[from] == Piece.EMPTY) {
            return Collections.unmodifiableList(tags);
        }

        String san = SAN.toAlgebraic(position, move);
        List<String> details = new ArrayList<>(6);
        MoveContext ctx = MoveContext.from(position, move, san);
        byte safetyTo = addPrimaryDetails(details, board, ctx);

        if (ctx.isCheck) {
            details.add(CHECK_TEXT);
        }
        if (ctx.isCheckmate) {
            details.add(CHECKMATE_TEXT);
        } else {
            Position next = position.copyOf().play(move);
            addSafetyDetail(details, board, next, board[from], from, safetyTo);
            if (!next.inCheck() && next.getMoves().isEmpty()) {
                details.add(STALEMATE_TEXT);
            }
        }

        StringBuilder tag = new StringBuilder(32);
        tag.append(MOVE_TAG_PREFIX).append(san);
        if (!details.isEmpty()) {
            tag.append(OPEN_PAREN_PREFIX).append(String.join(COMMA_SPACE, details)).append(CLOSE_PAREN);
        }
        tags.add(tag.toString());

        return Collections.unmodifiableList(tags);
    }

    /**
     * Adds the primary move description for a move.
     *
     * @param details the mutable detail list
     * @param board the position board before the move
     * @param ctx the parsed move context
     * @return the target square used for subsequent safety analysis
     */
    private static byte addPrimaryDetails(List<String> details, byte[] board, MoveContext ctx) {
        if (ctx.isCastleKingside || ctx.isCastleQueenside) {
            details.add(formatCastleDetail(ctx.position, ctx.from, ctx.isCastleKingside));
            return resolveCastleKingTarget(ctx.position, ctx.isCastleKingside);
        }
        details.add(formatMoveDetail(board[ctx.from], ctx.from, ctx.to));
        addCaptureAndPromotionDetails(details, board, ctx);
        return ctx.to;
    }

    /**
     * Adds capture and promotion details to the move description.
     *
     * @param details the mutable detail list
     * @param board the position board before the move
     * @param ctx the parsed move context
     */
    private static void addCaptureAndPromotionDetails(List<String> details, byte[] board, MoveContext ctx) {
        if (ctx.isEnPassant) {
            details.add(formatEnPassantDetail(board, board[ctx.from], ctx.to));
        } else if (ctx.position.isCapture(ctx.from, ctx.to)) {
            details.add(formatCaptureDetail(board[ctx.to], ctx.to));
        }
        if (chess.core.Move.isPromotion(ctx.move)) {
            details.add(formatPromotionDetail(ctx.move));
            if (chess.core.Move.isUnderPromotion(ctx.move)) {
                details.add(UNDERPROMOTION);
            }
        }
    }

    /**
     * Adds safety-related details based on attacker counts before and after the move.
     *
     * @param details the mutable detail list
     * @param board the position board before the move
     * @param next the position after the move
     * @param movingPiece the moving piece code
     * @param from the source square
     * @param to the target square
     */
    private static void addSafetyDetail(List<String> details, byte[] board, Position next, byte movingPiece,
            byte from, byte to) {
        boolean movingWhite = Piece.isWhite(movingPiece);
        boolean attackedBefore = AttackUtils.countAttackers(board, !movingWhite, from) > 0;
        byte[] nextBoard = next.getBoard();
        boolean attackedAfter = AttackUtils.countAttackers(nextBoard, !movingWhite, to) > 0;
        if (attackedBefore && !attackedAfter) {
            details.add(ESCAPES_ATTACK);
        } else if (!attackedBefore && attackedAfter) {
            details.add(MOVES_INTO_ATTACK);
        }
    }

    /**
     * Formats the basic move description.
     *
     * @param piece the moving piece
     * @param from the source square
     * @param to the target square
     * @return the formatted move detail
     */
    private static String formatMoveDetail(byte piece, byte from, byte to) {
        return Text.colorNameLower(piece) + SPACE_TEXT + Text.pieceNameLower(piece) + SPACE_TEXT
                + Text.squareNameLower(from) + TO + Text.squareNameLower(to);
    }

    /**
     * Formats a capture description.
     *
     * @param targetPiece the captured piece, or {@code EMPTY} when the target square was empty
     * @param to the capture square
     * @return the formatted capture detail
     */
    private static String formatCaptureDetail(byte targetPiece, byte to) {
        if (targetPiece == Piece.EMPTY) {
            return CAPTURES_ON_PREFIX + Text.squareNameLower(to);
        }
        return CAPTURES_PREFIX + Text.colorNameLower(targetPiece) + SPACE_TEXT + Text.pieceNameLower(targetPiece)
                + SPACE_TEXT
                + Text.squareNameLower(to);
    }

    /**
     * Formats an en-passant capture description.
     *
     * @param board the position board before the move
     * @param movingPiece the moving pawn
     * @param target the target square
     * @return the formatted en-passant detail
     */
    private static String formatEnPassantDetail(byte[] board, byte movingPiece, byte target) {
        byte captureSquare = Piece.isWhite(movingPiece) ? Field.uprank(target) : Field.downrank(target);
        byte capturedPiece = board[captureSquare];
        String color = capturedPiece == Piece.EMPTY ? oppositeColor(movingPiece) : Text.colorNameLower(capturedPiece);
        return CAPTURES_EN_PASSANT_PREFIX + color + PAWN_SUFFIX + Text.squareNameLower(captureSquare);
    }

    /**
     * Returns the opposite color name for the given piece.
     *
     * @param piece the piece used to determine the current color
     * @return the opposite color name
     */
    private static String oppositeColor(byte piece) {
        return Piece.isWhite(piece) ? BLACK : WHITE;
    }

    /**
     * Formats a promotion description.
     *
     * @param move the promotion move
     * @return the formatted promotion detail
     */
    private static String formatPromotionDetail(short move) {
        String promotion;
        switch (chess.core.Move.getPromotion(move)) {
            case 1:
                promotion = KNIGHT;
                break;
            case 2:
                promotion = BISHOP;
                break;
            case 3:
                promotion = ROOK;
                break;
            case 4:
                promotion = QUEEN;
                break;
            default:
                promotion = PIECE_KEY;
                break;
        }
        return PROMOTION_TO_PREFIX + promotion;
    }

    /**
     * Formats a castling description for a move.
     *
     * @param position the position before castling
     * @param kingFrom the king's source square
     * @param kingside whether the castle is kingside
     * @return the formatted castling detail
     */
    private static String formatCastleDetail(Position position, byte kingFrom, boolean kingside) {
        CastleInfo castle = CastleInfo.from(position, kingside);
        StringBuilder detail = new StringBuilder(64);
        detail.append(position.isWhiteTurn() ? WHITE : BLACK)
                .append(SPACE_TEXT).append(CASTLE_WORD).append(SPACE_TEXT)
                .append(kingside ? KINGSIDE : QUEENSIDE)
                .append(COLON_SPACE).append(KING_NAME).append(SPACE_TEXT)
                .append(Text.squareNameLower(kingFrom))
                .append(TO)
                .append(Text.squareNameLower(castle.kingTo));
        if (castle.rookFrom != Field.NO_SQUARE) {
            detail.append(COMMA_SPACE).append(ROOK_WORD).append(SPACE_TEXT)
                    .append(Text.squareNameLower(castle.rookFrom))
                    .append(TO)
                    .append(Text.squareNameLower(castle.rookTo));
        }
        return detail.toString();
    }

    /**
     * Resolves the king destination square for castling.
     *
     * @param position the position before castling
     * @param kingside whether the castle is kingside
     * @return the king destination square
     */
    private static byte resolveCastleKingTarget(Position position, boolean kingside) {
        if (position.isWhiteTurn()) {
            return kingside ? Field.WHITE_KINGSIDE_CASTLE_KING_TO_INDEX : Field.WHITE_QUEENSIDE_CASTLE_KING_TO_INDEX;
        }
        return kingside ? Field.BLACK_KINGSIDE_CASTLE_KING_TO_INDEX : Field.BLACK_QUEENSIDE_CASTLE_KING_TO_INDEX;
    }

    /**
     * Builds move tags for every move in a move list.
     *
     * @param position the position before the first move
     * @param moves the moves to describe
     * @return an immutable list containing all move tags
     * @throws NullPointerException if {@code position} or {@code moves} is {@code null}
     */
    public static List<String> tags(Position position, MoveList moves) {
        Objects.requireNonNull(position, POSITION);
        Objects.requireNonNull(moves, MOVES);
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            tags.addAll(tags(position, moves.get(i)));
        }
        return Collections.unmodifiableList(tags);
    }

    /**
     * Captures parsed move properties used while building a description.
 * @author Lennart A. Conrad
 * @since 2026
     */
    private static final class MoveContext {

        /**
         * The position before the move is applied.
         */
        private final Position position;

        /**
         * The encoded move.
         */
        private final short move;

        /**
         * The source square.
         */
        private final byte from;

        /**
         * The target square.
         */
        private final byte to;

        /**
         * Whether the move is an en-passant capture.
         */
        private final boolean isEnPassant;

        /**
         * Whether the move is queenside castling.
         */
        private final boolean isCastleQueenside;

        /**
         * Whether the move is kingside castling.
         */
        private final boolean isCastleKingside;

        /**
         * Whether the SAN notation indicates checkmate.
         */
        private final boolean isCheckmate;

        /**
         * Whether the SAN notation indicates check.
         */
        private final boolean isCheck;

        /**
         * Creates a parsed move context from SAN and move metadata.
         *
         * @param position the position before the move
         * @param move the encoded move
         * @param san the SAN representation of the move
         */
        private MoveContext(Position position, short move, String san) {
            byte fromIndex = chess.core.Move.getFromIndex(move);
            byte toIndex = chess.core.Move.getToIndex(move);
            boolean castleQueenside = san.startsWith(CASTLE_SAN_QUEENSIDE);
            boolean castleKingside = !castleQueenside && san.startsWith(CASTLE_SAN_KINGSIDE);
            boolean checkmate = san.endsWith(CHECKMATE_SUFFIX);
            this.position = position;
            this.move = move;
            this.from = fromIndex;
            this.to = toIndex;
            this.isEnPassant = position.isEnPassantCapture(fromIndex, toIndex);
            this.isCastleQueenside = castleQueenside;
            this.isCastleKingside = castleKingside;
            this.isCheckmate = checkmate;
            this.isCheck = checkmate || san.endsWith(CHECK_SUFFIX);
        }

        /**
         * Creates a move context for the given move.
         *
         * @param position the position before the move
         * @param move the encoded move
         * @param san the SAN representation of the move
         * @return the parsed move context
         */
        private static MoveContext from(Position position, short move, String san) {
            return new MoveContext(position, move, san);
        }
    }

    /**
     * Holds castling destination squares and rook movement details.
 * @author Lennart A. Conrad
 * @since 2026
     */
    private static final class CastleInfo {

        /**
         * The king destination square.
         */
        private final byte kingTo;

        /**
         * The rook source square.
         */
        private final byte rookFrom;

        /**
         * The rook destination square.
         */
        private final byte rookTo;

        /**
         * Creates a castling movement description.
         *
         * @param kingTo the king destination square
         * @param rookFrom the rook source square
         * @param rookTo the rook destination square
         */
        private CastleInfo(byte kingTo, byte rookFrom, byte rookTo) {
            this.kingTo = kingTo;
            this.rookFrom = rookFrom;
            this.rookTo = rookTo;
        }

        /**
         * Builds castling metadata for the current side to move.
         *
         * @param position the position before castling
         * @param kingside whether the castle is kingside
         * @return the resolved castling information
         */
        private static CastleInfo from(Position position, boolean kingside) {
            return position.isWhiteTurn()
                    ? whiteCastleInfo(position, kingside)
                    : blackCastleInfo(position, kingside);
        }

        /**
         * Builds White castling metadata.
         *
         * @param position the position before castling
         * @param kingside whether the castle is kingside
         * @return the resolved castling information
         */
        private static CastleInfo whiteCastleInfo(Position position, boolean kingside) {
            byte kingTo = kingside ? Field.WHITE_KINGSIDE_CASTLE_KING_TO_INDEX : Field.WHITE_QUEENSIDE_CASTLE_KING_TO_INDEX;
            byte rookTo = kingside ? Field.WHITE_KINGSIDE_CASTLE_ROOK_TO_INDEX : Field.WHITE_QUEENSIDE_CASTLE_ROOK_TO_INDEX;
            byte rookFrom = position.isChess960()
                    ? selectRook(position.getWhiteKingside(), position.getWhiteQueenside(), kingside)
                    : selectRook(Field.WHITE_KINGSIDE_CASTLE_ROOK_FROM_INDEX,
                            Field.WHITE_QUEENSIDE_CASTLE_ROOK_FROM_INDEX, kingside);
            return new CastleInfo(kingTo, rookFrom, rookTo);
        }

        /**
         * Builds Black castling metadata.
         *
         * @param position the position before castling
         * @param kingside whether the castle is kingside
         * @return the resolved castling information
         */
        private static CastleInfo blackCastleInfo(Position position, boolean kingside) {
            byte kingTo = kingside ? Field.BLACK_KINGSIDE_CASTLE_KING_TO_INDEX : Field.BLACK_QUEENSIDE_CASTLE_KING_TO_INDEX;
            byte rookTo = kingside ? Field.BLACK_KINGSIDE_CASTLE_ROOK_TO_INDEX : Field.BLACK_QUEENSIDE_CASTLE_ROOK_TO_INDEX;
            byte rookFrom = position.isChess960()
                    ? selectRook(position.getBlackKingside(), position.getBlackQueenside(), kingside)
                    : selectRook(Field.BLACK_KINGSIDE_CASTLE_ROOK_FROM_INDEX,
                            Field.BLACK_QUEENSIDE_CASTLE_ROOK_FROM_INDEX, kingside);
            return new CastleInfo(kingTo, rookFrom, rookTo);
        }

        /**
         * Selects the rook square for the requested castling side.
         *
         * @param kingsideRook the kingside rook square
         * @param queensideRook the queenside rook square
         * @param kingside whether the castle is kingside
         * @return the rook square to use
         */
        private static byte selectRook(byte kingsideRook, byte queensideRook, boolean kingside) {
            return kingside ? kingsideRook : queensideRook;
        }
    }

}
