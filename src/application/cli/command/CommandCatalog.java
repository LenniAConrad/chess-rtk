package application.cli.command;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import application.cli.CliCommand;
import application.cli.CliRegistry;
import utility.Json;

/**
 * Serializes the central CLI command registry into a deterministic,
 * machine-readable JSON catalog ({@code crtk.cli.catalog.v1}).
 *
 * <p>
 * The catalog is the stable contract that agents, SDKs, editors, and the
 * workbench command mirror consume instead of scraping prose help. It is
 * <em>generated</em> from {@link CliRegistry} and the canonical built-in help
 * text ({@link HelpCommand}) so it can never drift from what the CLI actually
 * exposes. Output is deterministic: children follow registry insertion order
 * and no wall-clock or environment-dependent values are emitted.
 * </p>
 *
 * <p>
 * Per-command flags are parsed from the same option blocks {@code crtk help}
 * renders; the raw block is also included verbatim as {@code optionsHelp} so a
 * consumer always has the authoritative text alongside the parsed view.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CommandCatalog {

	/**
	 * Stable schema identifier stamped into every emitted catalog.
	 */
	public static final String SCHEMA_VERSION = "crtk.cli.catalog.v1";

	/**
	 * Provenance note describing how the {@code flags} arrays are derived.
	 */
	private static final String NOTES =
			"Generated from the CLI registry and built-in help text; deterministic and dependency-free. "
			+ "Each command's flags are parsed from its canonical help block, which is also included verbatim "
			+ "as optionsHelp.";

	/**
	 * Utility class; prevent instantiation.
	 */
	private CommandCatalog() {
		// utility
	}

	/**
	 * Builds the full catalog for every top-level command area.
	 *
	 * @return catalog JSON text without a trailing newline
	 */
	public static String toJson() {
		return buildCatalog(CliRegistry.root().children());
	}

	/**
	 * Builds a scoped catalog rooted at a single command node.
	 *
	 * @param node command node to serialize
	 * @return catalog JSON text without a trailing newline
	 */
	public static String toJson(CliCommand node) {
		return buildCatalog(List.of(node));
	}

	/**
	 * Prints the catalog to a stream, optionally scoped to a command path.
	 *
	 * @param path command path tokens, or empty for the full catalog
	 * @param out  target stream
	 * @return process exit code ({@code 0} on success, {@code 2} for an unknown path)
	 */
	public static int print(List<String> path, PrintStream out) {
		if (path == null || path.isEmpty()) {
			out.println(toJson());
			return 0;
		}
		CliCommand node = CliRegistry.resolve(path);
		if (node == null || node.isRoot()) {
			System.err.println("Unknown command for help: " + String.join(" ", path));
			return 2;
		}
		out.println(toJson(node));
		return 0;
	}

	/**
	 * Builds the catalog envelope around the given top-level command nodes.
	 *
	 * @param commands command nodes to serialize at the top level
	 * @return catalog JSON text without a trailing newline
	 */
	private static String buildCatalog(List<CliCommand> commands) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		appendLine(sb, 1, "\"schemaVersion\": " + str(SCHEMA_VERSION) + ",");
		appendLine(sb, 1, "\"tool\": \"crtk\",");
		appendLine(sb, 1, "\"notes\": " + str(NOTES) + ",");
		appendLine(sb, 1, "\"exitCodes\": {");
		appendLine(sb, 2, "\"0\": " + str("success") + ",");
		appendLine(sb, 2, "\"2\": " + str("usage error, unknown command, or unexpected argument") + ",");
		appendLine(sb, 2, "\"3\": " + str("invalid input or command failure (details on stderr)"));
		appendLine(sb, 1, "},");
		sb.append(indent(1)).append("\"commands\": [\n");
		for (int idx = 0; idx < commands.size(); idx++) {
			sb.append(renderNode(commands.get(idx), 2));
			sb.append(idx < commands.size() - 1 ? ",\n" : "\n");
		}
		sb.append(indent(1)).append("]\n");
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Renders one command node, including its children, as a JSON object.
	 *
	 * @param node  command node
	 * @param depth indentation depth of the object's opening brace
	 * @return JSON object text starting at the node's indentation
	 */
	private static String renderNode(CliCommand node, int depth) {
		String ind = indent(depth);
		String fieldIndent = indent(depth + 1);
		List<String> fields = new ArrayList<>();
		fields.add(fieldIndent + "\"path\": " + str(node.commandPath()));
		fields.add(fieldIndent + "\"name\": " + str(node.name()));
		fields.add(fieldIndent + "\"summary\": " + str(node.summary()));
		fields.add(fieldIndent + "\"group\": " + node.hasChildren());
		fields.add(fieldIndent + "\"runnable\": " + node.isRunnable());
		fields.add(fieldIndent + "\"usage\": " + str(HelpCommand.renderedUsage(node)));
		if (node.about() != null && !node.about().isBlank()) {
			fields.add(fieldIndent + "\"about\": " + str(node.about()));
		}
		addStringArray(fields, fieldIndent, "aliases", node.aliases());
		addStringArray(fields, fieldIndent, "aliasPaths", node.aliasPaths());
		addStringArray(fields, fieldIndent, "conventions", node.conventions());
		addStringArray(fields, fieldIndent, "examples", node.examples());
		addStringArray(fields, fieldIndent, "related", node.related());
		if (!node.hasChildren()) {
			String options = HelpCommand.optionsHelp(node);
			if (options != null && !options.isBlank()) {
				List<Flag> flags = parseFlags(options);
				if (!flags.isEmpty()) {
					fields.add(renderFlags(flags, depth + 1));
				}
				fields.add(fieldIndent + "\"optionsHelp\": " + str(options));
			}
		}
		if (node.hasChildren()) {
			fields.add(renderChildren(node.children(), depth + 1));
		}
		return ind + "{\n" + String.join(",\n", fields) + "\n" + ind + "}";
	}

	/**
	 * Renders the {@code children} array field for a group node.
	 *
	 * @param children child command nodes
	 * @param depth    indentation depth of the field key
	 * @return JSON field text for the children array
	 */
	private static String renderChildren(List<CliCommand> children, int depth) {
		String ind = indent(depth);
		StringBuilder sb = new StringBuilder();
		sb.append(ind).append("\"children\": [\n");
		for (int k = 0; k < children.size(); k++) {
			sb.append(renderNode(children.get(k), depth + 1));
			sb.append(k < children.size() - 1 ? ",\n" : "\n");
		}
		sb.append(ind).append("]");
		return sb.toString();
	}

	/**
	 * Renders the {@code flags} array field for a leaf node.
	 *
	 * @param flags parsed flag descriptors
	 * @param depth indentation depth of the field key
	 * @return JSON field text for the flags array
	 */
	private static String renderFlags(List<Flag> flags, int depth) {
		String ind = indent(depth);
		String itemIndent = indent(depth + 1);
		String entryIndent = indent(depth + 2);
		StringBuilder sb = new StringBuilder();
		sb.append(ind).append("\"flags\": [\n");
		for (int k = 0; k < flags.size(); k++) {
			Flag flag = flags.get(k);
			List<String> entries = new ArrayList<>();
			entries.add(entryIndent + "\"names\": " + strArray(flag.names));
			if (flag.arg != null) {
				entries.add(entryIndent + "\"arg\": " + str(flag.arg));
			}
			entries.add(entryIndent + "\"description\": " + str(flag.description));
			sb.append(itemIndent).append("{\n");
			sb.append(String.join(",\n", entries)).append("\n");
			sb.append(itemIndent).append("}");
			sb.append(k < flags.size() - 1 ? ",\n" : "\n");
		}
		sb.append(ind).append("]");
		return sb.toString();
	}

	/**
	 * Parses flag descriptors from a canonical help option block.
	 *
	 * <p>
	 * Lines whose trimmed form starts with {@code -} become flags; sub-section
	 * headers (lines ending with {@code :}) and standalone prose are ignored for
	 * the structured view but preserved in the verbatim {@code optionsHelp} text.
	 * Indented continuation lines extend the previous flag's description.
	 * </p>
	 *
	 * @param optionsBlock raw option block including its marker line
	 * @return parsed flag descriptors in source order
	 */
	private static List<Flag> parseFlags(String optionsBlock) {
		List<Flag> out = new ArrayList<>();
		Flag current = null;
		for (String raw : optionsBlock.split("\n", -1)) {
			String trimmed = raw.strip();
			if (trimmed.isEmpty()) {
				current = null;
			} else if (trimmed.startsWith("-")) {
				current = parseFlagLine(trimmed);
				if (current != null) {
					out.add(current);
				}
			} else if (trimmed.endsWith(":")) {
				current = null;
			} else if (current != null) {
				current.description = current.description.isEmpty()
						? trimmed
						: current.description + " " + trimmed;
			}
		}
		return out;
	}

	/**
	 * Parses a single flag line into names, an optional argument metavar, and text.
	 *
	 * @param trimmed trimmed flag line (e.g. {@code --input|-i PATH  Input file})
	 * @return parsed flag descriptor, or {@code null} when no flag name is present
	 */
	private static Flag parseFlagLine(String trimmed) {
		int sep = indexOfDoubleSpace(trimmed);
		String head = sep < 0 ? trimmed : trimmed.substring(0, sep).strip();
		String description = sep < 0 ? "" : trimmed.substring(sep).strip();
		int space = head.indexOf(' ');
		String spec = space < 0 ? head : head.substring(0, space);
		String arg = space < 0 ? "" : head.substring(space + 1).strip();
		List<String> names = new ArrayList<>();
		for (String token : spec.split("\\|")) {
			String name = token.strip();
			if (!name.isEmpty()) {
				names.add(name);
			}
		}
		if (names.isEmpty()) {
			return null;
		}
		Flag flag = new Flag();
		flag.names = names;
		flag.arg = arg.isEmpty() ? null : arg;
		flag.description = description;
		return flag;
	}

	/**
	 * Finds the first index of a run of two or more spaces.
	 *
	 * @param text input text
	 * @return index of the first double space, or {@code -1} when absent
	 */
	private static int indexOfDoubleSpace(String text) {
		for (int k = 0; k + 1 < text.length(); k++) {
			if (text.charAt(k) == ' ' && text.charAt(k + 1) == ' ') {
				return k;
			}
		}
		return -1;
	}

	/**
	 * Appends an optional string-array field when the list is non-empty.
	 *
	 * @param fields    accumulating field list
	 * @param indent    field indentation
	 * @param key       JSON key
	 * @param values    string values
	 */
	private static void addStringArray(List<String> fields, String indent, String key, List<String> values) {
		if (values != null && !values.isEmpty()) {
			fields.add(indent + "\"" + key + "\": " + strArray(values));
		}
	}

	/**
	 * Renders a compact JSON array of strings.
	 *
	 * @param values string values
	 * @return JSON array text
	 */
	private static String strArray(List<String> values) {
		StringBuilder sb = new StringBuilder("[");
		for (int k = 0; k < values.size(); k++) {
			if (k > 0) {
				sb.append(",");
			}
			sb.append(str(values.get(k)));
		}
		return sb.append("]").toString();
	}

	/**
	 * Renders a JSON string literal, escaping the value.
	 *
	 * @param value source value
	 * @return quoted, escaped JSON string
	 */
	private static String str(String value) {
		return "\"" + (value == null ? "" : Json.esc(value)) + "\"";
	}

	/**
	 * Appends an indented line terminated by a newline.
	 *
	 * @param sb    target buffer
	 * @param depth indentation depth
	 * @param text  line content
	 */
	private static void appendLine(StringBuilder sb, int depth, String text) {
		sb.append(indent(depth)).append(text).append("\n");
	}

	/**
	 * Returns two-space indentation for the given depth.
	 *
	 * @param depth indentation depth
	 * @return indentation string
	 */
	private static String indent(int depth) {
		return "  ".repeat(depth);
	}

	/**
	 * Mutable holder for one parsed CLI flag descriptor.
	 */
	private static final class Flag {

		/**
		 * Accepted flag tokens (canonical plus aliases).
		 */
		private List<String> names = new ArrayList<>();

		/**
		 * Optional argument metavar, or {@code null} for boolean flags.
		 */
		private String arg;

		/**
		 * Human-readable description text.
		 */
		private String description = "";
	}
}
