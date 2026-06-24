package application.gui.workbench.command;

import java.util.List;

/**
 * Derives a short, human-readable result summary from a finished command's
 * arguments, exit code and combined output.
 *
 * <p>Deliberately conservative: it reads a handful of machine-readable tokens
 * the CRTK CLI already emits ({@code bestmove ...}, perft totals, validation
 * verdicts) and otherwise falls back to a plain exit-code summary. It does not
 * try to interpret arbitrary prose — when in doubt it says so rather than
 * guessing.</p>
 */
public final class CommandResultParser {

    /**
     * Prevents instantiation.
     */
    private CommandResultParser() {
        // utility
    }

    /**
     * Summarises a finished command run.
     *
     * @param args command arguments (without the {@code crtk} prefix)
     * @param exitCode process exit code
     * @param output combined stdout/stderr
     * @return a short one-line summary
     */
    public static String summarize(List<String> args, int exitCode, String output) {
        String text = output == null ? "" : output;
        if (exitCode != 0) {
            String detail = firstNonBlankLine(text);
            return detail.isEmpty()
                    ? "exit " + exitCode
                    : "exit " + exitCode + " · " + clip(detail);
        }
        String command = args == null || args.isEmpty() ? "" : String.join(" ", args);

        String bestMove = tokenAfter(text, "bestmove");
        if (!bestMove.isEmpty()) {
            return "bestmove " + bestMove;
        }
        String perft = perftSummary(text);
        if (!perft.isEmpty()) {
            return perft;
        }
        if (command.contains("perft-suite")) {
            return "perft suite passed";
        }
        if (command.startsWith("config validate")) {
            return "config valid";
        }
        if (command.equals("doctor") || command.startsWith("doctor ")) {
            return "doctor: all checks passed";
        }
        if (command.contains("uci-smoke")) {
            return "engine smoke passed";
        }
        String detail = firstNonBlankLine(text);
        return detail.isEmpty() ? "ok" : clip(detail);
    }

    /**
     * Builds the richer parsed-result text used by the Run command builder.
     * The method is intentionally conservative and keeps raw output available
     * for anything it cannot safely interpret.
     *
     * @param args command arguments (without the {@code crtk} prefix)
     * @param exitCode process exit code
     * @param output combined stdout/stderr
     * @param millis elapsed time in milliseconds
     * @return built the richer parsed-result text used by the Run command builder
     */
    public static String detail(List<String> args, int exitCode, String output, long millis) {
        String text = output == null ? "" : output;
        StringBuilder result = new StringBuilder();
        appendField(result, "Status", exitCode == 0 ? "complete" : "exit " + exitCode);
        if (millis >= 0L) {
            appendField(result, "Elapsed", millis + " ms");
        }
        String command = args == null || args.isEmpty() ? "" : String.join(" ", args);
        String bestMove = tokenAfter(text, "bestmove");
        if (!bestMove.isEmpty() || isEngineSearchCommand(command)) {
            appendField(result, "Best move", bestMove.isEmpty() ? "-" : bestMove);
            appendFieldIfPresent(result, "Score", scoreSummary(text));
            appendFieldIfPresent(result, "Depth", lastTokenAfter(text, "depth"));
            appendFieldIfPresent(result, "Nodes", lastTokenAfter(text, "nodes"));
            appendFieldIfPresent(result, "NPS", lastTokenAfter(text, "nps"));
            appendFieldIfPresent(result, "Time", timeSummary(text));
        } else {
            String perft = perftSummary(text);
            if (!perft.isEmpty()) {
                appendField(result, "Result", perft);
            } else if (command.startsWith("config validate")) {
                appendField(result, "Result", exitCode == 0 ? "config valid" : "config issue");
            } else if (command.equals("doctor") || command.startsWith("doctor ")) {
                appendField(result, "Result", exitCode == 0 ? "doctor checks passed" : "doctor issue");
            } else {
                String line = firstNonBlankLine(text);
                appendField(result, "Result", line.isEmpty() ? (exitCode == 0 ? "ok" : "see raw output") : clip(line));
            }
        }
        if (exitCode != 0) {
            String line = firstNonBlankLine(text);
            if (!line.isEmpty()) {
                appendField(result, "Message", clip(line));
            }
        }
        return result.toString().stripTrailing();
    }

    /**
     * Returns whether the command is an engine search-style command.
     *
     * @param command command text
     * @return true when parsed engine fields are useful
     */
    private static boolean isEngineSearchCommand(String command) {
        return command.startsWith("engine bestmove")
                || command.startsWith("engine analyze")
                || command.startsWith("engine builtin");
    }

    /**
     * Returns the whitespace-delimited token that follows the given marker
     * word in the output, or an empty string when the marker is absent.
     *
     * @param text output text
     * @param marker marker word to look for
     * @return token after the marker, or empty
     */
    private static String tokenAfter(String text, String marker) {
        for (String line : text.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if (parts[i].equals(marker)) {
                    return parts[i + 1];
                }
            }
        }
        return "";
    }

    /**
     * Returns the last token following a marker in the full output.
     *
     * @param text output text
     * @param marker marker word
     * @return last following token, or empty
     */
    private static String lastTokenAfter(String text, String marker) {
        String result = "";
        for (String line : text.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if (parts[i].equals(marker)) {
                    result = parts[i + 1];
                }
            }
        }
        return result;
    }

    /**
     * Extracts an engine score from UCI-like info output.
     *
     * @param text output text
     * @return score summary, or empty
     */
    private static String scoreSummary(String text) {
        String result = "";
        for (String line : text.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            for (int i = 0; i < parts.length - 2; i++) {
                if ("score".equals(parts[i])) {
                    result = parts[i + 1] + " " + parts[i + 2];
                }
            }
        }
        return result;
    }

    /**
     * Extracts the most useful time token from engine output.
     *
     * @param text output text
     * @return time summary, or empty
     */
    private static String timeSummary(String text) {
        String time = lastTokenAfter(text, "time");
        if (!time.isEmpty()) {
            return time + " ms";
        }
        return "";
    }

    /**
     * Extracts a perft node total when the output carries one.
     *
     * @param text output text
     * @return perft summary, or an empty string
     */
    private static String perftSummary(String text) {
        for (String line : text.split("\\R")) {
            String lower = line.toLowerCase();
            int idx = lower.indexOf("nodes");
            if (idx >= 0) {
                String digits = lastNumber(line);
                if (!digits.isEmpty()) {
                    return "perft: " + digits + " nodes";
                }
            }
        }
        return "";
    }

    /**
     * Returns the last run of digits (with optional separators stripped) in a
     * line, or an empty string when there is none.
     *
     * @param line text line
     * @return last numeric token, or empty
     */
    private static String lastNumber(String line) {
        String compact = line.replace(",", "").replace("_", "");
        String result = "";
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);
            if (Character.isDigit(c)) {
                current.append(c);
            } else {
                if (current.length() > 0) {
                    result = current.toString();
                }
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            result = current.toString();
        }
        return result;
    }

    /**
     * Returns the first non-blank line of the text, trimmed.
     *
     * @param text text to scan
     * @return first non-blank line, or an empty string
     */
    private static String firstNonBlankLine(String text) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    /**
     * Clips a string to a compact length for table display.
     *
     * @param text text to clip
     * @return clipped text
     */
    private static String clip(String text) {
        int max = 80;
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /**
     * Appends a label/value pair.
     *
     * @param out destination
     * @param label display label
     * @param value candidate value
     */
    private static void appendField(StringBuilder out, String label, String value) {
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(label).append(": ").append(value == null || value.isBlank() ? "-" : value);
    }

    /**
     * Appends a label/value pair only when a value is present.
     *
     * @param out destination
     * @param label display label
     * @param value candidate value
     */
    private static void appendFieldIfPresent(StringBuilder out, String label, String value) {
        if (value != null && !value.isBlank()) {
            appendField(out, label, value);
        }
    }
}
