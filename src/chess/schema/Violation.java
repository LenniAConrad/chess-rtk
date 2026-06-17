package chess.schema;

/**
 * Describes a single schema violation discovered by {@link Validator}.
 *
 * <p>The {@code pointer} field follows RFC 6901 JSON-pointer syntax (for
 * example {@code "/commands/0/path"}) so consumers can address the offending
 * location unambiguously.</p>
 *
 * @param pointer JSON pointer at which the violation was detected
 * @param message human-readable failure description
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public record Violation(String pointer, String message) {

	/**
	 * Renders the violation as {@code "<pointer>: <message>"}.
	 *
	 * @return formatted violation string
	 */
	public String render() {
		return (pointer == null || pointer.isEmpty() ? "/" : pointer) + ": " + message;
	}
}
