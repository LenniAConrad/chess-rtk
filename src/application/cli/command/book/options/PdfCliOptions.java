package application.cli.command.book.options;

import static application.cli.Constants.OPT_BLACK_DOWN;
import static application.cli.Constants.OPT_BINDING;
import static application.cli.Constants.OPT_BOARD_PIXELS;
import static application.cli.Constants.OPT_DIAGRAMS_PER_ROW;
import static application.cli.Constants.OPT_FLIP;
import static application.cli.Constants.OPT_FREE_WATERMARK;
import static application.cli.Constants.OPT_INTERIOR;
import static application.cli.Constants.OPT_NO_FEN;
import static application.cli.Constants.OPT_PAGE_SIZE;
import static application.cli.Constants.OPT_PAGES;
import static application.cli.Constants.OPT_WATERMARK;
import static application.cli.Constants.OPT_WATERMARK_ID;

import java.util.Locale;

import chess.book.cover.Binding;
import chess.book.cover.Interior;
import chess.pdf.Options;
import chess.pdf.document.PageSize;
import utility.Argv;

/**
 * Shared parsing for PDF-related CLI options.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PdfCliOptions {

	/**
	 * {@code --margin} option flag.
	 */
	public static final String OPT_MARGIN = "--margin";

	/**
	 * Utility class; prevent instantiation.
	 */
	private PdfCliOptions() {
		// utility
	}

	/**
	 * Parses a strict CLI page-size token.
	 *
	 * @param text raw page-size token
	 * @return parsed page size
	 */
	public static PageSize parsePageSize(String text) {
		if (text == null) {
			return PageSize.A4;
		}
		return switch (text.trim().toLowerCase(Locale.ROOT)) {
			case "a4" -> PageSize.A4;
			case "a5" -> PageSize.A5;
			case "letter" -> PageSize.LETTER;
			default -> throw new IllegalArgumentException("unsupported page size: " + text
					+ " (use a4, a5, or letter)");
		};
	}

	/**
	 * Direct PDF diagram layout options.
	 *
	 * @param pageSize physical page size
	 * @param diagramsPerRow number of diagrams per row
	 * @param boardPixels raster size per diagram
	 * @param whiteSideDown true when White is at the bottom
	 * @param showFen true when FEN text should be printed
	 */
	public record DiagramLayout(
			PageSize pageSize,
			int diagramsPerRow,
			int boardPixels,
			boolean whiteSideDown,
			boolean showFen
	) {

		/**
		 * Parses diagram layout flags with command-specific defaults.
		 *
		 * @param a argument parser
		 * @param defaultDiagramsPerRow default diagrams per row
		 * @param defaultBoardPixels default board raster size
		 * @return parsed layout
		 */
		public static DiagramLayout parse(Argv a, int defaultDiagramsPerRow, int defaultBoardPixels) {
			return new DiagramLayout(
					PdfCliOptions.parsePageSize(trimToNull(a.string(OPT_PAGE_SIZE))),
					a.integerOr(defaultDiagramsPerRow, OPT_DIAGRAMS_PER_ROW),
					a.integerOr(defaultBoardPixels, OPT_BOARD_PIXELS),
					!a.flag(OPT_FLIP, OPT_BLACK_DOWN),
					!a.flag(OPT_NO_FEN));
		}

		/**
		 * Converts CLI layout options into PDF writer options.
		 *
		 * @return configured PDF writer options
		 */
		public Options toPdfOptions() {
			return new Options()
					.setPageSize(pageSize)
					.setDiagramsPerRow(diagramsPerRow)
					.setBoardPixels(boardPixels)
					.setWhiteSideDown(whiteSideDown)
					.setShowFen(showFen);
		}
	}

	/**
	 * Nullable diagram-layout overrides used by manifest-driven commands.
	 *
	 * @param pageSize optional page-size override
	 * @param margin optional margin override
	 * @param diagramsPerRow optional diagrams-per-row override
	 * @param boardPixels optional board-raster override
	 * @param whiteSideDown optional board-orientation override
	 * @param showFen optional FEN visibility override
	 */
	public record DiagramLayoutOverrides(
			PageSize pageSize,
			Double margin,
			Integer diagramsPerRow,
			Integer boardPixels,
			Boolean whiteSideDown,
			Boolean showFen
	) {

		/**
		 * Parses optional diagram layout overrides.
		 *
		 * @param a argument parser
		 * @return parsed overrides
		 */
		public static DiagramLayoutOverrides parse(Argv a) {
			String pageSizeText = trimToNull(a.string(OPT_PAGE_SIZE));
			boolean blackDown = a.flag(OPT_FLIP, OPT_BLACK_DOWN);
			boolean hideFen = a.flag(OPT_NO_FEN);
			return new DiagramLayoutOverrides(
					pageSizeText == null ? null : PdfCliOptions.parsePageSize(pageSizeText),
					a.dbl(OPT_MARGIN),
					a.integer(OPT_DIAGRAMS_PER_ROW),
					a.integer(OPT_BOARD_PIXELS),
					blackDown ? Boolean.FALSE : null,
					hideFen ? Boolean.FALSE : null);
		}
	}

	/**
	 * Cover binding, paper, and optional page-count options.
	 *
	 * @param binding selected cover binding
	 * @param interior selected paper stock
	 * @param pages optional printed page-count override
	 */
	public record CoverLayout(Binding binding, Interior interior, Integer pages) {

		/**
		 * Parses cover layout options.
		 *
		 * @param a argument parser
		 * @return parsed cover layout
		 */
		public static CoverLayout parse(Argv a) {
			Integer pages = a.integer(OPT_PAGES);
			if (pages != null && pages < 0) {
				throw new IllegalArgumentException("--pages must be zero or greater");
			}
			return new CoverLayout(
					Binding.parse(a.string(OPT_BINDING)),
					Interior.parse(a.string(OPT_INTERIOR)),
					pages);
		}

		/**
		 * Returns the explicit page override, or zero for automatic inference.
		 *
		 * @return page override or zero
		 */
		public int pagesOrZero() {
			return pages == null ? 0 : pages.intValue();
		}

		/**
		 * Returns whether the caller supplied {@code --pages}.
		 *
		 * @return true when a page-count override exists
		 */
		public boolean hasPagesOverride() {
			return pages != null;
		}

		/**
		 * Converts the parsed layout to cover writer options.
		 *
		 * @param interiorPdfMetrics optional interior PDF metrics
		 * @return configured cover writer options
		 */
		public chess.book.cover.Options toCoverOptions(chess.pdf.DocumentMetrics interiorPdfMetrics) {
			return new chess.book.cover.Options()
					.setBinding(binding)
					.setInterior(interior)
					.setPages(pagesOrZero())
					.setInteriorPdfMetrics(interiorPdfMetrics);
		}
	}

	/**
	 * Free-edition watermark options.
	 *
	 * @param enabled true when watermarking is enabled
	 * @param id optional traceable watermark identifier
	 */
	public record Watermark(boolean enabled, String id) {

		/**
		 * Parses watermark flags.
		 *
		 * @param a argument parser
		 * @return parsed watermark options
		 */
		public static Watermark parse(Argv a) {
			String watermarkId = trimToNull(a.string(OPT_WATERMARK_ID));
			boolean freeWatermark = a.flag(OPT_FREE_WATERMARK, OPT_WATERMARK) || watermarkId != null;
			return new Watermark(freeWatermark, watermarkId);
		}

		/**
		 * Builds the console suffix for watermarked renders.
		 *
		 * @return status suffix
		 */
		public String suffix() {
			if (!enabled) {
				return "";
			}
			return id == null ? " (free watermarked PDF)"
					: " (free watermarked PDF, watermark ID embedded)";
		}
	}

	/**
	 * Normalizes optional strings by trimming and converting blank values to null.
	 *
	 * @param value raw value
	 * @return trimmed value or null
	 */
	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
