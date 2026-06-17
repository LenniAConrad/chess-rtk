package chess.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import chess.schema.JsonParser;
import chess.schema.JsonValue;
import chess.schema.Schemas;
import chess.schema.Validator;
import chess.schema.Violation;

/**
 * Re-hashes every artifact referenced by a {@code crtk.dataset.manifest.v1}
 * sidecar and reports drift, missing files, and schema breakage.
 *
 * <p>The verifier is the second half of the determinism contract: a manifest
 * makes the reproducibility claim, and {@link #verify(Path)} either confirms
 * it on a given filesystem or names the exact artifact (and section) that
 * has drifted. Both the manifest itself and its referenced files are checked
 * — a manifest that no longer validates against its schema is reported as a
 * structural failure before any hashing begins.</p>
 *
 * <p>Artifact basenames are resolved relative to the manifest file's parent
 * directory, so a manifest is portable across machine moves as long as the
 * sibling files travel with it.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetVerifier {

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetVerifier() {
		// utility
	}

	/**
	 * Verifies the manifest at the given path. Returns a structured report
	 * describing every artifact's status. Throws when the manifest file
	 * cannot be read at all — every other failure mode (parse errors,
	 * schema violations, drift, missing files) is captured in the report.
	 *
	 * @param manifestPath manifest file path
	 * @return verification report
	 * @throws IOException when reading the manifest file fails
	 */
	public static Report verify(Path manifestPath) throws IOException {
		String text = Files.readString(manifestPath, StandardCharsets.UTF_8);
		JsonValue parsed;
		try {
			parsed = JsonParser.parse(text);
		} catch (RuntimeException ex) {
			return Report.parseFailure(manifestPath, "not valid JSON: " + ex.getMessage());
		}
		Validator validator;
		try {
			validator = Schemas.load(DatasetManifest.SCHEMA_VERSION);
		} catch (RuntimeException ex) {
			return Report.parseFailure(manifestPath,
					"schema " + DatasetManifest.SCHEMA_VERSION + " unavailable: " + ex.getMessage());
		}
		List<Violation> schemaViolations = validator.validate(parsed);
		if (!schemaViolations.isEmpty()) {
			return Report.schemaFailure(manifestPath, schemaViolations);
		}
		Path parent = manifestPath.toAbsolutePath().getParent();
		LinkedHashMap<String, JsonValue> root = parsed.asObject();
		List<ArtifactStatus> inputs = verifySection(parent, root.get("inputs").asArray(), true);
		List<ArtifactStatus> outputs = verifySection(parent, root.get("outputs").asArray(), false);
		List<ArtifactStatus> weights = verifySection(parent, root.get("weights").asArray(), false);
		return Report.success(manifestPath, inputs, outputs, weights);
	}

	/**
	 * Verifies one {@code inputs} / {@code outputs} / {@code weights} array.
	 *
	 * <p>For inputs the absence of a sibling file is reported as
	 * {@link Status#UNAVAILABLE} and is not treated as a verification failure,
	 * because input sources may live anywhere on disk. For outputs and
	 * weights — both written by the exporter alongside the manifest by
	 * contract — absence is hard-{@link Status#MISSING}.</p>
	 *
	 * @param parentDir   directory in which artifact basenames are resolved
	 * @param entries     manifest section entries
	 * @param allowMissing {@code true} to downgrade missing/unreadable to soft
	 *                    {@link Status#UNAVAILABLE} (input sections only)
	 * @return per-artifact status list
	 */
	private static List<ArtifactStatus> verifySection(Path parentDir, List<JsonValue> entries,
			boolean allowMissing) {
		List<ArtifactStatus> out = new ArrayList<>(entries.size());
		Set<String> seenNames = new LinkedHashSet<>();
		for (JsonValue raw : entries) {
			LinkedHashMap<String, JsonValue> entry = raw.asObject();
			String name = entry.get("name").asString();
			String expected = entry.get("sha256").asString();
			if (!DatasetManifest.isPortableArtifactName(name)) {
				out.add(new ArtifactStatus(name, expected, null, Status.INVALID_NAME));
				continue;
			}
			if (!seenNames.add(name)) {
				out.add(new ArtifactStatus(name, expected, null, Status.DUPLICATE_NAME));
				continue;
			}
			if (!DatasetManifest.isSha256Hex(expected)) {
				out.add(new ArtifactStatus(name, expected, null, Status.INVALID_HASH));
				continue;
			}
			Path file = parentDir.resolve(name);
			if (!Files.isRegularFile(file)) {
				out.add(new ArtifactStatus(name, expected, null,
						allowMissing ? Status.UNAVAILABLE : Status.MISSING));
				continue;
			}
			String actual;
			try {
				actual = Provenance.sha256(file);
			} catch (IOException ex) {
				out.add(new ArtifactStatus(name, expected, null,
						allowMissing ? Status.UNAVAILABLE : Status.UNREADABLE));
				continue;
			}
			Status status = expected.equals(actual) ? Status.OK : Status.DRIFT;
			out.add(new ArtifactStatus(name, expected, actual, status));
		}
		return out;
	}

	/**
	 * Per-artifact verification status.
	 */
	public enum Status {

		/**
		 * The on-disk file hashes to the recorded SHA-256.
		 */
		OK,

		/**
		 * The on-disk file hashes to a different SHA-256 than recorded.
		 */
		DRIFT,

		/**
		 * The artifact file is absent next to the manifest.
		 */
		MISSING,

		/**
		 * The artifact file exists but could not be read.
		 */
		UNREADABLE,

		/**
		 * The artifact is absent from the local filesystem, but the section
		 * contract allows it (inputs only — sources may live anywhere).
		 */
		UNAVAILABLE,

		/**
		 * The artifact name is not the portable basename form emitted by CRTK
		 * manifests, so it was not resolved against the filesystem.
		 */
		INVALID_NAME,

		/**
		 * The recorded digest is not the lowercase SHA-256 hex form emitted by
		 * CRTK manifests.
		 */
		INVALID_HASH,

		/**
		 * The section contains the same artifact basename more than once.
		 */
		DUPLICATE_NAME
	}

	/**
	 * Top-level report outcome.
	 */
	public enum Outcome {

		/**
		 * Every artifact verified clean.
		 */
		OK,

		/**
		 * At least one artifact drifted, was missing, or was unreadable.
		 */
		DRIFT_DETECTED,

		/**
		 * The manifest could not be parsed as JSON.
		 */
		PARSE_FAILURE,

		/**
		 * The manifest is parsable but fails the published schema.
		 */
		SCHEMA_FAILURE
	}

	/**
	 * Per-artifact verification status.
	 *
	 * @param name           artifact basename
	 * @param expectedSha256 recorded SHA-256 digest
	 * @param actualSha256   on-disk SHA-256 digest, or {@code null} when unavailable
	 * @param status         verification status
	 */
	public record ArtifactStatus(String name, String expectedSha256,
			String actualSha256, Status status) {
	}

	/**
	 * Aggregate verification report.
	 *
	 * @param manifestPath     manifest file path
	 * @param outcome          top-level outcome
	 * @param inputs           per-input statuses (empty unless outcome is OK or DRIFT_DETECTED)
	 * @param outputs          per-output statuses
	 * @param weights          per-weights statuses
	 * @param schemaViolations schema violations (empty unless outcome is SCHEMA_FAILURE)
	 * @param parseFailure     parse-failure message, or {@code null}
	 */
	public record Report(Path manifestPath, Outcome outcome,
			List<ArtifactStatus> inputs, List<ArtifactStatus> outputs,
			List<ArtifactStatus> weights, List<Violation> schemaViolations,
			String parseFailure) {

		/**
		 * Builds a parse-failure report.
		 *
		 * @param manifestPath manifest file path
		 * @param message      diagnostic message
		 * @return parse-failure report
		 */
		static Report parseFailure(Path manifestPath, String message) {
			return new Report(manifestPath, Outcome.PARSE_FAILURE, List.of(), List.of(),
					List.of(), List.of(), message);
		}

		/**
		 * Builds a schema-failure report.
		 *
		 * @param manifestPath manifest file path
		 * @param violations   schema violations
		 * @return schema-failure report
		 */
		static Report schemaFailure(Path manifestPath, List<Violation> violations) {
			return new Report(manifestPath, Outcome.SCHEMA_FAILURE, List.of(), List.of(),
					List.of(), List.copyOf(violations), null);
		}

		/**
		 * Builds a hashing-stage report whose outcome reflects the worst section status.
		 *
		 * @param manifestPath manifest file path
		 * @param inputs       per-input statuses
		 * @param outputs      per-output statuses
		 * @param weights      per-weights statuses
		 * @return hashing-stage report
		 */
		static Report success(Path manifestPath, List<ArtifactStatus> inputs,
				List<ArtifactStatus> outputs, List<ArtifactStatus> weights) {
			Outcome outcome = (anyProblem(inputs) || anyProblem(outputs) || anyProblem(weights))
					? Outcome.DRIFT_DETECTED
					: Outcome.OK;
			return new Report(manifestPath, outcome, List.copyOf(inputs), List.copyOf(outputs),
					List.copyOf(weights), List.of(), null);
		}

		/**
		 * Indicates whether the report's outcome is the clean path.
		 *
		 * @return {@code true} when every artifact verified
		 */
		public boolean ok() {
			return outcome == Outcome.OK;
		}

		/**
		 * Lists artifact entries whose status is not {@link Status#OK}, across
		 * inputs, outputs, and weights in that order.
		 *
		 * @return concatenated list of problems
		 */
		public List<ArtifactStatus> problems() {
			List<ArtifactStatus> out = new ArrayList<>();
			collectProblems(inputs, out);
			collectProblems(outputs, out);
			collectProblems(weights, out);
			return out;
		}

		/**
		 * Returns whether at least one section entry counts as a verification failure.
		 *
		 * <p>{@link Status#UNAVAILABLE} entries are soft (not a failure); every
		 * other non-OK status is a failure.</p>
		 *
		 * @param entries section entries
		 * @return {@code true} when any entry is not OK and not UNAVAILABLE
		 */
		private static boolean anyProblem(List<ArtifactStatus> entries) {
			for (ArtifactStatus entry : entries) {
				if (entry.status() != Status.OK && entry.status() != Status.UNAVAILABLE) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Appends every failing entry from a section to the accumulator.
		 *
		 * @param entries section entries
		 * @param sink    accumulator
		 */
		private static void collectProblems(List<ArtifactStatus> entries, List<ArtifactStatus> sink) {
			for (ArtifactStatus entry : entries) {
				if (entry.status() != Status.OK && entry.status() != Status.UNAVAILABLE) {
					sink.add(entry);
				}
			}
		}
	}
}
