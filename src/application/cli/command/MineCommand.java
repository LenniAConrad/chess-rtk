package application.cli.command;

import static application.cli.Constants.OPT_CHESS960;
import static application.cli.Constants.OPT_CHESS960_SHORT;
import static application.cli.Constants.OPT_ENGINE_INSTANCES;
import static application.cli.Constants.OPT_ENGINE_INSTANCES_SHORT;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_FRONTIER;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_MAX_TOTAL;
import static application.cli.Constants.OPT_MAX_WAVES;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_PROTOCOL_PATH_SHORT;
import static application.cli.Constants.OPT_PUZZLE_ACCELERATE;
import static application.cli.Constants.OPT_PUZZLE_DRAWING;
import static application.cli.Constants.OPT_PUZZLE_QUALITY;
import static application.cli.Constants.OPT_PUZZLE_WINNING;
import static application.cli.Constants.OPT_RANDOM_COUNT;
import static application.cli.Constants.OPT_RANDOM_INFINITE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.PathOps.ensureParentDir;
import static application.cli.PgnOps.extractRecordsWithVariations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import application.Config;
import application.Pool;
import chess.core.Position;
import chess.core.Setup;
import chess.debug.LogService;
import chess.io.Reader;
import chess.io.Writer;
import chess.struct.Pgn;
import chess.struct.Record;
import chess.uci.Filter;
import chess.uci.Filter.FilterDSL;
import utility.Argv;

/**
 * Implements the {@code mine-puzzles} subcommand.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MineCommand {

	/**
	 * Prefix used to build fast JSON objects that start with a {@code kind} field.
	 *
	 * <p>
	 * This avoids a formatter by splicing the prefix with a kind value and the
	 * remaining JSON payload.
	 * </p>
	 */
	private static final String KIND_JSON_PREFIX = "{\"kind\":\"";

	/**
	 * Used for default limiting the number of waves executed.
	 */
	private static final int DEFAULT_MAX_WAVES = 100;

	/**
	 * Used for default capping the number of records per frontier.
	 */
	private static final int DEFAULT_MAX_FRONTIER = 5_000;

	/**
	 * Used for default capping the total number of processed records.
	 */
	private static final long DEFAULT_MAX_TOTAL = 500_000;

	/**
	 * Used for default random seed count when none are provided.
	 */
	private static final int DEFAULT_RANDOM_SEEDS = 100;

	/**
	 * Utility class; prevent instantiation.
	 */
	private MineCommand() {
		// utility
	}

	/**
	 * Handles {@code mine-puzzles}.
	 *
	 * <p>
	 * Resolves runtime configuration and filters, loads or generates seed
	 * positions,
	 * runs batched engine analysis in bounded waves, expands verified puzzles, and
	 * appends
	 * JSONL outputs for puzzles and non-puzzles.
	 * </p>
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runMine(Argv a) {
		final boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		final boolean chess960 = a.flag(OPT_CHESS960, OPT_CHESS960_SHORT);
		final Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		final String outRoot = CommandSupport.optional(a.string(OPT_OUTPUT, OPT_OUTPUT_SHORT), Config.getOutput());
		final String proto = CommandSupport.optional(a.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT),
				Config.getProtocolPath());
		final long engineInstances = CommandSupport.optional(a.lng(OPT_ENGINE_INSTANCES, OPT_ENGINE_INSTANCES_SHORT),
				Config.getEngineInstances());
		final long nodesCap = Math.max(1, CommandSupport.optional(a.lng(OPT_MAX_NODES), Config.getMaxNodes()));
		final long durMs = Math.max(
				1,
				CommandSupport.optionalDurationMs(a.duration(OPT_MAX_DURATION), Config.getMaxDuration()));
		final Long randomSeedOverride = a.lng(OPT_RANDOM_COUNT);
		final boolean randomInfinite = a.flag(OPT_RANDOM_INFINITE);
		final Long maxWavesOverride = a.lng(OPT_MAX_WAVES);
		final Long maxFrontierOverride = a.lng(OPT_MAX_FRONTIER);
		final Long maxTotalOverride = a.lng(OPT_MAX_TOTAL);

		final String qGate = a.string(OPT_PUZZLE_QUALITY);
		final String wGate = a.string(OPT_PUZZLE_WINNING);
		final String dGate = a.string(OPT_PUZZLE_DRAWING);
		final String accelDsl = a.string(OPT_PUZZLE_ACCELERATE);

		final Filter accel = filterOrDefault(accelDsl, Config::getPuzzleAccelerate);
		final Filter qF = filterOrDefault(qGate, Config::getPuzzleQuality);
		final Filter wF = filterOrDefault(wGate, Config::getPuzzleWinning);
		final Filter dF = filterOrDefault(dGate, Config::getPuzzleDrawing);
		final boolean anyOverride = (qGate != null) || (wGate != null) || (dGate != null);
		final Filter verify = anyOverride ? Config.buildPuzzleVerify(qF, wF, dF) : Config.getPuzzleVerify();
		final int analysisCacheSize = Config.getPuzzleAnalysisCacheSize();

		a.ensureConsumed();

		final int randomSeeds = Math.toIntExact(
				Math.max(1, CommandSupport.optional(randomSeedOverride, DEFAULT_RANDOM_SEEDS)));
		int maxWaves = Math.toIntExact(Math.max(1, CommandSupport.optional(maxWavesOverride, DEFAULT_MAX_WAVES)));
		int maxFrontier = Math.toIntExact(Math.max(1, CommandSupport.optional(maxFrontierOverride, DEFAULT_MAX_FRONTIER)));
		long maxTotal = Math.max(1, CommandSupport.optional(maxTotalOverride, DEFAULT_MAX_TOTAL));

		if (randomInfinite) {
			maxWaves = Integer.MAX_VALUE;
			maxTotal = Long.MAX_VALUE;
		}

		OutputTargets outs = resolveOutputs(outRoot, chess960);
		List<Record> seeds;

		try {
			if (input != null) {
				seeds = loadRecordsFromInput(input);
			} else {
				seeds = wrapSeeds(Setup.getRandomPositionSeeds(randomSeeds, chess960));
			}
		} catch (Exception ex) {
			LogService.error(ex, "Failed to load seed positions (input=%s)", String.valueOf(input));
			System.err.println("Failed to load seed positions; see log for details.");
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
			return;
		}

		final List<Record> frontier = seeds;
		final MiningConfig config = new MiningConfig(
				accel,
				verify,
				nodesCap,
				durMs,
				outs,
				randomInfinite,
				chess960,
				randomSeeds,
				maxFrontier,
				maxWaves,
				maxTotal,
				analysisCacheSize);

		try (Pool pool = Pool.create(Math.toIntExact(Math.max(1, engineInstances)), proto)) {
			if (outs.stdout) {
				mineStdout(pool, frontier, config);
				return;
			}

			// Touch output files up front so incremental flushes can append safely.
			flushJsonLines(outs.puzzles, List.of());
			flushJsonLines(outs.nonpuzzles, List.of());

			mine(pool, frontier, config);
		} catch (Exception e) {
			LogService.error(e, "Failed during mining (pool/create/analyse/flush)");
			System.err.println("Mining failed; see log for details.");
			if (verbose) {
				e.printStackTrace(System.err);
			}
			System.exit(1);
		}
	}

	/**
	 * Used for wrapping positions into mining records (with null parents).
	 *
	 * @param seeds source positions
	 * @return records initialized with positions
	 */
	private static List<Record> wrapSeeds(List<Setup.PositionSeed> seeds) {
		final List<Record> out = new ArrayList<>(seeds.size());
		for (Setup.PositionSeed seed : seeds) {
			out.add(new Record()
					.withPosition(seed.position())
					.withParent(seed.parent()));
		}
		return out;
	}

	/**
	 * Used for performing bounded multi-wave mining and expansion.
	 *
	 * <p>
	 * Applies an accelerate pre-filter, verifies puzzles, expands best-move
	 * replies,
	 * and prevents cycles via canonical FEN de-duplication.
	 * </p>
	 *
	 * @param pool     shared engine pool
	 * @param frontier initial frontier records
	 * @param config   mining configuration
	 * @throws IOException when mining fails unexpectedly
	 */
	private static void mine(
			Pool pool,
			List<Record> frontier,
			MiningConfig config) throws IOException {
		final Set<String> seenFen = new HashSet<>(frontier.size() * 2);
		final AnalysisCache analyzedFen = new AnalysisCache(config.analysisCacheSize());

		int waves = 0;
		int processed = 0;

		while (waves < config.maxWaves() && processed < config.maxTotal()) {
			frontier = prepareFrontierForWave(frontier, config, seenFen, analyzedFen, processed, waves);
			if (frontier.isEmpty()) {
				break;
			}

			frontier = capFrontier(frontier, config.maxFrontier());
			analyzeWave(pool, frontier, config.accel(), config.nodesCap(), config.durMs());

			final WaveState state = processFrontier(
					frontier,
					config.verify(),
					seenFen,
					analyzedFen,
					processed,
					config.maxTotal());

			if (!state.wavePuzzles.isEmpty()) {
				flushJsonLines(config.outs().puzzles, state.wavePuzzles);
			}
			if (!state.waveNonPuzzles.isEmpty()) {
				flushJsonLines(config.outs().nonpuzzles, state.waveNonPuzzles);
			}

			frontier = state.next;
			processed = state.processed;
			waves++;
		}
	}

	/**
	 * Used for mining with JSONL streaming to standard output.
	 *
	 * <p>
	 * This mode prints each result as soon as its engine analysis completes, so the
	 * output ordering is completion-order (not input-order).
	 * </p>
	 *
	 * @param pool     shared engine pool
	 * @param frontier initial frontier records
	 * @param config   mining configuration
	 */
	private static void mineStdout(
			Pool pool,
			List<Record> frontier,
			MiningConfig config) {
		final Set<String> seenFen = new HashSet<>(frontier.size() * 2);
		final AnalysisCache analyzedFen = new AnalysisCache(config.analysisCacheSize());

		int waves = 0;
		int processed = 0;

		while (waves < config.maxWaves() && processed < config.maxTotal()) {
			frontier = prepareFrontierForWave(frontier, config, seenFen, analyzedFen, processed, waves);
			if (frontier.isEmpty()) {
				break;
			}

			frontier = capFrontier(frontier, config.maxFrontier());
			final WaveState state = analyzeAndProcessWaveStdout(
					pool,
					frontier,
					config,
					seenFen,
					analyzedFen,
					processed,
					config.maxTotal());

			frontier = state.next;
			processed = state.processed;
			waves++;
		}
	}

	/**
	 * Analyzes a wave and emits each record as soon as it completes.
	 *
	 * @param pool       engine pool
	 * @param frontier   current frontier (deduplicated + capped)
	 * @param config     mining configuration
	 * @param seenFen    FEN de-duplication set
	 * @param analyzedFen analyzed FEN cache
	 * @param processed0 processed count entering the wave
	 * @param maxTotal   maximum records permitted
	 * @return next frontier + updated processed count
	 */
	private static WaveState analyzeAndProcessWaveStdout(
			Pool pool,
			List<Record> frontier,
			MiningConfig config,
			Set<String> seenFen,
			AnalysisCache analyzedFen,
			int processed0,
			long maxTotal) {
		final List<Record> next = new ArrayList<>(frontier.size() * 2);
		final int[] processed = new int[] { processed0 };

		pool.analyseEach(frontier, config.accel(), config.nodesCap(), config.durMs(), r -> {
			if (processed[0] >= maxTotal) {
				return;
			}
			processed[0]++;

			final Position pos = r.getPosition();
			if (pos != null) {
				analyzedFen.add(pos.toString());
			}

			final boolean isPuzzle = config.verify().apply(r.getAnalysis());
			System.out.println(prependKind(isPuzzle ? "puzzle" : "nonpuzzle", r.toJson()));

			if (isPuzzle) {
				expandBestMoveChildren(r, seenFen, analyzedFen, next, processed[0], maxTotal);
			}
		});

		return new WaveState(next, processed[0], List.of(), List.of());
	}

	/**
	 * Prepends {@code "kind":"..."} to a {@link chess.struct.Record#toJson()}
	 * object.
	 *
	 * @param kind       "puzzle" or "nonpuzzle"
	 * @param recordJson JSON object string starting with '{'
	 * @return JSON object string with inserted {@code kind} field
	 */
	private static String prependKind(String kind, String recordJson) {
		if (recordJson == null || recordJson.isEmpty()) {
			return KIND_JSON_PREFIX + kind + "\"}";
		}
		if (recordJson.charAt(0) == '{') {
			return KIND_JSON_PREFIX + kind + "\"," + recordJson.substring(1);
		}
		return KIND_JSON_PREFIX + kind + "\",\"record\":" + recordJson + "}";
	}

	/**
	 * Prepares the frontier for the next mining wave.
	 *
	 * <p>
	 * When {@link MiningConfig#infinite()} is enabled and the frontier becomes
	 * empty, this method refills it with new random seeds until either a
	 * non-empty, unique frontier is produced or mining limits are reached.
	 * </p>
	 *
	 * @param frontier    current frontier (possibly empty)
	 * @param config      mining configuration
	 * @param seenFen     global de-duplication set (mutated)
	 * @param analyzedFen already analyzed FEN cache (used to skip re-analysis)
	 * @param processed   processed count so far
	 * @param waves       waves completed so far
	 * @return deduplicated frontier for the next wave (may be empty)
	 */
	private static List<Record> prepareFrontierForWave(
			List<Record> frontier,
			MiningConfig config,
			Set<String> seenFen,
			AnalysisCache analyzedFen,
			int processed,
			int waves) {
		List<Record> prepared = frontier;
		while (prepared.isEmpty() && config.infinite() && waves < config.maxWaves() && processed < config.maxTotal()) {
			prepared = wrapSeeds(Setup.getRandomPositionSeeds(config.randomSeeds(), config.chess960()));
			prepared = deduplicateFrontier(prepared, seenFen, analyzedFen);
		}
		return deduplicateFrontier(prepared, seenFen, analyzedFen);
	}

	/**
	 * Used for holding per-wave results.
	 */
	private static final class WaveState {

		/**
		 * Next frontier to analyze in the following wave.
		 */
		final List<Record> next;

		/**
		 * Total processed count after this wave.
		 */
		final int processed;

		/**
		 * Verified puzzles encountered in this wave.
		 */
		final List<Record> wavePuzzles;

		/**
		 * Non-puzzles encountered in this wave.
		 */
		final List<Record> waveNonPuzzles;

		/**
		 * Creates a new per-wave state snapshot.
		 *
		 * @param next           next frontier
		 * @param processed      processed count after the wave
		 * @param wavePuzzles    puzzles found in the wave
		 * @param waveNonPuzzles non-puzzles found in the wave
		 */
		WaveState(List<Record> next, int processed, List<Record> wavePuzzles, List<Record> waveNonPuzzles) {
			this.next = next;
			this.processed = processed;
			this.wavePuzzles = wavePuzzles;
			this.waveNonPuzzles = waveNonPuzzles;
		}
	}

	/**
	 * Fixed-size LRU cache for remembering analyzed positions.
	 */
	private static final class AnalysisCache {

		/**
		 * Maximum number of entries retained by the cache.
		 */
		private final int maxSize;

		/**
		 * Backing map with access-order eviction.
		 */
		private final LinkedHashMap<String, Boolean> map;

		/**
		 * Creates a new LRU cache with the given maximum size.
		 *
		 * @param maxSize maximum number of FEN entries to keep
		 */
		AnalysisCache(int maxSize) {
			if (maxSize < 1) {
				throw new IllegalArgumentException("maxSize < 1");
			}
			this.maxSize = maxSize;
			int initialCapacity = Math.min(maxSize, 16_384);
			this.map = new LinkedHashMap<>(initialCapacity, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
					return size() > AnalysisCache.this.maxSize;
				}
			};
		}

		/**
		 * Reports whether a FEN is already cached.
		 *
		 * @param fen canonical FEN string
		 * @return true if the FEN is already cached
		 */
		boolean contains(String fen) {
			return map.get(fen) != null;
		}

		/**
		 * Adds a FEN to the cache.
		 *
		 * @param fen canonical FEN string
		 * @return true if the FEN was not already present
		 */
		boolean add(String fen) {
			return map.put(fen, Boolean.TRUE) == null;
		}
	}

	/**
	 * Immutable configuration bundle for the mining loop.
	 *
	 * @param accel             accelerate pre-filter
	 * @param verify            verification filter for classifying puzzles
	 * @param nodesCap          maximum nodes per position
	 * @param durMs             maximum duration per position (ms)
	 * @param outs              output targets for incremental persistence
	 * @param infinite          whether to keep generating random seeds when
	 *                          frontier is empty
	 * @param chess960          whether to generate Chess960 random seeds when
	 *                          refilling
	 * @param randomSeeds       number of random seeds to generate per refill
	 * @param maxFrontier       cap on frontier size per wave
	 * @param maxWaves          maximum waves to execute
	 * @param maxTotal          maximum records to process
	 * @param analysisCacheSize max analyzed positions to remember (LRU)
	 */
	private record MiningConfig(
			Filter accel,
			Filter verify,
			long nodesCap,
			long durMs,
			OutputTargets outs,
			boolean infinite,
			boolean chess960,
			int randomSeeds,
			int maxFrontier,
			int maxWaves,
			long maxTotal,
			int analysisCacheSize) {
	}

	/**
	 * Used for capping the frontier size to a fixed maximum.
	 *
	 * @param frontier current frontier
	 * @param limit    maximum allowed size
	 * @return possibly trimmed frontier
	 */
	private static List<Record> capFrontier(List<Record> frontier, int limit) {
		if (frontier.size() <= limit) {
			return frontier;
		}
		return new ArrayList<>(frontier.subList(0, limit));
	}

	/**
	 * Used for filtering out already-processed or duplicate positions from the
	 * frontier.
	 *
	 * @param frontier    current frontier
	 * @param seenFen     global de-duplication set (mutated to register queued
	 *                    positions)
	 * @param analyzedFen positions that have already been fully analyzed
	 * @return possibly trimmed frontier
	 */
	private static List<Record> deduplicateFrontier(
			List<Record> frontier,
			Set<String> seenFen,
			AnalysisCache analyzedFen) {
		if (frontier.isEmpty()) {
			return frontier;
		}

		final List<Record> unique = new ArrayList<>(frontier.size());
		final Set<String> waveSeen = new HashSet<>(frontier.size() * 2);

		for (Record rec : frontier) {
			final Position pos = rec.getPosition();
			if (pos != null) {
				final String fen = pos.toString();
				if (!analyzedFen.contains(fen) && waveSeen.add(fen)) {
					seenFen.add(fen); // Register for child de-duplication across waves.
					unique.add(rec);
				}
			}
		}

		return (unique.size() == frontier.size()) ? frontier : unique;
	}

	/**
	 * Used for analyzing a wave of records via the engine pool.
	 *
	 * @param pool     engine pool
	 * @param frontier current frontier
	 * @param accel    accelerate filter
	 * @param nodesCap nodes limit
	 * @param durMs    duration limit (ms)
	 */
	private static void analyzeWave(
			Pool pool,
			List<Record> frontier,
			Filter accel,
			long nodesCap,
			long durMs) {
		pool.analyseAll(frontier, accel, nodesCap, durMs);
	}

	/**
	 * Used for processing the analyzed frontier and building the next wave.
	 *
	 * @param frontier    current frontier
	 * @param verify      puzzle verification filter
	 * @param seenFen     FEN de-duplication set
	 * @param analyzedFen processed FEN cache for skipping re-analysis
	 * @param processed   processed count so far
	 * @param maxTotal    maximum records permitted
	 * @return next frontier and updated processed count
	 */
	private static WaveState processFrontier(
			List<Record> frontier,
			Filter verify,
			Set<String> seenFen,
			AnalysisCache analyzedFen,
			int processed,
			long maxTotal) {
		final List<Record> next = new ArrayList<>(frontier.size() * 2);
		final List<Record> wavePuzzles = new ArrayList<>();
		final List<Record> waveNonPuzzles = new ArrayList<>();

		for (Record r : frontier) {
			processed++;
			final Position pos = r.getPosition();
			if (pos != null) {
				analyzedFen.add(pos.toString());
			}
			if (verify.apply(r.getAnalysis())) {
				wavePuzzles.add(r);
				expandBestMoveChildren(r, seenFen, analyzedFen, next, processed, maxTotal);
			} else {
				waveNonPuzzles.add(r);
			}
			if (processed >= maxTotal) {
				break;
			}
		}

		return new WaveState(next, processed, wavePuzzles, waveNonPuzzles);
	}

	/**
	 * Used for expanding a record's best move and queuing all child replies.
	 *
	 * @param r           analyzed record
	 * @param seenFen     de-duplication set
	 * @param analyzedFen processed FEN cache for skipping re-analysis
	 * @param next        accumulator for next frontier
	 * @param processed   processed count so far
	 * @param maxTotal    maximum records permitted
	 */
	private static void expandBestMoveChildren(
			Record r,
			Set<String> seenFen,
			AnalysisCache analyzedFen,
			List<Record> next,
			int processed,
			long maxTotal) {
		final short best = r.getAnalysis().getBestMove();
		final Position parent = r.getPosition().copyOf().play(best);

		for (Position child : parent.generateSubPositions()) {
			final String fen = child.toString(); // assumes FEN canonicalization
			if (!analyzedFen.contains(fen) && seenFen.add(fen)) {
				next.add(new Record().withPosition(child).withParent(parent));
				if (processed + next.size() >= maxTotal) {
					break;
				}
			}
		}
	}

	/**
	 * Used for grouping output target paths for puzzles and non-puzzles.
	 */
	private static final class OutputTargets {

		/**
		 * When set, mining emits JSONL to standard output instead of writing files.
		 */
		final boolean stdout;

		/**
		 * Output path for puzzle JSONL data.
		 * Written incrementally during mining.
		 */
		final Path puzzles;

		/**
		 * Output path for non-puzzle JSONL data.
		 * Written incrementally alongside puzzle outputs.
		 */
		final Path nonpuzzles;

		/**
		 * Used for holding both puzzle and non-puzzle output targets.
		 *
		 * @param p path for puzzle JSONL output
		 * @param n path for non-puzzle JSONL output
		 */
		OutputTargets(Path p, Path n) {
			this(false, p, n);
		}

		/**
		 * Used for creating an output target that streams to stdout.
		 *
		 * @return output target configured for stdout
		 */
		static OutputTargets toStdout() {
			return new OutputTargets(true, null, null);
		}

		/**
		 * Creates an output target with explicit stdout flag and paths.
		 *
		 * @param stdout whether output should go to stdout
		 * @param p      puzzle output path
		 * @param n      non-puzzle output path
		 */
		private OutputTargets(boolean stdout, Path p, Path n) {
			this.stdout = stdout;
			this.puzzles = p;
			this.nonpuzzles = n;
		}
	}

	/**
	 * Used for resolving output file paths from a root path or filename.
	 *
	 * <p>
	 * When {@code outputRoot} is file-like ({@code .json} or {@code .jsonl}), the
	 * method derives sibling {@code .puzzles.json} and {@code .nonpuzzles.json}
	 * files. Otherwise, generates timestamped filenames inside the provided
	 * directory, prefixed with the chess variant.
	 * </p>
	 *
	 * @param outputRoot directory or file-like root specified on the CLI
	 * @param chess960   whether to tag outputs for Chess960
	 * @return resolved pair of output targets
	 */
	private static OutputTargets resolveOutputs(String outputRoot, boolean chess960) {
		if (outputRoot == null || outputRoot.isEmpty() || "-".equals(outputRoot)) {
			return OutputTargets.toStdout();
		}
		boolean isFileLike = outputRoot.endsWith(".json") || outputRoot.endsWith(".jsonl");
		Path basePath = Paths.get(outputRoot);
		String baseStem;

		if (isFileLike) {
			String fn = basePath.getFileName().toString();
			int dot = fn.lastIndexOf('.');
			baseStem = (dot > 0) ? fn.substring(0, dot) : fn;
			Path dir = basePath.getParent() == null
					? Paths.get(".")
					: basePath.getParent();
			Path puzzles = dir.resolve(baseStem + ".puzzles.json");
			Path nonpuzzle = dir.resolve(baseStem + ".nonpuzzles.json");
			return new OutputTargets(puzzles, nonpuzzle);
		} else {
			String tag = chess960 ? "chess960" : "standard";
			String ts = String.valueOf(System.currentTimeMillis());
			Path dir = basePath;
			Path puzzles = dir.resolve(tag + "-" + ts + ".puzzles.json");
			Path nonpuzzle = dir.resolve(tag + "-" + ts + ".nonpuzzles.json");
			return new OutputTargets(puzzles, nonpuzzle);
		}
	}

	/**
	 * Used for loading seed records from a supported input file.
	 *
	 * @param input path to a {@code .txt} or {@code .pgn} file
	 * @return list of parsed records (position + optional parent)
	 * @throws IOException when the input is unsupported or unreadable
	 */
	private static List<Record> loadRecordsFromInput(Path input) throws IOException {
		String name = input.getFileName().toString().toLowerCase();

		if (name.endsWith(".txt")) {
			return Reader.readPositionRecords(input);
		}

		if (name.endsWith(".pgn")) {
			return loadRecordsFromPgn(input);
		}

		throw new IOException("Unsupported input file (expect .txt or .pgn): " + input);
	}

	/**
	 * Reads a PGN file and extracts all mainline positions (after each ply) for
	 * every game.
	 *
	 * @param input PGN file
	 * @return list of records parsed from PGN movetext (variations preserved)
	 * @throws IOException if reading fails
	 */
	private static List<Record> loadRecordsFromPgn(Path input) throws IOException {
		List<chess.struct.Game> games = Pgn.read(input);
		List<Record> positions = new ArrayList<>();
		for (chess.struct.Game g : games) {
			positions.addAll(extractRecordsWithVariations(g));
		}
		return positions;
	}

	/**
	 * Used for writing records as JSON Lines to the target path (touching the file
	 * when empty).
	 *
	 * @param target  output path
	 * @param records puzzle or non-puzzle records to persist
	 * @throws IOException when writing fails
	 */
	private static void flushJsonLines(Path target, List<Record> records) throws IOException {
		if (records.isEmpty()) {
			ensureParentDir(target);
			// still touch file so downstream tooling can find it
			Files.createDirectories(target.getParent() == null ? Paths.get(".") : target.getParent());
			if (!Files.exists(target)) {
				Files.createFile(target);
			}
			return;
		}
		// Convert records to JSON strings (assumes Record::toJson exists; otherwise
		// adapt here).
		List<String> jsons = new ArrayList<>(records.size());
		for (Record r : records) {
			jsons.add(r.toJson());
		}
		ensureParentDir(target);
		Writer.appendJsonObjects(target, jsons);
	}

	/**
	 * Parses a Filter-DSL string or returns a default Filter when the CLI value is
	 * null.
	 *
	 * @param cliValue CLI-provided Filter-DSL string; may be null
	 * @param def      supplier of the default Filter to use when {@code cliValue}
	 *                 is absent
	 * @return the parsed Filter or the default value
	 */
	private static Filter filterOrDefault(String cliValue, Supplier<Filter> def) {
		if (cliValue == null) {
			return def.get();
		}
		try {
			return FilterDSL.fromString(cliValue);
		} catch (RuntimeException ex) {
			throw new IllegalArgumentException("Invalid filter expression: " + cliValue, ex);
		}
	}
}
