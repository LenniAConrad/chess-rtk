package testing;

import static testing.TestSupport.*;

import java.util.List;

import application.cli.CliCommand;
import application.cli.CliRegistry;
import utility.Json;

/**
 * Regression checks for the {@code crtk help --json} command catalog.
 *
 * <p>
 * Verifies that the catalog covers the entire CLI registry, is byte-for-byte
 * deterministic, is well-formed JSON, can be scoped to a subtree, and fails
 * loudly on an unknown path. These guards keep the machine-readable contract
 * in lockstep with the human help surface.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CommandCatalogRegressionTest {

	/**
	 * Stable schema identifier expected in every catalog envelope.
	 */
	private static final String SCHEMA_VERSION = "crtk.cli.catalog.v1";

	/**
	 * JSON key fragment used to locate a command path in the output.
	 */
	private static final String PATH_KEY = "\"path\": \"";

	/**
	 * Runs the regression checks.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		testEnvelopeShape();
		testCoversEveryRegistryCommand();
		testLeafFlagsAndOptions();
		testDeterministicOutput();
		testWellFormedJson();
		testScopedSubtree();
		testUnknownPathFailsLoud();
		System.out.println("CommandCatalogRegressionTest: all checks passed");
	}

	/**
	 * Verifies the top-level envelope fields and version stamp.
	 */
	private static void testEnvelopeShape() {
		String catalog = runMain("help", "--json");
		assertEquals(SCHEMA_VERSION, Json.parseStringField(catalog, "schemaVersion"),
				"catalog schemaVersion");
		assertTrue(catalog.contains("\"tool\": \"crtk\""), "catalog tool field");
		assertTrue(catalog.contains("\"exitCodes\": {"), "catalog exit-code taxonomy");
		assertTrue(catalog.contains("\"commands\": ["), "catalog commands array");
	}

	/**
	 * Verifies the catalog contains a path entry for every command in the registry.
	 */
	private static void testCoversEveryRegistryCommand() {
		String catalog = runMain("help", "--json");
		for (CliCommand area : CliRegistry.root().children()) {
			assertPathPresent(catalog, area);
		}
	}

	/**
	 * Verifies leaf commands expose parsed flags and the verbatim options block.
	 */
	private static void testLeafFlagsAndOptions() {
		String catalog = runMain("help", "--json");
		assertTrue(catalog.contains(PATH_KEY + "engine bestmove\""), "catalog includes engine bestmove");
		assertTrue(catalog.contains(PATH_KEY + "record export training-jsonl\""),
				"catalog includes nested record export training-jsonl");
		assertTrue(catalog.contains("\"flags\": ["), "catalog emits parsed flags");
		assertTrue(catalog.contains("\"optionsHelp\":"), "catalog emits verbatim option text");
		assertTrue(catalog.contains("\"--multipv\""), "catalog parses a known engine analyze flag");
		assertTrue(catalog.contains("\"find-mate\""), "catalog records command aliases");
	}

	/**
	 * Verifies the catalog is byte-for-byte stable across repeated runs.
	 */
	private static void testDeterministicOutput() {
		String first = runMain("help", "--json");
		String second = runMain("help", "--json");
		assertEquals(first, second, "catalog determinism");
	}

	/**
	 * Verifies the emitted catalog is balanced, well-formed JSON.
	 */
	private static void testWellFormedJson() {
		assertWellFormed(runMain("help", "--json"), "full catalog");
		assertWellFormed(runMain("help", "--json", "engine"), "engine subtree");
	}

	/**
	 * Verifies a scoped catalog includes only the requested subtree.
	 */
	private static void testScopedSubtree() {
		String scoped = runMain("help", "--json", "engine", "bestmove");
		assertEquals(SCHEMA_VERSION, Json.parseStringField(scoped, "schemaVersion"),
				"scoped catalog schemaVersion");
		assertTrue(scoped.contains(PATH_KEY + "engine bestmove\""), "scoped catalog includes the node");
		assertFalse(scoped.contains(PATH_KEY + "record\""), "scoped catalog excludes other areas");
	}

	/**
	 * Verifies an unknown path exits non-zero with a diagnostic on stderr.
	 */
	private static void testUnknownPathFailsLoud() {
		FailureResult result = runMainExpectFailure("help", "--json", "definitely-not-a-command");
		assertEquals(2, result.exitCode(), "unknown catalog path exit code");
		assertTrue(result.stderr().contains("Unknown command for help"),
				"unknown catalog path diagnostic");
	}

	/**
	 * Asserts the catalog contains the command's canonical path entry.
	 *
	 * @param catalog catalog JSON text
	 * @param command command node to locate
	 */
	private static void assertPathPresent(String catalog, CliCommand command) {
		assertTrue(catalog.contains(PATH_KEY + command.commandPath() + "\""),
				"catalog covers '" + command.commandPath() + "'");
		for (CliCommand child : command.children()) {
			assertPathPresent(catalog, child);
		}
	}

	/**
	 * Asserts a JSON string has balanced braces and brackets and closed strings.
	 *
	 * @param json  JSON text to scan
	 * @param label assertion label
	 */
	private static void assertWellFormed(String json, String label) {
		int brace = 0;
		int bracket = 0;
		boolean inString = false;
		boolean escaped = false;
		for (int i = 0; i < json.length(); i++) {
			char c = json.charAt(i);
			if (inString) {
				if (escaped) {
					escaped = false;
				} else if (c == '\\') {
					escaped = true;
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}
			if (c == '"') {
				inString = true;
			} else if (c == '{') {
				brace++;
			} else if (c == '}') {
				brace--;
			} else if (c == '[') {
				bracket++;
			} else if (c == ']') {
				bracket--;
			}
			assertTrue(brace >= 0 && bracket >= 0, label + ": negative nesting depth");
		}
		assertFalse(inString, label + ": unterminated string");
		assertEquals(0, brace, label + ": unbalanced braces");
		assertEquals(0, bracket, label + ": unbalanced brackets");
	}

	/**
	 * Utility class; prevent instantiation.
	 */
	private CommandCatalogRegressionTest() {
		// utility
	}
}
