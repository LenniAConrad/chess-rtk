package testing;

import static testing.TestSupport.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import chess.core.Move;
import chess.core.Position;
import chess.nn.lc0.bt4.Architecture;
import chess.nn.lc0.bt4.BinLoader;
import chess.nn.lc0.bt4.Encoder;
import chess.nn.lc0.bt4.InputFormat;
import chess.nn.lc0.bt4.Model;
import chess.nn.lc0.bt4.Network;
import chess.nn.lc0.bt4.PolicyEncoder;

/**
 * Regression checks for LCZero BT4 architecture helpers.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S2187")
public final class BT4RegressionTest {

    /**
     * Standard chess start.
     */
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Prevents instantiation.
     */
    private BT4RegressionTest() {
        // utility
    }

    /**
     * Runs all checks.
     *
     * @param args ignored
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    public static void main(String[] args) throws IOException {
        testEncoderClassicalPlanes();
        testEncoderModernAuxPlanes();
        testAttentionPolicyMap();
        testModelLoadAndForward();
        testNativeBackendsWhenAvailable();
        System.out.println("BT4RegressionTest: all checks passed");
    }

    /**
     * Verifies classical 112-plane piece placement.
     */
    private static void testEncoderClassicalPlanes() {
        Encoder.EncodedInput encoded = Encoder.encode(new Position(START_FEN), InputFormat.CLASSICAL_112);
        float[] planes = encoded.planes();
        assertEquals(Encoder.INPUT_CHANNELS * Encoder.TOKENS, planes.length, "classical plane length");
        assertClose(1.0f, planes[0 * 64 + 8], "white a2 pawn");
        assertClose(1.0f, planes[3 * 64], "white a1 rook");
        assertClose(1.0f, planes[6 * 64 + 48], "black a7 pawn as opponent");
        assertClose(1.0f, planes[11 * 64 + 60], "black e8 king as opponent");
        assertClose(1.0f, planes[(Encoder.AUX_BASE + 7) * 64], "edge plane");
    }

    /**
     * Verifies BT4 modern castling and en-passant auxiliary planes.
     */
    private static void testEncoderModernAuxPlanes() {
        Encoder.EncodedInput start = Encoder.encode(new Position(START_FEN), InputFormat.BT4_CANONICAL_112);
        float[] planes = start.planes();
        assertEquals(0, start.transform(), "start transform blocked by castling");
        assertClose(1.0f, planes[Encoder.AUX_BASE * 64], "white queenside rook");
        assertClose(1.0f, planes[Encoder.AUX_BASE * 64 + 56], "black queenside rook");
        assertClose(1.0f, planes[(Encoder.AUX_BASE + 1) * 64 + 7], "white kingside rook");
        assertClose(1.0f, planes[(Encoder.AUX_BASE + 1) * 64 + 63], "black kingside rook");

        Position enPassant = new Position("k7/8/8/3Pp3/8/8/8/7K w - e6 0 1");
        float[] epPlanes = Encoder.encode(enPassant, InputFormat.BT4_CANONICAL_112).planes();
        assertClose(1.0f, epPlanes[(Encoder.AUX_BASE + 4) * 64 + 44], "en-passant e6 plane");
    }

    /**
     * Verifies generated LC0 attention-policy mapping.
     */
    private static void testAttentionPolicyMap() {
        int[] map = PolicyEncoder.compressedByInternalMap();
        int mapped = 0;
        for (int value : map) {
            if (value >= 0) {
                mapped++;
            }
        }
        assertEquals(PolicyEncoder.POLICY_SIZE, mapped, "attention policy mapped entries");

        Position start = new Position(START_FEN);
        assertTrue(PolicyEncoder.compressedPolicyIndex(start, Move.parse("e2e4")) >= 0, "e2e4 policy index");
        assertTrue(PolicyEncoder.compressedPolicyIndex(start, Move.parse("g1f3")) >= 0, "g1f3 policy index");

        Position promo = new Position("8/P7/8/8/8/8/8/k6K w - - 0 1");
        assertEquals(1792, PolicyEncoder.compressedPolicyIndex(promo, Move.parse("a7a8n")), "a7a8n policy index");
        assertEquals(1793, PolicyEncoder.compressedPolicyIndex(promo, Move.parse("a7a8b")), "a7a8b policy index");
        assertEquals(1794, PolicyEncoder.compressedPolicyIndex(promo, Move.parse("a7a8r")), "a7a8r policy index");
    }

    /**
     * Verifies the Java reference forward path on a tiny deterministic network.
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private static void testModelLoadAndForward() throws IOException {
        Architecture architecture = syntheticArchitecture();
        Network.Weights weights = syntheticWeights(architecture);
        Path temp = Files.createTempFile("crtk-bt4-test-", ".bin");
        try {
            writeWeights(temp, weights);
            assertEquals(architecture, BinLoader.loadArchitecture(temp), "loaded architecture metadata");
            withBt4Backend("cpu", () -> {
                try (Model model = Model.load(temp)) {
                    assertEquals("test-bt4", model.info().name(), "loaded model name");
                    assertEquals(1, model.info().encoderLayers(), "loaded encoder count");
                    Position start = new Position(START_FEN);
                    Network.Prediction prediction = model.predict(start);
                    Encoder.EncodedInput encodedInput = Encoder.encode(start, architecture.inputFormat());
                    Network.Prediction encoded = model.predictEncoded(encodedInput.planes());
                    assertPredictionClose(prediction, encoded, "model predictEncoded parity");
                    List<Network.Prediction> positionBatch = model.predictBatch(List.of(start, start));
                    assertEquals(2, positionBatch.size(), "model predictBatch size");
                    assertPredictionClose(prediction, positionBatch.get(0), "model predictBatch first");
                    assertPredictionClose(prediction, positionBatch.get(1), "model predictBatch second");
                    List<Network.Prediction> encodedBatch = model.predictEncodedBatch(
                            List.of(encodedInput.planes(), encodedInput.planes()));
                    assertEquals(2, encodedBatch.size(), "model predictEncodedBatch size");
                    assertPredictionClose(prediction, encodedBatch.get(0), "model predictEncodedBatch first");
                    assertPredictionClose(prediction, encodedBatch.get(1), "model predictEncodedBatch second");
                    assertForwardOutputs(start, prediction);
                }
                try (Network direct = Network.create(weights); Network loaded = Network.load(temp)) {
                    assertEquals(Network.BACKEND_CPU, loaded.backend(), "forced CPU backend");
                    assertPredictionClose(
                            direct.predict(new Position(START_FEN)),
                            loaded.predict(new Position(START_FEN)),
                            "loaded network parity");
                    Position start = new Position(START_FEN);
                    Network.Prediction single = direct.predict(start);
                    List<Network.Prediction> directBatch = direct.predictBatch(List.of(start, start));
                    assertEquals(2, directBatch.size(), "direct predictBatch size");
                    assertPredictionClose(single, directBatch.get(0), "direct predictBatch first");
                    assertPredictionClose(single, directBatch.get(1), "direct predictBatch second");
                    Encoder.EncodedInput encodedInput = Encoder.encode(start, architecture.inputFormat());
                    List<Network.Prediction> directEncodedBatch = direct.predictEncodedBatch(
                            List.of(encodedInput.planes(), encodedInput.planes()));
                    assertEquals(2, directEncodedBatch.size(), "direct predictEncodedBatch size");
                    assertPredictionClose(single, directEncodedBatch.get(0), "direct predictEncodedBatch first");
                    assertPredictionClose(single, directEncodedBatch.get(1), "direct predictEncodedBatch second");
                    List<Network.TransformedPrediction> transformed = direct.predictBatchWithTransforms(
                            List.of(start, start));
                    assertEquals(2, transformed.size(), "direct transformed batch size");
                    assertEquals(encodedInput.transform(), transformed.get(0).transform(),
                            "direct transformed batch first transform");
                    assertEquals(encodedInput.transform(), transformed.get(1).transform(),
                            "direct transformed batch second transform");
                    assertPredictionClose(single, transformed.get(0).prediction(),
                            "direct transformed batch first prediction");
                    assertPredictionClose(single, transformed.get(1).prediction(),
                            "direct transformed batch second prediction");
                }
            });
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Compares native backends against the Java CPU path when the host has a
     * matching GPU and JNI library available.
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private static void testNativeBackendsWhenAvailable() throws IOException {
        Architecture architecture = syntheticArchitecture();
        Network.Weights weights = syntheticWeights(architecture);
        Path temp = Files.createTempFile("crtk-bt4-native-test-", ".bin");
        try {
            writeWeights(temp, weights);
            Position start = new Position(START_FEN);
            Network.Prediction expected;
            try (Network cpu = Network.create(weights)) {
                expected = cpu.predict(start);
            }
            assertNativeBackend(temp, expected, start, Network.BACKEND_CUDA,
                    chess.nn.lc0.bt4.cuda.Support.isAvailable());
            assertNativeBackend(temp, expected, start, Network.BACKEND_ROCM,
                    chess.nn.lc0.bt4.rocm.Support.isAvailable());
            assertNativeBackend(temp, expected, start, Network.BACKEND_ONEAPI,
                    chess.nn.lc0.bt4.oneapi.Support.isAvailable());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Verifies one native backend when available.
     *
     * @param path weights path
     * @param expected CPU prediction
     * @param position source position
     * @param backend backend name
     * @param available whether the backend is available
     * @throws IOException if loading fails
     */
    private static void assertNativeBackend(
            Path path,
            Network.Prediction expected,
            Position position,
            String backend,
            boolean available) throws IOException {
        if (!available) {
            return;
        }
        try {
            withBt4Backend(backend, () -> {
                try (Network network = Network.load(path)) {
                    assertEquals(backend, network.backend(), "BT4 native backend " + backend);
                    assertPredictionClose(expected, network.predict(position), "BT4 native " + backend + " parity");
                }
            });
        } catch (IOException e) {
            if (isNativeInitFailure(e)) {
                return;
            }
            throw e;
        }
    }

    /**
     * Returns true when a native probe found JNI support but no usable device.
     *
     * @param error load error
     * @return true for environment-only native init failures
     */
    private static boolean isNativeInitFailure(IOException error) {
        String message = error.getMessage();
        return message != null && message.contains("backend requested but failed to initialize");
    }

    /**
     * Builds the tiny deterministic test architecture.
     *
     * @return architecture
     */
    private static Architecture syntheticArchitecture() {
        return Architecture.simplified(
                "test-bt4",
                InputFormat.CLASSICAL_112,
                Architecture.InputEmbedding.PE_MAP,
                Encoder.INPUT_CHANNELS,
                Encoder.TOKENS,
                4,
                1,
                2,
                PolicyEncoder.POLICY_SIZE,
                1.0e-6f);
    }

    /**
     * Verifies deterministic output values from the synthetic forward pass.
     *
     * @param position source position
     * @param prediction prediction
     */
    private static void assertForwardOutputs(Position position, Network.Prediction prediction) {
        assertEquals(PolicyEncoder.POLICY_SIZE, prediction.policy().length, "synthetic policy size");
        assertEquals(3, prediction.wdl().length, "synthetic WDL size");
        float basePolicy = (float) (5.0 / Math.sqrt(2.0));
        int e2e4 = PolicyEncoder.compressedPolicyIndex(position, Move.parse("e2e4"));
        assertClose(basePolicy, prediction.policy()[e2e4], "synthetic e2e4 logit");
        assertClose(basePolicy + 2.5f, prediction.policy()[1792], "synthetic knight underpromotion logit");
        assertClose(basePolicy + 3.5f, prediction.policy()[1793], "synthetic bishop underpromotion logit");
        assertClose(basePolicy + 4.5f, prediction.policy()[1794], "synthetic rook underpromotion logit");
        double exp1 = Math.E;
        double exp2 = exp1 * exp1;
        float expectedWin = (float) (exp2 / (exp2 + exp1 + 1.0));
        float expectedLoss = (float) (1.0 / (exp2 + exp1 + 1.0));
        assertClose(expectedWin, prediction.wdl()[0], "synthetic win probability");
        assertClose(expectedLoss, prediction.wdl()[2], "synthetic loss probability");
        assertClose(expectedWin - expectedLoss, prediction.value(), "synthetic scalar value");
    }

    /**
     * Builds tiny zero-weight BT4 tensors with deterministic value-head bias.
     *
     * @param architecture architecture
     * @return weights
     */
    private static Network.Weights syntheticWeights(Architecture architecture) {
        Network.Dense input = dense(architecture.projectedInputWidth(), architecture.embeddingSize());
        Network.EncoderBlock encoder = syntheticEncoderBlock(architecture.embeddingSize(), architecture.attentionHeads());
        Network.PolicyHead policy = new Network.PolicyHead(
                dense(4, 4),
                List.of(),
                dense(4, 2, new float[4 * 2], new float[] { 1.0f, 2.0f }),
                dense(4, 2, new float[4 * 2], new float[] { 1.0f, 2.0f }),
                new float[] { 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.5f, 0.5f },
                Network.Activation.NONE);
        Network.ValueHead value = new Network.ValueHead(
                dense(4, 1),
                dense(64, 2),
                dense(2, 3, new float[2 * 3], new float[] { 2.0f, 1.0f, 0.0f }),
                Network.Activation.NONE);
        return new Network.Weights(architecture, input, List.of(encoder), policy, value);
    }

    /**
     * Builds a small encoder block whose computation is deterministic but still
     * exercises attention, residual adds, FFN, and layer normalization.
     *
     * @param embedding embedding width
     * @param heads attention heads
     * @return encoder block
     */
    private static Network.EncoderBlock syntheticEncoderBlock(int embedding, int heads) {
        Network.Attention attention = new Network.Attention(
                heads,
                dense(embedding, embedding),
                dense(embedding, embedding),
                dense(embedding, embedding),
                dense(embedding, embedding));
        float[] ones = filled(embedding, 1.0f);
        float[] zeros = new float[embedding];
        float[] beta = new float[] { 0.25f, 0.5f, 0.75f, 1.0f };
        return new Network.EncoderBlock(
                attention,
                dense(embedding, 3),
                dense(3, embedding),
                ones,
                zeros,
                zeros,
                beta,
                Network.Activation.MISH,
                1.0f);
    }

    /**
     * Creates a zero dense layer.
     *
     * @param in input dimension
     * @param out output dimension
     * @return dense layer
     */
    private static Network.Dense dense(int in, int out) {
        return dense(in, out, new float[in * out], new float[out]);
    }

    /**
     * Creates a dense layer.
     *
     * @param in input dimension
     * @param out output dimension
     * @param weights weights
     * @param bias bias
     * @return dense layer
     */
    private static Network.Dense dense(int in, int out, float[] weights, float[] bias) {
        return new Network.Dense(in, out, weights, bias);
    }

    /**
     * Creates a filled array.
     *
     * @param count element count
     * @param value value
     * @return array
     */
    private static float[] filled(int count, float value) {
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = value;
        }
        return out;
    }

    /**
     * Compares predictions with float tolerance.
     *
     * @param expected expected prediction
     * @param actual actual prediction
     * @param label assertion label
     */
    private static void assertPredictionClose(Network.Prediction expected, Network.Prediction actual, String label) {
        assertEquals(expected.policy().length, actual.policy().length, label + " policy length");
        assertEquals(expected.wdl().length, actual.wdl().length, label + " WDL length");
        for (int i = 0; i < expected.policy().length; i++) {
            assertClose(expected.policy()[i], actual.policy()[i], label + " policy " + i);
        }
        for (int i = 0; i < expected.wdl().length; i++) {
            assertClose(expected.wdl()[i], actual.wdl()[i], label + " WDL " + i);
        }
        assertClose(expected.value(), actual.value(), label + " value");
    }

    /**
     * Runs an action with a temporary BT4 backend property.
     *
     * @param backend backend value
     * @param action action to run
     * @throws IOException if the action fails
     */
    private static void withBt4Backend(String backend, ThrowingAction action) throws IOException {
        String previous = System.getProperty("crtk.lc0.bt4.backend");
        System.setProperty("crtk.lc0.bt4.backend", backend);
        try {
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty("crtk.lc0.bt4.backend");
            } else {
                System.setProperty("crtk.lc0.bt4.backend", previous);
            }
        }
    }

    /**
     * IO-capable test action.
     */
    @FunctionalInterface
    private interface ThrowingAction {
        /**
         * Runs the action.
         *
         * @throws IOException if the action fails
         */
        void run() throws IOException;
    }

    /**
     * Writes compact BT4 weights to disk.
     *
     * @param path output path
     * @param weights weights
     * @throws IOException if writing fails
     */
    private static void writeWeights(Path path, Network.Weights weights) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, 0x4A345442);
        writeInt(out, 2);
        writeArchitecture(out, weights.architecture());
        writeInputStack(out, weights.input(), weights.architecture());
        writeEncoderBlocks(out, weights.encoders(), weights.architecture().hasSmolgen());
        if (weights.architecture().hasSmolgen()) {
            writeFloatArray(out, weights.smolgenW());
        }
        writePolicyHead(out, weights.policyHead());
        writeValueHead(out, weights.valueHead());
        Files.write(path, out.toByteArray());
    }

    /**
     * Writes architecture metadata.
     *
     * @param out output
     * @param architecture architecture
     */
    private static void writeArchitecture(ByteArrayOutputStream out, Architecture architecture) {
        writeString(out, architecture.name());
        writeString(out, architecture.inputFormat().name());
        writeString(out, architecture.inputEmbedding().name());
        writeInt(out, architecture.inputChannels());
        writeInt(out, architecture.tokens());
        writeInt(out, architecture.embeddingSize());
        writeInt(out, architecture.encoderLayers());
        writeInt(out, architecture.attentionHeads());
        writeInt(out, architecture.policySize());
        writeFloat(out, architecture.layerNormEpsilon());
        writeInt(out, architecture.ffnHiddenSize());
        writeInt(out, architecture.smolgenHiddenChannels());
        writeInt(out, architecture.smolgenHiddenSize());
        writeInt(out, architecture.smolgenPerHeadDim());
        writeInt(out, architecture.smolgenGlobalSize());
        writeString(out, architecture.defaultActivation().name());
        writeString(out, architecture.smolgenActivation().name());
        writeString(out, architecture.ffnActivation().name());
        writeBool(out, architecture.hasInputPreproc());
        writeBool(out, architecture.hasInputEmbFfn());
        writeBool(out, architecture.hasInputGates());
        writeBool(out, architecture.hasSmolgen());
    }

    /**
     * Writes the input stack.
     *
     * @param out output
     * @param stack input stack
     * @param architecture architecture
     */
    private static void writeInputStack(ByteArrayOutputStream out, Network.InputStack stack,
            Architecture architecture) {
        if (architecture.hasInputPreproc()) {
            writeDense(out, stack.preproc());
        }
        writeDense(out, stack.embedding());
        if (architecture.inputEmbedding() == Architecture.InputEmbedding.PE_DENSE) {
            writeFloatArray(out, stack.embLnGamma());
            writeFloatArray(out, stack.embLnBeta());
        }
        if (architecture.hasInputGates()) {
            writeFloatArray(out, stack.multGate());
            writeFloatArray(out, stack.addGate());
        }
        if (architecture.hasInputEmbFfn()) {
            writeDense(out, stack.embFfn().dense1());
            writeDense(out, stack.embFfn().dense2());
            writeFloatArray(out, stack.embFfnLnGamma());
            writeFloatArray(out, stack.embFfnLnBeta());
        }
    }

    /**
     * Writes a smolgen block.
     *
     * @param out output
     * @param smolgen smolgen
     */
    private static void writeSmolgen(ByteArrayOutputStream out, Network.Smolgen smolgen) {
        writeDense(out, smolgen.compress());
        writeDense(out, smolgen.dense1());
        writeFloatArray(out, smolgen.ln1Gamma());
        writeFloatArray(out, smolgen.ln1Beta());
        writeDense(out, smolgen.dense2());
        writeFloatArray(out, smolgen.ln2Gamma());
        writeFloatArray(out, smolgen.ln2Beta());
    }

    /**
     * Writes a boolean as one byte.
     *
     * @param out output
     * @param value value
     */
    private static void writeBool(ByteArrayOutputStream out, boolean value) {
        out.write(value ? 1 : 0);
    }

    /**
     * Writes encoder blocks.
     *
     * @param out output
     * @param blocks blocks
     * @param hasSmolgen whether each attention layer carries smolgen weights
     */
    private static void writeEncoderBlocks(ByteArrayOutputStream out, List<Network.EncoderBlock> blocks,
            boolean hasSmolgen) {
        writeInt(out, blocks.size());
        for (Network.EncoderBlock block : blocks) {
            writeEncoderBlock(out, block, hasSmolgen);
        }
    }

    /**
     * Writes one encoder block.
     *
     * @param out output
     * @param block block
     * @param hasSmolgen whether the attention layer carries smolgen weights
     */
    private static void writeEncoderBlock(ByteArrayOutputStream out, Network.EncoderBlock block,
            boolean hasSmolgen) {
        writeAttention(out, block.attention(), hasSmolgen);
        writeDense(out, block.ffnIn());
        writeDense(out, block.ffnOut());
        writeFloatArray(out, block.ln1Gamma());
        writeFloatArray(out, block.ln1Beta());
        writeFloatArray(out, block.ln2Gamma());
        writeFloatArray(out, block.ln2Beta());
        writeString(out, block.activation().name());
        writeFloat(out, block.alpha());
    }

    /**
     * Writes one attention layer.
     *
     * @param out output
     * @param attention attention
     * @param hasSmolgen whether to append the trailing smolgen weights
     */
    private static void writeAttention(ByteArrayOutputStream out, Network.Attention attention,
            boolean hasSmolgen) {
        writeInt(out, attention.heads());
        writeDense(out, attention.query());
        writeDense(out, attention.key());
        writeDense(out, attention.value());
        writeDense(out, attention.out());
        if (hasSmolgen) {
            writeSmolgen(out, attention.smolgen());
        }
    }

    /**
     * Writes policy head weights.
     *
     * @param out output
     * @param head head
     */
    private static void writePolicyHead(ByteArrayOutputStream out, Network.PolicyHead head) {
        writeDense(out, head.embedding());
        writeEncoderBlocks(out, head.encoders(), false);
        writeDense(out, head.query());
        writeDense(out, head.key());
        writeFloatArray(out, head.promotionWeights());
        writeString(out, head.activation().name());
    }

    /**
     * Writes value head weights.
     *
     * @param out output
     * @param head head
     */
    private static void writeValueHead(ByteArrayOutputStream out, Network.ValueHead head) {
        writeDense(out, head.embedding());
        writeDense(out, head.fc1());
        writeDense(out, head.fc2());
        writeString(out, head.activation().name());
    }

    /**
     * Writes a dense layer.
     *
     * @param out output
     * @param dense dense layer
     */
    private static void writeDense(ByteArrayOutputStream out, Network.Dense dense) {
        writeInt(out, dense.inDim());
        writeInt(out, dense.outDim());
        writeFloatArray(out, dense.weights());
        writeFloatArray(out, dense.bias());
    }

    /**
     * Writes a string.
     *
     * @param out output
     * @param value value
     */
    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    /**
     * Writes a float array.
     *
     * @param out output
     * @param values values
     */
    private static void writeFloatArray(ByteArrayOutputStream out, float[] values) {
        writeInt(out, values.length);
        for (float value : values) {
            writeFloat(out, value);
        }
    }

    /**
     * Writes a little-endian int.
     *
     * @param out output
     * @param value value
     */
    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    /**
     * Writes a little-endian float.
     *
     * @param out output
     * @param value value
     */
    private static void writeFloat(ByteArrayOutputStream out, float value) {
        writeInt(out, Float.floatToIntBits(value));
    }
}
