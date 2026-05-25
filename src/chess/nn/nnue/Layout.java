package chess.nn.nnue;

import static chess.nn.nnue.UpstreamNetwork.*;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import chess.core.Position;
import chess.nn.nnue.UpstreamNetwork.Size;
import chess.nn.nnue.UpstreamNetwork.Variant;
import utility.Numbers;

/**
 * Stockfish NNUE tensor layouts, layers, and compressed-weight cursors.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
/**
 * Layout constants for one supported Stockfish format.
 */
final class Layout {

    /**
     * Variant.
     */
    final Variant variant;

    /**
     * Big/small size.
     */
    final Size size;

    /**
     * Transformed feature dimensions.
     */
    final int transformedDimensions;

    /**
     * First hidden size.
     */
    final int l2;

    /**
     * Second hidden size.
     */
    final int l3;

    /**
     * Whether this is a big net.
     */
    final boolean useThreats;

    /**
     * Creates a layout.
     *
     * @param variant variant
     * @param size size
     */
    Layout(Variant variant, Size size) {
        this.variant = variant;
        this.size = size;
        this.useThreats = size == Size.BIG;
        this.transformedDimensions = useThreats ? 1024 : 128;
        this.l2 = useThreats && variant == Variant.CURRENT ? 31 : 15;
        this.l3 = 32;
    }

    /**
     * Returns a layout.
     *
     * @param variant variant
     * @param size size
     * @return layout
     */
    static Layout of(Variant variant, Size size) {
        return new Layout(variant, size);
    }

    /**
     * Returns PSQ feature dimensions.
     *
     * @return dimensions
     */
    int psqDimensions() {
        return UpstreamFeatures.HALF_KA_DIMENSIONS;
    }

    /**
     * Returns threat feature dimensions.
     *
     * @return dimensions
     */
    int threatDimensions() {
        return useThreats ? variant.threatDimensions : 0;
    }

    /**
     * Returns total input feature dimensions.
     *
     * @return dimensions
     */
    int totalInputDimensions() {
        return psqDimensions() + threatDimensions();
    }

    /**
     * Returns the feature-transformer hash.
     *
     * @return hash
     */
    int featureHash() {
        int base = baseFeatureHash();
        return base ^ (transformedDimensions * 2);
    }

    /**
     * Returns the feature hash before the transformed-dimension mix-in.
     *
     * @return base feature hash
     */
    private int baseFeatureHash() {
        if (!useThreats) {
            return UpstreamFeatures.HALF_KA_HASH;
        }
        if (!variant.combinedBigFeatureHash) {
            return UpstreamFeatures.FULL_THREATS_HASH;
        }
        return combineFeatureHashes(
                UpstreamFeatures.FULL_THREATS_HASH,
                UpstreamFeatures.HALF_KA_HASH);
    }

    /**
     * Returns the architecture hash.
     *
     * @return hash
     */
    int archHash() {
        int hash = 0xEC42E90D;
        hash ^= transformedDimensions * 2;
        hash = affineHash(hash, l2 + 1);
        hash = clippedReluHash(hash);
        hash = affineHash(hash, l3);
        hash = clippedReluHash(hash);
        hash = affineHash(hash, 1);
        return hash;
    }

    /**
     * Returns the full network hash.
     *
     * @return hash
     */
    int networkHash() {
        return featureHash() ^ archHash();
    }

    /**
     * Combines feature hashes the same way Stockfish's feature transformer does.
     *
     * @param hashes component hashes
     * @return combined hash
     */
    private static int combineFeatureHashes(int... hashes) {
        int hash = 0;
        for (int component : hashes) {
            hash = (hash << 1) | (hash >>> 31);
            hash ^= component;
        }
        return hash;
    }

    /**
     * Computes a Stockfish affine-layer hash.
     *
     * @param previous previous layer hash
     * @param outputDimensions output dimensions
     * @return layer hash
     */
    private static int affineHash(int previous, int outputDimensions) {
        int hash = 0xCC03DAE4;
        hash += outputDimensions;
        hash ^= previous >>> 1;
        hash ^= previous << 31;
        return hash;
    }

    /**
     * Computes a Stockfish clipped-ReLU hash.
     *
     * @param previous previous layer hash
     * @return layer hash
     */
    private static int clippedReluHash(int previous) {
        return 0x538D24C7 + previous;
    }
}
