package chess.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import chess.core.Position;
import chess.nn.classifier.Encoder;
import chess.struct.Record;
import chess.uci.Analysis;
import chess.uci.Filter;
import utility.Json;

/**
 * Exports {@code .record} dumps into tensors for the classifier model.
 *
 * <p>
 * The export is deliberately classifier-specific: inputs are the same
 * side-to-move-oriented {@code 21 x 8 x 8} planes used by
 * {@link chess.nn.classifier.Model}, and labels are binary float targets
 * for one-logit binary classification training.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ClassifierDatasetExporter {

    /**
     * Number of flattened input floats per sample.
     */
    private static final int INPUTS = Encoder.TOTAL_CHANNELS * 64;

    /**
     * Large text buffer for record JSONL inputs.
     */
    private static final int TEXT_BUFFER_SIZE = 1 << 20;

    /**
     * Default uncapped class count.
     */
    public static final long NO_CLASS_CAP = Long.MAX_VALUE;

    /**
     * Shared closing fragment for top-level metadata sections.
     */
    private static final String METADATA_SECTION_END = "  },\n";

    /**
     * Utility class; prevent instantiation.
     */
    private ClassifierDatasetExporter() {
        // utility
    }

    /**
     * Export options for classifier datasets.
     *
     * @param rowFilter optional filter selecting rows before labeling
     * @param rowFilterDsl original row-filter DSL for metadata
     * @param labelFilter optional filter defining positive labels
     * @param labelFilterDsl original label-filter DSL for metadata
     * @param fallbackLabelFilter optional fallback positive-label filter used when
     *                            kind is absent
     * @param fallbackLabelFilterDsl fallback filter DSL for metadata
     * @param maxPositives maximum positive samples to write
     * @param maxNegatives maximum negative samples to write
     */
    public record Options(
        /**
         * Stores the row filter.
         */
        Filter rowFilter,
        /**
         * Stores the row filter dsl.
         */
        String rowFilterDsl,
        /**
         * Stores the label filter.
         */
        Filter labelFilter,
        /**
         * Stores the label filter dsl.
         */
        String labelFilterDsl,
        /**
         * Stores the fallback label filter.
         */
        Filter fallbackLabelFilter,
        /**
         * Stores the fallback label filter dsl.
         */
        String fallbackLabelFilterDsl,
        /**
         * Stores the max positives.
         */
        long maxPositives,
        /**
         * Stores the max negatives.
         */
        long maxNegatives
    ) {

        /**
         * Creates a normalized option set.
         */
        public Options {
            if (maxPositives < 0) {
                throw new IllegalArgumentException("maxPositives must be non-negative");
            }
            if (maxNegatives < 0) {
                throw new IllegalArgumentException("maxNegatives must be non-negative");
            }
        }
    }

    /**
     * Export counters.
     *
     * @param seen input objects seen
     * @param rowsWritten rows written
     * @param positives positive labels written
     * @param negatives negative labels written
     * @param skippedInvalid records that failed to parse
     * @param skippedMissingPosition records without a usable position
     * @param skippedRowFilter records rejected by row filter
     * @param skippedUnlabeled records without a usable binary label
     * @param skippedClassCap records skipped because the class cap was reached
     */
    public record Summary(
        /**
         * Stores the seen.
         */
        long seen,
        /**
         * Stores the rows written.
         */
        long rowsWritten,
        /**
         * Stores the positives.
         */
        long positives,
        /**
         * Stores the negatives.
         */
        long negatives,
        /**
         * Stores the skipped invalid.
         */
        long skippedInvalid,
        /**
         * Stores the skipped missing position.
         */
        long skippedMissingPosition,
        /**
         * Stores the skipped row filter.
         */
        long skippedRowFilter,
        /**
         * Stores the skipped unlabeled.
         */
        long skippedUnlabeled,
        /**
         * Stores the skipped class cap.
         */
        long skippedClassCap
    ) {
    }

    /**
     * Progress emitted after an input file has been fully consumed.
     *
     * @param file completed input file
     * @param completedFiles number of files completed so far
     * @param totalFiles total input files scheduled
     * @param summary cumulative export counters after this file
     */
    public record FileProgress(
        /**
         * Stores the file.
         */
        Path file,
        /**
         * Stores the completed files.
         */
        int completedFiles,
        /**
         * Stores the total files.
         */
        int totalFiles,
        /**
         * Stores the summary.
         */
        Summary summary
    ) {
    }

    /**
     * Exports one or more record files to classifier tensors.
     *
     * @param recordFiles input record files
     * @param outStem output stem
     * @param options export options
     * @return export counters
     * @throws IOException if reading or writing fails
     */
    public static Summary export(List<Path> recordFiles, Path outStem, Options options) throws IOException {
        return export(recordFiles, outStem, options, null, null);
    }

    /**
     * Exports one or more record files to classifier tensors and reports file-level
     * progress.
     *
     * @param recordFiles input record files
     * @param outStem output stem
     * @param options export options
     * @param fileProgress optional callback invoked after each fully consumed file
     * @return export counters
     * @throws IOException if reading or writing fails
     */
    public static Summary export(
            List<Path> recordFiles,
            Path outStem,
            Options options,
            Consumer<FileProgress> fileProgress) throws IOException {
        return export(recordFiles, outStem, options, fileProgress, null);
    }

    /**
     * Exports one or more record files to classifier tensors and reports progress.
     *
     * @param recordFiles input record files
     * @param outStem output stem
     * @param options export options
     * @param fileProgress optional callback invoked after each fully consumed file
     * @param byteProgress optional callback receiving cumulative bytes read across
     *                     all input files
     * @return export counters
     * @throws IOException if reading or writing fails
     */
    public static Summary export(
            List<Path> recordFiles,
            Path outStem,
            Options options,
            Consumer<FileProgress> fileProgress,
            LongConsumer byteProgress) throws IOException {
        Objects.requireNonNull(recordFiles, "recordFiles");
        Objects.requireNonNull(outStem, "outStem");
        Objects.requireNonNull(options, "options");
        if (recordFiles.isEmpty()) {
            throw new IllegalArgumentException("recordFiles must not be empty");
        }

        Path inputsPath = sibling(outStem, ".classifier.inputs.npy");
        Path labelsPath = sibling(outStem, ".classifier.labels.npy");
        Path metaPath = sibling(outStem, ".classifier.meta.json");

        MutableSummary stats = new MutableSummary();
        boolean success = false;
        try (NpyFloat32Writer inputsWriter = NpyFloat32Writer.open2D(inputsPath, INPUTS);
                NpyFloat32Writer labelsWriter = NpyFloat32Writer.open1D(labelsPath)) {
            long byteBase = 0L;
            for (int i = 0; i < recordFiles.size(); i++) {
                Path input = recordFiles.get(i);
                streamRecordJson(input, objJson -> {
                    try {
                        exportObject(objJson, options, inputsWriter, labelsWriter, stats);
                        if (stats.classCapsReached(options)) {
                            throw new StopExport();
                        }
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }, addBase(byteProgress, byteBase));
                byteBase += fileSize(input);
                notifyFileProgress(fileProgress, input, i + 1, recordFiles.size(), stats);
            }
            success = true;
        } catch (StopExport stop) {
            success = true;
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        } finally {
            if (!success) {
                Files.deleteIfExists(inputsPath);
                Files.deleteIfExists(labelsPath);
                Files.deleteIfExists(metaPath);
            }
        }

        Summary summary = stats.toSummary();
        writeMetadata(metaPath, recordFiles, inputsPath, labelsPath, summary, options);
        return summary;
    }

    /**
     * Invokes the optional file-level progress callback.
     */
    private static void notifyFileProgress(
            Consumer<FileProgress> fileProgress,
            Path file,
            int completedFiles,
            int totalFiles,
            MutableSummary stats) {
        if (fileProgress != null) {
            fileProgress.accept(new FileProgress(file, completedFiles, totalFiles, stats.toSummary()));
        }
    }

    /**
     * Exports one JSON record object if it has a position and label.
     */
    private static void exportObject(
            String objJson,
            Options options,
            NpyFloat32Writer inputsWriter,
            NpyFloat32Writer labelsWriter,
            MutableSummary stats) throws IOException {
        stats.seen++;

        Record rec;
        try {
            rec = Record.fromJson(objJson);
        } catch (Exception ex) {
            stats.skippedInvalid++;
            return;
        }
        if (rec == null) {
            stats.skippedInvalid++;
            return;
        }

        Position position = rec.getPosition();
        if (position == null) {
            stats.skippedMissingPosition++;
            return;
        }

        Analysis analysis = rec.getAnalysis();
        if (options.rowFilter() != null && (analysis == null || !options.rowFilter().apply(analysis))) {
            stats.skippedRowFilter++;
            return;
        }

        Optional<Boolean> label = resolveLabel(objJson, rec, options);
        if (label.isEmpty()) {
            stats.skippedUnlabeled++;
            return;
        }

        boolean positive = label.get().booleanValue();
        if (positive) {
            if (stats.positives >= options.maxPositives()) {
                stats.skippedClassCap++;
                return;
            }
        } else if (stats.negatives >= options.maxNegatives()) {
            stats.skippedClassCap++;
            return;
        }

        float[] encoded = Encoder.encode(position);
        inputsWriter.writeRow(encoded);
        labelsWriter.writeScalar(positive ? 1.0f : 0.0f);

        stats.rowsWritten++;
        if (positive) {
            stats.positives++;
        } else {
            stats.negatives++;
        }
    }

    /**
     * Resolves the binary label for a record.
     */
    private static Optional<Boolean> resolveLabel(String objJson, Record rec, Options options) {
        if (options.labelFilter() != null) {
            Analysis analysis = rec.getAnalysis();
            if (analysis == null) {
                return Optional.empty();
            }
            return Optional.of(options.labelFilter().apply(analysis));
        }

        String kind = Json.parseStringField(objJson, "kind");
        if (kind != null && !kind.isBlank()) {
            if ("puzzle".equalsIgnoreCase(kind)) {
                return Optional.of(Boolean.TRUE);
            }
            if ("nonpuzzle".equalsIgnoreCase(kind) || "non-puzzle".equalsIgnoreCase(kind)) {
                return Optional.of(Boolean.FALSE);
            }
            return Optional.empty();
        }

        if (options.fallbackLabelFilter() != null) {
            Analysis analysis = rec.getAnalysis();
            if (analysis == null) {
                return Optional.empty();
            }
            return Optional.of(options.fallbackLabelFilter().apply(analysis));
        }
        return Optional.empty();
    }

    /**
     * Streams raw record JSON objects while reporting cumulative bytes for this
     * input.
     */
    private static void streamRecordJson(Path input, Consumer<String> consumer, LongConsumer byteProgress)
            throws IOException {
        if (isJsonArrayFile(input)) {
            Json.streamTopLevelObjects(input, consumer, byteProgress);
            return;
        }
        try (InputStream in = Files.newInputStream(input);
                InputStream progressIn = Json.progressInput(in, byteProgress);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(progressIn, StandardCharsets.UTF_8),
                        TEXT_BUFFER_SIZE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    consumer.accept(trimmed);
                }
            }
        }
    }

     /**
     * Handles add base.
     * @param progress progress
     * @param base base
     * @return computed value
     */
     private static LongConsumer addBase(LongConsumer progress, long base) {
        return progress == null ? null : done -> progress.accept(base + done);
    }

     /**
     * Handles file size.
     * @param input input
     * @return computed value
     */
     private static long fileSize(Path input) {
        try {
            return input == null ? 0L : Files.size(input);
        } catch (IOException ex) {
            return 0L;
        }
    }

    /**
     * Detects whether a file starts with a JSON array.
     */
    private static boolean isJsonArrayFile(Path input) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(input), StandardCharsets.UTF_8),
                TEXT_BUFFER_SIZE)) {
            int c;
            while ((c = reader.read()) != -1) {
                boolean skip = c == '\uFEFF' || Character.isWhitespace(c);
                if (!skip) {
                    return c == '[';
                }
            }
        }
        return false;
    }

    /**
     * Writes metadata next to the tensors.
     */
    private static void writeMetadata(
            Path metaPath,
            List<Path> recordFiles,
            Path inputsPath,
            Path labelsPath,
            Summary summary,
            Options options) throws IOException {
        StringBuilder builder = new StringBuilder(2048);
        builder.append("{\n");
        builder.append("  \"sources\": [");
        for (int i = 0; i < recordFiles.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            appendJsonString(builder, recordFiles.get(i).toString());
        }
        builder.append("],\n");
        appendJsonField(builder, 2, "inputs_file", inputsPath.toString(), true);
        appendJsonField(builder, 2, "labels_file", labelsPath.toString(), true);
        builder.append("  \"records_seen\": ").append(summary.seen()).append(",\n");
        builder.append("  \"rows_written\": ").append(summary.rowsWritten()).append(",\n");
        builder.append("  \"positives\": ").append(summary.positives()).append(",\n");
        builder.append("  \"negatives\": ").append(summary.negatives()).append(",\n");
        builder.append("  \"skipped\": {\n");
        builder.append("    \"invalid\": ").append(summary.skippedInvalid()).append(",\n");
        builder.append("    \"missing_position\": ").append(summary.skippedMissingPosition()).append(",\n");
        builder.append("    \"row_filter\": ").append(summary.skippedRowFilter()).append(",\n");
        builder.append("    \"unlabeled\": ").append(summary.skippedUnlabeled()).append(",\n");
        builder.append("    \"class_cap\": ").append(summary.skippedClassCap()).append("\n");
        builder.append(METADATA_SECTION_END);
        builder.append("  \"inputs\": {\n");
        builder.append("    \"encoder\": \"classifier-21planes\",\n");
        builder.append("    \"shape\": [").append(summary.rowsWritten()).append(", ").append(INPUTS).append("],\n");
        builder.append("    \"planes\": 21,\n");
        builder.append("    \"squares\": 64,\n");
        builder.append("    \"order\": \"channel-major (plane * 64 + square), a1..h8\",\n");
        builder.append("    \"side_to_move_perspective\": true\n");
        builder.append(METADATA_SECTION_END);
        builder.append("  \"labels\": {\n");
        builder.append("    \"target\": \"binary_class\",\n");
        builder.append("    \"negative\": 0.0,\n");
        builder.append("    \"positive\": 1.0,\n");
        appendJsonField(builder, 4, "positive_definition", positiveDefinition(options), true);
        appendJsonField(builder, 4, "source", labelSource(options), true);
        appendJsonField(builder, 4, "row_filter", options.rowFilterDsl(), true);
        appendJsonField(builder, 4, "label_filter", options.labelFilterDsl(), true);
        appendJsonField(builder, 4, "fallback_label_filter", options.fallbackLabelFilterDsl(), true);
        builder.append("    \"recommended_pos_weight\": ");
        if (summary.positives() > 0) {
            builder.append(String.format(java.util.Locale.ROOT, "%.8f",
                    summary.negatives() / (double) summary.positives()));
        } else {
            builder.append("null");
        }
        builder.append("\n");
        builder.append(METADATA_SECTION_END);
        builder.append("  \"class_caps\": {\n");
        builder.append("    \"max_positives\": ").append(formatCap(options.maxPositives())).append(",\n");
        builder.append("    \"max_negatives\": ").append(formatCap(options.maxNegatives())).append("\n");
        builder.append("  }\n");
        builder.append("}\n");
        Files.writeString(metaPath, builder.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Appends an indented JSON string field with a trailing comma when requested.
     */
    private static void appendJsonField(StringBuilder builder, int indent, String name, String value, boolean comma) {
        builder.append(" ".repeat(indent));
        appendJsonString(builder, name);
        builder.append(": ");
        appendJsonString(builder, value);
        if (comma) {
            builder.append(',');
        }
        builder.append('\n');
    }

    /**
     * Appends a JSON string or {@code null}.
     */
    private static void appendJsonString(StringBuilder builder, String value) {
        if (value == null) {
            builder.append("null");
            return;
        }
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
            case '\\':
                builder.append("\\\\");
                break;
            case '"':
                builder.append("\\\"");
                break;
            case '\n':
                builder.append("\\n");
                break;
            case '\r':
                builder.append("\\r");
                break;
            case '\t':
                builder.append("\\t");
                break;
            default:
                builder.append(ch);
                break;
            }
        }
        builder.append('"');
    }

    /**
     * Returns a metadata string describing the label source.
     */
    private static String labelSource(Options options) {
        if (options.labelFilter() != null) {
            return "label-filter";
        }
        if (options.fallbackLabelFilter() != null) {
            return "kind, else fallback-label-filter";
        }
        return "kind";
    }

    /**
     * Returns a metadata string describing what the positive class means.
     */
    private static String positiveDefinition(Options options) {
        if (options.labelFilter() != null) {
            return "records matching --label-filter";
        }
        if (options.fallbackLabelFilter() != null) {
            return "kind=puzzle, else configured puzzle verification filter";
        }
        return "kind=puzzle";
    }

    /**
     * Formats a class cap for metadata.
     */
    private static String formatCap(long value) {
        return value == NO_CLASS_CAP ? "null" : Long.toString(value);
    }

    /**
     * Returns a sibling file path from an output stem and suffix.
     */
    private static Path sibling(Path outStem, String suffix) {
        return outStem.resolveSibling(outStem.getFileName().toString() + suffix);
    }

    /**
     * Mutable counters used while streaming.
     */
    private static final class MutableSummary {
         /**
         * Stores the seen.
         */
         private long seen;
         /**
         * Stores the rows written.
         */
         private long rowsWritten;
         /**
         * Stores the positives.
         */
         private long positives;
         /**
         * Stores the negatives.
         */
         private long negatives;
         /**
         * Stores the skipped invalid.
         */
         private long skippedInvalid;
         /**
         * Stores the skipped missing position.
         */
         private long skippedMissingPosition;
         /**
         * Stores the skipped row filter.
         */
         private long skippedRowFilter;
         /**
         * Stores the skipped unlabeled.
         */
         private long skippedUnlabeled;
         /**
         * Stores the skipped class cap.
         */
         private long skippedClassCap;

         /**
         * Handles class caps reached.
         * @param options options
         * @return computed value
         */
         private boolean classCapsReached(Options options) {
            if (options.maxPositives() == NO_CLASS_CAP || options.maxNegatives() == NO_CLASS_CAP) {
                return false;
            }
            return positives >= options.maxPositives() && negatives >= options.maxNegatives();
        }

         /**
         * Converts this value to summary.
         * @return computed value
         */
         private Summary toSummary() {
            return new Summary(
                    seen,
                    rowsWritten,
                    positives,
                    negatives,
                    skippedInvalid,
                    skippedMissingPosition,
                    skippedRowFilter,
                    skippedUnlabeled,
                    skippedClassCap);
        }
    }

    /**
     * Internal signal used to stop streaming once requested class caps are met.
     */
    private static final class StopExport extends RuntimeException {
         /**
         * Shared serial version uid constant.
         */
         private static final long serialVersionUID = 1L;
    }
}
