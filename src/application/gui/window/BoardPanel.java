package application.gui.window;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.Timer;

import application.gui.model.BoardArrow;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;

/**
 * BoardPanel class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class BoardPanel extends JPanel {

		/**
	 * Serialization version identifier.
	 */
@java.io.Serial
	private static final long serialVersionUID = 1L;
		/**
		 * DRAG_THRESHOLD constant.
		 */
		private static final int DRAG_THRESHOLD = 4;
		/**
		 * DOT_SCALE constant.
		 */
		private static final float DOT_SCALE = 0.22f;
		/**
		 * RING_SCALE constant.
		 */
		private static final float RING_SCALE = 0.78f;
		/**
		 * ARROW_ALPHA constant.
		 */
		private static final int ARROW_ALPHA = 86;
		/**
		 * ARROW_PREVIEW_ALPHA constant.
		 */
		private static final int ARROW_PREVIEW_ALPHA = 68;
		/**
		 * BEST_MOVE_ARROW_ALPHA constant.
		 */
		private static final int BEST_MOVE_ARROW_ALPHA = 132;
		/**
		 * owner field.
		 */
		private final GuiWindowHistory owner;
		/**
		 * boardState field.
		 */
		byte[] boardState = new byte[64];
		/**
		 * boardX field.
		 */
		int boardX;
		/**
		 * boardY field.
		 */
		int boardY;
		/**
		 * boardSize field.
		 */
		int boardSize;
		/**
		 * tileSize field.
		 */
		int tileSize;
		/**
		 * whiteDownView field.
		 */
		boolean whiteDownView = true;
		/**
		 * showLegal field.
		 */
		boolean showLegal;
		/**
		 * showCoords field.
		 */
		boolean showCoords;
		/**
		 * showHoverLegal field.
		 */
		boolean showHoverLegal;
		/**
		 * showHoverHighlight field.
		 */
		boolean showHoverHighlight;
		/**
		 * hoverOnlyLegal field.
		 */
		boolean hoverOnlyLegal;
		/**
		 * selectedSquare field.
		 */
		byte selectedSquare = Field.NO_SQUARE;
		/**
		 * hoverSquare field.
		 */
		byte hoverSquare = Field.NO_SQUARE;
		/**
		 * dragFrom field.
		 */
		byte dragFrom = Field.NO_SQUARE;
		/**
		 * dragPiece field.
		 */
		byte dragPiece = Piece.EMPTY;
		/**
		 * dragX field.
		 */
		int dragX;
		/**
		 * dragY field.
		 */
		int dragY;
		/**
		 * dragStartX field.
		 */
		int dragStartX;
		/**
		 * dragStartY field.
		 */
		int dragStartY;
		/**
		 * dragging field.
		 */
		boolean dragging;
		/**
		 * rightDragFrom field.
		 */
		byte rightDragFrom = Field.NO_SQUARE;
		/**
		 * rightDragTo field.
		 */
		byte rightDragTo = Field.NO_SQUARE;
		/**
		 * rightDragColor field.
		 */
		byte rightDragColor = 1;
		/**
		 * rightDragStartX field.
		 */
		int rightDragStartX;
		/**
		 * rightDragStartY field.
		 */
		int rightDragStartY;
		/**
		 * rightDragging field.
		 */
		boolean rightDragging;
		/**
		 * previewBoard field.
		 */
		byte[] previewBoard;
		/**
		 * previewActive field.
		 */
		boolean previewActive;
		/**
		 * previewMove field.
		 */
		short previewMove = Move.NO_MOVE;
		/**
		 * previewSignature field.
		 */
		int previewSignature = 0;
		/**
		 * previewFrom field.
		 */
		byte previewFrom = Field.NO_SQUARE;
		/**
		 * previewTo field.
		 */
		byte previewTo = Field.NO_SQUARE;
		/**
		 * animatedPieces field.
		 */
		private final List<AnimatedPiece> animatedPieces = new ArrayList<>();
		/**
		 * animatedCaptures field.
		 */
		private final List<AnimatedCapture> animatedCaptures = new ArrayList<>();
		/**
		 * animatedDestinations field.
		 */
		private final boolean[] animatedDestinations = new boolean[64];
		/**
		 * moveAnimationTimer field.
		 */
		private Timer moveAnimationTimer;
		/**
		 * moveAnimationStartNanos field.
		 */
		private long moveAnimationStartNanos = 0L;
		/**
		 * moveAnimationDurationMs field.
		 */
		private int moveAnimationDurationMs = 0;
		/**
		 * moveAnimationActive field.
		 */
		private boolean moveAnimationActive = false;
		/**
		 * lastMoveFrom field.
		 */
		byte lastMoveFrom = Field.NO_SQUARE;
		/**
		 * lastMoveTo field.
		 */
		byte lastMoveTo = Field.NO_SQUARE;
		/**
		 * checkSquare field.
		 */
		byte checkSquare = Field.NO_SQUARE;
		/**
		 * checkMate field.
		 */
		boolean checkMate = false;
		/**
		 * legalTargets field.
		 */
		final boolean[] legalTargets = new boolean[64];
		/**
		 * captureTargets field.
		 */
		final boolean[] captureTargets = new boolean[64];
		/**
		 * markers field.
		 */
		final byte[] markers = new byte[64];
		/**
		 * arrows field.
		 */
		final List<BoardArrow> arrows = new ArrayList<>();
		/**
		 * PIECE_CACHE_SIZE constant.
		 */
		private static final int PIECE_CACHE_SIZE = 13;
		/**
		 * PIECE_CACHE_OFFSET constant.
		 */
		private static final int PIECE_CACHE_OFFSET = 6;
		/**
		 * scaledPieces field.
		 */
		private final BufferedImage[] scaledPieces = new BufferedImage[PIECE_CACHE_SIZE];
		/**
		 * scaledPieceSize field.
		 */
		private int scaledPieceSize = -1;
		/**
		 * cachedLayer field.
		 */
		private BufferedImage cachedLayer;
		/**
		 * renderVersion field.
		 */
		private long renderVersion = 0;
		/**
		 * cachedVersion field.
		 */
		private long cachedVersion = -1;
		/**
		 * cachedWidth field.
		 */
		private int cachedWidth;
		/**
		 * cachedHeight field.
		 */
		private int cachedHeight;
		/**
		 * cachedBoardX field.
		 */
		private int cachedBoardX;
		/**
		 * cachedBoardY field.
		 */
		private int cachedBoardY;
		/**
		 * cachedBoardSize field.
		 */
		private int cachedBoardSize;
		/**
		 * cachedCoordPad field.
		 */
		private int cachedCoordPad;
		/**
		 * cachedShowCoords field.
		 */
		private boolean cachedShowCoords;

		/**
		 * AnimatedPiece record.
		 *
		 * Provides record behavior for the GUI module.
		 *
		 * @since 2026
		 * @author Lennart A. Conrad
		 */
		private record AnimatedPiece(
			/**
			 * Stores the from.
			 */
			byte from,
			/**
			 * Stores the to.
			 */
			byte to,
			/**
			 * Stores the piece.
			 */
			byte piece
		) {
	}

		/**
		 * AnimatedCapture record.
		 *
		 * Provides record behavior for the GUI module.
		 *
		 * @since 2026
		 * @author Lennart A. Conrad
		 */
		private record AnimatedCapture(
			/**
			 * Stores the square.
			 */
			byte square,
			/**
			 * Stores the piece.
			 */
			byte piece
		) {
	}

		/**
		 * BoardPanel method.
		 *
		 * @param owner parameter.
		 */
		BoardPanel(GuiWindowHistory owner) {
		this.owner = owner;
		setOpaque(false);
		setPreferredSize(new Dimension(640, 640));
		setDoubleBuffered(true);
		setFocusable(true);
		setFocusTraversalKeysEnabled(false);
		addFocusListener(new FocusAdapter() {
						/**
			 * Handles focus gained.
			 * @param e e value
			 */
@Override
			public void focusGained(FocusEvent e) {
				repaint();
			}

						/**
			 * Handles focus lost.
			 * @param e e value
			 */
@Override
			public void focusLost(FocusEvent e) {
				repaint();
			}
		});
		MouseAdapter mouseHandler = new MouseAdapter() {
						/**
			 * Handles mouse pressed.
			 * @param e e value
			 */
@Override
			public void mousePressed(MouseEvent e) {
				handleMousePressed(e);
			}

						/**
			 * Handles mouse dragged.
			 * @param e e value
			 */
@Override
			public void mouseDragged(MouseEvent e) {
				handleMouseDragged(e);
			}

						/**
			 * Handles mouse released.
			 * @param e e value
			 */
@Override
			public void mouseReleased(MouseEvent e) {
				handleMouseReleased(e);
			}

						/**
			 * Handles mouse moved.
			 * @param e e value
			 */
@Override
			public void mouseMoved(MouseEvent e) {
				handleMouseMoved(e);
			}

						/**
			 * Handles mouse exited.
			 * @param e e value
			 */
@Override
			public void mouseExited(MouseEvent e) {
				handleMouseExit();
			}
		};
		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);
	}

		/**
		 * setState method.
		 *
		 * @param position parameter.
		 * @param whiteDown parameter.
		 * @param showLegal parameter.
		 * @param showCoords parameter.
		 * @param showHoverLegal parameter.
		 * @param showHoverHighlight parameter.
		 * @param hoverOnlyLegal parameter.
		 */
		void setState(Position position, boolean whiteDown, boolean showLegal, boolean showCoords,
			boolean showHoverLegal, boolean showHoverHighlight, boolean hoverOnlyLegal) {
		byte[] previousBoard = boardState == null ? null : Arrays.copyOf(boardState, boardState.length);
		this.whiteDownView = whiteDown;
		this.showLegal = showLegal;
		this.showCoords = showCoords;
		this.showHoverLegal = showHoverLegal;
		this.showHoverHighlight = showHoverHighlight;
		this.hoverOnlyLegal = hoverOnlyLegal;
		if (!showHoverLegal && !showHoverHighlight) {
			hoverSquare = Field.NO_SQUARE;
		}
		this.boardState = position.getBoard();
		/**
		 * transitionMove field.
		 */
		short transitionMove = Move.NO_MOVE;
		if (!owner.consumeSkipNextBoardAnimation()) {
			transitionMove = owner.consumeAnimationMove();
			if (transitionMove == Move.NO_MOVE) {
				transitionMove = owner.lastMove;
			}
		}
		/**
		 * maybeStartMoveAnimation method.
		 *
		 * @param previousBoard parameter.
		 * @param thisboardState parameter.
		 * @param transitionMove parameter.
		 */
		maybeStartMoveAnimation(previousBoard, this.boardState, transitionMove);
		dragging = false;
		dragFrom = Field.NO_SQUARE;
		dragPiece = Piece.EMPTY;
		if (owner.lastMove != Move.NO_MOVE) {
			lastMoveFrom = Move.getFromIndex(owner.lastMove);
			lastMoveTo = Move.getToIndex(owner.lastMove);
		} else {
			lastMoveFrom = Field.NO_SQUARE;
			lastMoveTo = Field.NO_SQUARE;
		}
		if (position.inCheck()) {
			checkSquare = findKingSquare(position.isWhiteToMove());
			checkMate = position.isCheckmate();
		} else {
			checkSquare = Field.NO_SQUARE;
			checkMate = false;
		}
		if (hoverSquare != Field.NO_SQUARE && !canSelectSquare(hoverSquare) && !showHoverHighlight) {
			hoverSquare = Field.NO_SQUARE;
		}
		if (selectedSquare != Field.NO_SQUARE && !canSelectSquare(selectedSquare)) {
			/**
			 * clearSelection method.
			 */
			clearSelection();
		} else {
			/**
			 * updateLegalTargets method.
			 */
			updateLegalTargets();
		}
		/**
		 * invalidateRenderCache method.
		 */
		invalidateRenderCache();
		/**
		 * repaint method.
		 */
		repaint();
	}

		/**
		 * maybeStartMoveAnimation method.
		 *
		 * @param oldBoard parameter.
		 * @param newBoard parameter.
		 * @param move parameter.
		 */
		private void maybeStartMoveAnimation(byte[] oldBoard, byte[] newBoard, short move) {
		clearMoveAnimation();
		int duration = owner.getAnimationMillis();
		if (duration <= 0 || oldBoard == null || newBoard == null || move == Move.NO_MOVE) {
			return;
		}
		if (oldBoard.length != 64 || newBoard.length != 64) {
			return;
		}
		if (Arrays.equals(oldBoard, newBoard)) {
			return;
		}
		byte from = Move.getFromIndex(move);
		byte to = Move.getToIndex(move);
		if (from == Field.NO_SQUARE || to == Field.NO_SQUARE || from == to) {
			return;
		}
		byte movingPiece = oldBoard[from];
		if (Piece.isEmpty(movingPiece)) {
			return;
		}
		byte arrivedPiece = newBoard[to];
		if (Piece.isEmpty(arrivedPiece)) {
			return;
		}
		boolean sameColor = (Piece.isWhite(movingPiece) && Piece.isWhite(arrivedPiece))
				|| (Piece.isBlack(movingPiece) && Piece.isBlack(arrivedPiece));
		if (!sameColor) {
			return;
		}
		int diffSquares = 0;
		for (int i = 0; i < 64; i++) {
			if (oldBoard[i] != newBoard[i]) {
				diffSquares++;
			}
		}
		if (diffSquares > 6) {
			return;
		}
		List<AnimatedPiece> pieces = new ArrayList<>();
		pieces.add(new AnimatedPiece(from, to, movingPiece));
		int fromFile = Field.getX(from);
		int toFile = Field.getX(to);
		boolean kingMove = movingPiece == Piece.WHITE_KING || movingPiece == Piece.BLACK_KING;
		if (kingMove && Math.abs(toFile - fromFile) == 2) {
			AnimatedPiece rookAnimation = detectCastlingRookAnimation(oldBoard, newBoard, movingPiece, from);
			if (rookAnimation == null) {
				return;
			}
			pieces.add(rookAnimation);
		}
		List<AnimatedCapture> captures = detectAnimatedCaptures(oldBoard, newBoard, from, to, movingPiece);
		startMoveAnimation(pieces, captures, duration);
	}

	/**
	 * detectCastlingRookAnimation method.
	 *
	 * @param oldBoard parameter.
	 * @param newBoard parameter.
	 * @param movingPiece parameter.
	 * @param kingFrom parameter.
	 * @return return value.
	 */
	private AnimatedPiece detectCastlingRookAnimation(byte[] oldBoard, byte[] newBoard, byte movingPiece, byte kingFrom) {
		int rank = Field.getY(kingFrom);
		byte rookPiece = Piece.isWhite(movingPiece) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
		int[][] candidates = {
				{ 7, 5 }, // forward kingside
				{ 0, 3 }, // forward queenside
				{ 5, 7 }, // reverse kingside
				{ 3, 0 } // reverse queenside
		};
		for (int[] candidate : candidates) {
			byte rookFrom = (byte) Field.toIndex(candidate[0], rank);
			byte rookTo = (byte) Field.toIndex(candidate[1], rank);
			if (rookFrom == kingFrom || rookTo == kingFrom) {
				continue;
			}
			if (oldBoard[rookFrom] != rookPiece || newBoard[rookTo] != rookPiece) {
				continue;
			}
			if (newBoard[rookFrom] == rookPiece || oldBoard[rookTo] == rookPiece) {
				continue;
			}
			return new AnimatedPiece(rookFrom, rookTo, rookPiece);
		}
		return null;
	}

		/**
		 * detectAnimatedCaptures method.
		 *
		 * @param oldBoard parameter.
		 * @param newBoard parameter.
		 * @param from parameter.
		 * @param to parameter.
		 * @param movingPiece parameter.
		 * @return return value.
		 */
		private List<AnimatedCapture> detectAnimatedCaptures(
				byte[] oldBoard, byte[] newBoard, byte from, byte to, byte movingPiece) {
		List<AnimatedCapture> captures = new ArrayList<>(1);
		/**
		 * targetPiece field.
		 */
		byte targetPiece = oldBoard[to];
		if (!Piece.isEmpty(targetPiece)) {
			boolean oppositeColor = (Piece.isWhite(movingPiece) && Piece.isBlack(targetPiece))
					|| (Piece.isBlack(movingPiece) && Piece.isWhite(targetPiece));
			if (oppositeColor) {
				captures.add(new AnimatedCapture(to, targetPiece));
				return captures;
			}
		}
		/**
		 * pawn field.
		 */
		boolean pawn = movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN;
		boolean diagonal = Field.getX(from) != Field.getX(to);
		if (pawn && diagonal && Piece.isEmpty(targetPiece)) {
			int captureSquare = Piece.isWhite(movingPiece) ? to - 8 : to + 8;
			if (captureSquare >= 0 && captureSquare < 64) {
				/**
				 * captured field.
				 */
				byte captured = oldBoard[captureSquare];
				boolean oppositeColor = !Piece.isEmpty(captured)
						&& ((Piece.isWhite(movingPiece) && Piece.isBlack(captured))
								|| (Piece.isBlack(movingPiece) && Piece.isWhite(captured)));
				if (oppositeColor && Piece.isEmpty(newBoard[captureSquare])) {
					captures.add(new AnimatedCapture((byte) captureSquare, captured));
				}
			}
		}
		return captures;
	}

		/**
		 * startMoveAnimation method.
		 *
		 * @param pieces parameter.
		 * @param captures parameter.
		 * @param durationMs parameter.
		 */
		private void startMoveAnimation(List<AnimatedPiece> pieces, List<AnimatedCapture> captures, int durationMs) {
		if (pieces == null || pieces.isEmpty() || durationMs <= 0) {
			return;
		}
		animatedPieces.clear();
		animatedPieces.addAll(pieces);
		animatedCaptures.clear();
		if (captures != null && !captures.isEmpty()) {
			animatedCaptures.addAll(captures);
		}
		Arrays.fill(animatedDestinations, false);
		for (AnimatedPiece piece : animatedPieces) {
			if (piece != null && piece.to() != Field.NO_SQUARE) {
				animatedDestinations[piece.to()] = true;
			}
		}
		moveAnimationDurationMs = Math.max(1, durationMs);
		moveAnimationStartNanos = System.nanoTime();
		moveAnimationActive = true;
		if (moveAnimationTimer == null) {
			moveAnimationTimer = new Timer(16, e -> onMoveAnimationTick());
			moveAnimationTimer.setCoalesce(true);
		}
		if (!moveAnimationTimer.isRunning()) {
			moveAnimationTimer.start();
		}
		invalidateRenderCache();
	}

		/**
		 * onMoveAnimationTick method.
		 */
		private void onMoveAnimationTick() {
		if (!moveAnimationActive) {
			if (moveAnimationTimer != null && moveAnimationTimer.isRunning()) {
				moveAnimationTimer.stop();
			}
			return;
		}
		if (moveAnimationProgress() >= 1f) {
			clearMoveAnimation();
		}
		invalidateRenderCache();
		repaint();
	}

		/**
		 * moveAnimationProgress method.
		 *
		 * @return return value.
		 */
		private float moveAnimationProgress() {
		if (!moveAnimationActive || moveAnimationDurationMs <= 0) {
			return 1f;
		}
		long elapsedNanos = System.nanoTime() - moveAnimationStartNanos;
		float elapsedMs = elapsedNanos / 1_000_000f;
		if (elapsedMs <= 0f) {
			return 0f;
		}
		return Math.max(0f, Math.min(1f, elapsedMs / moveAnimationDurationMs));
	}

		/**
		 * clearMoveAnimation method.
		 */
		private void clearMoveAnimation() {
		moveAnimationActive = false;
		moveAnimationStartNanos = 0L;
		moveAnimationDurationMs = 0;
		animatedPieces.clear();
		animatedCaptures.clear();
		Arrays.fill(animatedDestinations, false);
		if (moveAnimationTimer != null && moveAnimationTimer.isRunning()) {
			moveAnimationTimer.stop();
		}
		invalidateRenderCache();
	}

		/**
		 * snapshot method.
		 *
		 * @param position parameter.
		 * @param whiteDown parameter.
		 * @param showLegal parameter.
		 * @param showCoords parameter.
		 * @return return value.
		 */
		BufferedImage snapshot(Position position, boolean whiteDown, boolean showLegal, boolean showCoords) {
		this.whiteDownView = whiteDown;
		this.showLegal = showLegal;
		this.showCoords = showCoords;
		this.boardState = position.getBoard();
		if (position.inCheck()) {
			checkSquare = findKingSquare(position.isWhiteToMove());
			checkMate = position.isCheckmate();
		} else {
			checkSquare = Field.NO_SQUARE;
			checkMate = false;
		}
		updateLegalTargets();
		int size = boardSize > 0 ? boardSize : 640;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = image.createGraphics();
		owner.applyRenderHints(g2);
		renderBoard(g2, 0, 0, size, false);
		g2.dispose();
		return image;
	}

		/**
		 * handleMousePressed method.
		 *
		 * @param e parameter.
		 */
		private void handleMousePressed(MouseEvent e) {
		if (!isFocusOwner()) {
			requestFocusInWindow();
		}
		if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
			byte sq = squareFromPoint(e.getX(), e.getY());
			rightDragFrom = sq;
			rightDragTo = sq;
			rightDragColor = annotationColorIndex(e);
			rightDragStartX = e.getX();
			rightDragStartY = e.getY();
			rightDragging = false;
			if (e.isPopupTrigger() && e.isControlDown()) {
				showBoardMenu(e);
				rightDragFrom = Field.NO_SQUARE;
				rightDragTo = Field.NO_SQUARE;
			}
			return;
		}
		if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) {
			return;
		}
		clearAnnotationArrows();
		byte sq = squareFromPoint(e.getX(), e.getY());
		if (sq == Field.NO_SQUARE) {
			clearSelection();
			updateLegalTargets();
			repaint();
			return;
		}
		if (canSelectSquare(sq)) {
			selectedSquare = sq;
			dragFrom = sq;
			dragPiece = boardState[sq];
			dragStartX = e.getX();
			dragStartY = e.getY();
			dragX = e.getX();
			dragY = e.getY();
			dragging = true;
			invalidateRenderCache();
			hoverSquare = Field.NO_SQUARE;
			updateLegalTargets();
			repaint();
			return;
		}
			if (selectedSquare != Field.NO_SQUARE) {
				attemptMove(selectedSquare, sq, false);
				clearSelection();
				updateLegalTargets();
				repaint();
			return;
		}
		clearSelection();
		updateLegalTargets();
		repaint();
	}

		/**
		 * handleMouseDragged method.
		 *
		 * @param e parameter.
		 */
		private void handleMouseDragged(MouseEvent e) {
			if (dragFrom == Field.NO_SQUARE && rightDragFrom == Field.NO_SQUARE) {
				return;
			}
			if (rightDragFrom != Field.NO_SQUARE && (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0) {
			int dx = e.getX() - rightDragStartX;
			int dy = e.getY() - rightDragStartY;
			if (!rightDragging && dx * dx + dy * dy > DRAG_THRESHOLD * DRAG_THRESHOLD) {
				rightDragging = true;
			}
			if (rightDragging) {
				byte sq = squareFromPoint(e.getX(), e.getY());
				if (sq != rightDragTo) {
					rightDragTo = sq;
					repaint();
				}
			}
			return;
		}
		if (hoverSquare != Field.NO_SQUARE) {
			hoverSquare = Field.NO_SQUARE;
		}
		int dx = e.getX() - dragStartX;
		int dy = e.getY() - dragStartY;
		boolean dragStarted = false;
		if (!dragging && dx * dx + dy * dy > DRAG_THRESHOLD * DRAG_THRESHOLD) {
			dragging = true;
			invalidateRenderCache();
			dragStarted = true;
		}
		if (dragging) {
			int oldX = dragX;
			int oldY = dragY;
			dragX = e.getX();
			dragY = e.getY();
			if (dragStarted) {
				repaint();
			} else {
				repaintDrag(oldX, oldY, dragX, dragY);
			}
		}
	}

		/**
		 * handleMouseReleased method.
		 *
		 * @param e parameter.
		 */
		private void handleMouseReleased(MouseEvent e) {
		if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
			byte target = squareFromPoint(e.getX(), e.getY());
			if (rightDragging && target != Field.NO_SQUARE && target != rightDragFrom) {
				toggleArrow(rightDragFrom, target, rightDragColor);
			} else {
				if (e.isControlDown()) {
					showBoardMenu(e);
				} else if (target != Field.NO_SQUARE) {
					toggleMarker(target, annotationColorIndex(e));
				} else if (e.isPopupTrigger()) {
					showBoardMenu(e);
				}
			}
			rightDragFrom = Field.NO_SQUARE;
			rightDragTo = Field.NO_SQUARE;
			rightDragging = false;
			repaint();
			return;
		}
		if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) {
			return;
		}
		if (dragFrom == Field.NO_SQUARE) {
			return;
		}
		byte target = squareFromPoint(e.getX(), e.getY());
		if (target != Field.NO_SQUARE && target != dragFrom) {
			attemptMove(dragFrom, target, true);
		}
		clearSelection();
		updateLegalTargets();
		dragFrom = Field.NO_SQUARE;
		dragPiece = Piece.EMPTY;
		dragging = false;
		invalidateRenderCache();
		repaint();
	}

		/**
		 * handleMouseMoved method.
		 *
		 * @param e parameter.
		 */
		private void handleMouseMoved(MouseEvent e) {
		if ((!showHoverLegal && !showHoverHighlight) || dragging || dragFrom != Field.NO_SQUARE) {
			return;
		}
		byte sq = squareFromPoint(e.getX(), e.getY());
		if (sq != Field.NO_SQUARE && !showHoverHighlight && !canSelectSquare(sq)) {
			sq = Field.NO_SQUARE;
		}
		if (sq == hoverSquare) {
			return;
		}
		hoverSquare = sq;
		updateLegalTargets();
		repaint();
	}

		/**
		 * handleMouseExit method.
		 */
		private void handleMouseExit() {
		if (hoverSquare == Field.NO_SQUARE) {
			return;
		}
		hoverSquare = Field.NO_SQUARE;
		updateLegalTargets();
		repaint();
	}

		/**
		 * showBoardMenu method.
		 *
		 * @param e parameter.
		 */
		private void showBoardMenu(MouseEvent e) {
		if (owner.boardMenu == null) {
			return;
		}
		owner.boardMenu.show(this, e.getX(), e.getY());
	}

		/**
		 * toggleMarker method.
		 *
		 * @param square parameter.
		 * @param color parameter.
		 */
		private void toggleMarker(byte square, byte color) {
		if (square == Field.NO_SQUARE) {
			return;
		}
		byte current = markers[square];
		if (current == color) {
			markers[square] = 0;
		} else {
			markers[square] = color;
		}
		invalidateRenderCache();
	}

		/**
		 * toggleArrow method.
		 *
		 * @param from parameter.
		 * @param to parameter.
		 * @param color parameter.
		 */
		private void toggleArrow(byte from, byte to, byte color) {
		if (from == Field.NO_SQUARE || to == Field.NO_SQUARE || from == to) {
			return;
		}
		for (int i = 0; i < arrows.size(); i++) {
			BoardArrow arrow = arrows.get(i);
			if (arrow.from() == from && arrow.to() == to) {
				if (arrow.color() == color) {
					arrows.remove(i);
				} else {
					arrows.set(i, new BoardArrow(from, to, color));
				}
				return;
			}
		}
		arrows.add(new BoardArrow(from, to, color));
		invalidateRenderCache();
	}

		/**
		 * clearAnnotationArrows method.
		 */
		private void clearAnnotationArrows() {
		boolean changed = !arrows.isEmpty();
		if (changed) {
			arrows.clear();
		}
		for (int i = 0; i < markers.length; i++) {
			if (markers[i] != 0) {
				markers[i] = 0;
				changed = true;
			}
		}
		if (!changed) {
			return;
		}
		invalidateRenderCache();
		repaint();
	}

		/**
		 * clearShapes method.
		 */
		void clearShapes() {
		arrows.clear();
		Arrays.fill(markers, (byte) 0);
		invalidateRenderCache();
		repaint();
	}

		/**
		 * invalidateRenderCache method.
		 */
		void invalidateRenderCache() {
		renderVersion++;
		cachedLayer = null;
	}

		/**
		 * attemptMove method.
		 *
		 * @param from parameter.
		 * @param to parameter.
		 * @param fromDrag parameter.
		 * @return return value.
		 */
		private boolean attemptMove(byte from, byte to, boolean fromDrag) {
		if (from == Field.NO_SQUARE || to == Field.NO_SQUARE || from == to) {
			return false;
		}
		if (fromDrag) {
			owner.skipNextBoardAnimation();
		}
		return owner.playMove(from, to);
	}

		/**
		 * repaintDrag method.
		 *
		 * @param oldX parameter.
		 * @param oldY parameter.
		 * @param newX parameter.
		 * @param newY parameter.
		 */
		private void repaintDrag(int oldX, int oldY, int newX, int newY) {
		int tile = tileSize > 0 ? tileSize : Math.max(1, Math.min(getWidth(), getHeight()) / 8);
		int pieceSize = Math.round(tile * GuiWindowBase.PIECE_SCALE);
		int pad = Math.max(Math.max(tile, pieceSize) / 2 + 6, Math.max(12, pieceSize));
		int minX = Math.min(oldX, newX) - pad;
		int minY = Math.min(oldY, newY) - pad;
		int maxX = Math.max(oldX, newX) + pad;
		int maxY = Math.max(oldY, newY) + pad;

		int ghostPad = Math.max(3, pieceSize / 6);
		int[] ghostOld = ghostBounds(oldX, oldY, tile, pieceSize, ghostPad);
		if (ghostOld != null) {
			minX = Math.min(minX, ghostOld[0]);
			minY = Math.min(minY, ghostOld[1]);
			maxX = Math.max(maxX, ghostOld[2]);
			maxY = Math.max(maxY, ghostOld[3]);
		}
		int[] ghostNew = ghostBounds(newX, newY, tile, pieceSize, ghostPad);
		if (ghostNew != null) {
			minX = Math.min(minX, ghostNew[0]);
			minY = Math.min(minY, ghostNew[1]);
			maxX = Math.max(maxX, ghostNew[2]);
			maxY = Math.max(maxY, ghostNew[3]);
		}
		repaint(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
	}

		/**
		 * ghostBounds method.
		 *
		 * @param x parameter.
		 * @param y parameter.
		 * @param tile parameter.
		 * @param pieceSize parameter.
		 * @param pad parameter.
		 * @return return value.
		 */
		private int[] ghostBounds(int x, int y, int tile, int pieceSize, int pad) {
		byte target = squareFromPoint(x, y);
		if (target == Field.NO_SQUARE || target == dragFrom) {
			return null;
		}
		if (showLegal && !legalTargets[target]) {
			return null;
		}
		int sx = screenFile(target);
		int sy = screenRank(target);
		int piecePad = (tile - pieceSize) / 2;
		int gx = boardX + sx * tile + piecePad;
		int gy = boardY + sy * tile + piecePad;
		return new int[] { gx - pad, gy - pad, gx + pieceSize + pad, gy + pieceSize + pad };
	}

		/**
		 * clearSelection method.
		 */
		private void clearSelection() {
		selectedSquare = Field.NO_SQUARE;
		Arrays.fill(legalTargets, false);
		Arrays.fill(captureTargets, false);
	}

		/**
		 * updateLegalTargets method.
		 */
		private void updateLegalTargets() {
		Arrays.fill(legalTargets, false);
		Arrays.fill(captureTargets, false);
		byte source = (selectedSquare != Field.NO_SQUARE && !hoverOnlyLegal) ? selectedSquare
				: (showHoverLegal ? hoverSquare : Field.NO_SQUARE);
		if (!showLegal || source == Field.NO_SQUARE || !canSelectSquare(source)) {
			return;
		}
		MoveList moves = owner.position.legalMoves();
		for (int i = 0; i < moves.size(); i++) {
			short move = moves.get(i);
			if (Move.getFromIndex(move) != source) {
				continue;
			}
			byte to = Move.getToIndex(move);
			legalTargets[to] = true;
			captureTargets[to] = owner.position.isCapture(source, to);
		}
	}

		/**
		 * canSelectSquare method.
		 *
		 * @param square parameter.
		 * @return return value.
		 */
		private boolean canSelectSquare(byte square) {
		if (square == Field.NO_SQUARE || boardState == null) {
			return false;
		}
		if (!owner.canUserPlayCurrentTurn()) {
			return false;
		}
		byte piece = boardState[square];
		if (Piece.isEmpty(piece)) {
			return false;
			}
			return (owner.position.isWhiteToMove() && Piece.isWhite(piece))
					|| (!owner.position.isWhiteToMove() && Piece.isBlack(piece));
	}

		/**
		 * squareFromPoint method.
		 *
		 * @param x parameter.
		 * @param y parameter.
		 * @return return value.
		 */
		private byte squareFromPoint(int x, int y) {
		if (boardSize <= 0 || tileSize <= 0) {
			return Field.NO_SQUARE;
		}
		int relX = x - boardX;
		int relY = y - boardY;
		if (relX < 0 || relY < 0 || relX >= boardSize || relY >= boardSize) {
			return Field.NO_SQUARE;
		}
		int file = Math.min(7, relX / tileSize);
		int rank = Math.min(7, relY / tileSize);
		int boardFile = whiteDownView ? file : 7 - file;
		int boardRank = whiteDownView ? 7 - rank : rank;
		return (byte) Field.toIndex(boardFile, boardRank);
	}

		/**
		 * screenFile method.
		 *
		 * @param square parameter.
		 * @return return value.
		 */
		int screenFile(byte square) {
		int file = Field.getX(square);
		return whiteDownView ? file : 7 - file;
	}

		/**
		 * screenRank method.
		 *
		 * @param square parameter.
		 * @return return value.
		 */
		int screenRank(byte square) {
		int rank = Field.getY(square);
		return whiteDownView ? 7 - rank : rank;
	}

		/**
		 * findKingSquare method.
		 *
		 * @param whiteTurn parameter.
		 * @return return value.
		 */
		private byte findKingSquare(boolean whiteTurn) {
		byte king = whiteTurn ? Piece.WHITE_KING : Piece.BLACK_KING;
		for (int i = 0; i < boardState.length; i++) {
			if (boardState[i] == king) {
				return (byte) i;
			}
		}
		return Field.NO_SQUARE;
	}

		/**
		 * renderBoard method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param size parameter.
		 */
		private void renderBoard(Graphics2D g2, int originX, int originY, int size) {
		renderBoard(g2, originX, originY, size, true);
	}

		/**
		 * renderBoard method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param size parameter.
		 * @param includeDrag parameter.
		 */
		private void renderBoard(Graphics2D g2, int originX, int originY, int size, boolean includeDrag) {
		int tile = size / 8;
		if (tile <= 0) {
			return;
		}
		Color light = owner.boardColor(GuiWindowBase.LICHESS_LIGHT);
		Color dark = owner.boardColor(GuiWindowBase.LICHESS_DARK);

		for (int screenRank = 0; screenRank < 8; screenRank++) {
			for (int screenFile = 0; screenFile < 8; screenFile++) {
				int boardFile = whiteDownView ? screenFile : 7 - screenFile;
				int boardRank = whiteDownView ? 7 - screenRank : screenRank;
				boolean isLight = ((boardFile + boardRank) & 1) == 1;
				g2.setColor(isLight ? light : dark);
				g2.fillRect(originX + screenFile * tile, originY + screenRank * tile, tile, tile);
			}
		}

		drawHighlights(g2, originX, originY, tile);
		drawPieces(g2, originX, originY, tile);
		drawPreviewPieces(g2, originX, originY, tile);
		if (includeDrag) {
			drawAnimatedCaptures(g2, originX, originY, tile);
		}
		if (includeDrag) {
			drawAnimatedPieces(g2, originX, originY, tile);
		}
		if (includeDrag) {
			drawDragGhost(g2, originX, originY, tile);
		}
		drawBestMoveArrow(g2, originX, originY, tile);
		drawShapes(g2, originX, originY, tile);
		drawAblationOverlay(g2, originX, originY, tile);
		if (includeDrag) {
			drawDragPiece(g2, tile);
		}
	}

		/**
		 * drawShapes method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawShapes(Graphics2D g2, int originX, int originY, int tile) {
		drawMarkers(g2, originX, originY, tile);
		drawArrows(g2, originX, originY, tile);
		if (rightDragging && rightDragFrom != Field.NO_SQUARE && rightDragTo != Field.NO_SQUARE
				&& rightDragTo != rightDragFrom) {
			Color base = annotationColor(rightDragColor);
			Color preview = withAlpha(base, ARROW_PREVIEW_ALPHA);
			drawArrow(g2, originX, originY, tile, rightDragFrom, rightDragTo, preview);
		}
	}

		/**
		 * drawMarkers method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawMarkers(Graphics2D g2, int originX, int originY, int tile) {
		BasicStroke ringStroke = new BasicStroke(Math.max(2f, tile * 0.08f));
		java.awt.Stroke prev = g2.getStroke();
		g2.setStroke(ringStroke);
		for (int square = 0; square < markers.length; square++) {
			byte color = markers[square];
			if (color == 0) {
				continue;
			}
			Color base = annotationColor(color);
			Color ring = new Color(base.getRed(), base.getGreen(), base.getBlue(), 150);
			g2.setColor(ring);
			int sx = screenFile((byte) square);
			int sy = screenRank((byte) square);
			int x = originX + sx * tile;
			int y = originY + sy * tile;
			int pad = Math.round(tile * 0.12f);
			g2.drawOval(x + pad, y + pad, tile - pad * 2, tile - pad * 2);
		}
		g2.setStroke(prev);
	}

		/**
		 * drawArrows method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawArrows(Graphics2D g2, int originX, int originY, int tile) {
		if (arrows.isEmpty()) {
			return;
		}
		for (BoardArrow arrowShape : arrows) {
			Color base = annotationColor(arrowShape.color());
			Color arrow = withAlpha(base, ARROW_ALPHA);
			drawArrow(g2, originX, originY, tile, arrowShape.from(), arrowShape.to(), arrow);
		}
	}

		/**
		 * drawArrow method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 * @param from parameter.
		 * @param to parameter.
		 * @param color parameter.
		 */
		private void drawArrow(Graphics2D g2, int originX, int originY, int tile, byte from, byte to, Color color) {
		if (from == Field.NO_SQUARE || to == Field.NO_SQUARE || from == to || tile <= 0) {
			return;
		}
		int fromX = originX + screenFile(from) * tile + tile / 2;
		int fromY = originY + screenRank(from) * tile + tile / 2;
		int toX = originX + screenFile(to) * tile + tile / 2;
		int toY = originY + screenRank(to) * tile + tile / 2;
		double dx = toX - fromX;
		double dy = toY - fromY;
		double len = Math.hypot(dx, dy);
		if (len < 1.0) {
			return;
		}
		double ux = dx / len;
		double uy = dy / len;
		double perpX = -uy;
		double perpY = ux;
		double headSide = Math.max(10, tile * 0.3);
		double headHeight = headSide * 0.8660254037844386;
		double halfHeadBase = headSide * 0.5;
		double tailInset = tile * 0.18;
		// Full shaft width = one third of the head triangle side length.
		double halfShaft = headSide / 6.0;
		double shaftLen = len - tailInset - headHeight;
		if (shaftLen <= 0.5) {
			return;
		}
		double tailX = fromX + ux * tailInset;
		double tailY = fromY + uy * tailInset;
		double neckX = tailX + ux * shaftLen;
		double neckY = tailY + uy * shaftLen;
		int[] xs = {
				(int) Math.round(tailX + perpX * halfShaft),
				(int) Math.round(neckX + perpX * halfShaft),
				(int) Math.round(neckX + perpX * halfHeadBase),
				toX,
				(int) Math.round(neckX - perpX * halfHeadBase),
				(int) Math.round(neckX - perpX * halfShaft),
				(int) Math.round(tailX - perpX * halfShaft)
		};
		int[] ys = {
				(int) Math.round(tailY + perpY * halfShaft),
				(int) Math.round(neckY + perpY * halfShaft),
				(int) Math.round(neckY + perpY * halfHeadBase),
				toY,
				(int) Math.round(neckY - perpY * halfHeadBase),
				(int) Math.round(neckY - perpY * halfShaft),
				(int) Math.round(tailY - perpY * halfShaft)
		};
		g2.setColor(color);
		g2.fillPolygon(xs, ys, xs.length);
	}

		/**
		 * withAlpha method.
		 *
		 * @param color parameter.
		 * @param alpha parameter.
		 * @return return value.
		 */
		private static Color withAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

		/**
		 * annotationColor method.
		 *
		 * @param color parameter.
		 * @return return value.
		 */
		private Color annotationColor(byte color) {
		Color base;
		switch (color) {
			case 2:
				base = new Color(219, 68, 68);
				break;
			case 3:
				base = new Color(70, 142, 247);
				break;
			case 4:
				base = new Color(235, 196, 68);
				break;
			default:
				base = owner.theme.accent();
				break;
		}
		if (!owner.lightMode) {
			base = owner.blend(base, Color.WHITE, 0.15f);
		}
		return base;
	}

		/**
		 * annotationColorIndex method.
		 *
		 * @param e parameter.
		 * @return return value.
		 */
		private byte annotationColorIndex(MouseEvent e) {
		boolean shift = e.isShiftDown();
		boolean ctrl = e.isControlDown();
		if (shift && ctrl) {
			return 4;
		}
		if (shift) {
			return 2;
		}
		if (ctrl) {
			return 3;
		}
		return 1;
	}

		/**
		 * bestMoveColor method.
		 *
		 * @return return value.
		 */
		private Color bestMoveColor() {
		return owner.lightMode ? new Color(36, 170, 90) : new Color(110, 230, 150);
	}

		/**
		 * drawCoordinates method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 * @param pad parameter.
		 */
		private void drawCoordinates(Graphics2D g2, int originX, int originY, int tile, int pad) {
		if (pad <= 0 || tile <= 0) {
			return;
		}
		float fontSize = Math.max(10f * owner.uiScale, tile * 0.22f);
		g2.setFont(owner.theme.bodyFont().deriveFont(java.awt.Font.BOLD, fontSize));
		java.awt.FontMetrics fm = g2.getFontMetrics();
		int ascent = fm.getAscent();
		int descent = fm.getDescent();
		int leftBase = originX - pad;
		int rightBase = originX + boardSize;
		int topBase = originY - pad;
		int bottomBase = originY + boardSize;
		Color coordColor = owner.lightMode ? new Color(72, 72, 72, 200) : new Color(205, 198, 216, 210);
		g2.setColor(coordColor);

		for (int file = 0; file < 8; file++) {
			char fileChar = (char) (whiteDownView ? ('a' + file) : ('h' - file));
			String text = String.valueOf(fileChar);
			int textW = fm.stringWidth(text);
			int x = originX + file * tile + (tile - textW) / 2;
			int topY = topBase + Math.max(ascent, (pad + ascent) / 2);
			int bottomY = bottomBase + Math.max(ascent, (pad + ascent) / 2);
			g2.drawString(text, x, topY);
			g2.drawString(text, x, bottomY);
		}

		for (int screenRank = 0; screenRank < 8; screenRank++) {
			int boardRank = whiteDownView ? 7 - screenRank : screenRank;
			char rankChar = (char) ('1' + boardRank);
			String text = String.valueOf(rankChar);
			int textW = fm.stringWidth(text);
			int y = originY + screenRank * tile + (tile + ascent - descent) / 2;
			int leftX = leftBase + Math.max(0, (pad - textW) / 2);
			int rightX = rightBase + Math.max(0, (pad - textW) / 2);
			g2.drawString(text, leftX, y);
			g2.drawString(text, rightX, y);
		}
	}

		/**
		 * drawHighlights method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawHighlights(Graphics2D g2, int originX, int originY, int tile) {
		if (lastMoveFrom != Field.NO_SQUARE && lastMoveTo != Field.NO_SQUARE) {
			Color fromColor = owner.lightMode ? new Color(255, 248, 219, 140) : new Color(96, 84, 52, 130);
			Color toColor = owner.lightMode ? new Color(255, 236, 169, 190) : new Color(122, 104, 64, 170);
			int fx = screenFile(lastMoveFrom);
			int fy = screenRank(lastMoveFrom);
			int tx = screenFile(lastMoveTo);
			int ty = screenRank(lastMoveTo);
			g2.setColor(fromColor);
			g2.fillRect(originX + fx * tile, originY + fy * tile, tile, tile);
			g2.setColor(toColor);
			g2.fillRect(originX + tx * tile, originY + ty * tile, tile, tile);
		}

		if (checkSquare != Field.NO_SQUARE) {
			int cx = screenFile(checkSquare);
			int cy = screenRank(checkSquare);
			int x = originX + cx * tile;
			int y = originY + cy * tile;
			float centerX = x + tile / 2f;
			float centerY = y + tile / 2f;
			float radius = tile * (checkMate ? 0.9f : 0.75f);
			float[] dist = { 0f, 0.7f, 1f };
			Color inner = checkMate ? new Color(255, 0, 0, 200) : new Color(230, 40, 40, 170);
			Color mid = checkMate ? new Color(200, 0, 0, 160) : new Color(180, 30, 30, 120);
			Color outer = new Color(0, 0, 0, 0);
			java.awt.Paint prevPaint = g2.getPaint();
			java.awt.RadialGradientPaint paint = new java.awt.RadialGradientPaint(centerX, centerY, radius, dist,
					new Color[] { inner, mid, outer });
			g2.setPaint(paint);
			g2.fillRect(x, y, tile, tile);
			g2.setPaint(prevPaint);
		}

		if (selectedSquare != Field.NO_SQUARE) {
			int sx = screenFile(selectedSquare);
			int sy = screenRank(selectedSquare);
			Color selectColor = owner.withAlpha(owner.theme.accent(), 80);
			g2.setColor(selectColor);
			g2.fillRect(originX + sx * tile, originY + sy * tile, tile, tile);
		}

		if (showHoverHighlight && hoverSquare != Field.NO_SQUARE && hoverSquare != selectedSquare && !dragging) {
			int hx = screenFile(hoverSquare);
			int hy = screenRank(hoverSquare);
			Color hoverColor = owner.withAlpha(owner.theme.accent(), 40);
			g2.setColor(hoverColor);
			g2.fillRect(originX + hx * tile, originY + hy * tile, tile, tile);
		}

		if (previewActive && previewTo != Field.NO_SQUARE) {
			int px = screenFile(previewTo);
			int py = screenRank(previewTo);
			Color previewColor = owner.withAlpha(owner.theme.accent(), 95);
			g2.setColor(previewColor);
			g2.fillRect(originX + px * tile, originY + py * tile, tile, tile);
		}

		if (!showLegal) {
			return;
		}
		if (selectedSquare == Field.NO_SQUARE && (!showHoverLegal || hoverSquare == Field.NO_SQUARE)) {
			return;
		}

		Color hintColor = owner.lightMode ? new Color(0, 0, 0, 85) : new Color(255, 255, 255, 90);
		g2.setColor(hintColor);
		BasicStroke ringStroke = new BasicStroke(Math.max(2f, tile * 0.08f));
		for (int square = 0; square < legalTargets.length; square++) {
			if (!legalTargets[square]) {
				continue;
			}
			int sx = screenFile((byte) square);
			int sy = screenRank((byte) square);
			int centerX = originX + sx * tile + tile / 2;
			int centerY = originY + sy * tile + tile / 2;
			if (captureTargets[square]) {
				int ringSize = Math.round(tile * RING_SCALE);
				int radius = ringSize / 2;
				java.awt.Stroke previous = g2.getStroke();
				g2.setStroke(ringStroke);
				g2.drawOval(centerX - radius, centerY - radius, ringSize, ringSize);
				g2.setStroke(previous);
			} else {
				int dotSize = Math.max(6, Math.round(tile * DOT_SCALE));
				int radius = dotSize / 2;
				g2.fillOval(centerX - radius, centerY - radius, dotSize, dotSize);
			}
		}
	}

		/**
		 * drawPieces method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawPieces(Graphics2D g2, int originX, int originY, int tile) {
		if (boardState == null) {
			return;
		}
		int pieceSize = Math.round(tile * GuiWindowBase.PIECE_SCALE);
		int pad = (tile - pieceSize) / 2;
		Composite previousComposite = null;
		for (int square = 0; square < boardState.length; square++) {
			byte piece = boardState[square];
			if (Piece.isEmpty(piece)) {
				continue;
			}
			if (moveAnimationActive && square >= 0 && square < animatedDestinations.length
					&& animatedDestinations[square]) {
				continue;
			}
			BufferedImage image = scaledPiece(piece, tile);
			if (image == null) {
				continue;
			}
			boolean dragOrigin = dragging && square == dragFrom;
			boolean fade = previewActive && !dragging && isPreviewOriginSquare(square, piece);
			if (dragOrigin || fade) {
				previousComposite = g2.getComposite();
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
			}
			int sx = screenFile((byte) square);
			int sy = screenRank((byte) square);
			int x = originX + sx * tile + pad;
			int y = originY + sy * tile + pad;
			g2.drawImage(image, x, y, null);
			if ((dragOrigin || fade) && previousComposite != null) {
				g2.setComposite(previousComposite);
				previousComposite = null;
			}
		}
	}

		/**
		 * isPreviewOriginSquare method.
		 *
		 * @param square parameter.
		 * @param piece parameter.
		 * @return return value.
		 */
		private boolean isPreviewOriginSquare(int square, byte piece) {
		if (!previewActive || previewBoard == null || boardState == null) {
			return false;
		}
		if (Piece.isEmpty(piece)) {
			return false;
		}
		if (owner.position.isWhiteToMove() && !Piece.isWhite(piece)) {
			return false;
		}
			if (!owner.position.isWhiteToMove() && !Piece.isBlack(piece)) {
			return false;
		}
		return Piece.isEmpty(previewBoard[square]);
	}

		/**
		 * drawPreviewPieces method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawPreviewPieces(Graphics2D g2, int originX, int originY, int tile) {
		if (!previewActive || previewBoard == null || boardState == null || dragging) {
			return;
		}
		int pieceSize = Math.round(tile * GuiWindowBase.PIECE_SCALE);
		int pad = (tile - pieceSize) / 2;
		Composite previous = g2.getComposite();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
		for (int square = 0; square < previewBoard.length; square++) {
			byte piece = previewBoard[square];
			if (Piece.isEmpty(piece)) {
				continue;
			}
			if (boardState[square] == piece) {
				continue;
			}
			BufferedImage image = scaledPiece(piece, tile);
			if (image == null) {
				continue;
			}
			int sx = screenFile((byte) square);
			int sy = screenRank((byte) square);
			int x = originX + sx * tile + pad;
			int y = originY + sy * tile + pad;
			g2.drawImage(image, x, y, null);
		}
		g2.setComposite(previous);
	}

		/**
		 * drawAnimatedCaptures method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawAnimatedCaptures(Graphics2D g2, int originX, int originY, int tile) {
		if (!moveAnimationActive || animatedCaptures.isEmpty()) {
			return;
		}
		float alpha = 1f - moveAnimationProgress();
		if (alpha <= 0f) {
			return;
		}
		Composite previous = g2.getComposite();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
		int pieceSize = Math.round(tile * GuiWindowBase.PIECE_SCALE);
		int pad = (tile - pieceSize) / 2;
		for (AnimatedCapture capture : animatedCaptures) {
			if (capture == null || capture.square() == Field.NO_SQUARE || Piece.isEmpty(capture.piece())) {
				continue;
			}
			BufferedImage image = scaledPiece(capture.piece(), tile);
			if (image == null) {
				continue;
			}
			int sx = screenFile(capture.square());
			int sy = screenRank(capture.square());
			int x = originX + sx * tile + pad;
			int y = originY + sy * tile + pad;
			g2.drawImage(image, x, y, null);
		}
		g2.setComposite(previous);
	}

		/**
		 * drawAnimatedPieces method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawAnimatedPieces(Graphics2D g2, int originX, int originY, int tile) {
		if (!moveAnimationActive || animatedPieces.isEmpty() || boardState == null) {
			return;
		}
		float t = moveAnimationProgress();
		int pieceSize = Math.round(tile * GuiWindowBase.PIECE_SCALE);
		int pad = (tile - pieceSize) / 2;
		for (AnimatedPiece animated : animatedPieces) {
			if (animated == null || animated.from() == Field.NO_SQUARE || animated.to() == Field.NO_SQUARE) {
				continue;
			}
			BufferedImage image = scaledPiece(animated.piece(), tile);
			if (image == null) {
				continue;
			}
			int fromFile = screenFile(animated.from());
			int fromRank = screenRank(animated.from());
			int toFile = screenFile(animated.to());
			int toRank = screenRank(animated.to());
			float file = fromFile + (toFile - fromFile) * t;
			float rank = fromRank + (toRank - fromRank) * t;
			int x = originX + Math.round(file * tile) + pad;
			int y = originY + Math.round(rank * tile) + pad;
			g2.drawImage(image, x, y, null);
		}
	}

		/**
		 * drawAblationOverlay method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawAblationOverlay(Graphics2D g2, int originX, int originY, int tile) {
		if (owner.ablationLabels == null) {
			return;
		}
		String[] labels = owner.ablationLabels;
		float fontSize = Math.max(9f, tile * 0.22f);
		Font font = owner.theme.monoFont().deriveFont(Font.BOLD, fontSize);
		g2.setFont(font);
		java.awt.FontMetrics fm = g2.getFontMetrics();
		int padX = Math.max(2, Math.round(tile * 0.08f));
		int padY = Math.max(1, Math.round(tile * 0.06f));
		Color baseBg = owner.theme.surfaceAlt();
		Color pos = owner.lightMode ? new Color(24, 140, 80) : new Color(110, 210, 150);
		Color neg = owner.lightMode ? new Color(200, 60, 60) : new Color(230, 110, 110);
		Color bg = new Color(baseBg.getRed(), baseBg.getGreen(), baseBg.getBlue(), 160);
		for (int square = 0; square < labels.length; square++) {
			String text = labels[square];
			if (text == null) {
				continue;
			}
			int sx = screenFile((byte) square);
			int sy = screenRank((byte) square);
			int x = originX + sx * tile;
			int y = originY + sy * tile;
			int textW = fm.stringWidth(text);
			int textH = fm.getAscent() + fm.getDescent();
			int tx = x + (tile - textW) / 2;
			int ty = y + tile - padY - fm.getDescent();
			int rectX = tx - padX;
			int rectY = ty - fm.getAscent() - padY;
			int rectW = textW + padX * 2;
			int rectH = textH + padY * 2;
			g2.setColor(bg);
			g2.fillRoundRect(rectX, rectY, rectW, rectH, 6, 6);
			g2.setColor(text.startsWith("-") ? neg : pos);
			g2.drawString(text, tx, ty);
		}
	}

		/**
		 * drawDragGhost method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawDragGhost(Graphics2D g2, int originX, int originY, int tile) {
		if (!dragging || Piece.isEmpty(dragPiece)) {
			return;
		}
		byte target = squareFromPoint(dragX, dragY);
		if (target == Field.NO_SQUARE || target == dragFrom) {
			return;
		}
		if (showLegal && !legalTargets[target]) {
			return;
		}
		BufferedImage image = scaledPiece(dragPiece, tile);
		if (image == null) {
			return;
		}
		int pieceSize = Math.round(tile * GuiWindowBase.PIECE_SCALE);
		int pad = (tile - pieceSize) / 2;
		int sx = screenFile(target);
		int sy = screenRank(target);
		int x = originX + sx * tile + pad;
		int y = originY + sy * tile + pad;
		Composite prev = g2.getComposite();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
		g2.drawImage(image, x, y, null);
		g2.setComposite(prev);
	}

		/**
		 * drawDragPiece method.
		 *
		 * @param g2 parameter.
		 * @param tile parameter.
		 */
		private void drawDragPiece(Graphics2D g2, int tile) {
		if (!dragging || Piece.isEmpty(dragPiece)) {
			return;
		}
		BufferedImage image = scaledPiece(dragPiece, tile);
		if (image == null) {
			return;
		}
		int pieceSize = Math.round(tile * GuiWindowBase.PIECE_SCALE);
		int x = dragX - pieceSize / 2;
		int y = dragY - pieceSize / 2;
		g2.drawImage(image, x, y, null);
	}

		/**
		 * scaledPiece method.
		 *
		 * @param piece parameter.
		 * @param tile parameter.
		 * @return return value.
		 */
		private BufferedImage scaledPiece(byte piece, int tile) {
		if (Piece.isEmpty(piece)) {
			return null;
		}
		int pieceSize = Math.round(tile * GuiWindowBase.PIECE_SCALE);
		if (pieceSize <= 0) {
			return null;
		}
		if (pieceSize != scaledPieceSize) {
			Arrays.fill(scaledPieces, null);
			scaledPieceSize = pieceSize;
		}
		int index = piece + PIECE_CACHE_OFFSET;
		if (index < 0 || index >= scaledPieces.length) {
			return owner.pieceImage(piece);
		}
		BufferedImage cached = scaledPieces[index];
		if (cached != null) {
			return cached;
		}
		BufferedImage base = owner.pieceImage(piece);
		if (base == null) {
			return null;
		}
		BufferedImage scaled = new BufferedImage(pieceSize, pieceSize, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = scaled.createGraphics();
		owner.applyRenderHints(g2);
		g2.drawImage(base, 0, 0, pieceSize, pieceSize, null);
		g2.dispose();
		scaledPieces[index] = scaled;
		return scaled;
	}

		/**
		 * drawBestMoveArrow method.
		 *
		 * @param g2 parameter.
		 * @param originX parameter.
		 * @param originY parameter.
		 * @param tile parameter.
		 */
		private void drawBestMoveArrow(Graphics2D g2, int originX, int originY, int tile) {
		if (owner.bestMoveToggle == null || !owner.bestMoveToggle.isSelected()) {
			return;
		}
		if (owner.engineBestMove == Move.NO_MOVE || !owner.position.isLegalMove(owner.engineBestMove)) {
			return;
		}
		byte from = Move.getFromIndex(owner.engineBestMove);
		byte to = Move.getToIndex(owner.engineBestMove);
		Color base = bestMoveColor();
		Color arrowColor = withAlpha(base, BEST_MOVE_ARROW_ALPHA);
		drawArrow(g2, originX, originY, tile, from, to, arrowColor);
	}
		/**
		 * setPreviewMove method.
		 *
		 * @param move parameter.
		 */
		void setPreviewMove(short move) {
		if (move == Move.NO_MOVE) {
			clearPreview();
			return;
		}
		setPreviewLine(new short[] { move });
	}

		/**
		 * setPreviewLine method.
		 *
		 * @param moves parameter.
		 */
		void setPreviewLine(short[] moves) {
		setPreviewLine(moves, Integer.MAX_VALUE);
	}

		/**
		 * setPreviewLine method.
		 *
		 * @param moves parameter.
		 * @param maxPlies parameter.
		 */
		void setPreviewLine(short[] moves, int maxPlies) {
		if (moves == null || moves.length == 0 || owner.position == null) {
			clearPreview();
			return;
		}
		int limit = Math.max(1, maxPlies);
		Position next = owner.position.copy();
		short firstMove = Move.NO_MOVE;
		int applied = 0;
		for (short move : moves) {
			if (applied >= limit) {
				break;
			}
			if (move == Move.NO_MOVE) {
				break;
			}
			if (!next.isLegalMove(move)) {
				break;
			}
			if (firstMove == Move.NO_MOVE) {
				firstMove = move;
			}
			next.play(move);
			applied++;
		}
		if (applied == 0 || firstMove == Move.NO_MOVE) {
			clearPreview();
			return;
		}
		int signature = previewSignature(moves, applied);
		if (previewActive && previewMove == firstMove && previewSignature == signature) {
			return;
		}
		previewBoard = next.getBoard();
		previewMove = firstMove;
		previewSignature = signature;
		previewFrom = Move.getFromIndex(firstMove);
		previewTo = Move.getToIndex(firstMove);
		previewActive = true;
		invalidateRenderCache();
		repaint();
	}

		/**
		 * setPreviewPosition method.
		 *
		 * @param previewPosition parameter.
		 * @param move parameter.
		 */
		void setPreviewPosition(Position previewPosition, short move) {
		if (previewPosition == null) {
			clearPreview();
			return;
		}
		byte[] nextBoard = previewPosition.getBoard();
		if (nextBoard == null || nextBoard.length != 64) {
			clearPreview();
			return;
		}
		int signature = 31 * Arrays.hashCode(nextBoard) + move;
		if (previewActive && previewSignature == signature) {
			return;
		}
		previewBoard = Arrays.copyOf(nextBoard, nextBoard.length);
		previewMove = move;
		previewSignature = signature;
		if (move != Move.NO_MOVE) {
			previewFrom = Move.getFromIndex(move);
			previewTo = Move.getToIndex(move);
		} else {
			previewFrom = Field.NO_SQUARE;
			previewTo = Field.NO_SQUARE;
		}
		previewActive = true;
		invalidateRenderCache();
		repaint();
	}

		/**
		 * previewSignature method.
		 *
		 * @param moves parameter.
		 * @param length parameter.
		 * @return return value.
		 */
		private static int previewSignature(short[] moves, int length) {
		int hash = 1;
		int limit = Math.min(length, moves.length);
		for (int i = 0; i < limit; i++) {
			hash = 31 * hash + moves[i];
		}
		return hash;
	}

		/**
		 * clearPreview method.
		 */
		void clearPreview() {
		previewActive = false;
		previewMove = Move.NO_MOVE;
		previewSignature = 0;
		previewFrom = Field.NO_SQUARE;
		previewTo = Field.NO_SQUARE;
		previewBoard = null;
		invalidateRenderCache();
		repaint();
	}

				/**
		 * Handles paint component.
		 * @param g g value
		 */
@Override
	/**
	 * paintComponent method.
	 *
	 * @param g parameter.
	 */
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		int size = Math.min(getWidth(), getHeight());
		if (size <= 0) {
			g2.dispose();
			return;
		}
		int coordPad = showCoords ? Math.min(Math.max(12, size / 24), size / 6) : 0;
		int tile = (size - coordPad * 2) / 8;
		if (tile <= 0) {
			g2.dispose();
			return;
		}
		boardSize = tile * 8;
		tileSize = tile;
		boardX = (getWidth() - boardSize) / 2;
		boardY = (getHeight() - boardSize) / 2;
		if (dragging) {
			boolean needsCache = cachedLayer == null
					|| cachedVersion != renderVersion
					|| cachedWidth != getWidth()
					|| cachedHeight != getHeight()
					|| cachedBoardX != boardX
					|| cachedBoardY != boardY
					|| cachedBoardSize != boardSize
					|| cachedCoordPad != coordPad
					|| cachedShowCoords != showCoords;
			if (needsCache) {
				cachedLayer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D cg = cachedLayer.createGraphics();
				owner.applyRenderHints(cg);
				renderBoard(cg, boardX, boardY, boardSize, false);
				if (showCoords) {
					drawCoordinates(cg, boardX, boardY, tile, coordPad);
				}
				cg.dispose();
				cachedVersion = renderVersion;
				cachedWidth = getWidth();
				cachedHeight = getHeight();
				cachedBoardX = boardX;
				cachedBoardY = boardY;
				cachedBoardSize = boardSize;
				cachedCoordPad = coordPad;
				cachedShowCoords = showCoords;
			}
			if (cachedLayer != null) {
				g2.drawImage(cachedLayer, 0, 0, null);
			}
			drawDragGhost(g2, boardX, boardY, tile);
			drawDragPiece(g2, tile);
			owner.applyRenderHints(g2);
		} else {
			owner.applyRenderHints(g2);
			renderBoard(g2, boardX, boardY, boardSize);
			if (showCoords) {
				drawCoordinates(g2, boardX, boardY, tile, coordPad);
			}
		}
		g2.dispose();
	}

}
