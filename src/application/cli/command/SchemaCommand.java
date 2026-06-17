package application.cli.command;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import chess.schema.JsonParseException;
import chess.schema.JsonValue;
import chess.schema.Schemas;
import chess.schema.Validator;
import chess.schema.Violation;
import utility.Argv;

/**
 * CLI handlers for the {@code crtk schema} area.
 *
 * <p>Provides three deterministic verbs over the {@link Schemas} registry:
 * {@code list} (print all registered schema names), {@code show NAME}
 * (print one schema's source text), and {@code validate NAME [--input PATH]}
 * (validate a JSON document against the named schema, reporting violations
 * with deterministic JSON-pointer paths). All output is stable for a given
 * input; no wall-clock data leaks.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SchemaCommand {

	/**
	 * Exit code used when validation fails with one or more violations.
	 */
	private static final int VALIDATION_FAILURE_EXIT = 3;

	/**
	 * Exit code used for argument-shape failures (missing or unknown schema, etc.).
	 */
	private static final int USAGE_FAILURE_EXIT = 2;

	/**
	 * Utility class; prevent instantiation.
	 */
	private SchemaCommand() {
		// utility
	}

	/**
	 * Handles {@code crtk schema list}.
	 *
	 * @param argv parsed arguments (no flags supported)
	 */
	public static void runList(Argv argv) {
		argv.ensureConsumed();
		for (String name : Schemas.list()) {
			System.out.println(name);
		}
	}

	/**
	 * Handles {@code crtk schema show NAME}.
	 *
	 * @param argv parsed arguments containing the schema name as a positional
	 */
	public static void runShow(Argv argv) {
		List<String> positionals = argv.positionals();
		argv.ensureConsumed();
		if (positionals.isEmpty()) {
			throw new CommandFailure("Usage: crtk schema show <name>", USAGE_FAILURE_EXIT);
		}
		if (positionals.size() > 1) {
			throw new CommandFailure(
					"crtk schema show takes exactly one schema name", USAGE_FAILURE_EXIT);
		}
		String name = positionals.get(0);
		if (!Schemas.isKnown(name)) {
			throw new CommandFailure(
					"Unknown schema '" + name + "'. Known schemas: " + Schemas.list(),
					USAGE_FAILURE_EXIT);
		}
		System.out.println(Schemas.rawText(name).stripTrailing());
	}

	/**
	 * Handles {@code crtk schema validate NAME [--input PATH]}.
	 *
	 * <p>Reads the document from {@code --input} when present or from
	 * standard input otherwise, parses it, and applies the named schema.
	 * Prints {@code "ok"} on success or {@code "<pointer>: <message>"} for
	 * every discovered violation. Exits {@code 0} on valid input,
	 * {@value #VALIDATION_FAILURE_EXIT} on violations, or
	 * {@value #USAGE_FAILURE_EXIT} on argument errors.</p>
	 *
	 * @param argv parsed arguments
	 */
	public static void runValidate(Argv argv) {
		String inputPath = argv.string("--input", "-i");
		List<String> positionals = argv.positionals();
		argv.ensureConsumed();
		if (positionals.isEmpty()) {
			throw new CommandFailure(
					"Usage: crtk schema validate <name> [--input PATH]", USAGE_FAILURE_EXIT);
		}
		if (positionals.size() > 1) {
			throw new CommandFailure(
					"crtk schema validate takes exactly one schema name", USAGE_FAILURE_EXIT);
		}
		String name = positionals.get(0);
		if (!Schemas.isKnown(name)) {
			throw new CommandFailure(
					"Unknown schema '" + name + "'. Known schemas: " + Schemas.list(),
					USAGE_FAILURE_EXIT);
		}
		String document = readDocument(inputPath);
		JsonValue parsed;
		try {
			parsed = chess.schema.JsonParser.parse(document);
		} catch (JsonParseException ex) {
			throw new CommandFailure(
					"input is not valid JSON: " + ex.getMessage(), VALIDATION_FAILURE_EXIT);
		}
		Validator validator = Schemas.load(name);
		List<Violation> violations = validator.validate(parsed);
		if (violations.isEmpty()) {
			System.out.println("ok");
			return;
		}
		for (Violation violation : violations) {
			System.err.println(violation.render());
		}
		throw new CommandFailure(
				"document does not validate against schema '" + name + "' ("
						+ violations.size() + " violation" + (violations.size() == 1 ? "" : "s") + ")",
				VALIDATION_FAILURE_EXIT);
	}

	/**
	 * Reads the input document from {@code --input} or from standard input.
	 *
	 * @param inputPath optional path to the document; {@code null} reads stdin
	 * @return raw document text
	 */
	private static String readDocument(String inputPath) {
		if (inputPath == null) {
			try {
				return readAll(System.in);
			} catch (IOException ex) {
				throw new CommandFailure(
						"failed to read stdin: " + ex.getMessage(), USAGE_FAILURE_EXIT);
			}
		}
		try {
			return Files.readString(Path.of(inputPath), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new CommandFailure(
					"failed to read --input '" + inputPath + "': " + ex.getMessage(),
					USAGE_FAILURE_EXIT);
		}
	}

	/**
	 * Reads an entire stream as UTF-8 text.
	 *
	 * @param stream source stream
	 * @return decoded text
	 * @throws IOException when reading fails
	 */
	private static String readAll(InputStream stream) throws IOException {
		return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
	}
}
