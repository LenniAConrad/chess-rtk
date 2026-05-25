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
