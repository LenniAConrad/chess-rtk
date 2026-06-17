package chess.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, dependency-free JSON value tree used by the schema validator.
 *
 * <p>A {@code JsonValue} is one of {@link Kind#NULL}, {@link Kind#BOOL},
 * {@link Kind#NUMBER}, {@link Kind#STRING}, {@link Kind#ARRAY}, or
 * {@link Kind#OBJECT}. The value space is exactly what RFC 8259 allows for
 * a JSON document; the implementation intentionally avoids reflection,
 * third-party libraries, and floating-point traps (-0 is normalised to 0;
 * NaN/Infinity are rejected by {@link JsonParser}).</p>
 *
 * <p>Instances are deeply immutable. Map iteration order is preserved by
 * construction (parser uses {@link java.util.LinkedHashMap}) so emitted
 * JSON can be made byte-deterministic.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class JsonValue {

	/**
	 * Tag enum identifying the underlying JSON kind of a value.
	 */
	public enum Kind {

		/**
		 * JSON {@code null}.
		 */
		NULL,

		/**
		 * JSON boolean.
		 */
		BOOL,

		/**
		 * JSON number (integer or floating point).
		 */
		NUMBER,

		/**
		 * JSON string.
		 */
		STRING,

		/**
		 * JSON array.
		 */
		ARRAY,

		/**
		 * JSON object.
		 */
		OBJECT
	}

	/**
	 * Shared canonical {@code null} value.
	 */
	private static final JsonValue NULL = new JsonValue(Kind.NULL, false, 0.0, false, null, null, null);

	/**
	 * Shared canonical {@code true} value.
	 */
	private static final JsonValue TRUE = new JsonValue(Kind.BOOL, true, 0.0, false, null, null, null);

	/**
	 * Shared canonical {@code false} value.
	 */
	private static final JsonValue FALSE = new JsonValue(Kind.BOOL, false, 0.0, false, null, null, null);

	/**
	 * Discriminator tag.
	 */
	private final Kind kind;

	/**
	 * Backing boolean value when {@link #kind} is {@link Kind#BOOL}.
	 */
	private final boolean booleanValue;

	/**
	 * Backing numeric value when {@link #kind} is {@link Kind#NUMBER}.
	 */
	private final double numberValue;

	/**
	 * Tracks whether the numeric value was originally an integer literal.
	 */
	private final boolean numberIsInteger;

	/**
	 * Backing string value when {@link #kind} is {@link Kind#STRING}.
	 */
	private final String stringValue;

	/**
	 * Backing array value when {@link #kind} is {@link Kind#ARRAY}.
	 */
	private final List<JsonValue> arrayValue;

	/**
	 * Backing object value when {@link #kind} is {@link Kind#OBJECT}.
	 */
	private final LinkedHashMap<String, JsonValue> objectValue;

	/**
	 * Constructs a typed value from its backing payload (validated by the static factories).
	 *
	 * @param kind            discriminator tag
	 * @param booleanValue    boolean payload (used when {@code kind == BOOL})
	 * @param numberValue     numeric payload (used when {@code kind == NUMBER})
	 * @param numberIsInteger flag marking integer numeric literals
	 * @param stringValue     string payload (used when {@code kind == STRING})
	 * @param arrayValue      array payload (used when {@code kind == ARRAY})
	 * @param objectValue     object payload (used when {@code kind == OBJECT})
	 */
	private JsonValue(Kind kind,
			boolean booleanValue,
			double numberValue,
			boolean numberIsInteger,
			String stringValue,
			List<JsonValue> arrayValue,
			LinkedHashMap<String, JsonValue> objectValue) {
		this.kind = kind;
		this.booleanValue = booleanValue;
		this.numberValue = numberValue == 0.0 ? 0.0 : numberValue;
		this.numberIsInteger = numberIsInteger;
		this.stringValue = stringValue;
		this.arrayValue = arrayValue;
		this.objectValue = objectValue;
	}

	/**
	 * Returns the canonical {@code null} value.
	 *
	 * @return JSON {@code null}
	 */
	public static JsonValue ofNull() {
		return NULL;
	}

	/**
	 * Returns a JSON boolean wrapping the given value.
	 *
	 * @param value boolean payload
	 * @return boxed JSON boolean
	 */
	public static JsonValue ofBool(boolean value) {
		return value ? TRUE : FALSE;
	}

	/**
	 * Returns a JSON integer wrapping the given long value.
	 *
	 * @param value integer payload
	 * @return JSON number flagged as integer
	 */
	public static JsonValue ofInteger(long value) {
		return new JsonValue(Kind.NUMBER, false, (double) value, true, null, null, null);
	}

	/**
	 * Returns a JSON number wrapping the given double, marking the integer flag accordingly.
	 *
	 * @param value     numeric payload (must be finite)
	 * @param isInteger {@code true} when the source literal had no fractional part or exponent
	 * @return JSON number
	 */
	public static JsonValue ofNumber(double value, boolean isInteger) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			throw new IllegalArgumentException("JSON numbers must be finite (got " + value + ")");
		}
		return new JsonValue(Kind.NUMBER, false, value, isInteger, null, null, null);
	}

	/**
	 * Returns a JSON string wrapping the given text.
	 *
	 * @param value string payload (must not be {@code null})
	 * @return JSON string
	 */
	public static JsonValue ofString(String value) {
		return new JsonValue(Kind.STRING, false, 0.0, false, Objects.requireNonNull(value, "value"), null, null);
	}

	/**
	 * Returns a JSON array wrapping the given items.
	 *
	 * @param items array payload (must not be {@code null}; deep copies are not made)
	 * @return JSON array
	 */
	public static JsonValue ofArray(List<JsonValue> items) {
		return new JsonValue(Kind.ARRAY, false, 0.0, false, null,
				List.copyOf(Objects.requireNonNull(items, "items")), null);
	}

	/**
	 * Returns a JSON object wrapping the given ordered entries.
	 *
	 * @param entries object payload (iteration order is preserved)
	 * @return JSON object
	 */
	public static JsonValue ofObject(LinkedHashMap<String, JsonValue> entries) {
		Objects.requireNonNull(entries, "entries");
		LinkedHashMap<String, JsonValue> copy = new LinkedHashMap<>(entries);
		return new JsonValue(Kind.OBJECT, false, 0.0, false, null, null, copy);
	}

	/**
	 * Returns the discriminator tag.
	 *
	 * @return underlying kind
	 */
	public Kind kind() {
		return kind;
	}

	/**
	 * Returns the boolean payload.
	 *
	 * @return boolean payload
	 * @throws IllegalStateException when the value is not a {@link Kind#BOOL}
	 */
	public boolean asBool() {
		expect(Kind.BOOL);
		return booleanValue;
	}

	/**
	 * Returns the numeric payload as a double.
	 *
	 * @return numeric payload
	 * @throws IllegalStateException when the value is not a {@link Kind#NUMBER}
	 */
	public double asNumber() {
		expect(Kind.NUMBER);
		return numberValue;
	}

	/**
	 * Indicates whether the source literal was an integer.
	 *
	 * @return {@code true} when the numeric literal had no fractional part or exponent
	 * @throws IllegalStateException when the value is not a {@link Kind#NUMBER}
	 */
	public boolean numberIsInteger() {
		expect(Kind.NUMBER);
		return numberIsInteger;
	}

	/**
	 * Returns the string payload.
	 *
	 * @return string payload
	 * @throws IllegalStateException when the value is not a {@link Kind#STRING}
	 */
	public String asString() {
		expect(Kind.STRING);
		return stringValue;
	}

	/**
	 * Returns the array payload.
	 *
	 * @return array payload (immutable copy)
	 * @throws IllegalStateException when the value is not a {@link Kind#ARRAY}
	 */
	public List<JsonValue> asArray() {
		expect(Kind.ARRAY);
		return arrayValue;
	}

	/**
	 * Returns the object payload.
	 *
	 * @return object payload (defensive copy)
	 * @throws IllegalStateException when the value is not a {@link Kind#OBJECT}
	 */
	public LinkedHashMap<String, JsonValue> asObject() {
		expect(Kind.OBJECT);
		return new LinkedHashMap<>(objectValue);
	}

	/**
	 * Compares this value to another for structural JSON equality.
	 *
	 * <p>Numbers are compared by their normalised double value; strings by code
	 * points; arrays by length and element-wise equality; objects by key set
	 * and per-key equality regardless of insertion order.</p>
	 *
	 * @param other value to compare against (may be {@code null})
	 * @return {@code true} when the two values represent identical JSON content
	 */
	public boolean structurallyEquals(JsonValue other) {
		if (other == null || other.kind != kind) {
			return false;
		}
		switch (kind) {
			case NULL:
				return true;
			case BOOL:
				return booleanValue == other.booleanValue;
			case NUMBER:
				return Double.compare(numberValue, other.numberValue) == 0;
			case STRING:
				return stringValue.equals(other.stringValue);
			case ARRAY:
				return arraysEqual(arrayValue, other.arrayValue);
			case OBJECT:
				return objectsEqual(objectValue, other.objectValue);
			default:
				return false;
		}
	}

	/**
	 * Throws when the value is not of the expected kind.
	 *
	 * @param expected required kind
	 */
	private void expect(Kind expected) {
		if (kind != expected) {
			throw new IllegalStateException("Expected JSON " + expected + " but got " + kind);
		}
	}

	/**
	 * Compares two arrays for structural equality.
	 *
	 * @param a first array
	 * @param b second array
	 * @return {@code true} when sizes match and every element is structurally equal
	 */
	private static boolean arraysEqual(List<JsonValue> a, List<JsonValue> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!a.get(i).structurallyEquals(b.get(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Compares two objects for structural equality without regard to iteration order.
	 *
	 * @param a first object
	 * @param b second object
	 * @return {@code true} when key sets match and every value is structurally equal
	 */
	private static boolean objectsEqual(LinkedHashMap<String, JsonValue> a,
			LinkedHashMap<String, JsonValue> b) {
		if (a.size() != b.size() || !a.keySet().equals(b.keySet())) {
			return false;
		}
		for (var entry : a.entrySet()) {
			if (!entry.getValue().structurallyEquals(b.get(entry.getKey()))) {
				return false;
			}
		}
		return true;
	}
}
