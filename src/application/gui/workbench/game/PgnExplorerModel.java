/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.game;

import chess.struct.Game;
import chess.struct.Pgn;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Search model for the workbench PGN explorer.
 */
public final class PgnExplorerModel {

    /**
     * Separator used in compact game labels.
     */
    private static final String SEPARATOR = " | ";

    /**
     * Prevents instantiation.
     */
    private PgnExplorerModel() {
        // utility
    }

    /**
     * Parses PGN content into searchable explorer entries.
     *
     * @param content raw PGN text
     * @return searchable PGN entries
     */
    public static List<Entry> entries(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<Game> games = Pgn.parseGames(content);
        java.util.ArrayList<Entry> out = new java.util.ArrayList<>(games.size());
        for (int i = 0; i < games.size(); i++) {
            out.add(entry(i + 1, games.get(i)));
        }
        return List.copyOf(out);
    }

    /**
     * Filters entries by tokenized title, detail, and tag text.
     *
     * @param entries source entries
     * @param query search query
     * @return matching entries in source order
     */
    public static List<Entry> filter(List<Entry> entries, String query) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.copyOf(entries);
        }
        String[] tokens = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        java.util.ArrayList<Entry> out = new java.util.ArrayList<>();
        for (Entry entry : entries) {
            if (matches(entry.searchable(), tokens)) {
                out.add(entry);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Creates one search entry.
     *
     * @param index one-based game index
     * @param game parsed PGN game
     * @return search entry
     */
    private static Entry entry(int index, Game game) {
        Map<String, String> tags = game.getTags();
        String event = tag(tags, "Event", "PGN");
        String white = tag(tags, "White", "?");
        String black = tag(tags, "Black", "?");
        String result = game.getResult() == null || game.getResult().isBlank()
                ? tag(tags, "Result", "*")
                : game.getResult();
        String title = index + ". " + event + SEPARATOR + white + " vs " + black;
        String detail = detail(tags, result);
        String searchable = (title + ' ' + detail + ' ' + tags.values())
                .toLowerCase(Locale.ROOT);
        return new Entry(index, game, title, detail, searchable);
    }

    /**
     * Builds the secondary detail string.
     *
     * @param tags PGN tags
     * @param result game result
     * @return compact detail
     */
    private static String detail(Map<String, String> tags, String result) {
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        addIfPresent(parts, tag(tags, "Date", ""));
        addIfPresent(parts, tag(tags, "Round", ""));
        addIfPresent(parts, tag(tags, "Site", ""));
        addIfPresent(parts, tag(tags, "ECO", ""));
        addIfPresent(parts, tag(tags, "Opening", ""));
        addIfPresent(parts, result);
        return String.join(SEPARATOR, parts);
    }

    /**
     * Adds a nonblank detail part.
     *
     * @param parts destination detail parts
     * @param value candidate value
     */
    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank() && !"?".equals(value)) {
            parts.add(value);
        }
    }

    /**
     * Returns a tag value with fallback.
     *
     * @param tags PGN tags
     * @param key tag key
     * @param fallback fallback value
     * @return tag value or fallback
     */
    private static String tag(Map<String, String> tags, String key, String fallback) {
        String value = tags.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Returns whether all search tokens match an entry haystack.
     *
     * @param haystack lowercased entry text
     * @param tokens lowercased query tokens
     * @return true when every token matches
     */
    private static boolean matches(String haystack, String[] tokens) {
        for (String token : tokens) {
            if (!token.isBlank() && !haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    /**
     * One searchable PGN game entry.
     *
     * @param index one-based game index
     * @param game parsed game
     * @param title primary display text
     * @param detail secondary display text
     * @param searchable lowercased searchable text
     */
    public record Entry(int index, Game game, String title, String detail, String searchable) {

        /**
         * Serializes this entry back to a single-game PGN block.
         *
         * @return PGN text
         */
        public String pgn() {
            return Pgn.toPgn(game);
        }

        /**
         * Returns the display title for Swing renderers.
         *
         * @return display title
         */
        @Override
        public String toString() {
            return title;
        }
    }
}
