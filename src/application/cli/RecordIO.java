package application.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import chess.struct.Record;
import utility.Json;

/**
 * Helpers for streaming {@link chess.struct.Record} dumps from disk.
 *
 * <p>Supports both newline-delimited JSON Lines and JSON arrays by delegating
 * parsing to the {@link utility.Json} utilities.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class RecordIO {

	/**
	 * Large text buffer used for bulk record and JSONL inputs.
	 */
	private static final int TEXT_BUFFER_SIZE = 1 << 20;

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordIO() {
		// utility
	}

	/**
	 * Consumer callback invoked for each successfully parsed record and once per
	 * invalid payload.
	 */
	public interface RecordConsumer {

		/**
		 * Called for each {@link Record} instance parsed from the current input.
		 *
		 * @param rec successfully parsed record
		 */
		void accept(Record rec);

		/**
		 * Called whenever an input fragment cannot be parsed into a record.
		 *
		 * <p>Implementations should track failures and/or log diagnostics as needed.
		 */
		void invalid();
	}

	/**
	 * Streams records from the specified file and forwards them to the consumer.
	 *
	 * @param input    path to a JSON array or newline-delimited records file
	 * @param verbose  whether to print stack traces for skipped records
	 * @param label    label printed with any parse errors
	 * @param consumer receiver for parsed records and invalid entries
	 * @throws IOException if reading the file fails
	 */
	public static void streamRecordFile(
			Path input,
			boolean verbose,
			String label,
			RecordConsumer consumer) throws IOException {
		streamRecordFile(input, verbose, label, consumer, null);
	}

	/**
	 * Streams records from the specified file while reporting cumulative bytes read.
	 *
	 * @param input        path to a JSON array or newline-delimited records file
	 * @param verbose      whether to print stack traces for skipped records
	 * @param label        label printed with any parse errors
	 * @param consumer     receiver for parsed records and invalid entries
	 * @param byteProgress optional callback receiving cumulative bytes read
	 * @throws IOException if reading the file fails
	 */
	public static void streamRecordFile(
			Path input,
			boolean verbose,
			String label,
			RecordConsumer consumer,
			LongConsumer byteProgress) throws IOException {
		if (isJsonArrayFile(input)) {
			Json.streamTopLevelObjects(input, objJson -> handleRecordJson(objJson, verbose, label, consumer), byteProgress);
			return;
		}
		try (InputStream in = Files.newInputStream(input);
				InputStream progressIn = Json.progressInput(in, byteProgress);
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(progressIn, StandardCharsets.UTF_8),
						TEXT_BUFFER_SIZE)) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				handleRecordJson(trimmed, verbose, label, consumer);
			}
		}
	}

	/**
	 * Streams raw JSON objects from a record file (array or JSONL).
	 *
	 * @param input    path to a JSON array or newline-delimited records file
	 * @param consumer receiver for each raw JSON object
	 * @throws IOException if reading the file fails
	 */
	public static void streamRecordJson(Path input, Consumer<String> consumer) throws IOException {
		streamRecordJson(input, consumer, null);
	}

	/**
	 * Streams raw JSON objects from a record file while reporting cumulative bytes
	 * read.
	 *
	 * @param input        path to a JSON array or newline-delimited records file
	 * @param consumer     receiver for each raw JSON object
	 * @param byteProgress optional callback receiving cumulative bytes read
	 * @throws IOException if reading the file fails
	 */
	public static void streamRecordJson(Path input, Consumer<String> consumer, LongConsumer byteProgress) throws IOException {
		if (isJsonArrayFile(input)) {
			Json.streamTopLevelObjects(input, consumer, byteProgress);
			return;
		}
		try (InputStream in = Files.newInputStream(input);
				InputStream progressIn = Json.progressInput(in, byteProgress);
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(progressIn, StandardCharsets.UTF_8),
						TEXT_BUFFER_SIZE)) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				consumer.accept(trimmed);
			}
		}
	}

	/**
	 * Parses a single record JSON fragment, notifying the consumer of success or failure.
	 *
	 * @param json     JSON text representing a record
	 * @param verbose  whether to log stack traces on parse failures
	 * @param label    label printed with diagnostics
	 * @param consumer consumer to notify about parsing results
	 */
	private static void handleRecordJson(
			String json,
			boolean verbose,
			String label,
			RecordConsumer consumer) {
		try {
			Record rec = Record.fromJson(json);
			if (rec == null) {
				consumer.invalid();
				return;
			}
			consumer.accept(rec);
		} catch (Exception ex) {
			consumer.invalid();
			if (verbose) {
				System.err.println(label + ": skipped invalid record: " + ex.getMessage());
			}
		}
	}

	/**
	 * Detects whether the file begins with a '[' (accounting for BOM/whitespace)
	 * and therefore contains a JSON array rather than newline-delimited entries.
	 *
	 * @param input path of the file to inspect
	 * @return {@code true} when the file looks like a JSON array
	 * @throws IOException when the file cannot be read
	 */
	public static boolean isJsonArrayFile(Path input) throws IOException {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(Files.newInputStream(input), StandardCharsets.UTF_8),
				TEXT_BUFFER_SIZE)) {
			int c;
			while ((c = reader.read()) != -1) {
				boolean skip = c == '\uFEFF' || Character.isWhitespace(c);
				if (!skip) {
					return c == '[';
				}
			}
		}
		return false;
	}
}
