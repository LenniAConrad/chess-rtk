package chess.book.study;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chess.book.model.Book;
import chess.pdf.Composition;
import chess.pdf.Options;
import chess.pdf.document.PageSize;
import utility.JsonFields;
import utility.Toml;

/**
 * Stores the richer manifest used by the puzzle-study pipeline.
 *
 * <p>
 * Unlike the lean puzzle-grid {@code chess.book.model.Book} manifest, this
 * model keeps per-entry commentary, hints, and figure lists by embedding the
 * repository's native {@link Composition} structure.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S1192")
public final class StudyBook {

	/**
	 * Default PDF page size.
	 */
	private static final PageSize DEFAULT_PAGE_SIZE = PageSize.A4;

	/**
	 * Default page margin in points.
	 */
	private static final double DEFAULT_MARGIN = 36.0;

	/**
	 * Default number of diagrams per row.
	 */
	private static final int DEFAULT_DIAGRAMS_PER_ROW = 2;

	/**
	 * Default raster size used for rendered diagrams.
	 */
	private static final int DEFAULT_BOARD_PIXELS = 900;

	/**
	 * Top-level title text.
	 */
	private String title = "Chess Puzzle Studies";

	/**
	 * Optional subtitle text.
	 */
	private String subtitle = "";

	/**
	 * Author credit.
	 */
	private String author = "Lennart A. Conrad";

	/**
	 * Publication time label.
	 */
	private String time = "";

	/**
	 * Publication location label.
	 */
	private String location = "";

	/**
	 * Optional printed page-count hint for cover generation.
	 */
	private int pages = 0;

	/**
	 * Back-cover blurb paragraphs.
	 */
	private String[] blurb = new String[0];

	/**
	 * Optional outbound links for the cover footer.
	 */
	private String[] link = new String[0];

	/**
	 * Selected PDF page size.
	 */
	private PageSize pageSize = DEFAULT_PAGE_SIZE;

	/**
	 * Page margin in PostScript points.
	 */
	private double margin = DEFAULT_MARGIN;

	/**
	 * Number of diagrams per row.
	 */
	private int diagramsPerRow = DEFAULT_DIAGRAMS_PER_ROW;

	/**
	 * Raster size used for rendered boards.
	 */
	private int boardPixels = DEFAULT_BOARD_PIXELS;

	/**
	 * Whether White is shown at the bottom of the diagrams.
	 */
	private boolean whiteSideDown = true;

	/**
	 * Whether FEN strings are printed under diagrams.
	 */
	private boolean showFen = true;

	/**
	 * Annotated composition entries.
	 */
	private Composition[] compositions = new Composition[0];

	/**
	 * Creates an empty puzzle-study manifest.
	 */
	public StudyBook() {
		// default
	}

	/**
	 * Loads an puzzle-study manifest from JSON or TOML.
	 *
	 * @param path input manifest path
	 * @return parsed manifest
	 * @throws IOException if the file cannot be read
	 */
	public static StudyBook load(Path path) throws IOException {
		String text = Files.readString(path, StandardCharsets.UTF_8);
		String trimmed = text.stripLeading();
		if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
			return fromJson(text);
		}
		return fromToml(text);
	}

	/**
	 * Parses an puzzle-study manifest from JSON.
	 *
	 * @param json JSON manifest text
	 * @return parsed manifest
	 */
	public static StudyBook fromJson(String json) {
		StudyBook book = new StudyBook();
		book.title = fallback(JsonFields.stringField(json, "title"), book.title);
		book.subtitle = fallback(JsonFields.stringField(json, "subtitle"), book.subtitle);
		book.author = fallback(JsonFields.stringField(json, "author"), book.author);
		book.time = fallback(JsonFields.stringField(json, "time"), book.time);
		book.location = fallback(JsonFields.stringField(json, "location"), book.location);
		book.pages = JsonFields.intField(json, "pages", book.pages);
		book.blurb = JsonFields.stringArrayField(json, "blurb");
		book.link = JsonFields.stringArrayField(json, "link");
		book.pageSize = parsePageSize(JsonFields.stringField(json, "pageSize"));
		book.margin = JsonFields.doubleField(json, "margin", book.margin);
		book.diagramsPerRow = JsonFields.intField(json, "diagramsPerRow", book.diagramsPerRow);
		book.boardPixels = JsonFields.intField(json, "boardPixels", book.boardPixels);
		book.whiteSideDown = JsonFields.booleanField(json, "whiteSideDown", book.whiteSideDown);
		book.showFen = JsonFields.booleanField(json, "showFen", book.showFen);
		book.compositions = parseJsonCompositions(JsonFields.arrayField(json, "compositions"));
		book.sanitizeLayout();
		return book;
	}

	/**
	 * Parses an puzzle-study manifest from TOML.
	 *
	 * @param source TOML manifest text
	 * @return parsed manifest
	 */
	public static StudyBook fromToml(String source) {
		try {
			Toml toml = Toml.load(new StringReader(source == null ? "" : source));
			StudyBook book = new StudyBook();
			book.title = fallback(toml.getString("title"), book.title);
			book.subtitle = fallback(toml.getString("subtitle"), book.subtitle);
			book.author = fallback(toml.getString("author"), book.author);
			book.time = fallback(toml.getString("time"), book.time);
			book.location = fallback(toml.getString("location"), book.location);
			book.pages = (int) toml.getLongOr("pages", book.pages);
			book.blurb = listToArray(toml.getStringList("blurb"));
			book.link = listToArray(toml.getStringList("link"));
			book.pageSize = parsePageSize(toml.getString("pageSize"));
			book.margin = toml.getDoubleOr("margin", book.margin);
			book.diagramsPerRow = (int) toml.getLongOr("diagramsPerRow", book.diagramsPerRow);
			book.boardPixels = (int) toml.getLongOr("boardPixels", book.boardPixels);
			book.whiteSideDown = parseBoolean(toml.getString("whiteSideDown"), book.whiteSideDown);
			book.showFen = parseBoolean(toml.getString("showFen"), book.showFen);
			book.compositions = parseTomlCompositions(toml.getTableArray("compositions"));
			book.sanitizeLayout();
			return book;
		} catch (IOException ex) {
			throw new IllegalArgumentException("failed to parse puzzle-study TOML", ex);
		}
	}

	/**
	 * Returns the title.
	 *
	 * @return title text
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Sets the title.
	 *
	 * @param title title text
	 * @return this manifest
	 */
	public StudyBook setTitle(String title) {
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
	 * @return this manifest
	 */
	public StudyBook setSubtitle(String subtitle) {
		this.subtitle = fallback(subtitle, "");
		return this;
	}

	/**
	 * Returns the author.
	 *
	 * @return author text
	 */
	public String getAuthor() {
		return author;
	}

	/**
	 * Sets the author.
	 *
	 * @param author author text
	 * @return this manifest
	 */
	public StudyBook setAuthor(String author) {
		this.author = fallback(author, "");
		return this;
	}

	/**
	 * Returns the publication time label.
	 *
	 * @return time label
	 */
	public String getTime() {
		return time;
	}

	/**
	 * Sets the publication time label.
	 *
	 * @param time time label
	 * @return this manifest
	 */
	public StudyBook setTime(String time) {
		this.time = fallback(time, "");
		return this;
	}

	/**
	 * Returns the publication location label.
	 *
	 * @return location label
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * Sets the publication location label.
	 *
	 * @param location location label
	 * @return this manifest
	 */
	public StudyBook setLocation(String location) {
		this.location = fallback(location, "");
		return this;
	}

	/**
	 * Returns the printed page-count hint.
	 *
	 * @return page-count hint
	 */
	public int getPages() {
		return pages;
	}

	/**
	 * Sets the printed page-count hint.
	 *
	 * @param pages page-count hint
	 * @return this manifest
	 */
	public StudyBook setPages(int pages) {
		this.pages = Math.max(0, pages);
		return this;
	}

	/**
	 * Returns the cover blurb.
	 *
	 * @return blurb lines
	 */
	public String[] getBlurb() {
		return safeArray(blurb);
	}

	/**
	 * Sets the cover blurb.
	 *
	 * @param blurb blurb lines
	 * @return this manifest
	 */
	public StudyBook setBlurb(String[] blurb) {
		this.blurb = safeArray(blurb);
		return this;
	}

	/**
	 * Returns the cover links.
	 *
	 * @return link lines
	 */
	public String[] getLink() {
		return safeArray(link);
	}

	/**
	 * Sets the cover links.
	 *
	 * @param link link lines
	 * @return this manifest
	 */
	public StudyBook setLink(String[] link) {
		this.link = safeArray(link);
		return this;
	}

	/**
	 * Returns the page size.
	 *
	 * @return page size
	 */
	public PageSize getPageSize() {
		return pageSize;
	}

	/**
	 * Sets the page size.
	 *
	 * @param pageSize page size
	 * @return this manifest
	 */
	public StudyBook setPageSize(PageSize pageSize) {
		this.pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
		return this;
	}

	/**
	 * Returns the page margin in points.
	 *
	 * @return page margin
	 */
	public double getMargin() {
		return margin;
	}

	/**
	 * Sets the page margin in points.
	 *
	 * @param margin page margin
	 * @return this manifest
	 */
	public StudyBook setMargin(double margin) {
		if (margin < 12.0) {
			throw new IllegalArgumentException("margin must be at least 12 points");
		}
		this.margin = margin;
		return this;
	}

	/**
	 * Returns the diagrams-per-row layout.
	 *
	 * @return diagrams per row
	 */
	public int getDiagramsPerRow() {
		return diagramsPerRow;
	}

	/**
	 * Sets the diagrams-per-row layout.
	 *
	 * @param diagramsPerRow diagrams per row
	 * @return this manifest
	 */
	public StudyBook setDiagramsPerRow(int diagramsPerRow) {
		if (diagramsPerRow <= 0) {
			throw new IllegalArgumentException("diagrams per row must be positive");
		}
		this.diagramsPerRow = diagramsPerRow;
		return this;
	}

	/**
	 * Returns the board raster size.
	 *
	 * @return board raster size
	 */
	public int getBoardPixels() {
		return boardPixels;
	}

	/**
	 * Sets the board raster size.
	 *
	 * @param boardPixels board raster size
	 * @return this manifest
	 */
	public StudyBook setBoardPixels(int boardPixels) {
		if (boardPixels < 256) {
			throw new IllegalArgumentException("boardPixels must be at least 256");
		}
		this.boardPixels = boardPixels;
		return this;
	}

	/**
	 * Returns whether White is shown at the bottom.
	 *
	 * @return true when White is shown at the bottom
	 */
	public boolean isWhiteSideDown() {
		return whiteSideDown;
	}

	/**
	 * Sets the board orientation.
	 *
	 * @param whiteSideDown true when White should be shown at the bottom
	 * @return this manifest
	 */
	public StudyBook setWhiteSideDown(boolean whiteSideDown) {
		this.whiteSideDown = whiteSideDown;
		return this;
	}

	/**
	 * Returns whether FEN strings are shown under diagrams.
	 *
	 * @return true when FEN strings are shown
	 */
	public boolean isShowFen() {
		return showFen;
	}

	/**
	 * Sets whether FEN strings are shown under diagrams.
	 *
	 * @param showFen true to show FEN strings
	 * @return this manifest
	 */
	public StudyBook setShowFen(boolean showFen) {
		this.showFen = showFen;
		return this;
	}

	/**
	 * Returns the annotated compositions.
	 *
	 * @return copied composition array
	 */
	public Composition[] getCompositions() {
		Composition[] copy = new Composition[compositions.length];
		for (int i = 0; i < compositions.length; i++) {
			copy[i] = copyComposition(compositions[i]);
		}
		return copy;
	}

	/**
	 * Sets the annotated compositions.
	 *
	 * @param compositions composition array
	 * @return this manifest
	 */
	public StudyBook setCompositions(Composition[] compositions) {
		if (compositions == null) {
			this.compositions = new Composition[0];
			return this;
		}
		this.compositions = new Composition[compositions.length];
		for (int i = 0; i < compositions.length; i++) {
			this.compositions[i] = copyComposition(compositions[i]);
		}
		return this;
	}

	/**
	 * Returns the rendered full title.
	 *
	 * @return title plus subtitle when present
	 */
	public String getFullTitle() {
		if (subtitle == null || subtitle.isBlank()) {
			return title;
		}
		return title + ": " + subtitle;
	}

	/**
	 * Converts this manifest into PDF rendering options.
	 *
	 * @return configured PDF options
	 */
	public Options toPdfOptions() {
		return new Options()
				.setPageSize(pageSize)
				.setMargin(margin)
				.setDiagramsPerRow(diagramsPerRow)
				.setBoardPixels(boardPixels)
				.setWhiteSideDown(whiteSideDown)
				.setShowFen(showFen)
				.setDocumentAuthor(author)
				.setDocumentSubject("Chess Puzzle Studies")
				.setDocumentCreator("chess.book.study.StudyBook")
				.setDocumentProducer("chess-rtk puzzle study pdf");
	}

	/**
	 * Converts this manifest into the lean cover metadata model.
	 *
	 * @return cover metadata book
	 */
	public Book toCoverBook() {
		return new Book()
				.setTitle(title)
				.setSubtitle(subtitle)
				.setAuthor(author)
				.setTime(time)
				.setLocation(location)
				.setPages(pages)
				.setBlurb(blurb)
				.setLink(link);
	}

	/**
	 * Parses composition entries from JSON.
	 *
	 * @param rawArray raw JSON array text
	 * @return composition array
	 */
	private static Composition[] parseJsonCompositions(String rawArray) {
		if (rawArray == null) {
			return new Composition[0];
		}
		List<String> objects = utility.Json.splitTopLevelObjects(rawArray);
		List<Composition> parsed = new ArrayList<>(objects.size());
		for (String object : objects) {
			parsed.add(parseJsonComposition(object));
		}
		return parsed.toArray(new Composition[0]);
	}

	/**
	 * Parses one composition from JSON.
	 *
	 * @param json raw composition JSON
	 * @return parsed composition
	 */
	private static Composition parseJsonComposition(String json) {
		return new Composition()
				.setTitle(JsonFields.stringField(json, "title"))
				.setDescription(JsonFields.stringField(json, "description"))
				.setAnalysis(JsonFields.stringField(json, "analysis"))
				.setComment(JsonFields.stringField(json, "comment"))
				.setHintLevel1(JsonFields.stringField(json, "hintLevel1"))
				.setHintLevel2(JsonFields.stringField(json, "hintLevel2"))
				.setHintLevel3(JsonFields.stringField(json, "hintLevel3"))
				.setHintLevel4(JsonFields.stringField(json, "hintLevel4"))
				.setId(JsonFields.stringField(json, "id"))
				.setTime(JsonFields.stringField(json, "time"))
				.setFigureMovesAlgebraic(List.of(JsonFields.stringArrayField(json, "figureMovesAlgebraic")))
				.setFigureMovesDetail(List.of(JsonFields.stringArrayField(json, "figureMovesDetail")))
				.setFigureFens(List.of(JsonFields.stringArrayField(json, "figureFens")))
				.setFigureArrows(List.of(JsonFields.stringArrayField(json, "figureArrows")));
	}

	/**
	 * Parses composition entries from TOML tables.
	 *
	 * @param tables parsed TOML table array
	 * @return composition array
	 */
	private static Composition[] parseTomlCompositions(List<Map<String, Object>> tables) {
		if (tables == null || tables.isEmpty()) {
			return new Composition[0];
		}
		List<Composition> parsed = new ArrayList<>(tables.size());
		for (Map<String, Object> table : tables) {
			parsed.add(new Composition()
					.setTitle(stringValue(table, "title"))
					.setDescription(stringValue(table, "description"))
					.setAnalysis(stringValue(table, "analysis"))
					.setComment(stringValue(table, "comment"))
					.setHintLevel1(stringValue(table, "hintLevel1"))
					.setHintLevel2(stringValue(table, "hintLevel2"))
					.setHintLevel3(stringValue(table, "hintLevel3"))
					.setHintLevel4(stringValue(table, "hintLevel4"))
					.setId(stringValue(table, "id"))
					.setTime(stringValue(table, "time"))
					.setFigureMovesAlgebraic(stringListValue(table, "figureMovesAlgebraic"))
					.setFigureMovesDetail(stringListValue(table, "figureMovesDetail"))
					.setFigureFens(stringListValue(table, "figureFens"))
					.setFigureArrows(stringListValue(table, "figureArrows")));
		}
		return parsed.toArray(new Composition[0]);
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
	 * Reads one string-list TOML table field.
	 *
	 * @param table parsed TOML table
	 * @param key field name
	 * @return string list, never null
	 */
	@SuppressWarnings("unchecked")
	private static List<String> stringListValue(Map<String, Object> table, String key) {
		if (table == null) {
			return List.of();
		}
		Object value = table.get(key);
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		List<String> out = new ArrayList<>(list.size());
		for (Object item : list) {
			out.add(item == null ? "" : item.toString());
		}
		return out;
	}

	/**
	 * Creates a deep copy of one composition.
	 *
	 * @param source source composition
	 * @return copied composition
	 */
	private static Composition copyComposition(Composition source) {
		if (source == null) {
			return new Composition();
		}
		return new Composition()
				.setTitle(source.getTitle())
				.setDescription(source.getDescription())
				.setAnalysis(source.getAnalysis())
				.setComment(source.getComment())
				.setHintLevel1(source.getHintLevel1())
				.setHintLevel2(source.getHintLevel2())
				.setHintLevel3(source.getHintLevel3())
				.setHintLevel4(source.getHintLevel4())
				.setId(source.getId())
				.setTime(source.getTime())
				.setFigureMovesAlgebraic(source.getFigureMovesAlgebraic())
				.setFigureMovesDetail(source.getFigureMovesDetail())
				.setFigureFens(source.getFigureFens())
				.setFigureArrows(source.getFigureArrows());
	}

	/**
	 * Parses one page-size token.
	 *
	 * @param token page-size token
	 * @return page size
	 */
	private static PageSize parsePageSize(String token) {
		if (token == null) {
			return DEFAULT_PAGE_SIZE;
		}
		String normalized = token.trim().toLowerCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "", "a4" -> PageSize.A4;
			case "a5" -> PageSize.A5;
			case "letter" -> PageSize.LETTER;
			default -> DEFAULT_PAGE_SIZE;
		};
	}

	/**
	 * Parses one boolean-like string.
	 *
	 * @param text source text
	 * @param fallback fallback value
	 * @return parsed boolean or fallback
	 */
	private static boolean parseBoolean(String text, boolean fallback) {
		if (text == null) {
			return fallback;
		}
		String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "true", "yes", "on", "1" -> true;
			case "false", "no", "off", "0" -> false;
			default -> fallback;
		};
	}

	/**
	 * Returns a stable token for one page size.
	 *
	 * @param pageSize page size
	 * @return page-size token
	 */
	public static String pageSizeToken(PageSize pageSize) {
		if (pageSize == null || pageSize == PageSize.A4) {
			return "a4";
		}
		if (pageSize == PageSize.A5) {
			return "a5";
		}
		if (pageSize == PageSize.LETTER) {
			return "letter";
		}
		return "a4";
	}

	/**
	 * Normalizes parsed layout values.
	 */
	private void sanitizeLayout() {
		pages = Math.max(0, pages);
		pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
		margin = Double.isFinite(margin) && margin >= 12.0 ? margin : DEFAULT_MARGIN;
		diagramsPerRow = Math.max(1, diagramsPerRow);
		boardPixels = Math.max(256, boardPixels);
		blurb = safeArray(blurb);
		link = safeArray(link);
		if (compositions == null) {
			compositions = new Composition[0];
		}
	}

	/**
	 * Returns one non-null string.
	 *
	 * @param value source value
	 * @param fallback fallback value
	 * @return normalized string
	 */
	private static String fallback(String value, String fallback) {
		return value == null ? fallback : value;
	}

	/**
	 * Sanitizes a string array input.
	 *
	 * @param value source array
	 * @return sanitized copy
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
	 * Converts a TOML string list into the manifest's array format.
	 *
	 * @param values parsed TOML strings
	 * @return sanitized array
	 */
	private static String[] listToArray(List<String> values) {
		if (values == null) {
			return new String[0];
		}
		return safeArray(values.toArray(new String[0]));
	}
}
