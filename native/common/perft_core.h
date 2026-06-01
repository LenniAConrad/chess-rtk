/*
 * native/common/perft_core.h
 *
 * Portable bitboard move generator + perft, a faithful C++ mirror of the Java
 * core (src/chess/core/MoveGenerator.java, Position.java, PositionMoveSupport.java,
 * SlidingAttacks.java). It is shared by the CPU host test harness and the
 * CUDA / ROCm / oneAPI perft kernels, so every function is annotated PERFT_HD
 * (= __host__ __device__ under a GPU compiler, empty under g++).
 *
 * Correctness contract (must match Java exactly or perft counts diverge):
 *   - INVERTED board orientation: a8 = bit 0, h1 = bit 63. White pawns move
 *     toward LOWER indices (single push = pawns >> 8). RANK_8 = bits 0..7.
 *   - Piece indices WP0..WK5, BP6..BK11; white = index < 6.
 *   - En passant square is recorded only when an enemy pawn can capture it.
 *   - Castling legality is fully decided during pseudo-move generation;
 *     Chess960 encodes the king move's target as the rook's source square.
 *   - Castling-rights keep-mask is applied to both move endpoints (captured
 *     rook on its home square loses the right).
 *   - Slider attacks use hyperbola quintessence with full 64-bit bit reversal.
 *
 * A correct perft needs only: pseudo-move generation + make + "is the mover's
 * king attacked?" + undo. The Java fast-legality shortcuts are performance-only
 * and intentionally omitted here.
 */
#ifndef CRTK_PERFT_CORE_H
#define CRTK_PERFT_CORE_H

#include <cstdint>

#if defined(__CUDACC__) || defined(__HIPCC__)
#define PERFT_HD __host__ __device__
#else
#define PERFT_HD
#endif

namespace crtk_perft {

// ---------------------------------------------------------------------------
// Constants (mirror chess.core.Position / Field / Move)
// ---------------------------------------------------------------------------
enum : int {
    WP = 0, WN = 1, WB = 2, WR = 3, WQ = 4, WK = 5,
    BP = 6, BN = 7, BB = 8, BR = 9, BQ = 10, BK = 11
};

enum : int {
    CASTLE_WK = 1, CASTLE_WQ = 2, CASTLE_BK = 4, CASTLE_BQ = 8
};

enum : int {
    PROMO_NONE = 0, PROMO_KNIGHT = 1, PROMO_BISHOP = 2, PROMO_ROOK = 3, PROMO_QUEEN = 4
};

constexpr int NO_SQUARE = -1;
constexpr int MAX_MOVES = 256;

// King / rook destination squares per castling right (inverted layout).
constexpr int SQ_G1 = 62, SQ_C1 = 58, SQ_G8 = 6, SQ_C8 = 2; // king targets
constexpr int SQ_F1 = 61, SQ_D1 = 59, SQ_F8 = 5, SQ_D8 = 3; // rook targets

constexpr uint64_t FILE_A = 0x0101010101010101ULL;
constexpr uint64_t FILE_H = 0x8080808080808080ULL;
constexpr uint64_t RANK_8 = 0x00000000000000FFULL; // bits 0..7   (8th rank)
constexpr uint64_t RANK_1 = 0xFF00000000000000ULL; // bits 56..63 (1st rank)
constexpr uint64_t RANK_3 = 0x0000FF0000000000ULL; // bits 40..47
constexpr uint64_t RANK_6 = 0x0000000000FF0000ULL; // bits 16..23

// ---------------------------------------------------------------------------
// Bit helpers
// ---------------------------------------------------------------------------
PERFT_HD inline int ctz64(uint64_t b) {
#if defined(__CUDA_ARCH__) || defined(__HIP_DEVICE_COMPILE__)
    // Device pass only: __ffsll is a device intrinsic. The host pass of a
    // __host__ __device__ function must use the host builtin instead.
    return __ffsll((unsigned long long) b) - 1;
#else
    return __builtin_ctzll(b);
#endif
}

PERFT_HD inline uint64_t reverse_bits64(uint64_t v) {
    v = ((v >> 1) & 0x5555555555555555ULL) | ((v & 0x5555555555555555ULL) << 1);
    v = ((v >> 2) & 0x3333333333333333ULL) | ((v & 0x3333333333333333ULL) << 2);
    v = ((v >> 4) & 0x0F0F0F0F0F0F0F0FULL) | ((v & 0x0F0F0F0F0F0F0F0FULL) << 4);
    v = ((v >> 8) & 0x00FF00FF00FF00FFULL) | ((v & 0x00FF00FF00FF00FFULL) << 8);
    v = ((v >> 16) & 0x0000FFFF0000FFFFULL) | ((v & 0x0000FFFF0000FFFFULL) << 16);
    v = (v >> 32) | (v << 32);
    return v;
}

// Mirror Java "x << 1": shifting the top bit out yields 0 (well-defined for
// unsigned). The hyperbola subtraction relies on this exact behavior.
PERFT_HD inline uint64_t shl1(uint64_t x) {
    return x << 1;
}

PERFT_HD inline int file_of(int sq) { return sq & 7; }
// Geometric rank index (Bits.rank): 0 = rank 1, 7 = rank 8.
PERFT_HD inline int rank_of(int sq) { return 7 - (sq >> 3); }
// Coordinate -> bit index (Field.toIndex), rank = geometric rank index.
PERFT_HD inline int to_index(int file, int rank) { return (7 - rank) * 8 + file; }

PERFT_HD inline bool on_board(int file, int rank) {
    return file >= 0 && file < 8 && rank >= 0 && rank < 8;
}

PERFT_HD inline bool on_3rd_rank(int sq) { return sq >= 40 && sq <= 47; }
PERFT_HD inline bool on_6th_rank(int sq) { return sq >= 16 && sq <= 23; }

// ---------------------------------------------------------------------------
// Precomputed attack tables (built once on host, uploaded to device verbatim)
// ---------------------------------------------------------------------------
struct Tables {
    uint64_t knight[64];
    uint64_t king[64];
    uint64_t wPawn[64]; // squares a white pawn on s attacks
    uint64_t bPawn[64]; // squares a black pawn on s attacks
    uint64_t rankMask[64];
    uint64_t fileMask[64];
    uint64_t diagMask[64];
    uint64_t antiMask[64];
};

// Line mask in RAW layout coordinates (row = sq >> 3), mirroring SlidingAttacks.lineMask.
inline uint64_t line_mask_raw(int sq, int df, int dr) {
    uint64_t out = 0;
    int file = sq & 7;
    int row = sq >> 3;
    file += df;
    row += dr;
    while (file >= 0 && file < 8 && row >= 0 && row < 8) {
        out |= 1ULL << (row * 8 + file);
        file += df;
        row += dr;
    }
    return out;
}

inline void build_tables(Tables& t) {
    const int knightDeltas[8][2] = {
        {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };
    const int kingDeltas[8][2] = {
        {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };
    for (int sq = 0; sq < 64; ++sq) {
        int file = file_of(sq);
        int rank = rank_of(sq);
        uint64_t kn = 0, kg = 0, wp = 0, bp = 0;
        for (int i = 0; i < 8; ++i) {
            int tf = file + knightDeltas[i][0];
            int tr = rank + knightDeltas[i][1];
            if (on_board(tf, tr)) {
                kn |= 1ULL << to_index(tf, tr);
            }
            tf = file + kingDeltas[i][0];
            tr = rank + kingDeltas[i][1];
            if (on_board(tf, tr)) {
                kg |= 1ULL << to_index(tf, tr);
            }
        }
        // pawn attacks: white attacks one geometric rank up, black one rank down.
        int wr = rank + 1;
        if (on_board(file - 1, wr)) {
            wp |= 1ULL << to_index(file - 1, wr);
        }
        if (on_board(file + 1, wr)) {
            wp |= 1ULL << to_index(file + 1, wr);
        }
        int br = rank - 1;
        if (on_board(file - 1, br)) {
            bp |= 1ULL << to_index(file - 1, br);
        }
        if (on_board(file + 1, br)) {
            bp |= 1ULL << to_index(file + 1, br);
        }
        t.knight[sq] = kn;
        t.king[sq] = kg;
        t.wPawn[sq] = wp;
        t.bPawn[sq] = bp;

        uint64_t self = 1ULL << sq;
        t.rankMask[sq] = line_mask_raw(sq, 1, 0) | line_mask_raw(sq, -1, 0) | self;
        t.fileMask[sq] = line_mask_raw(sq, 0, 1) | line_mask_raw(sq, 0, -1) | self;
        t.diagMask[sq] = line_mask_raw(sq, 1, 1) | line_mask_raw(sq, -1, -1) | self;
        t.antiMask[sq] = line_mask_raw(sq, 1, -1) | line_mask_raw(sq, -1, 1) | self;
    }
}

// ---------------------------------------------------------------------------
// Slider attacks (hyperbola quintessence)
// ---------------------------------------------------------------------------
PERFT_HD inline uint64_t line_attacks(int sq, uint64_t occ, uint64_t mask) {
    uint64_t bit = 1ULL << sq;
    uint64_t m = occ & mask;
    uint64_t fwd = m - shl1(bit);
    uint64_t rBit = reverse_bits64(bit);
    uint64_t rOcc = reverse_bits64(m);
    uint64_t rev = rOcc - shl1(rBit);
    return (fwd ^ reverse_bits64(rev)) & mask;
}

PERFT_HD inline uint64_t bishop_attacks(const Tables& t, int sq, uint64_t occ) {
    return line_attacks(sq, occ, t.diagMask[sq]) | line_attacks(sq, occ, t.antiMask[sq]);
}

PERFT_HD inline uint64_t rook_attacks(const Tables& t, int sq, uint64_t occ) {
    return line_attacks(sq, occ, t.rankMask[sq]) | line_attacks(sq, occ, t.fileMask[sq]);
}

// ---------------------------------------------------------------------------
// Position + undo state
// ---------------------------------------------------------------------------
struct Position {
    uint64_t pieces[12];
    uint64_t whiteOcc;
    uint64_t blackOcc;
    uint64_t occ;
    signed char board[64]; // square -> piece index, -1 = empty
    int wk;                // white king square, -1 if none
    int bk;                // black king square, -1 if none
    bool whiteToMove;
    int castling;
    int ep; // en passant target square, -1 if none
    bool chess960;
    int wkRook; // white kingside rook source square (-1 if right absent)
    int wqRook;
    int bkRook;
    int bqRook;
};

struct Undo {
    int moving;
    int captured;
    int capturedSquare;
    int kingTo;
    int castling;
    int ep;
    bool whiteToMove;
    int rook;
    int rookFrom;
    int rookTo;
    bool castle;
    bool enPassant; // true iff this move was an en-passant capture
};

// Detailed leaf-ply counters, mirroring chess.debug.Perft.Stats.
struct PerftCounts {
    unsigned long long nodes;
    unsigned long long captures;
    unsigned long long enPassant;
    unsigned long long castles;
    unsigned long long promotions;
    unsigned long long checks;
    unsigned long long checkmates;
};

struct MoveList {
    uint16_t moves[MAX_MOVES];
    int count;
};

PERFT_HD inline uint16_t encode_move(int from, int to, int promo) {
    return (uint16_t) (from | (to << 6) | (promo << 12));
}

PERFT_HD inline void push_move(MoveList& ml, int from, int to, int promo) {
    ml.moves[ml.count++] = encode_move(from, to, promo);
}

// ---------------------------------------------------------------------------
// Piece placement primitives (mirror PositionStateSupport)
// ---------------------------------------------------------------------------
PERFT_HD inline void set_piece(Position& p, int piece, int sq) {
    uint64_t mask = 1ULL << sq;
    p.pieces[piece] |= mask;
    p.board[sq] = (signed char) piece;
    if (piece < BP) {
        p.whiteOcc |= mask;
    } else {
        p.blackOcc |= mask;
    }
    p.occ |= mask;
    if (piece == WK) {
        p.wk = sq;
    } else if (piece == BK) {
        p.bk = sq;
    }
}

PERFT_HD inline void clear_piece(Position& p, int piece, int sq) {
    uint64_t mask = ~(1ULL << sq);
    p.pieces[piece] &= mask;
    p.board[sq] = -1;
    if (piece < BP) {
        p.whiteOcc &= mask;
    } else {
        p.blackOcc &= mask;
    }
    p.occ &= mask;
    if (piece == WK) {
        p.wk = NO_SQUARE;
    } else if (piece == BK) {
        p.bk = NO_SQUARE;
    }
}

PERFT_HD inline void move_piece(Position& p, int piece, int from, int to) {
    clear_piece(p, piece, from);
    set_piece(p, piece, to);
}

// ---------------------------------------------------------------------------
// Castling helpers (mirror Position / PositionMoveSupport)
// ---------------------------------------------------------------------------
PERFT_HD inline int castling_king_target(int right) {
    switch (right) {
        case CASTLE_WK: return SQ_G1;
        case CASTLE_WQ: return SQ_C1;
        case CASTLE_BK: return SQ_G8;
        case CASTLE_BQ: return SQ_C8;
        default: return NO_SQUARE;
    }
}

PERFT_HD inline int castling_rook_target(int right) {
    switch (right) {
        case CASTLE_WK: return SQ_F1;
        case CASTLE_WQ: return SQ_D1;
        case CASTLE_BK: return SQ_F8;
        case CASTLE_BQ: return SQ_D8;
        default: return NO_SQUARE;
    }
}

PERFT_HD inline int castling_rook_square(const Position& p, int right) {
    switch (right) {
        case CASTLE_WK: return p.wkRook;
        case CASTLE_WQ: return p.wqRook;
        case CASTLE_BK: return p.bkRook;
        case CASTLE_BQ: return p.bqRook;
        default: return NO_SQUARE;
    }
}

PERFT_HD inline int castling_move_target(const Position& p, int right) {
    return p.chess960 ? castling_rook_square(p, right) : castling_king_target(right);
}

PERFT_HD inline int castling_right_for_move(const Position& p, int moving, int to) {
    if (moving == WK) {
        if ((p.castling & CASTLE_WK) && to == castling_move_target(p, CASTLE_WK)) return CASTLE_WK;
        if ((p.castling & CASTLE_WQ) && to == castling_move_target(p, CASTLE_WQ)) return CASTLE_WQ;
    } else if (moving == BK) {
        if ((p.castling & CASTLE_BK) && to == castling_move_target(p, CASTLE_BK)) return CASTLE_BK;
        if ((p.castling & CASTLE_BQ) && to == castling_move_target(p, CASTLE_BQ)) return CASTLE_BQ;
    }
    return 0;
}

// keepMask for one square (mirror PositionMoveSupport.castlingKeepMask single-square effect).
PERFT_HD inline int castling_keep_for_square(const Position& p, int sq) {
    int keep = CASTLE_WK | CASTLE_WQ | CASTLE_BK | CASTLE_BQ;
    if (sq == p.wk) keep &= ~(CASTLE_WK | CASTLE_WQ);
    if (sq == p.bk) keep &= ~(CASTLE_BK | CASTLE_BQ);
    if (sq == p.wkRook) keep &= ~CASTLE_WK;
    if (sq == p.wqRook) keep &= ~CASTLE_WQ;
    if (sq == p.bkRook) keep &= ~CASTLE_BK;
    if (sq == p.bqRook) keep &= ~CASTLE_BQ;
    return keep;
}

PERFT_HD inline int promotion_piece_index(int moving, int promo) {
    if (promo == PROMO_NONE || (moving != WP && moving != BP)) return moving;
    bool white = (moving == WP);
    switch (promo) {
        case PROMO_KNIGHT: return white ? WN : BN;
        case PROMO_BISHOP: return white ? WB : BB;
        case PROMO_ROOK:   return white ? WR : BR;
        case PROMO_QUEEN:  return white ? WQ : BQ;
        default:           return moving;
    }
}

// ---------------------------------------------------------------------------
// Attack detection (mirror MoveGenerator.isSquareAttacked)
// ---------------------------------------------------------------------------
PERFT_HD inline bool square_attacked(const Tables& t, const Position& p, int sq,
        bool byWhite, uint64_t occ, uint64_t excluded) {
    uint64_t pawns = p.pieces[byWhite ? WP : BP];
    uint64_t pawnFrom = byWhite ? t.bPawn[sq] : t.wPawn[sq];
    if (pawnFrom & pawns) return true;
    if (t.knight[sq] & p.pieces[byWhite ? WN : BN]) return true;
    if (t.king[sq] & p.pieces[byWhite ? WK : BK]) return true;

    uint64_t included = ~excluded;
    uint64_t queens = p.pieces[byWhite ? WQ : BQ] & included;
    uint64_t bishopQueens = (p.pieces[byWhite ? WB : BB] & included) | queens;
    if (bishopQueens && (bishop_attacks(t, sq, occ) & bishopQueens)) return true;
    uint64_t rookQueens = (p.pieces[byWhite ? WR : BR] & included) | queens;
    if (rookQueens && (rook_attacks(t, sq, occ) & rookQueens)) return true;
    return false;
}

PERFT_HD inline bool king_attacked(const Tables& t, const Position& p, bool white) {
    int king = white ? p.wk : p.bk;
    if (king == NO_SQUARE) return false;
    return square_attacked(t, p, king, !white, p.occ, 0ULL);
}

// ---------------------------------------------------------------------------
// Pseudo-move generation (mirror MoveGenerator.generatePseudoLegalMoves)
// ---------------------------------------------------------------------------
PERFT_HD inline uint64_t en_passant_mask(const Position& p, bool white) {
    int ep = p.ep;
    if (ep == NO_SQUARE || p.board[ep] >= 0) return 0ULL;
    bool validRank = white ? on_6th_rank(ep) : on_3rd_rank(ep);
    return validRank ? (1ULL << ep) : 0ULL;
}

PERFT_HD inline void add_pawn_advance(MoveList& ml, int from, int to, bool white) {
    if ((white && to < 8) || (!white && to >= 56)) {
        push_move(ml, from, to, PROMO_QUEEN);
        push_move(ml, from, to, PROMO_ROOK);
        push_move(ml, from, to, PROMO_BISHOP);
        push_move(ml, from, to, PROMO_KNIGHT);
    } else {
        push_move(ml, from, to, PROMO_NONE);
    }
}

PERFT_HD inline void add_pawn_targets(MoveList& ml, uint64_t targets, int fromOffset, bool white) {
    while (targets) {
        int to = ctz64(targets);
        targets &= targets - 1;
        add_pawn_advance(ml, to + fromOffset, to, white);
    }
}

PERFT_HD inline void add_targets(MoveList& ml, int from, uint64_t targets) {
    while (targets) {
        int to = ctz64(targets);
        targets &= targets - 1;
        push_move(ml, from, to, PROMO_NONE);
    }
}

PERFT_HD inline void add_white_pawn_moves(const Position& p, MoveList& ml) {
    uint64_t pawns = p.pieces[WP];
    uint64_t empty = ~p.occ;
    uint64_t enemies = p.blackOcc & ~p.pieces[BK];
    uint64_t capturable = enemies | en_passant_mask(p, true);
    uint64_t single = (pawns >> 8) & empty;
    add_pawn_targets(ml, single, 8, true);
    add_pawn_targets(ml, ((single & RANK_3) >> 8) & empty, 16, true);
    add_pawn_targets(ml, ((pawns & ~FILE_A) >> 9) & capturable, 9, true);
    add_pawn_targets(ml, ((pawns & ~FILE_H) >> 7) & capturable, 7, true);
}

PERFT_HD inline void add_black_pawn_moves(const Position& p, MoveList& ml) {
    uint64_t pawns = p.pieces[BP];
    uint64_t empty = ~p.occ;
    uint64_t enemies = p.whiteOcc & ~p.pieces[WK];
    uint64_t capturable = enemies | en_passant_mask(p, false);
    uint64_t single = (pawns << 8) & empty;
    add_pawn_targets(ml, single, -8, false);
    add_pawn_targets(ml, ((single & RANK_6) << 8) & empty, -16, false);
    add_pawn_targets(ml, ((pawns & ~FILE_A) << 7) & capturable, -7, false);
    add_pawn_targets(ml, ((pawns & ~FILE_H) << 9) & capturable, -9, false);
}

PERFT_HD inline void add_knight_king_moves(const Position& p, MoveList& ml, const uint64_t* table,
        int pieceIndex, bool white) {
    uint64_t own = white ? p.whiteOcc : p.blackOcc;
    uint64_t enemyKing = p.pieces[white ? BK : WK];
    uint64_t pieces = p.pieces[pieceIndex];
    while (pieces) {
        int from = ctz64(pieces);
        pieces &= pieces - 1;
        add_targets(ml, from, table[from] & ~own & ~enemyKing);
    }
}

PERFT_HD inline void add_slider_moves(const Tables& t, const Position& p, MoveList& ml,
        int pieceIndex, bool white, bool diagonal, bool orthogonal) {
    uint64_t own = white ? p.whiteOcc : p.blackOcc;
    uint64_t enemyKing = p.pieces[white ? BK : WK];
    uint64_t pieces = p.pieces[pieceIndex];
    while (pieces) {
        int from = ctz64(pieces);
        pieces &= pieces - 1;
        uint64_t targets = 0;
        if (diagonal) targets |= bishop_attacks(t, from, p.occ);
        if (orthogonal) targets |= rook_attacks(t, from, p.occ);
        add_targets(ml, from, targets & ~own & ~enemyKing);
    }
}

PERFT_HD inline int rank_index(int sq) { return rank_of(sq); }

PERFT_HD inline bool valid_castle_geometry(bool white, int right, int kingFrom, int rookFrom) {
    int homeRank = white ? 0 : 7;
    if (rank_of(kingFrom) != homeRank || rank_of(rookFrom) != homeRank) return false;
    int kingFile = file_of(kingFrom);
    int rookFile = file_of(rookFrom);
    if (rookFile == kingFile) return false;
    bool kingside = (right == CASTLE_WK || right == CASTLE_BK);
    return kingside ? (rookFile > kingFile) : (rookFile < kingFile);
}

// Same-rank inclusive index span between two squares (mirror MoveGenerator.betweenInclusive).
PERFT_HD inline uint64_t between_inclusive(int a, int b) {
    if (rank_of(a) != rank_of(b)) {
        return (1ULL << a) | (1ULL << b);
    }
    int start = a < b ? a : b;
    int end = a < b ? b : a;
    uint64_t mask = 0;
    for (int sq = start; sq <= end; ++sq) {
        mask |= 1ULL << sq;
    }
    return mask;
}

PERFT_HD inline bool safe_king_castle_path(const Tables& t, const Position& p, int kingFrom,
        int kingTo, bool byWhite) {
    uint64_t path = between_inclusive(kingFrom, kingTo) & ~(1ULL << kingFrom);
    while (path) {
        int sq = ctz64(path);
        path &= path - 1;
        if (square_attacked(t, p, sq, byWhite, p.occ, 0ULL)) return false;
    }
    return true;
}

PERFT_HD inline void add_castle(const Tables& t, const Position& p, MoveList& ml, bool white, int right) {
    if (!(p.castling & right)) return;
    int kingFrom = white ? p.wk : p.bk;
    int rookFrom = castling_rook_square(p, right);
    int kingTo = castling_king_target(right);
    int rookTo = castling_rook_target(right);
    if (kingFrom == NO_SQUARE || rookFrom == NO_SQUARE) return;
    if (p.board[kingFrom] != (white ? WK : BK)) return;
    if (p.board[rookFrom] != (white ? WR : BR)) return;
    if (!valid_castle_geometry(white, right, kingFrom, rookFrom)) return;
    if (square_attacked(t, p, kingFrom, !white, p.occ, 0ULL)) return;

    uint64_t emptyMask = (between_inclusive(kingFrom, kingTo)
            | between_inclusive(kingFrom, rookFrom)
            | between_inclusive(rookFrom, rookTo))
            & ~(1ULL << kingFrom) & ~(1ULL << rookFrom);
    if (p.occ & emptyMask) return;
    if (!safe_king_castle_path(t, p, kingFrom, kingTo, !white)) return;
    push_move(ml, kingFrom, castling_move_target(p, right), PROMO_NONE);
}

PERFT_HD inline void generate_pseudo_moves(const Tables& t, const Position& p, MoveList& ml) {
    ml.count = 0;
    bool white = p.whiteToMove;
    if (white) {
        add_white_pawn_moves(p, ml);
        add_knight_king_moves(p, ml, t.knight, WN, true);
        add_slider_moves(t, p, ml, WB, true, true, false);
        add_slider_moves(t, p, ml, WR, true, false, true);
        add_slider_moves(t, p, ml, WQ, true, true, true);
        add_knight_king_moves(p, ml, t.king, WK, true);
        add_castle(t, p, ml, true, CASTLE_WK);
        add_castle(t, p, ml, true, CASTLE_WQ);
    } else {
        add_black_pawn_moves(p, ml);
        add_knight_king_moves(p, ml, t.knight, BN, false);
        add_slider_moves(t, p, ml, BB, false, true, false);
        add_slider_moves(t, p, ml, BR, false, false, true);
        add_slider_moves(t, p, ml, BQ, false, true, true);
        add_knight_king_moves(p, ml, t.king, BK, false);
        add_castle(t, p, ml, false, CASTLE_BK);
        add_castle(t, p, ml, false, CASTLE_BQ);
    }
}

// ---------------------------------------------------------------------------
// Make / undo (mirror PositionMoveSupport.play / undo, clocks omitted)
// ---------------------------------------------------------------------------
PERFT_HD inline int next_en_passant_square(const Tables& t, const Position& p, bool pawnMove,
        int delta, int from, int actualTo, bool white) {
    if (!pawnMove || (delta != 16 && delta != -16)) return NO_SQUARE;
    int target = (from + actualTo) / 2;
    uint64_t enemyPawns = p.pieces[white ? BP : WP];
    uint64_t attackers = white ? t.wPawn[target] : t.bPawn[target];
    return (attackers & enemyPawns) == 0ULL ? NO_SQUARE : target;
}

PERFT_HD inline void make_move(const Tables& t, Position& p, uint16_t move, Undo& u) {
    int from = move & 0x3F;
    int to = (move >> 6) & 0x3F;
    int promo = (move >> 12) & 0x7;
    int moving = p.board[from];
    bool white = (moving < BP);

    int castleRight = castling_right_for_move(p, moving, to);
    bool castle = castleRight != 0;
    int actualTo = castle ? castling_king_target(castleRight) : to;
    int captured = castle ? -1 : p.board[actualTo];
    int capturedSquare = actualTo;
    bool pawnMove = (moving == WP || moving == BP);

    u.moving = moving;
    u.captured = captured;
    u.capturedSquare = capturedSquare;
    u.kingTo = actualTo;
    u.castling = p.castling;
    u.ep = p.ep;
    u.whiteToMove = p.whiteToMove;
    u.rook = -1;
    u.rookFrom = NO_SQUARE;
    u.rookTo = NO_SQUARE;
    u.castle = false;
    u.enPassant = false;

    if (p.castling != 0) {
        p.castling &= castling_keep_for_square(p, from) & castling_keep_for_square(p, to);
    }

    clear_piece(p, moving, from);
    if (captured >= 0) {
        clear_piece(p, captured, actualTo);
    }

    if (pawnMove && captured < 0 && actualTo == p.ep) {
        int capSq = white ? actualTo + 8 : actualTo - 8;
        int capPiece = white ? BP : WP;
        u.captured = capPiece;
        u.capturedSquare = capSq;
        u.enPassant = true;
        clear_piece(p, capPiece, capSq);
    }

    int delta = actualTo - from;

    if (castle) {
        int rook = white ? WR : BR;
        int rookFrom = castling_rook_square(p, castleRight);
        int rookTo = castling_rook_target(castleRight);
        u.rook = rook;
        u.rookFrom = rookFrom;
        u.rookTo = rookTo;
        u.castle = true;
        move_piece(p, rook, rookFrom, rookTo);
    }

    int placed = (promo == 0) ? moving : promotion_piece_index(moving, promo);
    set_piece(p, placed, actualTo);

    p.ep = next_en_passant_square(t, p, pawnMove, delta, from, actualTo, white);
    p.whiteToMove = !p.whiteToMove;
}

PERFT_HD inline void undo_move(Position& p, uint16_t move, const Undo& u) {
    int from = move & 0x3F;
    int to = u.castle ? u.kingTo : ((move >> 6) & 0x3F);
    int promo = (move >> 12) & 0x7;
    int placed = (promo == 0) ? u.moving : promotion_piece_index(u.moving, promo);
    bool white = (u.moving < BP);
    uint64_t fromMask = 1ULL << from;
    uint64_t toMask = 1ULL << to;

    // 1. remove placed piece from destination (manual, no king-square touch).
    p.pieces[placed] &= ~toMask;
    p.board[to] = -1;
    if (white) {
        p.whiteOcc &= ~toMask;
    } else {
        p.blackOcc &= ~toMask;
    }
    p.occ &= ~toMask;

    // 2. undo castling rook (clear destination) before restoring the king on `from`.
    if (u.rook >= 0) {
        clear_piece(p, u.rook, u.rookTo);
    }

    // 3. restore moving piece on origin (manual occupancy + explicit king-square fix).
    p.pieces[u.moving] |= fromMask;
    p.board[from] = (signed char) u.moving;
    if (white) {
        p.whiteOcc |= fromMask;
    } else {
        p.blackOcc |= fromMask;
    }
    p.occ |= fromMask;
    if (u.moving == WK) {
        p.wk = from;
    } else if (u.moving == BK) {
        p.bk = from;
    }

    // 4. restore castling rook on its origin.
    if (u.rook >= 0) {
        set_piece(p, u.rook, u.rookFrom);
    }

    // 5. restore captured piece (normal or en-passant; capturedSquare covers both).
    if (u.captured >= 0) {
        uint64_t capMask = 1ULL << u.capturedSquare;
        p.pieces[u.captured] |= capMask;
        p.board[u.capturedSquare] = (signed char) u.captured;
        if (u.captured < BP) {
            p.whiteOcc |= capMask;
        } else {
            p.blackOcc |= capMask;
        }
        p.occ |= capMask;
    }

    // 6. restore snapshotted scalars.
    p.castling = u.castling;
    p.ep = u.ep;
    p.whiteToMove = u.whiteToMove;
}

// ---------------------------------------------------------------------------
// Perft (recursive; host + small-depth device use)
// ---------------------------------------------------------------------------
PERFT_HD inline uint64_t perft(const Tables& t, Position& p, int depth) {
    if (depth == 0) return 1ULL;
    MoveList ml;
    generate_pseudo_moves(t, p, ml);
    bool white = p.whiteToMove;
    uint64_t nodes = 0;
    for (int i = 0; i < ml.count; ++i) {
        uint16_t move = ml.moves[i];
        Undo u;
        make_move(t, p, move, u);
        if (!king_attacked(t, p, white)) {
            nodes += (depth == 1) ? 1ULL : perft(t, p, depth - 1);
        }
        undo_move(p, move, u);
    }
    return nodes;
}

// ---------------------------------------------------------------------------
// Iterative perft (explicit stack; recursion-free for SYCL/GPU device code)
// ---------------------------------------------------------------------------
constexpr int MAX_PERFT_DEPTH = 16;

PERFT_HD inline uint64_t perft_iter(const Tables& t, Position& p, int depth) {
    if (depth <= 0) return 1ULL;
    if (depth > MAX_PERFT_DEPTH) return 0ULL; // guard; perft this deep is infeasible per thread

    MoveList ml[MAX_PERFT_DEPTH];
    Undo undo[MAX_PERFT_DEPTH];
    int idx[MAX_PERFT_DEPTH];
    bool white[MAX_PERFT_DEPTH];

    uint64_t nodes = 0;
    int sp = 0;
    generate_pseudo_moves(t, p, ml[0]);
    idx[0] = 0;
    white[0] = p.whiteToMove;

    for (;;) {
        if (idx[sp] >= ml[sp].count) {
            if (sp == 0) break;
            --sp;
            undo_move(p, ml[sp].moves[idx[sp]], undo[sp]);
            ++idx[sp];
            continue;
        }
        uint16_t move = ml[sp].moves[idx[sp]];
        make_move(t, p, move, undo[sp]);
        if (king_attacked(t, p, white[sp])) {
            undo_move(p, move, undo[sp]);
            ++idx[sp];
            continue;
        }
        if (sp == depth - 1) {
            ++nodes;
            undo_move(p, move, undo[sp]);
            ++idx[sp];
            continue;
        }
        ++sp;
        generate_pseudo_moves(t, p, ml[sp]);
        idx[sp] = 0;
        white[sp] = p.whiteToMove;
    }
    return nodes;
}

// ---------------------------------------------------------------------------
// Detailed perft (per-leaf classification: captures, ep, castles, promotions,
// checks, checkmates) mirroring chess.debug.Perft
// ---------------------------------------------------------------------------
PERFT_HD inline bool has_legal_move(const Tables& t, Position& p) {
    MoveList ml;
    generate_pseudo_moves(t, p, ml);
    bool white = p.whiteToMove;
    for (int i = 0; i < ml.count; ++i) {
        uint16_t move = ml.moves[i];
        Undo u;
        make_move(t, p, move, u);
        bool legal = !king_attacked(t, p, white);
        undo_move(p, move, u);
        if (legal) {
            return true;
        }
    }
    return false;
}

PERFT_HD inline void classify_leaf(const Tables& t, Position& p, uint16_t move, const Undo& u,
        PerftCounts& out) {
    out.nodes++;
    if (u.captured >= 0) {
        out.captures++;
    }
    if (u.enPassant) {
        out.enPassant++;
    }
    if (u.castle) {
        out.castles++;
    }
    if (((move >> 12) & 0x7) != 0) {
        out.promotions++;
    }
    // p is the position after the move; the side to move is the opponent.
    if (king_attacked(t, p, p.whiteToMove)) {
        out.checks++;
        if (!has_legal_move(t, p)) {
            out.checkmates++;
        }
    }
}

PERFT_HD inline void perft_detailed_iter(const Tables& t, Position& p, int depth, PerftCounts& out) {
    out.nodes = 0;
    out.captures = 0;
    out.enPassant = 0;
    out.castles = 0;
    out.promotions = 0;
    out.checks = 0;
    out.checkmates = 0;
    if (depth <= 0) {
        out.nodes = 1;
        return;
    }
    if (depth > MAX_PERFT_DEPTH) {
        return;
    }

    MoveList ml[MAX_PERFT_DEPTH];
    Undo undo[MAX_PERFT_DEPTH];
    int idx[MAX_PERFT_DEPTH];
    bool white[MAX_PERFT_DEPTH];

    int sp = 0;
    generate_pseudo_moves(t, p, ml[0]);
    idx[0] = 0;
    white[0] = p.whiteToMove;

    for (;;) {
        if (idx[sp] >= ml[sp].count) {
            if (sp == 0) {
                break;
            }
            --sp;
            undo_move(p, ml[sp].moves[idx[sp]], undo[sp]);
            ++idx[sp];
            continue;
        }
        uint16_t move = ml[sp].moves[idx[sp]];
        make_move(t, p, move, undo[sp]);
        if (king_attacked(t, p, white[sp])) {
            undo_move(p, move, undo[sp]);
            ++idx[sp];
            continue;
        }
        if (sp == depth - 1) {
            classify_leaf(t, p, move, undo[sp], out);
            undo_move(p, move, undo[sp]);
            ++idx[sp];
            continue;
        }
        ++sp;
        generate_pseudo_moves(t, p, ml[sp]);
        idx[sp] = 0;
        white[sp] = p.whiteToMove;
    }
}

// ---------------------------------------------------------------------------
// Packed-position codec (Java <-> native contract)
//
// Layout: 13 little-endian uint64 per position.
//   words[0..11] = piece bitboards (WP..BK)
//   words[12]    = meta:
//      bit0      whiteToMove
//      bit1      chess960
//      bits2..5  castling rights
//      bits8..15 en-passant square   (0xFF = none)
//      bits16..23 white kingside rook source square (0xFF = none)
//      bits24..31 white queenside rook source square
//      bits32..39 black kingside rook source square
//      bits40..47 black queenside rook source square
// Occupancies, board[], and king squares are derived (not stored).
// ---------------------------------------------------------------------------
constexpr int PACK_WORDS = 13;

PERFT_HD inline int unpack_square(uint64_t meta, int shift) {
    int v = (int) ((meta >> shift) & 0xFF);
    return v == 0xFF ? NO_SQUARE : v;
}

PERFT_HD inline uint64_t pack_square(int sq) {
    return (uint64_t) (sq < 0 ? 0xFF : (sq & 0xFF));
}

// Serialize a position to PACK_WORDS little-endian words (must match unpack_position
// and chess.debug.gpu.PositionCodec on the Java side).
PERFT_HD inline void pack_position(const Position& p, uint64_t* words) {
    for (int piece = 0; piece < 12; ++piece) {
        words[piece] = p.pieces[piece];
    }
    uint64_t meta = 0;
    if (p.whiteToMove) meta |= 1ULL;
    if (p.chess960) meta |= 2ULL;
    meta |= (uint64_t) (p.castling & 0xF) << 2;
    meta |= pack_square(p.ep) << 8;
    meta |= pack_square(p.wkRook) << 16;
    meta |= pack_square(p.wqRook) << 24;
    meta |= pack_square(p.bkRook) << 32;
    meta |= pack_square(p.bqRook) << 40;
    words[12] = meta;
}

PERFT_HD inline void unpack_position(const uint64_t* words, Position& p) {
    p.whiteOcc = 0;
    p.blackOcc = 0;
    for (int i = 0; i < 64; ++i) p.board[i] = -1;
    for (int piece = 0; piece < 12; ++piece) {
        uint64_t bb = words[piece];
        p.pieces[piece] = bb;
        if (piece < BP) {
            p.whiteOcc |= bb;
        } else {
            p.blackOcc |= bb;
        }
        while (bb) {
            int sq = ctz64(bb);
            bb &= bb - 1;
            p.board[sq] = (signed char) piece;
        }
    }
    p.occ = p.whiteOcc | p.blackOcc;
    p.wk = p.pieces[WK] ? ctz64(p.pieces[WK]) : NO_SQUARE;
    p.bk = p.pieces[BK] ? ctz64(p.pieces[BK]) : NO_SQUARE;

    uint64_t meta = words[12];
    p.whiteToMove = (meta & 1ULL) != 0;
    p.chess960 = (meta & 2ULL) != 0;
    p.castling = (int) ((meta >> 2) & 0xF);
    p.ep = unpack_square(meta, 8);
    p.wkRook = unpack_square(meta, 16);
    p.wqRook = unpack_square(meta, 24);
    p.bkRook = unpack_square(meta, 32);
    p.bqRook = unpack_square(meta, 40);
}

} // namespace crtk_perft

#endif // CRTK_PERFT_CORE_H
