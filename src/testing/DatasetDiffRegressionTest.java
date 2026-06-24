package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.deleteRecursively;
import static testing.TestSupport.runMain;
import static testing.TestSupport.runMainExpectFailure;
import static testing.TestSupport.writeUtf8;

import java.nio.file.Path;
import java.util.List;

/**
 * Regression coverage for {@code crtk dataset diff}.
 *
 * <p>The diff is the third leg of the Theme I reproducibility tripod
 * (verify / audit / diff). This test pins the user-visible outcomes:</p>
 * <ul>
 *   <li>identical manifests → {@code outcome=identical}, exit 0;</li>
 *   <li>differing {@code crtkVersion} → envelope delta on stderr, exit 0
 *       (the JSON {@code identical=false} answers the script's question);</li>
 *   <li>differing argv → argv-different on stderr;</li>
 *   <li>differing artifact hash → per-section {@code sha256 changed} on stderr;</li>
 *   <li>{@code --strict} promotes any difference to exit 3;</li>
 *   <li>schema-invalid manifests → exit 3 before field comparison;</li>
 *   <li>non-basename artifact names → exit 3 before field comparison;</li>
 *   <li>invalid artifact digests → exit 3 before field comparison;</li>
 *   <li>duplicate artifact names → exit 3 before field comparison;</li>
 *   <li>missing {@code --left} or {@code --right} → exit 2.</li>
 * </ul>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetDiffRegressionTest {

	/**
	 * Valid lowercase SHA-256 fixture value.
	 */
	private static final String SHA_A =
			"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	/**
	 * Alternate valid lowercase SHA-256 fixture value.
	 */
	private static final String SHA_B =
			"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetDiffRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-diff-");
		try {
			testIdenticalManifestsMatch(workspace);
			testCrtkVersionDifferenceSurfacesInEnvelope(workspace);
			testArgvDifferenceSurfaces(workspace);
			testArtifactHashDifferenceSurfaces(workspace);
			testStrictFlagPromotesDifferenceToFailure(workspace);
			testSchemaInvalidManifestFailsBeforeDiff(workspace);
			testNonBasenameArtifactNameFailsBeforeDiff(workspace);
			testInvalidArtifactHashFailsBeforeDiff(workspace);
			testDuplicateArtifactNameFailsBeforeDiff(workspace);
			testMissingLeftFailsAsUsage();
			System.out.println("DatasetDiffRegressionTest: all checks passed");
		} finally {
			deleteRecursively(workspace);
		}
	}

	/**
	 * Verifies two identical manifests diff to identical=true with exit 0.
	 *
	 * @param workspace scratch directory
	 */
	private static void testIdenticalManifestsMatch(Path workspace) {
		Path manifest = workspace.resolve("identical.manifest.json");
		writeManifest(manifest, "abc", List.of("crtk", "x"), "1.0.0", SHA_A);
		String stdout = runMain("dataset", "diff",
				"--left", manifest.toString(),
				"--right", manifest.toString()).trim();
		assertTrue(stdout.contains("\"identical\":true"),
				"identical manifests report identical=true");
		assertTrue(stdout.contains("\"outcome\":\"identical\""),
				"identical manifests report outcome=identical");
	}

	/**
	 * Verifies a differing crtkVersion surfaces as an envelope delta.
	 *
	 * @param workspace scratch directory
	 */
	private static void testCrtkVersionDifferenceSurfacesInEnvelope(Path workspace) {
		Path left = workspace.resolve("v100.manifest.json");
		Path right = workspace.resolve("v101.manifest.json");
		writeManifest(left, "abc", List.of("crtk", "x"), "1.0.0", SHA_A);
		writeManifest(right, "abc", List.of("crtk", "x"), "1.0.1", SHA_A);
		String stdout = runMain("dataset", "diff",
				"--left", left.toString(),
				"--right", right.toString()).trim();
		assertTrue(stdout.contains("\"identical\":false"),
				"version difference makes identical=false");
		assertTrue(stdout.contains("\"envelopeDiffs\":1"),
				"version difference shows as one envelope delta");
		assertTrue(stdout.contains("\"outcome\":\"differ\""), "outcome=differ");
	}

	/**
	 * Verifies a differing argv surfaces in the summary and on stderr.
	 *
	 * @param workspace scratch directory
	 */
	private static void testArgvDifferenceSurfaces(Path workspace) {
		Path left = workspace.resolve("argv-a.manifest.json");
		Path right = workspace.resolve("argv-b.manifest.json");
		writeManifest(left, "abc", List.of("crtk", "x"), "1.0.0", SHA_A);
		writeManifest(right, "abc", List.of("crtk", "y"), "1.0.0", SHA_A);
		String stdout = runMain("dataset", "diff",
				"--left", left.toString(),
				"--right", right.toString()).trim();
		assertTrue(stdout.contains("\"argvDifferent\":true"),
				"differing argv flags as argvDifferent=true");
		assertTrue(stdout.contains("\"identical\":false"), "argv difference fails identity");
	}

	/**
	 * Verifies a differing artifact hash surfaces in the per-section count.
	 *
	 * @param workspace scratch directory
	 */
	private static void testArtifactHashDifferenceSurfaces(Path workspace) {
		Path left = workspace.resolve("hash-a.manifest.json");
		Path right = workspace.resolve("hash-b.manifest.json");
		writeManifest(left, "abc", List.of("crtk", "x"), "1.0.0", SHA_A);
		writeManifest(right, "abc", List.of("crtk", "x"), "1.0.0", SHA_B);
		String stdout = runMain("dataset", "diff",
				"--left", left.toString(),
				"--right", right.toString()).trim();
		assertTrue(stdout.contains("\"outputsDiff\":1"),
				"artifact hash difference shows as one output diff");
		assertTrue(stdout.contains("\"identical\":false"),
				"artifact difference fails identity");
	}

	/**
	 * Verifies {@code --strict} converts a non-identical comparison into exit 3.
	 *
	 * @param workspace scratch directory
	 */
	private static void testStrictFlagPromotesDifferenceToFailure(Path workspace) {
		Path left = workspace.resolve("strict-a.manifest.json");
		Path right = workspace.resolve("strict-b.manifest.json");
		writeManifest(left, "abc", List.of("crtk", "x"), "1.0.0", SHA_A);
		writeManifest(right, "abc", List.of("crtk", "x"), "1.0.1", SHA_A);
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "diff",
				"--left", left.toString(),
				"--right", right.toString(),
				"--strict");
		assertEquals(3, result.exitCode(), "--strict difference exits 3");
		assertTrue(result.stderr().contains("envelope/crtkVersion"),
				"stderr names the envelope category and field");
		assertTrue(result.stdout().contains("\"identical\":false"),
				"stdout summary still reports identical=false");
	}

	/**
	 * Verifies schema-invalid manifests fail instead of being compared as partial
	 * JSON objects.
	 *
	 * @param workspace scratch directory
	 */
	private static void testSchemaInvalidManifestFailsBeforeDiff(Path workspace) {
		Path left = workspace.resolve("schema-valid.manifest.json");
		Path right = workspace.resolve("schema-invalid.manifest.json");
		writeManifest(left, "abc", List.of("crtk", "x"), "1.0.0", SHA_A);
		writeSchemaInvalidManifest(right);
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "diff",
				"--left", left.toString(),
				"--right", right.toString());
		assertEquals(3, result.exitCode(), "schema-invalid manifest exits 3");
		assertTrue(result.stderr().contains("right: manifest does not validate"),
				"stderr names the schema-invalid side");
		assertTrue(result.stdout().contains("\"outcome\":\"parse_failure\""),
				"stdout summary reports parse_failure");
	}

	/**
	 * Verifies manifest artifact names must remain basenames even when the JSON
	 * shape validates.
	 *
	 * @param workspace scratch directory
	 */
	private static void testNonBasenameArtifactNameFailsBeforeDiff(Path workspace) {
		Path left = workspace.resolve("basename-valid.manifest.json");
		Path right = workspace.resolve("basename-invalid.manifest.json");
		writeManifest(left, "abc", List.of("crtk", "x"), "1.0.0", SHA_A);
		writeManifestWithOutputName(right, "abc", List.of("crtk", "x"),
				"1.0.0", "../outside.npy", SHA_A);
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "diff",
				"--left", left.toString(),
				"--right", right.toString());
		assertEquals(3, result.exitCode(), "non-basename artifact name exits 3");
		assertTrue(result.stderr().contains("right: outputs[0].name is not a portable artifact basename"),
				"stderr names the invalid artifact-name contract");
		assertTrue(result.stdout().contains("\"outcome\":\"parse_failure\""),
				"stdout summary reports parse_failure");
	}

	/**
	 * Verifies artifact digests must be lowercase SHA-256 hex strings before the
	 * diff compares fields.
	 *
	 * @param workspace scratch directory
	 */
	private static void testInvalidArtifactHashFailsBeforeDiff(Path workspace) {
		Path left = workspace.resolve("hash-valid.manifest.json");
		Path right = workspace.resolve("hash-invalid.manifest.json");
		writeManifest(left, "abc", List.of("crtk", "x"), "1.0.0", SHA_A);
		writeManifest(right, "abc", List.of("crtk", "x"), "1.0.0", "not-a-sha");
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "diff",
				"--left", left.toString(),
				"--right", right.toString());
		assertEquals(3, result.exitCode(), "invalid artifact digest exits 3");
		assertTrue(result.stderr().contains(
				"right: outputs[0].sha256 is not a lowercase SHA-256 digest"),
				"stderr names the invalid hash contract");
		assertTrue(result.stdout().contains("\"outcome\":\"parse_failure\""),
				"stdout summary reports parse_failure");
	}

	/**
	 * Verifies duplicate artifact names are rejected before the diff collapses
	 * section entries into a name-to-hash map.
	 *
	 * @param workspace scratch directory
	 */
	private static void testDuplicateArtifactNameFailsBeforeDiff(Path workspace) {
		Path left = workspace.resolve("duplicate-valid.manifest.json");
		Path right = workspace.resolve("duplicate-invalid.manifest.json");
		writeManifest(left, "abc", List.of("crtk", "x"), "1.0.0", SHA_A);
		writeManifestWithDuplicateOutput(right, "abc", List.of("crtk", "x"),
				"1.0.0", "out.features.npy", SHA_A);
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "diff",
				"--left", left.toString(),
				"--right", right.toString());
		assertEquals(3, result.exitCode(), "duplicate artifact name exits 3");
		assertTrue(result.stderr().contains(
				"right: outputs[1].name duplicates earlier artifact: out.features.npy"),
				"stderr names the duplicate artifact-name contract");
		assertTrue(result.stdout().contains("\"outcome\":\"parse_failure\""),
				"stdout summary reports parse_failure");
	}

	/**
	 * Verifies a missing {@code --left} flag exits as a usage failure.
	 */
	private static void testMissingLeftFailsAsUsage() {
		TestSupport.FailureResult result = runMainExpectFailure("dataset", "diff");
		assertEquals(2, result.exitCode(), "missing --left/--right exits 2");
		assertTrue(result.stderr().contains("Usage: crtk dataset diff"),
				"stderr shows the usage line");
	}

	/**
	 * Writes a minimal {@code crtk.dataset.manifest.v1} fixture.
	 *
	 * @param target       destination path
	 * @param gitCommit    fixed commit hash
	 * @param argv         argv tokens
	 * @param crtkVersion  source crtk version
	 * @param outputSha256 output artifact sha256
	 */
	private static void writeManifest(Path target, String gitCommit, java.util.List<String> argv,
			String crtkVersion, String outputSha256) {
		writeManifestWithOutputName(target, gitCommit, argv, crtkVersion,
				"out.features.npy", outputSha256);
	}

	/**
	 * Writes a minimal {@code crtk.dataset.manifest.v1} fixture with a custom
	 * output artifact name.
	 *
	 * @param target       destination path
	 * @param gitCommit    fixed commit hash
	 * @param argv         argv tokens
	 * @param crtkVersion  source crtk version
	 * @param outputName   output artifact name
	 * @param outputSha256 output artifact sha256
	 */
	private static void writeManifestWithOutputName(Path target, String gitCommit,
			java.util.List<String> argv, String crtkVersion, String outputName,
			String outputSha256) {
		writeManifestWithOutputs(target, gitCommit, argv, crtkVersion,
				"    {\"name\": \"" + outputName + "\", \"sha256\": \""
						+ outputSha256 + "\"}\n");
	}

	/**
	 * Writes a minimal {@code crtk.dataset.manifest.v1} fixture with duplicate
	 * output artifact entries.
	 *
	 * @param target       destination path
	 * @param gitCommit    fixed commit hash
	 * @param argv         argv tokens
	 * @param crtkVersion  source crtk version
	 * @param outputName   duplicated output artifact name
	 * @param outputSha256 output artifact sha256
	 */
	private static void writeManifestWithDuplicateOutput(Path target, String gitCommit,
			java.util.List<String> argv, String crtkVersion, String outputName,
			String outputSha256) {
		writeManifestWithOutputs(target, gitCommit, argv, crtkVersion,
				"    {\"name\": \"" + outputName + "\", \"sha256\": \""
						+ outputSha256 + "\"},\n"
						+ "    {\"name\": \"" + outputName + "\", \"sha256\": \""
						+ outputSha256 + "\"}\n");
	}

	/**
	 * Writes a minimal {@code crtk.dataset.manifest.v1} fixture with the supplied
	 * raw output entries.
	 *
	 * @param target       destination path
	 * @param gitCommit    fixed commit hash
	 * @param argv         argv tokens
	 * @param crtkVersion  source crtk version
	 * @param outputEntries raw JSON entries for the outputs array
	 */
	private static void writeManifestWithOutputs(Path target, String gitCommit,
			java.util.List<String> argv, String crtkVersion, String outputEntries) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"schemaVersion\": \"crtk.dataset.manifest.v1\",\n");
		sb.append("  \"exporter\": \"record.dataset.npy\",\n");
		sb.append("  \"crtkVersion\": \"").append(crtkVersion).append("\",\n");
		sb.append("  \"gitCommit\": \"").append(gitCommit).append("\",\n");
		sb.append("  \"argv\": [");
		for (int i = 0; i < argv.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append('"').append(argv.get(i)).append('"');
		}
		sb.append("],\n");
		sb.append("  \"inputs\": [{\"name\": \"in.json\", \"sha256\": \"")
				.append("0000000000000000000000000000000000000000000000000000000000000000")
				.append("\"}],\n");
		sb.append("  \"outputs\": [\n").append(outputEntries).append("  ],\n");
		sb.append("  \"weights\": []\n");
		sb.append("}\n");
		writeUtf8(target, sb.toString());
	}

	/**
	 * Writes a parsed-but-schema-invalid manifest fixture.
	 *
	 * @param target destination path
	 */
	private static void writeSchemaInvalidManifest(Path target) {
		String json = "{\n"
				+ "  \"schemaVersion\": \"crtk.dataset.manifest.v1\",\n"
				+ "  \"exporter\": \"record.dataset.npy\",\n"
				+ "  \"crtkVersion\": \"1.0.0\",\n"
				+ "  \"argv\": [\"crtk\", \"x\"],\n"
				+ "  \"inputs\": [],\n"
				+ "  \"outputs\": []\n"
				+ "}\n";
		writeUtf8(target, json);
	}

}
