package application.gui.workbench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
final class WorkbenchRealActivations {

    /**
     * NNUE weights path (Stockfish .nnue or CRTK .bin).
     */
    private static final Path NNUE_PATH = Path.of("models/crtk-halfkp.nnue");

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
    private String nnueVersionLabel = "synthetic NNUE (FEN-seeded)";

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
    WorkbenchRealActivations() {
        // models are loaded lazily on first inference call
    }

    /**
     * Fills an NNUE snapshot from real inference. Falls back to synthetic
     * activations when the network cannot be loaded.
     *
     * @param fen current position FEN
     * @param out destination snapshot
     * @return true when real inference produced the snapshot, false on fallback
     */
    synchronized boolean fillNnue(String fen, WorkbenchActivationSnapshot out) {
        out.clear();
        try {
            if (nnueModel == null && nnueLoadError == null) {
                if (!Files.exists(NNUE_PATH)) {
                    nnueLoadError = "model file missing: " + NNUE_PATH;
                } else {
                    nnueModel = chess.nn.nnue.Model.load(NNUE_PATH);
                    refreshNnueVersionLabel();
                }
            }
            if (nnueModel == null) {
                fallbackNnue(fen, out);
                return false;
            }
            chess.nn.nnue.Network inner = nnueModel.crtkNetwork();
            if (inner == null) {
                // Stockfish-format upstream network: capture not supported yet.
                nnueLoadError = "Stockfish upstream NNUE: capture not supported (using synthetic)";
                fallbackNnue(fen, out);
                return false;
            }
            chess.core.Position position = parsePosition(fen);
            inner.predict(position, out);
            return true;
        } catch (RuntimeException | IOException ex) {
            nnueLoadError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            fallbackNnue(fen, out);
            return false;
        }
    }

    /**
     * Returns a short label describing the loaded NNUE version.
     *
     * @return version string
     */
    synchronized String nnueVersionLabel() {
        return nnueVersionLabel;
    }

    /**
     * Refreshes the NNUE version label after a successful model load.
     */
    private void refreshNnueVersionLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append(NNUE_PATH.getFileName().toString());
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
     * Fills an LC0 CNN snapshot. CNN currently uses synthetic activations
     * because the CNN forward pass does not yet expose intermediate hooks;
     * inference is still run to produce real final outputs.
     *
     * @param fen current position FEN
     * @param out destination snapshot
     * @return true when real inference contributed to the snapshot
     */
    synchronized boolean fillCnn(String fen, WorkbenchActivationSnapshot out) {
        WorkbenchSyntheticActivations.fillCnn(fen, out);
        try {
            if (cnnNetwork == null && cnnLoadError == null) {
                if (!Files.exists(CNN_PATH)) {
                    cnnLoadError = "model file missing: " + CNN_PATH;
                } else {
                    cnnNetwork = chess.nn.lc0.cnn.Network.load(CNN_PATH);
                }
            }
            if (cnnNetwork == null) {
                return false;
            }
            chess.core.Position position = parsePosition(fen);
            float[] planes = chess.nn.lc0.cnn.Encoder.encode(position);
            chess.nn.lc0.cnn.Network.Prediction prediction = cnnNetwork.predictEncoded(planes);
            overrideCnnFinalOutputs(out, prediction);
            return true;
        } catch (RuntimeException | IOException ex) {
            cnnLoadError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return false;
        }
    }

    /**
     * Fills an LC0 BT4 snapshot from real inference. Falls back to synthetic
     * activations when the network cannot be loaded.
     *
     * @param fen current position FEN
     * @param out destination snapshot
     * @return true when real inference produced the snapshot
     */
    synchronized boolean fillBt4(String fen, WorkbenchActivationSnapshot out) {
        out.clear();
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
                WorkbenchSyntheticActivations.fillBt4(fen, out);
                return false;
            }
            chess.core.Position position = parsePosition(fen);
            bt4Network.predict(position, out);
            return true;
        } catch (RuntimeException | IOException ex) {
            bt4LoadError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            WorkbenchSyntheticActivations.fillBt4(fen, out);
            return false;
        }
    }

    /**
     * Returns a short status describing each network's load state for the UI.
     *
     * @return human-readable multi-line status
     */
    String status() {
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
    String statusFor(String key) {
        return switch (key) {
            case "nnue" -> describe(nnueModel != null && nnueModel.crtkNetwork() != null,
                    nnueLoadError);
            case "cnn" -> describeMixed(cnnNetwork != null, cnnLoadError,
                    "real heads + synthetic blocks");
            case "bt4" -> describe(bt4Network != null, bt4LoadError);
            default -> "?";
        };
    }

    /**
     * Returns a mixed-mode status word used when a network's final outputs are
     * real but its intermediate activations are synthetic.
     *
     * @param loaded whether the model is loaded
     * @param error error message or null
     * @param mixedLabel label to use for the mixed case
     * @return short status
     */
    private static String describeMixed(boolean loaded, String error, String mixedLabel) {
        if (loaded) {
            return mixedLabel;
        }
        if (error != null) {
            return "synthetic (" + error + ")";
        }
        return "synthetic (not loaded yet)";
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
    private static void fallbackNnue(String fen, WorkbenchActivationSnapshot out) {
        WorkbenchSyntheticActivations.fillNnue(fen, out);
    }

    /**
     * Overrides the synthetic CNN final policy/value entries with real values.
     *
     * @param out snapshot
     * @param prediction real CNN prediction
     */
    private static void overrideCnnFinalOutputs(WorkbenchActivationSnapshot out,
            chess.nn.lc0.cnn.Network.Prediction prediction) {
        if (prediction == null) {
            return;
        }
        if (prediction.policy() != null) {
            float[] policy = prediction.policy();
            out.put("cnn.policy.logits", new int[] { policy.length }, policy.clone());
        }
        float[] wdl = prediction.wdl();
        if (wdl != null && wdl.length == 3) {
            out.put("cnn.value.wdl", new int[] { 3 }, new float[] { wdl[0], wdl[1], wdl[2] });
            out.putScalar("cnn.value.scalar", wdl[0] - wdl[2]);
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
