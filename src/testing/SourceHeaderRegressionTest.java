package testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Regression checks for repository-wide Java source layout.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SourceHeaderRegressionTest {

    /**
     * Java source root checked by this regression.
     */
    private static final Path SOURCE_ROOT = Path.of("src");

    /**
     * Detached top-of-file attribution banner that should not be used.
     */
    private static final String DETACHED_ATTRIBUTION_HEADER = "/**\n"
            + " * Source file " + "attribution.\n"
            + " *\n"
            + " * @author Lennart A. Conrad\n"
            + " */\n\n";

    /**
     * Java source filename suffix.
     */
    private static final String JAVA_SOURCE_SUFFIX = ".java";

    /**
     * Prevents instantiation.
     */
    private SourceHeaderRegressionTest() {
        // utility
    }

    /**
     * Runs all source-layout regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        List<Path> filesWithDetachedHeaders = new ArrayList<>();
        for (Path sourceFile : javaSources()) {
            if (hasDetachedAttributionHeader(sourceFile)) {
                filesWithDetachedHeaders.add(sourceFile);
            }
        }
        if (!filesWithDetachedHeaders.isEmpty()) {
            throw new AssertionError("detached Java source attribution header: " + filesWithDetachedHeaders);
        }
        System.out.println("SourceHeaderRegressionTest: all checks passed");
    }

    /**
     * Lists Java source files.
     *
     * @return sorted Java source paths
     */
    private static List<Path> javaSources() {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(JAVA_SOURCE_SUFFIX))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new AssertionError("could not list Java source files", ex);
        }
    }

    /**
     * Verifies whether one source file starts with a detached attribution banner.
     *
     * @param sourceFile Java source file
     * @return true when the file starts with the detached banner
     */
    private static boolean hasDetachedAttributionHeader(Path sourceFile) {
        try {
            return Files.readString(sourceFile, StandardCharsets.UTF_8).startsWith(DETACHED_ATTRIBUTION_HEADER);
        } catch (IOException ex) {
            throw new AssertionError("could not read Java source file " + sourceFile, ex);
        }
    }
}
