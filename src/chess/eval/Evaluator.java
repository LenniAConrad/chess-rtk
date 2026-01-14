package chess.eval;

import chess.classical.Wdl;
import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.debug.LogService;
import chess.lc0.Model;
import chess.lc0.Network;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;

/**
 * Evaluates chess positions into a compact {@link Result}.
 *
 * <p>
 * This evaluator prefers LC0, but automatically falls back to a classical
 * heuristic evaluator when LC0 cannot be loaded or fails at runtime.
 * </p>
 *
 * <p>
 * Implementations may hold native resources (e.g., GPU memory) and therefore
 * implement {@link AutoCloseable}.
 * </p>
 *
 * <p>
 * <strong>Perspective:</strong> results are from the side-to-move perspective.
 * </p>
 *
 * <h4>Fallback behavior</h4>
 * <ul>
 * <li>On the first call, the evaluator tries to lazily load LC0 weights.</li>
 * <li>If loading fails, LC0 is disabled and all future evaluations use classical.</li>
 * <li>If prediction fails (runtime exception / linkage error / invalid outputs),
 * LC0 is disabled, the model is closed, and the current evaluation falls back
 * to classical.</li>
 * </ul>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Evaluator implements AutoCloseable {

    /**
     * Default weights path for convenience.
     */
    public static final Path DEFAULT_WEIGHTS = Model.DEFAULT_WEIGHTS;

    /**
     * Lock guarding model creation, use, and shutdown.
     */
    private final StampedLock modelLock = new StampedLock();

    /**
     * Weights path used if/when LC0 is loaded.
     */
    private final Path weights;

    /**
     * Whether classical evaluation should do terminal detection via move generation.
     */
    private final boolean terminalAwareClassical;

    /**
     * Lazily loaded LC0 model (may remain null if LC0 is unavailable).
     */
    private final AtomicReference<Model> model = new AtomicReference<>();

    /**
     * Whether the currently loaded LC0 model is using a GPU backend.
     *
     * <p>
     * GPU inference is routed through JNI and is treated as a single shared native
     * resource. To avoid backend-specific threading hazards, GPU predictions are serialized by
     * taking the write lock in {@link #tryPredictLc0(Position)}.
     * </p>
     */
    private volatile boolean gpuActive;

    /**
     * Set to true once LC0 is considered unusable; from then on we never attempt
     * to load or run it again.
     */
    private volatile boolean lc0Disabled;

    /**
     * Last backend used.
     */
    private volatile Backend lastBackend;

    /**
     * The first failure that caused LC0 to be disabled (if any).
     */
    private final AtomicReference<Throwable> lc0Failure = new AtomicReference<>();

    /**
     * Tracks the last backend that was logged.
     */
    private volatile Backend loggedBackend;

    /**
     * Small memoization of the last evaluated position (by {@link Position#signature()}) and result.
     *
     * <p>
     * This avoids redundant LC0/classical evaluation work when multiple callers request the same
     * evaluation repeatedly (for example when asking for both a backend label and an ablation
     * overlay for the same position).
     * </p>
     */
    private final AtomicReference<CacheEntry> lastEvalCache = new AtomicReference<>();

    /**
     * Memoization for LC0-only evaluations (a subset of {@link #lastEvalCache}).
     */
    private final AtomicReference<CacheEntry> lastLc0Cache = new AtomicReference<>();

    /**
     * Create an evaluator using {@link #DEFAULT_WEIGHTS} and a fast classical
     * fallback (no move-generation terminal detection).
     */
    public Evaluator() {
        this(DEFAULT_WEIGHTS, false);
    }

    /**
     * Create an evaluator.
     *
     * @param weights path to LC0 {@code .bin} weights (non-null)
     * @param terminalAwareClassical if true, the classical fallback will try to
     *                               detect checkmate/stalemate via move generation
     * @throws IllegalArgumentException if {@code weights} is null
     */
    public Evaluator(Path weights, boolean terminalAwareClassical) {
        if (weights == null) {
            throw new IllegalArgumentException("weights == null");
        }
        this.weights = weights;
        this.terminalAwareClassical = terminalAwareClassical;
    }

    /**
     * Evaluate a position.
     *
     * @param position position to evaluate (non-null)
     * @return evaluation result (non-null)
     * @throws IllegalArgumentException if {@code position} is null
     */
    public Result evaluate(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }

        long signature = position.signature();
        CacheEntry entry = lastEvalCache.get();
        if (entry != null && entry.signature() == signature) {
            Result cached = entry.result();
            lastBackend = cached.backend();
            logBackendOnce(cached.backend());
            return cached;
        }

        if (!lc0Disabled) {
            try {
                Lc0Prediction p = tryPredictLc0(position);
                if (p != null) {
                    Wdl wdl = toWdl(p.prediction.wdl());
                    Backend backend = mapBackend(p.backend);
                    double value = p.prediction.value();
                    lastBackend = backend;
                    logBackendOnce(backend);
                    Result result = new Result(backend, wdl, value, null);
                    cache(signature, result);
                    return result;
                }
            } catch (IOException | RuntimeException | LinkageError t) {
                disableLc0(t);
                // fall through to classical
            }
        }

        Wdl wdl = Wdl.evaluate(position, terminalAwareClassical);
        int cp = Wdl.evaluateStmCentipawns(position);
        double value = (wdl.win() - wdl.loss()) / (double) Wdl.TOTAL;
        lastBackend = Backend.CLASSICAL;
        logBackendOnce(Backend.CLASSICAL);
        Result result = new Result(Backend.CLASSICAL, wdl, value, cp);
        cache(signature, result);
        return result;
    }

    /**
     * Evaluate a position using LC0 only.
     *
     * @param position position to evaluate (non-null)
     * @return LC0-backed evaluation result
     * @throws IllegalStateException if LC0 is unavailable or initialization fails
     * @throws IllegalArgumentException if {@code position} is null
     */
    public Result evaluateLc0(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }

        long signature = position.signature();
        CacheEntry lc0Entry = lastLc0Cache.get();
        if (lc0Entry != null && lc0Entry.signature() == signature) {
            Result cached = lc0Entry.result();
            lastBackend = cached.backend();
            logBackendOnce(cached.backend());
            return cached;
        }
        CacheEntry entry = lastEvalCache.get();
        if (entry != null && entry.signature() == signature) {
            Result cached = entry.result();
            if (cached.backend() != Backend.CLASSICAL) {
                lastBackend = cached.backend();
                logBackendOnce(cached.backend());
                return cached;
            }
        }

        try {
            Lc0Prediction p = tryPredictLc0(position);
            if (p == null) {
                Throwable failure = lc0Failure.get();
                String reason = (failure != null && failure.getMessage() != null)
                        ? failure.getMessage()
                        : "LC0 unavailable or disabled.";
                throw new IllegalStateException(reason, failure);
            }
            Wdl wdl = toWdl(p.prediction.wdl());
            Backend backend = mapBackend(p.backend);
            double value = p.prediction.value();
            lastBackend = backend;
            logBackendOnce(backend);
            Result result = new Result(backend, wdl, value, null);
            cache(signature, result);
            return result;
        } catch (IOException | RuntimeException | LinkageError t) {
            disableLc0(t);
            throw new IllegalStateException("LC0 evaluation failed.", t);
        }
    }

    /**
     * Computes an ablation heatmap by removing each piece in turn and
     * re-evaluating the position.
     *
     * <p>
     * For each occupied square, the piece is temporarily removed (including kings),
     * the position is re-evaluated using LC0, and then the piece is restored.
     * If LC0 is unavailable, this falls back to the classical evaluator.
     * The returned matrix stores the inverted impact: {@code baseline - ablated},
     * using centipawns when available or a WDL→CP mapping otherwise. Empty squares
     * remain zero.
     * </p>
     *
     * <p>
     * Matrix indexing follows White's perspective: {@code matrix[0][0]} is a1,
     * {@code matrix[7][7]} is h8.
     * </p>
     *
     * @param position position to ablate (non-null)
     * @return 8x8 matrix of inverted ablation scores from the side-to-move perspective
     * @throws IllegalArgumentException if {@code position} is null
     */
    public int[][] ablation(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }

        boolean useLc0 = true;
        int baseline;
        try {
            baseline = scoreForAblation(evaluateLc0(position));
        } catch (IllegalStateException ex) {
            useLc0 = false;
            LogService.warn("LC0 ablation unavailable; falling back to classical.");
            baseline = scoreForAblation(evaluate(position));
        }
        int[][] matrix = new int[8][8];

        MutablePosition working = new MutablePosition(position);
        for (int index = 0; index < 64; index++) {
            byte piece = working.pieceAt(index);
            if (piece == Piece.EMPTY) {
                continue;
            }
            working.removePieceAt(index, piece);
            int ablated = useLc0
                    ? scoreForAblation(evaluateLc0(working))
                    : scoreForAblation(evaluate(working));
            working.restorePieceAt(index, piece);

            int file = Field.getX((byte) index);
            int rankFromBottom = Field.getY((byte) index);
            matrix[rankFromBottom][file] = baseline - ablated;
        }

        return matrix;
    }

    /**
     * Returns the backend used for the most recent {@link #evaluate(Position)} call.
     *
     * @return backend used for the last evaluation, or {@code null} if never used
     */
    public Backend lastBackend() {
        return lastBackend;
    }

    /**
     * Returns the stored LC0 failure (if LC0 has been disabled).
     *
     * @return failure cause, or {@code null} if LC0 is still considered usable
     */
    public Throwable lc0Failure() {
        return lc0Failure.get();
    }

    /**
     * Close the LC0 model if it was loaded.
     */
    @Override
    public void close() {
        long stamp = modelLock.writeLock();
        try {
            Model m = model.getAndSet(null);
            if (m != null) {
                m.close();
            }
            gpuActive = false;
        } finally {
            modelLock.unlockWrite(stamp);
        }
    }

    /**
     * Try to evaluate via LC0 without changing fallback state unless a failure occurs.
     *
     * @param position position to evaluate (non-null)
     * @return LC0 prediction bundle, or {@code null} when LC0 is unavailable/disabled
     * @throws IOException if model loading or prediction fails with an I/O error
     */
    private Lc0Prediction tryPredictLc0(Position position) throws IOException {
        if (lc0Disabled) {
            return null;
        }

        Model m = model.get();
        if (m == null) {
            long writeStamp = modelLock.writeLock();
            try {
                if (lc0Disabled) {
                    return null;
                }
                m = model.get();
                if (m == null) {
                    m = Model.load(weights);
                    model.set(m);
                    gpuActive = !"cpu".equalsIgnoreCase(m.backend());
                }
            } finally {
                modelLock.unlockWrite(writeStamp);
            }
        }

        final boolean exclusive = gpuActive;
        long stamp = exclusive ? modelLock.writeLock() : modelLock.readLock();
        try {
            if (lc0Disabled) {
                return null;
            }
            m = model.get();
            if (m == null) {
                return null;
            }
            Network.Prediction pred = m.predict(position);
            return new Lc0Prediction(pred, m.backend());
        } finally {
            if (exclusive) {
                modelLock.unlockWrite(stamp);
            } else {
                modelLock.unlockRead(stamp);
            }
        }
    }

    /**
     * Permanently disable LC0 after a failure and release any allocated resources.
     *
     * @param t failure that triggered the disable; ignored when {@code null}
     */
    private void disableLc0(Throwable t) {
        if (t == null) {
            return;
        }
        // Mark unusable first to prevent other threads from attempting to use it.
        lc0Disabled = true;
        // Do not keep memoized LC0 results around once we consider LC0 unusable.
        lastEvalCache.set(null);
        lastLc0Cache.set(null);
        if (lc0Failure.compareAndSet(null, t)) {
            LogService.error(t, "LC0 disabled; falling back to classical.", "Weights: " + weights.toAbsolutePath());
        }
        // Ensure we release resources if the model was partially/fully initialized.
        close();
    }

    /**
     * Caches the last evaluation result for quick reuse.
     * Updates both the general cache and the LC0-specific cache when applicable.
     *
     * @param signature position signature used as cache key
     * @param result evaluation result to cache
     */
    private void cache(long signature, Result result) {
        lastEvalCache.set(new CacheEntry(signature, result));
        if (result.backend() != Backend.CLASSICAL) {
            lastLc0Cache.set(new CacheEntry(signature, result));
        }
    }

    /**
     * Immutable cache entry holding a signature and result pair.
     * Ensures cached results are non-null.
     *
     * @param signature position signature used as cache key
     * @param result evaluation result to cache
     */
    private record CacheEntry(long signature, Result result) {
        /**
         * Validates the cache entry inputs for a recorded evaluation.
         *
         * @param signature position signature used as cache key
         * @param result evaluation result to cache (non-null)
         * @throws IllegalArgumentException if {@code result} is {@code null}
         */
        private CacheEntry {
            if (result == null) {
                throw new IllegalArgumentException("result == null");
            }
        }
    }

    /**
     * Logs the selected backend once for the lifetime of this evaluator.
     *
     * @param backend backend used for evaluation
     */
    private void logBackendOnce(Backend backend) {
        if (backend == loggedBackend) {
            return;
        }
        loggedBackend = backend;
        String msg;
        if (backend == Backend.LC0_CUDA) {
            msg = "Evaluator backend: LC0 (cuda), weights=" + weights;
        } else if (backend == Backend.LC0_ROCM) {
            msg = "Evaluator backend: LC0 (rocm), weights=" + weights;
        } else if (backend == Backend.LC0_ONEAPI) {
            msg = "Evaluator backend: LC0 (oneapi), weights=" + weights;
        } else if (backend == Backend.LC0_CPU) {
            msg = "Evaluator backend: LC0 (cpu), weights=" + weights;
        } else {
            msg = "Evaluator backend: classical";
        }
        LogService.info(msg);
    }

    /**
     * Returns a signed score for ablation overlays.
     *
     * @param result evaluation result
     * @return centipawn score when available, else scaled LC0 value
     */
    private static int scoreForAblation(Result result) {
        Integer cp = result.centipawns();
        if (cp != null) {
            return cp;
        }
        return wdlToCp(result.wdl());
    }

    /**
     * Converts a WDL triplet into a centipawn-like score using expected-score odds.
     *
     * @param wdl WDL triplet (scaled to {@link Wdl#TOTAL})
     * @return centipawn-like score from the side-to-move perspective
     */
    private static int wdlToCp(Wdl wdl) {
        double win = wdl.win() / (double) Wdl.TOTAL;
        double draw = wdl.draw() / (double) Wdl.TOTAL;
        double score = win + 0.5 * draw;
        double eps = 1e-6;
        double clamped = Math.max(eps, Math.min(1.0 - eps, score));
        double cp = 400.0 * Math.log10(clamped / (1.0 - clamped));
        return (int) Math.round(cp);
    }

    /**
     * Small holder for an LC0 prediction and backend identifier.
     */
    private static final class Lc0Prediction {
        /**
         * LC0 prediction payload containing policy/WDL/value outputs.
         * Used to avoid recomputing predictions for the same position.
         */
        private final Network.Prediction prediction;
        /**
         * Backend identifier reported by LC0.
         * Used for logging and backend selection.
         */
        private final String backend;

        /**
         * Creates a holder for an LC0 prediction with its backend identifier.
         *
         * @param prediction LC0 prediction payload
         * @param backend    backend identifier string
         */
        private Lc0Prediction(Network.Prediction prediction, String backend) {
            this.prediction = prediction;
            this.backend = backend;
        }
    }

    /**
     * Map an LC0 backend string to an enum value.
     *
     * @param backend backend string reported by LC0 (may be {@code null})
     * @return mapped backend enum (never null)
     */
    private static Backend mapBackend(String backend) {
        if (backend == null) {
            return Backend.LC0_CPU;
        }
        if ("cuda".equalsIgnoreCase(backend)) {
            return Backend.LC0_CUDA;
        }
        if ("rocm".equalsIgnoreCase(backend) || "amd".equalsIgnoreCase(backend) || "hip".equalsIgnoreCase(backend)) {
            return Backend.LC0_ROCM;
        }
        if ("oneapi".equalsIgnoreCase(backend) || "intel".equalsIgnoreCase(backend)) {
            return Backend.LC0_ONEAPI;
        }
        return Backend.LC0_CPU;
    }

    /**
     * Convert LC0 WDL floats into a normalized {@link Wdl} distribution.
     *
     * @param wdl array of length 3 in win/draw/loss order
     * @return normalized WDL with counts summing to {@link Wdl#TOTAL}
     * @throws IllegalStateException if the array is invalid or has non-finite/negative values
     */
    private static Wdl toWdl(float[] wdl) {
        if (wdl == null || wdl.length != 3) {
            throw new IllegalStateException("LC0 returned invalid WDL array");
        }
        float win = wdl[0];
        float draw = wdl[1];
        float loss = wdl[2];

        if (!Float.isFinite(win) || !Float.isFinite(draw) || !Float.isFinite(loss)) {
            throw new IllegalStateException("LC0 returned non-finite WDL values");
        }
        if (win < 0.0f || draw < 0.0f || loss < 0.0f) {
            throw new IllegalStateException("LC0 returned negative WDL values");
        }

        double sum = (double) win + (double) draw + loss;
        if (sum <= 0.0) {
            throw new IllegalStateException("LC0 returned WDL sum <= 0");
        }

        double pWin = win / sum;
        double pDraw = draw / sum;
        double pLoss = loss / sum;

        // Largest remainder to ensure exact sum == 1000 after rounding.
        int winBase = (int) Math.floor(pWin * Wdl.TOTAL);
        int drawBase = (int) Math.floor(pDraw * Wdl.TOTAL);
        int lossBase = (int) Math.floor(pLoss * Wdl.TOTAL);

        double winFrac = (pWin * Wdl.TOTAL) - winBase;
        double drawFrac = (pDraw * Wdl.TOTAL) - drawBase;
        double lossFrac = (pLoss * Wdl.TOTAL) - lossBase;

        int remainder = Wdl.TOTAL - (winBase + drawBase + lossBase);
        int w = winBase;
        int d = drawBase;
        int l = lossBase;

        for (int i = 0; i < remainder; i++) {
            if (winFrac >= drawFrac && winFrac >= lossFrac) {
                w++;
                winFrac = -1.0;
            } else if (drawFrac >= lossFrac) {
                d++;
                drawFrac = -1.0;
            } else {
                l++;
                lossFrac = -1.0;
            }
        }

        return new Wdl((short) w, (short) d, (short) l);
    }

    /**
     * Mutable position snapshot for temporary board edits during ablation.
     *
     * <p>
     * This wrapper exposes minimal, controlled mutations of the board array and
     * king locations, while keeping the rest of {@link Position} behavior intact.
     * </p>
     */
    private static final class MutablePosition extends Position {

        /**
         * Create a mutable copy of the provided position.
         *
         * @param source position to copy (non-null)
         */
        private MutablePosition(Position source) {
            super(source.toString());
        }

        /**
         * Returns the piece currently stored at {@code index}.
         *
         * @param index square index 0..63
         * @return piece code at the square
         */
        private byte pieceAt(int index) {
            return board[index];
        }

        /**
         * Removes a piece from the board and updates king tracking if needed.
         *
         * @param index square index 0..63
         * @param piece piece code currently on the square
         */
        private void removePieceAt(int index, byte piece) {
            board[index] = Piece.EMPTY;
            if (Piece.isKing(piece)) {
                if (Piece.isWhite(piece)) {
                    whiteKing = Field.NO_SQUARE;
                } else {
                    blackKing = Field.NO_SQUARE;
                }
            }
        }

        /**
         * Restores a piece to the board and updates king tracking if needed.
         *
         * @param index square index 0..63
         * @param piece piece code to restore
         */
        private void restorePieceAt(int index, byte piece) {
            board[index] = piece;
            if (Piece.isKing(piece)) {
                if (Piece.isWhite(piece)) {
                    whiteKing = (byte) index;
                } else {
                    blackKing = (byte) index;
                }
            }
        }

        /**
         * Returns whether the current side is in check, guarding missing-king cases.
         *
         * @return true if the side to move is in check and the king exists
         */
        @Override
        public boolean inCheck() {
            if (whitesTurn) {
                return whiteKing != Field.NO_SQUARE && super.inCheck();
            }
            return blackKing != Field.NO_SQUARE && super.inCheck();
        }
    }
}
