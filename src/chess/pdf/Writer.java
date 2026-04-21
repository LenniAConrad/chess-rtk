package chess.pdf;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import chess.core.Move;
import chess.core.Position;
import chess.images.render.Render;
import chess.pdf.document.Canvas;
import chess.pdf.document.Document;
import chess.pdf.document.Font;
import chess.pdf.document.Page;

/**
 * Direct chess PDF generation built on the local zero-dependency PDF writer.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Writer {

	/**
	 * Primary title color.
	 */
	private static final Color TITLE_COLOR = new Color(28, 33, 41);

	/**
	 * Metadata text color.
	 */
	private static final Color META_COLOR = new Color(98, 107, 120);

	/**
	 * Body text color.
	 */
	private static final Color BODY_COLOR = new Color(28, 33, 41);

	/**
	 * Accent color for headings and continuation headers.
	 */
	private static final Color ACCENT_COLOR = new Color(147, 103, 56);

	/**
	 * Divider rule color.
	 */
	private static final Color RULE_COLOR = new Color(220, 223, 228);

	/**
	 * Diagram card background color.
	 */
	private static final Color CARD_COLOR = new Color(246, 244, 239);

	/**
	 * Font used for composition titles.
	 */
	private static final Font TITLE_FONT = Font.HELVETICA_BOLD;

	/**
	 * Font used for section headings.
	 */
	private static final Font HEADING_FONT = Font.HELVETICA_BOLD;

	/**
	 * Font used for body paragraphs.
	 */
	private static final Font BODY_FONT = Font.TIMES_ROMAN;

	/**
	 * Bold body font used for figure captions.
	 */
	private static final Font BODY_BOLD_FONT = Font.TIMES_BOLD;

	/**
	 * Font used for metadata and details.
	 */
	private static final Font META_FONT = Font.HELVETICA;

	/**
	 * Font used for FEN strings.
	 */
	private static final Font MONO_FONT = Font.COURIER;

	/**
	 * Title font size in points.
	 */
	private static final double TITLE_SIZE = 20.0;

	/**
	 * Heading font size in points.
	 */
	private static final double HEADING_SIZE = 12.0;

	/**
	 * Body font size in points.
	 */
	private static final double BODY_SIZE = 11.0;

	/**
	 * Metadata font size in points.
	 */
	private static final double META_SIZE = 9.0;

	/**
	 * FEN font size in points.
	 */
	private static final double FEN_SIZE = 7.5;

	/**
	 * Utility class; prevent instantiation.
	 */
	private Writer() {
		// utility holder
	}

	/**
	 * Writes one composition as a PDF document.
	 *
	 * @param output      output file path
	 * @param composition composition to render
	 * @throws IOException if writing fails
	 */
	public static void writeComposition(Path output, Composition composition) throws IOException {
		writeComposition(output, composition, new Options());
	}

	/**
	 * Writes one composition as a PDF document with custom options.
	 *
	 * @param output      output file path
	 * @param composition composition to render
	 * @param options     layout options
	 * @throws IOException if writing fails
	 */
	public static void writeComposition(Path output, Composition composition, Options options)
			throws IOException {
		if (composition == null) {
			throw new IllegalArgumentException("composition cannot be null");
		}
		writeCompositions(output, defaultDocumentTitle(composition), List.of(composition), options);
	}

	/**
	 * Writes several compositions into one PDF document.
	 *
	 * @param output        output file path
	 * @param documentTitle document title
	 * @param compositions  compositions to render
	 * @throws IOException if writing fails
	 */
	public static void writeCompositions(Path output, String documentTitle, List<Composition> compositions)
			throws IOException {
		writeCompositions(output, documentTitle, compositions, new Options());
	}

	/**
	 * Writes several compositions into one PDF document with custom options.
	 *
	 * @param output        output file path
	 * @param documentTitle document title
	 * @param compositions  compositions to render
	 * @param options       layout options
	 * @throws IOException if writing fails
	 */
	public static void writeCompositions(Path output, String documentTitle, List<Composition> compositions,
			Options options) throws IOException {
		if (compositions == null || compositions.isEmpty()) {
			throw new IllegalArgumentException("compositions cannot be empty");
		}
		Options safeOptions = options == null ? new Options() : options;

		Document pdf = new Document()
				.setTitle(documentTitle == null ? "" : documentTitle)
				.setAuthor("chess-rtk")
				.setSubject("Chess PDF export")
				.setCreator("chess.pdf.Writer")
				.setProducer("chess-rtk pdf");

		for (int i = 0; i < compositions.size(); i++) {
			renderComposition(pdf, compositions.get(i), i + 1, safeOptions);
		}
		pdf.write(output);
	}

	/**
	 * Writes a simple diagram sheet from FEN strings.
	 *
	 * @param output        output file path
	 * @param documentTitle document title
	 * @param fens          position list
	 * @throws IOException if writing fails
	 */
	public static void writeDiagramSheet(Path output, String documentTitle, List<String> fens) throws IOException {
		writeDiagramSheet(output, documentTitle, fens, new Options());
	}

	/**
	 * Writes a simple diagram sheet from FEN strings with custom options.
	 *
	 * @param output        output file path
	 * @param documentTitle document title
	 * @param fens          position list
	 * @param options       layout options
	 * @throws IOException if writing fails
	 */
	public static void writeDiagramSheet(Path output, String documentTitle, List<String> fens, Options options)
			throws IOException {
		if (fens == null || fens.isEmpty()) {
			throw new IllegalArgumentException("fens cannot be empty");
		}
		Composition sheet = new Composition().setTitle(documentTitle);
		for (int i = 0; i < fens.size(); i++) {
			sheet.addFigure(fens.get(i), "Diagram " + (i + 1), "", "");
		}
		writeComposition(output, sheet, options);
	}

	/**
	 * Renders one composition into an existing PDF document.
	 *
	 * @param pdf target PDF document
	 * @param composition composition to render
	 * @param number one-based composition number
	 * @param options rendering options
	 */
	private static void renderComposition(Document pdf, Composition composition, int number, Options options) {
		Flow flow = Flow.firstPage(pdf, composition, number, options);

		if (!composition.getDescription().isBlank()) {
			flow = flow.paragraph(composition.getDescription(), false);
			flow.gap(10.0);
		}

		List<DiagramSpec> diagrams = buildDiagrams(composition, options);
		if (!diagrams.isEmpty()) {
			flow = flow.section("Diagrams");
			flow = drawDiagrams(flow, diagrams, options);
		}

		if (!composition.getComment().isBlank()) {
			flow = flow.section("Comment");
			flow = flow.paragraph(composition.getComment(), false);
		}

		flow = drawHint(flow, "Hint 1", composition.getHintLevel1());
		flow = drawHint(flow, "Hint 2", composition.getHintLevel2());
		flow = drawHint(flow, "Hint 3", composition.getHintLevel3());
		flow = drawHint(flow, "Hint 4", composition.getHintLevel4());

		if (!composition.getAnalysis().isBlank()) {
			flow = flow.section("Analysis");
			flow.paragraph(composition.getAnalysis(), false);
		}
	}

	/**
	 * Renders one hint section when text is present.
	 *
	 * @param flow current flow cursor
	 * @param title section title
	 * @param text hint text
	 * @return updated flow cursor
	 */
	private static Flow drawHint(Flow flow, String title, String text) {
		if (text.isBlank()) {
			return flow;
		}
		return flow.section(title).paragraph(text, false);
	}

	/**
	 * Renders diagram cards with captions and optional FEN text.
	 *
	 * @param flow current flow cursor
	 * @param diagrams diagrams to render
	 * @param options rendering options
	 * @return updated flow cursor
	 */
	private static Flow drawDiagrams(Flow flow, List<DiagramSpec> diagrams, Options options) {
		double margin = options.getMargin();
		double usableWidth = flow.page.getWidth() - margin * 2.0;
		double gap = 18.0;
		int perRow = options.getDiagramsPerRow();
		double boardWidth = (usableWidth - gap * (perRow - 1)) / perRow;
		double captionWidth = boardWidth;

		for (int rowStart = 0; rowStart < diagrams.size(); rowStart += perRow) {
			int rowEnd = Math.min(diagrams.size(), rowStart + perRow);
				double rowHeight = diagramRowHeight(flow.canvas, diagrams, rowStart, rowEnd, boardWidth, captionWidth,
						options);

			flow = flow.ensure(rowHeight + 8.0);

			for (int i = rowStart; i < rowEnd; i++) {
					DiagramSpec diagram = diagrams.get(i);
					double x = margin + (i - rowStart) * (boardWidth + gap);
					drawDiagramBlock(flow, diagram, x, boardWidth, captionWidth, options);
				}

			flow.y += rowHeight + 8.0;
		}

		return flow.gap(4.0);
	}

	/**
	 * Measures the tallest diagram block in one row.
	 *
	 * @param canvas target canvas for text measurement
	 * @param diagrams all diagram specs
	 * @param rowStart first diagram index in the row
	 * @param rowEnd exclusive row end index
	 * @param boardWidth rendered board width
	 * @param captionWidth text wrap width
	 * @param options rendering options
	 * @return required row height
	 */
	private static double diagramRowHeight(Canvas canvas, List<DiagramSpec> diagrams, int rowStart, int rowEnd,
			double boardWidth, double captionWidth, Options options) {
		double rowHeight = 0.0;
		for (int i = rowStart; i < rowEnd; i++) {
			rowHeight = Math.max(rowHeight, diagramBlockHeight(canvas, diagrams.get(i), boardWidth, captionWidth,
					options));
		}
		return rowHeight;
	}

	/**
	 * Measures one diagram block.
	 *
	 * @param canvas target canvas for text measurement
	 * @param diagram diagram to measure
	 * @param boardWidth rendered board width
	 * @param captionWidth text wrap width
	 * @param options rendering options
	 * @return required block height
	 */
	private static double diagramBlockHeight(Canvas canvas, DiagramSpec diagram, double boardWidth, double captionWidth,
			Options options) {
		double blockHeight = boardWidth;
		if (!diagram.caption.isBlank()) {
			blockHeight += 8.0 + canvas.measureWrappedText(captionWidth, BODY_BOLD_FONT, META_SIZE, 10.5,
					diagram.caption);
		}
		if (!diagram.detail.isBlank()) {
			blockHeight += 4.0 + canvas.measureWrappedText(captionWidth, META_FONT, META_SIZE, 9.5, diagram.detail);
		}
		if (options.isShowFen()) {
			blockHeight += 5.0 + canvas.measureWrappedText(captionWidth, MONO_FONT, FEN_SIZE, 8.5, diagram.fen);
		}
		return blockHeight;
	}

	/**
	 * Draws one diagram block.
	 *
	 * @param flow current flow cursor
	 * @param diagram diagram to draw
	 * @param x left edge
	 * @param boardWidth rendered board width
	 * @param captionWidth text wrap width
	 * @param options rendering options
	 */
	private static void drawDiagramBlock(Flow flow, DiagramSpec diagram, double x, double boardWidth,
			double captionWidth, Options options) {
		double y = flow.y;
		flow.canvas.fillRect(x - 6.0, y - 6.0, boardWidth + 12.0, boardWidth + 12.0, CARD_COLOR);
		flow.canvas.drawSvg(diagram.svg, x, y, boardWidth, boardWidth);

		double baseline = y + boardWidth + 8.0;
		double textY = drawOptionalText(flow.canvas, x, baseline, captionWidth, diagram.caption,
				new PdfTextStyle(BODY_BOLD_FONT, META_SIZE, 10.5, BODY_COLOR));
		textY = drawOptionalText(flow.canvas, x, textY, captionWidth, diagram.detail,
				new PdfTextStyle(META_FONT, META_SIZE, 9.5, META_COLOR));
		if (options.isShowFen()) {
			drawOptionalText(flow.canvas, x, textY, captionWidth, diagram.fen,
					new PdfTextStyle(MONO_FONT, FEN_SIZE, 8.5, META_COLOR));
		}
	}

	/**
	 * Draws wrapped text when non-blank and returns the next cursor.
	 *
	 * @param canvas target canvas
	 * @param x left edge
	 * @param y top edge
	 * @param width wrap width
	 * @param text text to draw
	 * @param style text style
	 * @return next y coordinate
	 */
	private static double drawOptionalText(Canvas canvas, double x, double y, double width, String text,
			PdfTextStyle style) {
		if (text.isBlank()) {
			return y;
		}
		return y + canvas.drawWrappedText(x, y, width, style.font(), style.size(), style.leading(), style.color(), text);
	}

	/**
	 * Converts composition figure lists into renderable diagram specs.
	 *
	 * @param composition source composition
	 * @param options rendering options
	 * @return diagram specs
	 */
	private static List<DiagramSpec> buildDiagrams(Composition composition, Options options) {
		List<String> fens = composition.getFigureFens();
		List<String> captions = composition.getFigureMovesAlgebraic();
		List<String> details = composition.getFigureMovesDetail();
		List<String> arrows = composition.getFigureArrows();

		List<DiagramSpec> diagrams = new ArrayList<>(fens.size());
		for (int i = 0; i < fens.size(); i++) {
			String fen = valueForFigure(fens, i, fens.size(), "");
			if (fen.isBlank()) {
				continue;
			}
			String caption = valueForFigure(captions, i, fens.size(), i == 0 ? "Start" : "Position " + (i + 1));
			String detail = valueForFigure(details, i, fens.size(), "");
			String arrow = valueForFigure(arrows, i, fens.size(), "");
			String svg = renderBoardSvg(fen, arrow, options);
			diagrams.add(new DiagramSpec(fen, caption, detail, svg));
		}
		return diagrams;
	}

	/**
	 * Renders one board position to SVG markup.
	 *
	 * @param fen board FEN
	 * @param arrow optional UCI arrow
	 * @param options rendering options
	 * @return SVG board markup
	 */
	private static String renderBoardSvg(String fen, String arrow, Options options) {
		Render render = new Render()
				.setPosition(new Position(fen))
				.setWhiteSideDown(options.isWhiteSideDown())
				.setShowBorder(true)
				.setShowCoordinatesOutside(true);
		if (!arrow.isBlank() && Move.isMove(arrow)) {
			render.setShowSpecialMoveHints(false);
			render.addArrow(Move.parse(arrow));
		}
		return render.renderSvg(options.getBoardPixels(), options.getBoardPixels());
	}

	/**
	 * Resolves an optional figure list entry with legacy off-by-one handling.
	 *
	 * @param list source list
	 * @param index requested figure index
	 * @param total total figure count
	 * @param fallback fallback value
	 * @return resolved value
	 */
	private static String valueForFigure(List<String> list, int index, int total, String fallback) {
		if (list == null || list.isEmpty()) {
			return fallback;
		}
		int valueIndex = figureValueIndex(list, index, total);
		if (valueIndex < 0) {
			return fallback;
		}
		String value = list.get(valueIndex);
		return value == null || value.isBlank() ? fallback : value;
	}

	/**
	 * Resolves the source index for one figure attribute.
	 *
	 * @param list source list
	 * @param index requested figure index
	 * @param total total figure count
	 * @return resolved source index, or -1 for the fallback
	 */
	private static int figureValueIndex(List<String> list, int index, int total) {
		if (list == null || list.isEmpty()) {
			return -1;
		}
		if (list.size() == total) {
			return index;
		}
		if (list.size() == total - 1) {
			return index == 0 ? -1 : index - 1;
		}
		return index < list.size() ? index : -1;
	}

	/**
	 * Returns the default PDF document title for a composition.
	 *
	 * @param composition source composition
	 * @return document title
	 */
	private static String defaultDocumentTitle(Composition composition) {
		return composition.getTitle().isBlank() ? "Chess Composition" : composition.getTitle();
	}

	/**
	 * Mutable cursor used to flow composition content across pages.
	 */
	private static final class Flow {

		/**
		 * Target PDF document.
		 */
		private final Document pdf;

		/**
		 * Composition currently being rendered.
		 */
		private final Composition composition;

		/**
		 * One-based composition number.
		 */
		private final int number;

		/**
		 * Rendering options.
		 */
		private final Options options;

		/**
		 * Page margin in points.
		 */
		private final double margin;

		/**
		 * Bottom content boundary.
		 */
		private final double bottom;

		/**
		 * Current page frame.
		 */
		private final Page page;

		/**
		 * Current page canvas.
		 */
		private final Canvas canvas;

		/**
		 * Current vertical cursor.
		 */
		private double y;

		/**
		 * Creates one flow cursor.
		 *
		 * @param pdf target PDF document
		 * @param composition composition being rendered
		 * @param number one-based composition number
		 * @param options rendering options
		 * @param page current page
		 * @param canvas current page canvas
		 * @param y current vertical cursor
		 */
		private Flow(Document pdf, Composition composition, int number, Options options, Page page,
				Canvas canvas, double y) {
			this.pdf = pdf;
			this.composition = composition;
			this.number = number;
			this.options = options;
			this.margin = options.getMargin();
			this.bottom = page.getHeight() - margin;
			this.page = page;
			this.canvas = canvas;
			this.y = y;
		}

		/**
		 * Creates the first page for a composition.
		 *
		 * @param pdf target PDF document
		 * @param composition composition to render
		 * @param number one-based composition number
		 * @param options rendering options
		 * @return initialized flow cursor
		 */
		private static Flow firstPage(Document pdf, Composition composition, int number, Options options) {
			Page page = pdf.addPage(options.getPageSize());
			Canvas canvas = page.canvas();
			double margin = options.getMargin();
			double width = page.getWidth() - margin * 2.0;

			String title = (composition.getTitle().isBlank() ? "Untitled Composition" : composition.getTitle());
			String numberedTitle = number + ". " + title;

			canvas.drawText(margin, margin, TITLE_FONT, TITLE_SIZE, TITLE_COLOR, numberedTitle);
			double y = margin + TITLE_FONT.lineHeight(TITLE_SIZE) + 8.0;
			canvas.line(margin, y, margin + width, y, RULE_COLOR, 1.0);
			y += 10.0;

			String meta = composeMeta(composition);
			if (!meta.isBlank()) {
				double consumed = canvas.drawWrappedText(margin, y, width, META_FONT, META_SIZE,
						META_FONT.lineHeight(META_SIZE),
						META_COLOR, meta);
				y += consumed + 8.0;
			}

			return new Flow(pdf, composition, number, options, page, canvas, y);
		}

		/**
		 * Opens a continuation page for the same composition.
		 *
		 * @return flow cursor on the new page
		 */
		private Flow continuedPage() {
			Page nextPage = pdf.addPage(options.getPageSize());
			Canvas nextCanvas = nextPage.canvas();
			double width = nextPage.getWidth() - margin * 2.0;
			String title = composition.getTitle().isBlank() ? "Untitled Composition" : composition.getTitle();
			nextCanvas.drawText(margin, margin, HEADING_FONT, HEADING_SIZE, ACCENT_COLOR,
					number + ". " + title + " (continued)");
			double nextY = margin + HEADING_FONT.lineHeight(HEADING_SIZE) + 6.0;
			nextCanvas.line(margin, nextY, margin + width, nextY, RULE_COLOR, 0.8);
			nextY += 10.0;
			return new Flow(pdf, composition, number, options, nextPage, nextCanvas, nextY);
		}

		/**
		 * Ensures enough vertical room remains on the current page.
		 *
		 * @param neededHeight required height
		 * @return current or continuation flow cursor
		 */
		private Flow ensure(double neededHeight) {
			if (y + neededHeight <= bottom) {
				return this;
			}
			return continuedPage();
		}

		/**
		 * Advances the vertical cursor by a fixed gap.
		 *
		 * @param points gap in points
		 * @return this flow cursor
		 */
		private Flow gap(double points) {
			y += points;
			return this;
		}

		/**
		 * Renders a section heading.
		 *
		 * @param title section title
		 * @return updated flow cursor
		 */
		private Flow section(String title) {
			Flow flow = ensure(HEADING_FONT.lineHeight(HEADING_SIZE) + 10.0);
			flow.canvas.drawText(margin, flow.y, HEADING_FONT, HEADING_SIZE, ACCENT_COLOR, title);
			flow.y += HEADING_FONT.lineHeight(HEADING_SIZE) + 6.0;
			return flow;
		}

		/**
		 * Renders a wrapped paragraph.
		 *
		 * @param text paragraph text
		 * @param compact whether compact leading should be used
		 * @return updated flow cursor
		 */
		private Flow paragraph(String text, boolean compact) {
			if (text == null || text.isBlank()) {
				return this;
			}
			double width = page.getWidth() - margin * 2.0;
			double leading = compact ? 13.0 : 14.0;
			List<String> lines = canvas.wrapLines(text, BODY_FONT, BODY_SIZE, width);
			Flow flow = this;
			for (String line : lines) {
				flow = flow.ensure(leading);
				if (!line.isBlank()) {
					flow.canvas.drawText(margin, flow.y, BODY_FONT, BODY_SIZE, BODY_COLOR, line);
				}
				flow.y += leading;
			}
			return flow.gap(4.0);
		}

		/**
		 * Builds the composition metadata line.
		 *
		 * @param composition source composition
		 * @return metadata text
		 */
		private static String composeMeta(Composition composition) {
			List<String> fields = new ArrayList<>(2);
			if (!composition.getId().isBlank()) {
				fields.add("ID: " + composition.getId());
			}
			if (!composition.getTime().isBlank()) {
				fields.add("Time: " + composition.getTime());
			}
			return String.join("   ", fields);
		}
	}

	/**
	 * Stores one wrapped-text style for diagram labels.
	 *
	 * @param font font to use
	 * @param size font size
	 * @param leading line advance
	 * @param color text color
	 * @since 2026
	 * @author Lennart A. Conrad
	 */
	private record PdfTextStyle(Font font, double size, double leading, Color color) {
	}

	/**
	 * Immutable data required to draw one diagram card.
	 */
	private static final class DiagramSpec {

		/**
		 * Diagram FEN.
		 */
		private final String fen;

		/**
		 * Diagram caption.
		 */
		private final String caption;

		/**
		 * Diagram secondary detail text.
		 */
		private final String detail;

		/**
		 * Rendered SVG markup.
		 */
		private final String svg;

		/**
		 * Creates one diagram spec.
		 *
		 * @param fen diagram FEN
		 * @param caption diagram caption
		 * @param detail secondary detail text
		 * @param svg rendered SVG markup
		 */
		private DiagramSpec(String fen, String caption, String detail, String svg) {
			this.fen = fen;
			this.caption = caption;
			this.detail = detail;
			this.svg = svg;
		}
	}
}
