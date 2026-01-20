package chess.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.debug.LogService;
import chess.struct.Plain;
import chess.struct.Record;
import chess.uci.Evaluation;
import chess.uci.Filter;
import chess.uci.Output;
import utility.Json;

/**
 * Used for providing utility methods to convert a JSON array of {@link Record} objects
 * into other output formats.
 *
 * <p>
 * This utility class is non-instantiable and operates in a streaming fashion to
 * avoid loading the entire input into memory.
 * </p>
 *
 * <p>
 * Input shape (whitespace allowed):
 * {@code [ { ... }, { ... }, ... ]}
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public class Converter {

    /**
     * Used for preventing instantiation of this utility class.
     */
    private Converter() {
        // prevents instantiation
    }

    /**
     * Used for stream-converting a JSON array file of records into one {@code .plain} file without
     * loading the whole input.
     *
     * <p>
     * Writes to a temp file, then atomically moves it into place.
     * </p>
     *
     * @param exportAll  include all exportable fields.
     * @param arguments  optional filter; {@code null} = emit all.
     * @param recordFile input JSON (array) path.
     * @param plainFile  output {@code .plain} path; if {@code null}, derived from {@code recordFile}.
     * @throws IllegalArgumentException if {@code recordFile} is {@code null}.
     */
    public static void recordToPlain(boolean exportAll, Filter arguments, Path recordFile, Path plainFile) {
        if (recordFile == null) {
            throw new IllegalArgumentException("recordfile is null");
        }
        if (plainFile == null) {
            plainFile = deriveOutputPath(recordFile, ".plain");
        }

        final Path tmp = plainFile.resolveSibling(plainFile.getFileName().toString() + ".tmp");
        final AtomicLong ok = new AtomicLong();
        final AtomicLong bad = new AtomicLong();

        LogService.info(String.format(
                "Converting records to plain format '%s' to '%s'.",
                recordFile, plainFile));

        try (BufferedWriter out = openWriter(tmp)) {
            Json.streamTopLevelObjects(
                    recordFile,
                    objJson -> processRecordJson(arguments, objJson, exportAll, out, ok, bad));
        } catch (Exception e) {
            cleanupTempQuietly(tmp);
            LogService.error(
                    e,
                    String.format(
                            "I/O error during record to plain conversion '%s' to '%s'.",
                            recordFile, plainFile));
            return;
        }

        try {
            if (ok.get() == 0L || Files.size(tmp) == 0L) {
                cleanupTempQuietly(tmp);
                LogService.info(String.format(
                        "No Plain blocks emitted; not overwriting existing output '%s' to '%s'.",
                        recordFile, plainFile));
                return;
            }
        } catch (IOException e) {
            cleanupTempQuietly(tmp);
            LogService.error(
                    e,
                    "Failed to stat temp file before finalizing",
                    String.format("Temp: %s", tmp));
            return;
        }

        try {
            finalizeOutput(tmp, plainFile);
        } catch (IOException e) {
            cleanupTempQuietly(tmp);
            LogService.error(
                    e,
                    String.format(
                            "Failed to move temp file into place during record to plain conversion '%s' to '%s'.",
                            recordFile, plainFile));
            return;
        }

        LogService.info(String.format(
                "Completed record to plain conversion '%s' to '%s' and wrote %d records while skipping %d invalid records.",
                recordFile, plainFile, ok.get(), bad.get()));
    }

    /**
     * Used for stream-converting a JSON array file of records into one CSV file with a column per requested PV.
     *
     * <p>
     * Always writes a header. PV columns are sized to the maximum principal variation count observed across records that
     * pass the filter. Each PV adds three columns: evaluation, best move in SAN, and the UCI PV line.
     * </p>
     *
     * @param arguments  optional filter; {@code null} = emit all.
     * @param recordFile input JSON (array) path.
     * @param csvFile    output {@code .csv} path; if {@code null}, derived from {@code recordFile}.
     * @throws IllegalArgumentException if {@code recordFile} is {@code null}.
     */
    public static void recordToCsv(Filter arguments, Path recordFile, Path csvFile) {
        if (recordFile == null) {
            throw new IllegalArgumentException("recordfile is null");
        }
        if (csvFile == null) {
            csvFile = deriveOutputPath(recordFile, ".csv");
        }

        final Path tmp = csvFile.resolveSibling(csvFile.getFileName().toString() + ".tmp");
        final AtomicLong ok = new AtomicLong();
        final AtomicLong bad = new AtomicLong();

        LogService.info(String.format(
                "Converting records to CSV '%s' to '%s'.",
                recordFile, csvFile));

        final int maxPv;
        try {
            maxPv = computeMaxPivot(arguments, recordFile);
        } catch (Exception e) {
            cleanupTempQuietly(tmp);
            LogService.error(
                    e,
                    String.format(
                            "Failed to scan input before CSV conversion '%s' to '%s'.",
                            recordFile, csvFile));
            return;
        }

        if (maxPv == 0) {
            LogService.info(String.format(
                    "CSV conversion found no eligible records in '%s'; skipping output '%s'.",
                    recordFile, csvFile));
            return;
        }

        try (BufferedWriter out = openWriter(tmp)) {
            writeCsvHeader(out, maxPv);
            Json.streamTopLevelObjects(
                    recordFile,
                    objJson -> processRecordCsv(arguments, objJson, maxPv, out, ok, bad));
        } catch (Exception e) {
            cleanupTempQuietly(tmp);
            LogService.error(
                    e,
                    String.format(
                            "I/O error during record to CSV conversion '%s' to '%s'.",
                            recordFile, csvFile));
            return;
        }

        try {
            if (ok.get() == 0L || Files.size(tmp) == 0L) {
                cleanupTempQuietly(tmp);
                LogService.info(String.format(
                        "No CSV rows emitted; not overwriting existing output '%s' to '%s'.",
                        recordFile, csvFile));
                return;
            }
        } catch (IOException e) {
            cleanupTempQuietly(tmp);
            LogService.error(
                    e,
                    "Failed to stat temp file before finalizing",
                    String.format("Temp: %s", tmp));
            return;
        }

        try {
            finalizeOutput(tmp, csvFile);
        } catch (IOException e) {
            cleanupTempQuietly(tmp);
            LogService.error(
                    e,
                    String.format(
                            "Failed to move temp file into place during record to CSV conversion '%s' to '%s'.",
                            recordFile, csvFile));
            return;
        }

        LogService.info(String.format(
                "Completed record to CSV conversion '%s' to '%s' and wrote %d records while skipping %d invalid records.",
                recordFile, csvFile, ok.get(), bad.get()));
    }

    /**
     * Converts a {@code .record} JSON array file into PGN games by linking records via their {@code parent} and
     * {@code position} FENs.
     *
     * <p>
     * The exporter connects records directly when a record's {@code parent} equals another record's {@code position},
     * and also bridges positions by generating legal subpositions to find matching {@code parent} nodes.
     * </p>
     *
     * @param recordFile input JSON (array) path.
     * @param pgnFile    output PGN path; if {@code null}, derived from {@code recordFile}.
     * @throws IllegalArgumentException if {@code recordFile} is {@code null}.
     */
    public static void recordToPgn(Path recordFile, Path pgnFile) {
        if (recordFile == null) {
            throw new IllegalArgumentException("recordfile is null");
        }
        if (pgnFile == null) {
            pgnFile = deriveOutputPath(recordFile, ".pgn");
        }
        RecordPgnExporter.export(recordFile, pgnFile);
    }

    /**
     * Converts a mixed puzzle/non-puzzle record file into PGN games by first
     * filtering the input JSON objects.
     *
     * @param recordFile        input JSON (array or JSONL) path.
     * @param pgnFile           output PGN path; if {@code null}, derived from {@code recordFile}.
     * @param includeRecordJson predicate to decide whether a record should be included; {@code null} = include all.
     * @throws IllegalArgumentException if {@code recordFile} is {@code null}.
     */
    public static void puzzlesToPgn(Path recordFile, Path pgnFile, Predicate<String> includeRecordJson) {
        if (recordFile == null) {
            throw new IllegalArgumentException("recordfile is null");
        }
        if (pgnFile == null) {
            pgnFile = deriveOutputPath(recordFile, ".pgn");
        }
        RecordPgnExporter.exportFiltered(recordFile, pgnFile, includeRecordJson);
    }

    /**
     * Opens a UTF-8 {@link BufferedWriter} for {@code tmp} with CREATE/TRUNCATE/WRITE, creating parent directories as
     * needed.
     *
     * @param tmp path to the temporary output file.
     * @return an open writer ready for output.
     * @throws IOException if the directory creation or file open fails.
     */
    private static BufferedWriter openWriter(Path tmp) throws IOException {
        ensureParentDirs(tmp);
        return Files.newBufferedWriter(
                tmp,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /**
     * Ensures the parent directory of {@code p} exists, creating it (and any missing intermediates) if necessary.
     *
     * @param p path whose parent directory should be present.
     * @throws IOException if directory creation fails.
     */
    private static void ensureParentDirs(Path p) throws IOException {
        final Path parent = p.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Scans the input once to determine the maximum PV count for CSV header sizing.
     *
     * @param arguments  optional filter; {@code null} = accept all.
     * @param recordFile input JSON (array) path.
     * @return maximum PV index observed across accepted records.
     * @throws IOException if streaming fails.
     */
    private static int computeMaxPivot(Filter arguments, Path recordFile) throws IOException {
        final int[] maxPv = new int[1];
        Json.streamTopLevelObjects(recordFile, objJson -> {
            try {
                Record r = Record.fromJson(objJson);
                if (r == null) {
                    return;
                }
                if (arguments != null && !arguments.apply(r.getAnalysis())) {
                    return;
                }
                int pivots = r.getAnalysis().getPivots();
                maxPv[0] = Math.max(maxPv[0], Math.max(1, pivots));
            } catch (IllegalArgumentException | NullPointerException ignore) {
                // Skip invalid records in the sizing pass.
            }
        });
        return maxPv[0];
    }

    /**
     * Writes the CSV header with base fields plus PV-specific columns.
     *
     * @param out   destination writer.
     * @param maxPv maximum PV count to include.
     * @throws IOException if writing fails.
     */
    private static void writeCsvHeader(BufferedWriter out, int maxPv) throws IOException {
        StringBuilder sb = new StringBuilder(64 + maxPv * 32);
        sb.append("created,engine,parent,position,description,tags");
        for (int pv = 1; pv <= maxPv; pv++) {
            sb.append(",eval_pv").append(pv)
                    .append(",bestmove_pv").append(pv).append("_san")
                    .append(",pv").append(pv).append("_uci");
        }
        sb.append(System.lineSeparator());
        out.write(sb.toString());
    }

    /**
     * Parses one JSON object into a {@link Record} and, if accepted by {@code arguments}, writes its Plain block.
     *
     * @param arguments optional filter; {@code null} = accept all.
     * @param objJson   JSON text of a single object.
     * @param exportall pass-through to {@code Plain.toString}.
     * @param out       destination writer.
     * @param ok        counter for successful writes.
     * @param bad       counter for invalid/rejected records.
     * @throws UncheckedIOException on write errors.
     */
    private static void processRecordJson(
            Filter arguments,
            String objJson,
            boolean exportall,
            BufferedWriter out,
            AtomicLong ok,
            AtomicLong bad) {
        try {
            Record r = Record.fromJson(objJson);
            if (r == null) {
                bad.incrementAndGet();
                return;
            }
            if (arguments == null || arguments.apply(r.getAnalysis())) {
                String block = Plain.toString(r, exportall);
                (writeBlock(out, block) ? ok : bad).incrementAndGet();
            }
        } catch (IllegalArgumentException ex) {
            bad.incrementAndGet();
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Parses one JSON object into a {@link Record} and, if accepted by {@code arguments}, writes its CSV row.
     *
     * @param arguments optional filter; {@code null} = accept all.
     * @param objJson   JSON text of a single object.
     * @param maxPv     column count for PVs.
     * @param out       destination writer.
     * @param ok        counter for successful writes.
     * @param bad       counter for invalid/rejected records.
     * @throws UncheckedIOException on write errors.
     */
    private static void processRecordCsv(
            Filter arguments,
            String objJson,
            int maxPv,
            BufferedWriter out,
            AtomicLong ok,
            AtomicLong bad) {
        try {
            Record r = Record.fromJson(objJson);
            if (r == null) {
                bad.incrementAndGet();
                return;
            }
            if (arguments == null || arguments.apply(r.getAnalysis())) {
                writeCsvRow(out, r, maxPv);
                ok.incrementAndGet();
            }
        } catch (IllegalArgumentException ex) {
            bad.incrementAndGet();
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Writes a single Plain block to the writer.
     *
     * @param out   writer to write to.
     * @param block string block to write.
     * @return true if block was written, false otherwise.
     * @throws IOException if write fails.
     */
    private static boolean writeBlock(BufferedWriter out, String block) throws IOException {
        if (block == null || block.isEmpty()) {
            return false;
        }
        out.write(block);
        return true;
    }

    /**
     * Writes one CSV row for the given record, emitting empty cells for missing PV data.
     *
     * @param out   destination writer.
     * @param r     record to serialize.
     * @param maxPv number of PV columns in the header.
     * @throws IOException if writing fails.
     */
    private static void writeCsvRow(BufferedWriter out, Record r, int maxPv) throws IOException {
        StringBuilder sb = new StringBuilder(128 + maxPv * 48);
        sb.append(r.getCreated()).append(',')
                .append(csv(r.getEngine())).append(',')
                .append(csv(toFen(r.getParent()))).append(',')
                .append(csv(toFen(r.getPosition()))).append(',')
                .append(csv(r.getDescription())).append(',')
                .append(csv(joinTags(r.getTags())));

        for (int pv = 1; pv <= maxPv; pv++) {
            Output best = r.getAnalysis().getBestOutput(pv);
            sb.append(',').append(csv(formatEvaluation(best)));
            sb.append(',').append(csv(formatBestMoveSan(r.getPosition(), r.getAnalysis().getBestMove(pv))));
            sb.append(',').append(csv(formatPvMoves(best)));
        }

        sb.append(System.lineSeparator());
        out.write(sb.toString());
    }

    /**
     * Formats the evaluation of an {@link Output} for CSV.
     *
     * <p>A mate score is prefixed with {@code '#'} so downstream consumers can
     * differentiate finishing lines, while centipawn values are emitted
     * verbatim.</p>
     *
     * @param best output to inspect.
     * @return evaluation string ({@code #N} for mate, centipawns otherwise) or empty.
     */
    private static String formatEvaluation(Output best) {
        if (best == null) {
            return "";
        }
        Evaluation eval = best.getEvaluation();
        if (eval == null || !eval.isValid()) {
            return "";
        }
        if (eval.isMate()) {
            return "#" + eval.getValue();
        }
        return Integer.toString(eval.getValue());
    }

    /**
     * Formats the best move for a PV as SAN, falling back to UCI if SAN generation fails.
     *
     * @param pos  source position.
     * @param best best move.
     * @return SAN move or UCI fallback; empty if unavailable.
     */
    private static String formatBestMoveSan(Position pos, short best) {
        if (pos == null || best == Move.NO_MOVE) {
            return "";
        }
        try {
            return SAN.toAlgebraic(pos, best);
        } catch (RuntimeException ex) {
            return Move.toString(best);
        }
    }

    /**
     * Formats the PV move list as space-separated UCI.
     *
     * @param best output to inspect.
     * @return PV string or empty if none.
     */
    private static String formatPvMoves(Output best) {
        if (best == null) {
            return "";
        }
        short[] moves = best.getMoves();
        if (moves == null || moves.length == 0) {
            return "";
        }
        StringBuilder pv = new StringBuilder(moves.length * 5);
        boolean first = true;
        for (short move : moves) {
            if (move == Move.NO_MOVE) {
                continue;
            }
            if (!first) {
                pv.append(' ');
            }
            pv.append(Move.toString(move));
            first = false;
        }
        return pv.toString();
    }

    /**
     * Escapes a value for safe CSV emission using RFC 4180 quoting rules.
     *
     * @param value string to escape.
     * @return escaped value (possibly quoted); never {@code null}.
     */
    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return needsQuote ? "\"" + escaped + "\"" : escaped;
    }

    /**
     * Joins tags for CSV output.
     *
     * @param tags tag array (may be null or empty).
     * @return semicolon-joined tags or empty.
     */
    private static String joinTags(String[] tags) {
        if (tags == null || tags.length == 0) {
            return "";
        }
        return String.join(";", tags);
    }

    /**
     * Returns the FEN string or empty if position is null.
     *
     * @param p position to convert.
     * @return FEN or empty.
     */
    private static String toFen(Position p) {
        return p == null ? "" : p.toString();
    }

    /**
     * Commits a temporary file by syncing it and atomically replacing the destination.
     *
     * <p>
     * Steps:
     * <ol>
     * <li>Ensure the destination directory exists.</li>
     * <li>Force the temp file to disk.</li>
     * <li>Move the temp file into place (Atomic move when supported, fallback to replace).</li>
     * <li>Attempt to sync the containing directory to make the move durable.</li>
     * </ol>
     * </p>
     *
     * @param tmp  temporary file to commit
     * @param dest final destination path
     * @throws IOException if syncing or moving fails
     */
    private static void finalizeOutput(Path tmp, Path dest) throws IOException {
        ensureParentDirs(dest);

        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            ch.force(true);
        }

        try {
            Files.move(tmp, dest,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }

        final Path parent = dest.getParent();
        if (parent != null) {
            try (FileChannel dirCh = FileChannel.open(parent, StandardOpenOption.READ)) {
                dirCh.force(true);
            } catch (IOException ignore) {
                // Not all platforms allow opening/syncing a directory; safe to ignore.
            }
        }
    }

    /**
     * Deletes the temporary file without propagating I/O failures.
     *
     * <p>
     * Best-effort cleanup that logs nothing; used when an earlier error already
     * caught the user's attention.
     * </p>
     *
     * @param tmp path to delete
     */
    private static void cleanupTempQuietly(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignore) {
            // Failed to delete temp file.
        }
    }

    /**
     * Derives a new file path by replacing or appending a file extension.
     *
     * <p>
     * If the input name already contains an extension, it is replaced; otherwise
     * the new extension is appended.
     * </p>
     *
     * @param input  original input path
     * @param newExt new file extension (including dot)
     * @return new {@link Path} with updated extension
     */
    private static Path deriveOutputPath(Path input, String newExt) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0 ? name.substring(0, dot) : name);
        return input.resolveSibling(base + newExt);
    }
}
