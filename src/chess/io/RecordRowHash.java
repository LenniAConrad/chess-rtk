package chess.io;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Stable row-hash helpers for record-derived dataset artifacts.
 *
 * <p>The v1 hash intentionally names its input contract: it hashes the raw
 * record JSON object text after trimming outer whitespace. That is strong
 * enough to prove split partitioning today, while leaving room for a later
 * semantic row hash over canonical FEN + label tuples without changing this
 * contract under consumers.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordRowHash {

	/**
	 * Public algorithm label stored in manifests.
	 */
	public static final String RAW_RECORD_JSON_V1 = "sha256(raw-record-json-v1)";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordRowHash() {
		// utility
	}

	/**
	 * Hashes one raw record JSON object.
	 *
	 * @param json raw record JSON object text
	 * @return lowercase SHA-256 hex digest
	 */
	public static String rawRecordJsonV1(String json) {
		String normalized = json == null ? "" : json.strip();
		return sha256Hex(normalized.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Hashes a sorted list of row hashes, preserving duplicates.
	 *
	 * @param rowHashes per-row hashes
	 * @return lowercase SHA-256 hex digest of newline-delimited sorted hashes
	 */
	public static String sortedMultisetDigest(List<String> rowHashes) {
		List<String> sorted = new ArrayList<>(rowHashes);
		sorted.sort(String::compareTo);
		MessageDigest digest = newDigest();
		for (String rowHash : sorted) {
			digest.update(rowHash.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) '\n');
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/**
	 * Hashes bytes with SHA-256.
	 *
	 * @param bytes input bytes
	 * @return lowercase SHA-256 hex digest
	 */
	private static String sha256Hex(byte[] bytes) {
		return HexFormat.of().formatHex(newDigest().digest(bytes));
	}

	/**
	 * Constructs a fresh SHA-256 digest.
	 *
	 * @return ready-to-use digest
	 */
	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable on this JDK", ex);
		}
	}
}
