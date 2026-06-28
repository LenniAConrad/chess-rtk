package chess.study;

import chess.struct.Game;
import java.util.ArrayList;
import java.util.List;

/**
 * Lichess-compatible numeric annotation glyph catalog for studies.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class NagCatalog {

    /**
     * NAG category with uniqueness rules.
     */
    public enum Category {
        /**
         * Exactly one move assessment may be active.
         */
        MOVE_ASSESSMENT,

        /**
         * Exactly one position assessment may be active.
         */
        POSITION_ASSESSMENT,

        /**
         * Multiple observations may be active.
         */
        OBSERVATION
    }

    /**
     * One NAG entry.
     *
     * @param code numeric annotation glyph code
     * @param symbol display symbol
     * @param label display label
     * @param category uniqueness category
     */
    public record Entry(int code, String symbol, String label, Category category) {
    }

    /**
     * Full study NAG palette.
     */
    private static final List<Entry> ENTRIES = List.of(
            new Entry(1, "!", "Good move", Category.MOVE_ASSESSMENT),
            new Entry(2, "?", "Mistake", Category.MOVE_ASSESSMENT),
            new Entry(3, "!!", "Brilliant move", Category.MOVE_ASSESSMENT),
            new Entry(4, "??", "Blunder", Category.MOVE_ASSESSMENT),
            new Entry(5, "!?", "Interesting move", Category.MOVE_ASSESSMENT),
            new Entry(6, "?!", "Dubious move", Category.MOVE_ASSESSMENT),
            new Entry(7, "□", "Only move", Category.MOVE_ASSESSMENT),
            new Entry(22, "⨀", "Zugzwang", Category.MOVE_ASSESSMENT),
            new Entry(10, "=", "Equal position", Category.POSITION_ASSESSMENT),
            new Entry(13, "∞", "Unclear position", Category.POSITION_ASSESSMENT),
            new Entry(14, "⩲", "White is slightly better", Category.POSITION_ASSESSMENT),
            new Entry(15, "⩱", "Black is slightly better", Category.POSITION_ASSESSMENT),
            new Entry(16, "±", "White is better", Category.POSITION_ASSESSMENT),
            new Entry(17, "∓", "Black is better", Category.POSITION_ASSESSMENT),
            new Entry(18, "+−", "White is winning", Category.POSITION_ASSESSMENT),
            new Entry(19, "-+", "Black is winning", Category.POSITION_ASSESSMENT),
            new Entry(146, "N", "Novelty", Category.OBSERVATION),
            new Entry(32, "↑↑", "Development", Category.OBSERVATION),
            new Entry(36, "↑", "Initiative", Category.OBSERVATION),
            new Entry(40, "→", "Attack", Category.OBSERVATION),
            new Entry(132, "⇆", "Counterplay", Category.OBSERVATION),
            new Entry(138, "⊕", "Time trouble", Category.OBSERVATION),
            new Entry(44, "=∞", "With compensation", Category.OBSERVATION),
            new Entry(140, "∆", "With the idea", Category.OBSERVATION));

    /**
     * Prevents instantiation.
     */
    private NagCatalog() {
        // utility
    }

    /**
     * Returns entries in a category.
     *
     * @param category category
     * @return entries
     */
    public static List<Entry> entries(Category category) {
        return ENTRIES.stream().filter(entry -> entry.category() == category).toList();
    }

    /**
     * Returns one catalog entry.
     *
     * @param code NAG code
     * @return entry, or {@code null}
     */
    public static Entry entry(int code) {
        for (Entry entry : ENTRIES) {
            if (entry.code() == code) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Toggles a NAG on a node while enforcing uniqueness rules.
     *
     * @param node node to mutate
     * @param code NAG code
     * @return true when the code was added, false when removed
     */
    public static boolean toggle(Game.Node node, int code) {
        Entry entry = entry(code);
        if (node == null || entry == null) {
            return false;
        }
        if (node.getNags().contains(Integer.valueOf(code))) {
            node.removeNag(code);
            return false;
        }
        List<Integer> next = new ArrayList<>(node.getNags());
        if (entry.category() != Category.OBSERVATION) {
            next.removeIf(value -> {
                Entry existing = entry(value.intValue());
                return existing != null && existing.category() == entry.category();
            });
        }
        next.add(Integer.valueOf(code));
        node.setNags(next);
        return true;
    }

    /**
     * Formats active NAGs as display symbols.
     *
     * @param nags active NAG codes
     * @return symbol text
     */
    public static String symbols(List<Integer> nags) {
        if (nags == null || nags.isEmpty()) {
            return "";
        }
        List<String> symbols = new ArrayList<>();
        for (Integer nag : nags) {
            Entry entry = nag == null ? null : entry(nag.intValue());
            symbols.add(entry == null ? "$" + nag : entry.symbol());
        }
        return String.join(" ", symbols);
    }
}
