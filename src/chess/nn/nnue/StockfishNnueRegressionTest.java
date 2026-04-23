package chess.nn.nnue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.core.Position;

/**
 * Regression checks for Stockfish-compatible NNUE loading and inference.
 */
public final class StockfishNnueRegressionTest {

    /**
     * Synthetic position with two kings and one pawn.
     */
    private static final Position ONE_PAWN = new Position("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1");

    /**
     * Position with a white knight attacking a black rook.
     */
    private static final Position ONE_THREAT = new Position("4k3/8/8/5r2/3N4/8/8/4K3 w - - 0 1");

    /**
     * Prevents instantiation.
     */
    private StockfishNnueRegressionTest() {
        // utility
    }

    /**
     * Runs regression checks.
     *
     * @param args ignored
     * @throws IOException if temp-file IO fails
     */
    public static void main(String[] args) throws IOException {
        testFeatureGeneration();
        testCurrentSmallNetLoadAndEvaluate();
        System.out.println("StockfishNnueRegressionTest: all checks passed");
    }

    /**
     * Verifies current Stockfish feature generation exposes expected active feature
     * groups.
     */
    private static void testFeatureGeneration() {
        int[] board = StockfishNnueFeatures.board(ONE_PAWN);
        assertEquals(3, StockfishNnueFeatures.pieceCount(board), "piece count");
        assertEquals(3, StockfishNnueFeatures.activeHalfKa(board, StockfishNnueFeatures.WHITE).length,
                "HalfKAv2_hm white active count");
        assertEquals(3, StockfishNnueFeatures.activeHalfKa(board, StockfishNnueFeatures.BLACK).length,
                "HalfKAv2_hm black active count");
        assertEquals(0, StockfishNnueFeatures.activeThreats(
                board, StockfishNnueFeatures.WHITE, StockfishNnueNetwork.Variant.CURRENT).length,
                "quiet current FullThreats white active count");

        int[] threatBoard = StockfishNnueFeatures.board(ONE_THREAT);
        assertPositive(StockfishNnueFeatures.activeThreats(
                threatBoard, StockfishNnueFeatures.WHITE, StockfishNnueNetwork.Variant.CURRENT).length,
                "current FullThreats white threat count");
    }

    /**
     * Writes and loads a synthetic current-development Stockfish small net.
     *
     * @throws IOException if temp-file IO fails
     */
    private static void testCurrentSmallNetLoadAndEvaluate() throws IOException {
        byte[] net = writeSyntheticCurrentSmallNet();
        Path temp = Files.createTempFile("crtk-stockfish-nnue-test-", ".nnue");
        try {
            Files.write(temp, net);

            StockfishNnueNetwork stockfish = StockfishNnueNetwork.load(temp);
            StockfishNnueNetwork.Info info = stockfish.info();
            assertEquals(StockfishNnueNetwork.Variant.CURRENT, info.variant(), "variant");
            assertEquals(StockfishNnueNetwork.Size.SMALL, info.size(), "size");
            assertEquals(6, stockfish.predict(ONE_PAWN).centipawns(), "direct Stockfish prediction");

            Model model = Model.load(temp);
            assertEquals(6, model.evaluateCentipawns(ONE_PAWN), "Model auto-detected Stockfish prediction");
            assertNotNull(model.stockfishInfo(), "Stockfish metadata");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Writes a synthetic Stockfish current small-net file.
     *
     * @return file bytes
     * @throws IOException if encoding fails
     */
    private static byte[] writeSyntheticCurrentSmallNet() throws IOException {
        StockfishNnueNetwork.Variant variant = StockfishNnueNetwork.Variant.CURRENT;
        StockfishNnueNetwork.Size size = StockfishNnueNetwork.Size.SMALL;
        int transformed = 128;
        int psqDimensions = StockfishNnueFeatures.HALF_KA_DIMENSIONS;
        int l2 = 15;
        int l3 = 32;

        int[] board = StockfishNnueFeatures.board(ONE_PAWN);
        int whiteFeature = StockfishNnueFeatures.activeHalfKa(board, StockfishNnueFeatures.WHITE)[1];

        short[] biases = new short[transformed];
        short[] weights = new short[psqDimensions * transformed];
        int[] psqt = new int[psqDimensions * 8];
        weights[whiteFeature * transformed] = 64;
        weights[(whiteFeature * transformed) + 64] = 64;
        psqt[whiteFeature * 8] = 160;

        byte[] description = "synthetic-current-small".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        writeInt(file, StockfishNnueNetwork.VERSION);
        writeInt(file, networkHash(variant, size));
        writeInt(file, description.length);
        file.write(description);

        writeInt(file, featureHash(variant, size));
        writeLeb(file, biases);
        writeLeb(file, weights);
        writeLeb(file, psqt);

        for (int bucket = 0; bucket < 8; bucket++) {
            writeInt(file, archHash(transformed, l2, l3));
            writeAffine(file, transformed, l2 + 1, new int[l2 + 1], fc0Weights());
            writeAffine(file, l2 * 2, l3, new int[l3], fc1Weights());
            writeAffine(file, l3, 1, new int[1], fc2Weights());
        }
        return file.toByteArray();
    }

    /**
     * Returns FC0 weights for the synthetic net.
     *
     * @return row-major weights
     */
    private static byte[] fc0Weights() {
        byte[] weights = new byte[16 * 128];
        weights[0] = 64;
        return weights;
    }

    /**
     * Returns FC1 weights for the synthetic net.
     *
     * @return row-major weights
     */
    private static byte[] fc1Weights() {
        byte[] weights = new byte[32 * 32];
        weights[15] = 8;
        return weights;
    }

    /**
     * Returns FC2 weights for the synthetic net.
     *
     * @return row-major weights
     */
    private static byte[] fc2Weights() {
        byte[] weights = new byte[32];
        weights[0] = 16;
        return weights;
    }

    /**
     * Writes an affine layer.
     *
     * @param out output stream
     * @param input input dimensions
     * @param output output dimensions
     * @param biases biases
     * @param weights padded row-major weights
     * @throws IOException if writing fails
     */
    private static void writeAffine(
            ByteArrayOutputStream out,
            int input,
            int output,
            int[] biases,
            byte[] weights) throws IOException {
        int padded = ((input + 31) / 32) * 32;
        assertEquals(output, biases.length, "affine bias length");
        assertEquals(output * padded, weights.length, "affine weight length");
        for (int bias : biases) {
            writeInt(out, bias);
        }
        out.write(weights);
    }

    /**
     * Computes a Stockfish network hash.
     *
     * @param variant variant
     * @param size size
     * @return hash
     */
    private static int networkHash(StockfishNnueNetwork.Variant variant, StockfishNnueNetwork.Size size) {
        int transformed = size == StockfishNnueNetwork.Size.BIG ? 1024 : 128;
        int l2 = size == StockfishNnueNetwork.Size.BIG && variant == StockfishNnueNetwork.Variant.CURRENT ? 31 : 15;
        return featureHash(variant, size) ^ archHash(transformed, l2, 32);
    }

    /**
     * Computes a Stockfish feature-transformer hash.
     *
     * @param variant variant
     * @param size size
     * @return hash
     */
    private static int featureHash(StockfishNnueNetwork.Variant variant, StockfishNnueNetwork.Size size) {
        int transformed = size == StockfishNnueNetwork.Size.BIG ? 1024 : 128;
        int base;
        if (size == StockfishNnueNetwork.Size.BIG) {
            base = variant == StockfishNnueNetwork.Variant.CURRENT
                    ? combineHashes(StockfishNnueFeatures.FULL_THREATS_HASH, StockfishNnueFeatures.HALF_KA_HASH)
                    : StockfishNnueFeatures.FULL_THREATS_HASH;
        } else {
            base = StockfishNnueFeatures.HALF_KA_HASH;
        }
        return base ^ (transformed * 2);
    }

    /**
     * Computes a Stockfish architecture hash.
     *
     * @param transformed transformed dimensions
     * @param l2 first hidden size
     * @param l3 second hidden size
     * @return hash
     */
    private static int archHash(int transformed, int l2, int l3) {
        int hash = 0xEC42E90D;
        hash ^= transformed * 2;
        hash = affineHash(hash, l2 + 1);
        hash = clippedHash(hash);
        hash = affineHash(hash, l3);
        hash = clippedHash(hash);
        hash = affineHash(hash, 1);
        return hash;
    }

    /**
     * Combines feature hashes.
     *
     * @param values hashes
     * @return combined hash
     */
    private static int combineHashes(int... values) {
        int hash = 0;
        for (int value : values) {
            hash = (hash << 1) | (hash >>> 31);
            hash ^= value;
        }
        return hash;
    }

    /**
     * Computes an affine-layer hash.
     *
     * @param previous previous hash
     * @param output output dimensions
     * @return hash
     */
    private static int affineHash(int previous, int output) {
        int hash = 0xCC03DAE4;
        hash += output;
        hash ^= previous >>> 1;
        hash ^= previous << 31;
        return hash;
    }

    /**
     * Computes a clipped-ReLU hash.
     *
     * @param previous previous hash
     * @return hash
     */
    private static int clippedHash(int previous) {
        return 0x538D24C7 + previous;
    }

    /**
     * Writes a short array as a Stockfish LEB128 block.
     *
     * @param out output
     * @param values values
     * @throws IOException if writing fails
     */
    private static void writeLeb(ByteArrayOutputStream out, short[] values) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (short value : values) {
            writeSignedLeb(payload, value);
        }
        writeLebPayload(out, payload);
    }

    /**
     * Writes an int array as a Stockfish LEB128 block.
     *
     * @param out output
     * @param values values
     * @throws IOException if writing fails
     */
    private static void writeLeb(ByteArrayOutputStream out, int[] values) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (int value : values) {
            writeSignedLeb(payload, value);
        }
        writeLebPayload(out, payload);
    }

    /**
     * Writes the LEB block wrapper.
     *
     * @param out output
     * @param payload encoded payload
     * @throws IOException if writing fails
     */
    private static void writeLebPayload(ByteArrayOutputStream out, ByteArrayOutputStream payload) throws IOException {
        out.write("COMPRESSED_LEB128".getBytes(StandardCharsets.US_ASCII));
        writeInt(out, payload.size());
        payload.writeTo(out);
    }

    /**
     * Writes one signed LEB128 value.
     *
     * @param out output
     * @param value value
     */
    private static void writeSignedLeb(ByteArrayOutputStream out, int value) {
        boolean more;
        do {
            int b = value & 0x7f;
            value >>= 7;
            more = ((b & 0x40) == 0 && value != 0) || ((b & 0x40) != 0 && value != -1);
            if (more) {
                b |= 0x80;
            }
            out.write(b);
        } while (more);
    }

    /**
     * Writes a little-endian int32.
     *
     * @param out output
     * @param value value
     */
    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    /**
     * Verifies equality for ints.
     *
     * @param expected expected
     * @param actual actual
     * @param label label
     */
    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies equality for objects.
     *
     * @param expected expected
     * @param actual actual
     * @param label label
     */
    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies a positive integer.
     *
     * @param actual actual value
     * @param label label
     */
    private static void assertPositive(int actual, String label) {
        if (actual <= 0) {
            throw new AssertionError(label + ": expected positive, got " + actual);
        }
    }

    /**
     * Verifies non-null value.
     *
     * @param value value
     * @param label label
     */
    private static void assertNotNull(Object value, String label) {
        if (value == null) {
            throw new AssertionError(label + ": expected non-null");
        }
    }
}
