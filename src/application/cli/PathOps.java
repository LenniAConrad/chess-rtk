package application.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Path and filesystem helpers used throughout the CLI implementation.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class PathOps {

	/**
	 * Default directory for user-visible generated files.
	 */
	public static final Path DEFAULT_DUMP_DIR = Paths.get("dump");

	/**
	 * Utility class; prevent instantiation.
	 */
	private PathOps() {
		// utility
	}

	/**
	 * Determines whether the given path refers to an executable available on the
	 * current {@code PATH}.
	 *
	 * <p>Handles absolute paths, relative paths containing a separator, and
	 * simple filenames resolved directly from {@code PATH}.
	 *
	 * @param path relative or absolute executable name
	 * @return {@code true} when the executable exists and is runnable
	 */
	public static boolean isExecutableOnPath(String path) {
		if (path == null || path.isEmpty()) {
			return false;
		}
		Path direct = Paths.get(path);
		if (direct.isAbsolute() || path.contains(File.separator)) {
			return Files.isExecutable(direct);
		}
		String env = System.getenv("PATH");
		if (env == null || env.isEmpty()) {
			return false;
		}
		for (String dir : env.split(File.pathSeparator)) {
			if (dir == null || dir.isEmpty()) {
				continue;
			}
			Path candidate = Paths.get(dir, path);
			if (Files.isExecutable(candidate)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates the parent directory of the supplied path if it is missing.
	 *
	 * @param p path whose parent directory should exist
	 * @throws IOException when directory creation fails
	 */
	public static void ensureParentDir(Path p) throws IOException {
		Path parent = p.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}

	/**
	 * Creates a temporary file inside the repository-local ignored output tree.
	 *
	 * <p>This avoids the system-wide temporary directory for generated CRTK
	 * artifacts while keeping the files out of source control.</p>
	 *
	 * @param prefix filename prefix
	 * @param suffix filename suffix
	 * @return temporary file path
	 * @throws IOException when directory or file creation fails
	 */
	public static Path createLocalTempFile(String prefix, String suffix) throws IOException {
		Path dir = Paths.get("out", "tmp");
		Files.createDirectories(dir);
		return Files.createTempFile(dir, prefix, suffix);
	}

	/**
	 * Creates a temporary directory inside the repository-local ignored output tree.
	 *
	 * @param prefix directory prefix
	 * @return temporary directory path
	 * @throws IOException when directory creation fails
	 */
	public static Path createLocalTempDirectory(String prefix) throws IOException {
		Path dir = Paths.get("out", "tmp");
		Files.createDirectories(dir);
		return Files.createTempDirectory(dir, prefix);
	}

	/**
	 * Returns a path inside the default dump directory.
	 *
	 * @param filename filename to resolve under {@code dump/}
	 * @return dump-local path
	 */
	public static Path dumpPath(String filename) {
		if (filename == null || filename.isBlank()) {
			return DEFAULT_DUMP_DIR;
		}
		return DEFAULT_DUMP_DIR.resolve(validateDumpFilename(filename));
	}

	/**
	 * Validates that a dump output name is a single filename, not a path.
	 *
	 * @param filename candidate filename
	 * @return normalized filename text
	 */
	private static String validateDumpFilename(String filename) {
		String trimmed = filename.trim();
		if (".".equals(trimmed) || "..".equals(trimmed)) {
			throw new IllegalArgumentException("dump filename must not be a traversal segment: " + filename);
		}
		Path path = Paths.get(trimmed);
		if (path.isAbsolute() || path.getParent() != null
				|| trimmed.contains("/") || trimmed.contains("\\")) {
			throw new IllegalArgumentException("dump filename must not contain path separators: " + filename);
		}
		return trimmed;
	}

	/**
	 * Derives an output path in the default dump directory by replacing the
	 * input's extension with the given suffix.
	 *
	 * @param input  input path used to derive the stem
	 * @param suffix suffix to append (including dot)
	 * @return new path under {@code dump/} with the derived filename
	 */
	public static Path deriveOutputPath(Path input, String suffix) {
		String name = input.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String stem = (dot > 0) ? name.substring(0, dot) : name;
		return dumpPath(stem + suffix);
	}
}
