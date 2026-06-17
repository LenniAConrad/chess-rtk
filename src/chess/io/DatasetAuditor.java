package chess.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Recursively walks a directory tree, verifies every
 * {@code *.manifest.json} sidecar found, and returns an aggregated report.
 *
 * <p>This is the "every manifest under this dir" counterpart to
 * {@link DatasetVerifier#verify(Path)}. The walk is deterministic — entries
 * are sorted by path before verification — so the same tree always
 * audits to the same per-manifest order.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetAuditor {

	/**
	 * Manifest filename suffix recognised by the auditor.
	 */
	private static final String MANIFEST_SUFFIX = ".manifest.json";

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetAuditor() {
		// utility
	}

	/**
	 * Walks the given root and verifies every manifest discovered, capped at
	 * {@code limit} entries (a non-positive value means no cap).
	 *
	 * @param root  directory to walk
	 * @param limit maximum number of manifests to verify, or {@code 0} for none-cap
	 * @return aggregated audit report
	 * @throws IOException when walking the directory fails
	 */
	public static AuditReport audit(Path root, int limit) throws IOException {
		List<Path> manifests = discoverManifests(root, limit);
		List<DatasetVerifier.Report> reports = new ArrayList<>(manifests.size());
		int oks = 0;
		int problems = 0;
		for (Path manifest : manifests) {
			DatasetVerifier.Report report;
			try {
				report = DatasetVerifier.verify(manifest);
			} catch (IOException ex) {
				report = parseFailure(manifest, "failed to read manifest: " + ex.getMessage());
			}
			reports.add(report);
			if (report.ok()) {
				oks++;
			} else {
				problems++;
			}
		}
		return new AuditReport(root, manifests.size(), oks, problems, List.copyOf(reports));
	}

	/**
	 * Returns whether a path looks like a manifest filename.
	 *
	 * @param path candidate path
	 * @return {@code true} when the basename ends with {@code .manifest.json}
	 */
	public static boolean isManifest(Path path) {
		return path.getFileName().toString().endsWith(MANIFEST_SUFFIX);
	}

	/**
	 * Enumerates every manifest file under the given root, sorted by path.
	 *
	 * @param root  directory to walk
	 * @param limit maximum number of manifests to return, or {@code 0} for no cap
	 * @return discovered manifest paths
	 * @throws IOException when walking fails
	 */
	private static List<Path> discoverManifests(Path root, int limit) throws IOException {
		if (!Files.isDirectory(root)) {
			throw new IOException("audit root '" + root + "' is not a directory");
		}
		try (Stream<Path> walk = Files.walk(root)) {
			Stream<Path> filtered = walk
					.filter(Files::isRegularFile)
					.filter(DatasetAuditor::isManifest)
					.sorted(Comparator.naturalOrder());
			if (limit > 0) {
				filtered = filtered.limit(limit);
			}
			return filtered.toList();
		}
	}

	/**
	 * Builds a parse-failure report directly when the verifier cannot even
	 * open the manifest file.
	 *
	 * @param manifest manifest file path
	 * @param message  diagnostic message
	 * @return parse-failure report
	 */
	private static DatasetVerifier.Report parseFailure(Path manifest, String message) {
		return new DatasetVerifier.Report(manifest, DatasetVerifier.Outcome.PARSE_FAILURE,
				List.of(), List.of(), List.of(), List.of(), message);
	}

	/**
	 * Aggregated audit report.
	 *
	 * @param root     audit root
	 * @param total    number of manifests discovered
	 * @param oks      number of manifests that verified clean
	 * @param problems number of manifests that failed verification
	 * @param reports  per-manifest verification reports (in discovery order)
	 */
	public record AuditReport(Path root, int total, int oks, int problems,
			List<DatasetVerifier.Report> reports) {

		/**
		 * Indicates whether every audited manifest verified clean.
		 *
		 * @return {@code true} when no manifest failed
		 */
		public boolean ok() {
			return problems == 0;
		}
	}
}
