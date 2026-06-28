package application.gui.workbench.network;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads real network weights lazily and runs inference to fill activation
 * snapshots for the Workbench network visualizer.
 *
 * <p>
 * The class is intentionally thin: it owns one model instance per architecture
 * and defers loading until the first request. Models that are missing on disk
 * (or fail to load) leave the corresponding snapshot empty so the panel falls
 * back to a clear "model not loaded" indicator.
 * </p>
 *
 * <p>
 * All public methods are safe to call from any thread, but a single instance
 * is not safe for concurrent inference of the same architecture; the workbench
 * uses one provider behind a single SwingWorker so this is fine.
 * </p>
 */
public final class RealActivations {

    /**
     * Read-only model state surfaced in the Network tab diagnostics pane.
     *
     * @param label user-facing model label
     * @param path weights path used by the workbench inference provider
     * @param present true when the file exists on disk
     * @param loaded true when the provider has a live model/network instance
     * @param state short state label
     * @param detail short explanatory detail
     */
    public record ModelStatus(String label, Path path, boolean present, boolean loaded,
            String state, String detail) {
    }

    /**
     * User-facing NNUE model family label.
     */
    public static final String LABEL_NNUE = "NNUE - HalfKP";

    /**
     * User-facing LC0 CNN model family label.
     */
    public static final String LABEL_CNN = "CNN - 10x128";

    /**
     * User-facing LC0 BT4 model family label.
     */
    public static final String LABEL_BT4 = "BT4 - 1024x15x32h";

    /**
     * User-facing OTIS model family label.
     */
    public static final String LABEL_OTIS = "OTIS (Policy + WDL)";

    /**
     * Long-running activation provider stage surfaced to the loading panel.
     */
    public enum Phase {
        /**
         * Model weights are being read from disk.
         */
        LOADING_MODEL("Loading model", "Reading model weights from disk"),

        /**
         * A loaded model is evaluating the current position.
         */
        RUNNING_INFERENCE("Running inference", "Evaluating the current position"),

        /**
         * The real model is unavailable and synthetic activations are being
         * generated instead.
         */
        SYNTHETIC_FALLBACK("Synthetic fallback", "Generating deterministic fallback activations");

        /**
         * Short loading-panel title.
         */
        private final String title;

        /**
         * Short loading-panel detail.
         */
        private final String detail;

        /**
         * Creates one provider phase.
         *
         * @param title short loading title
         * @param detail short loading detail
         */
        Phase(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }

        /**
         * Returns the short loading-panel title.
         *
         * @return title
         */
        public String title() {
            return title;
        }

        /**
         * Returns the short loading-panel detail.
         *
         * @return detail
         */
        public String detail() {
            return detail;
        }
    }

    /**
     * Progress callback for model load and inference stages.
     */
    @FunctionalInterface
    public interface ProgressListener {

        /**
         * Reports one provider stage.
         *
         * @param architecture architecture label
         * @param phase provider phase
         * @param path model path, when available
         */
        void onProgress(String architecture, Phase phase, Path path);
    }

    /**
     * Default NNUE weights path (Stockfish .nnue or CRTK .bin) used at
     * startup when no override is set.
     */
    private static final Path NNUE_PATH = Path.of("models/crtk-halfkp.nnue");

    /**
     * Current NNUE weights path. Defaults to {@link #NNUE_PATH} but the
     * workbench may override it via {@link #setNnuePath(Path)} to switch
     * between different NNUE network files on disk.
     */
    private Path nnuePath = NNUE_PATH;

    /**
     * LC0 CNN weights path.
     */
    private static final Path CNN_PATH = Path.of("models/leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin");

    /**
     * LC0 BT4 weights path (CRTK v2 .bin produced by the python converter).
     */
    private static final Path BT4_PATH = Path.of("models/bt4-1024x15x32h.bin");

    /**
     * OTIS policy/WDL weights path used by Workbench network views and Play.
     */
    private static final Path OTIS_PATH = Path.of("models/otis_lczero_base_real_step_04721112_crtk_otis_v2.bin");

    /**
     * Returns the CNN weights path used by the workbench.
     *
     * @return CNN weights path
     */
    public static Path cnnPath() {
        return CNN_PATH;
    }

    /**
     * Returns the BT4 weights path used by the workbench.
     *
     * @return BT4 weights path
     */
    public static Path bt4Path() {
        return BT4_PATH;
    }

    /**
     * Returns the OTIS weights path used by the workbench.
     *
     * @return OTIS weights path
     */
    public static Path otisPath() {
        return OTIS_PATH;
    }

    /**
     * NNUE model, lazily loaded.
     */
    private chess.nn.nnue.Model nnueModel;

    /**
     * NNUE load failure message; non-null when the model could not be loaded.
     */
    private String nnueLoadError;

    /**
     * Short label describing the loaded NNUE network.
     */
    private String nnueVersionLabel = "synthetic Stockfish-shaped NNUE (FEN-seeded)";

    /**
     * Cached NNUE feature-weight atlas snapshot. The atlas does not depend on
     * the current FEN, so it is computed exactly once per loaded network and
     * merged into every per-position snapshot.
     */
    private ActivationSnapshot nnueAtlasCache;

    /**
     * Cached atlases keyed by NNUE file path. Used by the "compare with" /
     * diff and grid view modes so the panel can render multiple variants
     * side-by-side without paying the load+marginalisation cost more than once
     * per variant.
     */
    private final Map<Path, ActivationSnapshot> atlasCacheByPath = new HashMap<>();

    /**
     * LC0 CNN network, lazily loaded.
     */
    private chess.nn.lc0.cnn.Network cnnNetwork;

    /**
     * CNN load failure message; non-null when the model could not be loaded.
     */
    private String cnnLoadError;

    /**
     * LC0 BT4 network, lazily loaded.
     */
    private chess.nn.lc0.bt4.Network bt4Network;

    /**
     * BT4 load failure message; non-null when the model could not be loaded.
     */
    private String bt4LoadError;

    /**
     * OTIS model, lazily loaded.
     */
    private chess.nn.otis.Model otisModel;

    /**
     * OTIS load failure message; non-null when the model could not be loaded.
     */
    private String otisLoadError;

    /**
     * Creates a provider.
     */
    public RealActivations() {
        // models are loaded lazily on first inference call
    }

    /**
     * Swaps the NNUE network file. Clears the cached model and atlas so the
     * next inference call loads the new weights.
     *
     * @param newPath path to a Stockfish .nnue or CRTK .bin file
     */
    public synchronized void setNnuePath(Path newPath) {
        if (newPath == null || newPath.equals(nnuePath)) {
            return;
        }
        nnuePath = newPath;
        nnueModel = null;
        nnueLoadError = null;
        nnueAtlasCache = null;
        nnueVersionLabel = "loading " + newPath.getFileName() + "...";
    }

    /**
     * Returns the currently-selected NNUE network path.
     *
     * @return path
     */
    public synchronized Path nnuePath() {
        return nnuePath;
    }

    /**
     * Runs NNUE inference for a position and returns a freshly-built, sealed
     * activation snapshot. Falls back to synthetic activations when the
     * network cannot be loaded. The returned snapshot is never shared or
     * reused, so the caller may publish it straight to a view without any
     * cross-thread aliasing.
     *
     * @param fen current position FEN
     * @return sealed snapshot (real inference or synthetic fallback)
     */
    public synchronized ActivationSnapshot inferNnue(String fen) {
        return inferNnue(fen, null);
    }

    /**
     * Runs NNUE inference and reports load/inference phases.
     *
     * @param fen current position FEN
     * @param progress optional progress listener
     * @return sealed snapshot (real inference or synthetic fallback)
     */
    public synchronized ActivationSnapshot inferNnue(String fen, ProgressListener progress) {
        ActivationSnapshot out = new ActivationSnapshot();
        try {
            if (nnueModel == null && nnueLoadError == null) {
                if (!Files.exists(nnuePath)) {
                    nnueLoadError = "model file missing: " + nnuePath;
                } else {
                    report(progress, LABEL_NNUE, Phase.LOADING_MODEL, nnuePath);
                    nnueModel = chess.nn.nnue.Model.load(nnuePath);
                    refreshNnueVersionLabel();
                }
            }
            if (nnueModel == null) {
                report(progress, LABEL_NNUE, Phase.SYNTHETIC_FALLBACK, nnuePath);
                fallbackNnue(fen, out);
            } else {
                report(progress, LABEL_NNUE, Phase.RUNNING_INFERENCE, nnuePath);
                chess.core.Position position = parsePosition(fen);
                nnueModel.predict(position, out);
                mergeAtlasFromModel(out);
            }
        } catch (RuntimeException | IOException ex) {
            nnueLoadError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            // Inference may have left the snapshot half-written; start clean.
            out = new ActivationSnapshot();
            report(progress, LABEL_NNUE, Phase.SYNTHETIC_FALLBACK, nnuePath);
            fallbackNnue(fen, out);
        }
        out.seal();
        return out;
    }

    /**
     * Returns a short label describing the loaded NNUE version.
     *
     * @return version string
     */
    public synchronized String nnueVersionLabel() {
        return nnueVersionLabel;
    }

    /**
     * Refreshes the NNUE version label after a successful model load.
     */
    private void refreshNnueVersionLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append(nnuePath.getFileName().toString());
        try {
            chess.nn.nnue.UpstreamNetwork.Info up = nnueModel.upstreamInfo();
            if (up != null) {
                sb.append("  ·  Stockfish HalfKAv2_hm (").append(up.variant().label()).append(")")
                  .append("  ·  ").append(up.inputFeatures()).append(" features")
                  .append("  ·  L1=").append(up.transformedDimensions())
                  .append("  ·  L2=").append(up.l2())
                  .append("  ·  L3=").append(up.l3());
            } else {
                chess.nn.nnue.Network.Info info = nnueModel.info();
                sb.append("  ·  CRTK HalfKP  ·  ")
                  .append(info.inputFeatures()).append(" features")
                  .append("  ·  L1=").append(info.hiddenSize())
                  .append("  ·  ").append(info.parameterCount() / 1_000_000).append("M params");
            }
        } catch (Exception ex) {
            sb.append("  ·  ").append(ex.getClass().getSimpleName());
        }
        nnueVersionLabel = sb.toString();
    }

    /**
     * Runs LC0 CNN inference for a position and returns a freshly-built,
     * sealed snapshot. When the local CNN weights are present, the snapshot is
     * captured from the real Java CPU forward pass (input planes, stem,
     * residual blocks, final map, policy head, and value head). If the model
     * cannot be loaded, the workbench falls back to deterministic synthetic
     * activations.
     *
     * @param fen current position FEN
     * @return sealed snapshot (synthetic blocks, real heads when loaded)
     */
    public synchronized ActivationSnapshot inferCnn(String fen) {
        return inferCnn(fen, null);
    }

    /**
     * Runs LC0 CNN inference and reports load/inference phases.
     *
     * @param fen current position FEN
     * @param progress optional progress listener
     * @return sealed snapshot (synthetic blocks, real heads when loaded)
     */
    public synchronized ActivationSnapshot inferCnn(String fen, ProgressListener progress) {
        ActivationSnapshot out = new ActivationSnapshot();
        try {
            if (cnnNetwork == null && cnnLoadError == null) {
                if (!Files.exists(CNN_PATH)) {
                    cnnLoadError = "model file missing: " + CNN_PATH;
                } else {
                    report(progress, LABEL_CNN, Phase.LOADING_MODEL, CNN_PATH);
                    // The visualizer needs intermediate tensors. Those are
                    // exposed by the Java CPU path; GPU backends only return
                    // final policy/value outputs.
                    cnnNetwork = chess.nn.lc0.cnn.Network.loadCpu(CNN_PATH);
                }
            }
            if (cnnNetwork == null) {
                report(progress, LABEL_CNN, Phase.SYNTHETIC_FALLBACK, CNN_PATH);
                SyntheticActivations.fillCnn(fen, out);
            } else {
                report(progress, LABEL_CNN, Phase.RUNNING_INFERENCE, CNN_PATH);
                chess.core.Position position = parsePosition(fen);
                float[] planes = chess.nn.lc0.cnn.Encoder.encode(position);
                cnnNetwork.predictEncoded(planes, out);
            }
        } catch (RuntimeException | IOException ex) {
            cnnLoadError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            out = new ActivationSnapshot();
            report(progress, LABEL_CNN, Phase.SYNTHETIC_FALLBACK, CNN_PATH);
            SyntheticActivations.fillCnn(fen, out);
        }
        out.seal();
        return out;
    }

    /**
     * Runs LC0 BT4 inference for a position and returns a freshly-built,
     * sealed snapshot. Falls back to synthetic activations when the network
     * cannot be loaded.
     *
     * @param fen current position FEN
     * @return sealed snapshot (real inference or synthetic fallback)
     */
    public synchronized ActivationSnapshot inferBt4(String fen) {
        return inferBt4(fen, null);
    }

    /**
     * Runs LC0 BT4 inference and reports load/inference phases.
     *
     * @param fen current position FEN
     * @param progress optional progress listener
     * @return sealed snapshot (real inference or synthetic fallback)
     */
    public synchronized ActivationSnapshot inferBt4(String fen, ProgressListener progress) {
        ActivationSnapshot out = new ActivationSnapshot();
        try {
            if (bt4Network == null && bt4LoadError == null) {
                if (!Files.exists(BT4_PATH)) {
                    bt4LoadError = "model file missing: " + BT4_PATH;
                } else {
                    report(progress, LABEL_BT4, Phase.LOADING_MODEL, BT4_PATH);
                    // The visualizer needs intermediate tensors. Those are
                    // exposed by the Java CPU path; GPU backends only return
                    // final policy/value outputs. Use the dedicated CPU loader
                    // so we never mutate JVM-wide backend selection (which would
                    // otherwise pin later BT4 search/engine loads to CPU too).
                    bt4Network = chess.nn.lc0.bt4.Network.loadCpu(BT4_PATH);
                }
            }
            if (bt4Network == null) {
                report(progress, LABEL_BT4, Phase.SYNTHETIC_FALLBACK, BT4_PATH);
                SyntheticActivations.fillBt4(fen, out);
            } else {
                report(progress, LABEL_BT4, Phase.RUNNING_INFERENCE, BT4_PATH);
                chess.core.Position position = parsePosition(fen);
                bt4Network.predict(position, out);
            }
        } catch (RuntimeException | IOException ex) {
            bt4LoadError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            // Inference may have left the snapshot half-written; start clean.
            out = new ActivationSnapshot();
            report(progress, LABEL_BT4, Phase.SYNTHETIC_FALLBACK, BT4_PATH);
            SyntheticActivations.fillBt4(fen, out);
        }
        out.seal();
        return out;
    }

    /**
     * Runs OTIS policy/WDL inference for a position and returns a freshly-built,
     * sealed snapshot. Falls back to synthetic activations when the randomized
     * placeholder weights cannot be loaded.
     *
     * @param fen current position FEN
     * @return sealed snapshot (real placeholder inference or synthetic fallback)
     */
    public synchronized ActivationSnapshot inferOtis(String fen) {
        return inferOtis(fen, null);
    }

    /**
     * Runs OTIS policy/WDL inference and reports load/inference phases.
     *
     * @param fen current position FEN
     * @param progress optional progress listener
     * @return sealed snapshot (real placeholder inference or synthetic fallback)
     */
    public synchronized ActivationSnapshot inferOtis(String fen, ProgressListener progress) {
        ActivationSnapshot out = new ActivationSnapshot();
        try {
            if (otisModel == null && otisLoadError == null) {
                if (!Files.exists(OTIS_PATH)) {
                    otisLoadError = "model file missing: " + OTIS_PATH;
                } else {
                    report(progress, LABEL_OTIS, Phase.LOADING_MODEL, OTIS_PATH);
                    // The visualizer needs intermediate tensors. Those are
                    // exposed by the Java CPU path; GPU backends only return
                    // final policy/value outputs.
                    otisModel = chess.nn.otis.Model.loadCpu(OTIS_PATH);
                }
            }
            if (otisModel == null) {
                report(progress, LABEL_OTIS, Phase.SYNTHETIC_FALLBACK, OTIS_PATH);
                SyntheticActivations.fillOtis(fen, out);
            } else {
                report(progress, LABEL_OTIS, Phase.RUNNING_INFERENCE, OTIS_PATH);
                chess.core.Position position = parsePosition(fen);
                otisModel.predict(position, out);
            }
        } catch (RuntimeException | IOException ex) {
            otisLoadError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            out = new ActivationSnapshot();
            report(progress, LABEL_OTIS, Phase.SYNTHETIC_FALLBACK, OTIS_PATH);
            SyntheticActivations.fillOtis(fen, out);
        }
        out.seal();
        return out;
    }

    /**
     * Returns a short status describing each network's load state for the UI.
     *
     * @return human-readable multi-line status
     */
    public String status() {
        return LABEL_NNUE + ": " + describe(nnueModel != null, nnueLoadError)
                + "   " + LABEL_CNN + ": " + describe(cnnNetwork != null, cnnLoadError)
                + "   " + LABEL_BT4 + ": " + describe(bt4Network != null, bt4LoadError)
                + "   " + LABEL_OTIS + ": " + describeOtis();
    }

    /**
     * Returns the per-architecture status string.
     *
     * @param key one of "nnue", "cnn", "bt4"
     * @return short status
     */
    public String statusFor(String key) {
    return switch (key) {
            case "nnue" -> describe(nnueModel != null, nnueLoadError);
            case "cnn" -> describe(cnnNetwork != null, cnnLoadError);
            case "bt4" -> describe(bt4Network != null, bt4LoadError);
            case "otis" -> describeOtis();
            default -> "?";
        };
    }

    /**
     * Returns model-file and load-state previews without triggering model
     * loading. Used by the diagnostics surface so users can distinguish
     * "installed but lazy" from "missing" and "loaded".
     *
     * @return current model status rows
     */
    public synchronized List<ModelStatus> modelStatuses() {
        return List.of(
                preview(LABEL_NNUE, nnuePath, nnueModel != null, nnueLoadError),
                preview(LABEL_CNN, CNN_PATH, cnnNetwork != null, cnnLoadError),
                preview(LABEL_BT4, BT4_PATH, bt4Network != null, bt4LoadError),
                otisPreview());
    }

    /**
     * Builds the OTIS model status row with architecture metadata.
     *
     * @return OTIS model status
     */
    private ModelStatus otisPreview() {
        boolean present = Files.exists(OTIS_PATH);
        String architecture = otisModel == null
                ? chess.nn.otis.Model.defaultArchitectureLabel()
                : otisModel.architectureLabel();
        if (otisModel != null) {
            return new ModelStatus(LABEL_OTIS, OTIS_PATH, present, true, "loaded",
                    fileDetail(OTIS_PATH, architecture + " - real inference ready"));
        }
        if (otisLoadError != null) {
            return new ModelStatus(LABEL_OTIS, OTIS_PATH, present, false, "fallback", otisLoadError);
        }
        if (present) {
            return new ModelStatus(LABEL_OTIS, OTIS_PATH, true, false, "available",
                    fileDetail(OTIS_PATH, architecture + " - loads on first inference"));
        }
        return new ModelStatus(LABEL_OTIS, OTIS_PATH, false, false, "missing",
                architecture + " - synthetic fallback");
    }

    /**
     * Builds a single model preview row.
     *
     * @param label model label
     * @param path weights path
     * @param loaded whether a live model instance exists
     * @param error load/inference error, or null
     * @return model status
     */
    private static ModelStatus preview(String label, Path path, boolean loaded, String error) {
        boolean present = path != null && Files.exists(path);
        String state;
        String detail;
        if (loaded) {
            state = "loaded";
            detail = fileDetail(path, "real inference ready");
        } else if (error != null) {
            state = "fallback";
            detail = error;
        } else if (present) {
            state = "available";
            detail = fileDetail(path, "loads on first inference");
        } else {
            state = "missing";
            detail = "synthetic fallback";
        }
    return new ModelStatus(label, path, present, loaded, state, detail);
    }

    /**
     * Returns compact file details for a model row.
     *
     * @param path file-system path
     * @param suffix fallback detail suffix
     * @return detail text
     */
    private static String fileDetail(Path path, String suffix) {
        if (path == null) {
            return suffix;
        }
        try {
            long bytes = Files.size(path);
    return readableBytes(bytes) + " - " + suffix;
        } catch (IOException | RuntimeException ex) {
            return suffix;
        }
    }

    /**
     * Formats a byte count for compact status rows.
     *
     * @param bytes size in bytes
     * @return compact size
     */
    private static String readableBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = { "KB", "MB", "GB" };
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024.0 && unit + 1 < units.length);
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    /**
     * Returns a status word for a network.
     *
     * @param loaded true when loaded
     * @param error error message or null
     * @return status word
     */
    private static String describe(boolean loaded, String error) {
        if (loaded) {
            return "real inference";
        }
        if (error != null) {
            return "synthetic (" + error + ")";
        }
        return "synthetic (not loaded yet)";
    }

    /**
     * Returns a compact OTIS status with the visible parameter count.
     *
     * @return OTIS status
     */
    private String describeOtis() {
        if (otisModel != null) {
            return "real inference - "
                    + chess.nn.otis.Model.formatParameterCount(otisModel.info().parameterCount())
                    + " params";
        }
        if (otisLoadError != null) {
            return "synthetic (" + otisLoadError + ")";
        }
        if (Files.exists(OTIS_PATH)) {
            return "available - "
                    + chess.nn.otis.Model.formatParameterCount(chess.nn.otis.Model.DEFAULT_PARAMETER_COUNT)
                    + " params";
        }
        return "synthetic (not loaded yet)";
    }

    /**
     * Reports one progress phase when a listener is present.
     *
     * @param listener optional listener
     * @param architecture architecture label
     * @param phase provider phase
     * @param path file-system path
     */
    private static void report(ProgressListener listener, String architecture, Phase phase, Path path) {
        if (listener != null) {
            listener.onProgress(architecture, phase, path);
        }
    }

    /**
     * Fills the NNUE snapshot with synthetic data as a fallback.
     *
     * @param fen FEN string
     * @param out destination
     */
    private static void fallbackNnue(String fen, ActivationSnapshot out) {
        SyntheticActivations.fillNnue(fen, out);
    }

    /**
     * Merges the model-level atlas (covers both CRTK and Stockfish-upstream
     * networks) into a per-position snapshot. Used by the upstream fallback
     * path where per-position activations are synthetic but the atlas
     * weights can still come from the real on-disk network.
     *
     * @param out destination snapshot
     */
    private void mergeAtlasFromModel(ActivationSnapshot out) {
        if (nnueAtlasCache == null) {
            if (nnueModel == null) {
                return;
            }
            ActivationSnapshot cache = new ActivationSnapshot();
            try {
                if (!nnueModel.dumpFeatureAtlas(cache)) {
                    return;
                }
                cache.seal();
                nnueAtlasCache = cache;
            } catch (RuntimeException ex) {
                // Atlas is optional; swallow and continue without it.
                return;
            }
        }
        copyAtlasInto(out);
    }

    /**
     * Copies cached atlas entries into a destination snapshot, replacing any
     * synthetic atlas data the caller may have written first.
     *
     * @param out destination snapshot
     */
    private void copyAtlasInto(ActivationSnapshot out) {
        for (Map.Entry<String, ActivationSnapshot.Entry> e
                : nnueAtlasCache.entries().entrySet()) {
            ActivationSnapshot.Entry entry = e.getValue();
            // Atlas tensors are model-level and sealed before they reach this
            // method. Sharing the immutable arrays avoids cloning the whole
            // weight atlas into every cached per-position snapshot.
            out.put(e.getKey(), entry.shape(), entry.data());
        }
    }

    /**
     * Parses a FEN into a position.
     *
     * @param fen FEN string
     * @return position
     */
    private static chess.core.Position parsePosition(String fen) {
        return new chess.core.Position(fen == null || fen.isBlank()
                ? "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                : fen);
    }
}
