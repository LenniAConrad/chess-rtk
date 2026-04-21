package chess.tag.piece;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.eval.Evaluator;
import chess.tag.core.Text;

/**
 * Classifies pieces by how their removal would affect the position.
 * <p>
 * The generated tags describe piece strength tiers and identify the most and
 * least influential pieces for each side.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class PieceAblation {

    /**
     * Threshold for a slightly strong king.
     */
    private static final int KING_SLIGHT_THRESHOLD = 80;

    /**
     * Threshold for a strong king.
     */
    private static final int KING_STRONG_THRESHOLD = 160;

    /**
     * Threshold for a very strong king.
     */
    private static final int KING_VERY_STRONG_THRESHOLD = 240;

    /**
     * Threshold for a weak king.
     */
    private static final int KING_WEAK_THRESHOLD = -160;

    /**
     * Threshold for a very weak king.
     */
    private static final int KING_SUPER_WEAK_THRESHOLD = -240;

    /**
     * Minimum margin used when deriving material-based strength thresholds.
     */
    private static final int MIN_MARGIN = 30;

    /**
     * Prevents instantiation of this utility class.
     */
    private PieceAblation() {
        // Utility class
    }

    /**
     * Computes piece-ablation tags using a fresh evaluator instance.
     *
     * @param position the position to analyze
     * @return an immutable list of ablation tags
     */
    public static List<String> tag(Position position) {
        try (Evaluator evaluator = new Evaluator()) {
            return tag(position, evaluator, true);
        }
    }

    /**
     * Computes piece-ablation tags using the supplied evaluator.
     *
     * @param position the position to analyze
     * @param evaluator the evaluator used to compute ablation values
     * @param includeColor whether to include the piece color in rendered tags
     * @return an immutable list of ablation tags and extrema tags
     * @throws NullPointerException if {@code position} or {@code evaluator} is {@code null}
     */
    public static List<String> tag(Position position, Evaluator evaluator, boolean includeColor) {
        Objects.requireNonNull(position, POSITION);
        Objects.requireNonNull(evaluator, EVALUATOR);

        int[][] ablation = evaluator.ablation(position);
        byte[] board = position.getBoard();
        boolean whiteToMove = position.isWhiteTurn();
        Accumulator acc = new Accumulator(includeColor);

        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY) {
                continue;
            }
            addPieceTag(acc, ablation, whiteToMove, piece, (byte) index);
        }

        return acc.toList();
    }

    /**
     * Adds one piece tag and records its strength score.
     *
     * @param acc the mutable accumulator receiving tags and extrema
     * @param ablation the evaluator-provided ablation matrix
     * @param whiteToMove whether White is the side to move
     * @param piece the piece to describe
     * @param square the piece square
     */
    private static void addPieceTag(Accumulator acc, int[][] ablation, boolean whiteToMove, byte piece,
            byte square) {
        int file = Field.getX(square);
        int rankFromBottom = Field.getY(square);
        int rawImpact = ablation[rankFromBottom][file];
        boolean pieceIsWhite = Piece.isWhite(piece);
        int ownerImpact = (pieceIsWhite == whiteToMove) ? rawImpact : -rawImpact;
        Strength strength = classify(piece, ownerImpact);
        acc.addTag(formatTag(strength, piece, square, acc.includeColor));
        acc.track(piece, square, strengthScore(piece, ownerImpact), pieceIsWhite);
    }

    /**
     * Classifies a piece into a strength tier.
     *
     * @param piece the piece being classified
     * @param ownerImpact the ablation impact from the owner's perspective
     * @return the strength tier for the piece
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
     * Classifies a king by its ablation impact.
     *
     * @param ownerImpact the king's ablation impact
     * @return the king strength tier
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
     * Classifies a non-king piece by comparing ablation impact to material value.
     *
     * @param ownerImpact the ablation impact from the owner's perspective
     * @param value the material value of the piece
     * @return the strength tier for the piece
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
     * Computes the score used to rank extrema among pieces.
     *
     * @param piece the piece being scored
     * @param ownerImpact the ablation impact from the owner's perspective
     * @return the ranking score for the piece
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
     * Formats a single ablation tag.
     *
     * @param strength the strength tier
     * @param piece the piece being described
     * @param square the square of the piece
     * @param includeColor whether to include the piece color
     * @return the serialized ablation tag
     */
    private static String formatTag(Strength strength, byte piece, byte square, boolean includeColor) {
        StringBuilder builder = new StringBuilder();
        builder.append(strength.label()).append(SPACE_CHAR);
        if (includeColor) {
            builder.append(Text.colorNameLower(piece)).append(SPACE_CHAR);
        }
        builder.append(Text.squareNameLower(square)).append(SPACE_CHAR).append(Text.pieceNameLower(piece));
        return builder.toString();
    }

    /**
     * Captures the most relevant information about a piece for extrema tracking.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class PieceSummary {

        /**
         * The piece code.
         */
        private final byte piece;

        /**
         * The piece square.
         */
        private final byte square;

        /**
         * The comparison score.
         */
        private final int score;

        /**
         * Creates a piece summary.
         *
         * @param piece the piece code
         * @param square the piece square
         * @param score the ranking score
         */
        private PieceSummary(byte piece, byte square, int score) {
            this.piece = piece;
            this.square = square;
            this.score = score;
        }
    }

    /**
     * Accumulates rendered tags and tracks the strongest and weakest pieces.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class Accumulator {

        /**
         * The rendered tags collected so far.
         */
        private final List<String> tags = new ArrayList<>();

        /**
         * Whether rendered tags should include the piece color.
         */
        private final boolean includeColor;

        /**
         * The strongest piece overall.
         */
        private PieceSummary strongest;

        /**
         * The weakest piece overall.
         */
        private PieceSummary weakest;

        /**
         * The strongest White piece.
         */
        private PieceSummary strongestWhite;

        /**
         * The weakest White piece.
         */
        private PieceSummary weakestWhite;

        /**
         * The strongest Black piece.
         */
        private PieceSummary strongestBlack;

        /**
         * The weakest Black piece.
         */
        private PieceSummary weakestBlack;

        /**
         * Creates an accumulator.
         *
         * @param includeColor whether rendered tags should include the piece color
         */
        private Accumulator(boolean includeColor) {
            this.includeColor = includeColor;
        }

        /**
         * Adds a rendered tag to the accumulator.
         *
         * @param tag the tag to add
         */
        private void addTag(String tag) {
            tags.add(tag);
        }

        /**
         * Tracks a piece for strongest and weakest comparisons.
         *
         * @param piece the piece code
         * @param square the piece square
         * @param score the ranking score
         * @param whitePiece whether the piece belongs to White
         */
        private void track(byte piece, byte square, int score, boolean whitePiece) {
            PieceSummary summary = new PieceSummary(piece, square, score);
            strongest = maxSummary(strongest, summary);
            weakest = minSummary(weakest, summary);
            if (whitePiece) {
                strongestWhite = maxSummary(strongestWhite, summary);
                weakestWhite = minSummary(weakestWhite, summary);
            } else {
                strongestBlack = maxSummary(strongestBlack, summary);
                weakestBlack = minSummary(weakestBlack, summary);
            }
        }

        /**
         * Returns the rendered tags including extrema summaries.
         *
         * @return the immutable rendered tag list
         */
        private List<String> toList() {
            addExtreme(STRONGEST, strongest);
            addExtreme(WEAKEST, weakest);
            addExtreme(STRONGEST_WHITE, strongestWhite);
            addExtreme(WEAKEST_WHITE, weakestWhite);
            addExtreme(STRONGEST_BLACK, strongestBlack);
            addExtreme(WEAKEST_BLACK, weakestBlack);
            return Collections.unmodifiableList(tags);
        }

        /**
         * Adds one extrema tag if a summary exists.
         *
         * @param label the extrema label
         * @param summary the summary to render
         */
        private void addExtreme(String label, PieceSummary summary) {
            if (summary != null) {
                tags.add(formatExtremeTag(label, summary));
            }
        }

        /**
         * Formats an extrema tag from a piece summary.
         *
         * @param label the extrema label
         * @param summary the piece summary
         * @return the rendered extrema tag
         */
        private static String formatExtremeTag(String label, PieceSummary summary) {
            return label + COLON_SPACE + Text.colorNameLower(summary.piece) + SPACE_TEXT
                    + Text.pieceNameLower(summary.piece) + SPACE_TEXT
                    + Text.squareNameLower(summary.square);
        }

        /**
         * Selects the better of two summaries by score.
         *
         * @param current the current summary
         * @param candidate the candidate summary
         * @return the summary with the greater score
         */
        private static PieceSummary maxSummary(PieceSummary current, PieceSummary candidate) {
            if (current == null || candidate.score > current.score) {
                return candidate;
            }
            return current;
        }

        /**
         * Selects the worse of two summaries by score.
         *
         * @param current the current summary
         * @param candidate the candidate summary
         * @return the summary with the lower score
         */
        private static PieceSummary minSummary(PieceSummary current, PieceSummary candidate) {
            if (current == null || candidate.score < current.score) {
                return candidate;
            }
            return current;
        }
    }

    /**
     * Strength tiers used to label the effect of removing a piece.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private enum Strength {

        /**
         * A very strong piece.
         */
        VERY_STRONG(VERY_STRONG_TEXT),

        /**
         * A strong piece.
         */
        STRONG(chess.tag.core.Literals.STRONG),

        /**
         * A slightly strong piece.
         */
        SLIGHTLY_STRONG(SLIGHTLY_STRONG_TEXT),

        /**
         * A neutral piece.
         */
        OK(chess.tag.core.Literals.NEUTRAL),

        /**
         * A slightly weak piece.
         */
        SLIGHTLY_WEAK(SLIGHTLY_WEAK_TEXT),

        /**
         * A weak piece.
         */
        WEAK(chess.tag.core.Literals.WEAK),

        /**
         * A very weak piece.
         */
        SUPER_WEAK(VERY_WEAK_TEXT);

        /**
         * The human-readable label for the tier.
         */
        private final String label;

        /**
         * Creates a strength tier with its display label.
         *
         * @param label the human-readable label
         */
        Strength(String label) {
            this.label = label;
        }

        /**
         * Returns the display label for the tier.
         *
         * @return the tier label
         */
        private String label() {
            return label;
        }
    }
}
