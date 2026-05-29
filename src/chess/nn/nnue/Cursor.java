package chess.nn.nnue;

import static chess.nn.nnue.UpstreamNetwork.*;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


/**
 * Little-endian byte cursor with Stockfish LEB128 helpers.
 */
final class Cursor {

    /**
     * Data.
     */
    final byte[] data;

    /**
     * Current offset.
     */
    int offset;

    /**
     * Creates a cursor.
     *
     * @param data source bytes
     */
    Cursor(byte[] data) {
        this.data = data;
    }

    /**
     * Reads a signed little-endian int32.
     *
     * @return value
     */
    int readInt() {
        require(4);
        int value = (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
        offset += 4;
        return value;
    }

    /**
     * Reads a signed little-endian int16.
     *
     * @return value
     */
    short readShort() {
        require(2);
        int value = (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
        offset += 2;
        return (short) value;
    }

    /**
     * Reads one byte.
     *
     * @return byte value
     */
    byte readByte() {
        require(1);
        return data[offset++];
    }

    /**
     * Reads a byte array.
     *
     * @param length byte count
     * @return byte array
     */
    byte[] readByteArray(long length) {
        int n = checkedLength(length, "byte array");
        require(n);
        byte[] out = Arrays.copyOfRange(data, offset, offset + n);
        offset += n;
        return out;
    }

    /**
     * Reads a string.
     *
     * @param length byte length
     * @return decoded string
     */
    String readString(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative string length.");
        }
        require(length);
        String out = new String(data, offset, length, StandardCharsets.UTF_8);
        offset += length;
        return out;
    }

    /**
     * Reads a signed LEB128-compressed short array.
     *
     * @param count array length
     * @return array
     * @throws IOException if compressed data is invalid
     */
    short[] readLebShortArray(long count) throws IOException {
        int n = checkedLength(count, "LEB short array");
        short[] out = new short[n];
        LebReader reader = beginLeb();
        for (int i = 0; i < n; i++) {
            out[i] = (short) reader.readSigned();
        }
        reader.finish();
        return out;
    }

    /**
     * Reads a signed LEB128-compressed int array.
     *
     * @param count array length
     * @return array
     * @throws IOException if compressed data is invalid
     */
    int[] readLebIntArray(long count) throws IOException {
        int n = checkedLength(count, "LEB int array");
        int[] out = new int[n];
        LebReader reader = beginLeb();
        for (int i = 0; i < n; i++) {
            out[i] = reader.readSigned();
        }
        reader.finish();
        return out;
    }

    /**
     * Returns whether unread bytes remain.
     *
     * @return true when remaining
     */
    boolean hasRemaining() {
        return offset != data.length;
    }

    /**
     * Starts a LEB128 block.
     *
     * @return reader
     * @throws IOException if the marker is missing
     */
    private LebReader beginLeb() throws IOException {
        require(LEB128_MAGIC.length);
        for (byte b : LEB128_MAGIC) {
            if (data[offset++] != b) {
                throw new IOException("Missing Stockfish LEB128 marker.");
            }
        }
        int byteCount = readInt();
        if (byteCount < 0) {
            throw new IOException("Negative Stockfish LEB128 block length.");
        }
        require(byteCount);
        return new LebReader(this, offset, offset + byteCount);
    }

    /**
     * Requires bytes to be available.
     *
     * @param length byte count
     */
    private void require(int length) {
        if (length < 0 || offset + length > data.length) {
            throw new BufferUnderflowException();
        }
    }
}
