package application.cli.command;

import static application.cli.ConfigOps.validateConfigToml;
import static application.cli.ConfigOps.validateModelPath;
import static application.cli.ConfigOps.validateProtocolConfig;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import application.Config;
import utility.Argv;

/**
 * Implements the {@code doctor} environment diagnostic command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DoctorCommand {

	/**
	 * Strict mode flag for turning warnings into a non-zero exit status.
	 */
	private static final String OPT_STRICT = "--strict";

	/**
	 * Utility class; prevent instantiation.
	 */
	private DoctorCommand() {
		// utility
	}

	/**
	 * Handles {@code doctor}.
	 *
	 * @param a argument parser for the command
	 */
	public static void runDoctor(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean strict = a.flag(OPT_STRICT);
		a.ensureConsumed();

		List<String> warnings = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		try {
			Config.reload();
			validateConfigToml(Config.getConfigPath(), warnings, errors);
			validateProtocolConfig(Config.getProtocolPath(), warnings, errors);
			validateModelPath("lc0-model-path", Config.getLc0ModelPath(), warnings);
			validateModelPath("t5-model-path", Config.getT5ModelPath(), warnings);
			validateDirectory("output", Config.getOutput(), warnings);
			printDoctorReport(warnings, errors, strict);
		} catch (CommandFailure failure) {
			throw failure;
		} catch (RuntimeException ex) {
			System.out.println("doctor: failed");
			System.out.println("Errors:");
			System.out.println("  - " + ex.getMessage());
			throw new CommandFailure("doctor: failed", ex, 2, verbose);
		}
	}

	/**
	 * Validates an advisory output directory path.
	 *
	 * @param label    diagnostic label
	 * @param pathText configured path text
	 * @param warnings mutable warning list
	 */
	private static void validateDirectory(String label, String pathText, List<String> warnings) {
		if (pathText == null || pathText.isBlank()) {
			warnings.add(label + " path is empty");
			return;
		}
		try {
			Path directory = Path.of(pathText).normalize();
			if (Files.exists(directory) && !Files.isDirectory(directory)) {
				warnings.add(label + " exists but is not a directory: " + directory.toAbsolutePath());
			}
		} catch (RuntimeException ex) {
			warnings.add("Invalid " + label + " path: " + pathText + " (" + ex.getMessage() + ")");
		}
	}

	/**
	 * Prints the final doctor report and exits on errors or strict warnings.
	 *
	 * @param warnings collected warnings
	 * @param errors   collected errors
	 * @param strict   whether warnings should fail the command
	 */
	private static void printDoctorReport(List<String> warnings, List<String> errors, boolean strict) {
		System.out.println("doctor: " + status(warnings, errors, strict));
		System.out.println("Java: " + System.getProperty("java.version"));
		System.out.println("Config: " + Config.getConfigPath().toAbsolutePath());
		System.out.println("Protocol: " + Config.getProtocolPath());
		System.out.println("Engine instances: " + Config.getEngineInstances());
		System.out.println("Output: " + Config.getOutput());
		printMessages("Warnings", warnings);
		printMessages("Errors", errors);
		if (!errors.isEmpty()) {
			throw new CommandFailure("", 2);
		}
		if (strict && !warnings.isEmpty()) {
			throw new CommandFailure("", 1);
		}
	}

	/**
	 * Formats the headline status.
	 *
	 * @param warnings collected warnings
	 * @param errors   collected errors
	 * @param strict   whether warnings fail the run
	 * @return status label
	 */
	private static String status(List<String> warnings, List<String> errors, boolean strict) {
		if (!errors.isEmpty()) {
			return "failed";
		}
		if (!warnings.isEmpty()) {
			return strict ? "failed-strict" : "ok-with-warnings";
		}
		return "ok";
	}

	/**
	 * Prints an optional diagnostic list.
	 *
	 * @param label    list label
	 * @param messages messages to print
	 */
	private static void printMessages(String label, List<String> messages) {
		if (messages.isEmpty()) {
			return;
		}
		System.out.println(label + ":");
		for (String message : messages) {
			System.out.println("  - " + message);
		}
	}
}
