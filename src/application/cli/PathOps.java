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
	 * Derives an output path by replacing the input's extension with the given suffix.
	 *
	 * @param input  input path used to derive the stem
	 * @param suffix suffix to append (including dot)
	 * @return new path alongside the input path with the new suffix
	 */
	public static Path deriveOutputPath(Path input, String suffix) {
		String name = input.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String stem = (dot > 0) ? name.substring(0, dot) : name;
		return input.resolveSibling(stem + suffix);
	}
}
