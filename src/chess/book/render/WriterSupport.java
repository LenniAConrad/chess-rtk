package chess.book.render;

import java.util.Locale;

import chess.book.model.Book;
import chess.book.model.Element;
import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;

/**
 * Shared metadata and solution helpers for {@link Writer}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class WriterSupport {

	/**
	 * Utility class; prevent instantiation.
	 */
	private WriterSupport() {
		// utility
	}

	/**
	 * Builds subject metadata for a watermarked PDF.
	 *
	 * @param baseSubject base subject text
	 * @param watermarkId visible watermark identifier
	 * @return PDF subject metadata
	 */
	static String watermarkSubject(String baseSubject, String watermarkId) {
		String id = normalizeWatermarkId(watermarkId);
		return id.isBlank() ? baseSubject : baseSubject + "; Watermark ID " + id;
	}

	/**
	 * Returns either the caller-supplied watermark identifier or a deterministic
	 * book fingerprint.
	 *
	 * @param book source book metadata
	 * @param watermarkId optional caller-supplied identifier
	 * @return display-safe watermark identifier
	 */
	static String buildWatermarkId(Book book, String watermarkId) {
		String explicit = normalizeWatermarkId(watermarkId);
		if (!explicit.isBlank()) {
			return explicit;
		}
		StringBuilder key = new StringBuilder(1024 + book.getElements().length * 96)
				.append(blankTo(book.getFullTitle(), "")).append('\n')
				.append(blankTo(book.getAuthor(), "")).append('\n')
				.append(blankTo(book.getTime(), "")).append('\n')
				.append(blankTo(book.getLocation(), "")).append('\n')
				.append(book.getElements().length).append('\n');
		for (Element element : book.getElements()) {
			key.append(blankTo(element.getPosition(), "")).append('\n')
					.append(blankTo(element.getMoves(), "")).append('\n');
		}
		String hex = String.format(Locale.ROOT, "%016X", fnv1a64(key.toString()));
		return "CRTK-" + hex.substring(0, 4) + "-" + hex.substring(4, 8)
				+ "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16);
	}

	/**
	 * Validates the requested book model.
	 *
	 * @param book requested book instance
	 * @return the validated book instance
	 */
	static Book requireBook(Book book) {
		if (book == null) {
			throw new IllegalArgumentException("book cannot be null");
		}
		if (book.getElements().length == 0) {
			throw new IllegalArgumentException("book has no puzzle elements");
		}
		return book;
	}

	/**
	 * Pre-parses every puzzle solution into a final position and last move.
	 *
	 * @param book source book
	 * @return per-puzzle solution cache
	 */
	static SolutionInfo[] buildSolutions(Book book) {
		Element[] elements = book.getElements();
		SolutionInfo[] solutions = new SolutionInfo[elements.length];
		for (int i = 0; i < elements.length; i++) {
			solutions[i] = buildSolution(elements[i]);
		}
		return solutions;
	}

	/**
	 * Builds a short cover copyright line.
	 *
	 * @param book source book
	 * @return copyright lines
	 */
	static String[] buildCoverCopyright(Book book) {
		String year = blankTo(book.getTime(), "");
		String author = blankTo(book.getAuthor(), "");
		StringBuilder builder = new StringBuilder(64);
		builder.append("Copyright (c) ");
		if (!year.isBlank()) {
			builder.append(year).append(' ');
		}
		builder.append("by ");
		builder.append(author.isBlank() ? book.getFullTitle() : author);
		return new String[] { builder.toString().trim(), "All rights reserved" };
	}

	/**
	 * Parses one puzzle solution line.
	 *
	 * @param element puzzle element to parse
	 * @return parsed solution information
	 */
	private static SolutionInfo buildSolution(Element element) {
		Position start = new Position(element.getPosition());
		SAN.PlayedLine line = SAN.playLine(start, element.getMoves());
		if (line.isParsed()) {
			return new SolutionInfo(line.getResult(), line.getLastMove(),
					MoveText.figurine(line.lastSanWithMoveNumber()));
		}

		String fallbackSan = SAN.lastMoveToken(element.getMoves());
		String label = line.hasLastMove() ? line.lastSanWithMoveNumber() : fallbackSan;
		return new SolutionInfo(start.copy(), Move.NO_MOVE, MoveText.figurine(label));
	}

	/**
	 * Normalizes a watermark identifier for PDF text and metadata.
	 *
	 * @param watermarkId source identifier
	 * @return printable ASCII identifier, or empty string when absent
	 */
	static String normalizeWatermarkId(String watermarkId) {
		String safe = normalizeWhitespace(watermarkId);
		if (safe.isBlank()) {
			return "";
		}
		StringBuilder normalized = new StringBuilder(Math.min(safe.length(), 72));
		for (int i = 0; i < safe.length(); i++) {
			char ch = safe.charAt(i);
			normalized.append(ch >= 32 && ch <= 126 ? ch : '?');
			if (normalized.length() >= 72) {
				break;
			}
		}
		return normalized.toString();
	}

	/**
	 * Computes a stable 64-bit FNV-1a fingerprint.
	 *
	 * @param text source text
	 * @return unsigned hash bits stored in a Java long
	 */
	private static long fnv1a64(String text) {
		long hash = 0xcbf29ce484222325L;
		for (int i = 0; i < text.length(); i++) {
			hash ^= text.charAt(i);
			hash *= 0x100000001b3L;
		}
		return hash;
	}

	/**
	 * Returns a non-null string fallback.
	 *
	 * @param value source value
	 * @param fallback fallback value
	 * @return non-null string
	 */
	private static String blankTo(String value, String fallback) {
		return value == null ? fallback : value;
	}

	/**
	 * Normalizes whitespace to a single-line representation.
	 *
	 * @param text source text
	 * @return normalized text
	 */
	private static String normalizeWhitespace(String text) {
		return blankTo(text, "").replace('\r', '\n').replace('\n', ' ').replaceAll("\\s+", " ").trim();
	}
}
