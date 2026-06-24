package testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import application.Main;

/**
 * Shared helpers for command-line regression tests.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class TestSupport {

    /**
     * Captured command failure details.
     *
     * @param exitCode process exit code
     * @param stdout captured standard output
     * @param stderr captured standard error
     */
    record FailureResult(
        /**
         * Stores the process exit code.
         */
        int exitCode,
        /**
         * Stores captured standard output.
         */
        String stdout,
        /**
         * Stores captured standard error.
         */
        String stderr
    ) {
    }

    /**
     * Captured command details without constraining success or failure.
     *
     * @param exitCode process exit code
     * @param stdout captured standard output
     * @param stderr captured standard error
     */
    record RunResult(
        /**
         * Stores the process exit code.
         */
        int exitCode,
        /**
         * Stores captured standard output.
         */
        String stdout,
        /**
         * Stores captured standard error.
         */
        String stderr
    ) {
    }

    /**
     * Prevents instantiation of this utility class.
     */
    private TestSupport() {
        // utility
    }

    /**
     * Captures standard output while invoking the application entry point.
     *
     * @param args command-line arguments to pass to {@link Main#main(String[])}
     * @return captured standard output
     */
    static String runMain(String... args) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream replacement = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(replacement);
            int exitCode = Main.run(args);
            if (exitCode != 0) {
                throw new AssertionError("command failed with exit code " + exitCode + ": "
                        + String.join(" ", args));
            }
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * Captures standard output and standard error for a command that is expected
     * to fail.
     *
     * @param args command-line arguments to pass to {@link Main#main(String[])}
     * @return captured exit status and streams
     */
    static FailureResult runMainExpectFailure(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        try (PrintStream outReplacement = new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8);
                PrintStream errReplacement = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8)) {
            System.setOut(outReplacement);
            System.setErr(errReplacement);
            int exitCode = Main.run(args);
            if (exitCode == 0) {
                throw new AssertionError("command unexpectedly succeeded: " + String.join(" ", args));
            }
            return new FailureResult(
                    exitCode,
                    stdoutBuffer.toString(StandardCharsets.UTF_8),
                    stderrBuffer.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    /**
     * Captures standard output and standard error for a command with any exit code.
     *
     * @param args command-line arguments to pass to {@link Main#main(String[])}
     * @return captured exit status and streams
     */
    static RunResult runMainAny(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        try (PrintStream outReplacement = new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8);
                PrintStream errReplacement = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8)) {
            System.setOut(outReplacement);
            System.setErr(errReplacement);
            return new RunResult(
                    Main.run(args),
                    stdoutBuffer.toString(StandardCharsets.UTF_8),
                    stderrBuffer.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    /**
     * Captures standard output while running a test action.
     *
     * @param action action to execute
     * @return captured standard output
     */
    static String captureStdout(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream replacement = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(replacement);
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * Verifies a boolean condition.
     *
     * @param condition condition to verify
     * @param label assertion label
     */
    static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + ": expected true");
        }
    }

    /**
     * Verifies that a boolean condition is false.
     *
     * @param condition condition to verify
     * @param label assertion label
     */
    static void assertFalse(boolean condition, String label) {
        if (condition) {
            throw new AssertionError(label + ": expected false");
        }
    }

    /**
     * Verifies integer equality.
     *
     * @param expected expected test value
     * @param actual actual test value
     * @param label assertion label
     */
    static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies long equality.
     *
     * @param expected expected test value
     * @param actual actual test value
     * @param label assertion label
     */
    static void assertEquals(long expected, long actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies byte equality.
     *
     * @param expected expected test value
     * @param actual actual test value
     * @param label assertion label
     */
    static void assertEquals(byte expected, byte actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies string equality.
     *
     * @param expected expected test value
     * @param actual actual test value
     * @param label assertion label
     */
    static void assertEquals(String expected, String actual, String label) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies object equality.
     *
     * @param expected expected test value
     * @param actual actual test value
     * @param label assertion label
     */
    static void assertEquals(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies object identity.
     *
     * @param expected expected object
     * @param actual actual object
     * @param label assertion label
     */
    static void assertSame(Object expected, Object actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected same instance");
        }
    }

    /**
     * Verifies that a value is non-null.
     *
     * @param value value to inspect
     * @param label assertion label
     */
    static void assertNotNull(Object value, String label) {
        if (value == null) {
            throw new AssertionError(label + ": expected non-null");
        }
    }

    /**
     * Verifies that text contains expected content.
     *
     * @param value value to inspect
     * @param needle expected substring
     */
    static void assertContains(String value, String needle) {
        if (!value.contains(needle)) {
            throw new AssertionError("Expected to find " + needle + " in " + value);
        }
    }

    /**
     * Verifies that text contains expected content.
     *
     * @param value value to inspect
     * @param needle expected substring
     * @param label assertion label
     */
    static void assertContains(String value, String needle, String label) {
        if (!value.contains(needle)) {
            throw new AssertionError(label + ": expected to find " + needle + " in " + value);
        }
    }

    /**
     * Verifies that at least one value contains expected content.
     *
     * @param values values to inspect
     * @param needle expected substring
     * @param label assertion label
     */
    static void assertContains(String[] values, String needle, String label) {
        for (String value : values) {
            if (value.contains(needle)) {
                return;
            }
        }
        throw new AssertionError(label + ": expected substring " + needle);
    }

    /**
     * Verifies that an action throws {@link IllegalArgumentException}.
     *
     * @param action action expected to throw
     * @param label assertion label
     */
    static void assertThrows(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + ": expected IllegalArgumentException");
    }

    /**
     * Verifies boolean-array equality.
     *
     * @param expected expected values
     * @param actual actual values
     * @param label assertion label
     */
    static void assertArrayEquals(boolean[] expected, boolean[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + Arrays.toString(expected)
                    + ", got " + Arrays.toString(actual));
        }
    }

    /**
     * Verifies approximate float equality.
     *
     * @param expected expected test value
     * @param actual actual test value
     * @param label assertion label
     */
    static void assertClose(float expected, float actual, String label) {
        if (Math.abs(expected - actual) > 1.0e-5f) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies approximate double equality.
     *
     * @param expected expected test value
     * @param actual actual test value
     * @param tolerance accepted absolute difference
     * @param label assertion label
     */
    static void assertNear(double expected, double actual, double tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Creates a temporary test workspace.
     *
     * @param prefix directory-name prefix
     * @return created directory
     */
    static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException ex) {
            throw new AssertionError("failed to create workspace: " + ex.getMessage(), ex);
        }
    }

    /**
     * Writes UTF-8 test content.
     *
     * @param path file path
     * @param content file content
     * @return written file path
     */
    static Path writeUtf8(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return path;
        } catch (IOException ex) {
            throw new AssertionError("failed to write " + path + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Reads UTF-8 test content.
     *
     * @param path file path
     * @return file content
     */
    static String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("failed to read " + path + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Reads ISO-8859-1 test content, commonly used for PDF metadata checks.
     *
     * @param path file path
     * @return file content
     */
    static String readLatin1(Path path) {
        try {
            return Files.readString(path, StandardCharsets.ISO_8859_1);
        } catch (IOException ex) {
            throw new AssertionError("failed to read " + path + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Reads ISO-8859-1 test content and verifies the file is larger than a
     * minimum byte count.
     *
     * @param path file path
     * @param minimumBytes exclusive lower bound for file size
     * @param label assertion label
     * @return file content
     */
    static String readLatin1WithMinSize(Path path, long minimumBytes, String label) {
        try {
            long size = Files.size(path);
            if (size <= minimumBytes) {
                throw new AssertionError(label + ": expected more than " + minimumBytes
                        + " bytes, got " + size);
            }
            return Files.readString(path, StandardCharsets.ISO_8859_1);
        } catch (IOException ex) {
            throw new AssertionError("failed to read " + path + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Counts UTF-8 text lines.
     *
     * @param path file path
     * @return line count
     */
    static long countUtf8Lines(Path path) {
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.count();
        } catch (IOException ex) {
            throw new AssertionError("failed to count lines in " + path + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Writes raw JSON object rows as a JSON-array fixture.
     *
     * @param path destination path
     * @param rows raw JSON object rows
     * @return written file path
     */
    static Path writeJsonArrayFixture(Path path, List<String> rows) {
        return writeUtf8(path, "[\n" + String.join(",\n", rows) + "\n]\n");
    }

    /**
     * Writes the shared two-row dataset fixture used by dataset CLI regressions.
     *
     * @param workspace scratch directory
     * @return path to the written fixture
     */
    static Path writeDatasetFixture(Path workspace) {
        String startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        String afterE4Fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
        Path fixture = workspace.resolve("fixture.record");
        String content = "[\n  "
                + datasetRecordObject(1711171067923L, startFen, startFen,
                        "info depth 1 seldepth 1 multipv 1 score cp 25 nodes 50 nps 50000 time 1 pv e2e4")
                + ",\n  "
                + datasetRecordObject(1711171067924L, startFen, afterE4Fen,
                        "info depth 1 seldepth 1 multipv 1 score cp -20 nodes 60 nps 60000 time 1 pv e7e5")
                + "\n]\n";
        return writeUtf8(fixture, content);
    }

    /**
     * Recursively deletes a directory tree, ignoring cleanup failures.
     *
     * @param root directory to remove
     */
    static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best-effort cleanup
                        }
                    });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    /**
     * Recursively deletes a directory tree and fails the test on cleanup errors.
     *
     * @param root directory to remove
     */
    static void deleteRecursivelyStrict(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new AssertionError("failed to delete " + path + ": " + ex.getMessage(), ex);
                        }
                    });
        } catch (IOException ex) {
            throw new AssertionError("failed to clean workspace: " + ex.getMessage(), ex);
        }
    }

    /**
     * Renders a minimal record-shaped JSON object for dataset fixtures.
     *
     * @param created epoch millis
     * @param parent parent FEN
     * @param position position FEN
     * @param uciLine one UCI {@code info} line for the analysis array
     * @return record JSON object text
     */
    private static String datasetRecordObject(long created, String parent, String position, String uciLine) {
        return "{"
                + "\"created\":" + created + ","
                + "\"engine\":\"Stockfish 16\","
                + "\"parent\":\"" + parent + "\","
                + "\"position\":\"" + position + "\","
                + "\"analysis\":[\"" + uciLine + "\"]"
                + "}";
    }
}
