package application.gui.workbench.board;

import chess.core.Field;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns board-annotation gesture state and persistent markup toggling.
 */
final class BoardMarkupInput {

    /**
     * Sentinel target value for circle markups.
     */
    private static final byte MARKUP_CIRCLE = Field.NO_SQUARE;

    /**
     * Persistent arrow and circle markups.
     */
    private final List<BoardMarkup> markups = new ArrayList<>();

    /**
     * Markup currently being drawn by the user.
     */
    private BoardMarkup currentMarkup;

    /**
     * Source square for a pending board markup.
     */
    private byte markupOrigin = Field.NO_SQUARE;

    /**
     * Active board markup brush.
     */
    private MarkupBrush markupBrush;

    /**
     * Brush used for unmodified annotation gestures.
     */
    private MarkupBrush directAnnotationBrush = MarkupBrush.defaultBrush();

    /**
     * Shape used by unmodified direct annotation gestures.
     */
    private BoardMarkupTool markupTool = BoardMarkupTool.ARROW;

    /**
     * True when left-click gestures create annotations instead of moving pieces.
     */
    private boolean directAnnotationMode;

    /**
     * Returns whether there are no persistent or preview annotations.
     *
     * @return true when markup state is empty
     */
    boolean isEmpty() {
        return markups.isEmpty() && currentMarkup == null;
    }

    /**
     * Returns whether an annotation gesture is active.
     *
     * @return true when a gesture has an origin square
     */
    boolean hasGesture() {
        return markupOrigin != Field.NO_SQUARE;
    }

    /**
     * Returns the persistent markups for package-local renderers.
     *
     * @return mutable persistent markup list
     */
    List<BoardMarkup> markups() {
        return markups;
    }

    /**
     * Returns a detached copy of the persistent markups.
     *
     * @return copied markup list
     */
    List<BoardMarkup> copyMarkups() {
        return new ArrayList<>(markups);
    }

    /**
     * Replaces the persistent markup list with a detached copy of the supplied
     * markups and clears any active gesture.
     *
     * @param replacement replacement markups
     */
    void replaceMarkups(List<BoardMarkup> replacement) {
        markups.clear();
        if (replacement != null) {
            markups.addAll(replacement);
        }
        clearGesture();
    }

    /**
     * Returns the preview markup currently being drawn.
     *
     * @return preview markup, or null
     */
    BoardMarkup currentMarkup() {
        return currentMarkup;
    }

    /**
     * Returns the number of persistent markups.
     *
     * @return markup count
     */
    int markupCount() {
        return markups.size();
    }

    /**
     * Removes one persistent markup by index.
     *
     * @param index markup index
     * @return true when a markup was removed
     */
    boolean removeAt(int index) {
        if (index < 0 || index >= markups.size()) {
            return false;
        }
        markups.remove(index);
        clearGesture();
        return true;
    }

    /**
     * Clears only persistent annotation markups, leaving any square highlights
     * owned by the board unchanged.
     */
    void clearMarkups() {
        markups.clear();
        clearGesture();
    }

    /**
     * Clears persistent and transient annotation state.
     */
    void clear() {
        markups.clear();
        clearGesture();
    }

    /**
     * Clears the active gesture without changing persistent markups.
     */
    void clearGesture() {
        currentMarkup = null;
        markupOrigin = Field.NO_SQUARE;
        markupBrush = null;
    }

    /**
     * Returns whether left-click annotation drawing is active.
     *
     * @return true when direct annotation input is enabled
     */
    boolean directAnnotationMode() {
        return directAnnotationMode;
    }

    /**
     * Sets whether left-click annotation drawing is active.
     *
     * @param enabled true to enable direct annotation input
     */
    void setDirectAnnotationMode(boolean enabled) {
        directAnnotationMode = enabled;
        clearGesture();
    }

    /**
     * Returns the selected direct annotation shape.
     *
     * @return annotation shape
     */
    BoardMarkupTool tool() {
        return markupTool;
    }

    /**
     * Sets the selected direct annotation shape.
     *
     * @param tool annotation shape
     */
    void setTool(BoardMarkupTool tool) {
        markupTool = tool == null ? BoardMarkupTool.ARROW : tool;
        clearGesture();
    }

    /**
     * Returns the brush used by unmodified annotation gestures.
     *
     * @return annotation brush
     */
    MarkupBrush directBrush() {
        return directAnnotationBrush;
    }

    /**
     * Sets the brush used by unmodified annotation gestures.
     *
     * @param brush annotation brush
     */
    void setDirectBrush(MarkupBrush brush) {
        if (brush == null) {
            return;
        }
        directAnnotationBrush = brush;
        clearGesture();
    }

    /**
     * Adds one already-resolved persistent markup.
     *
     * @param markup markup to append
     */
    void add(BoardMarkup markup) {
        if (markup != null) {
            markups.add(markup);
        }
    }

    /**
     * Begins one annotation gesture.
     *
     * @param origin origin square
     * @param target current target square
     * @param brush gesture brush
     */
    void begin(byte origin, byte target, MarkupBrush brush) {
        markupOrigin = origin;
        markupBrush = brush;
        currentMarkup = markupForTarget(target);
    }

    /**
     * Updates the active annotation preview.
     *
     * @param target current target square
     */
    void update(byte target) {
        currentMarkup = markupForTarget(target);
    }

    /**
     * Completes the active gesture and toggles the finished markup.
     *
     * @param target final target square
     */
    void complete(byte target) {
        BoardMarkup finished = markupForTarget(target);
        clearGesture();
        if (finished != null) {
            toggle(finished);
        }
    }

    /**
     * Chooses the brush for one annotation gesture. Unmodified gestures use the
     * selected rail brush; modifier gestures use fixed preset colors so
     * right-click annotations behave consistently in every board mode.
     *
     * @param event mouse event carrying modifier state
     * @return annotation brush for the gesture
     */
    MarkupBrush brushFor(MouseEvent event) {
        boolean modA = event.isShiftDown() || event.isControlDown();
        boolean modB = event.isAltDown() || event.isMetaDown() || event.isAltGraphDown();
        int gesture = (modA ? 1 : 0) + (modB ? 2 : 0);
        return gesture == 0 ? directAnnotationBrush : MarkupBrush.forGesture(gesture);
    }

    /**
     * Returns the persistent markup that the current preview would erase.
     *
     * @return matching markup index, or -1
     */
    int pendingEraseIndex() {
        if (currentMarkup == null) {
            return -1;
        }
        for (int i = 0; i < markups.size(); i++) {
            BoardMarkup existing = markups.get(i);
            if (sameEndpoints(existing, currentMarkup) && sameBrush(existing, currentMarkup)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Converts the active origin and target square into a board markup preview.
     *
     * @param target current target square
     * @return current preview markup, or null when the pointer is off-board
     */
    private BoardMarkup markupForTarget(byte target) {
        if (markupBrush == null || markupOrigin == Field.NO_SQUARE) {
            return null;
        }
        if (directAnnotationMode && markupTool == BoardMarkupTool.CIRCLE) {
            return new BoardMarkup(BoardMarkupTool.CIRCLE, markupOrigin, MARKUP_CIRCLE, markupBrush);
        }
        if (directAnnotationMode && markupTool == BoardMarkupTool.GLYPH) {
            return new BoardMarkup(BoardMarkupTool.GLYPH, markupOrigin, MARKUP_CIRCLE, markupBrush);
        }
        if (target == Field.NO_SQUARE) {
            return null;
        }
        if (directAnnotationMode && markupTool == BoardMarkupTool.RECTANGLE) {
            return rectangleMarkup(markupOrigin, target, markupBrush);
        }
        return new BoardMarkup(target == markupOrigin ? BoardMarkupTool.CIRCLE : BoardMarkupTool.ARROW,
                markupOrigin, target == markupOrigin ? MARKUP_CIRCLE : target, markupBrush);
    }

    /**
     * Creates a normalized rectangle markup from two corner squares.
     *
     * @param first first corner
     * @param second second corner
     * @param brush annotation brush
     * @return rectangle markup
     */
    private static BoardMarkup rectangleMarkup(byte first, byte second, MarkupBrush brush) {
        int fileA = Field.getX(first);
        int fileB = Field.getX(second);
        int rowA = first / 8;
        int rowB = second / 8;
        byte from = (byte) (Math.min(rowA, rowB) * 8 + Math.min(fileA, fileB));
        byte to = (byte) (Math.max(rowA, rowB) * 8 + Math.max(fileA, fileB));
        return new BoardMarkup(BoardMarkupTool.RECTANGLE, from, to, brush);
    }

    /**
     * Toggles one persistent annotation. A repeat gesture with the same endpoints
     * and brush erases the existing markup; a new brush on the same endpoints
     * replaces the old markup with the new one.
     *
     * @param markup completed annotation markup
     */
    private void toggle(BoardMarkup markup) {
        if (markup.isGlyph()) {
            toggleGlyph(markup);
            return;
        }
        boolean foundSameEndpoints = false;
        boolean foundSameBrush = false;
        for (int i = markups.size() - 1; i >= 0; i--) {
            BoardMarkup existing = markups.get(i);
            if (sameEndpoints(existing, markup)) {
                foundSameEndpoints = true;
                foundSameBrush |= sameBrush(existing, markup);
                markups.remove(i);
            }
        }
        if (!foundSameEndpoints || !foundSameBrush) {
            markups.add(markup);
        }
    }

    /**
     * Toggles one glyph annotation. Most glyphs can stack on the same square, but
     * mutually exclusive notation groups replace their siblings.
     *
     * @param markup completed glyph markup
     */
    private void toggleGlyph(BoardMarkup markup) {
        String replacementGroup = MarkupBrush.exclusiveGlyphGroup(markup.brush().glyph());
        for (int i = markups.size() - 1; i >= 0; i--) {
            BoardMarkup existing = markups.get(i);
            if (!sameEndpoints(existing, markup)) {
                continue;
            }
            if (sameBrush(existing, markup)) {
                markups.remove(i);
                return;
            }
            String existingGroup = MarkupBrush.exclusiveGlyphGroup(existing.brush().glyph());
            if (replacementGroup != null && replacementGroup.equals(existingGroup)) {
                markups.remove(i);
            }
        }
        markups.add(markup);
    }

    /**
     * Returns whether two markups point at the same arrow or circle endpoints.
     *
     * @param first first markup
     * @param second second markup
     * @return true when endpoints match
     */
    private static boolean sameEndpoints(BoardMarkup first, BoardMarkup second) {
        return first.tool() == second.tool() && first.from() == second.from() && first.to() == second.to();
    }

    /**
     * Returns whether two markups use the same user-visible brush.
     *
     * @param first first markup
     * @param second second markup
     * @return true when brushes match
     */
    private static boolean sameBrush(BoardMarkup first, BoardMarkup second) {
        return first.brush().matches(second.brush());
    }
}
