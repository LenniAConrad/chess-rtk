package chess.tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical sorting for tag output.
 */
public final class TagSort {

    private static final List<String> FAMILY_ORDER = List.of(
            "FACT",
            "META",
            "THREAT",
            "CAND",
            "PV",
            "IDEA",
            "TACTIC",
            "PIECE",
            "KING",
            "PAWN",
            "MATERIAL",
            "SPACE",
            "INITIATIVE",
            "DEVELOPMENT",
            "MOBILITY",
            "OUTPOST",
            "ENDGAME",
            "OPENING");

    private TagSort() {
        // utility
    }

    public static List<String> sort(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>(tags.size());
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        cleaned.sort((a, b) -> {
            int famA = familyRank(a);
            int famB = familyRank(b);
            if (famA != famB) {
                return Integer.compare(famA, famB);
            }
            return a.compareTo(b);
        });
        Set<String> deduped = new LinkedHashSet<>(cleaned);
        return List.copyOf(deduped);
    }

    private static int familyRank(String tag) {
        String family = familyOf(tag);
        int idx = FAMILY_ORDER.indexOf(family);
        return idx >= 0 ? idx : FAMILY_ORDER.size() + 1;
    }

    private static String familyOf(String tag) {
        int idx = tag.indexOf(':');
        if (idx <= 0) {
            return "";
        }
        return tag.substring(0, idx).trim().toUpperCase();
    }
}
