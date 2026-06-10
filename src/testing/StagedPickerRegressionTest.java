package testing;

import static testing.TestSupport.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import chess.core.Move;
import chess.core.MoveGenerator;
import chess.core.MoveList;
import chess.core.Position;
import chess.engine.AlphaBeta;

/**
 * Set-equivalence verification for the staged move picker's building blocks
 * and emission contract.
 *
 * <p>
 * The staged picker replaces eager fully-legal generation with pseudo-legal
 * staged generation plus lazy per-move validation, so its correctness reduces
 * to three machine-checkable set equalities over a diverse position corpus:
 * {@code isPseudoLegal} accepts exactly the generator's output over the whole
 * 16-bit move space, the tactical and quiet generators partition the full
 * pseudo-legal list, and the picker emits exactly the legal move set without
 * duplicates under arbitrary transposition-move and killer injections.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */

public final class StagedPickerRegressionTest {

    /**
     * Deterministic seed for corpus playouts and move injections.
     */
    private static final long RANDOM_SEED = 20260611L;

    /**
     * Random playouts per seed position.
     */
    private static final int PLAYOUTS_PER_SEED = 6;

    /**
     * Maximum plies per random playout.
     */
    private static final int MAX_PLAYOUT_PLIES = 40;

    /**
     * Upper bound on corpus positions receiving the full 65536-move
     * {@code isPseudoLegal} sweep; remaining positions get a sampled sweep.
     */
    private static final int FULL_SWEEP_POSITIONS = 1500;

    /**
     * Random candidate moves tested per position outside the full sweep.
     */
    private static final int SAMPLED_SWEEP_CANDIDATES = 512;

    /**
     * Seed positions chosen for tactical, castling, en-passant, promotion,
     * pin, near-stalemate, and Chess960 coverage.
     */
    private static final String[] SEED_FENS = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            "k7/8/8/3Pp3/8/8/8/7K w - e6 0 1",
            "7k/P6P/8/8/8/8/p6p/7K w - - 0 1",
            "8/3p4/8/2P1P3/8/8/8/k1K5 b - - 0 1",
            "4k3/4r3/8/8/4B3/8/4R3/4K3 w - - 0 1",
            "7k/5Q2/8/8/8/8/8/K7 w - - 0 1",
            "8/8/8/8/8/5k2/7p/7K b - - 0 1",
            "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1",
            "bqnnrkrb/pppppppp/8/8/8/8/PPPPPPPP/BQNNRKRB w GEge - 0 1",
            "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1",
            "4k3/8/8/3p4/8/8/8/4K3 w - d6 0 1"
    };

    /**
     * Prevents instantiation of this utility class.
     */
    private StagedPickerRegressionTest() {
        // utility
    }

    /**
     * Runs all checks.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        List<Position> corpus = buildCorpus();
        assertTrue(corpus.size() > 1000, "corpus large enough: " + corpus.size());
        testQuietTacticalPartition(corpus);
        testIsPseudoLegalMatchesGenerator(corpus);
        testStagedEmissionMatchesLegalMoves(corpus);
        System.out.println("StagedPickerRegressionTest: all checks passed over "
                + corpus.size() + " positions");
    }

    /**
     * Builds a deterministic corpus by random legal playouts from the seed
     * positions, keeping every intermediate position (including in-check
     * ones, which the pseudo-legal sweeps must also satisfy).
     *
     * @return corpus positions
     */
    private static List<Position> buildCorpus() {
        Random random = new Random(RANDOM_SEED);
        List<Position> corpus = new ArrayList<>();
        for (String fen : SEED_FENS) {
            for (int playout = 0; playout < PLAYOUTS_PER_SEED; playout++) {
                Position position = Position.fromFen(fen);
                corpus.add(position.copy());
                for (int plyCount = 0; plyCount < MAX_PLAYOUT_PLIES; plyCount++) {
                    MoveList legal = position.legalMoves();
                    if (legal.isEmpty()) {
                        break;
                    }
                    short move = legal.raw(random.nextInt(legal.size()));
                    position = position.play(move);
                    corpus.add(position.copy());
                }
            }
        }
        return corpus;
    }

    /**
     * Verifies the tactical and quiet pseudo-legal generators are disjoint and
     * union to the full pseudo-legal generator on every corpus position.
     *
     * @param corpus corpus positions
     */
    private static void testQuietTacticalPartition(List<Position> corpus) {
        MoveList tacticals = new MoveList();
        MoveList quiets = new MoveList();
        MoveList all = new MoveList();
        for (Position position : corpus) {
            MoveGenerator.generatePseudoLegalTacticals(position, tacticals);
            MoveGenerator.generatePseudoLegalQuiets(position, quiets);
            MoveGenerator.generatePseudoLegalMoves(position, all);
            Set<Short> union = new HashSet<>();
            for (int i = 0; i < tacticals.size(); i++) {
                assertTrue(union.add(tacticals.raw(i)),
                        "duplicate tactical move in " + position);
            }
            for (int i = 0; i < quiets.size(); i++) {
                assertTrue(union.add(quiets.raw(i)),
                        "quiet/tactical overlap or duplicate quiet in " + position);
            }
            assertEquals(all.size(), tacticals.size() + quiets.size(),
                    "tactical+quiet count equals full generator in " + position);
            for (int i = 0; i < all.size(); i++) {
                assertTrue(union.contains(all.raw(i)),
                        "generated move missing from partition in " + position);
            }
        }
    }

    /**
     * Verifies {@code isPseudoLegal} accepts exactly the full generator's
     * output: a complete 65536-value sweep on a prefix of the corpus and a
     * sampled sweep (plus all generated moves) on the rest.
     *
     * @param corpus corpus positions
     */
    private static void testIsPseudoLegalMatchesGenerator(List<Position> corpus) {
        Random random = new Random(RANDOM_SEED + 1);
        boolean[] generated = new boolean[1 << 16];
        MoveList moves = new MoveList();
        for (int index = 0; index < corpus.size(); index++) {
            Position position = corpus.get(index);
            MoveGenerator.generatePseudoLegalMoves(position, moves);
            for (int i = 0; i < moves.size(); i++) {
                generated[moves.raw(i) & 0xFFFF] = true;
            }
            if (index < FULL_SWEEP_POSITIONS) {
                for (int candidate = 0; candidate < (1 << 16); candidate++) {
                    short move = (short) candidate;
                    if (MoveGenerator.isPseudoLegal(position, move) != generated[candidate]) {
                        throw new AssertionError("isPseudoLegal mismatch for move " + candidate
                                + " in " + position);
                    }
                }
            } else {
                for (int i = 0; i < moves.size(); i++) {
                    assertTrue(MoveGenerator.isPseudoLegal(position, moves.raw(i)),
                            "generated move rejected in " + position);
                }
                for (int i = 0; i < SAMPLED_SWEEP_CANDIDATES; i++) {
                    int candidate = random.nextInt(1 << 16);
                    if (MoveGenerator.isPseudoLegal(position, (short) candidate) != generated[candidate]) {
                        throw new AssertionError("isPseudoLegal sampled mismatch for move "
                                + candidate + " in " + position);
                    }
                }
            }
            for (int i = 0; i < moves.size(); i++) {
                generated[moves.raw(i) & 0xFFFF] = false;
            }
        }
    }

    /**
     * Verifies the staged picker emits exactly the legal move set, without
     * duplicates, on every non-check corpus position, under empty, legal,
     * garbage, and duplicated transposition-move and killer injections, and
     * that a legal transposition move is emitted first.
     *
     * @param corpus corpus positions
     */
    private static void testStagedEmissionMatchesLegalMoves(List<Position> corpus) {
        Random random = new Random(RANDOM_SEED + 2);
        for (Position position : corpus) {
            if (position.inCheck()) {
                continue;
            }
            MoveList legal = position.legalMoves();
            Set<Short> expected = new HashSet<>();
            for (int i = 0; i < legal.size(); i++) {
                expected.add(legal.raw(i));
            }
            short legalMove = legal.isEmpty() ? Move.NO_MOVE : legal.raw(random.nextInt(legal.size()));
            short otherLegal = legal.isEmpty() ? Move.NO_MOVE : legal.raw(random.nextInt(legal.size()));
            short garbage = (short) random.nextInt(1 << 16);
            checkEmission(position, expected, Move.NO_MOVE, Move.NO_MOVE, Move.NO_MOVE);
            checkEmission(position, expected, legalMove, otherLegal, garbage);
            checkEmission(position, expected, garbage, otherLegal, otherLegal);
        }
    }

    /**
     * Asserts one picker emission equals the expected legal move set.
     *
     * @param position non-check position
     * @param expected legal move set
     * @param ttMove transposition move to inject
     * @param killer0 primary killer to inject
     * @param killer1 secondary killer to inject
     */
    private static void checkEmission(
            Position position,
            Set<Short> expected,
            short ttMove,
            short killer0,
            short killer1) {
        short[] emitted = AlphaBeta.stagedMoveEmission(position, ttMove, killer0, killer1);
        Set<Short> seen = new HashSet<>();
        for (short move : emitted) {
            assertTrue(seen.add(move), "duplicate emission " + move + " in " + position);
            assertTrue(expected.contains(move), "illegal emission " + move + " in " + position);
        }
        assertEquals(expected.size(), seen.size(), "emission count in " + position);
        if (ttMove != Move.NO_MOVE && expected.contains(ttMove)) {
            assertTrue(emitted.length > 0 && emitted[0] == ttMove,
                    "legal transposition move emitted first in " + position);
        }
    }
}
