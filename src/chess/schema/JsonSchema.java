package chess.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed representation of a JSON Schema node, restricted to the explicit
 * subset CRTK supports.
 *
 * <h2>Supported keywords</h2>
 * <ul>
 *   <li>{@code type} (single string or array of strings, values:
 *       {@code "object"|"array"|"string"|"number"|"integer"|"boolean"|"null"})</li>
 *   <li>{@code properties}, {@code required},
 *       {@code additionalProperties} (boolean or sub-schema)</li>
 *   <li>{@code items} (single sub-schema applied to every element)</li>
 *   <li>{@code enum} (array of allowed values), {@code const} (single allowed value)</li>
 *   <li>{@code $ref} pointing only at {@code #/$defs/<name>}</li>
 *   <li>{@code $defs} at the root for shared sub-schemas</li>
 * </ul>
 *
 * <h2>Ignored metadata</h2>
 * <p>{@code $id}, {@code $schema}, {@code title}, {@code description},
 * {@code examples}, and {@code default} are accepted and skipped.</p>
 *
 * <h2>Loud rejection</h2>
 * <p>Any other keyword (for example {@code oneOf}, {@code anyOf},
 * {@code allOf}, {@code patternProperties}, {@code format}, {@code pattern})
 * raises {@link IllegalArgumentException} so consumers never silently rely on
 * features that are not actually validated.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class JsonSchema {

	/**
	 * Recognised metadata keywords that the validator ignores by design.
	 */
	private static final Set<String> IGNORED_METADATA = Set.of(
			"$id", "$schema", "title", "description", "examples", "default");

	/**
	 * Recognised validation keywords supported by this implementation.
	 */
	private static final Set<String> SUPPORTED_KEYWORDS = Set.of(
			"type", "properties", "required", "additionalProperties",
			"items", "enum", "const", "$ref", "$defs");

	/**
	 * Permitted values of the {@code type} keyword.
	 */
	private static final Set<String> ALLOWED_TYPES = Set.of(
			"object", "array", "string", "number", "integer", "boolean", "null");

	/**
	 * Allowed JSON value kinds (empty when no {@code type} constraint applies).
	 */
	private final Set<JsonValue.Kind> allowedKinds;

	/**
	 * Tracks whether an {@code "integer"} type constraint is in force.
	 */
	private final boolean requireInteger;

	/**
	 * Allowed values for the {@code enum} keyword, or {@code null} when absent.
	 */
	private final List<JsonValue> enumValues;

	/**
	 * Sole allowed value for the {@code const} keyword, or {@code null} when absent.
	 */
	private final JsonValue constValue;

	/**
	 * Named sub-schemas under {@code properties}.
	 */
	private final LinkedHashMap<String, JsonSchema> properties;

	/**
	 * Names of required properties.
	 */
	private final Set<String> required;

	/**
	 * Whether unlisted properties are allowed (default {@code true}).
	 */
	private final boolean additionalAllowed;

	/**
	 * Sub-schema applied to additional properties, or {@code null} when none is set.
	 */
	private final JsonSchema additionalSchema;

	/**
	 * Sub-schema applied to every array element, or {@code null} when none is set.
	 */
	private final JsonSchema itemsSchema;

	/**
	 * Unresolved {@code $ref} pointer, or {@code null} when this schema is inline.
	 */
	private final String ref;

	/**
	 * Constructs a schema node from validated component parts.
	 *
	 * @param allowedKinds      permitted JSON kinds
	 * @param requireInteger    {@code true} when type constraint includes {@code "integer"}
	 * @param enumValues        allowed enum values, or {@code null}
	 * @param constValue        sole allowed value, or {@code null}
	 * @param properties        named sub-schemas
	 * @param required          required property names
	 * @param additionalAllowed whether extra properties are allowed by default
	 * @param additionalSchema  sub-schema for extra properties, or {@code null}
	 * @param itemsSchema       sub-schema for array items, or {@code null}
	 * @param ref               unresolved local {@code $ref}, or {@code null}
	 */
	private JsonSchema(Set<JsonValue.Kind> allowedKinds,
			boolean requireInteger,
			List<JsonValue> enumValues,
			JsonValue constValue,
			LinkedHashMap<String, JsonSchema> properties,
			Set<String> required,
			boolean additionalAllowed,
			JsonSchema additionalSchema,
			JsonSchema itemsSchema,
			String ref) {
		this.allowedKinds = allowedKinds;
		this.requireInteger = requireInteger;
		this.enumValues = enumValues;
		this.constValue = constValue;
		this.properties = properties;
		this.required = required;
		this.additionalAllowed = additionalAllowed;
		this.additionalSchema = additionalSchema;
		this.itemsSchema = itemsSchema;
		this.ref = ref;
	}

	/**
	 * Parses a schema node from its JSON representation.
	 *
	 * @param node JSON value representing the schema
	 * @return parsed schema
	 * @throws IllegalArgumentException when the schema uses unsupported keywords
	 *                                  or malformed structure
	 */
	public static JsonSchema parse(JsonValue node) {
		if (node.kind() != JsonValue.Kind.OBJECT) {
			throw new IllegalArgumentException("schema node must be an object, got " + node.kind());
		}
		LinkedHashMap<String, JsonValue> entries = node.asObject();
		validateKeywords(entries);

		if (entries.containsKey("$ref")) {
			return parseRef(entries);
		}

		Set<JsonValue.Kind> allowedKinds = EnumSet.noneOf(JsonValue.Kind.class);
		boolean requireInteger = false;
		if (entries.containsKey("type")) {
			JsonValue typeNode = entries.get("type");
			if (typeNode.kind() == JsonValue.Kind.STRING) {
				String t = typeNode.asString();
				assertAllowedType(t);
				addKind(allowedKinds, t);
				if ("integer".equals(t)) {
					requireInteger = true;
				}
			} else if (typeNode.kind() == JsonValue.Kind.ARRAY) {
				for (JsonValue item : typeNode.asArray()) {
					if (item.kind() != JsonValue.Kind.STRING) {
						throw new IllegalArgumentException("type[] entries must be strings");
					}
					String t = item.asString();
					assertAllowedType(t);
					addKind(allowedKinds, t);
					if ("integer".equals(t)) {
						requireInteger = true;
					}
				}
			} else {
				throw new IllegalArgumentException("type must be a string or array of strings");
			}
		}

		List<JsonValue> enumValues = null;
		if (entries.containsKey("enum")) {
			JsonValue enumNode = entries.get("enum");
			if (enumNode.kind() != JsonValue.Kind.ARRAY) {
				throw new IllegalArgumentException("enum must be an array");
			}
			enumValues = List.copyOf(enumNode.asArray());
			if (enumValues.isEmpty()) {
				throw new IllegalArgumentException("enum must not be empty");
			}
		}

		JsonValue constValue = entries.get("const");

		LinkedHashMap<String, JsonSchema> properties = new LinkedHashMap<>();
		if (entries.containsKey("properties")) {
			JsonValue propsNode = entries.get("properties");
			if (propsNode.kind() != JsonValue.Kind.OBJECT) {
				throw new IllegalArgumentException("properties must be an object");
			}
			for (Map.Entry<String, JsonValue> e : propsNode.asObject().entrySet()) {
				properties.put(e.getKey(), parse(e.getValue()));
			}
		}

		Set<String> required;
		if (entries.containsKey("required")) {
			JsonValue requiredNode = entries.get("required");
			if (requiredNode.kind() != JsonValue.Kind.ARRAY) {
				throw new IllegalArgumentException("required must be an array");
			}
			List<String> names = new ArrayList<>();
			for (JsonValue item : requiredNode.asArray()) {
				if (item.kind() != JsonValue.Kind.STRING) {
					throw new IllegalArgumentException("required entries must be strings");
				}
				names.add(item.asString());
			}
			required = Set.copyOf(names);
		} else {
			required = Collections.emptySet();
		}

		boolean additionalAllowed = true;
		JsonSchema additionalSchema = null;
		if (entries.containsKey("additionalProperties")) {
			JsonValue extras = entries.get("additionalProperties");
			if (extras.kind() == JsonValue.Kind.BOOL) {
				additionalAllowed = extras.asBool();
			} else if (extras.kind() == JsonValue.Kind.OBJECT) {
				additionalSchema = parse(extras);
				additionalAllowed = true;
			} else {
				throw new IllegalArgumentException(
						"additionalProperties must be boolean or object, got " + extras.kind());
			}
		}

		JsonSchema itemsSchema = null;
		if (entries.containsKey("items")) {
			itemsSchema = parse(entries.get("items"));
		}

		return new JsonSchema(allowedKinds, requireInteger, enumValues, constValue,
				properties, required, additionalAllowed, additionalSchema, itemsSchema, null);
	}

	/**
	 * Returns the root-level {@code $defs} entries as parsed sub-schemas.
	 *
	 * @param root root schema node
	 * @return mapping of definition name to parsed schema
	 * @throws IllegalArgumentException when {@code $defs} is malformed
	 */
	public static LinkedHashMap<String, JsonSchema> parseDefs(JsonValue root) {
		LinkedHashMap<String, JsonSchema> result = new LinkedHashMap<>();
		if (root.kind() != JsonValue.Kind.OBJECT) {
			return result;
		}
		LinkedHashMap<String, JsonValue> entries = root.asObject();
		if (!entries.containsKey("$defs")) {
			return result;
		}
		JsonValue defsNode = entries.get("$defs");
		if (defsNode.kind() != JsonValue.Kind.OBJECT) {
			throw new IllegalArgumentException("$defs must be an object");
		}
		for (Map.Entry<String, JsonValue> e : defsNode.asObject().entrySet()) {
			result.put(e.getKey(), parse(e.getValue()));
		}
		return result;
	}

	/**
	 * Returns the allowed JSON kinds (empty when no constraint applies).
	 *
	 * @return immutable view over allowed kinds
	 */
	public Set<JsonValue.Kind> allowedKinds() {
		return allowedKinds;
	}

	/**
	 * Indicates whether the {@code type:"integer"} constraint is in force.
	 *
	 * @return {@code true} when only integer-shaped numbers are accepted
	 */
	public boolean requireInteger() {
		return requireInteger;
	}

	/**
	 * Returns the allowed enum values, or {@code null} when no enum constraint is set.
	 *
	 * @return immutable enum list or {@code null}
	 */
	public List<JsonValue> enumValues() {
		return enumValues;
	}

	/**
	 * Returns the sole allowed value, or {@code null} when no const constraint is set.
	 *
	 * @return const value or {@code null}
	 */
	public JsonValue constValue() {
		return constValue;
	}

	/**
	 * Returns the named property sub-schemas.
	 *
	 * @return ordered mapping of property name to sub-schema
	 */
	public LinkedHashMap<String, JsonSchema> properties() {
		return properties;
	}

	/**
	 * Returns the set of required property names.
	 *
	 * @return immutable set of required names
	 */
	public Set<String> required() {
		return required;
	}

	/**
	 * Indicates whether additional properties are accepted.
	 *
	 * @return {@code true} when extra properties are allowed
	 */
	public boolean additionalAllowed() {
		return additionalAllowed;
	}

	/**
	 * Returns the sub-schema applied to additional properties, or {@code null} when none is set.
	 *
	 * @return additional-property schema or {@code null}
	 */
	public JsonSchema additionalSchema() {
		return additionalSchema;
	}

	/**
	 * Returns the sub-schema applied to array elements, or {@code null} when none is set.
	 *
	 * @return items schema or {@code null}
	 */
	public JsonSchema itemsSchema() {
		return itemsSchema;
	}

	/**
	 * Returns the unresolved {@code $ref} pointer, or {@code null} when none is set.
	 *
	 * @return ref pointer or {@code null}
	 */
	public String ref() {
		return ref;
	}

	/**
	 * Verifies that the schema object only uses supported keywords.
	 *
	 * @param entries schema object entries
	 */
	private static void validateKeywords(LinkedHashMap<String, JsonValue> entries) {
		for (String key : entries.keySet()) {
			if (SUPPORTED_KEYWORDS.contains(key) || IGNORED_METADATA.contains(key)) {
				continue;
			}
			throw new IllegalArgumentException("Unsupported schema keyword: " + key);
		}
	}

	/**
	 * Parses a schema reference object.
	 *
	 * @param entries schema entries containing {@code $ref}
	 * @return parsed reference schema
	 */
	private static JsonSchema parseRef(LinkedHashMap<String, JsonValue> entries) {
		JsonValue refNode = entries.get("$ref");
		if (refNode.kind() != JsonValue.Kind.STRING) {
			throw new IllegalArgumentException("$ref must be a string");
		}
		String pointer = refNode.asString();
		if (!pointer.startsWith("#/$defs/")) {
			throw new IllegalArgumentException(
					"$ref must point to #/$defs/<name>, got '" + pointer + "'");
		}
		for (String key : entries.keySet()) {
			if ("$ref".equals(key) || IGNORED_METADATA.contains(key)) {
				continue;
			}
			throw new IllegalArgumentException(
					"$ref schemas must not carry other validation keywords (found '" + key + "')");
		}
		return new JsonSchema(EnumSet.noneOf(JsonValue.Kind.class), false, null, null,
				new LinkedHashMap<>(), Collections.emptySet(), true, null, null, pointer);
	}

	/**
	 * Validates that a string is one of the supported type tokens.
	 *
	 * @param value type token
	 */
	private static void assertAllowedType(String value) {
		if (!ALLOWED_TYPES.contains(value)) {
			throw new IllegalArgumentException("Unsupported type value: " + value);
		}
	}

	/**
	 * Adds the JSON kind that corresponds to a type token.
	 *
	 * @param target accumulator
	 * @param token  type token
	 */
	private static void addKind(Set<JsonValue.Kind> target, String token) {
		switch (token) {
			case "object":
				target.add(JsonValue.Kind.OBJECT);
				break;
			case "array":
				target.add(JsonValue.Kind.ARRAY);
				break;
			case "string":
				target.add(JsonValue.Kind.STRING);
				break;
			case "number":
			case "integer":
				target.add(JsonValue.Kind.NUMBER);
				break;
			case "boolean":
				target.add(JsonValue.Kind.BOOL);
				break;
			case "null":
				target.add(JsonValue.Kind.NULL);
				break;
			default:
				throw new IllegalArgumentException("Unsupported type token: " + token);
		}
	}
}
