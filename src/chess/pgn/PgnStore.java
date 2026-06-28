package chess.pgn;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import chess.struct.Game;
import chess.struct.Pgn;
import utility.Json;

/**
 * Append-safe local PGN game store.
 *
 * <h2>Layout</h2>
 * <pre>
 *   &lt;root&gt;/
 *     games.jsonl      // source of truth: one JSON object per game, JSONL
 *     games.idx        // sidecar TSV: gameId &lt;tab&gt; byteOffset
 *     positions.idx    // sidecar TSV: hexSignatureCore &lt;tab&gt; gameId &lt;tab&gt; ply &lt;tab&gt; fen
 *     manifest.json    // versioned manifest: schema versions and counts
 * </pre>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><b>Append-only</b> — writes never modify earlier bytes. The legacy
 *       {@code Writer.appendJsonObjects} seek-rewrite path is intentionally
 *       avoided; this store uses {@link StandardOpenOption#APPEND} so
 *       interleaved readers always see a prefix of valid content.</li>
 *   <li><b>Idempotent on gameId</b> — {@link GameIdentity#compute(Game)} is the
 *       single join key. A second import of the same game is a no-op.</li>
 *   <li><b>FEN-verified lookup</b> — {@code signatureCore} is 64-bit FNV-1a
 *       (collidable). Position queries match by signature and then verify on
 *       FEN string equality, defeating the collision risk.</li>
 *   <li><b>Single-writer</b> — the store doesn't coordinate concurrent
 *       writers; readers are safe in parallel.</li>
 * </ul>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PgnStore {

	/**
	 * Schema identifier for the JSONL game record shape.
	 */
	public static final String GAME_SCHEMA = "crtk.pgn.game.v1";

	/**
	 * Schema identifier for the store manifest shape.
	 */
	public static final String MANIFEST_SCHEMA = "crtk.pgn.store.manifest.v1";

	/**
	 * Tab-delimited row separator used by sidecar indexes.
	 */
	private static final String INDEX_SEPARATOR = "\t";

	/**
	 * Cached gameId → byte-offset map for O(1) lookup.
	 */
	private final Map<String, Long> gameOffsets = new ConcurrentHashMap<>();

	/**
	 * Root directory of the store.
	 */
	private final Path root;

	/**
	 * Path to the JSONL games file.
	 */
	private final Path gamesFile;

	/**
	 * Path to the gameId → offset sidecar.
	 */
	private final Path gamesIndexFile;

	/**
	 * Path to the position index sidecar.
	 */
	private final Path positionsIndexFile;

	/**
	 * Path to the versioned manifest.
	 */
	private final Path manifestFile;

	/**
	 * Path to the tombstone sidecar (gameIds pending compaction).
	 */
	private final Path tombstonesFile;

	/**
	 * In-memory tombstone set (subset of historically-imported gameIds whose
	 * games have been logically deleted but not yet physically removed by
	 * {@link #compact()}).
	 */
	private final java.util.Set<String> tombstones = java.util.Collections.newSetFromMap(
			new java.util.concurrent.ConcurrentHashMap<>());

	/**
	 * Constructs a new store handle without performing I/O.
	 *
	 * @param root store root directory
	 */
	private PgnStore(Path root) {
		this.root = root;
		this.gamesFile = root.resolve("games.jsonl");
		this.gamesIndexFile = root.resolve("games.idx");
		this.positionsIndexFile = root.resolve("positions.idx");
		this.manifestFile = root.resolve("manifest.json");
		this.tombstonesFile = root.resolve("tombstones.idx");
	}

	/**
	 * Opens (or creates) the store rooted at the given directory.
	 *
	 * @param root store root directory
	 * @return open store handle
	 * @throws IOException when the layout cannot be created or read
	 */
	public static PgnStore open(Path root) throws IOException {
		PgnStore store = new PgnStore(root);
		store.bootstrap();
		return store;
	}

	/**
	 * Returns the store's root directory.
	 *
	 * @return root path
	 */
	public Path root() {
		return root;
	}

	/**
	 * Imports every game in a PGN file, returning the ingest report.
	 *
	 * @param pgnFile source PGN file
	 * @return per-file ingest report
	 * @throws IOException when reading or writing fails
	 */
	public ImportReport importPgn(Path pgnFile) throws IOException {
		List<Game> games;
		int malformed = 0;
		try {
			games = Pgn.read(pgnFile);
		} catch (IOException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			return new ImportReport(pgnFile.toString(), 0, 0, 0, 1);
		}
		int imported = 0;
		int duplicates = 0;
		for (Game game : games) {
			ImportOutcome outcome = importGameInternal(game, pgnFile.toString());
			switch (outcome) {
				case IMPORTED -> imported++;
				case DUPLICATE -> duplicates++;
				case MALFORMED -> malformed++;
			}
		}
		writeManifest();
		return new ImportReport(pgnFile.toString(), games.size(), imported, duplicates, malformed);
	}

	/**
	 * Imports every game contained in a PGN text block.
	 *
	 * @param pgnText     PGN content
	 * @param sourceLabel label recorded as the {@code importedFrom} field
	 * @return import report
	 * @throws IOException when writing fails
	 */
	public ImportReport importPgnText(String pgnText, String sourceLabel) throws IOException {
		List<Game> games;
		int malformed = 0;
		try {
			games = Pgn.parseGames(pgnText);
		} catch (RuntimeException ex) {
			return new ImportReport(cleanSourceLabel(sourceLabel), 0, 0, 0, 1);
		}
		int imported = 0;
		int duplicates = 0;
		for (Game game : games) {
			ImportOutcome outcome = importGameInternal(game, cleanSourceLabel(sourceLabel));
			switch (outcome) {
				case IMPORTED -> imported++;
				case DUPLICATE -> duplicates++;
				case MALFORMED -> malformed++;
			}
		}
		writeManifest();
		return new ImportReport(cleanSourceLabel(sourceLabel), games.size(), imported, duplicates, malformed);
	}

	/**
	 * Lists stored games in append order, newest first.
	 *
	 * @param limit maximum number of rows to return; non-positive means all
	 * @return visible stored games, excluding tombstones
	 * @throws IOException when reading fails
	 */
	public List<StoredGame> listGames(int limit) throws IOException {
		if (!Files.exists(gamesFile)) {
			return List.of();
		}
		List<StoredGame> games = new ArrayList<>();
		try (var lines = Files.lines(gamesFile, StandardCharsets.UTF_8)) {
			lines.forEach(line -> {
				StoredGame stored = parseStoredGame(line);
				if (stored != null && !stored.tombstone() && gameOffsets.containsKey(stored.gameId())) {
					games.add(stored);
				}
			});
		}
		Collections.reverse(games);
		if (limit > 0 && games.size() > limit) {
			return List.copyOf(games.subList(0, limit));
		}
		return List.copyOf(games);
	}

	/**
	 * Returns a stored game by its canonical identifier.
	 *
	 * @param gameId canonical game identifier
	 * @return stored game, or empty when absent or tombstoned
	 * @throws IOException when reading fails
	 */
	public Optional<StoredGame> findByGameId(String gameId) throws IOException {
		Long offset = gameOffsets.get(gameId);
		if (offset == null) {
			return Optional.empty();
		}
		String line = readLineAt(offset);
		if (line == null) {
			return Optional.empty();
		}
		StoredGame stored = parseStoredGame(line);
		return (stored == null || stored.tombstone()) ? Optional.empty() : Optional.of(stored);
	}

	/**
	 * Returns every stored game that contains a position equal to the given FEN.
	 *
	 * <p>The position index is scanned for matching {@code signatureCore}
	 * entries; each match is verified against the FEN string before its game
	 * id is returned, so FNV-1a collisions cannot return a wrong game.</p>
	 *
	 * @param fen position FEN to look up
	 * @return list of stored games (deduped on game id, in first-occurrence order)
	 * @throws IOException when reading fails
	 */
	public List<StoredGame> findByFen(String fen) throws IOException {
		long target = new chess.core.Position(fen).signatureCore();
		String hexTarget = Long.toHexString(target);
		List<String> orderedIds = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		if (!Files.exists(positionsIndexFile)) {
			return List.of();
		}
		try (var lines = Files.lines(positionsIndexFile, StandardCharsets.UTF_8)) {
			lines.forEach(line -> {
				String[] parts = line.split(INDEX_SEPARATOR, 4);
				if (parts.length != 4) {
					return;
				}
				if (!parts[0].equals(hexTarget)) {
					return;
				}
				if (!parts[3].equals(fen)) {
					return;
				}
				String gameId = parts[1];
				if (seen.add(gameId)) {
					orderedIds.add(gameId);
				}
			});
		}
		List<StoredGame> results = new ArrayList<>(orderedIds.size());
		for (String id : orderedIds) {
			findByGameId(id).ifPresent(results::add);
		}
		return results;
	}

	/**
	 * Returns aggregate statistics about the store.
	 *
	 * @return store statistics snapshot
	 * @throws IOException when reading the position index fails
	 */
	public Stats stats() throws IOException {
		long positionCount = Files.exists(positionsIndexFile)
				? countLines(positionsIndexFile)
				: 0L;
		long gameCount = gameOffsets.size();
		return new Stats(gameCount, positionCount, tombstones.size(), root,
				GAME_SCHEMA, MANIFEST_SCHEMA);
	}

	/**
	 * Tombstones a stored game so subsequent reads hide it. The bytes remain
	 * in {@code games.jsonl} until {@link #compact()} runs.
	 *
	 * @param gameId canonical game identifier to tombstone
	 * @return {@code true} when the game existed and was newly tombstoned,
	 *         {@code false} when it was already tombstoned or never present
	 * @throws IOException when writing the tombstone sidecar fails
	 */
	public boolean delete(String gameId) throws IOException {
		if (gameId == null || gameId.isBlank()) {
			return false;
		}
		if (!gameOffsets.containsKey(gameId)) {
			return false;
		}
		gameOffsets.remove(gameId);
		if (!tombstones.add(gameId)) {
			return false;
		}
		appendIndexLine(tombstonesFile, gameId);
		writeManifest();
		return true;
	}

	/**
	 * Physically removes every tombstoned row, rebuilds the sidecar indexes,
	 * and atomically replaces the on-disk files. After compaction the store
	 * has no pending tombstones.
	 *
	 * @return compaction report
	 * @throws IOException when reading or writing fails
	 */
	public CompactionReport compact() throws IOException {
		long startingGames = gameOffsets.size();
		long startingPositions = Files.exists(positionsIndexFile)
				? countLines(positionsIndexFile)
				: 0L;
		long tombstonesDropped = tombstones.size();
		if (tombstonesDropped == 0) {
			writeManifest();
			return new CompactionReport(startingGames, startingPositions,
					startingGames, startingPositions, 0L);
		}
		Path newGames = root.resolve("games.jsonl.compact");
		Path newGamesIdx = root.resolve("games.idx.compact");
		Path newPositions = root.resolve("positions.idx.compact");
		java.util.Set<String> survivors = new java.util.HashSet<>(gameOffsets.keySet());
		try {
			rewriteGames(survivors, newGames, newGamesIdx);
			rewritePositions(survivors, newPositions);
		} catch (IOException ex) {
			Files.deleteIfExists(newGames);
			Files.deleteIfExists(newGamesIdx);
			Files.deleteIfExists(newPositions);
			throw ex;
		}
		Files.move(newGames, gamesFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
				java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		Files.move(newGamesIdx, gamesIndexFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
				java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		Files.move(newPositions, positionsIndexFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
				java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		Files.deleteIfExists(tombstonesFile);
		tombstones.clear();
		reloadOffsetCacheFromIndex();
		writeManifest();
		long endingPositions = Files.exists(positionsIndexFile)
				? countLines(positionsIndexFile)
				: 0L;
		return new CompactionReport(startingGames, startingPositions,
				gameOffsets.size(), endingPositions, tombstonesDropped);
	}

	/**
	 * Rewrites {@code games.jsonl} and {@code games.idx} from scratch,
	 * keeping only games whose id is in the survivor set.
	 *
	 * @param survivors    surviving game ids
	 * @param newGames     destination games file
	 * @param newGamesIdx  destination games-index file
	 * @throws IOException when reading or writing fails
	 */
	private void rewriteGames(java.util.Set<String> survivors,
			Path newGames, Path newGamesIdx) throws IOException {
		try (java.io.OutputStream gamesOut = Files.newOutputStream(newGames,
						java.nio.file.StandardOpenOption.CREATE_NEW,
						java.nio.file.StandardOpenOption.WRITE);
				java.io.OutputStream idxOut = Files.newOutputStream(newGamesIdx,
						java.nio.file.StandardOpenOption.CREATE_NEW,
						java.nio.file.StandardOpenOption.WRITE);
				java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(Files.newInputStream(gamesFile),
								StandardCharsets.UTF_8))) {
			String line;
			long writtenOffset = 0L;
			while ((line = reader.readLine()) != null) {
				String gameId = utility.Json.parseStringField(line, "gameId");
				if (gameId == null || !survivors.contains(gameId)) {
					continue;
				}
				byte[] payload = (line + "\n").getBytes(StandardCharsets.UTF_8);
				gamesOut.write(payload);
				byte[] idxRow = (gameId + INDEX_SEPARATOR + writtenOffset + "\n")
						.getBytes(StandardCharsets.UTF_8);
				idxOut.write(idxRow);
				writtenOffset += payload.length;
			}
		}
	}

	/**
	 * Rewrites {@code positions.idx} from scratch, keeping only rows whose
	 * gameId is in the survivor set.
	 *
	 * @param survivors    surviving game ids
	 * @param newPositions destination positions-index file
	 * @throws IOException when reading or writing fails
	 */
	private void rewritePositions(java.util.Set<String> survivors, Path newPositions) throws IOException {
		try (java.io.OutputStream out = Files.newOutputStream(newPositions,
						java.nio.file.StandardOpenOption.CREATE_NEW,
						java.nio.file.StandardOpenOption.WRITE);
				java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(Files.newInputStream(positionsIndexFile),
								StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split(INDEX_SEPARATOR, 4);
				if (parts.length != 4) {
					continue;
				}
				if (!survivors.contains(parts[1])) {
					continue;
				}
				out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
			}
		}
	}

	/**
	 * Empties and re-populates {@link #gameOffsets} from the current
	 * {@code games.idx} sidecar (used after {@link #compact()}).
	 *
	 * @throws IOException when reading fails
	 */
	private void reloadOffsetCacheFromIndex() throws IOException {
		gameOffsets.clear();
		if (!Files.exists(gamesIndexFile)) {
			return;
		}
		try (var lines = Files.lines(gamesIndexFile, StandardCharsets.UTF_8)) {
			lines.forEach(line -> {
				int tab = line.indexOf(INDEX_SEPARATOR);
				if (tab > 0) {
					try {
						gameOffsets.put(line.substring(0, tab),
								Long.parseLong(line.substring(tab + 1).strip()));
					} catch (NumberFormatException ignored) {
						// corrupt row; skipped
					}
				}
			});
		}
	}

	/**
	 * Imports a single in-memory game.
	 *
	 * @param game        source game
	 * @param sourceLabel label recorded as the {@code importedFrom} field
	 * @return import outcome for the game
	 * @throws IOException when writing fails
	 */
	private ImportOutcome importGameInternal(Game game, String sourceLabel) throws IOException {
		String gameId;
		String pgnBlob;
		List<PositionWalker.PositionObservation> observations;
		try {
			gameId = GameIdentity.compute(game);
			pgnBlob = Pgn.toPgn(game);
			observations = PositionWalker.walkMainline(game);
		} catch (RuntimeException ex) {
			return ImportOutcome.MALFORMED;
		}
		if (gameOffsets.containsKey(gameId)) {
			return ImportOutcome.DUPLICATE;
		}
		String line = renderGameLine(gameId, game.getTags(), pgnBlob, sourceLabel);
		long offset = appendLine(gamesFile, line);
		gameOffsets.put(gameId, offset);
		appendIndexLine(gamesIndexFile, gameId + INDEX_SEPARATOR + offset);
		appendObservations(gameId, observations);
		return ImportOutcome.IMPORTED;
	}

	/**
	 * Normalizes optional source labels for reports and stored metadata.
	 *
	 * @param sourceLabel raw label
	 * @return non-null trimmed label
	 */
	private static String cleanSourceLabel(String sourceLabel) {
		return sourceLabel == null ? "" : sourceLabel.trim();
	}

	/**
	 * Appends every position observation for one game to the position index.
	 *
	 * @param gameId       canonical game identifier
	 * @param observations ordered position observations
	 * @throws IOException when writing fails
	 */
	private void appendObservations(String gameId, List<PositionWalker.PositionObservation> observations)
			throws IOException {
		StringBuilder batch = new StringBuilder(observations.size() * 96);
		for (PositionWalker.PositionObservation observation : observations) {
			batch.append(Long.toHexString(observation.signatureCore()))
					.append(INDEX_SEPARATOR).append(gameId)
					.append(INDEX_SEPARATOR).append(observation.ply())
					.append(INDEX_SEPARATOR).append(observation.fen())
					.append('\n');
		}
		Files.write(positionsIndexFile, batch.toString().getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
	}

	/**
	 * Ensures the layout exists and primes the in-memory offset cache.
	 *
	 * @throws IOException when the layout cannot be prepared
	 */
	private void bootstrap() throws IOException {
		Files.createDirectories(root);
		ensureRegularFile(gamesFile);
		ensureRegularFile(gamesIndexFile);
		ensureRegularFile(positionsIndexFile);
		if (Files.exists(manifestFile)) {
			// nothing to verify here yet — a future version bump can validate
			// the on-disk schemaVersion against MANIFEST_SCHEMA.
		}
		try (var lines = Files.lines(gamesIndexFile, StandardCharsets.UTF_8)) {
			lines.forEach(line -> {
				int tab = line.indexOf(INDEX_SEPARATOR);
				if (tab > 0) {
					try {
						gameOffsets.put(line.substring(0, tab),
								Long.parseLong(line.substring(tab + 1).strip()));
					} catch (NumberFormatException ignored) {
						// corrupt row; skipped — compact() will normalise later
					}
				}
			});
		}
		if (Files.exists(tombstonesFile)) {
			try (var lines = Files.lines(tombstonesFile, StandardCharsets.UTF_8)) {
				lines.forEach(line -> {
					String id = line.strip();
					if (!id.isEmpty()) {
						tombstones.add(id);
						gameOffsets.remove(id);
					}
				});
			}
		}
	}

	/**
	 * Ensures a regular file exists at the given path, creating it if missing.
	 *
	 * @param file target path
	 * @throws IOException when creation fails
	 */
	private static void ensureRegularFile(Path file) throws IOException {
		if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
			Files.createFile(file);
		}
	}

	/**
	 * Appends a JSONL line to a file and returns the byte offset of the line.
	 *
	 * @param file target file
	 * @param line line text without the trailing newline
	 * @return byte offset at which the line begins
	 * @throws IOException when writing fails
	 */
	private static long appendLine(Path file, String line) throws IOException {
		long offset = Files.size(file);
		byte[] payload = (line + "\n").getBytes(StandardCharsets.UTF_8);
		OpenOption[] options = new OpenOption[] {
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
				StandardOpenOption.APPEND };
		Files.write(file, payload, options);
		return offset;
	}

	/**
	 * Appends a tab-separated index row to the given sidecar file.
	 *
	 * @param file target sidecar file
	 * @param row  row text without the trailing newline
	 * @throws IOException when writing fails
	 */
	private static void appendIndexLine(Path file, String row) throws IOException {
		byte[] payload = (row + "\n").getBytes(StandardCharsets.UTF_8);
		Files.write(file, payload,
				StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
	}

	/**
	 * Reads a single JSONL line beginning at the given byte offset.
	 *
	 * @param offset byte offset of the line start
	 * @return decoded line text without the trailing newline, or {@code null} when out-of-range
	 * @throws IOException when reading fails
	 */
	private String readLineAt(long offset) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(gamesFile.toFile(), "r")) {
			if (offset >= raf.length()) {
				return null;
			}
			raf.seek(offset);
			java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
			int b;
			while ((b = raf.read()) != -1 && b != '\n') {
				buffer.write(b);
			}
			return buffer.toString(StandardCharsets.UTF_8);
		}
	}

	/**
	 * Counts the number of lines in a regular file.
	 *
	 * @param file file path
	 * @return line count
	 * @throws IOException when reading fails
	 */
	private static long countLines(Path file) throws IOException {
		try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
			return lines.count();
		}
	}

	/**
	 * Writes (or rewrites) the store manifest from the current in-memory state.
	 *
	 * @throws IOException when writing fails
	 */
	private void writeManifest() throws IOException {
		StringBuilder sb = new StringBuilder(256);
		sb.append("{\n");
		sb.append("  \"schemaVersion\": \"").append(MANIFEST_SCHEMA).append("\",\n");
		sb.append("  \"gameSchemaVersion\": \"").append(GAME_SCHEMA).append("\",\n");
		sb.append("  \"gameCount\": ").append(gameOffsets.size()).append(",\n");
		sb.append("  \"positionCount\": ").append(Files.exists(positionsIndexFile)
				? countLines(positionsIndexFile)
				: 0L).append(",\n");
		sb.append("  \"tombstoneCount\": ").append(tombstones.size()).append('\n');
		sb.append("}\n");
		Files.writeString(manifestFile, sb.toString(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE);
	}

	/**
	 * Renders one game's JSONL record line.
	 *
	 * @param gameId      canonical game id
	 * @param headers     game headers
	 * @param pgnBlob     full PGN text
	 * @param sourceLabel import-source label
	 * @return JSONL line (no trailing newline)
	 */
	private static String renderGameLine(String gameId, Map<String, String> headers, String pgnBlob,
			String sourceLabel) {
		StringBuilder sb = new StringBuilder(256 + pgnBlob.length());
		sb.append('{');
		sb.append("\"schemaVersion\":\"").append(GAME_SCHEMA).append('"');
		sb.append(",\"gameId\":\"").append(gameId).append('"');
		sb.append(",\"headers\":").append(renderHeaders(headers));
		sb.append(",\"pgn\":\"").append(Json.esc(pgnBlob)).append('"');
		sb.append(",\"tombstone\":false");
		if (sourceLabel != null && !sourceLabel.isEmpty()) {
			sb.append(",\"importedFrom\":\"").append(Json.esc(sourceLabel)).append('"');
		}
		sb.append('}');
		return sb.toString();
	}

	/**
	 * Renders a sorted-by-key JSON object for the game's headers.
	 *
	 * @param headers source headers
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

	/**
	 * Parses one stored-game JSONL line.
	 *
	 * @param line JSONL line text
	 * @return parsed stored game, or {@code null} when parsing fails
	 */
	private StoredGame parseStoredGame(String line) {
		String gameId = Json.parseStringField(line, "gameId");
		String pgn = Json.parseStringField(line, "pgn");
		String importedFrom = Json.parseStringField(line, "importedFrom");
		if (gameId == null || pgn == null) {
			return null;
		}
		boolean tombstone = line.contains("\"tombstone\":true");
		List<Game> parsed;
		try {
			parsed = Pgn.parseGames(pgn);
		} catch (RuntimeException ex) {
			return null;
		}
		Game game = parsed.isEmpty() ? null : parsed.get(0);
		Map<String, String> headers = game == null ? new LinkedHashMap<>() : game.getTags();
		return new StoredGame(gameId, game, pgn,
				Collections.unmodifiableMap(headers), tombstone, importedFrom);
	}

	/**
	 * Per-record outcome of a game import.
	 */
	private enum ImportOutcome {

		/**
		 * The game was a duplicate and not appended.
		 */
		DUPLICATE,

		/**
		 * The game was successfully appended.
		 */
		IMPORTED,

		/**
		 * The game could not be normalised or its identifier could not be computed.
		 */
		MALFORMED;
	}

	/**
	 * Per-file PGN ingest report.
	 *
	 * @param file         source file path
	 * @param gamesParsed  number of games successfully parsed from the source
	 * @param imported     number of games newly added to the store
	 * @param duplicates   number of games skipped because they were already present
	 * @param malformed    number of games that could not be normalised
	 */
	public record ImportReport(String file, int gamesParsed, int imported, int duplicates, int malformed) {
	}

	/**
	 * Aggregate statistics snapshot.
	 *
	 * @param gameCount             number of indexed games (excludes tombstones)
	 * @param positionCount         number of position observations
	 *                              (includes still-on-disk tombstoned rows
	 *                              pending compaction)
	 * @param tombstoneCount        number of games tombstoned but not yet compacted
	 * @param root                  store root directory
	 * @param gameSchemaVersion     stored-game schema version
	 * @param manifestSchemaVersion store-manifest schema version
	 */
	public record Stats(long gameCount, long positionCount, long tombstoneCount, Path root,
			String gameSchemaVersion, String manifestSchemaVersion) {
	}

	/**
	 * Per-compaction outcome describing the size delta produced by the run.
	 *
	 * @param gameCountBefore      games visible before compaction
	 * @param positionCountBefore  position observations on disk before compaction
	 * @param gameCountAfter       games visible after compaction
	 * @param positionCountAfter   position observations on disk after compaction
	 * @param tombstonesDropped    tombstoned game ids dropped by the run
	 */
	public record CompactionReport(long gameCountBefore, long positionCountBefore,
			long gameCountAfter, long positionCountAfter, long tombstonesDropped) {
	}

	/**
	 * Materialised stored-game view.
	 *
	 * @param gameId       canonical game identifier
	 * @param game         parsed game (may be {@code null} when reconstruction failed)
	 * @param pgn          full PGN text as stored
	 * @param headers      immutable header map
	 * @param tombstone    {@code true} when the row has been logically deleted
	 * @param importedFrom recorded source label or {@code null}
	 */
	public record StoredGame(String gameId, Game game, String pgn, Map<String, String> headers,
			boolean tombstone, String importedFrom) {
	}
}
