package chess.core;

/**
 * Legal move generation, attack tests, and node-only perft.
 *
 * <p>
 * Public methods operate on {@link Position} and produce compact
 * {@code chess.core.Move}-compatible {@code short} moves. Internal paths reuse
 * caller-owned move lists and undo states to avoid allocation during perft.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class MoveGenerator {

    /**
     * Promotion code for queen promotions, matching {@code chess.core.Move}.
     */
    private static final byte PROMOTION_QUEEN = 4;

    /**
     * Promotion code for rook promotions, matching {@code chess.core.Move}.
     */
    private static final byte PROMOTION_ROOK = 3;

    /**
     * Promotion code for bishop promotions, matching {@code chess.core.Move}.
     */
    private static final byte PROMOTION_BISHOP = 2;

    /**
     * Promotion code for knight promotions, matching {@code chess.core.Move}.
     */
    private static final byte PROMOTION_KNIGHT = 1;

    /**
     * Knight attack masks indexed by origin square.
     */
    static final long[] KNIGHT_ATTACKS = new long[64];

    /**
     * King attack masks indexed by origin square.
     */
    static final long[] KING_ATTACKS = new long[64];

    /**
     * Squares from which a White pawn would attack a target square.
     */
    static final long[] WHITE_PAWN_ATTACKS = new long[64];

    /**
     * Squares from which a Black pawn would attack a target square.
     */
    static final long[] BLACK_PAWN_ATTACKS = new long[64];

    /**
     * Empty-board rook lines used as cheap slider-presence guards.
     */
    private static final long[] ROOK_LINES = new long[64];

    /**
     * Empty-board bishop lines used as cheap slider-presence guards.
     */
    private static final long[] BISHOP_LINES = new long[64];

    static {
        for (int square = 0; square < 64; square++) {
            KNIGHT_ATTACKS[square] = jumpAttacks(square, new int[][] {
                    { 1, 2 }, { 2, 1 }, { 2, -1 }, { 1, -2 },
                    { -1, -2 }, { -2, -1 }, { -2, 1 }, { -1, 2 }
            });
            KING_ATTACKS[square] = jumpAttacks(square, new int[][] {
                    { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
                    { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
            });
            WHITE_PAWN_ATTACKS[square] = pawnAttacks(square, true);
            BLACK_PAWN_ATTACKS[square] = pawnAttacks(square, false);
            ROOK_LINES[square] = line(square, 1, 0) | line(square, -1, 0)
                    | line(square, 0, 1) | line(square, 0, -1);
            BISHOP_LINES[square] = line(square, 1, 1) | line(square, 1, -1)
                    | line(square, -1, 1) | line(square, -1, -1);
        }
    }

    /**
     * Prevents instantiation of this stateless utility class.
     */
    private MoveGenerator() {
        // utility
    }

    /**
     * Generates all legal moves for the side to move.
     *
     * <p>
     * The method first emits pseudo-legal moves, then uses fast legality filters
     * for the common no-check case and make/undo validation for the remaining
     * exceptional cases.
     * </p>
     *
     * @param position position
     * @return legal moves
     */
    public static MoveList generateLegalMoves(Position position) {
        MoveList pseudo = new MoveList();
        generatePseudoLegalMoves(position, pseudo);
        MoveList legal = new MoveList(Math.max(1, pseudo.size()));
        boolean white = position.isWhiteToMove();
        boolean inCheck = isKingAttacked(position, white);
        long pinned = inCheck ? 0L : pinnedPieces(position, white);
        int king = position.kingSquare(white);
        int enPassant = position.enPassantSquare();
        Position.State state = new Position.State();
        for (int i = 0; i < pseudo.size(); i++) {
            short move = pseudo.raw(i);
            if (!inCheck && isUsuallyLegal(position, move, pinned, king, enPassant)) {
                legal.addFast(move);
                continue;
            }
            position.play(move, state);
            if (!isKingAttacked(position, white)) {
                legal.addFast(move);
            }
            position.undo(move, state);
        }
        return legal;
    }

    /**
     * Generates pseudo-legal moves for the side to move.
     *
     * <p>
     * Pseudo-legal moves obey piece movement and board occupancy rules but may
     * leave the moving side's king in check.
     * </p>
     *
     * @param position position
     * @return pseudo-legal moves
     */
    public static MoveList generatePseudoLegalMoves(Position position) {
        MoveList moves = new MoveList();
        generatePseudoLegalMoves(position, moves);
        return moves;
    }

    /**
     * Generates pseudo-legal moves into a caller-owned list.
     *
     * <p>
     * The target list is cleared before moves are added.
     * </p>
     *
     * @param position position
     * @param moves target list
     */
    public static void generatePseudoLegalMoves(Position position, MoveList moves) {
        moves.clear();
        boolean white = position.isWhiteToMove();
        if (white) {
            addWhitePawnMoves(position, moves);
            addPieceMoves(position, moves, Position.WHITE_KNIGHT, KNIGHT_ATTACKS);
            addBishopMoves(position, moves, Position.WHITE_BISHOP);
            addRookMoves(position, moves, Position.WHITE_ROOK);
            addQueenMoves(position, moves, Position.WHITE_QUEEN);
            addPieceMoves(position, moves, Position.WHITE_KING, KING_ATTACKS);
            addWhiteCastles(position, moves);
        } else {
            addBlackPawnMoves(position, moves);
            addPieceMoves(position, moves, Position.BLACK_KNIGHT, KNIGHT_ATTACKS);
            addBishopMoves(position, moves, Position.BLACK_BISHOP);
            addRookMoves(position, moves, Position.BLACK_ROOK);
            addQueenMoves(position, moves, Position.BLACK_QUEEN);
            addPieceMoves(position, moves, Position.BLACK_KING, KING_ATTACKS);
            addBlackCastles(position, moves);
        }
    }

    /**
     * Runs node-only perft from a position.
     *
     * <p>
     * This path counts legal leaf nodes only. Use {@link chess.debug.Perft} when
     * detailed counters such as captures and checks are required.
     * </p>
     *
     * @param position position
     * @param depth non-negative perft depth
     * @return leaf count
     */
    public static long perft(Position position, int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("depth must be non-negative");
        }
        if (depth == 0) {
            return 1L;
        }
        PerftContext context = new PerftContext(depth);
        return perft(position, depth, context, 0);
    }

    /**
     * Counts legal moves without allocating a returned move list.
     *
     * @param position position
     * @return legal move count
     */
    public static int legalMoveCount(Position position) {
        MoveList moves = new MoveList();
        Position.State state = new Position.State();
        return legalMoveCount(position, moves, state);
    }

    /**
     * Returns whether the side to move has at least one legal move.
     *
     * @param position position
     * @return true when a legal move exists
     */
    public static boolean hasLegalMove(Position position) {
        MoveList moves = new MoveList();
        Position.State state = new Position.State();
        return hasLegalMove(position, moves, state);
    }

    /**
     * Recursively counts leaf nodes using mutable make/undo.
     *
     * @param position mutable position at the current ply
     * @param depth remaining depth
     * @param context reusable move and undo-state scratch objects
     * @param ply current recursion ply
     * @return legal leaf-node count
     */
    private static long perft(Position position, int depth, PerftContext context, int ply) {
        MoveList moves = context.moves[ply];
        Position.State state = context.states[ply];
        if (depth == 1) {
            return legalMoveCount(position, moves, state);
        }
        generatePseudoLegalMoves(position, moves);
        boolean white = position.isWhiteToMove();
        boolean inCheck = isKingAttacked(position, white);
        long pinned = inCheck ? 0L : pinnedPieces(position, white);
        int king = position.kingSquare(white);
        int enPassant = position.enPassantSquare();
        long nodes = 0L;
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            boolean usuallyLegal = !inCheck && isUsuallyLegal(position, move, pinned, king, enPassant);
            position.play(move, state);
            if (usuallyLegal || !isKingAttacked(position, white)) {
                nodes += perft(position, depth - 1, context, ply + 1);
            }
            position.undo(move, state);
        }
        return nodes;
    }

    /**
     * Counts legal moves using caller-owned scratch objects.
     *
     * @param position position to inspect
     * @param moves reusable move list
     * @param state reusable undo state
     * @return legal move count
     */
    public static int legalMoveCount(Position position, MoveList moves, Position.State state) {
        boolean white = position.isWhiteToMove();
        boolean inCheck = isKingAttacked(position, white);
        long pinned = inCheck ? 0L : pinnedPieces(position, white);
        if (!inCheck && pinned == 0L && position.enPassantSquare() == Field.NO_SQUARE) {
            return legalMoveCountNoPinsNoEnPassant(position, white, moves);
        }
        generatePseudoLegalMoves(position, moves);
        int count = 0;
        int king = position.kingSquare(white);
        int enPassant = position.enPassantSquare();
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            if (!inCheck && isUsuallyLegal(position, move, pinned, king, enPassant)) {
                count++;
                continue;
            }
            position.play(move, state);
            if (!isKingAttacked(position, white)) {
                count++;
            }
            position.undo(move, state);
        }
        return count;
    }

    /**
     * Counts legal moves in the common no-check, no-pin, no-en-passant case.
     *
     * <p>
     * This is the depth-one bulk counter used by node-only perft. It avoids
     * materializing most moves and validates only king destinations and castling.
     * </p>
     *
     * @param position position to inspect
     * @param white true when White is to move
     * @param moves scratch list used only for castling moves
     * @return legal move count
     */
    private static int legalMoveCountNoPinsNoEnPassant(
            Position position,
            boolean white,
            MoveList moves) {
        int count = white ? countWhitePawnMoves(position) : countBlackPawnMoves(position);
        count += countPieceMoves(
                position,
                position.pieces(white ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT),
                white);
        count += countBishopMoves(
                position,
                position.pieces(white ? Position.WHITE_BISHOP : Position.BLACK_BISHOP),
                white);
        count += countRookMoves(
                position,
                position.pieces(white ? Position.WHITE_ROOK : Position.BLACK_ROOK),
                white);
        count += countQueenMoves(
                position,
                position.pieces(white ? Position.WHITE_QUEEN : Position.BLACK_QUEEN),
                white);
        count += countLegalKingMoves(position, white);

        moves.clear();
        if (white) {
            addWhiteCastles(position, moves);
        } else {
            addBlackCastles(position, moves);
        }
        return count + moves.size();
    }

    /**
     * Counts White pawn moves without pins or en-passant.
     *
     * @param position position to inspect
     * @return legal White pawn move count in the simplified leaf case
     */
    private static int countWhitePawnMoves(Position position) {
        long pawns = position.pieces(Position.WHITE_PAWN);
        long empty = ~position.occupancy();
        long enemies = position.blackOccupancy() & ~position.pieces(Position.BLACK_KING);
        long single = (pawns >>> 8) & empty;
        return countWhitePawnTargets(single)
                + Long.bitCount(((single & Bits.RANK_3) >>> 8) & empty)
                + countWhitePawnTargets(((pawns & ~Bits.FILE_A) >>> 9) & enemies)
                + countWhitePawnTargets(((pawns & ~Bits.FILE_H) >>> 7) & enemies);
    }

    /**
     * Counts Black pawn moves without pins or en-passant.
     *
     * @param position position to inspect
     * @return legal Black pawn move count in the simplified leaf case
     */
    private static int countBlackPawnMoves(Position position) {
        long pawns = position.pieces(Position.BLACK_PAWN);
        long empty = ~position.occupancy();
        long enemies = position.whiteOccupancy() & ~position.pieces(Position.WHITE_KING);
        long single = (pawns << 8) & empty;
        return countBlackPawnTargets(single)
                + Long.bitCount(((single & Bits.RANK_6) << 8) & empty)
                + countBlackPawnTargets(((pawns & ~Bits.FILE_A) << 7) & enemies)
                + countBlackPawnTargets(((pawns & ~Bits.FILE_H) << 9) & enemies);
    }

    /**
     * Counts White pawn target squares, expanding promotions to four moves.
     *
     * @param targets target-square mask
     * @return move count represented by those targets
     */
    private static int countWhitePawnTargets(long targets) {
        long promotions = targets & Bits.RANK_8;
        return Long.bitCount(targets) + Long.bitCount(promotions) * 3;
    }

    /**
     * Counts Black pawn target squares, expanding promotions to four moves.
     *
     * @param targets target-square mask
     * @return move count represented by those targets
     */
    private static int countBlackPawnTargets(long targets) {
        long promotions = targets & Bits.RANK_1;
        return Long.bitCount(targets) + Long.bitCount(promotions) * 3;
    }

    /**
     * Counts knight moves for a set of pieces.
     *
     * @param position position to inspect
     * @param pieces source-piece mask
     * @param white true for White pieces
     * @return legal target count in the simplified leaf case
     */
    private static int countPieceMoves(Position position, long pieces, boolean white) {
        long own = position.occupancy(white);
        long enemyKing = position.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        int count = 0;
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            count += Long.bitCount(KNIGHT_ATTACKS[from] & ~own & ~enemyKing);
        }
        return count;
    }

    /**
     * Counts bishop moves for a set of bishops.
     *
     * @param position position to inspect
     * @param pieces bishop source mask
     * @param white true for White bishops
     * @return legal target count in the simplified leaf case
     */
    private static int countBishopMoves(Position position, long pieces, boolean white) {
        long own = position.occupancy(white);
        long enemyKing = position.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        long occupancy = position.occupancy();
        int count = 0;
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            count += Long.bitCount(bishopAttacks(from, occupancy) & ~own & ~enemyKing);
        }
        return count;
    }

    /**
     * Counts rook moves for a set of rooks.
     *
     * @param position position to inspect
     * @param pieces rook source mask
     * @param white true for White rooks
     * @return legal target count in the simplified leaf case
     */
    private static int countRookMoves(Position position, long pieces, boolean white) {
        long own = position.occupancy(white);
        long enemyKing = position.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        long occupancy = position.occupancy();
        int count = 0;
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            count += Long.bitCount(rookAttacks(from, occupancy) & ~own & ~enemyKing);
        }
        return count;
    }

    /**
     * Counts queen moves for a set of queens.
     *
     * @param position position to inspect
     * @param pieces queen source mask
     * @param white true for White queens
     * @return legal target count in the simplified leaf case
     */
    private static int countQueenMoves(Position position, long pieces, boolean white) {
        long own = position.occupancy(white);
        long enemyKing = position.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        long occupancy = position.occupancy();
        int count = 0;
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            long targets = bishopAttacks(from, occupancy) | rookAttacks(from, occupancy);
            count += Long.bitCount(targets & ~own & ~enemyKing);
        }
        return count;
    }

    /**
     * Counts legal king moves in the simplified leaf case.
     *
     * <p>
     * Unlike non-king pieces, king moves must always be checked against attacks
     * on the destination square.
     * </p>
     *
     * @param position position to inspect
     * @param white true for the White king
     * @return legal king move count
     */
    private static int countLegalKingMoves(Position position, boolean white) {
        int king = position.kingSquare(white);
        long targets = KING_ATTACKS[king]
                & ~position.occupancy(white)
                & ~position.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        int count = 0;
        long fromMask = 1L << king;
        long baseOccupancy = position.occupancy() & ~fromMask;
        while (targets != 0L) {
            int to = Long.numberOfTrailingZeros(targets);
            targets &= targets - 1L;
            long targetMask = 1L << to;
            if (!isSquareAttackedAfterKingMove(position, to, !white, baseOccupancy | targetMask)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks legal move existence using caller-owned scratch objects.
     *
     * @param position position to inspect
     * @param moves reusable move list
     * @param state reusable undo state
     * @return true when at least one legal move exists
     */
    public static boolean hasLegalMove(Position position, MoveList moves, Position.State state) {
        generatePseudoLegalMoves(position, moves);
        boolean white = position.isWhiteToMove();
        boolean inCheck = isKingAttacked(position, white);
        long pinned = inCheck ? 0L : pinnedPieces(position, white);
        int king = position.kingSquare(white);
        int enPassant = position.enPassantSquare();
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            if (!inCheck && isUsuallyLegal(position, move, pinned, king, enPassant)) {
                return true;
            }
            position.play(move, state);
            boolean legal = !isKingAttacked(position, white);
            position.undo(move, state);
            if (legal) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a pseudo-legal move is legal without make/undo.
     *
     * <p>
     * This predicate is valid only when the moving side is not currently in check.
     * It rejects king moves, pinned-piece moves, and en-passant moves, because
     * those cases still require explicit king-safety validation.
     * </p>
     *
     * @param position position containing the move
     * @param move encoded move
     * @param pinned absolute pinned-piece mask for the moving side
     * @param king moving side's king square
     * @param enPassant current en-passant target square
     * @return true when the move is legal without make/undo validation
     */
    public static boolean isUsuallyLegal(
            Position position,
            short move,
            long pinned,
            int king,
            int enPassant) {
        int from = move & 0x3F;
        if (from == king) {
            return false;
        }
        if ((pinned & (1L << from)) != 0L) {
            return false;
        }
        if (enPassant == Field.NO_SQUARE) {
            return true;
        }
        int to = (move >>> 6) & 0x3F;
        int moving = position.pieceIndexAt(from);
        return !isEnPassant(position, moving, to);
    }

    /**
     * Returns whether a pseudo-legal move is an en-passant capture.
     *
     * @param position position containing the move
     * @param moving moving piece index
     * @param to target square
     * @return true when the move is en-passant
     */
    private static boolean isEnPassant(Position position, int moving, int to) {
        return (moving == Position.WHITE_PAWN || moving == Position.BLACK_PAWN)
                && to == position.enPassantSquare()
                && position.pieceIndexAt(to) < 0;
    }

    /**
     * Finds pieces absolutely pinned to one side's king.
     *
     * @param position position to inspect
     * @param white true for White pinned pieces
     * @return mask of pinned friendly pieces
     */
    public static long pinnedPieces(Position position, boolean white) {
        int king = position.kingSquare(white);
        if (king == Field.NO_SQUARE) {
            return 0L;
        }
        long enemyRookQueen = position.pieces(white ? Position.BLACK_ROOK : Position.WHITE_ROOK)
                | position.pieces(white ? Position.BLACK_QUEEN : Position.WHITE_QUEEN);
        long enemyBishopQueen = position.pieces(white ? Position.BLACK_BISHOP : Position.WHITE_BISHOP)
                | position.pieces(white ? Position.BLACK_QUEEN : Position.WHITE_QUEEN);
        long own = position.occupancy(white);
        long all = position.occupancy();
        long pinned = 0L;
        if ((ROOK_LINES[king] & enemyRookQueen) != 0L) {
            pinned |= pinnedInDirection(king, own, all, enemyRookQueen, 1, 0)
                    | pinnedInDirection(king, own, all, enemyRookQueen, -1, 0)
                    | pinnedInDirection(king, own, all, enemyRookQueen, 0, 1)
                    | pinnedInDirection(king, own, all, enemyRookQueen, 0, -1);
        }
        if ((BISHOP_LINES[king] & enemyBishopQueen) != 0L) {
            pinned |= pinnedInDirection(king, own, all, enemyBishopQueen, 1, 1)
                    | pinnedInDirection(king, own, all, enemyBishopQueen, 1, -1)
                    | pinnedInDirection(king, own, all, enemyBishopQueen, -1, 1)
                    | pinnedInDirection(king, own, all, enemyBishopQueen, -1, -1);
        }
        return pinned;
    }

    /**
     * Finds a pinned piece in one ray direction from the king.
     *
     * @param king king square
     * @param own friendly occupancy mask
     * @param all combined occupancy mask
     * @param enemySliders enemy slider mask for this ray type
     * @param fileDelta file step for the ray
     * @param rowDelta row step for the ray
     * @return pinned piece mask, or zero when no pin exists
     */
    private static long pinnedInDirection(
            int king,
            long own,
            long all,
            long enemySliders,
            int fileDelta,
            int rowDelta) {
        int file = (king & 7) + fileDelta;
        int row = (king >>> 3) + rowDelta;
        long candidate = 0L;
        while (file >= 0 && file < 8 && row >= 0 && row < 8) {
            long square = 1L << (row * 8 + file);
            if ((all & square) != 0L) {
                if (candidate == 0L && (own & square) != 0L) {
                    candidate = square;
                } else {
                    return candidate != 0L && (enemySliders & square) != 0L ? candidate : 0L;
                }
            }
            file += fileDelta;
            row += rowDelta;
        }
        return 0L;
    }

    /**
     * Returns whether one color's king is currently attacked.
     *
     * @param position position
     * @param whiteKing true for White king
     * @return true when attacked
     */
    public static boolean isKingAttacked(Position position, boolean whiteKing) {
        byte king = position.kingSquare(whiteKing);
        return king != Field.NO_SQUARE && isSquareAttacked(position, king, !whiteKing);
    }

    /**
     * Returns whether a square is attacked by one side.
     *
     * @param position position
     * @param square target square
     * @param byWhite true for White attackers
     * @return true when attacked
     */
    public static boolean isSquareAttacked(Position position, int square, boolean byWhite) {
        return isSquareAttacked(position, square, byWhite, position.occupancy(), 0L);
    }

    /**
     * Returns whether a king target is attacked after the king has moved there.
     *
     * <p>
     * The supplied occupancy already removes the king from its origin and places
     * it on the target. Captured pieces on the target are masked out from slider
     * checks by removing that square from attacker bitboards.
     * </p>
     *
     * @param position position before the king move
     * @param square target square
     * @param byWhite true when checking White attacks
     * @param occupancy occupancy after the king move
     * @return true when the target square is attacked
     */
    private static boolean isSquareAttackedAfterKingMove(
            Position position,
            int square,
            boolean byWhite,
            long occupancy) {
        return isSquareAttacked(position, square, byWhite, occupancy, 1L << square);
    }

    /**
     * Returns whether a square is attacked using a supplied occupancy.
     *
     * @param position position
     * @param square target square
     * @param byWhite true for White attackers
     * @param occupancy occupied-square mask used for slider attacks
     * @param excludedAttacker attacker square to mask out, or 0
     * @return true when attacked
     */
    private static boolean isSquareAttacked(
            Position position,
            int square,
            boolean byWhite,
            long occupancy,
            long excludedAttacker) {
        return isAttackedByPawnsOrLeapers(position, square, byWhite)
                || isAttackedBySliders(position, square, byWhite, occupancy, excludedAttacker);
    }

    /**
     * Returns whether pawns, knights, or kings of one side attack a square.
     *
     * @param position position
     * @param square target square
     * @param byWhite true for White attackers
     * @return true when a non-slider attacks the square
     */
    private static boolean isAttackedByPawnsOrLeapers(Position position, int square, boolean byWhite) {
        int pawn = byWhite ? Position.WHITE_PAWN : Position.BLACK_PAWN;
        int knight = byWhite ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT;
        int king = byWhite ? Position.WHITE_KING : Position.BLACK_KING;
        long pawnAttacks = byWhite ? BLACK_PAWN_ATTACKS[square] : WHITE_PAWN_ATTACKS[square];
        return (pawnAttacks & position.pieces(pawn)) != 0L
                || (KNIGHT_ATTACKS[square] & position.pieces(knight)) != 0L
                || (KING_ATTACKS[square] & position.pieces(king)) != 0L;
    }

    /**
     * Returns whether bishops, rooks, or queens of one side attack a square.
     *
     * @param position position
     * @param square target square
     * @param byWhite true for White attackers
     * @param occupancy occupied-square mask used for slider attacks
     * @param excludedAttacker attacker square to mask out, or 0
     * @return true when a slider attacks the square
     */
    private static boolean isAttackedBySliders(
            Position position,
            int square,
            boolean byWhite,
            long occupancy,
            long excludedAttacker) {
        long includedAttackers = ~excludedAttacker;
        int bishop = byWhite ? Position.WHITE_BISHOP : Position.BLACK_BISHOP;
        int rook = byWhite ? Position.WHITE_ROOK : Position.BLACK_ROOK;
        int queen = byWhite ? Position.WHITE_QUEEN : Position.BLACK_QUEEN;
        long queens = position.pieces(queen) & includedAttackers;
        long bishopQueens = (position.pieces(bishop) & includedAttackers) | queens;
        if ((BISHOP_LINES[square] & bishopQueens) != 0L
                && (bishopAttacks(square, occupancy) & bishopQueens) != 0L) {
            return true;
        }
        long rookQueens = (position.pieces(rook) & includedAttackers) | queens;
        return (ROOK_LINES[square] & rookQueens) != 0L
                && (rookAttacks(square, occupancy) & rookQueens) != 0L;
    }

    /**
     * Returns empty-board knight attacks from one square.
     *
     * @param square origin square
     * @return attack mask
     */
    public static long knightAttacks(int square) {
        return KNIGHT_ATTACKS[square];
    }

    /**
     * Returns empty-board king attacks from one square.
     *
     * @param square origin square
     * @return attack mask
     */
    public static long kingAttacks(int square) {
        return KING_ATTACKS[square];
    }

    /**
     * Returns bishop attacks for an occupancy.
     *
     * @param square origin square
     * @param occupancy occupied-square mask
     * @return attack mask
     */
    public static long bishopAttacks(int square, long occupancy) {
        return SlidingAttacks.bishopAttacks(square, occupancy);
    }

    /**
     * Returns rook attacks for an occupancy.
     *
     * @param square origin square
     * @param occupancy occupied-square mask
     * @return attack mask
     */
    public static long rookAttacks(int square, long occupancy) {
        return SlidingAttacks.rookAttacks(square, occupancy);
    }

    /**
     * Adds pseudo-legal White pawn moves.
     *
     * @param position position
     * @param moves target list
     */
    private static void addWhitePawnMoves(Position position, MoveList moves) {
        long pawns = position.pieces(Position.WHITE_PAWN);
        long occupancy = position.occupancy();
        long empty = ~occupancy;
        long enemies = position.blackOccupancy() & ~position.pieces(Position.BLACK_KING);
        int enPassant = position.enPassantSquare();
        long enPassantMask = enPassant == Field.NO_SQUARE ? 0L : 1L << enPassant;
        long capturable = enemies | enPassantMask;

        long single = (pawns >>> 8) & empty;
        addPawnTargets(moves, single & Bits.RANK_8, 8, true);
        long quiet = single & ~Bits.RANK_8;
        addPawnTargets(moves, quiet, 8, true);
        addPawnTargets(moves, ((single & Bits.RANK_3) >>> 8) & empty, 16, true);
        addPawnTargets(moves, ((pawns & ~Bits.FILE_A) >>> 9) & capturable, 9, true);
        addPawnTargets(moves, ((pawns & ~Bits.FILE_H) >>> 7) & capturable, 7, true);
    }

    /**
     * Adds pseudo-legal Black pawn moves.
     *
     * @param position position
     * @param moves target list
     */
    private static void addBlackPawnMoves(Position position, MoveList moves) {
        long pawns = position.pieces(Position.BLACK_PAWN);
        long occupancy = position.occupancy();
        long empty = ~occupancy;
        long enemies = position.whiteOccupancy() & ~position.pieces(Position.WHITE_KING);
        int enPassant = position.enPassantSquare();
        long enPassantMask = enPassant == Field.NO_SQUARE ? 0L : 1L << enPassant;
        long capturable = enemies | enPassantMask;

        long single = (pawns << 8) & empty;
        addPawnTargets(moves, single & Bits.RANK_1, -8, false);
        long quiet = single & ~Bits.RANK_1;
        addPawnTargets(moves, quiet, -8, false);
        addPawnTargets(moves, ((single & Bits.RANK_6) << 8) & empty, -16, false);
        addPawnTargets(moves, ((pawns & ~Bits.FILE_A) << 7) & capturable, -7, false);
        addPawnTargets(moves, ((pawns & ~Bits.FILE_H) << 9) & capturable, -9, false);
    }

    /**
     * Adds one pawn advance or capture, expanding promotions when needed.
     *
     * @param moves target move list
     * @param from origin square
     * @param to target square
     * @param white true for a White pawn
     */
    private static void addPawnAdvance(MoveList moves, int from, int to, boolean white) {
        if ((white && to < 8) || (!white && to >= 56)) {
            moves.addFast(move(from, to, PROMOTION_QUEEN));
            moves.addFast(move(from, to, PROMOTION_ROOK));
            moves.addFast(move(from, to, PROMOTION_BISHOP));
            moves.addFast(move(from, to, PROMOTION_KNIGHT));
        } else {
            moves.addFast(move(from, to));
        }
    }

    /**
     * Adds pawn moves for target squares whose origin is a fixed offset away.
     *
     * @param moves target move list
     * @param targets target-square mask
     * @param fromOffset offset from target square back to origin square
     * @param white true for White pawns
     */
    private static void addPawnTargets(MoveList moves, long targets, int fromOffset, boolean white) {
        while (targets != 0L) {
            int to = Long.numberOfTrailingZeros(targets);
            targets &= targets - 1L;
            addPawnAdvance(moves, to + fromOffset, to, white);
        }
    }

    /**
     * Adds pseudo-legal non-sliding piece moves.
     *
     * @param position position to inspect
     * @param moves target move list
     * @param pieceIndex moving piece index
     * @param attackTable precomputed attack table indexed by origin square
     */
    private static void addPieceMoves(
            Position position,
            MoveList moves,
            int pieceIndex,
            long[] attackTable) {
        boolean white = pieceIndex < Position.BLACK_PAWN;
        long own = position.occupancy(white);
        long enemyKing = position.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        long pieces = position.pieces(pieceIndex);
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            addTargets(moves, from, attackTable[from] & ~own & ~enemyKing);
        }
    }

    /**
     * Adds pseudo-legal bishop moves.
     *
     * @param position position to inspect
     * @param moves target move list
     * @param pieceIndex bishop piece index
     */
    private static void addBishopMoves(Position position, MoveList moves, int pieceIndex) {
        boolean white = pieceIndex < Position.BLACK_PAWN;
        long own = position.occupancy(white);
        long enemyKing = position.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        long occupancy = position.occupancy();
        long pieces = position.pieces(pieceIndex);
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            addTargets(moves, from, bishopAttacks(from, occupancy) & ~own & ~enemyKing);
        }
    }

    /**
     * Adds pseudo-legal rook moves.
     *
     * @param position position to inspect
     * @param moves target move list
     * @param pieceIndex rook piece index
     */
    private static void addRookMoves(Position position, MoveList moves, int pieceIndex) {
        boolean white = pieceIndex < Position.BLACK_PAWN;
        long own = position.occupancy(white);
        long enemyKing = position.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        long occupancy = position.occupancy();
        long pieces = position.pieces(pieceIndex);
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            addTargets(moves, from, rookAttacks(from, occupancy) & ~own & ~enemyKing);
        }
    }

    /**
     * Adds pseudo-legal queen moves.
     *
     * @param position position to inspect
     * @param moves target move list
     * @param pieceIndex queen piece index
     */
    private static void addQueenMoves(Position position, MoveList moves, int pieceIndex) {
        boolean white = pieceIndex < Position.BLACK_PAWN;
        long own = position.occupancy(white);
        long enemyKing = position.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        long occupancy = position.occupancy();
        long pieces = position.pieces(pieceIndex);
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            long targets = bishopAttacks(from, occupancy) | rookAttacks(from, occupancy);
            addTargets(moves, from, targets & ~own & ~enemyKing);
        }
    }

    /**
     * Adds one encoded move for every target square in a mask.
     *
     * @param moves target move list
     * @param from origin square
     * @param targets target-square mask
     */
    private static void addTargets(MoveList moves, int from, long targets) {
        while (targets != 0L) {
            int to = Long.numberOfTrailingZeros(targets);
            targets &= targets - 1L;
            moves.addFast(move(from, to));
        }
    }

    /**
     * Adds legal White castling moves.
     *
     * @param position position to inspect
     * @param moves target move list
     */
    private static void addWhiteCastles(Position position, MoveList moves) {
        addCastle(position, moves, true, Position.WHITE_KINGSIDE);
        addCastle(position, moves, true, Position.WHITE_QUEENSIDE);
    }

    /**
     * Adds legal Black castling moves.
     *
     * @param position position to inspect
     * @param moves target move list
     */
    private static void addBlackCastles(Position position, MoveList moves) {
        addCastle(position, moves, false, Position.BLACK_KINGSIDE);
        addCastle(position, moves, false, Position.BLACK_QUEENSIDE);
    }

    /**
     * Adds one standard or Chess960 castling move when legal.
     *
     * @param position position to inspect
     * @param moves target move list
     * @param white true for White
     * @param right castling right to test
     */
    private static void addCastle(Position position, MoveList moves, boolean white, int right) {
        if (!position.canCastle(right)) {
            return;
        }
        int kingFrom = position.kingSquare(white);
        int rookFrom = position.castlingRookSquare(right);
        int kingTo = position.castlingKingTarget(right);
        int rookTo = position.castlingRookTarget(right);
        if (kingFrom == Field.NO_SQUARE
                || rookFrom == Field.NO_SQUARE
                || position.pieceAt(kingFrom) != (white ? Piece.WHITE_KING : Piece.BLACK_KING)
                || position.pieceAt(rookFrom) != (white ? Piece.WHITE_ROOK : Piece.BLACK_ROOK)
                || isSquareAttacked(position, kingFrom, !white)) {
            return;
        }

        long occupancy = position.occupancy();
        long emptyMask = castleEmptyMask(kingFrom, kingTo, rookFrom, rookTo)
                & ~(1L << kingFrom)
                & ~(1L << rookFrom);
        if ((occupancy & emptyMask) != 0L || !safeKingCastlePath(position, kingFrom, kingTo, !white)) {
            return;
        }
        moves.addFast(move(kingFrom, position.castlingMoveTarget(right)));
    }

    /**
     * Returns all back-rank squares that must be empty before castling.
     *
     * @param kingFrom king source square
     * @param kingTo king target square
     * @param rookFrom rook source square
     * @param rookTo rook target square
     * @return empty-square mask before removing the king and rook
     */
    private static long castleEmptyMask(int kingFrom, int kingTo, int rookFrom, int rookTo) {
        return betweenInclusive(kingFrom, kingTo)
                | betweenInclusive(kingFrom, rookFrom)
                | betweenInclusive(rookFrom, rookTo);
    }

    /**
     * Returns whether no square traversed by the castling king is attacked.
     *
     * @param position position to inspect
     * @param kingFrom king source square
     * @param kingTo king final square
     * @param byWhite true when testing attacks by White
     * @return true when the king path is safe
     */
    private static boolean safeKingCastlePath(Position position, int kingFrom, int kingTo, boolean byWhite) {
        long path = betweenInclusive(kingFrom, kingTo) & ~(1L << kingFrom);
        while (path != 0L) {
            int square = Long.numberOfTrailingZeros(path);
            path &= path - 1L;
            if (isSquareAttacked(position, square, byWhite)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a same-rank inclusive square mask between two squares.
     *
     * @param a first square
     * @param b second square
     * @return inclusive mask, or both endpoints when ranks differ
     */
    private static long betweenInclusive(int a, int b) {
        long mask = 0L;
        int start = Math.min(a, b);
        int end = Math.max(a, b);
        if (Bits.rank(a) != Bits.rank(b)) {
            return (1L << a) | (1L << b);
        }
        for (int square = start; square <= end; square++) {
            mask |= 1L << square;
        }
        return mask;
    }

    /**
     * Builds empty-board jump attacks for one square.
     *
     * @param square origin square
     * @param deltas file/rank jump offsets
     * @return attack mask
     */
    private static long jumpAttacks(int square, int[][] deltas) {
        long attacks = 0L;
        int file = Bits.file(square);
        int rank = Bits.rank(square);
        for (int[] delta : deltas) {
            int targetFile = file + delta[0];
            int targetRank = rank + delta[1];
            if (Field.isOnBoard(targetFile, targetRank)) {
                attacks |= Bits.bit(Bits.square(targetFile, targetRank));
            }
        }
        return attacks;
    }

    /**
     * Builds one empty-board sliding line from a square.
     *
     * @param square origin square
     * @param fileDelta file step for the ray
     * @param rowDelta row step for the ray
     * @return ray mask excluding the origin square
     */
    private static long line(int square, int fileDelta, int rowDelta) {
        long out = 0L;
        int file = (square & 7) + fileDelta;
        int row = (square >>> 3) + rowDelta;
        while (file >= 0 && file < 8 && row >= 0 && row < 8) {
            out |= 1L << (row * 8 + file);
            file += fileDelta;
            row += rowDelta;
        }
        return out;
    }

    /**
     * Builds empty-board pawn attacks from one square.
     *
     * @param square origin square
     * @param white true for a White pawn
     * @return attack mask
     */
    static long pawnAttacks(int square, boolean white) {
        long attacks = 0L;
        int file = Bits.file(square);
        int rank = Bits.rank(square) + (white ? 1 : -1);
        if (Field.isOnBoard(file - 1, rank)) {
            attacks |= Bits.bit(Bits.square(file - 1, rank));
        }
        if (Field.isOnBoard(file + 1, rank)) {
            attacks |= Bits.bit(Bits.square(file + 1, rank));
        }
        return attacks;
    }

    /**
     * Encodes a non-promotion move.
     *
     * @param from origin square
     * @param to target square
     * @return compact encoded move
     */
    private static short move(int from, int to) {
        return (short) (from | (to << 6));
    }

    /**
     * Encodes a promotion move.
     *
     * @param from origin square
     * @param to target square
     * @param promotion promotion code
     * @return compact encoded move
     */
    private static short move(int from, int to, int promotion) {
        return (short) (from | (to << 6) | (promotion << 12));
    }

    /**
     * Scratch arrays for one node-only perft traversal.
     *
     * <p>
     * One move list and one undo state are reused per ply to keep recursive perft
     * allocation-free after context construction.
     * </p>
     */
    private static final class PerftContext {

        /**
         * Reusable pseudo-legal move lists indexed by recursion ply.
         */
        private final MoveList[] moves;

        /**
         * Reusable undo states indexed by recursion ply.
         */
        private final Position.State[] states;

        /**
         * Creates scratch arrays for a traversal of the requested depth.
         *
         * @param depth requested perft depth
         */
        private PerftContext(int depth) {
            this.moves = new MoveList[depth];
            this.states = new Position.State[depth];
            for (int i = 0; i < depth; i++) {
                moves[i] = new MoveList();
                states[i] = new Position.State();
            }
        }
    }
}
