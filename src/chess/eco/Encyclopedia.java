package chess.eco;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

import chess.core.Position;
import chess.debug.LogService;
import utility.Toml;

/**
 * Used for mapping {@link Position chess positions} to Encyclopaedia of Chess
 * Openings (ECO) codes, names, and metadata loaded from a
 * <code>.eco.toml</code>
 * file.
 * <p>
 *
 * The class keeps one in-memory {@link Encyclopedia} instance per unique file
 * path and provides convenient static helpers to query the default book.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Encyclopedia {

     /**
     * Shared parallel parse threshold constant.
     */
     private static final int PARALLEL_PARSE_THRESHOLD = 512;

     /**
     * Shared prop parallel parse constant.
     */
     private static final String PROP_PARALLEL_PARSE = "crtk.eco.parallel";

     /**
     * Shared env parallel parse constant.
     */
     private static final String ENV_PARALLEL_PARSE = "CRTK_ECO_PARALLEL";

     /**
     * Shared key eco constant.
     */
     private static final String KEY_ECO = "eco";

     /**
     * Shared key name constant.
     */
     private static final String KEY_NAME = "name";

     /**
     * Shared key movetext constant.
     */
     private static final String KEY_MOVETEXT = "movetext";

    /**
     * Used for pointing to the default ECO book read by
     * {@link #defaultBook()}. The file is expected to reside in the working
     * directory.
     */
    private static final Path DEFAULT_PATH = Path.of("config/book.eco.toml");

    /**
     * Used for caching already-loaded ECO books so that each file is parsed at
     * most once per JVM.
     */
    private static final ConcurrentMap<Path, Encyclopedia> CACHE = new ConcurrentHashMap<>();

    /**
     * Used for obtaining the singleton instance backed by
     * {@value #DEFAULT_PATH}. The file is loaded on first access and then served
     * from the cache.
     *
     * @return the lazily initialised default {@link Encyclopedia}
     */
    public static Encyclopedia defaultBook() {
        return of(DEFAULT_PATH);
    }

    /**
     * Used for retrieving (or loading) the ECO book located at {@code path}. A
     * previously parsed instance is returned from the internal cache; otherwise
     * the file is read and parsed.
     *
     * @param path absolute or relative path to a <code>.eco.toml</code> file
     * @return the cached or newly created {@link Encyclopedia}
     * @throws IllegalStateException if the file cannot be read or parsed
     */
    public static Encyclopedia of(Path path) {
        Objects.requireNonNull(path, "path");
        return CACHE.computeIfAbsent(path.toAbsolutePath().normalize(), p -> {
            try {
                Encyclopedia t = new Encyclopedia(p);
                LogService.info(String.format(
                        "Loaded ECO book (%s) with %,d entries",
                        LogService.pathAbs(p), t.entries.length));
                return t;
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to load ECO book (" + LogService.pathAbs(p) + ")", ex);
            }
        });
    }

    /**
     * Used for storing all parsed ECO entries in book order. The array is
     * immutable after construction.
     */
    private final Entry[] entries;

    /**
     * Used for fast lookup from {@link Position} to {@link Entry}. The map is
     * built once during construction and exposed as an unmodifiable view.
     */
    private final Map<Position, Entry> byPosition;

    /**
     * Used for fast lookup from a "core" position signature (ignoring move counters)
     * to {@link Entry}. This makes ECO mapping resilient to FENs that omit or vary
     * half-move/full-move counters.
     */
    private final Map<Long, Entry> byCoreSignature;

    /**
     * Holds the raw ECO data extracted from a TOML table array before parsing it into an {@link Entry}.
     *
     * <p>
     * Each row stores the ECO code, the descriptive name, and the SAN movetext.
     * </p>
     */
    private record Row(    String eco,     String name,     String movetext) {
         /**
         * Creates a new row instance.
         * @param eco eco
         * @param name name
         * @param movetext movetext
         */
         private Row {
            Objects.requireNonNull(eco, KEY_ECO);
            Objects.requireNonNull(name, KEY_NAME);
            Objects.requireNonNull(movetext, KEY_MOVETEXT);
        }
    }

    /**
     * Result returned when attempting to parse a {@link Row}.
     *
     * <p>
     * Exactly one of {@link #entry()} and {@link #error()} should be non-null to indicate success or failure.
     * </p>
     */
    private record RowParseResult(    Entry entry,     String error) {
    }

    /**
     * Used for constructing an {@link Encyclopedia} from the TOML file at
     * {@code file}. The constructor is intentionally package-private; clients
     * should rely on {@link #of(Path)}.
     *
     * @param file the ECO book to load
     * @throws IOException if the file cannot be read or parsed
     */
    private Encyclopedia(Path file) throws IOException {
        String tomlContent = readToml(file);
        Map<String, List<Map<String, Object>>> arrays = parseTomlArrays(tomlContent);
        this.entries = loadEntries(arrays);
        this.byPosition = buildByPosition(entries);
        this.byCoreSignature = buildByCoreSignature(entries);
    }

    /**
     * Used for returning the opening name associated with {@code pos}.
     *
     * @param pos the position to query
     * @return the opening name or an empty string if unknown
     */
    public String getName(Position pos) {
        Entry n = getNode(pos);
        return n == null ? "" : n.getName();
    }

    /**
     * Used for returning the ECO code associated with {@code pos}.
     *
     * @param pos the position to query
     * @return the ECO code or an empty string if unknown
     */
    public String getECO(Position pos) {
        Entry n = getNode(pos);
        return n == null ? "" : n.getECO();
    }

    /**
     * Used for retrieving the full {@link Entry} for {@code pos}.
     *
     * @param pos the position to query
     * @return the matching {@link Entry} or {@code null} if none exists
     */
    public Entry getNode(Position pos) {
        Entry direct = byPosition.get(pos);
        if (direct != null) {
            return direct;
        }
        return byCoreSignature.get(pos.signatureCore());
    }

    /**
     * Used for quickly obtaining an opening name from the default ECO book.
     *
     * @see #getName(Position)
     */
    public static String name(Position pos) {
        return defaultBook().getName(pos);
    }

    /**
     * Used for quickly obtaining an ECO code from the default ECO book.
     *
     * @see #getECO(Position)
     */
    public static String eco(Position pos) {
        return defaultBook().getECO(pos);
    }

    /**
     * Used for quickly obtaining a full {@link Entry} from the default ECO book.
     *
     * @see #getNode(Position)
     */
    public static Entry node(Position pos) {
        return defaultBook().getNode(pos);
    }

    /**
     * Returns an immutable view of all parsed ECO entries in book order.
     *
     * @return list of {@link Entry} items
     */
    public List<Entry> entries() {
        return Collections.unmodifiableList(Arrays.asList(entries));
    }

    /**
     * Used for safely converting {@code o} to a string, returning
     * {@code null} when {@code o} is {@code null}.
     *
     * @param o an object that may be {@code null}
     * @return {@code o.toString()} or {@code null}
     */
    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

     /**
     * Reads the toml.
     * @param file file
     * @return computed value
     * @throws IOException if the operation fails
     */
     private static String readToml(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

     /**
     * Parses the toml arrays.
     * @param tomlContent toml content
     * @return computed value
     * @throws IOException if the operation fails
     */
     private static Map<String, List<Map<String, Object>>> parseTomlArrays(String tomlContent) throws IOException {
        Toml toml = Toml.load(new StringReader(tomlContent));
        return getTableArrays(toml);
    }

     /**
     * Handles load entries.
     * @param arrays arrays
     * @return computed value
     */
     private static Entry[] loadEntries(Map<String, List<Map<String, Object>>> arrays) {
        ArrayList<Entry> tmp = new ArrayList<>();
        if (shouldParallelParse()) {
            List<Row> rows = collectRows(arrays);
            RowParseResult[] parsed = parseRows(rows);
            appendParsedRows(rows, parsed, tmp);
        } else {
            parseSequential(arrays, tmp);
        }
        return tmp.toArray(Entry[]::new);
    }

     /**
     * Handles should parallel parse.
     * @return computed value
     */
     private static boolean shouldParallelParse() {
        return parallelParseEnabled() && Runtime.getRuntime().availableProcessors() > 1;
    }

     /**
     * Handles collect rows.
     * @param arrays arrays
     * @return computed value
     */
     private static List<Row> collectRows(Map<String, List<Map<String, Object>>> arrays) {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : arrays.entrySet()) {
            String eco = entry.getKey();
            for (Map<String, Object> tbl : entry.getValue()) {
                String name = asString(tbl.get(KEY_NAME));
                String movetext = asString(tbl.get(KEY_MOVETEXT));
                if (name != null && movetext != null) {
                    rows.add(new Row(eco, name, movetext));
                }
            }
        }
        return rows;
    }

     /**
     * Parses the rows.
     * @param rows rows
     * @return computed value
     */
     private static RowParseResult[] parseRows(List<Row> rows) {
        RowParseResult[] parsed = new RowParseResult[rows.size()];
        if (rows.size() >= PARALLEL_PARSE_THRESHOLD) {
            IntStream.range(0, rows.size()).parallel().forEach(i -> parsed[i] = parseRow(rows.get(i)));
        } else {
            for (int i = 0; i < rows.size(); i++) {
                parsed[i] = parseRow(rows.get(i));
            }
        }
        return parsed;
    }

     /**
     * Handles append parsed rows.
     * @param rows rows
     * @param parsed parsed
     * @param tmp tmp
     */
     private static void appendParsedRows(List<Row> rows, RowParseResult[] parsed, ArrayList<Entry> tmp) {
        tmp.ensureCapacity(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            RowParseResult result = parsed[i];
            if (result != null) {
                Entry entry = result.entry();
                if (entry != null) {
                    tmp.add(entry);
                } else if (result.error() != null) {
                    Row row = rows.get(i);
                    LogService.warn(String.format("Invalid ECO '%s' – %s", row.eco(), result.error()));
                }
            }
        }
    }

     /**
     * Parses the sequential.
     * @param arrays arrays
     * @param tmp tmp
     */
     private static void parseSequential(Map<String, List<Map<String, Object>>> arrays, ArrayList<Entry> tmp) {
        for (Map.Entry<String, List<Map<String, Object>>> entry : arrays.entrySet()) {
            String eco = entry.getKey();
            for (Map<String, Object> tbl : entry.getValue()) {
                String name = asString(tbl.get(KEY_NAME));
                String movetext = asString(tbl.get(KEY_MOVETEXT));
                if (name == null || movetext == null) {
                    continue;
                }
                try {
                    tmp.add(new Entry(eco, name, movetext));
                } catch (IllegalArgumentException ex) {
                    LogService.warn(String.format("Invalid ECO '%s' – %s", eco, ex.getMessage()));
                }
            }
        }
    }

     /**
     * Handles build by position.
     * @param entries entries
     * @return computed value
     */
     private static Map<Position, Entry> buildByPosition(Entry[] entries) {
        Map<Position, Entry> map = new HashMap<>(entries.length * 2);
        for (Entry n : entries) {
            map.put(n.position, n);
        }
        return Collections.unmodifiableMap(map);
    }

     /**
     * Handles build by core signature.
     * @param entries entries
     * @return computed value
     */
     private static Map<Long, Entry> buildByCoreSignature(Entry[] entries) {
        Map<Long, Entry> coreMap = new HashMap<>(entries.length * 2);
        for (Entry n : entries) {
            long sig = n.position.signatureCore();
            Entry existing = coreMap.computeIfAbsent(sig, ignored -> n);
            if (existing == n) {
                continue;
            }

            int existingMoves = existing.moves == null ? 0 : existing.moves.length;
            int candidateMoves = n.moves == null ? 0 : n.moves.length;
            if (candidateMoves > existingMoves) {
                coreMap.put(sig, n);
            }
        }
        return Collections.unmodifiableMap(coreMap);
    }

     /**
     * Parses the row.
     * @param row row
     * @return computed value
     */
     private static RowParseResult parseRow(Row row) {
        try {
            return new RowParseResult(new Entry(row.eco(), row.name(), row.movetext()), null);
        } catch (IllegalArgumentException ex) {
            return new RowParseResult(null, ex.getMessage());
        }
    }

     /**
     * Handles parallel parse enabled.
     * @return computed value
     */
     private static boolean parallelParseEnabled() {
        String prop = System.getProperty(PROP_PARALLEL_PARSE);
        if (prop != null) {
            return Boolean.parseBoolean(prop);
        }
        String env = System.getenv(ENV_PARALLEL_PARSE);
        if (env == null) {
            return false;
        }
        String value = env.trim();
        if (value.isEmpty()) {
            return false;
        }
        return !(value.equals("0") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no"));
    }

    /**
     * Used for extracting the private <em>tableArrays</em> field from the Toml
     * parser via reflection. This is necessary because the parser does not
     * expose table-array data publicly.
     *
     * @param toml the parsed {@link Toml} object
     * @return a map of table-array names to their row lists
     * @throws IOException if reflection fails
     */
    @SuppressWarnings({ "unchecked", "java:S3011" })
    private static Map<String, List<Map<String, Object>>> getTableArrays(Toml toml) throws IOException {
        try {
            Field f = Toml.class.getDeclaredField("tableArrays");
            f.setAccessible(true);
            return (Map<String, List<Map<String, Object>>>) f.get(toml);
        } catch (ReflectiveOperationException ex) {
            throw new IOException("Cannot access TOML table arrays", ex);
        }
    }

}
