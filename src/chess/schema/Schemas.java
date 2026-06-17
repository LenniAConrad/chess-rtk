package chess.schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that loads named JSON Schemas from the filesystem or classpath.
 *
 * <p>Schemas live as {@code schemas/<name>.schema.json} under the repository
 * root for filesystem builds and at the same path on the classpath for
 * jar-packaged builds. New schemas are added by appending their name to
 * {@link #KNOWN} so the catalog is intentional and reviewable.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Schemas {

	/**
	 * Canonical list of registered schema names.
	 */
	private static final List<String> KNOWN = List.of(
			"crtk.cli.catalog.v1",
			"crtk.record.v2",
			"crtk.record.training-jsonl.v1",
			"crtk.dataset.manifest.v1",
			"crtk.pgn.store.manifest.v1",
			"crtk.review.ply.v1",
			"crtk.review.study_unit.v1");

	/**
	 * Repository-relative directory containing schema files.
	 */
	private static final String SCHEMA_DIRECTORY = "schemas";

	/**
	 * Cache of loaded validators by schema name.
	 */
	private static final Map<String, Validator> VALIDATOR_CACHE = new HashMap<>();

	/**
	 * Cache of loaded source text by schema name.
	 */
	private static final Map<String, String> TEXT_CACHE = new HashMap<>();

	/**
	 * Utility class; prevent instantiation.
	 */
	private Schemas() {
		// utility
	}

	/**
	 * Returns the registered schema names in deterministic registry order.
	 *
	 * @return immutable list of schema names
	 */
	public static List<String> list() {
		return KNOWN;
	}

	/**
	 * Indicates whether a name is registered.
	 *
	 * @param name schema name
	 * @return {@code true} when the name is in {@link #KNOWN}
	 */
	public static boolean isKnown(String name) {
		return KNOWN.contains(name);
	}

	/**
	 * Loads (and caches) the source text for a named schema.
	 *
	 * @param name schema name
	 * @return raw schema text
	 * @throws IllegalArgumentException when the name is not registered or the file is missing
	 */
	public static String rawText(String name) {
		String cached;
		synchronized (TEXT_CACHE) {
			cached = TEXT_CACHE.get(name);
		}
		if (cached != null) {
			return cached;
		}
		ensureKnown(name);
		String text = readSchemaText(name);
		synchronized (TEXT_CACHE) {
			TEXT_CACHE.put(name, text);
		}
		return text;
	}

	/**
	 * Loads (and caches) the validator for a named schema.
	 *
	 * @param name schema name
	 * @return validator instance
	 * @throws IllegalArgumentException when the name is not registered or the file is missing
	 */
	public static Validator load(String name) {
		Validator cached;
		synchronized (VALIDATOR_CACHE) {
			cached = VALIDATOR_CACHE.get(name);
		}
		if (cached != null) {
			return cached;
		}
		Validator validator = Validator.fromText(rawText(name));
		synchronized (VALIDATOR_CACHE) {
			VALIDATOR_CACHE.put(name, validator);
		}
		return validator;
	}

	/**
	 * Verifies that a name is registered before attempting to load it.
	 *
	 * @param name schema name
	 */
	private static void ensureKnown(String name) {
		if (!isKnown(name)) {
			throw new IllegalArgumentException("Unknown schema name: '" + name + "'. Known names: " + KNOWN);
		}
	}

	/**
	 * Reads a schema's source text from the classpath or filesystem.
	 *
	 * @param name schema name
	 * @return raw schema text
	 */
	private static String readSchemaText(String name) {
		String fileName = name + ".schema.json";
		String classpathResource = "/" + SCHEMA_DIRECTORY + "/" + fileName;
		try (InputStream stream = Schemas.class.getResourceAsStream(classpathResource)) {
			if (stream != null) {
				return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read classpath schema '" + name + "'", e);
		}
		Path filesystemPath = Path.of(SCHEMA_DIRECTORY, fileName);
		if (Files.exists(filesystemPath)) {
			try {
				return Files.readString(filesystemPath, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new UncheckedIOException(
						"Failed to read schema file " + filesystemPath, e);
			}
		}
		throw new IllegalArgumentException(
				"Schema '" + name + "' not found on classpath or at " + filesystemPath);
	}
}
