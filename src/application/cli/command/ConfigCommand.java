package application.cli.command;

import static application.cli.ConfigOps.printValidationResults;
import static application.cli.ConfigOps.validateConfigToml;
import static application.cli.ConfigOps.validateModelPath;
import static application.cli.ConfigOps.validateProtocolConfig;
import static application.cli.Constants.OPT_JSON;

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
	 * Stable schema marker for machine-readable config output.
	 */
	private static final String SCHEMA = "crtk.config.v1";

	/**
	 * Utility class; prevent instantiation.
	 */
	private ConfigCommand() {
		// utility
	}

	/**
	 * Prints resolved configuration values to standard output.
	 *
	 * @param a argument parser for {@code config show}
	 */
	public static void runConfigShow(Argv a) {
		boolean json = a.flag(OPT_JSON);
		a.ensureConsumed();

		Config.reload();
		if (json) {
			printConfigJson();
			return;
		}
		printConfigText();
	}

	/**
	 * Prints resolved configuration values as text.
	 */
	private static void printConfigText() {
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
	 * Prints resolved configuration values as JSON.
	 */
	private static void printConfigJson() {
		System.out.println("{\"schema\":" + CommandSupport.jsonString(SCHEMA)
				+ ",\"config\":" + CommandSupport.jsonString(Config.getConfigPath().toAbsolutePath().toString())
				+ ",\"protocol\":" + CommandSupport.jsonNullableString(Config.getProtocolPath())
				+ ",\"lc0ModelPath\":" + CommandSupport.jsonNullableString(Config.getLc0ModelPath())
				+ ",\"t5ModelPath\":" + CommandSupport.jsonNullableString(Config.getT5ModelPath())
				+ ",\"output\":" + CommandSupport.jsonNullableString(Config.getOutput())
				+ ",\"engineInstances\":" + Config.getEngineInstances()
				+ ",\"maxNodes\":" + Config.getMaxNodes()
				+ ",\"maxDurationMs\":" + Config.getMaxDuration()
				+ ",\"puzzleAnalysisCache\":" + Config.getPuzzleAnalysisCacheSize()
				+ ",\"puzzleQuality\":" + CommandSupport.jsonNullableString(String.valueOf(Config.getPuzzleQuality()))
				+ ",\"puzzleWinning\":" + CommandSupport.jsonNullableString(String.valueOf(Config.getPuzzleWinning()))
				+ ",\"puzzleDrawing\":" + CommandSupport.jsonNullableString(String.valueOf(Config.getPuzzleDrawing()))
				+ ",\"puzzleAccelerate\":" + CommandSupport.jsonNullableString(String.valueOf(Config.getPuzzleAccelerate()))
				+ "}");
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
