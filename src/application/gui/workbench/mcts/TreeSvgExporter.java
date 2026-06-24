package application.gui.workbench.mcts;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.ui.NotationPainter;
import chess.book.render.NotationPieceSvg;
import chess.core.Piece;
import chess.core.Position;
import chess.images.assets.shape.SvgShapes;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
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
     * Amber omitted-node badge accent, matching the live canvas search accent.
     */
    private static final Color OMITTED_COLOR = new Color(0xFF, 0xB4, 0x54);

    /**
     * Caption move-label font (matches the live canvas: bold 11 sans-serif).
     */
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    /**
     * Metrics for {@link #LABEL_FONT}, used to advance the cursor between inline
     * text runs and figurine glyphs so the layout mirrors the live canvas.
     */
    private static final FontMetrics LABEL_METRICS = labelMetrics();

    /**
     * Pixel gap between an inline figurine glyph and adjacent text (matches the
     * live {@code NotationPainter} spacing).
     */
    private static final int NOTATION_PIECE_GAP = 2;

    /**
     * Embedded black fill used by every {@link NotationPieceSvg} outline glyph,
     * swapped for the caption text color on export.
     */
    private static final String NOTATION_GLYPH_FILL = "fill=\"#000000\"";

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
        int footer = model.omittedNodes() > 0 ? 32 : 0;
        int height = Math.max(2 * TreeLayout.MARGIN, model.height()) + footer;
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
        appendOmittedBadge(sb, model.omittedNodes(), width, height, textColor);
        sb.append("</svg>\n");
        return sb.toString();
    }

    /**
     * Appends a footer badge for aggregate nodes omitted by snapshot caps.
     *
     * @param sb output builder
     * @param omittedNodes omitted-node count
     * @param width SVG width
     * @param height SVG height
     * @param textColor badge text color
     */
    private static void appendOmittedBadge(StringBuilder sb, int omittedNodes,
            int width, int height, Color textColor) {
        if (omittedNodes <= 0) {
            return;
        }
        String label = "+" + compactCount(omittedNodes) + " omitted";
        int badgeW = Math.max(82, LABEL_METRICS.stringWidth(label) + 18);
        int x = Math.max(8, width - badgeW - 12);
        int y = Math.max(8, height - 28);
        sb.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                .append("\" width=\"").append(badgeW).append("\" height=\"20\" rx=\"10\"")
                .append(" fill=\"").append(hex(OMITTED_COLOR)).append("\" fill-opacity=\"0.24\"")
                .append(" stroke=\"").append(hex(OMITTED_COLOR)).append("\" stroke-opacity=\"0.85\"/>\n");
        sb.append("<text x=\"").append(x + 9).append("\" y=\"").append(y + 14)
                .append("\" font-family=\"sans-serif\" font-size=\"10\" font-weight=\"bold\" fill=\"")
                .append(hex(textColor)).append("\">").append(escape(label)).append("</text>\n");
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
        appendNotationLabel(sb, label, node.x() + 7, capY + 14, textColor);
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
     * Appends a node's move label as inline figurine notation: SAN piece letters
     * become the same neutral outline glyphs the live canvas paints (shared
     * {@link NotationPainter} segmentation + {@link NotationPieceSvg} sources,
     * recolored to the caption text color), while files, ranks, captures, and
     * suffixes stay as text. Mirrors {@code NotationPainter.draw} so the export
     * reads identically to the on-screen caption.
     *
     * @param sb output builder
     * @param label move label (SAN, or {@code root})
     * @param x left x coordinate
     * @param baseline text baseline
     * @param textColor caption text color
     */
    private static void appendNotationLabel(StringBuilder sb, String label, int x, int baseline,
            Color textColor) {
        int iconSize = NotationPainter.iconSize(LABEL_METRICS);
        int iconTop = baseline
                - (LABEL_METRICS.getAscent() - LABEL_METRICS.getDescent()) / 2 - iconSize / 2;
        double scale = iconSize / PIECE_VIEWBOX_SIZE;
        double cursor = x;
        for (NotationPainter.Segment segment : NotationPainter.tokenize(label)) {
            if (segment.isPiece()) {
                String source = NotationPieceSvg.svg(segment.piece());
                if (source != null) {
                    sb.append("<g transform=\"translate(").append(fmt(cursor)).append(' ').append(iconTop)
                            .append(") scale(").append(fmt(scale)).append(")\">")
                            .append(inlineGlyph(source, textColor)).append("</g>\n");
                }
                cursor += iconSize + NOTATION_PIECE_GAP;
            } else if (!segment.text().isEmpty()) {
                sb.append("<text x=\"").append(fmt(cursor)).append("\" y=\"").append(baseline)
                        .append("\" font-family=\"sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"")
                        .append(hex(textColor)).append("\">").append(escape(segment.text()))
                        .append("</text>\n");
                cursor += LABEL_METRICS.stringWidth(segment.text());
            }
        }
    }

    /**
     * Prepares one figurine glyph for embedding: strips the outer {@code <svg>}
     * wrapper and the per-glyph {@code <title>} (its fixed id would collide when
     * many glyphs share one document), then recolors the outline to the caption
     * text color.
     *
     * @param source notation glyph SVG source
     * @param color caption text color
     * @return inline-ready glyph markup
     */
    private static String inlineGlyph(String source, Color color) {
        String inner = innerSvg(source).replaceAll("<title[^>]*>.*?</title>", "");
        return inner.replace(NOTATION_GLYPH_FILL, "fill=\"" + hex(color) + "\"");
    }

    /**
     * Resolves font metrics for the caption move label without a live component.
     *
     * @return metrics for {@link #LABEL_FONT}
     */
    private static FontMetrics labelMetrics() {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        try {
            return g.getFontMetrics(LABEL_FONT);
        } finally {
            g.dispose();
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
     * Formats a count for a narrow badge.
     *
     * @param value count
     * @return compact count text
     */
    private static String compactCount(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return compactDecimal(value / 1_000_000_000.0, "B");
        }
        if (abs >= 1_000_000L) {
            return compactDecimal(value / 1_000_000.0, "M");
        }
        if (abs >= 1_000L) {
            return compactDecimal(value / 1_000.0, "k");
        }
        return Long.toString(value);
    }

    /**
     * Formats one compact decimal and removes a redundant ".0".
     *
     * @param value scaled value
     * @param suffix count suffix
     * @return formatted text
     */
    private static String compactDecimal(double value, String suffix) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text + suffix;
    }

    /**
     * Formats a color as a {@code #rrggbb} string.
     *
     * @param color display color
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
