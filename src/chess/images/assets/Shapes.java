package chess.images.assets;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import chess.core.Piece;
import chess.images.assets.shape.SvgShapes;
import utility.Svg;
import utility.Svg.DocumentModel;

/**
 * Renders embedded chess board and piece SVGs into images.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Shapes {

    /**
     * Default rendered piece size in pixels.
     */
    private static final int PIECE_SIZE = 200;

    /**
     * Default rendered board size in pixels.
     */
    private static final int BOARD_SIZE = 1600;

    /**
     * Hidden constructor for the utility holder.
     */
    private Shapes() {
        // utility holder
    }

    /**
     * Parsed chessboard SVG.
     */
    private static final DocumentModel BOARD_DOCUMENT = Svg.parse(SvgShapes.board());

    /**
     * Parsed white king SVG.
     */
    private static final DocumentModel WHITE_KING_DOCUMENT = Svg.parse(SvgShapes.whiteKing());

    /**
     * Parsed white queen SVG.
     */
    private static final DocumentModel WHITE_QUEEN_DOCUMENT = Svg.parse(SvgShapes.whiteQueen());

    /**
     * Parsed white rook SVG.
     */
    private static final DocumentModel WHITE_ROOK_DOCUMENT = Svg.parse(SvgShapes.whiteRook());

    /**
     * Parsed white bishop SVG.
     */
    private static final DocumentModel WHITE_BISHOP_DOCUMENT = Svg.parse(SvgShapes.whiteBishop());

    /**
     * Parsed white knight SVG.
     */
    private static final DocumentModel WHITE_KNIGHT_DOCUMENT = Svg.parse(SvgShapes.whiteKnight());

    /**
     * Parsed white pawn SVG.
     */
    private static final DocumentModel WHITE_PAWN_DOCUMENT = Svg.parse(SvgShapes.whitePawn());

    /**
     * Parsed black king SVG.
     */
    private static final DocumentModel BLACK_KING_DOCUMENT = Svg.parse(SvgShapes.blackKing());

    /**
     * Parsed black queen SVG.
     */
    private static final DocumentModel BLACK_QUEEN_DOCUMENT = Svg.parse(SvgShapes.blackQueen());

    /**
     * Parsed black rook SVG.
     */
    private static final DocumentModel BLACK_ROOK_DOCUMENT = Svg.parse(SvgShapes.blackRook());

    /**
     * Parsed black bishop SVG.
     */
    private static final DocumentModel BLACK_BISHOP_DOCUMENT = Svg.parse(SvgShapes.blackBishop());

    /**
     * Parsed black knight SVG.
     */
    private static final DocumentModel BLACK_KNIGHT_DOCUMENT = Svg.parse(SvgShapes.blackKnight());

    /**
     * Parsed black pawn SVG.
     */
    private static final DocumentModel BLACK_PAWN_DOCUMENT = Svg.parse(SvgShapes.blackPawn());

    /**
     * Rendered chessboard image.
     */
    public static final BufferedImage Board = renderSvg(BOARD_DOCUMENT, BOARD_SIZE, BOARD_SIZE);

    /**
     * Rendered white king image.
     */
    public static final BufferedImage WhiteKing = renderSvg(WHITE_KING_DOCUMENT);

    /**
     * Rendered white queen image.
     */
    public static final BufferedImage WhiteQueen = renderSvg(WHITE_QUEEN_DOCUMENT);

    /**
     * Rendered white rook image.
     */
    public static final BufferedImage WhiteRook = renderSvg(WHITE_ROOK_DOCUMENT);

    /**
     * Rendered white bishop image.
     */
    public static final BufferedImage WhiteBishop = renderSvg(WHITE_BISHOP_DOCUMENT);

    /**
     * Rendered white knight image.
     */
    public static final BufferedImage WhiteKnight = renderSvg(WHITE_KNIGHT_DOCUMENT);

    /**
     * Rendered white pawn image.
     */
    public static final BufferedImage WhitePawn = renderSvg(WHITE_PAWN_DOCUMENT);

    /**
     * Rendered black king image.
     */
    public static final BufferedImage BlackKing = renderSvg(BLACK_KING_DOCUMENT);

    /**
     * Rendered black queen image.
     */
    public static final BufferedImage BlackQueen = renderSvg(BLACK_QUEEN_DOCUMENT);

    /**
     * Rendered black rook image.
     */
    public static final BufferedImage BlackRook = renderSvg(BLACK_ROOK_DOCUMENT);

    /**
     * Rendered black bishop image.
     */
    public static final BufferedImage BlackBishop = renderSvg(BLACK_BISHOP_DOCUMENT);

    /**
     * Rendered black knight image.
     */
    public static final BufferedImage BlackKnight = renderSvg(BLACK_KNIGHT_DOCUMENT);

    /**
     * Rendered black pawn image.
     */
    public static final BufferedImage BlackPawn = renderSvg(BLACK_PAWN_DOCUMENT);

    /**
     * Default rendered logo size in pixels.
     */
    private static final int LOGO_SIZE = 512;

    /**
     * Parsed ChessRTK logo SVG.
     */
    private static final DocumentModel LOGO_DOCUMENT = Svg.parse(SvgShapes.logo());

    /**
     * Rendered ChessRTK logo image.
     */
    public static final BufferedImage Logo = renderSvg(LOGO_DOCUMENT, LOGO_SIZE, LOGO_SIZE);

    /**
     * Renders one embedded SVG by file name.
     *
     * @param fileName embedded SVG file name
     * @return rendered piece image
     */
    public static BufferedImage render(String fileName) {
        int size = "board.svg".equals(fileName) ? BOARD_SIZE : PIECE_SIZE;
        return renderSvg(document(fileName), size, size);
    }

    /**
     * Renders a parsed embedded SVG document at the default piece size.
     *
     * @param doc parsed SVG document
     * @return rendered piece image
     */
    private static BufferedImage renderSvg(DocumentModel doc) {
        return renderSvg(doc, PIECE_SIZE, PIECE_SIZE);
    }

    /**
     * Renders a parsed embedded SVG document at a requested size.
     *
     * @param doc parsed SVG document
     * @param width output width in pixels
     * @param height output height in pixels
     * @return rendered image
     */
    private static BufferedImage renderSvg(DocumentModel doc, int width, int height) {
        return Svg.render(doc, width, height);
    }

    /**
     * Draws the embedded vector chessboard into an existing graphics context.
     *
     * @param g graphics context
     * @param x target x position
     * @param y target y position
     * @param width target width
     * @param height target height
     */
    public static void drawBoard(Graphics2D g, double x, double y, double width, double height) {
        Svg.draw(BOARD_DOCUMENT, g, x, y, width, height);
    }

    /**
     * Last accent hex previously passed to {@link #drawBoard(Graphics2D, double, double, double, double, String)}.
     */
    private static String cachedAccentHex;

    /**
     * Parsed board document corresponding to {@link #cachedAccentHex}.
     */
    private static DocumentModel cachedAccentDocument;

    /**
     * Draws the embedded vector chessboard tinted with an accent color.
     *
     * <p>A {@code null} or blank accent yields the default neutral board. The
     * derived document is cached so successive calls with the same accent reuse
     * the same parsed SVG.</p>
     *
     * @param g graphics context
     * @param x target x position
     * @param y target y position
     * @param width target width
     * @param height target height
     * @param accentHex CSS-style hex color; {@code null} for default
     */
    public static void drawBoard(Graphics2D g, double x, double y, double width, double height,
            String accentHex) {
        if (accentHex == null || accentHex.isBlank()) {
            drawBoard(g, x, y, width, height);
            return;
        }
        Svg.draw(accentBoardDocument(accentHex), g, x, y, width, height);
    }

    /**
     * Returns a parsed accent-tinted board SVG, caching the most recently used hex.
     *
     * @param accentHex CSS-style hex color (non-null, non-blank)
     * @return parsed SVG document
     */
    private static synchronized DocumentModel accentBoardDocument(String accentHex) {
        if (cachedAccentDocument != null && accentHex.equals(cachedAccentHex)) {
            return cachedAccentDocument;
        }
        DocumentModel doc = Svg.parse(SvgShapes.boardWithAccent(accentHex));
        cachedAccentHex = accentHex;
        cachedAccentDocument = doc;
        return doc;
    }

    /**
     * Draws a piece SVG into an existing graphics context.
     *
     * @param piece piece code from {@link Piece}
     * @param g graphics context
     * @param x target x position
     * @param y target y position
     * @param width target width
     * @param height target height
     */
    public static void drawPiece(byte piece, Graphics2D g, double x, double y, double width, double height) {
        drawPiece(PieceSet.SLATE, piece, g, x, y, width, height);
    }

    /**
     * Outline-set fill for white pieces.
     */
    private static final Color OUTLINE_WHITE_FILL = new Color(0xF7F7F7);

    /**
     * Outline-set fill for black pieces.
     */
    private static final Color OUTLINE_BLACK_FILL = new Color(0x33363B);

    /**
     * Outline-set contour ink, shared by both colors for a single-weight look.
     */
    private static final Color OUTLINE_INK = new Color(0x23262B);

    /**
     * Outline-set contour stroke width as a fraction of the document viewBox.
     */
    private static final double OUTLINE_STROKE_FRACTION = 0.018;

    /**
     * Renders one piece using the requested {@link PieceSet}. The board reads
     * the active set from preferences; image and PDF exports keep the default
     * {@link PieceSet#SLATE} via the single-argument overload.
     *
     * @param set piece artwork set
     * @param piece piece code from {@link Piece}
     * @param g graphics context
     * @param x target x position
     * @param y target y position
     * @param width target width
     * @param height target height
     */
    public static void drawPiece(PieceSet set, byte piece, Graphics2D g,
            double x, double y, double width, double height) {
        if (set == PieceSet.STAUNTON) {
            chess.images.assets.shape.StauntonPieces.draw(piece, g, x, y, width, height);
            return;
        }
        DocumentModel doc = documentForPiece(piece);
        if (doc == null) {
            return;
        }
        if (set == PieceSet.OUTLINE) {
            boolean white = Piece.isWhitePiece(piece);
            Color fill = white ? OUTLINE_WHITE_FILL : OUTLINE_BLACK_FILL;
            double strokeUnits = doc.viewBoxWidth() * OUTLINE_STROKE_FRACTION;
            Svg.drawOutline(doc, g, x, y, width, height, fill, OUTLINE_INK, strokeUnits);
            return;
        }
        Svg.draw(doc, g, x, y, width, height);
    }

    /**
     * Resolves a chess piece code to its rendered SVG-backed image.
     *
     * @param piece piece code from {@link Piece}
     * @return rendered image or null for an empty/unknown piece
     */
    public static BufferedImage forPiece(byte piece) {
        return switch (piece) {
            case Piece.BLACK_BISHOP -> BlackBishop;
            case Piece.BLACK_KING -> BlackKing;
            case Piece.BLACK_KNIGHT -> BlackKnight;
            case Piece.BLACK_PAWN -> BlackPawn;
            case Piece.BLACK_QUEEN -> BlackQueen;
            case Piece.BLACK_ROOK -> BlackRook;
            case Piece.WHITE_BISHOP -> WhiteBishop;
            case Piece.WHITE_KING -> WhiteKing;
            case Piece.WHITE_KNIGHT -> WhiteKnight;
            case Piece.WHITE_PAWN -> WhitePawn;
            case Piece.WHITE_QUEEN -> WhiteQueen;
            case Piece.WHITE_ROOK -> WhiteRook;
            default -> null;
        };
    }

    /**
     * Resolves a chess piece code to its parsed SVG document.
     *
     * @param piece piece code from {@link Piece}
     * @return parsed SVG document or null for an empty/unknown piece
     */
    private static DocumentModel documentForPiece(byte piece) {
        return switch (piece) {
            case Piece.BLACK_BISHOP -> BLACK_BISHOP_DOCUMENT;
            case Piece.BLACK_KING -> BLACK_KING_DOCUMENT;
            case Piece.BLACK_KNIGHT -> BLACK_KNIGHT_DOCUMENT;
            case Piece.BLACK_PAWN -> BLACK_PAWN_DOCUMENT;
            case Piece.BLACK_QUEEN -> BLACK_QUEEN_DOCUMENT;
            case Piece.BLACK_ROOK -> BLACK_ROOK_DOCUMENT;
            case Piece.WHITE_BISHOP -> WHITE_BISHOP_DOCUMENT;
            case Piece.WHITE_KING -> WHITE_KING_DOCUMENT;
            case Piece.WHITE_KNIGHT -> WHITE_KNIGHT_DOCUMENT;
            case Piece.WHITE_PAWN -> WHITE_PAWN_DOCUMENT;
            case Piece.WHITE_QUEEN -> WHITE_QUEEN_DOCUMENT;
            case Piece.WHITE_ROOK -> WHITE_ROOK_DOCUMENT;
            default -> null;
        };
    }

    /**
     * Resolves parsed embedded SVG data by file name.
     *
     * @param fileName embedded SVG file name
     * @return parsed SVG document
     */
    private static DocumentModel document(String fileName) {
        return switch (fileName) {
            case "board.svg" -> BOARD_DOCUMENT;
            case "white-king.svg" -> WHITE_KING_DOCUMENT;
            case "white-queen.svg" -> WHITE_QUEEN_DOCUMENT;
            case "white-rook.svg" -> WHITE_ROOK_DOCUMENT;
            case "white-bishop.svg" -> WHITE_BISHOP_DOCUMENT;
            case "white-knight.svg" -> WHITE_KNIGHT_DOCUMENT;
            case "white-pawn.svg" -> WHITE_PAWN_DOCUMENT;
            case "black-king.svg" -> BLACK_KING_DOCUMENT;
            case "black-queen.svg" -> BLACK_QUEEN_DOCUMENT;
            case "black-rook.svg" -> BLACK_ROOK_DOCUMENT;
            case "black-bishop.svg" -> BLACK_BISHOP_DOCUMENT;
            case "black-knight.svg" -> BLACK_KNIGHT_DOCUMENT;
            case "black-pawn.svg" -> BLACK_PAWN_DOCUMENT;
            default -> throw new IllegalArgumentException("Unknown embedded SVG: " + fileName);
        };
    }
}
