package application.gui.workbench.ui;

import java.util.Locale;
import javax.swing.JTextField;

/**
 * Live type validation for Workbench text fields.
 *
 * <p>Several settings fields only accept a specific data type — a whole number
 * of openings, a decimal page margin, and so on. Stock {@link JTextField}s
 * happily accept {@code "160numbererrr"} and only fail much later when the value
 * reaches the CLI. A {@code FieldValidator} watches a field's text and, the
 * moment it stops matching the expected type, paints the field's border in the
 * error colour (via {@link InputChrome#setInvalid}) and swaps its tooltip for a
 * short explanation of what the field expects. Clearing the bad input restores
 * the resting border and the field's original tooltip.</p>
 *
 * <p>Hosts that want to gate a run/apply action pass an {@code onChange}
 * callback; it fires after every re-check so the host can re-aggregate validity
 * across all of its fields (see {@link #valid()} and {@link #problem()}).</p>
 */
public final class FieldValidator {

    /**
     * A type check for a field's raw text.
     */
    @FunctionalInterface
    public interface Check {

        /**
         * Validates one raw field value.
         *
         * @param value current field text (never {@code null})
         * @return {@code null} when the value is acceptable, otherwise a short,
         *     human-readable reason it is not
         */
        String problem(String value);
    }

    /**
     * The validated field.
     */
    private final JTextField field;

    /**
     * The type check applied to the field text.
     */
    private final Check check;

    /**
     * Callback invoked after every re-check, or {@code null}.
     */
    private final Runnable onChange;

    /**
     * Tooltip shown while the field is valid (its original tooltip).
     */
    private final String restingTooltip;

    /**
     * The current problem message, or {@code null} while valid.
     */
    private String problem;

    /**
     * Creates and wires a validator. Use {@link #attach}.
     *
     * @param field validated field
     * @param check type check
     * @param onChange post-check callback, or {@code null}
     */
    private FieldValidator(JTextField field, Check check, Runnable onChange) {
        this.field = field;
        this.check = check;
        this.onChange = onChange;
        this.restingTooltip = field.getToolTipText();
    }

    /**
     * Attaches live type validation to a field.
     *
     * @param field target field
     * @param check type check
     * @return the validator, retained so callers can query {@link #valid()}
     */
    public static FieldValidator attach(JTextField field, Check check) {
        return attach(field, check, null);
    }

    /**
     * Attaches live type validation to a field with a re-check callback.
     *
     * @param field target field
     * @param check type check
     * @param onChange callback run after every re-check (for example to refresh
     *     a status badge or enable/disable a run button), or {@code null}
     * @return the validator, retained so callers can query {@link #valid()}
     */
    public static FieldValidator attach(JTextField field, Check check, Runnable onChange) {
        FieldValidator validator = new FieldValidator(field, check, onChange);
        DocumentChangeSupport.onTextChange(validator::revalidate, field);
        field.addPropertyChangeListener("enabled", event -> validator.revalidate());
        validator.revalidate();
        return validator;
    }

    /**
     * Re-checks the field and applies the resulting border and tooltip. A
     * disabled field is always treated as valid, since its value is not used.
     */
    public void revalidate() {
        String value = field.getText() == null ? "" : field.getText();
        String next = field.isEnabled() ? check.problem(value) : null;
        this.problem = next;
        InputChrome.setInvalid(field, next != null);
        field.setToolTipText(next != null ? next : restingTooltip);
        if (onChange != null) {
            onChange.run();
        }
    }

    /**
     * Returns whether the field currently holds an acceptable value.
     *
     * @return true when valid
     */
    public boolean valid() {
        return problem == null;
    }

    /**
     * Returns the current validation problem.
     *
     * @return problem message, or {@code null} when valid
     */
    public String problem() {
        return problem;
    }

    /**
     * Builds a check for a whole number written in plain digits.
     *
     * @param min smallest accepted value (inclusive)
     * @param max largest accepted value (inclusive)
     * @param allowBlank true when an empty field is acceptable (uses a default)
     * @return whole-number check
     */
    public static Check wholeNumber(long min, long max, boolean allowBlank) {
        return value -> wholeNumberProblem(value, min, max, allowBlank, false);
    }

    /**
     * Builds a check for a whole number that tolerates grouping separators
     * ({@code ,}, {@code _}, or spaces), matching fields that display grouped
     * defaults such as {@code 50,000}.
     *
     * @param min smallest accepted value (inclusive)
     * @param max largest accepted value (inclusive)
     * @param allowBlank true when an empty field is acceptable (uses a default)
     * @return grouped whole-number check
     */
    public static Check groupedWholeNumber(long min, long max, boolean allowBlank) {
        return value -> wholeNumberProblem(value, min, max, allowBlank, true);
    }

    /**
     * Builds a check for a decimal number.
     *
     * @param min smallest accepted value (inclusive)
     * @param max largest accepted value (inclusive)
     * @param allowBlank true when an empty field is acceptable (uses a default)
     * @return decimal check
     */
    public static Check decimal(double min, double max, boolean allowBlank) {
        return value -> {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.isEmpty()) {
                return allowBlank ? null : "Enter a number" + rangeSuffix(min, max) + ".";
            }
            if (!trimmed.matches("(?:\\d+(?:\\.\\d+)?|\\.\\d+)")) {
                return "Enter a number — digits only, with an optional decimal point.";
            }
            double parsed;
            try {
                parsed = Double.parseDouble(trimmed);
            } catch (NumberFormatException ex) {
                return "Enter a number — digits only, with an optional decimal point.";
            }
            if (parsed < min || parsed > max) {
                return "Enter a number" + rangeSuffix(min, max) + ".";
            }
            return null;
        };
    }

    /**
     * Builds a check for a whole number with an optional trailing unit, such as
     * a budget ({@code 3000} nodes or {@code 200ms}) or a duration ({@code 1s}).
     * A plain number (no unit) is always allowed.
     *
     * @param allowBlank true when an empty field is acceptable (uses a default)
     * @param units accepted unit suffixes (case-insensitive); longer suffixes
     *     are matched first so {@code ms} wins over {@code m}
     * @return number-with-unit check
     */
    public static Check numberWithOptionalUnit(boolean allowBlank, String... units) {
        String[] ordered = units.clone();
        // Longest first so "ms" is preferred over "m" when both are accepted.
        java.util.Arrays.sort(ordered, (a, b) -> b.length() - a.length());
        StringBuilder pattern = new StringBuilder("(?i)\\d+");
        if (ordered.length > 0) {
            pattern.append("(?:");
            for (int i = 0; i < ordered.length; i++) {
                if (i > 0) {
                    pattern.append('|');
                }
                pattern.append(java.util.regex.Pattern.quote(ordered[i]));
            }
            pattern.append(")?");
        }
        String regex = pattern.toString();
        String hint = ordered.length == 0 ? ""
                : " optionally followed by a unit (" + String.join(", ", units) + ")";
        return value -> {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.isEmpty()) {
                return allowBlank ? null : "Enter a whole number" + hint + ".";
            }
            if (!trimmed.matches(regex)) {
                return "Enter a whole number" + hint + " — for example 200 or 500ms.";
            }
            return null;
        };
    }

    /**
     * Evaluates a whole-number value against the given bounds.
     *
     * @param value raw field text
     * @param min smallest accepted value (inclusive)
     * @param max largest accepted value (inclusive)
     * @param allowBlank true when an empty field is acceptable
     * @param grouped true to strip grouping separators before parsing
     * @return problem message, or {@code null} when acceptable
     */
    private static String wholeNumberProblem(String value, long min, long max,
            boolean allowBlank, boolean grouped) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return allowBlank ? null : "Enter a whole number" + rangeSuffix(min, max) + ".";
        }
        String digits = grouped ? trimmed.replaceAll("[,_\\s]", "") : trimmed;
        // Every numeric field here is non-negative, so a leading minus is just
        // another disallowed character rather than a separate "below minimum".
        if (!digits.matches("\\d+")) {
            return "Enter a whole number — digits only, no letters or symbols.";
        }
        long parsed;
        try {
            parsed = Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return "That number is too large.";
        }
        if (parsed < min || parsed > max) {
            return "Enter a whole number" + rangeSuffix(min, max) + ".";
        }
        return null;
    }

    /**
     * Describes the accepted range for an error message.
     *
     * @param min smallest accepted value (inclusive)
     * @param max largest accepted value (inclusive)
     * @return a phrase such as {@code " of at least 1"}, or an empty string when
     *     the bounds cover the whole range
     */
    private static String rangeSuffix(long min, long max) {
        boolean hasMin = min != Long.MIN_VALUE;
        boolean hasMax = max != Long.MAX_VALUE;
        if (min == 0 && !hasMax) {
            // "zero or more" reads as plain "a whole number"; no suffix needed.
            return "";
        }
        if (hasMin && hasMax) {
            return " between " + group(min) + " and " + group(max);
        }
        if (hasMin) {
            return " of at least " + group(min);
        }
        if (hasMax) {
            return " no greater than " + group(max);
        }
        return "";
    }

    /**
     * Describes the accepted range for a decimal error message.
     *
     * @param min smallest accepted value (inclusive)
     * @param max largest accepted value (inclusive)
     * @return a phrase describing the bounds, or an empty string when unbounded
     */
    private static String rangeSuffix(double min, double max) {
        boolean hasMin = min != Double.NEGATIVE_INFINITY;
        boolean hasMax = max != Double.POSITIVE_INFINITY;
        if (min == 0 && !hasMax) {
            return "";
        }
        if (hasMin && hasMax) {
            return " between " + trimDecimal(min) + " and " + trimDecimal(max);
        }
        if (hasMin) {
            return " of at least " + trimDecimal(min);
        }
        if (hasMax) {
            return " no greater than " + trimDecimal(max);
        }
        return "";
    }

    /**
     * Formats a bound with ASCII grouping separators.
     *
     * @param value bound value
     * @return grouped value
     */
    private static String group(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * Formats a decimal bound without a trailing {@code .0}.
     *
     * @param value bound value
     * @return formatted value
     */
    private static String trimDecimal(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.format(Locale.ROOT, "%,d", (long) value);
        }
        return String.format(Locale.ROOT, "%s", value);
    }
}
