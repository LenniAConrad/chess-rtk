package testing;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

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
     * @param expected expected value
     * @param actual actual value
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
     * @param expected expected value
     * @param actual actual value
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
     * @param expected expected value
     * @param actual actual value
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
     * @param expected expected value
     * @param actual actual value
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
     * @param expected expected value
     * @param actual actual value
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
     * @param expected expected value
     * @param actual actual value
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
     * @param expected expected value
     * @param actual actual value
     * @param tolerance accepted absolute difference
     * @param label assertion label
     */
    static void assertNear(double expected, double actual, double tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
