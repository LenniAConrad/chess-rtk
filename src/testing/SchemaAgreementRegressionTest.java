package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.runMain;
import static testing.TestSupport.runMainExpectFailure;

import java.util.List;

import chess.core.Position;
import chess.schema.JsonParser;
import chess.schema.JsonValue;
import chess.schema.Schemas;
import chess.schema.Validator;
import chess.schema.Violation;
import chess.struct.Record;
import chess.uci.Analysis;

/**
 * Regression coverage for the {@code chess.schema} validator and the
 * agreement between every registered emitter and its published schema.
 *
 * <p>The agreement test is the central drift gate: if a code change adds or
 * renames a field in an emitter without updating the schema (or vice versa),
 * this test fails. Negative cases ensure the validator is not a no-op: it
 * actually catches missing required fields, wrong types, additional
 * properties, broken {@code const} stamps, and {@code $ref} indirection.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SchemaAgreementRegressionTest {

	/**
	 * Schema name for the CLI command catalog.
	 */
	private static final String CATALOG_SCHEMA = "crtk.cli.catalog.v1";

	/**
	 * Schema name for emitted {@code chess.struct.Record} rows.
	 */
	private static final String RECORD_SCHEMA = "crtk.record.v2";

	/**
	 * Schema name for {@code record export training-jsonl} rows.
	 */
	private static final String TRAINING_JSONL_SCHEMA = "crtk.record.training-jsonl.v1";

	/**
	 * Schema name for emitted game-review ply rows.
	 */
	private static final String REVIEW_PLY_SCHEMA = "crtk.review.ply.v1";

	/**
	 * Utility class; prevent instantiation.
	 */
	private SchemaAgreementRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		testSchemaRegistryListsCatalog();
		testRegisteredSchemasParseCleanly();
		testCatalogValidatesAgainstItsOwnSchema();
		testRecordEmitterValidatesAgainstSchema();
		testTrainingJsonlEmitterValidatesAgainstSchema();
		testValidatorCatchesMissingRequired();
		testValidatorCatchesWrongType();
		testValidatorCatchesAdditionalProperty();
		testValidatorCatchesBrokenConstStamp();
		testValidatorCatchesUnresolvedRef();
		testCliSchemaListMatchesRegistry();
		testCliSchemaShowEmitsSource();
		testCliSchemaValidateOkPath();
		testCliSchemaValidateFailPath();
		testCliSchemaValidateUnknownSchemaFailsLoud();
		System.out.println("SchemaAgreementRegressionTest: all checks passed");
	}

	/**
	 * Verifies the registry exposes the catalog schema.
	 */
	private static void testSchemaRegistryListsCatalog() {
		List<String> names = Schemas.list();
		assertTrue(names.contains(CATALOG_SCHEMA), "Schemas.list() includes " + CATALOG_SCHEMA);
		assertTrue(Schemas.isKnown(CATALOG_SCHEMA), "Schemas.isKnown(" + CATALOG_SCHEMA + ")");
		assertTrue(Schemas.isKnown(RECORD_SCHEMA), "Schemas.isKnown(" + RECORD_SCHEMA + ")");
		assertTrue(Schemas.isKnown(TRAINING_JSONL_SCHEMA), "Schemas.isKnown(" + TRAINING_JSONL_SCHEMA + ")");
		assertTrue(Schemas.isKnown(REVIEW_PLY_SCHEMA), "Schemas.isKnown(" + REVIEW_PLY_SCHEMA + ")");
	}

	/**
	 * Verifies every registered schema parses without rejected keywords.
	 *
	 * <p>This is the loud-rejection guard: if a schema author accidentally uses
	 * an unsupported keyword like {@code oneOf}, the loader throws and this
	 * test catches it before downstream code starts trusting the schema.</p>
	 */
	private static void testRegisteredSchemasParseCleanly() {
		for (String name : Schemas.list()) {
			Validator validator = Schemas.load(name);
			assertTrue(validator != null, "validator loads for '" + name + "'");
		}
	}

	/**
	 * Verifies the catalog emitted by {@code crtk help --json} validates clean.
	 */
	private static void testCatalogValidatesAgainstItsOwnSchema() {
		String catalog = runMain("help", "--json");
		List<Violation> violations = Schemas.load(CATALOG_SCHEMA).validate(catalog);
		if (!violations.isEmpty()) {
			StringBuilder report = new StringBuilder("catalog disagrees with " + CATALOG_SCHEMA + ":\n");
			for (Violation violation : violations) {
				report.append("  ").append(violation.render()).append('\n');
			}
			throw new AssertionError(report.toString());
		}
	}

	/**
	 * Verifies the real {@link Record#toJson()} emitter validates cleanly.
	 */
	private static void testRecordEmitterValidatesAgainstSchema() {
		Analysis analysis = new Analysis();
		analysis.add("info depth 1 score cp 20 nodes 10 pv e2e4");
		Record record = new Record(1711171067923L,
				"Stockfish 16",
				new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
				new Position("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
				"fixture",
				new String[] { "META: source=test" },
				analysis);
		assertValid(RECORD_SCHEMA, record.toJson(), "record emitter");
	}

	/**
	 * Verifies {@code record export training-jsonl} rows validate against the
	 * published row schema.
	 */
	private static void testTrainingJsonlEmitterValidatesAgainstSchema() {
		try {
			java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("crtk-training-schema-");
			java.nio.file.Path input = dir.resolve("records.jsonl");
			java.nio.file.Path output = dir.resolve("training.jsonl");
			String parent = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
			String position = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
			java.nio.file.Files.writeString(input,
					"{\"created\":1,\"engine\":\"Stockfish\",\"parent\":\"" + parent
							+ "\",\"position\":\"" + position
							+ "\",\"description\":\"\",\"tags\":[],"
							+ "\"analysis\":[\"info depth 10 multipv 1 score cp 500 nodes 100 pv e7e5\"]}\n");
			runMain("record", "export", "training-jsonl",
					"--input", input.toString(),
					"--output", output.toString(),
					"--filter", "depth>=10",
					"--include-engine-metadata");
			for (String line : java.nio.file.Files.readAllLines(output)) {
				assertValid(TRAINING_JSONL_SCHEMA, line, "training-jsonl emitter");
			}
		} catch (java.io.IOException ex) {
			throw new AssertionError("failed to exercise training-jsonl emitter: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies the validator reports a missing required property.
	 */
	private static void testValidatorCatchesMissingRequired() {
		String missingTool = "{\"schemaVersion\":\"crtk.cli.catalog.v1\","
				+ "\"exitCodes\":{},\"commands\":[]}";
		List<Violation> violations = Schemas.load(CATALOG_SCHEMA).validate(missingTool);
		assertTrue(violations.size() == 1, "exactly one violation for missing required");
		assertTrue(violations.get(0).message().contains("required property 'tool'"),
				"violation names the missing field");
	}

	/**
	 * Asserts that a JSON document validates against a registered schema.
	 *
	 * @param schemaName registered schema name
	 * @param json emitted JSON document
	 * @param label diagnostic label
	 */
	private static void assertValid(String schemaName, String json, String label) {
		List<Violation> violations = Schemas.load(schemaName).validate(json);
		if (!violations.isEmpty()) {
			StringBuilder report = new StringBuilder(label + " disagrees with " + schemaName + ":\n");
			for (Violation violation : violations) {
				report.append("  ").append(violation.render()).append('\n');
			}
			throw new AssertionError(report.toString());
		}
	}

	/**
	 * Verifies the validator rejects a wrong-typed field.
	 */
	private static void testValidatorCatchesWrongType() {
		String wrongType = "{\"schemaVersion\":\"crtk.cli.catalog.v1\","
				+ "\"tool\":\"crtk\",\"exitCodes\":{},\"commands\":\"not-an-array\"}";
		List<Violation> violations = Schemas.load(CATALOG_SCHEMA).validate(wrongType);
		assertTrue(!violations.isEmpty(), "at least one violation for wrong-type field");
		boolean named = false;
		for (Violation violation : violations) {
			if (violation.pointer().contains("commands") && violation.message().contains("expected type")) {
				named = true;
			}
		}
		assertTrue(named, "violation locates the commands pointer and names the expected type");
	}

	/**
	 * Verifies the validator rejects an unknown property when additionalProperties:false.
	 */
	private static void testValidatorCatchesAdditionalProperty() {
		String withExtra = "{\"schemaVersion\":\"crtk.cli.catalog.v1\","
				+ "\"tool\":\"crtk\",\"exitCodes\":{},\"commands\":[],\"surprise\":42}";
		List<Violation> violations = Schemas.load(CATALOG_SCHEMA).validate(withExtra);
		boolean caught = false;
		for (Violation violation : violations) {
			if (violation.message().contains("additional property 'surprise'")) {
				caught = true;
			}
		}
		assertTrue(caught, "validator names the disallowed extra property");
	}

	/**
	 * Verifies the validator rejects a wrong {@code schemaVersion} const stamp.
	 */
	private static void testValidatorCatchesBrokenConstStamp() {
		String wrongStamp = "{\"schemaVersion\":\"crtk.cli.catalog.v2\","
				+ "\"tool\":\"crtk\",\"exitCodes\":{},\"commands\":[]}";
		List<Violation> violations = Schemas.load(CATALOG_SCHEMA).validate(wrongStamp);
		boolean caught = false;
		for (Violation violation : violations) {
			if (violation.pointer().endsWith("/schemaVersion") && violation.message().contains("const")) {
				caught = true;
			}
		}
		assertTrue(caught, "validator catches a wrong const-stamped schemaVersion");
	}

	/**
	 * Verifies the validator rejects a {@code $ref} pointing at a missing definition.
	 */
	private static void testValidatorCatchesUnresolvedRef() {
		String schemaText = "{\"type\":\"object\",\"properties\":{\"x\":{\"$ref\":\"#/$defs/Missing\"}},"
				+ "\"required\":[\"x\"],\"$defs\":{}}";
		Validator validator = Validator.fromSource(JsonParser.parse(schemaText));
		List<Violation> violations = validator.validate(JsonValue.ofObject(
				new java.util.LinkedHashMap<>(java.util.Map.of("x", JsonValue.ofBool(true)))));
		assertTrue(!violations.isEmpty(), "unresolved $ref must surface as a violation");
		boolean caught = false;
		for (Violation violation : violations) {
			if (violation.message().contains("unresolved $ref")) {
				caught = true;
			}
		}
		assertTrue(caught, "violation labels the unresolved $ref");
	}

	/**
	 * Verifies {@code crtk schema list} matches the registry contents and order.
	 */
	private static void testCliSchemaListMatchesRegistry() {
		String out = runMain("schema", "list");
		String[] lines = out.split("\n");
		List<String> expected = Schemas.list();
		assertTrue(lines.length >= expected.size(),
				"schema list emits at least as many lines as the registry");
		for (int i = 0; i < expected.size(); i++) {
			assertEquals(expected.get(i), lines[i], "schema list line " + i + " matches registry");
		}
	}

	/**
	 * Verifies {@code crtk schema show} emits the source text of the requested schema.
	 */
	private static void testCliSchemaShowEmitsSource() {
		String out = runMain("schema", "show", CATALOG_SCHEMA);
		assertTrue(out.contains("\"$id\": \"crtk.cli.catalog.v1\""),
				"schema show emits the $id stamp");
		assertTrue(out.contains("\"$defs\""), "schema show emits the $defs table");
	}

	/**
	 * Verifies {@code crtk schema validate} succeeds on a clean catalog.
	 */
	private static void testCliSchemaValidateOkPath() {
		String catalog = runMain("help", "--json");
		java.nio.file.Path tempFile;
		try {
			tempFile = java.nio.file.Files.createTempFile("crtk-catalog-", ".json");
			java.nio.file.Files.writeString(tempFile, catalog);
			try {
				String out = runMain("schema", "validate", CATALOG_SCHEMA,
						"--input", tempFile.toString());
				assertTrue(out.trim().equals("ok"),
						"schema validate prints 'ok' on a clean catalog");
			} finally {
				java.nio.file.Files.deleteIfExists(tempFile);
			}
		} catch (java.io.IOException ex) {
			throw new AssertionError("failed to write temp file: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies {@code crtk schema validate} fails loudly on an invalid document.
	 */
	private static void testCliSchemaValidateFailPath() {
		java.nio.file.Path tempFile;
		try {
			tempFile = java.nio.file.Files.createTempFile("crtk-invalid-", ".json");
			java.nio.file.Files.writeString(tempFile, "{\"schemaVersion\":\"bogus\"}");
			try {
				TestSupport.FailureResult result = runMainExpectFailure(
						"schema", "validate", CATALOG_SCHEMA, "--input", tempFile.toString());
				assertEquals(3, result.exitCode(), "validation failure exits with code 3");
				assertTrue(result.stderr().contains("/schemaVersion"),
						"stderr names the failing pointer");
			} finally {
				java.nio.file.Files.deleteIfExists(tempFile);
			}
		} catch (java.io.IOException ex) {
			throw new AssertionError("failed to write temp file: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies an unknown schema name surfaces a usage-shaped failure.
	 */
	private static void testCliSchemaValidateUnknownSchemaFailsLoud() {
		TestSupport.FailureResult result = runMainExpectFailure(
				"schema", "validate", "not.a.real.schema");
		assertEquals(2, result.exitCode(), "unknown schema name exits with code 2");
		assertTrue(result.stderr().contains("Unknown schema"), "stderr names the unknown schema");
	}
}
