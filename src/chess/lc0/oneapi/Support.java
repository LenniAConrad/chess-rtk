package chess.lc0.oneapi;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optional oneAPI (Intel) support via a tiny JNI shared library (no third-party Java deps).
 *
 * <p>This class loads the JNI library ({@code liblc0j_oneapi.so}/{@code lc0j_oneapi.dll})
 * and exposes capability checks such as {@link #deviceCount()}.
 *
 * <p>If the native library isn't present, or no Intel GPU exists, the Java code
 * automatically falls back to the pure-Java CPU path.
 *
 * <p>To enable oneAPI inference, build the native library and run Java with:
 * {@code -Djava.library.path=/path/to/build/dir}.
 *
 * <p>Build instructions for the native library live under {@code native/oneapi/} in this repo.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Support {

    /**
     * Base library name used by {@link System#loadLibrary(String)}.
     */
    private static final String LIB_BASE_NAME = "lc0j_oneapi";

    /**
     * Environment variable that can point to an explicit oneAPI JNI library path.
     *
     * <p>
     * Example: {@code UCICLI_ONEAPI_LIB=/absolute/path/to/liblc0j_oneapi.so}
     * </p>
     */
    private static final String ENV_ONEAPI_LIB = "UCICLI_ONEAPI_LIB";

    /**
     * Repository directory containing the optional oneAPI JNI sources/build outputs.
     */
    private static final String DIR_NATIVE_ONEAPI = "native/oneapi";

    /**
     * Default CMake build output directory name for the oneAPI JNI project.
     */
    private static final String DIR_BUILD = "build";

    /**
     * Directory name typically used by multi-config CMake generators for release builds.
     */
    private static final String DIR_RELEASE = "Release";

    /**
     * Directory name typically used by multi-config CMake generators for debug builds.
     */
    private static final String DIR_DEBUG = "Debug";

    /**
     * Common library subdirectory name.
     */
    private static final String DIR_LIB = "lib";

    /**
     * Common output directory name used in some local build setups.
     */
    private static final String DIR_OUT = "out";

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
     * no Intel GPU is visible or the runtime cannot initialize).
     * </p>
     *
     * @return {@code true} if the native library loaded.
     */
    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * Returns the number of Intel GPU devices visible to oneAPI.
     *
     * <p>If the native library is not present or the runtime cannot be initialized, this returns 0.
     *
     * @return number of available devices (0 when unavailable)
     */
    public static int deviceCount() {
        return DEVICE_COUNT;
    }

    /**
     * Tries to load from java.library.path first, then from the current directory.
     *
     * <p>Linux: liblc0j_oneapi.so, Windows: lc0j_oneapi.dll, macOS: liblc0j_oneapi.dylib (if built).
     *
     * @return {@code true} if the JNI library was found and loaded
     */
    private static boolean tryLoad() {
        if (tryLoadFromLibraryPath()) {
            return true;
        }

        String filename = platformLibraryFilename();

        Path explicit = explicitLibraryPath();
        if (explicit != null && tryLoadFromPath(explicit)) {
            return true;
        }

        return tryLoadFromCandidatePaths(candidatePaths(filename));
    }

    /**
     * Attempts to load the JNI library using {@link System#loadLibrary(String)}.
     *
     * @return {@code true} if the library was successfully loaded
     */
    private static boolean tryLoadFromLibraryPath() {
        try {
            System.loadLibrary(LIB_BASE_NAME);
            return true;
        } catch (UnsatisfiedLinkError | SecurityException ignore) {
            return false;
        }
    }

    /**
     * Returns the platform-specific filename for the JNI shared library.
     *
     * @return library filename such as {@code "liblc0j_oneapi.so"} or {@code "lc0j_oneapi.dll"}
     */
    private static String platformLibraryFilename() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String suffix;
        if (os.contains("win")) {
            suffix = ".dll";
        } else if (os.contains("mac")) {
            suffix = ".dylib";
        } else {
            suffix = ".so";
        }
        String prefix = os.contains("win") ? "" : "lib";
        return prefix + LIB_BASE_NAME + suffix;
    }

    /**
     * Resolves an explicit JNI library path from {@link #ENV_ONEAPI_LIB}.
     *
     * @return resolved path when set and exists, otherwise {@code null}
     */
    private static Path explicitLibraryPath() {
        String explicit = System.getenv(ENV_ONEAPI_LIB);
        if (explicit == null || explicit.isBlank()) {
            return null;
        }
        Path explicitPath = Path.of(explicit.trim());
        return Files.exists(explicitPath) ? explicitPath : null;
    }

    /**
     * Returns candidate locations that may contain the built JNI library.
     *
     * @param filename platform-specific library filename
     * @return candidate paths to attempt (never null)
     */
    private static Path[] candidatePaths(String filename) {
        return new Path[] {
                Path.of(filename),
                Path.of(DIR_NATIVE_ONEAPI, DIR_BUILD, filename),
                Path.of(DIR_NATIVE_ONEAPI, DIR_BUILD, DIR_RELEASE, filename),
                Path.of(DIR_NATIVE_ONEAPI, DIR_BUILD, DIR_DEBUG, filename),
                Path.of(DIR_NATIVE_ONEAPI, DIR_BUILD, DIR_LIB, filename),
                Path.of(DIR_NATIVE_ONEAPI, DIR_OUT, filename)
        };
    }

    /**
     * Attempts to load the JNI library from a list of candidate locations.
     *
     * @param candidates candidate locations
     * @return {@code true} if any candidate successfully loaded
     */
    private static boolean tryLoadFromCandidatePaths(Path[] candidates) {
        for (Path candidate : candidates) {
            if (tryLoadFromPath(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to load the JNI library from a specific filesystem location.
     *
     * @param path path to the shared library
     * @return {@code true} if the path exists and the load succeeds
     */
    private static boolean tryLoadFromPath(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        try {
            System.load(path.toAbsolutePath().toString());
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
     * JNI entry point implemented in {@code native/oneapi/lc0j_oneapi_jni.cpp}.
     *
     * @return number of visible Intel GPU devices (0 on error)
     */
    private static native int nativeDeviceCount();
}
