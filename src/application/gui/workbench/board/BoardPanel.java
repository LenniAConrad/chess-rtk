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
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
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
import javax.swing.Timer;

/**
 * Native Java2D chess board used by the CRTK Workbench.
 */
public final class BoardPanel extends JPanel {
    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Padding around the chessboard surface.
     */
    private static final int BOARD_MARGIN = 32;

    /**
     * Minimum rendered board size in pixels.
     */
    private static final int MIN_BOARD_SIZE = 64;

    /**
     * Width of the attached evaluation bar.
     */
    private static final int EVAL_BAR_WIDTH = 22;

    /**
     * Gap between the board and attached evaluation bar.
     */
    private static final int EVAL_BAR_GAP = 8;

    /**
     * Optional attached evaluation bar component.
     */
    private transient EvalBar evalBar;

    /**
     * Minimum pointer travel before a press becomes a drag.
     */
    private static final int DRAG_THRESHOLD = 5;

    /**
     * Default move-animation duration in milliseconds.
     */
    private static final int MOVE_ANIMATION_MS = BoardAnimationState.DEFAULT_MOVE_ANIMATION_MS;

    /**
     * Animation timer delay in milliseconds.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Extra repaint padding around drag dirty regions.
     */
    private static final int DRAG_REPAINT_PADDING = 8;

    /**
     * Minimum nanoseconds between throttled drag repaint flushes.
     */
    private static final long DRAG_REPAINT_FRAME_NANOS = 16_000_000L;

    /**
     * Shared one-pixel stroke.
     */
    private static final BasicStroke STROKE_1 = new BasicStroke(1f);

    /**
     * Composite used while painting dragged pieces.
     */
    private static final AlphaComposite DRAG_ALPHA = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f);

    /**
     * Opacity for the markup currently being drawn.
     */
    private static final double MARKUP_CURRENT_OPACITY = 0.9;

    /**
     * Opacity for a pending erase markup.
     */
    private static final double MARKUP_PENDING_ERASE_OPACITY = 0.6;

    /**
     * Current position rendered on the board.
     */
    private Position position;

    /**
     * True when White is rendered at the bottom.
     */
    private boolean whiteDown = true;

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
    private short lastMove = Move.NO_MOVE, suggestedMove = Move.NO_MOVE;

    /**
     * Queued premove arrow, independent from the legal suggested-move arrow.
     */
    private short pendingPremove = Move.NO_MOVE;

    /**
     * Piece selection and drag/drop state.
     */
    private final BoardPieceInput pieceInput = new BoardPieceInput();

    /**
     * Dirty rectangle queued for throttled drag repainting.
     */
    private Rectangle pendingDragDirty;

    /**
     * Last throttled drag repaint timestamp.
     */
    private long lastDragRepaintNanos;

    /**
     * Move handler used for drag-drop play.
     */
    private MoveHandler moveHandler;

    /**
     * Animation and drag repaint timers.
     */
    private final Timer animationTimer, dragRepaintTimer;

    /**
     * Cached board images for piece rendering.
     */
    private final BoardImageCache imageCache = new BoardImageCache();

    /**
     * Painter for board arrows.
     */
    private final BoardArrowPainter arrowPainter = new BoardArrowPainter();

    /**
     * Cached legal move list for the current position.
     */
    private MoveList cachedLegalMoves;

    /**
     * Board overlay visibility toggles.
     */
    private boolean showNotation = true, showLegalMovePreview = true,
            showLastMoveHighlight = true, showSuggestedMoveArrow = true;

    /**
     * Move, snap, flip, and wrong-move marker animation state.
     */
    private final BoardAnimationState animationState = new BoardAnimationState();

    /**
     * Programmatic square highlights.
     */
    private final Map<Byte, Color> squareHighlights = new LinkedHashMap<>();

    /**
     * Persistent annotation list and transient annotation gesture state.
     */
    private final BoardMarkupInput markupInput = new BoardMarkupInput();

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
    private boolean premoveSelection;

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
    private Runnable snapbackEndObserver, snapEndObserver;

    /**
     * True when illegal-move snapback sound is enabled.
     */
    private boolean snapbackSoundEnabled = true;

    /**
     * Mouseover-square observer.
     */
    private IntConsumer mouseoverSquareObserver;

    /**
     * Last square reported to the mouseover observer.
     */
    private byte lastMouseoverSquare = Field.NO_SQUARE;

    /**
     * True while setup editing is active.
     */
    private boolean setupEditMode;

    /**
     * Mutable board used by setup editing.
     */
    private final byte[] setupEditBoard = new byte[64];

    /**
     * Selected setup-edit piece and last edited square.
     */
    private byte setupEditSelectedPiece = Piece.WHITE_KING, setupEditLastSquare = Field.NO_SQUARE;

    /**
     * Setup edit observer.
     */
    private BiConsumer<Byte, Byte> setupEditObserver;

    /**
     * Creates the board panel.
     */
    public BoardPanel() {
        setOpaque(true);
        setBackground(Theme.BG);
        int basis = Math.round(620 * displayScale());
        setPreferredSize(new Dimension(basis, basis));
        setMinimumSize(new Dimension(Math.round(MIN_BOARD_SIZE + BOARD_MARGIN * 2 * displayScale()),
                Math.round(MIN_BOARD_SIZE + BOARD_MARGIN * 2 * displayScale())));
        animationTimer = new Timer(ANIMATION_DELAY_MS, event -> tickAnimation());
        animationTimer.setCoalesce(true);
        dragRepaintTimer = new Timer(ANIMATION_DELAY_MS, event -> flushPendingDragRepaint());
        dragRepaintTimer.setCoalesce(true);
        dragRepaintTimer.setRepeats(false);
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
        animationTimer.stop();
        dragRepaintTimer.stop();
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
            clearMoveAnimation();
        } else {
            startMoveAnimation(previousBoard, position == null ? null : position.getBoard(),
                    move, reverseMoveAnimation);
        }
        clearWrongMoveMarkerState();
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

    private static boolean legalPremoveShape(short move) {
        if (move == Move.NO_MOVE) {
            return false;
        }
        try {
            byte from = Move.getFromIndex(move);
            byte to = Move.getToIndex(move);
            return isSquareIndex(from) && isSquareIndex(to) && from != to;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean legalSuggestedMove(short move) {
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
        startAnimation();
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
     * Removes any highlight for a square.
     *
     * @param square board square index
     */
    public void clearSquareHighlight(byte square) {
        if (squareHighlights.remove(square) != null) {
            repaint();
        }
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
     * Returns whether left-click annotation drawing is active.
     *
     * @return true when direct annotation input is enabled
     */
    public boolean isDirectAnnotationMode() {
        return markupInput.directAnnotationMode();
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
     * Shows a temporary chess-web-style wrong-move badge on one square.
     *
     * @param square board square index
     */
    public void showWrongMoveMarker(byte square) {
        if (!isSquareIndex(square)) {
            return;
        }
        animationState.showWrongMoveMarker(square, System.currentTimeMillis());
        startAnimation();
        repaint();
    }

    /**
     * Clears the temporary wrong-move badge.
     */
    public void clearWrongMoveMarker() {
        if (animationState.wrongMoveMarkerSquare() == Field.NO_SQUARE) {
            return;
        }
        clearWrongMoveMarkerState();
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
            clearAllAnimations();
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
            clearAllAnimations();
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
     * Sets an observer notified when the position changes (old, new FEN).
     * @param observer callback observer
     */
    public void setChangeObserver(BiConsumer<String, String> observer) {
        changeObserver = observer;
    }
    /**
     * Sets a callback fired when a snapback animation completes.
     * @param observer callback observer
     */
    public void setSnapbackEndObserver(Runnable observer) {
        snapbackEndObserver = observer;
    }
    /**
     * Sets a callback fired when a snap animation completes.
     * @param observer callback observer
     */
    public void setSnapEndObserver(Runnable observer) {
        snapEndObserver = observer;
    }
    /**
     * Sets whether illegal drag snapbacks should play the generic board cue.
     * @param enabled true to play the snapback cue
     */
    public void setSnapbackSoundEnabled(boolean enabled) {
        snapbackSoundEnabled = enabled;
    }
    /**
     * Sets a callback fired when the pointer enters a different square. Field#NO_SQUARE on exit; null clears it
     * @param observer callback observer
     */
    public void setMouseoverSquareObserver(IntConsumer observer) {
        mouseoverSquareObserver = observer;
    }

    /**
     * Enables direct setup editing on the board surface.
     * @param enabled true when setup editing should intercept board input
     */
    public void setSetupEditMode(boolean enabled) {
        if (enabled == setupEditMode) {
            return;
        }
        setupEditMode = enabled;
        cancelPromotionOverlay();
        clearDragState();
        clearSelection();
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    /**
     * Returns whether setup editing is active.
     * @return true when setup editing is active
     */
    public boolean isSetupEditMode() {
        return setupEditMode;
    }

    /**
     * Replaces the board shown by setup editing.
     * @param board board piece array, or null to clear
     */
    public void setSetupEditBoard(byte[] board) {
        if (board == null) {
            Arrays.fill(setupEditBoard, Piece.EMPTY);
        } else if (board.length != setupEditBoard.length) {
            throw new IllegalArgumentException("Setup board must contain 64 squares");
        } else {
            System.arraycopy(board, 0, setupEditBoard, 0, setupEditBoard.length);
        }
        setupEditLastSquare = Field.NO_SQUARE;
        if (setupEditMode) {
            repaint();
        }
    }

    /**
     * Sets the piece painted by a left click in setup editing mode.
     * @param piece piece code, or {@link Piece#EMPTY} for erase
     */
    public void setSetupEditSelectedPiece(byte piece) {
        validateSetupPiece(piece);
        setupEditSelectedPiece = piece;
    }

    /**
     * Sets one setup-edit square and notifies the editor model.
     * @param square board square index
     * @param piece piece code
     */
    public void setSetupEditPieceAt(byte square, byte piece) {
        setSetupEditPieceAt(square, piece, true);
    }

    /**
     * Returns one setup-edit square.
     * @param square board square index
     * @return piece code
     */
    public byte setupEditPieceAt(byte square) {
        if (!isSquareIndex(square)) {
            throw new IllegalArgumentException("Invalid square " + square);
        }
        return setupEditBoard[square];
    }

    /**
     * Sets the setup-edit observer.
     * @param observer observer, or null
     */
    public void setSetupEditObserver(BiConsumer<Byte, Byte> observer) {
        setupEditObserver = observer;
    }

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
            clearMoveAnimation();
            clearWrongMoveMarkerState();
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
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            Rectangle board = boardBounds();
            drawShell(g, board);
            drawBoardContent(g, board);
            drawDraggedPiece(g, board);
        } finally {
            g.dispose();
        }
    }
    private void drawBoardContent(Graphics2D g, Rectangle board) {
        Graphics2D copy = (Graphics2D) g.create();
        try {
            copy.clip(board);
            drawBoardTexture(copy, board);
            drawCoordinates(copy, board);
            if (setupEditMode) {
                drawSetupEditHighlight(copy, board);
            } else {
                drawSquareHighlights(copy, board);
                drawMoveHighlights(copy, board);
                drawSelection(copy, board);
                drawCheckHighlight(copy, board);
                drawPremove(copy, board);
            }
            drawPieces(copy, board);
            if (!setupEditMode) {
                drawAnimatedCapture(copy, board);
                drawAnimatedMove(copy, board);
                drawSnapAnimation(copy, board);
                drawSuggestedMove(copy, board);
                drawBoardMarkups(copy, board);
            }
            if (!setupEditMode) {
                drawWrongMoveMarker(copy, board);
            }
            drawFlipOverlay(copy, board);
            if (!setupEditMode && promotionOverlay != null) {
                promotionOverlay.paint(copy, board, whiteDown, this::drawPieceAt);
            }
        } finally {
            copy.dispose();
        }
    }
    private void drawFlipOverlay(Graphics2D g, Rectangle board) {
        double flipProgress = animationState.flipAnimationProgress();
        if (Double.isNaN(flipProgress)) {
            return;
        }
        // Triangular ramp: alpha peaks at progress 0.5.
        float alpha = (float) (Math.sin(Math.PI * flipProgress) * 0.55);
        if (alpha <= 0f) {
            return;
        }
        java.awt.Composite saved = g.getComposite();
        Color savedColor = g.getColor();
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
            g.setColor(Theme.BG);
            g.fillRect(board.x, board.y, board.width, board.height);
        } finally {
            g.setComposite(saved);
            g.setColor(savedColor);
        }
    }
    private void drawSquareHighlights(Graphics2D g, Rectangle board) {
        if (squareHighlights.isEmpty()) {
            return;
        }
        Rectangle clip = g.getClipBounds();
        for (Map.Entry<Byte, Color> entry : squareHighlights.entrySet()) {
            Rectangle bounds = squareBounds(board, entry.getKey());
            if (intersectsClip(clip, bounds)) {
                BoardStyle.drawFilledSquareHighlight(g, bounds, entry.getValue());
            }
        }
    }
    private void drawBoardMarkups(Graphics2D g, Rectangle board) {
        if (markupInput.isEmpty()) {
            return;
        }
        int pendingEraseIndex = markupInput.pendingEraseIndex();
        List<BoardMarkup> boardMarkups = markupInput.markups();
        for (int i = 0; i < boardMarkups.size(); i++) {
            double opacity = i == pendingEraseIndex ? MARKUP_PENDING_ERASE_OPACITY : 1.0;
            drawBoardMarkup(g, board, boardMarkups.get(i), opacity);
        }
        BoardMarkup currentMarkup = markupInput.currentMarkup();
        if (currentMarkup != null && pendingEraseIndex < 0) {
            drawBoardMarkup(g, board, currentMarkup, MARKUP_CURRENT_OPACITY);
        }
    }
    private void drawBoardMarkup(Graphics2D g, Rectangle board, BoardMarkup markup, double opacity) {
        Color savedColor = g.getColor();
        try {
            g.setColor(markupColor(markup.brush().displayColor(), opacity));
            if (markup.isCircle()) {
                drawMarkupCircle(g, squareBounds(board, markup.from()), markup.brush());
            } else {
                drawMarkupArrow(g, board, markup);
            }
        } finally {
            g.setColor(savedColor);
        }
    }
    private static void drawMarkupCircle(Graphics2D g, Rectangle bounds, MarkupBrush brush) {
        Stroke savedStroke = g.getStroke();
        try {
            int cell = bounds.width;
            float strokeWidth = Math.max(3f, (float) cell * 4f / 64f);
            int diameter = Math.max(8, Math.round(cell - strokeWidth));
            g.setStroke(new BasicStroke(strokeWidth));
            g.drawOval(
                    bounds.x + Math.round(strokeWidth / 2f),
                    bounds.y + Math.round(strokeWidth / 2f),
                    diameter,
                    diameter);
        } finally {
            g.setStroke(savedStroke);
        }
    }
    private void drawMarkupArrow(Graphics2D g, Rectangle board, BoardMarkup markup) {
        int cell = Math.max(1, board.width / 8);
        float lineWidth = Math.max(5f, cell * markup.brush().lineWidth() / 64f);
        double gap = cell * BoardStyle.ARROW_PIECE_GAP_FRACTION;
        arrowPainter.draw(g, center(board, markup.from()), center(board, markup.to()), lineWidth, gap);
    }
    private static Color markupColor(Color color, double opacity) {
        int alpha = (int) Math.round(color.getAlpha() * Math.max(0.0, Math.min(1.0, opacity)));
        return Theme.withAlpha(color, alpha);
    }
    private void drawSnapAnimation(Graphics2D g, Rectangle board) {
        SnapAnimation snap = animationState.snapAnimation();
        if (snap == null) {
            return;
        }
        double progress = snap.progress();
        double eased = easeOutCubic(progress);
        int cell = board.width / 8;
        int x = (int) Math.round(snap.startX + (snap.endX - snap.startX) * eased);
        int y = (int) Math.round(snap.startY + (snap.endY - snap.startY) * eased);
        drawPieceAt(g, cell, x, y, snap.piece);
    }
    private void drawShell(Graphics2D g, Rectangle board) {
        g.setColor(Theme.BOARD_EDGE);
        g.fillRect(board.x - BoardStyle.BORDER_WIDTH, board.y - BoardStyle.BORDER_WIDTH,
                board.width + BoardStyle.BORDER_WIDTH * 2, board.height + BoardStyle.BORDER_WIDTH * 2);
    }
    private void drawBoardTexture(Graphics2D g, Rectangle board) {
        BufferedImage texture = imageCache.boardTexture(board.width);
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            g.drawImage(texture, board.x, board.y, null);
            return;
        }
        Rectangle dirty = board.intersection(clip);
        if (dirty.isEmpty()) {
            return;
        }
        int sx = dirty.x - board.x;
        int sy = dirty.y - board.y;
        g.drawImage(texture,
                dirty.x, dirty.y, dirty.x + dirty.width, dirty.y + dirty.height,
                sx, sy, sx + dirty.width, sy + dirty.height,
                null);
    }
    /**
     * Returns the cached board texture for tests and diagnostic callers.
     *
     * @param size texture size
     * @return cached texture
     */
    BufferedImage boardTexture(int size) {
        return imageCache.boardTexture(size);
    }

    private void drawCoordinates(Graphics2D g, Rectangle board) {
        if (!showNotation) {
            return;
        }
        BoardStyle.drawInsideCoordinates(g, board, whiteDown, 14);
    }
    private void drawMoveHighlights(Graphics2D g, Rectangle board) {
        if (!showLastMoveHighlight || lastMove == Move.NO_MOVE) {
            return;
        }
        Rectangle clip = g.getClipBounds();
        Rectangle from = squareBounds(board, Move.getFromIndex(lastMove));
        Rectangle to = squareBounds(board, Move.getToIndex(lastMove));
        if (intersectsClip(clip, from)) {
            BoardStyle.drawFilledSquareHighlight(g, from, Theme.LAST_MOVE_EDGE);
        }
        if (intersectsClip(clip, to)) {
            BoardStyle.drawFilledSquareHighlight(g, to, Theme.LAST_MOVE_EDGE);
        }
    }
    private void drawSelection(Graphics2D g, Rectangle board) {
        byte selectedSquare = pieceInput.selectedSquare();
        if (position == null || selectedSquare == Field.NO_SQUARE) {
            return;
        }
        Rectangle selected = squareBounds(board, selectedSquare);
        Rectangle clip = g.getClipBounds();
        if (intersectsClip(clip, selected)) {
            BoardStyle.drawFilledSquareHighlight(g, selected, Theme.SELECTED_EDGE);
        }
        if (showLegalMovePreview) {
            if (premoveSelection) {
                drawPremoveTargets(g, board);
            } else {
                drawLegalTargets(g, board);
            }
            drawDropTarget(g, board);
        }
    }
    private void drawCheckHighlight(Graphics2D g, Rectangle board) {
        byte square = checkedKingSquare();
        if (square == Field.NO_SQUARE) {
            return;
        }
        Rectangle bounds = squareBounds(board, square);
        java.awt.Paint savedPaint = g.getPaint();
        Stroke savedStroke = g.getStroke();
        Color savedColor = g.getColor();
        try {
            g.setColor(Theme.CHECK_FILL);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            float radius = bounds.width * 0.74f;
            Point2D center = new Point2D.Double(bounds.getCenterX(), bounds.y + bounds.height * 0.52);
            Color core = Theme.CHECK_CORE;
            Color glow = Theme.CHECK_GLOW;
            Color fade = Theme.withAlpha(glow, 0);
            g.setPaint(new RadialGradientPaint(center, radius,
                    CHECK_GRADIENT_FRACTIONS,
                    new Color[] { core, core, glow, fade, fade }));
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            g.setPaint(savedPaint);
            g.setColor(Theme.CHECK_EDGE);
            g.setStroke(STROKE_1);
            g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        } finally {
            g.setPaint(savedPaint);
            g.setStroke(savedStroke);
            g.setColor(savedColor);
        }
    }
    /**
     * Radial gradient stops for the checked-king highlight.
     */
    private static final float[] CHECK_GRADIENT_FRACTIONS = { 0.0f, 0.24f, 0.42f, 0.74f, 1.0f };
    private byte checkedKingSquare() {
        if (position == null || !position.inCheck()) {
            return Field.NO_SQUARE;
        }
        byte target = position.isWhiteToMove() ? Piece.WHITE_KING : Piece.BLACK_KING;
        byte[] board = position.getBoard();
        for (byte square = 0; square < 64; square++) {
            if (board[square] == target) {
                return square;
            }
        }
        return Field.NO_SQUARE;
    }
    private void drawLegalTargets(Graphics2D g, Rectangle board) {
        Rectangle clip = g.getClipBounds();
        byte selectedSquare = pieceInput.selectedSquare();
        for (byte target : pieceInput.selectedLegalTargets()) {
            if (target != selectedSquare) {
                Rectangle bounds = squareBounds(board, target);
                if (intersectsClip(clip, bounds)) {
                    BoardStyle.drawLegalTarget(g, bounds, isCaptureTarget(target));
                }
            }
        }
    }
    private void drawPremoveTargets(Graphics2D g, Rectangle board) {
        Rectangle clip = g.getClipBounds();
        byte selectedSquare = pieceInput.selectedSquare();
        for (byte target : pieceInput.selectedLegalTargets()) {
            if (target != selectedSquare) {
                Rectangle bounds = squareBounds(board, target);
                if (intersectsClip(clip, bounds)) {
                    BoardStyle.drawPremoveTarget(g, bounds, isOccupied(target));
                }
            }
        }
    }
    private void drawSetupEditHighlight(Graphics2D g, Rectangle board) {
        if (setupEditLastSquare == Field.NO_SQUARE) {
            return;
        }
        Rectangle bounds = squareBounds(board, setupEditLastSquare);
        if (!intersectsClip(g.getClipBounds(), bounds)) {
            return;
        }
        g.setColor(Theme.withAlpha(Theme.ACCENT, 74));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        Stroke previousStroke = g.getStroke();
        try {
            g.setColor(Theme.withAlpha(Theme.ACCENT, 190));
            g.setStroke(STROKE_1);
            g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
        } finally {
            g.setStroke(previousStroke);
        }
    }
    private void drawWrongMoveMarker(Graphics2D g, Rectangle board) {
        byte wrongMoveMarkerSquare = animationState.wrongMoveMarkerSquare();
        if (wrongMoveMarkerSquare == Field.NO_SQUARE) {
            return;
        }
        Rectangle square = squareBounds(board, wrongMoveMarkerSquare);
        if (!intersectsClip(g.getClipBounds(), square)) {
            return;
        }
        double progress = wrongMoveMarkerProgress();
        if (progress >= 1.0) {
            return;
        }
        double scale = progress < 0.6
                ? 0.25 + (1.08 - 0.25) * (progress / 0.6)
                : 1.08 + (1.0 - 1.08) * ((progress - 0.6) / 0.4);
        float alpha = (float) Math.max(0.0, Math.min(1.0, progress / 0.6));
        int baseSize = Math.max(14, Math.round(square.width * 0.46f));
        int size = Math.max(8, (int) Math.round(baseSize * scale));
        int half = Math.max(1, size / 2);
        int padding = Math.max(2, Math.round(square.width * 0.04f));
        int centerX = clamped(
                square.x + square.width - Math.round(square.width * 0.12f),
                square.x + half + padding,
                square.x + square.width - half - padding);
        int centerY = clamped(
                square.y + Math.round(square.height * 0.12f),
                square.y + half + padding,
                square.y + square.height - half - padding);
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        Graphics2D marker = (Graphics2D) g.create();
        try {
            marker.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            marker.setColor(Theme.STATUS_ERROR_TEXT);
            marker.fillOval(x, y, size, size);
            marker.setColor(Theme.withAlpha(Color.WHITE, 230));
            float stroke = Math.max(2f, size * 0.12f);
            marker.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int inset = Math.max(4, Math.round(size * 0.28f));
            marker.drawLine(x + inset, y + inset, x + size - inset, y + size - inset);
            marker.drawLine(x + size - inset, y + inset, x + inset, y + size - inset);
        } finally {
            marker.dispose();
        }
    }

    private static int clamped(int value, int min, int max) {
        if (min > max) {
            return (min + max) / 2;
        }
        return Math.max(min, Math.min(max, value));
    }

    private void drawPieces(Graphics2D g, Rectangle board) {
        byte[] pieces = setupEditMode ? setupEditBoard : position == null ? null : position.getBoard();
        if (pieces == null) {
            return;
        }
        int cell = board.width / 8;
        Rectangle clip = g.getClipBounds();
        for (byte square = 0; square < 64; square++) {
            byte piece = pieces[square];
            if (piece != Piece.EMPTY) {
                if (pieceInput.isDragging() && square == pieceInput.dragSquare()) {
                    continue;
                }
                if (animationState.moveAnimationActive() && square == animationState.animatedMoveTo()) {
                    continue;
                }
                if (animationState.moveAnimationActive() && square == animationState.animatedSecondaryMoveTo()) {
                    continue;
                }
                if (square == animationState.snapHiddenSquare()) {
                    continue;
                }
                Rectangle bounds = squareBounds(board, square);
                if (intersectsClip(clip, bounds)) {
                    drawPieceAt(g, cell, bounds.x, bounds.y, piece);
                }
            }
        }
    }
    private void drawPieceAt(Graphics2D g, int cell, int x, int y, byte piece) {
        BufferedImage image = pieceImage(piece, cell);
        if (image != null) {
            g.drawImage(image, x, y, null);
        }
    }
    private void drawSuggestedMove(Graphics2D g, Rectangle board) {
        if (!showSuggestedMoveArrow || suggestedMove == Move.NO_MOVE || !legalSuggestedMove(suggestedMove)) {
            return;
        }
        double gap = Math.max(1, board.width / 8) * BoardStyle.ARROW_PIECE_GAP_FRACTION;
        arrowPainter.drawSuggested(g, center(board, Move.getFromIndex(suggestedMove)),
                center(board, Move.getToIndex(suggestedMove)), gap);
    }
    private void drawPremove(Graphics2D g, Rectangle board) {
        if (pendingPremove == Move.NO_MOVE || !legalPremoveShape(pendingPremove)) {
            return;
        }
        Rectangle clip = g.getClipBounds();
        Rectangle from = squareBounds(board, Move.getFromIndex(pendingPremove));
        Rectangle to = squareBounds(board, Move.getToIndex(pendingPremove));
        if (intersectsClip(clip, from)) {
            BoardStyle.drawCurrentPremoveSquare(g, from);
        }
        if (intersectsClip(clip, to)) {
            BoardStyle.drawCurrentPremoveSquare(g, to);
        }
    }
    private void drawAnimatedMove(Graphics2D g, Rectangle board) {
        if (!animationState.moveAnimationActive()) {
            return;
        }
        drawAnimatedPiece(g, board,
                animationState.animatedMovePiece(),
                animationState.animatedMoveFrom(),
                animationState.animatedMoveTo());
        drawAnimatedPiece(g, board,
                animationState.animatedSecondaryMovePiece(),
                animationState.animatedSecondaryMoveFrom(),
                animationState.animatedSecondaryMoveTo());
    }
    private void drawAnimatedPiece(Graphics2D g, Rectangle board, byte piece, byte from, byte to) {
        if (piece == Piece.EMPTY || from == Field.NO_SQUARE || to == Field.NO_SQUARE) {
            return;
        }
        int cell = board.width / 8;
        Rectangle fromBounds = squareBounds(board, from);
        Rectangle toBounds = squareBounds(board, to);
        double progress = easeOutCubic(moveAnimationProgress());
        int x = (int) Math.round(fromBounds.x + (toBounds.x - fromBounds.x) * progress);
        int y = (int) Math.round(fromBounds.y + (toBounds.y - fromBounds.y) * progress);
        drawPieceAt(g, cell, x, y, piece);
    }
    private void drawAnimatedCapture(Graphics2D g, Rectangle board) {
        byte animatedCapturePiece = animationState.animatedCapturePiece();
        byte animatedCaptureSquare = animationState.animatedCaptureSquare();
        if (!animationState.moveAnimationActive()
                || animatedCapturePiece == Piece.EMPTY
                || animatedCaptureSquare == Field.NO_SQUARE) {
            return;
        }
        int cell = board.width / 8;
        Rectangle bounds = squareBounds(board, animatedCaptureSquare);
        float alpha = (float) Math.max(0.0, 1.0 - easeOutCubic(moveAnimationProgress()));
        java.awt.Composite savedComposite = g.getComposite();
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            drawPieceAt(g, cell, bounds.x, bounds.y, animatedCapturePiece);
        } finally {
            g.setComposite(savedComposite);
        }
    }
    private void drawDropTarget(Graphics2D g, Rectangle board) {
        if (!pieceInput.isDragging()) {
            return;
        }
        byte targetSquare = pieceInput.dragTargetSquare();
        if (targetSquare != Field.NO_SQUARE) {
            Rectangle bounds = squareBounds(board, targetSquare);
            if (intersectsClip(g.getClipBounds(), bounds)) {
                if (premoveSelection) {
                    BoardStyle.drawPremoveTarget(g, bounds, isOccupied(targetSquare));
                } else {
                    BoardStyle.drawLegalTarget(g, bounds, isDragCaptureTarget(targetSquare));
                }
            }
        }
    }
    private void drawDraggedPiece(Graphics2D g, Rectangle board) {
        byte draggedPiece = pieceInput.draggedPiece();
        if (!pieceInput.isDragging() || draggedPiece == Piece.EMPTY) {
            return;
        }
        int cell = board.width / 8;
        int scaledCell = (int) Math.round(cell * DRAG_SCALE);
        int offset = (scaledCell - cell) / 2;
        int dragX = pieceInput.dragX();
        int dragY = pieceInput.dragY();
        int pieceX = dragX - cell / 2 - offset;
        int pieceY = dragY - cell / 2 - offset;
        Rectangle bounds = new Rectangle(pieceX, pieceY, scaledCell, scaledCell);
        if (!intersectsClip(g.getClipBounds(), bounds)) {
            return;
        }
        java.awt.Composite savedComposite = g.getComposite();
        Color savedColor = g.getColor();
        try {
            // Soft contact shadow beneath the lifted piece gives the drag a
            // sense of elevation, matching the chess.com / lichess feel.
            int shadowWidth = Math.round(scaledCell * 0.66f);
            int shadowHeight = Math.max(6, Math.round(scaledCell * 0.22f));
            int shadowX = dragX - shadowWidth / 2;
            int shadowY = pieceY + scaledCell - shadowHeight - Math.round(scaledCell * 0.04f);
            for (int ring = 3; ring >= 1; ring--) {
                g.setColor(new Color(0, 0, 0, 16));
                g.fillOval(shadowX - ring * 2, shadowY - ring,
                        shadowWidth + ring * 4, shadowHeight + ring * 2);
            }
            g.setComposite(DRAG_ALPHA);
            BufferedImage scaled = imageCache.dragPieceImage(draggedPiece, scaledCell);
            g.drawImage(scaled, pieceX, pieceY, null);
        } finally {
            g.setComposite(savedComposite);
            g.setColor(savedColor);
        }
    }
    /**
     * Scale factor for the dragged piece image. Slightly larger than the
     * resting square so a picked-up piece reads as lifted off the board.
     */
    private static final double DRAG_SCALE = 1.12;
    private void warmDragPieceImage(byte piece) {
        Rectangle board = boardBounds();
        int cell = board.width / 8;
        int scaledCell = (int) Math.round(cell * DRAG_SCALE);
        if (scaledCell > 0) {
            imageCache.dragPieceImage(piece, scaledCell);
        }
    }
    private void handlePress(MouseEvent event) {
        if (setupEditMode) {
            handleSetupEdit(event);
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
        byte square = squareAt(event.getX(), event.getY());
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
    private void handleSetupEdit(MouseEvent event) {
        boolean paintGesture = setupPaintGesture(event);
        boolean eraseGesture = setupEraseGesture(event);
        if (!paintGesture && !eraseGesture) {
            return;
        }
        byte square = squareAt(event.getX(), event.getY());
        if (square == Field.NO_SQUARE) {
            return;
        }
        byte piece = eraseGesture ? Piece.EMPTY : setupEditSelectedPiece;
        setSetupEditPieceAt(square, piece, true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        event.consume();
    }

    /**
     * Returns whether a setup-editor event should paint the selected piece.
     *
     * @param event mouse event
     * @return true for left-button setup painting
     */
    private static boolean setupPaintGesture(MouseEvent event) {
        return SwingUtilities.isLeftMouseButton(event)
                || (event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0;
    }

    /**
     * Returns whether a setup-editor event should erase the target square.
     *
     * @param event mouse event
     * @return true for right-button setup erasing
     */
    private static boolean setupEraseGesture(MouseEvent event) {
        return SwingUtilities.isRightMouseButton(event)
                || (event.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0;
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
        markupInput.complete(squareAt(event.getX(), event.getY()));
        repaint();
    }

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
    private PromotionOverlay promotionOverlay;
    private void showPromotionOverlay(short[] candidates) {
        cancelPromotionOverlay();
        byte target = Move.getToIndex(candidates[0]);
        promotionOverlay = new PromotionOverlay(target, candidates, () -> position != null && position.isWhiteToMove());
        repaint();
    }
    private void cancelPromotionOverlay() {
        if (promotionOverlay != null) {
            promotionOverlay = null;
            repaint();
        }
    }
    private void handleDrag(MouseEvent event) {
        if (setupEditMode) {
            handleSetupEdit(event);
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
        Rectangle oldDirty = dragRepaintBounds(board, pieceInput.dragTargetSquare(), pieceInput.dragHoverSquare(),
                pieceInput.dragX(), pieceInput.dragY(), wasDragging, false);
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
            scheduleDragRepaint(union(oldDirty,
                    dragRepaintBounds(board, pieceInput.dragTargetSquare(), pieceInput.dragHoverSquare(),
                            pieceInput.dragX(), pieceInput.dragY(), pieceInput.isDragging(), dragStarted)));
        }
    }
    private void handleRelease(MouseEvent event) {
        if (setupEditMode) {
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
        startAnimation();
    }
    private byte legalDropTarget(byte square) {
        byte dragSquare = pieceInput.dragSquare();
        if (square == Field.NO_SQUARE || dragSquare == Field.NO_SQUARE) {
            return Field.NO_SQUARE;
        }
        return findLegalMove(dragSquare, square) == Move.NO_MOVE ? Field.NO_SQUARE : square;
    }
    private byte premoveDropTarget(byte square) {
        byte dragSquare = pieceInput.dragSquare();
        if (square == Field.NO_SQUARE || dragSquare == Field.NO_SQUARE || square == dragSquare) {
            return Field.NO_SQUARE;
        }
        return isPremoveTarget(dragSquare, pieceInput.draggedPiece(), square) ? square : Field.NO_SQUARE;
    }
    private boolean isCaptureTarget(byte square) {
        if (position == null || !isSquareIndex(square)) {
            return false;
        }
        byte piece = position.getBoard()[square];
        return piece != Piece.EMPTY && Piece.isWhite(piece) != position.isWhiteToMove();
    }
    private boolean isOccupied(byte square) {
        return position != null && isSquareIndex(square) && position.getBoard()[square] != Piece.EMPTY;
    }
    private boolean isDragCaptureTarget(byte square) {
        if (!premoveSelection || position == null || !isSquareIndex(square)) {
            return isCaptureTarget(square);
        }
        byte target = position.getBoard()[square];
        byte dragged = pieceInput.draggedPiece();
        return target != Piece.EMPTY && dragged != Piece.EMPTY && Piece.isWhite(target) != Piece.isWhite(dragged);
    }
    private boolean isPremoveSource(byte square, byte piece) {
        if (position == null || premoveStartFilter == null || premoveHandler == null) {
            return false;
        }
        if (!isSquareIndex(square) || piece == Piece.EMPTY) {
            return false;
        }
        return premoveStartFilter.test(new DragContext(square, piece, position.toString()));
    }
    private boolean queuePremove(byte from, byte to, byte piece) {
        if (premoveHandler == null || position == null) {
            return false;
        }
        if (!isSquareIndex(from) || !isSquareIndex(to) || from == to || piece == Piece.EMPTY
                || !isPremoveTarget(from, piece, to)) {
            return false;
        }
        short move = Move.of(from, to, premovePromotion(piece, to));
        boolean accepted = premoveHandler.queue(
                new PremoveContext(from, to, piece, position.toString(), move));
        if (accepted) {
            setPremove(move);
        }
        return accepted;
    }
    private byte[] premoveTargets(byte from, byte piece) {
        if (!isSquareIndex(from) || piece == Piece.EMPTY) {
            return new byte[0];
        }
        byte[] buffer = new byte[64];
        int count = 0;
        for (byte target = 0; target < 64; target++) {
            if (target != from && isPremoveTarget(from, piece, target)) {
                buffer[count++] = target;
            }
        }
        return Arrays.copyOf(buffer, count);
    }
    private boolean isPremoveTarget(byte from, byte piece, byte to) {
        if (!isSquareIndex(from) || !isSquareIndex(to) || from == to || piece == Piece.EMPTY) {
            return false;
        }
        int fromFile = Field.getX(from);
        int fromRank = Field.getY(from);
        int toFile = Field.getX(to);
        int toRank = Field.getY(to);
        int df = Math.abs(toFile - fromFile);
        int dr = Math.abs(toRank - fromRank);
        return switch (Math.abs(piece)) {
            case Piece.PAWN -> isPremovePawnTarget(piece, fromFile, fromRank, toFile, toRank);
            case Piece.KNIGHT -> df * dr == 2;
            case Piece.BISHOP -> df == dr;
            case Piece.ROOK -> df == 0 || dr == 0;
            case Piece.QUEEN -> df == dr || df == 0 || dr == 0;
            case Piece.KING -> Math.max(df, dr) == 1
                    || isPremoveCastleTarget(piece, fromFile, fromRank, toFile, toRank);
            default -> false;
        };
    }
    private static boolean isPremovePawnTarget(byte piece, int fromFile, int fromRank, int toFile, int toRank) {
        int direction = Piece.isWhite(piece) ? 1 : -1;
        int df = Math.abs(toFile - fromFile);
        int rankDelta = toRank - fromRank;
        if (df == 1) {
            return rankDelta == direction;
        }
        if (df != 0) {
            return false;
        }
        if (rankDelta == direction) {
            return true;
        }
        int startRank = Piece.isWhite(piece) ? 1 : 6;
        return fromRank == startRank && rankDelta == direction * 2;
    }
    private boolean isPremoveCastleTarget(byte piece, int fromFile, int fromRank, int toFile, int toRank) {
        boolean white = Piece.isWhite(piece);
        int homeRank = white ? 0 : 7;
        if (fromFile != 4 || fromRank != homeRank || toRank != homeRank) {
            return false;
        }
        byte[] board = position == null ? null : position.getBoard();
        if (board == null) {
            return false;
        }
        byte rook = white ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
        if (toFile == 6) {
            return board[(byte) Field.toIndex(7, homeRank)] == rook;
        }
        if (toFile == 2) {
            return board[(byte) Field.toIndex(0, homeRank)] == rook;
        }
        return false;
    }
    private static byte premovePromotion(byte piece, byte to) {
        if (piece != Piece.WHITE_PAWN && piece != Piece.BLACK_PAWN) {
            return 0;
        }
        int rank = Field.getY(to);
        return rank == 0 || rank == 7 ? (byte) 4 : 0;
    }
    private void clearDragState() {
        cancelPendingDragRepaint();
        pieceInput.clearDrag();
    }
    private void clearSelection() {
        pieceInput.clearSelection();
        premoveSelection = false;
    }
    private void setSetupEditPieceAt(byte square, byte piece, boolean notify) {
        if (!isSquareIndex(square)) {
            throw new IllegalArgumentException("Invalid square " + square);
        }
        validateSetupPiece(piece);
        if (setupEditBoard[square] == piece && setupEditLastSquare == square) {
            return;
        }
        setupEditBoard[square] = piece;
        setupEditLastSquare = square;
        if (notify && setupEditObserver != null) {
            setupEditObserver.accept(Byte.valueOf(square), Byte.valueOf(piece));
        }
        if (setupEditMode) {
            repaint(squareBounds(boardBounds(), square));
        }
    }
    private void selectSquare(byte square) {
        premoveSelection = false;
        pieceInput.select(square, new short[0], new byte[0]);
        refreshSelectedLegalMoves();
    }
    private void selectPremoveSquare(byte square) {
        premoveSelection = true;
        byte piece = position == null || !isSquareIndex(square) ? Piece.EMPTY : position.getBoard()[square];
        pieceInput.select(square, new short[0], premoveTargets(square, piece));
    }
    private void updateCursor(MouseEvent event) {
        if (setupEditMode) {
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
    private short findLegalMove(byte from, byte to) {
        if (pieceInput.isSelected(from)) {
            return findLegalMove(pieceInput.selectedLegalMoves(), to);
        }
        return findLegalMove(legalMovesFrom(from), to);
    }
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
    private static boolean containsTarget(byte[] targets, int count, byte target) {
        for (int i = 0; i < count; i++) {
            if (targets[i] == target) {
                return true;
            }
        }
        return false;
    }
    private Rectangle boardBounds() {
        // Reserve a left strip for the eval bar so the board square never
        // slides underneath it.
        int leftReserve = evalBar != null ? EVAL_BAR_WIDTH + EVAL_BAR_GAP : 0;
        int availWidth = getWidth() - leftReserve;
        int size = Math.min(availWidth - BOARD_MARGIN * 2, getHeight() - BOARD_MARGIN * 2);
        size = Math.max(MIN_BOARD_SIZE, size - size % 8);
        int x = leftReserve + (availWidth - size) / 2;
    return new Rectangle(x, (getHeight() - size) / 2, size, size);
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
        if (evalBar != null) {
            remove(evalBar);
        }
        evalBar = bar;
        if (bar != null) {
            setLayout(null);
            add(bar);
        }
        revalidate();
        repaint();
    }
    /**
     * Positions the attached eval bar flush against the board square.
     */
    @Override
    public void doLayout() {
        super.doLayout();
        if (evalBar != null) {
            Rectangle square = boardBounds();
            int barX = Math.max(0, square.x - EVAL_BAR_GAP - EVAL_BAR_WIDTH);
            evalBar.setBounds(barX, square.y, EVAL_BAR_WIDTH, square.height);
        }
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
        byte[] pieces = setupEditMode ? setupEditBoard.clone()
                : position == null ? new byte[64] : position.getBoard().clone();
        return new BoardExportSnapshot(
                pieces,
                whiteDown,
                lastMove,
                suggestedMove,
                pieceInput.selectedSquare(),
                pieceInput.selectedLegalTargets(),
                selectedCaptureTargets(),
                checkedKingSquare(),
                new LinkedHashMap<>(squareHighlights),
                markupInput.copyMarkups(),
                showNotation,
                showLegalMovePreview,
                showLastMoveHighlight,
                showSuggestedMoveArrow);
    }
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
    private Rectangle squareBounds(Rectangle board, byte square) {
        return BoardGeometry.squareBounds(board, square, whiteDown);
    }
    private BufferedImage pieceImage(byte piece, int cell) {
        return imageCache.pieceImage(piece, cell);
    }
    private Rectangle dragRepaintBounds(
            Rectangle board,
            byte targetSquare,
            byte hoverSquare,
            int pointerX,
            int pointerY,
            boolean includeDraggedPiece,
            boolean includeOriginSquare) {
        Rectangle dirty = null;
        byte dragSquare = pieceInput.dragSquare();
        if (includeOriginSquare && isSquareIndex(dragSquare)) {
            dirty = union(dirty, expanded(squareBounds(board, dragSquare), DRAG_REPAINT_PADDING));
        }
        if (isSquareIndex(targetSquare)) {
            dirty = union(dirty, expanded(squareBounds(board, targetSquare), DRAG_REPAINT_PADDING));
        }
        if (isSquareIndex(hoverSquare)) {
            dirty = union(dirty, expanded(squareBounds(board, hoverSquare), DRAG_REPAINT_PADDING));
        }
        if (includeDraggedPiece && pieceInput.draggedPiece() != Piece.EMPTY) {
            int cell = board.width / 8;
            int scaledCell = (int) Math.round(cell * DRAG_SCALE);
            dirty = union(dirty, new Rectangle(
                    pointerX - scaledCell / 2 - DRAG_REPAINT_PADDING,
                    pointerY - scaledCell / 2 - DRAG_REPAINT_PADDING,
                    scaledCell + DRAG_REPAINT_PADDING * 2,
                    scaledCell + DRAG_REPAINT_PADDING * 2));
        }
        return dirty;
    }
    private void scheduleDragRepaint(Rectangle dirty) {
        if (dirty == null) {
            return;
        }
        pendingDragDirty = union(pendingDragDirty, dirty);
        long now = System.nanoTime();
        long elapsed = lastDragRepaintNanos == 0L ? Long.MAX_VALUE : now - lastDragRepaintNanos;
        if (elapsed >= DRAG_REPAINT_FRAME_NANOS) {
            flushPendingDragRepaint();
            return;
        }
        if (!dragRepaintTimer.isRunning()) {
            long remaining = DRAG_REPAINT_FRAME_NANOS - elapsed;
            int delayMs = (int) Math.max(1L, (remaining + 999_999L) / 1_000_000L);
            dragRepaintTimer.setInitialDelay(delayMs);
            dragRepaintTimer.restart();
        }
    }
    private void flushPendingDragRepaint() {
        Rectangle dirty = pendingDragDirty;
        pendingDragDirty = null;
        dragRepaintTimer.stop();
        if (dirty != null) {
            lastDragRepaintNanos = System.nanoTime();
            repaintDirty(dirty);
        }
    }
    private void cancelPendingDragRepaint() {
        pendingDragDirty = null;
        lastDragRepaintNanos = 0L;
        dragRepaintTimer.stop();
    }
    private void repaintDirty(Rectangle dirty) {
        if (dirty != null) {
            repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }
    }
    private static Rectangle expanded(Rectangle bounds, int padding) {
    return new Rectangle(bounds.x - padding, bounds.y - padding,
                bounds.width + padding * 2, bounds.height + padding * 2);
    }
    private static Rectangle union(Rectangle first, Rectangle second) {
        if (first == null) {
            return second == null ? null : new Rectangle(second);
        }
        if (second == null) {
    return new Rectangle(first);
        }
        return first.union(second);
    }
    private static boolean intersectsClip(Rectangle clip, Rectangle bounds) {
        return clip == null || bounds == null || clip.intersects(bounds);
    }
    private byte squareAt(int x, int y) {
        return BoardGeometry.squareAt(boardBounds(), x, y, whiteDown);
    }
    private Point center(Rectangle board, byte square) {
        return BoardGeometry.center(board, square, whiteDown);
    }
    private void startAnimation() {
        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
    }
    private void startMoveAnimation(byte[] oldBoard, byte[] newBoard, short move,
            boolean reverseMoveAnimation) {
        if (!animationState.canAnimateMove()) {
            clearMoveAnimation();
            return;
        }
        clearMoveAnimation();
        if (oldBoard == null || newBoard == null || move == Move.NO_MOVE) {
            return;
        }
        byte from = Move.getFromIndex(move);
        byte to = Move.getToIndex(move);
        if (!isSquareIndex(from) || !isSquareIndex(to)) {
            return;
        }
        byte moveFrom = from;
        byte moveTo = to;
        if (reverseMoveAnimation) {
            from = moveTo;
            to = moveFrom;
        }
        byte piece = animatedPiece(oldBoard, newBoard, from, to);
        if (piece == Piece.EMPTY) {
            return;
        }
        animationState.startMove(piece, from, to, System.currentTimeMillis());
        configureAnimatedCapture(oldBoard, newBoard, from, to, piece);
        configureSecondaryMove(oldBoard, newBoard, moveFrom, moveTo, reverseMoveAnimation);
        startAnimation();
    }
    private static byte animatedPiece(byte[] oldBoard, byte[] newBoard, byte from, byte to) {
        byte source = oldBoard[from];
        byte target = newBoard[to];
        if (source == Piece.EMPTY || target == Piece.EMPTY || Piece.isWhite(source) != Piece.isWhite(target)) {
            return Piece.EMPTY;
        }
        return target;
    }
    private void configureAnimatedCapture(byte[] oldBoard, byte[] newBoard, byte from, byte to, byte movingPiece) {
        byte direct = oldBoard[to];
        if (direct != Piece.EMPTY && Piece.isWhite(direct) != Piece.isWhite(movingPiece)) {
            animationState.setAnimatedCapture(direct, to);
            return;
        }
        if (!Piece.isPawn(oldBoard[from]) || Field.getX(from) == Field.getX(to)) {
            return;
        }
        byte candidate = (byte) ((from / 8) * 8 + Field.getX(to));
        if (isSquareIndex(candidate) && oldBoard[candidate] != Piece.EMPTY && newBoard[candidate] == Piece.EMPTY
                && Piece.isWhite(oldBoard[candidate]) != Piece.isWhite(movingPiece)) {
            animationState.setAnimatedCapture(oldBoard[candidate], candidate);
        }
    }
    private void configureSecondaryMove(byte[] oldBoard, byte[] newBoard, byte moveFrom, byte moveTo,
            boolean reverseMoveAnimation) {
        byte kingFrom = reverseMoveAnimation ? moveTo : moveFrom;
        byte movingPiece = oldBoard[kingFrom];
        if (!Piece.isKing(movingPiece)) {
            return;
        }
        int fileDelta = Field.getX(moveTo) - Field.getX(moveFrom);
        if (Math.abs(fileDelta) != 2) {
            return;
        }
        int rank = moveFrom / 8;
        byte forwardRookFrom = (byte) (rank * 8 + (fileDelta > 0 ? 7 : 0));
        byte forwardRookTo = (byte) (rank * 8 + (fileDelta > 0 ? 5 : 3));
        byte rookFrom = reverseMoveAnimation ? forwardRookTo : forwardRookFrom;
        byte rookTo = reverseMoveAnimation ? forwardRookFrom : forwardRookTo;
        if (!isSquareIndex(rookFrom) || !isSquareIndex(rookTo)) {
            return;
        }
        byte rookPiece = oldBoard[rookFrom];
        if (rookPiece == Piece.EMPTY || newBoard[rookTo] != rookPiece) {
            return;
        }
        animationState.setAnimatedSecondaryMove(rookPiece, rookFrom, rookTo);
    }
    private void clearMoveAnimation() {
        animationState.clearMoveAnimation();
    }
    private void clearAllAnimations() {
        if (animationState.clearAllAnimations()) {
            // Commit a pending flip so disabling animations mid-flight does not
            // silently leave the board in its pre-flip orientation.
            whiteDown = !whiteDown;
        }
        animationTimer.stop();
    }
    private void tickAnimation() {
        if (animationState.moveAnimationActive() && moveAnimationProgress() >= 1.0) {
            clearMoveAnimation();
        }
        SnapAnimation finished = animationState.takeFinishedSnapAnimation();
        if (finished != null) {
            if (finished.snapback) {
                if (snapbackEndObserver != null) {
                    snapbackEndObserver.run();
                }
            } else if (snapEndObserver != null) {
                snapEndObserver.run();
            }
        }
        if (animationState.wrongMoveMarkerSquare() != Field.NO_SQUARE && wrongMoveMarkerProgress() >= 1.0) {
            clearWrongMoveMarkerState();
        }
        if (animationState.tickFlipAnimation(System.currentTimeMillis())) {
            whiteDown = !whiteDown;
        }
        if (!hasActiveAnimation()) {
            animationTimer.stop();
        }
        repaint();
    }
    private boolean hasActiveAnimation() {
        return animationState.hasActiveAnimation();
    }
    private double moveAnimationProgress() {
        return animationState.moveAnimationProgress(System.currentTimeMillis());
    }
    private double wrongMoveMarkerProgress() {
        return animationState.wrongMoveMarkerProgress(System.currentTimeMillis());
    }
    private void clearWrongMoveMarkerState() {
        animationState.clearWrongMoveMarker();
    }
    /**
     * Cubic ease-out matching the short board transition feel.
     * @param value new value
     * @return eased value
     */
    public static double easeOutCubic(double value) {
        return Ui.easeOutCubic(value);
    }
    private static boolean isSquareIndex(byte square) {
        return square >= 0 && square < 64;
    }
    private static void validateSetupPiece(byte piece) {
        if (piece < Piece.BLACK_KING || piece > Piece.WHITE_KING) {
            throw new IllegalArgumentException("Invalid piece " + piece);
        }
    }
    private static float displayScale() {
        try {
            java.awt.GraphicsEnvironment env = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
            java.awt.GraphicsDevice device = env.getDefaultScreenDevice();
            return (float) device.getDefaultConfiguration().getDefaultTransform().getScaleX();
        } catch (java.awt.HeadlessException ex) {
            return 1f;
        }
    }
}
