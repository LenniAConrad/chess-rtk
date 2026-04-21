package application.cli.command;

import static application.cli.Constants.CMD_RECORD;

import java.util.List;

import utility.Argv;

/**
 * Implements grouped {@code record <subcommand>} routing for record workflows.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordGroupCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordGroupCommand() {
		// utility
	}

	/**
	 * Handles {@code record <subcommand>}.
	 *
	 * @param a argument parser for the grouped command.
	 */
	public static void runRecord(Argv a) {
		List<String> rest = a.positionals();
		a.ensureConsumed();
		if (rest.isEmpty()) {
			printUsageAndExit();
			return;
		}

		String subcommand = rest.get(0);
		Argv nested = CommandGroupSupport.nestedArgv(rest);
		switch (subcommand) {
			case "export" -> runExport(rest);
			case "dataset" -> runDataset(rest);
			case "plain" -> RecordCommands.runRecordToPlain(nested);
			case "csv" -> RecordCommands.runRecordToCsv(nested);
			case "pgn" -> RecordCommands.runRecordToPgn(nested);
			case "puzzle-jsonl" -> RecordCommands.runRecordToPuzzleJsonl(nested);
			case "training-jsonl" -> RecordCommands.runRecordToTrainingJsonl(nested);
			case "npy" -> RecordCommands.runRecordToDataset(nested);
			case "lc0" -> RecordCommands.runRecordToLc0(nested);
			case "classifier" -> RecordCommands.runRecordToClassifier(nested);
			case "analysis-delta" -> RecordAnalysisDeltaCommand.runRecordAnalysisDelta(nested);
			case "files" -> RecordCommands.runRecords(nested);
			case "stats" -> StatsCommand.runStats(nested);
			case "tag-stats" -> StatsCommand.runStatsTags(nested);
			default -> {
				System.err.println(CMD_RECORD + ": unknown subcommand: " + subcommand);
				printUsageAndExit();
			}
		}
	}

	/**
	 * Handles {@code record export <format>}.
	 *
	 * @param rest grouped-command positionals
	 */
	private static void runExport(List<String> rest) {
		if (rest.size() < 2) {
			printExportUsageAndExit();
			return;
		}
		String format = rest.get(1);
		Argv nested = CommandGroupSupport.nestedArgv(rest, 2);
		switch (format) {
			case "plain" -> RecordCommands.runRecordToPlain(nested);
			case "csv" -> RecordCommands.runRecordToCsv(nested);
			case "pgn" -> RecordCommands.runRecordToPgn(nested);
			case "puzzle-jsonl" -> RecordCommands.runRecordToPuzzleJsonl(nested);
			case "training-jsonl" -> RecordCommands.runRecordToTrainingJsonl(nested);
			default -> {
				System.err.println(CMD_RECORD + " export: unknown format: " + format);
				printExportUsageAndExit();
			}
		}
	}

	/**
	 * Handles {@code record dataset <kind>}.
	 *
	 * @param rest grouped-command positionals
	 */
	private static void runDataset(List<String> rest) {
		if (rest.size() < 2 || rest.get(1).startsWith("-")) {
			RecordCommands.runRecordToDataset(CommandGroupSupport.nestedArgv(rest));
			return;
		}
		String kind = rest.get(1);
		Argv nested = CommandGroupSupport.nestedArgv(rest, 2);
		switch (kind) {
			case "npy" -> RecordCommands.runRecordToDataset(nested);
			case "lc0" -> RecordCommands.runRecordToLc0(nested);
			case "classifier" -> RecordCommands.runRecordToClassifier(nested);
			default -> {
				System.err.println(CMD_RECORD + " dataset: unknown kind: " + kind);
				printDatasetUsageAndExit();
			}
		}
	}

	/**
	 * Prints grouped command usage and exits with a usage status.
	 */
	private static void printUsageAndExit() {
		System.err.println("""
				usage: crtk record <subcommand> [options]

				subcommands:
				  export FORMAT      Export records as plain, csv, pgn, puzzle-jsonl, or training-jsonl
				  dataset KIND       Export tensors as npy, lc0, or classifier
				  files              Merge, filter, or split record files
				  stats              Summarize record files
				  tag-stats          Summarize tag distributions
				  analysis-delta     Compare parent/child analysis changes
				""");
		System.exit(2);
	}

	/**
	 * Prints {@code record export} usage and exits with a usage status.
	 */
	private static void printExportUsageAndExit() {
		System.err.println("""
				usage: crtk record export <format> [options]

				formats:
				  plain
				  csv
				  pgn
				  puzzle-jsonl
				  training-jsonl
				""");
		System.exit(2);
	}

	/**
	 * Prints {@code record dataset} usage and exits with a usage status.
	 */
	private static void printDatasetUsageAndExit() {
		System.err.println("""
				usage: crtk record dataset <kind> [options]

				kinds:
				  npy
				  lc0
				  classifier
				""");
		System.exit(2);
	}
}
