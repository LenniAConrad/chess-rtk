package testing;

import static testing.TestSupport.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.nn.t5.BinLoader;

/**
 * Regression checks for the lightweight T5 model loader.
 */
public final class T5RegressionTest {
    /**
     * T5 binary magic written by the exporter.
     */
    private static final int MAGIC = 0x4C545454;

    /**
     * Prevents instantiation.
     */
    private T5RegressionTest() {
        // utility
    }

    /**
     * Runs T5 regression checks.
     *
     * @param args unused command-line arguments
     * @throws IOException if a temporary test file cannot be written
     */
    public static void main(String[] args) throws IOException {
        testNegativeSentencepieceVocabularyFailsAsIOException();
        testNegativeTensorCountFailsAsIOException();
        System.out.println("T5RegressionTest: all checks passed");
    }

    /**
     * Verifies malformed vocabulary counts fail before array allocation.
     *
     * @throws IOException if a temporary test file cannot be written
     */
    private static void testNegativeSentencepieceVocabularyFailsAsIOException() throws IOException {
        Path file = writeMalformedModel(-1, 0);
        try {
            IOException ex = expectLoadFailure(file);
            assertTrue(ex.getMessage().contains("SentencePiece vocabulary size"),
                    "negative SentencePiece vocabulary reports field name");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * Verifies malformed tensor counts fail before map allocation.
     *
     * @throws IOException if a temporary test file cannot be written
     */
    private static void testNegativeTensorCountFailsAsIOException() throws IOException {
        Path file = writeMalformedModel(0, -1);
        try {
            IOException ex = expectLoadFailure(file);
            assertTrue(ex.getMessage().contains("tensor count"),
                    "negative tensor count reports field name");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * Loads a malformed model and returns the expected checked exception.
     *
     * @param file model path
     * @return thrown I/O exception
     * @throws IOException when loading unexpectedly succeeds
     */
    private static IOException expectLoadFailure(Path file) throws IOException {
        try {
            BinLoader.load(file.toString());
        } catch (IOException ex) {
            return ex;
        }
        throw new AssertionError("malformed T5 model unexpectedly loaded");
    }

    /**
     * Writes enough of a T5 header to reach the allocation-driving fields under
     * test.
     *
     * @param sentencepieceVocab vocabulary row count to write
     * @param tensorCount tensor count to write after an empty vocabulary
     * @return temporary model path
     * @throws IOException on write failure
     */
    private static Path writeMalformedModel(int sentencepieceVocab, int tensorCount) throws IOException {
        Path file = Files.createTempFile("crtk-t5-loader-", ".bin");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(MAGIC);
            out.writeInt(1);
            writeString(out, "malformed");
            for (int value : new int[] { 32, 8, 8, 16, 1, 1, 1, 4, 8, 0, 1, 0, 2 }) {
                out.writeInt(value);
            }
            out.writeInt(0);
            out.writeInt(0);
            out.writeFloat(1.0e-6f);
            out.writeInt(sentencepieceVocab);
            if (sentencepieceVocab == 0) {
                out.writeInt(tensorCount);
            }
        }
        return file;
    }

    /**
     * Writes one length-prefixed UTF-8 string.
     *
     * @param out output stream
     * @param value string value
     * @throws IOException on write failure
     */
    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
