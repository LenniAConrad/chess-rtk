package application.gui.workbench.game;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

import chess.core.Position;

/**
 * Shared FEN scanning and validation helpers for pasted positions and batch
 * inputs.
 */
public final class FenInput {

    /**
     * Prevents instantiation.
     */
    private FenInput() {
        // utility
    }

    /**
     * Result of FEN scanning: a successful FEN, or the most recent rejection
     * message from a non-bracketed non-empty line.
     *
     * @param fen parsed FEN, or null
     * @param firstError first parse error, or null
     */
    public record Scan(String fen, String firstError) { }

    /**
     * Batch FEN validation summary.
     *
     * @param rows non-empty candidate rows
     * @param validRows valid FEN rows
     * @param firstErrorLine one-based first error row, or zero
     * @param firstError first parse error, or null
     */
    public record Summary(int rows, int validRows, int firstErrorLine, String firstError) {

        /**
         * Returns whether at least one row failed to parse.
         *
         * @return true when a parse error was found
         */
        public boolean hasError() {
            return firstError != null;
        }
    }

    /**
     * Returns the first valid FEN line from pasted text, or {@code null} when
     * no candidate line parses.
     *
     * @param text raw text
     * @return FEN or null
     */
    public static String firstFenLine(String text) {
    return firstFenOrFailure(text).fen();
    }

    /**
     * Scans pasted text for a FEN line and remembers the first parse error.
     *
     * @param text raw text
     * @return scan result
     */
    public static Scan firstFenOrFailure(String text) {
        String firstError = null;
        for (String line : text.split("\\R")) {
            String candidate = line.trim();
            if (candidate.isEmpty() || candidate.startsWith("[")) {
                continue;
            }
            try {
    new Position(candidate);
    return new Scan(candidate, null);
            } catch (IllegalArgumentException ex) {
                if (firstError == null) {
                    firstError = fenErrorMessage(candidate, ex);
                }
            }
        }
    return new Scan(null, firstError);
    }

    /**
     * Validates every non-empty batch FEN row.
     *
     * @param text raw input
     * @return validation summary
     */
    public static Summary validateBatchFenInput(String text) {
        int rows = 0;
        int validRows = 0;
        int firstErrorLine = 0;
        String firstError = null;
        String[] lines = text == null ? new String[0] : text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String candidate = lines[i].trim();
            if (candidate.isEmpty() || candidate.startsWith("[")) {
                continue;
            }
            rows++;
            try {
    new Position(candidate);
                validRows++;
            } catch (IllegalArgumentException ex) {
                if (firstError == null) {
                    firstErrorLine = i + 1;
                    firstError = fenErrorMessage(candidate, ex);
                }
            }
        }
    return new Summary(rows, validRows, firstErrorLine, firstError);
    }

    /**
     * Returns a short FEN label for compact previews.
     *
     * @param fen full FEN
     * @return piece placement plus side to move when available
     */
    public static String compactPreview(String fen) {
        if (fen == null || fen.isBlank()) {
            return "";
        }
        String[] parts = fen.trim().split("\\s+");
        return parts.length > 1 ? parts[0] + " " + parts[1] : parts[0];
    }

    /**
     * Returns a stable FEN parse error message.
     *
     * @param candidate parsed row
     * @param ex parser exception
     * @return human-readable error
     */
    private static String fenErrorMessage(String candidate, IllegalArgumentException ex) {
        return ex.getMessage() == null ? "Could not parse FEN: " + candidate : ex.getMessage();
    }
}
