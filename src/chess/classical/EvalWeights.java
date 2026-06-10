package chess.classical;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reflective view over the tunable evaluation weights declared in {@link Wdl}.
 *
 * <p>
 * The classical evaluation's weights (material, piece-square tables, mobility
 * curves, king-attack weights, outpost/bishop-pair/tempo bonuses) are declared
 * as non-{@code final} {@code static int} / {@code int[]} fields in {@link Wdl};
 * this class enumerates exactly that set ("static, non-final, int or int[]") and
 * exposes it three ways:
 * </p>
 *
 * <ul>
 *   <li>as a flat integer parameter vector ({@link #readParameters()} /
 *       {@link #writeParameters(int[])}) for a tuner to optimise;</li>
 *   <li>as a human-readable {@code NAME = csv} text form
 *       ({@link #serialize()} / {@link #apply(String)}) for persistence;</li>
 *   <li>as a startup override loaded from the file named by the
 *       {@value #PROPERTY} system property ({@link #applyStartupOverrides()}),
 *       so a separately-tuned weight set can be measured against the baked-in
 *       defaults without recompiling.</li>
 * </ul>
 *
 * <p>
 * With no override the weights keep their declared defaults, so the evaluation
 * is byte-for-byte identical to the pre-tuning engine.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EvalWeights {

    /**
     * System property naming a weights file to load at startup.
     */
    public static final String PROPERTY = "crtk.evalweights";

    /**
     * Not instantiable.
     */
    private EvalWeights() {
    }

    /**
     * Cached tunable field list; the set never changes at runtime, so reflection
     * runs once instead of on every accessor call (a tuner hits these thousands
     * of times).
     */
    private static volatile List<Field> cachedFields;

    /**
     * Returns the tunable weight fields of {@link Wdl} — every static, non-final
     * {@code int} or {@code int[]} field — in a stable name order.
     *
     * @return accessible tunable fields, sorted by name
     */
    private static List<Field> tunableFields() {
        List<Field> cached = cachedFields;
        if (cached == null) {
            cached = computeTunableFields();
            cachedFields = cached;
        }
        return cached;
    }

    /**
     * Enumerates the tunable weight fields of {@link Wdl} by reflection.
     *
     * @return accessible tunable fields, sorted by name
     */
    private static List<Field> computeTunableFields() {
        List<Field> fields = new ArrayList<>();
        for (Field field : Wdl.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                continue;
            }
            Class<?> type = field.getType();
            if (type == int.class || type == int[].class) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
        fields.sort(Comparator.comparing(Field::getName));
        return fields;
    }

    /**
     * Reads an {@code int} field.
     *
     * @param field scalar field
     * @return its value
     */
    private static int getInt(Field field) {
        try {
            return field.getInt(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("cannot read " + field.getName(), ex);
        }
    }

    /**
     * Reads an {@code int[]} field.
     *
     * @param field array field
     * @return its array reference
     */
    private static int[] getArray(Field field) {
        try {
            return (int[]) field.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("cannot read " + field.getName(), ex);
        }
    }

    /**
     * Writes an {@code int} field.
     *
     * @param field scalar field
     * @param value new value
     */
    private static void setInt(Field field, int value) {
        try {
            field.setInt(null, value);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("cannot write " + field.getName(), ex);
        }
    }

    /**
     * One tunable scalar parameter: a scalar field, or one element of an array
     * field. Array references are captured directly (arrays are mutated in place,
     * never replaced), so {@link #get()}/{@link #set(int)} stay off the
     * reflection path that a tuner would otherwise hit millions of times.
     */
    private static final class ParamRef {

        /**
         * Scalar field, or {@code null} for an array element.
         */
        private final Field scalar;

        /**
         * Backing array, or {@code null} for a scalar field.
         */
        private final int[] array;

        /**
         * Index into {@link #array}, or {@code -1} for a scalar.
         */
        private final int element;

        /**
         * Display name ({@code NAME} or {@code NAME[i]}).
         */
        private final String name;

        private ParamRef(Field scalar, int[] array, int element, String name) {
            this.scalar = scalar;
            this.array = array;
            this.element = element;
            this.name = name;
        }

        private int get() {
            return scalar != null ? getInt(scalar) : array[element];
        }

        private void set(int value) {
            if (scalar != null) {
                setInt(scalar, value);
            } else {
                array[element] = value;
            }
        }
    }

    /**
     * Cached flat parameter index, built once.
     */
    private static volatile ParamRef[] cachedParams;

    /**
     * Returns the flat parameter index — one entry per tunable scalar and one per
     * tunable array element, in {@link #tunableFields()} order.
     *
     * @return parameter references
     */
    private static ParamRef[] params() {
        ParamRef[] cached = cachedParams;
        if (cached == null) {
            List<ParamRef> refs = new ArrayList<>();
            for (Field field : tunableFields()) {
                if (field.getType() == int.class) {
                    refs.add(new ParamRef(field, null, -1, field.getName()));
                } else {
                    int[] array = getArray(field);
                    for (int i = 0; i < array.length; i++) {
                        refs.add(new ParamRef(null, array, i, field.getName() + "[" + i + "]"));
                    }
                }
            }
            cached = refs.toArray(new ParamRef[0]);
            cachedParams = cached;
        }
        return cached;
    }

    /**
     * Returns the number of tunable scalar parameters.
     *
     * @return parameter count
     */
    public static int parameterCount() {
        return params().length;
    }

    /**
     * Reads one parameter by flat index.
     *
     * @param index parameter index
     * @return its value
     */
    public static int getParameter(int index) {
        return params()[index].get();
    }

    /**
     * Writes one parameter by flat index.
     *
     * @param index parameter index
     * @param value new value
     */
    public static void setParameter(int index, int value) {
        params()[index].set(value);
    }

    /**
     * Returns the names of the flat parameter vector, one entry per tunable
     * scalar and one per tunable array element ({@code NAME} or {@code NAME[i]}).
     *
     * @return parameter names in vector order
     */
    public static List<String> parameterNames() {
        List<String> names = new ArrayList<>();
        for (ParamRef ref : params()) {
            names.add(ref.name);
        }
        return names;
    }

    /**
     * Reads the current weights as a flat integer parameter vector, in the order
     * of {@link #parameterNames()}.
     *
     * @return parameter values
     */
    public static int[] readParameters() {
        ParamRef[] refs = params();
        int[] out = new int[refs.length];
        for (int i = 0; i < refs.length; i++) {
            out[i] = refs[i].get();
        }
        return out;
    }

    /**
     * Writes a flat integer parameter vector back into the weights, in the order
     * of {@link #parameterNames()}. Array fields are updated element-wise in
     * place so live references in the evaluation see the new values.
     *
     * @param values parameter values; must match the parameter count
     * @throws IllegalArgumentException if the length is wrong
     */
    public static void writeParameters(int[] values) {
        ParamRef[] refs = params();
        if (values.length != refs.length) {
            throw new IllegalArgumentException("expected " + refs.length + " parameters, got " + values.length);
        }
        for (int i = 0; i < refs.length; i++) {
            refs[i].set(values[i]);
        }
    }

    /**
     * Serialises every tunable weight to a {@code NAME = csv} text form, one
     * field per line, sorted by name.
     *
     * @return serialised weights
     */
    public static String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Field field : tunableFields()) {
            sb.append(field.getName()).append(" = ");
            if (field.getType() == int.class) {
                sb.append(getInt(field));
            } else {
                int[] array = getArray(field);
                for (int i = 0; i < array.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(array[i]);
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Applies a {@code NAME = csv} text form produced by {@link #serialize()}.
     * Unknown names are ignored (forward compatibility); a value-count mismatch
     * on an array field is an error. Blank lines and {@code #} comments are
     * skipped.
     *
     * @param text serialised weights
     */
    public static void apply(String text) {
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String name = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            Field field = fieldByName(name);
            if (field == null) {
                continue;
            }
            applyField(field, value);
        }
    }

    /**
     * Applies one parsed value to one field.
     *
     * @param field target field
     * @param value comma-separated value text
     */
    private static void applyField(Field field, String value) {
        if (field.getType() == int.class) {
            setInt(field, Integer.parseInt(value.trim()));
            return;
        }
        String[] parts = value.split(",");
        int[] array = getArray(field);
        if (parts.length != array.length) {
            throw new IllegalArgumentException(field.getName() + " expects " + array.length
                    + " values, got " + parts.length);
        }
        for (int i = 0; i < array.length; i++) {
            array[i] = Integer.parseInt(parts[i].trim());
        }
    }

    /**
     * Finds a tunable field by name.
     *
     * @param name field name
     * @return the field, or {@code null} if not a tunable weight
     */
    private static Field fieldByName(String name) {
        for (Field field : tunableFields()) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Loads weights from a file in {@link #serialize()} form.
     *
     * @param file weights file
     * @throws IOException if the file cannot be read
     */
    public static void load(Path file) throws IOException {
        apply(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * Saves the current weights to a file in {@link #serialize()} form.
     *
     * @param file weights file
     * @throws IOException if the file cannot be written
     */
    public static void save(Path file) throws IOException {
        Files.writeString(file, serialize(), StandardCharsets.UTF_8);
    }

    /**
     * Applies the weights file named by the {@value #PROPERTY} system property,
     * if set. A read or parse failure is fatal — an engine silently running with
     * the wrong weights would corrupt a measurement.
     */
    public static void applyStartupOverrides() {
        String path = System.getProperty(PROPERTY);
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            load(Path.of(path.trim()));
        } catch (IOException ex) {
            throw new UncheckedIOException("cannot load eval weights from " + path, ex);
        }
    }
}
