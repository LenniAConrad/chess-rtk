package chess.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Pure helpers for the provenance contract every CRTK exporter participates in.
 *
 * <p>This class is the single SHA-256 source of truth for {@code chess.io}.
 * The historical helper at {@code RunManifest.sha256(Path)} was private and
 * non-reusable; lifting it here lets every dataset manifest, every supply-chain
 * checker, and every future {@code crtk dataset audit} surface compute hashes
 * the same way without inviting a third copy.</p>
 *
 * <p>Git provenance is resolved deterministically from the local working tree:
 * an explicit {@code CRTK_GIT_COMMIT} environment variable wins (useful for
 * jar-packaged builds where {@code .git} is absent), otherwise {@code .git/HEAD}
 * is followed to its ref to obtain the commit hash. When neither path
 * resolves, the helper returns {@code null} rather than fabricating a value.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Provenance {

	/**
	 * Buffer size used while streaming files into the digest.
	 */
	private static final int DIGEST_BUFFER_SIZE = 1 << 13;

	/**
	 * Environment variable a build pipeline may use to inject the git commit
	 * hash when the {@code .git} directory is not on disk.
	 */
	private static final String COMMIT_ENV = "CRTK_GIT_COMMIT";

	/**
	 * Utility class; prevent instantiation.
	 */
	private Provenance() {
		// utility
	}

	/**
	 * Returns the SHA-256 hex digest of a file's content.
	 *
	 * @param path file path
	 * @return lowercase hex digest string
	 * @throws IOException when the file cannot be read or hashed
	 */
	public static String sha256(Path path) throws IOException {
		MessageDigest digest = newDigest();
		try (InputStream in = Files.newInputStream(path)) {
			byte[] buffer = new byte[DIGEST_BUFFER_SIZE];
			int read;
			while ((read = in.read(buffer)) >= 0) {
				digest.update(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/**
	 * Returns the SHA-256 hex digest of a byte array.
	 *
	 * @param data byte payload
	 * @return lowercase hex digest string
	 */
	public static String sha256(byte[] data) {
		return HexFormat.of().formatHex(newDigest().digest(data));
	}

	/**
	 * Returns the current git commit hash when discoverable, or {@code null}
	 * when neither the {@code CRTK_GIT_COMMIT} environment variable nor the
	 * local {@code .git} directory yields a usable value.
	 *
	 * @return git commit hash or {@code null}
	 */
	public static String gitCommit() {
		String injected = System.getenv(COMMIT_ENV);
		if (injected != null && !injected.isBlank()) {
			return injected.strip();
		}
		Path gitDir = Path.of(".git");
		if (!Files.isDirectory(gitDir)) {
			return null;
		}
		Path headFile = gitDir.resolve("HEAD");
		if (!Files.exists(headFile)) {
			return null;
		}
		try {
			String head = Files.readString(headFile, StandardCharsets.UTF_8).strip();
			if (!head.startsWith("ref: ")) {
				return head;
			}
			Path refPath = gitDir.resolve(head.substring("ref: ".length()).strip());
			if (!Files.exists(refPath)) {
				return null;
			}
			return Files.readString(refPath, StandardCharsets.UTF_8).strip();
		} catch (IOException ex) {
			return null;
		}
	}

	/**
	 * Constructs a fresh SHA-256 message digest.
	 *
	 * @return ready-to-update digest
	 */
	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable on this JDK", ex);
		}
	}
}
