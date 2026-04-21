package testing;

import java.util.List;

import chess.core.Position;
import chess.tag2.Tagging;

/**
 * Small no-framework regression checks for the second heuristic tagger.
 */
public final class TaggingRegressionTest {

     /**
     * Creates a new tagging regression test instance.
     */
     private TaggingRegressionTest() {
        // utility
    }

     /**
     * Handles main.
     * @param args args
     */
     public static void main(String[] args) {
        testStartPosition();
        testBackRankMate();
        testPin();
        testHangingPiece();
        testPawnStructure();
        System.out.println("TaggingRegressionTest: all checks passed");
    }

     /**
     * Handles test start position.
     */
     private static void testStartPosition() {
        List<String> tags = Tagging.tags(new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
        assertContains(tags, "META: to_move=white");
        assertContains(tags, "META: phase=opening");
        assertContains(tags, "FACT: status=normal");
        assertContains(tags, "FACT: castling=KQkq");
        assertContains(tags, "MOVE: legal=20");
        assertContains(tags, "MATERIAL: balance=equal");
    }

     /**
     * Handles test back rank mate.
     */
     private static void testBackRankMate() {
        List<String> tags = Tagging.tags(new Position("4R1k1/5ppp/8/8/8/8/8/6K1 b - - 0 1"));
        assertContains(tags, "FACT: status=checkmate");
        assertContains(tags, "CHECKMATE: winner=white");
        assertContains(tags, "CHECKMATE: pattern=back_rank_mate");
        assertContains(tags, "CHECKMATE: delivery=rook");
    }

     /**
     * Handles test pin.
     */
     private static void testPin() {
        List<String> tags = Tagging.tags(new Position("4r1k1/8/8/8/8/8/4B3/4K3 w - - 0 1"));
        assertContains(tags, "TACTIC: pin side=white piece=bishop square=e2 by=black_rook@e8");
    }

     /**
     * Handles test hanging piece.
     */
     private static void testHangingPiece() {
        List<String> tags = Tagging.tags(new Position("4k3/8/8/8/8/8/4q3/4R1K1 w - - 0 1"));
        assertContains(tags, "TACTIC: hanging side=black piece=queen square=e2");
    }

     /**
     * Handles test pawn structure.
     */
     private static void testPawnStructure() {
        List<String> tags = Tagging.tags(new Position("4k3/4P3/8/8/8/P7/P7/4K3 w - - 0 1"));
        assertContains(tags, "PAWN: doubled side=white file=a count=2");
        assertContains(tags, "PAWN: isolated side=white file=a");
        assertContains(tags, "PAWN: passed side=white square=e7");
        assertContains(tags, "PAWN: promotion_ready side=white square=e7");
    }

     /**
     * Handles assert contains.
     * @param tags tags
     * @param expected expected
     */
     private static void assertContains(List<String> tags, String expected) {
        if (!tags.contains(expected)) {
            throw new AssertionError("missing tag: " + expected + "\nactual: " + tags);
        }
    }
}
