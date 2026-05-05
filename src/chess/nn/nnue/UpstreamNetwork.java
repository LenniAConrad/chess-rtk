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
import utility.Numbers;

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
@SuppressWarnings("java:S3398")
public final class UpstreamNetwork implements AutoCloseable {

    /**
     * Stockfish NNUE file version.
     */
    static final int VERSION = 0x7AF32F20;

    /**
     * Output scale used by Stockfish.
     */
    private static final int OUTPUT_SCALE = 16;

    /**
     * Weight scale bits used by Stockfish clipped ReLU layers.
     */
    private static final int WEIGHT_SCALE_BITS = 6;

    /**
     * Number of PSQT buckets/layer stacks.
     */
    private static final int LAYER_STACKS = 8;

    /**
     * LEB128 array marker.
     */
    private static final byte[] LEB128_MAGIC = "COMPRESSED_LEB128".getBytes(StandardCharsets.US_ASCII);

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
        private final boolean combinedBigFeatureHash;

        /**
         * Whether small-net feature-transformer weights are doubled after loading.
         */
        private final boolean scaleSmallTransformer;

        /**
         * FullThreats dimensions.
         */
        private final int threatDimensions;

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
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        int[] board = UpstreamFeatures.board(position);
        int bucket = (UpstreamFeatures.pieceCount(board) - 1) / 4;
        if (bucket < 0) {
            bucket = 0;
        } else if (bucket >= LAYER_STACKS) {
            bucket = LAYER_STACKS - 1;
        }

        TransformOutput transformed = transformer.transform(position, board, bucket);
        int positional = layerStacks[bucket].propagate(transformed.features);
        return new Prediction(transformed.psqt / OUTPUT_SCALE, positional / OUTPUT_SCALE);
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
            return null;
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
     * Layout constants for one supported Stockfish format.
     */
    private static final class Layout {

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
        private Layout(Variant variant, Size size) {
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

    /**
     * Feature-transformer weights.
     */
    static final class FeatureTransformer {

        /**
         * Layout.
         */
        private final Layout layout;

        /**
         * Transformed feature dimensions.
         */
        final int transformedDimensions;

        /**
         * Biases.
         */
        final short[] biases;

        /**
         * PSQ feature weights, feature-major.
         */
        final short[] psqWeights;

        /**
         * Threat feature weights, feature-major.
         */
        final byte[] threatWeights;

        /**
         * PSQ PSQT weights.
         */
        final int[] psqtWeights;

        /**
         * Threat PSQT weights.
         */
        final int[] threatPsqtWeights;

        /**
         * Creates a feature transformer.
         *
         * @param layout layout
         * @param biases biases
         * @param psqWeights PSQ weights
         * @param threatWeights threat weights
         * @param psqtWeights PSQT weights
         * @param threatPsqtWeights threat PSQT weights
         */
        FeatureTransformer(
                Layout layout,
                short[] biases,
                short[] psqWeights,
                byte[] threatWeights,
                int[] psqtWeights,
                int[] threatPsqtWeights) {
            this.layout = layout;
            this.transformedDimensions = layout.transformedDimensions;
            this.biases = biases;
            this.psqWeights = psqWeights;
            this.threatWeights = threatWeights;
            this.psqtWeights = psqtWeights;
            this.threatPsqtWeights = threatPsqtWeights;
            validate();
        }

        /**
         * Reads feature-transformer weights.
         *
         * @param cursor source cursor
         * @param layout layout
         * @return feature transformer
         * @throws IOException if parsing fails
         */
        static FeatureTransformer read(Cursor cursor, Layout layout) throws IOException {
            short[] biases = cursor.readLebShortArray(layout.transformedDimensions);
            byte[] threatWeights = new byte[0];
            short[] psqWeights;
            int[] psqtWeights;
            int[] threatPsqtWeights = new int[0];

            if (layout.useThreats) {
                threatWeights = cursor.readByteArray((long) layout.threatDimensions() * layout.transformedDimensions);
                psqWeights = cursor.readLebShortArray((long) layout.psqDimensions() * layout.transformedDimensions);
                int[] combinedPsqt = cursor.readLebIntArray((long) layout.totalInputDimensions() * LAYER_STACKS);
                int threatPsqtLength = layout.threatDimensions() * LAYER_STACKS;
                threatPsqtWeights = Arrays.copyOfRange(combinedPsqt, 0, threatPsqtLength);
                psqtWeights = Arrays.copyOfRange(combinedPsqt, threatPsqtLength, combinedPsqt.length);
            } else {
                psqWeights = cursor.readLebShortArray((long) layout.psqDimensions() * layout.transformedDimensions);
                psqtWeights = cursor.readLebIntArray((long) layout.psqDimensions() * LAYER_STACKS);
                if (layout.variant.scaleSmallTransformer) {
                    scaleSmallTransformer(biases, psqWeights);
                }
            }

            return new FeatureTransformer(layout, biases, psqWeights, threatWeights, psqtWeights, threatPsqtWeights);
        }

        /**
         * Creates transformed features and PSQT contribution.
         *
         * @param position position
         * @param board Stockfish-order board
         * @param bucket layer bucket
         * @return transformed output
         */
        TransformOutput transform(Position position, int[] board, int bucket) {
            int[][] psqAccumulation = new int[2][transformedDimensions];
            int[][] threatAccumulation = layout.useThreats ? new int[2][transformedDimensions] : null;
            int[][] psqtAccumulation = new int[2][LAYER_STACKS];
            int[][] threatPsqtAccumulation = layout.useThreats ? new int[2][LAYER_STACKS] : null;

            for (int perspective = UpstreamFeatures.WHITE; perspective <= UpstreamFeatures.BLACK; perspective++) {
                accumulatePerspectiveFeatures(
                        board,
                        perspective,
                        psqAccumulation[perspective],
                        psqtAccumulation[perspective],
                        threatAccumulation == null ? null : threatAccumulation[perspective],
                        threatPsqtAccumulation == null ? null : threatPsqtAccumulation[perspective]);
            }

            int stm = UpstreamFeatures.sideToMove(position);
            int opponent = stm ^ 1;
            int psqt = psqtAccumulation[stm][bucket] - psqtAccumulation[opponent][bucket];
	        if (threatPsqtAccumulation != null) {
	            psqt = (psqt + threatPsqtAccumulation[stm][bucket] - threatPsqtAccumulation[opponent][bucket]) / 2;
	        } else {
	            psqt /= 2;
	        }

            int[] transformed = new int[transformedDimensions];
            int half = transformedDimensions / 2;
            int smallClamp = layout.variant.scaleSmallTransformer ? 254 : 255;
            for (int p = 0; p < 2; p++) {
                int perspective = p == 0 ? stm : opponent;
                int offset = half * p;
                writePerspectiveFeatures(
                        transformed,
                        offset,
                        psqAccumulation[perspective],
                        threatAccumulation == null ? null : threatAccumulation[perspective],
                        half,
                        smallClamp);
            }
            return new TransformOutput(transformed, psqt);
        }

        /**
         * Returns total input dimensions.
         *
         * @return dimensions
         */
        int totalInputDimensions() {
            return layout.totalInputDimensions();
        }

        /**
         * Copies biases into one accumulator.
         *
         * @param target target accumulator
         */
        private void copyBias(int[] target) {
            for (int i = 0; i < target.length; i++) {
                target[i] = biases[i];
            }
        }

        /**
         * Accumulates active PSQ and optional threat features for one perspective.
         *
         * @param board Stockfish-order board
         * @param perspective perspective color
         * @param psqAccumulation hidden PSQ accumulator
         * @param psqtAccumulation PSQT accumulator
         * @param threatAccumulation hidden threat accumulator, or {@code null}
         * @param threatPsqtAccumulation threat PSQT accumulator, or {@code null}
         */
        private void accumulatePerspectiveFeatures(
                int[] board,
                int perspective,
                int[] psqAccumulation,
                int[] psqtAccumulation,
                int[] threatAccumulation,
                int[] threatPsqtAccumulation) {
            copyBias(psqAccumulation);
            int[] psqFeatures = UpstreamFeatures.activeHalfKa(board, perspective);
            addShortFeatures(psqAccumulation, psqtAccumulation, psqFeatures, psqWeights, psqtWeights);
            if (!layout.useThreats) {
                return;
            }
            int[] threatFeatures = UpstreamFeatures.activeThreats(board, perspective, layout.variant);
            addByteFeatures(threatAccumulation, threatPsqtAccumulation, threatFeatures, threatWeights, threatPsqtWeights);
        }

        /**
         * Writes transformed features for one perspective half.
         *
         * @param transformed output feature vector
         * @param offset destination half offset
         * @param psqAccumulation PSQ accumulator
         * @param threatAccumulation threat accumulator, or {@code null}
         * @param half half-width of the transformed vector
         * @param smallClamp clamp value for small networks without threats
         */
        private void writePerspectiveFeatures(
                int[] transformed,
                int offset,
                int[] psqAccumulation,
                int[] threatAccumulation,
                int half,
                int smallClamp) {
            for (int j = 0; j < half; j++) {
                int sum0 = psqAccumulation[j];
                int sum1 = psqAccumulation[j + half];
                if (threatAccumulation != null) {
                    sum0 = Numbers.clamp(sum0 + threatAccumulation[j], 0, 255);
                    sum1 = Numbers.clamp(sum1 + threatAccumulation[j + half], 0, 255);
                } else {
                    sum0 = Numbers.clamp(sum0, 0, smallClamp);
                    sum1 = Numbers.clamp(sum1, 0, smallClamp);
                }
                transformed[offset + j] = (sum0 * sum1) / 512;
            }
        }

        /**
         * Adds short-valued feature weights.
         *
         * @param accumulation hidden accumulator
         * @param psqtAccumulation PSQT accumulator
         * @param features active feature list
         * @param weights feature weights
         * @param psqt PSQT weights
         */
        private void addShortFeatures(
                int[] accumulation,
                int[] psqtAccumulation,
                int[] features,
                short[] weights,
                int[] psqt) {
            for (int feature : features) {
                int weightBase = feature * transformedDimensions;
                for (int i = 0; i < transformedDimensions; i++) {
                    accumulation[i] += weights[weightBase + i];
                }
                int psqtBase = feature * LAYER_STACKS;
                for (int i = 0; i < LAYER_STACKS; i++) {
                    psqtAccumulation[i] += psqt[psqtBase + i];
                }
            }
        }

        /**
         * Adds byte-valued threat feature weights.
         *
         * @param accumulation hidden accumulator
         * @param psqtAccumulation PSQT accumulator
         * @param features active feature list
         * @param weights feature weights
         * @param psqt PSQT weights
         */
        private void addByteFeatures(
                int[] accumulation,
                int[] psqtAccumulation,
                int[] features,
                byte[] weights,
                int[] psqt) {
            for (int feature : features) {
                int weightBase = feature * transformedDimensions;
                for (int i = 0; i < transformedDimensions; i++) {
                    accumulation[i] += weights[weightBase + i];
                }
                int psqtBase = feature * LAYER_STACKS;
                for (int i = 0; i < LAYER_STACKS; i++) {
                    psqtAccumulation[i] += psqt[psqtBase + i];
                }
            }
        }

        /**
         * Scales Stockfish 18 small-net transformer weights after loading.
         *
         * @param biases biases
         * @param weights PSQ weights
         */
        private static void scaleSmallTransformer(short[] biases, short[] weights) {
            for (int i = 0; i < biases.length; i++) {
                biases[i] = (short) (biases[i] * 2);
            }
            for (int i = 0; i < weights.length; i++) {
                weights[i] = (short) (weights[i] * 2);
            }
        }

        /**
         * Validates tensor shapes.
         */
        private void validate() {
            requireLength(biases, transformedDimensions, "biases");
            requireLength(psqWeights, (long) layout.psqDimensions() * transformedDimensions, "psqWeights");
            requireLength(psqtWeights, (long) layout.psqDimensions() * LAYER_STACKS, "psqtWeights");
            if (layout.useThreats) {
                requireLength(threatWeights, (long) layout.threatDimensions() * transformedDimensions, "threatWeights");
                requireLength(threatPsqtWeights, (long) layout.threatDimensions() * LAYER_STACKS, "threatPsqtWeights");
            }
        }

        /**
         * Validates array length.
         *
         * @param values array
         * @param expected expected length
         * @param label label
         */
        private void requireLength(short[] values, long expected, String label) {
            if (values == null || values.length != checkedLength(expected, label)) {
                throw new IllegalArgumentException(label + " length mismatch.");
            }
        }

        /**
         * Validates array length.
         *
         * @param values array
         * @param expected expected length
         * @param label label
         */
        private void requireLength(int[] values, long expected, String label) {
            if (values == null || values.length != checkedLength(expected, label)) {
                throw new IllegalArgumentException(label + " length mismatch.");
            }
        }

        /**
         * Validates array length.
         *
         * @param values array
         * @param expected expected length
         * @param label label
         */
        private void requireLength(byte[] values, long expected, String label) {
            if (values == null || values.length != checkedLength(expected, label)) {
                throw new IllegalArgumentException(label + " length mismatch.");
            }
        }
    }

    /**
     * One Stockfish layer stack.
     */
    static final class Architecture {

        /**
         * Layout.
         */
        private final Layout layout;

        /**
         * FC0 layer.
         */
        private final AffineLayer fc0;

        /**
         * FC1 layer.
         */
        private final AffineLayer fc1;

        /**
         * FC2 layer.
         */
        private final AffineLayer fc2;

        /**
         * FC0 outputs.
         */
        final int fc0OutputDimensions;

        /**
         * FC1 outputs.
         */
        final int fc1OutputDimensions;

        /**
         * Creates an architecture.
         *
         * @param layout layout
         * @param fc0 first layer
         * @param fc1 second layer
         * @param fc2 output layer
         */
        Architecture(Layout layout, AffineLayer fc0, AffineLayer fc1, AffineLayer fc2) {
            this.layout = layout;
            this.fc0 = fc0;
            this.fc1 = fc1;
            this.fc2 = fc2;
            this.fc0OutputDimensions = layout.l2 + 1;
            this.fc1OutputDimensions = layout.l3;
        }

        /**
         * Creates reusable propagation scratch space for this architecture.
         *
         * @return dense-layer workspace
         */
        Scratch newScratch() {
            return new Scratch(layout);
        }

        /**
         * Reads a layer stack.
         *
         * @param cursor source cursor
         * @param layout layout
         * @return layer stack
         */
        static Architecture read(Cursor cursor, Layout layout) {
            AffineLayer fc0 = AffineLayer.read(cursor, layout.transformedDimensions, layout.l2 + 1);
            AffineLayer fc1 = AffineLayer.read(cursor, layout.l2 * 2, layout.l3);
            AffineLayer fc2 = AffineLayer.read(cursor, layout.l3, 1);
            return new Architecture(layout, fc0, fc1, fc2);
        }

        /**
         * Propagates transformed features through this layer stack.
         *
         * @param transformedFeatures transformed features
         * @return scaled Stockfish positional output
         */
        int propagate(int[] transformedFeatures) {
            return propagate(transformedFeatures, newScratch());
        }

        /**
         * Propagates transformed features through this layer stack using reusable
         * workspace.
         *
         * @param transformedFeatures transformed features
         * @param scratch reusable dense-layer workspace
         * @return scaled Stockfish positional output
         */
        int propagate(int[] transformedFeatures, Scratch scratch) {
            fc0.forwardInto(transformedFeatures, scratch.fc0Out);
            for (int i = 0; i < layout.l2; i++) {
                int value = scratch.fc0Out[i];
                scratch.fc1Input[i] = sqrClippedRelu(value);
                scratch.fc1Input[layout.l2 + i] = clippedRelu(value);
            }

            fc1.forwardInto(scratch.fc1Input, scratch.fc1Out);
            for (int i = 0; i < layout.l3; i++) {
                scratch.fc2Input[i] = clippedRelu(scratch.fc1Out[i]);
            }
            int output = fc2.forwardSingle(scratch.fc2Input);
            int fwdOut = scratch.fc0Out[layout.l2] * (600 * OUTPUT_SCALE)
                    / (127 * (1 << WEIGHT_SCALE_BITS));
            return output + fwdOut;
        }

        /**
         * Reusable dense-layer workspace.
         */
        static final class Scratch {

            /**
             * FC0 output buffer.
             */
            private final int[] fc0Out;

            /**
             * FC1 input buffer.
             */
            private final int[] fc1Input;

            /**
             * FC1 output buffer.
             */
            private final int[] fc1Out;

            /**
             * FC2 input buffer.
             */
            private final int[] fc2Input;

            /**
             * Creates one workspace sized for the architecture layout.
             *
             * @param layout layer layout
             */
            private Scratch(Layout layout) {
                this.fc0Out = new int[layout.l2 + 1];
                this.fc1Input = new int[layout.l2 * 2];
                this.fc1Out = new int[layout.l3];
                this.fc2Input = new int[layout.l3];
            }
        }

        /**
         * Clipped ReLU.
         *
         * @param value int32 input
         * @return uint8-like output
         */
        private static int clippedRelu(int value) {
            return Numbers.clamp(value >> WEIGHT_SCALE_BITS, 0, 127);
        }

        /**
         * Squared clipped ReLU.
         *
         * @param value int32 input
         * @return uint8-like output
         */
        private static int sqrClippedRelu(int value) {
            long squared = (long) value * value;
            long shifted = squared >> (2 * WEIGHT_SCALE_BITS + 7);
            return (int) Math.min(127L, shifted);
        }
    }

    /**
     * Dense affine layer with Stockfish's serialized shape.
     */
    static final class AffineLayer {

        /**
         * Input dimensions.
         */
        private final int inputDimensions;

        /**
         * Padded input dimensions.
         */
        private final int paddedInputDimensions;

        /**
         * Output dimensions.
         */
        private final int outputDimensions;

        /**
         * Biases.
         */
        private final int[] biases;

        /**
         * Row-major int8 weights.
         */
        private final byte[] weights;

        /**
         * Creates a layer.
         *
         * @param inputDimensions input dimensions
         * @param outputDimensions output dimensions
         * @param biases biases
         * @param weights row-major weights
         */
        AffineLayer(int inputDimensions, int outputDimensions, int[] biases, byte[] weights) {
            this.inputDimensions = inputDimensions;
            this.paddedInputDimensions = ceilToMultiple(inputDimensions, 32);
            this.outputDimensions = outputDimensions;
            this.biases = biases;
            this.weights = weights;
            validate();
        }

        /**
         * Reads a layer.
         *
         * @param cursor source cursor
         * @param inputDimensions input dimensions
         * @param outputDimensions output dimensions
         * @return layer
         */
        static AffineLayer read(Cursor cursor, int inputDimensions, int outputDimensions) {
            int paddedInputDimensions = ceilToMultiple(inputDimensions, 32);
            int[] biases = new int[outputDimensions];
            for (int i = 0; i < outputDimensions; i++) {
                biases[i] = cursor.readInt();
            }
            byte[] weights = cursor.readByteArray((long) outputDimensions * paddedInputDimensions);
            return new AffineLayer(inputDimensions, outputDimensions, biases, weights);
        }

        /**
         * Runs the layer.
         *
         * @param input unsigned byte-like input values in int form
         * @return output vector
         */
        void forwardInto(int[] input, int[] output) {
            System.arraycopy(biases, 0, output, 0, outputDimensions);
            int weightBase = 0;
            for (int out = 0; out < outputDimensions; out++) {
                int sum = output[out];
                for (int in = 0; in < inputDimensions; in++) {
                    sum += weights[weightBase + in] * input[in];
                }
                output[out] = sum;
                weightBase += paddedInputDimensions;
            }
        }

        /**
         * Runs a single-output layer without allocating an output vector.
         *
         * @param input unsigned byte-like input values in int form
         * @return scalar output
         */
        int forwardSingle(int[] input) {
            int sum = biases[0];
            for (int in = 0; in < inputDimensions; in++) {
                sum += weights[in] * input[in];
            }
            return sum;
        }

        /**
         * Validates layer shapes.
         */
        private void validate() {
            requireLength(biases, outputDimensions, "affine biases");
            requireLength(weights, (long) outputDimensions * paddedInputDimensions, "affine weights");
        }

        /**
         * Rounds up to a multiple.
         *
         * @param value value
         * @param multiple multiple
         * @return rounded value
         */
        private static int ceilToMultiple(int value, int multiple) {
            return ((value + multiple - 1) / multiple) * multiple;
        }

        /**
         * Validates array length.
         *
         * @param values array
         * @param expected expected length
         * @param label label
         */
        private void requireLength(int[] values, long expected, String label) {
            if (values == null || values.length != checkedLength(expected, label)) {
                throw new IllegalArgumentException(label + " length mismatch.");
            }
        }

        /**
         * Validates array length.
         *
         * @param values array
         * @param expected expected length
         * @param label label
         */
        private void requireLength(byte[] values, long expected, String label) {
            if (values == null || values.length != checkedLength(expected, label)) {
                throw new IllegalArgumentException(label + " length mismatch.");
            }
        }
    }

    /**
     * Feature-transform output.
     */
    private static final class TransformOutput {

        /**
         * Stores transformed features.
         */
        private final int[] features;

        /**
         * Stores PSQT output.
         */
        private final int psqt;

        /**
         * Creates one feature-transform output snapshot.
         *
         * @param features transformed features
         * @param psqt PSQT output
         */
        private TransformOutput(int[] features, int psqt) {
            this.features = features == null ? new int[0] : features.clone();
            this.psqt = psqt;
        }

	        /**
	         * Compares this transform output with another output snapshot.
	         *
	         * @param other object to compare
	         * @return true when the PSQT value and transformed features match
	         */
	        @Override
	        public boolean equals(Object other) {
	            return other instanceof TransformOutput that
	                    && psqt == that.psqt
	                    && Arrays.equals(features, that.features);
	        }

	        /**
	         * Computes a hash over the transformed features and PSQT value.
	         *
	         * @return transform output hash code
	         */
	        @Override
	        public int hashCode() {
	            int result = Arrays.hashCode(features);
            result = 31 * result + Integer.hashCode(psqt);
	            return result;
	        }

	        /**
	         * Formats this transform output for diagnostics.
	         *
	         * @return debug string containing transformed features and PSQT output
	         */
	        @Override
	        public String toString() {
	            return "TransformOutput[features="
                    + Arrays.toString(features)
                    + ", psqt="
                    + psqt
                    + "]";
        }
    }

    /**
     * Checks a length fits an int.
     *
     * @param length length
     * @param label label
     * @return int length
     */
    private static int checkedLength(long length, String label) {
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " length is too large: " + length);
        }
        return (int) length;
    }

    /**
     * Little-endian byte cursor with Stockfish LEB128 helpers.
     */
    static final class Cursor {

        /**
         * Data.
         */
        private final byte[] data;

        /**
         * Current offset.
         */
        private int offset;

        /**
         * Creates a cursor.
         *
         * @param data source bytes
         */
        Cursor(byte[] data) {
            this.data = data;
        }

        /**
         * Reads a signed little-endian int32.
         *
         * @return value
         */
        int readInt() {
            require(4);
            int value = (data[offset] & 0xff)
                    | ((data[offset + 1] & 0xff) << 8)
                    | ((data[offset + 2] & 0xff) << 16)
                    | ((data[offset + 3] & 0xff) << 24);
            offset += 4;
            return value;
        }

        /**
         * Reads a signed little-endian int16.
         *
         * @return value
         */
        short readShort() {
            require(2);
            int value = (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
            offset += 2;
            return (short) value;
        }

        /**
         * Reads one byte.
         *
         * @return byte value
         */
        byte readByte() {
            require(1);
            return data[offset++];
        }

        /**
         * Reads a byte array.
         *
         * @param length byte count
         * @return byte array
         */
        byte[] readByteArray(long length) {
            int n = checkedLength(length, "byte array");
            require(n);
            byte[] out = Arrays.copyOfRange(data, offset, offset + n);
            offset += n;
            return out;
        }

        /**
         * Reads a string.
         *
         * @param length byte length
         * @return decoded string
         */
        String readString(int length) {
            if (length < 0) {
                throw new IllegalArgumentException("Negative string length.");
            }
            require(length);
            String out = new String(data, offset, length, StandardCharsets.UTF_8);
            offset += length;
            return out;
        }

        /**
         * Reads a signed LEB128-compressed short array.
         *
         * @param count array length
         * @return array
         * @throws IOException if compressed data is invalid
         */
        short[] readLebShortArray(long count) throws IOException {
            int n = checkedLength(count, "LEB short array");
            short[] out = new short[n];
            LebReader reader = beginLeb();
            for (int i = 0; i < n; i++) {
                out[i] = (short) reader.readSigned();
            }
            reader.finish();
            return out;
        }

        /**
         * Reads a signed LEB128-compressed int array.
         *
         * @param count array length
         * @return array
         * @throws IOException if compressed data is invalid
         */
        int[] readLebIntArray(long count) throws IOException {
            int n = checkedLength(count, "LEB int array");
            int[] out = new int[n];
            LebReader reader = beginLeb();
            for (int i = 0; i < n; i++) {
                out[i] = reader.readSigned();
            }
            reader.finish();
            return out;
        }

        /**
         * Returns whether unread bytes remain.
         *
         * @return true when remaining
         */
        boolean hasRemaining() {
            return offset != data.length;
        }

        /**
         * Starts a LEB128 block.
         *
         * @return reader
         * @throws IOException if the marker is missing
         */
        private LebReader beginLeb() throws IOException {
            require(LEB128_MAGIC.length);
            for (byte b : LEB128_MAGIC) {
                if (data[offset++] != b) {
                    throw new IOException("Missing Stockfish LEB128 marker.");
                }
            }
            int byteCount = readInt();
            if (byteCount < 0) {
                throw new IOException("Negative Stockfish LEB128 block length.");
            }
            require(byteCount);
            return new LebReader(this, offset, offset + byteCount);
        }

        /**
         * Requires bytes to be available.
         *
         * @param length byte count
         */
        private void require(int length) {
            if (length < 0 || offset + length > data.length) {
                throw new BufferUnderflowException();
            }
        }
    }

    /**
     * Signed LEB128 block reader.
     */
    private static final class LebReader {

        /**
         * Parent cursor.
         */
        private final Cursor cursor;

        /**
         * Current block offset.
         */
        private int offset;

        /**
         * End of block.
         */
        private final int end;

        /**
         * Creates a reader.
         *
         * @param cursor parent cursor
         * @param start start offset
         * @param end end offset
         */
        LebReader(Cursor cursor, int start, int end) {
            this.cursor = cursor;
            this.offset = start;
            this.end = end;
        }

        /**
         * Reads one signed value.
         *
         * @return decoded value
         * @throws IOException if data is truncated
         */
        int readSigned() throws IOException {
            int result = 0;
            int shift = 0;
            int b;
            do {
                if (offset >= end) {
                    throw new IOException("Truncated Stockfish LEB128 block.");
                }
                b = cursor.data[offset++] & 0xff;
                result |= (b & 0x7f) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);

            if (shift < 32 && (b & 0x40) != 0) {
                result |= -(1 << shift);
            }
            return result;
        }

        /**
         * Completes the LEB block.
         *
         * @throws IOException if bytes remain unconsumed
         */
        void finish() throws IOException {
            if (offset != end) {
                throw new IOException("Unused bytes in Stockfish LEB128 block.");
            }
            cursor.offset = end;
        }
    }
}
