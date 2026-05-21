package testing;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.swing.JTextArea;

/**
 * Regression checks for GUI terminal text handling.
 */
public final class GuiTerminalRegressionTest {

	/**
	 * Terminal buffer implementation class.
	 */
	private static final String BUFFER_TYPE = "application.gui.window.TerminalOutputBuffer";

	/**
	 * Prevents instantiation.
	 */
	private GuiTerminalRegressionTest() {
		// utility
	}

	/**
	 * Runs GUI terminal regression checks.
	 *
	 * @param args unused command-line arguments
	 */
	public static void main(String[] args) {
		testCarriageReturnRewritesCurrentLine();
		testCarriageReturnWorksAcrossChunks();
		testClearResetsTerminalCursor();
		System.out.println("GuiTerminalRegressionTest: all checks passed");
	}

	/**
	 * Verifies carriage returns update the current terminal line instead of
	 * appending stale progress frames.
	 */
	private static void testCarriageReturnRewritesCurrentLine() {
		JTextArea area = new JTextArea();
		Object buffer = buffer(area);

		append(buffer, "depth 1\rdepth 2\nbestmove e2e4\n");

		assertEquals("depth 2\nbestmove e2e4\n", area.getText(),
				"carriage return rewrites current line");
	}

	/**
	 * Verifies process-reader chunk boundaries do not break carriage-return
	 * cursor state.
	 */
	private static void testCarriageReturnWorksAcrossChunks() {
		JTextArea area = new JTextArea();
		Object buffer = buffer(area);

		append(buffer, "info nodes 10");
		append(buffer, "\rinfo nodes 20");
		append(buffer, "\rinfo nodes 30\n");

		assertEquals("info nodes 30\n", area.getText(),
				"carriage return state survives split chunks");
	}

	/**
	 * Verifies clearing the terminal also clears the tracked write column.
	 */
	private static void testClearResetsTerminalCursor() {
		JTextArea area = new JTextArea();
		Object buffer = buffer(area);

		append(buffer, "old progress");
		invoke(buffer, "clear", new Class<?>[0]);
		append(buffer, "new\rnow\n");

		assertEquals("now\n", area.getText(), "clear resets terminal state");
	}

	/**
	 * Creates a terminal buffer for a text area.
	 *
	 * @param area target area
	 * @return buffer instance
	 */
	private static Object buffer(JTextArea area) {
		try {
			Constructor<?> constructor = Class.forName(BUFFER_TYPE).getDeclaredConstructor(JTextArea.class);
			constructor.setAccessible(true);
			return constructor.newInstance(area);
		} catch (InvocationTargetException ex) {
			rethrowCause(ex);
			throw new AssertionError("unreachable");
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("could not construct terminal buffer", ex);
		}
	}

	/**
	 * Appends text to a terminal buffer.
	 *
	 * @param buffer target buffer
	 * @param text text to append
	 */
	private static void append(Object buffer, String text) {
		invoke(buffer, "append", new Class<?>[] { String.class }, text);
	}

	/**
	 * Invokes a package-private buffer method.
	 *
	 * @param target target object
	 * @param name method name
	 * @param parameterTypes parameter types
	 * @param args arguments
	 * @return return value
	 */
	private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
		try {
			Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
			method.setAccessible(true);
			return method.invoke(target, args);
		} catch (InvocationTargetException ex) {
			rethrowCause(ex);
			throw new AssertionError("unreachable");
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("could not invoke terminal buffer method " + name, ex);
		}
	}

	/**
	 * Preserves the original failure thrown by a reflected method.
	 *
	 * @param ex invocation wrapper
	 */
	private static void rethrowCause(InvocationTargetException ex) {
		Throwable cause = ex.getCause();
		if (cause instanceof RuntimeException runtime) {
			throw runtime;
		}
		if (cause instanceof Error error) {
			throw error;
		}
		throw new AssertionError(cause);
	}

	/**
	 * Verifies equality.
	 *
	 * @param expected expected value
	 * @param actual actual value
	 * @param label assertion label
	 */
	private static void assertEquals(Object expected, Object actual, String label) {
		if (!java.util.Objects.equals(expected, actual)) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}
}
