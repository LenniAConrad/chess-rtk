package testing;

import static testing.TestSupport.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import chess.core.Setup;
import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Engine;
import chess.uci.Evaluation;
import chess.uci.Filter;
import chess.uci.Filter.FilterDSL;
import chess.uci.Output;
import chess.uci.Protocol;

/**
 * Regression checks for UCI protocol parsing, output parsing, filters, and the
 * engine process handshake.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class UCIRegressionTest {

	/**
	 * Prevents instantiation of this utility class.
	 */
	private UCIRegressionTest() {
		// utility
	}

	/**
	 * Runs all UCI regression checks.
	 *
	 * @param args ignored
	 * @throws Exception if a regression check fails unexpectedly
	 */
	public static void main(String[] args) throws Exception {
		testProtocolDoesNotRepairInvalidTemplates();
		testProtocolRequiresUciHandshakeTokens();
		testOutputIgnoresInvalidOptionalWdl();
		testAnalysisStoresContentWithoutDepthAtZero();
		testFilterDslRejectsTypos();
		testFilterRequiresPresentFields();
		testFilterDslRoundTripsChances();
		testEvaluationMateOrdering();
		testEngineHandshakeAndSearch();
		testEngineSkipsMissingChess960Command();
		System.out.println("UCIRegressionTest: all checks passed");
	}

	/**
	 * Invalid placeholders must remain invalid instead of falling back to defaults.
	 *
	 * @throws IOException if TOML parsing fails
	 */
	private static void testProtocolDoesNotRepairInvalidTemplates() throws IOException {
		Protocol protocol = new Protocol().fromToml(validProtocolToml().replace(
				"searchNodes = \"go nodes %d\"",
				"searchNodes = \"go nodes %d %s\""));

		assertFalse(protocol.assertValid(), "invalid protocol template rejected");
		assertContains(protocol.collectValidationErrors(), "searchNodes", "invalid template error mentions key");
	}

	/**
	 * Strict startup requires both the initialization command and response token.
	 *
	 * @throws IOException if TOML parsing fails
	 */
	private static void testProtocolRequiresUciHandshakeTokens() throws IOException {
		Protocol protocol = new Protocol().fromToml(validProtocolToml().replace("uciok = \"uciok\"\n", ""));

		assertFalse(protocol.assertValid(), "protocol without uciok rejected");
		assertContains(protocol.collectValidationErrors(), "uciok", "missing uciok error mentions key");
	}

	/**
	 * Optional WDL parse errors must not discard the rest of a usable line.
	 */
	private static void testOutputIgnoresInvalidOptionalWdl() {
		Output output = new Output("info depth 1 score cp 10 wdl 1 2 3 nodes 7 pv e2e4");

		assertTrue(output.hasEvaluation(), "output keeps evaluation with invalid WDL");
		assertFalse(output.hasChances(), "output ignores invalid WDL");
		assertEquals(7L, output.getNodes(), "output keeps nodes with invalid WDL");

		Output nonNumeric = new Output("info depth 1 score cp 10 wdl 1000 nope 0 nodes 7 pv e2e4");
		assertFalse(nonNumeric.hasChances(), "output ignores non-numeric WDL");
	}

	/**
	 * Useful info lines without depth should still be queryable deterministically.
	 */
	private static void testAnalysisStoresContentWithoutDepthAtZero() {
		Analysis analysis = new Analysis().add("info score cp 10 pv e2e4");

		assertNotNull(analysis.get(0, 1), "analysis stores no-depth content at depth zero");
	}

	/**
	 * Boolean typos should fail instead of silently becoming false.
	 */
	private static void testFilterDslRejectsTypos() {
		expectIllegalArgument(() -> FilterDSL.fromString("gate=AND;null=ture;"), "filter rejects boolean typo");
		expectIllegalArgument(() -> FilterDSL.fromString("gate=AND;leaf[eval>=0;"), "filter rejects unclosed leaf");
		expectIllegalArgument(() -> FilterDSL.fromString("depthx>=1;"), "filter rejects predicate prefix typo");
	}

	/**
	 * Predicates should not pass just because an absent field has a default value.
	 */
	private static void testFilterRequiresPresentFields() {
		Analysis withoutDepth = new Analysis().add("info score cp 10 pv e2e4");
		Filter depthFilter = Filter.builder()
				.withDepth(Filter.ComparisonOperator.GREATER_EQUAL, 1)
				.build();

		Analysis withoutNodes = new Analysis().add("info depth 1 score cp 10 pv e2e4");
		Filter nodesFilter = Filter.builder()
				.withNodes(Filter.ComparisonOperator.LESS_EQUAL, 1_000L)
				.build();

		assertFalse(depthFilter.apply(withoutDepth), "depth predicate requires depth field");
		assertFalse(nodesFilter.apply(withoutNodes), "nodes predicate requires nodes field");
	}

	/**
	 * WDL predicates should serialize without whitespace and parse back correctly.
	 */
	private static void testFilterDslRoundTripsChances() {
		Filter filter = Filter.builder()
				.withChances(Filter.ComparisonOperator.GREATER_EQUAL, new Chances((short) 790, (short) 200, (short) 10))
				.build();
		String dsl = FilterDSL.toString(filter);
		Filter parsed = FilterDSL.fromString(dsl);
		Analysis analysis = new Analysis().add("info depth 1 wdl 800 200 0");

		assertTrue(dsl.contains("chances>=790/200/10"), "chances DSL is compact");
		assertTrue(parsed.apply(analysis), "round-tripped chances filter applies");
	}

	/**
	 * Mate scores must sort winning mates above centipawns and losing mates below.
	 */
	private static void testEvaluationMateOrdering() {
		Evaluation mateOne = new Evaluation("#1");
		Evaluation mateTen = new Evaluation("#10");
		Evaluation cpWin = new Evaluation("1000");
		Evaluation matedTen = new Evaluation("#-10");
		Evaluation matedOne = new Evaluation("#-1");

		assertTrue(mateOne.isGreater(mateTen), "mate one beats mate ten");
		assertTrue(mateTen.isGreater(cpWin), "winning mate beats centipawns");
		assertTrue(cpWin.isGreater(matedTen), "centipawns beat losing mate");
		assertTrue(matedTen.isGreater(matedOne), "mated later beats mated sooner");
	}

	/**
	 * Engine startup must perform uci/uciok before readiness and search.
	 *
	 * @throws Exception if process setup or analysis fails
	 */
	private static void testEngineHandshakeAndSearch() throws Exception {
		Path dir = Files.createTempDirectory("crtk-uci-test");
		Path log = dir.resolve("commands.log");
		Path fakeEngine = dir.resolve("fake-uci.sh");
		Files.writeString(fakeEngine, fakeEngineScript(log));
		assertTrue(fakeEngine.toFile().setExecutable(true), "fake engine marked executable");

		Protocol protocol = new Protocol()
				.setPath(fakeEngine.toString())
				.setName("Fake UCI")
				.setSettings("test")
				.setSetup(new String[0]);

		Analysis analysis = new Analysis();
		try (Engine engine = new Engine(protocol)) {
			engine.analyse(Setup.getStandardStartPosition(), analysis, null, 1L, 2_000L);
		}

		List<String> commands = Files.readAllLines(log);
		assertEquals("uci", commands.get(0), "first command is uci");
		assertEquals("isready", commands.get(1), "second command is readiness check");
		assertTrue(commands.contains("go nodes 1"), "fake engine received node search");
		assertNotNull(analysis.getBestOutput(), "fake engine produced output");
		assertEquals(42, analysis.getBestOutput().getEvaluation().getValue(), "fake engine evaluation parsed");
	}

	/**
	 * Standard UCI behavior should still work when optional Chess960 configuration is
	 * absent.
	 *
	 * @throws Exception if process setup or analysis fails
	 */
	private static void testEngineSkipsMissingChess960Command() throws Exception {
		Path dir = Files.createTempDirectory("crtk-uci-no-chess960-test");
		Path log = dir.resolve("commands.log");
		Path fakeEngine = dir.resolve("fake-uci.sh");
		Files.writeString(fakeEngine, fakeEngineScript(log));
		assertTrue(fakeEngine.toFile().setExecutable(true), "fake engine marked executable");

		Protocol protocol = new Protocol()
				.setPath(fakeEngine.toString())
				.setName("Fake UCI without Chess960 option")
				.setSettings("test")
				.setSetChess960(null)
				.setSetup(new String[0]);

		Analysis analysis = new Analysis();
		try (Engine engine = new Engine(protocol)) {
			engine.analyse(Setup.getChess960ByIndex(0), analysis, null, 1L, 2_000L);
		}

		List<String> commands = Files.readAllLines(log);
		assertFalse(commands.stream().anyMatch(command -> command.contains("UCI_Chess960")),
				"missing Chess960 command is skipped");
		assertTrue(commands.contains("go nodes 1"), "fake engine still searched Chess960 position");
		assertNotNull(analysis.getBestOutput(), "fake engine produced output without Chess960 command");
	}

	/**
	 * Builds a minimal valid protocol TOML string.
	 *
	 * @return protocol TOML
	 */
	private static String validProtocolToml() {
		return ""
				+ "path = \"stockfish\"\n"
				+ "name = \"Stockfish\"\n"
				+ "settings = \"test\"\n"
				+ "isready = \"isready\"\n"
				+ "readyok = \"readyok\"\n"
				+ "showUci = \"uci\"\n"
				+ "uciok = \"uciok\"\n"
				+ "searchDepth = \"go depth %d\"\n"
				+ "searchNodes = \"go nodes %d\"\n"
				+ "searchTime = \"go movetime %d\"\n"
				+ "setPosition = \"position fen %s\"\n"
				+ "stop = \"stop\"\n"
				+ "newGame = \"ucinewgame\"\n"
				+ "setChess960 = \"setoption name UCI_Chess960 value %b\"\n"
				+ "setHashSize = \"setoption name Hash value %d\"\n"
				+ "setMultiPivotAmount = \"setoption name MultiPV value %d\"\n"
				+ "setThreadAmount = \"setoption name Threads value %d\"\n"
				+ "showWinDrawLoss = \"setoption name UCI_ShowWDL value %b\"\n"
				+ "setup = []\n";
	}

	/**
	 * Builds a small shell script that behaves like a minimal UCI engine.
	 *
	 * @param log path receiving commands sent by {@link Engine}
	 * @return shell script contents
	 */
	private static String fakeEngineScript(Path log) {
		String logPath = log.toString().replace("'", "'\"'\"'");
		return "#!/bin/sh\n"
				+ "LOG='" + logPath + "'\n"
				+ "while IFS= read -r line; do\n"
				+ "  printf '%s\\n' \"$line\" >> \"$LOG\"\n"
				+ "  case \"$line\" in\n"
				+ "    uci) printf '%s\\n' 'id name Fake UCI' 'uciok' ;;\n"
				+ "    isready) printf '%s\\n' 'readyok' ;;\n"
				+ "    'go nodes '*) printf '%s\\n' 'info depth 1 multipv 1 score cp 42 wdl 500 300 200 nodes 1 nps 1 hashfull 0 tbhits 0 time 1 pv e2e4' 'bestmove e2e4' ;;\n"
				+ "    stop) printf '%s\\n' 'bestmove e2e4' ;;\n"
				+ "  esac\n"
				+ "done\n";
	}

	/**
	 * Asserts that an action throws {@link IllegalArgumentException}.
	 *
	 * @param action action expected to fail
	 * @param label assertion label
	 */
	private static void expectIllegalArgument(Runnable action, String label) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(label + ": expected IllegalArgumentException");
	}

	/**
	 * Asserts that at least one item in an array contains a substring.
	 *
	 * @param values values to inspect
	 * @param needle expected substring
	 * @param label assertion label
	 */
	private static void assertContains(String[] values, String needle, String label) {
		for (String value : values) {
			if (value.contains(needle)) {
				return;
			}
		}
		throw new AssertionError(label + ": expected substring " + needle);
	}
}
