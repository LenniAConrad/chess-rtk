package chess.book.render;

import static chess.book.render.Writer.*;
import static chess.book.render.WriterText.*;

import java.awt.Color;
import java.util.Locale;
import java.util.Random;

import chess.book.model.Book;
import chess.book.model.Element;
import chess.core.Move;
import chess.core.Position;
import chess.images.render.Render;
import chess.pdf.document.Canvas;
import chess.pdf.document.Font;
import chess.pdf.document.PageSize;

/**
 * SVG, board-preview, shadow, and watermark helpers for {@link Writer}.
 */
final class WriterVisuals {

	/**
	 * Utility class; prevent instantiation.
	 */
	private WriterVisuals() {
		// utility
	}

	/**
	 * Renders the puzzle starting position as SVG.
	 *
	 * @param element puzzle element
	 * @return SVG board markup
	 */
	static String renderPuzzleSvg(Element element) {
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
	static String renderSolutionSvg(SolutionInfo solution) {
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
	static String brightenSolutionBoard(String svg) {
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
	static double pagePreviewAspectRatio(Book book) {
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
	static String renderSpecialRightsSvg(String fen, boolean enPassant, boolean castling) {
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
	static void applyFreeEditionWatermark(LayoutState state) {
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
	static void drawFreeEditionWatermark(LayoutState state, PageFrame frame) {
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
	static void drawRepeatedFreeWatermarkText(Canvas canvas, double width, double height,
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
	static void drawPrimaryFreeWatermarkText(Canvas canvas, double width, double height, String watermarkId,
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
	static void drawEdgeFreeWatermarkText(Canvas canvas, double width, double height, String watermarkId,
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
	static String watermarkPageLabel(String watermarkId, int pageNumber, int totalPages) {
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
	static void drawCenteredRotatedTextOutline(Canvas canvas, double centerX, double centerY,
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
	 * @param pageWidth source page width
	 * @param pageHeight source page height
	 * @param margin required clear margin
	 * @return fitted font size
	 */
	static double fittedRotatedFontSize(Font font, String text, double preferredSize, double minimumSize,
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
	static double fittedFontSize(Font font, String text, double preferredSize, double minimumSize,
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
	 * @param fontSize font size in points
	 * @param maxWidth available width
	 * @param maxHeight available height
	 * @return true when the rotated text fits
	 */
	static boolean rotatedTextFits(Font font, String text, double fontSize, double maxWidth,
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
	 * @param pageNumber source page number
	 * @param documentKey document key used to vary the pattern between books
	 * @return SVG noise overlay
	 */
	static String renderFreeWatermarkNoiseSvg(double width, double height, int pageNumber,
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
	 * @param pageNumber source page number
	 * @param watermarkId visible watermark identifier
	 * @return SVG corner-mark overlay
	 */
	static String renderFreeWatermarkCornerSvg(double width, double height, int pageNumber,
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
	static void appendCornerMark(StringBuilder svg, double left, double top, double cell, double pitch,
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
	 * @param pageNumber source page number
	 * @param documentKey document key used to vary the pattern between books
	 * @return SVG scratch overlay
	 */
	static String renderFreeWatermarkScratchSvg(double width, double height, int pageNumber,
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
	 * @param pageNumber source page number
	 * @param documentKey document key used to vary the pattern between books
	 * @param salt layer salt
	 * @return seeded random generator
	 */
	static Random freeWatermarkRandom(int pageNumber, String documentKey, long salt) {
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
	static void drawCaptionedPagePreview(Canvas canvas, Box box, String caption, LayoutState state,
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
	static void drawCaptionedBoardSvg(Canvas canvas, double x, double y, double size, String caption,
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
	static PagePreviewGeometry pagePreviewGeometry(Book book) {
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
	static String renderPagePreviewFrameSvg(PagePreviewGeometry geometry) {
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
	static ShadowBoxGeometry shadowBoxGeometry(double contentWidth, double contentHeight,
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
	static String renderShadowSvg(ShadowBoxGeometry geometry) {
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
	static void appendShadowLayers(StringBuilder svg, double contentWidth, double contentHeight, double blur) {
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
	static void appendShadowGradientDefs(StringBuilder svg, String prefix, double contentX, double contentY,
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
	static void appendLinearGradient(StringBuilder svg, String id, double x1, double y1, double x2,
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
	 * @param radius radius in pixels
	 */
	static void appendRadialGradient(StringBuilder svg, String id, double cx, double cy, double radius) {
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
	static void appendShadowStops(StringBuilder svg) {
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
	 * @param width width in pixels
	 * @param height height in pixels
	 * @param gradientId source gradient id
	 */
	static void appendUrlRect(StringBuilder svg, double x, double y, double width, double height,
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
	static StringBuilder openSvg(double width, double height, int capacity) {
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
	static String closeSvg(StringBuilder svg) {
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
	static void appendRect(StringBuilder svg, double x, double y, double width, double height, Color color,
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
	static void appendCircle(StringBuilder svg, double x, double y, double radius, Color color,
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
	static void appendLine(StringBuilder svg, double x1, double y1, double x2, double y2, SvgStroke stroke) {
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
	static String svgNumber(double value) {
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
	static String svgColor(Color color) {
		return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * Returns the vertical height reserved by a figure and its caption block.
	 *
	 * @param imageHeight figure image height
	 * @return reserved figure block height
	 */
	static double figureBlockHeight(double imageHeight) {
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
}
