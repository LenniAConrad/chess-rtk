package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalizes and orders tag strings into a predictable canonical sequence.
 * <p>
 * Sorting is family-aware so related tag categories stay grouped in a stable
 * order before duplicate removal is applied.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Sort {

    /**
     * Defines the preferred family ordering used when sorting tags.
     */
    private static final List<String> FAMILY_ORDER = List.of(
            FACT,
            META,
            MOVE_FAMILY,
            THREAT,
            CAND,
            PV,
            IDEA,
            TACTIC,
            CHECKMATE,
            PIECE,
            KING,
            PAWN_FAMILY,
            MATERIAL,
            SPACE,
            INITIATIVE,
            DEVELOPMENT,
            MOBILITY,
            OUTPOST,
            ENDGAME,
            OPENING);

    /**
     * Prevents instantiation of this utility class.
     */
    private Sort() {
        // utility
    }

    /**
     * Cleans, sorts, and deduplicates a list of tag strings.
     * <p>
     * Null and blank entries are discarded. Remaining tags are ordered by
     * family priority and then lexicographically within the same family.
     * </p>
     *
     * @param tags the tag strings to normalize
     * @return an immutable list containing the sorted unique tags
     */
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

    /**
     * Computes the rank of the family that a tag belongs to.
     *
     * @param tag the tag string whose family should be ranked
     * @return the zero-based rank for known families, or a fallback rank for
     *         unknown families
     */
    private static int familyRank(String tag) {
        String family = familyOf(tag);
        int idx = FAMILY_ORDER.indexOf(family);
        return idx >= 0 ? idx : FAMILY_ORDER.size() + 1;
    }

    /**
     * Extracts the family portion of a tag string.
     * <p>
     * The family is the text before the first colon, normalized to uppercase so
     * comparisons are stable.
     * </p>
     *
     * @param tag the tag string to inspect
     * @return the normalized family name, or an empty string if none exists
     */
    private static String familyOf(String tag) {
        int idx = tag.indexOf(COLON);
        if (idx <= 0) {
            return EMPTY;
        }
        return tag.substring(0, idx).trim().toUpperCase();
    }
}
