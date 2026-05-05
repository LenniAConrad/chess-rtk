package chess.book.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import chess.book.model.Book;
import chess.book.model.Element;
import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.struct.Record;
import chess.uci.Output;

/**
 * Builds puzzle collection book manifests from analyzed puzzle records.
 *
 * <p>
 * The builder expects each accepted record to provide a starting position plus
 * at least one principal-variation move in the embedded analysis payload. The
 * first PV is converted into move-numbered SAN text for the native book
 * renderer.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings({"java:S1192", "java:S135", "java:S6213"})
public final class Builder {

	/**
	 * Utility class; prevent instantiation.
	 */
	private Builder() {
		// utility
	}

	/**
	 * Builds one puzzle collection book model from puzzle records.
	 *
	 * @param records input records
	 * @param options build options
	 * @return build result with counts and the built book model
	 */
	public static BuildResult build(Iterable<Record> records, BuildOptions options) {
		if (records == null) {
			throw new IllegalArgumentException("records cannot be null");
		}
		BuildOptions safeOptions = options == null ? new BuildOptions() : options;
		List<Element> elements = new ArrayList<>();
		int skippedWithoutPosition = 0;
		int skippedWithoutVariation = 0;

		for (Record record : records) {
			if (record == null || record.getPosition() == null) {
				skippedWithoutPosition++;
				continue;
			}
			Element element = toElement(record);
			if (element == null) {
				skippedWithoutVariation++;
				continue;
			}
			elements.add(element);
		}

		if (elements.isEmpty()) {
			throw new IllegalArgumentException("no usable puzzles found in record input");
		}

		int puzzleCount = elements.size();
		String title = safeOptions.getTitle();
		String subtitle = defaultSubtitle(safeOptions.getSubtitle(), puzzleCount);
		Book book = new Book()
				.setTitle(title)
				.setSubtitle(subtitle)
				.setAuthor(safeOptions.getAuthor())
				.setTime(safeOptions.getTime())
				.setLocation(safeOptions.getLocation())
				.setLanguage(safeOptions.getLanguage())
				.setPages(safeOptions.getPages())
				.setTableFrequency(safeOptions.getTableFrequency())
				.setPuzzleRows(safeOptions.getPuzzleRows())
				.setPuzzleColumns(safeOptions.getPuzzleColumns())
				.setImprint(safeOptions.getImprint())
				.setDedication(safeOptions.getDedication())
				.setIntroduction(defaultIntroduction(safeOptions.getIntroduction(), puzzleCount, title, subtitle))
				.setHowToRead(safeOptions.getHowToRead())
				.setBlurb(defaultBlurb(safeOptions.getBlurb(), puzzleCount, title, subtitle))
				.setLink(safeOptions.getLink())
				.setAfterword(safeOptions.getAfterword())
				.setElements(elements.toArray(new Element[0]));

		return new BuildResult(book, puzzleCount, skippedWithoutPosition, skippedWithoutVariation);
	}

	/**
	 * Converts one record into a book element when a usable position and PV exist.
	 *
	 * @param record source record
	 * @return book element, or {@code null} when the record has no usable PV
	 */
	private static Element toElement(Record record) {
		Position start = record.getPosition();
		if (start == null) {
			return null;
		}
		Output best = record.getAnalysis() == null ? null : record.getAnalysis().getBestOutput();
		if (best == null) {
			return null;
		}
		short[] moves = best.getMoves();
		if (moves == null || moves.length == 0 || moves[0] == Move.NO_MOVE) {
			return null;
		}
		String sanLine = formatSanLine(start, moves);
		if (sanLine.isBlank()) {
			return null;
		}
		return new Element(start.toString(), sanLine);
	}

	/**
	 * Formats one PV as move-numbered SAN text.
	 *
	 * @param start starting position
	 * @param pv PV moves in compact form
	 * @return move-numbered SAN line, or blank on failure
	 */
	public static String formatSanLine(Position start, short[] pv) {
		if (start == null || pv == null || pv.length == 0) {
			return "";
		}
		List<String> sanMoves = new ArrayList<>(pv.length);
		Position cursor = start.copy();
		for (short move : pv) {
			if (move == Move.NO_MOVE) {
				break;
			}
			try {
				sanMoves.add(SAN.toAlgebraic(cursor, move));
				cursor.play(move);
			} catch (RuntimeException ex) {
				break;
			}
		}
		if (sanMoves.isEmpty()) {
			return "";
		}
		return formatLine(sanMoves, start.isWhiteToMove(), Math.max(1, start.fullMoveNumber()));
	}

	/**
	 * Formats SAN tokens with PGN-style move numbers.
	 *
	 * @param moves SAN tokens
	 * @param whiteToMove whether the first move belongs to White
	 * @param fullMove starting full-move number
	 * @return move-numbered SAN line
	 */
	private static String formatLine(List<String> moves, boolean whiteToMove, int fullMove) {
		StringBuilder sb = new StringBuilder(moves.size() * 8);
		int moveNumber = Math.max(1, fullMove);
		boolean white = whiteToMove;
		for (int i = 0; i < moves.size(); i++) {
			if (white) {
				sb.append(moveNumber).append(". ");
			} else {
				if (i == 0) {
					sb.append(moveNumber).append("... ");
				}
				moveNumber++;
			}
			sb.append(moves.get(i));
			white = !white;
			if (i + 1 < moves.size()) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	/**
	 * Returns the explicit subtitle or a generic count-based fallback.
	 *
	 * @param subtitle explicit subtitle
	 * @param count accepted puzzle count
	 * @return subtitle text
	 */
	private static String defaultSubtitle(String subtitle, int count) {
		if (subtitle != null && !subtitle.isBlank()) {
			return subtitle;
		}
		return String.format(Locale.ROOT, "%,d Chess Puzzles", count);
	}

	/**
	 * Returns explicit introduction paragraphs or puzzle collection defaults.
	 *
	 * @param introduction explicit introduction paragraphs
	 * @param count accepted puzzle count
	 * @param title title text
	 * @param subtitle subtitle text
	 * @return introduction paragraphs
	 */
	private static String[] defaultIntroduction(String[] introduction, int count, String title, String subtitle) {
		if (introduction != null && introduction.length > 0) {
			return introduction;
		}
		String countText = String.format(Locale.ROOT, "%,d", count);
		String fullTitle = subtitle == null || subtitle.isBlank() ? blankTo(title, "Chess Puzzle Collection")
				: blankTo(title, "Chess Puzzle Collection") + ": " + subtitle;
		return new String[] {
				fullTitle + " is a high-volume training book built around repetition, pattern recognition, and practical solving speed.",
				"This volume contains " + countText
						+ " curated positions laid out in the puzzle collection format: puzzle diagrams on the left, resolved positions on the right, and recurring lookup tables for fast review.",
				"Treat the book as a timed training run or work through it in smaller sessions. The goal is to recognize the decisive idea quickly, confirm it against the solution spread, and keep building fluency.",
				"Whether you use it as a blitz warm-up, a daily tactics block, or a long-form puzzle grind, the format is designed to keep you moving while still giving every position a clear answer path." };
	}

	/**
	 * Returns explicit blurb paragraphs or compact default back-cover copy.
	 *
	 * @param blurb explicit blurb paragraphs
	 * @param count accepted puzzle count
	 * @param title title text
	 * @param subtitle subtitle text
	 * @return blurb paragraphs
	 */
	private static String[] defaultBlurb(String[] blurb, int count, String title, String subtitle) {
		if (blurb != null && blurb.length > 0) {
			return blurb;
		}
		String countText = String.format(Locale.ROOT, "%,d", count);
		String fullTitle = subtitle == null || subtitle.isBlank() ? blankTo(title, "Chess Puzzle Collection")
				: blankTo(title, "Chess Puzzle Collection") + ": " + subtitle;
		return new String[] {
				fullTitle + " is a fast, high-volume chess training book designed for repetition and pattern recognition.",
				"Inside are " + countText
						+ " curated positions presented with clean diagrams, fast-reference solution tables, and full answer spreads that make review easy.",
				"Use it for blitz and rapid warm-ups, timed solving sessions, or steady daily practice aimed at faster recognition and cleaner calculation." };
	}

	/**
	 * Returns one non-blank string or a fallback.
	 *
	 * @param value source value
	 * @param fallback fallback value
	 * @return normalized string
	 */
	private static String blankTo(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
