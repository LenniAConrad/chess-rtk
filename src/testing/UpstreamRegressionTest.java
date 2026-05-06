package testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.core.Move;
import chess.core.Position;
import chess.nn.nnue.Model;
import chess.nn.nnue.UpstreamNetwork;

/**
 * Regression checks for Stockfish-compatible NNUE loading and inference.
 */
public final class UpstreamRegressionTest {

    /**
     * Internal upstream feature helper.
     */
    private static final String UPSTREAM_FEATURES_CLASS = "chess.nn.nnue.UpstreamFeatures";

    /**
     * Stockfish NNUE file version.
     */
    private static final int UPSTREAM_VERSION = 0x7AF32F20;

    /**
     * White color id.
     */
    private static final int WHITE = 0;

    /**
     * Black color id.
     */
    private static final int BLACK = 1;

    /**
     * HalfKAv2_hm feature dimensions.
     */
    private static final int HALF_KA_DIMENSIONS = 64 * (11 * 64) / 2;

    /**
     * HalfKAv2_hm feature hash.
     */
    private static final int HALF_KA_HASH = 0x7f234cb8;

    /**
     * FullThreats feature hash.
     */
    private static final int FULL_THREATS_HASH = 0x8f234cb8;

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
    private UpstreamRegressionTest() {
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
        testCurrentSmallNetIncrementalSearchState();
        System.out.println("UpstreamRegressionTest: all checks passed");
    }

    /**
     * Verifies current Stockfish feature generation exposes expected active feature
     * groups.
     */
    private static void testFeatureGeneration() {
        int[] board = upstreamBoard(ONE_PAWN);
        assertEquals(3, upstreamPieceCount(board), "piece count");
        assertEquals(3, upstreamActiveHalfKa(board, WHITE).length,
                "HalfKAv2_hm white active count");
        assertEquals(3, upstreamActiveHalfKa(board, BLACK).length,
                "HalfKAv2_hm black active count");
        assertEquals(0, upstreamActiveThreats(
                board, WHITE, UpstreamNetwork.Variant.CURRENT).length,
                "quiet current FullThreats white active count");

        int[] threatBoard = upstreamBoard(ONE_THREAT);
        assertPositive(upstreamActiveThreats(
                threatBoard, WHITE, UpstreamNetwork.Variant.CURRENT).length,
                "current FullThreats white threat count");
    }

    /**
     * Writes and loads a synthetic current-development Stockfish small net.
     *
     * @throws IOException if temp-file IO fails
     */
    private static void testCurrentSmallNetLoadAndEvaluate() throws IOException {
        byte[] net = writeSyntheticCurrentSmallNet();
        Path temp = Files.createTempFile("crtk-upstream-nnue-test-", ".nnue");
        try {
            Files.write(temp, net);

            UpstreamNetwork upstream = UpstreamNetwork.load(temp);
            UpstreamNetwork.Info info = upstream.info();
            assertEquals(UpstreamNetwork.Variant.CURRENT, info.variant(), "variant");
            assertEquals(UpstreamNetwork.Size.SMALL, info.size(), "size");
            assertEquals(6, upstream.predict(ONE_PAWN).centipawns(), "direct upstream prediction");

            Model model = Model.load(temp);
            assertEquals(6, model.evaluateCentipawns(ONE_PAWN), "Model auto-detected upstream prediction");
            assertNotNull(model.upstreamInfo(), "upstream metadata");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Verifies the incremental search-state path matches direct evaluation on the
     * synthetic current small net.
     *
     * @throws IOException if temp-file IO fails
     */
    private static void testCurrentSmallNetIncrementalSearchState() throws IOException {
        byte[] net = writeSyntheticCurrentSmallNet();
        Path temp = Files.createTempFile("crtk-upstream-nnue-search-test-", ".nnue");
        try {
            Files.write(temp, net);
            UpstreamNetwork upstream = UpstreamNetwork.load(temp);
            assertIncrementalMove(upstream,
                    "4k3/8/8/8/8/8/3P4/4K3 w - - 0 1",
                    "e1f2",
                    "upstream incremental king move");
            assertIncrementalMove(upstream,
                    "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1",
                    "e4d5",
                    "upstream incremental capture");
            assertIncrementalMove(upstream,
                    "4k2r/8/8/8/8/8/8/R3K2R w KQ - 0 1",
                    "e1g1",
                    "upstream incremental castle");
            assertIncrementalMove(upstream,
                    "6k1/4P3/8/8/8/8/8/4K3 w - - 0 1",
                    "e7e8q",
                    "upstream incremental promotion");
            assertIncrementalNullMove(upstream,
                    "4k3/8/8/8/8/8/3P4/4K3 w - - 0 1",
                    "upstream incremental null move");
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
        UpstreamNetwork.Variant variant = UpstreamNetwork.Variant.CURRENT;
        UpstreamNetwork.Size size = UpstreamNetwork.Size.SMALL;
        int transformed = 128;
        int psqDimensions = HALF_KA_DIMENSIONS;
        int l2 = 15;
        int l3 = 32;

        int[] board = upstreamBoard(ONE_PAWN);
        int whiteFeature = upstreamActiveHalfKa(board, WHITE)[1];

        short[] biases = new short[transformed];
        short[] weights = new short[psqDimensions * transformed];
        int[] psqt = new int[psqDimensions * 8];
        weights[whiteFeature * transformed] = 64;
        weights[(whiteFeature * transformed) + 64] = 64;
        psqt[whiteFeature * 8] = 160;

        byte[] description = "synthetic-current-small".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        writeInt(file, UPSTREAM_VERSION);
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
    private static int networkHash(UpstreamNetwork.Variant variant, UpstreamNetwork.Size size) {
        int transformed = size == UpstreamNetwork.Size.BIG ? 1024 : 128;
        int l2 = size == UpstreamNetwork.Size.BIG && variant == UpstreamNetwork.Variant.CURRENT ? 31 : 15;
        return featureHash(variant, size) ^ archHash(transformed, l2, 32);
    }

    /**
     * Computes a Stockfish feature-transformer hash.
     *
     * @param variant variant
     * @param size size
     * @return hash
     */
    private static int featureHash(UpstreamNetwork.Variant variant, UpstreamNetwork.Size size) {
        int transformed = size == UpstreamNetwork.Size.BIG ? 1024 : 128;
        int base;
        if (size == UpstreamNetwork.Size.BIG) {
            base = variant == UpstreamNetwork.Variant.CURRENT
                    ? combineHashes(FULL_THREATS_HASH, HALF_KA_HASH)
                    : FULL_THREATS_HASH;
        } else {
            base = HALF_KA_HASH;
        }
        return base ^ (transformed * 2);
    }

    /**
     * Converts a position into the internal Stockfish board representation.
     *
     * @param position position
     * @return board
     */
    private static int[] upstreamBoard(Position position) {
        return (int[]) invokeUpstreamFeatures("board", new Class<?>[] { Position.class }, position);
    }

    /**
     * Counts pieces through the internal upstream feature helper.
     *
     * @param board board
     * @return count
     */
    private static int upstreamPieceCount(int[] board) {
        return (Integer) invokeUpstreamFeatures("pieceCount", new Class<?>[] { int[].class }, board);
    }

    /**
     * Returns active HalfKAv2_hm features through the internal helper.
     *
     * @param board board
     * @param perspective perspective color
     * @return active features
     */
    private static int[] upstreamActiveHalfKa(int[] board, int perspective) {
        return (int[]) invokeUpstreamFeatures("activeHalfKa", new Class<?>[] { int[].class, int.class },
                board, perspective);
    }

    /**
     * Returns active FullThreats features through the internal helper.
     *
     * @param board board
     * @param perspective perspective color
     * @param variant upstream architecture variant
     * @return active features
     */
    private static int[] upstreamActiveThreats(int[] board, int perspective, UpstreamNetwork.Variant variant) {
        return (int[]) invokeUpstreamFeatures("activeThreats",
                new Class<?>[] { int[].class, int.class, UpstreamNetwork.Variant.class },
                board, perspective, variant);
    }

    /**
     * Invokes a package-private upstream feature method.
     *
     * @param name method name
     * @param parameterTypes method parameter types
     * @param args method arguments
     * @return method result
     */
    private static Object invokeUpstreamFeatures(String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> type = Class.forName(UPSTREAM_FEATURES_CLASS);
            Method method = type.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (InvocationTargetException ex) {
            rethrowCause(ex);
            throw new AssertionError("unreachable");
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not invoke " + UPSTREAM_FEATURES_CLASS + "." + name, ex);
        }
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
     * Verifies one played move against the incremental search-state path.
     *
     * @param upstream network to test
     * @param fen root FEN
     * @param moveUci legal move in UCI
     * @param label assertion label prefix
     */
    private static void assertIncrementalMove(
            UpstreamNetwork upstream,
            String fen,
            String moveUci,
            String label) {
        Position position = new Position(fen);
        Model.SearchState state = upstream.newSearchState(position, 4);
        assertNotNull(state, label + " search state");
        short move = Move.parse(moveUci);
        assertTrue(position.isLegalMove(move), label + " legal move");
        Position.State undo = new Position.State();
        position.play(move, undo);
        state.movePlayed(position, move, undo, 1);
        assertEquals(upstream.predict(position).centipawns(), state.evaluate(position, 1), label + " evaluation parity");
    }

    /**
     * Verifies one null move against the incremental search-state path.
     *
     * @param upstream network to test
     * @param fen root FEN
     * @param label assertion label prefix
     */
    private static void assertIncrementalNullMove(
            UpstreamNetwork upstream,
            String fen,
            String label) {
        Position position = new Position(fen);
        Model.SearchState state = upstream.newSearchState(position, 4);
        assertNotNull(state, label + " search state");
        Position.State undo = new Position.State();
        position.playNull(undo);
        state.nullMovePlayed(1);
        assertEquals(upstream.predict(position).centipawns(), state.evaluate(position, 1), label + " evaluation parity");
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
     * Preserves the original failure thrown by a reflected method.
     *
     * @param ex invocation wrapper
     */
    private static void rethrowCause(InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new AssertionError(cause);
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
     * Verifies a boolean condition.
     *
     * @param condition condition to verify
     * @param label label
     */
    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + ": expected true");
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
