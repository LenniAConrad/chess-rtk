package chess.nn.lc0.cnn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import chess.nn.lc0.cnn.Network.DebugValue;
import chess.nn.lc0.cnn.Network.Prediction;

/**
 * Parameter set for an SE (squeeze-and-excitation) unit.
 */
final class SeUnit {

    /**
     * Number of channels in the residual block.
     */
    final int channels;

    /**
     * Hidden dimension used in the SE bottleneck.
     */
    final int hidden;

    /**
     * First-layer weights (channels × hidden).
     */
    final float[] w1;

    /**
     * First-layer biases.
     */
    final float[] b1;

    /**
     * Second-layer weights (hidden × 2*channels).
     */
    final float[] w2;

    /**
     * Second-layer biases.
     */
    final float[] b2;

    /**
     * Creates an SE unit descriptor.
     * @param channels channels value
     * @param hidden hidden value
     * @param w1 w1 value
     * @param b1 b1 value
     * @param w2 w2 value
     * @param b2 b2 value
     */
    SeUnit(int channels, int hidden, float[] w1, float[] b1, float[] w2, float[] b2) {
        this.channels = channels;
        this.hidden = hidden;
        this.w1 = w1;
        this.b1 = b1;
        this.w2 = w2;
        this.b2 = b2;
    }
}
