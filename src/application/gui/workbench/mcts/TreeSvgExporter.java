package application.gui.workbench.mcts;

import application.gui.workbench.board.BoardStyle;
import chess.core.Piece;
import chess.core.Position;
import chess.images.assets.shape.SvgShapes;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Serializes a laid-out search tree to a standalone SVG document.
 *
 * <p>Every node is drawn as <em>true vector</em>: the board squares are
 * {@code <rect>}s and the pieces are the same scalable {@link SvgShapes} sources
 * used by the board exporter, so the document stays crisp at any zoom and never
 * embeds a raster image. Edges and captions are vector too. Transposition edges
 * are dashed and the principal variation is accented, matching the live
 * canvas.</p>
 */
final class TreeSvgExporter {

    /**
     * SVG view-box size of the embedded piece sources (matches BoardExporter).
     */
    private static final double PIECE_VIEWBOX_SIZE = 200.0;

    /**
     * Emerald selection ring, matching the live canvas.
     */
    private static final Color SELECT_COLOR = new Color(0x3D, 0xD6, 0x7E);

    /**
     * Prevents instantiation.
     */
    private TreeSvgExporter() {
    }

    /**
     * Renders a model to an SVG document.
     *
     * @param model laid-out tree
     * @param background canvas background color
     * @param accent principal-variation / selection accent
     * @param transpositionColor transposition-edge color
     * @param edgeColor primary-edge color
     * @param captionFill caption strip fill
     * @param textColor caption text color
     * @param mutedColor secondary text color
     * @return SVG document text
     */
    static String toSvg(TreeLayout.Model model, Color background, Color accent,
            Color transpositionColor, Color edgeColor, Color captionFill,
            Color textColor, Color mutedColor) {
        int width = Math.max(2 * TreeLayout.MARGIN, model.width());
        int height = Math.max(2 * TreeLayout.MARGIN, model.height());
        StringBuilder sb = new StringBuilder(1 << 16);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
                .append("width=\"").append(width).append("\" height=\"").append(height).append("\" ")
                .append("viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">\n");
        sb.append("<rect width=\"").append(width).append("\" height=\"").append(height)
                .append("\" fill=\"").append(hex(background)).append("\"/>\n");

        Map<String, TreeLayout.Node> byKey = new HashMap<>();
        for (TreeLayout.Node node : model.nodes()) {
            byKey.put(node.key(), node);
        }

        sb.append("<g fill=\"none\" stroke-linecap=\"round\">\n");
        for (TreeLayout.Edge edge : model.edges()) {
            TreeLayout.Node from = byKey.get(edge.fromKey());
            TreeLayout.Node to = byKey.get(edge.toKey());
            if (from == null || to == null) {
                continue;
            }
            int x1 = from.centerX();
            int y1 = from.y() + from.h();
            int x2 = to.centerX();
            int y2 = to.y();
            int midY = (y1 + y2) / 2;
            boolean pv = from.onPrincipalVariation() && to.onPrincipalVariation() && !edge.transposition();
            sb.append("<path d=\"M ").append(x1).append(' ').append(y1)
                    .append(" C ").append(x1).append(' ').append(midY).append(' ')
                    .append(x2).append(' ').append(midY).append(' ')
                    .append(x2).append(' ').append(y2).append("\" stroke=\"");
            if (edge.transposition()) {
                sb.append(hex(transpositionColor)).append("\" stroke-width=\"1.4\" stroke-dasharray=\"5,5\"/>\n");
            } else if (pv) {
                sb.append(hex(accent)).append("\" stroke-width=\"2.4\"/>\n");
            } else {
                sb.append(hex(edgeColor)).append("\" stroke-width=\"1.4\"/>\n");
            }
        }
        sb.append("</g>\n");

        for (TreeLayout.Node node : model.nodes()) {
            appendNode(sb, node, accent, captionFill, textColor, mutedColor);
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    /**
     * Appends one node group (vector board, caption, border) to the document.
     *
     * @param sb output builder
     * @param node node to render
     * @param accent accent color
     * @param captionFill caption strip fill
     * @param textColor caption text color
     * @param mutedColor secondary text color
     */
    private static void appendNode(StringBuilder sb, TreeLayout.Node node, Color accent,
            Color captionFill, Color textColor, Color mutedColor) {
        int side = node.w();
        int capY = node.y() + side;
        int capH = node.h() - side;
        appendVectorBoard(sb, node.info().fen(), node.x(), node.y(), side);
        sb.append("<rect x=\"").append(node.x()).append("\" y=\"").append(capY)
                .append("\" width=\"").append(side).append("\" height=\"").append(capH)
                .append("\" fill=\"").append(hex(captionFill)).append("\"/>\n");
        String label = node.root() ? "root" : node.info().san();
        sb.append("<text x=\"").append(node.x() + 7).append("\" y=\"").append(capY + 14)
                .append("\" font-family=\"sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"")
                .append(hex(textColor)).append("\">").append(escape(label)).append("</text>\n");
        sb.append("<text x=\"").append(node.x() + 7).append("\" y=\"").append(capY + capH - 5)
                .append("\" font-family=\"sans-serif\" font-size=\"9\" fill=\"")
                .append(hex(mutedColor)).append("\">")
                .append(escape(String.format(Locale.ROOT, "N=%,d  Q%+.2f",
                        node.info().visits(), node.info().q())))
                .append("</text>\n");
        if (node.selected()) {
            sb.append("<rect x=\"").append(node.x() - 1).append("\" y=\"").append(node.y() - 1)
                    .append("\" width=\"").append(node.w() + 2).append("\" height=\"").append(node.h() + 2)
                    .append("\" rx=\"7\" fill=\"none\" stroke=\"").append(hex(SELECT_COLOR))
                    .append("\" stroke-width=\"3\"/>\n");
        } else if (node.onPrincipalVariation()) {
            sb.append("<rect x=\"").append(node.x() - 1).append("\" y=\"").append(node.y() - 1)
                    .append("\" width=\"").append(node.w() + 2).append("\" height=\"").append(node.h() + 2)
                    .append("\" rx=\"7\" fill=\"none\" stroke=\"").append(hex(accent))
                    .append("\" stroke-width=\"1.6\"/>\n");
        }
    }

    /**
     * Appends one position as a vector board: 64 square {@code <rect>}s plus a
     * scaled {@link SvgShapes} piece group per occupied square. White is always
     * rendered at the bottom, matching the live tree canvas.
     *
     * @param sb output builder
     * @param fen position FEN
     * @param x board left coordinate
     * @param y board top coordinate
     * @param side board pixel size
     */
    private static void appendVectorBoard(StringBuilder sb, String fen, int x, int y, int side) {
        Rectangle board = new Rectangle(x, y, side, side);
        sb.append("<g shape-rendering=\"crispEdges\">\n");
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle cell = BoardStyle.cellBounds(board, row, col);
                sb.append("<rect x=\"").append(cell.x).append("\" y=\"").append(cell.y)
                        .append("\" width=\"").append(cell.width).append("\" height=\"").append(cell.height)
                        .append("\" fill=\"").append(hex(BoardStyle.squareColor(row, col))).append("\"/>\n");
            }
        }
        sb.append("</g>\n");
        if (fen == null || fen.isBlank()) {
            return;
        }
        Position position;
        try {
            position = new Position(fen);
        } catch (IllegalArgumentException ex) {
            return;
        }
        byte[] squares = position.getBoard();
        for (byte square = 0; square < Math.min(64, squares.length); square++) {
            byte piece = squares[square];
            if (piece == Piece.EMPTY) {
                continue;
            }
            String source = pieceSvg(piece);
            if (source.isBlank()) {
                continue;
            }
            Rectangle cell = BoardStyle.fieldSquareBounds(board, square, true);
            sb.append("<g transform=\"translate(").append(cell.x).append(' ').append(cell.y)
                    .append(") scale(").append(fmt(cell.width / PIECE_VIEWBOX_SIZE)).append(")\">")
                    .append(innerSvg(source)).append("</g>\n");
        }
    }

    /**
     * Returns the embedded SVG source for one piece.
     *
     * @param piece piece code
     * @return SVG source, or empty text
     */
    private static String pieceSvg(byte piece) {
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
            default -> "";
        };
    }

    /**
     * Extracts the nested content from a full SVG document.
     *
     * @param source SVG source
     * @return inner SVG content
     */
    private static String innerSvg(String source) {
        int start = source.indexOf('>');
        int end = source.lastIndexOf("</svg>");
        if (start < 0 || end <= start) {
            return source;
        }
        return source.substring(start + 1, end);
    }

    /**
     * Formats a compact SVG numeric value.
     *
     * @param value numeric value
     * @return compact SVG number
     */
    private static String fmt(double value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        return text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
    }

    /**
     * Formats a color as a {@code #rrggbb} string.
     *
     * @param color color
     * @return hex string
     */
    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Escapes XML metacharacters.
     *
     * @param text raw text
     * @return escaped text
     */
    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
