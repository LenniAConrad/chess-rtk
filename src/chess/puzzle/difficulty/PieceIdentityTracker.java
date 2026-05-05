package chess.puzzle.difficulty;

import java.util.Arrays;

import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;

/**
 * Tracks stable piece identities through an explicit puzzle solution tree.
 *
 * <p>
 * The tracker starts from the root FEN, assigns each piece an identity based on
 * its root square and piece index, and then carries those identities forward as
 * moves are applied. This prevents one piece that moves through several source
 * squares from being counted as several different pieces.
 * </p>
 *
 * <p>
 * <strong>Warning:</strong> identities are stable only within the explicit tree
 * rooted at one puzzle position. They are not historical game-piece identities
 * before the root FEN.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PieceIdentityTracker {

    /**
     * Sentinel for an empty or unknown identity.
     */
    public static final long NO_IDENTITY = 0L;

    /**
     * Number of board squares.
     */
    private static final int BOARD_SQUARES = 64;

    /**
     * Per-square identity table.
     */
    private final long[] identities;

    /**
     * Creates a tracker from a per-square identity table.
     *
     * @param identities per-square identity table
     */
    private PieceIdentityTracker(long[] identities) {
        this.identities = identities;
    }

    /**
     * Creates identities for all pieces in a root position.
     *
     * @param position root position
     * @return tracker for the root position
     */
    public static PieceIdentityTracker from(Position position) {
        long[] ids = new long[BOARD_SQUARES];
        if (position == null) {
            return new PieceIdentityTracker(ids);
        }
        for (int square = 0; square < BOARD_SQUARES; square++) {
            int piece = position.pieceAt(square);
            if (piece != Piece.EMPTY) {
                ids[square] = initialIdentity(piece, square);
            }
        }
        return new PieceIdentityTracker(ids);
    }

    /**
     * Returns the stable identity of the moving piece for a move.
     *
     * @param position position before the move
     * @param move encoded move
     * @return stable identity, or {@link #NO_IDENTITY}
     */
    public long movingIdentity(Position position, short move) {
        if (position == null || move == Move.NO_MOVE) {
            return NO_IDENTITY;
        }
        int from = Move.getFromIndex(move);
        if (!validSquare(from)) {
            return NO_IDENTITY;
        }
        long identity = identities[from];
        if (identity != NO_IDENTITY) {
            return identity;
        }
        int piece = position.pieceAt(from);
        return piece == Piece.EMPTY ? NO_IDENTITY : initialIdentity(piece, from);
    }

    /**
     * Applies a move and returns the resulting identity tracker.
     *
     * @param before position before the move
     * @param move encoded move
     * @return tracker for the position after the move
     */
    public PieceIdentityTracker after(Position before, short move) {
        if (before == null || move == Move.NO_MOVE) {
            return new PieceIdentityTracker(Arrays.copyOf(identities, identities.length));
        }
        try {
            return after(before, move, before.copy().play(move));
        } catch (RuntimeException ex) {
            return new PieceIdentityTracker(Arrays.copyOf(identities, identities.length));
        }
    }

    /**
     * Applies a move using a caller-provided after-position.
     *
     * @param before position before the move
     * @param move encoded move
     * @param after position after the move
     * @return tracker for the position after the move
     */
    public PieceIdentityTracker after(Position before, short move, Position after) {
        if (before == null || after == null || move == Move.NO_MOVE) {
            return new PieceIdentityTracker(Arrays.copyOf(identities, identities.length));
        }
        long[] next = new long[BOARD_SQUARES];
        boolean[] usedBefore = new boolean[BOARD_SQUARES];
        int from = Move.getFromIndex(move);
        int actualTo = before.actualToSquare(move);
        long movingIdentity = movingIdentity(before, move);

        keepUnchangedPieces(before, after, next, usedBefore, from);
        if (validSquare(actualTo) && after.pieceAt(actualTo) != Piece.EMPTY && movingIdentity != NO_IDENTITY) {
            next[actualTo] = movingIdentity;
            if (validSquare(from)) {
                usedBefore[from] = true;
            }
        }
        carryOtherMovedPieces(before, after, next, usedBefore);
        return new PieceIdentityTracker(next);
    }

    /**
     * Keeps identities for pieces that did not move.
     *
     * @param before position before the move
     * @param after position after the move
     * @param next destination identity table
     * @param usedBefore consumed before-squares
     * @param from moving-piece source square
     */
    private void keepUnchangedPieces(Position before, Position after, long[] next, boolean[] usedBefore, int from) {
        for (int square = 0; square < BOARD_SQUARES; square++) {
            if (square == from) {
                continue;
            }
            int beforePiece = before.pieceAt(square);
            if (beforePiece != Piece.EMPTY && beforePiece == after.pieceAt(square)) {
                next[square] = identityOrInitial(beforePiece, square);
                usedBefore[square] = true;
            }
        }
    }

    /**
     * Carries identities for secondary moved pieces such as castling rooks.
     *
     * @param before position before the move
     * @param after position after the move
     * @param next destination identity table
     * @param usedBefore consumed before-squares
     */
    private void carryOtherMovedPieces(Position before, Position after, long[] next, boolean[] usedBefore) {
        for (int square = 0; square < BOARD_SQUARES; square++) {
            int piece = after.pieceAt(square);
            if (piece == Piece.EMPTY || next[square] != NO_IDENTITY) {
                continue;
            }
            int source = findUnusedSource(before, usedBefore, piece);
            if (source >= 0) {
                next[square] = identityOrInitial(piece, source);
                usedBefore[source] = true;
            } else {
                next[square] = initialIdentity(piece, square);
            }
        }
    }

    /**
     * Finds an unused before-square containing a given piece.
     *
     * @param before position before the move
     * @param usedBefore consumed before-squares
     * @param piece piece index to match
     * @return source square, or {@link Field#NO_SQUARE}
     */
    private static int findUnusedSource(Position before, boolean[] usedBefore, int piece) {
        for (int square = 0; square < BOARD_SQUARES; square++) {
            if (!usedBefore[square] && before.pieceAt(square) == piece) {
                return square;
            }
        }
        return Field.NO_SQUARE;
    }

    /**
     * Returns a tracked identity or root-position fallback identity.
     *
     * @param piece piece index
     * @param square source square
     * @return non-zero identity
     */
    private long identityOrInitial(int piece, int square) {
        if (validSquare(square) && identities[square] != NO_IDENTITY) {
            return identities[square];
        }
        return initialIdentity(piece, square);
    }

    /**
     * Builds the root-position identity for one piece.
     *
     * @param piece piece index
     * @param square square index
     * @return non-zero identity
     */
    private static long initialIdentity(int piece, int square) {
        return ((long) (piece + 8) << 7) | (square + 1L);
    }

    /**
     * Returns whether a square index is valid.
     *
     * @param square square index
     * @return true for ordinary board squares
     */
    private static boolean validSquare(int square) {
        return square >= 0 && square < BOARD_SQUARES;
    }
}
