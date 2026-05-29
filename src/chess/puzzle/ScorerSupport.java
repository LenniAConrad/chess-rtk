package chess.puzzle;

import java.util.ArrayList;
import java.util.List;

import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.eval.CentipawnEvaluator;
import chess.puzzle.Scorer.PuzzleTreeSummary;

/**
 * Support types and formatting helpers for {@link Scorer}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class ScorerSupport {

    /**
     * Utility class; prevent instantiation.
     */
    private ScorerSupport() {
        // utility
    }

    /**
     * Converts a normalized puzzle score into a difficulty label.
     *
     * @param score normalized score
     * @return difficulty label
     */
    static String labelFor(double score) {
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
static List<String> featureNames(FeatureSignals signals, PuzzleTreeSummary tree, KeyShape key,
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
    if (signals.variation > 0.30 || tree.branchPointCount() > 0) {
        names.add("branching");
    }
    if (signals.diversity > 0.25) {
        names.add("piece_diversity");
    }
    if (tree.pieceIdentityCount() > 1) {
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
static String moveToString(short move) {
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
static String solutionSan(Position root, short move) {
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
static RootMaterial rootMaterial(Position position) {
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
 * Material-only fallback evaluator used when no full evaluator is configured.
 */
static final class MaterialVisibilityEvaluator implements CentipawnEvaluator {

    /**
     * Restricts fallback evaluator construction to the scorer.
     *
     * <p>
     * Instances are created only by {@link Scorer#defaultCheapEvaluator()}.
     * </p>
     */
    MaterialVisibilityEvaluator() {
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


}
