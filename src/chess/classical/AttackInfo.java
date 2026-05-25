package chess.classical;

import static chess.classical.Wdl.*;

import chess.core.Bits;
import chess.core.MoveGenerator;
import chess.core.Piece;
import chess.core.Position;

/**
 * Reusable scratch state used by the classical WDL evaluator.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
/**
 * Reusable attack-map scratch data for one evaluator pass.
 *
 * <p>
 * The evaluator builds this structure once per position and then shares it
 * across mobility, threat, passed-pawn, king-safety, and space terms. Keeping
 * the maps together avoids recomputing piece attacks in every feature.
 * </p>
 */
final class AttackInfo {

    /**
     * Attacks by side and piece type.
     *
     * <p>
     * The first dimension uses {@link #WHITE}/{@link #BLACK}; the second uses
     * {@link Piece} constants, with slot {@link #ALL_ATTACKS} holding the
     * union of every piece attack for that side.
     * </p>
     */
    final long[][] attackedBy = new long[2][7];

    /**
     * Squares attacked by at least two pieces of a side.
     */
    final long[] attackedBy2 = new long[2];

    /**
     * King-zone masks for each side's king.
     */
    final long[] kingZone = new long[2];

    /**
     * Number of pieces attacking the enemy king zone.
     */
    final int[] kingAttackersCount = new int[2];

    /**
     * Weighted king-zone attacking pressure.
     */
    final int[] kingAttackersWeight = new int[2];

    /**
     * Number of attacked squares in the enemy king zone.
     */
    final int[] kingAttacksCount = new int[2];

    /**
     * Midgame mobility-like attack count by side.
     */
    final int[] mobilityMg = new int[2];

    /**
     * Endgame mobility score by side.
     */
    final int[] mobilityEg = new int[2];

    /**
     * Midgame piece placement/activity score by side.
     */
    final int[] pieceMg = new int[2];

    /**
     * Endgame piece placement/activity score by side.
     */
    final int[] pieceEg = new int[2];

    /**
     * Builds attack data for one position.
     *
     * <p>
     * This method must be called before reading any field in this object. It
     * resets stale values, initializes both king zones, and scans both sides.
     * </p>
     *
     * @param pos position to scan
     */
    void build(Position pos) {
        reset();
        int whiteKing = pos.kingSquare(true);
        int blackKing = pos.kingSquare(false);
        kingZone[WHITE] = kingZoneMask(whiteKing);
        kingZone[BLACK] = kingZoneMask(blackKing);
        long occupancy = pos.occupancy();
        long whitePawnAttacks = pawnAttackMask(true, pos.pieces(Position.WHITE_PAWN));
        long blackPawnAttacks = pawnAttackMask(false, pos.pieces(Position.BLACK_PAWN));
        scanSide(pos, true, occupancy, blackPawnAttacks);
        scanSide(pos, false, occupancy, whitePawnAttacks);
    }

    /**
     * Clears all scratch fields.
     *
     * <p>
     * Arrays are reused through {@link EvalBuffers}, so every scalar and
     * bitboard slot has to be reset before scanning a new position.
     * </p>
     */
    private void reset() {
        for (int side = 0; side < 2; side++) {
            attackedBy2[side] = 0L;
            kingZone[side] = 0L;
            kingAttackersCount[side] = 0;
            kingAttackersWeight[side] = 0;
            kingAttacksCount[side] = 0;
            mobilityMg[side] = 0;
            mobilityEg[side] = 0;
            pieceMg[side] = 0;
            pieceEg[side] = 0;
            for (int i = 0; i < attackedBy[side].length; i++) {
                attackedBy[side][i] = 0L;
            }
        }
    }

    /**
     * Scans all pieces for one side.
     *
     * <p>
     * Pawn attacks are intentionally scanned before non-pawn pieces so outpost
     * and safe-mobility terms can reuse the side's pawn-control map.
     * </p>
     *
     * @param pos current position
     * @param white side to scan
     * @param occupancy occupied-square mask
     * @param enemyPawnAttacks enemy pawn attacks
     */
    private void scanSide(Position pos, boolean white, long occupancy, long enemyPawnAttacks) {
        int side = sideIndex(white);
        long own = pos.occupancy(white);
        long ownPawns = pos.pieces(white ? Position.WHITE_PAWN : Position.BLACK_PAWN);
        long enemyPawns = pos.pieces(white ? Position.BLACK_PAWN : Position.WHITE_PAWN);
        long enemyKing = pos.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        int ownKing = pos.kingSquare(white);
        scanPieces(pos, ownPawns, white, Piece.PAWN, occupancy, own, ownPawns, enemyPawns, enemyKing,
                enemyPawnAttacks, side, ownKing);
        scanPieces(pos, pos.pieces(white ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT), white, Piece.KNIGHT,
                occupancy, own, ownPawns, enemyPawns, enemyKing, enemyPawnAttacks, side, ownKing);
        scanPieces(pos, pos.pieces(white ? Position.WHITE_BISHOP : Position.BLACK_BISHOP), white, Piece.BISHOP,
                occupancy, own, ownPawns, enemyPawns, enemyKing, enemyPawnAttacks, side, ownKing);
        scanPieces(pos, pos.pieces(white ? Position.WHITE_ROOK : Position.BLACK_ROOK), white, Piece.ROOK,
                occupancy, own, ownPawns, enemyPawns, enemyKing, enemyPawnAttacks, side, ownKing);
        scanPieces(pos, pos.pieces(white ? Position.WHITE_QUEEN : Position.BLACK_QUEEN), white, Piece.QUEEN,
                occupancy, own, ownPawns, enemyPawns, enemyKing, enemyPawnAttacks, side, ownKing);
        scanPieces(pos, pos.pieces(white ? Position.WHITE_KING : Position.BLACK_KING), white, Piece.KING,
                occupancy, own, ownPawns, enemyPawns, enemyKing, enemyPawnAttacks, side, ownKing);
    }

    /**
     * Scans every piece in one bitboard and updates attack/activity state.
     *
     * @param pos current position
     * @param pieces bitboard containing pieces of one type and side
     * @param white side whose pieces are being scanned
     * @param type absolute {@link Piece} type being scanned
     * @param occupancy occupied-square mask for both sides
     * @param own friendly occupancy mask
     * @param ownPawns friendly pawn bitboard
     * @param enemyPawns enemy pawn bitboard
     * @param enemyKing enemy king bitboard, excluded from mobility targets
     * @param enemyPawnAttacks enemy pawn attack mask
     * @param side attack-table side index
     * @param ownKing friendly king square, or negative if absent
     */

    private void scanPieces(Position pos, long pieces, boolean white, int type, long occupancy, long own,
            long ownPawns, long enemyPawns, long enemyKing, long enemyPawnAttacks, int side, int ownKing) {
        long mobilityArea = ~(own | enemyKing | enemyPawnAttacks);
        int enemy = 1 - side;
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            long attacks = attacksForPieceType(white, type, from, occupancy);
            addAttacks(side, type, attacks);
            if (type != Piece.PAWN && type != Piece.KING) {
                int mobility = Long.bitCount(attacks & mobilityArea);
                int activityType = activityType(type);
                mobilityMg[side] += mobilityScore(activityType, mobility);
                mobilityEg[side] += mobilityEgScore(activityType, mobility);
                addPieceActivity(pos, side, white, type, from, mobility, attacks, own, ownPawns,
                        enemyPawns, enemyPawnAttacks, ownKing);
            }
            long kingHits = attacks & kingZone[enemy];
            if (kingHits != 0L && type != Piece.KING) {
                kingAttackersCount[side]++;
                kingAttackersWeight[side] += KING_ATTACK_WEIGHT[type];
                kingAttacksCount[side] += Long.bitCount(kingHits);
            }
        }
    }

    /**
     * Adds piece-specific activity terms for a single non-pawn, non-king piece.
     *
     * <p>
     * Minor pieces get outpost and king-protector terms, bishops also get
     * color-complex penalties and long-diagonal bonuses, and heavy pieces are
     * delegated to their own helpers.
     * </p>
     *
     * @param pos current position
     * @param side attack-table side index
     * @param white side that owns the piece
     * @param type absolute {@link Piece} type
     * @param square piece square
     * @param mobility safe mobility count for this piece
     * @param attacks attack mask for this piece
     * @param own friendly occupancy mask
     * @param ownPawns friendly pawn bitboard
     * @param enemyPawns enemy pawn bitboard
     * @param enemyPawnAttacks enemy pawn attack mask
     * @param ownKing friendly king square, or negative if absent
     */

    private void addPieceActivity(Position pos, int side, boolean white, int type, int square, int mobility,
            long attacks, long own, long ownPawns, long enemyPawns, long enemyPawnAttacks,
            int ownKing) {
        if (type == Piece.KNIGHT || type == Piece.BISHOP) {
            long pawnAttacks = attackedBy[side][Piece.PAWN];
            if (isOutpost(white, square, pawnAttacks, enemyPawnAttacks)) {
                int bonus = type == Piece.KNIGHT ? KNIGHT_OUTPOST_CP : BISHOP_OUTPOST_CP;
                pieceMg[side] += bonus;
                pieceEg[side] += bonus / 2;
            } else if ((attacks & outpostMask(white, pawnAttacks, enemyPawnAttacks) & ~own) != 0L) {
                int bonus = type == Piece.KNIGHT ? 12 : 7;
                pieceMg[side] += bonus;
                pieceEg[side] += bonus / 2;
            }
            if (minorBehindPawn(white, square, ownPawns)) {
                pieceMg[side] += 8;
                pieceEg[side] += 5;
            }
            if (ownKing >= 0) {
                int protectorDistance = kingDistance(ownKing, square);
                int weight = type == Piece.KNIGHT ? 3 : 2;
                pieceMg[side] -= Math.max(0, protectorDistance - 1) * weight;
            }
        }
        if (type == Piece.BISHOP) {
            int penalty = badBishopPenalty(square, mobility, ownPawns);
            pieceMg[side] -= penalty;
            pieceEg[side] -= penalty / 2;
            if (Long.bitCount(MoveGenerator.bishopAttacks(square, ownPawns | enemyPawns) & CENTER_SQUARES) >= 2) {
                pieceMg[side] += 7;
                pieceEg[side] += 4;
            }
        } else if (type == Piece.ROOK) {
            addRookActivity(pos, side, white, square, mobility);
        } else if (type == Piece.QUEEN) {
            addQueenActivity(side, white, square, enemyPawnAttacks);
        }
    }

    /**
     * Adds rook file, seventh-rank, and trapped-rook activity terms.
     *
     * @param pos current position
     * @param side attack-table side index
     * @param white side that owns the rook
     * @param square rook square
     * @param mobility safe rook mobility count
     */
    private void addRookActivity(Position pos, int side, boolean white, int square, int mobility) {
        long file = fileBitboard(square & 7);
        if (((pos.pieces(Position.WHITE_QUEEN) | pos.pieces(Position.BLACK_QUEEN)) & file) != 0L) {
            pieceMg[side] += 6;
        }
        int relRank = relativeRank(white, square);
        long seventh = white ? Bits.RANK_7 : Bits.RANK_2;
        if (relRank == 6 && (pos.occupancy(!white) & seventh) != 0L) {
            pieceMg[side] += 18;
            pieceEg[side] += 28;
        }
        int king = pos.kingSquare(white);
        if (mobility <= 3 && king >= 0 && sameFlank(square, king)) {
            int penalty = canCastleEither(pos, white) ? 10 : 22;
            pieceMg[side] -= penalty;
            pieceEg[side] -= penalty / 2;
        }
    }

    /**
     * Adds a small bonus for queens placed past the center without pawn
     * harassment.
     *
     * @param side attack-table side index
     * @param white side that owns the queen
     * @param square queen square
     * @param enemyPawnAttacks enemy pawn attack mask
     */
    private void addQueenActivity(int side, boolean white, int square, long enemyPawnAttacks) {
        if (relativeRank(white, square) >= 4 && ((1L << square) & enemyPawnAttacks) == 0L) {
            pieceMg[side] += 7;
            pieceEg[side] += 10;
        }
    }

    /**
     * Returns all safe pawn-backed outpost squares for one side.
     *
     * @param white side whose outposts are being built
     * @param ownPawnAttacks friendly pawn attack mask
     * @param enemyPawnAttacks enemy pawn attack mask
     * @return bitboard of reachable outpost squares
     */
    private long outpostMask(boolean white, long ownPawnAttacks, long enemyPawnAttacks) {
        long ranks = white
                ? Bits.RANK_4 | Bits.RANK_5 | Bits.RANK_6
                : Bits.RANK_5 | Bits.RANK_4 | Bits.RANK_3;
        return ranks & ownPawnAttacks & ~enemyPawnAttacks;
    }

    /**
     * Returns whether a minor piece sits directly behind a friendly pawn.
     *
     * @param white side that owns the minor piece
     * @param square minor-piece square
     * @param ownPawns friendly pawn bitboard
     * @return true when a friendly pawn is directly in front of the minor piece
     */
    private boolean minorBehindPawn(boolean white, int square, long ownPawns) {
        int pawnSquare = white ? square - 8 : square + 8;
        return pawnSquare >= 0 && pawnSquare < 64 && ((1L << pawnSquare) & ownPawns) != 0L;
    }

    /**
     * Returns a square rank relative to a side's promotion direction.
     *
     * @param white side whose perspective is used
     * @param square square to inspect
     * @return rank in {@code 0..7}, increasing toward promotion
     */
    private int relativeRank(boolean white, int square) {
        int rank = Bits.rank(square);
        return white ? rank : 7 - rank;
    }

    /**
     * Returns whether two pieces are on the same broad board flank.
     *
     * @param a first square
     * @param b second square
     * @return true when both squares are on the same half of the board
     */
    private boolean sameFlank(int a, int b) {
        return ((a & 7) <= 3) == ((b & 7) <= 3);
    }

    /**
     * Returns whether a side still has any castling option.
     *
     * @param pos current position
     * @param white side to inspect
     * @return true when at least one castling right remains
     */
    private boolean canCastleEither(Position pos, boolean white) {
        return white
                ? pos.canCastle(Position.WHITE_KINGSIDE) || pos.canCastle(Position.WHITE_QUEENSIDE)
                : pos.canCastle(Position.BLACK_KINGSIDE) || pos.canCastle(Position.BLACK_QUEENSIDE);
    }

    /**
     * Maps piece constants to mobility-table indexes.
     *
     * @param type absolute {@link Piece} type
     * @return mobility-table index for knight, bishop, rook, or queen
     */
    private int activityType(int type) {
        return switch (type) {
            case Piece.KNIGHT -> 0;
            case Piece.BISHOP -> 1;
            case Piece.ROOK -> 2;
            case Piece.QUEEN -> 3;
            default -> 0;
        };
    }

    /**
     * Adds one attack mask to the attack tables.
     *
     * @param side attack-table side index
     * @param type absolute {@link Piece} type
     * @param attacks attack mask to merge
     */
    private void addAttacks(int side, int type, long attacks) {
        attackedBy2[side] |= attackedBy[side][ALL_ATTACKS] & attacks;
        attackedBy[side][ALL_ATTACKS] |= attacks;
        attackedBy[side][type] |= attacks;
    }

    /**
     * Returns attacks for a piece type from one square.
     *
     * @param white side that owns the piece
     * @param type absolute {@link Piece} type
     * @param from source square
     * @param occupancy occupied-square mask for sliding attacks
     * @return pseudo-legal attack mask for the piece
     */
    private long attacksForPieceType(boolean white, int type, int from, long occupancy) {
        return switch (type) {
            case Piece.PAWN -> pawnAttackMask(white, 1L << from);
            case Piece.KNIGHT -> MoveGenerator.knightAttacks(from);
            case Piece.BISHOP -> MoveGenerator.bishopAttacks(from, occupancy);
            case Piece.ROOK -> MoveGenerator.rookAttacks(from, occupancy);
            case Piece.QUEEN -> MoveGenerator.bishopAttacks(from, occupancy)
                    | MoveGenerator.rookAttacks(from, occupancy);
            case Piece.KING -> MoveGenerator.kingAttacks(from);
            default -> 0L;
        };
    }

    /**
     * Builds a king-zone mask for one king square.
     *
     * @param king king square, or negative if absent
     * @return bitboard containing the king square and adjacent squares
     */
    private long kingZoneMask(int king) {
        if (king < 0) {
            return 0L;
        }
        return (1L << king) | MoveGenerator.kingAttacks(king);
    }
}
