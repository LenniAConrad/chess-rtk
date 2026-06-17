package application.cli.command;

import static application.cli.Constants.OPT_DEPTH;
import static application.cli.Constants.OPT_DEPTH_SHORT;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_LIMIT;
import static application.cli.Constants.OPT_HASH;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_MULTIPV;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_NO_WDL;
import static application.cli.Constants.OPT_OFFLINE;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PGN;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_PROTOCOL_PATH_SHORT;
import static application.cli.Constants.OPT_RECORD_OUTPUT;
import static application.cli.Constants.OPT_STUDY_OUTPUT;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_TO_STUDY;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WDL;

import static application.cli.PathOps.deriveOutputPath;
import static application.cli.PathOps.ensureParentDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import application.Config;
import application.cli.Validation;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.review.Classifier;
import chess.review.GameReviewer;
import chess.review.ReviewRow;
import chess.review.StudyUnit;
import chess.review.StudyUnitFactory;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.struct.Record;
import chess.uci.Protocol;
import utility.Argv;

/**
 * CLI handlers for {@code crtk review}.
 *
 * <p>The first shipped verb is the deterministic offline game-review path. It
 * parses PGN, delegates row assembly to {@link GameReviewer}, and writes the
 * published {@code crtk.review.ply.v1} JSONL contract, with optional study-unit
 * and Record sidecars for drillable mistake rows.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ReviewCommand {

	/**
	 * Command label used in diagnostics.
	 */
	private static final String CMD_REVIEW_GAME = "review game";

	/**
	 * Default offline search depth.
	 */
	private static final int DEFAULT_OFFLINE_DEPTH = 2;

	/**
	 * Default offline node budget per search.
	 */
	private static final long DEFAULT_OFFLINE_NODES = 25_000L;

	/**
	 * Default offline wall-clock budget; zero keeps the offline path byte-reproducible.
	 */
	private static final long DEFAULT_OFFLINE_DURATION_MS = 0L;

	/**
	 * Default row cap; zero means no cap.
	 */
	private static final int DEFAULT_LIMIT = 0;

	/**
	 * Default external-UCI MultiPV value.
	 */
	private static final int DEFAULT_UCI_MULTIPV = 2;

	/**
	 * Default external-UCI thread count.
	 */
	private static final int DEFAULT_UCI_THREADS = 1;

	/**
	 * Default external-UCI hash size in MB.
	 */
	private static final int DEFAULT_UCI_HASH_MB = 64;

	/**
	 * Review JSONL suffix.
	 */
	private static final String REVIEW_JSONL_SUFFIX = ".review.jsonl";

	/**
	 * Study-unit JSONL suffix.
	 */
	private static final String STUDY_JSONL_SUFFIX = ".study.jsonl";

	/**
	 * Study Record JSON suffix.
	 */
	private static final String STUDY_RECORD_SUFFIX = ".study.record.json";

	/**
	 * Exit code for argument-shape errors.
	 */
	private static final int USAGE_EXIT = 2;

	/**
	 * Exit code for unreadable input or invalid game content.
	 */
	private static final int INPUT_EXIT = 3;

	/**
	 * Utility class; prevent instantiation.
	 */
	private ReviewCommand() {
		// utility
	}

	/**
	 * Handles {@code crtk review game --pgn PATH --offline}.
	 *
	 * @param argv parsed command arguments
	 */
	public static void runGame(Argv argv) {
		boolean verbose = argv.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path pgn = resolvePgnPath(argv);
		boolean offline = argv.flag(OPT_OFFLINE);
		Path output = argv.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		Integer depthOpt = argv.integer(OPT_DEPTH, OPT_DEPTH_SHORT);
		Long nodesOpt = argv.lng(OPT_MAX_NODES, OPT_NODES);
		Duration durationOpt = argv.duration(OPT_MAX_DURATION);
		Integer limitOpt = argv.integer(OPT_LIMIT);
		String protocolPathOpt = argv.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT);
		Integer multipvOpt = argv.integer(OPT_MULTIPV);
		Integer threadsOpt = argv.integer(OPT_THREADS);
		Integer hashOpt = argv.integer(OPT_HASH);
		boolean wdl = argv.flag(OPT_WDL);
		boolean noWdl = argv.flag(OPT_NO_WDL);
		boolean toStudy = argv.flag(OPT_TO_STUDY);
		Path studyOutput = argv.path(OPT_STUDY_OUTPUT);
		Path recordOutput = argv.path(OPT_RECORD_OUTPUT);
		argv.ensureConsumed();

		if (pgn == null) {
			throw new CommandFailure(
					"Usage: crtk review game --pgn PATH [--offline] [--output PATH]",
					USAGE_EXIT);
		}
		if (!toStudy && (studyOutput != null || recordOutput != null)) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": " + OPT_STUDY_OUTPUT + " and "
					+ OPT_RECORD_OUTPUT + " require " + OPT_TO_STUDY, USAGE_EXIT);
		}
		int limit = limitOpt == null ? DEFAULT_LIMIT : limitOpt.intValue();
		validateSharedOptions(limit);

		Path resolvedOutput = output == null ? deriveOutputPath(pgn, REVIEW_JSONL_SUFFIX) : output;
		Path resolvedStudyOutput = studyOutput == null ? deriveOutputPath(pgn, STUDY_JSONL_SUFFIX) : studyOutput;
		Path resolvedRecordOutput = recordOutput == null ? deriveOutputPath(pgn, STUDY_RECORD_SUFFIX) : recordOutput;
		List<Game> games = readGames(pgn, verbose);
		GameReviewer.Review review = offline
				? reviewOffline(games, pgn, depthOpt, nodesOpt, durationOpt, limit,
						protocolPathOpt, multipvOpt, threadsOpt, hashOpt, wdl, noWdl, verbose)
				: reviewUci(games, pgn, protocolPathOpt, nodesOpt, durationOpt, limit,
						depthOpt, multipvOpt, threadsOpt, hashOpt, wdl, noWdl, verbose);
		GameReviewer.Review emittedReview = toStudy
				? stampStudyUnitIds(review)
				: review;
		StudyUnitFactory.Output study = toStudy
				? StudyUnitFactory.fromRows(emittedReview.rows())
				: new StudyUnitFactory.Output(List.of(), List.of());
		writeRows(resolvedOutput, emittedReview.rows(), verbose);
		if (toStudy) {
			writeStudyRows(resolvedStudyOutput, study.units(), verbose);
			writeStudyRecords(resolvedRecordOutput, study.records(), verbose);
		}
		System.out.println(summaryJson(resolvedOutput, emittedReview,
				toStudy ? resolvedStudyOutput : null,
				toStudy ? resolvedRecordOutput : null,
				study));
	}

	/**
	 * Runs the deterministic offline review backend.
	 *
	 * @param games parsed games
	 * @param pgn input PGN path
	 * @param depthOpt optional depth
	 * @param nodesOpt optional nodes
	 * @param durationOpt optional duration
	 * @param limit row cap
	 * @param protocolPathOpt UCI protocol option, invalid for offline mode
	 * @param multipvOpt UCI multipv option, invalid for offline mode
	 * @param threadsOpt UCI threads option, invalid for offline mode
	 * @param hashOpt UCI hash option, invalid for offline mode
	 * @param wdl UCI WDL flag, invalid for offline mode
	 * @param noWdl UCI no-WDL flag, invalid for offline mode
	 * @param verbose whether to print stack traces
	 * @return review result
	 */
	private static GameReviewer.Review reviewOffline(
			List<Game> games,
			Path pgn,
			Integer depthOpt,
			Long nodesOpt,
			Duration durationOpt,
			int limit,
			String protocolPathOpt,
			Integer multipvOpt,
			Integer threadsOpt,
			Integer hashOpt,
			boolean wdl,
			boolean noWdl,
			boolean verbose) {
		if (protocolPathOpt != null || multipvOpt != null || threadsOpt != null || hashOpt != null || wdl || noWdl) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": UCI engine options cannot be used with "
					+ OPT_OFFLINE, USAGE_EXIT);
		}
		int depth = depthOpt == null ? DEFAULT_OFFLINE_DEPTH : depthOpt.intValue();
		long nodes = nodesOpt == null ? DEFAULT_OFFLINE_NODES : nodesOpt.longValue();
		long durationMs = CommandSupport.optionalDurationMs(durationOpt, DEFAULT_OFFLINE_DURATION_MS);
		validateOfflineOptions(depth, nodes, durationMs);
		GameReviewer.Review review;
		try {
			review = GameReviewer.reviewOffline(games, new GameReviewer.Options(
					new Limits(depth, nodes, durationMs),
					limit,
					"pgn:" + pgn,
					Classifier.Thresholds.classical(),
					VersionCommand.VERSION));
		} catch (RuntimeException ex) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": failed to review PGN: " + ex.getMessage(),
					ex, INPUT_EXIT, verbose);
		}
		return review;
	}

	/**
	 * Runs the external UCI review backend.
	 *
	 * @param games parsed games
	 * @param pgn input PGN path
	 * @param protocolPathOpt optional protocol path
	 * @param nodesOpt optional nodes
	 * @param durationOpt optional duration
	 * @param limit row cap
	 * @param depthOpt offline-only depth option
	 * @param multipvOpt optional MultiPV
	 * @param threadsOpt optional threads
	 * @param hashOpt optional hash
	 * @param wdl request WDL output
	 * @param noWdl suppress WDL output
	 * @param verbose whether to print stack traces
	 * @return review result
	 */
	private static GameReviewer.Review reviewUci(
			List<Game> games,
			Path pgn,
			String protocolPathOpt,
			Long nodesOpt,
			Duration durationOpt,
			int limit,
			Integer depthOpt,
			Integer multipvOpt,
			Integer threadsOpt,
			Integer hashOpt,
			boolean wdl,
			boolean noWdl,
			boolean verbose) {
		if (depthOpt != null) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": " + OPT_DEPTH
					+ " is only supported with " + OPT_OFFLINE
					+ "; external review is bounded by " + OPT_MAX_NODES, USAGE_EXIT);
		}
		EngineSupport.validateWdlFlags(CMD_REVIEW_GAME, wdl, noWdl);
		String protocolPath = CommandSupport.optional(protocolPathOpt, Config.getProtocolPath());
		long nodes = Math.max(1L, CommandSupport.optional(nodesOpt, Config.getMaxNodes()));
		long durationMs = Math.max(1L,
				CommandSupport.optionalDurationMs(durationOpt, Config.getMaxDuration()));
		int multipv = multipvOpt == null ? DEFAULT_UCI_MULTIPV : multipvOpt.intValue();
		int threads = threadsOpt == null ? DEFAULT_UCI_THREADS : threadsOpt.intValue();
		int hash = hashOpt == null ? DEFAULT_UCI_HASH_MB : hashOpt.intValue();
		validateUciOptions(nodes, durationMs, multipv, threads, hash);
		Protocol protocol = EngineSupport.loadProtocolOrExit(protocolPath, verbose);
		try {
			return GameReviewer.reviewUci(games, new GameReviewer.UciOptions(
					protocol,
					protocolPath,
					nodes,
					durationMs,
					multipv,
					threads,
					hash,
					!noWdl,
					limit,
					"pgn:" + pgn,
					Classifier.Thresholds.classical(),
					VersionCommand.VERSION));
		} catch (IOException ex) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": engine failed: " + ex.getMessage(),
					ex, USAGE_EXIT, verbose);
		} catch (RuntimeException ex) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": failed to review PGN: " + ex.getMessage(),
					ex, INPUT_EXIT, verbose);
		}
	}

	/**
	 * Resolves PGN input from {@code --pgn} or {@code --input}.
	 *
	 * @param argv parsed arguments
	 * @return PGN path or {@code null}
	 */
	private static Path resolvePgnPath(Argv argv) {
		Path pgn = argv.path(OPT_PGN);
		Path input = argv.path(OPT_INPUT, OPT_INPUT_SHORT);
		if (pgn != null && input != null) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": provide either " + OPT_PGN
					+ " or " + OPT_INPUT + ", not both", USAGE_EXIT);
		}
		return pgn != null ? pgn : input;
	}

	/**
	 * Validates numeric options.
	 *
	 * @param depth search depth
	 * @param nodes node budget
	 * @param durationMs time budget
	 * @param limit row cap
	 */
	private static void validateOfflineOptions(int depth, long nodes, long durationMs) {
		Validation.requireBetweenInclusive(CMD_REVIEW_GAME, OPT_DEPTH, depth, 1, AlphaBeta.MAX_DEPTH);
		if (nodes < 0L) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": " + OPT_MAX_NODES
					+ " must be non-negative", USAGE_EXIT);
		}
		if (durationMs < 0L) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": " + OPT_MAX_DURATION
					+ " must be non-negative", USAGE_EXIT);
		}
	}

	/**
	 * Validates shared numeric options.
	 *
	 * @param limit row cap
	 */
	private static void validateSharedOptions(int limit) {
		if (limit < 0) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": " + OPT_LIMIT
					+ " must be non-negative", USAGE_EXIT);
		}
	}

	/**
	 * Validates external UCI options.
	 *
	 * @param nodes node budget
	 * @param durationMs watchdog budget
	 * @param multipv MultiPV setting
	 * @param threads thread count
	 * @param hash hash size
	 */
	private static void validateUciOptions(long nodes, long durationMs, int multipv, int threads, int hash) {
		Validation.requirePositive(CMD_REVIEW_GAME, OPT_MAX_NODES, nodes);
		Validation.requirePositive(CMD_REVIEW_GAME, OPT_MAX_DURATION, durationMs);
		Validation.requirePositive(CMD_REVIEW_GAME, OPT_MULTIPV, multipv);
		Validation.requirePositive(CMD_REVIEW_GAME, OPT_THREADS, threads);
		if (hash < 0) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": " + OPT_HASH
					+ " must be non-negative", USAGE_EXIT);
		}
	}

	/**
	 * Reads PGN games or exits with a structured failure.
	 *
	 * @param pgn input path
	 * @param verbose whether to print stack traces
	 * @return parsed games
	 */
	private static List<Game> readGames(Path pgn, boolean verbose) {
		try {
			List<Game> games = Pgn.read(pgn);
			if (games.isEmpty()) {
				throw new CommandFailure(CMD_REVIEW_GAME + ": PGN contains no games: " + pgn,
						INPUT_EXIT);
			}
			return games;
		} catch (CommandFailure failure) {
			throw failure;
		} catch (IOException ex) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": failed to read PGN: " + ex.getMessage(),
					ex, INPUT_EXIT, verbose);
		}
	}

	/**
	 * Writes review rows as JSONL.
	 *
	 * @param output output path
	 * @param rows rows to write
	 * @param verbose whether to print stack traces
	 */
	private static void writeRows(Path output, List<ReviewRow> rows, boolean verbose) {
		StringBuilder body = new StringBuilder(Math.max(16, rows.size() * 512));
		for (ReviewRow row : rows) {
			body.append(row.toJson()).append('\n');
		}
		try {
			ensureParentDir(output);
			Files.writeString(output, body.toString(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": failed to write output: " + ex.getMessage(),
					ex, USAGE_EXIT, verbose);
		}
	}

	/**
	 * Returns a review result whose drillable rows carry study-unit ids.
	 *
	 * @param review source review
	 * @return review with row ids stamped
	 */
	private static GameReviewer.Review stampStudyUnitIds(GameReviewer.Review review) {
		List<ReviewRow> stamped = review.rows().stream()
				.map(StudyUnitFactory::withStudyUnitId)
				.toList();
		return new GameReviewer.Review(stamped, review.gamesRead());
	}

	/**
	 * Writes study units as JSONL.
	 *
	 * @param output output path
	 * @param units study units to write
	 * @param verbose whether to print stack traces
	 */
	private static void writeStudyRows(Path output, List<StudyUnit> units, boolean verbose) {
		StringBuilder body = new StringBuilder(Math.max(16, units.size() * 512));
		for (StudyUnit unit : units) {
			body.append(unit.toJson()).append('\n');
		}
		try {
			ensureParentDir(output);
			Files.writeString(output, body.toString(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": failed to write study output: " + ex.getMessage(),
					ex, USAGE_EXIT, verbose);
		}
	}

	/**
	 * Writes study Record objects as a JSON array.
	 *
	 * @param output output path
	 * @param records records to write
	 * @param verbose whether to print stack traces
	 */
	private static void writeStudyRecords(Path output, List<Record> records, boolean verbose) {
		try {
			ensureParentDir(output);
			Files.writeString(output, Record.toJsonArray(records), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new CommandFailure(CMD_REVIEW_GAME + ": failed to write study records: " + ex.getMessage(),
					ex, USAGE_EXIT, verbose);
		}
	}

	/**
	 * Renders the command summary.
	 *
	 * @param output output path
	 * @param review review result
	 * @param studyOutput study output path, or {@code null}
	 * @param recordOutput study Record output path, or {@code null}
	 * @param study study artifacts
	 * @return compact JSON summary
	 */
	private static String summaryJson(
			Path output,
			GameReviewer.Review review,
			Path studyOutput,
			Path recordOutput,
			StudyUnitFactory.Output study) {
		StringBuilder sb = new StringBuilder(192)
				.append("{\"schemaVersion\":")
				.append(CommandSupport.jsonString(ReviewRow.SCHEMA_VERSION))
				.append(",\"output\":")
				.append(CommandSupport.jsonString(output.toString()))
				.append(",\"games\":")
				.append(review.gamesRead())
				.append(",\"rows\":")
				.append(review.rows().size());
		if (studyOutput != null) {
			sb.append(",\"studySchemaVersion\":")
					.append(CommandSupport.jsonString(StudyUnit.SCHEMA_VERSION))
					.append(",\"studyOutput\":")
					.append(CommandSupport.jsonString(studyOutput.toString()))
					.append(",\"recordOutput\":")
					.append(CommandSupport.jsonString(recordOutput.toString()))
					.append(",\"studyRows\":")
					.append(study.units().size())
					.append(",\"recordRows\":")
					.append(study.records().size());
		}
		return sb.append('}').toString();
	}
}
