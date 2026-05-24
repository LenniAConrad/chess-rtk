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

/** Native Java2D chess board used by the CRTK Workbench. */
public final class BoardPanel extends JPanel {
    /** Serialization identifier for Swing panel compatibility. */
    private static final long serialVersionUID = 1L;
    /** Board margin in pixels. */
    private static final int BOARD_MARGIN = 32;
    /** Minimum board size in pixels. */
    private static final int MIN_BOARD_SIZE = 64;
    /** Width of the engine eval bar when one is attached. */
    private static final int EVAL_BAR_WIDTH = 22;
    /** Gap between the eval bar and the board square. */
    private static final int EVAL_BAR_GAP = 8;
    /** Engine evaluation bar painted flush against the board, or null when no bar is attached. */
    private transient EvalBar evalBar;
    /** Minimum pointer movement before a press becomes a drag. */
    private static final int DRAG_THRESHOLD = 5;
    /** Piece glide duration for short board transitions. */
    private static final int MOVE_ANIMATION_MS = 95;
    /** Board animation timer interval. */
    private static final int ANIMATION_DELAY_MS = 16;
    /** Extra pixels included around drag dirty rectangles. */
    private static final int DRAG_REPAINT_PADDING = 8;
    /** Minimum interval between drag repaint submissions. */
    private static final long DRAG_REPAINT_FRAME_NANOS = 16_000_000L;
    /** Cached 1-pixel stroke for board hairlines. */
    private static final BasicStroke STROKE_1 = new BasicStroke(1f);
    /** Cached drag transparency composite. */
    private static final AlphaComposite DRAG_ALPHA = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f);
    /** Sentinel destination for a circle annotation. */
    private static final byte MARKUP_CIRCLE = Field.NO_SQUARE;
    /** Active-drawing opacity matching Chessground current shape rendering. */
    private static final double MARKUP_CURRENT_OPACITY = 0.9;
    /** Pending-erase opacity matching Chessground shape toggling feedback. */
    private static final double MARKUP_PENDING_ERASE_OPACITY = 0.6;
    /** Current position. */
    private Position position;
    /** Board orientation. */
    private boolean whiteDown = true;
    /** Last played move. */
    private short lastMove = Move.NO_MOVE;
    /** Suggested move highlighted by command output. */
    private short suggestedMove = Move.NO_MOVE;
    /** Selected square. */
    private byte selectedSquare = Field.NO_SQUARE;
    /** Legal moves from the selected square. */
    private short[] selectedLegalMoves = new short[0];
    /** Unique legal targets from the selected square. */
    private byte[] selectedLegalTargets = new byte[0];
    /** Square currently being dragged. */
    private byte dragSquare = Field.NO_SQUARE;
    /** Current legal drop target under the pointer. */
    private byte dragTargetSquare = Field.NO_SQUARE;
    /** Current pointer-hovered square during a drag, regardless of legality. */
    private byte dragHoverSquare = Field.NO_SQUARE;
    /** Piece currently being dragged. */
    private byte draggedPiece = Piece.EMPTY;
    /** Pointer x coordinate at drag start. */
    private int dragStartX;
    /** Pointer y coordinate at drag start. */
    private int dragStartY;
    /** Current drag pointer x coordinate. */
    private int dragX;
    /** Current drag pointer y coordinate. */
    private int dragY;
    /** Whether the current press has crossed the drag threshold. */
    private boolean draggingPiece;
    /** Dirty region accumulated while drag repainting is throttled. */
    private Rectangle pendingDragDirty;
    /** Last timestamp when a drag repaint was submitted. */
    private long lastDragRepaintNanos;
    /** Piece currently gliding after a played move. */
    private byte animatedMovePiece = Piece.EMPTY;
    /** Secondary piece gliding after a compound move such as castling. */
    private byte animatedSecondaryMovePiece = Piece.EMPTY;
    /** Captured piece currently fading out. */
    private byte animatedCapturePiece = Piece.EMPTY;
    /** Origin square for the gliding piece. */
    private byte animatedMoveFrom = Field.NO_SQUARE;
    /** Destination square for the gliding piece. */
    private byte animatedMoveTo = Field.NO_SQUARE;
    /** Origin square for a secondary gliding piece. */
    private byte animatedSecondaryMoveFrom = Field.NO_SQUARE;
    /** Destination square for a secondary gliding piece. */
    private byte animatedSecondaryMoveTo = Field.NO_SQUARE;
    /** Square where a captured piece fades out. */
    private byte animatedCaptureSquare = Field.NO_SQUARE;
    /** Move-animation start time. */
    private long moveAnimationStartedAt;
    /** Whether a move animation is currently active. */
    private boolean moveAnimationActive;
    /** Move handler. */
    private MoveHandler moveHandler;
    /** Timer driving subtle board animations. */
    private final Timer animationTimer;
    /** Single-shot timer that coalesces high-frequency drag repaint requests. */
    private final Timer dragRepaintTimer;
    /** Bitmap cache for board texture and piece images. */
    private final BoardImageCache imageCache = new BoardImageCache();
    /** Painter for suggested and user-drawn arrows. */
    private final BoardArrowPainter arrowPainter = new BoardArrowPainter();
    /** Cached legal moves for the current position (lazy, invalidated on every #setPosition). */
    private MoveList cachedLegalMoves;
    /** Whether file/rank coordinates are painted on the board. */
    private boolean showNotation = true;
    /** Whether selected-piece legal destinations and drag targets are painted. */
    private boolean showLegalMovePreview = true;
    /** Whether the previous move should be highlighted. */
    private boolean showLastMoveHighlight = true;
    /** Whether engine suggested moves should be painted as arrows. */
    private boolean showSuggestedMoveArrow = true;
    /** Whether board animations should run. */
    private boolean animationsEnabled = true;
    /** User-supplied square highlights (overlaid on top of the board texture). */
    private final Map<Byte, Color> squareHighlights = new LinkedHashMap<>();
    /** User-supplied Lichess-style arrow and circle markups. */
    private final List<BoardMarkup> boardMarkups = new ArrayList<>();
    /** Active right-button drawing preview. */
    private BoardMarkup currentMarkup;
    /** Origin square of the active right-button drawing gesture. */
    private byte markupOrigin = Field.NO_SQUARE;
    /** Brush of the active right-button drawing gesture. */
    private MarkupBrush markupBrush;
    /** Optional drag-start veto. When set, a drag is allowed only if the predicate returns true. Mirrors chessboard.js onDragStart. */
    private Predicate<DragContext> dragStartFilter;
    /** Optional drop callback. Receives drop context and returns the move to play, or Move.NO_MOVE for snapback. Mirrors onDrop. */
    private ToIntFunction<DropContext> dropResolver;
    /** Optional position-change observer (old FEN, new FEN). */
    private BiConsumer<String, String> changeObserver;
    /** Optional snapback-completion observer. */
    private Runnable snapbackEndObserver;
    /** Optional snap-completion observer. */
    private Runnable snapEndObserver;
    /** Whether illegal drag snapbacks should play the generic board cue. */
    private boolean snapbackSoundEnabled = true;
    /** Optional mouseover-square observer. */
    private IntConsumer mouseoverSquareObserver;
    /** Last square reported to #mouseoverSquareObserver, or Field#NO_SQUARE. */
    private byte lastMouseoverSquare = Field.NO_SQUARE;
    /** Active snapback/snap animation state. */
    private SnapAnimation snapAnimation;
    /** Square whose static piece should not be painted while a snap or move animation is making the piece visually appear there. */
    private byte snapHiddenSquare = Field.NO_SQUARE;
    /** When true, the next #setPosition call will skip starting a move animation. */
    private boolean suppressNextMoveAnimation;
    /** Active orientation-flip animation progress (0..1), or NaN when idle. */
    private double flipAnimationProgress = Double.NaN;
    /** Flip animation start time. */
    private long flipAnimationStartedAt;
    /** Move-animation duration (chessboard.js moveSpeed equivalent). */
    private int moveAnimationMs = MOVE_ANIMATION_MS;
    /** Snapback duration when an illegal drop returns to its origin. */
    private int snapbackAnimationMs = 90;
    /** Snap duration when a valid drop slides into place. */
    private int snapAnimationMs = 55;
    /** Flip animation duration. */
    private int flipAnimationMs = 140;
    /** Creates the board panel. */
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
            /** Starts board pointer interaction from a mouse press. */
            @Override
            public void mousePressed(MouseEvent event) {
                handlePress(event);
            }
            /** Completes a board pointer interaction. */
            @Override
            public void mouseReleased(MouseEvent event) {
                handleRelease(event);
            }
            /** Updates drag-and-drop piece movement. */
            @Override
            public void mouseDragged(MouseEvent event) {
                handleDrag(event);
            }
            /** Updates the cursor for draggable pieces. */
            @Override
            public void mouseMoved(MouseEvent event) {
                updateCursor(event);
            }
            /** Restores the default cursor after leaving the board. */
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
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
            }
        });
    }
    /** Stops the animation timer when the panel is detached so a recreated panel (theme reload, tab swap) does not leak the previous instance via t... */
    @Override
    public void removeNotify() {
        animationTimer.stop();
        dragRepaintTimer.stop();
        super.removeNotify();
    }
    /** Sets the position.
     * @param value new value
     * @param move move encoded in CRTK move format */
    public void setPosition(Position value, short move) {
        setPosition(value, move, false);
    }
    /** Sets the position and optionally reverses the move glide.
     * @param value new value
     * @param move move encoded in CRTK move format
     * @param reverseMoveAnimation true to glide the move from destination back to origin */
    public void setPosition(Position value, short move, boolean reverseMoveAnimation) {
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
            startMoveAnimation(previousBoard, position == null ? null : position.getBoard(),
                    move, reverseMoveAnimation);
        }
        repaint();
    }
    /** Returns lazily-cached legal moves for the current position.
     * @return cached legal moves, or null when no position is loaded */
    private MoveList currentLegalMoves() {
        if (position == null) {
            return null;
        }
        if (cachedLegalMoves == null) {
            cachedLegalMoves = position.legalMoves();
        }
        return cachedLegalMoves;
    }
    /** Sets board orientation.
     * @param value new value */
    public void setWhiteDown(boolean value) {
        whiteDown = value;
        repaint();
    }
    /** Returns board orientation.
     * @return true when white is rendered at the bottom */
    public boolean isWhiteDown() {
        return whiteDown;
    }
    /** Sets a suggested move.
     * @param move move encoded in CRTK move format */
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
    /** Returns whether a suggested move is legal in the current board state.
     * @param move move encoded in CRTK move format
     * @return true when the suggested move is legal */
    private boolean legalSuggestedMove(short move) {
        if (move == Move.NO_MOVE || position == null) {
            return false;
        }
        MoveList moves = currentLegalMoves();
        return moves != null && moves.contains(move);
    }
    /** Returns the current FEN, or null when no position has been set. chessboard.js position() equivalent.
     * @return current FEN string, or null when no position is loaded */
    public String position() {
        return position == null ? null : position.toString();
    }
    /** Replaces the position from a FEN. chessboard.js position(fen, useAnimation) equivalent.
     * @param fen FEN string
     * @param animate true to animate the position change */
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
    /** Resets to the standard start position. chessboard.js start(). */
    public void start() {
        position("start", true);
    }
    /** Clears the board (no pieces). chessboard.js clear(). */
    public void clear() {
        position("empty", true);
    }
    /** Toggles board orientation with a smooth flip animation. chessboard.js flip(). */
    public void flip() {
        // If a previous flip is still pending its midpoint commit, apply that
        // commit now so a rapid double-flip toggles the orientation twice
        // instead of swallowing the second call.
        if (flipPending) {
            whiteDown = !whiteDown;
            flipPending = false;
        }
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
    /** True between the start of a flip animation and its midpoint, while the orientation should still appear as it did before #flip() was called. */
    private boolean flipPending;
    /** Sets the orientation explicitly.
     * @param value new value */
    public void orientation(String value) {
        boolean nextWhiteDown = "white".equalsIgnoreCase(value);
        if (nextWhiteDown == whiteDown) {
            return;
        }
        flip();
    }
    /** Returns the current orientation as "white" or "black".
     * @return current orientation value */
    public String orientation() {
        return whiteDown ? "white" : "black";
    }
    /** Toggles whether file/rank coordinates are painted. chessboard.js showNotation.
     * @param show true to show the feature */
    public void setShowNotation(boolean show) {
        if (show == showNotation) {
            return;
        }
        showNotation = show;
        repaint();
    }
    /** Returns whether file/rank coordinates are currently painted.
     * @return true when board notation is painted */
    public boolean isShowNotation() {
        return showNotation;
    }
    /** Sets whether selected-piece legal destinations and drag targets are painted.
     * @param show true to show the feature */
    public void setShowLegalMovePreview(boolean show) {
        if (show == showLegalMovePreview) {
            return;
        }
        showLegalMovePreview = show;
        repaint();
    }
    /** Returns whether legal move previews are painted.
     * @return true when legal move previews are painted */
    public boolean isShowLegalMovePreview() {
        return showLegalMovePreview;
    }
    /** Sets whether the previous move should be highlighted.
     * @param show true to show the feature */
    public void setShowLastMoveHighlight(boolean show) {
        if (show == showLastMoveHighlight) {
            return;
        }
        showLastMoveHighlight = show;
        repaint();
    }
    /** Returns whether previous-move highlights are painted.
     * @return true when previous-move highlights are painted */
    public boolean isShowLastMoveHighlight() {
        return showLastMoveHighlight;
    }
    /** Sets whether suggested engine moves should be painted as arrows.
     * @param show true to show the feature */
    public void setShowSuggestedMoveArrow(boolean show) {
        if (show == showSuggestedMoveArrow) {
            return;
        }
        showSuggestedMoveArrow = show;
        repaint();
    }
    /** Returns whether suggested engine arrows are painted.
     * @return true when suggested-move arrows are painted */
    public boolean isShowSuggestedMoveArrow() {
        return showSuggestedMoveArrow;
    }
    /** Highlights one square with an arbitrary color.
     * @param square board square index
     * @param color display color */
    public void highlightSquare(byte square, Color color) {
        if (!isSquareIndex(square) || color == null) {
            return;
        }
        squareHighlights.put(square, color);
        repaint();
    }
    /** Removes any highlight for a square.
     * @param square board square index */
    public void clearSquareHighlight(byte square) {
        if (squareHighlights.remove(square) != null) {
            repaint();
        }
    }
    /** Removes all square and arrow markups. chessboard.js does not have a direct equivalent; mirrors common analysis-board UX. */
    public void clearMarkup() {
        if (squareHighlights.isEmpty() && boardMarkups.isEmpty()) {
            return;
        }
        squareHighlights.clear();
        boardMarkups.clear();
        clearMarkupGesture();
        repaint();
    }
    /** Adds an arrow markup between two squares.
     * @param from origin square
     * @param to destination square
     * @param color display color */
    public void addArrow(byte from, byte to, Color color) {
        if (!isSquareIndex(from) || !isSquareIndex(to) || color == null) {
            return;
        }
        boardMarkups.add(new BoardMarkup(from, to, MarkupBrush.forThemeColor(color)));
        repaint();
    }
    /** Sets the duration of move/snapback/snap/flip animations.
     * @param moveMs move animation duration in milliseconds
     * @param snapbackMs snapback animation duration in milliseconds
     * @param snapMs snap animation duration in milliseconds
     * @param flipMs flip animation duration in milliseconds */
    public void setAnimationSpeeds(int moveMs, int snapbackMs, int snapMs, int flipMs) {
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
    /** Enables or disables board animations without changing their configured speeds.
     * @param enabled true to enable the behavior */
    public void setAnimationsEnabled(boolean enabled) {
        if (enabled == animationsEnabled) {
            return;
        }
        animationsEnabled = enabled;
        if (!enabled) {
            clearAllAnimations();
        }
        repaint();
    }
    /** Returns whether board animations are enabled.
     * @return true when board animations are enabled */
    public boolean isAnimationsEnabled() {
        return animationsEnabled;
    }
    /** Sets a drag-start filter; called before each drag begins.
     * @param filter drag-start filter */
    public void setDragStartFilter(Predicate<DragContext> filter) {
        dragStartFilter = filter;
    }
    /** Sets a drop resolver; called when the user drops a piece.
     * @param resolver drop resolver callback */
    public void setDropResolver(ToIntFunction<DropContext> resolver) {
        dropResolver = resolver;
    }
    /** Sets an observer notified when the position changes (old, new FEN).
     * @param observer callback observer */
    public void setChangeObserver(BiConsumer<String, String> observer) {
        changeObserver = observer;
    }
    /** Sets a callback fired when a snapback animation completes.
     * @param observer callback observer */
    public void setSnapbackEndObserver(Runnable observer) {
        snapbackEndObserver = observer;
    }
    /** Sets a callback fired when a snap animation completes.
     * @param observer callback observer */
    public void setSnapEndObserver(Runnable observer) {
        snapEndObserver = observer;
    }
    /** Sets whether illegal drag snapbacks should play the generic board cue.
     * @param enabled true to play the snapback cue */
    public void setSnapbackSoundEnabled(boolean enabled) {
        snapbackSoundEnabled = enabled;
    }
    /** Sets a callback fired when the pointer enters a different square. Field#NO_SQUARE on exit; null clears it
     * @param observer callback observer */
    public void setMouseoverSquareObserver(IntConsumer observer) {
        mouseoverSquareObserver = observer;
    }
    /** Internal #setPosition extension that fires the change observer.
     * @param next next position
     * @param move move encoded in CRTK move format
     * @param animate true to animate the position change */
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
    /** Sets the move handler.
     * @param handler move handler callback */
    public void setMoveHandler(MoveHandler handler) {
        moveHandler = handler;
    }
    /** Paints the board shell, coordinates, highlights, pieces, and suggested move. */
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
    /** Draws everything clipped to the square chessboard.js board surface.
     * @param g graphics context
     * @param board board drawing bounds */
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
                promotionOverlay.paint(copy, board, whiteDown, this::drawPieceAt);
            }
        } finally {
            copy.dispose();
        }
    }
    /** Paints a soft veil over the board during the orientation flip so the change feels animated rather than instantaneous.
     * @param g graphics context
     * @param board board drawing bounds */
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
            g.setColor(Theme.BG);
            g.fillRect(board.x, board.y, board.width, board.height);
        } finally {
            g.setComposite(saved);
            g.setColor(savedColor);
        }
    }
    /** Paints user-supplied square highlights.
     * @param g graphics context
     * @param board board drawing bounds */
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
    /** Paints Lichess-style user annotations on top of pieces.
     * @param g graphics context
     * @param board board drawing bounds */
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
    /** Paints one user annotation.
     * @param g graphics context
     * @param board board drawing bounds
     * @param markup board markup to draw
     * @param opacity paint opacity from 0.0 to 1.0 */
    private void drawBoardMarkup(Graphics2D g, Rectangle board, BoardMarkup markup, double opacity) {
        Color savedColor = g.getColor();
        try {
            g.setColor(markupColor(markup.brush().themedColor(), opacity));
            if (markup.isCircle()) {
                drawMarkupCircle(g, squareBounds(board, markup.from()), markup.brush());
            } else {
                drawMarkupArrow(g, board, markup);
            }
        } finally {
            g.setColor(savedColor);
        }
    }
    /** Paints a Lichess-style square circle.
     * @param g graphics context
     * @param bounds drawing bounds
     * @param brush annotation brush */
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
    /** Paints a Lichess-style user arrow.
     * @param g graphics context
     * @param board board drawing bounds
     * @param markup board markup to draw */
    private void drawMarkupArrow(Graphics2D g, Rectangle board, BoardMarkup markup) {
        int cell = Math.max(1, board.width / 8);
        float lineWidth = Math.max(5f, cell * markup.brush().lineWidth() / 64f);
        arrowPainter.draw(g, center(board, markup.from()), center(board, markup.to()), lineWidth, lineWidth);
    }
    /** Returns the visible annotation color for one opacity multiplier.
     * @param color display color
     * @param opacity paint opacity from 0.0 to 1.0
     * @return color with opacity applied */
    private static Color markupColor(Color color, double opacity) {
        int alpha = (int) Math.round(color.getAlpha() * Math.max(0.0, Math.min(1.0, opacity)));
        return Theme.withAlpha(color, alpha);
    }
    /** Returns the mark that would be toggled off by the active drawing preview.
     * @return pending erase markup index, or -1 when nothing would be erased */
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
    /** Paints the snap/snapback animation ghost piece.
     * @param g graphics context
     * @param board board drawing bounds */
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
    /** Draws the chessboard.js board border.
     * @param g graphics context
     * @param board board drawing bounds */
    private void drawShell(Graphics2D g, Rectangle board) {
        g.setColor(Theme.BOARD_EDGE);
        g.fillRect(board.x - BoardStyle.BORDER_WIDTH, board.y - BoardStyle.BORDER_WIDTH,
                board.width + BoardStyle.BORDER_WIDTH * 2, board.height + BoardStyle.BORDER_WIDTH * 2);
    }
    /** Draws the cached chessboard.js board texture.
     * @param g graphics context
     * @param board board drawing bounds */
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
    /** Returns the cached board texture for a board size.
     * @param size board image size in pixels
     * @return cached board texture */
    private BufferedImage boardTexture(int size) {
        return imageCache.boardTexture(size);
    }
    /** Draws coordinates.
     * @param g graphics context
     * @param board board drawing bounds */
    private void drawCoordinates(Graphics2D g, Rectangle board) {
        if (!showNotation) {
            return;
        }
        BoardStyle.drawInsideCoordinates(g, board, whiteDown, 14);
    }
    /** Draws last-move highlights.
     * @param g graphics context
     * @param board board drawing bounds */
    private void drawMoveHighlights(Graphics2D g, Rectangle board) {
        // Suppress the last-move highlight only when the suggested-move arrow
        // is actually going to be drawn over the same squares — not merely
        // because a suggested move is set. drawSuggestedMove also requires the
        // suggestion to be legal in the current position.
        boolean arrowVisible = showSuggestedMoveArrow
                && suggestedMove != Move.NO_MOVE
                && legalSuggestedMove(suggestedMove);
        if (!showLastMoveHighlight || lastMove == Move.NO_MOVE || arrowVisible) {
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
    /** Draws the selected square and legal destinations.
     * @param g graphics context
     * @param board board drawing bounds */
    private void drawSelection(Graphics2D g, Rectangle board) {
        if (position == null || selectedSquare == Field.NO_SQUARE) {
            return;
        }
        Rectangle selected = squareBounds(board, selectedSquare);
        Rectangle clip = g.getClipBounds();
        if (intersectsClip(clip, selected)) {
            BoardStyle.drawFilledSquareHighlight(g, selected, Theme.SELECTED_EDGE);
        }
        if (showLegalMovePreview) {
            drawLegalTargets(g, board);
            drawDropTarget(g, board);
        }
    }
    /** Draws the checked-king marker.
     * @param g graphics context
     * @param board board drawing bounds */
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
    /** Cached radial-gradient stops for the check highlight. */
    private static final float[] CHECK_GRADIENT_FRACTIONS = { 0.0f, 0.24f, 0.42f, 0.74f, 1.0f };
    /** Returns the checked side-to-move king square.
     * @return checked king square, or {@link chess.core.Field#NO_SQUARE} when no king is in check */
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
    /** Draws selected-piece legal destinations.
     * @param g graphics context
     * @param board board drawing bounds */
    private void drawLegalTargets(Graphics2D g, Rectangle board) {
        Rectangle clip = g.getClipBounds();
        for (byte target : selectedLegalTargets) {
            if (target != selectedSquare) {
                Rectangle bounds = squareBounds(board, target);
                if (intersectsClip(clip, bounds)) {
                    BoardStyle.drawLegalTarget(g, bounds, isCaptureTarget(target));
                }
            }
        }
    }
    /** Draws all pieces.
     * @param g graphics context
     * @param board board drawing bounds */
    private void drawPieces(Graphics2D g, Rectangle board) {
        if (position == null) {
            return;
        }
        byte[] pieces = position.getBoard();
        int cell = board.width / 8;
        Rectangle clip = g.getClipBounds();
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
                Rectangle bounds = squareBounds(board, square);
                if (intersectsClip(clip, bounds)) {
                    drawPieceAt(g, cell, bounds.x, bounds.y, piece);
                }
            }
        }
    }
    /** Draws one piece glyph at a board-relative pixel position.
     * @param g graphics context
     * @param cell board cell size in pixels
     * @param x x coordinate
     * @param y y coordinate
     * @param piece signed piece code */
    private void drawPieceAt(Graphics2D g, int cell, int x, int y, byte piece) {
        BufferedImage image = pieceImage(piece, cell);
        if (image != null) {
            g.drawImage(image, x, y, null);
        }
    }
    /** Draws the suggested move arrow.
     * @param g graphics context
     * @param board board drawing bounds */
    private void drawSuggestedMove(Graphics2D g, Rectangle board) {
        if (!showSuggestedMoveArrow || suggestedMove == Move.NO_MOVE || !legalSuggestedMove(suggestedMove)) {
            return;
        }
        arrowPainter.drawSuggested(g, center(board, Move.getFromIndex(suggestedMove)),
                center(board, Move.getToIndex(suggestedMove)));
    }
    /** Draws the moving piece during a played-move animation.
     * @param g graphics context
     * @param board board drawing bounds */
    private void drawAnimatedMove(Graphics2D g, Rectangle board) {
        if (!moveAnimationActive) {
            return;
        }
        drawAnimatedPiece(g, board, animatedMovePiece, animatedMoveFrom, animatedMoveTo);
        drawAnimatedPiece(g, board, animatedSecondaryMovePiece, animatedSecondaryMoveFrom, animatedSecondaryMoveTo);
    }
    /** Draws one moving piece during a played-move animation.
     * @param g graphics context
     * @param board board drawing bounds
     * @param piece signed piece code
     * @param from origin square
     * @param to destination square */
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
    /** Draws a fading captured piece during a played-move animation.
     * @param g graphics context
     * @param board board drawing bounds */
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
    /** Draws the active drop target while dragging a piece.
     * @param g graphics context
     * @param board board drawing bounds */
    private void drawDropTarget(Graphics2D g, Rectangle board) {
        if (!draggingPiece) {
            return;
        }
        if (dragTargetSquare != Field.NO_SQUARE) {
            Rectangle bounds = squareBounds(board, dragTargetSquare);
            if (intersectsClip(g.getClipBounds(), bounds)) {
                BoardStyle.drawLegalTarget(g, bounds, isCaptureTarget(dragTargetSquare));
            }
        }
    }
    /** Draws the dragged piece at the current pointer position.
     * @param g graphics context
     * @param board board drawing bounds */
    private void drawDraggedPiece(Graphics2D g, Rectangle board) {
        if (!draggingPiece || draggedPiece == Piece.EMPTY) {
            return;
        }
        int cell = board.width / 8;
        int scaledCell = (int) Math.round(cell * DRAG_SCALE);
        int offset = (scaledCell - cell) / 2;
        int pieceX = dragX - cell / 2 - offset;
        int pieceY = dragY - cell / 2 - offset;
        Rectangle bounds = new Rectangle(pieceX, pieceY, scaledCell, scaledCell);
        if (!intersectsClip(g.getClipBounds(), bounds)) {
            return;
        }
        java.awt.Composite savedComposite = g.getComposite();
        Color savedColor = g.getColor();
        try {
            g.setComposite(DRAG_ALPHA);
            BufferedImage scaled = imageCache.dragPieceImage(draggedPiece, scaledCell);
            g.drawImage(scaled, pieceX, pieceY, null);
        } finally {
            g.setComposite(savedComposite);
            g.setColor(savedColor);
        }
    }
    /** Scale factor applied to a piece while it is being dragged so it lifts visibly over the board. */
    private static final double DRAG_SCALE = 1.0;
    /** Pre-renders the dragged piece bitmap before high-frequency drag events start.
     * @param piece signed piece code */
    private void warmDragPieceImage(byte piece) {
        Rectangle board = boardBounds();
        int cell = board.width / 8;
        int scaledCell = (int) Math.round(cell * DRAG_SCALE);
        if (scaledCell > 0) {
            imageCache.dragPieceImage(piece, scaledCell);
        }
    }
    /** Handles one mouse press.
     * @param event mouse event */
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
            warmDragPieceImage(piece);
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        } else {
            clearSelection();
        }
        repaint();
    }
    /** Starts a Lichess-style right-button annotation gesture.
     * @param event mouse event */
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
    /** Updates the active annotation preview.
     * @param event mouse event */
    private void handleMarkupDrag(MouseEvent event) {
        event.consume();
        currentMarkup = markupFromPointer(event.getX(), event.getY());
        repaint();
    }
    /** Commits or toggles the active annotation.
     * @param event mouse event */
    private void handleMarkupRelease(MouseEvent event) {
        event.consume();
        BoardMarkup finished = markupFromPointer(event.getX(), event.getY());
        clearMarkupGesture();
        if (finished != null) {
            toggleMarkup(finished);
        }
        repaint();
    }
    /** Returns the annotation shape described by the active gesture and pointer.
     * @param pointerX pointer x coordinate
     * @param pointerY pointer y coordinate
     * @return markup described by the active pointer gesture */
    private BoardMarkup markupFromPointer(int pointerX, int pointerY) {
        byte target = squareAt(pointerX, pointerY);
        if (target == Field.NO_SQUARE || markupBrush == null || markupOrigin == Field.NO_SQUARE) {
            return null;
        }
    return new BoardMarkup(markupOrigin, target == markupOrigin ? MARKUP_CIRCLE : target, markupBrush);
    }
    /** Clears the active right-button drawing gesture. */
    private void clearMarkupGesture() {
        currentMarkup = null;
        markupOrigin = Field.NO_SQUARE;
        markupBrush = null;
    }
    /** Returns the Lichess/Chessground brush for a mouse event modifier set.
     * @param event mouse event
     * @return annotation brush for the mouse modifiers */
    private static MarkupBrush markupBrush(MouseEvent event) {
        boolean modA = event.isShiftDown() || event.isControlDown();
        boolean modB = event.isAltDown() || event.isMetaDown() || event.isAltGraphDown();
        return MarkupBrush.forGesture((modA ? 1 : 0) + (modB ? 2 : 0));
    }
    /** Toggles one annotation using Chessground's same-endpoints behavior: same color deletes, different color replaces.
     * @param markup board markup to draw */
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
    /** Returns whether two annotations share the same shape endpoints.
     * @param first first value to compare
     * @param second second value to compare
     * @return true when both annotations share endpoints */
    private static boolean sameMarkupEndpoints(BoardMarkup first, BoardMarkup second) {
        return first.from() == second.from() && first.to() == second.to();
    }
    /** Returns whether two annotations use the same brush color.
     * @param first first value to compare
     * @param second second value to compare
     * @return true when both annotations use the same brush color */
    private static boolean sameMarkupBrush(BoardMarkup first, BoardMarkup second) {
        return first.brush().matches(second.brush());
    }
    /** Returns the subset of legal moves that target a square.
     * @param moves candidate moves
     * @param to destination square
     * @return legal moves targeting the supplied square */
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
    /** Plays the unique candidate, or shows a promotion picker when multiple promotion options are legal so the user can choose under-promotion.
     * @param candidates legal candidate moves
     * @param popupX popup x coordinate
     * @param popupY popup y coordinate */
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
    /** Active promotion picker overlay; null when no picker is visible. */
    private PromotionOverlay promotionOverlay;
    /** Shows the in-board promotion picker for the supplied candidates.
     * @param candidates legal candidate moves */
    private void showPromotionOverlay(short[] candidates) {
        cancelPromotionOverlay();
        byte target = Move.getToIndex(candidates[0]);
        promotionOverlay = new PromotionOverlay(target, candidates, () -> position != null && position.isWhiteToMove());
        repaint();
    }
    /** Cancels any visible promotion overlay. */
    private void cancelPromotionOverlay() {
        if (promotionOverlay != null) {
            promotionOverlay = null;
            repaint();
        }
    }
    /** Handles pointer movement during a piece drag.
     * @param event mouse event */
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
        Rectangle oldDirty = dragRepaintBounds(board, dragTargetSquare, dragHoverSquare,
                dragX, dragY, wasDragging, false);
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
            boolean dragStarted = !wasDragging && draggingPiece;
            scheduleDragRepaint(union(oldDirty,
                    dragRepaintBounds(board, dragTargetSquare, dragHoverSquare,
                            dragX, dragY, draggingPiece, dragStarted)));
        }
    }
    /** Handles pointer release after a possible drag.
     * @param event mouse event */
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
            if (snapbackSoundEnabled) {
                SoundService.play(SoundCue.ILLEGAL);
            }
        }
        repaint();
    }
    /** Returns true if the candidate set contains more than one promotion choice.
     * @param candidates legal candidate moves
     * @return true when the candidate list contains more than one promotion piece */
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
    /** Begins a snap or snapback animation for the dragged piece.
     * @param piece signed piece code
     * @param fromX origin x coordinate
     * @param fromY origin y coordinate
     * @param landingSquare target square for the snap animation
     * @param snapback true when the piece should return to its origin */
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
    /** Returns the drop target only when it is legal for the current drag origin.
     * @param square board square index
     * @return legal drop target, or {@link chess.core.Field#NO_SQUARE} when none exists */
    private byte legalDropTarget(byte square) {
        if (square == Field.NO_SQUARE || dragSquare == Field.NO_SQUARE) {
            return Field.NO_SQUARE;
        }
    return findLegalMove(dragSquare, square) == Move.NO_MOVE ? Field.NO_SQUARE : square;
    }
    /** Returns whether a target square contains an opponent piece.
     * @param square board square index
     * @return true when the square is a legal capture target */
    private boolean isCaptureTarget(byte square) {
        if (position == null || !isSquareIndex(square)) {
            return false;
        }
        byte piece = position.getBoard()[square];
        return piece != Piece.EMPTY && Piece.isWhite(piece) != position.isWhiteToMove();
    }
    /** Clears drag-only state. */
    private void clearDragState() {
        cancelPendingDragRepaint();
        dragSquare = Field.NO_SQUARE;
        dragTargetSquare = Field.NO_SQUARE;
        dragHoverSquare = Field.NO_SQUARE;
        draggedPiece = Piece.EMPTY;
        draggingPiece = false;
    }
    /** Clears selected-square move caches. */
    private void clearSelection() {
        selectedSquare = Field.NO_SQUARE;
        selectedLegalMoves = new short[0];
        selectedLegalTargets = new byte[0];
    }
    /** Selects one square and caches its legal moves.
     * @param square board square index */
    private void selectSquare(byte square) {
        selectedSquare = square;
        refreshSelectedLegalMoves();
    }
    /** Updates the cursor based on the square below the pointer.
     * @param event mouse event */
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
    /** Notifies the mouseover observer when the pointer enters a new square.
     * @param square board square index */
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
    /** Finds a legal move between two squares.
     * @param from origin square
     * @param to destination square
     * @return matching legal move, or {@link chess.core.Move#NO_MOVE} when none exists */
    private short findLegalMove(byte from, byte to) {
        if (selectedSquare == from) {
    return findLegalMove(selectedLegalMoves, to);
        }
    return findLegalMove(legalMovesFrom(from), to);
    }
    /** Finds a legal move to one target in a cached move array.
     * @param moves candidate moves
     * @param to destination square
     * @return matching legal move, or {@link chess.core.Move#NO_MOVE} when none exists */
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
    /** Returns legal moves from one square.
     * @param from origin square
     * @return legal moves from the supplied origin square */
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
    /** Refreshes selected-square legal move and target caches. */
    private void refreshSelectedLegalMoves() {
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
        selectedLegalMoves = Arrays.copyOf(moveBuffer, moveCount);
        selectedLegalTargets = Arrays.copyOf(targetBuffer, targetCount);
    }
    /** Returns whether a target is already present in a compact target buffer.
     * @param targets candidate target squares
     * @param count number of populated entries to inspect
     * @param target target square
     * @return true when the target is present in the populated entries */
    private static boolean containsTarget(byte[] targets, int count, byte target) {
        for (int i = 0; i < count; i++) {
            if (targets[i] == target) {
                return true;
            }
        }
        return false;
    }
    /** Returns board bounds.
     * @return current board drawing bounds */
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
    /** Attaches an engine eval bar as a child component, painted flush against the left edge of the board square and matched to its height.
     * @param bar evaluation bar to attach */
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
    /** Positions the attached eval bar flush against the board square. */
    @Override
    public void doLayout() {
        super.doLayout();
        if (evalBar != null) {
            Rectangle square = boardBounds();
            int barX = Math.max(0, square.x - EVAL_BAR_GAP - EVAL_BAR_WIDTH);
            evalBar.setBounds(barX, square.y, EVAL_BAR_WIDTH, square.height);
        }
    }
    /** Returns the rendered chessboard square in this panel's own coordinate space — used by the board stage to align the engine eval bar flush wit...
     * @return current board drawing bounds */
    public Rectangle currentBoardBounds() {
        return boardBounds();
    }
    /** Returns square bounds.
     * @param board board drawing bounds
     * @param square board square index
     * @return bounds for the supplied board square */
    private Rectangle squareBounds(Rectangle board, byte square) {
        return BoardGeometry.squareBounds(board, square, whiteDown);
    }
    /** Returns a scaled cached image for one chess piece.
     * @param piece signed piece code
     * @param cell board cell size in pixels
     * @return computed value */
    private BufferedImage pieceImage(byte piece, int cell) {
        return imageCache.pieceImage(piece, cell);
    }
    /** Returns whether a pointer movement has crossed the drag threshold.
     * @param startX starting pointer x coordinate
     * @param startY starting pointer y coordinate
     * @param currentX current pointer x coordinate
     * @param currentY current pointer y coordinate
     * @return true when the pointer movement exceeds the drag threshold */
    private static boolean isDragPastThreshold(int startX, int startY, int currentX, int currentY) {
        long dx = (long) currentX - startX;
        long dy = (long) currentY - startY;
        return dx * dx + dy * dy >= (long) DRAG_THRESHOLD * DRAG_THRESHOLD;
    }
    /** Returns the dirty area needed to repaint one drag frame.
     * @param board board drawing bounds
     * @param targetSquare target square
     * @param hoverSquare hovered square
     * @param pointerX pointer x coordinate
     * @param pointerY pointer y coordinate
     * @param includeDraggedPiece true to include the dragged piece bounds
     * @param includeOriginSquare true to include the source square whose static
     *     piece has just been hidden
     * @return computed value */
    private Rectangle dragRepaintBounds(
            Rectangle board,
            byte targetSquare,
            byte hoverSquare,
            int pointerX,
            int pointerY,
            boolean includeDraggedPiece,
            boolean includeOriginSquare) {
        Rectangle dirty = null;
        if (includeOriginSquare && isSquareIndex(dragSquare)) {
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
    /** Schedules one drag repaint, coalescing bursts to a display-frame cadence.
     * @param dirty dirty repaint bounds */
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
    /** Submits the accumulated drag dirty rectangle immediately. */
    private void flushPendingDragRepaint() {
        Rectangle dirty = pendingDragDirty;
        pendingDragDirty = null;
        dragRepaintTimer.stop();
        if (dirty != null) {
            lastDragRepaintNanos = System.nanoTime();
            repaintDirty(dirty);
        }
    }
    /** Cancels any delayed drag repaint request. */
    private void cancelPendingDragRepaint() {
        pendingDragDirty = null;
        lastDragRepaintNanos = 0L;
        dragRepaintTimer.stop();
    }
    /** Repaints one dirty rectangle.
     * @param dirty dirty repaint bounds */
    private void repaintDirty(Rectangle dirty) {
        if (dirty != null) {
            repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }
    }
    /** Returns a rectangle expanded by a fixed padding.
     * @param bounds drawing bounds
     * @param padding padding in pixels
     * @return expanded rectangle */
    private static Rectangle expanded(Rectangle bounds, int padding) {
    return new Rectangle(bounds.x - padding, bounds.y - padding,
                bounds.width + padding * 2, bounds.height + padding * 2);
    }
    /** Returns the union of two optional rectangles.
     * @param first first value to compare
     * @param second second value to compare
     * @return union rectangle */
    private static Rectangle union(Rectangle first, Rectangle second) {
        if (first == null) {
            return second == null ? null : new Rectangle(second);
        }
        if (second == null) {
    return new Rectangle(first);
        }
        return first.union(second);
    }
    /** Returns whether a drawing bounds intersects the current clip.
     * @param clip active graphics clip, or null when unclipped
     * @param bounds drawing bounds
     * @return true when the bounds should be painted */
    private static boolean intersectsClip(Rectangle clip, Rectangle bounds) {
        return clip == null || bounds == null || clip.intersects(bounds);
    }
    /** Returns the square at screen coordinates.
     * @param x x coordinate
     * @param y y coordinate
     * @return square at the coordinates, or {@link chess.core.Field#NO_SQUARE} outside the board */
    private byte squareAt(int x, int y) {
        return BoardGeometry.squareAt(boardBounds(), x, y, whiteDown);
    }
    /** Returns square center.
     * @param board board drawing bounds
     * @param square board square index
     * @return computed value */
    private Point center(Rectangle board, byte square) {
        return BoardGeometry.center(board, square, whiteDown);
    }
    /** Starts the animation timer when needed. */
    private void startAnimation() {
        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
    }
    /** Starts a played-move animation when the previous and current boards allow it.
     * @param oldBoard previous board array
     * @param newBoard new board array
     * @param move move encoded in CRTK move format
     * @param reverseMoveAnimation true when replay navigation should animate the move backward */
    private void startMoveAnimation(byte[] oldBoard, byte[] newBoard, short move,
            boolean reverseMoveAnimation) {
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
        animatedMovePiece = piece;
        animatedMoveFrom = from;
        animatedMoveTo = to;
        configureAnimatedCapture(oldBoard, newBoard, from, to, piece);
        configureSecondaryMove(oldBoard, newBoard, moveFrom, moveTo, reverseMoveAnimation);
        moveAnimationStartedAt = System.currentTimeMillis();
        moveAnimationActive = true;
        startAnimation();
    }
    /** Returns the piece to draw for a legal-looking board transition.
     * @param oldBoard previous board array
     * @param newBoard new board array
     * @param from animated origin square
     * @param to animated target square
     * @return piece to draw, or {@link Piece#EMPTY} when the boards do not describe that move */
    private static byte animatedPiece(byte[] oldBoard, byte[] newBoard, byte from, byte to) {
        byte source = oldBoard[from];
        byte target = newBoard[to];
        if (source == Piece.EMPTY || target == Piece.EMPTY || Piece.isWhite(source) != Piece.isWhite(target)) {
            return Piece.EMPTY;
        }
        return target;
    }
    /** Captures direct and en-passant victims for the fade-out part of the animation.
     * @param oldBoard previous board array
     * @param newBoard new board array
     * @param from origin square
     * @param to destination square
     * @param movingPiece piece being animated */
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
    /** Finds a secondary same-color piece move caused by a compound move.
     * @param oldBoard previous board array
     * @param newBoard new board array
     * @param moveFrom original move origin square
     * @param moveTo original move destination square
     * @param reverseMoveAnimation true when the compound move is being undone */
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
        animatedSecondaryMovePiece = rookPiece;
        animatedSecondaryMoveFrom = rookFrom;
        animatedSecondaryMoveTo = rookTo;
    }
    /** Clears played-move animation state. */
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
    /** Clears every currently active board animation. */
    private void clearAllAnimations() {
        clearMoveAnimation();
        snapAnimation = null;
        snapHiddenSquare = Field.NO_SQUARE;
        // Commit a pending flip so disabling animations mid-flight does not
        // silently leave the board in its pre-flip orientation.
        if (flipPending) {
            whiteDown = !whiteDown;
            flipPending = false;
        }
        flipAnimationStartedAt = 0L;
        flipAnimationProgress = Double.NaN;
        animationTimer.stop();
    }
    /** Advances board animations one frame. */
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
            double elapsed = (double) System.currentTimeMillis() - (double) flipAnimationStartedAt;
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
    /** Returns whether any board animation is still active.
     * @return true when any board animation is active */
    private boolean hasActiveAnimation() {
        return moveAnimationActive || snapAnimation != null || !Double.isNaN(flipAnimationProgress);
    }
    /** Returns played-move animation progress.
     * @return move-animation progress from 0.0 to 1.0 */
    private double moveAnimationProgress() {
        if (!moveAnimationActive || moveAnimationStartedAt == 0L) {
            return 1.0;
        }
        double elapsed = (double) System.currentTimeMillis() - (double) moveAnimationStartedAt;
        return Math.max(0.0, Math.min(1.0, elapsed / Math.max(1.0, moveAnimationMs)));
    }
    /** Cubic ease-out matching the short board transition feel.
     * @param value new value
     * @return eased value */
    public static double easeOutCubic(double value) {
        return Ui.easeOutCubic(value);
    }
    /** Returns whether a byte is a valid board square index.
     * @param square board square index
     * @return true when the byte is a valid board square index */
    private static boolean isSquareIndex(byte square) {
        return square >= 0 && square < 64;
    }
    /** Returns the system DPI scale factor used for sizing heuristics.
     * @return system display scale factor */
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
