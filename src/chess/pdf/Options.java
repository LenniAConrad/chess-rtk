package chess.pdf;

import chess.pdf.document.PageSize;

/**
 * Layout and rendering options for chess PDF generation.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Options {

	/**
	 * Physical page size for generated PDFs.
	 */
	private PageSize pageSize = PageSize.A4;

	/**
	 * Uniform page margin in points.
	 */
	private double margin = 36.0;

	/**
	 * Number of diagrams placed in one row.
	 */
	private int diagramsPerRow = 2;

	/**
	 * Source SVG board size requested from the chess renderer.
	 */
	private int boardPixels = 900;

	/**
	 * Whether diagrams are oriented with White at the bottom.
	 */
	private boolean whiteSideDown = true;

	/**
	 * Whether FEN strings are printed below diagrams.
	 */
	private boolean showFen = true;

	/**
	 * Returns the PDF page size.
	 *
	 * @return page size
	 */
	public PageSize getPageSize() {
		return pageSize;
	}

	/**
	 * Sets the PDF page size.
	 *
	 * @param pageSize page size
	 * @return this options object
	 */
	public Options setPageSize(PageSize pageSize) {
		this.pageSize = pageSize == null ? PageSize.A4 : pageSize;
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
	 * @param margin margin in points
	 * @return this options object
	 */
	public Options setMargin(double margin) {
		if (margin < 12.0) {
			throw new IllegalArgumentException("margin must be at least 12 points");
		}
		this.margin = margin;
		return this;
	}

	/**
	 * Returns the number of diagrams per row.
	 *
	 * @return diagrams per row
	 */
	public int getDiagramsPerRow() {
		return diagramsPerRow;
	}

	/**
	 * Sets the number of diagrams per row.
	 *
	 * @param diagramsPerRow diagrams per row
	 * @return this options object
	 */
	public Options setDiagramsPerRow(int diagramsPerRow) {
		if (diagramsPerRow <= 0) {
			throw new IllegalArgumentException("diagrams per row must be positive");
		}
		this.diagramsPerRow = diagramsPerRow;
		return this;
	}

	/**
	 * Returns the raster resolution used when rendering board images.
	 *
	 * @return board raster size in pixels
	 */
	public int getBoardPixels() {
		return boardPixels;
	}

	/**
	 * Sets the raster resolution used when rendering board images.
	 *
	 * @param boardPixels board raster size in pixels
	 * @return this options object
	 */
	public Options setBoardPixels(int boardPixels) {
		if (boardPixels < 256) {
			throw new IllegalArgumentException("boardPixels must be at least 256");
		}
		this.boardPixels = boardPixels;
		return this;
	}

	/**
	 * Returns whether White is shown at the bottom of diagrams.
	 *
	 * @return true when White is at the bottom
	 */
	public boolean isWhiteSideDown() {
		return whiteSideDown;
	}

	/**
	 * Sets diagram orientation.
	 *
	 * @param whiteSideDown true for White at the bottom
	 * @return this options object
	 */
	public Options setWhiteSideDown(boolean whiteSideDown) {
		this.whiteSideDown = whiteSideDown;
		return this;
	}

	/**
	 * Returns whether to print FEN text under diagrams.
	 *
	 * @return true when FEN lines are shown
	 */
	public boolean isShowFen() {
		return showFen;
	}

	/**
	 * Controls whether FEN text is printed under diagrams.
	 *
	 * @param showFen true to print FEN text
	 * @return this options object
	 */
	public Options setShowFen(boolean showFen) {
		this.showFen = showFen;
		return this;
	}
}
