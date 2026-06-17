package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.deleteRecursively;
import static testing.TestSupport.readUtf8;
import static testing.TestSupport.runMain;
import static testing.TestSupport.writeUtf8;
import static testing.TestSupport.writeDatasetFixture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import application.cli.command.CommandFailure;
import application.cli.command.DatasetManifestSupport;
import chess.io.DatasetManifest;
import chess.io.Provenance;
import chess.schema.JsonParser;
import chess.schema.JsonValue;
import chess.schema.Schemas;
import chess.schema.Validator;
import chess.schema.Violation;

/**
 * Regression coverage for the shared {@link DatasetManifest} sidecar that
 * every CRTK dataset exporter now emits.
 *
 * <p>Three things are pinned by this test:</p>
 * <ul>
 *   <li><b>Schema agreement</b> — the live manifest produced by each
 *       exporter (npy, lc0, classifier, training-jsonl) validates clean
 *       against {@code crtk.dataset.manifest.v1}.</li>
 *   <li><b>Hash correctness</b> — every SHA-256 recorded inside the
 *       manifest matches {@link Provenance#sha256(java.nio.file.Path)} of the
 *       referenced artifact on disk. This catches stale-hash regressions
 *       and field-ordering drift between the writer and consumers.</li>
 *   <li><b>Envelope identity</b> — the manifest carries the correct
 *       exporter label, the captured CRTK version, and the verbatim argv
 *       that produced it.</li>
 * </ul>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetManifestRegressionTest {

	/**
	 * Local LC0 test weights, when available.
	 */
	private static final Path LC0_TEST_WEIGHTS = Path.of("models",
			"leela_112planes-10blocksx128-policyhead80-valuehead32-policy4672-wdl3.bin");

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetManifestRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-manifest-");
		try {
			Path fixture = writeDatasetFixture(workspace);
			testNpyExporterEmitsValidManifest(workspace, fixture);
			testLc0ExporterEmitsValidManifest(workspace, fixture);
			testClassifierExporterEmitsValidManifest(workspace, fixture);
			testTrainingJsonlExporterEmitsValidManifest(workspace, fixture);
			testPuzzleEloJsonlExporterEmitsValidManifest(workspace, fixture);
			testPuzzleJsonlExporterEmitsValidManifestWhenWeightsPresent(workspace, fixture);
			testManifestWriteFailureIsFatal(workspace, fixture);
			System.out.println("DatasetManifestRegressionTest: all checks passed");
		} finally {
			deleteRecursively(workspace);
		}
	}

	/**
	 * Exercises {@code record dataset npy}.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testNpyExporterEmitsValidManifest(Path workspace, Path fixture) {
		Path prefix = workspace.resolve("npy");
		runMain("record", "dataset", "npy",
				"--input", fixture.toString(), "--output", prefix.toString(), "--row-hashes");
		Path rowHashes = rowHashPathFor(prefix);
		LinkedHashMap<String, JsonValue> manifest = validateManifest(
				Path.of(prefix + ".manifest.json"),
				"record.dataset.npy",
				List.of(fixture),
				List.of(
						Path.of(prefix + ".features.npy"),
						Path.of(prefix + ".labels.npy"),
						rowHashes));
		assertRowHashSidecar(manifest, "record.dataset.npy", rowHashes, 2L);
	}

	/**
	 * Exercises {@code record dataset lc0}.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testLc0ExporterEmitsValidManifest(Path workspace, Path fixture) {
		Path prefix = workspace.resolve("lc0");
		runMain("record", "dataset", "lc0",
				"--input", fixture.toString(), "--output", prefix.toString(), "--row-hashes");
		Path rowHashes = rowHashPathFor(prefix);
		LinkedHashMap<String, JsonValue> manifest = validateManifest(
				Path.of(prefix + ".lc0.manifest.json"),
				"record.dataset.lc0",
				List.of(fixture),
				List.of(
						Path.of(prefix + ".lc0.inputs.npy"),
						Path.of(prefix + ".lc0.policy.npy"),
						Path.of(prefix + ".lc0.value.npy"),
						rowHashes));
		assertRowHashSidecar(manifest, "record.dataset.lc0", rowHashes, 2L);
	}

	/**
	 * Exercises {@code record dataset classifier}.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testClassifierExporterEmitsValidManifest(Path workspace, Path fixture) {
		Path prefix = workspace.resolve("classifier");
		runMain("record", "dataset", "classifier",
				"--input", fixture.toString(), "--output", prefix.toString(), "--row-hashes");
		Path rowHashes = rowHashPathFor(prefix);
		LinkedHashMap<String, JsonValue> manifest = validateManifest(
				Path.of(prefix + ".classifier.manifest.json"),
				"record.dataset.classifier",
				List.of(fixture),
				List.of(
						Path.of(prefix + ".classifier.inputs.npy"),
						Path.of(prefix + ".classifier.labels.npy"),
						rowHashes));
		assertMetadataString(manifest, "record.dataset.classifier",
				"label_policy", "classifier-binary-21plane-v1");
		assertMetadataString(manifest, "record.dataset.classifier",
				"label_source", "kind, else fallback-label-filter");
		assertMetadataNumberAtLeast(manifest, "record.dataset.classifier", "records_seen", 2L);
		assertMetadataNumberAtLeast(manifest, "record.dataset.classifier", "rows_written", 0L);
		assertRowHashSidecar(manifest, "record.dataset.classifier", rowHashes,
				metadataLong(manifest, "record.dataset.classifier", "rows_written"));
	}

	/**
	 * Exercises {@code record export training-jsonl}.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testTrainingJsonlExporterEmitsValidManifest(Path workspace, Path fixture) {
		Path output = workspace.resolve("training.jsonl");
		runMain("record", "export", "training-jsonl",
				"--input", fixture.toString(), "--output", output.toString(), "--row-hashes");
		Path rowHashes = rowHashPathFor(output);
		LinkedHashMap<String, JsonValue> manifest = validateManifest(
				Path.of(output + ".manifest.json"),
				"record.export.training-jsonl",
				List.of(fixture),
				List.of(output, rowHashes));
		assertMetadataString(manifest, "record.export.training-jsonl",
				"label_policy", "training-jsonl-coarse-fine-v1");
		assertMetadataBool(manifest, "record.export.training-jsonl", "include_engine_metadata", false);
		assertMetadataNumberAtLeast(manifest, "record.export.training-jsonl", "records_seen", 2L);
		assertMetadataNumberAtLeast(manifest, "record.export.training-jsonl", "rows_written", 0L);
		assertRowHashSidecar(manifest, "record.export.training-jsonl", rowHashes,
				metadataLong(manifest, "record.export.training-jsonl", "rows_written"));
	}

	/**
	 * Exercises {@code record export puzzle-elo-jsonl}.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testPuzzleEloJsonlExporterEmitsValidManifest(Path workspace, Path fixture) {
		Path output = workspace.resolve("puzzle-elo.jsonl");
		runMain("record", "export", "puzzle-elo-jsonl",
				"--input", fixture.toString(), "--output", output.toString(), "--threads", "1", "--row-hashes");
		Path rowHashes = rowHashPathFor(output);
		LinkedHashMap<String, JsonValue> manifest = validateManifest(
				Path.of(output + ".manifest.json"),
				"record.export.puzzle-elo-jsonl",
				List.of(fixture),
				List.of(output, rowHashes));
		assertMetadataString(manifest, "record.export.puzzle-elo-jsonl",
				"label_policy", "crtk-tree-v14-evidence-direct");
		assertMetadataString(manifest, "record.export.puzzle-elo-jsonl",
				"rating_scope", "corpus-internal");
		assertMetadataBool(manifest, "record.export.puzzle-elo-jsonl", "calibrated_to_humans", false);
		assertMetadataString(manifest, "record.export.puzzle-elo-jsonl", "rating_source", "tree-search");
		assertMetadataNumberAtLeast(manifest, "record.export.puzzle-elo-jsonl", "records_seen", 2L);
		assertRowHashSidecar(manifest, "record.export.puzzle-elo-jsonl", rowHashes,
				metadataLong(manifest, "record.export.puzzle-elo-jsonl", "rows_written"));
	}

	/**
	 * Exercises {@code record export puzzle-jsonl} when local LC0 weights exist.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testPuzzleJsonlExporterEmitsValidManifestWhenWeightsPresent(Path workspace, Path fixture) {
		if (!Files.isRegularFile(LC0_TEST_WEIGHTS)) {
			return;
		}
		Path output = workspace.resolve("puzzle.jsonl");
		runMain("record", "export", "puzzle-jsonl",
				"--input", fixture.toString(),
				"--weights", LC0_TEST_WEIGHTS.toString(),
				"--output", output.toString(),
				"--row-hashes");
		Path rowHashes = rowHashPathFor(output);
		LinkedHashMap<String, JsonValue> manifest = validateManifest(
				Path.of(output + ".manifest.json"),
				"record.export.puzzle-jsonl",
				List.of(fixture),
				List.of(output, rowHashes));
		assertWeightsMatch("record.export.puzzle-jsonl", manifest, List.of(LC0_TEST_WEIGHTS));
		assertMetadataString(manifest, "record.export.puzzle-jsonl",
				"label_policy", "puzzle-jsonl-lc0-policy-v1");
		assertMetadataString(manifest, "record.export.puzzle-jsonl", "selector", "all");
		assertMetadataNumberAtLeast(manifest, "record.export.puzzle-jsonl", "records_seen", 2L);
		assertRowHashSidecar(manifest, "record.export.puzzle-jsonl", rowHashes,
				metadataLong(manifest, "record.export.puzzle-jsonl", "rows_written"));
	}

	/**
	 * Ensures provenance write failures fail the command instead of leaving a
	 * successful export without its manifest.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testManifestWriteFailureIsFatal(Path workspace, Path fixture) {
		Path output = workspace.resolve("manifest-output.txt");
		writeUtf8(output, "artifact\n");
		Path blocker = workspace.resolve("manifest-parent-is-file");
		writeUtf8(blocker, "not a directory\n");
		Path manifestPath = blocker.resolve("manifest.json");
		try {
			DatasetManifestSupport.write(
					"record.dataset.test",
					fixture,
					List.of(output),
					null,
					manifestPath);
			throw new AssertionError("manifest write failure did not fail the command");
		} catch (CommandFailure ex) {
			assertEquals(3, ex.exitCode(), "manifest write failure exit code");
			assertTrue(ex.getMessage().contains("failed to write provenance manifest"),
					"manifest write failure diagnostic");
		}
	}

	/**
	 * Asserts a manifest's envelope, schema validity, and per-artifact hashes.
	 *
	 * @param manifestPath  emitted manifest path
	 * @param exporter      expected exporter label
	 * @param expectedInputs expected input artifact paths
	 * @param expectedOutputs expected output artifact paths
	 */
	private static LinkedHashMap<String, JsonValue> validateManifest(Path manifestPath, String exporter,
			List<Path> expectedInputs, List<Path> expectedOutputs) {
		assertTrue(Files.isRegularFile(manifestPath),
				exporter + ": manifest file " + manifestPath + " was not written");
		String text = readUtf8(manifestPath);
		List<Violation> violations = Schemas.load(DatasetManifest.SCHEMA_VERSION).validate(text);
		if (!violations.isEmpty()) {
			StringBuilder report = new StringBuilder(exporter + ": manifest fails schema:\n");
			for (Violation violation : violations) {
				report.append("  ").append(violation.render()).append('\n');
			}
			throw new AssertionError(report.toString());
		}
		JsonValue parsed = JsonParser.parse(text);
		LinkedHashMap<String, JsonValue> root = parsed.asObject();
		assertEquals(DatasetManifest.SCHEMA_VERSION,
				root.get("schemaVersion").asString(),
				exporter + ": schemaVersion stamp");
		assertEquals(exporter,
				root.get("exporter").asString(),
				exporter + ": exporter label");
		String crtkVersion = root.get("crtkVersion").asString();
		assertTrue(!crtkVersion.isEmpty(),
				exporter + ": crtkVersion captured");
		List<JsonValue> argvTokens = root.get("argv").asArray();
		assertTrue(!argvTokens.isEmpty(),
				exporter + ": argv was not captured");
		String[] expectedArgvPrefix = exporter.startsWith("record.dataset.")
				? new String[] { "record", "dataset",
						exporter.substring("record.dataset.".length()) }
				: new String[] { "record", "export",
						exporter.substring("record.export.".length()) };
		for (int i = 0; i < expectedArgvPrefix.length; i++) {
			assertEquals(expectedArgvPrefix[i], argvTokens.get(i).asString(),
					exporter + ": argv[" + i + "]");
		}
		assertHashesMatch(exporter, root.get("inputs").asArray(), expectedInputs, "input");
		assertHashesMatch(exporter, root.get("outputs").asArray(), expectedOutputs, "output");
		return root;
	}

	/**
	 * Asserts manifest weights entries match the on-disk files.
	 *
	 * @param exporter exporter label for diagnostics
	 * @param manifest parsed manifest object
	 * @param expected expected weights paths
	 */
	private static void assertWeightsMatch(
			String exporter,
			LinkedHashMap<String, JsonValue> manifest,
			List<Path> expected) {
		assertHashesMatch(exporter, manifest.get("weights").asArray(), expected, "weights");
	}

	/**
	 * Asserts a string metadata field.
	 *
	 * @param manifest parsed manifest object
	 * @param exporter exporter label for diagnostics
	 * @param key metadata key
	 * @param expected expected string value
	 */
	private static void assertMetadataString(
			LinkedHashMap<String, JsonValue> manifest,
			String exporter,
			String key,
			String expected) {
		JsonValue value = metadata(manifest, exporter).get(key);
		assertTrue(value != null, exporter + ": missing metadata." + key);
		assertEquals(expected, value.asString(), exporter + ": metadata." + key);
	}

	/**
	 * Asserts a boolean metadata field.
	 *
	 * @param manifest parsed manifest object
	 * @param exporter exporter label for diagnostics
	 * @param key metadata key
	 * @param expected expected boolean value
	 */
	private static void assertMetadataBool(
			LinkedHashMap<String, JsonValue> manifest,
			String exporter,
			String key,
			boolean expected) {
		JsonValue value = metadata(manifest, exporter).get(key);
		assertTrue(value != null, exporter + ": missing metadata." + key);
		assertEquals(Boolean.toString(expected), Boolean.toString(value.asBool()),
				exporter + ": metadata." + key);
	}

	/**
	 * Asserts a numeric metadata field exists and is at least a floor.
	 *
	 * @param manifest parsed manifest object
	 * @param exporter exporter label for diagnostics
	 * @param key metadata key
	 * @param floor inclusive lower bound
	 */
	private static void assertMetadataNumberAtLeast(
			LinkedHashMap<String, JsonValue> manifest,
			String exporter,
			String key,
			long floor) {
		JsonValue value = metadata(manifest, exporter).get(key);
		assertTrue(value != null, exporter + ": missing metadata." + key);
		assertTrue(value.numberIsInteger(), exporter + ": metadata." + key + " is not an integer");
		assertTrue(value.asNumber() >= floor, exporter + ": metadata." + key + " below " + floor);
	}

	/**
	 * Returns a required integer metadata field.
	 *
	 * @param manifest parsed manifest object
	 * @param exporter exporter label for diagnostics
	 * @param key metadata key
	 * @return integer metadata value
	 */
	private static long metadataLong(LinkedHashMap<String, JsonValue> manifest, String exporter, String key) {
		JsonValue value = metadata(manifest, exporter).get(key);
		assertTrue(value != null, exporter + ": missing metadata." + key);
		assertTrue(value.numberIsInteger(), exporter + ": metadata." + key + " is not an integer");
		return (long) value.asNumber();
	}

	/**
	 * Asserts an optional row-hash sidecar is present and manifest-linked.
	 *
	 * @param manifest parsed manifest object
	 * @param exporter exporter label for diagnostics
	 * @param sidecar expected row-hash sidecar
	 * @param expectedRows expected line count
	 */
	private static void assertRowHashSidecar(
			LinkedHashMap<String, JsonValue> manifest,
			String exporter,
			Path sidecar,
			long expectedRows) {
		assertTrue(Files.isRegularFile(sidecar),
				exporter + ": row-hash sidecar was not written");
		assertMetadataString(manifest, exporter, "row_hash_algorithm", "sha256(raw-record-json-v1)");
		assertMetadataString(manifest, exporter, "row_hash_sidecar", sidecar.getFileName().toString());
		List<String> lines;
		try {
			lines = Files.readAllLines(sidecar, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new AssertionError(exporter + ": failed to read row-hash sidecar: "
					+ ex.getMessage(), ex);
		}
		assertEquals((int) expectedRows, lines.size(), exporter + ": row-hash sidecar row count");
		for (String line : lines) {
			assertTrue(line.matches("[0-9a-f]{64}"), exporter + ": row hash is not lowercase SHA-256");
		}
	}

	/**
	 * Returns the standard row-hash sidecar path for an output artifact.
	 *
	 * @param artifact output stem or file
	 * @return sidecar path
	 */
	private static Path rowHashPathFor(Path artifact) {
		return artifact.resolveSibling(artifact.getFileName() + ".rowhashes.txt");
	}

	/**
	 * Returns the manifest metadata object.
	 *
	 * @param manifest parsed manifest object
	 * @param exporter exporter label for diagnostics
	 * @return metadata object
	 */
	private static LinkedHashMap<String, JsonValue> metadata(
			LinkedHashMap<String, JsonValue> manifest,
			String exporter) {
		JsonValue metadata = manifest.get("metadata");
		assertTrue(metadata != null, exporter + ": metadata object missing");
		return metadata.asObject();
	}

	/**
	 * Asserts the manifest's artifact entries match the on-disk files.
	 *
	 * @param exporter exporter label for diagnostics
	 * @param entries  manifest artifact entries
	 * @param expected expected artifact paths
	 * @param kind     short kind label for diagnostics ({@code "input"}/{@code "output"})
	 */
	private static void assertHashesMatch(String exporter, List<JsonValue> entries,
			List<Path> expected, String kind) {
		assertEquals(expected.size(), entries.size(),
				exporter + ": " + kind + " count mismatch");
		for (int i = 0; i < expected.size(); i++) {
			Path file = expected.get(i);
			LinkedHashMap<String, JsonValue> entry = entries.get(i).asObject();
			assertEquals(file.getFileName().toString(),
					entry.get("name").asString(),
					exporter + ": " + kind + " name at index " + i);
			String onDisk;
			try {
				onDisk = Provenance.sha256(file);
			} catch (IOException ex) {
				throw new AssertionError(exporter + ": failed to hash on-disk artifact "
						+ file + ": " + ex.getMessage(), ex);
			}
			assertEquals(onDisk, entry.get("sha256").asString(),
					exporter + ": " + kind + " sha256 mismatch for " + file.getFileName());
		}
	}

}
