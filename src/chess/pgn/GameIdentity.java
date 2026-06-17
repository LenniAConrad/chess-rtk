package chess.pgn;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import chess.struct.Game;
import chess.struct.Pgn;

/**
 * Computes the canonical identifier for a {@link Game}: a deterministic
 * SHA-256 digest over the game's headers and mainline PGN text.
 *
 * <p>The identifier is the join key of {@link PgnStore}: import is idempotent
 * on this value, and lookup by id is O(1) via the gameId-to-byte-offset
 * sidecar index. Determinism matters here — two PGN-equivalent imports of the
 * same game must produce the same id even when the source files differ in
 * whitespace, header ordering, or comment placement.</p>
 *
 * <p>Normalisation rules applied before hashing:</p>
 * <ul>
 *   <li>Header keys are case-folded to lower case;</li>
 *   <li>headers are sorted alphabetically by key so source ordering does
 *       not change the id;</li>
 *   <li>header values are trimmed;</li>
 *   <li>the move text is the round-tripped {@code Pgn.toPgn} output stripped
 *       of leading whitespace, so equivalent move sequences hash the same
 *       regardless of input formatting.</li>
 * </ul>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GameIdentity {

	/**
	 * Algorithm token used for the digest.
	 */
	private static final String DIGEST_ALGORITHM = "SHA-256";

	/**
	 * Field separator interposed between successive normalised lines.
	 */
	private static final char FIELD_SEPARATOR = '\n';

	/**
	 * Utility class; prevent instantiation.
	 */
	private GameIdentity() {
		// utility
	}

	/**
	 * Returns the canonical hex SHA-256 game identifier.
	 *
	 * @param game source game (must not be {@code null})
	 * @return lowercase hex digest string
	 */
	public static String compute(Game game) {
		MessageDigest digest = newDigest();
		digest.update(canonicalSource(game).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(digest.digest());
	}

	/**
	 * Renders the normalised source string the identifier is computed over,
	 * exposed for test diagnostics and corpus debugging.
	 *
	 * @param game source game
	 * @return canonical source string
	 */
	public static String canonicalSource(Game game) {
		StringBuilder sb = new StringBuilder(256);
		appendHeaders(sb, game.getTags());
		sb.append(FIELD_SEPARATOR);
		sb.append("--moves--").append(FIELD_SEPARATOR);
		sb.append(Pgn.toPgn(game).strip());
		return sb.toString();
	}

	/**
	 * Appends the headers in case-folded, alphabetically sorted form.
	 *
	 * @param sb      destination buffer
	 * @param headers raw header map
	 */
	private static void appendHeaders(StringBuilder sb, Map<String, String> headers) {
		List<String> orderedKeys = new ArrayList<>(headers.keySet());
		Collections.sort(orderedKeys, String.CASE_INSENSITIVE_ORDER);
		for (String key : orderedKeys) {
			String value = headers.get(key);
			sb.append(key.toLowerCase(Locale.ROOT))
					.append('=')
					.append(value == null ? "" : value.strip())
					.append(FIELD_SEPARATOR);
		}
	}

	/**
	 * Constructs a fresh digest, wrapping the unchecked algorithm-missing case.
	 *
	 * @return ready-to-update digest
	 */
	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance(DIGEST_ALGORITHM);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(DIGEST_ALGORITHM + " unavailable on this JDK", ex);
		}
	}
}
