package testing;

import static testing.WorkbenchTestSupport.*;

import java.awt.Component;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.network.TensorViz;

import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;

/**
 * Board interaction, rendering, and navigation regression checks.
 */
final class WorkbenchBoardRegression {

    /**
     * Prevents instantiation.
     */
    private WorkbenchBoardRegression() {
        // utility
    }

    /**
     * Runs this focused regression group.
     */
    static void run() {
        testBoardHasNoInstructionTooltip();
        testBoardHasNoKeyboardPieceSelector();
        testBoardEditorBuildsAndAppliesFen();
        testWindowPositionNavigationRoutingSkipsTextAndDataControls();
        testWindowPositionNavigationBindsAllArrowFamilies();
        testBoardRightDragTogglesLichessArrowMarkup();
        testBoardRightClickTogglesLichessCircleMarkup();
        testBoardRightDragReplacesMarkupColorLikeLichess();
        testBoardDragEmitsLegalMove();
        testBoardDragInvalidHoverDoesNotPaintRedBox();
        testBoardDragDirtyBoundsIncludesInvalidHoverSquare();
        testBoardDragDirtyBoundsDoNotSpanOriginAfterStart();
        testBoardDragRepaintsCoalesceFastEvents();
        testBoardMoveAnimationStarts();
        testBoardCaptureAnimationStarts();
        testBoardCastlingAnimationStarts();
        testBoardPaintUsesChessboardJsColors();
        testBoardCoordinateLabelsUseOppositeSquareColor();
        testBoardCoordinatesPaintBelowOverlays();
        testBoardSuggestedMoveArrowIsLegalAndClean();
        testBoardSelectionUsesChessboardJsTargetMarkers();
        testBoardLegalMovePreviewCanBeHidden();
        testBoardLastMoveAndBestArrowCanBeHidden();
        testBoardInstantPositionShowsLastMoveWithoutAnimation();
        testBoardNotationAndAnimationsCanBeHidden();
        testBoardReverseMoveAnimationStarts();
        testBoardCheckHighlightPaintsCheckedKingMarker();
        testBoardTextureCachesRenderedLayer();
        testBoardPieceImageCacheReusesScaledSvg();
        testBoardStyleUsesSharedPixelGeometry();
        testTensorMiniBoardPiecesRenderWhiteBottom();
    }

    /**
     * Verifies the chessboard does not show an instructional tooltip over play.
     */
    private static void testBoardHasNoInstructionTooltip() {
        JComponent board = (JComponent) construct(type("BoardPanel"), new Class<?>[0]);
        assertEquals(null, board.getToolTipText(), "board instruction tooltip removed");
    }

    /**
     * Verifies board-local keyboard piece selection is disabled.
     */
    private static void testBoardHasNoKeyboardPieceSelector() {
        JComponent board = (JComponent) construct(type("BoardPanel"), new Class<?>[0]);
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("LEFT")),
                "board left key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("RIGHT")),
                "board right key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("UP")),
                "board up key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("DOWN")),
                "board down key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER")),
                "board enter key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("SPACE")),
                "board space key does not select pieces");
    }

    /**
     * Verifies the setup editor emits legal FEN and applies through its host
     * callback.
     */
    private static void testBoardEditorBuildsAndAppliesFen() {
        String base = "4k3/8/8/8/8/8/8/4K3 w - - 0 1";
        String[] applied = { null };
        Object editor = construct(type("BoardEditorPanel"),
                new Class<?>[] {
                        java.util.function.Supplier.class,
                        java.util.function.Consumer.class,
                        java.util.function.Consumer.class },
                (java.util.function.Supplier<String>) () -> base,
                (java.util.function.Consumer<String>) value -> applied[0] = value,
                (java.util.function.Consumer<String>) value -> {
                    // Clipboard writes are not needed for this regression.
                });

        assertTrue((Boolean) invoke(editor, "loadFen", new Class<?>[] { String.class }, base),
                "board editor loads valid FEN");
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        invoke(editor, "attachBoard", new Class<?>[] { type("BoardPanel") }, board);
        invoke(editor, "setEditingBoardActive", new Class<?>[] { boolean.class }, Boolean.TRUE);
        assertTrue((Boolean) invoke(board, "isSetupEditMode", new Class<?>[0]),
                "board editor activates direct editing on the main board");
        invoke(editor, "setPieceAt", new Class<?>[] { byte.class, byte.class },
                Field.H4, Piece.WHITE_QUEEN);
        assertEquals(Byte.valueOf(Piece.WHITE_QUEEN),
                invoke(editor, "pieceAt", new Class<?>[] { byte.class }, Field.H4),
                "board editor stores placed piece");
        assertEquals(Byte.valueOf(Piece.WHITE_QUEEN),
                invoke(board, "setupEditPieceAt", new Class<?>[] { byte.class }, Field.H4),
                "board editor paints pieces on the main board");
        assertEquals("4k3/8/8/8/7Q/8/8/4K3 w - - 0 1",
                invoke(editor, "fen", new Class<?>[0]), "board editor FEN placement");
        invoke(board, "setSetupEditPieceAt", new Class<?>[] { byte.class, byte.class },
                Field.A8, Piece.BLACK_ROOK);
        assertEquals(Byte.valueOf(Piece.BLACK_ROOK),
                invoke(editor, "pieceAt", new Class<?>[] { byte.class }, Field.A8),
                "main board direct edit updates editor model");

        invoke(editor, "setWhiteToMove", new Class<?>[] { boolean.class }, false);
        assertTrue((Boolean) invoke(editor, "applyEditedFen", new Class<?>[0]),
                "board editor applies legal FEN");
        assertEquals("r3k3/8/8/8/7Q/8/8/4K3 b - - 0 1", applied[0],
                "board editor normalizes applied FEN");
        invoke(editor, "setEditingBoardActive", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isSetupEditMode", new Class<?>[0]),
                "leaving editor disables direct board editing");
        assertFalse(readWorkbenchSource("board/BoardEditorPanel.java").contains("white bottom"),
                "board editor does not show the old orientation label");
        assertFalse(readWorkbenchSource("board/BoardEditorPanel.java").contains("label(\"main board\")"),
                "board editor does not replace orientation text with another board label");
        assertFalse(readWorkbenchSource("window/WindowBoardLayer.java").contains("Theme.section(\"Position\")"),
                "position controls no longer show a redundant position title");
        assertPaintsOpaqueCorner((JComponent) editor, 360, 560, "board editor opaque background");
    }

    /**
     * Reads one workbench source file for source-level UI regression checks.
     *
     * @param relativePath path below the workbench package
     * @return source text
     */
    private static String readWorkbenchSource(String relativePath) {
        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/application/gui/workbench", relativePath),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            throw new AssertionError("unable to read workbench source " + relativePath, ex);
        }
    }

    /**
     * Verifies one source fragment appears before another in a rendering method.
     *
     * @param source source text
     * @param first fragment expected first
     * @param second fragment expected second
     * @param label assertion label
     */
    private static void assertSourceOrder(String source, String first, String second, String label) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0, label + " missing first fragment");
        assertTrue(secondIndex >= 0, label + " missing second fragment");
        assertTrue(firstIndex < secondIndex, label);
    }

    /**
     * Verifies window-level arrow routing does not steal arrows from editors or
     * data controls.
     */
    private static void testWindowPositionNavigationRoutingSkipsTextAndDataControls() {
        assertTrue((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JButton("Run")),
                "button focus may route arrows to positions");
        assertFalse((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JTextField()),
                "text fields keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JTable(1, 1)),
                "tables keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JList<>()),
                "lists keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JComboBox<>()),
                "combo boxes keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("Window"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JSpinner()),
                "spinners keep arrow navigation");
    }

    /**
     * Verifies game-position navigation binds the main arrow cluster, keypad
     * arrows, and numpad direction keys.
     */
    private static void testWindowPositionNavigationBindsAllArrowFamilies() {
        KeyStroke[] keys = (KeyStroke[]) invokeStatic(type("Window"),
                "allPositionNavigationKeyStrokes", new Class<?>[0]);
        assertKeyStrokePresent(keys, KeyEvent.VK_LEFT, "left arrow");
        assertKeyStrokePresent(keys, KeyEvent.VK_RIGHT, "right arrow");
        assertKeyStrokePresent(keys, KeyEvent.VK_UP, "up arrow");
        assertKeyStrokePresent(keys, KeyEvent.VK_DOWN, "down arrow");
        assertKeyStrokePresent(keys, KeyEvent.VK_KP_LEFT, "keypad left arrow");
        assertKeyStrokePresent(keys, KeyEvent.VK_KP_RIGHT, "keypad right arrow");
        assertKeyStrokePresent(keys, KeyEvent.VK_KP_UP, "keypad up arrow");
        assertKeyStrokePresent(keys, KeyEvent.VK_KP_DOWN, "keypad down arrow");
        assertKeyStrokePresent(keys, KeyEvent.VK_NUMPAD4, "numpad 4");
        assertKeyStrokePresent(keys, KeyEvent.VK_NUMPAD6, "numpad 6");
        assertKeyStrokePresent(keys, KeyEvent.VK_NUMPAD8, "numpad 8");
        assertKeyStrokePresent(keys, KeyEvent.VK_NUMPAD2, "numpad 2");
        assertKeyStrokePresent(keys, KeyEvent.VK_HOME, "home");
        assertKeyStrokePresent(keys, KeyEvent.VK_END, "end");
    }

    /**
     * Verifies a key-stroke array contains one unmodified key.
     *
     * @param keys key-stroke array
     * @param keyCode key code
     * @param label assertion label
     */
    private static void assertKeyStrokePresent(KeyStroke[] keys, int keyCode, String label) {
        KeyStroke expected = KeyStroke.getKeyStroke(keyCode, 0);
        for (KeyStroke key : keys) {
            if (expected.equals(key)) {
                return;
            }
        }
        throw new AssertionError(label + ": key stroke not bound");
    }

    /**
     * Verifies right-button drag toggles a Lichess-style arrow.
     */
    private static void testBoardRightDragTogglesLichessArrowMarkup() {
        Component board = (Component) construct(type("BoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        Point from = boardPoint(Field.toIndex('e', '2'), true, 640, 640);
        Point to = boardPoint(Field.toIndex('e', '4'), true, 640, 640);

        drawRightArrow(board, from, to, 0);
        List<?> markups = (List<?>) field(board, "boardMarkups");
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "right drag adds one arrow");
        Object markup = markups.get(0);
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(markup, "from"), "arrow origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')), field(markup, "to"), "arrow target");
        assertEquals("green", field(field(markup, "brush"), "name"), "default arrow brush");

        drawRightArrow(board, from, to, 0);
        assertEquals(Integer.valueOf(0), Integer.valueOf(markups.size()), "same right drag deletes arrow");
    }

    /**
     * Verifies right-clicking one square toggles a Lichess-style circle marker.
     */
    private static void testBoardRightClickTogglesLichessCircleMarkup() {
        Component board = (Component) construct(type("BoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        Point square = boardPoint(Field.toIndex('d', '4'), true, 640, 640);

        rightClick(board, square, 0);
        List<?> markups = (List<?>) field(board, "boardMarkups");
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "right click adds one circle");
        Object markup = markups.get(0);
        assertEquals(Byte.valueOf(Field.toIndex('d', '4')), field(markup, "from"), "circle square");
        assertEquals(Byte.valueOf(Field.NO_SQUARE), field(markup, "to"), "circle has no target");

        rightClick(board, square, 0);
        assertEquals(Integer.valueOf(0), Integer.valueOf(markups.size()), "same right click deletes circle");
    }

    /**
     * Verifies drawing the same endpoints with a modifier replaces the color.
     */
    private static void testBoardRightDragReplacesMarkupColorLikeLichess() {
        Component board = (Component) construct(type("BoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        Point from = boardPoint(Field.toIndex('b', '1'), true, 640, 640);
        Point to = boardPoint(Field.toIndex('c', '3'), true, 640, 640);

        drawRightArrow(board, from, to, 0);
        drawRightArrow(board, from, to, MouseEvent.SHIFT_DOWN_MASK);
        List<?> markups = (List<?>) field(board, "boardMarkups");
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "same endpoints keep one markup");
        assertEquals("red", field(field(markups.get(0), "brush"), "name"),
                "shift right drag replaces with red brush");
    }

    /**
     * Verifies dragging a piece emits the legal move through the board callback.
     */
    private static void testBoardDragEmitsLegalMove() {
        Component board = (Component) construct(type("BoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        List<Short> played = new ArrayList<>();
        invoke(board, "setMoveHandler", new Class<?>[] { type("MoveHandler") },
                moveHandler(played));

        Point from = boardPoint(Field.toIndex('e', '2'), true, 640, 640);
        Point to = boardPoint(Field.toIndex('e', '4'), true, 640, 640);
        long now = System.currentTimeMillis();
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_PRESSED, now, from.x, from.y, 1));
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_DRAGGED, now + 1L, to.x, to.y, 0));
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_RELEASED, now + 2L, to.x, to.y, 1));

        assertEquals(Integer.valueOf(1), Integer.valueOf(played.size()), "dragged move count");
        assertEquals(Move.toString(Move.parse("e2e4")), Move.toString(played.get(0)), "dragged move");
    }

    /**
     * Verifies invalid drag hovers do not paint a red rejection box.
     */
    private static void testBoardDragInvalidHoverDoesNotPaintRedBox() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);

        byte from = Field.toIndex('e', '2');
        byte invalidHover = Field.toIndex('f', '3');
        setField(board, "dragSquare", Byte.valueOf(from));
        setField(board, "draggedPiece", Byte.valueOf(Piece.WHITE_PAWN));
        setField(board, "draggingPiece", Boolean.TRUE);
        setField(board, "dragX", Integer.valueOf(24));
        setField(board, "dragY", Integer.valueOf(24));
        setField(board, "dragHoverSquare", Byte.valueOf(Field.NO_SQUARE));

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        Point hover = boardPoint(invalidHover, true, 640, 640);
        int sampleX = hover.x - cell / 2 + 2;
        int sampleY = hover.y - cell / 2 + 2;
        Color baseline = new Color(paint(component, 640, 640).getRGB(sampleX, sampleY), true);

        setField(board, "dragHoverSquare", Byte.valueOf(invalidHover));
        Color actual = new Color(paint(component, 640, 640).getRGB(sampleX, sampleY), true);

        assertColor(baseline, actual, "invalid drag hover leaves square unboxed");
    }

    /**
     * Verifies drag repaint bounds include an invalid hovered square so a stale
     * hover marker cannot be left behind after pointer movement.
     */
    private static void testBoardDragDirtyBoundsIncludesInvalidHoverSquare() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        byte invalidHover = Field.toIndex('f', '3');
        setField(board, "dragSquare", Byte.valueOf(Field.toIndex('e', '2')));
        setField(board, "draggedPiece", Byte.valueOf(Piece.WHITE_PAWN));

        Rectangle boardBounds = (Rectangle) invoke(board, "boardBounds", new Class<?>[0]);
        Rectangle dirty = (Rectangle) invoke(board, "dragRepaintBounds",
                new Class<?>[] {
                        Rectangle.class,
                        byte.class,
                        byte.class,
                        int.class,
                        int.class,
                        boolean.class,
                        boolean.class },
                boardBounds, Field.NO_SQUARE, invalidHover, 12, 12, Boolean.FALSE, Boolean.FALSE);

        assertTrue(dirty != null && dirty.contains(boardPoint(invalidHover, true, 640, 640)),
                "drag dirty bounds include invalid hover square");
    }

    /**
     * Verifies active drag frames repaint only moving/hover regions instead of
     * spanning from the source square to the pointer on every mouse event.
     */
    private static void testBoardDragDirtyBoundsDoNotSpanOriginAfterStart() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        byte origin = Field.toIndex('a', '2');
        byte target = Field.toIndex('h', '7');
        setField(board, "dragSquare", Byte.valueOf(origin));
        setField(board, "draggedPiece", Byte.valueOf(Piece.WHITE_PAWN));
        Rectangle boardBounds = (Rectangle) invoke(board, "boardBounds", new Class<?>[0]);
        Point pointer = boardPoint(target, true, 640, 640);

        Rectangle firstFrame = (Rectangle) invoke(board, "dragRepaintBounds",
                new Class<?>[] {
                        Rectangle.class,
                        byte.class,
                        byte.class,
                        int.class,
                        int.class,
                        boolean.class,
                        boolean.class },
                boardBounds, Field.NO_SQUARE, target, pointer.x, pointer.y, Boolean.TRUE, Boolean.TRUE);
        Rectangle activeFrame = (Rectangle) invoke(board, "dragRepaintBounds",
                new Class<?>[] {
                        Rectangle.class,
                        byte.class,
                        byte.class,
                        int.class,
                        int.class,
                        boolean.class,
                        boolean.class },
                boardBounds, Field.NO_SQUARE, target, pointer.x, pointer.y, Boolean.TRUE, Boolean.FALSE);

        assertTrue(firstFrame != null && firstFrame.contains(boardPoint(origin, true, 640, 640)),
                "first drag frame repaints the hidden origin square");
        assertTrue(activeFrame != null, "active drag frame exists");
        assertFalse(activeFrame.contains(boardPoint(origin, true, 640, 640)),
                "active drag frame does not keep spanning back to origin");
        assertTrue(activeFrame.width < firstFrame.width || activeFrame.height < firstFrame.height,
                "active drag frame is smaller after origin is already hidden");
    }

    /**
     * Verifies rapid drag events are coalesced into one pending repaint
     * instead of submitting a repaint for every pointer sample.
     */
    private static void testBoardDragRepaintsCoalesceFastEvents() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        setField(board, "lastDragRepaintNanos", Long.valueOf(System.nanoTime()));
        Rectangle first = new Rectangle(10, 12, 22, 24);
        Rectangle second = new Rectangle(80, 90, 16, 18);

        invoke(board, "scheduleDragRepaint", new Class<?>[] { Rectangle.class }, first);
        invoke(board, "scheduleDragRepaint", new Class<?>[] { Rectangle.class }, second);

        Timer timer = (Timer) field(board, "dragRepaintTimer");
        Rectangle pending = (Rectangle) field(board, "pendingDragDirty");
        assertTrue(timer.isRunning(), "drag repaint timer coalesces fast samples");
        assertTrue(pending.contains(first) && pending.contains(second),
                "pending drag repaint is the union of fast samples");

        invoke(board, "flushPendingDragRepaint", new Class<?>[0]);
        assertEquals(null, field(board, "pendingDragDirty"), "flushed drag repaint clears pending region");
        assertFalse(timer.isRunning(), "flushed drag repaint stops timer");
    }

    /**
     * Verifies setting a played move starts the Java2D glide animation.
     */
    private static void testBoardMoveAnimationStarts() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Position start = new Position(START_FEN);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);

        short move = Move.parse("e2e4");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        assertTrue((Boolean) field(board, "moveAnimationActive"), "move animation active");
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(board, "animatedMoveFrom"), "animated origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')), field(board, "animatedMoveTo"), "animated target");
        double eased = (Double) invokeStatic(type("BoardPanel"), "easeOutCubic",
                new Class<?>[] { double.class }, 0.5d);
        assertTrue(eased > 0.5d && eased < 1.0d, "move animation ease-out curve");
    }

    /**
     * Verifies stepping backward glides the moved piece back to its origin.
     */
    private static void testBoardReverseMoveAnimationStarts() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Position start = new Position(START_FEN);
        short move = Move.parse("e2e4");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class, boolean.class },
                start, move, Boolean.TRUE);

        assertTrue((Boolean) field(board, "moveAnimationActive"), "reverse move animation active");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')), field(board, "animatedMoveFrom"),
                "reverse animated origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(board, "animatedMoveTo"),
                "reverse animated target");
    }

    /**
     * Verifies capture moves retain the victim piece for fade-out drawing.
     */
    private static void testBoardCaptureAnimationStarts() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Position start = new Position("8/8/8/3p4/4P3/8/8/4K2k w - - 0 1");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);

        short move = Move.parse("e4d5");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        assertTrue((Boolean) field(board, "moveAnimationActive"), "capture animation active");
        assertEquals(Byte.valueOf(Field.toIndex('d', '5')), field(board, "animatedCaptureSquare"),
                "capture fade square");
        assertFalse(((Byte) field(board, "animatedCapturePiece")).byteValue() == Piece.EMPTY,
                "capture fade piece");
    }

    /**
     * Verifies castling starts a secondary rook glide animation.
     */
    private static void testBoardCastlingAnimationStarts() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Position start = new Position("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);

        short move = Move.parse("e1g1");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        assertEquals(Byte.valueOf(Field.toIndex('h', '1')), field(board, "animatedSecondaryMoveFrom"),
                "castle rook origin");
        assertEquals(Byte.valueOf(Field.toIndex('f', '1')), field(board, "animatedSecondaryMoveTo"),
                "castle rook target");
        assertFalse(((Byte) field(board, "animatedSecondaryMovePiece")).byteValue() == Piece.EMPTY,
                "castle rook animated piece");
    }

    /**
     * Verifies the board paints chessboard.js base colors.
     */
    private static void testBoardPaintUsesChessboardJsColors() {
        Component board = (Component) construct(type("BoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position("8/8/8/8/8/8/8/4K2k w - - 0 1"), Move.NO_MOVE);
        Color moveHighlight = themeColor("BOARD_HIGHLIGHT");
        invoke(board, "highlightSquare", new Class<?>[] { byte.class, Color.class },
                Field.toIndex('e', '4'), moveHighlight);

        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            board.paint(graphics);
        } finally {
            graphics.dispose();
        }

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (640 - size) / 2;
        int boardY = (640 - size) / 2;
        assertColor(new Color(64, 64, 64),
                new Color(image.getRGB(boardX - 1, boardY - 1), true),
                "chessboard.js border");
        assertColor(new Color(240, 217, 181),
                new Color(image.getRGB(boardX + cell / 2, boardY + cell / 2), true),
                "chessboard.js light square");
        assertColor(new Color(181, 136, 99),
                new Color(image.getRGB(boardX + cell + cell / 2, boardY + cell / 2), true),
                "chessboard.js dark square");
        int e4x = boardX + 4 * cell;
        int e4y = boardY + 4 * cell;
        Color e4Center = new Color(image.getRGB(e4x + cell / 2, e4y + cell / 2), true);
        assertTrue(moveHighlight.getAlpha() < 160, "board move highlight is translucent");
        assertColorDistanceAtLeast(e4Center, themeColor("BOARD_LIGHT"), 20.0,
                "highlight tints square center");
        assertColorDistanceAtLeast(new Color(image.getRGB(e4x + 2, e4y + 2), true), themeColor("BOARD_LIGHT"),
                20.0, "filled move highlight tints edge");
    }

    /**
     * Verifies suggested best-move arrows are legal and do not leave square clutter.
     */
    private static void testBoardSuggestedMoveArrowIsLegalAndClean() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        short artificialLastMove = Move.parse("g1f3");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), artificialLastMove);

        invoke(board, "setSuggestedMove", new Class<?>[] { short.class }, Move.parse("e2e5"));
        assertEquals(Short.valueOf(Move.NO_MOVE), field(board, "suggestedMove"),
                "illegal suggested move rejected");

        invoke(board, "selectSquare", new Class<?>[] { byte.class }, Field.toIndex('e', '2'));
        short best = Move.parse("e2e4");
        invoke(board, "setSuggestedMove", new Class<?>[] { short.class }, best);
        assertEquals(Short.valueOf(best), field(board, "suggestedMove"), "legal suggested move accepted");
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(board, "selectedSquare"),
                "best arrow keeps the user's selected square");

        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (640 - size) / 2;
        int boardY = (640 - size) / 2;
        Color arrowBody = new Color(image.getRGB(boardX + 4 * cell + cell / 2, boardY + 5 * cell + cell / 2), true);
        assertTrue(arrowBody.getBlue() > arrowBody.getRed(), "suggested move arrow is blue");
        Color moveHighlight = themeColor("LAST_MOVE_EDGE");
        Color e4Edge = new Color(image.getRGB(boardX + 4 * cell + 2, boardY + 4 * cell + 2), true);
        assertFalse(e4Edge.equals(moveHighlight), "best arrow does not add square highlight");
        Color f3Edge = new Color(image.getRGB(boardX + 5 * cell + 2, boardY + 5 * cell + 2), true);
        assertColorDistanceAtLeast(f3Edge, baseBoardColor(Field.toIndex('f', '3')), 16.0,
                "best arrow keeps last-move highlight visible");
    }

    /**
     * Verifies legal destination previews can be disabled.
     */
    private static void testBoardLegalMovePreviewCanBeHidden() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        invoke(board, "selectSquare", new Class<?>[] { byte.class }, Field.toIndex('e', '2'));
        Color highlight = themeColor("BOARD_HIGHLIGHT");
        Color visibleEdge = boardSquareEdgeColor(component, Field.toIndex('e', '4'));
        assertFalse(visibleEdge.equals(highlight), "legal move preview avoids green square rectangles");
        Color visible = boardSquareCenterColor(component, Field.toIndex('e', '4'));
        assertColorDistanceAtLeast(visible, themeColor("BOARD_LIGHT"), 25.0,
                "legal move preview is visible on light squares");

        invoke(board, "setShowLegalMovePreview", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowLegalMovePreview", new Class<?>[0]),
                "legal move preview disabled");
        Color hidden = boardSquareCenterColor(component, Field.toIndex('e', '4'));
        assertColorDistanceAtLeast(visible, hidden, 10.0, "legal move preview hidden");
    }

    /**
     * Verifies selected, move-target, and capture-target overlays use the
     * shared translucent chessboard.js-style treatment.
     */
    private static void testBoardSelectionUsesChessboardJsTargetMarkers() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1"), Move.NO_MOVE);
        invoke(board, "selectSquare", new Class<?>[] { byte.class }, Field.toIndex('e', '4'));

        assertTrue(themeColor("SELECTED_EDGE").getAlpha() < 160, "selected square highlight is translucent");
        assertColorDistanceAtLeast(boardSquareEdgeColor(component, Field.toIndex('e', '4')),
                baseBoardColor(Field.toIndex('e', '4')), 20.0,
                "selected square is visibly tinted");
        assertColorDistanceAtLeast(boardSquareCenterColor(component, Field.toIndex('e', '5')),
                baseBoardColor(Field.toIndex('e', '5')), 30.0,
                "quiet legal move dot is visible");

        BufferedImage image = paint(component, 640, 640);
        Point captureCenter = boardPoint(Field.toIndex('d', '5'), true, 640, 640);
        int cell = boardCellSize(640, 640);
        Color captureHalo = new Color(image.getRGB(captureCenter.x - cell / 2 + 8, captureCenter.y), true);
        assertColorDistanceAtLeast(captureHalo, baseBoardColor(Field.toIndex('d', '5')), 18.0,
                "capture target halo is visible around the piece");
    }

    /**
     * Verifies last-move highlights and suggested arrows can be disabled.
     */
    private static void testBoardLastMoveAndBestArrowCanBeHidden() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        Position start = new Position(START_FEN);
        short move = Move.parse("e2e4");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start.copy().play(move), move);
        assertColorDistanceAtLeast(boardSquareEdgeColor(component, Field.toIndex('e', '2')),
                baseBoardColor(Field.toIndex('e', '2')), 16.0,
                "last move highlight visible by default");
        assertColorDistanceAtLeast(boardSquareCenterColor(component, Field.toIndex('e', '2')),
                baseBoardColor(Field.toIndex('e', '2')), 16.0,
                "last move highlight fills empty origin square");

        invoke(board, "setShowLastMoveHighlight", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowLastMoveHighlight", new Class<?>[0]),
                "last move highlight disabled");
        assertColor(baseBoardColor(Field.toIndex('e', '2')), boardSquareEdgeColor(component, Field.toIndex('e', '2')),
                "last move highlight hidden");

        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        Point arrowSample = boardPoint(Field.toIndex('e', '3'), true, 640, 640);
        Color baseline = new Color(paint(component, 640, 640).getRGB(arrowSample.x, arrowSample.y), true);
        invoke(board, "setShowSuggestedMoveArrow", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowSuggestedMoveArrow", new Class<?>[0]),
                "best move arrow disabled");
        invoke(board, "setSuggestedMove", new Class<?>[] { short.class }, Move.parse("e2e4"));
        Color hiddenArrow = new Color(paint(component, 640, 640).getRGB(arrowSample.x, arrowSample.y), true);
        assertColor(baseline, hiddenArrow, "best move arrow hidden");
    }

    /**
     * Verifies position jumps can preserve the last-move highlight without
     * starting a misleading move animation from an unrelated previous board.
     */
    private static void testBoardInstantPositionShowsLastMoveWithoutAnimation() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        Position start = new Position(START_FEN);
        short move = Move.parse("e2e4");
        Position after = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);
        invoke(board, "setPositionInstant", new Class<?>[] { Position.class, short.class }, after, move);

        assertEquals(Short.valueOf(move), field(board, "lastMove"),
                "instant position stores the last move for highlighting");
        assertFalse((Boolean) field(board, "moveAnimationActive"),
                "instant position does not start move animation");
        assertColorDistanceAtLeast(boardSquareCenterColor(component, Field.toIndex('e', '2')),
                baseBoardColor(Field.toIndex('e', '2')), 16.0,
                "instant position highlights last-move origin");
        assertColorDistanceAtLeast(boardSquareCenterColor(component, Field.toIndex('e', '4')),
                baseBoardColor(Field.toIndex('e', '4')), 16.0,
                "instant position highlights last-move destination");
    }

    /**
     * Verifies notation and animations can be disabled.
     */
    private static void testBoardNotationAndAnimationsCanBeHidden() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        assertTrue((Boolean) invoke(board, "isShowNotation", new Class<?>[0]), "board notation enabled by default");
        BufferedImage withNotation = paint(component, 640, 640);
        invoke(board, "setShowNotation", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowNotation", new Class<?>[0]), "board notation disabled");
        BufferedImage withoutNotation = paint(component, 640, 640);
        assertTrue(changedPixelCount(withNotation, withoutNotation) > 20,
                "board notation paints file and rank labels");
        assertBoardNotationCornersChange(withNotation, withoutNotation, 640, 640);

        invoke(board, "setAnimationsEnabled", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isAnimationsEnabled", new Class<?>[0]), "board animations disabled");
        Position start = new Position(START_FEN);
        short move = Move.parse("e2e4");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start.copy().play(move), move);
        assertFalse((Boolean) field(board, "moveAnimationActive"), "move animation suppressed");
    }

    /**
     * Verifies board coordinate labels use the opposite square colour, matching
     * the chessboard.js inside-coordinate treatment.
     */
    private static void testBoardCoordinateLabelsUseOppositeSquareColor() {
        Class<?> boardStyle = type("BoardStyle");
        Color lightSquare = themeColor("BOARD_LIGHT");
        Color darkSquare = themeColor("BOARD_DARK");
        Color lightLabel = (Color) invokeStatic(boardStyle, "coordinateTextColor",
                new Class<?>[] { Color.class }, lightSquare);
        Color darkLabel = (Color) invokeStatic(boardStyle, "coordinateTextColor",
                new Class<?>[] { Color.class }, darkSquare);
        assertEquals(themeColor("COORD_ON_LIGHT"), lightLabel,
                "coordinate label on a light square uses dark-square color");
        assertEquals(themeColor("COORD_ON_DARK"), darkLabel,
                "coordinate label on a dark square uses light-square color");
        assertEquals(darkSquare, lightLabel, "light-square coordinate is drawn in board dark");
        assertEquals(lightSquare, darkLabel, "dark-square coordinate is drawn in board light");
    }

    /**
     * Verifies board coordinates are painted just above the board squares so
     * highlights, pieces, arrows, and exported SVG overlays cover the labels.
     */
    private static void testBoardCoordinatesPaintBelowOverlays() {
        String panel = readWorkbenchSource("board/BoardPanel.java");
        assertSourceOrder(panel, "drawBoardTexture(copy, board);", "drawCoordinates(copy, board);",
                "board coordinates draw after board texture");
        assertSourceOrder(panel, "drawCoordinates(copy, board);", "drawSquareHighlights(copy, board);",
                "square highlights paint over board coordinates");
        assertSourceOrder(panel, "drawCoordinates(copy, board);", "drawPieces(copy, board);",
                "pieces paint over board coordinates");
        assertSourceOrder(panel, "drawCoordinates(copy, board);", "drawSuggestedMove(copy, board);",
                "suggested arrows paint over board coordinates");
        assertSourceOrder(panel, "drawCoordinates(copy, board);", "drawBoardMarkups(copy, board);",
                "user arrows and circles paint over board coordinates");

        String exporter = readWorkbenchSource("board/BoardExporter.java");
        assertSourceOrder(exporter, "BoardStyle.drawBoardSurface(g, board, false);",
                "BoardStyle.drawInsideCoordinates(g, board",
                "raster export coordinates draw after board surface");
        assertSourceOrder(exporter, "BoardStyle.drawInsideCoordinates(g, board",
                "paintRasterHighlights(snapshot, g, board);",
                "raster export highlights paint over coordinates");
        assertSourceOrder(exporter, "appendSquares(svg, board);",
                "appendCoordinates(svg, board, snapshot.whiteDown());",
                "svg export coordinates are low in DOM");
        assertSourceOrder(exporter, "appendCoordinates(svg, board, snapshot.whiteDown());",
                "appendSvgHighlights(snapshot, svg, board);",
                "svg export highlights are above coordinates in DOM");
    }

    /**
     * Verifies the board paints the checked-king marker.
     */
    private static void testBoardCheckHighlightPaintsCheckedKingMarker() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position("4k3/8/8/8/8/8/8/K3R3 b - - 0 1"), Move.NO_MOVE);
        assertEquals(Byte.valueOf(Field.toIndex('e', '8')),
                invoke(board, "checkedKingSquare", new Class<?>[0]),
                "checked king square");

        Component component = (Component) board;
        component.setSize(640, 640);
        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (640 - size) / 2;
        int boardY = (640 - size) / 2;
        Color marker = new Color(image.getRGB(boardX + 4 * cell + 5, boardY + 5), true);
        assertTrue(marker.getRed() > marker.getGreen() && marker.getRed() > marker.getBlue(),
                "check marker keeps a coral tint");
        assertColorDistanceAtLeast(marker, themeColor("BOARD_LIGHT"), 25.0,
                "check marker distinguishes from pastel light board squares");
    }

    /**
     * Verifies the expensive board layer is cached by board size.
     */
    private static void testBoardTextureCachesRenderedLayer() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Object firstTexture = invoke(board, "boardTexture", new Class<?>[] { int.class }, 576);
        Object secondTexture = invoke(board, "boardTexture", new Class<?>[] { int.class }, 576);
        Object largerTexture = invoke(board, "boardTexture", new Class<?>[] { int.class }, 640);

        assertTrue(firstTexture == secondTexture, "same board texture cache reuse");
        assertFalse(firstTexture == largerTexture, "different board texture cache refresh");
    }

    /**
     * Verifies scaled SVG pieces are cached per board cell size.
     */
    private static void testBoardPieceImageCacheReusesScaledSvg() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Object first = invoke(board, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 72);
        Object second = invoke(board, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 72);
        Object larger = invoke(board, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 80);

        assertTrue(first == second, "same cell piece cache reuse");
        assertFalse(first == larger, "different cell piece cache refresh");
    }

    /**
     * Verifies every board renderer shares one exact square grid for boards,
     * overlays, focus rings, and core/LERF square conversions.
     */
    private static void testBoardStyleUsesSharedPixelGeometry() {
        Rectangle board = new Rectangle(3, 5, 83, 83);
        boolean[][] covered = new boolean[board.height][board.width];
        int coveredPixels = 0;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle cell = BoardStyle.cellBounds(board, row, col);
                assertTrue(cell.width > 0 && cell.height > 0, "cell is visible");
                for (int y = cell.y; y < cell.y + cell.height; y++) {
                    for (int x = cell.x; x < cell.x + cell.width; x++) {
                        int localY = y - board.y;
                        int localX = x - board.x;
                        assertFalse(covered[localY][localX], "board cells do not overlap");
                        covered[localY][localX] = true;
                        coveredPixels++;
                    }
                }
            }
        }
        assertEquals(Integer.valueOf(board.width * board.height), Integer.valueOf(coveredPixels),
                "board cells cover the whole board");

        int e4Lerf = lerfSquare('e', 4);
        Rectangle e4FromCore = BoardStyle.fieldSquareBounds(board, Field.toIndex('e', '4'), true);
        Rectangle e4FromLerf = BoardStyle.lerfSquareBounds(board, e4Lerf, true);
        assertEquals(e4FromCore, e4FromLerf, "core and LERF e4 share visual bounds");
        assertTensorOverlayStaysInside(board, e4FromLerf, e4Lerf);
        assertTensorRingStaysInside(board, e4FromLerf, e4Lerf);
    }

    /**
     * Returns a LERF square index for a board coordinate.
     *
     * @param file file letter
     * @param rank rank number
     * @return LERF square index
     */
    private static int lerfSquare(char file, int rank) {
        return (rank - 1) * 8 + (file - 'a');
    }

    /**
     * Returns the unmarked base color for one white-bottom board square.
     *
     * @param square CRTK square index
     * @return base board color
     */
    private static Color baseBoardColor(byte square) {
        return BoardStyle.squareColor(square / 8, Field.getX(square));
    }

    /**
     * Returns the board cell size for the standard test component geometry.
     *
     * @param width component width
     * @param height component height
     * @return cell size in pixels
     */
    private static int boardCellSize(int width, int height) {
        int size = Math.min(width - 64, height - 64);
        size = Math.max(64, size - size % 8);
        return size / 8;
    }

    /**
     * Verifies a TensorViz square overlay touches only the shared target cell.
     *
     * @param board board bounds
     * @param expected expected square bounds
     * @param square LERF square index
     */
    private static void assertTensorOverlayStaysInside(Rectangle board, Rectangle expected, int square) {
        BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
        float[] values = new float[64];
        values[square] = 1.0f;
        Graphics2D graphics = image.createGraphics();
        try {
            TensorViz.drawSquareOverlay(graphics, board, values, 1.0f, false);
        } finally {
            graphics.dispose();
        }
        assertPaintStaysInside(image, expected, "tensor overlay");
    }

    /**
     * Verifies a TensorViz focus ring touches only the shared target cell.
     *
     * @param board board bounds
     * @param expected expected square bounds
     * @param square LERF square index
     */
    private static void assertTensorRingStaysInside(Rectangle board, Rectangle expected, int square) {
        BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            TensorViz.drawBoardSquareRing(graphics, board, square, Color.YELLOW);
        } finally {
            graphics.dispose();
        }
        assertPaintStaysInside(image, expected, "tensor ring");
    }

    /**
     * Verifies all painted pixels in an image live within expected bounds.
     *
     * @param image image to inspect
     * @param expected expected paint bounds
     * @param label assertion label
     */
    private static void assertPaintStaysInside(BufferedImage image, Rectangle expected, String label) {
        int painted = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                assertTrue(expected.contains(x, y), label + " paint stays in target square");
                painted++;
            }
        }
        assertTrue(painted > 0, label + " paints target square");
    }

    /**
     * Verifies network mini-board piece rendering uses absolute board
     * coordinates with White's home rank at the bottom.
     */
    private static void testTensorMiniBoardPiecesRenderWhiteBottom() {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            invokeStatic(type("TensorViz"), "drawPositionPieces",
                    new Class<?>[] { Graphics2D.class, Rectangle.class, String.class },
                    graphics, new Rectangle(0, 0, 80, 80),
                    "7k/8/8/8/8/8/8/K7 w - - 0 1");
        } finally {
            graphics.dispose();
        }
        int topLeftAlpha = alphaSum(image, 0, 0, 10, 10);
        int bottomLeftAlpha = alphaSum(image, 0, 70, 10, 10);
        assertTrue(bottomLeftAlpha > topLeftAlpha + 1000,
                "mini-board white king renders on a1 at the bottom");
    }
}
