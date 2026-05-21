package application.cli.command;

import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_FORMAT;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_JSON;
import static application.cli.Constants.OPT_JSONL;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Format.formatPvMoves;
import static application.cli.Format.formatPvMovesSan;
import static application.cli.Format.safeSan;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import application.cli.Validation;
import application.console.Bar;
import chess.core.Move;
import chess.core.Position;
import chess.engine.MateProver;
import utility.Argv;

/**
 * Deterministic forced-mate proof search command.
 */
public final class MateCommand {

	/**
	 * Command label used in diagnostics.
	 */
	private static final String CMD_MATE = "engine mate";

	/**
	 * Maximum mate-distance option.
	 */
	private static final String OPT_MAX_MATE = "--max-mate";

	/**
	 * Shorter mate-distance option alias.
	 */
	private static final String OPT_MATE = "--mate";

	/**
	 * Default CLI proof node budget.
	 */
	private static final long DEFAULT_NODE_LIMIT = 5_000_000L;

	/**
	 * Utility class; prevent instantiation.
	 */
	private MateCommand() {
		// utility
	}

	/**
	 * Supported output formats.
	 */
	private enum OutputFormat {
		/**
		 * S u m m a r y,.
		 */
		SUMMARY,
		/**
		 * U c i,.
		 */
		UCI,
		/**
		 * S a n,.
		 */
		SAN,
		/**
		 * B o t h,.
		 */
		BOTH,
		/**
		 * J s o n,.
		 */
		JSON,
		/**
		 * J s o n l.
		 */
		JSONL
	}

	/**
	 * Parsed options.
	 */
	private record Options(
			boolean verbose,
			Path input,
			String fen,
			int maxMate,
			long maxNodes,
			int threads,
			OutputFormat format) {
	}

	/**
	 * Handles {@code engine mate}.
	 *
	 * @param a argument parser
	 */
	public static void runMate(Argv a) {
		Options opts = parseOptions(a);
		List<String> fens = CommandSupport.resolveFenInputs(CMD_MATE, opts.input(), opts.fen());
		Bar bar = fens.size() > 1 ? new Bar(fens.size(), CMD_MATE, false, System.err) : null;
		for (int i = 0; i < fens.size(); i++) {
			searchAndPrint(fens.get(i), opts, i > 0);
			CommandSupport.step(bar);
		}
		CommandSupport.finish(bar);
	}

	/**
	 * Parses command options.
	 * @param a first value
	 * @return parse options result
	 */
	private static Options parseOptions(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String format = a.string(OPT_FORMAT);
		boolean json = a.flag(OPT_JSON);
		boolean jsonl = a.flag(OPT_JSONL);
		if (json && jsonl) {
			throw new CommandFailure(CMD_MATE + ": use either " + OPT_JSON + " or " + OPT_JSONL + ", not both", 2);
		}
		Integer maxMateOpt = a.integer(OPT_MAX_MATE, OPT_MATE);
		Long nodesOpt = a.lng(OPT_MAX_NODES, OPT_NODES);
		Integer threadsOpt = a.integer(OPT_THREADS);
		String fen = CommandSupport.resolveFenArgument(a, CMD_MATE, false);
		int maxMate = maxMateOpt == null ? MateProver.DEFAULT_MAX_MATE_MOVES : maxMateOpt;
		long maxNodes = nodesOpt == null ? DEFAULT_NODE_LIMIT : nodesOpt;
		int threads = threadsOpt == null ? 1 : threadsOpt;
		Validation.requireBetweenInclusive(CMD_MATE, OPT_MAX_MATE, maxMate, 1, 32);
		if (maxNodes < 0L) {
			throw new CommandFailure(CMD_MATE + ": " + OPT_MAX_NODES + " must be non-negative", 2);
		}
		Validation.requirePositive(CMD_MATE, OPT_THREADS, threads);
		OutputFormat parsedFormat = json ? OutputFormat.JSON : jsonl ? OutputFormat.JSONL : parseFormat(format);
		return new Options(verbose, input, fen, maxMate, maxNodes, threads, parsedFormat);
	}

	/**
	 * Parses output format.
	 * @param value value to use
	 * @return parse format result
	 */
	private static OutputFormat parseFormat(String value) {
		if (value == null || value.isBlank()) {
			return OutputFormat.SUMMARY;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "summary", "text" -> OutputFormat.SUMMARY;
			case "uci" -> OutputFormat.UCI;
			case "san" -> OutputFormat.SAN;
			case "both" -> OutputFormat.BOTH;
			case "json" -> OutputFormat.JSON;
			case "jsonl" -> OutputFormat.JSONL;
			default -> throw new CommandFailure(CMD_MATE + ": unsupported " + OPT_FORMAT
					+ " value: " + value + " (expected summary, uci, san, both, json, or jsonl)", 2);
		};
	}

	/**
	 * Searches one position and prints the requested output.
	 * @param entry entry value
	 * @param opts command options
	 * @param separate true to separate output sections
	 */
	private static void searchAndPrint(String entry, Options opts, boolean separate) {
		Position position = CommandSupport.parsePositionOrExit(entry, CMD_MATE, opts.verbose());
		long started = System.currentTimeMillis();
		MateProver.SearchResult result = MateProver.search(position, opts.maxMate(), opts.maxNodes(), opts.threads());
		long elapsed = Math.max(0L, System.currentTimeMillis() - started);
		printResult(entry, position, result, elapsed, opts, separate);
	}

	/**
	 * Prints one proof-search result.
	 * @param entry entry value
	 * @param position chess position
	 * @param result result value
	 * @param elapsedMillis elapsed time in milliseconds
	 * @param opts command options
	 * @param separate true to separate output sections
	 */
	private static void printResult(
			String entry,
			Position position,
			MateProver.SearchResult result,
			long elapsedMillis,
			Options opts,
			boolean separate) {
		MateProver.Proof proof = result.proof();
		if (opts.format() == OutputFormat.JSON || opts.format() == OutputFormat.JSONL) {
			printJson(entry, position, result, elapsedMillis, opts);
			return;
		}
		if (opts.format() == OutputFormat.UCI) {
			System.out.println(proof == null ? "0000" : Move.toString(proof.bestMove()));
			return;
		}
		if (opts.format() == OutputFormat.SAN) {
			System.out.println(proof == null ? "-" : safeSan(position, proof.bestMove()));
			return;
		}
		if (opts.format() == OutputFormat.BOTH) {
			String uci = proof == null ? "0000" : Move.toString(proof.bestMove());
			String san = proof == null ? "-" : safeSan(position, proof.bestMove());
			String mate = proof == null ? "-" : "#" + proof.mateMoves();
			String prefix = opts.input() == null ? "" : entry + "\t";
			System.out.println(prefix + uci + "\t" + san + "\t" + mate);
			return;
		}
		printSummary(entry, position, result, elapsedMillis, opts, separate);
	}

	/**
	 * Prints a human-readable summary.
	 * @param entry entry value
	 * @param position chess position
	 * @param result result value
	 * @param elapsedMillis elapsed time in milliseconds
	 * @param opts command options
	 * @param separate true to separate output sections
	 */
	private static void printSummary(
			String entry,
			Position position,
			MateProver.SearchResult result,
			long elapsedMillis,
			Options opts,
			boolean separate) {
		if (separate) {
			System.out.println();
		}
		MateProver.Proof proof = result.proof();
		System.out.println("FEN: " + entry);
		System.out.println("found: " + result.found());
		System.out.println("max-mate: " + opts.maxMate());
		System.out.println("threads: " + opts.threads());
		System.out.println("nodes: " + CommandSupport.formatCount(result.nodes()));
		System.out.println("time-ms: " + elapsedMillis);
		System.out.println("exhausted: " + result.exhausted());
		if (proof == null) {
			System.out.println("mate: none up to #" + opts.maxMate());
			return;
		}
		System.out.println("mate: #" + proof.mateMoves());
		System.out.println("best: " + Move.toString(proof.bestMove()) + " (" + safeSan(position, proof.bestMove()) + ")");
		System.out.println("pv: " + formatPvMoves(proof.principalVariation()));
		System.out.println("pv-san: " + formatPvMovesSan(position, proof.principalVariation()));
	}

	/**
	 * Prints a JSON object.
	 * @param entry entry value
	 * @param position chess position
	 * @param result result value
	 * @param elapsedMillis elapsed time in milliseconds
	 * @param opts command options
	 */
	private static void printJson(
			String entry,
			Position position,
			MateProver.SearchResult result,
			long elapsedMillis,
			Options opts) {
		MateProver.Proof proof = result.proof();
		StringBuilder sb = new StringBuilder();
		sb.append("{\"fen\":").append(CommandSupport.jsonString(entry))
				.append(",\"found\":").append(proof != null)
				.append(",\"maxMate\":").append(opts.maxMate())
				.append(",\"threads\":").append(opts.threads())
				.append(",\"nodes\":").append(result.nodes())
				.append(",\"timeMillis\":").append(elapsedMillis)
				.append(",\"exhausted\":").append(result.exhausted());
		if (proof != null) {
			sb.append(",\"mate\":").append(proof.mateMoves())
					.append(",\"bestMove\":").append(CommandSupport.jsonString(Move.toString(proof.bestMove())))
					.append(",\"bestSan\":").append(CommandSupport.jsonString(safeSan(position, proof.bestMove())))
					.append(",\"pv\":").append(CommandSupport.jsonString(formatPvMoves(proof.principalVariation())))
					.append(",\"pvSan\":").append(CommandSupport.jsonString(formatPvMovesSan(position, proof.principalVariation())));
		}
		sb.append('}');
		System.out.println(sb);
	}
}
