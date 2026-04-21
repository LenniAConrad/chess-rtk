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
	private final SquareListener squareListener;
	private final HoverListener hoverListener;

	/**
	 * Paint state.
	 */
	private Position position;
	private WebGuiTheme theme;
	private boolean whiteDown;
	private boolean showCoordinates;
	private boolean glassEnabled;
	private byte selectedSquare = Field.NO_SQUARE;
	private byte hoverSquare = Field.NO_SQUARE;
	private byte checkSquare = Field.NO_SQUARE;
	private short lastMove = Move.NO_MOVE;
	private final boolean[] legalTargets = new boolean[64];
	private final boolean[] captureTargets = new boolean[64];

	/**
	 * Cached board geometry for hit-testing.
	 */
	private int boardX;
	private int boardY;
	private int boardSize;
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
			@Override
			public void mouseMoved(MouseEvent event) {
				updateHover(squareAt(event.getX(), event.getY()));
			}

			@Override
			public void mouseExited(MouseEvent event) {
				updateHover(Field.NO_SQUARE);
			}

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
		this.position = position.copyOf();
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
			this.checkSquare = this.position.isWhiteTurn() ? this.position.getWhiteKing() : this.position.getBlackKing();
		} else {
			this.checkSquare = Field.NO_SQUARE;
		}
		repaint();
	}

	private void updateHover(byte square) {
		if (hoverSquare == square) {
			return;
		}
		hoverSquare = square;
		setCursor(square == Field.NO_SQUARE ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		hoverListener.onHover(square);
		repaint();
	}

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

	private void paintSquares(Graphics2D g) {
		for (int rank = 0; rank < 8; rank++) {
			for (int file = 0; file < 8; file++) {
				byte square = displayToSquare(file, rank);
				Color fill = (((file + rank) & 1) == 0) ? theme.boardLight() : theme.boardDark();
				g.setColor(fill);
				g.fillRect(boardX + file * tileSize, boardY + rank * tileSize, tileSize, tileSize);
			}
		}
	}

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

	private void paintSquareOverlay(Graphics2D g, byte square, Color color) {
		int file = displayFile(square);
		int rank = displayRank(square);
		g.setColor(color);
		g.fillRect(boardX + file * tileSize, boardY + rank * tileSize, tileSize, tileSize);
	}

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

	private Color coordinateColor(byte square) {
		int file = displayFile(square);
		int rank = displayRank(square);
		boolean lightSquare = ((file + rank) & 1) == 0;
		Color base = lightSquare ? theme.boardDark() : theme.boardLight();
		int contrast = (base.getRed() + base.getGreen() + base.getBlue()) / 3;
		return contrast < 128 ? new Color(255, 255, 255, 185) : new Color(60, 50, 40, 185);
	}

	private void paintBorder(Graphics2D g) {
		g.setColor(theme.boardFrame());
		g.setStroke(new BasicStroke(Math.max(2f, tileSize * 0.03f)));
		g.drawRoundRect(boardX, boardY, boardSize, boardSize, BOARD_RADIUS, BOARD_RADIUS);
	}

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

	private byte displayToSquare(int displayFile, int displayRank) {
		int file = whiteDown ? displayFile : 7 - displayFile;
		int rank = whiteDown ? displayRank : 7 - displayRank;
		return (byte) (rank * 8 + file);
	}

	private int displayFile(byte square) {
		int file = Field.getX(square);
		return whiteDown ? file : 7 - file;
	}

	private int displayRank(byte square) {
		int rank = Field.getY(square);
		return whiteDown ? rank : 7 - rank;
	}
}
