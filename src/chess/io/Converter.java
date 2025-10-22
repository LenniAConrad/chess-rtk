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

import utility.Json;
import chess.model.Record;
import chess.uci.Filter;
import chess.model.Plain;
import chess.debug.LogService;

/**
 * Used for providing utility methods to convert a JSON array of Record objects
 * into Plain format blocks.
 * This utility class is non-instantiable and operates in a streaming fashion to
 * avoid loading the entire
 * input into memory.
 *
 * Input shape (whitespace allowed):
 * [ { ... }, { ... }, ... ]
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
     * Used for stream-converting a JSON array file of records into one
     * {@code .plain} file without
     * loading the whole input. Writes to a temp file, then atomically moves it into
     * place.
     *
     * @param exportAll  include all exportable fields.
     * @param arguments  optional filter; {@code null} = emit all.
     * @param recordFile input JSON (array) path.
     * @param plainFile  output {@code .plain} path; if {@code null}, derived from
     *                   {@code recordFile}.
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
     * Opens a UTF-8 {@link BufferedWriter} for {@code tmp} with
     * CREATE/TRUNCATE/WRITE, creating parent directories as needed.
     *
     * @param tmp path to the temporary output file
     * @return an open writer ready for output
     * @throws IOException if the directory creation or file open fails
     */
    private static BufferedWriter openWriter(Path tmp) throws IOException {
        ensureParentDirs(tmp); // make sure the temp file's dir exists
        return Files.newBufferedWriter(
                tmp,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /**
     * Ensures the parent directory of {@code p} exists, creating it (and any
     * missing intermediates) if necessary. No-op if there is no parent or it
     * already exists.
     *
     * @param p path whose parent directory should be present
     * @throws IOException if directory creation fails
     */
    private static void ensureParentDirs(Path p) throws IOException {
        final Path parent = p.getParent();
        if (parent != null)
            Files.createDirectories(parent);
    }

    /**
     * Parses one JSON object into a Record and, if accepted by {@code arguments},
     * writes its Plain block. Updates {@code ok}/{@code bad} counters.
     *
     * @param arguments optional filter; {@code null} = accept all
     * @param objJson   JSON text of a single object
     * @param exportall pass-through to {@code Plain.toString}
     * @param out       destination writer
     * @param ok        counter for successful writes
     * @param bad       counter for invalid/rejected records
     * @throws UncheckedIOException on write errors
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
     * Used for writing a single Plain block to the writer.
     *
     * @param out   writer to write to
     * @param block string block to write
     * @return true if block was written, false otherwise
     * @throws IOException if write fails
     */
    private static boolean writeBlock(BufferedWriter out, String block) throws IOException {
        if (block == null || block.isEmpty()) {
            return false;
        }
        out.write(block);
        return true;
    }

    /**
     * Commits a temp file: ensure parent dir exists, fsync the temp, then move it
     * to {@code dest} (atomic when supported, else replace), and best-effort fsync
     * the destination directory.
     *
     * @param tmp  temporary file to commitsy
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
     * Used for silently cleaning up temporary files.
     *
     * @param tmp path to delete
     */
    private static void cleanupTempQuietly(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignore) {
            // Failed to delete temp file
        }
    }

    /**
     * Used for deriving a new file path by replacing or appending a file extension.
     *
     * @param input  original input path
     * @param newExt new file extension (including dot)
     * @return new Path with updated extension
     */
    private static Path deriveOutputPath(Path input, String newExt) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0 ? name.substring(0, dot) : name);
        return input.resolveSibling(base + newExt);
    }

}
