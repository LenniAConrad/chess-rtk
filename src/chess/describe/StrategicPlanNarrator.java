package chess.describe;

import static chess.describe.DescriptionLexicon.sentence;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic tag-to-plan narrator for classical position descriptions.
 *
 * <p>The narrator only phrases facts already present in
 * {@link PositionDescriptionInput#tags()}. It deliberately avoids naming
 * squares or new moves so the grounding verifier cannot confuse a board square
 * with an unvetted SAN move.</p>
 */
final class StrategicPlanNarrator {

    /**
     * Pawn-structure tag prefix.
     */
    private static final String PAWN_STRUCTURE_PREFIX = "FACT: pawn_structure type=";

    /**
     * Open-center tag.
     */
    private static final String CENTER_OPEN = "FACT: center_state=open";

    /**
     * Closed-center tag.
     */
    private static final String CENTER_CLOSED = "FACT: center_state=closed";

    /**
     * Space-advantage tag prefix.
     */
    private static final String SPACE_ADVANTAGE_PREFIX = "FACT: space_advantage=";

    /**
     * Side field used in structured tags.
     */
    private static final String SIDE_FIELD = " side=";

    /**
     * Maximum plan sentences for normal detail.
     */
    private static final int NORMAL_LIMIT = 1;

    /**
     * Maximum plan sentences for full detail.
     */
    private static final int FULL_LIMIT = 2;

    /**
     * Prevents instantiation.
     */
    private StrategicPlanNarrator() {
        // utility
    }

    /**
     * Builds deterministic why/plan sentences from extracted tags.
     *
     * @param input structured description input
     * @param full true when rendering full detail
     * @return plan sentences, already punctuated
     */
    static List<String> sentences(PositionDescriptionInput input, boolean full) {
        if (input == null || input.tags().isEmpty()) {
            return List.of();
        }
        String side = side(input);
        String opponent = opponent(side);
        List<String> out = new ArrayList<>();
        addPawnPlan(out, input.tags(), side, opponent);
        addCenterPlan(out, input.tags());
        addKingPlan(out, input.tags(), side, opponent);
        addSpacePlan(out, input.tags(), side, opponent);
        addPawnWeaknessPlan(out, input.tags(), side, opponent);
        return List.copyOf(out.subList(0, Math.min(out.size(), full ? FULL_LIMIT : NORMAL_LIMIT)));
    }

    /**
     * Adds a plan derived from passed-pawn tags.
     *
     * @param out sentence accumulator
     * @param tags extracted tags
     * @param side side to move, title-case
     * @param opponent opposing side, title-case
     */
    private static void addPawnPlan(List<String> out, List<String> tags, String side, String opponent) {
        String sideLower = lower(side);
        String opponentLower = lower(opponent);
        if (hasPawn(tags, "connected_passed", sideLower)) {
            out.add(sentence("Because " + side + " has connected passed pawns, the plan is to keep them moving "
                    + "together and supported"));
        } else if (hasPawn(tags, "passed", sideLower)) {
            out.add(sentence("Because " + side + " has a passed pawn, the plan is to push it only when it stays "
                    + "supported"));
        } else if (hasPawn(tags, "connected_passed", opponentLower)) {
            out.add(sentence(opponent + " has connected passed pawns, so " + side
                    + "'s plan starts with blockade and restraint"));
        } else if (hasPawn(tags, "passed", opponentLower)) {
            out.add(sentence(opponent + " has a passed pawn, so " + side
                    + "'s plan starts with blockade and restraint"));
        }
    }

    /**
     * Adds a plan derived from generic pawn-weakness tags.
     *
     * @param out sentence accumulator
     * @param tags extracted tags
     * @param side side to move, title-case
     * @param opponent opposing side, title-case
     */
    private static void addPawnWeaknessPlan(List<String> out, List<String> tags, String side, String opponent) {
        String sideLower = lower(side);
        String opponentLower = lower(opponent);
        if (hasPawnWeakness(tags, opponentLower)) {
            out.add(sentence(opponent + "'s pawn structure gives " + side + " a fixed target to pressure"));
        } else if (hasPawnWeakness(tags, sideLower)) {
            out.add(sentence(side + "'s pawn structure has a weakness, so the plan starts with keeping it defended"));
        }
    }

    /**
     * Adds a plan derived from king-safety tags.
     *
     * @param out sentence accumulator
     * @param tags extracted tags
     * @param side side to move, title-case
     * @param opponent opposing side, title-case
     */
    private static void addKingPlan(List<String> out, List<String> tags, String side, String opponent) {
        String sideLower = lower(side);
        String opponentLower = lower(opponent);
        if (hasTag(tags, opponentLower + " king exposed")) {
            out.add(sentence("Because the " + opponent + " king is exposed, " + side
                    + " should keep forcing play central to the plan"));
        } else if (hasTag(tags, sideLower + " king exposed")) {
            out.add(sentence("Because the " + side + " king is exposed, the plan starts with king safety before "
                    + "slower gains"));
        } else if (hasTag(tags, "open file near " + opponentLower + " king")) {
            out.add(sentence("The open file near the " + opponent + " king gives " + side
                    + " a clear direction for piece activity"));
        }
    }

    /**
     * Adds a plan derived from space-advantage tags.
     *
     * @param out sentence accumulator
     * @param tags extracted tags
     * @param side side to move, title-case
     * @param opponent opposing side, title-case
     */
    private static void addSpacePlan(List<String> out, List<String> tags, String side, String opponent) {
        String sideLower = lower(side);
        String opponentLower = lower(opponent);
        if (hasTag(tags, SPACE_ADVANTAGE_PREFIX + sideLower)) {
            out.add(sentence("Because " + side + " has the space advantage, the plan is to improve pieces without "
                    + "releasing the grip"));
        } else if (hasTag(tags, SPACE_ADVANTAGE_PREFIX + opponentLower)) {
            out.add(sentence(opponent + " has the space advantage, so " + side
                    + " needs to free space before chasing slow gains"));
        }
    }

    /**
     * Adds a plan derived from center-state tags.
     *
     * @param out sentence accumulator
     * @param tags extracted tags
     */
    private static void addCenterPlan(List<String> out, List<String> tags) {
        if (hasTag(tags, CENTER_OPEN)) {
            out.add(sentence("Because the center is open, piece activity is the plan and slow pawn play matters "
                    + "less"));
        } else if (hasTag(tags, CENTER_CLOSED)) {
            out.add(sentence("Because the center is closed, the plan is slower: improve pieces before opening "
                    + "lines"));
        }
    }

    /**
     * Tests whether a side has a tagged pawn weakness.
     *
     * @param tags extracted tags
     * @param side lower-case side
     * @return true when a weakness tag exists
     */
    private static boolean hasPawnWeakness(List<String> tags, String side) {
        return hasPawn(tags, "backward", side) || hasPawn(tags, "isolated", side) || hasPawn(tags, "doubled", side);
    }

    /**
     * Tests whether a side has a pawn-structure tag of a given type.
     *
     * @param tags extracted tags
     * @param type pawn-structure type
     * @param side lower-case side
     * @return true when the tag exists
     */
    private static boolean hasPawn(List<String> tags, String type, String side) {
        String prefix = PAWN_STRUCTURE_PREFIX + type + SIDE_FIELD + side;
        for (String tag : tags) {
            if (tag.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether a tag is present.
     *
     * @param tags extracted tags
     * @param wanted tag text
     * @return true when present
     */
    private static boolean hasTag(List<String> tags, String wanted) {
        return tags.contains(wanted);
    }

    /**
     * Returns the title-case side-to-move label.
     *
     * @param input structured input
     * @return side label
     */
    private static String side(PositionDescriptionInput input) {
        return "white".equals(input.sideToMove()) ? "White" : "Black";
    }

    /**
     * Returns the opposing side label.
     *
     * @param side title-case side
     * @return opposing side
     */
    private static String opponent(String side) {
        return "White".equals(side) ? "Black" : "White";
    }

    /**
     * Lowercases a title-case side label.
     *
     * @param side side label
     * @return lower-case side
     */
    private static String lower(String side) {
        return "White".equals(side) ? "white" : "black";
    }
}
