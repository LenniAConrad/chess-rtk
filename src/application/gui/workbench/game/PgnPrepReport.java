package application.gui.workbench.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic opening and player-prep report for loaded PGN database rows.
 */
public final class PgnPrepReport {

    /**
     * Prevents instantiation.
     */
    private PgnPrepReport() {
        // utility
    }

    /**
     * Builds a compact prep report.
     *
     * @param entries PGN database rows
     * @param player optional player name/query
     * @return report text
     */
    public static String report(List<PgnExplorerModel.Entry> entries, String player) {
        List<PgnExplorerModel.Entry> rows = entries == null ? List.of() : List.copyOf(entries);
        String normalizedPlayer = normalize(player);
        boolean playerMode = !normalizedPlayer.isBlank();
        Score score = score(rows, normalizedPlayer);
        StringBuilder out = new StringBuilder(2048);
        out.append("Prep report");
        if (playerMode) {
            out.append(" for ").append(player.trim());
        }
        out.append('\n');
        out.append("Games: ").append(rows.size()).append('\n');
        if (playerMode) {
            out.append("Player games: ").append(score.games()).append("  score: ")
                    .append(formatScore(score)).append('\n');
            out.append("As White: ").append(score.whiteGames()).append("  As Black: ")
                    .append(score.blackGames()).append('\n');
        }
        appendCounts(out, "Results", resultCounts(rows), 4);
        appendCounts(out, "Openings", openingCounts(rows), 8);
        appendCounts(out, "ECO", ecoCounts(rows), 8);
        if (playerMode) {
            appendWeakLines(out, rows, normalizedPlayer);
        }
        return out.toString();
    }

    /**
     * Computes player score.
     *
     * @param entries entries
     * @param player normalized player
     * @return score
     */
    private static Score score(List<PgnExplorerModel.Entry> entries, String player) {
        if (player.isBlank()) {
            return new Score(0, 0, 0, 0.0);
        }
        int games = 0;
        int white = 0;
        int black = 0;
        double points = 0.0;
        for (PgnExplorerModel.Entry entry : entries) {
            boolean asWhite = normalize(entry.white()).contains(player);
            boolean asBlack = normalize(entry.black()).contains(player);
            if (!asWhite && !asBlack) {
                continue;
            }
            games++;
            white += asWhite ? 1 : 0;
            black += asBlack ? 1 : 0;
            points += pointsFor(entry.result(), asWhite);
        }
        return new Score(games, white, black, points);
    }

    /**
     * Counts results.
     *
     * @param entries entries
     * @return result counts
     */
    private static Map<String, Integer> resultCounts(List<PgnExplorerModel.Entry> entries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PgnExplorerModel.Entry entry : entries) {
            add(counts, blankAs(entry.result(), "*"));
        }
        return counts;
    }

    /**
     * Counts openings.
     *
     * @param entries entries
     * @return opening counts
     */
    private static Map<String, Integer> openingCounts(List<PgnExplorerModel.Entry> entries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PgnExplorerModel.Entry entry : entries) {
            add(counts, blankAs(entry.opening(), "Unknown opening"));
        }
        return counts;
    }

    /**
     * Counts ECO codes.
     *
     * @param entries entries
     * @return ECO counts
     */
    private static Map<String, Integer> ecoCounts(List<PgnExplorerModel.Entry> entries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PgnExplorerModel.Entry entry : entries) {
            add(counts, blankAs(entry.eco(), "Unknown"));
        }
        return counts;
    }

    /**
     * Appends weak-line candidates for one player.
     *
     * @param out output
     * @param entries entries
     * @param player normalized player
     */
    private static void appendWeakLines(StringBuilder out, List<PgnExplorerModel.Entry> entries, String player) {
        Map<String, LineScore> scores = new LinkedHashMap<>();
        for (PgnExplorerModel.Entry entry : entries) {
            boolean asWhite = normalize(entry.white()).contains(player);
            boolean asBlack = normalize(entry.black()).contains(player);
            if (!asWhite && !asBlack) {
                continue;
            }
            String key = lineKey(entry);
            LineScore line = scores.computeIfAbsent(key, unused -> new LineScore());
            line.games++;
            line.points += pointsFor(entry.result(), asWhite);
        }
        List<Map.Entry<String, LineScore>> weak = new ArrayList<>(scores.entrySet());
        weak.removeIf(entry -> entry.getValue().games < 1 || entry.getValue().scorePercent() >= 50.0);
        weak.sort(Comparator
                .<Map.Entry<String, LineScore>>comparingDouble(entry -> entry.getValue().scorePercent())
                .thenComparing((a, b) -> Integer.compare(b.getValue().games, a.getValue().games))
                .thenComparing(Map.Entry::getKey));
        out.append("Weak-line candidates:\n");
        if (weak.isEmpty()) {
            out.append("- none below 50% in the filtered games\n");
            return;
        }
        int limit = Math.min(6, weak.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, LineScore> entry = weak.get(i);
            out.append("- ").append(entry.getKey()).append(": ")
                    .append(entry.getValue().games).append(" game")
                    .append(entry.getValue().games == 1 ? "" : "s")
                    .append(", ").append(formatPercent(entry.getValue().scorePercent())).append('\n');
        }
    }

    /**
     * Appends one count section.
     *
     * @param out output
     * @param title section title
     * @param counts counts
     * @param limit max rows
     */
    private static void appendCounts(StringBuilder out, String title, Map<String, Integer> counts, int limit) {
        out.append(title).append(":\n");
        if (counts.isEmpty()) {
            out.append("- none\n");
            return;
        }
        List<Map.Entry<String, Integer>> rows = new ArrayList<>(counts.entrySet());
        rows.sort(Comparator
                .<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        for (int i = 0; i < Math.min(limit, rows.size()); i++) {
            Map.Entry<String, Integer> row = rows.get(i);
            out.append("- ").append(row.getKey()).append(": ").append(row.getValue()).append('\n');
        }
    }

    /**
     * Adds one count.
     *
     * @param counts counts
     * @param key key
     */
    private static void add(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    /**
     * Returns a line label.
     *
     * @param entry entry
     * @return line label
     */
    private static String lineKey(PgnExplorerModel.Entry entry) {
        String eco = blankAs(entry.eco(), "Unknown");
        String opening = blankAs(entry.opening(), "Unknown opening");
        return eco + " " + opening;
    }

    /**
     * Calculates player points from a result.
     *
     * @param result result token
     * @param playerWhite true when the player had White
     * @return points
     */
    private static double pointsFor(String result, boolean playerWhite) {
        return switch (result == null ? "*" : result) {
            case "1-0" -> playerWhite ? 1.0 : 0.0;
            case "0-1" -> playerWhite ? 0.0 : 1.0;
            case "1/2-1/2" -> 0.5;
            default -> 0.0;
        };
    }

    /**
     * Formats a score.
     *
     * @param score score
     * @return text
     */
    private static String formatScore(Score score) {
        if (score.games() == 0) {
            return "0/0";
        }
        return String.format(Locale.ROOT, "%.1f/%d (%s)", score.points(), score.games(),
                formatPercent(score.points() * 100.0 / score.games()));
    }

    /**
     * Formats a percentage.
     *
     * @param value percent value
     * @return text
     */
    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    /**
     * Normalizes text for matching.
     *
     * @param value text
     * @return normalized text
     */
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Returns fallback for blank values.
     *
     * @param value value
     * @param fallback fallback
     * @return value or fallback
     */
    private static String blankAs(String value, String fallback) {
        return value == null || value.isBlank() || "?".equals(value) ? fallback : value;
    }

    /**
     * Player score aggregate.
     *
     * @param games games
     * @param whiteGames games as White
     * @param blackGames games as Black
     * @param points points
     */
    private record Score(int games, int whiteGames, int blackGames, double points) {
    }

    /**
     * Per-line score aggregate.
     */
    private static final class LineScore {
        /**
         * Games.
         */
        private int games;

        /**
         * Points.
         */
        private double points;

        /**
         * Returns score percent.
         *
         * @return percent
         */
        private double scorePercent() {
            return games == 0 ? 0.0 : points * 100.0 / games;
        }
    }
}
