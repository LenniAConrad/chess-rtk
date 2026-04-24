package utility;

/**
 * Small helpers for writing simple TOML manifest fields.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class TomlWriterSupport {

	/**
	 * Utility class; prevent instantiation.
	 */
	private TomlWriterSupport() {
		// utility
	}

	/**
	 * Appends one string field.
	 *
	 * @param sb target builder
	 * @param key TOML key
	 * @param value string value
	 */
	public static void appendString(StringBuilder sb, String key, String value) {
		sb.append(key).append(" = ").append(quote(value)).append('\n');
	}

	/**
	 * Appends one integer field.
	 *
	 * @param sb target builder
	 * @param key TOML key
	 * @param value integer value
	 */
	public static void appendInt(StringBuilder sb, String key, int value) {
		sb.append(key).append(" = ").append(value).append('\n');
	}

	/**
	 * Appends one floating-point field.
	 *
	 * @param sb target builder
	 * @param key TOML key
	 * @param value numeric value
	 */
	public static void appendDouble(StringBuilder sb, String key, double value) {
		sb.append(key).append(" = ").append(Double.toString(value)).append('\n');
	}

	/**
	 * Appends one boolean field.
	 *
	 * @param sb target builder
	 * @param key TOML key
	 * @param value boolean value
	 */
	public static void appendBoolean(StringBuilder sb, String key, boolean value) {
		sb.append(key).append(" = ").append(value).append('\n');
	}

	/**
	 * Appends one string-array field when values exist.
	 *
	 * @param sb target builder
	 * @param key TOML key
	 * @param values array values
	 */
	public static void appendStringArray(StringBuilder sb, String key, String[] values) {
		if (values == null || values.length == 0) {
			return;
		}
		sb.append(key).append(" = [");
		boolean first = true;
		for (String value : values) {
			if (!first) {
				sb.append(", ");
			}
			sb.append(quote(value));
			first = false;
		}
		sb.append("]\n");
	}

	/**
	 * Quotes one TOML string using the basic-string escape rules needed by
	 * manifest formats.
	 *
	 * @param value source text
	 * @return quoted TOML string
	 */
	public static String quote(String value) {
		String safe = value == null ? "" : value;
		StringBuilder escaped = new StringBuilder(safe.length() + 8).append('"');
		for (int i = 0; i < safe.length(); i++) {
			char ch = safe.charAt(i);
			switch (ch) {
				case '\\' -> escaped.append("\\\\");
				case '"' -> escaped.append("\\\"");
				case '\b' -> escaped.append("\\b");
				case '\t' -> escaped.append("\\t");
				case '\n' -> escaped.append("\\n");
				case '\f' -> escaped.append("\\f");
				case '\r' -> escaped.append("\\r");
				default -> escaped.append(ch);
			}
		}
		return escaped.append('"').toString();
	}
}
