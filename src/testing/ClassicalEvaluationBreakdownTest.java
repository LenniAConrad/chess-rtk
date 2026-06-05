package testing;

import chess.classical.Wdl;
import chess.core.MoveList;
import chess.core.Position;
import chess.struct.Game;
import java.util.Random;

/**
 * Regression test guarding the classical evaluation breakdown.
 *
 * <p>{@link Wdl#evaluateWhiteBreakdown(Position)} is a per-term decomposition of
 * the scalar {@link Wdl#evaluateWhiteCentipawns(Position)} used by the workbench
 * Evaluator view. This test proves the decomposition sums bit-identically to the
 * scalar evaluation across hand-picked and randomly walked positions, so the
 * inspection view can never silently disagree with the engine's real score.</p>
 */
public final class ClassicalEvaluationBreakdownTest {

    /**
     * Hand-picked positions exercising every evaluation term.
     */
    private static final String[] FENS = {
        Game.STANDARD_START_FEN,
        "r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3",
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "rnbq1rk1/pp2bppp/2p2n2/3p4/2PP4/2N1PN2/PP3PPP/R1BQKB1R w KQ - 0 7",
        "8/8/8/4k3/8/4K3/4P3/8 w - - 0 1",
        "6k1/5ppp/8/8/8/8/5PPP/6K1 w - - 0 1",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 1",
        "2kr3r/ppp2ppp/2n5/8/8/2N5/PPP2PPP/2KR3R w - - 0 1",
        "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
        "8/8/8/3k4/8/3K4/8/8 w - - 0 1",
        "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1",
    };

    /**
     * Prevents instantiation.
     */
    private ClassicalEvaluationBreakdownTest() {
    }

    /**
     * Runs all breakdown equality checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        int checks = 0;
        for (String fen : FENS) {
            checks += check(new Position(fen));
        }
        Random rng = new Random(20260603L);
        for (int walk = 0; walk < 400; walk++) {
            Position pos = new Position(Game.STANDARD_START_FEN);
            for (int ply = 0; ply < 50; ply++) {
                checks += check(pos);
                MoveList legal = pos.legalMoves();
                if (legal.isEmpty()) {
                    break;
                }
                pos.play(legal.get(rng.nextInt(legal.size())));
            }
        }
        System.out.println("ClassicalEvaluationBreakdownTest: all " + checks + " checks passed");
    }

    /**
     * Verifies the breakdown matches the scalar evaluators for one position.
     *
     * @param pos position to check
     * @return 1 (one check performed)
     */
    private static int check(Position pos) {
        int whiteScalar = Wdl.evaluateWhiteCentipawns(pos);
        Wdl.Breakdown breakdown = Wdl.evaluateWhiteBreakdown(pos);
        if (breakdown.whiteTotal() != whiteScalar) {
            throw new AssertionError("breakdown total " + breakdown.whiteTotal()
                    + " != scalar " + whiteScalar + " for " + pos);
        }
        int stmScalar = Wdl.evaluateStmCentipawns(pos);
        int expectedStm = pos.isWhiteToMove() ? whiteScalar : -whiteScalar;
        if (stmScalar != expectedStm) {
            throw new AssertionError("stm scalar " + stmScalar + " != " + expectedStm + " for " + pos);
        }
        if (breakdown.stmTotal() != stmScalar) {
            throw new AssertionError("breakdown stm " + breakdown.stmTotal()
                    + " != scalar stm " + stmScalar + " for " + pos);
        }
        return 1;
    }
}
