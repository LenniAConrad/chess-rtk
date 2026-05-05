package testing;

import java.util.ArrayList;
import java.util.List;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.tag.Delta;
import chess.tag.Sort;
import chess.tag.Generator;

/**
 * Small no-framework regression checks for canonical position tagging.
 */
@SuppressWarnings("java:S1192")
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
        testOpeningEcoTagsAndInheritance();
        testBackRankMate();
        testDoubleCheckMatePattern();
        testCornerAndSupportMatePatterns();
        testSmotheredMatePattern();
        testPin();
        testStaticSkewer();
        testHangingPiece();
        testHangingPawnsAreNotTacticalNoise();
        testQuietMinorOnlyPositionHasNoTactics();
        testPawnStructure();
        testConnectedPassedPawnsDifferentRanks();
        testBlackConnectedPassedPawnsDifferentRanks();
        testRequestedStrategicTagContracts();
        testOutpostRequiresNoEnemyPawnAttack();
        testMovedKingStillEmitsCastledNo();
        testPawnMajorityRegions();
        testEnPassantFactDoesNotCreatePromotionTactics();
        testForcedOnlyMoveTags();
        testMaterialImbalanceDelta();
        testMoveSpecificTacticsDoNotCollapseInDelta();
        testOppositeBishopsRequireSingleBishopEach();
        testLichessHangingPieceTags();
        testLichessCheckingPinTags();
        testLichessMutualPinTags();
        testLichessForkMoveTag();
        testPawnForkMoveTag();
        testLichessSkewerMoveTag();
        testSkewerRejectsPawnBehind();
        testStaticSkewerRejectsLowValueFrontTarget();
        testDefendedAttackedPieceIsNotHanging();
        testLichessDiscoveredAttackMoveTag();
        testQuietDiscoveredAttackAlternativesSuppressed();
        testLichessMateInOneMoveTag();
        testMultipleMateInOneMoves();
        testLegalPromotionMoveTags();
        testBlackPromotionMoveTags();
        testPromotionTagsRequireLegalPromotion();
        testGeneratedTacticalTagsHaveStableFieldsAndIdentities();
        testCanonicalOutputShapeAcrossRepresentativePositions();
        System.out.println("TaggingRegressionTest: all checks passed");
    }

     /**
     * Handles test start position.
     */
    private static void testStartPosition() {
        List<String> tags = Generator.tags(new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
        assertContains(tags, "META: to_move=white");
        assertContains(tags, "META: phase=opening");
        assertContains(tags, "FACT: status=normal");
        assertContains(tags, "FACT: castle_rights=KQkq");
        assertContains(tags, "MATERIAL: balance=equal");
        assertContains(tags, "MOVE: legal=20");
        assertContains(tags, "MOVE: quiet=20");
        assertNoPrefix(tags, "TACTIC:");
        assertNoPrefix(tags, "CHECKMATE:");
        assertNoPrefix(tags, "ENDGAME:");
        assertNotContains(tags, "MATERIAL: imbalance=opposite_color_bishops");
        assertPieceTierFields(tags);
    }

     /**
     * Verifies exact ECO tags and inherited opening context.
     */
     private static void testOpeningEcoTagsAndInheritance() {
        List<String> afterE4 = Generator.tags(new Position(
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"));
        assertContains(afterE4, "OPENING: eco=B00");
        assertContains(afterE4, "OPENING: name=\"King's Pawn Game\"");

        List<String> inherited = Generator.inheritOpeningTags(List.of("META: phase=middlegame"), afterE4);
        assertContains(inherited, "OPENING: eco=B00");
        assertContains(inherited, "OPENING: name=\"King's Pawn Game\"");
        assertCanonicalTags(inherited);

        List<String> afterKnights = Generator.tags(new Position(
                "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"));
        List<String> exactWins = Generator.inheritOpeningTags(afterKnights, afterE4);
        assertContains(exactWins, "OPENING: eco=C44");
        assertContains(exactWins, "OPENING: name=\"King's Knight Opening: Normal Variation\"");
        assertNotContains(exactWins, "OPENING: eco=B00");
        assertCanonicalTags(exactWins);
    }

     /**
     * Handles test back rank mate.
     */
    private static void testBackRankMate() {
        List<String> tags = Generator.tags(new Position("4R1k1/5ppp/8/8/8/8/8/6K1 b - - 0 1"));
        assertContains(tags, "FACT: status=checkmated");
        assertContains(tags, "FACT: in_check=black");
        assertContains(tags, "META: mated_in=0");
        assertContains(tags, "MOVE: legal=0");
        assertContains(tags, "MOVE: evasions=0");
        assertContains(tags, "CHECKMATE: winner=white");
        assertContains(tags, "CHECKMATE: defender=black");
        assertContains(tags, "CHECKMATE: delivery=rook");
        assertContains(tags, "CHECKMATE: pattern=back_rank_mate");
        assertNoPrefix(tags, "META: eval_cp=");
        assertNoPrefix(tags, "META: eval_bucket=");
        assertContains(tags, "PIECE: activity=high_mobility side=white piece=rook square=e8");
    }

     /**
     * Handles test double check mate pattern.
     */
     private static void testDoubleCheckMatePattern() {
        List<String> tags = Generator.tags(new Position("7k/6Q1/6K1/8/8/8/8/7R b - - 0 1"));
        assertContains(tags, "FACT: status=checkmated");
        assertContains(tags, "MOVE: legal=0");
        assertContains(tags, "CHECKMATE: winner=white");
        assertContains(tags, "CHECKMATE: defender=black");
        assertContains(tags, "CHECKMATE: delivery=multiple");
        assertContains(tags, "CHECKMATE: pattern=double_check");
    }

     /**
     * Handles test corner and support mate patterns.
     */
     private static void testCornerAndSupportMatePatterns() {
        List<String> tags = Generator.tags(new Position("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1"));
        assertContains(tags, "FACT: status=checkmated");
        assertContains(tags, "CHECKMATE: delivery=queen");
        assertContains(tags, "CHECKMATE: pattern=corner_mate");
        assertContains(tags, "CHECKMATE: pattern=support_mate");
    }

     /**
     * Handles test smothered mate pattern.
     */
     private static void testSmotheredMatePattern() {
        List<String> tags = Generator.tags(new Position("6rk/5Npp/8/8/8/8/8/6K1 b - - 0 1"));
        assertContains(tags, "FACT: status=checkmated");
        assertContains(tags, "CHECKMATE: delivery=knight");
        assertContains(tags, "CHECKMATE: pattern=smothered_mate");
        assertContains(tags, "CHECKMATE: pattern=corner_mate");
    }

     /**
     * Handles test pin.
     */
     private static void testPin() {
        List<String> tags = Generator.tags(new Position("4r1k1/8/8/8/8/8/4B3/4K3 w - - 0 1"));
        assertContains(tags, "TACTIC: motif=pin side=black detail=\"pin: black rook e8 pins white bishop e2 to king\"");
        assertContains(tags, "PIECE: activity=pin side=white piece=bishop square=e2");
    }

     /**
     * Handles test static skewer.
     */
     private static void testStaticSkewer() {
        List<String> tags = Generator.tags(new Position("4k3/8/8/8/8/8/8/r3K2R w - - 0 1"));
        assertContains(tags, "TACTIC: motif=skewer side=black detail=\"skewer: black rook a1 skewers white king e1 with white rook h1 behind\"");
    }

     /**
     * Handles test hanging piece.
     */
     private static void testHangingPiece() {
        List<String> tags = Generator.tags(new Position("4k3/8/8/8/8/8/4q3/4R1K1 w - - 0 1"));
        assertContains(tags, "TACTIC: motif=hanging side=black detail=\"hanging black queen e2\"");
    }

     /**
     * Handles test hanging pawns are not tactical noise.
     */
     private static void testHangingPawnsAreNotTacticalNoise() {
        List<String> tags = Generator.tags(new Position("8/8/5K2/5p2/4kP2/8/8/8 b - - 4 58"));
        assertNoTagContaining(tags, "motif=hanging side=white detail=\"hanging white pawn f4\"");
        assertNoTagContaining(tags, "motif=hanging side=black detail=\"hanging black pawn f5\"");
    }

     /**
     * Handles test quiet minor only position has no tactics.
     */
     private static void testQuietMinorOnlyPositionHasNoTactics() {
        List<String> tags = Generator.tags(new Position("4k3/8/8/8/8/8/4N3/4K3 w - - 0 1"));
        assertContains(tags, "FACT: status=insufficient");
        assertNoPrefix(tags, "TACTIC:");
    }

     /**
     * Handles test pawn structure.
     */
    private static void testPawnStructure() {
        List<String> tags = Generator.tags(new Position("4k3/4P3/8/8/8/P7/P7/4K3 w - - 0 1"));
        assertContains(tags, "PAWN: structure=doubled side=white file=a");
        assertContains(tags, "PAWN: structure=isolated side=white square=a2");
        assertContains(tags, "PAWN: structure=passed side=white square=e7");
        assertContains(tags, "PAWN: islands side=white count=2");
        assertContains(tags, "PAWN: islands side=black count=0");
        assertContains(tags, "PAWN: majority=queenside side=white");
        assertContains(tags, "MATERIAL: imbalance=rookless");
    }

     /**
     * Handles test connected passed pawns different ranks.
     */
     private static void testConnectedPassedPawnsDifferentRanks() {
        List<String> tags = Generator.tags(new Position("k7/8/1P6/2P5/8/8/8/7K w - - 0 1"));
        assertContains(tags, "PAWN: structure=passed side=white square=b6");
        assertContains(tags, "PAWN: structure=passed side=white square=c5");
        assertContains(tags, "PAWN: structure=connected_passed side=white squares=b6,c5");
    }

     /**
     * Handles test black connected passed pawns different ranks.
     */
     private static void testBlackConnectedPassedPawnsDifferentRanks() {
        List<String> tags = Generator.tags(new Position("7k/8/8/8/5p2/6p1/8/K7 b - - 0 1"));
        assertContains(tags, "PAWN: structure=passed side=black square=f4");
        assertContains(tags, "PAWN: structure=passed side=black square=g3");
        assertContains(tags, "PAWN: structure=connected_passed side=black squares=f4,g3");
    }

     /**
     * Handles test requested strategic tag contracts.
     */
     private static void testRequestedStrategicTagContracts() {
        List<String> outpost = Generator.tags(new Position("4k3/8/8/3N4/2P1P3/8/8/4K3 w - - 0 1"));
        assertContains(outpost, "OUTPOST: side=white square=d5 piece=knight");

        List<String> blackOutpost = Generator.tags(new Position("4k3/8/8/3p1p2/4b3/8/8/4K3 b - - 0 1"));
        assertContains(blackOutpost, "OUTPOST: side=black square=e4 piece=bishop");

        List<String> backward = Generator.tags(new Position("k7/8/8/4p3/4P3/3P4/8/7K w - - 0 1"));
        assertContains(backward, "PAWN: structure=backward side=white square=d3");

        List<String> start = Generator.tags(new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
        assertContains(start, "KING: castled=no side=white");
        assertContains(start, "KING: shelter=pawns_intact side=white");
        assertContains(start, "KING: safety=safe side=white");

        List<String> castled = Generator.tags(new Position("r4rk1/5ppp/8/8/8/8/5PPP/R4RK1 w - - 0 1"));
        assertContains(castled, "KING: castled=yes side=white");
        assertContains(castled, "KING: castled=yes side=black");
        assertContains(castled, "KING: safety=very_safe side=white");

        List<String> weakened = Generator.tags(new Position("4k3/4p3/8/8/8/8/4P3/4K3 w - - 0 1"));
        assertContains(weakened, "KING: shelter=weakened side=white");
        assertContains(weakened, "KING: safety=unsafe side=white");

        List<String> exposed = Generator.tags(new Position("r4rk1/8/8/8/8/8/8/R4RK1 w - - 0 1"));
        assertContains(exposed, "KING: shelter=open side=white");
        assertContains(exposed, "KING: safety=very_unsafe side=white");

        List<String> overload = Generator.tags(new Position("2r1r2k/8/8/8/8/8/2NRN3/7K b - - 0 1"));
        assertContainsPrefix(overload, "TACTIC: motif=overload side=white detail=\"overloaded defender: white rook d2");
    }

     /**
     * Handles test outpost requires no enemy pawn attack.
     */
     private static void testOutpostRequiresNoEnemyPawnAttack() {
        List<String> tags = Generator.tags(new Position("4k3/8/4p3/3N4/2P1P3/8/8/4K3 w - - 0 1"));
        assertNoTagContaining(tags, "OUTPOST: side=white square=d5 piece=knight");
    }

     /**
     * Handles test moved king still emits castled no.
     */
     private static void testMovedKingStillEmitsCastledNo() {
        List<String> tags = Generator.tags(new Position("4k3/8/8/8/8/8/8/3K4 w - - 0 1"));
        assertContains(tags, "KING: castled=no side=white");
        assertContains(tags, "KING: castled=no side=black");
    }

     /**
     * Handles test pawn majority regions.
     */
     private static void testPawnMajorityRegions() {
        List<String> center = Generator.tags(new Position("4k3/8/8/8/3PP3/8/8/4K3 w - - 0 1"));
        assertContains(center, "PAWN: majority=center side=white");

        List<String> kingside = Generator.tags(new Position("4k3/8/8/5pp1/8/8/8/4K3 b - - 0 1"));
        assertContains(kingside, "PAWN: majority=kingside side=black");
    }

     /**
     * Handles test en passant fact does not create promotion tactics.
     */
    private static void testEnPassantFactDoesNotCreatePromotionTactics() {
        List<String> tags = Generator.tags(new Position("k7/8/8/3Pp3/8/8/8/7K w - e6 0 1"));
        assertContains(tags, "FACT: en_passant=e6");
        assertContains(tags, "MOVE: en_passant=1");
        assertNoTagContaining(tags, "motif=promotion");
        assertNoTagContaining(tags, "motif=underpromotion");
    }

     /**
     * Handles test forced only move tags.
     */
    private static void testForcedOnlyMoveTags() {
        List<String> tags = Generator.tags(new Position("8/8/8/8/8/5k2/7q/7K w - - 0 1"));
        assertContains(tags, "FACT: status=check");
        assertContains(tags, "MOVE: legal=1");
        assertContains(tags, "MOVE: captures=1");
        assertContains(tags, "MOVE: evasions=1");
        assertContains(tags, "MOVE: only=h1h2");
        assertContains(tags, "MOVE: forced=true");
    }

     /**
     * Handles test material imbalance delta.
     */
    private static void testMaterialImbalanceDelta() {
        Delta delta = Delta.diff(
                List.of("MATERIAL: imbalance=queenless"),
                List.of("MATERIAL: imbalance=queenless", "MATERIAL: imbalance=bishop_pair_white"));
        assertContains(delta.added(), "MATERIAL: imbalance=bishop_pair_white");
        if (!delta.removed().isEmpty()) {
            throw new AssertionError("unexpected removed tags: " + delta.removed());
        }
        if (!delta.changed().isEmpty()) {
            throw new AssertionError("unexpected changed tags: " + delta.changed());
        }
    }

     /**
     * Handles test move specific tactics do not collapse in delta.
     */
     private static void testMoveSpecificTacticsDoNotCollapseInDelta() {
        List<String> after = List.of(
                "TACTIC: motif=discovered_attack side=white move=f3e5 piece=knight square=f3 slider=bishop@e2 target=bishop@g4",
                "TACTIC: motif=discovered_attack side=white move=f3e5 piece=knight square=f3 slider=bishop@e2 target=queen@h5");
        Delta delta = Delta.diff(List.of(), after);
        assertContains(delta.added(), after.get(0));
        assertContains(delta.added(), after.get(1));
        assertSize(delta.added(), 2, "move-specific tactical delta additions");
        if (!delta.changed().isEmpty() || !delta.removed().isEmpty()) {
            throw new AssertionError("unexpected tactical delta buckets: " + delta.toJson());
        }
    }

     /**
     * Handles test opposite bishops require single bishop each.
     */
     private static void testOppositeBishopsRequireSingleBishopEach() {
        List<String> tags = Generator.tags(new Position("2b1k3/8/8/8/8/8/8/2B1K2B w - - 0 1"));
        assertContains(tags, "MATERIAL: imbalance=bishop_pair_white");
        assertNotContains(tags, "MATERIAL: imbalance=opposite_color_bishops");
        assertNoTagContaining(tags, "opposite_bishops");
    }

     /**
     * Handles test lichess hanging piece tags.
     */
     private static void testLichessHangingPieceTags() {
        Position position = after("r6k/pp2r2p/4Rp1Q/3p4/8/1N1P2R1/PqP2bPP/7K b - - 0 24",
                "f2g3");
        List<String> tags = Generator.tags(position);
        assertContains(tags, "TACTIC: motif=hanging side=black detail=\"hanging black bishop g3\"");
        assertContains(tags, "TACTIC: motif=hanging side=black detail=\"hanging black rook e7\"");
        assertContains(tags, "TACTIC: motif=pin side=white detail=\"pin: white queen h6 pins black pawn h7 to king\"");
        assertContains(tags, "PIECE: activity=pin side=black piece=pawn square=h7");
    }

     /**
     * Handles test lichess checking pin tags.
     */
     private static void testLichessCheckingPinTags() {
        Position position = after("3r3r/pQNk1ppp/1qnb1n2/1B6/8/8/PPP3PP/3R1R1K w - - 5 19",
                "d1d6");
        List<String> tags = Generator.tags(position);
        assertContains(tags, "FACT: status=check");
        assertContains(tags, "FACT: in_check=black");
        assertContains(tags, "TACTIC: motif=hanging side=white detail=\"hanging white queen b7\"");
        assertContains(tags, "TACTIC: motif=hanging side=white detail=\"hanging white rook d6\"");
        assertContains(tags, "TACTIC: motif=pin side=white detail=\"pin: white bishop b5 pins black knight c6 to king\"");
        assertContains(tags, "PIECE: activity=pin side=black piece=knight square=c6");
    }

     /**
     * Handles test lichess mutual pin tags.
     */
     private static void testLichessMutualPinTags() {
        Position position = after("6k1/pp1r1pp1/1qp1p2p/4P2P/5Q2/1P4R1/P1Pr1PP1/R5K1 b - - 4 23",
                "b6d4");
        List<String> tags = Generator.tags(position);
        assertContains(tags, "TACTIC: motif=pin side=black detail=\"pin: black queen d4 pins white pawn f2 to king\"");
        assertContains(tags, "TACTIC: motif=pin side=white detail=\"pin: white rook g3 pins black pawn g7 to king\"");
        assertContains(tags, "PIECE: activity=pin side=white piece=pawn square=f2");
        assertContains(tags, "PIECE: activity=pin side=black piece=pawn square=g7");
    }

     /**
     * Handles test lichess fork move tag.
     */
     private static void testLichessForkMoveTag() {
        Position position = after("6k1/5p1p/4p3/4q3/3nN3/2Q3P1/PP3P1P/6K1 w - - 2 37", "e4d2");
        List<String> tags = Generator.tags(position);
        assertContainsPrefix(tags, "TACTIC: motif=fork side=black move=d4e2");
        assertNoTagContaining(tags, "behind=pawn@");
    }

     /**
     * Handles test pawn fork move tag.
     */
     private static void testPawnForkMoveTag() {
        List<String> tags = Generator.tags(new Position("8/3r1k2/8/4P3/8/8/8/4K3 w - - 0 1"));
        assertContains(tags, "TACTIC: motif=fork side=white move=e5e6 piece=pawn square=e6 targets=rook@d7,king@f7");
    }

     /**
     * Handles test lichess skewer move tag.
     */
     private static void testLichessSkewerMoveTag() {
        Position position = after("8/7R/8/5p2/4bk1P/8/2r2K2/6R1 w - - 7 51", "f2f1");
        List<String> tags = Generator.tags(position);
        assertContains(tags,
                "TACTIC: motif=skewer side=black move=c2c1 piece=rook square=c1 front=king@f1 behind=rook@g1");
    }

     /**
     * Handles test skewer rejects pawn behind.
     */
     private static void testSkewerRejectsPawnBehind() {
        Position position = after("1qr2rk1/pb2bppp/8/8/2p1N3/P1Bn2P1/2Q2PBP/1R3RK1 b - - 3 23",
                "b8c7");
        List<String> tags = Generator.tags(position);
        assertNoTagContaining(tags, "pawn h7 behind");
        assertNoTagContaining(tags, "behind=pawn@");
    }

     /**
     * Handles test static skewer rejects low value front target.
     */
     private static void testStaticSkewerRejectsLowValueFrontTarget() {
        List<String> tags = Generator.tags(new Position(
                "4rrk1/7p/q1p3n1/1p4QR/p3p3/2P2P2/P1P3PP/3R1K2 b - - 1 26"));
        assertNoTagContaining(tags, "skewers white pawn f3 with white king f1 behind");
        assertNoTagContaining(tags, "skewers black knight g6 with black king g8 behind");
    }

     /**
     * Handles test defended attacked piece is not hanging.
     */
     private static void testDefendedAttackedPieceIsNotHanging() {
        List<String> tags = Generator.tags(new Position("4k3/8/8/8/8/8/r3q3/4R1K1 w - - 0 1"));
        assertNoTagContaining(tags, "motif=hanging side=black detail=\"hanging black queen e2\"");
    }

     /**
     * Handles test lichess discovered attack move tag.
     */
     private static void testLichessDiscoveredAttackMoveTag() {
        Position position = after("r3kb1r/ppq2ppp/4pn2/2Ppn3/1P4bP/2P2N2/P3BPP1/RNBQ1RK1 b kq - 2 10",
                "f8e7");
        List<String> tags = Generator.tags(position);
        assertContains(tags,
                "TACTIC: motif=discovered_attack side=white move=f3e5 piece=knight square=f3 slider=bishop@e2 target=bishop@g4");
    }

     /**
     * Handles test quiet discovered attack alternatives suppressed.
     */
     private static void testQuietDiscoveredAttackAlternativesSuppressed() {
        Position position = after("r3kb1r/ppq2ppp/4pn2/2Ppn3/1P4bP/2P2N2/P3BPP1/RNBQ1RK1 b kq - 2 10",
                "f8e7");
        List<String> tags = Generator.tags(position);
        assertNoTagContaining(tags, "move=f3d2");
        assertNoTagContaining(tags, "move=f3d4");
        assertNoTagContaining(tags, "move=f3e1");
        assertNoTagContaining(tags, "move=f3g5");
        assertNoTagContaining(tags, "move=f3h2");
    }

     /**
     * Handles test lichess mate in one move tag.
     */
     private static void testLichessMateInOneMoveTag() {
        Position position = after("2kr1b1r/p1p2pp1/2pqb3/7p/3N2n1/2NPB3/PPP2PPP/R2Q1RK1 w - - 2 13",
                "d4e6");
        List<String> tags = Generator.tags(position);
        assertContains(tags, "TACTIC: motif=mate_in_1 side=black move=d6h2");
    }

     /**
     * Handles test multiple mate in one moves.
     */
     private static void testMultipleMateInOneMoves() {
        Position position = after("6Qk/p1p3pp/4N3/1p6/2q1r1n1/2B5/PP4PP/3R1R1K b - - 0 28",
                "h8g8");
        List<String> tags = Generator.tags(position);
        assertContains(tags, "TACTIC: motif=mate_in_1 side=white move=d1d8");
        assertContains(tags, "TACTIC: motif=mate_in_1 side=white move=f1f8");
    }

     /**
     * Handles test legal promotion move tags.
     */
     private static void testLegalPromotionMoveTags() {
        List<String> tags = Generator.tags(new Position("k7/6P1/8/8/8/8/8/7K w - - 0 1"));
        assertContains(tags, "MOVE: promotions=4");
        assertContains(tags, "MOVE: underpromotions=3");
        assertContains(tags, "TACTIC: motif=promotion side=white move=g7g8q piece=queen square=g8");
        assertContains(tags, "TACTIC: motif=underpromotion side=white move=g7g8b piece=bishop square=g8");
        assertContains(tags, "TACTIC: motif=underpromotion side=white move=g7g8n piece=knight square=g8");
        assertContains(tags, "TACTIC: motif=underpromotion side=white move=g7g8r piece=rook square=g8");
    }

     /**
     * Handles test black promotion move tags.
     */
     private static void testBlackPromotionMoveTags() {
        List<String> tags = Generator.tags(new Position("7k/8/8/8/8/8/6p1/K7 b - - 0 1"));
        assertContains(tags, "MOVE: promotions=4");
        assertContains(tags, "MOVE: underpromotions=3");
        assertContains(tags, "TACTIC: motif=promotion side=black move=g2g1q piece=queen square=g1");
        assertContains(tags, "TACTIC: motif=underpromotion side=black move=g2g1b piece=bishop square=g1");
        assertContains(tags, "TACTIC: motif=underpromotion side=black move=g2g1n piece=knight square=g1");
        assertContains(tags, "TACTIC: motif=underpromotion side=black move=g2g1r piece=rook square=g1");
    }

     /**
     * Handles test promotion tags require legal promotion.
     */
     private static void testPromotionTagsRequireLegalPromotion() {
        List<String> tags = Generator.tags(new Position("k7/8/6P1/8/8/8/8/7K w - - 0 1"));
        assertNoTagContaining(tags, "motif=promotion");
        assertNoTagContaining(tags, "motif=underpromotion");
    }

     /**
     * Handles test generated tactical tags have stable fields and identities.
     */
     private static void testGeneratedTacticalTagsHaveStableFieldsAndIdentities() {
        List<String> tags = new ArrayList<>();
        tags.addAll(Generator.tags(after("6k1/5p1p/4p3/4q3/3nN3/2Q3P1/PP3P1P/6K1 w - - 2 37",
                "e4d2")));
        tags.addAll(Generator.tags(after("8/7R/8/5p2/4bk1P/8/2r2K2/6R1 w - - 7 51", "f2f1")));
        tags.addAll(Generator.tags(after("r3kb1r/ppq2ppp/4pn2/2Ppn3/1P4bP/2P2N2/P3BPP1/RNBQ1RK1 b kq - 2 10",
                "f8e7")));
        tags.addAll(Generator.tags(new Position("k7/6P1/8/8/8/8/8/7K w - - 0 1")));
        tags.addAll(Generator.tags(new Position("7k/8/8/8/8/8/6p1/K7 b - - 0 1")));
        tags.addAll(Generator.tags(after("6Qk/p1p3pp/4N3/1p6/2q1r1n1/2B5/PP4PP/3R1R1K b - - 0 28",
                "h8g8")));
        List<String> tactics = tagsWithPrefix(tags, "TACTIC:");
        assertMoveSpecificTacticFields(tactics);
        Delta delta = Delta.diff(List.of(), tactics);
        assertSize(delta.added(), tactics.size(), "generated tactical identity additions");
        if (!delta.changed().isEmpty() || !delta.removed().isEmpty()) {
            throw new AssertionError("unexpected generated tactical delta buckets: " + delta.toJson());
        }
    }

     /**
     * Handles test canonical output shape across representative positions.
     */
     private static void testCanonicalOutputShapeAcrossRepresentativePositions() {
        List<List<String>> samples = List.of(
                Generator.tags(new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")),
                Generator.tags(new Position("4R1k1/5ppp/8/8/8/8/8/6K1 b - - 0 1")),
                Generator.tags(new Position("7k/6Q1/6K1/8/8/8/8/7R b - - 0 1")),
                Generator.tags(new Position("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1")),
                Generator.tags(new Position("6rk/5Npp/8/8/8/8/8/6K1 b - - 0 1")),
                Generator.tags(new Position("k7/6P1/8/8/8/8/8/7K w - - 0 1")),
                Generator.tags(new Position("7k/8/8/8/8/8/6p1/K7 b - - 0 1")),
                Generator.tags(new Position("k7/8/8/3Pp3/8/8/8/7K w - e6 0 1")),
                Generator.tags(new Position("k7/8/1P6/2P5/8/8/8/7K w - - 0 1")),
                Generator.tags(new Position("4k3/8/8/3N4/2P1P3/8/8/4K3 w - - 0 1")),
                Generator.tags(new Position("r4rk1/8/8/8/8/8/8/R4RK1 w - - 0 1")),
                Generator.tags(new Position("2r1r2k/8/8/8/8/8/2NRN3/7K b - - 0 1")),
                Generator.tags(after("6k1/5p1p/4p3/4q3/3nN3/2Q3P1/PP3P1P/6K1 w - - 2 37",
                        "e4d2")),
                Generator.tags(after("6k1/pp1r1pp1/1qp1p2p/4P2P/5Q2/1P4R1/P1Pr1PP1/R5K1 b - - 4 23",
                        "b6d4")),
                Generator.tags(after("r3kb1r/ppq2ppp/4pn2/2Ppn3/1P4bP/2P2N2/P3BPP1/RNBQ1RK1 b kq - 2 10",
                        "f8e7")));
        for (List<String> tags : samples) {
            assertCanonicalTags(tags);
            assertPieceTierFields(tags);
            assertMoveSpecificTacticFields(tagsWithPrefix(tags, "TACTIC:"));
            assertRequestedTagFields(tags);
        }
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

     /**
     * Handles assert contains prefix.
     * @param tags tags
     * @param prefix prefix
     */
     private static void assertContainsPrefix(List<String> tags, String prefix) {
        for (String tag : tags) {
            if (tag.startsWith(prefix)) {
                return;
            }
        }
        throw new AssertionError("missing tag prefix: " + prefix + "\nactual: " + tags);
    }

     /**
     * Handles assert move specific tactic fields.
     * @param tags tags
     */
    private static void assertMoveSpecificTacticFields(List<String> tags) {
        for (String tag : tags) {
            String move = field(tag, "move");
            if (!move.isEmpty() && !isUciMove(move)) {
                throw new AssertionError("malformed tactic move field: " + tag);
            }
            String square = field(tag, "square");
            if (!square.isEmpty() && !isSquare(square)) {
                throw new AssertionError("malformed tactic square field: " + tag);
            }
            assertTargetField(tag, "targets");
            assertTargetField(tag, "front");
            assertTargetField(tag, "behind");
            assertTargetField(tag, "slider");
            assertTargetField(tag, "target");
        }
    }

     /**
     * Handles assert requested tag fields.
     * @param tags tags
     */
     private static void assertRequestedTagFields(List<String> tags) {
        for (String tag : tags) {
            if (tag.startsWith("OUTPOST:")) {
                assertSideField(tag);
                assertSquareField(tag, "square");
                assertFieldOneOf(tag, "piece", List.of("knight", "bishop"));
            } else if (tag.startsWith("PAWN: structure=")) {
                assertPawnStructureFields(tag);
            } else if (tag.startsWith("PAWN: islands ")) {
                assertSideField(tag);
                if (!isNonNegativeInteger(field(tag, "count"))) {
                    throw new AssertionError("malformed pawn-island count: " + tag);
                }
            } else if (tag.startsWith("PAWN: majority=")) {
                assertFieldOneOf(tag, "majority", List.of("queenside", "center", "kingside"));
                assertSideField(tag);
            } else if (tag.startsWith("KING: castled=")) {
                assertFieldOneOf(tag, "castled", List.of("yes", "no"));
                assertSideField(tag);
            } else if (tag.startsWith("KING: shelter=")) {
                assertFieldOneOf(tag, "shelter", List.of("pawns_intact", "open", "weakened"));
                assertSideField(tag);
            } else if (tag.startsWith("KING: safety=")) {
                assertFieldOneOf(tag, "safety", List.of("safe", "very_safe", "unsafe", "very_unsafe"));
                assertSideField(tag);
            } else if (tag.startsWith("MOVE:")) {
                assertMoveFactFields(tag);
            } else if (tag.startsWith("CHECKMATE:")) {
                assertCheckmateFields(tag);
            } else if (tag.startsWith("TACTIC:")) {
                assertTacticalFields(tag);
            }
        }
    }

     /**
     * Handles assert move fact fields.
     * @param tag tag
     */
    private static void assertMoveFactFields(String tag) {
        String key = firstFieldKey(tag);
        if ("only".equals(key)) {
            if (!isUciMove(field(tag, key))) {
                throw new AssertionError("malformed only-move field: " + tag);
            }
            return;
        }
        if ("forced".equals(key)) {
            assertFieldOneOf(tag, key, List.of("true"));
            return;
        }
        if (!List.of("legal", "captures", "checks", "mates", "promotions", "castles", "quiet",
                "evasions", "en_passant", "underpromotions").contains(key)
                || !isNonNegativeInteger(field(tag, key))) {
            throw new AssertionError("malformed move fact tag: " + tag);
        }
    }

     /**
     * Handles assert checkmate fields.
     * @param tag tag
     */
    private static void assertCheckmateFields(String tag) {
        String key = firstFieldKey(tag);
        if ("winner".equals(key) || "defender".equals(key)) {
            assertFieldOneOf(tag, key, List.of("white", "black"));
        } else if ("delivery".equals(key)) {
            assertFieldOneOf(tag, key, List.of("pawn", "knight", "bishop", "rook", "queen", "king", "multiple"));
        } else if ("pattern".equals(key)) {
            assertFieldOneOf(tag, key, List.of("double_check", "back_rank_mate", "smothered_mate",
                    "corner_mate", "support_mate"));
        } else {
            throw new AssertionError("unknown checkmate tag: " + tag);
        }
    }

     /**
     * Handles assert pawn structure fields.
     * @param tag tag
     */
     private static void assertPawnStructureFields(String tag) {
        String structure = field(tag, "structure");
        assertSideField(tag);
        if (List.of("isolated", "passed", "backward").contains(structure)) {
            assertSquareField(tag, "square");
        } else if ("doubled".equals(structure)) {
            if (!isFile(field(tag, "file"))) {
                throw new AssertionError("malformed doubled-pawn file: " + tag);
            }
        } else if ("connected_passed".equals(structure)) {
            String squares = field(tag, "squares");
            String[] parts = squares.split(",");
            if (parts.length != 2 || !isSquare(parts[0]) || !isSquare(parts[1])) {
                throw new AssertionError("malformed connected-passed squares: " + tag);
            }
        } else {
            throw new AssertionError("unknown pawn structure tag: " + tag);
        }
    }

     /**
     * Handles assert tactical fields.
     * @param tag tag
     */
     private static void assertTacticalFields(String tag) {
        String motif = field(tag, "motif");
        assertFieldOneOf(tag, "motif", List.of("pin", "skewer", "overload", "hanging", "mate_in_1",
                "promotion", "underpromotion", "fork", "discovered_attack"));
        assertSideField(tag);
        String move = field(tag, "move");
        if (move.isEmpty()) {
            if (!tag.contains(" detail=\"") || !tag.endsWith("\"")) {
                throw new AssertionError("static tactic is missing quoted detail: " + tag);
            }
            return;
        }
        if (!isUciMove(move)) {
            throw new AssertionError("malformed tactic move field: " + tag);
        }
        if ("mate_in_1".equals(motif)) {
            return;
        }
        if ("promotion".equals(motif)) {
            assertFieldOneOf(tag, "piece", List.of("queen"));
            assertSquareField(tag, "square");
        } else if ("underpromotion".equals(motif)) {
            assertFieldOneOf(tag, "piece", List.of("bishop", "knight", "rook"));
            assertSquareField(tag, "square");
        } else if ("fork".equals(motif)) {
            assertFieldOneOf(tag, "piece", List.of("pawn", "knight", "bishop", "rook", "queen", "king"));
            assertSquareField(tag, "square");
            assertTargetField(tag, "targets");
        } else if ("skewer".equals(motif)) {
            assertFieldOneOf(tag, "piece", List.of("bishop", "rook", "queen"));
            assertSquareField(tag, "square");
            assertTargetField(tag, "front");
            assertTargetField(tag, "behind");
        } else if ("discovered_attack".equals(motif)) {
            assertFieldOneOf(tag, "piece", List.of("pawn", "knight", "bishop", "rook", "queen", "king"));
            assertSquareField(tag, "square");
            assertTargetField(tag, "slider");
            assertTargetField(tag, "target");
        }
    }

     /**
     * Handles assert side field.
     * @param tag tag
     */
     private static void assertSideField(String tag) {
        assertFieldOneOf(tag, "side", List.of("white", "black"));
    }

     /**
     * Handles assert square field.
     * @param tag tag
     * @param key key
     */
     private static void assertSquareField(String tag, String key) {
        if (!isSquare(field(tag, key))) {
            throw new AssertionError("malformed " + key + " field: " + tag);
        }
    }

     /**
     * Handles assert field one of.
     * @param tag tag
     * @param key key
     * @param allowed allowed
     */
     private static void assertFieldOneOf(String tag, String key, List<String> allowed) {
        String value = field(tag, key);
        if (!allowed.contains(value)) {
            throw new AssertionError("malformed " + key + " field: " + tag);
        }
    }

     /**
     * Handles assert target field.
     * @param tag tag
     * @param key key
     */
     private static void assertTargetField(String tag, String key) {
        String value = field(tag, key);
        if (value.isEmpty()) {
            return;
        }
        for (String target : value.split(",")) {
            if (!isTargetLabel(target)) {
                throw new AssertionError("malformed tactic " + key + " field: " + tag);
            }
        }
    }

     /**
     * Handles assert size.
     * @param values values
     * @param expected expected
     * @param label label
     */
     private static void assertSize(List<?> values, int expected, String label) {
        if (values.size() != expected) {
            throw new AssertionError(label + " expected size " + expected + " but was " + values.size()
                    + ": " + values);
        }
    }

     /**
     * Handles assert canonical tags.
     * @param tags tags
     */
     private static void assertCanonicalTags(List<String> tags) {
        List<String> canonical = Sort.sort(tags);
        if (!tags.equals(canonical)) {
            throw new AssertionError("tags are not canonical\nactual: " + tags + "\ncanonical: " + canonical);
        }
    }

     /**
     * Handles assert not contains.
     * @param tags tags
     * @param unexpected unexpected
     */
     private static void assertNotContains(List<String> tags, String unexpected) {
        if (tags.contains(unexpected)) {
            throw new AssertionError("unexpected tag: " + unexpected + "\nactual: " + tags);
        }
    }

     /**
     * Handles assert no prefix.
     * @param tags tags
     * @param prefix prefix
     */
     private static void assertNoPrefix(List<String> tags, String prefix) {
        for (String tag : tags) {
            if (tag.startsWith(prefix)) {
                throw new AssertionError("unexpected tag prefix " + prefix + ": " + tag + "\nactual: " + tags);
            }
        }
    }

     /**
     * Handles assert no tag containing.
     * @param tags tags
     * @param text text
     */
     private static void assertNoTagContaining(List<String> tags, String text) {
        for (String tag : tags) {
            if (tag.contains(text)) {
                throw new AssertionError("unexpected tag containing " + text + ": " + tag + "\nactual: " + tags);
            }
        }
    }

     /**
     * Handles assert piece tier fields.
     * @param tags tags
     */
     private static void assertPieceTierFields(List<String> tags) {
        for (String tag : tags) {
            if (!tag.startsWith("PIECE: tier=")) {
                continue;
            }
            String piece = field(tag, "piece");
            String square = field(tag, "square");
            if (!isPiece(piece) || !isSquare(square)) {
                throw new AssertionError("malformed piece tier tag: " + tag + "\nactual: " + tags);
            }
        }
    }

     /**
     * Handles field.
     * @param tag tag
     * @param key key
     * @return computed value
     */
    private static String field(String tag, String key) {
        String prefix = key + "=";
        for (String token : tag.split("\\s+")) {
            if (token.startsWith(prefix)) {
                return token.substring(prefix.length());
            }
        }
        return "";
    }

     /**
     * Returns the first key used by a tag.
     * @param tag tag
     * @return first field key
     */
    private static String firstFieldKey(String tag) {
        int colon = tag.indexOf(':');
        if (colon < 0) {
            return "";
        }
        String rest = tag.substring(colon + 1).trim();
        int eq = rest.indexOf('=');
        if (eq <= 0) {
            return "";
        }
        return rest.substring(0, eq).trim();
    }

     /**
     * Returns whether piece.
     * @param value value
     * @return true when piece
     */
     private static boolean isPiece(String value) {
        return List.of("pawn", "knight", "bishop", "rook", "queen", "king").contains(value);
    }

     /**
     * Returns whether uci move.
     * @param value value
     * @return true when uci move
     */
     private static boolean isUciMove(String value) {
        if (value == null || (value.length() != 4 && value.length() != 5)) {
            return false;
        }
        if (!isSquare(value.substring(0, 2)) || !isSquare(value.substring(2, 4))) {
            return false;
        }
        return value.length() == 4 || "qrbn".indexOf(value.charAt(4)) >= 0;
    }

     /**
     * Returns whether square.
     * @param value value
     * @return true when square
     */
     private static boolean isSquare(String value) {
        return value != null && value.length() == 2
                && value.charAt(0) >= 'a' && value.charAt(0) <= 'h'
                && value.charAt(1) >= '1' && value.charAt(1) <= '8';
    }

     /**
     * Returns whether target label.
     * @param value value
     * @return true when target label
     */
    private static boolean isTargetLabel(String value) {
        if (value == null) {
            return false;
        }
        int at = value.indexOf('@');
        return at > 0 && isPiece(value.substring(0, at)) && isSquare(value.substring(at + 1));
    }

     /**
     * Returns whether file.
     * @param value value
     * @return true when file
     */
     private static boolean isFile(String value) {
        return value != null && value.length() == 1 && value.charAt(0) >= 'a' && value.charAt(0) <= 'h';
    }

     /**
     * Returns whether non negative integer.
     * @param value value
     * @return true when non negative integer
     */
     private static boolean isNonNegativeInteger(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

     /**
     * Returns tags with prefix.
     * @param tags tags
     * @param prefix prefix
     * @return filtered tags
     */
     private static List<String> tagsWithPrefix(List<String> tags, String prefix) {
        List<String> filtered = new ArrayList<>();
        for (String tag : tags) {
            if (tag.startsWith(prefix)) {
                filtered.add(tag);
            }
        }
        return filtered;
    }

     /**
     * Returns a position after applying one UCI move.
     * @param fen fen
     * @param uci uci
     * @return position after move
     */
     private static Position after(String fen, String uci) {
        Position position = new Position(fen);
        MoveList moves = position.legalMoves();
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            if (uci.equals(Move.toString(move))) {
                return position.copy().play(move);
            }
        }
        throw new AssertionError("illegal move " + uci + " in " + fen);
    }
}
