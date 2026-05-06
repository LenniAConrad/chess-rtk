package testing;

import static testing.TestSupport.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import utility.Json;

/**
 * Regression checks for lightweight JSON helpers.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S2187")
public final class JsonRegressionTest {

	/**
	 * Payload size that is large enough to exceed Json's per-object streaming cap.
	 */
	private static final int STREAM_LIMIT_TRIGGER_CHARS = (1 << 23) + 32;

	/**
	 * Utility class; prevent instantiation.
	 */
	private JsonRegressionTest() {
		// utility
	}

	/**
	 * Runs JSON regression checks.
	 *
	 * @param args unused command-line arguments
	 * @throws Exception if a check fails unexpectedly
	 */
	public static void main(String[] args) throws Exception {
		testStreamTopLevelObjectsKeepsExactObjectText();
		testStreamTopLevelObjectsRejectsOversizedObject();
		testStringArrayParsesEscapedQuotes();
		testStringArrayKeepsLegacyUnescapedDetailQuotes();
		System.out.println("JsonRegressionTest: all checks passed");
	}

	/**
	 * Verifies normal streaming output is unchanged for small objects.
	 *
	 * @throws IOException if streaming fails
	 */
	private static void testStreamTopLevelObjectsKeepsExactObjectText() throws IOException {
		List<String> objects = new ArrayList<>();
		Json.streamTopLevelObjects(new StringReader("[{\"a\":\"{}\"},{\"b\":2}]"), objects::add);

		assertEquals(2, objects.size(), "streamed object count");
		assertEquals("{\"a\":\"{}\"}", objects.get(0), "first streamed object");
		assertEquals("{\"b\":2}", objects.get(1), "second streamed object");
	}

	/**
	 * Verifies the streaming reader enforces its documented per-object limit.
	 */
	private static void testStreamTopLevelObjectsRejectsOversizedObject() {
		try {
			Json.streamTopLevelObjects(new OversizedObjectReader(STREAM_LIMIT_TRIGGER_CHARS), ignored -> {
				throw new AssertionError("oversized object should not be emitted");
			});
		} catch (IOException ex) {
			assertTrue(ex.getMessage().contains("exceeds streaming limit"), "oversized object error message");
			return;
		}
		throw new AssertionError("oversized object was accepted");
	}

	/**
	 * Verifies standard escaped JSON quotes are still decoded normally.
	 */
	private static void testStringArrayParsesEscapedQuotes() {
		String json = "{\"tags\":[\"TACTIC: detail=\\\"hanging queen\\\"\",\"META: puzzle_rating=1200\"]}";
		String[] tags = Json.parseStringArrayField(json, "tags");

		assertEquals(2, tags.length, "escaped quote tag count");
		assertEquals("TACTIC: detail=\"hanging queen\"", tags[0], "escaped quote tag");
		assertEquals("META: puzzle_rating=1200", tags[1], "escaped quote following tag");
	}

	/**
	 * Verifies historical record dumps with unescaped detail quotes do not split one
	 * tactical tag into partial and empty tags.
	 */
	private static void testStringArrayKeepsLegacyUnescapedDetailQuotes() {
		String json = "{\"tags\":[\"TACTIC: motif=hanging side=black detail=\"hanging black queen e2\"\","
				+ "\"META: puzzle_rating=1200\"]}";
		String[] tags = Json.parseStringArrayField(json, "tags");

		assertEquals(2, tags.length, "legacy quote tag count");
		assertEquals("TACTIC: motif=hanging side=black detail=\"hanging black queen e2\"", tags[0],
				"legacy quote tag");
		assertEquals("META: puzzle_rating=1200", tags[1], "legacy quote following tag");
	}

	/**
	 * Streams a huge JSON array/object without materializing the full input string.
	 */
	private static final class OversizedObjectReader extends Reader {

		/**
		 * Prefix before the large string payload.
		 */
		private static final String PREFIX = "[{\"payload\":\"";

		/**
		 * Suffix after the large string payload.
		 */
		private static final String SUFFIX = "\"}]";

		/**
		 * Number of generated payload characters.
		 */
		private final int payloadChars;

		/**
		 * Current absolute stream position.
		 */
		private long index;

		/**
		 * Whether the reader has been closed.
		 */
		private boolean closed;

		/**
		 * Creates a streaming reader with the requested payload size.
		 *
		 * @param payloadChars generated string payload length
		 */
		private OversizedObjectReader(int payloadChars) {
			this.payloadChars = payloadChars;
		}

		/**
		 * Reads generated JSON text into the target buffer.
		 *
		 * @param cbuf target buffer
		 * @param off write offset
		 * @param len max chars to write
		 * @return chars written or {@code -1} at EOF
		 * @throws IOException if the reader was closed
		 */
		@Override
		public int read(char[] cbuf, int off, int len) throws IOException {
			if (closed) {
				throw new IOException("reader is closed");
			}
			if (len == 0) {
				return 0;
			}
			long total = (long) PREFIX.length() + payloadChars + SUFFIX.length();
			if (index >= total) {
				return -1;
			}
			int written = 0;
			while (written < len && index < total) {
				cbuf[off + written] = charAt(index);
				index++;
				written++;
			}
			return written;
		}

		/**
		 * Closes the generated reader.
		 */
		@Override
		public void close() {
			closed = true;
		}

		/**
		 * Returns the generated character at the requested absolute offset.
		 *
		 * @param absoluteIndex absolute generated offset
		 * @return generated character
		 */
		private char charAt(long absoluteIndex) {
			if (absoluteIndex < PREFIX.length()) {
				return PREFIX.charAt((int) absoluteIndex);
			}
			long payloadIndex = absoluteIndex - PREFIX.length();
			if (payloadIndex < payloadChars) {
				return 'a';
			}
			return SUFFIX.charAt((int) (payloadIndex - payloadChars));
		}
	}
}
