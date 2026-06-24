package chess.nn.lc0.cnn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import chess.nn.lc0.cnn.Network.DebugValue;
import chess.nn.lc0.cnn.Network.Prediction;

/**
 * Performs the forward pass for the CPU backend using reusable scratch space.
 */
final class Evaluator {

    /**
     * Thread-local buffers reused for each evaluation.
     */
    private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);

    /**
     * Clears the thread-local workspace for the current thread.
     * Allows buffers to be reclaimed or reinitialized later.
     */
    static void clearThreadLocal() {
        WORKSPACE.remove();
    }

    /**
     * Evaluates the model and returns logits, WDL and scalar value.
     *
     * @param w            parsed weights
     * @param encodedInput LC0 planes (length {@code inputChannels * 64})
     * @return inference
     */
    static Prediction evaluate(Weights w, float[] encodedInput) {
        return evaluate(w, encodedInput, null);
    }

    /**
     * Evaluates a batch of encoded inputs.
     *
     * @param w parsed weights
     * @param encodedBatch encoded input planes
     * @return predictions aligned with {@code encodedBatch}
     */
    static List<Prediction> evaluateBatch(Weights w, List<float[]> encodedBatch) {
        List<Prediction> out = new ArrayList<>(encodedBatch.size());
        for (float[] encodedInput : encodedBatch) {
            out.add(evaluate(w, encodedInput, null));
        }
        return out;
    }

    /**
     * Evaluates the model and optionally captures intermediate activations.
     *
     * @param w            parsed weights
     * @param encodedInput LC0 planes (length {@code inputChannels * 64})
     * @param sink optional activation collector
     * @return inference
     */
    static Prediction evaluate(Weights w, float[] encodedInput, chess.nn.ActivationSink sink) {
        Workspace ws = WORKSPACE.get();
        ws.ensureCapacity(w);
        capture(sink, "cnn.input", new int[] { w.inputChannels, 8, 8 }, encodedInput);

        w.inputLayer.forwardNoBias(encodedInput, ws.current);
        addBiasReLU(ws.current, w.inputLayer.bias, ws.current);
        capture(sink, "cnn.stem.relu", new int[] { w.trunkChannels, 8, 8 }, ws.current);

        for (int i = 0; i < w.blocks.size(); i++) {
            ResidualBlock block = w.blocks.get(i);
            block.conv1().forwardNoBias(ws.current, ws.tmp);
            addBiasReLU(ws.tmp, block.conv1().bias, ws.tmp);

            block.conv2().forwardNoBias(ws.tmp, ws.scratch);
            if (block.se() != null) {
                applySe(block.se(), ws, ws.scratch, block.conv2().bias, ws.current, ws.next);
            } else {
                addResidualReLU(ws.scratch, block.conv2().bias, ws.current, ws.next);
            }
            capture(sink, "cnn.block" + i + ".relu",
                    new int[] { w.trunkChannels, 8, 8 }, ws.next);
            float[] swap = ws.current;
            ws.current = ws.next;
            ws.next = swap;
        }
        capture(sink, "cnn.final.relu", new int[] { w.trunkChannels, 8, 8 }, ws.current);
        capture(sink, "cnn.final.activation", new int[] { 8, 8 },
                meanPerSquare(ws.current, w.trunkChannels));

        w.policyStem.forwardNoBias(ws.current, ws.policyHidden);
        addBiasReLU(ws.policyHidden, w.policyStem.bias, ws.policyHidden);
        capture(sink, "cnn.policy.hidden",
                new int[] { w.policyStem.outChannels, 8, 8 }, ws.policyHidden);
        w.policyOutput.forwardNoBias(ws.policyHidden, ws.policyPlanes);
        addBias(ws.policyPlanes, w.policyOutput.bias, ws.policyPlanes);
        capture(sink, "cnn.policy.planes",
                new int[] { w.policyOutput.outChannels, 8, 8 }, ws.policyPlanes);
        float[] policy = mapPolicy(ws.policyPlanes, w.policyMap);
        capture(sink, "cnn.policy.logits", new int[] { policy.length }, policy);

        w.valueConv.forwardNoBias(ws.current, ws.valueInput);
        addBiasReLU(ws.valueInput, w.valueConv.bias, ws.valueInput);
        capture(sink, "cnn.value.conv",
                new int[] { w.valueConv.outChannels, 8, 8 }, ws.valueInput);
        w.valueFc1.forward(ws.valueInput, ws.fc1, Activation.RELU);
        capture(sink, "cnn.value.fc1", new int[] { w.valueFc1.outDim }, ws.fc1);
        w.valueFc2.forward(ws.fc1, ws.logits, Activation.NONE);
        capture(sink, "cnn.value.logits", new int[] { w.valueFc2.outDim }, ws.logits);
        float[] raw = softmax(ws.logits);
        // LC0 WDL outputs are ordered [win, draw, loss] from the side-to-move ("our")
        // perspective.
        float win = raw[0];
        float draw = raw[1];
        float loss = raw[2];
        float[] wdl = new float[] { win, draw, loss };
        float scalar = win - loss;
        capture(sink, "cnn.value.wdl", new int[] { 3 }, wdl);
        capture(sink, "cnn.value.scalar", new int[] { 1 }, new float[] { scalar });

        return new Prediction(policy, wdl, scalar);
    }

    /**
     * Captures one tensor into the supplied sink.
     *
     * @param sink optional activation sink
     * @param key activation key
     * @param shape tensor shape
     * @param data source values; copied before publishing
     */
    private static void capture(chess.nn.ActivationSink sink, String key, int[] shape, float[] data) {
        if (sink == null) {
            return;
        }
        int size = 1;
        for (int dim : shape) {
            size *= dim;
        }
        sink.put(key, shape, Arrays.copyOf(data, size));
    }

    /**
     * Channel-averages a {@code [channels, 8, 8]} tensor into one board map.
     *
     * @param values channel-major tensor
     * @param channels channel count
     * @return 64-square mean map
     */
    private static float[] meanPerSquare(float[] values, int channels) {
        float[] out = new float[64];
        if (values == null || channels <= 0) {
            return out;
        }
        for (int c = 0; c < channels; c++) {
            int base = c * 64;
            for (int sq = 0; sq < 64; sq++) {
                out[sq] += values[base + sq];
            }
        }
        float inv = 1.0f / channels;
        for (int sq = 0; sq < 64; sq++) {
            out[sq] *= inv;
        }
        return out;
    }

    /**
     * Runs the value head just far enough to return raw WDL probabilities and stm
     * flag.
     *
     * @param w            parsed weights
     * @param encodedInput LC0 planes (length {@code inputChannels * 64})
     * @return debug-only values
     */
    static DebugValue debugValue(Weights w, float[] encodedInput) {
        Workspace ws = WORKSPACE.get();
        ws.ensureCapacity(w);

        w.inputLayer.forwardNoBias(encodedInput, ws.current);
        addBiasReLU(ws.current, w.inputLayer.bias, ws.current);

        for (ResidualBlock block : w.blocks) {
            block.conv1().forwardNoBias(ws.current, ws.tmp);
            addBiasReLU(ws.tmp, block.conv1().bias, ws.tmp);

            block.conv2().forwardNoBias(ws.tmp, ws.scratch);
            if (block.se() != null) {
                applySe(block.se(), ws, ws.scratch, block.conv2().bias, ws.current, ws.next);
            } else {
                addResidualReLU(ws.scratch, block.conv2().bias, ws.current, ws.next);
            }
            float[] swap = ws.current;
            ws.current = ws.next;
            ws.next = swap;
        }

        w.valueConv.forwardNoBias(ws.current, ws.valueInput);
        addBiasReLU(ws.valueInput, w.valueConv.bias, ws.valueInput);
        w.valueFc1.forward(ws.valueInput, ws.fc1, Activation.RELU);
        w.valueFc2.forward(ws.fc1, ws.logits, Activation.NONE);
        float[] raw = softmax(ws.logits);
        boolean blackToMove = encodedInput[108 * 64] > 0.5f;
        return new DebugValue(raw, blackToMove);
    }

    /**
     * Mutable buffers used during convolutional evaluation.
     */
    private static final class Workspace {

        /**
         * Current trunk activations [trunkChannels, 64].
         */
        float[] current = new float[0];

        /**
         * Next trunk activations [trunkChannels, 64] used for residual updates.
         */
        float[] next = new float[0];

        /**
         * Temporary buffer for intermediate conv output [trunkChannels, 64].
         */
        float[] tmp = new float[0];

        /**
         * Scratch buffer for conv output [trunkChannels, 64].
         */
        float[] scratch = new float[0];

        /**
         * Policy stem activations [policyStem.outChannels, 64].
         */
        float[] policyHidden = new float[0];

        /**
         * Raw policy planes [policyChannels, 64].
         */
        float[] policyPlanes = new float[0];

        /**
         * Value head conv activations [valueChannels, 64].
         */
        float[] valueInput = new float[0];

        /**
         * Value head hidden activations (dense).
         */
        float[] fc1 = new float[0];

        /**
         * Value head logits (dense).
         */
        float[] logits = new float[0];

        /**
         * SE pooled vector (per-channel mean).
         */
        float[] sePooled = new float[0];

        /**
         * SE hidden activations.
         */
        float[] seHidden = new float[0];

        /**
         * SE gate outputs (gamma and beta concatenated).
         */
        float[] seGates = new float[0];

        /**
         * Ensures all non-SE buffers match the current model dimensions.
         *
         * @param w current model weights
         */
        void ensureCapacity(Weights w) {
            int trunkSize = w.trunkChannels * 64;
            if (current.length != trunkSize)
                current = new float[trunkSize];
            if (next.length != trunkSize)
                next = new float[trunkSize];
            if (tmp.length != trunkSize)
                tmp = new float[trunkSize];
            if (scratch.length != trunkSize)
                scratch = new float[trunkSize];
            int policyHiddenSize = w.policyStem.outChannels * 64;
            if (policyHidden.length != policyHiddenSize)
                policyHidden = new float[policyHiddenSize];

            int policyPlanesSize = w.policyChannels * 64;
            if (policyPlanes.length != policyPlanesSize)
                policyPlanes = new float[policyPlanesSize];

            int valueInputSize = w.valueChannels * 64;
            if (valueInput.length != valueInputSize)
                valueInput = new float[valueInputSize];

            if (fc1.length != w.valueFc1.outDim)
                fc1 = new float[w.valueFc1.outDim];
            if (logits.length != w.valueFc2.outDim)
                logits = new float[w.valueFc2.outDim];
        }

        /**
         * Ensures SE-specific buffers match the provided SE unit dimensions.
         *
         * @param se SE unit controlling buffer sizes
         */
        void ensureSeCapacity(SeUnit se) {
            if (sePooled.length != se.channels)
                sePooled = new float[se.channels];
            if (seHidden.length != se.hidden)
                seHidden = new float[se.hidden];
            int gatesSize = se.channels * 2;
            if (seGates.length != gatesSize)
                seGates = new float[gatesSize];
        }
    }

    /**
     * Adds bias and applies ReLU when accumulating convolution outputs.
     *
     * @param convOut convolution output tensor
     * @param bias    bias vector per channel
     * @param dest    destination tensor
     */
    private static void addBiasReLU(float[] convOut, float[] bias, float[] dest) {
        int channels = bias.length;
        if (Parallel.enabledForChannels(channels)) {
            Parallel.forRange(0, channels, (start, end) -> addBiasReLUChannels(convOut, bias, dest, start, end));
        } else {
            addBiasReLUChannels(convOut, bias, dest, 0, channels);
        }
    }

    /**
     * Adds bias and applies ReLU for a subset of channels.
     *
     * @param convOut convolution output tensor
     * @param bias    bias vector per channel
     * @param dest    destination tensor
     * @param start   inclusive start channel
     * @param end     exclusive end channel
     */
    private static void addBiasReLUChannels(float[] convOut, float[] bias, float[] dest, int start, int end) {
        for (int ch = start; ch < end; ch++) {
            int base = ch * 64;
            float b = bias[ch];
            for (int i = 0; i < 64; i++) {
                float val = convOut[base + i] + b;
                dest[base + i] = val > 0f ? val : 0f;
            }
        }
    }

    /**
     * Adds bias to convolution outputs without activation.
     *
     * @param convOut convolution output tensor
     * @param bias    bias vector per channel
     * @param dest    destination tensor
     */
    private static void addBias(float[] convOut, float[] bias, float[] dest) {
        int channels = bias.length;
        if (Parallel.enabledForChannels(channels)) {
            Parallel.forRange(0, channels, (start, end) -> {
                for (int ch = start; ch < end; ch++) {
                    int base = ch * 64;
                    float b = bias[ch];
                    for (int i = 0; i < 64; i++) {
                        dest[base + i] = convOut[base + i] + b;
                    }
                }
            });
        } else {
            for (int ch = 0; ch < channels; ch++) {
                int base = ch * 64;
                float b = bias[ch];
                for (int i = 0; i < 64; i++) {
                    dest[base + i] = convOut[base + i] + b;
                }
            }
        }
    }

    /**
     * Combines residual input with convolution outputs then applies ReLU.
     *
     * @param convOut  convolution output tensor
     * @param bias     bias vector per channel
     * @param residual residual tensor to add
     * @param dest     destination tensor
     */
    private static void addResidualReLU(float[] convOut, float[] bias, float[] residual, float[] dest) {
        int channels = bias.length;
        for (int ch = 0; ch < channels; ch++) {
            int base = ch * 64;
            float b = bias[ch];
            for (int i = 0; i < 64; i++) {
                float val = convOut[base + i] + b + residual[base + i];
                dest[base + i] = val > 0f ? val : 0f;
            }
        }
    }

    /**
     * Executes the squeeze-and-excitation block when present in a residual block.
     *
     * @param se       SE unit descriptor
     * @param ws       workspace buffers
     * @param convOut  trunk convolution output
     * @param bias     trunk bias vector
     * @param residual residual input
     * @param dest     destination tensor
     */
    private static void applySe(SeUnit se, Workspace ws, float[] convOut, float[] bias, float[] residual,
            float[] dest) {
        ws.ensureSeCapacity(se);
        sePoolWithBias(convOut, bias, se.channels, ws.sePooled);
        denseRelu(ws.sePooled, se.w1, se.b1, se.channels, se.hidden, ws.seHidden);
        dense(ws.seHidden, se.w2, se.b2, se.hidden, se.channels * 2, ws.seGates);
        seCombine(convOut, bias, residual, dest, se.channels, ws.seGates);
    }

    /**
     * Global-average-pools each channel (mean over 64 squares) and adds bias to
     * form the SE input vector.
     *
     * @param convOut   convolution output
     * @param bias      bias vector per channel
     * @param channels  number of channels
     * @param pooledOut output vector
     */
    private static void sePoolWithBias(float[] convOut, float[] bias, int channels, float[] pooledOut) {
        float invSquares = 1f / 64f;
        for (int ch = 0; ch < channels; ch++) {
            int base = ch * 64;
            float sum = 0f;
            for (int i = 0; i < 64; i++) {
                sum += convOut[base + i];
            }
            pooledOut[ch] = sum * invSquares + bias[ch];
        }
    }

    /**
     * Dense layer with ReLU activation.
     *
     * @param input   input vector
     * @param weights weight matrix
     * @param bias    bias vector
     * @param inDim   input dimension
     * @param outDim  output dimension
     * @param out     output vector
     */
    private static void denseRelu(float[] input, float[] weights, float[] bias, int inDim, int outDim,
            float[] out) {
        for (int o = 0; o < outDim; o++) {
            float acc = bias[o];
            int weightBase = o * inDim;
            for (int i = 0; i < inDim; i++) {
                acc += weights[weightBase + i] * input[i];
            }
            out[o] = acc > 0f ? acc : 0f;
        }
    }

    /**
     * Dense layer without activation.
     *
     * @param input   input vector
     * @param weights weight matrix
     * @param bias    bias vector
     * @param inDim   input dimension
     * @param outDim  output dimension
     * @param out     output vector
     */
    private static void dense(float[] input, float[] weights, float[] bias, int inDim, int outDim, float[] out) {
        for (int o = 0; o < outDim; o++) {
            float acc = bias[o];
            int weightBase = o * inDim;
            for (int i = 0; i < inDim; i++) {
                acc += weights[weightBase + i] * input[i];
            }
            out[o] = acc;
        }
    }

    /**
     * Applies SE gating and combines with residual input, then applies ReLU.
     *
     * <p>
     * {@code gates} contains {@code [gammaLogit[channels], betaExtra[channels]]}.
     *
     * @param convOut  convolution output tensor
     * @param bias     bias vector per channel
     * @param residual residual tensor
     * @param dest     destination tensor
     * @param channels number of channels
     * @param gates    gate logits
     */
    private static void seCombine(float[] convOut, float[] bias, float[] residual, float[] dest, int channels,
            float[] gates) {
        for (int ch = 0; ch < channels; ch++) {
            float gamma = sigmoid(gates[ch]);
            float betaExtra = gates[ch + channels];
            float b = bias[ch];
            int base = ch * 64;
            for (int i = 0; i < 64; i++) {
                float z = convOut[base + i] + b;
                float val = gamma * z + residual[base + i] + betaExtra;
                dest[base + i] = val > 0f ? val : 0f;
            }
        }
    }

    /**
     * Maps the raw policy output planes to the compressed LC0 move-logit vector.
     *
     * <p>
     * The weights file provides {@code policyMap} indices into the uncompressed
     * plane tensor; out-of-range
     * indices are treated as zero.
     *
     * @param planes    raw policy planes
     * @param policyMap index map into the compressed move logits
     * @return compressed policy logits
     */
    private static float[] mapPolicy(float[] planes, int[] policyMap) {
        float[] out = new float[policyMap.length];
        for (int i = 0; i < policyMap.length; i++) {
            int idx = policyMap[i];
            if (idx >= 0 && idx < planes.length) {
                out[i] = planes[idx];
            }
        }
        return out;
    }

    /**
     * Applies softmax to logits to produce probabilities (numerically stabilized by
     * subtracting max).
     *
     * @param logits input logits
     * @return normalized probabilities of the same length
     */
    private static float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float val : logits) {
            if (val > max)
                max = val;
        }
        float sum = 0f;
        float[] out = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            float exp = (float) Math.exp(logits[i] - max);
            out[i] = exp;
            sum += exp;
        }
        if (sum > 0f) {
            for (int i = 0; i < out.length; i++) {
                out[i] /= sum;
            }
        }
        return out;
    }

    /**
     * Sigmoid helper used by the SE gating mechanism.
     *
     * @param x input logit
     * @return sigmoid(x)
     */
    private static float sigmoid(float x) {
        return 1f / (1f + (float) Math.exp(-x));
    }
}
