package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Java2D rendering pipeline for {@link BoardPanel}.
 */
final class BoardPanelPainter {

    /**
     * Shared one-pixel stroke.
     */
    private static final BasicStroke STROKE_1 = new BasicStroke(1f);

    /**
     * Composite used while painting dragged pieces.
     */
    private static final AlphaComposite DRAG_ALPHA = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f);

    /**
     * Radial gradient stops for the checked-king highlight.
     */
    private static final float[] CHECK_GRADIENT_FRACTIONS = { 0.0f, 0.24f, 0.42f, 0.74f, 1.0f };

    /**
     * Board panel state source.
     */
    private final BoardPanel boardPanel;

    /**
     * Creates a painter for one board panel.
     *
     * @param boardPanel board panel state source
     */
    BoardPanelPainter(BoardPanel boardPanel) {
        this.boardPanel = boardPanel;
    }

    /**
     * Draws the paint.
     *
     * @param g graphics context
     * @param board board state
     */
    void paint(Graphics2D g, Rectangle board) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        drawShell(g, board);
        drawBoardContent(g, board);
        drawDraggedPiece(g, board);
    }

    /**
     * Returns the checked king square.
     *
     * @return checked king square
     */
    byte checkedKingSquare() {
        if (boardPanel.position == null || !boardPanel.position.inCheck()) {
            return Field.NO_SQUARE;
        }
        byte target = boardPanel.position.isWhiteToMove() ? Piece.WHITE_KING : Piece.BLACK_KING;
        byte[] board = boardPanel.position.getBoard();
        for (byte square = 0; square < 64; square++) {
            if (board[square] == target) {
                return square;
            }
        }
        return Field.NO_SQUARE;
    }

    /**
     * Primes the piece-image cache used during drag painting.
     *
     * @param piece encoded piece
     */
    void warmDragPieceImage(byte piece) {
        Rectangle board = boardPanel.boardBounds();
        int cell = board.width / 8;
        int scaledCell = (int) Math.round(cell * BoardPanel.DRAG_SCALE);
        if (scaledCell > 0) {
            boardPanel.imageCache.dragPieceImage(piece, scaledCell);
        }
    }

    /**
     * Draws the draw board content.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawBoardContent(Graphics2D g, Rectangle board) {
        Graphics2D copy = (Graphics2D) g.create();
        try {
            copy.clip(board);
            drawBoardTexture(copy, board);
            drawCoordinates(copy, board);
            if (boardPanel.setupEditor.active()) {
                drawSetupEditHighlight(copy, board);
            } else {
                drawSquareHighlights(copy, board);
                drawMoveHighlights(copy, board);
                drawSelection(copy, board);
                drawCheckHighlight(copy, board);
                drawPremove(copy, board);
                boardPanel.markupPainter.drawBackgroundMarkups(copy, board,
                        boardPanel.whiteDown, boardPanel.markupInput);
            }
            drawPieces(copy, board);
            if (!boardPanel.setupEditor.active()) {
                drawAnimatedCapture(copy, board);
                drawAnimatedMove(copy, board);
                drawSnapAnimation(copy, board);
                drawSuggestedMove(copy, board);
                if (boardPanel.showSpecialMoveHints) {
                    boardPanel.markupPainter.drawSpecialMoveHints(copy, board,
                            boardPanel.whiteDown, boardPanel.position);
                }
                boardPanel.markupPainter.drawForegroundMarkups(copy, board,
                        boardPanel.whiteDown, boardPanel.markupInput);
            }
            if (!boardPanel.setupEditor.active()) {
                drawWrongMoveMarker(copy, board);
            }
            drawFlipOverlay(copy, board);
            if (!boardPanel.setupEditor.active() && boardPanel.promotionOverlay != null) {
                boardPanel.promotionOverlay.paint(copy, board, boardPanel.whiteDown, this::drawPieceAt);
            }
        } finally {
            copy.dispose();
        }
    }

    /**
     * Draws the draw flip overlay.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawFlipOverlay(Graphics2D g, Rectangle board) {
        double flipProgress = boardPanel.animationState.flipAnimationProgress();
        if (Double.isNaN(flipProgress)) {
            return;
        }
        float alpha = (float) (Math.sin(Math.PI * flipProgress) * 0.55);
        if (alpha <= 0f) {
            return;
        }
        java.awt.Composite saved = g.getComposite();
        Color savedColor = g.getColor();
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
            g.setColor(Theme.BG);
            g.fillRect(board.x, board.y, board.width, board.height);
        } finally {
            g.setComposite(saved);
            g.setColor(savedColor);
        }
    }

    /**
     * Draws the draw square highlights.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawSquareHighlights(Graphics2D g, Rectangle board) {
        if (boardPanel.squareHighlights.isEmpty()) {
            return;
        }
        Rectangle clip = g.getClipBounds();
        for (Map.Entry<Byte, Color> entry : boardPanel.squareHighlights.entrySet()) {
            Rectangle bounds = boardPanel.squareBounds(board, entry.getKey());
            if (BoardPanel.intersectsClip(clip, bounds)) {
                BoardStyle.drawFilledSquareHighlight(g, bounds, entry.getValue());
            }
        }
    }

    /**
     * Draws the draw snap animation.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawSnapAnimation(Graphics2D g, Rectangle board) {
        SnapAnimation snap = boardPanel.animationState.snapAnimation();
        if (snap == null) {
            return;
        }
        double progress = snap.progress();
        double eased = BoardPanel.easeOutCubic(progress);
        int cell = board.width / 8;
        int x = (int) Math.round(snap.startX + (snap.endX - snap.startX) * eased);
        int y = (int) Math.round(snap.startY + (snap.endY - snap.startY) * eased);
        drawPieceAt(g, cell, x, y, snap.piece);
    }

    /**
     * Draws the draw shell.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawShell(Graphics2D g, Rectangle board) {
        g.setColor(Theme.BOARD_EDGE);
        g.fillRect(board.x - BoardStyle.BORDER_WIDTH, board.y - BoardStyle.BORDER_WIDTH,
                board.width + BoardStyle.BORDER_WIDTH * 2, board.height + BoardStyle.BORDER_WIDTH * 2);
    }

    /**
     * Draws the draw board texture.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawBoardTexture(Graphics2D g, Rectangle board) {
        BufferedImage texture = boardPanel.imageCache.boardTexture(board.width,
                boardPanel.boardLightColor(), boardPanel.boardDarkColor());
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            g.drawImage(texture, board.x, board.y, null);
            return;
        }
        Rectangle dirty = board.intersection(clip);
        if (dirty.isEmpty()) {
            return;
        }
        int sx = dirty.x - board.x;
        int sy = dirty.y - board.y;
        g.drawImage(texture,
                dirty.x, dirty.y, dirty.x + dirty.width, dirty.y + dirty.height,
                sx, sy, sx + dirty.width, sy + dirty.height,
                null);
    }

    /**
     * Draws the draw coordinates.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawCoordinates(Graphics2D g, Rectangle board) {
        if (!boardPanel.showNotation) {
            return;
        }
        BoardStyle.drawInsideCoordinates(g, board, boardPanel.whiteDown, 14,
                boardPanel.boardLightColor(), boardPanel.boardDarkColor());
    }

    /**
     * Draws the draw move highlights.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawMoveHighlights(Graphics2D g, Rectangle board) {
        if (!boardPanel.showLastMoveHighlight || boardPanel.lastMove == Move.NO_MOVE) {
            return;
        }
        Rectangle clip = g.getClipBounds();
        Rectangle from = boardPanel.squareBounds(board, Move.getFromIndex(boardPanel.lastMove));
        Rectangle to = boardPanel.squareBounds(board, Move.getToIndex(boardPanel.lastMove));
        if (BoardPanel.intersectsClip(clip, from)) {
            BoardStyle.drawFilledSquareHighlight(g, from, Theme.LAST_MOVE_EDGE);
        }
        if (BoardPanel.intersectsClip(clip, to)) {
            BoardStyle.drawFilledSquareHighlight(g, to, Theme.LAST_MOVE_EDGE);
        }
    }

    /**
     * Draws the draw selection.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawSelection(Graphics2D g, Rectangle board) {
        byte selectedSquare = boardPanel.pieceInput.selectedSquare();
        if (boardPanel.position == null || selectedSquare == Field.NO_SQUARE) {
            return;
        }
        Rectangle selected = boardPanel.squareBounds(board, selectedSquare);
        Rectangle clip = g.getClipBounds();
        if (BoardPanel.intersectsClip(clip, selected)) {
            BoardStyle.drawFilledSquareHighlight(g, selected, Theme.SELECTED_EDGE);
        }
        if (boardPanel.showLegalMovePreview) {
            if (boardPanel.premoveSelection) {
                drawPremoveTargets(g, board);
            } else {
                drawLegalTargets(g, board);
            }
            drawDropTarget(g, board);
        }
    }

    /**
     * Draws the draw check highlight.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawCheckHighlight(Graphics2D g, Rectangle board) {
        byte square = checkedKingSquare();
        if (square == Field.NO_SQUARE) {
            return;
        }
        Rectangle bounds = boardPanel.squareBounds(board, square);
        java.awt.Paint savedPaint = g.getPaint();
        Stroke savedStroke = g.getStroke();
        Color savedColor = g.getColor();
        try {
            g.setColor(Theme.CHECK_FILL);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            float radius = bounds.width * 0.74f;
            Point2D center = new Point2D.Double(bounds.getCenterX(), bounds.y + bounds.height * 0.52);
            Color core = Theme.CHECK_CORE;
            Color glow = Theme.CHECK_GLOW;
            Color fade = Theme.withAlpha(glow, 0);
            g.setPaint(new RadialGradientPaint(center, radius,
                    CHECK_GRADIENT_FRACTIONS,
                    new Color[] { core, core, glow, fade, fade }));
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            g.setPaint(savedPaint);
            g.setColor(Theme.CHECK_EDGE);
            g.setStroke(STROKE_1);
            g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        } finally {
            g.setPaint(savedPaint);
            g.setStroke(savedStroke);
            g.setColor(savedColor);
        }
    }

    /**
     * Draws the draw legal targets.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawLegalTargets(Graphics2D g, Rectangle board) {
        Rectangle clip = g.getClipBounds();
        byte selectedSquare = boardPanel.pieceInput.selectedSquare();
        for (byte target : boardPanel.pieceInput.selectedLegalTargets()) {
            if (target != selectedSquare) {
                Rectangle bounds = boardPanel.squareBounds(board, target);
                if (BoardPanel.intersectsClip(clip, bounds)) {
                    BoardStyle.drawLegalTarget(g, bounds, boardPanel.isCaptureTarget(target));
                }
            }
        }
    }

    /**
     * Draws the draw premove targets.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawPremoveTargets(Graphics2D g, Rectangle board) {
        Rectangle clip = g.getClipBounds();
        byte selectedSquare = boardPanel.pieceInput.selectedSquare();
        for (byte target : boardPanel.pieceInput.selectedLegalTargets()) {
            if (target != selectedSquare) {
                Rectangle bounds = boardPanel.squareBounds(board, target);
                if (BoardPanel.intersectsClip(clip, bounds)) {
                    BoardStyle.drawPremoveTarget(g, bounds, boardPanel.isOccupied(target));
                }
            }
        }
    }

    /**
     * Draws the draw setup edit highlight.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawSetupEditHighlight(Graphics2D g, Rectangle board) {
        if (boardPanel.setupEditor.lastSquare() == Field.NO_SQUARE) {
            return;
        }
        Rectangle bounds = boardPanel.squareBounds(board, boardPanel.setupEditor.lastSquare());
        if (!BoardPanel.intersectsClip(g.getClipBounds(), bounds)) {
            return;
        }
        g.setColor(Theme.withAlpha(Theme.ACCENT, 74));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        Stroke previousStroke = g.getStroke();
        try {
            g.setColor(Theme.withAlpha(Theme.ACCENT, 190));
            g.setStroke(STROKE_1);
            g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
        } finally {
            g.setStroke(previousStroke);
        }
    }

    /**
     * Draws the draw wrong move marker.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawWrongMoveMarker(Graphics2D g, Rectangle board) {
        byte wrongMoveMarkerSquare = boardPanel.animationState.wrongMoveMarkerSquare();
        if (wrongMoveMarkerSquare == Field.NO_SQUARE) {
            return;
        }
        Rectangle square = boardPanel.squareBounds(board, wrongMoveMarkerSquare);
        if (!BoardPanel.intersectsClip(g.getClipBounds(), square)) {
            return;
        }
        double progress = Math.min(0.999, boardPanel.wrongMoveMarkerProgress());
        double scale = progress < 0.6
                ? 0.25 + (1.08 - 0.25) * (progress / 0.6)
                : 1.08 + (1.0 - 1.08) * ((progress - 0.6) / 0.4);
        float alpha = (float) Math.max(0.0, Math.min(1.0, progress / 0.6));
        int baseSize = Math.max(14, Math.round(square.width * 0.46f));
        int size = Math.max(8, (int) Math.round(baseSize * scale));
        int half = Math.max(1, size / 2);
        int padding = Math.max(2, Math.round(square.width * 0.04f));
        int centerX = clamped(
                square.x + square.width - Math.round(square.width * 0.12f),
                square.x + half + padding,
                square.x + square.width - half - padding);
        int centerY = clamped(
                square.y + Math.round(square.height * 0.12f),
                square.y + half + padding,
                square.y + square.height - half - padding);
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        Graphics2D marker = (Graphics2D) g.create();
        try {
            marker.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            marker.setColor(Theme.STATUS_ERROR_TEXT);
            marker.fillOval(x, y, size, size);
            marker.setColor(Theme.withAlpha(Color.WHITE, 230));
            float stroke = Math.max(2f, size * 0.12f);
            marker.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int inset = Math.max(4, Math.round(size * 0.28f));
            marker.drawLine(x + inset, y + inset, x + size - inset, y + size - inset);
            marker.drawLine(x + size - inset, y + inset, x + inset, y + size - inset);
        } finally {
            marker.dispose();
        }
    }

    /**
     * Draws the draw pieces.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawPieces(Graphics2D g, Rectangle board) {
        byte[] pieces = boardPanel.setupEditor.active() ? boardPanel.setupEditor.board()
                : boardPanel.position == null ? null : boardPanel.position.getBoard();
        if (pieces == null) {
            return;
        }
        int cell = board.width / 8;
        Rectangle clip = g.getClipBounds();
        for (byte square = 0; square < 64; square++) {
            byte piece = pieces[square];
            if (piece != Piece.EMPTY) {
                if (boardPanel.pieceInput.isDragging() && square == boardPanel.pieceInput.dragSquare()) {
                    continue;
                }
                if (boardPanel.animationState.moveAnimationActive()
                        && square == boardPanel.animationState.animatedMoveTo()) {
                    continue;
                }
                if (boardPanel.animationState.moveAnimationActive()
                        && square == boardPanel.animationState.animatedSecondaryMoveTo()) {
                    continue;
                }
                if (square == boardPanel.animationState.snapHiddenSquare()) {
                    continue;
                }
                Rectangle bounds = boardPanel.squareBounds(board, square);
                if (BoardPanel.intersectsClip(clip, bounds)) {
                    drawPieceAt(g, cell, bounds.x, bounds.y, piece);
                }
            }
        }
    }

    /**
     * Draws the draw piece at.
     *
     * @param g graphics context
     * @param cell board cell
     * @param x x coordinate in pixels
     * @param y y coordinate in pixels
     * @param piece encoded piece
     */
    private void drawPieceAt(Graphics2D g, int cell, int x, int y, byte piece) {
        BufferedImage image = boardPanel.imageCache.pieceImage(piece, cell);
        if (image != null) {
            g.drawImage(image, x, y, null);
        }
    }

    /**
     * Draws the draw suggested move.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawSuggestedMove(Graphics2D g, Rectangle board) {
        if (!boardPanel.showSuggestedMoveArrow
                || boardPanel.suggestedMove == Move.NO_MOVE
                || !boardPanel.legalSuggestedMove(boardPanel.suggestedMove)) {
            return;
        }
        double gap = Math.max(1, board.width / 8) * BoardStyle.ARROW_PIECE_GAP_FRACTION;
        boardPanel.arrowPainter.drawSuggested(g,
                boardPanel.center(board, Move.getFromIndex(boardPanel.suggestedMove)),
                boardPanel.center(board, Move.getToIndex(boardPanel.suggestedMove)), gap);
    }

    /**
     * Draws the draw premove.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawPremove(Graphics2D g, Rectangle board) {
        if (boardPanel.pendingPremove == Move.NO_MOVE || !BoardPanel.legalPremoveShape(boardPanel.pendingPremove)) {
            return;
        }
        Rectangle clip = g.getClipBounds();
        Rectangle from = boardPanel.squareBounds(board, Move.getFromIndex(boardPanel.pendingPremove));
        Rectangle to = boardPanel.squareBounds(board, Move.getToIndex(boardPanel.pendingPremove));
        if (BoardPanel.intersectsClip(clip, from)) {
            BoardStyle.drawCurrentPremoveSquare(g, from);
        }
        if (BoardPanel.intersectsClip(clip, to)) {
            BoardStyle.drawCurrentPremoveSquare(g, to);
        }
    }

    /**
     * Draws the draw animated move.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawAnimatedMove(Graphics2D g, Rectangle board) {
        if (!boardPanel.animationState.moveAnimationActive()) {
            return;
        }
        drawAnimatedPiece(g, board,
                boardPanel.animationState.animatedMovePiece(),
                boardPanel.animationState.animatedMoveFrom(),
                boardPanel.animationState.animatedMoveTo());
        drawAnimatedPiece(g, board,
                boardPanel.animationState.animatedSecondaryMovePiece(),
                boardPanel.animationState.animatedSecondaryMoveFrom(),
                boardPanel.animationState.animatedSecondaryMoveTo());
    }

    /**
     * Draws the draw animated piece.
     *
     * @param g graphics context
     * @param board board state
     * @param piece encoded piece
     * @param from source square or start value
     * @param to target square or end value
     */
    private void drawAnimatedPiece(Graphics2D g, Rectangle board, byte piece, byte from, byte to) {
        if (piece == Piece.EMPTY || from == Field.NO_SQUARE || to == Field.NO_SQUARE) {
            return;
        }
        int cell = board.width / 8;
        Rectangle fromBounds = boardPanel.squareBounds(board, from);
        Rectangle toBounds = boardPanel.squareBounds(board, to);
        double progress = BoardPanel.easeOutCubic(boardPanel.moveAnimationProgress());
        int x = (int) Math.round(fromBounds.x + (toBounds.x - fromBounds.x) * progress);
        int y = (int) Math.round(fromBounds.y + (toBounds.y - fromBounds.y) * progress);
        drawPieceAt(g, cell, x, y, piece);
    }

    /**
     * Draws the draw animated capture.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawAnimatedCapture(Graphics2D g, Rectangle board) {
        byte animatedCapturePiece = boardPanel.animationState.animatedCapturePiece();
        byte animatedCaptureSquare = boardPanel.animationState.animatedCaptureSquare();
        if (!boardPanel.animationState.moveAnimationActive()
                || animatedCapturePiece == Piece.EMPTY
                || animatedCaptureSquare == Field.NO_SQUARE) {
            return;
        }
        int cell = board.width / 8;
        Rectangle bounds = boardPanel.squareBounds(board, animatedCaptureSquare);
        float alpha = (float) Math.max(0.0, 1.0 - BoardPanel.easeOutCubic(boardPanel.moveAnimationProgress()));
        java.awt.Composite savedComposite = g.getComposite();
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            drawPieceAt(g, cell, bounds.x, bounds.y, animatedCapturePiece);
        } finally {
            g.setComposite(savedComposite);
        }
    }

    /**
     * Draws the draw drop target.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawDropTarget(Graphics2D g, Rectangle board) {
        if (!boardPanel.pieceInput.isDragging()) {
            return;
        }
        byte targetSquare = boardPanel.pieceInput.dragTargetSquare();
        if (targetSquare != Field.NO_SQUARE) {
            Rectangle bounds = boardPanel.squareBounds(board, targetSquare);
            if (BoardPanel.intersectsClip(g.getClipBounds(), bounds)) {
                if (boardPanel.premoveSelection) {
                    BoardStyle.drawPremoveTarget(g, bounds, boardPanel.isOccupied(targetSquare));
                } else {
                    BoardStyle.drawLegalTarget(g, bounds, boardPanel.isDragCaptureTarget(targetSquare));
                }
            }
        }
    }

    /**
     * Draws the draw dragged piece.
     *
     * @param g graphics context
     * @param board board state
     */
    private void drawDraggedPiece(Graphics2D g, Rectangle board) {
        byte draggedPiece = boardPanel.pieceInput.draggedPiece();
        if (!boardPanel.pieceInput.isDragging() || draggedPiece == Piece.EMPTY) {
            return;
        }
        int cell = board.width / 8;
        int scaledCell = (int) Math.round(cell * BoardPanel.DRAG_SCALE);
        int offset = (scaledCell - cell) / 2;
        int dragX = boardPanel.pieceInput.dragX();
        int dragY = boardPanel.pieceInput.dragY();
        int pieceX = dragX - cell / 2 - offset;
        int pieceY = dragY - cell / 2 - offset;
        Rectangle bounds = new Rectangle(pieceX, pieceY, scaledCell, scaledCell);
        if (!BoardPanel.intersectsClip(g.getClipBounds(), bounds)) {
            return;
        }
        java.awt.Composite savedComposite = g.getComposite();
        Color savedColor = g.getColor();
        try {
            int shadowWidth = Math.round(scaledCell * 0.66f);
            int shadowHeight = Math.max(6, Math.round(scaledCell * 0.22f));
            int shadowX = dragX - shadowWidth / 2;
            int shadowY = pieceY + scaledCell - shadowHeight - Math.round(scaledCell * 0.04f);
            for (int ring = 3; ring >= 1; ring--) {
                g.setColor(new Color(0, 0, 0, 16));
                g.fillOval(shadowX - ring * 2, shadowY - ring,
                        shadowWidth + ring * 4, shadowHeight + ring * 2);
            }
            g.setComposite(DRAG_ALPHA);
            BufferedImage scaled = boardPanel.imageCache.dragPieceImage(draggedPiece, scaledCell);
            g.drawImage(scaled, pieceX, pieceY, null);
        } finally {
            g.setComposite(savedComposite);
            g.setColor(savedColor);
        }
    }

    /**
     * Clamps a floating-point value into a closed range.
     *
     * @param value candidate value
     * @param min lower bound
     * @param max upper bound
     * @return clamped value
     */
    private static int clamped(int value, int min, int max) {
        if (min > max) {
            return (min + max) / 2;
        }
        return Math.max(min, Math.min(max, value));
    }
}
