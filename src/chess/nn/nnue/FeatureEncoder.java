package chess.nn.nnue;

import java.util.Arrays;

import chess.core.Piece;
import chess.core.Position;

/**
 * Encodes a {@link Position} into sparse HalfKP-style NNUE features.
 *
 * <p>
 * Each perspective has its own active feature list. A feature is keyed by the
 * perspective king square, a relative piece plane, and the piece square:
 * </p>
 *
 * <pre>
 * feature = ((kingSquare * 10 + piecePlane) * 64) + pieceSquare
 * </pre>
 *
 * <p>
 * Piece planes are relative to the perspective: own pawn/knight/bishop/rook/queen
 * followed by enemy pawn/knight/bishop/rook/queen. Kings are represented only by
 * the king-square bucket and are not emitted as piece features.
 * </p>
 */
public final class FeatureEncoder {

    /**
     * Number of board squares.
     */
    public static final int SQUARES = 64;

    /**
     * Non-king piece planes per perspective.
     */
    public static final int PIECE_PLANES = 10;

    /**
     * Maximum active non-king piece features per perspective.
     */
    public static final int MAX_ACTIVE_FEATURES = 30;

    /**
     * Total sparse input feature count.
     */
    public static final int FEATURE_COUNT = SQUARES * PIECE_PLANES * SQUARES;

    /**
     * Own pawn plane.
     */
    public static final int OWN_PAWN = 0;

    /**
     * Own knight plane.
     */
    public static final int OWN_KNIGHT = 1;

    /**
     * Own bishop plane.
     */
    public static final int OWN_BISHOP = 2;

    /**
     * Own rook plane.
     */
    public static final int OWN_ROOK = 3;

    /**
     * Own queen plane.
     */
    public static final int OWN_QUEEN = 4;

    /**
     * Enemy pawn plane.
     */
    public static final int ENEMY_PAWN = 5;

    /**
     * Enemy knight plane.
     */
    public static final int ENEMY_KNIGHT = 6;

    /**
     * Enemy bishop plane.
     */
    public static final int ENEMY_BISHOP = 7;

    /**
     * Enemy rook plane.
     */
    public static final int ENEMY_ROOK = 8;

    /**
     * Enemy queen plane.
     */
    public static final int ENEMY_QUEEN = 9;

    /**
     * Prevents instantiation.
     */
    private FeatureEncoder() {
        // utility
    }

    /**
     * Returns all active features for one side's perspective.
     *
     * @param position position to encode
     * @param whitePerspective true for White's perspective, false for Black's
     * @return active feature indices
     */
    public static int[] activeFeatures(Position position, boolean whitePerspective) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }

        byte[] board = position.getBoard();
        int kingSquare = perspectiveKingSquare(position, whitePerspective);
        int[] features = new int[MAX_ACTIVE_FEATURES];
        int count = 0;

        for (int positionIndex = 0; positionIndex < board.length; positionIndex++) {
            byte piece = board[positionIndex];
            int plane = piecePlane(piece, whitePerspective);
            if (plane < 0) {
                continue;
            }
            if (count == features.length) {
                features = Arrays.copyOf(features, features.length + 8);
            }
            int square = orientSquare(squareFromPositionIndex(positionIndex), whitePerspective);
            features[count++] = encodeFeature(kingSquare, plane, square);
        }

        return Arrays.copyOf(features, count);
    }

    /**
     * Encodes one feature from already-oriented components.
     *
     * @param kingSquare oriented perspective king square in a1..h8 order
     * @param piecePlane relative non-king piece plane
     * @param pieceSquare oriented piece square in a1..h8 order
     * @return sparse feature index
     */
    public static int encodeFeature(int kingSquare, int piecePlane, int pieceSquare) {
        requireSquare(kingSquare, "kingSquare");
        requireSquare(pieceSquare, "pieceSquare");
        if (piecePlane < 0 || piecePlane >= PIECE_PLANES) {
            throw new IllegalArgumentException("piecePlane out of range: " + piecePlane);
        }
        return ((kingSquare * PIECE_PLANES + piecePlane) * SQUARES) + pieceSquare;
    }

    /**
     * Converts {@link Position}'s square order ({@code a8..h1}) to the NNUE square
     * order ({@code a1..h8}).
     *
     * @param positionIndex index in {@link Position}'s board array
     * @return square in a1..h8 order
     */
    public static int squareFromPositionIndex(int positionIndex) {
        requireSquare(positionIndex, "positionIndex");
        int rankFromTop = positionIndex >>> 3;
        int file = positionIndex & 7;
        return ((7 - rankFromTop) << 3) | file;
    }

    /**
     * Orients an a1..h8 square for a perspective.
     *
     * <p>
     * White perspective is unchanged. Black perspective mirrors ranks so Black's
     * home rank maps to rank 1.
     * </p>
     *
     * @param square square in a1..h8 order
     * @param whitePerspective true for White's perspective, false for Black's
     * @return oriented square
     */
    public static int orientSquare(int square, boolean whitePerspective) {
        requireSquare(square, "square");
        return whitePerspective ? square : (square ^ 56);
    }

    /**
     * Returns the relative non-king piece plane for a piece.
     *
     * @param piece piece code from {@link Piece}
     * @param whitePerspective true for White's perspective, false for Black's
     * @return plane index, or {@code -1} for empty squares and kings
     */
    public static int piecePlane(byte piece, boolean whitePerspective) {
        if (piece == Piece.EMPTY || Piece.isKing(piece)) {
            return -1;
        }

        boolean own = whitePerspective ? Piece.isWhite(piece) : Piece.isBlack(piece);
        int offset = own ? 0 : 5;
        return switch (Math.abs(piece)) {
        case Piece.PAWN -> offset + OWN_PAWN;
        case Piece.KNIGHT -> offset + OWN_KNIGHT;
        case Piece.BISHOP -> offset + OWN_BISHOP;
        case Piece.ROOK -> offset + OWN_ROOK;
        case Piece.QUEEN -> offset + OWN_QUEEN;
        default -> -1;
        };
    }

    /**
     * Returns the perspective king square in oriented a1..h8 order.
     *
     * @param position position to inspect
     * @param whitePerspective true for White's perspective, false for Black's
     * @return oriented king square
     */
    public static int perspectiveKingSquare(Position position, boolean whitePerspective) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        int index = whitePerspective ? position.kingSquare(true) : position.kingSquare(false);
        if (index < 0) {
            throw new IllegalArgumentException("Position is missing the perspective king.");
        }
        return orientSquare(squareFromPositionIndex(index), whitePerspective);
    }

    /**
     * Validates a square index.
     *
     * @param square square index
     * @param label argument label
     */
    private static void requireSquare(int square, String label) {
        if (square < 0 || square >= SQUARES) {
            throw new IllegalArgumentException(label + " out of range: " + square);
        }
    }
}
