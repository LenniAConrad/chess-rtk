package chess.study;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.struct.Game;
import chess.struct.Pgn;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Editable PGN study tree backed directly by {@link Game.Node}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class StudyTreeModel {

    /**
     * Backing game.
     */
    private Game game;

    /**
     * Current selected path.
     */
    private StudyNodePath currentPath = StudyNodePath.root();

    /**
     * Creates an empty study tree.
     */
    public StudyTreeModel() {
        this(new Game());
    }

    /**
     * Creates a study tree for a game.
     *
     * @param game backing game
     */
    public StudyTreeModel(Game game) {
        load(game);
    }

    /**
     * Loads a game.
     *
     * @param nextGame backing game
     */
    public void load(Game nextGame) {
        game = nextGame == null ? new Game() : nextGame;
        currentPath = StudyNodePath.root();
    }

    /**
     * Returns the backing game.
     *
     * @return backing game
     */
    public Game game() {
        return game;
    }

    /**
     * Returns the current path.
     *
     * @return current path
     */
    public StudyNodePath currentPath() {
        return currentPath;
    }

    /**
     * Selects a path if it exists.
     *
     * @param path requested path
     * @return true when selected
     */
    public boolean select(StudyNodePath path) {
        StudyNodePath target = path == null ? StudyNodePath.root() : path;
        if (!target.isRoot() && findRef(target) == null) {
            return false;
        }
        currentPath = target;
        return true;
    }

    /**
     * Returns the current position.
     *
     * @return current position
     */
    public Position currentPosition() {
        return positionAt(currentPath);
    }

    /**
     * Computes a position by replaying UCI moves through the shared core.
     *
     * @param path path
     * @return position
     */
    public Position positionAt(StudyNodePath path) {
        Position cursor = startPosition();
        if (path == null) {
            return cursor;
        }
        for (String uci : path.toUciList()) {
            short move = Move.parse(uci);
            if (!cursor.isLegalMove(move)) {
                throw new IllegalArgumentException("illegal move in study path: " + uci + " at " + cursor);
            }
            cursor.play(move);
        }
        return cursor;
    }

    /**
     * Adds a legal UCI move at the current path.
     *
     * @param uci UCI move
     * @return selected path after the operation
     */
    public StudyNodePath addLegalMove(String uci) {
        return addLegalMove(Move.parse(uci));
    }

    /**
     * Adds a legal move at the current path.
     *
     * @param move encoded move
     * @return selected path after the operation
     */
    public StudyNodePath addLegalMove(short move) {
        Position before = currentPosition();
        if (!before.isLegalMove(move)) {
            throw new IllegalArgumentException("illegal study move: " + Move.toString(move));
        }
        StudyNodePath existing = childPath(currentPath, before, move);
        if (existing != null) {
            currentPath = existing;
            return currentPath;
        }
        Game.Node node = new Game.Node(SAN.toAlgebraic(before, move));
        appendChild(currentPath, node);
        currentPath = currentPath.append(move);
        return currentPath;
    }

    /**
     * Promotes the selected variation to the primary line.
     *
     * @return true when promoted
     */
    public boolean promoteVariation() {
        Ref selected = findRef(currentPath);
        if (selected == null || selected.previous() != null) {
            return false;
        }
        if (selected.rootVariationIndex() >= 0) {
            Game.Node oldMainline = game.getMainline();
            game.removeRootVariation(selected.node());
            game.setMainline(selected.node());
            if (oldMainline != null) {
                selected.node().addVariation(oldMainline);
            }
            return true;
        }
        if (selected.variationOwner() == null) {
            return false;
        }
        Ref owner = findRefByNode(selected.variationOwner());
        if (owner == null) {
            return false;
        }
        selected.variationOwner().removeVariation(selected.node());
        replaceNode(owner, selected.node());
        selected.node().addVariation(owner.node());
        return true;
    }

    /**
     * Moves the selected variation one slot upward, promoting at the top.
     *
     * @return true when reordered or promoted
     */
    public boolean promoteVariationOneStep() {
        Ref selected = findRef(currentPath);
        if (selected == null || selected.previous() != null) {
            return false;
        }
        if (selected.rootVariationIndex() > 0) {
            int index = selected.rootVariationIndex();
            game.removeRootVariation(selected.node());
            game.addRootVariation(index - 1, selected.node());
            return true;
        }
        if (selected.rootVariationIndex() == 0) {
            return promoteVariation();
        }
        if (selected.variationOwner() == null) {
            return false;
        }
        int index = selected.variationOwner().variationIndex(selected.node());
        if (index > 0) {
            selected.variationOwner().removeVariation(selected.node());
            selected.variationOwner().addVariation(index - 1, selected.node());
            return true;
        }
        return promoteVariation();
    }

    /**
     * Deletes the selected branch.
     *
     * @return true when deleted
     */
    public boolean deleteBranch() {
        Ref selected = findRef(currentPath);
        if (selected == null) {
            return false;
        }
        if (selected.previous() != null) {
            selected.previous().setNext(null);
        } else if (selected.node() == game.getMainline()) {
            game.setMainline(null);
        } else if (selected.rootVariationIndex() >= 0) {
            game.removeRootVariation(selected.node());
        } else if (selected.variationOwner() != null) {
            selected.variationOwner().removeVariation(selected.node());
        } else {
            return false;
        }
        currentPath = selected.beforePath();
        return true;
    }

    /**
     * Returns the comment before a node.
     *
     * @param path path
     * @return comment text
     */
    public String commentBefore(StudyNodePath path) {
        if (path == null || path.isRoot()) {
            return String.join(" ", game.getPreambleComments());
        }
        Ref ref = findRef(path);
        return ref == null ? "" : String.join(" ", ref.node().getCommentsBefore());
    }

    /**
     * Sets the comment before a node.
     *
     * @param path path
     * @param comment comment text
     */
    public void setCommentBefore(StudyNodePath path, String comment) {
        if (path == null || path.isRoot()) {
            game.setPreambleComments(commentList(comment));
            return;
        }
        Ref ref = requireRef(path);
        ref.node().setCommentsBefore(commentList(comment));
    }

    /**
     * Returns the comment after a node without shape directives.
     *
     * @param path path
     * @return comment text
     */
    public String commentAfter(StudyNodePath path) {
        Ref ref = path == null || path.isRoot() ? null : findRef(path);
        if (ref == null) {
            return "";
        }
        return ShapeCommentCodec.parse(String.join(" ", ref.node().getCommentsAfter())).text();
    }

    /**
     * Sets the comment after a node while preserving shapes.
     *
     * @param path path
     * @param comment comment text
     */
    public void setCommentAfter(StudyNodePath path, String comment) {
        Ref ref = requireRef(path);
        List<StudyShape> shapes = shapes(path);
        String rendered = ShapeCommentCodec.render(comment, shapes);
        ref.node().setCommentsAfter(commentList(rendered));
    }

    /**
     * Toggles a NAG on a node.
     *
     * @param path path
     * @param nag NAG code
     * @return true when added, false when removed
     */
    public boolean toggleNag(StudyNodePath path, int nag) {
        return NagCatalog.toggle(requireRef(path).node(), nag);
    }

    /**
     * Returns shapes at a node.
     *
     * @param path path
     * @return shapes
     */
    public List<StudyShape> shapes(StudyNodePath path) {
        Ref ref = path == null || path.isRoot() ? null : findRef(path);
        if (ref == null) {
            return List.of();
        }
        return ShapeCommentCodec.parse(String.join(" ", ref.node().getCommentsAfter())).shapes();
    }

    /**
     * Sets shapes at a node while preserving normal comment text.
     *
     * @param path path
     * @param shapes shapes
     */
    public void setShapes(StudyNodePath path, List<StudyShape> shapes) {
        Ref ref = requireRef(path);
        String comment = commentAfter(path);
        String rendered = ShapeCommentCodec.render(comment, shapes);
        ref.node().setCommentsAfter(commentList(rendered));
    }

    /**
     * Returns the next mainline path after the current path.
     *
     * @return next path, or {@code null}
     */
    public StudyNodePath nextMainlinePath() {
        if (currentPath.isRoot()) {
            Game.Node head = game.getMainline();
            return head == null ? null : pathForChild(StudyNodePath.root(), startPosition(), head);
        }
        Ref ref = findRef(currentPath);
        if (ref == null || ref.node().getNext() == null) {
            return null;
        }
        return pathForChild(currentPath, currentPosition(), ref.node().getNext());
    }

    /**
     * Returns flattened rows for UI display.
     *
     * @return rows
     */
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        appendRows(game.getMainline(), startPosition(), StudyNodePath.root(), 0, true, rows);
        for (Game.Node variation : game.getRootVariations()) {
            appendRows(variation, startPosition(), StudyNodePath.root(), 1, false, rows);
        }
        return List.copyOf(rows);
    }

    /**
     * Exports the edited game to PGN.
     *
     * @return PGN
     */
    public String toPgn() {
        return Pgn.toPgn(game);
    }

    /**
     * Flat UI row.
     *
     * @param path node path
     * @param moveNumber move-number label
     * @param san SAN
     * @param uci UCI
     * @param depth variation depth
     * @param mainline true for primary line
     * @param nags NAG codes
     * @param commentBefore comment before
     * @param commentAfter comment after
     */
    public record Row(StudyNodePath path, String moveNumber, String san, String uci, int depth,
            boolean mainline, List<Integer> nags, String commentBefore, String commentAfter) {

        /**
         * Creates a row.
         *
         * @param path node path
         * @param moveNumber move-number label
         * @param san SAN
         * @param uci UCI
         * @param depth variation depth
         * @param mainline true for primary line
         * @param nags NAG codes
         * @param commentBefore comment before
         * @param commentAfter comment after
         */
        public Row {
            nags = nags == null ? List.of() : List.copyOf(nags);
        }
    }

    /**
     * Returns the start position.
     *
     * @return start position
     */
    private Position startPosition() {
        return game.getStartPosition() == null
                ? new Position(Setup.getStandardStartFEN())
                : game.getStartPosition().copy();
    }

    /**
     * Appends a child node at a parent path.
     *
     * @param parentPath parent path
     * @param node child node
     */
    private void appendChild(StudyNodePath parentPath, Game.Node node) {
        if (parentPath == null || parentPath.isRoot()) {
            if (game.getMainline() == null) {
                game.setMainline(node);
            } else {
                game.addRootVariation(node);
            }
            return;
        }
        Ref parent = requireRef(parentPath);
        if (parent.node().getNext() == null) {
            parent.node().setNext(node);
        } else {
            parent.node().getNext().addVariation(node);
        }
    }

    /**
     * Finds an existing child path.
     *
     * @param parentPath parent path
     * @param before position before move
     * @param move encoded move
     * @return child path, or null
     */
    private StudyNodePath childPath(StudyNodePath parentPath, Position before, short move) {
        if (parentPath == null || parentPath.isRoot()) {
            if (nodeMoveEquals(game.getMainline(), before, move)) {
                return StudyNodePath.root().append(move);
            }
            for (Game.Node variation : game.getRootVariations()) {
                if (nodeMoveEquals(variation, before, move)) {
                    return StudyNodePath.root().append(move);
                }
            }
            return null;
        }
        Ref parent = requireRef(parentPath);
        Game.Node next = parent.node().getNext();
        if (nodeMoveEquals(next, before, move)) {
            return parentPath.append(move);
        }
        if (next != null) {
            for (Game.Node variation : next.getVariations()) {
                if (nodeMoveEquals(variation, before, move)) {
                    return parentPath.append(move);
                }
            }
        }
        return null;
    }

    /**
     * Builds a path for one child node.
     *
     * @param parentPath parent path
     * @param before position before child
     * @param node child node
     * @return child path
     */
    private static StudyNodePath pathForChild(StudyNodePath parentPath, Position before, Game.Node node) {
        try {
            return parentPath.append(SAN.fromAlgebraic(before, node.getSan()));
        } catch (RuntimeException ex) {
            return parentPath;
        }
    }

    /**
     * Finds a node reference.
     *
     * @param path path
     * @return reference, or null
     */
    private Ref findRef(StudyNodePath path) {
        if (path == null || path.isRoot()) {
            return null;
        }
        Ref mainline = findInSequence(game.getMainline(), startPosition(), StudyNodePath.root(),
                null, null, -1, true, -1, path);
        if (mainline != null) {
            return mainline;
        }
        List<Game.Node> rootVariations = game.getRootVariations();
        for (int i = 0; i < rootVariations.size(); i++) {
            Ref found = findInSequence(rootVariations.get(i), startPosition(), StudyNodePath.root(),
                    null, null, i, false, i, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Finds a node reference by identity.
     *
     * @param node node
     * @return reference, or null
     */
    private Ref findRefByNode(Game.Node node) {
        if (node == null) {
            return null;
        }
        for (Row row : rows()) {
            Ref ref = findRef(row.path());
            if (ref != null && ref.node() == node) {
                return ref;
            }
        }
        return null;
    }

    /**
     * Finds a node inside a sequence.
     *
     * @param node sequence head
     * @param start sequence start position
     * @param prefix path before sequence
     * @param previous previous node in sequence
     * @param variationOwner variation owner
     * @param variationIndex variation index
     * @param mainline primary-line flag
     * @param rootVariationIndex root variation index
     * @param target target path
     * @return reference, or null
     */
    private Ref findInSequence(Game.Node node, Position start, StudyNodePath prefix, Game.Node previous,
            Game.Node variationOwner, int variationIndex, boolean mainline, int rootVariationIndex,
            StudyNodePath target) {
        Position cursor = start.copy();
        StudyNodePath path = prefix;
        Game.Node current = node;
        Game.Node prev = previous;
        boolean first = true;
        while (current != null) {
            Position before = cursor.copy();
            StudyNodePath beforePath = path;
            short move = SAN.fromAlgebraic(cursor, current.getSan());
            path = path.append(move);
            Ref ref = new Ref(current, path, beforePath, before, prev,
                    first ? variationOwner : null,
                    first ? variationIndex : -1,
                    first && prev == null ? rootVariationIndex : -1,
                    mainline);
            if (path.equals(target)) {
                return ref;
            }
            List<Game.Node> variations = current.getVariations();
            for (int i = 0; i < variations.size(); i++) {
                Ref found = findInSequence(variations.get(i), before, beforePath,
                        null, current, i, false, -1, target);
                if (found != null) {
                    return found;
                }
            }
            cursor.play(move);
            prev = current;
            current = current.getNext();
            first = false;
        }
        return null;
    }

    /**
     * Replaces a node in its owning sequence.
     *
     * @param oldRef old node reference
     * @param replacement replacement node
     */
    private void replaceNode(Ref oldRef, Game.Node replacement) {
        if (oldRef.node() == game.getMainline()) {
            game.setMainline(replacement);
        } else if (oldRef.previous() != null) {
            oldRef.previous().setNext(replacement);
        } else if (oldRef.rootVariationIndex() >= 0) {
            int index = oldRef.rootVariationIndex();
            game.removeRootVariation(oldRef.node());
            game.addRootVariation(index, replacement);
        } else if (oldRef.variationOwner() != null) {
            int index = oldRef.variationOwner().variationIndex(oldRef.node());
            oldRef.variationOwner().removeVariation(oldRef.node());
            oldRef.variationOwner().addVariation(Math.max(0, index), replacement);
        }
    }

    /**
     * Appends display rows from a sequence.
     *
     * @param node sequence head
     * @param start sequence start position
     * @param prefix path before sequence
     * @param depth variation depth
     * @param mainline primary-line flag
     * @param rows rows
     */
    private void appendRows(Game.Node node, Position start, StudyNodePath prefix, int depth,
            boolean mainline, List<Row> rows) {
        Position cursor = start.copy();
        StudyNodePath path = prefix;
        Game.Node current = node;
        while (current != null) {
            Position before = cursor.copy();
            StudyNodePath beforePath = path;
            short move = SAN.fromAlgebraic(cursor, current.getSan());
            path = path.append(move);
            String uci = Move.toString(move);
            rows.add(new Row(path, moveNumber(before), current.getSan(), uci, depth, mainline,
                    current.getNags(), String.join(" ", current.getCommentsBefore()),
                    ShapeCommentCodec.parse(String.join(" ", current.getCommentsAfter())).text()));
            for (Game.Node variation : current.getVariations()) {
                appendRows(variation, before, beforePath, depth + 1, false, rows);
            }
            cursor.play(move);
            current = current.getNext();
        }
    }

    /**
     * Returns a move-number label.
     *
     * @param before position before move
     * @return move number
     */
    private static String moveNumber(Position before) {
        return before.isWhiteToMove()
                ? before.fullMoveNumber() + "."
                : before.fullMoveNumber() + "...";
    }

    /**
     * Returns whether a node decodes to a move.
     *
     * @param node node
     * @param before position before node
     * @param move encoded move
     * @return true when equal
     */
    private static boolean nodeMoveEquals(Game.Node node, Position before, short move) {
        if (node == null) {
            return false;
        }
        try {
            return SAN.fromAlgebraic(before, node.getSan()) == move;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Returns a required reference.
     *
     * @param path path
     * @return reference
     */
    private Ref requireRef(StudyNodePath path) {
        Ref ref = findRef(Objects.requireNonNull(path, "path"));
        if (ref == null) {
            throw new IllegalArgumentException("missing study node: " + path);
        }
        return ref;
    }

    /**
     * Converts comment text to a storage list.
     *
     * @param comment comment
     * @return comment list
     */
    private static List<String> commentList(String comment) {
        return comment == null || comment.isBlank() ? List.of() : List.of(comment.trim());
    }

    /**
     * Internal node reference.
     *
     * @param node node
     * @param path path
     * @param beforePath path before node
     * @param before position before node
     * @param previous previous node in sequence
     * @param variationOwner owner of this variation head
     * @param variationIndex variation index
     * @param rootVariationIndex root variation index
     * @param mainline true when on the primary line
     */
    private record Ref(Game.Node node, StudyNodePath path, StudyNodePath beforePath, Position before,
            Game.Node previous, Game.Node variationOwner, int variationIndex, int rootVariationIndex,
            boolean mainline) {
    }
}
