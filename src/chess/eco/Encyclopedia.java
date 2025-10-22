package chess.eco;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
     * Used for constructing an {@link Encyclopedia} from the TOML file at
     * {@code file}. The constructor is intentionally package-private; clients
     * should rely on {@link #of(Path)}.
     *
     * @param file the ECO book to load
     * @throws IOException if the file cannot be read or parsed
     */
    private Encyclopedia(Path file) throws IOException {
        // 1) Read file
        String tomlContent = Files.readString(file, StandardCharsets.UTF_8);

        // 2) Parse TOML ([[A00]] arrays)
        Toml toml = Toml.load(new StringReader(tomlContent));
        Map<String, List<Map<String, Object>>> arrays = getTableArrays(toml);

        // 3) Build nodes
        List<Entry> tmp = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : arrays.entrySet()) {
            String eco = entry.getKey();
            for (Map<String, Object> tbl : entry.getValue()) {
                String name = asString(tbl.get("name"));
                String movetext = asString(tbl.get("movetext"));
                if (name == null || movetext == null)
                    continue; // skip incomplete
                try {
                    tmp.add(new Entry(eco, name, movetext));
                } catch (IllegalArgumentException ex) {
                    LogService.warn(String.format("Invalid ECO '%s' – %s", eco, ex.getMessage()));
                }
            }
        }
        this.entries = tmp.toArray(Entry[]::new);

        Map<Position, Entry> map = new HashMap<>(entries.length * 2);
        for (Entry n : entries)
            map.put(n.position, n);
        this.byPosition = Collections.unmodifiableMap(map);
    }

    /**
     * Used for returning the opening name associated with {@code pos}.
     *
     * @param pos the position to query
     * @return the opening name or an empty string if unknown
     */
    public String getName(Position pos) {
        Entry n = byPosition.get(pos);
        return n == null ? "" : n.getName();
    }

    /**
     * Used for returning the ECO code associated with {@code pos}.
     *
     * @param pos the position to query
     * @return the ECO code or an empty string if unknown
     */
    public String getECO(Position pos) {
        Entry n = byPosition.get(pos);
        return n == null ? "" : n.getECO();
    }

    /**
     * Used for retrieving the full {@link Entry} for {@code pos}.
     *
     * @param pos the position to query
     * @return the matching {@link Entry} or {@code null} if none exists
     */
    public Entry getNode(Position pos) {
        return byPosition.get(pos);
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
