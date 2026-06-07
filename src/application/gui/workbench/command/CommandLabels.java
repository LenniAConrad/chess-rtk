package application.gui.workbench.command;

import application.gui.workbench.command.CommandTemplates.CommandOption;

/**
 * User-facing labels for command-builder metadata.
 */
final class CommandLabels {

    /**
     * Prevents instantiation.
     */
    private CommandLabels() {
        // utility
    }

    /**
     * Returns an option's user-facing label.
     *
     * @param option option metadata
     * @return display label
     */
    static String displayFlag(CommandOption option) {
        String defaultValue = option.defaultValue() == null ? "" : option.defaultValue();
        if (option.fixedChoice()) {
            return choiceLabel(defaultValue);
        }
        if (option.flag().isBlank() && !option.takesValue() && !defaultValue.isBlank()) {
            return defaultValue;
        }
        if (option.flag().isBlank() && option.splitValue()) {
            return option.choices().isEmpty() ? "arguments" : "command";
        }
        return option.flag().isBlank() ? "argument" : flagLabel(option.flag());
    }

    /**
     * Formats a fixed selector value as a compact UI label.
     *
     * @param value CLI value
     * @return display label
     */
    static String choiceLabel(String value) {
        return switch (value == null ? "" : value) {
            case "uci" -> "UCI";
            case "san" -> "SAN";
            case "both" -> "Both";
            case "uci-info" -> "UCI info";
            default -> value == null || value.isBlank()
                    ? "default"
                    : Character.toUpperCase(value.charAt(0)) + value.substring(1);
        };
    }

    /**
     * Formats common CLI flags as readable command-builder labels.
     *
     * @param flag raw CLI flag
     * @return display label
     */
    private static String flagLabel(String flag) {
        return switch (flag == null ? "" : flag) {
            case "--fen" -> "FEN";
            case "--startpos" -> "Start position";
            case "--randompos" -> "Random position";
            case "--input" -> "Input";
            case "--pgn" -> "PGN";
            case "--protocol-path" -> "Config path";
            case "--max-duration" -> "Max duration";
            case "--duration" -> "Duration";
            case "--depth" -> "Max depth";
            case "--max-nodes", "--nodes" -> "Max nodes";
            case "--multipv" -> "MultiPV / lines";
            case "--threads" -> "Threads";
            case "--hash" -> "Hash MB";
            case "--format" -> "Format";
            case "--fields" -> "Fields";
            case "--output" -> "Output";
            case "--weights" -> "Weights";
            case "--mate" -> "Mate distance";
            case "--json" -> "JSON";
            case "--jsonl" -> "JSONL";
            case "--wdl" -> "WDL";
            case "--no-wdl" -> "No WDL";
            case "--classical" -> "Classical";
            case "--nnue" -> "NNUE";
            case "--lc0" -> "LC0";
            case "--otis" -> "OTIS";
            case "--terminal-aware" -> "Terminal aware";
            case "--divide" -> "Divide";
            case "--gpu" -> "GPU";
            case "--split" -> "GPU split depth";
            case "--include-fen" -> "Include FEN";
            case "--sequence" -> "Sequence";
            case "--delta" -> "Delta";
            case "--mainline" -> "Mainline";
            case "--sidelines" -> "Sidelines";
            case "--analyze" -> "Analyze";
            case "--stdin" -> "stdin";
            case "--engine" -> "Engine";
            case "--detail" -> "Detail";
            case "--budget" -> "Budget";
            case "--model" -> "Model";
            case "--max-new" -> "Max tokens";
            case "--other" -> "Other FEN";
            case "--right" -> "Right FEN";
            case "--no-header" -> "No header";
            case "--quiet" -> "Quiet";
            case "--verbose" -> "Verbose";
            default -> flag == null || flag.isBlank() ? "option" : trimDash(flag);
        };
    }

    /**
     * Converts an unknown CLI flag to readable fallback text.
     *
     * @param flag raw flag
     * @return fallback label
     */
    private static String trimDash(String flag) {
        String text = flag;
        while (text.startsWith("-")) {
            text = text.substring(1);
        }
        text = text.replace('-', ' ');
        return text.isBlank()
                ? "option"
                : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
