package chess.nn.nnue;

import static chess.nn.nnue.UpstreamNetwork.*;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import chess.core.Position;
import chess.nn.nnue.UpstreamNetwork.Size;
import chess.nn.nnue.UpstreamNetwork.Variant;
import utility.Numbers;

/**
 * Signed LEB128 block reader.
 */
final class LebReader {

    /**
     * Parent cursor.
     */
    private final Cursor cursor;

    /**
     * Current block offset.
     */
    int offset;

    /**
     * End of block.
     */
    private final int end;

    /**
     * Creates a reader.
     *
     * @param cursor parent cursor
     * @param start start offset
     * @param end end offset
     */
    LebReader(Cursor cursor, int start, int end) {
        this.cursor = cursor;
        this.offset = start;
        this.end = end;
    }

    /**
     * Reads one signed value.
     *
     * @return decoded value
     * @throws IOException if data is truncated
     */
    int readSigned() throws IOException {
        int result = 0;
        int shift = 0;
        int b;
        do {
            if (offset >= end) {
                throw new IOException("Truncated Stockfish LEB128 block.");
            }
            b = cursor.data[offset++] & 0xff;
            result |= (b & 0x7f) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);

        if (shift < 32 && (b & 0x40) != 0) {
            result |= -(1 << shift);
        }
        return result;
    }

    /**
     * Completes the LEB block.
     *
     * @throws IOException if bytes remain unconsumed
     */
    void finish() throws IOException {
        if (offset != end) {
            throw new IOException("Unused bytes in Stockfish LEB128 block.");
        }
        cursor.offset = end;
    }
}
