package application.gui.web;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;

import javax.swing.JPanel;

import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;
import chess.images.assets.Shapes;

/**
 * Interactive board canvas for the chess-web-inspired desktop GUI.
 *
 * @since 2026
 */
final class WebBoardPanel extends JPanel {

		/**
	 * Serialization version identifier.
	 */
@java.io.Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Listener for square activation events.
	 */
	interface SquareListener {
		/**
		 * Called when the user activates a board square.
		 *
		 * @param square activated square
		 */
		void onSquare(byte square);
	}

	/**
	 * Listener for hover changes.
	 */
	interface HoverListener {
		/**
		 * Called when the hovered square changes.
		 *
		 * @param square hovered square or {@link Field#NO_SQUARE}
		 */
		void onHover(byte square);
	}

	/**
	 * Preferred board size.
	 */
	private static final int PREFERRED_SIZE = 760;

	/**
	 * Board corner radius.
	 */
	private static final int BOARD_RADIUS = 14;

	/**
	 * Insets around the board inside the panel.
	 */
	private static final int OUTER_PAD = 24;

	/**
	 * State listeners.
	 */
	private final transient SquareListener squareListener;
		/**
	 * Stores the hover listener.
	 */
private final transient HoverListener hoverListener;

	/**
	 * Paint state.
	 */
	private Position position;
		/**
	 * Stores the theme.
	 */
private WebGuiTheme theme;
		/**
	 * Stores the white down.
	 */
private boolean whiteDown;
		/**
	 * Stores the show coordinates.
	 */
private boolean showCoordinates;
		/**
	 * Stores the glass enabled.
	 */
private boolean glassEnabled;
		/**
	 * Stores the selected square.
	 */
private byte selectedSquare = Field.NO_SQUARE;
		/**
	 * Stores the hover square.
	 */
private byte hoverSquare = Field.NO_SQUARE;
		/**
	 * Stores the check square.
	 */
private byte checkSquare = Field.NO_SQUARE;
		/**
	 * Stores the last move.
	 */
private short lastMove = Move.NO_MOVE;
		/**
	 * Stores the legal targets.
	 */
private final boolean[] legalTargets = new boolean[64];
		/**
	 * Stores the capture targets.
	 */
private final boolean[] captureTargets = new boolean[64];

	/**
	 * Cached board geometry for hit-testing.
	 */
	private int boardX;
		/**
	 * Stores the board y.
	 */
private int boardY;
		/**
	 * Stores the board size.
	 */
private int boardSize;
		/**
	 * Stores the tile size.
	 */
private int tileSize;

	/**
	 * Creates the board panel.
	 *
	 * @param squareListener callback for clicks
	 * @param hoverListener callback for hover updates
	 */
	WebBoardPanel(SquareListener squareListener, HoverListener hoverListener) {
		this.squareListener = squareListener;
		this.hoverListener = hoverListener;
		this.position = new Position(WebGuiWindow.DEFAULT_FEN);
		this.theme = WebGuiTheme.light();
		this.whiteDown = true;
		this.showCoordinates = true;
		this.glassEnabled = true;
		setOpaque(false);
		setFocusable(false);
		MouseAdapter mouse = new MouseAdapter() {
						/**
			 * Handles mouse moved.
			 * @param event event value
			 */
@Override
			public void mouseMoved(MouseEvent event) {
				updateHover(squareAt(event.getX(), event.getY()));
			}

						/**
			 * Handles mouse exited.
			 * @param event event value
			 */
@Override
			public void mouseExited(MouseEvent event) {
				updateHover(Field.NO_SQUARE);
			}

						/**
			 * Handles mouse clicked.
			 * @param event event value
			 */
@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getButton() != MouseEvent.BUTTON1) {
					return;
				}
				byte square = squareAt(event.getX(), event.getY());
				if (square != Field.NO_SQUARE) {
					squareListener.onSquare(square);
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

		/**
	 * Returns the preferred size.
	 * @return computed value
	 */
@Override
	public java.awt.Dimension getPreferredSize() {
		return new java.awt.Dimension(PREFERRED_SIZE, PREFERRED_SIZE);
	}

	/**
	 * Applies the current board state.
	 *
	 * @param position current position
	 * @param lastMove last move leading into the position
	 * @param selectedSquare current selected square
	 * @param legalTargets legal target mask
	 * @param captureTargets capture target mask
	 * @param whiteDown true when White is shown at the bottom
	 * @param showCoordinates true to paint coordinates
	 * @param glassEnabled true to paint the glass overlay
	 * @param theme active theme
	 */
	void updateBoard(Position position, short lastMove, byte selectedSquare, boolean[] legalTargets,
			boolean[] captureTargets, boolean whiteDown, boolean showCoordinates, boolean glassEnabled,
			WebGuiTheme theme) {
		this.position = position.copy();
		this.lastMove = lastMove;
		this.selectedSquare = selectedSquare;
		this.whiteDown = whiteDown;
		this.showCoordinates = showCoordinates;
		this.glassEnabled = glassEnabled;
		this.theme = theme;
		Arrays.fill(this.legalTargets, false);
		Arrays.fill(this.captureTargets, false);
		if (legalTargets != null) {
			System.arraycopy(legalTargets, 0, this.legalTargets, 0,
					Math.min(this.legalTargets.length, legalTargets.length));
		}
		if (captureTargets != null) {
			System.arraycopy(captureTargets, 0, this.captureTargets, 0,
					Math.min(this.captureTargets.length, captureTargets.length));
		}
		if (this.position.inCheck()) {
			this.checkSquare = this.position.isWhiteToMove() ? this.position.kingSquare(true) : this.position.kingSquare(false);
		} else {
			this.checkSquare = Field.NO_SQUARE;
		}
		repaint();
	}

		/**
	 * Handles update hover.
	 * @param square square value
	 */
private void updateHover(byte square) {
		if (hoverSquare == square) {
			return;
		}
		hoverSquare = square;
		setCursor(square == Field.NO_SQUARE ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		hoverListener.onHover(square);
		repaint();
	}

		/**
	 * Handles paint component.
	 * @param graphics graphics value
	 */
@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		computeGeometry();
		paintShadow(g);
		RoundRectangle2D boardRect = new RoundRectangle2D.Float(boardX, boardY, boardSize, boardSize,
				BOARD_RADIUS, BOARD_RADIUS);
		g.setClip(boardRect);
		paintSquares(g);
		paintHighlights(g);
		paintLegalTargets(g);
		if (glassEnabled) {
			paintGlass(g);
		}
		paintPieces(g);
		if (showCoordinates) {
			paintCoordinates(g);
		}
		g.setClip(null);
		paintBorder(g);
		g.dispose();
	}

		/**
	 * Handles compute geometry.
	 */
private void computeGeometry() {
		int usableWidth = Math.max(8, getWidth() - OUTER_PAD * 2);
		int usableHeight = Math.max(8, getHeight() - OUTER_PAD * 2);
		boardSize = Math.max(8, Math.min(usableWidth, usableHeight));
		boardX = (getWidth() - boardSize) / 2;
		boardY = (getHeight() - boardSize) / 2;
		tileSize = boardSize / 8;
		boardSize = tileSize * 8;
		boardX = (getWidth() - boardSize) / 2;
		boardY = (getHeight() - boardSize) / 2;
	}

		/**
	 * Handles paint shadow.
	 * @param g g value
	 */
private void paintShadow(Graphics2D g) {
		g.setColor(theme.boardShadow());
		for (int i = 10; i >= 1; i--) {
			int alpha = Math.max(8, 36 - i * 2);
			g.setColor(new Color(theme.boardShadow().getRed(), theme.boardShadow().getGreen(),
					theme.boardShadow().getBlue(), alpha));
			g.fillRoundRect(boardX - i / 2, boardY + i / 2, boardSize + i, boardSize + i, BOARD_RADIUS + i,
					BOARD_RADIUS + i);
		}
	}

		/**
	 * Handles paint squares.
	 * @param g g value
	 */
private void paintSquares(Graphics2D g) {
		for (int rank = 0; rank < 8; rank++) {
			for (int file = 0; file < 8; file++) {
					Color fill = (((file + rank) & 1) == 0) ? theme.boardLight() : theme.boardDark();
				g.setColor(fill);
				g.fillRect(boardX + file * tileSize, boardY + rank * tileSize, tileSize, tileSize);
			}
		}
	}

		/**
	 * Handles paint highlights.
	 * @param g g value
	 */
private void paintHighlights(Graphics2D g) {
		if (lastMove != Move.NO_MOVE) {
			paintSquareOverlay(g, Move.getFromIndex(lastMove), theme.lastMove());
			paintSquareOverlay(g, Move.getToIndex(lastMove), theme.lastMove());
		}
		if (selectedSquare != Field.NO_SQUARE) {
			paintSquareOverlay(g, selectedSquare, theme.selected());
		}
		if (checkSquare != Field.NO_SQUARE) {
			paintSquareOverlay(g, checkSquare, theme.check());
		}
		if (hoverSquare != Field.NO_SQUARE && hoverSquare != selectedSquare && !legalTargets[hoverSquare]) {
			paintSquareOverlay(g, hoverSquare, new Color(theme.accentSoft().getRed(), theme.accentSoft().getGreen(),
					theme.accentSoft().getBlue(), 40));
		}
	}

		/**
	 * Handles paint square overlay.
	 * @param g g value
	 * @param square square value
	 * @param color color value
	 */
private void paintSquareOverlay(Graphics2D g, byte square, Color color) {
		int file = displayFile(square);
		int rank = displayRank(square);
		g.setColor(color);
		g.fillRect(boardX + file * tileSize, boardY + rank * tileSize, tileSize, tileSize);
	}

		/**
	 * Handles paint legal targets.
	 * @param g g value
	 */
private void paintLegalTargets(Graphics2D g) {
		byte[] board = position.getBoard();
		for (byte square = 0; square < 64; square++) {
			if (!legalTargets[square]) {
				continue;
			}
			int file = displayFile(square);
			int rank = displayRank(square);
			int x = boardX + file * tileSize;
			int y = boardY + rank * tileSize;
			if (captureTargets[square] || Piece.isPiece(board[square])) {
				g.setColor(theme.legalCapture());
				g.setStroke(new BasicStroke(Math.max(2f, tileSize * 0.075f)));
				int inset = Math.max(4, tileSize / 11);
				g.drawOval(x + inset, y + inset, tileSize - inset * 2, tileSize - inset * 2);
			} else {
				g.setColor(theme.legalMove());
				int diameter = Math.max(10, (int) (tileSize * 0.26f));
				int dx = x + (tileSize - diameter) / 2;
				int dy = y + (tileSize - diameter) / 2;
				g.fillOval(dx, dy, diameter, diameter);
			}
		}
	}

		/**
	 * Handles paint glass.
	 * @param g g value
	 */
private void paintGlass(Graphics2D g) {
		Color tint = theme.boardGlass();
		g.setColor(tint);
		g.fillRect(boardX, boardY, boardSize, boardSize);
		g.setPaint(new GradientPaint(boardX, boardY, new Color(255, 255, 255, 36), boardX, boardY + boardSize,
				new Color(255, 255, 255, 0)));
		g.fillRect(boardX, boardY, boardSize, boardSize / 2);
		g.setColor(new Color(255, 255, 255, 18));
		for (int i = 0; i < 8; i++) {
			int x = boardX + i * tileSize;
			int y = boardY + i * tileSize;
			g.drawLine(x, boardY, x + boardSize / 10, boardY + boardSize);
			g.drawLine(boardX, y, boardX + boardSize, y + boardSize / 10);
		}
	}

		/**
	 * Handles paint pieces.
	 * @param g g value
	 */
private void paintPieces(Graphics2D g) {
		byte[] board = position.getBoard();
		for (byte square = 0; square < 64; square++) {
			byte piece = board[square];
			if (!Piece.isPiece(piece)) {
				continue;
			}
			int file = displayFile(square);
			int rank = displayRank(square);
			double pieceInset = tileSize * 0.06;
			Shapes.drawPiece(piece, g,
					boardX + file * tileSize + pieceInset,
					boardY + rank * tileSize + pieceInset,
					tileSize - pieceInset * 2,
					tileSize - pieceInset * 2);
		}
	}

		/**
	 * Handles paint coordinates.
	 * @param g g value
	 */
private void paintCoordinates(Graphics2D g) {
		Font font = theme.smallFont().deriveFont(Font.BOLD, Math.max(11f, tileSize * 0.11f));
		g.setFont(font);
		for (int file = 0; file < 8; file++) {
			String label = whiteDown ? String.valueOf((char) ('a' + file)) : String.valueOf((char) ('h' - file));
			byte square = displayToSquare(file, 7);
			g.setColor(coordinateColor(square));
			int x = boardX + file * tileSize + tileSize - g.getFontMetrics().stringWidth(label) - 6;
			int y = boardY + boardSize - 7;
			g.drawString(label, x, y);
		}
		for (int rank = 0; rank < 8; rank++) {
			String label = whiteDown ? Integer.toString(8 - rank) : Integer.toString(rank + 1);
			byte square = displayToSquare(0, rank);
			g.setColor(coordinateColor(square));
			int x = boardX + 5;
			int y = boardY + rank * tileSize + g.getFontMetrics().getAscent() + 3;
			g.drawString(label, x, y);
		}
	}

		/**
	 * Handles coordinate color.
	 * @param square square value
	 * @return computed value
	 */
private Color coordinateColor(byte square) {
		int file = displayFile(square);
		int rank = displayRank(square);
		boolean lightSquare = ((file + rank) & 1) == 0;
		Color base = lightSquare ? theme.boardDark() : theme.boardLight();
		int contrast = (base.getRed() + base.getGreen() + base.getBlue()) / 3;
		return contrast < 128 ? new Color(255, 255, 255, 185) : new Color(60, 50, 40, 185);
	}

		/**
	 * Handles paint border.
	 * @param g g value
	 */
private void paintBorder(Graphics2D g) {
		g.setColor(theme.boardFrame());
		g.setStroke(new BasicStroke(Math.max(2f, tileSize * 0.03f)));
		g.drawRoundRect(boardX, boardY, boardSize, boardSize, BOARD_RADIUS, BOARD_RADIUS);
	}

		/**
	 * Handles square at.
	 * @param x x value
	 * @param y y value
	 * @return computed value
	 */
private byte squareAt(int x, int y) {
		if (x < boardX || y < boardY || x >= boardX + boardSize || y >= boardY + boardSize || tileSize <= 0) {
			return Field.NO_SQUARE;
		}
		int file = (x - boardX) / tileSize;
		int rank = (y - boardY) / tileSize;
		if (file < 0 || file > 7 || rank < 0 || rank > 7) {
			return Field.NO_SQUARE;
		}
		return displayToSquare(file, rank);
	}

		/**
	 * Handles display to square.
	 * @param displayFile display file value
	 * @param displayRank display rank value
	 * @return computed value
	 */
private byte displayToSquare(int displayFile, int displayRank) {
		int file = whiteDown ? displayFile : 7 - displayFile;
		int rank = whiteDown ? displayRank : 7 - displayRank;
		return (byte) (rank * 8 + file);
	}

		/**
	 * Handles display file.
	 * @param square square value
	 * @return computed value
	 */
private int displayFile(byte square) {
		int file = Field.getX(square);
		return whiteDown ? file : 7 - file;
	}

		/**
	 * Handles display rank.
	 * @param square square value
	 * @return computed value
	 */
private int displayRank(byte square) {
		int rank = Field.getY(square);
		return whiteDown ? rank : 7 - rank;
	}
}
