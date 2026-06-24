package application.gui.workbench.session;

import static application.cli.Constants.OPT_COVER_OUTPUT;
import static application.cli.Constants.OPT_DEPTH;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_HASH;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_ITERATIONS;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_MULTIPV;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_DIR;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PDF;
import static application.cli.Constants.OPT_PDF_OUTPUT;
import static application.cli.Constants.OPT_PGN;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_SUITE;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_WEIGHTS;

import application.cli.PathOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import utility.Json;

/**
 * Persists replayable JSON metadata for one workbench command run.
 */
public final class RunManifest {

    /**
     * Manifest schema identifier.
     */
    public static final String SCHEMA = "crtk.workbench.run-manifest.v1";

    /**
     * Directory for persisted workbench run manifests.
     */
    public static final Path DEFAULT_DIR = PathOps.dumpPath("workbench-runs");

    /**
     * Command options whose following token names an input/config file.
     */
    private static final List<String> INPUT_PATH_FLAGS = List.of(
            OPT_INPUT, OPT_INPUT_SHORT, OPT_PGN, OPT_SUITE, OPT_PROTOCOL_PATH, OPT_WEIGHTS, OPT_PDF);

    /**
     * Command options whose following token names an output path.
     */
    private static final List<String> OUTPUT_PATH_FLAGS = List.of(
            OPT_OUTPUT, OPT_OUTPUT_SHORT, OPT_OUTPUT_DIR, OPT_PDF_OUTPUT, OPT_COVER_OUTPUT);

    /**
     * Command options worth lifting into a compact "limits" object.
     */
    private static final List<String> LIMIT_FLAGS = List.of(
            OPT_DEPTH, OPT_NODES, OPT_MAX_NODES, OPT_MAX_DURATION, "--duration",
            OPT_THREADS, OPT_HASH, OPT_MULTIPV, OPT_ITERATIONS);

    /**
     * Command options worth lifting into a compact "engine" object.
     */
    private static final List<String> ENGINE_FLAGS = List.of(
            OPT_PROTOCOL_PATH, OPT_THREADS, OPT_HASH);

    /**
     * Maximum captured-output preview stored in the manifest.
     */
    private static final int OUTPUT_PREVIEW_LIMIT = 16_384;

    /**
     * Prevents instantiation.
     */
    private RunManifest() {
        // utility
    }

    /**
     * Writes a manifest into the default session directory.
     *
     * @param job finished or cancelled job
     * @param artifacts detected output artifacts
     * @param stdin optional stdin payload
     * @param workingDirectory process working directory
     * @return manifest path
     * @throws IOException when writing fails
     */
    public static Path write(Job job, List<Path> artifacts, String stdin,
            Path workingDirectory) throws IOException {
        return write(DEFAULT_DIR, job, artifacts, stdin, workingDirectory);
    }

    /**
     * Writes a manifest into a supplied directory.
     *
     * @param directory manifest directory
     * @param job finished or cancelled job
     * @param artifacts detected output artifacts
     * @param stdin optional stdin payload
     * @param workingDirectory process working directory
     * @return manifest path
     * @throws IOException when writing fails
     */
    public static Path write(Path directory, Job job, List<Path> artifacts, String stdin,
            Path workingDirectory) throws IOException {
        if (job == null) {
            throw new IllegalArgumentException("job is required");
        }
        Path dir = directory == null ? DEFAULT_DIR : directory;
        return SessionFiles.writeString(dir, fileName(job),
                toJson(job, artifacts, stdin, workingDirectory));
    }

    /**
     * Builds a stable manifest filename.
     *
     * @param job source job
     * @return filename
     */
    private static String fileName(Job job) {
        String status = job.status().name().toLowerCase(Locale.ROOT);
        return String.format(Locale.ROOT, "run-%05d-%s.json", job.id(), status);
    }

    /**
     * Serializes one manifest.
     *
     * @param job source job
     * @param artifacts detected output artifacts
     * @param stdin optional stdin payload
     * @param workingDirectory process working directory
     * @return JSON text
     */
    private static String toJson(Job job, List<Path> artifacts, String stdin,
            Path workingDirectory) {
        List<Path> outputPaths = artifacts == null ? List.of() : List.copyOf(artifacts);
        Map<String, String> limits = optionMap(job.args(), LIMIT_FLAGS);
        Map<String, String> engine = optionMap(job.args(), ENGINE_FLAGS);
        String fen = optionValue(job.args(), OPT_FEN);

        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        field(sb, "schema", SCHEMA, true);
        numericField(sb, "jobId", Long.toString(job.id()), true);
        field(sb, "createdAt", iso(job.createdAtMillis()), true);
        field(sb, "completedAt", iso(System.currentTimeMillis()), true);
        field(sb, "status", job.status().name().toLowerCase(Locale.ROOT), true);
        if (job.hasExitCode()) {
            numericField(sb, "exitCode", Integer.toString(job.exitCode()), true);
        } else {
            rawField(sb, "exitCode", "null", true);
        }
        numericField(sb, "durationMillis", Long.toString(job.durationMillis()), true);
        field(sb, "command", job.displayCommand(), true);
        rawField(sb, "args", Json.stringArray(job.args().toArray(String[]::new)), true);
        field(sb, "workingDirectory", normalize(workingDirectory), true);
        rawField(sb, "stdin", stdinObject(stdin), true);
        if (fen != null) {
            field(sb, "positionFen", fen, true);
        }
        rawField(sb, "limits", stringMapObject(limits), true);
        rawField(sb, "engine", stringMapObject(engine), true);
        rawField(sb, "inputs", pathEntries(job.args(), INPUT_PATH_FLAGS), true);
        rawField(sb, "outputs", outputPathEntries(job.args(), outputPaths), true);
        if (job.logPath() != null) {
            field(sb, "logPath", normalize(job.logPath()), true);
        }
        field(sb, "summary", job.resultSummary(), true);
        numericField(sb, "outputBytes", Integer.toString(bytes(job.output())), true);
        field(sb, "outputPreview", outputPreview(job.output()), false);
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Returns the first value for an option flag.
     *
     * @param args command arguments
     * @param flag option flag
     * @return option value or null
     */
    private static String optionValue(List<String> args, String flag) {
        if (args == null || flag == null) {
            return null;
        }
        for (int i = 0; i + 1 < args.size(); i++) {
            if (flag.equals(args.get(i))) {
                return args.get(i + 1);
            }
        }
        return null;
    }

    /**
     * Builds an ordered option map for selected flags.
     *
     * @param args command arguments
     * @param flags flags to capture
     * @return ordered flag-value map
     */
    private static Map<String, String> optionMap(List<String> args, List<String> flags) {
        Map<String, String> out = new LinkedHashMap<>();
        if (args == null) {
            return out;
        }
        for (int i = 0; i + 1 < args.size(); i++) {
            String flag = args.get(i);
            if (flags.contains(flag)) {
                out.put(flag, args.get(i + 1));
                i++;
            }
        }
        return out;
    }

    /**
     * Serializes input path entries discovered after selected option flags.
     *
     * @param args command arguments
     * @param flags path-valued flags
     * @return JSON array
     */
    private static String pathEntries(List<String> args, List<String> flags) {
        List<PathEntry> entries = new ArrayList<>();
        if (args != null) {
            for (int i = 0; i + 1 < args.size(); i++) {
                String flag = args.get(i);
                if (!flags.contains(flag)) {
                    continue;
                }
                Path path = parsePath(args.get(i + 1));
                if (path != null) {
                    entries.add(new PathEntry(flag, path));
                }
                i++;
            }
        }
    return serializePathEntries(entries);
    }

    /**
     * Serializes declared output paths plus detected artifact paths.
     *
     * @param args command arguments
     * @param artifacts detected output artifacts
     * @return JSON array
     */
    private static String outputPathEntries(List<String> args, List<Path> artifacts) {
        List<PathEntry> entries = new ArrayList<>();
        if (args != null) {
            for (int i = 0; i + 1 < args.size(); i++) {
                String flag = args.get(i);
                if (!OUTPUT_PATH_FLAGS.contains(flag)) {
                    continue;
                }
                Path path = parsePath(args.get(i + 1));
                if (path != null) {
                    entries.add(new PathEntry(flag, path));
                }
                i++;
            }
        }
        for (Path artifact : artifacts) {
            if (artifact != null && entries.stream().noneMatch(entry -> samePath(entry.path(), artifact))) {
                entries.add(new PathEntry("artifact", artifact));
            }
        }
    return serializePathEntries(entries);
    }

    /**
     * Serializes path entries.
     *
     * @param entries path entries
     * @return JSON array
     */
    private static String serializePathEntries(List<PathEntry> entries) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendPathEntry(sb, entries.get(i));
        }
        return sb.append(']').toString();
    }

    /**
     * Appends one path metadata object.
     *
     * @param sb destination
     * @param entry path entry
     */
    private static void appendPathEntry(StringBuilder sb, PathEntry entry) {
        Path path = entry.path().toAbsolutePath().normalize();
        boolean exists = Files.exists(path);
        sb.append('{');
        inlineField(sb, "kind", entry.kind(), true);
        inlineField(sb, "path", path.toString(), true);
        inlineRawField(sb, "exists", Boolean.toString(exists), true);
        if (exists && Files.isRegularFile(path)) {
            try {
                inlineRawField(sb, "bytes", Long.toString(Files.size(path)), true);
                inlineField(sb, "sha256", sha256(path), false);
            } catch (IOException ex) {
                inlineRawField(sb, "bytes", "null", true);
                inlineField(sb, "sha256", "", false);
            }
        } else {
            inlineRawField(sb, "bytes", "null", true);
            inlineField(sb, "sha256", "", false);
        }
        sb.append('}');
    }

    /**
     * Returns a JSON object for stdin metadata.
     *
     * @param stdin stdin payload
     * @return JSON object
     */
    private static String stdinObject(String stdin) {
        if (stdin == null || stdin.isEmpty()) {
            return "{\"present\":false,\"bytes\":0,\"sha256\":\"\"}";
        }
        byte[] bytes = stdin.getBytes(StandardCharsets.UTF_8);
        return "{\"present\":true,\"bytes\":" + bytes.length
                + ",\"sha256\":\"" + sha256(bytes) + "\"}";
    }

    /**
     * Serializes a string map as a JSON object.
     *
     * @param map values
     * @return JSON object
     */
    private static String stringMapObject(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (i++ > 0) {
                sb.append(',');
            }
            inlineField(sb, entry.getKey(), entry.getValue(), false);
        }
        return sb.append('}').toString();
    }

    /**
     * Parses a path token without throwing.
     *
     * @param token path token
     * @return path or null
     */
    private static Path parsePath(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Path.of(token);
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    /**
     * Compares two paths after absolute normalization.
     *
     * @param a first path
     * @param b second path
     * @return true when equivalent
     */
    private static boolean samePath(Path a, Path b) {
        if (a == null || b == null) {
            return false;
        }
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    /**
     * Returns a normalized path string.
     *
     * @param path file-system path
     * @return absolute path string
     */
    private static String normalize(Path path) {
        Path value = path == null ? Path.of("") : path;
        return value.toAbsolutePath().normalize().toString();
    }

    /**
     * Formats an epoch millis timestamp as ISO-8601.
     *
     * @param millis epoch millis
     * @return ISO timestamp
     */
    private static String iso(long millis) {
        return Instant.ofEpochMilli(millis).toString();
    }

    /**
     * Returns UTF-8 byte count.
     *
     * @param text text to render or parse
     * @return byte count
     */
    private static int bytes(String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Returns a bounded output preview.
     *
     * @param output command output
     * @return preview
     */
    private static String outputPreview(String output) {
        if (output == null || output.length() <= OUTPUT_PREVIEW_LIMIT) {
            return output == null ? "" : output;
        }
        return output.substring(0, OUTPUT_PREVIEW_LIMIT)
                + System.lineSeparator() + "... (preview truncated) ...";
    }

    /**
     * Computes a file SHA-256 hash.
     *
     * @param path file path
     * @return lowercase hex digest
     * @throws IOException on read failure
     */
    private static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(path)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Computes a byte-array SHA-256 hash.
     *
     * @param data bytes
     * @return lowercase hex digest
     */
    private static String sha256(byte[] data) {
        return HexFormat.of().formatHex(sha256Digest().digest(data));
    }

    /**
     * Creates a SHA-256 digest.
     *
     * @return digest
     */
    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
    throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * Appends one indented string field.
     *
     * @param sb destination
     * @param name field name
     * @param value field value
     * @param comma whether to append a trailing comma
     */
    private static void field(StringBuilder sb, String name, String value, boolean comma) {
        rawField(sb, name, quote(value), comma);
    }

    /**
     * Appends one indented numeric field.
     *
     * @param sb destination
     * @param name field name
     * @param value numeric value text
     * @param comma whether to append a trailing comma
     */
    private static void numericField(StringBuilder sb, String name, String value, boolean comma) {
        rawField(sb, name, value, comma);
    }

    /**
     * Appends one indented raw JSON field.
     *
     * @param sb destination
     * @param name field name
     * @param rawJson raw JSON text
     * @param comma whether to append a trailing comma
     */
    private static void rawField(StringBuilder sb, String name, String rawJson, boolean comma) {
        sb.append("  ").append(quote(name)).append(": ").append(rawJson == null ? "null" : rawJson);
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    /**
     * Appends one inline string field.
     *
     * @param sb destination
     * @param name field name
     * @param value field value
     * @param comma whether to append comma
     */
    private static void inlineField(StringBuilder sb, String name, String value, boolean comma) {
        inlineRawField(sb, name, quote(value), comma);
    }

    /**
     * Appends one inline raw JSON field.
     *
     * @param sb destination
     * @param name field name
     * @param rawJson raw JSON text
     * @param comma whether to append comma
     */
    private static void inlineRawField(StringBuilder sb, String name, String rawJson, boolean comma) {
        sb.append(quote(name)).append(':').append(rawJson == null ? "null" : rawJson);
        if (comma) {
            sb.append(',');
        }
    }

    /**
     * Quotes one JSON string.
     *
     * @param value candidate value
     * @return quoted JSON string
     */
    private static String quote(String value) {
        return '"' + Json.esc(value == null ? "" : value) + '"';
    }

    /**
     * Path entry metadata.
     *
     * @param kind path kind or source flag
     * @param path file-system path
     */
    private record PathEntry(String kind, Path path) {
    }
}
