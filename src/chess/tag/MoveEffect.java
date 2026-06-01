package chess.tag;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import chess.core.MoveInference;
import chess.core.Position;

/**
 * Move-transition tags: what a single move changed between a parent position and
 * the resulting child position.
 *
 * <p>This is the dynamic counterpart to {@link Generator}'s static position tags.
 * It is fully grounded and engine-free: it tags the parent and the child with the
 * existing static tagger, diffs the two tag sets with {@link Delta}, and reports
 * the tactical/threat/checkmate motifs the move <em>created</em> or
 * <em>removed</em>, alongside the move's own type. Every emitted fact is an
 * observed difference between two tagged positions plus the played move — never a
 * guess.</p>
 *
 * <p>Effect motifs carry their affected {@code side=} verbatim and are reported as
 * plain facts; whether a created motif helps the mover or the opponent is left to
 * the explanation layer, because motif side polarity is not uniform across motifs
 * (e.g. {@code fork side=} names the attacker, {@code hanging side=} names the
 * victim). Forward-looking {@code removes} are suppressed when the child is
 * terminal (checkmate), where vanished threats reflect the move executing them
 * rather than resolving them.</p>
 *
 * <p>Emitted family {@code MOVE_EFFECT:} (one tag per effect, each citing the move
 * in SAN so it is self-contained):</p>
 * <ul>
 *   <li>{@code MOVE_EFFECT: san=<san> type=checkmate|check|capture|quiet}</li>
 *   <li>{@code MOVE_EFFECT: san=<san> creates="<motif>[ side=<side>]"} — a
 *       tactical/threat/checkmate motif present in the child but not the parent.</li>
 *   <li>{@code MOVE_EFFECT: san=<san> removes="<motif>[ side=<side>]"} — one
 *       present in the parent but gone in the child (non-terminal children only).</li>
 * </ul>
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class MoveEffect {

    private static final String PREFIX = "MOVE_EFFECT: san=";

    private MoveEffect() {
        // utility
    }

    /**
     * Returns the grounded move-transition tags for the move connecting
     * {@code parent} to {@code child}, sorted and de-duplicated.
     *
     * @param parent the position before the move
     * @param child the position after the move
     * @return move-effect tags, or an empty list when either input is {@code null}
     */
    public static List<String> effects(Position parent, Position child) {
        if (parent == null || child == null) {
            return List.of();
        }
        MoveInference.Notation move = MoveInference.notation(parent, child);
        if (move == null || move.san() == null || move.san().isBlank()) {
            return List.of();
        }
        String san = move.san();

        List<String> parentTags = Generator.tags(parent);
        List<String> childTags = Generator.tags(child);
        Delta delta = Delta.diff(parentTags, childTags);

        TreeSet<String> out = new TreeSet<>();
        out.add(PREFIX + san + " type=" + moveType(san));

        for (String tag : delta.added()) {
            String effect = effectValue(tag);
            if (effect != null) {
                out.add(PREFIX + san + " creates=\"" + effect + "\"");
            }
        }
        if (!child.isCheckmate()) {
            for (String tag : delta.removed()) {
                String effect = effectValue(tag);
                if (effect != null) {
                    out.add(PREFIX + san + " removes=\"" + effect + "\"");
                }
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Classifies the move's own effect from its SAN, which canonically encodes
     * checkmate ({@code #}), check ({@code +}), and capture ({@code x}).
     *
     * @param san the move in standard algebraic notation
     * @return {@code checkmate}, {@code check}, {@code capture}, or {@code quiet}
     */
    private static String moveType(String san) {
        if (san.indexOf('#') >= 0) {
            return "checkmate";
        }
        if (san.indexOf('+') >= 0) {
            return "check";
        }
        if (san.indexOf('x') >= 0) {
            return "capture";
        }
        return "quiet";
    }

    /**
     * Builds the {@code creates=}/{@code removes=} value for a tactical, threat,
     * or checkmate tag: its motif descriptor plus the affected side when present.
     * Returns {@code null} for tags of other families.
     *
     * @param tag a canonical tag string
     * @return e.g. {@code fork side=white} or {@code back_rank_mate}, or {@code null}
     */
    private static String effectValue(String tag) {
        String motif = motifOf(tag);
        if (motif == null) {
            return null;
        }
        String side = sideOf(tag);
        return side == null ? motif : motif + " side=" + side;
    }

    /**
     * Extracts the descriptive motif token from a tactical/threat/checkmate tag,
     * or {@code null} when the tag is not one of those families.
     *
     * @param tag a canonical tag string
     * @return the motif descriptor (e.g. {@code fork}, {@code back_rank_mate},
     *     {@code mate}), or {@code null}
     */
    private static String motifOf(String tag) {
        if (tag.startsWith("TACTIC: ")) {
            return fieldValue(tag, "motif=");
        }
        if (tag.startsWith("CHECKMATE: ")) {
            return fieldValue(tag, "pattern=");
        }
        if (tag.startsWith("THREAT: ")) {
            return fieldValue(tag, "type=");
        }
        return null;
    }

    /**
     * Reads the {@code side=} value of a tag, or {@code null} when absent.
     *
     * @param tag a canonical tag string
     * @return {@code white}, {@code black}, or {@code null}
     */
    private static String sideOf(String tag) {
        return fieldValue(tag, "side=");
    }

    /**
     * Returns the unquoted token following {@code key} in {@code tag}, terminated
     * by the next space, or {@code null} when the key is absent.
     *
     * @param tag the tag string to read
     * @param key the field key including its trailing {@code =}
     * @return the field value, or {@code null}
     */
    private static String fieldValue(String tag, String key) {
        int i = tag.indexOf(key);
        if (i < 0) {
            return null;
        }
        int start = i + key.length();
        int end = tag.indexOf(' ', start);
        if (end < 0) {
            end = tag.length();
        }
        return tag.substring(start, end);
    }
}
