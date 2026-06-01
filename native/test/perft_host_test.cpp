/*
 * native/test/perft_host_test.cpp
 *
 * Host (CPU) validation of native/common/perft_core.h against the canonical
 * Chess Programming Wiki perft node counts. Compiles with a plain C++17 host
 * compiler (no CUDA): the shared move generator is exercised exactly as the GPU
 * kernel will call it, so a green run here means the device port is correct up
 * to the kernel plumbing.
 *
 * Build:  g++ -O2 -std=c++17 native/test/perft_host_test.cpp -o /tmp/perft_host_test
 * Run:    /tmp/perft_host_test
 */
#include "../common/perft_core.h"

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>

using namespace crtk_perft;

// Minimal FEN parser for the host test (standard FEN + X-FEN castling letters).
static bool parse_fen(const std::string& fen, Position& p) {
    std::memset(&p, 0, sizeof(p));
    for (int i = 0; i < 64; ++i) p.board[i] = -1;
    p.wk = p.bk = NO_SQUARE;
    p.ep = NO_SQUARE;
    p.wkRook = p.wqRook = p.bkRook = p.bqRook = NO_SQUARE;

    size_t pos = 0;
    auto field = [&](void) -> std::string {
        while (pos < fen.size() && fen[pos] == ' ') ++pos;
        size_t start = pos;
        while (pos < fen.size() && fen[pos] != ' ') ++pos;
        return fen.substr(start, pos - start);
    };

    std::string board = field();
    int sq = 0; // a8 = 0 in this layout, reading order matches index order.
    for (char c : board) {
        if (c == '/') continue;
        if (c >= '1' && c <= '8') {
            sq += c - '0';
            continue;
        }
        int piece;
        switch (c) {
            case 'P': piece = WP; break; case 'N': piece = WN; break;
            case 'B': piece = WB; break; case 'R': piece = WR; break;
            case 'Q': piece = WQ; break; case 'K': piece = WK; break;
            case 'p': piece = BP; break; case 'n': piece = BN; break;
            case 'b': piece = BB; break; case 'r': piece = BR; break;
            case 'q': piece = BQ; break; case 'k': piece = BK; break;
            default: return false;
        }
        p.pieces[piece] |= 1ULL << sq;
        p.board[sq] = (signed char) piece;
        ++sq;
    }

    std::string side = field();
    p.whiteToMove = (side == "w");

    std::string castling = field();
    int whiteKing = p.pieces[WK] ? ctz64(p.pieces[WK]) : NO_SQUARE;
    int blackKing = p.pieces[BK] ? ctz64(p.pieces[BK]) : NO_SQUARE;
    if (castling != "-") {
        for (char c : castling) {
            if (c == 'K') { p.wkRook = 63; p.castling |= CASTLE_WK; }
            else if (c == 'Q') { p.wqRook = 56; p.castling |= CASTLE_WQ; }
            else if (c == 'k') { p.bkRook = 7; p.castling |= CASTLE_BK; }
            else if (c == 'q') { p.bqRook = 0; p.castling |= CASTLE_BQ; }
            else if (c >= 'A' && c <= 'H') {
                int f = c - 'A';
                int rsq = to_index(f, 0);
                if (whiteKing != NO_SQUARE && f > file_of(whiteKing)) { p.wkRook = rsq; p.castling |= CASTLE_WK; }
                else { p.wqRook = rsq; p.castling |= CASTLE_WQ; }
                p.chess960 = true;
            } else if (c >= 'a' && c <= 'h') {
                int f = c - 'a';
                int rsq = to_index(f, 7);
                if (blackKing != NO_SQUARE && f > file_of(blackKing)) { p.bkRook = rsq; p.castling |= CASTLE_BK; }
                else { p.bqRook = rsq; p.castling |= CASTLE_BQ; }
                p.chess960 = true;
            }
        }
    }

    std::string ep = field();
    if (ep != "-" && ep.size() >= 2) {
        int f = ep[0] - 'a';
        int r = ep[1] - '1';
        p.ep = to_index(f, r);
    }

    // Derive caches.
    p.whiteOcc = 0;
    p.blackOcc = 0;
    for (int i = 0; i < 6; ++i) p.whiteOcc |= p.pieces[i];
    for (int i = 6; i < 12; ++i) p.blackOcc |= p.pieces[i];
    p.occ = p.whiteOcc | p.blackOcc;
    p.wk = whiteKing;
    p.bk = blackKing;
    return true;
}

struct Case {
    const char* name;
    const char* fen;
    int maxDepth;
    uint64_t expected[7]; // expected[d] for d = 1..maxDepth
};

int main() {
    Tables t;
    build_tables(t);

    Case cases[] = {
        {"startpos", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 6,
            {0, 20, 400, 8902, 197281, 4865609, 119060324}},
        {"kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 4,
            {0, 48, 2039, 97862, 4085603, 0, 0}},
        {"position3", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 5,
            {0, 14, 191, 2812, 43238, 674624, 0}},
        {"position4", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 4,
            {0, 6, 264, 9467, 422333, 0, 0}},
        {"position5", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 4,
            {0, 44, 1486, 62379, 2103487, 0, 0}},
        {"position6", "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 4,
            {0, 46, 2079, 89890, 3894594, 0, 0}},
        // Standard start, X-FEN (Chess960) castling letters: must match startpos
        // while routing castling through the king-to-rook-square encoding.
        {"xfen-start", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1", 5,
            {0, 20, 400, 8902, 197281, 4865609, 0}},
    };

    int failures = 0;
    for (const Case& c : cases) {
        Position root;
        if (!parse_fen(c.fen, root)) {
            std::printf("[FAIL] %-10s could not parse FEN\n", c.name);
            ++failures;
            continue;
        }
        for (int d = 1; d <= c.maxDepth; ++d) {
            Position p1 = root;
            uint64_t rec = perft(t, p1, d);
            Position p2 = root;
            uint64_t iter = perft_iter(t, p2, d); // the function the GPU kernel calls
            uint64_t want = c.expected[d];
            bool ok = rec == want && iter == want;
            std::printf("[%s] %-10s depth %d: rec=%llu iter=%llu (expected %llu)\n",
                    ok ? " OK " : "FAIL", c.name, d,
                    (unsigned long long) rec, (unsigned long long) iter,
                    (unsigned long long) want);
            if (!ok) ++failures;
        }

        // Codec round-trip: pack -> unpack -> perft_iter is exactly what each GPU
        // thread computes, so this validates pack_position/unpack_position too.
        uint64_t words[crtk_perft::PACK_WORDS];
        pack_position(root, words);
        Position decoded;
        unpack_position(words, decoded);
        uint64_t viaCodec = perft_iter(t, decoded, c.maxDepth);
        bool codecOk = viaCodec == c.expected[c.maxDepth];
        std::printf("[%s] %-10s codec   d%d: %llu (expected %llu)\n",
                codecOk ? " OK " : "FAIL", c.name, c.maxDepth,
                (unsigned long long) viaCodec, (unsigned long long) c.expected[c.maxDepth]);
        if (!codecOk) ++failures;
    }

    // Detailed counters vs canonical CPW numbers: {nodes,captures,ep,castles,promotions,checks,mates}.
    struct DetailedCase {
        const char* name;
        const char* fen;
        int depth;
        unsigned long long expect[7];
    };
    DetailedCase detailed[] = {
        {"startpos-d4", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 4,
            {197281, 1576, 0, 0, 0, 469, 8}},
        {"startpos-d5", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 5,
            {4865609, 82719, 258, 0, 0, 27351, 347}},
        {"kiwipete-d3", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 3,
            {97862, 17102, 45, 3162, 0, 993, 1}},
        {"kiwipete-d4", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 4,
            {4085603, 757163, 1929, 128013, 15172, 25523, 43}},
    };
    const char* labels[7] = {"nodes", "captures", "ep", "castles", "promotions", "checks", "mates"};
    for (const DetailedCase& c : detailed) {
        Position root;
        parse_fen(c.fen, root);
        Position p = root;
        PerftCounts got;
        perft_detailed_iter(t, p, c.depth, got);
        unsigned long long g[7] = {got.nodes, got.captures, got.enPassant, got.castles,
                got.promotions, got.checks, got.checkmates};
        bool ok = true;
        for (int i = 0; i < 7; ++i) {
            if (g[i] != c.expect[i]) {
                ok = false;
            }
        }
        std::printf("[%s] %-12s detailed d%d\n", ok ? " OK " : "FAIL", c.name, c.depth);
        if (!ok) {
            for (int i = 0; i < 7; ++i) {
                std::printf("        %-11s got=%llu expected=%llu%s\n", labels[i], g[i], c.expect[i],
                        g[i] == c.expect[i] ? "" : "  <-- MISMATCH");
            }
            ++failures;
        }
    }

    if (failures == 0) {
        std::printf("\nperft_host_test: all checks passed\n");
        return 0;
    }
    std::printf("\nperft_host_test: %d FAILURES\n", failures);
    return 1;
}
