package application.gui.workbench.game;

import chess.core.Position;
import chess.core.SAN;
import chess.struct.Game;
import chess.struct.Pgn;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
     * Filters entries to games whose mainline reaches the supplied position.
     * Halfmove and fullmove counters are ignored so imported PGN clock metadata
     * does not block practical database lookups.
     *
     * @param entries source entries
     * @param fen target FEN
     * @return matching entries
     */
    public static List<Entry> filterByPosition(List<Entry> entries, String fen) {
        if (entries == null || entries.isEmpty() || fen == null || fen.isBlank()) {
            return List.of();
        }
        String key = positionKey(fen);
        if (key.isBlank()) {
            return List.of();
        }
        ArrayList<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.reachesPositionKey(key)) {
                out.add(entry);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Removes exact duplicate game records while preserving the first copy and
     * input order.
     *
     * @param entries source entries
     * @return deduplicated entries
     */
    public static List<Entry> deduplicate(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Entry> unique = new LinkedHashMap<>();
        for (Entry entry : entries) {
            unique.putIfAbsent(entry.duplicateKey(), entry);
        }
        return List.copyOf(unique.values());
    }

    /**
     * Counts exact duplicate records.
     *
     * @param entries source entries
     * @return number of duplicate rows that can be removed
     */
    public static int duplicateCount(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        Set<String> seen = new LinkedHashSet<>();
        int duplicates = 0;
        for (Entry entry : entries) {
            if (!seen.add(entry.duplicateKey())) {
                duplicates++;
            }
        }
        return duplicates;
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
        String site = tag(tags, "Site", "");
        String date = tag(tags, "Date", "");
        String round = tag(tags, "Round", "");
        String white = tag(tags, "White", "?");
        String black = tag(tags, "Black", "?");
        String result = game.getResult() == null || game.getResult().isBlank()
                ? tag(tags, "Result", "*")
                : game.getResult();
        String eco = tag(tags, "ECO", "");
        String opening = tag(tags, "Opening", "");
        MainlineInfo line = mainlineInfo(game);
        String title = index + ". " + event + SEPARATOR + white + " vs " + black;
        String detail = detail(tags, result);
        String searchable = (title + ' ' + detail + ' ' + line.sanLine() + ' ' + tags.values())
                .toLowerCase(Locale.ROOT);
        String duplicateKey = positionKey(line.startFen()) + '|' + line.sanLine().toLowerCase(Locale.ROOT)
                + '|' + result;
        return new Entry(index, game, title, detail, searchable, event, site, date, round, white, black,
                result, eco, opening, line.plyCount(), line.startFen(), line.finalFen(),
                line.positionKeys(), duplicateKey);
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
     * Builds mainline database facts by replaying SAN through the shared core.
     *
     * @param game parsed game
     * @return mainline metadata
     */
    private static MainlineInfo mainlineInfo(Game game) {
        Position start = game.getStartPosition() == null
                ? new Position(Game.STANDARD_START_FEN)
                : game.getStartPosition().copy();
        Position cursor = start.copy();
        LinkedHashSet<String> positionKeys = new LinkedHashSet<>();
        positionKeys.add(positionKey(cursor.toString()));
        StringBuilder sanLine = new StringBuilder();
        int ply = 0;
        Game.Node current = game.getMainline();
        while (current != null) {
            String san = current.getSan();
            if (san == null || san.isBlank()) {
                break;
            }
            try {
                short move = SAN.fromAlgebraic(cursor, san);
                if (!sanLine.isEmpty()) {
                    sanLine.append(' ');
                }
                sanLine.append(SAN.toAlgebraic(cursor, move));
                cursor.play(move);
                positionKeys.add(positionKey(cursor.toString()));
                ply++;
                current = current.getNext();
            } catch (IllegalArgumentException ex) {
                break;
            }
        }
        return new MainlineInfo(ply, start.toString(), cursor.toString(),
                List.copyOf(positionKeys), sanLine.toString());
    }

    /**
     * Returns a position key without move counters.
     *
     * @param fen source FEN
     * @return normalized position key
     */
    private static String positionKey(String fen) {
        if (fen == null || fen.isBlank()) {
            return "";
        }
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            return "";
        }
        return parts[0] + ' ' + parts[1] + ' ' + parts[2] + ' ' + parts[3];
    }

    /**
     * Mainline metadata extracted from a PGN game.
     *
     * @param plyCount mainline ply count
     * @param startFen start FEN
     * @param finalFen final mainline FEN
     * @param positionKeys reached position keys
     * @param sanLine canonical SAN mainline
     */
    private record MainlineInfo(int plyCount, String startFen, String finalFen,
            List<String> positionKeys, String sanLine) {
    }

    /**
     * One searchable PGN game entry.
     *
     * @param index one-based game index
     * @param game parsed game
     * @param title primary display text
     * @param detail secondary display text
     * @param searchable lowercased searchable text
     * @param event PGN event
     * @param site PGN site
     * @param date PGN date
     * @param round PGN round
     * @param white White player
     * @param black Black player
     * @param result result token
     * @param eco ECO code
     * @param opening opening name
     * @param plyCount mainline ply count
     * @param startFen start FEN
     * @param finalFen final FEN
     * @param positionKeys normalized positions reached by the mainline
     * @param duplicateKey exact duplicate key
     */
    public record Entry(
            int index,
            Game game,
            String title,
            String detail,
            String searchable,
            String event,
            String site,
            String date,
            String round,
            String white,
            String black,
            String result,
            String eco,
            String opening,
            int plyCount,
            String startFen,
            String finalFen,
            List<String> positionKeys,
            String duplicateKey) {

        /**
         * Creates a normalized immutable entry.
         *
         * @param index one-based index
         * @param game parsed game
         * @param title title text
         * @param detail detail text
         * @param searchable search text
         * @param event event tag
         * @param site site tag
         * @param date date tag
         * @param round round tag
         * @param white White player
         * @param black Black player
         * @param result result token
         * @param eco ECO tag
         * @param opening opening tag
         * @param plyCount mainline ply count
         * @param startFen start FEN
         * @param finalFen final FEN
         * @param positionKeys reached position keys
         * @param duplicateKey duplicate key
         */
        public Entry {
            positionKeys = positionKeys == null ? List.of() : List.copyOf(positionKeys);
            duplicateKey = duplicateKey == null || duplicateKey.isBlank()
                    ? index + ":" + title
                    : duplicateKey;
        }

        /**
         * Returns whether the mainline reaches a normalized position key.
         *
         * @param positionKey normalized position key
         * @return true when reached
         */
        public boolean reachesPositionKey(String positionKey) {
            return positionKey != null && positionKeys.contains(positionKey);
        }

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
