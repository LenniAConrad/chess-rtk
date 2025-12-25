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
     * Used for adding or replacing a PGN tag while keeping insertion order.
     *
     * @param key   PGN tag name (e.g. {@code Event})
     * @param value tag value (may be empty but never null)
     */
    public void putTag(String key, String value) {
        if (key == null) {
            return;
        }
        tags.put(key, value != null ? value : "");
    }

    /**
     * Returns an ordered, unmodifiable view of all tags.
     */
    public Map<String, String> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    /**
     * Returns the starting position of this game. Defaults to the standard chess
     * start.
     */
    public Position getStartPosition() {
        return startPosition;
    }

    /**
     * Replaces the starting position of this game. {@code null} resets to the
     * standard start position.
     */
    public void setStartPosition(Position startPosition) {
        this.startPosition = startPosition != null ? startPosition : new Position(STANDARD_START_FEN);
    }

    /**
     * Returns the head node of the mainline, or {@code null} if the game has no
     * moves.
     */
    public Node getMainline() {
        return mainline;
    }

    /**
     * Sets the head node of the mainline.
     */
    public void setMainline(Node mainline) {
        this.mainline = mainline;
    }

    /**
     * Returns a read-only view of root-level variations that occur before any
     * mainline move (rare but legal in PGN).
     */
    public List<Node> getRootVariations() {
        return Collections.unmodifiableList(rootVariations);
    }

    /**
     * Adds a root-level variation starting at the given node.
     */
    public void addRootVariation(Node variation) {
        if (variation != null) {
            rootVariations.add(variation);
        }
    }

    /**
     * Comments that appear before the first move (sometimes used for game-wide
     * notes).
     */
    public List<String> getPreambleComments() {
        return Collections.unmodifiableList(preambleComments);
    }

    /**
     * Adds a comment that appears before the first move.
     */
    public void addPreambleComment(String comment) {
        if (comment != null && !comment.isEmpty()) {
            preambleComments.add(comment);
        }
    }

    /**
     * Game result token (e.g. {@code 1-0}, {@code 0-1}, {@code 1/2-1/2}, or
     * {@code *}).
     */
    public String getResult() {
        return result;
    }

    /**
     * Sets the game result token; null or empty resets it to {@code *}.
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
         */
        public String getSan() {
            return san;
        }

        /**
         * Mainline successor, or {@code null} if this is the final move in the line.
         */
        public Node getNext() {
            return next;
        }

        /**
         * Replaces the mainline successor of this node.
         */
        public void setNext(Node next) {
            this.next = next;
        }

        /**
         * Variations branching from this move (secondary lines).
         */
        public List<Node> getVariations() {
            return Collections.unmodifiableList(variations);
        }

        /**
         * Adds a variation starting at {@code node}.
         */
        public void addVariation(Node node) {
            if (node != null) {
                variations.add(node);
            }
        }

        /**
         * Comments that appear immediately before this move.
         */
        public List<String> getCommentsBefore() {
            return Collections.unmodifiableList(commentsBefore);
        }

        /**
         * Comments that appear immediately after this move.
         */
        public List<String> getCommentsAfter() {
            return Collections.unmodifiableList(commentsAfter);
        }

        /**
         * Adds a comment that appears before this move.
         */
        public void addCommentBefore(String comment) {
            if (comment != null && !comment.isEmpty()) {
                commentsBefore.add(comment);
            }
        }

        /**
         * Adds a comment that appears after this move.
         */
        public void addCommentAfter(String comment) {
            if (comment != null && !comment.isEmpty()) {
                commentsAfter.add(comment);
            }
        }

        /**
         * Numeric annotation glyphs attached to this move (e.g. $1 = good move).
         */
        public List<Integer> getNags() {
            return Collections.unmodifiableList(nags);
        }

        /**
         * Adds a numeric annotation glyph to this move.
         */
        public void addNag(int nag) {
            nags.add(nag);
        }
    }
}
