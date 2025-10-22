package application;

import chess.core.Setup;
import chess.debug.LogService;
import chess.eco.Encyclopedia;
import chess.uci.Filter;
import chess.uci.Filter.FilterDSL;
import chess.uci.Filter.Gate;
import utility.Toml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;

/**
 * Used for central configuration for the CLI application.
 * Loads a TOML configuration from disk with robust defaults, logging, and
 * simple fallbacks.
 *
 * <p>
 * Expected TOML keys:
 * </p>
 * <ul>
 * <li>protocol-path (string)</li>
 * <li>max-nodes (long; per-position node cap)</li>
 * <li>max-duration (long; per-position time cap in ms)</li>
 * <li>puzzle-quality (string; Filter DSL)</li>
 * <li>puzzle-winning (string; Filter DSL)</li>
 * <li>puzzle-drawing (string; Filter DSL)</li>
 * <li>puzzle-accelerate (string; Filter DSL)</li>
 * </ul>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Config {

    /**
     * Used for preventing instantiation of a static utility holder.
     */
    private Config() {
        // Prevent instantiation
    }

    /**
     * Used for building TOML key/value lines: key + EQ + value.
     */
    private static final String EQ = " = ";

    /**
     * Used for representing a single double-quote character in TOML assembly.
     */
    private static final String Q = "\"";

    /**
     * Used for building TOML key/value lines: key + EQ_Q + value + Q.
     */
    private static final String EQ_Q = EQ + Q; // used to build lines: key + EQ_Q + value + Q

    /**
     * Used for holding the relative path of the CLI config TOML file.
     */
    private static final String CONFIG_PATH = "config/cli.config.toml";

    /**
     * Used for holding the TOML key of the engine protocol path.
     */
    private static final String K_PROTOCOL_PATH = "protocol-path";

    /**
     * Used for holding the TOML key of the output path.
     */
    private static final String K_OUTPUT = "output";

    /**
     * Used for holding the TOML key of the engine instance count.
     */
    private static final String K_ENGINE_INSTANCES = "engine-instances";

    /**
     * Used for holding the TOML key of the maximum per-position node cap.
     */
    private static final String K_MAX_NODES = "max-nodes";

    /**
     * Used for holding the TOML key of the maximum per-position time cap in
     * milliseconds.
     */
    private static final String K_MAX_DURATION = "max-duration";

    /**
     * Used for holding the TOML key of the puzzle quality filter string.
     */
    private static final String K_PUZZLE_QUALITY = "puzzle-quality";

    /**
     * Used for holding the TOML key of the puzzle winning filter string.
     */
    private static final String K_PUZZLE_WINNING = "puzzle-winning";

    /**
     * Used for holding the TOML key of the puzzle drawing filter string.
     */
    private static final String K_PUZZLE_DRAWING = "puzzle-drawing";

    /**
     * Used for holding the TOML key of the puzzle acceleration (prefilter) string.
     */
    private static final String K_PUZZLE_ACCELERATE = "puzzle-accelerate";

    /**
     * Used for providing a default path to the engine protocol configuration TOML
     * file.
     */
    private static final String DEFAULT_PROTOCOL_PATH = "config/default.engine.toml";

    /**
     * Used for providing a default output for result dumps.
     */
    private static final String DEFAULT_OUTPUT = "dump/";

    /**
     * Used for providing a default count of engines for concurrent computation.
     */
    private static final int DEFAULT_ENGINE_INSTANCES = 1;

    /**
     * Used for providing the default per-position duration cap in milliseconds.
     */
    private static final long DEFAULT_MAX_NODES = 100_000_000L;

    /**
     * Used for caching the configured per-position duration cap in milliseconds.
     */
    private static final long DEFAULT_MAX_DURATION = 60_000L;

    /**
     * Used for providing the default Filter-DSL for puzzle quality selection.
     */
    private static final String DEFAULT_PUZZLE_QUALITY = "gate=AND;null=false;empty=false;"
            + "leaf[gate=AND;break=1;nodes>=50000000];"
            + "leaf[gate=AND;break=2;null=false;empty=false;nodes>=50000000];";

    /**
     * Used for providing the default Filter-DSL for identifying winning puzzles.
     */
    private static final String DEFAULT_PUZZLE_WINNING = "gate=AND;leaf[eval>=3.0];leaf[break=2;eval<=0.0];";

    /**
     * Used for providing the default Filter-DSL for identifying drawing puzzles.
     */
    private static final String DEFAULT_PUZZLE_DRAWING = "gate=AND;leaf[eval>=0.0];leaf[break=2;eval<=-3.0];";

    /**
     * Used for providing a fast prefilter Filter-DSL (acceleration) that
     * eliminates obvious non-target positions before deeper checks.
     */
    private static final String DEFAULT_PUZZLE_ACCELERATE = "gate=AND;"
            + "leaf[break=1;nodes>=2000000];"
            + "leaf[break=2;nodes>=2000000];"
            + "leaf[gate=OR;eval<3.0;leaf[break=2;eval>0.0]];"
            + "leaf[gate=OR;eval<0.0;leaf[break=2;eval>-3.0]];";

    /**
     * Used for seeding a brand-new configuration file with explanatory comments and
     * defaults that mirror the constant defaults in this class.
     */
    private static final String[] DEFAULT_CONFIG_FILE = {
            "# Engine protocol (path to your UCI/TOML config for Stockfish or compatible)",
            K_PROTOCOL_PATH + EQ_Q + DEFAULT_PROTOCOL_PATH + Q,
            "",
            "# Max amount of nodes to search per position.",
            K_MAX_NODES + EQ + DEFAULT_MAX_NODES,
            "",
            "# Max amount of time (ms) to search per position.",
            K_MAX_DURATION + EQ + DEFAULT_MAX_DURATION,
            "",
            "# Quality gate - require >= 50M nodes on PV1 and PV2 before accepting a puzzle.",
            K_PUZZLE_QUALITY + EQ_Q + DEFAULT_PUZZLE_QUALITY + Q,
            "",
            "# Winning puzzle - PV1 >= +3.0 and PV2 <= 0.0",
            K_PUZZLE_WINNING + EQ_Q + DEFAULT_PUZZLE_WINNING + Q,
            "",
            "# Drawing puzzle - PV1 >= 0.0 and PV2 <= -3.0",
            K_PUZZLE_DRAWING + EQ_Q + DEFAULT_PUZZLE_DRAWING + Q,
            "",
            "# Accelerate (prefilter) - >=2M nodes on PV1 and PV2 and NOT(win) and NOT(draw)",
            K_PUZZLE_ACCELERATE + EQ_Q + DEFAULT_PUZZLE_ACCELERATE + Q
    };

    /**
     * Used for caching the configured engine protocol path in memory.
     */
    private static volatile String protocolPath;

    /**
     * Used for caching the configured output path in memory.
     */
    private static volatile String output;

    /**
     * Used for caching the configured number of engine instances in memory.
     */
    private static volatile int engineInstances;

    /**
     * Used for caching the configured puzzle quality DSL string in memory.
     */
    private static volatile String puzzleQualityString;

    /**
     * Used for caching the configured puzzle winning DSL string in memory.
     */
    private static volatile String puzzleWinningString;

    /**
     * Used for caching the configured puzzle drawing DSL string in memory.
     */
    private static volatile String puzzleDrawingString;

    /**
     * Used for caching the configured puzzle acceleration DSL string in memory.
     */
    private static volatile String puzzleAccelerateString;

    /**
     * Used for caching the configured per-position node cap in memory.
     */
    private static volatile long maxNodes;

    /**
     * Used for caching the configured per-position duration cap in milliseconds in
     * memory.
     */
    private static volatile long maxDuration;

    /**
     * Used for holding the parsed Filter tree for the puzzle quality filter.
     */
    private static Filter puzzleQuality;

    /**
     * Used for holding the parsed Filter tree for the puzzle winning filter.
     */
    private static Filter puzzleWinning;

    /**
     * Used for holding the parsed Filter tree for the puzzle drawing filter.
     */
    private static Filter puzzleDrawing;

    /**
     * Used for holding the parsed Filter tree for the puzzle acceleration
     * filter.
     */
    private static Filter puzzleAccelerate;

    /**
     * Used for holding the parsed Filter tree for the puzzle verification filter.
     */
    private static Filter puzzleVerify;

    /**
     * Used for static initialization of the configuration at class load time.
     * Initializes directories, seeds defaults, loads TOML, and parses filters.
     */
    static {
        init();
        Path cfg = Paths.get(CONFIG_PATH);
        LogService.info("Config initialized (" + LogService.pathAbs(cfg) + ")");
    }

    /**
     * Used for obtaining the configured per-position node cap.
     *
     * @return maximum number of nodes allowed per position.
     */
    public static long getMaxNodes() {
        return maxNodes;
    }

    /**
     * Used for obtaining the configured per-position time cap in milliseconds.
     *
     * @return maximum duration in milliseconds allowed per position.
     */
    public static long getMaxDuration() {
        return maxDuration;
    }

    /**
     * Used for obtaining the parsed Filter representing the puzzle quality gate.
     *
     * @return parsed Filter for the quality gate.
     */
    public static Filter getPuzzleQuality() {
        return puzzleQuality;
    }

    /**
     * Used for obtaining the parsed Filter representing the winning-puzzle gate.
     *
     * @return parsed Filter for the winning gate.
     */
    public static Filter getPuzzleWinning() {
        return puzzleWinning;
    }

    /**
     * Used for obtaining the parsed Filter representing the drawing-puzzle gate.
     *
     * @return parsed Filter for the drawing gate.
     */
    public static Filter getPuzzleDrawing() {
        return puzzleDrawing;
    }

    /**
     * Used for obtaining the parsed Filter representing the acceleration
     * prefilter.
     *
     * @return parsed Filter for the acceleration prefilter.
     */
    public static Filter getPuzzleAccelerate() {
        return puzzleAccelerate;
    }

    /**
     * Used for obtaining the parsed Filter representing the overall puzzle
     * verification gate. Will verify both quality and winning/drawing puzzles.
     *
     * @return parsed Filter for the overall puzzle verification.
     */
    public static Filter getPuzzleVerify() {
        return puzzleVerify;
    }

    /**
     * Used for obtaining the configured engine protocol TOML path.
     *
     * @return absolute or relative protocol configuration path as a string.
     */
    public static String getProtocolPath() {
        return protocolPath;
    }

    /**
     * Used for obtaining the configured output.
     *
     * @return output path as a string.
     */
    public static String getOutput() {
        return output;
    }

    /**
     * Used for obtaining the configured number of engine instances.
     *
     * @return number of engine instances to use.
     */
    public static int getEngineInstances() {
        return engineInstances;
    }

    /**
     * Used for reloading configuration from disk in a thread-safe manner.
     * Re-runs the initialization pipeline and refreshes in-memory caches.
     */
    public static synchronized void reload() {
        init();
        LogService.info("Config reloaded (" + CONFIG_PATH + ")");
    }

    /**
     * Initializes configuration in a fixed, safe order:
     * <ol>
     * <li>Ensure config directory and seed defaults if missing.</li>
     * <li>Load TOML (line-tolerant) and log any parse issues.</li>
     * <li>Apply string/numeric values with per-key fallbacks.</li>
     * <li>Parse DSL settings with per-key fallbacks.</li>
     * </ol>
     *
     * <ul>
     * <li><b>Thread-safe:</b> synchronized to avoid concurrent init; use
     * {@code reload()} if needed.</li>
     * <li><b>Error handling:</b> no exceptions on user config errors—issues are
     * reported via {@code LogService.warn}.</li>
     * </ul>
     */
    private static synchronized void init() {
        final Path path = Paths.get(CONFIG_PATH);
        ensureConfigDirectory(path);
        seedConfigIfMissing(path);
        Optional<Toml> tomlOpt = loadToml(path);

        if (tomlOpt.isEmpty()) {
            LogService.warn("Config: TOML load failed; using all defaults.");
        } else {
            Toml t = tomlOpt.get();
            if (!t.getErrors().isEmpty()) {
                LogService.warn("Config: TOML had " + t.getErrors().size() + " issue(s):");
                for (String err : t.getErrors()) {
                    LogService.warn("  • " + err);
                }
            }
        }

        loadStringsOrDefaults(tomlOpt);
        loadNumericsOrDefaults(tomlOpt);
        parseFilterOrFallback();
    }

    /**
     * Used for ensuring the parent directory of the configuration file exists.
     *
     * @param path path of the configuration file whose parent should exist.
     */
    private static void ensureConfigDirectory(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            LogService.error(e, "Config: cannot create directory for " + CONFIG_PATH);
        }
    }

    /**
     * Used for creating a default configuration file when none exists.
     *
     * @param path path of the configuration file to create and seed.
     */
    private static void seedConfigIfMissing(Path path) {
        if (Files.exists(path))
            return;
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String line : DEFAULT_CONFIG_FILE) {
                w.write(line);
                w.newLine();
            }
            LogService.info("Config: created default config at " + CONFIG_PATH);
        } catch (IOException e) {
            LogService.error(e, "Config: failed to create default file at " + CONFIG_PATH);
        }
    }

    /**
     * Used for reading and parsing TOML from disk via {@link utility.Toml}.
     * Returns an empty Optional on failure, allowing callers to use hard defaults.
     *
     * @param path path to the TOML configuration file to load.
     * @return Optional containing a parsed {@link Toml} object if successful; empty
     *         otherwise.
     */
    private static Optional<Toml> loadToml(Path path) {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return Optional.of(Toml.load(r));
        } catch (IOException e) {
            LogService.error(e, "Config: failed to read " + CONFIG_PATH + " - using hard defaults");
            return Optional.empty();
        }
    }

    /**
     * Used for loading numeric settings from TOML (when present) or applying
     * defaults.
     * Populates the in-memory numeric cache for max nodes and duration per
     * position.
     *
     * @param tomlOpt optional TOML document used as the source of values.
     */
    private static void loadNumericsOrDefaults(Optional<Toml> tomlOpt) {
        Long nodes = tomlOpt.map(t -> t.getLong(K_MAX_NODES)).orElse(null);
        Long durMs = tomlOpt.map(t -> t.getLong(K_MAX_DURATION)).orElse(null);
        Long engines = tomlOpt.map(t -> t.getLong(K_ENGINE_INSTANCES)).orElse(null);

        maxNodes = Math.max(1, nonNullOrDefaultLong(nodes, K_MAX_NODES, DEFAULT_MAX_NODES));
        maxDuration = Math.max(1, nonNullOrDefaultLong(durMs, K_MAX_DURATION, DEFAULT_MAX_DURATION));
        engineInstances = Math.max(1,
                (int) nonNullOrDefaultLong(engines, K_ENGINE_INSTANCES, DEFAULT_ENGINE_INSTANCES));

    }

    /**
     * Used for returning a non-null numeric configuration value, logging a warning
     * when falling back.
     *
     * @param val  value read from TOML, possibly null.
     * @param key  TOML key associated with the value for logging context.
     * @param dflt default value to use when {@code val} is null.
     * @return {@code val} when non-null; otherwise {@code dflt}.
     */
    private static long nonNullOrDefaultLong(Long val, String key, long dflt) {
        if (val == null) {
            LogService.warn("Config: missing '" + key + "'; using default (" + dflt + ").");
            return dflt;
        }
        return val;
    }

    /**
     * Used for loading string settings from TOML (when present) or applying
     * defaults.
     * Populates the in-memory string cache used by the parsing step.
     *
     * @param tomlOpt optional TOML document used as the source of values.
     */
    private static void loadStringsOrDefaults(Optional<Toml> tomlOpt) {
        // Read from TOML if present, else null to trigger defaults
        String q = tomlOpt.map(t -> t.getString(K_PUZZLE_QUALITY)).orElse(null);
        String w = tomlOpt.map(t -> t.getString(K_PUZZLE_WINNING)).orElse(null);
        String d = tomlOpt.map(t -> t.getString(K_PUZZLE_DRAWING)).orElse(null);
        String a = tomlOpt.map(t -> t.getString(K_PUZZLE_ACCELERATE)).orElse(null);
        String pp = tomlOpt.map(t -> t.getString(K_PROTOCOL_PATH)).orElse(null);
        String outDir = tomlOpt.map(t -> t.getString(K_OUTPUT)).orElse(null);

        puzzleQualityString = nonNullOrDefaultString(q, K_PUZZLE_QUALITY, DEFAULT_PUZZLE_QUALITY);
        puzzleWinningString = nonNullOrDefaultString(w, K_PUZZLE_WINNING, DEFAULT_PUZZLE_WINNING);
        puzzleDrawingString = nonNullOrDefaultString(d, K_PUZZLE_DRAWING, DEFAULT_PUZZLE_DRAWING);
        puzzleAccelerateString = nonNullOrDefaultString(a, K_PUZZLE_ACCELERATE, DEFAULT_PUZZLE_ACCELERATE);

        protocolPath = nonNullOrDefaultString(pp, K_PROTOCOL_PATH, DEFAULT_PROTOCOL_PATH);
        output = nonNullOrDefaultString(outDir, K_OUTPUT, DEFAULT_OUTPUT);

    }

    /**
     * Used for returning a non-null configuration value, logging a warning when
     * falling back.
     *
     * @param val  value read from TOML, possibly null.
     * @param key  TOML key associated with the value for logging context.
     * @param dflt default value to use when {@code val} is null.
     * @return {@code val} when non-null; otherwise {@code dflt}.
     */
    private static String nonNullOrDefaultString(String val, String key, String dflt) {
        if (val == null) {
            LogService.warn("Config: missing '" + key + "'; using default.");
            return dflt;
        }
        return val;
    }

    /**
     * Used for parsing all DSL strings into {@link Filter} objects or applying
     * fallbacks.
     * Populates the in-memory parsed structures used by the getters.
     */
    private static void parseFilterOrFallback() {
        puzzleQuality = parseOrDefault(K_PUZZLE_QUALITY, puzzleQualityString, DEFAULT_PUZZLE_QUALITY);
        puzzleWinning = parseOrDefault(K_PUZZLE_WINNING, puzzleWinningString, DEFAULT_PUZZLE_WINNING);
        puzzleDrawing = parseOrDefault(K_PUZZLE_DRAWING, puzzleDrawingString, DEFAULT_PUZZLE_DRAWING);
        puzzleAccelerate = parseOrDefault(K_PUZZLE_ACCELERATE, puzzleAccelerateString, DEFAULT_PUZZLE_ACCELERATE);

        puzzleVerify = buildPuzzleVerify(puzzleQuality, puzzleWinning, puzzleDrawing);
    }

    /**
     * Used for parsing a single Filter-DSL string with fallback to defaults and
     * last-resort empty AND node.
     *
     * @param key   TOML key name for logging context.
     * @param value primary DSL string to parse.
     * @param dflt  default DSL string to parse when {@code value} is invalid.
     * @return parsed {@link Filter} tree; never null.
     */
    private static Filter parseOrDefault(String key, String value, String dflt) {
        try {
            return FilterDSL.fromString(value);
        } catch (Exception e) {
            LogService.error(e, "Config: invalid Filter DSL in '" + key + "', falling back to default.",
                    "Provided: " + value, "Exception: " + e.getMessage());
            try {
                return FilterDSL.fromString(dflt);
            } catch (Exception e2) {
                // Last resort: empty AND node
                LogService.error(e2, "Config: failed to parse default for '" + key + "'. Returning empty AND.",
                        "Provided: " + dflt,
                        "Exception: " + e2.getMessage());
                return Filter.builder().gate(Filter.Gate.AND).build();
            }
        }
    }

    /**
     * Used for building a combined puzzle verification Filter from quality,
     * winning, and drawing filters.
     *
     * @param quality Filter for puzzle quality.
     * @param winning Filter for winning puzzles.
     * @param drawing Filter for drawing puzzles.
     * @return combined Filter for overall puzzle verification.
     */
    protected static Filter buildPuzzleVerify(Filter quality, Filter winning, Filter drawing) {
        return Filter.builder().addLeaf(quality)
                .addLeaf(Filter.builder().gate(Gate.OR).addLeaf(winning).addLeaf(drawing).build()).build();
    }

    public static void main(String[] args) {
        System.out.println("Protocol path: " + Config.getProtocolPath());
        System.out.println("Max nodes: " + Config.getMaxNodes());
        System.out.println("Max duration: " + Config.getMaxDuration());
        System.out.println("Puzzle quality: " + Config.getPuzzleQuality());
        System.out.println("Puzzle winning: " + Config.getPuzzleWinning());
        System.out.println("Puzzle drawing: " + Config.getPuzzleDrawing());
        System.out.println("Puzzle accelerate: " + Config.getPuzzleAccelerate());
        System.out.println("Output: " + Config.getOutput());
        System.out.println("Instances: " + Config.getEngineInstances());
        System.out.println(Encyclopedia.name(Setup.getStandardStartPosition()));
    }
}
