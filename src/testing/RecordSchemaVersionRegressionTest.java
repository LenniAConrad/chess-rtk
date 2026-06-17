package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;

import chess.core.Position;
import chess.struct.Record;
import chess.struct.RecordValidator;
import chess.uci.Analysis;

/**
 * Regression coverage for the additive {@code schemaVersion} field on
 * {@link Record}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordSchemaVersionRegressionTest {

	/**
	 * Start-position FEN used by the record round-trip checks.
	 */
	private static final String START =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordSchemaVersionRegressionTest() {
		// utility
	}

	/**
	 * Runs every schema-version regression check.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		testUnversionedRecordParsesAsLegacyVersion();
		testNewRecordWritesCurrentSchemaVersion();
		testRoundTripPreservesExplicitSchemaVersion();
		testNumericDraftVersionNormalizes();
		testSchemaVersionDoesNotChangeRecordIdentity();
		testValidatorAcceptsMissingVersionAndRejectsBadVersion();
		System.out.println("RecordSchemaVersionRegressionTest: all checks passed");
	}

	/**
	 * Verifies old seven-field record JSON still parses and is marked v1.
	 */
	private static void testUnversionedRecordParsesAsLegacyVersion() {
		String json = "{\"created\":123,\"engine\":\"Stockfish\","
				+ "\"parent\":\"" + START + "\","
				+ "\"position\":\"" + START + "\","
				+ "\"description\":\"old row\","
				+ "\"tags\":[\"META: to_move=white\"],"
				+ "\"analysis\":[\"info depth 1 score cp 0\"]}";
		Record record = Record.fromJson(json);

		assertEquals(Record.LEGACY_SCHEMA_VERSION, record.getSchemaVersion(), "legacy schema version");
		assertTrue(record.toJson().startsWith("{\"schemaVersion\":\"crtk.record.v1\",\"created\":123"),
				"legacy row reserializes with explicit v1 stamp");
	}

	/**
	 * Verifies newly-created records stamp the current schema identifier.
	 */
	private static void testNewRecordWritesCurrentSchemaVersion() {
		Record record = new Record()
				.withCreated(456)
				.withEngine("internal")
				.withPosition(new Position(START));
		String json = record.toJson();

		assertTrue(json.startsWith("{\"schemaVersion\":\"" + Record.SCHEMA_VERSION + "\",\"created\":456"),
				"new row writes current schema version first");
		assertEquals(Record.SCHEMA_VERSION, Record.fromJson(json).getSchemaVersion(),
				"new row round-trip version");
	}

	/**
	 * Verifies an explicit schema identifier survives parse/serialize/parse.
	 */
	private static void testRoundTripPreservesExplicitSchemaVersion() {
		Record original = new Record("crtk.record.v7", 789, "engine", null, new Position(START),
				"explicit", new String[] { "custom" }, new Analysis());
		Record parsed = Record.fromJson(original.toJson());

		assertEquals("crtk.record.v7", parsed.getSchemaVersion(), "explicit version survives round-trip");
		assertEquals("crtk.record.v7", Record.fromJson(parsed.toJson()).getSchemaVersion(),
				"explicit version survives second round-trip");
	}

	/**
	 * Verifies numeric early-draft stamps are read tolerantly and normalized.
	 */
	private static void testNumericDraftVersionNormalizes() {
		Record parsed = Record.fromJson("{\"schemaVersion\":2,\"created\":1,"
				+ "\"position\":\"" + START + "\"}");

		assertEquals(Record.SCHEMA_VERSION, parsed.getSchemaVersion(), "numeric v2 normalized");
		assertTrue(parsed.toJson().startsWith("{\"schemaVersion\":\"" + Record.SCHEMA_VERSION + "\""),
				"numeric v2 reserializes as schema id");
	}

	/**
	 * Verifies schema versions do not participate in the historical dedupe identity.
	 */
	private static void testSchemaVersionDoesNotChangeRecordIdentity() {
		Record v1 = new Record("crtk.record.v1", 111, "engine", null, new Position(START),
				"same", new String[] { "tag" }, new Analysis());
		Record v2 = new Record("crtk.record.v2", 111, "engine", null, new Position(START),
				"same", new String[] { "tag" }, new Analysis());

		assertEquals(v1, v2, "schema version ignored by record identity");
		assertEquals(v1.hashCode(), v2.hashCode(), "schema version ignored by record hash");
	}

	/**
	 * Verifies fail-loud validation keeps old rows readable but rejects invalid
	 * version stamps.
	 */
	private static void testValidatorAcceptsMissingVersionAndRejectsBadVersion() {
		RecordValidator.Outcome missing = RecordValidator.validate("{\"created\":1}");
		assertTrue(missing.ok(), "missing schemaVersion remains valid");

		RecordValidator.Outcome badShape = RecordValidator.validate(
				"{\"schemaVersion\":\"record.v2\",\"created\":1}");
		assertTrue(!badShape.ok(), "bad schema id rejected");
		assertEquals("schemaVersion", badShape.issues().get(0).field(),
				"bad schema id issue field");

		RecordValidator.Outcome badValue = RecordValidator.validate(
				"{\"schemaVersion\":0,\"created\":1}");
		assertTrue(!badValue.ok(), "zero schemaVersion rejected");
		assertEquals("schemaVersion", badValue.issues().get(0).field(),
				"zero schemaVersion issue field");
	}
}
