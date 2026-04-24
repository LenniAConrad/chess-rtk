package chess.book.model;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chess.pdf.document.PageSize;
import utility.JsonFields;
import utility.Toml;

/**
 * Stores all metadata and puzzle content required to render a chess puzzle
 * book.
 *
 * <p>
 * The field set matches the current book manifest shape. The native PDF
 * generator accepts the same metadata from JSON and TOML sources.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Book {

	/**
	 * Number of PostScript points per centimeter.
	 */
	private static final double POINTS_PER_CM = 72.0 / 2.54;

	/**
	 * Default page width used by the book renderer.
	 */
	private static final double DEFAULT_PAPER_WIDTH_CM = 20.95;

	/**
	 * Default page height used by the book renderer.
	 */
	private static final double DEFAULT_PAPER_HEIGHT_CM = 27.94;

	/**
	 * Default binding-side margin used by the book renderer.
	 */
	private static final double DEFAULT_INNER_MARGIN_CM = 2.0;

	/**
	 * Default outside margin used by the book renderer.
	 */
	private static final double DEFAULT_OUTER_MARGIN_CM = 1.5;

	/**
	 * Default top margin used by the book renderer.
	 */
	private static final double DEFAULT_TOP_MARGIN_CM = 2.0;

	/**
	 * Default bottom margin used by the book renderer.
	 */
	private static final double DEFAULT_BOTTOM_MARGIN_CM = 2.0;

	/**
	 * Book title shown on the cover and in running headers.
	 */
	private String title = "TITLE";

	/**
	 * Book subtitle shown below the title on the cover.
	 */
	private String subtitle = "SUBTITLE";

	/**
	 * Author credit shown on the cover and in metadata.
	 */
	private String author = "AUTHOR";

	/**
	 * Human-readable publication time string.
	 */
	private String time = "";

	/**
	 * Human-readable publication location string.
	 */
	private String location = "";

	/**
	 * Book language for labels and default helper text.
	 */
	private Language language = Language.English;

	/**
	 * Expected printed page count used for cover spine calculations.
	 */
	private int pages = 0;

	/**
	 * Number of puzzle spreads between solution tables.
	 */
	private int tableFrequency = 6;

	/**
	 * Number of board rows on one puzzle page.
	 */
	private int puzzleRows = 5;

	/**
	 * Number of board columns on one puzzle page.
	 */
	private int puzzleColumns = 4;

	/**
	 * Physical page width in centimeters.
	 */
	private double paperWidthCm = DEFAULT_PAPER_WIDTH_CM;

	/**
	 * Physical page height in centimeters.
	 */
	private double paperHeightCm = DEFAULT_PAPER_HEIGHT_CM;

	/**
	 * Binding-side margin in centimeters.
	 */
	private double innerMarginCm = DEFAULT_INNER_MARGIN_CM;

	/**
	 * Outside margin in centimeters.
	 */
	private double outerMarginCm = DEFAULT_OUTER_MARGIN_CM;

	/**
	 * Top margin in centimeters.
	 */
	private double topMarginCm = DEFAULT_TOP_MARGIN_CM;

	/**
	 * Bottom margin in centimeters.
	 */
	private double bottomMarginCm = DEFAULT_BOTTOM_MARGIN_CM;

	/**
	 * Imprint lines shown near the cover footer.
	 */
	private String[] imprint = new String[0];

	/**
	 * Dedication lines shown on the dedication page.
	 */
	private String[] dedication = new String[0];

	/**
	 * Introduction paragraphs.
	 */
	private String[] introduction = new String[0];

	/**
	 * Optional custom how-to-read paragraphs.
	 */
	private String[] howToRead = new String[0];

	/**
	 * Cover blurb lines used by cover rendering.
	 */
	private String[] blurb = new String[0];

	/**
	 * Optional outbound purchase links.
	 */
	private String[] link = new String[0];

	/**
	 * Optional custom afterword paragraphs.
	 */
	private String[] afterword = new String[0];

	/**
	 * Puzzle elements rendered in the book.
	 */
	private Element[] elements = new Element[0];

	/**
	 * Creates an empty book model.
	 */
	public Book() {
		// default
	}

	/**
	 * Loads a book model from a JSON or TOML manifest.
	 *
	 * <p>
	 * This method accepts both manifest formats so the native PDF renderer can
	 * ingest either source without a conversion step.
	 * </p>
	 *
	 * @param path metadata path
	 * @return loaded book instance
	 * @throws IOException if the file cannot be read
	 */
	public static Book load(Path path) throws IOException {
		String text = Files.readString(path, StandardCharsets.UTF_8);
		String trimmed = text.stripLeading();
		if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
			return fromJson(text);
		}
		return fromToml(text);
	}

	/**
	 * Parses a book model from a JSON string.
	 *
	 * @param json raw JSON string
	 * @return parsed book instance
	 */
	public static Book fromJson(String json) {
		Book book = new Book();
		book.title = fallback(JsonFields.stringField(json, "title"), book.title);
		book.subtitle = fallback(JsonFields.stringField(json, "subtitle"), book.subtitle);
		book.author = fallback(JsonFields.stringField(json, "author"), book.author);
		book.time = fallback(JsonFields.stringField(json, "time"), book.time);
		book.location = fallback(JsonFields.stringField(json, "location"), book.location);
		book.language = Language.parse(JsonFields.stringField(json, "language"));
		book.pages = JsonFields.intField(json, "pages", book.pages);
		book.tableFrequency = JsonFields.intField(json, "tablefrequency", book.tableFrequency);
		book.puzzleRows = JsonFields.intField(json, "puzzlerows", book.puzzleRows);
		book.puzzleColumns = JsonFields.intField(json, "puzzlecolumns", book.puzzleColumns);
		book.paperWidthCm = JsonFields.doubleField(json, "paperwidth", book.paperWidthCm);
		book.paperHeightCm = JsonFields.doubleField(json, "paperheight", book.paperHeightCm);
		book.innerMarginCm = JsonFields.doubleField(json, "innermargin", book.innerMarginCm);
		book.outerMarginCm = JsonFields.doubleField(json, "outermargin", book.outerMarginCm);
		book.topMarginCm = JsonFields.doubleField(json, "topmargin", book.topMarginCm);
		book.bottomMarginCm = JsonFields.doubleField(json, "bottommargin", book.bottomMarginCm);
		book.imprint = JsonFields.stringArrayField(json, "imprint");
		book.dedication = JsonFields.stringArrayField(json, "dedication");
		book.introduction = JsonFields.stringArrayField(json, "introduction");
		book.howToRead = JsonFields.stringArrayField(json, "howToRead");
		book.blurb = JsonFields.stringArrayField(json, "blurb");
		book.link = JsonFields.stringArrayField(json, "link");
		book.afterword = JsonFields.stringArrayField(json, "afterword");
		book.elements = parseElements(json);
		book.sanitizeLayout();
		return book;
	}

	/**
	 * Parses a book model from a TOML string.
	 *
	 * <p>
	 * Supported TOML contains scalar top-level book fields, string-array front
	 * matter, and repeated {@code [[elements]]} tables with {@code position} and
	 * {@code moves} entries.
	 * </p>
	 *
	 * @param source raw TOML source
	 * @return parsed book instance
	 */
	public static Book fromToml(String source) {
		try {
			Toml toml = Toml.load(new StringReader(source == null ? "" : source));
			Book book = new Book();
			book.title = fallback(toml.getString("title"), book.title);
			book.subtitle = fallback(toml.getString("subtitle"), book.subtitle);
			book.author = fallback(toml.getString("author"), book.author);
			book.time = fallback(toml.getString("time"), book.time);
			book.location = fallback(toml.getString("location"), book.location);
			book.language = Language.parse(toml.getString("language"));
			book.pages = (int) toml.getLongOr("pages", book.pages);
			book.tableFrequency = (int) toml.getLongOr("tablefrequency", book.tableFrequency);
			book.puzzleRows = (int) toml.getLongOr("puzzlerows", book.puzzleRows);
			book.puzzleColumns = (int) toml.getLongOr("puzzlecolumns", book.puzzleColumns);
			book.paperWidthCm = toml.getDoubleOr("paperwidth", book.paperWidthCm);
			book.paperHeightCm = toml.getDoubleOr("paperheight", book.paperHeightCm);
			book.innerMarginCm = toml.getDoubleOr("innermargin", book.innerMarginCm);
			book.outerMarginCm = toml.getDoubleOr("outermargin", book.outerMarginCm);
			book.topMarginCm = toml.getDoubleOr("topmargin", book.topMarginCm);
			book.bottomMarginCm = toml.getDoubleOr("bottommargin", book.bottomMarginCm);
			book.imprint = listToArray(toml.getStringList("imprint"));
			book.dedication = listToArray(toml.getStringList("dedication"));
			book.introduction = listToArray(toml.getStringList("introduction"));
			book.howToRead = listToArray(toml.getStringList("howToRead"));
			book.blurb = listToArray(toml.getStringList("blurb"));
			book.link = listToArray(toml.getStringList("link"));
			book.afterword = listToArray(toml.getStringList("afterword"));
			book.elements = parseTomlElements(toml.getTableArray("elements"));
			book.sanitizeLayout();
			return book;
		} catch (IOException ex) {
			throw new IllegalArgumentException("failed to parse book TOML", ex);
		}
	}

	/**
	 * Returns the book title.
	 *
	 * @return title text
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Sets the book title.
	 *
	 * @param title title text
	 * @return this book for chaining
	 */
	public Book setTitle(String title) {
		this.title = fallback(title, "");
		return this;
	}

	/**
	 * Returns the subtitle.
	 *
	 * @return subtitle text
	 */
	public String getSubtitle() {
		return subtitle;
	}

	/**
	 * Sets the subtitle.
	 *
	 * @param subtitle subtitle text
	 * @return this book for chaining
	 */
	public Book setSubtitle(String subtitle) {
		this.subtitle = fallback(subtitle, "");
		return this;
	}

	/**
	 * Returns the author credit.
	 *
	 * @return author text
	 */
	public String getAuthor() {
		return author;
	}

	/**
	 * Sets the author credit.
	 *
	 * @param author author text
	 * @return this book for chaining
	 */
	public Book setAuthor(String author) {
		this.author = fallback(author, "");
		return this;
	}

	/**
	 * Returns the time string.
	 *
	 * @return time text
	 */
	public String getTime() {
		return time;
	}

	/**
	 * Sets the time string.
	 *
	 * @param time time text
	 * @return this book for chaining
	 */
	public Book setTime(String time) {
		this.time = fallback(time, "");
		return this;
	}

	/**
	 * Returns the location string.
	 *
	 * @return location text
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * Sets the location string.
	 *
	 * @param location location text
	 * @return this book for chaining
	 */
	public Book setLocation(String location) {
		this.location = fallback(location, "");
		return this;
	}

	/**
	 * Returns the language.
	 *
	 * @return book language
	 */
	public Language getLanguage() {
		return language;
	}

	/**
	 * Sets the language.
	 *
	 * @param language book language
	 * @return this book for chaining
	 */
	public Book setLanguage(Language language) {
		this.language = language == null ? Language.English : language;
		return this;
	}

	/**
	 * Returns the configured page-count target.
	 *
	 * @return target page count
	 */
	public int getPages() {
		return pages;
	}

	/**
	 * Sets the configured page-count target.
	 *
	 * @param pages target page count
	 * @return this book for chaining
	 */
	public Book setPages(int pages) {
		this.pages = Math.max(0, pages);
		return this;
	}

	/**
	 * Returns the section table frequency.
	 *
	 * @return puzzle-page cadence for solution tables
	 */
	public int getTableFrequency() {
		return tableFrequency;
	}

	/**
	 * Sets the section table frequency.
	 *
	 * @param tableFrequency puzzle-page cadence for solution tables
	 * @return this book for chaining
	 */
	public Book setTableFrequency(int tableFrequency) {
		this.tableFrequency = Math.max(1, tableFrequency);
		return this;
	}

	/**
	 * Returns the number of board rows per puzzle page.
	 *
	 * @return board rows
	 */
	public int getPuzzleRows() {
		return puzzleRows;
	}

	/**
	 * Sets the number of board rows per puzzle page.
	 *
	 * @param puzzleRows board rows
	 * @return this book for chaining
	 */
	public Book setPuzzleRows(int puzzleRows) {
		this.puzzleRows = Math.max(1, puzzleRows);
		return this;
	}

	/**
	 * Returns the number of board columns per puzzle page.
	 *
	 * @return board columns
	 */
	public int getPuzzleColumns() {
		return puzzleColumns;
	}

	/**
	 * Sets the number of board columns per puzzle page.
	 *
	 * @param puzzleColumns board columns
	 * @return this book for chaining
	 */
	public Book setPuzzleColumns(int puzzleColumns) {
		this.puzzleColumns = Math.max(1, puzzleColumns);
		return this;
	}

	/**
	 * Returns the paper width in centimeters.
	 *
	 * @return width in centimeters
	 */
	public double getPaperWidthCm() {
		return paperWidthCm;
	}

	/**
	 * Sets the paper width in centimeters.
	 *
	 * @param paperWidthCm width in centimeters
	 * @return this book for chaining
	 */
	public Book setPaperWidthCm(double paperWidthCm) {
		this.paperWidthCm = positiveOrDefault(paperWidthCm, DEFAULT_PAPER_WIDTH_CM);
		return this;
	}

	/**
	 * Returns the paper height in centimeters.
	 *
	 * @return height in centimeters
	 */
	public double getPaperHeightCm() {
		return paperHeightCm;
	}

	/**
	 * Sets the paper height in centimeters.
	 *
	 * @param paperHeightCm height in centimeters
	 * @return this book for chaining
	 */
	public Book setPaperHeightCm(double paperHeightCm) {
		this.paperHeightCm = positiveOrDefault(paperHeightCm, DEFAULT_PAPER_HEIGHT_CM);
		return this;
	}

	/**
	 * Returns the inner margin in centimeters.
	 *
	 * @return inner margin
	 */
	public double getInnerMarginCm() {
		return innerMarginCm;
	}

	/**
	 * Sets the inner margin in centimeters.
	 *
	 * @param innerMarginCm inner margin
	 * @return this book for chaining
	 */
	public Book setInnerMarginCm(double innerMarginCm) {
		this.innerMarginCm = nonNegativeOrDefault(innerMarginCm, DEFAULT_INNER_MARGIN_CM);
		return this;
	}

	/**
	 * Returns the outer margin in centimeters.
	 *
	 * @return outer margin
	 */
	public double getOuterMarginCm() {
		return outerMarginCm;
	}

	/**
	 * Sets the outer margin in centimeters.
	 *
	 * @param outerMarginCm outer margin
	 * @return this book for chaining
	 */
	public Book setOuterMarginCm(double outerMarginCm) {
		this.outerMarginCm = nonNegativeOrDefault(outerMarginCm, DEFAULT_OUTER_MARGIN_CM);
		return this;
	}

	/**
	 * Returns the top margin in centimeters.
	 *
	 * @return top margin
	 */
	public double getTopMarginCm() {
		return topMarginCm;
	}

	/**
	 * Sets the top margin in centimeters.
	 *
	 * @param topMarginCm top margin
	 * @return this book for chaining
	 */
	public Book setTopMarginCm(double topMarginCm) {
		this.topMarginCm = nonNegativeOrDefault(topMarginCm, DEFAULT_TOP_MARGIN_CM);
		return this;
	}

	/**
	 * Returns the bottom margin in centimeters.
	 *
	 * @return bottom margin
	 */
	public double getBottomMarginCm() {
		return bottomMarginCm;
	}

	/**
	 * Sets the bottom margin in centimeters.
	 *
	 * @param bottomMarginCm bottom margin
	 * @return this book for chaining
	 */
	public Book setBottomMarginCm(double bottomMarginCm) {
		this.bottomMarginCm = nonNegativeOrDefault(bottomMarginCm, DEFAULT_BOTTOM_MARGIN_CM);
		return this;
	}

	/**
	 * Returns the imprint lines.
	 *
	 * @return imprint lines, never null
	 */
	public String[] getImprint() {
		return imprint.clone();
	}

	/**
	 * Sets the imprint lines.
	 *
	 * @param imprint imprint lines
	 * @return this book for chaining
	 */
	public Book setImprint(String[] imprint) {
		this.imprint = safeArray(imprint);
		return this;
	}

	/**
	 * Returns the dedication lines.
	 *
	 * @return dedication lines, never null
	 */
	public String[] getDedication() {
		return dedication.clone();
	}

	/**
	 * Sets the dedication lines.
	 *
	 * @param dedication dedication lines
	 * @return this book for chaining
	 */
	public Book setDedication(String[] dedication) {
		this.dedication = safeArray(dedication);
		return this;
	}

	/**
	 * Returns the introduction paragraphs.
	 *
	 * @return introduction paragraphs, never null
	 */
	public String[] getIntroduction() {
		return introduction.clone();
	}

	/**
	 * Sets the introduction paragraphs.
	 *
	 * @param introduction introduction paragraphs
	 * @return this book for chaining
	 */
	public Book setIntroduction(String[] introduction) {
		this.introduction = safeArray(introduction);
		return this;
	}

	/**
	 * Returns the optional how-to-read paragraphs.
	 *
	 * @return how-to-read paragraphs, never null
	 */
	public String[] getHowToRead() {
		return howToRead.clone();
	}

	/**
	 * Sets the optional how-to-read paragraphs.
	 *
	 * @param howToRead how-to-read paragraphs
	 * @return this book for chaining
	 */
	public Book setHowToRead(String[] howToRead) {
		this.howToRead = safeArray(howToRead);
		return this;
	}

	/**
	 * Returns the blurb lines.
	 *
	 * @return blurb lines, never null
	 */
	public String[] getBlurb() {
		return blurb.clone();
	}

	/**
	 * Sets the blurb lines.
	 *
	 * @param blurb blurb lines
	 * @return this book for chaining
	 */
	public Book setBlurb(String[] blurb) {
		this.blurb = safeArray(blurb);
		return this;
	}

	/**
	 * Returns the purchase links.
	 *
	 * @return purchase links, never null
	 */
	public String[] getLink() {
		return link.clone();
	}

	/**
	 * Sets the purchase links.
	 *
	 * @param link purchase links
	 * @return this book for chaining
	 */
	public Book setLink(String[] link) {
		this.link = safeArray(link);
		return this;
	}

	/**
	 * Returns the afterword paragraphs.
	 *
	 * @return afterword paragraphs, never null
	 */
	public String[] getAfterword() {
		return afterword.clone();
	}

	/**
	 * Sets the afterword paragraphs.
	 *
	 * @param afterword afterword paragraphs
	 * @return this book for chaining
	 */
	public Book setAfterword(String[] afterword) {
		this.afterword = safeArray(afterword);
		return this;
	}

	/**
	 * Returns the puzzle elements.
	 *
	 * @return element array copy, never null
	 */
	public Element[] getElements() {
		Element[] copy = new Element[elements.length];
		for (int i = 0; i < elements.length; i++) {
			copy[i] = elements[i] == null ? new Element() : elements[i].copy();
		}
		return copy;
	}

	/**
	 * Sets the puzzle elements.
	 *
	 * @param elements puzzle elements
	 * @return this book for chaining
	 */
	public Book setElements(Element[] elements) {
		if (elements == null) {
			this.elements = new Element[0];
			return this;
		}
		this.elements = new Element[elements.length];
		for (int i = 0; i < elements.length; i++) {
			this.elements[i] = elements[i] == null ? new Element() : elements[i].copy();
		}
		return this;
	}

	/**
	 * Returns the rendered full title.
	 *
	 * @return title plus subtitle when available
	 */
	public String getFullTitle() {
		if (subtitle == null || subtitle.isBlank()) {
			return title;
		}
		return title + ": " + subtitle;
	}

	/**
	 * Creates a custom PDF page size from the centimeter dimensions.
	 *
	 * @return custom PDF page size
	 */
	public PageSize toPageSize() {
		return new PageSize(getFullTitle(), cmToPoints(paperWidthCm), cmToPoints(paperHeightCm));
	}

	/**
	 * Converts centimeters to PostScript points.
	 *
	 * @param centimeters source length
	 * @return converted point value
	 */
	public static double cmToPoints(double centimeters) {
		return centimeters * POINTS_PER_CM;
	}

	/**
	 * Extracts and parses the element array from the JSON object.
	 *
	 * @param json raw book JSON
	 * @return parsed element array
	 */
	private static Element[] parseElements(String json) {
		String rawArray = JsonFields.arrayField(json, "elements");
		if (rawArray == null) {
			return new Element[0];
		}
		List<String> objects = utility.Json.splitTopLevelObjects(rawArray);
		List<Element> elements = new ArrayList<>(objects.size());
		for (String object : objects) {
			elements.add(Element.fromJson(object));
		}
		return elements.toArray(new Element[0]);
	}

	/**
	 * Converts TOML {@code [[elements]]} entries into book elements.
	 *
	 * @param tables parsed TOML table-array entries
	 * @return parsed element array
	 */
	private static Element[] parseTomlElements(List<Map<String, Object>> tables) {
		if (tables == null || tables.isEmpty()) {
			return new Element[0];
		}
		List<Element> elements = new ArrayList<>(tables.size());
		for (Map<String, Object> table : tables) {
			elements.add(new Element(
					stringValue(table, "position"),
					stringValue(table, "moves")));
		}
		return elements.toArray(new Element[0]);
	}

	/**
	 * Reads one string-valued TOML table field.
	 *
	 * @param table parsed TOML table
	 * @param key field name
	 * @return string value, or an empty string when absent
	 */
	private static String stringValue(Map<String, Object> table, String key) {
		if (table == null) {
			return "";
		}
		Object value = table.get(key);
		return value == null ? "" : value.toString();
	}

	/**
	 * Returns one non-null string.
	 *
	 * @param value source value
	 * @param fallback fallback value
	 * @return non-null result
	 */
	private static String fallback(String value, String fallback) {
		return value == null ? fallback : value;
	}

	/**
	 * Sanitizes a string array input.
	 *
	 * @param value source array
	 * @return sanitized copy, never null
	 */
	private static String[] safeArray(String[] value) {
		if (value == null) {
			return new String[0];
		}
		String[] copy = new String[value.length];
		for (int i = 0; i < value.length; i++) {
			copy[i] = value[i] == null ? "" : value[i];
		}
		return copy;
	}

	/**
	 * Converts a TOML string list into the renderer's defensive array format.
	 *
	 * @param values parsed TOML string list
	 * @return sanitized array, never null
	 */
	private static String[] listToArray(List<String> values) {
		if (values == null) {
			return new String[0];
		}
		return safeArray(values.toArray(new String[0]));
	}

	/**
	 * Normalizes parsed layout values before rendering.
	 */
	private void sanitizeLayout() {
		pages = Math.max(0, pages);
		tableFrequency = Math.max(1, tableFrequency);
		puzzleRows = Math.max(1, puzzleRows);
		puzzleColumns = Math.max(1, puzzleColumns);
		paperWidthCm = positiveOrDefault(paperWidthCm, DEFAULT_PAPER_WIDTH_CM);
		paperHeightCm = positiveOrDefault(paperHeightCm, DEFAULT_PAPER_HEIGHT_CM);
		innerMarginCm = nonNegativeOrDefault(innerMarginCm, DEFAULT_INNER_MARGIN_CM);
		outerMarginCm = nonNegativeOrDefault(outerMarginCm, DEFAULT_OUTER_MARGIN_CM);
		topMarginCm = nonNegativeOrDefault(topMarginCm, DEFAULT_TOP_MARGIN_CM);
		bottomMarginCm = nonNegativeOrDefault(bottomMarginCm, DEFAULT_BOTTOM_MARGIN_CM);

		if (innerMarginCm + outerMarginCm >= paperWidthCm) {
			innerMarginCm = Math.min(DEFAULT_INNER_MARGIN_CM, paperWidthCm * 0.25);
			outerMarginCm = Math.min(DEFAULT_OUTER_MARGIN_CM, paperWidthCm * 0.25);
		}
		if (topMarginCm + bottomMarginCm >= paperHeightCm) {
			topMarginCm = Math.min(DEFAULT_TOP_MARGIN_CM, paperHeightCm * 0.25);
			bottomMarginCm = Math.min(DEFAULT_BOTTOM_MARGIN_CM, paperHeightCm * 0.25);
		}
	}

	/**
	 * Returns a finite positive value or a fallback.
	 *
	 * @param value source value
	 * @param fallback fallback value
	 * @return sanitized value
	 */
	private static double positiveOrDefault(double value, double fallback) {
		return Double.isFinite(value) && value > 0.0 ? value : fallback;
	}

	/**
	 * Returns a finite non-negative value or a fallback.
	 *
	 * @param value source value
	 * @param fallback fallback value
	 * @return sanitized value
	 */
	private static double nonNegativeOrDefault(double value, double fallback) {
		return Double.isFinite(value) && value >= 0.0 ? value : fallback;
	}
}
