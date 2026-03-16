package chess.tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import utility.Json;

/**
 * Computes tag deltas (added/removed/changed).
 */
public final class TagDelta {

    public static final class Change {
        public final String key;
        public final String from;
        public final String to;

        private Change(String key, String from, String to) {
            this.key = key;
            this.from = from;
            this.to = to;
        }
    }

    private final List<String> added;
    private final List<String> removed;
    private final List<Change> changed;

    private TagDelta(List<String> added, List<String> removed, List<Change> changed) {
        this.added = added;
        this.removed = removed;
        this.changed = changed;
    }

    public static TagDelta diff(List<String> before, List<String> after) {
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

        added = TagSort.sort(added);
        removed = TagSort.sort(removed);
        changed.sort((a, b) -> a.key.compareTo(b.key));

        return new TagDelta(added, removed, changed);
    }

    public List<String> added() {
        return added;
    }

    public List<String> removed() {
        return removed;
    }

    public List<Change> changed() {
        return changed;
    }

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && changed.isEmpty();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(256).append('{');
        appendField(sb, "added", Json.stringArray(added.toArray(new String[0])));
        appendField(sb, "removed", Json.stringArray(removed.toArray(new String[0])));
        appendField(sb, "changed", changedJson());
        return sb.append('}').toString();
    }

    private String changedJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i <  changed.size(); i++) {
            Change c = changed.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{')
                    .append("\"key\":\"").append(Json.esc(c.key)).append("\"")
                    .append(",\"from\":\"").append(Json.esc(c.from)).append("\"")
                    .append(",\"to\":\"").append(Json.esc(c.to)).append("\"")
                    .append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String name, String value) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":").append(value);
    }

    private static Map<String, String> toIdentityMap(List<String> tags) {
        Map<String, String> map = new HashMap<>();
        if (tags == null) {
            return map;
        }
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            TagLine line = new TagLine(tag);
            String identity = TagIdentity.identity(line);
            map.put(identity, line.raw);
        }
        return map;
    }
}
