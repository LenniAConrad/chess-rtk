package chess.nn.lc0.cnn;



/**
 * Fully connected layer descriptor.
 */
final class DenseLayer {

    /**
     * Input dimension.
     */
    final int inDim;

    /**
     * Output dimension.
     */
    final int outDim;

    /**
     * Flattened weight matrix (row-major).
     */
    final float[] weights;

    /**
     * Bias vector for each output unit.
     */
    final float[] bias;

    /**
     * Builds a dense layer descriptor.
     * @param inDim input dimension
     * @param outDim output dimension
     * @param weights network weights
     * @param bias bias vector
     */
    DenseLayer(int inDim, int outDim, float[] weights, float[] bias) {
        this.inDim = inDim;
        this.outDim = outDim;
        this.weights = weights;
        this.bias = bias;
    }

    /**
     * Runs the dense layer and applies the optional activation.
     * @param input input path or text
     * @param output output text
     * @param activation activation function
     */
    void forward(float[] input, float[] output, Activation activation) {
        for (int o = 0; o < outDim; o++) {
            float acc = bias[o];
            int weightBase = o * inDim;
            for (int i = 0; i < inDim; i++) {
                acc += weights[weightBase + i] * input[i];
            }
            if (activation == Activation.RELU && acc < 0f) {
                acc = 0f;
            }
            output[o] = acc;
        }
    }
}
