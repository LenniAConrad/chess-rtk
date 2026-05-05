package chess.tag;

import static chess.tag.core.Literals.*;

/**
 * Builds stable identity keys for parsed tag lines.
 * <p>
 * The identity is used to match semantically equivalent tag entries across
 * snapshots even when the raw text changes.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
final class Identity {

    /**
     * Prevents instantiation of this utility class.
     */
    private Identity() {
        // utility
    }

    /**
     * Computes the identity key for a parsed tag line.
     * <p>
     * The identity is derived from the tag family and the most specific fields
     * that distinguish one logical tag from another.
     * </p>
     *
     * @param line the parsed tag line to identify
     * @return the stable identity key for the line, or an empty string when the
     *         input is {@code null}
     */
    static String identity(Line line) {
        if (line == null) {
            return EMPTY;
        }
        String fam = line.family;
        switch (fam) {
            case META:
                return META + COLON + firstKey(line);
            case FACT:
                return FACT + COLON + firstKey(line);
            case MATERIAL:
                return materialIdentity(line);
            case MOVE_FAMILY:
                return MOVE_FAMILY + COLON + firstKey(line);
            case PIECE:
                return pieceIdentity(line);
            case PAWN_FAMILY:
                return pawnIdentity(line);
            case KING:
                return KING + COLON + firstKey(line) + COLON + line.fields.get(SIDE);
            case TACTIC:
                return tacticIdentity(line);
            case CHECKMATE:
                return checkmateIdentity(line);
            case THREAT:
                return THREAT + COLON + line.fields.get(TYPE) + COLON + line.fields.get(SIDE);
            case CAND:
                return CAND + COLON + line.fields.getOrDefault(ROLE, firstKey(line));
            case PV:
                return PV;
            case SPACE, INITIATIVE, DEVELOPMENT, MOBILITY:
                return fam;
            case OUTPOST:
                return OUTPOST + COLON + line.fields.get(SIDE) + COLON + line.fields.get(SQUARE) + COLON
                        + line.fields.get(PIECE_KEY);
            case ENDGAME:
                return firstFieldIdentity(ENDGAME, line);
            case OPENING:
                return fam + COLON + firstKey(line);
            default:
                return line.raw;
        }
    }

    /**
     * Builds the identity for a checkmate tag line.
     * <p>
     * Named patterns include the pattern value because several pattern tags may
     * coexist. Single-value mate attributes keep identity by field key so deltas
     * report a changed delivery/winner/defender instead of remove/add churn.
     * </p>
     *
     * @param line the checkmate tag line to identify
     * @return the normalized checkmate identity
     */
    private static String checkmateIdentity(Line line) {
        String key = firstKey(line);
        if (PATTERN.equals(key)) {
            return CHECKMATE + COLON + PATTERN + COLON + line.fields.get(PATTERN);
        }
        return CHECKMATE + COLON + key;
    }

    /**
     * Builds the identity for a material tag line.
     * <p>
     * When the canonical fields are present, the identity is normalized to the
     * piece-count form so that equivalent material descriptions compare equal.
     * </p>
     *
     * @param line the material tag line to identify
     * @return the normalized material identity
     */
    private static String materialIdentity(Line line) {
        if (hasFields(line, PIECE_KEY, COUNT, SIDE)) {
            return MATERIAL + COLON + PIECE_COUNT + COLON + line.fields.get(SIDE) + COLON + line.fields.get(PIECE_KEY);
        }
        if (line.fields.containsKey(IMBALANCE)) {
            return MATERIAL + COLON + IMBALANCE + COLON + line.fields.get(IMBALANCE);
        }
        return MATERIAL + COLON + firstKey(line);
    }

    /**
     * Builds an identity from the first field key and value.
     * <p>
     * This is used for families that can emit several entries with the same key
     * but different enum values.
     * </p>
     *
     * @param family the tag family
     * @param line the parsed tag line
     * @return the identity using the first field key and value
     */
    private static String firstFieldIdentity(String family, Line line) {
        String key = firstKey(line);
        if (key.isEmpty()) {
            return family + COLON;
        }
        return family + COLON + key + COLON + line.fields.get(key);
    }

    /**
     * Builds the identity for a piece tag line.
     * <p>
     * Different piece subtypes use different field combinations so that the
     * resulting identity tracks the exact semantic variant.
     * </p>
     *
     * @param line the piece tag line to identify
     * @return the normalized piece identity
     */
    private static String pieceIdentity(Line line) {
        if (line.fields.containsKey(TIER)) {
            return PIECE + COLON + TIER + COLON + line.fields.get(SIDE) + COLON + line.fields.get(PIECE_KEY) + COLON
                    + line.fields.get(SQUARE);
        }
        if (line.fields.containsKey(EXTREME)) {
            return PIECE + COLON + EXTREME + COLON + line.fields.get(EXTREME);
        }
        if (line.fields.containsKey(ACTIVITY)) {
            return PIECE + COLON + ACTIVITY + COLON + line.fields.get(ACTIVITY) + COLON + line.fields.get(SIDE)
                    + COLON + line.fields.get(PIECE_KEY) + COLON + line.fields.get(SQUARE);
        }
        return PIECE + COLON + firstKey(line);
    }

    /**
     * Builds the identity for a pawn-family tag line.
     * <p>
     * Pawn tags often represent structural concepts, so the identity favors the
     * structural fields when available.
     * </p>
     *
     * @param line the pawn-family tag line to identify
     * @return the normalized pawn-family identity
     */
    private static String pawnIdentity(Line line) {
        if (line.fields.containsKey(STRUCTURE)) {
            return pawnStructureIdentity(line, line.fields.get(STRUCTURE));
        }
        if (line.fields.containsKey(ISLANDS)) {
            return PAWN_FAMILY + COLON + ISLANDS + COLON + line.fields.get(SIDE);
        }
        if (line.fields.containsKey(MAJORITY)) {
            return PAWN_FAMILY + COLON + MAJORITY + COLON + line.fields.get(SIDE);
        }
        return PAWN_FAMILY + COLON + firstKey(line);
    }

    /**
     * Builds the identity for a pawn-structure tag line.
     * <p>
     * The distinguishing suffix depends on the structure type so that connected
     * passed pawns and doubled pawns remain unique and comparable.
     * </p>
     *
     * @param line the pawn-structure tag line to identify
     * @param structure the structure subtype to encode in the identity
     * @return the normalized pawn-structure identity
     */
    private static String pawnStructureIdentity(Line line, String structure) {
        String suffix = line.fields.get(SQUARE);
        if (CONNECTED_PASSED.equals(structure)) {
            suffix = line.fields.get(SQUARES);
        } else if (DOUBLED.equals(structure)) {
            suffix = line.fields.get(FILE);
        }
        return PAWN_FAMILY + COLON + STRUCTURE + COLON + structure + COLON + line.fields.get(SIDE) + COLON + suffix;
    }

    /**
     * Builds the identity for a tactical tag line.
     * <p>
     * Tactical tags are keyed primarily by motif, then optionally by detail or
     * side when those fields are present.
     * </p>
     *
     * @param line the tactical tag line to identify
     * @return the normalized tactical identity
     */
    private static String tacticIdentity(Line line) {
        if (line.fields.containsKey(MOVE)) {
            return TACTIC + COLON + line.fields.get(MOTIF) + COLON + line.fields.get(MOVE)
                    + tacticMoveSuffix(line);
        }
        if (line.fields.containsKey(DETAIL)) {
            return TACTIC + COLON + line.fields.get(MOTIF) + COLON + line.fields.get(DETAIL);
        }
        if (line.fields.containsKey(SIDE)) {
            return TACTIC + COLON + line.fields.get(MOTIF) + COLON + line.fields.get(SIDE);
        }
        return TACTIC + COLON + line.fields.get(MOTIF);
    }

    /**
     * Builds a distinguishing suffix for move-specific tactical tags.
     *
     * @param line the tactical tag line
     * @return a suffix that distinguishes multiple tags for the same move
     */
    private static String tacticMoveSuffix(Line line) {
        if (line.fields.containsKey("target")) {
            return COLON + line.fields.get("target");
        }
        if (line.fields.containsKey("front") || line.fields.containsKey("behind")) {
            return COLON + line.fields.get("front") + COLON + line.fields.get("behind");
        }
        if (line.fields.containsKey("targets")) {
            return COLON + line.fields.get("targets");
        }
        if (line.fields.containsKey(SQUARE)) {
            return COLON + line.fields.get(SQUARE);
        }
        return EMPTY;
    }

    /**
     * Checks whether a line contains all of the requested fields.
     *
     * @param line the line to inspect
     * @param keys the field names that must all be present
     * @return {@code true} when every requested field exists in the line
     */
    private static boolean hasFields(Line line, String... keys) {
        for (String key : keys) {
            if (!line.fields.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the first field name in the parsed line, or an empty string when
     * no fields are present.
     *
     * @param line the parsed tag line to inspect
     * @return the first field key, or an empty string if the line has no fields
     */
    private static String firstKey(Line line) {
        if (line.fields.isEmpty()) {
            return EMPTY;
        }
        return line.fields.keySet().iterator().next();
    }
}
