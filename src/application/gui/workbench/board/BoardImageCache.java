package application.gui.workbench.board;

import application.gui.workbench.ui.RenderAcceleration;
import chess.core.Piece;
import chess.images.assets.PieceSet;
import chess.images.assets.Shapes;
import java.awt.Color;
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
     * Active piece artwork set used to render cached piece bitmaps.
     */
    private PieceSet pieceSet = PieceSet.SLATE;

    /**
     * Cached board texture for the current board size.
     */
    private BufferedImage boardTextureCache;

    /**
     * Board size represented by {@link #boardTextureCache}.
     */
    private int boardTextureCacheSize = -1;

    /**
     * Light-square color represented by the cached board texture.
     */
    private Color boardTextureLight;

    /**
     * Dark-square color represented by the cached board texture.
     */
    private Color boardTextureDark;

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
        return boardTexture(size, BoardStyle.squareColor(0, 0), BoardStyle.squareColor(0, 1));
    }

    /**
     * Returns the cached board texture for a board size and color pair.
     *
     * @param size board image size in pixels
     * @param light light-square color
     * @param dark dark-square color
     * @return cached board texture
     */
    BufferedImage boardTexture(int size, Color light, Color dark) {
        Color safeLight = opaque(light == null ? BoardStyle.squareColor(0, 0) : light);
        Color safeDark = opaque(dark == null ? BoardStyle.squareColor(0, 1) : dark);
        if (boardTextureCache == null || boardTextureCacheSize != size
                || !sameRgb(boardTextureLight, safeLight) || !sameRgb(boardTextureDark, safeDark)) {
            boardTextureCache = renderBoardTexture(size, safeLight, safeDark);
            boardTextureCacheSize = size;
            boardTextureLight = safeLight;
            boardTextureDark = safeDark;
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
            cached = renderPieceImage(pieceSet, piece, cell);
            pieceImageCache[index] = cached;
        }
        return cached;
    }

    /**
     * Returns the active piece artwork set.
     *
     * @return active piece set
     */
    PieceSet pieceSet() {
        return pieceSet;
    }

    /**
     * Switches the piece artwork set, discarding cached bitmaps when it changes.
     *
     * @param set piece artwork set
     * @return true when the set changed and caches were cleared
     */
    boolean pieceSet(PieceSet set) {
        PieceSet next = set == null ? PieceSet.SLATE : set;
        if (next == pieceSet) {
            return false;
        }
        pieceSet = next;
        Arrays.fill(pieceImageCache, null);
        dragImageCache = null;
        dragImageCachedPiece = Piece.EMPTY;
        dragImageCachedCell = -1;
        return true;
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
            dragImageCache = renderPieceImage(pieceSet, piece, cell);
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
    private static BufferedImage renderBoardTexture(int size, Color light, Color dark) {
        BufferedImage image = RenderAcceleration.opaqueImage(size, size);
        Graphics2D graphics = image.createGraphics();
        try {
            int cell = size / 8;
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    graphics.setColor(BoardStyle.squareColor(row, col, light, dark));
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
     * @param set piece artwork set
     * @param piece signed piece code
     * @param cell target square size in pixels
     * @return rendered piece bitmap
     */
    private static BufferedImage renderPieceImage(PieceSet set, byte piece, int cell) {
        BufferedImage image = RenderAcceleration.translucentImage(cell, cell);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Shapes.drawPiece(set, piece, graphics, 0, 0, cell, cell);
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

    /**
     * Returns an opaque copy of a color.
     *
     * @param color source color
     * @return opaque color
     */
    private static Color opaque(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Returns whether two colors share RGB channels.
     *
     * @param first first color
     * @param second second color
     * @return true when RGB channels match
     */
    private static boolean sameRgb(Color first, Color second) {
        return first != null && second != null
                && first.getRed() == second.getRed()
                && first.getGreen() == second.getGreen()
                && first.getBlue() == second.getBlue();
    }
}
