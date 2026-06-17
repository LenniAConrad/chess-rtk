package chess.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import utility.Json;

/**
 * Builder and writer for {@code crtk.dataset.manifest.v1}, the uniform
 * provenance sidecar every CRTK dataset exporter ships alongside its
 * artifacts.
 *
 * <p>The manifest captures four classes of information:</p>
 * <ul>
 *   <li><b>identity</b> — schema stamp, exporter name, CRTK version, git commit;</li>
 *   <li><b>argv</b> — verbatim argument tokens passed to {@code application.Main};</li>
 *   <li><b>artifacts</b> — every input, output, and (optional) weights file with its
 *       SHA-256 hex digest (paths are stored as basenames so the manifest is
 *       byte-stable across machine moves);</li>
 *   <li><b>structure</b> — the JSON shape is pinned by
 *       {@code schemas/crtk.dataset.manifest.v1.schema.json} and exercised by
 *       {@code SchemaAgreementRegressionTest} / {@code DatasetManifestRegressionTest}.</li>
 * </ul>
 *
 * <p>Hashing reuses {@link Provenance#sha256(Path)}; JSON escaping reuses
 * {@link utility.Json#esc(String)}. No third-party dependencies are
 * introduced.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetManifest {

	/**
	 * Stable schema identifier stamped into every emitted manifest.
	 */
	public static final String SCHEMA_VERSION = "crtk.dataset.manifest.v1";

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetManifest() {
		// utility
	}

	/**
	 * Creates a fresh manifest builder.
	 *
	 * @return empty builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns whether an artifact name is the portable basename form emitted by
	 * CRTK manifests.
	 *
	 * @param name candidate artifact name from a manifest entry
	 * @return {@code true} when the name is a non-empty basename
	 */
	public static boolean isPortableArtifactName(String name) {
		if (name == null
				|| name.isBlank()
				|| ".".equals(name)
				|| "..".equals(name)
				|| name.indexOf('/') >= 0
				|| name.indexOf('\\') >= 0) {
			return false;
		}
		for (int i = 0; i < name.length(); i++) {
			if (Character.isISOControl(name.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns whether a string is the lowercase SHA-256 hex form emitted by CRTK.
	 *
	 * @param digest candidate digest string
	 * @return {@code true} for exactly 64 lowercase hex characters
	 */
	public static boolean isSha256Hex(String digest) {
		if (digest == null || digest.length() != 64) {
			return false;
		}
		for (int i = 0; i < digest.length(); i++) {
			char c = digest.charAt(i);
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Fluent builder accumulating manifest content prior to write.
	 */
	public static final class Builder {

		/**
		 * Logical exporter identifier (e.g. {@code "record.dataset.npy"}).
		 */
		private String exporter;

		/**
		 * Canonical CRTK version stamped into the manifest.
		 */
		private String crtkVersion;

		/**
		 * Optional git commit hash; {@code null} omits the field.
		 */
		private String gitCommit;

		/**
		 * Verbatim argv tokens passed to {@code application.Main}.
		 */
		private List<String> argv = List.of();

		/**
		 * Input artifact paths.
		 */
		private final List<Path> inputs = new ArrayList<>();

		/**
		 * Output artifact paths.
		 */
		private final List<Path> outputs = new ArrayList<>();

		/**
		 * Optional weights artifact paths.
		 */
		private final List<Path> weights = new ArrayList<>();

		/**
		 * Optional exporter-owned metadata values, stored as JSON value text.
		 */
		private final LinkedHashMap<String, String> metadata = new LinkedHashMap<>();

		/**
		 * Constructs an empty builder.
		 */
		private Builder() {
			// package-private
		}

		/**
		 * Sets the exporter identifier (required).
		 *
		 * @param name exporter identifier
		 * @return this builder
		 */
		public Builder exporter(String name) {
			this.exporter = Objects.requireNonNull(name, "exporter");
			return this;
		}

		/**
		 * Sets the CRTK version (required).
		 *
		 * @param version canonical version string
		 * @return this builder
		 */
		public Builder crtkVersion(String version) {
			this.crtkVersion = Objects.requireNonNull(version, "crtkVersion");
			return this;
		}

		/**
		 * Sets the git commit hash. {@code null} or blank omits the field.
		 *
		 * @param commit git commit hash or {@code null}
		 * @return this builder
		 */
		public Builder gitCommit(String commit) {
			this.gitCommit = (commit == null || commit.isBlank()) ? null : commit.strip();
			return this;
		}

		/**
		 * Sets the argv tokens to record (required).
		 *
		 * @param tokens verbatim argv tokens
		 * @return this builder
		 */
		public Builder argv(List<String> tokens) {
			this.argv = List.copyOf(Objects.requireNonNull(tokens, "argv"));
			return this;
		}

		/**
		 * Adds an input artifact path.
		 *
		 * @param path input file path
		 * @return this builder
		 */
		public Builder input(Path path) {
			inputs.add(Objects.requireNonNull(path, "input path"));
			return this;
		}

		/**
		 * Adds multiple input artifact paths.
		 *
		 * @param paths input file paths
		 * @return this builder
		 */
		public Builder inputs(List<Path> paths) {
			for (Path path : paths) {
				input(path);
			}
			return this;
		}

		/**
		 * Adds an output artifact path.
		 *
		 * @param path output file path
		 * @return this builder
		 */
		public Builder output(Path path) {
			outputs.add(Objects.requireNonNull(path, "output path"));
			return this;
		}

		/**
		 * Adds an optional weights artifact path. {@code null} is a no-op.
		 *
		 * @param path weights file path or {@code null}
		 * @return this builder
		 */
		public Builder weights(Path path) {
			if (path != null) {
				weights.add(path);
			}
			return this;
		}

		/**
		 * Adds a string metadata value.
		 *
		 * @param name  metadata key
		 * @param value metadata value
		 * @return this builder
		 */
		public Builder metadata(String name, String value) {
			metadata.put(requireMetadataName(name), str(value));
			return this;
		}

		/**
		 * Adds a numeric metadata value.
		 *
		 * @param name  metadata key
		 * @param value metadata value
		 * @return this builder
		 */
		public Builder metadataNumber(String name, long value) {
			metadata.put(requireMetadataName(name), Long.toString(value));
			return this;
		}

		/**
		 * Adds a boolean metadata value.
		 *
		 * @param name  metadata key
		 * @param value metadata value
		 * @return this builder
		 */
		public Builder metadataBoolean(String name, boolean value) {
			metadata.put(requireMetadataName(name), Boolean.toString(value));
			return this;
		}

		/**
		 * Serializes the manifest to a JSON string (no trailing newline).
		 *
		 * @return manifest JSON
		 * @throws IOException when an artifact cannot be hashed
		 */
		public String build() throws IOException {
			requireSet(exporter, "exporter");
			requireSet(crtkVersion, "crtkVersion");
			List<Entry> inputEntries = hashAll(inputs);
			List<Entry> outputEntries = hashAll(outputs);
			List<Entry> weightsEntries = hashAll(weights);
			return render(exporter, crtkVersion, gitCommit, argv, metadata,
					inputEntries, outputEntries, weightsEntries);
		}

		/**
		 * Writes the manifest to the given path, returning the path written.
		 *
		 * @param manifestPath destination path
		 * @return destination path
		 * @throws IOException when hashing or writing fails
		 */
		public Path writeTo(Path manifestPath) throws IOException {
			Path parent = manifestPath.getParent();
			if (parent != null && !Files.isDirectory(parent)) {
				Files.createDirectories(parent);
			}
			Files.writeString(manifestPath, build() + "\n", StandardCharsets.UTF_8);
			return manifestPath;
		}

		/**
		 * Hashes every path in the input list, preserving order.
		 *
		 * @param paths artifact paths
		 * @return entries with basenames and SHA-256 digests
		 * @throws IOException when a file cannot be hashed
		 */
		private static List<Entry> hashAll(List<Path> paths) throws IOException {
			List<Entry> out = new ArrayList<>(paths.size());
			for (Path path : paths) {
				out.add(new Entry(path.getFileName().toString(), Provenance.sha256(path)));
			}
			return out;
		}

		/**
		 * Throws when a required string field is unset or blank.
		 *
		 * @param value field value
		 * @param name  field name
		 */
		private static void requireSet(String value, String name) {
			if (value == null || value.isBlank()) {
				throw new IllegalStateException("DatasetManifest: " + name + " must be set");
			}
		}

		/**
		 * Validates a metadata key.
		 *
		 * @param name candidate metadata key
		 * @return stripped metadata key
		 */
		private static String requireMetadataName(String name) {
			if (name == null || name.isBlank()) {
				throw new IllegalArgumentException("DatasetManifest: metadata name must be set");
			}
			return name.strip();
		}
	}

	/**
	 * One artifact entry within the {@code inputs} / {@code outputs} /
	 * {@code weights} arrays.
	 *
	 * @param name   basename of the artifact file
	 * @param sha256 lowercase hex SHA-256 digest of the artifact's bytes
	 */
	public record Entry(String name, String sha256) {
	}

	/**
	 * Renders the manifest JSON for the given component values.
	 *
	 * @param exporter      logical exporter identifier
	 * @param crtkVersion   CRTK version
	 * @param gitCommit     git commit hash or {@code null}
	 * @param argv          verbatim argv tokens
	 * @param metadata      optional exporter-owned metadata values
	 * @param inputs        input artifact entries
	 * @param outputs       output artifact entries
	 * @param weights       weights artifact entries
	 * @return manifest JSON text (no trailing newline)
	 */
	private static String render(String exporter, String crtkVersion, String gitCommit,
			List<String> argv, Map<String, String> metadata,
			List<Entry> inputs, List<Entry> outputs, List<Entry> weights) {
		StringBuilder sb = new StringBuilder(512);
		sb.append("{\n");
		field(sb, 1, "schemaVersion", str(SCHEMA_VERSION), true);
		field(sb, 1, "exporter", str(exporter), true);
		field(sb, 1, "crtkVersion", str(crtkVersion), true);
		if (gitCommit != null) {
			field(sb, 1, "gitCommit", str(gitCommit), true);
		}
		field(sb, 1, "argv", strArray(argv), true);
		if (!metadata.isEmpty()) {
			field(sb, 1, "metadata", metadataObject(metadata), true);
		}
		field(sb, 1, "inputs", entriesArray(inputs), true);
		field(sb, 1, "outputs", entriesArray(outputs), true);
		field(sb, 1, "weights", entriesArray(weights), false);
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Appends one {@code "key": value} line at the given indentation.
	 *
	 * @param sb       target buffer
	 * @param depth    indentation depth (2 spaces per level)
	 * @param key      field name
	 * @param value    JSON-encoded value text
	 * @param trailing whether to append a trailing comma
	 */
	private static void field(StringBuilder sb, int depth, String key, String value, boolean trailing) {
		sb.append("  ".repeat(depth))
				.append('"').append(key).append("\": ")
				.append(value)
				.append(trailing ? "," : "")
				.append('\n');
	}

	/**
	 * Returns a compact JSON string array literal.
	 *
	 * @param values array values
	 * @return JSON array text
	 */
	private static String strArray(List<String> values) {
		StringBuilder sb = new StringBuilder().append('[');
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(str(values.get(i)));
		}
		return sb.append(']').toString();
	}

	/**
	 * Returns a compact JSON object for exporter metadata.
	 *
	 * @param values metadata key to encoded JSON value
	 * @return JSON object text
	 */
	private static String metadataObject(Map<String, String> values) {
		StringBuilder sb = new StringBuilder().append('{');
		int index = 0;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (index > 0) {
				sb.append(", ");
			}
			sb.append(str(entry.getKey())).append(": ").append(entry.getValue());
			index++;
		}
		return sb.append('}').toString();
	}

	/**
	 * Returns a JSON array of {@code Entry} objects (one per line).
	 *
	 * @param entries artifact entries
	 * @return JSON array text
	 */
	private static String entriesArray(List<Entry> entries) {
		if (entries.isEmpty()) {
			return "[]";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("[\n");
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			sb.append("    {\"name\": ").append(str(entry.name()))
					.append(", \"sha256\": ").append(str(entry.sha256())).append('}');
			sb.append(i < entries.size() - 1 ? ",\n" : "\n");
		}
		sb.append("  ]");
		return sb.toString();
	}

	/**
	 * Returns a JSON string literal for the given value.
	 *
	 * @param value source value
	 * @return JSON string text
	 */
	private static String str(String value) {
		return "\"" + (value == null ? "" : Json.esc(value)) + "\"";
	}
}
