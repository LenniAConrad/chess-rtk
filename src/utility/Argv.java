package utility;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Used for parsing command-line arguments in a minimal-but-robust way.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Flags: <code>--flag</code> / <code>--no-flag</code> /
 * <code>-f</code></li>
 * <li>Options: <code>--key value</code> | <code>--key=value</code> |
 * <code>-k value</code> | <code>-kVALUE</code></li>
 * <li>End-of-opts marker: <code>--</code> (everything after is positional)</li>
 * <li>Typed accessors and required/default helpers</li>
 * <li>Remaining positionals retrieval</li>
 * </ul>
 *
 * <p>
 * Usage pattern:
 * 
 * <pre>{@code
 * Argv a = new Argv(args);
 * boolean chess960 = a.flag("--chess960", "-9");
 * String out = a.string("--output", "-o");
 * long nodes = a.lng("--max-nodes");
 * Duration dur = a.duration("--max-duration"); // e.g., "60s", "2m", "60000"
 * java.nio.file.Path in = a.pathRequired("--input", "-i");
 * List<String> rest = a.positionals(); // if you expect positionals
 * a.ensureConsumed(); // errors on leftover options/tokens if any
 * }</pre>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Argv {

    /**
     * Used for storing normalized argv tokens.
     */
    private final List<String> tokens = new ArrayList<>();

    /**
     * Used for tracking the current read pointer over normalized tokens.
     */
    private int i = 0;

    /**
     * Used for remembering the first index of <code>--</code> (positionals start).
     */
    private int posStart = Integer.MAX_VALUE;

    /**
     * Used for constructing an {@code Argv} from the provided raw {@code argv}.
     * Normalizes tokens by splitting {@code --key=value} and {@code -kVALUE},
     * while preserving order and remembering where {@code --} appears.
     *
     * @param argv the raw {@code main(String[] argv)} array; can be {@code null}
     */
    public Argv(String[] argv) {
        if (argv == null) {
            argv = new String[0];
        }

        // Normalize tokens:
        // - Split --key=value into ["--key","value"]
        // - Split -kVALUE into ["-k","VALUE"] (single-letter short only)
        // - Preserve order; remember where '--' appears
        for (String raw : argv) {
            if (raw == null) {
                continue; // single allowed continue (Sonar S135)
            }

            if ("--".equals(raw)) {
                posStart = tokens.size();
                tokens.add("--");
            } else if (raw.startsWith("--")) {
                int eq = raw.indexOf('=');
                if (eq >= 0) {
                    tokens.add(raw.substring(0, eq));
                    tokens.add(raw.substring(eq + 1));
                } else {
                    tokens.add(raw);
                }
            } else if (raw.startsWith("-") && raw.length() > 2) {
                // single-letter short option glued to value, e.g. -oOUT
                tokens.add(raw.substring(0, 2));
                tokens.add(raw.substring(2));
            } else {
                tokens.add(raw);
            }
        }
    }

    // ----------------------- public API (flags/options) -----------------------

    /**
     * Used for reading a boolean flag option.
     * Supports {@code --flag}, {@code -f}, and {@code --no-flag}. The key is
     * consumed if present.
     *
     * @param keys the accepted key aliases (e.g., {@code "--verbose"},
     *             {@code "-v"})
     * @return {@code true} if a positive form was present; {@code false} if a
     *         negated form or absent
     */
    public boolean flag(String... keys) {
        int idx = matchKey(keys);
        if (idx < 0) {
            return false;
        }
        String tok = tokens.get(idx);
        consumeAt(idx);
        return !tok.startsWith("--no-");
    }

    /**
     * Used for retrieving the string value for the first matching option.
     *
     * @param keys the accepted key aliases
     * @return the option value, or {@code null} if the key is missing
     */
    public String string(String... keys) {
        return opt(keys);
    }

    /**
     * Used for retrieving the required string value for the first matching option.
     * Throws if missing or empty.
     *
     * @param keys the accepted key aliases
     * @return the non-empty option value
     * @throws IllegalArgumentException if missing or empty
     */
    public String stringRequired(String... keys) {
        String v = opt(keys);
        if (v == null || v.isEmpty()) {
            error("Missing value for %s", join(keys));
        }
        return v;
    }

    /**
     * Used for retrieving all string values for repeated options (in order).
     *
     * @param keys the accepted key aliases
     * @return a list of all values found; never {@code null}
     */
    public List<String> strings(String... keys) {
        List<String> out = new ArrayList<>();
        for (;;) {
            int idx = matchKey(keys);
            if (idx < 0) {
                break;
            }
            consumeAt(idx);
            out.add(expectValueAfter(idx, join(keys)));
        }
        return out;
    }

    /**
     * Used for retrieving an {@link Integer} value for an option.
     *
     * @param keys the accepted key aliases
     * @return the parsed integer, or {@code null} if the key is missing
     * @throws IllegalArgumentException if the value is not a valid integer
     */
    public Integer integer(String... keys) {
        return parseInt(opt(keys), null, join(keys));
    }

    /**
     * Used for retrieving a required {@code int} value for an option.
     *
     * @param keys the accepted key aliases
     * @return the parsed integer
     * @throws IllegalArgumentException if the key is missing or invalid
     */
    public int integerRequired(String... keys) {
        return parseInt(stringRequired(keys), null, join(keys));
    }

    /**
     * Used for retrieving an {@code int} value with a default.
     *
     * @param def  the default value to return if the key is missing
     * @param keys the accepted key aliases
     * @return the parsed integer, or {@code def} if missing
     * @throws IllegalArgumentException if present but invalid
     */
    public int integerOr(int def, String... keys) {
        return parseInt(opt(keys), def, join(keys));
    }

    /**
     * Used for retrieving a {@link Long} value for an option.
     *
     * @param keys the accepted key aliases
     * @return the parsed long, or {@code null} if the key is missing
     * @throws IllegalArgumentException if the value is not a valid long
     */
    public Long lng(String... keys) {
        return parseLong(opt(keys), null, join(keys));
    }

    /**
     * Used for retrieving a required {@code long} value for an option.
     *
     * @param keys the accepted key aliases
     * @return the parsed long
     * @throws IllegalArgumentException if the key is missing or invalid
     */
    public long lngRequired(String... keys) {
        return parseLong(stringRequired(keys), null, join(keys));
    }

    /**
     * Used for retrieving a {@code long} value with a default.
     *
     * @param def  the default value to return if the key is missing
     * @param keys the accepted key aliases
     * @return the parsed long, or {@code def} if missing
     * @throws IllegalArgumentException if present but invalid
     */
    public long lngOr(long def, String... keys) {
        return parseLong(opt(keys), def, join(keys));
    }

    /**
     * Used for retrieving a {@link Double} value for an option.
     *
     * @param keys the accepted key aliases
     * @return the parsed double, or {@code null} if the key is missing
     * @throws IllegalArgumentException if the value is not a valid double
     */
    public Double dbl(String... keys) {
        return parseDouble(opt(keys), null, join(keys));
    }

    /**
     * Used for retrieving a required {@code double} value for an option.
     *
     * @param keys the accepted key aliases
     * @return the parsed double
     * @throws IllegalArgumentException if the key is missing or invalid
     */
    public double dblRequired(String... keys) {
        return parseDouble(stringRequired(keys), null, join(keys));
    }

    /**
     * Used for retrieving a {@code double} value with a default.
     *
     * @param def  the default value to return if the key is missing
     * @param keys the accepted key aliases
     * @return the parsed double, or {@code def} if missing
     * @throws IllegalArgumentException if present but invalid
     */
    public double dblOr(double def, String... keys) {
        return parseDouble(opt(keys), def, join(keys));
    }

    /**
     * Used for retrieving a {@link java.nio.file.Path} for an option, if present.
     * Expands a leading {@code ~} to {@code user.home}.
     *
     * @param keys the accepted key aliases
     * @return the parsed path, or {@code null} if missing
     */
    public java.nio.file.Path path(String... keys) {
        String v = opt(keys);
        return v == null ? null : toPath(v);
    }

    /**
     * Used for retrieving a required {@link java.nio.file.Path} for an option.
     * Expands a leading {@code ~} to {@code user.home}.
     *
     * @param keys the accepted key aliases
     * @return the parsed path
     * @throws IllegalArgumentException if the key is missing
     */
    public java.nio.file.Path pathRequired(String... keys) {
        String v = stringRequired(keys);
        return toPath(v);
    }

    /**
     * Used for parsing a duration value from an option.
     * Accepts {@code 500ms}, {@code 60s}, {@code 2m}, {@code 1h}, or a plain number
     * (milliseconds).
     *
     * @param keys the accepted key aliases
     * @return the parsed duration, or {@code null} if missing
     * @throws IllegalArgumentException if the value is invalid
     */
    public Duration duration(String... keys) {
        String v = opt(keys);
        return v == null ? null : parseDuration(v, join(keys));
    }

    /**
     * Used for parsing a required duration value from an option.
     * Accepts {@code 500ms}, {@code 60s}, {@code 2m}, {@code 1h}, or a plain number
     * (milliseconds).
     *
     * @param keys the accepted key aliases
     * @return the parsed duration
     * @throws IllegalArgumentException if missing or invalid
     */
    public Duration durationRequired(String... keys) {
        String v = stringRequired(keys);
        return parseDuration(v, join(keys));
    }

    /**
     * Used for consuming and returning remaining positional arguments (after
     * {@code --} or leftovers).
     *
     * @return a list of positional arguments in order; never {@code null}
     */
    public List<String> positionals() {
        List<String> out = new ArrayList<>();
        int start = (posStart == Integer.MAX_VALUE) ? i : Math.min(posStart + 1, tokens.size());
        for (int k = Math.max(i, start); k < tokens.size(); k++) {
            String t = tokens.get(k);
            if (t == null || "--".equals(t)) {
                continue;
            }
            out.add(t);
            tokens.set(k, null);
        }
        i = tokens.size();
        return out;
    }

    /**
     * Used for throwing if any unconsumed, non-positional tokens remain.
     * Call {@link #positionals()} first if you want to permit implicit positionals.
     *
     * @throws IllegalArgumentException if unexpected option or positional is
     *                                  present
     */
    public void ensureConsumed() {
        for (int k = i; k < tokens.size(); k++) {
            String t = tokens.get(k);
            if (t == null || "--".equals(t)) {
                continue;
            }
            if (t.startsWith("-")) {
                error("Unexpected argument: '%s'", t);
            }
            error("Unexpected positional argument: '%s' (call positionals() to consume)", t);
        }
    }

    // ------------------------------ internals ------------------------------

    /**
     * Used for retrieving and consuming an option value for the first matching key,
     * if present.
     *
     * @param keys the accepted key aliases
     * @return the value, or {@code null} if the key is missing
     */
    private String opt(String... keys) {
        int idx = matchKey(keys);
        if (idx < 0) {
            return null;
        }
        consumeAt(idx);
        return expectValueAfter(idx, join(keys));
    }

    /**
     * Used for ensuring a value token exists immediately after the given key index.
     * Consumes the value token.
     *
     * @param keyIndex the index of the matched key in {@link #tokens}
     * @param keyLabel a label for error messages (usually a joined alias list)
     * @return the value token
     * @throws IllegalArgumentException if a value is missing
     */
    private String expectValueAfter(int keyIndex, String keyLabel) {
        int valIdx = nextIndex(keyIndex);
        if (valIdx >= tokens.size()) {
            error("Option %s expects a value", keyLabel);
        }
        String v = tokens.get(valIdx);
        if (v == null || "--".equals(v)) {
            error("Option %s expects a value", keyLabel);
        }
        tokens.set(valIdx, null);
        if (i <= valIdx) {
            i = valIdx + 1;
        }
        return v;
    }

    /**
     * Used for finding the index of the first occurrence of any key alias (or its
     * {@code --no-} form).
     * Scans from the current pointer up to the positionals marker.
     *
     * @param keys the accepted key aliases
     * @return the matching index, or {@code -1} if not found
     */
    private int matchKey(String... keys) {
        int end = (posStart == Integer.MAX_VALUE) ? tokens.size() : posStart;
        for (int k = i; k < end; k++) {
            String t = tokens.get(k);
            if (t == null || !t.startsWith("-")) {
                continue;
            }
            for (String key : keys) {
                if (key == null) {
                    continue;
                }
                if (t.equals(key) || t.equals("--no-" + stripDashes(key))) {
                    return k;
                }
            }
        }
        return -1;
    }

    /**
     * Used for consuming the key at the given index and advancing the cursor.
     *
     * @param idx the index to consume
     */
    private void consumeAt(int idx) {
        String tok = tokens.get(idx);
        tokens.set(idx, null);
        if (i <= idx) {
            i = idx + 1;
        }
        if ("--".equals(tok)) {
            posStart = idx;
        }
    }

    /**
     * Used for computing the next non-null token index after a given index.
     *
     * @param idx the current index
     * @return the next index candidate
     */
    private static int nextIndex(int idx) {
        return idx + 1;
    }

    /**
     * Used for removing leading dashes from a key string.
     *
     * @param k the key string, possibly starting with dashes
     * @return the key without leading dashes
     */
    private static String stripDashes(String k) {
        if (k == null) {
            return "";
        }
        int s = 0;
        while (s < k.length() && k.charAt(s) == '-') {
            s++;
        }
        return k.substring(s);
    }

    /**
     * Used for throwing a formatted {@link IllegalArgumentException}.
     *
     * @param fmt  a {@link String#format(String, Object...)}-style format
     * @param args the arguments to format into the message
     * @throws IllegalArgumentException always
     */
    private static void error(String fmt, Object... args) {
        throw new IllegalArgumentException(String.format(fmt, args));
    }

    /**
     * Used for joining key aliases for display in error messages.
     *
     * @param keys the key aliases
     * @return the joined label or {@code "<option>"} when none
     */
    private static String join(String... keys) {
        if (keys == null || keys.length == 0) {
            return "<option>";
        }
        return String.join("/", keys);
    }

    /**
     * Used for converting a string to a {@link java.nio.file.Path} with {@code ~}
     * expansion.
     *
     * @param v the input path string
     * @return the corresponding path
     */
    private static java.nio.file.Path toPath(String v) {
        if (v.startsWith("~")) {
            String home = System.getProperty("user.home", "");
            if (v.equals("~")) {
                return java.nio.file.Paths.get(home);
            }
            if (v.startsWith("~/")) {
                return java.nio.file.Paths.get(home, v.substring(2));
            }
        }
        return java.nio.file.Paths.get(v);
    }

    /**
     * Used for parsing an {@link Integer} from a string, with default fallback.
     *
     * @param s   the input string; may be {@code null}
     * @param def the default value when {@code s} is {@code null}
     * @param key the key label used for error messages
     * @return the parsed integer or {@code def} when {@code s} is {@code null}
     * @throws IllegalArgumentException if parsing fails
     */
    private static Integer parseInt(String s, Integer def, String key) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException nfe) {
            error("Invalid integer for %s: '%s'", key, s);
            return null;
        }
    }

    /**
     * Used for parsing a {@link Long} from a string, with default fallback.
     *
     * @param s   the input string; may be {@code null}
     * @param def the default value when {@code s} is {@code null}
     * @param key the key label used for error messages
     * @return the parsed long or {@code def} when {@code s} is {@code null}
     * @throws IllegalArgumentException if parsing fails
     */
    private static Long parseLong(String s, Long def, String key) {
        if (s == null) {
            return def;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException nfe) {
            error("Invalid long for %s: '%s'", key, s);
            return null;
        }
    }

    /**
     * Used for parsing a {@link Double} from a string, with default fallback.
     *
     * @param s   the input string; may be {@code null}
     * @param def the default value when {@code s} is {@code null}
     * @param key the key label used for error messages
     * @return the parsed double or {@code def} when {@code s} is {@code null}
     * @throws IllegalArgumentException if parsing fails
     */
    private static Double parseDouble(String s, Double def, String key) {
        if (s == null) {
            return def;
        }
        try {
            return Double.valueOf(s.trim());
        } catch (NumberFormatException nfe) {
            error("Invalid double for %s: '%s'", key, s);
            return null;
        }
    }

    /**
     * Used for parsing a {@link Duration} from a string.
     * Accepts suffixes {@code ms}, {@code s}, {@code m}, {@code h}, or a plain
     * number (milliseconds).
     *
     * @param s   the input string
     * @param key the key label used for error messages
     * @return the parsed duration
     * @throws IllegalArgumentException if parsing fails
     */
    private static Duration parseDuration(String s, String key) {
        String v = s.trim().toLowerCase();
        try {
            if (v.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(v.substring(0, v.length() - 2)));
            }
            if (v.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(v.substring(0, v.length() - 1)));
            }
            if (v.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(v.substring(0, v.length() - 1)));
            }
            if (v.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(v.substring(0, v.length() - 1)));
            }
            return Duration.ofMillis(Long.parseLong(v));
        } catch (NumberFormatException nfe) {
            error("Invalid duration for %s: '%s' (use e.g. 500ms, 60s, 2m, 1h, or plain ms)", key, s);
            return null;
        }
    }
}
