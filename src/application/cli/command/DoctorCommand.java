package application.cli.command;

import static application.cli.ConfigOps.validateConfigToml;
import static application.cli.ConfigOps.validateModelPath;
import static application.cli.ConfigOps.validateProtocolConfig;
import static application.cli.Constants.OPT_JSON;
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
	 * Stable schema marker for machine-readable doctor output.
	 */
	private static final String SCHEMA = "crtk.doctor.v1";

	/**
	 * Strict mode flag for turning warnings into a non-zero exit status.
	 */
	private static final String OPT_STRICT = "--strict";

	/**
	 * Immutable doctor report payload.
	 *
	 * @param status         overall status label
	 * @param javaVersion    resolved Java runtime version
	 * @param configPath     resolved CLI config path
	 * @param protocolPath   configured UCI protocol path
	 * @param engineInstances configured engine worker count
	 * @param output         configured output root
	 * @param warnings       non-fatal diagnostics
	 * @param errors         fatal diagnostics
	 * @param nativeBackends native backend availability rows
	 */
	private record DoctorReport(
			String status,
			String javaVersion,
			Path configPath,
			String protocolPath,
			int engineInstances,
			String output,
			List<String> warnings,
			List<String> errors,
			List<GpuCommand.Backend> nativeBackends) {
	}

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
		boolean json = a.flag(OPT_JSON);
		a.ensureConsumed();

		try {
			DoctorReport report = collectDoctorReport(strict);
			printDoctorReport(report, json);
			failOnDoctorReport(report, strict);
		} catch (CommandFailure failure) {
			throw failure;
		} catch (RuntimeException ex) {
			if (json) {
				printDoctorJson(failedReport(strict, ex));
			} else {
				System.out.println("doctor: failed");
				System.out.println("Errors:");
				System.out.println("  - " + ex.getMessage());
			}
			throw new CommandFailure("doctor: failed", ex, 2, verbose);
		}
	}

	/**
	 * Runs all doctor checks and returns the collected report.
	 *
	 * @param strict whether warnings should fail the command
	 * @return doctor report
	 */
	private static DoctorReport collectDoctorReport(boolean strict) {
		List<String> warnings = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		Config.reload();
		validateConfigToml(Config.getConfigPath(), warnings, errors);
		validateProtocolConfig(Config.getProtocolPath(), warnings, errors);
		validateModelPath("lc0-model-path", Config.getLc0ModelPath(), warnings);
		validateModelPath("t5-model-path", Config.getT5ModelPath(), warnings);
		validateDirectory("output", Config.getOutput(), warnings);
		return report(warnings, errors, strict, GpuCommand.backends());
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
	 * @param report doctor report to emit
	 * @param json whether JSON output is requested
	 */
	private static void printDoctorReport(DoctorReport report, boolean json) {
		if (json) {
			printDoctorJson(report);
		} else {
			printDoctorText(report);
		}
	}

	/**
	 * Throws the historic doctor exit status after a report was printed.
	 *
	 * @param report doctor report
	 * @param strict whether warnings should fail the command
	 */
	private static void failOnDoctorReport(DoctorReport report, boolean strict) {
		if (!report.errors().isEmpty()) {
			throw new CommandFailure("", 2);
		}
		if (strict && !report.warnings().isEmpty()) {
			throw new CommandFailure("", 1);
		}
	}

	/**
	 * Prints the human-readable doctor report.
	 *
	 * @param report doctor report
	 */
	private static void printDoctorText(DoctorReport report) {
		System.out.println("doctor: " + report.status());
		System.out.println("Java: " + report.javaVersion());
		System.out.println("Config: " + report.configPath().toAbsolutePath());
		System.out.println("Protocol: " + report.protocolPath());
		System.out.println("Engine instances: " + report.engineInstances());
		System.out.println("Output: " + report.output());
		printMessages("Warnings", report.warnings());
		printMessages("Errors", report.errors());
	}

	/**
	 * Prints the machine-readable doctor report.
	 *
	 * @param report doctor report
	 */
	private static void printDoctorJson(DoctorReport report) {
		System.out.println("{\"schema\":" + CommandSupport.jsonString(SCHEMA)
				+ ",\"status\":" + CommandSupport.jsonString(report.status())
				+ ",\"java\":" + CommandSupport.jsonNullableString(report.javaVersion())
				+ ",\"config\":" + CommandSupport.jsonString(report.configPath().toAbsolutePath().toString())
				+ ",\"protocol\":" + CommandSupport.jsonNullableString(report.protocolPath())
				+ ",\"engineInstances\":" + report.engineInstances()
				+ ",\"output\":" + CommandSupport.jsonNullableString(report.output())
				+ ",\"warnings\":" + CommandSupport.jsonStringArray(report.warnings())
				+ ",\"errors\":" + CommandSupport.jsonStringArray(report.errors())
				+ ",\"nativeBackends\":" + nativeBackendsJson(report.nativeBackends()) + "}");
	}

	/**
	 * Builds a normal doctor report.
	 *
	 * @param warnings collected warnings
	 * @param errors collected errors
	 * @param strict whether warnings fail the run
	 * @param nativeBackends native backend rows
	 * @return doctor report
	 */
	private static DoctorReport report(
			List<String> warnings,
			List<String> errors,
			boolean strict,
			List<GpuCommand.Backend> nativeBackends) {
		return new DoctorReport(
				status(warnings, errors, strict),
				System.getProperty("java.version"),
				Config.getConfigPath(),
				Config.getProtocolPath(),
				Config.getEngineInstances(),
				Config.getOutput(),
				List.copyOf(warnings),
				List.copyOf(errors),
				List.copyOf(nativeBackends));
	}

	/**
	 * Builds a failed doctor report for unexpected runtime failures.
	 *
	 * @param strict whether warnings fail the run
	 * @param ex exception to serialize
	 * @return failed doctor report
	 */
	private static DoctorReport failedReport(boolean strict, RuntimeException ex) {
		List<String> errors = List.of(errorMessage(ex));
		return new DoctorReport(
				status(List.of(), errors, strict),
				System.getProperty("java.version"),
				Config.getConfigPath(),
				Config.getProtocolPath(),
				Config.getEngineInstances(),
				Config.getOutput(),
				List.of(),
				errors,
				List.of());
	}

	/**
	 * Returns a stable user-facing exception message.
	 *
	 * @param ex exception to inspect
	 * @return non-empty message
	 */
	private static String errorMessage(RuntimeException ex) {
		String message = ex.getMessage();
		return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
	}

	/**
	 * Serializes native backend rows.
	 *
	 * @param backends native backend rows
	 * @return JSON array
	 */
	private static String nativeBackendsJson(List<GpuCommand.Backend> backends) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < backends.size(); i++) {
			GpuCommand.Backend backend = backends.get(i);
			if (i > 0) {
				sb.append(',');
			}
			sb.append("{\"name\":").append(CommandSupport.jsonString(backend.label()))
					.append(",\"loaded\":").append(backend.loaded())
					.append(",\"available\":").append(backend.available())
					.append(",\"deviceCount\":").append(backend.deviceCount())
					.append('}');
		}
		return sb.append(']').toString();
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
