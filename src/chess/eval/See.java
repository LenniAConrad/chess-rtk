package chess.eval;

import chess.core.Move;
import chess.core.Position;

/**
 * Static Exchange Evaluation (SEE).
 *
 * <p>Returns the net material (centipawns, mover's perspective) of a capture move
 * resolved by recursive least-valuable-attacker recaptures on the destination
 * square with the standing-pat (negamax) rule. Positive = the capture sequence
 * wins material, negative = loses it, 0 = even.
 *
 * <p>Self-contained: clones the engine's signed {@code byte[64]} board and
 * recomputes attackers each ply against the reduced occupancy, so removing each
 * capturer naturally exposes a slider behind it (x-ray / battery). Pins are
 * ignored (conventional SEE behavior).
 *
 * <p>API used, confirmed by reflection/probe: getBoard()->byte[64] signed
 * (white&gt;0); actualToSquare(short)->byte; isEnPassantCapture(short)->boolean;
 * capturedSquare(short)->byte (the EP pawn's square for en passant);
 * Move.getFromIndex/getToIndex/getPromotion(short)->byte. NOTE this build has no
 * Position.generateLegalMoves() (use legalMoves()) and no
 * Piece.getValue(int)/getValue(byte,boolean) overloads.
 *
 * <p>Geometry note (probe-confirmed): this engine's internal board is vertically
 * inverted relative to a standard a1=0 layout. FEN 4k3/8/8/8/4P3/8/8/4K3 yields
 * occupied indices {4=-6 (black king e8), 36=+1 (white pawn e4), 60=+6 (white
 * king e1)}, i.e. index = (7-rank)*8 + file, so WHITE advances toward LOW
 * indices. Sliding/knight/king geometry is index-symmetric and unaffected; the
 * pawn-attacker offsets and promotion rank below are written for this
 * orientation (white pawn attacks from target+7/target+9, promotes on rank 0).
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class See {

    /**
     * Utility class; prevent instantiation.
     */
    private See() {
    }

    /**
     * Pawn material value in centipawns for exchange accounting.
     */
    private static final int PAWN_VALUE = 100;
    /**
     * Knight material value in centipawns for exchange accounting.
     */
    private static final int KNIGHT_VALUE = 300;
    /**
     * Bishop material value in centipawns for exchange accounting.
     */
    private static final int BISHOP_VALUE = 300;
    /**
     * Rook material value in centipawns for exchange accounting.
     */
    private static final int ROOK_VALUE = 500;
    /**
     * Queen material value in centipawns for exchange accounting.
     */
    private static final int QUEEN_VALUE = 900;
    /**
     * Sentinel king value used so king captures dominate material ordering.
     */
    private static final int KING_VALUE = 20000;

    /**
     * Material values indexed by the normalized piece-type ids below.
     */
    private static final int[] VALUE_BY_TYPE = {
            0, PAWN_VALUE, KNIGHT_VALUE, BISHOP_VALUE, ROOK_VALUE, QUEEN_VALUE, KING_VALUE,
    };

    /**
     * Normalized pawn type id used by the local board copy.
     */
    private static final int PAWN = 1;
    /**
     * Normalized knight type id used by the local board copy.
     */
    private static final int KNIGHT = 2;
    /**
     * Normalized bishop type id used by the local board copy.
     */
    private static final int BISHOP = 3;
    /**
     * Normalized rook type id used by the local board copy.
     */
    private static final int ROOK = 4;
    /**
     * Normalized queen type id used by the local board copy.
     */
    private static final int QUEEN = 5;
    /**
     * Normalized king type id used by the local board copy.
     */
    private static final int KING = 6;

    /**
     * Full SEE value of {@code move} in centipawns, from the mover's perspective.
     *
     * @param position chess position
     * @param move encoded chess move
     * @return full SEE value of move in centipawns, from the mover's perspective
     */
    public static int see(Position position, short move) {
        return evaluate(position, move);
    }

    /**
     * Whether {@code see(position, move) >= threshold}.
     *
     * @param position chess position
     * @param move encoded chess move
     * @param threshold SEE acceptance threshold
     * @return whether see(position, move) >= threshold
     */
    public static boolean seeGreaterEqual(Position position, short move, int threshold) {
        return evaluate(position, move) >= threshold;
    }

    /**
     * Evaluates the material result of the capture sequence from the mover's
     * perspective.
     *
     * @param position chess position
     * @param move encoded move
     * @return SEE score in centipawns
     */
    private static int evaluate(Position position, short move) {
        final int to = position.actualToSquare(move) & 0xFF;
        final int from = Move.getFromIndex(move) & 0xFF;
        if (to > 63 || from > 63) {
            return 0;
        }

        final byte[] board = position.getBoard().clone();

        final boolean enPassant = position.isEnPassantCapture(move);

        int capturedType;
        if (enPassant) {
            int epPawnSquare = position.capturedSquare(move) & 0xFF;
            if (epPawnSquare <= 63 && board[epPawnSquare] != 0) {
                capturedType = Math.abs(board[epPawnSquare]);
                board[epPawnSquare] = 0;
            } else {
                capturedType = PAWN;
            }
        } else {
            int victim = board[to];
            if (victim == 0) {
                return 0; // not a capture
            }
            capturedType = Math.abs(victim);
        }

        int movingType = Math.abs(board[from]);
        if (movingType == 0) {
            return 0;
        }
        boolean moverWhite = board[from] > 0;

        int promotionType = Move.getPromotion(move); // 0=none,1=N,2=B,3=R,4=Q
        boolean promotion = promotionType >= 1 && promotionType <= 4;
        int promoPieceType = promotion ? promoToPieceType(promotionType) : 0;

        int[] gain = new int[64];
        int d = 0;
        gain[0] = VALUE_BY_TYPE[capturedType];
        if (promotion) {
            gain[0] += VALUE_BY_TYPE[promoPieceType] - PAWN_VALUE;
        }

        int occupantType = promotion ? promoPieceType : movingType;

        board[from] = 0;
        board[to] = (byte) (moverWhite ? occupantType : -occupantType);

        boolean sideToMoveWhite = !moverWhite;

        while (true) {
            int attackerSquare = leastValuableAttacker(board, to, sideToMoveWhite);
            if (attackerSquare < 0) {
                break;
            }

            d++;
            gain[d] = VALUE_BY_TYPE[occupantType] - gain[d - 1];

            int attackerType = Math.abs(board[attackerSquare]);
            int newOccupantType = attackerType;
            if (attackerType == PAWN && isPromotionRank(to, sideToMoveWhite)) {
                gain[d] += QUEEN_VALUE - PAWN_VALUE;
                newOccupantType = QUEEN;
            }

            board[attackerSquare] = 0;
            board[to] = (byte) (sideToMoveWhite ? newOccupantType : -newOccupantType);
            occupantType = newOccupantType;

            sideToMoveWhite = !sideToMoveWhite;
        }

        for (int i = d; i > 0; i--) {
            gain[i - 1] = -Math.max(-gain[i - 1], gain[i]);
        }
        return gain[0];
    }

    /**
     * Returns the promo to piece type.
     *
     * @param promotionCode promotion piece code
     * @return promo to piece type
     */
    private static int promoToPieceType(int promotionCode) {
        switch (promotionCode) {
            case 1: return KNIGHT;
            case 2: return BISHOP;
            case 3: return ROOK;
            case 4: return QUEEN;
            default: return QUEEN;
        }
    }

    // White pawns advance toward LOW indices on this engine's inverted board, so a
    // white pawn promotes on rank index 0; black on rank index 7.
    /**
     * Returns whether promotion rank.
     *
     * @param square board square index
     * @param white whether white
     * @return true when promotion rank
     */
    private static boolean isPromotionRank(int square, boolean white) {
        int rank = square >>> 3;
        return white ? rank == 0 : rank == 7;
    }

    /**
     * Returns the least valuable attacker.
     *
     * @param board board state
     * @param target target object
     * @param white whether white
     * @return least valuable attacker
     */
    private static int leastValuableAttacker(byte[] board, int target, boolean white) {
        int pawnSq = findPawnAttacker(board, target, white);
        if (pawnSq >= 0) {
            return pawnSq;
        }
        int knightSq = findKnightAttacker(board, target, white);
        if (knightSq >= 0) {
            return knightSq;
        }

        int best = -1;
        int bestValue = Integer.MAX_VALUE;

        int diag = findDiagonalAttacker(board, target, white);
        if (diag >= 0) {
            int v = VALUE_BY_TYPE[Math.abs(board[diag])];
            if (v < bestValue) {
                bestValue = v;
                best = diag;
            }
        }
        int orth = findOrthogonalAttacker(board, target, white);
        if (orth >= 0) {
            int v = VALUE_BY_TYPE[Math.abs(board[orth])];
            if (v < bestValue) {
                bestValue = v;
                best = orth;
            }
        }
        if (best >= 0) {
            return best;
        }
        return findKingAttacker(board, target, white);
    }

    // A WHITE pawn (advancing toward low indices) sits one HIGH rank from its
    // target, attacking diagonally; so it is at target+7 or target+9.
    /**
     * Returns the pawn attacker.
     *
     * @param board board state
     * @param target target object
     * @param white whether white
     * @return pawn attacker
     */
    private static int findPawnAttacker(byte[] board, int target, boolean white) {
        int tf = target & 7;
        int tr = target >>> 3;
        if (white) {
            if (tr <= 6) {
                if (tf >= 1 && board[target + 7] == PAWN) {
                    return target + 7;
                }
                if (tf <= 6 && board[target + 9] == PAWN) {
                    return target + 9;
                }
            }
        } else {
            if (tr >= 1) {
                if (tf >= 1 && board[target - 9] == -PAWN) {
                    return target - 9;
                }
                if (tf <= 6 && board[target - 7] == -PAWN) {
                    return target - 7;
                }
            }
        }
        return -1;
    }

    /**
     * Knight attack offsets in the engine's 0..63 board indexing.
     */
    private static final int[][] KNIGHT_DELTAS = {
            {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2},
    };

    /**
     * Returns the knight attacker.
     *
     * @param board board state
     * @param target target object
     * @param white whether white
     * @return knight attacker
     */
    private static int findKnightAttacker(byte[] board, int target, boolean white) {
        int tf = target & 7;
        int tr = target >>> 3;
        byte want = (byte) (white ? KNIGHT : -KNIGHT);
        for (int[] dlt : KNIGHT_DELTAS) {
            int f = tf + dlt[0];
            int r = tr + dlt[1];
            if (f < 0 || f > 7 || r < 0 || r > 7) {
                continue;
            }
            if (board[r * 8 + f] == want) {
                return r * 8 + f;
            }
        }
        return -1;
    }

    /**
     * King attack offsets in the engine's 0..63 board indexing.
     */
    private static final int[][] KING_DELTAS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
    };

    /**
     * Returns the king attacker.
     *
     * @param board board state
     * @param target target object
     * @param white whether white
     * @return king attacker
     */
    private static int findKingAttacker(byte[] board, int target, boolean white) {
        int tf = target & 7;
        int tr = target >>> 3;
        byte want = (byte) (white ? KING : -KING);
        for (int[] dlt : KING_DELTAS) {
            int f = tf + dlt[0];
            int r = tr + dlt[1];
            if (f < 0 || f > 7 || r < 0 || r > 7) {
                continue;
            }
            if (board[r * 8 + f] == want) {
                return r * 8 + f;
            }
        }
        return -1;
    }

    /**
     * Diagonal slider directions for bishop and queen x-ray scans.
     */
    private static final int[][] DIAG_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /**
     * Returns the diagonal attacker.
     *
     * @param board board state
     * @param target target object
     * @param white whether white
     * @return diagonal attacker
     */
    private static int findDiagonalAttacker(byte[] board, int target, boolean white) {
        int tf = target & 7;
        int tr = target >>> 3;
        int bishop = white ? BISHOP : -BISHOP;
        int queen = white ? QUEEN : -QUEEN;
        int found = -1;
        int foundVal = Integer.MAX_VALUE;
        for (int[] dlt : DIAG_DIRS) {
            int f = tf + dlt[0];
            int r = tr + dlt[1];
            while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
                int p = board[r * 8 + f];
                if (p != 0) {
                    if (p == bishop || p == queen) {
                        int v = VALUE_BY_TYPE[Math.abs(p)];
                        if (v < foundVal) {
                            foundVal = v;
                            found = r * 8 + f;
                        }
                    }
                    break;
                }
                f += dlt[0];
                r += dlt[1];
            }
        }
        return found;
    }

    /**
     * Orthogonal slider directions for rook and queen x-ray scans.
     */
    private static final int[][] ORTHO_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Returns the orthogonal attacker.
     *
     * @param board board state
     * @param target target object
     * @param white whether white
     * @return orthogonal attacker
     */
    private static int findOrthogonalAttacker(byte[] board, int target, boolean white) {
        int tf = target & 7;
        int tr = target >>> 3;
        int rook = white ? ROOK : -ROOK;
        int queen = white ? QUEEN : -QUEEN;
        int found = -1;
        int foundVal = Integer.MAX_VALUE;
        for (int[] dlt : ORTHO_DIRS) {
            int f = tf + dlt[0];
            int r = tr + dlt[1];
            while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
                int p = board[r * 8 + f];
                if (p != 0) {
                    if (p == rook || p == queen) {
                        int v = VALUE_BY_TYPE[Math.abs(p)];
                        if (v < foundVal) {
                            foundVal = v;
                            found = r * 8 + f;
                        }
                    }
                    break;
                }
                f += dlt[0];
                r += dlt[1];
            }
        }
        return found;
    }
}
