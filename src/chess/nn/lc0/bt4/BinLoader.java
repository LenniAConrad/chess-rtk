package chess.nn.lc0.bt4;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the compact CRTK BT4 binary format.
 *
 * <p>
 * Mirrors the lightweight custom-loader style used by the LC0 CNN and T5
 * Java evaluators. The format stores already-decoded float32 tensors in
 * row-major dense-layer order and is intended as a simple Java-side export
 * target rather than a replacement for LC0's protobuf.
 * </p>
 *
 * <p>
 * Version 2 adds the full BT4 input stack (preproc, embedding LN, gates,
 * embedding FFN, FFN LN) and per-attention smolgen weights, plus the shared
 * global {@code smolgenW} projection.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BinLoader {

    /**
     * CRTK BT4 binary magic, ASCII {@code BT4J}.
     */
    private static final int MAGIC = 0x4A345442;

    /**
     * Supported binary format version. v2 adds the full BT4 input stack and
     * smolgen attention bias generator.
     */
    private static final int VERSION = 2;

    /**
     * Maximum accepted string length.
     */
    private static final int MAX_STRING_LENGTH = 1_000_000;

    /**
     * Prevents instantiation.
     */
    private BinLoader() {
        // utility
    }

    /**
     * Loads a BT4 model from a compact CRTK binary file.
     *
     * @param path path to the model file
     * @return model wrapper
     * @throws IOException if the file cannot be read or parsed
     */
    public static Model load(Path path) throws IOException {
        return Model.of(Network.create(loadWeights(path)));
    }

    /**
     * Loads BT4 weights from a compact CRTK binary file.
     *
     * @param path path to the model file
     * @return parsed weights
     * @throws IOException if the file cannot be read or parsed
     */
    public static Network.Weights loadWeights(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path == null");
        }
        try {
            ByteBuffer in = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
            int magic = in.getInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid BT4 bin magic.");
            }
            int version = in.getInt();
            if (version != VERSION) {
                throw new IOException("Unsupported BT4 bin version: " + version + " (expected " + VERSION + ")");
            }

            Architecture architecture = readArchitecture(in);
            Network.InputStack inputStack = readInputStack(in, architecture);
            List<Network.EncoderBlock> encoders = readEncoderBlocks(in, architecture.hasSmolgen());
            float[] smolgenW = architecture.hasSmolgen() ? readFloatArray(in) : null;
            Network.PolicyHead policyHead = readPolicyHead(in);
            Network.ValueHead valueHead = readValueHead(in);
            if (in.hasRemaining()) {
                throw new IOException("Unexpected bytes at end of BT4 weights file.");
            }
            return new Network.Weights(architecture, inputStack, encoders, smolgenW, policyHead, valueHead);
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            throw new IOException("Malformed BT4 weights file.", e);
        }
    }

    /**
     * Loads only BT4 architecture metadata from a compact CRTK binary file.
     *
     * @param path path to the model file
     * @return parsed architecture metadata
     * @throws IOException if the file cannot be read or parsed
     */
    public static Architecture loadArchitecture(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path == null");
        }
        try {
            ByteBuffer in = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
            int magic = in.getInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid BT4 bin magic.");
            }
            int version = in.getInt();
            if (version != VERSION) {
                throw new IOException("Unsupported BT4 bin version: " + version + " (expected " + VERSION + ")");
            }
            return readArchitecture(in);
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            throw new IOException("Malformed BT4 weights file.", e);
        }
    }

    /**
     * Reads architecture metadata.
     *
     * @param in source buffer
     * @return architecture
     */
    private static Architecture readArchitecture(ByteBuffer in) {
        String name = readString(in);
        InputFormat inputFormat = readEnum(in, InputFormat.class);
        Architecture.InputEmbedding inputEmbedding = readEnum(in, Architecture.InputEmbedding.class);
        int inputChannels = in.getInt();
        int tokens = in.getInt();
        int embeddingSize = in.getInt();
        int encoderLayers = in.getInt();
        int attentionHeads = in.getInt();
        int policySize = in.getInt();
        float eps = in.getFloat();
        int ffnHiddenSize = in.getInt();
        int smolgenHiddenChannels = in.getInt();
        int smolgenHiddenSize = in.getInt();
        int smolgenPerHeadDim = in.getInt();
        int smolgenGlobalSize = in.getInt();
        Network.Activation defaultActivation = readEnum(in, Network.Activation.class);
        Network.Activation smolgenActivation = readEnum(in, Network.Activation.class);
        Network.Activation ffnActivation = readEnum(in, Network.Activation.class);
        boolean hasInputPreproc = readBool(in);
        boolean hasInputEmbFfn = readBool(in);
        boolean hasInputGates = readBool(in);
        boolean hasSmolgen = readBool(in);
        return new Architecture(
                name,
                inputFormat,
                inputEmbedding,
                inputChannels,
                tokens,
                embeddingSize,
                encoderLayers,
                attentionHeads,
                policySize,
                eps,
                ffnHiddenSize,
                smolgenHiddenChannels,
                smolgenHiddenSize,
                smolgenPerHeadDim,
                smolgenGlobalSize,
                defaultActivation,
                smolgenActivation,
                ffnActivation,
                hasInputPreproc,
                hasInputEmbFfn,
                hasInputGates,
                hasSmolgen);
    }

    /**
     * Reads the input stack.
     *
     * @param in source buffer
     * @param architecture network architecture
     * @return input stack
     */
    private static Network.InputStack readInputStack(ByteBuffer in, Architecture architecture) {
        Network.Dense preproc = architecture.hasInputPreproc() ? readDense(in) : null;
        Network.Dense embedding = readDense(in);
        float[] embLnGamma;
        float[] embLnBeta;
        if (architecture.inputEmbedding() == Architecture.InputEmbedding.PE_DENSE) {
            embLnGamma = readFloatArray(in);
            embLnBeta = readFloatArray(in);
        } else {
            embLnGamma = null;
            embLnBeta = null;
        }
        float[] multGate = architecture.hasInputGates() ? readFloatArray(in) : null;
        float[] addGate = architecture.hasInputGates() ? readFloatArray(in) : null;
        Network.Ffn embFfn = null;
        float[] embFfnLnGamma = null;
        float[] embFfnLnBeta = null;
        if (architecture.hasInputEmbFfn()) {
            Network.Dense d1 = readDense(in);
            Network.Dense d2 = readDense(in);
            embFfn = new Network.Ffn(d1, d2);
            embFfnLnGamma = readFloatArray(in);
            embFfnLnBeta = readFloatArray(in);
        }
        return new Network.InputStack(preproc, embedding, embLnGamma, embLnBeta,
                multGate, addGate, embFfn, embFfnLnGamma, embFfnLnBeta);
    }

    /**
     * Reads encoder blocks.
     *
     * @param in source buffer
     * @param hasSmolgen whether each attention layer carries smolgen weights
     * @return blocks
     */
    private static List<Network.EncoderBlock> readEncoderBlocks(ByteBuffer in, boolean hasSmolgen) {
        int count = readCount(in, "encoder blocks");
        List<Network.EncoderBlock> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(readEncoderBlock(in, hasSmolgen));
        }
        return blocks;
    }

    /**
     * Reads one encoder block.
     *
     * @param in source buffer
     * @param hasSmolgen whether the attention layer carries smolgen weights
     * @return block
     */
    private static Network.EncoderBlock readEncoderBlock(ByteBuffer in, boolean hasSmolgen) {
        Network.Attention attention = readAttention(in, hasSmolgen);
        Network.Dense ffnIn = readDense(in);
        Network.Dense ffnOut = readDense(in);
        float[] ln1Gamma = readFloatArray(in);
        float[] ln1Beta = readFloatArray(in);
        float[] ln2Gamma = readFloatArray(in);
        float[] ln2Beta = readFloatArray(in);
        Network.Activation activation = readEnum(in, Network.Activation.class);
        float alpha = in.getFloat();
        return new Network.EncoderBlock(
                attention,
                ffnIn,
                ffnOut,
                ln1Gamma,
                ln1Beta,
                ln2Gamma,
                ln2Beta,
                activation,
                alpha);
    }

    /**
     * Reads one attention layer.
     *
     * @param in source buffer
     * @param hasSmolgen whether to read trailing smolgen weights
     * @return attention
     */
    private static Network.Attention readAttention(ByteBuffer in, boolean hasSmolgen) {
        int heads = in.getInt();
        Network.Dense query = readDense(in);
        Network.Dense key = readDense(in);
        Network.Dense value = readDense(in);
        Network.Dense out = readDense(in);
        Network.Smolgen smolgen = hasSmolgen ? readSmolgen(in) : null;
        return new Network.Attention(heads, query, key, value, out, smolgen);
    }

    /**
     * Reads one smolgen block.
     *
     * @param in source buffer
     * @return smolgen
     */
    private static Network.Smolgen readSmolgen(ByteBuffer in) {
        Network.Dense compress = readDense(in);
        Network.Dense dense1 = readDense(in);
        float[] ln1Gamma = readFloatArray(in);
        float[] ln1Beta = readFloatArray(in);
        Network.Dense dense2 = readDense(in);
        float[] ln2Gamma = readFloatArray(in);
        float[] ln2Beta = readFloatArray(in);
        return new Network.Smolgen(compress, dense1, ln1Gamma, ln1Beta, dense2, ln2Gamma, ln2Beta);
    }

    /**
     * Reads the attention policy head.
     *
     * @param in source buffer
     * @return policy head
     */
    private static Network.PolicyHead readPolicyHead(ByteBuffer in) {
        Network.Dense embedding = readDense(in);
        List<Network.EncoderBlock> encoders = readEncoderBlocks(in, false);
        Network.Dense query = readDense(in);
        Network.Dense key = readDense(in);
        float[] promotionWeights = readFloatArray(in);
        Network.Activation activation = readEnum(in, Network.Activation.class);
        return new Network.PolicyHead(embedding, encoders, query, key, promotionWeights, activation);
    }

    /**
     * Reads the WDL value head.
     *
     * @param in source buffer
     * @return value head
     */
    private static Network.ValueHead readValueHead(ByteBuffer in) {
        Network.Dense embedding = readDense(in);
        Network.Dense fc1 = readDense(in);
        Network.Dense fc2 = readDense(in);
        Network.Activation activation = readEnum(in, Network.Activation.class);
        return new Network.ValueHead(embedding, fc1, fc2, activation);
    }

    /**
     * Reads one dense layer.
     *
     * @param in source buffer
     * @return dense layer
     */
    private static Network.Dense readDense(ByteBuffer in) {
        int inDim = in.getInt();
        int outDim = in.getInt();
        return new Network.Dense(inDim, outDim, readFloatArray(in), readFloatArray(in));
    }

    /**
     * Reads a length-prefixed float array.
     *
     * @param in source buffer
     * @return float array
     */
    private static float[] readFloatArray(ByteBuffer in) {
        int count = readCount(in, "float array");
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = in.getFloat();
        }
        return out;
    }

    /**
     * Reads a non-negative count.
     *
     * @param in source buffer
     * @param label field label
     * @return count
     */
    private static int readCount(ByteBuffer in, String label) {
        int count = in.getInt();
        if (count < 0) {
            throw new IllegalArgumentException("Negative " + label + " count: " + count);
        }
        return count;
    }

    /**
     * Reads a UTF-8 string.
     *
     * @param in source buffer
     * @return string
     */
    private static String readString(ByteBuffer in) {
        int length = in.getInt();
        if (length < 0 || length > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Invalid string length: " + length);
        }
        byte[] bytes = new byte[length];
        in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads an enum by stable name.
     *
     * @param in source buffer
     * @param type enum type
     * @param <E> enum type
     * @return enum value
     */
    private static <E extends Enum<E>> E readEnum(ByteBuffer in, Class<E> type) {
        return Enum.valueOf(type, readString(in));
    }

    /**
     * Reads a boolean stored as one byte.
     *
     * @param in source buffer
     * @return decoded boolean
     */
    private static boolean readBool(ByteBuffer in) {
        return in.get() != 0;
    }
}
