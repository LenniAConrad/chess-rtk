package application.gui.workbench.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Safe file creation helpers for session artifacts.
 */
final class SessionFiles {

    /**
     * Maximum number of filename suffix attempts before failing.
     */
    private static final int MAX_ATTEMPTS = 1_000;

    /**
     * Prevents instantiation.
     */
    private SessionFiles() {
        // utility
    }

    /**
     * Writes text into a new session file without overwriting an existing path.
     *
     * @param directory target directory
     * @param preferredName preferred filename
     * @param text file contents
     * @return created file path
     * @throws IOException when the file cannot be created
     */
    static Path writeString(Path directory, String preferredName, String text) throws IOException {
        Path dir = prepareDirectory(directory);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Path path = dir.resolve(candidateName(preferredName, attempt)).normalize();
            if (!dir.equals(path.getParent())) {
                throw new IOException("Refusing to write outside session directory: " + preferredName);
            }
            try {
                Files.writeString(path, text, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return path.toAbsolutePath().normalize();
            } catch (FileAlreadyExistsException ex) {
                // Try a suffixed filename without clobbering the existing path.
            }
        }
        throw new FileAlreadyExistsException(dir.resolve(preferredName).toString());
    }

    /**
     * Ensures a target directory exists and is not reached through a symbolic
     * link.
     *
     * @param directory target directory
     * @return absolute normalized directory path
     * @throws IOException when the directory is unsafe or cannot be created
     */
    private static Path prepareDirectory(Path directory) throws IOException {
        Path dir = directory.toAbsolutePath().normalize();
        rejectSymbolicLinkAncestor(dir);
        if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Session artifact path is not a directory: " + dir);
        }
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Rejects an existing symbolic link in the directory path.
     *
     * @param path directory path to inspect
     * @throws IOException when an ancestor is a symbolic link
     */
    private static void rejectSymbolicLinkAncestor(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(current)) {
                throw new IOException("Refusing session artifact path through symbolic link: " + current);
            }
        }
    }

    /**
     * Builds a candidate filename for a collision attempt.
     *
     * @param preferredName preferred filename
     * @param attempt zero-based attempt index
     * @return candidate filename
     */
    private static String candidateName(String preferredName, int attempt) {
        if (attempt == 0) {
            return preferredName;
        }
        int dot = preferredName.lastIndexOf('.');
        if (dot <= 0) {
            return preferredName + "-" + (attempt + 1);
        }
        return preferredName.substring(0, dot) + "-" + (attempt + 1)
                + preferredName.substring(dot);
    }
}
