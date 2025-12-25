package chess.struct;

import chess.core.Position;
import chess.uci.Analysis;
import utility.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Used for representing a compact, dependency-free JSON record that stores
 * engine analysis tied to a chess position.
 *
 * <pre>
 * JSON shape (single object):
 * {
 *   "created": 1711171067923,
 *   "engine": "Stockfish 16",
 *   "parent": "r1k5/2p4p/2p5/3p4/1Q4P1/1P3P2/PR3R1q/1K6 b - - 0 1",
 *   "position": "r1k5/2p4p/2p5/3p4/1Q4P1/1P3P2/PR3R2/1K5q w - - 1 34",
 *   "description": "mate in 3 from puzzle set X",
 *   "tags": ["tactic","mate"],
 *   "analysis": ["info depth 1 ...", "info depth 2 ..."]
 * }
 *
 * </pre>
 *
 * Used for storing multiple records as an array: [ { ... }, { ... } ].
 *
 * <p>
 * Used for avoiding external JSON libraries by relying on tolerant parsing for
 * this fixed shape.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public class Record {

    /**
     * Epoch millis when this record was created.
     */
    private long created;

    /**
     * Engine name/version used, e.g. "Stockfish 16".
     */
    private String engine;

    /**
     * Parent FEN (the position analyzed came from this FEN).
     */
    private Position parent;

    /**
     * Analyzed FEN.
     */
    private Position position;

    /**
     * Description of the position or the analysis (free form, may be empty).
     */
    private String description;

    /**
     * Optional free-form tags.
     */
    private String[] tags;

    /**
     * Aggregated engine output grid.
     */
    private Analysis analysis;

    /**
     * Used for creating a new evaluation record with current time, empty tags,
     * empty analysis,
     * and empty description.
     */
    public Record() {
        this.created = System.currentTimeMillis();
        this.engine = null;
        this.parent = null;
        this.position = null;
        this.description = "";
        this.tags = new String[0];
        this.analysis = new Analysis();
    }

    /**
     * Used for constructing an evaluation record from explicit values.
     *
     * @param created     epoch milliseconds when the record was created
     * @param engine      engine name and version (e.g., "Stockfish 16")
     * @param parent      parent FEN from which the analyzed position originated
     * @param position    analyzed position as a {@link chess.core.Position}
     * @param description free-form description of the position/analysis; null → ""
     * @param tags        optional tag array; null → empty array
     * @param analysis    engine analysis lines; null → empty
     *                    {@link chess.uci.Analysis}
     */
    public Record(long created, String engine, Position parent, Position position,
            String description, String[] tags, Analysis analysis) {
        this.created = created;
        this.engine = engine;
        this.parent = parent;
        this.position = position;
        this.description = description != null ? description : "";
        this.tags = tags != null ? tags : new String[0];
        this.analysis = analysis != null ? analysis : new Analysis();
    }

    /**
     * Used for creating a deep copy of this evaluation record.
     *
     * @return cloned {@code Record} with independent field copies
     */
    public Record copyOf() {
        return new Record(
                created,
                engine,
                parent != null ? parent.copyOf() : null,
                position != null ? position.copyOf() : null,
                description != null ? description : "",
                Arrays.copyOf(tags, tags.length),
                analysis != null ? analysis.copyOf() : new Analysis());
    }

    /**
     * Used for returning the creation timestamp of this record in epoch
     * milliseconds (UTC).
     * <p>
     * This corresponds to the {@code created} field serialized to JSON and is set
     * to
     * {@link System#currentTimeMillis()} in the no-arg constructor, or to the
     * parsed value when loading from JSON.
     *
     * @return epoch milliseconds when this record was created
     * @since 2025
     */
    public long getCreated() {
        return created;
    }

    /**
     * Used for returning the engine name and version that produced the analysis
     * payload (e.g., {@code "Stockfish 16"}).
     * <p>
     * May be {@code null} if the engine was not specified.
     *
     * @return engine identifier, or {@code null} if unknown
     * @since 2025
     */
    public String getEngine() {
        return engine;
    }

    /**
     * Used for returning the parent FEN as a {@link chess.core.Position},
     * indicating the origin of the analyzed position.
     * <p>
     * May be {@code null} if this record represents a root position with no parent.
     *
     * @return parent position, or {@code null} if none
     * @since 2025
     */
    public Position getParent() {
        return parent;
    }

    /**
     * Used for returning the analyzed position as a {@link chess.core.Position}.
     * <p>
     * May be {@code null} if not set (e.g., for partially constructed records).
     *
     * @return analyzed position, or {@code null} if not set
     * @since 2025
     */
    public Position getPosition() {
        return position;
    }

    /**
     * Used for returning the description; never {@code null} (empty string if not
     * set).
     *
     * @return non-null description string (possibly empty)
     */
    public String getDescription() {
        return description != null ? description : "";
    }

    /**
     * Used for setting the description; {@code null} is coerced to an empty string.
     *
     * @param description free-form description; {@code null} → empty string
     */
    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    /**
     * Used for fluently setting the description on this record.
     *
     * @param description free-form description; {@code null} → empty string
     * @return this record
     */
    public Record withDescription(String description) {
        setDescription(description);
        return this;
    }

    /**
     * Used for setting the engine identifier (name/version) for this record.
     * Coerces {@code null} to an empty string.
     *
     * @param engine engine name/version; {@code null} → empty string
     */
    public void setEngine(String engine) {
        this.engine = engine != null ? engine : "";
    }

    /**
     * Used for fluently setting the engine identifier for this record.
     *
     * @param engine engine name/version; {@code null} → empty string
     * @return this record
     */
    public Record withEngine(String engine) {
        setEngine(engine);
        return this;
    }

    /**
     * Used for setting the creation timestamp in epoch milliseconds (UTC).
     *
     * @param created epoch milliseconds when this record was created
     */
    public void setCreated(long created) {
        this.created = created;
    }

    /**
     * Used for fluently setting the creation timestamp in epoch milliseconds (UTC).
     *
     * @param created epoch milliseconds when this record was created
     * @return this record
     */
    public Record withCreated(long created) {
        setCreated(created);
        return this;
    }

    /**
     * Used for replacing the parent position of this record.
     * <p>
     * {@code null} clears the parent (e.g., when this record is a root).
     *
     * @param p new parent position; {@code null} clears it
     * @since 2025
     */
    public void setParent(Position p) {
        this.parent = p;
    }

    /**
     * Used for fluently replacing the parent position of this record.
     * <p>
     * {@code null} clears the parent (e.g., when this record is a root).
     *
     * @param p new parent position; {@code null} clears it
     * @return this record
     * @since 2025
     */
    public Record withParent(Position p) {
        setParent(p);
        return this;
    }

    /**
     * Used for replacing the analyzed position of this record.
     * <p>
     * {@code null} clears the analyzed position (for partially constructed
     * records).
     *
     * @param p new analyzed position; {@code null} clears it
     * @since 2025
     */
    public void setPosition(Position p) {
        this.position = p;
    }

    /**
     * Used for fluently replacing the analyzed position of this record.
     * <p>
     * {@code null} clears the analyzed position.
     *
     * @param p new analyzed position; {@code null} clears it
     * @return this record
     * @since 2025
     */
    public Record withPosition(Position p) {
        setPosition(p);
        return this;
    }

    /**
     * Used for returning a defensive copy of the tag list.
     * <p>
     * The internal {@code tags} array is never exposed directly; callers can modify
     * the returned array
     * without affecting this record’s state. When no tags are present, this returns
     * an empty array (never {@code null}).
     *
     * @return a new array containing this record’s tags (possibly empty)
     */
    public String[] getTags() {
        return Arrays.copyOf(tags, tags.length);
    }

    /**
     * Used for returning the aggregated engine analysis payload.
     * <p>
     * Guaranteed non-null: constructors initialize an empty analysis when none is
     * provided, and fluent setters
     * coerce {@code null} to an empty instance.
     *
     * @return analysis container (never {@code null}; may be empty)
     */
    public Analysis getAnalysis() {
        return analysis;
    }

    /**
     * Used for replacing the analysis payload while keeping this instance.
     *
     * @param a analysis to set; if null, an empty analysis is used
     * @return this record for chaining
     */
    public Record withAnalysis(Analysis a) {
        this.analysis = a != null ? a : new Analysis();
        return this;
    }

    /**
     * Used for adding non-null, non-empty tags and deduplicating existing ones.
     *
     * @param more tags to add; null or empty input is ignored
     * @return this record for chaining
     */
    public Record addTags(String... more) {
        if (more == null || more.length == 0)
            return this;
        List<String> merged = new ArrayList<>(Arrays.asList(this.tags));
        for (String t : more) {
            if (t != null && !t.isEmpty() && !merged.contains(t))
                merged.add(t);
        }
        this.tags = merged.toArray(new String[0]);
        return this;
    }

    /**
     * Used for serializing this record to compact JSON compatible with
     * {@link #fromJson(String)}.
     *
     * @return compact JSON object string
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        appendNumberField(sb, "created", created);
        sb.append(',');
        appendStringField(sb, "engine", engine);
        sb.append(',');
        appendStringField(sb, "parent", parent != null ? parent.toString() : null);
        sb.append(',');
        appendStringField(sb, "position", position != null ? position.toString() : null);
        sb.append(',');
        appendStringField(sb, "description", description);
        sb.append(',');
        appendRawField(sb, "tags", tags != null ? Json.stringArray(tags) : "[]");
        sb.append(',');
        appendRawField(sb, "analysis", analysis != null ? analysis.toString() : "[]");
        sb.append('}');
        return sb.toString();
    }

    private static void appendNumberField(StringBuilder sb, String name, long value) {
        sb.append('"').append(name).append('"').append(':').append(value);
    }

    /**
     * Appends a JSON string field with escaping, e.g. {@code "name":"value"}.
     *
     * @param sb    target builder
     * @param name  JSON field name
     * @param value raw value to escape; may be {@code null}
     */
    private static void appendStringField(StringBuilder sb, String name, String value) {
        sb.append('"').append(name).append('"').append(':').append('"').append(Json.esc(value)).append('"');
    }

    /**
     * Appends a JSON field whose value is already formatted JSON (array/object).
     *
     * @param sb      target builder
     * @param name    JSON field name
     * @param rawJson preformatted JSON payload (e.g., array string)
     */
    private static void appendRawField(StringBuilder sb, String name, String rawJson) {
        sb.append('"').append(name).append('"').append(':').append(rawJson);
    }

    /**
     * Used for serializing a list of records into a JSON array compatible with
     * {@link #fromJsonArray(String)}.
     *
     * @param nodes records to serialize; null or empty produces {@code []}
     * @return JSON array string
     */
    public static String toJsonArray(List<Record> nodes) {
        StringBuilder sb = new StringBuilder(Math.max(16, (nodes != null ? nodes.size() : 0) * 256));
        sb.append('[');
        if (nodes != null && !nodes.isEmpty()) {
            for (int i = 0; i < nodes.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(nodes.get(i).toJson());
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Used for parsing a single record from JSON produced by {@link #toJson()}.
     * Parsing is tolerant for this fixed shape.
     *
     * @param json JSON object string
     * @return parsed {@code Record}, or {@code null} if input is invalid
     */
    public static Record fromJson(String json) {
        if (json == null) {
            return null;
        }

        String s = json.trim();

        if (s.isEmpty() || s.charAt(0) != '{') {
            return null;
        }

        long created = Json.parseLongField(s, "created");
        String engine = Json.parseStringField(s, "engine");

        Position parent = null;
        try {
            String parentFen = Json.parseStringField(s, "parent");
            if (parentFen != null && !parentFen.equals("null")) {
                parent = new Position(parentFen);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }

        Position position = null;
        try {
            String posFen = Json.parseStringField(s, "position");
            if (posFen != null && !posFen.equals("null")) {
                position = new Position(posFen);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }

        String description = Json.parseStringField(s, "description");
        if (description == null)
            description = "";

        String[] tags = Json.parseStringArrayField(s, "tags");
        String[] lines = Json.parseStringArrayField(s, "analysis");

        Analysis a = new Analysis();
        if (lines.length > 0) {
            a.addAll(lines);
        }

        return new Record(created, engine, parent, position, description, tags, a);
    }

    /**
     * Used for returning the compact JSON representation of this record.
     *
     * @return JSON string
     */
    @Override
    public String toString() {
        return toJson();
    }

    /**
     * Used for testing equality based on key fields.
     *
     * @param o object to compare with
     * @return {@code true} if objects are equal; otherwise {@code false}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Record))
            return false;
        Record that = (Record) o;
        return created == that.created &&
                Objects.equals(engine, that.engine) &&
                Objects.equals(parent, that.parent) &&
                Objects.equals(position, that.position) &&
                Objects.equals(getDescription(), that.getDescription()) &&
                Arrays.equals(tags, that.tags);
    }

    /**
     * Used for computing a hash code consistent with {@link #equals(Object)}.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        int result = Objects.hash(created, engine, parent, position, getDescription());
        result = 31 * result + Arrays.hashCode(tags);
        return result;
    }

}
