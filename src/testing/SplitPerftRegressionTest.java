package testing;

import static testing.TestSupport.*;

import chess.core.MoveGenerator;
import chess.core.Position;
import chess.debug.SplitPerft;

/**
 * Regression checks for the split-depth perft driver used by GPU backends.
 *
 * <p>
 * The total node count produced by {@link SplitPerft} must be independent of the
 * split depth and must equal the trusted recursive perft. These checks exercise
 * every split depth from {@code 0} (single bulk call) up to and beyond
 * {@code depth} (frontier is all leaves) on positions that stress en passant,
 * castling, promotions, and checks, using both the sequential and parallel CPU
 * bulk counters.
 * </p>
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class SplitPerftRegressionTest {

    /**
     * Reference perft cases covering the classic move-generation edge cases.
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
    private SplitPerftRegressionTest() {
        // utility
    }

    /**
     * Runs all split-depth perft regression checks.
     *
     * @param args ignored.
     */
    public static void main(String[] args) {
        testSplitDepthInvariance();
        testEmptyFrontierIsZero();
        testDepthZeroIsOne();
        System.out.println("SplitPerftRegressionTest: all checks passed");
    }

    /**
     * Verifies the node count is split-depth invariant and matches recursive perft.
     */
    private static void testSplitDepthInvariance() {
        for (PerftCase testCase : CASES) {
            Position position = new Position(testCase.fen());
            long expected = MoveGenerator.perft(position.copy(), testCase.depth());
            for (int split = 0; split <= testCase.depth() + 1; split++) {
                long sequential = SplitPerft.perft(position.copy(), testCase.depth(), split, SplitPerft.CPU);
                assertEquals(expected, sequential,
                        testCase.name() + " sequential split=" + split + " depth=" + testCase.depth());

                long parallel = SplitPerft.perft(position.copy(), testCase.depth(), split,
                        SplitPerft.cpuParallel(4));
                assertEquals(expected, parallel,
                        testCase.name() + " parallel split=" + split + " depth=" + testCase.depth());
            }
        }
    }

    /**
     * Verifies a checkmated root yields a zero node count for any positive depth.
     */
    private static void testEmptyFrontierIsZero() {
        Position mate = new Position("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3");
        for (int split = 0; split <= 3; split++) {
            assertEquals(0L, SplitPerft.perft(mate.copy(), 2, split, SplitPerft.CPU),
                    "fools-mate split=" + split);
        }
    }

    /**
     * Verifies depth zero counts the root itself for every split depth.
     */
    private static void testDepthZeroIsOne() {
        Position position = new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        for (int split = 0; split <= 2; split++) {
            assertEquals(1L, SplitPerft.perft(position.copy(), 0, split, SplitPerft.CPU),
                    "depth-zero split=" + split);
        }
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
