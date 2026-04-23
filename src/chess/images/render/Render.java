package chess.images.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import chess.images.assets.Shapes;
import chess.images.assets.shape.SvgShapes;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.struct.Game;

/**
 * Lightweight board renderer for {@link Position} objects with optional arrows
 * and circles.
 *
 * @since 2024
 * @author Lennart A. Conrad
 */
public final class Render {

	/**
	 * Default fill color for arrows.
	 */
	private static final Color DEFAULT_ARROW_FILL = new Color(1.0f, 1.0f, 1.0f, 1.0f);
	
	/**
	 * Default outline color for arrows.
	 */
	private static final Color DEFAULT_ARROW_BORDER = new Color(0.0f, 0.0f, 0.0f, 1.0f);

	/**
	 * Default stroke for arrow outlines.
	 */
	private static final Stroke DEFAULT_ARROW_STROKE = new BasicStroke(3f);

	/**
	 * Default fill color for castling/en passant arrows (opaque gray).
	 */
	private static final Color DEFAULT_HINT_ARROW_FILL = new Color(120, 120, 120, 153);

	/**
	 * Default outline color for castling/en passant arrows (opaque gray).
	 */
	private static final Color DEFAULT_HINT_ARROW_BORDER = new Color(0, 0, 0, 0);

	/**
	 * Default arrow head height for castling/en passant arrows (in tiles).
	 */
	private static final double DEFAULT_HINT_ARROW_HEAD_HEIGHT = 0.3;

	/**
	 * Default start shortener for castling/en passant arrows (in tiles).
	 */
	private static final double DEFAULT_HINT_ARROW_START_SHORTENER = 0.25;

	/**
	 * Default end shortener for castling/en passant arrows (in tiles).
	 */
	private static final double DEFAULT_HINT_ARROW_END_SHORTENER = 0.25;
	
	/**
	 * Default fill color for hint circles.
	 */
	private static final Color DEFAULT_CIRCLE_FILL = new Color(1.0f, 1.0f, 1.0f, 0.7f);

	/**
	 * Default outline color for hint circles.
	 */
	private static final Color DEFAULT_CIRCLE_BORDER = new Color(0.0f, 0.0f, 0.0f, 1.0f);

	/**
	 * Default stroke for hint circles.
	 */
	private static final Stroke DEFAULT_CIRCLE_STROKE = new BasicStroke(2f);
	
	/**
	 * Default frame color surrounding the board. 
	 */
	private static final Color DEFAULT_FRAME = new Color(100, 100, 100);

	/**
	 * Default text color for per-square text overlays (used when no auto color applies).
	 */
	private static final Color DEFAULT_SQUARE_TEXT_COLOR = new Color(255, 255, 255, 255);

	/**
	 * Default background color for per-square text overlays (used when no auto color applies).
	 */
	private static final Color DEFAULT_SQUARE_TEXT_BACKGROUND = new Color(0, 0, 0, 170);

	/**
	 * Default border color for per-square text overlays (used when no auto color applies).
	 */
	private static final Color DEFAULT_SQUARE_TEXT_BORDER = new Color(255, 255, 255, 160);

	/**
	 * Text color used for overlays on squares occupied by White pieces when auto contrast is off.
	 * The high opacity keeps glyphs legible without overpowering the lighter board tiles.
	 */
	private static final Color DEFAULT_SQUARE_TEXT_WHITE_PIECE_TEXT = new Color(0, 0, 0, 235);

	/**
	 * Background color for White-piece text overlays when manual styling is requested.
	 * This translucent white frame makes digits stand out without obscuring the underlying square.
	 */
	private static final Color DEFAULT_SQUARE_TEXT_WHITE_PIECE_BACKGROUND = new Color(255, 255, 255, 200);

	/**
	 * Border color for White-piece overlays to visually separate them from the board.
	 * The low opacity keeps the border subtle while preserving contrast.
	 */
	private static final Color DEFAULT_SQUARE_TEXT_WHITE_PIECE_BORDER = new Color(0, 0, 0, 170);

	/**
	 * Text color used for overlays on squares occupied by Black pieces.
	 * Pure white delivers maximum contrast on the darker board sections.
	 */
	private static final Color DEFAULT_SQUARE_TEXT_BLACK_PIECE_TEXT = new Color(255, 255, 255, 255);

	/**
	 * Background color for Black-piece text overlays when explicit styling is needed.
	 * The translucent black keeps the overlay readable while allowing the board to remain visible.
	 */
	private static final Color DEFAULT_SQUARE_TEXT_BLACK_PIECE_BACKGROUND = new Color(0, 0, 0, 170);

	/**
	 * Border color for Black-piece overlays, mirroring the lighter border used elsewhere.
	 * This consistent tone helps overlay borders stay harmonious across themes.
	 */
	private static final Color DEFAULT_SQUARE_TEXT_BLACK_PIECE_BORDER = new Color(255, 255, 255, 160);

	/**
	 * Default text color for detail overlays (Times New Roman).
	 */
	private static final Color DEFAULT_DETAIL_TEXT_COLOR = new Color(140, 140, 140);

	/**
	 * Default background color for detail overlays (transparent).
	 */
	private static final Color DEFAULT_DETAIL_TEXT_BACKGROUND = new Color(0, 0, 0, 0);

	/**
	 * Default border color for detail overlays (transparent).
	 */
	private static final Color DEFAULT_DETAIL_TEXT_BORDER = new Color(0, 0, 0, 0);

	/**
	 * Default stroke for per-square text overlay background borders.
	 */
	private static final Stroke DEFAULT_SQUARE_TEXT_STROKE = new BasicStroke(1.25f);

	/**
	 * Board pixel width taken from the background asset.
	 */
	private final int boardWidth = Shapes.Board.getWidth();
	
	/**
	 * Board pixel height taken from the background asset.
	 */
	private final int boardHeight = Shapes.Board.getHeight();
	
	/**
	 * Single tile width in pixels.
	 */
	private final int tileWidth = boardWidth / 8;

	/**
	 * Single tile height in pixels.
	 */
	private final int tileHeight = boardHeight / 8;
	
	/**
	 * Thickness of the optional border in pixels.
	 */
	private final int borderThickness = Math.max(2, tileWidth / 10);

	/**
	 * Stroke used for castling/en passant arrows, scaled to the tile size.
	 */
	private final Stroke hintArrowStroke = new BasicStroke(Math.max(1f, tileWidth / 40f));

	/**
	 * Position currently being rendered.
	 */
	private Position position = new Position(Game.STANDARD_START_FEN);
	
	/**
	 * Whether White is at the bottom of the board.
	 */
	private boolean whiteSideDown = true;
	
	/**
	 * Whether a frame/border should be drawn.
	 */
	private boolean showBorder = true;

	/**
	 * Whether to draw coordinate labels inside the board.
	 */
	private boolean showCoordinates = false;

	/**
	 * Whether to draw coordinate labels outside the board image.
	 */
	private boolean showCoordinatesOutside = false;

	/**
	 * Whether to draw castling-right and en-passant hint arrows automatically.
	 */
	private boolean showSpecialMoveHints = true;

	/**
	 * Scale factor for rendering piece images (1.0 = default size).
	 */
	private double pieceScale = 1.0;

	/**
	 * Vertical pixel offset applied to pieces after scaling (negative moves up).
	 */
	private int pieceYOffset = 0;

	/** 
	 * Overlay arrows to draw.
	 */
	private final List<Arrow> arrows = new ArrayList<>();
	
	/**
	 * Overlay circles to draw
	 */
	private final List<Circle> circles = new ArrayList<>();

	/**
	 * Per-square text overlays (one per square).
	 */
	private final SquareText[] squareTexts = new SquareText[64];

	/**
	 * Maximum width (in pixels) for a single square text overlay, derived from {@link #tileWidth}.
	 */
	private final int squareTextMaxWidth = (int) (tileWidth * 0.72);

	/**
	 * Maximum height (in pixels) for a single square text overlay, derived from {@link #tileHeight}.
	 */
	private final int squareTextMaxHeight = (int) (tileHeight * 0.33);

	/**
	 * Initial font size (in pixels) used when laying out square text; clamped to a minimum for readability.
	 */
	private final int squareTextStartingFontSize = Math.max(8, (int) (tileHeight * 0.25));

	/**
	 * Initial font size (in pixels) used when laying out detail text.
	 */
	private final int detailTextStartingFontSize = Math.max(8, (int) (tileHeight * 0.22));

	/**
	 * Base font used for square text overlays (bold, sans-serif) at {@link #squareTextStartingFontSize}.
	 */
	private final Font squareTextBaseFont = new Font(Font.SANS_SERIF, Font.BOLD, squareTextStartingFontSize);

	/**
	 * Base font used for detail overlays (Times New Roman) at {@link #detailTextStartingFontSize}.
	 */
	private final Font detailTextBaseFont = new Font("Times New Roman", Font.PLAIN, detailTextStartingFontSize);

	/**
	 * Mutable style configuration used during square text rendering.
	 */
	private final SquareTextStyle squareTextStyle = new SquareTextStyle();

	/**
	 * Reusable text layout helper to avoid per-frame allocations when rendering square text.
	 */
	private final TextLayout squareTextLayout = new TextLayout();

	/**
	 * Reusable tile coordinate scratch value used during square text rendering.
	 */
	private final IntPoint squareTextTile = new IntPoint();

	/**
	 * Sets the position to render.
	 *
	 * @param position position to draw
	 * @return this renderer for chaining
	 */
	public Render setPosition(Position position) {
		if (position == null) {
			throw new IllegalArgumentException("position cannot be null");
		}
		this.position = position;
		return this;
	}

	/**
	 * Controls board orientation.
	 *
	 * @param value true for White at bottom, false for Black
	 * @return this renderer for chaining
	 */
	public Render setWhiteSideDown(boolean value) {
		this.whiteSideDown = value;
		return this;
	}

	/**
	 * Toggles drawing of the surrounding frame.
	 *
	 * @param value true to draw a frame
	 * @return this renderer for chaining
	 */
	public Render setShowBorder(boolean value) {
		this.showBorder = value;
		return this;
	}

	/**
	 * Toggles drawing of coordinate labels inside the board.
	 *
	 * @param value true to draw coordinates inside the board
	 * @return this renderer for chaining
	 */
	public Render setShowCoordinates(boolean value) {
		this.showCoordinates = value;
		return this;
	}

	/**
	 * Toggles drawing of coordinate labels outside the board.
	 *
	 * @param value true to draw coordinates outside the board
	 * @return this renderer for chaining
	 */
	public Render setShowCoordinatesOutside(boolean value) {
		this.showCoordinatesOutside = value;
		return this;
	}

	/**
	 * Toggles automatic castling-right and en-passant hint arrows.
	 *
	 * @param value true to draw special-move hints for the current position
	 * @return this renderer for chaining
	 */
	public Render setShowSpecialMoveHints(boolean value) {
		this.showSpecialMoveHints = value;
		return this;
	}

	/**
	 * Sets a scale factor and vertical offset for piece rendering.
	 *
	 * @param scale scale multiplier (1.0 = default size)
	 * @param yOffsetFraction vertical offset as a fraction of tile height (negative moves up)
	 * @return this renderer for chaining
	 */
	public Render setPieceScaleAndOffset(double scale, double yOffsetFraction) {
		if (scale <= 0.0) {
			throw new IllegalArgumentException("piece scale must be > 0");
		}
		this.pieceScale = scale;
		this.pieceYOffset = (int) Math.round(tileHeight * yOffsetFraction);
		return this;
	}

	/**
	 * Removes explicit arrows and disables automatic special-move hint arrows.
	 *
	 * @return this renderer for chaining
	 */
	public Render clearArrows() {
		arrows.clear();
		showSpecialMoveHints = false;
		return this;
	}

	/**
	 * Removes all circles.
	 *
	 * @return this renderer for chaining
	 */
	public Render clearCircles() {
		circles.clear();
		return this;
	}

	/**
	 * Removes all per-square text overlays.
	 *
	 * @return this renderer for chaining
	 */
	public Render clearSquareTexts() {
		for (int i = 0; i < squareTexts.length; i++) {
			squareTexts[i] = null;
		}
		return this;
	}

	/**
	 * Sets a small centered text overlay for a square using default styling.
	 * <p>
	 * Passing {@code null} or blank {@code text} clears the overlay for the square.
	 *
	 * @param index square index (0..63)
	 * @param text  label to draw (e.g. "+1.5")
	 * @return this renderer for chaining
	 */
	public Render setSquareText(byte index, String text) {
		int idx = toSquareIndex(index);
		if (text == null || text.isBlank()) {
			squareTexts[idx] = null;
			return this;
		}
		// Auto-colors: White pieces get dark text on light background; Black pieces get inverted.
		squareTexts[idx] = new SquareText(index, text, null, null, null, DEFAULT_SQUARE_TEXT_STROKE, null, false,
				false);
		return this;
	}

	/**
	 * Sets a square text overlay aligned to the bottom of the tile.
	 * <p>
	 * Passing {@code null} or blank {@code text} clears the overlay for the square.
	 *
	 * @param index square index (0..63)
	 * @param text  label to draw (e.g. "+1.5")
	 * @return this renderer for chaining
	 */
	public Render setSquareTextBottom(byte index, String text) {
		int idx = toSquareIndex(index);
		if (text == null || text.isBlank()) {
			squareTexts[idx] = null;
			return this;
		}
		// Auto-colors: White pieces get dark text on light background; Black pieces get inverted.
		squareTexts[idx] = new SquareText(index, text, null, null, null, DEFAULT_SQUARE_TEXT_STROKE, null, true,
				false);
		return this;
	}

	/**
	 * Sets a small centered detail overlay for a square using Times New Roman gray styling.
	 * <p>
	 * Passing {@code null} or blank {@code text} clears the overlay for the square.
	 *
	 * @param index square index (0..63)
	 * @param text  label to draw (e.g. "a", "1")
	 * @return this renderer for chaining
	 */
	public Render setSquareDetail(byte index, String text) {
		int idx = toSquareIndex(index);
		if (text == null || text.isBlank()) {
			squareTexts[idx] = null;
			return this;
		}
		squareTexts[idx] = new SquareText(index, text, DEFAULT_DETAIL_TEXT_COLOR, DEFAULT_DETAIL_TEXT_BACKGROUND,
				DEFAULT_DETAIL_TEXT_BORDER, DEFAULT_SQUARE_TEXT_STROKE, detailTextBaseFont, false, true);
		return this;
	}

	/**
	 * Sets a small centered text overlay for a square with custom styling.
	 * <p>
	 * Passing {@code null} or blank {@code text} clears the overlay for the square.
	 *
	 * @param index       square index (0..63)
	 * @param text        label to draw (e.g. "+1.5")
	 * @param textColor   text color
	 * @param background  background fill color (use alpha for transparency)
	 * @param border      background border color (use alpha for transparency)
	 * @param borderStroke background border stroke
	 * @return this renderer for chaining
	 */
	public Render setSquareText(byte index, String text, Color textColor, Color background, Color border,
			Stroke borderStroke) {
		int idx = toSquareIndex(index);
		if (text == null || text.isBlank()) {
			squareTexts[idx] = null;
			return this;
		}
		squareTexts[idx] = new SquareText(index, text, textColor, background, border, borderStroke, null, false,
				false);
		return this;
	}

	/**
	 * Adds an arrow with default styling.
	 *
	 * @param move encoded move
	 * @return this renderer for chaining
	 */
	public Render addArrow(short move) {
		return addArrow(move, DEFAULT_ARROW_BORDER, DEFAULT_ARROW_FILL, DEFAULT_ARROW_STROKE, 0.25, 0.25, 0.4);
	}

	/**
	 * Adds an arrow with custom styling.
	 *
	 * @param move           encoded move
	 * @param border         outline color
	 * @param fill           fill color
	 * @param stroke         outline stroke
	 * @param startShortener proportion of tile width trimmed from start
	 * @param endShortener   proportion of tile width trimmed from end
	 * @param headHeight     head height in tiles
	 * @return this renderer for chaining
	 */
	public Render addArrow(short move, Color border, Color fill, Stroke stroke, double startShortener,
			double endShortener, double headHeight) {
		int head = (int) (tileWidth * headHeight);
		int start = (int) (tileWidth * startShortener);
		int end = (int) (tileWidth * endShortener);
		arrows.add(new Arrow(move, head, start, end, border, fill, stroke));
		return this;
	}

	/**
	 * Adds a circle with default styling.
	 *
	 * @param index target square index
	 * @return this renderer for chaining
	 */
	public Render addCircle(byte index) {
		return addCircle(index, 0.5, DEFAULT_CIRCLE_BORDER, DEFAULT_CIRCLE_FILL, DEFAULT_CIRCLE_STROKE);
	}

	/**
	 * Adds a circle with custom styling.
	 *
	 * @param index         target square index
	 * @param diameterTiles circle diameter in tiles
	 * @param border        outline color
	 * @param fill          fill color
	 * @param stroke        outline stroke
	 * @return this renderer for chaining
	 */
	public Render addCircle(byte index, double diameterTiles, Color border, Color fill, Stroke stroke) {
		int diameter = (int) (tileWidth * diameterTiles);
		circles.add(new Circle(index, diameter, border, fill, stroke));
		return this;
	}

	/**
	 * Adds circles for every legal move from a square.
	 *
	 * @param pos       source position
	 * @param fromIndex origin square index
	 * @return this renderer for chaining
	 */
	public Render addLegalMoves(Position pos, byte fromIndex) {
		if (pos == null) {
			return this;
		}
		MoveList list = pos.legalMoves();
		for (int i = 0; i < list.size(); i++) {
			short m = list.get(i);
			if (Move.getFromIndex(m) == fromIndex) {
				addCircle(Move.getToIndex(m));
			}
		}
		return this;
	}

	/**
	 * Adds an opaque gray arrow indicating the last pawn move that enables en passant.
	 *
	 * @param pos position containing the en passant target
	 * @return this renderer for chaining
	 */
	public Render addEnPassant(Position pos) {
		if (pos == null) {
			return this;
		}
		byte enPassant = pos.enPassantSquare();
		if (enPassant == Field.NO_SQUARE) {
			return this;
		}
		if (Field.isOn6thRank(enPassant)) {
			byte from = (byte) (enPassant - 8);
			byte to = (byte) (enPassant + 8);
			addArrow(Move.of(from, to), DEFAULT_HINT_ARROW_BORDER, DEFAULT_HINT_ARROW_FILL, hintArrowStroke,
					DEFAULT_HINT_ARROW_START_SHORTENER, DEFAULT_HINT_ARROW_END_SHORTENER, DEFAULT_HINT_ARROW_HEAD_HEIGHT);
			return this;
		}
		if (Field.isOn3rdRank(enPassant)) {
			byte from = (byte) (enPassant + 8);
			byte to = (byte) (enPassant - 8);
			addArrow(Move.of(from, to), DEFAULT_HINT_ARROW_BORDER, DEFAULT_HINT_ARROW_FILL, hintArrowStroke,
					DEFAULT_HINT_ARROW_START_SHORTENER, DEFAULT_HINT_ARROW_END_SHORTENER, DEFAULT_HINT_ARROW_HEAD_HEIGHT);
		}
		return this;
	}

	/**
	 * Adds opaque gray arrows indicating castling rights for both sides.
	 *
	 * @param pos position containing castling rights
	 * @return this renderer for chaining
	 */
	public Render addCastlingRights(Position pos) {
		if (pos == null) {
			return this;
		}
		byte whiteKing = pos.kingSquare(true);
		byte blackKing = pos.kingSquare(false);
		if (whiteKing != Field.NO_SQUARE) {
			byte whiteKingside = pos.activeCastlingMoveTarget(Position.WHITE_KINGSIDE);
			if (whiteKingside != Field.NO_SQUARE) {
				addArrow(Move.of(whiteKing, whiteKingside), DEFAULT_HINT_ARROW_BORDER, DEFAULT_HINT_ARROW_FILL,
						hintArrowStroke, DEFAULT_HINT_ARROW_START_SHORTENER, DEFAULT_HINT_ARROW_END_SHORTENER,
						DEFAULT_HINT_ARROW_HEAD_HEIGHT);
			}
			byte whiteQueenside = pos.activeCastlingMoveTarget(Position.WHITE_QUEENSIDE);
			if (whiteQueenside != Field.NO_SQUARE) {
				addArrow(Move.of(whiteKing, whiteQueenside), DEFAULT_HINT_ARROW_BORDER, DEFAULT_HINT_ARROW_FILL,
						hintArrowStroke, DEFAULT_HINT_ARROW_START_SHORTENER, DEFAULT_HINT_ARROW_END_SHORTENER,
						DEFAULT_HINT_ARROW_HEAD_HEIGHT);
			}
		}
		if (blackKing != Field.NO_SQUARE) {
			byte blackKingside = pos.activeCastlingMoveTarget(Position.BLACK_KINGSIDE);
			if (blackKingside != Field.NO_SQUARE) {
				addArrow(Move.of(blackKing, blackKingside), DEFAULT_HINT_ARROW_BORDER, DEFAULT_HINT_ARROW_FILL,
						hintArrowStroke, DEFAULT_HINT_ARROW_START_SHORTENER, DEFAULT_HINT_ARROW_END_SHORTENER,
						DEFAULT_HINT_ARROW_HEAD_HEIGHT);
			}
			byte blackQueenside = pos.activeCastlingMoveTarget(Position.BLACK_QUEENSIDE);
			if (blackQueenside != Field.NO_SQUARE) {
				addArrow(Move.of(blackKing, blackQueenside), DEFAULT_HINT_ARROW_BORDER, DEFAULT_HINT_ARROW_FILL,
						hintArrowStroke, DEFAULT_HINT_ARROW_START_SHORTENER, DEFAULT_HINT_ARROW_END_SHORTENER,
						DEFAULT_HINT_ARROW_HEAD_HEIGHT);
			}
		}
		return this;
	}

	/**
	 * Renders the configured position with overlays into a new image.
	 *
	 * @return rendered board image
	 */
	public BufferedImage render() {
		RenderGeometry geometry = renderGeometry();
		return render(geometry.width, geometry.height);
	}

	/**
	 * Renders the configured position with overlays into a new image at a requested size.
	 *
	 * @param outputWidth requested output width
	 * @param outputHeight requested output height
	 * @return rendered board image
	 */
	public BufferedImage render(int outputWidth, int outputHeight) {
		return renderViewport(outputWidth, outputHeight, 0, 0, outputWidth, outputHeight);
	}

	/**
	 * Renders only a visible slice of a scaled board image.
	 *
	 * @param outputWidth full scaled output width
	 * @param outputHeight full scaled output height
	 * @param sourceX x coordinate of the visible slice within the scaled output
	 * @param sourceY y coordinate of the visible slice within the scaled output
	 * @param viewportWidth visible slice width
	 * @param viewportHeight visible slice height
	 * @return rendered viewport image
	 */
	public BufferedImage renderViewport(int outputWidth, int outputHeight, int sourceX, int sourceY,
			int viewportWidth, int viewportHeight) {
		if (outputWidth <= 0 || outputHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
			throw new IllegalArgumentException("render size must be positive");
		}

		RenderGeometry geometry = renderGeometry();
		BufferedImage img = new BufferedImage(viewportWidth, viewportHeight, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.translate(-sourceX, -sourceY);
		g.scale(outputWidth / (double) geometry.width, outputHeight / (double) geometry.height);
		paintToGraphics(g, geometry);
		g.dispose();
		return img;
	}

	/**
	 * Paints the current board state into a graphics context using native renderer coordinates.
	 *
	 * @param g graphics context
	 * @param geometry render geometry
	 */
	private void paintToGraphics(Graphics2D g, RenderGeometry geometry) {
		if (showBorder) {
			g.setPaint(DEFAULT_FRAME);
			if (showCoordinatesOutside) {
				int frameX = geometry.boardX - borderThickness;
				int frameY = geometry.boardY - borderThickness;
				int frameW = boardWidth + borderThickness * 2;
				int frameH = boardHeight + borderThickness * 2;
				g.fillRect(frameX, frameY, frameW, frameH);
			} else {
				g.fillRect(0, 0, geometry.width, geometry.height);
			}
		}

		Shapes.drawBoard(g, geometry.boardX, geometry.boardY, boardWidth, boardHeight);
		drawSquareTexts(g, geometry.boardX, geometry.boardY, true);
		drawCoordinates(g, geometry.boardX, geometry.boardY);
		drawPieces(g, geometry.boardX, geometry.boardY);
		drawCircles(g, geometry.boardX, geometry.boardY);
		drawSpecialMoveHints(g, geometry.boardX, geometry.boardY);
		drawArrows(g, geometry.boardX, geometry.boardY);
		drawSquareTexts(g, geometry.boardX, geometry.boardY, false);
	}

	/**
	 * Renders the configured position with overlays into an SVG document.
	 *
	 * @return rendered board SVG
	 */
	public String renderSvg() {
		RenderGeometry geometry = renderGeometry();
		return renderSvg(geometry.width, geometry.height);
	}

	/**
	 * Renders the configured position with overlays into an SVG document.
	 *
	 * <p>The requested width and height are written as SVG presentation dimensions;
	 * drawing coordinates remain in the native viewBox so the output stays vector
	 * scalable.</p>
	 *
	 * @param outputWidth requested SVG width
	 * @param outputHeight requested SVG height
	 * @return rendered board SVG
	 */
	public String renderSvg(int outputWidth, int outputHeight) {
		if (outputWidth <= 0 || outputHeight <= 0) {
			throw new IllegalArgumentException("render size must be positive");
		}

		RenderGeometry geometry = renderGeometry();
		StringBuilder svg = new StringBuilder(192_000);
		svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
				.append(geometry.width).append(' ').append(geometry.height).append("\" width=\"")
				.append(outputWidth).append("\" height=\"").append(outputHeight)
				.append("\" role=\"img\" aria-labelledby=\"title\">\n")
				.append("  <title id=\"title\">Chess position</title>\n");

		if (showBorder) {
			appendFrameSvg(svg, geometry);
		}
		appendBoardSvg(svg, geometry.boardX, geometry.boardY);
		appendSquareTextsSvg(svg, geometry.boardX, geometry.boardY, true);
		appendCoordinatesSvg(svg, geometry.boardX, geometry.boardY);
		appendPiecesSvg(svg, geometry.boardX, geometry.boardY);
		appendCirclesSvg(svg, geometry.boardX, geometry.boardY);
		appendSpecialMoveHintsSvg(svg, geometry.boardX, geometry.boardY);
		appendArrowsSvg(svg, geometry.boardX, geometry.boardY);
		appendSquareTextsSvg(svg, geometry.boardX, geometry.boardY, false);
		svg.append("</svg>\n");
		return svg.toString();
	}

	/**
	 * Returns the native rendered width before any external scaling.
	 *
	 * @return native rendered width
	 */
	public int renderedWidth() {
		return renderGeometry().width;
	}

	/**
	 * Returns the native rendered height before any external scaling.
	 *
	 * @return native rendered height
	 */
	public int renderedHeight() {
		return renderGeometry().height;
	}

	/**
	 * Computes the shared raster/SVG output geometry for the current renderer state.
	 *
	 * @return render dimensions and board origin
	 */
	private RenderGeometry renderGeometry() {
		int coordinatePadding = showCoordinatesOutside ? coordinatePadding() : 0;
		boolean outside = showCoordinatesOutside;
		int borderPadding = (!outside && showBorder) ? borderThickness * 2 : 0;
		int width = boardWidth + borderPadding + coordinatePadding * 2;
		int height = boardHeight + borderPadding + coordinatePadding * 2;
		int offset = showBorder ? borderThickness : 0;
		int boardX = outside ? coordinatePadding : offset;
		int boardY = outside ? coordinatePadding : offset;
		return new RenderGeometry(width, height, boardX, boardY);
	}

	/**
	 * Appends the optional frame/background rectangle to an SVG document.
	 *
	 * @param svg SVG builder
	 * @param geometry render geometry
	 */
	private void appendFrameSvg(StringBuilder svg, RenderGeometry geometry) {
		int frameX;
		int frameY;
		int frameW;
		int frameH;
		if (showCoordinatesOutside) {
			frameX = geometry.boardX - borderThickness;
			frameY = geometry.boardY - borderThickness;
			frameW = boardWidth + borderThickness * 2;
			frameH = boardHeight + borderThickness * 2;
		} else {
			frameX = 0;
			frameY = 0;
			frameW = geometry.width;
			frameH = geometry.height;
		}
		svg.append("  <rect x=\"").append(frameX).append("\" y=\"").append(frameY)
				.append("\" width=\"").append(frameW).append("\" height=\"").append(frameH).append("\"");
		appendColorAttribute(svg, "fill", DEFAULT_FRAME);
		svg.append("/>\n");
	}

	/**
	 * Appends the embedded vector chessboard to an SVG document.
	 *
	 * @param svg SVG builder
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private static void appendBoardSvg(StringBuilder svg, int boardX, int boardY) {
		svg.append("  <g transform=\"translate(").append(boardX).append(' ').append(boardY).append(")\">\n");
		appendEmbeddedSvgBody(svg, SvgShapes.board(), "    ");
		svg.append("  </g>\n");
	}

	/**
	 * Appends all pieces as embedded SVG groups.
	 *
	 * @param svg SVG builder
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void appendPiecesSvg(StringBuilder svg, int boardX, int boardY) {
		byte[] board = position.getBoard();
		boolean scaled = pieceScale != 1.0 || pieceYOffset != 0;
		for (int i = 0; i < board.length; i++) {
			String pieceSvg = svgForPiece(board[i]);
			if (pieceSvg == null) {
				continue;
			}
			int x = whiteSideDown ? Field.getX((byte) i) : Field.getXInverted((byte) i);
			int y = whiteSideDown ? Field.getYInverted((byte) i) : Field.getY((byte) i);
			int tileX = boardX + x * tileWidth;
			int tileY = boardY + y * tileHeight;
			int drawW = tileWidth;
			int drawH = tileHeight;
			int drawX = tileX;
			int drawY = tileY;
			if (scaled) {
				drawW = Math.max(1, (int) Math.round(tileWidth * pieceScale));
				drawH = Math.max(1, (int) Math.round(tileHeight * pieceScale));
				drawX = tileX + (tileWidth - drawW) / 2;
				drawY = tileY + (tileHeight - drawH) / 2 + pieceYOffset;
			}

			svg.append("  <g transform=\"translate(").append(drawX).append(' ').append(drawY).append(')');
			double scaleX = drawW / 200.0;
			double scaleY = drawH / 200.0;
			if (scaleX != 1.0 || scaleY != 1.0) {
				svg.append(" scale(");
				appendNumber(svg, scaleX);
				svg.append(' ');
				appendNumber(svg, scaleY);
				svg.append(')');
			}
			svg.append("\">\n");
			appendEmbeddedSvgBody(svg, pieceSvg, "    ");
			svg.append("  </g>\n");
		}
	}

	/**
	 * Appends circle overlays to an SVG document.
	 *
	 * @param svg SVG builder
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void appendCirclesSvg(StringBuilder svg, int boardX, int boardY) {
		for (Circle c : circles) {
			int x = whiteSideDown ? Field.getX(c.index) : Field.getXInverted(c.index);
			int y = whiteSideDown ? Field.getYInverted(c.index) : Field.getY(c.index);
			int px = boardX + x * tileWidth + (tileWidth - c.diameter) / 2;
			int py = boardY + y * tileHeight + (tileHeight - c.diameter) / 2;
			double radius = c.diameter / 2.0;
			svg.append("  <circle cx=\"");
			appendNumber(svg, px + radius);
			svg.append("\" cy=\"");
			appendNumber(svg, py + radius);
			svg.append("\" r=\"");
			appendNumber(svg, radius);
			svg.append('"');
			appendColorAttribute(svg, "fill", c.fill);
			appendColorAttribute(svg, "stroke", c.border);
			svg.append(" stroke-width=\"");
			appendNumber(svg, strokeWidth(c.stroke));
			svg.append("\"/>\n");
		}
	}

	/**
	 * Appends arrow overlays to an SVG document.
	 *
	 * @param svg SVG builder
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void appendArrowsSvg(StringBuilder svg, int boardX, int boardY) {
		for (Arrow arrow : arrows) {
			appendArrowSvg(svg, arrow, boardX, boardY);
		}
	}

	/**
	 * Appends automatic special-move hint arrows to an SVG document.
	 *
	 * @param svg SVG builder
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void appendSpecialMoveHintsSvg(StringBuilder svg, int boardX, int boardY) {
		for (Arrow arrow : specialMoveHintArrows()) {
			appendArrowSvg(svg, arrow, boardX, boardY);
		}
	}

	/**
	 * Appends one arrow overlay to an SVG document.
	 *
	 * @param svg SVG builder
	 * @param arrow arrow to append
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void appendArrowSvg(StringBuilder svg, Arrow arrow, int boardX, int boardY) {
			Polygon poly = arrow.polygon(boardX, boardY, tileWidth, tileHeight, whiteSideDown);
			if (poly.npoints <= 0) {
				return;
			}
			svg.append("  <polygon points=\"");
			for (int i = 0; i < poly.npoints; i++) {
				if (i > 0) {
					svg.append(' ');
				}
				svg.append(poly.xpoints[i]).append(',').append(poly.ypoints[i]);
			}
			svg.append('"');
			appendColorAttribute(svg, "fill", arrow.fillColor);
			appendColorAttribute(svg, "stroke", arrow.borderColor);
			svg.append(" stroke-linejoin=\"round\" stroke-width=\"");
			appendNumber(svg, strokeWidth(arrow.stroke));
			svg.append("\"/>\n");
	}

	/**
	 * Appends per-square text overlays to an SVG document.
	 *
	 * @param svg SVG builder
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void appendSquareTextsSvg(StringBuilder svg, int boardX, int boardY, boolean details) {
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scratch.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		SquareTextStyle style = squareTextStyle;
		TextLayout layout = squareTextLayout;
		IntPoint tile = squareTextTile;

		for (SquareText label : squareTexts) {
			if (!hasVisibleText(label) || label.detail != details) {
				continue;
			}
			int boardIndex = toSquareIndex(label.index);
			byte piece = position.getBoard()[boardIndex];
			resolveSquareTextStyle(label, piece, style);
			tileOrigin(label.index, boardX, boardY, tile);
			double maxWidthScale = label.bottomAligned ? 0.75 : 1.0;
			double maxHeightScale = label.bottomAligned ? 0.75 : 1.0;
			fitSquareTextFont(g, label.text, label.baseFont, layout, maxWidthScale, maxHeightScale);
			appendSquareTextBoxAndTextSvg(svg, label.text, tile.x, tile.y, style, layout, label.bottomAligned);
		}

		g.dispose();
	}

	/**
	 * Appends a single square-text background and glyph to an SVG document.
	 *
	 * @param svg SVG builder
	 * @param text label text
	 * @param tileX tile origin x coordinate
	 * @param tileY tile origin y coordinate
	 * @param style resolved style colors and stroke
	 * @param layout layout metrics
	 * @param bottomAligned whether to anchor the box to the bottom of the tile
	 */
	private void appendSquareTextBoxAndTextSvg(StringBuilder svg, String text, int tileX, int tileY,
			SquareTextStyle style, TextLayout layout, boolean bottomAligned) {
		int padX = Math.max(2, layout.fontSize / 4);
		int padY = padX;
		if (bottomAligned) {
			padX = Math.max(1, layout.fontSize / 5);
			padY = padX;
		}

		int boxWidth = Math.min(tileWidth, layout.textWidth + padX * 2);
		int boxHeight = Math.min(tileHeight, layout.textHeight + padY * 2);
		int boxX = tileX + (tileWidth - boxWidth) / 2;
		int boxY = tileY + (tileHeight - boxHeight) / 2;
		if (bottomAligned) {
			int bottomPad = Math.max(2, layout.fontSize / 6);
			boxY = tileY + tileHeight - boxHeight - bottomPad;
		}
		int arc = Math.max(4, boxHeight / 2);

		svg.append("  <rect x=\"").append(boxX).append("\" y=\"").append(boxY)
				.append("\" width=\"").append(boxWidth).append("\" height=\"").append(boxHeight)
				.append("\" rx=\"").append(Math.max(1, arc / 2)).append("\" ry=\"")
				.append(Math.max(1, arc / 2)).append('"');
		appendColorAttribute(svg, "fill", style.background);
		if (style.border != null) {
			appendColorAttribute(svg, "stroke", style.border);
			svg.append(" stroke-width=\"");
			appendNumber(svg, strokeWidth(style.borderStroke));
			svg.append('"');
		}
		svg.append("/>\n");

		int textX = boxX + (boxWidth - layout.textWidth) / 2;
		int textY = boxY + (boxHeight - layout.textHeight) / 2 + layout.fm.getAscent();
		svg.append("  <text x=\"").append(textX).append("\" y=\"").append(textY).append('"');
		appendFontAttributes(svg, layout.font);
		appendColorAttribute(svg, "fill", style.textColor);
		svg.append('>').append(escapeText(text)).append("</text>\n");
	}

	/**
	 * Appends coordinate labels to an SVG document.
	 *
	 * @param svg SVG builder
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void appendCoordinatesSvg(StringBuilder svg, int boardX, int boardY) {
		if (!showCoordinates && !showCoordinatesOutside) {
			return;
		}

		int gutter = showBorder ? borderThickness : 0;
		int fontSize = Math.max(10, (int) Math.round(tileHeight * 0.22));
		boolean useGutter = gutter >= fontSize + 4;
		if (showCoordinatesOutside) {
			fontSize = Math.max(10, (int) Math.round(tileHeight * 0.30));
		}

		int fontStyle = showCoordinatesOutside ? Font.BOLD : Font.PLAIN;
		Font font = new Font("Times New Roman", fontStyle, fontSize);
		Color detailColor = showCoordinatesOutside ? DEFAULT_FRAME : DEFAULT_DETAIL_TEXT_COLOR;

		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scratch.createGraphics();
		FontMetrics fm = g.getFontMetrics(font);

		svg.append("  <g");
		appendFontAttributes(svg, font);
		appendColorAttribute(svg, "fill", detailColor);
		svg.append(">\n");
		if (showCoordinatesOutside) {
			appendOutsideCoordinatesSvg(svg, fm, boardX, boardY);
		} else if (useGutter) {
			appendCoordinateGuttersSvg(svg, fm, boardX, boardY, gutter);
		} else {
			appendInlineCoordinatesSvg(svg, fm, boardX, boardY);
		}
		svg.append("  </g>\n");

		g.dispose();
	}

	/**
	 * Appends file letters and rank numbers inside the border gutter.
	 *
	 * @param svg SVG builder
	 * @param fm font metrics for sizing glyphs
	 * @param boardX left edge of the board
	 * @param boardY top edge of the board
	 * @param gutter width of the gutter area in pixels
	 */
	private void appendCoordinateGuttersSvg(StringBuilder svg, FontMetrics fm, int boardX, int boardY, int gutter) {
		int topY = boardY - gutter;
		int bottomY = boardY + boardHeight;
		int yTop = topY + (gutter - fm.getHeight()) / 2 + fm.getAscent();
		int yBottom = bottomY + (gutter - fm.getHeight()) / 2 + fm.getAscent();

		for (int file = 0; file < 8; file++) {
			char fileChar = whiteSideDown ? (char) ('a' + file) : (char) ('h' - file);
			String s = String.valueOf(fileChar);
			int cx = boardX + file * tileWidth + tileWidth / 2;
			int x = cx - fm.stringWidth(s) / 2;
			appendTextElement(svg, "    ", x, yTop, s);
			appendTextElement(svg, "    ", x, yBottom, s);
		}

		for (int rank = 0; rank < 8; rank++) {
			int label = whiteSideDown ? 8 - rank : 1 + rank;
			String s = String.valueOf(label);
			int cy = boardY + rank * tileHeight + tileHeight / 2;
			int y = cy + fm.getAscent() / 2 - 1;

			int leftX = boardX - gutter;
			int rightX = boardX + boardWidth;
			int xLeft = leftX + (gutter - fm.stringWidth(s)) / 2;
			int xRight = rightX + (gutter - fm.stringWidth(s)) / 2;
			appendTextElement(svg, "    ", xLeft, y, s);
			appendTextElement(svg, "    ", xRight, y, s);
		}
	}

	/**
	 * Appends coordinate labels over the edge squares themselves.
	 *
	 * @param svg SVG builder
	 * @param fm font metrics for sizing glyphs
	 * @param boardX left edge of the board
	 * @param boardY top edge of the board
	 */
	private void appendInlineCoordinatesSvg(StringBuilder svg, FontMetrics fm, int boardX, int boardY) {
		int pad = Math.max(2, fm.getAscent() / 5);
		int bottomRow = 7;

		for (int file = 0; file < 8; file++) {
			char fileChar = whiteSideDown ? (char) ('a' + file) : (char) ('h' - file);
			String s = String.valueOf(fileChar);
			int x = boardX + file * tileWidth + tileWidth - pad - fm.stringWidth(s);
			int y = boardY + bottomRow * tileHeight + tileHeight - pad - fm.getDescent();
			appendTextElement(svg, "    ", x, y, s);
		}

		for (int rank = 0; rank < 8; rank++) {
			int label = whiteSideDown ? 8 - rank : 1 + rank;
			String s = String.valueOf(label);
			int x = boardX + pad;
			int y = boardY + rank * tileHeight + pad + fm.getAscent();
			appendTextElement(svg, "    ", x, y, s);
		}
	}

	/**
	 * Appends coordinate labels outside the board area when requested.
	 *
	 * @param svg SVG builder
	 * @param fm font metrics for sizing glyphs
	 * @param boardX left edge of the board
	 * @param boardY top edge of the board
	 */
	private void appendOutsideCoordinatesSvg(StringBuilder svg, FontMetrics fm, int boardX, int boardY) {
		double halfFont = fm.getFont().getSize() / 2.0;
		int halfTileW = tileWidth / 2;
		int halfTileH = tileHeight / 2;

		if (whiteSideDown) {
			for (int i = 0; i < 8; i++) {
				String s = Integer.toString(i + 1);
				int y = (int) (boardY + tileHeight * (7 - i) + halfFont + halfTileH);
				appendTextElement(svg, "    ",
						(int) (boardWidth + boardX + halfTileW - (fm.stringWidth(s) / 2.0)),
						y,
						s);
				appendTextElement(svg, "    ",
						(int) (boardX - halfTileW - (fm.stringWidth(s) / 2.0)),
						y,
						s);

				s = Character.toString((char) ('a' + i));
				int x = (int) (boardX + (tileWidth * i) + halfTileW - (fm.stringWidth(s) / 2.0));
				appendTextElement(svg, "    ", x, (int) (boardHeight + boardY + halfFont + halfTileH), s);
				appendTextElement(svg, "    ", x, (int) (boardY + halfFont - halfTileH), s);
			}
			return;
		}

		for (int i = 0; i < 8; i++) {
			String s = Integer.toString(i + 1);
			int y = (int) (boardY + tileHeight * i + halfFont + halfTileH);
			appendTextElement(svg, "    ",
					(int) (boardWidth + boardX + halfTileW - (fm.stringWidth(s) / 2.0)),
					y,
					s);
			appendTextElement(svg, "    ",
					(int) (boardX - halfTileW - (fm.stringWidth(s) / 2.0)),
					y,
					s);

			s = Character.toString((char) ('a' + i));
			int x = (int) (boardX + (tileWidth * (7 - i)) + halfTileW - (fm.stringWidth(s) / 2.0));
			appendTextElement(svg, "    ", x, (int) (boardHeight + boardY + halfFont + halfTileH), s);
			appendTextElement(svg, "    ", x, (int) (boardY + halfFont - halfTileH), s);
		}
	}

	/**
	 * Draws pieces to the board.
	 *
	 * @param g      graphics context
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void drawPieces(Graphics2D g, int boardX, int boardY) {
		byte[] board = position.getBoard();
		for (int i = 0; i < board.length; i++) {
			int x = whiteSideDown ? Field.getX((byte) i) : Field.getXInverted((byte) i);
			int y = whiteSideDown ? Field.getYInverted((byte) i) : Field.getY((byte) i);
			byte piece = board[i];
			if (piece != Piece.EMPTY) {
				int tileX = boardX + x * tileWidth;
				int tileY = boardY + y * tileHeight;
				int drawW = Math.max(1, (int) Math.round(tileWidth * pieceScale));
				int drawH = Math.max(1, (int) Math.round(tileHeight * pieceScale));
				int drawX = tileX + (tileWidth - drawW) / 2;
				int drawY = tileY + (tileHeight - drawH) / 2 + pieceYOffset;
				Shapes.drawPiece(piece, g, drawX, drawY, drawW, drawH);
			}
		}
	}

	/**
	 * Draws circles overlaying target squares.
	 *
	 * @param g      graphics context
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void drawCircles(Graphics2D g, int boardX, int boardY) {
		for (Circle c : circles) {
			int x = whiteSideDown ? Field.getX(c.index) : Field.getXInverted(c.index);
			int y = whiteSideDown ? Field.getYInverted(c.index) : Field.getY(c.index);
			int px = boardX + x * tileWidth + (tileWidth - c.diameter) / 2;
			int py = boardY + y * tileHeight + (tileHeight - c.diameter) / 2;
			g.setStroke(c.stroke);
			g.setPaint(c.fill);
			g.fillOval(px, py, c.diameter, c.diameter);
			g.setPaint(c.border);
			g.drawOval(px, py, c.diameter, c.diameter);
		}
	}

	/**
	 * Draws arrows overlaying the board.
	 *
	 * @param g      graphics context
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void drawArrows(Graphics2D g, int boardX, int boardY) {
		for (Arrow arrow : arrows) {
			drawArrow(g, arrow, boardX, boardY);
		}
	}

	/**
	 * Draws automatic special-move hint arrows.
	 *
	 * @param g graphics context
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void drawSpecialMoveHints(Graphics2D g, int boardX, int boardY) {
		for (Arrow arrow : specialMoveHintArrows()) {
			drawArrow(g, arrow, boardX, boardY);
		}
	}

	/**
	 * Draws one arrow overlaying the board.
	 *
	 * @param g graphics context
	 * @param arrow arrow to draw
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void drawArrow(Graphics2D g, Arrow arrow, int boardX, int boardY) {
		Polygon poly = arrow.polygon(boardX, boardY, tileWidth, tileHeight, whiteSideDown);
		g.setStroke(arrow.stroke);
		g.setPaint(arrow.fillColor);
		g.fillPolygon(poly);
		g.setPaint(arrow.borderColor);
		g.drawPolygon(poly);
	}

	/**
	 * Builds the implicit special-move hint arrows for the current position.
	 *
	 * @return hint arrows, or an empty list when disabled
	 */
	private List<Arrow> specialMoveHintArrows() {
		if (!showSpecialMoveHints) {
			return List.of();
		}
		List<Arrow> hints = new ArrayList<>(5);
		addEnPassantHint(position, hints);
		addCastlingRightHints(position, hints);
		return hints;
	}

	/**
	 * Appends an en-passant hint arrow when the position contains an en-passant
	 * target square.
	 *
	 * @param pos source position
	 * @param target target arrow list
	 */
	private void addEnPassantHint(Position pos, List<Arrow> target) {
		if (pos == null) {
			return;
		}
		byte enPassant = pos.enPassantSquare();
		if (enPassant == Field.NO_SQUARE) {
			return;
		}
		if (Field.isOn6thRank(enPassant)) {
			target.add(hintArrow(Move.of((byte) (enPassant - 8), (byte) (enPassant + 8))));
			return;
		}
		if (Field.isOn3rdRank(enPassant)) {
			target.add(hintArrow(Move.of((byte) (enPassant + 8), (byte) (enPassant - 8))));
		}
	}

	/**
	 * Appends castling-right hint arrows for both sides.
	 *
	 * @param pos source position
	 * @param target target arrow list
	 */
	private void addCastlingRightHints(Position pos, List<Arrow> target) {
		if (pos == null) {
			return;
		}
		byte whiteKing = pos.kingSquare(true);
		byte blackKing = pos.kingSquare(false);
		if (whiteKing != Field.NO_SQUARE) {
			addCastlingRightHint(whiteKing, pos.activeCastlingMoveTarget(Position.WHITE_KINGSIDE), target);
			addCastlingRightHint(whiteKing, pos.activeCastlingMoveTarget(Position.WHITE_QUEENSIDE), target);
		}
		if (blackKing != Field.NO_SQUARE) {
			addCastlingRightHint(blackKing, pos.activeCastlingMoveTarget(Position.BLACK_KINGSIDE), target);
			addCastlingRightHint(blackKing, pos.activeCastlingMoveTarget(Position.BLACK_QUEENSIDE), target);
		}
	}

	/**
	 * Appends one castling-right hint arrow when the castling target is present.
	 *
	 * @param king king square
	 * @param targetSquare castling target square
	 * @param target target arrow list
	 */
	private void addCastlingRightHint(byte king, byte targetSquare, List<Arrow> target) {
		if (targetSquare != Field.NO_SQUARE) {
			target.add(hintArrow(Move.of(king, targetSquare)));
		}
	}

	/**
	 * Creates a default special-move hint arrow.
	 *
	 * @param move encoded move
	 * @return hint arrow
	 */
	private Arrow hintArrow(short move) {
		int head = (int) (tileWidth * DEFAULT_HINT_ARROW_HEAD_HEIGHT);
		int start = (int) (tileWidth * DEFAULT_HINT_ARROW_START_SHORTENER);
		int end = (int) (tileWidth * DEFAULT_HINT_ARROW_END_SHORTENER);
		return new Arrow(move, head, start, end, DEFAULT_HINT_ARROW_BORDER, DEFAULT_HINT_ARROW_FILL, hintArrowStroke);
	}

	/**
	 * Draws per-square text overlays centered in each tile, with a small background box.
	 *
	 * @param g      graphics context
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void drawSquareTexts(Graphics2D g, int boardX, int boardY, boolean details) {
		Font previousFont = g.getFont();
		Stroke previousStroke = g.getStroke();
		Object prevTextAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D metrics = scratch.createGraphics();
		metrics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		SquareTextStyle style = squareTextStyle;
		TextLayout layout = squareTextLayout;
		IntPoint tile = squareTextTile;

		for (SquareText label : squareTexts) {
			if (!hasVisibleText(label) || label.detail != details) {
				continue;
			}
			drawSquareText(g, metrics, boardX, boardY, label, style, layout, tile);
		}

		metrics.dispose();
		g.setFont(previousFont);
		g.setStroke(previousStroke);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, prevTextAA);
	}

		/**
		 * Determines whether the provided square label contains text worth drawing.
		 *
		 * <p>A label is visible when it is not {@code null} and contains non-whitespace
		 * characters.</p>
		 *
		 * @param label label to inspect
		 * @return {@code true} when the label has non-blank text
		 */
		private static boolean hasVisibleText(SquareText label) {
			return label != null && label.text != null && !label.text.isBlank();
		}

		/**
		 * Renders a single square text item with its background box.
		 *
		 * <p>The method resolves styling, computes the tile origin, sizes the font,
		 * and draws the background plus glyph in one pass.</p>
		 *
		 * @param g graphics context for drawing
		 * @param metrics graphics context used only for unscaled font metrics
		 * @param boardX board origin x coordinate
		 * @param boardY board origin y coordinate
		 * @param label square text label descriptor
		 * @param style reusable style object to write resolved colors into
		 * @param layout reusable layout buffer for font metrics
		 * @param tile reusable point used for tile coordinates
		 */
		private void drawSquareText(Graphics2D g, Graphics2D metrics, int boardX, int boardY, SquareText label,
				SquareTextStyle style, TextLayout layout, IntPoint tile) {
			String text = label.text;
			int boardIndex = toSquareIndex(label.index);
			byte piece = position.getBoard()[boardIndex];

		resolveSquareTextStyle(label, piece, style);
		tileOrigin(label.index, boardX, boardY, tile);
		double maxWidthScale = label.bottomAligned ? 0.75 : 1.0;
		double maxHeightScale = label.bottomAligned ? 0.75 : 1.0;
		fitSquareTextFont(metrics, text, label.baseFont, layout, maxWidthScale, maxHeightScale);
		drawSquareTextBoxAndText(g, text, tile.x, tile.y, style, layout, label.bottomAligned);
	}

	/**
	 * Draws rank/file coordinate labels either in the border gutter or inside edge squares.
	 *
	 * @param g      graphics context
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private void drawCoordinates(Graphics2D g, int boardX, int boardY) {
		if (!showCoordinates && !showCoordinatesOutside) {
			return;
		}

		int gutter = showBorder ? borderThickness : 0;
		int fontSize = Math.max(10, (int) Math.round(tileHeight * 0.22));
		boolean useGutter = gutter >= fontSize + 4;

		Font previousFont = g.getFont();
		Object prevTextAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		if (showCoordinatesOutside) {
			fontSize = Math.max(10, (int) Math.round(tileHeight * 0.30));
		}

		int fontStyle = showCoordinatesOutside ? Font.BOLD : Font.PLAIN;
		Font font = new Font("Times New Roman", fontStyle, fontSize);
		g.setFont(font);
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D metrics = scratch.createGraphics();
		FontMetrics fm = metrics.getFontMetrics(font);
		Color detailColor = showCoordinatesOutside ? DEFAULT_FRAME : DEFAULT_DETAIL_TEXT_COLOR;
		g.setPaint(detailColor);

		if (showCoordinatesOutside) {
			drawOutsideCoordinates(g, fm, boardX, boardY);
		} else if (useGutter) {
			drawCoordinateGutters(g, fm, boardX, boardY, gutter);
		} else {
			drawInlineCoordinates(g, fm, boardX, boardY);
		}

		metrics.dispose();
		g.setFont(previousFont);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, prevTextAA);
	}

		/**
		 * Draws file letters and rank numbers inside the border gutter.
		 *
		 * @param g graphics context to draw into
		 * @param fm font metrics for the current font
		 * @param boardX left edge of the board
		 * @param boardY top edge of the board
		 * @param gutter width of the gutter area in pixels
		 */
		private void drawCoordinateGutters(Graphics2D g, FontMetrics fm, int boardX, int boardY, int gutter) {
		int topY = boardY - gutter;
		int bottomY = boardY + boardHeight;
		int yTop = topY + (gutter - fm.getHeight()) / 2 + fm.getAscent();
		int yBottom = bottomY + (gutter - fm.getHeight()) / 2 + fm.getAscent();

		for (int file = 0; file < 8; file++) {
			char fileChar = whiteSideDown ? (char) ('a' + file) : (char) ('h' - file);
			String s = String.valueOf(fileChar);
			int cx = boardX + file * tileWidth + tileWidth / 2;
			int x = cx - fm.stringWidth(s) / 2;
			g.drawString(s, x, yTop);
			g.drawString(s, x, yBottom);
		}

		for (int rank = 0; rank < 8; rank++) {
			int label = whiteSideDown ? 8 - rank : 1 + rank;
			String s = String.valueOf(label);
			int cy = boardY + rank * tileHeight + tileHeight / 2;
			int y = cy + fm.getAscent() / 2 - 1;

			int leftX = boardX - gutter;
			int rightX = boardX + boardWidth;
			int xLeft = leftX + (gutter - fm.stringWidth(s)) / 2;
			int xRight = rightX + (gutter - fm.stringWidth(s)) / 2;
			g.drawString(s, xLeft, y);
			g.drawString(s, xRight, y);
		}
	}

		/**
		 * Draws coordinate labels over the edge squares themselves.
		 *
		 * @param g graphics context to draw into
		 * @param fm font metrics for label sizing
		 * @param boardX left edge of the board
		 * @param boardY top edge of the board
		 */
		private void drawInlineCoordinates(Graphics2D g, FontMetrics fm, int boardX, int boardY) {
		int pad = Math.max(2, fm.getAscent() / 5);
		int bottomRow = 7;

		for (int file = 0; file < 8; file++) {
			char fileChar = whiteSideDown ? (char) ('a' + file) : (char) ('h' - file);
			String s = String.valueOf(fileChar);
			int x = boardX + file * tileWidth + tileWidth - pad - fm.stringWidth(s);
			int y = boardY + bottomRow * tileHeight + tileHeight - pad - fm.getDescent();
			g.drawString(s, x, y);
		}

		for (int rank = 0; rank < 8; rank++) {
			int label = whiteSideDown ? 8 - rank : 1 + rank;
			String s = String.valueOf(label);
			int x = boardX + pad;
			int y = boardY + rank * tileHeight + pad + fm.getAscent();
			g.drawString(s, x, y);
		}
	}

		/**
		 * Draws coordinate labels outside the board area when requested.
		 *
		 * @param g graphics context to draw into
		 * @param fm font metrics for sizing glyphs
		 * @param boardX left edge of the board
		 * @param boardY top edge of the board
		 */
		private void drawOutsideCoordinates(Graphics2D g, FontMetrics fm, int boardX, int boardY) {
		double halfFont = g.getFont().getSize() / 2.0;
		int halfTileW = tileWidth / 2;
		int halfTileH = tileHeight / 2;

		if (whiteSideDown) {
			for (int i = 0; i < 8; i++) {
				String s = Integer.toString(i + 1);
				int y = (int) (boardY + tileHeight * (7 - i) + halfFont + halfTileH);
				g.drawString(s,
						(int) (boardWidth + boardX + halfTileW - (fm.stringWidth(s) / 2.0)),
						y);
				g.drawString(s,
						(int) (boardX - halfTileW - (fm.stringWidth(s) / 2.0)),
						y);

				s = Character.toString((char) ('a' + i));
				int x = (int) (boardX + (tileWidth * i) + halfTileW - (fm.stringWidth(s) / 2.0));
				g.drawString(s, x, (int) (boardHeight + boardY + halfFont + halfTileH));
				g.drawString(s, x, (int) (boardY + halfFont - halfTileH));
			}
			return;
		}

		for (int i = 0; i < 8; i++) {
			String s = Integer.toString(i + 1);
			int y = (int) (boardY + tileHeight * i + halfFont + halfTileH);
			g.drawString(s,
					(int) (boardWidth + boardX + halfTileW - (fm.stringWidth(s) / 2.0)),
					y);
			g.drawString(s,
					(int) (boardX - halfTileW - (fm.stringWidth(s) / 2.0)),
					y);

			s = Character.toString((char) ('a' + i));
			int x = (int) (boardX + (tileWidth * (7 - i)) + halfTileW - (fm.stringWidth(s) / 2.0));
			g.drawString(s, x, (int) (boardHeight + boardY + halfFont + halfTileH));
			g.drawString(s, x, (int) (boardY + halfFont - halfTileH));
		}
	}

		/**
		 * Computes padding to apply around coordinate labels.
		 *
		 * @return padding in pixels for coordinate drawing regions
		 */
		private int coordinatePadding() {
			return Math.max(16, tileWidth);
		}

		/**
		 * Resolves default colors when a square text label omits explicit styling.
		 *
		 * <p>Labels without an explicit palette inherit colors based on the occupying
		 * piece, while fully specified labels keep their provided values. The resolved
		 * colors are stored in the reusable {@link SquareTextStyle} to avoid allocations.</p>
		 *
		 * @param label label being rendered
		 * @param piece piece code currently occupying the square
		 * @param out style object to populate
		 */
		private void resolveSquareTextStyle(SquareText label, byte piece, SquareTextStyle out) {
		if (label.background == null || label.textColor == null || label.border == null) {
			if (Piece.isWhitePiece(piece)) {
				out.textColor = DEFAULT_SQUARE_TEXT_WHITE_PIECE_TEXT;
				out.background = DEFAULT_SQUARE_TEXT_WHITE_PIECE_BACKGROUND;
				out.border = DEFAULT_SQUARE_TEXT_WHITE_PIECE_BORDER;
			} else if (Piece.isBlackPiece(piece)) {
				out.textColor = DEFAULT_SQUARE_TEXT_BLACK_PIECE_TEXT;
				out.background = DEFAULT_SQUARE_TEXT_BLACK_PIECE_BACKGROUND;
				out.border = DEFAULT_SQUARE_TEXT_BLACK_PIECE_BORDER;
			} else {
				out.textColor = DEFAULT_SQUARE_TEXT_COLOR;
				out.background = DEFAULT_SQUARE_TEXT_BACKGROUND;
				out.border = DEFAULT_SQUARE_TEXT_BORDER;
			}
		} else {
			out.textColor = label.textColor;
			out.background = label.background;
			out.border = label.border;
		}
		out.borderStroke = label.borderStroke != null ? label.borderStroke : DEFAULT_SQUARE_TEXT_STROKE;
	}

		/**
		 * Computes the top-left pixel coordinate of a tile from the square index.
		 *
		 * <p>Accounts for the {@link #whiteSideDown} flag by flipping the horizontal
		 * and vertical references so the rendered board matches the requested orientation.</p>
		 *
		 * @param index square index (0= A8)
		 * @param boardX board origin x coordinate
		 * @param boardY board origin y coordinate
		 * @param out point to receive the coordinates
		 */
		private void tileOrigin(byte index, int boardX, int boardY, IntPoint out) {
		int x = whiteSideDown ? Field.getX(index) : Field.getXInverted(index);
		int y = whiteSideDown ? Field.getYInverted(index) : Field.getY(index);
		out.x = boardX + x * tileWidth;
		out.y = boardY + y * tileHeight;
	}

		/**
		 * Adjusts the font size for a square text label to fit within its tile.
		 *
		 * <p>The method measures the rendered width and height, shrinking the font in
		 * one-point steps until the text fits within {@link #squareTextMaxWidth} and
		 * {@link #squareTextMaxHeight} or a minimum size is reached.</p>
		 *
		 * @param g graphics context used for font metrics
		 * @param text label text to measure
		 * @param baseFont base font to start from
		 * @param out layout buffer populated with measurement results
		 * @param maxWidthScale scale to apply to {@link #squareTextMaxWidth}
		 * @param maxHeightScale scale to apply to {@link #squareTextMaxHeight}
		 */
		private void fitSquareTextFont(Graphics2D g, String text, Font baseFont, TextLayout out,
				double maxWidthScale, double maxHeightScale) {
		Font font = baseFont != null ? baseFont : squareTextBaseFont;
		int fontSize = font.getSize();
		FontMetrics fm = g.getFontMetrics(font);
		int textWidth = fm.stringWidth(text);
		int textHeight = fm.getAscent() + fm.getDescent();
		int maxWidth = (int) Math.max(1, Math.round(squareTextMaxWidth * maxWidthScale));
		int maxHeight = (int) Math.max(1, Math.round(squareTextMaxHeight * maxHeightScale));
		while ((textWidth > maxWidth || textHeight > maxHeight) && fontSize > 7) {
			fontSize--;
			font = font.deriveFont((float) fontSize);
			fm = g.getFontMetrics(font);
			textWidth = fm.stringWidth(text);
			textHeight = fm.getAscent() + fm.getDescent();
		}

		out.fontSize = fontSize;
		out.font = font;
		out.fm = fm;
		out.textWidth = textWidth;
		out.textHeight = textHeight;
	}

		/**
		 * Draws the rounded background box and the text for a square label.
		 *
		 * <p>The layout metrics include the computed font and text size, so this
		 * method simply centers the box/text within the tile and strokes the rounding
		 * using the resolved style.</p>
		 *
		 * @param g graphics context to draw into
		 * @param text label text
		 * @param tileX tile origin x coordinate
		 * @param tileY tile origin y coordinate
		 * @param style resolved style colors and stroke
		 * @param layout layout metrics produced by {@link #fitSquareTextFont}
		 * @param bottomAligned whether to anchor the box to the bottom of the tile
		 */
		private void drawSquareTextBoxAndText(Graphics2D g, String text, int tileX, int tileY, SquareTextStyle style,
				TextLayout layout, boolean bottomAligned) {
		int padX = Math.max(2, layout.fontSize / 4);
		int padY = padX;
		if (bottomAligned) {
			padX = Math.max(1, layout.fontSize / 5);
			padY = padX;
		}

		int boxWidth = Math.min(tileWidth, layout.textWidth + padX * 2);
		int boxHeight = Math.min(tileHeight, layout.textHeight + padY * 2);
		int boxX = tileX + (tileWidth - boxWidth) / 2;
		int boxY = tileY + (tileHeight - boxHeight) / 2;
		if (bottomAligned) {
			int bottomPad = Math.max(2, layout.fontSize / 6);
			boxY = tileY + tileHeight - boxHeight - bottomPad;
		}
		int arc = Math.max(4, boxHeight / 2);

		g.setPaint(style.background);
		g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, arc, arc);
		if (style.border != null) {
			g.setStroke(style.borderStroke);
			g.setPaint(style.border);
			g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, arc, arc);
		}

		g.setFont(layout.font);
		g.setPaint(style.textColor);
		int textX = boxX + (boxWidth - layout.textWidth) / 2;
		int textY = boxY + (boxHeight - layout.textHeight) / 2 + layout.fm.getAscent();
		g.drawString(text, textX, textY);
	}

	/**
	 * Maps a piece code to its embedded SVG source.
	 *
	 * @param piece piece code from {@link Piece}
	 * @return SVG source or null when empty
	 */
	private static String svgForPiece(byte piece) {
		return switch (piece) {
			case Piece.BLACK_BISHOP -> SvgShapes.blackBishop();
			case Piece.BLACK_KING -> SvgShapes.blackKing();
			case Piece.BLACK_KNIGHT -> SvgShapes.blackKnight();
			case Piece.BLACK_PAWN -> SvgShapes.blackPawn();
			case Piece.BLACK_QUEEN -> SvgShapes.blackQueen();
			case Piece.BLACK_ROOK -> SvgShapes.blackRook();
			case Piece.WHITE_BISHOP -> SvgShapes.whiteBishop();
			case Piece.WHITE_KING -> SvgShapes.whiteKing();
			case Piece.WHITE_KNIGHT -> SvgShapes.whiteKnight();
			case Piece.WHITE_PAWN -> SvgShapes.whitePawn();
			case Piece.WHITE_QUEEN -> SvgShapes.whiteQueen();
			case Piece.WHITE_ROOK -> SvgShapes.whiteRook();
			default -> null;
		};
	}

	/**
	 * Appends the body of an embedded SVG source, excluding its root and title.
	 *
	 * @param out SVG builder receiving the body
	 * @param source embedded SVG source
	 * @param indent indentation to prefix on each appended line
	 */
	private static void appendEmbeddedSvgBody(StringBuilder out, String source, String indent) {
		int start = source.indexOf("<g");
		int end = source.lastIndexOf("</svg>");
		if (start < 0 || end <= start) {
			throw new IllegalArgumentException("embedded SVG source does not contain a drawable group");
		}
		String body = source.substring(start, end).stripTrailing();
		String[] lines = body.split("\\R");
		for (String line : lines) {
			out.append(indent).append(line).append('\n');
		}
	}

	/**
	 * Appends a simple SVG text element.
	 *
	 * @param svg SVG builder
	 * @param indent indentation prefix
	 * @param x text x position
	 * @param y text y position
	 * @param text text content
	 */
	private static void appendTextElement(StringBuilder svg, String indent, int x, int y, String text) {
		svg.append(indent).append("<text x=\"").append(x).append("\" y=\"").append(y)
				.append("\">").append(escapeText(text)).append("</text>\n");
	}

	/**
	 * Appends SVG fill/stroke color and opacity attributes.
	 *
	 * @param svg SVG builder
	 * @param attribute attribute name, such as {@code fill} or {@code stroke}
	 * @param color color to serialize
	 */
	private static void appendColorAttribute(StringBuilder svg, String attribute, Color color) {
		Color value = color != null ? color : new Color(0, 0, 0, 0);
		svg.append(' ').append(attribute).append("=\"").append(hexColor(value)).append('"');
		if (value.getAlpha() < 255) {
			svg.append(' ').append(attribute).append("-opacity=\"");
			appendNumber(svg, value.getAlpha() / 255.0);
			svg.append('"');
		}
	}

	/**
	 * Appends SVG font attributes.
	 *
	 * @param svg SVG builder
	 * @param font font to serialize
	 */
	private static void appendFontAttributes(StringBuilder svg, Font font) {
		Font value = font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
		svg.append(" font-family=\"").append(escapeAttribute(svgFontFamily(value))).append("\" font-size=\"")
				.append(value.getSize()).append('"');
		if (value.isBold()) {
			svg.append(" font-weight=\"700\"");
		}
		if (value.isItalic()) {
			svg.append(" font-style=\"italic\"");
		}
	}

	/**
	 * Converts an AWT font family to a CSS-compatible SVG font family list.
	 *
	 * @param font font to inspect
	 * @return SVG font-family value
	 */
	private static String svgFontFamily(Font font) {
		String name = font.getName();
		String family = font.getFamily(Locale.ROOT);
		if ("Times New Roman".equalsIgnoreCase(name) || "Serif".equalsIgnoreCase(family)) {
			return "Times New Roman, Times, serif";
		}
		if (Font.SANS_SERIF.equalsIgnoreCase(name) || "SansSerif".equalsIgnoreCase(family)) {
			return "Arial, Helvetica, sans-serif";
		}
		return family;
	}

	/**
	 * Serializes a color as an SVG hex color without alpha.
	 *
	 * @param color color to serialize
	 * @return SVG hex color
	 */
	private static String hexColor(Color color) {
		return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * Returns the visible stroke width for SVG output.
	 *
	 * @param stroke AWT stroke
	 * @return stroke width
	 */
	private static float strokeWidth(Stroke stroke) {
		if (stroke instanceof BasicStroke basicStroke) {
			return basicStroke.getLineWidth();
		}
		return 1.0f;
	}

	/**
	 * Appends a compact decimal number suitable for SVG attributes.
	 *
	 * @param svg SVG builder
	 * @param value numeric value
	 */
	private static void appendNumber(StringBuilder svg, double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("non-finite SVG number: " + value);
		}
		double rounded = Math.rint(value);
		if (Math.abs(value - rounded) < 0.000001) {
			svg.append((long) rounded);
			return;
		}
		String text = String.format(Locale.ROOT, "%.6f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}
		svg.append(text);
	}

	/**
	 * Escapes text content for SVG.
	 *
	 * @param text raw text
	 * @return escaped text
	 */
	private static String escapeText(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		return text.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;");
	}

	/**
	 * Escapes attribute content for SVG.
	 *
	 * @param text raw attribute value
	 * @return escaped attribute value
	 */
	private static String escapeAttribute(String text) {
		return escapeText(text).replace("\"", "&quot;");
	}

	/**
	 * Converts a signed byte square index to a validated 0..63 integer index.
	 *
	 * @param index square index
	 * @return int index in range
	 * @throws IllegalArgumentException if outside 0..63
	 */
	private static int toSquareIndex(byte index) {
		int idx = index & 0xFF;
		if (idx < 0 || idx >= 64) {
			throw new IllegalArgumentException("square index must be in range 0..63, got " + idx);
		}
		return idx;
	}

	/**
	 * Render dimensions and board placement shared by raster and SVG output.
	 *
	 * @param width native output width
	 * @param height native output height
	 * @param boardX board origin x
	 * @param boardY board origin y
	 */
	private record RenderGeometry(
		/**
		 * Stores the width.
		 */
		int width,
		/**
		 * Stores the height.
		 */
		int height,
		/**
		 * Stores the board x.
		 */
		int boardX,
		/**
		 * Stores the board y.
		 */
		int boardY
	) {
	}

	/**
	 * Circle overlay state.
	 *
	 * @param index    target square index
	 * @param diameter circle diameter in pixels
	 * @param border   outline color
	 * @param fill     fill color
	 * @param stroke   outline stroke
	 */
	private record Circle(
		/**
		 * Stores the index.
		 */
		byte index,
		/**
		 * Stores the diameter.
		 */
		int diameter,
		/**
		 * Stores the border.
		 */
		Color border,
		/**
		 * Stores the fill.
		 */
		Color fill,
		/**
		 * Stores the stroke.
		 */
		Stroke stroke
	) {
	}

	/**
	 * Per-square text overlay state.
	 *
	 * @param index        target square index
	 * @param text         label to draw
	 * @param textColor    text color
	 * @param background   background fill color
	 * @param border       border color
	 * @param borderStroke border stroke
	 * @param baseFont     optional base font override
	 * @param bottomAligned whether to align the background box to the bottom of the tile
	 * @param detail       whether to draw this label below pieces and overlays
	 */
	private record SquareText(
		/**
		 * Stores the index.
		 */
		byte index,
		/**
		 * Stores the text.
		 */
		String text,
		/**
		 * Stores the text color.
		 */
		Color textColor,
		/**
		 * Stores the background.
		 */
		Color background,
		/**
		 * Stores the border.
		 */
		Color border,
		/**
		 * Stores the border stroke.
		 */
		Stroke borderStroke,
		/**
		 * Stores the base font.
		 */
		Font baseFont,
		/**
		 * Stores the bottom aligned.
		 */
		boolean bottomAligned,
		/**
		 * Stores the detail.
		 */
		boolean detail
	) {
	}

		/**
		 * Reusable mutable holder for square text colors and strokes.
		 * Keeps allocations low during the render loop.
		 */
		private static final class SquareTextStyle {

			/**
			 * Background fill color for the text box.
			 */
			private Color background;

			/**
			 * Border color for the text box outline.
			 */
			private Color border;

			/**
			 * Color used to draw the text glyph.
			 */
			private Color textColor;

			/**
			 * Stroke used when drawing the border.
			 */
			private Stroke borderStroke;
		}

		/**
		 * Temporary layout metrics for square text strings.
		 * Updated per label to track measured width, height, and font selection.
		 */
		private static final class TextLayout {

			/**
			 * Font chosen for the label.
			 */
			private Font font;

			/**
			 * Font metrics corresponding to {@link #font}.
			 */
			private FontMetrics fm;

			/**
			 * Measured width of the current text.
			 */
			private int textWidth;

			/**
			 * Measured height of the current text.
			 */
			private int textHeight;

			/**
			 * Rendered font size in points.
			 */
			private int fontSize;
		}

		/**
		 * Simple mutable point used for temporary tile origin calculations.
		 */
		private static final class IntPoint {

			/**
			 * X coordinate in pixels.
			 */
			private int x;

			/**
			 * Y coordinate in pixels.
			 */
			private int y;
		}
}
