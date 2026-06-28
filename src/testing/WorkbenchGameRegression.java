package testing;

import static testing.WorkbenchTestSupport.*;

import java.awt.Component;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import application.gui.workbench.engine.EngineGauntletPanel;
import application.gui.workbench.board.AnnotationGlyphs;
import application.gui.workbench.board.BoardExporter;
import application.gui.workbench.board.BoardMarkup;
import application.gui.workbench.board.BoardMarkupComment;
import application.gui.workbench.board.BoardMarkupTool;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.board.MarkupBrush;
import chess.core.Field;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.game.GameReviewPanel;
import application.gui.workbench.game.PgnExplorerModel;
import application.gui.workbench.game.PgnPrepReport;
import application.gui.workbench.game.PlayMoveHistoryModel;
import application.gui.workbench.game.PuzzleLibrary;
import application.gui.workbench.game.PuzzlePanel;
import application.gui.workbench.game.PuzzleSession;
import application.gui.workbench.game.ReviewCliArtifactProducer;
import application.gui.workbench.game.SavedGame;
import application.gui.workbench.game.SavedGameStore;
import application.gui.workbench.game.SanRenderer;
import application.gui.workbench.game.StudyAuthorPanel;
import application.gui.workbench.study.StudyRepository;
import application.gui.workbench.study.StudyWorkspacePanel;
import application.gui.workbench.game.TablebasePanel;
import application.gui.workbench.library.GameLibrary;
import application.gui.workbench.ui.NotationPainter;
import application.gui.workbench.ui.Theme;

import chess.review.Classifier;
import chess.review.Classifier.Request;
import chess.review.Classifier.Score;
import chess.review.Classifier.Thresholds;
import chess.review.ReviewRow;
import chess.review.ReviewRow.Assessment;
import chess.review.ReviewRow.Eval;
import chess.review.ReviewRow.GameRef;
import chess.review.ReviewRow.MoveChoice;
import chess.review.ReviewRow.Ply;
import chess.review.ReviewRow.Repro;
import chess.review.ReviewRow.Tags;
import chess.review.ReviewRow.Wdl;
import chess.review.StudyUnit;
import chess.review.StudyUnitFactory;
import chess.core.Move;
import chess.core.Field;
import chess.core.Position;
import chess.core.Setup;
import chess.study.ShapeCommentCodec;
import chess.study.StudyChapter;
import chess.study.StudyChapterMode;
import chess.study.StudyNodePath;
import chess.study.StudyProject;
import chess.study.StudyShape;
import chess.study.StudyTreeModel;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.uci.Output;

/**
 * Game notation, ECO, eval, and analysis regression checks.
 */
final class WorkbenchGameRegression {

    /**
     * Prevents instantiation.
     */
    private WorkbenchGameRegression() {
        // utility
    }

    /**
     * Runs this focused regression group.
     */
    static void run() {
        testWorkbenchSanRendererUsesNeutralPieceSvgs();
        testInlineNotationPainterHandlesFeatureLabels();
        testGameModelLoadsPgnVariations();
        testAnalyzeVariationTreeUsesVisibleVariationRows();
        testAnalyzeVariationTreeUsesCompactBlackMoveLabels();
        testGameModelNavigatesSelectedVariationLine();
        testGameModelCreatesAndExtendsEditableVariations();
        testPlayMoveHistoryModelPairsSanMoves();
        testPuzzleSessionExploresOpponentVariationBranches();
        testPuzzlePanelStartsStandardWithoutLoadedLibrary();
        testPuzzlePanelTogglesReviewStartEnd();
        testPuzzlePanelAnimatesOpponentReplySeparately();
        testPuzzleWrongMoveUsesChessWebMarker();
        testPuzzleWrongMoveMarkerStaysInsideEdgeSquare();
        testPuzzleSampleBranchTransitionAnimatesRewind();
        testPuzzleSessionReviewNavigation();
        testPuzzleLibraryLoadsDifficultCsv();
        testPuzzleLibraryLoadsChessWebPgn();
        testPuzzleLibraryDefaultPathResolves();
        testPuzzlePanelShufflesLoadedLibrary();
        testPuzzlePanelPaintsOpaqueSurface();
        testAnalysisBoardExportsRasterAndSvg();
        testPgnExplorerModelFiltersGames();
        testPgnPrepReportSummarizesPlayerOpenings();
        testEcoExplorerFiltersAndLoadsLines();
        testGameReviewPanelFindsStaticSwings();
        testGameReviewPanelLoadsProducedReviewArtifacts();
        testGameReviewPanelUsesLichessReviewModes();
        testGameReviewPanelBuildsStudyArtifactsViaCli();
        testSavedGameStoreRoundTripsAndValidatesMoves();
        testGameLibraryStoresCurrentAndImportedPgnGames();
        testStudyRepositoryCreatesStarterPgnBook();
        testStudyShapeCommentCodecRoundTrips();
        testBoardMarkupCommentRoundTrips();
        testStudyTreeModelEditsFullPgnTree();
        testStudyRepositorySavesPgnBackedProject();
        testStudyRepositoryImportsReviewStudyUnits();
        testStudyWorkspacePanelSmoke();
        testStudyAuthorPanelBuildsManifestFromGameLine();
        testAccessibilityLabelsForNewWorkbenchPanels();
        testEvalBarMapping();
        testEvalBarAnimation();
        testEvalBarFrameFollowsTheme();
        testEvalBarThinkingIsStatic();
        testEngineEvalParsing();
        testTablebasePanelSummarizesEngineOutput();
        testLiveEngineStatusFormatting();
        testOptionalPositiveIntegerParsing();
        testAnalysisGraphStoresSamples();
        testAnalysisGraphStoresCompletedCommandOutput();
        testAnalysisGraphExportsReportData();
        testExportToastMessageSummarizesArtifacts();
        testAnalysisGraphPaintsOpaqueSurface();
    }

    /**
     * Verifies SAN notation uses inline SVG pieces rather than Unicode
     * figurines.
     */
    private static void testWorkbenchSanRendererUsesNeutralPieceSvgs() {
        assertEquals(Integer.valueOf(3), invokeStatic(type("SanRenderer"), "pieceSvgCount",
                new Class<?>[] { String.class }, "1. Qxe8+ Nbd6 2. bxa8=Q#"),
                "queen, knight, and promotion use SVG pieces");
        JTable table = new JTable(1, 1);
        Theme.table(table, 27);
        SanRenderer renderer = new SanRenderer();
        Component component = renderer.getTableCellRendererComponent(table,
                "1. Qxe8+ Nbd6 2. bxa8=Q#", false, false, 0, 0);
        component.setSize(240, 27);
        BufferedImage image = paint(component, 240, 27);
        assertTrue(alphaSum(image, 0, 0, 240, 27) > 0, "SAN renderer paints SVG-backed notation");
        table.setSelectionBackground(new Color(0x445566));
        Component selectedComponent = renderer.getTableCellRendererComponent(table,
                "1. Qxe8+ Nbd6", true, false, 0, 0);
        selectedComponent.setSize(240, 27);
        BufferedImage selectedImage = paint(selectedComponent, 240, 27);
        assertColor(table.getSelectionBackground(), new Color(selectedImage.getRGB(235, 2), true),
                "SAN renderer fills selected table background");

        JTable cutoutTable = new JTable(1, 1);
        cutoutTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 42));
        cutoutTable.setBackground(Color.WHITE);
        cutoutTable.setForeground(Color.RED);
        Component cutout = renderer.getTableCellRendererComponent(cutoutTable,
                "Rb1", false, false, 0, 0);
        cutout.setSize(160, 64);
        BufferedImage cutoutImage = paint(cutout, 160, 64);
        assertColor(Color.WHITE, new Color(cutoutImage.getRGB(30, 32), true),
                "SAN rook uses report-style cutout interior");
        assertTrue(redPixelCount(cutoutImage, 6, 8, 48, 48) > 220,
                "SAN rook paints the cutout outline in the table foreground");
    }

    /**
     * Verifies shared inline notation also handles NN feature labels with
     * lowercase piece markers.
     */
    private static void testInlineNotationPainterHandlesFeatureLabels() {
        assertEquals(Integer.valueOf(4),
                Integer.valueOf(NotationPainter.pieceSvgCount("Kc5 / qd1 / Ne1 / pa7")),
                "feature labels replace both king and piece letters with SVG pieces");

        BufferedImage image = new BufferedImage(180, 46, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setFont(Theme.font(24, Font.BOLD));
        NotationPainter.draw(graphics, "Kc5 / qd1", 4, 32, 160, Color.RED);
        graphics.dispose();

        assertTrue(redPixelCount(image, 4, 6, 36, 36) > 120,
                "inline notation paints a cutout SVG for coordinate labels");
    }

    /**
     * Counts strongly red pixels in a rectangular image region.
     *
     * @param image source image
     * @param x region x
     * @param y region y
     * @param width region width
     * @param height region height
     * @return red pixel count
     */
    private static int redPixelCount(BufferedImage image, int x, int y, int width, int height) {
        int count = 0;
        for (int yy = y; yy < y + height; yy++) {
            for (int xx = x; xx < x + width; xx++) {
                Color color = new Color(image.getRGB(xx, yy), true);
                if (color.getRed() > 180 && color.getGreen() < 100 && color.getBlue() < 100) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Counts red-dominant error marker pixels in a rectangular region.
     *
     * @param image source image
     * @param x region x
     * @param y region y
     * @param width region width
     * @param height region height
     * @return red-dominant pixel count
     */
    private static int errorMarkerPixelCount(BufferedImage image, int x, int y, int width, int height) {
        int count = 0;
        for (int yy = y; yy < y + height; yy++) {
            for (int xx = x; xx < x + width; xx++) {
                Color color = new Color(image.getRGB(xx, yy), true);
                if (color.getAlpha() > 120
                        && color.getRed() > color.getGreen() * 2
                        && color.getRed() > color.getBlue() * 2) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Verifies imported algebraic movetext keeps PGN variation rows.
     */
    private static void testGameModelLoadsPgnVariations() {
        GameModel model = new GameModel();
        Game game = Pgn.parseGame("1. e4 (1. d4 d5) e5 *");
        model.loadGame(game.getStartPosition(), game);

        assertEquals(Integer.valueOf(4), Integer.valueOf(model.getRowCount()), "variation rows visible");
        assertEquals(Integer.valueOf(2), Integer.valueOf(model.lastPly()), "mainline ply count");
        assertEquals(Integer.valueOf(2), Integer.valueOf(model.variationRowCount()), "variation ply count");
        List<GameModel.VisibleMoveSnapshot> rows = model.visibleMoveSnapshots();
        assertEquals(Integer.valueOf(4), Integer.valueOf(rows.size()), "visible variation tree rows");
        assertTrue(rows.get(0).mainline(), "visible tree starts with the mainline move");
        assertFalse(rows.get(1).mainline(), "visible tree includes the PGN variation row");
        assertEquals("d4", rows.get(1).san(), "visible variation row keeps trimmed SAN");
        assertEquals(Integer.valueOf(1), Integer.valueOf(rows.get(1).variationDepth()),
                "visible variation row carries branch depth");
        assertEquals(Integer.valueOf(1), Integer.valueOf(rows.get(1).rowIndex()),
                "visible variation row keeps table navigation index");
        assertTrue(model.pgn().contains("(1. d4 d5)"), "PGN export preserves variation");

        Position afterD4 = new Position(START_FEN);
        afterD4.play(Move.parse("d2d4"));
        model.jumpToRow(1);
        assertEquals("d2d4", Move.toString(model.currentLastMove()), "variation row move");
        assertEquals(afterD4.toString(), model.currentPosition().toString(), "variation row position");
        assertEquals(List.of(Short.valueOf(Move.parse("d2d4"))), model.currentPath(),
                "variation row path");

        Position afterMainline = new Position(START_FEN);
        afterMainline.play(Move.parse("e2e4"));
        afterMainline.play(Move.parse("e7e5"));
        model.jumpToPly(2);
        assertEquals(afterMainline.toString(), model.currentPosition().toString(), "mainline navigation restored");
        assertEquals(List.of(Short.valueOf(Move.parse("e2e4")), Short.valueOf(Move.parse("e7e5"))),
                model.currentPath(), "mainline path");
    }

    /**
     * Verifies the Analyze-side variation tree paints and clicks visible PGN
     * variation rows, not only editable mainline plies.
     */
    private static void testAnalyzeVariationTreeUsesVisibleVariationRows() {
        GameModel model = new GameModel();
        Game game = Pgn.parseGame("1. e4 (1. d4 d5) e5 *");
        model.loadGame(game.getStartPosition(), game);
        List<Integer> jumps = new ArrayList<>();
        JComponent tree = (JComponent) construct(type("AnalyzeVariationTreePanel"),
                new Class<?>[] { GameModel.class, IntConsumer.class },
                model, (IntConsumer) jumps::add);

        assertTrue(tree.getPreferredSize().width >= 236,
                "analyze variation tree reserves room for branch text");
        assertTrue(tree.getToolTipText().contains("PGN variations"),
                "analyze variation tree tooltip explains branch navigation");
        tree.setSize(260, 180);
        paint(tree, 260, 180);
        Point variationPoint = variationTreePointForRow(tree, 1);
        tree.dispatchEvent(mouse(tree, MouseEvent.MOUSE_CLICKED, 1L,
                variationPoint.x, variationPoint.y, 1));
        assertEquals(Integer.valueOf(1), jumps.get(0),
                "analyze variation tree clicks through to the variation display row");
    }

    /**
     * Verifies the Analyze-side variation tree keeps black replies compact by
     * omitting repeated {@code N...} labels.
     */
    private static void testAnalyzeVariationTreeUsesCompactBlackMoveLabels() {
        GameModel model = new GameModel();
        Game game = Pgn.parseGame("1. e4 e5 (1... d5 2. exd5 Qxd5) 2. Nf3 Nc6 *");
        model.loadGame(game.getStartPosition(), game);
        JComponent tree = (JComponent) construct(type("AnalyzeVariationTreePanel"),
                new Class<?>[] { GameModel.class, IntConsumer.class },
                model, (IntConsumer) row -> {
                    // no-op
                });

        List<String> texts = variationTreeTokenTexts(tree);
        assertTrue(texts.contains("1. e4"), "tree keeps white move-number prefix");
        assertTrue(texts.contains("d5"), "tree shows black branch reply without move number");
        assertTrue(texts.contains("2. exd5"), "tree starts the next white pair with a move number");
        assertTrue(texts.contains("Qxd5"), "tree shows black continuation without move number");
        assertFalse(texts.contains("1... d5"), "tree omits repeated black branch move number");
        assertFalse(texts.contains("2... Qxd5"), "tree omits repeated black continuation move number");
    }

    /**
     * Finds any clickable point for a visible variation-tree row.
     *
     * @param tree variation tree component
     * @param row visible row
     * @return clickable point
     */
    private static Point variationTreePointForRow(JComponent tree, int row) {
        for (int y = 56; y < tree.getHeight(); y++) {
            for (int x = 0; x < tree.getWidth(); x++) {
                Integer hit = (Integer) invoke(tree, "rowAt",
                        new Class<?>[] { int.class, int.class },
                        Integer.valueOf(x), Integer.valueOf(y));
                if (hit.intValue() == row) {
                    return new Point(x, y);
                }
            }
        }
        throw new AssertionError("missing variation tree hit target for row " + row);
    }

    /**
     * Reads displayed variation-tree token text through the package-private
     * component API.
     *
     * @param tree variation tree component
     * @return token texts in display order
     */
    private static List<String> variationTreeTokenTexts(JComponent tree) {
        List<?> tokens = (List<?>) invoke(tree, "notationTokens", new Class<?>[0]);
        List<String> texts = new ArrayList<>();
        for (Object token : tokens) {
            texts.add((String) invoke(token, "text", new Class<?>[0]));
        }
        return texts;
    }

    /**
     * Verifies back/forward transport stays on the selected PGN variation.
     */
    private static void testGameModelNavigatesSelectedVariationLine() {
        GameModel model = new GameModel();
        Game game = Pgn.parseGame("1. e4 (1. d4 d5) e5 *");
        model.loadGame(game.getStartPosition(), game);

        model.jumpToRow(1);
        assertEquals("d2d4", Move.toString(model.currentLastMove()), "selected variation first move");
        assertTrue(model.canForward(), "variation continuation is available");
        assertTrue(model.navigate(1), "variation moves forward");
        assertEquals("d7d5", Move.toString(model.currentLastMove()), "variation forward keeps variation line");
        assertFalse(model.canForward(), "variation end has no synthetic mainline forward move");
        assertTrue(model.navigate(-1), "variation moves backward");
        assertEquals("d2d4", Move.toString(model.currentLastMove()), "variation back returns to previous variation move");
        assertTrue(model.navigate(-1), "variation returns to root");
        assertEquals(Integer.valueOf(0), Integer.valueOf(model.currentPly()), "variation back reaches root");
        assertEquals(Integer.valueOf(-1), Integer.valueOf(model.currentRow()), "root has no selected row");

        GameModel singleMoveVariation = new GameModel();
        Game shortVariation = Pgn.parseGame("1. e4 (1. d4) e5 *");
        singleMoveVariation.loadGame(shortVariation.getStartPosition(), shortVariation);
        singleMoveVariation.jumpToRow(1);
        assertFalse(singleMoveVariation.canForward(), "short variation does not borrow mainline forward state");
        assertFalse(singleMoveVariation.navigate(1), "short variation cannot step into unrelated mainline");
        assertEquals("d2d4", Move.toString(singleMoveVariation.currentLastMove()),
                "failed forward keeps selected variation position");
    }

    /**
     * Verifies alternate board moves become editable PGN variations instead of
     * replacing the mainline.
     */
    private static void testGameModelCreatesAndExtendsEditableVariations() {
        GameModel model = new GameModel();
        Position cursor = new Position(START_FEN);
        short e4 = Move.parse("e2e4");
        Position afterE4 = cursor.copy();
        afterE4.play(e4);
        model.append(cursor, e4, afterE4);
        cursor = afterE4;
        short e5 = Move.parse("e7e5");
        Position afterE5 = cursor.copy();
        afterE5.play(e5);
        model.append(cursor, e5, afterE5);

        model.jumpToPly(1);
        short d5 = Move.parse("d7d5");
        Position afterD5 = afterE4.copy();
        afterD5.play(d5);
        model.append(afterE4, d5, afterD5);
        assertEquals(Integer.valueOf(2), Integer.valueOf(model.lastPly()),
                "mainline length remains unchanged after branch creation");
        assertEquals(Integer.valueOf(1), Integer.valueOf(model.variationRowCount()),
                "alternate move becomes a variation row");
        assertEquals("d7d5", Move.toString(model.currentLastMove()),
                "new variation is selected");
        assertEquals(afterD5.toString(), model.currentPosition().toString(),
                "selected variation position is current");
        assertTrue(model.pgn().contains("(1... d5)"),
                "PGN export preserves editable black variation");
        assertTrue(model.canBack(), "new variation can navigate back");
        assertFalse(model.canForward(), "new one-move variation has no continuation yet");

        short exd5 = Move.parse("e4d5");
        Position afterExd5 = afterD5.copy();
        afterExd5.play(exd5);
        model.append(afterD5, exd5, afterExd5);
        assertEquals(Integer.valueOf(2), Integer.valueOf(model.variationRowCount()),
                "variation continuation is kept as a visible variation row");
        assertEquals("e4d5", Move.toString(model.currentLastMove()),
                "variation continuation is selected");
        assertTrue(model.pgn().contains("(1... d5 2. exd5)"),
                "PGN export preserves variation continuation");
        assertTrue(model.navigate(-1), "variation continuation navigates back");
        assertEquals("d7d5", Move.toString(model.currentLastMove()),
                "variation back stays inside branch");
        assertTrue(model.navigate(1), "variation can move forward again");
        assertEquals("e4d5", Move.toString(model.currentLastMove()),
                "variation forward restores branch continuation");

        model.jumpToPly(1);
        short c5 = Move.parse("c7c5");
        Position afterC5 = afterE4.copy();
        afterC5.play(c5);
        model.append(afterE4, c5, afterC5);
        assertEquals(Integer.valueOf(3), Integer.valueOf(model.variationRowCount()),
                "second alternate move keeps the first variation line");
        assertEquals("c7c5", Move.toString(model.currentLastMove()),
                "second alternate move is selected");
        String branchPgn = model.pgn();
        assertTrue(branchPgn.contains("(1... d5 2. exd5)"),
                "PGN keeps the first editable variation");
        assertTrue(branchPgn.contains("(1... c5)"),
                "PGN adds the second editable variation");

        GameModel rootBranch = new GameModel();
        Position root = new Position(START_FEN);
        Position rootE4 = root.copy();
        rootE4.play(e4);
        rootBranch.append(root, e4, rootE4);
        rootBranch.jumpToPly(0);
        short d4 = Move.parse("d2d4");
        Position rootD4 = root.copy();
        rootD4.play(d4);
        rootBranch.append(root, d4, rootD4);
        assertEquals(Integer.valueOf(1), Integer.valueOf(rootBranch.lastPly()),
                "root branch leaves mainline intact");
        assertEquals(Integer.valueOf(1), Integer.valueOf(rootBranch.variationRowCount()),
                "root alternate move becomes variation");
        assertTrue(rootBranch.pgn().contains("(1. d4)"),
                "PGN export preserves editable root variation");
    }

    /**
     * Verifies the Play move-history rail shows compact White/Black pairs
     * instead of the raw Ply/SAN/UCI/FEN game table.
     */
    private static void testPlayMoveHistoryModelPairsSanMoves() {
        GameModel game = new GameModel();
        Position cursor = new Position(START_FEN);
        for (String uci : List.of("e2e4", "e7e5", "g1f3")) {
            short move = Move.parse(uci);
            Position next = cursor.copy();
            next.play(move);
            game.append(cursor, move, next);
            cursor = next;
        }

        PlayMoveHistoryModel history = new PlayMoveHistoryModel(game);
        assertEquals(Integer.valueOf(3), Integer.valueOf(history.getColumnCount()),
                "play history has move number plus White/Black columns");
        assertEquals("#", history.getColumnName(0), "move-number column");
        assertEquals("White", history.getColumnName(1), "white column");
        assertEquals("Black", history.getColumnName(2), "black column");
        assertEquals(Integer.valueOf(2), Integer.valueOf(history.getRowCount()),
                "three plies become two move rows");
        assertEquals(Integer.valueOf(1), history.getValueAt(0, 0), "first move number");
        assertEquals("e4", history.getValueAt(0, 1), "white first move");
        assertEquals("e5", history.getValueAt(0, 2), "black first move");
        assertEquals("Nf3", history.getValueAt(1, 1), "white second move");
        assertEquals("", history.getValueAt(1, 2), "missing black reply stays blank");
    }

    /**
     * Verifies the native puzzle session follows the chess-web variation flow.
     */
    private static void testPuzzleSessionExploresOpponentVariationBranches() {
        String pgn = """
                [SetUp "1"]
                [FEN "6n1/1P2k2r/3r1b2/R2p1b1p/pp2NP2/1n6/7R/7K w - - 4 63"]

                63. Nxd6 Be4+ (Kxd6 64. b8=Q+) 64. Nxe4 Nxa5 65. b8=Q *
                """;
        PuzzleSession session = PuzzleSession.fromPgn(pgn, "test", PuzzleSession.VariationMode.EXPLORE);
        assertEquals(Integer.valueOf(2), Integer.valueOf(session.totalLines()),
                "puzzle session explores opponent branches");

        PuzzleSession.MoveResponse first = session.playUserMove(Move.parse("e4d6"), false);
        assertEquals(PuzzleSession.StepResult.CORRECT, first.result(), "first puzzle move correct");
        assertEquals("f5e4", Move.toString(first.autoPlayedMoves().get(0).shortValue()),
                "first opponent move auto-played");

        PuzzleSession.MoveResponse second = session.playUserMove(Move.parse("d6e4"), false);
        assertEquals(PuzzleSession.StepResult.CORRECT, second.result(), "second puzzle move correct");
        assertEquals("b3a5", Move.toString(second.autoPlayedMoves().get(0).shortValue()),
                "second opponent move auto-played");

        PuzzleSession.MoveResponse mainline = session.playUserMove(Move.parse("b7b8q"), false);
        assertEquals(PuzzleSession.StepResult.CORRECT, mainline.result(),
                "mainline branch completion advances to variation");
        assertEquals(Integer.valueOf(1), Integer.valueOf(mainline.snapshot().lineIndex()),
                "variation branch selected after mainline");

        PuzzleSession.MoveResponse variation = session.playUserMove(Move.parse("b7b8q"), false);
        assertEquals(PuzzleSession.StepResult.COMPLETED, variation.result(), "final branch completes puzzle");

        PuzzleSession wrong = PuzzleSession.fromPgn(pgn, "test", PuzzleSession.VariationMode.EXPLORE);
        PuzzleSession.MoveResponse rejected = wrong.playUserMove(Move.parse("e4f6"), false);
        assertEquals(PuzzleSession.StepResult.INCORRECT, rejected.result(), "wrong puzzle move rejected");
        assertEquals("e4d6", Move.toString(rejected.expectedMove()), "expected move reported");
    }

    /**
     * Verifies an idle puzzle panel shows the normal starting position when no
     * puzzle library is loaded.
     */
    private static void testPuzzlePanelStartsStandardWithoutLoadedLibrary() {
        PuzzlePanel panel = (PuzzlePanel) construct(type("PuzzlePanel"), new Class<?>[] { boolean.class },
                Boolean.FALSE);
        Object board = field(panel, "board");

        assertEquals(START_FEN, invoke(board, "position", new Class<?>[0]),
                "puzzle panel without library starts from the normal chess position");
        assertEquals(null, field(panel, "session"), "puzzle panel has no active sample session by default");
    }

    /**
     * Verifies the puzzle panel owns its own start/end review toggle instead of
     * relying on the analysis game model ply.
     */
    private static void testPuzzlePanelTogglesReviewStartEnd() {
        PuzzlePanel panel = (PuzzlePanel) construct(type("PuzzlePanel"), new Class<?>[] { boolean.class },
                Boolean.FALSE);
        invoke(panel, "loadSamplePuzzle", new Class<?>[0]);
        PuzzleSession session = (PuzzleSession) field(panel, "session");

        assertEquals(Integer.valueOf(0), Integer.valueOf(session.cursor().cursorIndex()),
                "puzzle review toggle starts at branch root");
        panel.toggleReviewStartEnd();
        assertEquals(Integer.valueOf(session.lastPly()), Integer.valueOf(session.cursor().cursorIndex()),
                "puzzle review toggle jumps to branch end");
        panel.toggleReviewStartEnd();
        assertEquals(Integer.valueOf(0), Integer.valueOf(session.cursor().cursorIndex()),
                "puzzle review toggle returns to branch root");
    }

    /**
     * Verifies the puzzle board shows the solver move first and then animates
     * the automatic opponent reply as a separate board transition.
     */
    private static void testPuzzlePanelAnimatesOpponentReplySeparately() {
        PuzzlePanel panel = (PuzzlePanel) construct(type("PuzzlePanel"), new Class<?>[] { boolean.class },
                Boolean.FALSE);
        invoke(panel, "loadSamplePuzzle", new Class<?>[0]);
        Object board = field(panel, "board");
        Position start = new Position(
                "6n1/1P2k2r/3r1b2/R2p1b1p/pp2NP2/1n6/7R/7K w - - 4 63");
        short userMove = Move.parse("e4d6");
        short opponentMove = Move.parse("f5e4");
        Position afterUser = start.copy();
        afterUser.play(userMove);
        Position afterOpponent = afterUser.copy();
        afterOpponent.play(opponentMove);

        invoke(panel, "playUserMove", new Class<?>[] { short.class }, userMove);

        assertEquals(afterUser.toString(), invoke(board, "position", new Class<?>[0]),
                "puzzle board pauses after solver move");
        assertTrue((Boolean) field(panel, "responseAnimationActive"),
                "opponent reply animation is queued");
        assertTrue(((Timer) field(panel, "responseAnimationTimer")).isRunning(),
                "opponent reply timer is running");

        invoke(panel, "advanceResponseAnimation", new Class<?>[0]);

        assertEquals(afterOpponent.toString(), invoke(board, "position", new Class<?>[0]),
                "puzzle board advances to opponent reply");
        assertFalse((Boolean) field(panel, "responseAnimationActive"),
                "opponent reply animation completes");
        Object animation = boardAnimation(board);
        assertTrue((Boolean) invoke(animation, "moveAnimationActive", new Class<?>[0]),
                "opponent reply uses board move animation");
        assertEquals(Byte.valueOf(Move.getFromIndex(opponentMove)),
                invoke(animation, "animatedMoveFrom", new Class<?>[0]),
                "opponent reply animated origin");
        assertEquals(Byte.valueOf(Move.getToIndex(opponentMove)),
                invoke(animation, "animatedMoveTo", new Class<?>[0]),
                "opponent reply animated target");
    }

    /**
     * Verifies wrong puzzle moves use the chess-web red target badge instead
     * of revealing the expected move with hint arrows.
     */
    private static void testPuzzleWrongMoveUsesChessWebMarker() {
        PuzzlePanel panel = (PuzzlePanel) construct(type("PuzzlePanel"), new Class<?>[] { boolean.class },
                Boolean.FALSE);
        invoke(panel, "loadSamplePuzzle", new Class<?>[0]);
        Object board = field(panel, "board");
        short wrongMove = Move.parse("e4f6");

        invoke(panel, "playUserMove", new Class<?>[] { short.class }, wrongMove);

        assertEquals(Short.valueOf(Move.NO_MOVE), field(board, "suggestedMove"),
                "wrong puzzle move does not reveal expected move arrow");
        assertEquals(Byte.valueOf(Move.getToIndex(wrongMove)),
                invoke(boardAnimation(board), "wrongMoveMarkerSquare", new Class<?>[0]),
                "wrong puzzle move marks attempted target");
        assertTrue(((java.util.Map<?, ?>) field(board, "squareHighlights")).isEmpty(),
                "wrong puzzle move avoids hint square highlight");
        assertEquals(Integer.valueOf(0), Integer.valueOf(((BoardPanel) board).markupCount()),
                "wrong puzzle move avoids hint arrow markup");
        String status = ((javax.swing.JLabel) field(panel, "statusLabel")).getText();
        assertFalse(status.contains("e4d6"), "wrong puzzle status does not print expected move");
    }

    /**
     * Verifies the wrong-move badge stays inside edge squares instead of being
     * clipped by the board edge.
     */
    private static void testPuzzleWrongMoveMarkerStaysInsideEdgeSquare() {
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        byte target = Field.toIndex('h', '8');

        invoke(board, "showWrongMoveMarker", new Class<?>[] { byte.class }, target);
        invoke(boardAnimation(board), "setWrongMoveMarkerStartedAt", new Class<?>[] { long.class },
                Long.valueOf(System.currentTimeMillis() - 200L));
        BufferedImage image = paint(component, 640, 640);
        Point center = boardPoint(target, true, 640, 640);
        int cell = Math.min(640 - 64, 640 - 64) / 8;
        int left = center.x - cell / 2;
        int top = center.y - cell / 2;
        int right = left + cell - 1;

        assertTrue(errorMarkerPixelCount(image, left, top, cell, cell) > 40,
                "wrong-move marker is visible on an edge square");
        assertEquals(Integer.valueOf(0),
                Integer.valueOf(errorMarkerPixelCount(image, left, top, cell, 1)),
                "wrong-move marker does not touch the board top edge");
        assertEquals(Integer.valueOf(0),
                Integer.valueOf(errorMarkerPixelCount(image, right, top, 1, cell)),
                "wrong-move marker does not touch the board right edge");
    }

    /**
     * Verifies the bundled sample puzzle shows the user's final mainline move
     * before rewinding to the variation branch and auto-playing the opponent.
     */
    private static void testPuzzleSampleBranchTransitionAnimatesRewind() {
        PuzzlePanel panel = (PuzzlePanel) construct(type("PuzzlePanel"), new Class<?>[] { boolean.class },
                Boolean.FALSE);
        invoke(panel, "loadSamplePuzzle", new Class<?>[0]);
        Object board = field(panel, "board");

        invoke(panel, "playUserMove", new Class<?>[] { short.class }, Move.parse("e4d6"));
        invoke(panel, "advanceResponseAnimation", new Class<?>[0]);
        invoke(panel, "playUserMove", new Class<?>[] { short.class }, Move.parse("d6e4"));
        invoke(panel, "advanceResponseAnimation", new Class<?>[0]);
        assertEquals("6n1/1P2k2r/5b2/n2p3p/pp2NP2/8/7R/7K w - - 0 65",
                invoke(board, "position", new Class<?>[0]),
                "sample reaches the final mainline solver decision");

        invoke(panel, "playUserMove", new Class<?>[] { short.class }, Move.parse("b7b8q"));

        assertEquals("1Q4n1/4k2r/5b2/n2p3p/pp2NP2/8/7R/7K b - - 0 65",
                invoke(board, "position", new Class<?>[0]),
                "sample shows the user's promotion before branch rewind");
        assertTrue((Boolean) field(panel, "responseAnimationActive"),
                "sample branch transition is animated");

        invoke(panel, "advanceResponseAnimation", new Class<?>[0]);
        assertEquals("6n1/1P2k2r/5b2/n2p3p/pp2NP2/8/7R/7K w - - 0 65",
                invoke(board, "position", new Class<?>[0]),
                "sample rewinds visibly before loading the variation reply");
        while ((Boolean) field(panel, "responseAnimationActive")) {
            invoke(panel, "advanceResponseAnimation", new Class<?>[0]);
        }
        assertEquals("6n1/1P5r/3k1b2/R2p1b1p/pp3P2/1n6/7R/7K w - - 0 64",
                invoke(board, "position", new Class<?>[0]),
                "sample settles on the variation branch after the opponent reply");
        PuzzleSession session = (PuzzleSession) field(panel, "session");
        assertEquals("b7b8q", Move.toString(session.expectedMove()),
                "sample variation still asks for the final solver move");
    }

    /**
     * Verifies puzzle review navigation can move before and after the active
     * board position without playing solver moves.
     */
    private static void testPuzzleSessionReviewNavigation() {
        PuzzleSession session = PuzzleSession.fromUciLine("Review", "test", START_FEN,
                List.of("e2e4", "e7e5", "g1f3"), PuzzleSession.VariationMode.MAINLINE);
        assertEquals(Integer.valueOf(0), Integer.valueOf(session.cursor().cursorIndex()),
                "review starts at the root");

        assertTrue(session.navigatePly(1), "review moves forward");
        assertEquals(Integer.valueOf(1), Integer.valueOf(session.cursor().cursorIndex()),
                "review cursor advances one ply");
        assertTrue(session.currentFen().contains(" b "), "review forward shows side to move after e4");
        assertEquals("e2e4", Move.toString(session.currentLastMove()),
                "review exposes the move that produced the displayed board");

        assertTrue(session.navigatePly(-1), "review moves backward");
        assertEquals(Integer.valueOf(0), Integer.valueOf(session.cursor().cursorIndex()),
                "review cursor returns to the root");
        assertEquals(Short.valueOf(Move.NO_MOVE), Short.valueOf(session.currentLastMove()),
                "review root has no last move");
        assertFalse(session.navigatePly(-1), "review clamps at the root");

        assertTrue(session.jumpToPly(session.lastPly()), "review jumps to end");
        assertEquals(Integer.valueOf(3), Integer.valueOf(session.cursor().cursorIndex()),
                "review cursor reaches final ply");
        assertFalse(session.navigatePly(1), "review clamps at the final ply");
    }

    /**
     * Verifies the bundled difficult puzzle CSV is parseable and playable.
     */
    private static void testPuzzleLibraryLoadsDifficultCsv() {
        List<PuzzleLibrary.Entry> entries;
        try {
            entries = PuzzleLibrary.read(Path.of("assets", "puzzles", "difficult-lichess-10k.csv"));
        } catch (java.io.IOException ex) {
            throw new AssertionError("difficult puzzle library loads", ex);
        }
        assertEquals(Integer.valueOf(10_000), Integer.valueOf(entries.size()), "difficult puzzle count");

        PuzzleLibrary.Entry first = entries.get(0);
        assertTrue(first.rating() >= entries.get(entries.size() - 1).rating(), "puzzles sorted by rating");
        PuzzleSession session = PuzzleSession.fromUciLine(first.title(), first.id(), first.fen(), first.moves(),
                PuzzleSession.VariationMode.MAINLINE);
        assertEquals(first.moves().get(0), Move.toString(session.expectedMove()), "first CSV move is expected");
        PuzzleSession.MoveResponse response = session.playUserMove(Move.parse(first.moves().get(0)), false);
        assertEquals(PuzzleSession.StepResult.CORRECT, response.result(), "CSV puzzle first move plays");
        assertTrue(PuzzleLibrary.toPgn(first).contains("[PuzzleRating \"" + first.rating() + "\"]"),
                "CSV puzzle preview includes rating");
    }

    /**
     * Verifies the bundled chess-web PGN library is indexed as a 100k
     * variation-aware puzzle source.
     */
    private static void testPuzzleLibraryLoadsChessWebPgn() {
        assertTrue(Files.isRegularFile(PuzzleLibrary.DEFAULT_PATH), "default chess-web PGN exists");
        List<PuzzleLibrary.Entry> entries;
        try {
            entries = PuzzleLibrary.read(PuzzleLibrary.DEFAULT_PATH);
        } catch (java.io.IOException ex) {
            throw new AssertionError("chess-web PGN puzzle library loads", ex);
        }
        assertEquals(Integer.valueOf(100_000), Integer.valueOf(entries.size()), "chess-web PGN puzzle count");

        PuzzleLibrary.Entry first = entries.get(0);
        assertTrue(first.hasPgnText(), "PGN puzzle keeps source text");
        assertTrue(PuzzleLibrary.toPgn(first).contains("(34. Rxc3"), "PGN puzzle keeps variation movetext");
        PuzzleSession session = PuzzleSession.fromPgn(first.pgnText(), first.id(),
                PuzzleSession.VariationMode.EXPLORE);
        assertEquals("d5c3", Move.toString(session.expectedMove()), "first chess-web PGN move is expected");
    }

    /**
     * Verifies the bundled chess-web puzzle path resolves through the default
     * loader path used by the installed workbench.
     */
    private static void testPuzzleLibraryDefaultPathResolves() {
        Path path = PuzzleLibrary.defaultPath();
        assertTrue(Files.isRegularFile(path), "default chess-web PGN resolves");
        assertEquals(PuzzleLibrary.DEFAULT_PATH.getFileName().toString(), path.getFileName().toString(),
                "resolved default puzzle file name");
    }

    /**
     * Verifies loaded puzzle libraries are shuffled before the first puzzle is
     * selected.
     */
    private static void testPuzzlePanelShufflesLoadedLibrary() {
        List<PuzzleLibrary.Entry> entries = new java.util.ArrayList<>();
        for (int index = 0; index < 12; index++) {
            entries.add(new PuzzleLibrary.Entry(
                    "puzzle-" + index,
                    START_FEN,
                    List.of("e2e4"),
                    0,
                    "",
                    "",
                    "",
                    ""));
        }

        List<PuzzleLibrary.Entry> shuffled = typedList(invokeStatic(
                type("PuzzlePanel"),
                "shuffledLibrary",
                new Class<?>[] { List.class, java.util.Random.class },
                entries,
                new java.util.Random(7L)), PuzzleLibrary.Entry.class);

        assertEquals(Integer.valueOf(entries.size()), Integer.valueOf(shuffled.size()),
                "shuffle preserves puzzle count");
        assertEquals(entries.stream().map(PuzzleLibrary.Entry::id).sorted().toList(),
                shuffled.stream().map(PuzzleLibrary.Entry::id).sorted().toList(),
                "shuffle preserves puzzle identities");
        assertFalse(entries.stream().map(PuzzleLibrary.Entry::id).toList()
                .equals(shuffled.stream().map(PuzzleLibrary.Entry::id).toList()),
                "loaded puzzle library order is shuffled");
    }

    /**
     * Verifies the puzzle panel paints a themed, non-transparent surface.
     */
    private static void testPuzzlePanelPaintsOpaqueSurface() {
        JComponent panel = new PuzzlePanel();
        assertPaintsOpaqueCorner(panel, 720, 520, "puzzle panel opaque background");
    }

    /**
     * Verifies analysis-board exports are rendered from board state instead of
     * relying on a visible component screenshot.
     */
    private static void testAnalysisBoardExportsRasterAndSvg() {
        BoardPanel board = new BoardPanel();
        Position position = new Position(Setup.getStandardStartFEN());
        short move = Move.parse("e2e4");
        position.play(move);
        board.setPositionInstant(position, move);

        BufferedImage image = BoardExporter.renderPng(board, 512);
        assertTrue(image.getWidth() >= 512 && image.getHeight() >= 512,
                "board PNG export uses requested resolution");
        assertTrue((image.getRGB(image.getWidth() / 2, image.getHeight() / 2) >>> 24) != 0,
                "board PNG export paints opaque board content");

        String svg = BoardExporter.toSvg(board, 512);
        assertTrue(svg.startsWith("<svg"), "board SVG export starts with svg root");
        assertTrue(svg.contains("ChessRTK analysis board"), "board SVG export has accessible label");
        assertTrue(svg.contains("<path"), "board SVG export embeds vector piece paths");
    }

    /**
     * Verifies the PGN explorer can index and filter multi-game PGN content.
     */
    private static void testPgnExplorerModelFiltersGames() {
        String pgn = """
                [Event "Training Match"]
                [Site "Berlin"]
                [Date "2026.05.24"]
                [White "Alpha"]
                [Black "Beta"]
                [Result "*"]
                [Opening "Ruy Lopez"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 *

                [Event "Study Set"]
                [Site "Paris"]
                [Date "2026.05.24"]
                [White "Gamma"]
                [Black "Delta"]
                [Result "*"]
                [Opening "Queen's Gambit"]

                1. d4 d5 2. c4 *
                """;
        List<PgnExplorerModel.Entry> entries = PgnExplorerModel.entries(pgn);
        assertEquals(Integer.valueOf(2), Integer.valueOf(entries.size()), "PGN explorer indexes games");
        assertEquals("Alpha", entries.get(0).white(), "PGN database exposes White player");
        assertEquals("Beta", entries.get(0).black(), "PGN database exposes Black player");
        assertEquals("Ruy Lopez", entries.get(0).opening(), "PGN database exposes opening tag");
        assertEquals(Integer.valueOf(5), Integer.valueOf(entries.get(0).plyCount()),
                "PGN database counts mainline plies");
        assertEquals(Integer.valueOf(1), Integer.valueOf(PgnExplorerModel.filter(entries, "alpha ruy").size()),
                "PGN explorer filters by player and opening");
        assertEquals(Integer.valueOf(1), Integer.valueOf(PgnExplorerModel.filter(entries, "study paris").size()),
                "PGN explorer filters by event and site");
        Position afterE4E5 = new Position(START_FEN);
        afterE4E5.play(Move.parse("e2e4"));
        afterE4E5.play(Move.parse("e7e5"));
        assertEquals(Integer.valueOf(1),
                Integer.valueOf(PgnExplorerModel.filterByPosition(entries, afterE4E5.toString()).size()),
                "PGN database filters games reaching current position");
        List<PgnExplorerModel.Entry> duplicated = PgnExplorerModel.entries(pgn + "\n\n" + entries.get(0).pgn());
        assertEquals(Integer.valueOf(1), Integer.valueOf(PgnExplorerModel.duplicateCount(duplicated)),
                "PGN database detects duplicate game rows");
        assertEquals(Integer.valueOf(2), Integer.valueOf(PgnExplorerModel.deduplicate(duplicated).size()),
                "PGN database removes duplicate game rows");
        assertTrue(entries.get(0).pgn().contains("[Event \"Training Match\"]"),
                "PGN explorer serializes selected game");
    }

    /**
     * Verifies prep reports summarize a player's openings, score, and weak
     * lines.
     */
    private static void testPgnPrepReportSummarizesPlayerOpenings() {
        String pgn = """
                [Event "Prep 1"]
                [White "Alpha"]
                [Black "Beta"]
                [Result "1-0"]
                [ECO "C60"]
                [Opening "Ruy Lopez"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 1-0

                [Event "Prep 2"]
                [White "Gamma"]
                [Black "Alpha"]
                [Result "1-0"]
                [ECO "B20"]
                [Opening "Sicilian Defense"]

                1. e4 c5 1-0

                [Event "Prep 3"]
                [White "Alpha"]
                [Black "Delta"]
                [Result "1/2-1/2"]
                [ECO "D06"]
                [Opening "Queen's Gambit"]

                1. d4 d5 2. c4 1/2-1/2
                """;
        String report = PgnPrepReport.report(PgnExplorerModel.entries(pgn), "Alpha");
        assertTrue(report.contains("Player games: 3  score: 1.5/3 (50.0%)"),
                "prep report player score");
        assertTrue(report.contains("As White: 2  As Black: 1"), "prep report color split");
        assertTrue(report.contains("- Ruy Lopez: 1"), "prep report opening count");
        assertTrue(report.contains("- C60: 1"), "prep report ECO count");
        assertTrue(report.contains("- B20 Sicilian Defense: 1 game, 0.0%"),
                "prep report weak-line candidate");
    }

    /**
     * Verifies the ECO explorer filters the bundled book and loads a selected
     * line through its host callback.
     */
    private static void testEcoExplorerFiltersAndLoadsLines() {
        Position root = new Position(START_FEN);
        List<Short> path = List.of(Short.valueOf(Move.parse("e2e4")), Short.valueOf(Move.parse("e7e5")));
        String[] loaded = { null };
        String[] copied = { null };
        Object explorer = construct(type("EcoExplorerPanel"),
                new Class<?>[] {
                        java.util.function.Supplier.class,
                        java.util.function.Supplier.class,
                        java.util.function.Consumer.class,
                        java.util.function.Consumer.class },
                (java.util.function.Supplier<Position>) root::copy,
                (java.util.function.Supplier<List<Short>>) () -> path,
                (java.util.function.Consumer<String>) value -> loaded[0] = value,
                (java.util.function.Consumer<String>) value -> copied[0] = value);

        assertTrue(((Integer) invoke(explorer, "rowCount", new Class<?>[0])).intValue() > 0,
                "ECO explorer has continuations for e4 e5");
        assertTrue(((Integer) invoke(explorer, "treeRowCount", new Class<?>[0])).intValue() > 1,
                "ECO opening tree has continuations for e4 e5");
        assertTrue((Boolean) invoke(explorer, "selectFirstTreeChild", new Class<?>[0]),
                "ECO opening tree selects first continuation");
        assertTrue((Boolean) invoke(explorer, "loadSelectedTreeLine", new Class<?>[0]),
                "ECO opening tree loads selected prefix");
        assertTrue(loaded[0] != null && loaded[0].startsWith("e4 e5"),
                "loaded ECO tree prefix starts at the standard opening root");
        assertTrue((Boolean) invoke(explorer, "copySelectedTreeLine", new Class<?>[0]),
                "ECO opening tree copies selected prefix");
        assertEquals(loaded[0], copied[0], "copied selected ECO tree prefix");
        invoke(explorer, "setFilter", new Class<?>[] { String.class }, "Ruy Lopez");
        assertTrue(((Integer) invoke(explorer, "rowCount", new Class<?>[0])).intValue() > 0,
                "ECO explorer filters Ruy Lopez rows");
        assertTrue((Boolean) invoke(explorer, "selectFirstRow", new Class<?>[0]),
                "ECO explorer selects first row");
        assertTrue((Boolean) invoke(explorer, "loadSelectedLine", new Class<?>[0]),
                "ECO explorer loads selected line");
        assertTrue(loaded[0] != null && loaded[0].contains("Bb5"), "loaded ECO movetext");
        assertTrue((Boolean) invoke(explorer, "copySelectedLine", new Class<?>[0]),
                "ECO explorer copies selected line");
        assertEquals(loaded[0], copied[0], "copied selected ECO movetext");
        JTable table = (JTable) field(explorer, "table");
        Component header = table.getTableHeader().getDefaultRenderer()
                .getTableCellRendererComponent(table, "Line", false, false, -1, 4);
        assertEquals(themeColor("PANEL_SOLID"), header.getBackground(),
                "ECO table header uses workbench background");
        assertEquals(themeColor("MUTED"), header.getForeground(),
                "ECO table header uses workbench foreground");
        JTree tree = (JTree) field(explorer, "openingTree");
        Component treeNode = tree.getCellRenderer().getTreeCellRendererComponent(tree,
                tree.getModel().getRoot(), false, true, false, 0, false);
        assertEquals(themeColor("PANEL_SOLID"), treeNode.getBackground(),
                "ECO tree renderer uses workbench background");
        assertEquals(themeColor("TEXT"), treeNode.getForeground(),
                "ECO tree renderer uses workbench foreground");
        assertTrue(table.getColumnModel().getColumn(1).getCellRenderer() instanceof SanRenderer,
                "ECO next-move column uses inline SAN renderer");
        assertTrue(table.getColumnModel().getColumn(4).getCellRenderer() instanceof SanRenderer,
                "ECO line column uses inline SAN renderer");
        assertTrue(NotationPainter.pieceSvgCount(loaded[0]) > 0,
                "selected ECO movetext contains drawable algebraic piece notation");
        assertPaintsOpaqueCorner((JComponent) explorer, 420, 520, "ECO explorer opaque background");
    }

    /**
     * Verifies post-game review uses mainline snapshots and surfaces tactical
     * static swings.
     */
    private static void testGameReviewPanelFindsStaticSwings() {
        Position root = new Position("4k3/8/8/8/8/8/4q3/4KQ2 w - - 0 1");
        GameModel model = new GameModel();
        model.loadLine(root, List.of(Short.valueOf(Move.parse("f1e2"))));

        List<GameModel.PlySnapshot> snapshots = model.mainlineSnapshots();
        assertEquals(Integer.valueOf(1), Integer.valueOf(snapshots.size()), "review snapshots mainline move");
        assertEquals(root.toString(), snapshots.get(0).beforeFen(), "review snapshot stores before position");
        assertEquals("f1e2", snapshots.get(0).uci(), "review snapshot stores UCI");

        List<GameReviewPanel.ReviewFinding> findings = GameReviewPanel.analyze(model);
        assertEquals(Integer.valueOf(1), Integer.valueOf(findings.size()), "review analyzes mainline move");
        GameReviewPanel.ReviewFinding finding = findings.get(0);
        assertEquals("f1e2", finding.uci(), "review finding keeps move identity");
        assertTrue("Strong".equals(finding.verdict()) || "Good".equals(finding.verdict()),
                "review marks queen capture as a positive swing");
        assertTrue(finding.afterWhiteCp() > finding.beforeWhiteCp(),
                "review stores White-relative evaluation swing");
        assertTrue(finding.summary().contains("Effects:") && finding.summary().contains("Position:"),
                "review explanation includes deterministic tags and position description");
    }

    /**
     * Verifies the review panel can render produced review/study JSONL
     * artifacts without recomputing verdicts from a game model.
     */
    private static void testGameReviewPanelLoadsProducedReviewArtifacts() {
        try {
            ReviewRow row = reviewArtifactFixture();
            StudyUnitFactory.Output artifacts = StudyUnitFactory.fromRows(List.of(row));
            assertEquals(Integer.valueOf(1), Integer.valueOf(artifacts.units().size()),
                    "review fixture emits one study unit");
            Path dir = Files.createTempDirectory("crtk-workbench-review-artifact-");
            Path reviewJsonl = dir.resolve("review.jsonl");
            Path studyJsonl = dir.resolve("study.jsonl");
            Files.writeString(reviewJsonl, row.toJson() + System.lineSeparator());
            Files.writeString(studyJsonl, artifacts.units().get(0).toJson() + System.lineSeparator());

            List<GameReviewPanel.ReviewFinding> rows =
                    GameReviewPanel.loadReviewArtifactRows(reviewJsonl, studyJsonl);
            assertEquals(Integer.valueOf(1), Integer.valueOf(rows.size()),
                    "review artifact row count");
            GameReviewPanel.ReviewFinding finding = rows.get(0);
            assertEquals("d1h5", finding.uci(), "review artifact played move");
            assertEquals("Blunder", finding.verdict(), "review artifact verdict");
            assertEquals(Integer.valueOf(1), Integer.valueOf(finding.ply()),
                    "review artifact display ply is one-based");
            assertEquals(Integer.valueOf(1000), Integer.valueOf(finding.lossCp()),
                    "review artifact loss comes from row");
            assertTrue(finding.summary().contains("Best: Qxd8 (d1d8)"),
                    "review artifact summary includes best move");
            assertTrue(finding.summary().contains("Study unit: artifact-game.p0"),
                    "review artifact summary links study unit");
            assertTrue(finding.summary().contains("Repro: Fake Review UCI, nodes 7"),
                    "review artifact summary preserves repro budgets");

            GameReviewPanel panel = new GameReviewPanel(GameModel::new, ply -> { }, value -> { });
            panel.loadReviewArtifacts(reviewJsonl, studyJsonl);
            assertEquals(Integer.valueOf(1), Integer.valueOf(panel.rowCount()),
                    "review panel loaded artifact rows");
            assertTrue(panel.selectFirstRow(), "review panel selects loaded artifact row");
            JTextArea detail = (JTextArea) field(panel, "detailArea");
            assertTrue(detail.getText().contains("Engine score from mover's view: +500 -> -500"),
                    "review panel detail comes from artifact evals");
        } catch (java.io.IOException ex) {
            throw new AssertionError("review artifact fixture failed", ex);
        }
    }

    /**
     * Verifies the Review panel exposes the Lichess-like analysis tabs and
     * notation/export panes.
     */
    private static void testGameReviewPanelUsesLichessReviewModes() {
        try {
            Position root = new Position("k2r4/8/8/8/8/8/8/3QK3 w - - 0 1");
            GameModel model = new GameModel();
            model.loadLine(root, List.of(Short.valueOf(Move.parse("d1h5"))));
            ReviewRow row = reviewArtifactFixture();
            StudyUnitFactory.Output artifacts = StudyUnitFactory.fromRows(List.of(row));
            Path dir = Files.createTempDirectory("crtk-workbench-lichess-review-");
            Path reviewJsonl = dir.resolve("review.jsonl");
            Path studyJsonl = dir.resolve("study.jsonl");
            Files.writeString(reviewJsonl, row.toJson() + System.lineSeparator());
            Files.writeString(studyJsonl, artifacts.units().get(0).toJson() + System.lineSeparator());

            GameReviewPanel panel = new GameReviewPanel(() -> model, ply -> { }, value -> { });
            panel.loadReviewArtifacts(reviewJsonl, studyJsonl);
            assertEquals(List.of("Computer analysis", "Move times", "Crosstable", "Share & export"),
                    panel.reviewModeLabels(), "review panel exposes Lichess-style mode labels");
            assertTrue(panel.selectReviewMode(0), "review panel selects analysis mode");
            assertTrue(panel.selectReviewMode(1), "review panel selects move-times mode");
            assertTrue(panel.selectReviewMode(2), "review panel selects crosstable mode");
            assertTrue(panel.selectReviewMode(3), "review panel selects export mode");
            assertTrue(panel.exportText().contains("Qh5"), "review export keeps PGN text");
            assertTrue(panel.notationText().contains("Blunder"), "review notation includes verdict");
            assertTrue(panel.notationText().contains("Qxd8 was best"), "review notation includes best move");
            panel.setSize(520, 560);
            panel.doLayout();
            BufferedImage image = paint(panel, 520, 560);
            assertTrue(alphaSum(image, 0, 0, image.getWidth(), image.getHeight()) > 0,
                    "Lichess-style review panel paints");
        } catch (java.io.IOException ex) {
            throw new AssertionError("Lichess review fixture failed", ex);
        }
    }

    /**
     * Verifies the Review panel can produce study artifacts through the real
     * {@code crtk review game --to-study} CLI path and render the result.
     */
    private static void testGameReviewPanelBuildsStudyArtifactsViaCli() {
        Position root = new Position("k2r4/8/8/8/8/8/8/3QK3 w - - 0 1");
        GameModel model = new GameModel();
        model.loadLine(root, List.of(Short.valueOf(Move.parse("d1h5"))));
        GameReviewPanel panel = new GameReviewPanel(() -> model, ply -> { }, value -> { },
                ReviewCliArtifactProducer.offline());
        assertTrue(panel.buildStudyArtifactsFromGame(), "review panel starts CLI artifact production");
        awaitReviewRows(panel, 1, "review panel CLI artifact rows");
        assertEquals(Integer.valueOf(1), Integer.valueOf(panel.rowCount()),
                "review panel loaded CLI artifact rows");
        assertTrue(panel.selectFirstRow(), "review panel selects CLI artifact row");
        JTextArea detail = (JTextArea) field(panel, "detailArea");
        assertTrue(detail.getText().contains("Study unit:"),
                "review panel CLI detail links study unit");
        assertTrue(detail.getText().contains("Repro: offline-alpha-beta"),
                "review panel CLI detail preserves offline repro");
    }

    /**
     * Verifies local Workbench game storage round-trips enough state to resume.
     */
    private static void testSavedGameStoreRoundTripsAndValidatesMoves() {
        try {
            Path dir = Files.createTempDirectory("crtk-workbench-saved-games-");
            SavedGameStore store = new SavedGameStore(dir.resolve("games.jsonl"));
            GameModel model = new GameModel();
            Position cursor = new Position(START_FEN);
            for (String uci : List.of("e2e4", "e7e5")) {
                short move = Move.parse(uci);
                Position next = cursor.copy();
                next.play(move);
                model.append(cursor, move, next);
                cursor = next;
            }

            SavedGame saved = SavedGame.capture("saved-fixture", 10L, 20L, "In progress", model);
            store.save(saved);
            List<SavedGame> loaded = store.load();
            assertEquals(Integer.valueOf(1), Integer.valueOf(loaded.size()), "saved game row count");
            SavedGame roundTrip = loaded.get(0);
            assertEquals("saved-fixture", roundTrip.id(), "saved game id");
            assertEquals("e2e4 e7e5", roundTrip.uciLine(), "saved game UCI line");
            assertEquals(Integer.valueOf(2), Integer.valueOf(roundTrip.currentPly()),
                    "saved game current ply");
            assertTrue(roundTrip.pgn().contains("1. e4 e5"), "saved game keeps PGN export");

            List<Short> moves = SavedGameStore.parseUciLine(new Position(roundTrip.startFen()),
                    roundTrip.uciLine());
            assertEquals(Integer.valueOf(2), Integer.valueOf(moves.size()), "saved game replay move count");
            assertEquals("e2e4", Move.toString(moves.get(0).shortValue()), "saved game first move");

            boolean rejected = false;
            try {
                SavedGameStore.parseUciLine(new Position(roundTrip.startFen()), "e2e5");
            } catch (IllegalArgumentException ex) {
                rejected = true;
            }
            assertTrue(rejected, "saved game replay rejects illegal UCI");
        } catch (java.io.IOException ex) {
            throw new AssertionError("saved-game fixture failed", ex);
        }
    }

    /**
     * Verifies the Workbench library persists current and imported PGN games.
     */
    private static void testGameLibraryStoresCurrentAndImportedPgnGames() {
        try {
            Path dir = Files.createTempDirectory("crtk-workbench-game-library-");
            GameLibrary library = new GameLibrary(dir.resolve("pgn-store"));
            GameModel model = new GameModel();
            Position cursor = new Position(START_FEN);
            for (String uci : List.of("e2e4", "e7e5", "g1f3")) {
                short move = Move.parse(uci);
                Position next = cursor.copy();
                next.play(move);
                model.append(cursor, move, next);
                cursor = next;
            }

            chess.pgn.PgnStore.ImportReport saved = library.saveCurrent(model, "Workbench Saved");
            assertEquals(Integer.valueOf(1), Integer.valueOf(saved.imported()),
                    "library imports current game");
            assertEquals(Integer.valueOf(1), Integer.valueOf(library.recent(10).size()),
                    "library current game row count");
            GameLibrary.Entry current = library.recent(10).get(0);
            assertTrue(current.pgn().contains("1. e4 e5 2. Nf3"),
                    "library stores current game PGN");
            assertTrue(current.searchableText().contains("workbench saved"),
                    "library search includes source");

            Path pgnFile = dir.resolve("import.pgn");
            Files.writeString(pgnFile, """
                    [Event "Imported Fixture"]
                    [Site "?"]
                    [White "Ada"]
                    [Black "Grace"]
                    [Result "*"]

                    1. d4 d5 *
                    """);
            chess.pgn.PgnStore.ImportReport imported = library.importPgn(pgnFile);
            assertEquals(Integer.valueOf(1), Integer.valueOf(imported.imported()),
                    "library imports PGN file");
            List<GameLibrary.Entry> rows = library.recent(10);
            assertEquals(Integer.valueOf(2), Integer.valueOf(rows.size()),
                    "library imported row count");
            assertEquals("Imported Fixture", rows.get(0).event(), "newest imported game first");
            assertTrue(rows.get(0).searchableText().contains("ada"),
                    "library search includes player tags");
        } catch (java.io.IOException ex) {
            throw new AssertionError("game-library fixture failed", ex);
        }
    }

    /**
     * Builds a produced-row fixture matching the review-to-study contract.
     *
     * @return review row with a stable study-unit id
     */
    private static ReviewRow reviewArtifactFixture() {
        String fen = "k2r4/8/8/8/8/8/8/3QK3 w - - 0 1";
        Classifier.Verdict verdict = Classifier.classify(new Request(
                Score.withWinShare(500, 0.90d),
                Score.withWinShare(-500, 0.01d),
                Score.centipawns(200),
                Thresholds.classical(),
                false));
        ReviewRow row = new ReviewRow(
                new GameRef("artifact-game", "pgn:fixture.pgn#1",
                        "Review Artifact", "Tester", "Fixture", null, null),
                new Ply(0, 1, ReviewRow.Color.WHITE, fen),
                new MoveChoice("d1h5", "Qh5", "d1d8", "Qxd8",
                        List.of("d1d8"), Integer.valueOf(200)),
                new Assessment(
                        new Eval(500, null, new Wdl(900, 90, 10)),
                        new Eval(-500, null, new Wdl(10, 90, 900)),
                        verdict,
                        "hanging_piece"),
                new Tags(
                        List.of("FACT: phase=endgame"),
                        List.of("THREAT: type=hanging piece=qh5", "TACTIC: motif=hanging_piece"),
                        List.of("THREAT: type=hanging piece=qh5", "TACTIC: motif=hanging_piece"),
                        List.of(),
                        List.of()),
                "endgame",
                "drill_puzzle",
                "artifact-game.p0",
                new Repro("Fake Review UCI", "config/review.engine.toml", 7L, 2_000L, 2, 1, 16,
                        "uci", "test", false));
        return StudyUnitFactory.withStudyUnitId(row);
    }

    /**
     * Waits for the asynchronous review panel command to populate rows.
     *
     * @param panel review panel
     * @param expectedRows expected minimum row count
     * @param label assertion label
     */
    private static void awaitReviewRows(GameReviewPanel panel, int expectedRows, String label) {
        long deadline = System.currentTimeMillis() + 20_000L;
        while (System.currentTimeMillis() < deadline) {
            flushEdt();
            if (panel.rowCount() >= expectedRows) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(label + ": interrupted", ex);
            }
        }
        throw new AssertionError(label + ": timed out with row count " + panel.rowCount());
    }

    /**
     * Verifies the local study repository seeds one empty PGN-backed study book.
     */
    private static void testStudyRepositoryCreatesStarterPgnBook() {
        try {
            Path dir = Files.createTempDirectory("crtk-study-repository-");
            StudyRepository repository = new StudyRepository(dir);
            assertEquals(Integer.valueOf(0), Integer.valueOf(repository.count()),
                    "new study repository starts empty");
            Path starter = repository.ensureStarterStudy();
            assertTrue(Files.isRegularFile(starter), "starter study PGN is written");
            String pgn = Files.readString(starter);
            assertTrue(pgn.contains("[Event \"Workbench Study\"]"),
                    "starter study has a readable event title");
            assertTrue(pgn.strip().endsWith("*"), "starter study is an unfinished PGN chapter");
            List<StudyRepository.StudyBook> studies = repository.studies();
            assertEquals(Integer.valueOf(1), Integer.valueOf(studies.size()),
                    "repository lists the starter study");
            assertEquals("Workbench Study", studies.get(0).title(), "starter study title");
            assertEquals(Integer.valueOf(1), Integer.valueOf(studies.get(0).chapters()),
                    "starter study exposes one PGN chapter");
            assertTrue(studies.get(0).empty(), "starter study chapter has no moves");
        } catch (java.io.IOException ex) {
            throw new AssertionError("study repository setup failed", ex);
        }
    }

    /**
     * Verifies graphical PGN comments preserve text and round-trip shapes.
     */
    private static void testStudyShapeCommentCodecRoundTrips() {
        ShapeCommentCodec.DecodedComment decoded = ShapeCommentCodec.parse(
                "Critical square [%csl Gb4,Yd5,Rf6] with tactic [%cal Ge2e4,Ye2d4,Re2g4]");
        assertEquals("Critical square with tactic", decoded.text(),
                "shape codec preserves non-shape comment text");
        assertEquals(Integer.valueOf(6), Integer.valueOf(decoded.shapes().size()),
                "shape codec parses circles and arrows");
        String rendered = ShapeCommentCodec.render(decoded);
        assertTrue(rendered.contains("[%csl Gb4,Yd5,Rf6]"),
                "shape codec renders circles");
        assertTrue(rendered.contains("[%cal Ge2e4,Ye2d4,Re2g4]"),
                "shape codec renders arrows");
        ShapeCommentCodec.DecodedComment reparsed = ShapeCommentCodec.parse(rendered);
        assertEquals(decoded.shapes(), reparsed.shapes(), "shape codec round-trips shape list");
    }

    /**
     * Verifies drawn board annotations (arrows, circles, glyph badges, rectangles) round-trip
     * through PGN comment commands: Lichess [%cal]/[%csl] plus crtk [%cgl]/[%crl] extensions.
     */
    private static void testBoardMarkupCommentRoundTrips() {
        List<BoardMarkup> markups = List.of(
                new BoardMarkup(BoardMarkupTool.ARROW, Field.toIndex("e2"), Field.toIndex("e4"),
                        MarkupBrush.forColor(new Color(0x81, 0xB6, 0x4C))),
                new BoardMarkup(BoardMarkupTool.CIRCLE, Field.toIndex("d5"), Field.NO_SQUARE,
                        MarkupBrush.forColor(new Color(0xD9, 0x51, 0x4E))),
                new BoardMarkup(BoardMarkupTool.GLYPH, Field.toIndex("g8"), Field.NO_SQUARE,
                        MarkupBrush.custom(new Color(0x6F, 0x52, 0xCC), Color.WHITE, 10, 4, AnnotationGlyphs.FORK)),
                new BoardMarkup(BoardMarkupTool.RECTANGLE, Field.toIndex("a1"), Field.toIndex("c3"),
                        MarkupBrush.custom(new Color(0xF0, 0xB1, 0x3A), 10)));

        String comment = BoardMarkupComment.encode("a good plan", markups);
        assertTrue(comment.contains("[%cal Ge2e4]"), "arrow encodes as a Lichess [%cal] directive");
        assertTrue(comment.contains("[%csl Rd5]"), "circle encodes as a Lichess [%csl] directive");
        assertTrue(comment.contains("[%cgl g8/" + AnnotationGlyphs.FORK + "/6F52CC]"),
                "glyph badge encodes as a crtk [%cgl] directive with token and colour");
        assertTrue(comment.contains("[%crl a1/c3/F0B13A]"), "rectangle encodes as a crtk [%crl] directive");
        assertEquals("a good plan", BoardMarkupComment.plainText(comment),
                "free-text is preserved alongside the directives");

        List<BoardMarkup> decoded = BoardMarkupComment.decode(comment);
        assertEquals(Integer.valueOf(4), Integer.valueOf(decoded.size()), "all four markups decode");
        BoardMarkup glyph = decoded.stream().filter(BoardMarkup::isGlyph).findFirst().orElseThrow();
        assertEquals(AnnotationGlyphs.FORK, glyph.brush().glyph(), "glyph token survives the round-trip");
        assertEquals("g8", Field.toString(glyph.from()), "glyph square survives the round-trip");
        assertEquals(new Color(0x6F, 0x52, 0xCC), glyph.brush().displayColor(),
                "glyph colour survives the round-trip");
        BoardMarkup rect = decoded.stream().filter(BoardMarkup::isRectangle).findFirst().orElseThrow();
        assertEquals("a1", Field.toString(rect.from()), "rectangle origin square survives");
        assertEquals("c3", Field.toString(rect.to()), "rectangle corner square survives");
    }

    /**
     * Verifies the local study tree edits full PGN trees without truncating
     * variations, comments, NAGs, or shapes.
     */
    private static void testStudyTreeModelEditsFullPgnTree() {
        StudyTreeModel tree = new StudyTreeModel(new Game());
        StudyNodePath e4 = tree.addLegalMove("e2e4");
        StudyNodePath e5 = tree.addLegalMove("e7e5");
        tree.select(e4);
        StudyNodePath c5 = tree.addLegalMove("c7c5");
        tree.select(e4);
        StudyNodePath nf6 = tree.addLegalMove("g8f6");

        tree.setCommentBefore(e4, "Root idea");
        tree.setCommentAfter(e4, "Take the center");
        tree.setShapes(e4, List.of(
                StudyShape.arrow(StudyShape.Brush.GREEN, "e2", "e4"),
                StudyShape.circle(StudyShape.Brush.YELLOW, "d5")));
        tree.toggleNag(e4, 1);
        tree.toggleNag(e4, 2);
        tree.toggleNag(e4, 146);
        tree.toggleNag(e4, 32);
        List<Integer> e4Nags = tree.rows().stream()
                .filter(row -> row.path().equals(e4))
                .findFirst()
                .orElseThrow()
                .nags();
        String annotated = tree.toPgn();
        assertTrue(annotated.contains("$2"), "study tree keeps latest move assessment NAG");
        assertFalse(e4Nags.contains(Integer.valueOf(1)), "study tree enforces one move assessment");
        assertTrue(annotated.contains("$146") && annotated.contains("$32"),
                "study tree allows multiple observation NAGs");
        assertTrue(annotated.contains("[%csl Yd5]"), "study tree stores circles in PGN comments");
        assertTrue(annotated.contains("[%cal Ge2e4]"), "study tree stores arrows in PGN comments");
        assertTrue(annotated.contains("(1... c5)") && annotated.contains("(1... Nf6)"),
                "study tree keeps sibling variations");

        tree.select(c5);
        assertTrue(tree.promoteVariation(), "study tree promotes selected variation");
        StudyTreeModel.Row c5Row = tree.rows().stream()
                .filter(row -> row.path().equals(c5))
                .findFirst()
                .orElseThrow();
        StudyTreeModel.Row e5Row = tree.rows().stream()
                .filter(row -> row.path().equals(e5))
                .findFirst()
                .orElseThrow();
        assertTrue(c5Row.mainline() && !e5Row.mainline(),
                "promoted variation becomes mainline and old line becomes variation");

        tree.select(nf6);
        assertTrue(tree.deleteBranch(), "study tree deletes selected branch");
        assertFalse(tree.toPgn().contains("Nf6"), "deleted branch is removed from PGN");

        Game roundTrip = Pgn.parseGame(tree.toPgn());
        assertTrue(Pgn.toPgn(roundTrip).contains("[%cal Ge2e4]"),
                "study tree save/reopen preserves graphical annotations");
        assertEquals("Take the center", new StudyTreeModel(roundTrip).commentAfter(e4),
                "study tree save/reopen preserves comments");
    }

    /**
     * Verifies StudyRepository saves and reopens PGN-backed projects with sidecar
     * chapter metadata.
     */
    private static void testStudyRepositorySavesPgnBackedProject() {
        try {
            Path dir = Files.createTempDirectory("crtk-study-crud-");
            StudyRepository repository = new StudyRepository(dir);
            StudyProject project = repository.createStudy("Local Study");
            Game imported = Pgn.parseGame("""
                    [Event "Chapter Import"]
                    [Result "*"]

                    {Root note} 1. e4 $1 {Center [%cal Ge2e4]} (1. d4 d5) e5 *
                    """);
            StudyChapter chapter = repository.addChapterFromGame(project, "Imported Chapter", imported);
            chapter.setMode(StudyChapterMode.PRACTICE);
            chapter.setOrientation("black");
            chapter.setDescription("Practice the imported chapter.");
            repository.addChapterFromFen(project, "FEN Chapter", "8/8/8/8/8/8/8/K6k w - - 0 1");
            repository.saveStudy(project);

            StudyProject reopened = repository.openStudy(project.pgnPath());
            assertEquals(Integer.valueOf(3), Integer.valueOf(reopened.chapters().size()),
                    "study repository reopens all chapters");
            StudyChapter reopenedImported = reopened.chapter(chapter.id()).orElseThrow();
            assertEquals(StudyChapterMode.PRACTICE, reopenedImported.mode(),
                    "study repository preserves sidecar mode");
            assertEquals("black", reopenedImported.orientation(),
                    "study repository preserves sidecar orientation");
            assertTrue(Pgn.toPgn(reopenedImported.game()).contains("(1. d4 d5)"),
                    "study repository preserves PGN variations");
            assertTrue(Pgn.toPgn(reopenedImported.game()).contains("[%cal Ge2e4]"),
                    "study repository preserves graphical comments");
            assertTrue(Files.exists(project.pgnPath().resolveSibling("local-study.crtk-study.json")),
                    "study repository writes sidecar metadata");
        } catch (java.io.IOException ex) {
            throw new AssertionError("study repository CRUD failed", ex);
        }
    }

    /**
     * Verifies study-unit JSONL imports become PGN-backed chapters with the
     * reviewed move kept as an annotated variation.
     */
    private static void testStudyRepositoryImportsReviewStudyUnits() {
        try {
            Path dir = Files.createTempDirectory("crtk-study-review-import-");
            StudyRepository repository = new StudyRepository(dir);
            StudyProject project = repository.createStudy("Review Import Study");
            String parentFen = "7k/8/8/8/8/8/4P3/4K3 w - - 0 1";
            Path jsonl = dir.resolve("unit.study.jsonl");
            Files.writeString(jsonl, """
                    {"schemaVersion":"%s","id":"review-unit-001","game_id":"g1","source":"fixture","event":"Training","white":"White","black":"Black","ply":12,"move_number":7,"color":"white","parent_fen":"%s","position_fen":"7k/8/8/8/4P3/8/8/4K3 b - - 0 1","played_uci":"e2e3","played_san":"e3","best_uci":"e2e4","best_san":"e4","refutation_line":["e2e4","h8g8"],"mistake_category":"blunder","mistake_motif":"tactic","recommended_action":"drill_puzzle","severity":0.75,"cp_loss":420,"wdl_loss":0.2,"difficulty":"hard","tags":["tactical","META: study_unit_id=review-unit-001"],"repro":{"engine":"fixture","protocol_path":null,"max_nodes":1,"max_duration_ms":0,"multipv":1,"threads":1,"hash":16,"search_mode":"offline","crtk_version":"test","deterministic":true}}
                    """.formatted(StudyUnit.SCHEMA_VERSION, parentFen));

            assertEquals(Integer.valueOf(1), Integer.valueOf(repository.importStudyUnitsFromJsonl(project, jsonl)),
                    "study repository imports one review study-unit row");
            StudyChapter imported = project.chapters().get(project.chapters().size() - 1);
            String pgn = Pgn.toPgn(imported.game());
            assertTrue(pgn.contains("[FEN \"" + parentFen + "\"]"),
                    "review import roots chapter at parent FEN");
            assertTrue(pgn.contains("1. e4 $1"),
                    "review import uses best move as mainline");
            assertTrue(pgn.contains("Kg8"),
                    "review import keeps refutation continuation");
            assertTrue(pgn.contains("(1. e3 $4"),
                    "review import keeps played blunder as variation");
            assertTrue(pgn.contains("category blunder") && pgn.contains("difficulty hard"),
                    "review import carries verdict metadata into comments");
            repository.saveStudy(project);
            StudyProject reopened = repository.openStudy(project.pgnPath());
            assertTrue(Pgn.toPgn(reopened.chapters().get(reopened.chapters().size() - 1).game()).contains("$4"),
                    "review import survives save and reopen");
        } catch (java.io.IOException ex) {
            throw new AssertionError("study-unit import failed", ex);
        }
    }

    /**
     * Verifies the Study Workspace can be constructed and painted headlessly.
     */
    private static void testStudyWorkspacePanelSmoke() {
        try {
            Path dir = Files.createTempDirectory("crtk-study-workspace-");
            List<String> positions = new ArrayList<>();
            StudyWorkspacePanel panel = new StudyWorkspacePanel(new StudyRepository(dir),
                    GameModel::new, () -> START_FEN, position -> positions.add(position.toString()),
                    value -> { });
            assertEquals("Study Workspace", panel.getAccessibleContext().getAccessibleName(),
                    "study workspace has accessible name");
            assertEquals(Integer.valueOf(1), Integer.valueOf(panel.chapterCount()),
                    "study workspace starts with one chapter");
            panel.setSize(900, 620);
            panel.doLayout();
            BufferedImage image = paint(panel, 900, 620);
            assertTrue(alphaSum(image, 0, 0, 900, 620) > 0,
                    "study workspace paints a non-empty surface");
            assertTrue(panel.exportPgnText().contains("[Event \"Workbench Study\"]"),
                    "study workspace exports PGN text");
            assertFalse(positions.isEmpty(), "study workspace publishes selected board position");
        } catch (java.io.IOException ex) {
            throw new AssertionError("study workspace smoke failed", ex);
        }
    }

    /**
     * Flushes pending event-dispatch-thread work.
     */
    private static void flushEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ex) {
            throw new AssertionError("failed to flush Swing event queue", ex);
        }
    }

    /**
     * Verifies study authoring exports the current game line as aligned
     * figure arrays.
     */
    private static void testStudyAuthorPanelBuildsManifestFromGameLine() {
        GameModel model = new GameModel();
        model.loadLine(new Position(START_FEN), List.of(
                Short.valueOf(Move.parse("e2e4")),
                Short.valueOf(Move.parse("e7e5"))));
        StudyAuthorPanel panel = new StudyAuthorPanel(() -> model, () -> START_FEN, value -> { });
        StudyAuthorPanel.StudyDraft draft = panel.draft();
        assertEquals(Integer.valueOf(3), Integer.valueOf(draft.figureFens().size()),
                "study draft includes start plus two moves");
        assertEquals("Start", draft.figureMovesAlgebraic().get(0), "study draft start label");
        assertEquals("e2e4", draft.figureArrows().get(1), "study draft first arrow");
        assertEquals("e7e5", draft.figureArrows().get(2), "study draft second arrow");
        String toml = StudyAuthorPanel.buildStudyToml(draft);
        assertTrue(toml.contains("[[compositions]]"), "study TOML has composition array");
        assertTrue(toml.contains("figureFens = ["), "study TOML has FEN array");
        assertTrue(toml.contains("figureArrows = ["), "study TOML has arrow array");
        assertTrue(toml.contains("1. e4"), "study TOML has SAN label");
    }

    /**
     * Verifies newly added GUI surfaces expose useful accessible names.
     */
    private static void testAccessibilityLabelsForNewWorkbenchPanels() {
        GameModel model = new GameModel();
        GameReviewPanel review = new GameReviewPanel(() -> model, ply -> { }, value -> { });
        JTable reviewTable = (JTable) field(review, "table");
        assertEquals("Post-game review findings", reviewTable.getAccessibleContext().getAccessibleName(),
                "review table accessible name");

        StudyAuthorPanel study = new StudyAuthorPanel(() -> model, () -> START_FEN, value -> { });
        JTextArea manifest = (JTextArea) field(study, "manifestArea");
        assertEquals("Generated study TOML", manifest.getAccessibleContext().getAccessibleName(),
                "study manifest accessible name");

        TablebasePanel tablebase = new TablebasePanel(() -> START_FEN, value -> { });
        JTextArea output = (JTextArea) field(tablebase, "outputArea");
        assertEquals("Endgame engine output", output.getAccessibleContext().getAccessibleName(),
                "tablebase output accessible name");

        EngineGauntletPanel gauntlet = new EngineGauntletPanel(value -> { });
        JTextArea command = (JTextArea) field(gauntlet, "commandArea");
        assertEquals("Gauntlet command preview", command.getAccessibleContext().getAccessibleName(),
                "gauntlet command accessible name");
    }

    /**
     * Verifies eval-bar score formatting and white-share mapping.
     */
    private static void testEvalBarMapping() {
        assertEquals("+0.42", formatCentipawns(42), "positive eval format");
        assertEquals("-0.42", formatCentipawns(-42), "negative eval format");
        assertClose(0.5, whiteShareForCentipawns(0), 0.0001, "neutral eval mapping");
        assertTrue(whiteShareForCentipawns(120) > 0.5, "positive eval mapping");
        assertTrue(whiteShareForCentipawns(-120) < 0.5, "negative eval mapping");
    }

    /**
     * Verifies eval-bar score changes use a bounded smooth transition.
     */
    private static void testEvalBarAnimation() {
        JComponent component = (JComponent) construct(type("EvalBar"), new Class<?>[0]);
        assertTrue(component.getPreferredSize().width <= 24, "eval bar is visually thin");
        assertClose(0.0, evalEase(0.0), 0.0001, "eval ease start");
        assertClose(0.5, evalEase(0.5), 0.0001, "eval ease midpoint");
        assertClose(1.0, evalEase(1.0), 0.0001, "eval ease end");
        assertTrue(evalEase(0.75) > evalEase(0.25), "eval ease monotonic");

        Object bar = component;
        invoke(bar, "setCentipawns", new Class<?>[] { int.class }, 250);
        Timer timer = (Timer) field(bar, "timer");
        assertTrue(timer.isRunning(), "eval score animation starts");
        timer.stop();
    }

    /**
     * Verifies the eval-bar rail border remains visible in light and dark mode.
     */
    private static void testEvalBarFrameFollowsTheme() {
        Theme.setMode(Theme.Mode.LIGHT);
        JComponent component = (JComponent) construct(type("EvalBar"), new Class<?>[0]);
        component.setSize(40, 260);
        Color lightFrame = new Color(paint(component, 40, 260).getRGB(20, 0), true);
        assertColor(themeColor("EVAL_FRAME"), lightFrame, "light eval frame paint");

        Theme.setMode(Theme.Mode.DARK);
        Theme.refreshComponentTree(component);
        component.setSize(40, 260);
        Color darkFrame = new Color(paint(component, 40, 260).getRGB(20, 0), true);
        assertColor(themeColor("EVAL_FRAME"), darkFrame, "dark eval frame paint");
        assertColorDistanceAtLeast(darkFrame, themeColor("BG"), 180.0,
                "dark eval frame contrasts with dark workbench background");
        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies the pending engine state does not start a loading animation.
     */
    private static void testEvalBarThinkingIsStatic() {
        Object bar = construct(type("EvalBar"), new Class<?>[0]);
        invoke(bar, "setThinking", new Class<?>[0]);
        Timer timer = (Timer) field(bar, "timer");
        assertFalse(timer.isRunning(), "eval thinking timer stopped");
        assertEquals("", field(bar, "label"), "eval thinking label hidden");
    }

    /**
     * Verifies eval parsing from engine analyze output.
     */
    private static void testEngineEvalParsing() {
        Object cp = parseEngineEval("PV1\n  eval: +42\n");
        assertTrue(cp != null, "centipawn eval parsed");
        assertFalse(engineEvalMate(cp), "centipawn eval is not mate");
        assertEquals(Integer.valueOf(42), Integer.valueOf(engineEvalValue(cp)), "centipawn eval value");

        Object mate = parseEngineEval("PV1\n  eval: #-3\n");
        assertTrue(mate != null, "mate eval parsed");
        assertTrue(engineEvalMate(mate), "mate eval flag");
        assertEquals(Integer.valueOf(-3), Integer.valueOf(engineEvalValue(mate)), "mate eval value");
    }

    /**
     * Verifies the Endgame panel command and tablebase-hit summary parser.
     */
    private static void testTablebasePanelSummarizesEngineOutput() {
        String fen = "8/8/8/8/8/8/4K2R/k7 w - - 0 1";
        List<String> args = TablebasePanel.analyzeArgs(fen);
        assertEquals(List.of("engine", "analyze"), args.subList(0, 2),
                "tablebase panel uses engine analyze");
        assertEquals(fen, valueAfterFlag(args, "--fen"), "tablebase command uses current FEN");
        assertEquals("8", valueAfterFlag(args, "--multipv"), "tablebase command requests winning moves");
        assertEquals("100000", valueAfterFlag(args, "--max-nodes"), "tablebase command is fixed-node");
        assertTrue(hasFlag(args, "--wdl"), "tablebase command requests WDL");

        String output = """
                PV1
                  eval: #1
                  tablebase hits: 2
                  wdl: 1000/0/0  bound: exact
                  best: h1h8 (Rh8#)
                PV2
                  eval: +0
                  tablebase hits: 1
                """;
        TablebasePanel.Summary summary = TablebasePanel.summarize(fen, output);
        assertEquals(Integer.valueOf(3), Integer.valueOf(summary.pieces()), "tablebase piece count");
        assertTrue(summary.eligible(), "tablebase position is eligible");
        assertEquals(Long.valueOf(3), Long.valueOf(summary.tablebaseHits()), "tablebase hit count");
        assertEquals(Integer.valueOf(2), Integer.valueOf(summary.pvCount()), "tablebase PV count");
        assertEquals("h1h8 (Rh8#)", summary.bestMove(), "tablebase best move");
        assertEquals("Tablebase hit reported", summary.verdict(), "tablebase verdict");
    }

    /**
     * Verifies streamed live-engine updates produce compact board status text.
     */
    private static void testLiveEngineStatusFormatting() {
        Output cp = new Output("info depth 12 multipv 1 score cp 42 nodes 100 pv e2e4 e7e5");
        String status = (String) invokeStatic(type("Window"), "formatLiveEngineStatus",
                new Class<?>[] { Output.class, short.class }, cp, Move.parse("e2e4"));
        assertEquals("live d12 +42 e2e4", status, "live centipawn status");

        Output mate = new Output("info depth 9 multipv 1 score mate -3 nodes 100 pv g1f3");
        String mateStatus = (String) invokeStatic(type("Window"), "formatLiveEngineStatus",
                new Class<?>[] { Output.class, short.class }, mate, Move.parse("g1f3"));
        assertEquals("live d9 #-3 g1f3", mateStatus, "live mate status");
    }

    /**
     * Verifies live-engine numeric settings reject non-positive values.
     */
    private static void testOptionalPositiveIntegerParsing() {
        JTextField blank = new JTextField(" ");
        assertEquals(null, invokeStatic(type("Window"), "optionalPositiveInteger",
                new Class<?>[] { JTextField.class, String.class }, blank, "--hash"), "blank optional integer");

        JTextField valid = new JTextField("128");
        assertEquals(Integer.valueOf(128), invokeStatic(type("Window"), "optionalPositiveInteger",
                new Class<?>[] { JTextField.class, String.class }, valid, "--hash"), "valid optional integer");

        try {
            invokeStatic(type("Window"), "optionalPositiveInteger",
                    new Class<?>[] { JTextField.class, String.class }, new JTextField("0"), "--hash");
            throw new AssertionError("zero optional integer rejected");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("positive integer"), "invalid optional integer message");
        }
    }

    /**
     * Verifies the analysis graph stores live samples and formats the latest eval.
     */
    private static void testAnalysisGraphStoresSamples() {
        Object graph = construct(type("AnalysisGraph"), new Class<?>[0]);
        invoke(graph, "resetForPosition", new Class<?>[] { String.class }, START_FEN);
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 12 multipv 1 score cp 42 nodes 1000 nps 5000 pv e2e4 e7e5"),
                Move.parse("e2e4"));
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 13 multipv 1 nodes 2200 pv e2e4 e7e5"),
                Move.parse("e2e4"));
        assertEquals(Integer.valueOf(2), invoke(graph, "sampleCount", new Class<?>[0]),
                "analysis graph sparse sample count");
        assertEquals("+0.42", invoke(graph, "latestEvalLabel", new Class<?>[0]),
                "analysis graph eval label");
    }

    /**
     * Verifies completed engine analyze output populates graph samples.
     */
    private static void testAnalysisGraphStoresCompletedCommandOutput() {
        Object graph = construct(type("AnalysisGraph"), new Class<?>[0]);
        invoke(graph, "resetForPosition", new Class<?>[] { String.class }, START_FEN);
        String output = """
                Engine: stockfish
                FEN: rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1

                PV1
                  eval: +42
                  depth: 12 (sel 20)
                  nodes: 1,000  nps: 5,000  time: 90ms
                  best: e2e4 (e4)
                """;
        assertTrue((Boolean) invoke(graph, "addCommandOutput",
                new Class<?>[] { boolean.class, String.class }, Boolean.TRUE, output),
                "completed analyze output adds graph sample");
        assertEquals(Integer.valueOf(1), invoke(graph, "sampleCount", new Class<?>[0]),
                "completed analyze output sample count");
        assertEquals("+0.42", invoke(graph, "latestEvalLabel", new Class<?>[0]),
                "completed analyze output eval label");
        String csv = (String) invoke(graph, "csvText", new Class<?>[0]);
        assertTrue(csv.contains("e2e4"), "completed analyze output stores best move");
        assertTrue(csv.contains(",1000,5000,90,"), "completed analyze output stores work metrics");
    }

    /**
     * Verifies graph data can be exported for reports and downstream analysis.
     */
    private static void testAnalysisGraphExportsReportData() {
        Object graph = construct(type("AnalysisGraph"), new Class<?>[0]);
        invoke(graph, "resetForPosition", new Class<?>[] { String.class }, START_FEN);
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 12 multipv 1 score cp 42 nodes 1000 nps 5000 time 90 pv e2e4 e7e5"),
                Move.parse("e2e4"));
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 13 multipv 1 score cp 15 nodes 2200 nps 7000 time 180 pv g1f3"),
                Move.parse("g1f3"));

        String csv = (String) invoke(graph, "csvText", new Class<?>[0]);
        assertTrue(csv.contains("index,eval_cp,eval,depth,nodes,nps,time_ms,best_move"),
                "analysis CSV header");
        assertTrue(csv.contains("g1f3"), "analysis CSV contains best move");

        String report = (String) invoke(graph, "reportText", new Class<?>[0]);
        assertTrue(report.contains("CRTK Workbench Analysis Report"), "analysis report title");
        assertTrue(report.contains("Samples: 2"), "analysis report sample count");
        assertTrue(report.contains("max 13"), "analysis report max depth");
    }

    /**
     * Verifies generated artifact notifications stay concise.
     */
    private static void testExportToastMessageSummarizesArtifacts() {
        String single = (String) invokeStatic(type("window.WindowGameLayer"), "exportToastMessage",
                new Class<?>[] { List.class }, List.of(Path.of("out.pdf")));
        assertEquals("Exported out.pdf", single, "single artifact toast");

        String multiple = (String) invokeStatic(type("window.WindowGameLayer"), "exportToastMessage",
                new Class<?>[] { List.class }, List.of(Path.of("a.pdf"), Path.of("b.csv")));
        assertEquals("Exported 2 files", multiple, "multi-artifact toast");
    }

    /**
     * Verifies the graph paints a solid non-blank surface.
     */
    private static void testAnalysisGraphPaintsOpaqueSurface() {
        JComponent graph = (JComponent) construct(type("AnalysisGraph"), new Class<?>[0]);
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 14 multipv 1 score cp -31 nodes 2000 nps 6500 pv g1f3"),
                Move.parse("g1f3"));
        assertPaintsOpaqueCorner(graph, 360, 260, "analysis graph opaque background");
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
}
