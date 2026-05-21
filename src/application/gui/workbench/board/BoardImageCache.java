package application.gui.workbench.board;

import chess.core.Piece;
import chess.images.assets.Shapes;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Small bitmap cache for board textures and rendered chess pieces.
 */
final class BoardImageCache {
    /**
     * Offset that maps signed piece codes into the image-cache array.
     */
    private static final int PIECE_CACHE_OFFSET = 6;

    /**
     * Number of slots needed for all signed piece codes and the empty square.
     */
    private static final int PIECE_CACHE_SIZE = PIECE_CACHE_OFFSET * 2 + 1;

    /**
     * Scaled piece image cache for the current board cell size.
     */
    private final BufferedImage[] pieceImageCache = new BufferedImage[PIECE_CACHE_SIZE];

    /**
     * Cell size represented by {@link #pieceImageCache}.
     */
    private int pieceImageCacheCell = -1;

    /**
     * Cached board texture for the current board size.
     */
    private BufferedImage boardTextureCache;

    /**
     * Board size represented by {@link #boardTextureCache}.
     */
    private int boardTextureCacheSize = -1;

    /**
     * Cached scaled bitmap for the currently dragged piece.
     */
    private byte dragImageCachedPiece = Piece.EMPTY;

    /**
     * Cell size represented by {@link #dragImageCache}.
     */
    private int dragImageCachedCell = -1;

    /**
     * Cached scaled drag bitmap.
     */
    private BufferedImage dragImageCache;

    /**
     * Returns the cached board texture for a board size.
     *
     * @param size board image size in pixels
     * @return cached board texture
     */
    BufferedImage boardTexture(int size) {
        if (boardTextureCache == null || boardTextureCacheSize != size) {
            boardTextureCache = renderBoardTexture(size);
            boardTextureCacheSize = size;
        }
        return boardTextureCache;
    }

    /**
     * Returns a scaled cached image for one chess piece.
     *
     * @param piece signed piece code
     * @param cell target square size in pixels
     * @return scaled piece image, or null when the piece code is empty or invalid
     */
    BufferedImage pieceImage(byte piece, int cell) {
        int index = pieceCacheIndex(piece);
        if (cell <= 0 || index < 0) {
            return null;
        }
        if (pieceImageCacheCell != cell) {
            clearPieceImageCache(cell);
        }
        BufferedImage cached = pieceImageCache[index];
        if (cached == null) {
            cached = renderPieceImage(piece, cell);
            pieceImageCache[index] = cached;
        }
        return cached;
    }

    /**
     * Returns a scaled drag-piece bitmap from a tiny dedicated cache.
     *
     * @param piece signed piece code
     * @param cell target square size in pixels
     * @return scaled drag image
     */
    BufferedImage dragPieceImage(byte piece, int cell) {
        if (dragImageCache == null || dragImageCachedPiece != piece || dragImageCachedCell != cell) {
            dragImageCache = renderPieceImage(piece, cell);
            dragImageCachedPiece = piece;
            dragImageCachedCell = cell;
        }
        return dragImageCache;
    }

    /**
     * Clears scaled piece images for a new board cell size.
     *
     * @param cell new board cell size in pixels
     */
    private void clearPieceImageCache(int cell) {
        Arrays.fill(pieceImageCache, null);
        pieceImageCacheCell = cell;
    }

    /**
     * Renders board squares into a texture image.
     *
     * @param size board image size in pixels
     * @return rendered board texture
     */
    private static BufferedImage renderBoardTexture(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            int cell = size / 8;
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    graphics.setColor(BoardGeometry.squareColor(row, col));
                    graphics.fillRect(col * cell, row * cell, cell, cell);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Renders one embedded SVG piece into an exact-size bitmap.
     *
     * @param piece signed piece code
     * @param cell target square size in pixels
     * @return rendered piece bitmap
     */
    private static BufferedImage renderPieceImage(byte piece, int cell) {
        BufferedImage image = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Shapes.drawPiece(piece, graphics, 0, 0, cell, cell);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Maps a signed piece code to an image-cache index.
     *
     * @param piece signed piece code
     * @return image-cache index, or -1 for empty and invalid codes
     */
    private static int pieceCacheIndex(byte piece) {
        if (piece == Piece.EMPTY || piece < -PIECE_CACHE_OFFSET || piece > PIECE_CACHE_OFFSET) {
            return -1;
        }
        return piece + PIECE_CACHE_OFFSET;
    }
}
