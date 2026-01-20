package application.cli.command;

import static application.cli.ConfigOps.printValidationResults;
import static application.cli.ConfigOps.validateConfigToml;
import static application.cli.ConfigOps.validateProtocolConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import application.Config;
import utility.Argv;

/**
 * Implements {@code config} subcommands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ConfigCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private ConfigCommand() {
		// utility
	}

	/**
	 * Handles {@code config}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runConfig(Argv a) {
		List<String> rest = a.positionals();
		a.ensureConsumed();

		if (rest.isEmpty()) {
			System.err.println("config requires a subcommand: show | validate");
			System.exit(2);
			return;
		}
		if (rest.size() > 1) {
			System.err.println("config accepts only one subcommand: show | validate");
			System.exit(2);
			return;
		}

		String sub = rest.get(0);
		switch (sub) {
			case "show" -> runConfigShow();
			case "validate" -> runConfigValidate();
			default -> {
				System.err.println("Unknown config subcommand: " + sub);
				System.exit(2);
			}
		}
	}

	/**
	 * Prints resolved configuration values to standard output.
	 */
	private static void runConfigShow() {
		Config.reload();
		Path configPath = Config.getConfigPath();
		System.out.println("Config path: " + configPath.toAbsolutePath());
		System.out.println("Protocol path: " + Config.getProtocolPath());
		System.out.println("Output: " + Config.getOutput());
		System.out.println("Engine instances: " + Config.getEngineInstances());
		System.out.println("Max nodes: " + Config.getMaxNodes());
		System.out.println("Max duration (ms): " + Config.getMaxDuration());
		System.out.println("Puzzle analysis cache: " + Config.getPuzzleAnalysisCacheSize());
		System.out.println("Puzzle quality: " + Config.getPuzzleQuality());
		System.out.println("Puzzle winning: " + Config.getPuzzleWinning());
		System.out.println("Puzzle drawing: " + Config.getPuzzleDrawing());
		System.out.println("Puzzle accelerate: " + Config.getPuzzleAccelerate());
	}

	/**
	 * Validates config and protocol files, printing any warnings/errors.
	 */
	private static void runConfigValidate() {
		Config.reload();

		List<String> warnings = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		Path configPath = Config.getConfigPath();
		validateConfigToml(configPath, warnings, errors);
		validateProtocolConfig(Config.getProtocolPath(), warnings, errors);
		printValidationResults(warnings, errors);
	}
}
