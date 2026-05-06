package application.cli.command;

import static application.cli.Constants.CMD_UCI_SMOKE;
import static application.cli.Constants.OPT_HASH;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_NO_WDL;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_PROTOCOL_PATH_SHORT;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WDL;
import static application.cli.EngineOps.analysePositionOrExit;
import static application.cli.EngineOps.configureEngine;
import static application.cli.EngineOps.resolveWdlFlag;
import static application.cli.Validation.requirePositive;

import java.time.Duration;

import application.Config;
import chess.core.Setup;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Output;
import chess.uci.Protocol;
import utility.Argv;

/**
 * Implements the {@code engine uci-smoke} protocol smoke test.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S107")
public final class UciSmokeCommand {

	/**
	 * Default node cap for the smoke search.
	 */
	private static final long DEFAULT_NODES = 1L;

	/**
	 * Default wall-clock cap for the smoke search.
	 */
	private static final Duration DEFAULT_DURATION = Duration.ofSeconds(5);

	/**
	 * Utility class; prevent instantiation.
	 */
	private UciSmokeCommand() {
		// utility
	}

	/**
	 * Handles {@code engine uci-smoke}.
	 *
	 * @param a argument parser for the command
	 */
	public static void runUciSmoke(Argv a) {
		Config.reload();
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		String protocolPath = a.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT);
		Long nodesOpt = a.lng(OPT_MAX_NODES, OPT_NODES);
		Duration durationOpt = a.duration(OPT_MAX_DURATION);
		Integer threads = a.integer(OPT_THREADS);
		Integer hash = a.integer(OPT_HASH);
		boolean wdl = a.flag(OPT_WDL);
		boolean noWdl = a.flag(OPT_NO_WDL);
		a.ensureConsumed();

		long nodes = nodesOpt == null ? DEFAULT_NODES : nodesOpt;
		Duration duration = durationOpt == null ? DEFAULT_DURATION : durationOpt;
		requirePositive(CMD_UCI_SMOKE, OPT_MAX_NODES, nodes);
		requirePositive(CMD_UCI_SMOKE, OPT_MAX_DURATION, duration.toMillis());

		Protocol protocol = EngineSupport.loadProtocolOrExit(
				protocolPath == null ? Config.getProtocolPath() : protocolPath,
				verbose);
		runSmoke(protocol, nodes, duration.toMillis(), threads, hash, wdl, noWdl, verbose);
	}

	/**
	 * Runs the engine startup and one-node search smoke test.
	 *
	 * @param protocol parsed engine protocol
	 * @param nodes    search node cap
	 * @param duration search duration cap in milliseconds
	 * @param threads  optional thread count
	 * @param hash     optional hash size
	 * @param wdl      whether WDL was requested
	 * @param noWdl    whether WDL disable was requested
	 * @param verbose  whether to print stack traces
	 */
	private static void runSmoke(
			Protocol protocol,
			long nodes,
			long duration,
			Integer threads,
			Integer hash,
			boolean wdl,
			boolean noWdl,
			boolean verbose) {
		long started = System.currentTimeMillis();
		try (Engine engine = new Engine(protocol)) {
			configureEngine(CMD_UCI_SMOKE, engine, threads, hash, null, resolveWdlFlag(wdl, noWdl));
			engine.newGame();
			Analysis analysis = analysePositionOrExit(
					engine,
					Setup.getStandardStartPosition(),
					nodes,
					duration,
					CMD_UCI_SMOKE,
					verbose);
			printResult(protocol, analysis, System.currentTimeMillis() - started);
		} catch (CommandFailure failure) {
			throw failure;
		} catch (Exception ex) {
			throw new CommandFailure(CMD_UCI_SMOKE + ": failed: " + ex.getMessage(), ex, 2, verbose);
		}
	}

	/**
	 * Prints a concise smoke-test result.
	 *
	 * @param protocol  protocol used for the run
	 * @param analysis  search analysis result
	 * @param elapsedMs elapsed wall-clock time
	 */
	private static void printResult(Protocol protocol, Analysis analysis, long elapsedMs) {
		Output output = analysis == null ? null : analysis.getBestOutput();
		System.out.println("uci-smoke: ok");
		System.out.println("engine: " + protocol.getName());
		System.out.println("path: " + protocol.getPath());
		System.out.println("elapsed-ms: " + elapsedMs);
		if (output != null) {
			System.out.println("depth: " + output.getDepth());
			System.out.println("nodes: " + output.getNodes());
			System.out.println("pv: " + output);
		} else {
			System.out.println("search: no parsed info output");
		}
	}
}
