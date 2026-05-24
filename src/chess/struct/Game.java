package chess.struct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import chess.core.Position;

/**
 * Represents a parsed PGN game including tags, starting position, movetext
 * (with variations), comments, and result.
 * <p>
 * This class is intentionally lightweight and mutable so that readers can build
 * it incrementally while walking a PGN stream. Tree nodes are linked via a
 * {@code next} reference for the mainline and a {@code variations} list for
 * sidelines.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public class Game {

    /**
     * Standard chess starting position as six-field FEN.
     */
    public static final String STANDARD_START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * PGN tags associated with this game.
     */
    private final Map<String, String> tags = new LinkedHashMap<>();

    /**
     * Comments that appear before the first move (sometimes used for game-wide
     * notes).
     */
    private final List<String> preambleComments = new ArrayList<>();

    /**
     * Root-level variations that occur before any mainline move (rare but legal
     * in PGN).
     */
    private final List<Node> rootVariations = new ArrayList<>();

    /**
     * Starting position of the game.
     */
    private Position startPosition = new Position(STANDARD_START_FEN);

    /**
     * Head node of the mainline.
     */
    private Node mainline;

    /**
     * Game result token (e.g. {@code 1-0}, {@code 0-1}, {@code 1/2-1/2}, or
     * {@code *}).
     */
    private String result = "*";

    /**
     * Adds or replaces a PGN header tag while preserving insertion order.
     *
     * <p>
     * Null keys are ignored. Empty strings are permitted as values but {@code null}
     * is converted to an empty string to ensure consistency.
     * </p>
     *
     * @param key   PGN tag name (e.g. {@code Event})
     * @param value tag value (may be empty but never {@code null})
     */
    public void putTag(String key, String value) {
        if (key == null) {
            return;
        }
        tags.put(key, value != null ? value : "");
    }

    /**
     * Returns an unmodifiable, insertion-order view of all PGN tags.
     *
     * <p>The returned map reflects current tags without allowing callers to
     * mutate the underlying storage.</p>
     *
     * @return unmodifiable tag map in insertion order
     */
    public Map<String, String> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    /**
     * Returns the starting position of this game.
     *
     * <p>When no custom start is set, this defaults to the standard chess start.</p>
     *
     * @return starting position (never {@code null})
     */
    public Position getStartPosition() {
        return startPosition;
    }

    /**
     * Replaces the starting position of this game.
     *
     * <p>Passing {@code null} resets the start to the standard FEN.</p>
     *
     * @param startPosition new starting position, or {@code null} for default
     */
    public void setStartPosition(Position startPosition) {
        this.startPosition = startPosition != null ? startPosition : new Position(STANDARD_START_FEN);
    }

    /**
     * Returns the head node of the mainline.
     *
     * <p>Returns {@code null} when the game has no moves.</p>
     *
     * @return head of the mainline, or {@code null}
     */
    public Node getMainline() {
        return mainline;
    }

    /**
     * Sets the head node of the mainline.
     *
     * @param mainline mainline head node, or {@code null} to clear
     */
    public void setMainline(Node mainline) {
        this.mainline = mainline;
    }

    /**
     * Returns a read-only view of root-level variations.
     *
     * <p>These variations appear before any mainline move and are rare but legal in PGN.</p>
     *
     * @return unmodifiable list of root variations
     */
    public List<Node> getRootVariations() {
        return Collections.unmodifiableList(rootVariations);
    }

    /**
     * Adds a root-level variation starting at the given node.
     *
     * @param variation variation node to register (ignored if {@code null})
     */
    public void addRootVariation(Node variation) {
        if (variation != null) {
            rootVariations.add(variation);
        }
    }

    /**
     * Returns comments that appear before the first move.
     *
     * <p>These are commonly used for game-wide notes or metadata.</p>
     *
     * @return unmodifiable list of preamble comments
     */
    public List<String> getPreambleComments() {
        return Collections.unmodifiableList(preambleComments);
    }

    /**
     * Adds a comment that appears before the first move.
     *
     * @param comment comment text to add (ignored when null/empty)
     */
    public void addPreambleComment(String comment) {
        if (comment != null && !comment.isEmpty()) {
            preambleComments.add(comment);
        }
    }

    /**
     * Returns the game result token.
     *
     * <p>Common values are {@code 1-0}, {@code 0-1}, {@code 1/2-1/2}, or {@code *}.</p>
     *
     * @return result token string
     */
    public String getResult() {
        return result;
    }

    /**
     * Sets the game result token.
     *
     * <p>A null or empty value resets the result to {@code *}.</p>
     *
     * @param result result token to set, or {@code null} to reset
     */
    public void setResult(String result) {
        this.result = (result == null || result.isEmpty()) ? "*" : result;
    }

    /**
     * Represents a single move in the game tree along with comments, NAGs, and
     * nested variations.
     */
    public static final class Node {

        /**
         * Move in Standard Algebraic Notation for this ply.
         */
        private final String san;
    
        /**
         * Secondary lines branching off this move.
         */
        private final List<Node> variations = new ArrayList<>();

        /**
         * Comments that appear immediately before this move.
         */
        private final List<String> commentsBefore = new ArrayList<>();

        /**
         * Comments that appear immediately after this move.
         */
        private final List<String> commentsAfter = new ArrayList<>();

        /**
         * Numeric annotation glyphs attached to this move.
         */
        private final List<Integer> nags = new ArrayList<>();

        /**
         * Mainline successor.
         */
        private Node next;

        /**
         * Creates a node for the given SAN move.
         *
         * @param san move in Standard Algebraic Notation
         */
        public Node(String san) {
            this.san = Objects.requireNonNull(san, "san");
        }

        /**
         * SAN move text for this ply.
         *
         * @return SAN string for the move
         */
        public String getSan() {
            return san;
        }

        /**
         * Mainline successor, or {@code null} if this is the final move in the line.
         *
         * @return next mainline node or {@code null}
         */
        public Node getNext() {
            return next;
        }

        /**
         * Replaces the mainline successor of this node.
         *
         * @param next next node in the mainline, or {@code null} to terminate
         */
        public void setNext(Node next) {
            this.next = next;
        }

        /**
         * Variations branching from this move (secondary lines).
         *
         * @return unmodifiable list of variation heads
         */
        public List<Node> getVariations() {
            return Collections.unmodifiableList(variations);
        }

        /**
         * Adds a variation starting at {@code node}.
         *
         * @param node variation head node (ignored when {@code null})
         */
        public void addVariation(Node node) {
            if (node != null) {
                variations.add(node);
            }
        }

        /**
         * Comments that appear immediately before this move.
         *
         * @return unmodifiable list of pre-move comments
         */
        public List<String> getCommentsBefore() {
            return Collections.unmodifiableList(commentsBefore);
        }

        /**
         * Comments that appear immediately after this move.
         *
         * @return unmodifiable list of post-move comments
         */
        public List<String> getCommentsAfter() {
            return Collections.unmodifiableList(commentsAfter);
        }

        /**
         * Adds a comment that appears before this move.
         *
         * @param comment comment text (ignored when {@code null} or empty)
         */
        public void addCommentBefore(String comment) {
            if (comment != null && !comment.isEmpty()) {
                commentsBefore.add(comment);
            }
        }

        /**
         * Adds a comment that appears after this move.
         *
         * @param comment comment text (ignored when {@code null} or empty)
         */
        public void addCommentAfter(String comment) {
            if (comment != null && !comment.isEmpty()) {
                commentsAfter.add(comment);
            }
        }

        /**
         * Numeric annotation glyphs attached to this move (e.g. $1 = good move).
         *
         * @return unmodifiable list of NAG codes
         */
        public List<Integer> getNags() {
            return Collections.unmodifiableList(nags);
        }

        /**
         * Adds a numeric annotation glyph (NAG) to this move.
         *
         * @param nag NAG code (e.g., {@code 1} = good move)
         */
        public void addNag(int nag) {
            nags.add(nag);
        }
    }
}
