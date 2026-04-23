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
            Main.main(args);
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
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
