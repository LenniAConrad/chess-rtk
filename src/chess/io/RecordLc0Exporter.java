package chess.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;

import chess.core.Position;
import chess.nn.lc0.Encoder;
import chess.nn.lc0.Network;
import chess.nn.lc0.PolicyEncoder;
import chess.struct.Record;
import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Json;

/**
 * Exporter that converts {@code .record} JSON arrays into LC0-style training tensors.
 *
 * <p>
 * Writes four files next to the requested output stem:
 * <ul>
 *   <li>{@code <stem>.lc0.inputs.npy} shaped {@code (N, 112*64)} float32</li>
 *   <li>{@code <stem>.lc0.policy.npy} shaped {@code (N, policySize)} float32 (one-hot)</li>
 *   <li>{@code <stem>.lc0.value.npy} shaped {@code (N,)} float32 (scalar in [-1, 1])</li>
 *   <li>{@code <stem>.lc0.meta.json} metadata for policy/value encoding</li>
 * </ul>
 * </p>
 *
 * <p>
 * Policy encoding uses the LC0 73-plane format (see {@link PolicyEncoder}).
 * If {@code weights} are provided, the policy is compressed using the model's
 * policy map to match the LC0 network output size.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordLc0Exporter {

    /**
     * LC0 input planes length (112 * 64).
     */
    private static final int INPUTS = 112 * 64;

    /**
     * Clamp range for centipawn conversion (in pawns).
     */
    private static final float PAWN_CLAMP = 20.0f;

    /**
     * Utility class; prevent instantiation.
     */
    private RecordLc0Exporter() {
        // utility
    }

    /**
     * Export a {@code .record} JSON array into LC0 input/policy/value tensors.
     *
     * @param recordFile input record JSON array
     * @param outStem output stem path
     * @param weights optional LC0 weights file to load a policy map for compression (nullable)
     * @throws IOException if reading or writing fails
     */
    public static void export(Path recordFile, Path outStem, Path weights) throws IOException {
        if (recordFile == null || outStem == null) {
            throw new IllegalArgumentException("recordFile and outStem must be non-null");
        }

        int[] policyMap = null;
        int[] policyMapInverseTmp = null;
        String policyMapSourceTmp = null;

        if (weights != null) {
            policyMap = Network.loadPolicyMap(weights);
            policyMapInverseTmp = invertPolicyMap(policyMap);
            policyMapSourceTmp = weights.toString();
        }

        final int[] policyMapInverse = policyMapInverseTmp;
        final String policyMapSource = policyMapSourceTmp;
        final int policySize = policyMap != null ? policyMap.length : PolicyEncoder.RAW_POLICY_SIZE;

        Path inputsPath = outStem.resolveSibling(outStem.getFileName().toString() + ".lc0.inputs.npy");
        Path policyPath = outStem.resolveSibling(outStem.getFileName().toString() + ".lc0.policy.npy");
        Path valuePath = outStem.resolveSibling(outStem.getFileName().toString() + ".lc0.value.npy");
        Path metaPath = outStem.resolveSibling(outStem.getFileName().toString() + ".lc0.meta.json");

        boolean success = false;
        long[] counters = new long[2]; // [0]=written, [1]=skipped

        try (NpyFloat32Writer inputsWriter = NpyFloat32Writer.open2D(inputsPath, INPUTS);
                NpyFloat32Writer policyWriter = NpyFloat32Writer.open2D(policyPath, policySize);
                NpyFloat32Writer valueWriter = NpyFloat32Writer.open1D(valuePath)) {

            float[] policyBuffer = new float[policySize];

            Consumer<String> sink = objJson -> {
                try {
                    if (!exportRecordObject(objJson, inputsWriter, policyWriter, valueWriter,
                            policyBuffer, policyMapInverse)) {
                        counters[1]++;
                    } else {
                        counters[0]++;
                    }
                } catch (IOException io) {
                    throw new UncheckedIOException(io);
                }
            };

            Json.streamTopLevelObjects(recordFile, sink);
            success = true;
        } catch (UncheckedIOException uio) {
            throw uio.getCause();
        } finally {
            if (!success) {
                Files.deleteIfExists(inputsPath);
                Files.deleteIfExists(policyPath);
                Files.deleteIfExists(valuePath);
            }
        }

        writeMetadata(metaPath, recordFile, counters[0], counters[1], policySize, policyMapSource);
    }

    private static boolean exportRecordObject(
            String json,
            NpyFloat32Writer inputsWriter,
            NpyFloat32Writer policyWriter,
            NpyFloat32Writer valueWriter,
            float[] policyBuffer,
            int[] policyMapInverse) throws IOException {
        Record parsedRecord = Record.fromJson(json);
        if (parsedRecord == null) {
            return false;
        }

        Position position = parsedRecord.getPosition();
        if (position == null) {
            return false;
        }

        Analysis analysis = parsedRecord.getAnalysis();
        if (analysis == null) {
            return false;
        }

        Output best = analysis.getBestOutput();
        if (best == null || best.getMoves() == null || best.getMoves().length == 0) {
            return false;
        }

        Evaluation evaluation = best.getEvaluation();
        Chances chances = best.getChances();
        if ((evaluation == null || !evaluation.isValid()) && chances == null) {
            return false;
        }

        short bestMove = best.getMoves()[0];
        int rawIndex = PolicyEncoder.rawPolicyIndex(position, bestMove);
        if (rawIndex < 0) {
            return false;
        }

        int policyIndex = rawIndex;
        if (policyMapInverse != null) {
            if (rawIndex >= policyMapInverse.length) {
                return false;
            }
            policyIndex = policyMapInverse[rawIndex];
            if (policyIndex < 0) {
                return false;
            }
        }

        float[] inputs = Encoder.encode(position);
        if (inputs.length != INPUTS) {
            return false;
        }

        Arrays.fill(policyBuffer, 0.0f);
        policyBuffer[policyIndex] = 1.0f;

        float value = valueFromEvaluation(evaluation, chances);

        inputsWriter.writeRow(inputs);
        policyWriter.writeRow(policyBuffer);
        valueWriter.writeScalar(value);
        return true;
    }

    private static float valueFromEvaluation(Evaluation evaluation, Chances chances) {
        if (chances != null) {
            float win = chances.getWinChance() / 1000.0f;
            float loss = chances.getLossChance() / 1000.0f;
            return clamp(win - loss, -1.0f, 1.0f);
        }
        if (evaluation == null || !evaluation.isValid()) {
            return 0.0f;
        }
        if (evaluation.isMate()) {
            return evaluation.getValue() >= 0 ? 1.0f : -1.0f;
        }
        float pawns = evaluation.getValue() / 100.0f;
        pawns = clamp(pawns, -PAWN_CLAMP, PAWN_CLAMP);
        return pawns / PAWN_CLAMP;
    }

    private static int[] invertPolicyMap(int[] policyMap) {
        int maxIndex = -1;
        for (int value : policyMap) {
            if (value > maxIndex) {
                maxIndex = value;
            }
        }
        int[] inverse = new int[maxIndex + 1];
        Arrays.fill(inverse, -1);
        for (int compressedIndex = 0; compressedIndex < policyMap.length; compressedIndex++) {
            int rawIndex = policyMap[compressedIndex];
            if (rawIndex >= 0 && rawIndex < inverse.length) {
                inverse[rawIndex] = compressedIndex;
            }
        }
        return inverse;
    }

    private static void writeMetadata(Path metaPath, Path recordFile, long written, long skipped,
            int policySize, String policyMapSource) throws IOException {
        StringBuilder builder = new StringBuilder(1024);
        builder.append("{\n");
        builder.append("  \"source\": \"").append(escapeJson(recordFile.toString())).append("\",\n");
        builder.append("  \"rows_written\": ").append(written).append(",\n");
        builder.append("  \"rows_skipped\": ").append(skipped).append(",\n");
        builder.append("  \"inputs\": {\n");
        builder.append("    \"planes\": 112,\n");
        builder.append("    \"squares\": 64,\n");
        builder.append("    \"order\": \"channel-major (plane * 64 + square), a1..h8\",\n");
        builder.append("    \"side_to_move_perspective\": true\n");
        builder.append("  },\n");
        builder.append("  \"policy\": {\n");
        builder.append("    \"encoding\": \"lc0-73plane\",\n");
        builder.append("    \"size\": ").append(policySize).append(",\n");
        builder.append("    \"compressed\": ").append(policyMapSource != null).append(",\n");
        builder.append("    \"weights_path\": ");
        if (policyMapSource != null) {
            builder.append("\"").append(escapeJson(policyMapSource)).append("\"");
        } else {
            builder.append("null");
        }
        builder.append(",\n");
        builder.append("    \"plane_order\": \"N,S,E,W,NE,NW,SE,SW\",\n");
        builder.append("    \"knight_order\": \"(1,2),(2,1),(2,-1),(1,-2),(-1,-2),(-2,-1),(-2,1),(-1,2)\",\n");
        builder.append("    \"underpromotions\": \"N,B,R x forward,forward-left,forward-right\"\n");
        builder.append("  },\n");
        builder.append("  \"value\": {\n");
        builder.append("    \"encoding\": \"scalar\",\n");
        builder.append("    \"range\": \"[-1,1]\",\n");
        builder.append("    \"source\": \"wdl if available else centipawn/mate\",\n");
        builder.append("    \"cp_clamp_pawns\": ").append(PAWN_CLAMP).append("\n");
        builder.append("  }\n");
        builder.append("}\n");

        Files.writeString(metaPath, builder.toString(), StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static float clamp(float value, float lo, float hi) {
        if (value < lo) {
            return lo;
        }
        if (value > hi) {
            return hi;
        }
        return value;
    }

    /**
     * Streaming .npy float32 writer that writes a placeholder header and patches
     * the row count in-place on close. Constant memory.
     */
    private static final class NpyFloat32Writer implements Closeable {

        /**
         * Width of the row-count placeholder field in the header.
         * Keeps header patching stable even for very large datasets.
         */
        private static final int ROWS_FIELD_WIDTH = 20;

        /**
         * Random-access file backing the output.
         * Used to patch the header on close.
         */
        private final RandomAccessFile raf;

        /**
         * File channel used for streaming writes.
         * Keeps payload writes efficient and sequential.
         */
        private final FileChannel channel;

        /**
         * Whether the output is one-dimensional (labels).
         * Controls header shape and write behavior.
         */
        private final boolean oneD;

        /**
         * Column count for 2D outputs (features).
         * Ignored for 1D label outputs.
         */
        private final int cols;

        /**
         * Offset in the file where the row-count digits begin.
         * Used to patch the placeholder on close.
         */
        private final long rowsFieldOffsetInFile;

        /**
         * Width of the row-count field in the header.
         * Matches the placeholder length.
         */
        private final int rowsFieldWidth;

        /**
         * Reusable buffer for writing full feature rows.
         * Avoids per-row allocations.
         */
        private final ByteBuffer rowBuf;

        /**
         * Reusable buffer for writing scalar labels.
         * Avoids per-write allocations.
         */
        private final ByteBuffer scalarBuf;

        /**
         * Number of rows written so far.
         * Incremented after each successful write.
         */
        private long rows = 0;

        /**
         * Whether the writer has been closed.
         * Prevents double-close operations.
         */
        private boolean closed = false;

        /**
         * Opens a 2D writer for feature rows.
         * Uses the provided column count for the header shape.
         *
         * @param path output file path
         * @param cols number of columns per row
         * @return new writer instance
         * @throws IOException if the file cannot be created
         */
        static NpyFloat32Writer open2D(Path path, int cols) throws IOException {
            return new NpyFloat32Writer(path, false, cols);
        }

        /**
         * Opens a 1D writer for scalar labels.
         * Writes a single-column shape in the header.
         *
         * @param path output file path
         * @return new writer instance
         * @throws IOException if the file cannot be created
         */
        static NpyFloat32Writer open1D(Path path) throws IOException {
            return new NpyFloat32Writer(path, true, -1);
        }

        /**
         * Creates a streaming .npy writer and writes the header placeholder.
         *
         * @param path output path
         * @param oneD whether the output is 1D (labels) or 2D (features)
         * @param cols number of columns for 2D output, ignored for 1D
         * @throws IOException if the file cannot be created or initialized
         */
        private NpyFloat32Writer(Path path, boolean oneD, int cols) throws IOException {
            this.oneD = oneD;
            this.cols = cols;

            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            RandomAccessFile localRaf = new RandomAccessFile(path.toFile(), "rw");
            FileChannel localChannel = localRaf.getChannel();

            long localRowsFieldOffsetInFile;
            int localRowsFieldWidth;
            ByteBuffer localRowBuf;
            ByteBuffer localScalarBuf;

            try {
                localRaf.setLength(0);

                // Build header with fixed-width rows field using spaces (NOT leading zeros).
                String rowsPlaceholder = padLeft(0L, ROWS_FIELD_WIDTH);

                String shape = oneD
                        ? "(" + rowsPlaceholder + ",)"
                        : "(" + rowsPlaceholder + ", " + cols + ",)";

                String header = "{'descr': '<f4', 'fortran_order': False, 'shape': " + shape + ", }";

                // NPY v1.0 header: magic(6) + version(2) + headerlen(2) + header bytes, padded to 16-byte alignment.
                int preamble = 10;
                int headerLenNoPad = header.length() + 1; // + '\n'
                int pad = (16 - ((preamble + headerLenNoPad) % 16)) % 16;
                String headerPadded = header + " ".repeat(pad) + "\n";
                byte[] headerBytes = headerPadded.getBytes(StandardCharsets.US_ASCII);

                // Locate where the placeholder lives within the header bytes so we can patch it later.
                int idx = headerPadded.indexOf(rowsPlaceholder);
                if (idx < 0) {
                    throw new IOException("Internal error: rows placeholder not found in header");
                }
                localRowsFieldOffsetInFile = (long) preamble + idx;
                localRowsFieldWidth = rowsPlaceholder.length();

                // Write magic + version
                localRaf.write(new byte[] { (byte) 0x93, 'N', 'U', 'M', 'P', 'Y' });
                localRaf.write(new byte[] { 1, 0 }); // v1.0

                // header length (uint16 little-endian)
                int headerLength = headerBytes.length;
                if (headerLength > 0xFFFF) {
                    throw new IOException("NPY header too large for v1.0: " + headerLength);
                }
                localRaf.write((byte) (headerLength & 0xFF));
                localRaf.write((byte) ((headerLength >>> 8) & 0xFF));

                // header bytes
                localRaf.write(headerBytes);

                // Payload buffers (reused; no per-row allocations)
                localScalarBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                localRowBuf = oneD
                        ? ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        : ByteBuffer.allocate(cols * 4).order(ByteOrder.LITTLE_ENDIAN);
            } catch (IOException e) {
                try {
                    localChannel.close();
                } catch (IOException closeEx) {
                    e.addSuppressed(closeEx);
                }
                try {
                    localRaf.close();
                } catch (IOException closeEx) {
                    e.addSuppressed(closeEx);
                }
                throw e;
            }

            this.raf = localRaf;
            this.channel = localChannel;
            this.rowsFieldOffsetInFile = localRowsFieldOffsetInFile;
            this.rowsFieldWidth = localRowsFieldWidth;
            this.rowBuf = localRowBuf;
            this.scalarBuf = localScalarBuf;
        }

        /**
         * Writes a single feature row to the output file.
         * Increments the row count after a successful write.
         *
         * @param row feature row to write
         * @throws IOException if writing fails
         */
        void writeRow(float[] row) throws IOException {
            if (oneD) {
                throw new IllegalStateException("This writer is 1D; use writeScalar()");
            }
            if (row.length != cols) {
                throw new IllegalArgumentException("Expected row length " + cols + " but got " + row.length);
            }

            rowBuf.clear();
            for (int index = 0; index < cols; index++) {
                rowBuf.putFloat(row[index]);
            }
            rowBuf.flip();
            while (rowBuf.hasRemaining()) {
                channel.write(rowBuf);
            }
            rows++;
        }

        /**
         * Writes a single scalar label to the output file.
         * Increments the row count after a successful write.
         *
         * @param value scalar value to write
         * @throws IOException if writing fails
         */
        void writeScalar(float value) throws IOException {
            scalarBuf.clear();
            scalarBuf.putFloat(value);
            scalarBuf.flip();
            while (scalarBuf.hasRemaining()) {
                channel.write(scalarBuf);
            }
            rows++;
        }

        /**
         * Patches the header with the final row count and closes the file.
         * Ensures the NPY header stays consistent with the written payload.
         *
         * @throws IOException if flushing or closing fails
         */
        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;

            IOException failure = null;
            try {
                // Patch the rows field in-place using spaces (valid Python integer literal formatting).
                String rowsStr = padLeft(rows, rowsFieldWidth);
                byte[] rowsBytes = rowsStr.getBytes(StandardCharsets.US_ASCII);

                raf.seek(rowsFieldOffsetInFile);
                raf.write(rowsBytes);

                channel.force(false);
            } catch (IOException e) {
                failure = e;
            }

            try {
                channel.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            try {
                raf.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }

            if (failure != null) {
                throw failure;
            }
        }

        /**
         * Pads a numeric value with leading spaces to a fixed width.
         * Used for the NPY header row-count placeholder.
         *
         * @param value value to format
         * @param width field width to pad to
         * @return padded string representation
         */
        private static String padLeft(long value, int width) {
            String text = Long.toString(value);
            int pad = width - text.length();
            if (pad <= 0) {
                return text;
            }
            return " ".repeat(pad) + text;
        }
    }
}
