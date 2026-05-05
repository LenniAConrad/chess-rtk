package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds canonical tag strings and collects tag output.
 * <p>
 * The builder preserves field insertion order, uppercases family names, and
 * quotes field values that need escaping. It is intentionally small so detector
 * code can use it without pulling in a broader tag object model.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Emitter {

    /**
     * Mutable tag sink.
     */
    private final List<String> tags;

    /**
     * Creates an emitter with default capacity.
     */
    public Emitter() {
        this(32);
    }

    /**
     * Creates an emitter with a caller-selected capacity.
     *
     * @param capacity expected number of tags
     */
    Emitter(int capacity) {
        tags = new ArrayList<>(Math.max(1, capacity));
    }

    /**
     * Starts a canonical tag builder for a family.
     *
     * @param family tag family name
     * @return a field builder for the tag
     */
    public static Builder tag(String family) {
        return new Builder(family);
    }

    /**
     * Adds a raw tag line if it is non-blank.
     *
     * @param tag tag line to add
     * @return this emitter
     */
    public Emitter add(String tag) {
        if (tag != null) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return this;
    }

    /**
     * Builds and adds one canonical tag.
     *
     * @param family tag family
     * @param keyValues alternating field keys and values
     * @return this emitter
     */
    public Emitter add(String family, String... keyValues) {
        Builder builder = tag(family);
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must contain key/value pairs");
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            builder.field(keyValues[i], keyValues[i + 1]);
        }
        return add(builder.build());
    }

    /**
     * Returns a sorted, deduplicated tag snapshot.
     *
     * @return canonical tag list
     */
    public List<String> sorted() {
        return Sort.sort(tags);
    }

    /**
     * Returns an immutable snapshot in insertion order.
     *
     * @return insertion-order tag list
     */
    public List<String> snapshot() {
        return List.copyOf(tags);
    }

    /**
     * Returns the mutable sink for detector orchestration.
     *
     * @return mutable tag list
     */
    List<String> sink() {
        return tags;
    }

    /**
     * Builder for one canonical tag.
     */
    public static final class Builder {

        /**
         * Canonical family name.
         */
        private final String family;

        /**
         * Field buffer.
         */
        private final StringBuilder fields = new StringBuilder();

        /**
         * Creates a builder for one family.
         *
         * @param family tag family name
         */
        private Builder(String family) {
            String value = Objects.requireNonNull(family, "family").trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("family must not be blank");
            }
            this.family = value.toUpperCase(Locale.ROOT);
        }

        /**
         * Appends a field when the supplied value is non-null.
         *
         * @param key field key
         * @param value field value
         * @return this builder
         */
        public Builder field(String key, Object value) {
            if (value == null) {
                return this;
            }
            String cleanKey = cleanKey(key);
            if (!fields.isEmpty()) {
                fields.append(SPACE_TEXT);
            }
            fields.append(cleanKey).append(EQUAL_SIGN).append(formatValue(String.valueOf(value)));
            return this;
        }

        /**
         * Emits the built tag into a target list.
         *
         * @param target target tag list
         */
        public void emit(List<String> target) {
            Objects.requireNonNull(target, "target").add(build());
        }

        /**
         * Builds the canonical tag string.
         *
         * @return tag string
         */
        public String build() {
            if (fields.isEmpty()) {
                return family + COLON;
            }
            return family + COLON_SPACE + fields;
        }

        /**
         * Returns the built tag string.
         *
         * @return tag string
         */
        @Override
        public String toString() {
            return build();
        }

        /**
         * Validates and normalizes a field key.
         *
         * @param key raw field key
         * @return normalized key
         */
        private static String cleanKey(String key) {
            String value = Objects.requireNonNull(key, "key").trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("field key must not be blank");
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isWhitespace(c) || c == EQUAL_SIGN) {
                    throw new IllegalArgumentException("invalid tag field key: " + key);
                }
            }
            return value;
        }

        /**
         * Formats a field value for the canonical tag grammar.
         *
         * @param value raw value
         * @return escaped field value
         */
        private static String formatValue(String value) {
            if (!needsQuotes(value)) {
                return value;
            }
            String escaped = value.replace(String.valueOf(BACKSLASH), ESCAPED_BACKSLASH)
                    .replace(String.valueOf(QUOTE), ESCAPED_QUOTE);
            return QUOTE + escaped + QUOTE;
        }

        /**
         * Checks whether a value needs quote escaping.
         *
         * @param value raw value
         * @return true when quotes are required
         */
        private static boolean needsQuotes(String value) {
            if (value.isEmpty()) {
                return true;
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isWhitespace(c) || c == QUOTE || c == BACKSLASH) {
                    return true;
                }
            }
            return false;
        }
    }
}
