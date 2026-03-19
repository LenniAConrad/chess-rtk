package chess.tag.position;

import static chess.tag.core.Literals.*;

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
 * Resolves opening-book metadata for positions.
 * <p>
 * This utility looks up the current position in the configured ECO book and
 * emits opening-related tags when a match is available.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Opening {

	/**
	 * The default ECO book path used for opening lookups.
	 */
	private static final Path DEFAULT_ECO_BOOK = Path.of(ECO_BOOK_PATH);

	/**
	 * Ensures the opening-book warning is only logged once.
	 */
	private static final AtomicBoolean WARNED = new AtomicBoolean(false);

	/**
	 * The cached ECO book instance.
	 */
	private static Encyclopedia book = null;

	/**
	 * Tracks whether the opening subsystem has been disabled after a load failure.
	 */
	private static boolean disabled = false;

	/**
	 * Prevents instantiation of this utility class.
	 */
	private Opening() {
		// utility
	}

	/**
	 * Returns opening tags for the given position when the ECO book can resolve it.
	 *
	 * @param position the position to inspect
	 * @return an immutable list containing ECO and/or opening-name tags
	 * @throws NullPointerException if {@code position} is {@code null}
	 */
	public static List<String> tags(Position position) {
		Objects.requireNonNull(position, POSITION);

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
			tags.add(META_ECO_PREFIX + eco);
		}
		String name = node.getName();
		if (name != null && !name.isBlank()) {
			tags.add(META_OPENING_NAME_PREFIX + name.replace(String.valueOf(QUOTE), String.valueOf(BACKSLASH) + QUOTE)
                    + QUOTE);
		}
		return Collections.unmodifiableList(tags);
	}

	/**
	 * Returns the cached ECO book or loads it on first use.
	 *
	 * @return the loaded encyclopedia, or {@code null} when unavailable
	 */
	private static Encyclopedia bookOrNull() {
		Encyclopedia cached = book;
		if (cached != null) {
			return cached;
		}
		if (disabled) {
			return null;
		}
		if (!Files.isRegularFile(DEFAULT_ECO_BOOK)) {
			disabled = true;
			return null;
		}
		try {
			Encyclopedia loaded = Encyclopedia.of(DEFAULT_ECO_BOOK);
			book = loaded;
			return loaded;
		} catch (RuntimeException ex) {
			disabled = true;
			if (WARNED.compareAndSet(false, true)) {
				LogService.warn(OPENING_DISABLED_LOG + LogService.pathAbs(DEFAULT_ECO_BOOK) + CLOSE_PAREN);
			}
			return null;
		}
	}
}
