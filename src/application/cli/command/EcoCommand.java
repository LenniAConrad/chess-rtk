package application.cli.command;

import static application.cli.Constants.OPT_BOOK;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_JSON;
import static application.cli.Constants.OPT_LIMIT;
import static application.cli.Constants.OPT_LINE;
import static application.cli.Constants.OPT_QUERY;
import static application.cli.Constants.OPT_QUERY_SHORT;
import static application.cli.Constants.OPT_STARTPOS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import application.cli.command.CommandSupport.OutputMode;
import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.eco.Encyclopedia;
import chess.eco.Entry;
import utility.Argv;

/**
 * CLI handlers for the {@code crtk eco} opening encyclopedia area.
 *
 * <p>
 * The command reads the same bundled {@link Encyclopedia} used by tagging and
 * the Workbench. Output order follows the deterministic book order except for
 * continuation counts, which are sorted by frequency and then move text.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EcoCommand {

	/**
	 * Default number of rows emitted by list-like commands.
	 */
	private static final int DEFAULT_LIMIT = 20;

	/**
	 * Exit code for invalid ECO book content.
	 */
	private static final int VALIDATION_FAILURE_EXIT = 3;

	/**
	 * Prevents instantiation.
	 */
	private EcoCommand() {
		// utility
	}

	/**
	 * Handles {@code crtk eco lookup}.
	 *
	 * @param argv parsed command-line arguments
	 */
	public static void runLookup(Argv argv) {
		String command = "eco lookup";
		EcoOptions options = parseEcoOptions(argv, command, false);
		Encyclopedia book = loadBook(options.bookPath(), command, options.verbose());
		Position position = resolvePosition(options, command, false);
		Entry match = book.getNode(position);
		printLookup(position, match, options.mode());
	}

	/**
	 * Handles {@code crtk eco search}.
	 *
	 * @param argv parsed command-line arguments
	 */
	public static void runSearch(Argv argv) {
		String command = "eco search";
		boolean verbose = argv.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path bookPath = argv.path(OPT_BOOK);
		OutputMode mode = CommandSupport.resolveOutputMode(argv, command);
		int limit = parseLimit(argv, command);
		String query = CommandSupport.trimToNull(argv.string(OPT_QUERY, OPT_QUERY_SHORT));
		List<String> positionals = argv.positionals();
		argv.ensureConsumed();
		if (query == null) {
			query = CommandSupport.trimToNull(String.join(" ", positionals));
		} else if (!positionals.isEmpty()) {
			throw new CommandFailure(command + ": use either " + OPT_QUERY + " or a positional query, not both", 2);
		}
		if (query == null) {
			throw new CommandFailure("Usage: crtk eco search --query TEXT", 2);
		}
		Encyclopedia book = loadBook(bookPath, command, verbose);
		List<Entry> matches = search(book.entries(), query, limit);
		printEntries(matches, mode);
	}

	/**
	 * Handles {@code crtk eco continuations}.
	 *
	 * @param argv parsed command-line arguments
	 */
	public static void runContinuations(Argv argv) {
		String command = "eco continuations";
		int limit = parseLimit(argv, command);
		EcoOptions options = parseEcoOptions(argv, command, true);
		Encyclopedia book = loadBook(options.bookPath(), command, options.verbose());
		Position position = resolvePosition(options, command, true);
		List<Continuation> continuations = continuations(book.entries(), position, limit);
		printContinuations(position, continuations, options.mode());
	}

	/**
	 * Handles {@code crtk eco validate}.
	 *
	 * @param argv parsed command-line arguments
	 */
	public static void runValidate(Argv argv) {
		String command = "eco validate";
		boolean verbose = argv.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path bookPath = argv.path(OPT_BOOK);
		boolean json = argv.flag(OPT_JSON);
		argv.ensureConsumed();

		Encyclopedia book = loadBook(bookPath, command, verbose);
		ValidationReport report = validate(book.entries());
		if (json) {
			System.out.println(validationJson(report));
		} else {
			System.out.println(report.ok() ? "ok" : "invalid");
			System.out.println("entries: " + report.entries());
			System.out.println("unique_codes: " + report.uniqueCodes());
			System.out.println("missing_codes: " + report.missingCodes().size());
			if (!report.missingCodes().isEmpty()) {
				System.out.println("missing: " + String.join(" ", report.missingCodes()));
			}
		}
		if (!report.ok()) {
			throw new CommandFailure(command + ": missing ECO codes: "
					+ String.join(" ", report.missingCodes()), VALIDATION_FAILURE_EXIT);
		}
	}

	private static EcoOptions parseEcoOptions(Argv argv, String command, boolean defaultToStart) {
		boolean verbose = argv.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path bookPath = argv.path(OPT_BOOK);
		OutputMode mode = CommandSupport.resolveOutputMode(argv, command);
		String line = CommandSupport.trimToNull(argv.string(OPT_LINE));
		boolean startPos = argv.flag(OPT_STARTPOS);
		String fen = CommandSupport.trimToNull(argv.string(OPT_FEN));
		List<String> positionals = argv.positionals();
		argv.ensureConsumed();
		return new EcoOptions(bookPath, mode, line, fen, positionals, startPos, defaultToStart, verbose);
	}

	private static int parseLimit(Argv argv, String command) {
		int limit = argv.integerOr(DEFAULT_LIMIT, OPT_LIMIT);
		if (limit < 0) {
			throw new CommandFailure(command + ": " + OPT_LIMIT + " must be non-negative", 2);
		}
		return limit;
	}

	private static Encyclopedia loadBook(Path path, String command, boolean verbose) {
		try {
			return path == null ? Encyclopedia.defaultBook() : Encyclopedia.of(path);
		} catch (RuntimeException ex) {
			throw new CommandFailure(command + ": failed to load ECO book: " + ex.getMessage(),
					ex, VALIDATION_FAILURE_EXIT, verbose);
		}
	}

	private static Position resolvePosition(EcoOptions options, String command, boolean defaultToStart) {
		if (options.line() != null) {
			if (options.fen() != null || options.startPos() || !options.positionals().isEmpty()) {
				throw new CommandFailure(command + ": use " + OPT_LINE
						+ " by itself, or choose a FEN selector", 2);
			}
			SAN.PlayedLine line = SAN.playLine(Setup.getStandardStartPosition(), options.line());
			if (!line.isParsed()) {
				throw new CommandFailure(command + ": invalid SAN token in " + OPT_LINE + ": "
						+ line.getInvalidToken(), 3);
			}
			return line.getResult();
		}
		Position position = CommandSupport.resolveSelectedPosition(
				command,
				options.fen(),
				options.positionals(),
				options.startPos(),
				false,
				defaultToStart || options.defaultToStart(),
				options.verbose());
		if (position == null) {
			throw new CommandFailure(command + " requires " + OPT_FEN + ", " + OPT_LINE
					+ ", " + OPT_STARTPOS + ", or a positional FEN", 2);
		}
		return position;
	}

	private static List<Entry> search(List<Entry> entries, String query, int limit) {
		String needle = query.toLowerCase(Locale.ROOT);
		List<Entry> matches = new ArrayList<>();
		for (Entry entry : entries) {
			if (matchesEntry(entry, needle)) {
				matches.add(entry);
				if (limit > 0 && matches.size() >= limit) {
					break;
				}
			}
		}
		return matches;
	}

	private static boolean matchesEntry(Entry entry, String needle) {
		return lower(entry.getECO()).contains(needle)
				|| lower(entry.getName()).contains(needle)
				|| lower(entry.getMovetext()).contains(needle);
	}

	private static String lower(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private static List<Continuation> continuations(List<Entry> entries, Position target, int limit) {
		Map<Short, MutableContinuation> byMove = new HashMap<>();
		long targetSignature = target.signatureCore();
		for (Entry entry : entries) {
			collectContinuation(entry, target, targetSignature, byMove);
		}
		List<Continuation> rows = byMove.values().stream()
				.map(item -> item.toContinuation(target))
				.sorted(Comparator
						.comparingInt(Continuation::count).reversed()
						.thenComparing(Continuation::san)
						.thenComparing(Continuation::uci))
				.toList();
		if (limit == 0 || rows.size() <= limit) {
			return rows;
		}
		return rows.subList(0, limit);
	}

	private static void collectContinuation(
			Entry entry,
			Position target,
			long targetSignature,
			Map<Short, MutableContinuation> byMove) {
		Position cursor = Setup.getStandardStartPosition();
		for (short move : entry.getMoves()) {
			if (cursor.signatureCore() == targetSignature && target.isLegalMove(move)) {
				byMove.computeIfAbsent(move, key -> new MutableContinuation(move, entry)).add(entry);
			}
			if (!cursor.isLegalMove(move)) {
				return;
			}
			cursor.play(move);
		}
	}

	private static ValidationReport validate(List<Entry> entries) {
		Set<String> codes = new HashSet<>();
		for (Entry entry : entries) {
			codes.add(entry.getECO());
		}
		List<String> missing = new ArrayList<>();
		for (char family = 'A'; family <= 'E'; family++) {
			for (int number = 0; number <= 99; number++) {
				String code = family + String.format("%02d", number);
				if (!codes.contains(code)) {
					missing.add(code);
				}
			}
		}
		return new ValidationReport(entries.size(), codes.size(), missing);
	}

	private static void printLookup(Position position, Entry match, OutputMode mode) {
		if (mode == OutputMode.JSON) {
			System.out.println("{\"fen\":" + q(position.toString()) + ",\"match\":"
					+ (match == null ? "null" : entryJson(match)) + "}");
			return;
		}
		if (mode == OutputMode.JSONL) {
			System.out.println(lookupJson(position, match));
			return;
		}
		if (match == null) {
			System.out.println("unknown\t-\t-");
		} else {
			System.out.println(entryText(match));
		}
	}

	private static void printEntries(List<Entry> entries, OutputMode mode) {
		if (mode == OutputMode.JSON) {
			StringBuilder sb = new StringBuilder("[");
			for (int i = 0; i < entries.size(); i++) {
				if (i > 0) {
					sb.append(',');
				}
				sb.append(entryJson(entries.get(i)));
			}
			System.out.println(sb.append(']'));
			return;
		}
		for (Entry entry : entries) {
			System.out.println(mode == OutputMode.JSONL ? entryJson(entry) : entryText(entry));
		}
	}

	private static void printContinuations(Position position, List<Continuation> continuations, OutputMode mode) {
		if (mode == OutputMode.JSON) {
			StringBuilder sb = new StringBuilder();
			sb.append("{\"fen\":").append(q(position.toString())).append(",\"continuations\":[");
			for (int i = 0; i < continuations.size(); i++) {
				if (i > 0) {
					sb.append(',');
				}
				sb.append(continuationJson(continuations.get(i)));
			}
			System.out.println(sb.append("]}"));
			return;
		}
		for (Continuation continuation : continuations) {
			System.out.println(mode == OutputMode.JSONL
					? continuationJson(continuation)
					: continuationText(continuation));
		}
	}

	private static String entryText(Entry entry) {
		return entry.getECO() + "\t" + entry.getName() + "\t" + entry.getMovetext();
	}

	private static String continuationText(Continuation continuation) {
		return continuation.san() + "\t" + continuation.uci() + "\t" + continuation.count()
				+ "\t" + continuation.eco() + "\t" + continuation.name() + "\t" + continuation.movetext();
	}

	private static String lookupJson(Position position, Entry match) {
		return "{\"fen\":" + q(position.toString()) + ",\"eco\":"
				+ (match == null ? "null" : q(match.getECO())) + ",\"name\":"
				+ (match == null ? "null" : q(match.getName())) + ",\"movetext\":"
				+ (match == null ? "null" : q(match.getMovetext())) + "}";
	}

	private static String entryJson(Entry entry) {
		return "{\"eco\":" + q(entry.getECO())
				+ ",\"name\":" + q(entry.getName())
				+ ",\"movetext\":" + q(entry.getMovetext())
				+ ",\"fen\":" + q(entry.getPosition().toString()) + "}";
	}

	private static String continuationJson(Continuation continuation) {
		return "{\"san\":" + q(continuation.san())
				+ ",\"uci\":" + q(continuation.uci())
				+ ",\"count\":" + continuation.count()
				+ ",\"eco\":" + q(continuation.eco())
				+ ",\"name\":" + q(continuation.name())
				+ ",\"movetext\":" + q(continuation.movetext()) + "}";
	}

	private static String validationJson(ValidationReport report) {
		return "{\"ok\":" + report.ok()
				+ ",\"entries\":" + report.entries()
				+ ",\"uniqueCodes\":" + report.uniqueCodes()
				+ ",\"missingCodes\":" + CommandSupport.jsonStringArray(report.missingCodes()) + "}";
	}

	private static String q(String value) {
		return CommandSupport.jsonString(value == null ? "" : value);
	}

	/**
	 * Parsed common options for position-oriented ECO commands.
	 */
	private record EcoOptions(
			Path bookPath,
			OutputMode mode,
			String line,
			String fen,
			List<String> positionals,
			boolean startPos,
			boolean defaultToStart,
			boolean verbose) {
	}

	/**
	 * Summary of parsed ECO code coverage.
	 */
	private record ValidationReport(
			int entries,
			int uniqueCodes,
			List<String> missingCodes) {

		private boolean ok() {
			return missingCodes.isEmpty();
		}
	}

	/**
	 * One aggregated next-move candidate from the ECO book.
	 */
	private record Continuation(
			String san,
			String uci,
			int count,
			String eco,
			String name,
			String movetext) {
	}

	/**
	 * Mutable accumulator used while counting continuation moves.
	 */
	private static final class MutableContinuation {

		private final short move;

		private final Entry first;

		private int count;

		private MutableContinuation(short move, Entry first) {
			this.move = move;
			this.first = first;
		}

		private void add(Entry ignored) {
			count++;
		}

		private Continuation toContinuation(Position position) {
			return new Continuation(
					SAN.toAlgebraic(position, move),
					Move.toString(move),
					count,
					first.getECO(),
					first.getName(),
					first.getMovetext());
		}
	}
}
