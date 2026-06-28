package application.gui.workbench.dataset;

import java.nio.file.Path;
import java.util.List;

/**
 * Immutable analysis result for one dataset source.
 *
 * @param source analyzed path
 * @param scannedFiles number of files visited
 * @param rows non-empty dataset rows inspected
 * @param validPositions rows with a parseable chess position
 * @param invalidRows rows without a usable position
 * @param duplicatePositions valid rows whose normalized FEN was already seen
 * @param whiteToMove valid rows with White to move
 * @param blackToMove valid rows with Black to move
 * @param inCheck valid rows where the side to move is in check
 * @param checkmates valid rows that are checkmates
 * @param stalemates valid rows that are stalemates
 * @param withTags rows carrying tags or labels
 * @param withEval rows carrying parseable engine scores
 * @param minMaterial minimum non-king material in centipawns
 * @param maxMaterial maximum non-king material in centipawns
 * @param averageMaterial average non-king material in centipawns
 * @param materialBuckets distribution of non-king material
 * @param evalBuckets distribution of score samples
 * @param topTags most common tags
 * @param topEngines most common engine names
 * @param samples representative valid rows
 * @param issues representative invalid or duplicate rows
 * @param truncated true when a row limit stopped the scan early
 * @param note human-readable scan note
 */
public record DatasetSummary(
        Path source,
        int scannedFiles,
        long rows,
        long validPositions,
        long invalidRows,
        long duplicatePositions,
        long whiteToMove,
        long blackToMove,
        long inCheck,
        long checkmates,
        long stalemates,
        long withTags,
        long withEval,
        int minMaterial,
        int maxMaterial,
        double averageMaterial,
        int[] materialBuckets,
        int[] evalBuckets,
        List<NamedCount> topTags,
        List<NamedCount> topEngines,
        List<SampleRow> samples,
        List<SampleRow> issues,
        boolean truncated,
        String note) {

    /**
     * Number of bars used by the material distribution chart.
     */
    public static final int MATERIAL_BUCKET_COUNT = 8;

    /**
     * Number of bars used by the evaluation distribution chart.
     */
    public static final int EVAL_BUCKET_COUNT = 7;

    /**
     * Normalizes mutable inputs into immutable values.
     *
     * @param source analyzed path
     * @param scannedFiles number of files visited
     * @param rows non-empty dataset rows inspected
     * @param validPositions rows with a parseable chess position
     * @param invalidRows rows without a usable position
     * @param duplicatePositions repeated normalized positions
     * @param whiteToMove valid rows with White to move
     * @param blackToMove valid rows with Black to move
     * @param inCheck valid rows where the side to move is in check
     * @param checkmates valid rows that are checkmates
     * @param stalemates valid rows that are stalemates
     * @param withTags rows carrying tags or labels
     * @param withEval rows carrying parseable engine scores
     * @param minMaterial minimum non-king material in centipawns
     * @param maxMaterial maximum non-king material in centipawns
     * @param averageMaterial average non-king material in centipawns
     * @param materialBuckets distribution of non-king material
     * @param evalBuckets distribution of score samples
     * @param topTags most common tags
     * @param topEngines most common engine names
     * @param samples representative valid rows
     * @param issues representative invalid or duplicate rows
     * @param truncated true when a row limit stopped the scan early
     * @param note human-readable scan note
     */
    public DatasetSummary {
        materialBuckets = materialBuckets == null ? new int[MATERIAL_BUCKET_COUNT] : materialBuckets.clone();
        evalBuckets = evalBuckets == null ? new int[EVAL_BUCKET_COUNT] : evalBuckets.clone();
        topTags = topTags == null ? List.of() : List.copyOf(topTags);
        topEngines = topEngines == null ? List.of() : List.copyOf(topEngines);
        samples = samples == null ? List.of() : List.copyOf(samples);
        issues = issues == null ? List.of() : List.copyOf(issues);
        note = note == null ? "" : note;
    }

    /**
     * Creates an empty summary for the initial panel state.
     *
     * @return empty summary
     */
    public static DatasetSummary empty() {
        return new DatasetSummary(null, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0, 0, 0.0d, new int[MATERIAL_BUCKET_COUNT], new int[EVAL_BUCKET_COUNT],
                List.of(), List.of(), List.of(), List.of(), false, "No dataset loaded");
    }

    /**
     * Returns the valid-row ratio.
     *
     * @return ratio in {@code 0..1}
     */
    public double validRatio() {
        return rows <= 0L ? 0.0d : (double) validPositions / (double) rows;
    }

    /**
     * Returns the duplicate-row ratio among valid rows.
     *
     * @return ratio in {@code 0..1}
     */
    public double duplicateRatio() {
        return validPositions <= 0L ? 0.0d : (double) duplicatePositions / (double) validPositions;
    }

    /**
     * Returns a defensive copy of the material distribution.
     *
     * @return material buckets
     */
    @Override
    public int[] materialBuckets() {
        return materialBuckets.clone();
    }

    /**
     * Returns a defensive copy of the evaluation distribution.
     *
     * @return evaluation buckets
     */
    @Override
    public int[] evalBuckets() {
        return evalBuckets.clone();
    }

    /**
     * Named count used by tag and engine frequency charts.
     *
     * @param name display label
     * @param count observed count
     */
    public record NamedCount(String name, long count) {
        /**
         * Normalizes null labels.
         *
         * @param name display label
         * @param count observed count
         */
        public NamedCount {
            name = name == null || name.isBlank() ? "unknown" : name.trim();
        }
    }

    /**
     * Preview row used by the sample and issue tables.
     *
     * @param file source file name
     * @param line line number or synthetic object index
     * @param kind detected row kind
     * @param fen normalized FEN, or raw row text when invalid
     * @param side side to move
     * @param material non-king material in centipawns
     * @param label compact tags or score label
     * @param issue issue text, blank for normal samples
     */
    public record SampleRow(
            String file,
            long line,
            String kind,
            String fen,
            String side,
            int material,
            String label,
            String issue) {

        /**
         * Normalizes nullable table values.
         *
         * @param file source file name
         * @param line line number or synthetic object index
         * @param kind detected row kind
         * @param fen normalized FEN, or raw row text when invalid
         * @param side side to move
         * @param material non-king material in centipawns
         * @param label compact tags or score label
         * @param issue issue text, blank for normal samples
         */
        public SampleRow {
            file = file == null ? "" : file;
            kind = kind == null ? "" : kind;
            fen = fen == null ? "" : fen;
            side = side == null ? "" : side;
            label = label == null ? "" : label;
            issue = issue == null ? "" : issue;
        }
    }
}
