package chess.book.series.ilovechess;

import chess.book.model.Language;

/**
 * Mutable options used when building an I Love Chess-style book manifest from
 * puzzle records.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BuildOptions {

	/**
	 * Default series title.
	 */
	private String title = "I Love Chess";

	/**
	 * Optional subtitle override.
	 */
	private String subtitle = "";

	/**
	 * Optional author credit.
	 */
	private String author = "Lennart A. Conrad";

	/**
	 * Optional publication time.
	 */
	private String time = "";

	/**
	 * Optional publication location.
	 */
	private String location = "";

	/**
	 * Book language.
	 */
	private Language language = Language.English;

	/**
	 * Optional printed page-count hint.
	 */
	private int pages = 0;

	/**
	 * Puzzle-page cadence between solution tables.
	 */
	private int tableFrequency = 6;

	/**
	 * Puzzle-grid row count.
	 */
	private int puzzleRows = 5;

	/**
	 * Puzzle-grid column count.
	 */
	private int puzzleColumns = 4;

	/**
	 * Optional imprint lines.
	 */
	private String[] imprint = new String[0];

	/**
	 * Optional dedication lines.
	 */
	private String[] dedication = new String[0];

	/**
	 * Optional introduction paragraphs.
	 */
	private String[] introduction = new String[0];

	/**
	 * Optional how-to-read paragraphs.
	 */
	private String[] howToRead = new String[0];

	/**
	 * Optional back-cover blurb paragraphs.
	 */
	private String[] blurb = new String[0];

	/**
	 * Optional purchase links.
	 */
	private String[] link = new String[0];

	/**
	 * Optional afterword paragraphs.
	 */
	private String[] afterword = new String[0];

	/**
	 * Returns the title.
	 *
	 * @return title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Sets the title.
	 *
	 * @param title title
	 * @return this options object
	 */
	public BuildOptions setTitle(String title) {
		this.title = blankTo(title, "I Love Chess");
		return this;
	}

	/**
	 * Returns the subtitle.
	 *
	 * @return subtitle
	 */
	public String getSubtitle() {
		return subtitle;
	}

	/**
	 * Sets the subtitle.
	 *
	 * @param subtitle subtitle
	 * @return this options object
	 */
	public BuildOptions setSubtitle(String subtitle) {
		this.subtitle = blankTo(subtitle, "");
		return this;
	}

	/**
	 * Returns the author.
	 *
	 * @return author
	 */
	public String getAuthor() {
		return author;
	}

	/**
	 * Sets the author.
	 *
	 * @param author author
	 * @return this options object
	 */
	public BuildOptions setAuthor(String author) {
		this.author = blankTo(author, "");
		return this;
	}

	/**
	 * Returns the time string.
	 *
	 * @return time string
	 */
	public String getTime() {
		return time;
	}

	/**
	 * Sets the time string.
	 *
	 * @param time time string
	 * @return this options object
	 */
	public BuildOptions setTime(String time) {
		this.time = blankTo(time, "");
		return this;
	}

	/**
	 * Returns the location string.
	 *
	 * @return location string
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * Sets the location string.
	 *
	 * @param location location string
	 * @return this options object
	 */
	public BuildOptions setLocation(String location) {
		this.location = blankTo(location, "");
		return this;
	}

	/**
	 * Returns the book language.
	 *
	 * @return language
	 */
	public Language getLanguage() {
		return language;
	}

	/**
	 * Sets the book language.
	 *
	 * @param language language
	 * @return this options object
	 */
	public BuildOptions setLanguage(Language language) {
		this.language = language == null ? Language.English : language;
		return this;
	}

	/**
	 * Returns the printed page-count hint.
	 *
	 * @return pages
	 */
	public int getPages() {
		return pages;
	}

	/**
	 * Sets the printed page-count hint.
	 *
	 * @param pages pages
	 * @return this options object
	 */
	public BuildOptions setPages(int pages) {
		this.pages = Math.max(0, pages);
		return this;
	}

	/**
	 * Returns the solution-table cadence.
	 *
	 * @return table frequency
	 */
	public int getTableFrequency() {
		return tableFrequency;
	}

	/**
	 * Sets the solution-table cadence.
	 *
	 * @param tableFrequency table frequency
	 * @return this options object
	 */
	public BuildOptions setTableFrequency(int tableFrequency) {
		this.tableFrequency = Math.max(1, tableFrequency);
		return this;
	}

	/**
	 * Returns the puzzle-grid row count.
	 *
	 * @return puzzle rows
	 */
	public int getPuzzleRows() {
		return puzzleRows;
	}

	/**
	 * Sets the puzzle-grid row count.
	 *
	 * @param puzzleRows puzzle rows
	 * @return this options object
	 */
	public BuildOptions setPuzzleRows(int puzzleRows) {
		this.puzzleRows = Math.max(1, puzzleRows);
		return this;
	}

	/**
	 * Returns the puzzle-grid column count.
	 *
	 * @return puzzle columns
	 */
	public int getPuzzleColumns() {
		return puzzleColumns;
	}

	/**
	 * Sets the puzzle-grid column count.
	 *
	 * @param puzzleColumns puzzle columns
	 * @return this options object
	 */
	public BuildOptions setPuzzleColumns(int puzzleColumns) {
		this.puzzleColumns = Math.max(1, puzzleColumns);
		return this;
	}

	/**
	 * Returns imprint lines.
	 *
	 * @return imprint lines
	 */
	public String[] getImprint() {
		return imprint.clone();
	}

	/**
	 * Sets imprint lines.
	 *
	 * @param imprint imprint lines
	 * @return this options object
	 */
	public BuildOptions setImprint(String[] imprint) {
		this.imprint = copy(imprint);
		return this;
	}

	/**
	 * Returns dedication lines.
	 *
	 * @return dedication lines
	 */
	public String[] getDedication() {
		return dedication.clone();
	}

	/**
	 * Sets dedication lines.
	 *
	 * @param dedication dedication lines
	 * @return this options object
	 */
	public BuildOptions setDedication(String[] dedication) {
		this.dedication = copy(dedication);
		return this;
	}

	/**
	 * Returns introduction paragraphs.
	 *
	 * @return introduction paragraphs
	 */
	public String[] getIntroduction() {
		return introduction.clone();
	}

	/**
	 * Sets introduction paragraphs.
	 *
	 * @param introduction introduction paragraphs
	 * @return this options object
	 */
	public BuildOptions setIntroduction(String[] introduction) {
		this.introduction = copy(introduction);
		return this;
	}

	/**
	 * Returns how-to-read paragraphs.
	 *
	 * @return how-to-read paragraphs
	 */
	public String[] getHowToRead() {
		return howToRead.clone();
	}

	/**
	 * Sets how-to-read paragraphs.
	 *
	 * @param howToRead how-to-read paragraphs
	 * @return this options object
	 */
	public BuildOptions setHowToRead(String[] howToRead) {
		this.howToRead = copy(howToRead);
		return this;
	}

	/**
	 * Returns back-cover blurb paragraphs.
	 *
	 * @return blurb paragraphs
	 */
	public String[] getBlurb() {
		return blurb.clone();
	}

	/**
	 * Sets back-cover blurb paragraphs.
	 *
	 * @param blurb blurb paragraphs
	 * @return this options object
	 */
	public BuildOptions setBlurb(String[] blurb) {
		this.blurb = copy(blurb);
		return this;
	}

	/**
	 * Returns purchase links.
	 *
	 * @return purchase links
	 */
	public String[] getLink() {
		return link.clone();
	}

	/**
	 * Sets purchase links.
	 *
	 * @param link purchase links
	 * @return this options object
	 */
	public BuildOptions setLink(String[] link) {
		this.link = copy(link);
		return this;
	}

	/**
	 * Returns afterword paragraphs.
	 *
	 * @return afterword paragraphs
	 */
	public String[] getAfterword() {
		return afterword.clone();
	}

	/**
	 * Sets afterword paragraphs.
	 *
	 * @param afterword afterword paragraphs
	 * @return this options object
	 */
	public BuildOptions setAfterword(String[] afterword) {
		this.afterword = copy(afterword);
		return this;
	}

	/**
	 * Returns one non-null string or a fallback.
	 *
	 * @param value source value
	 * @param fallback fallback string
	 * @return normalized string
	 */
	private static String blankTo(String value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? fallback : trimmed;
	}

	/**
	 * Returns a defensive string-array copy.
	 *
	 * @param values source values
	 * @return copied array
	 */
	private static String[] copy(String[] values) {
		if (values == null) {
			return new String[0];
		}
		String[] copy = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			copy[i] = values[i] == null ? "" : values[i];
		}
		return copy;
	}
}
