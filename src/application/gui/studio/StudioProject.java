package application.gui.studio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import chess.core.Position;

/**
 * Plain-folder research project persistence for GUI v3.
 */
public final class StudioProject {

	/**
	 * Root project directory.
	 */
	private final Path root;

	/**
	 * Constructor.
	 *
	 * @param root project root
	 */
	private StudioProject(Path root) {
		this.root = root;
	}

	/**
	 * Opens or creates a project folder.
	 *
	 * @param root project directory
	 * @return project
	 * @throws IOException when the directory cannot be created
	 */
	public static StudioProject open(Path root) throws IOException {
		if (root == null) {
			throw new IllegalArgumentException("Project folder is required.");
		}
		Files.createDirectories(root);
		Files.createDirectories(root.resolve("outputs"));
		return new StudioProject(root);
	}

	/**
	 * Autosaves the current study state.
	 *
	 * @param position current position
	 * @param tree current game tree
	 * @param fenList loaded FEN list
	 * @throws IOException on write failure
	 */
	public void save(Position position, StudioGameTree tree, List<String> fenList) throws IOException {
		Files.createDirectories(root);
		Files.createDirectories(outputsPath());
		Properties props = new Properties();
		props.setProperty("schemaVersion", "1");
		props.setProperty("savedAt", Instant.now().toString());
		props.setProperty("currentFen", position == null ? "" : position.toString());
		try (var out = Files.newOutputStream(root.resolve("project.properties"))) {
			props.store(out, "ChessRTK Studio project");
		}
		if (fenList != null && !fenList.isEmpty()) {
			Files.write(root.resolve("positions.fen"), fenList, StandardCharsets.UTF_8);
		}
		if (tree != null) {
			Files.writeString(root.resolve("game.pgn"), tree.toPgn() + System.lineSeparator(), StandardCharsets.UTF_8);
		}
		touch(root.resolve("notes.jsonl"));
		touch(root.resolve("analysis.jsonl"));
	}

	/**
	 * Appends a note record.
	 *
	 * @param scope note scope
	 * @param key note key
	 * @param text note text
	 * @throws IOException on write failure
	 */
	public void appendNote(String scope, String key, String text) throws IOException {
		String line = "{\"ts\":\"" + escape(Instant.now().toString()) + "\",\"scope\":\""
				+ escape(scope) + "\",\"key\":\"" + escape(key) + "\",\"text\":\""
				+ escape(text) + "\"}" + System.lineSeparator();
		Files.writeString(root.resolve("notes.jsonl"), line, StandardCharsets.UTF_8,
				Files.exists(root.resolve("notes.jsonl"))
						? java.nio.file.StandardOpenOption.APPEND
						: java.nio.file.StandardOpenOption.CREATE);
	}

	/**
	 * Appends an analysis record.
	 *
	 * @param jsonLine JSON object text
	 * @throws IOException on write failure
	 */
	public void appendAnalysis(String jsonLine) throws IOException {
		String text = (jsonLine == null ? "{}" : jsonLine.strip()) + System.lineSeparator();
		Files.writeString(root.resolve("analysis.jsonl"), text, StandardCharsets.UTF_8,
				Files.exists(root.resolve("analysis.jsonl"))
						? java.nio.file.StandardOpenOption.APPEND
						: java.nio.file.StandardOpenOption.CREATE);
	}

	/**
	 * Returns project root.
	 *
	 * @return root path
	 */
	public Path root() {
		return root;
	}

	/**
	 * Returns outputs directory.
	 *
	 * @return outputs path
	 */
	public Path outputsPath() {
		return root.resolve("outputs");
	}

	/**
	 * Creates an empty file when it does not already exist.
	 *
	 * @param path file path
	 * @throws IOException on write failure
	 */
	private static void touch(Path path) throws IOException {
		if (!Files.exists(path)) {
			Files.writeString(path, "", StandardCharsets.UTF_8);
		}
	}

	/**
	 * Escapes a string for the small JSONL records written by this class.
	 *
	 * @param text source text
	 * @return escaped JSON string content
	 */
	private static String escape(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
