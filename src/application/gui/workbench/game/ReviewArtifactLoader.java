package application.gui.workbench.game;

import chess.review.ReviewRow;
import chess.review.StudyUnit;
import chess.schema.JsonParser;
import chess.schema.JsonValue;
import chess.schema.JsonValue.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Loads review and study JSONL artifacts into Workbench review rows.
 */
final class ReviewArtifactLoader {

    /**
     * Default text for rows without an available FEN after the played move.
     */
    private static final String UNKNOWN_AFTER_FEN = "";

    /**
     * Prevents instantiation.
     */
    private ReviewArtifactLoader() {
        // utility
    }

    /**
     * Loads review findings from produced review and optional study artifacts.
     *
     * @param reviewJsonl review JSONL path
     * @param studyJsonl optional study-unit JSONL path, or {@code null}
     * @return review findings
     * @throws IOException if an artifact cannot be read
     */
    static List<GameReviewPanel.ReviewFinding> load(Path reviewJsonl, Path studyJsonl)
            throws IOException {
        if (reviewJsonl == null) {
            return List.of();
        }
        List<JsonValue> reviewRows = jsonl(reviewJsonl);
        Map<String, JsonValue> studyRows = studyJsonl == null ? Map.of() : studyRowsById(jsonl(studyJsonl));
        List<GameReviewPanel.ReviewFinding> findings = new ArrayList<>();
        for (JsonValue review : reviewRows) {
            findings.add(finding(review, studyRows));
        }
        return List.copyOf(findings);
    }

    /**
     * Loads non-blank JSON values from a JSONL file.
     *
     * @param path JSONL path
     * @return parsed rows
     * @throws IOException if the file cannot be read
     */
    private static List<JsonValue> jsonl(Path path) throws IOException {
        List<JsonValue> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (!line.isBlank()) {
                rows.add(JsonParser.parse(line));
            }
        }
        return rows;
    }

    /**
     * Indexes study-unit rows by id.
     *
     * @param rows parsed study rows
     * @return id to row map
     */
    private static Map<String, JsonValue> studyRowsById(List<JsonValue> rows) {
        Map<String, JsonValue> byId = new HashMap<>();
        for (JsonValue row : rows) {
            if (!schema(row, StudyUnit.SCHEMA_VERSION)) {
                continue;
            }
            String id = string(row, "id");
            if (id != null) {
                byId.put(id, row);
            }
        }
        return byId;
    }

    /**
     * Converts one review artifact row into a panel finding.
     *
     * @param review review JSON value
     * @param studyRows study rows indexed by id
     * @return review finding
     */
    private static GameReviewPanel.ReviewFinding finding(JsonValue review, Map<String, JsonValue> studyRows) {
        if (!schema(review, ReviewRow.SCHEMA_VERSION)) {
            throw new IllegalArgumentException("unsupported review artifact schema: " + string(review,
                    "schemaVersion"));
        }
        String studyId = string(review, "study_unit_id");
        JsonValue study = studyId == null ? null : studyRows.get(studyId);
        int plyIndex = integer(review, "ply", 0);
        int displayPly = plyIndex + 1;
        String san = fallback(string(review, "played_san"), string(review, "played_uci"));
        String uci = fallback(string(review, "played_uci"), "");
        String beforeFen = fallback(string(review, "fen"), string(study, "parent_fen"));
        String afterFen = study == null ? UNKNOWN_AFTER_FEN : fallback(string(study, "position_fen"),
                UNKNOWN_AFTER_FEN);
        int beforeCp = evalCp(review, "eval_before");
        int afterCp = evalCp(review, "eval_after");
        String verdict = verdict(string(review, "mistake_category"));
        int loss = integer(review, "cp_loss", Math.max(0, beforeCp - afterCp));
        String effects = effects(review, study);
        String summary = summary(review, study, displayPly, san, uci, beforeCp, afterCp, loss, verdict, effects);
        return new GameReviewPanel.ReviewFinding(displayPly, san, uci, beforeFen, afterFen, verdict, loss,
                beforeCp, afterCp, effects, summary);
    }

    /**
     * Builds a panel explanation directly from artifact fields.
     *
     * @param review review row
     * @param study matching study row, or {@code null}
     * @param displayPly one-based display ply
     * @param san played SAN
     * @param uci played UCI
     * @param beforeCp score before the move
     * @param afterCp score after the move
     * @param loss centipawn loss
     * @param verdict verdict label
     * @param effects effect summary
     * @return explanation text
     */
    private static String summary(JsonValue review, JsonValue study, int displayPly, String san, String uci,
            int beforeCp, int afterCp, int loss, String verdict, String effects) {
        String side = capitalize(string(review, "color"));
        String bestSan = fallback(string(review, "best_san"), string(study, "best_san"));
        String bestUci = fallback(string(review, "best_uci"), string(study, "best_uci"));
        String studyId = fallback(string(review, "study_unit_id"), string(study, "id"));
        StringBuilder out = new StringBuilder(384)
                .append("Ply ").append(displayPly).append(' ')
                .append(side == null ? "Side to move" : side)
                .append(" played ").append(san).append(" (").append(uci).append("). Verdict: ")
                .append(verdict).append(". Engine score from mover's view: ")
                .append(signedCp(beforeCp)).append(" -> ").append(signedCp(afterCp))
                .append(loss > 0 ? ", " + loss + " cp lost." : ".");
        if (bestSan != null || bestUci != null) {
            out.append("\nBest: ").append(fallback(bestSan, "?"));
            if (bestUci != null) {
                out.append(" (").append(bestUci).append(')');
            }
        }
        out.append("\nEffects: ").append(effects);
        if (studyId != null) {
            out.append("\nStudy unit: ").append(studyId);
        }
        appendStudyDetails(out, study);
        appendRepro(out, review);
        return out.toString();
    }

    /**
     * Appends study-specific fields when a study unit is available.
     *
     * @param out summary destination
     * @param study matching study row
     */
    private static void appendStudyDetails(StringBuilder out, JsonValue study) {
        if (study == null) {
            return;
        }
        String difficulty = string(study, "difficulty");
        if (difficulty != null) {
            out.append("\nDifficulty: ").append(difficulty);
        }
        List<String> refutation = strings(study, "refutation_line");
        if (!refutation.isEmpty()) {
            out.append("\nRefutation: ").append(String.join(" ", refutation));
        }
    }

    /**
     * Appends reproducibility metadata from the review row.
     *
     * @param out summary destination
     * @param review review row
     */
    private static void appendRepro(StringBuilder out, JsonValue review) {
        JsonValue repro = child(review, "repro");
        if (repro == null) {
            return;
        }
        String engine = string(repro, "engine");
        if (engine == null) {
            return;
        }
        out.append("\nRepro: ").append(engine);
        Long nodes = longValue(repro, "max_nodes");
        Long duration = longValue(repro, "max_duration_ms");
        Long multipv = longValue(repro, "multipv");
        if (nodes != null) {
            out.append(", nodes ").append(nodes);
        }
        if (duration != null) {
            out.append(", duration ").append(duration).append("ms");
        }
        if (multipv != null) {
            out.append(", multipv ").append(multipv);
        }
    }

    /**
     * Summarizes tag delta and study tags.
     *
     * @param review review row
     * @param study matching study row, or {@code null}
     * @return compact effects text
     */
    private static String effects(JsonValue review, JsonValue study) {
        List<String> added = strings(child(review, "tags_delta"), "added");
        List<String> after = strings(review, "tags_after");
        List<String> studyTags = strings(study, "tags");
        StringJoiner joiner = new StringJoiner("; ");
        append(joiner, added, 3);
        if (joiner.length() == 0) {
            append(joiner, after, 3);
        }
        if (joiner.length() == 0) {
            append(joiner, studyTags, 3);
        }
        return joiner.length() == 0 ? "No tactical tag delta" : joiner.toString();
    }

    /**
     * Appends the first non-meta values from a list.
     *
     * @param joiner destination
     * @param values source values
     * @param limit maximum values
     */
    private static void append(StringJoiner joiner, List<String> values, int limit) {
        int added = 0;
        for (String value : values) {
            if (value == null || value.isBlank() || value.startsWith("META: ")) {
                continue;
            }
            joiner.add(value);
            added++;
            if (added >= limit) {
                return;
            }
        }
    }

    /**
     * Returns whether a JSON value has the expected schema version.
     *
     * @param value JSON value
     * @param expected expected schema version
     * @return true when schema matches
     */
    private static boolean schema(JsonValue value, String expected) {
        return expected.equals(string(value, "schemaVersion"));
    }

    /**
     * Reads a nullable object child.
     *
     * @param value object value
     * @param name field name
     * @return child value, or {@code null}
     */
    private static JsonValue child(JsonValue value, String name) {
        if (value == null || value.kind() != Kind.OBJECT) {
            return null;
        }
        return value.asObject().get(name);
    }

    /**
     * Reads a nullable string field.
     *
     * @param value object value
     * @param name field name
     * @return string value, or {@code null}
     */
    private static String string(JsonValue value, String name) {
        JsonValue child = child(value, name);
        return child != null && child.kind() == Kind.STRING ? child.asString() : null;
    }

    /**
     * Reads a nullable integer field.
     *
     * @param value object value
     * @param name field name
     * @return integer value, or {@code null}
     */
    private static Long longValue(JsonValue value, String name) {
        JsonValue child = child(value, name);
        if (child == null || child.kind() != Kind.NUMBER || !child.numberIsInteger()) {
            return null;
        }
        return Long.valueOf((long) child.asNumber());
    }

    /**
     * Reads an integer field with a default.
     *
     * @param value object value
     * @param name field name
     * @param fallback fallback value
     * @return integer value
     */
    private static int integer(JsonValue value, String name, int fallback) {
        Long parsed = longValue(value, name);
        return parsed == null ? fallback : parsed.intValue();
    }

    /**
     * Reads an eval child centipawn field.
     *
     * @param row review row
     * @param name eval field name
     * @return centipawn score, or zero
     */
    private static int evalCp(JsonValue row, String name) {
        JsonValue eval = child(row, name);
        return integer(eval, "cp", 0);
    }

    /**
     * Reads an array of string values.
     *
     * @param value object value
     * @param name field name
     * @return string values
     */
    private static List<String> strings(JsonValue value, String name) {
        JsonValue array = child(value, name);
        if (array == null || array.kind() != Kind.ARRAY) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonValue item : array.asArray()) {
            if (item.kind() == Kind.STRING) {
                values.add(item.asString());
            }
        }
        return List.copyOf(values);
    }

    /**
     * Returns the first non-blank value.
     *
     * @param first first candidate
     * @param second second candidate
     * @return selected value
     */
    private static String fallback(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    /**
     * Normalizes artifact verdict labels for the existing table.
     *
     * @param label artifact label
     * @return display label
     */
    private static String verdict(String label) {
        if (label == null || label.isBlank()) {
            return "OK";
        }
        String normalized = label.toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "ok" -> "OK";
            case "inaccuracy" -> "Inaccuracy";
            case "mistake" -> "Mistake";
            case "blunder" -> "Blunder";
            default -> capitalize(normalized);
        };
    }

    /**
     * Capitalizes a lower-case label.
     *
     * @param value source value
     * @return display label
     */
    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Formats a centipawn value with sign.
     *
     * @param cp centipawn score
     * @return signed text
     */
    private static String signedCp(int cp) {
        return (cp >= 0 ? "+" : "") + cp;
    }
}
