package chess.core;

import java.util.Arrays;

/**
 * Mutable chess position backed by piece bitboards.
 *
 * <p>
 * The position keeps both aggregate occupancy masks and a square-to-piece table.
 * That combination makes move generation fast while still allowing cheap
 * captures, promotions, Chess960 castling, and undo operations during perft
 * recursion.
 * </p>
 *
 * @since 2025 (since 2023 but heavily modified in 2025)
 * @author Lennart A. Conrad
 */
public class Position implements Comparable<Position> {

    /**
     * Piece index for White pawns in the {@link #pieces} array.
     */
    public static final int WHITE_PAWN = 0;

    /**
     * Piece index for White knights in the {@link #pieces} array.
     */
    public static final int WHITE_KNIGHT = 1;

    /**
     * Piece index for White bishops in the {@link #pieces} array.
     */
    public static final int WHITE_BISHOP = 2;

    /**
     * Piece index for White rooks in the {@link #pieces} array.
     */
    public static final int WHITE_ROOK = 3;

    /**
     * Piece index for the White queen in the {@link #pieces} array.
     */
    public static final int WHITE_QUEEN = 4;

    /**
     * Piece index for the White king in the {@link #pieces} array.
     */
    public static final int WHITE_KING = 5;

    /**
     * Piece index for Black pawns in the {@link #pieces} array.
     */
    public static final int BLACK_PAWN = 6;

    /**
     * Piece index for Black knights in the {@link #pieces} array.
     */
    public static final int BLACK_KNIGHT = 7;

    /**
     * Piece index for Black bishops in the {@link #pieces} array.
     */
    public static final int BLACK_BISHOP = 8;

    /**
     * Piece index for Black rooks in the {@link #pieces} array.
     */
    public static final int BLACK_ROOK = 9;

    /**
     * Piece index for the Black queen in the {@link #pieces} array.
     */
    public static final int BLACK_QUEEN = 10;

    /**
     * Piece index for the Black king in the {@link #pieces} array.
     */
    public static final int BLACK_KING = 11;

    /**
     * Castling-right bit for White king-side castling.
     */
    public static final int WHITE_KINGSIDE = 1;

    /**
     * Castling-right bit for White queen-side castling.
     */
    public static final int WHITE_QUEENSIDE = 2;

    /**
     * Castling-right bit for Black king-side castling.
     */
    public static final int BLACK_KINGSIDE = 4;

    /**
     * Castling-right bit for Black queen-side castling.
     */
    public static final int BLACK_QUEENSIDE = 8;

    /**
     * Promotion code for knight promotions, matching {@code chess.core.Move}.
     */
    static final byte PROMOTION_KNIGHT = 1;

    /**
     * Promotion code for bishop promotions, matching {@code chess.core.Move}.
     */
    static final byte PROMOTION_BISHOP = 2;

    /**
     * Promotion code for rook promotions, matching {@code chess.core.Move}.
     */
    static final byte PROMOTION_ROOK = 3;

    /**
     * Promotion code for queen promotions, matching {@code chess.core.Move}.
     */
    static final byte PROMOTION_QUEEN = 4;

    /**
     * Piece bitboards indexed by the public piece-index constants.
     */
    final long[] pieces;

    /**
     * Square-to-piece table using the same piece indexes as {@link #pieces}.
     *
     * <p>
     * A value of {@code -1} marks an empty square. The table avoids scanning
     * piece bitboards when making and undoing moves.
     * </p>
     */
    final byte[] board;

    /**
     * Cached occupancy mask for all White pieces.
     */
    long whiteOccupancy;

    /**
     * Cached occupancy mask for all Black pieces.
     */
    long blackOccupancy;

    /**
     * Cached occupancy mask for every piece on the board.
     */
    long occupancy;

    /**
     * Cached square index for the White king, or {@link Field#NO_SQUARE}.
     */
    byte whiteKingSquare;

    /**
     * Cached square index for the Black king, or {@link Field#NO_SQUARE}.
     */
    byte blackKingSquare;

    /**
     * Side-to-move flag; {@code true} means White is to move.
     */
    boolean whiteToMove;

    /**
     * Current castling rights represented by the public castling bit constants.
     */
    int castlingRights;

    /**
     * En-passant target square for the current side to move.
     *
     * <p>
     * The value is {@link Field#NO_SQUARE} when no en-passant capture is
     * currently available.
     * </p>
     */
    byte enPassantSquare;

    /**
     * Halfmove clock from the FEN state.
     */
    short halfMoveClock;

    /**
     * Fullmove number from the FEN state.
     */
    short fullMoveNumber;

    /**
     * Whether castling moves use Chess960 source-rook move encoding.
     */
    boolean chess960Castling;

    /**
     * Current White king-side castling rook square, or {@link Field#NO_SQUARE}.
     */
    byte whiteKingsideRookSquare;

    /**
     * Current White queen-side castling rook square, or {@link Field#NO_SQUARE}.
     */
    byte whiteQueensideRookSquare;

    /**
     * Current Black king-side castling rook square, or {@link Field#NO_SQUARE}.
     */
    byte blackKingsideRookSquare;

    /**
     * Current Black queen-side castling rook square, or {@link Field#NO_SQUARE}.
     */
    byte blackQueensideRookSquare;

    /**
     * Creates an empty mutable position with no pieces and default counters.
     */
    Position() {
        this.pieces = new long[12];
        this.board = new byte[64];
        Arrays.fill(this.board, (byte) -1);
        this.whiteKingSquare = Field.NO_SQUARE;
        this.blackKingSquare = Field.NO_SQUARE;
        this.enPassantSquare = Field.NO_SQUARE;
        this.fullMoveNumber = 1;
        this.whiteKingsideRookSquare = Field.NO_SQUARE;
        this.whiteQueensideRookSquare = Field.NO_SQUARE;
        this.blackKingsideRookSquare = Field.NO_SQUARE;
        this.blackQueensideRookSquare = Field.NO_SQUARE;
    }

    /**
     * Creates a position from FEN text.
     *
     * @param fen FEN string to parse
     * @throws IllegalArgumentException when the FEN is invalid
     */
    public Position(String fen) {
        this();
        copyStateFrom(Fen.parse(fen));
    }

    /**
     * Creates a deep copy of another bitboard position.
     *
     * @param source source position
     */
    private Position(Position source) {
        this();
        PositionStateSupport.copyState(source, this);
    }

    /**
     * Copies all mutable state from another position into this one.
     *
     * @param source source position
     */
    private void copyStateFrom(Position source) {
        PositionStateSupport.copyState(source, this);
    }

    /**
     * Builds a position from a FEN string.
     *
     * @param fen FEN
     * @return parsed position
     */
    public static Position fromFen(String fen) {
        return Fen.parse(fen);
    }

    /**
     * Returns a deep copy of this position.
     *
     * @return copy
     */
    public Position copy() {
        return new Position(this);
    }

    /**
     * Serializes this position to FEN.
     *
     * @return FEN text
     */
    @Override
    public String toString() {
        return Fen.format(this);
    }

    /**
     * Compares this position to another bitboard position.
     *
     * <p>
     * The ordering follows the same state components as the core position:
     * board layout, side to move, Chess960 flag, castling rights, en-passant
     * square, king squares, and move counters.
     * </p>
     *
     * @param other position to compare
     * @return comparison result
     * @throws NullPointerException when {@code other} is null
     */
    @Override
    public int compareTo(Position other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        int cmp = Arrays.compare(board, other.board);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Boolean.compare(whiteToMove, other.whiteToMove);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Boolean.compare(chess960Castling, other.chess960Castling);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(castlingRights, other.castlingRights);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Byte.compare(whiteKingsideRookSquare, other.whiteKingsideRookSquare);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Byte.compare(whiteQueensideRookSquare, other.whiteQueensideRookSquare);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Byte.compare(blackKingsideRookSquare, other.blackKingsideRookSquare);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Byte.compare(blackQueensideRookSquare, other.blackQueensideRookSquare);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Byte.compare(enPassantSquare, other.enPassantSquare);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Byte.compare(whiteKingSquare, other.whiteKingSquare);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Byte.compare(blackKingSquare, other.blackKingSquare);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Short.compare(halfMoveClock, other.halfMoveClock);
        if (cmp != 0) {
            return cmp;
        }
        return Short.compare(fullMoveNumber, other.fullMoveNumber);
    }

    /**
     * Tests structural equality between two bitboard positions.
     *
     * @param obj object to compare
     * @return true when all position-state fields match
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Position other)) {
            return false;
        }
        return Arrays.equals(board, other.board)
                && whiteToMove == other.whiteToMove
                && chess960Castling == other.chess960Castling
                && castlingRights == other.castlingRights
                && whiteKingsideRookSquare == other.whiteKingsideRookSquare
                && whiteQueensideRookSquare == other.whiteQueensideRookSquare
                && blackKingsideRookSquare == other.blackKingsideRookSquare
                && blackQueensideRookSquare == other.blackQueensideRookSquare
                && enPassantSquare == other.enPassantSquare
                && whiteKingSquare == other.whiteKingSquare
                && blackKingSquare == other.blackKingSquare
                && halfMoveClock == other.halfMoveClock
                && fullMoveNumber == other.fullMoveNumber;
    }

    /**
     * Computes a hash code consistent with {@link #equals(Object)}.
     *
     * @return structural hash code
     */
    @Override
    public int hashCode() {
        int result = Arrays.hashCode(board);
        result = 31 * result + Boolean.hashCode(whiteToMove);
        result = 31 * result + Boolean.hashCode(chess960Castling);
        result = 31 * result + Integer.hashCode(castlingRights);
        result = 31 * result + Byte.hashCode(whiteKingsideRookSquare);
        result = 31 * result + Byte.hashCode(whiteQueensideRookSquare);
        result = 31 * result + Byte.hashCode(blackKingsideRookSquare);
        result = 31 * result + Byte.hashCode(blackQueensideRookSquare);
        result = 31 * result + Byte.hashCode(enPassantSquare);
        result = 31 * result + Byte.hashCode(whiteKingSquare);
        result = 31 * result + Byte.hashCode(blackKingSquare);
        result = 31 * result + Short.hashCode(halfMoveClock);
        result = 31 * result + Short.hashCode(fullMoveNumber);
        return result;
    }

    /**
     * Returns a 64-bit signature of the full position state.
     *
     * <p>
     * This is intended as a fast, allocation-free cache key and includes the
     * same fields used by {@link #equals(Object)} and {@link #hashCode()}.
     * </p>
     *
     * @return 64-bit full-state signature
     */
    public long signature() {
        long h = signatureCore();
        h ^= halfMoveClock & 0xFFFFL;
        h *= 1099511628211L;
        h ^= fullMoveNumber & 0xFFFFL;
        h *= 1099511628211L;
        return h;
    }

    /**
     * Returns a 64-bit signature without the halfmove and fullmove counters.
     *
     * <p>
     * The core signature keeps all move-legality-relevant state and omits only
     * counters that are usually irrelevant for opening, ECO, and tag caches.
     * </p>
     *
     * @return 64-bit core-state signature
     */
    public long signatureCore() {
        long h = 1469598103934665603L;
        for (byte piece : board) {
            h ^= piece & 0xFFL;
            h *= 1099511628211L;
        }
        h ^= whiteToMove ? 1L : 0L;
        h *= 1099511628211L;
        h ^= chess960Castling ? 1L : 0L;
        h *= 1099511628211L;
        h ^= castlingRights & 0xFFL;
        h *= 1099511628211L;
        h ^= whiteKingsideRookSquare & 0xFFL;
        h *= 1099511628211L;
        h ^= whiteQueensideRookSquare & 0xFFL;
        h *= 1099511628211L;
        h ^= blackKingsideRookSquare & 0xFFL;
        h *= 1099511628211L;
        h ^= blackQueensideRookSquare & 0xFFL;
        h *= 1099511628211L;
        h ^= enPassantSquare & 0xFFL;
        h *= 1099511628211L;
        h ^= whiteKingSquare & 0xFFL;
        h *= 1099511628211L;
        h ^= blackKingSquare & 0xFFL;
        h *= 1099511628211L;
        return h;
    }

    /**
     * Applies a legal or pseudo-legal move to this position.
     *
     * <p>
     * This convenience overload allocates an undo state. Perft code should use
     * {@link #play(short, State)} with caller-owned state objects.
     * </p>
     *
     * @param move encoded move
     * @return this position
     */
    public Position play(short move) {
        if (!isEncodedMove(move)) {
            throw new IllegalArgumentException("Invalid encoded move: " + (move & 0xFFFF));
        }
        return play(move, new State());
    }

    /**
     * Applies a legal or pseudo-legal move and fills caller-owned undo state.
     *
     * <p>
     * The method updates piece bitboards, square table, occupancy caches,
     * castling rights, en-passant state, move counters, and cached king squares.
     * It does not validate king safety; callers decide whether a pseudo-legal
     * move is legal.
     * </p>
     *
     * @param move encoded move
     * @param state target undo state
     * @return this position
     */
    public Position play(short move, State state) {
        int from = move & 0x3F;
        int to = (move >>> 6) & 0x3F;
        int promotion = (move >>> 12) & 0x7;
        int moving = pieceIndexAt(from);
        if (moving < 0) {
            throw new IllegalArgumentException("No piece on " + Bits.name(from));
        }

        boolean white = moving < BLACK_PAWN;
        int castleRight = castlingRightForMove(moving, to);
        boolean castleMove = castleRight != 0;
        int actualTo = castleMove ? castlingKingTarget(castleRight) : to;
        int captured = castleMove ? -1 : pieceIndexAt(actualTo);
        int capturedSquare = actualTo;
        boolean capture = !castleMove && captured >= 0;
        boolean pawnMove = moving == WHITE_PAWN || moving == BLACK_PAWN;
        saveUndoState(state, moving, captured, capturedSquare, actualTo);

        if (castlingRights != 0) {
            castlingRights &= castlingKeepMask(from, to);
        }

        clearPiece(moving, from);
        if (captured >= 0) {
            clearPiece(captured, actualTo);
        }

        if (pawnMove && actualTo == enPassantSquare && captured < 0) {
            applyEnPassantCapture(state, white, actualTo);
            capture = true;
        }

        int delta = actualTo - from;
        if (castleMove) {
            moveCastlingRook(white, castleRight, state);
            state.castle = true;
        }

        int placed = promotion == 0 ? moving : promotionPieceIndex(moving, promotion);
        setPiece(placed, actualTo);
        enPassantSquare = nextEnPassantSquare(pawnMove, delta, from, actualTo, white);
        updateHalfMoveClock(pawnMove || capture);
        if (!whiteToMove) {
            fullMoveNumber++;
        }
        whiteToMove = !whiteToMove;
        return this;
    }

    /**
     * Stores all reversible state needed to undo one move.
     *
     * @param state target undo state
     * @param moving moving piece index
     * @param captured captured piece index, or -1
     * @param capturedSquare square where the captured piece is removed
     * @param kingTo actual king target square for castling moves
     */
    private void saveUndoState(
            State state,
            int moving,
            int captured,
            int capturedSquare,
            int kingTo) {
        state.moving = moving;
        state.captured = captured;
        state.capturedSquare = capturedSquare;
        state.kingTo = kingTo;
        state.castlingRights = castlingRights;
        state.enPassantSquare = enPassantSquare;
        state.halfMoveClock = halfMoveClock;
        state.fullMoveNumber = fullMoveNumber;
        state.whiteToMove = whiteToMove;
        state.rook = -1;
        state.rookFrom = Field.NO_SQUARE;
        state.rookTo = Field.NO_SQUARE;
        state.enPassantCapture = false;
        state.castle = false;
    }

    /**
     * Removes the pawn captured by an en-passant move.
     *
     * @param state undo state receiving capture metadata
     * @param white true when White made the capture
     * @param actualTo en-passant target square
     */
    private void applyEnPassantCapture(State state, boolean white, int actualTo) {
        int capturedSquare = white ? actualTo + 8 : actualTo - 8;
        int captured = white ? BLACK_PAWN : WHITE_PAWN;
        state.captured = captured;
        state.capturedSquare = capturedSquare;
        state.enPassantCapture = true;
        clearPiece(captured, capturedSquare);
    }

    /**
     * Calculates the next en-passant square after a move.
     *
     * @param pawnMove true when the moving piece was a pawn
     * @param delta target minus origin
     * @param from origin square
     * @param actualTo final target square
     * @param white true when the moving piece was White
     * @return en-passant target square, or {@link Field#NO_SQUARE}
     */
    private byte nextEnPassantSquare(boolean pawnMove, int delta, int from, int actualTo, boolean white) {
        if (!pawnMove || (delta != 16 && delta != -16)) {
            return Field.NO_SQUARE;
        }
        int target = (from + actualTo) / 2;
        long enemyPawns = pieces[white ? BLACK_PAWN : WHITE_PAWN];
        long attackers = white ? MoveGenerator.WHITE_PAWN_ATTACKS[target] : MoveGenerator.BLACK_PAWN_ATTACKS[target];
        return (attackers & enemyPawns) == 0L ? Field.NO_SQUARE : (byte) target;
    }

    /**
     * Updates the halfmove clock after a move.
     *
     * @param reset true when a pawn move or capture resets the clock
     */
    private void updateHalfMoveClock(boolean reset) {
        if (reset) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }
    }

    /**
     * Undoes a move previously made with {@link #play(short, State)}.
     *
     * <p>
     * The supplied state must be the exact state object filled by the matching
     * call to {@code play}. No validation is performed in the hot undo path.
     * </p>
     *
     * @param move encoded move
     * @param state undo state
     */
    public void undo(short move, State state) {
        int from = move & 0x3F;
        int to = state.castle ? state.kingTo : (move >>> 6) & 0x3F;
        int promotion = (move >>> 12) & 0x7;
        int placed = promotion == 0 ? state.moving : promotionPieceIndex(state.moving, promotion);
        boolean white = state.moving < BLACK_PAWN;
        long fromMask = 1L << from;
        long toMask = 1L << to;

        pieces[placed] &= ~toMask;
        board[to] = -1;
        if (white) {
            whiteOccupancy &= ~toMask;
        } else {
            blackOccupancy &= ~toMask;
        }
        occupancy &= ~toMask;
        if (state.rook >= 0) {
            clearPiece(state.rook, state.rookTo);
        }
        pieces[state.moving] |= fromMask;
        board[from] = (byte) state.moving;
        if (white) {
            whiteOccupancy |= fromMask;
        } else {
            blackOccupancy |= fromMask;
        }
        occupancy |= fromMask;
        if (state.moving == WHITE_KING) {
            whiteKingSquare = (byte) from;
        } else if (state.moving == BLACK_KING) {
            blackKingSquare = (byte) from;
        }
        if (state.rook >= 0) {
            setPiece(state.rook, state.rookFrom);
        }
        if (state.captured >= 0) {
            long capturedMask = 1L << state.capturedSquare;
            pieces[state.captured] |= capturedMask;
            board[state.capturedSquare] = (byte) state.captured;
            if (state.captured < BLACK_PAWN) {
                whiteOccupancy |= capturedMask;
            } else {
                blackOccupancy |= capturedMask;
            }
            occupancy |= capturedMask;
        }

        castlingRights = state.castlingRights;
        enPassantSquare = state.enPassantSquare;
        halfMoveClock = state.halfMoveClock;
        fullMoveNumber = state.fullMoveNumber;
        whiteToMove = state.whiteToMove;
    }

    /**
     * Applies a reversible null move for search pruning.
     *
     * <p>
     * A null move is not a legal chess move; it is a search heuristic that passes
     * the turn after clearing any en-passant target. Castling rights, piece
     * placement, and move counters are intentionally left unchanged so the method
     * can be used only as a temporary make/undo operation inside search.
     * </p>
     *
     * @param state target undo state
     * @return this position
     * @throws IllegalStateException if the side to move is currently in check
     */
    public Position playNull(State state) {
        if (inCheck()) {
            throw new IllegalStateException("Cannot play a null move while in check");
        }
        saveNullUndoState(state);
        enPassantSquare = Field.NO_SQUARE;
        whiteToMove = !whiteToMove;
        return this;
    }

    /**
     * Stores reversible state for {@link #playNull(State)}.
     *
     * @param state target undo state
     */
    private void saveNullUndoState(State state) {
        state.moving = -1;
        state.captured = -1;
        state.capturedSquare = Field.NO_SQUARE;
        state.kingTo = Field.NO_SQUARE;
        state.castlingRights = castlingRights;
        state.enPassantSquare = enPassantSquare;
        state.halfMoveClock = halfMoveClock;
        state.fullMoveNumber = fullMoveNumber;
        state.whiteToMove = whiteToMove;
        state.rook = -1;
        state.rookFrom = Field.NO_SQUARE;
        state.rookTo = Field.NO_SQUARE;
        state.enPassantCapture = false;
        state.castle = false;
    }

    /**
     * Undoes a null move previously made with {@link #playNull(State)}.
     *
     * @param state undo state filled by the matching null move
     */
    public void undoNull(State state) {
        castlingRights = state.castlingRights;
        enPassantSquare = state.enPassantSquare;
        halfMoveClock = state.halfMoveClock;
        fullMoveNumber = state.fullMoveNumber;
        whiteToMove = state.whiteToMove;
    }

    /**
     * Generates all legal moves for the side to move.
     *
     * @return legal move list for the current side
     */
    public MoveList legalMoves() {
        return MoveGenerator.generateLegalMoves(this);
    }

    /**
     * Generates pseudo-legal moves for the side to move.
     *
     * <p>
     * Pseudo-legal moves obey piece movement but may leave the moving side's
     * king in check. This is mainly useful for diagnostics and lightweight
     * feature extraction.
     * </p>
     *
     * @return pseudo-legal move list
     */
    public MoveList pseudoLegalMoves() {
        return MoveGenerator.generatePseudoLegalMoves(this);
    }

    /**
     * Counts legal moves for the side to move.
     *
     * @return legal move count
     */
    public int legalMoveCount() {
        return MoveGenerator.legalMoveCount(this);
    }

    /**
     * Returns whether the side to move has at least one legal move.
     *
     * @return true when a legal move exists
     */
    public boolean hasLegalMove() {
        return MoveGenerator.hasLegalMove(this);
    }

    /**
     * Tests whether an encoded move is legal in this position.
     *
     * @param move encoded move to test
     * @return true when the move is currently legal
     */
    public boolean isLegalMove(short move) {
        if (!isEncodedMove(move)) {
            return false;
        }
        return legalMoves().contains(move);
    }

    /**
     * Returns all legal moves from one origin square.
     *
     * @param square origin square, 0..63
     * @return legal moves whose origin is {@code square}
     */
    public MoveList legalMovesFrom(int square) {
        Bits.requireSquare(square);
        Position context = queryContextForSquare(square);
        MoveList all = context == null ? new MoveList(1) : context.legalMoves();
        MoveList out = new MoveList(Math.max(1, all.size()));
        for (int i = 0; i < all.size(); i++) {
            short move = all.raw(i);
            if ((move & 0x3F) == square) {
                out.add(move);
            }
        }
        return out;
    }

    /**
     * Returns all legal moves between one origin and one encoded target square.
     *
     * <p>
     * Promotion moves can return multiple entries for the same square pair. For
     * Chess960 castling, the target is the encoded rook source square, matching
     * the compact move representation.
     * </p>
     *
     * @param from origin square, 0..63
     * @param to encoded target square, 0..63
     * @return legal moves from {@code from} to {@code to}
     */
    public MoveList legalMovesBetween(int from, int to) {
        Bits.requireSquare(from);
        Bits.requireSquare(to);
        MoveList candidates = legalMovesFrom(from);
        MoveList out = new MoveList(Math.max(1, candidates.size()));
        for (int i = 0; i < candidates.size(); i++) {
            short move = candidates.raw(i);
            if (((move >>> 6) & 0x3F) == to) {
                out.add(move);
            }
        }
        return out;
    }

    /**
     * Returns legal move target squares from one origin square.
     *
     * <p>
     * The returned mask uses encoded move targets. For Chess960 castling, that
     * means the castling rook source square, matching the move encoding.
     * </p>
     *
     * @param square origin square, 0..63
     * @return target-square mask
     */
    public long legalTargetsFrom(int square) {
        Bits.requireSquare(square);
        MoveList moves = legalMovesFrom(square);
        long targets = 0L;
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            if ((move & 0x3F) == square) {
                targets |= 1L << ((move >>> 6) & 0x3F);
            }
        }
        return targets;
    }

    /**
     * Builds the correct side-to-move context for piece-local queries.
     *
     * @param square origin square
     * @return this position or a temporary copy, or null for an empty square
     */
    private Position queryContextForSquare(int square) {
        int piece = pieceIndexAt(square);
        if (piece < 0) {
            return null;
        }
        boolean pieceWhite = piece < BLACK_PAWN;
        if (pieceWhite == whiteToMove) {
            return this;
        }
        Position copy = copy();
        copy.whiteToMove = pieceWhite;
        copy.enPassantSquare = Field.NO_SQUARE;
        return copy;
    }

    /**
     * Returns whether the requested side's king is currently in check.
     *
     * @param white true to inspect the White king
     * @return true when that king is attacked
     */
    public boolean inCheck(boolean white) {
        return MoveGenerator.isKingAttacked(this, white);
    }

    /**
     * Returns whether the side to move is currently in check.
     *
     * @return true when the side to move is in check
     */
    public boolean inCheck() {
        return inCheck(whiteToMove);
    }

    /**
     * Returns whether the side to move is checkmated.
     *
     * @return true when the side to move is in check and has no legal move
     */
    public boolean isCheckmate() {
        return inCheck() && !hasLegalMove();
    }

    /**
     * Returns whether the side to move is stalemated.
     *
     * @return true when the side to move is not in check and has no legal move
     */
    public boolean isStalemate() {
        return !inCheck() && !hasLegalMove();
    }

    /**
     * Returns whether neither side has enough material for any legal checkmate.
     *
     * <p>
     * This intentionally stays conservative because search uses this predicate
     * for draw pruning. Positions such as king and bishop versus king and bishop
     * on opposite colors, king and knight versus king and knight, or king and two
     * knights versus king can contain legal mate positions and are not treated as
     * automatic draws here.
     * </p>
     *
     * @return true for basic insufficient-material positions
     */
    public boolean isInsufficientMaterial() {
        if ((pieces[WHITE_PAWN] | pieces[BLACK_PAWN]
                | pieces[WHITE_ROOK] | pieces[BLACK_ROOK]
                | pieces[WHITE_QUEEN] | pieces[BLACK_QUEEN]) != 0L) {
            return false;
        }
        int whiteKnights = Long.bitCount(pieces[WHITE_KNIGHT]);
        int whiteBishops = Long.bitCount(pieces[WHITE_BISHOP]);
        int blackKnights = Long.bitCount(pieces[BLACK_KNIGHT]);
        int blackBishops = Long.bitCount(pieces[BLACK_BISHOP]);
        int whiteMinors = whiteKnights + whiteBishops;
        int blackMinors = blackKnights + blackBishops;
        if (whiteMinors == 0 && blackMinors == 0) {
            return true;
        }
        if (whiteMinors == 1 && blackMinors == 0) {
            return true;
        }
        if (whiteMinors == 0 && blackMinors == 1) {
            return true;
        }
        return whiteKnights == 0
                && blackKnights == 0
                && whiteBishops == 1
                && blackBishops == 1
                && sameSquareColor(pieces[WHITE_BISHOP], pieces[BLACK_BISHOP]);
    }

    /**
     * Returns whether two one-bit bishop masks occupy the same square color.
     *
     * @param first first bishop bitboard
     * @param second second bishop bitboard
     * @return true when both bishops are bound to the same color complex
     */
    private static boolean sameSquareColor(long first, long second) {
        return squareColor(Long.numberOfTrailingZeros(first)) == squareColor(Long.numberOfTrailingZeros(second));
    }

    /**
     * Returns a defensive board snapshot using repository piece codes.
     *
     * <p>
     * Empty squares contain {@link Piece#EMPTY}. Occupied squares contain the
     * same signed piece codes used by {@code chess.core.Position}, which makes
     * the snapshot convenient for existing tag-formatting helpers.
     * </p>
     *
     * @return 64-entry board copy
     */
    public byte[] getBoard() {
        byte[] out = new byte[64];
        for (int square = 0; square < board.length; square++) {
            int piece = board[square];
            out[square] = piece < 0 ? Piece.EMPTY : pieceCode(piece);
        }
        return out;
    }

    /**
     * Returns a defensive board snapshot using internal piece indexes.
     *
     * <p>
     * Empty squares contain {@code -1}. Occupied squares contain one of this
     * class's public piece-index constants.
     * </p>
     *
     * @return 64-entry internal board copy
     */
    public byte[] pieceIndexes() {
        return Arrays.copyOf(board, board.length);
    }

    /**
     * Returns whether a square is empty.
     *
     * @param square board square, 0..63
     * @return true when no piece occupies the square
     */
    public boolean isEmpty(int square) {
        Bits.requireSquare(square);
        return board[square] < 0;
    }

    /**
     * Returns whether any piece occupies a square.
     *
     * @param square board square, 0..63
     * @return true when occupied
     */
    public boolean hasPiece(int square) {
        return !isEmpty(square);
    }

    /**
     * Returns whether a specific internal piece occupies a square.
     *
     * @param square board square, 0..63
     * @param pieceIndex one of the public piece-index constants
     * @return true when that piece occupies the square
     */
    public boolean hasPiece(int square, int pieceIndex) {
        Bits.requireSquare(square);
        requirePieceIndex(pieceIndex);
        return board[square] == pieceIndex;
    }

    /**
     * Returns whether a White piece occupies a square.
     *
     * @param square board square, 0..63
     * @return true when the square holds a White piece
     */
    public boolean isWhitePieceAt(int square) {
        Bits.requireSquare(square);
        int piece = board[square];
        return piece >= WHITE_PAWN && piece <= WHITE_KING;
    }

    /**
     * Returns whether a Black piece occupies a square.
     *
     * @param square board square, 0..63
     * @return true when the square holds a Black piece
     */
    public boolean isBlackPieceAt(int square) {
        Bits.requireSquare(square);
        return board[square] >= BLACK_PAWN;
    }

    /**
     * Counts pieces of one internal type.
     *
     * @param pieceIndex one of the public piece-index constants
     * @return number of pieces of that type
     */
    public int countPieces(int pieceIndex) {
        requirePieceIndex(pieceIndex);
        return Long.bitCount(pieces[pieceIndex]);
    }

    /**
     * Counts pieces matching one repository piece code.
     *
     * @param piece repository piece code
     * @return number of matching pieces
     */
    public int countPieces(byte piece) {
        int index = pieceIndex(piece);
        return index < 0 ? 0 : countPieces(index);
    }

    /**
     * Counts all White pieces.
     *
     * @return White piece count
     */
    public int countWhitePieces() {
        return Long.bitCount(whiteOccupancy);
    }

    /**
     * Counts all Black pieces.
     *
     * @return Black piece count
     */
    public int countBlackPieces() {
        return Long.bitCount(blackOccupancy);
    }

    /**
     * Counts all pieces on the board.
     *
     * @return total piece count
     */
    public int countTotalPieces() {
        return Long.bitCount(occupancy);
    }

    /**
     * Returns the piece-count balance from White's perspective.
     *
     * @return White piece count minus Black piece count
     */
    public int countPieceDiscrepancy() {
        return countWhitePieces() - countBlackPieces();
    }

    /**
     * Returns White's material value in centipawns.
     *
     * @return total White material
     */
    public int countWhiteMaterial() {
        return material(true);
    }

    /**
     * Returns Black's material value in centipawns.
     *
     * @return total Black material
     */
    public int countBlackMaterial() {
        return material(false);
    }

    /**
     * Returns the total material value for both sides.
     *
     * @return combined centipawn material
     */
    public int countTotalMaterial() {
        return countWhiteMaterial() + countBlackMaterial();
    }

    /**
     * Returns the material balance from White's perspective.
     *
     * @return White material minus Black material
     */
    public int materialDiscrepancy() {
        return countWhiteMaterial() - countBlackMaterial();
    }

    /**
     * Returns whether both sides have at least one bishop and the bishop colors differ.
     *
     * @return true when the position has opposite-colored bishops
     */
    public boolean hasOppositeColoredBishops() {
        long white = pieces[WHITE_BISHOP];
        long black = pieces[BLACK_BISHOP];
        if (white == 0L || black == 0L) {
            return false;
        }
        boolean whiteLight = hasBishopOnColor(white, 0);
        boolean whiteDark = hasBishopOnColor(white, 1);
        boolean blackLight = hasBishopOnColor(black, 0);
        boolean blackDark = hasBishopOnColor(black, 1);
        return (whiteLight && blackDark) || (whiteDark && blackLight);
    }

    /**
     * Counts pawns of one side on a file.
     *
     * @param white true for White pawns
     * @param file file index, 0 for file a and 7 for file h
     * @return pawn count on the file
     */
    public int pawnFileCount(boolean white, int file) {
        if (file < 0 || file >= 8) {
            throw new IllegalArgumentException("Invalid file: " + file);
        }
        return Long.bitCount(pieces[white ? WHITE_PAWN : BLACK_PAWN] & fileMask(file));
    }

    /**
     * Returns doubled pawns for one side.
     *
     * <p>
     * A pawn is included when another friendly pawn shares its file.
     * </p>
     *
     * @param white true for White pawns
     * @return mask of doubled pawns
     */
    public long doubledPawns(boolean white) {
        long pawns = pieces[white ? WHITE_PAWN : BLACK_PAWN];
        long out = 0L;
        for (int file = 0; file < 8; file++) {
            long filePawns = pawns & fileMask(file);
            if (Long.bitCount(filePawns) > 1) {
                out |= filePawns;
            }
        }
        return out;
    }

    /**
     * Returns isolated pawns for one side.
     *
     * <p>
     * A pawn is isolated when no friendly pawn exists on either adjacent file.
     * </p>
     *
     * @param white true for White pawns
     * @return mask of isolated pawns
     */
    public long isolatedPawns(boolean white) {
        long pawns = pieces[white ? WHITE_PAWN : BLACK_PAWN];
        long out = 0L;
        long scan = pawns;
        while (scan != 0L) {
            int square = Bits.lsb(scan);
            scan = Bits.withoutLsb(scan);
            int file = square & 7;
            long adjacent = 0L;
            if (file > 0) {
                adjacent |= fileMask(file - 1);
            }
            if (file < 7) {
                adjacent |= fileMask(file + 1);
            }
            if ((pawns & adjacent) == 0L) {
                out |= 1L << square;
            }
        }
        return out;
    }

    /**
     * Returns passed pawns for one side.
     *
     * <p>
     * A pawn is passed when no enemy pawn exists on its file or adjacent files
     * ahead of it.
     * </p>
     *
     * @param white true for White pawns
     * @return mask of passed pawns
     */
    public long passedPawns(boolean white) {
        long pawns = pieces[white ? WHITE_PAWN : BLACK_PAWN];
        long out = 0L;
        long scan = pawns;
        while (scan != 0L) {
            int square = Bits.lsb(scan);
            scan = Bits.withoutLsb(scan);
            if (isPassedPawn(square)) {
                out |= 1L << square;
            }
        }
        return out;
    }

    /**
     * Returns whether the pawn on a square is passed.
     *
     * @param square pawn square, 0..63
     * @return true when the square contains a passed pawn
     */
    public boolean isPassedPawn(int square) {
        Bits.requireSquare(square);
        int pawn = board[square];
        if (pawn != WHITE_PAWN && pawn != BLACK_PAWN) {
            return false;
        }
        boolean white = pawn == WHITE_PAWN;
        long enemies = pieces[white ? BLACK_PAWN : WHITE_PAWN];
        int file = square & 7;
        int rank = Bits.rank(square);
        long blockers = 0L;
        for (int targetFile = Math.max(0, file - 1); targetFile <= Math.min(7, file + 1); targetFile++) {
            blockers |= fileMask(targetFile);
        }
        long scan = enemies & blockers;
        while (scan != 0L) {
            int enemy = Bits.lsb(scan);
            scan = Bits.withoutLsb(scan);
            int enemyRank = Bits.rank(enemy);
            if ((white && enemyRank > rank) || (!white && enemyRank < rank)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the attack mask produced by the piece on a square.
     *
     * <p>
     * The mask describes geometric attacks, not legal moves. Pawn entries are
     * diagonal attack squares. Sliding pieces stop at the first occupied square.
     * Empty squares return {@code 0}.
     * </p>
     *
     * @param square occupied square to inspect
     * @return attack mask for the piece on the square
     */
    public long attacksFrom(int square) {
        Bits.requireSquare(square);
        return switch (board[square]) {
            case WHITE_PAWN -> MoveGenerator.pawnAttacks(square, true);
            case BLACK_PAWN -> MoveGenerator.pawnAttacks(square, false);
            case WHITE_KNIGHT, BLACK_KNIGHT -> MoveGenerator.knightAttacks(square);
            case WHITE_BISHOP, BLACK_BISHOP -> MoveGenerator.bishopAttacks(square, occupancy);
            case WHITE_ROOK, BLACK_ROOK -> MoveGenerator.rookAttacks(square, occupancy);
            case WHITE_QUEEN, BLACK_QUEEN -> MoveGenerator.bishopAttacks(square, occupancy)
                    | MoveGenerator.rookAttacks(square, occupancy);
            case WHITE_KING, BLACK_KING -> MoveGenerator.kingAttacks(square);
            default -> 0L;
        };
    }

    /**
     * Returns all attackers of one side against a square.
     *
     * @param byWhite true to return White attackers
     * @param square target square, 0..63
     * @return mask of attacking pieces
     */
    public long attackerMask(boolean byWhite, int square) {
        Bits.requireSquare(square);
        if (byWhite) {
            return (MoveGenerator.BLACK_PAWN_ATTACKS[square] & pieces[WHITE_PAWN])
                    | (MoveGenerator.KNIGHT_ATTACKS[square] & pieces[WHITE_KNIGHT])
                    | (MoveGenerator.KING_ATTACKS[square] & pieces[WHITE_KING])
                    | (MoveGenerator.bishopAttacks(square, occupancy) & (pieces[WHITE_BISHOP] | pieces[WHITE_QUEEN]))
                    | (MoveGenerator.rookAttacks(square, occupancy) & (pieces[WHITE_ROOK] | pieces[WHITE_QUEEN]));
        }
        return (MoveGenerator.WHITE_PAWN_ATTACKS[square] & pieces[BLACK_PAWN])
                | (MoveGenerator.KNIGHT_ATTACKS[square] & pieces[BLACK_KNIGHT])
                | (MoveGenerator.KING_ATTACKS[square] & pieces[BLACK_KING])
                | (MoveGenerator.bishopAttacks(square, occupancy) & (pieces[BLACK_BISHOP] | pieces[BLACK_QUEEN]))
                | (MoveGenerator.rookAttacks(square, occupancy) & (pieces[BLACK_ROOK] | pieces[BLACK_QUEEN]));
    }

    /**
     * Returns attacker squares for one side against a square.
     *
     * @param byWhite true to return White attackers
     * @param square target square, 0..63
     * @return attacking square indexes
     */
    public byte[] attackerSquares(boolean byWhite, int square) {
        return squares(attackerMask(byWhite, square));
    }

    /**
     * Counts attackers of one side against a square.
     *
     * @param byWhite true to count White attackers
     * @param square target square, 0..63
     * @return attacker count
     */
    public int countAttackers(boolean byWhite, int square) {
        return Long.bitCount(attackerMask(byWhite, square));
    }

    /**
     * Returns whether a square is attacked by one side.
     *
     * @param square target square, 0..63
     * @param byWhite true to test White attacks
     * @return true when at least one piece attacks the square
     */
    public boolean isSquareAttacked(int square, boolean byWhite) {
        return attackerMask(byWhite, square) != 0L;
    }

    /**
     * Returns whether a square is attacked by White.
     *
     * @param square target square
     * @return true when White attacks the square
     */
    public boolean isAttackedByWhite(byte square) {
        return isSquareAttacked(square, true);
    }

    /**
     * Returns whether a square is attacked by Black.
     *
     * @param square target square
     * @return true when Black attacks the square
     */
    public boolean isAttackedByBlack(byte square) {
        return isSquareAttacked(square, false);
    }

    /**
     * Counts White attackers against a square.
     *
     * @param square target square
     * @return White attacker count
     */
    public int countAttackersByWhite(byte square) {
        return countAttackers(true, square);
    }

    /**
     * Counts Black attackers against a square.
     *
     * @param square target square
     * @return Black attacker count
     */
    public int countAttackersByBlack(byte square) {
        return countAttackers(false, square);
    }

    /**
     * Returns White attacker squares against a square.
     *
     * @param square target square
     * @return White attacker square indexes
     */
    public byte[] getAttackersByWhite(byte square) {
        return attackerSquares(true, square);
    }

    /**
     * Returns Black attacker squares against a square.
     *
     * @param square target square
     * @return Black attacker square indexes
     */
    public byte[] getAttackersByBlack(byte square) {
        return attackerSquares(false, square);
    }

    /**
     * Finds pin metadata for the piece on a square relative to its own king.
     *
     * @param square candidate pinned-piece square
     * @return pin metadata, or null when the piece is not pinned
     */
    public PinInfo findPinToOwnKing(byte square) {
        Bits.requireSquare(square);
        int piece = board[square];
        if (!canBePinned(piece)) {
            return null;
        }
        boolean white = piece < BLACK_PAWN;
        int king = kingSquare(white);
        if (king == Field.NO_SQUARE) {
            return null;
        }
        int kingFile = king & 7;
        int kingRow = king >>> 3;
        int pieceFile = square & 7;
        int pieceRow = square >>> 3;
        int fileDelta = Integer.compare(pieceFile, kingFile);
        int rowDelta = Integer.compare(pieceRow, kingRow);
        if (!aligned(kingFile, kingRow, pieceFile, pieceRow)) {
            return null;
        }
        if (hasPieceBetween(kingFile, kingRow, pieceFile, pieceRow, fileDelta, rowDelta)) {
            return null;
        }
        return pinnerBeyond(square, white, fileDelta, rowDelta);
    }

    /**
     * Returns whether a piece index can be pinned to its own king.
     *
     * @param piece piece index
     * @return true for non-king pieces on the board
     */
    private static boolean canBePinned(int piece) {
        return piece >= 0 && piece != WHITE_KING && piece != BLACK_KING;
    }

    /**
     * Returns whether any piece stands strictly between two aligned squares.
     *
     * @param kingFile king file
     * @param kingRow king rank
     * @param pieceFile candidate piece file
     * @param pieceRow candidate piece rank
     * @param fileDelta file step from king toward piece
     * @param rowDelta rank step from king toward piece
     * @return true when the line is blocked before the candidate piece
     */
    private boolean hasPieceBetween(
            int kingFile,
            int kingRow,
            int pieceFile,
            int pieceRow,
            int fileDelta,
            int rowDelta) {
        int file = kingFile + fileDelta;
        int row = kingRow + rowDelta;
        while (file != pieceFile || row != pieceRow) {
            if (board[row * 8 + file] >= 0) {
                return true;
            }
            file += fileDelta;
            row += rowDelta;
        }
        return false;
    }

    /**
     * Finds an enemy slider beyond a candidate pinned piece.
     *
     * @param square candidate pinned-piece square
     * @param white true when the candidate piece is White
     * @param fileDelta file step away from the king
     * @param rowDelta rank step away from the king
     * @return pin metadata, or null when no pinner exists
     */
    private PinInfo pinnerBeyond(byte square, boolean white, int fileDelta, int rowDelta) {
        int file = (square & 7) + fileDelta;
        int row = (square >>> 3) + rowDelta;
        boolean diagonal = fileDelta != 0 && rowDelta != 0;
        while (file >= 0 && file < 8 && row >= 0 && row < 8) {
            int target = row * 8 + file;
            int pinner = board[target];
            if (pinner >= 0) {
                if (isEnemySliderForPin(pinner, white, diagonal)) {
                    return new PinInfo(square, (byte) target, pieceCode(pinner));
                }
                return null;
            }
            file += fileDelta;
            row += rowDelta;
        }
        return null;
    }

    /**
     * Returns whether the piece on a square is pinned to its own king.
     *
     * @param square candidate pinned-piece square
     * @return true when pinned
     */
    public boolean isPinnedToOwnKing(byte square) {
        return findPinToOwnKing(square) != null;
    }

    /**
     * Returns the internal piece moved by an encoded move.
     *
     * @param move encoded move
     * @return moving piece index, or -1 when the origin is empty
     */
    public int movingPiece(short move) {
        if (!isEncodedMove(move)) {
            return -1;
        }
        return board[move & 0x3F];
    }

    /**
     * Returns the final target square for a move.
     *
     * <p>
     * For standard moves this is the encoded target. For castling, this is the
     * final king square.
     * </p>
     *
     * @param move encoded move
     * @return actual target square, or {@link Field#NO_SQUARE} for an empty origin
     */
    public byte actualToSquare(short move) {
        int moving = movingPiece(move);
        if (moving < 0) {
            return Field.NO_SQUARE;
        }
        int to = (move >>> 6) & 0x3F;
        int castleRight = castlingRightForMove(moving, to);
        return castleRight == 0 ? (byte) to : castlingKingTarget(castleRight);
    }

    /**
     * Returns whether a move is a castling move in this position.
     *
     * @param move encoded move
     * @return true when the move castles
     */
    public boolean isCastle(short move) {
        int moving = movingPiece(move);
        return moving >= 0 && castlingRightForMove(moving, (move >>> 6) & 0x3F) != 0;
    }

    /**
     * Returns whether a move promotes a pawn.
     *
     * @param move encoded move
     * @return true when the promotion field is non-zero
     */
    public boolean isPromotion(short move) {
        if (!isEncodedMove(move)) {
            return false;
        }
        return ((move >>> 12) & 0x7) != 0;
    }

    /**
     * Returns whether a move captures a piece.
     *
     * @param move encoded move
     * @return true for normal and en-passant captures
     */
    public boolean isCapture(short move) {
        return capturedPiece(move) >= 0;
    }

    /**
     * Returns whether a move from one square to another captures.
     *
     * @param movefrom origin square
     * @param moveto target square
     * @return true for normal and en-passant captures
     */
    public boolean isCapture(byte movefrom, byte moveto) {
        if (movefrom < 0 || movefrom >= 64 || moveto < 0 || moveto >= 64) {
            return false;
        }
        return pieceIndexAt(moveto) >= 0 || isEnPassantCapture(movefrom, moveto);
    }

    /**
     * Returns whether a move is an en-passant capture.
     *
     * @param move encoded move
     * @return true when the move captures en-passant
     */
    public boolean isEnPassantCapture(short move) {
        int moving = movingPiece(move);
        int to = (move >>> 6) & 0x3F;
        return (moving == WHITE_PAWN || moving == BLACK_PAWN)
                && to == enPassantSquare
                && board[to] < 0;
    }

    /**
     * Returns whether a move from one square to another captures en-passant.
     *
     * @param movefrom origin square
     * @param moveto target square
     * @return true when the square pair is an en-passant capture
     */
    public boolean isEnPassantCapture(short movefrom, short moveto) {
        if (movefrom < 0 || movefrom >= 64 || moveto < 0 || moveto >= 64) {
            return false;
        }
        int moving = pieceIndexAt(movefrom);
        return (moving == WHITE_PAWN || moving == BLACK_PAWN)
                && moveto == enPassantSquare
                && board[moveto] < 0;
    }

    /**
     * Returns the captured piece for a move.
     *
     * @param move encoded move
     * @return captured internal piece index, or -1 when no piece is captured
     */
    public int capturedPiece(short move) {
        if (isCastle(move)) {
            return -1;
        }
        if (isEnPassantCapture(move)) {
            return movingPiece(move) == WHITE_PAWN ? BLACK_PAWN : WHITE_PAWN;
        }
        byte target = actualToSquare(move);
        return target == Field.NO_SQUARE ? -1 : board[target];
    }

    /**
     * Returns the square containing the captured piece for a move.
     *
     * @param move encoded move
     * @return captured square, or {@link Field#NO_SQUARE} when no capture occurs
     */
    public byte capturedSquare(short move) {
        if (isEnPassantCapture(move)) {
            return movingPiece(move) == WHITE_PAWN
                    ? (byte) (((move >>> 6) & 0x3F) + 8)
                    : (byte) (((move >>> 6) & 0x3F) - 8);
        }
        return capturedPiece(move) >= 0 ? actualToSquare(move) : Field.NO_SQUARE;
    }

    /**
     * Counts legal moves originating from one square.
     *
     * @param square origin square
     * @return number of legal moves from that square
     */
    public int countLegalMovesFrom(byte square) {
        return legalMovesFrom(square).size();
    }

    /**
     * Generates all legal successor positions.
     *
     * @return successor positions, one per legal move
     */
    public Position[] generateSubPositions() {
        MoveList moves = legalMoves();
        Position[] positions = new Position[moves.size()];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = copy().play(moves.raw(i));
        }
        return positions;
    }

    /**
     * Runs node-only perft from this position.
     *
     * @param depth non-negative depth
     * @return legal leaf-node count
     */
    public long perft(int depth) {
        return MoveGenerator.perft(copy(), depth);
    }

    /**
     * Returns the current checkers against the side to move.
     *
     * @return attacker squares checking the active king
     */
    public byte[] getCheckers() {
        byte king = kingSquare(whiteToMove);
        return king == Field.NO_SQUARE ? new byte[0] : attackerSquares(!whiteToMove, king);
    }

    /**
     * Returns the bitboard for one piece type.
     *
     * @param pieceIndex one of the public piece-index constants
     * @return bitboard for that piece type
     */
    public long pieces(int pieceIndex) {
        requirePieceIndex(pieceIndex);
        return pieces[pieceIndex];
    }

    /**
     * Returns the occupancy mask for all White pieces.
     *
     * @return White occupancy mask
     */
    public long whiteOccupancy() {
        return whiteOccupancy;
    }

    /**
     * Returns the occupancy mask for all Black pieces.
     *
     * @return Black occupancy mask
     */
    public long blackOccupancy() {
        return blackOccupancy;
    }

    /**
     * Returns the occupancy mask for every piece on the board.
     *
     * @return combined occupancy mask
     */
    public long occupancy() {
        return occupancy;
    }

    /**
     * Returns the occupancy mask for one side.
     *
     * @param white true for White
     * @return side occupancy mask
     */
    public long occupancy(boolean white) {
        return white ? whiteOccupancy() : blackOccupancy();
    }

    /**
     * Returns whether White is to move.
     *
     * @return true for White
     */
    public boolean isWhiteToMove() {
        return whiteToMove;
    }

    /**
     * Returns the current castling-right bit mask.
     *
     * @return mask composed from the public castling constants
     */
    public int castlingRights() {
        return castlingRights;
    }

    /**
     * Returns whether one castling right is present.
     *
     * @param right one of the public castling constants
     * @return true when present
     */
    public boolean canCastle(int right) {
        return (castlingRights & right) != 0;
    }

    /**
     * Returns the current en-passant target square.
     *
     * @return square or {@link Field#NO_SQUARE}
     */
    public byte enPassantSquare() {
        return enPassantSquare;
    }

    /**
     * Returns the halfmove clock.
     *
     * @return halfmove clock from the FEN state
     */
    public short halfMoveClock() {
        return halfMoveClock;
    }

    /**
     * Returns the fullmove number.
     *
     * @return fullmove number from the FEN state
     */
    public short fullMoveNumber() {
        return fullMoveNumber;
    }

    /**
     * Returns the cached king square for one side.
     *
     * @param white true for White
     * @return square, or {@link Field#NO_SQUARE}
     */
    public byte kingSquare(boolean white) {
        return white ? whiteKingSquare : blackKingSquare;
    }

    /**
     * Returns whether this position uses Chess960 castling encoding.
     *
     * @return true for Chess960 castling metadata
     */
    public boolean isChess960() {
        return chess960Castling;
    }

    /**
     * Returns the rook source square for a current castling right.
     *
     * @param right one of the public castling constants
     * @return rook source square, or {@link Field#NO_SQUARE}
     */
    public byte castlingRookSquare(int right) {
        return switch (right) {
            case WHITE_KINGSIDE -> whiteKingsideRookSquare;
            case WHITE_QUEENSIDE -> whiteQueensideRookSquare;
            case BLACK_KINGSIDE -> blackKingsideRookSquare;
            case BLACK_QUEENSIDE -> blackQueensideRookSquare;
            default -> Field.NO_SQUARE;
        };
    }

    /**
     * Returns the encoded king target used for a castling move.
     *
     * <p>
     * Standard chess keeps normal UCI encoding, where the king target is c/g.
     * Chess960 uses the rook source square, matching UCI's Chess960 castling
     * convention and avoiding ambiguity when the king already starts on c/g.
     * </p>
     *
     * @param right one castling right
     * @return encoded move target square
     */
    public byte castlingMoveTarget(int right) {
        return chess960Castling ? castlingRookSquare(right) : castlingKingTarget(right);
    }

    /**
     * Returns the encoded move target for an available castling right.
     *
     * @param right one castling right
     * @return encoded target, or {@link Field#NO_SQUARE} when the right is absent
     */
    public byte activeCastlingMoveTarget(int right) {
        return canCastle(right) ? castlingMoveTarget(right) : Field.NO_SQUARE;
    }

    /**
     * Returns the final king square for a castling right.
     *
     * @param right one castling right
     * @return final king square, or {@link Field#NO_SQUARE}
     */
    public byte castlingKingTarget(int right) {
        return switch (right) {
            case WHITE_KINGSIDE -> Field.G1;
            case WHITE_QUEENSIDE -> Field.C1;
            case BLACK_KINGSIDE -> Field.G8;
            case BLACK_QUEENSIDE -> Field.C8;
            default -> Field.NO_SQUARE;
        };
    }

    /**
     * Returns the final rook square for a castling right.
     *
     * @param right one castling right
     * @return final rook square, or {@link Field#NO_SQUARE}
     */
    public byte castlingRookTarget(int right) {
        return switch (right) {
            case WHITE_KINGSIDE -> Field.F1;
            case WHITE_QUEENSIDE -> Field.D1;
            case BLACK_KINGSIDE -> Field.F8;
            case BLACK_QUEENSIDE -> Field.D8;
            default -> Field.NO_SQUARE;
        };
    }

    /**
     * Returns the repository piece code on a square.
     *
     * @param square board square, 0..63
     * @return {@link Piece} code, or {@link Piece#EMPTY}
     */
    public byte pieceAt(int square) {
        Bits.requireSquare(square);
        int index = pieceIndexAt(square);
        return index < 0 ? Piece.EMPTY : pieceCode(index);
    }

    /**
     * Returns the internal piece index on a square.
     *
     * @param square board square, 0..63
     * @return index, or -1
     */
    int pieceIndexAt(int square) {
        return board[square];
    }

    /**
     * Converts a repository piece code to an internal piece index.
     *
     * @param piece {@link Piece} code
     * @return piece index, or -1 for empty/unknown pieces
     */
    static int pieceIndex(byte piece) {
        return switch (piece) {
            case Piece.WHITE_PAWN -> WHITE_PAWN;
            case Piece.WHITE_KNIGHT -> WHITE_KNIGHT;
            case Piece.WHITE_BISHOP -> WHITE_BISHOP;
            case Piece.WHITE_ROOK -> WHITE_ROOK;
            case Piece.WHITE_QUEEN -> WHITE_QUEEN;
            case Piece.WHITE_KING -> WHITE_KING;
            case Piece.BLACK_PAWN -> BLACK_PAWN;
            case Piece.BLACK_KNIGHT -> BLACK_KNIGHT;
            case Piece.BLACK_BISHOP -> BLACK_BISHOP;
            case Piece.BLACK_ROOK -> BLACK_ROOK;
            case Piece.BLACK_QUEEN -> BLACK_QUEEN;
            case Piece.BLACK_KING -> BLACK_KING;
            default -> -1;
        };
    }

    /**
     * Converts an internal piece index to a repository piece code.
     *
     * @param index piece index
     * @return {@link Piece} code, or {@link Piece#EMPTY}
     */
    static byte pieceCode(int index) {
        return switch (index) {
            case WHITE_PAWN -> Piece.WHITE_PAWN;
            case WHITE_KNIGHT -> Piece.WHITE_KNIGHT;
            case WHITE_BISHOP -> Piece.WHITE_BISHOP;
            case WHITE_ROOK -> Piece.WHITE_ROOK;
            case WHITE_QUEEN -> Piece.WHITE_QUEEN;
            case WHITE_KING -> Piece.WHITE_KING;
            case BLACK_PAWN -> Piece.BLACK_PAWN;
            case BLACK_KNIGHT -> Piece.BLACK_KNIGHT;
            case BLACK_BISHOP -> Piece.BLACK_BISHOP;
            case BLACK_ROOK -> Piece.BLACK_ROOK;
            case BLACK_QUEEN -> Piece.BLACK_QUEEN;
            case BLACK_KING -> Piece.BLACK_KING;
            default -> Piece.EMPTY;
        };
    }

    /**
     * Validates an internal piece index.
     *
     * @param pieceIndex candidate piece index
     */
    private static void requirePieceIndex(int pieceIndex) {
        if (pieceIndex < WHITE_PAWN || pieceIndex > BLACK_KING) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex);
        }
    }

    /**
     * Returns whether a compact move value is usable by this position.
     *
     * @param move candidate encoded move
     * @return true when the value is not the sentinel and has a valid promotion
     */
    private static boolean isEncodedMove(short move) {
        if (move == Move.NO_MOVE || move < 0) {
            return false;
        }
        return ((move >>> 12) & 0x7) <= PROMOTION_QUEEN;
    }

    /**
     * Returns material for one side.
     *
     * @param white true for White material
     * @return centipawn material
     */
    private int material(boolean white) {
        int start = white ? WHITE_PAWN : BLACK_PAWN;
        int end = white ? WHITE_KING : BLACK_KING;
        int value = 0;
        for (int piece = start; piece <= end; piece++) {
            value += Long.bitCount(pieces[piece]) * Piece.getValue(pieceCode(piece));
        }
        return value;
    }

    /**
     * Returns the mask for one file.
     *
     * @param file file index, 0..7
     * @return file mask
     */
    private static long fileMask(int file) {
        return switch (file) {
            case 0 -> Bits.FILE_A;
            case 1 -> Bits.FILE_B;
            case 2 -> Bits.FILE_C;
            case 3 -> Bits.FILE_D;
            case 4 -> Bits.FILE_E;
            case 5 -> Bits.FILE_F;
            case 6 -> Bits.FILE_G;
            case 7 -> Bits.FILE_H;
            default -> throw new IllegalArgumentException("Invalid file: " + file);
        };
    }

    /**
     * Checks whether a bishop mask contains at least one bishop on a square color.
     *
     * @param bishops bishop mask
     * @param color square color, 0 or 1
     * @return true when present
     */
    private static boolean hasBishopOnColor(long bishops, int color) {
        long scan = bishops;
        while (scan != 0L) {
            int square = Bits.lsb(scan);
            scan = Bits.withoutLsb(scan);
            if (squareColor(square) == color) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the checkerboard color of a square.
     *
     * @param square board square, 0..63
     * @return square color, 0 or 1
     */
    private static int squareColor(int square) {
        return (Bits.file(square) + Bits.rank(square)) & 1;
    }

    /**
     * Converts a bitboard mask to square indexes.
     *
     * @param mask square mask
     * @return squares in least-significant-bit order
     */
    private static byte[] squares(long mask) {
        byte[] out = new byte[Long.bitCount(mask)];
        int index = 0;
        while (mask != 0L) {
            int square = Bits.lsb(mask);
            mask = Bits.withoutLsb(mask);
            out[index++] = (byte) square;
        }
        return out;
    }

    /**
     * Returns whether two squares share a rank, file, or diagonal.
     *
     * @param firstFile first square file
     * @param firstRow first square row
     * @param secondFile second square file
     * @param secondRow second square row
     * @return true when aligned
     */
    private static boolean aligned(int firstFile, int firstRow, int secondFile, int secondRow) {
        return firstFile == secondFile
                || firstRow == secondRow
                || Math.abs(firstFile - secondFile) == Math.abs(firstRow - secondRow);
    }

    /**
     * Checks whether a piece can pin along the inspected line.
     *
     * @param piece candidate pinning piece index
     * @param pinnedWhite true when the pinned piece is White
     * @param diagonal true for a diagonal pin line
     * @return true when the enemy slider pins along that line
     */
    private static boolean isEnemySliderForPin(int piece, boolean pinnedWhite, boolean diagonal) {
        boolean enemy = pinnedWhite ? piece >= BLACK_PAWN : piece >= WHITE_PAWN && piece <= WHITE_KING;
        if (!enemy) {
            return false;
        }
        if (diagonal) {
            return piece == WHITE_BISHOP || piece == BLACK_BISHOP
                    || piece == WHITE_QUEEN || piece == BLACK_QUEEN;
        }
        return piece == WHITE_ROOK || piece == BLACK_ROOK
                || piece == WHITE_QUEEN || piece == BLACK_QUEEN;
    }

    /**
     * Computes the castling rights retained after touching two squares.
     *
     * <p>
     * Earlier versions stored a 64-entry table on each Chess960 position. The
     * same mask can be derived from the current king and rook squares, avoiding a
     * per-position array allocation while preserving the branch-free call site
     * when no castling rights remain.
     * </p>
     *
     * @param first first touched square
     * @param second second touched square
     * @return castling-right mask retained after both squares are touched
     */
    private int castlingKeepMask(int first, int second) {
        return castlingKeepMask(first) & castlingKeepMask(second);
    }

    /**
     * Computes the castling rights retained after touching one square.
     *
     * @param square touched square
     * @return castling-right mask retained after touching the square
     */
    private int castlingKeepMask(int square) {
        int keep = WHITE_KINGSIDE | WHITE_QUEENSIDE | BLACK_KINGSIDE | BLACK_QUEENSIDE;
        if (square == whiteKingSquare) {
            keep &= ~(WHITE_KINGSIDE | WHITE_QUEENSIDE);
        }
        if (square == blackKingSquare) {
            keep &= ~(BLACK_KINGSIDE | BLACK_QUEENSIDE);
        }
        if (square == whiteKingsideRookSquare) {
            keep &= ~WHITE_KINGSIDE;
        }
        if (square == whiteQueensideRookSquare) {
            keep &= ~WHITE_QUEENSIDE;
        }
        if (square == blackKingsideRookSquare) {
            keep &= ~BLACK_KINGSIDE;
        }
        if (square == blackQueensideRookSquare) {
            keep &= ~BLACK_QUEENSIDE;
        }
        return keep;
    }

    /**
     * Returns the castling right represented by a king move target.
     *
     * @param moving moving piece index
     * @param target encoded move target
     * @return castling-right bit or zero
     */
    int castlingRightForMove(int moving, int target) {
        if (moving == WHITE_KING) {
            if (canCastle(WHITE_KINGSIDE) && target == castlingMoveTarget(WHITE_KINGSIDE)) {
                return WHITE_KINGSIDE;
            }
            if (canCastle(WHITE_QUEENSIDE) && target == castlingMoveTarget(WHITE_QUEENSIDE)) {
                return WHITE_QUEENSIDE;
            }
        } else if (moving == BLACK_KING) {
            if (canCastle(BLACK_KINGSIDE) && target == castlingMoveTarget(BLACK_KINGSIDE)) {
                return BLACK_KINGSIDE;
            }
            if (canCastle(BLACK_QUEENSIDE) && target == castlingMoveTarget(BLACK_QUEENSIDE)) {
                return BLACK_QUEENSIDE;
            }
        }
        return 0;
    }

    /**
     * Moves the rook for a standard or Chess960 castling move.
     *
     * @param white true for White
     * @param right castling-right bit
     * @param state undo state receiving rook-move metadata
     */
    private void moveCastlingRook(boolean white, int right, State state) {
        int rook = white ? WHITE_ROOK : BLACK_ROOK;
        int rookFrom = castlingRookSquare(right);
        int rookTo = castlingRookTarget(right);
        state.rook = rook;
        state.rookFrom = rookFrom;
        state.rookTo = rookTo;
        movePiece(rook, rookFrom, rookTo);
    }

    /**
     * Places one piece on a square and updates all cached occupancy data.
     *
     * @param piece piece index
     * @param square square
     */
    void setPiece(int piece, int square) {
        PositionStateSupport.setPiece(this, piece, square);
    }

    /**
     * Removes any piece from a square for controlled analysis-only mutation.
     *
     * @param square square to clear
     */
    protected void clearAnalysisSquare(int square) {
        int piece = pieceIndexAt(square);
        if (piece >= 0) {
            clearPiece(piece, square);
        }
    }

    /**
     * Places a repository piece code on a square for controlled analysis-only mutation.
     *
     * @param square target square
     * @param piece repository piece code
     */
    protected void setAnalysisSquare(int square, byte piece) {
        int index = pieceIndex(piece);
        if (index >= 0) {
            setPiece(index, square);
        }
    }

    /**
     * Removes one piece from a square and updates all cached occupancy data.
     *
     * @param piece piece index
     * @param square square
     */
    private void clearPiece(int piece, int square) {
        PositionStateSupport.clearPiece(this, piece, square);
    }

    /**
     * Moves one piece between two squares using the shared set/clear helpers.
     *
     * @param piece piece index
     * @param from origin
     * @param to target
     */
    private void movePiece(int piece, int from, int to) {
        PositionStateSupport.movePiece(this, piece, from, to);
    }

    /**
     * Returns the piece index that should occupy the target square after a move.
     *
     * @param moving moving piece
     * @param promotion promotion code
     * @return piece index
     */
    private int promotionPieceIndex(int moving, int promotion) {
        return PositionStateSupport.promotionPieceIndex(moving, promotion);
    }

    /**
     * Describes a piece pinned to its own king by an enemy slider.
     *
     * <p>
     * The piece code is stored in repository {@link Piece} format so callers can
     * feed it directly into existing tag text helpers.
     * </p>
     */
    public static final class PinInfo {

        /**
         * Square occupied by the pinned piece.
         */
        public final byte pinnedSquare;

        /**
         * Square occupied by the pinning enemy piece.
         */
        public final byte pinnerSquare;

        /**
         * Repository piece code for the pinning enemy piece.
         */
        public final byte pinnerPiece;

        /**
         * Creates immutable pin metadata.
         *
         * @param pinnedSquare square occupied by the pinned piece
         * @param pinnerSquare square occupied by the pinning piece
         * @param pinnerPiece repository piece code for the pinning piece
         */
        private PinInfo(byte pinnedSquare, byte pinnerSquare, byte pinnerPiece) {
            this.pinnedSquare = pinnedSquare;
            this.pinnerSquare = pinnerSquare;
            this.pinnerPiece = pinnerPiece;
        }
    }

    /**
     * Mutable undo state for allocation-free make/undo recursion.
     *
     * <p>
     * Instances are owned by callers and reused per search ply. The fields mirror
     * every reversible part of {@link Position} that can change during
     * {@link #play(short, State)} or {@link #playNull(State)}.
     * </p>
     */
    public static final class State {

        /**
         * Creates an empty undo state for one make/undo slot.
         */
        public State() {
            // filled by Position.play(short, State)
        }

        /**
         * Piece index moved by the encoded move.
         */
        private int moving;

        /**
         * Captured piece index, or -1 when the move was not a capture.
         */
        private int captured;

        /**
         * Square where the captured piece was removed.
         *
         * <p>
         * This differs from the move target square for en-passant captures.
         * </p>
         */
        private int capturedSquare;

        /**
         * Actual king target square for a castling move.
         */
        private int kingTo;

        /**
         * Castling-right mask before the move was applied.
         */
        private int castlingRights;

        /**
         * En-passant target square before the move was applied.
         */
        private byte enPassantSquare;

        /**
         * Halfmove clock before the move was applied.
         */
        private short halfMoveClock;

        /**
         * Fullmove number before the move was applied.
         */
        private short fullMoveNumber;

        /**
         * Side-to-move flag before the move was applied.
         */
        private boolean whiteToMove;

        /**
         * Castling rook piece index, or -1 when the move was not castling.
         */
        private int rook;

        /**
         * Castling rook origin square.
         */
        private int rookFrom;

        /**
         * Castling rook target square.
         */
        private int rookTo;

        /**
         * Whether the move captured by en-passant.
         */
        private boolean enPassantCapture;

        /**
         * Whether the move was castling.
         */
        private boolean castle;

        /**
         * Returns whether the move captured a piece.
         *
         * @return true when captured
         */
        public boolean capture() {
            return captured >= 0;
        }

        /**
         * Returns whether the move was an en-passant capture.
         *
         * @return true when en-passant
         */
        public boolean enPassantCapture() {
            return enPassantCapture;
        }

        /**
         * Returns whether the move was standard castling.
         *
         * @return true when castling
         */
        public boolean castle() {
            return castle;
        }
    }

}
