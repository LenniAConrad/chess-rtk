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
 * Precomputed per-square neighbor indices for a given convolution kernel size.
 *
 * <p>
 * Used to speed up spatial convolutions by skipping off-board kernel taps.
 */
final class KernelNeighbors {

    /**
     * Start offsets into {@link #square}/{@link #kernelIndex} for each board square
     * (length 65).
     */
    final int[] start; // length 65

    /**
     * Flattened list of neighboring input squares, indexed by ranges in
     * {@link #start}.
     */
    final int[] square;

    /**
     * Flattened list of kernel tap indices aligned with {@link #square}.
     */
    final int[] kernelIndex;

    /**
     * Creates neighbor lookups for convolution kernels.
     *
     * @param start       offsets into {@code square}/{@code kernelIndex} per board
     *                    square
     * @param square      flattened neighbor square indices
     * @param kernelIndex flattened kernel tap indices aligned with {@code square}
     */
    private KernelNeighbors(int[] start, int[] square, int[] kernelIndex) {
        this.start = start;
        this.square = square;
        this.kernelIndex = kernelIndex;
    }

    /**
     * Precomputes neighbor ranges for each of the 64 squares for the given odd
     * kernel size (e.g. 3).
     *
     * @param kernel convolution kernel size
     * @return neighbor lookups for the supplied kernel
     */
    static KernelNeighbors precompute(int kernel) {
        int pad = kernel / 2;
        int maxNeighbors = 64 * kernel * kernel;
        int[] start = new int[65];
        int[] square = new int[maxNeighbors];
        int[] kernelIndex = new int[maxNeighbors];

        int off = 0;
        for (int sq = 0; sq < 64; sq++) {
            start[sq] = off;
            int row = sq / 8;
            int col = sq % 8;
            for (int ky = 0; ky < kernel; ky++) {
                int inRow = row + ky - pad;
                if (inRow < 0 || inRow >= 8) {
                    continue;
                }
                for (int kx = 0; kx < kernel; kx++) {
                    int inCol = col + kx - pad;
                    if (inCol < 0 || inCol >= 8) {
                        continue;
                    }
                    square[off] = inRow * 8 + inCol;
                    kernelIndex[off] = ky * kernel + kx;
                    off++;
                }
            }
        }
        start[64] = off;

        int[] squareTrim = Arrays.copyOf(square, off);
        int[] kernelTrim = Arrays.copyOf(kernelIndex, off);
        return new KernelNeighbors(start, squareTrim, kernelTrim);
    }
}
