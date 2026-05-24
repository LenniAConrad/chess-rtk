package application.cli.command;

import static application.cli.ConfigOps.printValidationResults;
import static application.cli.ConfigOps.validateConfigToml;
import static application.cli.ConfigOps.validateModelPath;
import static application.cli.ConfigOps.validateProtocolConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import application.Config;

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
	 * Prints resolved configuration values to standard output.
	 */
	public static void runConfigShow() {
		Config.reload();
		Path configPath = Config.getConfigPath();
		System.out.println("Config path: " + configPath.toAbsolutePath());
		System.out.println("Protocol path: " + Config.getProtocolPath());
		System.out.println("LC0 model path: " + Config.getLc0ModelPath());
		System.out.println("T5 model path: " + Config.getT5ModelPath());
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
	public static void runConfigValidate() {
		Config.reload();

		List<String> warnings = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		Path configPath = Config.getConfigPath();
		validateConfigToml(configPath, warnings, errors);
		validateProtocolConfig(Config.getProtocolPath(), warnings, errors);
		validateModelPath("lc0-model-path", Config.getLc0ModelPath(), warnings);
		validateModelPath("t5-model-path", Config.getT5ModelPath(), warnings);
		printValidationResults(warnings, errors);
	}
}
