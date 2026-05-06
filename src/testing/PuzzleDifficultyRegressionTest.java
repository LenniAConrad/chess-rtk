package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertFalse;
import static testing.TestSupport.assertTrue;

import java.util.List;

import chess.core.Move;
import chess.core.Position;
import chess.puzzle.Difficulty;
import chess.puzzle.PieceIdentityTracker;
import chess.puzzle.Scorer;
import chess.puzzle.Goal;
import chess.uci.Analysis;

/**
 * Regression checks for puzzle difficulty estimation.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PuzzleDifficultyRegressionTest {

    /**
     * Simple mate-in-one position: {@code Qg7#}.
     */
    private static final String MATE_IN_ONE_FEN = "7k/8/5KQ1/8/8/8/8/8 w - - 0 1";

    /**
     * Promotion position with legal underpromotion choices.
     */
    private static final String UNDERPROMOTION_FEN = "7k/P7/1K6/8/8/8/8/8 w - - 0 1";

    /**
     * Direct mate-in-one that a material-only evaluator can rank poorly.
     */
    private static final String HIDDEN_MATE_IN_ONE_FEN =
            "7Q/5pp1/1p3qk1/r5p1/3p2B1/5P1P/6P1/6K1 w - - 0 43";

    /**
     * Standard starting position.
     */
    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Long engine PV where the actual puzzle key is the first move.
     */
    private static final String LONG_CONVERSION_FEN =
            "r3kb2/2p2p1p/p2p4/4pqNQ/2Pn4/2N1B3/Pr6/R3KR2 b - - 1 19";

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleDifficultyRegressionTest() {
        // utility
    }

    /**
     * Runs the regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testMateInOneIsVeryEasyWin();
        testHiddenMateInOneDoesNotBecomeHard();
        testDrawGoalInference();
        testUnderpromotionFeature();
        testVariationRecordsAreCounted();
        testUnprovenLongConversionIsDiscounted();
        testExplicitMainlineDepthIncreasesDifficulty();
        testLongerExplicitLineOutranksShorterLine();
        testLongestLineTailBreaksEqualBurden();
        testPostKeyBranchesIncreaseDifficulty();
        testMoreVariationOutranksSingleContinuation();
        testSpecialMoveHierarchy();
        testPieceIdentityFollowsRepeatedMover();
        testDifficultyTags();
        System.out.println("PuzzleDifficultyRegressionTest: all checks passed");
    }

    /**
     * Verifies a forcing mate-in-one is classified as an easy winning puzzle.
     */
    private static void testMateInOneIsVeryEasyWin() {
        Analysis analysis = new Analysis()
                .add("info depth 8 multipv 1 mate 1 pv g6g7")
                .add("info depth 8 multipv 2 cp 0 pv g6g8");
        Difficulty difficulty = scoreSingle(new Position(MATE_IN_ONE_FEN), analysis);
        assertEquals(Goal.WIN, difficulty.goal(), "mate-in-one goal");
        assertTrue(difficulty.score() <= 0.35, "mate-in-one score is easy");
        assertTrue(difficulty.features().featureNames().contains("mate_key"), "mate-in-one feature");
    }

    /**
     * Verifies a direct mate does not become hard just because cheap eval misses it.
     */
    private static void testHiddenMateInOneDoesNotBecomeHard() {
        Analysis analysis = new Analysis()
                .add("info depth 12 multipv 1 mate 1 pv h8h5")
                .add("info depth 12 multipv 2 cp -441 pv h8g7");
        Difficulty difficulty = scoreSingle(new Position(HIDDEN_MATE_IN_ONE_FEN), analysis);
        assertEquals(Goal.WIN, difficulty.goal(), "hidden mate-in-one goal");
        assertTrue(difficulty.rating() < 1500, "hidden mate-in-one rating stays below hard range");
        assertFalse("hard".equals(difficulty.label()) || "very_hard".equals(difficulty.label()),
                "hidden mate-in-one is not hard");
        assertTrue(difficulty.features().featureNames().contains("mate_key"), "hidden mate-in-one feature");
    }

    /**
     * Verifies drawing resources are inferred from PV1 holding while PV2 loses.
     */
    private static void testDrawGoalInference() {
        Analysis analysis = new Analysis()
                .add("info depth 10 multipv 1 cp 0 pv e2e4")
                .add("info depth 10 multipv 2 cp -500 pv d2d4");
        Difficulty difficulty = scoreSingle(new Position(START_FEN), analysis);
        assertEquals(Goal.DRAW, difficulty.goal(), "draw goal");
        assertTrue(difficulty.features().featureNames().contains("draw_resource"), "draw resource feature");
        assertTrue(difficulty.tags().contains("META: puzzle_goal=draw"), "draw goal tag");
    }

    /**
     * Verifies underpromotion is surfaced as a difficulty feature.
     */
    private static void testUnderpromotionFeature() {
        Analysis analysis = new Analysis()
                .add("info depth 10 multipv 1 cp 500 pv a7a8n")
                .add("info depth 10 multipv 2 cp 0 pv a7a8q");
        Difficulty difficulty = scoreSingle(new Position(UNDERPROMOTION_FEN), analysis);
        assertTrue(difficulty.features().featureNames().contains("underpromotion"), "underpromotion feature");
    }

    /**
     * Verifies explicit puzzle variation records are surfaced in features and tags.
     */
    private static void testVariationRecordsAreCounted() {
        Position root = new Position(START_FEN);
        Analysis analysis = new Analysis()
                .add("info depth 10 multipv 1 cp 500 pv e2e4 e7e5")
                .add("info depth 10 multipv 2 cp 420 pv d2d4 d7d5");
        Difficulty difficulty = scoreTree(root, analysis,
                new Scorer.PuzzleTreeSummary(4, 2, 1, 2, 0.20, 1.50, Math.log1p(1.0), 2, 2, 3, 0, 0, 0.25,
                        3, 0, 0, 0));
        assertEquals(2, difficulty.features().variationCount(), "root variation count");
        assertEquals(4, difficulty.features().recordVariationCount(), "tree node count includes root");
        assertEquals(1, difficulty.features().branchPointCount(), "branch point count");
        assertTrue(difficulty.features().featureNames().contains("branching"), "branching feature");
        assertTrue(difficulty.tags().contains("META: puzzle_variations=2"), "variation tag");
        assertTrue(difficulty.tags().contains("META: puzzle_branch_points=1"), "branch point tag");
    }

    /**
     * Verifies a long non-mate PV is not treated as a full multi-move puzzle when
     * no explicit continuation tree supports it.
     */
    private static void testUnprovenLongConversionIsDiscounted() {
        Difficulty difficulty = scoreSingle(new Position(LONG_CONVERSION_FEN), longConversionAnalysis());
        assertTrue(difficulty.rating() < 2200, "unproven long conversion rating is discounted");
        assertFalse(difficulty.features().featureNames().contains("long_line"),
                "unproven long conversion is not tagged long_line");
    }

    /**
     * Verifies an explicit multi-move puzzle line counts even when it has no side
     * branches. A forced single continuation should be harder than a bare engine PV,
     * but less branchy than a real variation tree.
     */
    private static void testExplicitMainlineDepthIncreasesDifficulty() {
        Position root = new Position(LONG_CONVERSION_FEN);
        Difficulty baseline = scoreSingle(root, longConversionAnalysis());
        Difficulty explicitLine = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(4, 1, 0, 4, 0.25, 1.08, 0.0, 3, 3, 4, 0, 0, 0.25,
                        3, 0, 0, 0));

        assertTrue(explicitLine.rating() > baseline.rating(), "explicit line depth increases rating");
        assertTrue(explicitLine.features().featureNames().contains("multi_move"), "explicit line is tagged multi_move");
    }

    /**
     * Verifies longer explicit continuations outrank shorter continuations when the
     * root position is otherwise identical.
     */
    private static void testLongerExplicitLineOutranksShorterLine() {
        Position root = new Position(LONG_CONVERSION_FEN);
        Difficulty shortLine = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(2, 1, 0, 2, 0.25, 0.50, 0.0, 2, 2, 2, 0, 0, 0.50,
                        2, 0, 0, 0));
        Difficulty longLine = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(7, 1, 0, 7, 0.25, 1.59, 0.0, 3, 3, 5, 0, 0, 0.20,
                        5, 0, 0, 0));

        assertTrue(longLine.features().rawScore() > shortLine.features().rawScore(),
                "longer explicit line has higher raw score");
        assertTrue(longLine.rating() > shortLine.rating(), "longer explicit line has higher rating");
    }

    /**
     * Verifies the extreme tree tail still rewards the longest explicit line when
     * harmonic continuation burden is otherwise identical.
     */
    private static void testLongestLineTailBreaksEqualBurden() {
        Position root = new Position(LONG_CONVERSION_FEN);
        Difficulty longestLine = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(4, 1, 0, 4, 0.25, 1.08, 0.0, 3, 3, 4, 0, 0, 0.25,
                        3, 0, 0, 0));
        Difficulty sameBurdenShorterBranches = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(4, 1, 0, 2, 0.25, 1.08, 0.0, 3, 3, 4, 0, 0, 0.25,
                        3, 0, 0, 0));

        assertTrue(longestLine.rating() > sameBurdenShorterBranches.rating(),
                "longest explicit line receives a higher rating when burden is otherwise equal");
    }

    /**
     * Verifies real branches after the key move raise difficulty more than duplicate
     * root alternatives.
     */
    private static void testPostKeyBranchesIncreaseDifficulty() {
        Position root = new Position(LONG_CONVERSION_FEN);
        Difficulty explicitLine = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(4, 1, 0, 4, 0.25, 1.08, 0.0, 3, 3, 4, 0, 0, 0.25,
                        3, 0, 0, 0));
        Difficulty branched = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(5, 1, 1, 4, 0.25, 1.58, Math.log1p(1.0), 3, 3, 4, 0, 0, 0.25,
                        4, 0, 0, 0));
        assertTrue(branched.rating() > explicitLine.rating(), "post-key branches increase rating");
        assertTrue(branched.features().featureNames().contains("branching"), "post-key branch feature");
    }

    /**
     * Verifies extra explicit variations outrank a single forced continuation.
     */
    private static void testMoreVariationOutranksSingleContinuation() {
        Position root = new Position(LONG_CONVERSION_FEN);
        Difficulty forced = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(4, 1, 0, 4, 0.25, 1.08, 0.0, 3, 3, 4, 0, 0, 0.25,
                        3, 0, 0, 0));
        Difficulty varied = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(8, 4, 2, 4, 0.25, 1.08, Math.log1p(3.0), 3, 3, 5, 0, 0, 0.20,
                        4, 0, 0, 0));

        assertTrue(varied.features().rawScore() > forced.features().rawScore(),
                "explicit variations have higher raw score");
        assertTrue(varied.rating() > forced.rating(), "explicit variations have higher rating");
    }

    /**
     * Verifies rare solution moves use the intended importance order:
     * underpromotion, then en passant, then castling.
     */
    private static void testSpecialMoveHierarchy() {
        Position root = new Position(LONG_CONVERSION_FEN);
        Difficulty ordinary = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(3, 1, 0, 3, 0.25, 0.83, 0.0, 2, 2, 3, 0, 0, 0.34,
                        2, 0, 0, 0));
        Difficulty castling = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(3, 1, 0, 3, 0.25, 0.83, 0.0, 2, 2, 3, 0, 0, 0.34,
                        2, 0, 0, 1));
        Difficulty enPassant = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(3, 1, 0, 3, 0.25, 0.83, 0.0, 2, 2, 3, 0, 0, 0.34,
                        2, 0, 1, 0));
        Difficulty underpromotion = scoreTree(root, longConversionAnalysis(),
                new Scorer.PuzzleTreeSummary(3, 1, 0, 3, 0.25, 0.83, 0.0, 2, 2, 3, 0, 0, 0.34,
                        2, 1, 0, 0));

        assertTrue(castling.features().rawScore() > ordinary.features().rawScore(),
                "castling raises raw score");
        assertTrue(enPassant.features().rawScore() > castling.features().rawScore(),
                "en passant outranks castling");
        assertTrue(underpromotion.features().rawScore() > enPassant.features().rawScore(),
                "underpromotion outranks en passant");
        assertTrue(underpromotion.rating() > enPassant.rating() && enPassant.rating() > castling.rating(),
                "special move rating order is preserved");
    }

    /**
     * Verifies stable piece identities follow a piece after it moves once, so a
     * repeated mover is not counted as two different solver pieces.
     */
    private static void testPieceIdentityFollowsRepeatedMover() {
        Position root = new Position("8/8/8/8/8/8/8/R3K2k w - - 0 1");
        PieceIdentityTracker identities = PieceIdentityTracker.from(root);

        short firstRookMove = Move.parse("a1a2");
        long firstIdentity = identities.movingIdentity(root, firstRookMove);
        Position afterFirst = root.copy().play(firstRookMove);
        PieceIdentityTracker afterFirstIdentities = identities.after(root, firstRookMove, afterFirst);

        short blackReply = Move.parse("h1g1");
        Position afterReply = afterFirst.copy().play(blackReply);
        PieceIdentityTracker afterReplyIdentities = afterFirstIdentities.after(afterFirst, blackReply, afterReply);

        short secondRookMove = Move.parse("a2a3");
        long secondIdentity = afterReplyIdentities.movingIdentity(afterReply, secondRookMove);

        assertTrue(firstIdentity != PieceIdentityTracker.NO_IDENTITY, "tracked first rook identity");
        assertEquals(firstIdentity, secondIdentity, "same rook keeps identity after moving");
    }

    /**
     * Verifies stable META tags are emitted for downstream puzzle workflows.
     */
    private static void testDifficultyTags() {
        Analysis analysis = new Analysis()
                .add("info depth 10 multipv 1 cp 500 pv e2e4")
                .add("info depth 10 multipv 2 cp 0 pv d2d4");
        Difficulty difficulty = scoreSingle(new Position(START_FEN), analysis);
        List<String> tags = difficulty.tags();
        assertTrue(tags.contains("META: puzzle_goal=win"), "puzzle goal tag");
        assertTrue(tags.stream().anyMatch(tag -> tag.startsWith("META: puzzle_rating=")), "puzzle rating tag");
        assertTrue(tags.stream().anyMatch(tag -> tag.startsWith("META: puzzle_difficulty_score=")),
                "puzzle difficulty score tag");
        assertTrue(tags.stream().anyMatch(tag -> tag.startsWith("META: puzzle_solution_plies=")),
                "puzzle solution plies tag");
    }

    /**
     * Scores a single solver position without explicit continuation evidence.
     *
     * @param root root position
     * @param analysis root analysis
     * @return single-node difficulty
     */
    private static Difficulty scoreSingle(Position root, Analysis analysis) {
        Scorer.NodeScore node = Scorer.scoreNode(root, analysis);
        return Scorer.score(node, Scorer.PuzzleTreeSummary.single(node));
    }

    /**
     * Scores a root position with an explicit continuation summary.
     *
     * @param root root position
     * @param analysis root analysis
     * @param tree explicit tree summary
     * @return tree-aware difficulty
     */
    private static Difficulty scoreTree(Position root, Analysis analysis, Scorer.PuzzleTreeSummary tree) {
        return Scorer.score(Scorer.scoreNode(root, analysis), tree);
    }

    /**
     * Returns a compact root analysis for {@link #LONG_CONVERSION_FEN}.
     */
    private static Analysis longConversionAnalysis() {
        return new Analysis()
                .add("info depth 18 multipv 1 cp 617 pv "
                        + "d4c2 e1e2 c2a1 e3d2 b2d2 e2e1 a1c2 e1d2 f5f1 d2c2 "
                        + "f1f5 c3e4 h7h6 h5f3 f5f3 g5f3 f8g7 c2c1 g7h8 e4c3 c7c6 c3e4")
                .add("info depth 18 multipv 2 cp -350 pv f5g6 h5g6 f7g6 e3d4 e5d4 c3d5")
                .add("info depth 18 multipv 3 cp -480 pv b2g2 e3d4 g2g5");
    }
}
