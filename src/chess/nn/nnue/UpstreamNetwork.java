package chess.nn.nnue;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;
import chess.gpu.BackendNames;

/**
 * Loader and pure-Java evaluator for modern Stockfish NNUE files.
 *
 * <p>
 * This supports the Stockfish 18 SFNNv10 format and the current Stockfish
 * development SFNNv13-style format used in 2026 master. It intentionally keeps
 * the implementation scalar and portable; it is compatibility-oriented rather
 * than a replacement for Stockfish's highly optimized SIMD inference code. See
 * the package documentation for how this Stockfish path relates to the compact
 * CRTK-format NNUE implementation.
 * </p>
 */

public final class UpstreamNetwork implements AutoCloseable {

    /**
     * Stockfish NNUE file version.
     */
    static final int VERSION = 0x7AF32F20;

    /**
     * Output scale used by Stockfish.
     */
    static final int OUTPUT_SCALE = 16;

    /**
     * Weight scale bits used by Stockfish clipped ReLU layers.
     */
    static final int WEIGHT_SCALE_BITS = 6;

    /**
     * Number of PSQT buckets/layer stacks.
     */
    static final int LAYER_STACKS = 8;

    /**
     * LEB128 array marker.
     */
    static final byte[] LEB128_MAGIC = "COMPRESSED_LEB128".getBytes(StandardCharsets.US_ASCII);

    /**
     * Active backend.
     */
    public static final String BACKEND = BackendNames.CPU;

    /**
     * Supported Stockfish NNUE architecture variants.
     */
    public enum Variant {

        /**
         * Stockfish 18 release architecture, described upstream as SFNNv10.
         */
        SF_18("SFNNv10", false, true, 79856),

        /**
         * Current 2026 Stockfish development architecture, described upstream as
         * SFNNv13 after the L2-size update.
         */
        CURRENT("SFNNv13", true, false, 60720);

        /**
         * Human-readable architecture name.
         */
        private final String label;

        /**
         * Whether the big feature-transformer hash combines threat and PSQ feature
         * hashes.
         */
        final boolean combinedBigFeatureHash;

        /**
         * Whether small-net feature-transformer weights are doubled after loading.
         */
        final boolean scaleSmallTransformer;

        /**
         * FullThreats dimensions.
         */
        final int threatDimensions;

        /**
         * Creates a variant.
         *
         * @param label label
         * @param combinedBigFeatureHash true for current master
         * @param scaleSmallTransformer true for Stockfish 18 small nets
         * @param threatDimensions FullThreats dimensions
         */
        Variant(String label, boolean combinedBigFeatureHash, boolean scaleSmallTransformer, int threatDimensions) {
            this.label = label;
            this.combinedBigFeatureHash = combinedBigFeatureHash;
            this.scaleSmallTransformer = scaleSmallTransformer;
            this.threatDimensions = threatDimensions;
        }

        /**
         * Returns the architecture label.
         *
         * @return label
         */
        public String label() {
            return label;
        }
    }

    /**
     * Stockfish big or small NNUE.
     */
    public enum Size {

        /**
         * Big Stockfish net. Uses PSQ and FullThreats features.
         */
        BIG,

        /**
         * Small Stockfish net. Uses only PSQ features.
         */
        SMALL
    }

    /**
     * Variant.
     */
    private final Variant variant;

    /**
     * Big/small size.
     */
    private final Size size;

    /**
     * Network description from the file header.
     */
    private final String description;

    /**
     * Header hash.
     */
    private final int networkHash;

    /**
     * Feature transformer.
     */
    private final FeatureTransformer transformer;

    /**
     * Bucketed network layer stacks.
     */
    private final Architecture[] layerStacks;

    /**
     * Creates a loaded Stockfish NNUE network.
     *
     * @param variant variant
     * @param size big/small size
     * @param description file description
     * @param networkHash network hash
     * @param transformer feature transformer
     * @param layerStacks bucketed architecture stacks
     */
    private UpstreamNetwork(
            Variant variant,
            Size size,
            String description,
            int networkHash,
            FeatureTransformer transformer,
            Architecture[] layerStacks) {
        this.variant = variant;
        this.size = size;
        this.description = description;
        this.networkHash = networkHash;
        this.transformer = transformer;
        this.layerStacks = layerStacks;
    }

    /**
     * Loads a Stockfish NNUE file, auto-detecting supported variants by header hash.
     *
     * @param path file path
     * @return loaded network
     * @throws IOException if the file is unsupported or invalid
     */
    public static UpstreamNetwork load(Path path) throws IOException {
        return load(path, null);
    }

    /**
     * Loads a Stockfish NNUE file with an optional preferred variant.
     *
     * <p>
     * The preferred variant is only needed for small nets whose Stockfish 18 and
     * current-development headers are intentionally identical.
     * </p>
     *
     * @param path file path
     * @param preferredVariant variant to use for ambiguous small-net headers, or null
     * @return loaded network
     * @throws IOException if the file is unsupported or invalid
     */
    public static UpstreamNetwork load(Path path, Variant preferredVariant) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path == null");
        }
        byte[] data = Files.readAllBytes(path);
        return load(data, preferredVariant, path.getFileName() == null ? "" : path.getFileName().toString());
    }

    /**
     * Loads a Stockfish NNUE file from bytes.
     *
     * @param data file bytes
     * @param preferredVariant variant to use for ambiguous small-net headers, or null
     * @param filename source filename used for known Stockfish default-net hints
     * @return loaded network
     * @throws IOException if parsing fails
     */
    static UpstreamNetwork load(byte[] data, Variant preferredVariant, String filename) throws IOException {
        try {
            Cursor cursor = new Cursor(data);
            int version = cursor.readInt();
            if (version != VERSION) {
                throw new IOException("Not a Stockfish NNUE file (unsupported version/header).");
            }
            int hash = cursor.readInt();
            String description = cursor.readString(cursor.readInt());
            Layout layout = detectLayout(hash, preferredVariant, filename);

            int transformerHash = cursor.readInt();
            if (transformerHash != layout.featureHash()) {
                throw new IOException("Stockfish NNUE feature-transformer hash mismatch.");
            }
            FeatureTransformer transformer = FeatureTransformer.read(cursor, layout);

            Architecture[] stacks = new Architecture[LAYER_STACKS];
            for (int i = 0; i < stacks.length; i++) {
                int stackHash = cursor.readInt();
                if (stackHash != layout.archHash()) {
                    throw new IOException("Stockfish NNUE layer-stack hash mismatch at bucket " + i + ".");
                }
                stacks[i] = Architecture.read(cursor, layout);
            }
            if (cursor.hasRemaining()) {
                throw new IOException("Unexpected bytes at end of Stockfish NNUE file.");
            }
            return new UpstreamNetwork(
                    layout.variant,
                    layout.size,
                    description,
                    hash,
                    transformer,
                    stacks);
        } catch (BufferUnderflowException | IllegalArgumentException ex) {
            throw new IOException("Invalid Stockfish NNUE file.", ex);
        }
    }

    /**
     * Returns whether bytes look like a supported Stockfish NNUE file header.
     *
     * @param data bytes to inspect
     * @return true for Stockfish NNUE version header
     */
    static boolean hasUpstreamHeader(byte[] data) {
        if (data == null || data.length < Integer.BYTES) {
            return false;
        }
        int version = (data[0] & 0xff)
                | ((data[1] & 0xff) << 8)
                | ((data[2] & 0xff) << 16)
                | ((data[3] & 0xff) << 24);
        return version == VERSION;
    }

    /**
     * Creates a synthetic network from already-decoded parts. Intended for tests.
     *
     * @param variant architecture variant
     * @param size big/small size
     * @param transformer feature transformer
     * @param stacks layer stacks
     * @return network
     */
    static UpstreamNetwork createSynthetic(
            Variant variant,
            Size size,
            FeatureTransformer transformer,
            Architecture[] stacks) {
        Layout layout = Layout.of(variant, size);
        return new UpstreamNetwork(variant, size, "synthetic", layout.networkHash(), transformer, stacks);
    }

    /**
     * Evaluates a position.
     *
     * @param position position to evaluate
     * @return Stockfish NNUE prediction
     */
    public Prediction predict(Position position) {
        return predict(position, null);
    }

    /**
     * Evaluates a position and optionally captures Stockfish NNUE activations.
     *
     * @param position position to evaluate
     * @param sink activation collector; null for production callers
     * @return Stockfish NNUE prediction
     */
    public Prediction predict(Position position, chess.nn.ActivationSink sink) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        int[] board = UpstreamFeatures.board(position);
        int bucket = bucket(UpstreamFeatures.pieceCount(board));

        TransformOutput transformed = transformer.transform(position, board, bucket);
        Architecture architecture = layerStacks[bucket];
        Architecture.Scratch scratch = architecture.newScratch();
        int positional = architecture.propagate(transformed.features, scratch);
        Prediction prediction = new Prediction(transformed.psqt / OUTPUT_SCALE, positional / OUTPUT_SCALE);
        if (sink != null) {
            captureActivations(sink, position, board, bucket, transformed, architecture, scratch, prediction);
        }
        return prediction;
    }

    /**
     * Captures intermediate activations for Workbench-style inspection.
     *
     * @param sink activation collector
     * @param position position
     * @param board Stockfish-order board
     * @param bucket material bucket
     * @param transformed feature-transform output
     * @param architecture selected dense layer stack
     * @param scratch populated dense-layer workspace
     * @param prediction final prediction
     */
    private void captureActivations(
            chess.nn.ActivationSink sink,
            Position position,
            int[] board,
            int bucket,
            TransformOutput transformed,
            Architecture architecture,
            Architecture.Scratch scratch,
            Prediction prediction) {
        int stm = UpstreamFeatures.sideToMove(position);
        int opponent = stm ^ 1;
        int hidden = transformer.transformedDimensions;
        int half = hidden / 2;
        int[] usHalfKa = UpstreamFeatures.activeHalfKa(board, stm);
        int[] themHalfKa = UpstreamFeatures.activeHalfKa(board, opponent);
        int[] usThreats = size == Size.BIG ? UpstreamFeatures.activeThreats(board, stm, variant) : new int[0];
        int[] themThreats = size == Size.BIG ? UpstreamFeatures.activeThreats(board, opponent, variant) : new int[0];

        sink.put("nnue.stockfish.bucket", new int[] { 1 }, new float[] { bucket });
        sink.put("nnue.stockfish.psqt.cp", new int[] { 1 }, new float[] { prediction.psqt() });
        sink.put("nnue.stockfish.positional.cp", new int[] { 1 }, new float[] { prediction.positional() });
        sink.put("nnue.stockfish.transformed", new int[] { hidden }, toFloats(transformed.features));
        sink.put("nnue.stockfish.transformed.us", new int[] { half },
                toFloats(transformed.features, 0, half));
        sink.put("nnue.stockfish.transformed.them", new int[] { half },
                toFloats(transformed.features, half, half));

        sink.put("nnue.features.us.indices", new int[] { usHalfKa.length }, toFloats(usHalfKa));
        sink.put("nnue.features.them.indices", new int[] { themHalfKa.length }, toFloats(themHalfKa));
        sink.put("nnue.features.us.impact", new int[] { usHalfKa.length },
                featureImpacts(usHalfKa, half));
        sink.put("nnue.features.them.impact", new int[] { themHalfKa.length },
                featureImpacts(themHalfKa, half));
        sink.put("nnue.features.us.weights", new int[] { usHalfKa.length, half },
                featureWeights(usHalfKa, half));
        sink.put("nnue.features.them.weights", new int[] { themHalfKa.length, half },
                featureWeights(themHalfKa, half));
        sink.put("nnue.stockfish.features.us.threat.indices", new int[] { usThreats.length }, toFloats(usThreats));
        sink.put("nnue.stockfish.features.them.threat.indices", new int[] { themThreats.length }, toFloats(themThreats));

        int l2 = architecture.layout.l2;
        int l3 = architecture.layout.l3;
        sink.put("nnue.stockfish.fc0.raw", new int[] { l2 + 1 }, toFloats(scratch.fc0Out));
        sink.put("nnue.stockfish.fc0.sqr", new int[] { l2 }, toFloats(scratch.fc1Input, 0, l2));
        sink.put("nnue.stockfish.fc0.crelu", new int[] { l2 }, toFloats(scratch.fc1Input, l2, l2));
        sink.put("nnue.stockfish.fc0.weights.us", new int[] { l2, half },
                affineWeights(architecture.fc0, l2, 0, half));
        sink.put("nnue.stockfish.fc0.weights.fwd.us", new int[] { half },
                affineWeightRow(architecture.fc0, l2, 0, half));
        sink.put("nnue.stockfish.fc1.input", new int[] { l2 * 2 }, toFloats(scratch.fc1Input));
        sink.put("nnue.stockfish.fc1.raw", new int[] { l3 }, toFloats(scratch.fc1Out));
        sink.put("nnue.stockfish.fc1.clipped", new int[] { l3 }, toFloats(scratch.fc2Input));
        sink.put("nnue.stockfish.fc1.weights.combined", new int[] { l3, l2 },
                combinedFc1Weights(architecture.fc1, l2, l3));

        float[] fc2Weights = affineWeights(architecture.fc2, 1, 0, l3);
        float[] fc2Contribution = new float[l3];
        float fc2TermsCp = 0.0f;
        for (int i = 0; i < l3; i++) {
            fc2Contribution[i] = scratch.fc2Input[i] * fc2Weights[i] / (float) OUTPUT_SCALE;
            fc2TermsCp += fc2Contribution[i];
        }
        float fc2BiasCp = architecture.fc2.biases[0] / (float) OUTPUT_SCALE;
        float fwdCp = stockfishFwdContributionCp(scratch.fc0Out[l2]);
        sink.put("nnue.stockfish.fc2.weights", new int[] { l3 }, fc2Weights);
        sink.put("nnue.stockfish.fc2.contribution", new int[] { l3 }, fc2Contribution);
        sink.put("nnue.stockfish.fc2.bias.cp", new int[] { 1 }, new float[] { fc2BiasCp });
        sink.put("nnue.stockfish.fc0.fwd.cp", new int[] { 1 }, new float[] { fwdCp });
        sink.put("nnue.stockfish.output.parts", new int[] { 4 },
                new float[] { prediction.psqt(), fc2BiasCp, fc2TermsCp, fwdCp });

        // Compatibility tensors consumed by the existing NNUE workbench panels.
        sink.put("nnue.accumulator.us", new int[] { half }, toFloats(transformed.features, 0, half));
        sink.put("nnue.accumulator.them", new int[] { half }, toFloats(transformed.features, half, half));
        sink.put("nnue.clipped.us", new int[] { half }, toFloats(transformed.features, 0, half));
        sink.put("nnue.clipped.them", new int[] { half }, toFloats(transformed.features, half, half));
        sink.put("nnue.output.weights.us", new int[] { l3 }, fc2Weights);
        sink.put("nnue.output.weights.them", new int[] { l3 }, new float[l3]);
        sink.put("nnue.output.contribution.us", new int[] { l3 }, fc2Contribution);
        sink.put("nnue.output.contribution.them", new int[] { l3 }, new float[l3]);
        sink.put("nnue.output.contribution.total", new int[] { l3 }, fc2Contribution);
        sink.put("nnue.output.affine", new int[] { 1 }, new float[] { prediction.centipawns() });
        sink.put("nnue.output.centipawns", new int[] { 1 }, new float[] { prediction.centipawns() });
    }

    /**
     * Builds an approximate feature-to-transformer row matrix for active HalfKA
     * features. Stockfish's transformer multiplies paired accumulator halves, so
     * this combines each pair into one visible transformed lane.
     *
     * @param features active feature indices
     * @param half visible half width
     * @return row-major weights
     */
    private float[] featureWeights(int[] features, int half) {
        float[] out = new float[features.length * half];
        int hidden = transformer.transformedDimensions;
        for (int row = 0; row < features.length; row++) {
            int feature = features[row];
            int base = feature * hidden;
            for (int col = 0; col < half; col++) {
                out[row * half + col] = (float) transformer.psqWeights[base + col]
                        + transformer.psqWeights[base + half + col];
            }
        }
        return out;
    }

    /**
     * Computes a compact per-feature magnitude for active feature lanes.
     *
     * @param features active feature indices
     * @param half visible half width
     * @return impact proxy
     */
    private float[] featureImpacts(int[] features, int half) {
        float[] out = new float[features.length];
        float[] rows = featureWeights(features, half);
        for (int row = 0; row < features.length; row++) {
            float sum = 0.0f;
            for (int col = 0; col < half; col++) {
                sum += rows[row * half + col];
            }
            out[row] = sum / Math.max(1, half);
        }
        return out;
    }

    /**
     * Extracts a slice of affine-layer weights as floats.
     *
     * @param layer affine layer
     * @param rows row count to copy
     * @param inputOffset first input column
     * @param cols column count
     * @return row-major weight matrix
     */
    private static float[] affineWeights(AffineLayer layer, int rows, int inputOffset, int cols) {
        float[] out = new float[rows * cols];
        for (int row = 0; row < rows; row++) {
            int base = row * layer.paddedInputDimensions + inputOffset;
            for (int col = 0; col < cols; col++) {
                out[row * cols + col] = layer.weights[base + col];
            }
        }
        return out;
    }

    /**
     * Extracts one affine-layer weight row as floats.
     *
     * @param layer affine layer
     * @param row row to copy
     * @param inputOffset first input column
     * @param cols column count
     * @return copied row
     */
    private static float[] affineWeightRow(AffineLayer layer, int row, int inputOffset, int cols) {
        float[] out = new float[cols];
        int base = row * layer.paddedInputDimensions + inputOffset;
        for (int col = 0; col < cols; col++) {
            out[col] = layer.weights[base + col];
        }
        return out;
    }

    /**
     * Combines Stockfish FC1's squared-ReLU and clipped-ReLU inputs into one
     * weight per visible FC0 lane.
     *
     * @param layer FC1 layer
     * @param l2 FC0 hidden width
     * @param l3 FC1 hidden width
     * @return row-major combined weights
     */
    private static float[] combinedFc1Weights(AffineLayer layer, int l2, int l3) {
        float[] out = new float[l3 * l2];
        for (int row = 0; row < l3; row++) {
            int base = row * layer.paddedInputDimensions;
            for (int col = 0; col < l2; col++) {
                out[row * l2 + col] = (float) layer.weights[base + col]
                        + layer.weights[base + l2 + col];
            }
        }
        return out;
    }

    /**
     * Converts Stockfish's FC0 forward row into centipawns.
     *
     * @param fc0Fwd raw FC0 forward output
     * @return forward contribution in centipawns
     */
    private static float stockfishFwdContributionCp(int fc0Fwd) {
        return fc0Fwd * 600.0f / (127.0f * (1 << WEIGHT_SCALE_BITS));
    }

    /**
     * Converts an int array to floats.
     *
     * @param values values
     * @return converted values
     */
    private static float[] toFloats(int[] values) {
        return toFloats(values, 0, values == null ? 0 : values.length);
    }

    /**
     * Converts an int array slice to floats.
     *
     * @param values values
     * @param offset first index
     * @param length slice length
     * @return converted values
     */
    private static float[] toFloats(int[] values, int offset, int length) {
        float[] out = new float[Math.max(0, length)];
        if (values == null) {
            return out;
        }
        for (int i = 0; i < out.length && offset + i < values.length; i++) {
            out[i] = values[offset + i];
        }
        return out;
    }

    /**
     * Opens optional per-search incremental state.
     *
     * <p>
     * This currently supports Stockfish small nets, which are the default
     * HalfKAv2_hm-style nets without threat features. Big nets still fall back to
     * full rebuild evaluation.
     * </p>
     *
     * @param root root position at ply 0
     * @param searchPlies maximum ply count the search may reach
     * @return incremental state, or {@code null} when unsupported
     */
    public Model.SearchState newSearchState(Position root, int searchPlies) {
        if (root == null) {
            throw new IllegalArgumentException("root == null");
        }
        if (searchPlies <= 0) {
            throw new IllegalArgumentException("searchPlies must be positive");
        }
        if (size != Size.SMALL) {
            // BIG nets keep PSQ (HalfKA) incremental and rebuild threats per node.
            return transformer.useThreats() ? new BigSearchState(root, searchPlies) : null;
        }
        return new SmallSearchState(root, searchPlies);
    }

    /**
     * Per-search incremental state for Stockfish small nets.
     */
    private final class SmallSearchState implements Model.SearchState {

        /**
         * Dedicated buffers indexed by ply.
         */
        private final SmallSearchSlot[] buffers;

        /**
         * Active slot views indexed by ply.
         */
        private final SmallSearchSlot[] active;

        /**
         * Reusable dense-layer workspace for search-time evaluation.
         */
        private final Architecture.Scratch scratch;

        /**
         * Creates incremental state rooted at one position.
         *
         * @param root root position
         * @param searchPlies maximum ply count
         */
        private SmallSearchState(Position root, int searchPlies) {
            this.buffers = new SmallSearchSlot[searchPlies];
            this.active = new SmallSearchSlot[searchPlies];
            this.scratch = layerStacks[0].newScratch();
            for (int ply = 0; ply < searchPlies; ply++) {
                buffers[ply] = new SmallSearchSlot(transformer.transformedDimensions);
            }
            refreshSmallSlot(buffers[0], root);
            active[0] = buffers[0];
        }

	        /**
	         * Updates the child search slot after a normal move.
	         *
	         * @param position child position after the move
	         * @param move encoded move that was played
	         * @param state undo state filled by the move application
	         * @param ply child ply from the root
	         */
	        @Override
	        public void movePlayed(Position position, short move, Position.State state, int ply) {
	            SmallSearchSlot child = buffers[ply];
	            child.copyFrom(active[ply - 1]);
            active[ply] = child;
            updatePerspective(child, position, move, state, UpstreamFeatures.WHITE);
	            updatePerspective(child, position, move, state, UpstreamFeatures.BLACK);
	        }

	        /**
	         * Reuses the parent search slot for a null-move child ply.
	         *
	         * @param ply child ply from the root
	         */
	        @Override
	        public void nullMovePlayed(int ply) {
	            active[ply] = active[ply - 1];
	        }

	        /**
	         * Evaluates the active small-network slot at one search ply.
	         *
	         * @param position current position
	         * @param ply current ply from the root
	         * @return centipawn score from the side-to-move perspective
	         */
	        @Override
	        public int evaluate(Position position, int ply) {
	            return evaluateSmall(active[ply], position, scratch);
        }

        /**
         * Updates one perspective after a move has been played.
         *
         * @param slot child slot
         * @param position child position
         * @param move encoded move
         * @param state undo state filled by the move application
         * @param perspective Stockfish color id
         */
        private void updatePerspective(
                SmallSearchSlot slot,
                Position position,
                short move,
                Position.State state,
                int perspective) {
            int moving = state.movingPiece();
            if (moving == kingPieceIndex(perspective == UpstreamFeatures.WHITE)) {
                refreshSmallPerspective(slot, position, perspective);
                return;
            }

            updateHalfKaMove(
                    slot,
                    position,
                    perspective,
                    moving,
                    Move.getFromIndex(move),
                    state.actualToSquare(),
                    promotedPieceIndex(moving, Move.getPromotion(move)));
            if (state.capture()) {
                updateHalfKaRemoval(slot, position, perspective, state.capturedPiece(), state.capturedSquare());
            }
            if (state.castle()) {
                updateHalfKaMove(
                        slot,
                        position,
                        perspective,
                        state.rookPiece(),
                        state.rookFromSquare(),
                        state.rookToSquare(),
                        state.rookPiece());
            }
        }
    }

    /**
     * Per-search incremental state for the BIG nets (threats present). Keeps the
     * HalfKA PSQ accumulators incremental exactly like {@link SmallSearchState},
     * but rebuilds the threat accumulators from scratch each evaluation, because
     * threat features depend non-locally on board occupancy and are not cleanly
     * incremental. The combined PSQ+threat result is bit-identical to a full
     * {@link FeatureTransformer#transform} rebuild (verified by parity tests).
     */
    private final class BigSearchState implements Model.SearchState {

        /**
         * PSQ accumulator slots indexed by ply.
         */
        private final SmallSearchSlot[] buffers;

        /**
         * Active PSQ slot views indexed by ply.
         */
        private final SmallSearchSlot[] active;

        /**
         * Reusable dense-layer workspace.
         */
        private final Architecture.Scratch scratch;

        /**
         * Reusable per-perspective threat accumulators, rebuilt each evaluation.
         */
        private final int[][] threatAccumulation;

        /**
         * Reusable per-perspective threat PSQT accumulators.
         */
        private final int[][] threatPsqtAccumulation;

        /**
         * Creates incremental BIG-net state rooted at one position.
         *
         * @param root root position
         * @param searchPlies maximum ply count
         */
        private BigSearchState(Position root, int searchPlies) {
            this.buffers = new SmallSearchSlot[searchPlies];
            this.active = new SmallSearchSlot[searchPlies];
            this.scratch = layerStacks[0].newScratch();
            this.threatAccumulation = new int[2][transformer.transformedDimensions];
            this.threatPsqtAccumulation = new int[2][LAYER_STACKS];
            for (int ply = 0; ply < searchPlies; ply++) {
                buffers[ply] = new SmallSearchSlot(transformer.transformedDimensions);
            }
            refreshSmallSlot(buffers[0], root);
            active[0] = buffers[0];
        }

        /**
         * Updates the child PSQ slot incrementally after a normal move.
         *
         * @param position child position after the move
         * @param move encoded move that was played
         * @param state undo state filled by the move application
         * @param ply child ply from the root
         */
        @Override
        public void movePlayed(Position position, short move, Position.State state, int ply) {
            SmallSearchSlot child = buffers[ply];
            child.copyFrom(active[ply - 1]);
            active[ply] = child;
            updateBigPerspective(child, position, move, state, UpstreamFeatures.WHITE);
            updateBigPerspective(child, position, move, state, UpstreamFeatures.BLACK);
        }

        /**
         * Reuses the parent slot for a null-move child ply.
         *
         * @param ply child ply from the root
         */
        @Override
        public void nullMovePlayed(int ply) {
            active[ply] = active[ply - 1];
        }

        /**
         * Evaluates the active BIG-net slot, rebuilding threats for this node.
         *
         * @param position current position
         * @param ply current ply from the root
         * @return centipawns from the side-to-move perspective
         */
        @Override
        public int evaluate(Position position, int ply) {
            return evaluateBig(active[ply], position, threatAccumulation, threatPsqtAccumulation, scratch);
        }

        /**
         * Updates one perspective's PSQ accumulator after a move (mirrors
         * {@link SmallSearchState}'s logic; threats are not part of the slot).
         *
         * @param slot child slot
         * @param position child position
         * @param move encoded move
         * @param state undo state filled by the move application
         * @param perspective Stockfish color id
         */
        private void updateBigPerspective(
                SmallSearchSlot slot,
                Position position,
                short move,
                Position.State state,
                int perspective) {
            int moving = state.movingPiece();
            if (moving == kingPieceIndex(perspective == UpstreamFeatures.WHITE)) {
                refreshSmallPerspective(slot, position, perspective);
                return;
            }
            updateHalfKaMove(
                    slot,
                    position,
                    perspective,
                    moving,
                    Move.getFromIndex(move),
                    state.actualToSquare(),
                    promotedPieceIndex(moving, Move.getPromotion(move)));
            if (state.capture()) {
                updateHalfKaRemoval(slot, position, perspective, state.capturedPiece(), state.capturedSquare());
            }
            if (state.castle()) {
                updateHalfKaMove(
                        slot,
                        position,
                        perspective,
                        state.rookPiece(),
                        state.rookFromSquare(),
                        state.rookToSquare(),
                        state.rookPiece());
            }
        }
    }

    /**
     * Evaluates one position from an incremental BIG-net slot: PSQ accumulators
     * are incremental, threat accumulators are rebuilt here. Combines PSQ and
     * threats exactly as {@link FeatureTransformer#transform}, so the result is
     * bit-identical to a full rebuild.
     *
     * @param slot active PSQ slot
     * @param position current position
     * @param threatAccumulation reusable per-perspective threat accumulators
     * @param threatPsqtAccumulation reusable per-perspective threat PSQT accumulators
     * @param scratch dense-layer workspace
     * @return centipawns from the side-to-move perspective
     */
    private int evaluateBig(
            SmallSearchSlot slot,
            Position position,
            int[][] threatAccumulation,
            int[][] threatPsqtAccumulation,
            Architecture.Scratch scratch) {
        int[] board = UpstreamFeatures.board(position);
        int bucket = bucket(UpstreamFeatures.pieceCount(board));
        int stm = UpstreamFeatures.sideToMove(position);
        int opponent = stm ^ 1;
        transformer.accumulateThreatFeatures(board, stm, threatAccumulation[stm], threatPsqtAccumulation[stm]);
        transformer.accumulateThreatFeatures(
                board, opponent, threatAccumulation[opponent], threatPsqtAccumulation[opponent]);

        int psqt = slot.psqtAccumulation[stm][bucket] - slot.psqtAccumulation[opponent][bucket];
        psqt = (psqt + threatPsqtAccumulation[stm][bucket] - threatPsqtAccumulation[opponent][bucket]) / 2;

        int half = transformer.transformedDimensions / 2;
        int smallClamp = variant.scaleSmallTransformer ? 254 : 255;
        transformer.writePerspectiveFeatures(
                slot.transformed, 0, slot.psqAccumulation[stm], threatAccumulation[stm], half, smallClamp);
        transformer.writePerspectiveFeatures(
                slot.transformed, half, slot.psqAccumulation[opponent], threatAccumulation[opponent], half, smallClamp);
        int positional = layerStacks[bucket].propagate(slot.transformed, scratch);
        return (psqt / OUTPUT_SCALE) + (positional / OUTPUT_SCALE);
    }

    /**
     * Mutable small-net accumulator slot.
     */
    private static final class SmallSearchSlot {

        /**
         * PSQ accumulators indexed by perspective.
         */
        private final int[][] psqAccumulation;

        /**
         * PSQT accumulators indexed by perspective.
         */
        private final int[][] psqtAccumulation;

        /**
         * Reusable transformed feature buffer.
         */
        private final int[] transformed;

        /**
         * Creates a slot.
         *
         * @param transformedDimensions transformed feature width
         */
        private SmallSearchSlot(int transformedDimensions) {
            this.psqAccumulation = new int[2][transformedDimensions];
            this.psqtAccumulation = new int[2][LAYER_STACKS];
            this.transformed = new int[transformedDimensions];
        }

        /**
         * Copies another slot into this slot.
         *
         * @param other source slot
         */
        private void copyFrom(SmallSearchSlot other) {
            for (int perspective = 0; perspective < 2; perspective++) {
                System.arraycopy(other.psqAccumulation[perspective], 0,
                        psqAccumulation[perspective], 0, psqAccumulation[perspective].length);
                System.arraycopy(other.psqtAccumulation[perspective], 0,
                        psqtAccumulation[perspective], 0, LAYER_STACKS);
            }
        }
    }

    /**
     * Rebuilds one small-net slot from a position.
     *
     * @param slot slot to rebuild
     * @param position position to encode
     */
    private void refreshSmallSlot(SmallSearchSlot slot, Position position) {
        int[] board = UpstreamFeatures.board(position);
        refreshSmallPerspective(slot, board, UpstreamFeatures.WHITE);
        refreshSmallPerspective(slot, board, UpstreamFeatures.BLACK);
    }

    /**
     * Rebuilds one small-net perspective from a child position.
     *
     * @param slot slot to rebuild
     * @param position child position
     * @param perspective Stockfish color id
     */
    private void refreshSmallPerspective(SmallSearchSlot slot, Position position, int perspective) {
        refreshSmallPerspective(slot, UpstreamFeatures.board(position), perspective);
    }

    /**
     * Rebuilds one small-net perspective from a precomputed board.
     *
     * @param slot slot to rebuild
     * @param board position in Stockfish order
     * @param perspective Stockfish color id
     */
    private void refreshSmallPerspective(SmallSearchSlot slot, int[] board, int perspective) {
        Arrays.fill(slot.psqtAccumulation[perspective], 0);
        transformer.accumulatePerspectiveFeatures(
                board,
                perspective,
                slot.psqAccumulation[perspective],
                slot.psqtAccumulation[perspective],
                null,
                null);
    }

    /**
     * Evaluates one position from an incremental small-net slot.
     *
     * @param slot active slot
     * @param position current position
     * @return centipawns from the side-to-move perspective
     * @param scratch scratch value
     */
    private int evaluateSmall(SmallSearchSlot slot, Position position, Architecture.Scratch scratch) {
        int bucket = bucket(position.countTotalPieces());
        int stm = UpstreamFeatures.sideToMove(position);
        int opponent = stm ^ 1;
        int psqt = (slot.psqtAccumulation[stm][bucket] - slot.psqtAccumulation[opponent][bucket]) / 2;

        int half = transformer.transformedDimensions / 2;
        int smallClamp = variant.scaleSmallTransformer ? 254 : 255;
        transformer.writePerspectiveFeatures(
                slot.transformed,
                0,
                slot.psqAccumulation[stm],
                null,
                half,
                smallClamp);
        transformer.writePerspectiveFeatures(
                slot.transformed,
                half,
                slot.psqAccumulation[opponent],
                null,
                half,
                smallClamp);
        int positional = layerStacks[bucket].propagate(slot.transformed, scratch);
        return (psqt / OUTPUT_SCALE) + (positional / OUTPUT_SCALE);
    }

    /**
     * Applies one moved-piece HalfKA delta.
     *
     * @param slot slot to update
     * @param position child position
     * @param perspective Stockfish color id
     * @param sourcePiece moving piece before the move
     * @param fromSquare origin square in {@link Position} order
     * @param toSquare destination square in {@link Position} order
     * @param targetPiece placed piece after the move
     */
    private void updateHalfKaMove(
            SmallSearchSlot slot,
            Position position,
            int perspective,
            int sourcePiece,
            int fromSquare,
            int toSquare,
            int targetPiece) {
        int removeFeature = halfKaFeatureIndex(position, perspective, sourcePiece, fromSquare);
        int addFeature = halfKaFeatureIndex(position, perspective, targetPiece, toSquare);
        applyHalfKaDelta(slot, perspective, removeFeature, addFeature);
    }

    /**
     * Applies one removed-piece HalfKA delta.
     *
     * @param slot slot to update
     * @param position child position
     * @param perspective Stockfish color id
     * @param piece removed piece
     * @param square removed-piece square in {@link Position} order
     */
    private void updateHalfKaRemoval(
            SmallSearchSlot slot,
            Position position,
            int perspective,
            int piece,
            int square) {
        int feature = halfKaFeatureIndex(position, perspective, piece, square);
        applyHalfKaDelta(slot, perspective, feature, -1);
    }

    /**
     * Applies a HalfKA feature delta to one perspective.
     *
     * @param slot slot to update
     * @param perspective Stockfish color id
     * @param removeFeature feature to remove, or {@code -1}
     * @param addFeature feature to add, or {@code -1}
     */
    private void applyHalfKaDelta(
            SmallSearchSlot slot,
            int perspective,
            int removeFeature,
            int addFeature) {
        if (removeFeature >= 0) {
            addHalfKaFeature(slot, perspective, removeFeature, -1);
        }
        if (addFeature >= 0) {
            addHalfKaFeature(slot, perspective, addFeature, 1);
        }
    }

    /**
     * Adds or removes one HalfKA feature.
     *
     * @param slot slot to update
     * @param perspective Stockfish color id
     * @param feature feature index
     * @param sign {@code +1} to add, {@code -1} to remove
     */
    private void addHalfKaFeature(SmallSearchSlot slot, int perspective, int feature, int sign) {
        int weightBase = feature * transformer.transformedDimensions;
        for (int index = 0; index < transformer.transformedDimensions; index++) {
            slot.psqAccumulation[perspective][index] += sign * transformer.psqWeights[weightBase + index];
        }
        int psqtBase = feature * LAYER_STACKS;
        for (int bucket = 0; bucket < LAYER_STACKS; bucket++) {
            slot.psqtAccumulation[perspective][bucket] += sign * transformer.psqtWeights[psqtBase + bucket];
        }
    }

    /**
     * Returns one HalfKA feature index for a position piece.
     *
     * @param position child position
     * @param perspective Stockfish color id
     * @param pieceIndex internal piece index
     * @param square square in {@link Position} order
     * @return feature index, or {@code -1} when unavailable
     */
    private static int halfKaFeatureIndex(Position position, int perspective, int pieceIndex, int square) {
        int piece = UpstreamFeatures.encodedPiece(pieceCode(pieceIndex));
        if (piece == 0) {
            return -1;
        }
        int kingSquare = UpstreamFeatures.squareFromPositionIndex(
                position.kingSquare(perspective == UpstreamFeatures.WHITE));
        return UpstreamFeatures.halfKaFeatureIndex(
                perspective,
                UpstreamFeatures.squareFromPositionIndex(square),
                piece,
                kingSquare);
    }

    /**
     * Returns the Stockfish bucket for a piece count.
     *
     * @param pieceCount piece count
     * @return bucket index
     */
    private static int bucket(int pieceCount) {
        int bucket = (pieceCount - 1) / 4;
        if (bucket < 0) {
            return 0;
        }
        return Math.min(LAYER_STACKS - 1, bucket);
    }

    /**
     * Returns the internal king piece index for one side.
     *
     * @param white true for White
     * @return internal king piece index
     */
    private static int kingPieceIndex(boolean white) {
        return white ? Position.WHITE_KING : Position.BLACK_KING;
    }

    /**
     * Returns the placed piece index after a move.
     *
     * @param movingPiece moving piece before the move
     * @param promotion promotion code
     * @return placed internal piece index
     */
    private static int promotedPieceIndex(int movingPiece, int promotion) {
        if (promotion == 0) {
            return movingPiece;
        }
        boolean white = movingPiece < Position.BLACK_PAWN;
        return switch (promotion) {
            case 1 -> white ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT;
            case 2 -> white ? Position.WHITE_BISHOP : Position.BLACK_BISHOP;
            case 3 -> white ? Position.WHITE_ROOK : Position.BLACK_ROOK;
            case 4 -> white ? Position.WHITE_QUEEN : Position.BLACK_QUEEN;
            default -> movingPiece;
        };
    }

    /**
     * Maps an internal piece index to the repository piece code.
     *
     * @param pieceIndex internal piece index
     * @return repository piece code, or {@link Piece#EMPTY}
     */
    private static byte pieceCode(int pieceIndex) {
        return switch (pieceIndex) {
            case Position.WHITE_PAWN -> Piece.WHITE_PAWN;
            case Position.WHITE_KNIGHT -> Piece.WHITE_KNIGHT;
            case Position.WHITE_BISHOP -> Piece.WHITE_BISHOP;
            case Position.WHITE_ROOK -> Piece.WHITE_ROOK;
            case Position.WHITE_QUEEN -> Piece.WHITE_QUEEN;
            case Position.WHITE_KING -> Piece.WHITE_KING;
            case Position.BLACK_PAWN -> Piece.BLACK_PAWN;
            case Position.BLACK_KNIGHT -> Piece.BLACK_KNIGHT;
            case Position.BLACK_BISHOP -> Piece.BLACK_BISHOP;
            case Position.BLACK_ROOK -> Piece.BLACK_ROOK;
            case Position.BLACK_QUEEN -> Piece.BLACK_QUEEN;
            case Position.BLACK_KING -> Piece.BLACK_KING;
            default -> Piece.EMPTY;
        };
    }

    /**
     * Writes the position-independent feature-weight atlas to {@code sink}.
     *
     * <p>For each hidden accumulator slot the atlas marginalises the king-
     * square dimension out of the HalfKAv2_hm feature transformer to expose
     * a per-piece-type, per-board-square weight footprint. The result is the
     * Wikipedia-style weight image: every tile is one (slot, piece-type,
     * board-square) tuple averaged across all 64 king positions.</p>
     *
     * <p>Eleven piece planes are produced, mapped to the network's input
     * encoding:</p>
     * <ol>
     *   <li>0..4 — own P/N/B/R/Q (white perspective)</li>
     *   <li>5..9 — enemy P/N/B/R/Q</li>
     *   <li>10 — kings (own and enemy share the king plane in HalfKAv2_hm)</li>
     * </ol>
     *
     * <p>Outputs:</p>
     * <ul>
     *   <li>{@code nnue.atlas.weights} — shape {@code [hidden, 11, 64]}</li>
     *   <li>{@code nnue.atlas.king} — shape {@code [hidden, 64]}, average
     *       weight per king-square (across all pieces and squares)</li>
     *   <li>{@code nnue.atlas.output} — shape {@code [hidden]}, neuron
     *       magnitude proxy for tile sort ordering</li>
     * </ul>
     *
     * @param sink activation collector; must not be null
     */
    public void dumpFeatureAtlas(chess.nn.ActivationSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink == null");
        }
        int hidden = transformer.transformedDimensions;
        int planes = 11;
        int squares = 64;
        int kings = 64;
        int[] pieceCodes = new int[planes];
        pieceCodes[0] = UpstreamFeatures.PAWN;
        pieceCodes[1] = UpstreamFeatures.KNIGHT;
        pieceCodes[2] = UpstreamFeatures.BISHOP;
        pieceCodes[3] = UpstreamFeatures.ROOK;
        pieceCodes[4] = UpstreamFeatures.QUEEN;
        pieceCodes[5] = UpstreamFeatures.PAWN | 8;
        pieceCodes[6] = UpstreamFeatures.KNIGHT | 8;
        pieceCodes[7] = UpstreamFeatures.BISHOP | 8;
        pieceCodes[8] = UpstreamFeatures.ROOK | 8;
        pieceCodes[9] = UpstreamFeatures.QUEEN | 8;
        pieceCodes[10] = UpstreamFeatures.KING;
        float[] atlas = new float[hidden * planes * squares];
        float[] kingMap = new float[hidden * squares];
        float[] magnitude = new float[hidden];
        float kingNorm = 1.0f / planes;
        float inv = 1.0f / kings;
        for (int k = 0; k < kings; k++) {
            for (int p = 0; p < planes; p++) {
                int pieceCode = pieceCodes[p];
                for (int s = 0; s < squares; s++) {
                    int feature = UpstreamFeatures.halfKaFeatureIndex(
                            UpstreamFeatures.WHITE, s, pieceCode, k);
                    if (feature < 0 || feature >= UpstreamFeatures.HALF_KA_DIMENSIONS) {
                        continue;
                    }
                    int base = feature * hidden;
                    int atlasOffset = p * squares + s;
                    for (int h = 0; h < hidden; h++) {
                        float w = transformer.psqWeights[base + h];
                        atlas[h * planes * squares + atlasOffset] += w * inv;
                        kingMap[h * squares + k] += w * inv * kingNorm;
                        magnitude[h] += Math.abs(w);
                    }
                }
            }
        }
        sink.put("nnue.atlas.weights", new int[] { hidden, planes, squares }, atlas);
        sink.put("nnue.atlas.king", new int[] { hidden, squares }, kingMap);
        sink.put("nnue.atlas.output", new int[] { hidden }, magnitude);
    }

    /**
     * Returns network metadata.
     *
     * @return metadata
     */
    public Info info() {
        return new Info(
                variant,
                size,
                transformer.totalInputDimensions(),
                transformer.transformedDimensions,
                layerStacks[0].fc0OutputDimensions - 1,
                layerStacks[0].fc1OutputDimensions,
                networkHash,
                description);
    }

    /**
     * Returns the active backend.
     *
     * @return backend identifier
     */
    public String backendName() {
        return BACKEND;
    }

    /**
     * Returns the architecture variant.
     *
     * @return variant
     */
    public Variant variant() {
        return variant;
    }

    /**
     * Returns whether this is a big or small network.
     *
     * @return size
     */
    public Size size() {
        return size;
    }

    /**
     * Releases resources. The Java implementation owns no native resources.
     */
    @Override
    public void close() {
        // no native resources
    }

    /**
     * Detects layout by network hash.
     *
     * @param hash network hash
     * @param preferredVariant preferred variant for ambiguous small nets
     * @param filename source filename
     * @return layout
     * @throws IOException if unsupported
     */
    private static Layout detectLayout(int hash, Variant preferredVariant, String filename) throws IOException {
        Layout match = null;
        for (Variant variant : Variant.values()) {
            for (Size size : Size.values()) {
                Layout layout = Layout.of(variant, size);
                if (layout.networkHash() == hash) {
                    if (match != null) {
                        Variant resolved = resolveAmbiguousSmallVariant(preferredVariant, filename);
                        return Layout.of(resolved, Size.SMALL);
                    }
                    match = layout;
                }
            }
        }
        if (match == null) {
            throw new IOException("Unsupported Stockfish NNUE architecture hash: 0x"
                    + Integer.toHexString(hash) + ".");
        }
        return match;
    }

    /**
     * Resolves small-net hash ambiguity.
     *
     * @param preferredVariant explicit preference
     * @param filename source filename
     * @return resolved variant
     */
    private static Variant resolveAmbiguousSmallVariant(Variant preferredVariant, String filename) {
        if (preferredVariant != null) {
            return preferredVariant;
        }
        if ("nn-37f18f62d772.nnue".equals(filename)) {
            return Variant.SF_18;
        }
        if ("nn-47fc8b7fff06.nnue".equals(filename)) {
            return Variant.CURRENT;
        }
        return Variant.CURRENT;
    }

    /**
     * Stockfish NNUE metadata.
     *
     * @param variant variant
     * @param size big/small size
     * @param inputFeatures total input feature dimensions
     * @param transformedDimensions transformed feature dimensions
     * @param l2 first dense hidden size
     * @param l3 second dense hidden size
     * @param hash network hash
     * @param description file description
     */
    public record Info(
        /**
         * Stores the variant.
         */
        Variant variant,
        /**
         * Stores the size.
         */
        Size size,
        /**
         * Stores the input feature count.
         */
        int inputFeatures,
        /**
         * Stores the transformed dimensions.
         */
        int transformedDimensions,
        /**
         * Stores the first hidden size.
         */
        int l2,
        /**
         * Stores the second hidden size.
         */
        int l3,
        /**
         * Stores the architecture hash.
         */
        int hash,
        /**
         * Stores the net description.
         */
        String description
    ) {
    }

    /**
     * Stockfish NNUE prediction.
     *
     * @param psqt material/PSQT contribution in centipawns
     * @param positional layer-stack contribution in centipawns
     */
    public record Prediction(
        /**
         * Stores the PSQT contribution.
         */
        int psqt,
        /**
         * Stores the positional contribution.
         */
        int positional
    ) {

        /**
         * Returns total centipawns from the side-to-move perspective.
         *
         * @return centipawns
         */
        public int centipawns() {
            return psqt + positional;
        }

        /**
         * Returns total score in pawns.
         *
         * @return pawn score
         */
        public float pawns() {
            return centipawns() / 100.0f;
        }
    }

    /**
     * Checks a length fits an int.
     *
     * @param length length
     * @param label label
     * @return int length
     */
    static int checkedLength(long length, String label) {
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " length is too large: " + length);
        }
        return (int) length;
    }

}
