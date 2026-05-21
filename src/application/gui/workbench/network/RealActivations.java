package application.gui.workbench.network;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

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
        ActivationSnapshot out = new ActivationSnapshot();
        try {
            if (nnueModel == null && nnueLoadError == null) {
                if (!Files.exists(nnuePath)) {
                    nnueLoadError = "model file missing: " + nnuePath;
                } else {
                    nnueModel = chess.nn.nnue.Model.load(nnuePath);
                    refreshNnueVersionLabel();
                }
            }
            if (nnueModel == null) {
                fallbackNnue(fen, out);
            } else {
                chess.core.Position position = parsePosition(fen);
                nnueModel.predict(position, out);
                mergeAtlasFromModel(out);
            }
        } catch (RuntimeException | IOException ex) {
            nnueLoadError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            // Inference may have left the snapshot half-written; start clean.
            out = new ActivationSnapshot();
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
                sb.append("  ·  Stockfish HalfKP (").append(up.variant().label()).append(")")
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
        ActivationSnapshot out = new ActivationSnapshot();
        try {
            if (cnnNetwork == null && cnnLoadError == null) {
                if (!Files.exists(CNN_PATH)) {
                    cnnLoadError = "model file missing: " + CNN_PATH;
                } else {
                    // The visualizer needs intermediate tensors. Those are
                    // exposed by the Java CPU path; GPU backends only return
                    // final policy/value outputs.
                    cnnNetwork = chess.nn.lc0.cnn.Network.loadCpu(CNN_PATH);
                }
            }
            if (cnnNetwork == null) {
                SyntheticActivations.fillCnn(fen, out);
            } else {
                chess.core.Position position = parsePosition(fen);
                float[] planes = chess.nn.lc0.cnn.Encoder.encode(position);
                cnnNetwork.predictEncoded(planes, out);
            }
        } catch (RuntimeException | IOException ex) {
            cnnLoadError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            out = new ActivationSnapshot();
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
        ActivationSnapshot out = new ActivationSnapshot();
        try {
            if (bt4Network == null && bt4LoadError == null) {
                if (!Files.exists(BT4_PATH)) {
                    bt4LoadError = "model file missing: " + BT4_PATH;
                } else {
                    System.setProperty("crtk.lc0.bt4.backend", "cpu");
                    bt4Network = chess.nn.lc0.bt4.Network.load(BT4_PATH);
                }
            }
            if (bt4Network == null) {
                SyntheticActivations.fillBt4(fen, out);
            } else {
                chess.core.Position position = parsePosition(fen);
                bt4Network.predict(position, out);
            }
        } catch (RuntimeException | IOException ex) {
            bt4LoadError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            // Inference may have left the snapshot half-written; start clean.
            out = new ActivationSnapshot();
            SyntheticActivations.fillBt4(fen, out);
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
        return "NNUE: " + describe(nnueModel != null, nnueLoadError)
                + "   CNN: " + describe(cnnNetwork != null, cnnLoadError)
                + "   BT4: " + describe(bt4Network != null, bt4LoadError);
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
                preview("NNUE", nnuePath, nnueModel != null, nnueLoadError),
                preview("LC0 CNN", CNN_PATH, cnnNetwork != null, cnnLoadError),
                preview("LC0 BT4", BT4_PATH, bt4Network != null, bt4LoadError));
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
     * @param path model path
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
     * Fills the NNUE snapshot with synthetic data as a fallback.
     *
     * @param fen FEN
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
     * Returns the cached atlas for an arbitrary NNUE file path, loading and
     * caching the network on first call. Used by diff and grid views.
     *
     * @param path path to a .nnue file or null for the current network
     * @return atlas snapshot or null on load failure
     */
    public synchronized ActivationSnapshot atlasFor(Path path) {
        if (path == null || path.equals(nnuePath)) {
            // Use the live cache when asked about the current network so we
            // pay no extra cost for the most common case.
            if (nnueAtlasCache != null) {
                return nnueAtlasCache;
            }
            return null;
        }
        ActivationSnapshot hit = atlasCacheByPath.get(path);
        if (hit != null) {
            return hit;
        }
        if (!Files.exists(path)) {
            return null;
        }
        try (chess.nn.nnue.Model side = chess.nn.nnue.Model.load(path)) {
            ActivationSnapshot snap = new ActivationSnapshot();
            if (!side.dumpFeatureAtlas(snap)) {
                return null;
            }
            snap.seal();
            atlasCacheByPath.put(path, snap);
            return snap;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * Parses a FEN into a position.
     *
     * @param fen FEN
     * @return position
     */
    private static chess.core.Position parsePosition(String fen) {
        return new chess.core.Position(fen == null || fen.isBlank()
                ? "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                : fen);
    }
}
