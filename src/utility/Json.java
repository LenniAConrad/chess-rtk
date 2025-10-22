package utility;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Utility class for basic JSON operations including parsing and formatting.
 * 
 * This class provides methods to:
 * - Convert arrays to JSON string representations.
 * - Parse JSON strings into arrays or individual fields.
 * - Split JSON arrays into individual objects.
 * 
 * It supports a limited subset of JSON syntax and is not a full parser.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public class Json {

    // Private constructor to prevent instantiation
    private Json() {
        // Prevents instantiation of utility class
    }

    /**
     * Converts an array of strings into a JSON array string.
     *
     * Each string is escaped appropriately and enclosed in double quotes.
     * Null input returns {@code null}.
     *
     * @param value array of strings to convert
     * @return JSON-formatted string array, or {@code null} if input is null
     */
    public static String stringArray(String[] value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder().append('[');
        for (int i = 0; i < value.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(esc(value[i])).append('"');
        }
        return sb.append(']').toString();
    }

    /**
     * Used for splitting a JSON array string into a list of top-level JSON object
     * substrings.
     * Assumes the string starts with '[' and ends with ']'.
     *
     * @param json JSON array string
     * @return list of top-level object substrings; never {@code null}
     */
    public static List<String> splitTopLevelObjects(String json) {
        if (json == null) {
            return java.util.Collections.emptyList();
        }
        String s = json.trim();
        int n = s.length();
        if (n < 2 || s.charAt(0) != '[' || s.charAt(n - 1) != ']') {
            return java.util.Collections.emptyList();
        }
        // Delegate scanning logic to keep complexity low
        return extractTopLevelObjects(s, 1, n - 1);
    }

    /**
     * Streams each top-level JSON object from a gigantic JSON array file without
     * loading
     * the whole file into memory. Only one object is held at a time.
     *
     * <p>
     * Expected input shape (whitespace allowed):
     * </p>
     * 
     * <pre>
     *   [ { ... }, { ... }, ... ]
     * </pre>
     *
     * <p>
     * This is not a full JSON validator. It focuses on robustly finding balanced
     * top-level objects while safely skipping over strings and escapes.
     * </p>
     *
     * @param path     file path to a JSON array (e.g., 100 GB)
     * @param consumer callback receiving each complete top-level object as a String
     * @throws IOException if an I/O error occurs or the input is malformed in ways
     *                     we detect
     */
    public static void streamTopLevelObjects(Path path, Consumer<String> consumer) throws IOException {
        try (InputStream in = Files.newInputStream(path);
                Reader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 20)) {
            streamTopLevelObjects(r, consumer);
        } catch (IOException e) {
            throw new IOException("Failed streaming top-level JSON objects from: " + path, e);
        }
    }

    /**
     * Streams each top-level JSON object from a {@link Reader} whose content is a
     * single JSON array, emitting every object as its exact substring
     * (from the opening '{' to the matching '}' inclusive) to the given
     * {@code consumer}.
     *
     * <p>
     * <strong>Expected input shape:</strong>
     * 
     * <pre>{@code
     * [ { ... }, { ... }, ... ]
     * }</pre>
     *
     * <h3>Behavior</h3>
     * <ul>
     * <li>Skips an optional UTF-8 BOM and any leading whitespace before the first
     * '['.</li>
     * <li>Requires the first non-whitespace character to be '['; otherwise throws
     * {@link IOException}.</li>
     * <li>Between objects, commas and whitespace are ignored; other non-structural
     * characters are leniently ignored.</li>
     * <li>Inside objects, tracks string/escape state so braces within string
     * literals do not affect depth.</li>
     * <li>Nested arrays inside objects are allowed; only brace depth for {@code {}}
     * is tracked.</li>
     * <li>Upon reading the closing ']' at top level, returns normally (any trailing
     * whitespace is ignored).</li>
     * <li>If EOF occurs while an object is still open, throws
     * {@link IOException}.</li>
     * </ul>
     *
     * <h3>Performance characteristics</h3>
     * <ul>
     * <li>Reads in 64&nbsp;KiB chunks; the per-object buffer starts at ~1&nbsp;MiB
     * and grows as needed.</li>
     * <li>Does not parse or materialize objects beyond their textual slice;
     * downstream parsing is caller’s responsibility.</li>
     * </ul>
     *
     * <h3>Thread-safety</h3>
     * <p>
     * This method maintains no shared state and is thread-safe with respect to
     * other calls,
     * but it does not synchronize on the provided {@link Reader} or
     * {@code consumer}.
     * </p>
     *
     * <h3>Notes</h3>
     * <ul>
     * <li>Exceptions thrown by {@code consumer.accept(String)} propagate to the
     * caller.</li>
     * <li>This is a tolerant splitter, not a full JSON validator.</li>
     * </ul>
     *
     * @param r        non-null character stream containing a single JSON array of
     *                 objects.
     * @param consumer non-null sink that receives each object’s exact JSON text.
     * @throws IOException if the input does not begin with an array, if a top-level
     *                     object is
     *                     unterminated at EOF, or if the underlying {@link Reader}
     *                     fails.
     * @see #streamTopLevelObjects(java.nio.file.Path, java.util.function.Consumer)
     */
    public static void streamTopLevelObjects(Reader r, Consumer<String> consumer) throws IOException {
        final char[] buf = new char[1 << 16]; // 64 KiB chunks
        final StreamScan st = new StreamScan(1 << 20); // ~1 MiB initial builder

        int read;
        while ((read = r.read(buf)) != -1) {
            for (int i = 0; i < read; i++) {
                final char c = buf[i];

                if (!st.inArray) {
                    handlePreamble(st, c);
                } else if (st.depth == 0) {
                    if (handleTopLevel(st, c)) {
                        return;
                    }
                } else {
                    handleInsideObject(st, c, consumer);
                }
            }
        }

        if (st.depth != 0) {
            throw new IOException("Unterminated JSON object at EOF");
        }
    }

    /**
     * Holds streaming scanner state to avoid many local vars in the public method.
     */
    private static final class StreamScan {
        boolean inArray;
        boolean inStr;
        boolean esc;
        int depth; // 0 = not inside an object; >0 = brace depth
        final StringBuilder obj;

        StreamScan(int initialCapacity) {
            this.obj = new StringBuilder(initialCapacity);
        }
    }

    /**
     * Handles the preamble before the opening '['.
     * 
     * @return true if the character was fully handled (caller should continue).
     */
    private static boolean handlePreamble(StreamScan st, char c) throws IOException {
        if (c == '\uFEFF' || Character.isWhitespace(c)) {
            return true; // skip BOM/whitespace
        }
        if (c == '[') {
            st.inArray = true;
            return true;
        }
        throw new IOException("Expected '[' at start of JSON array");
    }

    /**
     * Handles characters at top array level (outside any object).
     * 
     * @return true if we reached the end of the array (']').
     */
    private static boolean handleTopLevel(StreamScan st, char c) {
        switch (c) {
            case '{':
                startObject(st);
                return false;
            case ']':
                return true; // end of array
            case ',':
                return false; // separator between objects
            default:
                // ignore whitespace (and leniently ignore any other junk)
                return false;
        }
    }

    /** Initializes state for a new object and appends the opening '{'. */
    private static void startObject(StreamScan st) {
        st.depth = 1;
        st.inStr = false;
        st.esc = false;
        st.obj.setLength(0);
        st.obj.append('{');
    }

    /**
     * Appends the char and dispatches to string/non-string handling while inside an
     * object.
     */
    private static void handleInsideObject(StreamScan st, char c, Consumer<String> consumer) {
        st.obj.append(c);
        if (st.inStr) {
            handleStringChar(st, c);
        } else {
            handleNonStringChar(st, c, consumer);
        }
    }

    /** Updates state while inside a JSON string literal. */
    private static void handleStringChar(StreamScan st, char c) {
        if (st.esc) {
            st.esc = false; // current char is escaped; do not interpret it
        } else if (c == '\\') {
            st.esc = true; // next char will be escaped
        } else if (c == '"') {
            st.inStr = false; // string closed
        }
    }

    /**
     * Updates state while not in a string: quotes/braces end/start + object
     * completion.
     */
    private static void handleNonStringChar(StreamScan st, char c, Consumer<String> consumer) {
        if (c == '"') {
            st.inStr = true;
            st.esc = false;
            return;
        }
        if (c == '{') {
            st.depth++;
            return;
        }
        if (c == '}') {
            st.depth--;
            if (st.depth == 0) {
                // Completed a top-level object
                consumer.accept(st.obj.toString());
                // obj will be reset when next object starts to avoid new allocation
            }
        }
    }

    /**
     * Used for scanning the given range (inside the surrounding '[' and ']') and
     * collecting substrings
     * for each top-level JSON object {@code { ... }} by iterating explicitly with
     * index management.
     *
     * @param s           full JSON array string
     * @param from        start index (inclusive) right after '['
     * @param toExclusive end index (exclusive) just before ']'
     * @return list of top-level JSON object substrings
     */
    private static List<String> extractTopLevelObjects(String s, int from, int toExclusive) {
        List<String> parts = new ArrayList<>();
        if (s == null) {
            return parts;
        }
        int lo = Math.max(0, from);
        int hi = Math.min(s.length(), toExclusive);

        int depth = 0;
        int start = -1;

        int i = lo;
        while (i < hi) {
            char c = s.charAt(i);

            if (c == '"') {
                // jump past the full JSON string literal (handles escapes)
                i = skipJsonString(s, i, hi) + 1;
                continue;
            }

            if (c == '{') {
                if (depth == 0) {
                    start = i; // mark start of a top-level object
                }
                depth++;
            } else if (c == '}' && depth > 0 && --depth == 0 && start >= 0) {
                parts.add(s.substring(start, i + 1));
                start = -1;
            }

            i++;
        }
        return parts;
    }

    /**
     * Used for advancing past a JSON string literal that starts at position
     * {@code i} (where {@code s.charAt(i) == '"'}).
     * Handles backslash escapes and returns the index of the closing quote, or
     * {@code toExclusive - 1} if unterminated.
     *
     * @param s           source text
     * @param i           index of the opening quote
     * @param toExclusive upper bound (exclusive) for scanning
     * @return index of the closing quote, or {@code toExclusive - 1} if
     *         unterminated
     */
    private static int skipJsonString(String s, int i, int toExclusive) {
        boolean esc = false;
        int j = i + 1;
        while (j < toExclusive) {
            char ch = s.charAt(j);
            if (esc) {
                esc = false; // current char is escaped
            } else if (ch == '\\') {
                esc = true; // next char is escaped
            } else if (ch == '"') {
                return j; // found closing quote
            }
            j++;
        }
        // Unterminated string; clamp to end-of-range so caller loop can finish safely.
        return toExclusive - 1;
    }

    /**
     * Used for escaping a string for safe inclusion in JSON string literals.
     *
     * @param s input string; null is treated as empty
     * @return escaped string
     */
    public static String esc(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\");
                    break;
                case '"':
                    sb.append("\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Used for extracting a quoted string field by name.
     *
     * @param json JSON source
     * @param name field name
     * @return field value, or {@code null} when missing or malformed
     */
    public static String parseStringField(String json, String name) {
        String key = '"' + name + '"' + ':';
        int idx = json.indexOf(key);
        if (idx < 0) {
            return null;
        }
        int q1 = json.indexOf('"', idx + key.length());
        if (q1 < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int i = q1 + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                switch (c) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    default:
                        sb.append(c);
                }
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    /**
     * Used for extracting a numeric long field by name.
     *
     * @param json JSON source
     * @param name field name
     * @return parsed long value
     * @throws IllegalArgumentException if the field is missing or unparsable
     */
    public static long parseLongField(String json, String name) {
        String key = '"' + name + '"' + ':';
        int idx = json.indexOf(key);

        if (idx < 0) {
            throw new IllegalArgumentException("Field '" + name + "' not found in JSON: " + json);
        }

        int i = idx + key.length();

        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }

        int j = i;

        while (j < json.length() && (json.charAt(j) == '-' || Character.isDigit(json.charAt(j)))) {
            j++;
        }

        if (i == j) {
            throw new IllegalArgumentException("No numeric value found for field '" + name + "'");
        }

        try {
            return Long.parseLong(json.substring(i, j));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unparsable long for field '" + name + "': " + json.substring(i, j), e);
        }
    }

    /**
     * Used for extracting an array of strings from a JSON field.
     *
     * @param json JSON source
     * @param name field name
     * @return parsed string array; never {@code null}
     */
    public static String[] parseStringArrayField(String json, String name) {
        if (json == null || name == null) {
            return new String[0];
        }

        String key = '"' + name + '"' + ':';
        int idx = json.indexOf(key);

        if (idx < 0) {
            return new String[0];
        }

        int lb = json.indexOf('[', idx + key.length());
        if (lb < 0) {
            return new String[0];
        }

        return parseStringArrayFrom(json, lb + 1);
    }

    /**
     * Used for defining the supported escape characters in JSON strings.
     * The characters in this string correspond by index to the mapped values in
     * {@code MAP}.
     */
    private static final String ESC = "\"\\nrt";

    /**
     * Used for mapping escape characters to their actual character representations.
     * Index positions align with those in {@code ESC}.
     */
    private static final char[] MAP = { '"', '\\', '\n', '\r', '\t' };

    /**
     * Used for holding the current parsing state within a JSON string.
     *
     * @param inStr true if currently parsing inside a quoted string
     * @param esc   true if the previous character was an escape character
     *              (backslash)
     */
    private static final class StrState {
        /**
         * Used for indicating whether the parser is currently inside a quoted JSON
         * string.
         * This affects how characters like commas or brackets are interpreted.
         */
        boolean inStr;

        /**
         * Used for indicating whether the last character was a backslash,
         * signaling that the current character should be treated as an escape sequence.
         */
        boolean esc;
    }

    /**
     * Used for parsing a JSON array of strings starting at index {@code start}
     * (immediately after '[')
     * and returning the array of strings once the closing ']' is found (outside of
     * strings).
     *
     * Supports the escapes: \" \\ \n \r \t
     *
     * @param json  the full JSON input string
     * @param start the index just after the opening '[' of the string array
     * @return an array of strings parsed from the JSON array
     */
    private static String[] parseStringArrayFrom(String json, int start) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        StrState st = new StrState();
        boolean done = false;

        for (int i = start, n = json.length(); i < n && !done; i++) {
            char c = json.charAt(i);
            if (st.inStr) {
                handleInString(c, st, cur, out);
            } else {
                done = handleTopLevel(c, st, cur);
            }
        }
        return out.toArray(new String[0]);
    }

    /**
     * Used for handling characters within a quoted JSON string.
     *
     * @param c   the current character being parsed
     * @param st  the current string parsing state
     * @param cur the current string builder accumulating the string content
     * @param out the output list of strings
     */
    private static void handleInString(char c, StrState st, StringBuilder cur, List<String> out) {
        if (st.esc) {
            int k = ESC.indexOf(c);
            cur.append(k >= 0 ? MAP[k] : c);
            st.esc = false;
        } else if (c == '\\') {
            st.esc = true;
        } else if (c == '"') {
            out.add(cur.toString());
            st.inStr = false;
        } else {
            cur.append(c);
        }
    }

    /**
     * Used for handling characters outside of quoted JSON strings, at the top array
     * level.
     *
     * @param c   the current character being parsed
     * @param st  the current string parsing state
     * @param cur the current string builder (reset when entering a new string)
     * @return true if the closing ']' of the array is reached (outside strings)
     */
    private static boolean handleTopLevel(char c, StrState st, StringBuilder cur) {
        if (c == '"') {
            st.inStr = true;
            cur.setLength(0);
            return false;
        }
        return c == ']';
    }
}
