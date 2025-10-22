package chess.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Appends JSON objects to a file that stores a single top-level JSON array.
 * <p>
 * Behavior:
 * <ul>
 * <li>If the file is empty or doesn’t exist yet, it writes:
 * <code>[{...}, {...}]</code>.</li>
 * <li>If the file already contains a JSON array and ends with <code>]</code>,
 * it removes that trailing <code>]</code>, conditionally writes a comma (when
 * the array isn’t empty),
 * appends all provided objects separated by commas, and then writes a closing
 * <code>]</code>.</li>
 * <li>Whitespace at the end of the file is ignored when checking for the
 * trailing bracket.</li>
 * </ul>
 * <b>NOTE:</b> Each element in {@code jsonObjects} must be a complete JSON
 * object/string
 * (e.g., <code>{"k":1}</code>) without a trailing comma. This method does not
 * validate JSON.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Writer {

    /**
     * Used for preventing instantiation of this utility class.
     */
    private Writer() {
        // non-instantiable
    }

    /**
     * Used for appending JSON values to a JSON-array file using a single
     * open/seek/write pass.
     *
     * @param file        path to the target JSON file (created if missing)
     * @param jsonObjects list of JSON values (objects/arrays/strings) to append
     * @throws IOException if an I/O error occurs
     */
    public static void appendJsonObjects(Path file, List<String> jsonObjects) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(jsonObjects, "jsonObjects");
        if (jsonObjects.isEmpty())
            return;

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            long len = raf.length();

            if (len == 0) {
                raf.seek(0);
                raf.write('[');
                writeJoined(raf, jsonObjects);
                raf.write(']');
                raf.getChannel().force(true);
                return;
            }

            long lastPos = lastNonWhitespacePos(raf, len);
            if (lastPos < 0) {
                raf.setLength(0);
                raf.seek(0);
                raf.write('[');
                writeJoined(raf, jsonObjects);
                raf.write(']');
                raf.getChannel().force(true);
                return;
            }

            raf.seek(lastPos);
            int last = raf.read();

            boolean hadClosingBracket = (last == ']');
            if (hadClosingBracket) {
                raf.setLength(lastPos);
                len = lastPos;
            }

            long beforePos = lastNonWhitespacePos(raf, raf.length());
            boolean needComma = false;
            if (beforePos >= 0) {
                raf.seek(beforePos);
                int c = raf.read();
                needComma = (c != '[');
            } else {
                raf.setLength(0);
            }

            if (raf.length() == 0) {
                raf.seek(0);
                raf.write('[');
                writeJoined(raf, jsonObjects);
                raf.write(']');
                raf.getChannel().force(true);
                return;
            }

            raf.seek(raf.length());

            if (needComma) {
                raf.write(',');
            }
            
            writeJoined(raf, jsonObjects);
            raf.write(']');
            raf.getChannel().force(true);
        }
    }

    /**
     * Used for appending JSON values to a JSON-array file with a varargs
     * convenience overload.
     *
     * @param file        path to the target JSON file (created if missing)
     * @param jsonObjects JSON values (objects/arrays/strings) to append
     * @throws IOException if an I/O error occurs
     */
    public static void appendJsonObjects(Path file, String... jsonObjects) throws IOException {
        appendJsonObjects(file, List.of(jsonObjects));
    }

    /**
     * Used for writing multiple JSON fragments to a file joined by commas.
     *
     * @param raf   open random access file handle
     * @param parts list of JSON fragments to write
     * @throws IOException if an I/O error occurs
     */
    private static void writeJoined(RandomAccessFile raf, List<String> parts) throws IOException {
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                raf.write(',');
            }
            byte[] bytes = parts.get(i).getBytes(StandardCharsets.UTF_8);
            raf.write(bytes);
        }
    }

    /**
     * Used for finding the last non-whitespace byte position in a file.
     *
     * @param raf    open random access file handle
     * @param length length of the file in bytes
     * @return position of the last non-whitespace byte, or -1 if none exists
     * @throws IOException if an I/O error occurs
     */
    private static long lastNonWhitespacePos(RandomAccessFile raf, long length) throws IOException {
        if (length <= 0)
            return -1;
        long pos = length - 1;
        while (pos >= 0) {
            raf.seek(pos);
            int b;
            try {
                b = raf.readUnsignedByte();
            } catch (EOFException e) {
                b = -1;
            }
            if (b == -1) {
                return -1;
            }
            if (!isWhitespace((byte) b)) {
                return pos;
            }
            pos--;
        }
        return -1;
    }

    /**
     * Used for checking whether a byte represents a whitespace character.
     *
     * @param b byte value to check
     * @return true if whitespace, false otherwise
     */
    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }
}
