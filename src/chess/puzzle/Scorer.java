package chess.puzzle;

import java.util.ArrayList;
import java.util.List;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.eval.CentipawnEvaluator;
import chess.uci.Analysis;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Numbers;

/**
 * Estimates puzzle difficulty from deep MultiPV truth and cheap classical
 * visibility signals.
 *
 * <p>
 * The scorer is intentionally inexpensive. It treats the supplied UCI analysis
 * as the puzzle truth, then runs only static and one-ply classical evaluation to
 * estimate how discoverable the solution is to a weaker solver.
 * </p>
 *
 * <p>
 * <strong>Warning:</strong> the produced scores are deterministic heuristics
 * tuned for puzzle ordering and export labels. They should not be used as proof
 * of objective chess difficulty or as human player ratings.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Scorer {

    /**
     * Centipawn threshold used by the mining gates for winning resources.
     *
     * <p>
     * A primary variation at or above this score is treated as a winning puzzle
     * objective for the side to move.
     * </p>
     */
    private static final int WIN_CP = 300;

    /**
     * Centipawn window treated as holding equality for draw-resource puzzles.
     *
     * <p>
     * A best line inside this losing-side window can be classified as a draw
     * resource when lower-ranked alternatives are clearly losing.
     * </p>
     */
    private static final int DRAW_HOLD_CP = 80;

    /**
     * Centipawn value used when no second PV is available.
     *
     * <p>
     * The sentinel is deliberately far below normal scores so missing PV2 data
     * does not accidentally look like a close alternative to PV1.
     * </p>
     */
    private static final int UNKNOWN_SECOND_CP = Integer.MIN_VALUE / 4;

    /**
     * Cheap move-score tolerance before a move is considered clearly better.
     *
     * <p>
     * Differences inside this window are treated as ties for cheap-evaluator
     * ranking.
     * </p>
     */
    private static final int CHEAP_RANK_TOLERANCE_CP = 20;

    /**
     * Default cheap evaluator used for visibility checks.
     *
     * <p>
     * The evaluator is resolved lazily through {@link #defaultCheapEvaluator()}
     * so standalone builds can fall back to material-only visibility.
     * </p>
     */
    private static final CentipawnEvaluator DEFAULT_CHEAP_EVALUATOR = defaultCheapEvaluator();

    /**
     * Lowest user-facing Elo-like puzzle rating.
     *
     * <p>
     * This is the floor for direct scorer ratings.
     * </p>
     */
    private static final int RATING_FLOOR = 600;

    /**
     * Highest user-facing Elo-like puzzle rating.
     */
    private static final int RATING_CEILING = 3000;

    /**
     * Rating span used by the direct feature-score scale before tree tails.
     *
     * <p>
     * A normalized score of {@code 1.0} maps to
     * {@code RATING_FLOOR + STANDARD_RATING_SPAN} before any explicit
     * long-tree tail bonus is added.
     * </p>
     */
    private static final int STANDARD_RATING_SPAN = 2200;

    /**
     * Raw-score point where the Lichess-like mid-band lift begins.
     *
     * <p>
     * Scores below this point remain close to the direct tactical signal so
     * genuinely simple puzzles do not drift into the training-body median.
     * </p>
     */
    private static final double DIRECT_MID_BAND_START = 0.24;

    /**
     * Raw-score width over which the Lichess-like mid-band lift reaches full
     * strength.
     */
    private static final double DIRECT_MID_BAND_WIDTH = 0.48;

    /**
     * Strength of the direct mid-band lift.
     *
     * <p>
     * The lift makes middle and upper-middle puzzles closer to observed Lichess
     * puzzle ratings without using corpus rank or subset calibration.
     * </p>
     */
    private static final double DIRECT_MID_BAND_LIFT = 4.30;

    /**
     * Decay exponent that keeps the mid-band lift from flooding the extreme hard
     * tail.
     */
    private static final double DIRECT_MID_BAND_DECAY_EXPONENT = 2.95;

    /**
     * Normalized score where the upper-body shoulder begins.
     *
     * <p>
     * This soft shoulder keeps upper-intermediate puzzles from jumping straight
     * into the extreme tail while preserving their ordering.
     * </p>
     */
    private static final double DIRECT_UPPER_BODY_SHOULDER_START = 0.48;

    /**
     * Width of the upper-body shoulder fade-in.
     */
    private static final double DIRECT_UPPER_BODY_SHOULDER_WIDTH = 0.30;

    /**
     * Strength of the upper-body shoulder.
     */
    private static final double DIRECT_UPPER_BODY_SHOULDER_STRENGTH = 0.25;

    /**
     * Normalized score where hard-tail compression begins.
     *
     * <p>
     * Lichess ratings keep the 2900+ tail very small. This threshold leaves the
     * main training body untouched while preventing shallow saturated root
     * tactics from crowding the very top.
     * </p>
     */
    private static final double DIRECT_HARD_TAIL_COMPRESSION_START = 0.84;

    /**
     * Multiplier applied to direct-score excess above the hard-tail compression
     * start.
     *
     * <p>
     * Explicit continuation tails are applied after this curve, so rare long
     * trees and special moves can still separate themselves near the ceiling.
     * </p>
     */
    private static final double DIRECT_HARD_TAIL_COMPRESSION_SCALE = 0.72;

    /**
     * Width of the hard-tail compression blend.
     *
     * <p>
     * Blending the compression over a short score interval removes a slope kink
     * around the hard-tail threshold, which keeps adjacent upper-body puzzles
     * ordered more smoothly.
     * </p>
     */
    private static final double DIRECT_HARD_TAIL_COMPRESSION_WIDTH = 0.10;

    /**
     * Upper raw-score limit for the easy-band dampener.
     *
     * <p>
     * Low tactical-signal positions are slightly expanded downward so the easy
     * tail does not bunch around 900 Elo.
     * </p>
     */
    private static final double DIRECT_EASY_BAND_DAMP_END = 0.40;

    /**
     * Width of the easy-band dampener fade-out.
     */
    private static final double DIRECT_EASY_BAND_DAMP_WIDTH = 0.28;

    /**
     * Strength of the easy-band dampener.
     */
    private static final double DIRECT_EASY_BAND_DAMP_STRENGTH = 0.16;

    /**
     * Tiny feature-based spread applied before direct scoring to avoid feature-bundle
     * pile-ups without using graph-level smoothing.
     *
     * <p>
     * The spread is small enough to break ties between otherwise identical
     * feature bundles without changing the broad difficulty category.
     * </p>
     */
    private static final double FEATURE_TIE_SPREAD = 0.012;

    /**
     * Small deterministic rating spread for otherwise identical feature bundles.
     *
     * <p>
     * This uses only the puzzle position signature, so the same puzzle keeps the
     * same rating no matter which subset is exported. The spread is deliberately
     * bounded: it breaks visible integer-rating ties without letting a hash
     * substitute for chess evidence.
     * </p>
     */
    private static final double DIRECT_RATING_JITTER_WIDTH = 72.0;

    /**
     * Minimum multiplier for ordinary explicit-tree evidence.
     *
     * <p>
     * Long technical endgame trees still matter, but length alone should not
     * receive the same weight as a materially rich calculation tree with varied
     * moving pieces.
     * </p>
     */
    private static final double ORDINARY_TREE_EVIDENCE_MIN_SCALE = 0.55;

    /**
     * Material value treated as a materially rich root position.
     *
     * <p>
     * The value excludes kings and is used only to decide how much ordinary tree
     * length/branch evidence should count.
     * </p>
     */
    private static final double TREE_MATERIAL_RICHNESS_CP = 3600.0;

    /**
     * Raw-score weight for the difficulty of non-root continuation nodes.
     *
     * <p>
     * This rewards explicit continuation positions that are themselves hard to
     * find, instead of treating every follow-up move as equal.
     * </p>
     */
    private static final double TREE_CONTINUATION_NODE_WEIGHT = 0.17;

    /**
     * Raw-score weight for proven continuation depth.
     *
     * <p>
     * Long forced solution trees should rank above otherwise similar one-move
     * puzzles because the solver must keep finding correct moves after the key.
     * </p>
     */
    private static final double TREE_CONTINUATION_DEPTH_WEIGHT = 0.26;

    /**
     * Raw-score weight for explicit opponent-reply variation.
     *
     * <p>
     * Branching solution trees are harder because the solver must be prepared for
     * multiple defensive tries instead of memorizing only one line.
     * </p>
     */
    private static final double TREE_VARIATION_WEIGHT = 0.12;

    /**
     * Raw-score weight for piece diversity inside the solution tree.
     *
     * <p>
     * Requiring several piece types or several distinct moving pieces creates
     * real human cognitive load, so this is weighted above generic branch fanout.
     * </p>
     */
    private static final double TREE_DIVERSITY_WEIGHT = 0.16;

    /**
     * Raw-score weight for rare move forms inside the solution tree.
     *
     * <p>
     * Underpromotion, en-passant, and castling are rare solution requirements and
     * must materially affect corpus ordering. Underpromotion carries the largest
     * signal, followed by en-passant and then castling.
     * </p>
     */
    private static final double TREE_SPECIAL_WEIGHT = 0.16;

    /**
     * Raw-score weight for nonforcing continuation moves.
     *
     * <p>
     * Quiet follow-up moves are slightly harder to discover, but this signal is
     * intentionally modest compared with proven depth and variation.
     * </p>
     */
    private static final double TREE_NONFORCING_WEIGHT = 0.04;

    /**
     * Raw-score tail for extreme explicit solution-tree shape.
     *
     * <p>
     * The ordinary tree weights reward the average case. This tail is reserved for
     * unusually long or broad source-backed puzzle trees so they can outrank
     * shallow positions that are already saturated by root visibility alone. It
     * emphasizes sustained line length and piece diversity more than one large
     * fanout node, because that better matches human calculation load.
     * </p>
     */
    private static final double TREE_COMPLEXITY_TAIL_WEIGHT = 0.34;

    /**
     * Branch-load value treated as a full variation signal.
     *
     * <p>
     * A higher threshold keeps one position with many similar replies from
     * dominating the score.
     * </p>
     */
    private static final double TREE_BRANCH_LOAD_FULL_SIGNAL = 4.0;

    /**
     * Root-reply count treated as a full root-variation signal.
     *
     * <p>
     * Five distinct replies after the key are already enough to make a puzzle
     * variation-heavy for human training purposes.
     * </p>
     */
    private static final double TREE_ROOT_REPLY_FULL_SIGNAL = 5.0;

    /**
     * Explicit plies treated as a full long-continuation signal.
     */
    private static final double TREE_LONG_PLY_FULL_SIGNAL = 21.0;

    /**
     * Tree-node count treated as a full broad-tree signal.
     */
    private static final double TREE_NODE_FULL_SIGNAL = 96.0;

    /**
     * Branch-point count treated as a full distributed-variation signal.
     *
     * <p>
     * This is intentionally lower than the raw tree-node threshold so branches
     * spread across several calculation points matter more than a single large
     * fanout.
     * </p>
     */
    private static final double TREE_BRANCH_POINT_FULL_SIGNAL = 12.0;

    /**
     * Direct rating tail per logarithmic branch-load unit.
     */
    private static final double TREE_BRANCH_RATING_TAIL = 28.0;

    /**
     * Direct rating tail per logarithmic continuation-depth unit.
     *
     * <p>
     * A single extra reply receives only a small bump, while many proven follow-up
     * moves can move a puzzle farther into the hard tail.
     * </p>
     */
    private static final double TREE_DEPTH_RATING_TAIL = 26.0;

    /**
     * Direct rating tail per logarithmic longest-line ply unit.
     *
     * <p>
     * This keeps saturated scores ordered by the actual number of
     * moves a human must calculate.
     * </p>
     */
    private static final double TREE_LONG_LINE_RATING_TAIL = 6.0;

    /**
     * Direct rating tail per logarithmic root-reply unit.
     *
     * <p>
     * Root replies are useful variation evidence, but deeper branch load carries
     * most of the branch rating tail.
     * </p>
     */
    private static final double TREE_ROOT_REPLY_RATING_TAIL = 10.0;

    /**
     * Direct rating tail per logarithmic broad-tree node unit.
     *
     * <p>
     * Node count contributes only after the tree is larger than an ordinary short
     * continuation so breadth does not swamp tactical visibility.
     * </p>
     */
    private static final double TREE_NODE_RATING_TAIL = 9.0;

    /**
     * Direct rating tail for rare special moves.
     *
     * <p>
     * Direct export ratings visibly reward underpromotion, en-passant, and
     * castling in that order without depending on corpus rank.
     * </p>
     */
    private static final double TREE_SPECIAL_RATING_TAIL = 72.0;

    /**
     * Full special-move signal assigned to underpromotions.
     */
    private static final double TREE_UNDERPROMOTION_SIGNAL = 1.00;

    /**
     * Special-move signal assigned to en-passant captures.
     */
    private static final double TREE_EN_PASSANT_SIGNAL = 0.72;

    /**
     * Special-move signal assigned to castling moves.
     */
    private static final double TREE_CASTLE_SIGNAL = 0.48;

    /**
     * Root-key weirdness contribution for underpromotion moves.
     */
    private static final double KEY_UNDERPROMOTION_WEIRDNESS = 0.85;

    /**
     * Root-key weirdness contribution for en-passant moves.
     */
    private static final double KEY_EN_PASSANT_WEIRDNESS = 0.60;

    /**
     * Root-key weirdness contribution for castling moves.
     */
    private static final double KEY_CASTLE_WEIRDNESS = 0.42;

    /**
     * Prevents instantiation of this utility class.
     *
     * <p>
     * The scorer exposes only static entry points.
     * </p>
     */
    private Scorer() {
        // utility
    }

    /**
     * Creates the default cheap evaluator without forcing a compile-time dependency
     * on the full classical evaluator package.
     *
     * <p>
     * Reflection keeps this package usable in reduced tooling builds that do not
     * include the full evaluator implementation.
     * </p>
     *
     * @return classical evaluator when present, otherwise a material fallback
     */
    private static CentipawnEvaluator defaultCheapEvaluator() {
        try {
            Object evaluator = Class.forName("chess.eval.Classical").getDeclaredConstructor().newInstance();
            if (evaluator instanceof CentipawnEvaluator centipawnEvaluator) {
                return centipawnEvaluator;
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            // Standalone scoring builds may not include the full classical stack.
        }
        return new MaterialVisibilityEvaluator();
    }

    /**
     * Scores a puzzle from a pre-extracted continuation tree.
     *
     * <p>
     * This overload uses the package default cheap evaluator for visibility
     * checks.
     * </p>
     *
     * @param root root puzzle position
     * @param analysis root analysis
     * @param tree explicit continuation summary
     * @return difficulty estimate
     * @throws IllegalArgumentException if {@code root} is null
     */
    public static Difficulty score(Position root, Analysis analysis, PuzzleTreeSummary tree) {
        return score(scoreNode(root, analysis, DEFAULT_CHEAP_EVALUATOR), tree);
    }

    /**
     * Scores one solver-to-move position independently of any engine PV tail.
     *
     * <p>
     * Only the root node is scored here. Use {@link #score(NodeScore,
     * PuzzleTreeSummary)} to add explicit continuation-tree evidence.
     * </p>
     *
     * @param root root position
     * @param analysis deep MultiPV analysis for this position
     * @return per-position difficulty estimate
     * @throws IllegalArgumentException if {@code root} is null
     */
    public static NodeScore scoreNode(Position root, Analysis analysis) {
        return scoreNode(root, analysis, DEFAULT_CHEAP_EVALUATOR);
    }

    /**
     * Scores one solver-to-move position independently of any engine PV tail.
     *
     * <p>
     * The supplied cheap evaluator is used for static and one-ply visibility
     * checks. It is expected to return scores from the side-to-move perspective.
     * </p>
     *
     * @param root root position
     * @param analysis deep MultiPV analysis for this position
     * @param cheapEvaluator inexpensive evaluator used for visibility checks
     * @return per-position difficulty estimate
     * @throws IllegalArgumentException if {@code root} or {@code cheapEvaluator} is null
     */
    public static NodeScore scoreNode(Position root, Analysis analysis, CentipawnEvaluator cheapEvaluator) {
        if (root == null) {
            throw new IllegalArgumentException("root == null");
        }
        if (cheapEvaluator == null) {
            throw new IllegalArgumentException("cheapEvaluator == null");
        }

        Output best = analysis == null ? null : analysis.getBestOutput(1);
        short solution = analysis == null ? Move.NO_MOVE : analysis.getBestMove(1);
        int deepBestCp = best == null ? cheapEvaluator.evaluate(root) : evalAsCp(best.getEvaluation());
        Integer deepSecondCp = secondCp(analysis);
        Integer deepMarginCp = deepSecondCp == null ? null : deepBestCp - deepSecondCp;
        Goal goal = inferGoal(deepBestCp, deepSecondCp);

        MoveList legalMoves = root.legalMoves();
        CheapMoveSummary cheap = cheapMoveSummary(root, legalMoves, solution, cheapEvaluator);
        KeyShape key = keyShape(root, solution, legalMoves);

        double ambiguity = candidateAmbiguity(deepMarginCp, legalMoves.size());
        double hiddenness = hiddenness(goal, deepBestCp, cheap.staticCp, cheap.bestCp, cheap.solutionCp);
        double weirdness = moveWeirdness(key, cheap.staticCp, cheap.solutionCp);
        double nonforcing = tacticalNonforcing(key);
        double choiceComplexity = moveChoiceComplexity(legalMoves.size());
        double fineStructure = fineNodeStructure(goal, deepBestCp, deepMarginCp,
                cheap.staticCp, cheap.bestCp, cheap.solutionCp, cheap.solutionRank, legalMoves.size());

        double rawScore = Numbers.clamp01(
                0.10 * ambiguity
                        + 0.35 * hiddenness
                        + 0.23 * weirdness
                        + 0.17 * nonforcing
                        + 0.09 * choiceComplexity
                        + 0.06 * fineStructure);
        rawScore = adjustForDirectMate(rawScore, key, 1, weirdness);

        int fromSquare = solution == Move.NO_MOVE ? -1 : Move.getFromIndex(solution);
        int movingPiece = fromSquare >= 0 && fromSquare < 64 ? root.pieceAt(fromSquare) : Piece.EMPTY;
        int pieceType = Math.abs(movingPiece);
        int toSquare = solution == Move.NO_MOVE ? -1 : root.actualToSquare(solution);
        RootMaterial material = rootMaterial(root);

        return new NodeScore(
                root.signatureCore(),
                goal,
                rawScore,
                moveToString(solution),
                Move.hash(solution),
                deepBestCp,
                deepSecondCp,
                deepMarginCp,
                cheap.staticCp,
                moveToString(cheap.bestMove),
                cheap.bestCp,
                cheap.solutionCp,
                cheap.solutionRank,
                legalMoves.size(),
                key.check,
                key.mate,
                key.capture,
                key.promotion,
                key.underpromotion,
                key.castle,
                key.enPassant,
                key.quiet,
                pieceType,
                material.nonKingPieceCount(),
                material.nonPawnPieceCount(),
                material.nonKingMaterialCp(),
                fromSquare,
                toSquare);
    }

    /**
     * Builds the final puzzle score from a root node and the explicit continuation
     * tree rooted at that node.
     *
     * <p>
     * The root-node score provides tactical visibility; the tree summary adds
     * proven continuation depth, branching, piece diversity, and rare move forms.
     * </p>
     *
     * <p>
     * <strong>Warning:</strong> unverified engine PV length is not treated as
     * puzzle depth. Only explicit tree evidence raises continuation difficulty.
     * </p>
     *
     * @param node root node score
     * @param tree explicit continuation summary
     * @return final difficulty
     * @throws IllegalArgumentException if {@code node} is null
     */
    public static Difficulty score(NodeScore node, PuzzleTreeSummary tree) {
        if (node == null) {
            throw new IllegalArgumentException("node == null");
        }
        PuzzleTreeSummary t = tree == null ? PuzzleTreeSummary.single(node) : tree;
        double length = treeContinuationBurden(t);
        double variation = treeVariationComplexity(t);
        double diversity = treePieceDiversity(t);
        double special = treeSpecialMoveScore(t);
        double nonforcing = treeNonforcingScore(t);
        double complexityTail = treeComplexityTail(t);
        double ordinaryTreeScale = ordinaryTreeEvidenceScale(node, t);
        double ordinaryTreeScore = TREE_CONTINUATION_NODE_WEIGHT * t.continuationNodeScore
                + TREE_CONTINUATION_DEPTH_WEIGHT * length
                + TREE_VARIATION_WEIGHT * variation
                + TREE_DIVERSITY_WEIGHT * diversity
                + TREE_NONFORCING_WEIGHT * nonforcing
                + TREE_COMPLEXITY_TAIL_WEIGHT * complexityTail;
        double rawScore = Numbers.clamp01(node.rawScore
                + ordinaryTreeScale * ordinaryTreeScore
                + TREE_SPECIAL_WEIGHT * special);
        double spread = featureTieSpread(node, t);
        if (rawScore >= 0.995 && hardTailCognitiveLoad(t) >= 0.55) {
            spread = Math.max(-0.05, spread);
        }
        double adjustedRawScore = Numbers.clamp01(rawScore + spread * FEATURE_TIE_SPREAD);
        double baseScore = capDirectMateScore(directScore(adjustedRawScore), node.keyShape(), 1);
        int rating = ratingForTreeScore(baseScore, t, ordinaryTreeScale);
        rating = addDirectRatingJitter(rating, node.positionSignature);
        rating = capDirectMateRating(rating, node.keyShape(), 1);
        double score = scoreForRating(rating);
        String label = labelFor(score);
        FeatureSignals signals = new FeatureSignals(node.goal, node.rawScore, length, variation, diversity, special,
                nonforcing);
        List<String> names = featureNames(signals, t, node.keyShape(),
                new CheapVisibility(node.cheapStaticCp, node.cheapSolutionCp));
        DifficultyFeatures features = new DifficultyFeatures(
                node.goal,
                adjustedRawScore,
                node.solutionMoveUci,
                node.deepBestCp,
                node.deepSecondCp,
                node.deepMarginCp,
                node.cheapStaticCp,
                node.cheapBestMoveUci,
                node.cheapBestCp,
                node.cheapSolutionCp,
                node.solutionRankByCheap,
                node.legalMoveCount,
                explicitTreePlies(t),
                Math.max(1, t.rootReplyCount),
                t.nodeCount,
                t.branchPointCount,
                node.keyCheck,
                node.keyMate,
                node.keyCapture,
                node.keyPromotion,
                node.keyUnderpromotion,
                names);
        return new Difficulty(node.goal, score, rating, label, features);
    }

    /**
     * Extracts the second PV score.
     *
     * <p>
     * The score is converted into the centipawn-like representation used by the
     * rest of the scorer.
     * </p>
     *
     * @param analysis root analysis
     * @return second PV score, or null when unavailable
     */
    private static Integer secondCp(Analysis analysis) {
        if (analysis == null || analysis.getPivots() < 2) {
            return null;
        }
        Output second = analysis.getBestOutput(2);
        if (second == null) {
            return null;
        }
        return evalAsCp(second.getEvaluation());
    }

    /**
     * Infers the puzzle goal from the same broad thresholds used by mining.
     *
     * <p>
     * A winning objective requires PV1 to exceed {@link #WIN_CP}. A drawing
     * objective requires PV1 to hold while PV2 falls below the losing gate.
     * </p>
     *
     * @param bestCp PV1 score
     * @param secondCp PV2 score
     * @return inferred goal
     */
    private static Goal inferGoal(int bestCp, Integer secondCp) {
        int second = secondCp == null ? UNKNOWN_SECOND_CP : secondCp;
        if (bestCp >= WIN_CP) {
            return Goal.WIN;
        }
        if (bestCp >= -DRAW_HOLD_CP && bestCp < WIN_CP && second <= -WIN_CP) {
            return Goal.DRAW;
        }
        return Goal.UNKNOWN;
    }

    /**
     * Computes cheap one-ply classical move preferences.
     *
     * <p>
     * The summary captures how visible the engine solution is to a much cheaper
     * evaluator: root static score, cheap best move, solution score, and solution
     * rank.
     * </p>
     *
     * @param root root position
     * @param legalMoves legal root moves
     * @param solution solution move
     * @param evaluator cheap evaluator
     * @return cheap move summary
     */
    private static CheapMoveSummary cheapMoveSummary(Position root, MoveList legalMoves, short solution,
            CentipawnEvaluator evaluator) {
        int staticCp = evaluator.evaluate(root);
        boolean solutionLegal = legalMoves.contains(solution);
        CheapMoveScores scores = cheapMoveScores(root, legalMoves, solution, evaluator);
        int bestCp = scores.bestCp == Integer.MIN_VALUE ? staticCp : scores.bestCp;
        int solutionCp = fallbackSolutionCp(scores.solutionCp, solutionLegal, bestCp, staticCp);
        int rank = solutionRank(root, legalMoves, solution, solutionCp, solutionLegal, evaluator);
        return new CheapMoveSummary(staticCp, scores.bestMove, bestCp, solutionCp, rank);
    }

    /**
     * Finds the cheap evaluator's best move and solution score.
     *
     * <p>
     * Scores are measured from the root side's perspective after each legal root
     * move.
     * </p>
     *
     * @param root root position
     * @param legalMoves legal root moves
     * @param solution engine solution move
     * @param evaluator cheap evaluator
     * @return cheap move scores before fallback defaults are applied
     */
    private static CheapMoveScores cheapMoveScores(Position root, MoveList legalMoves, short solution,
            CentipawnEvaluator evaluator) {
        short bestMove = Move.NO_MOVE;
        int bestCp = Integer.MIN_VALUE;
        int solutionCp = Integer.MIN_VALUE;
        for (int i = 0; i < legalMoves.size(); i++) {
            short move = legalMoves.raw(i);
            int score = cheapMoveScore(root, move, evaluator);
            if (score > bestCp || (score == bestCp && Move.compare(move, bestMove) < 0)) {
                bestCp = score;
                bestMove = move;
            }
            if (Move.equals(move, solution)) {
                solutionCp = score;
            }
        }
        return new CheapMoveScores(bestMove, bestCp, solutionCp);
    }

    /**
     * Applies a deterministic fallback when the solution is not scored directly.
     *
     * <p>
     * Legal but unscored solutions inherit the cheap best score; illegal or
     * missing solutions fall back to the static root score.
     * </p>
     *
     * @param solutionCp scored solution value, or {@link Integer#MIN_VALUE}
     * @param solutionLegal whether the solution appears in the legal move list
     * @param bestCp cheap best score
     * @param staticCp cheap static root score
     * @return solution score to use for downstream visibility calculations
     */
    private static int fallbackSolutionCp(int solutionCp, boolean solutionLegal, int bestCp, int staticCp) {
        if (solutionCp == Integer.MIN_VALUE) {
            return solutionLegal ? bestCp : staticCp;
        }
        return solutionCp;
    }

    /**
     * Ranks the solution under the cheap evaluator.
     *
     * <p>
     * Only moves that beat the solution by more than
     * {@link #CHEAP_RANK_TOLERANCE_CP} count as clearly preferred.
     * </p>
     *
     * @param root root position
     * @param legalMoves legal root moves
     * @param solution engine solution move
     * @param solutionCp cheap score assigned to the solution
     * @param solutionLegal whether the solution appears in the legal move list
     * @param evaluator cheap evaluator
     * @return one-based solution rank under the cheap evaluator
     */
    private static int solutionRank(Position root, MoveList legalMoves, short solution, int solutionCp,
            boolean solutionLegal, CentipawnEvaluator evaluator) {
        int rank = 1;
        for (int i = 0; i < legalMoves.size(); i++) {
            short move = legalMoves.raw(i);
            if (Move.equals(move, solution)) {
                continue;
            }
            int score = cheapMoveScore(root, move, evaluator);
            if (score > solutionCp + CHEAP_RANK_TOLERANCE_CP) {
                rank++;
            }
        }
        if (!solutionLegal) {
            rank = Math.max(rank, legalMoves.size());
        }
        return rank;
    }

    /**
     * Evaluates a root move from the root side's perspective.
     *
     * <p>
     * The child position is evaluated from the opponent's side-to-move
     * perspective, then negated back to the root player.
     * </p>
     *
     * @param root root position
     * @param move legal root move
     * @param evaluator cheap evaluator
     * @return root-perspective score after the move
     */
    private static int cheapMoveScore(Position root, short move, CentipawnEvaluator evaluator) {
        Position child = root.copy();
        child.play(move);
        return -evaluator.evaluate(child);
    }

    /**
     * Determines forcing and unusual key-move properties.
     *
     * <p>
     * The returned shape is used both for scoring and for human-readable feature
     * names.
     * </p>
     *
     * @param root root position
     * @param solution solution move
     * @param legalMoves legal root moves
     * @return key-shape summary
     */
    private static KeyShape keyShape(Position root, short solution, MoveList legalMoves) {
        if (solution == Move.NO_MOVE || !legalMoves.contains(solution)) {
            return new KeyShape(false, false, false, false, false, false, false, false);
        }
        boolean capture = root.isCapture(solution);
        boolean promotion = Move.isPromotion(solution);
        boolean underpromotion = Move.isUnderPromotion(solution);
        boolean castle = root.isCastle(solution);
        boolean enPassant = root.isEnPassantCapture(solution);
        Position child = root.copy();
        child.play(solution);
        boolean check = child.inCheck();
        boolean mate = child.isCheckmate();
        boolean quiet = !capture && !check;
        return new KeyShape(capture, promotion, underpromotion, castle, enPassant, check, mate, quiet);
    }

    /**
     * Converts a UCI evaluation to a centipawn-like value.
     *
     * <p>
     * Mate values are converted to large signed sentinels so mate and centipawn
     * scores can pass through the same arithmetic.
     * </p>
     *
     * <p>
     * <strong>Warning:</strong> converted mate values are ordering sentinels, not
     * literal centipawn evaluations.
     * </p>
     *
     * @param eval UCI evaluation
     * @return centipawn score
     */
    private static int evalAsCp(Evaluation eval) {
        if (eval == null || !eval.isValid()) {
            return 0;
        }
        if (!eval.isMate()) {
            return eval.getValue();
        }
        int sign = Integer.compare(eval.getValue(), 0);
        if (sign == 0) {
            return 0;
        }
        int distancePenalty = Math.min(999, Math.abs(eval.getValue()));
        return sign * (100_000 - distancePenalty);
    }

    /**
     * Scores root candidate ambiguity.
     *
     * <p>
     * Close PV1/PV2 margins and large legal move menus make the root choice more
     * ambiguous.
     * </p>
     *
     * @param marginCp PV1-PV2 margin
     * @param legalMoves number of legal moves
     * @return normalized score
     */
    private static double candidateAmbiguity(Integer marginCp, int legalMoves) {
        double margin = marginCp == null ? 0.20 : Numbers.clamp01(1.0 - Math.max(0, marginCp) / 300.0);
        double choice = Numbers.clamp01((legalMoves - 12) / 40.0);
        return Numbers.clamp01(0.80 * margin + 0.20 * choice);
    }

    /**
     * Scores how crowded the legal move menu is, independent of PV length.
     *
     * <p>
     * The score is normalized so ordinary move counts stay near zero while very
     * broad positions approach one.
     * </p>
     *
     * @param legalMoves number of legal root moves
     * @return normalized move-choice complexity
     */
    private static double moveChoiceComplexity(int legalMoves) {
        return Numbers.clamp01((legalMoves - 8) / 38.0);
    }

    /**
     * Adds continuous, position-derived texture to the root node score. This reduces
     * coarse feature-bundle plateaus by using real score/rank/mobility signals
     * instead of post-processing the histogram.
     *
     * <p>
     * The value is deliberately low-weighted. It breaks ties among similar
     * feature bundles without dominating the primary scoring signals.
     * </p>
     *
     * @param goal inferred puzzle objective
     * @param deepBestCp deep best score
     * @param deepMarginCp PV1-PV2 margin, if available
     * @param cheapStaticCp cheap static root score
     * @param cheapBestCp cheap one-ply best score
     * @param cheapSolutionCp cheap one-ply solution score
     * @param solutionRank cheap one-ply rank for the solution
     * @param legalMoves number of legal root moves
     * @return normalized fine-structure score
     */
    private static double fineNodeStructure(Goal goal, int deepBestCp, Integer deepMarginCp, int cheapStaticCp,
            int cheapBestCp, int cheapSolutionCp, int solutionRank, int legalMoves) {
        double rank = Numbers.clamp01((solutionRank - 1) / 9.0);
        double cheapGap = Numbers.clamp01((cheapBestCp - cheapSolutionCp) / 650.0);
        double staticGap;
        if (goal == Goal.DRAW) {
            staticGap = Numbers.clamp01((-cheapStaticCp) / 700.0);
        } else {
            staticGap = Numbers.clamp01((Math.min(Math.abs(deepBestCp), 1600) - Math.abs(cheapStaticCp)) / 1200.0);
        }
        double margin = deepMarginCp == null ? 0.20 : Numbers.clamp01(1.0 - Math.max(0, deepMarginCp) / 900.0);
        double mobility = Numbers.clamp01((legalMoves - 14) / 36.0);
        return Numbers.clamp01(0.30 * rank + 0.25 * cheapGap + 0.20 * staticGap
                + 0.15 * margin + 0.10 * mobility);
    }

    /**
     * Scores how hidden the deep truth is from the cheap evaluator.
     *
     * <p>
     * The score rises when the cheap evaluator does not statically see the
     * resource or actively prefers another one-ply move.
     * </p>
     *
     * @param goal puzzle goal
     * @param deepBestCp deep best score
     * @param cheapStaticCp cheap root static score
     * @param cheapBestCp cheap one-ply best score
     * @param cheapSolutionCp cheap one-ply solution score
     * @return normalized score
     */
    private static double hiddenness(Goal goal, int deepBestCp, int cheapStaticCp, int cheapBestCp,
            int cheapSolutionCp) {
        double staticHidden;
        if (goal == Goal.WIN) {
            staticHidden = Numbers.clamp01((WIN_CP - cheapStaticCp) / 700.0);
        } else if (goal == Goal.DRAW) {
            staticHidden = Numbers.clamp01((-100 - cheapStaticCp) / 700.0);
        } else {
            staticHidden = Numbers.clamp01((Math.min(deepBestCp, 1200) - cheapStaticCp) / 1000.0);
        }
        double moveHidden = Numbers.clamp01((cheapBestCp - cheapSolutionCp) / 500.0);
        return Math.max(staticHidden, moveHidden);
    }

    /**
     * Prevents direct mate-in-one puzzles from being inflated by hidden static eval.
     *
     * <p>
     * A one-move checkmate is usually easy even when a material evaluator ranks the
     * mating move poorly. The cap still leaves room for unusual mate keys, such as
     * underpromotions or moves the cheap evaluator misses badly.
     * </p>
     *
     * @param rawScore raw weighted feature score before direct-mate adjustment
     * @param key key move shape
     * @param solutionPlies number of plies in the main PV
     * @param weirdness normalized key-move weirdness score
     * @return adjusted raw score
     */
    private static double adjustForDirectMate(double rawScore, KeyShape key, int solutionPlies, double weirdness) {
        if (!key.mate || solutionPlies > 1) {
            return rawScore;
        }
        double cap = 0.14 + 0.06 * weirdness;
        if (key.underpromotion) {
            cap += 0.16;
        } else if (key.promotion) {
            cap += 0.04;
        }
        return Math.min(rawScore, Numbers.clamp01(cap));
    }

    /**
     * Converts raw feature evidence to direct normalized difficulty.
     *
     * <p>
     * This is intentionally local to the puzzle. It does not inspect the export
     * population, so exporting only hard puzzles preserves their hard ratings
     * instead of stretching that subset across the full scale.
     * </p>
     *
     * @param rawScore raw weighted feature score
     * @return direct normalized score
     */
    private static double directScore(double rawScore) {
        double score = Numbers.clamp01(rawScore);
        double easyBand = Numbers.clamp01((DIRECT_EASY_BAND_DAMP_END - score)
                / DIRECT_EASY_BAND_DAMP_WIDTH);
        double smoothEasyBand = smootherStep(easyBand);
        double easyDamp = DIRECT_EASY_BAND_DAMP_STRENGTH
                * smoothEasyBand
                * score
                * (1.0 - score);
        score = Numbers.clamp01(score - easyDamp);
        double band = Numbers.clamp01((score - DIRECT_MID_BAND_START) / DIRECT_MID_BAND_WIDTH);
        double smoothBand = smoothStep(band);
        double midLift = DIRECT_MID_BAND_LIFT
                * smoothBand
                * score
                * Math.pow(1.0 - score, DIRECT_MID_BAND_DECAY_EXPONENT);
        double lifted = Numbers.clamp01(score + midLift);
        double shoulderBand = Numbers.clamp01((lifted - DIRECT_UPPER_BODY_SHOULDER_START)
                / DIRECT_UPPER_BODY_SHOULDER_WIDTH);
        double smoothShoulderBand = smootherStep(shoulderBand);
        double shoulderDamp = DIRECT_UPPER_BODY_SHOULDER_STRENGTH
                * smoothShoulderBand
                * (lifted - DIRECT_UPPER_BODY_SHOULDER_START)
                * (1.0 - lifted);
        lifted = Numbers.clamp01(lifted - shoulderDamp);
        if (lifted > DIRECT_HARD_TAIL_COMPRESSION_START) {
            double excess = lifted - DIRECT_HARD_TAIL_COMPRESSION_START;
            double bandedCompression = smootherStep(Numbers.clamp01(excess / DIRECT_HARD_TAIL_COMPRESSION_WIDTH));
            double scale = 1.0 - (1.0 - DIRECT_HARD_TAIL_COMPRESSION_SCALE) * bandedCompression;
            lifted = DIRECT_HARD_TAIL_COMPRESSION_START
                    + excess * scale;
        }
        return Numbers.clamp01(lifted);
    }

    /**
     * Smoothly interpolates a normalized blend value.
     *
     * <p>
     * The quintic smootherstep has zero first and second derivative at both
     * endpoints, which avoids visible slope changes where curve bands begin or
     * end.
     * </p>
     *
     * @param x normalized blend value
     * @return eased blend value in {@code [0,1]}
     */
    private static double smootherStep(double x) {
        double t = Numbers.clamp01(x);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /**
     * Smoothly interpolates a normalized mid-band lift value.
     *
     * <p>
     * The cubic smoothstep preserves the established mid-band curve shape while
     * still easing in and out at the band edges.
     * </p>
     *
     * @param x normalized blend value
     * @return eased blend value in {@code [0,1]}
     */
    private static double smoothStep(double x) {
        double t = Numbers.clamp01(x);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Converts normalized tree difficulty to the direct Elo-like scale.
     *
     * <p>
     * The base rating uses a convex direct feature curve over the standard span.
     * Extremely broad explicit trees can add a small logarithmic tail above the
     * standard range.
     * </p>
     *
     * @param score normalized tree difficulty score
     * @param tree explicit continuation summary
     * @param ordinaryTreeScale multiplier for ordinary tree evidence
     * @return direct Elo-like rating
     */
    private static int ratingForTreeScore(double score, PuzzleTreeSummary tree, double ordinaryTreeScale) {
        int rating = RATING_FLOOR + (int) Math.round(STANDARD_RATING_SPAN * Numbers.clamp01(score));
        double depthTail = Math.log1p(Math.max(0.0, tree.continuationDepthLoad - 0.25))
                * TREE_DEPTH_RATING_TAIL;
        double lineTail = Math.log1p(Math.max(0, explicitTreePlies(tree) - 3)) * TREE_LONG_LINE_RATING_TAIL;
        double branchTail = Math.log1p(Math.max(0.0, tree.branchLoad)) * TREE_BRANCH_RATING_TAIL;
        double rootReplyTail = Math.log1p(Math.max(0, tree.rootReplyCount - 1)) * TREE_ROOT_REPLY_RATING_TAIL;
        double breadthTail = Math.log1p(Math.max(0, tree.nodeCount - 12)) * TREE_NODE_RATING_TAIL;
        double specialTail = treeSpecialMoveScore(tree) * TREE_SPECIAL_RATING_TAIL;
        double ordinaryTail = depthTail + lineTail + branchTail + rootReplyTail + breadthTail;
        int directRating = rating + (int) Math.round(ordinaryTreeScale * ordinaryTail + specialTail);
        return Math.max(RATING_FLOOR, Math.min(RATING_CEILING, directRating));
    }

    /**
     * Adds deterministic per-position spread to direct ratings.
     *
     * @param rating direct rating before jitter
     * @param signature position signature
     * @return rating with bounded deterministic jitter
     */
    private static int addDirectRatingJitter(int rating, long signature) {
        double unit = deterministicUnit(signature);
        double half = DIRECT_RATING_JITTER_WIDTH / 2.0;
        int jitter;
        if (rating <= RATING_FLOOR + half) {
            jitter = (int) Math.round(unit * DIRECT_RATING_JITTER_WIDTH);
        } else if (rating >= RATING_CEILING - half) {
            jitter = -(int) Math.round(unit * DIRECT_RATING_JITTER_WIDTH);
        } else {
            jitter = (int) Math.round(unit * DIRECT_RATING_JITTER_WIDTH - half);
        }
        return Math.max(RATING_FLOOR, Math.min(RATING_CEILING, rating + jitter));
    }

    /**
     * Returns a deterministic unit value for a long key.
     *
     * @param value source value
     * @return value in {@code [0,1)}
     */
    private static double deterministicUnit(long value) {
        long x = value + 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return ((x >>> 11) & ((1L << 53) - 1)) * 0x1.0p-53;
    }

    /**
     * Converts a direct rating back to the normalized standard scorer scale.
     *
     * @param rating direct Elo-like rating
     * @return normalized score used for labels and metadata
     */
    private static double scoreForRating(int rating) {
        return Numbers.clamp01((rating - RATING_FLOOR) / (double) STANDARD_RATING_SPAN);
    }

    /**
     * Returns explicit continuation plies from solver-depth evidence only.
     *
     * <p>
     * Solver depth counts solver moves; exported plies include alternating
     * opponent replies, so depth {@code 1} becomes one ply and depth {@code 2}
     * becomes three plies.
     * </p>
     *
     * @param tree explicit continuation summary
     * @return explicit solution plies
     */
    private static int explicitTreePlies(PuzzleTreeSummary tree) {
        return Math.max(1, tree.maxSolverDepth * 2 - 1);
    }

    /**
     * Harmonic continuation burden across every explicit non-root solver node,
     * normalized to a 12-solver-move line. This makes a continuation count by its
     * depth and presence in the extracted tree, not by whether it is the longest
     * line.
     *
     * <p>
     * Harmonic depth gives diminishing extra weight to very deep continuations.
     * </p>
     *
     * @param tree explicit continuation summary
     * @return normalized continuation burden
     */
    private static double treeContinuationBurden(PuzzleTreeSummary tree) {
        if (tree.continuationDepthLoad <= 0.0) {
            return 0.0;
        }
        return Numbers.clamp01(tree.continuationDepthLoad / (harmonic(12) - 1.0));
    }

    /**
     * Harmonic number H_n.
     *
     * <p>
     * Used to normalize explicit continuation depth loads.
     * </p>
     *
     * @param n number of terms
     * @return harmonic sum through {@code n}
     */
    private static double harmonic(int n) {
        double sum = 0.0;
        for (int i = 1; i <= Math.max(1, n); i++) {
            sum += 1.0 / i;
        }
        return sum;
    }

    /**
     * Scores explicit opponent-reply branching.
     *
     * <p>
     * Root reply count contributes lightly. Branches spread across several
     * calculation points carry more weight than one large fanout, because a
     * single node with many similar replies is usually easier for a human to
     * chunk than several separate decision points.
     * </p>
     *
     * @param tree explicit continuation summary
     * @return normalized variation complexity
     */
    private static double treeVariationComplexity(PuzzleTreeSummary tree) {
        double root = Numbers.clamp01(
                Math.log1p(Math.max(0, tree.rootReplyCount - 1)) / Math.log1p(TREE_ROOT_REPLY_FULL_SIGNAL));
        double branches = Numbers.clamp01(tree.branchLoad / TREE_BRANCH_LOAD_FULL_SIGNAL);
        double spread = Numbers.clamp01(Math.log1p(Math.max(0, tree.branchPointCount))
                / Math.log1p(TREE_BRANCH_POINT_FULL_SIGNAL));
        double distributedBranches = branches * (0.35 + 0.65 * spread);
        return Numbers.clamp01(0.25 * root + 0.75 * distributedBranches);
    }

    /**
     * Scores extreme explicit tree size for corpus-tail ordering.
     *
     * <p>
     * This signal is separate from the ordinary continuation and variation scores
     * because it is meant to break the hard-tail saturation case: a long or broad
     * source-backed puzzle should sit above a shallow puzzle with a difficult root
     * move when both otherwise reach the top of the raw scale.
     * </p>
     *
     * @param tree explicit continuation summary
     * @return normalized extreme tree-shape score
     */
    private static double treeComplexityTail(PuzzleTreeSummary tree) {
        double plies = Numbers.clamp01((explicitTreePlies(tree) - 1.0)
                / Math.max(1.0, TREE_LONG_PLY_FULL_SIGNAL - 1.0));
        double nodes = Numbers.clamp01(Math.log1p(Math.max(0, tree.nodeCount - 1))
                / Math.log1p(Math.max(1.0, TREE_NODE_FULL_SIGNAL - 1.0)));
        double branches = Numbers.clamp01(Math.log1p(Math.max(0, tree.branchPointCount))
                / Math.log1p(TREE_BRANCH_POINT_FULL_SIGNAL));
        double replies = Numbers.clamp01(Math.log1p(Math.max(0, tree.rootReplyCount - 1))
                / Math.log1p(Math.max(1.0, TREE_ROOT_REPLY_FULL_SIGNAL - 1.0)));
        double diversity = treePieceDiversity(tree);
        double moveVariety = treeMoveVariety(tree);
        return Numbers.clamp01(0.30 * plies + 0.16 * nodes + 0.10 * branches + 0.30 * diversity
                + 0.08 * replies + 0.06 * moveVariety);
    }

    /**
     * Scores piece-type and stable moving-piece diversity.
     *
     * <p>
     * Requiring several piece types or several distinct tracked pieces increases
     * the memorization burden of a multi-move puzzle.
     * </p>
     *
     * @param tree explicit continuation summary
     * @return normalized diversity score
     */
    private static double treePieceDiversity(PuzzleTreeSummary tree) {
        double typeDiversity = Numbers.clamp01(Math.max(0, tree.pieceTypeCount - 1) / 4.0);
        double tacticalTypeDiversity = Numbers.clamp01(tree.nonPawnNonKingPieceTypeCount / 4.0);
        double pieceDiversity = Numbers.clamp01(Math.max(0, tree.pieceIdentityCount - 1) / 7.0);
        double moveVariety = treeMoveVariety(tree);
        double raw = 0.35 * typeDiversity + 0.40 * tacticalTypeDiversity + 0.25 * pieceDiversity;
        return Numbers.clamp01(raw * (0.70 + 0.30 * moveVariety));
    }

    /**
     * Scores how much ordinary explicit-tree evidence should count.
     *
     * <p>
     * This prevents repeated technical endgame trees from outranking richer
     * tactical trees purely because they contain many source-backed nodes. Rare
     * move forms are scored separately and are not dampened by this multiplier.
     * </p>
     *
     * @param node root node score
     * @param tree explicit continuation summary
     * @return multiplier for ordinary continuation, branch, and breadth evidence
     */
    private static double ordinaryTreeEvidenceScale(NodeScore node, PuzzleTreeSummary tree) {
        double material = Numbers.clamp01(node.rootNonKingMaterialCp / TREE_MATERIAL_RICHNESS_CP);
        double nonPawnPieces = Numbers.clamp01(node.rootNonPawnPieceCount / 6.0);
        double tacticalTypes = Numbers.clamp01(tree.nonPawnNonKingPieceTypeCount / 3.0);
        double moveVariety = treeMoveVariety(tree);
        double legalChoice = Numbers.clamp01((node.legalMoveCount - 5) / 25.0);
        double cheapRank = Numbers.clamp01((node.solutionRankByCheap - 1) / 6.0);
        double richness = Numbers.clamp01(0.22 * material
                + 0.20 * nonPawnPieces
                + 0.22 * tacticalTypes
                + 0.18 * moveVariety
                + 0.10 * legalChoice
                + 0.08 * cheapRank);
        return ORDINARY_TREE_EVIDENCE_MIN_SCALE
                + (1.0 - ORDINARY_TREE_EVIDENCE_MIN_SCALE) * richness;
    }

    /**
     * Scores moving-piece variety inside the explicit tree.
     *
     * <p>
     * A tree where one tracked piece makes almost every solver move is easier to
     * chunk than a tree requiring several independent piece decisions.
     * </p>
     *
     * @param tree explicit continuation summary
     * @return normalized moving-piece variety
     */
    private static double treeMoveVariety(PuzzleTreeSummary tree) {
        if (tree.nodeCount <= 1) {
            return 0.0;
        }
        return Numbers.clamp01(1.0 - tree.dominantPieceMoveShare);
    }

    /**
     * Scores rare legal move forms in the explicit solution tree.
     *
     * <p>
     * Underpromotion, en-passant, and castling are uncommon in puzzle solution
     * trees, so they add a bounded special-move score.
     * </p>
     *
     * @param tree explicit continuation summary
     * @return normalized special-move score
     */
    private static double treeSpecialMoveScore(PuzzleTreeSummary tree) {
        double score = 0.0;
        if (tree.underpromotionCount > 0) {
            score += TREE_UNDERPROMOTION_SIGNAL;
        }
        if (tree.enPassantCount > 0) {
            score += TREE_EN_PASSANT_SIGNAL;
        }
        if (tree.castleCount > 0) {
            score += TREE_CASTLE_SIGNAL;
        }
        return Numbers.clamp01(score);
    }

    /**
     * Scores how often the required solver moves are non-checking non-captures.
     *
     * <p>
     * Quiet required moves are usually less forcing and therefore receive a
     * small difficulty contribution.
     * </p>
     *
     * @param tree explicit continuation summary
     * @return normalized nonforcing-move score
     */
    private static double treeNonforcingScore(PuzzleTreeSummary tree) {
        if (tree.nodeCount <= 0) {
            return 0.0;
        }
        return Numbers.clamp01(tree.nonforcingMoveCount / (double) tree.nodeCount);
    }

    /**
     * Clamps a value into an arbitrary inclusive range.
     *
     * @param value input value
     * @param min minimum value
     * @param max maximum value
     * @return clamped value
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Caps direct mate-in-one scores.
     *
     * <p>
     * Direct mates should not be promoted into hard buckets merely because the
     * cheap evaluator fails to see the mate.
     * </p>
     *
     * @param score score before cap
     * @param key key move shape
     * @param solutionPlies explicit solution plies
     * @return capped score for direct mates, otherwise the original score
     */
    private static double capDirectMateScore(double score, KeyShape key, int solutionPlies) {
        if (!key.mate || solutionPlies > 1) {
            return score;
        }
        return Math.min(score, 0.40);
    }

    /**
     * Caps direct mate-in-one ratings.
     *
     * <p>
     * The rating cap mirrors {@link #capDirectMateScore(double, KeyShape, int)}
     * so display ratings remain aligned with labels.
     * </p>
     *
     * @param rating rating before cap
     * @param key key move shape
     * @param solutionPlies explicit solution plies
     * @return capped rating for direct mates, otherwise the original rating
     */
    private static int capDirectMateRating(int rating, KeyShape key, int solutionPlies) {
        if (!key.mate || solutionPlies > 1) {
            return rating;
        }
        return Math.min(rating, 1480);
    }

    /**
     * Feature-derived local spread for tree-scored puzzles.
     *
     * <p>
     * The result is centered around zero and later scaled by
     * {@link #FEATURE_TIE_SPREAD}. It is intended only to break repeated
     * feature-bundle ties.
     * </p>
     *
     * @param node root node score
     * @param tree explicit continuation summary
     * @return signed spread value in approximately {@code [-0.5, 0.5]}
     */
    private static double featureTieSpread(NodeScore node, PuzzleTreeSummary tree) {
        double rank = Numbers.clamp01((node.solutionRankByCheap - 1) / 12.0);
        double legal = Numbers.clamp01((node.legalMoveCount - 18) / 42.0);
        double margin = node.deepMarginCp == null ? 0.25
                : Numbers.clamp01(1.0 - Math.max(0, node.deepMarginCp) / 1000.0);
        double treeShape = Numbers.clamp01(0.36 * treeContinuationBurden(tree)
                + 0.18 * treeVariationComplexity(tree)
                + 0.18 * treeComplexityTail(tree)
                + 0.23 * treePieceDiversity(tree)
                + 0.05 * treeNonforcingScore(tree));
        double special = treeSpecialMoveScore(tree);
        double unit = Numbers.clamp01(0.25 * rank + 0.20 * legal + 0.20 * margin
                + 0.25 * treeShape + 0.10 * special);
        return unit - 0.5;
    }

    /**
     * Scores cognitive load for saturated hard-tail protection.
     *
     * <p>
     * This protects genuinely hard explicit trees from being demoted by the
     * small feature tie spread. Long calculation length and diverse moving pieces
     * carry most of the signal; discounted variation and broad tree shape
     * contribute only after those primary human-load features are present.
     * </p>
     *
     * @param tree explicit continuation summary
     * @return normalized cognitive-load score
     */
    private static double hardTailCognitiveLoad(PuzzleTreeSummary tree) {
        return Numbers.clamp01(0.42 * treeContinuationBurden(tree)
                + 0.30 * treePieceDiversity(tree)
                + 0.15 * treeComplexityTail(tree)
                + 0.08 * treeVariationComplexity(tree)
                + 0.05 * treeMoveVariety(tree));
    }

    /**
     * Scores move shape rarity and cheap-eval concessions.
     *
     * <p>
     * Quiet moves, rare move forms, and material-looking concessions raise the
     * score. Checking, mating, and capturing keys reduce it because they are
     * usually easier forcing signals.
     * </p>
     *
     * @param key key move shape
     * @param cheapStaticCp cheap static root score
     * @param cheapSolutionCp cheap one-ply solution score
     * @return normalized score
     */
    private static double moveWeirdness(KeyShape key, int cheapStaticCp, int cheapSolutionCp) {
        double score = 0.0;
        if (key.quiet) {
            score += 0.35;
        }
        if (key.underpromotion) {
            score += KEY_UNDERPROMOTION_WEIRDNESS;
        } else if (key.promotion) {
            score += 0.18;
        }
        if (key.enPassant) {
            score += KEY_EN_PASSANT_WEIRDNESS;
        }
        if (key.castle) {
            score += KEY_CASTLE_WEIRDNESS;
        }
        if (cheapSolutionCp < cheapStaticCp - 150) {
            score += 0.30;
        }
        if (key.mate) {
            score -= 0.30;
        } else if (key.check) {
            score -= 0.18;
        }
        if (key.capture) {
            score -= 0.12;
        }
        return Numbers.clamp01(score);
    }

    /**
     * Scores whether the key move is forcing.
     *
     * <p>
     * Non-checking, non-capturing keys are scored as nonforcing.
     * </p>
     *
     * @param key key move shape
     * @return normalized score
     */
    private static double tacticalNonforcing(KeyShape key) {
        return !key.check && !key.capture ? 1.0 : 0.0;
    }

    /**
     * Converts normalized score to a label.
     *
     * @param score normalized difficulty score
     * @return difficulty label
     */
    private static String labelFor(double score) {
        if (score <= 0.20) {
            return "very_easy";
        }
        if (score <= 0.35) {
            return "easy";
        }
        if (score <= 0.55) {
            return "medium";
        }
        if (score <= 0.70) {
            return "hard";
        }
        return "very_hard";
    }

    /**
     * Builds a compact list of high-signal feature names.
     *
     * <p>
     * The feature list is explanatory metadata, not a complete feature vector.
     * It favors stable names that are useful in CSV exports and tags.
     * </p>
     *
     * @param signals scalar scoring signals
     * @param tree explicit continuation summary
     * @param key key move shape
     * @param cheap cheap visibility values
     * @return feature names
     */
    private static List<String> featureNames(FeatureSignals signals, PuzzleTreeSummary tree, KeyShape key,
            CheapVisibility cheap) {
        List<String> names = new ArrayList<>();
        addGoalFeature(names, signals.goal);
        addSignalFeatures(names, signals, tree);
        addKeyFeatures(names, key);
        addCheapVisibilityFeature(names, cheap);
        if (names.isEmpty()) {
            names.add("forcing_key");
        }
        return List.copyOf(names);
    }

    /**
     * Adds the puzzle-goal feature name.
     *
     * <p>
     * Winning puzzles are the default and do not receive an extra feature name.
     * </p>
     *
     * @param names mutable feature-name list
     * @param goal inferred puzzle goal
     */
    private static void addGoalFeature(List<String> names, Goal goal) {
        if (goal == Goal.DRAW) {
            names.add("draw_resource");
        } else if (goal == Goal.UNKNOWN) {
            names.add("uncertain_goal");
        }
    }

    /**
     * Adds scalar difficulty signal names.
     *
     * <p>
     * Names are added only when a signal crosses a threshold that should be
     * visible to report readers.
     * </p>
     *
     * @param names mutable feature-name list
     * @param signals scalar scoring signals
     * @param tree explicit continuation summary
     */
    private static void addSignalFeatures(List<String> names, FeatureSignals signals, PuzzleTreeSummary tree) {
        if (signals.node > 0.45) {
            names.add("ambiguous_candidates");
        }
        if (signals.node > 0.60) {
            names.add("hidden_eval");
        }
        if (signals.length > 0.35) {
            names.add("multi_move");
        }
        if (signals.variation > 0.30 || tree.branchPointCount > 0) {
            names.add("branching");
        }
        if (signals.diversity > 0.25) {
            names.add("piece_diversity");
        }
        if (tree.pieceIdentityCount > 1) {
            names.add("multiple_pieces");
        }
        if (signals.special > 0.0) {
            names.add("special_move");
        }
        if (signals.nonforcing > 0.75) {
            names.add("nonforcing");
        }
    }

    /**
     * Adds key-move shape names.
     *
     * <p>
     * These names describe properties of the first required solver move.
     * </p>
     *
     * @param names mutable feature-name list
     * @param key key move shape
     */
    private static void addKeyFeatures(List<String> names, KeyShape key) {
        if (key.quiet) {
            names.add("quiet_key");
        }
        if (key.underpromotion) {
            names.add("underpromotion");
        } else if (key.promotion) {
            names.add("promotion");
        }
        if (key.enPassant) {
            names.add("en_passant");
        }
        if (key.castle) {
            names.add("castling");
        }
        if (key.mate) {
            names.add("mate_key");
        }
    }

    /**
     * Adds cheap-evaluator visibility names.
     *
     * <p>
     * A large cheap static-to-solution drop is treated as a possible sacrifice
     * or temporary concession.
     * </p>
     *
     * @param names mutable feature-name list
     * @param cheap cheap visibility values
     */
    private static void addCheapVisibilityFeature(List<String> names, CheapVisibility cheap) {
        if (cheap.solutionCp < cheap.staticCp - 150) {
            names.add("sacrifice_or_concession");
        }
    }

    /**
     * Formats a move safely.
     *
     * <p>
     * Invalid or sentinel moves are converted to {@code 0000} instead of
     * propagating formatting exceptions into exports.
     * </p>
     *
     * @param move encoded move
     * @return UCI move text
     */
    private static String moveToString(short move) {
        try {
            return Move.toString(move);
        } catch (RuntimeException ex) {
            return "0000";
        }
    }

    /**
     * Formats the solution move as SAN when available.
     *
     * <p>
     * The method falls back to UCI text when SAN conversion is unavailable for
     * the supplied position.
     * </p>
     *
     * @param root root position
     * @param move solution move
     * @return SAN or UCI fallback
     */
    public static String solutionSan(Position root, short move) {
        if (root == null || move == Move.NO_MOVE) {
            return "0000";
        }
        try {
            return SAN.toAlgebraic(root, move);
        } catch (RuntimeException ex) {
            return moveToString(move);
        }
    }

    /**
     * Summarizes non-king root material for tree-evidence scaling.
     *
     * @param position position to inspect
     * @return root material summary
     */
    private static RootMaterial rootMaterial(Position position) {
        int pawns = pieceCount(position, Position.WHITE_PAWN) + pieceCount(position, Position.BLACK_PAWN);
        int knights = pieceCount(position, Position.WHITE_KNIGHT) + pieceCount(position, Position.BLACK_KNIGHT);
        int bishops = pieceCount(position, Position.WHITE_BISHOP) + pieceCount(position, Position.BLACK_BISHOP);
        int rooks = pieceCount(position, Position.WHITE_ROOK) + pieceCount(position, Position.BLACK_ROOK);
        int queens = pieceCount(position, Position.WHITE_QUEEN) + pieceCount(position, Position.BLACK_QUEEN);
        int nonPawnPieces = knights + bishops + rooks + queens;
        int nonKingPieces = pawns + nonPawnPieces;
        int material = pawns * Piece.VALUE_PAWN
                + knights * Piece.VALUE_KNIGHT
                + bishops * Piece.VALUE_BISHOP
                + rooks * Piece.VALUE_ROOK
                + queens * Piece.VALUE_QUEEN;
        return new RootMaterial(nonKingPieces, nonPawnPieces, material);
    }

    /**
     * Counts pieces for one position piece index.
     *
     * @param position position to inspect
     * @param pieceIndex position piece index
     * @return number of pieces of that indexed type
     */
    private static int pieceCount(Position position, int pieceIndex) {
        return Long.bitCount(position.pieces(pieceIndex));
    }

    /**
     * Minimal material/tempo evaluator used when the full classical evaluator is not
     * on the classpath.
     *
     * <p>
     * This fallback keeps puzzle-difficulty tooling available in stripped-down
     * builds. It is intentionally simple and used only for visibility estimates.
     * </p>
     *
     * <p>
     * <strong>Warning:</strong> this evaluator is not a replacement for the full
     * classical evaluator; it only provides deterministic material ordering.
     * </p>
     */
    private static final class MaterialVisibilityEvaluator implements CentipawnEvaluator {

        /**
         * Restricts fallback evaluator construction to the scorer.
         *
         * <p>
         * Instances are created only by {@link Scorer#defaultCheapEvaluator()}.
         * </p>
         */
        private MaterialVisibilityEvaluator() {
            // fallback helper
        }

        /**
         * Evaluates material from the side-to-move perspective.
         *
         * <p>
         * A small tempo hint and bishop-pair bonus are included to avoid fully
         * flat material scores in simple positions.
         * </p>
         *
         * @param position position to evaluate
         * @return centipawn estimate
         */
        @Override
        public int evaluate(Position position) {
            int white = sideMaterial(position, true);
            int black = sideMaterial(position, false);
            int whitePerspective = white - black + (position.isWhiteToMove() ? 8 : -8);
            return position.isWhiteToMove() ? whitePerspective : -whitePerspective;
        }

        /**
         * Scores material and a bishop-pair hint for one side.
         *
         * <p>
         * The returned value is always from the requested side's material
         * perspective, independent of side to move.
         * </p>
         *
         * @param position position to inspect
         * @param white    side to score
         * @return material score
         */
        private static int sideMaterial(Position position, boolean white) {
            int pawnIndex = white ? Position.WHITE_PAWN : Position.BLACK_PAWN;
            int knightIndex = white ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT;
            int bishopIndex = white ? Position.WHITE_BISHOP : Position.BLACK_BISHOP;
            int rookIndex = white ? Position.WHITE_ROOK : Position.BLACK_ROOK;
            int queenIndex = white ? Position.WHITE_QUEEN : Position.BLACK_QUEEN;
            int bishops = Long.bitCount(position.pieces(bishopIndex));
            return Long.bitCount(position.pieces(pawnIndex)) * Piece.VALUE_PAWN
                    + Long.bitCount(position.pieces(knightIndex)) * Piece.VALUE_KNIGHT
                    + bishops * Piece.VALUE_BISHOP
                    + Long.bitCount(position.pieces(rookIndex)) * Piece.VALUE_ROOK
                    + Long.bitCount(position.pieces(queenIndex)) * Piece.VALUE_QUEEN
                    + (bishops >= 2 ? 30 : 0);
        }
    }

    /**
     * Root material summary used by tree-evidence heuristics.
     *
     * <p>
     * Kings are excluded because they do not reflect tactical material richness.
     * </p>
     *
     * @param nonKingPieceCount number of non-king pieces on the board
     * @param nonPawnPieceCount number of non-pawn, non-king pieces on the board
     * @param nonKingMaterialCp material value of all non-king pieces
     */
    private record RootMaterial(int nonKingPieceCount, int nonPawnPieceCount, int nonKingMaterialCp) {

        /**
         * Normalizes material counters.
         *
         * @param nonKingPieceCount number of non-king pieces on the board
         * @param nonPawnPieceCount number of non-pawn, non-king pieces on the board
         * @param nonKingMaterialCp material value of all non-king pieces
         */
        private RootMaterial {
            nonKingPieceCount = Math.max(0, nonKingPieceCount);
            nonPawnPieceCount = Math.max(0, nonPawnPieceCount);
            nonKingMaterialCp = Math.max(0, nonKingMaterialCp);
        }
    }

    /**
     * Cheap move summary.
     *
     * <p>
     * The summary is the normalized view of cheap-evaluator root visibility
     * after fallback defaults are applied.
     * </p>
     *
     * @param staticCp static root score
     * @param bestMove cheap best move
     * @param bestCp cheap best score
     * @param solutionCp cheap solution score
     * @param solutionRank cheap solution rank
     */
    private record CheapMoveSummary(int staticCp, short bestMove, int bestCp, int solutionCp, int solutionRank) {
    }

    /**
     * Cheap move search result before fallback defaults are applied.
     *
     * <p>
     * Sentinel scores indicate that the legal move loop did not produce a value
     * for that field.
     * </p>
     *
     * @param bestMove cheap best move
     * @param bestCp cheap best score, or {@link Integer#MIN_VALUE}
     * @param solutionCp solution score, or {@link Integer#MIN_VALUE}
     */
    private record CheapMoveScores(short bestMove, int bestCp, int solutionCp) {
    }

    /**
     * Per-position difficulty signals for one solver-to-move record.
     *
     * <p>
     * This record captures only the root position. It does not include
     * continuation-tree evidence; that is supplied separately through
     * {@link PuzzleTreeSummary}.
     * </p>
     *
     * <p>
     * <strong>Warning:</strong> centipawn fields are from the analyzed side to
     * move. Converted mate values are large ordering sentinels, not literal
     * material scores.
     * </p>
     *
     * @param positionSignature core signature of the analyzed position
     * @param goal inferred objective
     * @param rawScore per-position raw difficulty before continuation bonuses
     * @param solutionMoveUci best move in UCI notation
     * @param solutionMoveHash deterministic hash of the best move
     * @param deepBestCp deep PV1 score from the side to move
     * @param deepSecondCp deep PV2 score from the side to move, if available
     * @param deepMarginCp PV1 minus PV2 in centipawns, if available
     * @param cheapStaticCp static cheap score from the side to move
     * @param cheapBestMoveUci one-ply cheap preferred move
     * @param cheapBestCp one-ply cheap best score
     * @param cheapSolutionCp one-ply cheap solution score
     * @param solutionRankByCheap one-ply cheap rank, retained for diagnostics only
     * @param legalMoveCount number of legal moves
     * @param keyCheck whether the key checks
     * @param keyMate whether the key mates
     * @param keyCapture whether the key captures
     * @param keyPromotion whether the key promotes
     * @param keyUnderpromotion whether the key underpromotes
     * @param keyCastle whether the key castles
     * @param keyEnPassant whether the key captures en-passant
     * @param keyQuiet whether the key is non-checking and non-capturing
     * @param pieceType absolute type of the moving piece
     * @param rootNonKingPieceCount non-king piece count in the root position
     * @param rootNonPawnPieceCount non-pawn, non-king piece count in the root position
     * @param rootNonKingMaterialCp non-king material value in the root position
     * @param fromSquare encoded source square, or -1
     * @param toSquare actual target square, or -1
     */
    public record NodeScore(
            long positionSignature,
            Goal goal,
            double rawScore,
            String solutionMoveUci,
            int solutionMoveHash,
            int deepBestCp,
            Integer deepSecondCp,
            Integer deepMarginCp,
            int cheapStaticCp,
            String cheapBestMoveUci,
            int cheapBestCp,
            int cheapSolutionCp,
            int solutionRankByCheap,
            int legalMoveCount,
            boolean keyCheck,
            boolean keyMate,
            boolean keyCapture,
            boolean keyPromotion,
            boolean keyUnderpromotion,
            boolean keyCastle,
            boolean keyEnPassant,
            boolean keyQuiet,
            int pieceType,
            int rootNonKingPieceCount,
            int rootNonPawnPieceCount,
            int rootNonKingMaterialCp,
            int fromSquare,
            int toSquare) {

        /**
         * Normalizes nullable/scalar inputs.
         *
         * <p>
         * The constructor keeps invalid text fields exportable and clamps
         * feature-like scalar values into their supported ranges.
         * </p>
         *
         * @param positionSignature core signature of the analyzed position
         * @param goal inferred objective
         * @param rawScore per-position raw difficulty before continuation bonuses
         * @param solutionMoveUci best move in UCI notation
         * @param solutionMoveHash deterministic hash of the best move
         * @param deepBestCp deep PV1 score from the side to move
         * @param deepSecondCp deep PV2 score from the side to move, if available
         * @param deepMarginCp PV1 minus PV2 in centipawns, if available
         * @param cheapStaticCp static cheap score from the side to move
         * @param cheapBestMoveUci one-ply cheap preferred move
         * @param cheapBestCp one-ply cheap best score
         * @param cheapSolutionCp one-ply cheap solution score
         * @param solutionRankByCheap one-ply cheap rank for the solution
         * @param legalMoveCount number of legal moves
         * @param keyCheck whether the key checks
         * @param keyMate whether the key mates
         * @param keyCapture whether the key captures
         * @param keyPromotion whether the key promotes
         * @param keyUnderpromotion whether the key underpromotes
         * @param keyCastle whether the key castles
         * @param keyEnPassant whether the key captures en-passant
         * @param keyQuiet whether the key is non-checking and non-capturing
         * @param pieceType absolute type of the moving piece
         * @param rootNonKingPieceCount non-king piece count in the root position
         * @param rootNonPawnPieceCount non-pawn, non-king piece count in the root position
         * @param rootNonKingMaterialCp non-king material value in the root position
         * @param fromSquare encoded source square, or -1
         * @param toSquare actual target square, or -1
         */
        public NodeScore {
            goal = goal == null ? Goal.UNKNOWN : goal;
            rawScore = Numbers.clamp01(rawScore);
            solutionMoveUci = solutionMoveUci == null ? "0000" : solutionMoveUci;
            cheapBestMoveUci = cheapBestMoveUci == null ? "0000" : cheapBestMoveUci;
            pieceType = Math.max(0, Math.min(Piece.KING, pieceType));
            rootNonKingPieceCount = Math.max(0, rootNonKingPieceCount);
            rootNonPawnPieceCount = Math.max(0, rootNonPawnPieceCount);
            rootNonKingMaterialCp = Math.max(0, rootNonKingMaterialCp);
        }

        /**
         * Recreates the compact key-shape record.
         *
         * <p>
         * This is used internally so later scoring phases do not have to carry
         * the individual key-shape booleans separately.
         * </p>
         *
         * @return key-shape summary
         */
        private KeyShape keyShape() {
            return new KeyShape(keyCapture, keyPromotion, keyUnderpromotion, keyCastle, keyEnPassant,
                    keyCheck, keyMate, keyQuiet);
        }
    }

    /**
     * Explicit continuation-tree summary measured from linked puzzle records.
     *
     * <p>
     * The summary intentionally stores aggregate evidence from explicit puzzle
     * records rather than trusting unverified engine PV length.
     * </p>
     *
     * <p>
     * <strong>Warning:</strong> all depth and branch values are evidence values.
     * Supplying inflated values will inflate difficulty estimates.
     * </p>
     *
     * @param nodeCount solver-to-move positions in the tree, including the root
     * @param rootReplyCount analyzed opponent replies immediately after the root key
     * @param branchPointCount opponent-reply positions with more than one child
     * @param maxSolverDepth deepest solver move count in the tree, root = 1
     * @param continuationNodeScore harmonic average of non-root node difficulty
     * @param continuationDepthLoad harmonic depth mass over every non-root node
     * @param branchLoad sublinear branch burden across the tree
     * @param pieceTypeCount distinct moving piece types across solver moves
     * @param nonPawnNonKingPieceTypeCount distinct non-pawn, non-king moving piece
     *        types across solver moves
     * @param pieceIdentityCount stable distinct moving pieces across solver moves
     * @param pawnMoveCount solver moves made by pawns
     * @param kingMoveCount solver moves made by kings
     * @param dominantPieceMoveShare largest share of solver moves made by one
     *        tracked piece
     * @param nonforcingMoveCount solver moves that do not check or capture
     * @param underpromotionCount underpromotion solver moves
     * @param enPassantCount en-passant solver moves
     * @param castleCount castling solver moves
     */
    public record PuzzleTreeSummary(
            int nodeCount,
            int rootReplyCount,
            int branchPointCount,
            int maxSolverDepth,
            double continuationNodeScore,
            double continuationDepthLoad,
            double branchLoad,
            int pieceTypeCount,
            int nonPawnNonKingPieceTypeCount,
            int pieceIdentityCount,
            int pawnMoveCount,
            int kingMoveCount,
            double dominantPieceMoveShare,
            int nonforcingMoveCount,
            int underpromotionCount,
            int enPassantCount,
            int castleCount) {

        /**
         * Normalizes numeric fields.
         *
         * <p>
         * Counts are floored to valid ranges, and normalized scalar inputs are
         * clamped so malformed summaries cannot produce negative burdens.
         * </p>
         *
         * @param nodeCount solver-to-move positions in the tree, including the root
         * @param rootReplyCount analyzed opponent replies immediately after the root key
         * @param branchPointCount opponent-reply positions with more than one child
         * @param maxSolverDepth deepest solver move count in the tree, root = 1
         * @param continuationNodeScore harmonic average of non-root node difficulty
         * @param continuationDepthLoad harmonic depth mass over every non-root node
         * @param branchLoad sublinear branch burden across the tree
         * @param pieceTypeCount distinct moving piece types across solver moves
         * @param nonPawnNonKingPieceTypeCount distinct non-pawn, non-king moving
         *        piece types across solver moves
         * @param pieceIdentityCount stable distinct moving pieces across solver moves
         * @param pawnMoveCount solver moves made by pawns
         * @param kingMoveCount solver moves made by kings
         * @param dominantPieceMoveShare largest share of solver moves made by one
         *        tracked piece
         * @param nonforcingMoveCount solver moves that do not check or capture
         * @param underpromotionCount underpromotion solver moves
         * @param enPassantCount en-passant solver moves
         * @param castleCount castling solver moves
         */
        public PuzzleTreeSummary {
            nodeCount = Math.max(1, nodeCount);
            rootReplyCount = Math.max(1, rootReplyCount);
            branchPointCount = Math.max(0, branchPointCount);
            maxSolverDepth = Math.max(1, maxSolverDepth);
            continuationNodeScore = Numbers.clamp01(continuationNodeScore);
            continuationDepthLoad = Math.max(0.0, continuationDepthLoad);
            branchLoad = Math.max(0.0, branchLoad);
            pieceTypeCount = Math.max(0, pieceTypeCount);
            nonPawnNonKingPieceTypeCount = Math.max(0, nonPawnNonKingPieceTypeCount);
            pieceIdentityCount = Math.max(0, pieceIdentityCount);
            pawnMoveCount = Math.max(0, pawnMoveCount);
            kingMoveCount = Math.max(0, kingMoveCount);
            dominantPieceMoveShare = Numbers.clamp01(dominantPieceMoveShare);
            nonforcingMoveCount = Math.max(0, nonforcingMoveCount);
            underpromotionCount = Math.max(0, underpromotionCount);
            enPassantCount = Math.max(0, enPassantCount);
            castleCount = Math.max(0, castleCount);
        }

        /**
         * Creates a one-position tree.
         *
         * <p>
         * Use this factory when only a root puzzle record is known and no
         * explicit continuation records support a longer line.
         * </p>
         *
         * @param node root node score
         * @return single-node continuation summary
         */
        public static PuzzleTreeSummary single(NodeScore node) {
            int pieceTypes = node != null && node.pieceType > 0 ? 1 : 0;
            int nonPawnNonKingTypes = node != null && node.pieceType > Piece.PAWN && node.pieceType < Piece.KING
                    ? 1
                    : 0;
            int pieceIdentities = node != null && node.fromSquare >= 0 ? 1 : 0;
            return new PuzzleTreeSummary(
                    1,
                    1,
                    0,
                    1,
                    0.0,
                    0.0,
                    0.0,
                    pieceTypes,
                    nonPawnNonKingTypes,
                    pieceIdentities,
                    node != null && node.pieceType == Piece.PAWN ? 1 : 0,
                    node != null && node.pieceType == Piece.KING ? 1 : 0,
                    1.0,
                    node != null && node.keyQuiet ? 1 : 0,
                    node != null && node.keyUnderpromotion ? 1 : 0,
                    node != null && node.keyEnPassant ? 1 : 0,
                    node != null && node.keyCastle ? 1 : 0);
        }
    }

    /**
     * Scalar feature signals used for feature names.
     *
     * <p>
     * The values are already normalized and are used only to select explanatory
     * feature names.
     * </p>
     *
     * @param goal puzzle goal
     * @param node root node difficulty score
     * @param length solution length score
     * @param variation variation complexity score
     * @param diversity piece diversity score
     * @param special special move score
     * @param nonforcing nonforcing-key score
     */
    private record FeatureSignals(Goal goal, double node, double length, double variation, double diversity,
            double special, double nonforcing) {
    }

    /**
     * Cheap-evaluator visibility scores used for feature names.
     *
     * <p>
     * These values are retained after scoring so features can explain whether a
     * move looked like a concession to the cheap evaluator.
     * </p>
     *
     * @param staticCp root static score
     * @param solutionCp solution score
     */
    private record CheapVisibility(int staticCp, int solutionCp) {
    }

    /**
     * Key move shape summary.
     *
     * <p>
     * The shape records tactical and rare-move properties of the first required
     * solver move.
     * </p>
     *
     * @param capture whether the key captures
     * @param promotion whether the key promotes
     * @param underpromotion whether the key underpromotes
     * @param castle whether the key castles
     * @param enPassant whether the key captures en-passant
     * @param check whether the key checks
     * @param mate whether the key mates
     * @param quiet whether the key is quiet
     */
    private record KeyShape(boolean capture, boolean promotion, boolean underpromotion, boolean castle,
            boolean enPassant, boolean check, boolean mate, boolean quiet) {
    }
}
