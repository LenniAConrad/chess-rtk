package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.deleteRecursively;
import static testing.TestSupport.readUtf8;
import static testing.TestSupport.runMain;
import static testing.TestSupport.writeDatasetFixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import chess.schema.JsonParser;
import chess.schema.JsonValue;

/**
 * Byte-equality determinism harness for CRTK's record-derived exporters.
 *
 * <p>Runs each deterministic exporter twice over a fixed in-memory fixture and
 * asserts every output file is byte-identical between the two runs. For
 * artifacts that intentionally embed absolute output paths (only
 * {@code classifier} today), the test removes a documented field list and
 * compares the remaining JSON structurally. Anything outside that documented
 * exception list must hash identically.</p>
 *
 * <p>This test is the central drift gate for CRTK's reproducibility promise.
 * If an exporter starts baking {@link System#currentTimeMillis()}, a random
 * seed, an unstable map iteration order, or an environment fingerprint into
 * its output, this test fails on the very next CI run.</p>
 *
 * <h2>Exporters covered</h2>
 * <ul>
 *   <li>{@code record dataset npy} — 21-plane tensors plus labels (no manifest today)</li>
 *   <li>{@code record dataset lc0} — input/policy/value tensors and a path-independent manifest</li>
 *   <li>{@code record dataset classifier} — 21-plane tensors, labels, and a manifest
 *       that embeds absolute output paths (the only documented exception)</li>
 *   <li>{@code record export training-jsonl} — JSONL training rows</li>
 * </ul>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetDeterminismRegressionTest {

	/**
	 * Fields stripped from the legacy classifier {@code .meta.json} sidecar
	 * before structural comparison. The file embeds absolute output paths
	 * supplied by the caller — those are not part of the determinism contract.
	 */
	private static final Set<String> CLASSIFIER_LEGACY_META_STRIPS =
			Set.of("inputs_file", "labels_file");

	/**
	 * Fields stripped from every {@code crtk.dataset.manifest.v1} sidecar
	 * before structural comparison. The verbatim {@code argv} carries
	 * absolute input/output paths supplied by the caller and is documented as
	 * caller-context, not part of the determinism contract. Every other field
	 * — including the per-artifact basenames and SHA-256 digests — must agree
	 * byte-for-byte across the two runs.
	 */
	private static final Set<String> DATASET_MANIFEST_STRIPS = Set.of("argv");

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetDeterminismRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-determinism-");
		try {
			Path fixture = writeDatasetFixture(workspace);
			testNpyExporterIsByteDeterministic(workspace, fixture);
			testLc0ExporterIsByteDeterministic(workspace, fixture);
			testClassifierExporterIsByteDeterministic(workspace, fixture);
			testTrainingJsonlExporterIsByteDeterministic(workspace, fixture);
			System.out.println("DatasetDeterminismRegressionTest: all checks passed");
		} finally {
			deleteRecursively(workspace);
		}
	}

	/**
	 * Pins byte-equality for the {@code record dataset npy} exporter.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testNpyExporterIsByteDeterministic(Path workspace, Path fixture) {
		List<Path> first = runExporter(workspace, "npy-1", fixture,
				new String[] { "record", "dataset", "npy" });
		List<Path> second = runExporter(workspace, "npy-2", fixture,
				new String[] { "record", "dataset", "npy" });
		compareOutputs("npy", first, second);
	}

	/**
	 * Pins byte-equality for the {@code record dataset lc0} exporter.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testLc0ExporterIsByteDeterministic(Path workspace, Path fixture) {
		List<Path> first = runExporter(workspace, "lc0-1", fixture,
				new String[] { "record", "dataset", "lc0" });
		List<Path> second = runExporter(workspace, "lc0-2", fixture,
				new String[] { "record", "dataset", "lc0" });
		compareOutputs("lc0", first, second);
	}

	/**
	 * Pins byte-equality for the {@code record dataset classifier} exporter.
	 *
	 * <p>The classifier writes its absolute output paths into the manifest, so
	 * the test strips {@link #CLASSIFIER_MANIFEST_PATH_FIELDS} from each
	 * manifest before comparing. Every other byte of every other artifact
	 * must agree.</p>
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testClassifierExporterIsByteDeterministic(Path workspace, Path fixture) {
		List<Path> first = runExporter(workspace, "classifier-1", fixture,
				new String[] { "record", "dataset", "classifier" });
		List<Path> second = runExporter(workspace, "classifier-2", fixture,
				new String[] { "record", "dataset", "classifier" });
		compareOutputs("classifier", first, second);
	}

	/**
	 * Pins byte-equality for the {@code record export training-jsonl} exporter.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testTrainingJsonlExporterIsByteDeterministic(Path workspace, Path fixture) {
		List<Path> first = runExporter(workspace, "training-jsonl-1", fixture,
				new String[] { "record", "export", "training-jsonl" });
		List<Path> second = runExporter(workspace, "training-jsonl-2", fixture,
				new String[] { "record", "export", "training-jsonl" });
		compareOutputs("training-jsonl", first, second);
	}

	/**
	 * Runs one exporter into a fresh per-variant subdirectory and returns its outputs.
	 *
	 * @param workspace scratch directory
	 * @param variant   per-variant label (used as a subdirectory name)
	 * @param fixture   shared input fixture
	 * @param verb      argv tokens for the exporter (e.g. {@code record dataset npy})
	 * @return list of files produced under the variant directory, sorted by name
	 */
	private static List<Path> runExporter(Path workspace, String variant, Path fixture, String[] verb) {
		Path outDir = workspace.resolve(variant);
		try {
			Files.createDirectories(outDir);
		} catch (IOException ex) {
			throw new AssertionError("failed to create variant dir: " + ex.getMessage(), ex);
		}
		Path outputPrefix = outDir.resolve("out");
		List<String> argv = new ArrayList<>();
		for (String token : verb) {
			argv.add(token);
		}
		argv.add("--input");
		argv.add(fixture.toString());
		argv.add("--output");
		argv.add(outputPrefix.toString());
		runMain(argv.toArray(new String[0]));
		try (Stream<Path> walk = Files.walk(outDir)) {
			return walk.filter(Files::isRegularFile).sorted().toList();
		} catch (IOException ex) {
			throw new AssertionError("failed to list output dir: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Compares two runs file-by-file, applying per-filename strip rules
	 * before structural comparison and otherwise asserting byte equality.
	 *
	 * @param label  exporter label for diagnostics
	 * @param first  file list from the first run
	 * @param second file list from the second run
	 */
	private static void compareOutputs(String label, List<Path> first, List<Path> second) {
		assertEquals(first.size(), second.size(),
				label + ": file count mismatch between two runs");
		assertTrue(!first.isEmpty(), label + ": at least one output expected");
		for (int i = 0; i < first.size(); i++) {
			Path a = first.get(i);
			Path b = second.get(i);
			String relA = a.getParent().getFileName() + "/" + a.getFileName();
			String relB = b.getParent().getFileName() + "/" + b.getFileName();
			assertEquals(a.getFileName().toString(), b.getFileName().toString(),
					label + ": file name mismatch at index " + i + " (" + relA + " vs " + relB + ")");
			compareSingleFile(label, a, b);
		}
	}

	/**
	 * Compares one file from two runs, dispatching to manifest-stripped
	 * comparison for files whose name matches a documented manifest pattern.
	 *
	 * @param label exporter label for diagnostics
	 * @param a     file from the first run
	 * @param b     file from the second run
	 */
	private static void compareSingleFile(String label, Path a, Path b) {
		String filename = a.getFileName().toString();
		Set<String> strips = stripsForFilename(filename);
		if (strips != null) {
			compareManifestFiles(label, filename, a, b, strips);
			return;
		}
		String hashA = sha256(a);
		String hashB = sha256(b);
		assertEquals(hashA, hashB,
				label + ": " + filename + " differs between runs (hash " + hashA + " vs " + hashB + ")");
	}

	/**
	 * Returns the top-level keys to strip before comparing a manifest-shaped
	 * file, or {@code null} when the file is a pure-bytes artifact.
	 *
	 * @param filename file basename
	 * @return strip set or {@code null}
	 */
	private static Set<String> stripsForFilename(String filename) {
		if (filename.endsWith(".manifest.json")) {
			return DATASET_MANIFEST_STRIPS;
		}
		if (filename.endsWith(".classifier.meta.json")) {
			return CLASSIFIER_LEGACY_META_STRIPS;
		}
		if (filename.endsWith("meta.json")) {
			return Set.of();
		}
		return null;
	}

	/**
	 * Structurally compares two manifest files after removing the documented
	 * path fields. Any other key change fails the assertion.
	 *
	 * @param label           exporter label for diagnostics
	 * @param filename        manifest file name for diagnostics
	 * @param a               manifest from the first run
	 * @param b               manifest from the second run
	 * @param pathFieldStrips fields removed before comparing (top-level only)
	 */
	private static void compareManifestFiles(String label, String filename, Path a, Path b,
			Set<String> pathFieldStrips) {
		String textA = readUtf8(a);
		String textB = readUtf8(b);
		JsonValue parsedA = JsonParser.parse(textA);
		JsonValue parsedB = JsonParser.parse(textB);
		JsonValue strippedA = stripPathFields(parsedA, pathFieldStrips);
		JsonValue strippedB = stripPathFields(parsedB, pathFieldStrips);
		assertTrue(strippedA.structurallyEquals(strippedB),
				label + ": " + filename + " differs in non-path fields between runs");
	}

	/**
	 * Returns a copy of a JSON object value with the documented top-level
	 * fields removed. Non-object values pass through unchanged.
	 *
	 * @param value           original value
	 * @param pathFieldStrips top-level keys to remove
	 * @return stripped JSON value
	 */
	private static JsonValue stripPathFields(JsonValue value, Set<String> pathFieldStrips) {
		if (value.kind() != JsonValue.Kind.OBJECT || pathFieldStrips.isEmpty()) {
			return value;
		}
		LinkedHashMap<String, JsonValue> entries = new LinkedHashMap<>(value.asObject());
		for (String key : pathFieldStrips) {
			entries.remove(key);
		}
		return JsonValue.ofObject(entries);
	}

	/**
	 * Returns the SHA-256 hex digest of a file's content.
	 *
	 * @param path file path
	 * @return hex digest string
	 */
	private static String sha256(Path path) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(Files.readAllBytes(path));
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException ex) {
			throw new AssertionError("SHA-256 unavailable on this JDK", ex);
		} catch (IOException ex) {
			throw new AssertionError("failed to read " + path + ": " + ex.getMessage(), ex);
		}
	}

}
