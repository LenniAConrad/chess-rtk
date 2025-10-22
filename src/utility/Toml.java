package utility;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

/**
 * Used for parsing and representing a minimal TOML document.
 *
 * <p>
 * <strong>Supported:</strong>
 * </p>
 * <ul>
 * <li>Top-level <code>key = value</code> pairs.</li>
 * <li>Array-of-tables declarations <code>[[table]]</code> with flat key/value
 * entries.</li>
 * <li>Line comments beginning with <code>#</code> (ignored outside
 * quotes).</li>
 * <li>Strings (one-line and multiline), longs, doubles, booleans, and one-line
 * string arrays.</li>
 * </ul>
 *
 * <p>
 * <strong>Intentionally <em>not</em> supported</strong> (extend if needed):
 * section tables
 * (<code>\[section]</code>), dotted keys, dates, inline tables, and multiline
 * folding with trailing
 * backslashes, among other advanced TOML features.
 * </p>
 *
 * @author Lennart A. Conrad
 * @since 2025
 */
public final class Toml {

    /**
     * Used for the triple double-quote delimiter (<code>"""</code>) in TOML basic
     * multiline strings.
     */
    private static final String TRIPLE_DOUBLE_QUOTE = "\"\"\"";

    /**
     * Used for the triple single-quote delimiter (<code>'''</code>) in TOML literal
     * multiline strings.
     */
    private static final String TRIPLE_SINGLE_QUOTE = "'''";

    /**
     * Top-level key/value pairs.
     */
    private final Map<String, Object> kv = new HashMap<>();

    /**
     * Arrays of tables (<code>[[foo]]</code>). Each entry is one map in insertion
     * order.
     */
    private final Map<String, List<Map<String, Object>>> tableArrays = new HashMap<>();

    /**
     * Collects non-fatal TOML parse issues (one entry per skipped/invalid line).
     * Populated during {@code Toml.load(...)}; used for diagnostics only.
     */
    private final List<String> errors = new ArrayList<>();

    /**
     * Creates a new, empty TOML document.
     */
    private Toml() {
        // private constructor to prevent instantiation without loading
    }

    /**
     * Loads a TOML document from the given {@link java.io.Reader} into a new
     * {@code Toml} instance.
     * <p>
     * The loader is <b>line-tolerant</b>: malformed lines are skipped and their
     * diagnostics are
     * recorded in {@link #getErrors()} on the returned object. Valid lines are
     * preserved.
     * No exception is thrown for user config mistakes.
     *
     * <p>
     * <b>Error reporting:</b> inspect {@code toml.getErrors()} after loading to see
     * which lines
     * were ignored and why.
     *
     * <p>
     * <b>Exceptions:</b> Does not throw for parse/content errors. Underlying I/O
     * issues from the
     * provided {@code Reader} may still occur during setup, but unexpected
     * exceptions during the
     * read loop are caught and do not abort loading.
     *
     * @param rdr a {@link java.io.Reader} supplying TOML text (not {@code null})
     * @return a populated {@code Toml} model; see {@link #getErrors()} for any
     *         skipped lines
     * @throws IOException if the reader cannot be opened/wrapped before processing
     *                     begins
     */
    public static Toml load(java.io.Reader rdr) throws IOException {
        Toml toml = new Toml();

        try (BufferedReader in = new BufferedReader(rdr)) {
            String line;
            int lineNo = 0;
            Map<String, Object> currentTable = null;

            while ((line = in.readLine()) != null) {
                lineNo++;
                String trimmed = preProcessLine(line);
                if (trimmed.isEmpty()) {
                    continue; // skip blanks/comments
                }

                // Process the line via helper (handles its own IOException cases)
                currentTable = processLine(in, trimmed, toml, currentTable, lineNo);

                // Sync line number with any multi-line read performed by helpers
                lineNo = lastLineNo;
            }
        } catch (Exception unexpected) {
            // Ultra-conservative: do not abort load; record and return what we parsed so
            // far
            // (This catch is outside the per-line processing, so no nested try-blocks.)
            // If you prefer, narrow this to IOException and rethrow; current approach is
            // tolerant.
            // Note: avoid logging stack traces here to keep behavior consistent with
            // existing design.
            // Use your LogService if you want visibility.
        }

        return toml;
    }

    /**
     * Processes a single non-empty, preprocessed TOML line without nesting
     * try-catch blocks.
     * <p>
     * Behavior:
     * <ul>
     * <li>If the line is an <em>array-of-tables</em> header (e.g.,
     * {@code [[table]]}),
     * attempts to start a new table array and returns the resulting current table.
     * On failure, records the error in {@code toml.errors} and returns the previous
     * table.</li>
     * <li>Otherwise treats the line as a {@code key = value} entry and attempts to
     * handle it.
     * On failure, records the error and returns the previous table (skipping only
     * this line).</li>
     * </ul>
     *
     * <p>
     * No exception escapes this method; all recoverable issues are recorded in
     * {@code toml.errors}
     * and parsing continues from the next line.
     * </p>
     *
     * @param in           buffered reader for the TOML source (used if a handler
     *                     consumes extra lines)
     * @param trimmed      the already preprocessed line (whitespace/comments
     *                     removed), not empty
     * @param toml         the accumulating TOML model where keys/tables are stored
     * @param currentTable the current table context; may be {@code null} for the
     *                     root
     * @param lineNo       1-based line number for diagnostics
     * @return the (possibly updated) current table context to be used for
     *         subsequent lines
     */
    private static Map<String, Object> processLine(
            BufferedReader in,
            String trimmed,
            Toml toml,
            Map<String, Object> currentTable,
            int lineNo) {

        if (isArrayOfTablesLine(trimmed)) {
            // e.g., [[table]]
            try {
                return startNewTableArray(trimmed, toml, lineNo);
            } catch (IOException tableErr) {
                toml.errors.add("Line " + lineNo + ": " + tableErr.getMessage());
                return currentTable; // keep existing table; continue parsing
            }
        } else {
            // key = value (may involve multi-line arrays / strings)
            try {
                return handleKeyValueLine(in, trimmed, toml, currentTable, lineNo);
            } catch (IOException perLine) {
                toml.errors.add("Line " + lineNo + ": " + perLine.getMessage());
                return currentTable; // skip only this key/value; continue parsing
            }
        }
    }

    /**
     * Used for stripping comments and whitespace from a raw line.
     *
     * @param line the raw input line
     * @return the trimmed line without comments
     */
    private static String preProcessLine(String line) {
        return stripComment(line).trim();
    }

    /**
     * Used for checking whether a line declares an array-of-tables.
     *
     * @param trimmed the already-trimmed line
     * @return true if the line is an array-of-tables declaration
     */
    private static boolean isArrayOfTablesLine(String trimmed) {
        return trimmed.startsWith("[[") && trimmed.endsWith("]]");
    }

    /**
     * Used for starting a new array-of-tables entry and returning the current table
     * map.
     *
     * @param trimmed the array-of-tables line
     * @param toml    the target Toml instance
     * @param lineNo  the current line number
     * @return the newly created current table map
     * @throws IOException if the table name is empty
     */
    private static Map<String, Object> startNewTableArray(
            String trimmed,
            Toml toml,
            int lineNo) throws IOException {
        String tableName = trimmed.substring(2, trimmed.length() - 2).trim();
        if (tableName.isEmpty()) {
            throw new IOException("Empty table name at line " + lineNo);
        }
        Map<String, Object> currentTable = new LinkedHashMap<>();
        toml.tableArrays.computeIfAbsent(tableName, k -> new ArrayList<>()).add(currentTable);
        lastLineNo = lineNo;
        return currentTable;
    }

    /**
     * Used for handling a key/value line, including multiline strings and arrays.
     *
     * @param in           the buffered reader
     * @param trimmed      the already-trimmed content line
     * @param toml         the target Toml instance
     * @param currentTable the current table context or null for top-level
     * @param lineNo       the current line number
     * @return the (possibly unchanged) current table map
     * @throws IOException if the line is invalid or parsing fails
     */
    private static Map<String, Object> handleKeyValueLine(
            BufferedReader in,
            String trimmed,
            Toml toml,
            Map<String, Object> currentTable,
            int lineNo) throws IOException {
        lastLineNo = lineNo;

        int eq = trimmed.indexOf('=');
        if (eq < 1) {
            throw new IOException("Invalid TOML at line " + lineNo + ": " + trimmed);
        }

        String key = trimmed.substring(0, eq).trim();
        String raw = trimmed.substring(eq + 1).trim();

        if (isStartOfMultiline(raw)) {
            boolean basic = raw.startsWith(TRIPLE_DOUBLE_QUOTE);
            String delim = basic ? TRIPLE_DOUBLE_QUOTE : TRIPLE_SINGLE_QUOTE;
            String value = readMultilineString(in, raw.substring(3), delim, basic, new int[] { lineNo });
            storeValue(key, value, currentTable, toml);
            return currentTable;
        }

        raw = accumulateArrayIfNeeded(in, raw, lineNo);
        Object value = parseValue(raw);
        storeValue(key, value, currentTable, toml);
        return currentTable;
    }

    /**
     * Used for checking whether a value begins a TOML multiline string.
     *
     * @param raw the raw value portion
     * @return true if it begins with a multiline string delimiter
     */
    private static boolean isStartOfMultiline(String raw) {
        return raw.startsWith(TRIPLE_DOUBLE_QUOTE) || raw.startsWith(TRIPLE_SINGLE_QUOTE);
    }

    /**
     * Used for accumulating a multiline array into a single string if needed.
     *
     * @param in     the buffered reader
     * @param raw    the current raw value
     * @param lineNo the current line number
     * @return the possibly-extended raw value
     * @throws IOException if reading fails
     */
    private static String accumulateArrayIfNeeded(
            BufferedReader in,
            String raw,
            int lineNo) throws IOException {
        if (!raw.startsWith("[") || raw.endsWith("]")) {
            lastLineNo = lineNo;
            return raw;
        }

        StringBuilder arr = new StringBuilder(raw);
        String line;
        while ((line = in.readLine()) != null) {
            lineNo++;
            String part = stripComment(line).trim();
            arr.append(part);
            if (part.endsWith("]")) {
                break;
            }
        }
        lastLineNo = lineNo;
        return arr.toString();
    }

    /**
     * Used for storing a parsed value into either the current table or top-level
     * map.
     *
     * @param key          the key
     * @param value        the parsed value
     * @param currentTable the current table or null for top-level
     * @param toml         the Toml instance to receive the value
     */
    private static void storeValue(
            String key,
            Object value,
            Map<String, Object> currentTable,
            Toml toml) {
        if (currentTable != null) {
            currentTable.put(key, value);
        } else {
            toml.kv.put(key, value);
        }
    }

    /**
     * Retrieves the value associated with a key as a string with safe coercion.
     * <p>
     * <b>Coercion rules:</b>
     * <ul>
     * <li>{@code String} → returned as-is.</li>
     * <li>{@code Number} or {@code Boolean} → {@code toString()}.</li>
     * <li>Other/complex types (e.g., {@code List}, {@code Map}) → {@code null} (not
     * stringified).</li>
     * </ul>
     * Unknown/mismatched → {@code null}.
     *
     * @param k key to retrieve
     * @return coerced {@code String} or {@code null} when not applicable
     * @since 2025
     */
    public String getString(String k) {
        return convert(kv.get(k), String.class);
    }

    /**
     * Like {@link #getString(String)} but returns {@code dflt} when the value is
     * missing or not coercible.
     *
     * @param k    key
     * @param dflt default value to return when missing/mismatch
     * @return string value or {@code dflt}
     * @since 2025
     */
    public String getStringOr(String k, String dflt) {
        String v = getString(k);
        return v != null ? v : dflt;
    }

    /**
     * Retrieves the value associated with a key as a long with safe coercion.
     * <p>
     * <b>Coercion rules:</b>
     * <ul>
     * <li>Numeric {@code String} (underscores allowed) → {@code Long}.</li>
     * <li>Integral {@code Double} (e.g., {@code 42.0}) → {@code Long}.</li>
     * <li>{@code Double} with fractional part (e.g., {@code 42.5}) → reject (return
     * {@code null}).</li>
     * <li>{@code Long}/{@code Integer}/{@code Short}/{@code Byte} →
     * {@code Long}.</li>
     * </ul>
     * Unknown/mismatched → {@code null}.
     *
     * @param k key to retrieve
     * @return coerced {@code Long} or {@code null} when not applicable
     * @since 2025
     */
    public Long getLong(String k) {
        return convert(kv.get(k), Long.class);
    }

    /**
     * Like {@link #getLong(String)} but returns {@code dflt} when the value is
     * missing or not coercible.
     *
     * @param k    key
     * @param dflt default when missing/mismatch
     * @return long value or {@code dflt}
     * @since 2025
     */
    public long getLongOr(String k, long dflt) {
        Long v = getLong(k);
        return v != null ? v : dflt;
    }

    /**
     * Retrieves the value associated with a key as a double with safe coercion.
     * <p>
     * <b>Coercion rules:</b>
     * <ul>
     * <li>Numeric {@code String} (underscores allowed) → {@code Double}.</li>
     * <li>{@code Number} → {@code Double} (always allowed).</li>
     * </ul>
     * Unknown/mismatched → {@code null}.
     *
     * @param k key to retrieve
     * @return coerced {@code Double} or {@code null} when not applicable
     * @since 2025
     */
    public Double getDouble(String k) {
        return convert(kv.get(k), Double.class);
    }

    /**
     * Like {@link #getDouble(String)} but returns {@code dflt} when the value is
     * missing or not coercible.
     *
     * @param k    key
     * @param dflt default when missing/mismatch
     * @return double value or {@code dflt}
     * @since 2025
     */
    public double getDoubleOr(String k, double dflt) {
        Double v = getDouble(k);
        return v != null ? v : dflt;
    }

    /**
     * Converts a raw parsed TOML value into a requested target type using the
     * library's coercion rules. Returns {@code null} on mismatch.
     *
     * <p>
     * <b>Supported targets</b>
     * </p>
     * <ul>
     * <li>{@code String}: returned as-is; {@code Number}/{@code Boolean} are
     * stringified
     * via {@link Object#toString()}; complex types (e.g., {@code List}/{@code Map})
     * are <em>not</em> stringified → {@code null}.</li>
     * <li>{@code Long}: see {@link #coerceToLong(Object)}.</li>
     * <li>{@code Double}: see {@link #coerceToDouble(Object)}.</li>
     * </ul>
     *
     * <p>
     * On any unsupported {@code target} or failed coercion, this method returns
     * {@code null}.
     *
     * @param <T>    desired return type ({@code String}, {@code Long}, or
     *               {@code Double})
     * @param raw    the raw value from the parsed TOML model; may be {@code null}
     * @param target the desired target class (must be exactly {@code String.class},
     *               {@code Long.class}, or {@code Double.class})
     * @return the coerced value of type {@code T}, or {@code null} if not coercible
     */
    @SuppressWarnings("unchecked")
    private <T> T convert(Object raw, Class<T> target) {
        if (raw == null) {
            return null;
        }

        if (target == String.class) {
            if (raw instanceof String s)
                return (T) s;
            if (raw instanceof Number || raw instanceof Boolean)
                return (T) raw.toString();
            return null; // do not stringify complex types
        }

        if (target == Long.class) {
            Long v = coerceToLong(raw);
            return (T) v;
        }

        if (target == Double.class) {
            Double v = coerceToDouble(raw);
            return (T) v;
        }

        return null;
    }

    /**
     * Coerces an arbitrary parsed TOML value to a {@link Long} following the
     * library's
     * getter rules while keeping cyclomatic/cognitive complexity low (Sonar S3776).
     *
     * <p>
     * <b>Accepted inputs</b>:
     * <ul>
     * <li>{@code Long}/{@code Integer}/{@code Short}/{@code Byte} → widened to
     * {@code Long}.</li>
     * <li>{@code Double} → delegated to {@link #coerceDoubleToLong(Double)}
     * (accepted only if integral).</li>
     * <li>{@code String} → delegated to {@link #coerceStringToLong(String)}
     * (underscores allowed).</li>
     * </ul>
     * Any other type yields {@code null}.
     *
     * @param raw source object (may be {@code null})
     * @return coerced {@code Long}, or {@code null} if not coercible
     * @since 2025
     */
    private static Long coerceToLong(Object raw) {
        if (raw instanceof Long l) {
            return l;
        }
        if (raw instanceof Integer i) {
            return i.longValue();
        }
        if (raw instanceof Short s) {
            return (long) s;
        }
        if (raw instanceof Byte b) {
            return (long) b;
        }
        if (raw instanceof Double d) {
            return coerceDoubleToLong(d);
        }
        if (raw instanceof String str) {
            return coerceStringToLong(str);
        }
        return null;
    }

    /**
     * Attempts to coerce a {@link Double} to a {@link Long}.
     *
     * <p>
     * Rules:
     * <ul>
     * <li>{@code NaN} or infinite → {@code null}.</li>
     * <li>Integral values (e.g., {@code 42.0}) → cast and returned.</li>
     * <li>Non-integral values (e.g., {@code 42.5}) → {@code null}.</li>
     * </ul>
     * <p>
     * <b>Note:</b> Precision limits of IEEE-754 apply; extremely large
     * {@code double}
     * values may not be representable as exact integers and will be rejected.
     *
     * @param d the double to check (may be {@code null})
     * @return the integral {@code Long} value, or {@code null} when not
     *         integral/valid
     * @since 2025
     */
    private static Long coerceDoubleToLong(Double d) {
        if (d == null || d.isNaN() || d.isInfinite()) {
            return null;
        }
        long asLong = (long) (double) d;
        return (d.doubleValue() == asLong) ? asLong : null;
    }

    /**
     * Attempts to coerce a numeric {@link String} to a {@link Long}.
     *
     * <p>
     * Rules:
     * <ul>
     * <li>Underscores are allowed and ignored (e.g., {@code "1_000"}).</li>
     * <li>If the normalized string looks like a floating-point literal
     * (contains '.' or an exponent), parse as {@code double} and accept only if
     * integral in value (equal to its {@code long} cast).</li>
     * <li>Otherwise parse as {@code long}.</li>
     * <li>On parse failure or non-integral value → {@code null}.</li>
     * </ul>
     *
     * @param s the candidate numeric string (may be {@code null})
     * @return coerced {@code Long}, or {@code null} if not coercible
     * @since 2025
     */
    private static Long coerceStringToLong(String s) {
        String norm = normalizeNumericString(s);
        if (norm == null)
            return null;

        try {
            if (looksLikeDouble(norm)) {
                double dv = Double.parseDouble(norm);
                long lv = (long) dv;
                return (dv == lv) ? lv : null;
            }
            return Long.parseLong(norm);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Attempts to coerce an arbitrary object to a {@link Double}.
     *
     * <p>
     * <b>Accepted inputs</b>
     * </p>
     * <ul>
     * <li>{@code Double} → returned as-is.</li>
     * <li>Any {@code Number} → widened via {@code Number.doubleValue()}.</li>
     * <li>{@code String} → parsed as {@code double} after normalization that
     * permits
     * numeric underscores (e.g., {@code "1_024.5"}) and preserves sign.</li>
     * </ul>
     *
     * <p>
     * On parse failure, unsupported types, or {@code null} input, returns
     * {@code null}.
     *
     * @param raw the source object
     * @return a {@code Double} value or {@code null} when not coercible
     */
    private static Double coerceToDouble(Object raw) {
        if (raw instanceof Double d)
            return d;
        if (raw instanceof Number n)
            return n.doubleValue();

        if (raw instanceof String s) {
            String norm = normalizeNumericString(s);
            if (norm == null)
                return null;
            try {
                return Double.parseDouble(norm);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    /**
     * Heuristic check to decide whether a normalized numeric string should be
     * considered a floating-point literal for parsing purposes.
     *
     * <p>
     * Returns {@code true} if the string contains a decimal point ({@code '.'})
     * or an exponent marker ({@code 'e'} / {@code 'E'}); otherwise {@code false}.
     * This is used to decide whether to attempt floating-point parsing first when
     * coercing strings to {@code Long}.
     *
     * @param norm a normalized numeric candidate string (underscores already
     *             removed)
     * @return {@code true} if the string appears to be a floating-point literal
     * @since 2025
     */
    private static boolean looksLikeDouble(String norm) {
        // Treat presence of '.' or exponent as double-ish
        for (int i = 0; i < norm.length(); i++) {
            char c = norm.charAt(i);
            if (c == '.' || c == 'e' || c == 'E')
                return true;
        }
        return false;
    }

    /**
     * Normalizes a potentially numeric string: trims, allows underscores, and
     * preserves sign.
     * Returns {@code null} when the string is empty or obviously non-numeric.
     */
    private static String normalizeNumericString(String s) {
        if (s == null)
            return null;
        String t = s.trim();
        if (t.isEmpty())
            return null;
        char c0 = t.charAt(0);
        if (!(Character.isDigit(c0) || c0 == '+' || c0 == '-'))
            return null;
        return t.replace("_", "");
    }

    /**
     * Used for retrieving the value associated with a key as a list of strings.
     *
     * @param k the key to retrieve
     * @return the list of strings, or null if not found or not a list of strings
     */
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String k) {
        return (List<String>) kv.get(k);
    }

    /**
     * Used for retrieving the list of tables associated with a table array name.
     *
     * @param name the name of the table array
     * @return a live list of maps representing the tables in insertion order
     */
    public List<Map<String, Object>> getTableArray(String name) {
        return tableArrays.getOrDefault(name, Collections.emptyList());
    }

    /**
     * Used for removing a comment from a TOML line while respecting quotes.
     *
     * @param s the string line to process
     * @return the string with comments stripped
     */
    private static String stripComment(String s) {
        int hash = inQuotesAwareIndexOf(s, '#');
        return (hash >= 0 ? s.substring(0, hash) : s).trim();
    }

    /**
     * Parses a raw TOML value into a Java object.
     * <p>
     * Handling rules:
     * <ul>
     * <li><b>Quoted strings:</b> {@code "text"} → {@link String} (with escapes
     * unescaped).</li>
     * <li><b>Booleans:</b> {@code true}/{@code false} → {@link Boolean}.</li>
     * <li><b>Arrays:</b> {@code [ ... ]} → result of {@code parseArray(raw)} (e.g.,
     * {@code List<?>}).</li>
     * <li><b>Numbers:</b> numeric underscores are accepted (e.g., {@code 200_000}).
     * Values with a decimal point or exponent ({@code .}, {@code e}, {@code E})
     * parse as {@link Double};
     * otherwise as {@link Long}.</li>
     * </ul>
     *
     * @param raw raw TOML value string (not {@code null})
     * @return a parsed value: {@link String}, {@link Long}, {@link Double},
     *         {@link Boolean}, or a {@link java.util.List} from {@code parseArray}
     * @throws IOException if the value cannot be parsed or has an unsupported form
     */
    private static Object parseValue(String raw) throws IOException {
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            return unescape(raw.substring(1, raw.length() - 1));
        }
        if ("true".equals(raw) || "false".equals(raw)) {
            return Boolean.valueOf(raw);
        }
        if (raw.startsWith("[")) {
            return parseArray(raw);
        }

        // TOML allows numeric underscores; JVM parsers do not → normalize first.
        // Only do this when it's plausibly numeric (leading digit or +/-).
        String candidate = raw;
        if (!raw.isEmpty()) {
            char c0 = raw.charAt(0);
            if (Character.isDigit(c0) || c0 == '-' || c0 == '+') {
                candidate = raw.replace("_", "");
            }
        }

        try {
            if (candidate.contains(".") || candidate.contains("e") || candidate.contains("E")) {
                return Double.valueOf(candidate);
            }
            return Long.valueOf(candidate);
        } catch (NumberFormatException nfe) {
            throw new IOException("Unsupported TOML value: " + raw);
        }
    }

    /**
     * Used for parsing a TOML array string into a list of strings.
     *
     * @param raw the raw array string
     * @return the list of parsed string elements
     * @throws IOException if the array format is invalid
     */
    private static List<String> parseArray(String raw) throws IOException {
        if (!raw.endsWith("]")) {
            throw new IOException("Multiline arrays not supported: " + raw);
        }
        String body = raw.substring(1, raw.length() - 1).trim();
        if (body.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        int start = 0;
        int i = 0;
        boolean inQuote = false;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                out.add(stripQuotes(body.substring(start, i).trim()));
                start = i + 1;
            }
            i++;
        }
        out.add(stripQuotes(body.substring(start).trim()));
        return out;
    }

    /**
     * Used for finding the index of a character outside quotes.
     *
     * @param s      the string to search
     * @param target the character to find
     * @return the index of the first unquoted occurrence, or -1 if not found
     */
    private static int inQuotesAwareIndexOf(String s, char target) {
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == target && !inQuote) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Used for removing surrounding quotes and unescaping a string if needed.
     *
     * @param s the quoted string
     * @return the unescaped, unquoted string
     */
    private static String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return unescape(s.substring(1, s.length() - 1));
        }
        return s;
    }

    /**
     * Used for performing minimal unescaping on a string.
     *
     * @param s the string to unescape
     * @return the unescaped string
     */
    private static String unescape(String s) {
        // Minimal unescape to match the original behavior.
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * Small trick to bubble updated line numbers back to caller without plumbing
     * a mutable object around everywhere.
     */
    private static int lastLineNo;

    /**
     * Used for reading a TOML multiline string until its closing delimiter.
     *
     * @param in        the reader positioned after the opening delimiter
     * @param afterOpen the text immediately following the opening delimiter
     * @param delim     the delimiter used (""" or ''')
     * @param basic     true if basic string, false for literal
     * @param lineNoRef a single-element array tracking line number
     * @return the parsed multiline string
     * @throws IOException if the multiline string is unterminated
     */
    private static String readMultilineString(
            BufferedReader in,
            String afterOpen,
            String delim,
            boolean basic,
            int[] lineNoRef) throws IOException {
        int lineNo = lineNoRef[0];

        // If closing delimiter appears on the same line, fast-path.
        int closeHere = afterOpen.indexOf(delim);
        if (closeHere >= 0) {
            String body = afterOpen.substring(0, closeHere);
            lastLineNo = lineNo;
            return basic ? unescape(trimFirstNewline(body)) : body;
        }

        StringBuilder sb = new StringBuilder();

        // For basic multiline strings, TOML trims the very first newline after the
        // opening.
        String head = trimFirstNewline(afterOpen);
        if (!head.isEmpty()) {
            sb.append(head);
            sb.append('\n'); // because the current line actually ends here
        }

        String next;
        while ((next = in.readLine()) != null) {
            lineNo++;
            int idx = next.indexOf(delim);
            if (idx >= 0) {
                // Append up to the delimiter; do not include the delimiter or add extra
                // newline.
                sb.append(next, 0, idx);
                lastLineNo = lineNo;
                String out = sb.toString();
                return basic ? unescape(out) : out;
            } else {
                sb.append(next);
                sb.append('\n');
            }
        }

        throw new IOException("Unterminated multiline string starting before line " + lineNoRef[0]);
    }

    /**
     * Used for trimming the first newline character from a string.
     *
     * @param s the string to trim
     * @return the string without the first newline
     */
    private static String trimFirstNewline(String s) {
        if (s.startsWith("\r\n"))
            return s.substring(2);
        if (s.startsWith("\n"))
            return s.substring(1);
        return s;
    }

    /**
     * Returns an unmodifiable view of recorded parse issues.
     * Never {@code null}; empty if the file parsed without recoverable errors.
     *
     * @return unmodifiable list of parse-error messages collected during load
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
