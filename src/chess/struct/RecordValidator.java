package chess.struct;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import chess.core.Position;
import chess.schema.JsonParseException;
import chess.schema.JsonParser;
import chess.schema.JsonValue;

/**
 * Pure fail-loud validator for a single {@code Record}-shaped JSON object.
 *
 * <p>The historical {@link Record#fromJson(String)} path is intentionally
 * tolerant: it returns {@code null} for several distinct failure modes so
 * batch streamers can silently drop unparsable rows. That tolerance is
 * exactly what {@code crtk record validate} exists to replace. This
 * validator never returns {@code null}; it returns a structured
 * {@link Outcome} that reports every detected issue with the field name and
 * a human-readable message.</p>
 *
 * <p>Parsing is delegated to {@link JsonParser} so JSON-level errors
 * (unterminated strings, illegal control characters, duplicate keys, etc.)
 * are surfaced before any field inspection. FEN-level errors are surfaced
 * by attempting to construct a {@link Position} for {@code parent} and
 * {@code position}; the strict-FEN behaviour of {@code Position} is the
 * canonical source of truth.</p>
 *
 * <p>Unknown fields are accepted (forward-compat). The published
 * {@code crtk.record.v2} JSON Schema is the stricter contract for schema
 * validation; this validator stays focused on the long-standing ingest shape
 * and keeps old unversioned rows readable.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordValidator {

	/**
	 * Pointer used when an issue describes the root object itself.
	 */
	private static final String ROOT_FIELD = "$";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordValidator() {
		// utility
	}

	/**
	 * Validates the JSON text for one record.
	 *
	 * @param jsonObject raw JSON text for a single record (may be {@code null})
	 * @return validation outcome with structured issues
	 */
	public static Outcome validate(String jsonObject) {
		if (jsonObject == null) {
			return Outcome.failed(new Issue(ROOT_FIELD, "input was null"));
		}
		JsonValue parsed;
		try {
			parsed = JsonParser.parse(jsonObject);
		} catch (JsonParseException ex) {
			return Outcome.failed(new Issue(ROOT_FIELD, "not valid JSON: " + ex.getMessage()));
		}
		if (parsed.kind() != JsonValue.Kind.OBJECT) {
			return Outcome.failed(new Issue(ROOT_FIELD,
					"expected JSON object, got " + parsed.kind().name().toLowerCase()));
		}
		List<Issue> issues = new ArrayList<>();
		LinkedHashMap<String, JsonValue> entries = parsed.asObject();
		checkOptionalSchemaVersion(entries, issues);
		checkCreated(entries, issues);
		checkOptionalString(entries, "engine", issues);
		checkFenField(entries, "parent", issues);
		checkFenField(entries, "position", issues);
		checkOptionalString(entries, "description", issues);
		checkStringArray(entries, "tags", issues);
		checkStringArray(entries, "analysis", issues);
		return issues.isEmpty() ? Outcome.success() : Outcome.failed(issues);
	}

	/**
	 * Validates the optional {@code schemaVersion} field. Historical rows may omit
	 * it; when present it must be either a {@code crtk.record.vN} identifier or a
	 * positive integer from early draft emitters.
	 *
	 * @param entries object entries
	 * @param issues  accumulator for discovered issues
	 */
	private static void checkOptionalSchemaVersion(LinkedHashMap<String, JsonValue> entries, List<Issue> issues) {
		if (!entries.containsKey("schemaVersion")) {
			return;
		}
		JsonValue value = entries.get("schemaVersion");
		if (value.kind() == JsonValue.Kind.STRING) {
			if (!validSchemaId(value.asString())) {
				issues.add(new Issue("schemaVersion", "expected crtk.record.vN schema id"));
			}
			return;
		}
		if (value.kind() == JsonValue.Kind.NUMBER) {
			double number = value.asNumber();
			if ((!value.numberIsInteger() && number != Math.rint(number)) || number < 1) {
				issues.add(new Issue("schemaVersion", "expected positive integer version"));
			}
			return;
		}
		issues.add(new Issue("schemaVersion",
				"expected schema id string or integer, got " + value.kind().name().toLowerCase()));
	}

	/**
	 * Returns whether a record schema identifier has the canonical
	 * {@code crtk.record.vN} shape.
	 *
	 * @param value schema identifier text
	 * @return {@code true} when valid
	 */
	private static boolean validSchemaId(String value) {
		if (value == null || !value.startsWith("crtk.record.v")) {
			return false;
		}
		String suffix = value.substring("crtk.record.v".length());
		if (suffix.isEmpty()) {
			return false;
		}
		for (int i = 0; i < suffix.length(); i++) {
			if (!Character.isDigit(suffix.charAt(i))) {
				return false;
			}
		}
		try {
			return Integer.parseInt(suffix) > 0;
		} catch (NumberFormatException ex) {
			return false;
		}
	}

	/**
	 * Validates the required {@code created} epoch-millis field.
	 *
	 * @param entries object entries
	 * @param issues  accumulator for discovered issues
	 */
	private static void checkCreated(LinkedHashMap<String, JsonValue> entries, List<Issue> issues) {
		if (!entries.containsKey("created")) {
			issues.add(new Issue("created", "missing required field"));
			return;
		}
		JsonValue value = entries.get("created");
		if (value.kind() != JsonValue.Kind.NUMBER) {
			issues.add(new Issue("created",
					"expected integer, got " + value.kind().name().toLowerCase()));
			return;
		}
		double number = value.asNumber();
		if (!value.numberIsInteger() && number != Math.rint(number)) {
			issues.add(new Issue("created", "expected integer epoch millis, got fractional " + number));
		}
	}

	/**
	 * Validates that an optional field is a string when present.
	 *
	 * @param entries object entries
	 * @param field   field name
	 * @param issues  accumulator for discovered issues
	 */
	private static void checkOptionalString(LinkedHashMap<String, JsonValue> entries,
			String field, List<Issue> issues) {
		if (!entries.containsKey(field)) {
			return;
		}
		JsonValue value = entries.get(field);
		if (value.kind() == JsonValue.Kind.NULL) {
			return;
		}
		if (value.kind() != JsonValue.Kind.STRING) {
			issues.add(new Issue(field, "expected string, got " + value.kind().name().toLowerCase()));
		}
	}

	/**
	 * Validates that an optional FEN field is a parseable {@link Position} when present.
	 *
	 * @param entries object entries
	 * @param field   field name ({@code "parent"} or {@code "position"})
	 * @param issues  accumulator for discovered issues
	 */
	private static void checkFenField(LinkedHashMap<String, JsonValue> entries,
			String field, List<Issue> issues) {
		if (!entries.containsKey(field)) {
			return;
		}
		JsonValue value = entries.get(field);
		if (value.kind() == JsonValue.Kind.NULL) {
			return;
		}
		if (value.kind() != JsonValue.Kind.STRING) {
			issues.add(new Issue(field, "expected FEN string, got " + value.kind().name().toLowerCase()));
			return;
		}
		String fen = value.asString();
		if (fen.isEmpty() || "null".equals(fen)) {
			return;
		}
		try {
			new Position(fen);
		} catch (IllegalArgumentException ex) {
			issues.add(new Issue(field, "invalid FEN: " + ex.getMessage()));
		}
	}

	/**
	 * Validates that an optional field is an array of strings when present.
	 *
	 * @param entries object entries
	 * @param field   field name ({@code "tags"} or {@code "analysis"})
	 * @param issues  accumulator for discovered issues
	 */
	private static void checkStringArray(LinkedHashMap<String, JsonValue> entries,
			String field, List<Issue> issues) {
		if (!entries.containsKey(field)) {
			return;
		}
		JsonValue value = entries.get(field);
		if (value.kind() == JsonValue.Kind.NULL) {
			return;
		}
		if (value.kind() != JsonValue.Kind.ARRAY) {
			issues.add(new Issue(field, "expected array of strings, got " + value.kind().name().toLowerCase()));
			return;
		}
		List<JsonValue> items = value.asArray();
		for (int i = 0; i < items.size(); i++) {
			JsonValue item = items.get(i);
			if (item.kind() != JsonValue.Kind.STRING) {
				issues.add(new Issue(field + "[" + i + "]",
						"expected string, got " + item.kind().name().toLowerCase()));
			}
		}
	}

	/**
	 * One field-level validation issue.
	 *
	 * @param field   field name or path (e.g. {@code "tags[1]"})
	 * @param message human-readable failure message
	 */
	public record Issue(String field, String message) {

		/**
		 * Renders the issue as {@code "field: message"}.
		 *
		 * @return formatted issue string
		 */
		public String render() {
			return field + ": " + message;
		}
	}

	/**
	 * Validation outcome for one record.
	 *
	 * @param ok     {@code true} when no issues were detected
	 * @param issues immutable list of issues (empty when {@code ok} is true)
	 */
	public record Outcome(boolean ok, List<Issue> issues) {

		/**
		 * Returns a clean outcome with no issues.
		 *
		 * @return successful outcome
		 */
		public static Outcome success() {
			return new Outcome(true, List.of());
		}

		/**
		 * Returns a failed outcome carrying one issue.
		 *
		 * @param issue single issue describing the failure
		 * @return failed outcome
		 */
		public static Outcome failed(Issue issue) {
			return new Outcome(false, List.of(issue));
		}

		/**
		 * Returns a failed outcome carrying multiple issues.
		 *
		 * @param issues structured issues
		 * @return failed outcome
		 */
		public static Outcome failed(List<Issue> issues) {
			return new Outcome(false, List.copyOf(issues));
		}
	}
}
