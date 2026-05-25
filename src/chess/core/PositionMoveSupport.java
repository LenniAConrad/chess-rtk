package chess.core;

import static chess.core.Position.*;

/**
 * Package-local make/undo mechanics for {@link Position}.
 */
final class PositionMoveSupport {

    /**
     * Utility class; prevent instantiation.
     */
    private PositionMoveSupport() {
        // utility
    }

    /**
     * Returns whether a compact move value is usable by this position.
     * @param move candidate encoded move
     * @return true when the value is not the sentinel and has a valid promotion
     */
    static boolean isEncodedMove(short move) {
        if (move == Move.NO_MOVE || move < 0) {
            return false;
        }
        return ((move >>> 12) & 0x7) <= PROMOTION_QUEEN;
    }

    /**
     * Applies a legal or pseudo-legal move and fills caller-owned undo state.
     * @param position position to mutate
     * @param move encoded move
     * @param state undo state
     * @return mutated position
     */
    static Position play(Position position, short move, Position.State state) {
        int from = move & 0x3F;
        int to = (move >>> 6) & 0x3F;
        int promotion = (move >>> 12) & 0x7;
        int moving = position.pieceIndexAt(from);
        if (moving < 0) {
            throw new IllegalArgumentException("No piece on " + Bits.name(from));
        }

        boolean white = moving < BLACK_PAWN;
        int castleRight = castlingRightForMove(position, moving, to);
        boolean castleMove = castleRight != 0;
        int actualTo = castleMove ? position.castlingKingTarget(castleRight) : to;
        int captured = castleMove ? -1 : position.pieceIndexAt(actualTo);
        int capturedSquare = actualTo;
        boolean capture = !castleMove && captured >= 0;
        boolean pawnMove = moving == WHITE_PAWN || moving == BLACK_PAWN;
        saveUndoState(position, state, moving, captured, capturedSquare, actualTo);

        if (position.castlingRights != 0) {
            position.castlingRights &= castlingKeepMask(position, from, to);
        }

        PositionStateSupport.clearPiece(position, moving, from);
        if (captured >= 0) {
            PositionStateSupport.clearPiece(position, captured, actualTo);
        }

        if (pawnMove && actualTo == position.enPassantSquare && captured < 0) {
            applyEnPassantCapture(position, state, white, actualTo);
            capture = true;
        }

        int delta = actualTo - from;
        if (castleMove) {
            moveCastlingRook(position, white, castleRight, state);
            state.castle = true;
        }

        int placed = promotion == 0 ? moving : PositionStateSupport.promotionPieceIndex(moving, promotion);
        PositionStateSupport.setPiece(position, placed, actualTo);
        position.enPassantSquare = nextEnPassantSquare(position, pawnMove, delta, from, actualTo, white);
        updateHalfMoveClock(position, pawnMove || capture);
        if (!position.whiteToMove) {
            position.fullMoveNumber++;
        }
        position.whiteToMove = !position.whiteToMove;
        return position;
    }

    /**
     * Undoes a move previously made with {@link #play(Position, short, Position.State)}.
     * @param position position to mutate
     * @param move encoded move
     * @param state undo state
     */
    static void undo(Position position, short move, Position.State state) {
        int from = move & 0x3F;
        int to = state.castle ? state.kingTo : (move >>> 6) & 0x3F;
        int promotion = (move >>> 12) & 0x7;
        int placed = promotion == 0 ? state.moving : PositionStateSupport.promotionPieceIndex(state.moving, promotion);
        boolean white = state.moving < BLACK_PAWN;
        long fromMask = 1L << from;
        long toMask = 1L << to;

        position.pieces[placed] &= ~toMask;
        position.board[to] = -1;
        if (white) {
            position.whiteOccupancy &= ~toMask;
        } else {
            position.blackOccupancy &= ~toMask;
        }
        position.occupancy &= ~toMask;
        if (state.rook >= 0) {
            PositionStateSupport.clearPiece(position, state.rook, state.rookTo);
        }
        position.pieces[state.moving] |= fromMask;
        position.board[from] = (byte) state.moving;
        if (white) {
            position.whiteOccupancy |= fromMask;
        } else {
            position.blackOccupancy |= fromMask;
        }
        position.occupancy |= fromMask;
        if (state.moving == WHITE_KING) {
            position.whiteKingSquare = (byte) from;
        } else if (state.moving == BLACK_KING) {
            position.blackKingSquare = (byte) from;
        }
        if (state.rook >= 0) {
            PositionStateSupport.setPiece(position, state.rook, state.rookFrom);
        }
        if (state.captured >= 0) {
            long capturedMask = 1L << state.capturedSquare;
            position.pieces[state.captured] |= capturedMask;
            position.board[state.capturedSquare] = (byte) state.captured;
            if (state.captured < BLACK_PAWN) {
                position.whiteOccupancy |= capturedMask;
            } else {
                position.blackOccupancy |= capturedMask;
            }
            position.occupancy |= capturedMask;
        }

        position.castlingRights = state.castlingRights;
        position.enPassantSquare = state.enPassantSquare;
        position.halfMoveClock = state.halfMoveClock;
        position.fullMoveNumber = state.fullMoveNumber;
        position.whiteToMove = state.whiteToMove;
    }

    /**
     * Applies a reversible null move.
     * @param position position to mutate
     * @param state undo state
     * @return mutated position
     */
    static Position playNull(Position position, Position.State state) {
        if (position.inCheck()) {
            throw new IllegalStateException("Cannot play a null move while in check");
        }
        saveNullUndoState(position, state);
        position.enPassantSquare = Field.NO_SQUARE;
        position.whiteToMove = !position.whiteToMove;
        return position;
    }

    /**
     * Undoes a null move.
     * @param position position to mutate
     * @param state undo state
     */
    static void undoNull(Position position, Position.State state) {
        position.castlingRights = state.castlingRights;
        position.enPassantSquare = state.enPassantSquare;
        position.halfMoveClock = state.halfMoveClock;
        position.fullMoveNumber = state.fullMoveNumber;
        position.whiteToMove = state.whiteToMove;
    }

    /**
     * Returns the castling right represented by a king move target.
     * @param position position context
     * @param moving moving piece index
     * @param target encoded move target
     * @return castling-right bit or zero
     */
    static int castlingRightForMove(Position position, int moving, int target) {
        if (moving == WHITE_KING) {
            if (position.canCastle(WHITE_KINGSIDE) && target == position.castlingMoveTarget(WHITE_KINGSIDE)) {
                return WHITE_KINGSIDE;
            }
            if (position.canCastle(WHITE_QUEENSIDE) && target == position.castlingMoveTarget(WHITE_QUEENSIDE)) {
                return WHITE_QUEENSIDE;
            }
        } else if (moving == BLACK_KING) {
            if (position.canCastle(BLACK_KINGSIDE) && target == position.castlingMoveTarget(BLACK_KINGSIDE)) {
                return BLACK_KINGSIDE;
            }
            if (position.canCastle(BLACK_QUEENSIDE) && target == position.castlingMoveTarget(BLACK_QUEENSIDE)) {
                return BLACK_QUEENSIDE;
            }
        }
        return 0;
    }

    private static void saveUndoState(Position position, Position.State state, int moving, int captured,
            int capturedSquare, int kingTo) {
        state.moving = moving;
        state.captured = captured;
        state.capturedSquare = capturedSquare;
        state.kingTo = kingTo;
        state.castlingRights = position.castlingRights;
        state.enPassantSquare = position.enPassantSquare;
        state.halfMoveClock = position.halfMoveClock;
        state.fullMoveNumber = position.fullMoveNumber;
        state.whiteToMove = position.whiteToMove;
        state.rook = -1;
        state.rookFrom = Field.NO_SQUARE;
        state.rookTo = Field.NO_SQUARE;
        state.enPassantCapture = false;
        state.castle = false;
    }

    private static void applyEnPassantCapture(Position position, Position.State state, boolean white, int actualTo) {
        int capturedSquare = white ? actualTo + 8 : actualTo - 8;
        int captured = white ? BLACK_PAWN : WHITE_PAWN;
        state.captured = captured;
        state.capturedSquare = capturedSquare;
        state.enPassantCapture = true;
        PositionStateSupport.clearPiece(position, captured, capturedSquare);
    }

    private static byte nextEnPassantSquare(Position position, boolean pawnMove, int delta, int from, int actualTo,
            boolean white) {
        if (!pawnMove || (delta != 16 && delta != -16)) {
            return Field.NO_SQUARE;
        }
        int target = (from + actualTo) / 2;
        long enemyPawns = position.pieces[white ? BLACK_PAWN : WHITE_PAWN];
        long attackers = white ? MoveGenerator.WHITE_PAWN_ATTACKS[target] : MoveGenerator.BLACK_PAWN_ATTACKS[target];
        return (attackers & enemyPawns) == 0L ? Field.NO_SQUARE : (byte) target;
    }

    private static void updateHalfMoveClock(Position position, boolean reset) {
        if (reset) {
            position.halfMoveClock = 0;
        } else {
            position.halfMoveClock++;
        }
    }

    private static void saveNullUndoState(Position position, Position.State state) {
        state.moving = -1;
        state.captured = -1;
        state.capturedSquare = Field.NO_SQUARE;
        state.kingTo = Field.NO_SQUARE;
        state.castlingRights = position.castlingRights;
        state.enPassantSquare = position.enPassantSquare;
        state.halfMoveClock = position.halfMoveClock;
        state.fullMoveNumber = position.fullMoveNumber;
        state.whiteToMove = position.whiteToMove;
        state.rook = -1;
        state.rookFrom = Field.NO_SQUARE;
        state.rookTo = Field.NO_SQUARE;
        state.enPassantCapture = false;
        state.castle = false;
    }

    private static int castlingKeepMask(Position position, int first, int second) {
        return castlingKeepMask(position, first) & castlingKeepMask(position, second);
    }

    private static int castlingKeepMask(Position position, int square) {
        int keep = WHITE_KINGSIDE | WHITE_QUEENSIDE | BLACK_KINGSIDE | BLACK_QUEENSIDE;
        if (square == position.whiteKingSquare) {
            keep &= ~(WHITE_KINGSIDE | WHITE_QUEENSIDE);
        }
        if (square == position.blackKingSquare) {
            keep &= ~(BLACK_KINGSIDE | BLACK_QUEENSIDE);
        }
        if (square == position.whiteKingsideRookSquare) {
            keep &= ~WHITE_KINGSIDE;
        }
        if (square == position.whiteQueensideRookSquare) {
            keep &= ~WHITE_QUEENSIDE;
        }
        if (square == position.blackKingsideRookSquare) {
            keep &= ~BLACK_KINGSIDE;
        }
        if (square == position.blackQueensideRookSquare) {
            keep &= ~BLACK_QUEENSIDE;
        }
        return keep;
    }

    private static void moveCastlingRook(Position position, boolean white, int right, Position.State state) {
        int rook = white ? WHITE_ROOK : BLACK_ROOK;
        int rookFrom = position.castlingRookSquare(right);
        int rookTo = position.castlingRookTarget(right);
        state.rook = rook;
        state.rookFrom = rookFrom;
        state.rookTo = rookTo;
        PositionStateSupport.movePiece(position, rook, rookFrom, rookTo);
    }
}
