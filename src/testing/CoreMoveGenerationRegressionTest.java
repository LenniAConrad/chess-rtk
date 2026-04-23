package testing;

import static testing.TestSupport.*;

import java.util.Set;
import java.util.TreeSet;

import chess.core.Bits;
import chess.core.Fen;
import chess.core.MoveGenerator;
import chess.core.SAN;
import chess.core.SlidingAttacks;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.debug.Perft;

/**
 * Regression checks for core move generation.
 */
public final class CoreMoveGenerationRegressionTest {

    /**
     * Default CPW depth for normal regression runs.
     */
    private static final int DEFAULT_CPW_MAX_DEPTH = 3;

    /**
     * System-property override for deeper local CPW perft checks.
     */
    private static final int CPW_MAX_DEPTH = Integer.getInteger(
            "crtk.core.cpw.depth",
            DEFAULT_CPW_MAX_DEPTH);

    /**
     * Default detailed CPW depth for normal regression runs.
     */
    private static final int DEFAULT_DETAILED_CPW_MAX_DEPTH = 3;

    /**
     * System-property override for detailed CPW perft checks.
     */
    private static final int DETAILED_CPW_MAX_DEPTH = Integer.getInteger(
            "crtk.core.detailed.cpw.depth",
            DEFAULT_DETAILED_CPW_MAX_DEPTH);

    /**
     * Chessprogramming Wiki perft positions and published node counts.
     */
    private static final PerftCase[] CPW_PERFT = {
            new PerftCase(
                    "CPW initial position",
                    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    new long[] { 1L, 20L, 400L, 8902L, 197281L, 4865609L, 119060324L }),
            new PerftCase(
                    "CPW position 2 Kiwipete",
                    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                    new long[] { 1L, 48L, 2039L, 97862L, 4085603L, 193690690L, 8031647685L }),
            new PerftCase(
                    "CPW position 3",
                    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
                    new long[] { 1L, 14L, 191L, 2812L, 43238L, 674624L, 11030083L, 178633661L, 3009794393L }),
            new PerftCase(
                    "CPW position 4",
                    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                    new long[] { 1L, 6L, 264L, 9467L, 422333L, 15833292L, 706045033L }),
            new PerftCase(
                    "CPW position 4 mirrored",
                    "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
                    new long[] { 1L, 6L, 264L, 9467L, 422333L, 15833292L, 706045033L }),
            new PerftCase(
                    "CPW position 5",
                    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
                    new long[] { 1L, 44L, 1486L, 62379L, 2103487L, 89941194L }),
            new PerftCase(
                    "CPW position 6",
                    "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
                    new long[] { 1L, 46L, 2079L, 89890L, 3894594L, 164075551L, 6923051137L })
    };

    /**
     * Chessprogramming Wiki detailed perft positions.
     */
    private static final DetailedPerftCase[] CPW_DETAILED_PERFT = {
            new DetailedPerftCase(
                    "CPW initial position",
                    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    new Perft.Stats[] {
                            stats(1L, 0L, 0L, 0L, 0L, 0L, 0L),
                            stats(20L, 0L, 0L, 0L, 0L, 0L, 0L),
                            stats(400L, 0L, 0L, 0L, 0L, 0L, 0L),
                            stats(8902L, 34L, 0L, 0L, 0L, 12L, 0L),
                            stats(197281L, 1576L, 0L, 0L, 0L, 469L, 8L),
                            stats(4865609L, 82719L, 258L, 0L, 0L, 27351L, 347L),
                            stats(119060324L, 2812008L, 5248L, 0L, 0L, 809099L, 10828L)
                    }),
            new DetailedPerftCase(
                    "CPW position 2 Kiwipete",
                    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                    new Perft.Stats[] {
                            stats(1L, 0L, 0L, 0L, 0L, 0L, 0L),
                            stats(48L, 8L, 0L, 2L, 0L, 0L, 0L),
                            stats(2039L, 351L, 1L, 91L, 0L, 3L, 0L),
                            stats(97862L, 17102L, 45L, 3162L, 0L, 993L, 1L),
                            stats(4085603L, 757163L, 1929L, 128013L, 15172L, 25523L, 43L),
                            stats(193690690L, 35043416L, 73365L, 4993637L, 8392L, 3309887L, 30171L)
                    }),
            new DetailedPerftCase(
                    "CPW position 3",
                    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
                    new Perft.Stats[] {
                            stats(1L, 0L, 0L, 0L, 0L, 0L, 0L),
                            stats(14L, 1L, 0L, 0L, 0L, 2L, 0L),
                            stats(191L, 14L, 0L, 0L, 0L, 10L, 0L),
                            stats(2812L, 209L, 2L, 0L, 0L, 267L, 0L),
                            stats(43238L, 3348L, 123L, 0L, 0L, 1680L, 17L),
                            stats(674624L, 52051L, 1165L, 0L, 0L, 52950L, 0L),
                            stats(11030083L, 940350L, 33325L, 0L, 7552L, 452473L, 2733L)
                    }),
            new DetailedPerftCase(
                    "CPW position 4",
                    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                    new Perft.Stats[] {
                            stats(1L, 0L, 0L, 0L, 0L, 0L, 0L),
                            stats(6L, 0L, 0L, 0L, 0L, 0L, 0L),
                            stats(264L, 87L, 0L, 6L, 48L, 10L, 0L),
                            stats(9467L, 1021L, 4L, 0L, 120L, 38L, 22L),
                            stats(422333L, 131393L, 0L, 7795L, 60032L, 15492L, 5L),
                            stats(15833292L, 2046173L, 6512L, 0L, 329464L, 200568L, 50562L)
                    })
    };

    /**
     * Prevents instantiation.
     */
    private CoreMoveGenerationRegressionTest() {
        // utility
    }

    /**
     * Runs all checks.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        testAttackTables();
        testFenAndSanHelpers();
        testPositionHelpers();
        testInsufficientMaterial();
        testDetailedPerftCounters();
        testCpwDetailedPerftResults();
        testCpwMoveSetParity();
        testCpwPerftResults();
        testChess960CastlingSupport();
        System.out.println("CoreMoveGenerationRegressionTest: all checks passed");
    }

    /**
     * Verifies detailed perft classifies leaf move properties.
     */
    private static void testDetailedPerftCounters() {
        Perft.Stats startDepth2 = Perft.run(chess.core.Position.fromFen(CPW_PERFT[0].fen), 2).stats();
        assertEquals(400L, startDepth2.nodes(), "start position detailed perft depth 2 nodes");
        assertEquals(0L, startDepth2.captures(), "start position detailed perft depth 2 captures");
        assertEquals(0L, startDepth2.enPassant(), "start position detailed perft depth 2 en-passant");
        assertEquals(0L, startDepth2.castles(), "start position detailed perft depth 2 castles");
        assertEquals(0L, startDepth2.promotions(), "start position detailed perft depth 2 promotions");

        Perft.Stats castles = Perft.run(
                chess.core.Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"), 1).stats();
        assertEquals(2L, castles.castles(), "detailed perft castles");

        Perft.Stats enPassant = Perft.run(
                chess.core.Position.fromFen("k7/8/8/3Pp3/8/8/8/7K w - e6 0 1"), 1).stats();
        assertEquals(1L, enPassant.enPassant(), "detailed perft en-passant");
        assertEquals(1L, enPassant.captures(), "detailed perft en-passant capture");

        Perft.Stats promotions = Perft.run(
                chess.core.Position.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1"), 1).stats();
        assertEquals(4L, promotions.promotions(), "detailed perft promotions");

        Perft.Stats checks = Perft.run(
                chess.core.Position.fromFen("4k3/8/8/8/8/8/R7/4K3 w - - 0 1"), 1).stats();
        assertTrue(checks.checks() > 0L, "detailed perft checks");
    }

    /**
     * Verifies common engine attack helpers.
     */
    private static void testAttackTables() {
        assertEquals(8, Bits.popcount(MoveGenerator.knightAttacks(Field.toIndex("e4"))),
                "knight attacks from e4");
        assertEquals(14, Bits.popcount(MoveGenerator.rookAttacks(Field.toIndex("d4"), 0L)),
                "rook attacks from d4 on empty board");
        assertEquals(13, Bits.popcount(MoveGenerator.bishopAttacks(Field.toIndex("d4"), 0L)),
                "bishop attacks from d4 on empty board");
        assertEquals(10, Bits.popcount(SlidingAttacks.rookRelevantBlockers(Field.toIndex("d4"))),
                "rook relevant blockers from d4");
        assertEquals(9, Bits.popcount(SlidingAttacks.bishopRelevantBlockers(Field.toIndex("d4"))),
                "bishop relevant blockers from d4");

        long rookBlockers = Bits.bit(Field.toIndex("d6"))
                | Bits.bit(Field.toIndex("f4"))
                | Bits.bit(Field.toIndex("d2"))
                | Bits.bit(Field.toIndex("b4"));
        assertEquals(8, Bits.popcount(MoveGenerator.rookAttacks(Field.toIndex("d4"), rookBlockers)),
                "rook attacks from d4 with four blockers");
    }

    /**
     * Verifies direct core FEN and SAN conversion paths.
     */
    private static void testFenAndSanHelpers() {
        String start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        assertEquals(start, chess.core.Position.fromFen(start).toString(), "core FEN round trip");
        assertEquals(start, Fen.normalize(
                "  rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR   w   KQkq   -   0  1  "),
                "core FEN whitespace normalization");

        String chess960 = "4k3/8/8/8/8/8/8/RK5R w HA - 0 1";
        assertEquals(chess960, chess.core.Position.fromFen(chess960).toString(),
                "core Chess960 FEN round trip");

        chess.core.Position startPosition = chess.core.Position.fromFen(start);
        assertEquals("e4", SAN.toAlgebraic(startPosition, Move.parse("e2e4")),
                "core SAN pawn move");
        assertEquals("e2e4", Move.toString(SAN.fromAlgebraic(startPosition, "e4")),
                "core SAN pawn parse");

        chess.core.Position castlePosition = chess.core.Position.fromFen(
                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertEquals("O-O", SAN.toAlgebraic(castlePosition, Move.parse("e1g1")),
                "core SAN standard kingside castle");
        assertEquals("O-O-O", SAN.toAlgebraic(castlePosition, Move.parse("e1c1")),
                "core SAN standard queenside castle");
        assertEquals("e1g1", Move.toString(SAN.fromAlgebraic(castlePosition, "0-0!")),
                "core SAN normalized castle parse");

        chess.core.Position chess960Castle = chess.core.Position.fromFen(chess960);
        assertEquals("O-O", SAN.toAlgebraic(chess960Castle, Move.parse("b1h1")),
                "core SAN Chess960 kingside castle");
        assertEquals("O-O-O", SAN.toAlgebraic(chess960Castle, Move.parse("b1a1")),
                "core SAN Chess960 queenside castle");
        assertEquals("b1a1", Move.toString(SAN.fromAlgebraic(chess960Castle, "O-O-O")),
                "core SAN Chess960 castle parse");

        chess.core.Position promotion = chess.core.Position.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1");
        assertEquals("a8=Q+", SAN.toAlgebraic(promotion, Move.parse("a7a8q")),
                "core SAN promotion check");
        assertEquals("a7a8q", Move.toString(SAN.fromAlgebraic(promotion, "a8=Q+")),
                "core SAN promotion parse");

        chess.core.Position line = SAN.playLine(startPosition,
                "1.e4 e5 2.Nf3 Nc6").getResult();
        assertEquals("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
                line.toString(), "core SAN line play");

        short invalidPromotion = (short) ((5 << 12) | (Field.A8 << 6) | Field.B8);
        assertThrows(() -> Move.toString(invalidPromotion), "core invalid promotion UCI rejected");
    }

    /**
     * Verifies public core position helpers intended for tagging.
     */
    private static void testPositionHelpers() {
        String fen = CPW_PERFT[1].fen;
        Position core = new Position(fen);
        chess.core.Position bit = chess.core.Position.fromFen(fen);
        byte[] coreBoard = core.getBoard();
        byte[] bitBoard = bit.getBoard();
        for (int square = 0; square < 64; square++) {
            assertEquals(coreBoard[square], bitBoard[square], "helper board square " + square);
            assertEquals(core.countAttackersByWhite((byte) square), bit.countAttackersByWhite((byte) square),
                    "helper white attackers " + square);
            assertEquals(core.countAttackersByBlack((byte) square), bit.countAttackersByBlack((byte) square),
                    "helper black attackers " + square);
        }
        assertEquals(core.countWhiteMaterial(), bit.countWhiteMaterial(), "helper white material");
        assertEquals(core.countBlackMaterial(), bit.countBlackMaterial(), "helper black material");
        assertEquals(core.materialDiscrepancy(), bit.materialDiscrepancy(), "helper material discrepancy");
        assertEquals(core.countWhitePieces(), bit.countWhitePieces(), "helper white pieces");
        assertEquals(core.countBlackPieces(), bit.countBlackPieces(), "helper black pieces");
        assertEquals(core.countPieces(Piece.WHITE_BISHOP), bit.countPieces(Piece.WHITE_BISHOP),
                "helper bishop count");
        assertEquals(core.legalMoves().size(), bit.legalMoveCount(), "helper legal move count");
        assertTrue(core.inCheck() == bit.inCheck(), "helper in check");
        assertTrue(squareSet(core.getCheckers()).equals(squareSet(bit.getCheckers())), "helper checkers");
        assertEquals(0, bit.compareTo(bit.copy()), "helper compare copy");
        assertTrue(bit.equals(bit.copy()), "helper equals copy");
        assertEquals(bit.hashCode(), bit.copy().hashCode(), "helper hash copy");
        assertEquals(bit.signature(), bit.copy().signature(), "helper signature copy");

        chess.core.Position start = chess.core.Position.fromFen(CPW_PERFT[0].fen);
        chess.core.Position startDifferentClock = chess.core.Position.fromFen(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 7 12");
        assertEquals(start.signatureCore(), startDifferentClock.signatureCore(), "helper signature core ignores clocks");
        assertTrue(start.signature() != startDifferentClock.signature(), "helper signature includes clocks");
        long e2Targets = start.legalTargetsFrom(Field.E2);
        assertTrue((e2Targets & Bits.bit(Field.E3)) != 0L, "helper legal targets e2e3");
        assertTrue((e2Targets & Bits.bit(Field.E4)) != 0L, "helper legal targets e2e4");
        assertEquals(chess.core.Position.WHITE_PAWN, start.movingPiece(Move.parse("e2e4")),
                "helper moving piece");
        assertTrue(start.isLegalMove(Move.parse("e2e4")), "helper legal move test");
        assertTrue(!start.isCapture(Move.parse("e2e4")), "helper quiet move capture test");
        assertTrue(!start.isLegalMove(Move.NO_MOVE), "helper no-move legal test");
        assertTrue(!start.isCastle(Move.NO_MOVE), "helper no-move castle test");
        assertTrue(!start.isCapture(Move.NO_MOVE), "helper no-move capture test");
        assertTrue(!start.isPromotion(Move.NO_MOVE), "helper no-move promotion test");
        assertEquals(-1, start.movingPiece(Move.NO_MOVE), "helper no-move moving piece");
        assertEquals(Field.NO_SQUARE, start.actualToSquare(Move.NO_MOVE), "helper no-move target");
        assertEquals(Field.NO_SQUARE, start.capturedSquare(Move.NO_MOVE), "helper no-move captured square");
        assertThrows(() -> start.play(Move.NO_MOVE), "helper no-move play rejected");
        assertThrows(() -> start.pieceAt(-1), "helper invalid square rejected");
        assertThrows(() -> start.pieces(12), "helper invalid piece rejected");

        chess.core.Position castle = chess.core.Position.fromFen(
                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertTrue(castle.isCastle(Move.parse("e1g1")), "helper castle classifier");
        assertEquals(Field.G1, castle.actualToSquare(Move.parse("e1g1")), "helper castle target");

        chess.core.Position enPassant = chess.core.Position.fromFen(
                "k7/8/8/3Pp3/8/8/8/7K w - e6 0 1");
        short ep = Move.parse("d5e6");
        assertTrue(enPassant.isEnPassantCapture(ep), "helper en-passant classifier");
        assertEquals(chess.core.Position.BLACK_PAWN, enPassant.capturedPiece(ep),
                "helper en-passant captured piece");
        assertEquals(Field.E5, enPassant.capturedSquare(ep), "helper en-passant captured square");

        chess.core.Position pawns = chess.core.Position.fromFen(
                "8/8/8/8/8/P7/P7/4K2k w - - 0 1");
        assertEquals(2, Long.bitCount(pawns.doubledPawns(true)), "helper doubled pawns");
        assertEquals(2, Long.bitCount(pawns.isolatedPawns(true)), "helper isolated pawns");
        assertEquals(2, Long.bitCount(pawns.passedPawns(true)), "helper passed pawns");
        assertEquals(2, pawns.pawnFileCount(true, 0), "helper pawn file count");

        chess.core.Position bishops = chess.core.Position.fromFen(
                "4k3/8/8/8/8/8/2b5/2B1K3 w - - 0 1");
        assertTrue(bishops.hasOppositeColoredBishops(), "helper opposite-colored bishops");

        chess.core.Position pinned = chess.core.Position.fromFen(
                "4k3/8/8/8/4r3/8/4B3/4K3 w - - 0 1");
        chess.core.Position.PinInfo pin = pinned.findPinToOwnKing(Field.E2);
        assertTrue(pin != null, "helper pin exists");
        assertEquals(Field.E2, pin.pinnedSquare, "helper pinned square");
        assertEquals(Field.E4, pin.pinnerSquare, "helper pinner square");
        assertEquals(Piece.BLACK_ROOK, pin.pinnerPiece, "helper pinner piece");
    }

    /**
     * Verifies dead-material detection stays conservative enough for search.
     */
    private static void testInsufficientMaterial() {
        assertTrue(chess.core.Position.fromFen("8/8/8/8/8/8/8/4K2k w - - 0 1").isInsufficientMaterial(),
                "bare kings dead material");
        assertTrue(chess.core.Position.fromFen("8/8/8/8/8/8/8/2B1K2k w - - 0 1").isInsufficientMaterial(),
                "lone bishop cannot mate bare king");
        assertTrue(chess.core.Position.fromFen("7k/6b1/8/8/8/8/3B4/4K3 w - - 0 1").isInsufficientMaterial(),
                "same-colored bishops are dead material");
        assertFalse(chess.core.Position.fromFen("7k/7b/8/8/8/8/3B4/4K3 w - - 0 1").isInsufficientMaterial(),
                "opposite-colored bishops can contain mate positions");
        assertFalse(chess.core.Position.fromFen("k7/2K5/1NN5/8/8/8/8/8 b - - 0 1").isInsufficientMaterial(),
                "two knights can contain mate positions");
    }

    /**
     * Compares CPW legal move sets at depth 1 against the existing generator.
     */
    private static void testCpwMoveSetParity() {
        for (PerftCase testCase : CPW_PERFT) {
            assertMoveSetParity(testCase);
        }
    }

    /**
     * Compares perft counts against the Chessprogramming Wiki published counts.
     */
    private static void testCpwPerftResults() {
        if (CPW_MAX_DEPTH < 0) {
            throw new IllegalArgumentException("crtk.core.cpw.depth must be non-negative");
        }
        for (PerftCase testCase : CPW_PERFT) {
            int maxDepth = Math.min(CPW_MAX_DEPTH, testCase.nodes.length - 1);
            for (int depth = 0; depth <= maxDepth; depth++) {
                assertPerft(testCase, depth);
            }
        }
    }

    /**
     * Compares detailed perft counters against CPW published counters.
     */
    private static void testCpwDetailedPerftResults() {
        if (DETAILED_CPW_MAX_DEPTH < 0) {
            throw new IllegalArgumentException("crtk.core.detailed.cpw.depth must be non-negative");
        }
        for (DetailedPerftCase testCase : CPW_DETAILED_PERFT) {
            int maxDepth = Math.min(DETAILED_CPW_MAX_DEPTH, testCase.stats.length - 1);
            for (int depth = 0; depth <= maxDepth; depth++) {
                assertDetailedPerft(testCase, depth);
            }
        }
    }

    /**
     * Verifies Chess960 castling positions are accepted and counted correctly.
     */
    private static void testChess960CastlingSupport() {
        String openCastles = "4k3/8/8/8/8/8/8/RK5R w HA - 0 1";
        assertMoveSetParity(new PerftCase("Chess960 open castles", openCastles, new long[] { 1L, 25L }));
        Perft.Stats stats = Perft.run(chess.core.Position.fromFen(openCastles), 1).stats();
        assertEquals(25L, stats.nodes(), "Chess960 open castles depth 1 nodes");
        assertEquals(2L, stats.castles(), "Chess960 open castles depth 1 castles");

        assertMoveSetParity(new PerftCase(
                "Chess960 start 0",
                "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1",
                new long[] { 1L, 20L }));
    }

    /**
     * Compares legal move sets for one FEN.
     *
     * @param fen FEN
     */
    private static void assertMoveSetParity(PerftCase testCase) {
        Position position = new Position(testCase.fen);
        Set<String> expected = moveSet(position.legalMoves());
        Set<String> actual = moveSet(MoveGenerator.generateLegalMoves(position.copy()));
        if (!expected.equals(actual)) {
            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(actual);
            Set<String> extra = new TreeSet<>(actual);
            extra.removeAll(expected);
            throw new AssertionError(testCase.name + " move set mismatch\nmissing=" + missing + "\nextra=" + extra);
        }
        assertEquals(testCase.nodes[1], actual.size(), testCase.name + " depth 1 move count");
    }

    /**
     * Compares perft counts for one CPW case and depth.
     *
     * @param testCase CPW case
     * @param depth depth
     */
    private static void assertPerft(PerftCase testCase, int depth) {
        long expected = testCase.nodes[depth];
        long actual = MoveGenerator.perft(chess.core.Position.fromFen(testCase.fen), depth);
        assertEquals(expected, actual, testCase.name + " perft depth " + depth);
    }

    /**
     * Compares detailed perft for one CPW case and depth.
     */
    private static void assertDetailedPerft(DetailedPerftCase testCase, int depth) {
        Perft.Stats expected = testCase.stats[depth];
        Perft.Stats actual = Perft.run(chess.core.Position.fromFen(testCase.fen), depth).stats();
        assertEquals(expected.nodes(), actual.nodes(), testCase.name + " detailed nodes depth " + depth);
        assertEquals(expected.captures(), actual.captures(), testCase.name + " detailed captures depth " + depth);
        assertEquals(expected.enPassant(), actual.enPassant(), testCase.name + " detailed ep depth " + depth);
        assertEquals(expected.castles(), actual.castles(), testCase.name + " detailed castles depth " + depth);
        assertEquals(expected.promotions(), actual.promotions(), testCase.name + " detailed promotions depth " + depth);
        assertEquals(expected.checks(), actual.checks(), testCase.name + " detailed checks depth " + depth);
        assertEquals(expected.checkmates(), actual.checkmates(), testCase.name + " detailed mates depth " + depth);
    }

    /**
     * Creates detailed perft stats.
     */
    private static Perft.Stats stats(
            long nodes,
            long captures,
            long enPassant,
            long castles,
            long promotions,
            long checks,
            long checkmates) {
        return new Perft.Stats(nodes, captures, enPassant, castles, promotions, checks, checkmates);
    }

    /**
     * Converts a move list into UCI text.
     *
     * @param moves moves
     * @return sorted set
     */
    private static Set<String> moveSet(MoveList moves) {
        Set<String> out = new TreeSet<>();
        for (int i = 0; i < moves.size(); i++) {
            out.add(Move.toString(moves.get(i)));
        }
        return out;
    }

    /**
     * Verifies that a callback throws an illegal-argument exception.
     */
    private static void assertThrows(Runnable runnable, String label) {
        try {
            runnable.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + ": expected IllegalArgumentException");
    }

    /**
     * Converts square arrays to stable sets for order-insensitive comparisons.
     */
    private static Set<Integer> squareSet(byte[] squares) {
        Set<Integer> out = new TreeSet<>();
        for (byte square : squares) {
            out.add((int) square);
        }
        return out;
    }

    /**
     * One CPW perft case.
     */
    private record PerftCase(
            /**
             * Case name.
             */
            String name,
            /**
             * FEN.
             */
            String fen,
            /**
             * Node counts indexed by depth.
             */
            long[] nodes) {
    }

    /**
     * One CPW detailed perft case.
     */
    private record DetailedPerftCase(
            /**
             * Case name.
             */
            String name,
            /**
             * FEN.
             */
            String fen,
            /**
             * Detailed counters indexed by depth.
             */
            Perft.Stats[] stats) {
    }
}
