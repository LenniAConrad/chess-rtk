package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;

import java.util.ArrayList;
import java.util.List;

import chess.describe.ClassicalPositionDescriptionGenerator;
import chess.describe.PositionDescriptionDetail;
import chess.describe.PositionDescriptionInput;
import chess.describe.PositionDescriptionVerifier;
import chess.describe.PositionDescriptionVerifier.Verification;

/**
 * Regression checks for position-description grounding verification.
 */
public final class DescribeVerifierRegressionTest {

    /**
     * Standard chess starting position.
     */
    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Utility class; prevent instantiation.
     */
    private DescribeVerifierRegressionTest() {
        // utility
    }

    /**
     * Runs every verifier regression check.
     *
     * @param args ignored command-line arguments
     */
    public static void main(String[] args) {
        testClassicalOutputIsGrounded();
        testIllegalSanFallsBackToClassicalText();
        testSideAndEvaluationContradictionsAreFlagged();
        testMaterialSurplusClaimsAreFlaggedAndBacked();
        testOnlyMoveAndOpeningClaimsAreFlagged();
        testGroundedOnlyMoveAndOpeningClaimsPass();
        System.out.println("DescribeVerifierRegressionTest: all checks passed");
    }

    /**
     * Verifies existing deterministic classical output passes the grounding gate.
     */
    private static void testClassicalOutputIsGrounded() {
        PositionDescriptionInput input = PositionDescriptionInput.fromFen(START_FEN);
        ClassicalPositionDescriptionGenerator generator = new ClassicalPositionDescriptionGenerator();
        String text = generator.generate(input, PositionDescriptionDetail.NORMAL);
        Verification verification = PositionDescriptionVerifier.verify(input, text);
        assertTrue(verification.grounded(), "classical position description is grounded");
    }

    /**
     * Verifies a hallucinated move is rejected and replaced by classical text.
     */
    private static void testIllegalSanFallsBackToClassicalText() {
        PositionDescriptionInput input = PositionDescriptionInput.fromFen(START_FEN);
        String fallback = new ClassicalPositionDescriptionGenerator().generate(input, PositionDescriptionDetail.BRIEF);
        String hallucinated = "White is to move and Qh5 is the only serious candidate.";
        Verification verification = PositionDescriptionVerifier.verify(input, hallucinated);
        assertTrue(!verification.grounded(), "hallucinated SAN is rejected");
        assertTrue(verification.violations().stream().anyMatch(v -> "san".equals(v.kind())),
                "hallucinated SAN violation kind");
        assertEquals(fallback, PositionDescriptionVerifier.groundedOrFallback(input, hallucinated, fallback),
                "hallucinated text falls back to classical");
    }

    /**
     * Verifies side-to-move and evaluation contradictions are rejected.
     */
    private static void testSideAndEvaluationContradictionsAreFlagged() {
        PositionDescriptionInput input = PositionDescriptionInput.fromFen(
                "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1");
        String contradicted = "Black is to move and Black is winning.";
        Verification verification = PositionDescriptionVerifier.verify(input, contradicted);
        assertTrue(!verification.grounded(), "contradictory text is rejected");
        assertTrue(verification.violations().stream().anyMatch(v -> "side_to_move".equals(v.kind())),
                "side-to-move violation kind");
        assertTrue(verification.violations().stream().anyMatch(v -> "evaluation".equals(v.kind())),
                "evaluation violation kind");
    }

    /**
     * Verifies concrete material-surplus claims require matching piece counts.
     */
    private static void testMaterialSurplusClaimsAreFlaggedAndBacked() {
        PositionDescriptionInput start = PositionDescriptionInput.fromFen(START_FEN);
        String falseClaims = "White is a rook up. Black has an extra pawn. White's extra bishop matters.";
        Verification falseVerification = PositionDescriptionVerifier.verify(start, falseClaims);
        assertTrue(!falseVerification.grounded(), "unsupported material claims are rejected");
        assertTrue(falseVerification.violations().stream().filter(v -> "material".equals(v.kind())).count() >= 3,
                "unsupported material claims produce material violations");

        PositionDescriptionInput whiteRookUp = PositionDescriptionInput.fromFen(
                "4k3/8/8/8/8/8/8/R3K3 w - - 0 1");
        assertTrue(PositionDescriptionVerifier.verify(whiteRookUp, "White is a rook up.").grounded(),
                "white rook-surplus claim is grounded");

        PositionDescriptionInput whitePawnUp = PositionDescriptionInput.fromFen(
                "4k3/8/8/8/8/8/P7/4K3 w - - 0 1");
        assertTrue(PositionDescriptionVerifier.verify(whitePawnUp, "White has an extra pawn.").grounded(),
                "white pawn-surplus claim is grounded");

        PositionDescriptionInput blackPieceUp = PositionDescriptionInput.fromFen(
                "4k3/8/8/8/8/8/8/2n1K3 b - - 0 1");
        assertTrue(PositionDescriptionVerifier.verify(blackPieceUp, "Black has an extra knight.").grounded(),
                "black knight-surplus claim is grounded");
        assertTrue(PositionDescriptionVerifier.verify(blackPieceUp, "Black is a piece up.").grounded(),
                "black minor-piece-surplus claim is grounded");
    }

    /**
     * Verifies unproved only-move and named-opening claims are rejected.
     */
    private static void testOnlyMoveAndOpeningClaimsAreFlagged() {
        PositionDescriptionInput input = PositionDescriptionInput.fromFen(START_FEN);
        String hallucinated = "White has the only move in the Najdorf.";
        Verification verification = PositionDescriptionVerifier.verify(input, hallucinated);
        assertTrue(!verification.grounded(), "unproved only-move and opening claim is rejected");
        assertTrue(verification.violations().stream().anyMatch(v -> "only_move".equals(v.kind())),
                "only-move violation kind");
        assertTrue(verification.violations().stream().anyMatch(v -> "opening".equals(v.kind())),
                "opening violation kind");
    }

    /**
     * Verifies the same claim shapes pass when the structured facts back them.
     */
    private static void testGroundedOnlyMoveAndOpeningClaimsPass() {
        PositionDescriptionInput onlyMove = PositionDescriptionInput.fromFen(
                "8/8/8/8/8/5k2/7q/7K w - - 0 1");
        Verification onlyMoveVerification = PositionDescriptionVerifier.verify(onlyMove,
                "White's only move is Kxh2.");
        assertTrue(onlyMoveVerification.grounded(), "legal single-move claim is grounded");

        PositionDescriptionInput opening = withTag(PositionDescriptionInput.fromFen(START_FEN),
                "OPENING: name=\"Sicilian Defense: Najdorf Variation\"");
        Verification openingVerification = PositionDescriptionVerifier.verify(opening,
                "The Najdorf is the named opening.");
        assertTrue(openingVerification.grounded(), "opening-name fact backs opening claim");
    }

    /**
     * Returns a copy of a description input with one extra source tag.
     *
     * @param input source input
     * @param tag extra tag
     * @return copied input
     */
    private static PositionDescriptionInput withTag(PositionDescriptionInput input, String tag) {
        List<String> tags = new ArrayList<>(input.tags());
        tags.add(tag);
        return new PositionDescriptionInput(
                input.fen(),
                input.sideToMove(),
                input.status(),
                input.inCheck(),
                input.phase(),
                input.material(),
                input.moves(),
                input.evaluation(),
                tags,
                input.threats(),
                input.candidates());
    }
}
