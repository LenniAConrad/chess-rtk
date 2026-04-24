package application.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import utility.Argv;

/**
 * Immutable-ish command metadata node used for CLI dispatch and help rendering.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CliCommand {

	/**
	 * Canonical command token for this node.
	 */
	private final String name;

	/**
	 * One-line summary shown in grouped help output.
	 */
	private final String summary;

	/**
	 * Optional longer description.
	 */
	private String about;

	/**
	 * Optional usage tail appended after the command path.
	 */
	private String usageTail;

	/**
	 * Optional legacy help key used to extract detailed option blocks.
	 */
	private String helpKey;

	/**
	 * Optional command handler for executable nodes.
	 */
	private Consumer<Argv> handler;

	/**
	 * Local aliases accepted under the same parent.
	 */
	private final List<String> aliases = new ArrayList<>();

	/**
	 * Example invocations for help output.
	 */
	private final List<String> examples = new ArrayList<>();

	/**
	 * Command-style conventions for help output.
	 */
	private final List<String> conventions = new ArrayList<>();

	/**
	 * Related command paths for help output.
	 */
	private final List<String> related = new ArrayList<>();

	/**
	 * Canonical child list in insertion order.
	 */
	private final List<CliCommand> children = new ArrayList<>();

	/**
	 * Token lookup including aliases.
	 */
	private final Map<String, CliCommand> childrenByToken = new LinkedHashMap<>();

	/**
	 * Parent command node, or {@code null} for the root.
	 */
	private CliCommand parent;

	/**
	 * Creates a new command node.
	 *
	 * @param name    canonical token
	 * @param summary one-line summary
	 */
	private CliCommand(String name, String summary) {
		this.name = Objects.requireNonNull(name, "name");
		this.summary = Objects.requireNonNull(summary, "summary");
	}

	/**
	 * Creates a new group node.
	 *
	 * @param name    canonical token
	 * @param summary one-line summary
	 * @return group command node
	 */
	public static CliCommand group(String name, String summary) {
		return new CliCommand(name, summary);
	}

	/**
	 * Creates a new executable leaf node.
	 *
	 * @param name    canonical token
	 * @param summary one-line summary
	 * @param handler command handler
	 * @return leaf command node
	 */
	public static CliCommand leaf(String name, String summary, Consumer<Argv> handler) {
		return new CliCommand(name, summary).handler(handler);
	}

	/**
	 * Adds a longer description.
	 *
	 * @param value description text
	 * @return this node
	 */
	public CliCommand about(String value) {
		this.about = value;
		return this;
	}

	/**
	 * Adds a usage tail.
	 *
	 * @param value usage suffix after the command path
	 * @return this node
	 */
	public CliCommand usage(String value) {
		this.usageTail = value;
		return this;
	}

	/**
	 * Adds a legacy help key.
	 *
	 * @param value help lookup key
	 * @return this node
	 */
	public CliCommand helpKey(String value) {
		this.helpKey = value;
		return this;
	}

	/**
	 * Assigns or replaces the handler.
	 *
	 * @param value command handler
	 * @return this node
	 */
	public CliCommand handler(Consumer<Argv> value) {
		this.handler = value;
		return this;
	}

	/**
	 * Adds a local alias under the same parent.
	 *
	 * @param alias alias token
	 * @return this node
	 */
	public CliCommand alias(String alias) {
		if (alias != null && !alias.isBlank()) {
			aliases.add(alias);
		}
		return this;
	}

	/**
	 * Adds an example invocation.
	 *
	 * @param example command example
	 * @return this node
	 */
	public CliCommand example(String example) {
		if (example != null && !example.isBlank()) {
			examples.add(example);
		}
		return this;
	}

	/**
	 * Adds a command-style convention.
	 *
	 * @param convention convention text
	 * @return this node
	 */
	public CliCommand convention(String convention) {
		if (convention != null && !convention.isBlank()) {
			conventions.add(convention);
		}
		return this;
	}

	/**
	 * Adds a related command path.
	 *
	 * @param commandPath related command path without the {@code crtk} prefix
	 * @return this node
	 */
	public CliCommand related(String commandPath) {
		if (commandPath != null && !commandPath.isBlank()) {
			related.add(commandPath);
		}
		return this;
	}

	/**
	 * Adds a child command and registers its aliases.
	 *
	 * @param child child node
	 * @return this node
	 */
	public CliCommand add(CliCommand child) {
		Objects.requireNonNull(child, "child");
		child.parent = this;
		registerToken(child.name, child);
		for (String alias : child.aliases) {
			registerToken(alias, child);
		}
		children.add(child);
		return this;
	}

	/**
	 * Returns the canonical command token.
	 *
	 * @return token
	 */
	public String name() {
		return name;
	}

	/**
	 * Returns the one-line summary.
	 *
	 * @return summary
	 */
	public String summary() {
		return summary;
	}

	/**
	 * Returns the optional longer description.
	 *
	 * @return description or {@code null}
	 */
	public String about() {
		return about;
	}

	/**
	 * Returns the optional usage tail.
	 *
	 * @return usage tail or {@code null}
	 */
	public String usageTail() {
		return usageTail;
	}

	/**
	 * Returns the legacy help key.
	 *
	 * @return help key or {@code null}
	 */
	public String helpKey() {
		return helpKey;
	}

	/**
	 * Returns the optional handler.
	 *
	 * @return handler or {@code null}
	 */
	public Consumer<Argv> handler() {
		return handler;
	}

	/**
	 * Returns local alias tokens.
	 *
	 * @return aliases
	 */
	public List<String> aliases() {
		return List.copyOf(aliases);
	}

	/**
	 * Returns example invocations.
	 *
	 * @return examples
	 */
	public List<String> examples() {
		return List.copyOf(examples);
	}

	/**
	 * Returns command-style conventions.
	 *
	 * @return conventions
	 */
	public List<String> conventions() {
		return List.copyOf(conventions);
	}

	/**
	 * Returns related commands.
	 *
	 * @return related commands
	 */
	public List<String> related() {
		return List.copyOf(related);
	}

	/**
	 * Returns canonical children in insertion order.
	 *
	 * @return children
	 */
	public List<CliCommand> children() {
		return List.copyOf(children);
	}

	/**
	 * Returns the parent node.
	 *
	 * @return parent or {@code null}
	 */
	public CliCommand parent() {
		return parent;
	}

	/**
	 * Resolves a child token, including aliases.
	 *
	 * @param token child token
	 * @return matching child or {@code null}
	 */
	public CliCommand child(String token) {
		return childrenByToken.get(token);
	}

	/**
	 * Returns whether the node has registered children.
	 *
	 * @return true when children exist
	 */
	public boolean hasChildren() {
		return !children.isEmpty();
	}

	/**
	 * Returns whether the node can execute a handler directly.
	 *
	 * @return true when a handler exists
	 */
	public boolean isRunnable() {
		return handler != null;
	}

	/**
	 * Returns whether the node is the synthetic root.
	 *
	 * @return true for the root node
	 */
	public boolean isRoot() {
		return parent == null;
	}

	/**
	 * Returns the canonical command path without the {@code crtk} prefix.
	 *
	 * @return canonical path
	 */
	public String commandPath() {
		if (isRoot()) {
			return "";
		}
		if (parent == null || parent.isRoot()) {
			return name;
		}
		return parent.commandPath() + " " + name;
	}

	/**
	 * Returns full alias paths for this node.
	 *
	 * @return alias command paths without the {@code crtk} prefix
	 */
	public List<String> aliasPaths() {
		if (isRoot() || aliases.isEmpty()) {
			return List.of();
		}
		String prefix = (parent == null || parent.isRoot()) ? "" : parent.commandPath() + " ";
		List<String> out = new ArrayList<>(aliases.size());
		for (String alias : aliases) {
			out.add(prefix + alias);
		}
		return List.copyOf(out);
	}

	/**
	 * Registers a child token and rejects duplicates.
	 *
	 * @param token child token
	 * @param child child node
	 */
	private void registerToken(String token, CliCommand child) {
		CliCommand existing = childrenByToken.putIfAbsent(token, child);
		if (existing != null) {
			throw new IllegalStateException("Duplicate CLI token under '" + commandPath() + "': " + token);
		}
	}
}
