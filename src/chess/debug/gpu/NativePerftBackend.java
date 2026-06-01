package chess.debug.gpu;

import java.util.Locale;

import chess.core.Position;
import chess.core.Setup;
import chess.gpu.BackendNames;

/**
 * Selects an available native perft backend (CUDA, ROCm, or oneAPI) and
 * dispatches bulk-perft calls to it.
 *
 * <p>
 * Selection mirrors the OTIS model: the {@code crtk.perft.backend} system
 * property forces a specific vendor ({@code cuda}/{@code rocm}/{@code oneapi}),
 * while the default {@code auto} tries CUDA, then ROCm, then oneAPI. There is no
 * dynamic registry; the three vendor backends are enumerated by hand, matching
 * the rest of the codebase.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class NativePerftBackend {

    /**
     * Number of detailed counters returned per packed position.
     */
    private static final int DETAIL_FIELDS = 7;

    /**
     * Vendor used by the last detailed-capability probe.
     */
    private static volatile Vendor detailedProbeVendor;

    /**
     * Cached result from the last detailed-capability probe.
     */
    private static volatile Boolean detailedProbeAvailable;

    /**
     * System property selecting a specific native perft backend.
     */
    public static final String BACKEND_PROPERTY = "crtk.perft.backend";

    /**
     * Utility class; prevents instantiation.
     */
    private NativePerftBackend() {
    }

    /**
     * Supported native vendors.
     */
    public enum Vendor {
        /**
         * NVIDIA CUDA backend.
         */
        CUDA,
        /**
         * AMD ROCm/HIP backend.
         */
        ROCM,
        /**
         * Intel oneAPI/SYCL backend.
         */
        ONEAPI
    }

    /**
     * Returns whether any native perft backend is available.
     *
     * @return true when a backend can service bulk-perft calls
     */
    public static boolean isAvailable() {
        return select() != null;
    }

    /**
     * Returns whether the selected backend can service detailed perft counters.
     *
     * <p>
     * Older native perft libraries expose only the node-count JNI symbol. Treat
     * that as a partial backend instead of failing later with an
     * {@link UnsatisfiedLinkError}; node-only perft can still use the device.
     * </p>
     *
     * @return true when the selected backend exports detailed counters
     */
    public static boolean isDetailedAvailable() {
        Vendor vendor = select();
        if (vendor == null) {
            return false;
        }
        Boolean cached = detailedProbeAvailable;
        if (cached != null && vendor == detailedProbeVendor) {
            return cached.booleanValue();
        }
        synchronized (NativePerftBackend.class) {
            cached = detailedProbeAvailable;
            if (cached != null && vendor == detailedProbeVendor) {
                return cached.booleanValue();
            }
            boolean available = probeDetailed(vendor);
            detailedProbeVendor = vendor;
            detailedProbeAvailable = available;
            return available;
        }
    }

    /**
     * Returns the selected vendor, or {@code null} when none is available.
     *
     * @return selected vendor or null
     */
    public static Vendor selected() {
        return select();
    }

    /**
     * Returns the selected backend identifier for diagnostics.
     *
     * @return backend id (cuda/rocm/oneapi), or "none"
     */
    public static String name() {
        Vendor vendor = select();
        if (vendor == null) {
            return "none";
        }
        return switch (vendor) {
            case CUDA -> BackendNames.CUDA;
            case ROCM -> BackendNames.ROCM;
            case ONEAPI -> BackendNames.ONEAPI;
        };
    }

    /**
     * Counts {@code perft(remainingDepth)} for each packed frontier position on
     * the selected device.
     *
     * @param packed packed positions ({@code count * PositionCodec.WORDS} longs)
     * @param count number of frontier positions
     * @param remainingDepth depth below each frontier position
     * @return per-position leaf-node counts
     * @throws IllegalStateException when no backend is available or the native call fails
     */
    public static long[] bulkPerft(long[] packed, int count, int remainingDepth) {
        Vendor vendor = select();
        if (vendor == null) {
            throw new IllegalStateException("no native perft backend available");
        }
        long[] counts = switch (vendor) {
            case CUDA -> chess.nn.perft.cuda.Backend.bulkPerft(packed, count, remainingDepth);
            case ROCM -> chess.nn.perft.rocm.Backend.bulkPerft(packed, count, remainingDepth);
            case ONEAPI -> chess.nn.perft.oneapi.Backend.bulkPerft(packed, count, remainingDepth);
        };
        if (counts == null) {
            throw new IllegalStateException("native perft backend (" + vendor + ") returned no result");
        }
        return counts;
    }

    /**
     * Counts detailed perft (7 counters per frontier position) on the selected
     * device.
     *
     * @param packed packed positions ({@code count * PositionCodec.WORDS} longs)
     * @param count number of frontier positions
     * @param remainingDepth depth below each frontier position
     * @return {@code count * 7} counters
     * @throws IllegalStateException when no backend is available or the native call fails
     */
    public static long[] bulkPerftDetailed(long[] packed, int count, int remainingDepth) {
        Vendor vendor = select();
        if (vendor == null) {
            throw new IllegalStateException("no native perft backend available");
        }
        long[] counts = bulkPerftDetailed(vendor, packed, count, remainingDepth);
        if (counts == null) {
            throw new IllegalStateException("native perft backend (" + vendor + ") returned no result");
        }
        return counts;
    }

    /**
     * Probes the detailed JNI entry point for a vendor.
     *
     * @param vendor selected backend vendor
     * @return true when a one-position depth-zero detailed call succeeds
     */
    private static boolean probeDetailed(Vendor vendor) {
        try {
            long[] packed = PositionCodec.pack(new Position[] {Setup.getStandardStartPosition()});
            long[] counts = bulkPerftDetailed(vendor, packed, 1, 0);
            return counts != null && counts.length == DETAIL_FIELDS;
        } catch (UnsatisfiedLinkError | NoSuchMethodError ex) {
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Dispatches detailed bulk perft to an explicit vendor.
     *
     * @param vendor selected backend vendor
     * @param packed packed positions
     * @param count number of packed positions
     * @param remainingDepth depth below each packed position
     * @return flat detailed counters, or null on native failure
     */
    private static long[] bulkPerftDetailed(Vendor vendor, long[] packed, int count, int remainingDepth) {
        return switch (vendor) {
            case CUDA -> chess.nn.perft.cuda.Backend.bulkPerftDetailed(packed, count, remainingDepth);
            case ROCM -> chess.nn.perft.rocm.Backend.bulkPerftDetailed(packed, count, remainingDepth);
            case ONEAPI -> chess.nn.perft.oneapi.Backend.bulkPerftDetailed(packed, count, remainingDepth);
        };
    }

    /**
     * Resolves the backend to use, honoring {@link #BACKEND_PROPERTY}.
     *
     * @return selected vendor, or null when none is available
     */
    private static Vendor select() {
        String request = System.getProperty(BACKEND_PROPERTY, "auto").trim().toLowerCase(Locale.ROOT);
        return switch (request) {
            case "cuda" -> chess.nn.perft.cuda.Backend.isAvailable() ? Vendor.CUDA : null;
            case "rocm", "amd", "hip" -> chess.nn.perft.rocm.Backend.isAvailable() ? Vendor.ROCM : null;
            case "oneapi", "intel", "sycl" -> chess.nn.perft.oneapi.Backend.isAvailable() ? Vendor.ONEAPI : null;
            default -> auto();
        };
    }

    /**
     * Auto-selects the first available backend (CUDA, then ROCm, then oneAPI).
     *
     * @return first available vendor, or null
     */
    private static Vendor auto() {
        if (chess.nn.perft.cuda.Backend.isAvailable()) {
            return Vendor.CUDA;
        }
        if (chess.nn.perft.rocm.Backend.isAvailable()) {
            return Vendor.ROCM;
        }
        if (chess.nn.perft.oneapi.Backend.isAvailable()) {
            return Vendor.ONEAPI;
        }
        return null;
    }
}
