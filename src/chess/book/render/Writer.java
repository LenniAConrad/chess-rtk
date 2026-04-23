package chess.book.render;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chess.book.model.Book;
import chess.book.model.Element;
import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.images.assets.shape.SvgShapes;
import chess.images.render.Render;
import chess.pdf.document.Canvas;
import chess.pdf.document.Document;
import chess.pdf.document.Font;
import chess.pdf.document.Page;
import chess.pdf.document.PageSize;

/**
 * Renders chess-book manifests directly to native PDF documents.
 *
 * <p>
 * The layout uses mirrored inner and outer margins, front matter, a generated
 * table of contents, puzzle spreads, recurring solution tables, outer page
 * numbers, and running headers on body pages.
 * </p>
 *
 * <p>
 * The implementation stays dependency-free and uses only the local PDF package
 * plus the existing chess renderer for SVG board generation.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Writer {

	/**
	 * Producer metadata written into the generated PDF.
	 */
	private static final String PRODUCER = "chess-rtk native book pdf";

	/**
	 * Creator metadata written into the generated PDF.
	 */
	private static final String CREATOR = "chess-rtk native book renderer";

	/**
	 * Subject metadata written into normal book PDFs.
	 */
	private static final String SUBJECT = "Chess Book";

	/**
	 * Restriction text embedded in free-edition metadata and page overlays.
	 */
	private static final String FREE_WATERMARK_RESTRICTION =
			"Free electronic copy; printing, resale, and unauthorized redistribution not allowed";

	/**
	 * Subject metadata written into watermarked free-edition PDFs.
	 */
	private static final String FREE_WATERMARK_SUBJECT =
			"Chess Book - " + FREE_WATERMARK_RESTRICTION;

	/**
	 * Main diagonal text for watermarked free-edition PDFs.
	 */
	private static final String FREE_WATERMARK_MAIN = "FREE ELECTRONIC COPY";

	/**
	 * Short repeated text used as a dense free-edition anti-print pattern.
	 */
	private static final String FREE_WATERMARK_REPEAT =
			"FREE COPY - NO PRINTING - NO RESALE";

	/**
	 * Board pixel size used when asking the SVG renderer for source markup.
	 */
	private static final int BOARD_PIXELS = 900;

	/**
	 * Cover-title font size.
	 */
	private static final double COVER_TITLE_SIZE = 26.0;

	/**
	 * Cover-subtitle font size.
	 */
	private static final double COVER_SUBTITLE_SIZE = 14.0;

	/**
	 * Cover footer font size.
	 */
	private static final double COVER_FOOTER_SIZE = 10.0;

	/**
	 * Main section-heading font size.
	 */
	private static final double SECTION_TITLE_SIZE = 18.0;

	/**
	 * Top offset for normal section headings.
	 */
	private static final double SECTION_HEADING_TOP_OFFSET = 6.4;

	/**
	 * Gap between the section number and the section title.
	 */
	private static final double SECTION_TITLE_GAP = 3.5;

	/**
	 * Table-of-contents title font size.
	 */
	private static final double TOC_TITLE_SIZE = 18.0;

	/**
	 * Table-of-contents row font size.
	 */
	private static final double TOC_ENTRY_SIZE = 10.0;

	/**
	 * Body-copy font size.
	 */
	private static final double BODY_SIZE = 10.0;

	/**
	 * Body-copy leading.
	 */
	private static final double BODY_LEADING = 12.0;

	/**
	 * Running-header font size.
	 */
	private static final double HEADER_SIZE = 10.0;

	/**
	 * Distance from the body top edge to the running-header baseline.
	 */
	private static final double HEADER_BODY_TOP_OFFSET = 35.85;

	/**
	 * Footer font size.
	 */
	private static final double FOOTER_SIZE = 10.0;

	/**
	 * Puzzle-label font size.
	 */
	private static final double PUZZLE_LABEL_SIZE = 9.0;

	/**
	 * Solution-label font size.
	 */
	private static final double SOLUTION_LABEL_SIZE = 8.5;

	/**
	 * Solution-footnote font size.
	 */
	private static final double SOLUTION_FOOTNOTE_SIZE = 8.0;

	/**
	 * Clearance between the last solution-grid label and the solution footnote.
	 */
	private static final double SOLUTION_FOOTNOTE_CLEARANCE = 8.0;

	/**
	 * Table font size.
	 */
	private static final double TABLE_SIZE = 9.0;

	/**
	 * Table header font size.
	 */
	private static final double TABLE_HEADER_SIZE = 10.0;

	/**
	 * Table body line advance.
	 */
	private static final double TABLE_LEADING = 12.5;

	/**
	 * Generic heading and body color.
	 */
	private static final Color INK = new Color(32, 32, 32);

	/**
	 * Muted secondary-text color.
	 */
	private static final Color MUTED = new Color(82, 82, 82);

	/**
	 * Table-rule gray used by solution tables.
	 */
	private static final Color TABLE_GRAY = new Color(128, 128, 128);

	/**
	 * Subtle cover separator color.
	 */
	private static final Color SEPARATOR = new Color(110, 110, 110);

	/**
	 * Decorative footer line color for the closing page.
	 */
	private static final Color CLOSING_LINE = new Color(150, 150, 150);

	/**
	 * Large watermark color.
	 */
	private static final Color FREE_WATERMARK_LARGE = new Color(68, 68, 68, 44);

	/**
	 * Repeated watermark color.
	 */
	private static final Color FREE_WATERMARK_SMALL = new Color(70, 70, 70, 22);

	/**
	 * Edge notice color for watermarked free-edition PDFs.
	 */
	private static final Color FREE_WATERMARK_NOTICE = new Color(34, 34, 34, 118);

	/**
	 * Page-identifier watermark color.
	 */
	private static final Color FREE_WATERMARK_ID = new Color(34, 34, 34, 90);

	/**
	 * Gap between grid cells.
	 */
	private static final double GRID_GAP = 5.0;

	/**
	 * Board width fraction used by puzzle and solution figures.
	 */
	private static final double PUZZLE_BOARD_FRACTION = 0.22;

	/**
	 * Board width fraction used by special-move figures.
	 */
	private static final double SPECIAL_EXAMPLE_BOARD_FRACTION = 0.31;

	/**
	 * Minimum visual gutter fraction used between special-move examples.
	 */
	private static final double SPECIAL_EXAMPLE_GUTTER_FRACTION = 0.022;

	/**
	 * Horizontal marker indent used by itemized lists.
	 */
	private static final double LIST_MARKER_INDENT = 18.0;

	/**
	 * Body-text offset used for list items after the marker.
	 */
	private static final double LIST_BODY_INDENT = 34.0;

	/**
	 * Gap between a list marker and its body text.
	 */
	private static final double LIST_MARKER_GAP = 5.0;

	/**
	 * Vertical gap between consecutive list items.
	 */
	private static final double LIST_ITEM_GAP = 4.0;

	/**
	 * Vertical gap between labels and boards inside a grid cell.
	 */
	private static final double GRID_LABEL_GAP = 3.0;

	/**
	 * Padding applied inside each table row cell.
	 */
	private static final double TABLE_CELL_PADDING = 2.0;

	/**
	 * White-king figurine placeholder used before native SVG notation rendering.
	 */
	private static final char NOTATION_KING = '\u2654';

	/**
	 * White-queen figurine placeholder used before native SVG notation rendering.
	 */
	private static final char NOTATION_QUEEN = '\u2655';

	/**
	 * White-rook figurine placeholder used before native SVG notation rendering.
	 */
	private static final char NOTATION_ROOK = '\u2656';

	/**
	 * White-bishop figurine placeholder used before native SVG notation rendering.
	 */
	private static final char NOTATION_BISHOP = '\u2657';

	/**
	 * White-knight figurine placeholder used before native SVG notation rendering.
	 */
	private static final char NOTATION_KNIGHT = '\u2658';

	/**
	 * White-pawn figurine placeholder used before native SVG notation rendering.
	 */
	private static final char NOTATION_PAWN = '\u2659';

	/**
	 * Scale applied to inline piece SVG boxes relative to the surrounding text.
	 */
	private static final double NOTATION_PIECE_SIZE_SCALE = 1.16;

	/**
	 * Left side bearing reserved before one inline piece SVG.
	 */
	private static final double NOTATION_PIECE_LEFT_PADDING_SCALE = 0.04;

	/**
	 * Right side bearing reserved after one inline piece SVG.
	 */
	private static final double NOTATION_PIECE_RIGHT_PADDING_SCALE = 0.19;

	/**
	 * Upward shift applied to inline piece SVG boxes relative to the text top.
	 */
	private static final double NOTATION_PIECE_TOP_SHIFT_SCALE = 0.03;

	/**
	 * Pattern for numbered list items in user-supplied prose.
	 */
	private static final Pattern NUMBERED_LIST_ITEM = Pattern.compile("(\\d+[.)])\\s+(.+)");

	/**
	 * Internal marker used for unordered list items.
	 */
	private static final String UNORDERED_LIST_MARKER = "\u2022";

	/**
	 * Diameter of the filled vector circle used for unordered list markers.
	 */
	private static final double LIST_BULLET_DIAMETER = 3.4;

	/**
	 * Margin reserved above body-page content after a section heading.
	 */
	private static final double SECTION_AFTER_GAP = 26.0;

	/**
	 * Additional vertical gap between paragraphs.
	 */
	private static final double PARAGRAPH_GAP = 12.0;

	/**
	 * Font size used for how-to-read subheadings.
	 */
	private static final double SUBHEADING_SIZE = BODY_SIZE;

	/**
	 * Font size used for example-board captions.
	 */
	private static final double CAPTION_SIZE = 8.0;

	/**
	 * Gap between example images and their italic captions.
	 */
	private static final double CAPTION_GAP = 10.0;

	/**
	 * Line advance used for wrapped example captions.
	 */
	private static final double CAPTION_LEADING = 9.0;

	/**
	 * Maximum number of caption lines reserved inside figure blocks.
	 */
	private static final int CAPTION_MAX_LINES = 3;

	/**
	 * Gap after figure captions before normal prose resumes.
	 */
	private static final double CAPTION_TRAILING_GAP = 16.0;

	/**
	 * Relative drop-shadow size used for standalone example boards.
	 */
	private static final double EXAMPLE_BOARD_SHADOW_FRACTION = 0.05;

	/**
	 * Native width used when composing page-preview SVGs.
	 */
	private static final int PAGE_PREVIEW_WIDTH = 2000;

	/**
	 * Relative drop-shadow size used for page-preview SVGs.
	 */
	private static final double PAGE_PREVIEW_SHADOW_FRACTION = 0.05;

	/**
	 * Base number of vector speckles drawn on each A4-equivalent watermarked page.
	 */
	private static final int FREE_WATERMARK_DOTS_A4 = 260;

	/**
	 * Base number of short vector scratches drawn on each A4-equivalent watermarked page.
	 */
	private static final int FREE_WATERMARK_SCRATCHES_A4 = 64;

	/**
	 * Angle used for large free-edition watermark text.
	 */
	private static final double FREE_WATERMARK_ANGLE = -34.0;

	/**
	 * Minimum distance between repeated free-edition labels.
	 */
	private static final double FREE_WATERMARK_REPEAT_STEP = 38.0;

	/**
	 * Deterministic salt used by the free-edition noise generator.
	 */
	private static final long FREE_WATERMARK_SEED = 0x4352544b46524545L;

	/**
	 * Relative page-preview border size.
	 */
	private static final double PAGE_PREVIEW_BORDER_FRACTION = 0.002;

	/**
	 * Page-preview border color.
	 */
	private static final Color PAGE_PREVIEW_BORDER_COLOR = new Color(100, 100, 100);

	/**
	 * Extra SVG view-box space reserved around vector drop shadows.
	 */
	private static final double PAGE_SHADOW_RESERVE_SCALE = 2.0;

	/**
	 * Maximum opacity at the contact edge of a vector shadow.
	 */
	private static final double PAGE_SHADOW_CONTACT_OPACITY = 0.42;

	/**
	 * Example FEN used to demonstrate en-passant arrows.
	 */
	private static final String EXAMPLE_EN_PASSANT_FEN = "8/2k5/8/5Pp1/8/8/4K3/8 w - g6 0 1";

	/**
	 * Example FEN used to demonstrate castling arrows.
	 */
	private static final String EXAMPLE_CASTLING_FEN = "r3k3/8/8/8/8/8/8/4K2R w Kq - 0 1";

	/**
	 * Example FEN used to demonstrate combined en-passant and castling arrows.
	 */
	private static final String EXAMPLE_SPECIAL_RIGHTS_FEN = "1k2r3/n6p/8/3pP3/8/6B1/4P3/R3K3 w Q d6 0 1";

	/**
	 * Normal embedded board separator color.
	 */
	private static final String BOARD_GRID_FILL = "#b2b2b2";

	/**
	 * Normal embedded light-square color.
	 */
	private static final String BOARD_LIGHT_FILL = "#e5e5e5";

	/**
	 * Normal embedded dark-square color.
	 */
	private static final String BOARD_DARK_FILL = "#cccccc";

	/**
	 * Brightened board separator color used on solution diagrams.
	 */
	private static final String SOLUTION_BOARD_GRID_FILL = "#d3d3d3";

	/**
	 * Brightened light-square color used on solution diagrams.
	 */
	private static final String SOLUTION_BOARD_LIGHT_FILL = "#ffffff";

	/**
	 * Brightened dark-square color used on solution diagrams.
	 */
	private static final String SOLUTION_BOARD_DARK_FILL = "#efefef";

	/**
	 * Common SVG tag terminator used by generated gradient definitions.
	 */
	private static final String SVG_TAG_END = "\">\n";

	/**
	 * Private constructor because rendering is exposed through static entry points.
	 */
	private Writer() {
		// utility
	}

	/**
	 * Writes a fully rendered chess book to the requested output path.
	 *
	 * <p>
	 * The renderer performs a small layout iteration to stabilize the generated
	 * table of contents before producing the final PDF bytes.
	 * </p>
	 *
	 * @param output target PDF path
	 * @param book book metadata and puzzle payload
	 * @throws IOException if the target file cannot be written
	 */
	public static void write(Path output, Book book) throws IOException {
		write(output, book, false);
	}

	/**
	 * Writes a fully rendered chess book to the requested output path.
	 *
	 * @param output target PDF path
	 * @param book book metadata and puzzle payload
	 * @param freeWatermark whether to create a noisy watermarked free-edition PDF
	 * @throws IOException if the target file cannot be written
	 */
	public static void write(Path output, Book book, boolean freeWatermark) throws IOException {
		write(output, book, freeWatermark, null);
	}

	/**
	 * Writes a fully rendered chess book to the requested output path.
	 *
	 * @param output target PDF path
	 * @param book book metadata and puzzle payload
	 * @param freeWatermark whether to create a noisy watermarked free-edition PDF
	 * @param watermarkId optional visible identifier for traceable copies
	 * @throws IOException if the target file cannot be written
	 */
	public static void write(Path output, Book book, boolean freeWatermark, String watermarkId) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output cannot be null");
		}
		Book safeBook = WriterSupport.requireBook(book);
		SolutionInfo[] solutions = WriterSupport.buildSolutions(safeBook);
		String safeWatermarkId = freeWatermark ? WriterSupport.buildWatermarkId(safeBook, watermarkId) : "";

		int tocPages = 1;
		LayoutResult preview = null;
		for (int i = 0; i < 6; i++) {
			preview = layout(safeBook, solutions, tocPages, false, false, "");
			int measured = measureTocPages(safeBook, preview.tocEntries);
			if (measured == tocPages) {
				break;
			}
			tocPages = measured;
		}

		LayoutResult rendered = layout(safeBook, solutions, tocPages, true, freeWatermark, safeWatermarkId);
		rendered.document.write(output);
	}

	/**
	 * Builds either a simulated or real layout pass for the supplied book.
	 *
	 * @param book book metadata and content
	 * @param solutions parsed solution cache for each puzzle
	 * @param tocPages reserved table-of-contents page count
	 * @param render whether real PDF pages should be created
	 * @param freeWatermark whether to add noisy free-edition watermark overlays
	 * @param watermarkId visible watermark identifier
	 * @return layout summary for the pass
	 */
	private static LayoutResult layout(Book book, SolutionInfo[] solutions, int tocPages, boolean render,
			boolean freeWatermark, String watermarkId) {
		Document document = render ? createDocument(book, freeWatermark, watermarkId) : null;

		LayoutState state = new LayoutState(book, solutions, document, tocPages, watermarkId);
		renderCover(state);
		renderDedication(state);
		reserveTocPages(state);
		clearDoublePage(state);
		renderIntroduction(state);
		renderHowToRead(state);
		renderPuzzles(state);
		renderAfterword(state);
		renderToc(state);
		if (render && freeWatermark) {
			applyFreeEditionWatermark(state);
		}
		return new LayoutResult(document, state.tocEntries);
	}

	/**
	 * Creates a configured PDF document for a real render pass.
	 *
	 * @param book source book metadata
	 * @param freeWatermark whether the free-edition subject should be written
	 * @param watermarkId visible watermark identifier
	 * @return configured PDF document
	 */
	private static Document createDocument(Book book, boolean freeWatermark, String watermarkId) {
		String subject = freeWatermark ? WriterSupport.watermarkSubject(FREE_WATERMARK_SUBJECT, watermarkId) : SUBJECT;
		return new Document()
				.setTitle(book.getFullTitle())
				.setAuthor(book.getAuthor())
				.setSubject(subject)
				.setCreator(CREATOR)
				.setProducer(PRODUCER);
	}

	/**
	 * Renders the cover page and enforces the next odd-page start.
	 *
	 * @param state active layout state
	 */
	private static void renderCover(LayoutState state) {
		PageFrame page = addPage(state, PageStyle.HIDDEN, "");
		if (page.canvas == null) {
			clearDoublePage(state);
			return;
		}

		double titleWidth = textWidth(Font.LATIN_MODERN_BOLD, COVER_TITLE_SIZE, state.book.getTitle());
		double titleX = page.left + Math.max(0.0, (page.width - titleWidth) / 2.0);
		double titleY = page.top + page.height * 0.18;
		page.canvas.drawText(titleX, titleY, Font.LATIN_MODERN_BOLD, COVER_TITLE_SIZE, INK, state.book.getTitle());

		double lineY = titleY + Font.LATIN_MODERN_BOLD.lineHeight(COVER_TITLE_SIZE) + 10.0;
		page.canvas.line(page.left + page.width * 0.1, lineY, page.right - page.width * 0.1, lineY, SEPARATOR, 0.8);

		String subtitle = state.book.getSubtitle();
		if (!subtitle.isBlank()) {
				drawCentered(page.canvas, page.left, page.right, lineY + 16.0,
						textStyle(Font.LATIN_MODERN_ROMAN, COVER_SUBTITLE_SIZE, MUTED), subtitle);
		}

		double imprintY = page.bottom - 62.0;
		for (String line : safeArray(state.book.getImprint())) {
			if (!line.isBlank()) {
					drawCentered(page.canvas, page.left, page.right, imprintY,
							textStyle(Font.LATIN_MODERN_ROMAN, COVER_FOOTER_SIZE, INK), line);
				imprintY += 11.0;
			}
		}

		String[] copyright = buildCoverCopyright(state.book);
		double copyrightY = page.bottom - 30.0;
		for (String line : copyright) {
				drawCentered(page.canvas, page.left, page.right, copyrightY,
						textStyle(Font.LATIN_MODERN_ROMAN, COVER_FOOTER_SIZE, INK), line);
			copyrightY += 11.0;
		}
		clearDoublePage(state);
	}

	/**
	 * Builds a short cover copyright line.
	 *
	 * @param book source book
	 * @return copyright lines
	 */
	private static String[] buildCoverCopyright(Book book) {
		return WriterSupport.buildCoverCopyright(book);
	}

	/**
	 * Renders the optional dedication page and enforces the next odd-page start.
	 *
	 * @param state active layout state
	 */
	private static void renderDedication(LayoutState state) {
		String[] lines = safeArray(state.book.getDedication());
		if (lines.length == 0) {
			return;
		}

		PageFrame page = addPage(state, PageStyle.HIDDEN, "");
		if (page.canvas != null) {
			List<String> visible = new ArrayList<>();
			for (String line : lines) {
				if (!line.isBlank()) {
					visible.add(line);
				}
			}
			double lineHeight = Font.LATIN_MODERN_ROMAN.lineHeight(BODY_SIZE + 1.0);
			double startY = page.top + Math.max(0.0, (page.height - visible.size() * lineHeight) / 2.0);
			double y = startY;
			for (String line : visible) {
					drawCentered(page.canvas, page.left, page.right, y,
							textStyle(Font.LATIN_MODERN_ROMAN, BODY_SIZE + 1.0, INK), line);
				y += lineHeight;
			}
		}

		clearDoublePage(state);
	}

	/**
	 * Reserves the table-of-contents pages in the current page stream.
	 *
	 * @param state active layout state
	 */
	private static void reserveTocPages(LayoutState state) {
		for (int i = 0; i < state.tocPages; i++) {
			PageFrame page = addPage(state, PageStyle.PLAIN, "");
			state.tocPageFrames.add(page);
		}
	}

	/**
	 * Renders the optional introduction section.
	 *
	 * @param state active layout state
	 */
	private static void renderIntroduction(LayoutState state) {
		String[] intro = safeArray(state.book.getIntroduction());
		if (intro.length == 0) {
			return;
		}
		TextCursor cursor = openSection(state, Texts.introduction(state.book.getLanguage()), false);
		cursor = renderParagraphs(state, cursor, Texts.introduction(state.book.getLanguage()), intro, false);

		String signature = buildIntroductionSignature(state.book);
		if (!signature.isBlank()) {
			cursor = ensureBodySpace(state, cursor, BODY_LEADING, Texts.introduction(state.book.getLanguage()));
			if (cursor.page.canvas != null) {
				double width = textWidth(Font.LATIN_MODERN_BOLD, BODY_SIZE, signature);
				double x = state.book.getLanguage().isLeftToRight()
						? cursor.page.right - width
						: cursor.page.left;
				cursor.page.canvas.drawText(x, cursor.y, Font.LATIN_MODERN_BOLD, BODY_SIZE, INK, signature);
			}
			cursor.y += BODY_LEADING;
		}

		clearDoublePage(state);
	}

	/**
	 * Renders the how-to-read section using either custom or fallback paragraphs.
	 *
	 * @param state active layout state
	 */
	private static void renderHowToRead(LayoutState state) {
		String[] paragraphs = safeArray(state.book.getHowToRead());
		String title = Texts.howToRead(state.book.getLanguage());
		if (paragraphs.length > 0) {
			TextCursor cursor = openSection(state, title, false);
			renderParagraphs(state, cursor, title, paragraphs, false);
			clearDoublePage(state);
			return;
		}

		renderDefaultHowToRead(state, title);
		clearDoublePage(state);
	}

	/**
	 * Renders generated how-to-read pages.
	 *
	 * @param state active layout state
	 * @param title section title
	 */
	private static void renderDefaultHowToRead(LayoutState state, String title) {
		String[] paragraphs = Texts.defaultHowToRead();
		TextCursor cursor = openSection(state, title, false);
		cursor = renderParagraphs(state, cursor, title, new String[] { paragraphs[0], paragraphs[1] }, false);
		cursor = renderParagraphs(state, cursor, title, new String[] { "Here is an example for reference:" }, false);
		cursor = renderPuzzleAndSolutionExamples(state, cursor, title);
		renderParagraphs(state, cursor, title, new String[] { paragraphs[2] }, false);

		PageFrame next = addPage(state, PageStyle.BODY, title);
		cursor = new TextCursor(next, next.top);
		cursor = renderSubheading(state, cursor, title, "Special Illustrations for Castling and En Passant");
		cursor = renderParagraphs(state, cursor, title, new String[] { paragraphs[3] }, false);
		cursor = renderBullets(state, cursor, title, new String[] {
				"Board Orientation: White is always at the bottom of every diagram. The a1 square is the lower-left square, and h8 is the upper-right square.",
				"En Passant: If en passant is an option, an arrow will highlight the last pawn move that makes it possible.",
				"Castling: An arrow will point in the direction you can castle." });
		cursor = renderParagraphs(state, cursor, title, new String[] { "Here are some examples for reference:" },
				false);
		cursor = renderSpecialMoveExamples(state, cursor, title);
		cursor = renderParagraphs(state, cursor, title, new String[] { paragraphs[4] }, false);
		cursor = renderSubheading(state, cursor, title, "How to Use This Book");
		cursor = renderBullets(state, cursor, title, new String[] {
				"Solve the Puzzles: Begin by studying the position on the left page. Carefully consider your options and try to find the best move for White. When you believe you've found the solution, look at the right page to see the final position and check your answer.",
				"Reference the Solutions: Between the puzzles, you will regularly find solution tables. These list the correct moves for the puzzles. At the end of each solution page, the page number of the next solution table is indicated, so you can easily continue navigating through the book." });
		renderParagraphs(state, cursor, title, new String[] { paragraphs[5], paragraphs[6] }, false);
	}

	/**
	 * Renders the example puzzle and solution diagrams in the how-to-read section.
	 *
	 * @param state active layout state
	 * @param cursor active text cursor
	 * @param runningHeader running header text
	 * @return updated text cursor
	 */
	private static TextCursor renderPuzzleAndSolutionExamples(LayoutState state, TextCursor cursor,
			String runningHeader) {
		double previewWidth = Math.min(150.0, (cursor.page.width - 30.0) / 2.0);
		double previewHeight = previewWidth * pagePreviewAspectRatio(state.book);
		double required = figureBlockHeight(previewHeight);
		cursor = ensureBodySpace(state, cursor, required, runningHeader);
		if (cursor.page.canvas != null) {
			double leftX = cursor.page.left + (cursor.page.width - previewWidth * 2.0 - 30.0) / 2.0;
			double rightX = leftX + previewWidth + 30.0;
			drawCaptionedPagePreview(cursor.page.canvas, new Box(leftX, cursor.y, previewWidth, previewHeight),
					"Puzzle Page", state, false);
			drawCaptionedPagePreview(cursor.page.canvas, new Box(rightX, cursor.y, previewWidth, previewHeight),
					"Solution Page", state, true);
		}
		cursor.y += required + PARAGRAPH_GAP;
		return cursor;
	}

	/**
	 * Renders the castling and en-passant example diagrams.
	 *
	 * @param state active layout state
	 * @param cursor active text cursor
	 * @param runningHeader running header text
	 * @return updated text cursor
	 */
	private static TextCursor renderSpecialMoveExamples(LayoutState state, TextCursor cursor, String runningHeader) {
		double shadowReserveScale = EXAMPLE_BOARD_SHADOW_FRACTION * PAGE_SHADOW_RESERVE_SCALE;
		double gutter = cursor.page.width * SPECIAL_EXAMPLE_GUTTER_FRACTION;
		double rawBoardSize = cursor.page.width * SPECIAL_EXAMPLE_BOARD_FRACTION;
		double maxBoardSize = Math.max(10.0,
				(cursor.page.width - gutter * 4.0) / (3.0 * (1.0 + shadowReserveScale)));
		double boardSize = Math.min(rawBoardSize, maxBoardSize);
		double shadowReserve = boardSize * shadowReserveScale;
		double figureWidth = boardSize + shadowReserve;
		double horizontalGap = Math.max(gutter, (cursor.page.width - figureWidth * 3.0) / 4.0);
		double required = figureBlockHeight(figureWidth);
		cursor = ensureBodySpace(state, cursor, required, runningHeader);
		if (cursor.page.canvas != null) {
			double x = cursor.page.left + horizontalGap;
			drawCaptionedBoardSvg(cursor.page.canvas, x, cursor.y, boardSize,
					"White can capture en passant on g6.",
					renderSpecialRightsSvg(EXAMPLE_EN_PASSANT_FEN, true, false));
			x += figureWidth + horizontalGap;
			drawCaptionedBoardSvg(cursor.page.canvas, x, cursor.y, boardSize,
					"White can castle kingside, and Black can castle queenside.",
					renderSpecialRightsSvg(EXAMPLE_CASTLING_FEN, false, true));
			x += figureWidth + horizontalGap;
			drawCaptionedBoardSvg(cursor.page.canvas, x, cursor.y, boardSize,
					"White can capture en passant on d6 and castle queenside.",
					renderSpecialRightsSvg(EXAMPLE_SPECIAL_RIGHTS_FEN, true, true));
		}
		cursor.y += required + PARAGRAPH_GAP;
		return cursor;
	}

	/**
	 * Renders a bold subsection-style heading inside flowing prose.
	 *
	 * @param state active layout state
	 * @param cursor active text cursor
	 * @param runningHeader running header text
	 * @param text heading text
	 * @return updated text cursor
	 */
	private static TextCursor renderSubheading(LayoutState state, TextCursor cursor, String runningHeader,
			String text) {
		cursor = ensureBodySpace(state, cursor, BODY_LEADING + 4.0, runningHeader);
		if (cursor.page.canvas != null) {
			cursor.page.canvas.drawText(cursor.page.left, cursor.y, Font.LATIN_MODERN_BOLD, SUBHEADING_SIZE, INK,
					text);
		}
		cursor.y += BODY_LEADING + 2.0;
		return cursor;
	}

	/**
	 * Renders simple bullet paragraphs using the same body flow as normal prose.
	 *
	 * @param state active layout state
	 * @param cursor active text cursor
	 * @param runningHeader running header text
	 * @param items bullet item text
	 * @return updated text cursor
	 */
	private static TextCursor renderBullets(LayoutState state, TextCursor cursor, String runningHeader,
			String[] items) {
		for (String item : safeArray(items)) {
			cursor = renderListItem(state, cursor, runningHeader, new ListItem(UNORDERED_LIST_MARKER, item));
		}
		return cursor;
	}

	/**
	 * Builds the introduction signature line from location and time.
	 *
	 * @param book source book
	 * @return signature text
	 */
	private static String buildIntroductionSignature(Book book) {
		String left = blankTo(book.getLocation(), "");
		String right = blankTo(book.getTime(), "");
		if (left.isBlank()) {
			return right;
		}
		if (right.isBlank()) {
			return left;
		}
		return left + " - " + right;
	}

	/**
	 * Renders the puzzle section, its spreads, and its recurring solution tables.
	 *
	 * @param state active layout state
	 */
	private static void renderPuzzles(LayoutState state) {
		String puzzlesTitle = Texts.puzzles(state.book.getLanguage());
		PageFrame sectionPage = addPage(state, PageStyle.PLAIN, "");
		String number = Integer.toString(++state.sectionNumber);
		state.tocEntries.add(new TocEntry(number + " " + puzzlesTitle, 1, sectionPage.pageNumber));
		drawCenteredSectionPage(sectionPage, number, puzzlesTitle);
		clearEvenPage(state);

		List<PuzzleBlock> blocks = buildPuzzleBlocks(state.book);
		for (int i = 0; i < blocks.size(); i++) {
			PuzzleBlock block = blocks.get(i);
			String sectionLabel = Texts.section(state.book.getLanguage()) + (i + 1);
			int blockStartPage = state.pageNumber + 1;
			state.tocEntries.add(new TocEntry(sectionLabel, 2, blockStartPage));
			int tableStartPage = blockStartPage + block.spreadCount * 2;

			for (int spread = 0; spread < block.spreadCount; spread++) {
				int index = block.startIndex + spread * puzzlesPerPage(state.book);
				int count = Math.min(puzzlesPerPage(state.book), block.count - spread * puzzlesPerPage(state.book));
				PageFrame puzzlePage = addPage(state, PageStyle.BODY, sectionLabel);
				renderPuzzleSpread(state, puzzlePage, index, count);
				clearDoublePage(state);

				PageFrame solutionPage = addPage(state, PageStyle.BODY, sectionLabel);
				renderSolutionSpread(state, solutionPage, index, count, tableStartPage);
				clearEvenPage(state);
			}

			renderSolutionTable(state, sectionLabel, block.startIndex, block.count);
			clearEvenBodyPage(state, sectionLabel);
		}
	}

	/**
	 * Builds the logical puzzle blocks between recurring solution tables.
	 *
	 * @param book source book
	 * @return ordered block list
	 */
	private static List<PuzzleBlock> buildPuzzleBlocks(Book book) {
		List<PuzzleBlock> blocks = new ArrayList<>();
		int perPage = puzzlesPerPage(book);
		int perBlock = Math.max(perPage, perPage * Math.max(1, book.getTableFrequency()));
		int total = book.getElements().length;
		for (int index = 0; index < total; index += perBlock) {
			int count = Math.min(perBlock, total - index);
			int spreads = (count + perPage - 1) / perPage;
			blocks.add(new PuzzleBlock(index, count, spreads));
		}
		return blocks;
	}

	/**
	 * Returns the number of puzzles placed on one puzzle page.
	 *
	 * @param book source book
	 * @return page capacity
	 */
	private static int puzzlesPerPage(Book book) {
		return Math.max(1, book.getPuzzleRows()) * Math.max(1, book.getPuzzleColumns());
	}

	/**
	 * Computes the shared puzzle and solution grid geometry for one page.
	 *
	 * <p>
	 * Both sides of a puzzle spread use the same reserved footnote clearance so
	 * row tops and board sizes stay level across the left and right pages.
	 * </p>
	 *
	 * @param page target page
	 * @param book source book
	 * @return shared board-grid metrics
	 */
	private static GridMetrics boardGrid(PageFrame page, Book book) {
		int rows = Math.max(1, book.getPuzzleRows());
		int columns = Math.max(1, book.getPuzzleColumns());
		double gridHeight = Math.max(40.0, page.height - solutionFootnoteReserve());
		double cellHeight = (gridHeight - GRID_GAP * (rows - 1)) / rows;
		double labelHeight = Math.max(
				Font.LATIN_MODERN_BOLD.lineHeight(PUZZLE_LABEL_SIZE),
				Font.LATIN_MODERN_BOLD.lineHeight(SOLUTION_LABEL_SIZE));
		double targetBoardWidth = page.width * PUZZLE_BOARD_FRACTION;
		double boardWidth = Math.max(12.0,
				Math.min(Math.min(targetBoardWidth, page.width / columns),
						cellHeight - labelHeight - GRID_LABEL_GAP));
		double horizontalGap = columns == 1 ? 0.0
				: Math.max(0.0, (page.width - boardWidth * columns) / (columns - 1));
		return new GridMetrics(columns, boardWidth, cellHeight, boardWidth, horizontalGap);
	}

	/**
	 * Returns the vertical clearance reserved for solution footnotes.
	 *
	 * @return reserved height in points
	 */
	private static double solutionFootnoteReserve() {
		return Font.LATIN_MODERN_ROMAN.lineHeight(SOLUTION_FOOTNOTE_SIZE) + SOLUTION_FOOTNOTE_CLEARANCE;
	}

	/**
	 * Renders the puzzle-side grid for one spread.
	 *
	 * @param state active layout state
	 * @param page target page
	 * @param startIndex first puzzle index on the page
	 * @param count number of puzzles to render
	 */
	private static void renderPuzzleSpread(LayoutState state, PageFrame page, int startIndex, int count) {
		if (page.canvas == null) {
			return;
		}
		GridMetrics grid = boardGrid(page, state.book);

		for (int local = 0; local < count; local++) {
			int index = startIndex + local;
			int row = local / grid.columns;
			int column = local % grid.columns;
			double cellX = page.left + column * (grid.boardWidth + grid.horizontalGap);
			double cellY = page.top + row * (grid.cellHeight + GRID_GAP);
			double boardX = cellX;
			double boardY = cellY;
			page.canvas.drawSvg(renderPuzzleSvg(state.book.getElements()[index]), boardX, boardY, grid.boardWidth,
					grid.boardWidth);
			String label = Integer.toString(index + 1);
				drawCentered(page.canvas, cellX, cellX + grid.cellWidth, boardY + grid.boardWidth + GRID_LABEL_GAP,
						textStyle(Font.LATIN_MODERN_BOLD, PUZZLE_LABEL_SIZE, INK), label);
		}
	}

	/**
	 * Renders the solution-side grid for one spread.
	 *
	 * @param state active layout state
	 * @param page target page
	 * @param startIndex first puzzle index on the page
	 * @param count number of solutions to render
	 * @param tableStartPage page number of the block's solution table
	 */
	private static void renderSolutionSpread(LayoutState state, PageFrame page, int startIndex, int count,
			int tableStartPage) {
		if (page.canvas == null) {
			return;
		}
		GridMetrics grid = boardGrid(page, state.book);

		for (int local = 0; local < count; local++) {
			int index = startIndex + local;
			int row = local / grid.columns;
			int column = local % grid.columns;
			double cellX = page.left + column * (grid.boardWidth + grid.horizontalGap);
			double cellY = page.top + row * (grid.cellHeight + GRID_GAP);
			double boardX = cellX;
			double boardY = cellY;
			page.canvas.drawSvg(renderSolutionSvg(state.solutions[index]), boardX, boardY, grid.boardWidth,
					grid.boardWidth);
			String label = Integer.toString(index + 1) + " (" + state.solutions[index].lastSanLabel + ")";
			drawCenteredNotationText(page.canvas, cellX, cellX + grid.cellWidth,
					boardY + grid.boardWidth + GRID_LABEL_GAP,
					textStyle(Font.LATIN_MODERN_BOLD, SOLUTION_LABEL_SIZE, INK),
					fitNotationText(label, Font.LATIN_MODERN_BOLD, SOLUTION_LABEL_SIZE, grid.cellWidth));
		}

		String footnote = Texts.fullSolutions(state.book.getLanguage()) + tableStartPage;
		Font footnoteFont = Font.LATIN_MODERN_ITALIC;
		double footnoteHeight = footnoteFont.lineHeight(SOLUTION_FOOTNOTE_SIZE);
		double footnoteWidth = textWidth(footnoteFont, SOLUTION_FOOTNOTE_SIZE, footnote);
		double footnoteX = page.left + Math.max(0.0, (page.width - footnoteWidth) / 2.0);
		double footnoteY = page.bottom - footnoteHeight;
		page.canvas.drawText(footnoteX, footnoteY, footnoteFont, SOLUTION_FOOTNOTE_SIZE, MUTED, footnote);
		page.canvas.linkToPage(footnoteX, footnoteY, footnoteWidth, footnoteHeight, tableStartPage);
	}

	/**
	 * Renders the recurring solution table block with repeated headers.
	 *
	 * @param state active layout state
	 * @param sectionLabel running-header label for odd pages
	 * @param startIndex first puzzle index in the block
	 * @param count number of puzzle rows covered by the block
	 */
	private static void renderSolutionTable(LayoutState state, String sectionLabel, int startIndex, int count) {
		List<TableRow> rows = buildTableRows(state.book, startIndex, count);
		TextCursor cursor = new TextCursor(addPage(state, PageStyle.BODY, sectionLabel), 0.0);
		cursor.y = drawTableHeader(cursor.page, state.book);
		boolean rowsOnPage = false;

		for (TableRow row : rows) {
			for (TableRow segment : splitTableRow(row, cursor.page)) {
				double rowHeight = tableRowHeight(segment, cursor.page.width);
				if (cursor.y + rowHeight > cursor.page.bottom) {
					if (rowsOnPage) {
						drawTableBottomRule(cursor.page, cursor.y);
					}
					cursor = new TextCursor(addPage(state, PageStyle.BODY, sectionLabel), 0.0);
					cursor.y = drawTableHeader(cursor.page, state.book);
				}
				if (cursor.page.canvas != null) {
					drawTableRow(cursor.page, segment, cursor.y);
				}
				cursor.y += rowHeight;
				rowsOnPage = true;
			}
		}

		if (rowsOnPage) {
			drawTableBottomRule(cursor.page, cursor.y);
		}
	}

	/**
	 * Draws the closing horizontal rule for one visible table page.
	 *
	 * @param page target page
	 * @param y rule y coordinate
	 */
	private static void drawTableBottomRule(PageFrame page, double y) {
		if (page.canvas != null) {
			page.canvas.line(tableLeft(page), y, tableRight(page), y, TABLE_GRAY, 0.6);
		}
	}

	/**
	 * Builds the solution-table row list for one block.
	 *
	 * @param book source book
	 * @param startIndex first puzzle index in the block
	 * @param count puzzle count in the block
	 * @return ordered table rows
	 */
	private static List<TableRow> buildTableRows(Book book, int startIndex, int count) {
		List<TableRow> rows = new ArrayList<>((count + 1) / 2);
		Element[] elements = book.getElements();
		for (int i = 0; i < count; i += 2) {
			int leftIndex = startIndex + i;
			int rightIndex = leftIndex + 1;
			String leftId = Integer.toString(leftIndex + 1);
			String leftMoves = MoveText.figurine(normalizeWhitespace(elements[leftIndex].getMoves()));
			String rightId = rightIndex < startIndex + count ? Integer.toString(rightIndex + 1) : "";
			String rightMoves = rightIndex < startIndex + count
					? MoveText.figurine(normalizeWhitespace(elements[rightIndex].getMoves()))
					: "";
			rows.add(new TableRow(leftId, leftMoves, rightId, rightMoves));
		}
		return rows;
	}

	/**
	 * Measures the rendered height of one solution-table row.
	 *
	 * @param row row to measure
	 * @param tableWidth available table width
	 * @return measured row height
	 */
	private static double tableRowHeight(TableRow row, double tableWidth) {
		double[] widths = tableColumnWidths(tableContentWidth(tableWidth));
		double leftMovesHeight = wrappedNotationHeight(row.leftMoves, Font.LATIN_MODERN_ROMAN, TABLE_SIZE,
				widths[1] - TABLE_CELL_PADDING * 2.0, TABLE_LEADING);
		double rightMovesHeight = wrappedNotationHeight(row.rightMoves, Font.LATIN_MODERN_ROMAN, TABLE_SIZE,
				widths[3] - TABLE_CELL_PADDING * 2.0, TABLE_LEADING);
		double contentHeight = Math.max(TABLE_LEADING, Math.max(leftMovesHeight, rightMovesHeight));
		return Math.max(TABLE_LEADING + TABLE_CELL_PADDING * 2.0, contentHeight + TABLE_CELL_PADDING * 2.0);
	}

	/**
	 * Splits a table row into page-sized drawing segments when a single solution
	 * line is too tall for one page.
	 *
	 * <p>
	 * Normal rows remain a single segment. Oversized rows keep the same ID columns
	 * on every segment so the continuation is still readable after a page break.
	 * </p>
	 *
	 * @param row row to split
	 * @param page current page geometry
	 * @return one or more page-safe row segments
	 */
	private static List<TableRow> splitTableRow(TableRow row, PageFrame page) {
		double[] widths = tableColumnWidths(tableContentWidth(page.width));
		List<String> leftLines = nonEmptyLines(wrapNotationLines(row.leftMoves, Font.LATIN_MODERN_ROMAN, TABLE_SIZE,
				widths[1] - TABLE_CELL_PADDING * 2.0));
		List<String> rightLines = nonEmptyLines(wrapNotationLines(row.rightMoves, Font.LATIN_MODERN_ROMAN, TABLE_SIZE,
				widths[3] - TABLE_CELL_PADDING * 2.0));
		int maxLines = maxTableSegmentLines(page);
		int totalLines = Math.max(leftLines.size(), rightLines.size());
		if (totalLines <= maxLines) {
			return List.of(row);
		}

		List<TableRow> segments = new ArrayList<>((totalLines + maxLines - 1) / maxLines);
		for (int start = 0; start < totalLines; start += maxLines) {
			int end = Math.min(totalLines, start + maxLines);
			segments.add(new TableRow(
					row.leftId,
					joinLines(leftLines, start, end),
					row.rightId,
					joinLines(rightLines, start, end)));
		}
		return segments;
	}

	/**
	 * Computes how many wrapped move lines can fit in one table segment.
	 *
	 * @param page page geometry used for the table
	 * @return maximum wrapped line count per segment
	 */
	private static int maxTableSegmentLines(PageFrame page) {
		double headerHeight = Math.max(16.0, Font.LATIN_MODERN_BOLD.lineHeight(TABLE_HEADER_SIZE)
				+ TABLE_CELL_PADDING * 2.0);
		double bodyHeight = Math.max(15.0, page.bottom - page.top - headerHeight);
		double usableHeight = Math.max(TABLE_LEADING, bodyHeight - TABLE_CELL_PADDING * 2.0);
		return Math.max(1, (int) Math.floor(usableHeight / TABLE_LEADING));
	}

	/**
	 * Ensures a wrapped line list contains at least one drawable row slot.
	 *
	 * @param lines wrapped lines
	 * @return original lines or a single blank line
	 */
	private static List<String> nonEmptyLines(List<String> lines) {
		return lines == null || lines.isEmpty() ? List.of("") : lines;
	}

	/**
	 * Joins a bounded slice of wrapped lines into text that preserves line breaks.
	 *
	 * @param lines source lines
	 * @param start inclusive start index
	 * @param end exclusive end index
	 * @return newline-joined line slice
	 */
	private static String joinLines(List<String> lines, int start, int end) {
		if (start >= lines.size()) {
			return "";
		}
		return String.join("\n", lines.subList(start, Math.min(end, lines.size())));
	}

	/**
	 * Draws the table header and returns the next content cursor position.
	 *
	 * @param page target page
	 * @param book source book
	 * @return next vertical cursor position
	 */
	private static double drawTableHeader(PageFrame page, Book book) {
		double y = page.top;
		if (page.canvas != null) {
			page.canvas.line(tableLeft(page), y, tableRight(page), y, TABLE_GRAY, 0.8);
			String id = Texts.id(book.getLanguage());
			String moves = Texts.moves(book.getLanguage());
			TableRow header = new TableRow(id, moves, id, moves);
			drawTableRowInternal(page, header, y, true);
		}
		return y + Math.max(16.0, Font.LATIN_MODERN_BOLD.lineHeight(TABLE_HEADER_SIZE) + TABLE_CELL_PADDING * 2.0);
	}

	/**
	 * Draws one non-header table row.
	 *
	 * @param page target page
	 * @param row row to draw
	 * @param y top edge of the row
	 */
	private static void drawTableRow(PageFrame page, TableRow row, double y) {
		drawTableRowInternal(page, row, y, false);
	}

	/**
	 * Draws one table row with optional header styling.
	 *
	 * @param page target page
	 * @param row row to draw
	 * @param y top edge of the row
	 * @param header whether the row is a header row
	 */
	private static void drawTableRowInternal(PageFrame page, TableRow row, double y, boolean header) {
		if (page.canvas == null) {
			return;
		}
		double rowHeight = header
				? Math.max(16.0, Font.LATIN_MODERN_BOLD.lineHeight(TABLE_HEADER_SIZE) + TABLE_CELL_PADDING * 2.0)
				: tableRowHeight(row, page.width);
		double tableWidth = tableContentWidth(page.width);
		double[] widths = tableColumnWidths(tableWidth);
		double x = tableLeft(page);

		if (header) {
			page.canvas.line(tableLeft(page), y + rowHeight, tableRight(page), y + rowHeight, TABLE_GRAY, 0.6);
		}
		drawTableCell(page.canvas, x, y, widths[0], rowHeight, row.leftId,
				tableCellStyle(Font.LATIN_MODERN_BOLD, header ? TABLE_HEADER_SIZE : TABLE_SIZE, true));
		x += widths[0];
		drawTableCell(page.canvas, x, y, widths[1], rowHeight, row.leftMoves,
				tableCellStyle(header ? Font.LATIN_MODERN_BOLD : Font.LATIN_MODERN_ROMAN,
						header ? TABLE_HEADER_SIZE : TABLE_SIZE, false));
		x += widths[1];
		drawTableCell(page.canvas, x, y, widths[2], rowHeight, row.rightId,
				tableCellStyle(Font.LATIN_MODERN_BOLD, header ? TABLE_HEADER_SIZE : TABLE_SIZE, true));
		x += widths[2];
		drawTableCell(page.canvas, x, y, widths[3], rowHeight, row.rightMoves,
				tableCellStyle(header ? Font.LATIN_MODERN_BOLD : Font.LATIN_MODERN_ROMAN,
						header ? TABLE_HEADER_SIZE : TABLE_SIZE, false));
	}

	/**
	 * Returns the four logical solution-table column widths.
	 *
	 * @param tableWidth total table width
	 * @return width array in left-id, left-moves, right-id, right-moves order
	 */
	private static double[] tableColumnWidths(double tableWidth) {
		return new double[] {
				tableWidth * (0.05 / 0.90),
				tableWidth * (0.40 / 0.90),
				tableWidth * (0.05 / 0.90),
				tableWidth * (0.40 / 0.90) };
	}

	/**
	 * Returns the centered table width used by solution tables.
	 *
	 * @param pageWidth page content width
	 * @return table content width
	 */
	private static double tableContentWidth(double pageWidth) {
		return pageWidth * 0.90;
	}

	/**
	 * Returns the table's left edge.
	 *
	 * @param page target page
	 * @return table left edge
	 */
	private static double tableLeft(PageFrame page) {
		return page.left + page.width * 0.05;
	}

	/**
	 * Returns the table's right edge.
	 *
	 * @param page target page
	 * @return table right edge
	 */
	private static double tableRight(PageFrame page) {
		return tableLeft(page) + tableContentWidth(page.width);
	}

	/**
	 * Creates a reusable text style descriptor.
	 *
	 * @param font font to use
	 * @param size font size
	 * @param color fill color
	 * @return text style descriptor
	 */
	private static TextStyle textStyle(Font font, double size, Color color) {
		return new TextStyle(font, size, color);
	}

	/**
	 * Creates a solution-table cell style descriptor.
	 *
	 * @param font font to use
	 * @param size font size
	 * @param centered whether text should be centered in the cell
	 * @return table cell style descriptor
	 */
	private static TableCellStyle tableCellStyle(Font font, double size, boolean centered) {
		return new TableCellStyle(textStyle(font, size, INK), centered);
	}

	/**
	 * Draws one table cell.
	 *
	 * @param canvas target canvas
	 * @param x cell left edge
	 * @param y cell top edge
	 * @param width cell width
	 * @param height cell height
	 * @param text cell text
	 * @param style table cell style
	 */
	private static void drawTableCell(Canvas canvas, double x, double y, double width, double height, String text,
			TableCellStyle style) {
		Font font = style.text().font();
		double size = style.text().size();
		boolean notation = containsNotationPiece(text);
		List<String> lines = notation
				? wrapNotationLines(text, font, size, Math.max(10.0, width - TABLE_CELL_PADDING * 2.0))
				: wrapLines(text, font, size, Math.max(10.0, width - TABLE_CELL_PADDING * 2.0));
		double leading = size <= TABLE_SIZE ? TABLE_LEADING : BODY_LEADING;
		double totalHeight = lines.size() * leading;
		double cursorY = y + Math.max(TABLE_CELL_PADDING, (height - totalHeight) / 2.0);
		for (String line : lines) {
			if (style.centered()) {
				if (notation) {
					drawCenteredNotationText(canvas, x, x + width, cursorY, style.text(), line);
				} else {
					drawCentered(canvas, x, x + width, cursorY, style.text(), line);
				}
			} else if (notation) {
				drawNotationText(canvas, x + TABLE_CELL_PADDING, cursorY, font, size, style.text().color(), line);
			} else {
				canvas.drawText(x + TABLE_CELL_PADDING, cursorY, font, size, style.text().color(), line);
			}
			cursorY += leading;
		}
	}

	/**
	 * Renders the optional closing page.
	 *
	 * @param state active layout state
	 */
	private static void renderAfterword(LayoutState state) {
		String[] paragraphs = safeArray(state.book.getAfterword());
		if (paragraphs.length == 0) {
			paragraphs = Texts.defaultAfterword(state.book.getLanguage());
		}
		if (paragraphs.length == 0) {
			return;
		}

		clearDoubleBodyPage(state, state.book.getFullTitle());
		PageFrame page = addPage(state, PageStyle.PLAIN, "");
		if (page.canvas == null) {
			return;
		}

		List<String> lines = new ArrayList<>();
		for (String paragraph : paragraphs) {
			for (String line : wrapLines(paragraph, Font.LATIN_MODERN_ROMAN, BODY_SIZE + 2.0, page.width * 0.72)) {
				lines.add(line);
			}
			lines.add("");
		}
		double leading = BODY_LEADING;
		double totalHeight = Math.max(0.0, lines.size() - 1.0) * leading;
		double y = page.top + Math.max(0.0, (page.height - totalHeight) / 2.0);
		for (String line : lines) {
			if (!line.isBlank()) {
				drawCentered(page.canvas, page.left, page.right, y,
						textStyle(Font.LATIN_MODERN_ITALIC, BODY_SIZE + 2.0, INK), line);
			}
			y += leading;
		}
		double lineY = Math.min(page.bottom - 10.0, y + 10.0);
		page.canvas.line(page.left + page.width * 0.34, lineY, page.right - page.width * 0.34, lineY, CLOSING_LINE,
				0.7);
		drawClosingHeart(page, lineY + 18.0);
	}

	/**
	 * Draws a small vector heart below the afterword with a chess cutout.
	 *
	 * @param page target page
	 * @param y top edge
	 */
	private static void drawClosingHeart(PageFrame page, double y) {
		double size = Math.min(54.0, Math.max(34.0, page.width * 0.13));
		double x = page.left + (page.width - size) / 2.0;
		if (y + size > page.bottom) {
			return;
		}
		page.canvas.drawSvg("""
				<svg viewBox="0 0 100 96" xmlns="http://www.w3.org/2000/svg">
				  <path d="M50 86 C21 59 8 44 8 27 C8 13 18 5 31 5 C40 5 47 10 50 18 C53 10 60 5 69 5 C82 5 92 13 92 27 C92 44 79 59 50 86 Z" fill="#e6e6e6" stroke="#969696" stroke-width="4"/>
				  <path d="M50 20 L50 30" fill="none" stroke="#ffffff" stroke-width="5" stroke-linecap="round"/>
				  <path d="M43 25 L57 25" fill="none" stroke="#ffffff" stroke-width="5" stroke-linecap="round"/>
				  <path d="M36 66 C39 58 43 54 43 43 C43 35 57 35 57 43 C57 54 61 58 64 66 Z" fill="#ffffff"/>
				  <path d="M41 70 L59 70 L64 78 L36 78 Z" fill="#ffffff"/>
				  <circle cx="50" cy="40" r="10" fill="#ffffff"/>
				</svg>
				""", x, y, size, size);
	}

	/**
	 * Fills the reserved table-of-contents pages after final page numbers are known.
	 *
	 * @param state active layout state
	 */
	private static void renderToc(LayoutState state) {
		if (state.tocPageFrames.isEmpty()) {
			return;
		}

		String title = Texts.tableOfContents(state.book.getLanguage());
		int pageIndex = 0;
		PageFrame page = state.tocPageFrames.get(pageIndex);
		double y = drawTocHeading(page, title, true);

		for (TocEntry entry : state.tocEntries) {
			double lineHeight = 14.0;
			if (y + lineHeight > page.bottom) {
				pageIndex++;
				if (pageIndex >= state.tocPageFrames.size()) {
					break;
				}
				page = state.tocPageFrames.get(pageIndex);
				y = page.top;
			}
			drawTocEntry(page, entry, y);
			y += lineHeight;
		}
	}

	/**
	 * Measures how many pages the current table of contents will need.
	 *
	 * @param book source book for page geometry
	 * @param entries collected table-of-contents entries
	 * @return required page count
	 */
	private static int measureTocPages(Book book, List<TocEntry> entries) {
		PageSize size = book.toPageSize();
		double top = Book.cmToPoints(book.getTopMarginCm());
		double bottom = Book.cmToPoints(book.getBottomMarginCm());
		double firstHeight = size.getHeight() - top - bottom - 34.0;
		double continuationHeight = size.getHeight() - top - bottom;
		double remaining = firstHeight;
		int pages = 1;
		for (int i = 0; i < entries.size(); i++) {
			if (remaining < 14.0) {
				pages++;
				remaining = continuationHeight;
			}
			remaining -= 14.0;
		}
		return Math.max(1, pages);
	}

	/**
	 * Opens a standard section page and adds a top-level TOC entry.
	 *
	 * @param state active layout state
	 * @param title section title
	 * @param centered whether the section page should be vertically centered
	 * @return text cursor positioned after the section heading
	 */
	private static TextCursor openSection(LayoutState state, String title, boolean centered) {
		PageFrame page = addPage(state, PageStyle.PLAIN, "");
		String number = Integer.toString(++state.sectionNumber);
		state.tocEntries.add(new TocEntry(number + " " + title, 1, page.pageNumber));
		double y = centered
				? drawCenteredSectionPage(page, number, title)
				: drawSectionHeading(page, number, title);
		return new TextCursor(page, y);
	}

	/**
	 * Draws a normal section heading near the top margin.
	 *
	 * @param page target page
	 * @param number section number text
	 * @param title section title
	 * @return next vertical cursor position
	 */
	private static double drawSectionHeading(PageFrame page, String number, String title) {
		double headingTop = page.top + SECTION_HEADING_TOP_OFFSET;
		if (page.canvas == null) {
			return headingTop + SECTION_AFTER_GAP;
		}
		drawCentered(page.canvas, page.left, page.right, headingTop,
				textStyle(Font.LATIN_MODERN_BOLD, SECTION_TITLE_SIZE, INK), number);
		double titleY = headingTop + Font.LATIN_MODERN_BOLD.lineHeight(SECTION_TITLE_SIZE) + SECTION_TITLE_GAP;
		drawCentered(page.canvas, page.left, page.right, titleY,
				textStyle(Font.LATIN_MODERN_BOLD, SECTION_TITLE_SIZE, INK), title);
		double lineY = titleY + Font.LATIN_MODERN_BOLD.lineHeight(SECTION_TITLE_SIZE) + 8.0;
		page.canvas.line(page.left + page.width * 0.2, lineY, page.right - page.width * 0.2, lineY, SEPARATOR, 0.8);
		return lineY + SECTION_AFTER_GAP;
	}

	/**
	 * Draws a vertically centered section heading.
	 *
	 * @param page target page
	 * @param number section number text
	 * @param title section title
	 * @return next vertical cursor position
	 */
	private static double drawCenteredSectionPage(PageFrame page, String number, String title) {
		if (page.canvas == null) {
			return page.top + SECTION_AFTER_GAP;
		}
		double textHeight = Font.LATIN_MODERN_BOLD.lineHeight(SECTION_TITLE_SIZE);
		double y = page.top + Math.max(0.0, page.height * 0.36 - textHeight * 2.0);
		drawCentered(page.canvas, page.left, page.right, y,
				textStyle(Font.LATIN_MODERN_BOLD, SECTION_TITLE_SIZE, INK), number);
		drawCentered(page.canvas, page.left, page.right, y + textHeight + 2.0,
				textStyle(Font.LATIN_MODERN_BOLD, SECTION_TITLE_SIZE, INK), title);
		double lineY = y + textHeight * 2.0 + 10.0;
		page.canvas.line(page.left + page.width * 0.2, lineY, page.right - page.width * 0.2, lineY, SEPARATOR, 0.8);
		return lineY + SECTION_AFTER_GAP;
	}

	/**
	 * Renders a paragraph array across as many pages as required.
	 *
	 * @param state active layout state
	 * @param cursor active text cursor
	 * @param runningHeader section title for continuation-page headers
	 * @param paragraphs paragraphs to render
	 * @param centered whether paragraphs should be centered
	 * @return updated text cursor
	 */
	private static TextCursor renderParagraphs(LayoutState state, TextCursor cursor, String runningHeader,
			String[] paragraphs, boolean centered) {
		for (String paragraph : safeArray(paragraphs)) {
			cursor = renderParagraph(state, cursor, runningHeader, paragraph, centered);
		}
		return cursor;
	}

	/**
	 * Renders one paragraph, routing list-like source lines through the list
	 * renderer.
	 *
	 * @param state active layout state
	 * @param cursor active text cursor
	 * @param runningHeader section title for continuation-page headers
	 * @param paragraph source paragraph
	 * @param centered whether the paragraph should be centered
	 * @return updated text cursor
	 */
	private static TextCursor renderParagraph(LayoutState state, TextCursor cursor, String runningHeader,
			String paragraph, boolean centered) {
		if (centered || !containsListItem(paragraph)) {
			return renderPlainParagraph(state, cursor, runningHeader, paragraph, centered);
		}

		String[] sourceLines = normalizeWhitespacePreserveBreaks(paragraph).split("\n", -1);
		for (String sourceLine : sourceLines) {
			ListItem item = parseListItem(sourceLine);
			if (item == null) {
				cursor = renderPlainParagraph(state, cursor, runningHeader, sourceLine, false);
			} else {
				cursor = renderListItem(state, cursor, runningHeader, item);
			}
		}
		return cursor;
	}

	/**
	 * Renders one ordinary paragraph without list marker handling.
	 *
	 * @param state active layout state
	 * @param cursor active text cursor
	 * @param runningHeader section title for continuation-page headers
	 * @param paragraph source paragraph
	 * @param centered whether the paragraph should be centered
	 * @return updated text cursor
	 */
	private static TextCursor renderPlainParagraph(LayoutState state, TextCursor cursor, String runningHeader,
			String paragraph, boolean centered) {
		List<String> lines = wrapLines(paragraph, Font.LATIN_MODERN_ROMAN, BODY_SIZE,
				centered ? cursor.page.width * 0.82 : cursor.page.width);
		if (lines.isEmpty()) {
			cursor.y += PARAGRAPH_GAP;
			return cursor;
		}
		for (String line : lines) {
			cursor = ensureBodySpace(state, cursor, BODY_LEADING, runningHeader);
			if (cursor.page.canvas != null && !line.isBlank()) {
				if (centered) {
						drawCentered(cursor.page.canvas, cursor.page.left, cursor.page.right, cursor.y,
								textStyle(Font.LATIN_MODERN_ROMAN, BODY_SIZE, INK), line);
				} else {
					cursor.page.canvas.drawText(cursor.page.left, cursor.y, Font.LATIN_MODERN_ROMAN, BODY_SIZE, INK, line);
				}
			}
			cursor.y += BODY_LEADING;
		}
		cursor.y += PARAGRAPH_GAP;
		return cursor;
	}

	/**
	 * Renders one list item with a hanging indent.
	 *
	 * @param state active layout state
	 * @param cursor active text cursor
	 * @param runningHeader section title for continuation-page headers
	 * @param item parsed list item
	 * @return updated text cursor
	 */
	private static TextCursor renderListItem(LayoutState state, TextCursor cursor, String runningHeader,
			ListItem item) {
		double bodyOffset = listBodyOffset(item.marker);
		double bodyWidth = Math.max(20.0, cursor.page.width - bodyOffset);
		List<String> lines = wrapLines(item.text, Font.LATIN_MODERN_ROMAN, BODY_SIZE, bodyWidth);
		if (lines.isEmpty()) {
			cursor.y += LIST_ITEM_GAP;
			return cursor;
		}

		for (int i = 0; i < lines.size(); i++) {
			cursor = ensureBodySpace(state, cursor, BODY_LEADING, runningHeader);
			if (cursor.page.canvas != null && !lines.get(i).isBlank()) {
				double bodyX = cursor.page.left + bodyOffset;
				if (i == 0) {
					drawListMarker(cursor.page.canvas, cursor.page.left + LIST_MARKER_INDENT, cursor.y, item.marker);
				}
				drawListText(cursor.page.canvas, bodyX, cursor.y, lines.get(i), i == 0);
			}
			cursor.y += BODY_LEADING;
		}
		cursor.y += LIST_ITEM_GAP;
		return cursor;
	}

	/**
	 * Draws a list marker, using a filled vector circle for unordered items.
	 *
	 * @param canvas target canvas
	 * @param x marker left edge
	 * @param y text top edge
	 * @param marker marker text
	 */
	private static void drawListMarker(Canvas canvas, double x, double y, String marker) {
		if (UNORDERED_LIST_MARKER.equals(marker)) {
			double top = y + BODY_SIZE * 0.52 - LIST_BULLET_DIAMETER / 2.0;
			canvas.drawSvg("""
					<svg viewBox="0 0 10 10" xmlns="http://www.w3.org/2000/svg">
					  <circle cx="5" cy="5" r="4.4" fill="#202020"/>
					</svg>
					""", x, top, LIST_BULLET_DIAMETER, LIST_BULLET_DIAMETER);
			return;
		}
		canvas.drawText(x, y, Font.LATIN_MODERN_ROMAN, BODY_SIZE, INK, marker);
	}

	/**
	 * Draws list body text, using bold for a short label before the first colon.
	 *
	 * @param canvas target canvas
	 * @param x text left edge
	 * @param y text top edge
	 * @param text text to draw
	 * @param allowBoldLabel whether a leading label may be bolded
	 */
	private static void drawListText(Canvas canvas, double x, double y, String text, boolean allowBoldLabel) {
		int labelEnd = allowBoldLabel ? listLabelEnd(text) : -1;
		if (labelEnd < 0) {
			canvas.drawText(x, y, Font.LATIN_MODERN_ROMAN, BODY_SIZE, INK, text);
			return;
		}

		String label = text.substring(0, labelEnd + 1);
		String rest = text.substring(labelEnd + 1);
		canvas.drawTextPair(x, y, Font.LATIN_MODERN_BOLD, label, Font.LATIN_MODERN_ROMAN, rest, BODY_SIZE, INK);
	}

	/**
	 * Returns the list body offset for a marker.
	 *
	 * @param marker list marker
	 * @return body-text x offset relative to the page content left edge
	 */
	private static double listBodyOffset(String marker) {
		return Math.max(LIST_BODY_INDENT,
				LIST_MARKER_INDENT + textWidth(Font.LATIN_MODERN_ROMAN, BODY_SIZE, marker) + LIST_MARKER_GAP);
	}

	/**
	 * Returns the end index of a short list label before a colon.
	 *
	 * @param text source line
	 * @return colon index, or -1 when the line does not start with a label
	 */
	private static int listLabelEnd(String text) {
		int colon = text.indexOf(':');
		if (colon <= 0 || colon > 36) {
			return -1;
		}
		String label = text.substring(0, colon);
		for (int i = 0; i < label.length(); i++) {
			if (Character.isLetter(label.charAt(i))) {
				return colon;
			}
		}
		return -1;
	}

	/**
	 * Returns whether a paragraph contains at least one list-like source line.
	 *
	 * @param paragraph source paragraph
	 * @return true when a source line starts with bullet or numbering syntax
	 */
	private static boolean containsListItem(String paragraph) {
		for (String line : normalizeWhitespacePreserveBreaks(paragraph).split("\n", -1)) {
			if (parseListItem(line) != null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Parses one source line as a bullet or numbered list item.
	 *
	 * @param sourceLine source line
	 * @return parsed list item, or null when the line is not list syntax
	 */
	private static ListItem parseListItem(String sourceLine) {
		String line = blankTo(sourceLine, "").trim();
		if (line.length() > 2 && (line.startsWith("- ") || line.startsWith("* "))) {
			return new ListItem(UNORDERED_LIST_MARKER, line.substring(2));
		}
		if (line.length() > 2 && line.startsWith("\u2022 ")) {
			return new ListItem(UNORDERED_LIST_MARKER, line.substring(2));
		}

		Matcher matcher = NUMBERED_LIST_ITEM.matcher(line);
		if (matcher.matches()) {
			return new ListItem(matcher.group(1), matcher.group(2));
		}
		return null;
	}

	/**
	 * Ensures the cursor has room for another body line, creating a continuation
	 * page when necessary.
	 *
	 * @param state active layout state
	 * @param cursor active text cursor
	 * @param requiredHeight height required for the next draw
	 * @param runningHeader section title for continuation-page headers
	 * @return updated cursor
	 */
	private static TextCursor ensureBodySpace(LayoutState state, TextCursor cursor, double requiredHeight,
			String runningHeader) {
		if (cursor.y + requiredHeight <= cursor.page.bottom) {
			return cursor;
		}
		PageFrame next = addPage(state, PageStyle.BODY, runningHeader);
		return new TextCursor(next, next.top);
	}

	/**
	 * Creates a new physical page and draws its header/footer chrome.
	 *
	 * @param state active layout state
	 * @param style page style to apply
	 * @param oddHeaderText text used on odd-page running headers
	 * @return created page frame
	 */
	private static PageFrame addPage(LayoutState state, PageStyle style, String oddHeaderText) {
		state.pageNumber++;
		boolean odd = (state.pageNumber & 1) == 1;
		double leftMargin = odd ? state.innerMargin : state.outerMargin;
		double rightMargin = odd ? state.outerMargin : state.innerMargin;
		double top = state.topMargin;
		double bottom = state.pageSize.getHeight() - state.bottomMargin;

		Page page = state.document == null ? null : state.document.addPage(state.pageSize);
		Canvas canvas = page == null ? null : page.canvas();
		PageFrame frame = new PageFrame(state.pageNumber, canvas, leftMargin, state.pageSize.getWidth() - rightMargin,
				top, bottom);
		state.pageFrames.add(frame);

		if (canvas != null) {
			drawPageChrome(state.book, frame, style, oddHeaderText);
		}
		return frame;
	}

	/**
	 * Draws headers and footers for one page frame.
	 *
	 * @param book source book
	 * @param frame target frame
	 * @param style page style
	 * @param oddHeaderText text used on odd pages
	 */
	private static void drawPageChrome(Book book, PageFrame frame, PageStyle style, String oddHeaderText) {
		if (frame.canvas == null || style == PageStyle.HIDDEN) {
			return;
		}

		boolean odd = (frame.pageNumber & 1) == 1;
		double footerY = frame.bottom + Math.max(10.0, (Book.cmToPoints(book.getBottomMarginCm()) - FOOTER_SIZE) / 2.0);
		String pageText = Integer.toString(frame.pageNumber);
		if (odd) {
			double pageWidth = textWidth(Font.LATIN_MODERN_ROMAN, FOOTER_SIZE, pageText);
			frame.canvas.drawText(frame.right - pageWidth, footerY, Font.LATIN_MODERN_ROMAN, FOOTER_SIZE, INK, pageText);
		} else {
			frame.canvas.drawText(frame.left, footerY, Font.LATIN_MODERN_ROMAN, FOOTER_SIZE, INK, pageText);
		}

		if (style != PageStyle.BODY) {
			return;
		}

		double headerY = Math.max(8.0, frame.top - HEADER_BODY_TOP_OFFSET);
		if (odd) {
			String text = normalizeHeader(oddHeaderText);
			double width = textWidth(Font.LATIN_MODERN_ROMAN, HEADER_SIZE, text);
			frame.canvas.drawText(frame.right - width, headerY, Font.LATIN_MODERN_ROMAN, HEADER_SIZE, INK, text);
		} else {
			frame.canvas.drawText(frame.left, headerY, Font.LATIN_MODERN_ROMAN, HEADER_SIZE, INK,
					normalizeHeader(book.getFullTitle()));
		}
	}

	/**
	 * Normalizes running-header text.
	 *
	 * @param text source text
	 * @return normalized header text
	 */
	private static String normalizeHeader(String text) {
		return blankTo(text, "").toUpperCase(Locale.ROOT);
	}

	/**
	 * Ensures the next content page starts on an odd page number.
	 *
	 * @param state active layout state
	 */
	private static void clearDoublePage(LayoutState state) {
		if ((state.pageNumber & 1) == 1) {
			addPage(state, PageStyle.HIDDEN, "");
		}
	}

	/**
	 * Ensures the next content page starts on an odd page number using a body-style
	 * inserted page when one is needed.
	 *
	 * @param state active layout state
	 * @param oddHeaderText text used on odd-page running headers
	 */
	private static void clearDoubleBodyPage(LayoutState state, String oddHeaderText) {
		if ((state.pageNumber & 1) == 1) {
			addPage(state, PageStyle.BODY, oddHeaderText);
		}
	}

	/**
	 * Ensures the next content page starts on an even page number.
	 *
	 * @param state active layout state
	 */
	private static void clearEvenPage(LayoutState state) {
		if ((state.pageNumber & 1) == 0) {
			addPage(state, PageStyle.HIDDEN, "");
		}
	}

	/**
	 * Ensures the next content page starts on an even page number using a
	 * body-style inserted page when one is needed.
	 *
	 * @param state active layout state
	 * @param oddHeaderText text used on odd-page running headers
	 */
	private static void clearEvenBodyPage(LayoutState state, String oddHeaderText) {
		if ((state.pageNumber & 1) == 0) {
			addPage(state, PageStyle.BODY, oddHeaderText);
		}
	}

	/**
	 * Renders one puzzle board as SVG.
	 *
	 * @param element source puzzle element
	 * @return SVG board markup
	 */
	private static String renderPuzzleSvg(Element element) {
		Position position = new Position(element.getPosition());
		return new Render()
				.setPosition(position)
				.setWhiteSideDown(true)
				.setShowBorder(true)
				.renderSvg(BOARD_PIXELS, BOARD_PIXELS);
	}

	/**
	 * Renders one solution board as SVG.
	 *
	 * @param solution parsed solution description
	 * @return SVG board markup
	 */
	private static String renderSolutionSvg(SolutionInfo solution) {
		Render render = new Render()
				.setPosition(solution.result.copy())
				.setWhiteSideDown(true)
				.setShowBorder(true)
				.setShowSpecialMoveHints(false);
		if (solution.lastMove != Move.NO_MOVE) {
			render.addArrow(solution.lastMove);
		}
		return brightenSolutionBoard(render.renderSvg(BOARD_PIXELS, BOARD_PIXELS));
	}

	/**
	 * Brightens only the board colors in a solution-board SVG.
	 *
	 * <p>
	 * This applies a brightness and contrast operation to the board image before
	 * pieces and arrows are drawn.
	 * Piece fills are intentionally left untouched.
	 * </p>
	 *
	 * @param svg source solution SVG
	 * @return SVG with only board-square colors brightened
	 */
	private static String brightenSolutionBoard(String svg) {
		return svg
				.replace(BOARD_GRID_FILL, SOLUTION_BOARD_GRID_FILL)
				.replace(BOARD_LIGHT_FILL, SOLUTION_BOARD_LIGHT_FILL)
				.replace(BOARD_DARK_FILL, SOLUTION_BOARD_DARK_FILL);
	}

	/**
	 * Computes the page-preview aspect ratio including border and shadow
	 * padding.
	 *
	 * @param book source book settings
	 * @return preview height divided by preview width
	 */
	private static double pagePreviewAspectRatio(Book book) {
		PagePreviewGeometry geometry = pagePreviewGeometry(book);
		return geometry.totalHeight() / geometry.totalWidth();
	}

	/**
	 * Renders one special-rights example board as vector SVG.
	 *
	 * @param fen source FEN
	 * @param enPassant whether to draw en-passant arrows
	 * @param castling whether to draw castling-right arrows
	 * @return rendered board SVG
	 */
	private static String renderSpecialRightsSvg(String fen, boolean enPassant, boolean castling) {
		Position position = new Position(fen);
		Render render = new Render()
				.setPosition(position)
				.setWhiteSideDown(true)
				.setShowBorder(true)
				.setShowSpecialMoveHints(false);
		if (enPassant) {
			render.addEnPassant(position);
		}
		if (castling) {
			render.addCastlingRights(position);
		}
		return render.renderSvg(BOARD_PIXELS, BOARD_PIXELS);
	}

	/**
	 * Appends free-edition watermark overlays after all normal page content has been
	 * drawn.
	 *
	 * @param state final render state
	 */
	private static void applyFreeEditionWatermark(LayoutState state) {
		for (PageFrame frame : state.pageFrames) {
			if (frame.canvas == null) {
				continue;
			}
			drawFreeEditionWatermark(state, frame);
		}
	}

	/**
	 * Draws the noisy anti-print watermark for one page.
	 *
	 * @param state final render state
	 * @param frame target page frame
	 */
	private static void drawFreeEditionWatermark(LayoutState state, PageFrame frame) {
		double width = state.pageSize.getWidth();
		double height = state.pageSize.getHeight();
		frame.canvas.drawSvg(renderFreeWatermarkNoiseSvg(width, height, frame.pageNumber, state.book.getFullTitle()),
				0.0, 0.0, width, height);
		drawRepeatedFreeWatermarkText(frame.canvas, width, height, state.watermarkId);
		drawPrimaryFreeWatermarkText(frame.canvas, width, height, state.watermarkId, frame.pageNumber,
				state.pageFrames.size());
		drawEdgeFreeWatermarkText(frame.canvas, width, height, state.watermarkId, frame.pageNumber,
				state.pageFrames.size());
		frame.canvas.drawSvg(renderFreeWatermarkCornerSvg(width, height, frame.pageNumber, state.watermarkId),
				0.0, 0.0, width, height);
		frame.canvas.drawSvg(renderFreeWatermarkScratchSvg(width, height, frame.pageNumber, state.book.getFullTitle()),
				0.0, 0.0, width, height);
	}

	/**
	 * Draws repeated low-opacity text across the page.
	 *
	 * @param canvas target canvas
	 * @param width page width
	 * @param height page height
	 * @param watermarkId visible watermark identifier
	 */
	private static void drawRepeatedFreeWatermarkText(Canvas canvas, double width, double height,
			String watermarkId) {
		String repeated = FREE_WATERMARK_REPEAT + " - " + WriterSupport.normalizeWatermarkId(watermarkId);
		double fontSize = Math.max(5.0, Math.min(6.8, width / 88.0));
		double textWidth = textWidth(Font.LATIN_MODERN_BOLD, fontSize, repeated);
		double stepX = Math.max(104.0, textWidth + 30.0);
		double stepY = Math.max(FREE_WATERMARK_REPEAT_STEP, fontSize * 6.3);
		double diagonal = Math.hypot(width, height);
		double minX = width / 2.0 - diagonal - textWidth;
		double maxX = width / 2.0 + diagonal;
		double minY = height / 2.0 - diagonal;
		double maxY = height / 2.0 + diagonal;
		int row = 0;
		for (double y = minY; y <= maxY; y += stepY) {
			double rowOffset = (row & 1) == 0 ? 0.0 : stepX * 0.5;
			for (double x = minX - rowOffset; x <= maxX; x += stepX) {
				canvas.drawTextRotatedEncoded(x, y, FREE_WATERMARK_ANGLE, width / 2.0, height / 2.0,
						Font.LATIN_MODERN_BOLD, fontSize, FREE_WATERMARK_SMALL, repeated);
			}
			row++;
		}
	}

	/**
	 * Draws the main free-edition label and edge notices.
	 *
	 * @param canvas target canvas
	 * @param width page width
	 * @param height page height
	 * @param watermarkId visible watermark identifier
	 * @param pageNumber one-based page number
	 * @param totalPages total page count
	 */
	private static void drawPrimaryFreeWatermarkText(Canvas canvas, double width, double height, String watermarkId,
			int pageNumber, int totalPages) {
		double margin = Math.max(36.0, width * 0.07);
		double mainSize = fittedRotatedFontSize(Font.LATIN_MODERN_BOLD, FREE_WATERMARK_MAIN,
				Math.max(24.0, Math.min(46.0, width * 0.078)), 22.0, width, height, margin);
		drawCenteredRotatedTextOutline(canvas, width / 2.0, height * 0.43, FREE_WATERMARK_ANGLE,
				textStyle(Font.LATIN_MODERN_BOLD, mainSize, FREE_WATERMARK_LARGE), FREE_WATERMARK_MAIN);
		drawCenteredRotatedTextOutline(canvas, width / 2.0, height * 0.51, FREE_WATERMARK_ANGLE,
				textStyle(Font.LATIN_MODERN_BOLD, Math.max(13.0, mainSize * 0.36), FREE_WATERMARK_LARGE),
				"PRINTING / RESALE NOT ALLOWED");
		drawCenteredRotatedTextOutline(canvas, width / 2.0, height * 0.58, FREE_WATERMARK_ANGLE,
				textStyle(Font.LATIN_MODERN_BOLD, Math.max(10.5, mainSize * 0.25), FREE_WATERMARK_LARGE),
				"UNAUTHORIZED REDISTRIBUTION NOT ALLOWED");
		drawCenteredRotatedTextOutline(canvas, width / 2.0, height * 0.645, FREE_WATERMARK_ANGLE,
				textStyle(Font.LATIN_MODERN_BOLD, Math.max(6.6, mainSize * 0.16), FREE_WATERMARK_ID),
				watermarkPageLabel(watermarkId, pageNumber, totalPages));
	}

	/**
	 * Draws top, bottom, and side notices with the page-specific watermark label.
	 *
	 * @param canvas target canvas
	 * @param width page width
	 * @param height page height
	 * @param watermarkId visible watermark identifier
	 * @param pageNumber one-based page number
	 * @param totalPages total page count
	 */
	private static void drawEdgeFreeWatermarkText(Canvas canvas, double width, double height, String watermarkId,
			int pageNumber, int totalPages) {
		String pageLabel = watermarkPageLabel(watermarkId, pageNumber, totalPages);
		String notice = "FREE ELECTRONIC COPY - NO PRINTING, RESALE, OR UNAUTHORIZED REDISTRIBUTION | " + pageLabel;
		double noticeSize = fittedFontSize(Font.LATIN_MODERN_BOLD, notice, Math.max(6.2, Math.min(8.0, width / 80.0)),
				5.1, width - 32.0);
		String fittedNotice = fitText(notice, Font.LATIN_MODERN_BOLD, noticeSize, width - 32.0);
		canvas.drawTextOutline(16.0, 10.0, Font.LATIN_MODERN_BOLD, noticeSize, FREE_WATERMARK_NOTICE, fittedNotice);
		canvas.drawTextOutline(16.0, height - noticeSize - 12.0, Font.LATIN_MODERN_BOLD, noticeSize,
				FREE_WATERMARK_NOTICE, fittedNotice);

		String sideNotice = fitText("FREE ELECTRONIC COPY | " + pageLabel, Font.LATIN_MODERN_BOLD, 6.6,
				height - 48.0);
		double sideSize = fittedFontSize(Font.LATIN_MODERN_BOLD, sideNotice, 6.6, 4.8, height - 48.0);
		drawCenteredRotatedTextOutline(canvas, 14.0, height / 2.0, -90.0,
				textStyle(Font.LATIN_MODERN_BOLD, sideSize, FREE_WATERMARK_NOTICE), sideNotice);
		drawCenteredRotatedTextOutline(canvas, width - 14.0, height / 2.0, 90.0,
				textStyle(Font.LATIN_MODERN_BOLD, sideSize, FREE_WATERMARK_NOTICE), sideNotice);
	}

	/**
	 * Builds the visible per-page watermark label.
	 *
	 * @param watermarkId document watermark identifier
	 * @param pageNumber one-based page number
	 * @param totalPages total page count
	 * @return page-specific label
	 */
	private static String watermarkPageLabel(String watermarkId, int pageNumber, int totalPages) {
		return "WATERMARK ID " + WriterSupport.normalizeWatermarkId(watermarkId)
				+ " - PAGE " + pageNumber + " OF " + Math.max(pageNumber, totalPages);
	}

	/**
	 * Draws vector-outline text centered around a point and then rotated.
	 *
	 * @param canvas target canvas
	 * @param centerX target center x
	 * @param centerY target center y
	 * @param angleDegrees rotation angle
	 * @param style text style
	 * @param text text to draw
	 */
	private static void drawCenteredRotatedTextOutline(Canvas canvas, double centerX, double centerY,
			double angleDegrees, TextStyle style, String text) {
		double width = textWidth(style.font(), style.size(), text);
		canvas.drawTextOutlineRotated(centerX - width / 2.0, centerY - style.size() / 2.0, angleDegrees, centerX,
				centerY, style.font(), style.size(), style.color(), text);
	}

	/**
	 * Fits rotated watermark text so the important label stays inside the page.
	 *
	 * @param font font to measure
	 * @param text text to fit
	 * @param preferredSize starting font size
	 * @param minimumSize minimum accepted font size
	 * @param pageWidth page width
	 * @param pageHeight page height
	 * @param margin required clear margin
	 * @return fitted font size
	 */
	private static double fittedRotatedFontSize(Font font, String text, double preferredSize, double minimumSize,
			double pageWidth, double pageHeight, double margin) {
		double size = Math.max(minimumSize, preferredSize);
		while (size > minimumSize
				&& !rotatedTextFits(font, text, size, pageWidth - margin * 2.0, pageHeight - margin * 2.0)) {
			size *= 0.94;
		}
		return Math.max(minimumSize, size);
	}

	/**
	 * Fits one unrotated text line to the available width.
	 *
	 * @param font font to measure
	 * @param text text to fit
	 * @param preferredSize starting font size
	 * @param minimumSize minimum accepted font size
	 * @param maxWidth available width
	 * @return fitted font size
	 */
	private static double fittedFontSize(Font font, String text, double preferredSize, double minimumSize,
			double maxWidth) {
		double size = Math.max(minimumSize, preferredSize);
		while (size > minimumSize && textWidth(font, size, text) > maxWidth) {
			size *= 0.94;
		}
		return Math.max(minimumSize, size);
	}

	/**
	 * Returns whether a rotated text bounding box fits inside the supplied area.
	 *
	 * @param font font to measure
	 * @param text text to fit
	 * @param fontSize font size
	 * @param maxWidth available width
	 * @param maxHeight available height
	 * @return true when the rotated text fits
	 */
	private static boolean rotatedTextFits(Font font, String text, double fontSize, double maxWidth,
			double maxHeight) {
		double radians = Math.toRadians(FREE_WATERMARK_ANGLE);
		double cos = Math.abs(Math.cos(radians));
		double sin = Math.abs(Math.sin(radians));
		double textWidth = textWidth(font, fontSize, text);
		double textHeight = font.lineHeight(fontSize);
		return textWidth * cos + textHeight * sin <= maxWidth
				&& textWidth * sin + textHeight * cos <= maxHeight;
	}

	/**
	 * Builds low-opacity vector speckle noise for a free-edition page.
	 *
	 * @param width page width
	 * @param height page height
	 * @param pageNumber page number
	 * @param documentKey document key used to vary the pattern between books
	 * @return SVG noise overlay
	 */
	private static String renderFreeWatermarkNoiseSvg(double width, double height, int pageNumber,
			String documentKey) {
		double areaScale = Math.max(0.45, width * height / (PageSize.A4.getWidth() * PageSize.A4.getHeight()));
		int dots = Math.max(48, (int) Math.round(FREE_WATERMARK_DOTS_A4 * areaScale));
		Random random = freeWatermarkRandom(pageNumber, documentKey, 0x51L);
		StringBuilder svg = openSvg(width, height, 4096 + dots * 96);
		for (int i = 0; i < dots; i++) {
			double x = random.nextDouble() * width;
			double y = random.nextDouble() * height;
			double radius = 0.12 + random.nextDouble() * 0.34;
			int gray = 58 + random.nextInt(68);
			appendCircle(svg, x, y, radius, new Color(gray, gray, gray), 0.028);
		}
		return closeSvg(svg);
	}

	/**
	 * Builds small page-specific corner marks that survive cropping and make page
	 * screenshots easier to trace.
	 *
	 * @param width page width
	 * @param height page height
	 * @param pageNumber page number
	 * @param watermarkId visible watermark identifier
	 * @return SVG corner-mark overlay
	 */
	private static String renderFreeWatermarkCornerSvg(double width, double height, int pageNumber,
			String watermarkId) {
		StringBuilder svg = openSvg(width, height, 2048);
		Random random = freeWatermarkRandom(pageNumber, watermarkId, 0xd5L);
		double cell = Math.max(2.0, Math.min(3.2, width / 190.0));
		double pitch = cell * 1.45;
		double side = pitch * 7.0;
		double margin = Math.max(14.0, width * 0.025);
		appendCornerMark(svg, margin, margin, cell, pitch, random);
		appendCornerMark(svg, width - margin - side, margin, cell, pitch, random);
		appendCornerMark(svg, margin, height - margin - side, cell, pitch, random);
		appendCornerMark(svg, width - margin - side, height - margin - side, cell, pitch, random);
		return closeSvg(svg);
	}

	/**
	 * Appends one small deterministic corner mark.
	 *
	 * @param svg target SVG builder
	 * @param left left edge
	 * @param top top edge
	 * @param cell filled square size
	 * @param pitch grid pitch
	 * @param random deterministic source
	 */
	private static void appendCornerMark(StringBuilder svg, double left, double top, double cell, double pitch,
			Random random) {
		Color color = new Color(36, 36, 36);
		for (int row = 0; row < 7; row++) {
			for (int col = 0; col < 7; col++) {
				boolean anchor = row == 0 || col == 0 || row == 6 || col == 6;
				if (anchor || random.nextBoolean()) {
					appendRect(svg, left + col * pitch, top + row * pitch, cell, cell, color, anchor ? 0.20 : 0.13);
				}
			}
		}
	}

	/**
	 * Builds short low-opacity scratches and diagonal hatching for a free-edition page.
	 *
	 * @param width page width
	 * @param height page height
	 * @param pageNumber page number
	 * @param documentKey document key used to vary the pattern between books
	 * @return SVG scratch overlay
	 */
	private static String renderFreeWatermarkScratchSvg(double width, double height, int pageNumber,
			String documentKey) {
		double areaScale = Math.max(0.45, width * height / (PageSize.A4.getWidth() * PageSize.A4.getHeight()));
		int scratches = Math.max(12, (int) Math.round(FREE_WATERMARK_SCRATCHES_A4 * areaScale));
		Random random = freeWatermarkRandom(pageNumber, documentKey, 0xa7L);
		StringBuilder svg = openSvg(width, height, 4096 + scratches * 128);

		for (double y = -height * 0.40; y < height * 1.30; y += 52.0) {
			appendLine(svg, 0.0, y, width, y + width * 0.70, new SvgStroke(new Color(55, 55, 55), 0.014, 0.18));
		}
		for (double y = -height * 0.25; y < height * 1.20; y += 104.0) {
			appendLine(svg, width, y, 0.0, y + width * 0.52, new SvgStroke(new Color(55, 55, 55), 0.009, 0.15));
		}

		for (int i = 0; i < scratches; i++) {
			double x = random.nextDouble() * width;
			double y = random.nextDouble() * height;
			double length = 6.0 + random.nextDouble() * 28.0;
			double angle = random.nextDouble() * Math.PI;
			double x2 = x + Math.cos(angle) * length;
			double y2 = y + Math.sin(angle) * length;
			int gray = 34 + random.nextInt(95);
			appendLine(svg, x, y, x2, y2,
					new SvgStroke(new Color(gray, gray, gray), 0.026, 0.20 + random.nextDouble() * 0.30));
		}
		return closeSvg(svg);
	}

	/**
	 * Creates a deterministic pseudo-random generator for one watermark layer.
	 *
	 * @param pageNumber page number
	 * @param documentKey document key used to vary the pattern between books
	 * @param salt layer salt
	 * @return seeded random generator
	 */
	private static Random freeWatermarkRandom(int pageNumber, String documentKey, long salt) {
		long key = blankTo(documentKey, "").hashCode();
		long seed = FREE_WATERMARK_SEED ^ (pageNumber * 0x9E3779B97F4A7C15L) ^ (key << 21) ^ salt;
		return new Random(seed);
	}

	/**
	 * Draws a captioned page preview using only vector SVG and PDF primitives.
	 *
	 * @param canvas target canvas
	 * @param box content box
	 * @param caption caption text
	 * @param state active layout state
	 * @param solutions whether to render solution boards instead of puzzle boards
	 */
	private static void drawCaptionedPagePreview(Canvas canvas, Box box, String caption, LayoutState state,
			boolean solutions) {
		PagePreviewGeometry geometry = pagePreviewGeometry(state.book);
		canvas.drawSvg(renderPagePreviewFrameSvg(geometry), box.x(), box.y(), box.width(), box.height());

		Element[] elements = state.book.getElements();
		if (elements.length > 0) {
			double scaleX = box.width() / geometry.totalWidth();
			double scaleY = box.height() / geometry.totalHeight();
			int capacity = geometry.rows() * geometry.columns();
			for (int local = 0; local < capacity; local++) {
				int row = local / geometry.columns();
				int column = local % geometry.columns();
				int index = local % elements.length;
				double boardX = box.x() + (geometry.boardStartX()
						+ column * (geometry.boardSize() + geometry.horizontalGap())) * scaleX;
				double boardY = box.y() + (geometry.boardStartY()
						+ row * (geometry.boardSize() + geometry.verticalGap())) * scaleY;
				String boardSvg = solutions
						? renderSolutionSvg(state.solutions[index])
						: renderPuzzleSvg(elements[index]);
				canvas.drawSvg(boardSvg, boardX, boardY, geometry.boardSize() * scaleX, geometry.boardSize() * scaleY);
			}
		}

		drawCenteredWrapped(canvas, new TextBlock(box.x(), box.y() + box.height() + CAPTION_GAP, box.width()),
				textStyle(Font.LATIN_MODERN_ITALIC, CAPTION_SIZE, INK), CAPTION_LEADING, caption, CAPTION_MAX_LINES);
	}

	/**
	 * Draws a captioned board image using a vector shadow and an SVG board.
	 *
	 * @param canvas target canvas
	 * @param x left edge
	 * @param y top edge
	 * @param size board size
	 * @param caption caption text
	 * @param svg board SVG
	 */
	private static void drawCaptionedBoardSvg(Canvas canvas, double x, double y, double size, String caption,
			String svg) {
		ShadowBoxGeometry geometry = shadowBoxGeometry(size, size, EXAMPLE_BOARD_SHADOW_FRACTION);
		canvas.drawSvg(renderShadowSvg(geometry), x, y, geometry.totalWidth(), geometry.totalHeight());
		canvas.drawSvg(svg, x + geometry.blur(), y + geometry.blur(), size, size);
		drawCenteredWrapped(canvas, new TextBlock(x, y + geometry.totalHeight() + CAPTION_GAP, geometry.totalWidth()),
				textStyle(Font.LATIN_MODERN_ITALIC, CAPTION_SIZE, INK), CAPTION_LEADING, caption, CAPTION_MAX_LINES);
	}

	/**
	 * Computes the native geometry used by page-preview thumbnails.
	 *
	 * @param book source book settings
	 * @return preview geometry
	 */
	private static PagePreviewGeometry pagePreviewGeometry(Book book) {
		int columns = Math.max(1, book.getPuzzleColumns());
		int rows = Math.max(1, book.getPuzzleRows());
		double pageWidth = PAGE_PREVIEW_WIDTH;
		double pageHeight = pageWidth * book.getPaperHeightCm() / book.getPaperWidthCm();
		double border = Math.max(1.0, pageWidth * PAGE_PREVIEW_BORDER_FRACTION);
		double framedWidth = pageWidth + border * 2.0;
		double framedHeight = pageHeight + border * 2.0;
		ShadowBoxGeometry shadow = shadowBoxGeometry(framedWidth, framedHeight, PAGE_PREVIEW_SHADOW_FRACTION);
		double percentBoardWidth = Math.floor((1.0 / columns - Math.log(columns) / 100.0) * 100.0) / 100.0;
		percentBoardWidth = Math.max(0.05, Math.min(0.95, percentBoardWidth));
		double left = book.getInnerMarginCm() / book.getPaperWidthCm() * pageWidth;
		double top = book.getTopMarginCm() / book.getPaperHeightCm() * pageHeight;
		double bottom = book.getBottomMarginCm() / book.getPaperHeightCm() * pageHeight;
		double usableWidth = pageWidth - left * 2.0;
		double usableHeight = pageHeight - top - bottom;
		double boardSize = usableWidth * percentBoardWidth;
		double horizontalGap = Math.max(0.0, (usableWidth - boardSize * columns) / columns);
		double verticalGap = Math.max(0.0, (usableHeight - boardSize * rows) / rows);
		double boardStartX = shadow.blur() + border + left;
		double boardStartY = shadow.blur() + border + top;
		return new PagePreviewGeometry(shadow.totalWidth(), shadow.totalHeight(), framedWidth, framedHeight, border,
				pageWidth, pageHeight, boardStartX, boardStartY, boardSize, horizontalGap, verticalGap, rows, columns,
				shadow.blur());
	}

	/**
	 * Builds the vector frame and shadow used behind one page preview.
	 *
	 * @param geometry preview geometry
	 * @return frame SVG
	 */
	private static String renderPagePreviewFrameSvg(PagePreviewGeometry geometry) {
		StringBuilder svg = openSvg(geometry.totalWidth(), geometry.totalHeight(), 768);
		appendShadowLayers(svg, geometry.framedWidth(), geometry.framedHeight(), geometry.shadowBlur());
		double x = geometry.shadowBlur();
		double y = geometry.shadowBlur();
		appendRect(svg, x, y, geometry.framedWidth(), geometry.framedHeight(), PAGE_PREVIEW_BORDER_COLOR, 1.0);
		appendRect(svg, x + geometry.border(), y + geometry.border(), geometry.pageWidth(), geometry.pageHeight(),
				Color.WHITE, 1.0);
		return closeSvg(svg);
	}

	/**
	 * Computes the shadow box reserved around a vector figure.
	 *
	 * @param contentWidth inner content width
	 * @param contentHeight inner content height
	 * @param shadowFraction shadow size relative to the content width
	 * @return shadow geometry
	 */
	private static ShadowBoxGeometry shadowBoxGeometry(double contentWidth, double contentHeight,
			double shadowFraction) {
		double blur = Math.max(1.0, contentWidth * shadowFraction);
		double shadowReserve = blur * PAGE_SHADOW_RESERVE_SCALE;
		return new ShadowBoxGeometry(contentWidth + shadowReserve, contentHeight + shadowReserve, blur);
	}

	/**
	 * Builds a soft vector drop shadow sized for the supplied content box.
	 *
	 * @param geometry shadow geometry
	 * @return shadow-only SVG
	 */
	private static String renderShadowSvg(ShadowBoxGeometry geometry) {
		StringBuilder svg = openSvg(geometry.totalWidth(), geometry.totalHeight(), 512);
		appendShadowLayers(svg, geometry.totalWidth() - geometry.blur() * PAGE_SHADOW_RESERVE_SCALE,
				geometry.totalHeight() - geometry.blur() * PAGE_SHADOW_RESERVE_SCALE, geometry.blur());
		return closeSvg(svg);
	}

	/**
	 * Appends lower-right shadow gradients while staying fully vector.
	 *
	 * <p>
	 * The visible SVG is split into exposed right, bottom, and corner regions and
	 * painted with transparent SVG gradients instead of a rasterized PNG shadow.
	 * </p>
	 *
	 * @param svg target SVG builder
	 * @param contentWidth width of the shadowed content
	 * @param contentHeight height of the shadowed content
	 * @param blur nominal blur radius
	 */
	private static void appendShadowLayers(StringBuilder svg, double contentWidth, double contentHeight, double blur) {
		double contentX = blur;
		double contentY = blur;
		double right = contentX + contentWidth;
		double bottom = contentY + contentHeight;
		String prefix = "page-shadow";
		appendShadowGradientDefs(svg, prefix, contentX, contentY, right, bottom, blur);
		appendUrlRect(svg, right, contentY + blur, blur, Math.max(0.1, contentHeight - blur),
				prefix + "-right");
		appendUrlRect(svg, contentX + blur, bottom, Math.max(0.1, contentWidth - blur), blur,
				prefix + "-bottom");
		appendUrlRect(svg, right, bottom, blur, blur, prefix + "-corner");
		appendUrlRect(svg, right, contentY, blur, blur, prefix + "-top-right");
		appendUrlRect(svg, contentX, bottom, blur, blur, prefix + "-bottom-left");
	}

	/**
	 * Appends the SVG gradients used by the page-preview shadow.
	 *
	 * @param svg target SVG builder
	 * @param prefix id prefix
	 * @param contentX content left edge
	 * @param contentY content top edge
	 * @param right content right edge
	 * @param bottom content bottom edge
	 * @param blur shadow blur/offset size
	 */
	private static void appendShadowGradientDefs(StringBuilder svg, String prefix, double contentX, double contentY,
			double right, double bottom, double blur) {
		svg.append("  <defs>\n");
		appendLinearGradient(svg, prefix + "-right", right, contentY, right + blur, contentY);
		appendLinearGradient(svg, prefix + "-bottom", contentX, bottom, contentX, bottom + blur);
		appendRadialGradient(svg, prefix + "-corner", right, bottom, blur);
		appendRadialGradient(svg, prefix + "-top-right", right, contentY + blur, blur);
		appendRadialGradient(svg, prefix + "-bottom-left", contentX + blur, bottom, blur);
		svg.append("  </defs>\n");
	}

	/**
	 * Appends one black-to-transparent linear shadow gradient.
	 *
	 * @param svg target SVG builder
	 * @param id gradient id
	 * @param x1 start x
	 * @param y1 start y
	 * @param x2 end x
	 * @param y2 end y
	 */
	private static void appendLinearGradient(StringBuilder svg, String id, double x1, double y1, double x2,
			double y2) {
		svg.append("    <linearGradient id=\"").append(id)
				.append("\" gradientUnits=\"userSpaceOnUse\" x1=\"").append(svgNumber(x1))
				.append("\" y1=\"").append(svgNumber(y1))
				.append("\" x2=\"").append(svgNumber(x2))
				.append("\" y2=\"").append(svgNumber(y2)).append(SVG_TAG_END);
		appendShadowStops(svg);
		svg.append("    </linearGradient>\n");
	}

	/**
	 * Appends one black-to-transparent radial shadow gradient.
	 *
	 * @param svg target SVG builder
	 * @param id gradient id
	 * @param cx center x
	 * @param cy center y
	 * @param radius radius
	 */
	private static void appendRadialGradient(StringBuilder svg, String id, double cx, double cy, double radius) {
		svg.append("    <radialGradient id=\"").append(id)
				.append("\" gradientUnits=\"userSpaceOnUse\" cx=\"").append(svgNumber(cx))
				.append("\" cy=\"").append(svgNumber(cy))
				.append("\" r=\"").append(svgNumber(radius)).append(SVG_TAG_END);
		appendShadowStops(svg);
		svg.append("    </radialGradient>\n");
	}

	/**
	 * Appends the page-preview shadow gradient stops.
	 *
	 * @param svg target SVG builder
	 */
	private static void appendShadowStops(StringBuilder svg) {
		svg.append("      <stop offset=\"0\" stop-color=\"#000000\" stop-opacity=\"")
				.append(svgNumber(PAGE_SHADOW_CONTACT_OPACITY)).append("\"/>\n");
		svg.append("      <stop offset=\"1\" stop-color=\"#000000\" stop-opacity=\"0\"/>\n");
	}

	/**
	 * Appends a rectangle filled by a local SVG gradient.
	 *
	 * @param svg target SVG builder
	 * @param x left edge
	 * @param y top edge
	 * @param width width
	 * @param height height
	 * @param gradientId gradient id
	 */
	private static void appendUrlRect(StringBuilder svg, double x, double y, double width, double height,
			String gradientId) {
		svg.append("  <rect x=\"").append(svgNumber(x))
				.append("\" y=\"").append(svgNumber(y))
				.append("\" width=\"").append(svgNumber(width))
				.append("\" height=\"").append(svgNumber(height))
				.append("\" fill=\"url(#").append(gradientId).append(")\"/>\n");
	}

	/**
	 * Opens a simple SVG document builder.
	 *
	 * @param width native view-box width
	 * @param height native view-box height
	 * @param capacity initial builder capacity
	 * @return initialized SVG builder
	 */
	private static StringBuilder openSvg(double width, double height, int capacity) {
		StringBuilder svg = new StringBuilder(capacity);
		svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
				.append(svgNumber(width)).append(' ').append(svgNumber(height)).append(SVG_TAG_END);
		return svg;
	}

	/**
	 * Closes an SVG document builder and returns the final markup.
	 *
	 * @param svg source builder
	 * @return finalized SVG text
	 */
	private static String closeSvg(StringBuilder svg) {
		svg.append("</svg>\n");
		return svg.toString();
	}

	/**
	 * Appends one filled rectangle to an SVG builder.
	 *
	 * @param svg target SVG builder
	 * @param x left edge
	 * @param y top edge
	 * @param width rectangle width
	 * @param height rectangle height
	 * @param color fill color
	 * @param opacity fill opacity
	 */
	private static void appendRect(StringBuilder svg, double x, double y, double width, double height, Color color,
			double opacity) {
		svg.append("  <rect x=\"").append(svgNumber(x))
				.append("\" y=\"").append(svgNumber(y))
				.append("\" width=\"").append(svgNumber(width))
				.append("\" height=\"").append(svgNumber(height))
				.append("\" fill=\"").append(svgColor(color)).append('"');
		if (opacity < 0.999) {
			svg.append(" fill-opacity=\"").append(svgNumber(opacity)).append('"');
		}
		svg.append("/>\n");
	}

	/**
	 * Appends one filled circle to an SVG builder.
	 *
	 * @param svg target SVG builder
	 * @param x center x
	 * @param y center y
	 * @param radius circle radius
	 * @param color fill color
	 * @param opacity fill opacity
	 */
	private static void appendCircle(StringBuilder svg, double x, double y, double radius, Color color,
			double opacity) {
		svg.append("  <circle cx=\"").append(svgNumber(x))
				.append("\" cy=\"").append(svgNumber(y))
				.append("\" r=\"").append(svgNumber(radius))
				.append("\" fill=\"").append(svgColor(color)).append('"');
		if (opacity < 0.999) {
			svg.append(" fill-opacity=\"").append(svgNumber(opacity)).append('"');
		}
		svg.append("/>\n");
	}

	/**
	 * Appends one stroked line to an SVG builder.
	 *
	 * @param svg target SVG builder
	 * @param x1 first x coordinate
	 * @param y1 first y coordinate
	 * @param x2 second x coordinate
	 * @param y2 second y coordinate
	 * @param stroke stroke style
	 */
	private static void appendLine(StringBuilder svg, double x1, double y1, double x2, double y2, SvgStroke stroke) {
		svg.append("  <path d=\"M").append(svgNumber(x1)).append(' ').append(svgNumber(y1))
				.append(" L").append(svgNumber(x2)).append(' ').append(svgNumber(y2))
				.append("\" fill=\"none\" stroke=\"").append(svgColor(stroke.color()))
				.append("\" stroke-width=\"").append(svgNumber(stroke.width()))
				.append("\" stroke-linecap=\"round\"");
		if (stroke.opacity() < 0.999) {
			svg.append(" stroke-opacity=\"").append(svgNumber(stroke.opacity())).append('"');
		}
		svg.append("/>\n");
	}

	/**
	 * Formats one decimal value for SVG attributes.
	 *
	 * @param value numeric value
	 * @return compact decimal string
	 */
	private static String svgNumber(double value) {
		double rounded = Math.rint(value);
		if (Math.abs(value - rounded) < 1e-9) {
			return Long.toString((long) rounded);
		}
		String text = String.format(Locale.ROOT, "%.4f", value);
		int cut = text.length();
		while (cut > 0 && text.charAt(cut - 1) == '0') {
			cut--;
		}
		if (cut > 0 && text.charAt(cut - 1) == '.') {
			cut--;
		}
		return text.substring(0, cut);
	}

	/**
	 * Serializes one color as a CSS hex string.
	 *
	 * @param color source color
	 * @return six-digit hex color
	 */
	private static String svgColor(Color color) {
		return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * Returns the vertical height reserved by a figure and its caption block.
	 *
	 * @param imageHeight figure image height
	 * @return reserved figure block height
	 */
	private static double figureBlockHeight(double imageHeight) {
		return imageHeight + CAPTION_GAP + CAPTION_LEADING * CAPTION_MAX_LINES + CAPTION_TRAILING_GAP;
	}

	/**
	 * Draws centered text between two x coordinates.
	 *
	 * @param canvas target canvas
	 * @param left left boundary
	 * @param right right boundary
	 * @param y top edge
	 * @param style text style
	 * @param text text to draw
	 */
	private static void drawCentered(Canvas canvas, double left, double right, double y, TextStyle style, String text) {
		String safe = blankTo(text, "");
		double width = textWidth(style.font(), style.size(), safe);
		double x = left + Math.max(0.0, (right - left - width) / 2.0);
		canvas.drawText(x, y, style.font(), style.size(), style.color(), safe);
	}

	/**
	 * Draws centered chess notation, replacing figurine placeholders with SVG
	 * pieces.
	 *
	 * @param canvas target canvas
	 * @param left left boundary
	 * @param right right boundary
	 * @param y top edge
	 * @param style text style for ordinary text
	 * @param text notation text to draw
	 */
	private static void drawCenteredNotationText(Canvas canvas, double left, double right, double y, TextStyle style,
			String text) {
		String safe = blankTo(text, "");
		double width = notationTextWidth(style.font(), style.size(), safe);
		double x = left + Math.max(0.0, (right - left - width) / 2.0);
		drawNotationText(canvas, x, y, style.font(), style.size(), style.color(), safe);
	}

	/**
	 * Draws one chess notation run using embedded SVGs for piece symbols.
	 *
	 * @param canvas target canvas
	 * @param x left edge
	 * @param y text top edge
	 * @param font font for ordinary characters
	 * @param size font size
	 * @param color fill color for ordinary characters
	 * @param text notation text to draw
	 */
	private static void drawNotationText(Canvas canvas, double x, double y, Font font, double size, Color color,
			String text) {
		String safe = blankTo(text, "");
		double cursorX = x;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < safe.length(); i++) {
			char ch = safe.charAt(i);
			String pieceSvg = notationPieceSvg(ch);
			if (pieceSvg == null) {
				run.append(ch);
				continue;
			}
			cursorX = drawNotationTextRun(canvas, cursorX, y, font, size, color, run);
			drawNotationPiece(canvas, cursorX, y, size, pieceSvg);
			cursorX += notationPieceAdvance(size);
		}
		drawNotationTextRun(canvas, cursorX, y, font, size, color, run);
	}

	/**
	 * Draws a plain text segment inside notation and returns the next cursor.
	 *
	 * @param canvas target canvas
	 * @param x left edge
	 * @param y text top edge
	 * @param font font to use
	 * @param size font size
	 * @param color fill color
	 * @param run pending ordinary text
	 * @return cursor after the text segment
	 */
	private static double drawNotationTextRun(Canvas canvas, double x, double y, Font font, double size, Color color,
			StringBuilder run) {
		if (run.isEmpty()) {
			return x;
		}
		String text = run.toString();
		canvas.drawText(x, y, font, size, color, text);
		run.setLength(0);
		return x + textWidth(font, size, text);
	}

	/**
	 * Draws one inline notation piece SVG.
	 *
	 * @param canvas target canvas
	 * @param x logical cursor x coordinate
	 * @param y surrounding text top edge
	 * @param size surrounding font size
	 * @param pieceSvg embedded SVG source
	 */
	private static void drawNotationPiece(Canvas canvas, double x, double y, double size, String pieceSvg) {
		double boxSize = notationPieceBoxSize(size);
		double drawX = x + notationPieceLeftPadding(size);
		double drawY = y - size * NOTATION_PIECE_TOP_SHIFT_SCALE;
		canvas.drawSvg(pieceSvg, drawX, drawY, boxSize, boxSize);
	}

	/**
	 * Draws centered wrapped text with a line cap.
	 *
	 * @param canvas target canvas
	 * @param block text block geometry
	 * @param style text style
	 * @param leading line advance
	 * @param text text to draw
	 * @param maxLines maximum line count
	 */
	private static void drawCenteredWrapped(Canvas canvas, TextBlock block, TextStyle style, double leading, String text,
			int maxLines) {
		List<String> lines = wrapLines(text, style.font(), style.size(), block.width());
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			drawCentered(canvas, block.x(), block.x() + block.width(), block.y() + i * leading, style, lines.get(i));
		}
	}

	/**
	 * Draws a table-of-contents heading and returns the next cursor position.
	 *
	 * @param page target page
	 * @param title heading text
	 * @param first whether the first-page layout should be used
	 * @return next vertical cursor position
	 */
	private static double drawTocHeading(PageFrame page, String title, boolean first) {
		if (page.canvas == null) {
			return page.top + (first ? 34.0 : 18.0);
		}
		double y = page.top;
		if (first) {
			drawCentered(page.canvas, page.left, page.right, y,
					textStyle(Font.LATIN_MODERN_BOLD, TOC_TITLE_SIZE, INK), title);
			double lineY = y + Font.LATIN_MODERN_BOLD.lineHeight(TOC_TITLE_SIZE) + 8.0;
			page.canvas.line(page.left + page.width * 0.2, lineY, page.right - page.width * 0.2, lineY, SEPARATOR, 0.8);
			return lineY + 16.0;
		}
		page.canvas.drawText(page.left, y, Font.LATIN_MODERN_BOLD, TOC_ENTRY_SIZE, INK, title);
		return y + 18.0;
	}

	/**
	 * Draws one table-of-contents line with dot leaders.
	 *
	 * @param page target page
	 * @param entry TOC entry to draw
	 * @param y top edge
	 */
	private static void drawTocEntry(PageFrame page, TocEntry entry, double y) {
		if (page.canvas == null) {
			return;
		}
		double indent = entry.level == 1 ? 0.0 : 18.0;
		double x = page.left + indent;
		String pageText = Integer.toString(entry.pageNumber);
		double pageWidth = textWidth(Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, pageText);
		double pageX = page.right - pageWidth;
		double titleMaxWidth = Math.max(10.0, pageX - x - 14.0);
		String title = fitText(entry.title, Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, titleMaxWidth * 0.72);
		double titleWidth = textWidth(Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, title);
		double dotsX = x + titleWidth + 6.0;
		double dotsWidth = Math.max(0.0, pageX - dotsX - 6.0);
		String dots = dotLeader(Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, dotsWidth);
		page.canvas.drawText(x, y, Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, INK, title);
		page.canvas.drawText(dotsX, y, Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, MUTED, dots);
		page.canvas.drawText(pageX, y, Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, INK, pageText);
		page.canvas.linkToPage(x, y, page.right - x, Math.max(14.0,
				Font.LATIN_MODERN_ROMAN.lineHeight(TOC_ENTRY_SIZE)), entry.pageNumber);
	}

	/**
	 * Builds a dot-leader string that fits inside the requested width.
	 *
	 * @param font measurement font
	 * @param size font size
	 * @param width target width
	 * @return dot-leader string
	 */
	private static String dotLeader(Font font, double size, double width) {
		double dotWidth = Math.max(0.1, textWidth(font, size, "."));
		int count = Math.max(0, (int) Math.floor(width / dotWidth));
		StringBuilder builder = new StringBuilder(count);
		for (int i = 0; i < count; i++) {
			builder.append('.');
		}
		return builder.toString();
	}

	/**
	 * Fits a string into the requested width using an ASCII ellipsis fallback.
	 *
	 * @param text source text
	 * @param font measurement font
	 * @param size font size
	 * @param width target width
	 * @return fitted string
	 */
	private static String fitText(String text, Font font, double size, double width) {
		String safe = blankTo(text, "");
		if (textWidth(font, size, safe) <= width) {
			return safe;
		}
		String ellipsis = "...";
		int end = safe.length();
		while (end > 1) {
			String candidate = safe.substring(0, end).trim() + ellipsis;
			if (textWidth(font, size, candidate) <= width) {
				return candidate;
			}
			end--;
		}
		return ellipsis;
	}

	/**
	 * Fits chess notation into the requested width while measuring SVG pieces.
	 *
	 * @param text source notation
	 * @param font measurement font for ordinary text
	 * @param size font size
	 * @param width target width
	 * @return fitted notation string
	 */
	private static String fitNotationText(String text, Font font, double size, double width) {
		String safe = blankTo(text, "");
		if (notationTextWidth(font, size, safe) <= width) {
			return safe;
		}
		String ellipsis = "...";
		int end = safe.length();
		while (end > 1) {
			String candidate = safe.substring(0, end).trim() + ellipsis;
			if (notationTextWidth(font, size, candidate) <= width) {
				return candidate;
			}
			end--;
		}
		return ellipsis;
	}

	/**
	 * Measures wrapped notation height using SVG-aware piece widths.
	 *
	 * @param text source notation
	 * @param font font to measure ordinary text with
	 * @param size font size
	 * @param width wrap width
	 * @param leading line advance
	 * @return wrapped height
	 */
	private static double wrappedNotationHeight(String text, Font font, double size, double width, double leading) {
		return wrapNotationLines(text, font, size, width).size() * leading;
	}

	/**
	 * Wraps text using the same rules for both simulation and rendering passes.
	 *
	 * @param text source text
	 * @param font font to measure with
	 * @param size font size
	 * @param width wrap width
	 * @return wrapped line list
	 */
	private static List<String> wrapLines(String text, Font font, double size, double width) {
		return wrapLines(text, font, size, width, false);
	}

	/**
	 * Wraps notation using SVG-aware piece widths.
	 *
	 * @param text source notation
	 * @param font font to measure ordinary text with
	 * @param size font size
	 * @param width wrap width
	 * @return wrapped line list
	 */
	private static List<String> wrapNotationLines(String text, Font font, double size, double width) {
		return wrapLines(text, font, size, width, true);
	}

	/**
	 * Wraps text with optional SVG notation measurement.
	 *
	 * @param text source text
	 * @param font font to measure with
	 * @param size font size
	 * @param width wrap width
	 * @param notation whether figurine placeholders should use SVG widths
	 * @return wrapped line list
	 */
	private static List<String> wrapLines(String text, Font font, double size, double width, boolean notation) {
		List<String> lines = new ArrayList<>();
		String safe = normalizeWhitespacePreserveBreaks(text);
		if (safe.isBlank()) {
			return lines;
		}
		if (width <= 0.0) {
			lines.add(safe.trim());
			return lines;
		}

		String[] paragraphs = safe.split("\n", -1);
		for (String paragraph : paragraphs) {
			if (paragraph.isBlank()) {
				lines.add("");
				continue;
			}
			String[] words = paragraph.trim().split("\\s+");
			StringBuilder line = new StringBuilder();
			for (String word : words) {
				appendWrappedWord(lines, line, word, font, size, width, notation);
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
			}
		}
		return lines;
	}

	/**
	 * Appends one word to the current wrapped line, flushing as needed.
	 *
	 * @param lines output line list
	 * @param line current line builder
	 * @param word word to append
	 * @param font measurement font
	 * @param size font size
	 * @param width wrap width
	 * @param notation whether figurine placeholders should use SVG widths
	 */
	private static void appendWrappedWord(List<String> lines, StringBuilder line, String word, Font font, double size,
			double width, boolean notation) {
		String candidate = line.isEmpty() ? word : line + " " + word;
		if (line.isEmpty() || measuredTextWidth(font, size, candidate, notation) <= width) {
			line.setLength(0);
			line.append(candidate);
			return;
		}
		lines.add(line.toString());
		appendBrokenWord(lines, word, font, size, width, notation, line);
	}

	/**
	 * Appends a single oversized word by breaking it into line-sized fragments.
	 *
	 * @param lines output line list
	 * @param word oversized word
	 * @param font measurement font
	 * @param size font size
	 * @param width wrap width
	 * @param notation whether figurine placeholders should use SVG widths
	 * @param scratch reusable line builder
	 */
	private static void appendBrokenWord(List<String> lines, String word, Font font, double size, double width,
			boolean notation, StringBuilder scratch) {
		scratch.setLength(0);
		for (int i = 0; i < word.length(); i++) {
			char ch = word.charAt(i);
			String candidate = scratch.toString() + ch;
			if (!scratch.isEmpty() && measuredTextWidth(font, size, candidate, notation) > width) {
				lines.add(scratch.toString());
				scratch.setLength(0);
			}
			scratch.append(ch);
		}
	}

	/**
	 * Measures a text run with optional notation SVG widths.
	 *
	 * @param font font to measure ordinary text with
	 * @param size font size
	 * @param text text to measure
	 * @param notation whether figurine placeholders should use SVG widths
	 * @return measured width
	 */
	private static double measuredTextWidth(Font font, double size, String text, boolean notation) {
		return notation ? notationTextWidth(font, size, text) : textWidth(font, size, text);
	}

	/**
	 * Measures chess notation with SVG piece advances instead of figurine glyphs.
	 *
	 * @param font font to measure ordinary text with
	 * @param size font size
	 * @param text notation text to measure
	 * @return measured width
	 */
	private static double notationTextWidth(Font font, double size, String text) {
		String safe = blankTo(text, "");
		double width = 0.0;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < safe.length(); i++) {
			char ch = safe.charAt(i);
			if (notationPieceSvg(ch) == null) {
				run.append(ch);
				continue;
			}
			width += textWidth(font, size, run.toString());
			run.setLength(0);
			width += notationPieceAdvance(size);
		}
		return width + textWidth(font, size, run.toString());
	}

	/**
	 * Returns whether a text run contains a notation piece placeholder.
	 *
	 * @param text text to inspect
	 * @return true when at least one SVG-rendered notation piece is present
	 */
	private static boolean containsNotationPiece(String text) {
		String safe = blankTo(text, "");
		for (int i = 0; i < safe.length(); i++) {
			if (notationPieceSvg(safe.charAt(i)) != null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Resolves the embedded SVG source for a notation piece placeholder.
	 *
	 * @param ch notation placeholder character
	 * @return embedded SVG source, or null when the character is ordinary text
	 */
	private static String notationPieceSvg(char ch) {
		return switch (ch) {
			case NOTATION_KING -> SvgShapes.whiteKing();
			case NOTATION_QUEEN -> SvgShapes.whiteQueen();
			case NOTATION_ROOK -> SvgShapes.whiteRook();
			case NOTATION_BISHOP -> SvgShapes.whiteBishop();
			case NOTATION_KNIGHT -> SvgShapes.whiteKnight();
			case NOTATION_PAWN -> SvgShapes.whitePawn();
			default -> null;
		};
	}

	/**
	 * Computes the SVG box size used for one inline notation piece.
	 *
	 * @param size surrounding font size
	 * @return square SVG box size
	 */
	private static double notationPieceBoxSize(double size) {
		return size * NOTATION_PIECE_SIZE_SCALE;
	}

	/**
	 * Computes the left side bearing before one inline notation piece.
	 *
	 * @param size surrounding font size
	 * @return left padding before the piece
	 */
	private static double notationPieceLeftPadding(double size) {
		return size * NOTATION_PIECE_LEFT_PADDING_SCALE;
	}

	/**
	 * Computes the right side bearing after one inline notation piece.
	 *
	 * @param size surrounding font size
	 * @return right padding after the piece
	 */
	private static double notationPieceRightPadding(double size) {
		return size * NOTATION_PIECE_RIGHT_PADDING_SCALE;
	}

	/**
	 * Computes the logical advance used for one inline notation piece.
	 *
	 * @param size surrounding font size
	 * @return cursor advance after drawing the piece
	 */
	private static double notationPieceAdvance(double size) {
		return notationPieceLeftPadding(size) + notationPieceBoxSize(size) + notationPieceRightPadding(size);
	}

	/**
	 * Measures the width of a text run.
	 *
	 * @param font font to measure with
	 * @param size font size
	 * @param text text to measure
	 * @return measured width
	 */
	private static double textWidth(Font font, double size, String text) {
		return font.textWidth(blankTo(text, ""), size);
	}

	/**
	 * Returns a non-null string fallback.
	 *
	 * @param value source value
	 * @param fallback fallback when the source is null
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
		return normalizeWhitespacePreserveBreaks(text).replace('\n', ' ').replaceAll("\\s+", " ").trim();
	}

	/**
	 * Normalizes line endings while preserving paragraph breaks.
	 *
	 * @param text source text
	 * @return normalized text
	 */
	private static String normalizeWhitespacePreserveBreaks(String text) {
		return blankTo(text, "").replace("\r\n", "\n").replace('\r', '\n').trim();
	}

	/**
	 * Returns a null-safe array clone.
	 *
	 * @param source source array
	 * @return cloned array, never null
	 */
	private static String[] safeArray(String[] source) {
		return source == null ? new String[0] : source.clone();
	}

	/**
	 * Describes one physical page style.
	 *
	 * @since 2026
	 * @author Lennart A. Conrad
	 */
	private enum PageStyle {

		/**
		 * Page without running headers or page numbers.
		 */
		HIDDEN,

		/**
		 * Page with outer page number only.
		 */
		PLAIN,

		/**
		 * Page with running header plus outer page number.
		 */
		BODY
	}

}
