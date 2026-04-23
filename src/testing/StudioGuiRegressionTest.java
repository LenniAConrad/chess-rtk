package testing;

import static testing.TestSupport.*;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import application.gui.studio.BoardViewModel;
import application.gui.studio.StudioBoardMapper;
import application.gui.studio.StudioBoardPanel;
import application.gui.studio.StudioGameNode;
import application.gui.studio.StudioGameTree;
import application.gui.studio.StudioProject;
import application.gui.studio.StudioTask;
import application.gui.studio.StudioTaskCatalog;
import application.gui.studio.StudioTheme;
import chess.core.Field;
import chess.core.Move;
import chess.core.Position;
import chess.struct.Game;

/**
 * Regression checks for the experimental Studio GUI v3 support classes.
 */
public final class StudioGuiRegressionTest {

	private StudioGuiRegressionTest() {
		// utility
	}

	/**
	 * Runs all Studio checks.
	 *
	 * @param args ignored
	 * @throws IOException on filesystem failures
	 */
	public static void main(String[] args) throws IOException {
		testBoardMapping();
		testGameTreeVariationsAndPgn();
		testProjectSave();
		testTaskPreview();
		testBoardPaintSmoke();
		testStudioFileGuardrail();
		System.out.println("StudioGuiRegressionTest: all checks passed");
	}

	private static void testBoardMapping() {
		Rectangle board = new Rectangle(10, 20, 800, 800);
		assertEquals(Field.A8, StudioBoardMapper.squareAt(new Point(20, 30), board, true), "white a8");
		assertEquals(Field.H1, StudioBoardMapper.squareAt(new Point(799, 810), board, true), "white h1");
		assertEquals(Field.H1, StudioBoardMapper.squareAt(new Point(20, 30), board, false), "black h1");
		assertEquals(Field.A8, StudioBoardMapper.squareAt(new Point(799, 810), board, false), "black a8");
	}

	private static void testGameTreeVariationsAndPgn() {
		StudioGameTree tree = StudioGameTree.fromPosition(new Position(Game.STANDARD_START_FEN));
		StudioGameNode e4 = tree.play(Move.parse("e2e4"));
		tree.play(Move.parse("e7e5"));
		tree.navigateTo(e4);
		StudioGameNode c5 = tree.play(Move.parse("c7c5"));
		c5.setComment("Sicilian");
		c5.addNag(1);
		assertEquals(2, e4.children().size(), "variation count");
		assertTrue(tree.promoteNode(c5), "promote variation");
		assertEquals("c5", e4.children().get(0).san(), "promoted SAN");
		String pgn = tree.toPgn();
		assertTrue(pgn.contains("Sicilian"), "PGN comment");
		assertTrue(pgn.contains("$1"), "PGN NAG");
		StudioGameTree parsed = StudioGameTree.fromPgn(pgn);
		assertTrue(parsed.nodes().size() >= 2, "PGN roundtrip nodes");
	}

	private static void testProjectSave() throws IOException {
		Path dir = Files.createTempDirectory("crtk-studio-test");
		StudioProject project = StudioProject.open(dir);
		StudioGameTree tree = StudioGameTree.fromPosition(new Position(Game.STANDARD_START_FEN));
		project.save(new Position(Game.STANDARD_START_FEN), tree, List.of(Game.STANDARD_START_FEN));
		project.appendNote("position", Game.STANDARD_START_FEN, "note");
		project.appendAnalysis("{\"ok\":true}");
		assertTrue(Files.exists(dir.resolve("project.properties")), "project properties");
		assertTrue(Files.exists(dir.resolve("positions.fen")), "positions file");
		assertTrue(Files.readString(dir.resolve("notes.jsonl")).contains("note"), "notes file");
		assertTrue(Files.readString(dir.resolve("analysis.jsonl")).contains("\"ok\""), "analysis file");
	}

	private static void testTaskPreview() {
		StudioTaskCatalog catalog = StudioTaskCatalog.defaults();
		StudioTask task = catalog.find("legal-both").orElseThrow();
		String preview = StudioTaskCatalog.preview(task, Game.STANDARD_START_FEN, Map.of("--extra", "x y"));
		assertTrue(preview.startsWith("crtk move list"), "preview command");
		assertTrue(preview.contains("--fen"), "preview FEN flag");
		assertTrue(preview.contains("'x y'"), "preview quoted value");
	}

	private static void testBoardPaintSmoke() {
		StudioBoardPanel panel = new StudioBoardPanel();
		panel.setViewModel(BoardViewModel.of(new Position(Game.STANDARD_START_FEN), true, StudioTheme.light()));
		BufferedImage image = panel.renderImage(512);
		int nonTransparent = 0;
		for (int y = 0; y < image.getHeight(); y += 8) {
			for (int x = 0; x < image.getWidth(); x += 8) {
				if ((image.getRGB(x, y) >>> 24) != 0) {
					nonTransparent++;
				}
			}
		}
		assertTrue(nonTransparent > 200, "nonblank board render");
	}

	private static void testStudioFileGuardrail() throws IOException {
		Path root = Path.of("src", "application", "gui", "studio");
		try (var stream = Files.walk(root)) {
			for (Path path : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
				long lines;
				try (var fileLines = Files.lines(path)) {
					lines = fileLines.count();
				}
				if (lines > 800) {
					throw new AssertionError(path + " exceeds 800 lines: " + lines);
				}
			}
		}
	}




}
