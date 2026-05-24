package chess.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Streaming NumPy {@code .npy} float32 writer.
 *
 * <p>
 * The writer keeps memory usage constant by writing a placeholder header first
 * and patching the final row count on close.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class NpyFloat32Writer implements Closeable {

    /**
     * Width of the row-count placeholder field in the header.
     */
    private static final int ROWS_FIELD_WIDTH = 20;

    /**
     * Random-access file backing the output.
     */
    private final RandomAccessFile raf;

    /**
     * File channel used for streaming payload writes.
     */
    private final FileChannel channel;

    /**
     * Whether this writer emits a one-dimensional tensor.
     */
    private final boolean oneD;

    /**
     * Column count for two-dimensional tensors.
     */
    private final int cols;

    /**
     * Offset in the file where row-count digits begin.
     */
    private final long rowsFieldOffsetInFile;

    /**
     * Width of the row-count field in the header.
     */
    private final int rowsFieldWidth;

    /**
     * Reusable buffer for row payloads.
     */
    private final ByteBuffer rowBuffer;

    /**
     * Reusable buffer for scalar payloads.
     */
    private final ByteBuffer scalarBuffer;

    /**
     * Number of rows written.
     */
    private long rows;

    /**
     * Prevents double close.
     */
    private boolean closed;

    /**
     * Opens a two-dimensional float32 writer.
     *
     * @param path output file path
     * @param cols columns per row
     * @return writer
     * @throws IOException if initialization fails
     */
    static NpyFloat32Writer open2D(Path path, int cols) throws IOException {
        return new NpyFloat32Writer(path, false, cols);
    }

    /**
     * Opens a one-dimensional float32 writer.
     *
     * @param path output file path
     * @return writer
     * @throws IOException if initialization fails
     */
    static NpyFloat32Writer open1D(Path path) throws IOException {
        return new NpyFloat32Writer(path, true, -1);
    }

    /**
     * Creates the writer and emits the placeholder header.
     *
     * @param path output file path
     * @param oneD whether the tensor is one-dimensional
     * @param cols columns for two-dimensional tensors
     * @throws IOException if initialization fails
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
        ByteBuffer localRowBuffer;
        ByteBuffer localScalarBuffer;

        try {
            localRaf.setLength(0);

            String rowsPlaceholder = padLeft(0L, ROWS_FIELD_WIDTH);
            String shape = oneD
                    ? "(" + rowsPlaceholder + ",)"
                    : "(" + rowsPlaceholder + ", " + cols + ",)";
            String header = "{'descr': '<f4', 'fortran_order': False, 'shape': " + shape + ", }";

            int preamble = 10;
            int headerLenNoPad = header.length() + 1;
            int pad = (16 - ((preamble + headerLenNoPad) % 16)) % 16;
            String headerPadded = header + " ".repeat(pad) + "\n";
            byte[] headerBytes = headerPadded.getBytes(StandardCharsets.US_ASCII);

            int placeholderIndex = headerPadded.indexOf(rowsPlaceholder);
            if (placeholderIndex < 0) {
                throw new IOException("Internal error: rows placeholder not found in NPY header");
            }
            localRowsFieldOffsetInFile = (long) preamble + placeholderIndex;
            localRowsFieldWidth = rowsPlaceholder.length();

            localRaf.write(new byte[] { (byte) 0x93, 'N', 'U', 'M', 'P', 'Y' });
            localRaf.write(new byte[] { 1, 0 });

            int headerLength = headerBytes.length;
            if (headerLength > 0xFFFF) {
                throw new IOException("NPY header too large for v1.0: " + headerLength);
            }
            localRaf.write((byte) (headerLength & 0xFF));
            localRaf.write((byte) ((headerLength >>> 8) & 0xFF));
            localRaf.write(headerBytes);

            localScalarBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            localRowBuffer = oneD
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
        this.rowBuffer = localRowBuffer;
        this.scalarBuffer = localScalarBuffer;
    }

    /**
     * Writes one two-dimensional row.
     *
     * @param row row values
     * @throws IOException if writing fails
     */
    void writeRow(float[] row) throws IOException {
        if (oneD) {
            throw new IllegalStateException("This writer is 1D; use writeScalar()");
        }
        if (row.length != cols) {
            throw new IllegalArgumentException("Expected row length " + cols + " but got " + row.length);
        }

        rowBuffer.clear();
        for (float value : row) {
            rowBuffer.putFloat(value);
        }
        rowBuffer.flip();
        while (rowBuffer.hasRemaining()) {
            channel.write(rowBuffer);
        }
        rows++;
    }

    /**
     * Writes one scalar value.
     *
     * @param value scalar value
     * @throws IOException if writing fails
     */
    void writeScalar(float value) throws IOException {
        scalarBuffer.clear();
        scalarBuffer.putFloat(value);
        scalarBuffer.flip();
        while (scalarBuffer.hasRemaining()) {
            channel.write(scalarBuffer);
        }
        rows++;
    }

    /**
     * Returns the number of rows written so far.
     *
     * @return row count
     */
    long rows() {
        return rows;
    }

    /**
     * Patches the header and closes the file.
     *
     * @throws IOException if flushing or closing fails
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        IOException failure = null;
        try {
            String rowsText = padLeft(rows, rowsFieldWidth);
            byte[] rowsBytes = rowsText.getBytes(StandardCharsets.US_ASCII);
            raf.seek(rowsFieldOffsetInFile);
            raf.write(rowsBytes);
            channel.force(false);
        } catch (IOException e) {
            failure = e;
        }

        try {
            channel.close();
        } catch (IOException e) {
            failure = appendFailure(failure, e);
        }
        try {
            raf.close();
        } catch (IOException e) {
            failure = appendFailure(failure, e);
        }

        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Adds a suppressed close failure to an existing failure.
     *
     * @param failure existing failure
     * @param next next failure
     * @return combined failure
     */
    private static IOException appendFailure(IOException failure, IOException next) {
        if (failure == null) {
            return next;
        }
        failure.addSuppressed(next);
        return failure;
    }

    /**
     * Left-pads a row count with spaces for in-place NPY header patching.
     *
     * @param value numeric value
     * @param width field width
     * @return padded value
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
