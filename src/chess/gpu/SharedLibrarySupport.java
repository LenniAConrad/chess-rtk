package chess.gpu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntSupplier;

/**
 * Shared JNI loader for optional GPU backends.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SharedLibrarySupport {

  /**
   * Build directory name used by native backend projects.
   */
  private static final String BUILD_DIR = "build";

  /**
   * Release build subdirectory name used by some generators.
   */
  private static final String RELEASE_DIR = "Release";

  /**
   * Debug build subdirectory name used by some generators.
   */
  private static final String DEBUG_DIR = "Debug";

  /**
   * Library output subdirectory name used by some generators.
   */
  private static final String LIB_DIR = "lib";

  /**
   * Alternate output directory name used by some local builds.
   */
  private static final String OUT_DIR = "out";

  /**
   * Immutable load state for a JNI library.
   */
  public static final class State {
    /**
     * Whether the library load completed successfully.
     */
    private final boolean loaded;

    /**
     * Number of devices reported by the native backend.
     */
    private final int deviceCount;

     /**
     * Creates a new state instance.
     * @param loaded loaded
     * @param deviceCount device count
     */
     private State(boolean loaded, int deviceCount) {
      this.loaded = loaded;
      this.deviceCount = deviceCount;
    }

    /**
     * @return {@code true} if the JNI library was successfully loaded
     */
    public boolean loaded() {
      return loaded;
    }

    /**
     * @return device count reported by JNI (0 on failure)
     */
    public int deviceCount() {
      return deviceCount;
    }
  }

  /**
   * Utility class; instantiation is not allowed.
   */
  private SharedLibrarySupport() {}

  /**
   * Attempts to load a JNI library and query a device count.
   *
   * @param libBaseName base library name used by {@link System#loadLibrary(String)}
   * @param envVar optional environment variable holding an explicit library path
   * @param nativeDir optional native directory containing build outputs
   * @param deviceCountSupplier native call to query device count
   * @return immutable load state
   */
  public static State load(String libBaseName, String envVar, String nativeDir, IntSupplier deviceCountSupplier) {
    return load(new String[] {libBaseName}, envVar, nativeDir, deviceCountSupplier);
  }

  /**
   * Attempts to load a JNI library from multiple compatible base names and query a device count.
   *
   * @param libBaseNames base library names used by {@link System#loadLibrary(String)}, in priority order
   * @param envVar optional environment variable holding an explicit library path
   * @param nativeDir optional native directory containing build outputs
   * @param deviceCountSupplier native call to query device count
   * @return immutable load state
   */
  public static State load(String[] libBaseNames, String envVar, String nativeDir, IntSupplier deviceCountSupplier) {
    boolean loaded = tryLoad(libBaseNames, envVar, nativeDir);
    int deviceCount = loaded ? safeDeviceCount(deviceCountSupplier) : 0;
    return new State(loaded, deviceCount);
  }

  /**
   * Tries the supported load locations in priority order.
   *
   * @param libBaseNames library base names
   * @param envVar environment variable that may point to an explicit library file
   * @param nativeDir native project directory used to derive fallback paths
   * @return {@code true} when a library was loaded
   */
  private static boolean tryLoad(String[] libBaseNames, String envVar, String nativeDir) {
    for (String libBaseName : libBaseNames) {
      if (tryLoadFromLibraryPath(libBaseName)) {
        return true;
      }
    }

    Path explicit = explicitLibraryPath(envVar);
    if (explicit != null && tryLoadFromPath(explicit)) {
      return true;
    }

    for (String libBaseName : libBaseNames) {
      String filename = platformLibraryFilename(libBaseName);
      if (tryLoadFromCandidatePaths(candidatePaths(filename, nativeDir))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Tries {@link System#loadLibrary(String)} first so system-level installs work without path juggling.
   *
   * @param libBaseName library base name
   * @return {@code true} when the runtime resolves and loads the library
   */
  private static boolean tryLoadFromLibraryPath(String libBaseName) {
    try {
      System.loadLibrary(libBaseName);
      return true;
    } catch (UnsatisfiedLinkError | SecurityException ignore) {
      return false;
    }
  }

  /**
   * Maps a base library name to the platform-specific filename expected on disk.
   *
   * @param libBaseName library base name
   * @return platform-specific library filename
   */
  private static String platformLibraryFilename(String libBaseName) {
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
    return prefix + libBaseName + suffix;
  }

  /**
   * Reads an optional explicit library path from an environment variable.
   *
   * @param envVar environment variable name
   * @return existing explicit path or {@code null} when unavailable
   */
  private static Path explicitLibraryPath(String envVar) {
    if (envVar == null || envVar.isBlank()) {
      return null;
    }
    String explicit = System.getenv(envVar);
    if (explicit == null || explicit.isBlank()) {
      return null;
    }
    Path explicitPath = Path.of(explicit.trim());
    return Files.exists(explicitPath) ? explicitPath : null;
  }

  /**
   * Builds the list of filesystem locations checked after the normal library path.
   *
   * @param filename platform-specific library filename
   * @param nativeDir native project directory
   * @return ordered candidate paths to inspect
   */
  private static Path[] candidatePaths(String filename, String nativeDir) {
    if (nativeDir == null || nativeDir.isBlank()) {
      return new Path[] {Path.of(filename)};
    }
    return new Path[] {
        Path.of(filename),
        Path.of(nativeDir, BUILD_DIR, filename),
        Path.of(nativeDir, BUILD_DIR, RELEASE_DIR, filename),
        Path.of(nativeDir, BUILD_DIR, DEBUG_DIR, filename),
        Path.of(nativeDir, BUILD_DIR, LIB_DIR, filename),
        Path.of(nativeDir, OUT_DIR, filename)
    };
  }

  /**
   * Tries each candidate path until one loads successfully.
   *
   * @param candidates candidate library file paths
   * @return {@code true} when one candidate loads
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
   * Attempts to load a specific library file from disk.
   *
   * @param path library file path
   * @return {@code true} when the file exists and loads successfully
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
   * Queries the native backend for its device count without letting backend failures escape.
   *
   * @param deviceCountSupplier native device count callback
   * @return device count, or {@code 0} when the callback fails
   */
  private static int safeDeviceCount(IntSupplier deviceCountSupplier) {
    try {
      return deviceCountSupplier.getAsInt();
    } catch (UnsatisfiedLinkError | RuntimeException t) {
      return 0;
    }
  }
}
