package application.gui.studio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Goal-based CRTK task catalog for GUI v3.
 */
public final class StudioTaskCatalog {

	/**
	 * Immutable list of known tasks.
	 */
	private final List<StudioTask> tasks;

	/**
	 * Constructor.
	 *
	 * @param tasks task definitions
	 */
	private StudioTaskCatalog(List<StudioTask> tasks) {
		this.tasks = List.copyOf(tasks);
	}

	/**
	 * Creates the default v1 catalog.
	 *
	 * @return catalog
	 */
	public static StudioTaskCatalog defaults() {
		List<StudioTask> list = new ArrayList<>();
		list.add(task("validate-fen", "Validate current FEN", "Position",
				"Check whether the current FEN is legal and normalized.", List.of("fen", "validate"), true, false));
		list.add(task("normalize-fen", "Normalize current FEN", "Position",
				"Serialize the current FEN in CRTK's canonical form.", List.of("fen", "normalize"), true, false));
		list.add(task("legal-both", "List legal moves", "Moves",
				"Show legal moves as UCI and SAN.", List.of("move", "list", "--format", "both"), true, false));
		list.add(task("bestmove", "Find best move", "Engine",
				"Run a bounded engine bestmove search.", List.of("engine", "bestmove", "--format", "both"), true, false));
		list.add(task("analyze", "Analyze position", "Engine",
				"Run engine analysis for the current position.", List.of("engine", "analyze"), true, false));
		list.add(task("perft", "Run perft", "Correctness",
				"Count legal move tree nodes for the current position.", List.of("engine", "perft", "--depth", "3"),
				true, true));
		return new StudioTaskCatalog(list);
	}

	/**
	 * Returns all tasks.
	 *
	 * @return task list
	 */
	public List<StudioTask> tasks() {
		return tasks;
	}

	/**
	 * Finds a task.
	 *
	 * @param id task id
	 * @return optional task
	 */
	public Optional<StudioTask> find(String id) {
		return tasks.stream().filter(t -> t.id().equals(id)).findFirst();
	}

	/**
	 * Builds CRTK arguments for a task.
	 *
	 * @param task task
	 * @param fen current FEN
	 * @param options additional options
	 * @return argument list after the executable
	 */
	public static List<String> argsFor(StudioTask task, String fen, Map<String, String> options) {
		List<String> args = new ArrayList<>(task.baseArgs());
		if (task.supportsFen() && fen != null && !fen.isBlank()) {
			args.add("--fen");
			args.add(fen);
		}
		if (options != null) {
			for (Map.Entry<String, String> entry : options.entrySet()) {
				if (entry.getKey() == null || entry.getKey().isBlank()) {
					continue;
				}
				args.add(entry.getKey());
				if (entry.getValue() != null && !entry.getValue().isBlank()) {
					args.add(entry.getValue());
				}
			}
		}
		return args;
	}

	/**
	 * Builds a copyable command preview.
	 *
	 * @param task task
	 * @param fen current FEN
	 * @param options additional options
	 * @return command preview
	 */
	public static String preview(StudioTask task, String fen, Map<String, String> options) {
		List<String> parts = new ArrayList<>();
		parts.add("crtk");
		parts.addAll(argsFor(task, fen, options));
		return shellJoin(parts);
	}

	/**
	 * Returns empty options map for callers that need a mutable map.
	 *
	 * @return mutable map
	 */
	public static Map<String, String> emptyOptions() {
		return new LinkedHashMap<>();
	}

	/**
	 * Creates a task descriptor.
	 *
	 * @param id task id
	 * @param label display label
	 * @param group task group
	 * @param description task description
	 * @param args base CRTK arguments
	 * @param supportsFen whether the task accepts a FEN argument
	 * @param advanced whether the task is advanced
	 * @return task descriptor
	 */
	private static StudioTask task(String id, String label, String group, String description,
			List<String> args, boolean supportsFen, boolean advanced) {
		return new StudioTask(id, label, group, description, args, supportsFen, advanced);
	}

	/**
	 * Joins command parts for shell display.
	 *
	 * @param parts command parts
	 * @return shell command
	 */
	private static String shellJoin(List<String> parts) {
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(quote(part));
		}
		return sb.toString();
	}

	/**
	 * Quotes one shell command part when needed.
	 *
	 * @param part command part
	 * @return quoted command part
	 */
	private static String quote(String part) {
		if (part == null) {
			return "''";
		}
		if (part.matches("[A-Za-z0-9_./:=+-]+")) {
			return part;
		}
		return "'" + part.replace("'", "'\"'\"'") + "'";
	}
}
