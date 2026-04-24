package application.gui.studio;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import chess.core.Field;
import chess.core.Move;
import chess.core.Position;
import chess.images.assets.Pictures;

/**
 * Reusable board renderer and input surface for GUI v3.
 */
public final class StudioBoardPanel extends JPanel {

	/**
	 * Serialization identifier for Swing component compatibility.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Fraction of a square occupied by a rendered piece image.
	 */
	private static final float PIECE_SCALE = 0.88f;

	/**
	 * Current immutable board view state.
	 */
	private BoardViewModel model = BoardViewModel.of(new Position(Position.fromFen(
			chess.struct.Game.STANDARD_START_FEN).toString()), true, StudioTheme.light());

	/**
	 * Listener receiving board input events.
	 */
	private transient BoardListener listener;

	/**
	 * Square where the current left-button drag started.
	 */
	private byte leftPressSquare = Field.NO_SQUARE;

	/**
	 * Square where the current right-button drag started.
	 */
	private byte rightPressSquare = Field.NO_SQUARE;

	/**
	 * Board event listener.
	 */
	public interface BoardListener {
		/**
		 * Handles selection of a board square.
		 *
		 * @param square selected square
		 */
		void squareSelected(byte square);

		/**
		 * Handles a requested move between two board squares.
		 *
		 * @param from source square
		 * @param to destination square
		 */
		void moveRequested(byte from, byte to);

		/**
		 * Handles a requested board mark.
		 *
		 * @param mark mark request
		 */
		void markRequested(BoardMark mark);

		/**
		 * Handles hover changes over the board.
		 *
		 * @param square hovered square or {@link Field#NO_SQUARE}
		 */
		void hoverSquare(byte square);
	}

	/**
	 * Constructor.
	 */
	public StudioBoardPanel() {
		setPreferredSize(new Dimension(640, 640));
		setMinimumSize(new Dimension(360, 360));
		setFocusable(true);
		MouseAdapter mouse = new MouseAdapter() {
			/**
			 * Records the square where a left or right press starts.
			 *
			 * @param e mouse event
			 */
			@Override
			public void mousePressed(MouseEvent e) {
				requestFocusInWindow();
				byte square = squareAt(e.getPoint());
				if (SwingUtilities.isRightMouseButton(e)) {
					rightPressSquare = square;
				} else {
					leftPressSquare = square;
				}
			}

			/**
			 * Dispatches a selection, move, or mark request when the mouse is released.
			 *
			 * @param e mouse event
			 */
			@Override
			public void mouseReleased(MouseEvent e) {
				byte square = squareAt(e.getPoint());
				if (SwingUtilities.isRightMouseButton(e)) {
					handleRightRelease(square);
				} else {
					handleLeftRelease(square);
				}
			}

			/**
			 * Reports the board square currently under the pointer.
			 *
			 * @param e mouse event
			 */
			@Override
			public void mouseMoved(MouseEvent e) {
				if (listener != null) {
					listener.hoverSquare(squareAt(e.getPoint()));
				}
			}

			/**
			 * Clears hover state when the pointer leaves the board.
			 *
			 * @param e mouse event
			 */
			@Override
			public void mouseExited(MouseEvent e) {
				if (listener != null) {
					listener.hoverSquare(Field.NO_SQUARE);
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	/**
	 * Updates the rendered model.
	 *
	 * @param model board model
	 */
	public void setViewModel(BoardViewModel model) {
		this.model = model;
		repaint();
	}

	/**
	 * Sets listener.
	 *
	 * @param listener event listener
	 */
	public void setBoardListener(BoardListener listener) {
		this.listener = listener;
	}

	/**
	 * Renders the board to an image.
	 *
	 * @param size image size
	 * @return rendered image
	 */
	public BufferedImage renderImage(int size) {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try {
			paintBoard(g, new Rectangle(0, 0, size - size % 8, size - size % 8));
		} finally {
			g.dispose();
		}
		return image;
	}

	/**
	 * Paints the themed board surface.
	 *
	 * @param graphics graphics context
	 */
	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		try {
			g.setColor(model.theme().background());
			g.fillRect(0, 0, getWidth(), getHeight());
			paintBoard(g, StudioBoardMapper.centeredBoard(getWidth(), getHeight()));
		} finally {
			g.dispose();
		}
	}

	/**
	 * Paints all board layers inside the supplied board rectangle.
	 *
	 * @param g graphics context
	 * @param board board bounds
	 */
	private void paintBoard(Graphics2D g, Rectangle board) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int tile = board.width / 8;
		drawSquares(g, board, tile);
		drawLastMove(g, board);
		drawSelection(g, board);
		drawLegalTargets(g, board, tile);
		drawMarks(g, board, tile);
		drawPieces(g, board, tile);
		drawCoordinates(g, board, tile);
	}

	/**
	 * Draws the checkerboard squares and border.
	 *
	 * @param g graphics context
	 * @param board board bounds
	 * @param tile square size in pixels
	 */
	private void drawSquares(Graphics2D g, Rectangle board, int tile) {
		for (int rank = 0; rank < 8; rank++) {
			for (int file = 0; file < 8; file++) {
				boolean light = ((file + rank) & 1) == 0;
				g.setColor(light ? model.theme().boardLight() : model.theme().boardDark());
				g.fillRect(board.x + file * tile, board.y + rank * tile, tile, tile);
			}
		}
		g.setColor(model.theme().border());
		g.drawRect(board.x, board.y, board.width - 1, board.height - 1);
	}

	/**
	 * Highlights the previous move, when one is available.
	 *
	 * @param g graphics context
	 * @param board board bounds
	 */
	private void drawLastMove(Graphics2D g, Rectangle board) {
		if (model.lastMove() == Move.NO_MOVE) {
			return;
		}
		g.setColor(model.theme().lastMove());
		fillSquare(g, board, Move.getFromIndex(model.lastMove()));
		fillSquare(g, board, Move.getToIndex(model.lastMove()));
	}

	/**
	 * Highlights the currently selected square.
	 *
	 * @param g graphics context
	 * @param board board bounds
	 */
	private void drawSelection(Graphics2D g, Rectangle board) {
		if (model.selectedSquare() == Field.NO_SQUARE) {
			return;
		}
		g.setColor(model.theme().selected());
		fillSquare(g, board, model.selectedSquare());
	}

	/**
	 * Draws legal move targets and capture rings for the selected piece.
	 *
	 * @param g graphics context
	 * @param board board bounds
	 * @param tile square size in pixels
	 */
	private void drawLegalTargets(Graphics2D g, Rectangle board, int tile) {
		boolean[] legal = model.legalTargets();
		boolean[] captures = model.captureTargets();
		for (int square = 0; square < 64; square++) {
			if (legal == null || !legal[square]) {
				continue;
			}
			Rectangle r = StudioBoardMapper.squareBounds((byte) square, board, model.whiteDown());
			int cx = r.x + tile / 2;
			int cy = r.y + tile / 2;
			if (captures != null && captures[square]) {
				g.setColor(model.theme().capture());
				g.setStroke(new BasicStroke(Math.max(3f, tile * 0.07f)));
				g.drawOval(r.x + tile / 8, r.y + tile / 8, tile * 3 / 4, tile * 3 / 4);
			} else {
				g.setColor(model.theme().legal());
				int dot = Math.max(8, tile / 5);
				g.fillOval(cx - dot / 2, cy - dot / 2, dot, dot);
			}
		}
	}

	/**
	 * Draws user-created circles and arrows.
	 *
	 * @param g graphics context
	 * @param board board bounds
	 * @param tile square size in pixels
	 */
	private void drawMarks(Graphics2D g, Rectangle board, int tile) {
		List<BoardMark> marks = model.marks();
		if (marks == null) {
			return;
		}
		for (BoardMark mark : marks) {
			g.setColor(markColor(mark.colorIndex()));
			if (mark.type() == BoardMarkType.CIRCLE) {
				Rectangle r = StudioBoardMapper.squareBounds(mark.from(), board, model.whiteDown());
				g.setStroke(new BasicStroke(Math.max(4f, tile * 0.08f)));
				g.drawOval(r.x + tile / 7, r.y + tile / 7, tile * 5 / 7, tile * 5 / 7);
			} else {
				drawArrow(g, board, tile, mark.from(), mark.to(), markColor(mark.colorIndex()));
			}
		}
	}

	/**
	 * Draws all piece images at their current squares.
	 *
	 * @param g graphics context
	 * @param board board bounds
	 * @param tile square size in pixels
	 */
	private void drawPieces(Graphics2D g, Rectangle board, int tile) {
		byte[] pieces = model.position().getBoard();
		int pieceSize = Math.round(tile * PIECE_SCALE);
		int pad = (tile - pieceSize) / 2;
		for (int square = 0; square < 64; square++) {
			Image image = pieceImage(pieces[square]);
			if (image == null) {
				continue;
			}
			Rectangle r = StudioBoardMapper.squareBounds((byte) square, board, model.whiteDown());
			g.drawImage(image, r.x + pad, r.y + pad, pieceSize, pieceSize, null);
		}
	}

	/**
	 * Draws file and rank labels for the current orientation.
	 *
	 * @param g graphics context
	 * @param board board bounds
	 * @param tile square size in pixels
	 */
	private void drawCoordinates(Graphics2D g, Rectangle board, int tile) {
		g.setColor(new Color(0, 0, 0, model.theme().lightMode() ? 120 : 170));
		for (int i = 0; i < 8; i++) {
			int file = model.whiteDown() ? i : 7 - i;
			int rank = model.whiteDown() ? 8 - i : i + 1;
			g.drawString(String.valueOf((char) ('a' + file)), board.x + i * tile + tile - 14,
					board.y + board.height - 5);
			g.drawString(String.valueOf(rank), board.x + 5, board.y + i * tile + 14);
		}
	}

	/**
	 * Fills a single board square.
	 *
	 * @param g graphics context
	 * @param board board bounds
	 * @param square square to fill
	 */
	private void fillSquare(Graphics2D g, Rectangle board, byte square) {
		Rectangle r = StudioBoardMapper.squareBounds(square, board, model.whiteDown());
		g.fillRect(r.x, r.y, r.width, r.height);
	}

	/**
	 * Draws an arrow between two board squares.
	 *
	 * @param g graphics context
	 * @param board board bounds
	 * @param tile square size in pixels
	 * @param from source square
	 * @param to target square
	 * @param color arrow color
	 */
	private void drawArrow(Graphics2D g, Rectangle board, int tile, byte from, byte to, Color color) {
		if (from == Field.NO_SQUARE || to == Field.NO_SQUARE || from == to) {
			return;
		}
		Rectangle a = StudioBoardMapper.squareBounds(from, board, model.whiteDown());
		Rectangle b = StudioBoardMapper.squareBounds(to, board, model.whiteDown());
		double x1 = a.getCenterX();
		double y1 = a.getCenterY();
		double x2 = b.getCenterX();
		double y2 = b.getCenterY();
		double dx = x2 - x1;
		double dy = y2 - y1;
		double len = Math.max(1.0, Math.hypot(dx, dy));
		double ux = dx / len;
		double uy = dy / len;
		int head = Math.max(14, tile / 3);
		int endX = (int) Math.round(x2 - ux * tile * 0.25);
		int endY = (int) Math.round(y2 - uy * tile * 0.25);
		g.setColor(color);
		g.setStroke(new BasicStroke(Math.max(6f, tile * 0.12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawLine((int) Math.round(x1 + ux * tile * 0.2), (int) Math.round(y1 + uy * tile * 0.2), endX, endY);
		Polygon poly = new Polygon();
		poly.addPoint(endX, endY);
		poly.addPoint((int) Math.round(endX - ux * head - uy * head * 0.55),
				(int) Math.round(endY - uy * head + ux * head * 0.55));
		poly.addPoint((int) Math.round(endX - ux * head + uy * head * 0.55),
				(int) Math.round(endY - uy * head - ux * head * 0.55));
			g.fillPolygon(poly);
		}

	/**
	 * Maps a mark color index to a translucent drawing color.
	 *
	 * @param index mark color index
	 * @return drawing color
	 */
	private Color markColor(int index) {
		Color[] colors = {
				new Color(112, 153, 78, 190),
				new Color(87, 111, 163, 185),
				new Color(231, 145, 68, 185),
				new Color(214, 54, 54, 175)
		};
		return colors[Math.floorMod(index, colors.length)];
	}

	/**
	 * Returns the image asset for a piece code.
	 *
	 * @param piece piece code
	 * @return piece image, or null for empty squares
	 */
	private Image pieceImage(byte piece) {
		return switch (piece) {
			case Position.WHITE_PAWN -> Pictures.WhitePawn;
			case Position.WHITE_KNIGHT -> Pictures.WhiteKnight;
			case Position.WHITE_BISHOP -> Pictures.WhiteBishop;
			case Position.WHITE_ROOK -> Pictures.WhiteRook;
			case Position.WHITE_QUEEN -> Pictures.WhiteQueen;
			case Position.WHITE_KING -> Pictures.WhiteKing;
			case Position.BLACK_PAWN -> Pictures.BlackPawn;
			case Position.BLACK_KNIGHT -> Pictures.BlackKnight;
			case Position.BLACK_BISHOP -> Pictures.BlackBishop;
			case Position.BLACK_ROOK -> Pictures.BlackRook;
			case Position.BLACK_QUEEN -> Pictures.BlackQueen;
			case Position.BLACK_KING -> Pictures.BlackKing;
			default -> null;
		};
	}

	/**
	 * Converts a component point to a board square.
	 *
	 * @param point component point
	 * @return square index or {@link Field#NO_SQUARE}
	 */
	private byte squareAt(Point point) {
		return StudioBoardMapper.squareAt(point, StudioBoardMapper.centeredBoard(getWidth(), getHeight()),
				model.whiteDown());
	}

	/**
	 * Handles release of the left mouse button.
	 *
	 * @param square release square
	 */
	private void handleLeftRelease(byte square) {
		if (listener == null || leftPressSquare == Field.NO_SQUARE || square == Field.NO_SQUARE) {
			leftPressSquare = Field.NO_SQUARE;
			return;
		}
		if (leftPressSquare == square) {
			listener.squareSelected(square);
		} else {
			listener.moveRequested(leftPressSquare, square);
		}
		leftPressSquare = Field.NO_SQUARE;
	}

	/**
	 * Handles release of the right mouse button.
	 *
	 * @param square release square
	 */
	private void handleRightRelease(byte square) {
		if (listener == null || rightPressSquare == Field.NO_SQUARE || square == Field.NO_SQUARE) {
			rightPressSquare = Field.NO_SQUARE;
			return;
		}
		BoardMark mark = rightPressSquare == square
				? BoardMark.circle(square, 0)
				: BoardMark.arrow(rightPressSquare, square, 0);
		listener.markRequested(mark);
		rightPressSquare = Field.NO_SQUARE;
	}
}
