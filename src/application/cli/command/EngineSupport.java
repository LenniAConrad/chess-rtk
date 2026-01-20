package application.cli.command;

import java.nio.file.Path;
import java.nio.file.Paths;

import chess.debug.LogService;
import chess.uci.Protocol;

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
