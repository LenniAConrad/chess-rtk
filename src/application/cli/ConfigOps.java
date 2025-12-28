package application.cli;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import chess.uci.Protocol;
import utility.Toml;

/**
 * Configuration validation helpers for the CLI.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class ConfigOps {

	/**
	 * Utility class; prevent instantiation.
	 */
	private ConfigOps() {
		// utility
	}

	/**
	 * Validates the CLI configuration TOML file and adds parse diagnostics to the provided lists.
	 *
	 * @param configPath path to the CLI configuration TOML file
	 * @param warnings   mutable list receiving non-fatal warnings
	 * @param errors     mutable list receiving fatal validation problems
	 */
	public static void validateConfigToml(Path configPath, List<String> warnings, List<String> errors) {
		if (Files.notExists(configPath)) {
			errors.add("Missing config file: " + configPath.toAbsolutePath());
			return;
		}
		try (BufferedReader reader = Files.newBufferedReader(configPath)) {
			Toml toml = Toml.load(reader);
			if (!toml.getErrors().isEmpty()) {
				warnings.add("Config TOML parse issues: " + toml.getErrors().size());
			}
		} catch (Exception e) {
			errors.add("Failed to read config TOML: " + e.getMessage());
		}
	}

	/**
	 * Validates the configured protocol file path and parses the protocol for further validation.
	 *
	 * @param protocolPathValue protocol file path string extracted from the CLI config
	 * @param warnings          mutable list receiving non-fatal warnings
	 * @param errors            mutable list receiving fatal validation problems
	 */
	public static void validateProtocolConfig(String protocolPathValue, List<String> warnings, List<String> errors) {
		if (protocolPathValue == null || protocolPathValue.isEmpty()) {
			errors.add("protocol-path is empty in config");
			return;
		}
		Path protocolPath = Path.of(protocolPathValue);
		if (Files.notExists(protocolPath)) {
			errors.add("Missing protocol TOML: " + protocolPath.toAbsolutePath());
			return;
		}
		validateProtocolFile(protocolPath, warnings, errors);
	}

	/**
	 * Parses a protocol TOML file, validates its contents, and checks that the declared engine is on PATH.
	 *
	 * @param protocolPath path to the protocol TOML file
	 * @param warnings     mutable list receiving non-fatal warnings
	 * @param errors       mutable list receiving fatal validation problems
	 */
	public static void validateProtocolFile(Path protocolPath, List<String> warnings, List<String> errors) {
		try {
			String toml = Files.readString(protocolPath);
			Protocol protocol = new Protocol().fromToml(toml);
			appendProtocolIssues(protocol, warnings, errors);
			String enginePath = protocol.getPath();
			if (!PathOps.isExecutableOnPath(enginePath)) {
				errors.add("Engine executable not found on PATH: " + enginePath);
			}
		} catch (Exception e) {
			errors.add("Failed to parse protocol TOML: " + e.getMessage());
		}
	}

	/**
	 * Appends protocol validation issues to the provided warning/error lists.
	 *
	 * @param protocol protocol instance to inspect
	 * @param warnings mutable list receiving non-fatal warnings
	 * @param errors   mutable list receiving fatal validation problems
	 */
	public static void appendProtocolIssues(Protocol protocol, List<String> warnings, List<String> errors) {
		String[] validation = protocol.collectValidationErrors();
		if (validation.length > 0) {
			if (!protocol.assertValid()) {
				errors.add("Protocol validation issues:");
				Collections.addAll(errors, validation);
			} else {
				warnings.add("Protocol has non-essential issues: " + validation.length);
			}
		}
		if (!protocol.assertExtras()) {
			warnings.add("Protocol missing optional fields (extras)");
		}
	}

	/**
	 * Prints aggregated validation warnings/errors and exits with the appropriate status code.
	 *
	 * @param warnings list of warning messages accumulated during validation
	 * @param errors   list of error messages accumulated during validation
	 */
	public static void printValidationResults(List<String> warnings, List<String> errors) {
		if (warnings.isEmpty() && errors.isEmpty()) {
			System.out.println("Config validation OK.");
			return;
		}
		if (!warnings.isEmpty()) {
			System.out.println("Warnings:");
			for (String warn : warnings) {
				System.out.println("  - " + warn);
			}
		}
		if (!errors.isEmpty()) {
			System.out.println("Errors:");
			for (String err : errors) {
				System.out.println("  - " + err);
			}
		}
		System.exit(errors.isEmpty() ? 1 : 2);
	}
}
