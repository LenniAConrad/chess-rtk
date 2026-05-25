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
 * Pure-Java CPU tensors and evaluator for {@link Network}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
enum Activation {

    /**
     * Rectified linear unit.
     */
    RELU,

    /**
     * No activation (identity).
     */
    NONE
}
