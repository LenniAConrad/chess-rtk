package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.deleteRecursively;
import static testing.TestSupport.runMain;
import static testing.TestSupport.runMainExpectFailure;
import static testing.TestSupport.writeDatasetFixture;
import static testing.TestSupport.writeUtf8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.io.Provenance;

/**
 * Regression coverage for {@code crtk dataset verify}.
 *
 * <p>The verifier is the second half of the manifest determinism contract.
 * This test pins the user-visible outcomes:</p>
 * <ul>
 *   <li>clean verification of a freshly-emitted manifest → exit 0;</li>
 *   <li>drift on a tampered artifact → exit 3, stderr names the artifact;</li>
 *   <li>missing artifact next to the manifest → exit 3, stderr names it;</li>
 *   <li>non-basename artifact names → exit 3 before any path traversal;</li>
 *   <li>invalid artifact digests → exit 3 before hashing artifacts;</li>
 *   <li>duplicate artifact names → exit 3 before hashing artifacts;</li>
 *   <li>malformed manifest JSON → exit 3, stderr labels the parse failure;</li>
 *   <li>missing {@code --input} → usage failure (exit 2).</li>
 * </ul>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetVerifyRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetVerifyRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-verify-");
		try {
			Path fixture = writeDatasetFixture(workspace);
			testCleanVerificationOk(workspace, fixture);
			testTamperingOnOutputSurfacesAsDrift(workspace, fixture);
			testMissingOutputSurfacesAsMissing(workspace, fixture);
			testNonBasenameOutputNameFails(workspace);
			testInvalidOutputHashFails(workspace);
			testDuplicateOutputNameFails(workspace);
			testMalformedManifestFailsLoud(workspace);
			testMissingInputFailsAsUsage();
			System.out.println("DatasetVerifyRegressionTest: all checks passed");
		} finally {
			deleteRecursively(workspace);
		}
	}

	/**
	 * Verifies a freshly-exported manifest verifies clean.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testCleanVerificationOk(Path workspace, Path fixture) {
		Path prefix = workspace.resolve("ok").resolve("out");
		runExporter(prefix, fixture);
		Path manifest = Path.of(prefix + ".manifest.json");
		String stdout = runMain("dataset", "verify", "--input", manifest.toString()).trim();
		assertTrue(stdout.contains("\"outcome\":\"ok\""), "clean verification reports outcome=ok");
		assertTrue(stdout.contains("\"problems\":0"), "clean verification reports zero problems");
	}

	/**
	 * Verifies a tampered artifact surfaces as drift on stderr and exits 3.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testTamperingOnOutputSurfacesAsDrift(Path workspace, Path fixture) {
		Path prefix = workspace.resolve("drift").resolve("out");
		runExporter(prefix, fixture);
		Path tampered = Path.of(prefix + ".features.npy");
		writeUtf8(tampered, "tampered content\n");
		Path manifest = Path.of(prefix + ".manifest.json");
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "verify", "--input", manifest.toString());
		assertEquals(3, result.exitCode(), "drift exits 3");
		assertTrue(result.stderr().contains("outputs/out.features.npy"),
				"stderr names the drifted output");
		assertTrue(result.stderr().contains("drift"),
				"stderr labels the failure as drift");
	}

	/**
	 * Verifies a missing artifact surfaces as missing on stderr and exits 3.
	 *
	 * @param workspace shared scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testMissingOutputSurfacesAsMissing(Path workspace, Path fixture) {
		Path prefix = workspace.resolve("missing").resolve("out");
		runExporter(prefix, fixture);
		Path labels = Path.of(prefix + ".labels.npy");
		try {
			Files.deleteIfExists(labels);
		} catch (IOException ex) {
			throw new AssertionError("failed to delete artifact: " + ex.getMessage(), ex);
		}
		Path manifest = Path.of(prefix + ".manifest.json");
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "verify", "--input", manifest.toString());
		assertEquals(3, result.exitCode(), "missing artifact exits 3");
		assertTrue(result.stderr().contains("outputs/out.labels.npy"),
				"stderr names the missing artifact");
		assertTrue(result.stderr().contains("missing"),
				"stderr labels the failure as missing");
	}

	/**
	 * Verifies a schema-valid manifest cannot make verification resolve paths
	 * outside the manifest directory.
	 *
	 * @param workspace shared scratch directory
	 */
	private static void testNonBasenameOutputNameFails(Path workspace) {
		Path manifestDir = workspace.resolve("traversal");
		Path outside = workspace.resolve("outside-output.npy");
		try {
			Files.createDirectories(manifestDir);
		} catch (IOException ex) {
			throw new AssertionError("failed to prepare traversal fixture: " + ex.getMessage(), ex);
		}
		writeUtf8(outside, "outside artifact\n");
		Path manifest = manifestDir.resolve("traversal.manifest.json");
		writeTraversalManifest(manifest, "../outside-output.npy", sha256(outside));
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "verify", "--input", manifest.toString());
		assertEquals(3, result.exitCode(), "non-basename artifact name exits 3");
		assertTrue(result.stderr().contains("outputs/../outside-output.npy"),
				"stderr names the invalid artifact entry");
		assertTrue(result.stderr().contains("invalid_name"),
				"stderr labels the artifact name as invalid");
	}

	/**
	 * Verifies a schema-valid manifest with a malformed digest fails before
	 * hashing the referenced artifact.
	 *
	 * @param workspace shared scratch directory
	 */
	private static void testInvalidOutputHashFails(Path workspace) {
		Path manifestDir = workspace.resolve("invalid-hash");
		Path output = manifestDir.resolve("out.features.npy");
		try {
			Files.createDirectories(manifestDir);
		} catch (IOException ex) {
			throw new AssertionError("failed to prepare invalid-hash fixture: "
					+ ex.getMessage(), ex);
		}
		writeUtf8(output, "artifact\n");
		Path manifest = manifestDir.resolve("invalid-hash.manifest.json");
		writeTraversalManifest(manifest, "out.features.npy", "not-a-sha");
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "verify", "--input", manifest.toString());
		assertEquals(3, result.exitCode(), "invalid artifact digest exits 3");
		assertTrue(result.stderr().contains("outputs/out.features.npy"),
				"stderr names the invalid-hash artifact");
		assertTrue(result.stderr().contains("invalid_hash"),
				"stderr labels the artifact hash as invalid");
	}

	/**
	 * Verifies duplicate artifact names in one section fail before comparison or
	 * hashing can collapse them.
	 *
	 * @param workspace shared scratch directory
	 */
	private static void testDuplicateOutputNameFails(Path workspace) {
		Path manifestDir = workspace.resolve("duplicate-name");
		Path output = manifestDir.resolve("out.features.npy");
		try {
			Files.createDirectories(manifestDir);
		} catch (IOException ex) {
			throw new AssertionError("failed to prepare duplicate-name fixture: "
					+ ex.getMessage(), ex);
		}
		writeUtf8(output, "artifact\n");
		Path manifest = manifestDir.resolve("duplicate-name.manifest.json");
		writeDuplicateOutputManifest(manifest, "out.features.npy", sha256(output));
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "verify", "--input", manifest.toString());
		assertEquals(3, result.exitCode(), "duplicate artifact name exits 3");
		assertTrue(result.stderr().contains("outputs/out.features.npy"),
				"stderr names the duplicate artifact");
		assertTrue(result.stderr().contains("duplicate_name"),
				"stderr labels the artifact name as duplicate");
	}

	/**
	 * Verifies a malformed manifest surfaces a parse failure and exits 3.
	 *
	 * @param workspace shared scratch directory
	 */
	private static void testMalformedManifestFailsLoud(Path workspace) {
		Path manifest = workspace.resolve("malformed.manifest.json");
		writeUtf8(manifest, "{this is not valid JSON\n");
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "verify", "--input", manifest.toString());
		assertEquals(3, result.exitCode(), "malformed manifest exits 3");
		assertTrue(result.stderr().contains("manifest parse failure"),
				"stderr labels the manifest parse failure");
	}

	/**
	 * Verifies a missing {@code --input} flag exits as a usage failure.
	 */
	private static void testMissingInputFailsAsUsage() {
		TestSupport.FailureResult result = runMainExpectFailure("dataset", "verify");
		assertEquals(2, result.exitCode(), "missing --input exits 2");
		assertTrue(result.stderr().contains("Usage: crtk dataset verify"),
				"stderr shows the usage line");
	}

	/**
	 * Runs {@code record dataset npy} into the given output prefix.
	 *
	 * @param outputPrefix output prefix (e.g. {@code <tmp>/<variant>/out})
	 * @param fixture      shared input fixture
	 */
	private static void runExporter(Path outputPrefix, Path fixture) {
		try {
			Files.createDirectories(outputPrefix.getParent());
		} catch (IOException ex) {
			throw new AssertionError("failed to create variant dir: " + ex.getMessage(), ex);
		}
		runMain("record", "dataset", "npy",
				"--input", fixture.toString(),
				"--output", outputPrefix.toString());
	}

	/**
	 * Writes a schema-valid manifest whose artifact name is not a basename.
	 *
	 * @param target destination manifest
	 * @param outputName output artifact name to record
	 * @param outputSha256 output SHA-256 digest to record
	 */
	private static void writeTraversalManifest(Path target, String outputName, String outputSha256) {
		String json = "{\n"
				+ "  \"schemaVersion\": \"crtk.dataset.manifest.v1\",\n"
				+ "  \"exporter\": \"record.dataset.npy\",\n"
				+ "  \"crtkVersion\": \"1.0.0\",\n"
				+ "  \"argv\": [\"record\", \"dataset\", \"npy\"],\n"
				+ "  \"inputs\": [],\n"
				+ "  \"outputs\": [{\"name\": \"" + outputName + "\", \"sha256\": \""
				+ outputSha256 + "\"}],\n"
				+ "  \"weights\": []\n"
				+ "}\n";
		writeUtf8(target, json);
	}

	/**
	 * Writes a schema-valid manifest with a duplicated output artifact name.
	 *
	 * @param target destination manifest
	 * @param outputName output artifact name to duplicate
	 * @param outputSha256 output SHA-256 digest to record
	 */
	private static void writeDuplicateOutputManifest(Path target, String outputName, String outputSha256) {
		String json = "{\n"
				+ "  \"schemaVersion\": \"crtk.dataset.manifest.v1\",\n"
				+ "  \"exporter\": \"record.dataset.npy\",\n"
				+ "  \"crtkVersion\": \"1.0.0\",\n"
				+ "  \"argv\": [\"record\", \"dataset\", \"npy\"],\n"
				+ "  \"inputs\": [],\n"
				+ "  \"outputs\": [\n"
				+ "    {\"name\": \"" + outputName + "\", \"sha256\": \"" + outputSha256 + "\"},\n"
				+ "    {\"name\": \"" + outputName + "\", \"sha256\": \"" + outputSha256 + "\"}\n"
				+ "  ],\n"
				+ "  \"weights\": []\n"
				+ "}\n";
		writeUtf8(target, json);
	}

	/**
	 * Computes a SHA-256 digest for a fixture path.
	 *
	 * @param path file path
	 * @return lowercase SHA-256 digest
	 */
	private static String sha256(Path path) {
		try {
			return Provenance.sha256(path);
		} catch (IOException ex) {
			throw new AssertionError("failed to hash " + path + ": " + ex.getMessage(), ex);
		}
	}

}
