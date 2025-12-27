package chess.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility for managing the session cache directory used by logging.
 */
public final class SessionCache {

	/**
	 * Default location for session artifacts.
	 */
	private static final Path SESSION_DIR = Paths.get("session");

	/**
	 * Prevents instantiation of this utility class.
	 */
	private SessionCache() {
	}

	/**
	 * Returns the default session directory path.
	 *
	 * @return session directory path
	 */
	public static Path directory() {
		return SESSION_DIR;
	}

	/**
	 * Ensures the session directory exists on disk.
	 *
	 * @throws IOException if the directory cannot be created
	 */
	public static void ensureDirectory() throws IOException {
		Files.createDirectories(SESSION_DIR);
	}

	/**
	 * Deletes all files and subdirectories inside the session directory. The
	 * directory itself is preserved.
	 *
	 * @throws IOException if the path is not a directory or deletion fails
	 */
	public static void clean() throws IOException {
		if (!Files.exists(SESSION_DIR)) {
			return;
		}
		if (!Files.isDirectory(SESSION_DIR)) {
			throw new IOException("Session path is not a directory: " + SESSION_DIR);
		}

		try (Stream<Path> walk = Files.walk(SESSION_DIR)) {
			List<Path> targets = walk
					.filter(p -> !p.equals(SESSION_DIR))
					.sorted(Comparator.reverseOrder())
					.collect(Collectors.toList());
			for (Path p : targets) {
				Files.deleteIfExists(p);
			}
		}
	}
}
