package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertContains;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.countUtf8Lines;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.deleteRecursively;
import static testing.TestSupport.readUtf8;
import static testing.TestSupport.runMain;
import static testing.TestSupport.runMainExpectFailure;
import static testing.TestSupport.writeJsonArrayFixture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Stream;

import chess.io.RecordSplitter;
import chess.io.RecordSplitter.SplitSpec;
import chess.io.RecordSplitter.Strategy;

/**
 * Regression coverage for {@code crtk record split}.
 *
 * <p>The split is a load-bearing reproducibility primitive: train/val/test
 * choice must be a pure function of (seed, group-key). This test pins six
 * invariants — five from the synthesis ("group-aware deterministic split
 * without leakage") plus one usage check:</p>
 * <ul>
 *   <li>two runs with the same seed produce byte-identical outputs;</li>
 *   <li>two records sharing a {@code position} land in the same split
 *       (transpositions can't straddle splits);</li>
 *   <li>every input record lands in exactly one split (no duplicates, no losses);</li>
 *   <li>two seeds produce demonstrably different bucket assignments;</li>
 *   <li>a sibling {@code .manifest.json} is written per split file, a top-level
 *       {@code .split.manifest.json} records the row-hash digest, and all
 *       manifests validate against the published schema;</li>
 *   <li>a missing required flag exits as a usage failure.</li>
 * </ul>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordSplitRegressionTest {

	/**
	 * Sample FENs used to construct the fixture.
	 */
	private static final List<String> SAMPLE_FENS = List.of(
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
			"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
			"rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
			"r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
			"rnbqkb1r/pppp1ppp/5n2/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
			"r1bqkb1r/pppp1ppp/2n2n2/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 4 4",
			"rnbqkbnr/pp1ppppp/8/2p5/8/4P3/PPPP1PPP/RNBQKBNR b KQkq - 0 2",
			"rnbqkbnr/p1pppppp/8/1p6/8/4P3/PPPP1PPP/RNBQKBNR w KQkq - 0 3",
			"r1bqkbnr/pppp1ppp/2n5/4p3/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 2 3",
			"rnbqkbnr/ppp2ppp/8/3pp3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 3",
			"rnbqkb1r/pp1ppppp/5n2/2p5/4P3/3P4/PPP2PPP/RNBQKBNR b KQkq - 0 3",
			"rnbqkb1r/pp1p1ppp/4pn2/2p5/4P3/2N5/PPPP1PPP/R1BQKBNR w KQkq - 0 4");

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordSplitRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-split-");
		try {
			testTwoRunsAreByteIdenticalForSameSeed(workspace);
			testRecordsSharingPositionLandInSameSplit(workspace);
			testEveryInputRecordLandsInExactlyOneSplit(workspace);
			testDifferentSeedsProduceDifferentAssignments(workspace);
			testManifestSidecarValidatesAgainstSchema(workspace);
			testRowHashSidecarsAreOptIn(workspace);
			testMissingFlagFailsAsUsage();
			System.out.println("RecordSplitRegressionTest: all checks passed");
		} finally {
			deleteRecursively(workspace);
		}
	}

	/**
	 * Verifies two runs with the same seed produce byte-identical outputs.
	 *
	 * @param workspace scratch directory
	 */
	private static void testTwoRunsAreByteIdenticalForSameSeed(Path workspace) {
		Path fixture = writeFixture(workspace.resolve("byte.record"));
		Path firstPrefix = workspace.resolve("byte-a");
		Path secondPrefix = workspace.resolve("byte-b");
		runMain("record", "split",
				"--input", fixture.toString(),
				"--output", firstPrefix.toString(),
				"--split", "70:15:15",
				"--seed", "1");
		runMain("record", "split",
				"--input", fixture.toString(),
				"--output", secondPrefix.toString(),
				"--split", "70:15:15",
				"--seed", "1");
		for (String name : List.of("train", "val", "test")) {
			String firstHash = sha256(RecordSplitter.outputPathFor(firstPrefix, name));
			String secondHash = sha256(RecordSplitter.outputPathFor(secondPrefix, name));
			assertEquals(firstHash, secondHash,
					"split '" + name + "' is byte-identical across two same-seed runs");
		}
	}

	/**
	 * Verifies two records sharing a {@code position} land in the same split.
	 *
	 * @param workspace scratch directory
	 */
	private static void testRecordsSharingPositionLandInSameSplit(Path workspace) {
		Path fixture = writeFixture(workspace.resolve("group.record"));
		// Use the in-process bucketFor directly so this assertion is independent
		// of file paths and CLI plumbing.
		SplitSpec spec = SplitSpec.parse("70:15:15", List.of("train", "val", "test"));
		String first = bucketFor(SAMPLE_FENS.get(0), spec, 1L);
		String firstAgain = bucketFor(SAMPLE_FENS.get(0), spec, 1L);
		String firstWithHalfmoveBumped = bucketFor(
				SAMPLE_FENS.get(0).replace("- 0 1", "- 5 7"), spec, 1L);
		assertEquals(first, firstAgain,
				"same FEN twice always yields the same bucket");
		assertEquals(first, firstWithHalfmoveBumped,
				"transpositions (different halfmove/fullmove counters) yield the same bucket");
		assertTrue(fixture != null, "fixture path must exist for the suite");
	}

	/**
	 * Verifies every input record lands in exactly one split.
	 *
	 * @param workspace scratch directory
	 */
	private static void testEveryInputRecordLandsInExactlyOneSplit(Path workspace) {
		Path fixture = writeFixture(workspace.resolve("cover.record"));
		Path prefix = workspace.resolve("cover");
		String stdout = runMain("record", "split",
				"--input", fixture.toString(),
				"--output", prefix.toString(),
				"--split", "70:15:15",
				"--seed", "1").trim();
		long trainCount = countUtf8Lines(RecordSplitter.outputPathFor(prefix, "train"));
		long valCount = countUtf8Lines(RecordSplitter.outputPathFor(prefix, "val"));
		long testCount = countUtf8Lines(RecordSplitter.outputPathFor(prefix, "test"));
		long total = trainCount + valCount + testCount;
		assertEquals(SAMPLE_FENS.size(), (int) total,
				"every input record lands in exactly one split");
		assertTrue(stdout.contains("\"total\":" + SAMPLE_FENS.size()),
				"summary reports the correct total");
	}

	/**
	 * Verifies different seeds produce demonstrably different bucket assignments.
	 *
	 * @param workspace scratch directory
	 */
	private static void testDifferentSeedsProduceDifferentAssignments(Path workspace) {
		Path fixture = writeFixture(workspace.resolve("seed.record"));
		Path prefixA = workspace.resolve("seedA");
		Path prefixB = workspace.resolve("seedB");
		runMain("record", "split",
				"--input", fixture.toString(),
				"--output", prefixA.toString(),
				"--split", "70:15:15",
				"--seed", "1");
		runMain("record", "split",
				"--input", fixture.toString(),
				"--output", prefixB.toString(),
				"--split", "70:15:15",
				"--seed", "2");
		String trainHashA = sha256(RecordSplitter.outputPathFor(prefixA, "train"));
		String trainHashB = sha256(RecordSplitter.outputPathFor(prefixB, "train"));
		assertTrue(!trainHashA.equals(trainHashB),
				"different seeds produce different train splits");
	}

	/**
	 * Verifies a manifest sidecar is written per split and validates against the schema.
	 *
	 * @param workspace scratch directory
	 */
	private static void testManifestSidecarValidatesAgainstSchema(Path workspace) {
		Path fixture = writeFixture(workspace.resolve("manifest.record"));
		Path prefix = workspace.resolve("manifest");
		runMain("record", "split",
				"--input", fixture.toString(),
				"--output", prefix.toString(),
				"--split", "70:15:15",
				"--seed", "1");
		for (String name : List.of("train", "val", "test")) {
			Path split = RecordSplitter.outputPathFor(prefix, name);
			Path manifest = split.resolveSibling(split.getFileName() + ".manifest.json");
			assertTrue(Files.isRegularFile(manifest),
					"manifest sidecar exists for split '" + name + "'");
			runMain("schema", "validate", "crtk.dataset.manifest.v1",
					"--input", manifest.toString());
		}
		Path aggregate = prefix.resolveSibling(prefix.getFileName() + ".split.manifest.json");
		assertTrue(Files.isRegularFile(aggregate),
				"aggregate split manifest exists");
		runMain("schema", "validate", "crtk.dataset.manifest.v1",
				"--input", aggregate.toString());
		String text = readUtf8(aggregate);
		assertContains(text, "\"exporter\": \"record.split.aggregate\"",
				"aggregate manifest exporter");
		assertContains(text, "\"rows_in\": 12",
				"aggregate manifest input row count");
		assertContains(text, "\"row_hash_algorithm\": \"sha256(raw-record-json-v1)\"",
				"aggregate manifest row-hash algorithm");
		assertContains(text, "\"row_hashes_sorted_sha256\":",
				"aggregate manifest row-hash digest");
	}

	/**
	 * Verifies {@code --row-hashes} emits one hash per output row and records the
	 * sidecars in manifests.
	 *
	 * @param workspace scratch directory
	 */
	private static void testRowHashSidecarsAreOptIn(Path workspace) {
		Path fixture = writeFixture(workspace.resolve("rowhash.record"));
		Path prefix = workspace.resolve("rowhash");
		runMain("record", "split",
				"--input", fixture.toString(),
				"--output", prefix.toString(),
				"--split", "70:15:15",
				"--seed", "1",
				"--row-hashes");
		for (String name : List.of("train", "val", "test")) {
			Path split = RecordSplitter.outputPathFor(prefix, name);
			Path sidecar = rowHashPathFor(split);
			assertTrue(Files.isRegularFile(sidecar),
					"row-hash sidecar exists for split '" + name + "'");
			assertEquals((int) countUtf8Lines(split), (int) countUtf8Lines(sidecar),
					"row-hash sidecar has one hash per split row for '" + name + "'");
			assertRowsLookLikeSha256(sidecar);
			Path manifest = split.resolveSibling(split.getFileName() + ".manifest.json");
			assertContains(readUtf8(manifest), sidecar.getFileName().toString(),
					"per-split manifest lists row-hash sidecar for '" + name + "'");
		}
		Path aggregate = prefix.resolveSibling(prefix.getFileName() + ".split.manifest.json");
		String aggregateText = readUtf8(aggregate);
		assertContains(aggregateText, "\"row_hash_sidecars\": true",
				"aggregate manifest records row-hash sidecars");
		assertContains(aggregateText, ".rowhashes.txt",
				"aggregate manifest lists row-hash sidecars");
	}

	/**
	 * Verifies a missing required flag exits as a usage failure.
	 */
	private static void testMissingFlagFailsAsUsage() {
		TestSupport.FailureResult result = runMainExpectFailure("record", "split");
		assertEquals(2, result.exitCode(), "missing required flag exits 2");
		assertTrue(result.stderr().contains("Usage: crtk record split"),
				"stderr shows the usage line");
	}

	/**
	 * Wraps the static splitter for inline use.
	 *
	 * @param fen  position FEN
	 * @param spec split spec
	 * @param seed deterministic seed
	 * @return chosen bucket name
	 */
	private static String bucketFor(String fen, SplitSpec spec, long seed) {
		String json = "{\"created\":1,\"position\":\"" + fen + "\"}";
		return RecordSplitter.bucketFor(json, spec, seed, Strategy.FEN);
	}

	/**
	 * Writes a 12-record JSON-array fixture using {@link #SAMPLE_FENS}.
	 *
	 * @param target destination file
	 * @return target path
	 */
	private static Path writeFixture(Path target) {
		List<String> rows = new java.util.ArrayList<>();
		for (int i = 0; i < SAMPLE_FENS.size(); i++) {
			rows.add("{\"created\":" + (1711171067923L + i)
					+ ",\"position\":\"" + SAMPLE_FENS.get(i) + "\"}");
		}
		return writeJsonArrayFixture(target, rows);
	}

	/**
	 * Returns the SHA-256 hex digest of a file's content.
	 *
	 * @param path file path
	 * @return lowercase hex digest
	 */
	private static String sha256(Path path) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(Files.readAllBytes(path));
			return java.util.HexFormat.of().formatHex(digest.digest());
		} catch (Exception ex) {
			throw new AssertionError("hash failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Computes the row-hash sidecar path for a split output.
	 *
	 * @param splitOutput split JSONL output path
	 * @return row-hash sidecar path
	 */
	private static Path rowHashPathFor(Path splitOutput) {
		String name = splitOutput.getFileName().toString();
		String sidecar = name.endsWith(".jsonl")
				? name.substring(0, name.length() - ".jsonl".length()) + ".rowhashes.txt"
				: name + ".rowhashes.txt";
		return splitOutput.resolveSibling(sidecar);
	}

	/**
	 * Asserts every nonblank row in a sidecar is a SHA-256 hex digest.
	 *
	 * @param sidecar row-hash sidecar
	 */
	private static void assertRowsLookLikeSha256(Path sidecar) {
		try (Stream<String> lines = Files.lines(sidecar, StandardCharsets.UTF_8)) {
			lines.filter(line -> !line.isBlank())
					.forEach(line -> assertTrue(line.matches("[0-9a-f]{64}"),
							"row hash is lowercase SHA-256: " + line));
		} catch (IOException ex) {
			throw new AssertionError("failed to read row-hash sidecar: " + ex.getMessage(), ex);
		}
	}

}
