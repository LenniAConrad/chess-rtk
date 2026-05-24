package application.gui.workbench.dataset;

import application.cli.RecordIO;
import chess.core.Position;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import utility.JsonFields;

/**
 * Fast, line-oriented dataset scanner used by the Workbench Datasets tab.
 */
public final class DatasetAnalyzer {

    /**
     * Maximum representative valid rows kept in memory.
     */
    private static final int MAX_SAMPLE_ROWS = 80;

    /**
     * Maximum representative issue rows kept in memory.
     */
    private static final int MAX_ISSUE_ROWS = 80;

    /**
     * Top frequency rows shown for tag and engine charts.
     */
    private static final int TOP_FREQUENCY_LIMIT = 10;

    /**
     * Large text buffer used for dataset scans.
     */
    private static final int TEXT_BUFFER_SIZE = 1 << 20;

    /**
     * Upper score bound used by the eval histogram.
     */
    private static final int EVAL_CP_LIMIT = 900;

    /**
     * Common dataset file extensions accepted during directory scans.
     */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "fen", "fens", "txt", "csv", "tsv", "json", "jsonl", "record", "records", "pgn");

    /**
     * UCI-style score matcher used inside analysis lines.
     */
    private static final Pattern SCORE_PATTERN = Pattern.compile("score\\s+(cp|mate)\\s+(-?\\d+)");

    /**
     * Prevents instantiation.
     */
    private DatasetAnalyzer() {
        // utility
    }

    /**
     * Analyzes a dataset path.
     *
     * @param source file or directory to scan
     * @param rowLimit maximum non-empty rows to inspect
     * @return dataset summary
     * @throws IOException if a file cannot be read
     */
    public static DatasetSummary analyze(Path source, long rowLimit) throws IOException {
        if (source == null) {
            throw new IOException("No dataset path selected");
        }
        if (!Files.exists(source)) {
            throw new IOException("Dataset path does not exist: " + source);
        }
        MutableSummary summary = new MutableSummary(source, Math.max(1L, rowLimit));
        if (Files.isDirectory(source)) {
            scanDirectory(source, summary);
        } else if (Files.isRegularFile(source)) {
            scanFile(source, summary);
        } else {
            throw new IOException("Dataset path is neither a file nor a directory: " + source);
        }
        return summary.toSummary();
    }

    /**
     * Scans every supported file under a directory.
     *
     * @param source directory path
     * @param summary mutable summary
     * @throws IOException when traversal fails
     */
    private static void scanDirectory(Path source, MutableSummary summary) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(DatasetAnalyzer::supported)
                    .sorted()
                    .toList();
            for (Path file : files) {
                scanFile(file, summary);
                if (summary.truncated || Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        }
    }

    /**
     * Scans one file, including JSON arrays when detected.
     *
     * @param file file path
     * @param summary mutable summary
     * @throws IOException when reading fails
     */
    private static void scanFile(Path file, MutableSummary summary) throws IOException {
        summary.files++;
        if (RecordIO.isJsonArrayFile(file)) {
            scanJsonArray(file, summary);
            return;
        }
        try (InputStream in = Files.newInputStream(file);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8), TEXT_BUFFER_SIZE)) {
            String line;
            long lineNumber = 0L;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                scanRow(file, lineNumber, line, summary);
                if (summary.truncated) {
                    return;
                }
            }
        }
    }

    /**
     * Streams JSON objects from a top-level JSON array.
     *
     * @param file JSON array path
     * @param summary mutable summary
     * @throws IOException when reading fails
     */
    private static void scanJsonArray(Path file, MutableSummary summary) throws IOException {
        try {
            RecordIO.streamRecordJson(file, json -> {
                scanRowUnchecked(file, summary.rows + 1L, json, summary);
                if (summary.truncated || Thread.currentThread().isInterrupted()) {
                    throw new StopScan();
                }
            });
        } catch (StopScan stop) {
            if (Thread.currentThread().isInterrupted()) {
                InterruptedIOException interrupted = new InterruptedIOException("Dataset scan interrupted");
                interrupted.initCause(stop);
                throw interrupted;
            }
        }
    }

    /**
     * Scans one raw dataset row.
     *
     * @param file source file
     * @param line line number
     * @param raw raw row
     * @param summary mutable summary
     * @throws IOException when the scan has been interrupted
     */
    private static void scanRow(Path file, long line, String raw, MutableSummary summary) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Dataset scan interrupted");
        }
        scanRowUnchecked(file, line, raw, summary);
    }

    /**
     * Scans one row and converts checked failures into issue rows.
     *
     * @param file source file
     * @param line line number
     * @param raw raw row
     * @param summary mutable summary
     */
    private static void scanRowUnchecked(Path file, long line, String raw, MutableSummary summary) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        if (summary.rows >= summary.rowLimit) {
            summary.truncated = true;
            return;
        }
        summary.rows++;
        RowData row = rowData(trimmed);
        if (row.fen == null || row.fen.isBlank()) {
            summary.invalid(file, line, row.kind, compact(trimmed), "no FEN found");
            return;
        }
        try {
            Position position = new Position(row.fen);
            summary.valid(file, line, row, position);
        } catch (IllegalArgumentException ex) {
            summary.invalid(file, line, row.kind, compact(row.fen), compact(ex.getMessage()));
        }
    }

    /**
     * Extracts structured values from a dataset row.
     *
     * @param row row text
     * @return extracted row data
     */
    private static RowData rowData(String row) {
        if (row.startsWith("{")) {
            String fen = firstNonBlank(
                    JsonFields.stringField(row, "position"),
                    JsonFields.stringField(row, "fen"),
                    JsonFields.stringField(row, "FEN"),
                    JsonFields.stringField(row, "parent"),
                    extractFen(row));
            return new RowData(fen, "JSON", tags(row), engine(row), score(row));
        }
        String fen = extractFen(row);
        String kind = looksLikeFenRow(row, fen) ? "FEN" : "Text";
        return new RowData(fen, kind, List.of(), "", score(row));
    }

    /**
     * Reads tag-like labels from a JSON row.
     *
     * @param json JSON row
     * @return tag list
     */
    private static List<String> tags(String json) {
        List<String> tags = new ArrayList<>();
        for (String tag : JsonFields.stringArrayField(json, "tags")) {
            addTag(tags, tag);
        }
        addTag(tags, JsonFields.stringField(json, "tag"));
        addTag(tags, JsonFields.stringField(json, "label"));
        addTag(tags, JsonFields.stringField(json, "result"));
        return List.copyOf(tags);
    }

    /**
     * Adds a normalized tag to a list.
     *
     * @param tags destination list
     * @param value tag value
     */
    private static void addTag(List<String> tags, String value) {
        if (value != null && !value.isBlank()) {
            tags.add(value.trim());
        }
    }

    /**
     * Reads an engine name from a JSON row.
     *
     * @param json JSON row
     * @return engine name or blank
     */
    private static String engine(String json) {
        return firstNonBlank(JsonFields.stringField(json, "engine"), JsonFields.stringField(json, "engineName"), "");
    }

    /**
     * Reads an evaluation score from a JSON or text row.
     *
     * @param row row text
     * @return centipawn score or null
     */
    private static Integer score(String row) {
        Double numeric = firstNumeric(row, "evalCp", "centipawns", "cp", "eval", "score");
        if (numeric != null) {
            return (int) Math.round(numeric);
        }
        Integer scoreText = parseScoreText(JsonFields.stringField(row, "score"));
        if (scoreText != null) {
            return scoreText;
        }
        for (String line : JsonFields.stringArrayField(row, "analysis")) {
            Integer parsed = parseScoreText(line);
            if (parsed != null) {
                return parsed;
            }
        }
        return parseScoreText(row);
    }

    /**
     * Finds the first numeric JSON field among candidate names.
     *
     * @param json JSON text
     * @param names field names
     * @return numeric value or null
     */
    private static Double firstNumeric(String json, String... names) {
        for (String name : names) {
            double value = JsonFields.doubleField(json, name, Double.NaN);
            if (!Double.isNaN(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Parses UCI score text.
     *
     * @param text text containing a score
     * @return centipawn score or null
     */
    private static Integer parseScoreText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = SCORE_PATTERN.matcher(text);
        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group(2));
            if ("mate".equals(matcher.group(1))) {
                return value < 0 ? -EVAL_CP_LIMIT : EVAL_CP_LIMIT;
            }
            return value;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("#")) {
            return trimmed.startsWith("#-") ? -EVAL_CP_LIMIT : EVAL_CP_LIMIT;
        }
        return null;
    }

    /**
     * Extracts the first FEN-looking substring from a row.
     *
     * @param text source text
     * @return FEN or blank
     */
    private static String extractFen(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] tokens = text.trim().split("\\s+");
        for (int i = 0; i + 5 < tokens.length; i++) {
            if (isFenTokenWindow(tokens, i)) {
                return tokens[i] + ' ' + tokens[i + 1] + ' ' + tokens[i + 2] + ' '
                        + tokens[i + 3] + ' ' + tokens[i + 4] + ' ' + tokens[i + 5];
            }
        }
        return "";
    }

    /**
     * Returns whether six adjacent tokens form a FEN-like record.
     *
     * @param tokens source tokens
     * @param start first token index
     * @return true when the token window looks like FEN
     */
    private static boolean isFenTokenWindow(String[] tokens, int start) {
        return isBoardToken(tokens[start])
                && ("w".equalsIgnoreCase(tokens[start + 1]) || "b".equalsIgnoreCase(tokens[start + 1]))
                && isCastlingToken(tokens[start + 2])
                && isEnPassantToken(tokens[start + 3])
                && isNonNegativeInteger(tokens[start + 4])
                && isNonNegativeInteger(tokens[start + 5]);
    }

    /**
     * Returns whether a token looks like a FEN piece-placement field.
     *
     * @param token candidate token
     * @return true when the token has eight slash-separated ranks
     */
    private static boolean isBoardToken(String token) {
        int slashes = 0;
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (ch == '/') {
                slashes++;
            } else if ("pnbrqkPNBRQK12345678".indexOf(ch) < 0) {
                return false;
            }
        }
        return slashes == 7;
    }

    /**
     * Returns whether a token looks like a FEN castling field.
     *
     * @param token candidate token
     * @return true when castling rights are syntactically plausible
     */
    private static boolean isCastlingToken(String token) {
        return "-".equals(token) || token.matches("[KQkqA-Ha-h]+");
    }

    /**
     * Returns whether a token looks like a FEN en-passant field.
     *
     * @param token candidate token
     * @return true when the token is "-" or a third/sixth-rank square
     */
    private static boolean isEnPassantToken(String token) {
        return "-".equals(token) || token.matches("[a-h][36]");
    }

    /**
     * Returns whether a token is a non-negative base-10 integer.
     *
     * @param token candidate token
     * @return true when every character is a digit
     */
    private static boolean isNonNegativeInteger(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether the row itself appears to be a FEN line.
     *
     * @param row row text
     * @param fen extracted FEN
     * @return true when the row starts with the extracted FEN
     */
    private static boolean looksLikeFenRow(String row, String fen) {
        return fen != null && !fen.isBlank() && row.startsWith(fen.substring(0, Math.min(fen.length(), 8)));
    }

    /**
     * Returns whether a path has a supported dataset extension.
     *
     * @param path candidate path
     * @return true when supported
     */
    private static boolean supported(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the first non-blank value.
     *
     * @param values candidate values
     * @return first non-blank value or blank
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * Produces compact table text.
     *
     * @param text source text
     * @return compact text
     */
    private static String compact(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 140 ? normalized : normalized.substring(0, 137) + "...";
    }

    /**
     * Runtime exception used to stop JSON-array callbacks early.
     */
    private static final class StopScan extends RuntimeException {

        /**
         * Serialization identifier for exception compatibility.
         */
        private static final long serialVersionUID = 1L;
    }

    /**
     * Extracted values for one row.
     *
     * @param fen FEN text
     * @param kind detected row kind
     * @param tags normalized tags
     * @param engine engine label
     * @param score centipawn score
     */
    private record RowData(String fen, String kind, List<String> tags, String engine, Integer score) {
        /**
         * Normalizes nullable row fields.
         */
        RowData {
            fen = fen == null ? "" : fen;
            kind = kind == null ? "" : kind;
            tags = tags == null ? List.of() : List.copyOf(tags);
            engine = engine == null ? "" : engine;
        }
    }

    /**
     * Mutable accumulator for a scan.
     */
    private static final class MutableSummary {

        /**
         * Source path being scanned.
         */
        private final Path source;

        /**
         * Maximum rows to inspect.
         */
        private final long rowLimit;

        /**
         * Normalized FEN set used for duplicate detection.
         */
        private final Set<String> seen = new HashSet<>();

        /**
         * Tag frequency table.
         */
        private final Map<String, Long> tags = new HashMap<>();

        /**
         * Engine frequency table.
         */
        private final Map<String, Long> engines = new HashMap<>();

        /**
         * Representative valid rows.
         */
        private final List<DatasetSummary.SampleRow> samples = new ArrayList<>();

        /**
         * Representative issue rows.
         */
        private final List<DatasetSummary.SampleRow> issues = new ArrayList<>();

        /**
         * Material histogram.
         */
        private final int[] materialBuckets = new int[DatasetSummary.MATERIAL_BUCKET_COUNT];

        /**
         * Evaluation histogram.
         */
        private final int[] evalBuckets = new int[DatasetSummary.EVAL_BUCKET_COUNT];

        /**
         * Number of scanned files.
         */
        private int files;

        /**
         * Number of non-empty rows.
         */
        private long rows;

        /**
         * Number of valid positions.
         */
        private long valid;

        /**
         * Number of invalid rows.
         */
        private long invalid;

        /**
         * Number of duplicate positions.
         */
        private long duplicates;

        /**
         * Number of positions with White to move.
         */
        private long white;

        /**
         * Number of positions with Black to move.
         */
        private long black;

        /**
         * Number of positions where side to move is in check.
         */
        private long checks;

        /**
         * Number of checkmate rows.
         */
        private long mates;

        /**
         * Number of stalemate rows.
         */
        private long stalemates;

        /**
         * Number of rows with tags.
         */
        private long tagged;

        /**
         * Number of rows with scores.
         */
        private long scored;

        /**
         * Lowest observed material.
         */
        private int minMaterial = Integer.MAX_VALUE;

        /**
         * Highest observed material.
         */
        private int maxMaterial = Integer.MIN_VALUE;

        /**
         * Material sum for averaging.
         */
        private long materialSum;

        /**
         * Whether the scan stopped early.
         */
        private boolean truncated;

        /**
         * Creates an accumulator.
         *
         * @param source source path
         * @param rowLimit maximum rows
         */
        MutableSummary(Path source, long rowLimit) {
            this.source = source;
            this.rowLimit = rowLimit;
        }

        /**
         * Adds a valid row to the summary.
         *
         * @param file source file
         * @param line line number
         * @param row extracted row data
         * @param position parsed position
         */
        void valid(Path file, long line, RowData row, Position position) {
            valid++;
            String normalizedFen = position.toString();
            boolean duplicate = !seen.add(normalizedFen);
            if (duplicate) {
                duplicates++;
            }
            if (position.isWhiteToMove()) {
                white++;
            } else {
                black++;
            }
            if (position.inCheck()) {
                checks++;
            }
            if (position.isCheckmate()) {
                mates++;
            } else if (position.isStalemate()) {
                stalemates++;
            }
            int material = position.countTotalMaterial();
            observeMaterial(material);
            observeTags(row.tags);
            observeEngine(row.engine);
            observeScore(row.score);
            addSample(file, line, row.kind, normalizedFen, position.isWhiteToMove() ? "white" : "black",
                    material, label(row), duplicate ? "duplicate FEN" : "");
        }

        /**
         * Adds an invalid row to the summary.
         *
         * @param file source file
         * @param line line number
         * @param kind detected row kind
         * @param text row text
         * @param issue issue text
         */
        void invalid(Path file, long line, String kind, String text, String issue) {
            invalid++;
            addIssue(file, line, kind, text, "", 0, "", issue);
        }

        /**
         * Records material distribution.
         *
         * @param material non-king material
         */
        private void observeMaterial(int material) {
            minMaterial = Math.min(minMaterial, material);
            maxMaterial = Math.max(maxMaterial, material);
            materialSum += material;
            int bucket = Math.min(materialBuckets.length - 1, Math.max(0, material / 1000));
            materialBuckets[bucket]++;
        }

        /**
         * Records tag frequencies.
         *
         * @param values row tags
         */
        private void observeTags(List<String> values) {
            if (!values.isEmpty()) {
                tagged++;
            }
            for (String tag : values) {
                tags.merge(tag, 1L, Long::sum);
            }
        }

        /**
         * Records engine frequencies.
         *
         * @param value engine name
         */
        private void observeEngine(String value) {
            if (value != null && !value.isBlank()) {
                engines.merge(value.trim(), 1L, Long::sum);
            }
        }

        /**
         * Records score distribution.
         *
         * @param score centipawn score
         */
        private void observeScore(Integer score) {
            if (score == null) {
                return;
            }
            scored++;
            int clamped = Math.max(-EVAL_CP_LIMIT, Math.min(EVAL_CP_LIMIT, score));
            int bucket = (int) ((long) (clamped + EVAL_CP_LIMIT) * evalBuckets.length / (2L * EVAL_CP_LIMIT + 1L));
            evalBuckets[Math.min(evalBuckets.length - 1, Math.max(0, bucket))]++;
        }

        /**
         * Adds a valid-row sample when capacity remains.
         *
         * @param file source file
         * @param line line number
         * @param kind row kind
         * @param fen FEN text
         * @param side side to move
         * @param material material value
         * @param label compact label
         * @param issue optional issue
         */
        private void addSample(Path file, long line, String kind, String fen, String side,
                int material, String label, String issue) {
            DatasetSummary.SampleRow sample = sample(file, line, kind, fen, side, material, label, issue);
            if (samples.size() < MAX_SAMPLE_ROWS) {
                samples.add(sample);
            }
            if (!issue.isBlank() && issues.size() < MAX_ISSUE_ROWS) {
                issues.add(sample);
            }
        }

        /**
         * Adds an issue sample when capacity remains.
         *
         * @param file source file
         * @param line line number
         * @param kind row kind
         * @param text row text
         * @param side side to move
         * @param material material value
         * @param label compact label
         * @param issue issue text
         */
        private void addIssue(Path file, long line, String kind, String text, String side,
                int material, String label, String issue) {
            if (issues.size() < MAX_ISSUE_ROWS) {
                issues.add(sample(file, line, kind, text, side, material, label, issue));
            }
        }

        /**
         * Creates a sample row.
         *
         * @param file source file
         * @param line line number
         * @param kind row kind
         * @param fen FEN or row text
         * @param side side to move
         * @param material material value
         * @param label compact label
         * @param issue issue text
         * @return sample row
         */
        private DatasetSummary.SampleRow sample(Path file, long line, String kind, String fen, String side,
                int material, String label, String issue) {
            Path name = file == null ? null : file.getFileName();
            return new DatasetSummary.SampleRow(name == null ? "" : name.toString(), line, kind,
                    compact(fen), side, material, compact(label), compact(issue));
        }

        /**
         * Builds the immutable summary.
         *
         * @return immutable summary
         */
        DatasetSummary toSummary() {
            int safeMin = valid == 0L ? 0 : minMaterial;
            int safeMax = valid == 0L ? 0 : maxMaterial;
            double average = valid == 0L ? 0.0d : (double) materialSum / (double) valid;
            String note = truncated ? "Row limit reached; increase the limit for a full scan" : "Scan complete";
            return new DatasetSummary(source, files, rows, valid, invalid, duplicates, white, black,
                    checks, mates, stalemates, tagged, scored, safeMin, safeMax, average,
                    materialBuckets, evalBuckets, top(tags), top(engines), samples, issues, truncated, note);
        }

        /**
         * Formats a row label from tags and score.
         *
         * @param row row data
         * @return compact label
         */
        private static String label(RowData row) {
            List<String> parts = new ArrayList<>();
            if (row.score != null) {
                parts.add(row.score >= EVAL_CP_LIMIT ? "#+" : row.score <= -EVAL_CP_LIMIT ? "#-" : row.score + " cp");
            }
            if (!row.tags.isEmpty()) {
                parts.add(String.join(", ", row.tags.subList(0, Math.min(3, row.tags.size()))));
            }
            if (!row.engine.isBlank()) {
                parts.add(row.engine);
            }
            return String.join("  ", parts);
        }

        /**
         * Converts a frequency map to sorted top rows.
         *
         * @param values frequency map
         * @return sorted top rows
         */
        private static List<DatasetSummary.NamedCount> top(Map<String, Long> values) {
            return values.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                            .thenComparing(Map.Entry::getKey))
                    .limit(TOP_FREQUENCY_LIMIT)
                    .map(entry -> new DatasetSummary.NamedCount(entry.getKey(), entry.getValue()))
                    .toList();
        }
    }
}
