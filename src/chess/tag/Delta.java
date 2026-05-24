package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import utility.Json;

/**
 * Computes and serializes the differences between two ordered sets of tag lines.
 * <p>
 * This type is used to compare two tag collections, classify entries as added,
 * removed, or changed, and emit the result as JSON for downstream processing.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Delta {

    /**
     * Describes a single tag entry whose identity stayed the same but whose raw
     * text changed between two snapshots.
 * @author Lennart A. Conrad
 * @since 2026
 */
    public static final class Change {

        /**
         * The identity key used to match the tag across snapshots.
         */
        public final String key;

        /**
         * The raw tag text from the earlier snapshot.
         */
        public final String from;

        /**
         * The raw tag text from the later snapshot.
         */
        public final String to;

        /**
         * Creates a change record for a tag entry.
         *
         * @param key the identity used to match the tag line
         * @param from the raw tag text before the change
         * @param to the raw tag text after the change
         */
        private Change(String key, String from, String to) {
            this.key = key;
            this.from = from;
            this.to = to;
        }
    }

    /**
     * The tag lines that exist only in the later snapshot.
     */
    private final List<String> added;

    /**
     * The tag lines that exist only in the earlier snapshot.
     */
    private final List<String> removed;

    /**
     * The tag lines whose identity stayed stable but whose contents changed.
     */
    private final List<Change> changed;

    /**
     * Creates a delta from the supplied buckets.
     *
     * @param added the lines that were introduced in the later snapshot
     * @param removed the lines that were removed from the earlier snapshot
     * @param changed the lines that kept identity but changed content
     */
    private Delta(List<String> added, List<String> removed, List<Change> changed) {
        this.added = added;
        this.removed = removed;
        this.changed = changed;
    }

    /**
     * Compares two tag lists and classifies the differences by identity.
     *
     * @param before the earlier tag list to compare from
     * @param after the later tag list to compare to
     * @return a delta describing added, removed, and changed entries
     */
    public static Delta diff(List<String> before, List<String> after) {
        Map<String, String> left = toIdentityMap(before);
        Map<String, String> right = toIdentityMap(after);

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<Change> changed = new ArrayList<>();

        for (Map.Entry<String, String> entry : right.entrySet()) {
            String key = entry.getKey();
            String newVal = entry.getValue();
            String oldVal = left.remove(key);
            if (oldVal == null) {
                added.add(newVal);
            } else if (!oldVal.equals(newVal)) {
                changed.add(new Change(key, oldVal, newVal));
            }
        }
        removed.addAll(left.values());

        added = Sort.sort(added);
        removed = Sort.sort(removed);
        changed.sort((a, b) -> a.key.compareTo(b.key));

        return new Delta(added, removed, changed);
    }

    /**
     * Returns the tag lines that were added in the later snapshot.
     *
     * @return the added tag lines
     */
    public List<String> added() {
        return added;
    }

    /**
     * Returns the tag lines that were removed from the earlier snapshot.
     *
     * @return the removed tag lines
     */
    public List<String> removed() {
        return removed;
    }

    /**
     * Returns the tag lines that changed content while keeping identity.
     *
     * @return the changed tag records
     */
    public List<Change> changed() {
        return changed;
    }

    /**
     * Checks whether the delta contains any additions, removals, or changes.
     *
     * @return {@code true} when no differences were detected
     */
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && changed.isEmpty();
    }

    /**
     * Serializes this delta to a compact JSON object.
     *
     * @return the JSON representation of this delta
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(256).append(OPEN_BRACE);
        appendField(sb, JSON_ADDED, Json.stringArray(added.toArray(new String[0])));
        appendField(sb, JSON_REMOVED, Json.stringArray(removed.toArray(new String[0])));
        appendField(sb, JSON_CHANGED, changedJson());
        return sb.append(CLOSE_BRACE).toString();
    }

    /**
     * Serializes the changed entries as a JSON array.
     *
     * @return the JSON array text for the changed entries
     */
    private String changedJson() {
        StringBuilder sb = new StringBuilder();
        sb.append(OPEN_BRACKET);
        for (int i = 0; i <  changed.size(); i++) {
            Change c = changed.get(i);
            if (i > 0) {
                sb.append(COMMA);
            }
            sb.append(OPEN_BRACE)
                    .append(QUOTE).append(JSON_KEY).append(JSON_STRING_SEPARATOR).append(Json.esc(c.key)).append(QUOTE)
                    .append(COMMA).append(QUOTE).append(JSON_FROM).append(JSON_STRING_SEPARATOR).append(Json.esc(c.from))
                    .append(QUOTE)
                    .append(COMMA).append(QUOTE).append(JSON_TO).append(JSON_STRING_SEPARATOR).append(Json.esc(c.to))
                    .append(QUOTE)
                    .append(CLOSE_BRACE);
        }
        sb.append(CLOSE_BRACKET);
        return sb.toString();
    }

    /**
     * Appends a named JSON field to the current object buffer.
     *
     * @param sb the buffer receiving the serialized field
     * @param name the field name
     * @param value the already serialized field value
     */
    private static void appendField(StringBuilder sb, String name, String value) {
        if (sb.length() > 1) {
            sb.append(COMMA);
        }
        sb.append(QUOTE).append(name).append(JSON_NAME_SEPARATOR).append(value);
    }

    /**
     * Converts a list of raw tag lines into a map keyed by their identity.
     *
     * @param tags the raw tag lines to normalize
     * @return a map from identity to raw tag text
     */
    private static Map<String, String> toIdentityMap(List<String> tags) {
        Map<String, String> map = new HashMap<>();
        if (tags == null) {
            return map;
        }
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            Line line = new Line(tag);
            String identity = Identity.identity(line);
            map.put(identity, line.raw);
        }
        return map;
    }
}
