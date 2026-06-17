package chess.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a {@link JsonSchema} to a parsed {@link JsonValue} tree and reports
 * structural mismatches as a list of {@link Violation} records.
 *
 * <p>A {@code Validator} is created from a schema document via
 * {@link #fromSource(JsonValue)}, which parses the root schema together with
 * the {@code $defs} table. Violations are collected, never thrown, so callers
 * can produce a complete diagnostic in one pass instead of failing on the
 * first issue.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Validator {

	/**
	 * Root schema applied to incoming values.
	 */
	private final JsonSchema root;

	/**
	 * Resolved {@code $defs} table used to follow {@code $ref} pointers.
	 */
	private final Map<String, JsonSchema> defs;

	/**
	 * Constructs a validator from a parsed root schema and definitions map.
	 *
	 * @param root root schema
	 * @param defs resolved {@code $defs} table
	 */
	private Validator(JsonSchema root, Map<String, JsonSchema> defs) {
		this.root = root;
		this.defs = defs;
	}

	/**
	 * Constructs a validator from the schema source document.
	 *
	 * @param schemaDocument parsed JSON schema document
	 * @return validator that applies the document
	 * @throws IllegalArgumentException when the schema is malformed
	 */
	public static Validator fromSource(JsonValue schemaDocument) {
		LinkedHashMap<String, JsonSchema> defs = JsonSchema.parseDefs(schemaDocument);
		JsonSchema root = JsonSchema.parse(schemaDocument);
		return new Validator(root, defs);
	}

	/**
	 * Constructs a validator by parsing the schema source from text.
	 *
	 * @param schemaText schema document text
	 * @return validator that applies the document
	 */
	public static Validator fromText(String schemaText) {
		return fromSource(JsonParser.parse(schemaText));
	}

	/**
	 * Validates a parsed JSON value against the configured schema.
	 *
	 * @param value parsed JSON value to validate
	 * @return immutable list of violations (empty when the value validates clean)
	 */
	public List<Violation> validate(JsonValue value) {
		List<Violation> violations = new ArrayList<>();
		validateNode(root, value, "", violations);
		return List.copyOf(violations);
	}

	/**
	 * Validates raw JSON text against the configured schema.
	 *
	 * @param json raw JSON document text
	 * @return immutable list of violations (empty when the value validates clean)
	 */
	public List<Violation> validate(String json) {
		return validate(JsonParser.parse(json));
	}

	/**
	 * Recursively validates a node and collects any violations.
	 *
	 * @param schema   schema being applied at this position
	 * @param value    value being validated
	 * @param pointer  RFC 6901 pointer into the source value
	 * @param out      accumulator for discovered violations
	 */
	private void validateNode(JsonSchema schema, JsonValue value, String pointer, List<Violation> out) {
		JsonSchema effective = resolve(schema, pointer, out);
		if (effective == null) {
			return;
		}
		if (!checkKind(effective, value, pointer, out)) {
			return;
		}
		checkInteger(effective, value, pointer, out);
		checkEnumAndConst(effective, value, pointer, out);
		switch (value.kind()) {
			case OBJECT:
				validateObject(effective, value, pointer, out);
				break;
			case ARRAY:
				validateArray(effective, value, pointer, out);
				break;
			default:
				// nothing more to check for scalars
		}
	}

	/**
	 * Resolves a schema's {@code $ref} pointer, reporting unresolved refs.
	 *
	 * @param schema  schema possibly containing a {@code $ref}
	 * @param pointer pointer used for error reporting
	 * @param out     accumulator for discovered violations
	 * @return resolved schema, or {@code null} when the reference is unresolved
	 */
	private JsonSchema resolve(JsonSchema schema, String pointer, List<Violation> out) {
		JsonSchema current = schema;
		int guard = 0;
		while (current.ref() != null) {
			String name = current.ref().substring("#/$defs/".length());
			JsonSchema target = defs.get(name);
			if (target == null) {
				out.add(new Violation(pointer,
						"unresolved $ref: #/$defs/" + name + " (no such definition)"));
				return null;
			}
			current = target;
			if (++guard > 32) {
				out.add(new Violation(pointer, "cyclic $ref chain detected"));
				return null;
			}
		}
		return current;
	}

	/**
	 * Verifies that a value's JSON kind matches the schema's type constraint.
	 *
	 * @param schema  active schema
	 * @param value   value being validated
	 * @param pointer pointer for error reporting
	 * @param out     accumulator for violations
	 * @return {@code true} when the kind matches and further checks may proceed
	 */
	private static boolean checkKind(JsonSchema schema, JsonValue value, String pointer, List<Violation> out) {
		if (schema.allowedKinds().isEmpty()) {
			return true;
		}
		if (!schema.allowedKinds().contains(value.kind())) {
			out.add(new Violation(pointer,
					"expected type " + describeKinds(schema) + " but got " + value.kind().name().toLowerCase()));
			return false;
		}
		return true;
	}

	/**
	 * Enforces the {@code type:"integer"} refinement on numeric values.
	 *
	 * @param schema  active schema
	 * @param value   value being validated
	 * @param pointer pointer for error reporting
	 * @param out     accumulator for violations
	 */
	private static void checkInteger(JsonSchema schema, JsonValue value, String pointer, List<Violation> out) {
		if (!schema.requireInteger() || value.kind() != JsonValue.Kind.NUMBER) {
			return;
		}
		double n = value.asNumber();
		if (!value.numberIsInteger() && n != Math.rint(n)) {
			out.add(new Violation(pointer, "expected integer but got fractional number " + n));
		}
	}

	/**
	 * Enforces {@code enum} and {@code const} constraints on the value.
	 *
	 * @param schema  active schema
	 * @param value   value being validated
	 * @param pointer pointer for error reporting
	 * @param out     accumulator for violations
	 */
	private static void checkEnumAndConst(JsonSchema schema, JsonValue value, String pointer, List<Violation> out) {
		if (schema.constValue() != null && !value.structurallyEquals(schema.constValue())) {
			out.add(new Violation(pointer, "value does not match const constraint"));
		}
		List<JsonValue> enumValues = schema.enumValues();
		if (enumValues == null) {
			return;
		}
		for (JsonValue candidate : enumValues) {
			if (value.structurallyEquals(candidate)) {
				return;
			}
		}
		out.add(new Violation(pointer, "value is not one of the enum-allowed values"));
	}

	/**
	 * Validates an object's required keys, listed properties, and additional properties.
	 *
	 * @param schema  active schema
	 * @param value   object value
	 * @param pointer pointer for error reporting
	 * @param out     accumulator for violations
	 */
	private void validateObject(JsonSchema schema, JsonValue value, String pointer, List<Violation> out) {
		LinkedHashMap<String, JsonValue> entries = value.asObject();
		for (String requiredName : schema.required()) {
			if (!entries.containsKey(requiredName)) {
				out.add(new Violation(pointer, "missing required property '" + requiredName + "'"));
			}
		}
		for (Map.Entry<String, JsonValue> entry : entries.entrySet()) {
			String key = entry.getKey();
			JsonSchema propertySchema = schema.properties().get(key);
			if (propertySchema != null) {
				validateNode(propertySchema, entry.getValue(), pointer + "/" + escapePointer(key), out);
				continue;
			}
			if (schema.additionalSchema() != null) {
				validateNode(schema.additionalSchema(), entry.getValue(),
						pointer + "/" + escapePointer(key), out);
			} else if (!schema.additionalAllowed()) {
				out.add(new Violation(pointer + "/" + escapePointer(key),
						"additional property '" + key + "' is not allowed"));
			}
		}
	}

	/**
	 * Validates each element of an array against the {@code items} sub-schema.
	 *
	 * @param schema  active schema
	 * @param value   array value
	 * @param pointer pointer for error reporting
	 * @param out     accumulator for violations
	 */
	private void validateArray(JsonSchema schema, JsonValue value, String pointer, List<Violation> out) {
		if (schema.itemsSchema() == null) {
			return;
		}
		List<JsonValue> items = value.asArray();
		for (int i = 0; i < items.size(); i++) {
			validateNode(schema.itemsSchema(), items.get(i), pointer + "/" + i, out);
		}
	}

	/**
	 * Renders the schema's allowed kinds for diagnostic messages.
	 *
	 * @param schema active schema
	 * @return human-readable type description
	 */
	private static String describeKinds(JsonSchema schema) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (JsonValue.Kind kind : schema.allowedKinds()) {
			if (!first) {
				sb.append('|');
			}
			sb.append(kind.name().toLowerCase());
			first = false;
		}
		return sb.toString();
	}

	/**
	 * Escapes a property name for use in a JSON pointer (RFC 6901).
	 *
	 * @param name property name
	 * @return escaped pointer segment
	 */
	private static String escapePointer(String name) {
		return name.replace("~", "~0").replace("/", "~1");
	}
}
