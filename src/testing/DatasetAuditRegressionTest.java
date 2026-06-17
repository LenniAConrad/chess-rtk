package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.deleteRecursively;
import static testing.TestSupport.runMain;
import static testing.TestSupport.runMainExpectFailure;
import static testing.TestSupport.writeUtf8;
import static testing.TestSupport.writeDatasetFixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression coverage for {@code crtk dataset audit}.
 *
 * <p>The auditor extends the manifest verifier to operate on a whole directory
 * tree. This test pins the four user-visible outcomes:</p>
 * <ul>
 *   <li>a tree of clean exports → all OK, exit 0;</li>
 *   <li>a tree with one tampered artifact → exit 3, stderr names the
 *       offending manifest and artifact, stdout aggregate reports 1 failed;</li>
 *   <li>{@code --limit} caps the audit count;</li>
 *   <li>a non-directory {@code --root} exits 2.</li>
 * </ul>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetAuditRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetAuditRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-audit-");
		try {
			Path fixture = writeDatasetFixture(workspace);
			testCleanTreeAuditsOk(workspace, fixture);
			testTamperedTreeFailsLoud(workspace, fixture);
			testLimitCapsAuditCount(workspace, fixture);
			testNonDirectoryRootFailsAsUsage(workspace, fixture);
			System.out.println("DatasetAuditRegressionTest: all checks passed");
		} finally {
			deleteRecursively(workspace);
		}
	}

	/**
	 * Verifies a tree of three clean exports audits clean.
	 *
	 * @param workspace scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testCleanTreeAuditsOk(Path workspace, Path fixture) {
		Path root = workspace.resolve("clean");
		exportTriple(root, fixture);
		String stdout = runMain("dataset", "audit", "--root", root.toString()).trim();
		assertTrue(stdout.contains("\"total\":3"), "clean audit total=3");
		assertTrue(stdout.contains("\"ok\":3"), "clean audit ok=3");
		assertTrue(stdout.contains("\"failed\":0"), "clean audit failed=0");
		assertTrue(stdout.contains("\"outcome\":\"ok\""), "clean audit outcome=ok");
	}

	/**
	 * Verifies a tree with one tampered artifact exits 3 and names the failure.
	 *
	 * @param workspace scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testTamperedTreeFailsLoud(Path workspace, Path fixture) {
		Path root = workspace.resolve("dirty");
		exportTriple(root, fixture);
		Path tampered = root.resolve("c").resolve("out.features.npy");
		writeUtf8(tampered, "tampered\n");
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "audit", "--root", root.toString());
		assertEquals(3, result.exitCode(), "tampered tree exits 3");
		assertTrue(result.stderr().contains("out.features.npy"),
				"stderr names the drifted artifact");
		assertTrue(result.stderr().contains("drift"),
				"stderr labels the failure as drift");
		assertTrue(result.stdout().contains("\"total\":3"), "aggregate total=3");
		assertTrue(result.stdout().contains("\"failed\":1"), "aggregate failed=1");
	}

	/**
	 * Verifies {@code --limit} caps the audit count.
	 *
	 * @param workspace scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testLimitCapsAuditCount(Path workspace, Path fixture) {
		Path root = workspace.resolve("limit");
		exportTriple(root, fixture);
		String stdout = runMain("dataset", "audit", "--root", root.toString(),
				"--limit", "2").trim();
		assertTrue(stdout.contains("\"total\":2"),
				"--limit 2 reports total=2 instead of the full 3");
		assertTrue(stdout.contains("\"ok\":2"),
				"--limit 2 reports ok=2 on a clean tree");
	}

	/**
	 * Verifies a non-directory {@code --root} exits 2.
	 *
	 * @param workspace scratch directory
	 * @param fixture   shared input fixture
	 */
	private static void testNonDirectoryRootFailsAsUsage(Path workspace, Path fixture) {
		TestSupport.FailureResult result = runMainExpectFailure(
				"dataset", "audit", "--root", fixture.toString());
		assertEquals(2, result.exitCode(), "non-directory --root exits 2");
		assertTrue(result.stderr().contains("not a directory"),
				"stderr explains the root mismatch");
	}

	/**
	 * Runs {@code record dataset npy} into three sibling subdirectories of {@code root}.
	 *
	 * @param root    audit root
	 * @param fixture shared input fixture
	 */
	private static void exportTriple(Path root, Path fixture) {
		for (String name : new String[] { "a", "b", "c" }) {
			Path prefix = root.resolve(name).resolve("out");
			try {
				Files.createDirectories(prefix.getParent());
			} catch (IOException ex) {
				throw new AssertionError("failed to create subdir: " + ex.getMessage(), ex);
			}
			runMain("record", "dataset", "npy",
					"--input", fixture.toString(),
					"--output", prefix.toString());
		}
	}
}
