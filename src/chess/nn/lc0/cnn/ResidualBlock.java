package chess.nn.lc0.cnn;



/**
 * Residual block containing convolutional layers and an optional SE unit.
 */
record ResidualBlock(
    /**
     * Stores the conv1.
     */
    ConvLayer conv1,
    /**
     * Stores the conv2.
     */
    ConvLayer conv2,
    /**
     * Stores the se.
     */
    SeUnit se
) {
}
