package application.cli.command;

import java.util.List;

import utility.Argv;

/**
 * Shared helpers for grouped CLI command routers.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class CommandGroupSupport {

	/**
	 * Utility class; prevent instantiation.
	 */
	private CommandGroupSupport() {
		// utility
	}

	/**
	 * Builds an argument parser for the tokens after the grouped subcommand.
	 *
	 * @param rest grouped-command positionals
	 * @return parser for arguments after the first token
	 */
	static Argv nestedArgv(List<String> rest) {
		return nestedArgv(rest, 1);
	}

	/**
	 * Builds an argument parser for grouped-command tokens after {@code startIndex}.
	 *
	 * @param rest       grouped-command positionals
	 * @param startIndex first token to keep in the nested parser
	 * @return parser for the requested tail
	 */
	static Argv nestedArgv(List<String> rest, int startIndex) {
		return new Argv(rest.subList(startIndex, rest.size()).toArray(new String[0]));
	}
}
