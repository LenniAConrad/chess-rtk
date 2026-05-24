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
	 * PDF metadata author string.
	 */
	private String documentAuthor = "chess-rtk";

	/**
	 * PDF metadata subject string.
	 */
	private String documentSubject = "Chess PDF export";

	/**
	 * PDF metadata creator string.
	 */
	private String documentCreator = "chess.pdf.Writer";

	/**
	 * PDF metadata producer string.
	 */
	private String documentProducer = "chess-rtk pdf";

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

	/**
	 * Returns the PDF metadata author string.
	 *
	 * @return author metadata
	 */
	public String getDocumentAuthor() {
		return documentAuthor;
	}

	/**
	 * Sets the PDF metadata author string.
	 *
	 * @param documentAuthor author metadata
	 * @return this options object
	 */
	public Options setDocumentAuthor(String documentAuthor) {
		this.documentAuthor = normalizeMetadata(documentAuthor, "chess-rtk");
		return this;
	}

	/**
	 * Returns the PDF metadata subject string.
	 *
	 * @return subject metadata
	 */
	public String getDocumentSubject() {
		return documentSubject;
	}

	/**
	 * Sets the PDF metadata subject string.
	 *
	 * @param documentSubject subject metadata
	 * @return this options object
	 */
	public Options setDocumentSubject(String documentSubject) {
		this.documentSubject = normalizeMetadata(documentSubject, "Chess PDF export");
		return this;
	}

	/**
	 * Returns the PDF metadata creator string.
	 *
	 * @return creator metadata
	 */
	public String getDocumentCreator() {
		return documentCreator;
	}

	/**
	 * Sets the PDF metadata creator string.
	 *
	 * @param documentCreator creator metadata
	 * @return this options object
	 */
	public Options setDocumentCreator(String documentCreator) {
		this.documentCreator = normalizeMetadata(documentCreator, "chess.pdf.Writer");
		return this;
	}

	/**
	 * Returns the PDF metadata producer string.
	 *
	 * @return producer metadata
	 */
	public String getDocumentProducer() {
		return documentProducer;
	}

	/**
	 * Sets the PDF metadata producer string.
	 *
	 * @param documentProducer producer metadata
	 * @return this options object
	 */
	public Options setDocumentProducer(String documentProducer) {
		this.documentProducer = normalizeMetadata(documentProducer, "chess-rtk pdf");
		return this;
	}

	/**
	 * Normalizes document metadata while preserving the built-in defaults.
	 *
	 * @param value raw metadata text
	 * @param fallback default value
	 * @return normalized metadata text
	 */
	private static String normalizeMetadata(String value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? fallback : trimmed;
	}
}
