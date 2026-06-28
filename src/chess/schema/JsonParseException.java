package chess.schema;

/**
 * Raised when {@link JsonParser} cannot decode an input string into a JSON value.
 *
 * <p>The exception carries the offset at which parsing failed so callers can
 * produce a deterministic, reviewable diagnostic.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class JsonParseException extends RuntimeException {

	/**
	 * Serialisation identifier.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Byte offset at which the failure occurred.
	 */
	private final int offset;

	/**
	 * Constructs a new exception capturing the failure offset and message.
	 *
	 * @param offset  byte offset where parsing failed
	 * @param message human-readable failure description
	 */
	public JsonParseException(int offset, String message) {
		super("JSON parse error at offset " + offset + ": " + message);
		this.offset = offset;
	}
}
