package chess.engine;

import java.util.Locale;

/**
 * Immutable game-clock time control: a base time per game plus a per-move
 * increment, both in milliseconds.
 *
 * <p>
 * The canonical text form is {@code BASE+INC} in seconds with optional decimal
 * fractions, for example {@code 10+0.1} for ten seconds base plus a tenth of a
 * second per move, matching the common engine-testing notation. A bare
 * {@code BASE} (no plus sign) means no increment. {@link #NONE} represents the
 * absence of a game clock so callers can avoid {@code null}.
 * </p>
 *
 * @param baseMillis base time per game in milliseconds, or zero for no clock
 * @param incrementMillis per-move increment in milliseconds
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public record TimeControl(
    /**
     * Base time per game in milliseconds. A value of zero means the clock is
     * disabled (see {@link #NONE}).
     */
    long baseMillis,
    /**
     * Increment added to the clock after each completed move, in milliseconds.
     */
    long incrementMillis
) {

    /**
     * Disabled time control (no game clock).
     */
    public static final TimeControl NONE = new TimeControl(0L, 0L);

    /**
     * Milliseconds per second, for spec parsing and formatting.
     */
    private static final long MILLIS_PER_SECOND = 1_000L;

    /**
     * Validates the time control.
     *
     * @param baseMillis base time per game in milliseconds, or zero for no clock
     * @param incrementMillis per-move increment in milliseconds
     * @throws IllegalArgumentException if a component is negative or an
     *         increment is given without a base time
     */
    public TimeControl {
        if (baseMillis < 0L || incrementMillis < 0L) {
            throw new IllegalArgumentException("time control must be non-negative");
        }
        if (baseMillis == 0L && incrementMillis > 0L) {
            throw new IllegalArgumentException("time control increment requires a base time");
        }
    }

    /**
     * Returns whether this time control enables a game clock.
     *
     * @return true when a positive base time is configured
     */
    public boolean enabled() {
        return baseMillis > 0L;
    }

    /**
     * Parses a {@code BASE+INC} specification in seconds (decimals allowed),
     * for example {@code 10+0.1}; a bare {@code BASE} means no increment.
     *
     * @param spec time-control specification text
     * @return parsed time control with a positive base time
     * @throws IllegalArgumentException for a malformed or non-positive spec
     */
    public static TimeControl parse(String spec) {
        String trimmed = spec == null ? "" : spec.trim();
        int plus = trimmed.indexOf('+');
        String baseText = plus < 0 ? trimmed : trimmed.substring(0, plus).trim();
        String incrementText = plus < 0 ? "0" : trimmed.substring(plus + 1).trim();
        long baseMillis = parseSecondsMillis(baseText, spec);
        long incrementMillis = parseSecondsMillis(incrementText, spec);
        if (baseMillis <= 0L) {
            throw new IllegalArgumentException(
                    "time control base must be positive: '" + spec + "'");
        }
        return new TimeControl(baseMillis, incrementMillis);
    }

    /**
     * Parses one seconds value of a spec into milliseconds.
     *
     * @param text seconds text, decimals allowed
     * @param spec full specification, for diagnostics
     * @return non-negative milliseconds, rounded to the nearest millisecond
     * @throws IllegalArgumentException for empty, malformed, or negative text
     */
    private static long parseSecondsMillis(String text, String spec) {
        if (text.isEmpty()) {
            throw new IllegalArgumentException(invalidSpec(spec));
        }
        double seconds;
        try {
            seconds = Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(invalidSpec(spec));
        }
        if (!Double.isFinite(seconds) || seconds < 0.0) {
            throw new IllegalArgumentException(invalidSpec(spec));
        }
        return Math.round(seconds * MILLIS_PER_SECOND);
    }

    /**
     * Builds the shared malformed-spec diagnostic.
     *
     * @param spec offending specification text
     * @return diagnostic message
     */
    private static String invalidSpec(String spec) {
        return "invalid time control '" + spec + "' (expected BASE+INC seconds, e.g. 10+0.1)";
    }

    /**
     * Returns the canonical {@code BASE+INC} text in seconds, for example
     * {@code 10+0.1}; round-trips through {@link #parse}.
     *
     * @return canonical spec text
     */
    public String text() {
        return secondsText(baseMillis) + "+" + secondsText(incrementMillis);
    }

    /**
     * Formats milliseconds as compact seconds without trailing zeros.
     *
     * @param millis non-negative milliseconds
     * @return compact seconds text, for example {@code 10} or {@code 0.05}
     */
    private static String secondsText(long millis) {
        if (millis % MILLIS_PER_SECOND == 0L) {
            return Long.toString(millis / MILLIS_PER_SECOND);
        }
        String fraction = String.format(Locale.ROOT, "%03d", millis % MILLIS_PER_SECOND);
        while (fraction.endsWith("0")) {
            fraction = fraction.substring(0, fraction.length() - 1);
        }
        return (millis / MILLIS_PER_SECOND) + "." + fraction;
    }
}
