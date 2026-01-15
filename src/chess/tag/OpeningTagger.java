package chess.tag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import chess.core.Position;
import chess.debug.LogService;
import chess.eco.Encyclopedia;
import chess.eco.Entry;

/**
 * Emits opening metadata tags (ECO code + opening name) for positions that match
 * the configured ECO book.
 *
 * <p>
 * The ECO mapping is optional: if {@code config/book.eco.toml} is missing or
 * cannot be parsed, this tagger returns no tags and does not fail the calling
 * pipeline.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class OpeningTagger {

	private static final Path DEFAULT_ECO_BOOK = Path.of("config/book.eco.toml");

	private static final AtomicBoolean WARNED = new AtomicBoolean(false);

	private static volatile Encyclopedia BOOK = null;

	private static volatile boolean DISABLED = false;

	private OpeningTagger() {
		// utility
	}

	public static List<String> tags(Position position) {
		Objects.requireNonNull(position, "position");

		if (position.isChess960()) {
			return Collections.emptyList();
		}

		Encyclopedia book = bookOrNull();
		if (book == null) {
			return Collections.emptyList();
		}

		Entry node = book.getNode(position);
		if (node == null) {
			return Collections.emptyList();
		}

		List<String> tags = new ArrayList<>(2);
		String eco = node.getECO();
		if (eco != null && !eco.isBlank()) {
			tags.add("eco: " + eco);
		}
		String name = node.getName();
		if (name != null && !name.isBlank()) {
			tags.add("opening: " + name);
		}
		return Collections.unmodifiableList(tags);
	}

	private static Encyclopedia bookOrNull() {
		Encyclopedia cached = BOOK;
		if (cached != null) {
			return cached;
		}
		if (DISABLED) {
			return null;
		}
		if (!Files.isRegularFile(DEFAULT_ECO_BOOK)) {
			DISABLED = true;
			return null;
		}
		try {
			Encyclopedia loaded = Encyclopedia.of(DEFAULT_ECO_BOOK);
			BOOK = loaded;
			return loaded;
		} catch (RuntimeException ex) {
			DISABLED = true;
			if (WARNED.compareAndSet(false, true)) {
				LogService.warn("Opening tags disabled: unable to load ECO book (" + LogService.pathAbs(DEFAULT_ECO_BOOK) + ")");
			}
			return null;
		}
	}
}

