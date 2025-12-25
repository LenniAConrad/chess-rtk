package chess.lc0.cuda;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optional CUDA support via a tiny JNI shared library (no third-party Java deps).
 *
 * <p>This class is responsible for loading the JNI library ({@code liblc0j_cuda.so}/{@code lc0j_cuda.dll})
 * and exposing simple capability checks such as {@link #deviceCount()}.
 *
 * <p>If the native library isn't present, or no CUDA device exists, the Java code
 * automatically falls back to the pure-Java CPU path.
 *
 * <p>To enable CUDA inference, build the native library and run Java with:
 * {@code -Djava.library.path=/path/to/build/dir}.
 *
 * <p>Build instructions for the native library live under {@code native-cuda/} in this repo.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Support {
    /**
     * Base library name used by {@link System#loadLibrary(String)}.
     */
    private static final String LIB_BASE_NAME = "lc0j_cuda";

    /**
     * Whether the JNI library was successfully loaded.
     */
    private static final boolean LOADED = tryLoad();

    /**
     * Cached device count (0 if unavailable).
     */
    private static final int DEVICE_COUNT = LOADED ? safeDeviceCount() : 0;

    /**
     * Utility class, prevents instantiation.
     */
    private Support() {}

    /**
     * @return {@code true} if the JNI library loaded and {@link #deviceCount()} is greater than zero
     */
    public static boolean isAvailable() {
        return DEVICE_COUNT > 0;
    }

    /**
     * Returns whether the JNI library was successfully loaded.
     *
     * <p>
     * This can be {@code true} even when {@link #deviceCount()} is zero (for example, when the library is present but
     * no CUDA device is visible or the CUDA runtime cannot initialize).
     * </p>
     *
     * @return {@code true} if the native library loaded.
     */
    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * Returns the number of CUDA devices visible to the CUDA runtime.
     *
     * <p>If the native library is not present or the CUDA runtime cannot be initialized, this returns 0.
     *
     * @return number of available CUDA devices (0 when unavailable)
     */
    public static int deviceCount() {
        return DEVICE_COUNT;
    }

    /**
     * Tries to load from java.library.path first, then from the current directory.
     *
     * <p>Linux: liblc0j_cuda.so, Windows: lc0j_cuda.dll, macOS: liblc0j_cuda.dylib (if built).
     *
     * @return {@code true} if the JNI library was found and loaded
     */
    private static boolean tryLoad() {
        try {
            System.loadLibrary(LIB_BASE_NAME);
            return true;
        } catch (UnsatisfiedLinkError | SecurityException ignore) {
            // fall through
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        String suffix;
        if (os.contains("win")) suffix = ".dll";
        else if (os.contains("mac")) suffix = ".dylib";
        else suffix = ".so";

        String prefix = os.contains("win") ? "" : "lib";
        Path local = Path.of(prefix + LIB_BASE_NAME + suffix);
        if (!Files.exists(local)) {
            return false;
        }
        try {
            System.load(local.toAbsolutePath().toString());
            return true;
        } catch (UnsatisfiedLinkError | SecurityException ignore) {
            return false;
        }
    }

    /**
     * Calls into the JNI library to query device count, returning 0 on any failure.
     *
     * @return device count reported by JNI, or 0 on failure
     */
    private static int safeDeviceCount() {
        try {
            return nativeDeviceCount();
        } catch (UnsatisfiedLinkError | RuntimeException t) {
            return 0;
        }
    }

    /**
     * JNI entry point implemented in {@code native-cuda/lc0j_cuda_jni.cu}.
     *
     * @return number of visible CUDA devices (0 on error)
     */
    private static native int nativeDeviceCount();
}
