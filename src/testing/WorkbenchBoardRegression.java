package testing;

import static testing.TestSupport.readUtf8;
import static testing.WorkbenchTestSupport.*;

import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
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

import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.board.PremoveContext;
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
        testBoardDragQueuesPremoveForNonSideToMovePiece();
        testBoardRejectsNonLichessPremoveShape();
        testBoardPremoveSelectionShowsLichessBlueDestinations();
        testBoardQueuedPremoveUsesLichessBlueSquares();
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
     * Verifies the setup editor drives the shared board, keeps the FEN preview
     * stable, prunes stale FEN metadata, and applies through its host callback.
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
                "board editor activates direct editing on the shared board");
        JTextField fenPreview = (JTextField) field(editor, "fenPreviewField");
        @SuppressWarnings("unchecked")
        JComboBox<String> enPassant = (JComboBox<String>) field(editor, "enPassantBox");
        Dimension previewSize = fenPreview.getPreferredSize();
        invoke(editor, "setPieceAt", new Class<?>[] { byte.class, byte.class },
                Field.H4, Piece.WHITE_QUEEN);
        assertEquals(Byte.valueOf(Piece.WHITE_QUEEN),
                invoke(board, "setupEditPieceAt", new Class<?>[] { byte.class }, Field.H4),
                "board editor paints pieces on the shared board");
        assertEquals("4k3/8/8/8/7Q/8/8/4K3 w - - 0 1",
                invoke(editor, "fen", new Class<?>[0]), "board editor FEN placement");
        assertEquals(previewSize, fenPreview.getPreferredSize(), "FEN preview width stays fixed after edit");
        invoke(board, "setSetupEditPieceAt", new Class<?>[] { byte.class, byte.class },
                Field.A8, Piece.BLACK_ROOK);
        assertEquals("r3k3/8/8/8/7Q/8/8/4K3 w - - 0 1",
                invoke(editor, "fen", new Class<?>[0]), "shared board edit updates editor FEN");
        assertTrue((Boolean) invoke(editor, "loadFen", new Class<?>[] { String.class },
                "r3k3/8/8/8/8/8/8/4K3 w - - 0 1"),
                "board editor reloads after position changes");
        assertTrue((Boolean) invoke(board, "isSetupEditMode", new Class<?>[0]),
                "board editor remains editable after reload");
        assertTrue((Boolean) invoke(editor, "loadFen", new Class<?>[] { String.class }, START_FEN),
                "board editor loads start position for metadata pruning");
        assertFalse(enPassant.isEnabled(), "en-passant dropdown disables when no target is possible");
        assertEquals(Integer.valueOf(0), Integer.valueOf(enPassant.getItemCount()),
                "en-passant dropdown has no choices when no target is possible");
        invoke(board, "setSetupEditPieceAt", new Class<?>[] { byte.class, byte.class },
                Field.H1, Piece.EMPTY);
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN1 w Qkq - 0 1",
                invoke(editor, "fen", new Class<?>[0]), "removing rook clears matching castling right");
        assertEquals(previewSize, fenPreview.getPreferredSize(), "FEN preview width stays fixed after castling prune");
        assertTrue((Boolean) invoke(editor, "loadFen", new Class<?>[] { String.class },
                "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"),
                "board editor loads en-passant setup");
        assertTrue(enPassant.isEnabled(), "en-passant dropdown enables for legal target");
        assertEquals(Integer.valueOf(2), Integer.valueOf(enPassant.getItemCount()),
                "en-passant dropdown offers none plus the target square");
        assertEquals("d6", enPassant.getSelectedItem(), "en-passant dropdown selects loaded target");
        invoke(board, "setSetupEditPieceAt", new Class<?>[] { byte.class, byte.class },
                Field.D5, Piece.EMPTY);
        assertEquals("4k3/8/8/4P3/8/8/8/4K3 w - - 0 1",
                invoke(editor, "fen", new Class<?>[0]), "removing passed pawn clears en-passant target");
        assertFalse(enPassant.isEnabled(), "en-passant dropdown disables after target is removed");
        assertEquals(Integer.valueOf(0), Integer.valueOf(enPassant.getItemCount()),
                "en-passant dropdown clears choices after target is removed");
        assertEquals(previewSize, fenPreview.getPreferredSize(), "FEN preview width stays fixed after en-passant prune");

        @SuppressWarnings("unchecked")
        JComboBox<String> sideToMove = (JComboBox<String>) field(editor, "sideToMoveBox");
        sideToMove.setSelectedItem("Black");
        assertTrue((Boolean) invoke(editor, "applyEditedFen", new Class<?>[0]),
                "board editor applies legal FEN");
        assertEquals("4k3/8/8/4P3/8/8/8/4K3 b - - 0 1", applied[0],
                "board editor normalizes applied FEN");
        assertTrue((Boolean) invoke(board, "isSetupEditMode", new Class<?>[0]),
                "applying keeps the shared board editable");
        invoke(editor, "setEditingBoardActive", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isSetupEditMode", new Class<?>[0]),
                "leaving editor disables shared-board setup editing");
        assertFalse(readWorkbenchSource("board/BoardEditorPanel.java").contains("white bottom"),
                "board editor does not show the old orientation label");
        assertFalse(readWorkbenchSource("board/BoardEditorPanel.java").contains("label(\"main board\")"),
                "board editor does not replace orientation text with another board label");
        assertFalse(readWorkbenchSource("board/BoardEditorPanel.java").contains("new BoardPanel"),
                "board editor does not create a small embedded board");
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
        return readUtf8(java.nio.file.Path.of("src/application/gui/workbench", relativePath));
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
        List<?> markups = boardMarkups(board);
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "right drag adds one arrow");
        Object markup = markups.get(0);
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(markup, "from"), "arrow origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')), field(markup, "to"), "arrow target");
        assertEquals("green", field(field(markup, "brush"), "name"), "default arrow brush");

        drawRightArrow(board, from, to, 0);
        markups = boardMarkups(board);
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
        List<?> markups = boardMarkups(board);
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "right click adds one circle");
        Object markup = markups.get(0);
        assertEquals(Byte.valueOf(Field.toIndex('d', '4')), field(markup, "from"), "circle square");
        assertEquals(Byte.valueOf(Field.NO_SQUARE), field(markup, "to"), "circle has no target");

        rightClick(board, square, 0);
        markups = boardMarkups(board);
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
        List<?> markups = boardMarkups(board);
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
     * Verifies a human piece can be dragged as a premove while the FEN says the
     * opponent is to move.
     */
    private static void testBoardDragQueuesPremoveForNonSideToMovePiece() {
        BoardPanel board = new BoardPanel();
        board.setSize(640, 640);
        board.setPositionInstant(new Position(
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
                Move.NO_MOVE);
        PremoveContext[] queued = new PremoveContext[1];
        board.setPremoveStartFilter(context -> Piece.isWhite(context.piece()));
        board.setPremoveHandler(context -> {
            queued[0] = context;
            return true;
        });

        Point from = boardPoint(Field.toIndex('g', '1'), true, 640, 640);
        Point to = boardPoint(Field.toIndex('f', '3'), true, 640, 640);
        long now = System.currentTimeMillis();
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_PRESSED, now, from.x, from.y, 1));
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_DRAGGED, now + 1L, to.x, to.y, 0));
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_RELEASED, now + 2L, to.x, to.y, 1));

        assertTrue(queued[0] != null, "premove drag queues a context");
        assertEquals("g1f3", Move.toString(queued[0].tentativeMove()), "premove drag move");
        assertEquals(Move.toString(queued[0].tentativeMove()),
                Move.toString((Short) field(board, "pendingPremove")), "premove arrow state");
    }

    /**
     * Verifies premove input follows lichess/chessground pseudo-legal piece
     * movement instead of accepting arbitrary destination squares.
     */
    private static void testBoardRejectsNonLichessPremoveShape() {
        BoardPanel board = premoveBoard();
        PremoveContext[] queued = new PremoveContext[1];
        board.setPremoveStartFilter(context -> Piece.isWhite(context.piece()));
        board.setPremoveHandler(context -> {
            queued[0] = context;
            return true;
        });

        Point from = boardPoint(Field.toIndex('g', '1'), true, 640, 640);
        Point illegal = boardPoint(Field.toIndex('g', '3'), true, 640, 640);
        long now = System.currentTimeMillis();
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_PRESSED, now, from.x, from.y, 1));
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_DRAGGED, now + 1L, illegal.x, illegal.y, 0));
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_RELEASED, now + 2L, illegal.x, illegal.y, 1));

        assertEquals(null, queued[0], "invalid knight premove shape is not queued");
        assertEquals(Short.valueOf(Move.NO_MOVE), field(board, "pendingPremove"),
                "invalid premove leaves no current premove");
    }

    /**
     * Verifies selecting a premove source paints blue lichess-style destination
     * dots instead of green legal-move dots.
     */
    private static void testBoardPremoveSelectionShowsLichessBlueDestinations() {
        BoardPanel board = premoveBoard();
        board.setPremoveStartFilter(context -> Piece.isWhite(context.piece()));
        board.setPremoveHandler(context -> true);
        Point from = boardPoint(Field.toIndex('g', '1'), true, 640, 640);
        Point target = boardPoint(Field.toIndex('f', '3'), true, 640, 640);

        Color baseline = new Color(paint(board, 640, 640).getRGB(target.x, target.y), true);
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), from.x, from.y, 1));
        Color selected = new Color(paint(board, 640, 640).getRGB(target.x, target.y), true);

        assertTrue(blueOverlayVisible(baseline, selected), "premove destination marker is lichess blue");
    }

    /**
     * Verifies a queued premove is shown as blue origin/destination square fills,
     * matching chessground's current-premove class rather than a red arrow.
     */
    private static void testBoardQueuedPremoveUsesLichessBlueSquares() {
        BoardPanel board = premoveBoard();
        byte targetSquare = Field.toIndex('f', '3');
        Point target = boardPoint(targetSquare, true, 640, 640);
        Color baseline = new Color(paint(board, 640, 640).getRGB(target.x, target.y), true);

        board.setPremove(Move.parse("g1f3"));
        Color queued = new Color(paint(board, 640, 640).getRGB(target.x, target.y), true);

        assertTrue(blueOverlayVisible(baseline, queued), "queued premove destination square is lichess blue");
    }

    /**
     * Builds a board where White may premove because Black is to move.
     *
     * @return premove test board
     */
    private static BoardPanel premoveBoard() {
        BoardPanel board = new BoardPanel();
        board.setSize(640, 640);
        board.setPositionInstant(new Position(
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
                Move.NO_MOVE);
        return board;
    }

    /**
     * Returns whether an overlay shifted a sampled square toward lichess blue.
     *
     * @param before baseline square color
     * @param after overlaid square color
     * @return true when blue increased and red decreased
     */
    private static boolean blueOverlayVisible(Color before, Color after) {
        return after.getRed() < before.getRed()
                && after.getGreen() < before.getGreen()
                && after.getBlue() - after.getRed() > before.getBlue() - before.getRed();
    }

    /**
     * Returns the board annotations captured by the export snapshot.
     *
     * @param board board component
     * @return exported annotation list
     */
    private static List<?> boardMarkups(Object board) {
        Object snapshot = invoke(board, "exportSnapshot", new Class<?>[0]);
        return (List<?>) field(snapshot, "boardMarkups");
    }

    /**
     * Returns the board animation state helper.
     *
     * @param board board component
     * @return animation state helper
     */
    private static Object boardAnimation(Object board) {
        return field(board, "animationState");
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
        seedBoardDrag(board, from, Piece.WHITE_PAWN, 24, 24, true);
        setBoardDragHover(board, Field.NO_SQUARE, Field.NO_SQUARE);

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        Point hover = boardPoint(invalidHover, true, 640, 640);
        int sampleX = hover.x - cell / 2 + 2;
        int sampleY = hover.y - cell / 2 + 2;
        Color baseline = new Color(paint(component, 640, 640).getRGB(sampleX, sampleY), true);

        setBoardDragHover(board, invalidHover, Field.NO_SQUARE);
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
        Object animation = field(board, "animation");
        byte invalidHover = Field.toIndex('f', '3');
        seedBoardDrag(board, Field.toIndex('e', '2'), Piece.WHITE_PAWN, 12, 12, false);

        Rectangle boardBounds = (Rectangle) invoke(board, "boardBounds", new Class<?>[0]);
        Rectangle dirty = (Rectangle) invoke(animation, "dragRepaintBounds",
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
        Object animation = field(board, "animation");
        byte origin = Field.toIndex('a', '2');
        byte target = Field.toIndex('h', '7');
        seedBoardDrag(board, origin, Piece.WHITE_PAWN, 0, 0, false);
        Rectangle boardBounds = (Rectangle) invoke(board, "boardBounds", new Class<?>[0]);
        Point pointer = boardPoint(target, true, 640, 640);

        Rectangle firstFrame = (Rectangle) invoke(animation, "dragRepaintBounds",
                new Class<?>[] {
                        Rectangle.class,
                        byte.class,
                        byte.class,
                        int.class,
                        int.class,
                        boolean.class,
                        boolean.class },
                boardBounds, Field.NO_SQUARE, target, pointer.x, pointer.y, Boolean.TRUE, Boolean.TRUE);
        Rectangle activeFrame = (Rectangle) invoke(animation, "dragRepaintBounds",
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
        Object animation = field(board, "animation");
        setField(animation, "lastDragRepaintNanos", Long.valueOf(System.nanoTime()));
        Rectangle first = new Rectangle(10, 12, 22, 24);
        Rectangle second = new Rectangle(80, 90, 16, 18);

        invoke(animation, "scheduleDragRepaint", new Class<?>[] { Rectangle.class }, first);
        invoke(animation, "scheduleDragRepaint", new Class<?>[] { Rectangle.class }, second);

        Timer timer = (Timer) field(animation, "dragRepaintTimer");
        Rectangle pending = (Rectangle) field(animation, "pendingDragDirty");
        assertTrue(timer.isRunning(), "drag repaint timer coalesces fast samples");
        assertTrue(pending.contains(first) && pending.contains(second),
                "pending drag repaint is the union of fast samples");

        invoke(animation, "flushPendingDragRepaint", new Class<?>[0]);
        assertEquals(null, field(animation, "pendingDragDirty"), "flushed drag repaint clears pending region");
        assertFalse(timer.isRunning(), "flushed drag repaint stops timer");
    }

    /**
     * Seeds the board's piece drag helper for paint and dirty-region tests.
     *
     * @param board board component
     * @param square drag source square
     * @param piece dragged piece
     * @param pointerX pointer x-coordinate
     * @param pointerY pointer y-coordinate
     * @param dragging true to mark the drag as active
     */
    private static void seedBoardDrag(
            Object board,
            byte square,
            byte piece,
            int pointerX,
            int pointerY,
            boolean dragging) {
        Object input = field(board, "pieceInput");
        invoke(input, "startDrag", new Class<?>[] { byte.class, byte.class, int.class, int.class },
                Byte.valueOf(square), Byte.valueOf(piece), Integer.valueOf(pointerX), Integer.valueOf(pointerY));
        invoke(input, "setDragging", new Class<?>[] { boolean.class }, Boolean.valueOf(dragging));
    }

    /**
     * Seeds drag hover and target squares in the board's piece drag helper.
     *
     * @param board board component
     * @param hoverSquare hovered square
     * @param targetSquare legal target square
     */
    private static void setBoardDragHover(Object board, byte hoverSquare, byte targetSquare) {
        Object input = field(board, "pieceInput");
        invoke(input, "setDragHoverTarget", new Class<?>[] { byte.class, byte.class },
                Byte.valueOf(hoverSquare), Byte.valueOf(targetSquare));
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

        Object animation = boardAnimation(board);
        assertTrue((Boolean) invoke(animation, "moveAnimationActive", new Class<?>[0]),
                "move animation active");
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')),
                invoke(animation, "animatedMoveFrom", new Class<?>[0]), "animated origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')),
                invoke(animation, "animatedMoveTo", new Class<?>[0]), "animated target");
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

        Object animation = boardAnimation(board);
        assertTrue((Boolean) invoke(animation, "moveAnimationActive", new Class<?>[0]),
                "reverse move animation active");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')),
                invoke(animation, "animatedMoveFrom", new Class<?>[0]),
                "reverse animated origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')),
                invoke(animation, "animatedMoveTo", new Class<?>[0]),
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

        Object animation = boardAnimation(board);
        assertTrue((Boolean) invoke(animation, "moveAnimationActive", new Class<?>[0]),
                "capture animation active");
        assertEquals(Byte.valueOf(Field.toIndex('d', '5')),
                invoke(animation, "animatedCaptureSquare", new Class<?>[0]),
                "capture fade square");
        assertFalse(((Byte) invoke(animation, "animatedCapturePiece", new Class<?>[0])).byteValue() == Piece.EMPTY,
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

        Object animation = boardAnimation(board);
        assertEquals(Byte.valueOf(Field.toIndex('h', '1')),
                invoke(animation, "animatedSecondaryMoveFrom", new Class<?>[0]),
                "castle rook origin");
        assertEquals(Byte.valueOf(Field.toIndex('f', '1')),
                invoke(animation, "animatedSecondaryMoveTo", new Class<?>[0]),
                "castle rook target");
        assertFalse(((Byte) invoke(animation, "animatedSecondaryMovePiece", new Class<?>[0])).byteValue()
                == Piece.EMPTY,
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
        Object snapshot = invoke(board, "exportSnapshot", new Class<?>[0]);
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(snapshot, "selectedSquare"),
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
        assertFalse((Boolean) invoke(boardAnimation(board), "moveAnimationActive", new Class<?>[0]),
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
        assertFalse((Boolean) invoke(boardAnimation(board), "moveAnimationActive", new Class<?>[0]),
                "move animation suppressed");
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
     * highlights, background rectangles, pieces, arrows, and exported SVG
     * overlays cover the labels.
     */
    private static void testBoardCoordinatesPaintBelowOverlays() {
        String panel = readWorkbenchSource("board/BoardPanelPainter.java");
        assertSourceOrder(panel, "drawBoardTexture(copy, board);", "drawCoordinates(copy, board);",
                "board coordinates draw after board texture");
        assertSourceOrder(panel, "drawCoordinates(copy, board);", "drawSquareHighlights(copy, board);",
                "square highlights paint over board coordinates");
        assertSourceOrder(panel, "drawCoordinates(copy, board);", "markupPainter.drawBackgroundMarkups(copy, board",
                "rectangle annotations paint over board coordinates");
        assertSourceOrder(panel, "markupPainter.drawBackgroundMarkups(copy, board", "drawPieces(copy, board);",
                "pieces paint over rectangle annotations");
        assertSourceOrder(panel, "drawCoordinates(copy, board);", "drawPieces(copy, board);",
                "pieces paint over board coordinates");
        assertSourceOrder(panel, "drawCoordinates(copy, board);", "drawSuggestedMove(copy, board);",
                "suggested arrows paint over board coordinates");
        assertSourceOrder(panel, "drawCoordinates(copy, board);", "markupPainter.drawForegroundMarkups(copy, board",
                "user arrows and circles paint over board coordinates");

        String exporter = readWorkbenchSource("board/BoardExporter.java");
        assertSourceOrder(exporter, "BoardStyle.drawBoardSurface(g, board, false,",
                "BoardStyle.drawInsideCoordinates(g, board",
                "raster export coordinates draw after board surface");
        assertSourceOrder(exporter, "BoardStyle.drawInsideCoordinates(g, board",
                "paintRasterHighlights(snapshot, g, board);",
                "raster export highlights paint over coordinates");
        assertSourceOrder(exporter, "appendSquares(snapshot, svg, board);",
                "appendCoordinates(snapshot, svg, board, snapshot.whiteDown());",
                "svg export coordinates are low in DOM");
        assertSourceOrder(exporter, "appendCoordinates(snapshot, svg, board, snapshot.whiteDown());",
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
        Object painter = field(board, "painter");
        assertEquals(Byte.valueOf(Field.toIndex('e', '8')),
                invoke(painter, "checkedKingSquare", new Class<?>[0]),
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
        Object imageCache = field(board, "imageCache");
        Object first = invoke(imageCache, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 72);
        Object second = invoke(imageCache, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 72);
        Object larger = invoke(imageCache, "pieceImage", new Class<?>[] { byte.class, int.class },
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
            TensorViz.drawSquareOverlay(graphics, board, values, 1.0f, true);
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
