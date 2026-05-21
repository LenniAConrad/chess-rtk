package application.gui.workbench;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed engine evaluation from command output.
 *
 * @param mate true when the value is a mate distance
 * @param value signed centipawn or mate value from the side-to-move perspective
 */
record WorkbenchEngineEval(boolean mate, int value) {

    /**
     * Engine evaluation line pattern.
     */
    private static final Pattern EVAL_PATTERN = Pattern.compile("(?m)^\\s*eval:\\s*(#-?\\d+|[+-]?\\d+)\\b");

    /**
     * Parses the first engine evaluation emitted by {@code engine analyze}.
     *
     * @param output command output
     * @return parsed evaluation, or null when unavailable
     */
    static WorkbenchEngineEval parse(String output) {
        if (output == null) {
            return null;
        }
        Matcher matcher = EVAL_PATTERN.matcher(output);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        try {
            if (value.startsWith("#")) {
                return new WorkbenchEngineEval(true, Integer.parseInt(value.substring(1)));
            }
            return new WorkbenchEngineEval(false, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
