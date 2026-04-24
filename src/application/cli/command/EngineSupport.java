package application.cli.command;

import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_HASH;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_MULTIPV;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_NO_WDL;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_PROTOCOL_PATH_SHORT;
import static application.cli.Constants.OPT_RANDOMPOS;
import static application.cli.Constants.OPT_STARTPOS;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_WDL;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import application.Config;
import chess.debug.LogService;
import chess.uci.Protocol;
import utility.Argv;

/**
 * Shared helpers for engine-driven commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EngineSupport {

	/**
	 * Utility class; prevent instantiation.
	 */
	private EngineSupport() {
		// utility
	}

	/**
	 * Shared UCI-engine command options.
	 *
	 * @param input optional input file path
	 * @param fen optional single FEN
	 * @param protocolPath protocol TOML path
	 * @param nodesCap max node budget
	 * @param durationMillis max duration budget
	 * @param multipv optional MultiPV override
	 * @param threads optional thread override
	 * @param hash optional hash override
	 * @param wdl request WDL output
	 * @param noWdl disable WDL output
	 */
	public record UciOptions(
			Path input,
			String fen,
			String protocolPath,
			long nodesCap,
			long durationMillis,
			Integer multipv,
			Integer threads,
			Integer hash,
			boolean wdl,
			boolean noWdl) {
	}

	/**
	 * Parses common options used by external UCI-engine commands.
	 *
	 * @param a argument parser
	 * @param cmd command label for diagnostics
	 * @param defaultToStart whether to use the standard start position by default
	 * @return parsed common options
	 */
	public static UciOptions parseUciOptions(Argv a, String cmd, boolean defaultToStart) {
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		boolean startPos = a.flag(OPT_STARTPOS);
		boolean randomPos = a.flag(OPT_RANDOMPOS);
		String fen = a.string(OPT_FEN);
		String protocolPath = CommandSupport.optional(a.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT),
				Config.getProtocolPath());
		long nodesCap = Math.max(1, CommandSupport.optional(a.lng(OPT_MAX_NODES, OPT_NODES), Config.getMaxNodes()));
		long durationMillis = Math.max(1,
				CommandSupport.optionalDurationMs(a.duration(OPT_MAX_DURATION), Config.getMaxDuration()));
		Integer multipv = a.integer(OPT_MULTIPV);
		Integer threads = a.integer(OPT_THREADS);
		Integer hash = a.integer(OPT_HASH);
		boolean wdl = a.flag(OPT_WDL);
		boolean noWdl = a.flag(OPT_NO_WDL);
		List<String> rest = a.positionals();
		a.ensureConsumed();
		String resolvedFen = CommandSupport.resolveSelectedFen(cmd, fen, rest, startPos, randomPos, defaultToStart);
		validateWdlFlags(cmd, wdl, noWdl);
		return new UciOptions(input, resolvedFen, protocolPath, nodesCap, durationMillis, multipv, threads, hash, wdl,
				noWdl);
	}

	/**
	 * Validates that only one WDL toggle was provided.
	 *
	 * @param cmd command label for diagnostics
	 * @param wdl WDL-enable flag
	 * @param noWdl WDL-disable flag
	 */
	public static void validateWdlFlags(String cmd, boolean wdl, boolean noWdl) {
		if (wdl && noWdl) {
			throw new CommandFailure(cmd + ": only one of " + OPT_WDL + " or " + OPT_NO_WDL + " may be set", 2);
		}
	}

	/**
	 * Loads and validates a protocol TOML file or exits on failure.
	 *
	 * @param protocolPath path to the protocol TOML file
	 * @param verbose      whether to print stack traces on failure
	 * @return parsed protocol instance
	 */
	public static Protocol loadProtocolOrExit(String protocolPath, boolean verbose) {
		Path path = Paths.get(protocolPath);
		try {
			String toml = java.nio.file.Files.readString(path);
			Protocol protocol = new Protocol().fromToml(toml);
			String[] errors = protocol.collectValidationErrors();
			if (!protocol.assertValid()) {
				System.err.println("Protocol is missing required values:");
				for (String err : errors) {
					System.err.println("  - " + err);
				}
				System.exit(2);
			}
			if (errors.length > 0) {
				LogService.warn("Protocol has non-essential issues: " + errors.length);
			}
			return protocol;
		} catch (Exception e) {
			System.err.println("Failed to load protocol: " + e.getMessage());
			if (verbose) {
				e.printStackTrace(System.err);
			}
			System.exit(2);
			return null;
		}
	}
}
