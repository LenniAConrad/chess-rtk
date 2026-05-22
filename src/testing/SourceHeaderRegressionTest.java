/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Regression checks for repository-wide Java source attribution headers.
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
     * Required top-of-file Java source attribution header.
     */
    private static final String REQUIRED_HEADER = """
            /**
             * Source file attribution.
             *
             * @author Lennart A. Conrad
             */

            """;

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
     * Runs all source-header regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        List<Path> missingHeaders = new ArrayList<>();
        for (Path sourceFile : javaSources()) {
            if (!hasRequiredHeader(sourceFile)) {
                missingHeaders.add(sourceFile);
            }
        }
        if (!missingHeaders.isEmpty()) {
            throw new AssertionError("missing Java source attribution header: " + missingHeaders);
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
     * Verifies one source file starts with the standard attribution header.
     *
     * @param sourceFile Java source file
     * @return true when the file starts with the required header
     */
    private static boolean hasRequiredHeader(Path sourceFile) {
        try {
            return Files.readString(sourceFile, StandardCharsets.UTF_8).startsWith(REQUIRED_HEADER);
        } catch (IOException ex) {
            throw new AssertionError("could not read Java source file " + sourceFile, ex);
        }
    }
}
