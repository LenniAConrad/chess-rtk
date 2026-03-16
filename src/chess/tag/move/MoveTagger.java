package chess.tag.move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.tag.core.AttackUtils;
import chess.tag.core.Text;

/**
 * Generates compact move-centric tags that describe tactical outcomes.
 *
 * <p>
 * Each tag is emitted as a single sentence containing the SAN move plus a compact
 * list of features, e.g. {@code "move: Bxg4+ (white bishop e2 to g4, captures black pawn g4, check)"}.
 * The tagger looks only at the supplied position and move list; it does not
 * attempt to validate illegal moves.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MoveTagger {

    /**
     * Utility class, prevents instantiation.
     */
    private MoveTagger() {
        // Utility class
    }

    /**
     * Produces tags for a single move.
     *
     * @param position position to evaluate from
     * @param move move to evaluate
     * @return immutable list of tags for the move
     */
    public static List<String> tags(Position position, short move) {
        Objects.requireNonNull(position, "position");
        List<String> tags = new ArrayList<>(1);
        byte[] board = position.getBoard();
        byte from = Move.getFromIndex(move);
        byte to = Move.getToIndex(move);
        if (board[from] == Piece.EMPTY) {
            return Collections.unmodifiableList(tags);
        }

        String san = SAN.toAlgebraic(position, move);
        List<String> details = new ArrayList<>(6);
        boolean isEnPassant = position.isEnPassantCapture(from, to);
        boolean isCastleQueenside = san.startsWith("O-O-O");
        boolean isCastleKingside = !isCastleQueenside && san.startsWith("O-O");
        byte safetyTo = to;
        if (isCastleKingside || isCastleQueenside) {
            details.add(formatCastleDetail(position, from, isCastleKingside));
            safetyTo = resolveCastleKingTarget(position, isCastleKingside);
        } else {
            details.add(formatMoveDetail(board[from], from, to));
            if (isEnPassant) {
                details.add(formatEnPassantDetail(board, board[from], to));
            } else if (position.isCapture(from, to)) {
                details.add(formatCaptureDetail(board[to], to));
            }
            if (Move.isPromotion(move)) {
                details.add(formatPromotionDetail(move));
                if (Move.isUnderPromotion(move)) {
                    details.add("underpromotion");
                }
            }
        }

        boolean isCheckmate = san.endsWith("#");
        boolean isCheck = isCheckmate || san.endsWith("+");
        if (isCheck) {
            details.add("check");
        }
        if (isCheckmate) {
            details.add("checkmate");
        } else {
            Position next = position.copyOf().play(move);
            addSafetyDetail(details, board, next, board[from], from, safetyTo);
            if (!next.inCheck() && next.getMoves().isEmpty()) {
                details.add("stalemate");
            }
        }

        StringBuilder tag = new StringBuilder(32);
        tag.append("move: ").append(san);
        if (!details.isEmpty()) {
            tag.append(" (").append(String.join(", ", details)).append(")");
        }
        tags.add(tag.toString());

        return Collections.unmodifiableList(tags);
    }

    private static void addSafetyDetail(List<String> details, byte[] board, Position next, byte movingPiece,
            byte from, byte to) {
        boolean movingWhite = Piece.isWhite(movingPiece);
        boolean attackedBefore = AttackUtils.countAttackers(board, !movingWhite, from) > 0;
        byte[] nextBoard = next.getBoard();
        boolean attackedAfter = AttackUtils.countAttackers(nextBoard, !movingWhite, to) > 0;
        if (attackedBefore && !attackedAfter) {
            details.add("escapes attack");
        } else if (!attackedBefore && attackedAfter) {
            details.add("moves into attack");
        }
    }

    private static String formatMoveDetail(byte piece, byte from, byte to) {
        return Text.colorNameLower(piece) + " " + Text.pieceNameLower(piece) + " "
                + Text.squareNameLower(from) + " to " + Text.squareNameLower(to);
    }

    private static String formatCaptureDetail(byte targetPiece, byte to) {
        if (targetPiece == Piece.EMPTY) {
            return "captures on " + Text.squareNameLower(to);
        }
        return "captures " + Text.colorNameLower(targetPiece) + " " + Text.pieceNameLower(targetPiece) + " "
                + Text.squareNameLower(to);
    }

    private static String formatEnPassantDetail(byte[] board, byte movingPiece, byte target) {
        byte captureSquare = Piece.isWhite(movingPiece) ? Field.uprank(target) : Field.downrank(target);
        byte capturedPiece = board[captureSquare];
        String color = capturedPiece == Piece.EMPTY
                ? (Piece.isWhite(movingPiece) ? "black" : "white")
                : Text.colorNameLower(capturedPiece);
        return "captures en passant " + color + " pawn " + Text.squareNameLower(captureSquare);
    }

    private static String formatPromotionDetail(short move) {
        String promotion;
        switch (Move.getPromotion(move)) {
            case 1:
                promotion = "knight";
                break;
            case 2:
                promotion = "bishop";
                break;
            case 3:
                promotion = "rook";
                break;
            case 4:
                promotion = "queen";
                break;
            default:
                promotion = "piece";
                break;
        }
        return "promotion to " + promotion;
    }

    private static String formatCastleDetail(Position position, byte kingFrom, boolean kingside) {
        boolean white = position.isWhiteTurn();
        byte kingTo;
        byte rookFrom;
        byte rookTo;
        if (white) {
            kingTo = kingside ? Field.WHITE_KINGSIDE_CASTLE_KING_TO_INDEX : Field.WHITE_QUEENSIDE_CASTLE_KING_TO_INDEX;
            rookTo = kingside ? Field.WHITE_KINGSIDE_CASTLE_ROOK_TO_INDEX : Field.WHITE_QUEENSIDE_CASTLE_ROOK_TO_INDEX;
            rookFrom = position.isChess960()
                    ? (kingside ? position.getWhiteKingside() : position.getWhiteQueenside())
                    : (kingside ? Field.WHITE_KINGSIDE_CASTLE_ROOK_FROM_INDEX : Field.WHITE_QUEENSIDE_CASTLE_ROOK_FROM_INDEX);
        } else {
            kingTo = kingside ? Field.BLACK_KINGSIDE_CASTLE_KING_TO_INDEX : Field.BLACK_QUEENSIDE_CASTLE_KING_TO_INDEX;
            rookTo = kingside ? Field.BLACK_KINGSIDE_CASTLE_ROOK_TO_INDEX : Field.BLACK_QUEENSIDE_CASTLE_ROOK_TO_INDEX;
            rookFrom = position.isChess960()
                    ? (kingside ? position.getBlackKingside() : position.getBlackQueenside())
                    : (kingside ? Field.BLACK_KINGSIDE_CASTLE_ROOK_FROM_INDEX : Field.BLACK_QUEENSIDE_CASTLE_ROOK_FROM_INDEX);
        }
        StringBuilder detail = new StringBuilder(64);
        detail.append(white ? "white" : "black")
                .append(" castle ")
                .append(kingside ? "kingside" : "queenside")
                .append(": king ")
                .append(Text.squareNameLower(kingFrom))
                .append(" to ")
                .append(Text.squareNameLower(kingTo));
        if (rookFrom != Field.NO_SQUARE) {
            detail.append(", rook ")
                    .append(Text.squareNameLower(rookFrom))
                    .append(" to ")
                    .append(Text.squareNameLower(rookTo));
        }
        return detail.toString();
    }

    private static byte resolveCastleKingTarget(Position position, boolean kingside) {
        if (position.isWhiteTurn()) {
            return kingside ? Field.WHITE_KINGSIDE_CASTLE_KING_TO_INDEX : Field.WHITE_QUEENSIDE_CASTLE_KING_TO_INDEX;
        }
        return kingside ? Field.BLACK_KINGSIDE_CASTLE_KING_TO_INDEX : Field.BLACK_QUEENSIDE_CASTLE_KING_TO_INDEX;
    }

    /**
     * Produces tags for every move in {@code moves}.
     *
     * @param position position to evaluate from
     * @param moves list of candidate moves
     * @return immutable list of tags across all moves
     */
    public static List<String> tags(Position position, MoveList moves) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(moves, "moves");
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            tags.addAll(tags(position, moves.get(i)));
        }
        return Collections.unmodifiableList(tags);
    }

}
