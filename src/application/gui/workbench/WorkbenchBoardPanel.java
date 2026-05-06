package application.gui.workbench;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.images.assets.Shapes;

/**
 * Native Java2D chess board used by the CRTK Workbench.
 */
final class WorkbenchBoardPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Board margin in pixels.
     */
    private static final int BOARD_MARGIN = 32;

    /**
     * Minimum board size in pixels.
     */
    private static final int MIN_BOARD_SIZE = 64;

    /**
     * Chessboard.js board border width.
     */
    private static final int BOARD_BORDER = 2;

    /**
     * Minimum pointer movement before a press becomes a drag.
     */
    private static final int DRAG_THRESHOLD = 5;

    /**
     * Piece glide duration for short board transitions.
     */
    private static final int MOVE_ANIMATION_MS = 95;

    /**
     * Board animation timer interval.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Offset that maps signed piece codes into the image-cache array.
     */
    private static final int PIECE_CACHE_OFFSET = 6;

    /**
     * Number of slots needed for all signed piece codes and the empty square.
     */
    private static final int PIECE_CACHE_SIZE = PIECE_CACHE_OFFSET * 2 + 1;

    /**
     * Extra pixels included around drag dirty rectangles.
     */
    private static final int DRAG_REPAINT_PADDING = 8;

    /**
     * Cached 1-pixel stroke for board hairlines.
     */
    private static final BasicStroke STROKE_1 = new BasicStroke(1f);

    /**
     * Cached 2-pixel stroke for selected-square edges.
     */
    private static final BasicStroke STROKE_2 = new BasicStroke(2f);

    /**
     * Cached arrow body stroke.
     */
    private static final BasicStroke ARROW_STROKE = new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER);

    /**
     * Cached drag transparency composite.
     */
    private static final AlphaComposite DRAG_ALPHA = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f);

    /**
     * Cached suggested-arrow color matching chessboard-arrows' 0.8 canvas opacity.
     */
    private static final Color ARROW_FILL = WorkbenchTheme.withAlpha(WorkbenchTheme.BOARD_ARROW, 204);

    /**
     * Cached transparent variant of the check-glow used at the radial gradient edges.
     */
    private static final Color CHECK_GLOW_FADE = WorkbenchTheme.withAlpha(WorkbenchTheme.CHECK_GLOW, 0);

    /**
     * Suggested-arrow head radius matching chessboard-arrows.
     */
    private static final int ARROW_HEAD_RADIUS = 15;

    /**
     * Suggested-arrow shorten-to-target distance.
     */
    private static final int ARROW_SHORTEN = 15;

    /**
     * Sentinel destination for a circle annotation.
     */
    private static final byte MARKUP_CIRCLE = Field.NO_SQUARE;

    /**
     * Lichess/Chessground user annotation brushes.
     */
    private static final MarkupBrush[] MARKUP_BRUSHES = {
            new MarkupBrush("green", new Color(0x15, 0x78, 0x1B), 10),
            new MarkupBrush("red", new Color(0x88, 0x20, 0x20), 10),
            new MarkupBrush("blue", new Color(0x00, 0x30, 0x88), 10),
            new MarkupBrush("yellow", new Color(0xE6, 0x8F, 0x00), 10)
    };

    /**
     * Active-drawing opacity matching Chessground current shape rendering.
     */
    private static final double MARKUP_CURRENT_OPACITY = 0.9;

    /**
     * Pending-erase opacity matching Chessground shape toggling feedback.
     */
    private static final double MARKUP_PENDING_ERASE_OPACITY = 0.6;

    /**
     * Current position.
     */
    private Position position;

    /**
     * Board orientation.
     */
    private boolean whiteDown = true;

    /**
     * Last played move.
     */
    private short lastMove = Move.NO_MOVE;

    /**
     * Suggested move highlighted by command output.
     */
    private short suggestedMove = Move.NO_MOVE;

    /**
     * Selected square.
     */
    private byte selectedSquare = Field.NO_SQUARE;

    /**
     * Legal moves from the selected square.
     */
    private short[] selectedLegalMoves = new short[0];

    /**
     * Unique legal targets from the selected square.
     */
    private byte[] selectedLegalTargets = new byte[0];

    /**
     * Square currently being dragged.
     */
    private byte dragSquare = Field.NO_SQUARE;

    /**
     * Current legal drop target under the pointer.
     */
    private byte dragTargetSquare = Field.NO_SQUARE;

    /**
     * Current pointer-hovered square during a drag, regardless of legality.
     */
    private byte dragHoverSquare = Field.NO_SQUARE;

    /**
     * Piece currently being dragged.
     */
    private byte draggedPiece = Piece.EMPTY;

    /**
     * Pointer x coordinate at drag start.
     */
    private int dragStartX;

    /**
     * Pointer y coordinate at drag start.
     */
    private int dragStartY;

    /**
     * Current drag pointer x coordinate.
     */
    private int dragX;

    /**
     * Current drag pointer y coordinate.
     */
    private int dragY;

    /**
     * Whether the current press has crossed the drag threshold.
     */
    private boolean draggingPiece;

    /**
     * Piece currently gliding after a played move.
     */
    private byte animatedMovePiece = Piece.EMPTY;

    /**
     * Secondary piece gliding after a compound move such as castling.
     */
    private byte animatedSecondaryMovePiece = Piece.EMPTY;

    /**
     * Captured piece currently fading out.
     */
    private byte animatedCapturePiece = Piece.EMPTY;

    /**
     * Origin square for the gliding piece.
     */
    private byte animatedMoveFrom = Field.NO_SQUARE;

    /**
     * Destination square for the gliding piece.
     */
    private byte animatedMoveTo = Field.NO_SQUARE;

    /**
     * Origin square for a secondary gliding piece.
     */
    private byte animatedSecondaryMoveFrom = Field.NO_SQUARE;

    /**
     * Destination square for a secondary gliding piece.
     */
    private byte animatedSecondaryMoveTo = Field.NO_SQUARE;

    /**
     * Square where a captured piece fades out.
     */
    private byte animatedCaptureSquare = Field.NO_SQUARE;

    /**
     * Move-animation start time.
     */
    private long moveAnimationStartedAt;

    /**
     * Whether a move animation is currently active.
     */
    private boolean moveAnimationActive;

    /**
     * Move handler.
     */
    private MoveHandler moveHandler;

    /**
     * Timer driving subtle board animations.
     */
    private final Timer animationTimer;

    /**
     * Scaled piece image cache for the current board cell size.
     */
    private final BufferedImage[] pieceImageCache = new BufferedImage[PIECE_CACHE_SIZE];

    /**
     * Cell size represented by {@link #pieceImageCache}.
     */
    private int pieceImageCacheCell = -1;

    /**
     * Cached board texture for the current board size.
     */
    private BufferedImage boardTextureCache;

    /**
     * Board size represented by {@link #boardTextureCache}.
     */
    private int boardTextureCacheSize = -1;

    /**
     * Cached legal moves for the current position (lazy, invalidated on every {@link #setPosition}).
     */
    private MoveList cachedLegalMoves;

    /**
     * Whether file/rank coordinates are painted on the board.
     */
    private boolean showNotation = true;

    /**
     * Whether selected-piece legal destinations and drag targets are painted.
     */
    private boolean showLegalMovePreview = true;

    /**
     * Whether the previous move should be highlighted.
     */
    private boolean showLastMoveHighlight = true;

    /**
     * Whether engine suggested moves should be painted as arrows.
     */
    private boolean showSuggestedMoveArrow = true;

    /**
     * Whether board animations should run.
     */
    private boolean animationsEnabled = true;

    /**
     * User-supplied square highlights (overlaid on top of the board texture).
     */
    private final Map<Byte, Color> squareHighlights = new LinkedHashMap<>();

    /**
     * User-supplied Lichess-style arrow and circle markups.
     */
    private final List<BoardMarkup> boardMarkups = new ArrayList<>();

    /**
     * Active right-button drawing preview.
     */
    private BoardMarkup currentMarkup;

    /**
     * Origin square of the active right-button drawing gesture.
     */
    private byte markupOrigin = Field.NO_SQUARE;

    /**
     * Brush of the active right-button drawing gesture.
     */
    private MarkupBrush markupBrush;

    /**
     * Optional drag-start veto. When set, a drag is allowed only if the
     * predicate returns true. Mirrors chessboard.js {@code onDragStart}.
     */
    private Predicate<DragContext> dragStartFilter;

    /**
     * Optional drop callback. Receives drop context and returns the move to
     * play, or {@code Move.NO_MOVE} for snapback. Mirrors {@code onDrop}.
     */
    private ToIntFunction<DropContext> dropResolver;

    /**
     * Optional position-change observer (old FEN, new FEN).
     */
    private BiConsumer<String, String> changeObserver;

    /**
     * Optional snapback-completion observer.
     */
    private Runnable snapbackEndObserver;

    /**
     * Optional snap-completion observer.
     */
    private Runnable snapEndObserver;

    /**
     * Optional mouseover-square observer.
     */
    private IntConsumer mouseoverSquareObserver;

    /**
     * Last square reported to {@link #mouseoverSquareObserver}, or
     * {@link Field#NO_SQUARE}.
     */
    private byte lastMouseoverSquare = Field.NO_SQUARE;

    /**
     * Active snapback/snap animation state.
     */
    private SnapAnimation snapAnimation;

    /**
     * Square whose static piece should not be painted while a snap or move
     * animation is making the piece visually appear there. Avoids double-
     * rendering during snaps.
     */
    private byte snapHiddenSquare = Field.NO_SQUARE;

    /**
     * When true, the next {@link #setPosition} call will skip starting a
     * move animation. Set by {@link #handleRelease} when the snap animation
     * already covers the piece's motion.
     */
    private boolean suppressNextMoveAnimation;

    /**
     * Active orientation-flip animation progress (0..1), or NaN when idle.
     */
    private double flipAnimationProgress = Double.NaN;

    /**
     * Flip animation start time.
     */
    private long flipAnimationStartedAt;

    /**
     * Move-animation duration (chessboard.js {@code moveSpeed} equivalent).
     */
    private int moveAnimationMs = MOVE_ANIMATION_MS;

    /**
     * Snapback duration when an illegal drop returns to its origin.
     */
    private int snapbackAnimationMs = 90;

    /**
     * Snap duration when a valid drop slides into place.
     */
    private int snapAnimationMs = 55;

    /**
     * Flip animation duration.
     */
    private int flipAnimationMs = 140;

    /**
     * Creates the board panel.
     */
    WorkbenchBoardPanel() {
        setOpaque(true);
        setBackground(WorkbenchTheme.BG);
        int basis = Math.round(620 * displayScale());
        setPreferredSize(new Dimension(basis, basis));
        setMinimumSize(new Dimension(Math.round(MIN_BOARD_SIZE + BOARD_MARGIN * 2 * displayScale()),
                Math.round(MIN_BOARD_SIZE + BOARD_MARGIN * 2 * displayScale())));
        animationTimer = new Timer(ANIMATION_DELAY_MS, event -> tickAnimation());
        animationTimer.setCoalesce(true);
        MouseAdapter mouseHandler = new MouseAdapter() {
            /**
             * Starts board pointer interaction from a mouse press.
             *
             * @param event mouse event
             */
            @Override
            public void mousePressed(MouseEvent event) {
                handlePress(event);
            }

            /**
             * Completes a board pointer interaction.
             *
             * @param event mouse event
             */
            @Override
            public void mouseReleased(MouseEvent event) {
                handleRelease(event);
            }

            /**
             * Updates drag-and-drop piece movement.
             *
             * @param event mouse event
             */
            @Override
            public void mouseDragged(MouseEvent event) {
                handleDrag(event);
            }

            /**
             * Updates the cursor for draggable pieces.
             *
             * @param event mouse event
             */
            @Override
            public void mouseMoved(MouseEvent event) {
                updateCursor(event);
            }

            /**
             * Restores the default cursor after leaving the board.
             *
             * @param event mouse event
             */
            @Override
            public void mouseExited(MouseEvent event) {
                setCursor(Cursor.getDefaultCursor());
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
            }
        });
    }

    /**
     * Sets the position.
     *
     * @param value position
     * @param move last move
     */
    void setPosition(Position value, short move) {
        byte[] previousBoard = position == null ? null : position.getBoard();
        position = value;
        cachedLegalMoves = null;
        lastMove = move;
        suggestedMove = Move.NO_MOVE;
        clearSelection();
        clearDragState();
        if (suppressNextMoveAnimation) {
            suppressNextMoveAnimation = false;
            clearMoveAnimation();
        } else {
            startMoveAnimation(previousBoard, position == null ? null : position.getBoard(), move);
        }
        repaint();
    }

    /**
     * Returns lazily-cached legal moves for the current position.
     *
     * @return move list, or null when there is no position
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
     *
     * @param value true when White is at the bottom
     */
    void setWhiteDown(boolean value) {
        whiteDown = value;
        repaint();
    }

    /**
     * Returns board orientation.
     *
     * @return true when White is at the bottom
     */
    boolean isWhiteDown() {
        return whiteDown;
    }

    /**
     * Sets a suggested move.
     *
     * @param move suggested move
     */
    void setSuggestedMove(short move) {
        short next = legalSuggestedMove(move) ? move : Move.NO_MOVE;
        suggestedMove = next;
        if (next != Move.NO_MOVE) {
            clearSelection();
            clearDragState();
        }
        repaint();
    }

    /**
     * Returns whether a suggested move is legal in the current board state.
     *
     * @param move encoded move
     * @return true when drawable as a best-move arrow
     */
    private boolean legalSuggestedMove(short move) {
        if (move == Move.NO_MOVE || position == null) {
            return false;
        }
        MoveList moves = currentLegalMoves();
        return moves != null && moves.contains(move);
    }

    /**
     * Returns the current FEN, or {@code null} when no position has been set.
     * chessboard.js {@code position()} equivalent.
     *
     * @return current FEN
     */
    String position() {
        return position == null ? null : position.toString();
    }

    /**
     * Replaces the position from a FEN.
     * chessboard.js {@code position(fen, useAnimation)} equivalent.
     *
     * @param fen FEN string, or {@code "start"}, or {@code "empty"}
     * @param animate whether to animate the change
     */
    void position(String fen, boolean animate) {
        if (fen == null || fen.equalsIgnoreCase("empty")) {
            applyPosition(null, Move.NO_MOVE, animate);
            return;
        }
        String resolved = fen.equalsIgnoreCase("start") ? chess.core.Setup.getStandardStartFEN() : fen;
        applyPosition(new Position(resolved), Move.NO_MOVE, animate);
    }

    /**
     * Resets to the standard start position. chessboard.js {@code start()}.
     */
    void start() {
        position("start", true);
    }

    /**
     * Clears the board (no pieces). chessboard.js {@code clear()}.
     */
    void clear() {
        position("empty", true);
    }

    /**
     * Toggles board orientation with a smooth flip animation.
     * chessboard.js {@code flip()}.
     */
    void flip() {
        if (!animationsEnabled || flipAnimationMs <= 0 || getWidth() <= 0) {
            // Headless or zero-sized panel: flip immediately.
            whiteDown = !whiteDown;
            repaint();
            return;
        }
        flipAnimationStartedAt = System.currentTimeMillis();
        flipAnimationProgress = 0.0;
        flipPending = true;
        startAnimation();
        repaint();
    }

    /**
     * True between the start of a flip animation and its midpoint, while the
     * orientation should still appear as it did before {@link #flip()} was
     * called.
     */
    private boolean flipPending;

    /**
     * Sets the orientation explicitly.
     *
     * @param value {@code "white"} or {@code "black"}
     */
    void orientation(String value) {
        boolean nextWhiteDown = "white".equalsIgnoreCase(value);
        if (nextWhiteDown == whiteDown) {
            return;
        }
        flip();
    }

    /**
     * Returns the current orientation as {@code "white"} or {@code "black"}.
     *
     * @return orientation string
     */
    String orientation() {
        return whiteDown ? "white" : "black";
    }

    /**
     * Toggles whether file/rank coordinates are painted.
     * chessboard.js {@code showNotation}.
     *
     * @param show true to paint notation
     */
    void setShowNotation(boolean show) {
        if (show == showNotation) {
            return;
        }
        showNotation = show;
        repaint();
    }

    /**
     * Returns whether file/rank coordinates are currently painted.
     *
     * @return true when painted
     */
    boolean isShowNotation() {
        return showNotation;
    }

    /**
     * Sets whether selected-piece legal destinations and drag targets are painted.
     *
     * @param show true to paint legal move previews
     */
    void setShowLegalMovePreview(boolean show) {
        if (show == showLegalMovePreview) {
            return;
        }
        showLegalMovePreview = show;
        repaint();
    }

    /**
     * Returns whether legal move previews are painted.
     *
     * @return true when legal previews are visible
     */
    boolean isShowLegalMovePreview() {
        return showLegalMovePreview;
    }

    /**
     * Sets whether the previous move should be highlighted.
     *
     * @param show true to paint last-move highlights
     */
    void setShowLastMoveHighlight(boolean show) {
        if (show == showLastMoveHighlight) {
            return;
        }
        showLastMoveHighlight = show;
        repaint();
    }

    /**
     * Returns whether previous-move highlights are painted.
     *
     * @return true when last-move highlights are visible
     */
    boolean isShowLastMoveHighlight() {
        return showLastMoveHighlight;
    }

    /**
     * Sets whether suggested engine moves should be painted as arrows.
     *
     * @param show true to paint suggested arrows
     */
    void setShowSuggestedMoveArrow(boolean show) {
        if (show == showSuggestedMoveArrow) {
            return;
        }
        showSuggestedMoveArrow = show;
        repaint();
    }

    /**
     * Returns whether suggested engine arrows are painted.
     *
     * @return true when suggested arrows are visible
     */
    boolean isShowSuggestedMoveArrow() {
        return showSuggestedMoveArrow;
    }

    /**
     * Highlights one square with an arbitrary color.
     *
     * @param square square index
     * @param color highlight color (alpha respected)
     */
    void highlightSquare(byte square, Color color) {
        if (!isSquareIndex(square) || color == null) {
            return;
        }
        squareHighlights.put(square, color);
        repaint();
    }

    /**
     * Removes any highlight for a square.
     *
     * @param square square index
     */
    void clearSquareHighlight(byte square) {
        if (squareHighlights.remove(square) != null) {
            repaint();
        }
    }

    /**
     * Removes all square and arrow markups. chessboard.js does not have a
     * direct equivalent; mirrors common analysis-board UX.
     */
    void clearMarkup() {
        if (squareHighlights.isEmpty() && boardMarkups.isEmpty()) {
            return;
        }
        squareHighlights.clear();
        boardMarkups.clear();
        clearMarkupGesture();
        repaint();
    }

    /**
     * Adds an arrow markup between two squares.
     *
     * @param from origin square
     * @param to target square
     * @param color stroke color
     */
    void addArrow(byte from, byte to, Color color) {
        if (!isSquareIndex(from) || !isSquareIndex(to) || color == null) {
            return;
        }
        boardMarkups.add(new BoardMarkup(from, to, new MarkupBrush("custom", color, 10)));
        repaint();
    }

    /**
     * Sets the duration of move/snapback/snap/flip animations.
     *
     * @param moveMs move animation duration
     * @param snapbackMs snapback animation duration
     * @param snapMs snap animation duration
     * @param flipMs flip animation duration
     */
    void setAnimationSpeeds(int moveMs, int snapbackMs, int snapMs, int flipMs) {
        moveAnimationMs = Math.max(0, moveMs);
        snapbackAnimationMs = Math.max(0, snapbackMs);
        snapAnimationMs = Math.max(0, snapMs);
        flipAnimationMs = Math.max(0, flipMs);
        animationsEnabled = moveAnimationMs > 0
                || snapbackAnimationMs > 0
                || snapAnimationMs > 0
                || flipAnimationMs > 0;
        if (!animationsEnabled) {
            clearAllAnimations();
        }
    }

    /**
     * Enables or disables board animations without changing their configured speeds.
     *
     * @param enabled true to animate board transitions
     */
    void setAnimationsEnabled(boolean enabled) {
        if (enabled == animationsEnabled) {
            return;
        }
        animationsEnabled = enabled;
        if (!enabled) {
            clearAllAnimations();
        }
        repaint();
    }

    /**
     * Returns whether board animations are enabled.
     *
     * @return true when animations are enabled
     */
    boolean isAnimationsEnabled() {
        return animationsEnabled;
    }

    /**
     * Sets a drag-start filter; called before each drag begins.
     *
     * @param filter predicate; null clears the filter
     */
    void setDragStartFilter(Predicate<DragContext> filter) {
        dragStartFilter = filter;
    }

    /**
     * Sets a drop resolver; called when the user drops a piece.
     *
     * @param resolver resolver; null clears the resolver
     */
    void setDropResolver(ToIntFunction<DropContext> resolver) {
        dropResolver = resolver;
    }

    /**
     * Sets an observer notified when the position changes (old, new FEN).
     *
     * @param observer observer; null clears it
     */
    void setChangeObserver(BiConsumer<String, String> observer) {
        changeObserver = observer;
    }

    /**
     * Sets a callback fired when a snapback animation completes.
     *
     * @param observer callback; null clears it
     */
    void setSnapbackEndObserver(Runnable observer) {
        snapbackEndObserver = observer;
    }

    /**
     * Sets a callback fired when a snap animation completes.
     *
     * @param observer callback; null clears it
     */
    void setSnapEndObserver(Runnable observer) {
        snapEndObserver = observer;
    }

    /**
     * Sets a callback fired when the pointer enters a different square.
     *
     * @param observer callback receiving the square index, or
     *        {@link Field#NO_SQUARE} on exit; null clears it
     */
    void setMouseoverSquareObserver(IntConsumer observer) {
        mouseoverSquareObserver = observer;
    }

    /**
     * Internal {@link #setPosition} extension that fires the change observer.
     *
     * @param next next position
     * @param move last move
     * @param animate whether to animate
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
            clearSelection();
            clearDragState();
            clearMoveAnimation();
            repaint();
        }
        String newFen = next == null ? null : next.toString();
        if (changeObserver != null && !Objects.equals(oldFen, newFen)) {
            changeObserver.accept(oldFen, newFen);
        }
    }


    /**
     * Sets the move handler.
     *
     * @param handler move handler
     */
    void setMoveHandler(MoveHandler handler) {
        moveHandler = handler;
    }

    /**
     * Paints the board shell, coordinates, highlights, pieces, and suggested move.
     *
     * @param graphics graphics context
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

    /**
     * Draws everything clipped to the square chessboard.js board surface.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawBoardContent(Graphics2D g, Rectangle board) {
        Graphics2D copy = (Graphics2D) g.create();
        try {
            copy.clip(board);
            drawBoardTexture(copy, board);
            drawSquareHighlights(copy, board);
            drawMoveHighlights(copy, board);
            drawSelection(copy, board);
            drawCheckHighlight(copy, board);
            drawPieces(copy, board);
            drawAnimatedCapture(copy, board);
            drawAnimatedMove(copy, board);
            drawSnapAnimation(copy, board);
            drawSuggestedMove(copy, board);
            drawBoardMarkups(copy, board);
            drawCoordinates(copy, board);
            drawFlipOverlay(copy, board);
            if (promotionOverlay != null) {
                promotionOverlay.paint(copy, board);
            }
        } finally {
            copy.dispose();
        }
    }

    /**
     * Paints a soft veil over the board during the orientation flip so the
     * change feels animated rather than instantaneous.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawFlipOverlay(Graphics2D g, Rectangle board) {
        if (Double.isNaN(flipAnimationProgress)) {
            return;
        }
        // Triangular ramp: alpha peaks at progress 0.5.
        float alpha = (float) (Math.sin(Math.PI * flipAnimationProgress) * 0.55);
        if (alpha <= 0f) {
            return;
        }
        java.awt.Composite saved = g.getComposite();
        Color savedColor = g.getColor();
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
            g.setColor(WorkbenchTheme.BG);
            g.fillRect(board.x, board.y, board.width, board.height);
        } finally {
            g.setComposite(saved);
            g.setColor(savedColor);
        }
    }

    /**
     * Paints user-supplied square highlights.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawSquareHighlights(Graphics2D g, Rectangle board) {
        if (squareHighlights.isEmpty()) {
            return;
        }
        for (Map.Entry<Byte, Color> entry : squareHighlights.entrySet()) {
            Rectangle bounds = squareBounds(board, entry.getKey());
            drawSquareHighlight(g, bounds, entry.getValue());
        }
    }

    /**
     * Paints Lichess-style user annotations on top of pieces.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawBoardMarkups(Graphics2D g, Rectangle board) {
        if (boardMarkups.isEmpty() && currentMarkup == null) {
            return;
        }
        int pendingEraseIndex = pendingEraseMarkupIndex();
        for (int i = 0; i < boardMarkups.size(); i++) {
            double opacity = i == pendingEraseIndex ? MARKUP_PENDING_ERASE_OPACITY : 1.0;
            drawBoardMarkup(g, board, boardMarkups.get(i), opacity);
        }
        if (currentMarkup != null && pendingEraseIndex < 0) {
            drawBoardMarkup(g, board, currentMarkup, MARKUP_CURRENT_OPACITY);
        }
    }

    /**
     * Paints one user annotation.
     *
     * @param g graphics
     * @param board board bounds
     * @param markup markup shape
     * @param opacity additional opacity multiplier
     */
    private void drawBoardMarkup(Graphics2D g, Rectangle board, BoardMarkup markup, double opacity) {
        Color savedColor = g.getColor();
        try {
            g.setColor(markupColor(markup.brush().color(), opacity));
            if (markup.isCircle()) {
                drawMarkupCircle(g, squareBounds(board, markup.from()), markup.brush());
            } else {
                drawMarkupArrow(g, board, markup);
            }
        } finally {
            g.setColor(savedColor);
        }
    }

    /**
     * Paints a Lichess-style square circle.
     *
     * @param g graphics
     * @param bounds square bounds
     * @param brush annotation brush
     */
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

    /**
     * Paints a Lichess-style user arrow.
     *
     * @param g graphics
     * @param board board bounds
     * @param markup arrow markup
     */
    private void drawMarkupArrow(Graphics2D g, Rectangle board, BoardMarkup markup) {
        int cell = Math.max(1, board.width / 8);
        float lineWidth = Math.max(5f, cell * markup.brush().lineWidth() / 64f);
        drawArrow(g, center(board, markup.from()), center(board, markup.to()), lineWidth, lineWidth);
    }

    /**
     * Returns the visible annotation color for one opacity multiplier.
     *
     * @param color base color
     * @param opacity opacity multiplier
     * @return derived color
     */
    private static Color markupColor(Color color, double opacity) {
        int alpha = (int) Math.round(color.getAlpha() * Math.max(0.0, Math.min(1.0, opacity)));
        return WorkbenchTheme.withAlpha(color, alpha);
    }

    /**
     * Returns the mark that would be toggled off by the active drawing preview.
     *
     * @return index, or -1 when nothing is pending erase
     */
    private int pendingEraseMarkupIndex() {
        if (currentMarkup == null) {
            return -1;
        }
        for (int i = 0; i < boardMarkups.size(); i++) {
            BoardMarkup existing = boardMarkups.get(i);
            if (sameMarkupEndpoints(existing, currentMarkup) && sameMarkupBrush(existing, currentMarkup)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Paints the snap/snapback animation ghost piece.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawSnapAnimation(Graphics2D g, Rectangle board) {
        SnapAnimation snap = snapAnimation;
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

    /**
     * Draws the chessboard.js board border.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawShell(Graphics2D g, Rectangle board) {
        g.setColor(WorkbenchTheme.BOARD_EDGE);
        g.fillRect(board.x - BOARD_BORDER, board.y - BOARD_BORDER,
                board.width + BOARD_BORDER * 2, board.height + BOARD_BORDER * 2);
    }

    /**
     * Draws the cached chessboard.js board texture.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawBoardTexture(Graphics2D g, Rectangle board) {
        g.drawImage(boardTexture(board.width), board.x, board.y, null);
    }

    /**
     * Renders board squares into a texture image.
     *
     * @param size board size
     * @return board texture
     */
    private static BufferedImage renderBoardTexture(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            int cell = size / 8;
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    graphics.setColor(squareColor(row, col));
                    graphics.fillRect(col * cell, row * cell, cell, cell);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Returns the cached board texture for a board size.
     *
     * @param size board size
     * @return board texture
     */
    private BufferedImage boardTexture(int size) {
        if (boardTextureCache == null || boardTextureCacheSize != size) {
            boardTextureCache = renderBoardTexture(size);
            boardTextureCacheSize = size;
        }
        return boardTextureCache;
    }

    /**
     * Draws coordinates.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawCoordinates(Graphics2D g, Rectangle board) {
        if (!showNotation) {
            return;
        }
        int cell = board.width / 8;
        int fileInlinePad = 3;
        int fileBlockPad = 1;
        int rankInlinePad = 2;
        int rankBlockPad = 2;
        int bottomRank = whiteDown ? 1 : 8;
        int leftFile = whiteDown ? 0 : 7;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i < 8; i++) {
            int file = whiteDown ? i : 7 - i;
            int rank = whiteDown ? 8 - i : i + 1;
            String fileText = String.valueOf((char) ('a' + file));
            String rankText = String.valueOf(rank);
            g.setColor(notationColor(file, bottomRank));
            g.drawString(fileText, board.x + i * cell + cell - metrics.stringWidth(fileText) - fileInlinePad,
                    board.y + board.height - fileBlockPad - metrics.getDescent());
            g.setColor(notationColor(leftFile, rank));
            g.drawString(rankText, board.x + rankInlinePad,
                    board.y + i * cell + rankBlockPad + metrics.getAscent());
        }
    }

    /**
     * Draws last-move highlights.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawMoveHighlights(Graphics2D g, Rectangle board) {
        if (!showLastMoveHighlight || lastMove == Move.NO_MOVE
                || (showSuggestedMoveArrow && suggestedMove != Move.NO_MOVE)) {
            return;
        }
        drawSquareHighlight(g, squareBounds(board, Move.getFromIndex(lastMove)), WorkbenchTheme.LAST_MOVE_EDGE);
        drawSquareHighlight(g, squareBounds(board, Move.getToIndex(lastMove)), WorkbenchTheme.LAST_MOVE_EDGE);
    }

    /**
     * Draws a chessboard.js inset square highlight.
     *
     * @param g graphics
     * @param bounds square bounds
     * @param edge edge color
     */
    private static void drawSquareHighlight(Graphics2D g, Rectangle bounds, Color edge) {
        Stroke savedStroke = g.getStroke();
        Color savedColor = g.getColor();
        try {
            g.setColor(edge);
            g.setStroke(new BasicStroke(3f));
            for (int inset = 1; inset <= 3; inset++) {
                g.drawRect(bounds.x + inset, bounds.y + inset,
                        bounds.width - inset * 2 - 1, bounds.height - inset * 2 - 1);
            }
        } finally {
            g.setStroke(savedStroke);
            g.setColor(savedColor);
        }
    }

    /**
     * Draws the selected square and legal destinations.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawSelection(Graphics2D g, Rectangle board) {
        if (position == null || selectedSquare == Field.NO_SQUARE) {
            return;
        }
        Rectangle selected = squareBounds(board, selectedSquare);
        drawSquareHighlight(g, selected, WorkbenchTheme.SELECTED_EDGE);
        if (showLegalMovePreview) {
            drawLegalTargets(g, board);
            drawDropTarget(g, board);
        }
    }

    /**
     * Draws the checked-king marker.
     *
     * @param g graphics
     * @param board board bounds
     */
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
            g.setColor(WorkbenchTheme.CHECK_FILL);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            float radius = bounds.width * 0.74f;
            Point2D center = new Point2D.Double(bounds.getCenterX(), bounds.y + bounds.height * 0.52);
            Color core = WorkbenchTheme.CHECK_CORE;
            Color glow = WorkbenchTheme.CHECK_GLOW;
            g.setPaint(new RadialGradientPaint(center, radius,
                    CHECK_GRADIENT_FRACTIONS,
                    new Color[] { core, core, glow, CHECK_GLOW_FADE, CHECK_GLOW_FADE }));
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            g.setPaint(savedPaint);
            g.setColor(WorkbenchTheme.CHECK_EDGE);
            g.setStroke(STROKE_1);
            g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        } finally {
            g.setPaint(savedPaint);
            g.setStroke(savedStroke);
            g.setColor(savedColor);
        }
    }

    /**
     * Cached radial-gradient stops for the check highlight.
     */
    private static final float[] CHECK_GRADIENT_FRACTIONS = { 0.0f, 0.24f, 0.42f, 0.74f, 1.0f };

    /**
     * Returns the checked side-to-move king square.
     *
     * @return king square or no square
     */
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

    /**
     * Draws a chessboard.js legal move marker.
     *
     * @param g graphics
     * @param bounds square bounds
     * @param capture whether the target contains a capturable piece
     */
    private static void drawLegalTarget(Graphics2D g, Rectangle bounds, boolean capture) {
        Stroke savedStroke = g.getStroke();
        Color savedColor = g.getColor();
        try {
            int centerX = bounds.x + bounds.width / 2;
            int centerY = bounds.y + bounds.height / 2;
            if (capture) {
                int diameter = Math.max(22, Math.round(bounds.width * 0.64f));
                int strokeWidth = Math.max(2, Math.round(bounds.width * 0.035f));
                g.setColor(WorkbenchTheme.LEGAL_CAPTURE_EDGE);
                g.setStroke(new BasicStroke(strokeWidth));
                g.drawOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);
            } else {
                int diameter = Math.max(8, Math.round(bounds.width * 0.20f));
                g.setColor(WorkbenchTheme.LEGAL_TARGET);
                g.fillOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);
            }
        } finally {
            g.setStroke(savedStroke);
            g.setColor(savedColor);
        }
    }

    /**
     * Draws selected-piece legal destinations.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawLegalTargets(Graphics2D g, Rectangle board) {
        for (byte target : selectedLegalTargets) {
            if (target != selectedSquare) {
                drawLegalTarget(g, squareBounds(board, target), isCaptureTarget(target));
            }
        }
    }

    /**
     * Draws all pieces.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawPieces(Graphics2D g, Rectangle board) {
        if (position == null) {
            return;
        }
        byte[] pieces = position.getBoard();
        int cell = board.width / 8;
        for (byte square = 0; square < 64; square++) {
            byte piece = pieces[square];
            if (piece != Piece.EMPTY) {
                if (draggingPiece && square == dragSquare) {
                    continue;
                }
                if (moveAnimationActive && square == animatedMoveTo) {
                    continue;
                }
                if (moveAnimationActive && square == animatedSecondaryMoveTo) {
                    continue;
                }
                if (square == snapHiddenSquare) {
                    continue;
                }
                drawPiece(g, board, cell, square, piece);
            }
        }
    }

    /**
     * Draws one piece glyph.
     *
     * @param g graphics
     * @param board board bounds
     * @param cell square size
     * @param square square
     * @param piece piece
     */
    private void drawPiece(Graphics2D g, Rectangle board, int cell, byte square, byte piece) {
        Rectangle bounds = squareBounds(board, square);
        drawPieceAt(g, cell, bounds.x, bounds.y, piece);
    }

    /**
     * Draws one piece glyph at a board-relative pixel position.
     *
     * @param g graphics
     * @param cell square size
     * @param x x coordinate
     * @param y y coordinate
     * @param piece piece
     */
    private void drawPieceAt(Graphics2D g, int cell, int x, int y, byte piece) {
        BufferedImage image = pieceImage(piece, cell);
        if (image != null) {
            g.drawImage(image, x, y, null);
        }
    }

    /**
     * Draws the suggested move arrow.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawSuggestedMove(Graphics2D g, Rectangle board) {
        if (!showSuggestedMoveArrow || suggestedMove == Move.NO_MOVE || !legalSuggestedMove(suggestedMove)) {
            return;
        }
        Color savedColor = g.getColor();
        try {
            g.setColor(ARROW_FILL);
            drawArrow(g, center(board, Move.getFromIndex(suggestedMove)), center(board, Move.getToIndex(suggestedMove)));
        } finally {
            g.setColor(savedColor);
        }
    }

    /**
     * Draws the moving piece during a played-move animation.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawAnimatedMove(Graphics2D g, Rectangle board) {
        if (!moveAnimationActive) {
            return;
        }
        drawAnimatedPiece(g, board, animatedMovePiece, animatedMoveFrom, animatedMoveTo);
        drawAnimatedPiece(g, board, animatedSecondaryMovePiece, animatedSecondaryMoveFrom, animatedSecondaryMoveTo);
    }

    /**
     * Draws one moving piece during a played-move animation.
     *
     * @param g graphics
     * @param board board bounds
     * @param piece piece
     * @param from origin square
     * @param to destination square
     */
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

    /**
     * Draws a fading captured piece during a played-move animation.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawAnimatedCapture(Graphics2D g, Rectangle board) {
        if (!moveAnimationActive || animatedCapturePiece == Piece.EMPTY || animatedCaptureSquare == Field.NO_SQUARE) {
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

    /**
     * Draws the active drop target while dragging a piece.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawDropTarget(Graphics2D g, Rectangle board) {
        if (!draggingPiece) {
            return;
        }
        if (dragTargetSquare != Field.NO_SQUARE) {
            Rectangle bounds = squareBounds(board, dragTargetSquare);
            drawLegalTarget(g, bounds, isCaptureTarget(dragTargetSquare));
            return;
        }
    }

    /**
     * Draws the dragged piece at the current pointer position.
     *
     * @param g graphics
     * @param board board bounds
     */
    private void drawDraggedPiece(Graphics2D g, Rectangle board) {
        if (!draggingPiece || draggedPiece == Piece.EMPTY) {
            return;
        }
        int cell = board.width / 8;
        int scaledCell = (int) Math.round(cell * DRAG_SCALE);
        int offset = (scaledCell - cell) / 2;
        int pieceX = dragX - cell / 2 - offset;
        int pieceY = dragY - cell / 2 - offset;

        java.awt.Composite savedComposite = g.getComposite();
        Color savedColor = g.getColor();
        try {
            g.setComposite(DRAG_ALPHA);
            BufferedImage scaled = scaledDragImage(draggedPiece, scaledCell);
            g.drawImage(scaled, pieceX, pieceY, null);
        } finally {
            g.setComposite(savedComposite);
            g.setColor(savedColor);
        }
    }

    /**
     * Scale factor applied to a piece while it is being dragged so it lifts
     * visibly over the board.
     */
    private static final double DRAG_SCALE = 1.0;

    /**
     * Cached scaled bitmap for the currently dragged piece; one entry only,
     * keyed by piece + cell, so dragging does not invalidate the main cache.
     */
    private byte dragImageCachedPiece = Piece.EMPTY;

    /**
     * Cell size for {@link #dragImageCache}.
     */
    private int dragImageCachedCell = -1;

    /**
     * Cached scaled drag bitmap.
     */
    private BufferedImage dragImageCache;

    /**
     * Returns a scaled drag-piece bitmap from a tiny dedicated cache.
     *
     * @param piece dragged piece
     * @param cell scaled cell size
     * @return rendered bitmap
     */
    private BufferedImage scaledDragImage(byte piece, int cell) {
        if (dragImageCache == null || dragImageCachedPiece != piece || dragImageCachedCell != cell) {
            dragImageCache = renderPieceImage(piece, cell);
            dragImageCachedPiece = piece;
            dragImageCachedCell = cell;
        }
        return dragImageCache;
    }

    /**
     * Handles one mouse press.
     *
     * @param event mouse event
     */
    private void handlePress(MouseEvent event) {
        if (SwingUtilities.isRightMouseButton(event)) {
            handleMarkupPress(event);
            return;
        }
        if (position == null || moveHandler == null) {
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        if (promotionOverlay != null) {
            short chosen = promotionOverlay.hitTest(event.getX(), event.getY(), boardBounds());
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
        if (selectedSquare != Field.NO_SQUARE) {
            short[] candidates = matchingMovesTo(selectedLegalMoves, square);
            if (candidates.length > 0) {
                clearSelection();
                playOrPromote(candidates, event.getX(), event.getY());
                return;
            }
        }
        byte piece = position.getBoard()[square];
        if (piece != Piece.EMPTY && Piece.isWhite(piece) == position.isWhiteToMove()) {
            if (dragStartFilter != null
                    && !dragStartFilter.test(new DragContext(square, piece, position.toString()))) {
                clearSelection();
                repaint();
                return;
            }
            selectSquare(square);
            dragSquare = square;
            draggedPiece = piece;
            dragStartX = event.getX();
            dragStartY = event.getY();
            dragX = dragStartX;
            dragY = dragStartY;
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        } else {
            clearSelection();
        }
        repaint();
    }

    /**
     * Starts a Lichess-style right-button annotation gesture.
     *
     * @param event mouse event
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
        markupOrigin = square;
        markupBrush = markupBrush(event);
        currentMarkup = markupFromPointer(event.getX(), event.getY());
        repaint();
    }

    /**
     * Updates the active annotation preview.
     *
     * @param event mouse event
     */
    private void handleMarkupDrag(MouseEvent event) {
        event.consume();
        currentMarkup = markupFromPointer(event.getX(), event.getY());
        repaint();
    }

    /**
     * Commits or toggles the active annotation.
     *
     * @param event mouse event
     */
    private void handleMarkupRelease(MouseEvent event) {
        event.consume();
        BoardMarkup finished = markupFromPointer(event.getX(), event.getY());
        clearMarkupGesture();
        if (finished != null) {
            toggleMarkup(finished);
        }
        repaint();
    }

    /**
     * Returns the annotation shape described by the active gesture and pointer.
     *
     * @param pointerX pointer x
     * @param pointerY pointer y
     * @return markup shape, or null when the pointer is off-board
     */
    private BoardMarkup markupFromPointer(int pointerX, int pointerY) {
        byte target = squareAt(pointerX, pointerY);
        if (target == Field.NO_SQUARE || markupBrush == null || markupOrigin == Field.NO_SQUARE) {
            return null;
        }
        return new BoardMarkup(markupOrigin, target == markupOrigin ? MARKUP_CIRCLE : target, markupBrush);
    }

    /**
     * Clears the active right-button drawing gesture.
     */
    private void clearMarkupGesture() {
        currentMarkup = null;
        markupOrigin = Field.NO_SQUARE;
        markupBrush = null;
    }

    /**
     * Returns the Lichess/Chessground brush for a mouse event modifier set.
     *
     * @param event mouse event
     * @return brush
     */
    private static MarkupBrush markupBrush(MouseEvent event) {
        boolean modA = event.isShiftDown() || event.isControlDown();
        boolean modB = event.isAltDown() || event.isMetaDown() || event.isAltGraphDown();
        return MARKUP_BRUSHES[(modA ? 1 : 0) + (modB ? 2 : 0)];
    }

    /**
     * Toggles one annotation using Chessground's same-endpoints behavior:
     * same color deletes, different color replaces.
     *
     * @param markup shape to toggle
     */
    private void toggleMarkup(BoardMarkup markup) {
        boolean foundSameEndpoints = false;
        boolean foundSameBrush = false;
        for (int i = boardMarkups.size() - 1; i >= 0; i--) {
            BoardMarkup existing = boardMarkups.get(i);
            if (sameMarkupEndpoints(existing, markup)) {
                foundSameEndpoints = true;
                foundSameBrush |= sameMarkupBrush(existing, markup);
                boardMarkups.remove(i);
            }
        }
        if (!foundSameEndpoints || !foundSameBrush) {
            boardMarkups.add(markup);
        }
    }

    /**
     * Returns whether two annotations share the same shape endpoints.
     *
     * @param first first shape
     * @param second second shape
     * @return true when endpoints match
     */
    private static boolean sameMarkupEndpoints(BoardMarkup first, BoardMarkup second) {
        return first.from() == second.from() && first.to() == second.to();
    }

    /**
     * Returns whether two annotations use the same brush color.
     *
     * @param first first shape
     * @param second second shape
     * @return true when brush colors match
     */
    private static boolean sameMarkupBrush(BoardMarkup first, BoardMarkup second) {
        return first.brush().color().getRGB() == second.brush().color().getRGB();
    }

    /**
     * Returns the subset of legal moves that target a square.
     *
     * @param moves legal moves originating at one square
     * @param to target square
     * @return matching moves
     */
    private static short[] matchingMovesTo(short[] moves, byte to) {
        int count = 0;
        short[] buffer = new short[moves.length];
        for (short move : moves) {
            if (Move.getToIndex(move) == to) {
                buffer[count++] = move;
            }
        }
        return Arrays.copyOf(buffer, count);
    }

    /**
     * Plays the unique candidate, or shows a promotion picker when multiple
     * promotion options are legal so the user can choose under-promotion.
     *
     * @param candidates candidate moves sharing the same (from, to)
     * @param popupX picker x coordinate
     * @param popupY picker y coordinate
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
     * Active promotion picker overlay; null when no picker is visible.
     */
    private PromotionOverlay promotionOverlay;

    /**
     * Shows the in-board promotion picker for the supplied candidates.
     * Pieces are stacked vertically on the destination file in the order
     * Queen, Rook, Bishop, Knight to match chess.com's layout.
     *
     * @param candidates promotion candidates (all share the same to-square)
     */
    private void showPromotionOverlay(short[] candidates) {
        cancelPromotionOverlay();
        byte target = Move.getToIndex(candidates[0]);
        promotionOverlay = new PromotionOverlay(target, candidates);
        repaint();
    }

    /**
     * Cancels any visible promotion overlay.
     */
    private void cancelPromotionOverlay() {
        if (promotionOverlay != null) {
            promotionOverlay = null;
            repaint();
        }
    }

    /**
     * Promotion overlay state.
     */
    private final class PromotionOverlay {

        /** Target square (rank-1 for white, rank-8 for black). */
        private final byte target;

        /** Candidate moves in canonical order (queen, rook, bishop, knight). */
        private final short[] sorted;

        PromotionOverlay(byte target, short[] candidates) {
            this.target = target;
            this.sorted = sortPromotions(candidates);
        }

        /**
         * Sorts candidates as Queen, Rook, Bishop, Knight.
         */
        private static short[] sortPromotions(short[] in) {
            short queen = Move.NO_MOVE;
            short rook = Move.NO_MOVE;
            short bishop = Move.NO_MOVE;
            short knight = Move.NO_MOVE;
            for (short m : in) {
                switch (Move.getPromotion(m)) {
                    case 4 -> queen = m;
                    case 3 -> rook = m;
                    case 2 -> bishop = m;
                    case 1 -> knight = m;
                    default -> { /* ignored */ }
                }
            }
            short[] out = new short[4];
            int n = 0;
            if (queen != Move.NO_MOVE) out[n++] = queen;
            if (rook != Move.NO_MOVE) out[n++] = rook;
            if (bishop != Move.NO_MOVE) out[n++] = bishop;
            if (knight != Move.NO_MOVE) out[n++] = knight;
            return Arrays.copyOf(out, n);
        }

        /**
         * Returns the bounds for one option index, stacked vertically toward
         * the visual center of the board so the overlay does not run off-edge.
         */
        Rectangle optionBounds(int optionIndex, Rectangle board) {
            Rectangle squareRect = squareBounds(board, target);
            int cell = squareRect.width;
            int x = squareRect.x;
            int direction = squareRect.y < board.y + board.height / 2 ? 1 : -1;
            int y = squareRect.y + cell * direction * optionIndex;
            return new Rectangle(x, y, cell, cell);
        }

        /**
         * Resolves a click coordinate to the chosen move, or
         * {@link Move#NO_MOVE} if the click missed the overlay.
         *
         * @param clickX click x
         * @param clickY click y
         * @param board board bounds
         * @return chosen move or NO_MOVE
         */
        short hitTest(int clickX, int clickY, Rectangle board) {
            for (int i = 0; i < sorted.length; i++) {
                if (optionBounds(i, board).contains(clickX, clickY)) {
                    return sorted[i];
                }
            }
            return Move.NO_MOVE;
        }

        /**
         * Paints the overlay.
         *
         * @param g graphics
         * @param board board bounds
         */
        void paint(Graphics2D g, Rectangle board) {
            // Dim the rest of the board so the picker chips are unambiguous.
            Color savedScrimColor = g.getColor();
            try {
                g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.BG, 140));
                g.fillRect(board.x, board.y, board.width, board.height);
            } finally {
                g.setColor(savedScrimColor);
            }
            for (int i = 0; i < sorted.length; i++) {
                Rectangle bounds = optionBounds(i, board);
                Color savedColor = g.getColor();
                java.awt.Composite savedComposite = g.getComposite();
                try {
                    g.setColor(WorkbenchTheme.PANEL_SOLID);
                    g.fillRoundRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4, 8, 8);
                    g.setColor(WorkbenchTheme.LINE);
                    g.drawRoundRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4, 8, 8);
                    short move = sorted[i];
                    byte piece = pieceForPromotion(Move.getPromotion(move));
                    int cell = bounds.width;
                    drawPieceAt(g, cell, bounds.x, bounds.y, piece);
                } finally {
                    g.setColor(savedColor);
                    g.setComposite(savedComposite);
                }
            }
        }

        /**
         * Maps the encoded promotion code to the actual side-to-move piece
         * (positive = white, negative = black).
         *
         * @param code 1..4
         * @return signed piece code
         */
        private byte pieceForPromotion(int code) {
            int sign = position != null && position.isWhiteToMove() ? 1 : -1;
            return (byte) (sign * switch (code) {
                case 4 -> Piece.WHITE_QUEEN;
                case 3 -> Piece.WHITE_ROOK;
                case 2 -> Piece.WHITE_BISHOP;
                case 1 -> Piece.WHITE_KNIGHT;
                default -> Piece.WHITE_QUEEN;
            });
        }
    }

    /**
     * Handles pointer movement during a piece drag.
     *
     * @param event mouse event
     */
    private void handleDrag(MouseEvent event) {
        if (markupOrigin != Field.NO_SQUARE) {
            handleMarkupDrag(event);
            return;
        }
        if (dragSquare == Field.NO_SQUARE || draggedPiece == Piece.EMPTY) {
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        boolean wasDragging = draggingPiece;
        Rectangle board = boardBounds();
        Rectangle oldDirty = dragRepaintBounds(board, dragTargetSquare, dragHoverSquare, dragX, dragY, wasDragging);
        dragX = event.getX();
        dragY = event.getY();
        if (!draggingPiece && isDragPastThreshold(dragStartX, dragStartY, dragX, dragY)) {
            draggingPiece = true;
        }
        if (draggingPiece) {
            byte hovered = squareAt(dragX, dragY);
            dragHoverSquare = hovered;
            dragTargetSquare = legalDropTarget(hovered);
        } else {
            dragTargetSquare = Field.NO_SQUARE;
            dragHoverSquare = Field.NO_SQUARE;
        }
        if (wasDragging || draggingPiece) {
            repaintDirty(union(oldDirty,
                    dragRepaintBounds(board, dragTargetSquare, dragHoverSquare, dragX, dragY, draggingPiece)));
        }
    }

    /**
     * Handles pointer release after a possible drag.
     *
     * @param event mouse event
     */
    private void handleRelease(MouseEvent event) {
        if (markupOrigin != Field.NO_SQUARE) {
            handleMarkupRelease(event);
            return;
        }
        if (dragSquare == Field.NO_SQUARE) {
            updateCursor(event);
            return;
        }
        if (!draggingPiece) {
            clearDragState();
            updateCursor(event);
            return;
        }
        byte from = dragSquare;
        byte piece = draggedPiece;
        int releaseX = event.getX();
        int releaseY = event.getY();
        byte target = squareAt(releaseX, releaseY);
        short[] candidates = target == Field.NO_SQUARE ? new short[0] : matchingMovesTo(legalMovesFrom(from), target);
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
            suppressNextMoveAnimation = true;
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
        }
        repaint();
    }

    /**
     * Returns true if the candidate set contains more than one promotion choice.
     *
     * @param candidates candidate moves
     * @return true when a promotion picker is needed
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
     * Begins a snap or snapback animation for the dragged piece.
     *
     * @param piece dragged piece
     * @param fromX pointer x at release
     * @param fromY pointer y at release
     * @param landingSquare destination square (origin square for snapback)
     * @param snapback true for snapback (illegal drop)
     */
    private void startSnapAnimation(byte piece, int fromX, int fromY, byte landingSquare, boolean snapback) {
        if (!isSquareIndex(landingSquare)) {
            return;
        }
        if (!animationsEnabled) {
            return;
        }
        Rectangle bounds = squareBounds(boardBounds(), landingSquare);
        int cell = bounds.width;
        int duration = snapback ? snapbackAnimationMs : snapAnimationMs;
        if (duration <= 0 || cell <= 0) {
            return;
        }
        snapAnimation = new SnapAnimation(piece, fromX - cell / 2, fromY - cell / 2, bounds.x, bounds.y, duration,
                snapback);
        snapHiddenSquare = landingSquare;
        startAnimation();
    }

    /**
     * Returns the drop target only when it is legal for the current drag origin.
     *
     * @param square candidate square
     * @return legal square or no square
     */
    private byte legalDropTarget(byte square) {
        if (square == Field.NO_SQUARE || dragSquare == Field.NO_SQUARE) {
            return Field.NO_SQUARE;
        }
        return findLegalMove(dragSquare, square) == Move.NO_MOVE ? Field.NO_SQUARE : square;
    }

    /**
     * Returns whether a target square contains an opponent piece.
     *
     * @param square target square
     * @return true when a legal marker should be a capture ring
     */
    private boolean isCaptureTarget(byte square) {
        if (position == null || !isSquareIndex(square)) {
            return false;
        }
        byte piece = position.getBoard()[square];
        return piece != Piece.EMPTY && Piece.isWhite(piece) != position.isWhiteToMove();
    }

    /**
     * Clears drag-only state.
     */
    private void clearDragState() {
        dragSquare = Field.NO_SQUARE;
        dragTargetSquare = Field.NO_SQUARE;
        dragHoverSquare = Field.NO_SQUARE;
        draggedPiece = Piece.EMPTY;
        draggingPiece = false;
    }

    /**
     * Clears selected-square move caches.
     */
    private void clearSelection() {
        selectedSquare = Field.NO_SQUARE;
        selectedLegalMoves = new short[0];
        selectedLegalTargets = new byte[0];
    }

    /**
     * Selects one square and caches its legal moves.
     *
     * @param square selected square
     */
    private void selectSquare(byte square) {
        selectedSquare = square;
        refreshSelectedLegalMoves();
    }

    /**
     * Updates the cursor based on the square below the pointer.
     *
     * @param event mouse event
     */
    private void updateCursor(MouseEvent event) {
        if (position == null) {
            setCursor(Cursor.getDefaultCursor());
            notifyMouseover(Field.NO_SQUARE);
            return;
        }
        byte square = squareAt(event.getX(), event.getY());
        byte piece = square == Field.NO_SQUARE ? Piece.EMPTY : position.getBoard()[square];
        boolean draggable = piece != Piece.EMPTY && Piece.isWhite(piece) == position.isWhiteToMove();
        setCursor(draggable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        notifyMouseover(square);
    }

    /**
     * Notifies the mouseover observer when the pointer enters a new square.
     *
     * @param square hovered square
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
     * Finds a legal move between two squares.
     *
     * @param from origin square
     * @param to target square
     * @return legal move or {@link Move#NO_MOVE}
     */
    private short findLegalMove(byte from, byte to) {
        if (selectedSquare == from) {
            return findLegalMove(selectedLegalMoves, to);
        }
        return findLegalMove(legalMovesFrom(from), to);
    }

    /**
     * Finds a legal move to one target in a cached move array.
     *
     * @param moves legal moves from one origin
     * @param to target square
     * @return legal move or {@link Move#NO_MOVE}
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
     * Returns legal moves from one square.
     *
     * @param from origin square
     * @return compact move array
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
     * Refreshes selected-square legal move and target caches.
     */
    private void refreshSelectedLegalMoves() {
        if (position == null || selectedSquare == Field.NO_SQUARE) {
            clearSelection();
            return;
        }
        MoveList moves = currentLegalMoves();
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
        selectedLegalMoves = Arrays.copyOf(moveBuffer, moveCount);
        selectedLegalTargets = Arrays.copyOf(targetBuffer, targetCount);
    }

    /**
     * Returns whether a target is already present in a compact target buffer.
     *
     * @param targets target buffer
     * @param count populated target count
     * @param target target square
     * @return true when present
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
     * Returns board bounds.
     *
     * @return board rectangle
     */
    private Rectangle boardBounds() {
        int size = Math.min(getWidth() - BOARD_MARGIN * 2, getHeight() - BOARD_MARGIN * 2);
        size = Math.max(MIN_BOARD_SIZE, size - size % 8);
        return new Rectangle((getWidth() - size) / 2, (getHeight() - size) / 2, size, size);
    }

    /**
     * Returns square bounds.
     *
     * @param board board bounds
     * @param square square
     * @return square bounds
     */
    private Rectangle squareBounds(Rectangle board, byte square) {
        int cell = board.width / 8;
        int file = Field.getX(square);
        int rankRow = square / 8;
        int col = whiteDown ? file : 7 - file;
        int row = whiteDown ? rankRow : 7 - rankRow;
        return new Rectangle(board.x + col * cell, board.y + row * cell, cell, cell);
    }

    /**
     * Returns a scaled cached image for one chess piece.
     *
     * @param piece piece code
     * @param cell board cell size
     * @return cached image or null for empty/unknown pieces
     */
    private BufferedImage pieceImage(byte piece, int cell) {
        int index = pieceCacheIndex(piece);
        if (cell <= 0 || index < 0) {
            return null;
        }
        if (pieceImageCacheCell != cell) {
            clearPieceImageCache(cell);
        }
        BufferedImage cached = pieceImageCache[index];
        if (cached == null) {
            cached = renderPieceImage(piece, cell);
            pieceImageCache[index] = cached;
        }
        return cached;
    }

    /**
     * Clears scaled piece images for a new board cell size.
     *
     * @param cell new cell size
     */
    private void clearPieceImageCache(int cell) {
        Arrays.fill(pieceImageCache, null);
        pieceImageCacheCell = cell;
    }

    /**
     * Renders one embedded SVG piece into an exact-size bitmap.
     *
     * @param piece piece code
     * @param cell output image size
     * @return rendered image
     */
    private static BufferedImage renderPieceImage(byte piece, int cell) {
        BufferedImage image = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Shapes.drawPiece(piece, graphics, 0, 0, cell, cell);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Maps a signed piece code to an image-cache index.
     *
     * @param piece piece code
     * @return cache index or -1 when not cacheable
     */
    private static int pieceCacheIndex(byte piece) {
        if (piece == Piece.EMPTY || piece < -PIECE_CACHE_OFFSET || piece > PIECE_CACHE_OFFSET) {
            return -1;
        }
        return piece + PIECE_CACHE_OFFSET;
    }

    /**
     * Returns whether a pointer movement has crossed the drag threshold.
     *
     * @param startX drag start x coordinate
     * @param startY drag start y coordinate
     * @param currentX current x coordinate
     * @param currentY current y coordinate
     * @return true when the drag threshold has been crossed
     */
    private static boolean isDragPastThreshold(int startX, int startY, int currentX, int currentY) {
        long dx = currentX - startX;
        long dy = currentY - startY;
        return dx * dx + dy * dy >= (long) DRAG_THRESHOLD * DRAG_THRESHOLD;
    }

    /**
     * Returns the dirty area needed to repaint one drag frame.
     *
     * @param board board bounds
     * @param targetSquare highlighted drop target
     * @param hoverSquare hovered square
     * @param pointerX pointer x coordinate
     * @param pointerY pointer y coordinate
     * @param includeDraggedPiece whether to include the floating piece bounds
     * @return dirty rectangle or null
     */
    private Rectangle dragRepaintBounds(
            Rectangle board,
            byte targetSquare,
            byte hoverSquare,
            int pointerX,
            int pointerY,
            boolean includeDraggedPiece) {
        Rectangle dirty = null;
        if (isSquareIndex(dragSquare)) {
            dirty = union(dirty, expanded(squareBounds(board, dragSquare), DRAG_REPAINT_PADDING));
        }
        if (isSquareIndex(targetSquare)) {
            dirty = union(dirty, expanded(squareBounds(board, targetSquare), DRAG_REPAINT_PADDING));
        }
        if (isSquareIndex(hoverSquare)) {
            dirty = union(dirty, expanded(squareBounds(board, hoverSquare), DRAG_REPAINT_PADDING));
        }
        if (includeDraggedPiece && draggedPiece != Piece.EMPTY) {
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

    /**
     * Repaints one dirty rectangle.
     *
     * @param dirty dirty rectangle
     */
    private void repaintDirty(Rectangle dirty) {
        if (dirty != null) {
            repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }
    }

    /**
     * Returns a rectangle expanded by a fixed padding.
     *
     * @param bounds source bounds
     * @param padding padding in pixels
     * @return expanded rectangle
     */
    private static Rectangle expanded(Rectangle bounds, int padding) {
        return new Rectangle(bounds.x - padding, bounds.y - padding,
                bounds.width + padding * 2, bounds.height + padding * 2);
    }

    /**
     * Returns the union of two optional rectangles.
     *
     * @param first first rectangle
     * @param second second rectangle
     * @return union rectangle or null
     */
    private static Rectangle union(Rectangle first, Rectangle second) {
        if (first == null) {
            return second == null ? null : new Rectangle(second);
        }
        if (second == null) {
            return new Rectangle(first);
        }
        return first.union(second);
    }

    /**
     * Returns the chessboard.js square color for a board cell.
     *
     * @param row visual row
     * @param col visual column
     * @return square color
     */
    private static Color squareColor(int row, int col) {
        return isLightCell(row, col) ? WorkbenchTheme.BOARD_LIGHT : WorkbenchTheme.BOARD_DARK;
    }

    /**
     * Returns whether a visual board cell is light.
     *
     * @param row visual row
     * @param col visual column
     * @return true for light cells
     */
    private static boolean isLightCell(int row, int col) {
        return ((row + col) & 1) == 0;
    }

    /**
     * Returns the chessboard.js coordinate color for a board square.
     *
     * @param file file index from zero
     * @param rank rank number from one
     * @return notation color
     */
    private static Color notationColor(int file, int rank) {
        return isLightSquare(file, rank) ? WorkbenchTheme.COORD_ON_LIGHT : WorkbenchTheme.COORD_ON_DARK;
    }

    /**
     * Returns whether a real board square is light.
     *
     * @param file file index from zero
     * @param rank rank number from one
     * @return true for light squares
     */
    private static boolean isLightSquare(int file, int rank) {
        return ((file + rank) & 1) == 0;
    }

    /**
     * Returns the square at screen coordinates.
     *
     * @param x x coordinate
     * @param y y coordinate
     * @return square or no square
     */
    private byte squareAt(int x, int y) {
        Rectangle board = boardBounds();
        if (!board.contains(x, y)) {
            return Field.NO_SQUARE;
        }
        int cell = board.width / 8;
        int col = (x - board.x) / cell;
        int row = (y - board.y) / cell;
        int file = whiteDown ? col : 7 - col;
        int rankRow = whiteDown ? row : 7 - row;
        return (byte) (rankRow * 8 + file);
    }

    /**
     * Returns square center.
     *
     * @param board board bounds
     * @param square square
     * @return point
     */
    private Point center(Rectangle board, byte square) {
        Rectangle bounds = squareBounds(board, square);
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    /**
     * Draws an arrow between two points.
     *
     * @param g graphics
     * @param from origin
     * @param to target
     */
    private void drawArrow(Graphics2D g, Point from, Point to) {
        drawArrow(g, from, to, ARROW_STROKE.getLineWidth(), ARROW_SHORTEN);
    }

    /**
     * Draws an arrow from square center to square center.
     *
     * @param g graphics
     * @param from origin
     * @param to target
     * @param lineWidth arrow body width
     * @param shorten target shortening distance
     */
    private void drawArrow(Graphics2D g, Point from, Point to, float lineWidth, double shorten) {
        double distance = from.distance(to);
        if (distance < 2.0) {
            return;
        }
        double angle = Math.atan2(to.y - from.y, to.x - from.x);
        double targetShorten = Math.min(shorten, distance * 0.35);
        double headRadius = Math.min(Math.max(ARROW_HEAD_RADIUS, lineWidth * 2.6), Math.max(5.0, distance * 0.25));
        int x2 = (int) Math.round(to.x - Math.cos(angle) * targetShorten);
        int y2 = (int) Math.round(to.y - Math.sin(angle) * targetShorten);
        Stroke savedStroke = g.getStroke();
        try {
            g.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
            g.drawLine(from.x, from.y, x2, y2);
            arrowHead.reset();
            for (int i = 0; i < 3; i++) {
                double pointAngle = angle + i * (2.0 * Math.PI / 3.0);
                arrowHead.addPoint(
                        (int) Math.round(Math.cos(pointAngle) * headRadius + x2),
                        (int) Math.round(Math.sin(pointAngle) * headRadius + y2));
            }
            g.fillPolygon(arrowHead);
        } finally {
            g.setStroke(savedStroke);
        }
    }

    /**
     * Reusable polygon buffer for the suggested-move arrow head.
     */
    private final Polygon arrowHead = new Polygon();

    /**
     * Starts the animation timer when needed.
     */
    private void startAnimation() {
        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
    }

    /**
     * Starts a played-move animation when the previous and current boards allow it.
     *
     * @param oldBoard board before the move
     * @param newBoard board after the move
     * @param move played move
     */
    private void startMoveAnimation(byte[] oldBoard, byte[] newBoard, short move) {
        if (!animationsEnabled || moveAnimationMs <= 0) {
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
        byte piece = newBoard[to] == Piece.EMPTY ? oldBoard[from] : newBoard[to];
        if (piece == Piece.EMPTY) {
            return;
        }
        animatedMovePiece = piece;
        animatedMoveFrom = from;
        animatedMoveTo = to;
        configureAnimatedCapture(oldBoard, newBoard, from, to, piece);
        configureSecondaryMove(oldBoard, newBoard, from, to, piece);
        moveAnimationStartedAt = System.currentTimeMillis();
        moveAnimationActive = true;
        startAnimation();
    }

    /**
     * Captures direct and en-passant victims for the fade-out part of the animation.
     *
     * @param oldBoard board before the move
     * @param newBoard board after the move
     * @param from move origin
     * @param to move target
     * @param movingPiece moving piece
     */
    private void configureAnimatedCapture(byte[] oldBoard, byte[] newBoard, byte from, byte to, byte movingPiece) {
        byte direct = oldBoard[to];
        if (direct != Piece.EMPTY && Piece.isWhite(direct) != Piece.isWhite(movingPiece)) {
            animatedCapturePiece = direct;
            animatedCaptureSquare = to;
            return;
        }
        if (!Piece.isPawn(oldBoard[from]) || Field.getX(from) == Field.getX(to)) {
            return;
        }
        byte candidate = (byte) ((from / 8) * 8 + Field.getX(to));
        if (isSquareIndex(candidate) && oldBoard[candidate] != Piece.EMPTY && newBoard[candidate] == Piece.EMPTY
                && Piece.isWhite(oldBoard[candidate]) != Piece.isWhite(movingPiece)) {
            animatedCapturePiece = oldBoard[candidate];
            animatedCaptureSquare = candidate;
        }
    }

    /**
     * Finds a secondary same-color piece move caused by a compound move.
     *
     * @param oldBoard board before the move
     * @param newBoard board after the move
     * @param from primary move origin
     * @param to primary move target
     * @param movingPiece primary moving piece
     */
    private void configureSecondaryMove(byte[] oldBoard, byte[] newBoard, byte from, byte to, byte movingPiece) {
        if (!Piece.isKing(movingPiece)) {
            return;
        }
        int fileDelta = Field.getX(to) - Field.getX(from);
        if (Math.abs(fileDelta) != 2) {
            return;
        }
        int rank = from / 8;
        int rookFromFile = fileDelta > 0 ? 7 : 0;
        int rookToFile = fileDelta > 0 ? 5 : 3;
        byte rookFrom = (byte) (rank * 8 + rookFromFile);
        byte rookTo = (byte) (rank * 8 + rookToFile);
        if (!isSquareIndex(rookFrom) || !isSquareIndex(rookTo)) {
            return;
        }
        byte rookPiece = oldBoard[rookFrom];
        if (rookPiece == Piece.EMPTY || newBoard[rookTo] != rookPiece) {
            return;
        }
        animatedSecondaryMovePiece = rookPiece;
        animatedSecondaryMoveFrom = rookFrom;
        animatedSecondaryMoveTo = rookTo;
    }

    /**
     * Clears played-move animation state.
     */
    private void clearMoveAnimation() {
        animatedMovePiece = Piece.EMPTY;
        animatedSecondaryMovePiece = Piece.EMPTY;
        animatedCapturePiece = Piece.EMPTY;
        animatedMoveFrom = Field.NO_SQUARE;
        animatedMoveTo = Field.NO_SQUARE;
        animatedSecondaryMoveFrom = Field.NO_SQUARE;
        animatedSecondaryMoveTo = Field.NO_SQUARE;
        animatedCaptureSquare = Field.NO_SQUARE;
        moveAnimationStartedAt = 0L;
        moveAnimationActive = false;
    }

    /**
     * Clears every currently active board animation.
     */
    private void clearAllAnimations() {
        clearMoveAnimation();
        snapAnimation = null;
        snapHiddenSquare = Field.NO_SQUARE;
        flipAnimationStartedAt = 0L;
        flipAnimationProgress = Double.NaN;
        flipPending = false;
        animationTimer.stop();
    }

    /**
     * Advances board animations one frame.
     */
    private void tickAnimation() {
        if (moveAnimationActive && moveAnimationProgress() >= 1.0) {
            clearMoveAnimation();
        }
        if (snapAnimation != null && snapAnimation.progress() >= 1.0) {
            SnapAnimation finished = snapAnimation;
            snapAnimation = null;
            snapHiddenSquare = Field.NO_SQUARE;
            if (finished.snapback) {
                if (snapbackEndObserver != null) {
                    snapbackEndObserver.run();
                }
            } else if (snapEndObserver != null) {
                snapEndObserver.run();
            }
        }
        if (!Double.isNaN(flipAnimationProgress)) {
            double elapsed = System.currentTimeMillis() - flipAnimationStartedAt;
            flipAnimationProgress = Math.max(0.0, Math.min(1.0, elapsed / Math.max(1.0, flipAnimationMs)));
            if (flipPending && flipAnimationProgress >= 0.5) {
                whiteDown = !whiteDown;
                flipPending = false;
            }
            if (flipAnimationProgress >= 1.0) {
                flipAnimationProgress = Double.NaN;
                flipPending = false;
            }
        }
        if (!hasActiveAnimation()) {
            animationTimer.stop();
        }
        repaint();
    }

    /**
     * Returns whether any board animation is still active.
     *
     * @return true when the board should keep repainting
     */
    private boolean hasActiveAnimation() {
        return moveAnimationActive || snapAnimation != null || !Double.isNaN(flipAnimationProgress);
    }

    /**
     * Returns played-move animation progress.
     *
     * @return progress from zero to one
     */
    private double moveAnimationProgress() {
        if (!moveAnimationActive || moveAnimationStartedAt == 0L) {
            return 1.0;
        }
        double elapsed = System.currentTimeMillis() - moveAnimationStartedAt;
        return Math.max(0.0, Math.min(1.0, elapsed / Math.max(1.0, moveAnimationMs)));
    }

    /**
     * Cubic ease-out matching the short board transition feel.
     *
     * @param value linear progress
     * @return eased progress
     */
    static double easeOutCubic(double value) {
        return WorkbenchUi.easeOutCubic(value);
    }

    /**
     * Returns whether a byte is a valid board square index.
     *
     * @param square square
     * @return true for 0..63
     */
    private static boolean isSquareIndex(byte square) {
        return square >= 0 && square < 64;
    }

    /**
     * Returns the system DPI scale factor used for sizing heuristics.
     *
     * @return scale factor; 1.0 on standard displays, &gt;1 on HiDPI
     */
    private static float displayScale() {
        try {
            java.awt.GraphicsEnvironment env = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
            java.awt.GraphicsDevice device = env.getDefaultScreenDevice();
            return (float) device.getDefaultConfiguration().getDefaultTransform().getScaleX();
        } catch (java.awt.HeadlessException ex) {
            return 1f;
        }
    }

    /**
     * Move handler callback.
     */
    @FunctionalInterface
    interface MoveHandler {

        /**
         * Plays a move.
         *
         * @param move encoded move
         */
        void play(short move);
    }

    /**
     * Annotation brush.
     *
     * @param name brush name
     * @param color brush color
     * @param lineWidth Chessground-style line width
     */
    record MarkupBrush(String name, Color color, int lineWidth) { }

    /**
     * One arrow or circle markup overlay.
     *
     * @param from origin square
     * @param to target square, or {@link #MARKUP_CIRCLE} for a circle
     * @param brush annotation brush
     */
    record BoardMarkup(byte from, byte to, MarkupBrush brush) {

        /**
         * Returns whether this annotation is a circle marker.
         *
         * @return true for circles
         */
        boolean isCircle() {
            return to == MARKUP_CIRCLE;
        }
    }

    /**
     * Context handed to a drag-start filter.
     *
     * @param square origin square
     * @param piece piece on the origin square
     * @param fen current FEN
     */
    record DragContext(byte square, byte piece, String fen) { }

    /**
     * Context handed to a drop resolver.
     *
     * @param fromSquare origin square
     * @param toSquare drop square ({@link Field#NO_SQUARE} when off-board)
     * @param piece piece being dropped
     * @param fen FEN before the drop
     * @param defaultMove first matching legal move, or {@link Move#NO_MOVE}
     */
    record DropContext(byte fromSquare, byte toSquare, byte piece, String fen, short defaultMove) { }

    /**
     * Snapback/snap animation runtime state.
     */
    private static final class SnapAnimation {
        final byte piece;
        final int startX;
        final int startY;
        final int endX;
        final int endY;
        final long startedAt;
        final int durationMs;
        final boolean snapback;

        SnapAnimation(byte piece, int startX, int startY, int endX, int endY, int durationMs, boolean snapback) {
            this.piece = piece;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.durationMs = Math.max(1, durationMs);
            this.startedAt = System.currentTimeMillis();
            this.snapback = snapback;
        }

        double progress() {
            return Math.max(0.0, Math.min(1.0, (System.currentTimeMillis() - startedAt) / (double) durationMs));
        }
    }
}
