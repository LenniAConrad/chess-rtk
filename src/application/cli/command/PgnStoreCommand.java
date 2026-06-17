package application.cli.command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import chess.pgn.PgnStore;
import chess.struct.Pgn;
import utility.Argv;
import utility.Json;

/**
 * CLI handlers for the {@code crtk pgn} area — the local PGN game store.
 *
 * <p>Verbs mirror the canonical noun-then-verb shape: {@code import} loads
 * games from a PGN file (idempotent on {@code gameId}), {@code find} returns
 * games whose mainline passes through a given FEN (FEN-verified to defeat
 * FNV-1a collisions), {@code show} renders one game by id, and {@code stats}
 * summarises the store.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PgnStoreCommand {

	/**
	 * Default store location relative to the working directory.
	 */
	private static final Path DEFAULT_STORE = Path.of("dump", "pgn-store");

	/**
	 * Exit code used for argument-shape failures.
	 */
	private static final int USAGE_FAILURE_EXIT = 2;

	/**
	 * Exit code used for input failures (unreadable file, unknown id, etc.).
	 */
	private static final int INPUT_FAILURE_EXIT = 3;

	/**
	 * Utility class; prevent instantiation.
	 */
	private PgnStoreCommand() {
		// utility
	}

	/**
	 * Handles {@code crtk pgn import --input PATH [--store DIR]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runImport(Argv argv) {
		Path input = argv.path("--input", "-i");
		Path storeRoot = resolveStore(argv);
		argv.ensureConsumed();
		if (input == null) {
			throw new CommandFailure(
					"Usage: crtk pgn import --input PATH [--store DIR]",
					USAGE_FAILURE_EXIT);
		}
		try {
			PgnStore store = PgnStore.open(storeRoot);
			PgnStore.ImportReport report = store.importPgn(input);
			System.out.println(renderImportReportJson(report));
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk pgn import: failed to import: " + ex.getMessage(),
					INPUT_FAILURE_EXIT);
		}
	}

	/**
	 * Handles {@code crtk pgn show --gameId ID [--store DIR] [--format pgn|json]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runShow(Argv argv) {
		String gameId = argv.string("--gameId", "--game-id");
		String format = argv.string("--format");
		Path storeRoot = resolveStore(argv);
		argv.ensureConsumed();
		if (gameId == null || gameId.isBlank()) {
			throw new CommandFailure(
					"Usage: crtk pgn show --gameId ID [--store DIR] [--format pgn|json]",
					USAGE_FAILURE_EXIT);
		}
		String resolvedFormat = format == null ? "pgn" : format.toLowerCase(Locale.ROOT);
		if (!"pgn".equals(resolvedFormat) && !"json".equals(resolvedFormat)) {
			throw new CommandFailure(
					"crtk pgn show: --format must be pgn or json (got " + format + ")",
					USAGE_FAILURE_EXIT);
		}
		try {
			PgnStore store = PgnStore.open(storeRoot);
			Optional<PgnStore.StoredGame> result = store.findByGameId(gameId);
			if (result.isEmpty()) {
				throw new CommandFailure(
						"crtk pgn show: no game with id " + gameId,
						INPUT_FAILURE_EXIT);
			}
			PgnStore.StoredGame stored = result.get();
			if ("pgn".equals(resolvedFormat)) {
				System.out.println(stored.game() == null ? stored.pgn() : Pgn.toPgn(stored.game()));
			} else {
				System.out.println(renderStoredGameJson(stored));
			}
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk pgn show: failed to read store: " + ex.getMessage(),
					INPUT_FAILURE_EXIT);
		}
	}

	/**
	 * Handles {@code crtk pgn find --fen FEN [--store DIR] [--limit N]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runFind(Argv argv) {
		String fen = argv.string("--fen");
		Integer limit = argv.integer("--limit");
		Path storeRoot = resolveStore(argv);
		argv.ensureConsumed();
		if (fen == null || fen.isBlank()) {
			throw new CommandFailure(
					"Usage: crtk pgn find --fen FEN [--store DIR] [--limit N]",
					USAGE_FAILURE_EXIT);
		}
		try {
			PgnStore store = PgnStore.open(storeRoot);
			List<PgnStore.StoredGame> matches = store.findByFen(fen);
			int cap = limit == null || limit < 0 ? matches.size() : Math.min(limit, matches.size());
			StringBuilder sb = new StringBuilder().append("{\"matches\":[");
			for (int i = 0; i < cap; i++) {
				if (i > 0) {
					sb.append(',');
				}
				sb.append(renderMatchSummaryJson(matches.get(i)));
			}
			sb.append("],\"matchCount\":").append(matches.size())
					.append(",\"returned\":").append(cap).append('}');
			System.out.println(sb);
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk pgn find: failed to read store: " + ex.getMessage(),
					INPUT_FAILURE_EXIT);
		}
	}

	/**
	 * Handles {@code crtk pgn delete --gameId ID [--store DIR]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runDelete(Argv argv) {
		String gameId = argv.string("--gameId", "--game-id");
		Path storeRoot = resolveStore(argv);
		argv.ensureConsumed();
		if (gameId == null || gameId.isBlank()) {
			throw new CommandFailure(
					"Usage: crtk pgn delete --gameId ID [--store DIR]",
					USAGE_FAILURE_EXIT);
		}
		try {
			PgnStore store = PgnStore.open(storeRoot);
			boolean removed = store.delete(gameId);
			System.out.println("{\"gameId\":\"" + gameId
					+ "\",\"tombstoned\":" + removed + "}");
			if (!removed) {
				throw new CommandFailure(
						"crtk pgn delete: no game with id " + gameId,
						INPUT_FAILURE_EXIT);
			}
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk pgn delete: failed to write tombstone: " + ex.getMessage(),
					INPUT_FAILURE_EXIT);
		}
	}

	/**
	 * Handles {@code crtk pgn compact [--store DIR]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runCompact(Argv argv) {
		Path storeRoot = resolveStore(argv);
		argv.ensureConsumed();
		try {
			PgnStore store = PgnStore.open(storeRoot);
			PgnStore.CompactionReport report = store.compact();
			StringBuilder sb = new StringBuilder().append('{');
			sb.append("\"root\":\"").append(Json.esc(storeRoot.toString())).append('"');
			sb.append(",\"gameCountBefore\":").append(report.gameCountBefore());
			sb.append(",\"gameCountAfter\":").append(report.gameCountAfter());
			sb.append(",\"positionCountBefore\":").append(report.positionCountBefore());
			sb.append(",\"positionCountAfter\":").append(report.positionCountAfter());
			sb.append(",\"tombstonesDropped\":").append(report.tombstonesDropped());
			sb.append('}');
			System.out.println(sb);
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk pgn compact: failed to compact store: " + ex.getMessage(),
					INPUT_FAILURE_EXIT);
		}
	}

	/**
	 * Handles {@code crtk pgn stats [--store DIR]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runStats(Argv argv) {
		Path storeRoot = resolveStore(argv);
		argv.ensureConsumed();
		try {
			PgnStore store = PgnStore.open(storeRoot);
			PgnStore.Stats stats = store.stats();
			StringBuilder sb = new StringBuilder().append('{');
			sb.append("\"root\":\"").append(Json.esc(stats.root().toString())).append('"');
			sb.append(",\"gameSchemaVersion\":\"").append(stats.gameSchemaVersion()).append('"');
			sb.append(",\"manifestSchemaVersion\":\"").append(stats.manifestSchemaVersion()).append('"');
			sb.append(",\"gameCount\":").append(stats.gameCount());
			sb.append(",\"positionCount\":").append(stats.positionCount());
			sb.append(",\"tombstoneCount\":").append(stats.tombstoneCount());
			sb.append('}');
			System.out.println(sb);
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk pgn stats: failed to read store: " + ex.getMessage(),
					INPUT_FAILURE_EXIT);
		}
	}

	/**
	 * Resolves the store path from {@code --store}, the {@code CRTK_PGN_STORE}
	 * environment variable, or the working-directory default.
	 *
	 * @param argv parsed arguments
	 * @return resolved store root
	 */
	private static Path resolveStore(Argv argv) {
		Path explicit = argv.path("--store");
		if (explicit != null) {
			return explicit;
		}
		String env = System.getenv("CRTK_PGN_STORE");
		if (env != null && !env.isBlank()) {
			return Path.of(env);
		}
		return DEFAULT_STORE;
	}

	/**
	 * Renders the per-file ingest report as a single JSON object.
	 *
	 * @param report ingest report
	 * @return single-line JSON object
	 */
	private static String renderImportReportJson(PgnStore.ImportReport report) {
		return "{\"file\":\"" + Json.esc(report.file()) + "\""
				+ ",\"games_parsed\":" + report.gamesParsed()
				+ ",\"imported\":" + report.imported()
				+ ",\"duplicates\":" + report.duplicates()
				+ ",\"malformed\":" + report.malformed()
				+ "}";
	}

	/**
	 * Renders a stored game's headers and PGN as JSON.
	 *
	 * @param stored stored game
	 * @return single-line JSON object
	 */
	private static String renderStoredGameJson(PgnStore.StoredGame stored) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"gameId\":\"").append(stored.gameId()).append('"');
		sb.append(",\"headers\":").append(renderHeaders(stored.headers()));
		sb.append(",\"pgn\":\"").append(Json.esc(stored.pgn())).append('"');
		sb.append(",\"tombstone\":").append(stored.tombstone());
		sb.append('}');
		return sb.toString();
	}

	/**
	 * Renders a short summary suitable for {@code find} match lists.
	 *
	 * @param stored stored game
	 * @return single-line JSON object
	 */
	private static String renderMatchSummaryJson(PgnStore.StoredGame stored) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"gameId\":\"").append(stored.gameId()).append('"');
		sb.append(",\"headers\":").append(renderHeaders(stored.headers()));
		sb.append('}');
		return sb.toString();
	}

	/**
	 * Renders the headers map as a sorted JSON object.
	 *
	 * @param headers headers map
	 * @return JSON object literal
	 */
	private static String renderHeaders(Map<String, String> headers) {
		TreeMap<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		if (headers != null) {
			sorted.putAll(headers);
		}
		StringBuilder sb = new StringBuilder().append('{');
		boolean first = true;
		for (Map.Entry<String, String> entry : sorted.entrySet()) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append('"').append(Json.esc(entry.getKey())).append("\":\"")
					.append(Json.esc(entry.getValue() == null ? "" : entry.getValue()))
					.append('"');
		}
		return sb.append('}').toString();
	}
}
