package chess.tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.eval.Evaluator;

/**
 * Uses evaluator ablation to label pieces by strength tier.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class PieceAblationTagger {

    /**
     * Slight positive king impact from ablation.
     */
    private static final int KING_SLIGHT_THRESHOLD = 80;

    /**
     * Strong king impact indicating robust safety.
     */
    private static final int KING_STRONG_THRESHOLD = 160;

    /**
     * Very strong king impact, rare in sharp positions.
     */
    private static final int KING_VERY_STRONG_THRESHOLD = 240;

    /**
     * Weak king impact starting to resemble an exposed monarch.
     */
    private static final int KING_WEAK_THRESHOLD = -160;

    /**
     * Super weak threshold for extremely exposed kings.
     */
    private static final int KING_SUPER_WEAK_THRESHOLD = -240;

    /**
     * Minimum delta used to differentiate weak/strong tiers for non-king pieces.
     */
    private static final int MIN_MARGIN = 30;

    /**
     * Utility class, prevents instantiation.
     */
    private PieceAblationTagger() {
        // Utility class
    }

    /**
     * Tags pieces by running a fresh evaluator instance.
     *
     * @param position position to inspect
     * @return immutable list of piece tags
     */
    public static List<String> tag(Position position) {
        try (Evaluator evaluator = new Evaluator()) {
            return tag(position, evaluator, true);
        }
    }

    /**
     * Tags pieces using the provided evaluator instance.
     *
     * @param position position to inspect
     * @param evaluator evaluator to use for ablation
     * @param includeColor when true, prefixes tags with WHITE/BLACK
     * @return immutable list of piece tags
     */
    public static List<String> tag(Position position, Evaluator evaluator, boolean includeColor) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(evaluator, "evaluator");

        int[][] ablation = evaluator.ablation(position);
        byte[] board = position.getBoard();
        boolean whiteToMove = position.isWhiteTurn();
        List<String> tags = new ArrayList<>();
        PieceSummary strongest = null;
        PieceSummary weakest = null;

        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY) {
                continue;
            }
            byte square = (byte) index;
            int file = Field.getX(square);
            int rankFromBottom = Field.getY(square);
            int rawImpact = ablation[rankFromBottom][file];
            boolean pieceIsWhite = Piece.isWhite(piece);
            // Flip to the piece owner's perspective when it is not the side to move.
            int ownerImpact = (pieceIsWhite == whiteToMove) ? rawImpact : -rawImpact;
            Strength strength = classify(piece, ownerImpact);
            tags.add(formatTag(strength, piece, square, includeColor));
            int strengthScore = strengthScore(piece, ownerImpact);
            if (strongest == null || strengthScore > strongest.score) {
                strongest = new PieceSummary(piece, square, strengthScore);
            }
            if (weakest == null || strengthScore < weakest.score) {
                weakest = new PieceSummary(piece, square, strengthScore);
            }
        }

        if (strongest != null) {
            tags.add(formatExtremeTag("strongest", strongest));
        }
        if (weakest != null) {
            tags.add(formatExtremeTag("weakest", weakest));
        }

        return Collections.unmodifiableList(tags);
    }

    /**
     * Picks a strength tier for a piece based on how much its ablation hurts its owner.
     *
     * @param piece the piece being classified
     * @param ownerImpact ablation impact normalized to the piece owner
     * @return chosen strength tier
     */
    private static Strength classify(byte piece, int ownerImpact) {
        if (Piece.isKing(piece)) {
            return classifyKing(ownerImpact);
        }
        int value = Piece.getMaterialValue(piece);
        if (value <= 0) {
            return classifyKing(ownerImpact);
        }
        return classifyByMaterial(ownerImpact, value);
    }

    /**
     * Classifies a king into a strength tier using fixed thresholds.
     *
     * @param ownerImpact king impact from the owner's perspective
     * @return selected strength tier
     */
    private static Strength classifyKing(int ownerImpact) {
        if (ownerImpact >= KING_VERY_STRONG_THRESHOLD) {
            return Strength.VERY_STRONG;
        }
        if (ownerImpact >= KING_STRONG_THRESHOLD) {
            return Strength.STRONG;
        }
        if (ownerImpact >= KING_SLIGHT_THRESHOLD) {
            return Strength.SLIGHTLY_STRONG;
        }
        if (ownerImpact <= KING_SUPER_WEAK_THRESHOLD) {
            return Strength.SUPER_WEAK;
        }
        if (ownerImpact <= KING_WEAK_THRESHOLD) {
            return Strength.WEAK;
        }
        if (ownerImpact <= -KING_SLIGHT_THRESHOLD) {
            return Strength.SLIGHTLY_WEAK;
        }
        return Strength.OK;
    }

    /**
     * Classifies non-king pieces using value-scaled thresholds.
     *
     * @param ownerImpact impact seen by the piece's owner
     * @param value material value of the piece
     * @return selected strength tier
     */
    private static Strength classifyByMaterial(int ownerImpact, int value) {
        int slight = Math.max(MIN_MARGIN, value / 6);
        int strong = Math.max(MIN_MARGIN * 2, value / 3);
        int very = Math.max(MIN_MARGIN * 3, value / 2);
        int delta = ownerImpact - value;
        if (delta >= very) {
            return Strength.VERY_STRONG;
        }
        if (delta >= strong) {
            return Strength.STRONG;
        }
        if (delta >= slight) {
            return Strength.SLIGHTLY_STRONG;
        }
        if (delta <= -very) {
            return Strength.SUPER_WEAK;
        }
        if (delta <= -strong) {
            return Strength.WEAK;
        }
        if (delta <= -slight) {
            return Strength.SLIGHTLY_WEAK;
        }
        return Strength.OK;
    }

    /**
     * Computes a score for ranking strongest/weakest pieces.
     *
     * @param piece the piece being scored
     * @param ownerImpact impact from the owner's perspective
     * @return delta used to compare pieces
     */
    private static int strengthScore(byte piece, int ownerImpact) {
        if (Piece.isKing(piece)) {
            return ownerImpact;
        }
        int value = Piece.getMaterialValue(piece);
        if (value <= 0) {
            return ownerImpact;
        }
        return ownerImpact - value;
    }

    /**
     * Builds the natural language tag describing a single piece.
     *
     * @param strength determined strength tier
     * @param piece piece code
     * @param square board index of the piece
     * @param includeColor if true, prefixes the color
     * @return formatted tag string
     */
    private static String formatTag(Strength strength, byte piece, byte square, boolean includeColor) {
        StringBuilder builder = new StringBuilder();
        builder.append(strength.label()).append(' ');
        if (includeColor) {
            builder.append(Text.colorNameLower(piece)).append(' ');
        }
        builder.append(Text.squareNameLower(square)).append(' ').append(Text.pieceNameLower(piece));
        return builder.toString();
    }

    /**
     * Formats the strongest/weakest summary tag.
     *
     * @param label descriptor ("strongest" or "weakest")
     * @param summary summary data for the piece
     * @return formatted summary string
     */
    private static String formatExtremeTag(String label, PieceSummary summary) {
        return label + ": " + Text.colorNameLower(summary.piece) + " " + Text.pieceNameLower(summary.piece) + " "
                + Text.squareNameLower(summary.square);
    }

    /**
     * Minimal summary for ranking extremes by strength score.
     */
    private static final class PieceSummary {

        /**
         * Piece code (white/black).
         */
        private final byte piece;

        /**
         * Board index of the piece.
         */
        private final byte square;

        /**
         * Score used for ranking strongest/weaks pieces.
         */
        private final int score;

        /**
         * @param piece piece code
         * @param square board index
         * @param score ranking score
         */
        private PieceSummary(byte piece, byte square, int score) {
            this.piece = piece;
            this.square = square;
            this.score = score;
        }
    }

    /**
     * Enumerates the tiers used for describing placement impact.
     */
    private enum Strength {

        /**
         * Piece is much stronger than expected.
         */
        VERY_STRONG("very strong"),

        /**
         * Piece enjoys a clear advantage in its placement.
         */
        STRONG("strong"),

        /**
         * Piece is slightly above the neutral threshold.
         */
        SLIGHTLY_STRONG("slightly strong"),

        /**
         * Piece placement is approximately neutral.
         */
        OK("neutral"),

        /**
         * Piece is slightly behind expected placement.
         */
        SLIGHTLY_WEAK("slightly weak"),

        /**
         * Piece placement is clearly suboptimal.
         */
        WEAK("weak"),

        /**
         * Piece is significantly hurting the owner.
         */
        SUPER_WEAK("very weak");

        /**
         * Human readable label used in the generated tag.
         */
        private final String label;

        /**
         * Builds a tier wrapper that holds the text emitted for that tier.
         *
         * @param label human readable form of the tier
         */
        Strength(String label) {
            this.label = label;
        }

        /**
         * Returns the literal text that should be emitted when describing the tier.
         *
         * @return plain label used in the final tag
         */
        private String label() {
            return label;
        }
    }
}
