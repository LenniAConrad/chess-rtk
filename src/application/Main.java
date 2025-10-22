package application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import chess.core.Position;
import chess.core.Setup;
import chess.debug.LogService;
import chess.debug.Printer;
import chess.io.Converter;
import chess.io.Reader;
import chess.io.Writer;
import chess.uci.Filter;
import chess.uci.Filter.FilterDSL;
import chess.model.Record;
import utility.Argv;

/**
 * Used for providing the CLI entry point and dispatching subcommands.
 *
 * <p>
 * Recognized subcommands are {@code convert}, {@code mine}, {@code print}, and
 * {@code help}.
 * Prints usage information when no subcommand is supplied. For unknown
 * subcommands, prints an
 * error and exits with status {@code 2}.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Main {

	/**
	 * Used for parsing top-level CLI arguments and delegating to a subcommand
	 * handler.
	 *
	 * <p>
	 * Behavior:
	 * <ul>
	 * <li>Attempts to read the first positional argument as the subcommand.</li>
	 * <li>Delegates remaining positionals to the corresponding {@code run*}
	 * method.</li>
	 * <li>On unknown subcommands, prints help and exits with non-zero status.</li>
	 * </ul>
	 *
	 * @param argv raw command-line arguments; first positional must be a valid
	 *             subcommand.
	 */
	public static void main(String[] argv) {
		Argv a = new Argv(argv);

		List<String> head = a.positionals();

		a.ensureConsumed();

		if (head.isEmpty()) {
			help();
			return;
		}

		String sub = head.get(0);
		String[] tail = head.subList(1, head.size()).toArray(new String[0]);
		Argv b = new Argv(tail);

		switch (sub) {
			case "convert" -> runConvert(b);
			case "mine" -> runMine(b);
			case "print" -> runPrint(b);
			case "help", "-h", "--help" -> help();
			default -> {
				System.err.println("Unknown command: " + sub);
				help();
				System.exit(2);
			}
		}
	}

	/**
	 * Used for handling the {@code convert} subcommand.
	 *
	 * <p>
	 * Converts a {@code .record} JSON file into a {@code .plain} file. Optionally
	 * filters
	 * records using a Filter-DSL string and/or includes sidelines in the output.
	 *
	 * <p>
	 * Side effects:
	 * <ul>
	 * <li>Reads from the provided input path.</li>
	 * <li>Writes a new file to the output path (derived when omitted).</li>
	 * </ul>
	 *
	 * @param a parsed argument vector for the subcommand. Recognized options:
	 *          <ul>
	 *          <li>{@code -a | --export-all | --sidelines} — include sidelines in
	 *          the output.</li>
	 *          <li>{@code -f | --filter <dsl>} — Filter-DSL used to select
	 *          records.</li>
	 *          <li>{@code -i | --input <path>} — required input {@code .record}
	 *          file.</li>
	 *          <li>{@code -o | --output <path>} — optional output {@code .plain}
	 *          file path.</li>
	 *          </ul>
	 */
	private static void runConvert(Argv a) {
		boolean exportAll = a.flag("--sidelines", "--export-all", "-a");
		String filterDsl = a.string("--filter", "-f");
		Path in = a.pathRequired("--input", "-i");
		Path out = a.path("--output", "-o");
		a.ensureConsumed();

		Filter filter = null;
		if (filterDsl != null && !filterDsl.isEmpty()) {
			filter = FilterDSL.fromString(filterDsl);
		}

		Converter.recordToPlain(exportAll, filter, in, out);
	}

	/**
	 * Used for handling the {@code print} subcommand.
	 *
	 * <p>
	 * Parses a FEN supplied via {@code --fen} or as a single positional argument
	 * after the
	 * subcommand and pretty-prints the position. Exits with status {@code 2} when
	 * no FEN is provided.
	 *
	 * <p>
	 * Options:
	 * <ul>
	 * <li>{@code --fen "<FEN...>"} — FEN string; may also be provided
	 * positionally.</li>
	 * <li>{@code --verbose} or {@code -v} — also print stack traces to stderr on
	 * errors.</li>
	 * </ul>
	 */
	private static void runPrint(Argv a) {
		boolean verbose = a.flag("--verbose", "-v");
		String fen = a.string("--fen");
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}

		a.ensureConsumed();

		if (fen == null || fen.isEmpty()) {
			System.err.println("print requires a FEN (use --fen or positional)");
			System.exit(2);
			return;
		}

		try {
			Position pos = new Position(fen.trim());
			Printer.board(pos);
		} catch (IllegalArgumentException ex) {
			// Invalid FEN or position construction error
			System.err.println("Error: invalid FEN. " + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, "print: invalid FEN", "FEN: " + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception t) {
			System.err.println("Error: failed to print position. " + (t.getMessage() == null ? "" : t.getMessage()));
			LogService.error(t, "print: unexpected failure while printing position", "FEN: " + fen);
			if (verbose) {
				t.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Used for printing usage information to standard output.
	 *
	 * <p>
	 * Includes brief explanations for each subcommand and the most relevant flags.
	 * Intended for
	 * interactive use from the command line.
	 */
	private static void help() {
		System.out.println("""
				usage: app <command> [options]

				commands:
				  convert   Convert .record JSON to .plain
				  mine      Mine chess puzzles (supports Chess960 / PGN / FEN list / random)
				  print     Pretty-print a FEN

				convert options:
				  --input|-i <path>          Input .record file (required)
				  --output|-o <path>         Output .plain file (optional; default derived)
				  --filter|-f <dsl>          Filter-DSL string for selecting records
				  --sidelines|--export-all   Include sidelines in output

				mine options (overrides & inputs):
				  --chess960|-9               Enable Chess960 mining
				  --input|-i <path>           PGN or TXT with FENs; omit to use random
				  --output|-o <path>          Output path/dir for puzzles

				  --protocol-path|-P <toml>   Override Config.getProtocolPath()
				  --engine-instances|-e <n>   Override Config.getEngineInstances()
				  --max-nodes <n>             Override Config.getMaxNodes()
				  --max-duration <dur>        Override Config.getMaxDuration(), e.g. 60s, 2m, 60000

				  --puzzle-quality <dsl>      Override quality gate DSL
				  --puzzle-winning <dsl>      Override winning gate DSL
				  --puzzle-drawing <dsl>      Override drawing gate DSL
				  --puzzle-accelerate <dsl>   Override accelerate prefilter DSL

				print options:
				  --fen "<FEN...>"            FEN string (or supply as positional)
				""");
	}

	/**
	 * Used for handling the {@code mine} subcommand.
	 *
	 * <p>
	 * Resolves runtime configuration and filters, loads or generates seed
	 * positions,
	 * runs batched engine analysis in bounded waves, expands verified puzzles, and
	 * appends
	 * JSONL outputs for puzzles and non-puzzles.
	 *
	 * @param a parsed argument vector for the subcommand
	 */
	private static void runMine(Argv a) {
		final boolean verbose = a.flag("--verbose", "-v");
		final boolean chess960 = a.flag("--chess960", "-9");
		final Path input = a.path("--input", "-i");
		final String outRoot = optional(a.string("--output", "-o"), Config.getOutput());
		final String proto = optional(a.string("--protocol-path", "-P"), Config.getProtocolPath());
		final long engineInstances = optional(a.lng("--engine-instances", "-e"), Config.getEngineInstances());
		final long nodesCap = Math.max(1, optional(a.lng("--max-nodes"), Config.getMaxNodes()));
		final long durMs = Math.max(
				1,
				optionalDurationMs(a.duration("--max-duration"), Config.getMaxDuration()));

		final String qGate = a.string("--puzzle-quality");
		final String wGate = a.string("--puzzle-winning");
		final String dGate = a.string("--puzzle-drawing");
		final String accelDsl = a.string("--puzzle-accelerate");

		final Filter accel = filterOrDefault(accelDsl, Config::getPuzzleAccelerate);
		final Filter qF = filterOrDefault(qGate, Config::getPuzzleQuality);
		final Filter wF = filterOrDefault(wGate, Config::getPuzzleWinning);
		final Filter dF = filterOrDefault(dGate, Config::getPuzzleDrawing);
		final boolean anyOverride = (qGate != null) || (wGate != null) || (dGate != null);
		final Filter verify = anyOverride ? Config.buildPuzzleVerify(qF, wF, dF) : Config.getPuzzleVerify();

		a.ensureConsumed();

		final OutputTargets outs = resolveOutputs(outRoot, chess960);

		final List<Position> seeds;
		try {
			seeds = (input != null) ? loadPositionsFromInput(input) : Setup.getRandomPositions(100, chess960);
		} catch (Exception ex) {
			LogService.error(ex, "Failed to load seed positions (input=%s)", String.valueOf(input));
			System.out.println("Failed to load seed positions; see log for details.");
			if (verbose) {
				ex.printStackTrace(System.out);
			}
			System.exit(2);
			return;
		}

		final List<Record> frontier = wrapSeeds(seeds);

		try (Pool pool = Pool.create(Math.toIntExact(Math.max(1, engineInstances)), proto)) {
			final MiningResult res = mine(pool, frontier, accel, verify, nodesCap, durMs);
			flushJsonLines(outs.puzzles, res.puzzles);
			flushJsonLines(outs.nonpuzzles, res.nonpuzzles);
		} catch (Exception e) {
			LogService.error(e, "Failed during mining (pool/create/analyse/flush)");
			System.out.println("Mining failed; see log for details.");
			if (verbose) {
				e.printStackTrace(System.out);
			}
			System.exit(1);
		}
	}

	/**
	 * Used for returning both puzzle and non-puzzle collections.
	 *
	 * @param pz verified puzzle records
	 * @param np rejected (non-puzzle) records
	 */
	private static final class MiningResult {
		final List<Record> puzzles;
		final List<Record> nonpuzzles;

		MiningResult(List<Record> pz, List<Record> np) {
			this.puzzles = pz;
			this.nonpuzzles = np;
		}
	}

	/**
	 * Used for wrapping positions into mining records.
	 *
	 * @param seeds source positions
	 * @return records initialized with positions
	 */
	private static List<Record> wrapSeeds(List<Position> seeds) {
		final List<Record> out = new ArrayList<>(seeds.size());
		for (Position p : seeds) {
			out.add(new Record().withPosition(p));
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
	 *
	 * @param pool     shared engine pool
	 * @param frontier initial frontier records
	 * @param accel    accelerate pre-filter
	 * @param verify   puzzle verification filter
	 * @param nodesCap max nodes per position
	 * @param durMs    max duration per position (ms)
	 * @return collected puzzles and non-puzzles
	 */
	private static MiningResult mine(
			Pool pool,
			List<Record> frontier,
			Filter accel,
			Filter verify,
			long nodesCap,
			long durMs) {
		final List<Record> puzzles = new ArrayList<>();
		final List<Record> nonPuzzles = new ArrayList<>();
		final Set<String> seenFen = new java.util.HashSet<>(frontier.size() * 2);

		int waves = 0;
		int processed = 0;

		while (true) {
			if (shouldStop(frontier, waves, processed)) {
				break;
			}

			frontier = capFrontier(frontier, MAX_FRONTIER);
			analyzeWave(pool, frontier, accel, nodesCap, durMs);

			final WaveState state = processFrontier(
					frontier,
					verify,
					seenFen,
					puzzles,
					nonPuzzles,
					processed);

			frontier = state.next;
			processed = state.processed;
			waves++;
		}

		return new MiningResult(puzzles, nonPuzzles);
	}

	/**
	 * Used for holding per-wave results.
	 *
	 * @param next      next frontier
	 * @param processed total processed count so far
	 */
	private static final class WaveState {
		final List<Record> next;
		final int processed;

		WaveState(List<Record> next, int processed) {
			this.next = next;
			this.processed = processed;
		}
	}

	/** Used for limiting the number of waves executed. */
	private static final int MAX_WAVES = 4;

	/** Used for capping the number of records per frontier. */
	private static final int MAX_FRONTIER = 100_000;

	/** Used for capping the total number of processed records. */
	private static final int MAX_TOTAL = 500_000;

	/**
	 * Used for deciding whether the mining loop should terminate.
	 *
	 * @param frontier  current frontier
	 * @param waves     waves completed
	 * @param processed total processed count
	 * @return true when the loop must end
	 */
	private static boolean shouldStop(List<Record> frontier, int waves, int processed) {
		return frontier.isEmpty() || waves >= MAX_WAVES || processed >= MAX_TOTAL;
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
	 * @param frontier   current frontier
	 * @param verify     puzzle verification filter
	 * @param seenFen    FEN de-duplication set
	 * @param puzzles    collected puzzles
	 * @param nonPuzzles collected non-puzzles
	 * @param processed  processed count so far
	 * @return next frontier and updated processed count
	 */
	private static WaveState processFrontier(
			List<Record> frontier,
			Filter verify,
			Set<String> seenFen,
			List<Record> puzzles,
			List<Record> nonPuzzles,
			int processed) {
		final List<Record> next = new ArrayList<>(frontier.size() * 2);

		for (Record r : frontier) {
			processed++;
			if (verify.apply(r.getAnalysis())) {
				puzzles.add(r);
				expandBestMoveChildren(r, seenFen, next, processed);
			} else {
				nonPuzzles.add(r);
			}
			if (processed >= MAX_TOTAL) {
				break;
			}
		}

		return new WaveState(next, processed);
	}

	/**
	 * Used for expanding a record's best move and queuing all child replies.
	 *
	 * @param r         analyzed record
	 * @param seenFen   de-duplication set
	 * @param next      accumulator for next frontier
	 * @param processed processed count so far
	 */
	private static void expandBestMoveChildren(
			Record r,
			Set<String> seenFen,
			List<Record> next,
			int processed) {
		final short best = r.getAnalysis().getBestMove();
		final Position parent = r.getPosition().copyOf().play(best);

		for (Position child : parent.generateSubPositions()) {
			final String fen = child.toString(); // assumes FEN canonicalization
			if (seenFen.add(fen)) {
				next.add(new Record().withPosition(child).withParent(parent));
				if (processed + next.size() >= MAX_TOTAL) {
					break;
				}
			}
		}
	}

	/**
	 * Used for returning {@code value} when non-null, otherwise the
	 * {@code def}ault.
	 *
	 * @param value candidate value
	 * @param def   default value
	 * @return chosen value
	 */
	private static String optional(String value, String def) {
		return (value == null || value.isEmpty()) ? def : value;
	}

	/**
	 * Used for returning {@code value} when non-null, otherwise the
	 * {@code def}ault.
	 *
	 * @param value candidate value
	 * @param def   default value
	 * @return chosen value
	 */
	private static long optional(Long value, long def) {
		return (value == null) ? def : value;
	}

	/**
	 * Used for converting the optional duration to milliseconds with a default.
	 *
	 * @param value candidate duration
	 * @param defMs default duration in milliseconds
	 * @return chosen duration in milliseconds
	 */
	private static long optionalDurationMs(Duration value, long defMs) {
		return (value == null) ? defMs : value.toMillis();
	}

	private static final class OutputTargets {
		Path puzzles;
		Path nonpuzzles;

		OutputTargets(Path p, Path n) {
			this.puzzles = p;
			this.nonpuzzles = n;
		}
	}

	private static OutputTargets resolveOutputs(String outputRoot, boolean chess960) {
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
			Path puzzles = dir.resolve(baseStem + ".puzzles.jsonl");
			Path nonpuzzle = dir.resolve(baseStem + ".nonpuzzles.jsonl");
			return new OutputTargets(puzzles, nonpuzzle);
		} else {
			String tag = chess960 ? "chess960" : "standard";
			String ts = String.valueOf(System.currentTimeMillis());
			Path dir = basePath;
			Path puzzles = dir.resolve(tag + "-" + ts + ".puzzles.jsonl");
			Path nonpuzzle = dir.resolve(tag + "-" + ts + ".nonpuzzles.jsonl");
			return new OutputTargets(puzzles, nonpuzzle);
		}
	}

	private static List<Position> loadPositionsFromInput(Path input) throws java.io.IOException {
		String name = input.getFileName().toString().toLowerCase();

		if (name.endsWith(".txt")) {
			return Reader.readPositionList(input);
		}

		throw new IOException("Unsupported input file (expect .txt): " + input);
	}

	private static void flushJsonLines(Path target, List<Record> records) throws IOException {
		if (records.isEmpty()) {
			ensureParentDir(target);
			// still touch file so downstream tooling can find it
			Files.createDirectories(target.getParent() == null ? Paths.get(".") : target.getParent());
			if (!Files.exists(target))
				Files.createFile(target);
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

	private static void ensureParentDir(Path p) throws IOException {
		Path parent = p.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}

	/**
	 * Parses a Filter-DSL string or returns a default Filter when the CLI value is
	 * null.
	 * 
	 * @param cliValue
	 * @param def
	 * @return the parsed Filter or the default value
	 */
	private static Filter filterOrDefault(String cliValue, Supplier<Filter> def) {
		if (cliValue == null)
			return def.get();
		try {
			return FilterDSL.fromString(cliValue);
		} catch (RuntimeException ex) {
			throw new IllegalArgumentException("Invalid filter expression: " + cliValue, ex);
		}
	}
}