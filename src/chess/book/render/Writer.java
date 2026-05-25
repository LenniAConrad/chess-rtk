package chess.book.render;

import static chess.book.render.WriterText.*;
import static chess.book.render.WriterVisuals.*;

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
	static final String PRODUCER = "chess-rtk native book pdf";

	/**
	 * Creator metadata written into the generated PDF.
	 */
	static final String CREATOR = "chess-rtk native book renderer";

	/**
	 * Subject metadata written into normal book PDFs.
	 */
	static final String SUBJECT = "Chess Book";

	/**
	 * Restriction text embedded in free-edition metadata and page overlays.
	 */
	static final String FREE_WATERMARK_RESTRICTION =
			"Free electronic copy; printing, resale, and unauthorized redistribution not allowed";

	/**
	 * Subject metadata written into watermarked free-edition PDFs.
	 */
	static final String FREE_WATERMARK_SUBJECT =
			"Chess Book - " + FREE_WATERMARK_RESTRICTION;

	/**
	 * Main diagonal text for watermarked free-edition PDFs.
	 */
	static final String FREE_WATERMARK_MAIN = "FREE ELECTRONIC COPY";

	/**
	 * Short repeated text used as a dense free-edition anti-print pattern.
	 */
	static final String FREE_WATERMARK_REPEAT =
			"FREE COPY - NO PRINTING - NO RESALE";

	/**
	 * Board pixel size used when asking the SVG renderer for source markup.
	 */
	static final int BOARD_PIXELS = 900;

	/**
	 * Cover-title font size.
	 */
	static final double COVER_TITLE_SIZE = 26.0;

	/**
	 * Cover-subtitle font size.
	 */
	static final double COVER_SUBTITLE_SIZE = 14.0;

	/**
	 * Cover footer font size.
	 */
	static final double COVER_FOOTER_SIZE = 10.0;

	/**
	 * Main section-heading font size.
	 */
	static final double SECTION_TITLE_SIZE = 18.0;

	/**
	 * Top offset for normal section headings.
	 */
	static final double SECTION_HEADING_TOP_OFFSET = 6.4;

	/**
	 * Gap between the section number and the section title.
	 */
	static final double SECTION_TITLE_GAP = 3.5;

	/**
	 * Table-of-contents title font size.
	 */
	static final double TOC_TITLE_SIZE = 18.0;

	/**
	 * Table-of-contents row font size.
	 */
	static final double TOC_ENTRY_SIZE = 10.0;

	/**
	 * Body-copy font size.
	 */
	static final double BODY_SIZE = 10.0;

	/**
	 * Body-copy leading.
	 */
	static final double BODY_LEADING = 12.0;

	/**
	 * Running-header font size.
	 */
	static final double HEADER_SIZE = 10.0;

	/**
	 * Distance from the body top edge to the running-header baseline.
	 */
	static final double HEADER_BODY_TOP_OFFSET = 35.85;

	/**
	 * Footer font size.
	 */
	static final double FOOTER_SIZE = 10.0;

	/**
	 * Puzzle-label font size.
	 */
	static final double PUZZLE_LABEL_SIZE = 9.0;

	/**
	 * Solution-label font size.
	 */
	static final double SOLUTION_LABEL_SIZE = 8.5;

	/**
	 * Solution-footnote font size.
	 */
	static final double SOLUTION_FOOTNOTE_SIZE = 8.0;

	/**
	 * Clearance between the last solution-grid label and the solution footnote.
	 */
	static final double SOLUTION_FOOTNOTE_CLEARANCE = 8.0;

	/**
	 * Table font size.
	 */
	static final double TABLE_SIZE = 9.0;

	/**
	 * Table header font size.
	 */
	static final double TABLE_HEADER_SIZE = 10.0;

	/**
	 * Table body line advance.
	 */
	static final double TABLE_LEADING = 12.5;

	/**
	 * Generic heading and body color.
	 */
	static final Color INK = new Color(32, 32, 32);

	/**
	 * Muted secondary-text color.
	 */
	static final Color MUTED = new Color(82, 82, 82);

	/**
	 * Table-rule gray used by solution tables.
	 */
	static final Color TABLE_GRAY = new Color(128, 128, 128);

	/**
	 * Subtle cover separator color.
	 */
	static final Color SEPARATOR = new Color(110, 110, 110);

	/**
	 * Decorative footer line color for the closing page.
	 */
	static final Color CLOSING_LINE = new Color(150, 150, 150);

	/**
	 * Large watermark color.
	 */
	static final Color FREE_WATERMARK_LARGE = new Color(68, 68, 68, 44);

	/**
	 * Repeated watermark color.
	 */
	static final Color FREE_WATERMARK_SMALL = new Color(70, 70, 70, 22);

	/**
	 * Edge notice color for watermarked free-edition PDFs.
	 */
	static final Color FREE_WATERMARK_NOTICE = new Color(34, 34, 34, 118);

	/**
	 * Page-identifier watermark color.
	 */
	static final Color FREE_WATERMARK_ID = new Color(34, 34, 34, 90);

	/**
	 * Gap between grid cells.
	 */
	static final double GRID_GAP = 5.0;

	/**
	 * Board width fraction used by puzzle and solution figures.
	 */
	static final double PUZZLE_BOARD_FRACTION = 0.22;

	/**
	 * Board width fraction used by special-move figures.
	 */
	static final double SPECIAL_EXAMPLE_BOARD_FRACTION = 0.31;

	/**
	 * Minimum visual gutter fraction used between special-move examples.
	 */
	static final double SPECIAL_EXAMPLE_GUTTER_FRACTION = 0.022;

	/**
	 * Horizontal marker indent used by itemized lists.
	 */
	static final double LIST_MARKER_INDENT = 18.0;

	/**
	 * Body-text offset used for list items after the marker.
	 */
	static final double LIST_BODY_INDENT = 34.0;

	/**
	 * Gap between a list marker and its body text.
	 */
	static final double LIST_MARKER_GAP = 5.0;

	/**
	 * Vertical gap between consecutive list items.
	 */
	static final double LIST_ITEM_GAP = 4.0;

	/**
	 * Vertical gap between labels and boards inside a grid cell.
	 */
	static final double GRID_LABEL_GAP = 3.0;

	/**
	 * Padding applied inside each table row cell.
	 */
	static final double TABLE_CELL_PADDING = 2.0;

	/**
	 * Scale applied to inline piece SVG boxes relative to the surrounding text.
	 */
	static final double NOTATION_PIECE_SIZE_SCALE = 1.16;

	/**
	 * Left side bearing reserved before one inline piece SVG.
	 */
	static final double NOTATION_PIECE_LEFT_PADDING_SCALE = 0.04;

	/**
	 * Right side bearing reserved after one inline piece SVG.
	 */
	static final double NOTATION_PIECE_RIGHT_PADDING_SCALE = 0.19;

	/**
	 * Upward shift applied to inline piece SVG boxes relative to the text top.
	 */
	static final double NOTATION_PIECE_TOP_SHIFT_SCALE = 0.03;

	/**
	 * Pattern for numbered list items in user-supplied prose.
	 */
	static final Pattern NUMBERED_LIST_ITEM = Pattern.compile("(\\d+[.)])\\s+(.+)");

	/**
	 * Internal marker used for unordered list items.
	 */
	static final String UNORDERED_LIST_MARKER = "\u2022";

	/**
	 * Diameter of the filled vector circle used for unordered list markers.
	 */
	static final double LIST_BULLET_DIAMETER = 3.4;

	/**
	 * Margin reserved above body-page content after a section heading.
	 */
	static final double SECTION_AFTER_GAP = 26.0;

	/**
	 * Additional vertical gap between paragraphs.
	 */
	static final double PARAGRAPH_GAP = 12.0;

	/**
	 * Font size used for how-to-read subheadings.
	 */
	static final double SUBHEADING_SIZE = BODY_SIZE;

	/**
	 * Font size used for example-board captions.
	 */
	static final double CAPTION_SIZE = 8.0;

	/**
	 * Gap between example images and their italic captions.
	 */
	static final double CAPTION_GAP = 10.0;

	/**
	 * Line advance used for wrapped example captions.
	 */
	static final double CAPTION_LEADING = 9.0;

	/**
	 * Maximum number of caption lines reserved inside figure blocks.
	 */
	static final int CAPTION_MAX_LINES = 3;

	/**
	 * Gap after figure captions before normal prose resumes.
	 */
	static final double CAPTION_TRAILING_GAP = 16.0;

	/**
	 * Relative drop-shadow size used for standalone example boards.
	 */
	static final double EXAMPLE_BOARD_SHADOW_FRACTION = 0.05;

	/**
	 * Native width used when composing page-preview SVGs.
	 */
	static final int PAGE_PREVIEW_WIDTH = 2000;

	/**
	 * Relative drop-shadow size used for page-preview SVGs.
	 */
	static final double PAGE_PREVIEW_SHADOW_FRACTION = 0.05;

	/**
	 * Base number of vector speckles drawn on each A4-equivalent watermarked page.
	 */
	static final int FREE_WATERMARK_DOTS_A4 = 260;

	/**
	 * Base number of short vector scratches drawn on each A4-equivalent watermarked page.
	 */
	static final int FREE_WATERMARK_SCRATCHES_A4 = 64;

	/**
	 * Angle used for large free-edition watermark text.
	 */
	static final double FREE_WATERMARK_ANGLE = -34.0;

	/**
	 * Minimum distance between repeated free-edition labels.
	 */
	static final double FREE_WATERMARK_REPEAT_STEP = 38.0;

	/**
	 * Deterministic salt used by the free-edition noise generator.
	 */
	static final long FREE_WATERMARK_SEED = 0x4352544b46524545L;

	/**
	 * Relative page-preview border size.
	 */
	static final double PAGE_PREVIEW_BORDER_FRACTION = 0.002;

	/**
	 * Page-preview border color.
	 */
	static final Color PAGE_PREVIEW_BORDER_COLOR = new Color(100, 100, 100);

	/**
	 * Extra SVG view-box space reserved around vector drop shadows.
	 */
	static final double PAGE_SHADOW_RESERVE_SCALE = 2.0;

	/**
	 * Maximum opacity at the contact edge of a vector shadow.
	 */
	static final double PAGE_SHADOW_CONTACT_OPACITY = 0.42;

	/**
	 * Example FEN used to demonstrate en-passant arrows.
	 */
	static final String EXAMPLE_EN_PASSANT_FEN = "8/2k5/8/5Pp1/8/8/4K3/8 w - g6 0 1";

	/**
	 * Example FEN used to demonstrate castling arrows.
	 */
	static final String EXAMPLE_CASTLING_FEN = "r3k3/8/8/8/8/8/8/4K2R w Kq - 0 1";

	/**
	 * Example FEN used to demonstrate combined en-passant and castling arrows.
	 */
	static final String EXAMPLE_SPECIAL_RIGHTS_FEN = "1k2r3/n6p/8/3pP3/8/6B1/4P3/R3K3 w Q d6 0 1";

	/**
	 * Normal embedded board separator color.
	 */
	static final String BOARD_GRID_FILL = "#b2b2b2";

	/**
	 * Normal embedded light-square color.
	 */
	static final String BOARD_LIGHT_FILL = "#e5e5e5";

	/**
	 * Normal embedded dark-square color.
	 */
	static final String BOARD_DARK_FILL = "#cccccc";

	/**
	 * Brightened board separator color used on solution diagrams.
	 */
	static final String SOLUTION_BOARD_GRID_FILL = "#d3d3d3";

	/**
	 * Brightened light-square color used on solution diagrams.
	 */
	static final String SOLUTION_BOARD_LIGHT_FILL = "#ffffff";

	/**
	 * Brightened dark-square color used on solution diagrams.
	 */
	static final String SOLUTION_BOARD_DARK_FILL = "#efefef";

	/**
	 * Common SVG tag terminator used by generated gradient definitions.
	 */
	static final String SVG_TAG_END = "\">\n";

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
	static LayoutResult layout(Book book, SolutionInfo[] solutions, int tocPages, boolean render,
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
	static Document createDocument(Book book, boolean freeWatermark, String watermarkId) {
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
	static void renderCover(LayoutState state) {
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
	static String[] buildCoverCopyright(Book book) {
		return WriterSupport.buildCoverCopyright(book);
	}

	/**
	 * Renders the optional dedication page and enforces the next odd-page start.
	 *
	 * @param state active layout state
	 */
	static void renderDedication(LayoutState state) {
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
	static void reserveTocPages(LayoutState state) {
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
	static void renderIntroduction(LayoutState state) {
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
	static void renderHowToRead(LayoutState state) {
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
	static void renderDefaultHowToRead(LayoutState state, String title) {
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
	static TextCursor renderPuzzleAndSolutionExamples(LayoutState state, TextCursor cursor,
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
	static TextCursor renderSpecialMoveExamples(LayoutState state, TextCursor cursor, String runningHeader) {
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
	static TextCursor renderSubheading(LayoutState state, TextCursor cursor, String runningHeader,
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
	static TextCursor renderBullets(LayoutState state, TextCursor cursor, String runningHeader,
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
	static String buildIntroductionSignature(Book book) {
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
	static void renderPuzzles(LayoutState state) {
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
	static List<PuzzleBlock> buildPuzzleBlocks(Book book) {
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
	static int puzzlesPerPage(Book book) {
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
	static GridMetrics boardGrid(PageFrame page, Book book) {
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
	static double solutionFootnoteReserve() {
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
	static void renderPuzzleSpread(LayoutState state, PageFrame page, int startIndex, int count) {
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
	static void renderSolutionSpread(LayoutState state, PageFrame page, int startIndex, int count,
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
	static void renderSolutionTable(LayoutState state, String sectionLabel, int startIndex, int count) {
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
	static void drawTableBottomRule(PageFrame page, double y) {
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
	static List<TableRow> buildTableRows(Book book, int startIndex, int count) {
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
	static double tableRowHeight(TableRow row, double tableWidth) {
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
	static List<TableRow> splitTableRow(TableRow row, PageFrame page) {
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
	static int maxTableSegmentLines(PageFrame page) {
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
	static List<String> nonEmptyLines(List<String> lines) {
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
	static String joinLines(List<String> lines, int start, int end) {
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
	static double drawTableHeader(PageFrame page, Book book) {
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
	static void drawTableRow(PageFrame page, TableRow row, double y) {
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
	static void drawTableRowInternal(PageFrame page, TableRow row, double y, boolean header) {
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
	static double[] tableColumnWidths(double tableWidth) {
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
	static double tableContentWidth(double pageWidth) {
		return pageWidth * 0.90;
	}

	/**
	 * Returns the table's left edge.
	 *
	 * @param page target page
	 * @return table left edge
	 */
	static double tableLeft(PageFrame page) {
		return page.left + page.width * 0.05;
	}

	/**
	 * Returns the table's right edge.
	 *
	 * @param page target page
	 * @return table right edge
	 */
	static double tableRight(PageFrame page) {
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
	static TextStyle textStyle(Font font, double size, Color color) {
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
	static TableCellStyle tableCellStyle(Font font, double size, boolean centered) {
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
	static void drawTableCell(Canvas canvas, double x, double y, double width, double height, String text,
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
	static void renderAfterword(LayoutState state) {
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
	static void drawClosingHeart(PageFrame page, double y) {
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
	static void renderToc(LayoutState state) {
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
	static int measureTocPages(Book book, List<TocEntry> entries) {
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
	static TextCursor openSection(LayoutState state, String title, boolean centered) {
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
	static double drawSectionHeading(PageFrame page, String number, String title) {
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
	static double drawCenteredSectionPage(PageFrame page, String number, String title) {
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
	static TextCursor renderParagraphs(LayoutState state, TextCursor cursor, String runningHeader,
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
	static TextCursor renderParagraph(LayoutState state, TextCursor cursor, String runningHeader,
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
	static TextCursor renderPlainParagraph(LayoutState state, TextCursor cursor, String runningHeader,
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
	static TextCursor renderListItem(LayoutState state, TextCursor cursor, String runningHeader,
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
	static void drawListMarker(Canvas canvas, double x, double y, String marker) {
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
	static void drawListText(Canvas canvas, double x, double y, String text, boolean allowBoldLabel) {
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
	static double listBodyOffset(String marker) {
		return Math.max(LIST_BODY_INDENT,
				LIST_MARKER_INDENT + textWidth(Font.LATIN_MODERN_ROMAN, BODY_SIZE, marker) + LIST_MARKER_GAP);
	}

	/**
	 * Returns the end index of a short list label before a colon.
	 *
	 * @param text source line
	 * @return colon index, or -1 when the line does not start with a label
	 */
	static int listLabelEnd(String text) {
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
	static boolean containsListItem(String paragraph) {
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
	static ListItem parseListItem(String sourceLine) {
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
	static TextCursor ensureBodySpace(LayoutState state, TextCursor cursor, double requiredHeight,
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
	static PageFrame addPage(LayoutState state, PageStyle style, String oddHeaderText) {
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
	static void drawPageChrome(Book book, PageFrame frame, PageStyle style, String oddHeaderText) {
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
	static String normalizeHeader(String text) {
		return blankTo(text, "").toUpperCase(Locale.ROOT);
	}

	/**
	 * Ensures the next content page starts on an odd page number.
	 *
	 * @param state active layout state
	 */
	static void clearDoublePage(LayoutState state) {
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
	static void clearDoubleBodyPage(LayoutState state, String oddHeaderText) {
		if ((state.pageNumber & 1) == 1) {
			addPage(state, PageStyle.BODY, oddHeaderText);
		}
	}

	/**
	 * Ensures the next content page starts on an even page number.
	 *
	 * @param state active layout state
	 */
	static void clearEvenPage(LayoutState state) {
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
	static void clearEvenBodyPage(LayoutState state, String oddHeaderText) {
		if ((state.pageNumber & 1) == 0) {
			addPage(state, PageStyle.BODY, oddHeaderText);
		}
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
