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
}
