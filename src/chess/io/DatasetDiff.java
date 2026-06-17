package chess.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import chess.schema.JsonParser;
import chess.schema.JsonValue;
import chess.schema.Schemas;
import chess.schema.Validator;
import chess.schema.Violation;

/**
 * Computes a structured diff between two {@code crtk.dataset.manifest.v1}
 * sidecars.
 *
 * <p>Where {@link DatasetVerifier} answers "does this manifest still match
 * the artifacts on disk?", {@link DatasetDiff} answers "why do these two
 * manifests disagree?" — the second question every consumer of a
 * reproducible dataset eventually asks. The diff categorises differences
 * into four orthogonal buckets:</p>
 * <ol>
 *   <li><b>envelope</b> — the identity fields (schemaVersion, exporter,
 *       crtkVersion, gitCommit) that name the producing tool and version;</li>
 *   <li><b>argv</b> — the exact tokens recorded by the producing CLI;</li>
 *   <li><b>artifact lineup</b> — per-section (inputs/outputs/weights), which
 *       basenames are added or removed between the two runs;</li>
 *   <li><b>artifact hashes</b> — for basenames present in both, whether the
 *       recorded SHA-256 digests agree.</li>
 * </ol>
 *
 * <p>Diffs are pure functions of the parsed JSON; no filesystem state is
 * consulted. The implementation reuses {@link chess.schema.JsonParser} so
 * malformed JSON yields a structured failure instead of an exception.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetDiff {

	/**
	 * Envelope field names compared by the diff.
	 */
	private static final List<String> ENVELOPE_FIELDS = List.of(
			"schemaVersion", "exporter", "crtkVersion", "gitCommit");

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetDiff() {
		// utility
	}

	/**
	 * Computes the diff between the two given manifest files.
	 *
	 * @param left  left manifest path
	 * @param right right manifest path
	 * @return diff result (may indicate parse failure on either side)
	 * @throws IOException when a manifest cannot be read
	 */
	public static DiffResult diff(Path left, Path right) throws IOException {
		JsonValue leftRoot = parseManifest(left);
		JsonValue rightRoot = parseManifest(right);
		if (leftRoot == null || rightRoot == null) {
			return DiffResult.parseFailure(left, right,
					leftRoot == null ? "left manifest is not valid JSON" : null,
					rightRoot == null ? "right manifest is not valid JSON" : null);
		}
		if (leftRoot.kind() != JsonValue.Kind.OBJECT || rightRoot.kind() != JsonValue.Kind.OBJECT) {
			return DiffResult.parseFailure(left, right,
					leftRoot.kind() == JsonValue.Kind.OBJECT
							? null
							: "left manifest is not a JSON object",
					rightRoot.kind() == JsonValue.Kind.OBJECT
							? null
							: "right manifest is not a JSON object");
		}
		Validator validator;
		try {
			validator = Schemas.load(DatasetManifest.SCHEMA_VERSION);
		} catch (RuntimeException ex) {
			String message = "schema " + DatasetManifest.SCHEMA_VERSION
					+ " unavailable: " + ex.getMessage();
			return DiffResult.parseFailure(left, right, message, message);
		}
		List<Violation> leftViolations = validator.validate(leftRoot);
		List<Violation> rightViolations = validator.validate(rightRoot);
		if (!leftViolations.isEmpty() || !rightViolations.isEmpty()) {
			return DiffResult.parseFailure(left, right,
					leftViolations.isEmpty() ? null : schemaError(leftViolations),
					rightViolations.isEmpty() ? null : schemaError(rightViolations));
		}
		LinkedHashMap<String, JsonValue> leftMap = leftRoot.asObject();
		LinkedHashMap<String, JsonValue> rightMap = rightRoot.asObject();
		String leftArtifactError = artifactEntryError(leftMap);
		String rightArtifactError = artifactEntryError(rightMap);
		if (leftArtifactError != null || rightArtifactError != null) {
			return DiffResult.parseFailure(left, right, leftArtifactError, rightArtifactError);
		}
		List<FieldDelta> envelope = compareEnvelope(leftMap, rightMap);
		ArgvDiff argv = compareArgv(leftMap, rightMap);
		SectionDiff inputs = compareSection(leftMap, rightMap, "inputs");
		SectionDiff outputs = compareSection(leftMap, rightMap, "outputs");
		SectionDiff weights = compareSection(leftMap, rightMap, "weights");
		return DiffResult.success(left, right, envelope, argv, inputs, outputs, weights);
	}

	/**
	 * Parses a manifest file, returning {@code null} on JSON parse failure.
	 *
	 * @param manifest manifest path
	 * @return parsed root value, or {@code null}
	 * @throws IOException when the file cannot be read
	 */
	private static JsonValue parseManifest(Path manifest) throws IOException {
		String text = Files.readString(manifest, StandardCharsets.UTF_8);
		try {
			return JsonParser.parse(text);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	/**
	 * Renders a compact schema-validation error for CLI diagnostics.
	 *
	 * @param violations schema violations
	 * @return one-line validation diagnostic
	 */
	private static String schemaError(List<Violation> violations) {
		StringBuilder sb = new StringBuilder("manifest does not validate against ")
				.append(DatasetManifest.SCHEMA_VERSION)
				.append(" (")
				.append(violations.size())
				.append(" violation")
				.append(violations.size() == 1 ? "" : "s")
				.append(')');
		if (!violations.isEmpty()) {
			sb.append(": ").append(violations.get(0).render());
		}
		return sb.toString();
	}

	/**
	 * Returns the first artifact contract violation in a parsed manifest.
	 *
	 * @param root manifest object
	 * @return diagnostic, or {@code null} when every artifact entry is valid
	 */
	private static String artifactEntryError(LinkedHashMap<String, JsonValue> root) {
		for (String section : List.of("inputs", "outputs", "weights")) {
			List<JsonValue> entries = root.get(section).asArray();
			Set<String> seenNames = new LinkedHashSet<>();
			for (int i = 0; i < entries.size(); i++) {
				LinkedHashMap<String, JsonValue> entry = entries.get(i).asObject();
				String name = entry.get("name").asString();
				if (!DatasetManifest.isPortableArtifactName(name)) {
					return section + "[" + i + "].name is not a portable artifact basename: " + name;
				}
				if (!seenNames.add(name)) {
					return section + "[" + i + "].name duplicates earlier artifact: " + name;
				}
				String sha256 = entry.get("sha256").asString();
				if (!DatasetManifest.isSha256Hex(sha256)) {
					return section + "[" + i + "].sha256 is not a lowercase SHA-256 digest";
				}
			}
		}
		return null;
	}

	/**
	 * Compares the envelope (identity) fields between two manifests.
	 *
	 * @param left  left object entries
	 * @param right right object entries
	 * @return ordered list of envelope deltas (empty when every field agrees)
	 */
	private static List<FieldDelta> compareEnvelope(
			LinkedHashMap<String, JsonValue> left, LinkedHashMap<String, JsonValue> right) {
		List<FieldDelta> deltas = new ArrayList<>();
		for (String field : ENVELOPE_FIELDS) {
			String leftValue = stringFieldOrNull(left, field);
			String rightValue = stringFieldOrNull(right, field);
			if (!nullSafeEquals(leftValue, rightValue)) {
				deltas.add(new FieldDelta(field, leftValue, rightValue));
			}
		}
		return deltas;
	}

	/**
	 * Compares the {@code argv} token lists.
	 *
	 * @param left  left object entries
	 * @param right right object entries
	 * @return argv diff
	 */
	private static ArgvDiff compareArgv(
			LinkedHashMap<String, JsonValue> left, LinkedHashMap<String, JsonValue> right) {
		List<String> leftArgv = stringArray(left, "argv");
		List<String> rightArgv = stringArray(right, "argv");
		return new ArgvDiff(leftArgv.equals(rightArgv), leftArgv, rightArgv);
	}

	/**
	 * Compares one artifact section (inputs/outputs/weights) between the two manifests.
	 *
	 * @param left  left object entries
	 * @param right right object entries
	 * @param name  section name
	 * @return per-section diff
	 */
	private static SectionDiff compareSection(
			LinkedHashMap<String, JsonValue> left,
			LinkedHashMap<String, JsonValue> right,
			String name) {
		LinkedHashMap<String, String> leftEntries = artifactMap(left, name);
		LinkedHashMap<String, String> rightEntries = artifactMap(right, name);
		Set<String> union = new LinkedHashSet<>();
		union.addAll(leftEntries.keySet());
		union.addAll(rightEntries.keySet());
		List<String> added = new ArrayList<>();
		List<String> removed = new ArrayList<>();
		List<HashDelta> hashChanges = new ArrayList<>();
		for (String basename : union) {
			String leftSha = leftEntries.get(basename);
			String rightSha = rightEntries.get(basename);
			if (leftSha == null && rightSha != null) {
				added.add(basename);
			} else if (leftSha != null && rightSha == null) {
				removed.add(basename);
			} else if (leftSha != null && !leftSha.equals(rightSha)) {
				hashChanges.add(new HashDelta(basename, leftSha, rightSha));
			}
		}
		return new SectionDiff(name, added, removed, hashChanges);
	}

	/**
	 * Extracts an artifact section as a basename → SHA-256 map.
	 *
	 * @param root object entries
	 * @param name section name
	 * @return map preserving JSON entry order
	 */
	private static LinkedHashMap<String, String> artifactMap(
			LinkedHashMap<String, JsonValue> root, String name) {
		LinkedHashMap<String, String> out = new LinkedHashMap<>();
		JsonValue raw = root.get(name);
		if (raw == null || raw.kind() != JsonValue.Kind.ARRAY) {
			return out;
		}
		for (JsonValue entry : raw.asArray()) {
			if (entry.kind() != JsonValue.Kind.OBJECT) {
				continue;
			}
			LinkedHashMap<String, JsonValue> e = entry.asObject();
			String basename = stringFieldOrNull(e, "name");
			String sha256 = stringFieldOrNull(e, "sha256");
			if (basename != null) {
				out.put(basename, sha256 == null ? "" : sha256);
			}
		}
		return out;
	}

	/**
	 * Returns a string-array field as a Java list.
	 *
	 * @param root object entries
	 * @param name field name
	 * @return ordered list of string tokens
	 */
	private static List<String> stringArray(
			LinkedHashMap<String, JsonValue> root, String name) {
		List<String> out = new ArrayList<>();
		JsonValue raw = root.get(name);
		if (raw == null || raw.kind() != JsonValue.Kind.ARRAY) {
			return out;
		}
		for (JsonValue entry : raw.asArray()) {
			if (entry.kind() == JsonValue.Kind.STRING) {
				out.add(entry.asString());
			}
		}
		return out;
	}

	/**
	 * Returns a string field, or {@code null} when the field is missing or
	 * not a string.
	 *
	 * @param root object entries
	 * @param name field name
	 * @return field value or {@code null}
	 */
	private static String stringFieldOrNull(LinkedHashMap<String, JsonValue> root, String name) {
		JsonValue raw = root.get(name);
		return (raw != null && raw.kind() == JsonValue.Kind.STRING) ? raw.asString() : null;
	}

	/**
	 * Null-safe string equality.
	 *
	 * @param a first value (may be {@code null})
	 * @param b second value (may be {@code null})
	 * @return {@code true} when both are {@code null} or both are equal strings
	 */
	private static boolean nullSafeEquals(String a, String b) {
		if (a == null) {
			return b == null;
		}
		return a.equals(b);
	}

	/**
	 * Top-level diff outcome.
	 */
	public enum Outcome {

		/**
		 * The two manifests are byte-for-byte equivalent for every documented field.
		 */
		IDENTICAL,

		/**
		 * At least one documented field, argv token, or artifact entry differs.
		 */
		DIFFER,

		/**
		 * One or both manifests could not be parsed or schema-validated.
		 */
		PARSE_FAILURE
	}

	/**
	 * One envelope-field delta.
	 *
	 * @param field      field name
	 * @param leftValue  left-side value (may be {@code null})
	 * @param rightValue right-side value (may be {@code null})
	 */
	public record FieldDelta(String field, String leftValue, String rightValue) {
	}

	/**
	 * Argv diff.
	 *
	 * @param identical  {@code true} when the two argv lists match exactly
	 * @param leftArgv   left-side tokens
	 * @param rightArgv  right-side tokens
	 */
	public record ArgvDiff(boolean identical, List<String> leftArgv, List<String> rightArgv) {
	}

	/**
	 * One hash-change entry inside a section diff.
	 *
	 * @param name      artifact basename
	 * @param leftSha256  left-side digest
	 * @param rightSha256 right-side digest
	 */
	public record HashDelta(String name, String leftSha256, String rightSha256) {
	}

	/**
	 * Per-section diff (inputs/outputs/weights).
	 *
	 * @param section       section name
	 * @param added         basenames present only on the right
	 * @param removed       basenames present only on the left
	 * @param hashChanges   basenames in both whose SHA-256 differs
	 */
	public record SectionDiff(String section, List<String> added, List<String> removed,
			List<HashDelta> hashChanges) {

		/**
		 * Indicates whether this section is byte-for-byte equivalent.
		 *
		 * @return {@code true} when no entries differ
		 */
		public boolean identical() {
			return added.isEmpty() && removed.isEmpty() && hashChanges.isEmpty();
		}
	}

	/**
	 * Top-level diff result.
	 *
	 * @param left          left manifest path
	 * @param right         right manifest path
	 * @param outcome       top-level outcome
	 * @param envelope      envelope-field deltas
	 * @param argv          argv diff
	 * @param inputs        inputs section diff
	 * @param outputs       outputs section diff
	 * @param weights       weights section diff
	 * @param leftError     left-side parse error (only when outcome is PARSE_FAILURE)
	 * @param rightError    right-side parse error (only when outcome is PARSE_FAILURE)
	 */
	public record DiffResult(Path left, Path right, Outcome outcome,
			List<FieldDelta> envelope, ArgvDiff argv,
			SectionDiff inputs, SectionDiff outputs, SectionDiff weights,
			String leftError, String rightError) {

		/**
		 * Indicates whether the two manifests are byte-for-byte equivalent.
		 *
		 * @return {@code true} when the diff is empty across every category
		 */
		public boolean identical() {
			return outcome == Outcome.IDENTICAL;
		}

		/**
		 * Builds a clean parse-failure diff result.
		 *
		 * @param left       left manifest path
		 * @param right      right manifest path
		 * @param leftError  left-side parse error or {@code null}
		 * @param rightError right-side parse error or {@code null}
		 * @return parse-failure diff result
		 */
		static DiffResult parseFailure(Path left, Path right, String leftError, String rightError) {
			SectionDiff empty = new SectionDiff("", List.of(), List.of(), List.of());
			return new DiffResult(left, right, Outcome.PARSE_FAILURE,
					List.of(),
					new ArgvDiff(true, List.of(), List.of()),
					empty, empty, empty,
					leftError, rightError);
		}

		/**
		 * Builds a success-stage diff result whose outcome reflects whether any
		 * category contains a difference.
		 *
		 * @param left      left manifest path
		 * @param right     right manifest path
		 * @param envelope  envelope deltas
		 * @param argv      argv diff
		 * @param inputs    inputs diff
		 * @param outputs   outputs diff
		 * @param weights   weights diff
		 * @return diff result
		 */
		static DiffResult success(Path left, Path right, List<FieldDelta> envelope,
				ArgvDiff argv, SectionDiff inputs, SectionDiff outputs, SectionDiff weights) {
			boolean identical = envelope.isEmpty()
					&& argv.identical()
					&& inputs.identical()
					&& outputs.identical()
					&& weights.identical();
			return new DiffResult(left, right,
					identical ? Outcome.IDENTICAL : Outcome.DIFFER,
					List.copyOf(envelope), argv, inputs, outputs, weights,
					null, null);
		}
	}
}
