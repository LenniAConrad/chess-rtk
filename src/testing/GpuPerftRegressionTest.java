package testing;

import static testing.TestSupport.*;

import chess.core.Move;
import chess.core.Position;
import chess.core.Setup;
import chess.debug.Perft;
import chess.debug.SplitPerft;
import chess.debug.gpu.GpuPerft;
import chess.debug.gpu.NativePerftBackend;
import chess.debug.gpu.PositionCodec;

/**
 * End-to-end checks for the native (GPU) bulk-perft path.
 *
 * <p>
 * When a native perft backend is loaded (e.g. {@code CRTK_PERFT_CUDA_LIB} points
 * at a real or host-emulated library), the device bulk counter must agree with
 * the trusted CPU split perft at every split depth, including Chess960 positions.
 * When no backend is present the checks are skipped so the suite stays green on
 * machines without a GPU.
 * </p>
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class GpuPerftRegressionTest {

    /**
     * Perft cases covering castling, en passant, promotions, checks, and Chess960.
     */
    private static final PerftCase[] CASES = {
            new PerftCase("startpos", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 4),
            new PerftCase("kiwipete",
                    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 3),
            new PerftCase("ep-and-checks", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 4),
            new PerftCase("promotions", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 3),
            new PerftCase("chess960", "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1", 3)
    };

    /**
     * Prevents instantiation of this utility class.
     */
    private GpuPerftRegressionTest() {
        // utility
    }

    /**
     * Runs the native bulk-perft regression checks.
     *
     * @param args ignored.
     */
    public static void main(String[] args) {
        if (!NativePerftBackend.isAvailable()) {
            System.out.println("GpuPerftRegressionTest: skipped (no native perft backend; "
                    + "set CRTK_PERFT_CUDA_LIB to a libperft_*.so to enable)");
            return;
        }
        System.out.println("GpuPerftRegressionTest: backend=" + NativePerftBackend.name());
        testNativeMatchesCpu();
        testChess960Castling();
        testDetailedMatchesCpu();
        testDivideMatchesCpu();
        testPackLength();
        System.out.println("GpuPerftRegressionTest: all checks passed");
    }

    /**
     * Verifies native bulk perft equals CPU perft at every split depth.
     */
    private static void testNativeMatchesCpu() {
        SplitPerft.BulkCounter gpu = GpuPerft.bulkCounter();
        for (PerftCase testCase : CASES) {
            Position position = new Position(testCase.fen());
            long expected = SplitPerft.perft(position.copy(), testCase.depth(), 0, SplitPerft.CPU);
            for (int split = 0; split <= testCase.depth(); split++) {
                long got = SplitPerft.perft(position.copy(), testCase.depth(), split, gpu);
                assertEquals(expected, got,
                        testCase.name() + " native split=" + split + " depth=" + testCase.depth());
            }
        }
    }

    /**
     * Verifies native bulk perft on real Chess960 starts at a depth that reaches
     * castling, using the trusted CPU perft as the oracle. This exercises the
     * king-to-rook-square castling encoding with non-standard back ranks, which
     * the standard perft positions do not cover.
     */
    private static void testChess960Castling() {
        SplitPerft.BulkCounter gpu = GpuPerft.bulkCounter();
        int[] indexes = {0, 137, 360, 518, 762, 959};
        int depth = 5;
        for (int index : indexes) {
            Position position = Setup.getChess960ByIndex(index);
            long expected = SplitPerft.perft(position.copy(), depth, 0, SplitPerft.CPU);
            for (int split : new int[] {0, 2, depth}) {
                long got = SplitPerft.perft(position.copy(), depth, split, gpu);
                assertEquals(expected, got, "chess960[" + index + "] native split=" + split);
            }
        }
    }

    /**
     * Verifies the detailed device counters (captures, en passant, castles,
     * promotions, checks, checkmates) match the trusted CPU {@link Perft} at every
     * split depth.
     */
    private static void testDetailedMatchesCpu() {
        for (PerftCase testCase : CASES) {
            Position position = new Position(testCase.fen());
            Perft.Stats cpu = Perft.run(position.copy(), testCase.depth()).stats();
            for (int split = 0; split <= testCase.depth(); split++) {
                Perft.Stats gpu = GpuPerft.perftDetailed(position.copy(), testCase.depth(), split);
                String tag = testCase.name() + " detailed split=" + split;
                assertEquals(cpu.nodes(), gpu.nodes(), tag + " nodes");
                assertEquals(cpu.captures(), gpu.captures(), tag + " captures");
                assertEquals(cpu.enPassant(), gpu.enPassant(), tag + " enPassant");
                assertEquals(cpu.castles(), gpu.castles(), tag + " castles");
                assertEquals(cpu.promotions(), gpu.promotions(), tag + " promotions");
                assertEquals(cpu.checks(), gpu.checks(), tag + " checks");
                assertEquals(cpu.checkmates(), gpu.checkmates(), tag + " checkmates");
            }
        }
    }

    /**
     * Verifies GPU divide totals and per-root-move rows match CPU divide.
     */
    private static void testDivideMatchesCpu() {
        for (PerftCase testCase : CASES) {
            Position position = new Position(testCase.fen());
            int depth = Math.min(3, testCase.depth());
            assertDivideEquals(
                    Perft.divideNodes(position.copy(), depth),
                    GpuPerft.divide(position.copy(), depth, 1, false),
                    testCase.name() + " node divide");
            assertDivideEquals(
                    Perft.divide(position.copy(), depth),
                    GpuPerft.divide(position.copy(), depth, 1, true),
                    testCase.name() + " detailed divide");
        }
    }

    /**
     * Compares divide results, ignoring elapsed timing.
     *
     * @param expected CPU result
     * @param actual GPU result
     * @param tag assertion label
     */
    private static void assertDivideEquals(Perft.DivideResult expected, Perft.DivideResult actual, String tag) {
        assertStatsEquals(expected.total(), actual.total(), tag + " total");
        assertEquals(expected.entries().size(), actual.entries().size(), tag + " entry count");
        for (int i = 0; i < expected.entries().size(); i++) {
            Perft.DivideEntry expectedEntry = expected.entries().get(i);
            Perft.DivideEntry actualEntry = actual.entries().get(i);
            assertEquals(Move.toString(expectedEntry.move()), Move.toString(actualEntry.move()), tag + " move " + i);
            assertStatsEquals(expectedEntry.stats(), actualEntry.stats(), tag + " entry " + i);
        }
    }

    /**
     * Compares perft counters.
     *
     * @param expected expected stats
     * @param actual actual stats
     * @param tag assertion label
     */
    private static void assertStatsEquals(Perft.Stats expected, Perft.Stats actual, String tag) {
        assertEquals(expected.nodes(), actual.nodes(), tag + " nodes");
        assertEquals(expected.captures(), actual.captures(), tag + " captures");
        assertEquals(expected.enPassant(), actual.enPassant(), tag + " enPassant");
        assertEquals(expected.castles(), actual.castles(), tag + " castles");
        assertEquals(expected.promotions(), actual.promotions(), tag + " promotions");
        assertEquals(expected.checks(), actual.checks(), tag + " checks");
        assertEquals(expected.checkmates(), actual.checkmates(), tag + " checkmates");
    }

    /**
     * Verifies the pack codec produces the expected flat length.
     */
    private static void testPackLength() {
        Position position = new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        Position[] frontier = SplitPerft.expandFrontier(position, 1);
        long[] packed = PositionCodec.pack(frontier);
        assertEquals(frontier.length * PositionCodec.WORDS, packed.length, "packed length");
    }

    /**
     * One perft validation case.
     *
     * @param name display name.
     * @param fen  root FEN.
     * @param depth total perft depth.
     */
    private record PerftCase(
        /**
         * Stores the name.
         */
        String name,
        /**
         * Stores the fen.
         */
        String fen,
        /**
         * Stores the depth.
         */
        int depth
    ) {
    }
}
