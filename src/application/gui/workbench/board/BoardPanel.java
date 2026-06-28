package application.gui.workbench.board;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.ui.EvalBar;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.PremoveGeometry;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Native Java2D chess board used by the CRTK Workbench.
 */
public final class BoardPanel extends JPanel {
    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Minimum pointer travel before a press becomes a drag.
     */
    private static final int DRAG_THRESHOLD = 5;

    /**
     * Scale factor for dragged piece images.
     */
    static final double DRAG_SCALE = 1.12;

    /**
     * Maximum number of annotation history entries retained for undo/redo.
     */
    private static final int MARKUP_HISTORY_LIMIT = 64;

    /**
     * Current position rendered on the board.
     */
    Position position;

    /**
     * True when White is rendered at the bottom.
     */
    boolean whiteDown = true;

    /**
     * Optional material strip above the board (the top side's captures), or null.
     */
    private transient MaterialStrip materialTop;

    /**
     * Optional material strip below the board (the bottom side's captures), or null.
     */
    private transient MaterialStrip materialBottom;

    /**
     * Last played move and suggested engine move.
     */
    short lastMove = Move.NO_MOVE, suggestedMove = Move.NO_MOVE;

    /**
     * Queued premove arrow, independent from the legal suggested-move arrow.
     */
    short pendingPremove = Move.NO_MOVE;

    /**
     * Piece selection and drag/drop state.
     */
    final BoardPieceInput pieceInput = new BoardPieceInput();

    /**
     * Move handler used for drag-drop play.
     */
    private MoveHandler moveHandler;

    /**
     * Cached board images for piece rendering.
     */
    final BoardImageCache imageCache = new BoardImageCache();

    /**
     * Painter for board arrows.
     */
    final BoardArrowPainter arrowPainter = new BoardArrowPainter();

    /**
     * Painter for user board markups.
     */
    final BoardMarkupPainter markupPainter = new BoardMarkupPainter();

    /**
     * Painter for Java2D board rendering.
     */
    private final BoardPanelPainter painter = new BoardPanelPainter(this);

    /**
     * Layout helper that owns board bounds, square geometry, and eval-bar placement.
     */
    private final BoardPanelLayout layout = new BoardPanelLayout(this);

    /**
     * Cached legal move list for the current position.
     */
    private MoveList cachedLegalMoves;

    /**
     * Board overlay visibility toggles.
     */
    boolean showNotation = true, showLegalMovePreview = true,
            showLastMoveHighlight = true, showSuggestedMoveArrow = true, showSpecialMoveHints,
            showUndefendedPieces, showPinnedPieces, showCheckableKing;

    /**
     * Optional user-selected board square colors.
     */
    private Color boardLightColor, boardDarkColor;

    /**
     * Move, snap, flip, and wrong-move marker animation state.
     */
    final BoardAnimationState animationState = new BoardAnimationState();

    /**
     * Timer and dirty-repaint driver for board animations.
     */
    private final BoardPanelAnimation animation = new BoardPanelAnimation(this);

    /**
     * Programmatic square highlights.
     */
    final Map<Byte, Color> squareHighlights = new LinkedHashMap<>();

    /**
     * Persistent annotation list and transient annotation gesture state.
     */
    final BoardMarkupInput markupInput = new BoardMarkupInput();

    /**
     * Undo history for user-created board annotations.
     */
    private final Deque<List<BoardMarkup>> markupUndoStack = new ArrayDeque<>();

    /**
     * Redo history for user-created board annotations.
     */
    private final Deque<List<BoardMarkup>> markupRedoStack = new ArrayDeque<>();

    /**
     * Observer notified when persistent annotations change.
     */
    private Runnable markupChangeObserver;

    /**
     * Optional drag-start filter.
     */
    private Predicate<DragContext> dragStartFilter;

    /**
     * Optional drag-start filter for premoves while the opponent is to move.
     */
    private Predicate<DragContext> premoveStartFilter;

    /**
     * Optional handler for queued premoves.
     */
    private PremoveHandler premoveHandler;

    /**
     * True when the current selection/drag is a premove gesture.
     */
    boolean premoveSelection;

    /**
     * Optional drop resolver for custom board interactions.
     */
    private ToIntFunction<DropContext> dropResolver;

    /**
     * FEN and SAN change observer.
     */
    private BiConsumer<String, String> changeObserver;

    /**
     * Snapback and snap completion observers.
     */
    Runnable snapbackEndObserver, snapEndObserver;

    /**
     * True when illegal-move snapback sound is enabled.
     */
    private boolean snapbackSoundEnabled = true;

    /**
     * Mouseover-square observer.
     */
    private IntConsumer mouseoverSquareObserver;

    /**
     * Optional square-click observer for read-only board tools.
     */
    private IntConsumer clickSquareObserver;

    /**
     * Last square reported to the mouseover observer.
     */
    private byte lastMouseoverSquare = Field.NO_SQUARE;

    /**
     * Direct board setup-edit controller.
     */
    final BoardSetupEditor setupEditor = new BoardSetupEditor(this);

    /**
     * Creates the board panel.
     */
    public BoardPanel() {
        setOpaque(true);
        setBackground(Theme.BG);
        setPreferredSize(BoardPanelLayout.preferredSize());
        setMinimumSize(BoardPanelLayout.minimumSize());
        MouseAdapter mouseHandler = new MouseAdapter() {
            /**
             * Starts board pointer interaction from a mouse press.
             */
            @Override
            public void mousePressed(MouseEvent event) {
                handlePress(event);
            }
            /**
             * Completes a board pointer interaction.
             */
            @Override
            public void mouseReleased(MouseEvent event) {
                handleRelease(event);
            }
            /**
             * Updates drag-and-drop piece movement.
             */
            @Override
            public void mouseDragged(MouseEvent event) {
                handleDrag(event);
            }
            /**
             * Updates the cursor for draggable pieces.
             */
            @Override
            public void mouseMoved(MouseEvent event) {
                updateCursor(event);
            }
            /**
             * Restores the default cursor after leaving the board.
             */
            @Override
            public void mouseExited(MouseEvent event) {
                setCursor(Cursor.getDefaultCursor());
                notifyMouseover(Field.NO_SQUARE);
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
            }
        });
    }
    /**
     * Stops the animation timer when the panel is detached so a recreated panel (theme reload, tab swap) does not leak the previous instance via t...
     */
    @Override
    public void removeNotify() {
        animation.stop();
        super.removeNotify();
    }
    /**
     * Sets the position.
     * @param value new value
     * @param move move encoded in CRTK move format
     */
    public void setPosition(Position value, short move) {
        setPosition(value, move, false);
    }
    /**
     * Sets the position and optionally reverses the move glide.
     * @param value new value
     * @param move move encoded in CRTK move format
     * @param reverseMoveAnimation true to glide the move from destination back to origin
     */
    public void setPosition(Position value, short move, boolean reverseMoveAnimation) {
        setPosition(value, move, reverseMoveAnimation, true);
    }
    /**
     * Sets the position without starting a move animation.
     * @param value new value
     * @param move move encoded in CRTK move format
     */
    public void setPositionInstant(Position value, short move) {
        setPosition(value, move, false, false);
    }
    /**
     * Installs a new position, invalidates cached legal moves, and optionally
     * starts the move animation.
     *
     * @param value new position, or {@code null} for an empty board
     * @param move encoded move
     * @param reverseMoveAnimation whether to animate from destination to source
     * @param animateMove whether to animate the board transition
     */
    private void setPosition(Position value, short move, boolean reverseMoveAnimation, boolean animateMove) {
        byte[] previousBoard = position == null ? null : position.getBoard();
        position = value;
        cachedLegalMoves = null;
        lastMove = move;
        suggestedMove = Move.NO_MOVE;
        pendingPremove = Move.NO_MOVE;
        clearSelection();
        clearDragState();
        if (!animateMove || animationState.consumeSuppressNextMoveAnimation()) {
            animation.clearMoveAnimation();
        } else {
            animation.startMoveAnimation(previousBoard, position == null ? null : position.getBoard(),
                    move, reverseMoveAnimation);
        }
        animation.clearWrongMoveMarkerState();
        refreshMaterialStrips();
        repaint();
    }

    /**
     * Attaches the material strips painted above and below the board. They are
     * refreshed automatically whenever the position or orientation changes.
     *
     * @param top strip above the board (top side's captures)
     * @param bottom strip below the board (bottom side's captures)
     */
    public void setMaterialStrips(MaterialStrip top, MaterialStrip bottom) {
        this.materialTop = top;
        this.materialBottom = bottom;
        refreshMaterialStrips();
    }

    /**
     * Updates the material strips for the current position and orientation. The
     * bottom strip shows the side rendered at the bottom of the board.
     */
    private void refreshMaterialStrips() {
        if (materialTop == null || materialBottom == null) {
            return;
        }
        boolean bottomIsWhite = whiteDown;
        materialBottom.update(position, bottomIsWhite);
        materialTop.update(position, !bottomIsWhite);
    }
    /**
     * Returns cached legal moves for the current position.
     *
     * @return legal moves, or {@code null} when the board is empty
     */
    private MoveList currentLegalMoves() {
        if (position == null) {
            return null;
        }
        if (cachedLegalMoves == null) {
            cachedLegalMoves = position.legalMoves();
        }
        return cachedLegalMoves;
    }
    /**
     * Sets board orientation.
     * @param value new value
     */
    public void setWhiteDown(boolean value) {
        whiteDown = value;
        refreshMaterialStrips();
        repaint();
    }
    /**
     * Returns board orientation.
     * @return true when white is rendered at the bottom
     */
    public boolean isWhiteDown() {
        return whiteDown;
    }
    /**
     * Sets a suggested move.
     * @param move move encoded in CRTK move format
     */
    public void setSuggestedMove(short move) {
        short next = legalSuggestedMove(move) ? move : Move.NO_MOVE;
        if (next == suggestedMove) {
            return;
        }
        suggestedMove = next;
        // The arrow co-exists with the user's own selection / drag: clearing
        // them here cancelled a move the moment the engine replied.
        repaint();
    }

    /**
     * Shows or clears the queued premove square highlights. Unlike suggested
     * moves, premoves are not required to be legal in the current position
     * because they execute only after the opponent reply.
     *
     * @param move queued premove, or {@link Move#NO_MOVE}
     */
    public void setPremove(short move) {
        short next = legalPremoveShape(move) ? move : Move.NO_MOVE;
        if (next == pendingPremove) {
            return;
        }
        pendingPremove = next;
        repaint();
    }

    /**
     * Clears any queued premove highlight.
     */
    public void clearPremove() {
        setPremove(Move.NO_MOVE);
    }

    /**
     * Returns whether a premove has a plausible source and target square.
     *
     * @param move encoded move
     * @return true when the move shape can be queued as a premove
     */
    static boolean legalPremoveShape(short move) {
        return PremoveGeometry.isEncodedShape(move);
    }

    /**
     * Returns whether the suggested move is legal in the current position.
     *
     * @param move encoded move
     * @return true when the current legal-move cache contains the move
     */
    boolean legalSuggestedMove(short move) {
        if (move == Move.NO_MOVE || position == null) {
            return false;
        }
        MoveList moves = currentLegalMoves();
        return moves != null && moves.contains(move);
    }
    /**
     * Returns the current FEN, or null when no position has been set. chessboard.js position() equivalent.
     * @return current FEN string, or null when no position is loaded
     */
    public String position() {
        return position == null ? null : position.toString();
    }
    /**
     * Replaces the position from a FEN. chessboard.js position(fen, useAnimation) equivalent.
     * @param fen FEN string
     * @param animate true to animate the position change
     */
    public void position(String fen, boolean animate) {
        if (fen == null || fen.equalsIgnoreCase("empty")) {
            applyPosition(null, Move.NO_MOVE, animate);
            return;
        }
        String resolved = fen.equalsIgnoreCase("start") ? chess.core.Setup.getStandardStartFEN() : fen;
        Position parsed;
        try {
            parsed = new Position(resolved);
        } catch (RuntimeException ex) {
            // Malformed FEN — keep the current position rather than letting
            // the parse failure unwind into UI handlers (palette, scripted callers).
            return;
        }
        applyPosition(parsed, Move.NO_MOVE, animate);
    }
    /**
     * Resets to the standard start position. chessboard.js start().
     */
    public void start() {
        position("start", true);
    }
    /**
     * Clears the board (no pieces). chessboard.js clear().
     */
    public void clear() {
        position("empty", true);
    }
    /**
     * Toggles board orientation with a smooth flip animation. chessboard.js flip().
     */
    public void flip() {
        // If a previous flip is still pending its midpoint commit, apply that
        // commit now so a rapid double-flip toggles the orientation twice
        // instead of swallowing the second call.
        if (animationState.flipPending()) {
            whiteDown = !whiteDown;
            animationState.clearFlipPending();
        }
        if (!animationState.canAnimateFlip() || getWidth() <= 0) {
            // Headless or zero-sized panel: flip immediately.
            whiteDown = !whiteDown;
            repaint();
            return;
        }
        animationState.startFlipAnimation(System.currentTimeMillis());
        animation.startAnimation();
        repaint();
    }
    /**
     * Sets the orientation explicitly.
     * @param value new value
     */
    public void orientation(String value) {
        boolean nextWhiteDown = "white".equalsIgnoreCase(value);
        if (nextWhiteDown == whiteDown) {
            return;
        }
        flip();
    }
    /**
     * Returns the current orientation as "white" or "black".
     * @return current orientation value
     */
    public String orientation() {
        return whiteDown ? "white" : "black";
    }
    /**
     * Toggles whether file/rank coordinates are painted. chessboard.js showNotation.
     * @param show true to show the feature
     */
    public void setShowNotation(boolean show) {
        if (show == showNotation) {
            return;
        }
        showNotation = show;
        repaint();
    }

    /**
     * Toggles whether glyph annotation badges are drawn with a drop shadow.
     *
     * @param show true to draw glyph badges with a soft drop shadow
     */
    public void setGlyphShadow(boolean show) {
        if (show == markupPainter.isGlyphShadow()) {
            return;
        }
        markupPainter.setGlyphShadow(show);
        repaint();
    }

    /**
     * Returns whether glyph annotation badges are drawn with a drop shadow.
     *
     * @return true when the glyph drop shadow is enabled
     */
    public boolean isGlyphShadow() {
        return markupPainter.isGlyphShadow();
    }
    /**
     * Returns whether file/rank coordinates are currently painted.
     * @return true when board notation is painted
     */
    public boolean isShowNotation() {
        return showNotation;
    }
    /**
     * Sets whether selected-piece legal destinations and drag targets are painted.
     * @param show true to show the feature
     */
    public void setShowLegalMovePreview(boolean show) {
        if (show == showLegalMovePreview) {
            return;
        }
        showLegalMovePreview = show;
        repaint();
    }

    /**
     * Selects the piece artwork set, re-rendering cached piece bitmaps and
     * repainting when the set changes.
     *
     * @param set piece artwork set
     */
    public void setPieceSet(chess.images.assets.PieceSet set) {
        if (imageCache.pieceSet(set)) {
            repaint();
        }
    }

    /**
     * Returns the active piece artwork set.
     *
     * @return active piece set
     */
    public chess.images.assets.PieceSet pieceSet() {
        return imageCache.pieceSet();
    }
    /**
     * Returns whether legal move previews are painted.
     *
     * @return true when legal move previews are painted
     */
    public boolean isShowLegalMovePreview() {
        return showLegalMovePreview;
    }

    /**
     * Sets whether the previous move should be highlighted.
     *
     * @param show true to show the feature
     */
    public void setShowLastMoveHighlight(boolean show) {
        if (show == showLastMoveHighlight) {
            return;
        }
        showLastMoveHighlight = show;
        repaint();
    }

    /**
     * Returns whether previous-move highlights are painted.
     *
     * @return true when previous-move highlights are painted
     */
    public boolean isShowLastMoveHighlight() {
        return showLastMoveHighlight;
    }

    /**
     * Sets whether suggested engine moves should be painted as arrows.
     *
     * @param show true to show the feature
     */
    public void setShowSuggestedMoveArrow(boolean show) {
        if (show == showSuggestedMoveArrow) {
            return;
        }
        showSuggestedMoveArrow = show;
        repaint();
    }

    /**
     * Returns whether suggested engine arrows are painted.
     *
     * @return true when suggested-move arrows are painted
     */
    public boolean isShowSuggestedMoveArrow() {
        return showSuggestedMoveArrow;
    }

    /**
     * Sets whether castling-right and en-passant hint arrows are painted.
     *
     * @param show true to show special-move hints
     */
    public void setShowSpecialMoveHints(boolean show) {
        if (show == showSpecialMoveHints) {
            return;
        }
        showSpecialMoveHints = show;
        repaint();
    }

    /**
     * Returns whether castling-right and en-passant hint arrows are painted.
     *
     * @return true when special-move hints are painted
     */
    public boolean isShowSpecialMoveHints() {
        return showSpecialMoveHints;
    }

    /**
     * Sets whether attacked, undefended pieces are marked with board badges.
     *
     * @param show true to show undefended-piece badges
     */
    public void setShowUndefendedPieces(boolean show) {
        if (show == showUndefendedPieces) {
            return;
        }
        showUndefendedPieces = show;
        repaint();
    }

    /**
     * Returns whether undefended-piece badges are painted.
     *
     * @return true when undefended-piece badges are painted
     */
    public boolean isShowUndefendedPieces() {
        return showUndefendedPieces;
    }

    /**
     * Sets whether pieces pinned to their own king are marked with board badges.
     *
     * @param show true to show pinned-piece badges
     */
    public void setShowPinnedPieces(boolean show) {
        if (show == showPinnedPieces) {
            return;
        }
        showPinnedPieces = show;
        repaint();
    }

    /**
     * Returns whether pinned-piece badges are painted.
     *
     * @return true when pinned-piece badges are painted
     */
    public boolean isShowPinnedPieces() {
        return showPinnedPieces;
    }

    /**
     * Sets whether the opponent king is marked when a legal checking move exists.
     *
     * @param show true to show the checkable-king badge
     */
    public void setShowCheckableKing(boolean show) {
        if (show == showCheckableKing) {
            return;
        }
        showCheckableKing = show;
        repaint();
    }

    /**
     * Returns whether the checkable-king badge is painted.
     *
     * @return true when the checkable-king badge is painted
     */
    public boolean isShowCheckableKing() {
        return showCheckableKing;
    }

    /**
     * Sets custom board square colors.
     *
     * @param light light-square color
     * @param dark dark-square color
     */
    public void setBoardColors(Color light, Color dark) {
        Color nextLight = light == null ? null : opaqueColor(light);
        Color nextDark = dark == null ? null : opaqueColor(dark);
        if (Objects.equals(boardLightColor, nextLight) && Objects.equals(boardDarkColor, nextDark)) {
            return;
        }
        boardLightColor = nextLight;
        boardDarkColor = nextDark;
        repaint();
    }

    /**
     * Returns the opaque color.
     *
     * @param color display color
     * @return opaque color
     */
    private static Color opaqueColor(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Restores theme board square colors.
     */
    public void resetBoardColors() {
        setBoardColors(null, null);
    }

    /**
     * Returns the active light-square color.
     *
     * @return light-square color
     */
    public Color boardLightColor() {
        return boardLightColor == null ? Theme.BOARD_LIGHT : boardLightColor;
    }

    /**
     * Returns the active dark-square color.
     *
     * @return dark-square color
     */
    public Color boardDarkColor() {
        return boardDarkColor == null ? Theme.BOARD_DARK : boardDarkColor;
    }

    /**
     * Highlights one square with an arbitrary color.
     *
     * @param square board square index
     * @param color display color
     */
    public void highlightSquare(byte square, Color color) {
        if (!isSquareIndex(square) || color == null) {
            return;
        }
        squareHighlights.put(square, color);
        repaint();
    }

    /**
     * Removes all square highlights and arrow markups from the shared board
     * surface.
     */
    public void clearMarkup() {
        if (squareHighlights.isEmpty() && markupInput.isEmpty()) {
            return;
        }
        squareHighlights.clear();
        markupInput.clear();
        notifyMarkupChanged();
        repaint();
    }

    /**
     * Enables or disables direct annotation input. When enabled, left-click
     * gestures create board annotations; right-click annotations remain
     * available in all modes.
     *
     * @param enabled true to route left-click gestures to annotation drawing
     */
    public void setDirectAnnotationMode(boolean enabled) {
        if (markupInput.directAnnotationMode() == enabled) {
            return;
        }
        markupInput.setDirectAnnotationMode(enabled);
        clearDragState();
        clearSelection();
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    /**
     * Selects the shape used by unmodified direct annotation gestures.
     *
     * @param tool annotation shape
     */
    public void setMarkupTool(BoardMarkupTool tool) {
        markupInput.setTool(tool);
        repaint();
    }

    /**
     * Returns the shape used by unmodified direct annotation gestures.
     *
     * @return annotation shape
     */
    public BoardMarkupTool markupTool() {
        return markupInput.tool();
    }

    /**
     * Selects the brush used by unmodified direct annotation gestures.
     *
     * @param brush annotation brush
     */
    public void setDirectAnnotationBrush(MarkupBrush brush) {
        if (brush == null) {
            return;
        }
        markupInput.setDirectBrush(brush);
        repaint();
    }

    /**
     * Returns the brush used by unmodified direct annotation gestures.
     *
     * @return annotation brush
     */
    public MarkupBrush directAnnotationBrush() {
        return markupInput.directBrush();
    }

    /**
     * Returns the number of persistent board annotations.
     *
     * @return annotation count
     */
    public int markupCount() {
        return markupInput.markupCount();
    }

    /**
     * Returns a detached copy of persistent board annotations.
     *
     * @return copied annotation list
     */
    public List<BoardMarkup> boardMarkups() {
        return markupInput.copyMarkups();
    }

    /**
     * Returns the persistent board annotations as a PGN comment body, using
     * Lichess {@code [%cal]}/{@code [%csl]} directives for arrows/circles and crtk
     * {@code [%cgl]}/{@code [%crl]} extensions for glyph badges and rectangles.
     *
     * @return PGN comment body, or an empty string when there are no annotations
     */
    public String markupComment() {
        return BoardMarkupComment.encode(markupInput.copyMarkups());
    }

    /**
     * Replaces the persistent board annotations with those parsed from a PGN
     * comment body (inverse of {@link #markupComment()}).
     *
     * @param comment PGN comment body containing graphical directives
     */
    public void applyMarkupComment(String comment) {
        List<BoardMarkup> before = markupInput.copyMarkups();
        markupInput.replaceMarkups(BoardMarkupComment.decode(comment));
        recordMarkupEdit(before);
        notifyMarkupChanged();
        repaint();
    }

    /**
     * Clears user annotations while preserving non-annotation square highlights.
     */
    public void clearUserMarkup() {
        if (markupInput.markupCount() == 0 && !markupInput.hasGesture()) {
            return;
        }
        List<BoardMarkup> before = markupInput.copyMarkups();
        markupInput.clearMarkups();
        recordMarkupEdit(before);
        notifyMarkupChanged();
        repaint();
    }

    /**
     * Removes one persistent board annotation by list index.
     *
     * @param index annotation index
     */
    public void removeMarkup(int index) {
        List<BoardMarkup> before = markupInput.copyMarkups();
        if (!markupInput.removeAt(index)) {
            return;
        }
        recordMarkupEdit(before);
        notifyMarkupChanged();
        repaint();
    }

    /**
     * Returns whether an annotation edit can be undone.
     *
     * @return true when undo history is available
     */
    public boolean canUndoMarkupEdit() {
        return !markupUndoStack.isEmpty();
    }

    /**
     * Returns whether an undone annotation edit can be restored.
     *
     * @return true when redo history is available
     */
    public boolean canRedoMarkupEdit() {
        return !markupRedoStack.isEmpty();
    }

    /**
     * Restores the previous user-annotation snapshot when available.
     */
    public void undoMarkupEdit() {
        if (markupUndoStack.isEmpty()) {
            return;
        }
        markupRedoStack.push(markupInput.copyMarkups());
        restoreMarkupSnapshot(markupUndoStack.pop());
    }

    /**
     * Reapplies the next user-annotation snapshot when available.
     */
    public void redoMarkupEdit() {
        if (markupRedoStack.isEmpty()) {
            return;
        }
        markupUndoStack.push(markupInput.copyMarkups());
        restoreMarkupSnapshot(markupRedoStack.pop());
    }

    /**
     * Sets an observer notified after persistent annotation changes.
     *
     * @param observer callback observer, or null to clear
     */
    public void setMarkupChangeObserver(Runnable observer) {
        markupChangeObserver = observer;
    }

    /**
     * Shows a temporary chess-web-style wrong-move badge on one square.
     *
     * @param square board square index
     */
    public void showWrongMoveMarker(byte square) {
        if (!isSquareIndex(square)) {
            return;
        }
        animationState.showWrongMoveMarker(square, System.currentTimeMillis());
        animation.startAnimation();
        repaint();
    }

    /**
     * Clears the temporary wrong-move badge.
     */
    public void clearWrongMoveMarker() {
        if (animationState.wrongMoveMarkerSquare() == Field.NO_SQUARE) {
            return;
        }
        animation.clearWrongMoveMarkerState();
        repaint();
    }

    /**
     * Adds a persistent arrow markup between two squares using the standard
     * Workbench annotation width.
     *
     * @param from origin square
     * @param to destination square
     * @param color display color
     */
    public void addArrow(byte from, byte to, Color color) {
        addArrowMarkup(from, to, color == null ? null : MarkupBrush.forColor(color));
    }

    /**
     * Adds a persistent arrow with an exact colour and line width, for dense
     * overlays such as the tactical-incidence relation graph where the default
     * markup width is too heavy.
     *
     * @param from origin square
     * @param to destination square
     * @param color exact arrow colour (alpha honoured)
     * @param lineWidth arrow line width in pixels
     */
    public void addArrow(byte from, byte to, Color color, int lineWidth) {
        addArrowMarkup(from, to, color == null ? null : MarkupBrush.custom(color, lineWidth));
    }

    /**
     * Adds one already-resolved arrow brush to the persistent board markup list.
     *
     * @param from origin square
     * @param to destination square
     * @param brush resolved annotation brush
     */
    private void addArrowMarkup(byte from, byte to, MarkupBrush brush) {
        if (!isSquareIndex(from) || !isSquareIndex(to) || brush == null) {
            return;
        }
        markupInput.add(new BoardMarkup(from, to, brush));
        repaint();
    }

    /**
     * Sets the duration of move/snapback/snap/flip animations.
     * @param moveMs move animation duration in milliseconds
     * @param snapbackMs snapback animation duration in milliseconds
     * @param snapMs snap animation duration in milliseconds
     * @param flipMs flip animation duration in milliseconds
     */
    public void setAnimationSpeeds(int moveMs, int snapbackMs, int snapMs, int flipMs) {
        animationState.setAnimationSpeeds(moveMs, snapbackMs, snapMs, flipMs);
        if (!animationState.animationsEnabled()) {
            animation.clearAllAnimations();
        }
    }
    /**
     * Enables or disables board animations without changing their configured speeds.
     * @param enabled true to enable the behavior
     */
    public void setAnimationsEnabled(boolean enabled) {
        if (enabled == animationState.animationsEnabled()) {
            return;
        }
        animationState.setAnimationsEnabled(enabled);
        if (!enabled) {
            animation.clearAllAnimations();
        }
        repaint();
    }
    /**
     * Returns whether board animations are enabled.
     * @return true when board animations are enabled
     */
    public boolean isAnimationsEnabled() {
        return animationState.animationsEnabled();
    }
    /**
     * Sets a drag-start filter; called before each drag begins.
     * @param filter drag-start filter
     */
    public void setDragStartFilter(Predicate<DragContext> filter) {
        dragStartFilter = filter;
    }

    /**
     * Sets a premove drag-start filter; called for non-side-to-move pieces while
     * a premove handler is installed.
     *
     * @param filter premove drag-start filter
     */
    public void setPremoveStartFilter(Predicate<DragContext> filter) {
        premoveStartFilter = filter;
    }

    /**
     * Sets a premove handler for queuing future human moves while the opponent
     * is thinking.
     *
     * @param handler premove handler
     */
    public void setPremoveHandler(PremoveHandler handler) {
        premoveHandler = handler;
    }

    /**
     * Sets a drop resolver; called when the user drops a piece.
     * @param resolver drop resolver callback
     */
    public void setDropResolver(ToIntFunction<DropContext> resolver) {
        dropResolver = resolver;
    }
    /**
     * Sets whether illegal drag snapbacks should play the generic board cue.
     * @param enabled true to play the snapback cue
     */
    public void setSnapbackSoundEnabled(boolean enabled) {
        snapbackSoundEnabled = enabled;
    }
    /**
     * Sets a callback fired when the user left-clicks a board square.
     *
     * @param observer callback observer, or null to clear
     */
    public void setClickSquareObserver(IntConsumer observer) {
        clickSquareObserver = observer;
    }

    /**
     * Enables direct setup editing on the board surface.
     * @param enabled true when setup editing should intercept board input
     */
    public void setSetupEditMode(boolean enabled) {
        setupEditor.setMode(enabled);
    }

    /**
     * Returns whether setup editing is active.
     * @return true when setup editing is active
     */
    public boolean isSetupEditMode() {
        return setupEditor.active();
    }

    /**
     * Replaces the board shown by setup editing.
     * @param board board piece array, or null to clear
     */
    public void setSetupEditBoard(byte[] board) {
        setupEditor.setBoard(board);
    }

    /**
     * Sets the piece painted by a left click in setup editing mode.
     * @param piece piece code, or {@link Piece#EMPTY} for erase
     */
    public void setSetupEditSelectedPiece(byte piece) {
        setupEditor.setSelectedPiece(piece);
    }

    /**
     * Sets one setup-edit square and notifies the editor model.
     * @param square board square index
     * @param piece piece code
     */
    public void setSetupEditPieceAt(byte square, byte piece) {
        setupEditor.setPieceAt(square, piece);
    }

    /**
     * Returns one setup-edit square.
     * @param square board square index
     * @return piece code
     */
    public byte setupEditPieceAt(byte square) {
        return setupEditor.pieceAt(square);
    }

    /**
     * Sets the setup-edit observer.
     * @param observer observer, or null
     */
    public void setSetupEditObserver(BiConsumer<Byte, Byte> observer) {
        setupEditor.setObserver(observer);
    }

    /**
     * Applies a chessboard.js-style position change and notifies observers when
     * the FEN changes.
     *
     * @param next next position, or {@code null} for an empty board
     * @param move encoded move
     * @param animate whether to animate the transition
     */
    private void applyPosition(Position next, short move, boolean animate) {
        String oldFen = position == null ? null : position.toString();
        if (animate) {
            setPosition(next, move);
        } else {
            position = next;
            cachedLegalMoves = null;
            lastMove = move;
            suggestedMove = Move.NO_MOVE;
            pendingPremove = Move.NO_MOVE;
            clearSelection();
            clearDragState();
            animation.clearMoveAnimation();
            animation.clearWrongMoveMarkerState();
            repaint();
        }
        String newFen = next == null ? null : next.toString();
        if (changeObserver != null && !Objects.equals(oldFen, newFen)) {
            changeObserver.accept(oldFen, newFen);
        }
    }
    /**
     * Sets the move handler.
     * @param handler move handler callback
     */
    public void setMoveHandler(MoveHandler handler) {
        moveHandler = handler;
    }
    /**
     * Paints the board shell, low-level coordinates, highlights, pieces, and suggested move.
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            painter.paint(g, boardBounds());
        } finally {
            g.dispose();
        }
    }
    /**
     * Returns the cached board texture for tests and diagnostic callers.
     *
     * @param size texture size
     * @return cached texture
     */
    BufferedImage boardTexture(int size) {
        return imageCache.boardTexture(size, boardLightColor(), boardDarkColor());
    }

    /**
     * Primes the piece-image cache used during drag painting.
     *
     * @param piece encoded piece
     */
    private void warmDragPieceImage(byte piece) {
        painter.warmDragPieceImage(piece);
    }

    /**
     * Handles board mouse presses for setup editing, annotation gestures, moves,
     * premoves, and promotion choices.
     *
     * @param event mouse press event
     */
    private void handlePress(MouseEvent event) {
        if (setupEditor.active()) {
            setupEditor.handle(event);
            return;
        }
        if (markupInput.directAnnotationMode() && SwingUtilities.isLeftMouseButton(event)) {
            handleMarkupPress(event);
            return;
        }
        if (SwingUtilities.isRightMouseButton(event)) {
            handleMarkupPress(event);
            return;
        }
        byte clickedSquare = squareAt(event.getX(), event.getY());
        if (clickSquareObserver != null && SwingUtilities.isLeftMouseButton(event)
                && clickedSquare != Field.NO_SQUARE) {
            clickSquareObserver.accept(clickedSquare);
            if (moveHandler == null && !markupInput.directAnnotationMode()) {
                event.consume();
                return;
            }
        }
        if (position == null || (moveHandler == null && premoveHandler == null)) {
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        if (promotionOverlay != null) {
            short chosen = promotionOverlay.hitTest(event.getX(), event.getY(), boardBounds(), whiteDown);
            cancelPromotionOverlay();
            if (chosen != Move.NO_MOVE) {
                moveHandler.play(chosen);
            }
            return;
        }
        suggestedMove = Move.NO_MOVE;
        clearDragState();
        byte square = clickedSquare;
        if (square == Field.NO_SQUARE) {
            return;
        }
        if (pieceInput.hasSelection()) {
            if (premoveSelection) {
                byte from = pieceInput.selectedSquare();
                byte selectedPiece = isSquareIndex(from) ? position.getBoard()[from] : Piece.EMPTY;
                if (queuePremove(from, square, selectedPiece)) {
                    clearSelection();
                    repaint();
                    return;
                }
            }
            short[] candidates = pieceInput.selectedMovesTo(square);
            if (candidates.length > 0) {
                clearSelection();
                playOrPromote(candidates, event.getX(), event.getY());
                return;
            }
        }
        byte piece = position.getBoard()[square];
        if (moveHandler != null && piece != Piece.EMPTY && Piece.isWhite(piece) == position.isWhiteToMove()) {
            if (dragStartFilter != null
                    && !dragStartFilter.test(new DragContext(square, piece, position.toString()))) {
                clearSelection();
                repaint();
                return;
            }
            selectSquare(square);
            pieceInput.startDrag(square, piece, event.getX(), event.getY());
            warmDragPieceImage(piece);
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        } else if (isPremoveSource(square, piece)) {
            selectPremoveSquare(square);
            pieceInput.startDrag(square, piece, event.getX(), event.getY());
            warmDragPieceImage(piece);
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        } else {
            clearSelection();
        }
        repaint();
    }
    /**
     * Begins one board-annotation gesture from the pressed square.
     *
     * @param event press event
     */
    private void handleMarkupPress(MouseEvent event) {
        byte square = squareAt(event.getX(), event.getY());
        if (square == Field.NO_SQUARE) {
            return;
        }
        event.consume();
        clearDragState();
        clearSelection();
        cancelPromotionOverlay();
        markupInput.begin(square, square, markupInput.brushFor(event));
        repaint();
    }

    /**
     * Updates the preview annotation while the pointer is dragged.
     *
     * @param event drag event
     */
    private void handleMarkupDrag(MouseEvent event) {
        event.consume();
        markupInput.update(squareAt(event.getX(), event.getY()));
        repaint();
    }

    /**
     * Completes the active annotation gesture and toggles the resulting markup.
     *
     * @param event release event
     */
    private void handleMarkupRelease(MouseEvent event) {
        event.consume();
        List<BoardMarkup> before = markupInput.copyMarkups();
        markupInput.complete(squareAt(event.getX(), event.getY()));
        recordMarkupEdit(before);
        notifyMarkupChanged();
        repaint();
    }

    /**
     * Records an undo snapshot when a user annotation edit changed the markups.
     *
     * @param before annotation list before the edit
     */
    private void recordMarkupEdit(List<BoardMarkup> before) {
        List<BoardMarkup> after = markupInput.copyMarkups();
        if (sameMarkupList(before, after)) {
            return;
        }
        markupUndoStack.push(detachedMarkupList(before));
        while (markupUndoStack.size() > MARKUP_HISTORY_LIMIT) {
            markupUndoStack.removeLast();
        }
        markupRedoStack.clear();
    }

    /**
     * Restores a persistent annotation snapshot.
     *
     * @param snapshot snapshot to restore
     */
    private void restoreMarkupSnapshot(List<BoardMarkup> snapshot) {
        markupInput.replaceMarkups(detachedMarkupList(snapshot));
        notifyMarkupChanged();
        repaint();
    }

    /**
     * Returns a detached copy of a markup list.
     *
     * @param markups source markups
     * @return detached list
     */
    private static List<BoardMarkup> detachedMarkupList(List<BoardMarkup> markups) {
        return markups == null ? List.of() : new ArrayList<>(markups);
    }

    /**
     * Returns whether two markup lists contain the same annotations in order.
     *
     * @param first first list
     * @param second second list
     * @return true when lists match
     */
    private static boolean sameMarkupList(List<BoardMarkup> first, List<BoardMarkup> second) {
        return detachedMarkupList(first).equals(detachedMarkupList(second));
    }

    /**
     * Notifies the annotation observer if one is installed.
     */
    private void notifyMarkupChanged() {
        if (markupChangeObserver != null) {
            markupChangeObserver.run();
        }
    }

    /**
     * Plays a move immediately or opens promotion selection.
     *
     * @param candidates legal moves from the source square to the target square
     * @param popupX promotion popup x coordinate
     * @param popupY promotion popup y coordinate
     */
    private void playOrPromote(short[] candidates, int popupX, int popupY) {
        boolean anyPromotion = false;
        for (short m : candidates) {
            anyPromotion |= Move.getPromotion(m) != 0;
        }
        if (candidates.length == 1 || !anyPromotion) {
            moveHandler.play(candidates[0]);
            return;
        }
        showPromotionOverlay(candidates);
    }
    /**
     * Active promotion choice overlay.
     */
    PromotionOverlay promotionOverlay;
    /**
     * Shows the promotion chooser for the candidate promotion moves.
     *
     * @param candidates legal promotion candidates
     */
    private void showPromotionOverlay(short[] candidates) {
        cancelPromotionOverlay();
        byte target = Move.getToIndex(candidates[0]);
        promotionOverlay = new PromotionOverlay(target, candidates, () -> position != null && position.isWhiteToMove());
        repaint();
    }
    /**
     * Clears the active promotion chooser, if one is visible.
     */
    void cancelPromotionOverlay() {
        if (promotionOverlay != null) {
            promotionOverlay = null;
            repaint();
        }
    }
    /**
     * Updates drag hover state and schedules minimal repaint regions.
     *
     * @param event mouse drag event
     */
    private void handleDrag(MouseEvent event) {
        if (setupEditor.active()) {
            setupEditor.handle(event);
            return;
        }
        if (markupInput.hasGesture()) {
            handleMarkupDrag(event);
            return;
        }
        if (!pieceInput.hasDragSource()) {
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        boolean wasDragging = pieceInput.isDragging();
        Rectangle board = boardBounds();
        Rectangle oldDirty = animation.dragRepaintBounds(board, pieceInput.dragTargetSquare(),
                pieceInput.dragHoverSquare(), pieceInput.dragX(), pieceInput.dragY(), wasDragging, false);
        pieceInput.updatePointer(event.getX(), event.getY());
        if (!pieceInput.isDragging() && pieceInput.isPastDragThreshold(DRAG_THRESHOLD)) {
            pieceInput.setDragging(true);
        }
        if (pieceInput.isDragging()) {
            byte hovered = squareAt(pieceInput.dragX(), pieceInput.dragY());
            pieceInput.setDragHoverTarget(hovered,
                    premoveSelection ? premoveDropTarget(hovered) : legalDropTarget(hovered));
        } else {
            pieceInput.clearDragHoverTarget();
        }
        if (wasDragging || pieceInput.isDragging()) {
            boolean dragStarted = !wasDragging && pieceInput.isDragging();
            animation.scheduleDragRepaint(oldDirty, animation.dragRepaintBounds(board, pieceInput.dragTargetSquare(),
                    pieceInput.dragHoverSquare(), pieceInput.dragX(), pieceInput.dragY(), pieceInput.isDragging(),
                    dragStarted));
        }
    }
    /**
     * Resolves a board drag release into a move, premove, promotion popup, or
     * snapback animation.
     *
     * @param event mouse release event
     */
    private void handleRelease(MouseEvent event) {
        if (setupEditor.active()) {
            updateCursor(event);
            return;
        }
        if (markupInput.hasGesture()) {
            handleMarkupRelease(event);
            return;
        }
        if (!pieceInput.hasDragSource()) {
            updateCursor(event);
            return;
        }
        if (!pieceInput.isDragging()) {
            clearDragState();
            updateCursor(event);
            return;
        }
        byte from = pieceInput.dragSquare();
        byte piece = pieceInput.draggedPiece();
        int releaseX = event.getX();
        int releaseY = event.getY();
        byte target = squareAt(releaseX, releaseY);
        if (premoveSelection) {
            boolean queued = queuePremove(from, target, piece);
            clearDragState();
            clearSelection();
            setCursor(Cursor.getDefaultCursor());
            if (queued) {
                startSnapAnimation(piece, releaseX, releaseY, from, true);
            }
            repaint();
            return;
        }
        short[] candidates = target == Field.NO_SQUARE ? new short[0]
                : BoardPieceInput.matchingMovesTo(legalMovesFrom(from), target);
        short defaultMove = candidates.length > 0 ? candidates[0] : Move.NO_MOVE;
        short resolved = defaultMove;
        if (dropResolver != null) {
            int response = dropResolver.applyAsInt(
    new DropContext(from, target, piece, position == null ? null : position.toString(), defaultMove));
            resolved = (short) response;
        }
        clearDragState();
        clearSelection();
        setCursor(Cursor.getDefaultCursor());
        if (resolved != Move.NO_MOVE && candidates.length > 0) {
            startSnapAnimation(piece, releaseX, releaseY, target, false);
            // The snap animation IS the move animation visually; suppress the
            // redundant from->to slide that setPosition would otherwise start.
            animationState.suppressNextMoveAnimation();
            if (candidates.length == 1 || !hasPromotionAlternatives(candidates)) {
                moveHandler.play(resolved);
            } else {
                playOrPromote(candidates, releaseX, releaseY);
            }
        } else {
            // Illegal drop: keep the piece logically on its origin (position is
            // unchanged) but hide the static piece there while the snapback
            // animation slides it home, so the user sees only one moving piece.
            startSnapAnimation(piece, releaseX, releaseY, from, true);
            if (snapbackSoundEnabled) {
                SoundService.play(SoundCue.ILLEGAL);
            }
        }
        repaint();
    }
    /**
     * Returns whether the candidate list contains more than one promotion choice.
     *
     * @param candidates legal moves to the same target square
     * @return true when a promotion chooser is required
     */
    private static boolean hasPromotionAlternatives(short[] candidates) {
        if (candidates.length <= 1) {
            return false;
        }
        boolean anyPromotion = false;
        for (short m : candidates) {
            anyPromotion |= Move.getPromotion(m) != 0;
        }
        return anyPromotion;
    }
    /**
     * Starts a snap or snapback animation from a pointer location to a square.
     *
     * @param piece encoded piece
     * @param fromX starting x coordinate
     * @param fromY starting y coordinate
     * @param landingSquare board square index
     * @param snapback whether snapback
     */
    private void startSnapAnimation(byte piece, int fromX, int fromY, byte landingSquare, boolean snapback) {
        if (!isSquareIndex(landingSquare)) {
            return;
        }
        if (!animationState.animationsEnabled()) {
            return;
        }
        Rectangle bounds = squareBounds(boardBounds(), landingSquare);
        int cell = bounds.width;
        int duration = animationState.snapDuration(snapback);
        if (duration <= 0 || cell <= 0) {
            return;
        }
        animationState.startSnapAnimation(
                new SnapAnimation(piece, fromX - cell / 2, fromY - cell / 2, bounds.x, bounds.y, duration,
                        snapback),
                landingSquare);
        animation.startAnimation();
    }
    /**
     * Returns the square if the active drag can legally move there.
     *
     * @param square board square index
     * @return target square, or {@link Field#NO_SQUARE}
     */
    private byte legalDropTarget(byte square) {
        byte dragSquare = pieceInput.dragSquare();
        if (square == Field.NO_SQUARE || dragSquare == Field.NO_SQUARE) {
            return Field.NO_SQUARE;
        }
        return findLegalMove(dragSquare, square) == Move.NO_MOVE ? Field.NO_SQUARE : square;
    }
    /**
     * Returns the square if the active drag can queue a premove there.
     *
     * @param square board square index
     * @return target square, or {@link Field#NO_SQUARE}
     */
    private byte premoveDropTarget(byte square) {
        byte dragSquare = pieceInput.dragSquare();
        if (square == Field.NO_SQUARE || dragSquare == Field.NO_SQUARE || square == dragSquare) {
            return Field.NO_SQUARE;
        }
        return PremoveGeometry.isTarget(position, dragSquare, pieceInput.draggedPiece(), square)
                ? square
                : Field.NO_SQUARE;
    }
    /**
     * Returns whether the square contains an opponent piece.
     *
     * @param square board square index
     * @return true when the current side can capture the square's occupant
     */
    boolean isCaptureTarget(byte square) {
        if (position == null || !isSquareIndex(square)) {
            return false;
        }
        byte piece = position.getBoard()[square];
        return piece != Piece.EMPTY && Piece.isWhite(piece) != position.isWhiteToMove();
    }
    /**
     * Returns whether the square currently contains any piece.
     *
     * @param square board square index
     * @return true when a piece occupies the square
     */
    boolean isOccupied(byte square) {
        return position != null && isSquareIndex(square) && position.getBoard()[square] != Piece.EMPTY;
    }
    /**
     * Returns whether the square should be highlighted as a capture target for
     * the active drag.
     *
     * @param square board square index
     * @return true when the dragged piece would capture on the square
     */
    boolean isDragCaptureTarget(byte square) {
        if (!premoveSelection || position == null || !isSquareIndex(square)) {
            return isCaptureTarget(square);
        }
        byte target = position.getBoard()[square];
        byte dragged = pieceInput.draggedPiece();
        return target != Piece.EMPTY && dragged != Piece.EMPTY && Piece.isWhite(target) != Piece.isWhite(dragged);
    }
    /**
     * Returns whether a piece can start a premove drag from the square.
     *
     * @param square board square index
     * @param piece encoded piece on the square
     * @return true when the premove handler accepts the source
     */
    private boolean isPremoveSource(byte square, byte piece) {
        if (position == null || premoveStartFilter == null || premoveHandler == null) {
            return false;
        }
        if (!isSquareIndex(square) || piece == Piece.EMPTY) {
            return false;
        }
        return premoveStartFilter.test(new DragContext(square, piece, position.toString()));
    }
    /**
     * Queues a premove after validating the source, target, and promotion shape.
     *
     * @param from source square
     * @param to target square
     * @param piece encoded moving piece
     * @return true when the premove handler accepted the move
     */
    private boolean queuePremove(byte from, byte to, byte piece) {
        if (premoveHandler == null || position == null) {
            return false;
        }
        if (!isSquareIndex(from) || !isSquareIndex(to) || from == to || piece == Piece.EMPTY
                || !PremoveGeometry.isTarget(position, from, piece, to)) {
            return false;
        }
        short move = Move.of(from, to, PremoveGeometry.promotion(piece, to));
        boolean accepted = premoveHandler.queue(
                new PremoveContext(from, to, piece, position.toString(), move));
        if (accepted) {
            setPremove(move);
        }
        return accepted;
    }
    /**
     * Returns every pseudo-legal premove target for one source square.
     *
     * @param from source square
     * @param piece encoded moving piece
     * @return target squares
     */
    private byte[] premoveTargets(byte from, byte piece) {
        return PremoveGeometry.targets(position, from, piece);
    }
    /**
     * Clears drag state and any pending drag repaint.
     */
    void clearDragState() {
        animation.cancelPendingDragRepaint();
        pieceInput.clearDrag();
    }
    /**
     * Clears selected square and premove-selection state.
     */
    void clearSelection() {
        pieceInput.clearSelection();
        premoveSelection = false;
    }
    /**
     * Selects a side-to-move piece and refreshes its legal targets.
     *
     * @param square board square index
     */
    private void selectSquare(byte square) {
        premoveSelection = false;
        pieceInput.select(square, new short[0], new byte[0]);
        refreshSelectedLegalMoves();
    }
    /**
     * Selects a premove source and computes pseudo-legal premove targets.
     *
     * @param square board square index
     */
    private void selectPremoveSquare(byte square) {
        premoveSelection = true;
        byte piece = position == null || !isSquareIndex(square) ? Piece.EMPTY : position.getBoard()[square];
        pieceInput.select(square, new short[0], premoveTargets(square, piece));
    }
    /**
     * Updates the cursor and mouseover callback for the pointer location.
     *
     * @param event mouse event
     */
    private void updateCursor(MouseEvent event) {
        if (setupEditor.active()) {
            byte square = squareAt(event.getX(), event.getY());
            setCursor(square == Field.NO_SQUARE
                    ? Cursor.getDefaultCursor()
                    : Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            notifyMouseover(square);
            return;
        }
        if (position == null) {
            setCursor(Cursor.getDefaultCursor());
            notifyMouseover(Field.NO_SQUARE);
            return;
        }
        byte square = squareAt(event.getX(), event.getY());
        byte piece = square == Field.NO_SQUARE ? Piece.EMPTY : position.getBoard()[square];
        boolean draggable = piece != Piece.EMPTY && ((moveHandler != null && Piece.isWhite(piece) == position.isWhiteToMove())
                || isPremoveSource(square, piece));
        setCursor(draggable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        notifyMouseover(square);
    }
    /**
     * Notifies listeners when the hovered board square changes.
     *
     * @param square board square index
     */
    private void notifyMouseover(byte square) {
        if (mouseoverSquareObserver == null) {
            return;
        }
        if (square == lastMouseoverSquare) {
            return;
        }
        lastMouseoverSquare = square;
        mouseoverSquareObserver.accept(square);
    }
    /**
     * Finds a legal move from one square to another, preferring the selected
     * move cache when possible.
     *
     * @param from source square
     * @param to target square
     * @return matching move, or {@link Move#NO_MOVE}
     */
    private short findLegalMove(byte from, byte to) {
        if (pieceInput.isSelected(from)) {
            return findLegalMove(pieceInput.selectedLegalMoves(), to);
        }
        return findLegalMove(legalMovesFrom(from), to);
    }
    /**
     * Finds the first move to a target square, preferring queen promotion.
     *
     * @param moves encoded moves
     * @param to target square
     * @return matching move, or {@link Move#NO_MOVE}
     */
    private static short findLegalMove(short[] moves, byte to) {
        short first = Move.NO_MOVE;
        for (short move : moves) {
            if (Move.getToIndex(move) == to) {
                if (first == Move.NO_MOVE) {
                    first = move;
                }
                if (Move.getPromotion(move) == 4) {
                    return move;
                }
            }
        }
        return first;
    }
    /**
     * Returns legal moves whose source square matches {@code from}.
     *
     * @param from source square
     * @return matching legal moves
     */
    private short[] legalMovesFrom(byte from) {
        MoveList moves = currentLegalMoves();
        if (moves == null) {
            return new short[0];
        }
        short[] buffer = new short[moves.size()];
        int count = 0;
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            if (Move.getFromIndex(move) == from) {
                buffer[count++] = move;
            }
        }
        return Arrays.copyOf(buffer, count);
    }
    /**
     * Refreshes cached legal moves and target highlights for the selected square.
     */
    private void refreshSelectedLegalMoves() {
        byte selectedSquare = pieceInput.selectedSquare();
        if (position == null || selectedSquare == Field.NO_SQUARE) {
            clearSelection();
            return;
        }
        MoveList moves = currentLegalMoves();
        if (moves == null) {
            clearSelection();
            return;
        }
        short[] moveBuffer = new short[moves.size()];
        byte[] targetBuffer = new byte[moves.size()];
        int moveCount = 0;
        int targetCount = 0;
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            if (Move.getFromIndex(move) == selectedSquare) {
                moveBuffer[moveCount++] = move;
                byte target = Move.getToIndex(move);
                if (!containsTarget(targetBuffer, targetCount, target)) {
                    targetBuffer[targetCount++] = target;
                }
            }
        }
        pieceInput.select(selectedSquare, Arrays.copyOf(moveBuffer, moveCount),
                Arrays.copyOf(targetBuffer, targetCount));
    }
    /**
     * Returns whether a compact target buffer already contains a square.
     *
     * @param targets target buffer
     * @param count populated entries in the buffer
     * @param target square to find
     * @return true when the square is already present
     */
    private static boolean containsTarget(byte[] targets, int count, byte target) {
        for (int i = 0; i < count; i++) {
            if (targets[i] == target) {
                return true;
            }
        }
        return false;
    }
    /**
     * Returns the current on-screen board bounds.
     *
     * @return board bounds in component coordinates
     */
    Rectangle boardBounds() {
        return layout.boardBounds();
    }

    /**
     * Returns the current on-screen bounds of the board square, so adjacent
     * components (such as the material strips) can align to it.
     *
     * @return board square rectangle in this panel's coordinates
     */
    public Rectangle boardSquareBounds() {
        return boardBounds();
    }
    /**
     * Attaches an engine eval bar as a child component, painted flush against the left edge of the board square and matched to its height.
     * @param bar evaluation bar to attach
     */
    public void setEvalBar(EvalBar bar) {
        layout.setEvalBar(bar);
    }
    /**
     * Positions the attached eval bar flush against the board square.
     */
    @Override
    public void doLayout() {
        super.doLayout();
        layout.doLayout();
    }
    /**
     * Returns the rendered chessboard square in this panel's own coordinate space — used by the board stage to align the engine eval bar flush wit...
     * @return current board drawing bounds
     */
    public Rectangle currentBoardBounds() {
        return boardBounds();
    }
    /**
     * Returns immutable board state for render-based PNG/SVG exports.
     * @return export snapshot
     */
    BoardExportSnapshot exportSnapshot() {
        byte[] pieces = setupEditor.active() ? setupEditor.boardCopy()
                : position == null ? new byte[64] : position.getBoard().clone();
        return new BoardExportSnapshot(
                pieces,
                setupEditor.active() || position == null ? null : position.copy(),
                boardLightColor(),
                boardDarkColor(),
                whiteDown,
                lastMove,
                suggestedMove,
                pieceInput.selectedSquare(),
                pieceInput.selectedLegalTargets(),
                selectedCaptureTargets(),
                painter.checkedKingSquare(),
                new LinkedHashMap<>(squareHighlights),
                markupInput.copyMarkups(),
                showNotation,
                showLegalMovePreview,
                showLastMoveHighlight,
                showSuggestedMoveArrow,
                showSpecialMoveHints,
                markupPainter.isGlyphShadow());
    }
    /**
     * Returns selected legal targets that currently contain capturable pieces.
     *
     * @return capture target squares
     */
    private byte[] selectedCaptureTargets() {
        byte[] selectedLegalTargets = pieceInput.selectedLegalTargets();
        byte[] captures = new byte[selectedLegalTargets.length];
        int count = 0;
        for (byte target : selectedLegalTargets) {
            if (isCaptureTarget(target)) {
                captures[count++] = target;
            }
        }
        return Arrays.copyOf(captures, count);
    }
    /**
     * Returns the on-screen rectangle for one board square.
     *
     * @param square board square index
     * @param board board array indexed by square
     * @return square bounds in component coordinates
     */
    Rectangle squareBounds(Rectangle board, byte square) {
        return BoardGeometry.squareBounds(board, square, whiteDown);
    }
    /**
     * Returns whether a rectangle should be painted for the active clip.
     *
     * @param clip current graphics clip, or {@code null}
     * @param bounds candidate paint bounds
     * @return true when the candidate intersects the clip
     */
    static boolean intersectsClip(Rectangle clip, Rectangle bounds) {
        return clip == null || bounds == null || clip.intersects(bounds);
    }
    /**
     * Returns the board square under a component coordinate.
     *
     * @param x x coordinate in pixels
     * @param y y coordinate in pixels
     * @return square index, or {@link Field#NO_SQUARE}
     */
    byte squareAt(int x, int y) {
        return BoardGeometry.squareAt(boardBounds(), x, y, whiteDown);
    }
    /**
     * Returns the center point of a board square.
     *
     * @param board board bounds
     * @param square board square index
     * @return square center in component coordinates
     */
    Point center(Rectangle board, byte square) {
        return BoardGeometry.center(board, square, whiteDown);
    }
    /**
     * Returns normalized progress for the current move animation.
     *
     * @return progress in the range {@code 0.0..1.0}
     */
    double moveAnimationProgress() {
        return animation.moveAnimationProgress();
    }
    /**
     * Returns normalized progress for the wrong-move marker animation.
     *
     * @return progress in the range {@code 0.0..1.0}
     */
    double wrongMoveMarkerProgress() {
        return animation.wrongMoveMarkerProgress();
    }
    /**
     * Cubic ease-out matching the short board transition feel.
     * @param value new value
     * @return eased value
     */
    public static double easeOutCubic(double value) {
        return Ui.easeOutCubic(value);
    }
    /**
     * Returns whether a byte is a valid 0..63 board square.
     *
     * @param square board square index
     * @return true when the square is on the board
     */
    static boolean isSquareIndex(byte square) {
        return square >= 0 && square < 64;
    }
}
