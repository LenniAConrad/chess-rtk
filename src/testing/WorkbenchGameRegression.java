package testing;

import static testing.WorkbenchTestSupport.*;

import java.awt.Component;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;

import application.gui.workbench.game.GameModel;
import application.gui.workbench.game.PgnExplorerModel;
import application.gui.workbench.game.PuzzleLibrary;
import application.gui.workbench.game.PuzzlePanel;
import application.gui.workbench.game.PuzzleSession;
import application.gui.workbench.game.SanRenderer;
import application.gui.workbench.ui.Theme;

import chess.core.Move;
import chess.core.Position;
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
        testGameModelLoadsPgnVariations();
        testGameModelNavigatesSelectedVariationLine();
        testPuzzleSessionExploresOpponentVariationBranches();
        testPuzzleLibraryLoadsDifficultCsv();
        testPuzzlePanelPaintsOpaqueSurface();
        testPgnExplorerModelFiltersGames();
        testEcoExplorerFiltersAndLoadsLines();
        testEvalBarMapping();
        testEvalBarAnimation();
        testEvalBarFrameFollowsTheme();
        testEvalBarThinkingIsStatic();
        testEngineEvalParsing();
        testLiveEngineStatusFormatting();
        testOptionalPositiveIntegerParsing();
        testAnalysisGraphStoresSamples();
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
     * Verifies imported algebraic movetext keeps PGN variation rows.
     */
    private static void testGameModelLoadsPgnVariations() {
        GameModel model = new GameModel();
        Game game = Pgn.parseGame("1. e4 (1. d4 d5) e5 *");
        model.loadGame(game.getStartPosition(), game);

        assertEquals(Integer.valueOf(4), Integer.valueOf(model.getRowCount()), "variation rows visible");
        assertEquals(Integer.valueOf(2), Integer.valueOf(model.lastPly()), "mainline ply count");
        assertEquals(Integer.valueOf(2), Integer.valueOf(model.variationRowCount()), "variation ply count");
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
     * Verifies the puzzle panel paints a themed, non-transparent surface.
     */
    private static void testPuzzlePanelPaintsOpaqueSurface() {
        JComponent panel = new PuzzlePanel();
        assertPaintsOpaqueCorner(panel, 720, 520, "puzzle panel opaque background");
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
        assertEquals(Integer.valueOf(1), Integer.valueOf(PgnExplorerModel.filter(entries, "alpha ruy").size()),
                "PGN explorer filters by player and opening");
        assertEquals(Integer.valueOf(1), Integer.valueOf(PgnExplorerModel.filter(entries, "study paris").size()),
                "PGN explorer filters by event and site");
        assertTrue(entries.get(0).pgn().contains("[Event \"Training Match\"]"),
                "PGN explorer serializes selected game");
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
        assertPaintsOpaqueCorner((JComponent) explorer, 420, 520, "ECO explorer opaque background");
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
}
