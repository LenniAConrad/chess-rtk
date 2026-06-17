package chess.io;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import chess.core.Position;
import chess.schema.JsonParser;
import chess.schema.JsonValue;
import utility.Json;

/**
 * Deterministic key derivation for record-file deduplication.
 *
 * <p>The public surface intentionally stays small: callers choose a key and
 * keep policy, then stream records through {@link #keyFor(String, Key)}. The
 * row-hash path canonicalizes JSON objects with sorted keys, so semantically
 * identical rows are not kept twice just because field order changed.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordDeduper {

	/**
	 * Field used to derive FEN-based keys.
	 */
	private static final String POSITION_FIELD = "position";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordDeduper() {
		// utility
	}

	/**
	 * Computes the deduplication key for one raw record JSON object.
	 *
	 * @param json raw record JSON object
	 * @param key  key strategy
	 * @return stable deduplication key
	 */
	public static String keyFor(String json, Key key) {
		Key resolved = (key == null) ? Key.POSITION_SIGNATURE : key;
		return switch (resolved) {
			case POSITION_SIGNATURE -> prefix("position-signature",
					nonBlankOrFallback(RecordSplitter.groupKey(json, RecordSplitter.Strategy.FEN), json));
			case FEN_EXACT -> prefix("fen-exact", nonBlankOrFallback(canonicalFen(json), json));
			case ROW_HASH -> prefix("row-hash", rowHash(json));
		};
	}

	/**
	 * Parses a user-facing key token.
	 *
	 * @param token key token; {@code null} defaults to {@code position-signature}
	 * @return parsed key
	 */
	public static Key parseKey(String token) {
		if (token == null || token.isBlank()) {
			return Key.POSITION_SIGNATURE;
		}
		return switch (token.toLowerCase(Locale.ROOT)) {
			case "position", "position-signature", "signature", "fen-position" -> Key.POSITION_SIGNATURE;
			case "fen", "fen-exact", "canonical-fen" -> Key.FEN_EXACT;
			case "row", "row-hash", "json", "json-row" -> Key.ROW_HASH;
			default -> throw new IllegalArgumentException("unsupported --key '" + token
					+ "' (expected position-signature, fen-exact, or row-hash)");
		};
	}

	/**
	 * Parses a user-facing keep token.
	 *
	 * @param token keep token; {@code null} defaults to {@code first}
	 * @return parsed keep policy
	 */
	public static Keep parseKeep(String token) {
		if (token == null || token.isBlank()) {
			return Keep.FIRST;
		}
		return switch (token.toLowerCase(Locale.ROOT)) {
			case "first" -> Keep.FIRST;
			case "last" -> Keep.LAST;
			case "highest-depth" -> throw new IllegalArgumentException(
					"--keep highest-depth is reserved until record analysis depth is extracted "
							+ "from the canonical analysis schema; use first or last today");
			default -> throw new IllegalArgumentException("unsupported --keep '" + token
					+ "' (expected first or last)");
		};
	}

	/**
	 * Returns the user-facing token for a key.
	 *
	 * @param key key enum
	 * @return token used in summaries and manifests
	 */
	public static String keyToken(Key key) {
		return switch ((key == null) ? Key.POSITION_SIGNATURE : key) {
			case POSITION_SIGNATURE -> "position-signature";
			case FEN_EXACT -> "fen-exact";
			case ROW_HASH -> "row-hash";
		};
	}

	/**
	 * Returns the user-facing token for a keep policy.
	 *
	 * @param keep keep enum
	 * @return token used in summaries and manifests
	 */
	public static String keepToken(Keep keep) {
		return switch ((keep == null) ? Keep.FIRST : keep) {
			case FIRST -> "first";
			case LAST -> "last";
		};
	}

	/**
	 * Computes a canonical full-FEN key from the record's {@code position} field.
	 *
	 * @param json raw record JSON object
	 * @return canonical full FEN or empty text when unavailable
	 */
	private static String canonicalFen(String json) {
		String rawFen = Json.parseStringField(json, POSITION_FIELD);
		if (rawFen == null || rawFen.isBlank()) {
			return "";
		}
		try {
			return new Position(rawFen).toString();
		} catch (RuntimeException ex) {
			return "";
		}
	}

	/**
	 * Uses the computed value when present, else falls back to a full-row hash.
	 *
	 * @param value computed key value
	 * @param json  raw record JSON object
	 * @return non-empty key material
	 */
	private static String nonBlankOrFallback(String value, String json) {
		if (value != null && !value.isBlank()) {
			return value;
		}
		return "missing-position:" + rowHash(json);
	}

	/**
	 * Prefixes key material with its strategy so unlike keys cannot collide.
	 *
	 * @param prefix key strategy token
	 * @param value  key material
	 * @return prefixed key
	 */
	private static String prefix(String prefix, String value) {
		return prefix + ":" + value;
	}

	/**
	 * Computes a SHA-256 hash over canonical JSON for a record row.
	 *
	 * @param json raw record JSON object
	 * @return SHA-256 hex digest
	 */
	private static String rowHash(String json) {
		String material;
		try {
			material = canonicalJson(JsonParser.parse(json));
		} catch (RuntimeException ex) {
			material = (json == null) ? "" : json.strip();
		}
		return sha256(material);
	}

	/**
	 * Serializes a parsed JSON tree with deterministic object key ordering.
	 *
	 * @param value parsed value
	 * @return canonical JSON text
	 */
	private static String canonicalJson(JsonValue value) {
		return switch (value.kind()) {
			case NULL -> "null";
			case BOOL -> Boolean.toString(value.asBool());
			case NUMBER -> canonicalNumber(value);
			case STRING -> "\"" + Json.esc(value.asString()) + "\"";
			case ARRAY -> canonicalArray(value.asArray());
			case OBJECT -> canonicalObject(value.asObject());
		};
	}

	/**
	 * Serializes a JSON number using the parser's normalized numeric value.
	 *
	 * @param value numeric JSON value
	 * @return canonical number text
	 */
	private static String canonicalNumber(JsonValue value) {
		double number = value.asNumber();
		if (value.numberIsInteger() && number >= Long.MIN_VALUE && number <= Long.MAX_VALUE) {
			return Long.toString((long) number);
		}
		return Double.toString(number == 0.0 ? 0.0 : number);
	}

	/**
	 * Serializes a JSON array.
	 *
	 * @param values array values
	 * @return canonical array text
	 */
	private static String canonicalArray(List<JsonValue> values) {
		StringBuilder out = new StringBuilder();
		out.append('[');
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				out.append(',');
			}
			out.append(canonicalJson(values.get(i)));
		}
		return out.append(']').toString();
	}

	/**
	 * Serializes a JSON object with lexicographically sorted keys.
	 *
	 * @param values object values
	 * @return canonical object text
	 */
	private static String canonicalObject(Map<String, JsonValue> values) {
		List<String> keys = new ArrayList<>(values.keySet());
		keys.sort(Comparator.naturalOrder());
		StringBuilder out = new StringBuilder();
		out.append('{');
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0) {
				out.append(',');
			}
			String key = keys.get(i);
			out.append('"').append(Json.esc(key)).append("\":");
			out.append(canonicalJson(values.get(key)));
		}
		return out.append('}').toString();
	}

	/**
	 * Computes a SHA-256 digest for text.
	 *
	 * @param text input text
	 * @return hex digest
	 */
	private static String sha256(String text) {
		MessageDigest digest = newDigest();
		byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
		StringBuilder out = new StringBuilder(hash.length * 2);
		for (byte b : hash) {
			out.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
		}
		return out.toString();
	}

	/**
	 * Constructs a SHA-256 digest.
	 *
	 * @return digest instance
	 */
	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable on this JDK", ex);
		}
	}

	/**
	 * Deduplication key strategy.
	 */
	public enum Key {

		/**
		 * Group by position identity: canonical FEN without halfmove/fullmove counters.
		 */
		POSITION_SIGNATURE,

		/**
		 * Group by canonical full FEN, including halfmove/fullmove counters.
		 */
		FEN_EXACT,

		/**
		 * Group by canonical full-row JSON hash.
		 */
		ROW_HASH
	}

	/**
	 * Collision resolution strategy.
	 */
	public enum Keep {

		/**
		 * Keep the first row seen for a duplicate key.
		 */
		FIRST,

		/**
		 * Keep the last row seen for a duplicate key.
		 */
		LAST
	}
}
